package com.eboat.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eboat.data.db.EboatDatabase
import com.eboat.data.location.LocationRepository
import com.eboat.data.offline.DownloadProgress
import com.eboat.data.offline.OfflineRegionInfo
import com.eboat.data.offline.OfflineRepository
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
    private val offlineRepo = OfflineRepository(application)

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
