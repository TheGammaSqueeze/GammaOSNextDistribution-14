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

import static com.android.car.internal.property.CarPropertyErrorCodes.STATUS_OK;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.property.CarPropertyManager;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.IVehicle.getCallback;
import android.hardware.automotive.vehicle.V2_0.IVehicle.getPropConfigsCallback;
import android.hardware.automotive.vehicle.V2_0.IVehicleCallback;
import android.hardware.automotive.vehicle.V2_0.StatusCode;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyStatus;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.SparseArray;

import com.android.car.VehicleStub.AsyncGetSetRequest;
import com.android.car.hal.HalPropConfig;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.HalPropValueBuilder;
import com.android.car.hal.HidlHalPropConfig;
import com.android.car.hal.VehicleHalCallback;
import com.android.car.internal.property.CarPropertyErrorCodes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public final class HidlVehicleStubUnitTest {

    private static final int TEST_PROP = 1;
    private static final int TEST_ACCESS = 2;
    private static final float TEST_SAMPLE_RATE = 3.0f;
    private static final int TEST_VALUE = 3;
    private static final int TEST_AREA = 4;
    private static final int TEST_STATUS = 5;

    private static final int VHAL_PROP_SUPPORTED_PROPERTY_IDS = 0x11410F48;

    private static final HalPropValue TEST_PROP_VALUE;
    private static final VehiclePropValue TEST_VHAL_VEHICLE_PROP_VALUE;

    static {
        HalPropValueBuilder builder = new HalPropValueBuilder(/* isAidl= */ false);
        TEST_PROP_VALUE = builder.build(TEST_PROP, /* areaId= */ 0, TEST_VALUE);

        TEST_VHAL_VEHICLE_PROP_VALUE = new VehiclePropValue();
        TEST_VHAL_VEHICLE_PROP_VALUE.prop = TEST_PROP;
        TEST_VHAL_VEHICLE_PROP_VALUE.value.int32Values.add(TEST_VALUE);
    }

    @Mock
    private IVehicle mHidlVehicle;
    @Mock
    private VehicleStub.VehicleStubCallbackInterface mAsyncCallback;

    private VehicleStub mHidlVehicleStub;

    private AsyncGetSetRequest defaultVehicleStubAsyncRequest(HalPropValue value) {
        return new AsyncGetSetRequest(/* serviceRequestId=*/ 0, value, /* timeoutInMs= */ 1000);
    }

    @Before
    public void setUp() {
        mHidlVehicleStub = new HidlVehicleStub(mHidlVehicle);

        assertThat(mHidlVehicleStub.isValid()).isTrue();
    }

    @Test
    public void testGetInterfaceDescriptorHidl() throws Exception {
        mHidlVehicleStub.getInterfaceDescriptor();

        verify(mHidlVehicle).interfaceDescriptor();
    }

    @Test
    public void testLinkToDeathHidl() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);

        mHidlVehicleStub.linkToDeath(recipient);

        verify(mHidlVehicle).linkToDeath(recipient, 0);
    }

    @Test
    public void testUnlinkToDeathHidl() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);

        mHidlVehicleStub.unlinkToDeath(recipient);

        verify(mHidlVehicle).unlinkToDeath(recipient);
    }

    @Test
    public void testUnlinkToDeathRemoteException() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);
        doThrow(new RemoteException()).when(mHidlVehicle).unlinkToDeath(recipient);

        mHidlVehicleStub.unlinkToDeath(recipient);
    }

    @Test
    public void testGetAllPropConfigsHidl() throws Exception {
        ArrayList<VehiclePropConfig> hidlConfigs = new ArrayList<VehiclePropConfig>();
        VehiclePropConfig hidlConfig = new VehiclePropConfig();
        hidlConfig.prop = TEST_PROP;
        hidlConfig.access = TEST_ACCESS;
        hidlConfigs.add(hidlConfig);

        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            callback.onValues(StatusCode.INVALID_ARG, /* configs = */ null);
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());
        when(mHidlVehicle.getAllPropConfigs()).thenReturn(hidlConfigs);

        HalPropConfig[] configs = mHidlVehicleStub.getAllPropConfigs();

        assertThat(configs.length).isEqualTo(1);
        assertThat(configs[0].getPropId()).isEqualTo(TEST_PROP);
        assertThat(configs[0].getAccess()).isEqualTo(TEST_ACCESS);
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequests() throws Exception {
        ArrayList<Integer> supportedPropIds = new ArrayList(Arrays.asList(
                VHAL_PROP_SUPPORTED_PROPERTY_IDS, 1, 2, 3, 4));
        int numConfigsPerRequest = 2;
        VehiclePropValue requestPropValue = new VehiclePropValue();
        requestPropValue.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;

        SparseArray<VehiclePropConfig> expectedConfigsById = new SparseArray<>();
        for (int i = 0; i < supportedPropIds.size(); i++) {
            int propId = supportedPropIds.get(i);
            VehiclePropConfig config = new VehiclePropConfig();
            config.prop = propId;
            if (propId == VHAL_PROP_SUPPORTED_PROPERTY_IDS) {
                config.configArray = new ArrayList(Arrays.asList(numConfigsPerRequest));
            }
            expectedConfigsById.put(propId, config);
        }

        // Return the supported IDs in get().
        doAnswer(inv -> {
            getCallback callback = (getCallback) inv.getArgument(1);
            VehiclePropValue propValue = new VehiclePropValue();
            propValue.prop = ((VehiclePropValue) inv.getArgument(0)).prop;
            propValue.value.int32Values = supportedPropIds;
            callback.onValues(StatusCode.OK, propValue);
            return null;
        }).when(mHidlVehicle).get(eq(requestPropValue), any());

        // Return the appropriate configs in getPropConfigs().
        doAnswer(inv -> {
            ArrayList<Integer> requestPropIds = (ArrayList<Integer>) inv.getArgument(0);
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            ArrayList<VehiclePropConfig> configs = new ArrayList<>();
            for (int j = 0; j < requestPropIds.size(); j++) {
                int propId = requestPropIds.get(j);
                configs.add(expectedConfigsById.get(propId));
            }
            callback.onValues(StatusCode.OK, configs);
            return null;
        }).when(mHidlVehicle).getPropConfigs(any(), any());

        HalPropConfig[] configs = mHidlVehicleStub.getAllPropConfigs();

        HalPropConfig[] expectedConfigs = new HalPropConfig[expectedConfigsById.size()];
        for (int i = 0; i < expectedConfigsById.size(); i++) {
            expectedConfigs[i] = new HidlHalPropConfig(expectedConfigsById.valueAt(i));
        }
        // Order does not matter.
        Comparator<HalPropConfig> configCmp =
                (config1, config2) -> (config1.getPropId() - config2.getPropId());
        Arrays.sort(configs, configCmp);
        Arrays.sort(expectedConfigs, configCmp);

        assertThat(configs.length).isEqualTo(expectedConfigs.length);
        for (int i = 0; i < configs.length; i++) {
            assertThat(configs[i].getPropId()).isEqualTo(expectedConfigs[i].getPropId());
        }
        verify(mHidlVehicle, never()).getAllPropConfigs();
        ArgumentCaptor<ArrayList<Integer>> captor = ArgumentCaptor.forClass(ArrayList.class);
        // The first request to check whether the property is supported.
        // Next 3 requests are sub requests.
        verify(mHidlVehicle, times(4)).getPropConfigs(captor.capture(), any());
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsNoConfig() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            VehiclePropConfig config = new VehiclePropConfig();
            // No config array.
            config.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;
            callback.onValues(StatusCode.OK, new ArrayList(Arrays.asList(config)));
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());

        assertThrows(IllegalArgumentException.class, () -> mHidlVehicleStub.getAllPropConfigs());
        verify(mHidlVehicle, never()).getAllPropConfigs();
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsInvalidConfig() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            VehiclePropConfig config = new VehiclePropConfig();
            // NumConfigsPerRequest is not a valid number.
            config.configArray = new ArrayList(Arrays.asList(0));
            config.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;
            callback.onValues(StatusCode.OK, new ArrayList(Arrays.asList(config)));
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());

        assertThrows(IllegalArgumentException.class, () -> mHidlVehicleStub.getAllPropConfigs());
        verify(mHidlVehicle, never()).getAllPropConfigs();
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsGetValueInvalidArg() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            VehiclePropConfig config = new VehiclePropConfig();
            config.configArray = new ArrayList(Arrays.asList(1));
            config.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;
            callback.onValues(StatusCode.OK, new ArrayList(Arrays.asList(config)));
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());

        doAnswer(inv -> {
            getCallback callback = (getCallback) inv.getArgument(1);
            callback.onValues(StatusCode.INVALID_ARG, /* configs= */ null);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        assertThrows(ServiceSpecificException.class, () -> mHidlVehicleStub.getAllPropConfigs());
        verify(mHidlVehicle, never()).getAllPropConfigs();
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsNoConfigReturned() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            callback.onValues(StatusCode.OK, new ArrayList<VehiclePropConfig>());
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());
        when(mHidlVehicle.getAllPropConfigs()).thenReturn(new ArrayList<VehiclePropConfig>());

        mHidlVehicleStub.getAllPropConfigs();

        // Must fall back to getAllPropConfigs when no config is returned for
        // VHAL_PROP_SUPPORTED_PROPERTY_IDS;
        verify(mHidlVehicle).getAllPropConfigs();
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsGetValueError() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            VehiclePropConfig config = new VehiclePropConfig();
            config.configArray = new ArrayList(Arrays.asList(1));
            config.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;
            callback.onValues(StatusCode.OK, new ArrayList(Arrays.asList(config)));
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());

        doAnswer(inv -> {
            getCallback callback = (getCallback) inv.getArgument(1);
            callback.onValues(StatusCode.INTERNAL_ERROR, null);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        assertThrows(ServiceSpecificException.class, () -> mHidlVehicleStub.getAllPropConfigs());
        verify(mHidlVehicle, never()).getAllPropConfigs();
    }

    @Test
    public void testGetAllPropConfigsHidlMultipleRequestsGetValueUnavailable() throws Exception {
        doAnswer(inv -> {
            getPropConfigsCallback callback = (getPropConfigsCallback) inv.getArgument(1);
            VehiclePropConfig config = new VehiclePropConfig();
            config.configArray = new ArrayList(Arrays.asList(1));
            config.prop = VHAL_PROP_SUPPORTED_PROPERTY_IDS;
            callback.onValues(StatusCode.OK, new ArrayList(Arrays.asList(config)));
            return null;
        }).when(mHidlVehicle).getPropConfigs(
                eq(new ArrayList<>(Arrays.asList(VHAL_PROP_SUPPORTED_PROPERTY_IDS))), any());

        doAnswer(inv -> {
            getCallback callback = (getCallback) inv.getArgument(1);
            VehiclePropValue value = new VehiclePropValue();
            value.status = VehiclePropertyStatus.UNAVAILABLE;
            callback.onValues(StatusCode.OK, value);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        ServiceSpecificException exception = assertThrows(ServiceSpecificException.class,
                () -> mHidlVehicleStub.getAllPropConfigs());
        assertThat(exception.errorCode).isEqualTo(
                StatusCode.INTERNAL_ERROR);
        verify(mHidlVehicle, never()).getAllPropConfigs();
    }

    @Test
    public void testSubscribeHidl() throws Exception {
        SubscribeOptions aidlOptions = new SubscribeOptions();
        aidlOptions.propId = TEST_PROP;
        aidlOptions.sampleRate = TEST_SAMPLE_RATE;
        android.hardware.automotive.vehicle.V2_0.SubscribeOptions hidlOptions =
                new android.hardware.automotive.vehicle.V2_0.SubscribeOptions();
        hidlOptions.propId = TEST_PROP;
        hidlOptions.sampleRate = TEST_SAMPLE_RATE;
        hidlOptions.flags = android.hardware.automotive.vehicle.V2_0.SubscribeFlags.EVENTS_FROM_CAR;

        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        VehicleStub.SubscriptionClient client = mHidlVehicleStub.newSubscriptionClient(callback);

        client.subscribe(new SubscribeOptions[]{aidlOptions});

        verify(mHidlVehicle).subscribe(
                (IVehicleCallback.Stub) client,
                new ArrayList<android.hardware.automotive.vehicle.V2_0.SubscribeOptions>(
                        Arrays.asList(hidlOptions)));
    }

    @Test
    public void testUnsubscribeHidl() throws Exception {
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        VehicleStub.SubscriptionClient client = mHidlVehicleStub.newSubscriptionClient(callback);

        client.unsubscribe(TEST_PROP);

        verify(mHidlVehicle).unsubscribe((IVehicleCallback.Stub) client, TEST_PROP);
    }

    @Test
    public void testGetAsync() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            VehiclePropValue propValue = (VehiclePropValue) args[0];
            assertThat(propValue.prop).isEqualTo(TEST_PROP);
            getCallback callback = (getCallback) args[1];
            callback.onValues(StatusCode.OK, propValue);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        HalPropValue value = TEST_PROP_VALUE;

        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(value);

        mHidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getHalPropValue()).isEqualTo(value);
    }

    @Test
    public void testGetAsyncError() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            VehiclePropValue propValue = (VehiclePropValue) args[0];
            assertThat(propValue.prop).isEqualTo(TEST_PROP);
            getCallback callback = (getCallback) args[1];
            callback.onValues(StatusCode.INVALID_ARG, propValue);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        HalPropValue value = TEST_PROP_VALUE;
        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(value);
        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        mHidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    @Test
    public void testGetAsyncTryAgainError() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            VehiclePropValue propValue = (VehiclePropValue) args[0];
            assertThat(propValue.prop).isEqualTo(TEST_PROP);
            getCallback callback = (getCallback) args[1];
            callback.onValues(StatusCode.TRY_AGAIN, propValue);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        HalPropValue value = TEST_PROP_VALUE;
        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(value);
        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        mHidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyErrorCodes.STATUS_TRY_AGAIN);
    }

    @Test
    public void testGetAsyncOneOkayResultOneNoValue() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            VehiclePropValue propValue = (VehiclePropValue) args[0];
            getCallback callback = (getCallback) args[1];
            if (propValue.prop == TEST_PROP) {
                // For TEST_PROP, return no result.
                callback.onValues(StatusCode.OK, null);
            } else {
                callback.onValues(StatusCode.OK, propValue);
            }
            return null;
        }).when(mHidlVehicle).get(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/* isAidl= */ false);
        HalPropValue newTestValue = builder.build(/* propId= */ 2, /* areaId= */ 0, TEST_VALUE);
        AsyncGetSetRequest request0 = defaultVehicleStubAsyncRequest(TEST_PROP_VALUE);
        AsyncGetSetRequest request1 = new AsyncGetSetRequest(
                /* serviceRequestId=*/ 1, newTestValue, /* timeoutInMs= */ 1000);
        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        mHidlVehicleStub.getAsync(List.of(request0, request1), mAsyncCallback);

        verify(mAsyncCallback, timeout(1000).times(2)).onGetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).hasSize(2);
        List<VehicleStub.GetVehicleStubAsyncResult> callResult0 = argumentCaptor.getAllValues()
                .get(0);
        assertThat(callResult0).hasSize(1);
        assertThat(callResult0.get(0).getServiceRequestId()).isEqualTo(0);
        assertThat(callResult0.get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        List<VehicleStub.GetVehicleStubAsyncResult> callResult1 = argumentCaptor.getAllValues()
                .get(1);
        assertThat(callResult1).hasSize(1);
        assertThat(callResult1.get(0).getServiceRequestId()).isEqualTo(1);
        assertThat(callResult1.get(0).getHalPropValue()).isEqualTo(newTestValue);
        assertThat(callResult1.get(0).getErrorCode()).isEqualTo(STATUS_OK);
    }

    @Test
    public void testGetSync() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            VehiclePropValue propValue = (VehiclePropValue) args[0];
            assertThat(propValue.prop).isEqualTo(TEST_PROP);
            getCallback callback = (getCallback) args[1];
            callback.onValues(StatusCode.OK, propValue);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        HalPropValue gotValue = mHidlVehicleStub.get(TEST_PROP_VALUE);

        assertThat(gotValue).isEqualTo(TEST_PROP_VALUE);
    }

    @Test(expected = ServiceSpecificException.class)
    public void testGetSyncError() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            VehiclePropValue propValue = (VehiclePropValue) args[0];
            assertThat(propValue.prop).isEqualTo(TEST_PROP);
            getCallback callback = (getCallback) args[1];
            callback.onValues(StatusCode.INVALID_ARG, propValue);
            return null;
        }).when(mHidlVehicle).get(any(), any());

        mHidlVehicleStub.get(TEST_PROP_VALUE);
    }

    @Test
    public void testSetAsync() throws Exception {
        when(mHidlVehicle.set(TEST_VHAL_VEHICLE_PROP_VALUE)).thenReturn(StatusCode.OK);

        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(
                TEST_PROP_VALUE);

        mHidlVehicleStub.setAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(STATUS_OK);
    }

    @Test
    public void testSetAsyncRemoteException() throws Exception {
        when(mHidlVehicle.set(TEST_VHAL_VEHICLE_PROP_VALUE)).thenThrow(new RemoteException());

        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(
                TEST_PROP_VALUE);

        mHidlVehicleStub.setAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    @Test
    public void testSetAsyncServiceSpecificExceptionTryAgain() throws Exception {
        when(mHidlVehicle.set(TEST_VHAL_VEHICLE_PROP_VALUE)).thenReturn(StatusCode.TRY_AGAIN);

        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(
                TEST_PROP_VALUE);

        mHidlVehicleStub.setAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyErrorCodes.STATUS_TRY_AGAIN);
    }

    @Test
    public void testSetAsyncServiceSpecificExceptionNotAvailable() throws Exception {
        when(mHidlVehicle.set(TEST_VHAL_VEHICLE_PROP_VALUE)).thenReturn(StatusCode.NOT_AVAILABLE);

        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(
                TEST_PROP_VALUE);

        mHidlVehicleStub.setAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testSetAsyncServiceSpecificExceptionInternal() throws Exception {
        when(mHidlVehicle.set(TEST_VHAL_VEHICLE_PROP_VALUE)).thenReturn(StatusCode.INTERNAL_ERROR);

        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(
                TEST_PROP_VALUE);

        mHidlVehicleStub.setAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    @Test
    public void testSetSync() throws Exception {
        when(mHidlVehicle.set(TEST_VHAL_VEHICLE_PROP_VALUE)).thenReturn(StatusCode.OK);

        mHidlVehicleStub.set(TEST_PROP_VALUE);
    }

    @Test
    public void testSetSyncError() throws Exception {
        when(mHidlVehicle.set(TEST_VHAL_VEHICLE_PROP_VALUE)).thenReturn(StatusCode.INVALID_ARG);

        ServiceSpecificException exception = assertThrows(ServiceSpecificException.class, () -> {
            mHidlVehicleStub.set(TEST_PROP_VALUE);
        });
        assertThat(exception.errorCode).isEqualTo(StatusCode.INVALID_ARG);
    }

    @Test
    public void testHidlVehicleCallbackOnPropertyEvent() throws Exception {
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        VehicleStub.SubscriptionClient client = mHidlVehicleStub.newSubscriptionClient(callback);
        IVehicleCallback.Stub hidlCallback = (IVehicleCallback.Stub) client;

        hidlCallback.onPropertyEvent(new ArrayList<VehiclePropValue>(Arrays.asList(
                TEST_VHAL_VEHICLE_PROP_VALUE)));

        verify(callback).onPropertyEvent(new ArrayList<HalPropValue>(Arrays.asList(
                TEST_PROP_VALUE)));
    }

    @Test
    public void testHidlVehicleCallbackOnPropertySetError() throws Exception {
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        VehicleStub.SubscriptionClient client = mHidlVehicleStub.newSubscriptionClient(callback);
        IVehicleCallback.Stub hidlCallback = (IVehicleCallback.Stub) client;
        VehiclePropError error = new VehiclePropError();
        error.propId = TEST_PROP;
        error.areaId = TEST_AREA;
        error.errorCode = TEST_STATUS;

        hidlCallback.onPropertySetError(TEST_STATUS, TEST_PROP, TEST_AREA);

        verify(callback).onPropertySetError(new ArrayList<VehiclePropError>(Arrays.asList(error)));
    }

    @Test
    public void testDumpHidl() throws Exception {
        ArrayList<String> options = new ArrayList<>();
        FileDescriptor fd = mock(FileDescriptor.class);

        mHidlVehicleStub.dump(fd, options);

        verify(mHidlVehicle).debug(any(), eq(options));
    }
}
