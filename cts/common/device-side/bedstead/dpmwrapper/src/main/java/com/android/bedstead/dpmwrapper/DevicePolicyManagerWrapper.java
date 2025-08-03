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
package com.android.bedstead.dpmwrapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.HashMap;

final class DevicePolicyManagerWrapper
        extends TestAppSystemServiceFactory.ServiceManagerWrapper<DevicePolicyManager> {

    private static final String TAG = DevicePolicyManagerWrapper.class.getSimpleName();

    private static final HashMap<Context, DevicePolicyManager> sSpies = new HashMap<>();

    @Override
    DevicePolicyManager getWrapper(Context context, DevicePolicyManager dpm, Answer<?> answer) {
        int userId = context.getUserId();
        DevicePolicyManager spy = sSpies.get(context);
        if (spy != null) {
            Log.d(TAG, "getWrapper(): returning cached spy for user " + userId);
            return spy;
        }

        spy = Mockito.spy(dpm);
        String spyString = "DevicePolicyManagerWrapper#" + System.identityHashCode(spy);
        Log.d(TAG, "get(): created spy for user " + context.getUserId() + ": " + spyString);


        // TODO(b/176993670): ideally there should be a way to automatically mock all DPM methods,
        // but that's probably not doable, as there is no contract (such as an interface) to specify
        // which ones should be spied and which ones should not (in fact, if there was an interface,
        // we wouldn't need Mockito and could wrap the calls using java's DynamicProxy
        try {
            doReturn(spyString).when(spy).toString();

            // Basic methods used by most tests
            doAnswer(answer).when(spy).isAdminActive(any());
            doAnswer(answer).when(spy).isDeviceOwnerApp(any());
            doAnswer(answer).when(spy).isManagedProfile(any());
            doAnswer(answer).when(spy).isProfileOwnerApp(any());
            doAnswer(answer).when(spy).isAffiliatedUser();

            // Used by SetTimeTest
            doAnswer(answer).when(spy).setTime(any(), anyLong());
            doAnswer(answer).when(spy).setTimeZone(any(), any());
            doAnswer(answer).when(spy).setGlobalSetting(any(), any(), any());

            // Used by UserControlDisabledPackagesTest
            doAnswer(answer).when(spy).setUserControlDisabledPackages(any(), any());
            doAnswer(answer).when(spy).getUserControlDisabledPackages(any());

            // Used by DeviceOwnerProvisioningTest
            doAnswer(answer).when(spy).enableSystemApp(any(), any(String.class));
            doAnswer(answer).when(spy).enableSystemApp(any(), any(Intent.class));
            doAnswer(answer).when(spy).canAdminGrantSensorsPermissions();

            // Used by NetworkLoggingTest
            doAnswer(answer).when(spy).retrieveNetworkLogs(any(), anyLong());
            doAnswer(answer).when(spy).setNetworkLoggingEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).isNetworkLoggingEnabled(any());

            // Used by CtsVerifier
            doAnswer(answer).when(spy).addUserRestriction(any(), any());
            doAnswer(answer).when(spy).clearUserRestriction(any(), any());
            doAnswer(answer).when(spy).clearDeviceOwnerApp(any());
            doAnswer(answer).when(spy).setKeyguardDisabledFeatures(any(), anyInt());
            doAnswer(answer).when(spy).setPasswordQuality(any(), anyInt());
            doAnswer(answer).when(spy).setMaximumTimeToLock(any(), anyInt());
            doAnswer(answer).when(spy).setPermittedAccessibilityServices(any(), any());
            doAnswer(answer).when(spy).setPermittedInputMethods(any(), any());
            doAnswer(answer).when(spy).setDeviceOwnerLockScreenInfo(any(), any());
            doAnswer(answer).when(spy).setKeyguardDisabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setAutoTimeRequired(any(), anyBoolean());
            doAnswer(answer).when(spy).setStatusBarDisabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setOrganizationName(any(), any());
            doAnswer(answer).when(spy).setSecurityLoggingEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setPermissionGrantState(any(), any(), any(), anyInt());
            doAnswer(answer).when(spy).clearPackagePersistentPreferredActivities(any(), any());
            doAnswer(answer).when(spy).setAlwaysOnVpnPackage(any(), any(), anyBoolean());
            doAnswer(answer).when(spy).setRecommendedGlobalProxy(any(), any());
            doAnswer(answer).when(spy).uninstallCaCert(any(), any());
            doAnswer(answer).when(spy).setMaximumFailedPasswordsForWipe(any(), anyInt());
            doAnswer(answer).when(spy).setSecureSetting(any(), any(), any());
            doAnswer(answer).when(spy).setAffiliationIds(any(), any());
            doAnswer(answer).when(spy).setStartUserSessionMessage(any(), any());
            doAnswer(answer).when(spy).setEndUserSessionMessage(any(), any());
            doAnswer(answer).when(spy).setLogoutEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).removeUser(any(), any());
            doAnswer(answer).when(spy).setMinimumRequiredWifiSecurityLevel(anyInt());
            doAnswer(answer).when(spy).setWifiSsidPolicy(any());

            // Used by DevicePolicySafetyCheckerIntegrationTest
            doAnswer(answer).when(spy).createAndManageUser(any(), any(), any(), any(), anyInt());
            doAnswer(answer).when(spy).lockNow();
            doAnswer(answer).when(spy).lockNow(anyInt());
            doAnswer(answer).when(spy).logoutUser(any());
            doAnswer(answer).when(spy).reboot(any());
            doAnswer(answer).when(spy).removeActiveAdmin(any());
            doAnswer(answer).when(spy).removeKeyPair(any(), any());
            doAnswer(answer).when(spy).requestBugreport(any());
            doAnswer(answer).when(spy).setAlwaysOnVpnPackage(any(), any(), anyBoolean(), any());
            doAnswer(answer).when(spy).setApplicationHidden(any(), any(), anyBoolean());
            doAnswer(answer).when(spy).setApplicationRestrictions(any(), any(), any());
            doAnswer(answer).when(spy).setCameraDisabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setFactoryResetProtectionPolicy(any(), any());
            doAnswer(answer).when(spy).setGlobalPrivateDnsModeOpportunistic(any());
            doAnswer(answer).when(spy).setKeepUninstalledPackages(any(), any());
            doAnswer(answer).when(spy).setLockTaskFeatures(any(), anyInt());
            doAnswer(answer).when(spy).setLockTaskPackages(any(), any());
            doAnswer(answer).when(spy).setMasterVolumeMuted(any(), anyBoolean());
            doAnswer(answer).when(spy).setOverrideApnsEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).setPermissionPolicy(any(), anyInt());
            doAnswer(answer).when(spy).setRestrictionsProvider(any(), any());
            doAnswer(answer).when(spy).setSystemUpdatePolicy(any(), any());
            doAnswer(answer).when(spy).setTrustAgentConfiguration(any(), any(), any());
            doAnswer(answer).when(spy).startUserInBackground(any(), any());
            doAnswer(answer).when(spy).stopUser(any(), any());
            doAnswer(answer).when(spy).switchUser(any(), any());
            doAnswer(answer).when(spy).wipeData(anyInt(), any());
            doAnswer(answer).when(spy).wipeData(anyInt());

            // Used by ListForegroundAffiliatedUsersTest
            doAnswer(answer).when(spy).listForegroundAffiliatedUsers();

            // Used by UserSessionTest
            doAnswer(answer).when(spy).getStartUserSessionMessage(any());
            doAnswer(answer).when(spy).setStartUserSessionMessage(any(), any());
            doAnswer(answer).when(spy).getEndUserSessionMessage(any());
            doAnswer(answer).when(spy).setEndUserSessionMessage(any(), any());

            // Used by SuspendPackageTest
            doAnswer(answer).when(spy).getPolicyExemptApps();

            // Used by PrivacyDeviceOwnerTest
            doAnswer(answer).when(spy).getDeviceOwner();

            // Used by AdminActionBookkeepingTest
            doAnswer(answer).when(spy).getDeviceOwnerOrganizationName();
            doAnswer(answer).when(spy).setOrganizationName(any(), any());
            doAnswer(answer).when(spy).retrieveSecurityLogs(any());
            doAnswer(answer).when(spy).getLastSecurityLogRetrievalTime();
            doAnswer(answer).when(spy).getLastBugReportRequestTime();
            doAnswer(answer).when(spy).isDeviceManaged();
            doAnswer(answer).when(spy).isCurrentInputMethodSetByOwner();
            doAnswer(answer).when(spy).installCaCert(any(), any());
            doAnswer(answer).when(spy).getOwnerInstalledCaCerts(any());
            doAnswer(answer).when(spy).retrievePreRebootSecurityLogs(any());
            doAnswer(answer).when(spy).getLastNetworkLogRetrievalTime();

            // Used by PrivateDnsPolicyTest
            doAnswer(answer).when(spy).getGlobalPrivateDnsHost(any());
            doAnswer(answer).when(spy).getGlobalPrivateDnsMode(any());
            doAnswer(answer).when(spy).setGlobalPrivateDnsModeSpecifiedHost(any(), any());

            // Used by StorageEncryptionTest
            doAnswer(answer).when(spy).getStorageEncryptionStatus();
            doAnswer(answer).when(spy).setStorageEncryption(any(), anyBoolean());

            // Used by AdminConfiguredNetworksTest
            doAnswer(answer).when(spy).setConfiguredNetworksLockdownState(any(), anyBoolean());

            // Used by SecurityLoggingTest
            doAnswer(answer).when(spy).isSecurityLoggingEnabled(any());
            doAnswer(answer).when(spy).setDelegatedScopes(any(), any(), any());
            doAnswer(answer).when(spy).retrieveSecurityLogs(any());

            // Used by WifiTest
            doAnswer(answer).when(spy).getWifiMacAddress(any());

            // Used by AdminConfiguredNetworksTest
            doAnswer(answer).when(spy).hasLockdownAdminConfiguredNetworks(any());

            // Used by DevicePolicyLoggingTest
            doAnswer(answer).when(spy).getAutoTimeEnabled(any());

            // Used by FactoryResetProtectionPolicyTest
            doAnswer(answer).when(spy).getFactoryResetProtectionPolicy(any());

            // Used by DefaultSmsApplicationTest
            doAnswer(answer).when(spy).setDefaultSmsApplication(any(), any());

            // Used by OverrideApnTest
            doAnswer(answer).when(spy).addOverrideApn(any(), any());
            doAnswer(answer).when(spy).updateOverrideApn(any(), anyInt(), any());
            doAnswer(answer).when(spy).removeOverrideApn(any(), anyInt());
            doAnswer(answer).when(spy).getOverrideApns(any());
            doAnswer(answer).when(spy).isOverrideApnEnabled(any());

            // Used for DevicePolicyLoggingTest.
            doAnswer(answer).when(spy).setPasswordMinimumLength(any(), anyInt());
            doAnswer(answer).when(spy).setPasswordMinimumNumeric(any(), anyInt());
            doAnswer(answer).when(spy).setPasswordMinimumNonLetter(any(), anyInt());
            doAnswer(answer).when(spy).setPasswordMinimumLetters(any(), anyInt());
            doAnswer(answer).when(spy).setPasswordMinimumLowerCase(any(), anyInt());
            doAnswer(answer).when(spy).setPasswordMinimumUpperCase(any(), anyInt());
            doAnswer(answer).when(spy).setPasswordMinimumSymbols(any(), anyInt());
            doAnswer(answer).when(spy).setRequiredPasswordComplexity(anyInt());
            doAnswer(answer).when(spy).setUninstallBlocked(any(), any(), anyBoolean());
            doAnswer(answer).when(spy).setPreferentialNetworkServiceEnabled(anyBoolean());
            doAnswer(answer).when(spy).setPersonalAppsSuspended(any(), anyBoolean());

            // Used by PasswordRequirementsTest
            doAnswer(answer).when(spy).getPasswordMinimumLength(any());
            doAnswer(answer).when(spy).getPasswordMinimumNumeric(any());
            doAnswer(answer).when(spy).getPasswordMinimumLetters(any());
            doAnswer(answer).when(spy).getPasswordMinimumUpperCase(any());
            doAnswer(answer).when(spy).getPasswordMinimumLowerCase(any());
            doAnswer(answer).when(spy).getPasswordMinimumNonLetter(any());
            doAnswer(answer).when(spy).getPasswordMinimumSymbols(any());

            // Used by SecurityLoggingTest
            doAnswer(answer).when(spy).getDelegatedScopes(any(), any());
            doAnswer(answer).when(spy).setPasswordExpirationTimeout(any(), anyLong());
            doAnswer(answer).when(spy).setPasswordHistoryLength(any(), anyInt());
            doAnswer(answer).when(spy).setMaximumFailedPasswordsForWipe(any(), anyInt());

            // Used by AccessibilityServicesTest
            doAnswer(answer).when(spy).getPermittedAccessibilityServices(any());

            // Used by InputMethodsTest
            doAnswer(answer).when(spy).getPermittedInputMethods(any());

            // Used by CommonCriteriaModeTest
            doAnswer(answer).when(spy).setCommonCriteriaModeEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).isCommonCriteriaModeEnabled(any());

            // Used by AppRestrictionsDelegateTest
            doAnswer(answer).when(spy).getApplicationRestrictions(any(), any());

            // Used by PackageAccessDelegateTest
            doAnswer(answer).when(spy).isApplicationHidden(any(), any());
            doAnswer(answer).when(spy).isPackageSuspended(any(), any());
            doAnswer(answer).when(spy).setPackagesSuspended(any(), any(), anyBoolean());

            // Used by PermissionGrantDelegateTest
            doAnswer(answer).when(spy).getPermissionGrantState(any(), any(), any());
            doAnswer(answer).when(spy).getPermissionPolicy(any());

            // Used by BlockUninstallDelegateTest
            doAnswer(answer).when(spy).isUninstallBlocked(any(), any());

            // Used By DelegationTest
            doAnswer(answer).when(spy).getDelegatePackages(any(), any());

            // Used by TrustAgentInfoTest
            doAnswer(answer).when(spy).getTrustAgentConfiguration(any(), any());

            // Used by BackupServiceActiveTest
            doAnswer(answer).when(spy).setBackupServiceEnabled(any(), anyBoolean());
            doAnswer(answer).when(spy).isBackupServiceEnabled(any());

            // Used by PendingSystemUpdateTest
            doAnswer(answer).when(spy).notifyPendingSystemUpdate(anyLong());
            doAnswer(answer).when(spy).getPendingSystemUpdate(any());

            // Used by AffiliationTest (GTS)
            doAnswer(answer).when(spy).getAffiliationIds(any());

            // Used by LockScreenInfoTest
            doAnswer(answer).when(spy).getDeviceOwnerLockScreenInfo();

            // TODO(b/176993670): add more methods below as tests are converted
        } catch (Exception e) {
            // Should never happen, but needs to be catch as some methods declare checked exceptions
            Log.wtf("Exception setting mocks", e);
        }

        sSpies.put(context, spy);
        Log.d(TAG, "getWrapper(): returning new spy for context " + context  + " ("
                + context.getPackageName() + ")" + " and user " + userId);

        return spy;
    }
}
