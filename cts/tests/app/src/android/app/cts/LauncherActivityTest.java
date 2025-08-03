/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.app.stubs.LauncherActivityStub;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LauncherActivityTest {

    private static final String TAG = LauncherActivityTest.class.getSimpleName();

    @Rule
    public ActivityTestRule<LauncherActivityStub> mActivityRule =
            new ActivityTestRule<LauncherActivityStub>(LauncherActivityStub.class,
                    "android.app.stubs", Intent.FLAG_ACTIVITY_NEW_TASK,
                    /* initialTouchMode= */ false, /* launchActivity= */ true);

    private Instrumentation mInstrumentation;
    private LauncherActivityStub mActivity;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        Log.d(TAG, "setUp(): user=" + mActivity.getUser()
                + ", display=" + mActivity.getDisplayId());
    }

    @Test
    public void testLaunchActivity() throws Throwable {
        Log.d(TAG, "testLaunchActivity(): thread=" + Thread.currentThread());
        mInstrumentation.runOnMainSync(() -> {
            Log.d(TAG, "runOnMainSync(): thread=" + Thread.currentThread());
            // Test getTargetIntent. LaunchActivity#getTargetIntent() just returns a Intent()
            // instance with no content, so we use LaunchActivityStub#getSuperIntent() to get the
            // default Intent, and create a new intent for other tests.
            assertWithMessage("intent on super class")
                .that(mActivity.getSuperIntent()).isNotNull();

            // Test makeListItems. Make sure the size > 0. The sorted order is related to the sort
            // way, so it's mutable.
            assertWithMessage("list of items")
                .that(mActivity.makeListItems()).isNotEmpty();

            // There should be an activity(but with uncertain content) in position 0.
            assertWithMessage("intent on position 0")
                .that(mActivity.intentForPosition(0)).isNotNull();
        });
        mInstrumentation.waitForIdleSync();
        // Test onListItemClick
        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        sendKeys(KeyEvent.KEYCODE_DPAD_DOWN);
        sendKeys(KeyEvent.KEYCODE_DPAD_CENTER);
        assertWithMessage("items on list clicked").that(mActivity.isOnListItemClick).isTrue();
    }

    // copied from InstrumentationTestCase
    private void sendKeys(int... keys) {
        int count = keys.length;
        for (int i = 0; i < count; i++) {
            try {
                mInstrumentation.sendKeyDownUpSync(keys[i]);
            } catch (SecurityException e) {
                // Ignore security exceptions that are now thrown
                // when trying to send to another app, to retain
                // compatibility with existing tests.
            }
        }
        mInstrumentation.waitForIdleSync();
    }
}
