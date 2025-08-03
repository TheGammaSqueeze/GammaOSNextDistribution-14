/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm.keyguard;

import static android.server.wm.UiDeviceUtils.pressEnterButton;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;
import android.server.wm.KeyEventActivity;
import android.server.wm.LockScreenSession;
import android.view.KeyEvent;

import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.WindowUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest KeyguardInputTests
 */
@Presubmit
public class KeyguardInputTests extends KeyguardTestBase {
    @Rule
    public ActivityTestRule<KeyEventActivity> mActivityRule =
            new ActivityTestRule<>(KeyEventActivity.class);
    private KeyEventActivity mActivity;

    @Before
    @Override
    public void setUp() {
        assumeTrue(supportsInsecureLock());

        mActivity = mActivityRule.getActivity();
        WindowUtil.waitForFocus(mActivity);
    }

    /**
     * Launch an activity on top of the keyguard, and inject ENTER key. Ensure that the ENTER key
     * goes to the activity.
     */
    @Test
    public void testReceiveKeysOnTopOfKeyguard() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final ComponentName KEY_EVENT_ACTIVITY = new ComponentName("android.server.wm.cts",
                "android.server.wm.KeyEventActivity");
        lockScreenSession.gotoKeyguard(KEY_EVENT_ACTIVITY);
        pressEnterButton();
        assertReceivedKey(KeyEvent.KEYCODE_ENTER);
        assertNoMoreEvents();
    }

    private void assertReceivedKey(int keycode) {
        KeyEvent event = mActivity.popKey();
        assertNotNull(event);
        assertEquals(keycode, event.getKeyCode());
        assertEquals(KeyEvent.ACTION_DOWN, event.getAction());
        event = mActivity.popKey();
        assertNotNull(event);
        assertEquals(keycode, event.getKeyCode());
        assertEquals(KeyEvent.ACTION_UP, event.getAction());
    }

    private void assertNoMoreEvents() {
        KeyEvent event = mActivity.popKey();
        assertNull(event);
    }
}
