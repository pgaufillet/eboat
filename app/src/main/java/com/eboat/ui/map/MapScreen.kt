package com.eboat.ui.map

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eboat.R
import com.eboat.domain.model.Waypoint
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.util.Locale

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val boatState by viewModel.boatState.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()
    val waypoints by viewModel.waypoints.collectAsState()
    var permissionGranted by remember { mutableStateOf(false) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var boatMarker by remember { mutableStateOf<Marker?>(null) }
    val waypointMarkers = remember { mutableMapOf<Long, Marker>() }

    // Dialog state for creating a new waypoint
    var showWaypointDialog by remember { mutableStateOf(false) }
    var pendingWaypointLatLng by remember { mutableStateOf<LatLng?>(null) }
    var waypointName by remember { mutableStateOf("") }

    // Dialog state for deleting a waypoint
    var waypointToDelete by remember { mutableStateOf<Waypoint?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted = permissions.values.all { it }
        if (permissionGranted) {
            viewModel.startTracking()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Update boat marker and center map when position updates
    LaunchedEffect(boatState.hasPosition, boatState.latitude, boatState.longitude, boatState.courseOverGround) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (boatState.hasPosition) {
            val position = LatLng(boatState.latitude, boatState.longitude)
            val baseBitmap = ContextCompat.getDrawable(context, R.drawable.ic_boat)!!
                .toBitmap(96, 96)
            val rotated = rotateBitmap(baseBitmap, boatState.courseOverGround)
            val icon = IconFactory.getInstance(context).fromBitmap(rotated)

            boatMarker?.let { map.removeMarker(it) }
            boatMarker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("eboat")
                    .icon(icon)
            )
            map.animateCamera(
                CameraUpdateFactory.newLatLng(position),
                1_000
            )
        }
    }

    // Sync waypoint markers with database
    LaunchedEffect(waypoints, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val currentIds = waypoints.map { it.id }.toSet()

        // Remove markers for deleted waypoints
        waypointMarkers.keys.filter { it !in currentIds }.forEach { id ->
            waypointMarkers.remove(id)?.let { map.removeMarker(it) }
        }

        // Add markers for new waypoints
        waypoints.forEach { wp ->
            if (wp.id !in waypointMarkers) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(wp.latitude, wp.longitude))
                        .title(wp.name)
                )
                waypointMarkers[wp.id] = marker
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // MapLibre map
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    getMapAsync { map ->
                        mapLibreMap = map
                        map.setStyle("asset://map_style.json")
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(46.15, -1.15)) // Default: La Rochelle
                            .zoom(10.0)
                            .build()
                        map.uiSettings.isCompassEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isZoomGesturesEnabled = true

                        // Long press to create waypoint
                        map.addOnMapLongClickListener { latLng ->
                            pendingWaypointLatLng = latLng
                            waypointName = ""
                            showWaypointDialog = true
                            true
                        }

                        // Tap marker to delete waypoint
                        map.setOnMarkerClickListener { marker ->
                            val wp = waypoints.find { waypointMarkers[it.id] == marker }
                            if (wp != null) {
                                waypointToDelete = wp
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            },
            update = { mapView ->
                mapView.onResume()
            }
        )

        // Navigation data overlay
        if (boatState.hasPosition) {
            NavigationOverlay(
                latitude = boatState.latitude,
                longitude = boatState.longitude,
                sog = boatState.speedOverGround,
                cog = boatState.courseOverGround,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }

        // Center on boat button
        if (boatState.hasPosition) {
            FloatingActionButton(
                onClick = {
                    mapLibreMap?.animateCamera(
                        CameraUpdateFactory.newLatLng(
                            LatLng(boatState.latitude, boatState.longitude)
                        ),
                        500
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text("\u2295", color = Color.White)
            }
        }
    }

    // Create waypoint dialog
    if (showWaypointDialog) {
        AlertDialog(
            onDismissRequest = { showWaypointDialog = false },
            title = { Text("Nouveau waypoint") },
            text = {
                TextField(
                    value = waypointName,
                    onValueChange = { waypointName = it },
                    label = { Text("Nom") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val latLng = pendingWaypointLatLng
                        if (latLng != null && waypointName.isNotBlank()) {
                            viewModel.addWaypoint(waypointName.trim(), latLng.latitude, latLng.longitude)
                        }
                        showWaypointDialog = false
                    }
                ) { Text("Créer") }
            },
            dismissButton = {
                TextButton(onClick = { showWaypointDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Delete waypoint dialog
    waypointToDelete?.let { wp ->
        AlertDialog(
            onDismissRequest = { waypointToDelete = null },
            title = { Text("Supprimer ${wp.name} ?") },
            text = {
                Text("${formatCoordinate(wp.latitude, true)} ${formatCoordinate(wp.longitude, false)}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWaypoint(wp)
                        waypointToDelete = null
                    }
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { waypointToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun NavigationOverlay(
    latitude: Double,
    longitude: Double,
    sog: Float,
    cog: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.7f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavDataItem("LAT", formatCoordinate(latitude, isLatitude = true))
        NavDataItem("LON", formatCoordinate(longitude, isLatitude = false))
        NavDataItem("SOG", String.format(Locale.US, "%.1f kn", sog))
        NavDataItem("COG", String.format(Locale.US, "%.0f\u00B0", cog))
    }
}

@Composable
private fun NavDataItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}

private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees, source.width / 2f, source.height / 2f) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun formatCoordinate(value: Double, isLatitude: Boolean): String {
    val direction = if (isLatitude) {
        if (value >= 0) "N" else "S"
    } else {
        if (value >= 0) "E" else "W"
    }
    val abs = Math.abs(value)
    val degrees = abs.toInt()
    val minutes = (abs - degrees) * 60
    return String.format(Locale.US, "%d\u00B0%05.2f'%s", degrees, minutes, direction)
}
