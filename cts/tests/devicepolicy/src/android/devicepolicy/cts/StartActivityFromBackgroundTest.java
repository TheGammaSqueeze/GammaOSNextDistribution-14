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

package android.devicepolicy.cts;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.content.Intent;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.StartActivityFromBackground;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestAppActivityReference;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class StartActivityFromBackgroundTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Ignore("b/285537184")
    @CannotSetPolicyTest(policy = StartActivityFromBackground.class)
    @Test
    public void startActivityFromBackground_dpcNotAllowed_unableToStart() {
        TestAppActivityReference testActivity = sDeviceState.dpc().activities().any();
        Intent intent = new Intent()
                .addFlags(FLAG_ACTIVITY_NEW_TASK)
                .setComponent(testActivity.component().componentName());

        sDeviceState.dpc().context().startActivity(intent);

        Poll.forValue("Start foreground activity from background",
                () -> TestApis.activities().foregroundActivity())
                .toNotBeEqualTo(testActivity.component()).errorOnFail().await();
    }

    @Ignore("b/285537184")
    @CanSetPolicyTest(policy = StartActivityFromBackground.class)
    @Test
    public void startActivityFromBackground_dpcAllowed_ableToStart() {
        TestAppActivityReference testActivity = sDeviceState.dpc().activities().any();
        Intent intent = new Intent()
                .addFlags(FLAG_ACTIVITY_NEW_TASK)
                .setComponent(testActivity.component().componentName());

        sDeviceState.dpc().context().startActivity(intent);

        Poll.forValue("Start foreground activity from background",
                () -> TestApis.activities().foregroundActivity())
                .toBeEqualTo(testActivity.component()).errorOnFail().await();
    }
}
