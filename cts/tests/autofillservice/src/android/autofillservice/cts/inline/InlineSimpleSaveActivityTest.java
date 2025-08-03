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

package android.autofillservice.cts.inline;

import static android.autofillservice.cts.activities.SimpleSaveActivity.ID_COMMIT;
import static android.autofillservice.cts.activities.SimpleSaveActivity.ID_INPUT;
import static android.autofillservice.cts.activities.SimpleSaveActivity.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.assertTextAndValue;
import static android.autofillservice.cts.testcore.Helper.findNodeByResourceId;
import static android.autofillservice.cts.testcore.InstrumentedAutoFillServiceInlineEnabled.SERVICE_NAME;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import android.autofillservice.cts.activities.SimpleSaveActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InlineUiBot;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import org.junit.Test;
import org.junit.rules.TestRule;

@Presubmit
public class InlineSimpleSaveActivityTest
        extends AutoFillServiceTestCase.AutoActivityLaunch<SimpleSaveActivity> {

    private static final String TAG = "InlineSimpleSaveActivityTest";
    protected SimpleSaveActivity mActivity;

    public InlineSimpleSaveActivityTest() {
        super(getInlineUiBot());
    }

    @Override
    protected void enableService() {
        Helper.enableAutofillService(SERVICE_NAME);
    }

    @NonNull
    @Override
    protected AutofillActivityTestRule<SimpleSaveActivity> getActivityRule() {
        return new AutofillActivityTestRule<SimpleSaveActivity>(SimpleSaveActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Override
    public TestRule getMainTestRule() {
        return InlineUiBot.annotateRule(super.getMainTestRule());
    }

    @Test
    public void testAutofillSave() throws Exception {
        // Set service.
        enableService();

         // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT)
                .build());

        // Trigger auto-fill and IME.
        mUiBot.selectByRelativeId(ID_INPUT);
        mUiBot.waitForIdle();

        sReplier.getNextFillRequest();

        // Suggestion strip was never shown.
        mUiBot.assertNoDatasetsEver();

        // Change input
        mActivity.setTextAndWaitTextChange(/* input= */ "ID", /* password= */  null);


        // Trigger save UI.
        mUiBot.selectByRelativeId(ID_COMMIT);
        mUiBot.waitForIdle();

        // Confirm the save UI shown
        final UiObject2 saveUi = mUiBot.assertSaveShowing(SAVE_DATA_TYPE_GENERIC);

        // Save it...
        mUiBot.saveForAutofill(saveUi, true);

        // ... and assert results
        final InstrumentedAutoFillService.SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_INPUT), "ID");
    }

    @Test
    public void testAutofill_oneDatasetAndSave() throws Exception {
        // Set service.
        enableService();

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_INPUT, ID_PASSWORD)
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_INPUT, "id")
                        .setField(ID_PASSWORD, "pass")
                        .setPresentation(createPresentation("YO"))
                        .setInlinePresentation(createInlinePresentation("YO"))
                        .build());
        sReplier.addResponse(builder.build());
        mActivity.expectAutoFill("id", "pass");

        // Trigger auto-fill and IME.
        mUiBot.selectByRelativeId(ID_INPUT);
        mUiBot.waitForIdle();

        sReplier.getNextFillRequest();

        // Confirm one suggestion
        mUiBot.assertDatasets("YO");

        // Select suggestion
        final SimpleSaveActivity.FillExpectation fillExpectation =
                mActivity.expectAutoFill("id", "pass");
        mUiBot.selectDataset("YO");
        mUiBot.waitForIdle();

        // Check the results.
        fillExpectation.assertAutoFilled();

        // Change input
        mActivity.setTextAndWaitTextChange(/* input= */ "ID", /* password= */  null);

        // Trigger save UI.
        mUiBot.selectByRelativeId(ID_COMMIT);
        mUiBot.waitForIdle();

        // Confirm the save UI shown
        final UiObject2 saveUi = mUiBot.assertUpdateShowing(SAVE_DATA_TYPE_GENERIC);

        // Save it...
        mUiBot.saveForAutofill(saveUi, true);

        // ... and assert results
        final InstrumentedAutoFillService.SaveRequest saveRequest = sReplier.getNextSaveRequest();
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_INPUT), "ID");
        assertTextAndValue(findNodeByResourceId(saveRequest.structure, ID_PASSWORD), "pass");
    }
}
