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

package android.platform.helpers.media;

import static android.platform.uiautomator_helpers.DeviceHelpers.assertVisibility;

import static org.junit.Assert.assertNotNull;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Rect;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.util.HealthTestingUtils;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Media instrumentation for testing. */
public final class MediaInstrumentation {

    private static final int WAIT_TIME_MILLIS = 5000;
    private static final String PKG = "com.android.systemui";
    private static final String MEDIA_CONTROLLER_RES_ID = "qs_media_controls";
    private static int notificationID = 0;

    private final UiDevice mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    private final String mChannelId;
    private final NotificationManager mManager;
    private final MediaSession mMediaSession;
    private final Handler mHandler;
    private final MediaSessionCallback mCallback;
    private final Context mContext;
    // TODO(bennolin): support legacy version media controller. Please refer
    //  go/media-t-app-changes for more details.
    private final boolean mUseLegacyVersion;
    private final List<Consumer<Integer>> mMediaSessionStateChangedListeners;
    private final int mNotificationId;
    private final MockMediaPlayer mPlayer;
    private int mCurrentMediaState;

    // the idx of mMediaSources which represents current media source.
    private int mCurrentMediaSource;
    private final List<MediaMetadata> mMediaSources;

    private MediaInstrumentation(
            Context context, MediaSession mediaSession,
            List<MediaMetadata> mediaSources,
            String channelId, boolean useLegacyVersion
    ) {
        mHandler = new Handler(Looper.getMainLooper());
        mContext = context;
        mMediaSession = mediaSession;
        mChannelId = channelId;
        mUseLegacyVersion = useLegacyVersion;
        mManager = context.getSystemService(NotificationManager.class);
        mCurrentMediaState = PlaybackState.STATE_NONE;
        mPlayer = new MockMediaPlayer();
        mCallback = new MediaSessionCallback(mPlayer);
        mMediaSources = mediaSources;
        mCurrentMediaSource = 0;
        mNotificationId = ++notificationID;
        mMediaSessionStateChangedListeners = new ArrayList<>();
        initialize();
    }

    private void initialize() {
        mHandler.post(() -> mMediaSession.setCallback(mCallback));
        mCallback.addOnMediaStateChangedListener(this::onMediaSessionStateChanged);
        mCallback.addOnMediaStateChangedListener(this::onMediaSessionSkipTo);
        MediaMetadata source = mMediaSources.stream().findFirst().orElse(null);
        mMediaSession.setMetadata(source);
        mMediaSession.setActive(true);
        mPlayer.setDataSource(source);
        mPlayer.setOnCompletionListener(() -> setCurrentMediaState(PlaybackState.STATE_STOPPED));
        setCurrentMediaState(
                source == null ? PlaybackState.STATE_NONE : PlaybackState.STATE_STOPPED);
    }

    Notification.Builder buildNotification() {
        return new Notification.Builder(mContext, mChannelId)
                .setContentTitle("MediaInstrumentation")
                .setContentText("media")
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(mMediaSession.getSessionToken()));
    }

    public void createNotification() {
        mManager.notify(mNotificationId, buildNotification().build());
    }

    /** Cancel the Media notification */
    public void cancelNotification() {
        mManager.cancel(mNotificationId);
    }

    UiObject2 scrollToMediaNotification(MediaMetadata meta) {
        final BySelector qsScrollViewSelector = By.res(PKG, "expanded_qs_scroll_view");
        final BySelector mediaTitleSelector = By.res(PKG, "header_title")
                .text(meta.getString(MediaMetadata.METADATA_KEY_TITLE));
        final BySelector umoSelector = By.res(PKG, MEDIA_CONTROLLER_RES_ID)
                .hasDescendant(mediaTitleSelector);
        UiObject2 notification = mDevice.wait(Until.findObject(umoSelector), WAIT_TIME_MILLIS);
        if (notification == null) {
            // Try to scroll down the QS container to make UMO visible.
            UiObject2 qsScrollView = mDevice.wait(Until.findObject(qsScrollViewSelector),
                    WAIT_TIME_MILLIS);
            assertNotNull("Unable to scroll the QS container.", qsScrollView);
            qsScrollView.scroll(Direction.DOWN, 1.0f, 100);
            notification = mDevice.wait(Until.findObject(umoSelector), WAIT_TIME_MILLIS);
        }
        assertNotNull("Unable to find UMO.", notification);
        // The UMO may still not be fully visible, double check it's visibility.
        notification = ensureUMOFullyVisible(notification);
        assertNotNull("UMO isn't fully visible.", notification);
        mDevice.waitForIdle();
        HealthTestingUtils.waitForValueToSettle(
                () -> "UMO isn't settle after timeout.", notification::getVisibleBounds);
        return notification;
    }

    private UiObject2 ensureUMOFullyVisible(UiObject2 umo) {
        final BySelector footerSelector = By.res(PKG, "qs_footer_actions");
        UiObject2 footer = mDevice.wait(Until.findObject(footerSelector), WAIT_TIME_MILLIS);
        assertNotNull("Can't find QS actions footer.", footer);
        Rect umoBound = umo.getVisibleBounds();
        Rect footerBound = footer.getVisibleBounds();
        int distance = umoBound.bottom - footerBound.top;
        if (distance <= 0) {
            return umo;
        }
        distance += footerBound.height();
        UiObject2 scrollable = mDevice.wait(Until.findObject(By.scrollable(true)), WAIT_TIME_MILLIS);
        scrollable.scroll(
                Direction.DOWN, (float)distance / scrollable.getVisibleBounds().height(), 100);
        return mDevice.wait(Until.findObject(By.res(umo.getResourceName())), WAIT_TIME_MILLIS);
    }

    /**
     * Find the UMO that belongs to the current MediaInstrumentation (Media Session).
     * If the UMO can't be found, the function will raise an assertion error.
     *
     * @return MediaController
     */
    public MediaController getMediaNotification() {
        MediaMetadata source = mMediaSources.stream().findFirst().orElseThrow();
        UiObject2 notification = scrollToMediaNotification(source);
        return new MediaController(this, notification);
    }

    /**
     * Find the UMO in current view. This method will only check UMO in current view page different
     * than {@link #getMediaNotification()} to seek UMO in quick setting view.
     *
     * @return MediaController
     * @throws AssertionError if the UMO can't be found in current view.
     */
    public MediaController getMediaNotificationInCurrentView() {
        MediaMetadata source = mMediaSources.stream().findFirst().orElseThrow();
        final BySelector mediaTitleSelector = By.res(PKG, "header_title")
                .text(source.getString(MediaMetadata.METADATA_KEY_TITLE));
        final BySelector umoSelector = By.res(PKG, MEDIA_CONTROLLER_RES_ID)
                .hasDescendant(mediaTitleSelector);
        UiObject2 notification = mDevice.wait(Until.findObject(umoSelector), WAIT_TIME_MILLIS);
        assertNotNull("Unable to find UMO.", notification);
        mDevice.waitForIdle();
        HealthTestingUtils.waitForValueToSettle(
                () -> "UMO isn't settle after timeout.", notification::getVisibleBounds);
        return new MediaController(this, notification);
    }

    /**
     * Wait for UMO is gone.
     *
     * @param timeout Maximum amount of time to wait in milliseconds.
     * @return The final result returned by the condition, or null if the condition was not met
     *     before the timeout.
     */
    public boolean waitUmoGone(long timeout) {
        return mDevice.wait(Until.gone(By.res(PKG, MEDIA_CONTROLLER_RES_ID)), timeout);
    }

    public boolean isMediaNotificationVisible() {
        return mDevice.hasObject(By.res(PKG, MEDIA_CONTROLLER_RES_ID));
    }

    /** Assert that the media notification is visible with a 10 second timeout. */
    public void assertMediaNotificationVisible() {
        assertVisibility(
                By.res(PKG, MEDIA_CONTROLLER_RES_ID),
                true,
                Duration.ofSeconds(10),
                () -> "UMO should be visible on lockscreen.");
    }

    public void addMediaSessionStateChangedListeners(Consumer<Integer> listener) {
        mMediaSessionStateChangedListeners.add(listener);
    }

    public void clearMediaSessionStateChangedListeners() {
        mMediaSessionStateChangedListeners.clear();
    }

    private void onMediaSessionStateChanged(int state) {
        setCurrentMediaState(state);
        for (Consumer<Integer> listener : mMediaSessionStateChangedListeners) {
            listener.accept(state);
        }
    }

    private void onMediaSessionSkipTo(int state) {
        final int sources = mMediaSources.size();
        if (sources <= 0) { // no media sources to skip to
            return;
        }
        switch (state) {
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
                mCurrentMediaSource = (mCurrentMediaSource + 1) % sources;
                break;
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
                mCurrentMediaSource = (mCurrentMediaSource - 1) % sources;
                break;
            default: // the state changing isn't related to skip.
                return;
        }
        mMediaSession.setMetadata(mMediaSources.get(mCurrentMediaSource));
        mPlayer.setDataSource(mMediaSources.get(mCurrentMediaSource));
        mPlayer.reset();
        mPlayer.pause();
        setCurrentMediaState(PlaybackState.STATE_PAUSED);
        createNotification();
    }

    private void updatePlaybackState() {
        if (mUseLegacyVersion) {
            // TODO(bennolin): add legacy version, be aware of `setState`  and  `ACTION_SEEK_TO`
            //  are still relevant to legacy version controller.
            return;
        }
        mMediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(getAvailableActions(mCurrentMediaState))
                .setState(mCurrentMediaState, mPlayer.getCurrentPosition(), 1.0f)
                .build());
    }

    /**
     * Sets the Media's state to the given state.
     *
     * @param state the {@link PlaybackState}.
     */
    public void setCurrentMediaState(int state) {
        mCurrentMediaState = state;
        updatePlaybackState();
    }

    private Long getAvailableActions(int state) {
        switch (state) {
            case PlaybackState.STATE_PLAYING:
                return PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            case PlaybackState.STATE_PAUSED:
                return PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_STOP
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            case PlaybackState.STATE_STOPPED:
                return PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            default:
                return PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO;
        }
    }

    public static class Builder {

        private final boolean mUseLegacyVersion;
        private final Context mContext;
        private final MediaSession mSession;
        private String mChannelId;
        private final List<MediaMetadata> mDataSources;

        public Builder(Context context, MediaSession session) {
            mUseLegacyVersion = false;
            mContext = context;
            mChannelId = "";
            mSession = session;
            mDataSources = new ArrayList<>();
        }

        public Builder setChannelId(String id) {
            mChannelId = id;
            return this;
        }

        public Builder addDataSource(MediaMetadata source) {
            mDataSources.add(source);
            return this;
        }

        public MediaInstrumentation build() {
            if (mChannelId.isEmpty()) {
                NotificationManager manager = mContext.getSystemService(NotificationManager.class);
                mChannelId = MediaInstrumentation.class.getCanonicalName();
                NotificationChannel channel = new NotificationChannel(
                        mChannelId, "Default", NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
            return new MediaInstrumentation(
                    mContext, mSession, mDataSources, mChannelId, mUseLegacyVersion);
        }
    }
}
