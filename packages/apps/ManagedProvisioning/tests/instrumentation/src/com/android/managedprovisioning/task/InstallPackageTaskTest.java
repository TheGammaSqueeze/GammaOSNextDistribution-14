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

package com.android.managedprovisioning.task;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;

import static com.android.managedprovisioning.task.InstallPackageTask.ERROR_INSTALLATION_FAILED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.compatibility.common.util.BlockingCallback;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;


@SmallTest
public class InstallPackageTaskTest {

    private final static int RESULT_SUCCESS = -1;
    private final static int USER_ID = 0;
    private final static UserHandle USER_HANDLE = new UserHandle(USER_ID);
    private final static Instrumentation sInstrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
    private final static Context sContext = InstrumentationRegistry.getTargetContext();
    private final static String TEST_PACKAGE_NAME = "test.package.name";
    private static final String INVALID_FILE_CONTENTS = "invalid contents";
    private static final PackageLocationProvider FILE_NULL_PATH_PROVIDER = () -> null;
    private static final PackageLocationProvider FILE_INVALID_PATH_PROVIDER =
            () -> new File("invalid/path");
    private static final String TEST_APP = "com.android.bedstead.testapp.EmptyTestApp";

    private InstallPackageBlockingCallback mInstallPackageBlockingCallback;

    @Before
    public void setUp() {
        mInstallPackageBlockingCallback = new InstallPackageBlockingCallback();
    }

    @EnsureHasPermission(WRITE_EXTERNAL_STORAGE)
    @Test
    public void run_success() throws IOException, InterruptedException {
        File appToInstallFile = getAppToInstallFile();
        TestApp testApp = writeApkToInstall(appToInstallFile);
        InstallPackageTask task = new InstallPackageTask(
                () -> appToInstallFile,
                sContext,
                createProvisioningParams(testApp.packageName()),
                mInstallPackageBlockingCallback,
                testApp.packageName());
        int resultCode;

        try {
            sInstrumentation.runOnMainSync(() -> task.run(USER_ID));
            resultCode = mInstallPackageBlockingCallback.await();
        } finally {
            // TODO(b/191277673): Use TestApp#uninstall(UserHandle) when available to use.
        }

        assertThat(resultCode).isEqualTo(RESULT_SUCCESS);
        assertFileDeleted(appToInstallFile);
    }

    @Test
    public void run_fileIsNull_success() throws InterruptedException {
        InstallPackageTask task = new InstallPackageTask(
                FILE_NULL_PATH_PROVIDER,
                sContext,
                createProvisioningParams(TEST_PACKAGE_NAME),
                mInstallPackageBlockingCallback,
                TEST_PACKAGE_NAME);

        task.run(USER_ID);
        int resultCode = mInstallPackageBlockingCallback.await();

        assertThat(resultCode).isEqualTo(RESULT_SUCCESS);
    }

    @Test
    public void run_fileDoesNotExist_fail() throws InterruptedException {
        InstallPackageTask task = new InstallPackageTask(
                FILE_INVALID_PATH_PROVIDER,
                sContext,
                createProvisioningParams(TEST_PACKAGE_NAME),
                mInstallPackageBlockingCallback,
                TEST_PACKAGE_NAME);

        sInstrumentation.runOnMainSync(() -> task.run(USER_ID));
        int resultCode = mInstallPackageBlockingCallback.await();

        assertThat(resultCode).isEqualTo(ERROR_INSTALLATION_FAILED);
    }

    @Ignore("b/191285670")
    @EnsureHasPermission(WRITE_EXTERNAL_STORAGE)
    @Test
    public void run_packageAlreadyInstalled_success() throws IOException, InterruptedException {
        File appToInstallFile = getAppToInstallFile();
        TestApp testApp = writeApkToInstall(appToInstallFile);
        testApp.install(USER_HANDLE);
        InstallPackageTask task = new InstallPackageTask(
                () -> appToInstallFile,
                sContext,
                createProvisioningParams(testApp.packageName()),
                mInstallPackageBlockingCallback,
                testApp.packageName());
        int resultCode;

        try {
            sInstrumentation.runOnMainSync(() -> task.run(USER_ID));
            resultCode = mInstallPackageBlockingCallback.await();
        } finally {
            // TODO(b/191277673): Use TestApp#uninstall(UserHandle) when available to use.
        }

        assertThat(resultCode).isEqualTo(RESULT_SUCCESS);
        assertFileDeleted(appToInstallFile);
    }

    @EnsureHasPermission(WRITE_EXTERNAL_STORAGE)
    @Test
    public void run_invalidApkFile_error() throws IOException, InterruptedException {
        File appToInstallFile = getAppToInstallFile();
        writeInvalidFile(appToInstallFile);
        InstallPackageTask task = new InstallPackageTask(
                () -> appToInstallFile,
                sContext,
                createProvisioningParams(TEST_PACKAGE_NAME),
                mInstallPackageBlockingCallback,
                TEST_PACKAGE_NAME);
        int resultCode;

        sInstrumentation.runOnMainSync(() -> task.run(USER_ID));
        resultCode = mInstallPackageBlockingCallback.await();

        assertThat(resultCode).isEqualTo(ERROR_INSTALLATION_FAILED);
        assertFileDeleted(appToInstallFile);
    }

    private void assertFileDeleted(File appToInstallFile) {
        if (appToInstallFile.exists()) {
            appToInstallFile.delete();
            fail("File " + appToInstallFile.getAbsolutePath() + " was not deleted.");
        }
    }

    private void writeInvalidFile(File appToInstallFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(appToInstallFile)) {
            writer.println(INVALID_FILE_CONTENTS);
        }
    }

    private static ProvisioningParams createProvisioningParams(String packageName) {
        return new ProvisioningParams.Builder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                .setDeviceAdminPackageName(packageName)
                .build();
    }

    private static TestApp writeApkToInstall(File appToInstallFile) throws IOException {
        TestAppProvider testAppProvider = new TestAppProvider();
        TestApp testApp = testAppProvider
                .query()
                // TODO(b/192330233): We use this specific app as it does not have the testOnly flag
                .wherePackageName().isEqualTo(TEST_APP)
                .get();
        testApp.writeApkFile(appToInstallFile);
        return testApp;
    }

    private static File getAppToInstallFile() throws IOException {
        return File.createTempFile(
                /* prefix= */ "test_app" + Math.random() * Integer.MAX_VALUE,
                ".apk",
                sContext.getCacheDir());
    }

    private static class InstallPackageBlockingCallback extends BlockingCallback<Integer>
            implements AbstractProvisioningTask.Callback {
        @Override
        public void onSuccess(AbstractProvisioningTask task) {
            callbackTriggered(RESULT_SUCCESS);
        }

        @Override
        public void onError(
                AbstractProvisioningTask task, int errorCode, String errorMessage) {
            callbackTriggered(errorCode);
        }
    }
}
