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

import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class UserStatsTests<T> extends DeviceTestCase implements IBuildReceiver {
    protected IBuildInfo mCtsBuild;
    protected List<Integer> mUsersToRemove = new ArrayList();
    protected int mAtomId;

    public UserStatsTests(int atomId) {
        mAtomId = atomId;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(WAIT_TIME_LONG);
        getDevice().executeShellCommand("adb logcat -c");
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

    protected int userCreate(String userName, boolean isGuest) throws Exception {
        uploadConfigForPushedAtom();
        int userId = getDevice().createUser(userName, isGuest, false /* ephemeral */);
        mUsersToRemove.add(userId);
        RunUtil.getDefault().sleep(WAIT_TIME_LONG);
        return userId;
    }


    protected int removeGuestUser(String userName, boolean isGuest) throws Exception {
        int userId = getDevice().createUser(userName, isGuest, false /* ephemeral */);
        mUsersToRemove.add(userId);
        uploadConfigForPushedAtom();
        getDevice().removeUser(userId);
        mUsersToRemove.clear();
        RunUtil.getDefault().sleep(WAIT_TIME_LONG);
        return userId;
    }

    protected int switchUser(String userName, boolean isGuest) throws Exception {
        int userId = getDevice().createUser(userName, isGuest, false /* ephemeral */);
        mUsersToRemove.add(userId);
        uploadConfigForPushedAtom();
        getDevice().switchUser(userId);
        // Timeout 2 minutes
        waitUntilDispatchUserSwitchComplete(120000);
        RunUtil.getDefault().sleep(WAIT_TIME_LONG);
        return userId;
    }


    private void waitUntilDispatchUserSwitchComplete(long timeoutInMillis) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeElapsed = 0;
        String adbSwitchFinished = null;

        while ((adbSwitchFinished == null || adbSwitchFinished.isEmpty())
                && timeElapsed < timeoutInMillis) {
            RunUtil.getDefault().sleep(WAIT_TIME_LONG);
            adbSwitchFinished = getDevice()
                    .executeShellCommand("adb logcat | grep  -E \"SystemServerTiming: "
                            + "dispatchUserSwitchComplete-|SystemServerTiming: "
                            + "stopFreezingScreen\"");
            timeElapsed = System.currentTimeMillis() - startTime;
        }
    }

    protected void uploadConfigForPushedAtom() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                mAtomId);
    }
}
