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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.TestJournalProvider.TestJournalContainer;
import android.server.wm.TouchHelper;
import android.server.wm.backlegacyapp.Components;
import android.view.KeyEvent;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

/**
 * Integration test for back navigation legacy mode
 */
public class BackNavigationLegacyTest extends ActivityManagerTestBase {
    private Instrumentation mInstrumentation;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();

    }

    @Test
    public void receiveOnBackPressed() {
        TestJournalContainer.start();
        launchActivity(BACK_LEGACY);
        mWmState.assertActivityDisplayed(BACK_LEGACY);
        waitAndAssertActivityState(BACK_LEGACY, STATE_RESUMED, "Activity should be resumed");
        mInstrumentation.waitForIdleSync();
        mInstrumentation.getUiAutomation().syncInputTransactions();
        TouchHelper.injectKey(KeyEvent.KEYCODE_BACK, false /* longpress */, true /* sync */);
        waitForIdle();
        assertTrue("OnBackPressed should have been called",
                TestJournalContainer.get(BACK_LEGACY).extras.getBoolean(
                        Components.KEY_ON_BACK_PRESSED_CALLED));
        assertFalse("OnBackInvoked should not have been called",
                TestJournalContainer.get(BACK_LEGACY).extras.getBoolean(
                        Components.KEY_ON_BACK_INVOKED_CALLED));
    }
}
