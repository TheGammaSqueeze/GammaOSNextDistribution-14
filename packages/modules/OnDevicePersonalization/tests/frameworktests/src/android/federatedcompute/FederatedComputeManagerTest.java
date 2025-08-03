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
package android.federatedcompute;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IFederatedComputeService;
import android.federatedcompute.common.ScheduleFederatedComputeRequest;
import android.federatedcompute.common.TrainingOptions;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(Parameterized.class)
public class FederatedComputeManagerTest {

    private final Context mContext =
            spy(new MyTestContext(ApplicationProvider.getApplicationContext()));

    private static final ComponentName OWNER_COMPONENT =
            ComponentName.createRelative("com.android.package.name", "com.android.class.name");

    @Parameterized.Parameter(0)
    public String scenario;

    @Parameterized.Parameter(1)
    public ScheduleFederatedComputeRequest request;

    @Parameterized.Parameter(2)
    public String populationName;

    @Parameterized.Parameter(3)
    public IFederatedComputeService iFederatedComputeService;

    @Mock private PackageManager mMockPackageManager;
    @Mock private IBinder mMockIBinder;
    @Mock private IFederatedComputeService mMockIService;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                        {"schedule-allNull", null, null, null},
                        {
                                "schedule-default-iService",
                                new ScheduleFederatedComputeRequest.Builder()
                                        .setTrainingOptions(new TrainingOptions.Builder().build())
                                        .build(),
                                null,
                                new IFederatedComputeService.Default()
                        },
                        {
                                "schedule-mockIService-RemoteException",
                                new ScheduleFederatedComputeRequest.Builder()
                                        .setTrainingOptions(new TrainingOptions.Builder().build())
                                        .build(),
                                null,
                                null /* mock will be returned */
                        },
                        {
                                "schedule-mockIService-onSuccess",
                                new ScheduleFederatedComputeRequest.Builder()
                                        .setTrainingOptions(new TrainingOptions.Builder().build())
                                        .build(),
                                null,
                                null /* mock will be returned */
                        },
                        {
                                "schedule-mockIService-onFailure",
                                new ScheduleFederatedComputeRequest.Builder()
                                        .setTrainingOptions(new TrainingOptions.Builder().build())
                                        .build(),
                                null,
                                null /* mock will be returned */
                        },
                        {"cancel-allNull", null, null, null},
                        {
                                "cancel-default-iService",
                                null,
                                "testPopulation",
                                new IFederatedComputeService.Default()
                        },
                        {
                                "cancel-mockIService-RemoteException",
                                null,
                                "testPopulation",
                                null /* mock will be returned */
                        },
                        {
                                "cancel-mockIService-onSuccess",
                                null,
                                "testPopulation",
                                null /* mock will be returned */
                        },
                        {
                                "cancel-mockIService-onFailure",
                                null,
                                "testPopulation",
                                null /* mock will be returned */
                        },
                });
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ResolveInfo resolveInfo = new ResolveInfo();
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.name = "TestName";
        serviceInfo.packageName = "com.android.federatedcompute.services";
        resolveInfo.serviceInfo = serviceInfo;
        when(mMockPackageManager.queryIntentServices(any(), anyInt()))
                .thenReturn(List.of(resolveInfo));
        when(mMockIBinder.queryLocalInterface(any())).thenReturn(iFederatedComputeService);
    }

    @Test
    public void testScheduleFederatedCompute() throws RemoteException {
        FederatedComputeManager manager = new FederatedComputeManager(mContext);
        OutcomeReceiver<Object, Exception> spyCallback;

        switch (scenario) {
            case "schedule-allNull":
                assertThrows(
                        NullPointerException.class, () -> manager.schedule(request, null, null));
                break;
            case "schedule-default-iService":
                manager.schedule(request, Executors.newSingleThreadExecutor(), null);
                break;
            case "schedule-mockIService-RemoteException":
                when(mMockIBinder.queryLocalInterface(any())).thenReturn(mMockIService);
                doThrow(new RemoteException()).when(mMockIService).schedule(any(), any(), any());
                spyCallback = spy(new MyTestCallback());

                manager.schedule(request, Runnable::run, spyCallback);

                verify(mContext, times(1)).bindService(any(), anyInt(), any(), any());
                verify(spyCallback, times(1)).onError(any(RemoteException.class));
                verify(mContext, times(1)).unbindService(any());
                break;
            case "schedule-mockIService-onSuccess":
                when(mMockIBinder.queryLocalInterface(any())).thenReturn(mMockIService);
                doAnswer(
                        invocation -> {
                            IFederatedComputeCallback federatedComputeCallback =
                                    invocation.getArgument(2);
                            federatedComputeCallback.onSuccess();
                            return null;
                        })
                        .when(mMockIService)
                        .schedule(any(), any(), any());
                spyCallback = spy(new MyTestCallback());

                manager.schedule(request, Runnable::run, spyCallback);

                verify(mContext, times(1)).bindService(any(), anyInt(), any(), any());
                verify(spyCallback, times(1)).onResult(isNull());
                verify(mContext, times(1)).unbindService(any());
                break;
            case "schedule-mockIService-onFailure":
                when(mMockIBinder.queryLocalInterface(any())).thenReturn(mMockIService);
                doAnswer(
                        invocation -> {
                            IFederatedComputeCallback federatedComputeCallback =
                                    invocation.getArgument(2);
                            federatedComputeCallback.onFailure(1);
                            return null;
                        })
                        .when(mMockIService)
                        .schedule(any(), any(), any());
                spyCallback = spy(new MyTestCallback());

                manager.schedule(request, Runnable::run, spyCallback);

                verify(mContext, times(1)).bindService(any(), anyInt(), any(), any());
                verify(spyCallback, times(1)).onError(any(FederatedComputeException.class));
                verify(mContext, times(1)).unbindService(any());
                break;
            case "cancel-allNull":
                assertThrows(
                        NullPointerException.class,
                        () ->
                                manager.cancel(
                                        OWNER_COMPONENT,
                                        populationName,
                                        null,
                                        null));
                break;
            case "cancel-default-iService":
                manager.cancel(
                        OWNER_COMPONENT,
                        populationName,
                        Executors.newSingleThreadExecutor(),
                        null);
                break;
            case "cancel-mockIService-RemoteException":
                when(mMockIBinder.queryLocalInterface(any())).thenReturn(mMockIService);
                doThrow(new RemoteException())
                        .when(mMockIService)
                        .cancel(any(), any(), any());
                spyCallback = spy(new MyTestCallback());

                manager.cancel(
                        OWNER_COMPONENT,
                        populationName,
                        Runnable::run,
                        spyCallback);

                verify(mContext, times(1)).bindService(any(), anyInt(), any(), any());
                verify(spyCallback, times(1)).onError(any(RemoteException.class));
                verify(mContext, times(1)).unbindService(any());
                break;
            case "cancel-mockIService-onSuccess":
                when(mMockIBinder.queryLocalInterface(any())).thenReturn(mMockIService);
                doAnswer(
                        invocation -> {
                            IFederatedComputeCallback federatedComputeCallback =
                                    invocation.getArgument(2);
                            federatedComputeCallback.onSuccess();
                            return null;
                        })
                        .when(mMockIService)
                        .cancel(any(), any(), any());
                spyCallback = spy(new MyTestCallback());

                manager.cancel(
                        OWNER_COMPONENT,
                        populationName,
                        Runnable::run,
                        spyCallback);

                verify(mContext, times(1)).bindService(any(), anyInt(), any(), any());
                verify(spyCallback, times(1)).onResult(isNull());
                verify(mContext, times(1)).unbindService(any());
                break;
            case "cancel-mockIService-onFailure":
                when(mMockIBinder.queryLocalInterface(any())).thenReturn(mMockIService);
                doAnswer(
                        invocation -> {
                            IFederatedComputeCallback federatedComputeCallback =
                                    invocation.getArgument(2);
                            federatedComputeCallback.onFailure(1);
                            return null;
                        })
                        .when(mMockIService)
                        .cancel(any(), any(), any());
                spyCallback = spy(new MyTestCallback());

                manager.cancel(
                        OWNER_COMPONENT,
                        populationName,
                        Runnable::run,
                        spyCallback);

                verify(mContext, times(1)).bindService(any(), anyInt(), any(), any());
                verify(spyCallback, times(1)).onError(any(FederatedComputeException.class));
                verify(mContext, times(1)).unbindService(any());
                break;
            default:
                break;
        }
    }

    public class MyTestContext extends ContextWrapper {

        MyTestContext(Context context) {
            super(context);
        }

        @Override
        public PackageManager getPackageManager() {
            return mMockPackageManager != null ? mMockPackageManager : super.getPackageManager();
        }

        @Override
        public boolean bindService(
                Intent service, int flags, Executor executor, ServiceConnection conn) {
            executor.execute(
                    () -> {
                        conn.onServiceConnected(null, mMockIBinder);
                    });
            return true;
        }

        public void unbindService(ServiceConnection conn) {}
    }

    public class MyTestCallback implements OutcomeReceiver<Object, Exception> {

        @Override
        public void onResult(Object o) {}

        @Override
        public void onError(Exception error) {
            OutcomeReceiver.super.onError(error);
        }
    }
}
