/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.verifier.managedprovisioning;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class PolicyTransparencyTestActivity extends PassFailButtons.Activity implements
        View.OnClickListener, CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemSelectedListener {

    private static final String TAG = PolicyTransparencyTestActivity.class.getSimpleName();

    public static final String ACTION_SHOW_POLICY_TRANSPARENCY_TEST =
            "com.android.cts.verifier.managedprovisioning.action.SHOW_POLICY_TRANSPARENCY_TEST";

    // Identifies a test to perform. Type String. The possible values are the ones underneath.
    public static final String EXTRA_TEST =
            "com.android.cts.verifier.managedprovisioning.extra.TEST";

    // In this case: should also contain an extra
    // {@link CommandReceiverActivity.EXTRA_USER_RESTRICTION}
    public static final String TEST_CHECK_USER_RESTRICTION = "check-user-restriction";
    public static final String TEST_CHECK_AUTO_TIME_REQUIRED = "check-auto-time-required";
    public static final String TEST_CHECK_KEYGURAD_UNREDACTED_NOTIFICATION =
            "check-keyguard-unredacted-notification";
    public static final String TEST_CHECK_LOCK_SCREEN_INFO = "check-lock-screen-info";
    public static final String TEST_CHECK_MAXIMUM_TIME_TO_LOCK = "check-maximum-time-to-lock";
    public static final String TEST_CHECK_PERMITTED_ACCESSIBILITY_SERVICE =
            "check-permitted-accessibility-service";
    public static final String TEST_CHECK_PERMITTED_INPUT_METHOD = "check-permitted-input-method";

    public static final String EXTRA_SETTINGS_INTENT_ACTION =
            "com.android.cts.verifier.managedprovisioning.extra.SETTINGS_INTENT_ACTION";
    public static final String EXTRA_TITLE =
            "com.android.cts.verifier.managedprovisioning.extra.TITLE";
    // Identifies the test in the calling activity. We will set the result for this test.
    // Type: String
    public static final String EXTRA_TEST_ID =
            "com.android.cts.verifier.managedprovisioning.extra.TEST_ID";

    private static final Map<String, PolicyTestItem> POLICY_TEST_ITEMS = new ArrayMap<>();

    /**
     * List of restrictions that might not have an option for user to change on Settings.
     */
    private static final List<String> OPTIONAL_USER_RESTRICTION_ACTIONS = Arrays
            .asList(
                UserManager.DISALLOW_CONFIG_CELL_BROADCASTS,
                UserManager.DISALLOW_NETWORK_RESET,
                UserManager.DISALLOW_CONFIG_TETHERING);

    static {
        POLICY_TEST_ITEMS.put(TEST_CHECK_AUTO_TIME_REQUIRED, new PolicyTestItem(
                R.string.auto_time_required_set_step,
                R.string.set_auto_time_required_action,
                R.string.set_auto_time_required_widget_label,
                R.id.switch_widget,
                CommandReceiverActivity.COMMAND_SET_AUTO_TIME_REQUIRED));
        POLICY_TEST_ITEMS.put(TEST_CHECK_KEYGURAD_UNREDACTED_NOTIFICATION, new PolicyTestItem(
                R.string.disallow_keyguard_unredacted_notifications_set_step,
                R.string.disallow_keyguard_unredacted_notifications_action,
                R.string.disallow_keyguard_unredacted_notifications_widget_label,
                R.id.switch_widget,
                CommandReceiverActivity.COMMAND_DISALLOW_KEYGUARD_UNREDACTED_NOTIFICATIONS));
        POLICY_TEST_ITEMS.put(TEST_CHECK_LOCK_SCREEN_INFO, new PolicyTestItem(
                R.string.lock_screen_info_set_step,
                R.string.set_lock_screen_info_action,
                R.string.set_lock_screen_info_widget_label,
                R.id.edit_text_widget,
                CommandReceiverActivity.COMMAND_SET_LOCK_SCREEN_INFO));
        POLICY_TEST_ITEMS.put(TEST_CHECK_MAXIMUM_TIME_TO_LOCK, new PolicyTestItem(
                R.string.maximum_time_to_lock_set_step,
                R.string.set_maximum_time_to_lock_action,
                R.string.set_maximum_time_to_lock_widget_label,
                R.id.edit_text_widget,
                CommandReceiverActivity.COMMAND_SET_MAXIMUM_TO_LOCK));
        POLICY_TEST_ITEMS.put(TEST_CHECK_PERMITTED_ACCESSIBILITY_SERVICE, new PolicyTestItem(
                R.string.permitted_accessibility_services_set_step,
                R.string.set_permitted_accessibility_services_action,
                R.string.set_permitted_accessibility_services_widget_label,
                R.id.switch_widget,
                CommandReceiverActivity.COMMAND_ALLOW_ONLY_SYSTEM_ACCESSIBILITY_SERVICES));
        POLICY_TEST_ITEMS.put(TEST_CHECK_PERMITTED_INPUT_METHOD, new PolicyTestItem(
                R.string.permitted_input_methods_set_step,
                R.string.set_permitted_input_methods_action,
                R.string.set_permitted_input_methods_widget_label,
                R.id.switch_widget,
                CommandReceiverActivity.COMMAND_ALLOW_ONLY_SYSTEM_INPUT_METHODS));
    }

    private boolean mForceCurrentUserDpm;
    private String mSettingsIntentAction;
    private String mTestId;
    private String mTitle;
    private String mTest;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.policy_transparency_test);
        setPassFailButtonClickListeners();

        mForceCurrentUserDpm =
                getIntent().getBooleanExtra(
                        CommandReceiverActivity.EXTRA_USE_CURRENT_USER_DPM, false);
        mTitle = getIntent().getStringExtra(EXTRA_TITLE);
        mTestId = getIntent().getStringExtra(EXTRA_TEST_ID);
        mSettingsIntentAction = getIntent().getStringExtra(EXTRA_SETTINGS_INTENT_ACTION);
        mTest = getIntent().getStringExtra(EXTRA_TEST);

        setTitle(mTitle);
        findViewById(R.id.open_settings_button).setOnClickListener(this);
        updateTestInstructions();
    }

    private void updateTestInstructions() {
        String setStep = null;
        String userAction = null;
        String widgetLabel = null;
        int widgetId = 0;
        String note = "";
        if (TEST_CHECK_USER_RESTRICTION.equals(mTest)) {
            setStep = getString(R.string.user_restriction_set_step, mTitle);
            final String userRestriction = getIntent().getStringExtra(
                    CommandReceiverActivity.EXTRA_USER_RESTRICTION);
            userAction = UserRestrictions.getUserAction(this, userRestriction);
            widgetLabel = mTitle;
            widgetId = R.id.switch_widget;
            if (OPTIONAL_USER_RESTRICTION_ACTIONS.contains(userRestriction)) {
                note = getString(R.string.optional_policy_transparency_test_note);
            }
        } else {
            final PolicyTestItem testItem = POLICY_TEST_ITEMS.get(mTest);
            setStep = getString(testItem.setStep);
            userAction = getString(testItem.userAction);
            widgetLabel = getString(testItem.widgetLabel);
            widgetId = testItem.widgetId;
        }
        ((TextView) findViewById(R.id.widget_label)).setText(widgetLabel);
        ((TextView) findViewById(R.id.test_instructions)).setText(
                getString(R.string.policy_transparency_test_instructions,
                        setStep, userAction, note));
        updateWidget(widgetId);
    }

    private void updateWidget(int widgetId) {
        if (widgetId == R.id.switch_widget) {
            Switch switchWidget = (Switch) findViewById(R.id.switch_widget);
            switchWidget.setOnCheckedChangeListener(this);
            switchWidget.setVisibility(View.VISIBLE);
        } else if (widgetId == R.id.edit_text_widget) {
            findViewById(R.id.edit_text_widget).setVisibility(View.VISIBLE);
            Button updateButton = (Button) findViewById(R.id.update_button);
            updateButton.setOnClickListener(this);
            updateButton.setVisibility(View.VISIBLE);
        } else {
            throw new IllegalArgumentException("Unknown widgetId: " + widgetId);
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.open_settings_button) {
            try {
                startActivity(new Intent(mSettingsIntentAction));
            } catch (ActivityNotFoundException e) {
                // If the given settings intent is not handled, use the main settings intent
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        } else if (view.getId() == R.id.update_button) {
            final PolicyTestItem testItem = POLICY_TEST_ITEMS.get(mTest);
            final Intent intent = new Intent(CommandReceiverActivity.ACTION_EXECUTE_COMMAND);
            intent.putExtra(CommandReceiverActivity.EXTRA_COMMAND, testItem.command);
            final EditText editText = (EditText) findViewById(R.id.edit_text_widget);
            intent.putExtra(CommandReceiverActivity.EXTRA_VALUE, editText.getText().toString());
            startActivity(intent);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        final Intent intent;
        if (TEST_CHECK_USER_RESTRICTION.equals(mTest)) {
            final String userRestriction = getIntent().getStringExtra(
                    CommandReceiverActivity.EXTRA_USER_RESTRICTION);
            intent = mForceCurrentUserDpm
                    ? CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                            userRestriction, isChecked)
                    : CommandReceiverActivity.createSetDeviceOwnerUserRestrictionIntent(
                            userRestriction, isChecked);
        } else {
            intent = new Intent(CommandReceiverActivity.ACTION_EXECUTE_COMMAND);
            final PolicyTestItem testItem = POLICY_TEST_ITEMS.get(mTest);
            intent.putExtra(CommandReceiverActivity.EXTRA_COMMAND, testItem.command);
            intent.putExtra(CommandReceiverActivity.EXTRA_ENFORCED, isChecked);
            intent.putExtra(CommandReceiverActivity.EXTRA_USE_CURRENT_USER_DPM,
                    mForceCurrentUserDpm);
        }
        startActivity(intent);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        final PolicyTestItem testItem = POLICY_TEST_ITEMS.get(mTest);
        final Intent intent = new Intent(CommandReceiverActivity.ACTION_EXECUTE_COMMAND);
        intent.putExtra(CommandReceiverActivity.EXTRA_COMMAND, testItem.command);
        Log.d(TAG, "onItemSelected(): command=" + testItem.command);
        startActivity(intent);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing.
    }

    @Override
    public String getTestId() {
        return mTestId;
    }

    public static class TestAccessibilityService extends AccessibilityService {

        @Override
        public void onAccessibilityEvent(AccessibilityEvent event) {
            // Do nothing
        }

        @Override
        public void onInterrupt() {
            // Do nothing
        }
    }

    public static class TestInputMethod extends InputMethodService {
        @Override
        public boolean onEvaluateFullscreenMode() {
            return false;
        }

        @Override
        public boolean onEvaluateInputViewShown() {
            return false;
        }
    }

    private static class PolicyTestItem {
        public final int setStep;
        public final int userAction;
        public final int widgetLabel;
        public final int widgetId;
        public final String command;

        public PolicyTestItem(int setStep, int userAction, int widgetLabel, int widgetId,
                String command) {
            this.setStep = setStep;
            this.userAction = userAction;
            this.widgetLabel = widgetLabel;
            this.widgetId = widgetId;
            this.command = command;
        }
    }
}
