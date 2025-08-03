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
 * Schedule a non-existent population training task from Odp Test app UI
 * Force the task execution through ADB commands and verify error handling and exit behavior
 */
public class ScheduleNonExistentPopulationForTraining {
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
        mTestHelper.inputNonExistentPopulationForScheduleTraining();
    }

    @Test
    public void testScheduleNonExistentPopulationForTraining() throws IOException {
        mTestHelper.clickScheduleTraining();
        mTestHelper.forceExecuteTrainingForNonExistentPopulation();
    }

    /** Return device to original state after test exeuction */
    @AfterClass
    public static void tearDown() throws IOException {
        TestHelper.pressHome();
        TestHelper.wrapUp();
    }
}
