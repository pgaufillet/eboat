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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
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

    // Depth layer
    val depthLayerVisible by viewModel.depthLayerVisible.collectAsState()

    // Bearing compass tool
    var bearingMode by remember { mutableStateOf(false) }
    var bearingPointA by remember { mutableStateOf<LatLng?>(null) }
    var bearingPointB by remember { mutableStateOf<LatLng?>(null) }
    var bearingLine by remember { mutableStateOf<Polyline?>(null) }
    var bearingMarkerA by remember { mutableStateOf<Marker?>(null) }
    var bearingMarkerB by remember { mutableStateOf<Marker?>(null) }

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

    // Toggle depth layer visibility
    LaunchedEffect(depthLayerVisible, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        map.getStyle { style ->
            style.getLayer("depth-overlay")?.setProperties(
                org.maplibre.android.style.layers.PropertyFactory.visibility(
                    if (depthLayerVisible) org.maplibre.android.style.layers.Property.VISIBLE
                    else org.maplibre.android.style.layers.Property.NONE
                )
            )
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
                        map.uiSettings.isCompasssEnabled = true
                        map.uiSettings.compassGravity = android.view.Gravity.TOP or android.view.Gravity.START
                        map.uiSettings.setCompasssMargins(40, 140, 0, 0)
                        map.uiSettings.setCompasssFadeFacingNorth(false)
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isZoomGesturesEnabled = true

                        map.addOnMapLongClickListener { latLng ->
                            if (bearingMode) {
                                val bearingIcon = IconFactory.getInstance(ctx).fromBitmap(
                                    ContextCompat.getDrawable(ctx, R.drawable.ic_bearing_point)!!.toBitmap(48, 48)
                                )
                                if (bearingPointA == null) {
                                    bearingPointA = latLng
                                    bearingMarkerA?.let { map.removeMarker(it) }
                                    bearingMarkerA = map.addMarker(
                                        MarkerOptions().position(latLng).title("A").icon(bearingIcon)
                                    )
                                } else {
                                    bearingPointB = latLng
                                    bearingMarkerB?.let { map.removeMarker(it) }
                                    bearingMarkerB = map.addMarker(
                                        MarkerOptions().position(latLng).title("B").icon(bearingIcon)
                                    )
                                    bearingLine?.let { map.removePolyline(it) }
                                    bearingLine = map.addPolyline(
                                        PolylineOptions()
                                            .add(bearingPointA!!)
                                            .add(latLng)
                                            .color(android.graphics.Color.parseColor("#FF9800"))
                                            .width(3f)
                                    )
                                }
                                return@addOnMapLongClickListener true
                            }
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
                    Text(stringResource(R.string.error_message, progress.error!!), color = Color(0xFFE63946), style = MaterialTheme.typography.bodySmall)
                } else {
                    val pctText = if (progress.percent >= 0) "${progress.percent}%" else "..."
                    val sizeKb = progress.completedBytes / 1024
                    Text(stringResource(R.string.downloading_progress, pctText, sizeKb), color = Color.White, style = MaterialTheme.typography.bodySmall)
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
                Text(stringResource(R.string.long_press_add_points, zoneCreationPoints.size),
                    color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Row {
                    if (zoneCreationPoints.size >= 3) {
                        TextButton(onClick = {
                            val pts = zoneCreationPoints.map { it.latitude to it.longitude }
                            viewModel.addAlertZone(zoneName.ifBlank { "Zone" }, pts, zoneAlertOnEntry)
                            zoneCreationMode = false
                            zoneCreationPoints.clear()
                        }) { Text(stringResource(R.string.confirm), color = Color.White) }
                    }
                    TextButton(onClick = {
                        zoneCreationMode = false
                        zoneCreationPoints.clear()
                    }) { Text(stringResource(R.string.cancel), color = Color.White) }
                }
            }
        }

        // Move mode banner
        if (waypointToMove != null) {
            Text(
                stringResource(R.string.long_press_to_place, waypointToMove!!.name),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Color(0xFFE63946), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Bearing compass banner
        if (bearingMode) {
            val bearingText = if (bearingPointA != null && bearingPointB != null) {
                val brg = com.eboat.domain.navigation.bearingDeg(
                    bearingPointA!!.latitude, bearingPointA!!.longitude,
                    bearingPointB!!.latitude, bearingPointB!!.longitude
                )
                val dist = com.eboat.domain.navigation.distanceNm(
                    bearingPointA!!.latitude, bearingPointA!!.longitude,
                    bearingPointB!!.latitude, bearingPointB!!.longitude
                )
                String.format(Locale.US, "%.0f\u00B0  %.2f NM", brg, dist)
            } else if (bearingPointA != null) {
                stringResource(R.string.long_press_second_point)
            } else {
                stringResource(R.string.long_press_first_point)
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Color(0xFFFF9800), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(bearingText, color = Color.White, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    bearingPointA = null; bearingPointB = null
                    bearingLine?.let { mapLibreMap?.removePolyline(it) }; bearingLine = null
                    bearingMarkerA?.let { mapLibreMap?.removeMarker(it) }; bearingMarkerA = null
                    bearingMarkerB?.let { mapLibreMap?.removeMarker(it) }; bearingMarkerB = null
                }) { Text(stringResource(R.string.reset), color = Color.White) }
                TextButton(onClick = {
                    bearingMode = false; bearingPointA = null; bearingPointB = null
                    bearingLine?.let { mapLibreMap?.removePolyline(it) }; bearingLine = null
                    bearingMarkerA?.let { mapLibreMap?.removeMarker(it) }; bearingMarkerA = null
                    bearingMarkerB?.let { mapLibreMap?.removeMarker(it) }; bearingMarkerB = null
                }) { Text(stringResource(R.string.close), color = Color.White) }
            }
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
                    Text(stringResource(R.string.wind_legend), color = Color.White, style = MaterialTheme.typography.labelSmall)
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
                    Text(stringResource(R.string.swell_legend), color = Color(0xFF0077B6), style = MaterialTheme.typography.labelSmall)
                }
                if (WeatherLayerType.PRESSURE in weatherLayers) {
                    Text(stringResource(R.string.pressure_legend), color = Color.White, style = MaterialTheme.typography.labelSmall)
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
                    NavDataItem("\u2693", if (anchorState.isDragging) stringResource(R.string.alarm) else "OK")
                    NavDataItem(stringResource(R.string.dist), String.format(Locale.US, "%.0f m", anchorState.currentDistanceMeters))
                    NavDataItem(stringResource(R.string.radius), "${anchorState.radiusMeters} m")
                }
            }
            if (guidance.active && !guidance.routeComplete) {
                GuidanceOverlay(guidance = guidance)
            }
            if (guidance.routeComplete) {
                Text(
                    stringResource(R.string.route_complete),
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
            title = { Text(stringResource(R.string.new_waypoint)) },
            text = {
                Column {
                    TextField(
                        value = waypointName,
                        onValueChange = { waypointName = it },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { manualCoords = !manualCoords }) {
                        Text(if (manualCoords) stringResource(R.string.use_map_position) else stringResource(R.string.enter_coordinates))
                    }
                    if (manualCoords) {
                        TextField(
                            value = wpLat,
                            onValueChange = { wpLat = it },
                            label = { Text(stringResource(R.string.latitude_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = wpLon,
                            onValueChange = { wpLon = it },
                            label = { Text(stringResource(R.string.longitude_hint)) },
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
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showWaypointDialog = false }) { Text(stringResource(R.string.cancel)) }
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
                        }) { Text(stringResource(R.string.confirm)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWaypoint(wp)
                    waypointToAct = null
                }) { Text(stringResource(R.string.delete), color = Color(0xFFE63946)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        waypointToMove = wp
                        waypointToAct = null
                    }) { Text(stringResource(R.string.move)) }
                    TextButton(onClick = {
                        editCoords = !editCoords
                        editLat = wp.latitude.toString()
                        editLon = wp.longitude.toString()
                    }) { Text("Coords") }
                    TextButton(onClick = {
                        viewModel.fetchWeather(wp.latitude, wp.longitude)
                        waypointToAct = null
                        showWeatherDialog = true
                    }) { Text(stringResource(R.string.weather)) }
                    TextButton(onClick = {
                        viewModel.fetchTides(wp.latitude, wp.longitude)
                        waypointToAct = null
                        showTideDialog = true
                    }) { Text(stringResource(R.string.tides)) }
                    TextButton(onClick = { waypointToAct = null }) { Text(stringResource(R.string.close)) }
                }
            }
        )
    }

    // Route creation dialog
    if (showRouteCreation) {
        AlertDialog(
            onDismissRequest = { showRouteCreation = false },
            title = { Text(stringResource(R.string.new_route)) },
            text = {
                Column {
                    TextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        label = { Text(stringResource(R.string.route_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.select_waypoints_in_order),
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
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showRouteCreation = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Route list dialog
    if (showRouteList) {
        AlertDialog(
            onDismissRequest = { showRouteList = false },
            title = { Text(stringResource(R.string.routes)) },
            text = {
                Column {
                    if (waypoints.size >= 2) {
                        TextButton(onClick = {
                            showRouteList = false
                            routeName = ""
                            selectedWaypointIds.clear()
                            showRouteCreation = true
                        }) { Text(stringResource(R.string.new_route_button)) }
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
                        Text(stringResource(R.string.no_routes), modifier = Modifier.padding(12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRouteList = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Offline dialog
    if (showOfflineDialog) {
        var confirmClear by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showOfflineDialog = false },
            title = { Text(stringResource(R.string.offline_maps)) },
            text = {
                Column {
                    // Download current view
                    Text(
                        stringResource(R.string.download_visible_area),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    TextField(
                        value = offlineRegionName,
                        onValueChange = { offlineRegionName = it },
                        label = { Text(stringResource(R.string.area_name)) },
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
                    ) { Text(stringResource(R.string.download)) }

                    // Saved regions
                    if (offlineRegions.isNotEmpty()) {
                        Text(
                            stringResource(R.string.saved_areas),
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
                            ) { Text(stringResource(R.string.clear_cache), color = Color(0xFFE63946)) }
                        } else {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.delete_all_areas), color = Color(0xFFE63946),
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = {
                                    viewModel.clearAllOfflineRegions()
                                    confirmClear = false
                                }) { Text(stringResource(R.string.confirm), color = Color(0xFFE63946)) }
                                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOfflineDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Tide dialog
    if (showTideDialog) {
        AlertDialog(
            onDismissRequest = { showTideDialog = false },
            title = { Text(stringResource(R.string.tides)) },
            text = {
                Column {
                    val data = tideData
                    if (data == null) {
                        Text(stringResource(R.string.loading))
                    } else if (data.highLow.isEmpty()) {
                        Text(stringResource(R.string.no_tide_data))
                    } else {
                        Text("Position: ${data.stationName}",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        val timeFormat = java.text.SimpleDateFormat("EEE HH:mm", Locale.getDefault())
                        data.highLow.forEach { extreme ->
                            val icon = if (extreme.isHigh) "\u25B2" else "\u25BC"
                            val label = if (extreme.isHigh) stringResource(R.string.hw) else stringResource(R.string.lw)
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
                        Text(stringResource(R.string.tides_at_waypoints),
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
                TextButton(onClick = { showTideDialog = false }) { Text(stringResource(R.string.close)) }
            },
            dismissButton = {
                if (boatState.hasPosition) {
                    TextButton(onClick = { viewModel.fetchTidesAtBoat() }) {
                        Text(stringResource(R.string.boat_position))
                    }
                }
            }
        )
    }

    // Alert zone dialog
    if (showAlertZoneDialog) {
        AlertDialog(
            onDismissRequest = { showAlertZoneDialog = false },
            title = { Text(stringResource(R.string.alert_zones)) },
            text = {
                Column {
                    TextButton(onClick = {
                        showAlertZoneDialog = false
                        zoneName = ""
                        zoneAlertOnEntry = true
                        zoneCreationPoints.clear()
                        zoneCreationMode = true
                    }) { Text(stringResource(R.string.new_zone_entry)) }
                    TextButton(onClick = {
                        showAlertZoneDialog = false
                        zoneName = ""
                        zoneAlertOnEntry = false
                        zoneCreationPoints.clear()
                        zoneCreationMode = true
                    }) { Text(stringResource(R.string.new_zone_exit)) }

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
                                    if (zone.alertOnEntry) stringResource(R.string.entry_alert) else stringResource(R.string.exit_alert),
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
                        Text(stringResource(R.string.no_alert_zones), modifier = Modifier.padding(12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAlertZoneDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Main menu
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            containerColor = Color(0xFF152233),
            titleContentColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF0077B6), RoundedCornerShape(4.dp))
                    )
                    Text("  eboat", style = MaterialTheme.typography.titleLarge,
                        color = Color.White)
                }
            },
            text = {
                @Composable
                fun MenuSection(title: String) {
                    Text(title.uppercase(),
                        color = Color(0xFF4FC3F7),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp, start = 4.dp))
                }

                @Composable
                fun PrimaryItem(label: String, sub: String, accent: Color, action: () -> Unit) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { action() }
                            .padding(vertical = 2.dp)
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier
                            .size(10.dp)
                            .background(accent, RoundedCornerShape(5.dp)))
                        Text(label, color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 14.dp).weight(1f))
                        Text(sub, color = Color.White.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                @Composable
                fun SecondaryItem(label: String, sub: String, action: () -> Unit) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { action() }
                            .padding(vertical = 1.dp)
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f))
                        Text(sub, color = Color.White.copy(alpha = 0.35f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                @Composable
                fun LayerChip(label: String, active: Boolean, action: () -> Unit) {
                    val bg = if (active) Color(0xFF0077B6).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f)
                    val border = if (active) Color(0xFF0077B6) else Color.Transparent
                    Box(
                        modifier = Modifier
                            .clickable { action() }
                            .background(bg, RoundedCornerShape(20.dp))
                            .then(
                                if (active) Modifier.background(bg, RoundedCornerShape(20.dp))
                                else Modifier
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label,
                            color = if (active) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }

                Column {
                    MenuSection(stringResource(R.string.navigation))
                    PrimaryItem(stringResource(R.string.routes),
                        if (activeRoute != null) activeRoute!!.name else "${routes.size}",
                        Color(0xFFE63946)) {
                        showMenu = false; showRouteList = true
                    }
                    PrimaryItem(stringResource(R.string.anchor),
                        if (anchorState.active) "${anchorState.radiusMeters}m" else "---",
                        if (anchorState.isDragging) Color(0xFFE63946)
                        else if (anchorState.active) Color(0xFF2E7D32)
                        else Color(0xFF666666)) {
                        showMenu = false; showAnchorDialog = true
                    }
                    PrimaryItem(stringResource(R.string.weather),
                        if (weatherLayers.isNotEmpty()) stringResource(R.string.layers_count, weatherLayers.size) else "---",
                        Color(0xFF0077B6)) {
                        showMenu = false; viewModel.fetchWeatherAtBoat(); showWeatherDialog = true
                    }

                    MenuSection(stringResource(R.string.data))
                    SecondaryItem(stringResource(R.string.tides), stringResource(R.string.hw_lw)) {
                        showMenu = false; viewModel.fetchTidesAtBoat(); showTideDialog = true
                    }
                    SecondaryItem("AIS",
                        if (aisConnected) stringResource(R.string.targets_count, aisTargets.size) else "---") {
                        showMenu = false; showAisDialog = true
                    }
                    SecondaryItem(stringResource(R.string.alert_zones), "${alertZones.size}") {
                        showMenu = false; showAlertZoneDialog = true
                    }

                    MenuSection(stringResource(R.string.tools))
                    SecondaryItem(stringResource(R.string.compass), stringResource(R.string.bearing_and_distance)) {
                        showMenu = false; bearingMode = true; bearingPointA = null; bearingPointB = null
                    }
                    SecondaryItem(stringResource(R.string.trip_log),
                        if (tripRecording) "REC" else stringResource(R.string.trips_count, tripIds.size)) {
                        showMenu = false; showTripDialog = true
                    }
                    SecondaryItem(stringResource(R.string.offline_maps), stringResource(R.string.zones_count, offlineRegions.size)) {
                        showMenu = false; viewModel.refreshOfflineRegions(); showOfflineDialog = true
                    }

                    MenuSection(stringResource(R.string.layers))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LayerChip(stringResource(R.string.soundings), depthLayerVisible) { viewModel.toggleDepthLayer() }
                        LayerChip(stringResource(R.string.weather), weatherLayers.isNotEmpty()) {
                            val allTypes = com.eboat.domain.model.WeatherLayerType.values().toSet()
                            if (weatherLayers.isEmpty()) {
                                allTypes.forEach { viewModel.toggleWeatherLayer(it) }
                                viewModel.fetchWeatherOverlay()
                            } else viewModel.clearWeatherLayers()
                        }
                    }

                    // Help
                    TextButton(
                        onClick = { showMenu = false; showHelp = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp)
                    ) { Text(stringResource(R.string.help), color = Color.White.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text(stringResource(R.string.close), color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }

    // Weather dialog
    if (showWeatherDialog) {
        AlertDialog(
            onDismissRequest = { showWeatherDialog = false },
            title = { Text(stringResource(R.string.marine_weather)) },
            text = {
                Column {
                    val data = weatherData
                    if (data == null) {
                        Text(stringResource(R.string.loading))
                    } else if (data.forecasts.isEmpty()) {
                        Text(stringResource(R.string.no_weather_data))
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
                            Text(stringResource(R.string.time), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.wind), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.gust), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                            Text(stringResource(R.string.wave), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
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
                        Text(stringResource(R.string.weather_at_waypoints),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        waypoints.take(5).forEach { wp ->
                            TextButton(onClick = {
                                viewModel.fetchWeather(wp.latitude, wp.longitude)
                            }) { Text(wp.name, style = MaterialTheme.typography.bodySmall) }
                        }
                    }

                    // Layer selector
                    Text(stringResource(R.string.map_layers),
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
                            Text(stringResource(R.string.load_overlay))
                        }
                        TextButton(onClick = { viewModel.clearWeatherLayers() }) {
                            Text(stringResource(R.string.hide_all))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeatherDialog = false }) { Text(stringResource(R.string.close)) }
            },
            dismissButton = {
                if (boatState.hasPosition) {
                    TextButton(onClick = { viewModel.fetchWeatherAtBoat() }) {
                        Text(stringResource(R.string.boat_position))
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
                            label = { Text(stringResource(R.string.ip_address)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        TextField(value = aisPort, onValueChange = { aisPort = it },
                            label = { Text(stringResource(R.string.port)) }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        TextButton(onClick = {
                            viewModel.connectAis(aisHost, aisPort.toIntOrNull() ?: 10110)
                        }) { Text(stringResource(R.string.connect)) }
                    } else {
                        Text(stringResource(R.string.connected_targets, aisTargets.size))
                        aisTargets.values.sortedByDescending { it.lastUpdate }.take(10).forEach { t ->
                            Text("${t.mmsi} ${t.name.ifBlank { "---" }} ${String.format(Locale.US, "%.1f kn", t.sog)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAisDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Trip log dialog
    if (showTripDialog) {
        AlertDialog(
            onDismissRequest = { showTripDialog = false },
            title = { Text(stringResource(R.string.trip_log)) },
            text = {
                Column {
                    if (!tripRecording) {
                        TextButton(onClick = { viewModel.startTripRecording() }) {
                            Text(stringResource(R.string.start_recording))
                        }
                    } else {
                        TextButton(onClick = { viewModel.stopTripRecording() }) {
                            Text(stringResource(R.string.stop_recording), color = Color(0xFFE63946))
                        }
                    }
                    if (tripIds.isNotEmpty()) {
                        Text(stringResource(R.string.recorded_trips),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp))
                        tripIds.forEach { id ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.trip_number, id))
                                TextButton(onClick = { viewModel.deleteTrip(id) }) { Text("X") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTripDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Help dialog
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.help)) },
            text = {
                val helpItems = listOf(
                    R.string.long_press to R.string.help_long_press,
                    R.string.tap_on_marker to R.string.help_tap_marker,
                    R.string.recenter_label to R.string.help_recenter,
                    R.string.anchor_label to R.string.help_anchor,
                    R.string.menu_label to R.string.help_menu,
                    R.string.routes to R.string.help_routes,
                    R.string.alert_zones to R.string.help_alert_zones,
                    R.string.offline_maps to R.string.help_offline_maps,
                    R.string.trip_log to R.string.help_trip_log
                )
                LazyColumn {
                    items(helpItems) { (titleRes, descRes) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF1B3A5C))
                            Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    // Anchor dialog
    if (showAnchorDialog) {
        var sliderRadius by remember { mutableStateOf(anchorState.radiusMeters.toFloat()) }
        AlertDialog(
            onDismissRequest = { showAnchorDialog = false },
            title = { Text(if (anchorState.active) stringResource(R.string.anchor_watch) else stringResource(R.string.drop_anchor)) },
            text = {
                Column {
                    if (!anchorState.active) {
                        Text(stringResource(R.string.alarm_radius, sliderRadius.toInt()))
                        androidx.compose.material3.Slider(
                            value = sliderRadius,
                            onValueChange = { sliderRadius = it },
                            valueRange = 20f..300f,
                            steps = 27
                        )
                        Text(
                            stringResource(R.string.alarm_radius_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    } else {
                        Text(stringResource(R.string.distance_value, String.format(Locale.US, "%.0f m", anchorState.currentDistanceMeters)))
                        Text(stringResource(R.string.radius_value, anchorState.radiusMeters))
                        if (anchorState.isDragging) {
                            Text(stringResource(R.string.boat_is_drifting), color = Color(0xFFE63946),
                                style = MaterialTheme.typography.titleMedium)
                        }
                        Text(stringResource(R.string.adjust_radius), modifier = Modifier.padding(top = 8.dp))
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
                    }) { Text(stringResource(R.string.drop)) }
                } else {
                    TextButton(onClick = {
                        viewModel.liftAnchor()
                        showAnchorDialog = false
                    }) { Text(stringResource(R.string.lift_anchor), color = Color(0xFFE63946)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnchorDialog = false }) { Text(stringResource(R.string.close)) }
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
