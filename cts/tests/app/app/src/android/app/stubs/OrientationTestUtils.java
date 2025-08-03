/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.app.stubs;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Rect;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class OrientationTestUtils {
    /**
     * Change the activity's orientation to something different and then switch back. This is used
     * to trigger {@link Activity#onConfigurationChanged(android.content.res.Configuration)}.
     *
     * @param activity whose orientation will be changed and restored
     */
    public static void toggleOrientation(Activity activity) {
        final int[] orientations = getOrientations(activity);
        activity.setRequestedOrientation(orientations[1]);
        activity.setRequestedOrientation(orientations[0]);
    }

    /**
     * Switches the device's orientation from landscape to portrait or portrait to landscape.
     *
     * @param activity whose orientation will be changed
     * @return original orientation
     */
    public static void switchOrientation(final Activity activity) {
        final int[] orientations = getOrientations(activity);
        activity.setRequestedOrientation(orientations[1]);
    }

    /**
     * Returns window orientation and toggled orientation.
     * @param activity context to get the window info
     * @return The first element is original orientation and the second element is toggled
     *     orientation.
     */
    private static int[] getOrientations(final Activity activity) {
        // Check the display dimension to get the current device orientation.
        final Rect bounds = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
        final int originalOrientation = bounds.width() > bounds.height()
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        final int newOrientation = originalOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        return new int[] { originalOrientation, newOrientation };
    }

    /** Checks whether the window dimension is close to square. */
    public static boolean isCloseToSquareBounds(final Activity activity) {
        final Resources resources = activity.getResources();
        final float closeToSquareMaxAspectRatio;
        try {
            closeToSquareMaxAspectRatio = resources.getFloat(resources.getIdentifier(
                    "config_closeToSquareDisplayMaxAspectRatio", "dimen", "android"));
        } catch (Resources.NotFoundException e) {
            // Assume device is not close to square.
            return false;
        }
        final Rect bounds = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
        final int w = bounds.width();
        final int h = bounds.height();
        final float aspectRatio = Math.max(w, h) / (float) Math.min(w, h);
        return aspectRatio <= closeToSquareMaxAspectRatio;
    }

    /**
     * Observer used in stub activities to wait for some event.
     */
    public static class Observer {
        private static final int TIMEOUT_SEC = 3;
        private final AtomicReference<CountDownLatch> mLatch = new AtomicReference();

        /**
         * Starts observing event.
         * The returned CountDownLatch will get activated when onObserved is invoked after this
         * call. The method cannot be called multiple times unless reset() is invoked.
         * @return CountDownLatch will get activated when onObserved is invoked after this call.
         */
        public void startObserving() {
            final CountDownLatch latch = new CountDownLatch(1);
            assertTrue(mLatch.compareAndSet(null, latch));
        }

        /**
         * Waits until onObserved is invoked.
         */
        public void await() throws InterruptedException {
            try {
                assertTrue(mLatch.get().await(TIMEOUT_SEC, TimeUnit.SECONDS));
            } finally {
                mLatch.set(null);
            }
        }

        /**
         * Notifies an event is observed.
         * If this method is invoked after startObserving, the returned CountDownLatch will get
         * activated. Otherwise it does nothing.
         */
        public void onObserved() {
            final CountDownLatch latch = mLatch.get();
            if (latch != null) {
                latch.countDown();
            }
        }
    }
}
