/**
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accessibilityservice.cts.utils;

import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.findWindowByTitleAndDisplay;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.view.Display.DEFAULT_DISPLAY;

import android.app.Activity;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowInsets;

import com.android.compatibility.common.util.TestUtils;

/**
 * Utilities needed when interacting with the display
 */
public class DisplayUtils {
    private static final int DISPLAY_ADDED_TIMEOUT_MS = 5000;
    // Tolerance that allows for rounding differences in how various parts of
    // Android calculate on-screen bounds given non-integer screen scaling or
    // dp/pixel density.
    private static final int BOUNDS_IN_SCREEN_TOLERANCE_PX = 1;

    public static int getStatusBarHeight(Activity activity) {
        return activity.getWindow().getDecorView().getRootWindowInsets()
                .getInsets(WindowInsets.Type.statusBars()).top;
    }

    /**
     * Checks if the bounds origin match the provided point, to a tolerance of
     * {@link #BOUNDS_IN_SCREEN_TOLERANCE_PX} pixels.
     */
    public static boolean fuzzyBoundsInScreenSameOrigin(int[] origin, Rect bounds) {
        return Math.abs((origin[0]) - bounds.left) <= BOUNDS_IN_SCREEN_TOLERANCE_PX
                && Math.abs((origin[1]) - bounds.top) <= BOUNDS_IN_SCREEN_TOLERANCE_PX;
    }

    /**
     * Checks if the bounds origins match each other, to a tolerance of
     * {@link #BOUNDS_IN_SCREEN_TOLERANCE_PX} pixels.
     */
    public static boolean fuzzyBoundsInScreenSameOrigin(Rect boundsA, Rect boundsB) {
        return Math.abs((boundsA.left) - boundsB.left) <= BOUNDS_IN_SCREEN_TOLERANCE_PX
                && Math.abs((boundsA.top) - boundsB.top) <= BOUNDS_IN_SCREEN_TOLERANCE_PX;
    }

    /**
     * Checks if a larger rect contains another, to a tolerance of
     * {@link #BOUNDS_IN_SCREEN_TOLERANCE_PX} pixels.
     */
    public static boolean fuzzyBoundsInScreenContains(Rect larger, Rect smaller) {
        final Rect largerExpanded = new Rect(larger);
        largerExpanded.inset(-BOUNDS_IN_SCREEN_TOLERANCE_PX, -BOUNDS_IN_SCREEN_TOLERANCE_PX);
        return largerExpanded.contains(smaller);
    }

    public static class VirtualDisplaySession implements AutoCloseable {
        private VirtualDisplay mVirtualDisplay;
        private ImageReader mReader;

        public Display createDisplay(Context context, int width, int height, int density,
                boolean isPrivate) {
            if (mReader != null) {
                throw new IllegalStateException(
                        "Only one display can be created during this session.");
            }
            mReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888,
                    1 /* maxImages */);
            int flags = isPrivate ? 0
                    :(VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | VIRTUAL_DISPLAY_FLAG_PUBLIC);
            mVirtualDisplay = context.getSystemService(DisplayManager.class).createVirtualDisplay(
                    "A11yDisplay", width, height, density, mReader.getSurface(), flags);
            return mVirtualDisplay.getDisplay();
        }

        @Override
        public void close() {
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
            }
            if (mReader != null) {
                mReader.close();
            }
        }

        /**
         * Creates a virtual display having same size with default display and waits until it's
         * in display list. The density of the virtual display is based on
         * {@link DisplayMetrics#xdpi} so that the threshold of gesture detection is same as
         * the default display's.
         *
         * @param context
         * @param isPrivate if this display is a private display.
         * @return virtual display.
         *
         * @throws IllegalStateException if called from main thread.
         */
        public Display createDisplayWithDefaultDisplayMetricsAndWait(Context context,
                boolean isPrivate) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                throw new IllegalStateException("Should not call from main thread");
            }

            final Object waitObject = new Object();
            final DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int i) {
                    synchronized (waitObject) {
                        waitObject.notifyAll();
                    }
                }

                @Override
                public void onDisplayRemoved(int i) {
                }

                @Override
                public void onDisplayChanged(int i) {
                }
            };
            final DisplayManager displayManager = (DisplayManager) context.getSystemService(
                    Context.DISPLAY_SERVICE);
            displayManager.registerDisplayListener(listener, null);

            final DisplayMetrics metrics = new DisplayMetrics();
            displayManager.getDisplay(DEFAULT_DISPLAY).getRealMetrics(metrics);
            final Display display = createDisplay(context, metrics.widthPixels,
                    metrics.heightPixels, (int) metrics.xdpi, isPrivate);

            try {
                TestUtils.waitOn(waitObject,
                        () -> displayManager.getDisplay(display.getDisplayId()) != null,
                        DISPLAY_ADDED_TIMEOUT_MS,
                        String.format("wait for virtual display %d adding", display.getDisplayId()));
            } finally {
                displayManager.unregisterDisplayListener(listener);
            }
            return display;
        }
    }

    public static void touchDisplay(UiAutomation uiAutomation, int displayId,
            CharSequence activityTitle) {
        final Rect areaOfActivityWindowOnDisplay = new Rect();
        findWindowByTitleAndDisplay(uiAutomation, activityTitle, displayId)
                .getBoundsInScreen(areaOfActivityWindowOnDisplay);

        final int xOnScreen =
                areaOfActivityWindowOnDisplay.centerX();
        final int yOnScreen =
                areaOfActivityWindowOnDisplay.centerY();
        final long downEventTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = MotionEvent.obtain(downEventTime,
                downEventTime, MotionEvent.ACTION_DOWN, xOnScreen, yOnScreen, 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        downEvent.setDisplayId(displayId);
        uiAutomation.injectInputEvent(downEvent, true);

        final long upEventTime = downEventTime + 10;
        final MotionEvent upEvent = MotionEvent.obtain(downEventTime, upEventTime,
                MotionEvent.ACTION_UP, xOnScreen, yOnScreen, 0);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        upEvent.setDisplayId(displayId);
        uiAutomation.injectInputEvent(upEvent, true);
    }
}
