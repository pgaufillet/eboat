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
import com.eboat.domain.model.GuidanceState
import com.eboat.domain.model.Route
import com.eboat.domain.model.Waypoint
import com.eboat.domain.model.AnchorState
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.PolygonOptions
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
    val guidance by viewModel.guidance.collectAsState()
    val activeRouteWaypoints by viewModel.activeRouteWaypoints.collectAsState()
    val alertZones by viewModel.alertZones.collectAsState()
    val triggeredZones by viewModel.triggeredZones.collectAsState()
    val anchorState by viewModel.anchorState.collectAsState()
    val offlineRegions by viewModel.offlineRegions.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
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

    // Alert zones
    var showAlertZoneDialog by remember { mutableStateOf(false) }
    var zoneCreationMode by remember { mutableStateOf(false) }
    val zoneCreationPoints = remember { mutableStateListOf<LatLng>() }
    var zoneAlertOnEntry by remember { mutableStateOf(true) }
    var zoneName by remember { mutableStateOf("") }
    val alertZonePolygons = remember { mutableMapOf<Long, org.maplibre.android.annotations.Polygon>() }

    // Anchor
    var showAnchorDialog by remember { mutableStateOf(false) }
    var anchorCircle by remember { mutableStateOf<org.maplibre.android.annotations.Polygon?>(null) }

    // Offline dialog
    var showOfflineDialog by remember { mutableStateOf(false) }
    var offlineRegionName by remember { mutableStateOf("") }

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

    // Draw anchor circle
    LaunchedEffect(anchorState.active, anchorState.latitude, anchorState.longitude, anchorState.radiusMeters, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        anchorCircle?.let { map.removePolygon(it) }
        if (anchorState.active) {
            val center = LatLng(anchorState.latitude, anchorState.longitude)
            val radiusDeg = anchorState.radiusMeters / 111_320.0
            val points = (0..72).map { i ->
                val angle = Math.toRadians(i * 5.0)
                LatLng(
                    center.latitude + radiusDeg * Math.cos(angle),
                    center.longitude + radiusDeg * Math.sin(angle) / Math.cos(Math.toRadians(center.latitude))
                )
            }
            val fillColor = if (anchorState.isDragging)
                android.graphics.Color.argb(60, 230, 57, 70)
            else
                android.graphics.Color.argb(40, 0, 119, 182)
            val strokeColor = if (anchorState.isDragging)
                android.graphics.Color.parseColor("#E63946")
            else
                android.graphics.Color.parseColor("#0077B6")
            anchorCircle = map.addPolygon(
                PolygonOptions()
                    .addAll(points)
                    .fillColor(fillColor)
                    .strokeColor(strokeColor)
            )
        } else {
            anchorCircle = null
        }
    }

    // Draw alert zones
    LaunchedEffect(alertZones, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val currentIds = alertZones.map { it.id }.toSet()
        alertZonePolygons.keys.filter { it !in currentIds }.forEach { id ->
            alertZonePolygons.remove(id)?.let { map.removePolygon(it) }
        }
        alertZones.forEach { zone ->
            if (zone.id !in alertZonePolygons) {
                val pts = zone.decodePoints().map { LatLng(it.first, it.second) }
                if (pts.size >= 3) {
                    val isTriggered = zone.id in triggeredZones
                    val fill = if (isTriggered) android.graphics.Color.argb(60, 230, 57, 70)
                        else if (zone.alertOnEntry) android.graphics.Color.argb(30, 230, 57, 70)
                        else android.graphics.Color.argb(30, 46, 125, 50)
                    val stroke = if (isTriggered) android.graphics.Color.parseColor("#E63946")
                        else if (zone.alertOnEntry) android.graphics.Color.parseColor("#E63946")
                        else android.graphics.Color.parseColor("#2E7D32")
                    alertZonePolygons[zone.id] = map.addPolygon(
                        PolygonOptions().addAll(pts).fillColor(fill).strokeColor(stroke)
                    )
                }
            }
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
                            if (zoneCreationMode) {
                                zoneCreationPoints.add(latLng)
                                return@addOnMapLongClickListener true
                            }
                            if (waypointToMove != null) {
                                viewModel.moveWaypoint(waypointToMove!!, latLng.latitude, latLng.longitude)
                                waypointToMove = null
                            } else {
                                // Check if near an existing waypoint (tap tolerance)
                                val nearWp = waypoints.minByOrNull {
                                    com.eboat.domain.navigation.distanceNm(
                                        latLng.latitude, latLng.longitude,
                                        it.latitude, it.longitude
                                    )
                                }
                                val nearDist = nearWp?.let {
                                    com.eboat.domain.navigation.distanceNm(
                                        latLng.latitude, latLng.longitude,
                                        it.latitude, it.longitude
                                    )
                                }
                                // If within ~200m of a waypoint, open action menu instead
                                if (nearWp != null && nearDist != null && nearDist < 0.1) {
                                    waypointToAct = nearWp
                                } else {
                                    pendingWaypointLatLng = latLng
                                    waypointName = ""
                                    showWaypointDialog = true
                                }
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

        // Download progress bar
        downloadProgress?.let { progress ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (progress.error != null) {
                    Text("Erreur: ${progress.error}", color = Color(0xFFE63946), style = MaterialTheme.typography.bodySmall)
                } else {
                    val pctText = if (progress.percent >= 0) "${progress.percent}%" else "..."
                    val sizeKb = progress.completedBytes / 1024
                    Text("T\u00e9l\u00e9chargement $pctText ($sizeKb Ko)", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    if (progress.percent >= 0) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress.percent / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = Color(0xFF0077B6)
                        )
                    }
                }
            }
        }

        // Zone creation banner
        if (zoneCreationMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Color(0xFFFF9800), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Appui long pour ajouter des points (${zoneCreationPoints.size})",
                    color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Row {
                    if (zoneCreationPoints.size >= 3) {
                        TextButton(onClick = {
                            val pts = zoneCreationPoints.map { it.latitude to it.longitude }
                            viewModel.addAlertZone(zoneName.ifBlank { "Zone" }, pts, zoneAlertOnEntry)
                            zoneCreationMode = false
                            zoneCreationPoints.clear()
                        }) { Text("Valider", color = Color.White) }
                    }
                    TextButton(onClick = {
                        zoneCreationMode = false
                        zoneCreationPoints.clear()
                    }) { Text("Annuler", color = Color.White) }
                }
            }
        }

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

        // Guidance + Navigation overlays
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (anchorState.active) {
                val bgColor = if (anchorState.isDragging) Color(0xFFE63946) else Color(0xFF2E7D32)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NavDataItem("\u2693", if (anchorState.isDragging) "ALARME" else "OK")
                    NavDataItem("DIST", String.format(Locale.US, "%.0f m", anchorState.currentDistanceMeters))
                    NavDataItem("RAYON", "${anchorState.radiusMeters} m")
                }
            }
            if (guidance.active && !guidance.routeComplete) {
                GuidanceOverlay(guidance = guidance)
            }
            if (guidance.routeComplete) {
                Text(
                    "Route termin\u00e9e",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2E7D32), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            if (boatState.hasPosition) {
                NavigationOverlay(
                    latitude = boatState.latitude,
                    longitude = boatState.longitude,
                    sog = boatState.speedOverGround,
                    cog = boatState.courseOverGround
                )
            }
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
            // Offline button
            FloatingActionButton(
                onClick = {
                    viewModel.refreshOfflineRegions()
                    showOfflineDialog = true
                },
                containerColor = Color(0xFF0077B6)
            ) { Text("\u2193", color = Color.White) }
            // Alert zone button
            FloatingActionButton(
                onClick = { showAlertZoneDialog = true },
                containerColor = Color(0xFFFF9800)
            ) { Text("Z", color = Color.White) }
            // Anchor button
            if (boatState.hasPosition) {
                FloatingActionButton(
                    onClick = { showAnchorDialog = true },
                    containerColor = if (anchorState.active) {
                        if (anchorState.isDragging) Color(0xFFE63946) else Color(0xFF2E7D32)
                    } else Color(0xFF666666)
                ) { Text("\u2693", color = Color.White) }
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

    // Offline dialog
    if (showOfflineDialog) {
        var confirmClear by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showOfflineDialog = false },
            title = { Text("Cartes hors-ligne") },
            text = {
                Column {
                    // Download current view
                    Text(
                        "T\u00e9l\u00e9charger la zone visible :",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    TextField(
                        value = offlineRegionName,
                        onValueChange = { offlineRegionName = it },
                        label = { Text("Nom de la zone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = {
                            val map = mapLibreMap ?: return@TextButton
                            val bounds = map.projection.visibleRegion.latLngBounds
                            val zoom = map.cameraPosition.zoom
                            if (offlineRegionName.isNotBlank()) {
                                viewModel.downloadRegion(
                                    name = offlineRegionName.trim(),
                                    styleUrl = "asset://map_style.json",
                                    bounds = bounds,
                                    minZoom = (zoom - 2).coerceAtLeast(0.0),
                                    maxZoom = (zoom + 4).coerceAtMost(18.0)
                                )
                                showOfflineDialog = false
                            }
                        }
                    ) { Text("T\u00e9l\u00e9charger") }

                    // Saved regions
                    if (offlineRegions.isNotEmpty()) {
                        Text(
                            "Zones sauvegard\u00e9es :",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        offlineRegions.forEach { region ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(region.name, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    viewModel.deleteOfflineRegion(region.id)
                                }) { Text("X") }
                            }
                        }
                    }

                    // Clear cache
                    if (offlineRegions.isNotEmpty()) {
                        if (!confirmClear) {
                            TextButton(
                                onClick = { confirmClear = true },
                                modifier = Modifier.padding(top = 8.dp)
                            ) { Text("Vider le cache", color = Color(0xFFE63946)) }
                        } else {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Supprimer toutes les zones ?", color = Color(0xFFE63946),
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = {
                                    viewModel.clearAllOfflineRegions()
                                    confirmClear = false
                                }) { Text("Confirmer", color = Color(0xFFE63946)) }
                                TextButton(onClick = { confirmClear = false }) { Text("Annuler") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOfflineDialog = false }) { Text("Fermer") }
            }
        )
    }

    // Alert zone dialog
    if (showAlertZoneDialog) {
        AlertDialog(
            onDismissRequest = { showAlertZoneDialog = false },
            title = { Text("Zones d'alerte") },
            text = {
                Column {
                    TextButton(onClick = {
                        showAlertZoneDialog = false
                        zoneName = ""
                        zoneAlertOnEntry = true
                        zoneCreationPoints.clear()
                        zoneCreationMode = true
                    }) { Text("+ Nouvelle zone (entr\u00e9e)") }
                    TextButton(onClick = {
                        showAlertZoneDialog = false
                        zoneName = ""
                        zoneAlertOnEntry = false
                        zoneCreationPoints.clear()
                        zoneCreationMode = true
                    }) { Text("+ Nouvelle zone (sortie)") }

                    alertZones.forEach { zone ->
                        val isTriggered = zone.id in triggeredZones
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    zone.name,
                                    color = if (isTriggered) Color(0xFFE63946) else Color.Unspecified
                                )
                                Text(
                                    if (zone.alertOnEntry) "Alerte entr\u00e9e" else "Alerte sortie",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            if (isTriggered) {
                                TextButton(onClick = {
                                    viewModel.clearTriggeredZone(zone.id)
                                    com.eboat.service.AnchorAlarmService.clearAlarm(context)
                                }) { Text("OK") }
                            }
                            TextButton(onClick = { viewModel.deleteAlertZone(zone) }) { Text("X") }
                        }
                    }
                    if (alertZones.isEmpty()) {
                        Text("Aucune zone d'alerte", modifier = Modifier.padding(12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAlertZoneDialog = false }) { Text("Fermer") }
            }
        )
    }

    // Anchor dialog
    if (showAnchorDialog) {
        var sliderRadius by remember { mutableStateOf(anchorState.radiusMeters.toFloat()) }
        AlertDialog(
            onDismissRequest = { showAnchorDialog = false },
            title = { Text(if (anchorState.active) "Veille de mouillage" else "Mouiller l'ancre") },
            text = {
                Column {
                    if (!anchorState.active) {
                        Text("Rayon d'alarme : ${sliderRadius.toInt()} m")
                        androidx.compose.material3.Slider(
                            value = sliderRadius,
                            onValueChange = { sliderRadius = it },
                            valueRange = 20f..300f,
                            steps = 27
                        )
                        Text(
                            "L'alarme se d\u00e9clenchera si le bateau d\u00e9rive au-del\u00e0 de ce rayon.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    } else {
                        Text("Distance : ${String.format(Locale.US, "%.0f m", anchorState.currentDistanceMeters)}")
                        Text("Rayon : ${anchorState.radiusMeters} m")
                        if (anchorState.isDragging) {
                            Text("LE BATEAU D\u00c9RIVE !", color = Color(0xFFE63946),
                                style = MaterialTheme.typography.titleMedium)
                        }
                        Text("\nAjuster le rayon :", modifier = Modifier.padding(top = 8.dp))
                        sliderRadius = anchorState.radiusMeters.toFloat()
                        androidx.compose.material3.Slider(
                            value = sliderRadius,
                            onValueChange = {
                                sliderRadius = it
                                viewModel.setAnchorRadius(it.toInt())
                            },
                            valueRange = 20f..300f,
                            steps = 27
                        )
                    }
                }
            },
            confirmButton = {
                if (!anchorState.active) {
                    TextButton(onClick = {
                        viewModel.dropAnchor(sliderRadius.toInt())
                        showAnchorDialog = false
                    }) { Text("Mouiller") }
                } else {
                    TextButton(onClick = {
                        viewModel.liftAnchor()
                        showAnchorDialog = false
                    }) { Text("Lever l'ancre", color = Color(0xFFE63946)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnchorDialog = false }) { Text("Fermer") }
            }
        )
    }
}

@Composable
private fun GuidanceOverlay(guidance: GuidanceState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B3A5C).copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavDataItem("WPT", "${guidance.nextWaypointIndex + 1}/${guidance.totalWaypoints}\n${guidance.nextWaypointName}")
        NavDataItem("BRG", String.format(Locale.US, "%.0f\u00B0", guidance.bearingToWaypoint))
        NavDataItem("DST", String.format(Locale.US, "%.2f nm", guidance.distanceToWaypointNm))
        NavDataItem("XTE", String.format(Locale.US, "%.2f nm", guidance.crossTrackNm))
        val etaText = guidance.etaSeconds?.let { secs ->
            val h = secs / 3600
            val m = (secs % 3600) / 60
            if (h > 0) String.format(Locale.US, "%dh%02dm", h, m)
            else String.format(Locale.US, "%dm", m)
        } ?: "--"
        NavDataItem("ETA", etaText)
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
