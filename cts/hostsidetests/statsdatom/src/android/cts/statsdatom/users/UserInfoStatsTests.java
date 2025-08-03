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
import com.android.os.AtomsProto.UserInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.List;

public class UserInfoStatsTests extends DeviceTestCase implements IBuildReceiver {
    public static final int ATOM_ID = 10152;
    protected IBuildInfo mCtsBuild;
    protected List<Integer> mUsersToRemove = new ArrayList();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().switchUser(0);
        for (Integer userId : mUsersToRemove) {
            getDevice().removeUser(userId);
        }
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        super.tearDown();
        RunUtil.getDefault().sleep(WAIT_TIME_LONG);
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testGuestUserExists() throws Exception {
        if (getDevice().isMultiUserSupported()) {
            String userName = "TestUser_" + System.currentTimeMillis();
            int userId = userCreate(userName, true);

            uploadConfigForPulledAtom();
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
            List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());

            assertThat(data).isNotEmpty();

            UserInfo systemUser = findUserById(data, 0);
            assertThat(systemUser).isNotNull();
            assertThat(systemUser.getUserType().toString())
                    .isAnyOf("FULL_SYSTEM", "SYSTEM_HEADLESS");
            assertThat(systemUser.getIsUserRunningUnlocked()).isTrue();

            UserInfo guestUser = findUserById(data, userId);
            assertThat(guestUser).isNotNull();
            assertThat(guestUser.getUserType().toString()).isEqualTo("FULL_GUEST");
            assertThat(guestUser.getIsUserRunningUnlocked()).isFalse();
        }
    }


    public void testSecondaryUserExists() throws Exception {
        if (getDevice().isMultiUserSupported()) {
            String userName = "TestUser_" + System.currentTimeMillis();
            int userId = userCreate(userName, false);

            uploadConfigForPulledAtom();
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
            List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());

            assertThat(data).isNotEmpty();

            UserInfo systemUser = findUserById(data, 0);
            assertThat(systemUser).isNotNull();
            assertThat(systemUser.getUserType().toString())
                    .isAnyOf("FULL_SYSTEM", "SYSTEM_HEADLESS");
            assertThat(systemUser.getIsUserRunningUnlocked()).isTrue();

            UserInfo secondaryUser = findUserById(data, userId);
            assertThat(secondaryUser).isNotNull();
            assertThat(secondaryUser.getUserType().toString()).isEqualTo("FULL_SECONDARY");
            assertThat(secondaryUser.getIsUserRunningUnlocked()).isFalse();
        }
    }

    private UserInfo findUserById(List<AtomsProto.Atom> data, int userId) {
        for (AtomsProto.Atom atom: data) {
            if (atom.getUserInfo().getUserId() == userId) {
                return atom.getUserInfo();
            }
        }
        return null;
    }

    private int userCreate(String userName, boolean isGuest) throws Exception {
        int userId = getDevice().createUser(userName, isGuest, false /* ephemeral */);
        mUsersToRemove.add(userId);
        RunUtil.getDefault().sleep(WAIT_TIME_LONG);
        return userId;
    }

    private void uploadConfigForPulledAtom() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                ATOM_ID);
    }

}
