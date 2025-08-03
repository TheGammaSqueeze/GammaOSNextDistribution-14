/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.hal;

import static android.car.Car.PERMISSION_VENDOR_EXTENSION;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;
import static android.car.VehiclePropertyIds.PERF_VEHICLE_SPEED;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_INTERNAL_ERROR;
import static android.car.hardware.property.VehicleHalStatusCode.STATUS_NOT_AVAILABLE;
import static android.car.hardware.property.VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_ENGINE;
import static android.car.hardware.property.VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO;
import static android.car.hardware.property.VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_ENGINE;
import static android.hardware.automotive.vehicle.VehicleProperty.SUPPORT_CUSTOMIZE_VENDOR_PERMISSION;

import static com.android.car.internal.property.CarPropertyErrorCodes.STATUS_OK;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;
import android.hardware.automotive.vehicle.VehicleVendorPermission;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.ArraySet;

import androidx.test.runner.AndroidJUnit4;

import com.android.car.VehicleStub.AsyncGetSetRequest;
import com.android.car.VehicleStub.GetVehicleStubAsyncResult;
import com.android.car.VehicleStub.SetVehicleStubAsyncResult;
import com.android.car.VehicleStub.VehicleStubCallbackInterface;
import com.android.car.hal.VehicleHal.HalSubscribeOptions;
import com.android.car.hal.property.PropertyHalServiceConfigs;
import com.android.car.hal.test.AidlVehiclePropValueBuilder;
import com.android.car.internal.property.AsyncPropertyServiceRequest;
import com.android.car.internal.property.CarPropertyErrorCodes;
import com.android.car.internal.property.CarSubscription;
import com.android.car.internal.property.GetSetValueResult;
import com.android.car.internal.property.GetSetValueResultList;
import com.android.car.internal.property.IAsyncPropertyResultCallback;
import com.android.internal.annotations.VisibleForTesting;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class PropertyHalServiceTest {
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private VehicleHal mVehicleHal;

    @Mock
    private IAsyncPropertyResultCallback mGetAsyncPropertyResultCallback;
    @Mock
    private IAsyncPropertyResultCallback mSetAsyncPropertyResultCallback;
    @Mock
    private IBinder mGetAsyncPropertyResultBinder;
    @Mock
    private IBinder mSetAsyncPropertyResultBinder;
    @Mock
    private PropertyHalService.PropertyHalListener mPropertyHalListener;
    @Captor
    private ArgumentCaptor<GetSetValueResultList> mAsyncResultCaptor;
    @Captor
    private ArgumentCaptor<IBinder.DeathRecipient> mDeathRecipientCaptor;
    @Captor
    private ArgumentCaptor<List> mListArgumentCaptor;
    @Mock
    private CarPropertyConfig mMockCarPropertyConfig1;
    @Mock
    private CarPropertyConfig mMockCarPropertyConfig2;

    private PropertyHalService mPropertyHalService;
    private static final int REQUEST_ID_1 = 1;
    private static final int REQUEST_ID_2 = 2;
    private static final int REQUEST_ID_3 = 3;
    private static final int REQUEST_ID_4 = 4;
    private static final int REQUEST_ID_5 = 5;
    private static final int RECEIVED_REQUEST_ID_1 = 0;
    private static final int RECEIVED_REQUEST_ID_2 = 1;
    private static final int RECEIVED_REQUEST_ID_3 = 2;
    private static final int INT32_PROP = VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION;
    private static final int PROPERTY_VALUE = 123;
    private static final int VENDOR_ERROR_CODE = 1234;
    private static final int SYSTEM_ERROR_CODE = 4321;
    private static final int VENDOR_PROPERTY_1 = 0x21e01111;
    private static final int VENDOR_PROPERTY_2 = 0x21e01112;
    private static final int VENDOR_PROPERTY_3 = 0x21e01113;
    private static final float SAMPLE_RATE_HZ = 17.0f;
    private static final AsyncPropertyServiceRequest GET_PROPERTY_SERVICE_REQUEST_1 =
            new AsyncPropertyServiceRequest(REQUEST_ID_1, HVAC_TEMPERATURE_SET, /* areaId= */ 0);
    private static final AsyncPropertyServiceRequest GET_PROPERTY_SERVICE_REQUEST_2 =
            new AsyncPropertyServiceRequest(REQUEST_ID_2, HVAC_TEMPERATURE_SET, /* areaId= */ 0);
    private static final AsyncPropertyServiceRequest GET_PROPERTY_SERVICE_REQUEST_STATIC_1 =
            new AsyncPropertyServiceRequest(REQUEST_ID_1, INT32_PROP, /* areaId= */ 0);
    private static final AsyncPropertyServiceRequest GET_PROPERTY_SERVICE_REQUEST_STATIC_2 =
            new AsyncPropertyServiceRequest(REQUEST_ID_2, INT32_PROP, /* areaId= */ 0);
    private static final AsyncPropertyServiceRequest SET_HVAC_REQUEST_ID_1 =
            new AsyncPropertyServiceRequest(REQUEST_ID_1, HVAC_TEMPERATURE_SET, /* areaId= */ 0,
                    new CarPropertyValue(HVAC_TEMPERATURE_SET, /* areaId= */ 0, SAMPLE_RATE_HZ));
    private static final AsyncPropertyServiceRequest SET_SPEED_REQUEST_ID_2 =
            new AsyncPropertyServiceRequest(REQUEST_ID_2, PERF_VEHICLE_SPEED, /* areaId= */ 0,
                    new CarPropertyValue(PERF_VEHICLE_SPEED, /* areaId= */ 0, SAMPLE_RATE_HZ));
    private static final AsyncPropertyServiceRequest SET_SPEED_REQUEST_ID_3 =
            new AsyncPropertyServiceRequest(REQUEST_ID_3, PERF_VEHICLE_SPEED, /* areaId= */ 0,
                    new CarPropertyValue(PERF_VEHICLE_SPEED, /* areaId= */ 0, SAMPLE_RATE_HZ));
    private static final AsyncPropertyServiceRequest SET_VEHICLE_SPEED_AREA_ID_1_REQUEST =
            new AsyncPropertyServiceRequest(REQUEST_ID_4, PERF_VEHICLE_SPEED, /* areaId= */ 1,
                    new CarPropertyValue(PERF_VEHICLE_SPEED, /* areaId= */ 1, SAMPLE_RATE_HZ));
    private static final AsyncPropertyServiceRequest SET_VEHICLE_SPEED_AREA_ID_2_REQUEST =
            new AsyncPropertyServiceRequest(REQUEST_ID_5, PERF_VEHICLE_SPEED, /* areaId= */ 2,
                    new CarPropertyValue(PERF_VEHICLE_SPEED, /* areaId= */ 2, SAMPLE_RATE_HZ));

    private static final long TEST_UPDATE_TIMESTAMP_NANOS = 1234;
    private final HalPropValueBuilder mPropValueBuilder = new HalPropValueBuilder(
            /* isAidl= */ true);
    private final HalPropValue mPropValue = mPropValueBuilder.build(
            HVAC_TEMPERATURE_SET, /* areaId= */ 0, TEST_UPDATE_TIMESTAMP_NANOS, /* status= */ 0,
            SAMPLE_RATE_HZ);
    private final HalPropValue mNonTargetPropValue = mPropValueBuilder.build(
            HVAC_TEMPERATURE_SET, /* areaId= */ 0, TEST_UPDATE_TIMESTAMP_NANOS, /* status= */ 0,
            16.0f);
    private final HalPropValue mPropValue2 = mPropValueBuilder.build(
            PERF_VEHICLE_SPEED, /* areaId= */ 0, TEST_UPDATE_TIMESTAMP_NANOS, /* status= */ 0,
            SAMPLE_RATE_HZ);
    private final HalPropValue mPropValue3 = mPropValueBuilder.build(
            INT32_PROP, /* areaId= */ 0, TEST_UPDATE_TIMESTAMP_NANOS, /* status= */ 0,
            3);

    private AsyncPropertyServiceRequest copyRequest(AsyncPropertyServiceRequest request) {
        return new AsyncPropertyServiceRequest(request.getRequestId(), request.getPropertyId(),
                request.getAreaId(), request.getCarPropertyValue());
    }

    @Before
    public void setUp() {
        when(mVehicleHal.getHalPropValueBuilder()).thenReturn(mPropValueBuilder);
        mPropertyHalService = new PropertyHalService(mVehicleHal);
        mPropertyHalService.setPropertyHalServiceConfigs(PropertyHalServiceConfigs.newConfigs());
        mPropertyHalService.init();

        HalPropConfig mockPropConfig1 = mock(HalPropConfig.class);
        when(mockPropConfig1.getPropId()).thenReturn(VehicleProperty.HVAC_TEMPERATURE_SET);
        when(mockPropConfig1.getChangeMode()).thenReturn(VehiclePropertyChangeMode.ON_CHANGE);
        when(mockPropConfig1.toCarPropertyConfig(VehicleProperty.HVAC_TEMPERATURE_SET))
                .thenReturn(mMockCarPropertyConfig1);
        when(mMockCarPropertyConfig1.getChangeMode())
                .thenReturn(VehiclePropertyChangeMode.ON_CHANGE);
        when(mMockCarPropertyConfig1.getAreaIds()).thenReturn(new int[]{0});

        HalPropConfig mockPropConfig2 = mock(HalPropConfig.class);
        when(mockPropConfig2.getPropId()).thenReturn(VehicleProperty.PERF_VEHICLE_SPEED);
        when(mockPropConfig2.getChangeMode()).thenReturn(VehiclePropertyChangeMode.CONTINUOUS);
        when(mockPropConfig2.getMinSampleRate()).thenReturn(20.0f);
        when(mockPropConfig2.getMaxSampleRate()).thenReturn(100.0f);
        when(mockPropConfig2.toCarPropertyConfig(VehicleProperty.PERF_VEHICLE_SPEED))
                .thenReturn(mMockCarPropertyConfig2);
        when(mMockCarPropertyConfig2.getChangeMode())
                .thenReturn(VehiclePropertyChangeMode.CONTINUOUS);
        when(mMockCarPropertyConfig2.getMinSampleRate()).thenReturn(20.0f);
        when(mMockCarPropertyConfig2.getMaxSampleRate()).thenReturn(100.0f);
        when(mMockCarPropertyConfig2.getAreaIds()).thenReturn(new int[]{0});

        HalPropConfig mockPropConfig3 = mock(HalPropConfig.class);
        when(mockPropConfig3.getChangeMode()).thenReturn(VehiclePropertyChangeMode.STATIC);
        when(mockPropConfig3.getPropId()).thenReturn(INT32_PROP);

        mPropertyHalService.takeProperties(List.of(mockPropConfig1, mockPropConfig2,
                mockPropConfig3));
        mPropertyHalService.getPropertyList();
    }

    @After
    public void tearDown() {
        mPropertyHalService.release();
        mPropertyHalService = null;
    }

    private void verifyNoPendingRequest() {
        assertWithMessage("No pending async requests after test finish")
                .that(mPropertyHalService.countPendingAsyncRequests()).isEqualTo(0);
        assertWithMessage("No pending async set request waiting for update after test finish")
                .that(mPropertyHalService.countHalPropIdToWaitForUpdateRequests()).isEqualTo(0);
    }

    private Object deliverResult(InvocationOnMock invocation, Integer expectedServiceRequestId,
            int errorCode, HalPropValue propValue, boolean get) {
        CarPropertyErrorCodes carPropertyErrorCodes =
                new CarPropertyErrorCodes(
                        errorCode,
                        /* vendorErrorCode= */ 0,
                        /* systemErrorCode= */ 0);
        return deliverResult(
                invocation, expectedServiceRequestId, carPropertyErrorCodes, propValue, get);
    }

    private Object deliverResult(InvocationOnMock invocation, Integer expectedServiceRequestId,
            CarPropertyErrorCodes carPropertyErrorCodes, HalPropValue propValue, boolean get) {
        Object[] args = invocation.getArguments();
        List getVehicleHalRequests = (List) args[0];
        Map<VehicleStubCallbackInterface, List<GetVehicleStubAsyncResult>> callbackToGetResults =
                new HashMap<>();
        Map<VehicleStubCallbackInterface, List<SetVehicleStubAsyncResult>> callbackToSetResults =
                new HashMap<>();
        for (int i = 0; i < getVehicleHalRequests.size(); i++) {
            AsyncGetSetRequest getVehicleHalRequest = (AsyncGetSetRequest)
                    getVehicleHalRequests.get(i);
            VehicleStubCallbackInterface callback = (VehicleStubCallbackInterface) args[1];
            if (callbackToGetResults.get(callback) == null) {
                callbackToGetResults.put(callback, new ArrayList<>());
            }
            if (callbackToSetResults.get(callback) == null) {
                callbackToSetResults.put(callback, new ArrayList<>());
            }

            int serviceRequestId = getVehicleHalRequest.getServiceRequestId();
            if (expectedServiceRequestId != null) {
                assertThat(serviceRequestId).isEqualTo(expectedServiceRequestId);
            }

            if (get) {
                if (propValue != null) {
                    callbackToGetResults.get(callback).add(new GetVehicleStubAsyncResult(
                            serviceRequestId, propValue));
                } else {
                    callbackToGetResults.get(callback).add(new GetVehicleStubAsyncResult(
                            serviceRequestId, carPropertyErrorCodes));
                }
            } else {
                callbackToSetResults.get(callback).add(new SetVehicleStubAsyncResult(
                        serviceRequestId, carPropertyErrorCodes));
            }
        }

        for (VehicleStubCallbackInterface callback : callbackToGetResults.keySet()) {
            callback.onGetAsyncResults(callbackToGetResults.get(callback));
        }
        for (VehicleStubCallbackInterface callback : callbackToSetResults.keySet()) {
            callback.onSetAsyncResults(callbackToSetResults.get(callback));
        }

        return null;
    }

    private Object deliverOkayGetResult(InvocationOnMock invocation) {
        return deliverOkayGetResult(invocation, mPropValue);
    }

    private Object deliverOkayGetResult(InvocationOnMock invocation, HalPropValue propValue) {
        return deliverResult(invocation, /* expectedServiceRequestId= */ null,
                STATUS_OK, propValue, true);
    }

    private Object deliverOkayGetResult(InvocationOnMock invocation,
            Integer expectedServiceRequestId) {
        return deliverResult(invocation, expectedServiceRequestId, STATUS_OK,
                mPropValue, true);
    }

    private Object deliverOkaySetResult(InvocationOnMock invocation) {
        return deliverOkaySetResult(invocation, /* expectedServiceRequestId= */ null);
    }

    private Object deliverOkaySetResult(InvocationOnMock invocation,
            Integer expectedServiceRequestId) {
        return deliverResult(invocation, expectedServiceRequestId, STATUS_OK,
                null, false);
    }

    private Object deliverTryAgainGetResult(InvocationOnMock invocation) {
        return deliverTryAgainGetResult(invocation, /* expectedServiceRequestId= */ null);
    }

    private Object deliverTryAgainGetResult(InvocationOnMock invocation,
            Integer expectedServiceRequestId) {
        return deliverResult(invocation, expectedServiceRequestId,
                CarPropertyErrorCodes.STATUS_TRY_AGAIN, null, true);
    }

    private Object deliverTryAgainSetResult(InvocationOnMock invocation) {
        return deliverTryAgainSetResult(invocation, /* expectedServiceRequestId= */ null);
    }

    private Object deliverTryAgainSetResult(InvocationOnMock invocation,
            Integer expectedServiceRequestId) {
        return deliverResult(invocation, expectedServiceRequestId,
                CarPropertyErrorCodes.STATUS_TRY_AGAIN, null, false);
    }

    private Object deliverErrorGetResult(InvocationOnMock invocation, int errorCode) {
        return deliverErrorGetResult(invocation, /* expectedServiceRequestId= */ null, errorCode);
    }

    private Object deliverErrorGetResult(InvocationOnMock invocation,
            Integer expectedServiceRequestId, int errorCode) {
        return deliverResult(invocation, expectedServiceRequestId, errorCode, null, true);
    }

    private Object deliverErrorSetResult(InvocationOnMock invocation, int errorCode) {
        return deliverErrorSetResult(invocation, /* expectedServiceRequestId= */ null, errorCode);
    }

    private Object deliverErrorSetResult(InvocationOnMock invocation,
            Integer expectedServiceRequestId, int errorCode) {
        return deliverResult(invocation, expectedServiceRequestId, errorCode, null, false);
    }

    @Test
    public void testGetCarPropertyValuesAsync() {
        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(List.of(GET_PROPERTY_SERVICE_REQUEST_1),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        ArgumentCaptor<List<AsyncGetSetRequest>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(mVehicleHal).getAsync(captor.capture(),
                any(VehicleStubCallbackInterface.class));
        AsyncGetSetRequest gotRequest = captor.getValue().get(0);
        assertThat(gotRequest.getServiceRequestId()).isEqualTo(RECEIVED_REQUEST_ID_1);
        assertThat(gotRequest.getHalPropValue().getPropId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(gotRequest.getTimeoutUptimeMs()).isGreaterThan(1000);

        mPropertyHalService.cancelRequests(new int[]{REQUEST_ID_1});
        verify(mVehicleHal, timeout(1000)).cancelRequests(List.of(0));

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_multipleRequests() {
        List<AsyncPropertyServiceRequest> getPropertyServiceRequests = new ArrayList<>();
        getPropertyServiceRequests.add(GET_PROPERTY_SERVICE_REQUEST_1);
        getPropertyServiceRequests.add(GET_PROPERTY_SERVICE_REQUEST_2);
        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(getPropertyServiceRequests,
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        ArgumentCaptor<List<AsyncGetSetRequest>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(mVehicleHal).getAsync(captor.capture(),
                any(VehicleStubCallbackInterface.class));
        AsyncGetSetRequest gotRequest0 = captor.getValue().get(0);
        assertThat(gotRequest0.getServiceRequestId()).isEqualTo(RECEIVED_REQUEST_ID_1);
        assertThat(gotRequest0.getHalPropValue().getPropId()).isEqualTo(
                HVAC_TEMPERATURE_SET);
        assertThat(gotRequest0.getHalPropValue().getAreaId()).isEqualTo(0);
        assertThat(gotRequest0.getTimeoutUptimeMs()).isGreaterThan(1000);
        AsyncGetSetRequest gotRequest1 = captor.getValue().get(1);
        assertThat(gotRequest1.getServiceRequestId()).isEqualTo(RECEIVED_REQUEST_ID_2);
        assertThat(gotRequest1.getHalPropValue().getPropId()).isEqualTo(
                HVAC_TEMPERATURE_SET);
        assertThat(gotRequest1.getHalPropValue().getAreaId()).isEqualTo(0);
        assertThat(gotRequest1.getTimeoutUptimeMs()).isGreaterThan(1000);

        mPropertyHalService.cancelRequests(new int[]{REQUEST_ID_1, REQUEST_ID_2});

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_staticCacheMultipleRequests() throws Exception {
        doAnswer((invocation) -> {
            return deliverOkayGetResult(invocation, mPropValue3);
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(
                List.of(GET_PROPERTY_SERVICE_REQUEST_STATIC_1), mGetAsyncPropertyResultCallback,
                /* timeoutInMs= */ 1000, /* asyncRequestStartTime= */ 0);

        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyValue().getValue()).isEqualTo(3);
        assertThat(result.getCarPropertyValue().getAreaId()).isEqualTo(0);
        reset(mVehicleHal);
        reset(mGetAsyncPropertyResultCallback);
        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(
                List.of(GET_PROPERTY_SERVICE_REQUEST_STATIC_1), mGetAsyncPropertyResultCallback,
                /* timeoutInMs= */ 1000, /* asyncRequestStartTime= */ 0);
        verify(mVehicleHal, never()).getAsync(any(), any());
        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult cachedResult = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(cachedResult.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(cachedResult.getCarPropertyValue().getValue()).isEqualTo(3);
        assertThat(cachedResult.getCarPropertyValue().getAreaId()).isEqualTo(0);
    }

    @Test
    public void testGetCarPropertyValuesAsync_linkToDeath() throws RemoteException {
        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();
        List<AsyncPropertyServiceRequest> getPropertyServiceRequests = mock(List.class);
        mPropertyHalService.getCarPropertyValuesAsync(getPropertyServiceRequests,
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);
        verify(mGetAsyncPropertyResultBinder).linkToDeath(any(IBinder.DeathRecipient.class),
                anyInt());

        mPropertyHalService.cancelRequests(new int[]{REQUEST_ID_1});

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_binderDiedBeforeLinkToDeath() throws RemoteException {
        IBinder mockBinder = mock(IBinder.class);
        when(mGetAsyncPropertyResultCallback.asBinder()).thenReturn(mockBinder);
        doThrow(new RemoteException()).when(mockBinder).linkToDeath(any(), anyInt());
        List<AsyncPropertyServiceRequest> getPropertyServiceRequests = mock(List.class);

        assertThrows(IllegalStateException.class, () -> {
            mPropertyHalService.getCarPropertyValuesAsync(getPropertyServiceRequests,
                    mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                    /* asyncRequestStartTime= */ 0);
        });

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_unlinkToDeath_onBinderDied() throws RemoteException {
        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();
        List<AsyncPropertyServiceRequest> getPropertyServiceRequests = mock(List.class);
        mPropertyHalService.getCarPropertyValuesAsync(getPropertyServiceRequests,
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        ArgumentCaptor<IBinder.DeathRecipient> recipientCaptor = ArgumentCaptor.forClass(
                IBinder.DeathRecipient.class);
        verify(mGetAsyncPropertyResultBinder).linkToDeath(recipientCaptor.capture(), eq(0));

        recipientCaptor.getValue().binderDied();

        verify(mGetAsyncPropertyResultBinder).unlinkToDeath(recipientCaptor.getValue(), 0);

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_normalResult() throws RemoteException {
        doAnswer((invocation) -> {
            return deliverOkayGetResult(invocation, RECEIVED_REQUEST_ID_1);
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(List.of(GET_PROPERTY_SERVICE_REQUEST_1),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);


        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyValue().getValue()).isEqualTo(SAMPLE_RATE_HZ);
        assertThat(result.getCarPropertyValue().getAreaId()).isEqualTo(0);

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_retryTwiceAndSucceed() throws RemoteException {
        doAnswer(new Answer() {
            private int mCount = 0;

            public Object answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                List<AsyncGetSetRequest> getVehicleHalRequests = (List<AsyncGetSetRequest>) args[0];
                assertThat(getVehicleHalRequests.size()).isEqualTo(1);

                switch (mCount++) {
                    case 0:
                        return deliverTryAgainGetResult(invocation, RECEIVED_REQUEST_ID_1);
                    case 1:
                        return deliverTryAgainGetResult(invocation, RECEIVED_REQUEST_ID_2);
                    case 2:
                        return deliverOkayGetResult(invocation, RECEIVED_REQUEST_ID_3);
                    default:
                        throw new IllegalStateException("Only expect 3 calls");
                }
            }
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(List.of(GET_PROPERTY_SERVICE_REQUEST_1),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyValue().getValue()).isEqualTo(SAMPLE_RATE_HZ);
        assertThat(result.getCarPropertyValue().getAreaId()).isEqualTo(0);

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_retryAndTimeout() throws RemoteException {
        doAnswer((invocation) -> {
            // For every request, we return retry result.
            return deliverTryAgainGetResult(invocation);
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(List.of(GET_PROPERTY_SERVICE_REQUEST_1),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 10,
                /* asyncRequestStartTime= */ 0);

        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_TIMEOUT);

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_noResultTimeout() throws RemoteException {
        doNothing().when(mVehicleHal).getAsync(anyList(),
                any(VehicleStubCallbackInterface.class));
        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(List.of(GET_PROPERTY_SERVICE_REQUEST_1),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 10,
                /* asyncRequestStartTime= */ 0);

        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_TIMEOUT);

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_timeoutFromVehicleStub() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            List getVehicleHalRequests = (List) args[0];
            AsyncGetSetRequest getVehicleHalRequest =
                    (AsyncGetSetRequest) getVehicleHalRequests.get(0);
            VehicleStubCallbackInterface getVehicleStubAsyncCallback =
                    (VehicleStubCallbackInterface) args[1];
            // Simulate the request has already timed-out.
            getVehicleStubAsyncCallback.onRequestsTimeout(List.of(
                    getVehicleHalRequest.getServiceRequestId()));
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(List.of(GET_PROPERTY_SERVICE_REQUEST_1),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_TIMEOUT);

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_errorResult() throws RemoteException {
        doAnswer((invocation) -> {
            return deliverErrorGetResult(invocation, RECEIVED_REQUEST_ID_1,
                    CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(List.of(GET_PROPERTY_SERVICE_REQUEST_1),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        assertThat(result.getCarPropertyValue()).isEqualTo(null);

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_errorResultVendorErrorCode() throws RemoteException {
        doAnswer((invocation) -> {
            CarPropertyErrorCodes errorCodes = new CarPropertyErrorCodes(
                    CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR,
                    VENDOR_ERROR_CODE,
                    SYSTEM_ERROR_CODE);
            return deliverResult(invocation, RECEIVED_REQUEST_ID_1, errorCodes,
                    /* propValue= */ null, /* get= */ true);
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(List.of(GET_PROPERTY_SERVICE_REQUEST_1),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        assertThat(result.getCarPropertyErrorCodes().getVendorErrorCode())
                .isEqualTo(VENDOR_ERROR_CODE);
        assertThat(result.getCarPropertyErrorCodes().getSystemErrorCode()).isEqualTo(
                SYSTEM_ERROR_CODE);
        assertThat(result.getCarPropertyValue()).isEqualTo(null);

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_propStatusUnavailable() throws RemoteException {
        doAnswer((invocation) -> {
            HalPropValue propValue = mPropValueBuilder.build(
                    HVAC_TEMPERATURE_SET, /* areaId= */ 0, TEST_UPDATE_TIMESTAMP_NANOS,
                    VehiclePropertyStatus.UNAVAILABLE, SAMPLE_RATE_HZ);
            return deliverOkayGetResult(invocation, propValue);
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(List.of(GET_PROPERTY_SERVICE_REQUEST_1),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        assertThat(result.getCarPropertyErrorCodes().getVendorErrorCode()).isEqualTo(0);
        assertThat(result.getCarPropertyErrorCodes().getSystemErrorCode()).isEqualTo(0);
        assertThat(result.getCarPropertyValue()).isEqualTo(null);

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_propStatusError() throws RemoteException {
        doAnswer((invocation) -> {
            HalPropValue propValue = mPropValueBuilder.build(
                    HVAC_TEMPERATURE_SET, /* areaId= */ 0, TEST_UPDATE_TIMESTAMP_NANOS,
                    VehiclePropertyStatus.ERROR, SAMPLE_RATE_HZ);
            return deliverOkayGetResult(invocation, propValue);
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(List.of(GET_PROPERTY_SERVICE_REQUEST_1),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        assertThat(result.getCarPropertyErrorCodes().getVendorErrorCode()).isEqualTo(0);
        assertThat(result.getCarPropertyErrorCodes().getSystemErrorCode()).isEqualTo(0);
        assertThat(result.getCarPropertyValue()).isEqualTo(null);

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_multipleResultsSameCall() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            List<AsyncGetSetRequest> getVehicleHalRequests = (List) args[0];
            VehicleStubCallbackInterface getVehicleStubAsyncCallback =
                    (VehicleStubCallbackInterface) args[1];

            assertThat(getVehicleHalRequests.size()).isEqualTo(2);
            assertThat(getVehicleHalRequests.get(0).getServiceRequestId()).isEqualTo(
                    RECEIVED_REQUEST_ID_1);
            assertThat(getVehicleHalRequests.get(0).getHalPropValue().getPropId()).isEqualTo(
                    HVAC_TEMPERATURE_SET);
            assertThat(getVehicleHalRequests.get(1).getServiceRequestId()).isEqualTo(
                    RECEIVED_REQUEST_ID_2);
            assertThat(getVehicleHalRequests.get(1).getHalPropValue().getPropId()).isEqualTo(
                    HVAC_TEMPERATURE_SET);

            List<GetVehicleStubAsyncResult> getVehicleStubAsyncResults =
                    new ArrayList<>();
            getVehicleStubAsyncResults.add(
                    new GetVehicleStubAsyncResult(RECEIVED_REQUEST_ID_1, mPropValue));
            getVehicleStubAsyncResults.add(
                    new GetVehicleStubAsyncResult(RECEIVED_REQUEST_ID_2, mPropValue));
            getVehicleStubAsyncCallback.onGetAsyncResults(getVehicleStubAsyncResults);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        List<AsyncPropertyServiceRequest> getPropertyServiceRequests = new ArrayList<>();
        getPropertyServiceRequests.add(GET_PROPERTY_SERVICE_REQUEST_1);
        getPropertyServiceRequests.add(GET_PROPERTY_SERVICE_REQUEST_2);
        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(getPropertyServiceRequests,
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result1 = mAsyncResultCaptor.getValue().getList().get(0);
        GetSetValueResult result2 = mAsyncResultCaptor.getValue().getList().get(1);
        assertThat(result1.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result1.getCarPropertyValue().getValue()).isEqualTo(SAMPLE_RATE_HZ);
        assertThat(result2.getRequestId()).isEqualTo(REQUEST_ID_2);
        assertThat(result2.getCarPropertyValue().getValue()).isEqualTo(SAMPLE_RATE_HZ);

        verifyNoPendingRequest();
    }

    @Test
    public void testGetCarPropertyValuesAsync_multipleResultsDifferentCalls()
            throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            List<AsyncGetSetRequest> getVehicleHalRequests = (List) args[0];
            VehicleStubCallbackInterface getVehicleStubAsyncCallback =
                    (VehicleStubCallbackInterface) args[1];

            assertThat(getVehicleHalRequests.size()).isEqualTo(2);
            assertThat(getVehicleHalRequests.get(0).getServiceRequestId()).isEqualTo(
                    RECEIVED_REQUEST_ID_1);
            assertThat(getVehicleHalRequests.get(0).getHalPropValue().getPropId()).isEqualTo(
                    HVAC_TEMPERATURE_SET);
            assertThat(getVehicleHalRequests.get(1).getServiceRequestId()).isEqualTo(
                    RECEIVED_REQUEST_ID_2);
            assertThat(getVehicleHalRequests.get(1).getHalPropValue().getPropId()).isEqualTo(
                    HVAC_TEMPERATURE_SET);

            getVehicleStubAsyncCallback.onGetAsyncResults(
                    List.of(new GetVehicleStubAsyncResult(RECEIVED_REQUEST_ID_1,
                            mPropValue)));
            getVehicleStubAsyncCallback.onGetAsyncResults(
                    List.of(new GetVehicleStubAsyncResult(RECEIVED_REQUEST_ID_2,
                            mPropValue)));
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        List<AsyncPropertyServiceRequest> getPropertyServiceRequests = new ArrayList<>();
        getPropertyServiceRequests.add(GET_PROPERTY_SERVICE_REQUEST_1);
        getPropertyServiceRequests.add(GET_PROPERTY_SERVICE_REQUEST_2);
        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.getCarPropertyValuesAsync(getPropertyServiceRequests,
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mGetAsyncPropertyResultCallback, timeout(1000).times(2))
                .onGetValueResults(mAsyncResultCaptor.capture());
        List<GetSetValueResultList> getValuesResults = mAsyncResultCaptor.getAllValues();
        assertThat(getValuesResults.get(0).getList().get(0).getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(getValuesResults.get(0).getList().get(0).getCarPropertyValue().getValue())
                .isEqualTo(SAMPLE_RATE_HZ);
        assertThat(getValuesResults.get(1).getList().get(0).getRequestId()).isEqualTo(REQUEST_ID_2);
        assertThat(getValuesResults.get(1).getList().get(0).getCarPropertyValue().getValue())
                .isEqualTo(SAMPLE_RATE_HZ);

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync() {
        Set<Integer> vhalCancelledRequestIds = new ArraySet<>();
        doAnswer((invocation) -> {
            List<Integer> ids = (List<Integer>) invocation.getArgument(0);
            for (int i = 0; i < ids.size(); i++) {
                vhalCancelledRequestIds.add(ids.get(i));
            }
            return null;
        }).when(mVehicleHal).cancelRequests(any());
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        ArgumentCaptor<List<AsyncGetSetRequest>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(mVehicleHal).setAsync(captor.capture(), any(VehicleStubCallbackInterface.class));
        AsyncGetSetRequest gotRequest = captor.getValue().get(0);
        assertThat(gotRequest.getServiceRequestId()).isEqualTo(RECEIVED_REQUEST_ID_1);
        assertThat(gotRequest.getHalPropValue().getPropId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(gotRequest.getHalPropValue().getFloatValue(0)).isEqualTo(SAMPLE_RATE_HZ);
        assertThat(gotRequest.getTimeoutUptimeMs()).isGreaterThan(1000);

        mPropertyHalService.cancelRequests(new int[]{REQUEST_ID_1});

        // Because we cancel the onging async set property request, the ongoing get initial value
        // request should be cancelled as well.
        verify(mVehicleHal, timeout(1000).times(2)).cancelRequests(any());
        assertThat(vhalCancelledRequestIds).containsExactlyElementsIn(new Integer[]{0, 1});

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync_configNotFound() {
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();
        AsyncPropertyServiceRequest request = new AsyncPropertyServiceRequest(
                REQUEST_ID_1, /* propId= */ 1, /* areaId= */ 0,
                new CarPropertyValue(/* propId= */ 1, /* areaId= */ 0, SAMPLE_RATE_HZ));

        assertThrows(IllegalArgumentException.class, () -> {
            mPropertyHalService.setCarPropertyValuesAsync(List.of(request),
                    mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                    /* asyncRequestStartTime= */ 0);
        });

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync_linkToDeath() throws RemoteException {
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();
        List<AsyncPropertyServiceRequest> setPropertyServiceRequests = mock(List.class);

        mPropertyHalService.setCarPropertyValuesAsync(setPropertyServiceRequests,
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mSetAsyncPropertyResultBinder).linkToDeath(any(IBinder.DeathRecipient.class),
                anyInt());

        verifyNoPendingRequest();
    }

    // Test the case where we don't wait for property update event. The success callback should be
    // immediately called when the set request succeeded.
    @Test
    public void testSetCarPropertyValuesAsync_noWaitForPropertyUpdate()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        AsyncPropertyServiceRequest request = copyRequest(SET_HVAC_REQUEST_ID_1);
        request.setWaitForPropertyUpdate(false);

        mPropertyHalService.setCarPropertyValuesAsync(List.of(request),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        // Must not subscribe to the property for update events.
        verify(mVehicleHal, never()).subscribeProperty(any(), anyList());
        // Must not send get initial value request.
        verify(mVehicleHal, never()).getAsync(any(), any());
        assertThat(setInvocationWrap).hasSize(1);

        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));

        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                .isEqualTo(STATUS_OK);
        // This should be the time when the request is successfully sent.
        assertThat(result.getUpdateTimestampNanos()).isGreaterThan(0);

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync_noWaitForPropertyUpdateWithMultipleAreaRequests()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        AsyncPropertyServiceRequest request1 = copyRequest(SET_VEHICLE_SPEED_AREA_ID_1_REQUEST);
        AsyncPropertyServiceRequest request2 = copyRequest(SET_VEHICLE_SPEED_AREA_ID_2_REQUEST);
        request1.setWaitForPropertyUpdate(false);
        request2.setWaitForPropertyUpdate(false);

        mPropertyHalService.setCarPropertyValuesAsync(List.of(request1, request2),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        // Must not subscribe to the property for update events.
        verify(mVehicleHal, never()).subscribeProperty(any(), anyList());
        verify(mVehicleHal, never()).subscribeProperty(any(), anyList());
        // Must not send get initial value request.
        verify(mVehicleHal, never()).getAsync(any(), any());
        assertThat(setInvocationWrap).hasSize(1);

        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));

        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        GetSetValueResult result1 = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result1.getRequestId()).isEqualTo(REQUEST_ID_4);
        assertThat(result1.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                .isEqualTo(STATUS_OK);
        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        GetSetValueResult result2 = mAsyncResultCaptor.getValue().getList().get(1);
        assertThat(result2.getRequestId()).isEqualTo(REQUEST_ID_5);
        assertThat(result2.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                .isEqualTo(STATUS_OK);

        // This should be the time when the request is successfully sent.
        assertThat(result1.getUpdateTimestampNanos()).isGreaterThan(0);
        assertThat(result2.getUpdateTimestampNanos()).isGreaterThan(0);

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync_mixWaitNoWaitForPropertyUpdate() throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();
        List<HalServiceBase> serviceWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            serviceWrap.add(invocation.getArgument(0));
            return null;
        }).when(mVehicleHal).subscribeProperty(any(), anyList());
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        AsyncPropertyServiceRequest request1 = copyRequest(SET_HVAC_REQUEST_ID_1);
        AsyncPropertyServiceRequest request2 = copyRequest(SET_SPEED_REQUEST_ID_2);
        request2.setWaitForPropertyUpdate(false);

        mPropertyHalService.setCarPropertyValuesAsync(List.of(request1, request2),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(hvacHalSubscribeOption());
        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Returns the get initial value result, assume we failed to get initial values.
        deliverErrorGetResult(getInvocationWrap.get(0),
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);

        verify(mSetAsyncPropertyResultCallback, never()).onSetValueResults(any());

        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));

        // We should receive the success callback for request 2 since the set request is already
        // sent.
        verify(mSetAsyncPropertyResultCallback).onSetValueResults(
                mAsyncResultCaptor.capture());

        // Deliver updated value for request 1. Request 2 don't need an updated value.
        assertThat(serviceWrap).hasSize(1);
        serviceWrap.get(0).onHalEvents(List.of(mPropValue));

        // Get init value result must not be passed to the client.
        verify(mSetAsyncPropertyResultCallback, never()).onGetValueResults(any());
        // We should receive the success callback for request 1.
        verify(mSetAsyncPropertyResultCallback, times(2)).onSetValueResults(
                mAsyncResultCaptor.capture());
        for (GetSetValueResultList results: mAsyncResultCaptor.getAllValues()) {
            GetSetValueResult result = results.getList().get(0);
            assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                    .isEqualTo(STATUS_OK);
            if (result.getRequestId() == REQUEST_ID_1) {
                assertThat(result.getUpdateTimestampNanos()).isEqualTo(
                        TEST_UPDATE_TIMESTAMP_NANOS);
            } else {
                assertThat(result.getUpdateTimestampNanos()).isNotEqualTo(
                        TEST_UPDATE_TIMESTAMP_NANOS);
                // This should be elapsedRealTimeNano when the set is done.
                assertThat(result.getUpdateTimestampNanos()).isGreaterThan(0);
            }
        }
        // After the result comes, we must unsubscribe the property.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));

        verifyNoPendingRequest();
    }

    // Test the case where we get the result for the init value before we get the result for
    // async set. The init value is the same as the target value.
    @Test
    public void testSetCarPropertyValuesAsync_initValueSameAsTargetValue_beforeSetResult()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(hvacHalSubscribeOption());
        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Returns the get initial value result.
        deliverOkayGetResult(getInvocationWrap.get(0));

        verify(mSetAsyncPropertyResultCallback, never()).onSetValueResults(any());

        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));

        // Get init value result must not be passed to the client.
        verify(mSetAsyncPropertyResultCallback, never()).onGetValueResults(any());
        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                .isEqualTo(STATUS_OK);
        assertThat(result.getUpdateTimestampNanos()).isEqualTo(TEST_UPDATE_TIMESTAMP_NANOS);
        // After the result comes, we must unsubscribe the property.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));

        verifyNoPendingRequest();
    }

    // Test the case where we get the result for the init value after we get the result for
    // async set. The init value is the same as the target value.
    @Test
    public void testSetCarPropertyValuesAsync_initValueSameAsTargetValue_afterSetResult()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(hvacHalSubscribeOption());
        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));

        verify(mSetAsyncPropertyResultCallback, never()).onSetValueResults(any());

        // Returns the get initial value result.
        deliverOkayGetResult(getInvocationWrap.get(0));

        // Get init value result must not be passed to the client.
        verify(mSetAsyncPropertyResultCallback, never()).onGetValueResults(any());
        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                .isEqualTo(STATUS_OK);
        assertThat(result.getUpdateTimestampNanos()).isEqualTo(TEST_UPDATE_TIMESTAMP_NANOS);
        // After the result comes, we must unsubscribe the property.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));

        verifyNoPendingRequest();
    }

    // Test the case where the get initial value and set value request retried once.
    // The init value is the same as the target value.
    @Test
    public void testSetCarPropertyValuesAsync_initValueSameAsTargetValue_retry()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(hvacHalSubscribeOption());
        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Retry get initial value request.
        deliverTryAgainGetResult(getInvocationWrap.get(0));
        // Retry set initial value request.
        deliverTryAgainSetResult(setInvocationWrap.get(0));

        // Wait until the retry happens.
        verify(mVehicleHal, timeout(1000).times(2)).getAsync(any(), any());
        verify(mVehicleHal, timeout(1000).times(2)).setAsync(any(), any());

        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(1));
        // Returns the get initial value result.
        deliverOkayGetResult(getInvocationWrap.get(1));

        verify(mSetAsyncPropertyResultCallback, never()).onGetValueResults(any());
        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                .isEqualTo(STATUS_OK);
        assertThat(result.getUpdateTimestampNanos()).isEqualTo(TEST_UPDATE_TIMESTAMP_NANOS);
        // After the result comes, we must unsubscribe the property.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));

        verifyNoPendingRequest();
    }

    // Test the case where the get initial value returns a different value than target value.
    @Test
    public void testSetCarPropertyValuesAsync_initValueDiffTargetValue_timeout()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 100,
                /* asyncRequestStartTime= */ 0);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(hvacHalSubscribeOption());
        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Returns the get initial value result.
        deliverOkayGetResult(getInvocationWrap.get(0), mPropValueBuilder.build(
                HVAC_TEMPERATURE_SET, /* areaId= */ 0, 16.0f));
        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));

        // Get init value result must not be passed to the client.
        verify(mSetAsyncPropertyResultCallback, never()).onGetValueResults(any());
        // Eventually the request should time out.
        verify(mSetAsyncPropertyResultCallback, timeout(1000)).onSetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_TIMEOUT);

        verifyNoPendingRequest();
    }

    // Test the case where we get set value error result after we get the initial value result.
    @Test
    public void testSetCarPropertyValuesAsync_errorSetResultAfterTargetInitValueResult()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(hvacHalSubscribeOption());
        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Returns the get initial value result.
        deliverOkayGetResult(getInvocationWrap.get(0));
        // Returns the set value result.
        deliverErrorSetResult(setInvocationWrap.get(0),
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);

        // Get init value result must not be passed to the client.
        verify(mSetAsyncPropertyResultCallback, never()).onGetValueResults(any());
        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        // After the result comes, we must unsubscribe the property.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));

        verifyNoPendingRequest();
    }

    // Test the callback is only invoked once even though the same result is returned multiple
    // times.
    @Test
    public void testSetCarPropertyValuesAsync_callbackOnlyCalledOncePerRequest()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(hvacHalSubscribeOption());
        deliverErrorSetResult(setInvocationWrap.get(0),
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);

        // The same result is returned again.
        deliverErrorSetResult(setInvocationWrap.get(0),
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);

        // We must only call callback once.
        verify(mSetAsyncPropertyResultCallback, times(1)).onSetValueResults(any());
        // After the result comes, we must unsubscribe the property.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));

        verifyNoPendingRequest();
    }

    // Test the case where the get init value request has error result. The result must be ignored.
    @Test
    public void testSetCarPropertyValuesAsync_errorInitValueResult_timeout()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 100,
                /* asyncRequestStartTime= */ 0);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(hvacHalSubscribeOption());
        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Returns the get initial value result, assume we failed to get initial values.
        deliverErrorGetResult(getInvocationWrap.get(0),
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));

        // Get init value result must not be passed to the client.
        verify(mSetAsyncPropertyResultCallback, never()).onGetValueResults(any());
        // Eventually the request should time out.
        verify(mSetAsyncPropertyResultCallback, timeout(1000)).onSetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_TIMEOUT);

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync_propertyUpdatedThroughEvent()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();
        List<HalServiceBase> serviceWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            serviceWrap.add(invocation.getArgument(0));
            return null;
        }).when(mVehicleHal).subscribeProperty(any(), anyList());
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        float testSampleRate = 20.0f;
        AsyncPropertyServiceRequest request2 = copyRequest(SET_SPEED_REQUEST_ID_2);
        request2.setUpdateRateHz(testSampleRate);

        mPropertyHalService.setCarPropertyValuesAsync(List.of(
                SET_HVAC_REQUEST_ID_1, request2),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Returns the get initial value result.
        deliverErrorGetResult(getInvocationWrap.get(0),
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(
                hvacHalSubscribeOption(), speedHalSubscribeOption(testSampleRate));

        // Notify the property is updated to the target value.
        assertThat(serviceWrap).hasSize(1);
        serviceWrap.get(0).onHalEvents(List.of(mPropValue));
        serviceWrap.get(0).onHalEvents(List.of(mPropValue2));

        verify(mSetAsyncPropertyResultCallback, times(2)).onSetValueResults(
                mAsyncResultCaptor.capture());
        for (GetSetValueResultList results : mAsyncResultCaptor.getAllValues()) {
            GetSetValueResult result = results.getList().get(0);
            assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                    .isEqualTo(STATUS_OK);
            assertThat(result.getUpdateTimestampNanos()).isEqualTo(TEST_UPDATE_TIMESTAMP_NANOS);
        }
        // After the result comes, we must unsubscribe the property.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));
        verify(mVehicleHal).unsubscribeProperty(any(), eq(PERF_VEHICLE_SPEED));

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync_propertyUpdatedThroughEvent_ignoreNonTargetEvent()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();
        List<HalServiceBase> serviceWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        // Because HVAC_TEMPERATURE_SET is ON_CHANGE property, the sample rate is 0.
        doAnswer((invocation) -> {
            serviceWrap.add(invocation.getArgument(0));
            return null;
        }).when(mVehicleHal).subscribeProperty(any(), anyList());
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Returns the get initial value result. This is not the target value.
        deliverOkayGetResult(getInvocationWrap.get(0), mPropValueBuilder.build(
                HVAC_TEMPERATURE_SET, /* areaId= */ 0, 16.0f));
        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(hvacHalSubscribeOption());

        // Generate a property update event with non-target value.
        serviceWrap.get(0).onHalEvents(List.of(mNonTargetPropValue));

        verify(mSetAsyncPropertyResultCallback, never()).onSetValueResults(any());

        // Notify the property is updated to the target value.
        serviceWrap.get(0).onHalEvents(List.of(mPropValue));

        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                .isEqualTo(STATUS_OK);
        assertThat(result.getUpdateTimestampNanos()).isEqualTo(TEST_UPDATE_TIMESTAMP_NANOS);
        // After the result comes, we must unsubscribe the property.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync_updateSubscriptionRate()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        AsyncPropertyServiceRequest request = copyRequest(SET_SPEED_REQUEST_ID_2);
        request.setUpdateRateHz(20.0f);
        mPropertyHalService.setCarPropertyValuesAsync(List.of(request),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(speedHalSubscribeOption(20f));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        mPropertyHalService.subscribeProperty(List.of(createCarSubscriptionOption(
                PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 40.0f,
                /* enableVur= */ true)));

        // Subscription rate has to be updated according to client subscription.
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(speedHalSubscribeOption(40f));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        // After client unsubscribe, revert back to the internal subscription rate.
        mPropertyHalService.unsubscribeProperty(PERF_VEHICLE_SPEED);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(speedHalSubscribeOption(20f));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        // New client subscription must overwrite the internal rate.
        mPropertyHalService.subscribeProperty(List.of(createCarSubscriptionOption(
                PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 50.0f,
                /* enableVur= */ true)));

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(speedHalSubscribeOption(50f));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        // Finish the async set request.
        deliverOkaySetResult(setInvocationWrap.get(0));
        deliverOkayGetResult(getInvocationWrap.get(0), mPropValue2);

        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        assertThat(
                mAsyncResultCaptor.getValue().getList().get(0)
                        .getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                .isEqualTo(STATUS_OK);

        // After the internal subscription is finished, the client subscription must be kept,
        // which causes no update to update rate.
        verify(mVehicleHal, never()).subscribeProperty(any(), any());

        mPropertyHalService.unsubscribeProperty(PERF_VEHICLE_SPEED);

        // After both client and internal unsubscription, the property must be unsubscribed.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(PERF_VEHICLE_SPEED));

        verifyNoPendingRequest();
    }

    @Test
    public void testMultipleSetCarPropertyValuesAsync_updateSubscriptionRateWithDifferentAreaIds()
            throws Exception {
        when(mMockCarPropertyConfig2.getAreaIds()).thenReturn(new int[]{0, 1, 2});
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        AsyncPropertyServiceRequest request1 = copyRequest(SET_VEHICLE_SPEED_AREA_ID_1_REQUEST);
        request1.setUpdateRateHz(20.0f);
        AsyncPropertyServiceRequest request2 = copyRequest(SET_VEHICLE_SPEED_AREA_ID_2_REQUEST);
        request2.setUpdateRateHz(21.0f);
        mPropertyHalService.setCarPropertyValuesAsync(List.of(request1, request2),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);


        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{1}, 20f,
                        /* enableVariableUpdateRate= */ true),
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{2}, 21f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);


        List<CarSubscription> carSubscriptions = new ArrayList<>();
        carSubscriptions.add(createCarSubscriptionOption(PERF_VEHICLE_SPEED, new int[] {1},
                /* updateRateHz= */ 40.0f, /* enableVur= */ true));
        mPropertyHalService.subscribeProperty(carSubscriptions);
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{1}, 40f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        carSubscriptions = new ArrayList<>();
        carSubscriptions.add(createCarSubscriptionOption(PERF_VEHICLE_SPEED, new int[] {2},
                /* updateRateHz= */ 41.0f, /* enableVur= */ true));
        mPropertyHalService.subscribeProperty(carSubscriptions);
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{2}, 41f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        mPropertyHalService.unsubscribeProperty(PERF_VEHICLE_SPEED);

        // After client unsubscribe, revert back to the internal subscription rate

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{1}, 20f,
                        /* enableVariableUpdateRate= */ true),
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{2}, 21f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);


        carSubscriptions = new ArrayList<>();
        carSubscriptions.add(createCarSubscriptionOption(PERF_VEHICLE_SPEED, new int[] {1},
                /* updateRateHz= */ 30.0f, /* enableVur= */ true));
        mPropertyHalService.subscribeProperty(carSubscriptions);
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{1}, 30f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        carSubscriptions = new ArrayList<>();
        carSubscriptions.add(createCarSubscriptionOption(PERF_VEHICLE_SPEED, new int[] {2},
                /* updateRateHz= */ 31.0f, /* enableVur= */ true));
        mPropertyHalService.subscribeProperty(carSubscriptions);
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{2}, 31f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        deliverOkaySetResult(setInvocationWrap.get(0));
        deliverOkayGetResult(getInvocationWrap.get(0), mPropValue2);

        // After the internal subscription is finished, the client subscription must be kept.
        // The internal subscription rate is lower than the client rate, so no rate change.
        verify(mVehicleHal, never()).subscribeProperty(any(), any());

        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        assertThat(mAsyncResultCaptor.getValue().getList().get(0).getCarPropertyErrorCodes()
                .getCarPropertyManagerErrorCode()).isEqualTo(STATUS_OK);
        assertThat(mAsyncResultCaptor.getValue().getList().get(1).getCarPropertyErrorCodes()
                .getCarPropertyManagerErrorCode()).isEqualTo(STATUS_OK);

        mPropertyHalService.unsubscribeProperty(PERF_VEHICLE_SPEED);

        // After both client and internal unsubscription, the property must be unsubscribed.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(PERF_VEHICLE_SPEED));

        verifyNoPendingRequest();
    }

    @Test
    public void testMultipleSetCarPropertyValuesAsync_overlappingSetAsync()
            throws Exception {
        when(mMockCarPropertyConfig2.getAreaIds()).thenReturn(new int[]{0, 1, 2});
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        AsyncPropertyServiceRequest request1 = copyRequest(SET_VEHICLE_SPEED_AREA_ID_1_REQUEST);
        request1.setUpdateRateHz(20.0f);
        AsyncPropertyServiceRequest request2 = copyRequest(SET_VEHICLE_SPEED_AREA_ID_2_REQUEST);
        request2.setUpdateRateHz(21.0f);
        mPropertyHalService.setCarPropertyValuesAsync(List.of(request1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);
        mPropertyHalService.setCarPropertyValuesAsync(List.of(request2),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(2);
        assertThat(getInvocationWrap).hasSize(2);

        verify(mVehicleHal, times(2)).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getAllValues().get(0)).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{1}, 20f,
                        /* enableVariableUpdateRate= */ true));
        assertThat(mListArgumentCaptor.getAllValues().get(1)).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{2}, 21f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        List<CarSubscription> carSubscriptions = new ArrayList<>();
        carSubscriptions.add(createCarSubscriptionOption(PERF_VEHICLE_SPEED, new int[] {1},
                /* updateRateHz= */ 40.0f, /* enableVur= */ true));
        carSubscriptions.add(createCarSubscriptionOption(PERF_VEHICLE_SPEED, new int[] {2},
                /* updateRateHz= */ 41.0f, /* enableVur= */ true));
        mPropertyHalService.subscribeProperty(carSubscriptions);

        // Subscription rate has to be updated according to client subscription.
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{1}, 40f,
                        /* enableVariableUpdateRate= */ true),
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{2}, 41f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);

        deliverOkaySetResult(setInvocationWrap.get(0));
        deliverOkayGetResult(getInvocationWrap.get(0), mPropValue2);

        // Unsubscribing to internal rate 20.0f and 21.0f does not change the current client rate
        // 40 and 41.
        verify(mVehicleHal, never()).subscribeProperty(any(), any());

        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        mPropertyHalService.unsubscribeProperty(PERF_VEHICLE_SPEED);
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(new HalSubscribeOptions(
                PERF_VEHICLE_SPEED, new int[]{2}, 21f, /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);

        deliverOkaySetResult(setInvocationWrap.get(1));
        deliverOkayGetResult(getInvocationWrap.get(1), mPropValue2);

        // Subscription rate has to be updated according to client subscription.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(PERF_VEHICLE_SPEED));

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync_withSubscriptionFromDifferentAreaIds()
            throws Exception {
        when(mMockCarPropertyConfig2.getAreaIds()).thenReturn(new int[]{0, 1});
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        AsyncPropertyServiceRequest request = copyRequest(SET_VEHICLE_SPEED_AREA_ID_1_REQUEST);
        request.setUpdateRateHz(20.0f);
        mPropertyHalService.setCarPropertyValuesAsync(List.of(request),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getAllValues().get(0)).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{1}, 20f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        mPropertyHalService.subscribeProperty(List.of(createCarSubscriptionOption(
                PERF_VEHICLE_SPEED, new int[]{0, 1}, /* updateRateHz= */ 40.0f,
                /* enableVur= */ true)));

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getAllValues().get(0)).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{0, 1}, 40f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        // After client unsubscribe, revert back to the internal subscription rate.
        mPropertyHalService.unsubscribeProperty(PERF_VEHICLE_SPEED);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getAllValues().get(0)).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{1}, 20f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        // New client subscription must overwrite the internal rate.
        List<CarSubscription> carSubscriptions = new ArrayList<>();
        carSubscriptions.add(createCarSubscriptionOption(PERF_VEHICLE_SPEED, new int[] {1},
                /* updateRateHz= */ 50.0f, /* enableVur= */ true));
        mPropertyHalService.subscribeProperty(carSubscriptions);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getAllValues().get(0)).containsExactly(
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{1}, 50f,
                        /* enableVariableUpdateRate= */ true));
        clearInvocations(mVehicleHal);
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);
        clearInvocations(mVehicleHal);

        // Finish the async set request.
        deliverOkaySetResult(setInvocationWrap.get(0));
        deliverOkayGetResult(getInvocationWrap.get(0), mPropValue2);

        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        assertThat(mAsyncResultCaptor.getValue().getList().get(0).getCarPropertyErrorCodes()
                .getCarPropertyManagerErrorCode()).isEqualTo(STATUS_OK);

        // After the internal subscription is finished, the client is still subscribed at 50hz
        // and no update rate change is required.
        verify(mVehicleHal, never()).subscribeProperty(any(), any());

        mPropertyHalService.unsubscribeProperty(PERF_VEHICLE_SPEED);

        // After both client and internal unsubscription, the property must be unsubscribed.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(PERF_VEHICLE_SPEED));

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync_defaultSubscriptionRate()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_SPEED_REQUEST_ID_2),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 10,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Default sample rate is set to max sample rate.
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(speedHalSubscribeOption(100f));

        // Finish the async set request.
        deliverOkaySetResult(setInvocationWrap.get(0));
        deliverOkayGetResult(getInvocationWrap.get(0), mPropValue2);
    }

    @Test
    public void testSetCarPropertyValuesAsync_defaultSubscriptionRateDifferentAreaIds() {
        when(mMockCarPropertyConfig2.getAreaIds()).thenReturn(new int[]{0, 1});
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(
                List.of(SET_VEHICLE_SPEED_AREA_ID_1_REQUEST, SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 10,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Default sample rate is set to max sample rate.
        float maxSampleRate = 100.0f;
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(
                hvacHalSubscribeOption(),
                new HalSubscribeOptions(PERF_VEHICLE_SPEED, new int[]{1}, maxSampleRate,
                        /* enableVariableUpdateRate= */ true));

        // Finish the async set request.
        deliverOkaySetResult(setInvocationWrap.get(0));
        deliverOkayGetResult(getInvocationWrap.get(0), mPropValue2);
    }

    @Test
    public void testSetCarPropertyValuesAsync_setUpdateRateHz()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.subscribeProperty(List.of(createCarSubscriptionOption(
                PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 22.0f,
                /* enableVur= */ true)));

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getAllValues().get(0)).containsExactly(
                speedHalSubscribeOption(22.0f));
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);
        clearInvocations(mVehicleHal);

        AsyncPropertyServiceRequest request1 = copyRequest(SET_SPEED_REQUEST_ID_2);
        request1.setUpdateRateHz(23.1f);
        AsyncPropertyServiceRequest request2 = copyRequest(SET_SPEED_REQUEST_ID_3);
        request2.setUpdateRateHz(23.2f);
        mPropertyHalService.setCarPropertyValuesAsync(List.of(request1, request2),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getAllValues().get(0)).containsExactly(
                speedHalSubscribeOption(23.2f));
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);
        clearInvocations(mVehicleHal);

        // Finish the async set request.
        deliverOkaySetResult(setInvocationWrap.get(0));
        deliverOkayGetResult(getInvocationWrap.get(0), mPropValue2);

        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        // Both request must succeed.
        assertThat(mAsyncResultCaptor.getValue().getList()).hasSize(2);
        assertThat(mAsyncResultCaptor.getValue().getList().get(0).getCarPropertyErrorCodes()
                .getCarPropertyManagerErrorCode()).isEqualTo(STATUS_OK);
        assertThat(mAsyncResultCaptor.getValue().getList().get(1).getCarPropertyErrorCodes()
                .getCarPropertyManagerErrorCode()).isEqualTo(STATUS_OK);

        // After internal subscription complete, the client subscription rate must be kept.
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(speedHalSubscribeOption(22.0f));

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync_cancelledBeforePropertyUpdateEvent()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();
        List<HalServiceBase> serviceWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        // Because HVAC_TEMPERATURE_SET is ON_CHANGE property, the sample rate is 0.
        doAnswer((invocation) -> {
            serviceWrap.add(invocation.getArgument(0));
            return null;
        }).when(mVehicleHal).subscribeProperty(any(), anyList());
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(hvacHalSubscribeOption());

        // Cancel the ongoing request.
        mPropertyHalService.cancelRequests(new int[]{REQUEST_ID_1});

        // Notify the property is updated to the target value after the request is cancelled.
        serviceWrap.get(0).onHalEvents(List.of(mPropValue));

        verify(mSetAsyncPropertyResultCallback, never()).onSetValueResults(any());
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));

        verifyNoPendingRequest();
    }

    @Test
    public void testSetCarPropertyValuesAsync_timeoutBeforePropertyUpdateEvent()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();
        List<HalServiceBase> serviceWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        // Because HVAC_TEMPERATURE_SET is ON_CHANGE property, the sample rate is 0.
        doAnswer((invocation) -> {
            serviceWrap.add(invocation.getArgument(0));
            return null;
        }).when(mVehicleHal).subscribeProperty(any(), anyList());
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(hvacHalSubscribeOption());

        List<AsyncGetSetRequest> setAsyncRequests = setInvocationWrap.get(0).getArgument(0);
        VehicleStubCallbackInterface callback = setInvocationWrap.get(0).getArgument(1);
        callback.onRequestsTimeout(
                List.of(setAsyncRequests.get(0).getServiceRequestId()));

        // Notify the property is updated to the target value after the request is cancelled.
        serviceWrap.get(0).onHalEvents(List.of(mPropValue));

        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        assertThat(mAsyncResultCaptor.getValue().getList()).hasSize(1);
        assertThat(
                mAsyncResultCaptor.getValue().getList().get(0).getCarPropertyErrorCodes()
                        .getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_TIMEOUT);
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));

        verifyNoPendingRequest();
    }

    // If we receive errors for the [propId, areaId] we are setting via onPropertySetError, we must
    // fail the pending request.
    @Test
    public void testSetCarPropertyValuesAsync_onPropertySetError() throws RemoteException {
        when(mMockCarPropertyConfig1.getAreaIds()).thenReturn(new int[]{1});
        List<HalServiceBase> serviceWrap = new ArrayList<>();
        AsyncPropertyServiceRequest setPropertyRequest =
                new AsyncPropertyServiceRequest(1, HVAC_TEMPERATURE_SET, /* areaId= */ 1);

        doNothing().when(mVehicleHal).setAsync(anyList(),
                any(VehicleStubCallbackInterface.class));
        doNothing().when(mVehicleHal).getAsync(anyList(),
                any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            serviceWrap.add(invocation.getArgument(0));
            return null;
        }).when(mVehicleHal).subscribeProperty(any(), anyList());
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(setPropertyRequest),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        ArrayList<VehiclePropError> vehiclePropErrors = new ArrayList<>();
        VehiclePropError error1 = new VehiclePropError();
        error1.propId = HVAC_TEMPERATURE_SET;
        error1.areaId = 1;
        error1.errorCode = STATUS_NOT_AVAILABLE | (0x1234 << 16);
        // Error 2 has the wrong area ID and must be ignored.
        VehiclePropError error2 = new VehiclePropError();
        error2.propId = HVAC_TEMPERATURE_SET;
        error2.areaId = 2;
        error2.errorCode = STATUS_INTERNAL_ERROR;
        vehiclePropErrors.add(error1);
        vehiclePropErrors.add(error2);
        assertThat(serviceWrap).hasSize(1);
        serviceWrap.get(0).onPropertySetError(vehiclePropErrors);

        verify(mSetAsyncPropertyResultCallback).onSetValueResults(mAsyncResultCaptor.capture());
        assertThat(mAsyncResultCaptor.getValue().getList()).hasSize(1);
        assertThat(
                mAsyncResultCaptor.getValue().getList().get(0).getCarPropertyErrorCodes()
                        .getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        assertThat(
                mAsyncResultCaptor.getValue().getList().get(0).getCarPropertyErrorCodes()
                        .getVendorErrorCode())
                .isEqualTo(0x1234);
        assertThat(mAsyncResultCaptor.getValue().getList().get(0).getCarPropertyErrorCodes()
                        .getSystemErrorCode())
                .isEqualTo(STATUS_NOT_AVAILABLE);
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));

        verifyNoPendingRequest();
    }

    @Test
    public void testOnSetAsyncResults_RetryAndTimeout() throws RemoteException {
        doAnswer((invocation) -> {
            // For every request, we return retry result.
            return deliverTryAgainSetResult(invocation);
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 10,
                /* asyncRequestStartTime= */ 0);

        verify(mSetAsyncPropertyResultCallback, timeout(1000)).onSetValueResults(
                mAsyncResultCaptor.capture());
        assertThat(mAsyncResultCaptor.getValue().getList().get(0).getRequestId())
                .isEqualTo(REQUEST_ID_1);
        assertThat(
                mAsyncResultCaptor.getValue().getList().get(0).getCarPropertyErrorCodes()
                        .getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_TIMEOUT);

        verifyNoPendingRequest();
    }

    @Test
    public void testOnSetAsyncResults_TimeoutFromVehicleStub() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            List requests = (List) args[0];
            AsyncGetSetRequest request = (AsyncGetSetRequest) requests.get(0);
            VehicleStubCallbackInterface callback = (VehicleStubCallbackInterface) args[1];
            // Simulate the request has already timed-out.
            callback.onRequestsTimeout(List.of(request.getServiceRequestId()));
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));

        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mSetAsyncPropertyResultCallback, timeout(1000)).onSetValueResults(
                mAsyncResultCaptor.capture());
        assertThat(mAsyncResultCaptor.getValue().getList().get(0).getRequestId())
                .isEqualTo(REQUEST_ID_1);
        assertThat(mAsyncResultCaptor.getValue().getList().get(0).getCarPropertyErrorCodes()
                        .getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_TIMEOUT);

        verifyNoPendingRequest();
    }

    @Test
    public void testOnSetAsyncResults_errorResult() throws RemoteException {
        doAnswer((invocation) -> {
            return deliverErrorSetResult(invocation, RECEIVED_REQUEST_ID_1,
                    CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mSetAsyncPropertyResultCallback, timeout(1000)).onSetValueResults(
                mAsyncResultCaptor.capture());
        GetSetValueResult result = mAsyncResultCaptor.getValue().getList().get(0);
        assertThat(result.getRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(result.getCarPropertyErrorCodes().getCarPropertyManagerErrorCode())
                .isEqualTo(CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
        assertThat(result.getCarPropertyValue()).isEqualTo(null);

        verifyNoPendingRequest();
    }

    @Test
    public void setProperty_handlesHalAndMgrPropIdMismatch() {
        HalPropConfig mockPropConfig = mock(HalPropConfig.class);
        CarPropertyValue mockCarPropertyValue = mock(CarPropertyValue.class);
        when(mockCarPropertyValue.getPropertyId()).thenReturn(
                VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS);
        when(mockCarPropertyValue.getAreaId()).thenReturn(0);
        when(mockCarPropertyValue.getValue()).thenReturn(1.0f);
        when(mockPropConfig.getPropId()).thenReturn(VehicleProperty.VEHICLE_SPEED_DISPLAY_UNITS);
        mPropertyHalService.takeProperties(List.of(mockPropConfig));

        mPropertyHalService.setProperty(mockCarPropertyValue);

        HalPropValue value = mPropValueBuilder.build(
                VehicleProperty.VEHICLE_SPEED_DISPLAY_UNITS, /* areaId= */ 0, /* value= */ 1.0f);
        verify(mVehicleHal).set(value);

        verifyNoPendingRequest();
    }

    @Test
    public void testCancelRequests_getCarPropertyValuesAsync() throws Exception {
        doReturn(mGetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();
        List<InvocationOnMock> invocationWrap = new ArrayList<>();
        doAnswer((invocation) -> {
            invocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        mPropertyHalService.getCarPropertyValuesAsync(List.of(
                GET_PROPERTY_SERVICE_REQUEST_1, GET_PROPERTY_SERVICE_REQUEST_2),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        assertThat(invocationWrap).hasSize(1);

        // Cancel the first request.
        mPropertyHalService.cancelRequests(new int[]{REQUEST_ID_1});

        verify(mVehicleHal).cancelRequests(List.of(0));

        // Deliver the results.
        deliverOkayGetResult(invocationWrap.get(0));

        // We should only get the result for request 2.
        verify(mGetAsyncPropertyResultCallback, timeout(1000)).onGetValueResults(
                mAsyncResultCaptor.capture());
        assertThat(mAsyncResultCaptor.getValue().getList().get(0).getRequestId())
                .isEqualTo(REQUEST_ID_2);

        verifyNoPendingRequest();
    }

    @Test
    public void testCancelRequests_setCarPropertyValuesAsync() throws Exception {
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        mPropertyHalService.setCarPropertyValuesAsync(List.of(
                SET_HVAC_REQUEST_ID_1, SET_SPEED_REQUEST_ID_2),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        assertThat(setInvocationWrap).hasSize(1);
        assertThat(getInvocationWrap).hasSize(1);

        // Cancel the first request.
        mPropertyHalService.cancelRequests(new int[]{REQUEST_ID_1});

        // Note that because both the async set and the get init value request has the same manager
        // request ID, so they will all be cancelled.
        verify(mVehicleHal, timeout(1000).times(2)).cancelRequests(any());

        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));
        // Returns the get initial value result.
        deliverOkayGetResult(getInvocationWrap.get(0));

        // We should only get the result for request 2.
        verify(mSetAsyncPropertyResultCallback, timeout(1000)).onSetValueResults(
                mAsyncResultCaptor.capture());
        assertThat(mAsyncResultCaptor.getValue().getList().get(0).getRequestId())
                .isEqualTo(REQUEST_ID_2);

        verifyNoPendingRequest();
    }

    @Test
    public void testCancelRequests_noPendingRequests() {
        mPropertyHalService.cancelRequests(new int[]{0});

        verify(mVehicleHal, never()).cancelRequests(any());

        verifyNoPendingRequest();
    }

    // Test that when the client binder died, all pending async requests must be cleared.
    @Test
    public void testOnBinderDied() throws Exception {
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();
        // We return the same binder object for both set and get here.
        doReturn(mSetAsyncPropertyResultBinder).when(mGetAsyncPropertyResultCallback).asBinder();
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));

        mPropertyHalService.getCarPropertyValuesAsync(List.of(GET_PROPERTY_SERVICE_REQUEST_1),
                mGetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);
        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_SPEED_REQUEST_ID_2),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mSetAsyncPropertyResultBinder).linkToDeath(mDeathRecipientCaptor.capture(),
                anyInt());

        assertThat(setInvocationWrap).hasSize(1);
        // One is for async get, one is for initial value.
        assertThat(getInvocationWrap).hasSize(2);

        // Simulate client binder died.
        mDeathRecipientCaptor.getValue().binderDied();

        verify(mSetAsyncPropertyResultBinder).unlinkToDeath(any(), anyInt());

        // All async pending requests must be cleared.
        verifyNoPendingRequest();
    }

    @Test
    public void testGetPropertySync() throws Exception {
        HalPropValue value = mPropValueBuilder.build(INT32_PROP, /* areaId= */ 0, PROPERTY_VALUE);
        when(mVehicleHal.get(INT32_PROP, /* areaId= */ 0)).thenReturn(value);

        CarPropertyValue carPropValue = mPropertyHalService.getProperty(
                INT32_PROP, /* areaId= */ 0);

        assertThat(carPropValue.getValue()).isEqualTo(PROPERTY_VALUE);
    }

    @Test
    public void testGetPropertySyncWithCache() throws Exception {
        HalPropValue value = mPropValueBuilder.build(INT32_PROP, /* areaId= */ 0, PROPERTY_VALUE);
        when(mVehicleHal.get(INT32_PROP, /* areaId= */ 0)).thenReturn(value);
        mPropertyHalService.getProperty(INT32_PROP, /* areaId= */ 0);
        reset(mVehicleHal);

        CarPropertyValue carPropValue = mPropertyHalService.getProperty(
                INT32_PROP, /* areaId= */ 0);

        assertWithMessage("CarPropertyValue cached value").that(carPropValue.getValue())
                .isEqualTo(PROPERTY_VALUE);
        verify(mVehicleHal, never()).get(anyInt(), anyInt());
    }

    @Test
    public void testGetPropertySyncErrorPropStatus() throws Exception {
        HalPropValue value = mPropValueBuilder.build(
                AidlVehiclePropValueBuilder.newBuilder(INT32_PROP)
                        .setStatus(VehiclePropertyStatus.ERROR).addIntValues(0).build());
        when(mVehicleHal.get(INT32_PROP, /* areaId= */ 0)).thenReturn(value);

        assertThat(mPropertyHalService.getProperty(INT32_PROP, /*areaId=*/0)).isEqualTo(
                new CarPropertyValue<>(INT32_PROP, /*areaId=*/0,
                        CarPropertyValue.STATUS_ERROR, /*timestampNanos=*/0, Integer.valueOf(0)));
    }

    @Test
    public void testGetPropertySyncUnavailablePropStatus() throws Exception {
        HalPropValue value = mPropValueBuilder.build(
                AidlVehiclePropValueBuilder.newBuilder(INT32_PROP)
                        .setStatus(VehiclePropertyStatus.UNAVAILABLE).addIntValues(0).build());
        when(mVehicleHal.get(INT32_PROP, /* areaId= */ 0)).thenReturn(value);

        assertThat(mPropertyHalService.getProperty(INT32_PROP, /*areaId=*/0)).isEqualTo(
                new CarPropertyValue<>(INT32_PROP, /*areaId=*/0,
                        CarPropertyValue.STATUS_UNAVAILABLE, /*timestampNanos=*/0,
                        Integer.valueOf(0)));
    }

    @Test
    public void testGetPropertySyncInvalidProp() throws Exception {
        // This property has no valid int array element.
        HalPropValue value = mPropValueBuilder.build(
                AidlVehiclePropValueBuilder.newBuilder(INT32_PROP).build());
        when(mVehicleHal.get(INT32_PROP, /* areaId= */ 0)).thenReturn(value);

        assertThat(mPropertyHalService.getProperty(INT32_PROP, /*areaId=*/0)).isEqualTo(
                new CarPropertyValue<>(INT32_PROP, /*areaId=*/0,
                        CarPropertyValue.STATUS_ERROR, /*timestampNanos=*/0, Integer.valueOf(0)));
    }

    @Test
    public void testOnPropertySetError() throws Exception {
        ArrayList<VehiclePropError> vehiclePropErrors = new ArrayList<>();
        VehiclePropError error1 = new VehiclePropError();
        error1.propId = HVAC_TEMPERATURE_SET;
        error1.areaId = 1;
        error1.errorCode = STATUS_NOT_AVAILABLE | (0x1234 << 16);
        VehiclePropError error2 = new VehiclePropError();
        error2.propId = PERF_VEHICLE_SPEED;
        error2.areaId = 0;
        error2.errorCode = STATUS_INTERNAL_ERROR;
        vehiclePropErrors.add(error1);
        vehiclePropErrors.add(error2);

        mPropertyHalService.setPropertyHalListener(mPropertyHalListener);
        mPropertyHalService.onPropertySetError(vehiclePropErrors);

        verify(mPropertyHalListener).onPropertySetError(HVAC_TEMPERATURE_SET, 1,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_PROPERTY_NOT_AVAILABLE);
        verify(mPropertyHalListener).onPropertySetError(PERF_VEHICLE_SPEED, 0,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
    }

    @Test
    public void testNoCustomizeVendorPermission() throws Exception {
        HalPropConfig vendor1Config = mock(HalPropConfig.class);
        when(vendor1Config.getPropId()).thenReturn(VENDOR_PROPERTY_1);
        when(mVehicleHal.getPropConfig(SUPPORT_CUSTOMIZE_VENDOR_PERMISSION)).thenReturn(null);

        mPropertyHalService.takeProperties(List.of(vendor1Config));

        // By default we require PERMISSION_VENDOR_EXTENSION for getting/setting vendor props.
        assertThat(mPropertyHalService.getReadPermission(VENDOR_PROPERTY_1))
                .isEqualTo(PERMISSION_VENDOR_EXTENSION);
        assertThat(mPropertyHalService.getWritePermission(VENDOR_PROPERTY_1))
                .isEqualTo(PERMISSION_VENDOR_EXTENSION);
    }

    @Test
    public void testCustomizeVendorPermission() throws Exception {
        HalPropConfig mockVendorPermConfig = mock(HalPropConfig.class);
        // Use the same test config we used in PropertyHalServiceTest.
        when(mockVendorPermConfig.getConfigArray()).thenReturn(new int[]{
                VENDOR_PROPERTY_1,
                VehicleVendorPermission.PERMISSION_DEFAULT,
                VehicleVendorPermission.PERMISSION_NOT_ACCESSIBLE,
                VENDOR_PROPERTY_2,
                VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_ENGINE,
                VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_ENGINE,
                VENDOR_PROPERTY_3,
                VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_INFO,
                VehicleVendorPermission.PERMISSION_DEFAULT
        });
        when(mVehicleHal.getPropConfig(SUPPORT_CUSTOMIZE_VENDOR_PERMISSION)).thenReturn(
                mockVendorPermConfig);
        HalPropConfig vendor1Config = mock(HalPropConfig.class);
        when(vendor1Config.getPropId()).thenReturn(VENDOR_PROPERTY_1);
        HalPropConfig vendor2Config = mock(HalPropConfig.class);
        when(vendor2Config.getPropId()).thenReturn(VENDOR_PROPERTY_2);
        HalPropConfig vendor3Config = mock(HalPropConfig.class);
        when(vendor3Config.getPropId()).thenReturn(VENDOR_PROPERTY_3);

        mPropertyHalService.takeProperties(List.of(vendor1Config, vendor2Config,
                vendor3Config));

        assertThat(mPropertyHalService.getReadPermission(VENDOR_PROPERTY_1))
                .isEqualTo(PERMISSION_VENDOR_EXTENSION);
        assertThat(mPropertyHalService.getWritePermission(VENDOR_PROPERTY_1)).isNull();
        assertThat(mPropertyHalService.getReadPermission(VENDOR_PROPERTY_2))
                .isEqualTo(PERMISSION_GET_CAR_VENDOR_CATEGORY_ENGINE);
        assertThat(mPropertyHalService.getWritePermission(VENDOR_PROPERTY_2))
                .isEqualTo(PERMISSION_SET_CAR_VENDOR_CATEGORY_ENGINE);
        assertThat(mPropertyHalService.getReadPermission(VENDOR_PROPERTY_3))
                .isEqualTo(PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO);
        assertThat(mPropertyHalService.getWritePermission(VENDOR_PROPERTY_3))
                .isEqualTo(PERMISSION_VENDOR_EXTENSION);
    }

    @Test
    public void testHalSubscribeOptions_equals() {
        HalSubscribeOptions options1 = new HalSubscribeOptions(/* propId= */ 5,
                /* areaId= */ new int[]{0, 3}, /* updateRateHz= */ 53f);
        HalSubscribeOptions options2 = new HalSubscribeOptions(/* propId= */ 5,
                /* areaId= */ new int[]{0, 3}, /* updateRateHz= */ 53f);

        assertWithMessage("Equal hal subscribe options")
                .that(options1.equals(options2)).isTrue();
    }

    @Test
    public void testHalSubscribeOptions_notEquals() {
        HalSubscribeOptions options1 = new HalSubscribeOptions(/* propId= */ 5,
                /* areaId= */ new int[]{0, 3}, /* updateRateHz= */ 55f,
                /* enableVariableUpdateRate= */ true);
        HalSubscribeOptions options2 = new HalSubscribeOptions(/* propId= */ 5,
                /* areaId= */ new int[]{0, 3}, /* updateRateHz= */ 53f,
                /* enableVariableUpdateRate= */ true);

        assertWithMessage("Non-equal hal subscribe options")
                .that(options1.equals(options2)).isFalse();
    }

    @Test
    public void testHalSubscribeOptions_hashcode() {
        HalSubscribeOptions options1 = new HalSubscribeOptions(/* propId= */ 5,
                /* areaId= */ new int[]{0, 3}, /* updateRateHz= */ 53f,
                /* enableVariableUpdateRate= */ true);
        HalSubscribeOptions options2 = new HalSubscribeOptions(/* propId= */ 5,
                /* areaId= */ new int[]{0, 3}, /* updateRateHz= */ 53f,
                /* enableVariableUpdateRate= */ true);

        assertWithMessage("Hashcode hal subscribe options")
                .that(options1.hashCode()).isEqualTo(options2.hashCode());
    }

    /**
     * This test is for verifying when we need to update sample rate for multiple properties, both
     * subscribeProperty and unsubscribeProperty may happen.
     */
    @Test
    public void testOnHalEvnts_unsubscribeAndUpdateSampleRates()
            throws Exception {
        List<InvocationOnMock> setInvocationWrap = new ArrayList<>();
        List<InvocationOnMock> getInvocationWrap = new ArrayList<>();
        List<HalServiceBase> serviceWrap = new ArrayList<>();

        doAnswer((invocation) -> {
            setInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).setAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            getInvocationWrap.add(invocation);
            return null;
        }).when(mVehicleHal).getAsync(anyList(), any(VehicleStubCallbackInterface.class));
        doAnswer((invocation) -> {
            serviceWrap.add(invocation.getArgument(0));
            return null;
        }).when(mVehicleHal).subscribeProperty(any(), anyList());
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();

        // Subscribe to speed at 40hz.
        mPropertyHalService.subscribeProperty(List.of(
                createCarSubscriptionOption(
                        PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 40.0f,
                        /* enableVur= */ true)));
        // Issues set async request, which should cause speed to be subscribed at 100hz.
        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_HVAC_REQUEST_ID_1),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);
        // Issues set async request, which should cause hvac to be subscribed at 0hz.
        mPropertyHalService.setCarPropertyValuesAsync(List.of(SET_SPEED_REQUEST_ID_2),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        // Returns the set value result.
        deliverOkaySetResult(setInvocationWrap.get(0));
        deliverOkaySetResult(setInvocationWrap.get(1));

        verify(mVehicleHal, times(3)).subscribeProperty(any(), mListArgumentCaptor.capture());
        // This is caused by subscribeProperty.
        assertThat(mListArgumentCaptor.getAllValues().get(0)).containsExactly(
                speedHalSubscribeOption(40.0f));
        // This is caused by setCarPropertyValuesAsync.
        assertThat(mListArgumentCaptor.getAllValues().get(1)).containsExactly(
                hvacHalSubscribeOption());
        // This is caused by setCarPropertyValuesAsync.
        assertThat(mListArgumentCaptor.getAllValues().get(2)).containsExactly(
                speedHalSubscribeOption(100.0f));

        clearInvocations(mVehicleHal);

        // Send change value events for set requests.
        serviceWrap.get(0).onHalEvents(List.of(mPropValue, mPropValue2));

        // We no longer need to subscribe to temp.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(HVAC_TEMPERATURE_SET));
        // Speed should be subscribed at 40hz.
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getAllValues().get(3)).containsExactly(
                speedHalSubscribeOption(40.0f));

        verifyNoPendingRequest();
    }

    @Test
    public void testSubscribeProperty_exceptionFromVhal() throws Exception {
        doThrow(new ServiceSpecificException(0)).when(mVehicleHal).subscribeProperty(
                any(), anyList());

        assertThrows(ServiceSpecificException.class, () ->
                mPropertyHalService.subscribeProperty(List.of(
                        createCarSubscriptionOption(
                                PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 40.0f,
                                /* enableVur= */ true))));
    }

    /**
     * Tests that if we receive exception from underlying layer, client must be able to retry the
     * operation and causes a retry to VHAL. If the error goes away in VHAL, the retry must succeed.
     */
    @Test
    public void testSubscribeProperty_exceptionFromVhal_retryFixed() throws Exception {
        doThrow(new ServiceSpecificException(0)).when(mVehicleHal).subscribeProperty(
                any(), anyList());

        assertThrows(ServiceSpecificException.class, () ->
                mPropertyHalService.subscribeProperty(List.of(
                        createCarSubscriptionOption(
                                PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 40.0f,
                                /* enableVur= */ true))));

        clearInvocations(mVehicleHal);
        // Simulate the error has been fixed.
        doNothing().when(mVehicleHal).subscribeProperty(any(), anyList());

        // Retry the operation.
        mPropertyHalService.subscribeProperty(List.of(
                createCarSubscriptionOption(
                        PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 40.0f,
                        /* enableVur= */ true)));

        // The retry request must go to VehicleHal.
        verify(mVehicleHal).subscribeProperty(any(), anyList());

        mPropertyHalService.unsubscribeProperty(PERF_VEHICLE_SPEED);

        verify(mVehicleHal).unsubscribeProperty(any(), eq(PERF_VEHICLE_SPEED));

        verifyNoPendingRequest();
    }

    @Test
    public void testSubscribeProperty_enableVur() throws Exception {
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        mPropertyHalService.subscribeProperty(List.of(createCarSubscriptionOption(
                PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 40.0f,
                /* enableVur= */ true)));

        // Subscription rate has to be updated according to client subscription.
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(speedHalSubscribeOption(40f));
    }

    @Test
    public void testSubscribeProperty_disablesVur() throws Exception {
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        mPropertyHalService.subscribeProperty(List.of(createCarSubscriptionOption(
                PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 40.0f,
                /* enableVur= */ false)));

        // Subscription rate has to be updated according to client subscription.
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(new HalSubscribeOptions(
                PERF_VEHICLE_SPEED, new int[]{0}, 40.0f, /* enableVariableUpdateRate= */ false));
    }

    @Test
    public void testSubscribeProperty_withResolution() throws Exception {
        mListArgumentCaptor = ArgumentCaptor.forClass(List.class);

        mPropertyHalService.subscribeProperty(List.of(createCarSubscriptionOption(
                PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 40.0f,
                /* enableVur= */ false, /* resolution */ 1.0f)));

        // Subscription rate has to be updated according to client subscription.
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(new HalSubscribeOptions(
                PERF_VEHICLE_SPEED, new int[]{0}, 40.0f, /* enableVariableUpdateRate= */ false,
                /* resolution */ 1.0f));
    }

    @Test
    public void testSubscribeProperty_setAsync_clientDisablesVur() throws Exception {
        doReturn(mSetAsyncPropertyResultBinder).when(mSetAsyncPropertyResultCallback).asBinder();
        AsyncPropertyServiceRequest request = copyRequest(SET_SPEED_REQUEST_ID_2);
        request.setUpdateRateHz(20.0f);

        mPropertyHalService.setCarPropertyValuesAsync(List.of(request),
                mSetAsyncPropertyResultCallback, /* timeoutInMs= */ 1000,
                /* asyncRequestStartTime= */ 0);

        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(speedHalSubscribeOption(20f));
        clearInvocations(mVehicleHal);

        // Client disables Vur.
        mPropertyHalService.subscribeProperty(List.of(createCarSubscriptionOption(
                PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 10.0f,
                /* enableVur= */ false)));

        // Vur must be turned off.
        verify(mVehicleHal).subscribeProperty(any(), mListArgumentCaptor.capture());
        assertThat(mListArgumentCaptor.getValue()).containsExactly(new HalSubscribeOptions(
                PERF_VEHICLE_SPEED, new int[]{0}, 20.0f, /* enableVariableUpdateRate= */ false));

        mPropertyHalService.cancelRequests(new int[]{REQUEST_ID_2});
    }

    @Test
    public void testUnubscribeProperty_exceptionFromVhal() throws Exception {
        mPropertyHalService.subscribeProperty(List.of(
                createCarSubscriptionOption(
                        PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 40.0f,
                        /* enableVur= */ true)));
        doThrow(new ServiceSpecificException(0)).when(mVehicleHal).unsubscribeProperty(
                any(), anyInt());

        assertThrows(ServiceSpecificException.class, () ->
                mPropertyHalService.unsubscribeProperty(PERF_VEHICLE_SPEED));
    }

    /**
     * Tests that if we receive exception from underlying layer, client must be able to retry the
     * operation and causes a retry to VHAL. If the error goes away in VHAL, the retry must succeed.
     */
    @Test
    public void tesUnsubscribeProperty_exceptionFromVhal_retryFixed() throws Exception {
        mPropertyHalService.subscribeProperty(List.of(
                createCarSubscriptionOption(
                        PERF_VEHICLE_SPEED, new int[]{0}, /* updateRateHz= */ 40.0f,
                        /* enableVur= */ true)));
        doThrow(new ServiceSpecificException(0)).when(mVehicleHal).unsubscribeProperty(
                any(), anyInt());

        assertThrows(ServiceSpecificException.class, () ->
                mPropertyHalService.unsubscribeProperty(PERF_VEHICLE_SPEED));

        // Simulate the error has been fixed.
        clearInvocations(mVehicleHal);
        doNothing().when(mVehicleHal).unsubscribeProperty(any(), anyInt());

        mPropertyHalService.unsubscribeProperty(PERF_VEHICLE_SPEED);

        // The retry request must go to VehicleHal.
        verify(mVehicleHal).unsubscribeProperty(any(), eq(PERF_VEHICLE_SPEED));
    }

    /** Creates a {@code CarSubscription} with Vur off. */
    @VisibleForTesting
    public static CarSubscription createCarSubscriptionOption(int propertyId,
            int[] areaId, float updateRateHz) {
        return createCarSubscriptionOption(propertyId, areaId, updateRateHz,
                /* enableVur= */ false, /*resolution*/ 0.0f);
    }

    /** Creates a {@code CarSubscription}. */
    @VisibleForTesting
    public static CarSubscription createCarSubscriptionOption(int propertyId,
            int[] areaId, float updateRateHz, boolean enableVur) {
        return createCarSubscriptionOption(propertyId, areaId, updateRateHz,
                enableVur, /*resolution*/ 0.0f);
    }

    /** Creates a {@code CarSubscription}. */
    @VisibleForTesting
    public static CarSubscription createCarSubscriptionOption(int propertyId,
            int[] areaId, float updateRateHz, boolean enableVur, float resolution) {
        CarSubscription options = new CarSubscription();
        options.propertyId = propertyId;
        options.areaIds = areaId;
        options.updateRateHz = updateRateHz;
        options.enableVariableUpdateRate = enableVur;
        options.resolution = resolution;
        return options;
    }

    private static HalSubscribeOptions hvacHalSubscribeOption() {
        return new HalSubscribeOptions(HVAC_TEMPERATURE_SET, new int[]{0}, 0.0f,
                /* enableVariableUpdateRate= */ false);
    }

    private static HalSubscribeOptions speedHalSubscribeOption(float speed) {
        return new HalSubscribeOptions(
                PERF_VEHICLE_SPEED, new int[]{0}, speed, /* enableVariableUpdateRate= */ true);
    }
}
