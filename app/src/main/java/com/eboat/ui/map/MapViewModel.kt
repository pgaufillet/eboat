package com.eboat.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eboat.data.location.LocationRepository
import com.eboat.domain.model.BoatState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val locationRepository = LocationRepository(application)

    private val _boatState = MutableStateFlow(BoatState())
    val boatState: StateFlow<BoatState> = _boatState.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

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
}
