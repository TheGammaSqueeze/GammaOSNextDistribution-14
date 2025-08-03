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

import static com.android.car.internal.property.CarPropertyErrorCodes.STATUS_OK;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.property.CarPropertyManager;
import android.car.test.mocks.JavaMockitoHelper;
import android.hardware.automotive.vehicle.GetValueRequest;
import android.hardware.automotive.vehicle.GetValueRequests;
import android.hardware.automotive.vehicle.GetValueResult;
import android.hardware.automotive.vehicle.GetValueResults;
import android.hardware.automotive.vehicle.IVehicle;
import android.hardware.automotive.vehicle.IVehicleCallback;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.SetValueRequest;
import android.hardware.automotive.vehicle.SetValueRequests;
import android.hardware.automotive.vehicle.SetValueResult;
import android.hardware.automotive.vehicle.SetValueResults;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropConfigs;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.hardware.automotive.vehicle.VehiclePropErrors;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehiclePropValues;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;

import com.android.car.VehicleStub.AsyncGetSetRequest;
import com.android.car.hal.HalPropConfig;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.HalPropValueBuilder;
import com.android.car.hal.VehicleHalCallback;
import com.android.car.internal.LargeParcelable;
import com.android.car.internal.property.CarPropertyErrorCodes;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@RunWith(MockitoJUnitRunner.class)
public final class AidlVehicleStubUnitTest {

    private static final int TEST_PROP = 1;
    private static final int TEST_ACCESS = 2;
    private static final float TEST_SAMPLE_RATE = 3.0f;
    private static final int TEST_VALUE = 3;
    private static final int TEST_AREA = 4;
    private static final int TEST_STATUS = 5;

    private static final int VHAL_PROP_SUPPORTED_PROPERTY_IDS = 0x11410F48;

    private static final HalPropValue HVAC_PROP_VALUE;
    private static final HalPropValue TEST_PROP_VALUE;

    static {
        HalPropValueBuilder builder = new HalPropValueBuilder(/* isAidl= */true);
        HVAC_PROP_VALUE =  builder.build(HVAC_TEMPERATURE_SET, /* areaId= */ 0, 17.0f);
        TEST_PROP_VALUE = builder.build(TEST_PROP, /* areaId= */ 0, TEST_VALUE);
    }

    @Mock
    private IVehicle mAidlVehicle;
    @Mock
    private IBinder mAidlBinder;
    @Mock
    private VehicleStub.VehicleStubCallbackInterface mAsyncCallback;

    private AidlVehicleStub mAidlVehicleStub;

    private final HandlerThread mHandlerThread = new HandlerThread(
            AidlVehicleStubUnitTest.class.getSimpleName());
    private Handler mHandler;

    private int[] getTestIntValues(int length) {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = TEST_VALUE;
        }
        return values;
    }

    private AsyncGetSetRequest defaultVehicleStubAsyncRequest(HalPropValue value) {
        return new AsyncGetSetRequest(/* serviceRequestId=*/ 0, value,
                /* timeoutUptimeMs= */ SystemClock.uptimeMillis() + 1000);
    }

    @Before
    public void setUp() {
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        when(mAidlVehicle.asBinder()).thenReturn(mAidlBinder);

        mAidlVehicleStub = new AidlVehicleStub(mAidlVehicle, mHandlerThread);

        assertThat(mAidlVehicleStub.isValid()).isTrue();
    }

    @After
    public void tearDown() {
        // Remove all pending messages in the handler queue.
        mHandlerThread.quitSafely();
    }

    @Test
    public void testGetInterfaceDescriptorAidl() throws Exception {
        mAidlVehicleStub.getInterfaceDescriptor();

        verify(mAidlBinder).getInterfaceDescriptor();
    }

    @Test(expected = IllegalStateException.class)
    public void testGetInterfaceDescriptorRemoteException() throws Exception {
        when(mAidlBinder.getInterfaceDescriptor()).thenThrow(new RemoteException());

        mAidlVehicleStub.getInterfaceDescriptor();
    }

    @Test
    public void testLinkToDeathAidl() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);

        mAidlVehicleStub.linkToDeath(recipient);

        verify(mAidlBinder).linkToDeath(recipient, 0);
    }

    @Test(expected = IllegalStateException.class)
    public void testLinkToDeathRemoteException() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);
        doThrow(new RemoteException()).when(mAidlBinder).linkToDeath(recipient, 0);

        mAidlVehicleStub.linkToDeath(recipient);
    }

    @Test
    public void testUnlinkToDeathAidl() throws Exception {
        IVehicleDeathRecipient recipient = mock(IVehicleDeathRecipient.class);

        mAidlVehicleStub.unlinkToDeath(recipient);

        verify(mAidlBinder).unlinkToDeath(recipient, 0);
    }

    @Test
    public void testGetAllProdConfigsAidlSmallData() throws Exception {
        VehiclePropConfigs aidlConfigs = new VehiclePropConfigs();
        VehiclePropConfig aidlConfig = new VehiclePropConfig();
        aidlConfig.prop = TEST_PROP;
        aidlConfig.access = TEST_ACCESS;
        aidlConfigs.sharedMemoryFd = null;
        aidlConfigs.payloads = new VehiclePropConfig[]{aidlConfig};

        when(mAidlVehicle.getAllPropConfigs()).thenReturn(aidlConfigs);

        HalPropConfig[] configs = mAidlVehicleStub.getAllPropConfigs();

        assertThat(configs.length).isEqualTo(1);
        assertThat(configs[0].getPropId()).isEqualTo(TEST_PROP);
        assertThat(configs[0].getAccess()).isEqualTo(TEST_ACCESS);
    }

    @Test
    public void testGetAllPropConfigsAidlLargeData() throws Exception {
        int configSize = 1000;
        VehiclePropConfigs aidlConfigs = new VehiclePropConfigs();
        VehiclePropConfig aidlConfig = new VehiclePropConfig();
        aidlConfig.prop = TEST_PROP;
        aidlConfig.access = TEST_ACCESS;
        aidlConfigs.payloads = new VehiclePropConfig[configSize];
        for (int i = 0; i < configSize; i++) {
            aidlConfigs.payloads[i] = aidlConfig;
        }

        aidlConfigs = (VehiclePropConfigs) LargeParcelable.toLargeParcelable(aidlConfigs, () -> {
            VehiclePropConfigs newConfigs = new VehiclePropConfigs();
            newConfigs.payloads = new VehiclePropConfig[0];
            return newConfigs;
        });

        assertThat(aidlConfigs.sharedMemoryFd).isNotNull();

        when(mAidlVehicle.getAllPropConfigs()).thenReturn(aidlConfigs);

        HalPropConfig[] configs = mAidlVehicleStub.getAllPropConfigs();

        assertThat(configs.length).isEqualTo(configSize);
        for (int i = 0; i < configSize; i++) {
            assertThat(configs[i].getPropId()).isEqualTo(TEST_PROP);
            assertThat(configs[i].getAccess()).isEqualTo(TEST_ACCESS);
        }
    }

    @Test
    public void testSubscribeAidl() throws Exception {
        SubscribeOptions option = new SubscribeOptions();
        option.propId = TEST_PROP;
        option.sampleRate = TEST_SAMPLE_RATE;
        SubscribeOptions[] options = new SubscribeOptions[]{option};

        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);

        client.subscribe(options);

        verify(mAidlVehicle).subscribe((IVehicleCallback) client, options,
                /*maxSharedMemoryFileCount=*/2);
    }

    @Test
    public void testUnsubscribeAidl() throws Exception {
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);

        client.unsubscribe(TEST_PROP);

        verify(mAidlVehicle).unsubscribe((IVehicleCallback) client, new int[]{TEST_PROP});
    }

    @Test
    public void testGetAidlSmallData() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            callback.onGetValues(results);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = TEST_PROP_VALUE;

        HalPropValue gotValue = mAidlVehicleStub.get(value);

        assertThat(gotValue).isEqualTo(value);
        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testGetAidlLargeData() throws Exception {
        int dataSize = 2000;
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(0);
            assertThat(requests.sharedMemoryFd).isNotNull();
            requests = (GetValueRequests)
                    LargeParcelable.reconstructStableAIDLParcelable(
                            requests, /*keepSharedMemory=*/false);
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];

            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            results = (GetValueResults) LargeParcelable.toLargeParcelable(
                    results, () -> {
                        GetValueResults newResults = new GetValueResults();
                        newResults.payloads = new GetValueResult[0];
                        return newResults;
                    });

            assertThat(results.sharedMemoryFd).isNotNull();

            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            callback.onGetValues(results);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, 0, 0, getTestIntValues(dataSize));

        HalPropValue gotValue = mAidlVehicleStub.get(value);

        assertThat(gotValue).isEqualTo(value);
        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test(expected = ServiceSpecificException.class)
    public void testGetAidlError() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            GetValueResults results = createGetValueResults(StatusCode.INVALID_ARG,
                    requests.payloads);

            callback.onGetValues(results);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = TEST_PROP_VALUE;

        mAidlVehicleStub.get(value);
        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testGetAidlAsyncCallback() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            // Call callback after 100ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onGetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 100);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = TEST_PROP_VALUE;

        HalPropValue gotValue = mAidlVehicleStub.get(value);

        assertThat(gotValue).isEqualTo(value);
        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    private GetValueResults createGetValueResults(int status, GetValueRequest[] requests) {
        GetValueResults results = new GetValueResults();
        results.payloads = new GetValueResult[requests.length];
        for (int i = 0; i < requests.length; i++) {
            GetValueResult result = new GetValueResult();
            result.status = status;
            if (status == StatusCode.OK) {
                result.prop = requests[i].prop;
            }
            result.requestId = requests[i].requestId;
            results.payloads[i] = result;
        }
        return results;
    }

    @Test
    public void testGetAsyncAidl() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(HVAC_TEMPERATURE_SET);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            // Call callback after 100ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onGetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 100);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = HVAC_PROP_VALUE;

        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(value);

        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getHalPropValue()).isEqualTo(value);
    }

    @Test
    public void testGetAsyncAidlError() throws RemoteException {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(HVAC_TEMPERATURE_SET);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            GetValueResults results = createGetValueResults(StatusCode.INTERNAL_ERROR,
                    requests.payloads);

            // Call callback after 100ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onGetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 100);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = HVAC_PROP_VALUE;

        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(value);

        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    private void createGetAsyncAidlException(int errorCode) throws Exception {
        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(
                HVAC_PROP_VALUE);
        ServiceSpecificException exception = new ServiceSpecificException(errorCode);
        doThrow(exception).when(mAidlVehicle).getValues(any(), any());

        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);
    }

    @Test
    public void testGetAsyncAidlServiceSpecificExceptionInternalError() throws Exception {
        createGetAsyncAidlException(StatusCode.INTERNAL_ERROR);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    @Test
    public void testGetAsyncAidlServiceSpecificExceptionTryAgainError() throws Exception {
        createGetAsyncAidlException(StatusCode.TRY_AGAIN);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyErrorCodes.STATUS_TRY_AGAIN);
    }

    @Test
    public void testGetAsyncAidlServiceSpecificExceptionNotAvailable() throws Exception {
        createGetAsyncAidlException(StatusCode.NOT_AVAILABLE);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testGetAsyncAidlServiceSpecificExceptionNotAvailableDisabled() throws Exception {
        createGetAsyncAidlException(StatusCode.NOT_AVAILABLE_DISABLED);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testGetAsyncAidlServiceSpecificExceptionNotAvailableSpeedLow() throws Exception {
        createGetAsyncAidlException(StatusCode.NOT_AVAILABLE_SPEED_LOW);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testGetAsyncAidlServiceSpecificExceptionNotAvailableSpeedHigh() throws Exception {
        createGetAsyncAidlException(StatusCode.NOT_AVAILABLE_SPEED_HIGH);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testGetAsyncAidlServiceSpecificExceptionNotAvailablePoorVisibility()
            throws Exception {
        createGetAsyncAidlException(StatusCode.NOT_AVAILABLE_POOR_VISIBILITY);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testGetAsyncAidlServiceSpecificExceptionNotAvailableSafety() throws Exception {
        createGetAsyncAidlException(StatusCode.NOT_AVAILABLE_SAFETY);

        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    private void postOnBinderDied(CountDownLatch latch, DeathRecipient deathRecipient) {
        mHandler.post(() -> {
            deathRecipient.binderDied();
            latch.countDown();
        });
    }

    private void mockGetValues(List<IVehicleCallback.Stub> callbackWrapper,
            List<GetValueResults> resultsWrapper) throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);
            callbackWrapper.add(callback);
            resultsWrapper.add(results);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());
    }

    private void triggerCallback(List<IVehicleCallback.Stub> callbackWrapper,
            List<GetValueResults> resultsWrapper) throws Exception {
        assertWithMessage("callback wrapper").that(callbackWrapper).hasSize(1);
        assertWithMessage("results wrapper").that(resultsWrapper).hasSize(1);
        callbackWrapper.get(0).onGetValues(resultsWrapper.get(0));
    }

    // Test that the client information is cleaned-up and client callback will not be called if
    // client's binder died after the getAsync request.
    @Test
    public void testGetAsyncAidlBinderDiedAfterRegisterCallback() throws Exception {
        List<IVehicleCallback.Stub> callbackWrapper = new ArrayList<>();
        List<GetValueResults> resultsWrapper = new ArrayList<>();
        List<DeathRecipient> recipientWrapper = new ArrayList<>();
        mockGetValues(callbackWrapper, resultsWrapper);
        doAnswer((invocation) -> {
            recipientWrapper.add((DeathRecipient) invocation.getArguments()[0]);
            return null;
        }).when(mAsyncCallback).linkToDeath(any());

        HalPropValue value = HVAC_PROP_VALUE;

        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(value);

        // Send the getAsync request.
        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        verify(mAsyncCallback).linkToDeath(any());

        CountDownLatch latch = new CountDownLatch(1);
        // After sending the request, the client died. Must call the binderDied from a different
        // thread.
        postOnBinderDied(latch, recipientWrapper.get(0));
        JavaMockitoHelper.await(latch, /* timeoutMs= */ 1000);

        // Trigger the callback after the binder is dead.
        triggerCallback(callbackWrapper, resultsWrapper);

        verify(mAsyncCallback, never()).onGetAsyncResults(any());
    }

    // Test that the client information is cleaned-up and client callback will not be called if
    // client's binder died while getAsync is adding the callbacks.
    @Test
    public void testGetAsyncAidlBinderDiedWhileRegisterCallback() throws Exception {
        List<IVehicleCallback.Stub> callbackWrapper = new ArrayList<>();
        List<GetValueResults> resultsWrapper = new ArrayList<>();
        mockGetValues(callbackWrapper, resultsWrapper);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer((invocation) -> {
            // After linkToDeath is called, the client died immediately. Must call the binderDied
            // from a different thread.
            postOnBinderDied(latch, ((DeathRecipient) invocation.getArguments()[0]));
            return null;
        }).when(mAsyncCallback).linkToDeath(any());

        HalPropValue value = HVAC_PROP_VALUE;

        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(value);

        // Send the getAsync request.
        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);

        verify(mAsyncCallback).linkToDeath(any());
        // Make sure binderDied is called.
        JavaMockitoHelper.await(latch, /* timeoutMs= */ 1000);

        // Make sure we have finished registering the callback and the client is also died before
        // we send the callback out.
        // This will trigger the callback.
        triggerCallback(callbackWrapper, resultsWrapper);

        verify(mAsyncCallback, never()).onGetAsyncResults(any());
    }

    // Test that the client callback will not be called if client's binder already died before
    // getAsync is called.
    @Test
    public void testGetAsyncAidlBinderDiedBeforeRegisterCallback() throws Exception {
        doThrow(new RemoteException()).when(mAsyncCallback).linkToDeath(any());

        HalPropValue value = HVAC_PROP_VALUE;

        AsyncGetSetRequest getVehicleStubAsyncRequest = defaultVehicleStubAsyncRequest(value);

        // Send the getAsync request.
        assertThrows(IllegalStateException.class, () -> {
            mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest), mAsyncCallback);
        });
    }

    @Test
    public void testGetAsyncAidlTimeout() throws Exception {
        List<IVehicleCallback.Stub> callbackWrapper = new ArrayList<>();
        List<GetValueResults> resultsWrapper = new ArrayList<>();
        mockGetValues(callbackWrapper, resultsWrapper);

        HalPropValue value = HVAC_PROP_VALUE;

        long now = SystemClock.uptimeMillis();
        AsyncGetSetRequest getVehicleStubAsyncRequest1 = new AsyncGetSetRequest(
                /* serviceRequestId= */ 0, value, /* timeoutUptimeMs= */ now);
        AsyncGetSetRequest getVehicleStubAsyncRequest2 = new AsyncGetSetRequest(
                /* serviceRequestId= */ 1, value, /* timeoutUptimeMs= */ now);

        // Send the getAsync request.
        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest1, getVehicleStubAsyncRequest2),
                mAsyncCallback);

        verify(mAsyncCallback, timeout(1000)).onRequestsTimeout(List.of(0, 1));

        // Trigger the callback.
        triggerCallback(callbackWrapper, resultsWrapper);

        // If the requests already timed-out, we must not receive any results.
        verify(mAsyncCallback, never()).onGetAsyncResults(any());
    }

    // Test that if the request finished before the timeout, the timeout callback must not be
    // called.
    @Test
    public void testGetAsyncAidlTimeoutMustNotTiggerAfterRequestFinished() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            try {
                callback.onGetValues(results);
            } catch (RemoteException e) {
                // ignore.
            }
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = HVAC_PROP_VALUE;

        long timeoutUptimeMs = SystemClock.uptimeMillis() + 100;
        AsyncGetSetRequest getVehicleStubAsyncRequest1 = new AsyncGetSetRequest(
                /* serviceRequestId= */ 0, value, timeoutUptimeMs);
        AsyncGetSetRequest getVehicleStubAsyncRequest2 = new AsyncGetSetRequest(
                /* serviceRequestId= */ 1, value, timeoutUptimeMs);

        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest1, getVehicleStubAsyncRequest2),
                mAsyncCallback);

        // Our callback is called synchronously so we can verify immediately.
        verify(mAsyncCallback).onGetAsyncResults(any());
        verify(mAsyncCallback, after(500).never()).onRequestsTimeout(any());
    }

    @Test
    public void testGetAsyncAidlOneRequestTimeoutOneFinish() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            // Only generate result for the first request.
            GetValueResults results = createGetValueResults(
                    StatusCode.OK, new GetValueRequest[]{requests.payloads[0]});

            try {
                callback.onGetValues(results);
            } catch (RemoteException e) {
                // ignore.
            }
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = HVAC_PROP_VALUE;

        // Requests will timeout after 100ms from this point. Don't make this too short otherwise
        // the okay result will timeout.
        long timeoutUptimeMs = SystemClock.uptimeMillis() + 100;
        AsyncGetSetRequest getVehicleStubAsyncRequest1 = new AsyncGetSetRequest(
                /* serviceRequestId= */ 0, value, timeoutUptimeMs);
        AsyncGetSetRequest getVehicleStubAsyncRequest2 = new AsyncGetSetRequest(
                /* serviceRequestId= */ 1, value, timeoutUptimeMs);

        // Send the getAsync request.
        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest1, getVehicleStubAsyncRequest2),
                mAsyncCallback);

        // Request 0 has result.
        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        // Our callback is called synchronously so we can verify immediately.
        verify(mAsyncCallback).onGetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().size()).isEqualTo(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        // Request 1 timed-out.
        verify(mAsyncCallback, timeout(1000)).onRequestsTimeout(List.of(1));
    }

    @Test
    public void testCancelAsyncGetRequests() throws Exception {
        List<IVehicleCallback.Stub> callbackWrapper = new ArrayList<>();
        List<GetValueResults> resultsWrapper = new ArrayList<>();
        mockGetValues(callbackWrapper, resultsWrapper);

        HalPropValue value = HVAC_PROP_VALUE;

        long timeoutUptimeMs = SystemClock.uptimeMillis() + 1000;
        AsyncGetSetRequest getVehicleStubAsyncRequest1 = new AsyncGetSetRequest(
                /* serviceRequestId= */ 0, value, timeoutUptimeMs);
        AsyncGetSetRequest getVehicleStubAsyncRequest2 = new AsyncGetSetRequest(
                /* serviceRequestId= */ 1, value, timeoutUptimeMs);

        // Send the getAsync request.
        mAidlVehicleStub.getAsync(List.of(getVehicleStubAsyncRequest1, getVehicleStubAsyncRequest2),
                mAsyncCallback);

        // Cancel the first request.
        mAidlVehicleStub.cancelRequests(List.of(0));

        // Trigger the callback.
        triggerCallback(callbackWrapper, resultsWrapper);

        // We should only receive the result for the second request.
        ArgumentCaptor<List<VehicleStub.GetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mAsyncCallback, timeout(1000)).onGetAsyncResults(
                argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().size()).isEqualTo(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(
                1);
        verify(mAsyncCallback, never()).onRequestsTimeout(any());
    }

    @Test
    public void testGetSyncAidlTimeout() throws Exception {
        mAidlVehicleStub.setSyncOpTimeoutInMs(100);
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            GetValueRequests requests = (GetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            GetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.prop.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            GetValueResults results = createGetValueResults(StatusCode.OK, requests.payloads);

            // Call callback after 200ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onGetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 200);
            return null;
        }).when(mAidlVehicle).getValues(any(), any());

        HalPropValue value = TEST_PROP_VALUE;

        ServiceSpecificException exception = assertThrows(ServiceSpecificException.class, () -> {
            mAidlVehicleStub.get(value);
        });
        assertThat(exception.errorCode).isEqualTo(StatusCode.INTERNAL_ERROR);
        assertThat(exception.getMessage()).contains("request timeout");

        PollingCheck.check("callback is not called", 1000, () -> {
            return !mHandler.hasMessagesOrCallbacks();
        });

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testSetSyncAidlSmallData() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            SetValueRequests requests = (SetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            SetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.value.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            SetValueResults results = new SetValueResults();
            SetValueResult result = new SetValueResult();
            result.status = StatusCode.OK;
            result.requestId = request.requestId;
            results.payloads = new SetValueResult[]{result};

            callback.onSetValues(results);
            return null;
        }).when(mAidlVehicle).setValues(any(), any());

        HalPropValue value = TEST_PROP_VALUE;

        mAidlVehicleStub.set(value);

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testSetSyncAidlLargeData() throws Exception {
        int dataSize = 2000;
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            SetValueRequests requests = (SetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(0);
            assertThat(requests.sharedMemoryFd).isNotNull();
            requests = (SetValueRequests)
                    LargeParcelable.reconstructStableAIDLParcelable(
                            requests, /*keepSharedMemory=*/false);
            assertThat(requests.payloads.length).isEqualTo(1);
            SetValueRequest request = requests.payloads[0];

            SetValueResults results = new SetValueResults();
            SetValueResult result = new SetValueResult();
            result.status = StatusCode.OK;
            result.requestId = request.requestId;
            results.payloads = new SetValueResult[]{result};

            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            callback.onSetValues(results);
            return null;
        }).when(mAidlVehicle).setValues(any(), any());

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue value = builder.build(TEST_PROP, 0, 0, 0, getTestIntValues(dataSize));

        mAidlVehicleStub.set(value);

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test(expected = ServiceSpecificException.class)
    public void testSetSyncAidlError() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            SetValueRequests requests = (SetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            SetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.value.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            SetValueResults results = new SetValueResults();
            SetValueResult result = new SetValueResult();
            result.status = StatusCode.INVALID_ARG;
            result.requestId = request.requestId;
            results.payloads = new SetValueResult[]{result};

            callback.onSetValues(results);
            return null;
        }).when(mAidlVehicle).setValues(any(), any());

        HalPropValue value = TEST_PROP_VALUE;

        mAidlVehicleStub.set(value);

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testSetSyncAidlAsyncCallback() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            SetValueRequests requests = (SetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            SetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.value.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            SetValueResults results = new SetValueResults();
            SetValueResult result = new SetValueResult();
            result.status = StatusCode.OK;
            result.requestId = request.requestId;
            results.payloads = new SetValueResult[]{result};

            // Call callback after 100ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onSetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 100);
            return null;
        }).when(mAidlVehicle).setValues(any(), any());

        HalPropValue value = TEST_PROP_VALUE;

        mAidlVehicleStub.set(value);

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    @Test
    public void testSetSyncAidlTimeout() throws Exception {
        mAidlVehicleStub.setSyncOpTimeoutInMs(100);
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            SetValueRequests requests = (SetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            SetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.value.prop).isEqualTo(TEST_PROP);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];

            SetValueResults results = new SetValueResults();
            SetValueResult result = new SetValueResult();
            result.status = StatusCode.OK;
            result.requestId = request.requestId;
            results.payloads = new SetValueResult[]{result};

            // Call callback after 200ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onSetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 200);
            return null;
        }).when(mAidlVehicle).setValues(any(), any());

        HalPropValue value = TEST_PROP_VALUE;

        ServiceSpecificException exception = assertThrows(ServiceSpecificException.class, () -> {
            mAidlVehicleStub.set(value);
        });
        assertThat(exception.errorCode).isEqualTo(StatusCode.INTERNAL_ERROR);
        assertThat(exception.getMessage()).contains("request timeout");

        PollingCheck.check("callback is not called", 1000, () -> {
            return !mHandler.hasMessagesOrCallbacks();
        });

        assertThat(mAidlVehicleStub.countPendingRequests()).isEqualTo(0);
    }

    private SetValueResults createSetValueResults(int status, SetValueRequest[] requests) {
        SetValueResults results = new SetValueResults();
        results.payloads = new SetValueResult[requests.length];
        for (int i = 0; i < requests.length; i++) {
            SetValueResult result = new SetValueResult();
            result.status = status;
            result.requestId = requests[i].requestId;
            results.payloads[i] = result;
        }
        return results;
    }

    @Test
    public void testSetAsync() throws Exception {
        doAnswer((invocation) -> {
            Object[] args = invocation.getArguments();
            SetValueRequests requests = (SetValueRequests) args[1];
            assertThat(requests.payloads.length).isEqualTo(1);
            SetValueRequest request = requests.payloads[0];
            assertThat(request.requestId).isEqualTo(0);
            assertThat(request.value.prop).isEqualTo(HVAC_TEMPERATURE_SET);
            IVehicleCallback.Stub callback = (IVehicleCallback.Stub) args[0];
            SetValueResults results = createSetValueResults(StatusCode.OK, requests.payloads);

            // Call callback after 100ms.
            mHandler.postDelayed(() -> {
                try {
                    callback.onSetValues(results);
                } catch (RemoteException e) {
                    // ignore.
                }
            }, /* delayMillis= */ 10);
            return null;
        }).when(mAidlVehicle).setValues(any(), any());

        AsyncGetSetRequest request = defaultVehicleStubAsyncRequest(HVAC_PROP_VALUE);

        mAidlVehicleStub.setAsync(List.of(request), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);

        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(STATUS_OK);
    }

    @Test
    public void testSetAsyncRemoteException() throws Exception {
        doThrow(new RemoteException()).when(mAidlVehicle).setValues(any(), any());
        AsyncGetSetRequest request = defaultVehicleStubAsyncRequest(HVAC_PROP_VALUE);

        mAidlVehicleStub.setAsync(List.of(request), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_INTERNAL_ERROR);
    }

    @Test
    public void testSetAsyncServiceSpecificExceptionTryAgain() throws Exception {
        doThrow(new ServiceSpecificException(StatusCode.TRY_AGAIN)).when(mAidlVehicle)
                .setValues(any(), any());
        AsyncGetSetRequest request = defaultVehicleStubAsyncRequest(HVAC_PROP_VALUE);

        mAidlVehicleStub.setAsync(List.of(request), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyErrorCodes.STATUS_TRY_AGAIN);
    }

    @Test
    public void testSetAsyncServiceSpecificExceptionNotAvailable() throws Exception {
        doThrow(new ServiceSpecificException(StatusCode.NOT_AVAILABLE)).when(mAidlVehicle)
                .setValues(any(), any());
        AsyncGetSetRequest request = defaultVehicleStubAsyncRequest(HVAC_PROP_VALUE);

        mAidlVehicleStub.setAsync(List.of(request), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testSetAsyncServiceSpecificExceptionNotAvailableDisabled() throws Exception {
        doThrow(new ServiceSpecificException(StatusCode.NOT_AVAILABLE_DISABLED)).when(mAidlVehicle)
                .setValues(any(), any());
        AsyncGetSetRequest request = defaultVehicleStubAsyncRequest(HVAC_PROP_VALUE);

        mAidlVehicleStub.setAsync(List.of(request), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testSetAsyncServiceSpecificExceptionNotAvailableSpeedLow() throws Exception {
        doThrow(new ServiceSpecificException(StatusCode.NOT_AVAILABLE_SPEED_LOW)).when(mAidlVehicle)
                .setValues(any(), any());
        AsyncGetSetRequest request = defaultVehicleStubAsyncRequest(HVAC_PROP_VALUE);

        mAidlVehicleStub.setAsync(List.of(request), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testSetAsyncServiceSpecificExceptionNotAvailableSpeedHigh() throws Exception {
        doThrow(new ServiceSpecificException(StatusCode.NOT_AVAILABLE_SPEED_HIGH))
                .when(mAidlVehicle)
                .setValues(any(), any());
        AsyncGetSetRequest request = defaultVehicleStubAsyncRequest(HVAC_PROP_VALUE);

        mAidlVehicleStub.setAsync(List.of(request), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testSetAsyncServiceSpecificExceptionNotAvailablePoorVisibility() throws Exception {
        doThrow(new ServiceSpecificException(StatusCode.NOT_AVAILABLE_POOR_VISIBILITY))
                .when(mAidlVehicle)
                .setValues(any(), any());
        AsyncGetSetRequest request = defaultVehicleStubAsyncRequest(HVAC_PROP_VALUE);

        mAidlVehicleStub.setAsync(List.of(request), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testSetAsyncServiceSpecificExceptionNotAvailableSafety() throws Exception {
        doThrow(new ServiceSpecificException(StatusCode.NOT_AVAILABLE_SAFETY)).when(mAidlVehicle)
                .setValues(any(), any());
        AsyncGetSetRequest request = defaultVehicleStubAsyncRequest(HVAC_PROP_VALUE);

        mAidlVehicleStub.setAsync(List.of(request), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
    }

    @Test
    public void testSetAsyncServiceSpecificExceptionVendorErrorCode() throws Exception {
        doThrow(new ServiceSpecificException(
                StatusCode.NOT_AVAILABLE_SAFETY | (0x1234 << 16))).when(mAidlVehicle)
                .setValues(any(), any());
        AsyncGetSetRequest request = defaultVehicleStubAsyncRequest(HVAC_PROP_VALUE);

        mAidlVehicleStub.setAsync(List.of(request), mAsyncCallback);

        ArgumentCaptor<List<VehicleStub.SetVehicleStubAsyncResult>> argumentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mAsyncCallback, timeout(1000)).onSetAsyncResults(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).hasSize(1);
        assertThat(argumentCaptor.getValue().get(0).getServiceRequestId()).isEqualTo(0);
        assertThat(argumentCaptor.getValue().get(0).getErrorCode()).isEqualTo(
                CarPropertyManager.STATUS_ERROR_NOT_AVAILABLE);
        assertThat(argumentCaptor.getValue().get(0).getVendorErrorCode()).isEqualTo(0x1234);
    }

    @Test
    public void testAidlVehicleCallbackOnPropertyEventSmallData() throws Exception {
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);
        IVehicleCallback aidlCallback = (IVehicleCallback) client;
        VehiclePropValues propValues = new VehiclePropValues();
        VehiclePropValue propValue = new VehiclePropValue();
        propValue.prop = TEST_PROP;
        propValue.value = new RawPropValues();
        propValue.value.int32Values = new int[]{TEST_VALUE};
        propValues.payloads = new VehiclePropValue[]{propValue};

        aidlCallback.onPropertyEvent(propValues, /*sharedMemoryFileCount=*/0);

        verify(callback).onPropertyEvent(new ArrayList<HalPropValue>(Arrays.asList(
                TEST_PROP_VALUE)));
    }

    @Test
    public void testAidlVehicleCallbackOnPropertyEventLargeData() throws Exception {
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);
        IVehicleCallback aidlCallback = (IVehicleCallback) client;
        VehiclePropValues propValues = new VehiclePropValues();
        VehiclePropValue propValue = new VehiclePropValue();
        propValue.prop = TEST_PROP;
        int dataSize = 2000;
        int[] intValues = getTestIntValues(dataSize);
        propValue.value = new RawPropValues();
        propValue.value.int32Values = intValues;
        propValues.payloads = new VehiclePropValue[]{propValue};
        propValues = (VehiclePropValues) LargeParcelable.toLargeParcelable(propValues, () -> {
            VehiclePropValues newValues = new VehiclePropValues();
            newValues.payloads = new VehiclePropValue[0];
            return newValues;
        });
        assertThat(propValues.sharedMemoryFd).isNotNull();

        HalPropValueBuilder builder = new HalPropValueBuilder(/*isAidl=*/true);
        HalPropValue halPropValue = builder.build(TEST_PROP, 0, 0, 0, intValues);

        aidlCallback.onPropertyEvent(propValues, /*sharedMemoryFileCount=*/0);

        verify(callback).onPropertyEvent(new ArrayList<HalPropValue>(Arrays.asList(halPropValue)));
    }

    @Test
    public void testAidlVehicleCallbackOnPropertySetErrorSmallData() throws Exception {
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);
        IVehicleCallback aidlCallback = (IVehicleCallback) client;
        VehiclePropErrors errors = new VehiclePropErrors();
        VehiclePropError error = new VehiclePropError();
        error.propId = TEST_PROP;
        error.areaId = TEST_AREA;
        error.errorCode = TEST_STATUS;
        errors.payloads = new VehiclePropError[]{error};

        aidlCallback.onPropertySetError(errors);

        verify(callback).onPropertySetError(new ArrayList<VehiclePropError>(Arrays.asList(error)));
    }

    @Test
    public void testAidlVehicleCallbackOnPropertySetErrorLargeData() throws Exception {
        VehicleHalCallback callback = mock(VehicleHalCallback.class);
        VehicleStub.SubscriptionClient client = mAidlVehicleStub.newSubscriptionClient(callback);
        IVehicleCallback aidlCallback = (IVehicleCallback) client;
        VehiclePropErrors errors = new VehiclePropErrors();
        VehiclePropError error = new VehiclePropError();
        error.propId = TEST_PROP;
        error.areaId = TEST_AREA;
        error.errorCode = TEST_STATUS;
        int errorCount = 1000;
        errors.payloads = new VehiclePropError[errorCount];
        for (int i = 0; i < errorCount; i++) {
            errors.payloads[i] = error;
        }
        errors = (VehiclePropErrors) LargeParcelable.toLargeParcelable(errors, () -> {
            VehiclePropErrors newErrors = new VehiclePropErrors();
            newErrors.payloads = new VehiclePropError[0];
            return newErrors;
        });
        assertThat(errors.sharedMemoryFd).isNotNull();

        ArrayList<VehiclePropError> expectErrors = new ArrayList<VehiclePropError>(errorCount);
        for (int i = 0; i < errorCount; i++) {
            expectErrors.add(error);
        }

        aidlCallback.onPropertySetError(errors);

        verify(callback).onPropertySetError(expectErrors);
    }

    @Test
    public void testDumpAidl() throws Exception {
        ArrayList<String> options = new ArrayList<>();
        FileDescriptor fd = mock(FileDescriptor.class);

        mAidlVehicleStub.dump(fd, options);

        verify(mAidlBinder).dump(eq(fd), eq(new String[0]));
    }
}
