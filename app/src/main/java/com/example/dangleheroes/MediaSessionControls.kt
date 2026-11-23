package com.example.dangleheroes

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.provider.Settings
import android.service.notification.NotificationListenerService

/** Bound by Android after the user grants Notification Access in system settings. */
class MediaAccessNotificationListener : NotificationListenerService()

object MediaSessionControls {
    const val ACTION_TOGGLE_PLAY_PAUSE = 0
    const val ACTION_PREVIOUS = 1
    const val ACTION_NEXT = 2

    const val RESULT_SENT = 0
    const val RESULT_ACCESS_REQUIRED = 1
    const val RESULT_NO_SESSION = 2
    const val RESULT_FAILED = 3

    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(context.packageName)
    }

    fun perform(context: Context, action: Int): Int {
        if (!hasNotificationAccess(context)) return RESULT_ACCESS_REQUIRED

        val component = ComponentName(context, MediaAccessNotificationListener::class.java)
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = try {
            manager.getActiveSessions(component)
        } catch (_: SecurityException) {
            return RESULT_ACCESS_REQUIRED
        } catch (_: Exception) {
            return RESULT_FAILED
        }

        if (sessions.isEmpty()) return RESULT_NO_SESSION

        val controller = selectController(sessions) ?: return RESULT_NO_SESSION
        return try {
            val controls = controller.transportControls
            when (action) {
                ACTION_TOGGLE_PLAY_PAUSE -> {
                    val state = controller.playbackState?.state
                    val isPlaying = state == android.media.session.PlaybackState.STATE_PLAYING ||
                        state == android.media.session.PlaybackState.STATE_BUFFERING ||
                        state == android.media.session.PlaybackState.STATE_FAST_FORWARDING
                    if (isPlaying) controls.pause() else controls.play()
                }
                ACTION_PREVIOUS -> controls.skipToPrevious()
                ACTION_NEXT -> controls.skipToNext()
                else -> return RESULT_FAILED
            }
            RESULT_SENT
        } catch (_: SecurityException) {
            RESULT_ACCESS_REQUIRED
        } catch (_: Exception) {
            RESULT_FAILED
        }
    }

    private fun selectController(sessions: List<MediaController>): MediaController? {
        fun state(controller: MediaController) = controller.playbackState?.state
        return sessions.firstOrNull { state(it) == android.media.session.PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { state(it) == android.media.session.PlaybackState.STATE_BUFFERING }
            ?: sessions.firstOrNull { state(it) == android.media.session.PlaybackState.STATE_PAUSED }
            ?: sessions.firstOrNull()
    }
}
