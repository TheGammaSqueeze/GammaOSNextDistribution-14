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

package android.autofillservice.cts;

import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.getContext;
import static android.autofillservice.cts.testcore.InstrumentedAutoFillService.FillRequest;
import static android.content.IntentSender.SendIntentException;
import static android.service.autofill.AutofillService.EXTRA_FILL_RESPONSE;

import static org.testng.Assert.assertThrows;

import android.autofillservice.cts.activities.LoginActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.content.Intent;
import android.os.SystemClock;
import android.service.autofill.FillResponse;
import android.util.Log;

import androidx.test.filters.FlakyTest;

import org.junit.Test;

/**
 * Test accepting delayed fill responses from autofill service.
 */
public class DelayFillTest extends AutoFillServiceTestCase.AutoActivityLaunch<LoginActivity> {
    private static final String TAG = "DelayFillTest";

    private LoginActivity mActivity;

    @Override
    protected AutofillActivityTestRule<LoginActivity> getActivityRule() {
        return new AutofillActivityTestRule<LoginActivity>(LoginActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @FlakyTest(bugId = 271521636)
    @Test
    public void testDelayedFill() throws Exception {
        // Set service.
        enableService();

        // Add placeholder response
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .addDataset(new CannedFillResponse.CannedDataset.Builder(
                                createPresentation("placeholder"))
                                .setField(ID_USERNAME, "filled").build())
                        .setFillResponseFlags(FillResponse.FLAG_DELAY_FILL)
                        .build());

        // Trigger autofill on username
        mUiBot.selectByRelativeId(ID_USERNAME);

        // Wait for fill request to be processed
        FillRequest fillRequest = sReplier.getNextFillRequest();

        // Wait until dataset is shown
        mUiBot.assertDatasets("placeholder");

        // Create the actual response
        FillResponse actualResponse = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("dataset"))
                                .setField(ID_USERNAME, "filled")
                                .build())
                .build()
                .asFillResponse(fillRequest.contexts,
                        (id) -> Helper.findNodeByResourceId(fillRequest.contexts, id));
        Intent intent = new Intent()
                .putExtra(EXTRA_FILL_RESPONSE, actualResponse);

        // Send delayed fill response
        fillRequest.delayFillIntentSender.sendIntent(getContext(), 0, intent, null, null, null);

        // Wait for fill response to be processed
        sleep();

        // Dataset of second response should be shown
        mUiBot.assertDatasets("dataset");
    }

    @Test
    public void testServiceDidNotSetDelayFillFlag() throws Exception {
        // Set service.
        enableService();

        // Add response
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .addDataset(new CannedFillResponse.CannedDataset.Builder(
                                createPresentation("placeholder"))
                                        .setField(ID_USERNAME, "filled")
                                        .build())
                        .build());

        // Trigger autofill on username
        mUiBot.selectByRelativeId(ID_USERNAME);

        // Wait for fill request to be processed
        FillRequest fillRequest = sReplier.getNextFillRequest();

        // Wait until dataset is shown
        mUiBot.assertDatasets("placeholder");

        // Create the actual response
        FillResponse actualResponse = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("dataset"))
                                .setField(ID_USERNAME, "filled")
                                .build())
                .build()
                .asFillResponse(fillRequest.contexts,
                        (id) -> Helper.findNodeByResourceId(fillRequest.contexts, id));
        Intent intent = new Intent()
                .putExtra(EXTRA_FILL_RESPONSE, actualResponse);

        // Send delayed fill response
        fillRequest.delayFillIntentSender.sendIntent(getContext(), 0, intent, null, null, null);

        // Wait for fill response to be processed
        sleep();

        // Dataset of placeholder response should still be shown
        mUiBot.assertDatasets("placeholder");
    }

    @FlakyTest(bugId = 271521636)
    @Test
    public void testPreventSendingDelayedFillIntentTwice() throws Exception {
        // Set service.
        enableService();

        // Add placeholder response
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .addDataset(new CannedFillResponse.CannedDataset.Builder(
                                createPresentation("placeholder"))
                                .setField(ID_USERNAME, "filled").build())
                        .setFillResponseFlags(FillResponse.FLAG_DELAY_FILL)
                        .build());

        // Trigger autofill on username
        mUiBot.selectByRelativeId(ID_USERNAME);

        // Wait for fill request to be processed
        FillRequest fillRequest = sReplier.getNextFillRequest();

        // Wait until dataset is shown
        mUiBot.assertDatasets("placeholder");

        // Create the actual response
        FillResponse actualResponse = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("dataset"))
                        .setField(ID_USERNAME, "filled")
                        .build())
                .build()
                .asFillResponse(fillRequest.contexts,
                        (id) -> Helper.findNodeByResourceId(fillRequest.contexts, id));
        Intent intent = new Intent()
                .putExtra(EXTRA_FILL_RESPONSE, actualResponse);

        // Send delayed fill response
        fillRequest.delayFillIntentSender.sendIntent(getContext(), 0, intent, null, null, null);

        // Wait for fill response to be processed
        sleep();

        // Dataset of second response should be shown
        mUiBot.assertDatasets("dataset");

        // Create another delayed response
        FillResponse anotherResponse = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("dataset2"))
                        .setField(ID_USERNAME, "filled")
                        .build())
                .build()
                .asFillResponse(fillRequest.contexts,
                        (id) -> Helper.findNodeByResourceId(fillRequest.contexts, id));
        Intent anotherIntent = new Intent()
                .putExtra(EXTRA_FILL_RESPONSE, anotherResponse);

        // Tries to send another delayed fill response
        assertThrows(SendIntentException.class, () ->
                fillRequest.delayFillIntentSender
                        .sendIntent(getContext(), 0, anotherIntent, null, null, null));

        // Wait for fill response to be processed
        sleep();

        // Dataset of second response should still be shown
        mUiBot.assertDatasets("dataset");
    }

    @Test
    public void testSetDelayFillFlagTwiceButIntentCanOnlyBeSentOnce() throws Exception {
        // Set service.
        enableService();

        // Add placeholder response
        CannedFillResponse delayFillResponse = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("placeholder"))
                        .setField(ID_USERNAME, "filled").build())
                .setFillResponseFlags(FillResponse.FLAG_DELAY_FILL)
                .build();
        sReplier.addResponse(delayFillResponse);

        // Trigger autofill on username
        mUiBot.selectByRelativeId(ID_USERNAME);

        // Wait for fill request to be processed
        FillRequest fillRequest = sReplier.getNextFillRequest();

        // Wait until dataset is shown
        mUiBot.assertDatasets("placeholder");

        // Create the response that sets FLAG_DELAY_FILL again
        FillResponse secondResponse = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("dataset"))
                        .setField(ID_USERNAME, "filled").build())
                .setFillResponseFlags(FillResponse.FLAG_DELAY_FILL)
                .build()
                .asFillResponse(fillRequest.contexts,
                        (id) -> Helper.findNodeByResourceId(fillRequest.contexts, id));
        Intent intent = new Intent()
                .putExtra(EXTRA_FILL_RESPONSE, secondResponse);

        // Send delayed fill response
        fillRequest.delayFillIntentSender.sendIntent(getContext(), 0, intent, null, null, null);

        // Wait for fill response to be processed
        sleep();

        // Dataset of second response should be shown
        mUiBot.assertDatasets("dataset");

        // Create third response
        FillResponse thirdResponse = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("dataset2"))
                        .setField(ID_USERNAME, "filled")
                        .build())
                .build()
                .asFillResponse(fillRequest.contexts,
                        (id) -> Helper.findNodeByResourceId(fillRequest.contexts, id));
        Intent intent2 = new Intent()
                .putExtra(EXTRA_FILL_RESPONSE, thirdResponse);

        // Tries to send third delayed fill response
        assertThrows(SendIntentException.class, () ->
                fillRequest.delayFillIntentSender
                        .sendIntent(getContext(), 0, intent2, null, null, null));

        // Wait for fill response to be processed
        sleep();

        // Dataset of second response should still be shown
        mUiBot.assertDatasets("dataset");
    }

    @Test
    public void testOnlyAcceptDelayFillForLastRequest() throws Exception {
        // Set service.
        enableService();

        // Add placeholder response for first request
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .addDataset(new CannedFillResponse.CannedDataset.Builder(
                                createPresentation("placeholder"))
                                .setField(ID_USERNAME, "filled").build())
                        .setFillResponseFlags(FillResponse.FLAG_DELAY_FILL)
                        .build());

        // Trigger autofill on username
        mUiBot.selectByRelativeId(ID_USERNAME);

        // Wait for first fill request to be processed
        FillRequest fillRequest = sReplier.getNextFillRequest();

        // Wait until dataset is shown
        mUiBot.assertDatasets("placeholder");

        // Create delayed fill response for first request
        FillResponse response = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("dataset"))
                        .setField(ID_USERNAME, "filled").build())
                .build()
                .asFillResponse(fillRequest.contexts,
                        (id) -> Helper.findNodeByResourceId(fillRequest.contexts, id));
        Intent intent = new Intent()
                .putExtra(EXTRA_FILL_RESPONSE, response);

        // Add placeholder response for second request
        sReplier.addResponse(
                new CannedFillResponse.Builder()
                        .addDataset(new CannedFillResponse.CannedDataset.Builder(
                                createPresentation("placeholder2"))
                                .setField(ID_USERNAME, "filled").build())
                        .setFillResponseFlags(FillResponse.FLAG_DELAY_FILL)
                        .build());

        // Manually trigger autofill
        mActivity.getAutofillManager().requestAutofill(mActivity.getUsername());

        // Wait for fill response to be processed
        sleep();

        // Wait for second fill request to be processed
        FillRequest fillRequest2 = sReplier.getNextFillRequest();

        // Dataset of second response should be shown
        mUiBot.assertDatasets("placeholder2");

        // Send delayed fill response for first request
        assertThrows(SendIntentException.class, () ->
                fillRequest.delayFillIntentSender
                        .sendIntent(getContext(), 0, intent, null, null, null));

        // Wait for fill response to be processed
        sleep();

        // Dataset of second response should still be shown
        mUiBot.assertDatasets("placeholder2");

        // Create delayed fill response for second request
        FillResponse response2 = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder(
                        createPresentation("dataset2"))
                        .setField(ID_USERNAME, "filled")
                        .build())
                .build()
                .asFillResponse(fillRequest2.contexts,
                        (id) -> Helper.findNodeByResourceId(fillRequest2.contexts, id));
        Intent intent2 = new Intent()
                .putExtra(EXTRA_FILL_RESPONSE, response2);

        // Send delayed fill response for second request
        fillRequest2.delayFillIntentSender.sendIntent(getContext(), 0, intent2, null, null, null);

        // Wait for fill response to be processed
        sleep();

        // Dataset of second delayed response should be shown
        mUiBot.assertDatasets("dataset2");
    }

    private void sleep() {
        Log.d(TAG, "sleeping 2s ");
        SystemClock.sleep(2000);
    }
}
