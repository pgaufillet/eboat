package com.eboat.ui.map

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateListOf
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
import com.eboat.domain.model.Route
import com.eboat.domain.model.Waypoint
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
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
    val waypoints by viewModel.waypoints.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()
    val activeRouteWaypoints by viewModel.activeRouteWaypoints.collectAsState()
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var boatMarker by remember { mutableStateOf<Marker?>(null) }
    val waypointMarkers = remember { mutableMapOf<Long, Marker>() }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }

    // Waypoint creation dialog
    var showWaypointDialog by remember { mutableStateOf(false) }
    var pendingWaypointLatLng by remember { mutableStateOf<LatLng?>(null) }
    var waypointName by remember { mutableStateOf("") }

    // Waypoint action dialog (delete / move)
    var waypointToAct by remember { mutableStateOf<Waypoint?>(null) }

    // Waypoint move mode
    var waypointToMove by remember { mutableStateOf<Waypoint?>(null) }

    // Route creation mode
    var showRouteCreation by remember { mutableStateOf(false) }
    var routeName by remember { mutableStateOf("") }
    val selectedWaypointIds = remember { mutableStateListOf<Long>() }

    // Route list dialog
    var showRouteList by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
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

    // Update boat marker
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
                MarkerOptions().position(position).title("eboat").icon(icon)
            )
            map.animateCamera(CameraUpdateFactory.newLatLng(position), 1_000)
        }
    }

    // Sync waypoint markers
    LaunchedEffect(waypoints, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val currentIds = waypoints.map { it.id }.toSet()
        waypointMarkers.keys.filter { it !in currentIds }.forEach { id ->
            waypointMarkers.remove(id)?.let { map.removeMarker(it) }
        }
        waypoints.forEach { wp ->
            if (wp.id !in waypointMarkers) {
                waypointMarkers[wp.id] = map.addMarker(
                    MarkerOptions().position(LatLng(wp.latitude, wp.longitude)).title(wp.name)
                )
            } else {
                waypointMarkers[wp.id]?.position = LatLng(wp.latitude, wp.longitude)
            }
        }
    }

    // Draw active route polyline
    LaunchedEffect(activeRouteWaypoints, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        routePolyline?.let { map.removePolyline(it) }
        if (activeRouteWaypoints.size >= 2) {
            val points = activeRouteWaypoints.map { LatLng(it.latitude, it.longitude) }
            routePolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .color(android.graphics.Color.parseColor("#E63946"))
                    .width(4f)
            )
        } else {
            routePolyline = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    getMapAsync { map ->
                        mapLibreMap = map
                        map.setStyle("asset://map_style.json")
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(46.15, -1.15))
                            .zoom(10.0)
                            .build()
                        map.uiSettings.isCompassEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isZoomGesturesEnabled = true

                        map.addOnMapLongClickListener { latLng ->
                            if (waypointToMove != null) {
                                // Complete move
                                viewModel.moveWaypoint(waypointToMove!!, latLng.latitude, latLng.longitude)
                                waypointToMove = null
                            } else {
                                // Create new waypoint
                                pendingWaypointLatLng = latLng
                                waypointName = ""
                                showWaypointDialog = true
                            }
                            true
                        }

                        map.setOnMarkerClickListener { marker ->
                            if (marker == boatMarker) return@setOnMarkerClickListener false
                            val wp = waypoints.find { waypointMarkers[it.id] == marker }
                            if (wp != null) {
                                waypointToAct = wp
                                true
                            } else false
                        }
                    }
                }
            },
            update = { it.onResume() }
        )

        // Move mode banner
        if (waypointToMove != null) {
            Text(
                "Appui long pour placer ${waypointToMove!!.name}",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Color(0xFFE63946), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Navigation overlay
        if (boatState.hasPosition) {
            NavigationOverlay(
                latitude = boatState.latitude,
                longitude = boatState.longitude,
                sog = boatState.speedOverGround,
                cog = boatState.courseOverGround,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }

        // Active route name
        activeRoute?.let { route ->
            Text(
                route.name,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp)
                    .background(Color(0xFF1B3A5C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { viewModel.deactivateRoute() },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Buttons column
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (boatState.hasPosition) {
                FloatingActionButton(
                    onClick = {
                        mapLibreMap?.animateCamera(
                            CameraUpdateFactory.newLatLng(
                                LatLng(boatState.latitude, boatState.longitude)
                            ), 500
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) { Text("\u2295", color = Color.White) }
            }
            // Route button
            FloatingActionButton(
                onClick = {
                    if (routes.isEmpty() && waypoints.size >= 2) {
                        routeName = ""
                        selectedWaypointIds.clear()
                        showRouteCreation = true
                    } else {
                        showRouteList = true
                    }
                },
                containerColor = Color(0xFF1B3A5C)
            ) { Text("R", color = Color.White) }
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
                TextButton(onClick = {
                    val latLng = pendingWaypointLatLng
                    if (latLng != null && waypointName.isNotBlank()) {
                        viewModel.addWaypoint(waypointName.trim(), latLng.latitude, latLng.longitude)
                    }
                    showWaypointDialog = false
                }) { Text("Cr\u00e9er") }
            },
            dismissButton = {
                TextButton(onClick = { showWaypointDialog = false }) { Text("Annuler") }
            }
        )
    }

    // Waypoint action dialog (move / delete)
    waypointToAct?.let { wp ->
        AlertDialog(
            onDismissRequest = { waypointToAct = null },
            title = { Text(wp.name) },
            text = {
                Text("${formatCoordinate(wp.latitude, true)} ${formatCoordinate(wp.longitude, false)}")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWaypoint(wp)
                    waypointToAct = null
                }) { Text("Supprimer") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        waypointToMove = wp
                        waypointToAct = null
                    }) { Text("D\u00e9placer") }
                    TextButton(onClick = { waypointToAct = null }) { Text("Fermer") }
                }
            }
        )
    }

    // Route creation dialog
    if (showRouteCreation) {
        AlertDialog(
            onDismissRequest = { showRouteCreation = false },
            title = { Text("Nouvelle route") },
            text = {
                Column {
                    TextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        label = { Text("Nom de la route") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "S\u00e9lectionner les waypoints (dans l'ordre) :",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    LazyColumn {
                        items(waypoints) { wp ->
                            val index = selectedWaypointIds.indexOf(wp.id)
                            val selected = index >= 0
                            Text(
                                text = if (selected) "${index + 1}. ${wp.name}" else wp.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selected) selectedWaypointIds.remove(wp.id)
                                        else selectedWaypointIds.add(wp.id)
                                    }
                                    .background(
                                        if (selected) Color(0xFF1B3A5C).copy(alpha = 0.15f)
                                        else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(12.dp),
                                color = if (selected) Color(0xFF1B3A5C) else Color.Unspecified
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (routeName.isNotBlank() && selectedWaypointIds.size >= 2) {
                            viewModel.createRoute(routeName.trim(), selectedWaypointIds.toList())
                            showRouteCreation = false
                        }
                    }
                ) { Text("Cr\u00e9er") }
            },
            dismissButton = {
                TextButton(onClick = { showRouteCreation = false }) { Text("Annuler") }
            }
        )
    }

    // Route list dialog
    if (showRouteList) {
        AlertDialog(
            onDismissRequest = { showRouteList = false },
            title = { Text("Routes") },
            text = {
                Column {
                    if (waypoints.size >= 2) {
                        TextButton(onClick = {
                            showRouteList = false
                            routeName = ""
                            selectedWaypointIds.clear()
                            showRouteCreation = true
                        }) { Text("+ Nouvelle route") }
                    }
                    routes.forEach { route ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.activateRoute(route)
                                    showRouteList = false
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                route.name,
                                color = if (activeRoute?.id == route.id) Color(0xFFE63946) else Color.Unspecified
                            )
                            TextButton(onClick = {
                                viewModel.deleteRoute(route)
                            }) { Text("X") }
                        }
                    }
                    if (routes.isEmpty()) {
                        Text("Aucune route", modifier = Modifier.padding(12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRouteList = false }) { Text("Fermer") }
            }
        )
    }
}

@Composable
private fun NavigationOverlay(
    latitude: Double, longitude: Double, sog: Float, cog: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
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
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = Color.White)
    }
}

private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees, source.width / 2f, source.height / 2f) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun formatCoordinate(value: Double, isLatitude: Boolean): String {
    val direction = if (isLatitude) { if (value >= 0) "N" else "S" }
    else { if (value >= 0) "E" else "W" }
    val abs = Math.abs(value)
    val degrees = abs.toInt()
    val minutes = (abs - degrees) * 60
    return String.format(Locale.US, "%d\u00B0%05.2f'%s", degrees, minutes, direction)
}
