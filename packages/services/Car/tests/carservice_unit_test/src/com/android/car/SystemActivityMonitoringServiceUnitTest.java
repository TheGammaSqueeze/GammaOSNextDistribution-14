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

package com.android.car;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.builtin.app.ActivityManagerHelper.ProcessObserverCallback;
import android.car.builtin.os.ProcessHelper;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.res.Resources;
import android.os.HandlerThread;
import android.os.UserHandle;

import com.android.car.internal.ICarServiceHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;

public class SystemActivityMonitoringServiceUnitTest extends AbstractExtendedMockitoTestCase {

    private static final long PASSENGER_PROCESS_GROUP_SET_RETRY_TIMEOUT_MS = 1;

    @Mock
    private Context mContext;
    @Mock
    private Resources mResources;
    @Mock
    private ICarServiceHelper mICarServiceHelper;
    @Mock
    private CarOccupantZoneService mCarOccupantZoneService;
    @Mock
    private SystemActivityMonitoringService.Injector mInjector;

    private int mNonCurrentUserId;

    private boolean mThrowExceptionOnGetSetProcessGroup;
    private ArrayList<Integer> mProcessGroupsForGet = new ArrayList<>();

    private SystemActivityMonitoringService mService;

    @Before
    public void setUp() throws Exception {
        CarServiceHelperWrapper wrapper = CarServiceHelperWrapper.create();
        wrapper.setCarServiceHelper(mICarServiceHelper);
        CarLocalServices.addService(CarOccupantZoneService.class, mCarOccupantZoneService);
        when(mICarServiceHelper.getProcessGroup(anyInt())).thenAnswer(pid -> {
            if (mThrowExceptionOnGetSetProcessGroup) {
                throw new IllegalArgumentException();
            }
            return mProcessGroupsForGet.remove(0);
        });

        when(mInjector.getPassengerActivitySetProcessGroupRetryTimeoutMs())
                .thenReturn(PASSENGER_PROCESS_GROUP_SET_RETRY_TIMEOUT_MS);
        // Use some user id which is not user 0 or other user.
        mNonCurrentUserId = ActivityManager.getCurrentUser() + 100;

        mService = new SystemActivityMonitoringService(mContext, mInjector);
    }

    @After
    public void tearDown() {
        CarLocalServices.removeAllServices();
    }

    @Test
    public void testSetProcessGroupInFirstTry() throws Exception {
        setUpAssignPassengerActivityToFgGroup(/*enableResource=*/ true, /*hasDriverZone=*/ true,
                /*hasPassengerZones=*/ true);

        mService.init();

        ProcessObserverCallback cb = verifyAndGetProcessObserverCallback();

        mProcessGroupsForGet.add(ProcessHelper.THREAD_GROUP_TOP_APP);
        int processPid = 1;
        // appId does not matter here but better to avoid 0.
        int processUid = UserHandle.getUid(mNonCurrentUserId, /*appId=*/ 1);
        cb.onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ true);

        verify(mICarServiceHelper).getProcessGroup(processPid);
        verify(mICarServiceHelper).setProcessGroup(processPid, ProcessHelper.THREAD_GROUP_DEFAULT);
    }

    @Test
    public void testSetProcessGroupInSecondTry() throws Exception {
        setUpAssignPassengerActivityToFgGroup(/*enableResource=*/ true, /*hasDriverZone=*/ true,
                /*hasPassengerZones=*/ true);

        mService.init();

        ProcessObserverCallback cb = verifyAndGetProcessObserverCallback();

        // 1st entry is not top app yet, so it will retry.
        mProcessGroupsForGet.add(ProcessHelper.THREAD_GROUP_DEFAULT);
        mProcessGroupsForGet.add(ProcessHelper.THREAD_GROUP_TOP_APP);

        int processPid = 1;
        // appId does not matter here but better to avoid 0.
        int processUid = UserHandle.getUid(mNonCurrentUserId, /*appId=*/ 1);
        cb.onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ true);

        // Double the waiting time so that we have enough delay
        waitForHandlerThreadToComplete(2 * PASSENGER_PROCESS_GROUP_SET_RETRY_TIMEOUT_MS);

        verify(mICarServiceHelper, times(2)).getProcessGroup(processPid);
        verify(mICarServiceHelper).setProcessGroup(processPid,
                ProcessHelper.THREAD_GROUP_DEFAULT);
    }

    @Test
    public void testGetProcessGroupFailure() throws Exception {
        setUpAssignPassengerActivityToFgGroup(/*enableResource=*/ true, /*hasDriverZone=*/ true,
                /*hasPassengerZones=*/ true);

        mService.init();

        ProcessObserverCallback cb = verifyAndGetProcessObserverCallback();

        mThrowExceptionOnGetSetProcessGroup = true;

        int processPid = 1;
        // appId does not matter here but better to avoid 0.
        int processUid = UserHandle.getUid(mNonCurrentUserId, /*appId=*/ 1);
        cb.onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ true);

        verify(mICarServiceHelper).getProcessGroup(processPid);
        verify(mICarServiceHelper, never()).setProcessGroup(anyInt(), anyInt());
    }

    @Test
    public void testIgnoreCurrentUser() throws Exception {
        setUpAssignPassengerActivityToFgGroup(/*enableResource=*/ true, /*hasDriverZone=*/ true,
                /*hasPassengerZones=*/ true);

        mService.init();

        ProcessObserverCallback cb = verifyAndGetProcessObserverCallback();

        int processPid = 1;
        // appId does not matter here but better to avoid 0.
        int processUid = UserHandle.getUid(ActivityManager.getCurrentUser(), /*appId=*/ 1);
        cb.onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ true);

        verify(mICarServiceHelper, never()).getProcessGroup(processPid);
        verify(mICarServiceHelper, never()).setProcessGroup(anyInt(), anyInt());
    }

    @Test
    public void testIgnoreSystemUser() throws Exception {
        setUpAssignPassengerActivityToFgGroup(/*enableResource=*/ true, /*hasDriverZone=*/ true,
                /*hasPassengerZones=*/ true);

        mService.init();

        ProcessObserverCallback cb = verifyAndGetProcessObserverCallback();

        int processPid = 1;
        // appId does not matter here but better to avoid 0.
        int processUid = UserHandle.getUid(UserHandle.USER_SYSTEM, /*appId=*/ 1);
        cb.onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ true);

        verify(mICarServiceHelper, never()).getProcessGroup(processPid);
        verify(mICarServiceHelper, never()).setProcessGroup(anyInt(), anyInt());
    }

    @Test
    public void testIgnoreWhenDisabledFromResource() throws Exception {
        doTestDisabledConfig(/*enableResource=*/ false, /*hasDriverZone=*/ true,
                /*hasPassengerZones=*/ true);
    }

    @Test
    public void testIgnoreWithoutDriverZone() throws Exception {
        doTestDisabledConfig(/*enableResource=*/ true, /*hasDriverZone=*/ false,
                /*hasPassengerZones=*/ true);
    }

    @Test
    public void testIgnoreWithoutPassengerZone() throws Exception {
        doTestDisabledConfig(/*enableResource=*/ true, /*hasDriverZone=*/ true,
                /*hasPassengerZones=*/ false);
    }

    @Test
    public void testRegisterProcessRunningStateCallback() {
        setUpAssignPassengerActivityToFgGroup(/*enableResource=*/ true, /*hasDriverZone=*/ true,
                /*hasPassengerZone=*/ true);

        mService.init();
        ProcessObserverCallback customCallback = mock(ProcessObserverCallback.class);
        mService.registerProcessObserverCallback(customCallback);

        ProcessObserverCallback cb = verifyAndGetProcessObserverCallback();

        int processPid = 1;
        // appId does not matter here but better to avoid 0.
        int processUid = UserHandle.getUid(ActivityManager.getCurrentUser(), /*appId=*/ 1);

        cb.onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ true);
        // Double the waiting time so that we have enough delay
        waitForHandlerThreadToComplete(2 * PASSENGER_PROCESS_GROUP_SET_RETRY_TIMEOUT_MS);

        verify(customCallback)
                .onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ true);

        cb.onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ false);
        // Double the waiting time so that we have enough delay
        waitForHandlerThreadToComplete(2 * PASSENGER_PROCESS_GROUP_SET_RETRY_TIMEOUT_MS);

        verify(customCallback)
                .onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ false);

        cb.onProcessDied(processPid, processUid);
        // Double the waiting time so that we have enough delay
        waitForHandlerThreadToComplete(2 * PASSENGER_PROCESS_GROUP_SET_RETRY_TIMEOUT_MS);

        verify(customCallback).onProcessDied(processPid, processUid);
    }

    @Test
    public void testUnregisterProcessRunningStateCallback() {
        setUpAssignPassengerActivityToFgGroup(/*enableResource=*/ true, /*hasDriverZone=*/ true,
                /*hasPassengerZone=*/ true);

        mService.init();
        ProcessObserverCallback customCallback = mock(ProcessObserverCallback.class);
        mService.registerProcessObserverCallback(customCallback);
        mService.unregisterProcessObserverCallback(customCallback);

        ProcessObserverCallback cb = verifyAndGetProcessObserverCallback();

        int processPid = 1;
        // appId does not matter here but better to avoid 0.
        int processUid = UserHandle.getUid(ActivityManager.getCurrentUser(), /*appId=*/ 1);

        cb.onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ true);
        // Double the waiting time so that we have enough delay
        waitForHandlerThreadToComplete(2 * PASSENGER_PROCESS_GROUP_SET_RETRY_TIMEOUT_MS);

        verify(customCallback, never())
                .onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ true);
    }

    private void setUpAssignPassengerActivityToFgGroup(boolean enableResource,
            boolean hasDriverZone, boolean hasPassengerZone) {
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(anyInt())).thenReturn(enableResource);
        when(mCarOccupantZoneService.hasDriverZone()).thenReturn(hasDriverZone);
        when(mCarOccupantZoneService.hasPassengerZones()).thenReturn(hasPassengerZone);
    }

    private ProcessObserverCallback verifyAndGetProcessObserverCallback() {
        ArgumentCaptor<ProcessObserverCallback> cbCaptor = ArgumentCaptor.forClass(
                ProcessObserverCallback.class);
        verify(mInjector).registerProcessObserverCallback(cbCaptor.capture());
        return cbCaptor.getValue();
    }

    private void doTestDisabledConfig(boolean enableResource, boolean hasDriverZone,
            boolean hasPassengerZone) throws Exception {
        setUpAssignPassengerActivityToFgGroup(enableResource, hasDriverZone, hasPassengerZone);

        mService.init();

        ProcessObserverCallback cb = verifyAndGetProcessObserverCallback();

        mProcessGroupsForGet.add(ProcessHelper.THREAD_GROUP_TOP_APP);
        int processPid = 1;
        // appId does not matter here but better to avoid 0.
        int processUid = UserHandle.getUid(mNonCurrentUserId, /*appId=*/ 1);
        cb.onForegroundActivitiesChanged(processPid, processUid, /*foreground=*/ true);

        verify(mICarServiceHelper, never()).getProcessGroup(processPid);
        verify(mICarServiceHelper, never()).setProcessGroup(processPid,
                ProcessHelper.THREAD_GROUP_DEFAULT);
    }

    private void waitForHandlerThreadToComplete(long delay) {
        String threadName = mService.getClass().getSimpleName();
        HandlerThread thread = CarServiceUtils.getHandlerThread(threadName);
        CarServiceUtils.runOnLooperSyncDelayed(thread.getLooper(), () -> {}, delay);
    }
}
