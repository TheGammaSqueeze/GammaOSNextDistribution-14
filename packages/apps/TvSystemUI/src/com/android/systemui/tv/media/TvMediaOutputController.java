/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.tv.media;

import static com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_3POINT5_MM_AUDIO_DEVICE;
import static com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_BLUETOOTH_DEVICE;
import static com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_CAST_DEVICE;
import static com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_CAST_GROUP_DEVICE;
import static com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_FAST_PAIR_BLUETOOTH_DEVICE;
import static com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_PHONE_DEVICE;
import static com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_USB_C_AUDIO_DEVICE;

import android.app.KeyguardManager;
import android.content.Context;
import android.media.AudioManager;
import android.media.session.MediaSessionManager;
import android.os.PowerExemptionManager;
import android.text.TextUtils;

import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.media.MediaDevice;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.media.dialog.MediaItem;
import com.android.systemui.media.dialog.MediaOutputController;
import com.android.systemui.media.nearby.NearbyMediaDevicesManager;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection;
import com.android.systemui.tv.res.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Extends {@link MediaOutputController} to create a TV specific ordering and grouping of devices
 * which are shown in the {@link TvMediaOutputDialogActivity}.
 */
public class TvMediaOutputController extends MediaOutputController {

    private final Context mContext;
    private final AudioManager mAudioManager;

    public TvMediaOutputController(
            @NotNull Context context,
            String packageName,
            MediaSessionManager mediaSessionManager,
            LocalBluetoothManager lbm,
            ActivityStarter starter,
            CommonNotifCollection notifCollection,
            DialogTransitionAnimator dialogTransitionAnimator,
            NearbyMediaDevicesManager nearbyMediaDevicesManager,
            AudioManager audioManager,
            PowerExemptionManager powerExemptionManager,
            KeyguardManager keyGuardManager,
            FeatureFlags featureFlags,
            UserTracker userTracker) {
        super(context, packageName, mediaSessionManager, lbm, starter, notifCollection,
                dialogTransitionAnimator, nearbyMediaDevicesManager, audioManager,
                powerExemptionManager, keyGuardManager, featureFlags, userTracker);
        mContext = context;
        mAudioManager = audioManager;
    }

    void showVolumeDialog() {
        mAudioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
    }

    /**
     * Assigns lower priorities to devices that should be shown higher up in the list.
     */
    private int getDevicePriorityGroup(MediaDevice mediaDevice) {
        int mediaDeviceType = mediaDevice.getDeviceType();
        return switch (mediaDeviceType) {
            case TYPE_PHONE_DEVICE -> 1;
            case TYPE_USB_C_AUDIO_DEVICE -> 2;
            case TYPE_3POINT5_MM_AUDIO_DEVICE -> 3;
            case TYPE_CAST_DEVICE, TYPE_CAST_GROUP_DEVICE, TYPE_BLUETOOTH_DEVICE,
                    TYPE_FAST_PAIR_BLUETOOTH_DEVICE -> 5;
            default -> 4;
        };
    }

    private void sortMediaDevices(List<MediaDevice> mediaDevices) {
        mediaDevices.sort((device1, device2) -> {
            int priority1 = getDevicePriorityGroup(device1);
            int priority2 = getDevicePriorityGroup(device2);

            if (priority1 != priority2) {
                return (priority1 < priority2) ? -1 : 1;
            }
            // Show connected before disconnected devices
            if (device1.isConnected() != device2.isConnected()) {
                return device1.isConnected() ? -1 : 1;
            }
            return device1.getName().compareToIgnoreCase(device2.getName());
        });
    }

    @Override
    protected List<MediaItem> buildMediaItems(List<MediaItem> oldMediaItems,
            List<MediaDevice> devices) {
        synchronized (mMediaDevicesLock) {
            if (oldMediaItems.isEmpty()) {
                return buildInitialList(devices);
            }
            return buildBetterSubsequentList(oldMediaItems, devices);
        }
    }

    private List<MediaItem> buildInitialList(List<MediaDevice> devices) {
        sortMediaDevices(devices);

        List<MediaItem> finalMediaItems = new ArrayList<>();
        boolean disconnectedDevicesAdded = false;
        for (MediaDevice device : devices) {
            // Add divider before first disconnected device
            if (!device.isConnected() && !disconnectedDevicesAdded) {
                addOtherDevicesDivider(finalMediaItems);
                disconnectedDevicesAdded = true;
            }
            finalMediaItems.add(new MediaItem(device));
        }
        addConnectAnotherDeviceItem(finalMediaItems);
        return finalMediaItems;
    }

    /**
     * Keep devices that have not changed their connection state in the same order.
     * If there is a new connected device, put it at the *bottom* of the connected devices list and
     * if there is a newly disconnected device, add it at the *top* of the disconnected devices.
     */
    private List<MediaItem> buildBetterSubsequentList(List<MediaItem> previousMediaItems,
            List<MediaDevice> devices) {

        final List<MediaItem> targetMediaItems = new ArrayList<>();
        // Only use the actual devices, not the dividers etc.
        List<MediaItem> oldMediaItems = previousMediaItems.stream()
                .filter(mediaItem -> mediaItem.getMediaDevice().isPresent()).toList();
        addItemsBasedOnConnection(targetMediaItems, oldMediaItems, devices,
                /* isConnected= */ true);
        addItemsBasedOnConnection(targetMediaItems, oldMediaItems, devices,
                /* isConnected= */ false);

        addConnectAnotherDeviceItem(targetMediaItems);
        return targetMediaItems;
    }

    private void addItemsBasedOnConnection(List<MediaItem> targetMediaItems,
            List<MediaItem> oldMediaItems, List<MediaDevice> devices, boolean isConnected) {

        List<MediaDevice> matchingMediaDevices = new ArrayList<>();
        for (MediaItem originalMediaItem : oldMediaItems) {
            // Only go through the device items
            MediaDevice oldDevice = originalMediaItem.getMediaDevice().get();

            for (MediaDevice newDevice : devices) {
                if (TextUtils.equals(oldDevice.getId(), newDevice.getId())
                        && oldDevice.isConnected() == isConnected
                        && newDevice.isConnected() == isConnected) {
                    matchingMediaDevices.add(newDevice);
                    break;
                }
            }
        }
        devices.removeAll(matchingMediaDevices);

        List<MediaDevice> newMediaDevices = new ArrayList<>();
        for (MediaDevice remainingDevice : devices) {
            if (remainingDevice.isConnected() == isConnected) {
                newMediaDevices.add(remainingDevice);
            }
        }
        devices.removeAll(newMediaDevices);

        // Add new connected devices at the end, add new disconnected devices at the start
        if (isConnected) {
            targetMediaItems.addAll(matchingMediaDevices.stream().map(MediaItem::new).toList());
            targetMediaItems.addAll(newMediaDevices.stream().map(MediaItem::new).toList());
        } else {
            if (!matchingMediaDevices.isEmpty() || !newMediaDevices.isEmpty()) {
                addOtherDevicesDivider(targetMediaItems);
            }
            targetMediaItems.addAll(newMediaDevices.stream().map(MediaItem::new).toList());
            targetMediaItems.addAll(matchingMediaDevices.stream().map(MediaItem::new).toList());
        }
    }

    private void addOtherDevicesDivider(List<MediaItem> mediaItems) {
        mediaItems.add(new MediaItem(mContext.getString(
                R.string.media_output_dialog_other_devices),
                MediaItem.MediaItemType.TYPE_GROUP_DIVIDER));
    }

    private void addConnectAnotherDeviceItem(List<MediaItem> mediaItems) {
        mediaItems.add(new MediaItem(null, MediaItem.MediaItemType.TYPE_GROUP_DIVIDER));
        mediaItems.add(new MediaItem());
    }

    @Override
    protected void start(@NotNull Callback cb) {
        super.start(cb);
    }

    @Override
    protected void stop() {
        super.stop();
    }

    @Override
    protected void setTemporaryAllowListExceptionIfNeeded(MediaDevice targetDevice) {
        super.setTemporaryAllowListExceptionIfNeeded(targetDevice);
    }

    @Override
    protected void connectDevice(MediaDevice mediaDevice) {
        super.connectDevice(mediaDevice);
    }
}
