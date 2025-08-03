/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.bluetooth.audio_util;

import android.annotation.Nullable;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.bluetooth.BluetoothEventLogger;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Objects;

/*
 * A class to synchronize Media Controller Callbacks and only pass through
 * an update once all the relevant information is current.
 *
 * TODO (apanicke): Once MediaPlayer2 is supported better, replace this class
 * with that.
 */
public class MediaPlayerWrapper {
    private static final String TAG = "AudioMediaPlayerWrapper";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    static boolean sTesting = false;
    private static final int PLAYBACK_STATE_CHANGE_EVENT_LOGGER_SIZE = 5;
    private static final String PLAYBACK_STATE_CHANGE_LOGGER_EVENT_TITLE =
            "BTAudio Playback State change Event";

    final Context mContext;
    private MediaController mMediaController;
    private String mPackageName;
    private Looper mLooper;
    private final BluetoothEventLogger mPlaybackStateChangeEventLogger;

    private MediaData mCurrentData;

    @GuardedBy("mCallbackLock")
    private MediaControllerListener mControllerCallbacks = null;
    private final Object mCallbackLock = new Object();
    private Callback mRegisteredCallback = null;

    public interface Callback {
        void mediaUpdatedCallback(MediaData data);
        void sessionUpdatedCallback(String packageName);
    }

    boolean isPlaybackStateReady() {
        if (getPlaybackState() == null) {
            d("isPlaybackStateReady(): PlaybackState is null");
            return false;
        }

        return true;
    }

    boolean isMetadataReady() {
        if (getMetadata() == null) {
            d("isMetadataReady(): Metadata is null");
            return false;
        }

        return true;
    }

    MediaPlayerWrapper(Context context, MediaController controller, Looper looper) {
        mContext = context;
        mMediaController = controller;
        mPackageName = controller.getPackageName();
        mLooper = looper;
        mPlaybackStateChangeEventLogger =
                new BluetoothEventLogger(
                        PLAYBACK_STATE_CHANGE_EVENT_LOGGER_SIZE,
                        PLAYBACK_STATE_CHANGE_LOGGER_EVENT_TITLE);

        mCurrentData = new MediaData(null, null, null);
        mCurrentData.queue = Util.toMetadataList(mContext, getQueue());
        mCurrentData.metadata = Util.toMetadata(mContext, getMetadata());
        mCurrentData.state = getPlaybackState();
    }

    void cleanup() {
        unregisterCallback();

        mMediaController = null;
        mLooper = null;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public MediaSession.Token getSessionToken() {
        return mMediaController.getSessionToken();
    }

    protected List<MediaSession.QueueItem> getQueue() {
        return mMediaController.getQueue();
    }

    protected MediaMetadata getMetadata() {
        return mMediaController.getMetadata();
    }

    Metadata getCurrentMetadata() {
        return Util.toMetadata(mContext, getMetadata());
    }

    public PlaybackState getPlaybackState() {
        return mMediaController.getPlaybackState();
    }

    long getActiveQueueID() {
        PlaybackState state = mMediaController.getPlaybackState();
        if (state == null) return -1;
        return state.getActiveQueueItemId();
    }

    List<Metadata> getCurrentQueue() {
        // MediaSession#QueueItem's MediaDescription doesn't necessarily include media duration,
        // so the playing media info metadata should be obtained by the MediaController.
        // MediaSession doesn't include the Playlist Metadata, only the current song one.
        Metadata mediaPlayingMetadata = getCurrentMetadata();

        // The queue metadata is built with QueueId in place of MediaId, so we can't compare it.
        // MediaDescription is usually compared via its title, artist and album.
        if (mediaPlayingMetadata != null) {
            for (Metadata metadata : mCurrentData.queue) {
                if (metadata.title == null || metadata.artist == null || metadata.album == null) {
                    // if one of the informations is missing we can't assume it is the same media.
                    continue;
                }
                if (metadata.title.equals(mediaPlayingMetadata.title)
                        && metadata.artist.equals(mediaPlayingMetadata.artist)
                        && metadata.album.equals(mediaPlayingMetadata.album)) {
                    // Replace default values by MediaController non default values.
                    metadata.replaceDefaults(mediaPlayingMetadata);
                }
            }
        }
        return mCurrentData.queue;
    }

    // We don't return the cached info here in order to always provide the freshest data.
    MediaData getCurrentMediaData() {
        MediaData data = new MediaData(
                getCurrentMetadata(),
                getPlaybackState(),
                getCurrentQueue());
        return data;
    }

    void playItemFromQueue(long qid) {
        // Return immediately if no queue exists.
        if (getQueue() == null) {
            Log.w(TAG, "playItemFromQueue: Trying to play item for player that has no queue: "
                    + mPackageName);
            return;
        }

        MediaController.TransportControls controller = mMediaController.getTransportControls();
        controller.skipToQueueItem(qid);
    }

    public void playCurrent() {
        MediaController.TransportControls controller = mMediaController.getTransportControls();
        controller.play();
    }

    public void stopCurrent() {
        MediaController.TransportControls controller = mMediaController.getTransportControls();
        controller.stop();
    }

    public void pauseCurrent() {
        MediaController.TransportControls controller = mMediaController.getTransportControls();
        controller.pause();
    }

    public void seekTo(long position) {
        MediaController.TransportControls controller = mMediaController.getTransportControls();
        controller.seekTo(position);
    }

    public void fastForward() {
        MediaController.TransportControls controller = mMediaController.getTransportControls();
        controller.fastForward();
    }

    public void rewind() {
        MediaController.TransportControls controller = mMediaController.getTransportControls();
        controller.rewind();
    }

    public void skipToPrevious() {
        MediaController.TransportControls controller = mMediaController.getTransportControls();
        controller.skipToPrevious();
    }

    public void skipToNext() {
        MediaController.TransportControls controller = mMediaController.getTransportControls();
        controller.skipToNext();
    }

    public void setPlaybackSpeed(float speed) {
        MediaController.TransportControls controller = mMediaController.getTransportControls();
        controller.setPlaybackSpeed(speed);
    }

    // TODO (apanicke): Implement shuffle and repeat support. Right now these use custom actions
    // and it may only be possible to do this with Google Play Music
    public boolean isShuffleSupported() {
        return false;
    }

    public boolean isRepeatSupported() {
        return false;
    }

    public boolean isShuffleSet() {
        return false;
    }

    public boolean isRepeatSet() {
        return false;
    }

    void toggleShuffle(boolean on) {
        return;
    }

    void toggleRepeat(boolean on) {
        return;
    }

    /**
     * Return whether the queue, metadata, and queueID are all in sync.
     */
    boolean isMetadataSynced() {
        List<MediaSession.QueueItem> queue = getQueue();
        if (queue != null && getActiveQueueID() != -1) {
            // Check if currentPlayingQueueId is in the current Queue
            MediaSession.QueueItem currItem = null;

            for (MediaSession.QueueItem item : queue) {
                if (item.getQueueId()
                        == getActiveQueueID()) { // The item exists in the current queue
                    currItem = item;
                    break;
                }
            }

            // Check if current playing song in Queue matches current Metadata
            Metadata qitem = Util.toMetadata(mContext, currItem);
            Metadata mdata = Util.toMetadata(mContext, getMetadata());
            if (currItem == null || !qitem.equals(mdata)) {
                if (DEBUG) {
                    Log.d(TAG, "Metadata currently out of sync for " + mPackageName);
                    Log.d(TAG, "  └ Current queueItem: " + qitem);
                    Log.d(TAG, "  └ Current metadata : " + mdata);
                }

                // Some player do not provide full song info in queue item, allow case
                // that only title and artist match.
                if (Objects.equals(qitem.title, mdata.title)
                        && Objects.equals(qitem.artist, mdata.artist)) {
                    Log.d(TAG, mPackageName + " Only Title and Artist info sync for metadata");
                    return true;
                }
                return false;
            }
        }

        return true;
    }

    /**
     * Register a callback which gets called when media updates happen. The callbacks are
     * called on the same Looper that was passed in to create this object.
     */
    void registerCallback(Callback callback) {
        if (callback == null) {
            e("Cannot register null callbacks for " + mPackageName);
            return;
        }

        synchronized (mCallbackLock) {
            mRegisteredCallback = callback;
        }

        // Update the current data since it could have changed while we weren't registered for
        // updates
        mCurrentData = new MediaData(
                Util.toMetadata(mContext, getMetadata()),
                getPlaybackState(),
                Util.toMetadataList(mContext, getQueue()));

        synchronized (mCallbackLock) {
            mControllerCallbacks = new MediaControllerListener(mMediaController, mLooper);
        }
    }

    /**
     * Unregisters from updates. Note, this doesn't require the looper to be shut down.
     */
    void unregisterCallback() {
        // Prevent a race condition where a callback could be called while shutting down
        synchronized (mCallbackLock) {
            mRegisteredCallback = null;
            if (mControllerCallbacks == null) return;
            mControllerCallbacks.cleanup();
            mControllerCallbacks = null;
        }
    }

    void updateMediaController(MediaController newController) {
        if (Objects.equals(newController, mMediaController)) return;

        mMediaController = newController;

        synchronized (mCallbackLock) {
            if (mRegisteredCallback == null || mControllerCallbacks == null) {
                d("Controller for " + mPackageName + " maybe is not activated.");
                return;
            }

            mControllerCallbacks.cleanup();

            // Update the current data since it could be different on the new controller for the
            // player
            mCurrentData = new MediaData(
                    Util.toMetadata(mContext, getMetadata()),
                    getPlaybackState(),
                    Util.toMetadataList(mContext, getQueue()));

            mControllerCallbacks = new MediaControllerListener(mMediaController, mLooper);
        }
        d("Controller for " + mPackageName + " was updated.");
    }

    private void sendMediaUpdate() {
        MediaData newData = new MediaData(
                Util.toMetadata(mContext, getMetadata()),
                getPlaybackState(),
                Util.toMetadataList(mContext, getQueue()));

        if (newData.equals(mCurrentData)) {
            // This may happen if the controller is fully synced by the time the
            // first update is completed
            Log.v(TAG, "Trying to update with last sent metadata");
            return;
        }

        synchronized (mCallbackLock) {
            if (mRegisteredCallback == null) {
                Log.e(TAG, mPackageName
                        + ": Trying to send an update with no registered callback");
                return;
            }

            Log.v(TAG, "trySendMediaUpdate(): Metadata has been updated for " + mPackageName);
            mRegisteredCallback.mediaUpdatedCallback(newData);
        }

        mCurrentData = newData;
    }

    class TimeoutHandler extends Handler {
        private static final int MSG_TIMEOUT = 0;
        private static final long CALLBACK_TIMEOUT_MS = 2000;

        TimeoutHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what != MSG_TIMEOUT) {
                Log.wtf(TAG, "Unknown message on timeout handler: " + msg.what);
                return;
            }

            Log.e(TAG, "Timeout while waiting for metadata to sync for " + mPackageName);
            Log.e(TAG, "  └ Current Metadata: " +  Util.toMetadata(mContext, getMetadata()));
            Log.e(TAG, "  └ Current Playstate: " + getPlaybackState());
            List<Metadata> current_queue = Util.toMetadataList(mContext, getQueue());
            for (int i = 0; i < current_queue.size(); i++) {
                Log.e(TAG, "  └ QueueItem(" + i + "): " + current_queue.get(i));
            }

            sendMediaUpdate();

            // TODO(apanicke): Add metric collection here.

            if (sTesting) Log.wtf(TAG, "Crashing the stack");
        }
    }

    class MediaControllerListener extends MediaController.Callback {
        private final Object mTimeoutHandlerLock = new Object();
        private Handler mTimeoutHandler;
        private MediaController mController;

        MediaControllerListener(MediaController controller, Looper newLooper) {
            synchronized (mTimeoutHandlerLock) {
                mTimeoutHandler = new TimeoutHandler(newLooper);

                mController = controller;
                // Register the callbacks to execute on the same thread as the timeout thread. This
                // prevents a race condition where a timeout happens at the same time as an update.
                mController.registerCallback(this, mTimeoutHandler);
            }
        }

        void cleanup() {
            synchronized (mTimeoutHandlerLock) {
                mController.unregisterCallback(this);
                mController = null;
                mTimeoutHandler.removeMessages(TimeoutHandler.MSG_TIMEOUT);
                mTimeoutHandler = null;
            }
        }

        void trySendMediaUpdate() {
            synchronized (mTimeoutHandlerLock) {
                if (mTimeoutHandler == null) return;
                mTimeoutHandler.removeMessages(TimeoutHandler.MSG_TIMEOUT);

                if (!isMetadataSynced()) {
                    d("trySendMediaUpdate(): Starting media update timeout");
                    mTimeoutHandler.sendEmptyMessageDelayed(TimeoutHandler.MSG_TIMEOUT,
                            TimeoutHandler.CALLBACK_TIMEOUT_MS);
                    return;
                }
            }

            sendMediaUpdate();
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata mediaMetadata) {
            if (!isMetadataReady()) {
                Log.v(
                        TAG,
                        "onMetadataChanged(): "
                                + mPackageName
                                + " tried to update with no metadata");
                return;
            }

            if (DEBUG) {
                Log.v(TAG, "onMetadataChanged(): " + mPackageName + " : "
                        + Util.toMetadata(mContext, mediaMetadata));
            }

            if (!Objects.equals(mediaMetadata, getMetadata())) {
                e("The callback metadata doesn't match controller metadata");
            }

            // TODO: Certain players update different metadata fields as they load, such as Album
            // Art. For track changed updates we only care about the song information like title
            // and album and duration. In the future we can use this to know when Album art is
            // loaded.

            // TODO: Spotify needs a metadata update debouncer as it sometimes updates the metadata
            // twice in a row with the only difference being that the song duration is rounded to
            // the nearest second.
            if (Objects.equals(Util.toMetadata(mContext, mediaMetadata), mCurrentData.metadata)) {
                Log.w(TAG, "onMetadataChanged(): " + mPackageName
                        + " tried to update with no new data");
                return;
            }

            trySendMediaUpdate();
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            if (!isPlaybackStateReady()) {
                Log.v(
                        TAG,
                        "onPlaybackStateChanged(): "
                                + mPackageName
                                + " tried to update with no state");
                return;
            }

            mPlaybackStateChangeEventLogger.logv(
                    TAG, "onPlaybackStateChanged(): " + mPackageName + " : " + state);

            if (!playstateEquals(state, getPlaybackState())) {
                e("The callback playback state doesn't match the current state");
            }

            if (playstateEquals(state, mCurrentData.state)) {
                Log.w(TAG, "onPlaybackStateChanged(): " + mPackageName
                        + " tried to update with no new data");
                return;
            }

            // If state isn't null and there is no playstate, ignore the update.
            if (state != null && state.getState() == PlaybackState.STATE_NONE) {
                Log.v(TAG, "Waiting to send update as controller has no playback state");
                return;
            }

            trySendMediaUpdate();
        }

        @Override
        public void onQueueChanged(@Nullable List<MediaSession.QueueItem> queue) {
            if (!isPlaybackStateReady() || !isMetadataReady()) {
                Log.v(TAG, "onQueueChanged(): " + mPackageName
                        + " tried to update with no queue");
                return;
            }

            Log.v(TAG, "onQueueChanged(): " + mPackageName);

            if (!Objects.equals(queue, getQueue())) {
                e("The callback queue isn't the current queue");
            }

            List<Metadata> current_queue = Util.toMetadataList(mContext, queue);
            if (current_queue.equals(mCurrentData.queue)) {
                Log.w(TAG, "onQueueChanged(): " + mPackageName
                        + " tried to update with no new data");
                return;
            }

            if (DEBUG) {
                for (int i = 0; i < current_queue.size(); i++) {
                    Log.d(TAG, "  └ QueueItem(" + i + "): " + current_queue.get(i));
                }
            }

            trySendMediaUpdate();
        }

        @Override
        public void onSessionDestroyed() {
            Log.w(TAG, "The session was destroyed " + mPackageName);
            mRegisteredCallback.sessionUpdatedCallback(mPackageName);
        }

        @VisibleForTesting
        Handler getTimeoutHandler() {
            return mTimeoutHandler;
        }
    }

    /**
     * Checks wheter the core information of two PlaybackStates match. This function allows a
     * certain amount of deviation between the position fields of the PlaybackStates. This is to
     * prevent matches from failing when updates happen in quick succession.
     *
     * The maximum allowed deviation is defined by PLAYSTATE_BOUNCE_IGNORE_PERIOD and is measured
     * in milliseconds.
     */
    private static final long PLAYSTATE_BOUNCE_IGNORE_PERIOD = 500;
    public static boolean playstateEquals(PlaybackState a, PlaybackState b) {
        if (a == b) return true;

        if (a != null && b != null
                && a.getState() == b.getState()
                && a.getActiveQueueItemId() == b.getActiveQueueItemId()
                && Math.abs(a.getPosition() - b.getPosition()) < PLAYSTATE_BOUNCE_IGNORE_PERIOD) {
            return true;
        }

        return false;
    }

    private static void e(String message) {
        if (sTesting) {
            Log.wtf(TAG, message);
        } else {
            Log.e(TAG, message);
        }
    }

    private void d(String message) {
        if (DEBUG) Log.d(TAG, mPackageName + ": " + message);
    }

    @VisibleForTesting
    Handler getTimeoutHandler() {
        synchronized (mCallbackLock) {
            if (mControllerCallbacks == null) return null;
            return mControllerCallbacks.getTimeoutHandler();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(mMediaController.toString() + "\n");
        sb.append("Current Data:\n");
        sb.append("  Song: " + mCurrentData.metadata + "\n");
        sb.append("  PlayState: " + mCurrentData.state + "\n");
        sb.append("  Queue: size=" + mCurrentData.queue.size() + "\n");
        for (Metadata data : mCurrentData.queue) {
            sb.append("    " + data + "\n");
        }
        mPlaybackStateChangeEventLogger.dump(sb);
        return sb.toString();
    }
}
