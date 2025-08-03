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

package com.android.bedstead.nene.packages;

import static com.android.queryable.queries.ActivityQuery.activity;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class ProcessReferenceTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities().contains(activity().where().exported().isTrue())
            .get();

    @Test
    @Ignore("Killing by granting + ungranting a permission is currently not working")
    public void kill_killsProcess() {
        try (TestAppInstance testApp = sTestApp.install()) {
            testApp.activities().query().whereActivity().exported().isTrue().get().start();

            int pidBefore = testApp.process().pid();

            testApp.process().kill();

            Poll.forValue("pid",
                    () -> testApp.process() == null ? -1 : testApp.process().pid())
                    .toNotBeEqualTo(pidBefore)
                    .errorOnFail()
                    .await();
        }
    }

    @Test
    public void crash_crashesProcess() {
        try (TestAppInstance testApp = sTestApp.install()) {
            testApp.activities().query().whereActivity().exported().isTrue().get().start();

            int pidBefore = testApp.process().pid();

            testApp.process().crash();

            Poll.forValue("pid",
                            () -> testApp.process() == null ? -1 : testApp.process().pid())
                    .toNotBeEqualTo(pidBefore)
                    .errorOnFail()
                    .await();
        }
    }
}
