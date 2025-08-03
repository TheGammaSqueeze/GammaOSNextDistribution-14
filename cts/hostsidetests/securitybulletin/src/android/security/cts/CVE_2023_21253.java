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

package android.security.cts;

import static com.android.tradefed.util.CommandStatus.SUCCESS;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import static java.lang.String.format;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.CommandResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_21253 extends NonRootSecurityTestCase {
    private final List<String> mAssumptionFailureList = new ArrayList<String>();
    private final List<String> mFailureList = new ArrayList<String>();

    @AsbSecurityTest(cveBugId = 266580022)
    @Test
    public void testPocCVE_2023_21253() {
        try {
            // Prebuilt apks used in this test are same as used in ApkVerifierTest.java. Dependence
            // on prebuilt apks cannot be removed because with fix, apks with more than 10 signers
            // cannot be built due to changes done in 'apksigner'.
            final String v1Only10SignersApk = "v1-only-10-signers.apk";
            final String v1Only11SignersApk = "v1-only-11-signers.apk";
            final String v2Only10SignersApk = "v2-only-10-signers.apk";
            final String v2Only11SignersApk = "v2-only-11-signers.apk";

            // Check V1 apk signature scheme
            checkApkSignerScheme(v1Only10SignersApk, v1Only11SignersApk, "V1 apk signature");

            // Check V2 apk signature scheme
            checkApkSignerScheme(v2Only10SignersApk, v2Only11SignersApk, "V2 apk signature");

            // Fail the test if any failure strings are present in mFailureList
            assertTrue(
                    format(
                            "Vulnerable to b/266580022! Failures are :- %s",
                            mFailureList.toString()),
                    mFailureList.isEmpty());

            // Assumption failure if any exception messages are present in mAssumptionFailureList
            assumeTrue(
                    format("Exceptions occurred :- %s", mAssumptionFailureList.toString()),
                    mAssumptionFailureList.isEmpty());
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    private void checkApkSignerScheme(
            String apkWith10Signers, String apkWith11Signers, String apkSignerScheme) {
        try {
            final CommandResult outputV1Only10 = installAndCheck(apkWith10Signers);
            final CommandResult outputV1Only11 = installAndCheck(apkWith11Signers);
            if (outputV1Only10.getStatus() != SUCCESS) {
                // outputV1Only10 should be successful as it is expected that installation of an apk
                // with 10 signers should be allowed
                mAssumptionFailureList.add(
                        format("Unable to install app %s with 10 signatures", apkWith10Signers));
                return;
            }
            if (outputV1Only11.getStatus() == SUCCESS) {
                // Add scheme to mFailureList if installation of an apk with 11 signers is
                // successful
                mFailureList.add(
                        format(
                                "%s scheme allows installation of apk %s with more than 10 signers",
                                apkSignerScheme, apkWith11Signers));
            }
        } catch (Exception e) {
            // Add exception occurred in mAssumptionFailureList. This is done to avoid test
            // termination midway and ensure that both the schemes are checked
            mAssumptionFailureList.add(
                    format(
                            "Exception %s occurred while checking %s apk signer scheme",
                            e.getMessage(), apkSignerScheme));
        }
    }

    private CommandResult installAndCheck(String apkName) throws Exception {
        final ITestDevice device = getDevice();
        final String apkPath = AdbUtils.TMP_PATH + apkName;
        try {
            // Push apk file to /data/local/tmp
            AdbUtils.pushResource(AdbUtils.RESOURCE_ROOT + apkName, apkPath, device);

            // Install apk
            return device.executeShellV2Command("pm install " + apkPath);
        } finally {
            // Uninstall apk
            device.executeShellV2Command("pm uninstall android.appsecurity.cts.tinyapp");

            // Remove apk file from /data/local/tmp
            AdbUtils.removeResources(new String[] {apkName}, AdbUtils.TMP_PATH, device);
        }
    }
}
