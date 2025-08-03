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
package android.server.wm.backnavigation;

import static android.server.wm.UiDeviceUtils.wakeUpAndUnlock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Dialog;
import android.app.Instrumentation;
import android.platform.test.annotations.Presubmit;
import android.server.wm.TouchHelper;
import android.view.KeyEvent;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Integration test for back navigation
 */
@Presubmit
public class BackNavigationTests {
    @Rule
    public final ActivityScenarioRule<BackNavigationActivity> mScenarioRule =
            new ActivityScenarioRule<>(BackNavigationActivity.class);
    private ActivityScenario<BackNavigationActivity> mScenario;
    private Instrumentation mInstrumentation;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        wakeUpAndUnlock(mInstrumentation.getContext());
        mScenario = mScenarioRule.getScenario();
    }

    @Test
    public void registerCallback_initialized() {
        CountDownLatch latch = registerBackCallback();
        mScenario.moveToState(Lifecycle.State.RESUMED);
        invokeBackAndAssertCallbackIsCalled(latch);
    }

    @Test
    public void registerCallback_created() {
        mScenario.moveToState(Lifecycle.State.CREATED);
        CountDownLatch latch = registerBackCallback();
        mScenario.moveToState(Lifecycle.State.RESUMED);
        invokeBackAndAssertCallbackIsCalled(latch);
    }

    @Test
    public void registerCallback_resumed() {
        mScenario.moveToState(Lifecycle.State.RESUMED);
        CountDownLatch latch = registerBackCallback();
        invokeBackAndAssertCallbackIsCalled(latch);
    }

    @Test
    public void registerCallback_dialog() {
        CountDownLatch backInvokedLatch = new CountDownLatch(1);
        mScenario.onActivity(activity -> {
            Dialog dialog = new Dialog(activity, 0);
            dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                        backInvokedLatch.countDown();
                    });
            dialog.show();
        });
        invokeBackAndAssertCallbackIsCalled(backInvokedLatch);
    }

    @Test
    public void onBackPressedNotCalled() {
        mScenario.moveToState(Lifecycle.State.RESUMED);
        CountDownLatch latch = registerBackCallback();
        invokeBackAndAssertCallbackIsCalled(latch);
        mScenario.onActivity((activity) ->
                assertFalse("Activity.onBackPressed should not be called",
                        activity.mOnBackPressedCalled));
    }

    @Test
    public void registerCallback_relaunch() {
        mScenario.moveToState(Lifecycle.State.RESUMED);
        CountDownLatch latch1 = registerBackCallback();

        ActivityScenario<BackNavigationActivity> newScenario = mScenario.recreate();
        newScenario.moveToState(Lifecycle.State.RESUMED);
        CountDownLatch latch2 = registerBackCallback(newScenario, true);

        invokeBackAndAssertCallbackIsCalled(latch2);
        invokeBackAndAssertCallback(latch1, false);
    }

    private void invokeBackAndAssertCallbackIsCalled(CountDownLatch latch) {
        invokeBackAndAssertCallback(latch, true);
    }

    private void invokeBackAndAssertCallback(CountDownLatch latch, boolean isCalled) {
        try {
            // Make sure the application is idle and input windows is up-to-date.
            mInstrumentation.waitForIdleSync();
            mInstrumentation.getUiAutomation().syncInputTransactions();
            TouchHelper.injectKey(KeyEvent.KEYCODE_BACK, false /* longpress */, true /* sync */);
            if (isCalled) {
                assertTrue("OnBackInvokedCallback.onBackInvoked() was not called",
                        latch.await(500, TimeUnit.MILLISECONDS));
            } else {
                assertFalse("OnBackInvokedCallback.onBackInvoked() was called",
                        latch.await(500, TimeUnit.MILLISECONDS));
            }
        } catch (InterruptedException ex) {
            fail("Application died before invoking the callback.\n" + ex.getMessage());
        }
    }

    private CountDownLatch registerBackCallback() {
        return registerBackCallback(mScenario, false);
    }

    private CountDownLatch registerBackCallback(ActivityScenario<?> scenario,
            boolean unregisterAfterCalled) {
        CountDownLatch backInvokedLatch = new CountDownLatch(1);
        CountDownLatch backRegisteredLatch = new CountDownLatch(1);
        final OnBackInvokedCallback callback = new OnBackInvokedCallback() {
            @Override
            public void onBackInvoked() {
                backInvokedLatch.countDown();
                if (unregisterAfterCalled) {
                    scenario.onActivity(activity -> activity.getOnBackInvokedDispatcher()
                            .unregisterOnBackInvokedCallback(this));
                }
            }
        };

        scenario.onActivity(activity -> {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, callback);
            backRegisteredLatch.countDown();
        });
        try {
            if (!backRegisteredLatch.await(100, TimeUnit.MILLISECONDS)) {
                fail("Back callback was not registered on the Activity thread. This might be "
                        + "an error with the test itself.");
            }
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
        return backInvokedLatch;
    }
}
