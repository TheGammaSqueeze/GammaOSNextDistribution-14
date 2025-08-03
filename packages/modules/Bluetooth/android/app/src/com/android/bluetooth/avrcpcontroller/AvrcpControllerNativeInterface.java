/*
 * Copyright 2023 The Android Open Source Project
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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.UUID;

/** Provides Bluetooth AVRCP Controller native interface for the AVRCP Controller service */
public class AvrcpControllerNativeInterface {
    static final String TAG = AvrcpControllerNativeInterface.class.getSimpleName();
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    private AvrcpControllerService mAvrcpController;

    @GuardedBy("INSTANCE_LOCK")
    private static AvrcpControllerNativeInterface sInstance;

    private static final Object INSTANCE_LOCK = new Object();

    static AvrcpControllerNativeInterface getInstance() {
        synchronized (INSTANCE_LOCK) {
            if (sInstance == null) {
                sInstance = new AvrcpControllerNativeInterface();
            }
            return sInstance;
        }
    }

    /** Set singleton instance. */
    @VisibleForTesting
    public static void setInstance(AvrcpControllerNativeInterface instance) {
        synchronized (INSTANCE_LOCK) {
            sInstance = instance;
        }
    }

    void init(AvrcpControllerService controller) {
        mAvrcpController = controller;
        initNative();
    }

    void cleanup() {
        cleanupNative();
    }

    boolean sendPassThroughCommand(byte[] address, int keyCode, int keyState) {
        return sendPassThroughCommandNative(address, keyCode, keyState);
    }

    void setPlayerApplicationSettingValues(
            byte[] address, byte numAttrib, byte[] attribIds, byte[] attribVal) {
        setPlayerApplicationSettingValuesNative(address, numAttrib, attribIds, attribVal);
    }

    void sendAbsVolRsp(byte[] address, int absVol, int label) {
        sendAbsVolRspNative(address, absVol, label);
    }

    void sendRegisterAbsVolRsp(byte[] address, byte rspType, int absVol, int label) {
        sendRegisterAbsVolRspNative(address, rspType, absVol, label);
    }

    void getCurrentMetadata(byte[] address) {
        getCurrentMetadataNative(address);
    }

    void getPlaybackState(byte[] address) {
        getPlaybackStateNative(address);
    }

    void getNowPlayingList(byte[] address, int start, int end) {
        getNowPlayingListNative(address, start, end);
    }

    void getFolderList(byte[] address, int start, int end) {
        getFolderListNative(address, start, end);
    }

    void getPlayerList(byte[] address, int start, int end) {
        getPlayerListNative(address, start, end);
    }

    void changeFolderPath(byte[] address, byte direction, long uid) {
        changeFolderPathNative(address, direction, uid);
    }

    void playItem(byte[] address, byte scope, long uid, int uidCounter) {
        playItemNative(address, scope, uid, uidCounter);
    }

    void setBrowsedPlayer(byte[] address, int playerId) {
        setBrowsedPlayerNative(address, playerId);
    }

    /**********************************************************************************************/
    /*********************************** callbacks from native ************************************/
    /**********************************************************************************************/

    // Called by JNI when a device has connected or disconnected.
    void onConnectionStateChanged(
            boolean remoteControlConnected, boolean browsingConnected, byte[] address) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(
                    TAG,
                    "onConnectionStateChanged: "
                            + (" remoteControlConnected=" + remoteControlConnected)
                            + (" browsingConnected=" + browsingConnected)
                            + (" device=" + device));
        }

        mAvrcpController.onConnectionStateChanged(
                remoteControlConnected, browsingConnected, device);
    }

    // Called by JNI to notify Avrcp of a remote device's Cover Art PSM
    @VisibleForTesting
    void getRcPsm(byte[] address, int psm) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "getRcPsm: device=" + device + " psm=" + psm);
        }

        mAvrcpController.getRcPsm(device, psm);
    }

    // Called by JNI to report remote Player's capabilities
    void handlePlayerAppSetting(byte[] address, byte[] playerAttribRsp, int rspLen) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "handlePlayerAppSetting: device=" + device + " rspLen=" + rspLen);
        }

        mAvrcpController.handlePlayerAppSetting(device, playerAttribRsp, rspLen);
    }

    @VisibleForTesting
    void onPlayerAppSettingChanged(byte[] address, byte[] playerAttribRsp, int rspLen) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "onPlayerAppSettingChanged: device=" + device);
        }

        mAvrcpController.onPlayerAppSettingChanged(device, playerAttribRsp, rspLen);
    }

    // Called by JNI when remote wants to set absolute volume.
    void handleSetAbsVolume(byte[] address, byte absVol, byte label) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "handleSetAbsVolume: device=" + device);
        }

        mAvrcpController.handleSetAbsVolume(device, absVol, label);
    }

    // Called by JNI when remote wants to receive absolute volume notifications.
    void handleRegisterNotificationAbsVol(byte[] address, byte label) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "handleRegisterNotificationAbsVol: device=" + device);
        }

        mAvrcpController.handleRegisterNotificationAbsVol(device, label);
    }

    // Called by JNI when a track changes and local AvrcpController is registered for updates.
    void onTrackChanged(byte[] address, byte numAttributes, int[] attributes, String[] attribVals) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "onTrackChanged: device=" + device);
        }

        mAvrcpController.onTrackChanged(device, numAttributes, attributes, attribVals);
    }

    // Called by JNI periodically based upon timer to update play position
    void onPlayPositionChanged(byte[] address, int songLen, int currSongPosition) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "onPlayPositionChanged: device=" + device + " pos=" + currSongPosition);
        }

        mAvrcpController.onPlayPositionChanged(device, songLen, currSongPosition);
    }

    // Called by JNI on changes of play status
    void onPlayStatusChanged(byte[] address, byte playStatus) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "onPlayStatusChanged: device=" + device + " playStatus=" + playStatus);
        }

        mAvrcpController.onPlayStatusChanged(device, toPlaybackStateFromJni(playStatus));
    }

    // Browsing related JNI callbacks.
    void handleGetFolderItemsRsp(byte[] address, int status, AvrcpItem[] items) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(
                    TAG,
                    "handleGetFolderItemsRsp:"
                            + (" device=" + device)
                            + (" status=" + status)
                            + (" NumberOfItems=" + items.length));
        }

        mAvrcpController.handleGetFolderItemsRsp(device, status, items);
    }

    void handleGetPlayerItemsRsp(byte[] address, AvrcpPlayer[] items) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(
                    TAG,
                    "handleGetFolderItemsRsp:"
                            + (" device=" + device)
                            + (" NumberOfItems=" + items.length));
        }

        mAvrcpController.handleGetPlayerItemsRsp(device, Arrays.asList(items));
    }

    // JNI Helper functions to convert native objects to java.
    static AvrcpItem createFromNativeMediaItem(
            byte[] address, long uid, int type, String name, int[] attrIds, String[] attrVals) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (VDBG) {
            Log.d(
                    TAG,
                    "createFromNativeMediaItem:"
                            + (" device=" + device)
                            + (" uid=" + uid)
                            + (" type=" + type)
                            + (" name=" + name)
                            + (" attrids=" + Arrays.toString(attrIds))
                            + (" attrVals=" + Arrays.toString(attrVals)));
        }

        return new AvrcpItem.Builder()
                .fromAvrcpAttributeArray(attrIds, attrVals)
                .setDevice(device)
                .setItemType(AvrcpItem.TYPE_MEDIA)
                .setType(type)
                .setUid(uid)
                .setUuid(UUID.randomUUID().toString())
                .setPlayable(true)
                .build();
    }

    static AvrcpItem createFromNativeFolderItem(
            byte[] address, long uid, int type, String name, int playable) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (VDBG) {
            Log.d(
                    TAG,
                    "createFromNativeFolderItem:"
                            + (" device=" + device)
                            + (" uid=" + uid)
                            + (" type=" + type)
                            + (" name=" + name)
                            + (" playable=" + playable));
        }

        return new AvrcpItem.Builder()
                .setDevice(device)
                .setItemType(AvrcpItem.TYPE_FOLDER)
                .setType(type)
                .setUid(uid)
                .setUuid(UUID.randomUUID().toString())
                .setDisplayableName(name)
                .setPlayable(playable == 0x01)
                .setBrowsable(true)
                .build();
    }

    static AvrcpPlayer createFromNativePlayerItem(
            byte[] address,
            int id,
            String name,
            byte[] transportFlags,
            int playStatus,
            int playerType) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (VDBG) {
            Log.d(
                    TAG,
                    "createFromNativePlayerItem:"
                            + (" device=" + device)
                            + (" name=" + name)
                            + (" transportFlags=" + Arrays.toString(transportFlags))
                            + (" playStatus=" + playStatus)
                            + (" playerType=" + playerType));
        }

        return new AvrcpPlayer.Builder()
                .setDevice(device)
                .setPlayerId(id)
                .setPlayerType(playerType)
                .setSupportedFeatures(transportFlags)
                .setName(name)
                .setPlayStatus(toPlaybackStateFromJni(playStatus))
                .build();
    }

    void handleChangeFolderRsp(byte[] address, int count) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "handleChangeFolderRsp: device=" + device + " count=" + count);
        }

        mAvrcpController.handleChangeFolderRsp(device, count);
    }

    void handleSetBrowsedPlayerRsp(byte[] address, int items, int depth) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "handleSetBrowsedPlayerRsp: device=" + device + " depth=" + depth);
        }

        mAvrcpController.handleSetBrowsedPlayerRsp(device, items, depth);
    }

    void handleSetAddressedPlayerRsp(byte[] address, int status) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "handleSetAddressedPlayerRsp device=" + device + " status=" + status);
        }

        mAvrcpController.handleSetAddressedPlayerRsp(device, status);
    }

    void handleAddressedPlayerChanged(byte[] address, int id) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "handleAddressedPlayerChanged: device=" + device + " id=" + id);
        }

        mAvrcpController.handleAddressedPlayerChanged(device, id);
    }

    void handleNowPlayingContentChanged(byte[] address) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "handleNowPlayingContentChanged: device=" + device);
        }
        mAvrcpController.handleNowPlayingContentChanged(device);
    }

    void onAvailablePlayerChanged(byte[] address) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        if (DBG) {
            Log.d(TAG, "onAvailablePlayerChanged: device=" + device);
        }
        mAvrcpController.onAvailablePlayerChanged(device);
    }

    /*
     *  Play State Values from JNI
     */
    private static final byte JNI_PLAY_STATUS_STOPPED = 0x00;
    private static final byte JNI_PLAY_STATUS_PLAYING = 0x01;
    private static final byte JNI_PLAY_STATUS_PAUSED = 0x02;
    private static final byte JNI_PLAY_STATUS_FWD_SEEK = 0x03;
    private static final byte JNI_PLAY_STATUS_REV_SEEK = 0x04;

    private static int toPlaybackStateFromJni(int fromJni) {
        switch (fromJni) {
            case JNI_PLAY_STATUS_STOPPED:
                return PlaybackStateCompat.STATE_STOPPED;
            case JNI_PLAY_STATUS_PLAYING:
                return PlaybackStateCompat.STATE_PLAYING;
            case JNI_PLAY_STATUS_PAUSED:
                return PlaybackStateCompat.STATE_PAUSED;
            case JNI_PLAY_STATUS_FWD_SEEK:
                return PlaybackStateCompat.STATE_FAST_FORWARDING;
            case JNI_PLAY_STATUS_REV_SEEK:
                return PlaybackStateCompat.STATE_REWINDING;
            default:
                return PlaybackStateCompat.STATE_NONE;
        }
    }

    /**********************************************************************************************/
    /******************************************* native *******************************************/
    /**********************************************************************************************/

    private native void initNative();

    private native void cleanupNative();

    /**
     * Send button press commands to addressed device
     *
     * @param keyCode key code as defined in AVRCP specification
     * @param keyState 0 = key pressed, 1 = key released
     * @return command was sent
     */
    private native boolean sendPassThroughCommandNative(byte[] address, int keyCode, int keyState);

    /**
     * TODO DELETE: This method is not used Send group navigation commands
     *
     * @param keyCode next/previous
     * @param keyState state
     * @return command was sent
     */
    private native boolean sendGroupNavigationCommandNative(
            byte[] address, int keyCode, int keyState);

    /**
     * Change player specific settings such as shuffle
     *
     * @param numAttrib number of settings being sent
     * @param attribIds list of settings to be changed
     * @param attribVal list of settings values
     */
    private native void setPlayerApplicationSettingValuesNative(
            byte[] address, byte numAttrib, byte[] attribIds, byte[] attribVal);

    /**
     * Send response to set absolute volume
     *
     * @param absVol new volume
     * @param label label
     */
    private native void sendAbsVolRspNative(byte[] address, int absVol, int label);

    /**
     * Register for any volume level changes
     *
     * @param rspType type of response
     * @param absVol current volume
     * @param label label
     */
    private native void sendRegisterAbsVolRspNative(
            byte[] address, byte rspType, int absVol, int label);

    /**
     * Fetch the current track's metadata
     *
     * <p>This method is specifically meant to allow us to fetch image handles that may not have
     * been sent to us yet, prior to having a BIP client connection. See the AVRCP 1.6+
     * specification, section 4.1.7, for more details.
     */
    private native void getCurrentMetadataNative(byte[] address);

    /** Fetch the playback state */
    private native void getPlaybackStateNative(byte[] address);

    /**
     * Fetch the current now playing list
     *
     * @param start first index to retrieve
     * @param end last index to retrieve
     */
    private native void getNowPlayingListNative(byte[] address, int start, int end);

    /**
     * Fetch the current folder's listing
     *
     * @param start first index to retrieve
     * @param end last index to retrieve
     */
    private native void getFolderListNative(byte[] address, int start, int end);

    /**
     * Fetch the listing of players
     *
     * @param start first index to retrieve
     * @param end last index to retrieve
     */
    private native void getPlayerListNative(byte[] address, int start, int end);

    /**
     * Change the current browsed folder
     *
     * @param direction up/down
     * @param uid folder unique id
     */
    private native void changeFolderPathNative(byte[] address, byte direction, long uid);

    /**
     * Play item with provided uid
     *
     * @param scope scope of item to played
     * @param uid song unique id
     * @param uidCounter counter
     */
    private native void playItemNative(byte[] address, byte scope, long uid, int uidCounter);

    /**
     * Set a specific player for browsing
     *
     * @param playerId player number
     */
    private native void setBrowsedPlayerNative(byte[] address, int playerId);

    /**
     * TODO DELETE: This method is not used Set a specific player for handling playback commands
     *
     * @param playerId player number
     */
    private native void setAddressedPlayerNative(byte[] address, int playerId);
}
