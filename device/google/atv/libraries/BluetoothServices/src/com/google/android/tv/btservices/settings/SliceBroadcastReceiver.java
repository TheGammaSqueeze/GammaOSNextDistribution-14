/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.tv.btservices.settings;

import static android.content.Intent.FLAG_INCLUDE_STOPPED_PACKAGES;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.content.Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND;

import static com.android.tv.twopanelsettings.slices.SlicesConstants.EXTRA_SLICE_FOLLOWUP;
import static com.google.android.tv.btservices.settings.ConnectedDevicesSliceProvider.KEY_EXTRAS_DEVICE;
import static com.google.android.tv.btservices.settings.SlicesUtil.CEC_SLICE_URI;
import static com.google.android.tv.btservices.settings.SlicesUtil.EXTRAS_SLICE_URI;
import static com.google.android.tv.btservices.settings.SlicesUtil.FIND_MY_REMOTE_PHYSICAL_BUTTON_ENABLED_SETTING;
import static com.google.android.tv.btservices.settings.SlicesUtil.FIND_MY_REMOTE_SLICE_URI;
import static com.google.android.tv.btservices.settings.SlicesUtil.GENERAL_SLICE_URI;
import static com.google.android.tv.btservices.settings.SlicesUtil.notifyToGoBack;
import static com.google.android.tv.btservices.settings.SlicesUtil.setFindMyRemoteButtonEnabled;

import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import android.util.Log;

import com.google.android.tv.btservices.BluetoothUtils;
import com.google.android.tv.btservices.PowerUtils;

import java.util.ArrayList;

/**
 * This broadcast receiver handles two cases:
 * (a) CEC control toggle.
 * (b) Handle the followup pending intent for "rename"/"forget" preference to notify TvSettings UI
 * flow to go back.
 */
public class SliceBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "SliceBroadcastReceiver";
    static final String CEC = "CEC";

    static final String TOGGLE_TYPE = "TOGGLE_TYPE";
    static final String TOGGLE_STATE = "TOGGLE_STATE";

    static final String ACTION_TOGGLE_CHANGED = "com.google.android.settings.usage.TOGGLE_CHANGED";
    static final String ACTION_FIND_MY_REMOTE = "com.google.android.tv.FIND_MY_REMOTE";
    static final String ACTIVE_AUDIO_OUTPUT = "ACTIVE_AUDIO_OUTPUT";
    private static final String ACTION_UPDATE_SLICE = "UPDATE_SLICE";
    private static final String ACTION_BACK_AND_UPDATE_SLICE = "BACK_AND_UPDATE_SLICE";
    private static final String PARAM_URIS = "URIS";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case ACTION_TOGGLE_CHANGED: {
                final boolean isChecked = intent.getBooleanExtra(TOGGLE_STATE, false);
                final String toggleType = intent.getStringExtra(TOGGLE_TYPE);
                if (CEC.equals(toggleType)) {
                    PowerUtils.enableCecControl(context, isChecked);
                    context.getContentResolver().notifyChange(CEC_SLICE_URI, null);
                    context.getContentResolver().notifyChange(GENERAL_SLICE_URI, null);
                } else if (FIND_MY_REMOTE_PHYSICAL_BUTTON_ENABLED_SETTING.equals(toggleType)) {
                    setFindMyRemoteButtonEnabled(context, isChecked);
                    context.getContentResolver().notifyChange(FIND_MY_REMOTE_SLICE_URI, null);
                } else if (ACTIVE_AUDIO_OUTPUT.equals(toggleType)) {
                    boolean enable = intent.getBooleanExtra(TOGGLE_STATE, false);
                    BluetoothDevice device = intent.getParcelableExtra(KEY_EXTRAS_DEVICE,
                            BluetoothDevice.class);
                    BluetoothUtils.setActiveAudioOutput(enable ? device : null);
                    // If there is followup pendingIntent, send it
                    try {
                        PendingIntent followupPendingIntent = intent.getParcelableExtra(
                                EXTRA_SLICE_FOLLOWUP, PendingIntent.class);
                        if (followupPendingIntent != null) {
                            followupPendingIntent.send();
                        }
                    } catch (Throwable ex) {
                        Log.e(TAG, "Followup PendingIntent for slice cannot be sent", ex);
                    }
                }
                break;
            }

            case ACTION_BACK_AND_UPDATE_SLICE:
                notifyToGoBack(context, Uri.parse(intent.getStringExtra(EXTRAS_SLICE_URI)));
                // fall-through
            case ACTION_UPDATE_SLICE:
                ArrayList<String> uris = intent.getStringArrayListExtra(PARAM_URIS);
                uris.forEach(
                        uri -> context.getContentResolver().notifyChange(Uri.parse(uri), null));
                break;
            case ACTION_FIND_MY_REMOTE:
                context.sendBroadcast(
                        new Intent(ACTION_FIND_MY_REMOTE)
                                .putExtra("reason", "SETTINGS")
                                .setFlags(FLAG_INCLUDE_STOPPED_PACKAGES | FLAG_RECEIVER_FOREGROUND
                                        | FLAG_RECEIVER_INCLUDE_BACKGROUND),
                        "com.google.android.tv.permission.FIND_MY_REMOTE");
                break;

            default:
                // no-op
        }
    }

    public static PendingIntent updateSliceIntent(Context context, int requestCode,
            ArrayList<String> uris, String updatedUri) {
        Intent i = new Intent(context, SliceBroadcastReceiver.class).setAction(
                ACTION_UPDATE_SLICE).putStringArrayListExtra(PARAM_URIS, uris).setData(
                Uri.parse(updatedUri));
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static PendingIntent backAndUpdateSliceIntent(Context context, int requestCode,
            ArrayList<String> uris, String navigatingBackUri) {
        Intent i = new Intent(context, SliceBroadcastReceiver.class).setAction(
                ACTION_BACK_AND_UPDATE_SLICE).putStringArrayListExtra(PARAM_URIS, uris).putExtra(
                EXTRAS_SLICE_URI, navigatingBackUri).setData(Uri.parse(navigatingBackUri));
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
