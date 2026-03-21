package com.eboat.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eboat.data.db.EboatDatabase
import com.eboat.data.location.LocationRepository
import com.eboat.domain.model.BoatState
import com.eboat.domain.model.Route
import com.eboat.domain.model.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val locationRepository = LocationRepository(application)
    private val db = EboatDatabase.getInstance(application)
    private val waypointDao = db.waypointDao()
    private val routeDao = db.routeDao()

    private val _boatState = MutableStateFlow(BoatState())
    val boatState: StateFlow<BoatState> = _boatState.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    val waypoints: StateFlow<List<Waypoint>> = waypointDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routes: StateFlow<List<Route>> = routeDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Waypoints for the currently active route, in order */
    private val _activeRouteWaypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val activeRouteWaypoints: StateFlow<List<Waypoint>> = _activeRouteWaypoints.asStateFlow()

    private val _activeRoute = MutableStateFlow<Route?>(null)
    val activeRoute: StateFlow<Route?> = _activeRoute.asStateFlow()

    fun startTracking() {
        if (_isTracking.value) return
        _isTracking.value = true
        viewModelScope.launch {
            locationRepository.observeLocation().collect { state ->
                _boatState.value = state
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
                _activeRoute.value = null
                _activeRouteWaypoints.value = emptyList()
            }
            routeDao.deleteRoute(route)
        }
    }

    fun activateRoute(route: Route) {
        _activeRoute.value = route
        viewModelScope.launch {
            routeDao.observeWaypointsForRoute(route.id).collect { wps ->
                _activeRouteWaypoints.value = wps
            }
        }
    }

    fun deactivateRoute() {
        _activeRoute.value = null
        _activeRouteWaypoints.value = emptyList()
    }
}
