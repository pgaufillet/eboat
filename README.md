# eboat

An Android navigation and monitoring app for boaters. Real-time GPS positioning, route planning, weather and tide data, anchor watch alarm, AIS vessel tracking, and offline maps.

## Features

### Navigation & Routing
- Real-time GPS tracking with accuracy display
- Create and store named waypoints
- Multi-waypoint route planning with active guidance (bearing, distance, ETA, cross-track error)
- Auto-advance to next waypoint on arrival
- Trip logging with timestamped position, speed, and course

### Anchor Watch
- Drop anchor with configurable alarm radius
- Background monitoring via foreground service
- Audio and vibration alarm when the boat drifts beyond the set radius

### Alert Zones
- Define polygon-based geofence zones
- Configurable alerts on entry or exit
- Persistent zone storage

### Weather & Tides
- Marine weather forecasts: wind, waves, swell, pressure, visibility
- Weather overlay layers on the map
- Tide predictions with high/low extremes
- Contextual weather along route corridors

### AIS
- Real-time tracking of nearby vessels via NMEA over TCP
- Display AIS targets with position, speed, heading, and course

### Maps & Offline
- MapLibre-based interactive map
- Download map regions for offline use
- Depth sounding layer toggle
- Compass tool for bearing and distance measurement

## Tech Stack

- **Kotlin** 2.1.0 with coroutines
- **Jetpack Compose** (BOM 2025.01.01)
- **MapLibre Android SDK** 11.8.0
- **Room** 2.6.1 (local database)
- **Google Play Services Location** 21.3.0
- **Material Design 3**

## Requirements

- Android 14+ (API 34)
- GPS-enabled device
- Internet connection for weather/tide data and map tiles
- (Optional) Network-connected AIS receiver for vessel tracking

## Building

```bash
git clone https://github.com/your-username/eboat.git
cd eboat
./gradlew assembleDebug
```

Install the debug APK on a connected device:

```bash
./gradlew installDebug
```

## Permissions

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS positioning |
| `ACCESS_COARSE_LOCATION` | Fallback location |
| `INTERNET` | Weather, tides, map tiles |
| `ACCESS_NETWORK_STATE` | Connectivity checks |
| `FOREGROUND_SERVICE` | Anchor watch background monitoring |
| `POST_NOTIFICATIONS` | Anchor alarm notifications |
| `VIBRATE` | Alarm feedback |

## APIs Used

- [Open-Meteo](https://open-meteo.com/) for weather forecasts (free, no API key required)
- [Open-Meteo Marine](https://open-meteo.com/en/docs/marine-weather-api) for tide and marine data
- [MapLibre](https://maplibre.org/) for map rendering

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
