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
 * limitations under the License.
 */

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.WindowUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Certain KeyEvents should never be delivered to apps. These keys are:
 *      KEYCODE_ASSIST
 *      KEYCODE_VOICE_ASSIST
 *      KEYCODE_HOME
 *      KEYCODE_VIDEO_APP_X
 *      KEYCODE_FEATURED_APP_X
 *      KEYCODE_DEMO_APP_X
 * This test launches an Activity and injects KeyEvents with the corresponding key codes.
 * The test will fail if any of these keys are received by the activity.
 * Note: The ASSIST tests were removed because they caused a side-effect of launching the
 * assistant asynchronously (as intended), which causes problems with tests which happen to
 * be running later and lose focus/visibility because of that extra window.
 *
 * Certain combinations of keys should be treated as shortcuts. Those are:
 *      KEYCODE_META_* + KEYCODE_ENTER --> KEYCODE_BACK
 *      KEYCODE_META_* + KEYCODE_DEL --> KEYCODE_HOME
 * For those combinations, we make sure that they are either delivered to the app
 * as the desired key (KEYCODE_BACK), or not delivered to the app (KEYCODE_HOME).
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class KeyEventInterceptTest {
    private InputEventInterceptTestActivity mActivity;
    private Instrumentation mInstrumentation;

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<InputEventInterceptTestActivity> mActivityRule =
            new ActivityTestRule<>(InputEventInterceptTestActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        WindowUtil.waitForFocus(mActivity);
    }

    @Test
    public void testKeyCodeHome() {
        testKey(KeyEvent.KEYCODE_HOME);
    }

    @Test
    public void testKeyCodesForLaunchingApplications() {
        testKey(KeyEvent.KEYCODE_VIDEO_APP_1);
        testKey(KeyEvent.KEYCODE_VIDEO_APP_2);
        testKey(KeyEvent.KEYCODE_VIDEO_APP_3);
        testKey(KeyEvent.KEYCODE_VIDEO_APP_4);
        testKey(KeyEvent.KEYCODE_VIDEO_APP_5);
        testKey(KeyEvent.KEYCODE_VIDEO_APP_6);
        testKey(KeyEvent.KEYCODE_VIDEO_APP_7);
        testKey(KeyEvent.KEYCODE_VIDEO_APP_8);
        testKey(KeyEvent.KEYCODE_FEATURED_APP_1);
        testKey(KeyEvent.KEYCODE_FEATURED_APP_2);
        testKey(KeyEvent.KEYCODE_FEATURED_APP_3);
        testKey(KeyEvent.KEYCODE_FEATURED_APP_4);
        testKey(KeyEvent.KEYCODE_DEMO_APP_1);
        testKey(KeyEvent.KEYCODE_DEMO_APP_2);
        testKey(KeyEvent.KEYCODE_DEMO_APP_3);
        testKey(KeyEvent.KEYCODE_DEMO_APP_4);
    }

    @Test
    public void testKeyCodeHomeShortcutLeftMeta() {
        testKeyCodeHomeShortcut(KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_ON);
    }

    @Test
    public void testKeyCodeHomeShortcutRightMeta() {
        testKeyCodeHomeShortcut(KeyEvent.META_META_RIGHT_ON | KeyEvent.META_META_ON);
    }

    private void testKeyCodeHomeShortcut(int metaState) {
        final long downTime = SystemClock.uptimeMillis();
        injectEvent(new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ENTER, 0, metaState));
        injectEvent(new KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_ENTER, 0, metaState));

        assertKeyNotReceived();
    }

    private void testKey(int keyCode) {
        sendKey(keyCode);
        assertKeyNotReceived();
    }

    private void sendKey(int keyCodeToSend) {
        long downTime = SystemClock.uptimeMillis();
        injectEvent(new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                keyCodeToSend, 0, 0));
        injectEvent(new KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP,
                keyCodeToSend, 0, 0));
    }

    private void injectEvent(KeyEvent event) {
        final UiAutomation automation = mInstrumentation.getUiAutomation();
        automation.injectInputEvent(event, true);
    }

    private void assertKeyNotReceived() {
        KeyEvent keyEvent = mActivity.mKeyEvents.poll();
        if (keyEvent == null) {
            return;
        }
        fail("Should not have received " + KeyEvent.keyCodeToString(keyEvent.getKeyCode()));
    }

    private void assertKeyReceived(int keyCode, int action) {
        KeyEvent keyEvent = mActivity.mKeyEvents.poll();
        if (keyEvent == null) {
            fail("Did not receive " + KeyEvent.keyCodeToString(keyCode) + ", queue is empty");
        }
        assertEquals(keyCode, keyEvent.getKeyCode());
        assertEquals(action, keyEvent.getAction());
    }
}
