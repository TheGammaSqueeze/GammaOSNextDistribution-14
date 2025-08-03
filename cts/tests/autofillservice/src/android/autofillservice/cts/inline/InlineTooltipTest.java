/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static android.autofillservice.cts.testcore.InstrumentedAutoFillServiceInlineEnabled.SERVICE_NAME;

import android.autofillservice.cts.commontests.AbstractLoginActivityTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InlineUiBot;

import org.junit.Test;
import org.junit.rules.TestRule;

/**
 * Tests inline suggestions tooltip behaviors.
 */
public class InlineTooltipTest extends AbstractLoginActivityTestCase {

    InlineUiBot mInlineUiBot;

    public InlineTooltipTest() {
        super(getInlineUiBot());
        mInlineUiBot = getInlineUiBot();
    }

    @Override
    protected boolean isInlineMode() {
        return true;
    }

    @Override
    public TestRule getMainTestRule() {
        return InlineUiBot.annotateRule(super.getMainTestRule());
    }

    @Override
    protected void enableService() {
        Helper.enableAutofillService(SERVICE_NAME);
    }

    @Test
    public void testShowTooltip() throws Exception {
        // Set service.
        enableService();

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .setInlineTooltipPresentation(
                                Helper.createInlineTooltipPresentation("The Username Tooltip"))
                        .build());

        sReplier.addResponse(builder.build());

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();

        mUiBot.assertDatasets("The Username");
        mInlineUiBot.assertTooltipShowing("The Username Tooltip");
    }

    @Test
    public void testShowTooltipWithTwoFields() throws Exception {
        // Set service.
        enableService();

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .setInlineTooltipPresentation(
                                Helper.createInlineTooltipPresentation("The Username Tooltip"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Password"))
                        .setInlinePresentation(createInlinePresentation("The Password"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "lollipop")
                        .setPresentation(createPresentation("The Password2"))
                        .setInlinePresentation(createInlinePresentation("The Password2"))
                        .setInlineTooltipPresentation(
                                Helper.createInlineTooltipPresentation("The Password Tooltip"))
                        .build());

        sReplier.addResponse(builder.build());

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();

        mUiBot.assertDatasets("The Username");
        mInlineUiBot.assertTooltipShowing("The Username Tooltip");

        // Switch focus to password
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("The Password", "The Password2");
        mInlineUiBot.assertTooltipShowing("The Password Tooltip");

        // Switch focus back to username
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("The Username");
        mInlineUiBot.assertTooltipShowing("The Username Tooltip");

        mActivity.expectAutoFill("dude");
        mUiBot.selectDataset("The Username");
        mUiBot.waitForIdleSync();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    public void testShowTooltipWithSecondDataset() throws Exception {
        // Set service.
        enableService();

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "sweet")
                        .setPresentation(createPresentation("The Username2"))
                        .setInlinePresentation(createInlinePresentation("The Username2"))
                        .setInlineTooltipPresentation(
                                Helper.createInlineTooltipPresentation("The Username Tooltip"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "candy")
                        .setPresentation(createPresentation("The Username3"))
                        .setInlinePresentation(createInlinePresentation("The Username3"))
                        .build());

        sReplier.addResponse(builder.build());

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();

        mUiBot.assertDatasets("The Username", "The Username2", "The Username3");
        mInlineUiBot.assertTooltipShowing("The Username Tooltip");
    }
}
