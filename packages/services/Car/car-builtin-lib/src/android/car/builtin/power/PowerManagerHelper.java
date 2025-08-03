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

package android.car.builtin.power;

import android.annotation.SystemApi;
import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

/**
 * Helper for PowerManager related operations.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class PowerManagerHelper {

    /** See {@code PowerManager.BRIGHTNESS_ON} */
    public static final int BRIGHTNESS_ON = PowerManager.BRIGHTNESS_ON;

    /** See {@code PowerManager.BRIGHTNESS_OFF} */
    public static final int BRIGHTNESS_OFF = PowerManager.BRIGHTNESS_OFF;

    /** See {@code PowerManager.BRIGHTNESS_DEFAULT} */
    public static final int BRIGHTNESS_DEFAULT = PowerManager.BRIGHTNESS_DEFAULT;

    /** See {@code PowerManager.BRIGHTNESS_INVALID} */
    public static final int BRIGHTNESS_INVALID = PowerManager.BRIGHTNESS_INVALID;

    /** See {@code PowerManager.BRIGHTNESS_MAX} */
    public static final float BRIGHTNESS_MAX = PowerManager.BRIGHTNESS_MAX;

    /** See {@code PowerManager.BRIGHTNESS_MIN} */
    public static final float BRIGHTNESS_MIN = PowerManager.BRIGHTNESS_MIN;

    /** See {@code PowerManager.BRIGHTNESS_OFF_FLOAT} */
    public static final float BRIGHTNESS_OFF_FLOAT = PowerManager.BRIGHTNESS_OFF_FLOAT;

    /** See {@code PowerManager.BRIGHTNESS_INVALID_FLOAT} */
    public static final float BRIGHTNESS_INVALID_FLOAT = PowerManager.BRIGHTNESS_INVALID_FLOAT;

    private PowerManagerHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }

    /**
     * Gets the maximum supported screen brightness setting.
     * This wraps {@link PowerManager.getMaximumScreenBrightnessSetting}.
     *
     * @param context Context to use.
     * @return The maximum value that can be set by the user.
     */
    public static int getMaximumScreenBrightnessSetting(Context context) {
        return context.getSystemService(PowerManager.class).getMaximumScreenBrightnessSetting();
    }

    /**
     * Gets the minimum supported screen brightness setting.
     * This wraps {@link PowerManager.getMinimumScreenBrightnessSetting}.
     *
     * @param context Context to use.
     * @return The minimum value that can be set by the user.
     */
    public static int getMinimumScreenBrightnessSetting(Context context) {
        return context.getSystemService(PowerManager.class).getMinimumScreenBrightnessSetting();
    }

    /**
     * Forces the {@link com.android.server.display.DisplayGroup#DEFAULT default display group}
     * to turn on or off.
     *
     * @param context Context to use.
     * @param on Whether to turn the display on or off.
     * @param upTime The time when the request was issued, in the {@link SystemClock#uptimeMillis}
     *               time base.
     */
    public static void setDisplayState(Context context, boolean on, long upTime) {
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        if (on) {
            powerManager.wakeUp(upTime, PowerManager.WAKE_REASON_UNKNOWN, "wake up by CarService");
        } else {
            powerManager.goToSleep(upTime,
                    PowerManager.GO_TO_SLEEP_REASON_DISPLAY_GROUPS_TURNED_OFF, /* flags= */ 0);
        }
    }

    /**
     * Turns off the display of {@code displayId}.
     *
     * @param context Context to use.
     * @param displayId The display ID to turn off. If {@code displayId} is
     *                  {@link Display#INVALID_DISPLAY}, then all displays are turned off.
     * @param upTime The time when the request was issued, in the {@link SystemClock#uptimeMillis}
     *               time base.
     */
    public static void goToSleep(Context context, int displayId, long upTime) {
        context.getSystemService(PowerManager.class).goToSleep(displayId, upTime,
                PowerManager.GO_TO_SLEEP_REASON_DISPLAY_GROUPS_TURNED_OFF, /* flags= */ 0);
    }

    /**
     * Turns off the device.
     *
     * @param context Context to use.
     * @param confirm If {@code true}, shows a shutdown confirmation dialog.
     * @param reason Code to pass to android_reboot() (e.g. "userrequested"), or {@code null}.
     * @param wait If {@code true}, this call waits for the shutdown to complete and does not
     *             return.
     */
    public static void shutdown(Context context, boolean confirm, String reason, boolean wait) {
        context.getSystemService(PowerManager.class).shutdown(confirm, reason, wait);
    }

    /**
     * Acquires a wake lock for the givien display.
     *
     * <p>This wraps {@link PowerManager#newWakeLock(int, String, int)}.
     *
     * @param context Context to use.
     * @param levelAndFlags Combination of wake lock level and flag values defining the requested
     *                      behavior of the WakeLock.
     * @param tag Your class name (or other tag) for debugging purposes.
     * @param displayId The display id to which this wake lock is tied.
     *
     * @see PowerManager#PARTIAL_WAKE_LOCK
     * @see PowerManager#FULL_WAKE_LOCK
     * @see PowerManager#SCREEN_DIM_WAKE_LOCK
     * @see PowerManager#SCREEN_BRIGHT_WAKE_LOCK
     * @see PowerManager#PROXIMITY_SCREEN_OFF_WAKE_LOCK
     * @see PowerManager#ACQUIRE_CAUSES_WAKEUP
     * @see PowerManager#ON_AFTER_RELEASE
     */
    public static WakeLock newWakeLock(Context context, int levelAndFlags, String tag,
            int displayId) {
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        return powerManager.newWakeLock(levelAndFlags, tag, displayId);
    }
}
