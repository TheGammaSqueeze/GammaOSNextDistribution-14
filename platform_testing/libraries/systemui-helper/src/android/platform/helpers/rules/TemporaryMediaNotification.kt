/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.platform.helpers.rules

import android.R
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.test.InstrumentationRegistry
import java.time.Duration
import org.junit.rules.ExternalResource

/** Posts a temporary media notification, and deletes it when the test finishes. */
class TemporaryMediaNotification(private val id: String) : ExternalResource() {

    companion object {
        // Pieces of the media session.
        private const val SESSION_KEY = "Session"
        private const val SESSION_TITLE = "Title"
        private const val SESSION_ARTIST = "Artist"
        private const val PLAYBACK_SPEED = 1f
        private val SESSION_DURATION = Duration.ofMinutes(60)
        private val SESSION_POSITION = Duration.ofMinutes(6)

        // Pieces of the notification.
        private const val NOTIFICATION_ID = 1
        private const val TITLE = "Media-style Notification"
        private const val TEXT = "Notification for a test media session"
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaSession: MediaSession

    override fun before() {
        val context = InstrumentationRegistry.getTargetContext()
        notificationManager = context.getSystemService(NotificationManager::class.java)!!
        mediaSession = MediaSession(context, SESSION_KEY)

        // create a solid color bitmap to use as album art in media metadata
        val bitmap: Bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.YELLOW)

        mediaSession.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_ARTIST, SESSION_ARTIST)
                .putString(MediaMetadata.METADATA_KEY_TITLE, SESSION_TITLE)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, SESSION_DURATION.toMillis())
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                .build()
        )
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, SESSION_POSITION.toMillis(), PLAYBACK_SPEED)
                .setActions(
                    PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SKIP_TO_NEXT
                )
                .addCustomAction("action.rew", "Rewind", R.drawable.ic_media_rew)
                .addCustomAction("action.ff", "Fast Forward", R.drawable.ic_media_ff)
                .build()
        )

        val notificationBuilder =
            Notification.Builder(context, id)
                .setContentTitle(TITLE)
                .setContentText(TEXT)
                .setSmallIcon(R.drawable.ic_media_pause)
                .setStyle(
                    Notification.MediaStyle()
                        .setShowActionsInCompactView(1, 2, 3)
                        .setMediaSession(mediaSession.sessionToken)
                )
                .setColor(Color.BLUE)
                .setColorized(true)
                .addAction(R.drawable.ic_media_rew, "rewind", null)
                .addAction(R.drawable.ic_media_previous, "previous track", null)
                .addAction(R.drawable.ic_media_pause, "pause", null)
                .addAction(R.drawable.ic_media_next, "next track", null)
                .addAction(R.drawable.ic_media_ff, "fast forward", null)

        mediaSession.isActive = true
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    override fun after() {
        notificationManager.cancel(NOTIFICATION_ID)
        mediaSession.release()
    }
}
