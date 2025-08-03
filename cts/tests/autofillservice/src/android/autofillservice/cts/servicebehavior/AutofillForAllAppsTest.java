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

import static android.autofillservice.cts.testcore.Helper.ID_IMEACTION_LABEL;
import static android.autofillservice.cts.testcore.Helper.ID_IMEACTION_TEXT;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME_LABEL;
import static android.autofillservice.cts.testcore.Helper.findNodeByResourceId;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;


import android.autofillservice.cts.activities.ImeOptionActivity;
import android.autofillservice.cts.activities.LoginNotImportantForAutofillActivity;
import android.autofillservice.cts.activities.LoginNotImportantUsernameImportantPasswordActivity;
import android.autofillservice.cts.activities.MultilineLoginActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.FillRequest;
import android.content.Intent;
import android.provider.DeviceConfig;
import android.util.Log;
import android.view.autofill.AutofillFeatureFlags;
import android.view.inputmethod.EditorInfo;

import com.android.compatibility.common.util.DeviceConfigStateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AutofillForAllAppsTest extends
        AutoFillServiceTestCase.ManualActivityLaunch {

    private static final String
            DEVICE_CONFIG_INCLUDE_ALL_AUTOFILL_TYPE_NOT_NONE_VIEWS_IN_ASSIST_STRUCTURE =
                "include_all_autofill_type_not_none_views_in_assist_structure";
    private static final String
            DEVICE_CONFIG_INCLUDE_ALL_VIEWS_IN_ASSIST_STRUCTURE =
                "include_all_views_in_assist_structure";
    private static final String
            DEVICE_CONFIG_PACKAGE_AND_ACTIVITY_ALLOWLIST_FOR_TRIGGERING_FILL_REQUEST =
                "package_and_activity_allowlist_for_triggering_fill_request";
    private static final String
            DEVICE_CONFIG_TRIGGER_FILL_REQUEST_ON_FILTERED_IMPORTANT_VIEWS =
                "trigger_fill_request_on_filtered_important_views";
    private static final String DEVICE_CONFIG_MULTILINE_FILTER_ENABLED = "multiline_filter_enabled";

    private DeviceConfigStateManager mDenyListStateManager =
            new DeviceConfigStateManager(mContext, DeviceConfig.NAMESPACE_AUTOFILL,
              AutofillFeatureFlags.DEVICE_CONFIG_PACKAGE_DENYLIST_FOR_UNIMPORTANT_VIEW);
    private DeviceConfigStateManager mAllowlistStateManager =
            new DeviceConfigStateManager(mContext, DeviceConfig.NAMESPACE_AUTOFILL,
                DEVICE_CONFIG_PACKAGE_AND_ACTIVITY_ALLOWLIST_FOR_TRIGGERING_FILL_REQUEST);
    private DeviceConfigStateManager mMultilineFilterEnabledStateManager =
            new DeviceConfigStateManager(mContext, DeviceConfig.NAMESPACE_AUTOFILL,
                DEVICE_CONFIG_MULTILINE_FILTER_ENABLED);
    private DeviceConfigStateManager mImeActionFlagStateManager =
            new DeviceConfigStateManager(mContext, DeviceConfig.NAMESPACE_AUTOFILL,
              AutofillFeatureFlags.DEVICE_CONFIG_NON_AUTOFILLABLE_IME_ACTION_IDS);
    private DeviceConfigStateManager mIsTriggerFillRequestOnUnimportantViewEnabledStateManager =
            new DeviceConfigStateManager(mContext, DeviceConfig.NAMESPACE_AUTOFILL,
              AutofillFeatureFlags.DEVICE_CONFIG_TRIGGER_FILL_REQUEST_ON_UNIMPORTANT_VIEW);
    private DeviceConfigStateManager
                mTriggerFillRequestOnSelectedImportantViewsEnabledStateManager =
                    new DeviceConfigStateManager(mContext, DeviceConfig.NAMESPACE_AUTOFILL,
                            DEVICE_CONFIG_TRIGGER_FILL_REQUEST_ON_FILTERED_IMPORTANT_VIEWS);
    private DeviceConfigStateManager mSholdIncludeAutofillTypeNotNoneFlagStateManager =
            new DeviceConfigStateManager(mContext, DeviceConfig.NAMESPACE_AUTOFILL,
                    DEVICE_CONFIG_INCLUDE_ALL_AUTOFILL_TYPE_NOT_NONE_VIEWS_IN_ASSIST_STRUCTURE);
    private DeviceConfigStateManager mShouldIncludeAllViewsInAssistStructureFlagStateManager =
            new DeviceConfigStateManager(mContext, DeviceConfig.NAMESPACE_AUTOFILL,
                    DEVICE_CONFIG_INCLUDE_ALL_VIEWS_IN_ASSIST_STRUCTURE);


    private static final String TAG = "AutofillForAllAppsTest";
    private static final String TEST_CTS_PACKAGE_NAME = "android.autofillservice.cts";
    private static final String TEST_NOT_IMPORTANT_FOR_AUTOFILL_ACTIVITY_NAME =
            "android.autofillservice.cts/.activities.LoginNotImportantForAutofillActivity";
    private static final String TEST_OTHER_ACTIVITY_NAME =
            "android.autofillservice.cts/.activities.LoginActivity";
    private static final String TEST_IME_ACTION_ACTIVITY_NAME =
            "android.autofillservice.cts/.activities.ImeOptionActivity";
    private final CannedFillResponse.Builder mLoginResponseBuilder =
            new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                    .setField(ID_USERNAME, "dude")
                    .setField(ID_PASSWORD, "sweet")
                    .setPresentation(createPresentation("Dropdown Presentation"))
                    .build());
    private final CannedFillResponse.Builder mImeOptionResponseBuilder =
            new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                  .setField(ID_IMEACTION_TEXT, "dude")
                  .setPresentation(createPresentation("Dropdown Presentation"))
                  .build());

    @Before
    public void setDenyListImeActionToEmptySetTriggerFillResponseOnUnimportantViewToTrue() {
        setImeActionFlagValue("");
        setDenyListFlagValue("");
        setAllowlistFlagValue("");
        setIsTriggerFillRequestOnUnimportantViewEnabledFlagValue(Boolean.toString(true));
        setIsApplyHeuristicOnImportantViewEnabledFlagValue("false");
        setShouldIncludeAutofillTypeNotNoneFlagValue("false");
        setShouldIncludeAllViewsInAssistStructure("false");
        setMultilineFilterFlagValue("false");
    }

    @After
    public void setDenyListImeActionToEmptySetTriggerFillResponseOnUnimportantViewToFalse() {
        setImeActionFlagValue("");
        setDenyListFlagValue("");
        setAllowlistFlagValue("");
        setIsTriggerFillRequestOnUnimportantViewEnabledFlagValue(Boolean.toString(false));
        setIsApplyHeuristicOnImportantViewEnabledFlagValue("false");
        setShouldIncludeAutofillTypeNotNoneFlagValue("false");
        setShouldIncludeAllViewsInAssistStructure("false");
        setMultilineFilterFlagValue("false");
    }

    @Test
    public void testUnimportantViewTriggerFillRequest() throws Exception {
        enableService();
        // Set response with a dataset
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start activity
        startActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field to trigger fill request
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert fill request is triggered  on username
        // (a not important view since parent is set to notImportantExcludeDescends)
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testUnimportantViewNotTriggerFillRequestWhenActivityInDenylist() throws Exception {
        enableService();
        setDenyListFlagValue(
                TEST_CTS_PACKAGE_NAME + ":" + TEST_NOT_IMPORTANT_FOR_AUTOFILL_ACTIVITY_NAME + ";");
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start login activity.
        startActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert no fill request is triggered since the package is on deny list.
        sReplier.assertOnFillRequestNotCalled();
    }

    @Test
    public void testUnimportantViewTriggerFillRequestWhenDenylistIsWronglyFormatted()
            throws Exception {
        enableService();
        // Left out ";" in the end to make the denylist wrongly formatted
        setDenyListFlagValue(
                TEST_CTS_PACKAGE_NAME + ":" + TEST_NOT_IMPORTANT_FOR_AUTOFILL_ACTIVITY_NAME);
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start login activity.
        startActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert fill request is still triggered since the activity in denylist is wrongly
        // formatted
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testUnimportantViewNotTriggerFillRequestWhenPackageInDenylist() throws Exception {
        enableService();
        setDenyListFlagValue(TEST_CTS_PACKAGE_NAME + ":;");
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start login activity.
        startActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert fill request is not triggered since the activity is on deny list.
        sReplier.assertOnFillRequestNotCalled();
    }

    @Test
    public void testUnimportantViewTriggerFillRequestWhenOtherActivityOfPackageInDenylist()
            throws Exception {
        enableService();
        setDenyListFlagValue(TEST_CTS_PACKAGE_NAME + ":" + TEST_OTHER_ACTIVITY_NAME + ";");
        // Set response with a dataset
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start login activity.
        startActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // Click on username field to trigger fill request
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert fill request is triggered since current activity is not in denylist
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testAddingSingleHeuristicCanStopTriggeringFillRequestOnUnimportantViews()
            throws Exception {
        enableService();
        // Test when adding IME_ACTION_GO in flag, fill request won't be triggered
        setImeActionFlagValue(String.valueOf(EditorInfo.IME_ACTION_GO));
        // Set response with a dataset
        sReplier.addResponse(mImeOptionResponseBuilder.build());
        // Start ime option activity.
        startImeOptionActivity();
        // Test normally, fill request would be triggered on unimportant views
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        mUiBot.selectByRelativeId(ID_IMEACTION_TEXT);
        mUiBot.waitForIdleSync();

        sReplier.assertOnFillRequestNotCalled();
    }

    @Test
    public void testAddingSeveralHeuristicsCanStopTriggeringFillRequestOnUnimportantViews()
            throws Exception {
        enableService();
        // Test when adding multiple ime action ids including ime_action_go in flag, fill request
        // won't be triggered
        setImeActionFlagValue(String.valueOf(EditorInfo.IME_ACTION_GO) + "," + String.valueOf(
                EditorInfo.IME_ACTION_SEND));
        // Set response with a dataset
        sReplier.addResponse(mImeOptionResponseBuilder.build());
        // Start ime option activity.
        startImeOptionActivity();
        // Test normally, fill request would be triggered on unimportant views
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        mUiBot.selectByRelativeId(ID_IMEACTION_TEXT);
        mUiBot.waitForIdleSync();

        sReplier.assertOnFillRequestNotCalled();
    }

    @Test
    public void testAddingSeveralHeuristicsCanStopTriggeringFillRequestOnImportantViews()
        throws Exception {
        enableService();
        // Test when adding multiple ime action ids including ime_action_go in flag, fill request
        // won't be triggered
        setImeActionFlagValue(String.valueOf(EditorInfo.IME_ACTION_GO) + "," + String.valueOf(
            EditorInfo.IME_ACTION_SEND));
        setIsApplyHeuristicOnImportantViewEnabledFlagValue("true");

        // Set response with a dataset
        sReplier.addResponse(mImeOptionResponseBuilder.build());
        // Start ime option activity.
        startImeOptionActivity();
        // Test normally, fill request would be triggered on unimportant views
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        mUiBot.selectByRelativeId(Helper.ID_IMEACTION_TEXT_IMPORTANT_FOR_AUTOFILL);
        mUiBot.waitForIdleSync();

        sReplier.assertOnFillRequestNotCalled();
    }

    @Test
    public void testAddingPacakgeToAllowlist_ViewWithImeActionGoStillTriggerFillRequest()
            throws Exception {
        enableService();
        setImeActionFlagValue(String.valueOf(EditorInfo.IME_ACTION_GO));
        setAllowlistFlagValue(TEST_CTS_PACKAGE_NAME + ":;");
        // Set response with a dataset
        sReplier.addResponse(mImeOptionResponseBuilder.build());
        // Start ime option activity.
        startImeOptionActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        mUiBot.selectByRelativeId(ID_IMEACTION_TEXT);
        mUiBot.waitForIdleSync();

        // assert fill request is triggered since package is in allowlist
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testAddingActivityToAllowlist_ViewWithImeActionGoStillTriggerFillRequest()
            throws Exception {
        enableService();
        setImeActionFlagValue(String.valueOf(EditorInfo.IME_ACTION_GO));
        setAllowlistFlagValue(TEST_CTS_PACKAGE_NAME + ":" + TEST_IME_ACTION_ACTIVITY_NAME + ";");
        // Set response with a dataset
        sReplier.addResponse(mImeOptionResponseBuilder.build());
        // Start ime option activity.
        startImeOptionActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        mUiBot.selectByRelativeId(ID_IMEACTION_TEXT);
        mUiBot.waitForIdleSync();

        // assert fill request is triggered since package is in allowlist
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testAddingOtherActivityToAllowlist_ViewWithImeActionGoDoesntTriggerFillRequest()
        throws Exception {
        enableService();
        setImeActionFlagValue(String.valueOf(EditorInfo.IME_ACTION_GO));
        setAllowlistFlagValue(TEST_CTS_PACKAGE_NAME + ":" + TEST_OTHER_ACTIVITY_NAME + ";");
        // Set response with a dataset
        sReplier.addResponse(mImeOptionResponseBuilder.build());
        // Start ime option activity.
        startImeOptionActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        mUiBot.selectByRelativeId(ID_IMEACTION_TEXT);
        mUiBot.waitForIdleSync();

        // assert fill request is not triggered since current activity is not allowed
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testMultilineFilterCanStopViewTriggerFillRequest()
            throws Exception {
        enableService();
        setMultilineFilterFlagValue("true");
        setIsApplyHeuristicOnImportantViewEnabledFlagValue("true");
        // Set response with a dataset
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start ime option activity.
        MultilineLoginActivity tempActivity = startMultilineLoginActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // focus on username field
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert fill request is not triggered since it should be filterd by multiline check
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testSelectedViewsStrillTriggerFillRequest() throws Exception {
        enableService();
        setIsApplyHeuristicOnImportantViewEnabledFlagValue("true");
        setMultilineFilterFlagValue("true");
        // Set response with a dataset
        sReplier.addResponse(mLoginResponseBuilder.build());
        // Start Login Activity
        startLoginActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        // focus on username field
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        // assert fill request is triggered since the views passes heuristic and multiline check
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testAssistStructure_includeAutofillTypeNotNoneViews()
            throws Exception {
        enableService();
        // Disable trigger fill request on unimportant view to prevent view included by this flag
        setIsTriggerFillRequestOnUnimportantViewEnabledFlagValue("false");
        setShouldIncludeAutofillTypeNotNoneFlagValue("true");

        // Set response for password since only password is important for autofill
        sReplier.addResponse(mLoginResponseBuilder.build());
        startLoginNotImportantUsernameImportantPasswordActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        mUiBot.selectByRelativeId(Helper.ID_PASSWORD);
        mUiBot.waitForIdleSync();

        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Username field should be in assist structure, since although it's not important, its
        // autofill type is text and include autofill type not none flag is turned on.
        assertNotNull("Username field is not in assist structure",
                findNodeByResourceId(fillRequest.structure, ID_USERNAME));
        // Username label should not be in assist structure, since it's not important and autofill
        // type is none.
        assertNull("Username label should not be in assist structure when only include autofill "
                        + "type not none view flag is turned on",
                            findNodeByResourceId(fillRequest.structure, ID_USERNAME_LABEL));
        // safe-check
        sReplier.assertNoUnhandledFillRequests();
    }

    @Test
    public void testAssistStructure_includeAllViews()
            throws Exception {
        enableService();
        // Disable trigger fill request on unimportant view to prevent view included by this flag
        setIsTriggerFillRequestOnUnimportantViewEnabledFlagValue("false");
        setShouldIncludeAllViewsInAssistStructure("true");

        sReplier.addResponse(mLoginResponseBuilder.build());
        startLoginNotImportantUsernameImportantPasswordActivity();
        mUiBot.waitForIdleSync();
        sReplier.assertNoUnhandledFillRequests();

        mUiBot.selectByRelativeId(Helper.ID_PASSWORD);
        mUiBot.waitForIdleSync();

        final FillRequest fillRequest = sReplier.getNextFillRequest();

        // Both username field and username label should be in assist structure, since although they
        // are not important for autofill, include all views in assist structure flag is turned on
        assertNotNull("Username field is not in assist structure",
                findNodeByResourceId(fillRequest.structure, ID_USERNAME));
        assertNotNull("Username label is not in assist structure",
                findNodeByResourceId(fillRequest.structure, ID_USERNAME_LABEL));
        // safe-check
        sReplier.assertNoUnhandledFillRequests();
    }

    private void setMultilineFilterFlagValue(String value) {
        Helper.setDeviceConfig(mMultilineFilterEnabledStateManager, value);
    }

    private void setShouldIncludeAllViewsInAssistStructure(String value) {
        Helper.setDeviceConfig(mShouldIncludeAllViewsInAssistStructureFlagStateManager, value);
    }

    private void setShouldIncludeAutofillTypeNotNoneFlagValue(String value) {
        Helper.setDeviceConfig(mSholdIncludeAutofillTypeNotNoneFlagStateManager,
                value);
    }

    private void setIsApplyHeuristicOnImportantViewEnabledFlagValue(String value) {
        Helper.setDeviceConfig(mTriggerFillRequestOnSelectedImportantViewsEnabledStateManager,
            value);
    }

    private void setImeActionFlagValue(String value) {
        Helper.setDeviceConfig(mImeActionFlagStateManager, value);
    }

    private void setDenyListFlagValue(String value) {
        Helper.setDeviceConfig(mDenyListStateManager, value);
    }

    private void setAllowlistFlagValue(String value) {
        Helper.setDeviceConfig(mAllowlistStateManager, value);
    }

    private void setIsTriggerFillRequestOnUnimportantViewEnabledFlagValue(String value) {
        Helper.setDeviceConfig(mIsTriggerFillRequestOnUnimportantViewEnabledStateManager, value);
    }

    private LoginNotImportantForAutofillActivity startActivity() throws Exception {
        final Intent intent = new Intent(mContext, LoginNotImportantForAutofillActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        waitSeveralIdleSyncToAssertSpecifiedUiShown(ID_USERNAME_LABEL, 11);
        return LoginNotImportantForAutofillActivity.getCurrentActivity();
    }

    private LoginNotImportantUsernameImportantPasswordActivity
            startLoginNotImportantUsernameImportantPasswordActivity()  throws Exception {
        final Intent intent =
                new Intent(mContext, LoginNotImportantUsernameImportantPasswordActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        waitSeveralIdleSyncToAssertSpecifiedUiShown(ID_USERNAME_LABEL, 11);
        return LoginNotImportantUsernameImportantPasswordActivity.getCurrentActivity();
    }

    private ImeOptionActivity startImeOptionActivity() throws Exception {
        final Intent intent = new Intent(mContext, ImeOptionActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        waitSeveralIdleSyncToAssertSpecifiedUiShown(ID_IMEACTION_LABEL, 11);
        return ImeOptionActivity.getCurrentActivity();
    }

    private MultilineLoginActivity startMultilineLoginActivity() throws Exception {
        final Intent intent = new Intent(mContext, MultilineLoginActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        waitSeveralIdleSyncToAssertSpecifiedUiShown(ID_USERNAME, 11);
        return MultilineLoginActivity.getCurrentActivity();
    }

    private void waitSeveralIdleSyncToAssertSpecifiedUiShown(String labelId, int numOfRounds) {
        int count = 0;
        while (count < numOfRounds) {
            try {
                mUiBot.assertShownByRelativeId(labelId);
                return;
            } catch (Exception e) {
                mUiBot.waitForIdleSync();
                count += 1;
            }
        }
        Log.w(TAG,
                "Label " + labelId + "didn't show after "
                    + numOfRounds + " rounds of wait idle syncs");
        return;
    }
}
