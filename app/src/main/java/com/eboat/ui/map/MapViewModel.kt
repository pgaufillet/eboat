package com.eboat.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eboat.data.db.EboatDatabase
import com.eboat.data.location.LocationRepository
import com.eboat.data.offline.DownloadProgress
import com.eboat.data.offline.OfflineRegionInfo
import com.eboat.data.offline.OfflineRepository
import com.eboat.data.ais.AisRepository
import com.eboat.data.tide.TideRepository
import com.eboat.data.weather.WeatherRepository
import com.eboat.domain.model.AisTarget
import com.eboat.domain.model.TideData
import com.eboat.domain.model.WeatherLayerType
import com.eboat.domain.navigation.corridorGrid
import com.eboat.domain.model.TripLogEntry
import com.eboat.domain.model.WeatherData
import com.eboat.domain.model.AlertZone
import com.eboat.domain.model.AnchorState
import com.eboat.domain.model.BoatState
import com.eboat.domain.model.GuidanceState
import com.eboat.domain.model.Route
import com.eboat.domain.model.Waypoint
import com.eboat.service.AnchorAlarmService
import com.eboat.domain.navigation.bearingDeg
import com.eboat.domain.navigation.crossTrackNm
import com.eboat.domain.navigation.distanceNm
import com.eboat.domain.navigation.etaSeconds
import com.eboat.domain.navigation.isPointInPolygon
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Arrival radius in nautical miles */
private const val ARRIVAL_RADIUS_NM = 0.05

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val locationRepository = LocationRepository(application)
    private val db = EboatDatabase.getInstance(application)
    private val waypointDao = db.waypointDao()
    private val routeDao = db.routeDao()
    private val alertZoneDao = db.alertZoneDao()
    private val offlineRepo = OfflineRepository(application)
    private val tideRepo = TideRepository()
    private val weatherRepo = WeatherRepository()
    private val aisRepo = AisRepository()
    private val tripLogDao = db.tripLogDao()

    private val _boatState = MutableStateFlow(BoatState())
    val boatState: StateFlow<BoatState> = _boatState.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    val waypoints: StateFlow<List<Waypoint>> = waypointDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routes: StateFlow<List<Route>> = routeDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeRouteWaypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val activeRouteWaypoints: StateFlow<List<Waypoint>> = _activeRouteWaypoints.asStateFlow()

    private val _activeRoute = MutableStateFlow<Route?>(null)
    val activeRoute: StateFlow<Route?> = _activeRoute.asStateFlow()

    private val _guidance = MutableStateFlow(GuidanceState())
    val guidance: StateFlow<GuidanceState> = _guidance.asStateFlow()

    private var routeWaypointsJob: Job? = null

    fun startTracking() {
        if (_isTracking.value) return
        _isTracking.value = true
        viewModelScope.launch {
            locationRepository.observeLocation().collect { state ->
                _boatState.value = state
                updateGuidance(state)
                updateAnchorAlarm(state)
                updateAlertZones(state)
                logTripPoint(state)
            }
        }
    }

    fun stopTracking() {
        _isTracking.value = false
    }

    fun addWaypoint(name: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            waypointDao.insert(Waypoint(name = name, latitude = latitude, longitude = longitude))
        }
    }

    fun moveWaypoint(waypoint: Waypoint, newLat: Double, newLng: Double) {
        viewModelScope.launch {
            waypointDao.update(waypoint.copy(latitude = newLat, longitude = newLng))
        }
    }

    fun deleteWaypoint(waypoint: Waypoint) {
        viewModelScope.launch {
            waypointDao.delete(waypoint)
        }
    }

    fun createRoute(name: String, waypointIds: List<Long>) {
        viewModelScope.launch {
            routeDao.createRoute(name, waypointIds)
        }
    }

    fun deleteRoute(route: Route) {
        viewModelScope.launch {
            if (_activeRoute.value?.id == route.id) {
                deactivateRoute()
            }
            routeDao.deleteRoute(route)
        }
    }

    fun activateRoute(route: Route) {
        _activeRoute.value = route
        _guidance.value = GuidanceState(active = true, nextWaypointIndex = 0)
        routeWaypointsJob?.cancel()
        routeWaypointsJob = viewModelScope.launch {
            routeDao.observeWaypointsForRoute(route.id).collect { wps ->
                _activeRouteWaypoints.value = wps
                _guidance.value = _guidance.value.copy(totalWaypoints = wps.size)
                updateGuidance(_boatState.value)
            }
        }
    }

    fun deactivateRoute() {
        routeWaypointsJob?.cancel()
        _activeRoute.value = null
        _activeRouteWaypoints.value = emptyList()
        _guidance.value = GuidanceState()
    }

    // --- Map layers ---

    private val _depthLayerVisible = MutableStateFlow(true)
    val depthLayerVisible: StateFlow<Boolean> = _depthLayerVisible.asStateFlow()

    fun toggleDepthLayer() {
        _depthLayerVisible.value = !_depthLayerVisible.value
    }

    // --- Tides ---

    private val _tideData = MutableStateFlow<TideData?>(null)
    val tideData: StateFlow<TideData?> = _tideData.asStateFlow()

    fun fetchTides(lat: Double, lon: Double) {
        viewModelScope.launch {
            _tideData.value = tideRepo.fetchTides(lat, lon)
        }
    }

    fun fetchTidesAtBoat() {
        val boat = _boatState.value
        if (boat.hasPosition) fetchTides(boat.latitude, boat.longitude)
    }

    // --- Weather ---

    private val _weatherData = MutableStateFlow<WeatherData?>(null)
    val weatherData: StateFlow<WeatherData?> = _weatherData.asStateFlow()

    private val _weatherGrid = MutableStateFlow<List<WeatherData>>(emptyList())
    val weatherGrid: StateFlow<List<WeatherData>> = _weatherGrid.asStateFlow()

    private val _weatherLayers = MutableStateFlow<Set<WeatherLayerType>>(emptySet())
    val weatherLayers: StateFlow<Set<WeatherLayerType>> = _weatherLayers.asStateFlow()

    fun fetchWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            _weatherData.value = weatherRepo.fetchForecast(lat, lon)
        }
    }

    fun fetchWeatherAtBoat() {
        val boat = _boatState.value
        if (boat.hasPosition) fetchWeather(boat.latitude, boat.longitude)
    }

    fun toggleWeatherLayer(type: WeatherLayerType) {
        val current = _weatherLayers.value.toMutableSet()
        if (type in current) current.remove(type) else current.add(type)
        _weatherLayers.value = current
    }

    fun clearWeatherLayers() {
        _weatherLayers.value = emptySet()
        _weatherGrid.value = emptyList()
    }

    fun fetchWeatherOverlay() {
        viewModelScope.launch {
            val wps = _activeRouteWaypoints.value
            val gridPoints = if (wps.size >= 2) {
                corridorGrid(wps.map { it.latitude to it.longitude })
            } else {
                val boat = _boatState.value
                if (boat.hasPosition) {
                    corridorGrid(listOf(boat.latitude to boat.longitude))
                } else emptyList()
            }
            if (gridPoints.isNotEmpty()) {
                _weatherGrid.value = weatherRepo.fetchCorridor(gridPoints)
            }
        }
    }

    // --- AIS ---

    val aisTargets: StateFlow<Map<String, AisTarget>> = aisRepo.targets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val aisConnected: StateFlow<Boolean> = aisRepo.connected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun connectAis(host: String, port: Int) {
        viewModelScope.launch { aisRepo.connect(host, port) }
    }

    // --- Trip log ---

    private val _tripRecording = MutableStateFlow(false)
    val tripRecording: StateFlow<Boolean> = _tripRecording.asStateFlow()

    private var currentTripId: Long = 0
    private var lastLogTime: Long = 0

    val tripIds: StateFlow<List<Long>> = tripLogDao.observeTripIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startTripRecording() {
        viewModelScope.launch {
            currentTripId = (tripLogDao.getLastTripId() ?: 0) + 1
            _tripRecording.value = true
            lastLogTime = 0
        }
    }

    fun stopTripRecording() {
        _tripRecording.value = false
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch { tripLogDao.deleteTrip(tripId) }
    }

    suspend fun getTripEntries(tripId: Long) = tripLogDao.getEntriesForTrip(tripId)

    private fun logTripPoint(boat: BoatState) {
        if (!_tripRecording.value || !boat.hasPosition) return
        val now = System.currentTimeMillis()
        // Log every 10 seconds
        if (now - lastLogTime < 10_000) return
        lastLogTime = now
        viewModelScope.launch {
            tripLogDao.insert(TripLogEntry(
                tripId = currentTripId,
                latitude = boat.latitude,
                longitude = boat.longitude,
                sog = boat.speedOverGround,
                cog = boat.courseOverGround
            ))
        }
    }

    // --- Alert zones ---

    val alertZones: StateFlow<List<AlertZone>> = alertZoneDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _triggeredZones = MutableStateFlow<Set<Long>>(emptySet())
    val triggeredZones: StateFlow<Set<Long>> = _triggeredZones.asStateFlow()

    /** Track which zones the boat was previously inside */
    private val previouslyInside = mutableSetOf<Long>()

    fun addAlertZone(name: String, points: List<Pair<Double, Double>>, alertOnEntry: Boolean) {
        viewModelScope.launch {
            alertZoneDao.insert(AlertZone(
                name = name,
                pointsEncoded = AlertZone.encodePoints(points),
                alertOnEntry = alertOnEntry
            ))
        }
    }

    fun deleteAlertZone(zone: AlertZone) {
        viewModelScope.launch {
            alertZoneDao.delete(zone)
            _triggeredZones.value = _triggeredZones.value - zone.id
            previouslyInside.remove(zone.id)
        }
    }

    fun toggleAlertZone(zone: AlertZone) {
        viewModelScope.launch {
            alertZoneDao.update(zone.copy(enabled = !zone.enabled))
        }
    }

    fun clearTriggeredZone(zoneId: Long) {
        _triggeredZones.value = _triggeredZones.value - zoneId
    }

    private fun updateAlertZones(boat: BoatState) {
        if (!boat.hasPosition) return
        val zones = alertZones.value.filter { it.enabled }
        val newTriggered = mutableSetOf<Long>()

        for (zone in zones) {
            val points = zone.decodePoints()
            if (points.size < 3) continue
            val inside = isPointInPolygon(boat.latitude, boat.longitude, points)
            val wasInside = zone.id in previouslyInside

            if (zone.alertOnEntry && inside && !wasInside) {
                newTriggered.add(zone.id)
                AnchorAlarmService.triggerAlarm(getApplication())
            } else if (!zone.alertOnEntry && !inside && wasInside) {
                newTriggered.add(zone.id)
                AnchorAlarmService.triggerAlarm(getApplication())
            }

            if (inside) previouslyInside.add(zone.id) else previouslyInside.remove(zone.id)
        }

        if (newTriggered.isNotEmpty()) {
            _triggeredZones.value = _triggeredZones.value + newTriggered
        }
    }

    // --- Anchor alarm ---

    private val _anchorState = MutableStateFlow(AnchorState())
    val anchorState: StateFlow<AnchorState> = _anchorState.asStateFlow()

    fun dropAnchor(radiusMeters: Int = 50) {
        val boat = _boatState.value
        if (!boat.hasPosition) return
        _anchorState.value = AnchorState(
            active = true,
            latitude = boat.latitude,
            longitude = boat.longitude,
            radiusMeters = radiusMeters
        )
        AnchorAlarmService.start(getApplication())
    }

    fun liftAnchor() {
        _anchorState.value = AnchorState()
        val app = getApplication<Application>()
        AnchorAlarmService.clearAlarm(app)
        AnchorAlarmService.stop(app)
    }

    fun setAnchorRadius(meters: Int) {
        val state = _anchorState.value
        if (state.active) {
            _anchorState.value = state.copy(radiusMeters = meters)
            // Clear alarm if we're now back within radius
            if (state.currentDistanceMeters <= meters) {
                _anchorState.value = _anchorState.value.copy(isDragging = false)
                AnchorAlarmService.clearAlarm(getApplication())
            }
        }
    }

    private fun updateAnchorAlarm(boat: BoatState) {
        val anchor = _anchorState.value
        if (!anchor.active || !boat.hasPosition) return

        val distNm = distanceNm(boat.latitude, boat.longitude, anchor.latitude, anchor.longitude)
        val distMeters = distNm * 1852.0
        val wasDragging = anchor.isDragging
        val isDragging = distMeters > anchor.radiusMeters

        _anchorState.value = anchor.copy(
            currentDistanceMeters = distMeters,
            isDragging = isDragging
        )

        if (isDragging && !wasDragging) {
            AnchorAlarmService.triggerAlarm(getApplication())
        } else if (!isDragging && wasDragging) {
            AnchorAlarmService.clearAlarm(getApplication())
        }
    }

    // --- Offline ---

    private val _offlineRegions = MutableStateFlow<List<OfflineRegionInfo>>(emptyList())
    val offlineRegions: StateFlow<List<OfflineRegionInfo>> = _offlineRegions.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    fun refreshOfflineRegions() {
        viewModelScope.launch {
            _offlineRegions.value = offlineRepo.listRegions()
        }
    }

    fun downloadRegion(
        name: String,
        styleUrl: String,
        bounds: org.maplibre.android.geometry.LatLngBounds,
        minZoom: Double,
        maxZoom: Double
    ) {
        viewModelScope.launch {
            offlineRepo.downloadRegion(name, styleUrl, bounds, minZoom, maxZoom).collect { progress ->
                _downloadProgress.value = progress
                if (progress.isComplete) {
                    _downloadProgress.value = null
                    refreshOfflineRegions()
                }
            }
        }
    }

    fun deleteOfflineRegion(regionId: Long) {
        viewModelScope.launch {
            offlineRepo.deleteRegion(regionId)
            refreshOfflineRegions()
        }
    }

    fun clearAllOfflineRegions() {
        viewModelScope.launch {
            offlineRepo.clearAllRegions()
            refreshOfflineRegions()
        }
    }

    fun advanceToNextWaypoint() {
        val g = _guidance.value
        if (g.nextWaypointIndex < g.totalWaypoints - 1) {
            _guidance.value = g.copy(nextWaypointIndex = g.nextWaypointIndex + 1)
            updateGuidance(_boatState.value)
        }
    }

    private fun updateGuidance(boat: BoatState) {
        val g = _guidance.value
        if (!g.active || !boat.hasPosition) return
        val wps = _activeRouteWaypoints.value
        if (wps.isEmpty() || g.nextWaypointIndex >= wps.size) return

        val target = wps[g.nextWaypointIndex]
        val dist = distanceNm(boat.latitude, boat.longitude, target.latitude, target.longitude)
        val bearing = bearingDeg(boat.latitude, boat.longitude, target.latitude, target.longitude)
        val eta = etaSeconds(dist, boat.speedOverGround)

        // Cross-track: from previous waypoint (or boat start) to target
        val xte = if (g.nextWaypointIndex > 0) {
            val prev = wps[g.nextWaypointIndex - 1]
            crossTrackNm(
                boat.latitude, boat.longitude,
                prev.latitude, prev.longitude,
                target.latitude, target.longitude
            )
        } else 0.0

        _guidance.value = g.copy(
            nextWaypointName = target.name,
            bearingToWaypoint = bearing,
            distanceToWaypointNm = dist,
            crossTrackNm = xte,
            etaSeconds = eta
        )

        // Auto-advance when within arrival radius
        if (dist < ARRIVAL_RADIUS_NM) {
            if (g.nextWaypointIndex < wps.size - 1) {
                _guidance.value = _guidance.value.copy(
                    nextWaypointIndex = g.nextWaypointIndex + 1
                )
            } else {
                _guidance.value = _guidance.value.copy(routeComplete = true)
            }
        }
    }
}
