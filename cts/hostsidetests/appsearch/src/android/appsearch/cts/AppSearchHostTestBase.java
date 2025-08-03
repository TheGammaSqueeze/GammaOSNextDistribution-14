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

package android.appsearch.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import java.util.Map;

import javax.annotation.Nonnull;

public abstract class AppSearchHostTestBase extends BaseHostJUnit4Test {
    protected static final String TARGET_APK_A = "CtsAppSearchHostTestHelperA.apk";
    protected static final String TARGET_PKG_A = "android.appsearch.app.helper_a";
    protected static final String TEST_CLASS_A = TARGET_PKG_A + ".AppSearchDeviceTest";
    protected static final String TEST_STORAGE_AUGMENTER_CLASS_A =
            TARGET_PKG_A + ".AppSearchStorageAugmenterDeviceTest";
    protected static final String TEST_CONTACTS_INDEXER_CLASS_A =
            TARGET_PKG_A + ".ContactsIndexerDeviceTest";
    protected static final String TEST_ENTERPRISE_CONTACTS_CLASS_A =
            TARGET_PKG_A + ".EnterpriseContactsDeviceTest";
    protected static final String TARGET_APK_B = "CtsAppSearchHostTestHelperB.apk";
    protected static final String TARGET_PKG_B = "android.appsearch.app.helper_b";
    protected static final String TEST_CLASS_B = TARGET_PKG_B + ".AppSearchDeviceTest";

    protected static final String USER_ID_KEY = "userId";

    protected static final long DEFAULT_INSTRUMENTATION_TIMEOUT_MS = 600_000; // 10min

    protected void runDeviceTestAsUserInPkgA(@Nonnull String testMethod, int userId)
            throws Exception {
        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(getDevice(), TARGET_PKG_A, TEST_CLASS_A, testMethod, userId,
                        DEFAULT_INSTRUMENTATION_TIMEOUT_MS)).isTrue();
    }

    protected void runDeviceTestAsUserInPkgA(@Nonnull String testMethod, int userId,
            @Nonnull Map<String, String> args) throws Exception {
        DeviceTestRunOptions deviceTestRunOptions = new DeviceTestRunOptions(TARGET_PKG_A)
                .setTestClassName(TEST_CLASS_A)
                .setTestMethodName(testMethod)
                .setMaxInstrumentationTimeoutMs(DEFAULT_INSTRUMENTATION_TIMEOUT_MS)
                .setUserId(userId);
        for (Map.Entry<String, String> entry : args.entrySet()) {
            deviceTestRunOptions.addInstrumentationArg(entry.getKey(), entry.getValue());
        }
        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(deviceTestRunOptions)).isTrue();
    }

    protected void runStorageAugmenterDeviceTestAsUserInPkgA(@Nonnull String testMethod, int userId)
            throws Exception {
        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(getDevice(), TARGET_PKG_A, TEST_STORAGE_AUGMENTER_CLASS_A,
                        testMethod, userId, DEFAULT_INSTRUMENTATION_TIMEOUT_MS)).isTrue();
    }

    protected void runContactsIndexerDeviceTestAsUserInPkgA(@Nonnull String testMethod, int userId,
            @Nonnull Map<String, String> args) throws Exception {
        DeviceTestRunOptions deviceTestRunOptions = new DeviceTestRunOptions(TARGET_PKG_A)
                .setTestClassName(TEST_CONTACTS_INDEXER_CLASS_A)
                .setTestMethodName(testMethod)
                .setMaxInstrumentationTimeoutMs(DEFAULT_INSTRUMENTATION_TIMEOUT_MS)
                .setUserId(userId);
        for (Map.Entry<String, String> entry : args.entrySet()) {
            deviceTestRunOptions.addInstrumentationArg(entry.getKey(), entry.getValue());
        }
        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(deviceTestRunOptions)).isTrue();
    }

    protected void runEnterpriseContactsDeviceTestAsUserInPkgA(@Nonnull String testMethod,
            int userId, @Nonnull Map<String, String> args) throws Exception {
        DeviceTestRunOptions deviceTestRunOptions = new DeviceTestRunOptions(TARGET_PKG_A)
                .setTestClassName(TEST_ENTERPRISE_CONTACTS_CLASS_A)
                .setTestMethodName(testMethod)
                .setMaxInstrumentationTimeoutMs(DEFAULT_INSTRUMENTATION_TIMEOUT_MS)
                .setUserId(userId);
        for (Map.Entry<String, String> entry : args.entrySet()) {
            deviceTestRunOptions.addInstrumentationArg(entry.getKey(), entry.getValue());
        }
        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(deviceTestRunOptions)).isTrue();
    }

    protected void runDeviceTestAsUserInPkgB(@Nonnull String testMethod, int userId)
            throws Exception {
        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(getDevice(), TARGET_PKG_B, TEST_CLASS_B, testMethod, userId,
                        DEFAULT_INSTRUMENTATION_TIMEOUT_MS)).isTrue();
    }

    protected void rebootAndWaitUntilReady() throws Exception {
        rebootAndWaitUntilReady(getDevice());
    }

    protected static void rebootAndWaitUntilReady(ITestDevice device) throws Exception {
        if (device.getBooleanProperty("init.userspace_reboot.is_supported", false)) {
            // Soft reboot, reboots only userspace part of device. It waits for device to be
            // available.
            device.rebootUserspace();
        } else {
            device.reboot(); // reboot() waits for device available
        }
    }
}
