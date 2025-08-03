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

package android.server.wm.activity;

import static android.content.res.Configuration.UI_MODE_TYPE_DESK;
import static android.content.res.Configuration.UI_MODE_TYPE_MASK;
import static android.content.res.Configuration.UI_MODE_TYPE_NORMAL;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.deskresources.Components.DESK_RESOURCES_ACTIVITY;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.RotationSession;
import android.view.Surface;

import org.junit.Test;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceOther:DockConfigChangeTests
 */
@Presubmit
public class DockConfigChangeTests extends ActivityManagerTestBase {

    @Test
    public void testDeskMode_noConfigChange() {
        // Test only applies to behavior when the config_skipActivityRelaunchWhenDocking flag is
        // enabled.
        assumeTrue(getConfigSkipRelaunchOnDock());

        RotationSession rotationSession = createManagedRotationSession();

        launchActivity(TEST_ACTIVITY);
        waitAndAssertResumedActivity(TEST_ACTIVITY, "Activity must be resumed");

        // Set rotation to the same rotation as the device would be rotated to after docking. This
        // prevents an extraneous config change from the device rotating when docked/undocked.
        rotateToDockRotation(rotationSession);
        waitAndAssertResumedActivity(TEST_ACTIVITY, "Activity must be resumed");
        separateTestJournal();

        final DockTestSession dockTestSession = mObjectTracker.manage(new DockTestSession());

        // Dock the device.
        dockTestSession.dock();
        dockTestSession.waitForDeskUiMode(TEST_ACTIVITY);

        // The activity receives a configuration change instead of relaunching.
        assertRelaunchOrConfigChanged(TEST_ACTIVITY, 0 /* numRelaunch */, 1 /* numConfigChange */);

        // Undock the device.
        separateTestJournal();
        dockTestSession.undock();
        dockTestSession.waitForNormalUiMode(TEST_ACTIVITY);

        // The activity receives another configuration change.
        assertRelaunchOrConfigChanged(TEST_ACTIVITY, 0 /* numRelaunch */, 1 /* numConfigChange */);
    }

    @Test
    public void testDeskMode_hasDeskResources_relaunches() {
        // Test only applies to behavior when the config_skipActivityRelaunchWhenDocking flag is
        // enabled.
        assumeTrue(getConfigSkipRelaunchOnDock());

        RotationSession rotationSession = createManagedRotationSession();

        launchActivity(DESK_RESOURCES_ACTIVITY);
        waitAndAssertResumedActivity(DESK_RESOURCES_ACTIVITY, "Activity must be resumed");

        // Set rotation to the same rotation as the device would be rotated to after docking. This
        // prevents an extraneous config change from the device rotating when docked/undocked.
        rotateToDockRotation(rotationSession);
        waitAndAssertResumedActivity(DESK_RESOURCES_ACTIVITY, "Activity must be resumed");
        separateTestJournal();

        final DockTestSession dockTestSession = mObjectTracker.manage(new DockTestSession());

        // Dock the device.
        dockTestSession.dock();
        dockTestSession.waitForDeskUiMode(DESK_RESOURCES_ACTIVITY);

        // The activity is relaunched since the app has -desk resources.
        assertRelaunchOrConfigChanged(DESK_RESOURCES_ACTIVITY, 1 /* numRelaunch */,
                0 /* numConfigChange */);

        // Undock the device.
        separateTestJournal();
        dockTestSession.undock();
        dockTestSession.waitForNormalUiMode(DESK_RESOURCES_ACTIVITY);

        // The activity is relaunched again.
        assertRelaunchOrConfigChanged(DESK_RESOURCES_ACTIVITY, 1 /* numRelaunch */,
                0 /* numConfigChange */);
    }

    boolean getConfigSkipRelaunchOnDock() {
        return mContext.getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_skipActivityRelaunchWhenDocking",
                        "bool", "android"));
    }

    /**
     * Rotates the device to the same rotation as it would rotate to when docked.
     *
     * Dock rotation is read from config_deskDockRotation.
     */
    void rotateToDockRotation(RotationSession rotationSession) {
        int rotation = rotationDegreesToConst(mContext.getResources().getInteger(
                Resources.getSystem().getIdentifier("config_deskDockRotation",
                        "integer", "android")));
        if (rotation == -1) {
            // -1 could come from the const itself, which means no rotation on dock, or from
            // rotationDegreesToConst, which means we got an unexpected value from the resource.
            return;
        }
        rotationSession.set(rotation);
    }

    /**
     * Converts from a rotation in degrees to a {@link Surface.Rotation} constant.
     *
     * Returns -1 if a value that doesn't match a {@link Surface.Rotation} constant is provided.
     */
    private int rotationDegreesToConst(int rotationDegrees) {
        switch (rotationDegrees) {
            case 0:
                return Surface.ROTATION_0;
            case 90:
                return Surface.ROTATION_90;
            case 180:
                return Surface.ROTATION_180;
            case 270:
                return Surface.ROTATION_270;
        }
        return -1;
    }


    private class DockTestSession implements AutoCloseable {
        private void dock() {
            runShellCommand("dumpsys DockObserver set state "
                    + Intent.EXTRA_DOCK_STATE_HE_DESK);
        }

        private void undock() {
            runShellCommand("dumpsys DockObserver set state "
                    + Intent.EXTRA_DOCK_STATE_UNDOCKED);
        }

        private void waitForDeskUiMode(ComponentName activity) {
            waitForUiMode(activity, UI_MODE_TYPE_DESK);
        }

        private void waitForNormalUiMode(ComponentName activity) {
            waitForUiMode(activity, UI_MODE_TYPE_NORMAL);
        }

        /**
         * Waits until the activity has received the expected uiMode in a configuration change.
         */
        private void waitForUiMode(ComponentName activity, int expectedUiMode) {
            assertTrue(mWmState.waitFor(state -> {
                int actualUiMode = state.getActivity(activity).getUiMode();
                return (actualUiMode & UI_MODE_TYPE_MASK) == expectedUiMode;
            }, "Didn't enter expected uiMode in time: " + expectedUiMode));
        }

        @Override
        public void close() throws Exception {
            runShellCommand("dumpsys DockObserver reset");
        }
    }
}
