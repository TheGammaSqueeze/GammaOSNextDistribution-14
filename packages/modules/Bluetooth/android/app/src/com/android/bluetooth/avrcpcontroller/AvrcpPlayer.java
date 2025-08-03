/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.bluetooth.BluetoothDevice;
import android.net.Uri;
import android.os.SystemClock;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.util.Arrays;

/*
 * Contains information about remote player
 */
class AvrcpPlayer {
    private static final String TAG = "AvrcpPlayer";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    public static final int DEFAULT_ID = -1;

    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_BROADCASTING_AUDIO = 2;
    public static final int TYPE_BROADCASTING_VIDEO = 3;

    public static final int SUB_TYPE_UNKNOWN = -1;
    public static final int SUB_TYPE_AUDIO_BOOK = 0;
    public static final int SUB_TYPE_PODCAST = 1;

    public static final int FEATURE_PLAY = 40;
    public static final int FEATURE_STOP = 41;
    public static final int FEATURE_PAUSE = 42;
    public static final int FEATURE_REWIND = 44;
    public static final int FEATURE_FAST_FORWARD = 45;
    public static final int FEATURE_FORWARD = 47;
    public static final int FEATURE_PREVIOUS = 48;
    public static final int FEATURE_BROWSING = 59;
    public static final int FEATURE_NOW_PLAYING = 65;

    private BluetoothDevice mDevice;
    private int mPlayStatus = PlaybackStateCompat.STATE_NONE;
    private long mPlayTime = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
    private long mPlayTimeUpdate = 0;
    private float mPlaySpeed = 1;
    private int mId;
    private String mName = "";
    private int mPlayerType;
    private byte[] mPlayerFeatures = new byte[16];
    private long mAvailableActions = PlaybackStateCompat.ACTION_PREPARE;
    private AvrcpItem mCurrentTrack;
    private PlaybackStateCompat mPlaybackStateCompat;
    private PlayerApplicationSettings mSupportedPlayerApplicationSettings =
            new PlayerApplicationSettings();
    private PlayerApplicationSettings mCurrentPlayerApplicationSettings;

    private AvrcpPlayer(BluetoothDevice device, int id, int playerType, int playerSubType,
            String name, byte[] playerFeatures, int playStatus) {
        mDevice = device;
        mId = id;
        mName = name;
        mPlayerType = playerType;
        mPlayerFeatures = Arrays.copyOf(playerFeatures, playerFeatures.length);
        PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(mAvailableActions);
        mPlaybackStateCompat = playbackStateBuilder.build();
        updateAvailableActions();
        setPlayStatus(playStatus);
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }

    public int getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public void setPlayTime(int playTime) {
        mPlayTime = playTime;
        mPlayTimeUpdate = SystemClock.elapsedRealtime();
        mPlaybackStateCompat = new PlaybackStateCompat.Builder(mPlaybackStateCompat).setState(
                mPlayStatus, mPlayTime,
                mPlaySpeed).build();
    }

    public long getPlayTime() {
        return mPlayTime;
    }

    public void setPlayStatus(int playStatus) {
        if (mPlayTime != PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN) {
            mPlayTime += mPlaySpeed * (SystemClock.elapsedRealtime()
                    - mPlaybackStateCompat.getLastPositionUpdateTime());
        }
        mPlayStatus = playStatus;
        switch (mPlayStatus) {
            case PlaybackStateCompat.STATE_STOPPED:
                mPlaySpeed = 0;
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                mPlaySpeed = 1;
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                mPlaySpeed = 0;
                break;
            case PlaybackStateCompat.STATE_FAST_FORWARDING:
                mPlaySpeed = 3;
                break;
            case PlaybackStateCompat.STATE_REWINDING:
                mPlaySpeed = -3;
                break;
        }

        mPlaybackStateCompat = new PlaybackStateCompat.Builder(mPlaybackStateCompat).setState(
                mPlayStatus, mPlayTime,
                mPlaySpeed).build();
    }

    public void setSupportedPlayerApplicationSettings(
            PlayerApplicationSettings playerApplicationSettings) {
        mSupportedPlayerApplicationSettings = playerApplicationSettings;
        updateAvailableActions();
    }

    public void setCurrentPlayerApplicationSettings(
            PlayerApplicationSettings playerApplicationSettings) {
        Log.d(TAG, "Settings changed");
        mCurrentPlayerApplicationSettings = playerApplicationSettings;
        MediaSessionCompat session = BluetoothMediaBrowserService.getSession();
        session.setRepeatMode(mCurrentPlayerApplicationSettings.getSetting(
                PlayerApplicationSettings.REPEAT_STATUS));
        session.setShuffleMode(mCurrentPlayerApplicationSettings.getSetting(
                PlayerApplicationSettings.SHUFFLE_STATUS));
    }

    public int getPlayStatus() {
        return mPlayStatus;
    }

    public boolean supportsFeature(int featureId) {
        int byteNumber = featureId / 8;
        byte bitMask = (byte) (1 << (featureId % 8));
        return (mPlayerFeatures[byteNumber] & bitMask) == bitMask;
    }

    public boolean supportsSetting(int settingType, int settingValue) {
        return mSupportedPlayerApplicationSettings.supportsSetting(settingType, settingValue);
    }

    public PlaybackStateCompat getPlaybackState() {
        if (DBG) {
            Log.d(TAG, "getPlayBackState state " + mPlayStatus + " time " + mPlayTime);
        }
        return mPlaybackStateCompat;
    }

    public synchronized void updateCurrentTrack(AvrcpItem update) {
        if (update != null) {
            long trackNumber = update.getTrackNumber();
            mPlaybackStateCompat = new PlaybackStateCompat.Builder(
                    mPlaybackStateCompat).setActiveQueueItemId(
                    trackNumber - 1).build();
        }
        mCurrentTrack = update;
    }

    public synchronized boolean notifyImageDownload(String uuid, Uri imageUri) {
        if (DBG) Log.d(TAG, "Got an image download -- uuid=" + uuid + ", uri=" + imageUri);
        if (uuid == null || imageUri == null || mCurrentTrack == null) return false;
        if (uuid.equals(mCurrentTrack.getCoverArtUuid())) {
            mCurrentTrack.setCoverArtLocation(imageUri);
            if (DBG) Log.d(TAG, "Image UUID '" + uuid + "' was added to current track.");
            return true;
        }
        return false;
    }

    public synchronized AvrcpItem getCurrentTrack() {
        return mCurrentTrack;
    }

    private void updateAvailableActions() {
        mAvailableActions = PlaybackStateCompat.ACTION_PREPARE;
        if (supportsFeature(FEATURE_PLAY)) {
            mAvailableActions = mAvailableActions | PlaybackStateCompat.ACTION_PLAY;
        }
        if (supportsFeature(FEATURE_STOP)) {
            mAvailableActions = mAvailableActions | PlaybackStateCompat.ACTION_STOP;
        }
        if (supportsFeature(FEATURE_PAUSE)) {
            mAvailableActions = mAvailableActions | PlaybackStateCompat.ACTION_PAUSE;
        }
        if (supportsFeature(FEATURE_REWIND)) {
            mAvailableActions = mAvailableActions | PlaybackStateCompat.ACTION_REWIND;
        }
        if (supportsFeature(FEATURE_FAST_FORWARD)) {
            mAvailableActions = mAvailableActions | PlaybackStateCompat.ACTION_FAST_FORWARD;
        }
        if (supportsFeature(FEATURE_FORWARD)) {
            mAvailableActions = mAvailableActions | PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
        }
        if (supportsFeature(FEATURE_PREVIOUS)) {
            mAvailableActions = mAvailableActions | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        }
        if (mSupportedPlayerApplicationSettings.supportsSetting(
                PlayerApplicationSettings.REPEAT_STATUS)) {
            mAvailableActions |= PlaybackStateCompat.ACTION_SET_REPEAT_MODE;
        }
        if (mSupportedPlayerApplicationSettings.supportsSetting(
                PlayerApplicationSettings.SHUFFLE_STATUS)) {
            mAvailableActions |= PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
        }
        mPlaybackStateCompat = new PlaybackStateCompat.Builder(mPlaybackStateCompat)
                .setActions(mAvailableActions).build();

        if (DBG) Log.d(TAG, "Supported Actions = " + mAvailableActions);
    }

    @Override
    public String toString() {
        return "<AvrcpPlayer id=" + mId + " name=" + mName + " track=" + mCurrentTrack
                + " playState=" + mPlaybackStateCompat + ">";
    }

    /**
     * A Builder object for an AvrcpPlayer
     */
    public static class Builder {
        private static final String TAG = "AvrcpPlayer.Builder";
        private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

        private BluetoothDevice mDevice = null;
        private int mPlayerId = AvrcpPlayer.DEFAULT_ID;
        private int mPlayerType = AvrcpPlayer.TYPE_UNKNOWN;
        private int mPlayerSubType = AvrcpPlayer.SUB_TYPE_UNKNOWN;
        private String mPlayerName = null;
        private byte[] mSupportedFeatures = new byte[16];

        private int mPlayStatus = PlaybackStateCompat.STATE_NONE;
        private long mPlayTime = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        private float mPlaySpeed = 1;
        private long mPlayTimeUpdate = 0;

        private AvrcpItem mTrack = null;

        /**
         * Set the device that this Player came from
         *
         * @param device The BleutoothDevice representing the remote device
         * @return This object, so you can continue building
         */
        public Builder setDevice(BluetoothDevice device) {
            mDevice = device;
            return this;
        }

        /**
         * Set the Player ID for this Player
         *
         * @param playerId The ID for this player, defined in AVRCP 6.10.2.1
         * @return This object, so you can continue building
         */
        public Builder setPlayerId(int playerId) {
            mPlayerId = playerId;
            return this;
        }

        /**
         * Set the Player Type for this Player
         *
         * @param playerType The type for this player, defined in AVRCP 6.10.2.1
         * @return This object, so you can continue building
         */
        public Builder setPlayerType(int playerType) {
            mPlayerType = playerType;
            return this;
        }

        /**
         * Set the Player Sub-type for this Player
         *
         * @param playerSubType The sub-type for this player, defined in AVRCP 6.10.2.1
         * @return This object, so you can continue building
         */
        public Builder setPlayerSubType(int playerSubType) {
            mPlayerSubType = playerSubType;
            return this;
        }

        /**
         * Set the name for this Player. This is what users will see when browsing.
         *
         * @param name The name for this player, defined in AVRCP 6.10.2.1
         * @return This object, so you can continue building
         */
        public Builder setName(String name) {
            mPlayerName = name;
            return this;
        }

        /**
         * Set the entire set of supported features for this Player.
         *
         * @param features The feature set for this player, defined in AVRCP 6.10.2.1
         * @return This object, so you can continue building
         */
        public Builder setSupportedFeatures(byte[] supportedFeatures) {
            mSupportedFeatures = supportedFeatures;
            return this;
        }

        /**
         * Set a single features as supported for this Player.
         *
         * @param feature The feature for this player, defined in AVRCP 6.10.2.1
         * @return This object, so you can continue building
         */
        public Builder setSupportedFeature(int feature) {
            int byteNumber = feature / 8;
            byte bitMask = (byte) (1 << (feature % 8));
            mSupportedFeatures[byteNumber] = (byte) (mSupportedFeatures[byteNumber] | bitMask);
            return this;
        }

        /**
         * Set the initial play status of the Player.
         *
         * @param playStatus The play state for this player as a PlaybackStateCompat.STATE_* value
         * @return This object, so you can continue building
         */
        public Builder setPlayStatus(int playStatus) {
            mPlayStatus = playStatus;
            return this;
        }

        /**
         * Set the initial play status of the Player.
         *
         * @param track The initial track for this player
         * @return This object, so you can continue building
         */
        public Builder setCurrentTrack(AvrcpItem track) {
            mTrack = track;
            return this;
        }

        public AvrcpPlayer build() {
            AvrcpPlayer player = new AvrcpPlayer(mDevice, mPlayerId, mPlayerType, mPlayerSubType,
                    mPlayerName, mSupportedFeatures, mPlayStatus);
            player.updateCurrentTrack(mTrack);
            return player;
        }
    }
}
