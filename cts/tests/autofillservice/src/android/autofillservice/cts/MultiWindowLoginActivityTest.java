/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.autofillservice.cts;

import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.ShellUtils.tap;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityTaskManager;
import android.autofillservice.cts.activities.MultiWindowEmptyActivity;
import android.autofillservice.cts.activities.MultiWindowLoginActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.graphics.Rect;
import android.platform.test.annotations.AppModeFull;
import android.server.wm.TestTaskOrganizer;
import android.view.View;

import androidx.test.filters.FlakyTest;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.concurrent.TimeoutException;

@AppModeFull(reason = "This test requires android.permission.MANAGE_ACTIVITY_TASKS")
public class MultiWindowLoginActivityTest
        extends AutoFillServiceTestCase.AutoActivityLaunch<MultiWindowLoginActivity> {

    private MultiWindowLoginActivity mActivity;
    private TestTaskOrganizer mTaskOrganizer;

    @Override
    protected AutofillActivityTestRule<MultiWindowLoginActivity> getActivityRule() {
        return new AutofillActivityTestRule<MultiWindowLoginActivity>(
                MultiWindowLoginActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
                mTaskOrganizer = new TestTaskOrganizer();
            }
        };
    }

    @Override
    protected void cleanAllActivities() {
        MultiWindowEmptyActivity.finishAndWaitDestroy();
    }

    @Override
    protected TestRule getMainTestRule() {
        return RuleChain.outerRule(new AdoptShellPermissionsRule()).around(getActivityRule());
    }

    @Before
    public void setup() {
        assumeTrue("Skipping test: no split multi-window support",
                ActivityTaskManager.supportsSplitScreenMultiWindow(mActivity));
    }

    @After
    public void tearDown() {
        mTaskOrganizer.unregisterOrganizerIfNeeded();
    }

    /**
     * Touch a view and expect autofill window change
     */
    protected void tapViewAndExpectWindowEvent(View view) throws TimeoutException {
        mUiBot.waitForWindowChange(() -> tap(view));
    }

    /**
     * Touch specific position on device display and expect autofill window change.
     */
    protected void tapPointAndExpectWindowEvent(int x, int y) {
        mUiBot.waitForWindowChange(() -> runShellCommand("input touchscreen tap %d %d", x, y));
    }

    protected String runAmStartActivity(String activity) {
        return runShellCommand("am start %s/%s", mPackageName, activity);
    }

    protected void amStartActivity(Class<? extends Activity> activity2) {
        // it doesn't work using startActivity(intent), have to go through shell command.
        runAmStartActivity(activity2.getName());
    }

    @FlakyTest(bugId = 267196677) // TODO: find out why this test fails
    @Test
    public void testSplitWindow() throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .setPresentation(createPresentation("The Dude"))
                .build());
        // Trigger auto-fill.
        mActivity.onUsername(View::requestFocus);
        sReplier.getNextFillRequest();
        mUiBot.assertDatasets("The Dude");

        amStartActivity(MultiWindowEmptyActivity.class);
        mUiBot.waitForIdleSync();
        MultiWindowEmptyActivity emptyActivity = MultiWindowEmptyActivity.getInstance();

        mTaskOrganizer.putTaskInSplitPrimary(mActivity.getTaskId());
        mUiBot.waitForIdleSync();
        mTaskOrganizer.putTaskInSplitSecondary(emptyActivity.getTaskId());
        mUiBot.waitForIdleSync();

        // Make sure both activities are showing
        mUiBot.assertShownByRelativeId(Helper.ID_USERNAME);  // MultiWindowLoginActivity
        mUiBot.assertShownByRelativeId(MultiWindowEmptyActivity.ID_EMPTY);

        // No dataset as MultiWindowLoginActivity loses window focus
        mUiBot.assertNoDatasets();
        // MultiWindowEmptyActivity will have window focus
        assertThat(emptyActivity.hasWindowFocus()).isTrue();
        // MultiWindowLoginActivity username field is still focused but window has no focus
        assertThat(mActivity.getUsername().hasFocus()).isTrue();
        assertThat(mActivity.hasWindowFocus()).isFalse();

        // Make MultiWindowLoginActivity to regain window focus and fill ui is expected to show
        tapViewAndExpectWindowEvent(mActivity.getUsername());
        mUiBot.assertNoDatasetsEver();
        assertThat(emptyActivity.hasWindowFocus()).isFalse();

        // Tap on MultiWindowEmptyActivity and fill ui is gone.
        Rect emptyActivityBounds = mTaskOrganizer.getSecondaryTaskBounds();
        // Because tap(View) will get wrong physical start position of view while in split screen
        // and make bot cannot tap on MultiWindowEmptyActivity, so use task bounds and tap its
        // center.
        tapPointAndExpectWindowEvent(emptyActivityBounds.centerX(), emptyActivityBounds.centerY());
        mUiBot.assertNoDatasetsEver();
        assertThat(emptyActivity.hasWindowFocus()).isTrue();
        // MultiWindowLoginActivity username field is still focused but window has no focus
        assertThat(mActivity.getUsername().hasFocus()).isTrue();
        assertThat(mActivity.hasWindowFocus()).isFalse();
    }
}
