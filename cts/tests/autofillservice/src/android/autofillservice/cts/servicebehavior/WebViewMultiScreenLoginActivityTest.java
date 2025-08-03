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
package android.autofillservice.cts.servicebehavior;

import static android.autofillservice.cts.activities.AbstractWebViewActivity.HTML_NAME_PASSWORD;
import static android.autofillservice.cts.activities.AbstractWebViewActivity.HTML_NAME_USERNAME;
import static android.autofillservice.cts.testcore.CustomDescriptionHelper.newCustomDescriptionWithUsernameAndPassword;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD_LABEL;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME_LABEL;
import static android.autofillservice.cts.testcore.Helper.assertTextAndValue;
import static android.autofillservice.cts.testcore.Helper.findNodeByHtmlName;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_USERNAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.autofillservice.cts.R;
import android.autofillservice.cts.activities.MyWebView;
import android.autofillservice.cts.activities.WebViewMultiScreenLoginActivity;
import android.autofillservice.cts.commontests.AbstractWebViewTestCase;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.FillRequest;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.SaveRequest;
import android.content.ComponentName;
import android.service.autofill.CharSequenceTransformation;
import android.service.autofill.SaveInfo;
import android.util.Log;
import android.view.KeyEvent;
import android.view.autofill.AutofillId;

import androidx.test.uiautomator.UiObject2;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class WebViewMultiScreenLoginActivityTest
        extends AbstractWebViewTestCase<WebViewMultiScreenLoginActivity> {

    private static final String TAG = "WebViewMultiScreenLoginTest";

    private static final Pattern MATCH_ALL = Pattern.compile("^(.*)$");

    private WebViewMultiScreenLoginActivity mActivity;

    @Override
    protected AutofillActivityTestRule<WebViewMultiScreenLoginActivity> getActivityRule() {
        return new AutofillActivityTestRule<WebViewMultiScreenLoginActivity>(
                WebViewMultiScreenLoginActivity.class) {

            // TODO(b/111838239): latest WebView implementation calls AutofillManager.isEnabled() to
            // disable autofill for optimization when it returns false, and unfortunately the value
            // returned by that method does not change when the service is enabled / disabled, so we
            // need to start enable the service before launching the activity.
            // Once that's fixed, remove this overridden method.
            @Override
            protected void beforeActivityLaunched() {
                super.beforeActivityLaunched();
                Log.i(TAG, "Setting service before launching the activity");
                enableService();
            }

            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @Test
    public void testSave_eachFieldSeparately() throws Exception {
        // Set service.
        enableService();

        // Load WebView
        final MyWebView myWebView = mActivity.loadWebView(mUiBot);
        // Validation check to make sure autofill is enabled in the application context
        Helper.assertAutofillEnabled(myWebView.getContext(), true);

        /*
         * First screen: username
         */
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME, HTML_NAME_USERNAME)
                .setSaveInfoDecorator((builder, nodeResolver) -> {
                    final AutofillId usernameId = nodeResolver.apply(HTML_NAME_USERNAME);
                    final CharSequenceTransformation usernameTrans = new CharSequenceTransformation
                            .Builder(usernameId, MATCH_ALL, "$1").build();
                    builder.setCustomDescription(newCustomDescriptionWithUsernameAndPassword()
                            .addChild(R.id.username, usernameTrans)
                            .build());
                })
                .build());

        // Trigger autofill.
        mActivity.getUsernameInput().click();
        mUiBot.waitForIdle();

        // check received request and no suggestion shown
        final FillRequest fillRequest1 = sReplier.getNextFillRequest();
        assertThat(fillRequest1.contexts).hasSize(1);
        mUiBot.assertNoDatasetsEver();

        // Now trigger save.
        if (INJECT_EVENTS) {
            mActivity.getUsernameInput().click();
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_D);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_U);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_D);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_E);
        } else {
            mActivity.getUsernameInput().setText("dude");
        }
        mActivity.getNextButton().click();
        mUiBot.waitForIdle();

        // Assert save UI shown.
        final UiObject2 saveUi1 = mUiBot.assertSaveShowing(
                SaveInfo.NEGATIVE_BUTTON_STYLE_CANCEL, null, SAVE_DATA_TYPE_USERNAME);
        mUiBot.assertChildText(saveUi1, ID_USERNAME_LABEL, "User:");
        mUiBot.assertChildText(saveUi1, ID_USERNAME, "dude");

        // Save then assert save request and saveui disappear.
        mUiBot.saveForAutofill(saveUi1, true);
        final SaveRequest saveRequest1 = sReplier.getNextSaveRequest();
        assertThat(saveRequest1.contexts).hasSize(1);
        assertTextAndValue(findNodeByHtmlName(saveRequest1.structure, HTML_NAME_USERNAME), "dude");
        mUiBot.assertSaveNotShowing();

        /*
         * Second screen: password
         */
        mActivity.waitForPasswordScreen(mUiBot);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, HTML_NAME_PASSWORD)
                .setSaveInfoDecorator((builder, nodeResolver) -> {
                    final AutofillId passwordId = nodeResolver.apply(HTML_NAME_PASSWORD);
                    final CharSequenceTransformation passwordTrans = new CharSequenceTransformation
                            .Builder(passwordId, MATCH_ALL, "$1").build();
                    builder.setCustomDescription(newCustomDescriptionWithUsernameAndPassword()
                            .addChild(R.id.password, passwordTrans)
                            .build());
                })
                .build());

        // Trigger autofill.
        mActivity.getPasswordInput().click();
        mUiBot.waitForIdle();

        // check received request and no suggestion shown
        final FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertThat(fillRequest2.contexts).hasSize(1);
        mUiBot.assertNoDatasetsEver();

        // Now trigger save.
        if (INJECT_EVENTS) {
            mActivity.getPasswordInput().click();
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_S);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_W);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_E);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_E);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_T);
        } else {
            mActivity.getPasswordInput().setText("sweet");
        }
        mActivity.getLoginButton().click();
        mUiBot.waitForIdle();

        // Assert save UI shown.
        final UiObject2 saveUi2 = mUiBot.assertSaveShowing(
                SaveInfo.NEGATIVE_BUTTON_STYLE_CANCEL, null, SAVE_DATA_TYPE_PASSWORD);
        mUiBot.assertChildText(saveUi2, ID_PASSWORD_LABEL, "Pass:");
        mUiBot.assertChildText(saveUi2, ID_PASSWORD, "sweet");

        // Save then assert save request and saveui disappear.
        mUiBot.saveForAutofill(saveUi2, true);
        final SaveRequest saveRequest2 = sReplier.getNextSaveRequest();
        assertThat(saveRequest2.contexts).hasSize(1);
        assertTextAndValue(findNodeByHtmlName(saveRequest2.structure, HTML_NAME_PASSWORD), "sweet");
        mUiBot.assertSaveNotShowing();
    }

    @Test
    public void testSave_bothFieldsAtOnce() throws Exception {
        // Set service.
        enableService();

        // Load WebView
        final MyWebView myWebView = mActivity.loadWebView(mUiBot);
        // Validation check to make sure autofill is enabled in the application context
        Helper.assertAutofillEnabled(myWebView.getContext(), true);

        /*
         * First screen: username
         */
        final AtomicReference<AutofillId> usernameId = new AtomicReference<>();
        sReplier.addResponse(new CannedFillResponse.Builder()
                .setIgnoreFields(HTML_NAME_USERNAME)
                .setSaveInfoFlags(SaveInfo.FLAG_DELAY_SAVE)
                .setSaveInfoDecorator((builder, nodeResolver) -> {
                    usernameId.set(nodeResolver.apply(HTML_NAME_USERNAME));

                })
                .build());

        // Trigger autofill.
        mActivity.getUsernameInput().click();
        mUiBot.waitForIdle();

        // check received request and no suggestion shown
        final FillRequest fillRequest1 = sReplier.getNextFillRequest();
        assertThat(fillRequest1.contexts).hasSize(1);
        mUiBot.assertNoDatasetsEver();

        // Change username to trigger save.
        if (INJECT_EVENTS) {
            mActivity.getUsernameInput().click();
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_D);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_U);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_D);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_E);
        } else {
            mActivity.getUsernameInput().setText("dude");
        }
        mActivity.getNextButton().click();
        mUiBot.waitForIdle();

        // Assert save UI was not shown.
        mUiBot.assertSaveNotShowing();

        /*
         * Second screen: password
         */
        mActivity.waitForPasswordScreen(mUiBot);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME | SAVE_DATA_TYPE_PASSWORD,
                        HTML_NAME_PASSWORD)
                .setSaveInfoDecorator((builder, nodeResolver) -> {
                    final AutofillId passwordId = nodeResolver.apply(HTML_NAME_PASSWORD);
                    final CharSequenceTransformation usernameTrans = new CharSequenceTransformation
                            .Builder(usernameId.get(), MATCH_ALL, "$1").build();
                    final CharSequenceTransformation passwordTrans = new CharSequenceTransformation
                            .Builder(passwordId, MATCH_ALL, "$1").build();
                    Log.d(TAG, "setting CustomDescription: u=" + usernameId + ", p=" + passwordId);
                    builder.setCustomDescription(newCustomDescriptionWithUsernameAndPassword()
                            .addChild(R.id.username, usernameTrans)
                            .addChild(R.id.password, passwordTrans)
                            .build());
                })
                .build());

        // Trigger autofill.
        mActivity.getPasswordInput().click();
        mUiBot.waitForIdle();

        // check received request and no suggestion shown
        final FillRequest fillRequest2 = sReplier.getNextFillRequest();
        assertThat(fillRequest2.contexts).hasSize(2);
        mUiBot.assertNoDatasetsEver();

        // Now trigger save.
        if (INJECT_EVENTS) {
            mActivity.getPasswordInput().click();
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_S);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_W);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_E);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_E);
            mActivity.dispatchKeyPress(KeyEvent.KEYCODE_T);
        } else {
            mActivity.getPasswordInput().setText("sweet");
        }
        mActivity.getLoginButton().click();
        mUiBot.waitForIdle();

        // Assert save UI shown.
        final UiObject2 saveUi = mUiBot.assertSaveShowing(
                SaveInfo.NEGATIVE_BUTTON_STYLE_CANCEL, null, SAVE_DATA_TYPE_USERNAME,
                SAVE_DATA_TYPE_PASSWORD);
        mUiBot.assertChildText(saveUi, ID_PASSWORD_LABEL, "Pass:");
        mUiBot.assertChildText(saveUi, ID_PASSWORD, "sweet");
        mUiBot.assertChildText(saveUi, ID_USERNAME_LABEL, "User:");
        mUiBot.assertChildText(saveUi, ID_USERNAME, "dude");

        // Save then assert save request and saveui disappear.
        mUiBot.saveForAutofill(saveUi, true);
        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        mUiBot.assertSaveNotShowing();

        // Username is set in the 1st context
        final AssistStructure previousStructure = saveRequest.contexts.get(0).getStructure();
        assertWithMessage("no structure for 1st activity").that(previousStructure).isNotNull();
        assertTextAndValue(findNodeByHtmlName(previousStructure, HTML_NAME_USERNAME), "dude");
        final ComponentName componentPrevious = previousStructure.getActivityComponent();
        assertThat(componentPrevious).isEqualTo(mActivity.getComponentName());

        // Password is set in the 2nd context
        final AssistStructure currentStructure = saveRequest.contexts.get(1).getStructure();
        assertWithMessage("no structure for 2nd activity").that(currentStructure).isNotNull();
        assertTextAndValue(findNodeByHtmlName(currentStructure, HTML_NAME_PASSWORD), "sweet");
        final ComponentName componentCurrent = currentStructure.getActivityComponent();
        assertThat(componentCurrent).isEqualTo(mActivity.getComponentName());
    }
}
