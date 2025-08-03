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
 * limitations under the License
 */

package android.host.accounts;

import static android.host.accounts.AccountManagerXUserTest.runDeviceTests;

import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.assertMetricsLogged;

import static org.junit.Assert.fail;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.SystemUserOnly;
import android.stats.devicepolicy.EventId;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Host-side tests for {@link android.accounts.AccountManager}
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@SystemUserOnly
@AppModeFull(reason = "instant applications cannot see any other application")
// TODO(b/187939873): Migrate to device side tests.
public class AccountManagerHostSideTest implements IDeviceTest, IBuildReceiver {

    private static final String TEST_WITH_PERMISSION_APK =
            "CtsAccountManagerCrossUserApp.apk";
    private static final String TEST_WITH_PERMISSION_PKG =
            "com.android.cts.accountmanager";
    private static final String ACCOUNT_TYPE =
            "com.android.cts.accountmanager";
    private static final String AUTH_TOKEN_TYPE = "testAuthTokenType";
    private static final String REQUIRED_FEATURES_STR = "testRequiredFeature1;testRequiredFeature2";

    private String mOldVerifierValue;
    private IBuildInfo mCtsBuild;
    private ITestDevice mDevice;
    private int mCurrentUser;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = Objects.requireNonNull(device);
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Before
    public void setUp() throws Exception {
        mCurrentUser = getDevice().getCurrentUser();

        mOldVerifierValue =
                getDevice().executeShellCommand("settings get global package_verifier_enable");
        getDevice().executeShellCommand("settings put global package_verifier_enable 0");

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        File apkFile = buildHelper.getTestFile(TEST_WITH_PERMISSION_APK);
        getDevice().installPackageForUser(
                apkFile,
                /* reinstall= */ true,
                /* grantPermissions= */ true,
                mCurrentUser,
                /* extraArgs= */"-t");
        waitForBroadcastIdle();

    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_WITH_PERMISSION_PKG);
        getDevice().executeShellCommand("settings put global package_verifier_enable "
                + mOldVerifierValue);
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Test
    @Ignore("Flaky test - b/303573931")
    public void tesAddAccount_logsMetrics() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTests(
                    getDevice(),
                    TEST_WITH_PERMISSION_PKG,
                    ".AccountManagerCrossUserTest",
                    "testAccountManager_addMockAccountForCurrentUser",
                    mCurrentUser,
                    /* testArgs= */ null,
                    /* timeout= */ 60L,
                    TimeUnit.SECONDS);
        }, new DevicePolicyEventWrapper.Builder(EventId.ADD_ACCOUNT_VALUE)
                .setStrings(ACCOUNT_TYPE,
                        TEST_WITH_PERMISSION_PKG,
                        AUTH_TOKEN_TYPE,
                        REQUIRED_FEATURES_STR)
                .build());
    }

    @Test
    @Ignore("Flaky test - b/303573931")
    public void tesAddAccountExplicitly_logsMetrics() throws Exception {
        final String[] expectedVisibilityStr = new String[]{"0:", "1:", "2:", "3:", "4:"};
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTests(
                    getDevice(),
                    TEST_WITH_PERMISSION_PKG,
                    ".AccountManagerCrossUserTest",
                    "testAccountManager_addAccountExplicitlyForCurrentUser",
                    mCurrentUser,
                    /* testArgs= */ null,
                    /* timeout= */ 60L,
                    TimeUnit.SECONDS);
        }, new DevicePolicyEventWrapper.Builder(EventId.ADD_ACCOUNT_EXPLICITLY_VALUE)
                .setStrings(ACCOUNT_TYPE,
                        TEST_WITH_PERMISSION_PKG,
                        expectedVisibilityStr)
                .build());
    }

    @Test
    @Ignore("Flaky test - b/303573931")
    public void tesGetAuthToken_logsMetrics() throws Exception {
        assertMetricsLogged(getDevice(), () -> {
            runDeviceTests(
                    getDevice(),
                    TEST_WITH_PERMISSION_PKG,
                    ".AccountManagerCrossUserTest",
                    "testAccountManager_getAuthTokenForCurrentUser",
                    mCurrentUser,
                    /* testArgs= */ null,
                    /* timeout= */ 60L,
                    TimeUnit.SECONDS);
        }, new DevicePolicyEventWrapper.Builder(EventId.GET_ACCOUNT_AUTH_TOKEN_VALUE)
                .setStrings(ACCOUNT_TYPE,
                        TEST_WITH_PERMISSION_PKG)
                .build());
    }

    protected void waitForBroadcastIdle() throws DeviceNotAvailableException {
        final CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        // We allow 8min for the command to complete and 4min for the command to start to
        // output something.
        getDevice().executeShellCommand(
                /* command= */ "am wait-for-broadcast-idle",
                receiver,
                /* maxTimeoutForCommand= */ 8,
                /* maxTimeToOutputShellResponse= */ 4,
                TimeUnit.MINUTES,
                /* retryAttempts= */ 0);
        final String output = receiver.getOutput();
        if (!output.contains("All broadcast queues are idle!")) {
            LogUtil.CLog.e("Output from 'am wait-for-broadcast-idle': %s", output);
            fail("'am wait-for-broadcase-idle' did not complete.");
        }
    }
}
