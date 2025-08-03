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

package android.car.builtin.display;

import android.annotation.FloatRange;
import android.annotation.SystemApi;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.hardware.display.DisplayManager.EventsMask;
import android.os.Handler;

/**
 * Helper for DisplayManager related operations.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class DisplayManagerHelper {

    /**
     * Event type for when a new display is added.
     *
     * @see #registerDisplayListener(DisplayListener, Handler, long)
     */
    public static final long EVENT_FLAG_DISPLAY_ADDED = DisplayManager.EVENT_FLAG_DISPLAY_ADDED;

    /**
     * Event type for when a display is removed.
     *
     * @see #registerDisplayListener(DisplayListener, Handler, long)
     */
    public static final long EVENT_FLAG_DISPLAY_REMOVED = DisplayManager.EVENT_FLAG_DISPLAY_REMOVED;

    /**
     * Event type for when a display is changed.
     *
     * @see #registerDisplayListener(DisplayListener, Handler, long)
     */
    public static final long EVENT_FLAG_DISPLAY_CHANGED = DisplayManager.EVENT_FLAG_DISPLAY_CHANGED;

    /**
     * Event flag to register for a display's brightness changes. This notification is sent
     * through the {@link DisplayListener#onDisplayChanged} callback method. New brightness
     * values can be retrieved via {@link android.view.Display#getBrightnessInfo()}.
     *
     * @see #registerDisplayListener(DisplayListener, Handler, long)
     */
    public static final long EVENT_FLAG_DISPLAY_BRIGHTNESS =
            DisplayManager.EVENT_FLAG_DISPLAY_BRIGHTNESS;

    private DisplayManagerHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }

    /**
     * Registers a display listener to receive notifications about given display event types.
     *
     * @param context The context to use.
     * @param listener The listener to register.
     * @param handler The handler on which the listener should be invoked, or null
     * if the listener should be invoked on the calling thread's looper.
     * @param eventsMask A bitmask of the event types for which this listener is subscribed.
     *
     * @see DisplayManager#EVENT_FLAG_DISPLAY_ADDED
     * @see DisplayManager#EVENT_FLAG_DISPLAY_CHANGED
     * @see DisplayManager#EVENT_FLAG_DISPLAY_REMOVED
     * @see DisplayManager#EVENT_FLAG_DISPLAY_BRIGHTNESS
     * @see DisplayManager#registerDisplayListener(DisplayListener, Handler)
     * @see DisplayManager#unregisterDisplayListener
     */
    public static void registerDisplayListener(Context context, DisplayListener listener,
            Handler handler, @EventsMask long eventsMask) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        displayManager.registerDisplayListener(listener, handler, eventsMask);
    }

    /**
     * Gets the brightness of the specified display.
     *
     * @param context Context to use.
     * @param displayId The display of which brightness value to get from.
     */
    public static float getBrightness(Context context, int displayId) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        return displayManager.getBrightness(displayId);
    }

    /**
     * Sets the brightness of the specified display.
     *
     * @param context Context to use.
     * @param displayId the logical display id
     * @param brightness The brightness value from 0.0f to 1.0f.
     */
    public static void setBrightness(Context context, int displayId,
            @FloatRange(from = 0f, to = 1f) float brightness) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        displayManager.setBrightness(displayId, brightness);
    }
}
