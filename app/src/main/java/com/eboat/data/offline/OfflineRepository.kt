package com.eboat.data.offline

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OfflineRegionInfo(
    val id: Long,
    val name: String,
    val bounds: LatLngBounds,
    val minZoom: Double,
    val maxZoom: Double,
    val completedSize: Long,
    val isComplete: Boolean
)

data class DownloadProgress(
    val percent: Int,
    val completedResources: Long,
    val requiredResources: Long,
    val completedBytes: Long,
    val isComplete: Boolean,
    val error: String? = null
)

class OfflineRepository(context: Context) {

    private val offlineManager = OfflineManager.getInstance(context)
    private val pixelRatio = context.resources.displayMetrics.density

    init {
        // Raise default limit from 6000 to 50000 tiles
        offlineManager.setOfflineMapboxTileCountLimit(50_000)
    }

    suspend fun listRegions(): List<OfflineRegionInfo> = suspendCancellableCoroutine { cont ->
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val result = regions?.map { region ->
                    val def = region.definition as OfflineTilePyramidRegionDefinition
                    val name = try {
                        JSONObject(String(region.metadata)).optString("name", "Region ${region.id}")
                    } catch (_: Exception) { "Region ${region.id}" }
                    val bounds = def.bounds ?: return@map null

                    OfflineRegionInfo(
                        id = region.id,
                        name = name,
                        bounds = bounds,
                        minZoom = def.minZoom,
                        maxZoom = def.maxZoom,
                        completedSize = 0,
                        isComplete = false
                    )
                }?.filterNotNull() ?: emptyList()
                cont.resume(result)
            }
            override fun onError(error: String) {
                cont.resumeWithException(RuntimeException(error))
            }
        })
    }

    fun downloadRegion(
        name: String,
        styleUrl: String,
        bounds: LatLngBounds,
        minZoom: Double,
        maxZoom: Double
    ): Flow<DownloadProgress> = callbackFlow {
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl, bounds, minZoom, maxZoom, pixelRatio
        )
        val metadata = JSONObject().put("name", name).toString().toByteArray()

        offlineManager.createOfflineRegion(definition, metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val percent = if (status.requiredResourceCount > 0 && status.isRequiredResourceCountPrecise)
                                (status.completedResourceCount * 100 / status.requiredResourceCount).toInt()
                            else -1

                            trySend(DownloadProgress(
                                percent = percent,
                                completedResources = status.completedResourceCount,
                                requiredResources = status.requiredResourceCount,
                                completedBytes = status.completedResourceSize,
                                isComplete = status.isComplete
                            ))

                            if (status.isComplete) {
                                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                channel.close()
                            }
                        }
                        override fun onError(error: OfflineRegionError) {
                            trySend(DownloadProgress(0, 0, 0, 0, false, error.message))
                        }
                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            trySend(DownloadProgress(0, 0, 0, 0, false,
                                "Tile limit exceeded ($limit)"))
                            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            channel.close()
                        }
                    })
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }
                override fun onError(error: String) {
                    trySend(DownloadProgress(0, 0, 0, 0, false, error))
                    channel.close()
                }
            }
        )
        awaitClose { }
    }

    suspend fun deleteRegion(regionId: Long): Unit = suspendCancellableCoroutine { cont ->
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val region = regions?.find { it.id == regionId }
                if (region == null) {
                    cont.resume(Unit)
                    return
                }
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() { cont.resume(Unit) }
                    override fun onError(error: String) {
                        cont.resumeWithException(RuntimeException(error))
                    }
                })
            }
            override fun onError(error: String) {
                cont.resumeWithException(RuntimeException(error))
            }
        })
    }

    suspend fun clearAllRegions(): Unit = suspendCancellableCoroutine { cont ->
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                if (regions.isNullOrEmpty()) {
                    cont.resume(Unit)
                    return
                }
                var remaining = regions.size
                var failed = false
                regions.forEach { region ->
                    region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            remaining--
                            if (remaining == 0 && !failed) cont.resume(Unit)
                        }
                        override fun onError(error: String) {
                            if (!failed) {
                                failed = true
                                cont.resumeWithException(RuntimeException(error))
                            }
                        }
                    })
                }
            }
            override fun onError(error: String) {
                cont.resumeWithException(RuntimeException(error))
            }
        })
    }
}
