/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.autofillservice.cts.saveui;

import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.assertActivityShownInBackground;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_PASSWORD;
import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_USERNAME;

import static org.junit.Assume.assumeTrue;

import android.autofillservice.cts.R;
import android.autofillservice.cts.activities.LoginActivity;
import android.autofillservice.cts.activities.LoginImportantForCredentialManagerActivity;
import android.autofillservice.cts.activities.LoginMixedImportantForCredentialManagerActivity;
import android.autofillservice.cts.activities.SimpleAfterLoginActivity;
import android.autofillservice.cts.activities.SimpleBeforeLoginActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.content.Context;
import android.content.Intent;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.View;

import com.android.compatibility.common.util.CddTest;

import org.junit.Rule;
import org.junit.Test;

/**
 * Tests whether autofill save dialog is shown as expected.
 */
public class AutofillSaveDialogTest extends AutoFillServiceTestCase.ManualActivityLaunch {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();


    // This does not assert that icon is actually hidden, this has to be done manually.
    @Test
    public void testShowSaveUiHideIcon() throws Exception {
        // Set service.
        enableService();

        // Start SimpleBeforeLoginActivity before login activity.
        startActivityWithFlag(mContext, SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_NEW_TASK);
        mUiBot.assertShownByRelativeId(SimpleBeforeLoginActivity.ID_BEFORE_LOGIN);

        // Start LoginActivity.
        startActivityWithFlag(SimpleBeforeLoginActivity.getCurrentActivity(), LoginActivity.class,
                /* flags= */ 0);
        mUiBot.assertShownByRelativeId(LoginActivity.ID_USERNAME_CONTAINER);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME, ID_USERNAME)
                .setShowSaveDialogIcon(false)
                .build());

        // Trigger autofill on username.
        LoginActivity loginActivity = LoginActivity.getCurrentActivity();
        loginActivity.onUsername(View::requestFocus);

        // Wait for fill request to be processed.
        sReplier.getNextFillRequest();

        // Set data.
        loginActivity.onUsername((v) -> v.setText("test"));

        // Start SimpleAfterLoginActivity after login activity.
        startActivityWithFlag(loginActivity, SimpleAfterLoginActivity.class, /* flags= */ 0);
        mUiBot.assertShownByRelativeId(SimpleAfterLoginActivity.ID_AFTER_LOGIN);

        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_USERNAME);

        // Restart SimpleBeforeLoginActivity with CLEAR_TOP and SINGLE_TOP.
        startActivityWithFlag(SimpleAfterLoginActivity.getCurrentActivity(),
                SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        assertActivityShownInBackground(SimpleBeforeLoginActivity.class);

        mUiBot.assertSaveShowing(SAVE_DATA_TYPE_USERNAME);
    }


    // This test can assert that the label has changed. Checking that the icon has changed
    // will need to be done manually.
    @Test
    public void testShowSaveUiCustomServiceLabel() throws Exception {
        // Disable this test for Automotive until we know why it's failing.
        // bug: 270482520
        assumeTrue("Skip Automotive", !Helper.isAutomotive(sContext));

         // Set service.
        enableService();

        // Start SimpleBeforeLoginActivity before login activity.
        startActivityWithFlag(mContext, SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_NEW_TASK);
        mUiBot.assertShownByRelativeId(SimpleBeforeLoginActivity.ID_BEFORE_LOGIN);

        // Start LoginActivity.
        startActivityWithFlag(SimpleBeforeLoginActivity.getCurrentActivity(), LoginActivity.class,
                /* flags= */ 0);
        mUiBot.assertShownByRelativeId(LoginActivity.ID_USERNAME_CONTAINER);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME, ID_USERNAME)
                .setIconResourceId(R.drawable.android)
                .setServiceDisplayNameResourceId(R.string.custom_service_name)
                .build());

        // Trigger autofill on username.
        LoginActivity loginActivity = LoginActivity.getCurrentActivity();
        loginActivity.onUsername(View::requestFocus);

        // Wait for fill request to be processed.
        sReplier.getNextFillRequest();

        // Set data.
        loginActivity.onUsername((v) -> v.setText("test"));

        // Start SimpleAfterLoginActivity after login activity.
        startActivityWithFlag(loginActivity, SimpleAfterLoginActivity.class, /* flags= */ 0);
        mUiBot.assertShownByRelativeId(SimpleAfterLoginActivity.ID_AFTER_LOGIN);

        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_USERNAME);

        // Restart SimpleBeforeLoginActivity with CLEAR_TOP and SINGLE_TOP.
        startActivityWithFlag(SimpleAfterLoginActivity.getCurrentActivity(),
                SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        assertActivityShownInBackground(SimpleBeforeLoginActivity.class);

        // Verify save ui dialog with custom service name
        mUiBot.assertSaveShowingWithCustomServiceName(SAVE_DATA_TYPE_USERNAME,
                mContext.getResources().getString(R.string.custom_service_name));
    }

    @Test
    public void testShowSaveUiWhenLaunchActivityWithFlagClearTopAndSingleTop() throws Exception {
        // Set service.
        enableService();

        // Start SimpleBeforeLoginActivity before login activity.
        startActivityWithFlag(mContext, SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_NEW_TASK);
        mUiBot.assertShownByRelativeId(SimpleBeforeLoginActivity.ID_BEFORE_LOGIN);

        // Start LoginActivity.
        startActivityWithFlag(SimpleBeforeLoginActivity.getCurrentActivity(), LoginActivity.class,
                /* flags= */ 0);
        mUiBot.assertShownByRelativeId(LoginActivity.ID_USERNAME_CONTAINER);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME, ID_USERNAME)
                .build());

        // Trigger autofill on username.
        LoginActivity loginActivity = LoginActivity.getCurrentActivity();
        loginActivity.onUsername(View::requestFocus);

        // Wait for fill request to be processed.
        sReplier.getNextFillRequest();

        // Set data.
        loginActivity.onUsername((v) -> v.setText("test"));

        // Start SimpleAfterLoginActivity after login activity.
        startActivityWithFlag(loginActivity, SimpleAfterLoginActivity.class, /* flags= */ 0);
        mUiBot.assertShownByRelativeId(SimpleAfterLoginActivity.ID_AFTER_LOGIN);

        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_USERNAME);

        // Restart SimpleBeforeLoginActivity with CLEAR_TOP and SINGLE_TOP.
        startActivityWithFlag(SimpleAfterLoginActivity.getCurrentActivity(),
                SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        assertActivityShownInBackground(SimpleBeforeLoginActivity.class);

        // Verify save ui dialog.
        mUiBot.assertSaveShowing(SAVE_DATA_TYPE_USERNAME);
    }

    @Test
    public void testShowSaveUiWhenLaunchActivityWithFlagClearTaskAndNewTask() throws Exception {
        // Set service.
        enableService();

        // Start SimpleBeforeLoginActivity before login activity.
        startActivityWithFlag(mContext, SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_NEW_TASK);
        mUiBot.assertShownByRelativeId(SimpleBeforeLoginActivity.ID_BEFORE_LOGIN);

        // Start LoginActivity.
        startActivityWithFlag(SimpleBeforeLoginActivity.getCurrentActivity(), LoginActivity.class,
                /* flags= */ 0);
        mUiBot.assertShownByRelativeId(LoginActivity.ID_USERNAME_CONTAINER);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME, ID_USERNAME)
                .build());

        // Trigger autofill on username.
        LoginActivity loginActivity = LoginActivity.getCurrentActivity();
        loginActivity.onUsername(View::requestFocus);

        // Wait for fill request to be processed.
        sReplier.getNextFillRequest();

        // Set data.
        loginActivity.onUsername((v) -> v.setText("test"));

        // Start SimpleAfterLoginActivity with CLEAR_TASK and NEW_TASK after login activity.
        startActivityWithFlag(loginActivity, SimpleAfterLoginActivity.class,
                Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        assertActivityShownInBackground(SimpleAfterLoginActivity.class);

        // Verify save ui dialog.
        mUiBot.assertSaveShowing(SAVE_DATA_TYPE_USERNAME);
    }

    @Test
    @CddTest(requirement = "9.8.14/C1-1")
    // This test asserts save dialog is suppressed when there is credman field in screen
    public void testSuppressSaveDialog_onOnlyCredmanFields_withIsCredential() throws Exception {
        testSuppressSaveDialog_OnCredmanOnlyFields(false);
    }

    @Test
    @CddTest(requirement = "9.8.14/C1-1")
    // This test asserts save dialog is suppressed when there is credman field in screen
    public void testSuppressSaveDialog_onOnlyCredmanFields_withAutofillHint() throws Exception {
        testSuppressSaveDialog_OnCredmanOnlyFields(true);
    }

    @Test
    @CddTest(requirement = "9.8.14/C1-1")
    // This test asserts save dialog is suppressed when there is both credman and non-credman fields
    // in activity
    public void testSuppressSaveDialog_onMixedFields_withIsCredential()
            throws Exception {
        testSuppressSaveDialog_onMixedFields(false);
    }

    @Test
    @CddTest(requirement = "9.8.14/C1-1")
    // This test asserts save dialog is suppressed when there is both credman and non-credman fields
    // in activity
    public void testSuppressSaveDialog_onMixedFields_withAutofillHint()
            throws Exception {
        testSuppressSaveDialog_onMixedFields(true);
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.ignore_view_state_reset_to_empty")
    public void testShowSaveUiAfterLoginViewReset() throws Exception {
        // Set service.
        enableService();

        // Start SimpleBeforeLoginActivity before login activity.
        startActivityWithFlag(mContext, SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_NEW_TASK);
        mUiBot.assertShownByRelativeId(SimpleBeforeLoginActivity.ID_BEFORE_LOGIN);

        // Start LoginActivity.
        startActivityWithFlag(SimpleBeforeLoginActivity.getCurrentActivity(), LoginActivity.class,
                /* flags= */ 0);
        mUiBot.assertShownByRelativeId(LoginActivity.ID_USERNAME_CONTAINER);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME, ID_USERNAME)
                .build());

        // Trigger autofill on username.
        LoginActivity loginActivity = LoginActivity.getCurrentActivity();
        loginActivity.onUsername(View::requestFocus);

        // Wait for fill request to be processed.
        sReplier.getNextFillRequest();

        // Set data.
        loginActivity.onUsername((v) -> v.setText("test"));

        // Reset view
        loginActivity.onUsername((v) -> v.setText(""));

        // Start SimpleAfterLoginActivity after login activity.
        startActivityWithFlag(loginActivity, SimpleAfterLoginActivity.class, /* flags= */ 0);
        mUiBot.assertShownByRelativeId(SimpleAfterLoginActivity.ID_AFTER_LOGIN);

        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_USERNAME);

        // Restart SimpleBeforeLoginActivity with CLEAR_TOP and SINGLE_TOP.
        startActivityWithFlag(SimpleAfterLoginActivity.getCurrentActivity(),
                SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        assertActivityShownInBackground(SimpleBeforeLoginActivity.class);

        // Verify save ui dialog.
        mUiBot.assertSaveShowing(SAVE_DATA_TYPE_USERNAME);
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.ignore_view_state_reset_to_empty")
    public void testDontShowSaveUiIfViewIsResetToEmptyProgressively() throws Exception {
        // Set service.
        enableService();

        // Start SimpleBeforeLoginActivity before login activity.
        startActivityWithFlag(mContext, SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_NEW_TASK);
        mUiBot.assertShownByRelativeId(SimpleBeforeLoginActivity.ID_BEFORE_LOGIN);

        // Start LoginActivity.
        startActivityWithFlag(SimpleBeforeLoginActivity.getCurrentActivity(), LoginActivity.class,
                /* flags= */ 0);
        mUiBot.assertShownByRelativeId(LoginActivity.ID_USERNAME_CONTAINER);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME, ID_USERNAME)
                .build());

        // Trigger autofill on username.
        LoginActivity loginActivity = LoginActivity.getCurrentActivity();
        loginActivity.onUsername(View::requestFocus);

        // Wait for fill request to be processed.
        sReplier.getNextFillRequest();

        // Set data.
        loginActivity.onUsername((v) -> v.setText("test"));
        // Reset view progressively
        loginActivity.onUsername((v) -> v.setText("tes"));
        loginActivity.onUsername((v) -> v.setText("te"));
        loginActivity.onUsername((v) -> v.setText("t"));
        loginActivity.onUsername((v) -> v.setText(""));

        // Start SimpleAfterLoginActivity after login activity.
        startActivityWithFlag(loginActivity, SimpleAfterLoginActivity.class, /* flags= */ 0);
        mUiBot.assertShownByRelativeId(SimpleAfterLoginActivity.ID_AFTER_LOGIN);

        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_USERNAME);

        // Restart SimpleBeforeLoginActivity with CLEAR_TOP and SINGLE_TOP.
        startActivityWithFlag(SimpleAfterLoginActivity.getCurrentActivity(),
                SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        assertActivityShownInBackground(SimpleBeforeLoginActivity.class);

        // Verify save ui dialog.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_USERNAME);
    }

    @Test
    @RequiresFlagsDisabled("android.service.autofill.ignore_view_state_reset_to_empty")
    public void testDontShowSaveUiAfterLoginViewResetIfFlagNotSet() throws Exception {
        // Set service.
        enableService();

        // Start SimpleBeforeLoginActivity before login activity.
        startActivityWithFlag(mContext, SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_NEW_TASK);
        mUiBot.assertShownByRelativeId(SimpleBeforeLoginActivity.ID_BEFORE_LOGIN);

        // Start LoginActivity.
        startActivityWithFlag(SimpleBeforeLoginActivity.getCurrentActivity(), LoginActivity.class,
                /* flags= */ 0);
        mUiBot.assertShownByRelativeId(LoginActivity.ID_USERNAME_CONTAINER);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME, ID_USERNAME)
                .build());

        // Trigger autofill on username.
        LoginActivity loginActivity = LoginActivity.getCurrentActivity();
        loginActivity.onUsername(View::requestFocus);

        // Wait for fill request to be processed.
        sReplier.getNextFillRequest();

        // Set data.
        loginActivity.onUsername((v) -> v.setText("test"));

        // Reset view
        loginActivity.onUsername((v) -> v.setText(""));

        // Start SimpleAfterLoginActivity after login activity.
        startActivityWithFlag(loginActivity, SimpleAfterLoginActivity.class, /* flags= */ 0);
        mUiBot.assertShownByRelativeId(SimpleAfterLoginActivity.ID_AFTER_LOGIN);

        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_USERNAME);

        // Restart SimpleBeforeLoginActivity with CLEAR_TOP and SINGLE_TOP.
        startActivityWithFlag(SimpleAfterLoginActivity.getCurrentActivity(),
                SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        assertActivityShownInBackground(SimpleBeforeLoginActivity.class);

        // Verify save ui dialog not shown.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_USERNAME);
    }

    private void testSuppressSaveDialog_OnCredmanOnlyFields(boolean useAutofillHint)
            throws Exception {
        // Set service.
        enableService();

        // Start SimpleBeforeLoginActivity before login activity.
        startActivityWithFlag(mContext, SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_NEW_TASK);
        mUiBot.assertShownByRelativeId(SimpleBeforeLoginActivity.ID_BEFORE_LOGIN);

        // Start LoginActivity.
        startActivityWithFlag(SimpleBeforeLoginActivity.getCurrentActivity(),
                LoginImportantForCredentialManagerActivity.class, /* flags= */ 0, useAutofillHint);
        mUiBot.assertShownByRelativeId(
                LoginImportantForCredentialManagerActivity.ID_USERNAME_CONTAINER);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_USERNAME, ID_USERNAME)
                .build());

        // Trigger autofill on username.
        LoginImportantForCredentialManagerActivity loginActivity =
                LoginImportantForCredentialManagerActivity.getCurrentActivity();
        loginActivity.onUsername(View::requestFocus);

        // Wait for fill request to be processed.
        sReplier.getNextFillRequest();

        // Set data.
        loginActivity.onUsername((v) -> v.setText("test"));

        // Start SimpleAfterLoginActivity with CLEAR_TASK and NEW_TASK after login activity.
        startActivityWithFlag(loginActivity, SimpleAfterLoginActivity.class,
                Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        assertActivityShownInBackground(SimpleAfterLoginActivity.class);

        // Verify save ui dialog.
        mUiBot.assertSaveNotShowing();
    }

    private void testSuppressSaveDialog_onMixedFields(boolean useAutofillHint)
            throws Exception {
        // Set service.
        enableService();

        // Start SimpleBeforeLoginActivity before login activity.
        startActivityWithFlag(mContext, SimpleBeforeLoginActivity.class,
                Intent.FLAG_ACTIVITY_NEW_TASK);
        mUiBot.assertShownByRelativeId(SimpleBeforeLoginActivity.ID_BEFORE_LOGIN);

        // Start LoginActivity.
        startActivityWithFlag(SimpleBeforeLoginActivity.getCurrentActivity(),
                LoginMixedImportantForCredentialManagerActivity.class, /* flags= */ 0,
                useAutofillHint);
        mUiBot.assertShownByRelativeId(
                LoginMixedImportantForCredentialManagerActivity.ID_USERNAME_CONTAINER);

        sReplier.addResponse(new CannedFillResponse.Builder()
                .setRequiredSavableIds(SAVE_DATA_TYPE_PASSWORD, ID_USERNAME, ID_PASSWORD)
                .build());

        // Trigger autofill on username.
        LoginMixedImportantForCredentialManagerActivity loginActivity =
                LoginMixedImportantForCredentialManagerActivity.getCurrentActivity();
        loginActivity.onUsername(View::requestFocus);

        // Wait for fill request to be processed.
        sReplier.getNextFillRequest();

        // Set data.
        loginActivity.onUsername((v) -> v.setText("test"));
        loginActivity.onPassword((v) -> v.setText("passowrd"));

        // Start SimpleAfterLoginActivity with CLEAR_TASK and NEW_TASK after login activity.
        startActivityWithFlag(loginActivity, SimpleAfterLoginActivity.class,
                Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        assertActivityShownInBackground(SimpleAfterLoginActivity.class);

        // Verify save ui dialog.
        mUiBot.assertSaveNotShowing(SAVE_DATA_TYPE_PASSWORD);
    }

    private void startActivityWithFlag(Context context, Class<?> clazz, int flags) {
        startActivityWithFlag(context, clazz, flags, false);
    }

    private void startActivityWithFlag(Context context, Class<?> clazz, int flags,
                                       boolean useAutofillHint) {
        final Intent intent = new Intent(context, clazz)
                .setFlags(flags)
                .putExtra("useAutofillHint", useAutofillHint);

        context.startActivity(intent);
    }
}
