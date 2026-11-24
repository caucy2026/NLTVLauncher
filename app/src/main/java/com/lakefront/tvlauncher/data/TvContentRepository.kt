package com.lakefront.tvlauncher.data

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import com.lakefront.tvlauncher.logic.RawChannel
import com.lakefront.tvlauncher.logic.RawProgram
import com.lakefront.tvlauncher.logic.TvAccess
import com.lakefront.tvlauncher.logic.TvFailures
import com.lakefront.tvlauncher.logic.TvInput
import com.lakefront.tvlauncher.logic.TvMapper

/**
 * Reads only the TV listings this launcher is allowed to see.
 * Queries use the public AndroidX TvContract URIs. Selection clauses are not
 * supported by these tables, so rows are filtered in memory.
 *
 * An empty result is normal: many apps and TV builds publish no Watch Next
 * programs and no browsable preview channels.
 */
class TvContentRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun load(): TvInput {
        if (!providerPresent()) {
            return TvInput(TvAccess.ProviderMissing)
        }
        return try {
            val watch = queryPrograms(
                uri = TvContractCompat.WatchNextPrograms.CONTENT_URI,
                channelColumn = false,
            )
            val channels = queryChannels()
            val programs = queryPrograms(
                uri = TvContractCompat.PreviewPrograms.CONTENT_URI,
                channelColumn = true,
            )
            val permissionGranted = hasListingsPermission()
            val nothingReadable = watch.isEmpty() && channels.isEmpty() && programs.isEmpty()
            val access = if (!permissionGranted && nothingReadable) {
                TvAccess.PermissionDenied
            } else {
                TvAccess.Ok
            }
            val labels = appLabels(channels.mapNotNull { it.packageName }.distinct())
            TvInput(
                access = access,
                watchNext = watch,
                channels = channels,
                programs = programs,
                appLabels = labels,
            )
        } catch (error: SecurityException) {
            Log.i(TAG, "TV listings permission denied: ${error.message}")
            TvInput(TvFailures.classify(error.javaClass.simpleName, error.message))
        } catch (error: Exception) {
            Log.w(TAG, "TV provider unavailable", error)
            TvInput(TvFailures.classify(error.javaClass.simpleName, error.message))
        }
    }

    private fun providerPresent(): Boolean {
        return try {
            val client = resolver.acquireUnstableContentProviderClient(TvContractCompat.Channels.CONTENT_URI)
            if (client == null) {
                false
            } else {
                client.close()
                true
            }
        } catch (error: SecurityException) {
            true
        } catch (error: Exception) {
            Log.i(TAG, "No TV provider: ${error.message}")
            false
        }
    }

    private fun hasListingsPermission(): Boolean {
        val names = LISTING_PERMISSIONS
        return names.any { name ->
            appContext.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun queryChannels(): List<RawChannel> {
        val projection = arrayOf(
            android.provider.BaseColumns._ID,
            TvContractCompat.Channels.COLUMN_DISPLAY_NAME,
            TvContractCompat.Channels.COLUMN_TYPE,
            TvContractCompat.Channels.COLUMN_BROWSABLE,
            TvContractCompat.BaseTvColumns.COLUMN_PACKAGE_NAME,
        )
        val cursor = queryCursor(TvContractCompat.Channels.CONTENT_URI, projection) ?: return emptyList()
        cursor.use { rows ->
            val result = ArrayList<RawChannel>()
            while (rows.moveToNext()) {
                result += RawChannel(
                    id = rows.long(android.provider.BaseColumns._ID),
                    displayName = rows.string(TvContractCompat.Channels.COLUMN_DISPLAY_NAME),
                    packageName = rows.string(TvContractCompat.BaseTvColumns.COLUMN_PACKAGE_NAME),
                    browsable = rows.bool(TvContractCompat.Channels.COLUMN_BROWSABLE, default = false),
                    type = rows.string(TvContractCompat.Channels.COLUMN_TYPE),
                )
            }
            return result
        }
    }

    private fun queryPrograms(uri: android.net.Uri, channelColumn: Boolean): List<RawProgram> {
        val columns = buildList {
            add(android.provider.BaseColumns._ID)
            add("title")
            add("poster_art_uri")
            add("thumbnail_uri")
            add("intent_uri")
            add("last_playback_position_millis")
            add("duration_millis")
            add("package_name")
            add("last_engagement_time_utc_millis")
            add("browsable")
            if (channelColumn) {
                add(TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID)
                add(TvContractCompat.PreviewPrograms.COLUMN_WEIGHT)
            }
        }.toTypedArray()
        val cursor = queryCursor(uri, columns) ?: queryCursor(
            uri,
            arrayOf(android.provider.BaseColumns._ID, "title", "intent_uri", "poster_art_uri"),
        ) ?: return emptyList()
        cursor.use { rows ->
            val result = ArrayList<RawProgram>()
            while (rows.moveToNext()) {
                result += RawProgram(
                    id = rows.long(android.provider.BaseColumns._ID),
                    channelId = rows.optionalLong(TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID),
                    title = rows.string("title"),
                    posterUri = rows.string("poster_art_uri"),
                    thumbnailUri = rows.string("thumbnail_uri"),
                    intentUri = rows.string("intent_uri"),
                    positionMs = rows.long("last_playback_position_millis"),
                    durationMs = rows.long("duration_millis"),
                    packageName = rows.string("package_name"),
                    engagementMs = rows.long("last_engagement_time_utc_millis"),
                    weight = rows.int(TvContractCompat.PreviewPrograms.COLUMN_WEIGHT),
                    browsable = rows.bool("browsable", default = true),
                )
            }
            return result
        }
    }

    private fun queryCursor(uri: android.net.Uri, projection: Array<String>): Cursor? {
        return try {
            resolver.query(uri, projection, null, null, null)
        } catch (error: SecurityException) {
            throw error
        } catch (error: Exception) {
            Log.i(TAG, "Query failed for $uri: ${error.message}")
            null
        }
    }

    private fun appLabels(packages: List<String>): Map<String, String> {
        val labels = HashMap<String, String>()
        for (pkg in packages) {
            val label = try {
                val info = packageManager().getApplicationInfo(pkg, 0)
                packageManager().getApplicationLabel(info)?.toString()?.trim().orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (label.isNotEmpty()) labels[pkg] = label
        }
        return labels
    }

    private fun packageManager() = appContext.packageManager

    private fun Cursor.string(column: String): String? {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return null
        return getString(index)
    }

    private fun Cursor.long(column: String): Long {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return 0L
        return getLong(index)
    }

    private fun Cursor.optionalLong(column: String): Long? {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return null
        return getLong(index)
    }

    private fun Cursor.int(column: String): Int {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return 0
        return getInt(index)
    }

    private fun Cursor.bool(column: String, default: Boolean): Boolean {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return default
        return getInt(index) != 0
    }

    companion object {
        private const val TAG = "LakeTv"
        const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
        const val LEGACY_READ_TV_LISTINGS = "com.android.providers.tv.permission.READ_TV_LISTINGS"
        val LISTING_PERMISSIONS = arrayOf(
            READ_TV_LISTINGS,
            LEGACY_READ_TV_LISTINGS,
        )

        fun snapshot(input: TvInput) = TvMapper.map(input)
    }
}
