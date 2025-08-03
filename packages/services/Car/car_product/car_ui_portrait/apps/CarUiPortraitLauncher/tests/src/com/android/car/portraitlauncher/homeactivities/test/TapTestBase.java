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

package com.android.car.portraitlauncher.homeactivities.test;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Rect;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.view.MotionEvent;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;

import java.io.IOException;

public class TapTestBase {
    private static final int TIMEOUT = 10000;
    private static final String TAG = TapTestBase.class.getSimpleName();
    private static final String PKG_LAUNCHER = "com.android.car.portraitlauncher";
    private static final String TEST_CONTAINER_RES_ID = "test_container";
    private static final String CONTROLBAR_RES_ID = "control_bar_area";
    private static final String GRIPBAR_RES_ID = "grip_bar";
    private static final String PKG_TEST = "com.android.car.portraitlauncher.homeactivities.test";
    static final int CLICK_DELTA = 50;
    final UiDevice mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    TestActivity mTestActivity;

    @After
    public void tearDown() {
        pressHome();
    }

    /**
     * Verifies if the click is make to given {@code activity}.
     */
    public void verifyClick(int x, int y, int offSiteX, int offsiteY, TestActivity activity) {
        int eventBefore = activity.getMotionEventLog().size();
        tapWithShell(x, y);
        int afterClick = activity.getMotionEventLog().size();
        assertThat(afterClick).isEqualTo(eventBefore + 1);
        MotionEvent motionEvent = activity.getMotionEvent();
        assertThat(x).isEqualTo((int) motionEvent.getX() + offSiteX);
        assertThat(y).isEqualTo((int) motionEvent.getY() + offsiteY);
    }

    private void tapWithShell(int x, int y) {
        runShellCmd("input tap"
                + " "
                + x
                + " "
                + y
        );
    }

    private void runShellCmd(String cmd) {
        Log.d(TAG, "run" + cmd);
        try {
            String result = mUiDevice.executeShellCommand(cmd).trim();
            Log.d(TAG, "Output of '" + cmd + "': '" + result + "'");
        } catch (IOException e) {
            Log.e(TAG, "Fail to run cmd" + cmd);
        }
    }

    /** Presses home button. */
    public void pressHome() {
        inputKeyEvent("KEYCODE_HOME");
    }

    /** Presses App grid button. */
    public void openAppGrid() {
        startAction("com.android.car.carlauncher.ACTION_APP_GRID");
    }

    /**
     * Returns the visible {@link Rect} of view associated with ID {@link TEST_CONTAINER_RES_ID}
     * from package {@link PKG_TEST}.
     */
    public Rect getTestContainerRect() {
        return getRect(PKG_TEST, TEST_CONTAINER_RES_ID);
    }

    /**
     * Returns the visible {@link Rect} of view associated with ID {@link TEST_CONTAINER_RES_ID}
     * from package {@link PKG_TEST}.
     */
    public Rect getControlBarRect() {
        return getRect(PKG_LAUNCHER, CONTROLBAR_RES_ID);
    }

    /**
     * Returns the visible {@link Rect} of view associated with ID {@link TEST_CONTAINER_RES_ID}
     * from package {@link PKG_TEST}.
     */
    public Rect getGripBarRect() {
        return getRect(PKG_LAUNCHER, GRIPBAR_RES_ID);
    }


    /**
     * Returns the visible {@link Rect} of view associated with given {@code id} from given package
     * {@link pkg}.
     */
    public Rect getRect(String pkg, String id) {
        BySelector testContainerBySelector = By.res(pkg, id);
        UiObject2 testObject = mUiDevice.wait(Until.findObject(testContainerBySelector), TIMEOUT);
        return testObject.getVisibleBounds();
    }

    private void inputKeyEvent(String keyCode) {
        runShellCmd("input keyevent " + keyCode);
    }

    private void startAction(String action) {
        runShellCmd("am start -a " + action);
    }
}
