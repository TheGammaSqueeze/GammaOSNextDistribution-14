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

package com.android.bedstead.testapp;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.os.UserHandle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(BedsteadJUnit4.class)
public final class TestAppTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final UserReference sUser = TestApis.users().instrumented();
    private static final UserHandle sUserHandle = sUser.userHandle();
    private static final UserReference sNonExistingUser = TestApis.users().find(9999);
    private static final UserHandle sNonExistingUserHandle = sNonExistingUser.userHandle();
    private static final Context sContext = TestApis.context().instrumentedContext();

    @Test
    public void reference_returnsNeneReference() {
        TestApp testApp = sDeviceState.testApps().any();

        assertThat(testApp.pkg()).isEqualTo(TestApis.packages().find(testApp.packageName()));
    }

    @Test
    public void install_noUserSpecified_installsInInstrumentedUser() {
        TestApp testApp = sDeviceState.testApps().any();

        testApp.install();

        try {
            assertThat(testApp.pkg().installedOnUser(sUser)).isTrue();
        } finally {
            testApp.uninstall(sUser);
        }
    }

    @Test
    public void install_userReference_installs() {
        TestApp testApp = sDeviceState.testApps().any();

        testApp.install(sUser);

        try {
            assertThat(testApp.pkg().installedOnUser(sUser)).isTrue();
        } finally {
            testApp.uninstall(sUser);
        }
    }

    @Test
    public void install_userReference_returnsReferenceToInstance() {
        TestApp testApp = sDeviceState.testApps().any();

        try {
            TestAppInstance testAppInstance = testApp.install(sUser);

            assertThat(testAppInstance.testApp()).isEqualTo(testApp);
            assertThat(testAppInstance.user()).isEqualTo(sUser);
        } finally {
            testApp.uninstall(sUser);
        }
    }

    @Test
    public void install_userHandle_installs() {
        TestApp testApp = sDeviceState.testApps().any();

        testApp.install(sUserHandle);

        try {
            assertThat(testApp.pkg().installedOnUser(sUser)).isTrue();
        } finally {
            testApp.uninstall(sUser);
        }
    }

    @Test
    public void install_userHandle_returnsReferenceToInstance() {
        TestApp testApp = sDeviceState.testApps().any();

        try {
            TestAppInstance testAppInstance = testApp.install(sUserHandle);

            assertThat(testAppInstance.testApp()).isEqualTo(testApp);
            assertThat(testAppInstance.user()).isEqualTo(sUser);
        } finally {
            testApp.uninstall(sUser);
        }
    }

    @Test
    public void install_nullUserReference_throwsException() {
        TestApp testApp = sDeviceState.testApps().any();

        assertThrows(NullPointerException.class, () -> testApp.install((UserReference) null));
    }

    @Test
    public void install_nullUserHandle_throwsException() {
        TestApp testApp = sDeviceState.testApps().any();

        assertThrows(NullPointerException.class, () -> testApp.install((UserHandle) null));
    }

    @Test
    public void instance_userHandle_instanceIsNotInstalled_stillReturnsInstance() {
        TestApp testApp = sDeviceState.testApps().any();

        TestAppInstance testAppInstance = testApp.instance(sUserHandle);

        assertThat(testAppInstance.testApp()).isEqualTo(testApp);
        assertThat(testAppInstance.user()).isEqualTo(sUser);
    }

    @Test
    public void instance_userReference_instanceIsNotInstalled_stillReturnsInstance() {
        TestApp testApp = sDeviceState.testApps().any();

        TestAppInstance testAppInstance = testApp.instance(sNonExistingUserHandle);

        assertThat(testAppInstance.testApp()).isEqualTo(testApp);
        assertThat(testAppInstance.user()).isEqualTo(sNonExistingUser);
    }

    @Test
    public void instance_userHandle_nonExistingUser_stillReturnsInstance() {
        TestApp testApp = sDeviceState.testApps().any();

        TestAppInstance testAppInstance = testApp.instance(sUserHandle);

        assertThat(testAppInstance.testApp()).isEqualTo(testApp);
        assertThat(testAppInstance.user()).isEqualTo(sUser);
    }

    @Test
    public void instance_nullUserHandle_throwsException() {
        TestApp testApp = sDeviceState.testApps().any();

        assertThrows(NullPointerException.class, () -> testApp.instance((UserHandle) null));
    }

    @Test
    public void instance_userReference_nonExistingUser_stillReturnsInstance() {
        TestApp testApp = sDeviceState.testApps().any();

        TestAppInstance testAppInstance = testApp.instance(sNonExistingUser);

        assertThat(testAppInstance.testApp()).isEqualTo(testApp);
        assertThat(testAppInstance.user()).isEqualTo(sNonExistingUser);
    }

    @Test
    public void instance_nullUserReference_throwsException() {
        TestApp testApp = sDeviceState.testApps().any();

        assertThrows(NullPointerException.class, () -> testApp.instance((UserReference) null));
    }

    @Test
    public void uninstall_nullUserReference_throwsException() {
        TestApp testApp = sDeviceState.testApps().any();

        assertThrows(NullPointerException.class, () -> testApp.uninstall((UserReference) null));
    }

    @Test
    public void uninstall_nullUserHandle_throwsException() {
        TestApp testApp = sDeviceState.testApps().any();

        assertThrows(NullPointerException.class, () -> testApp.uninstall((UserHandle) null));
    }

    @Test
    public void uninstall_userReference_nonExistingUser_doesNothing() {
        TestApp testApp = sDeviceState.testApps().any();

        testApp.uninstall(sNonExistingUser);
    }

    @Test
    public void uninstall_userHandle_nonExistingUser_doesNothing() {
        TestApp testApp = sDeviceState.testApps().any();

        testApp.uninstall(sNonExistingUserHandle);
    }

    @Test
    public void uninstall_userReference_notInstalled_doesNothing() {
        TestApp testApp = sDeviceState.testApps().any();
        testApp.uninstall(sUser);

        testApp.uninstall(sUser);
    }

    @Test
    public void uninstall_userHandle_notInstalled_doesNothing() {
        TestApp testApp = sDeviceState.testApps().any();
        testApp.uninstall(sUser);

        testApp.uninstall(sUserHandle);
    }

    @Test
    public void uninstall_noUserSpecified_uninstallsFromInstrumentedUser() {
        TestApp testApp = sDeviceState.testApps().any();
        testApp.install(sUser);

        testApp.uninstall();

        assertThat(testApp.pkg().installedOnUser(sUser)).isFalse();
    }

    @Test
    public void uninstall_userHandle_uninstalls() {
        TestApp testApp = sDeviceState.testApps().any();
        testApp.install(sUser);

        testApp.uninstall(sUserHandle);

        assertThat(testApp.pkg().installedOnUser(sUser)).isFalse();
    }

    @Test
    public void uninstall_userReference_uninstalls() {
        TestApp testApp = sDeviceState.testApps().any();
        testApp.install(sUser);

        testApp.uninstall(sUser);

        assertThat(testApp.pkg().installedOnUser(sUser)).isFalse();
    }

    @Test
    public void writeApkFile_writesFile() throws Exception {
        TestApp testApp = sDeviceState.testApps().any();
        File filesDir = sContext.getExternalFilesDir(/* type= */ null);
        File outputFile = new File(filesDir, "test.apk");
        outputFile.delete();

        testApp.writeApkFile(outputFile);

        try {
            assertThat(outputFile.exists()).isTrue();
        } finally {
            outputFile.delete();
        }
    }

    @Test
    @EnsureHasDeviceOwner
    public void install_repeated_hasRemoteDpcDeviceOwner_doesNotFailVerification() {
        TestApp testApp = sDeviceState.testApps().any();
        try (TestAppInstance t = testApp.install()) {
            // Intentionally empty
        }
        try (TestAppInstance t = testApp.install()) {
            // Intentionally empty
        }
    }

    @Test
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    public void install_repeated_hasRemoteDpcWorkProfile_doesNotFailVerification() {
        TestApp testApp = sDeviceState.testApps().any();

        // The first install can be into the parent or the work profile and it will succeed
        try (TestAppInstance t = testApp.install()) {
            // Intentionally empty
        }

        // The second will fail 100% of the time if DISALLOW_INSTALL_UNKNOWN_SOURCES is enabled
        try (TestAppInstance t = testApp.install(sDeviceState.workProfile())) {
            // Intentionally empty
        }
    }

    @Test
    @EnsureHasWorkProfile
    public void install_repeated_hasRemoteDpcWorkProfile_installsInParent_doesNotFailVerification() {
        TestApp testApp = sDeviceState.testApps().any();
        try (TestAppInstance t = testApp.install()) {
            // Intentionally empty
        }
        try (TestAppInstance t = testApp.install()) {
            // Intentionally empty
        }
    }
}
