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

import static android.app.Activity.RESULT_OK;
import static android.autofillservice.cts.testcore.AugmentedHelper.assertBasicRequestInfo;
import static android.autofillservice.cts.testcore.CannedFillResponse.NO_RESPONSE;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;

import android.autofillservice.cts.activities.AugmentedAuthActivity;
import android.autofillservice.cts.activities.AugmentedLoginActivity;
import android.autofillservice.cts.commontests.AugmentedAutofillAutoActivityLaunchTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedAugmentedFillResponse;
import android.autofillservice.cts.testcore.CtsAugmentedAutofillService;
import android.autofillservice.cts.testcore.InlineUiBot;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.IntentSender;
import android.net.Uri;
import android.platform.test.annotations.Presubmit;
import android.service.autofill.Dataset;
import android.view.ContentInfo;
import android.view.OnReceiveContentListener;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.filters.FlakyTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.concurrent.atomic.AtomicInteger;

@Presubmit
public class InlineAugmentedContentTest
        extends AugmentedAutofillAutoActivityLaunchTestCase<AugmentedLoginActivity> {
    private static final String TAG = "AutofillTest";

    private static final AtomicInteger SUFFIX_COUNTER = new AtomicInteger(1);

    private AugmentedLoginActivity mActivity;
    private ContentResolver mContentResolver;
    private MyContentReceiver mMyContentReceiver;

    public InlineAugmentedContentTest() {
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

    @Before
    public void before() throws Exception {
        mContentResolver = mContext.getContentResolver();
        mMyContentReceiver = new MyContentReceiver();
        enableService();
        enableAugmentedService();
        sReplier.addResponse(NO_RESPONSE);
    }

    @FlakyTest(
            bugId = 292285491,
            detail = "Meet July-31-23 trunk stable no flaky SLO. Deflake asap")
    @Test
    public void testFillContent_text() throws Exception {
        final String suggestion = "Sample Text";

        // Set expectations
        final EditText targetField = mActivity.getUsername();
        targetField.setOnReceiveContentListener(MyContentReceiver.MIME_TYPES, mMyContentReceiver);
        final AutofillId targetFieldId = targetField.getAutofillId();
        final AutofillValue targetFieldContents = targetField.getAutofillValue();
        sAugmentedReplier.addResponse(suggestion(targetFieldId, suggestion));

        // Trigger autofill request
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        CtsAugmentedAutofillService.AugmentedFillRequest augRequest =
                sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(augRequest, mActivity, targetFieldId, targetFieldContents);

        // Confirm suggestions
        mUiBot.assertDatasets(suggestion);

        // Tap on suggestion
        mActivity.expectAutoFill(suggestion);
        mUiBot.selectDataset(suggestion);
        mUiBot.waitForIdleSync();
        mActivity.assertAutoFilled();
    }

    @Test
    public void testFillContent_contentUri() throws Exception {
        final String suggestionTitle = "Sample Image";
        final Uri suggestionUri = sampleContentUri();
        final String suggestionMimeType = "image/png";

        // Set expectations
        final EditText targetField = mActivity.getUsername();
        targetField.setOnReceiveContentListener(MyContentReceiver.MIME_TYPES, mMyContentReceiver);
        final AutofillId targetFieldId = targetField.getAutofillId();
        final AutofillValue targetFieldContents = targetField.getAutofillValue();
        sAugmentedReplier.addResponse(
                suggestion(targetFieldId, suggestionTitle, suggestionUri, suggestionMimeType));

        // Trigger autofill request
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        CtsAugmentedAutofillService.AugmentedFillRequest augRequest =
                sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(augRequest, mActivity, targetFieldId, targetFieldContents);

        // Confirm suggestions
        mUiBot.assertDatasets(suggestionTitle);

        // Tap on suggestion
        mActivity.expectAutoFill(MyContentReceiver.TEXT_FILLED_FOR_URI);
        mUiBot.selectDataset(suggestionTitle);
        mUiBot.waitForIdleSync();
        mActivity.assertAutoFilled();
    }

    @Test
    public void testFillContent_contentUri_authFlow() throws Exception {
        final String suggestionTitle = "Sample Image";
        final Uri suggestionUri = sampleContentUri();
        final String suggestionMimeType = "image/png";

        // Set expectations
        final EditText targetField = mActivity.getUsername();
        targetField.setOnReceiveContentListener(MyContentReceiver.MIME_TYPES, mMyContentReceiver);
        final AutofillId targetFieldId = targetField.getAutofillId();
        final AutofillValue targetFieldContents = targetField.getAutofillValue();
        ClipData suggestionBeforeAuth = ClipData.newPlainText(suggestionTitle, "before auth");
        ClipData suggestionAfterAuth = new ClipData(suggestionTitle,
                new String[]{suggestionMimeType}, new ClipData.Item(suggestionUri));
        sAugmentedReplier.addResponse(
                suggestionWithAuthFlow(targetFieldId, suggestionBeforeAuth, suggestionAfterAuth));

        // Trigger autofill request
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdle();
        sReplier.getNextFillRequest();
        CtsAugmentedAutofillService.AugmentedFillRequest augRequest =
                sAugmentedReplier.getNextFillRequest();

        // Assert request
        assertBasicRequestInfo(augRequest, mActivity, targetFieldId, targetFieldContents);

        // Confirm suggestions
        mUiBot.assertDatasets(suggestionTitle);

        // Tap on suggestion
        mUiBot.selectDataset(suggestionTitle);
        mUiBot.waitForIdle();

        // Tap on the auth activity button and assert that the dataset from the auth activity is
        // filled into the field.
        mActivity.expectAutoFill(MyContentReceiver.TEXT_FILLED_FOR_URI);
        mUiBot.selectByRelativeId(AugmentedAuthActivity.ID_AUTH_ACTIVITY_BUTTON);
        mUiBot.waitForIdleSync();
        mActivity.assertAutoFilled();
    }

    private static CannedAugmentedFillResponse suggestion(AutofillId targetFieldId,
            String suggestion) {
        ClipData clip = ClipData.newPlainText(suggestion, suggestion);
        return suggestion(targetFieldId, clip);
    }

    private static CannedAugmentedFillResponse suggestion(AutofillId targetFieldId,
            String suggestionTitle, Uri suggestion, String suggestionMimeType) {
        ClipData clip = new ClipData(suggestionTitle,
                new String[]{suggestionMimeType}, new ClipData.Item(suggestion));
        return suggestion(targetFieldId, clip);
    }

    private static CannedAugmentedFillResponse suggestion(AutofillId targetFieldId,
            ClipData content) {
        return new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("inline").build(),
                        targetFieldId)
                .addInlineSuggestion(new CannedAugmentedFillResponse.Dataset.Builder("inline")
                        .setContent(targetFieldId, content)
                        .build())
                .build();
    }

    private CannedAugmentedFillResponse suggestionWithAuthFlow(AutofillId targetFieldId,
            ClipData contentBeforeAuth, ClipData contentAfterAuth) {
        Dataset authResult = new Dataset.Builder(createInlinePresentation("auth"))
                .setId("dummyId")
                .setContent(targetFieldId, contentAfterAuth)
                .build();
        IntentSender authAction = AugmentedAuthActivity.createSender(mContext, 1,
                authResult, null, RESULT_OK);
        return new CannedAugmentedFillResponse.Builder()
                .setDataset(new CannedAugmentedFillResponse.Dataset.Builder("inline").build(),
                        targetFieldId)
                .addInlineSuggestion(new CannedAugmentedFillResponse.Dataset.Builder("inline")
                        .setContent(targetFieldId, contentBeforeAuth)
                        .setAuthentication(authAction)
                        .build())
                .build();
    }

    private static Uri sampleContentUri() {
        String uniqueSuffix = System.currentTimeMillis() + "_" + SUFFIX_COUNTER.getAndIncrement();
        return Uri.parse(ContentResolver.SCHEME_CONTENT + "://example/" + uniqueSuffix);
    }

    private static final class MyContentReceiver implements OnReceiveContentListener {
        public static final String[] MIME_TYPES = new String[]{"image/*"};
        public static final String TEXT_FILLED_FOR_URI = "uri";

        @Override
        public ContentInfo onReceiveContent(View view, ContentInfo payload) {
            StringBuilder sb = new StringBuilder();
            ClipData clip = payload.getClip();
            for (int i = 0; i < clip.getItemCount(); i++) {
                if (i > 0) {
                    sb.append("\n");
                }
                ClipData.Item item = clip.getItemAt(i);
                if (item.getUri() != null) {
                    sb.append(TEXT_FILLED_FOR_URI);
                } else {
                    sb.append(item.getText());
                }
            }
            ((TextView) view).setText(sb.toString());
            return null;
        }
    }
}
