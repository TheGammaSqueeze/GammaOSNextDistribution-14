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

package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

import com.android.bedstead.deviceadminapp.DeviceAdminApp;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingCallback;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(BedsteadJUnit4.class)
public final class ClearApplicationDataTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // We specify this instead of using RemoteDPC as
    // DevicePolicyManager#clearApplicationUserData uses an executor
    private static final String DEVICE_ADMIN_PKG = "com.android.cts.deviceandprofileowner";
    private static final Executor sExecutor = Executors.newSingleThreadExecutor();
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final ComponentName sComponentName =
            DeviceAdminApp.deviceAdminComponentName(sContext);
    private static final TestApp sTestApp = sDeviceState.testApps().any();
    private static final String DEVICE_PROVISIONING_PACKAGE = TestApis.resources().getString(
            TestApis.resources().getIdentifier("config_deviceProvisioningPackage",
                    "string", "android"));
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#clearApplicationUserData")
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @Test
    public void clearApplicationUserData_calledByProfileOwner_testPackage_isCleared()
            throws Exception {
        try (TestAppInstance testAppInstance = sTestApp.install();
             ProfileOwner p = TestApis.devicePolicy().setProfileOwner(
                     TestApis.users().instrumented(), sComponentName)) {
            ClearApplicationDataCallback callback = new ClearApplicationDataCallback();

            sLocalDevicePolicyManager.clearApplicationUserData(
                    sComponentName, testAppInstance.packageName(), sExecutor, callback);

            ClearApplicationUserDataResponse response = callback.await();
            assertThat(response.packageName).isEqualTo(testAppInstance.packageName());
            assertThat(response.succeeded).isTrue();
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#clearApplicationUserData")
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @RequireRunOnSystemUser
    @Test
    public void clearApplicationUserData_calledByDeviceOwner_testPackage_isCleared()
            throws Exception {
        try (TestAppInstance testAppInstance = sTestApp.install();
             DeviceOwner d = TestApis.devicePolicy().setDeviceOwner(sComponentName)) {
            ClearApplicationDataCallback callback = new ClearApplicationDataCallback();

            sLocalDevicePolicyManager.clearApplicationUserData(
                    sComponentName, testAppInstance.packageName(), sExecutor, callback);

            ClearApplicationUserDataResponse response = callback.await();
            assertThat(response.packageName).isEqualTo(testAppInstance.packageName());
            assertThat(response.succeeded).isTrue();
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#clearApplicationUserData")
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @Test
    public void clearApplicationUserData_calledByProfileOwner_activeAdmin_isNotCleared()
            throws Exception {
        try (ProfileOwner p = TestApis.devicePolicy().setProfileOwner(
                TestApis.users().instrumented(), sComponentName)) {
            ClearApplicationDataCallback callback = new ClearApplicationDataCallback();

            sLocalDevicePolicyManager.clearApplicationUserData(
                    sComponentName, DEVICE_ADMIN_PKG, sExecutor, callback);

            ClearApplicationUserDataResponse response = callback.await();
            assertThat(response.packageName).isEqualTo(DEVICE_ADMIN_PKG);
            assertThat(response.succeeded).isFalse();
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#clearApplicationUserData")
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @RequireRunOnSystemUser
    @Test
    public void clearApplicationUserData_calledByDeviceOwner_activeAdmin_isNotCleared()
            throws Exception {
        try (DeviceOwner d = TestApis.devicePolicy().setDeviceOwner(sComponentName)) {
            ClearApplicationDataCallback callback = new ClearApplicationDataCallback();

            sLocalDevicePolicyManager.clearApplicationUserData(
                    sComponentName, DEVICE_ADMIN_PKG, sExecutor, callback);

            ClearApplicationUserDataResponse response = callback.await();
            assertThat(response.packageName).isEqualTo(DEVICE_ADMIN_PKG);
            assertThat(response.succeeded).isFalse();
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#clearApplicationUserData")
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @Test
    public void clearApplicationUserData_calledByProfileOwner_deviceProvisioning_isNotCleared()
            throws Exception {
        try (ProfileOwner p =
                     TestApis.devicePolicy().setProfileOwner(TestApis.users().instrumented(),
                             sComponentName)) {
            ClearApplicationDataCallback callback = new ClearApplicationDataCallback();

            sLocalDevicePolicyManager.clearApplicationUserData(
                    sComponentName, DEVICE_PROVISIONING_PACKAGE, sExecutor, callback);

            ClearApplicationUserDataResponse response = callback.await();
            assertThat(response.packageName).isEqualTo(DEVICE_PROVISIONING_PACKAGE);
            assertThat(response.succeeded).isFalse();
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#clearApplicationUserData")
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @RequireRunOnSystemUser
    @Test
    public void clearApplicationUserData_calledByDeviceOwner_deviceProvisioning_isNotCleared()
            throws Exception {
        try (DeviceOwner d = TestApis.devicePolicy().setDeviceOwner(sComponentName)) {
            ClearApplicationDataCallback callback = new ClearApplicationDataCallback();

            sLocalDevicePolicyManager.clearApplicationUserData(
                    sComponentName, DEVICE_PROVISIONING_PACKAGE, sExecutor, callback);

            ClearApplicationUserDataResponse response = callback.await();
            assertThat(response.packageName).isEqualTo(DEVICE_PROVISIONING_PACKAGE);
            assertThat(response.succeeded).isFalse();
        }
    }

    private final class ClearApplicationDataCallback
            extends BlockingCallback<ClearApplicationUserDataResponse>
            implements DevicePolicyManager.OnClearApplicationUserDataListener {
        @Override
        public void onApplicationUserDataCleared(String packageName, boolean succeeded) {
            ClearApplicationUserDataResponse response = new ClearApplicationUserDataResponse(
                    packageName, succeeded);
            callbackTriggered(response);
        }
    }

    private final class ClearApplicationUserDataResponse {
        private final String packageName;
        private final boolean succeeded;

        private ClearApplicationUserDataResponse(String packageName, boolean succeeded) {
            this.packageName = packageName;
            this.succeeded = succeeded;
        }
    }

}
