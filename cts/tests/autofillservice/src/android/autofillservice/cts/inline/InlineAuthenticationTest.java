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

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.autofillservice.cts.activities.LoginActivity.getWelcomeMessage;
import static android.autofillservice.cts.testcore.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.UNUSED_AUTOFILL_VALUE;
import static android.autofillservice.cts.testcore.InstrumentedAutoFillServiceInlineEnabled.SERVICE_NAME;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;

import static com.google.common.truth.Truth.assertWithMessage;

import android.autofillservice.cts.activities.AuthenticationActivity;
import android.autofillservice.cts.commontests.AbstractLoginActivityTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.DeviceUtils;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InlineUiBot;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.SaveRequest;
import android.autofillservice.cts.testcore.UiBot;
import android.content.IntentSender;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.view.inputmethod.InlineSuggestionsRequest;

import androidx.test.filters.FlakyTest;

import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.regex.Pattern;

public class InlineAuthenticationTest extends AbstractLoginActivityTestCase {

    private static final String TAG = "InlineAuthenticationTest";

    // TODO: move common part to the other places
    enum ClientStateLocation {
        INTENT_ONLY,
        FILL_RESPONSE_ONLY,
        BOTH
    }

    public InlineAuthenticationTest() {
        super(getInlineUiBot());
    }

    @Override
    protected void enableService() {
        Helper.enableAutofillService(SERVICE_NAME);
    }

    @Override
    public TestRule getMainTestRule() {
        return InlineUiBot.annotateRule(super.getMainTestRule());
    }

    /**
     * This test verifies the behavior that user starts a new AutofillSession in Authentication
     * Activity during the FillResponse authentication flow, we will fallback to dropdown when
     * authentication done and then back to original Activity.
     */
    @Presubmit
    @Test
    public void testFillResponseAuth_withNewAutofillSessionStartByActivity()
            throws Exception {
        // Set service.
        enableService();

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.Builder().addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "dude")
                                .setField(ID_PASSWORD, "sweet")
                                .setId("name")
                                .setInlinePresentation(createInlinePresentation("Dataset"))
                                .setPresentation(createPresentation("Dataset"))
                                .build()).build());
        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication, ID_USERNAME, ID_PASSWORD)
                .setPresentation(createPresentation("Tap to auth!"))
                .setInlinePresentation(createInlinePresentation("Tap to auth!"))
                .build());

        // Trigger auto-fill.
        assertSuggestionShownBySelectViewId(ID_USERNAME, "Tap to auth!");
        sReplier.getNextFillRequest();

        // Need to trigger autofill on AuthenticationActivity
        // Set expected response for autofill on AuthenticationActivity
        AuthenticationActivity.setResultCode(RESULT_OK);
        AuthenticationActivity.setRequestAutofillForAuthenticationActivity(true);
        sReplier.addResponse(NO_RESPONSE);
        // Select the dataset to start authentication
        mUiBot.selectDataset("Tap to auth!");
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        // Select yes button in AuthenticationActivity to finish authentication
        mUiBot.selectByRelativeId("yes");
        mUiBot.waitForIdle();

        // Check fallback to dropdown
        final UiBot dropDownUiBot = getDropdownUiBot();
        dropDownUiBot.assertDatasets("Dataset");
    }

    @FlakyTest(
            bugId = 291803739,
            detail = "Autofill behavior is flaky. See bug, try to resolve asap")
    @Presubmit
    @Test
    public void testFillResponseAuth() throws Exception {
        // Set service.
        enableService();

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.Builder().addDataset(
                        new CannedDataset.Builder()
                                .setField(ID_USERNAME, "dude")
                                .setField(ID_PASSWORD, "sweet")
                                .setId("name")
                                .setInlinePresentation(createInlinePresentation("Dataset"))
                                .setPresentation(createPresentation("Dataset"))
                                .build()).build());
        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setAuthentication(authentication, ID_USERNAME, ID_PASSWORD)
                .setPresentation(createPresentation("Tap to auth!"))
                .setInlinePresentation(createInlinePresentation("Tap to auth!"))
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill
        assertSuggestionShownBySelectViewId(ID_USERNAME, "Tap to auth!");

        sReplier.getNextFillRequest();

        // Set AuthenticationActivity result code
        AuthenticationActivity.setResultCode(RESULT_OK);
        // Select the dataset to start authentication
        mUiBot.selectDataset("Tap to auth!");
        mUiBot.waitForIdleSync();
        // Authentication done, show real dataset
        DeviceUtils.Dataset.assertShowsInline();
        mUiBot.waitForIdleSync();

        // Select the dataset and check the result is autofilled.
        mUiBot.selectDataset("Dataset");
        mUiBot.waitForIdleSync();
        mUiBot.assertNoDatasets();
        mActivity.assertAutoFilled();
    }

    @Presubmit
    @Test
    public void testDatasetAuthTwoFields() throws Exception {
        datasetAuthTwoFields(/* cancelFirstAttempt */ false);
    }

    @Test
    @FlakyTest(bugId = 185876679)
    @AppModeFull(reason = "testDatasetAuthTwoFields() is enough")
    public void testDatasetAuthTwoFieldsUserCancelsFirstAttempt() throws Exception {
        datasetAuthTwoFields(/* cancelFirstAttempt */ true);
    }

    private void datasetAuthTwoFields(boolean cancelFirstAttempt) throws Exception {
        // Set service.
        enableService();

        // Prepare the authenticated response
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .build());
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, UNUSED_AUTOFILL_VALUE)
                        .setField(ID_PASSWORD, UNUSED_AUTOFILL_VALUE)
                        .setPresentation(createPresentation("auth"))
                        .setInlinePresentation(createInlinePresentation("auth"))
                        .setAuthentication(authentication)
                        .build());
        sReplier.addResponse(builder.build());
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill.
        assertSuggestionShownBySelectViewId(ID_USERNAME, "auth");
        sReplier.getNextFillRequest();

        // Make sure UI is show on 2nd field as well
        assertSuggestionShownBySelectViewId(ID_PASSWORD, "auth");

        // Now tap on 1st field to show it again...
        assertSuggestionShownBySelectViewId(ID_USERNAME, "auth");

        if (cancelFirstAttempt) {
            // Trigger the auth dialog, but emulate cancel.
            AuthenticationActivity.setResultCode(RESULT_CANCELED);
            mUiBot.selectDataset("auth");
            mUiBot.waitForIdle();
            mUiBot.assertDatasets("auth");

            // Make sure it's still shown on other fields...
            assertSuggestionShownBySelectViewId(ID_PASSWORD, "auth");

            // Tap on 1st field to show it again...
            assertSuggestionShownBySelectViewId(ID_USERNAME, "auth");
        }

        // ...and select it this time
        AuthenticationActivity.setResultCode(RESULT_OK);
        mUiBot.selectDataset("auth");
        mUiBot.waitForIdle();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Presubmit
    @Test
    public void testDatasetAuthPinnedPresentationSelectedAndAutofilled() throws Exception {
        testDatasetAuthEphemeralOrPinned(/* isEphemeralDataset= */ null, /* isPinned= */true);
    }

    @Presubmit
    @Test
    public void testDatasetAuthEphemeralIsTrue() throws Exception {
        testDatasetAuthEphemeralOrPinned(/* isEphemeralDataset= */ true, /* isPinned= */false);
    }

    @Presubmit
    @Test
    public void testDatasetAuthEphemeralIsFalse() throws Exception {
        testDatasetAuthEphemeralOrPinned(/* isEphemeralDataset= */ false, /* isPinned= */false);
    }

    @Presubmit
    @Test
    public void testDatasetAuthEphemeralNotSet() throws Exception {
        testDatasetAuthEphemeralOrPinned(/* isEphemeralDataset= */ null, /* isPinned= */false);
    }

    private void testDatasetAuthEphemeralOrPinned(Boolean isEphemeralDataset, boolean isPinned)
            throws Exception {
        // Set service.
        enableService();

        // Prepare the authenticated dataset
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude", null,
                                Helper.createInlinePresentation("dude"))
                        .setField(ID_PASSWORD, "sweet", null,
                                Helper.createInlinePresentation("sweet"))
                        .build(), null, isEphemeralDataset);

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, UNUSED_AUTOFILL_VALUE, null,
                                isPinned ? Helper.createPinnedInlinePresentation("auth-username")
                                        : Helper.createInlinePresentation("auth-username"))
                        .setField(ID_PASSWORD, UNUSED_AUTOFILL_VALUE, null,
                                Helper.createInlinePresentation("auth-password"))
                        .setPresentation(createPresentation("auth"))
                        .setAuthentication(authentication)
                        .build());
        sReplier.addResponse(builder.build());

        // Trigger auto-fill, verify seeing dataset.
        assertSuggestionShownBySelectViewId(ID_USERNAME, "auth-username");
        sReplier.getNextFillRequest();

        // ...and select the dataset, then check the authentication result is autofilled.
        mActivity.expectAutoFill("dude", "sweet");
        AuthenticationActivity.setResultCode(RESULT_OK);
        mUiBot.selectDataset("auth-username");
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();

        // Clear the username field; workaround reset bug fix by clearing progressively
        mUiBot.clearTextByRelativeId(ID_USERNAME);
        final boolean expectOldDataset = isEphemeralDataset == null ? isPinned : isEphemeralDataset;
        if (!expectOldDataset) {
            // Expect to see the suggestion returned from auth intent.
            assertSuggestionShownBySelectViewId(ID_USERNAME, "dude");
            return;
        }

        // Below codes are only applicable for the ephemeral case (isEphemeralData is set to true
        // or isPinned is set to true)

        // Expect to see the old suggestion, rather than the one returned from auth intent.
        assertSuggestionShownBySelectViewId(ID_USERNAME, "auth-username");
        // Now select the dataset again and verify that the same authentication flow happens.
        mActivity.expectAutoFill("dude", "sweet");
        AuthenticationActivity.setResultCode(RESULT_OK);
        mUiBot.selectDataset("auth-username");
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();

        // Clear the username field, put focus on password field, and then clear the password field,
        // Expect to see the old suggestion.
        mUiBot.clearTextByRelativeId(ID_USERNAME);
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.clearTextByRelativeId(ID_PASSWORD);
        assertSuggestionShownBySelectViewId(ID_PASSWORD, "auth-password");

        // Now select the dataset again and verify that the same authentication flow happens.
        mActivity.expectAutoFill("dude", "sweet");
        AuthenticationActivity.setResultCode(RESULT_OK);
        mUiBot.selectDataset("auth-password");
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();

        // Clear the password field, and expect to see the old suggestion again, rather than
        // the one returned from auth intent.
        mUiBot.clearTextByRelativeId(ID_PASSWORD);
        assertSuggestionShownBySelectViewId(ID_PASSWORD, "auth-password");

        // Now select the dataset again and verify that the same authentication flow happens.
        mActivity.expectAutoFill("dude", "sweet");
        AuthenticationActivity.setResultCode(RESULT_OK);
        mUiBot.selectDataset("auth-password");
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();
    }

    @Presubmit
    @Test
    public void testDatasetAuthFilteringUsingRegex() throws Exception {
        // Set service.
        enableService();

        // Create the authentication intents
        final CannedDataset unlockedDataset = new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .build();
        final IntentSender authentication = AuthenticationActivity.createSender(mContext, 1,
                unlockedDataset);
        final Pattern min2Chars = Pattern.compile(".{2,}");
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, UNUSED_AUTOFILL_VALUE, min2Chars)
                        .setField(ID_PASSWORD, UNUSED_AUTOFILL_VALUE)
                        .setPresentation(createPresentation("auth"))
                        .setInlinePresentation(createInlinePresentation("auth"))
                        .setAuthentication(authentication)
                        .build())
                .build());
        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill, make sure it's showing initially.
        assertSuggestionShownBySelectViewId(ID_USERNAME, "auth");
        sReplier.getNextFillRequest();

        // ...then type something to hide it.
        mActivity.onUsername((v) -> v.setText("a"));
        // Suggestion strip was not shown.
        mUiBot.assertNoDatasetsEver();
        mUiBot.waitForIdle();

        // ...now type something again to show it, as the input will have 2 chars.
        mActivity.onUsername((v) -> v.setText("aa"));
        mUiBot.waitForIdle();
        mUiBot.assertDatasets("auth");

        // ...and select it
        mUiBot.selectDataset("auth");
        mUiBot.waitForIdle();
        mUiBot.assertNoDatasets();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Presubmit
    @Test
    public void testDatasetAuthInlineSuggestionsRequestForTwoPartitions() throws Exception {
        // Set service.
        enableService();

        // Prepare the authenticated response and configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, UNUSED_AUTOFILL_VALUE)
                        .setPresentation(createPresentation("auth"))
                        .setInlinePresentation(createInlinePresentation("auth"))
                        .setAuthentication(AuthenticationActivity.createSender(mContext, 1,
                                new CannedDataset.Builder()
                                        .setField(ID_USERNAME, "dude")
                                        .build()))
                        .build())
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude");

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();

        final InlineSuggestionsRequest inlineSuggestionsRequest =
                sReplier.getNextFillRequest().inlineRequest;

        // Tap authentication request.
        mUiBot.selectDataset("auth");
        mUiBot.waitForIdle();

        // Check the results.
        mActivity.assertAutoFilled();
        mUiBot.waitForIdle();

        // Assert inline suggestions request on authentication activity.
        assertWithMessage("Auth intent extras should contain InlineSuggestionsRequest")
                .that(AuthenticationActivity.getInlineSuggestionsRequest())
                .isEqualTo(inlineSuggestionsRequest);

        // Now tap on password to trigger a new autofill request since it's a different partition.

        // Prepare the authenticated response
        sReplier.addResponse(new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_PASSWORD, UNUSED_AUTOFILL_VALUE)
                        .setPresentation(createPresentation("auth2"))
                        .setInlinePresentation(createInlinePresentation("auth2"))
                        .setAuthentication(AuthenticationActivity.createSender(mContext, 2,
                                new CannedDataset.Builder()
                                        .setField(ID_PASSWORD, "sweet")
                                        .build()))
                        .build())
                .build());
        // Set expectation for the activity
        mActivity.expectPasswordAutoFill("sweet");

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_PASSWORD);
        mUiBot.waitForIdle();
        final InlineSuggestionsRequest inlineSuggestionsRequest2 =
                sReplier.getNextFillRequest().inlineRequest;

        // Tap authentication request.
        mUiBot.selectDataset("auth2");
        mUiBot.waitForIdle();

        // Check the results.
        mActivity.assertAutoFilled();
        mUiBot.waitForIdle();

        // Assert inline suggestions request on authentication activity.
        assertWithMessage("Auth intent extras should contain InlineSuggestionsRequest")
                .that(AuthenticationActivity.getInlineSuggestionsRequest())
                .isEqualTo(inlineSuggestionsRequest2);
    }

    @Presubmit
    @Test
    public void testDatasetAuthClientStateSetOnIntentOnly() throws Exception {
        fillDatasetAuthWithClientState(ClientStateLocation.INTENT_ONLY);
    }

    @Test
    @AppModeFull(reason = "testDatasetAuthClientStateSetOnIntentOnly() is enough")
    public void testDatasetAuthClientStateSetOnFillResponseOnly() throws Exception {
        fillDatasetAuthWithClientState(ClientStateLocation.FILL_RESPONSE_ONLY);
    }

    @Test
    @AppModeFull(reason = "testDatasetAuthClientStateSetOnIntentOnly() is enough")
    public void testDatasetAuthClientStateSetOnIntentAndFillResponse() throws Exception {
        fillDatasetAuthWithClientState(ClientStateLocation.BOTH);
    }

    private void fillDatasetAuthWithClientState(ClientStateLocation where) throws Exception {
        // Set service.
        enableService();

        // Prepare the authenticated response
        final CannedDataset dataset = new CannedDataset.Builder()
                .setField(ID_USERNAME, "dude")
                .setField(ID_PASSWORD, "sweet")
                .build();
        final IntentSender authentication = where == ClientStateLocation.FILL_RESPONSE_ONLY
                ? AuthenticationActivity.createSender(mContext, 1,
                dataset)
                : AuthenticationActivity.createSender(mContext, 1,
                        dataset, Helper.newClientState("CSI", "FromIntent"));

        // Configure the service behavior
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .setExtras(Helper.newClientState("CSI", "FromResponse"))
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, UNUSED_AUTOFILL_VALUE)
                        .setField(ID_PASSWORD, UNUSED_AUTOFILL_VALUE)
                        .setPresentation(createPresentation("auth"))
                        .setInlinePresentation(createInlinePresentation("auth"))
                        .setAuthentication(authentication)
                        .build())
                .build());

        // Set expectation for the activity
        mActivity.expectAutoFill("dude", "sweet");

        // Trigger auto-fill, make sure it's showing initially.
        assertSuggestionShownBySelectViewId(ID_USERNAME, "auth");
        InstrumentedAutoFillService.FillRequest fillRequest = sReplier.getNextFillRequest();
        final InlineSuggestionsRequest inlineSuggestionsRequest = fillRequest.inlineRequest;

        // Tap authentication request.
        mUiBot.selectDataset("auth");
        mUiBot.waitForIdle();

        // Check the results.
        mActivity.assertAutoFilled();
        mUiBot.waitForIdle();

        // Now trigger save.
        mActivity.onUsername((v) -> v.setText("malkovich"));
        mUiBot.waitForIdle();
        mActivity.onPassword((v) -> v.setText("malkovich"));
        mUiBot.waitForIdle();


        final String expectedMessage = getWelcomeMessage("malkovich");
        final String actualMessage = mActivity.tapLogin();
        mUiBot.waitForIdle();
        assertWithMessage("Wrong welcome msg").that(actualMessage).isEqualTo(expectedMessage);

        mUiBot.updateForAutofill(/* yesDoIt */ true, SAVE_DATA_TYPE_PASSWORD);
        mUiBot.waitForIdle();

        // Assert client state on authentication activity.
        Helper.assertAuthenticationClientState("auth activity", AuthenticationActivity.getData(),
                "CSI", "FromResponse");
        assertWithMessage("Auth intent extras should contain InlineSuggestionsRequest")
                .that(AuthenticationActivity.getInlineSuggestionsRequest())
                .isEqualTo(inlineSuggestionsRequest);

        // Assert client state on save request.
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        final String expectedValue = where == ClientStateLocation.FILL_RESPONSE_ONLY
                ? "FromResponse" : "FromIntent";
        Helper.assertAuthenticationClientState("on save", saveRequest.data, "CSI", expectedValue);
    }

    private void assertSuggestionShownBySelectViewId(String id, String...names)
            throws Exception {
        mUiBot.selectByRelativeId(id);
        mUiBot.waitForIdle();
        mUiBot.assertDatasets(names);
    }
}
