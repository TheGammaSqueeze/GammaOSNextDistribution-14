/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm.activity;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.Point;
import android.graphics.Rect;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerState.DisplayArea;
import android.view.Display;

import androidx.test.rule.ActivityTestRule;

import org.hamcrest.Matcher;

class AspectRatioTestsBase extends ActivityManagerTestBase {
    // The delta allowed when comparing two floats for equality. We consider them equal if they are
    // within two significant digits of each other.
    private static final float FLOAT_EQUALITY_DELTA = .01f;

    interface AssertAspectRatioCallback {
        void assertAspectRatio(float actual, int displayId, Point activitySize, Point displaySize);
    }

    void runAspectRatioTest(final ComponentName componentName, int windowingMode,
            final AssertAspectRatioCallback callback) {
        launchActivity(componentName, windowingMode);
        mWmState.computeState();

        final int displayId = mWmState.getDisplayByActivity(componentName);
        final WindowManagerState.Activity activity = mWmState.getActivity(componentName);
        final WindowManagerState.DisplayContent display = mWmState.getDisplay(displayId);

        final Rect bounds = activity.getAppBounds();
        final int shortSide = Math.min(bounds.width(), bounds.height());
        final int longSide = Math.max(bounds.width(), bounds.height());
        final float actualAspectRatio = (float) longSide / (float) shortSide;

        callback.assertAspectRatio(actualAspectRatio, display.mId,
                new Point(bounds.width(), bounds.height()),
                new Point(display.getAppRect().width(), display.getAppRect().height()));
    }

    void runAspectRatioTest(final ActivityTestRule activityRule,
            final AssertAspectRatioCallback callback) {
        final Activity activity = launchActivity(activityRule);
        mWmState.computeState();
        try {
            final Point displaySize = new Point();
            getDisplay(activity).getSize(displaySize);
            callback.assertAspectRatio(getActivityAspectRatio(activity),
                    getDisplay(activity).getDisplayId(),
                    getActivitySize(activity),
                    displaySize);
        } finally {
            finishActivity(activityRule);
        }

        // TODO(b/35810513): All this rotation stuff doesn't really work yet. Need to make sure
        // context is updated correctly here. Also, what does it mean to be holding a reference to
        // this activity if changing the orientation will cause a relaunch?
//        activity.setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);
//        waitForIdle();
//        callback.assertAspectRatio(getAspectRatio(activity));
//
//        activity.setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
//        waitForIdle();
//        callback.assertAspectRatio(getAspectRatio(activity));
    }

    float getDisplayAspectRatio(ComponentName componentName) {
        final DisplayArea tda = mWmState.getTaskDisplayArea(componentName);
        final Rect appRect = tda.getAppBounds();
        final int shortSide = Math.min(appRect.width(), appRect.height());
        final int longSide = Math.max(appRect.width(), appRect.height());
        return (float) longSide / (float) shortSide;
    }

    int getMinimalTaskSize(ComponentName componentName) {
        final int displayId = mWmState.getDisplayByActivity(componentName);
        final WindowManagerState.DisplayContent displayContent = mWmState.getDisplay(displayId);
        return displayContent.getMinSizeOfResizeableTaskDp();
    }

    private static float getActivityAspectRatio(Activity activity) {
        return getAspectRatio(getDisplay(activity));
    }

    private static Point getActivitySize(Activity activity) {
        final Point size = new Point();
        getDisplay(activity).getSize(size);
        return size;
    }

    private static Display getDisplay(Activity activity) {
        return activity.getWindow().peekDecorView().getDisplay();
    }

    private static float getAspectRatio(Display display) {
        final Point size = new Point();
        display.getSize(size);
        final float longSide = Math.max(size.x, size.y);
        final float shortSide = Math.min(size.x, size.y);
        return longSide / shortSide;
    }

    protected Activity launchActivity(final ActivityTestRule activityRule) {
        final Activity activity = activityRule.launchActivity(null);
        waitForIdle();
        return activity;
    }

    private void finishActivity(ActivityTestRule activityRule) {
        final Activity activity = activityRule.getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    static Matcher<Float> greaterThanOrEqualToInexact(float expected) {
        return greaterThanOrEqualTo(expected - FLOAT_EQUALITY_DELTA);
    }

    static Matcher<Float> lessThanOrEqualToInexact(float expected) {
        return lessThanOrEqualTo(expected + FLOAT_EQUALITY_DELTA);
    }
}
