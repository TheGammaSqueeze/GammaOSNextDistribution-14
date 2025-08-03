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

package android.appsearch.app.helper_a;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ContactsIndexerDeviceTest {

    private static final int MIN_INDEXER_JOB_ID = 16942831;
    private static final String USER_ID_KEY = "userId";

    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    @Test
    public void testFullUpdateJobIsScheduled() throws Exception {
        mUiAutomation.adoptShellPermissionIdentity();
        try {
            Bundle args = InstrumentationRegistry.getArguments();
            int userId = Integer.parseInt(args.getString(USER_ID_KEY));
            int jobId = MIN_INDEXER_JOB_ID + userId;
            assertJobWaiting(jobId);
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testFullUpdateJobIsNotScheduled() throws Exception {
        mUiAutomation.adoptShellPermissionIdentity();
        try {
            Bundle args = InstrumentationRegistry.getArguments();
            int userId = Integer.parseInt(args.getString(USER_ID_KEY));
            int jobId = MIN_INDEXER_JOB_ID + userId;
            assertNoJob(jobId);
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }

    }

    private String getJobState(int jobId) throws Exception {
        return SystemUtil.runShellCommand(mUiAutomation,
                "cmd jobscheduler get-job-state android " + jobId).trim();

    }

    private void assertJobWaiting(int jobId) throws Exception {
        assertThat(getJobState(jobId)).contains("waiting");
    }

    private void assertNoJob(int jobId) throws Exception {
        assertThat(getJobState(jobId)).contains("unknown");
    }
}
