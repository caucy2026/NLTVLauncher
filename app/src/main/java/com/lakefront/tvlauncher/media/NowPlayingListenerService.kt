package com.lakefront.tvlauncher.media

import android.service.notification.NotificationListenerService

/**
 * Declared only so the user can grant notification access, which is what lets the launcher read
 * other apps' media sessions through MediaSessionManager. The service itself does nothing.
 */
class NowPlayingListenerService : NotificationListenerService()
