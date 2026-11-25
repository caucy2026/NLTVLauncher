package com.lakefront.tvlauncher.debug

import android.app.Activity
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat

/**
 * Debug-only, read-only probe for the TV listing tables.
 *
 * It answers the question "is there anything in Watch Next on this device" from a process that
 * actually holds `READ_TV_LISTINGS`. Querying from `adb shell` cannot answer it: the shell does not
 * hold the permission, so every table looks empty no matter what the provider contains.
 *
 * Start it explicitly and read the result from logcat:
 *
 * ```
 * adb shell am start -n com.lakefront.tvlauncher/com.lakefront.tvlauncher.debug.TvListingsProbeActivity
 * adb shell logcat -d -s LakeTvProbe
 * ```
 */
class TvListingsProbeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread {
            try {
                probe()
            } catch (error: Exception) {
                Log.w(TAG, "probe failed: ${error.javaClass.simpleName}: ${error.message}")
            } finally {
                runOnUiThread { finish() }
            }
        }.start()
    }

    private fun probe() {
        val resolver = contentResolver
        val authority = TvContractCompat.AUTHORITY
        val provider = packageManager.resolveContentProvider(authority, 0)
        Log.i(TAG, "authority=$authority provider=${provider?.packageName ?: "MISSING"}")
        Log.i(
            TAG,
            "permissions readTvListings=${granted(TvContractCompat.PERMISSION_READ_TV_LISTINGS)} " +
                "accessAllEpgData=${granted(PERMISSION_ACCESS_ALL_EPG_DATA)} " +
                "writeEpgData=${granted(PERMISSION_WRITE_EPG_DATA)}",
        )

        dump("channel", TvContractCompat.Channels.CONTENT_URI, resolver)
        dump("watch_next_program", TvContractCompat.WatchNextPrograms.CONTENT_URI, resolver)
        dump("preview_program", TvContractCompat.PreviewPrograms.CONTENT_URI, resolver)
    }

    private fun dump(label: String, uri: Uri, resolver: ContentResolver) {
        val rows = queryWithFallback(
            resolver,
            uri,
            arrayOf(
                "_id",
                "package_name",
                "type",
                "browsable",
                "searchable",
                "title",
            ),
            arrayOf("_id", "package_name"),
        )
        if (rows == null) {
            Log.i(TAG, "$label: query failed")
            return
        }
        Log.i(TAG, "$label: rows=${rows.size}")
        rows.take(5).forEach { row -> Log.i(TAG, "$label row: $row") }
    }

    private fun queryWithFallback(
        resolver: ContentResolver,
        uri: Uri,
        wide: Array<String>,
        narrow: Array<String>,
    ): List<String>? {
        queryFlat(resolver, uri, wide)?.let { return it }
        Log.i(TAG, "$uri: falling back to $narrow")
        return queryFlat(resolver, uri, narrow)
    }

    private fun queryFlat(
        resolver: ContentResolver,
        uri: Uri,
        projection: Array<String>,
    ): List<String>? {
        return try {
            // No selection: a caller without ACCESS_ALL_EPG_DATA gets SecurityException for one.
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                val out = ArrayList<String>(cursor.count)
                while (cursor.moveToNext()) {
                    val fields = projection.mapNotNull { column ->
                        val index = cursor.getColumnIndex(column)
                        if (index < 0) {
                            null
                        } else {
                            val value = if (cursor.isNull(index)) "null" else cursor.getString(index)
                            "$column=$value"
                        }
                    }
                    out += fields.joinToString(", ")
                }
                out
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "$uri: SecurityException: ${error.message}")
            null
        } catch (error: Exception) {
            Log.w(TAG, "$uri: ${error.javaClass.simpleName}: ${error.message}")
            null
        }
    }

    private fun granted(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "LakeTvProbe"
        const val PERMISSION_ACCESS_ALL_EPG_DATA =
            "com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA"
        const val PERMISSION_WRITE_EPG_DATA =
            "com.android.providers.tv.permission.WRITE_EPG_DATA"
    }
}
