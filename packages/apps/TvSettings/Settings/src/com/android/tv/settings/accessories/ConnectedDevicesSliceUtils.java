/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.accessories;

import android.bluetooth.BluetoothDevice;
import android.content.ContentProviderClient;
import android.content.Context;
import android.net.Uri;
import android.provider.Settings;

import com.android.tv.twopanelsettings.slices.SlicesConstants;

/** Util class for {@ConnectedDevicesSliceProvider} */
public final class ConnectedDevicesSliceUtils {

    static final String AUTHORITY = "com.android.tv.settings.accessories.sliceprovider";
    static final String GENERAL_PATH = "general";
    static final String BLUETOOTH_DEVICE_PATH = "device";
    static final String EXTRAS_DIRECTION = "extras_direction";
    static final String EXTRAS_SLICE_URI = "extras_slice_uri";
    static final String FIND_MY_REMOTE_PATH = "find_my_remote";
    static final String DIRECTION_BACK = "direction_back";
    public static final Uri GENERAL_SLICE_URI =
            Uri.parse("content://" + AUTHORITY + "/" + GENERAL_PATH);
    static final Uri BLUETOOTH_DEVICE_SLICE_URI =
            Uri.parse("content://" + AUTHORITY + "/" + BLUETOOTH_DEVICE_PATH);
    static final Uri FIND_MY_REMOTE_SLICE_URI =
            Uri.parse("content://" + AUTHORITY + "/" + FIND_MY_REMOTE_PATH);

    /**
     * The {@link Settings.Global} integer setting name.
     *
     * <p>The settings tells whether the physical button integration for Find My Remote feature
     * is enabled. Default value: 1.
     */
    static final String FIND_MY_REMOTE_PHYSICAL_BUTTON_ENABLED_SETTING =
            "find_my_remote_physical_button_enabled";

    static String getDeviceAddr(Uri uri) {
        if (uri.getPathSegments().size() >= 2) {
            return uri.getPathSegments().get(1).split(" ")[0];
        }
        return null;
    }

    static boolean isGeneralPath(Uri uri) {
        return GENERAL_PATH.equals(getFirstSegment(uri));
    }

    static boolean isBluetoothDevicePath(Uri uri) {
        return BLUETOOTH_DEVICE_PATH.equals(getFirstSegment(uri));
    }

    static boolean isFindMyRemotePath(Uri uri) {
        return FIND_MY_REMOTE_PATH.equals(getFirstSegment(uri));
    }

    /** Check if slice provider exists. */
    static boolean isSliceProviderValid(Context context, String uri) {
        if (uri == null) {
            return false;
        }
        ContentProviderClient client =
                context.getContentResolver().acquireContentProviderClient(Uri.parse(uri));
        if (client != null) {
            client.close();
            return true;
        } else {
            return false;
        }
    }

    static Uri getDeviceUri(String deviceAddr, String aliasName) {
        return Uri.withAppendedPath(
                BLUETOOTH_DEVICE_SLICE_URI, deviceAddr + " " + aliasName);
    }

    private static String getFirstSegment(Uri uri) {
        if (uri.getPathSegments().size() > 0) {
            return uri.getPathSegments().get(0);
        }
        return null;
    }

    static void notifyToGoBack(Context context, Uri uri) {
        Uri appendedUri = uri
                .buildUpon().path("/" + SlicesConstants.PATH_STATUS)
                .appendQueryParameter(SlicesConstants.PARAMETER_URI, uri.toString())
                .appendQueryParameter(SlicesConstants.PARAMETER_DIRECTION, SlicesConstants.BACKWARD)
                .build();
        context.getContentResolver().notifyChange(appendedUri, null);
    }

    static void notifyDeviceChanged(Context context, BluetoothDevice device) {
        if (device != null) {
            context.getContentResolver().notifyChange(
                    getDeviceUri(device.getAddress(), device.getAlias()), null);
        }
    }

    private ConnectedDevicesSliceUtils() {
        // do not allow instantiation
    }

    public static boolean isFindMyRemoteButtonEnabled(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                FIND_MY_REMOTE_PHYSICAL_BUTTON_ENABLED_SETTING, 1) != 0;
    }

    static void setFindMyRemoteButtonEnabled(Context context, boolean enabled) {
        Settings.Global.putInt(context.getContentResolver(),
                FIND_MY_REMOTE_PHYSICAL_BUTTON_ENABLED_SETTING,
                enabled ? 1 : 0);
    }
}
