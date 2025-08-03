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
import static android.autofillservice.cts.testcore.AugmentedHelper.assertBasicRequestInfo;
import static android.autofillservice.cts.testcore.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;

import static com.google.common.truth.Truth.assertThat;

import android.autofillservice.cts.activities.AugmentedAuthActivity;
import android.autofillservice.cts.activities.AugmentedLoginActivity;
import android.autofillservice.cts.commontests.AugmentedAutofillAutoActivityLaunchTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedAugmentedFillResponse;
import android.autofillservice.cts.testcore.CtsAugmentedAutofillService;
import android.autofillservice.cts.testcore.InlineUiBot;
import android.content.IntentSender;
import android.platform.test.annotations.Presubmit;
import android.service.autofill.Dataset;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.EditText;

import androidx.test.filters.FlakyTest;

import org.junit.Test;
import org.junit.rules.TestRule;

@Presubmit
public class InlineAugmentedAuthTest
        extends AugmentedAutofillAutoActivityLaunchTestCase<AugmentedLoginActivity> {

    protected AugmentedLoginActivity mActivity;

    public InlineAugmentedAuthTest() {
        super(getInlineUiBot());
    }

    @Override
    protected AutofillActivityTestRule<AugmentedLoginActivity> getActivityRule() {
        return new AutofillActivityTestRule<AugmentedLoginActivity>(AugmentedLoginActivity.class) {
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

    @FlakyTest(
            bugId = 292281375,
            detail = "Meet July-31-23 trunk stable no flaky SLO. Deflake asap")
    @Test
    public void testDatasetAuth_resultOk_validDataset() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText unField = mActivity.getUsername();
        final AutofillId unFieldId = unField.getAutofillId();
        final AutofillValue unValue = unField.getAutofillValue();
        sReplier.addResponse(NO_RESPONSE);
        Dataset authResult = new Dataset.Builder(createInlinePresentation("auth"))
                .setId("dummyId")
                .setValue(unFieldId, AutofillValue.forText("Auth Result"))
                .build();
        IntentSender authAction = AugmentedAuthActivity.createSender(mContext, 1,
                authResult, null, RESULT_OK);
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("bla").build(),
                        unFieldId)
                .addInlineSuggestion(new CannedAugmentedFillResponse.Dataset.Builder("inline")
                        .setField(unFieldId, "John Smith", createInlinePresentation("John"))
                        .setAuthentication(authAction)
                        .build())
                .build());

        // Trigger autofill request
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        CtsAugmentedAutofillService.AugmentedFillRequest request1 =
                sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(request1, mActivity, unFieldId, unValue);

        // Confirm suggestions
        mUiBot.assertDatasets("John");

        // Tap on suggestion
        mUiBot.selectDataset("John");
        mUiBot.waitForIdle();

        // Tap on the auth activity button and assert that the dataset from the auth activity is
        // filled into the field.
        mActivity.expectAutoFill("Auth Result");
        mUiBot.selectByRelativeId(AugmentedAuthActivity.ID_AUTH_ACTIVITY_BUTTON);
        mUiBot.waitForIdle();
        mActivity.assertAutoFilled();
        assertThat(unField.getText().toString()).isEqualTo("Auth Result");
        mUiBot.assertNoDatasets();
    }

    @Test
    public void testDatasetAuth_resultOk_nullDataset() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText unField = mActivity.getUsername();
        final AutofillId unFieldId = unField.getAutofillId();
        final AutofillValue unValue = unField.getAutofillValue();
        sReplier.addResponse(NO_RESPONSE);
        IntentSender authAction = AugmentedAuthActivity.createSender(mContext, 1,
                null, null, RESULT_OK);
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("bla").build(),
                        unFieldId)
                .addInlineSuggestion(new CannedAugmentedFillResponse.Dataset.Builder("inline")
                        .setField(unFieldId, "John Smith", createInlinePresentation("John"))
                        .setAuthentication(authAction)
                        .build())
                .build());

        // Trigger autofill request
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        CtsAugmentedAutofillService.AugmentedFillRequest request1 =
                sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(request1, mActivity, unFieldId, unValue);

        // Confirm suggestions
        mUiBot.assertDatasets("John");

        // Tap on suggestion
        mUiBot.selectDataset("John");
        mUiBot.waitForIdle();

        // Tap on the auth activity button and assert that the field is left unchanged (since the
        // dataset returned from the auth activity is null).
        mUiBot.selectByRelativeId(AugmentedAuthActivity.ID_AUTH_ACTIVITY_BUTTON);
        mUiBot.waitForIdle();
        assertThat(unField.getText().toString()).isEqualTo("");
    }

    @FlakyTest(bugId = 244112879)
    @Test
    public void testDatasetAuth_resultCancel() throws Exception {
        // Set services
        enableService();
        enableAugmentedService();

        // Set expectations
        final EditText unField = mActivity.getUsername();
        final AutofillId unFieldId = unField.getAutofillId();
        final AutofillValue unValue = unField.getAutofillValue();
        sReplier.addResponse(NO_RESPONSE);
        Dataset authResult = new Dataset.Builder(createInlinePresentation("auth"))
                .setId("dummyId")
                .setValue(unFieldId, AutofillValue.forText("Auth Result"))
                .build();
        IntentSender authAction = AugmentedAuthActivity.createSender(mContext, 1,
                authResult, null, RESULT_CANCELED);
        sAugmentedReplier.addResponse(new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("bla").build(),
                        unFieldId)
                .addInlineSuggestion(new CannedAugmentedFillResponse.Dataset.Builder("inline")
                        .setField(unFieldId, "John Smith", createInlinePresentation("John"))
                        .setAuthentication(authAction)
                        .build())
                .build());

        // Trigger autofill request
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        CtsAugmentedAutofillService.AugmentedFillRequest request1 =
                sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(request1, mActivity, unFieldId, unValue);

        // Confirm suggestions
        mUiBot.assertDatasets("John");

        // Tap on suggestion
        mUiBot.selectDataset("John");
        mUiBot.waitForIdle();

        // Tap on the auth activity button and assert that the field is left unchanged (since the
        // result code returned by the auth activity is RESULT_CANCELED).
        mUiBot.selectByRelativeId(AugmentedAuthActivity.ID_AUTH_ACTIVITY_BUTTON);
        mUiBot.waitForIdle();
        assertThat(unField.getText().toString()).isEqualTo("");

        // Return from the auth activity to login activity, if the login onResume() is prior to
        // the test finished, there is another FillRequest() will be received. Because it may
        // notifyViewEntered() in onResume().
        mUiBot.assertShownByRelativeId(ID_USERNAME);
        sAugmentedReplier.getNextFillRequest();
    }
}
