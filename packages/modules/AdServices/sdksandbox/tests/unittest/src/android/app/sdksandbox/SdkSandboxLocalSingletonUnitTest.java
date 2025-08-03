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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.IBinder;
import android.os.RemoteException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class SdkSandboxLocalSingletonUnitTest {
    private IBinder mBinder;

    @Before
    public void setup() throws RemoteException {
        mBinder = Mockito.mock(IBinder.class);
        Mockito.when(mBinder.getInterfaceDescriptor()).thenReturn(ISdkToServiceCallback.DESCRIPTOR);
    }

    @After
    public void tearDown() {
        SdkSandboxLocalSingleton.destroySingleton();
    }

    @Test
    public void testInitInstanceWithIncorrectType() throws RemoteException {
        IBinder binder2 = Mockito.mock(IBinder.class);
        Mockito.when(binder2.getInterfaceDescriptor()).thenReturn("xyz");
        assertThrows(
                "IInterface not supported",
                UnsupportedOperationException.class,
                () -> SdkSandboxLocalSingleton.initInstance(binder2));
    }

    @Test
    public void testInitInstance() {
        SdkSandboxLocalSingleton.initInstance(mBinder);
        SdkSandboxLocalSingleton singletonInstance = SdkSandboxLocalSingleton.getExistingInstance();
        assertThat(singletonInstance).isNotNull();
        assertThat(singletonInstance.getSdkToServiceCallback().asBinder()).isEqualTo(mBinder);
    }

    @Test
    public void testInitInstanceWhenAlreadyExists() {
        SdkSandboxLocalSingleton.initInstance(mBinder);

        IBinder binder2 = Mockito.mock(IBinder.class);
        SdkSandboxLocalSingleton.initInstance(binder2);

        // Assert that the callback was not overridden and the new instance was not created.
        SdkSandboxLocalSingleton singletonInstance = SdkSandboxLocalSingleton.getExistingInstance();
        assertThat(singletonInstance).isNotNull();
        assertThat(singletonInstance.getSdkToServiceCallback().asBinder()).isEqualTo(mBinder);
    }

    @Test
    public void testGetInstanceReturnsSameObjectAlways() {
        SdkSandboxLocalSingleton.initInstance(mBinder);

        SdkSandboxLocalSingleton singletonInstance = SdkSandboxLocalSingleton.getExistingInstance();
        assertThat(singletonInstance).isNotNull();
        assertThat(SdkSandboxLocalSingleton.getExistingInstance())
                .isSameInstanceAs(singletonInstance);
    }

    @Test
    public void testGetInstanceWhenItDoesNotExist() {
        assertThrows(
                "SdkSandboxLocalSingleton not found",
                IllegalStateException.class,
                SdkSandboxLocalSingleton::getExistingInstance);
    }
}
