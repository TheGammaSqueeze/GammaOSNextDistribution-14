/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.bluetooth.avrcpcontroller;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.media.MediaBrowserServiceCompat;

import com.android.bluetooth.BluetoothPrefs;
import com.android.bluetooth.R;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the MediaBrowserService interface to AVRCP and A2DP
 *
 * This service provides a means for external applications to access A2DP and AVRCP.
 * The applications are expected to use MediaBrowser (see API) and all the music
 * browsing/playback/metadata can be controlled via MediaBrowser and MediaController.
 *
 * The current behavior of MediaSessionCompat exposed by this service is as follows:
 * 1. MediaSessionCompat is active (i.e. SystemUI and other overview UIs can see updates) when
 * device is connected and first starts playing. Before it starts playing we do not activate the
 * session.
 * 1.1 The session is active throughout the duration of connection.
 * 2. The session is de-activated when the device disconnects. It will be connected again when (1)
 * happens.
 */
public class BluetoothMediaBrowserService extends MediaBrowserServiceCompat {
    private static final String TAG = "BluetoothMediaBrowserService";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private static BluetoothMediaBrowserService sBluetoothMediaBrowserService;

    private MediaSessionCompat mSession;

    // Browsing related structures.
    private List<MediaSessionCompat.QueueItem> mMediaQueue = new ArrayList<>();

    // Media Framework Content Style constants
    private static final String CONTENT_STYLE_SUPPORTED =
            "android.media.browse.CONTENT_STYLE_SUPPORTED";
    public static final String CONTENT_STYLE_PLAYABLE_HINT =
            "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT";
    public static final String CONTENT_STYLE_BROWSABLE_HINT =
            "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT";
    public static final int CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1;
    public static final int CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2;

    // Error messaging extras
    public static final String ERROR_RESOLUTION_ACTION_INTENT =
            "android.media.extras.ERROR_RESOLUTION_ACTION_INTENT";
    public static final String ERROR_RESOLUTION_ACTION_LABEL =
            "android.media.extras.ERROR_RESOLUTION_ACTION_LABEL";

    // Receiver for making sure our error message text matches the system locale
    private class LocaleChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_LOCALE_CHANGED)) {
                if (DBG) Log.d(TAG, "Locale has updated");
                if (sBluetoothMediaBrowserService == null) return;
                MediaSessionCompat session = sBluetoothMediaBrowserService.getSession();

                // Update playback state error message under new locale, if applicable
                MediaControllerCompat controller = session.getController();
                PlaybackStateCompat playbackState =
                        controller == null ? null : controller.getPlaybackState();
                if (playbackState != null && playbackState.getErrorMessage() != null) {
                    setErrorPlaybackState();
                }

                // Update queue title under new locale
                session.setQueueTitle(getString(R.string.bluetooth_a2dp_sink_queue_name));
            }
        }
    }

    private LocaleChangedReceiver mReceiver;

    /**
     * Initialize this BluetoothMediaBrowserService, creating our MediaSessionCompat, MediaPlayer
     * and MediaMetaData, and setting up mechanisms to talk with the AvrcpControllerService.
     */
    @Override
    public void onCreate() {
        if (DBG) Log.d(TAG, "Service Created");
        super.onCreate();

        // Create and configure the MediaSessionCompat
        mSession = new MediaSessionCompat(this, TAG);
        setSessionToken(mSession.getSessionToken());
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setQueueTitle(getString(R.string.bluetooth_a2dp_sink_queue_name));
        mSession.setQueue(mMediaQueue);
        setErrorPlaybackState();
        sBluetoothMediaBrowserService = this;

        mReceiver = new LocaleChangedReceiver();
        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "Service Destroyed");
        unregisterReceiver(mReceiver);
        mReceiver = null;
    }

    /**
     * BrowseResult is used to return the contents of a node along with a status. The status is
     * used to indicate success, a pending download, or error conditions. BrowseResult is used in
     * onLoadChildren() and getContents() in BluetoothMediaBrowserService and in getContents() in
     * AvrcpControllerService.
     * The following statuses have been implemented:
     * 1. SUCCESS - Contents have been retrieved successfully.
     * 2. DOWNLOAD_PENDING - Download is in progress and may or may not have contents to return.
     * 3. NO_DEVICE_CONNECTED - If no device is connected there are no contents to be retrieved.
     * 4. ERROR_MEDIA_ID_INVALID - Contents could not be retrieved as the media ID is invalid.
     * 5. ERROR_NO_AVRCP_SERVICE - Contents could not be retrieved as AvrcpControllerService is not
     *                             connected.
     */
    public static class BrowseResult {
        // Possible statuses for onLoadChildren
        public static final byte SUCCESS = 0x00;
        public static final byte DOWNLOAD_PENDING = 0x01;
        public static final byte NO_DEVICE_CONNECTED = 0x02;
        public static final byte ERROR_MEDIA_ID_INVALID = 0x03;
        public static final byte ERROR_NO_AVRCP_SERVICE = 0x04;

        private List<MediaItem> mResults;
        private final byte mStatus;

        List<MediaItem> getResults() {
            return mResults;
        }

        byte getStatus() {
            return mStatus;
        }

        String getStatusString() {
            switch (mStatus) {
                case DOWNLOAD_PENDING:
                    return "DOWNLOAD_PENDING";
                case SUCCESS:
                    return "SUCCESS";
                case NO_DEVICE_CONNECTED:
                    return "NO_DEVICE_CONNECTED";
                case ERROR_MEDIA_ID_INVALID:
                    return "ERROR_MEDIA_ID_INVALID";
                case ERROR_NO_AVRCP_SERVICE:
                    return "ERROR_NO_AVRCP_SERVICE";
                default:
                    return "UNDEFINED_ERROR_CASE";
            }
        }

        BrowseResult(List<MediaItem> results, byte status) {
            mResults = results;
            mStatus = status;
        }
    }

    BrowseResult getContents(final String parentMediaId) {
        AvrcpControllerService avrcpControllerService =
                AvrcpControllerService.getAvrcpControllerService();
        if (avrcpControllerService == null) {
            Log.w(TAG, "getContents(id=" + parentMediaId + "): AVRCP Controller Service not ready");
            return new BrowseResult(new ArrayList(0), BrowseResult.ERROR_NO_AVRCP_SERVICE);
        } else {
            return avrcpControllerService.getContents(parentMediaId);
        }
    }

    private void setErrorPlaybackState() {
        Bundle extras = new Bundle();
        extras.putString(ERROR_RESOLUTION_ACTION_LABEL,
                getString(R.string.bluetooth_connect_action));
        Intent launchIntent = new Intent();
        launchIntent.setAction(BluetoothPrefs.BLUETOOTH_SETTING_ACTION);
        launchIntent.addCategory(BluetoothPrefs.BLUETOOTH_SETTING_CATEGORY);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                launchIntent, flags);
        extras.putParcelable(ERROR_RESOLUTION_ACTION_INTENT, pendingIntent);
        PlaybackStateCompat errorState = new PlaybackStateCompat.Builder()
                .setErrorMessage(getString(R.string.bluetooth_disconnected))
                .setExtras(extras)
                .setState(PlaybackStateCompat.STATE_ERROR, 0, 0)
                .build();
        mSession.setPlaybackState(errorState);
    }

    private Bundle getDefaultStyle() {
        Bundle style = new Bundle();
        style.putBoolean(CONTENT_STYLE_SUPPORTED, true);
        style.putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE);
        style.putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE);
        return style;
    }

    @Override
    public synchronized void onLoadChildren(final String parentMediaId,
            final Result<List<MediaItem>> result) {
        if (DBG) Log.d(TAG, "Request for contents, id= " + parentMediaId);
        BrowseResult contents = getContents(parentMediaId);
        byte status = contents.getStatus();
        List<MediaItem> results = contents.getResults();
        if (status == BrowseResult.DOWNLOAD_PENDING && results == null) {
            Log.i(TAG, "Download pending - no results, id= " + parentMediaId);
            result.detach();
        } else {
            if (DBG) {
                Log.d(TAG, "Received Contents, id= " + parentMediaId + ", status= "
                        + contents.getStatusString() + ", results=" + results);
            }
            result.sendResult(results);
        }
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        Log.i(TAG, "Browser Client Connection Request, client='" + clientPackageName + "')");
        Bundle style = getDefaultStyle();
        return new BrowserRoot(BrowseTree.ROOT, style);
    }

    private void updateNowPlayingQueue(BrowseTree.BrowseNode node) {
        List<MediaItem> songList = node.getContents();
        mMediaQueue.clear();
        if (songList != null && songList.size() > 0) {
            for (MediaItem song : songList) {
                mMediaQueue.add(new MediaSessionCompat.QueueItem(
                        song.getDescription(),
                        mMediaQueue.size()));
            }
            mSession.setQueue(mMediaQueue);
        } else {
            mSession.setQueue(null);
        }
        if (DBG) Log.d(TAG, "Now Playing List Changed, queue=" + mMediaQueue);
    }

    private void clearNowPlayingQueue() {
        mMediaQueue.clear();
        mSession.setQueue(null);
    }

    static synchronized void notifyChanged(BrowseTree.BrowseNode node) {
        if (sBluetoothMediaBrowserService != null) {
            if (node.getScope() == AvrcpControllerService.BROWSE_SCOPE_NOW_PLAYING) {
                sBluetoothMediaBrowserService.updateNowPlayingQueue(node);
            } else {
                if (DBG) Log.d(TAG, "Browse Node contents changed, node=" + node);
                sBluetoothMediaBrowserService.notifyChildrenChanged(node.getID());
            }
        }
    }

    static synchronized void addressedPlayerChanged(MediaSessionCompat.Callback callback) {
        if (sBluetoothMediaBrowserService != null) {
            if (callback == null) {
                sBluetoothMediaBrowserService.setErrorPlaybackState();
                sBluetoothMediaBrowserService.clearNowPlayingQueue();
            }
            sBluetoothMediaBrowserService.mSession.setCallback(callback);
        } else {
            Log.w(TAG, "addressedPlayerChanged Unavailable");
        }
    }

    static synchronized void trackChanged(AvrcpItem track) {
        if (DBG) Log.d(TAG, "Track Changed, track=" + track);
        if (sBluetoothMediaBrowserService != null) {
            if (track != null) {
                sBluetoothMediaBrowserService.mSession.setMetadata(track.toMediaMetadata());
            } else {
                sBluetoothMediaBrowserService.mSession.setMetadata(null);
            }

        } else {
            Log.w(TAG, "trackChanged Unavailable");
        }
    }

    static synchronized void notifyChanged(PlaybackStateCompat playbackState) {
        if (DBG) Log.d(TAG, "Playback State Changed, state=" + playbackState);
        if (sBluetoothMediaBrowserService != null) {
            sBluetoothMediaBrowserService.mSession.setPlaybackState(playbackState);
        } else {
            Log.w(TAG, "notifyChanged Unavailable");
        }
    }

    /**
     * Send AVRCP Play command
     */
    public static synchronized void play() {
        if (sBluetoothMediaBrowserService != null) {
            sBluetoothMediaBrowserService.mSession.getController().getTransportControls().play();
        } else {
            Log.w(TAG, "play Unavailable");
        }
    }

    /**
     * Send AVRCP Pause command
     */
    public static synchronized void pause() {
        if (sBluetoothMediaBrowserService != null) {
            sBluetoothMediaBrowserService.mSession.getController().getTransportControls().pause();
        } else {
            Log.w(TAG, "pause Unavailable");
        }
    }

    /**
     * Get playback state
     */
    public static synchronized PlaybackStateCompat getPlaybackState() {
        if (sBluetoothMediaBrowserService != null) {
            MediaSessionCompat session = sBluetoothMediaBrowserService.getSession();
            if (session == null) return null;
            MediaControllerCompat controller = session.getController();
            PlaybackStateCompat playbackState =
                    controller == null ? null : controller.getPlaybackState();
            return playbackState;
        }
        return null;
    }

    /**
     * Get object for controlling playback
     */
    public static synchronized MediaControllerCompat.TransportControls getTransportControls() {
        if (sBluetoothMediaBrowserService != null) {
            return sBluetoothMediaBrowserService.mSession.getController().getTransportControls();
        } else {
            Log.w(TAG, "transportControls Unavailable");
            return null;
        }
    }

    /**
     * Set Media session active whenever we have Focus of any kind
     */
    public static synchronized void setActive(boolean active) {
        if (sBluetoothMediaBrowserService != null) {
            if (DBG) Log.d(TAG, "Setting the session active state to:" + active);
            sBluetoothMediaBrowserService.mSession.setActive(active);
        } else {
            Log.w(TAG, "setActive Unavailable");
        }
    }

    /**
     * Checks if the media session is active or not.
     * @return true if media session is active, false otherwise.
     */
    @VisibleForTesting
    public static synchronized boolean isActive() {
        if (sBluetoothMediaBrowserService != null) {
            return sBluetoothMediaBrowserService.mSession.isActive();
        }
        return false;
    }
    /**
     * Get Media session for updating state
     */
    public static synchronized MediaSessionCompat getSession() {
        if (sBluetoothMediaBrowserService != null) {
            return sBluetoothMediaBrowserService.mSession;
        } else {
            Log.w(TAG, "getSession Unavailable");
            return null;
        }
    }

    /**
     * Reset the state of BluetoothMediaBrowserService to that before a device connected
     */
    public static synchronized void reset() {
        if (sBluetoothMediaBrowserService != null) {
            sBluetoothMediaBrowserService.clearNowPlayingQueue();
            sBluetoothMediaBrowserService.mSession.setMetadata(null);
            sBluetoothMediaBrowserService.setErrorPlaybackState();
            sBluetoothMediaBrowserService.mSession.setCallback(null);
            if (DBG) Log.d(TAG, "Service state has been reset");
        } else {
            Log.w(TAG, "reset unavailable");
        }
    }

    /**
     * Get the state of the BluetoothMediaBrowserService as a debug string
     */
    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append(TAG + ":");
        if (sBluetoothMediaBrowserService != null) {
            MediaSessionCompat session = sBluetoothMediaBrowserService.getSession();
            MediaControllerCompat controller = session.getController();
            MediaMetadataCompat metadata = controller == null ? null : controller.getMetadata();
            PlaybackStateCompat playbackState =
                    controller == null ? null : controller.getPlaybackState();
            List<MediaSessionCompat.QueueItem> queue =
                    controller == null ? null : controller.getQueue();
            if (metadata != null) {
                sb.append("\n    track={");
                sb.append("title=" + metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
                sb.append(", artist="
                        + metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
                sb.append(", album=" + metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));
                sb.append(", track_number="
                        + metadata.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER));
                sb.append(", total_tracks="
                        + metadata.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS));
                sb.append(", genre=" + metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE));
                sb.append(", album_art="
                        + metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI));
                sb.append("}");
            } else {
                sb.append("\n    track=" + metadata);
            }
            sb.append("\n    playbackState=" + playbackState);
            sb.append("\n    queue=" + queue);
            sb.append("\n    internal_queue=" + sBluetoothMediaBrowserService.mMediaQueue);
            sb.append("\n    session active state=").append(isActive());
        } else {
            Log.w(TAG, "dump Unavailable");
            sb.append(" null");
        }
        return sb.toString();
    }
}
