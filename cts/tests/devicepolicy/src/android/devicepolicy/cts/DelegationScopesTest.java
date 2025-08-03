/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.ACTION_APPLICATION_DELEGATION_SCOPES_CHANGED;
import static android.app.admin.DevicePolicyManager.DELEGATION_APP_RESTRICTIONS;
import static android.app.admin.DevicePolicyManager.DELEGATION_CERT_INSTALL;
import static android.app.admin.DevicePolicyManager.DELEGATION_CERT_SELECTION;
import static android.app.admin.DevicePolicyManager.DELEGATION_NETWORK_LOGGING;
import static android.app.admin.DevicePolicyManager.DELEGATION_SECURITY_LOGGING;
import static android.app.admin.DevicePolicyManager.EXTRA_DELEGATION_SCOPES;
import static android.content.Context.RECEIVER_EXPORTED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.util.Log;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDelegate;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.Delegation;
import com.android.bedstead.harrier.policies.NetworkLoggingDelegation;
import com.android.bedstead.harrier.policies.SecurityLoggingDelegation;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.eventlib.truth.EventLogsSubject;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests that delegation scopes can be correctly set via {@link
 * DevicePolicyManager#setDelegatedScopes(ComponentName, String, List)}, and that subsequent access
 * and app notification works correctly.
 */
@RunWith(BedsteadJUnit4.class)
@Postsubmit(reason = "new test")
@EnsureHasNoDelegate
public final class DelegationScopesTest {

    private static final String TAG = DelegationScopesTest.class.getSimpleName();

    private static final String TEST_SCOPE = DELEGATION_CERT_INSTALL;
    private static final String TEST_SCOPE_2 = DELEGATION_APP_RESTRICTIONS;

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApis sTestApis = new TestApis();
    private static final UserReference sUser = sTestApis.users().instrumented();

    private static final TestApp sTestApp = sDeviceState.testApps()
            .query().whereActivities().isNotEmpty().get();
    private static final TestApp sTestApp2 = sDeviceState.testApps().any();

    @CanSetPolicyTest(policy = Delegation.class)
    public void getDelegatedScopes_returnsFromSetDelegatedScopes() {
        try (TestAppInstance testApp = sTestApp.install(sUser)) {
            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Arrays.asList(TEST_SCOPE, TEST_SCOPE_2));
                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatedScopes(
                                sDeviceState.dpc().componentName(),
                                testApp.packageName()))
                        .containsExactly(TEST_SCOPE, TEST_SCOPE_2);
            } finally {
                resetDelegatedScopes(testApp);
            }
        }
    }

    @CanSetPolicyTest(policy = Delegation.class)
    public void getDelegatedScopes_fromApp_returnsFromSetDelegatedScopes() {
        try (TestAppInstance testApp = sTestApp.install(sUser)) {
            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Arrays.asList(TEST_SCOPE, TEST_SCOPE_2));

                assertThat(testApp.devicePolicyManager().getDelegatedScopes(
                                null, testApp.packageName()))
                        .containsExactly(TEST_SCOPE, TEST_SCOPE_2);
            } finally {
                resetDelegatedScopes(testApp);
            }
        }
    }

    @CanSetPolicyTest(policy = Delegation.class)
    public void getDelegatedScopes_fromApp_passComponentName_throwsException() {
        try (TestAppInstance testApp = sTestApp.install(sUser)) {
            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Arrays.asList(TEST_SCOPE, TEST_SCOPE_2));

                assertThrows(SecurityException.class, () ->
                        testApp.devicePolicyManager().getDelegatedScopes(
                        sDeviceState.dpc().componentName(), testApp.packageName()));
            } finally {
                resetDelegatedScopes(testApp);
            }
        }
    }

    @CanSetPolicyTest(policy = Delegation.class)
    public void getDelegatedScopes_fromApp_differentPackage_throwsException() {
        try (TestAppInstance testApp = sTestApp.install(sUser);
             TestAppInstance testApp2 = sTestApp2.install(sUser)) {
            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Arrays.asList(TEST_SCOPE, TEST_SCOPE_2));
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp2.packageName(),
                        Arrays.asList(TEST_SCOPE, TEST_SCOPE_2));

                assertThrows(SecurityException.class, () ->
                        testApp.devicePolicyManager().getDelegatedScopes(
                                null, testApp2.packageName()));
            } finally {
                resetDelegatedScopes(testApp);
                resetDelegatedScopes(testApp2);
            }
        }
    }

    @CannotSetPolicyTest(policy = Delegation.class, includeNonDeviceAdminStates = false)
    public void setDelegatedScopes_invalidAdmin_throwsSecurityException() {
        try (TestAppInstance testApp = sTestApp.install(sUser)) {
            assertThrows(SecurityException.class, () ->
                    sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                            sDeviceState.dpc().componentName(),
                            testApp.packageName(),
                            Arrays.asList(TEST_SCOPE, TEST_SCOPE_2)));
        }
    }

    @CannotSetPolicyTest(policy = Delegation.class)
    public void getDelegatedScopes_invalidAdmin_throwsSecurityException() {
        try (TestAppInstance testApp = sTestApp.install(sUser)) {
            assertThrows(SecurityException.class, () ->
                    sDeviceState.dpc().devicePolicyManager().getDelegatedScopes(
                            sDeviceState.dpc().componentName(), testApp.packageName()));
        }
    }

    @CanSetPolicyTest(policy = Delegation.class)
    public void getDelegatedScopes_returnsLatestFromSetDelegatedScopes() {
        try (TestAppInstance testApp = sTestApp.install(sUser)) {

            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Collections.singletonList(TEST_SCOPE));
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Collections.singletonList(TEST_SCOPE_2));

                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatedScopes(
                                sDeviceState.dpc().componentName(),
                                testApp.packageName()))
                        .containsExactly(TEST_SCOPE_2);

            } finally {
                resetDelegatedScopes(testApp);
            }
        }
    }

    @CanSetPolicyTest(policy = Delegation.class)
    public void setDelegatedScopes_uninstalledPackage_throwsExceptionWithoutChangingState() {
        // This test cannot be split into two without ErrorProne complaining that an Exception is
        // being caught without a fail() on the second test.
        String uninstalledPackage = "uninstalled_package_name";
        assertThrows(Exception.class, () ->
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        uninstalledPackage,
                        Collections.singletonList(TEST_SCOPE)));
        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getDelegatedScopes(sDeviceState.dpc().componentName(), uninstalledPackage))
                .isEmpty();
    }

    @CanSetPolicyTest(policy = Delegation.class)
    public void getDelegatePackages_oneApp_twoScopes_returnsFromSetDelegatedScopes() {
        try (TestAppInstance testApp = sTestApp.install(sUser)) {

            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Arrays.asList(TEST_SCOPE, TEST_SCOPE_2));

                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatePackages(sDeviceState.dpc().componentName(), TEST_SCOPE))
                        .containsExactly(testApp.packageName());
                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatePackages(sDeviceState.dpc().componentName(), TEST_SCOPE_2))
                        .containsExactly(testApp.packageName());

            } finally {
                resetDelegatedScopes(testApp);
            }
        }
    }

    @CannotSetPolicyTest(policy = Delegation.class, includeNonDeviceAdminStates = false)
    public void getDelegatePackages_invalidAdmin_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager().getDelegatePackages(
                        sDeviceState.dpc().componentName(), TEST_SCOPE));
    }

    @CanSetPolicyTest(policy = Delegation.class)
    public void getDelegatePackages_twoApps_differentScopes_returnsFromSetDelegatedScopes() {
        try (TestAppInstance testApp = sTestApp.install(sUser);
             TestAppInstance testApp2 = sTestApp2.install(sUser)) {

            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Collections.singletonList(TEST_SCOPE));
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp2.packageName(),
                        Collections.singletonList(TEST_SCOPE_2));

                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatePackages(sDeviceState.dpc().componentName(), TEST_SCOPE))
                        .containsExactly(testApp.packageName());
                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatePackages(sDeviceState.dpc().componentName(), TEST_SCOPE_2))
                        .containsExactly(testApp2.packageName());

            } finally {
                resetDelegatedScopes(testApp);
                resetDelegatedScopes(testApp2);
            }
        }
    }

    @PolicyAppliesTest(policy = Delegation.class)
    public void getDelegatePackages_twoApps_sameScope_returnsFromSetDelegatedScopes() {
        try (TestAppInstance testApp = sTestApp.install(sUser);
             TestAppInstance testApp2 = sTestApp2.install(sUser)) {

            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Collections.singletonList(TEST_SCOPE));
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp2.packageName(),
                        Collections.singletonList(TEST_SCOPE));

                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatePackages(sDeviceState.dpc().componentName(), TEST_SCOPE))
                        .containsExactly(
                                testApp.packageName(),
                                testApp2.packageName());

            } finally {
                resetDelegatedScopes(testApp);
                resetDelegatedScopes(testApp2);
            }
        }
    }

    @PolicyAppliesTest(policy = NetworkLoggingDelegation.class)
    public void setDelegatedScopes_networkLogging_validAdminType_noException() {
        try (TestAppInstance testApp = sTestApp.install(sUser)) {
            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Collections.singletonList(DELEGATION_NETWORK_LOGGING));
            } finally {
                resetDelegatedScopes(testApp);
            }
        }
    }

    @CannotSetPolicyTest(
            policy = NetworkLoggingDelegation.class, includeNonDeviceAdminStates = false)
    public void setDelegatedScopes_networkLogging_invalidAdminType_throwsSecurityException() {
        try (TestAppInstance testApp = sTestApp.install(sUser)) {
            try {
                assertThrows(SecurityException.class, () ->
                        sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                                sDeviceState.dpc().componentName(),
                                testApp.packageName(),
                                Collections.singletonList(DELEGATION_NETWORK_LOGGING)));
            } finally {
                try {
                    resetDelegatedScopes(testApp);
                } catch (Exception e) {
                    // Can happen when API is not allowed to be called by a financed device owner.
                    Log.w(TAG, "Failed to reset delegated scopes", e);
                }
            }
        }
    }

    @PolicyAppliesTest(policy = SecurityLoggingDelegation.class)
    // TODO(b/198774281): add a negative policy test (in line with all the others here) once we can
    //  correctly mark security logging delegation as possible for COPE profile POs.
    public void setDelegatedScopes_securityLogging_validAdminType_noException() {
        try (TestAppInstance testApp = sTestApp.install(sUser)) {
            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Collections.singletonList(DELEGATION_SECURITY_LOGGING));
            } finally {
                resetDelegatedScopes(testApp);
            }
        }
    }

    @PolicyAppliesTest(policy = Delegation.class)
    public void setDelegatedScopes_certSelection_settingSecondApp_revokesFirstApp() {
        try (TestAppInstance testApp = sTestApp.install(sUser);
             TestAppInstance testApp2 = sTestApp2.install(sUser)) {

            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Collections.singletonList(DELEGATION_CERT_SELECTION));
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp2.packageName(),
                        Collections.singletonList(DELEGATION_CERT_SELECTION));

                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatedScopes(
                                sDeviceState.dpc().componentName(),
                                testApp.packageName()))
                        .isEmpty();
                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatedScopes(
                                sDeviceState.dpc().componentName(),
                                testApp2.packageName()))
                        .containsExactly(DELEGATION_CERT_SELECTION);

            } finally {
                resetDelegatedScopes(testApp);
                resetDelegatedScopes(testApp2);
            }
        }
    }

    @PolicyAppliesTest(policy = NetworkLoggingDelegation.class)
    public void setDelegatedScopes_networkLogging_settingSecondApp_revokesFirstApp() {
        try (TestAppInstance testApp = sTestApp.install(sUser);
             TestAppInstance testApp2 = sTestApp2.install(sUser)) {

            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Collections.singletonList(DELEGATION_NETWORK_LOGGING));
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp2.packageName(),
                        Collections.singletonList(DELEGATION_NETWORK_LOGGING));

                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatedScopes(
                                sDeviceState.dpc().componentName(),
                                testApp.packageName()))
                        .isEmpty();
                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatedScopes(
                                sDeviceState.dpc().componentName(),
                                testApp2.packageName()))
                        .containsExactly(DELEGATION_NETWORK_LOGGING);

            } finally {
                resetDelegatedScopes(testApp);
                resetDelegatedScopes(testApp2);
            }
        }
    }

    @PolicyAppliesTest(policy = SecurityLoggingDelegation.class)
    public void setDelegatedScopes_securityLogging_settingSecondApp_revokesFirstApp() {
        try (TestAppInstance testApp = sTestApp.install(sUser);
             TestAppInstance testApp2 = sTestApp2.install(sUser)) {

            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Collections.singletonList(DELEGATION_SECURITY_LOGGING));
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp2.packageName(),
                        Collections.singletonList(DELEGATION_SECURITY_LOGGING));

                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatedScopes(
                                sDeviceState.dpc().componentName(),
                                testApp.packageName()))
                        .isEmpty();
                assertThat(sDeviceState.dpc().devicePolicyManager()
                        .getDelegatedScopes(
                                sDeviceState.dpc().componentName(),
                                testApp2.packageName()))
                        .containsExactly(DELEGATION_SECURITY_LOGGING);

            } finally {
                resetDelegatedScopes(testApp);
                resetDelegatedScopes(testApp2);
            }
        }
    }

    @PolicyAppliesTest(policy = Delegation.class)
    public void setDelegatedScopes_delegatedPackageReceivesScopesFromBroadcast() {
        try (TestAppInstance testApp = sTestApp.install(sUser)) {
            testApp.registerReceiver(
                    new IntentFilter(ACTION_APPLICATION_DELEGATION_SCOPES_CHANGED),
                    RECEIVER_EXPORTED);

            try {
                sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                        sDeviceState.dpc().componentName(),
                        testApp.packageName(),
                        Arrays.asList(TEST_SCOPE, TEST_SCOPE_2));

                EventLogsSubject.assertThat(testApp.events().broadcastReceived()
                        .whereIntent().action()
                        .isEqualTo(ACTION_APPLICATION_DELEGATION_SCOPES_CHANGED)
                        .whereIntent().extras().key(EXTRA_DELEGATION_SCOPES)
                        .stringListValue().contains(TEST_SCOPE, TEST_SCOPE_2))
                        .eventOccurred();

            } finally {
                resetDelegatedScopes(testApp);
            }
        }
    }

    private void resetDelegatedScopes(TestAppInstance testApp) {
        sDeviceState.dpc().devicePolicyManager().setDelegatedScopes(
                sDeviceState.dpc().componentName(),
                testApp.packageName(),
                /* scopes= */ Collections.emptyList());
    }
}
