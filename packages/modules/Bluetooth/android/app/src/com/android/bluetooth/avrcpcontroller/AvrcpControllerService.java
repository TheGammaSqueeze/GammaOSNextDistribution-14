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

import static java.util.Objects.requireNonNull;

import android.annotation.RequiresPermission;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAvrcpPlayerSettings;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothAvrcpController;
import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import com.android.bluetooth.BluetoothPrefs;
import com.android.bluetooth.R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dpsink.A2dpSinkService;
import com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService.BrowseResult;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.SynchronousResultReceiver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides Bluetooth AVRCP Controller profile, as a service in the Bluetooth application.
 */
public class AvrcpControllerService extends ProfileService {
    static final String TAG = AvrcpControllerService.class.getSimpleName();
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    static final int MAXIMUM_CONNECTED_DEVICES = 5;

    /**
     * Owned Components
     */
    private static final String ON_ERROR_SETTINGS_ACTIVITY =
            BluetoothPrefs.class.getCanonicalName();
    private static final String COVER_ART_PROVIDER = AvrcpCoverArtProvider.class.getCanonicalName();

    /* Folder/Media Item scopes.
     * Keep in sync with AVRCP 1.6 sec. 6.10.1
     */
    public static final byte BROWSE_SCOPE_PLAYER_LIST = 0x00;
    public static final byte BROWSE_SCOPE_VFS = 0x01;
    public static final byte BROWSE_SCOPE_SEARCH = 0x02;
    public static final byte BROWSE_SCOPE_NOW_PLAYING = 0x03;

    /* Folder navigation directions
     * This is borrowed from AVRCP 1.6 spec and must be kept with same values
     */
    public static final byte FOLDER_NAVIGATION_DIRECTION_UP = 0x00;
    public static final byte FOLDER_NAVIGATION_DIRECTION_DOWN = 0x01;

    /*
     * KeyCoded for Pass Through Commands
     */
    public static final int PASS_THRU_CMD_ID_PLAY = 0x44;
    public static final int PASS_THRU_CMD_ID_PAUSE = 0x46;
    public static final int PASS_THRU_CMD_ID_VOL_UP = 0x41;
    public static final int PASS_THRU_CMD_ID_VOL_DOWN = 0x42;
    public static final int PASS_THRU_CMD_ID_STOP = 0x45;
    public static final int PASS_THRU_CMD_ID_FF = 0x49;
    public static final int PASS_THRU_CMD_ID_REWIND = 0x48;
    public static final int PASS_THRU_CMD_ID_FORWARD = 0x4B;
    public static final int PASS_THRU_CMD_ID_BACKWARD = 0x4C;

    /* Key State Variables */
    public static final int KEY_STATE_PRESSED = 0;
    public static final int KEY_STATE_RELEASED = 1;

    /* Active Device State Variables */
    public static final int DEVICE_STATE_INACTIVE = 0;
    public static final int DEVICE_STATE_ACTIVE = 1;

    static BrowseTree sBrowseTree;
    private static AvrcpControllerService sService;

    private AdapterService mAdapterService;
    private final AvrcpControllerNativeInterface mNativeInterface;

    protected Map<BluetoothDevice, AvrcpControllerStateMachine> mDeviceStateMap =
            new ConcurrentHashMap<>(1);
    private BluetoothDevice mActiveDevice = null;
    private final Object mActiveDeviceLock = new Object();

    private boolean mCoverArtEnabled = false;
    protected AvrcpCoverArtManager mCoverArtManager;

    private class ImageDownloadCallback implements AvrcpCoverArtManager.Callback {
        @Override
        public void onImageDownloadComplete(BluetoothDevice device,
                AvrcpCoverArtManager.DownloadEvent event) {
            if (DBG) {
                Log.d(TAG, "Image downloaded [device: " + device + ", uuid: " + event.getUuid()
                        + ", uri: " + event.getUri());
            }
            AvrcpControllerStateMachine stateMachine = getStateMachine(device);
            if (stateMachine == null) {
                Log.e(TAG, "No state machine found for device " + device);
                mCoverArtManager.removeImage(device, event.getUuid());
                return;
            }
            stateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_IMAGE_DOWNLOADED,
                    event);
        }
    }

    public AvrcpControllerService(Context ctx) {
        super(ctx);
        mNativeInterface = requireNonNull(AvrcpControllerNativeInterface.getInstance());
    }

    @VisibleForTesting
    public AvrcpControllerService(Context ctx, AvrcpControllerNativeInterface nativeInterface) {
        super(ctx);
        mNativeInterface = requireNonNull(nativeInterface);
    }

    public static boolean isEnabled() {
        return BluetoothProperties.isProfileAvrcpControllerEnabled().orElse(false);
    }

    @Override
    public synchronized void start() {
        mNativeInterface.init(this);
        setComponentAvailable(ON_ERROR_SETTINGS_ACTIVITY, true);
        mAdapterService = AdapterService.getAdapterService();
        mCoverArtEnabled = getResources().getBoolean(R.bool.avrcp_controller_enable_cover_art);
        if (mCoverArtEnabled) {
            setComponentAvailable(COVER_ART_PROVIDER, true);
            mCoverArtManager = new AvrcpCoverArtManager(this, new ImageDownloadCallback());
        }
        sBrowseTree = new BrowseTree(null);
        sService = this;

        // Start the media browser service.
        Intent startIntent = new Intent(this, BluetoothMediaBrowserService.class);
        startService(startIntent);
        setActiveDevice(null);
    }

    @Override
    public synchronized void stop() {
        setActiveDevice(null);
        Intent stopIntent = new Intent(this, BluetoothMediaBrowserService.class);
        stopService(stopIntent);
        for (AvrcpControllerStateMachine stateMachine : mDeviceStateMap.values()) {
            stateMachine.quitNow();
        }
        mDeviceStateMap.clear();

        sService = null;
        sBrowseTree = null;
        if (mCoverArtManager != null) {
            mCoverArtManager.cleanup();
            mCoverArtManager = null;
            setComponentAvailable(COVER_ART_PROVIDER, false);
        }
        setComponentAvailable(ON_ERROR_SETTINGS_ACTIVITY, false);
        mNativeInterface.cleanup();
    }

    public static AvrcpControllerService getAvrcpControllerService() {
        return sService;
    }

    /**
     * Get the current active device
     */
    public BluetoothDevice getActiveDevice() {
        synchronized (mActiveDeviceLock) {
            return mActiveDevice;
        }
    }

    /**
     * Set the current active device, notify devices of activity status
     */
    @VisibleForTesting
    boolean setActiveDevice(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "setActiveDevice(device=" + device + ")");
        }
        A2dpSinkService a2dpSinkService = A2dpSinkService.getA2dpSinkService();
        if (a2dpSinkService == null) {
            Log.w(TAG, "setActiveDevice(device=" + device + "): A2DP Sink not available");
            return false;
        }

        BluetoothDevice currentActiveDevice = getActiveDevice();
        if ((device == null && currentActiveDevice == null)
                || (device != null && device.equals(currentActiveDevice))) {
            return true;
        }

        // Try and update the active device
        synchronized (mActiveDeviceLock) {
            if (a2dpSinkService.setActiveDevice(device)) {
                mActiveDevice = device;

                // Pause the old active device
                if (currentActiveDevice != null) {
                    AvrcpControllerStateMachine oldStateMachine =
                            getStateMachine(currentActiveDevice);
                    if (oldStateMachine != null) {
                        oldStateMachine.setDeviceState(DEVICE_STATE_INACTIVE);
                    }
                }

                AvrcpControllerStateMachine stateMachine = getStateMachine(device);
                if (stateMachine != null) {
                    stateMachine.setDeviceState(DEVICE_STATE_ACTIVE);
                } else {
                    BluetoothMediaBrowserService.reset();
                }
                return true;
            }
        }

        Log.w(TAG, "setActiveDevice(device=" + device + "): A2DP Sink request failed");
        return false;
    }


    protected void getCurrentMetadataIfNoCoverArt(BluetoothDevice device) {
        if (device == null) return;
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine == null) return;
        AvrcpItem track = stateMachine.getCurrentTrack();
        if (track != null && track.getCoverArtLocation() == null) {
            mNativeInterface.getCurrentMetadata(Utils.getByteAddress(device));
        }
    }

    @VisibleForTesting
    void refreshContents(BrowseTree.BrowseNode node) {
        BluetoothDevice device = node.getDevice();
        if (device == null) {
            return;
        }
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.requestContents(node);
        }
    }

    void playItem(String parentMediaId) {
        if (DBG) Log.d(TAG, "playItem(" + parentMediaId + ")");
        // Check if the requestedNode is a player rather than a song
        BrowseTree.BrowseNode requestedNode = sBrowseTree.findBrowseNodeByID(parentMediaId);
        if (requestedNode == null) {
            for (AvrcpControllerStateMachine stateMachine : mDeviceStateMap.values()) {
                // Check each state machine for the song and then play it
                requestedNode = stateMachine.findNode(parentMediaId);
                if (requestedNode != null) {
                    if (DBG) Log.d(TAG, "Found a node");
                    BluetoothDevice device = stateMachine.getDevice();
                    if (device != null) {
                        setActiveDevice(device);
                    }
                    stateMachine.playItem(requestedNode);
                    break;
                }
            }
        }
    }

    /*Java API*/

    /**
     * Get a List of MediaItems that are children of the specified media Id
     *
     * @param parentMediaId The player or folder to get the contents of
     * @return List of Children if available, an empty list if there are none, or null if a search
     *     must be performed.
     */
    public synchronized BrowseResult getContents(String parentMediaId) {
        if (DBG) Log.d(TAG, "getContents(" + parentMediaId + ")");

        BrowseTree.BrowseNode requestedNode = sBrowseTree.findBrowseNodeByID(parentMediaId);
        if (requestedNode == null) {
            for (AvrcpControllerStateMachine stateMachine : mDeviceStateMap.values()) {
                requestedNode = stateMachine.findNode(parentMediaId);
                if (requestedNode != null) {
                    break;
                }
            }
        }

        if (DBG) {
            Log.d(TAG, "getContents(" + parentMediaId + "): "
                    + (requestedNode == null
                            ? "Failed to find node"
                            : "node=" + requestedNode + ", device=" + requestedNode.getDevice()));
        }

        // If we don't find a node in the tree then do not have any way to browse for the contents.
        // Return an empty list instead.
        if (requestedNode == null) {
            return new BrowseResult(new ArrayList(0), BrowseResult.ERROR_MEDIA_ID_INVALID);
        }
        if (parentMediaId.equals(BrowseTree.ROOT) && requestedNode.getChildrenCount() == 0) {
            return new BrowseResult(null, BrowseResult.NO_DEVICE_CONNECTED);
        }
        // If we found a node and it belongs to a device then go ahead and make it active
        BluetoothDevice device = requestedNode.getDevice();
        if (device != null) {
            setActiveDevice(device);
        }

        List<MediaItem> contents = requestedNode.getContents();

        if (!requestedNode.isCached()) {
            if (DBG) Log.d(TAG, "getContents(" + parentMediaId + "): node download pending");
            refreshContents(requestedNode);
            /* Ongoing downloads can have partial results and we want to make sure they get sent
             * to the client. If a download gets kicked off as a result of this request, the
             * contents will be null until the first results arrive.
             */
            return new BrowseResult(contents, BrowseResult.DOWNLOAD_PENDING);
        }
        if (DBG) {
            Log.d(TAG, "getContents(" + parentMediaId + "): return node, contents="
                    + requestedNode.getContents());
        }
        return new BrowseResult(contents, BrowseResult.SUCCESS);
    }


    @Override
    protected IProfileServiceBinder initBinder() {
        return new AvrcpControllerServiceBinder(this);
    }

    //Binder object: Must be static class or memory leak may occur
    @VisibleForTesting
    static class AvrcpControllerServiceBinder extends IBluetoothAvrcpController.Stub
            implements IProfileServiceBinder {
        private AvrcpControllerService mService;

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        private AvrcpControllerService getService(AttributionSource source) {
            if (Utils.isInstrumentationTestMode()) {
                return mService;
            }
            if (!Utils.checkServiceAvailable(mService, TAG)
                    || !Utils.checkCallerIsSystemOrActiveOrManagedUser(mService, TAG)
                    || !Utils.checkConnectPermissionForDataDelivery(mService, source, TAG)) {
                return null;
            }
            return mService;
        }

        AvrcpControllerServiceBinder(AvrcpControllerService service) {
            mService = service;
        }

        @Override
        public void cleanup() {
            mService = null;
        }

        @Override
        public void getConnectedDevices(AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                AvrcpControllerService service = getService(source);
                List<BluetoothDevice> defaultValue = new ArrayList<BluetoothDevice>(0);
                if (service != null) {
                    defaultValue = service.getConnectedDevices();
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getDevicesMatchingConnectionStates(int[] states,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                AvrcpControllerService service = getService(source);
                List<BluetoothDevice> defaultValue = new ArrayList<BluetoothDevice>(0);
                if (service != null) {
                    defaultValue = service.getDevicesMatchingConnectionStates(states);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getConnectionState(BluetoothDevice device, AttributionSource source,
                SynchronousResultReceiver receiver) {
            try {
                AvrcpControllerService service = getService(source);
                int defaultValue = BluetoothProfile.STATE_DISCONNECTED;
                if (service != null) {
                    defaultValue = service.getConnectionState(device);
                }
                receiver.send(defaultValue);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void sendGroupNavigationCmd(BluetoothDevice device, int keyCode, int keyState,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                AvrcpControllerService service = getService(source);
                Log.w(TAG, "sendGroupNavigationCmd not implemented");
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void setPlayerApplicationSetting(BluetoothAvrcpPlayerSettings settings,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                AvrcpControllerService service = getService(source);
                Log.w(TAG, "setPlayerApplicationSetting not implemented");
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }

        @Override
        public void getPlayerSettings(BluetoothDevice device,
                AttributionSource source, SynchronousResultReceiver receiver) {
            try {
                AvrcpControllerService service = getService(source);
                Log.w(TAG, "getPlayerSettings not implemented");
                receiver.send(null);
            } catch (RuntimeException e) {
                receiver.propagateException(e);
            }
        }
    }

    // Called by JNI when a device has connected or disconnected.
    @VisibleForTesting
    synchronized void onConnectionStateChanged(
            boolean remoteControlConnected, boolean browsingConnected, BluetoothDevice device) {
        StackEvent event =
                StackEvent.connectionStateChanged(remoteControlConnected, browsingConnected);
        AvrcpControllerStateMachine stateMachine = getOrCreateStateMachine(device);
        if (remoteControlConnected || browsingConnected) {
            stateMachine.connect(event);
            // The first device to connect gets to be the active device
            if (getActiveDevice() == null) {
                setActiveDevice(device);
            }
        } else {
            stateMachine.disconnect();
            if (device.equals(getActiveDevice())) {
                setActiveDevice(null);
            }
        }
    }

    // Called by JNI to notify Avrcp of a remote device's Cover Art PSM
    @VisibleForTesting
    void getRcPsm(BluetoothDevice device, int psm) {
        AvrcpControllerStateMachine stateMachine = getOrCreateStateMachine(device);
        stateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_RECEIVED_COVER_ART_PSM, psm);
    }

    // Called by JNI when remote wants to receive absolute volume notifications.
    @VisibleForTesting
    synchronized void handleRegisterNotificationAbsVol(BluetoothDevice device, byte label) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.sendMessage(
                    AvrcpControllerStateMachine.MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION,
                    label);
        }
    }

    // Called by JNI when remote wants to set absolute volume.
    @VisibleForTesting
    synchronized void handleSetAbsVolume(BluetoothDevice device, byte absVol, byte label) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.sendMessage(
                    AvrcpControllerStateMachine.MESSAGE_PROCESS_SET_ABS_VOL_CMD, absVol, label);
        }
    }

    /**
     * Notify AVRCP Controller of an audio focus state change so we can make requests of the active
     * player to stop and start playing.
     */
    public void onAudioFocusStateChanged(int state) {
        if (DBG) {
            Log.d(TAG, "onAudioFocusStateChanged(state=" + state + ")");
        }

        // Make sure the active device isn't changed while we're processing the event so play/pause
        // commands get routed to the correct device
        synchronized (mActiveDeviceLock) {
            switch (state) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    BluetoothMediaBrowserService.setActive(true);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    BluetoothMediaBrowserService.setActive(false);
                    break;
            }
            BluetoothDevice device = getActiveDevice();
            if (device == null) {
                Log.w(TAG, "No active device set, ignore focus change");
                return;
            }

            AvrcpControllerStateMachine stateMachine = mDeviceStateMap.get(device);
            if (stateMachine == null) {
                Log.w(TAG, "No state machine for active device.");
                return;
            }
            stateMachine.sendMessage(AvrcpControllerStateMachine.AUDIO_FOCUS_STATE_CHANGE, state);
        }
    }

    // Called by JNI when a track changes and local AvrcpController is registered for updates.
    @VisibleForTesting
    synchronized void onTrackChanged(
            BluetoothDevice device, byte numAttributes, int[] attributes, String[] attribVals) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            AvrcpItem.Builder aib = new AvrcpItem.Builder();
            aib.fromAvrcpAttributeArray(attributes, attribVals);
            aib.setDevice(device);
            aib.setItemType(AvrcpItem.TYPE_MEDIA);
            aib.setUuid(UUID.randomUUID().toString());
            AvrcpItem item = aib.build();
            if (mCoverArtManager != null) {
                String handle = item.getCoverArtHandle();
                if (handle != null) {
                    item.setCoverArtUuid(mCoverArtManager.getUuidForHandle(device, handle));
                }
            }
            stateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_TRACK_CHANGED,
                    item);
        }
    }

    // Called by JNI periodically based upon timer to update play position
    @VisibleForTesting
    synchronized void onPlayPositionChanged(
            BluetoothDevice device, int songLen, int currSongPosition) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.sendMessage(
                    AvrcpControllerStateMachine.MESSAGE_PROCESS_PLAY_POS_CHANGED,
                    songLen, currSongPosition);
        }
    }

    // Called by JNI on changes of play status
    @VisibleForTesting
    synchronized void onPlayStatusChanged(BluetoothDevice device, int playbackState) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.sendMessage(
                    AvrcpControllerStateMachine.MESSAGE_PROCESS_PLAY_STATUS_CHANGED, playbackState);
        }
    }

    // Called by JNI to report remote Player's capabilities
    @VisibleForTesting
    synchronized void handlePlayerAppSetting(
            BluetoothDevice device, byte[] playerAttribRsp, int rspLen) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            PlayerApplicationSettings supportedSettings =
                    PlayerApplicationSettings.makeSupportedSettings(playerAttribRsp);
            stateMachine.sendMessage(
                    AvrcpControllerStateMachine.MESSAGE_PROCESS_SUPPORTED_APPLICATION_SETTINGS,
                    supportedSettings);
        }
    }

    @VisibleForTesting
    synchronized void onPlayerAppSettingChanged(
            BluetoothDevice device, byte[] playerAttribRsp, int rspLen) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {

            PlayerApplicationSettings currentSettings =
                    PlayerApplicationSettings.makeSettings(playerAttribRsp);
            stateMachine.sendMessage(
                    AvrcpControllerStateMachine.MESSAGE_PROCESS_CURRENT_APPLICATION_SETTINGS,
                    currentSettings);
        }
    }

    @VisibleForTesting
    void onAvailablePlayerChanged(BluetoothDevice device) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_AVAILABLE_PLAYER_CHANGED);
        }
    }

    // Browsing related JNI callbacks.
    void handleGetFolderItemsRsp(BluetoothDevice device, int status, AvrcpItem[] items) {
        List<AvrcpItem> itemsList = new ArrayList<>();
        for (AvrcpItem item : items) {
            if (VDBG) Log.d(TAG, item.toString());
            if (mCoverArtManager != null) {
                String handle = item.getCoverArtHandle();
                if (handle != null) {
                    item.setCoverArtUuid(mCoverArtManager.getUuidForHandle(device, handle));
                }
            }
            itemsList.add(item);
        }

        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                    itemsList);
        }
    }

    void handleGetPlayerItemsRsp(BluetoothDevice device, List<AvrcpPlayer> itemsList) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_PLAYER_ITEMS,
                    itemsList);
        }
    }

    @VisibleForTesting
    void handleChangeFolderRsp(BluetoothDevice device, int count) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_FOLDER_PATH,
                    count);
        }
    }

    @VisibleForTesting
    void handleSetBrowsedPlayerRsp(BluetoothDevice device, int items, int depth) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_SET_BROWSED_PLAYER,
                    items, depth);
        }
    }

    @VisibleForTesting
    void handleSetAddressedPlayerRsp(BluetoothDevice device, int status) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.sendMessage(
                    AvrcpControllerStateMachine.MESSAGE_PROCESS_SET_ADDRESSED_PLAYER);
        }
    }

    @VisibleForTesting
    void handleAddressedPlayerChanged(BluetoothDevice device, int id) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.sendMessage(
                    AvrcpControllerStateMachine.MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED, id);
        }
    }

    @VisibleForTesting
    void handleNowPlayingContentChanged(BluetoothDevice device) {
        AvrcpControllerStateMachine stateMachine = getStateMachine(device);
        if (stateMachine != null) {
            stateMachine.nowPlayingContentChanged();
        }
    }

    /* Generic Profile Code */

    /**
     * Disconnect the given Bluetooth device.
     *
     * @return true if disconnect is successful, false otherwise.
     */
    public synchronized boolean disconnect(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "disconnect(device=" + device + ")");
        }
        AvrcpControllerStateMachine stateMachine = mDeviceStateMap.get(device);
        // a map state machine instance doesn't exist. maybe it is already gone?
        if (stateMachine == null) {
            return false;
        }
        int connectionState = stateMachine.getState();
        if (connectionState != BluetoothProfile.STATE_CONNECTED
                && connectionState != BluetoothProfile.STATE_CONNECTING) {
            return false;
        }
        stateMachine.disconnect();
        return true;
    }

    /**
     * Remove state machine from device map once it is no longer needed.
     */
    public void removeStateMachine(AvrcpControllerStateMachine stateMachine) {
        if (stateMachine == null) {
            return;
        }
        BluetoothDevice device = stateMachine.getDevice();
        if (device.equals(getActiveDevice())) {
            setActiveDevice(null);
        }
        mDeviceStateMap.remove(stateMachine.getDevice());
        stateMachine.quitNow();
    }

    public List<BluetoothDevice> getConnectedDevices() {
        return getDevicesMatchingConnectionStates(new int[]{BluetoothAdapter.STATE_CONNECTED});
    }

    protected AvrcpControllerStateMachine getStateMachine(BluetoothDevice device) {
        if (device == null) {
            return null;
        }
        return mDeviceStateMap.get(device);
    }

    protected AvrcpControllerStateMachine getOrCreateStateMachine(BluetoothDevice device) {
        AvrcpControllerStateMachine newStateMachine =
                new AvrcpControllerStateMachine(device, this, mNativeInterface);
        AvrcpControllerStateMachine existingStateMachine =
                mDeviceStateMap.putIfAbsent(device, newStateMachine);
        // Given null is not a valid value in our map, ConcurrentHashMap will return null if the
        // key was absent and our new value was added. We should then start and return it. Else
        // we quit the new one so we don't leak a thread
        if (existingStateMachine == null) {
            newStateMachine.start();
            return newStateMachine;
        } else {
            // If you try to quit a StateMachine that hasn't been constructed yet, the StateMachine
            // spits out an NPE trying to read a state stack array that only gets made on start().
            // We can just quit the thread made explicitly
            newStateMachine.getHandler().getLooper().quit();
        }
        return existingStateMachine;
    }

    protected AvrcpCoverArtManager getCoverArtManager() {
        return mCoverArtManager;
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        if (DBG) Log.d(TAG, "getDevicesMatchingConnectionStates" + Arrays.toString(states));
        List<BluetoothDevice> deviceList = new ArrayList<>();
        BluetoothDevice[] bondedDevices = mAdapterService.getBondedDevices();
        int connectionState;
        for (BluetoothDevice device : bondedDevices) {
            connectionState = getConnectionState(device);
            if (DBG) Log.d(TAG, "Device: " + device + "State: " + connectionState);
            for (int i = 0; i < states.length; i++) {
                if (connectionState == states[i]) {
                    deviceList.add(device);
                }
            }
        }
        if (DBG) Log.d(TAG, deviceList.toString());
        Log.d(TAG, "GetDevicesDone");
        return deviceList;
    }

    synchronized int getConnectionState(BluetoothDevice device) {
        AvrcpControllerStateMachine stateMachine = mDeviceStateMap.get(device);
        return (stateMachine == null) ? BluetoothProfile.STATE_DISCONNECTED
                : stateMachine.getState();
    }

    @Override
    public void dump(StringBuilder sb) {
        super.dump(sb);
        ProfileService.println(sb, "Devices Tracked = " + mDeviceStateMap.size());
        ProfileService.println(sb, "Active Device = " + mActiveDevice);

        for (AvrcpControllerStateMachine stateMachine : mDeviceStateMap.values()) {
            ProfileService.println(sb,
                    "==== StateMachine for " + stateMachine.getDevice() + " ====");
            stateMachine.dump(sb);
        }
        sb.append("\n  BrowseTree:\n");
        sBrowseTree.dump(sb);

        sb.append("\n  Cover Artwork Enabled: " + (mCoverArtEnabled ? "True" : "False"));
        if (mCoverArtManager != null) {
            sb.append("\n  " + mCoverArtManager.toString());
        }

        sb.append("\n  " + BluetoothMediaBrowserService.dump() + "\n");
    }
}
