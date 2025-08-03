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
package com.android.internal.car.updatable;

import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_REMOVED;
import static com.android.car.internal.common.CommonConstants.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.car.ICarResultReceiver;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.util.UserTestingHelper.UserInfoBuilder;
import android.content.pm.UserInfo;
import android.os.RemoteException;
import android.os.UserHandle;

import com.android.car.internal.ICarSystemServerClient;
import com.android.server.SystemService.TargetUser;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class CarServiceProxyTest extends AbstractExtendedMockitoTestCase {

    @Mock
    private CarServiceHelperServiceUpdatableImpl mCarServiceHelperServiceUpdatableImpl;
    @Mock
    private ICarSystemServerClient mCarService;

    @Mock
    private ICarResultReceiver mFactoryResetCallback1;

    @Mock
    private ICarResultReceiver mFactoryResetCallback2;

    private final TargetUser mFromUser = new TargetUser(new UserInfo(101, "fromUser", 0));
    private final TargetUser mToUser = new TargetUser(new UserInfo(102, "toUser", 0));

    private final UserInfo mRemovedUser1 = new UserInfoBuilder(100).build();
    private final UserInfo mRemovedUser2 = new UserInfoBuilder(200).build();
    private final UserInfo mRemovedUser3 = new UserInfoBuilder(300).build();

    private CarServiceProxy mCarServiceProxy;

    public CarServiceProxyTest() {
        super(CarServiceProxy.TAG);
    }

    @Before
    public void setUpMocks() {
        mCarServiceProxy = new CarServiceProxy(mCarServiceHelperServiceUpdatableImpl);
    }

    @Test
    public void testInitBootUser_CarServiceNotNull() throws RemoteException {
        connectToCarService();

        callInitBootUser();

        verifyInitBootUserCalled();
    }

    @Test
    public void testInitBootUser_CarServiceNull() throws RemoteException {
        callInitBootUser();

        verifyInitBootUserNeverCalled();
    }

    @Test
    public void testSendUserLifecycleEvent_CarServiceNotNull() throws RemoteException {
        connectToCarService();

        callSendLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING);

        verifySendLifecycleEventCalled(USER_LIFECYCLE_EVENT_TYPE_SWITCHING);
    }

    @Test
    public void testSendUserLifecycleEvent_CarServiceNull() throws RemoteException {
        callSendLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING);

        verifySendLifecycleEventNeverCalled();
    }

    @Test
    public void testHandleCarServiceConnection() throws RemoteException {
        callInitBootUser();
        callSendLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_SWITCHING);
        callOnUserRemoved();

        // Call again to make sure only one call is made after the service is connected
        callInitBootUser();

        verifyInitBootUserNeverCalled();
        verifySendLifecycleEventNeverCalled();
        verifyOnUserRemovedNeverCalled();

        connectToCarService();

        verifyInitBootUserCalled();
        verifySendLifecycleEventCalled(USER_LIFECYCLE_EVENT_TYPE_SWITCHING);
        verifyLifecycleEventCalledForUserRemoval();
    }

    @Test
    public void testOnUserRemoved_CarServiceNotNull() throws RemoteException {
        connectToCarService();

        callOnUserRemoved();

        verifyLifecycleEventCalledForUserRemoval();
    }

    @Test
    public void testOnUserRemoved_CarServiceNull() throws RemoteException {
        callOnUserRemoved();

        verifySendLifecycleEventNeverCalled();
    }

    @Test
    public void testOnFactoryReset_CarServiceNotNull() throws RemoteException {
        connectToCarService();

        callOnFactoryReset(mFactoryResetCallback1);
        callOnFactoryReset(mFactoryResetCallback2);

        verifyOnFactoryResetCalled(mFactoryResetCallback1);
        verifyOnFactoryResetCalled(mFactoryResetCallback2);
    }

    @Test
    public void testOnFactoryReset_CarServiceNull() throws RemoteException {
        callOnFactoryReset(mFactoryResetCallback1);
        callOnFactoryReset(mFactoryResetCallback2);

        verifyOnFactoryResetNeverCalled();
    }

    @Test
    public void testOnFactoryReset_CarServiceNullThenConnected() throws RemoteException {
        callOnFactoryReset(mFactoryResetCallback1);
        callOnFactoryReset(mFactoryResetCallback2);
        connectToCarService();

        verifyOnFactoryResetNotCalled(mFactoryResetCallback1);
        verifyOnFactoryResetCalled(mFactoryResetCallback2);
    }

    private void connectToCarService() {
        mCarServiceProxy.handleCarServiceConnection(mCarService);
    }

    private void callInitBootUser() {
        mCarServiceProxy.initBootUser();
    }

    private void callSendLifecycleEvent(int eventType) {
        mCarServiceProxy.sendUserLifecycleEvent(eventType, mFromUser.getUserIdentifier(),
                mToUser.getUserIdentifier());
    }

    private void callOnUserRemoved() {
        mCarServiceProxy.onUserRemoved(mRemovedUser1.getUserHandle());
        mCarServiceProxy.onUserRemoved(mRemovedUser2.getUserHandle());
        mCarServiceProxy.onUserRemoved(mRemovedUser3.getUserHandle());
    }

    private void callOnFactoryReset(ICarResultReceiver callback) {
        mCarServiceProxy.onFactoryReset(callback);
    }

    private void verifyInitBootUserCalled() throws RemoteException {
        verify(mCarService).initBootUser();
    }

    private void verifyInitBootUserNeverCalled() throws RemoteException {
        verify(mCarService, never()).initBootUser();
    }

    private void verifySendLifecycleEventCalled(int eventType) throws RemoteException {
        verify(mCarService).onUserLifecycleEvent(eventType,
                mFromUser.getUserIdentifier(), mToUser.getUserIdentifier());
    }

    private void verifySendLifecycleEventNeverCalled() throws RemoteException {
        verify(mCarService, never()).onUserLifecycleEvent(anyInt(), anyInt(), anyInt());
    }

    private void verifyLifecycleEventCalledForUserRemoval() throws RemoteException {
        verify(mCarService).onUserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_REMOVED,
                UserHandle.USER_NULL, mRemovedUser1.getUserHandle().getIdentifier());
        verify(mCarService).onUserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_REMOVED,
                UserHandle.USER_NULL, mRemovedUser2.getUserHandle().getIdentifier());
        verify(mCarService).onUserLifecycleEvent(USER_LIFECYCLE_EVENT_TYPE_REMOVED,
                UserHandle.USER_NULL, mRemovedUser3.getUserHandle().getIdentifier());
    }

    private void verifyOnUserRemovedNeverCalled() throws RemoteException {
        verify(mCarService, never()).onUserRemoved(any());
    }

    private void verifyOnFactoryResetCalled(ICarResultReceiver callback) throws RemoteException {
        verify(mCarService).onFactoryReset(callback);
    }

    private void verifyOnFactoryResetNotCalled(ICarResultReceiver callback) throws RemoteException {
        verify(mCarService, never()).onFactoryReset(callback);
    }

    private void verifyOnFactoryResetNeverCalled() throws RemoteException {
        verify(mCarService, never()).onFactoryReset(any());
    }
}
