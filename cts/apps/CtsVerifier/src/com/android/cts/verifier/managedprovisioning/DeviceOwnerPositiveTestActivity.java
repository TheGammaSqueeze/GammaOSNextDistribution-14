/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.os.UserHandle.myUserId;

import static com.android.cts.verifier.managedprovisioning.CommandReceiverActivity.createIntentForDisablingKeyguardOrStatusBar;
import static com.android.cts.verifier.managedprovisioning.Utils.createInteractiveTestItem;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory;
import com.android.compatibility.common.util.CddTest;
import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;
import com.android.cts.verifier.TestResult;
import com.android.cts.verifier.features.FeatureUtil;

/**
 * Activity that lists all positive device owner tests. Requires the following adb command be issued
 * by the user prior to starting the tests:
 *
 * adb shell dpm set-device-owner
 *  'com.android.cts.verifier/com.android.cts.verifier.managedprovisioning.DeviceAdminTestReceiver'
 */
@CddTest(requirements = {
        "7.7",
        "9.11.1/C-3-4", // setRequiredPasswordComplexity behaviour
        "9.11.1/C-5-1", // setRequiredPasswordComplexity behaviour
        "9.11.1/C-6-2", // setRequiredPasswordComplexity behaviour
        "9.11.1/C-8-1", // setRequiredPasswordComplexity behaviour
})
public class DeviceOwnerPositiveTestActivity extends PassFailButtons.TestListActivity {
    private static final String TAG = "DeviceOwnerPositiveTestActivity";

    private static final String ACTION_CHECK_DEVICE_OWNER =
            "com.android.cts.verifier.managedprovisioning.action.CHECK_DEVICE_OWNER";
    private static final String ACTION_CHECK_PROFILE_OWNER =
            "com.android.cts.verifier.managedprovisioning.action.CHECK_PROFILE_OWNER";
    private static final String ACTION_CHECK_CURRENT_USER_AFFILIATED =
            "com.android.cts.verifier.managedprovisioning.action.CHECK_USER_AFFILIATED";

    static final String EXTRA_TEST_ID = "extra-test-id";

    private static final String CHECK_DEVICE_OWNER_TEST_ID = "CHECK_DEVICE_OWNER";
    private static final String CHECK_PROFILE_OWNER_TEST_ID = "CHECK_PROFILE_OWNER";
    private static final String CHECK_USER_AFFILIATED_TEST_ID = "CHECK_USER_AFFILIATED";
    private static final String DEVICE_ADMIN_SETTINGS_ID = "DEVICE_ADMIN_SETTINGS";
    private static final String WIFI_LOCKDOWN_TEST_ID = WifiLockdownTestActivity.class.getName();
    private static final String DISABLE_STATUS_BAR_TEST_ID = "DISABLE_STATUS_BAR";
    private static final String DISABLE_KEYGUARD_TEST_ID = "DISABLE_KEYGUARD";
    private static final String LOCK_TASK_UI_TEST_ID = "LOCK_TASK_UI";
    private static final String CHECK_PERMISSION_LOCKDOWN_TEST_ID =
            PermissionLockdownTestActivity.class.getName();
    private static final String DISALLOW_CONFIG_BT_ID = "DISALLOW_CONFIG_BT";
    private static final String DISALLOW_CONFIG_WIFI_ID = "DISALLOW_CONFIG_WIFI";
    private static final String DISALLOW_CONFIG_VPN_ID = "DISALLOW_CONFIG_VPN";
    private static final String DISALLOW_USB_FILE_TRANSFER_ID = "DISALLOW_USB_FILE_TRANSFER";
    private static final String SET_USER_ICON_TEST_ID = "SET_USER_ICON";
    private static final String DISALLOW_DATA_ROAMING_ID = "DISALLOW_DATA_ROAMING";
    private static final String DISALLOW_FACTORY_RESET_ID = "DISALLOW_FACTORY_RESET";
    private static final String POLICY_TRANSPARENCY_TEST_ID = "POLICY_TRANSPARENCY";
    private static final String ENTERPRISE_PRIVACY_TEST_ID = "ENTERPRISE_PRIVACY";
    private static final String NETWORK_LOGGING_UI_TEST_ID = "NETWORK_LOGGING_UI";
    private static final String DISALLOW_USER_SWITCH_TEST_ID = "DISALLOW_USER_SWITCH";
    private static final String USER_SWITCHER_MESSAGE_TEST_ID = "USER_SWITCHER_MESSAGE";
    private static final String ENABLE_LOGOUT_TEST_ID = "ENABLE_LOGOUT";
    private static final String MANAGED_USER_TEST_ID = "MANAGED_USER_UI";
    private static final String REMOVE_DEVICE_OWNER_TEST_ID = "REMOVE_DEVICE_OWNER";
    private static final String DISALLOW_AMBIENT_DISPLAY_ID = "DISALLOW_AMBIENT_DISPLAY";
    private static final String DISALLOW_REMOVE_USER_TEST_ID = "DISALLOW_REMOVE_USER";
    private static final String DISABLE_USB_DATA_SIGNALING_TEST_ID = "DISABLE_USB_DATA_SIGNALING";
    private static final String SET_REQUIRED_PASSWORD_COMPLEXITY_ID =
            "SET_REQUIRED_PASSWORD_COMPLEXITY";
    private static final String DISALLOW_ADD_WIFI_CONFIG_ID = "DISALLOW_ADD_WIFI_CONFIG";
    private static final String WIFI_SECURITY_LEVEL_RESTRICTION_ID =
            "WIFI_SECURITY_LEVEL_RESTRICTION";
    private static final String ACTION_CONNECT_INPUT =
            "com.google.android.intent.action.CONNECT_INPUT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tidy up in case previous run crashed.
        new ByodFlowTestHelper(this).tearDown();

        if (ACTION_CHECK_DEVICE_OWNER.equals(getIntent().getAction())) {
            DevicePolicyManager dpm = TestAppSystemServiceFactory.getDevicePolicyManager(this,
                    DeviceAdminTestReceiver.class, /* forDeviceOwner= */ true);
            if (dpm.isDeviceOwnerApp(getPackageName())) {
                // Set DISALLOW_ADD_USER on behalf of ManagedProvisioning.
                dpm.addUserRestriction(DeviceAdminTestReceiver.getReceiverComponentName(),
                        UserManager.DISALLOW_ADD_USER);
                TestResult.setPassedResult(this, getIntent().getStringExtra(EXTRA_TEST_ID),
                        null, null);
            } else {
                TestResult.setFailedResult(this, getIntent().getStringExtra(EXTRA_TEST_ID),
                        getString(R.string.device_owner_incorrect_profile_owner, myUserId()),
                        null);
            }

            finish();
            return;
        }
        if (ACTION_CHECK_PROFILE_OWNER.equals(getIntent().getAction())) {
            DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
            if (dpm.isProfileOwnerApp(getPackageName())) {
                TestResult.setPassedResult(this, getIntent().getStringExtra(EXTRA_TEST_ID),
                        null, null);
            } else {
                TestResult.setFailedResult(this, getIntent().getStringExtra(EXTRA_TEST_ID),
                        getString(R.string.device_owner_incorrect_device_owner, myUserId()), null);
            }
            finish();
            return;
        }
        if (ACTION_CHECK_CURRENT_USER_AFFILIATED.equals(getIntent().getAction())) {
            DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
            if (dpm.isAffiliatedUser()) {
                TestResult.setPassedResult(this, getIntent().getStringExtra(EXTRA_TEST_ID),
                        null, null);
            } else {
                TestResult.setFailedResult(this, getIntent().getStringExtra(EXTRA_TEST_ID),
                        getString(R.string.device_owner_user_not_affiliated, myUserId()), null);
            }
            finish();
            return;
        }

        setContentView(R.layout.positive_device_owner);
        setInfoResources(R.string.device_owner_positive_tests,
                R.string.device_owner_positive_tests_info, 0);
        setPassFailButtonClickListeners();

        final ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        adapter.add(TestListItem.newCategory(this, R.string.device_owner_positive_category));

        addTestsToAdapter(adapter);

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);

        View setDeviceOwnerButton = findViewById(R.id.set_device_owner_button);
        setDeviceOwnerButton.setOnClickListener((v) -> {
            StringBuilder builder = new StringBuilder();
            int messageResId;
            if (UserManager.isHeadlessSystemUserMode()) {
                builder.append(getString(R.string.grant_headless_system_user_permissions));
                messageResId = R.string.set_device_owner_headless_dialog_text;
            } else {
                messageResId = R.string.set_device_owner_dialog_text;
            }

            String message = builder.append(getString(messageResId)).toString();
            Log.i(TAG, message);
            new AlertDialog.Builder(
                    DeviceOwnerPositiveTestActivity.this)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setTitle(R.string.set_device_owner_dialog_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });

    }

    @Override
    public void finish() {
        String action = getIntent().getAction();
        switch(action != null ? action : "") {
            case ACTION_CHECK_DEVICE_OWNER:
            case ACTION_CHECK_PROFILE_OWNER:
            case ACTION_CHECK_CURRENT_USER_AFFILIATED:
                // If this activity was started for checking device / profile owner status, then no
                // need to do any tear down.
                Log.d(TAG, "NOT starting createTearDownIntent() due to " + action);
                break;
            default:
                // Pass and fail buttons are known to call finish() when clicked,
                // and this is when we want to remove the device owner.
                Log.d(TAG, "Starting createTearDownIntent() due to " + action);
                startActivity(createTearDownIntent());
                break;
        }
        super.finish();
    }

    private void addTestsToAdapter(final ArrayTestListAdapter adapter) {
        adapter.add(createTestItem(this, CHECK_DEVICE_OWNER_TEST_ID,
                R.string.device_owner_check_device_owner_test,
                new Intent(ACTION_CHECK_DEVICE_OWNER)
                        .putExtra(EXTRA_TEST_ID, getIntent().getStringExtra(EXTRA_TEST_ID))));
        if (UserManager.isHeadlessSystemUserMode()) {
            adapter.add(createTestItem(this, CHECK_PROFILE_OWNER_TEST_ID,
                    R.string.device_owner_check_profile_owner_test,
                    new Intent(ACTION_CHECK_PROFILE_OWNER)
                            .putExtra(EXTRA_TEST_ID, getIntent().getStringExtra(EXTRA_TEST_ID))));
            adapter.add(createTestItem(this, CHECK_USER_AFFILIATED_TEST_ID,
                    R.string.device_owner_check_user_affiliation_test,
                    new Intent(ACTION_CHECK_CURRENT_USER_AFFILIATED)
                            .putExtra(EXTRA_TEST_ID, getIntent().getStringExtra(EXTRA_TEST_ID))));
        }

        // device admin settings
        adapter.add(createInteractiveTestItem(this, DEVICE_ADMIN_SETTINGS_ID,
                R.string.device_owner_device_admin_visible,
                R.string.device_owner_device_admin_visible_info,
                new ButtonInfo(
                        R.string.device_owner_settings_go,
                        new Intent(Settings.ACTION_SECURITY_SETTINGS))));

        PackageManager packageManager = getPackageManager();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            // WiFi Lock down tests
            adapter.add(createTestItem(this, WIFI_LOCKDOWN_TEST_ID,
                    R.string.device_owner_wifi_lockdown_test,
                    new Intent(this, WifiLockdownTestActivity.class)));

            // DISALLOW_CONFIG_WIFI
            adapter.add(createInteractiveTestItem(this, DISALLOW_CONFIG_WIFI_ID,
                    R.string.device_owner_disallow_config_wifi,
                    R.string.device_owner_disallow_config_wifi_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_set,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_CONFIG_WIFI, true)),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_WIFI_SETTINGS)),
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_unset,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_CONFIG_WIFI, false))
                    }));

            // DISALLOW_ADD_WIFI_CONFIG
            adapter.add(createInteractiveTestItem(this, DISALLOW_ADD_WIFI_CONFIG_ID,
                    R.string.device_owner_disallow_add_wifi_config,
                    R.string.device_owner_disallow_add_wifi_config_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_set,
                                    CommandReceiverActivity
                                            .createSetDeviceOwnerUserRestrictionIntent(
                                                    UserManager.DISALLOW_ADD_WIFI_CONFIG, true)),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_WIFI_SETTINGS)),
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_unset,
                                    CommandReceiverActivity
                                            .createSetDeviceOwnerUserRestrictionIntent(
                                                    UserManager.DISALLOW_ADD_WIFI_CONFIG, false))
                    }));

            // WIFI_SECURITY_LEVEL_RESTRICTION
            adapter.add(createInteractiveTestItem(this, WIFI_SECURITY_LEVEL_RESTRICTION_ID,
                    R.string.device_owner_wifi_security_level_restriction,
                    R.string.device_owner_wifi_security_level_restriction_info,
                    new ButtonInfo[]{
                            new ButtonInfo(
                                    R.string.set_wifi_security_level_open,
                                    createSetWifiSecurityLevelIntent(
                                            DevicePolicyManager.WIFI_SECURITY_OPEN)),
                            new ButtonInfo(
                                    R.string.set_wifi_security_level_personal,
                                    createSetWifiSecurityLevelIntent(
                                            DevicePolicyManager.WIFI_SECURITY_PERSONAL)),
                            new ButtonInfo(
                                    R.string.set_wifi_security_level_enterprise_eap,
                                    createSetWifiSecurityLevelIntent(
                                            DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_EAP)),
                            new ButtonInfo(
                                    R.string.set_wifi_security_level_enterprise_192,
                                    createSetWifiSecurityLevelIntent(
                                            DevicePolicyManager.WIFI_SECURITY_ENTERPRISE_192)),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_WIFI_SETTINGS))}));

            // WIFI_SSID_RESTRICTION
            adapter.add(TestListItem.newTest(this,
                    R.string.device_owner_ssid_restriction,
                    SsidRestrictionTestActivity.class.getName(),
                    new Intent(this, SsidRestrictionTestActivity.class),
                    /* requiredFeatures */ null));
        }

        // DISALLOW_AMBIENT_DISPLAY.
        // TODO: After the ambient display feature flag is added in PackageManager (b/135591614),
        // uncomment this test and run it only when ambient display is supported by the device.

        // adapter.add(createInteractiveTestItem(this, DISALLOW_AMBIENT_DISPLAY_ID,
        //         R.string.device_owner_disallow_ambient_display,
        //         R.string.device_owner_disallow_ambient_display_info,
        //         new ButtonInfo[] {
        //                 new ButtonInfo(
        //                         R.string.device_owner_user_restriction_set,
        //                         CommandReceiverActivity.createSetUserRestrictionIntent(
        //                                 UserManager.DISALLOW_AMBIENT_DISPLAY, true)),
        //                 new ButtonInfo(
        //                         R.string.device_owner_settings_go,
        //                         new Intent(Settings.ACTION_DISPLAY_SETTINGS))}));

        // DISALLOW_CONFIG_VPN
        if (FeatureUtil.isConfigVpnSupported(this)) {
            adapter.add(createInteractiveTestItem(this, DISALLOW_CONFIG_VPN_ID,
                    R.string.device_owner_disallow_config_vpn,
                    R.string.device_owner_disallow_config_vpn_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_user_vpn_restriction_set,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_CONFIG_VPN, true)),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_VPN_SETTINGS)),
                            new ButtonInfo(
                                    R.string.device_owner_vpn_test,
                                    new Intent(this, VpnTestActivity.class)),
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_unset,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_CONFIG_VPN, false))
                    }));
        }

        // DISALLOW_DATA_ROAMING
        if (FeatureUtil.isDataRoamingSupported(this)
                && packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            adapter.add(createInteractiveTestItem(this, DISALLOW_DATA_ROAMING_ID,
                    R.string.device_owner_disallow_data_roaming,
                    R.string.device_owner_disallow_data_roaming_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_set,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_DATA_ROAMING, true)),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)),
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_unset,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_DATA_ROAMING, false))
                    }));
        }

        // DISALLOW_FACTORY_RESET
        adapter.add(createInteractiveTestItem(this, DISALLOW_FACTORY_RESET_ID,
                R.string.device_owner_disallow_factory_reset,
                R.string.device_owner_disallow_factory_reset_info,
                new ButtonInfo[] {
                        new ButtonInfo(
                                R.string.device_owner_user_restriction_set,
                                CommandReceiverActivity.createSetDeviceOwnerUserRestrictionIntent(
                                        UserManager.DISALLOW_FACTORY_RESET, true)),
                        new ButtonInfo(
                                R.string.device_owner_user_restriction_unset,
                                CommandReceiverActivity.createSetDeviceOwnerUserRestrictionIntent(
                                        UserManager.DISALLOW_FACTORY_RESET, false))
        }));

        // DISALLOW_CONFIG_BLUETOOTH
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            adapter.add(createInteractiveTestItem(this, DISALLOW_CONFIG_BT_ID,
                    R.string.device_owner_disallow_config_bt,
                    R.string.device_owner_disallow_config_bt_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_set,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_CONFIG_BLUETOOTH, true)),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Utils.isTV(this) ? ACTION_CONNECT_INPUT
                                            : Settings.ACTION_BLUETOOTH_SETTINGS)),
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_unset,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_CONFIG_BLUETOOTH, false))
            }));
        }

        // DISALLOW_USB_FILE_TRANSFER
        if (FeatureUtil.isUsbFileTransferSupported(this)) {
            adapter.add(createInteractiveTestItem(this, DISALLOW_USB_FILE_TRANSFER_ID,
                    R.string.device_owner_disallow_usb_file_transfer_test,
                    R.string.device_owner_disallow_usb_file_transfer_test_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_set,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_USB_FILE_TRANSFER, true)),
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_unset,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_USB_FILE_TRANSFER, false))
                    }));
        }

        // DISABLE_STATUS_BAR_TEST
        if (FeatureUtil.isStatusBarSupported(this)) {
            adapter.add(createInteractiveTestItem(this, DISABLE_STATUS_BAR_TEST_ID,
                    R.string.device_owner_disable_statusbar_test,
                    R.string.device_owner_disable_statusbar_test_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_disable_statusbar_button,
                                    createIntentForDisablingKeyguardOrStatusBar(this,
                                            CommandReceiverActivity.COMMAND_SET_STATUSBAR_DISABLED,
                                            /* disabled= */ true)),
                            new ButtonInfo(
                                    R.string.device_owner_reenable_statusbar_button,
                                    createIntentForDisablingKeyguardOrStatusBar(this,
                                            CommandReceiverActivity.COMMAND_SET_STATUSBAR_DISABLED,
                                            /* disabled= */ false))
                    }));
        }

        // Without PIN/Password watches don't have any lockscreen, so this policy isn't applicable
        // setKeyguardDisabled
        if (FeatureUtil.isKeyguardShownWhenUserDoesntHaveCredentials(this)
                && Utils.isLockscreenSupported(this)) {
            adapter.add(createInteractiveTestItem(this, DISABLE_KEYGUARD_TEST_ID,
                    R.string.device_owner_disable_keyguard_test,
                    R.string.device_owner_disable_keyguard_test_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_disable_keyguard_button,
                                    createIntentForDisablingKeyguardOrStatusBar(this,
                                            CommandReceiverActivity.COMMAND_SET_KEYGUARD_DISABLED,
                                            /* disabled= */ true)),
                            new ButtonInfo(
                                    R.string.device_owner_reenable_keyguard_button,
                                    createIntentForDisablingKeyguardOrStatusBar(this,
                                            CommandReceiverActivity.COMMAND_SET_KEYGUARD_DISABLED,
                                            /* disabled= */ false))
                    }));
        }

        // setLockTaskFeatures
        if (FeatureUtil.isLockTaskSupported(this)) {
            final Intent lockTaskUiTestIntent = new Intent(this, LockTaskUiTestActivity.class);
            lockTaskUiTestIntent.putExtra(LockTaskUiTestActivity.EXTRA_TEST_ID,
                    LOCK_TASK_UI_TEST_ID);
            adapter.add(createTestItem(this, LOCK_TASK_UI_TEST_ID,
                    R.string.device_owner_lock_task_ui_test,
                    lockTaskUiTestIntent));
        }

        // setUserIcon
        adapter.add(createInteractiveTestItem(this, SET_USER_ICON_TEST_ID,
                R.string.device_owner_set_user_icon,
                R.string.device_owner_set_user_icon_instruction,
                new ButtonInfo[] {
                        new ButtonInfo(
                                R.string.device_owner_set_user_icon_button,
                                createSetUserIconIntent(R.drawable.user_icon_1)),
                        new ButtonInfo(
                                R.string.disallow_set_user_icon,
                                CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                        UserManager.DISALLOW_SET_USER_ICON, true)),
                        new ButtonInfo(
                                R.string.device_owner_set_user_icon2_button,
                                createSetUserIconIntent(R.drawable.user_icon_2)),
                        new ButtonInfo(
                                R.string.device_owner_settings_go,
                                new Intent(Settings.ACTION_SETTINGS)),
                        new ButtonInfo(
                                R.string.device_owner_user_restriction_unset,
                                CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                        UserManager.DISALLOW_SET_USER_ICON, false))
        }));

        // setPermissionGrantState
        adapter.add(createTestItem(this, CHECK_PERMISSION_LOCKDOWN_TEST_ID,
                R.string.device_profile_owner_permission_lockdown_test,
                new Intent(PermissionLockdownTestActivity.ACTION_CHECK_PERMISSION_LOCKDOWN)));

        // Policy Transparency
        final Intent policyTransparencyTestIntent = new Intent(this,
                PolicyTransparencyTestListActivity.class);
        policyTransparencyTestIntent.putExtra(
                PolicyTransparencyTestListActivity.EXTRA_MODE,
                PolicyTransparencyTestListActivity.MODE_DEVICE_OWNER);
        // So that PolicyTransparencyTestListActivity knows which test to update with the result:
        policyTransparencyTestIntent.putExtra(
                PolicyTransparencyTestActivity.EXTRA_TEST_ID, POLICY_TRANSPARENCY_TEST_ID);
        adapter.add(createTestItem(this, POLICY_TRANSPARENCY_TEST_ID,
                R.string.device_profile_owner_policy_transparency_test,
                policyTransparencyTestIntent));

        // Enterprise Privacy
        final Intent enterprisePolicyTestIntent = new Intent(this,
                EnterprisePrivacyTestListActivity.class);
        enterprisePolicyTestIntent.putExtra(
                EnterprisePrivacyTestListActivity.EXTRA_TEST_ID, ENTERPRISE_PRIVACY_TEST_ID);
        adapter.add(createTestItem(this, ENTERPRISE_PRIVACY_TEST_ID,
                R.string.enterprise_privacy_test,
                enterprisePolicyTestIntent));

        if (FeatureUtil.supportManagedSecondaryUsers(this)) {
            // Managed user
            adapter.add(createInteractiveTestItem(this, MANAGED_USER_TEST_ID,
                    R.string.managed_user_test,
                    R.string.managed_user_positive_tests_instructions,
                    new ButtonInfo[]{
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    createCreateManagedUserIntent())}));

            // User switcher message
            adapter.add(createInteractiveTestItem(this, USER_SWITCHER_MESSAGE_TEST_ID,
                    R.string.device_owner_user_switcher_message,
                    R.string.device_owner_user_switcher_message_info,
                    new ButtonInfo[]{
                            new ButtonInfo(
                                    R.string.device_owner_with_user_switcher_message,
                                    createWithUserSwitcherMessageIntent()),
                            new ButtonInfo(
                                    R.string.device_owner_without_user_switcher_message,
                                    createWithoutUserSwitcherMessageIntent())}));

            // Enable logout
            adapter.add(createInteractiveTestItem(this, ENABLE_LOGOUT_TEST_ID,
                    R.string.device_owner_enable_logout,
                    R.string.device_owner_enable_logout_info,
                    new ButtonInfo[]{
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    createEnableLogoutIntent())}));

            // DISALLOW_USER_SWITCH
            adapter.add(createInteractiveTestItem(this, DISALLOW_USER_SWITCH_TEST_ID,
                    R.string.device_owner_disallow_user_switch,
                    R.string.device_owner_disallow_user_switch_info,
                    new ButtonInfo[]{
                            new ButtonInfo(R.string.device_owner_disallow_user_switch_create_user,
                                    createCreateManagedUserWithoutSetupIntent()),
                            new ButtonInfo(R.string.device_owner_user_restriction_set,
                                    CommandReceiverActivity
                                            .createSetDeviceOwnerUserRestrictionIntent(
                                                    UserManager.DISALLOW_USER_SWITCH,
                                                    /* enforced= */ true)),
                            new ButtonInfo(R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_USER_SETTINGS)),
                            new ButtonInfo(R.string.device_owner_user_restriction_unset,
                                    CommandReceiverActivity
                                            .createSetDeviceOwnerUserRestrictionIntent(
                                                    UserManager.DISALLOW_USER_SWITCH,
                                                    /* enforced= */ false))
            }));

            // DISALLOW_REMOVE_USER
            adapter.add(createInteractiveTestItem(this, DISALLOW_REMOVE_USER_TEST_ID,
                    R.string.disallow_remove_user,
                    R.string.device_owner_disallow_remove_user_info,
                    new ButtonInfo[]{
                            new ButtonInfo(
                                    R.string.device_owner_disallow_remove_user_create_user,
                                    createCreateManagedUserWithoutSetupIntent()),
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_set,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_REMOVE_USER, true)),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_USER_SETTINGS)),
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_unset,
                                    CommandReceiverActivity.createSetCurrentUserRestrictionIntent(
                                            UserManager.DISALLOW_REMOVE_USER, false)),
                            new ButtonInfo(
                                    R.string.device_owner_remove_secondary_user,
                                    createRemoveSecondaryUsersIntent())
            }));
        }

        // Network logging UI
        adapter.add(createInteractiveTestItem(this, NETWORK_LOGGING_UI_TEST_ID,
                R.string.device_owner_network_logging_ui,
                R.string.device_owner_network_logging_ui_info,
                new ButtonInfo[] {
                        new ButtonInfo(
                                R.string.device_owner_enable_network_logging_button,
                                createEnableNetworkLoggingIntent()),
                        new ButtonInfo(
                                R.string.device_owner_disable_network_logging_button,
                                createDisableNetworkLoggingIntent())}));

        // Customize lock screen message
        if (FeatureUtil.isSwipeToUnlockSupported(this)
                && FeatureUtil.isCustomizeLockScreenMessageSupported(this)
                && Utils.isLockscreenSupported(this)) {
            adapter.add(TestListItem.newTest(this,
                    R.string.device_owner_customize_lockscreen_message,
                    LockscreenMessageTestActivity.class.getName(),
                    new Intent(this, LockscreenMessageTestActivity.class),
                    /* requiredFeatures */ null));
        }

        // setUsbDataSignalingEnabled
        if (!FeatureUtil.isTelevision(this) && canUsbDataSignalingBeDisabled()) {
            adapter.add(createInteractiveTestItem(this, DISABLE_USB_DATA_SIGNALING_TEST_ID,
                    R.string.device_owner_disable_usb_data_signaling_test,
                    R.string.device_owner_disable_usb_data_signaling_test_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_SETTINGS)),
                            new ButtonInfo(
                                    R.string.device_owner_disable_usb_data_signaling_test,
                                    createDisableUsbDataSignalingIntent()),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_SETTINGS)),
                            new ButtonInfo(
                                    R.string.device_owner_enable_usb_data_signaling_test,
                                    createEnableUsbDataSignalingIntent())
                    }));
        }

        // setRequiredPasswordComplexity
        if (Utils.isLockscreenSupported(this) && FeatureUtil.isPasswordSupported(this)) {
            adapter.add(createInteractiveTestItem(this, SET_REQUIRED_PASSWORD_COMPLEXITY_ID,
                    R.string.device_owner_required_password_complexity_test,
                    R.string.device_owner_required_password_complexity_test_info,
                    new ButtonInfo[]{
                            new ButtonInfo(
                                    R.string.set_low_required_password_complexity,
                                    createSetRequiredPasswordComplexityIntent(
                                            DevicePolicyManager.PASSWORD_COMPLEXITY_LOW)),
                            new ButtonInfo(
                                    R.string.set_medium_required_password_complexity,
                                    createSetRequiredPasswordComplexityIntent(
                                            DevicePolicyManager.PASSWORD_COMPLEXITY_MEDIUM)),
                            new ButtonInfo(
                                    R.string.set_high_required_password_complexity,
                                    createSetRequiredPasswordComplexityIntent(
                                            DevicePolicyManager.PASSWORD_COMPLEXITY_HIGH)),
                            new ButtonInfo(
                                    R.string.remove_required_password_complexity,
                                    createSetRequiredPasswordComplexityIntent(
                                            DevicePolicyManager.PASSWORD_COMPLEXITY_NONE)),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_SECURITY_SETTINGS))}));
        }

        // removeDeviceOwner
        adapter.add(createInteractiveTestItem(this, REMOVE_DEVICE_OWNER_TEST_ID,
                R.string.device_owner_remove_device_owner_test,
                R.string.device_owner_remove_device_owner_test_info,
                new ButtonInfo[]{
                        new ButtonInfo(
                                R.string.device_owner_settings_go,
                                new Intent(Settings.ACTION_SECURITY_SETTINGS)),
                        new ButtonInfo(
                                R.string.remove_device_owner_button,
                                createTearDownIntent())}));
    }

    static TestListItem createTestItem(Activity activity, String id, int titleRes,
            Intent intent) {
        intent.putExtra(EXTRA_TEST_ID, id);
        return TestListItem.newTest(activity, titleRes, id, intent, null);
    }

    private Intent createTearDownIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_REMOVE_DEVICE_OWNER);
    }

    private Intent createSetUserIconIntent(int iconRes) {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_SET_USER_ICON)
                .putExtra(CommandReceiverActivity.EXTRA_VALUE, iconRes);
    }

    private Intent createEnableNetworkLoggingIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_ENABLE_NETWORK_LOGGING);
    }

    private Intent createDisableNetworkLoggingIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_DISABLE_NETWORK_LOGGING);
    }

    private Intent createCreateManagedUserIntent() {
        // Set execution start time for counting test execution time.
        mStartTime = System.currentTimeMillis();
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_CREATE_MANAGED_USER);
    }

    private Intent createWithUserSwitcherMessageIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_WITH_USER_SWITCHER_MESSAGE);
    }

    private Intent createWithoutUserSwitcherMessageIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_WITHOUT_USER_SWITCHER_MESSAGE);
    }

    private Intent createEnableLogoutIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_ENABLE_LOGOUT);
    }

    private Intent createCreateManagedUserWithoutSetupIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_CREATE_MANAGED_USER_WITHOUT_SETUP);
    }

    private Intent createRemoveSecondaryUsersIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_REMOVE_SECONDARY_USERS);
    }

    private Intent createEnableUsbDataSignalingIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_ENABLE_USB_DATA_SIGNALING);
    }

    private Intent createDisableUsbDataSignalingIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_DISABLE_USB_DATA_SIGNALING);
    }

    private Intent createSetRequiredPasswordComplexityIntent(int complexity) {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_USE_CURRENT_USER_DPM, true)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_SET_REQUIRED_PASSWORD_COMPLEXITY)
                .putExtra(CommandReceiverActivity.EXTRA_VALUE, complexity);
    }

    private Intent createSetWifiSecurityLevelIntent(int level) {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_SET_WIFI_SECURITY_LEVEL)
                .putExtra(CommandReceiverActivity.EXTRA_VALUE, level);
    }

    private boolean canUsbDataSignalingBeDisabled() {
        DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.canUsbDataSignalingBeDisabled();
    }
}
