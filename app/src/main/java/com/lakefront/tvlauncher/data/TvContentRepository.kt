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

    /** Set when a query fails because the authority does not exist any more. */
    private var providerVanished = false

    /** Set when the provider exists but has no watch_next/preview_program table. */
    private var listingsUnsupported = false

    fun load(): TvInput {
        providerVanished = false
        listingsUnsupported = false
        val authority = providerAuthority() ?: return TvInput(TvAccess.ProviderMissing)
        return try {
            val watch = queryPrograms(
                uri = watchNextUri(authority),
                channelColumn = false,
            )
            val channels = queryChannels(authority)
            val programs = queryPrograms(
                uri = listingsUri(authority, "preview_program"),
                channelColumn = true,
            )
            val permissionGranted = hasListingsPermission()
            val nothingReadable = watch.isEmpty() && channels.isEmpty() && programs.isEmpty()
            val emptyListings = watch.isEmpty() && programs.isEmpty()
            // Apps publish Watch Next through android.tvprovider. A device that only answers on
            // the legacy android.media.tv authority has no such channel, so an empty result there
            // is not "nothing played yet" but "this system cannot carry Watch Next at all".
            val legacyOnly = authority == LEGACY_TV_PROVIDER_AUTHORITY
            val access = when {
                providerVanished -> TvAccess.ProviderMissing
                (listingsUnsupported || legacyOnly) && emptyListings ->
                    TvAccess.ListingsUnsupported
                !permissionGranted && nothingReadable -> TvAccess.PermissionDenied
                else -> TvAccess.Ok
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

    /**
     * Picks the authority this device actually publishes. Android TV builds answer on
     * android.tvprovider; TV builds without the Google TV provider (and the AndroidX
     * TvContractCompat constants) only answer on the legacy android.media.tv authority. Querying
     * just android.tvprovider read nothing at all on such devices.
     */
    private fun providerAuthority(): String? {
        for (authority in TV_PROVIDER_AUTHORITIES) {
            val info = try {
                appContext.packageManager.resolveContentProvider(authority, 0)
            } catch (error: Exception) {
                Log.i(TAG, "Provider lookup failed for $authority: ${error.message}")
                null
            }
            if (info != null) {
                Log.i(TAG, "Using TV provider authority $authority")
                return authority
            }
        }
        Log.i(TAG, "No TV listings provider: ${TV_PROVIDER_AUTHORITIES.joinToString()}")
        return null
    }

    private fun listingsUri(authority: String, path: String): android.net.Uri =
        android.net.Uri.parse("content://$authority/$path")

    /**
     * Watch Next lives at a different path per authority: the modern authority uses the
     * two-segment ".../watch_next/program", the legacy one the single table ".../watch_next_program".
     */
    private fun watchNextUri(authority: String): android.net.Uri =
        if (authority == TV_PROVIDER_AUTHORITY) {
            listingsUri(authority, "watch_next/program")
        } else {
            listingsUri(authority, "watch_next_program")
        }

    private fun hasListingsPermission(): Boolean {
        val names = LISTING_PERMISSIONS
        return names.any { name ->
            appContext.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun queryChannels(authority: String): List<RawChannel> {
        val projection = arrayOf(
            android.provider.BaseColumns._ID,
            TvContractCompat.Channels.COLUMN_DISPLAY_NAME,
            TvContractCompat.Channels.COLUMN_TYPE,
            TvContractCompat.Channels.COLUMN_BROWSABLE,
            TvContractCompat.BaseTvColumns.COLUMN_PACKAGE_NAME,
        )
        val cursor = queryCursor(listingsUri(authority, "channel"), projection) ?: return emptyList()
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
            classifyQueryFailure(error)
            Log.i(TAG, "Query failed for $uri: ${error.message}")
            null
        }
    }

    /**
     * Separates "the authority is gone" from "this provider has no such table". The legacy
     * android.media.tv provider on plain TV builds answers for /channel but returns
     * "Unknown URI … /watch_next/program", which is not a missing provider.
     */
    private fun classifyQueryFailure(error: Exception) {
        val message = error.message?.lowercase().orEmpty()
        val authorityGone = "could not find provider" in message ||
            "unable to find provider" in message ||
            "unknown authority" in message ||
            "no provider" in message ||
            "unknown url" in message
        if (authorityGone) {
            providerVanished = true
            return
        }
        if ("unknown uri" in message) {
            listingsUnsupported = true
            return
        }
        if ("illegalstate" in error.javaClass.simpleName.lowercase() &&
            "provider" in message
        ) {
            providerVanished = true
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
        const val TV_PROVIDER_AUTHORITY = "android.tvprovider"
        const val LEGACY_TV_PROVIDER_AUTHORITY = "android.media.tv"
        val TV_PROVIDER_AUTHORITIES = arrayOf(TV_PROVIDER_AUTHORITY, LEGACY_TV_PROVIDER_AUTHORITY)

        const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
        const val LEGACY_READ_TV_LISTINGS = "com.android.providers.tv.permission.READ_TV_LISTINGS"
        val LISTING_PERMISSIONS = arrayOf(
            READ_TV_LISTINGS,
            LEGACY_READ_TV_LISTINGS,
        )

        fun snapshot(input: TvInput) = TvMapper.map(input)
    }
}
