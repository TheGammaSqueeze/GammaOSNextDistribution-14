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

package android.adservices.ondevicepersonalization;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigService;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationConfigServiceCallback;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executor;

@RunWith(Parameterized.class)
public class OnDevicePersonalizationConfigManagerTest {

    @Mock private IOnDevicePersonalizationConfigService mMockConfigService;

    private OnDevicePersonalizationConfigManager mConfigManager;

    @Parameterized.Parameter(0)
    public String scenario;

    @Before
    public void setUp() {
        mMockConfigService = Mockito.mock(IOnDevicePersonalizationConfigService.class);
        TestServiceBinder mTestBinder = new TestServiceBinder(mMockConfigService);
        mConfigManager = new OnDevicePersonalizationConfigManager(mTestBinder);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                    {"testSuccess"}, {"testNPE"}, {"testGenericException"},
                });
    }

    @Test
    public void testSetPersonalizationStatus() throws RemoteException {
        OutcomeReceiver<Void, Exception> spyCallback = spy(new MyTestCallback());

        switch (scenario) {
            case "testSuccess":
                doAnswer(
                                invocation -> {
                                    IOnDevicePersonalizationConfigServiceCallback serviceCallback =
                                            invocation.getArgument(1);
                                    serviceCallback.onSuccess();
                                    return null;
                                })
                        .when(mMockConfigService)
                        .setPersonalizationStatus(anyBoolean(), any());
                mConfigManager.setPersonalizationEnabled(true, Runnable::run, spyCallback);
                verify(spyCallback, times(1)).onResult(isNull());
                break;
            case "testNPE":
                doThrow(new NullPointerException())
                        .when(mMockConfigService)
                        .setPersonalizationStatus(anyBoolean(), any());
                assertThrows(
                        NullPointerException.class,
                        () ->
                                mConfigManager.setPersonalizationEnabled(
                                        true, Runnable::run, spyCallback));
                break;
            case "testGenericException":
                doThrow(new RuntimeException())
                        .when(mMockConfigService)
                        .setPersonalizationStatus(anyBoolean(), any());
                mConfigManager.setPersonalizationEnabled(true, Runnable::run, spyCallback);
                verify(spyCallback, times(1)).onError(any(RuntimeException.class));
                break;
        }
    }

    static class TestServiceBinder
            extends AbstractServiceBinder<IOnDevicePersonalizationConfigService> {
        private final IOnDevicePersonalizationConfigService mService;

        TestServiceBinder(IOnDevicePersonalizationConfigService service) {
            mService = service;
        }

        @Override
        public IOnDevicePersonalizationConfigService getService(Executor executor) {
            return mService;
        }

        @Override
        public void unbindFromService() {}
    }

    public static class MyTestCallback implements OutcomeReceiver<Void, Exception> {

        @Override
        public void onResult(Void result) {}

        @Override
        public void onError(Exception error) {}
    }
}
