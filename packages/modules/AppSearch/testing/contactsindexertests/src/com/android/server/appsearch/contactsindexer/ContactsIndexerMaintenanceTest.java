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

package com.android.server.appsearch.contactsindexer;

import static android.Manifest.permission.RECEIVE_BOOT_COMPLETED;

import static com.android.server.appsearch.contactsindexer.ContactsIndexerMaintenanceService.MIN_INDEXER_JOB_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.app.UiAutomation;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.UserInfo;
import android.os.CancellationSignal;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;

import org.mockito.MockitoSession;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ContactsIndexerMaintenanceTest {
    private static final int DEFAULT_USER_ID = 0;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private Context mContextWrapper;
    private ContactsIndexerMaintenanceService mContactsIndexerMaintenanceService;
    private MockitoSession session;
    @Mock private JobScheduler mockJobScheduler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContextWrapper = new ContextWrapper(mContext) {
            @Override
            @Nullable
            public Object getSystemService(String name) {
                if (Context.JOB_SCHEDULER_SERVICE.equals(name)) {
                    return mockJobScheduler;
                }
                return getSystemService(name);
            }
        };
        mContactsIndexerMaintenanceService = spy(new ContactsIndexerMaintenanceService());
        doNothing().when(mContactsIndexerMaintenanceService).jobFinished(any(), anyBoolean());
        session = ExtendedMockito.mockitoSession().
                mockStatic(LocalManagerRegistry.class).
                startMocking();
    }

    @After
    public void tearDown() {
        session.finishMocking();
    }

    @Test
    public void testScheduleFullUpdateJob_oneOff_isNotPeriodic() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(RECEIVE_BOOT_COMPLETED);
            ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContext, DEFAULT_USER_ID,
                    /*periodic=*/ false, /*intervalMillis=*/ -1);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        JobInfo jobInfo = getPendingFullUpdateJob(DEFAULT_USER_ID);
        assertThat(jobInfo).isNotNull();
        assertThat(jobInfo.isRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.isRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.isPersisted()).isTrue();
        assertThat(jobInfo.isPeriodic()).isFalse();
    }

    @Test
    public void testScheduleFullUpdateJob_periodic_isPeriodic() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(RECEIVE_BOOT_COMPLETED);
            ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContext, /*userId=*/ 0,
                    /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(7));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        JobInfo jobInfo = getPendingFullUpdateJob(DEFAULT_USER_ID);
        assertThat(jobInfo).isNotNull();
        assertThat(jobInfo.isRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.isRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.isPersisted()).isTrue();
        assertThat(jobInfo.isPeriodic()).isTrue();
        assertThat(jobInfo.getIntervalMillis()).isEqualTo(TimeUnit.DAYS.toMillis(7));
        assertThat(jobInfo.getFlexMillis()).isEqualTo(TimeUnit.DAYS.toMillis(7)/2);
    }

    @Test
    public void testScheduleFullUpdateJob_oneOffThenPeriodic_isRescheduled() {
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ false, /*intervalMillis=*/ -1);
        ArgumentCaptor<JobInfo> firstJobInfoCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mockJobScheduler).schedule(firstJobInfoCaptor.capture());
        JobInfo firstJobInfo = firstJobInfoCaptor.getValue();

        when(mockJobScheduler.getPendingJob(eq(MIN_INDEXER_JOB_ID))).thenReturn(firstJobInfo);
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(7));
        ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mockJobScheduler, times(2)).schedule(argumentCaptor.capture());
        List<JobInfo> jobInfos = argumentCaptor.getAllValues();
        JobInfo jobInfo = jobInfos.get(1);
        assertThat(jobInfo.isRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.isRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.isPersisted()).isTrue();
        assertThat(jobInfo.isPeriodic()).isTrue();
        assertThat(jobInfo.getIntervalMillis()).isEqualTo(TimeUnit.DAYS.toMillis(7));
    }

    @Test
    public void testScheduleFullUpdateJob_differentParams_isRescheduled() {
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(7));
        ArgumentCaptor<JobInfo> firstJobInfoCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mockJobScheduler).schedule(firstJobInfoCaptor.capture());
        JobInfo firstJobInfo = firstJobInfoCaptor.getValue();

        when(mockJobScheduler.getPendingJob(eq(MIN_INDEXER_JOB_ID))).thenReturn(firstJobInfo);
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(30));
        ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        // Mockito.verify() counts the number of occurrences from the beginning of the test.
        // This verify() uses times(2) to also account for the call to JobScheduler.schedule() above
        // where the first JobInfo is captured.
        verify(mockJobScheduler, times(2)).schedule(argumentCaptor.capture());
        List<JobInfo> jobInfos = argumentCaptor.getAllValues();
        JobInfo jobInfo = jobInfos.get(1);
        assertThat(jobInfo.isRequireBatteryNotLow()).isTrue();
        assertThat(jobInfo.isRequireDeviceIdle()).isTrue();
        assertThat(jobInfo.isPersisted()).isTrue();
        assertThat(jobInfo.isPeriodic()).isTrue();
        assertThat(jobInfo.getIntervalMillis()).isEqualTo(TimeUnit.DAYS.toMillis(30));
    }

    @Test
    public void testScheduleFullUpdateJob_sameParams_isNotRescheduled() {
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(7));
        ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mockJobScheduler).schedule(argumentCaptor.capture());
        JobInfo firstJobInfo = argumentCaptor.getValue();

        when(mockJobScheduler.getPendingJob(eq(MIN_INDEXER_JOB_ID))).thenReturn(firstJobInfo);
        ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContextWrapper, DEFAULT_USER_ID,
                /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(7));
        // Mockito.verify() counts the number of occurrences from the beginning of the test.
        // This verify() uses the default count of 1 (equivalent to times(1)) to account for the
        // call to JobScheduler.schedule() above where the first JobInfo is captured.
        verify(mockJobScheduler).schedule(any(JobInfo.class));
    }

    @Test
    public void testDoFullUpdateForUser_withInitializedLocalService_isSuccessful() {
        ExtendedMockito.doReturn(Mockito.mock(ContactsIndexerManagerService.LocalService.class))
                .when(() -> LocalManagerRegistry.getManager(
                        ContactsIndexerManagerService.LocalService.class));
        boolean updateSucceeded = mContactsIndexerMaintenanceService
                .doFullUpdateForUser(mContextWrapper, null, 0,
                        new CancellationSignal());
        assertThat(updateSucceeded).isTrue();
    }

    @Test
    public void testDoFullUpdateForUser_withUninitializedLocalService_failsGracefully() {
        ExtendedMockito.doReturn(null)
                .when(() -> LocalManagerRegistry.getManager(
                        ContactsIndexerManagerService.LocalService.class));
        boolean updateSucceeded = mContactsIndexerMaintenanceService
                .doFullUpdateForUser(mContextWrapper, null, 0,
                        new CancellationSignal());
        assertThat(updateSucceeded).isFalse();
    }

    @Test
    public void testDoFullUpdateForUser_onEncounteringException_failsGracefully() {
        ContactsIndexerManagerService.LocalService mockService = Mockito.mock(
                ContactsIndexerManagerService.LocalService.class);
        doThrow(RuntimeException.class).when(mockService).doFullUpdateForUser(anyInt(), any());
        ExtendedMockito.doReturn(mockService)
                .when(() -> LocalManagerRegistry.getManager(
                        ContactsIndexerManagerService.LocalService.class));

        boolean updateSucceeded = mContactsIndexerMaintenanceService
                .doFullUpdateForUser(mContextWrapper, null, 0,
                        new CancellationSignal());

        assertThat(updateSucceeded).isFalse();
    }

    @Test
    public void testDoFullUpdateForUser_cancelsBackgroundJob_whenCiDisabled() {
        ExtendedMockito.doReturn(null)
                .when(() -> LocalManagerRegistry.getManager(
                        ContactsIndexerManagerService.LocalService.class));

        mContactsIndexerMaintenanceService
                .doFullUpdateForUser(mContextWrapper, null, 0,
                        new CancellationSignal());

        verify(mockJobScheduler).cancel(MIN_INDEXER_JOB_ID);
    }

    @Test
    public void testDoFullUpdateForUser_doesNotCancelBackgroundJob_whenCiEnabled() {
        ExtendedMockito.doReturn(Mockito.mock(ContactsIndexerManagerService.LocalService.class))
                .when(() -> LocalManagerRegistry.getManager(
                        ContactsIndexerManagerService.LocalService.class));

        mContactsIndexerMaintenanceService
                .doFullUpdateForUser(mContextWrapper, null, 0,
                        new CancellationSignal());

        verifyZeroInteractions(mockJobScheduler);
    }

    @Test
    public void testCancelPendingFullUpdateJob_succeeds() throws IOException {
        UserInfo userInfo = new UserInfo(DEFAULT_USER_ID, /*name=*/ "default", /*flags=*/ 0);
        SystemService.TargetUser user = new SystemService.TargetUser(userInfo);
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(RECEIVE_BOOT_COMPLETED);
            ContactsIndexerMaintenanceService.scheduleFullUpdateJob(mContext, DEFAULT_USER_ID,
                    /*periodic=*/ true, /*intervalMillis=*/ TimeUnit.DAYS.toMillis(7));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        JobInfo jobInfo = getPendingFullUpdateJob(DEFAULT_USER_ID);
        assertThat(jobInfo).isNotNull();

        ContactsIndexerMaintenanceService.cancelFullUpdateJobIfScheduled(mContext,
                user.getUserHandle());

        jobInfo = getPendingFullUpdateJob(DEFAULT_USER_ID);
        assertThat(jobInfo).isNull();
    }

    @Test
    public void test_onStartJob_handlesExceptionGracefully() {
        mContactsIndexerMaintenanceService.onStartJob(null);
    }

    @Test
    public void test_onStopJob_handlesExceptionGracefully() {
        mContactsIndexerMaintenanceService.onStopJob(null);
    }

    @Nullable
    private JobInfo getPendingFullUpdateJob(@UserIdInt int userId) {
        int jobId = MIN_INDEXER_JOB_ID + userId;
        return mContext.getSystemService(JobScheduler.class).getPendingJob(jobId);
    }
}
