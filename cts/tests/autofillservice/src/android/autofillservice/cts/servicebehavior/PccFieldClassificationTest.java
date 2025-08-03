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
package android.autofillservice.cts.servicebehavior;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.UNUSED_AUTOFILL_VALUE;
import static android.autofillservice.cts.testcore.Helper.assertHasFlags;
import static android.autofillservice.cts.testcore.Helper.disableFillDialogFeature;
import static android.autofillservice.cts.testcore.Helper.disablePccDetectionFeature;
import static android.autofillservice.cts.testcore.Helper.enableFillDialogFeature;
import static android.autofillservice.cts.testcore.Helper.enablePccDetectionFeature;
import static android.autofillservice.cts.testcore.Helper.isImeShowing;
import static android.autofillservice.cts.testcore.Helper.preferPccDetectionOverProvider;
import static android.service.autofill.FillRequest.FLAG_SUPPORTS_FILL_DIALOG;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.autofillservice.cts.activities.AuthenticationActivity;
import android.autofillservice.cts.activities.LoginActivity;
import android.autofillservice.cts.commontests.FieldClassificationServiceManualActivityLaunchTestCase;
import android.autofillservice.cts.testcore.CannedFieldClassificationResponse;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.IdMode;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService;
import android.autofillservice.cts.testcore.MyAutofillCallback;
import android.content.IntentSender;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.view.View;

import androidx.test.uiautomator.UiObject2;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.TimeoutException;

@AppModeFull
public class PccFieldClassificationTest extends
        FieldClassificationServiceManualActivityLaunchTestCase {

    public static final String DROPDOWN_PRESENTATION = "Dropdown Presentation";
    public static final String DROPDOWN_PRESENTATION2 = "Dropdown Presentation2";
    public static final String DIALOG_PRESENTATION = "Dialog Presentation";

    /*
      Ideally, autofill hints should be taken directly from HintConstants defined in androidx
      library at https://developer.android.com/reference/androidx/autofill/HintConstants.

      There are 2 options

      First is to refactor our test infra so that we can specify which datasets are for hints,
      which are for autofill ids and which ones are for both. We can also consider using helper
      utilities to bring it close to actual Dataset, instead of using CannedDataset. This may
      require some refactoring.

      Second is to use activities with their resources naming in such a way that it doesn't
      conflict with the hints name. Since, we want to support PCC testing across existing tests
      which use same naming for some hints (eg: username, password), this may require refactoring.
      This approach isn't ideal since test writer needs to keep this in mind.

      For now, we just use autofill hints name with "hint_" prefix, but actual services shouldn't
       do this.
     */
    public static final String AUTOFILL_HINT_PASSWORD = "hint_password";
    public static final String AUTOFILL_HINT_USERNAME = "hint_username";
    public static final String AUTOFILL_HINT_NEW_PASSWORD = "hint_new_password";

    @Before
    public void setup() throws Exception {
        assumeTrue("PCC is enabled", Helper.isPccSupported(mContext));

        enableService();
        enablePccDetectionFeature(sContext, AUTOFILL_HINT_USERNAME, AUTOFILL_HINT_PASSWORD,
                AUTOFILL_HINT_NEW_PASSWORD);
        enablePccDetectionService();
        sReplier.setIdMode(IdMode.PCC_ID);
    }

    @After
    public void destroy() {
        sReplier.setIdMode(IdMode.RESOURCE_ID);
        disablePccDetectionFeature(sContext);
    }

    @Test
    @Ignore("PCC turndown")
    public void testFieldClassificationRequestIsSentWhenScreenEntered() throws Exception {

        sClassificationReplier.addResponse(new CannedFieldClassificationResponse.Builder()
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_PASSWORD, Set.of(AUTOFILL_HINT_PASSWORD)))
                .build());

        startLoginActivity();
        mUiBot.waitForIdleSync();
        sClassificationReplier.getNextFieldClassificationRequest();
        sClassificationReplier.assertNoUnhandledFieldClassificationRequests();
    }

    @Test
    @Ignore("PCC turndown")
    public void testFieldClassification_withFillDialog() throws Exception {

        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        sClassificationReplier.addResponse(new CannedFieldClassificationResponse.Builder()
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_PASSWORD, Set.of(AUTOFILL_HINT_PASSWORD)))
                .build());

        // Set response with a dataset > fill dialog should have two buttons
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation(DROPDOWN_PRESENTATION))
                        .setDialogPresentation(createPresentation(DIALOG_PRESENTATION))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD)
                .build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();


        // Assert FieldClassification request was sent
        sClassificationReplier.getNextFieldClassificationRequest();
        sClassificationReplier.assertNoUnhandledFieldClassificationRequests();

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final InstrumentedAutoFillService.FillRequest fillRequest =
                sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);
        // assert that request contains hints
        assertFillRequestHints(fillRequest);
        sReplier.assertNoUnhandledFillRequests();
        mUiBot.waitForIdleSync();

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Verify IME is not shown
        assertThat(isImeShowing(activity.getRootWindowInsets())).isFalse();

        // Verify the content of fill dialog, and then select dataset in fill dialog
        mUiBot.assertFillDialogHeader("Dialog Header");
        mUiBot.assertFillDialogRejectButton();
        mUiBot.assertFillDialogAcceptButton();
        final UiObject2 picker = mUiBot.assertFillDialogDatasets(DIALOG_PRESENTATION);

        // Set expected value, then select dataset
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset(picker, DIALOG_PRESENTATION);

        // Check the results.
        activity.assertAutoFilled();
    }

    @Test
    @Ignore("PCC turndown")
    public void testFieldClassification_withDropdownDialog() throws Exception {

        // Enable feature and test service
        disableFillDialogFeature(sContext);
        enableService();

        sClassificationReplier.addResponse(new CannedFieldClassificationResponse.Builder()
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_PASSWORD, Set.of(AUTOFILL_HINT_PASSWORD)))
                .build());

        // Set response with a dataset > fill dialog should have two buttons
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation(DROPDOWN_PRESENTATION))
                        .build())
                .build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        // Set expected value
        activity.expectAutoFill("dude", "sweet");

        // Assert FieldClassification request was sent
        sClassificationReplier.getNextFieldClassificationRequest();

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        final InstrumentedAutoFillService.FillRequest fillRequest =
                sReplier.getNextFillRequest();
        // assert that request contains hints
        assertFillRequestHints(fillRequest);
        mUiBot.waitForIdleSync();


        // Auto-fill it.
        final UiObject2 picker = mUiBot.assertDatasetsWithBorders(
                null /* expectedHeader */, null /* expectedFooter */, DROPDOWN_PRESENTATION);
        mUiBot.selectDataset(picker, DROPDOWN_PRESENTATION);

        // Check the results.
        activity.assertAutoFilled();

        sClassificationReplier.assertNoUnhandledFieldClassificationRequests();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    @Ignore("PCC turndown")
    public void testFieldClassification_mergeResultsFromPccAndProvider_sameDataset()
            throws Exception {

        // Enable feature and test service
        disableFillDialogFeature(sContext);
        enableService();

        sClassificationReplier.addResponse(new CannedFieldClassificationResponse.Builder()
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_PASSWORD, Set.of(AUTOFILL_HINT_PASSWORD)))
                .build());

        // Set response with a dataset > fill dialog should have two buttons
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(AUTOFILL_HINT_PASSWORD, "sweet")
                        .setPresentation(createPresentation(DROPDOWN_PRESENTATION))
                        .build())
                .build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        // Assert FieldClassification request was sent
        sClassificationReplier.getNextFieldClassificationRequest();

        // Set expected value
        // TODO: change it back to assert both username and password in next release
        activity.expectPasswordAutoFill("sweet");

        // Click on password field to trigger autofill
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        final InstrumentedAutoFillService.FillRequest fillRequest =
                sReplier.getNextFillRequest();
        // assert that request contains hints
        assertFillRequestHints(fillRequest);
        mUiBot.waitForIdleSync();


        // Auto-fill it.
        UiObject2 picker = mUiBot.assertDatasetsWithBorders(
                null /* expectedHeader */, null /* expectedFooter */, DROPDOWN_PRESENTATION);
        mUiBot.selectDataset(picker, DROPDOWN_PRESENTATION);

        // Check the results.
        activity.assertAutoFilled();


        // Set expected value
        activity.expectAutoFill("dude");

        sClassificationReplier.assertNoUnhandledFieldClassificationRequests();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    @Ignore("PCC turndown")
    public void testFieldClassification_mergeResultsFromPccAndProvider_separateDataset()
            throws Exception {

        // Enable feature and test service
        disableFillDialogFeature(sContext);
        enableService();

        sClassificationReplier.addResponse(new CannedFieldClassificationResponse.Builder()
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_USERNAME, Set.of(AUTOFILL_HINT_USERNAME)))
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_PASSWORD, Set.of(AUTOFILL_HINT_PASSWORD)))
                .build());

        // Set response with a dataset > fill dialog should have two buttons
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation(DROPDOWN_PRESENTATION))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(AUTOFILL_HINT_PASSWORD, "sweet")
                        .setPresentation(createPresentation(DROPDOWN_PRESENTATION2))
                        .build())
                .build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        // Assert FieldClassification request was sent
        sClassificationReplier.getNextFieldClassificationRequest();

        // Set expected value for password only
        activity.expectPasswordAutoFill("sweet");

        // Click on password field to trigger autofill
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        final InstrumentedAutoFillService.FillRequest fillRequest =
                sReplier.getNextFillRequest();
        // assert that request contains hints
        assertFillRequestHints(fillRequest);
        mUiBot.waitForIdleSync();


        // Auto-fill it.
        UiObject2 picker = mUiBot.assertDatasetsWithBorders(
                null /* expectedHeader */, null /* expectedFooter */, DROPDOWN_PRESENTATION2);
        mUiBot.selectDataset(picker, DROPDOWN_PRESENTATION2);

        // Check the results.
        activity.assertAutoFilled();


        // Set expected value for username
        activity.expectAutoFill("dude");

        // Click on username field to see presentation from previous autofill request.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();


        // Auto-fill it.
        picker = mUiBot.assertDatasetsWithBorders(
                null /* expectedHeader */, null /* expectedFooter */, DROPDOWN_PRESENTATION);
        mUiBot.selectDataset(picker, DROPDOWN_PRESENTATION);

        // Check the results.
        activity.assertAutoFilled();

        sClassificationReplier.assertNoUnhandledFieldClassificationRequests();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    @Ignore("PCC turndown")
    public void testFieldClassification_preferPccDetection_sameDetection() throws Exception {

        // Enable feature and test service
        disableFillDialogFeature(sContext);
        preferPccDetectionOverProvider(sContext, true);
        enableService();

        sClassificationReplier.addResponse(new CannedFieldClassificationResponse.Builder()
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_USERNAME, Set.of(AUTOFILL_HINT_USERNAME)))
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_PASSWORD, Set.of(AUTOFILL_HINT_PASSWORD)))
                .build());

        // Set response with a dataset > fill dialog should have two buttons
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "username")
                        .setField(ID_PASSWORD, "password")
                        .setPresentation(createPresentation(DROPDOWN_PRESENTATION))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(AUTOFILL_HINT_USERNAME, "hint_username")
                        .setField(AUTOFILL_HINT_PASSWORD, "hint_password")
                        .setPresentation(createPresentation(DROPDOWN_PRESENTATION2))
                        .build())
                .build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        // Assert FieldClassification request was sent
        sClassificationReplier.getNextFieldClassificationRequest();

        // Set expected value
        activity.expectAutoFill("hint_username", "hint_password");

        // Click on password field to trigger autofill
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        final InstrumentedAutoFillService.FillRequest fillRequest =
                sReplier.getNextFillRequest();
        // assert that request contains hints
        assertFillRequestHints(fillRequest);
        mUiBot.waitForIdleSync();

        // Auto-fill it.
        final UiObject2 picker = mUiBot.assertDatasetsWithBorders(
                null /* expectedHeader */, null /* expectedFooter */, DROPDOWN_PRESENTATION2);
        mUiBot.selectDataset(picker, DROPDOWN_PRESENTATION2);

        // Check the results.
        activity.assertAutoFilled();

        sClassificationReplier.assertNoUnhandledFieldClassificationRequests();
        sReplier.assertNoUnhandledFillRequests();

        sClassificationReplier.assertNoUnhandledFieldClassificationRequests();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    @Ignore("PCC turndown")
    public void testFieldClassification_noDetectionFromProvider() throws Exception {
        preferPccDetectionOverProvider(sContext, false);
        testNoDetectionFromProvider();
    }

    @Test
    @Ignore("PCC turndown")
    public void testFieldClassification_noDetectionFromProvider_preferPcc() throws Exception {
        preferPccDetectionOverProvider(sContext, true);
        testNoDetectionFromProvider();
    }

    private void testNoDetectionFromProvider() throws Exception {

        // Enable feature and test service
        disableFillDialogFeature(sContext);
        enableService();

        sClassificationReplier.addResponse(new CannedFieldClassificationResponse.Builder()
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_USERNAME, Set.of(AUTOFILL_HINT_USERNAME)))
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_PASSWORD, Set.of(AUTOFILL_HINT_PASSWORD)))
                .build());

        // Set response with a dataset > fill dialog should have two buttons
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(AUTOFILL_HINT_USERNAME, "hint_username")
                        .setField(AUTOFILL_HINT_PASSWORD, "hint_password")
                        .setPresentation(createPresentation(DROPDOWN_PRESENTATION))
                        .build())
                .build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        // Assert FieldClassification request was sent
        sClassificationReplier.getNextFieldClassificationRequest();

        // Set expected value
        activity.expectAutoFill("hint_username", "hint_password");

        // Click on password field to trigger autofill
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        final InstrumentedAutoFillService.FillRequest fillRequest =
                sReplier.getNextFillRequest();
        // assert that request contains hints
        assertFillRequestHints(fillRequest);
        mUiBot.waitForIdleSync();

        // Auto-fill it.
        final UiObject2 picker = mUiBot.assertDatasetsWithBorders(
                null /* expectedHeader */, null /* expectedFooter */, DROPDOWN_PRESENTATION);
        mUiBot.selectDataset(picker, DROPDOWN_PRESENTATION);

        // Check the results.
        activity.assertAutoFilled();

        sClassificationReplier.assertNoUnhandledFieldClassificationRequests();
        sReplier.assertNoUnhandledFillRequests();

        sClassificationReplier.assertNoUnhandledFieldClassificationRequests();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    @Ignore("PCC turndown")
    public void testDatasetAuthTwoFields() throws Exception {
        datasetAuthTwoFields(false);
    }

    @Test
    @Ignore("PCC turndown")
    @AppModeFull(reason = "testDatasetAuthTwoFields() is enough")
    public void testDatasetAuthTwoFieldsUserCancelsFirstAttempt() throws Exception {
        datasetAuthTwoFields(true);
    }

    private void datasetAuthTwoFields(boolean cancelFirstAttempt) throws Exception {
        // Set service.
        disableFillDialogFeature(sContext);

        sClassificationReplier.addResponse(new CannedFieldClassificationResponse.Builder()
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_USERNAME, Set.of(AUTOFILL_HINT_USERNAME)))
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_PASSWORD, Set.of(AUTOFILL_HINT_PASSWORD)))
                .build());

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.CannedDataset.Builder()
                        .setField(AUTOFILL_HINT_USERNAME, "dude")
                        .setField(AUTOFILL_HINT_PASSWORD, "sweet")
                        .build());

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(AUTOFILL_HINT_USERNAME, UNUSED_AUTOFILL_VALUE)
                        .setField(AUTOFILL_HINT_PASSWORD, UNUSED_AUTOFILL_VALUE)
                        .setPresentation(createPresentation("Tap to auth dataset"))
                        .setAuthentication(authentication)
                        .build())
                .build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        final MyAutofillCallback callback = activity.registerCallback();
        mUiBot.waitForIdleSync();

        // Assert FieldClassification request was sent
        sClassificationReplier.getNextFieldClassificationRequest();

        // Set expectation for the activity
        activity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.

        // Click on password field to trigger autofill
        requestFocusOnUsername(activity);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = activity.getUsername();
        callback.assertUiShownEvent(username);
        mUiBot.assertDatasets("Tap to auth dataset");

        // Make sure UI is show on 2nd field as well
        final View password = activity.getPassword();
        // Click on password field to trigger autofill
        requestFocusOnPassword(activity);

        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(password);
        mUiBot.assertDatasets("Tap to auth dataset");

        // Now tap on 1st field to show it again...
        requestFocusOnUsername(activity);
        callback.assertUiHiddenEvent(password);
        callback.assertUiShownEvent(username);
        mUiBot.assertDatasets("Tap to auth dataset");

        if (cancelFirstAttempt) {
            // Trigger the auth dialog, but emulate cancel.
            AuthenticationActivity.setResultCode(RESULT_CANCELED);
            mUiBot.selectDataset("Tap to auth dataset");
            callback.assertUiHiddenEvent(username);
            callback.assertUiShownEvent(username);
            mUiBot.assertDatasets("Tap to auth dataset");

            // Make sure it's still shown on other fields...
            requestFocusOnPassword(activity);
            callback.assertUiHiddenEvent(username);
            callback.assertUiShownEvent(password);
            mUiBot.assertDatasets("Tap to auth dataset");

            // Tap on 1st field to show it again...
            requestFocusOnUsername(activity);
            callback.assertUiHiddenEvent(password);
            callback.assertUiShownEvent(username);
        }

        // ...and select it this time
        AuthenticationActivity.setResultCode(RESULT_OK);
        mUiBot.selectDataset("Tap to auth dataset");
        callback.assertUiHiddenEvent(username);
        mUiBot.assertNoDatasets();

        // Check the results.
        activity.assertAutoFilled();
    }

    @Test
    @Ignore("PCC turndown")
    public void testFillResponseAuthBothFields() throws Exception {
        fillResponseAuthBothFields(false);
    }

    @Test
    @Ignore("PCC turndown")
    @AppModeFull(reason = "testFillResponseAuthBothFields() is enough")
    public void testFillResponseAuthBothFieldsUserCancelsFirstAttempt() throws Exception {
        fillResponseAuthBothFields(true);
    }

    private void fillResponseAuthBothFields(boolean cancelFirstAttempt) throws Exception {

        sClassificationReplier.addResponse(new CannedFieldClassificationResponse.Builder()
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_USERNAME, Set.of(AUTOFILL_HINT_USERNAME)))
                .addFieldClassification(
                        new CannedFieldClassificationResponse.CannedFieldClassification(
                                ID_PASSWORD, Set.of(AUTOFILL_HINT_PASSWORD)))
                .build());

        // Prepare the authenticated response
        final Bundle clientState = new Bundle();
        clientState.putString("numbers", "4815162342");
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.Builder().addDataset(
                                new CannedFillResponse.CannedDataset.Builder()
                                        .setField(AUTOFILL_HINT_USERNAME, "dude")
                                        .setField(AUTOFILL_HINT_PASSWORD, "sweet")
                                        .setId("name")
                                        .setPresentation(createPresentation("Dataset"))
                                        .build())
                        .setExtras(clientState).build());

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication, ID_USERNAME, ID_PASSWORD)
                .setPresentation(createPresentation("Tap to auth response"))
                .setExtras(clientState)
                .build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        final MyAutofillCallback callback = activity.registerCallback();
        mUiBot.waitForIdleSync();

        // Set expectation for the activity
        activity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        requestFocusOnUsername(activity);

        // Wait for onFill() before proceeding.
        sReplier.getNextFillRequest();
        final View username = activity.getUsername();
        callback.assertUiShownEvent(username);
        mUiBot.assertDatasets("Tap to auth response");

        // Make sure UI is show on 2nd field as well
        final View password = activity.getPassword();
        requestFocusOnPassword(activity);
        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(password);
        mUiBot.assertDatasets("Tap to auth response");

        // Now tap on 1st field to show it again...
//        requestFocusOnUsername(activity); // maybe hidden by the suggestions from the first one

        requestFocusOnUsername(activity);
        callback.assertUiHiddenEvent(password);
        callback.assertUiShownEvent(username);

        if (cancelFirstAttempt) {
            // Trigger the auth dialog, but emulate cancel.
            AuthenticationActivity.setResultCode(RESULT_CANCELED);
            mUiBot.selectDataset("Tap to auth response");
            callback.assertUiHiddenEvent(username);
            callback.assertUiShownEvent(username);
            mUiBot.assertDatasets("Tap to auth response");

            // Make sure it's still shown on other fields...
            requestFocusOnPassword(activity);
            callback.assertUiHiddenEvent(username);
            callback.assertUiShownEvent(password);
            mUiBot.assertDatasets("Tap to auth response");

            // Tap on 1st field to show it again...
            requestFocusOnUsername(activity);
            callback.assertUiHiddenEvent(password);
            callback.assertUiShownEvent(username);
        }

        // ...and select it this time
        AuthenticationActivity.setResultCode(RESULT_OK);
        mUiBot.selectDataset("Tap to auth response");
        callback.assertUiHiddenEvent(username);
        callback.assertUiShownEvent(username);
        final UiObject2 picker = mUiBot.assertDatasets("Dataset");
        mUiBot.selectDataset(picker, "Dataset");
        callback.assertUiHiddenEvent(username);
        mUiBot.assertNoDatasets();

        // Check the results.
        activity.assertAutoFilled();

        final Bundle data = AuthenticationActivity.getData();
        assertThat(data).isNotNull();
        final String extraValue = data.getString("numbers");
        assertThat(extraValue).isEqualTo("4815162342");
    }

    /**
     * Requests focus on username and expect Window event happens.
     */
    protected void requestFocusOnUsername(LoginActivity activity) throws TimeoutException {
        mUiBot.waitForWindowChange(() -> activity.onUsername(View::requestFocus));
    }

    /**
     * Requests focus on password and expect Window event happens.
     */
    protected void requestFocusOnPassword(LoginActivity activity) throws TimeoutException {
        mUiBot.waitForWindowChange(() -> activity.onPassword(View::requestFocus));
    }

    /**
     * Asserts that the fill request contains hints
     */
    private void assertFillRequestHints(InstrumentedAutoFillService.FillRequest fillRequest) {
        assertThat(fillRequest.hints.size()).isEqualTo(3);
        assertThat(fillRequest.hints.get(0)).isEqualTo(AUTOFILL_HINT_USERNAME);
        assertThat(fillRequest.hints.get(1)).isEqualTo(AUTOFILL_HINT_PASSWORD);
        assertThat(fillRequest.hints.get(2)).isEqualTo(AUTOFILL_HINT_NEW_PASSWORD);
    }

}

