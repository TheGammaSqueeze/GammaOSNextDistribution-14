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

package com.android.car.custominput.sample;

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;

import android.app.ActivityOptions;
import android.car.CarOccupantZoneManager;
import android.car.input.CustomInputEvent;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Handles incoming {@link CustomInputEvent}. In this implementation, incoming events are expected
 * to have the display id and the function set.
 */
public final class CustomInputEventListener {

    private static final String TAG = "CustomInputEventSample";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String NOTIFY_DRIVER_UI_ACTION =
            "com.android.car.custominput.sample.driverui.action";
    private static final String NOTIFY_DRIVER_UI_EXTRA_KEY = "NOTIFY_DRIVER_UI_EXTRA_KEY";
    private static final String NOTIFY_DRIVER_UI_MOVE_NAV_VIEW = "MOVE_NAV_VIEW";

    private final SampleCustomInputService mService;
    private final Context mContext;
    private final CarAudioManager mCarAudioManager;
    private final CarOccupantZoneManager mCarOccupantZoneManager;

    /** List of defined actions for this reference service implementation */
    @Retention(RetentionPolicy.SOURCE)
    public @interface EventAction {

        /** Launches Map action. */
        int LAUNCH_MAPS_ACTION = 1001;

        /** Accepts incoming call action. */
        int ACCEPT_INCOMING_CALL_ACTION = 1002;

        /** Rejects incoming call action. */
        int REJECT_INCOMING_CALL_ACTION = 1003;

        /** Increases media volume action. */
        int INCREASE_MEDIA_VOLUME_ACTION = 1004;

        /** Increases media volume action. */
        int DECREASE_MEDIA_VOLUME_ACTION = 1005;

        /** Increases alarm volume action. */
        int INCREASE_ALARM_VOLUME_ACTION = 1006;

        /** Increases alarm volume action. */
        int DECREASE_ALARM_VOLUME_ACTION = 1007;

        /** Simulates the HOME button (re-injects the HOME KeyEvent against Car Input API. */
        int BACK_HOME_ACTION = 1008;

        /** Injects KEYCODE_VOICE_ASSIST (action down) key event */
        int INJECT_VOICE_ASSIST_ACTION_DOWN = 1009;

        /** Injects KEYCODE_VOICE_ASSIST (action up) key event */
        int INJECT_VOICE_ASSIST_ACTION_UP = 1010;

        /** Notify DriverUi to move the navigation view */
        int DRIVER_UI_MOVE_NAVIGATION_VIEW = 1011;
    }

    public CustomInputEventListener(
            @NonNull Context context,
            @NonNull CarAudioManager carAudioManager,
            @NonNull CarOccupantZoneManager carOccupantZoneManager,
            @NonNull SampleCustomInputService service) {
        mContext = context;
        mCarAudioManager = carAudioManager;
        mCarOccupantZoneManager = carOccupantZoneManager;
        mService = service;
    }

    public void handle(int targetDisplayType, CustomInputEvent event) {
        if (!isValidTargetDisplayType(targetDisplayType)) {
            return;
        }
        int targetDisplayId = getDisplayIdForDisplayType(targetDisplayType);
        @EventAction int action = event.getInputCode();
        switch (action) {
            case EventAction.LAUNCH_MAPS_ACTION:
                launchMap(targetDisplayId);
                break;
            case EventAction.ACCEPT_INCOMING_CALL_ACTION:
                acceptIncomingCall(targetDisplayType);
                break;
            case EventAction.REJECT_INCOMING_CALL_ACTION:
                rejectIncomingCall(targetDisplayType);
                break;
            case EventAction.INCREASE_MEDIA_VOLUME_ACTION:
                increaseVolume(targetDisplayId, AudioAttributes.USAGE_MEDIA);
                break;
            case EventAction.DECREASE_MEDIA_VOLUME_ACTION:
                decreaseVolume(targetDisplayId, AudioAttributes.USAGE_MEDIA);
                break;
            case EventAction.INCREASE_ALARM_VOLUME_ACTION:
                increaseVolume(targetDisplayId, AudioAttributes.USAGE_ALARM);
                break;
            case EventAction.DECREASE_ALARM_VOLUME_ACTION:
                decreaseVolume(targetDisplayId, AudioAttributes.USAGE_ALARM);
                break;
            case EventAction.BACK_HOME_ACTION:
                launchHome(targetDisplayType);
                break;
            case EventAction.INJECT_VOICE_ASSIST_ACTION_DOWN:
                injectKeyEvent(targetDisplayType,
                        newKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST));
                break;
            case EventAction.INJECT_VOICE_ASSIST_ACTION_UP:
                injectKeyEvent(targetDisplayType,
                        newKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST));
                break;
            case EventAction.DRIVER_UI_MOVE_NAVIGATION_VIEW:
                notifyDriverUi(NOTIFY_DRIVER_UI_MOVE_NAV_VIEW);
                break;
            default:
                Log.e(TAG, "Ignoring event [" + action + "]");
        }
    }

    private int getDisplayIdForDisplayType(int targetDisplayType) {
        int displayId = mCarOccupantZoneManager.getDisplayIdForDriver(targetDisplayType);
        if (DEBUG) {
            Log.d(TAG, "Resolved display id {" + displayId + "} for display type {"
                    + targetDisplayType + "}");
        }
        return displayId;
    }

    private int getOccupantZoneIdForDisplayId(/* unused for now */ int displayId) {
        // TODO(b/170975186): Use CarOccupantZoneManager to retrieve the associated zoneId with
        //                  the display id passed as parameter.
        return PRIMARY_AUDIO_ZONE;
    }

    private static boolean isValidTargetDisplayType(int displayType) {
        if (displayType == CarOccupantZoneManager.DISPLAY_TYPE_MAIN) {
            return true;
        }
        Log.w(TAG,
                "This service implementation can only handle CustomInputEvent with "
                        + "targetDisplayType set to main display (main display type is {"
                        + CarOccupantZoneManager.DISPLAY_TYPE_MAIN + "}), current display type is {"
                        + displayType + "})");
        return false;
    }

    private void notifyDriverUi(String action) {
        if (DEBUG) {
            Log.d(TAG, "Notify DriverUi w/ action =" + action);
        }
        Intent driveUiIntent = new Intent(NOTIFY_DRIVER_UI_ACTION);
        driveUiIntent.putExtra(NOTIFY_DRIVER_UI_EXTRA_KEY, action);
        driveUiIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        mService.sendBroadcast(driveUiIntent);
    }

    private void launchMap(int targetDisplayId) {
        if (DEBUG) {
            Log.d(TAG, "Launching Maps on display id {" + targetDisplayId + "}");
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(targetDisplayId);
        Intent mapsIntent = new Intent(Intent.ACTION_VIEW);
        mapsIntent.setClassName(mContext.getString(R.string.maps_app_package),
                mContext.getString(R.string.maps_activity_class));
        mapsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mService.startActivity(mapsIntent, options.toBundle());
    }

    private void acceptIncomingCall(int targetDisplayType) {
        Log.i(TAG, "Entering acceptIncomingCall");
        if (DEBUG) {
            Log.d(TAG, "Accepting incoming call on display id {" + targetDisplayType + "}");
        }
        injectKeyEvent(targetDisplayType, KeyEvent.KEYCODE_CALL);
    }

    private void rejectIncomingCall(int targetDisplayType) {
        if (DEBUG) {
            Log.d(TAG, "Rejecting incoming call on display id {" + targetDisplayType + "}");
        }
        injectKeyEvent(targetDisplayType, KeyEvent.KEYCODE_ENDCALL);
    }

    private void increaseVolume(int targetDisplayId, int usage) {
        int zoneId = getOccupantZoneIdForDisplayId(targetDisplayId);
        int volumeGroupId = mCarAudioManager.getVolumeGroupIdForUsage(zoneId, usage);
        int maxVolume = mCarAudioManager.getGroupMaxVolume(zoneId, volumeGroupId);
        int volume = mCarAudioManager.getGroupVolume(zoneId, volumeGroupId);
        if (volume > maxVolume) {
            throw new IllegalStateException("Volume (" + volume + ") is higher than MaxVolume ("
                    + maxVolume + ") for zoneId (" + zoneId + ") and volumeGroupId ("
                    + volumeGroupId + ")");
        }
        if (volume == maxVolume) {
            if (DEBUG) {
                Log.d(TAG, "Volume for stream type (" + usage + ") is already max (" + maxVolume
                        + ")");
            }
            return;
        }
        volume++;
        if (DEBUG) {
            Log.d(TAG, "Increasing volume for stream type (" + usage + ") to: " + volume
                    + " (max volume is "
                    + maxVolume + ")");
        }
        mCarAudioManager.setGroupVolume(volumeGroupId, volume, AudioManager.FLAG_SHOW_UI);
    }

    private void decreaseVolume(int targetDisplayId, int usage) {
        int zoneId = getOccupantZoneIdForDisplayId(targetDisplayId);
        int volumeGroupId = mCarAudioManager.getVolumeGroupIdForUsage(zoneId, usage);
        int minVolume = mCarAudioManager.getGroupMinVolume(zoneId, volumeGroupId);
        int volume = mCarAudioManager.getGroupVolume(zoneId, volumeGroupId);
        if (volume < minVolume) {
            throw new IllegalStateException("Volume (" + volume + ") is lower than MinVolume ("
                    + minVolume + ") for zoneId (" + zoneId + ") and volumeGroupId ("
                    + volumeGroupId + ")");
        }
        if (volume == minVolume) {
            if (DEBUG) {
                Log.d(TAG, "Volume for stream type (" + usage + ") is already min (" + minVolume
                        + ")");
            }
            return;
        }
        volume--;
        if (DEBUG) {
            Log.d(TAG, "Decreasing volume for stream type (" + usage + ") to: " + volume
                    + " (min volume is "
                    + minVolume + ")");
        }
        mCarAudioManager.setGroupVolume(volumeGroupId, volume, AudioManager.FLAG_SHOW_UI);
    }

    private void launchHome(int targetDisplayType) {
        if (DEBUG) {
            Log.d(TAG, "Injecting HOME KeyEvent on display type {" + targetDisplayType + "}");
        }
        injectKeyEvent(targetDisplayType, KeyEvent.KEYCODE_HOME);
    }

    private void injectKeyEvent(int targetDisplayType, int keyCode) {
        KeyEvent keyDown = newKeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        injectKeyEvent(targetDisplayType, keyDown);

        KeyEvent keyUp = newKeyEvent(KeyEvent.ACTION_UP, keyCode);
        injectKeyEvent(targetDisplayType, keyUp);
    }

    private KeyEvent newKeyEvent(int action, int keyCode) {
        long currentTime = SystemClock.uptimeMillis();
        return new KeyEvent(/* downTime= */ currentTime, /* eventTime= */ currentTime,
                action, keyCode, /* repeat= */ 0);
    }

    private void injectKeyEvent(int targetDisplayType, KeyEvent event) {
        mService.injectKeyEvent(event, targetDisplayType);
    }

}
