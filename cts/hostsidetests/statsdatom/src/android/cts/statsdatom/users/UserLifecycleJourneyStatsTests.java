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

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto.UserLifecycleJourneyReported;
import com.android.os.StatsLog.EventMetricData;

import java.util.ArrayList;
import java.util.List;

public class UserLifecycleJourneyStatsTests extends UserStatsTests<UserLifecycleJourneyReported> {
    static final int USER_LIFECYCLE_JOURNEY_REPORTED = 264;

    public UserLifecycleJourneyStatsTests() {
        super(USER_LIFECYCLE_JOURNEY_REPORTED);
    }

    protected UserLifecycleJourneyReported getAtom(EventMetricData data) {
        return data.getAtom().getUserLifecycleJourneyReported();
    }

    public void testCreateGuestUser()  throws Exception {
        if (getDevice().isMultiUserSupported()) {
            String userName = "TestUser_" + System.currentTimeMillis();
            int userId = userCreate(userName, true);
            List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
            assertExpectedEvents(data, getDevice().getCurrentUser(),
                    userId, "USER_CREATE_FULL_GUEST");
        }
    }

    public void testRemoveGuestUser() throws Exception {
        if (getDevice().isMultiUserSupported()) {
            String userName = "TestUser_" + System.currentTimeMillis();
            int userId = removeGuestUser(userName, true);
            List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
            assertExpectedEvents(data, getDevice().getCurrentUser(),
                    userId, "USER_REMOVE_FULL_GUEST",
                    "USER_LIFECYCLE_FULL_GUEST");
        }
    }

    // Failing due to issue b/246283671
//    public void testSwitchToGuestUser() throws Exception {
//        String userName = "TestUser_" + System.currentTimeMillis();
//        int userId = switchUser(userName, true);
//        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
//        assertExpectedEvents(data, 0, userId, "USER_SWITCH_UI_FULL_GUEST");
//    }

    public void testCreateFullUser() throws Exception {
        if (getDevice().isMultiUserSupported()) {
            String userName = "FullUser_" + System.currentTimeMillis();
            int userId = userCreate(userName, false);
            List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
            assertExpectedEvents(data, getDevice().getCurrentUser(),
                    userId, "USER_CREATE_FULL_SECONDARY");
        }
    }

    public void testRemoveFullUser() throws Exception {
        if (getDevice().isMultiUserSupported()) {
            String userName = "FullUser_" + System.currentTimeMillis();
            int userId = removeGuestUser(userName, false);
            List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
            assertExpectedEvents(data, getDevice().getCurrentUser(),
                    userId, "USER_REMOVE_FULL_SECONDARY",
                    "USER_LIFECYCLE_FULL_SECONDARY");
        }
    }

    // Failing due to issue b/246283671
//    public void testSwitchToFullUser() throws Exception {
//        String userName = "FullUser_" + System.currentTimeMillis();
//        int userId = switchUser(userName, false);
//        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
//        assertExpectedEvents(data, 0, userId, "USER_SWITCH_UI_FULL_SECONDARY");
//    }

    protected void assertExpectedEvents(List<EventMetricData> data, int originalId, int targetId,
            String ... eventNames) {
        List<String> expectedData = prepareUserExpectedEvents(eventNames);
        for (int i = 0; i < data.size(); i++) {
            UserLifecycleJourneyReported atom = getAtom(data.get(i));
            String expectedName = atom.getJourney() + "_" + atom.getUserType();
            long originUserId = atom.getOriginUser();
            long targetUserId = atom.getTargetUser();
            assertThat(expectedData).contains(expectedName);
            expectedData.remove(expectedName);
            assertThat(targetUserId).isEqualTo(targetId);
            assertThat(originUserId).isEqualTo(originalId);
        }
        assertThat(expectedData).isEmpty();
    }

    private List<String> prepareUserExpectedEvents(String ... events) {
        List<String> expectedData = new ArrayList<>();
        for (String event : events) {
            expectedData.add(event);
        }
        return expectedData;
    }

}
