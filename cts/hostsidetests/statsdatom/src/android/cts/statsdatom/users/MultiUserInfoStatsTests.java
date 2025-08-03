/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.cts.statsdatom.users;

import static android.cts.statsdatom.lib.AtomTestUtils.WAIT_TIME_LONG;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.AtomsProto.MultiUserInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.List;

public class MultiUserInfoStatsTests extends DeviceTestCase implements IBuildReceiver {
    public static final int ATOM_ID = 10160;
    protected IBuildInfo mCtsBuild;
    static final String USER_SWITCHER_ENABLED = "user_switcher_enabled";
    static final String GLOBAL_NAMESPACE = "global";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testMultiUserInfo() throws Exception {
        int maxSupportedUsers = getDevice().getMaxNumberOfUsersSupported();
        boolean isUserSwitcherEnabled = isUserSwitcherEnabled();
        // Atoms are collected only if isUserSwitcher is enabled
        uploadConfigForPulledAtom();
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());
        if (isUserSwitcherEnabled) {
            assertThat(data).isNotEmpty();
            MultiUserInfo multiUserInfo = data.get(0).getMultiUserInfo();
            assertThat(multiUserInfo.getMaxSupportedUsers()).isEqualTo(maxSupportedUsers);
            assertThat(multiUserInfo.getMultiUserSettingOn()).isEqualTo(true);
            assertThat(multiUserInfo.getSupportsAddingFullUsers()).isEqualTo(true);
        } else {
            data.isEmpty();
        }
    }

    private boolean isUserSwitcherEnabled() throws Exception {
        boolean config_showUserSwitcherByDefault = DeviceUtils
                .checkDeviceFor(getDevice(), "checkConfigShowUserSwitcher");
        String userSwitcherEnabled = getDevice()
                .getSetting(GLOBAL_NAMESPACE, USER_SWITCHER_ENABLED);
        boolean multiUserSettingOn = stringIsNullOrEmpty(userSwitcherEnabled)
                ? config_showUserSwitcherByDefault
                : Integer.parseInt(userSwitcherEnabled) == 1;

        return getDevice().isMultiUserSupported() && multiUserSettingOn;
    }

    private boolean stringIsNullOrEmpty(String str) {
        return str == null || str.equals("null") || str.isEmpty();
    }

    private void uploadConfigForPulledAtom() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                ATOM_ID);
    }

}
