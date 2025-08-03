/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.server.wm.backnavigation;

import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.backlegacyapp.Components.BACK_LEGACY;
import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.Condition;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.server.wm.backlegacyapp.Components;

import org.junit.Before;
import org.junit.Test;

/**
 * Integration test for back navigation legacy mode
 *
 *  <p>Build/Install/Run:
 *      atest CtsWindowManagerDeviceBackNavigation:BackNavigationLegacyGestureTest
 */
@Presubmit
public class BackNavigationLegacyGestureTest extends ActivityManagerTestBase {
    private static final int ACTIVITY_FOCUS_TIMEOUT_MS = 3000;
    @Before
    public void setup() throws Exception {
        super.setUp();
        enableAndAssumeGestureNavigationMode();
    }

    @Test
    public void receiveOnBackPressed() {
        TestJournalContainer.start();
        launchActivity(BACK_LEGACY);
        mWmState.assertActivityDisplayed(BACK_LEGACY);
        waitAndAssertActivityState(BACK_LEGACY, STATE_RESUMED, "Activity should be resumed");
        waitForActivityFocused(ACTIVITY_FOCUS_TIMEOUT_MS, BACK_LEGACY);
        triggerBackEventByGesture(DEFAULT_DISPLAY);
        final Bundle resultExtras = Condition.waitForResult(
                new Condition<Bundle>("wait for onBackPressed")
                        .setResultSupplier(() -> TestJournalContainer.get(
                                BACK_LEGACY).extras)
                        .setResultValidator(extras ->
                                extras.containsKey(Components.KEY_ON_BACK_PRESSED_CALLED)));
        if (resultExtras == null
                || !resultExtras.getBoolean(Components.KEY_ON_BACK_PRESSED_CALLED)) {
            fail("OnBackPressed should have been called");
        }
        assertFalse("OnBackInvoked should not have been called",
                TestJournalContainer.get(BACK_LEGACY).extras.getBoolean(
                        Components.KEY_ON_BACK_INVOKED_CALLED));
    }
}
