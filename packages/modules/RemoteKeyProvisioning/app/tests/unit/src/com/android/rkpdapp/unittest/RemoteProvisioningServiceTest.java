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

package com.android.rkpdapp.unittest;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.ActivityThread;
import android.app.Application;
import android.content.Context;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.rkpdapp.IGetRegistrationCallback;
import com.android.rkpdapp.IRemoteProvisioning;
import com.android.rkpdapp.interfaces.ServiceManagerInterface;
import com.android.rkpdapp.interfaces.SystemInterface;
import com.android.rkpdapp.service.RemoteProvisioningService;
import com.android.rkpdapp.testutil.SystemPropertySetter;
import com.android.rkpdapp.utils.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RemoteProvisioningServiceTest {
    private IRemoteProvisioning mBinder;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        RemoteProvisioningService service = new RemoteProvisioningService();
        service.attach(mContext, ActivityThread.currentActivityThread(),
                "RemoteProvisioningService", null, mock(Application.class), null);
        mBinder = IRemoteProvisioning.Stub.asInterface(service.onBind(null));
        Settings.clearPreferences(mContext);
    }

    @After
    public void tearDown() {
        Settings.resetDefaultConfig(mContext);
        ServiceManagerInterface.setInstances(null);
    }

    @Test
    public void getRegistrationSuccess() throws Exception {
        try (SystemPropertySetter ignored = SystemPropertySetter.setHostname("fake-base-url")) {
            SystemInterface mockHal = mock(SystemInterface.class);
            doReturn("test-irpc").when(mockHal).getServiceName();
            ServiceManagerInterface.setInstances(new SystemInterface[]{mockHal});

            IGetRegistrationCallback callback = mock(IGetRegistrationCallback.class);
            mBinder.getRegistration(42, "test-irpc", callback);
            verify(callback).onSuccess(notNull());
        }
    }

    @Test
    public void getRegistrationNoHostName() throws Exception {
        try (SystemPropertySetter ignored = SystemPropertySetter.setHostname("")) {
            IGetRegistrationCallback callback = mock(IGetRegistrationCallback.class);
            mBinder.getRegistration(42, "irpc", callback);
            verify(callback).onError(matches("RKP is disabled.*"));
        }
    }

    @Test
    public void getRegistrationHandlesCallbackFailure() throws Exception {
        try (SystemPropertySetter ignored = SystemPropertySetter.setHostname("something")) {
            SystemInterface mockHal = mock(SystemInterface.class);
            doReturn("test-irpc").when(mockHal).getServiceName();
            ServiceManagerInterface.setInstances(new SystemInterface[]{mockHal});

            IGetRegistrationCallback callback = mock(IGetRegistrationCallback.class);
            doThrow(new RemoteException("doom")).when(callback).onSuccess(any());

            assertThrows(RuntimeException.class,
                    () -> mBinder.getRegistration(42, "test-irpc", callback));
        }
    }

    @Test
    public void getRegistrationWithInvalidHalName() throws Exception {
        try (SystemPropertySetter ignored = SystemPropertySetter.setHostname("something")) {
            ServiceManagerInterface.setInstances(new SystemInterface[0]);
            IGetRegistrationCallback callback = mock(IGetRegistrationCallback.class);
            mBinder.getRegistration(0, "non-existent", callback);
            verify(callback).onError(matches("Invalid HAL name: non-existent"));
        }
    }
}
