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

import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.NULL_DATASET_ID;
import static android.autofillservice.cts.testcore.Helper.assertFillEventForDatasetSelected;
import static android.autofillservice.cts.testcore.Helper.assertFillEventForDatasetShown;
import static android.autofillservice.cts.testcore.Helper.assertFillEventForSaveShown;
import static android.autofillservice.cts.testcore.Helper.assertNoDeprecatedClientState;
import static android.autofillservice.cts.testcore.InstrumentedAutoFillServiceInlineEnabled.SERVICE_NAME;
import static android.service.autofill.FillEventHistory.Event.UI_TYPE_INLINE;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import android.autofillservice.cts.activities.LoginActivity;
import android.autofillservice.cts.commontests.FillEventHistoryCommonTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InlineUiBot;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.service.autofill.FillEventHistory;
import android.service.autofill.FillEventHistory.Event;

import androidx.test.filters.FlakyTest;
import androidx.test.uiautomator.UiObject2;

import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;

/**
 * Test that uses {@link LoginActivity} to test {@link FillEventHistory}.
 */
@Presubmit
@AppModeFull(reason = "Service-specific test")
public class InlineFillEventHistoryTest extends FillEventHistoryCommonTestCase {

    public InlineFillEventHistoryTest() {
        super(getInlineUiBot());
    }

    @Override
    protected boolean isInlineMode() {
        return true;
    }

    @Override
    protected void enableService() {
        Helper.enableAutofillService(SERVICE_NAME);
    }

    @Override
    public TestRule getMainTestRule() {
        return InlineUiBot.annotateRule(super.getMainTestRule());
    }

    @FlakyTest(
            bugId = 292152886,
            detail = "Meet July-31-23 trunk stable no flaky SLO. Deflake asap")
    @Override
    @Test
    public void testAuthenticationSelected() throws Exception {
        super.testAuthenticationSelected();
    }

    @Test
    public void testOneDatasetAndSave() throws Exception {
        enableService();

        // Set expectations.
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_GENERIC, ID_USERNAME)
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "id")
                        .setField(ID_PASSWORD, "pass")
                        .setPresentation(createPresentation("Dataset"))
                        .setInlinePresentation(createInlinePresentation("Dataset"))
                        .build())
                .build());

        // Trigger auto-fill and IME.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();

        // Set expected
        mActivity.expectAutoFill("id", "pass");

        // Suggestion strip was shown.
        mUiBot.assertDatasets("Dataset");
        mUiBot.selectDataset("Dataset");

        // Verify auto filled
        mActivity.assertAutoFilled();

        // Change username and password
        mActivity.setTextAndWaitTextChange("ID", "PASS");

        // Trigger save UI.
        mActivity.tapSave();
        mUiBot.waitForIdle();

        // Confirm the save UI shown
        final UiObject2 saveUi = mUiBot.assertUpdateShowing(SAVE_DATA_TYPE_GENERIC);

        // Save it...
        mUiBot.saveForAutofill(saveUi, true);
        mUiBot.waitForIdle();
        sReplier.getNextSaveRequest();

        // Verify events history
        final FillEventHistory selection = InstrumentedAutoFillService.getFillEventHistory(4);
        assertNoDeprecatedClientState(selection);
        final List<Event> events = selection.getEvents();
        assertFillEventForDatasetShown(events.get(0), UI_TYPE_INLINE);
        assertFillEventForDatasetSelected(events.get(1), NULL_DATASET_ID, UI_TYPE_INLINE);
        assertFillEventForSaveShown(events.get(2), NULL_DATASET_ID);
        assertFillEventForDatasetShown(events.get(3), UI_TYPE_INLINE);
    }
}
