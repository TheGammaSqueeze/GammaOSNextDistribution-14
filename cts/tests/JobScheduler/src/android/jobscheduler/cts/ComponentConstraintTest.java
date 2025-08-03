/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.jobscheduler.cts;

import android.app.job.JobInfo;
import android.content.pm.PackageManager;

/**
 * Schedules jobs with various component-enabled states.
 */
public class ComponentConstraintTest extends BaseJobSchedulerTest {
    private static final String TAG = "ComponentConstraintTest";
    /** Unique identifier for the job scheduled by this suite of tests. */
    private static final int COMPONENT_JOB_ID = ComponentConstraintTest.class.hashCode();

    private JobInfo.Builder mBuilder;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mBuilder = new JobInfo.Builder(COMPONENT_JOB_ID, kJobServiceComponent);
    }

    @Override
    public void tearDown() throws Exception {
        setJobServiceEnabled(true);
        super.tearDown();
    }

    public void testScheduleAfterComponentEnabled() throws Exception {
        setJobServiceEnabled(true);
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.build());

        runSatisfiedJob(COMPONENT_JOB_ID);
        assertTrue("Job with enabled service didn't fire.", kTestEnvironment.awaitExecution());
    }

    /*
        Test intentionally disabled but kept here to acknowledge the case wasn't accidentally
        forgotten. Historically, JobScheduler has thrown an exception when an app called schedule()
        with a disabled service. That behavior cannot be changed easily.

        public void testScheduleAfterComponentDisabled() throws Exception {
            setJobServiceEnabled(false);
            kTestEnvironment.setExpectedExecutions(0);
            mJobScheduler.schedule(mBuilder.build());

            runSatisfiedJob(COMPONENT_JOB_ID);
            assertTrue("Job with disabled service fired.", kTestEnvironment.awaitTimeout());
        }
    */

    public void testComponentDisabledAfterSchedule() throws Exception {
        setJobServiceEnabled(true);
        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(mBuilder.setMinimumLatency(1000).build());
        setJobServiceEnabled(false);

        runSatisfiedJob(COMPONENT_JOB_ID);
        assertTrue("Job with disabled service fired.", kTestEnvironment.awaitTimeout());
    }

    public void testComponentDisabledAndReenabledAfterSchedule() throws Exception {
        setJobServiceEnabled(true);
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.setMinimumLatency(1000).build());

        setJobServiceEnabled(false);
        runSatisfiedJob(COMPONENT_JOB_ID);
        assertTrue("Job with disabled service fired.", kTestEnvironment.awaitTimeout());

        setJobServiceEnabled(true);
        runSatisfiedJob(COMPONENT_JOB_ID);
        assertTrue("Job with enabled service didn't fire.", kTestEnvironment.awaitExecution());
    }

    private void setJobServiceEnabled(boolean enabled) {
        final int state = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        getContext().getPackageManager().setComponentEnabledSetting(
                kJobServiceComponent, state, PackageManager.DONT_KILL_APP);
    }
}
