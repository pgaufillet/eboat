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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import com.eboat.domain.model.WeatherLayerType
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
    val tideData by viewModel.tideData.collectAsState()
    val alertZones by viewModel.alertZones.collectAsState()
    val triggeredZones by viewModel.triggeredZones.collectAsState()
    val anchorState by viewModel.anchorState.collectAsState()
    val offlineRegions by viewModel.offlineRegions.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var boatMarker by remember { mutableStateOf<Marker?>(null) }
    val waypointMarkers = remember { mutableMapOf<Long, Marker>() }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var activeLegPolyline by remember { mutableStateOf<Polyline?>(null) }

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

    // Menu
    var showMenu by remember { mutableStateOf(false) }

    // Weather
    val weatherData by viewModel.weatherData.collectAsState()
    val weatherGrid by viewModel.weatherGrid.collectAsState()
    val weatherLayers by viewModel.weatherLayers.collectAsState()
    var showWeatherDialog by remember { mutableStateOf(false) }
    var weatherLayersInitialized by remember { mutableStateOf(false) }

    // AIS
    val aisTargets by viewModel.aisTargets.collectAsState()
    val aisConnected by viewModel.aisConnected.collectAsState()
    var showAisDialog by remember { mutableStateOf(false) }
    var aisHost by remember { mutableStateOf("192.168.1.1") }
    var aisPort by remember { mutableStateOf("10110") }

    // Trip log
    val tripRecording by viewModel.tripRecording.collectAsState()
    val tripIds by viewModel.tripIds.collectAsState()
    var showTripDialog by remember { mutableStateOf(false) }

    // Help
    var showHelp by remember { mutableStateOf(false) }

    // Tide dialog
    var showTideDialog by remember { mutableStateOf(false) }

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

    // Draw active leg: boat position to next waypoint
    LaunchedEffect(guidance.active, guidance.nextWaypointIndex, boatState.latitude, boatState.longitude, activeRouteWaypoints, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        activeLegPolyline?.let { map.removePolyline(it) }
        activeLegPolyline = null
        if (guidance.active && !guidance.routeComplete && boatState.hasPosition && activeRouteWaypoints.isNotEmpty()) {
            val idx = guidance.nextWaypointIndex.coerceIn(0, activeRouteWaypoints.size - 1)
            val target = activeRouteWaypoints[idx]
            activeLegPolyline = map.addPolyline(
                PolylineOptions()
                    .add(LatLng(boatState.latitude, boatState.longitude))
                    .add(LatLng(target.latitude, target.longitude))
                    .color(android.graphics.Color.argb(140, 230, 57, 70))
                    .width(3f)
            )
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

    // Weather overlay layers
    LaunchedEffect(weatherGrid, weatherLayers, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        map.getStyle { style ->
            // Build wave height cells as GeoJSON
            val now = System.currentTimeMillis()
            val cellSizeDeg = 0.08 // ~5 NM at mid-latitudes

            if (weatherLayers.isNotEmpty() && weatherGrid.isNotEmpty()) {
                val waveFeatures = mutableListOf<org.maplibre.geojson.Feature>()
                val windFeatures = mutableListOf<org.maplibre.geojson.Feature>()
                val swellFeatures = mutableListOf<org.maplibre.geojson.Feature>()
                val pressureFeatures = mutableListOf<org.maplibre.geojson.Feature>()

                for (wd in weatherGrid) {
                    val forecast = wd.forecasts.firstOrNull { it.time >= now } ?: wd.forecasts.lastOrNull() ?: continue
                    val lat = wd.latitude
                    val lon = wd.longitude

                    // Cell polygon
                    val cellCoords = listOf(
                        org.maplibre.geojson.Point.fromLngLat(lon - cellSizeDeg, lat - cellSizeDeg),
                        org.maplibre.geojson.Point.fromLngLat(lon + cellSizeDeg, lat - cellSizeDeg),
                        org.maplibre.geojson.Point.fromLngLat(lon + cellSizeDeg, lat + cellSizeDeg),
                        org.maplibre.geojson.Point.fromLngLat(lon - cellSizeDeg, lat + cellSizeDeg),
                        org.maplibre.geojson.Point.fromLngLat(lon - cellSizeDeg, lat - cellSizeDeg)
                    )
                    val polygon = org.maplibre.geojson.Polygon.fromLngLats(listOf(cellCoords))
                    val point = org.maplibre.geojson.Point.fromLngLat(lon, lat)

                    if (WeatherLayerType.WAVE_HEIGHT in weatherLayers) {
                        val f = org.maplibre.geojson.Feature.fromGeometry(polygon)
                        f.addNumberProperty("waveHeight", forecast.waveHeightM)
                        waveFeatures.add(f)
                    }
                    if (WeatherLayerType.WIND in weatherLayers) {
                        val f = org.maplibre.geojson.Feature.fromGeometry(point)
                        f.addNumberProperty("windSpeed", forecast.windSpeedKnots)
                        f.addNumberProperty("windDirection", forecast.windDirectionDeg)
                        f.addStringProperty("windLabel", String.format(java.util.Locale.US, "%.0f", forecast.windSpeedKnots))
                        windFeatures.add(f)
                    }
                    if (WeatherLayerType.SWELL in weatherLayers) {
                        val f = org.maplibre.geojson.Feature.fromGeometry(point)
                        f.addNumberProperty("swellHeight", forecast.swellHeightM)
                        f.addNumberProperty("swellDirection", forecast.swellDirectionDeg)
                        f.addStringProperty("swellLabel", String.format(java.util.Locale.US, "%.1fm %ds", forecast.swellHeightM, forecast.swellPeriodS.toInt()))
                        swellFeatures.add(f)
                    }
                    if (WeatherLayerType.PRESSURE in weatherLayers) {
                        val f = org.maplibre.geojson.Feature.fromGeometry(polygon)
                        f.addNumberProperty("pressure", forecast.pressureHpa)
                        pressureFeatures.add(f)
                    }
                }

                // Register images if needed
                if (!weatherLayersInitialized) {
                    val windBmp = ContextCompat.getDrawable(context, R.drawable.ic_wind_arrow)!!.toBitmap(48, 48)
                    val swellBmp = ContextCompat.getDrawable(context, R.drawable.ic_swell_arrow)!!.toBitmap(48, 48)
                    style.addImage("wind-arrow", windBmp)
                    style.addImage("swell-arrow", swellBmp)
                    weatherLayersInitialized = true
                }

                // Update or create sources
                fun updateOrCreateSource(id: String, fc: org.maplibre.geojson.FeatureCollection) {
                    val existing = style.getSource(id) as? org.maplibre.android.style.sources.GeoJsonSource
                    if (existing != null) {
                        existing.setGeoJson(fc)
                    } else {
                        style.addSource(org.maplibre.android.style.sources.GeoJsonSource(id, fc))
                    }
                }

                // Wave height fill layer
                if (waveFeatures.isNotEmpty()) {
                    updateOrCreateSource("weather-wave-src", org.maplibre.geojson.FeatureCollection.fromFeatures(waveFeatures))
                    if (style.getLayer("weather-wave-layer") == null) {
                        val layer = org.maplibre.android.style.layers.FillLayer("weather-wave-layer", "weather-wave-src")
                        layer.setProperties(
                            org.maplibre.android.style.layers.PropertyFactory.fillColor(
                                org.maplibre.android.style.expressions.Expression.interpolate(
                                    org.maplibre.android.style.expressions.Expression.linear(),
                                    org.maplibre.android.style.expressions.Expression.get("waveHeight"),
                                    org.maplibre.android.style.expressions.Expression.stop(0f, org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#90CAF9"))),
                                    org.maplibre.android.style.expressions.Expression.stop(1f, org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#1565C0"))),
                                    org.maplibre.android.style.expressions.Expression.stop(2f, org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#FF9800"))),
                                    org.maplibre.android.style.expressions.Expression.stop(3f, org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#E63946")))
                                )
                            ),
                            org.maplibre.android.style.layers.PropertyFactory.fillOpacity(0.3f)
                        )
                        style.addLayerBelow(layer, "openseamap-overlay")
                    }
                }

                // Wind symbol layer
                if (windFeatures.isNotEmpty()) {
                    updateOrCreateSource("weather-wind-src", org.maplibre.geojson.FeatureCollection.fromFeatures(windFeatures))
                    if (style.getLayer("weather-wind-layer") == null) {
                        val layer = org.maplibre.android.style.layers.SymbolLayer("weather-wind-layer", "weather-wind-src")
                        layer.setProperties(
                            org.maplibre.android.style.layers.PropertyFactory.iconImage("wind-arrow"),
                            org.maplibre.android.style.layers.PropertyFactory.iconRotate(org.maplibre.android.style.expressions.Expression.get("windDirection")),
                            org.maplibre.android.style.layers.PropertyFactory.iconSize(0.7f),
                            org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                            org.maplibre.android.style.layers.PropertyFactory.textField(org.maplibre.android.style.expressions.Expression.get("windLabel")),
                            org.maplibre.android.style.layers.PropertyFactory.textSize(10f),
                            org.maplibre.android.style.layers.PropertyFactory.textColor(android.graphics.Color.WHITE),
                            org.maplibre.android.style.layers.PropertyFactory.textHaloColor(android.graphics.Color.BLACK),
                            org.maplibre.android.style.layers.PropertyFactory.textHaloWidth(1.5f),
                            org.maplibre.android.style.layers.PropertyFactory.textOffset(arrayOf(0f, 2f)),
                            org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap(true)
                        )
                        style.addLayer(layer)
                    }
                }

                // Swell symbol layer
                if (swellFeatures.isNotEmpty()) {
                    updateOrCreateSource("weather-swell-src", org.maplibre.geojson.FeatureCollection.fromFeatures(swellFeatures))
                    if (style.getLayer("weather-swell-layer") == null) {
                        val layer = org.maplibre.android.style.layers.SymbolLayer("weather-swell-layer", "weather-swell-src")
                        layer.setProperties(
                            org.maplibre.android.style.layers.PropertyFactory.iconImage("swell-arrow"),
                            org.maplibre.android.style.layers.PropertyFactory.iconRotate(org.maplibre.android.style.expressions.Expression.get("swellDirection")),
                            org.maplibre.android.style.layers.PropertyFactory.iconSize(0.6f),
                            org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                            org.maplibre.android.style.layers.PropertyFactory.textField(org.maplibre.android.style.expressions.Expression.get("swellLabel")),
                            org.maplibre.android.style.layers.PropertyFactory.textSize(9f),
                            org.maplibre.android.style.layers.PropertyFactory.textColor(android.graphics.Color.parseColor("#0077B6")),
                            org.maplibre.android.style.layers.PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                            org.maplibre.android.style.layers.PropertyFactory.textHaloWidth(1.5f),
                            org.maplibre.android.style.layers.PropertyFactory.textOffset(arrayOf(0f, 2f)),
                            org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap(true)
                        )
                        style.addLayer(layer)
                    }
                }

                // Pressure fill layer
                if (pressureFeatures.isNotEmpty()) {
                    updateOrCreateSource("weather-pressure-src", org.maplibre.geojson.FeatureCollection.fromFeatures(pressureFeatures))
                    if (style.getLayer("weather-pressure-layer") == null) {
                        val layer = org.maplibre.android.style.layers.FillLayer("weather-pressure-layer", "weather-pressure-src")
                        layer.setProperties(
                            org.maplibre.android.style.layers.PropertyFactory.fillColor(
                                org.maplibre.android.style.expressions.Expression.interpolate(
                                    org.maplibre.android.style.expressions.Expression.linear(),
                                    org.maplibre.android.style.expressions.Expression.get("pressure"),
                                    org.maplibre.android.style.expressions.Expression.stop(1000f, org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#E63946"))),
                                    org.maplibre.android.style.expressions.Expression.stop(1013f, org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#FFC107"))),
                                    org.maplibre.android.style.expressions.Expression.stop(1025f, org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#4CAF50")))
                                )
                            ),
                            org.maplibre.android.style.layers.PropertyFactory.fillOpacity(0.2f)
                        )
                        style.addLayerBelow(layer, "openseamap-overlay")
                    }
                }

                // Toggle visibility for all layers
                for (type in WeatherLayerType.values()) {
                    val layerId = when (type) {
                        WeatherLayerType.WIND -> "weather-wind-layer"
                        WeatherLayerType.WAVE_HEIGHT -> "weather-wave-layer"
                        WeatherLayerType.SWELL -> "weather-swell-layer"
                        WeatherLayerType.PRESSURE -> "weather-pressure-layer"
                    }
                    style.getLayer(layerId)?.setProperties(
                        org.maplibre.android.style.layers.PropertyFactory.visibility(
                            if (type in weatherLayers) org.maplibre.android.style.layers.Property.VISIBLE
                            else org.maplibre.android.style.layers.Property.NONE
                        )
                    )
                }
            } else {
                // Hide all weather layers
                for (layerId in listOf("weather-wave-layer", "weather-wind-layer", "weather-swell-layer", "weather-pressure-layer")) {
                    style.getLayer(layerId)?.setProperties(
                        org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE)
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
                        map.uiSettings.compassGravity = android.view.Gravity.TOP or android.view.Gravity.START
                        map.uiSettings.setCompassMargins(40, 140, 0, 0)
                        map.uiSettings.setCompassFadeFacingNorth(false)
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

        // Weather legend (bottom-left)
        if (weatherLayers.isNotEmpty() && weatherGrid.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 16.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (WeatherLayerType.WIND in weatherLayers) {
                    Text("Vent (kn)", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                if (WeatherLayerType.WAVE_HEIGHT in weatherLayers) {
                    Row {
                        listOf(
                            Color(0xFF90CAF9) to "0",
                            Color(0xFF1565C0) to "1m",
                            Color(0xFFFF9800) to "2m",
                            Color(0xFFE63946) to "3m+"
                        ).forEach { (color, label) ->
                            Box(modifier = Modifier.size(12.dp).background(color))
                            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
                if (WeatherLayerType.SWELL in weatherLayers) {
                    Text("Houle (m/s)", color = Color(0xFF0077B6), style = MaterialTheme.typography.labelSmall)
                }
                if (WeatherLayerType.PRESSURE in weatherLayers) {
                    Text("Pression (hPa)", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
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

        // Active route name — positioned below compass (top-left, offset down)
        activeRoute?.let { route ->
            Text(
                "\u25C0 ${route.name}",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 110.dp, start = 16.dp)
                    .background(Color(0xFF1B3A5C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { viewModel.deactivateRoute() },
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Menu button (top-right) + center on boat
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                ) { Text("\u2295", color = Color.White) }
            }
            // Anchor quick-access (colored by state)
            if (boatState.hasPosition) {
                FloatingActionButton(
                    onClick = { showAnchorDialog = true },
                    containerColor = if (anchorState.active) {
                        if (anchorState.isDragging) Color(0xFFE63946) else Color(0xFF2E7D32)
                    } else Color(0xFF666666),
                    modifier = Modifier.size(40.dp)
                ) { Text("\u2693", color = Color.White, style = MaterialTheme.typography.bodySmall) }
            }
            // Menu hamburger
            FloatingActionButton(
                onClick = { showMenu = true },
                containerColor = Color(0xFF1B3A5C),
                modifier = Modifier.size(40.dp)
            ) { Text("\u2261", color = Color.White) }
        }
    }

    // Create waypoint dialog
    if (showWaypointDialog) {
        var wpLat by remember { mutableStateOf(pendingWaypointLatLng?.let {
            formatCoordinate(it.latitude, true) } ?: "") }
        var wpLon by remember { mutableStateOf(pendingWaypointLatLng?.let {
            formatCoordinate(it.longitude, false) } ?: "") }
        var manualCoords by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showWaypointDialog = false },
            title = { Text("Nouveau waypoint") },
            text = {
                Column {
                    TextField(
                        value = waypointName,
                        onValueChange = { waypointName = it },
                        label = { Text("Nom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { manualCoords = !manualCoords }) {
                        Text(if (manualCoords) "Utiliser position carte" else "Saisir coordonn\u00e9es")
                    }
                    if (manualCoords) {
                        TextField(
                            value = wpLat,
                            onValueChange = { wpLat = it },
                            label = { Text("Latitude (ex: 46.1500)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = wpLon,
                            onValueChange = { wpLon = it },
                            label = { Text("Longitude (ex: -1.1500)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (waypointName.isNotBlank()) {
                        if (manualCoords) {
                            val lat = wpLat.toDoubleOrNull()
                            val lon = wpLon.toDoubleOrNull()
                            if (lat != null && lon != null) {
                                viewModel.addWaypoint(waypointName.trim(), lat, lon)
                            }
                        } else {
                            val latLng = pendingWaypointLatLng
                            if (latLng != null) {
                                viewModel.addWaypoint(waypointName.trim(), latLng.latitude, latLng.longitude)
                            }
                        }
                    }
                    showWaypointDialog = false
                }) { Text("Cr\u00e9er") }
            },
            dismissButton = {
                TextButton(onClick = { showWaypointDialog = false }) { Text("Annuler") }
            }
        )
    }

    // Waypoint action dialog (move / edit coords / delete)
    waypointToAct?.let { wp ->
        var editCoords by remember { mutableStateOf(false) }
        var editLat by remember { mutableStateOf(wp.latitude.toString()) }
        var editLon by remember { mutableStateOf(wp.longitude.toString()) }

        AlertDialog(
            onDismissRequest = { waypointToAct = null },
            title = { Text(wp.name) },
            text = {
                Column {
                    if (!editCoords) {
                        Text("${formatCoordinate(wp.latitude, true)} ${formatCoordinate(wp.longitude, false)}")
                    } else {
                        TextField(value = editLat, onValueChange = { editLat = it },
                            label = { Text("Latitude") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        TextField(value = editLon, onValueChange = { editLon = it },
                            label = { Text("Longitude") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        TextButton(onClick = {
                            val lat = editLat.toDoubleOrNull()
                            val lon = editLon.toDoubleOrNull()
                            if (lat != null && lon != null) {
                                viewModel.moveWaypoint(wp, lat, lon)
                                waypointToAct = null
                            }
                        }) { Text("Valider") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWaypoint(wp)
                    waypointToAct = null
                }) { Text("Supprimer", color = Color(0xFFE63946)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        waypointToMove = wp
                        waypointToAct = null
                    }) { Text("D\u00e9placer") }
                    TextButton(onClick = {
                        editCoords = !editCoords
                        editLat = wp.latitude.toString()
                        editLon = wp.longitude.toString()
                    }) { Text("Coords") }
                    TextButton(onClick = {
                        viewModel.fetchWeather(wp.latitude, wp.longitude)
                        waypointToAct = null
                        showWeatherDialog = true
                    }) { Text("M\u00e9t\u00e9o") }
                    TextButton(onClick = {
                        viewModel.fetchTides(wp.latitude, wp.longitude)
                        waypointToAct = null
                        showTideDialog = true
                    }) { Text("Mar\u00e9es") }
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

    // Tide dialog
    if (showTideDialog) {
        AlertDialog(
            onDismissRequest = { showTideDialog = false },
            title = { Text("Mar\u00e9es") },
            text = {
                Column {
                    val data = tideData
                    if (data == null) {
                        Text("Chargement...")
                    } else if (data.highLow.isEmpty()) {
                        Text("Pas de donn\u00e9es de mar\u00e9es pour cette position")
                    } else {
                        Text("Position: ${data.stationName}",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        val timeFormat = java.text.SimpleDateFormat("EEE HH:mm", Locale.getDefault())
                        data.highLow.forEach { extreme ->
                            val icon = if (extreme.isHigh) "\u25B2" else "\u25BC"
                            val label = if (extreme.isHigh) "PM" else "BM"
                            val color = if (extreme.isHigh) Color(0xFF0077B6) else Color(0xFF2E7D32)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$icon $label", color = color)
                                Text(timeFormat.format(java.util.Date(extreme.time)))
                                Text(String.format(Locale.US, "%.1f m", extreme.height))
                            }
                        }
                    }

                    // Quick access to tides at waypoints
                    if (waypoints.isNotEmpty()) {
                        Text("Mar\u00e9es aux waypoints :",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        waypoints.take(5).forEach { wp ->
                            TextButton(onClick = {
                                viewModel.fetchTides(wp.latitude, wp.longitude)
                            }) { Text(wp.name, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTideDialog = false }) { Text("Fermer") }
            },
            dismissButton = {
                if (boatState.hasPosition) {
                    TextButton(onClick = { viewModel.fetchTidesAtBoat() }) {
                        Text("Position bateau")
                    }
                }
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

    // Main menu
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("Menu") },
            text = {
                Column {
                    val menuItems = listOf(
                        "Routes" to { showMenu = false; showRouteList = true },
                        "Cartes hors-ligne" to { showMenu = false; viewModel.refreshOfflineRegions(); showOfflineDialog = true },
                        "Mar\u00e9es" to { showMenu = false; viewModel.fetchTidesAtBoat(); showTideDialog = true },
                        "M\u00e9t\u00e9o" to { showMenu = false; viewModel.fetchWeatherAtBoat(); showWeatherDialog = true },
                        "Zones d'alerte" to { showMenu = false; showAlertZoneDialog = true },
                        "AIS" to { showMenu = false; showAisDialog = true },
                        "Journal de bord" to { showMenu = false; showTripDialog = true },
                        "Aide" to { showMenu = false; showHelp = true }
                    )
                    menuItems.forEach { (label, action) ->
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { action() }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenu = false }) { Text("Fermer") }
            }
        )
    }

    // Weather dialog
    if (showWeatherDialog) {
        AlertDialog(
            onDismissRequest = { showWeatherDialog = false },
            title = { Text("M\u00e9t\u00e9o marine") },
            text = {
                Column {
                    val data = weatherData
                    if (data == null) {
                        Text("Chargement...")
                    } else if (data.forecasts.isEmpty()) {
                        Text("Pas de donn\u00e9es m\u00e9t\u00e9o")
                    } else {
                        // Show position context
                        Text(
                            "${formatCoordinate(data.latitude, true)} ${formatCoordinate(data.longitude, false)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val timeFormat = java.text.SimpleDateFormat("EEE HH:mm", Locale.getDefault())
                        val now = System.currentTimeMillis()
                        val filtered = data.forecasts.filter { it.time >= now }.take(8)
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Heure", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("Vent", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("Raf.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                            Text("Vague", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                            Text("hPa", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                        }
                        filtered.forEach { f ->
                            val windDir = arrayOf("N","NE","E","SE","S","SW","W","NW")[(f.windDirectionDeg / 45).toInt() % 8]
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(timeFormat.format(java.util.Date(f.time)),
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(String.format(Locale.US, "%.0f kn %s", f.windSpeedKnots, windDir),
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(String.format(Locale.US, "%.0f", f.gustSpeedKnots),
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
                                Text(String.format(Locale.US, "%.1fm", f.waveHeightM),
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
                                Text(String.format(Locale.US, "%.0f", f.pressureHpa),
                                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.7f))
                            }
                        }
                    }

                    // Quick access to weather at waypoints
                    if (waypoints.isNotEmpty()) {
                        Text("M\u00e9t\u00e9o aux waypoints :",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        waypoints.take(5).forEach { wp ->
                            TextButton(onClick = {
                                viewModel.fetchWeather(wp.latitude, wp.longitude)
                            }) { Text(wp.name, style = MaterialTheme.typography.bodySmall) }
                        }
                    }

                    // Layer selector
                    Text("Couches carte :",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    WeatherLayerType.values().forEach { type ->
                        val checked = type in weatherLayers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleWeatherLayer(type) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.toggleWeatherLayer(type) }
                            )
                            Text(type.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        TextButton(onClick = { viewModel.fetchWeatherOverlay() }) {
                            Text("Charger overlay")
                        }
                        TextButton(onClick = { viewModel.clearWeatherLayers() }) {
                            Text("Masquer tout")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeatherDialog = false }) { Text("Fermer") }
            },
            dismissButton = {
                if (boatState.hasPosition) {
                    TextButton(onClick = { viewModel.fetchWeatherAtBoat() }) {
                        Text("Position bateau")
                    }
                }
            }
        )
    }

    // AIS dialog
    if (showAisDialog) {
        AlertDialog(
            onDismissRequest = { showAisDialog = false },
            title = { Text("AIS") },
            text = {
                Column {
                    if (!aisConnected) {
                        TextField(value = aisHost, onValueChange = { aisHost = it },
                            label = { Text("Adresse IP") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        TextField(value = aisPort, onValueChange = { aisPort = it },
                            label = { Text("Port") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        TextButton(onClick = {
                            viewModel.connectAis(aisHost, aisPort.toIntOrNull() ?: 10110)
                        }) { Text("Connecter") }
                    } else {
                        Text("Connect\u00e9 \u2022 ${aisTargets.size} cibles")
                        aisTargets.values.sortedByDescending { it.lastUpdate }.take(10).forEach { t ->
                            Text("${t.mmsi} ${t.name.ifBlank { "---" }} ${String.format(Locale.US, "%.1f kn", t.sog)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAisDialog = false }) { Text("Fermer") }
            }
        )
    }

    // Trip log dialog
    if (showTripDialog) {
        AlertDialog(
            onDismissRequest = { showTripDialog = false },
            title = { Text("Journal de bord") },
            text = {
                Column {
                    if (!tripRecording) {
                        TextButton(onClick = { viewModel.startTripRecording() }) {
                            Text("D\u00e9marrer l'enregistrement")
                        }
                    } else {
                        TextButton(onClick = { viewModel.stopTripRecording() }) {
                            Text("Arr\u00eater l'enregistrement", color = Color(0xFFE63946))
                        }
                    }
                    if (tripIds.isNotEmpty()) {
                        Text("Trajets enregistr\u00e9s :",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp))
                        tripIds.forEach { id ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("Trajet #$id")
                                TextButton(onClick = { viewModel.deleteTrip(id) }) { Text("X") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTripDialog = false }) { Text("Fermer") }
            }
        )
    }

    // Help dialog
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Aide") },
            text = {
                LazyColumn {
                    items(listOf(
                        "Appui long" to "Cr\u00e9er un waypoint (loin d'un WP existant) ou modifier un WP (pr\u00e8s d'un existant)",
                        "Tap sur marqueur" to "Voir les d\u00e9tails / supprimer / d\u00e9placer",
                        "\u2295 (recentrer)" to "Recentrer la carte sur le bateau",
                        "\u2693 (ancre)" to "Activer/d\u00e9sactiver l'alarme de mouillage",
                        "\u2261 (menu)" to "Acc\u00e9der aux fonctions : routes, cartes offline, mar\u00e9es, m\u00e9t\u00e9o, zones d'alerte, AIS, journal de bord",
                        "Routes" to "Cr\u00e9er une route en s\u00e9lectionnant des waypoints dans l'ordre. Activer pour afficher le guidage (BRG/DST/ETA/XTE)",
                        "Zones d'alerte" to "D\u00e9finir des zones polygonales avec alerte \u00e0 l'entr\u00e9e ou la sortie",
                        "Cartes hors-ligne" to "T\u00e9l\u00e9charger la zone visible pour naviguer sans connexion",
                        "Journal de bord" to "Enregistre automatiquement la trace GPS toutes les 10 secondes"
                    )) { (title, desc) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF1B3A5C))
                            Text(desc, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Fermer") }
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
