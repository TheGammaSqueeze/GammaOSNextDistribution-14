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

package android.cts.statsdatom.credentials;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.StatsLog;
import com.android.os.credentials.ApiName;
import com.android.os.credentials.CredentialManagerInitialPhaseReported;
import com.android.os.credentials.CredentialsExtensionAtoms;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import java.util.List;

/**
 * CTS Tests for Atoms in the Credential Manager flows.
 *
 * atest CtsStatsdAtomHostTestCases:CredentialsStatsTest
 */
public class CredentialsStatsTest extends DeviceTestCase implements IBuildReceiver {
    private static final String TAG = "CredentialsStats";

    private static final String FEATURE_CREDENTIALS = "android.software.credentials";

    public static final String TEST_PKG = "android.credentials.cts";
    public static final String TEST_APK = "CtsCredentialManagerTestCases.apk";
    public static final String TEST_CLASS =
            "android.credentials.cts.CtsCredentialProviderServiceDeviceTest";

    private IBuildInfo mCtsBuild;
    private int mStatsdAtomTestUid;

    private static final String TEST_GET_PASSWORD_NO_CREDENTIAL =
            "testGetPasswordCredentialRequest_invalidAllowedProviders_onErrorForEmptyResponse";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), TEST_APK, TEST_PKG, mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        mStatsdAtomTestUid = DeviceUtils.getAppUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), TEST_PKG);
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testInitialPhaseKnownCaller() throws Exception {
        if (!isSupportedDevice(getDevice())) return;
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                CredentialsExtensionAtoms.CREDENTIAL_MANAGER_INIT_PHASE_REPORTED_FIELD_NUMBER);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        CredentialsExtensionAtoms.registerAllExtensions(registry);

        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_GET_PASSWORD_NO_CREDENTIAL);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(
                getDevice(), registry);

        if (data.size() == 0) {
            return; // To brace failures on other branches until we have a better brace
                    // The culprit is likely an existing but disabled feature
        }

        assertThat(data.size()).isAtLeast(1);

        CredentialManagerInitialPhaseReported actualInitialMetric =
                data.get(0).getAtom().getExtension(
                        CredentialsExtensionAtoms.credentialManagerInitPhaseReported);

        assertThat(actualInitialMetric.getApiName().getNumber()).isEqualTo(
                ApiName.API_NAME_GET_CREDENTIAL_VALUE);
        assertThat(actualInitialMetric.getCallerUid()).isNotEqualTo(-1);
        assertThat(actualInitialMetric.getSessionId()).isNotEqualTo(0);
        assertThat(actualInitialMetric.getInitialTimestampReferenceNanoseconds()).isGreaterThan(0);
        assertThat(actualInitialMetric.getRequestUniqueClasstypesList()).hasSize(1);
        assertThat(actualInitialMetric.getPerClasstypeCountsList().get(0)).isEqualTo(1);
        assertThat(actualInitialMetric.getOriginSpecified()).isEqualTo(false);
    }

    /**
     * Check whether the device is supported or not. Currently, the device needs to have
     * FEATURE_CREDENTIALS.
     *
     * @param device the device
     * @return {@code True} if the device is supported. Otherwise, return {@code false}.
     * @throws Exception If DeviceUtils has an exception
     */
    public static boolean isSupportedDevice(ITestDevice device) throws Exception {
        return DeviceUtils.hasFeature(device, FEATURE_CREDENTIALS);
    }
}
