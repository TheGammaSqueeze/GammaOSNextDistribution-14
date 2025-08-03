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

package com.android.bluetooth.avrcp;

import android.annotation.NonNull;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUtils;
import android.bluetooth.IBluetoothAvrcpTarget;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Looper;
import android.os.UserManager;
import android.sysprop.BluetoothProperties;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import com.android.bluetooth.BluetoothEventLogger;
import com.android.bluetooth.BluetoothMetricsProto;
import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.audio_util.MediaData;
import com.android.bluetooth.audio_util.MediaPlayerList;
import com.android.bluetooth.audio_util.MediaPlayerWrapper;
import com.android.bluetooth.audio_util.Metadata;
import com.android.bluetooth.audio_util.PlayStatus;
import com.android.bluetooth.audio_util.PlayerInfo;
import com.android.bluetooth.audio_util.PlayerSettingsManager;
import com.android.bluetooth.btservice.MetricsLogger;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Objects;

/**
 * Provides Bluetooth AVRCP Target profile as a service in the Bluetooth application.
 * @hide
 */
public class AvrcpTargetService extends ProfileService {
    private static final String TAG = "AvrcpTargetService";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int AVRCP_MAX_VOL = 127;
    private static final int MEDIA_KEY_EVENT_LOGGER_SIZE = 20;
    private static final String MEDIA_KEY_EVENT_LOGGER_TITLE = "BTAudio Media Key Events";
    private static int sDeviceMaxVolume = 0;
    private final BluetoothEventLogger mMediaKeyEventLogger =
            new BluetoothEventLogger(MEDIA_KEY_EVENT_LOGGER_SIZE, MEDIA_KEY_EVENT_LOGGER_TITLE);

    private AvrcpVersion mAvrcpVersion;
    private MediaPlayerList mMediaPlayerList;
    private PlayerSettingsManager mPlayerSettingsManager;
    private AudioManager mAudioManager;
    private AvrcpBroadcastReceiver mReceiver;
    private AvrcpNativeInterface mNativeInterface;
    private AvrcpVolumeManager mVolumeManager;
    private ServiceFactory mFactory = new ServiceFactory();
    private final BroadcastReceiver mUserUnlockedReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // EXTRA_USER_HANDLE is sent for ACTION_USER_UNLOCKED
                    // (even if the documentation doesn't mention it)
                    final int userId =
                            intent.getIntExtra(
                                    Intent.EXTRA_USER_HANDLE,
                                    BluetoothUtils.USER_HANDLE_NULL.getIdentifier());
                    if (userId == BluetoothUtils.USER_HANDLE_NULL.getIdentifier()) {
                        Log.e(TAG, "userChangeReceiver received an invalid EXTRA_USER_HANDLE");
                        return;
                    }
                    if (mMediaPlayerList != null) {
                        mMediaPlayerList.init(new ListCallback());
                    }
                }
            };

    // Only used to see if the metadata has changed from its previous value
    private MediaData mCurrentData;

    // Cover Art Service (Storage + BIP Server)
    private AvrcpCoverArtService mAvrcpCoverArtService = null;

    private static AvrcpTargetService sInstance = null;

    public AvrcpTargetService(Context ctx) {
        super(ctx);
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileAvrcpTargetEnabled().orElse(false);
    }

    class ListCallback implements MediaPlayerList.MediaUpdateCallback {
        @Override
        public void run(MediaData data) {
            if (mNativeInterface == null) return;

            boolean metadata = !Objects.equals(mCurrentData.metadata, data.metadata);
            boolean state = !MediaPlayerWrapper.playstateEquals(mCurrentData.state, data.state);
            boolean queue = !Objects.equals(mCurrentData.queue, data.queue);

            if (DEBUG) {
                Log.d(TAG, "onMediaUpdated: track_changed=" + metadata
                        + " state=" + state + " queue=" + queue);
            }
            mCurrentData = data;

            mNativeInterface.sendMediaUpdate(metadata, state, queue);
        }

        @Override
        public void run(boolean availablePlayers, boolean addressedPlayers,
                boolean uids) {
            if (mNativeInterface == null) return;

            mNativeInterface.sendFolderUpdate(availablePlayers, addressedPlayers, uids);
        }
    }

    private class AvrcpBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(AudioManager.ACTION_VOLUME_CHANGED)) {
                int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                if (streamType == AudioManager.STREAM_MUSIC) {
                    int volume = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, 0);
                    BluetoothDevice activeDevice = getA2dpActiveDevice();
                    if (activeDevice != null
                            && !mVolumeManager.getAbsoluteVolumeSupported(activeDevice)) {
                        Log.d(TAG, "stream volume change to " + volume + " " + activeDevice);
                        mVolumeManager.storeVolumeForDevice(activeDevice, volume);
                    }
                }
            }
        }
    }

    /**
     * Set the AvrcpTargetService instance.
     */
    @VisibleForTesting
    public static void set(AvrcpTargetService instance) {
        sInstance = instance;
    }

    /**
     * Get the AvrcpTargetService instance. Returns null if the service hasn't been initialized.
     */
    public static AvrcpTargetService get() {
        return sInstance;
    }

    public AvrcpCoverArtService getCoverArtService() {
        return mAvrcpCoverArtService;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    protected IProfileServiceBinder initBinder() {
        return new AvrcpTargetBinder(this);
    }

    @Override
    public void start() {
        if (sInstance != null) {
            throw new IllegalStateException("start() called twice");
        }

        IntentFilter userFilter = new IntentFilter();
        userFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        userFilter.addAction(Intent.ACTION_USER_UNLOCKED);
        getApplicationContext().registerReceiver(mUserUnlockedReceiver, userFilter);

        Log.i(TAG, "Starting the AVRCP Target Service");
        mCurrentData = new MediaData(null, null, null);

        mAudioManager = getSystemService(AudioManager.class);
        sDeviceMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        mMediaPlayerList = new MediaPlayerList(Looper.myLooper(), this);

        mPlayerSettingsManager = new PlayerSettingsManager(mMediaPlayerList, this);

        mNativeInterface = AvrcpNativeInterface.getInstance();
        mNativeInterface.init(AvrcpTargetService.this);

        mAvrcpVersion = AvrcpVersion.getCurrentSystemPropertiesValue();

        mVolumeManager = new AvrcpVolumeManager(this, mAudioManager, mNativeInterface);

        UserManager userManager = getApplicationContext().getSystemService(UserManager.class);
        if (userManager.isUserUnlocked()) {
            mMediaPlayerList.init(new ListCallback());
        }

        if (getResources().getBoolean(R.bool.avrcp_target_enable_cover_art)) {
            if (mAvrcpVersion.isAtleastVersion(AvrcpVersion.AVRCP_VERSION_1_6)) {
                mAvrcpCoverArtService = new AvrcpCoverArtService(this);
                boolean started = mAvrcpCoverArtService.start();
                if (!started) {
                    Log.e(TAG, "Failed to start cover art service");
                    mAvrcpCoverArtService = null;
                }
            } else {
                Log.e(TAG, "Please use AVRCP version 1.6 to enable cover art");
            }
        }

        mReceiver = new AvrcpBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction(AudioManager.ACTION_VOLUME_CHANGED);
        registerReceiver(mReceiver, filter);

        // Only allow the service to be used once it is initialized
        sInstance = this;
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stopping the AVRCP Target Service");

        if (sInstance == null) {
            Log.w(TAG, "stop() called before start()");
            return;
        }

        if (mAvrcpCoverArtService != null) {
            mAvrcpCoverArtService.stop();
        }
        mAvrcpCoverArtService = null;

        sInstance = null;
        unregisterReceiver(mReceiver);

        // We check the interfaces first since they only get set on User Unlocked
        if (mPlayerSettingsManager != null) mPlayerSettingsManager.cleanup();
        if (mMediaPlayerList != null) mMediaPlayerList.cleanup();
        if (mNativeInterface != null) mNativeInterface.cleanup();
        getApplicationContext().unregisterReceiver(mUserUnlockedReceiver);

        mPlayerSettingsManager = null;
        mMediaPlayerList = null;
        mNativeInterface = null;
        mAudioManager = null;
        mReceiver = null;
    }

    private void init() {
    }

    private BluetoothDevice getA2dpActiveDevice() {
        A2dpService service = mFactory.getA2dpService();
        if (service == null) {
            return null;
        }
        return service.getActiveDevice();
    }

    private void setA2dpActiveDevice(@NonNull BluetoothDevice device) {
        A2dpService service = A2dpService.getA2dpService();
        if (service == null) {
            Log.d(TAG, "setA2dpActiveDevice: A2dp service not found");
            return;
        }
        service.setActiveDevice(device);
    }

    void deviceConnected(BluetoothDevice device, boolean absoluteVolume) {
        Log.i(TAG, "deviceConnected: device=" + device + " absoluteVolume=" + absoluteVolume);
        mVolumeManager.deviceConnected(device, absoluteVolume);
        MetricsLogger.logProfileConnectionEvent(BluetoothMetricsProto.ProfileId.AVRCP);
    }

    void deviceDisconnected(BluetoothDevice device) {
        Log.i(TAG, "deviceDisconnected: device=" + device);
        mVolumeManager.deviceDisconnected(device);
    }

    /**
     * Remove the stored volume for a device.
     */
    public void removeStoredVolumeForDevice(BluetoothDevice device) {
        if (device == null) return;

        mVolumeManager.removeStoredVolumeForDevice(device);
    }

    /**
     * Retrieve the remembered volume for a device. Returns -1 if there is no volume for the
     * device.
     */
    public int getRememberedVolumeForDevice(BluetoothDevice device) {
        if (device == null) return -1;

        return mVolumeManager.getVolume(device, mVolumeManager.getNewDeviceVolume());
    }

    /**
     * Handle when A2DP connection state changes.
     *
     * <p>If the A2DP connection disconnects, we request AVRCP to disconnect device as well.
     */
    public void handleA2dpConnectionStateChanged(BluetoothDevice device, int newState) {
        if (device == null || mNativeInterface == null) return;
        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // If there is no connection, disconnectDevice() will do nothing
            if (mNativeInterface.disconnectDevice(device.getAddress())) {
                Log.d(TAG, "request to disconnect device " + device);
            }
        }
    }
    /**
     * Handle when Active Device changes in A2DP.
     *
     * <p>Signal to the service that the current audio out device has changed and to inform the
     * audio service whether the new device supports absolute volume. If it does, also set the
     * absolute volume level on the remote device.
     */
    public void handleA2dpActiveDeviceChanged(BluetoothDevice device) {
        mVolumeManager.volumeDeviceSwitched(device);
        if (mNativeInterface != null) {
            // Update all the playback status info for each connected device
            mNativeInterface.sendMediaUpdate(false, true, false);
        }
    }

    // TODO (apanicke): Add checks to rejectlist Absolute Volume devices if they behave poorly.
    void setVolume(int avrcpVolume) {
        BluetoothDevice activeDevice = getA2dpActiveDevice();
        if (activeDevice == null) {
            Log.d(TAG, "setVolume: no active device");
            return;
        }

        mVolumeManager.setVolume(activeDevice, avrcpVolume);
    }

    /**
     * Set the volume on the remote device. Does nothing if the device doesn't support absolute
     * volume.
     */
    public void sendVolumeChanged(int deviceVolume) {
        BluetoothDevice activeDevice = getA2dpActiveDevice();
        if (activeDevice == null) {
            Log.d(TAG, "sendVolumeChanged: no active device");
            return;
        }

        mVolumeManager.sendVolumeChanged(activeDevice, deviceVolume);
    }

    Metadata getCurrentSongInfo() {
        Metadata metadata = mMediaPlayerList.getCurrentSongInfo();
        if (mAvrcpCoverArtService != null && metadata.image != null) {
            String imageHandle = mAvrcpCoverArtService.storeImage(metadata.image);
            if (imageHandle != null) metadata.image.setImageHandle(imageHandle);
        }
        return metadata;
    }

    PlayStatus getPlayState() {
        return PlayStatus.fromPlaybackState(mMediaPlayerList.getCurrentPlayStatus(),
                Long.parseLong(getCurrentSongInfo().duration));
    }

    String getCurrentMediaId() {
        String id = mMediaPlayerList.getCurrentMediaId();
        if (id != null && !id.isEmpty()) return id;

        Metadata song = getCurrentSongInfo();
        if (song != null && !song.mediaId.isEmpty()) return song.mediaId;

        // We always want to return something, the error string just makes debugging easier
        return "error";
    }

    List<Metadata> getNowPlayingList() {
        String currentMediaId = getCurrentMediaId();
        Metadata currentTrack = null;
        String imageHandle = null;
        List<Metadata> nowPlayingList = mMediaPlayerList.getNowPlayingList();
        if (mAvrcpCoverArtService != null) {
            for (Metadata metadata : nowPlayingList) {
                if (TextUtils.equals(metadata.mediaId, currentMediaId)) {
                    currentTrack = metadata;
                } else if (metadata.image != null) {
                    imageHandle = mAvrcpCoverArtService.storeImage(metadata.image);
                    if (imageHandle != null) {
                        metadata.image.setImageHandle(imageHandle);
                    }
                }
            }

            // Always store the current item from the queue last so we know the image is in storage
            if (currentTrack != null) {
                imageHandle = mAvrcpCoverArtService.storeImage(currentTrack.image);
                if (imageHandle != null) {
                    currentTrack.image.setImageHandle(imageHandle);
                }
            }
        }
        return nowPlayingList;
    }

    int getCurrentPlayerId() {
        return mMediaPlayerList.getCurrentPlayerId();
    }

    // TODO (apanicke): Have the Player List also contain info about the play state of each player
    List<PlayerInfo> getMediaPlayerList() {
        return mMediaPlayerList.getMediaPlayerList();
    }

    void getPlayerRoot(int playerId, MediaPlayerList.GetPlayerRootCallback cb) {
        mMediaPlayerList.getPlayerRoot(playerId, cb);
    }

    void getFolderItems(int playerId, String mediaId, MediaPlayerList.GetFolderItemsCallback cb) {
        mMediaPlayerList.getFolderItems(playerId, mediaId, cb);
    }

    void playItem(int playerId, boolean nowPlaying, String mediaId) {
        // NOTE: playerId isn't used if nowPlaying is true, since its assumed to be the current
        // active player
        mMediaPlayerList.playItem(playerId, nowPlaying, mediaId);
    }

    void sendMediaKeyEvent(int key, boolean pushed) {
        BluetoothDevice activeDevice = getA2dpActiveDevice();
        MediaPlayerWrapper player = mMediaPlayerList.getActivePlayer();
        mMediaKeyEventLogger.logd(
                TAG,
                "sendMediaKeyEvent:"
                        + " device="
                        + activeDevice
                        + " key="
                        + key
                        + " pushed="
                        + pushed
                        + " to "
                        + (player == null ? null : player.getPackageName()));
        int action = pushed ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
        KeyEvent event = new KeyEvent(action, AvrcpPassthrough.toKeyCode(key));
        mAudioManager.dispatchMediaKeyEvent(event);
    }

    void setActiveDevice(BluetoothDevice device) {
        Log.i(TAG, "setActiveDevice: device=" + device);
        if (device == null) {
            Log.wtf(TAG, "setActiveDevice: could not find device " + device);
            return;
        }
        setA2dpActiveDevice(device);
    }

    /**
     * Called from native to update current active player shuffle mode.
     */
    boolean setShuffleMode(int shuffleMode) {
        return mPlayerSettingsManager.setPlayerShuffleMode(shuffleMode);
    }

    /**
     * Called from native to update current active player repeat mode.
     */
    boolean setRepeatMode(int repeatMode) {
        return mPlayerSettingsManager.setPlayerRepeatMode(repeatMode);
    }

    /**
     * Called from native to get the current active player repeat mode.
     */
    int getRepeatMode() {
        return mPlayerSettingsManager.getPlayerRepeatMode();
    }

    /**
     * Called from native to get the current active player shuffle mode.
     */
    int getShuffleMode() {
        return mPlayerSettingsManager.getPlayerShuffleMode();
    }

    /**
     * Called from player callback to indicate new settings to remote device.
     */
    public void sendPlayerSettings(int repeatMode, int shuffleMode) {
        if (mNativeInterface == null) {
            Log.i(TAG, "Tried to send Player Settings while native interface is null");
            return;
        }

        mNativeInterface.sendPlayerSettings(repeatMode, shuffleMode);
    }

    /**
     * Dump debugging information to the string builder
     */
    public void dump(StringBuilder sb) {
        sb.append("\nProfile: AvrcpTargetService:\n");
        if (sInstance == null) {
            sb.append("AvrcpTargetService not running");
            return;
        }

        StringBuilder tempBuilder = new StringBuilder();
        tempBuilder.append("AVRCP version: " + mAvrcpVersion + "\n");

        if (mMediaPlayerList != null) {
            mMediaPlayerList.dump(tempBuilder);
        } else {
            tempBuilder.append("\nMedia Player List is empty\n");
        }

        mMediaKeyEventLogger.dump(tempBuilder);
        tempBuilder.append("\n");
        mVolumeManager.dump(tempBuilder);
        if (mAvrcpCoverArtService != null) {
            tempBuilder.append("\n");
            mAvrcpCoverArtService.dump(tempBuilder);
        }

        // Tab everything over by two spaces
        sb.append(tempBuilder.toString().replaceAll("(?m)^", "  "));
    }

    private static class AvrcpTargetBinder extends IBluetoothAvrcpTarget.Stub
            implements IProfileServiceBinder {
        private AvrcpTargetService mService;

        AvrcpTargetBinder(AvrcpTargetService service) {
            mService = service;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @Override
        public void sendVolumeChanged(int volume) {
            if (mService == null
                    || !Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)) {
                return;
            }

            mService.sendVolumeChanged(volume);
        }
    }
}
