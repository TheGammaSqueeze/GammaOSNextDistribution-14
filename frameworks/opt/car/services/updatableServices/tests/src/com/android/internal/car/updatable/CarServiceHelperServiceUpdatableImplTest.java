/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.car.internal.common.CommonConstants.CAR_SERVICE_INTERFACE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import android.car.ICar;
import android.car.builtin.os.UserManagerHelper;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.car.CarServiceHelperInterface;
import com.android.server.wm.CarActivityInterceptorInterface;
import com.android.server.wm.CarDisplayCompatScaleProviderInterface;
import com.android.server.wm.CarLaunchParamsModifierInterface;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * This class contains unit tests for the {@link CarServiceHelperServiceUpdatableImpl}.
 */
@RunWith(AndroidJUnit4.class)
public final class CarServiceHelperServiceUpdatableImplTest
        extends AbstractExtendedMockitoTestCase {

    private MockitoSession mSession;

    @Mock
    private Context mMockContext;
    @Mock
    private CarServiceProxy mCarServiceProxy;
    @Mock
    private CarServiceHelperInterface mCarServiceHelperInterface;
    @Mock
    private CarLaunchParamsModifierInterface mCarLaunchParamsModifierInterface;
    @Mock
    private CarActivityInterceptorInterface mCarActivityInterceptorInterface;
    @Mock
    private CarDisplayCompatScaleProviderInterface
            mCarDisplayCompatScaleProviderInterface;
    @Mock
    private ICar mICarBinder;
    @Mock
    private IBinder mIBinder;

    private CarServiceHelperServiceUpdatableImpl mCarServiceHelperServiceUpdatableImpl;

    public CarServiceHelperServiceUpdatableImplTest() {
        super(CarServiceHelperServiceUpdatableImpl.TAG);
    }

    @Before
    public void setTestFixtures() {
        Map<String, Object> interfaces = new ArrayMap<>();
        interfaces.put(CarServiceHelperInterface.class.getSimpleName(),
                mCarServiceHelperInterface);
        interfaces.put(CarLaunchParamsModifierInterface.class.getSimpleName(),
                mCarLaunchParamsModifierInterface);
        interfaces.put(CarActivityInterceptorInterface.class.getSimpleName(),
                mCarActivityInterceptorInterface);
        interfaces.put(CarDisplayCompatScaleProviderInterface.class.getSimpleName(),
                mCarDisplayCompatScaleProviderInterface);
        interfaces.put(CarServiceProxy.class.getSimpleName(), mCarServiceProxy);

        mCarServiceHelperServiceUpdatableImpl = new CarServiceHelperServiceUpdatableImpl(
                mMockContext,
                interfaces);
    }

    @Test
    public void testCarServiceLaunched() throws Exception {
        mockSystemContext();
        mockBindService();

        mCarServiceHelperServiceUpdatableImpl.onStart();

        verifyBindService();
    }

    @Test
    public void testHandleCarServiceConnection() throws Exception {
        mockICarBinder();

        mCarServiceHelperServiceUpdatableImpl.handleCarServiceConnection(mIBinder);

        verify(mICarBinder).setSystemServerConnections(any(), any());
    }

    @Test
    public void testHandleCarServiceCrash() throws Exception {
        mockICarBinder();
        doThrow(new RemoteException()).when(mICarBinder).setSystemServerConnections(any(), any());

        mCarServiceHelperServiceUpdatableImpl.handleCarServiceConnection(mIBinder);

        verify(mCarServiceHelperInterface).dumpServiceStacks();
    }

    @Test
    public void testOnUserRemoved() throws Exception {
        UserHandle user = UserHandle.of(101);
        mCarServiceHelperServiceUpdatableImpl.onUserRemoved(user);

        verify(mCarServiceProxy).onUserRemoved(user);
    }

    @Test
    public void testOnFactoryReset() throws Exception {
        BiConsumer<Integer, Bundle> callback = (x, y) -> {};
        mCarServiceHelperServiceUpdatableImpl.onFactoryReset(callback);

        verify(mCarServiceProxy).onFactoryReset(any());
    }

    @Test
    public void testInitBootUser() throws Exception {
        mCarServiceHelperServiceUpdatableImpl.initBootUser();

        verify(mCarServiceProxy).initBootUser();
    }

    @Test
    public void testSendUserLifecycleEvent_nullFromUser() throws Exception {
        int eventType = 1;
        UserHandle userFrom = null;
        UserHandle userTo = UserHandle.SYSTEM;

        mCarServiceHelperServiceUpdatableImpl.sendUserLifecycleEvent(eventType, userFrom, userTo);

        verify(mCarServiceProxy).sendUserLifecycleEvent(eventType, UserManagerHelper.USER_NULL,
                userTo.getIdentifier());
    }

    @Test
    public void testSendUserLifecycleEvent() throws Exception {
        int eventType = 1;
        UserHandle userFrom = UserHandle.SYSTEM;
        int userId = 101;
        UserHandle userTo = UserHandle.of(userId);

        mCarServiceHelperServiceUpdatableImpl.sendUserLifecycleEvent(eventType, userFrom, userTo);

        verify(mCarServiceProxy).sendUserLifecycleEvent(eventType, userFrom.getIdentifier(),
                userTo.getIdentifier());
    }

    @Test
    public void testGetProcessGroup() throws Exception {
        when(mCarServiceHelperInterface.getProcessGroup(42)).thenReturn(108);

        assertWithMessage("getProcessGroup(42)")
                .that(mCarServiceHelperServiceUpdatableImpl.mHelper.getProcessGroup(42))
                .isEqualTo(108);
    }

    @Test
    public void testSetProcessGroup() throws Exception {
        mCarServiceHelperServiceUpdatableImpl.mHelper.setProcessGroup(42, 108);

        verify(mCarServiceHelperInterface).setProcessGroup(42, 108);
    }

    @Test
    public void testStartUserInBackgroundVisibleOnDisplay() throws Exception {
        int userId = 100;
        int displayId = 2;

        mCarServiceHelperServiceUpdatableImpl.mHelper.startUserInBackgroundVisibleOnDisplay(userId,
                displayId);

        verify(mCarServiceHelperInterface).startUserInBackgroundVisibleOnDisplay(userId,
                displayId);
    }

    @Test
    public void testGetMainDisplayAssignedToUser() throws Exception {
        when(mCarServiceHelperInterface.getMainDisplayAssignedToUser(42)).thenReturn(108);
        assertWithMessage("getMainDisplayAssignedToUser(42)")
                .that(mCarServiceHelperServiceUpdatableImpl.mHelper
                        .getMainDisplayAssignedToUser(42))
                .isEqualTo(108);
    }

    @Test
    public void testGetUserAssignedToDisplay() throws Exception {
        when(mCarServiceHelperInterface.getUserAssignedToDisplay(42)).thenReturn(108);

        assertWithMessage("getUserAssignedToDisplay(42)")
                .that(mCarServiceHelperServiceUpdatableImpl.mHelper.getUserAssignedToDisplay(42))
                .isEqualTo(108);
    }

    private void mockICarBinder() {
        when(ICar.Stub.asInterface(mIBinder)).thenReturn(mICarBinder);
    }

    private void mockSystemContext() {
        when(mMockContext.createContextAsUser(UserHandle.SYSTEM, /* flags= */ 0))
                .thenReturn(mMockContext);
    }

    private void mockBindService() {
        when(mMockContext.bindService(any(), eq(Context.BIND_AUTO_CREATE), any(), any()))
                .thenReturn(true);
    }

    private void verifyBindService() throws Exception {
        verify(mMockContext).bindService(
                argThat(intent -> intent.getAction().equals(CAR_SERVICE_INTERFACE)),
                eq(Context.BIND_AUTO_CREATE), any(), any());
    }
}
