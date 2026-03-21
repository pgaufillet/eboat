package com.eboat.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eboat.data.db.EboatDatabase
import com.eboat.data.location.LocationRepository
import com.eboat.domain.model.BoatState
import com.eboat.domain.model.Waypoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val locationRepository = LocationRepository(application)
    private val waypointDao = EboatDatabase.getInstance(application).waypointDao()

    private val _boatState = MutableStateFlow(BoatState())
    val boatState: StateFlow<BoatState> = _boatState.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    val waypoints: StateFlow<List<Waypoint>> = waypointDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun deleteWaypoint(waypoint: Waypoint) {
        viewModelScope.launch {
            waypointDao.delete(waypoint)
        }
    }
}
