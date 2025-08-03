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

package com.android.car.hal;

import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.feature.FeatureFlags;
import android.car.hardware.property.CarPropertyManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.VehicleAreaConfig;
import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.hardware.automotive.vehicle.VehiclePropertyType;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;

import com.android.car.CarServiceUtils;
import com.android.car.VehicleStub;
import com.android.car.VehicleStub.AsyncGetSetRequest;
import com.android.car.hal.VehicleHal.HalSubscribeOptions;
import com.android.car.internal.util.ArrayUtils;
import com.android.car.internal.util.IndentingPrintWriter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class VehicleHalTest extends AbstractExtendedMockitoTestCase {

    private static final int WAIT_TIMEOUT_MS = 1000;

    private static final int SOME_READ_ON_CHANGE_PROPERTY = 0x01;
    private static final int SOME_READ_WRITE_STATIC_PROPERTY = 0x02;
    private static final int SOME_BOOL_PROPERTY = VehiclePropertyType.BOOLEAN | 0x03;
    private static final int SOME_INT32_PROPERTY = VehiclePropertyType.INT32 | 0x04;
    private static final int SOME_INT32_VEC_PROPERTY = VehiclePropertyType.INT32_VEC | 0x05;
    private static final int SOME_FLOAT_PROPERTY = VehiclePropertyType.FLOAT | 0x06;
    private static final int SOME_FLOAT_VEC_PROPERTY = VehiclePropertyType.FLOAT_VEC | 0x07;
    private static final int SOME_INT64_PROPERTY = VehiclePropertyType.INT64 | 0x10;
    private static final int SOME_INT64_VEC_PROPERTY = VehiclePropertyType.INT64_VEC | 0x11;
    private static final int CONTINUOUS_PROPERTY = VehiclePropertyType.INT32 | 0x12;
    private static final int SOME_WRITE_ONLY_ON_CHANGE_PROPERTY = 0x13;
    private static final int UNSUPPORTED_PROPERTY = -1;
    private static final int AREA_ID_1 = 1;
    private static final int AREA_ID_2 = 3;
    private static final int AREA_ID_3 = 5;
    private static final int[] AREA_IDS_LIST = {AREA_ID_1, AREA_ID_2, AREA_ID_3};

    private static final float ANY_SAMPLING_RATE_1 = 60f;
    private static final float ANY_SAMPLING_RATE_2 = 33f;

    @Mock private Context mContext;
    @Mock private PowerHalService mPowerHalService;
    @Mock private PropertyHalService mPropertyHalService;
    @Mock private InputHalService mInputHalService;
    @Mock private VmsHalService mVmsHalService;
    @Mock private UserHalService mUserHalService;
    @Mock private DiagnosticHalService mDiagnosticHalService;
    @Mock private ClusterHalService mClusterHalService;
    @Mock private TimeHalService mTimeHalService;
    @Mock private VehicleStub mVehicle;
    @Mock private VehicleStub.VehicleStubCallbackInterface mGetVehicleStubAsyncCallback;
    @Mock private VehicleStub.VehicleStubCallbackInterface mSetVehicleStubAsyncCallback;
    @Mock private VehicleStub.SubscriptionClient mSubscriptionClient;
    @Mock private FeatureFlags mFeatureFlags;

    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            VehicleHal.class.getSimpleName());
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());
    private final HalPropValueBuilder mPropValueBuilder = new HalPropValueBuilder(/*isAidl=*/true);
    private static final int REQUEST_ID_1 = 1;
    private static final int REQUEST_ID_2 = 1;
    private final HalPropValue mHalPropValue = mPropValueBuilder.build(HVAC_TEMPERATURE_SET, 0);
    private final AsyncGetSetRequest mGetVehicleRequest1 =
            new AsyncGetSetRequest(REQUEST_ID_1, mHalPropValue, /* timeoutInMs= */ 0);
    private final AsyncGetSetRequest mGetVehicleRequest2 =
            new AsyncGetSetRequest(REQUEST_ID_2, mHalPropValue, /* timeoutInMs= */ 0);
    private final AsyncGetSetRequest mSetVehicleRequest =
            new AsyncGetSetRequest(REQUEST_ID_1, mHalPropValue, /* timeoutInMs= */ 0);

    @Rule public final TestName mTestName = new TestName();

    private VehicleHal mVehicleHal;

    /** Hal services configurations */
    private final ArrayList<VehiclePropConfig> mConfigs = new ArrayList<>();

    private void initHalServices(VehiclePropConfig powerHalConfig,
            List<VehiclePropConfig> propertyHalConfigs) throws Exception {
        // Initialize PowerHAL service with a READ ON_CHANGE property
        when(mPowerHalService.getAllSupportedProperties()).thenReturn(
                new int[]{SOME_READ_ON_CHANGE_PROPERTY});
        mConfigs.add(powerHalConfig);

        // Initialize PropertyHAL service.
        int[] propIds = new int[propertyHalConfigs.size()];
        int i = 0;
        for (VehiclePropConfig propertyHalConfig : propertyHalConfigs) {
            mConfigs.add(propertyHalConfig);
            propIds[i] = propertyHalConfig.prop;
            i++;
        }
        when(mPropertyHalService.getAllSupportedProperties()).thenReturn(propIds);

        // Initialize the remaining services with empty properties
        when(mInputHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mVmsHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mUserHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mDiagnosticHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mTimeHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mClusterHalService.getAllSupportedProperties()).thenReturn(new int[0]);
    }

    private void initVehicleHal() throws Exception {
        var halPropConfigs = toHalPropConfigs(mConfigs);
        when(mVehicle.getAllPropConfigs()).thenReturn(halPropConfigs);
        mVehicleHal.fetchAllPropConfigs();

        when(mFeatureFlags.variableUpdateRate()).thenReturn(true);
        when(mFeatureFlags.subscriptionWithResolution()).thenReturn(true);
        mVehicleHal.setFeatureFlags(mFeatureFlags);

        mVehicleHal.priorityInit();
    }

    private static Answer<Void> checkConfigs(ArrayList<VehiclePropConfig> configs) {
        return new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                ArrayList<HalPropConfig> halConfigs =
                        (ArrayList<HalPropConfig>) invocation.getArguments()[0];
                ArrayList<VehiclePropConfig> aidlConfigs = new ArrayList<VehiclePropConfig>();
                for (HalPropConfig halConfig : halConfigs) {
                    aidlConfigs.add((VehiclePropConfig) (halConfig.toVehiclePropConfig()));
                }
                assertThat(configs).isEqualTo(aidlConfigs);
                return null;
            }
        };
    }

    private static HalPropConfig[] toHalPropConfigs(List<VehiclePropConfig> configs) {
        HalPropConfig[] halConfigs = new HalPropConfig[configs.size()];
        for (int i = 0; i < configs.size(); i++) {
            VehiclePropConfig config = configs.get(i);
            if (config.areaConfigs == null || config.areaConfigs.length == 0) {
                config.areaConfigs = new VehicleAreaConfig[AREA_IDS_LIST.length];

                for (int j = 0; j < AREA_IDS_LIST.length; j++) {
                    VehicleAreaConfig areaConfig = new VehicleAreaConfig();
                    areaConfig.areaId = AREA_IDS_LIST[j];
                    areaConfig.access = config.access;
                    config.areaConfigs[j] = areaConfig;
                }
            }

            halConfigs[i] = new AidlHalPropConfig(config);
        }
        return halConfigs;
    }

    private VehiclePropConfig getPowerHalConfig() {
        VehiclePropConfig powerHalConfig = new VehiclePropConfig();
        powerHalConfig.prop = SOME_READ_ON_CHANGE_PROPERTY;
        powerHalConfig.access = VehiclePropertyAccess.READ_WRITE;
        powerHalConfig.changeMode = VehiclePropertyChangeMode.ON_CHANGE;
        return powerHalConfig;
    }

    private VehiclePropConfig getStaticPropertyHalConfig() {
        VehiclePropConfig propertyHalConfig = new VehiclePropConfig();
        propertyHalConfig.prop = SOME_READ_WRITE_STATIC_PROPERTY;
        propertyHalConfig.access = VehiclePropertyAccess.READ_WRITE;
        propertyHalConfig.changeMode = VehiclePropertyChangeMode.STATIC;
        return propertyHalConfig;
    }

    private VehiclePropConfig getWriteOnlyPropertyHalConfig() {
        VehiclePropConfig writeOnlyConfig = new VehiclePropConfig();
        writeOnlyConfig.prop = SOME_WRITE_ONLY_ON_CHANGE_PROPERTY;
        writeOnlyConfig.access = VehiclePropertyAccess.WRITE;
        writeOnlyConfig.changeMode = VehiclePropertyChangeMode.ON_CHANGE;
        return writeOnlyConfig;
    }

    private VehiclePropConfig getContinuousPropertyHalConfig() {
        VehiclePropConfig propertyHalConfig = new VehiclePropConfig();
        propertyHalConfig.prop = CONTINUOUS_PROPERTY;
        propertyHalConfig.access = VehiclePropertyAccess.READ_WRITE;
        propertyHalConfig.changeMode = VehiclePropertyChangeMode.CONTINUOUS;
        return propertyHalConfig;
    }

    @Before
    public void setUp() throws Exception {
        when(mVehicle.getHalPropValueBuilder()).thenReturn(mPropValueBuilder);
        when(mVehicle.newSubscriptionClient(any())).thenReturn(mSubscriptionClient);

        mVehicleHal = new VehicleHal(mContext, mPowerHalService,
                mPropertyHalService, mInputHalService, mVmsHalService, mUserHalService,
                mDiagnosticHalService, mClusterHalService, mTimeHalService,
                mHandlerThread, mVehicle);

        mConfigs.clear();

        String methodName = mTestName.getMethodName();
        if (!methodName.endsWith("_skipSetupInit")) {
            VehiclePropConfig powerHalConfig = getPowerHalConfig();
            VehiclePropConfig writeOnlyConfig = getWriteOnlyPropertyHalConfig();
            VehiclePropConfig staticPropConfig = getStaticPropertyHalConfig();
            VehiclePropConfig continuousPropConfig = getContinuousPropertyHalConfig();

            initHalServices(powerHalConfig,
                    List.of(staticPropConfig, writeOnlyConfig, continuousPropConfig));
            initVehicleHal();
        }
    }

    @Test
    public void isPropertySubscribable_true() {
        VehiclePropConfig continuousPropConfig = new VehiclePropConfig();
        continuousPropConfig.prop = CONTINUOUS_PROPERTY;
        continuousPropConfig.access = VehiclePropertyAccess.READ_WRITE;
        continuousPropConfig.changeMode = VehiclePropertyChangeMode.CONTINUOUS;

        assertThat(VehicleHal.isPropertySubscribable(new AidlHalPropConfig(continuousPropConfig)))
                .isTrue();
    }

    @Test
    public void isPropertySubscribable_staticFalse() {
        VehiclePropConfig staticPropConfig = new VehiclePropConfig();
        staticPropConfig.prop = SOME_READ_WRITE_STATIC_PROPERTY;
        staticPropConfig.access = VehiclePropertyAccess.READ_WRITE;
        staticPropConfig.changeMode = VehiclePropertyChangeMode.STATIC;

        assertThat(VehicleHal.isPropertySubscribable(new AidlHalPropConfig(staticPropConfig)))
                .isFalse();
    }

    @Test
    public void isPropertySubscribable_nonReadableFalse() {
        VehiclePropConfig continuousPropConfig = new VehiclePropConfig();
        continuousPropConfig.prop = CONTINUOUS_PROPERTY;
        continuousPropConfig.access = VehiclePropertyAccess.WRITE;
        continuousPropConfig.changeMode = VehiclePropertyChangeMode.CONTINUOUS;

        assertThat(VehicleHal.isPropertySubscribable(new AidlHalPropConfig(continuousPropConfig)))
                .isFalse();
    }

    @Test
    public void testGetAsync() {
        mVehicleHal.getAsync(List.of(mGetVehicleRequest1), mGetVehicleStubAsyncCallback);

        ArgumentCaptor<List<AsyncGetSetRequest>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(mVehicle).getAsync(captor.capture(),
                any(VehicleStub.VehicleStubCallbackInterface.class));
        assertThat(captor.getValue().get(0).getServiceRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(captor.getValue().get(0).getHalPropValue()).isEqualTo(mHalPropValue);
    }

    @Test
    public void testGetAsync_multipleRequests() {
        List<AsyncGetSetRequest> getVehicleHalRequests = new ArrayList<>();
        getVehicleHalRequests.add(mGetVehicleRequest1);
        getVehicleHalRequests.add(mGetVehicleRequest2);

        mVehicleHal.getAsync(getVehicleHalRequests, mGetVehicleStubAsyncCallback);

        ArgumentCaptor<List<AsyncGetSetRequest>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(mVehicle).getAsync(captor.capture(),
                any(VehicleStub.VehicleStubCallbackInterface.class));
        assertThat(captor.getValue().get(0).getServiceRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(captor.getValue().get(0).getHalPropValue()).isEqualTo(mHalPropValue);
        assertThat(captor.getValue().get(1).getServiceRequestId()).isEqualTo(REQUEST_ID_2);
        assertThat(captor.getValue().get(1).getHalPropValue()).isEqualTo(mHalPropValue);
    }

    @Test
    public void testSetAsync() {
        mVehicleHal.setAsync(List.of(mSetVehicleRequest), mSetVehicleStubAsyncCallback);

        ArgumentCaptor<List<AsyncGetSetRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(mVehicle).setAsync(captor.capture(),
                any(VehicleStub.VehicleStubCallbackInterface.class));
        assertThat(captor.getValue().get(0).getServiceRequestId()).isEqualTo(REQUEST_ID_1);
        assertThat(captor.getValue().get(0).getHalPropValue()).isEqualTo(mHalPropValue);
    }

    @Test
    public void testInit_skipSetupInit() throws Exception {
        VehiclePropConfig powerHalConfig = getPowerHalConfig();
        VehiclePropConfig propertyHalConfig = getStaticPropertyHalConfig();

        // When takeProperties is called, verify the arguments. We cannot verify this afterwards
        // because the input arg is a reference that would be updated after the call. Mockito does
        // not do deep copy.
        doAnswer(checkConfigs(
                new ArrayList<VehiclePropConfig>(Arrays.asList(powerHalConfig))))
                .when(mPowerHalService).takeProperties(any());
        doAnswer(checkConfigs(
                new ArrayList<VehiclePropConfig>(Arrays.asList(propertyHalConfig))))
                .when(mPropertyHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mInputHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mVmsHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mUserHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mDiagnosticHalService).takeProperties(any());

        initHalServices(powerHalConfig, List.of(propertyHalConfig));
        initVehicleHal();

        verify(mPowerHalService).init();
        verify(mPropertyHalService).init();
        verify(mInputHalService).init();
        verify(mVmsHalService).init();
        verify(mUserHalService).init();
        verify(mDiagnosticHalService).init();
    }

    @Test
    public void testInitWith0SupportedProps_skipSetupInit() throws Exception {
        when(mPowerHalService.getAllSupportedProperties()).thenReturn(new int[]{});
        when(mPowerHalService.isSupportedProperty(eq(SOME_READ_ON_CHANGE_PROPERTY)))
                .thenReturn(true);
        when(mPowerHalService.isSupportedProperty(not(eq(SOME_READ_ON_CHANGE_PROPERTY))))
                .thenReturn(false);
        VehiclePropConfig powerHalConfig = getPowerHalConfig();
        mConfigs.add(powerHalConfig);
        assertThat(VehicleHal.isPropertySubscribable(new AidlHalPropConfig(powerHalConfig)))
                .isTrue();

        when(mPropertyHalService.getAllSupportedProperties()).thenReturn(new int[]{});
        when(mPropertyHalService.isSupportedProperty(eq(SOME_READ_WRITE_STATIC_PROPERTY)))
                .thenReturn(true);
        when(mPropertyHalService.isSupportedProperty(not(eq(SOME_READ_WRITE_STATIC_PROPERTY))))
                .thenReturn(false);
        VehiclePropConfig propertyHalConfig = getStaticPropertyHalConfig();
        mConfigs.add(propertyHalConfig);
        assertThat(VehicleHal.isPropertySubscribable(new AidlHalPropConfig(propertyHalConfig)))
                .isFalse();

        // Initialize the remaining services with empty properties
        when(mInputHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mVmsHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mUserHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mDiagnosticHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mTimeHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mClusterHalService.getAllSupportedProperties()).thenReturn(new int[0]);

        initVehicleHal();
    }

    @Test
    public void testInitTwice_skipSetupInit() throws Exception {
        initHalServices(getPowerHalConfig(), List.of(getStaticPropertyHalConfig()));
        initVehicleHal();
        mVehicleHal.priorityInit();

        // getAllPropConfigs should only be called once.
        verify(mVehicle, times(1)).getAllPropConfigs();
    }

    @Test
    public void testInitWithEmptyConfigs_skipSetupInit() throws Exception {
        // Initialize PowerHAL service with a READ_WRITE and ON_CHANGE property
        when(mPowerHalService.getAllSupportedProperties()).thenReturn(
                new int[]{SOME_READ_ON_CHANGE_PROPERTY});

        // Initialize PropertyHAL service with a READ_WRITE and STATIC property
        when(mPropertyHalService.getAllSupportedProperties()).thenReturn(
                new int[]{SOME_READ_WRITE_STATIC_PROPERTY});

        // Initialize the remaining services with empty properties
        when(mInputHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mVmsHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mUserHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mDiagnosticHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mTimeHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mClusterHalService.getAllSupportedProperties()).thenReturn(new int[0]);

        // Return empty prop configs.
        when(mVehicle.getAllPropConfigs()).thenReturn(new HalPropConfig[0]);

        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mPowerHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mPropertyHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mInputHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mVmsHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mUserHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mDiagnosticHalService).takeProperties(any());

        mVehicleHal.priorityInit();

        verify(mPowerHalService).init();
        verify(mPropertyHalService).init();
        verify(mInputHalService).init();
        verify(mVmsHalService).init();
        verify(mUserHalService).init();
        verify(mDiagnosticHalService).init();
    }

    @Test
    public void testInitWithNullConfigs_skipSetupInit() throws Exception {
        // Initialize PowerHAL service with a READ_WRITE and ON_CHANGE property
        when(mPowerHalService.getAllSupportedProperties()).thenReturn(
                new int[]{SOME_READ_ON_CHANGE_PROPERTY});

        // Initialize PropertyHAL service with a READ_WRITE and STATIC property
        when(mPropertyHalService.getAllSupportedProperties()).thenReturn(
                new int[]{SOME_READ_WRITE_STATIC_PROPERTY});

        // Initialize the remaining services with empty properties
        when(mInputHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mVmsHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mUserHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mDiagnosticHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mTimeHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mClusterHalService.getAllSupportedProperties()).thenReturn(new int[0]);

        // Return empty prop configs.
        when(mVehicle.getAllPropConfigs()).thenReturn(null);

        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mPowerHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mPropertyHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mInputHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mVmsHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mUserHalService).takeProperties(any());
        doAnswer(checkConfigs(new ArrayList<VehiclePropConfig>()))
                .when(mDiagnosticHalService).takeProperties(any());

        mVehicleHal.priorityInit();

        verify(mPowerHalService).init();
        verify(mPropertyHalService).init();
        verify(mInputHalService).init();
        verify(mVmsHalService).init();
        verify(mUserHalService).init();
        verify(mDiagnosticHalService).init();
    }

    @Test
    public void testInitGetAllProdConfigsException_skipSetupInit() throws Exception {
        // Initialize PowerHAL service with a READ_WRITE and ON_CHANGE property
        when(mPowerHalService.getAllSupportedProperties()).thenReturn(
                new int[]{SOME_READ_ON_CHANGE_PROPERTY});

        // Initialize PropertyHAL service with a READ_WRITE and STATIC property
        when(mPropertyHalService.getAllSupportedProperties()).thenReturn(
                new int[]{SOME_READ_WRITE_STATIC_PROPERTY});

        // Initialize the remaining services with empty properties
        when(mInputHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mVmsHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mUserHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mDiagnosticHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mTimeHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mClusterHalService.getAllSupportedProperties()).thenReturn(new int[0]);

        // Throw exception.
        when(mVehicle.getAllPropConfigs()).thenThrow(new RemoteException());

        assertThrows(RuntimeException.class, () -> mVehicleHal.priorityInit());
    }

    @Test
    public void testRelease() throws Exception {
        mVehicleHal.subscribeProperty(
                mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY, ANY_SAMPLING_RATE_1);

        mVehicleHal.release();

        verify(mPowerHalService).release();
        verify(mPropertyHalService).release();
        verify(mInputHalService).release();
        verify(mVmsHalService).release();
        verify(mUserHalService).release();
        verify(mDiagnosticHalService).release();
        verify(mSubscriptionClient).unsubscribe(SOME_READ_ON_CHANGE_PROPERTY);
    }

    @Test
    public void testReleaseUnsubscribeRemoteException() throws Exception {
        mVehicleHal.subscribeProperty(
                mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY, ANY_SAMPLING_RATE_1);
        // This exception should be captured into a warning.
        doThrow(new RemoteException()).when(mSubscriptionClient).unsubscribe(
                SOME_READ_ON_CHANGE_PROPERTY);

        mVehicleHal.release();

        verify(mPowerHalService).release();
        verify(mPropertyHalService).release();
        verify(mInputHalService).release();
        verify(mVmsHalService).release();
        verify(mUserHalService).release();
        verify(mDiagnosticHalService).release();
        verify(mSubscriptionClient).unsubscribe(SOME_READ_ON_CHANGE_PROPERTY);
    }

    @Test
    public void testGetters() {
        assertThat(mVehicleHal.getDiagnosticHal()).isEqualTo(mDiagnosticHalService);
        assertThat(mVehicleHal.getPowerHal()).isEqualTo(mPowerHalService);
        assertThat(mVehicleHal.getPropertyHal()).isEqualTo(mPropertyHalService);
        assertThat(mVehicleHal.getInputHal()).isEqualTo(mInputHalService);
        assertThat(mVehicleHal.getUserHal()).isEqualTo(mUserHalService);
        assertThat(mVehicleHal.getVmsHal()).isEqualTo(mVmsHalService);
        assertThat(mVehicleHal.getClusterHal()).isEqualTo(mClusterHalService);
        assertThat(mVehicleHal.getEvsHal()).isNotNull();
    }

    @Test
    public void testSubscribeProperty_registeringReadWriteAndOnChangeProperty() throws Exception {
        // Act
        mVehicleHal.subscribeProperty(
                mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY, ANY_SAMPLING_RATE_1);

        // Assert
        SubscribeOptions expectedOptions = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, AREA_IDS_LIST);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
    }

    @Test
    public void testSubscribeProperty_defaultRateDefaultFlag() throws Exception {
        mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);

        // Assert
        SubscribeOptions expectedOptions = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                0f, AREA_IDS_LIST);


        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
    }

    @Test
    public void testSubscribeProperty_defaultFlag() throws Exception {
        mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1);

        // Assert
        SubscribeOptions expectedOptions = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, AREA_IDS_LIST);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
    }

    @Test
    public void testSubScribeProperty_unownedProperty() throws Exception {
        // PropertyHalService does not own SOME_READ_ON_CHANGE_PROPERTY.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mVehicleHal.subscribeProperty(
                                mPropertyHalService,
                                SOME_READ_ON_CHANGE_PROPERTY,
                                ANY_SAMPLING_RATE_1));
    }

    @Test
    public void testSubScribeProperty_noConfigProperty() throws Exception {
        // Property UNSUPPORTED_PROPERTY does not have config.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mVehicleHal.subscribeProperty(
                                mPowerHalService, UNSUPPORTED_PROPERTY, ANY_SAMPLING_RATE_1));
    }

    @Test
    public void testSubscribeProperty_RemoteExceptionFromVhal() throws Exception {
        doThrow(new RemoteException()).when(mSubscriptionClient).subscribe(any());

        assertThrows(ServiceSpecificException.class, () ->
                mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY,
                        ANY_SAMPLING_RATE_1));
    }

    @Test
    public void testSubscribeProperty_ServiceSpecificExceptionFromVhal() throws Exception {
        doThrow(new ServiceSpecificException(0)).when(mSubscriptionClient).subscribe(any());

        assertThrows(ServiceSpecificException.class, () ->
                mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY,
                        ANY_SAMPLING_RATE_1));
    }

    @Test
    public void testSubscribeProperty_ServiceSpecificExceptionFromVhal_retry() throws Exception {
        doThrow(new ServiceSpecificException(0)).when(mSubscriptionClient).subscribe(any());

        assertThrows(ServiceSpecificException.class, () ->
                mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY,
                        ANY_SAMPLING_RATE_1));

        clearInvocations(mSubscriptionClient);
        doNothing().when(mSubscriptionClient).subscribe(any());

        mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1);

        SubscribeOptions expectedOptions = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, AREA_IDS_LIST);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
    }

    @Test
    public void testSubscribePropertySafe_ServiceSpecificExceptionFromVhal() throws Exception {
        doThrow(new ServiceSpecificException(0)).when(mSubscriptionClient).subscribe(any());

        // Exception should be handled inside subscribePropertySafe.
        mVehicleHal.subscribePropertySafe(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1);
    }

    @Test
    public void testSubscribeProperty_registeringStaticProperty() throws Exception {
        mVehicleHal.subscribeProperty(
                mPowerHalService, SOME_READ_WRITE_STATIC_PROPERTY, ANY_SAMPLING_RATE_1);

        verify(mSubscriptionClient, never()).subscribe(any());
    }

    @Test
    public void testSubscribeProperty_registeringWriteOnlyProperty_notAllowed() throws Exception {
        // SOME_WRITE_ONLY_ON_CHANGE_PROPERTY is owned by mPropertyHalService.
        assertThrows(IllegalArgumentException.class, () -> mVehicleHal.subscribeProperty(
                mPropertyHalService, SOME_WRITE_ONLY_ON_CHANGE_PROPERTY, ANY_SAMPLING_RATE_1));

        verify(mSubscriptionClient, never()).subscribe(any());
    }

    @Test
    public void testSubscribeProperty_subscribeSameSampleRate_ignored() throws Exception {
        mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1);

        verify(mSubscriptionClient).subscribe(any());
        clearInvocations(mSubscriptionClient);

        // Subscribe the same property with same sample rate must be ignored.
        mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1);

        verify(mSubscriptionClient, never()).subscribe(any());
    }

    @Test
    public void testSubscribeProperty_enableVur() throws Exception {
        int[] areaIds = new int[] {AREA_ID_1};
        HalSubscribeOptions option = new HalSubscribeOptions(CONTINUOUS_PROPERTY,
                areaIds, ANY_SAMPLING_RATE_1, /*enableVariableUpdateRate=*/ true);
        mVehicleHal.subscribeProperty(mPropertyHalService, List.of(option));

        SubscribeOptions expectedOptions = createSubscribeOptions(CONTINUOUS_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds, /*enableVur=*/ true);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
    }

    @Test
    public void testSubscribeProperty_enableVur_featureDisabled() throws Exception {
        when(mFeatureFlags.variableUpdateRate()).thenReturn(false);
        int[] areaIds = new int[] {AREA_ID_1};
        HalSubscribeOptions option = new HalSubscribeOptions(CONTINUOUS_PROPERTY,
                areaIds, ANY_SAMPLING_RATE_1, /*enableVariableUpdateRate=*/ true);
        mVehicleHal.subscribeProperty(mPropertyHalService, List.of(option));

        SubscribeOptions expectedOptions = createSubscribeOptions(CONTINUOUS_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds, /*enableVur=*/ false);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
    }

    @Test
    public void testSubscribeProperty_enableVur_vurChange() throws Exception {
        int[] areaIds = new int[] {AREA_ID_1};
        HalSubscribeOptions option = new HalSubscribeOptions(CONTINUOUS_PROPERTY,
                areaIds, ANY_SAMPLING_RATE_1, /*enableVariableUpdateRate=*/ true);
        mVehicleHal.subscribeProperty(mPropertyHalService, List.of(option));

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{createSubscribeOptions(
                CONTINUOUS_PROPERTY, ANY_SAMPLING_RATE_1, areaIds, /*enableVur=*/ true)}));

        HalSubscribeOptions option2 = new HalSubscribeOptions(CONTINUOUS_PROPERTY,
                areaIds, ANY_SAMPLING_RATE_1, /*enableVariableUpdateRate=*/ false);
        clearInvocations(mSubscriptionClient);
        mVehicleHal.subscribeProperty(mPropertyHalService, List.of(option2));

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{createSubscribeOptions(
                CONTINUOUS_PROPERTY, ANY_SAMPLING_RATE_1, areaIds, /*enableVur=*/ false)}));

        clearInvocations(mSubscriptionClient);
        // No option change.
        mVehicleHal.subscribeProperty(mPropertyHalService, List.of(option2));

        verify(mSubscriptionClient, never()).subscribe(any());
    }

    @Test
    public void testSubscribeProperty_enableVur_falseForOnChangeProperty() throws Exception {
        int[] areaIds = new int[] {AREA_ID_1};
        HalSubscribeOptions option = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds, ANY_SAMPLING_RATE_1, /*enableVariableUpdateRate=*/ true);
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(option));

        SubscribeOptions expectedOptions = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds, /*enableVur=*/ false);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
    }

    @Test
    public void testSubscribeProperty_withResolution() throws Exception {
        int[] areaIds = new int[] {AREA_ID_1};
        HalSubscribeOptions option = new HalSubscribeOptions(CONTINUOUS_PROPERTY,
                areaIds, ANY_SAMPLING_RATE_1, /*enableVariableUpdateRate=*/ true,
                /*resolution*/ 1.0f);
        mVehicleHal.subscribeProperty(mPropertyHalService, List.of(option));

        SubscribeOptions expectedOptions = createSubscribeOptions(CONTINUOUS_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds, /*enableVur=*/ true, /*resolution*/ 1.0f);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
    }

    @Test
    public void testSubscribeProperty_withResolution_featureDisabled() throws Exception {
        when(mFeatureFlags.subscriptionWithResolution()).thenReturn(false);
        int[] areaIds = new int[] {AREA_ID_1};
        HalSubscribeOptions option = new HalSubscribeOptions(CONTINUOUS_PROPERTY,
                areaIds, ANY_SAMPLING_RATE_1, /*enableVariableUpdateRate=*/ true,
                /*resolution*/ 1.0f);
        mVehicleHal.subscribeProperty(mPropertyHalService, List.of(option));

        SubscribeOptions expectedOptions = createSubscribeOptions(CONTINUOUS_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds, /*enableVur=*/ true, /*resolution*/ 0.0f);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
    }

    @Test
    public void testSubscribeProperty_withResolution_resolutionChange() throws Exception {
        int[] areaIds = new int[] {AREA_ID_1};
        HalSubscribeOptions option = new HalSubscribeOptions(CONTINUOUS_PROPERTY,
                areaIds, ANY_SAMPLING_RATE_1, /*enableVariableUpdateRate=*/ true,
                /*resolution*/ 1.0f);
        mVehicleHal.subscribeProperty(mPropertyHalService, List.of(option));

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{createSubscribeOptions(
                CONTINUOUS_PROPERTY, ANY_SAMPLING_RATE_1, areaIds, /*enableVur=*/ true,
                /*resolution*/ 1.0f)}));

        HalSubscribeOptions option2 = new HalSubscribeOptions(CONTINUOUS_PROPERTY,
                areaIds, ANY_SAMPLING_RATE_1, /*enableVariableUpdateRate=*/ true,
                /*resolution*/ 0.1f);
        clearInvocations(mSubscriptionClient);
        mVehicleHal.subscribeProperty(mPropertyHalService, List.of(option2));

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{createSubscribeOptions(
                CONTINUOUS_PROPERTY, ANY_SAMPLING_RATE_1, areaIds, /*enableVur=*/ true,
                /*resolution*/ 0.1f)}));

        clearInvocations(mSubscriptionClient);
        // No option change.
        mVehicleHal.subscribeProperty(mPropertyHalService, List.of(option2));

        verify(mSubscriptionClient, never()).subscribe(any());
    }

    @Test
    public void testSubscribeProperty_withResolution_zeroForOnChangeProperty() throws Exception {
        int[] areaIds = new int[] {AREA_ID_1};
        HalSubscribeOptions option = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds, ANY_SAMPLING_RATE_1, /*enableVariableUpdateRate=*/ true,
                /*resolution*/ 1.0f);
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(option));

        SubscribeOptions expectedOptions = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds, /*enableVur=*/ false, /*resolution*/ 0.0f);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
    }

    @Test
    public void testSubscribeProperty_subscribeWithAreaId() throws Exception {
        int[] areaIds = new int[] {AREA_ID_1};
        HalSubscribeOptions option = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds, ANY_SAMPLING_RATE_1);
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(option));

        SubscribeOptions expectedOptions = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
    }

    @Test
    public void testSubscribeProperty_withSameSampleRateDifferentArea() throws Exception {
        // Arrange
        int[] areaIds1 = new int[] {AREA_ID_1};
        int[] areaIds2 = new int[] {AREA_ID_2};
        HalSubscribeOptions options1 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1);
        HalSubscribeOptions options2 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds2, ANY_SAMPLING_RATE_1);

        // Act
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options1));
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options2));

        // Assert
        SubscribeOptions expectedOptions1 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds1);
        SubscribeOptions expectedOptions2 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds2);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions1}));
        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions2}));
    }

    @Test
    public void testSubscribeProperty_withSameSampleRateIntersectingAreas() throws Exception {
        // Arrange
        int[] areaIds1 = new int[] {AREA_ID_1, AREA_ID_2};
        int[] areaIds2 = new int[] {AREA_ID_2, AREA_ID_3};
        HalSubscribeOptions options1 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1);
        HalSubscribeOptions options2 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds2, ANY_SAMPLING_RATE_1);

        // Act
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options1));
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options2));

        // Assert
        SubscribeOptions expectedOptions1 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds1);
        SubscribeOptions expectedOptions2 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, new int[] {AREA_ID_3});

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions1}));
        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions2}));
    }

    @Test
    public void testSubscribeProperty_withSameSampleRateMoreAreas() throws Exception {
        // Arrange
        int[] areaIds1 = new int[] {AREA_ID_1};
        int[] areaIds2 = new int[] {AREA_ID_1, AREA_ID_2};
        HalSubscribeOptions options1 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1);
        HalSubscribeOptions options2 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds2, ANY_SAMPLING_RATE_1);

        // Act
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options1));
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options2));

        // Assert
        SubscribeOptions expectedOptions1 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds1);
        SubscribeOptions expectedOptions2 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, new int[]{AREA_ID_2});

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions1}));
        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions2}));
    }

    @Test
    public void testSubscribeProperty_withSameSampleRateSameArea() throws Exception {
        // Arrange
        int[] areaIds1 = new int[] {AREA_ID_1, AREA_ID_2};
        int[] areaIds2 = new int[] {AREA_ID_1};
        HalSubscribeOptions options1 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1);
        HalSubscribeOptions options2 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds2, ANY_SAMPLING_RATE_2);

        // Act
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options1));
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options2));

        // Assert
        SubscribeOptions expectedOptions1 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds1);
        SubscribeOptions expectedOptions2 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds2);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions1}));
        verify(mSubscriptionClient, never())
                .subscribe(eq(new SubscribeOptions[]{expectedOptions2}));
    }

    @Test
    public void testSubscribeProperty_precisionThresholdMetOverThreshold() throws Exception {
        // Arrange
        int[] areaIds1 = new int[] {AREA_ID_1, AREA_ID_2};
        HalSubscribeOptions options1 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1);

        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options1));
        reset(mSubscriptionClient);

        HalSubscribeOptions options2 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1 + 0.001f);

        // Assert
        // Sampling rate is 60.00099...
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options1, options2));

        verify(mSubscriptionClient, never()).subscribe(any());
    }

    @Test
    public void testSubscribeProperty_precisionThresholdMetUnderThreshold() throws Exception {
        // Arrange
        int[] areaIds1 = new int[] {AREA_ID_1, AREA_ID_2};
        HalSubscribeOptions options1 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1);

        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options1));
        reset(mSubscriptionClient);

        HalSubscribeOptions options2 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1 - 0.001f);
        // Assert
        // Sampling rate is 59.9990005
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options1, options2));

        verify(mSubscriptionClient, never()).subscribe(any());
    }

    @Test
    public void testSubscribeProperty_precisionThresholdNotMet() throws Exception {
        // Arrange
        int[] areaIds1 = new int[] {AREA_ID_1, AREA_ID_2};
        HalSubscribeOptions options1 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1);

        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options1));
        reset(mSubscriptionClient);

        HalSubscribeOptions options2 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1 + 0.0011f);

        // Assert
        // Sampling rate is 60.00109
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options2));

        verify(mSubscriptionClient, times(1)).subscribe(any());
    }

    @Test
    public void testSubscribeProperty_withDifferentSampleRateWithAreaId() throws Exception {
        // Arrange
        int[] areaIds1 = new int[] {AREA_ID_1, AREA_ID_2};
        int[] areaIds2 = new int[] {AREA_ID_1, AREA_ID_2};
        HalSubscribeOptions options1 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1);
        HalSubscribeOptions options2 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_2);

        // Act
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options1));
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options2));

        // Assert
        SubscribeOptions expectedOptions1 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds1);
        SubscribeOptions expectedOptions2 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_2, areaIds2);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions1}));
        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions2}));
    }

    @Test
    public void testSubscribeProperty_withMultipleOptions() throws Exception {
        // Arrange
        int[] areaIds1 = new int[] {AREA_ID_1, AREA_ID_2};
        int[] areaIds2 = new int[] {AREA_ID_3};

        HalSubscribeOptions options1 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds1, ANY_SAMPLING_RATE_1);
        HalSubscribeOptions options2 = new HalSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                areaIds2, ANY_SAMPLING_RATE_2);

        // Act
        mVehicleHal.subscribeProperty(mPowerHalService, List.of(options1, options2));

        // Assert
        SubscribeOptions expectedOptions1 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, areaIds1);
        SubscribeOptions expectedOptions2 = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_2, areaIds2);

        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions1,
                expectedOptions2}));
    }

    @Test
    public void testUnsubscribeProperty() throws Exception {
        // Arrange
        mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);

        //Act
        mVehicleHal.unsubscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);

        // Assert
        verify(mSubscriptionClient).unsubscribe(eq(SOME_READ_ON_CHANGE_PROPERTY));
    }

    @Test
    public void testUnsubscribeProperty_unsupportedProperty() throws Exception {
        //Act
        mVehicleHal.unsubscribeProperty(mPowerHalService, UNSUPPORTED_PROPERTY);

        // Assert
        verify(mSubscriptionClient, never()).unsubscribe(anyInt());
    }

    @Test
    public void testUnsubscribeProperty_unSubscribableProperty() throws Exception {
        //Act
        mVehicleHal.unsubscribeProperty(mPropertyHalService, SOME_READ_WRITE_STATIC_PROPERTY);

        // Assert
        verify(mSubscriptionClient, never()).unsubscribe(anyInt());
    }

    @Test
    public void testUnsubscribeProperty_remoteExceptionFromVhal() throws Exception {
        mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);
        doThrow(new RemoteException()).when(mSubscriptionClient).unsubscribe(anyInt());

        assertThrows(ServiceSpecificException.class, () ->
                mVehicleHal.unsubscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY));
    }

    @Test
    public void testUnubscribeProperty_ServiceSpecificExceptionFromVhal() throws Exception {
        mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);
        doThrow(new ServiceSpecificException(0)).when(mSubscriptionClient).unsubscribe(anyInt());

        assertThrows(ServiceSpecificException.class, () ->
                mVehicleHal.unsubscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY));
    }

    @Test
    public void testUnsubscribeProperty_ServiceSpecificExceptionFromVhal_retry() throws Exception {
        mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);
        doThrow(new ServiceSpecificException(0)).when(mSubscriptionClient).unsubscribe(anyInt());

        assertThrows(ServiceSpecificException.class, () ->
                mVehicleHal.unsubscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY));

        clearInvocations(mSubscriptionClient);
        doNothing().when(mSubscriptionClient).unsubscribe(anyInt());

        mVehicleHal.unsubscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);

        verify(mSubscriptionClient).unsubscribe(eq(SOME_READ_ON_CHANGE_PROPERTY));
    }

    @Test
    public void testUnsubscribePropertySafe_ServiceSpecificExceptionFromVhal() throws Exception {
        mVehicleHal.subscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);
        doThrow(new ServiceSpecificException(0)).when(mSubscriptionClient).unsubscribe(anyInt());

        // Exception should be handled inside unsubscribePropertySafe.
        mVehicleHal.unsubscribePropertySafe(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);
    }

    @Test
    public void testUnsubscribe_notSubscribed() throws Exception {
        //Act
        mVehicleHal.unsubscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);

        // Assert
        verify(mSubscriptionClient, never()).unsubscribe(anyInt());
    }

    @Test
    public void testUnsubscribe_afterSubscribeThenUnsubscribe() throws Exception {
        // Arrange
        mVehicleHal.subscribeProperty(
                mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY, ANY_SAMPLING_RATE_1);
        SubscribeOptions expectedOptions = createSubscribeOptions(SOME_READ_ON_CHANGE_PROPERTY,
                ANY_SAMPLING_RATE_1, AREA_IDS_LIST);
        verify(mSubscriptionClient).subscribe(eq(new SubscribeOptions[]{expectedOptions}));
        mVehicleHal.unsubscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);
        verify(mSubscriptionClient).unsubscribe(SOME_READ_ON_CHANGE_PROPERTY);
        clearInvocations(mSubscriptionClient);

        // Act
        mVehicleHal.unsubscribeProperty(mPowerHalService, SOME_READ_ON_CHANGE_PROPERTY);

        // Assert
        verify(mSubscriptionClient, never()).unsubscribe(anyInt());
    }

    @Test
    public void testOnPropertyEvent() {
        // Arrange
        List<HalPropValue> dispatchList = mock(List.class);
        when(mPowerHalService.getDispatchList()).thenReturn(dispatchList);

        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        ArrayList<HalPropValue> propValues = new ArrayList<>();
        propValues.add(propValue);

        // Act
        mVehicleHal.onPropertyEvent(propValues);

        // Assert
        verify(dispatchList, timeout(WAIT_TIMEOUT_MS)).add(propValue);
        verify(mPowerHalService, timeout(WAIT_TIMEOUT_MS)).onHalEvents(dispatchList);
        verify(dispatchList, timeout(WAIT_TIMEOUT_MS)).clear();
    }

    @Test
    public void testOnPropertyEvent_existingInfo() {
        // Arrange
        List<HalPropValue> dispatchList = mock(List.class);
        when(mPowerHalService.getDispatchList()).thenReturn(dispatchList);

        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        ArrayList<HalPropValue> propValues = new ArrayList<>();
        propValues.add(propValue);

        // Act
        mVehicleHal.onPropertyEvent(propValues);
        mVehicleHal.onPropertyEvent(propValues);
        // Wait for event to be handled.
        verify(dispatchList, timeout(WAIT_TIMEOUT_MS).times(2)).clear();

        // Assert
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        IndentingPrintWriter indentingPrintWriter = new IndentingPrintWriter(printWriter);
        mVehicleHal.dump(indentingPrintWriter);
        String actual = writer.toString();

        // There should be 2 events.
        int index = actual.indexOf("Property:");
        assertThat(index).isNotEqualTo(-1);
        assertThat(actual.indexOf("Property:", index + 1)).isNotEqualTo(-1);
    }

    @Test
    public void testOnPropertyEvent_unsupportedProperty() {
        HalPropValue propValue = mPropValueBuilder.build(UNSUPPORTED_PROPERTY,
                AREA_ID_1);
        ArrayList<HalPropValue> propValues = new ArrayList<>();
        propValues.add(propValue);

        mVehicleHal.onPropertyEvent(propValues);

        verify(mPowerHalService, after(100).never()).onHalEvents(any());
    }

    @Test
    public void testOnPropertySetError() {
        // Arrange
        ArrayList<VehiclePropError> errors = new ArrayList<VehiclePropError>();
        VehiclePropError error1 = new VehiclePropError();
        error1.propId = SOME_READ_ON_CHANGE_PROPERTY;
        error1.areaId = AREA_ID_1;
        error1.errorCode = CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN;
        errors.add(error1);
        VehiclePropError error2 = new VehiclePropError();
        error2.propId = SOME_READ_ON_CHANGE_PROPERTY;
        error2.areaId = 1;
        error2.errorCode = CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG;
        errors.add(error2);
        VehiclePropError error3 = new VehiclePropError();
        error3.propId = SOME_READ_WRITE_STATIC_PROPERTY;
        error3.areaId = AREA_ID_1;
        error3.errorCode = CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN;
        errors.add(error3);

        // Act
        mVehicleHal.onPropertySetError(errors);

        // Assert
        verify(mPowerHalService, timeout(WAIT_TIMEOUT_MS)).onPropertySetError(
                new ArrayList<VehiclePropError>(Arrays.asList(error1, error2)));
        verify(mPropertyHalService, timeout(WAIT_TIMEOUT_MS)).onPropertySetError(
                new ArrayList<VehiclePropError>(Arrays.asList(error3)));
    }

    @Test
    public void testOnPropertySetError_invalidProp() {
        // Arrange
        ArrayList<VehiclePropError> errors = new ArrayList<VehiclePropError>();
        VehiclePropError error = new VehiclePropError();
        error.propId = VehicleProperty.INVALID;
        error.errorCode = CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN;
        error.areaId = AREA_ID_1;
        errors.add(error);

        // Act
        mVehicleHal.onPropertySetError(errors);

        // Assert
        verify(mPowerHalService, after(100).never()).onPropertySetError(errors);
    }

    @Test
    public void testOnPropertySetError_unsupportedProp() {
        // Arrange
        ArrayList<VehiclePropError> errors = new ArrayList<VehiclePropError>();
        VehiclePropError error = new VehiclePropError();
        error.propId = UNSUPPORTED_PROPERTY;
        error.errorCode = CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN;
        error.areaId = AREA_ID_1;
        errors.add(error);

        // Act
        mVehicleHal.onPropertySetError(errors);

        // Assert
        verify(mPowerHalService, after(100).never()).onPropertySetError(errors);
    }

    @Test
    public void testInjectOnPropertySetError() {
        // Arrange
        ArrayList<VehiclePropError> errors = new ArrayList<VehiclePropError>();
        VehiclePropError error = new VehiclePropError();
        error.propId = SOME_READ_ON_CHANGE_PROPERTY;
        error.errorCode = CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN;
        error.areaId = AREA_ID_1;
        errors.add(error);

        // Act
        mHandler.post(() -> mVehicleHal.onPropertySetError(errors));
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert
        verify(mPowerHalService, timeout(WAIT_TIMEOUT_MS)).onPropertySetError(errors);
    }

    @Test
    public void testGetIfSupportedOrFail() throws Exception {
        // Arrange
        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        when(mVehicle.get(any(HalPropValue.class))).thenReturn(propValue);

        // Act
        HalPropValue actual = mVehicleHal.getIfSupportedOrFail(SOME_READ_ON_CHANGE_PROPERTY,
                /* numberOfRetries= */ 1);

        // Assert
        assertThat(actual).isEqualTo(propValue);
    }

    @Test
    public void testGetIfSupportedOrFail_unsupportedProperty() {
        HalPropValue actual = mVehicleHal.getIfSupportedOrFail(UNSUPPORTED_PROPERTY,
                /* numberOfRetries= */ 1);

        assertThat(actual).isNull();
    }

    @Test
    public void testGetIfSupportedOrFail_serviceSpecificException() throws Exception {
        when(mVehicle.get(any(HalPropValue.class))).thenThrow(
                new ServiceSpecificException(StatusCode.INTERNAL_ERROR));

        assertThrows(IllegalStateException.class, () -> mVehicleHal.getIfSupportedOrFail(
                SOME_READ_ON_CHANGE_PROPERTY, /* numberOfRetries= */ 1));
    }

    @Test
    public void testGetIfSupportedOrFail_serviceSpecificExceptionRetrySucceed() throws Exception {
        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                /* areaId */ 0);
        when(mVehicle.get(any(HalPropValue.class))).thenThrow(
                new ServiceSpecificException(StatusCode.TRY_AGAIN)).thenReturn(propValue);

        // Retry once.
        HalPropValue actual = mVehicleHal.getIfSupportedOrFail(SOME_READ_ON_CHANGE_PROPERTY,
                /* numberOfRetries= */ 2);

        assertThat(actual).isEqualTo(propValue);
        verify(mVehicle, times(2)).get(propValue);
    }

    @Test
    public void testGetIfSupportedOrFail_serviceSpecificExceptionRetryFailed() throws Exception {
        when(mVehicle.get(any(HalPropValue.class))).thenThrow(
                new ServiceSpecificException(StatusCode.TRY_AGAIN)).thenThrow(
                new ServiceSpecificException(StatusCode.TRY_AGAIN));

        assertThrows(IllegalStateException.class, () -> mVehicleHal.getIfSupportedOrFail(
                SOME_READ_ON_CHANGE_PROPERTY, /* numberOfRetries= */ 2));
    }

    @Test
    public void testGetIfSupportedOrFailForEarlyStage_skipSetupInit() throws Exception {
        // Skip setup init() because this function would be called before init() is called.
        // Initialize PowerHAL service with a READ_WRITE and ON_CHANGE property
        when(mPowerHalService.getAllSupportedProperties()).thenReturn(
                new int[]{SOME_READ_ON_CHANGE_PROPERTY});
        mConfigs.add(getPowerHalConfig());
        var halPropConfigs = toHalPropConfigs(mConfigs);
        when(mVehicle.getAllPropConfigs()).thenReturn(halPropConfigs);
        mVehicleHal.fetchAllPropConfigs();

        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        when(mVehicle.get(any(HalPropValue.class))).thenReturn(propValue);

        // Act
        HalPropValue actual = mVehicleHal.getIfSupportedOrFailForEarlyStage(
                SOME_READ_ON_CHANGE_PROPERTY, /* numberOfRetries= */ 1);

        // Assert
        assertThat(actual).isEqualTo(propValue);
    }

    @Test
    public void testGetClazz() throws Exception {
        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1, 0, 0, new int[]{1, 2}, new float[]{1.1f, 1.2f},
                new long[0], "test", new byte[]{0x00, 0x01});
        when(mVehicle.get(any(HalPropValue.class))).thenReturn(propValue);

        assertThat(mVehicleHal.<Integer>get(Integer.class, propValue)).isEqualTo(1);
        assertThat(mVehicleHal.<Integer>get(int.class, propValue)).isEqualTo(1);
        assertThat(mVehicleHal.<Boolean>get(Boolean.class, propValue)).isTrue();
        assertThat(mVehicleHal.<Boolean>get(boolean.class, propValue)).isTrue();
        assertThat(mVehicleHal.<Float>get(Float.class, propValue)).isEqualTo(1.1f);
        assertThat(mVehicleHal.<Float>get(float.class, propValue)).isEqualTo(1.1f);
        assertThat(mVehicleHal.<Float[]>get(Float[].class, propValue)).isEqualTo(
                new Float[]{1.1f, 1.2f});
        assertThat(mVehicleHal.<Integer[]>get(Integer[].class, propValue)).isEqualTo(
                new Integer[]{1, 2});
        assertThat(mVehicleHal.<float[]>get(float[].class, propValue)).isEqualTo(
                new float[]{1.1f, 1.2f});
        assertThat(mVehicleHal.<int[]>get(int[].class, propValue)).isEqualTo(
                new int[]{1, 2});
        assertThat(mVehicleHal.<byte[]>get(byte[].class, propValue)).isEqualTo(
                new byte[]{(byte) 0x00, (byte) 0x01});
        assertThat(mVehicleHal.<String>get(String.class, propValue)).isEqualTo("test");
    }

    @Test
    public void testGetClazz_unexpectedType() throws Exception {
        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1, "test");

        when(mVehicle.get(any(HalPropValue.class))).thenReturn(propValue);

        assertThrows(IllegalArgumentException.class, () -> mVehicleHal
                .<android.hardware.automotive.vehicle.V2_0.VehiclePropValue>get(
                        HalPropValue.class, propValue));
    }

    @Test
    public void testGetClazz_defaultArea() throws Exception {
        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                /* areaId */ 0, 1);
        when(mVehicle.get(any(HalPropValue.class))).thenReturn(propValue);

        Integer actual = mVehicleHal.get(Integer.class, SOME_READ_ON_CHANGE_PROPERTY);

        assertThat(actual).isEqualTo(1);
        HalPropValue requestProp = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                /* areaId */ 0);
        verify(mVehicle).get(requestProp);
    }

    @Test
    public void testGetWithRetry_retrySucceed() throws Exception {
        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1, 1);
        when(mVehicle.get(any(HalPropValue.class))).thenThrow(new ServiceSpecificException(
                StatusCode.TRY_AGAIN)).thenReturn(propValue);

        Integer actual = mVehicleHal.get(Integer.class, SOME_READ_ON_CHANGE_PROPERTY, AREA_ID_1);

        assertThat(actual).isEqualTo(1);
        HalPropValue requestProp = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        verify(mVehicle, times(2)).get(requestProp);
    }

    @Test
    public void testGetWithRetry_retryFailed() throws Exception {
        when(mVehicle.get(any(HalPropValue.class))).thenThrow(new ServiceSpecificException(
                StatusCode.TRY_AGAIN)).thenThrow(new ServiceSpecificException(
                StatusCode.TRY_AGAIN)).thenThrow(new ServiceSpecificException(
                StatusCode.TRY_AGAIN));
        mVehicleHal.setMaxDurationForRetryMs(200);
        mVehicleHal.setSleepBetweenRetryMs(100);

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () -> {
            mVehicleHal.get(Integer.class, SOME_READ_ON_CHANGE_PROPERTY);
        });
        assertThat(e.errorCode).isEqualTo(StatusCode.TRY_AGAIN);
    }

    @Test
    public void testGetWithRetry_nonRetriableError() throws Exception {
        when(mVehicle.get(any(HalPropValue.class))).thenThrow(new ServiceSpecificException(
                StatusCode.TRY_AGAIN)).thenThrow(new ServiceSpecificException(
                StatusCode.NOT_AVAILABLE));
        mVehicleHal.setMaxDurationForRetryMs(1000);
        mVehicleHal.setSleepBetweenRetryMs(100);

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () -> {
            mVehicleHal.get(Integer.class, SOME_READ_ON_CHANGE_PROPERTY);
        });
        assertThat(e.errorCode).isEqualTo(StatusCode.NOT_AVAILABLE);
        verify(mVehicle, times(2)).get(any());
    }

    @Test
    public void testGetWithRetry_IllegalArgumentException() throws Exception {
        when(mVehicle.get(any(HalPropValue.class))).thenThrow(new ServiceSpecificException(
                StatusCode.INVALID_ARG));

        assertThrows(IllegalArgumentException.class, () -> {
            mVehicleHal.get(Integer.class, SOME_READ_ON_CHANGE_PROPERTY);
        });
    }

    @Test
    public void testGetWithRetry_vhalReturnsNull() throws Exception {
        when(mVehicle.get(any(HalPropValue.class))).thenReturn(null);

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () -> {
            mVehicleHal.get(Integer.class, SOME_READ_ON_CHANGE_PROPERTY);
        });
        assertThat(e.errorCode).isEqualTo(StatusCode.NOT_AVAILABLE);
    }

    @Test
    public void testGetWithRetry_RemoteExceptionFromVhal() throws Exception {
        // RemoteException is retriable.
        when(mVehicle.get(any(HalPropValue.class))).thenThrow(new RemoteException())
                .thenThrow(new RemoteException())
                .thenThrow(new RemoteException());
        mVehicleHal.setMaxDurationForRetryMs(200);
        mVehicleHal.setSleepBetweenRetryMs(100);

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () -> {
            mVehicleHal.get(Integer.class, SOME_READ_ON_CHANGE_PROPERTY);
        });
        assertThat(e.errorCode).isEqualTo(StatusCode.TRY_AGAIN);
    }

    @Test
    public void testGetWithRetry_IllegalArgumentExceptionFromVhal() throws Exception {
        // IllegalArgumentException is not a retriable exception.
        when(mVehicle.get(any(HalPropValue.class))).thenThrow(new IllegalArgumentException());

        assertThrows(IllegalArgumentException.class, () -> {
            mVehicleHal.get(Integer.class, SOME_READ_ON_CHANGE_PROPERTY);
        });
    }

    // A test class to class protected method of VehicleHal.
    private class VehicleHalTestClass extends VehicleHal {
        VehicleHalTestClass(Context context,
                PowerHalService powerHal,
                PropertyHalService propertyHal,
                InputHalService inputHal,
                VmsHalService vmsHal,
                UserHalService userHal,
                DiagnosticHalService diagnosticHal,
                ClusterHalService clusterHalService,
                TimeHalService timeHalService,
                HandlerThread handlerThread,
                VehicleStub vehicleStub) {
            super(context, powerHal, propertyHal, inputHal, vmsHal, userHal, diagnosticHal,
                    clusterHalService, timeHalService, handlerThread, vehicleStub);
        }
    }

    @Test
    public void testSet() throws Exception {
        VehicleHalTestClass t = new VehicleHalTestClass(mContext, mPowerHalService,
                mPropertyHalService, mInputHalService, mVmsHalService, mUserHalService,
                mDiagnosticHalService, mClusterHalService, mTimeHalService,
                mHandlerThread, mVehicle);
        t.init();

        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        t.set(propValue);

        verify(mVehicle).set(propValue);
    }

    @Test
    public void testSetWithRetry_retrySucceed() throws Exception {
        VehicleHalTestClass t = new VehicleHalTestClass(mContext, mPowerHalService,
                mPropertyHalService, mInputHalService, mVmsHalService, mUserHalService,
                mDiagnosticHalService, mClusterHalService, mTimeHalService,
                mHandlerThread, mVehicle);
        t.init();
        doThrow(new ServiceSpecificException(StatusCode.TRY_AGAIN))
                .doNothing().when(mVehicle).set(any());

        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        t.setMaxDurationForRetryMs(1000);
        t.setSleepBetweenRetryMs(100);
        t.set(propValue);

        verify(mVehicle, times(2)).set(propValue);
    }

    @Test
    public void testSetWithRetry_retryFailed() throws Exception {
        VehicleHalTestClass t = new VehicleHalTestClass(mContext, mPowerHalService,
                mPropertyHalService, mInputHalService, mVmsHalService, mUserHalService,
                mDiagnosticHalService, mClusterHalService, mTimeHalService,
                mHandlerThread, mVehicle);
        t.init();
        doThrow(new ServiceSpecificException(StatusCode.TRY_AGAIN))
                .doThrow(new ServiceSpecificException(StatusCode.TRY_AGAIN))
                .doThrow(new ServiceSpecificException(StatusCode.TRY_AGAIN))
                .when(mVehicle).set(any());

        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        t.setMaxDurationForRetryMs(200);
        t.setSleepBetweenRetryMs(100);

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () -> {
            t.set(propValue);
        });
        assertThat(e.errorCode).isEqualTo(StatusCode.TRY_AGAIN);
    }

    @Test
    public void testSetWithRetry_nonRetriableError() throws Exception {
        VehicleHalTestClass t = new VehicleHalTestClass(mContext, mPowerHalService,
                mPropertyHalService, mInputHalService, mVmsHalService, mUserHalService,
                mDiagnosticHalService, mClusterHalService, mTimeHalService,
                mHandlerThread, mVehicle);
        t.init();
        doThrow(new ServiceSpecificException(StatusCode.TRY_AGAIN))
                .doThrow(new ServiceSpecificException(StatusCode.TRY_AGAIN))
                .doThrow(new ServiceSpecificException(StatusCode.INTERNAL_ERROR))
                .when(mVehicle).set(any());

        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        t.setMaxDurationForRetryMs(1000);
        t.setSleepBetweenRetryMs(100);

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () -> {
            t.set(propValue);
        });
        assertThat(e.errorCode).isEqualTo(StatusCode.INTERNAL_ERROR);
    }

    @Test
    public void testSetWithRetry_IllegalArgumentException() throws Exception {
        VehicleHalTestClass t = new VehicleHalTestClass(mContext, mPowerHalService,
                mPropertyHalService, mInputHalService, mVmsHalService, mUserHalService,
                mDiagnosticHalService, mClusterHalService, mTimeHalService,
                mHandlerThread, mVehicle);
        t.init();
        doThrow(new ServiceSpecificException(StatusCode.TRY_AGAIN))
                .doThrow(new ServiceSpecificException(StatusCode.TRY_AGAIN))
                .doThrow(new ServiceSpecificException(StatusCode.INVALID_ARG))
                .when(mVehicle).set(any());

        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        t.setMaxDurationForRetryMs(1000);
        t.setSleepBetweenRetryMs(100);

        assertThrows(IllegalArgumentException.class, () -> {
            t.set(propValue);
        });
    }

    @Test
    public void testSetWithRetry_RemoteExceptionFromVhal() throws Exception {
        // RemoteException is retriable.
        doThrow(new RemoteException()).doThrow(new RemoteException()).doThrow(new RemoteException())
                .when(mVehicle).set(any());
        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        mVehicleHal.setMaxDurationForRetryMs(200);
        mVehicleHal.setSleepBetweenRetryMs(100);

        ServiceSpecificException e = assertThrows(ServiceSpecificException.class, () -> {
            mVehicleHal.set(propValue);
        });
        assertThat(e.errorCode).isEqualTo(StatusCode.TRY_AGAIN);
    }

    @Test
    public void testSetWithRetry_IllegalArgumentExceptionFromVhal() throws Exception {
        // IllegalArgumentException is not a retriable exception.
        doThrow(new IllegalArgumentException()).when(mVehicle).set(any());
        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);

        assertThrows(IllegalArgumentException.class, () -> {
            mVehicleHal.set(propValue);
        });
    }

    @Test
    public void testSetter_bool() throws Exception {
        mVehicleHal.set(SOME_READ_ON_CHANGE_PROPERTY, AREA_ID_1).to(true);
        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1, new int[]{1});
        verify(mVehicle).set(propValue);
    }

    @Test
    public void testSetter_int() throws Exception {
        mVehicleHal.set(SOME_READ_ON_CHANGE_PROPERTY, AREA_ID_1).to(2);

        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1, new int[]{2});
        verify(mVehicle).set(propValue);
    }

    @Test
    public void testSetter_ints() throws Exception {
        mVehicleHal.set(SOME_READ_ON_CHANGE_PROPERTY, AREA_ID_1).to(new int[]{1, 2});

        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1, new int[]{1, 2});
        verify(mVehicle).set(propValue);
    }

    @Test
    public void testSetter_integers() throws Exception {
        mVehicleHal.set(SOME_READ_ON_CHANGE_PROPERTY, AREA_ID_1).to(Arrays.asList(1, 2));

        HalPropValue propValue = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1, new int[]{1, 2});
        verify(mVehicle).set(propValue);
    }

    // Testing dump methods

    @Test
    public void testDump() {
        // Arrange
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        IndentingPrintWriter indentingPrintWriter = new IndentingPrintWriter(printWriter);

        // Act
        mVehicleHal.dump(indentingPrintWriter);

        // Assert
        String actual = writer.toString();
        assertThat(actual).contains("Property Id: 1 // 0x1 name: 0x1, service: mPowerHalService");
        assertThat(actual).contains(
                "Property Id: 2 // 0x2 name: 0x2, service: mPropertyHalService");
    }

    @Test
    public void testDumpListHals() {
        // Arrange
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);

        // Act
        mVehicleHal.dumpListHals(printWriter);

        // Assert
        assertServiceNamesAreDumped(writer.toString());
    }

    @Test
    public void testDumpSpecificHals() {
        // Arrange
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);

        // Act
        mVehicleHal.dumpSpecificHals(printWriter, "PowerHalService", "UnsupportedHalService");

        // Assert
        String actual = writer.toString();
        assertThat(actual).contains("No HAL named UnsupportedHalService");
        verify(mPowerHalService).dump(any());
    }

    private void assertServiceNamesAreDumped(String actual) {
        assertThat(actual).contains("com.android.car.hal.PowerHalService");
        assertThat(actual).contains("com.android.car.hal.InputHalService");
        assertThat(actual).contains("com.android.car.hal.DiagnosticHalService");
        assertThat(actual).contains("com.android.car.hal.VmsHalService");
        assertThat(actual).contains("com.android.car.hal.UserHalService");
        assertThat(actual).contains("com.android.car.hal.PropertyHalService");
    }

    @Test
    public void testDumpPropertyValueByCommand_all() throws Exception {
        // Arrange
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);

        HalPropValue propValue = mPropValueBuilder.build(/*propId=*/0, /*areaId=*/0, "some_value");
        when(mVehicle.get(any(HalPropValue.class))).thenReturn(propValue);

        // Act
        mVehicleHal.dumpPropertyValueByCommand(printWriter, /* propId= */ -1, /* areaId= */-1);

        // Assert
        assertThat(writer.toString()).contains("string: some_value");
    }

    @Test
    public void testDumpPropertyValueByCommand_allAreaIdsNoAreaConfig() throws Exception {
        // Arrange
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);

        HalPropValue propValue = mPropValueBuilder.build(/*propId=*/0, /*areaId=*/0, "some_value");
        when(mVehicle.get(any(HalPropValue.class))).thenReturn(propValue);

        // Act
        mVehicleHal.dumpPropertyValueByCommand(printWriter, SOME_READ_ON_CHANGE_PROPERTY,
                /* areaId= */-1);

        // Assert
        assertThat(writer.toString()).contains("string: some_value");

        HalPropValue requestProp = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY,
                AREA_ID_1);
        verify(mVehicle).get(requestProp);
    }

    @Test
    public void testDumpPropertyValueByCommand_allAreaIdsWithAreaConfig_skipSetupInit()
            throws Exception {
        initHalServices(getPowerHalConfig(),
                List.of(getStaticPropertyHalConfig(), getContinuousPropertyHalConfig()));
        VehicleAreaConfig areaConfig = new VehicleAreaConfig();
        areaConfig.areaId = 123;
        areaConfig.access = mConfigs.get(0).access;
        mConfigs.get(0).areaConfigs =
                ArrayUtils.appendElement(
                        VehicleAreaConfig.class, mConfigs.get(0).areaConfigs, areaConfig, true);
        initVehicleHal();

        // Arrange
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);

        HalPropValue propValue = mPropValueBuilder.build(/*propId=*/0, /*areaId=*/0, "some_value");
        when(mVehicle.get(any(HalPropValue.class))).thenReturn(propValue);

        // Act
        mVehicleHal.dumpPropertyValueByCommand(printWriter, SOME_READ_ON_CHANGE_PROPERTY,
                /* areaId= */-1);

        // Assert
        assertThat(writer.toString()).contains("string: some_value");

        HalPropValue requestProp = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY, 123);
        verify(mVehicle).get(requestProp);
    }

    @Test
    public void testDumpPropertyValueByCommand_propArea() throws Exception {
        initHalServices(getPowerHalConfig(),
                List.of(getStaticPropertyHalConfig(), getContinuousPropertyHalConfig()));
        VehicleAreaConfig areaConfig = new VehicleAreaConfig();
        areaConfig.areaId = 123;
        areaConfig.access = mConfigs.get(0).access;
        mConfigs.get(0).areaConfigs =
                ArrayUtils.appendElement(
                        VehicleAreaConfig.class, mConfigs.get(0).areaConfigs, areaConfig, true);
        initVehicleHal();

        // Arrange
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);

        HalPropValue propValue = mPropValueBuilder.build(/*propId=*/0, /*areaId=*/0, "some_value");
        when(mVehicle.get(any(HalPropValue.class))).thenReturn(propValue);

        // Act
        mVehicleHal.dumpPropertyValueByCommand(printWriter, SOME_READ_ON_CHANGE_PROPERTY, 123);

        // Assert
        assertThat(writer.toString()).contains("string: some_value");

        HalPropValue requestProp = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY, 123);
        verify(mVehicle).get(requestProp);
    }

    @Test
    public void testDumpPropertyValueByCommand_byConfigNoAreaConfigsGetValueException()
            throws Exception {
        // Arrange
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);

        when(mVehicle.get(any(HalPropValue.class))).thenThrow(
                new ServiceSpecificException(StatusCode.INTERNAL_ERROR));

        // Act
        mVehicleHal.dumpPropertyValueByCommand(printWriter, SOME_READ_ON_CHANGE_PROPERTY,
                /* areaId= */-1);

        // Assert
        assertThat(writer.toString()).contains("Can not get property value");
    }

    @Test
    public void testDumpPropertyValueByCommand_byConfigWithAreaConfigsGetValueException()
             throws Exception {
        initHalServices(getPowerHalConfig(),
                List.of(getStaticPropertyHalConfig(), getContinuousPropertyHalConfig()));
        VehicleAreaConfig areaConfig = new VehicleAreaConfig();
        areaConfig.areaId = 123;
        areaConfig.access = mConfigs.get(0).access;
        mConfigs.get(0).areaConfigs =
                ArrayUtils.appendElement(
                        VehicleAreaConfig.class, mConfigs.get(0).areaConfigs, areaConfig, true);
        initVehicleHal();

        // Arrange
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);

        when(mVehicle.get(any(HalPropValue.class))).thenThrow(
                new ServiceSpecificException(StatusCode.INTERNAL_ERROR));

        // Act
        mVehicleHal.dumpPropertyValueByCommand(printWriter, SOME_READ_ON_CHANGE_PROPERTY,
                /* areaId= */-1);

        // Assert
        assertThat(writer.toString()).contains("Can not get property value");
        HalPropValue requestProp = mPropValueBuilder.build(SOME_READ_ON_CHANGE_PROPERTY, AREA_ID_1);
        verify(mVehicle).get(requestProp);
    }

    @Test
    public void testDumpPropertyValueByCommand_GetValueException() throws Exception {
        // Arrange
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);

        when(mVehicle.get(any(HalPropValue.class))).thenThrow(
                new ServiceSpecificException(StatusCode.INTERNAL_ERROR));

        // Act
        mVehicleHal.dumpPropertyValueByCommand(printWriter, SOME_READ_ON_CHANGE_PROPERTY,
                /* areaId= */ -1);

        // Assert
        assertThat(writer.toString()).contains("Can not get property value");
    }

    @Test
    public void testDumpPropertyValueByCommand_unsupportedProp() throws Exception {
        // Arrange
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);

        HalPropValue propValue = mPropValueBuilder.build(/*propId=*/0, /*areaId=*/0, "some_value");
        when(mVehicle.get(any(HalPropValue.class))).thenReturn(propValue);

        // Act
        // Note here we cannot use UNSUPPORTED_PROPERTY because its value -1 has special meaning
        // in this function call.
        mVehicleHal.dumpPropertyValueByCommand(printWriter, 0, /* areaId= */-1);

        // Assert
        assertThat(writer.toString()).contains("not supported by HAL");
    }

    @Test
    public void testDumpPropertyConfigs() {
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);

        mVehicleHal.dumpPropertyConfigs(printWriter, SOME_READ_ON_CHANGE_PROPERTY);

        assertThat(writer.toString()).contains("Property:INVALID_PROPERTY_ID(0x1)");
    }

    // Testing vehicle hal property getters

    @Test
    public void testGetForPropertyIdAndAreaId() throws Exception {
        // Arrange
        int propertyId = 123;  // Any property id
        int areaId = 456;  // Any area id
        HalPropValue expectResultValue = mPropValueBuilder.build(0, 0);
        when(mVehicle.get(any())).thenReturn(expectResultValue);

        // Act
        HalPropValue gotResultValue = mVehicleHal.get(propertyId, areaId);

        // Assert
        HalPropValue expectedPropValue = mPropValueBuilder.build(propertyId, areaId);
        verify(mVehicle).get(expectedPropValue);
        assertThat(gotResultValue).isEqualTo(expectResultValue);
    }

    @Test
    public void testGet_HalPropValue() throws Exception {
        // Arrange
        HalPropValue propValue = mPropValueBuilder.build(0, 0);
        when(mVehicle.get(any())).thenReturn(propValue);

        // Act
        HalPropValue result = mVehicleHal.get(propValue);

        // Assert
        verify(mVehicle).get(propValue);
        assertThat(result).isEqualTo(propValue);
    }

    // Make a copy of the prop value reference so that we could check them later.
    // This is necessary because the passed in value list would be cleared after the call.
    private static Answer<Void> storePropValues(List<HalPropValue> values) {
        return new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                values.addAll((List<HalPropValue>) invocation.getArguments()[0]);
                return null;
            }
        };
    }

    private void setupInjectEventTest(int property, List<HalPropValue> values)
            throws Exception {
        VehiclePropConfig config = new VehiclePropConfig();
        config.prop = property;
        mConfigs.add(config);

        when(mPowerHalService.getAllSupportedProperties()).thenReturn(new int[]{property});
        when(mPropertyHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mInputHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mVmsHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mUserHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mDiagnosticHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mTimeHalService.getAllSupportedProperties()).thenReturn(new int[0]);
        when(mClusterHalService.getAllSupportedProperties()).thenReturn(new int[0]);

        List<HalPropValue> dispatchList = new ArrayList<HalPropValue>();
        when(mPowerHalService.getDispatchList()).thenReturn(dispatchList);
        doAnswer(storePropValues(values)).when(mPowerHalService).onHalEvents(any());

        initVehicleHal();
    }

    @Test
    public void testInjectVhalEvent_intProperty_skipSetupInit() throws Exception {
        // Arrange
        List<HalPropValue> values = new ArrayList<HalPropValue>();
        setupInjectEventTest(SOME_INT32_PROPERTY, values);
        long time = SystemClock.elapsedRealtimeNanos();

        // Act
        mVehicleHal.injectVhalEvent(SOME_INT32_PROPERTY, AREA_ID_1, "1", 0);
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert
        assertThat(values.size()).isEqualTo(1);
        HalPropValue prop = values.get(0);
        assertThat(prop.getPropId()).isEqualTo(SOME_INT32_PROPERTY);
        assertThat(prop.getAreaId()).isEqualTo(AREA_ID_1);
        assertThat(prop.getInt32Value(0)).isEqualTo(1);
        assertThat(prop.getTimestamp()).isGreaterThan(time);
    }

    @Test
    public void testInjectVhalEvent_intVecProperty_skipSetupInit() throws Exception {
        // Arrange
        List<HalPropValue> values = new ArrayList<HalPropValue>();
        setupInjectEventTest(SOME_INT32_VEC_PROPERTY, values);
        long time = SystemClock.elapsedRealtimeNanos();

        // Act
        mVehicleHal.injectVhalEvent(SOME_INT32_VEC_PROPERTY, AREA_ID_1, "1,2", 0);
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert
        assertThat(values.size()).isEqualTo(1);
        HalPropValue prop = values.get(0);
        assertThat(prop.getPropId()).isEqualTo(SOME_INT32_VEC_PROPERTY);
        assertThat(prop.getAreaId()).isEqualTo(AREA_ID_1);
        assertThat(prop.getInt32Value(0)).isEqualTo(1);
        assertThat(prop.getInt32Value(1)).isEqualTo(2);
        assertThat(prop.getTimestamp()).isGreaterThan(time);
    }

    @Test
    public void testInjectVhalEvent_longProperty_skipSetupInit() throws Exception {
        // Arrange
        List<HalPropValue> values = new ArrayList<HalPropValue>();
        setupInjectEventTest(SOME_INT64_PROPERTY, values);
        long time = SystemClock.elapsedRealtimeNanos();

        // Act
        mVehicleHal.injectVhalEvent(SOME_INT64_PROPERTY, AREA_ID_1, "1", 0);
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert
        assertThat(values.size()).isEqualTo(1);
        HalPropValue prop = values.get(0);
        assertThat(prop.getPropId()).isEqualTo(SOME_INT64_PROPERTY);
        assertThat(prop.getAreaId()).isEqualTo(AREA_ID_1);
        assertThat(prop.getInt64Value(0)).isEqualTo(1);
        assertThat(prop.getTimestamp()).isGreaterThan(time);
    }

    @Test
    public void testInjectVhalEvent_longVecProperty_skipSetupInit() throws Exception {
        // Arrange
        List<HalPropValue> values = new ArrayList<HalPropValue>();
        setupInjectEventTest(SOME_INT64_VEC_PROPERTY, values);
        long time = SystemClock.elapsedRealtimeNanos();

        // Act
        mVehicleHal.injectVhalEvent(SOME_INT64_VEC_PROPERTY, AREA_ID_1, "1,2", 0);
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert
        assertThat(values.size()).isEqualTo(1);
        HalPropValue prop = values.get(0);
        assertThat(prop.getPropId()).isEqualTo(SOME_INT64_VEC_PROPERTY);
        assertThat(prop.getAreaId()).isEqualTo(AREA_ID_1);
        assertThat(prop.getInt64Value(0)).isEqualTo(1);
        assertThat(prop.getInt64Value(1)).isEqualTo(2);
        assertThat(prop.getTimestamp()).isGreaterThan(time);
    }

    @Test
    public void testInjectVhalEvent_bool_skipSetupInit() throws Exception {
        // Arrange
        List<HalPropValue> values = new ArrayList<HalPropValue>();
        setupInjectEventTest(SOME_BOOL_PROPERTY, values);
        long time = SystemClock.elapsedRealtimeNanos();

        // Act
        mVehicleHal.injectVhalEvent(SOME_BOOL_PROPERTY, AREA_ID_1, "True", 0);
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert
        assertThat(values.size()).isEqualTo(1);
        HalPropValue prop = values.get(0);
        assertThat(prop.getPropId()).isEqualTo(SOME_BOOL_PROPERTY);
        assertThat(prop.getAreaId()).isEqualTo(AREA_ID_1);
        assertThat(prop.getInt32Value(0)).isEqualTo(1);
        assertThat(prop.getTimestamp()).isGreaterThan(time);
    }

    @Test
    public void testInjectVhalEvent_floatProperty_skipSetupInit() throws Exception {
        // Arrange
        List<HalPropValue> values = new ArrayList<HalPropValue>();
        setupInjectEventTest(SOME_FLOAT_PROPERTY, values);
        long time = SystemClock.elapsedRealtimeNanos();

        // Act
        mVehicleHal.injectVhalEvent(SOME_FLOAT_PROPERTY, AREA_ID_1, "1.1", 0);
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert
        assertThat(values.size()).isEqualTo(1);
        HalPropValue prop = values.get(0);
        assertThat(prop.getPropId()).isEqualTo(SOME_FLOAT_PROPERTY);
        assertThat(prop.getAreaId()).isEqualTo(AREA_ID_1);
        assertThat(prop.getFloatValue(0)).isEqualTo(1.1f);
        assertThat(prop.getTimestamp()).isGreaterThan(time);
    }

    @Test
    public void testInjectVhalEvent_floatVecProperty_skipSetupInit() throws Exception {
        // Arrange
        List<HalPropValue> values = new ArrayList<HalPropValue>();
        setupInjectEventTest(SOME_FLOAT_VEC_PROPERTY, values);
        long time = SystemClock.elapsedRealtimeNanos();

        // Act
        mVehicleHal.injectVhalEvent(SOME_FLOAT_VEC_PROPERTY, AREA_ID_1, "1.1,1.2", 0);
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert
        assertThat(values.size()).isEqualTo(1);
        HalPropValue prop = values.get(0);
        assertThat(prop.getPropId()).isEqualTo(SOME_FLOAT_VEC_PROPERTY);
        assertThat(prop.getAreaId()).isEqualTo(AREA_ID_1);
        assertThat(prop.getFloatValue(0)).isEqualTo(1.1f);
        assertThat(prop.getFloatValue(1)).isEqualTo(1.2f);
        assertThat(prop.getTimestamp()).isGreaterThan(time);
    }

    @Test
    public void testInjectVhalEvent_unsupportedProperty_skipSetupInit() throws Exception {
        // Arrange
        List<HalPropValue> values = new ArrayList<HalPropValue>();
        // SOME_READ_ON_CHANGE_PROPERTY does not have a valid property type.
        setupInjectEventTest(SOME_READ_ON_CHANGE_PROPERTY, values);

        // Act
        mVehicleHal.injectVhalEvent(SOME_READ_ON_CHANGE_PROPERTY, AREA_ID_1, "1", 0);
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert
        verify(mPowerHalService, never()).onHalEvents(any());
    }

    @Test
    public void testInjectContinuousVhalEvent_skipSetupInit() throws Exception {
        // Arrange
        List<HalPropValue> values = new ArrayList<HalPropValue>();
        setupInjectEventTest(SOME_INT32_PROPERTY, values);

        // Act
        mVehicleHal.injectContinuousVhalEvent(SOME_INT32_PROPERTY, AREA_ID_1, "1", 10, 1);
        // Wait for injection to complete.
        SystemClock.sleep(1000);
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert

        // Should be called multiple times, might be less than 10 times, so make it 5 to be safe.
        assertThat(values.size()).isGreaterThan(5);
    }

    @Test
    public void testInjectContinuousVhalEvent_unsupportedProp_skipSetupInit() throws Exception {
        // Arrange
        List<HalPropValue> values = new ArrayList<HalPropValue>();
        // SOME_READ_ON_CHANGE_PROPERTY does not have a valid property type.
        setupInjectEventTest(SOME_READ_ON_CHANGE_PROPERTY, values);

        // Act
        mVehicleHal.injectContinuousVhalEvent(
                SOME_READ_ON_CHANGE_PROPERTY, AREA_ID_1, "1", 10, 1);
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert
        verify(mPowerHalService, never()).onHalEvents(any());
    }

    @Test
    public void testInjectContinuousVhalEvent_invalidSampleRate_skipSetupInit() throws Exception {
        // Arrange
        List<HalPropValue> values = new ArrayList<HalPropValue>();
        setupInjectEventTest(SOME_INT32_PROPERTY, values);

        // Act
        mVehicleHal.injectContinuousVhalEvent(SOME_INT32_PROPERTY, AREA_ID_1, "1", -1, 1);
        CarServiceUtils.runOnLooperSync(mHandlerThread.getLooper(), () -> {});

        // Assert
        verify(mPowerHalService, never()).onHalEvents(any());
    }

    @Test
    public void testCancelRequests() throws Exception {
        List<Integer> requestIds = mock(List.class);

        mVehicleHal.cancelRequests(requestIds);

        verify(mVehicle).cancelRequests(requestIds);
    }

    @Test
    public void testIsAidlVhal() {
        when(mVehicle.isAidlVhal()).thenReturn(true);

        assertWithMessage("Enabled aidl vhal").that(mVehicleHal.isAidlVhal()).isTrue();
    }

    @Test
    public void testIsFakeModeEnabled() {
        when(mVehicle.isFakeModeEnabled()).thenReturn(true);

        assertWithMessage("Fake Mode Enabled").that(mVehicleHal.isFakeModeEnabled())
                .isTrue();
    }

    @Test
    public void testSetPropertyFromCommandBoolean() throws Exception {
        mVehicleHal.setPropertyFromCommand(SOME_BOOL_PROPERTY, AREA_ID_1, "true", null);
        ArgumentCaptor<HalPropValue> captor =
                ArgumentCaptor.forClass(HalPropValue.class);

        verify(mVehicle).set(captor.capture());
        HalPropValue halPropValue = captor.getValue();
        expectWithMessage("Boolean property Id").that(halPropValue.getPropId())
                .isEqualTo(SOME_BOOL_PROPERTY);
        expectWithMessage("Global area Id").that(halPropValue.getAreaId())
                .isEqualTo(AREA_ID_1);
        expectWithMessage("Boolean property value").that(halPropValue.getInt32Value(0))
                .isEqualTo(1);
    }

    @Test
    public void testSetPropertyFromCommandInt32() throws Exception {
        mVehicleHal.setPropertyFromCommand(SOME_INT32_PROPERTY, AREA_ID_1, "55", null);
        ArgumentCaptor<HalPropValue> captor =
                ArgumentCaptor.forClass(HalPropValue.class);

        verify(mVehicle).set(captor.capture());
        HalPropValue halPropValue = captor.getValue();
        expectWithMessage("Int32 property Id").that(halPropValue.getPropId())
                .isEqualTo(SOME_INT32_PROPERTY);
        expectWithMessage("Global area Id").that(halPropValue.getAreaId())
                .isEqualTo(AREA_ID_1);
        expectWithMessage("Int32 property value").that(halPropValue.getInt32Value(0))
                .isEqualTo(55);
    }

    @Test
    public void testSetPropertyFromCommandInt64() throws Exception {
        mVehicleHal.setPropertyFromCommand(SOME_INT64_PROPERTY, AREA_ID_1,
                Long.toString((long) Integer.MAX_VALUE + 1) , null);
        ArgumentCaptor<HalPropValue> captor =
                ArgumentCaptor.forClass(HalPropValue.class);

        verify(mVehicle).set(captor.capture());
        HalPropValue halPropValue = captor.getValue();
        expectWithMessage("Int64 property Id").that(halPropValue.getPropId())
                .isEqualTo(SOME_INT64_PROPERTY);
        expectWithMessage("Global area Id").that(halPropValue.getAreaId())
                .isEqualTo(AREA_ID_1);
        expectWithMessage("Int64 property value").that(halPropValue.getInt64Value(0))
                .isEqualTo((long) Integer.MAX_VALUE + 1);
    }

    @Test
    public void testSetPropertyFromCommandFloat() throws Exception {
        mVehicleHal.setPropertyFromCommand(SOME_FLOAT_PROPERTY, AREA_ID_1,
                "555.55f" , null);
        ArgumentCaptor<HalPropValue> captor =
                ArgumentCaptor.forClass(HalPropValue.class);

        verify(mVehicle).set(captor.capture());
        HalPropValue halPropValue = captor.getValue();
        expectWithMessage("Float property value").that(halPropValue.getPropId())
                .isEqualTo(SOME_FLOAT_PROPERTY);
        expectWithMessage("Global area Id").that(halPropValue.getAreaId())
                .isEqualTo(AREA_ID_1);
        expectWithMessage("Float property value").that(halPropValue.getFloatValue(0))
                .isEqualTo(555.55f);
    }

    @Test
    public void testSetPropertyFromCommandMixedTypes_throwsIllegalArgument() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                mVehicleHal.setPropertyFromCommand(SOME_FLOAT_VEC_PROPERTY
                        | SOME_INT64_VEC_PROPERTY, AREA_ID_1, "6234" , null));

        assertWithMessage("Multiple property types").that(thrown).hasMessageThat()
                .contains("Unsupported property type: property");
    }

    private SubscribeOptions createSubscribeOptions(int propId, float sampleRateHz, int[] areaIds) {
        return createSubscribeOptions(propId, sampleRateHz, areaIds, /*enableVur=*/ false,
                /*resolution*/ 0.0f);
    }

    private SubscribeOptions createSubscribeOptions(int propId, float sampleRateHz, int[] areaIds,
            boolean enableVur) {
        return createSubscribeOptions(propId, sampleRateHz, areaIds, enableVur,
                /*resolution*/ 0.0f);
    }

    private SubscribeOptions createSubscribeOptions(int propId, float sampleRateHz, int[] areaIds,
            boolean enableVur, float resolution) {
        SubscribeOptions opts = new SubscribeOptions();
        opts.propId = propId;
        opts.sampleRate = sampleRateHz;
        opts.areaIds = areaIds;
        opts.enableVariableUpdateRate = enableVur;
        opts.resolution = resolution;
        return opts;
    }
}
