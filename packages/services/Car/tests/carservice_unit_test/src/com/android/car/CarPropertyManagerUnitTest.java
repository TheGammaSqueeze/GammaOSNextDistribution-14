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

import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.ICarProperty;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;

import com.android.car.internal.property.CarPropertyErrorCodes;
import com.android.car.internal.property.GetSetValueResult;
import com.android.car.internal.property.GetSetValueResultList;
import com.android.car.internal.property.IAsyncPropertyResultCallback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>This class contains unit tests for the {@link CarPropertyManager}.
 *
 * <p>Most test cases are already migrated to CarLibHostUnitTest. Host-side unit tests are
 * preferred. New tests should be added to CarLibHostUnitTest. This test class only contains
 * test cases that cannot be executed on host.
 */
@RunWith(MockitoJUnitRunner.class)
public final class CarPropertyManagerUnitTest {
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Mock
    private Car mCar;
    @Mock
    private ApplicationInfo mApplicationInfo;
    @Mock
    private ICarProperty mICarProperty;
    @Mock
    private Context mContext;
    @Mock
    private CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback;
    @Mock
    private CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback2;
    @Mock
    private CarPropertyManager.GetPropertyCallback mGetPropertyCallback;

    private CarPropertyManager mCarPropertyManager;

    @Before
    public void setUp() throws RemoteException {
        when(mCar.getContext()).thenReturn(mContext);
        when(mCar.getEventHandler()).thenReturn(mMainHandler);

        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.R;
        when(mContext.getApplicationInfo()).thenReturn(mApplicationInfo);

        mCarPropertyManager = new CarPropertyManager(mCar, mICarProperty);
    }

    @Test
    public void testGetPropertiesAsync_cancellationSignalCancelRequests() throws Exception {
        CarPropertyManager.GetPropertyRequest getPropertyRequest = createGetPropertyRequest();
        CancellationSignal cancellationSignal = new CancellationSignal();
        List<IAsyncPropertyResultCallback> callbackWrapper = new ArrayList<>();
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            callbackWrapper.add((IAsyncPropertyResultCallback) args[1]);
            return null;
        }).when(mICarProperty).getPropertiesAsync(any(), any(), anyLong());

        mCarPropertyManager.getPropertiesAsync(List.of(getPropertyRequest), cancellationSignal,
                /* callbackExecutor= */ null, mGetPropertyCallback);

        // Cancel the pending request.
        cancellationSignal.cancel();

        verify(mICarProperty).cancelRequests(new int[]{0});

        // Call the manager callback after the request is already cancelled.
        GetSetValueResult getValueResult =
                GetSetValueResult.newErrorResult(0,
                        new CarPropertyErrorCodes(
                                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR,
                                /* vendorErrorCode= */ 0,
                                /* systemErrorCode= */ 0));
        assertThat(callbackWrapper.size()).isEqualTo(1);
        callbackWrapper.get(0).onGetValueResults(
                new GetSetValueResultList(List.of(getValueResult)));

        // No client callbacks should be called.
        verify(mGetPropertyCallback, never()).onFailure(any());
        verify(mGetPropertyCallback, never()).onSuccess(any());
    }

    private CarPropertyManager.GetPropertyRequest createGetPropertyRequest() {
        return mCarPropertyManager.generateGetPropertyRequest(HVAC_TEMPERATURE_SET, 0);
    }
}
