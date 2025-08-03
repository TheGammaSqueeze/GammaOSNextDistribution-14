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

package android.car.cts.builtin.content;

import static android.car.cts.builtin.app.DisplayUtils.VirtualDisplaySession;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.car.builtin.content.ContextHelper;
import android.car.cts.builtin.activity.SingleUseActivity;
import android.car.cts.builtin.activity.VirtualDisplayIdTestActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.server.wm.ActivityManagerTestBase;
import android.view.Display;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ContextHelperTest extends ActivityManagerTestBase {
    private static final int ACTIVITY_FOCUS_TIMEOUT_MS = 10_000;

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();

    @Test
    public void testDefaultDisplayId() throws Exception {
        // execution and assertion
        assertThat(ContextHelper.getDisplayId(mContext)).isEqualTo(Display.DEFAULT_DISPLAY);
    }

    @Test
    public void testVirtualDisplayId() throws Exception {
        // check the assumption
        String requiredFeature = PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS;
        assumeTrue(mContext.getPackageManager().hasSystemFeature(requiredFeature));

        try (VirtualDisplaySession session = new VirtualDisplaySession()) {
            // setup: create a virtual display
            int createdVirtualDisplayId = session
                    .createDisplayWithDefaultDisplayMetricsAndWait(mContext, true).getDisplayId();

            assertNotEquals(createdVirtualDisplayId, Display.DEFAULT_DISPLAY);
            assertNotEquals(createdVirtualDisplayId, Display.INVALID_DISPLAY);

            // execution: launch VirtualDisplayIdActivity in the virtual display
            launchVirtualDisplayIdTestActivity(createdVirtualDisplayId, mContext.getPackageName(),
                    VirtualDisplayIdTestActivity.class.getName());

            // assertion
            assertEquals(createdVirtualDisplayId, VirtualDisplayIdTestActivity.getDisplayId());
        }
    }

    @Test
    @Ignore("b/248609709")
    public void testStartActivityAsUser() throws Exception {
        // setup
        Intent startIntent = new Intent(mContext, SingleUseActivity.class);
        ComponentName testActivityName = startIntent.getComponent();
        SingleUseActivity theInstance = null;

        try {
            // execute
            ContextHelper.startActivityAsUser(mContext, startIntent,
                    /* options= */ null, UserHandle.CURRENT);
            waitAndAssertTopResumedActivity(testActivityName, Display.DEFAULT_DISPLAY,
                    "Activity must be resumed");

            // assert
            theInstance = SingleUseActivity.getInstance();
            assertThat(theInstance).isNotNull();
        } finally {
            // teardown
            if (theInstance != null) {
                theInstance.finish();
            }
        }
    }

    private void launchVirtualDisplayIdTestActivity(int displayId,
            String pkgName, String activityClassName) {
        ComponentName testActivity = new ComponentName(pkgName, activityClassName);
        launchActivityOnDisplay(testActivity, displayId);
        waitForActivityFocused(ACTIVITY_FOCUS_TIMEOUT_MS, testActivity);
    }
}
