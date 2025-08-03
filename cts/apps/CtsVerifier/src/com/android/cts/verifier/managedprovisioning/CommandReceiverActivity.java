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

import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
import static android.app.admin.DevicePolicyManager.MAKE_USER_EPHEMERAL;
import static android.app.admin.DevicePolicyManager.SKIP_SETUP_WIZARD;

import static com.android.cts.verifier.Utils.flattenToShortString;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.admin.WifiSsidPolicy;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.net.ProxyInfo;
import android.net.wifi.WifiSsid;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.android.bedstead.dpmwrapper.TestAppSystemServiceFactory;
import com.android.cts.verifier.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CommandReceiverActivity extends Activity {
    private static final String TAG = "CommandReceiverActivity";

    public static final String ACTION_EXECUTE_COMMAND =
            "com.android.cts.verifier.managedprovisioning.action.EXECUTE_COMMAND";
    public static final String EXTRA_COMMAND =
            "com.android.cts.verifier.managedprovisioning.extra.COMMAND";

    public static final String COMMAND_SET_USER_RESTRICTION = "set-user_restriction";
    public static final String COMMAND_DISALLOW_KEYGUARD_UNREDACTED_NOTIFICATIONS =
            "disallow-keyguard-unredacted-notifications";
    public static final String COMMAND_SET_AUTO_TIME_REQUIRED = "set-auto-time-required";
    public static final String COMMAND_SET_GLOBAL_SETTING =
            "set-global-setting";
    public static final String COMMAND_SET_MAXIMUM_TO_LOCK = "set-maximum-time-to-lock";
    public static final String COMMAND_SET_KEYGUARD_DISABLED = "set-keyguard-disabled";
    public static final String COMMAND_SET_LOCK_SCREEN_INFO = "set-lock-screen-info";
    public static final String COMMAND_SET_STATUSBAR_DISABLED = "set-statusbar-disabled";
    public static final String COMMAND_SET_LOCK_TASK_FEATURES = "set-lock-task-features";
    public static final String COMMAND_ALLOW_ONLY_SYSTEM_INPUT_METHODS =
            "allow-only-system-input-methods";
    public static final String COMMAND_ALLOW_ONLY_SYSTEM_ACCESSIBILITY_SERVICES =
            "allow-only-system-accessibility-services";
    public static final String COMMAND_CLEAR_POLICIES = "clear-policies";
    public static final String COMMAND_REMOVE_DEVICE_OWNER = "remove-device-owner";
    public static final String COMMAND_REQUEST_BUGREPORT = "request-bugreport";
    public static final String COMMAND_SET_USER_ICON = "set-user-icon";
    public static final String COMMAND_RETRIEVE_NETWORK_LOGS = "retrieve-network-logs";
    public static final String COMMAND_RETRIEVE_SECURITY_LOGS = "retrieve-security-logs";
    public static final String COMMAND_SET_ORGANIZATION_NAME = "set-organization-name";
    public static final String COMMAND_ENABLE_NETWORK_LOGGING = "enable-network-logging";
    public static final String COMMAND_DISABLE_NETWORK_LOGGING = "disable-network-logging";
    public static final String COMMAND_INSTALL_HELPER_PACKAGE = "install-helper-package";
    public static final String COMMAND_UNINSTALL_HELPER_PACKAGE = "uninstall-helper-package";
    public static final String COMMAND_SET_PERMISSION_GRANT_STATE = "set-permission-grant-state";
    public static final String COMMAND_ADD_PERSISTENT_PREFERRED_ACTIVITIES =
            "add-persistent-preferred-activities";
    public static final String COMMAND_CLEAR_PERSISTENT_PREFERRED_ACTIVITIES =
            "clear-persistent-preferred-activities";
    public static final String COMMAND_CREATE_MANAGED_PROFILE = "create-managed-profile";
    public static final String COMMAND_REMOVE_MANAGED_PROFILE = "remove-managed-profile";
    public static final String COMMAND_SET_ALWAYS_ON_VPN = "set-always-on-vpn";
    public static final String COMMAND_CLEAR_ALWAYS_ON_VPN = "clear-always-on-vpn";
    public static final String COMMAND_SET_GLOBAL_HTTP_PROXY = "set-global-http-proxy";
    public static final String COMMAND_CLEAR_GLOBAL_HTTP_PROXY = "clear-global-http-proxy";
    public static final String COMMAND_INSTALL_CA_CERT = "install-ca-cert";
    public static final String COMMAND_CLEAR_CA_CERT = "clear-ca-cert";
    public static final String COMMAND_SET_MAXIMUM_PASSWORD_ATTEMPTS =
            "set-maximum-password-attempts";
    public static final String COMMAND_CLEAR_MAXIMUM_PASSWORD_ATTEMPTS =
            "clear-maximum-password-attempts";
    public static final String COMMAND_SET_DEFAULT_IME = "set-default-ime";
    public static final String COMMAND_CLEAR_DEFAULT_IME = "clear-default-ime";
    public static final String COMMAND_CREATE_MANAGED_USER = "create-managed-user";
    public static final String COMMAND_CREATE_MANAGED_USER_WITHOUT_SETUP =
            "create-managed-user-without-setup";
    public static final String COMMAND_REMOVE_SECONDARY_USERS = "remove-secondary-users";
    public static final String COMMAND_WITH_USER_SWITCHER_MESSAGE = "with-user-switcher-message";
    public static final String COMMAND_WITHOUT_USER_SWITCHER_MESSAGE =
            "without-user-switcher-message";
    public static final String COMMAND_ENABLE_LOGOUT = "enable-logout";
    public static final String COMMAND_DISABLE_USB_DATA_SIGNALING = "disable-usb-data-signaling";
    public static final String COMMAND_ENABLE_USB_DATA_SIGNALING = "enable-usb-data-signaling";
    public static final String COMMAND_SET_REQUIRED_PASSWORD_COMPLEXITY =
            "set-required-password-complexity";
    public static final String COMMAND_SET_WIFI_SECURITY_LEVEL = "set-wifi-security-level";
    public static final String COMMAND_SET_SSID_ALLOWLIST = "set-ssid-allowlist";
    public static final String COMMAND_SET_SSID_DENYLIST = "set-ssid-denylist";
    public static final String COMMAND_CHECK_NEW_USER_DISCLAIMER = "check-new-user-disclaimer";

    public static final String EXTRA_USER_RESTRICTION =
            "com.android.cts.verifier.managedprovisioning.extra.USER_RESTRICTION";
    public static final String EXTRA_USE_CURRENT_USER_DPM =
            "com.android.cts.verifier.managedprovisioning.extra.USE_CURRENT_USER_DPM";
    public static final String EXTRA_SETTING =
            "com.android.cts.verifier.managedprovisioning.extra.SETTING";
    // This extra can be used along with a command extra to set policy to
    // specify if that policy is enforced or not.
    public static final String EXTRA_ENFORCED =
            "com.android.cts.verifier.managedprovisioning.extra.ENFORCED";
    public static final String EXTRA_VALUE =
            "com.android.cts.verifier.managedprovisioning.extra.VALUE";
    public static final String EXTRA_ORGANIZATION_NAME =
            "com.android.cts.verifier.managedprovisioning.extra.ORGANIZATION_NAME";
    public static final String EXTRA_PERMISSION =
            "com.android.cts.verifier.managedprovisioning.extra.PERMISSION";
    public static final String EXTRA_GRANT_STATE =
            "com.android.cts.verifier.managedprovisioning.extra.GRANT_STATE";

    // We care about installing and uninstalling only. It does not matter what apk is used.
    // NotificationBot.apk is a good choice because it comes bundled with the CTS verifier.
    protected static final String HELPER_APP_LOCATION = "/sdcard/NotificationBot.apk";
    protected static final String HELPER_APP_PKG = "com.android.cts.robot";

    public static final String ACTION_INSTALL_COMPLETE =
            "com.android.cts.verifier.managedprovisioning.action.ACTION_INSTALL_COMPLETE";
    public static final String ACTION_UNINSTALL_COMPLETE =
            "com.android.cts.verifier.managedprovisioning.action.ACTION_UNINSTALL_COMPLETE";

    /*
     * The CA cert below is the content of cacert.pem as generated by:
     *
     * openssl req -new -x509 -days 3650 -extensions v3_ca -keyout cakey.pem -out cacert.pem
     */
    private static final String TEST_CA =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIDXTCCAkWgAwIBAgIJAK9Tl/F9V8kSMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV\n" +
            "BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX\n" +
            "aWRnaXRzIFB0eSBMdGQwHhcNMTUwMzA2MTczMjExWhcNMjUwMzAzMTczMjExWjBF\n" +
            "MQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50\n" +
            "ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n" +
            "CgKCAQEAvItOutsE75WBTgTyNAHt4JXQ3JoseaGqcC3WQij6vhrleWi5KJ0jh1/M\n" +
            "Rpry7Fajtwwb4t8VZa0NuM2h2YALv52w1xivql88zce/HU1y7XzbXhxis9o6SCI+\n" +
            "oVQSbPeXRgBPppFzBEh3ZqYTVhAqw451XhwdA4Aqs3wts7ddjwlUzyMdU44osCUg\n" +
            "kVg7lfPf9sTm5IoHVcfLSCWH5n6Nr9sH3o2ksyTwxuOAvsN11F/a0mmUoPciYPp+\n" +
            "q7DzQzdi7akRG601DZ4YVOwo6UITGvDyuAAdxl5isovUXqe6Jmz2/myTSpAKxGFs\n" +
            "jk9oRoG6WXWB1kni490GIPjJ1OceyQIDAQABo1AwTjAdBgNVHQ4EFgQUH1QIlPKL\n" +
            "p2OQ/AoLOjKvBW4zK3AwHwYDVR0jBBgwFoAUH1QIlPKLp2OQ/AoLOjKvBW4zK3Aw\n" +
            "DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAcMi4voMMJHeQLjtq8Oky\n" +
            "Azpyk8moDwgCd4llcGj7izOkIIFqq/lyqKdtykVKUWz2bSHO5cLrtaOCiBWVlaCV\n" +
            "DYAnnVLM8aqaA6hJDIfaGs4zmwz0dY8hVMFCuCBiLWuPfiYtbEmjHGSmpQTG6Qxn\n" +
            "ZJlaK5CZyt5pgh5EdNdvQmDEbKGmu0wpCq9qjZImwdyAul1t/B0DrsWApZMgZpeI\n" +
            "d2od0VBrCICB1K4p+C51D93xyQiva7xQcCne+TAnGNy9+gjQ/MyR8MRpwRLv5ikD\n" +
            "u0anJCN8pXo6IMglfMAsoton1J6o5/ae5uhC6caQU8bNUsCK570gpNfjkzo6rbP0\n" +
            "wQ==\n" +
            "-----END CERTIFICATE-----";

    private ComponentName mAdmin;
    private DevicePolicyManager mDpm;
    private UserManager mUm;
    private ActivityManager mAm;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        try {
            // On phones, the test runs on user 0, which is the Device Owner, but on headless system
            // user mode it runs in a different user.
            // Most DPM operations must be set on device owner user, but a few - like adding user
            // restrictions - must be set in the current user.
            boolean forDeviceOwner = !intent.getBooleanExtra(EXTRA_USE_CURRENT_USER_DPM, false);
            mDpm = TestAppSystemServiceFactory.getDevicePolicyManager(this,
                            DeviceAdminTestReceiver.class, forDeviceOwner);

            mUm = getSystemService(UserManager.class);
            mAm = getSystemService(ActivityManager.class);
            mAdmin = DeviceAdminTestReceiver.getReceiverComponentName();
            final String command = intent.getStringExtra(EXTRA_COMMAND);
            Log.i(TAG, "Command: " + command + " forDeviceOwner: " + forDeviceOwner);
            switch (command) {
                case COMMAND_SET_USER_RESTRICTION: {
                    String restrictionKey = intent.getStringExtra(EXTRA_USER_RESTRICTION);
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    Log.i(TAG, "Setting '" + restrictionKey + "'=" + enforced
                            + " using " + mDpm + " for user "
                            + (forDeviceOwner ? UserHandle.SYSTEM : UserHandle.myUserId()));
                    if (enforced) {
                        mDpm.addUserRestriction(mAdmin, restrictionKey);
                    } else {
                        mDpm.clearUserRestriction(mAdmin, restrictionKey);
                    }
                } break;
                case COMMAND_DISALLOW_KEYGUARD_UNREDACTED_NOTIFICATIONS: {
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    mDpm.setKeyguardDisabledFeatures(mAdmin, enforced
                            ? DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS
                            : 0);
                } break;
                case COMMAND_SET_AUTO_TIME_REQUIRED: {
                    mDpm.setAutoTimeRequired(mAdmin,
                            intent.getBooleanExtra(EXTRA_ENFORCED, false));
                    break;
                }
                case COMMAND_SET_LOCK_SCREEN_INFO: {
                    mDpm.setDeviceOwnerLockScreenInfo(mAdmin, intent.getStringExtra(EXTRA_VALUE));
                    break;
                }
                case COMMAND_SET_MAXIMUM_TO_LOCK: {
                    final long timeInSeconds = Long.parseLong(intent.getStringExtra(EXTRA_VALUE));
                    mDpm.setMaximumTimeToLock(mAdmin,
                            TimeUnit.SECONDS.toMillis(timeInSeconds) /* in milliseconds */);
                } break;
                case COMMAND_SET_KEYGUARD_DISABLED: {
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    KeyguardManager km = this.getSystemService(KeyguardManager.class);
                    if (km.isKeyguardSecure()) {
                        Toast.makeText(this, getString(R.string.device_owner_lockscreen_secure),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        mDpm.setKeyguardDisabled(mAdmin, enforced);
                    }
                } break;
                case COMMAND_SET_STATUSBAR_DISABLED: {
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    Log.d(TAG, "calling setStatusBarDisabled(" + flattenToShortString(mAdmin)
                            + ", " + enforced + ") using " + mDpm + " on user "
                            + UserHandle.myUserId());
                    mDpm.setStatusBarDisabled(mAdmin, enforced);
                } break;
                case COMMAND_SET_LOCK_TASK_FEATURES: {
                    int flags = intent.getIntExtra(EXTRA_VALUE,
                            DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
                    mDpm.setLockTaskFeatures(mAdmin, flags);
                    // If feature HOME is used, we need to allow the current launcher
                    if ((flags & LOCK_TASK_FEATURE_HOME) != 0) {
                        mDpm.setLockTaskPackages(mAdmin,
                                new String[] {getPackageName(), getCurrentLauncherPackage()});
                    } else {
                        mDpm.setLockTaskPackages(mAdmin, new String[] {getPackageName()});
                    }
                } break;
                case COMMAND_ALLOW_ONLY_SYSTEM_INPUT_METHODS: {
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    mDpm.setPermittedInputMethods(mAdmin,
                            enforced ? getEnabledNonSystemImes() : null);
                } break;
                case COMMAND_ALLOW_ONLY_SYSTEM_ACCESSIBILITY_SERVICES: {
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    mDpm.setPermittedAccessibilityServices(mAdmin,
                            enforced ? new ArrayList() : null);
                } break;
                case COMMAND_SET_GLOBAL_SETTING: {
                    final String setting = intent.getStringExtra(EXTRA_SETTING);
                    final String value = intent.getStringExtra(EXTRA_VALUE);
                    Log.d(TAG, "Setting global property '" + setting + "' to '" + value
                            + "' using " + mDpm);
                    mDpm.setGlobalSetting(mAdmin, setting, value);
                } break;
                case COMMAND_REMOVE_DEVICE_OWNER: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        Log.e(TAG, COMMAND_REMOVE_DEVICE_OWNER + ": " + getPackageName()
                                + " is not DO for user " + UserHandle.myUserId());
                        return;
                    }
                    clearAllPoliciesAndRestrictions();
                    Log.i(TAG, "Clearing device owner app " + getPackageName());
                    mDpm.clearDeviceOwnerApp(getPackageName());

                    if (UserManager.isHeadlessSystemUserMode()) {
                        Log.i(TAG, "Clearing profile owner app (" + mAdmin.flattenToString()
                                + " on user " + UserHandle.myUserId());
                        DevicePolicyManager localDpm = getSystemService(DevicePolicyManager.class);
                        localDpm.clearProfileOwner(mAdmin);
                    }

                } break;
                case COMMAND_REQUEST_BUGREPORT: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        Log.i(TAG, "Not requesting bugreport as " + getPackageName() + " is not a "
                                + "DO when asked to " + mDpm);
                        return;
                    }
                    Log.i(TAG, "Requesting a bugreport using " + mDpm);
                    final boolean bugreportStarted = mDpm.requestBugreport(mAdmin);
                    Log.i(TAG, "Bug report started: " + bugreportStarted);
                    if (!bugreportStarted) {
                        Utils.showBugreportNotification(this, getString(
                                R.string.bugreport_already_in_progress),
                                Utils.BUGREPORT_NOTIFICATION_ID);
                    }
                } break;
                case COMMAND_CLEAR_POLICIES: {
                    int mode = intent.getIntExtra(PolicyTransparencyTestListActivity.EXTRA_MODE,
                            PolicyTransparencyTestListActivity.MODE_DEVICE_OWNER);
                    if (mode == PolicyTransparencyTestListActivity.MODE_DEVICE_OWNER) {
                        if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                            return;
                        }
                        clearAllPoliciesAndRestrictions();
                    } else if (mode == PolicyTransparencyTestListActivity.MODE_MANAGED_PROFILE
                            || mode == PolicyTransparencyTestListActivity.MODE_MANAGED_USER) {
                        if (!mDpm.isProfileOwnerApp(getPackageName())) {
                            return;
                        }
                        clearProfileOwnerRelatedPoliciesAndRestrictions(mode);
                    }
                    // No policies need to be cleared for COMP at the moment.
                } break;
                case COMMAND_SET_USER_ICON: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    int iconRes = intent.getIntExtra(EXTRA_VALUE,
                            com.android.cts.verifier.R.drawable.icon);
                    mDpm.setUserIcon(mAdmin, BitmapFactory.decodeResource(getResources(), iconRes));
                } break;
                case COMMAND_RETRIEVE_NETWORK_LOGS: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setNetworkLoggingEnabled(mAdmin, true);
                    mDpm.retrieveNetworkLogs(mAdmin, 0 /* batchToken */);
                    mDpm.setNetworkLoggingEnabled(mAdmin, false);
                } break;
                case COMMAND_RETRIEVE_SECURITY_LOGS: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setSecurityLoggingEnabled(mAdmin, true);
                    mDpm.retrieveSecurityLogs(mAdmin);
                    mDpm.setSecurityLoggingEnabled(mAdmin, false);
                } break;
                case COMMAND_SET_ORGANIZATION_NAME: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setOrganizationName(mAdmin,
                            intent.getStringExtra(EXTRA_ORGANIZATION_NAME));
                } break;
                case COMMAND_ENABLE_NETWORK_LOGGING: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setNetworkLoggingEnabled(mAdmin, true);
                } break;
                case COMMAND_DISABLE_NETWORK_LOGGING: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setNetworkLoggingEnabled(mAdmin, false);
                } break;
                case COMMAND_INSTALL_HELPER_PACKAGE: {
                    installHelperPackage();
                } break;
                case COMMAND_UNINSTALL_HELPER_PACKAGE: {
                    uninstallHelperPackage();
                } break;
                case COMMAND_SET_PERMISSION_GRANT_STATE: {
                    String pkgName = getPackageName();
                    String permission = intent.getStringExtra(EXTRA_PERMISSION);
                    int grantState = intent.getIntExtra(EXTRA_GRANT_STATE,
                            DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
                    String action;
                    switch (grantState) {
                        case DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED:
                            action = "Granting " + permission;
                            break;
                        case DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED:
                            action = "Denying " + permission;
                            break;
                        case DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT:
                            action = "Setting " + permission + " to default state";
                            break;
                        default:
                            action = "Setting grantState of " + permission + " to " + grantState;
                    }
                    Log.d(TAG, action + " to " + pkgName + " using " + mDpm);
                    int stateBefore = mDpm.getPermissionGrantState(mAdmin, pkgName, permission);
                    mDpm.setPermissionGrantState(mAdmin, pkgName, permission, grantState);
                    int stateAfter = mDpm.getPermissionGrantState(mAdmin, pkgName, permission);
                    Log.d(TAG, "Grant state: before=" + stateBefore + ", after=" + stateAfter);
                } break;
                case COMMAND_ADD_PERSISTENT_PREFERRED_ACTIVITIES: {
                    final ComponentName componentName =
                            EnterprisePrivacyTestDefaultAppActivity.COMPONENT_NAME;
                    IntentFilter filter;
                    // Camera
                    filter = new IntentFilter();
                    filter.addAction(MediaStore.ACTION_IMAGE_CAPTURE);
                    filter.addAction(MediaStore.ACTION_VIDEO_CAPTURE);
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    // Map
                    filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_VIEW);
                    filter.addDataScheme("geo");
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    // E-mail
                    filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_SENDTO);
                    filter.addAction(Intent.ACTION_SEND);
                    filter.addAction(Intent.ACTION_SEND_MULTIPLE);
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    // Calendar
                    filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_INSERT);
                    filter.addDataType("vnd.android.cursor.dir/event");
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    // Contacts
                    filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_PICK);
                    filter.addDataType(ContactsContract.Contacts.CONTENT_TYPE);
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    // Dialer
                    filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_DIAL);
                    filter.addAction(Intent.ACTION_CALL);
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    getPackageManager().setComponentEnabledSetting(componentName,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
                } break;
                case COMMAND_CLEAR_PERSISTENT_PREFERRED_ACTIVITIES: {
                    mDpm.clearPackagePersistentPreferredActivities(mAdmin, getPackageName());
                    getPackageManager().setComponentEnabledSetting(
                            EnterprisePrivacyTestDefaultAppActivity.COMPONENT_NAME,
                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                            PackageManager.DONT_KILL_APP);
                } break;
                case COMMAND_SET_ALWAYS_ON_VPN: {
                    if (!isDeviceOwnerAppOrEquivalent(getPackageName())) {
                        return;
                    }
                    mDpm.setAlwaysOnVpnPackage(mAdmin, getPackageName(),
                            false /* lockdownEnabled */);
                } break;
                case COMMAND_CLEAR_ALWAYS_ON_VPN: {
                    if (!isDeviceOwnerAppOrEquivalent(getPackageName())) {
                        return;
                    }
                    mDpm.setAlwaysOnVpnPackage(mAdmin, null /* vpnPackage */,
                            false /* lockdownEnabled */);
                } break;
                case COMMAND_SET_GLOBAL_HTTP_PROXY: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setRecommendedGlobalProxy(mAdmin,
                            ProxyInfo.buildDirectProxy("example.com", 123));
                } break;
                case COMMAND_CLEAR_GLOBAL_HTTP_PROXY: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setRecommendedGlobalProxy(mAdmin, null);
                } break;
                case COMMAND_INSTALL_CA_CERT: {
                    if (!isDeviceOwnerAppOrEquivalent(getPackageName())) {
                        return;
                    }
                    mDpm.installCaCert(mAdmin, TEST_CA.getBytes());
                } break;
                case COMMAND_CLEAR_CA_CERT: {
                    if (!isDeviceOwnerAppOrEquivalent(getPackageName())) {
                        return;
                    }
                    mDpm.uninstallCaCert(mAdmin, TEST_CA.getBytes());
                } break;
                case COMMAND_SET_MAXIMUM_PASSWORD_ATTEMPTS: {
                    if (!isDeviceOwner()) return;
                    int max = 100;
                    Log.d(TAG, "Setting maximum password attempts to " + max + " using" + mDpm);
                    mDpm.setMaximumFailedPasswordsForWipe(mAdmin, max);
                } break;
                case COMMAND_CLEAR_MAXIMUM_PASSWORD_ATTEMPTS: {
                    if (!isDeviceOwner()) return;
                    Log.d(TAG, "Clearing maximum password attempts using" + mDpm);
                    mDpm.setMaximumFailedPasswordsForWipe(mAdmin, 0);
                } break;
                case COMMAND_SET_DEFAULT_IME: {
                    if (!isDeviceOwner()) return;
                    Log.d(TAG, "Setting " + Settings.Secure.DEFAULT_INPUT_METHOD + " using "
                            + mDpm);
                    mDpm.setSecureSetting(mAdmin, Settings.Secure.DEFAULT_INPUT_METHOD,
                            getPackageName());
                } break;
                case COMMAND_CLEAR_DEFAULT_IME: {
                    if (!isDeviceOwner()) return;
                    Log.d(TAG, "Clearing " + Settings.Secure.DEFAULT_INPUT_METHOD + " using "
                            + mDpm);
                    mDpm.setSecureSetting(mAdmin, Settings.Secure.DEFAULT_INPUT_METHOD, null);
                } break;
                case COMMAND_CREATE_MANAGED_USER:{
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    PersistableBundle extras = new PersistableBundle();
                    extras.putBoolean(DeviceAdminTestReceiver.EXTRA_MANAGED_USER_TEST, true);
                    UserHandle userHandle = mDpm.createAndManageUser(mAdmin, "managed user", mAdmin,
                            extras,
                            SKIP_SETUP_WIZARD | MAKE_USER_EPHEMERAL);
                    Log.i(TAG, "Created user " + userHandle + "; setting affiliation ids to "
                            + DeviceAdminTestReceiver.AFFILIATION_ID);
                    mDpm.setAffiliationIds(mAdmin,
                            Collections.singleton(DeviceAdminTestReceiver.AFFILIATION_ID));
                    // TODO(b/204483021): move to helper class / restore after user is logged out
                    if (UserManager.isHeadlessSystemUserMode()) {
                        mAm.setStopUserOnSwitch(ActivityManager.STOP_USER_ON_SWITCH_FALSE);
                    }
                    Log.d(TAG, "Starting user " + userHandle);
                    mDpm.startUserInBackground(mAdmin, userHandle);
                } break;
                case COMMAND_CREATE_MANAGED_USER_WITHOUT_SETUP:{
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    PersistableBundle extras = new PersistableBundle();
                    extras.putBoolean(DeviceAdminTestReceiver.EXTRA_MANAGED_USER_TEST, true);
                    mDpm.createAndManageUser(mAdmin, "managed user", mAdmin, extras, /* flags */ 0);
                } break;
                case COMMAND_REMOVE_SECONDARY_USERS: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    for (UserHandle secondaryUser : mDpm.getSecondaryUsers(mAdmin)) {
                        mDpm.removeUser(mAdmin, secondaryUser);
                    }
                } break;
                case COMMAND_WITH_USER_SWITCHER_MESSAGE: {
                    createAndSwitchUserWithMessage("Start user session", "End user session");
                } break;
                case COMMAND_WITHOUT_USER_SWITCHER_MESSAGE: {
                    createAndSwitchUserWithMessage(null, null);
                } break;
                case COMMAND_ENABLE_LOGOUT: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.addUserRestriction(mAdmin, UserManager.DISALLOW_USER_SWITCH);
                    mDpm.setLogoutEnabled(mAdmin, true);
                    UserHandle userHandle = mDpm.createAndManageUser(mAdmin, "managed user", mAdmin,
                            null, SKIP_SETUP_WIZARD | MAKE_USER_EPHEMERAL);
                    mDpm.switchUser(mAdmin, userHandle);
                } break;
                case COMMAND_DISABLE_USB_DATA_SIGNALING: {
                    mDpm.setUsbDataSignalingEnabled(false);
                    break;
                }
                case COMMAND_ENABLE_USB_DATA_SIGNALING: {
                    mDpm.setUsbDataSignalingEnabled(true);
                    break;
                }
                case COMMAND_SET_REQUIRED_PASSWORD_COMPLEXITY: {
                    int complexity = intent.getIntExtra(EXTRA_VALUE,
                            DevicePolicyManager.PASSWORD_COMPLEXITY_NONE);
                    Log.d(TAG, "calling setRequiredPasswordComplexity(" + complexity + ")");
                    mDpm.setRequiredPasswordComplexity(complexity);
                } break;
                case COMMAND_SET_WIFI_SECURITY_LEVEL: {
                    int level = intent.getIntExtra(EXTRA_VALUE,
                            DevicePolicyManager.WIFI_SECURITY_OPEN);
                    Log.d(TAG, "calling setWifiSecurityLevel(" + level + ")");
                    mDpm.setMinimumRequiredWifiSecurityLevel(level);
                } break;
                case COMMAND_SET_SSID_ALLOWLIST: {
                    String ssid = intent.getStringExtra(EXTRA_VALUE);
                    WifiSsidPolicy policy;
                    if (ssid.isEmpty()) {
                        policy = null;
                    } else {
                        Set<WifiSsid> ssids = new ArraySet<>(Arrays.asList(
                                WifiSsid.fromBytes(ssid.getBytes(StandardCharsets.UTF_8))));
                        policy = new WifiSsidPolicy(
                                WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_ALLOWLIST, ssids);
                    }
                    mDpm.setWifiSsidPolicy(policy);
                } break;
                case COMMAND_SET_SSID_DENYLIST: {
                    String ssid = intent.getStringExtra(EXTRA_VALUE);
                    WifiSsidPolicy policy;
                    if (ssid.isEmpty()) {
                        policy = null;
                    } else {
                        Set<WifiSsid> ssids = new ArraySet<>(Arrays.asList(
                                WifiSsid.fromBytes(ssid.getBytes(StandardCharsets.UTF_8))));
                        policy = new WifiSsidPolicy(
                                WifiSsidPolicy.WIFI_SSID_POLICY_TYPE_DENYLIST, ssids);
                    }
                    mDpm.setWifiSsidPolicy(policy);
                } break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute command: " + intent, e);
        } finally {
            finish();
        }
    }

    /**
     * Checks if {@code CtsVerifier} is the device owner.
     */
    private boolean isDeviceOwner() {
        // Cannot use mDpm as it would be the DPM of the current user on headless system user mode,
        // which would return false
        DevicePolicyManager dpm = TestAppSystemServiceFactory.getDevicePolicyManager(this,
                DeviceAdminTestReceiver.class, /* forDeviceOwner= */ true);
        boolean isIt = dpm.isDeviceOwnerApp(getPackageName());
        Log.v(TAG, "is " + getPackageName() + " DO, using " + dpm + "? " + isIt);
        return isIt;
    }

    /**
     * Checks if the {@code packageName} is a device owner app, or a profile owner app in the
     * headless system user mode.
      */
    private boolean isDeviceOwnerAppOrEquivalent(String packageName) {
        return mDpm.isDeviceOwnerApp(packageName)
                || (UserManager.isHeadlessSystemUserMode() && mDpm.isProfileOwnerApp(packageName));
    }

    private void installHelperPackage() throws Exception {
        if (UserManager.isHeadlessSystemUserMode()) {
            // App was already installed on user 0 (as instructed), so we just install it for the
            // current user - installing directly to current user would be harder as it would
            // require a custom ContentProvider to push the APK into a secondary user using adb
            Log.i(TAG, "installing existing helper app (" + HELPER_APP_PKG + ") using " + mDpm);
            mDpm.installExistingPackage(mAdmin, HELPER_APP_PKG);
            return;
        }
        LogAndSelfUnregisterBroadcastReceiver.register(this, ACTION_INSTALL_COMPLETE);
        final PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
        final PackageInstaller.Session session = packageInstaller.openSession(
                packageInstaller.createSession(new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL)));
        final File file = new File(HELPER_APP_LOCATION);
        Log.i(TAG, "installing helper package from " + file);
        final InputStream in = new FileInputStream(file);
        final OutputStream out = session.openWrite("CommandReceiverActivity", 0, file.length());
        final byte[] buffer = new byte[65536];
        int count;
        while ((count = in.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }
        session.fsync(out);
        in.close();
        out.close();
        session.commit(PendingIntent
                .getBroadcast(this, 0,
                        new Intent(ACTION_INSTALL_COMPLETE).setPackage(getPackageName()),
                        PendingIntent.FLAG_MUTABLE)
                .getIntentSender());
    }

    private void uninstallHelperPackage() {
        LogAndSelfUnregisterBroadcastReceiver.register(this, ACTION_UNINSTALL_COMPLETE);
        PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
        Log.i(TAG, "Uninstalling package " + HELPER_APP_PKG + " using " + packageInstaller);
        try {
            packageInstaller.uninstall(HELPER_APP_PKG, PendingIntent.getBroadcast(this,
                    /* requestCode= */ 0,
                    new Intent(ACTION_UNINSTALL_COMPLETE).setPackage(getPackageName()),
                    PendingIntent.FLAG_MUTABLE).getIntentSender());
        } catch (IllegalArgumentException e) {
            // The package is not installed: that's fine
        }
    }

    private void clearAllPoliciesAndRestrictions() throws Exception {
        Log.d(TAG, "clearAllPoliciesAndRestrictions() started");
        clearProfileOwnerRelatedPolicies();
        clearPolicyTransparencyUserRestriction(
                PolicyTransparencyTestListActivity.MODE_DEVICE_OWNER);

        // There are a few user restrictions that are used, but not for policy transparency
        mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_CONFIG_BLUETOOTH);
        mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_CONFIG_VPN);
        mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_DATA_ROAMING);
        mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_USER_SWITCH);

        mDpm.setDeviceOwnerLockScreenInfo(mAdmin, null);
        mDpm.setKeyguardDisabled(mAdmin, false);
        mDpm.setAutoTimeRequired(mAdmin, false);
        mDpm.setStatusBarDisabled(mAdmin, false);
        mDpm.setOrganizationName(mAdmin, null);
        mDpm.setNetworkLoggingEnabled(mAdmin, false);
        mDpm.setSecurityLoggingEnabled(mAdmin, false);
        mDpm.setPermissionGrantState(mAdmin, getPackageName(),
                Manifest.permission.ACCESS_FINE_LOCATION,
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        mDpm.setPermissionGrantState(mAdmin, getPackageName(), Manifest.permission.RECORD_AUDIO,
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        mDpm.setPermissionGrantState(mAdmin, getPackageName(), Manifest.permission.CAMERA,
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        mDpm.clearPackagePersistentPreferredActivities(mAdmin, getPackageName());
        mDpm.setAlwaysOnVpnPackage(mAdmin, null, false);
        mDpm.setRecommendedGlobalProxy(mAdmin, null);
        mDpm.uninstallCaCert(mAdmin, TEST_CA.getBytes());
        mDpm.setMaximumFailedPasswordsForWipe(mAdmin, 0);
        mDpm.setSecureSetting(mAdmin, Settings.Secure.DEFAULT_INPUT_METHOD, null);
        mDpm.setStartUserSessionMessage(mAdmin, null);
        mDpm.setEndUserSessionMessage(mAdmin, null);
        mDpm.setLogoutEnabled(mAdmin, false);

        uninstallHelperPackage();

        // Must wait until package is uninstalled to reset affiliation ids, otherwise the package
        // cannot be removed on headless system user mode (as caller must be an affiliated PO)
        Log.d(TAG, "resetting affiliation ids");
        mDpm.setAffiliationIds(mAdmin, Collections.emptySet());

        removeManagedProfile();
        getPackageManager().setComponentEnabledSetting(
                EnterprisePrivacyTestDefaultAppActivity.COMPONENT_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
        Log.d(TAG, "clearAllPoliciesAndRestrictions() finished");
    }

    private void clearProfileOwnerRelatedPoliciesAndRestrictions(int mode) {
        clearPolicyTransparencyUserRestriction(mode);
        clearProfileOwnerRelatedPolicies();
    }

    private void clearProfileOwnerRelatedPolicies() {
        Log.d(TAG, "clearProfileOwnerRelatedPolicies() started");
        mDpm.setKeyguardDisabledFeatures(mAdmin, 0);
        mDpm.setPasswordQuality(mAdmin, 0);
        mDpm.setMaximumTimeToLock(mAdmin, 0);
        mDpm.setPermittedAccessibilityServices(mAdmin, null);
        mDpm.setPermittedInputMethods(mAdmin, null);
        Log.d(TAG, "clearProfileOwnerRelatedPolicies() finished");
    }

    private void clearPolicyTransparencyUserRestriction(int mode) {
        for (String userRestriction : UserRestrictions.getUserRestrictionsForPolicyTransparency(
                mode)) {
            mDpm.clearUserRestriction(mAdmin, userRestriction);
        }
    }

    private void removeManagedProfile() {
        for (UserHandle userHandle : mUm.getUserProfiles()) {
            Log.d(TAG, "removing managed profile for " + userHandle);
            mDpm.removeUser(mAdmin, userHandle);
        }
    }

    /**
     * Creates an intent to set the given user restriction using the device owner's {@code dpm}.
     */
    public static Intent createSetDeviceOwnerUserRestrictionIntent(String restriction,
            boolean enforced) {
        return createSetUserRestrictionIntent(restriction, enforced, /* currentUserDpm= */ false);
    }

    /**
     * Creates an intent to set the given user restriction using the current user's {@code dpm}.
     */
    public static Intent createSetCurrentUserRestrictionIntent(String restriction,
            boolean enforced) {
        return createSetUserRestrictionIntent(restriction, enforced, /* currentUserDpm= */ true);
    }

    private static Intent createSetUserRestrictionIntent(String restriction, boolean enforced,
            boolean forceCurrentUserDpm) {
        Log.d(TAG, "createSetUserRestrictionIntent(): restriction=" + restriction
                + ", enforced=" + enforced + ", forceCurrentUserDpm=" + forceCurrentUserDpm);
        Intent intent = new Intent(ACTION_EXECUTE_COMMAND);
        if (forceCurrentUserDpm) {
            intent.putExtra(EXTRA_USE_CURRENT_USER_DPM, true);
        }
        return intent
                .putExtra(EXTRA_COMMAND, COMMAND_SET_USER_RESTRICTION)
                .putExtra(EXTRA_USER_RESTRICTION, restriction)
                .putExtra(EXTRA_ENFORCED, enforced);
    }

    private List<String> getEnabledNonSystemImes() {
        InputMethodManager inputMethodManager = getSystemService(InputMethodManager.class);
        final List<InputMethodInfo> inputMethods = inputMethodManager.getEnabledInputMethodList();
        return inputMethods.stream()
                .filter(inputMethodInfo -> !isSystemInputMethodInfo(inputMethodInfo))
                .map(inputMethodInfo -> inputMethodInfo.getPackageName())
                .filter(packageName -> !packageName.equals(getPackageName()))
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean isSystemInputMethodInfo(InputMethodInfo inputMethodInfo) {
        final ApplicationInfo applicationInfo = inputMethodInfo.getServiceInfo().applicationInfo;
        return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private void createAndSwitchUserWithMessage(String startUserSessionMessage,
            String endUserSessionMessage) {
        if (!mDpm.isDeviceOwnerApp(getPackageName())) {
            return;
        }
        mDpm.setStartUserSessionMessage(mAdmin, startUserSessionMessage);
        mDpm.setEndUserSessionMessage(mAdmin, endUserSessionMessage);
        mDpm.setAffiliationIds(mAdmin,
                Collections.singleton(DeviceAdminTestReceiver.AFFILIATION_ID));

        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean(DeviceAdminTestReceiver.EXTRA_LOGOUT_ON_START, true);
        UserHandle userHandle = mDpm.createAndManageUser(mAdmin, "managed user", mAdmin,
                extras,
                SKIP_SETUP_WIZARD | MAKE_USER_EPHEMERAL);
        // TODO(b/204483021): move to helper class / restore after user is logged out
        if (UserManager.isHeadlessSystemUserMode()) {
            mAm.setStopUserOnSwitch(ActivityManager.STOP_USER_ON_SWITCH_FALSE);
        }
        Log.d(TAG, "Switching to user " + userHandle);
        mDpm.switchUser(mAdmin, userHandle);
    }

    private String getCurrentLauncherPackage() {
        ResolveInfo resolveInfo = getPackageManager()
            .resolveActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return null;
        }

        return resolveInfo.activityInfo.packageName;
    }

    static Intent createIntentForDisablingKeyguardOrStatusBar(Context context, String command,
            boolean disabled) {
        return new Intent(context, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_USE_CURRENT_USER_DPM, true)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND, command)
                .putExtra(CommandReceiverActivity.EXTRA_ENFORCED, disabled);
    }
}
