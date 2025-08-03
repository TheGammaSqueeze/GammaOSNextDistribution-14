/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.federatedcompute.test.scenario.federatedcompute;

import android.platform.test.scenario.annotation.Scenario;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@Scenario
@RunWith(JUnit4.class)
/**
 * Trigger scheduling of a federatedCompute encryption key fetch job
 * Then force the job execution through ADB commands
 */
public class ScheduleAndForceEncryptionKeyFetchJob {
    private TestHelper mTestHelper = new TestHelper();

    /** Prepare the device before entering the test class */
    @BeforeClass
    public static void prepareDevice() throws IOException {
        TestHelper.initialize();
        TestHelper.killRunningProcess();
    }

    @Before
    public void setup() throws IOException {
        mTestHelper.pressHome();
        mTestHelper.openTestApp();
        mTestHelper.inputPopulationForScheduleTraining();
    }

    @Test
    public void testScheduleAndForceEncryptionKeyFetchJob() throws IOException {
        mTestHelper.clickScheduleTraining(); // encryption key fetch job scheduled during the call
        mTestHelper.forceExecuteEncryptionKeyFetchJob();
    }

    /** Return device to original state after test exeuction */
    @AfterClass
    public static void tearDown() throws IOException {
        TestHelper.pressHome();
        TestHelper.wrapUp();
    }
}
