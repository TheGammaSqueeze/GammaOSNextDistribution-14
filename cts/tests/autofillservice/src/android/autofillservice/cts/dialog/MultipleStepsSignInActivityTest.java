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

package android.autofillservice.cts.dialog;

import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD_LABEL;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME_LABEL;
import static android.autofillservice.cts.testcore.Helper.assertHasFlags;
import static android.autofillservice.cts.testcore.Helper.assertMockImeStatus;
import static android.autofillservice.cts.testcore.Helper.enableFillDialogFeature;
import static android.service.autofill.FillRequest.FLAG_SUPPORTS_FILL_DIALOG;

import android.autofillservice.cts.activities.MultipleStepsSignInActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService;
import android.content.Intent;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;


/**
 * The tests for showing fill dialog for an Activity that only updates the content for login
 * steps, the app doesn't go to the new activty.
 */
public class MultipleStepsSignInActivityTest extends AutoFillServiceTestCase.ManualActivityLaunch {
    MultipleStepsSignInActivity mActivity;

    @After
    public void teardown() {
        if (mActivity != null) {
            mActivity.finish();
        }
        mActivity = null;
    }

    @Test
    @Ignore("bug b/275461003, test is failing on panther device")
    // TODO: re-enable test once root cause is found
    public void testShowFillDialog_contentChanged_shownFillDialog() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("Menu Username"))
                        .setDialogPresentation(createPresentation("Dialog Username"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_USERNAME);
        sReplier.addResponse(builder.build());

        // Start activity
        mActivity = startMultipleStepsSignInActivity();

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final InstrumentedAutoFillService.FillRequest fillRequest = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);

        // Click on username field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // Verify fill dialog shown
        mUiBot.assertFillDialogDatasets("Dialog Username");

        // Do nothing for fill dialog. Click outside to hide fill dialog and IME
        hideFillDialogAndIme(mActivity);

        // Set response for second page
        final CannedFillResponse.Builder builder2 = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Menu Password"))
                        .setDialogPresentation(createPresentation("Dialog Password"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder2.build());

        mActivity.nextPage();

        mUiBot.assertShownByRelativeId(ID_PASSWORD_LABEL);

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final InstrumentedAutoFillService.FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest2.flags, FLAG_SUPPORTS_FILL_DIALOG);

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Verify fill dialog shown
        mUiBot.assertFillDialogDatasets("Dialog Password");
    }

    @Test
    public void testShowFillDialog_backPrevPage_notShownFillDialog() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("Menu Username"))
                        .setDialogPresentation(createPresentation("Dialog Username"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_USERNAME);
        sReplier.addResponse(builder.build());

        // Start activity
        mActivity = startMultipleStepsSignInActivity();

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final InstrumentedAutoFillService.FillRequest fillRequest = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);

        // Set response for second page
        final CannedFillResponse.Builder builder2 = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Menu Password"))
                        .setDialogPresentation(createPresentation("Dialog Password"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder2.build());

        // Do nothing on the 1st page and go to next page
        mActivity.nextPage();

        mUiBot.assertShownByRelativeId(ID_PASSWORD_LABEL);

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final InstrumentedAutoFillService.FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest2.flags, FLAG_SUPPORTS_FILL_DIALOG);

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Verify fill dialog shown
        mUiBot.assertFillDialogDatasets("Dialog Password");

        // Go back previous page
        mActivity.prevPage();

        mUiBot.assertShownByRelativeId(ID_USERNAME_LABEL);

        // Verify there is no any fill request because response already exists
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field to trigger menu UI
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // Verify fill menu shown
        mUiBot.assertDatasets("Menu Username");
    }

    @Test
    public void testShowFillDialog_doNothingThenBackPrevPage_notShownFillDialog() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("Menu Username"))
                        .setDialogPresentation(createPresentation("Dialog Username"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_USERNAME);
        sReplier.addResponse(builder.build());

        // Start activity
        mActivity = startMultipleStepsSignInActivity();

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final InstrumentedAutoFillService.FillRequest fillRequest = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);

        // Set response for second page
        final CannedFillResponse.Builder builder2 = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Menu Password"))
                        .setDialogPresentation(createPresentation("Dialog Password"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder2.build());

        // Do nothing on the 1st page and go to next page
        mActivity.nextPage();

        mUiBot.assertShownByRelativeId(ID_PASSWORD_LABEL);

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final InstrumentedAutoFillService.FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest2.flags, FLAG_SUPPORTS_FILL_DIALOG);

        // Do nothing and go back previous page
        mActivity.prevPage();

        mUiBot.assertShownByRelativeId(ID_USERNAME_LABEL);

        // Verify there is no any fill request because response already exists
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field to trigger menu UI
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // Verify fill menu shown
        mUiBot.assertDatasets("Menu Username");
    }

    private void hideFillDialogAndIme(MultipleStepsSignInActivity activity) throws Exception {
        // Hide fill dialog via touch outside, the ime will appear.
        mUiBot.touchOutsideDialog();
        mUiBot.waitForIdleSync();

        assertMockImeStatus(activity, /* expectedImeShow= */ true);

        // Hide the IME before the next test.
        activity.hideSoftInput();

        assertMockImeStatus(activity, /* expectedImeShow= */ false);
    }

    private MultipleStepsSignInActivity startMultipleStepsSignInActivity() throws Exception {
        final Intent intent = new Intent(mContext, MultipleStepsSignInActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        mContext.startActivity(intent);
        mUiBot.assertShownByRelativeId(ID_USERNAME_LABEL);
        return MultipleStepsSignInActivity.getCurrentActivity();
    }
}
