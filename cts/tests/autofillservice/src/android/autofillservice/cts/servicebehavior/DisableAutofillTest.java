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

package android.autofillservice.cts.servicebehavior;

import static android.autofillservice.cts.testcore.Timeouts.ACTIVITY_RESURRECTION;
import static android.autofillservice.cts.testcore.Timeouts.CALLBACK_NOT_CALLED_TIMEOUT_MS;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.autofillservice.cts.activities.AbstractAutoFillActivity;
import android.autofillservice.cts.activities.PreSimpleSaveActivity;
import android.autofillservice.cts.activities.SimpleSaveActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.MyAutofillCallback;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.service.autofill.FillResponse;
import android.util.Log;
import android.support.test.uiautomator.UiDevice;

import androidx.test.InstrumentationRegistry;

import androidx.test.filters.FlakyTest;

import com.android.compatibility.common.util.RetryableException;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for the {@link android.service.autofill.FillResponse.Builder#disableAutofill(long)} API.
 */
public class DisableAutofillTest extends AutoFillServiceTestCase.ManualActivityLaunch {

    private static final String TAG = "DisableAutofillTest";
    private Instrumentation mInstrumentation;

    /**
     * Defines what to do after the activity being tested is launched.
     */
    enum PostLaunchAction {
        /**
         * Used when the service disables autofill in the fill response for this activty. As such:
         *
         * <ol>
         *   <li>There should be a fill request on {@code sReplier}.
         *   <li>The first UI focus should generate a
         *   {@link android.view.autofill.AutofillManager.AutofillCallback#EVENT_INPUT_UNAVAILABLE}
         *   event.
         *   <li>Subsequent UI focus should not trigger events.
         * </ol>
         */
        ASSERT_DISABLING,

        /**
         * Used when the service already disabled autofill prior to launching activty. As such:
         *
         * <ol>
         *   <li>There should be no fill request on {@code sReplier}.
         *   <li>There should be no callback calls when UI is focused
         * </ol>
         */
        ASSERT_DISABLED,

        /**
         * Used when autofill is enabled, so it tries to autofill the activity.
         */
        ASSERT_ENABLED_AND_AUTOFILL
    }

    /**
     * Launches and finishes {@link SimpleSaveActivity}, returning how long it took.
     */
    private long launchSimpleSaveActivity(PostLaunchAction action) throws Exception {
        Log.v(TAG, "launchSimpleSaveActivity(): " + action);
        sReplier.assertNoUnhandledFillRequests();

        if (action == PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL) {
            sReplier.addResponse(new CannedFillResponse.Builder()
                    .addDataset(new CannedDataset.Builder()
                            .setField(SimpleSaveActivity.ID_INPUT, "id")
                            .setField(SimpleSaveActivity.ID_PASSWORD, "pass")
                            .setPresentation(createPresentation("YO"))
                            .build())
                    .build());

        }

        final UiDevice device = UiDevice.getInstance(mInstrumentation);
        final long before = SystemClock.elapsedRealtime();
        final SimpleSaveActivity activity = startSimpleSaveActivity();
        final MyAutofillCallback callback = activity.registerCallback();

        try {
            // Trigger autofill
            activity.syncRunOnUiThread(() -> activity.mInput.requestFocus());

            if (action == PostLaunchAction.ASSERT_DISABLING) {
                callback.assertUiUnavailableEvent(activity.mInput);
                callback.assertNotCalled();
                sReplier.getNextFillRequest();
                device.waitForIdle();

                // Make sure other fields are not triggered.
                activity.syncRunOnUiThread(() -> activity.mPassword.requestFocus());
                callback.assertNotCalled();
            } else if (action == PostLaunchAction.ASSERT_DISABLED) {
                // Make sure forced requests are ignored as well.
                activity.getAutofillManager().requestAutofill(activity.mInput);
                callback.assertNotCalled();
            } else if (action == PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL) {
                callback.assertUiShownEvent(activity.mInput);
                sReplier.getNextFillRequest();
                final SimpleSaveActivity.FillExpectation autofillExpectation =
                        activity.expectAutoFill("id", "pass");
                mUiBot.selectDataset("YO");
                autofillExpectation.assertAutoFilled();
            }

            // Asserts isEnabled() status.
            assertAutofillEnabled(activity, action == PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL);
        } finally {
            mUiBot.waitForWindowChange(() -> activity.finish());
        }
        return SystemClock.elapsedRealtime() - before;
    }

    /**
     * Launches and finishes {@link PreSimpleSaveActivity}, returning how long it took.
     */
    private long launchPreSimpleSaveActivity(PostLaunchAction action) throws Exception {
        Log.v(TAG, "launchPreSimpleSaveActivity(): " + action);
        sReplier.assertNoUnhandledFillRequests();

        if (action == PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL) {
            sReplier.addResponse(new CannedFillResponse.Builder()
                    .addDataset(new CannedDataset.Builder()
                            .setField(PreSimpleSaveActivity.ID_PRE_INPUT, "yo")
                            .setPresentation(createPresentation("YO"))
                            .build())
                    .build());
        }

        final long before = SystemClock.elapsedRealtime();
        final PreSimpleSaveActivity activity = startPreSimpleSaveActivity();
        final MyAutofillCallback callback = activity.registerCallback();

        try {
            // Trigger autofill
            activity.syncRunOnUiThread(() -> activity.mPreInput.requestFocus());

            if (action == PostLaunchAction.ASSERT_DISABLING) {
                callback.assertUiUnavailableEvent(activity.mPreInput);
                sReplier.getNextFillRequest();
            } else if (action == PostLaunchAction.ASSERT_DISABLED) {
                activity.getAutofillManager().requestAutofill(activity.mPreInput);
                callback.assertNotCalled();
            } else if (action == PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL) {
                callback.assertUiShownEvent(activity.mPreInput);
                sReplier.getNextFillRequest();
                final PreSimpleSaveActivity.FillExpectation autofillExpectation =
                        activity.expectAutoFill("yo");
                mUiBot.selectDataset("YO");
                autofillExpectation.assertAutoFilled();
            }

            // Asserts isEnabled() status.
            assertAutofillEnabled(activity, action == PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL);
        } finally {
            mUiBot.waitForWindowChange(() -> activity.finish());
        }
        return SystemClock.elapsedRealtime() - before;
    }

    @After
    public void clearAutofillOptions() throws Exception {
        // Clear AutofillOptions.
        Helper.clearApplicationAutofillOptions(sContext);
    }

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Before
    public void resetAutofillOptions() throws Exception {
        // Reset AutofillOptions to avoid cts package was added to augmented autofill allowlist.
        Helper.resetApplicationAutofillOptions(sContext);
    }

    @FlakyTest(
            bugId = 292280852,
            detail = "Meet July-31-23 trunk stable no flaky SLO. Deflake asap")
    @Presubmit
    @Test
    public void testDisableApp() throws Exception {
        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(
                new CannedFillResponse.Builder().disableAutofill(Long.MAX_VALUE).build());

        // Trigger autofill for the first time.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLING);

        // Launch activity again.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLED);

        // Now try it using a different activity - should be disabled too.
        launchPreSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLED);
    }

    @Test
    @AppModeFull(reason = "testDisableApp() is enough")
    public void testDisableAppThenWaitToReenableIt() throws Exception {
        // Set service.
        enableService();

        // Need to wait the equivalent of launching 2 activities, plus some extra legging room
        final long duration = 2 * ACTIVITY_RESURRECTION.ms() + 500;

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder().disableAutofill(duration).build());

        // Trigger autofill for the first time.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLING);

        // Launch activity again.
        long passedTime = launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLED);

        // Wait for the timeout, then try again, autofilling it this time.
        sleep(passedTime, duration);
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL);

        // Also try it on another activity.
        launchPreSimpleSaveActivity(PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL);
    }

    @Ignore("b/275112488")
    @Test
    @AppModeFull(reason = "testDisableApp() is enough")
    public void testDisableAppThenResetServiceToReenableIt() throws Exception {
        enableService();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .disableAutofill(Long.MAX_VALUE).build());

        // Trigger autofill for the first time.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLING);
        // Launch activity again.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLED);

        // Then "reset" service to re-enable autofill.
        disableService();
        enableService();

        // Try again on activity that disabled it.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL);

        // Try again on other activity.
        launchPreSimpleSaveActivity(PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL);
    }

    @Ignore("b/270482520") // Failing on local devices
    @Test
    public void testDisableActivity() throws Exception {
        // Disable this test for Automotive until we know why it's failing.
        // bug: 270482520
        assumeTrue("Skip Automotive", !Helper.isAutomotive(sContext));

        // Set service.
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .disableAutofill(Long.MAX_VALUE)
                .setFillResponseFlags(FillResponse.FLAG_DISABLE_ACTIVITY_ONLY)
                .build());

        // Trigger autofill for the first time.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLING);

        // Launch activity again.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLED);

        // Now try it using a different activity - should work.
        launchPreSimpleSaveActivity(PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL);
    }

    @Ignore("b/270482520") // Find out why this test fails
    @Test
    @AppModeFull(reason = "testDisableActivity() is enough")
    public void testDisableActivityThenWaitToReenableIt() throws Exception {
        // Set service.
        enableService();

        // Need to wait the equivalent of launching 2 activities, plus some extra legging room
        final long duration = 2 * ACTIVITY_RESURRECTION.ms() + 500;

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .disableAutofill(duration)
                .setFillResponseFlags(FillResponse.FLAG_DISABLE_ACTIVITY_ONLY)
                .build());

        // Trigger autofill for the first time.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLING);

        // Launch activity again.
        long passedTime = launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLED);

        // Make sure other app is working.
        passedTime += launchPreSimpleSaveActivity(PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL);

        // Wait for the timeout, then try again, autofilling it this time.
        sleep(passedTime, duration);
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL);
    }

    @FlakyTest(bugId = 278560571)
    @Test
    @AppModeFull(reason = "testDisableActivity() is enough")
    public void testDisableActivityThenResetServiceToReenableIt() throws Exception {
        enableService();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .disableAutofill(Long.MAX_VALUE)
                .setFillResponseFlags(FillResponse.FLAG_DISABLE_ACTIVITY_ONLY)
                .build());

        // Trigger autofill for the first time.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLING);
        // Launch activity again.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_DISABLED);

        // Make sure other app is working.
        launchPreSimpleSaveActivity(PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL);

        // Then "reset" service to re-enable autofill.
        disableService();
        enableService();

        // Try again on activity that disabled it.
        launchSimpleSaveActivity(PostLaunchAction.ASSERT_ENABLED_AND_AUTOFILL);
    }

    private void assertAutofillEnabled(AbstractAutoFillActivity activity, boolean expected)
            throws Exception {
        ACTIVITY_RESURRECTION.run(
                "assertAutofillEnabled(" + activity.getComponentName().flattenToShortString() + ")",
                () -> {
                    return activity.getAutofillManager().isEnabled() == expected
                            ? Boolean.TRUE : null;
                });
    }

    private void sleep(long passedTime, long disableDuration) {
        final long napTime = disableDuration - passedTime + 500;
        if (napTime <= 0) {
            // Throw an exception so ACTIVITY_RESURRECTION is increased
            throw new RetryableException("took longer than expcted to launch activities: "
                            + "passedTime=" + passedTime + "ms, disableDuration=" + disableDuration
                            + ", ACTIVITY_RESURRECTION=" + ACTIVITY_RESURRECTION
                            + ", CALLBACK_NOT_CALLED_TIMEOUT_MS=" + CALLBACK_NOT_CALLED_TIMEOUT_MS);
        }
        Log.v(TAG, "Sleeping for " + napTime + "ms (duration=" + disableDuration + "ms, passedTime="
                + passedTime + ")");
        SystemClock.sleep(napTime);
    }
}
