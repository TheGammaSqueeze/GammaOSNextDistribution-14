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

package com.android.car.telemetry.publisher;

import static android.car.hardware.property.CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AreaIdConfig;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.car.telemetry.TelemetryProto;
import android.os.Looper;
import android.os.PersistableBundle;

import com.android.car.CarPropertyService;
import com.android.car.internal.property.CarPropertyConfigList;
import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.car.test.FakeHandlerWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class VehiclePropertyPublisherTest {
    private static final int PROP_STRING_ID = 0x00100000;
    private static final int PROP_BOOLEAN_ID = 0x00200000;
    private static final int PROP_INT_ID = 0x00400000;
    private static final int PROP_INT_ID_2 = 0x00400001;
    private static final int PROP_INT_VEC_ID = 0x00410000;
    private static final int PROP_LONG_ID = 0x00500000;
    private static final int PROP_LONG_VEC_ID = 0x00510000;
    private static final int PROP_FLOAT_ID = 0x00600000;
    private static final int PROP_FLOAT_VEC_ID = 0x00610000;
    private static final int PROP_BYTES_ID = 0x00700000;
    private static final int PROP_MIXED_ID = 0x00e00000;
    private static final int AREA_ID = 20;
    private static final int STATUS = 0;
    private static final float PROP_READ_RATE = 0.0f;
    private static final CarPropertyEvent PROP_STRING_EVENT =
            new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                    new CarPropertyValue<>(PROP_STRING_ID, AREA_ID, "hi"));
    private static final CarPropertyEvent PROP_BOOLEAN_EVENT =
            new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                    new CarPropertyValue<>(PROP_BOOLEAN_ID, AREA_ID, true));
    private static final CarPropertyEvent PROP_INT_EVENT =
            new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                    new CarPropertyValue<>(PROP_INT_ID, AREA_ID, 1));
    private static final CarPropertyEvent PROP_INT_VEC_EVENT =
            new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                    new CarPropertyValue<>(PROP_INT_VEC_ID, AREA_ID, new Integer[] {1, 2}));
    private static final CarPropertyEvent PROP_LONG_EVENT =
            new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                    new CarPropertyValue<>(PROP_LONG_ID, AREA_ID, 10L));
    private static final CarPropertyEvent PROP_LONG_VEC_EVENT =
            new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                    new CarPropertyValue<>(PROP_LONG_VEC_ID, AREA_ID, new Long[] {10L, 20L}));
    private static final CarPropertyEvent PROP_FLOAT_EVENT =
            new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                    new CarPropertyValue<>(PROP_FLOAT_ID, AREA_ID, 1f));
    private static final CarPropertyEvent PROP_FLOAT_VEC_EVENT =
            new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                    new CarPropertyValue<>(PROP_FLOAT_VEC_ID, AREA_ID, new Float[] {1f, 2f}));
    private static final CarPropertyEvent PROP_BYTES_EVENT =
            new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                    new CarPropertyValue<>(PROP_BYTES_ID, AREA_ID,
                            new byte[] {(byte) 1, (byte) 2}));
    private static final CarPropertyEvent PROP_MIXED_EVENT =
            new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                    new CarPropertyValue<>(PROP_MIXED_ID, AREA_ID, new Object[] {
                            "test",
                            (Boolean) true,
                            (Integer) 1,
                            (Integer) 2,
                            (Integer) 3,
                            (Integer) 4,
                            (Long) 2L,
                            (Long) 5L,
                            (Long) 6L,
                            (Float) 3f,
                            (Float) 7f,
                            (Float) 8f,
                            (byte) 5,
                            (byte) 6
                    }));

    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_STRING =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                            .setReadRate(PROP_READ_RATE)
                            .setVehiclePropertyId(PROP_STRING_ID))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_BOOLEAN =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                            .setReadRate(PROP_READ_RATE)
                            .setVehiclePropertyId(PROP_BOOLEAN_ID))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_INT =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                            .setReadRate(PROP_READ_RATE)
                            .setVehiclePropertyId(PROP_INT_ID))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_INT_VEC =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                            .setReadRate(PROP_READ_RATE)
                            .setVehiclePropertyId(PROP_INT_VEC_ID))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_LONG =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                            .setReadRate(PROP_READ_RATE)
                            .setVehiclePropertyId(PROP_LONG_ID))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_LONG_VEC =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                            .setReadRate(PROP_READ_RATE)
                            .setVehiclePropertyId(PROP_LONG_VEC_ID))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_FLOAT =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                            .setReadRate(PROP_READ_RATE)
                            .setVehiclePropertyId(PROP_FLOAT_ID))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_FLOAT_VEC =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                            .setReadRate(PROP_READ_RATE)
                            .setVehiclePropertyId(PROP_FLOAT_VEC_ID))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_BYTES =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                            .setReadRate(PROP_READ_RATE)
                            .setVehiclePropertyId(PROP_BYTES_ID))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_MIXED =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                            .setReadRate(PROP_READ_RATE)
                            .setVehiclePropertyId(PROP_MIXED_ID))
                    .build();
    private static final TelemetryProto.Publisher PUBLISHER_PARAMS_INVALID =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                            .setReadRate(PROP_READ_RATE)
                            .setVehiclePropertyId(-200))
                    .build();

    // CarPropertyConfigs for mMockCarPropertyService.
    private static final CarPropertyConfig<Integer> PROP_STRING_CONFIG =
            CarPropertyConfig.newBuilder(Integer.class, PROP_STRING_ID, AREA_ID)
                    .setAccess(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                    .addAreaIdConfig(new AreaIdConfig.Builder(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ, /* areaId */ 0).build())
                    .build();
    private static final CarPropertyConfig<Integer> PROP_BOOLEAN_CONFIG =
            CarPropertyConfig.newBuilder(Integer.class, PROP_BOOLEAN_ID, AREA_ID)
                    .setAccess(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                    .addAreaIdConfig(new AreaIdConfig.Builder(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ, /* areaId */ 0).build())
                    .build();
    private static final CarPropertyConfig<Integer> PROP_INT_CONFIG =
            CarPropertyConfig.newBuilder(Integer.class, PROP_INT_ID, AREA_ID)
                    .setAccess(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                    .addAreaIdConfig(new AreaIdConfig.Builder(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ, /* areaId */ 0).build())
                    .build();
    private static final CarPropertyConfig<Integer> PROP_INT_VEC_CONFIG =
            CarPropertyConfig.newBuilder(Integer.class, PROP_INT_VEC_ID, AREA_ID)
                    .setAccess(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                    .addAreaIdConfig(new AreaIdConfig.Builder(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ, /* areaId */ 0).build())
                    .build();
    private static final CarPropertyConfig<Integer> PROP_LONG_CONFIG =
            CarPropertyConfig.newBuilder(Integer.class, PROP_LONG_ID, AREA_ID)
                    .setAccess(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                    .addAreaIdConfig(new AreaIdConfig.Builder(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ, /* areaId */ 0).build())
                    .build();
    private static final CarPropertyConfig<Integer> PROP_LONG_VEC_CONFIG =
            CarPropertyConfig.newBuilder(Integer.class, PROP_LONG_VEC_ID, AREA_ID)
                    .setAccess(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                    .addAreaIdConfig(new AreaIdConfig.Builder(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ, /* areaId */ 0).build())
                    .build();
    private static final CarPropertyConfig<Integer> PROP_FLOAT_CONFIG =
            CarPropertyConfig.newBuilder(Integer.class, PROP_FLOAT_ID, AREA_ID)
                    .setAccess(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                    .addAreaIdConfig(new AreaIdConfig.Builder(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ, /* areaId */ 0).build())
                    .build();
    private static final CarPropertyConfig<Integer> PROP_FLOAT_VEC_CONFIG =
            CarPropertyConfig.newBuilder(Integer.class, PROP_FLOAT_VEC_ID, AREA_ID)
                    .setAccess(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                    .addAreaIdConfig(new AreaIdConfig.Builder(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ, /* areaId */ 0).build())
                    .build();
    private static final CarPropertyConfig<Integer> PROP_BYTES_CONFIG =
            CarPropertyConfig.newBuilder(Integer.class, PROP_BYTES_ID, AREA_ID)
                    .setAccess(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                    .addAreaIdConfig(new AreaIdConfig.Builder(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ, /* areaId */ 0).build())
                    .build();
    private static final CarPropertyConfig<Integer> PROP_MIXED_CONFIG =
            CarPropertyConfig.newBuilder(Integer.class, PROP_MIXED_ID, AREA_ID)
                    .setAccess(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                    .addAreaIdConfig(new AreaIdConfig.Builder(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ, /* areaId */ 0).build())
                    .setConfigArray(new ArrayList<Integer>(
                            Arrays.asList(1, 1, 1, 3, 1, 2, 1, 2, 2))).build();
    private static final CarPropertyConfig<Integer> PROP_CONFIG_2_WRITE_ONLY =
            CarPropertyConfig.newBuilder(Integer.class, PROP_INT_ID_2, AREA_ID)
                    .setAccess(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE)
                    .addAreaIdConfig(new AreaIdConfig.Builder(
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE, /* areaId */ 0)
                            .build()).build();

    private final FakeHandlerWrapper mFakeHandlerWrapper =
            new FakeHandlerWrapper(Looper.getMainLooper(), FakeHandlerWrapper.Mode.QUEUEING);
    private final FakePublisherListener mFakePublisherListener = new FakePublisherListener();

    @Mock
    private DataSubscriber mMockStringDataSubscriber;
    @Mock
    private DataSubscriber mMockBoolDataSubscriber;
    @Mock
    private DataSubscriber mMockIntDataSubscriber;
    @Mock
    private DataSubscriber mMockIntVecDataSubscriber;
    @Mock
    private DataSubscriber mMockLongDataSubscriber;
    @Mock
    private DataSubscriber mMockLongVecDataSubscriber;
    @Mock
    private DataSubscriber mMockFloatDataSubscriber;
    @Mock
    private DataSubscriber mMockFloatVecDataSubscriber;
    @Mock
    private DataSubscriber mMockBytesDataSubscriber;
    @Mock
    private DataSubscriber mMockMixedDataSubscriber;
    @Mock
    private CarPropertyService mMockCarPropertyService;

    @Captor
    private ArgumentCaptor<ICarPropertyEventListener> mCarPropertyCallbackCaptor;
    @Captor
    private ArgumentCaptor<PersistableBundle> mBundleCaptor;
    @Captor
    private ArgumentCaptor<List<PersistableBundle>> mBundleListCaptor;

    private VehiclePropertyPublisher mVehiclePropertyPublisher;

    @Before
    public void setUp() {
        when(mMockStringDataSubscriber.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_STRING);
        when(mMockBoolDataSubscriber.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_BOOLEAN);
        when(mMockIntDataSubscriber.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_INT);
        when(mMockIntVecDataSubscriber.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_INT_VEC);
        when(mMockLongDataSubscriber.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_LONG);
        when(mMockLongVecDataSubscriber.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_LONG_VEC);
        when(mMockFloatDataSubscriber.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_FLOAT);
        when(mMockFloatVecDataSubscriber.getPublisherParam())
            .thenReturn(PUBLISHER_PARAMS_FLOAT_VEC);
        when(mMockBytesDataSubscriber.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_BYTES);
        when(mMockMixedDataSubscriber.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_MIXED);
        when(mMockCarPropertyService.getPropertyList())
                .thenReturn(new CarPropertyConfigList(List.of(
                        PROP_STRING_CONFIG,
                        PROP_BOOLEAN_CONFIG,
                        PROP_INT_CONFIG,
                        PROP_INT_VEC_CONFIG,
                        PROP_LONG_CONFIG,
                        PROP_LONG_VEC_CONFIG,
                        PROP_FLOAT_CONFIG,
                        PROP_FLOAT_VEC_CONFIG,
                        PROP_BYTES_CONFIG,
                        PROP_MIXED_CONFIG,
                        PROP_CONFIG_2_WRITE_ONLY)));
        mVehiclePropertyPublisher = new VehiclePropertyPublisher(
                mMockCarPropertyService,
                mFakePublisherListener,
                mFakeHandlerWrapper.getMockHandler());
    }

    @Test
    public void testAddDataSubscriber_registersNewCallback() {
        mVehiclePropertyPublisher.addDataSubscriber(mMockIntDataSubscriber);

        verify(mMockCarPropertyService).registerListener(
                eq(PROP_INT_ID), eq(PROP_READ_RATE), any());
        assertThat(mVehiclePropertyPublisher.hasDataSubscriber(mMockIntDataSubscriber)).isTrue();
    }

    @Test
    public void testAddDataSubscriber_withSamePropertyId_registersSingleListener() {
        DataSubscriber subscriber2 = mock(DataSubscriber.class);
        when(subscriber2.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_INT);

        mVehiclePropertyPublisher.addDataSubscriber(mMockIntDataSubscriber);
        mVehiclePropertyPublisher.addDataSubscriber(subscriber2);

        verify(mMockCarPropertyService, times(1))
                .registerListener(eq(PROP_INT_ID), eq(PROP_READ_RATE), any());
        assertThat(mVehiclePropertyPublisher.hasDataSubscriber(mMockIntDataSubscriber)).isTrue();
        assertThat(mVehiclePropertyPublisher.hasDataSubscriber(subscriber2)).isTrue();
    }

    @Test
    public void testAddDataSubscriber_failsIfInvalidCarProperty() {
        DataSubscriber invalidDataSubscriber = mock(DataSubscriber.class);
        when(invalidDataSubscriber.getPublisherParam()).thenReturn(
                TelemetryProto.Publisher.newBuilder()
                        .setVehicleProperty(TelemetryProto.VehiclePropertyPublisher.newBuilder()
                                .setReadRate(PROP_READ_RATE)
                                .setVehiclePropertyId(PROP_INT_ID_2))
                        .build());

        Throwable error = assertThrows(IllegalArgumentException.class,
                () -> mVehiclePropertyPublisher.addDataSubscriber(invalidDataSubscriber));

        assertThat(error).hasMessageThat().contains("No access.");
        assertThat(mVehiclePropertyPublisher.hasDataSubscriber(mMockIntDataSubscriber)).isFalse();
    }

    @Test
    public void testAddDataSubscriber_failsIfNoReadAccess() {
        DataSubscriber invalidDataSubscriber = mock(DataSubscriber.class);
        when(invalidDataSubscriber.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_INVALID);

        Throwable error = assertThrows(IllegalArgumentException.class,
                () -> mVehiclePropertyPublisher.addDataSubscriber(invalidDataSubscriber));

        assertThat(error).hasMessageThat().contains("not found");
        assertThat(mVehiclePropertyPublisher.hasDataSubscriber(mMockIntDataSubscriber)).isFalse();
    }

    @Test
    public void testRemoveDataSubscriber_succeeds() {
        mVehiclePropertyPublisher.addDataSubscriber(mMockIntDataSubscriber);

        mVehiclePropertyPublisher.removeDataSubscriber(mMockIntDataSubscriber);

        verify(mMockCarPropertyService, times(1)).unregisterListener(eq(PROP_INT_ID), any());
        assertThat(mVehiclePropertyPublisher.hasDataSubscriber(mMockIntDataSubscriber)).isFalse();
    }

    @Test
    public void testRemoveDataSubscriber_ignoresIfNotFound() {
        mVehiclePropertyPublisher.removeDataSubscriber(mMockIntDataSubscriber);
    }

    @Test
    public void testRemoveAllDataSubscribers_succeeds() {
        DataSubscriber subscriber2 = mock(DataSubscriber.class);
        when(subscriber2.getPublisherParam()).thenReturn(PUBLISHER_PARAMS_INT);
        mVehiclePropertyPublisher.addDataSubscriber(mMockIntDataSubscriber);
        mVehiclePropertyPublisher.addDataSubscriber(subscriber2);

        mVehiclePropertyPublisher.removeAllDataSubscribers();

        assertThat(mVehiclePropertyPublisher.hasDataSubscriber(mMockIntDataSubscriber)).isFalse();
        assertThat(mVehiclePropertyPublisher.hasDataSubscriber(subscriber2)).isFalse();
        verify(mMockCarPropertyService, times(1)).unregisterListener(eq(PROP_INT_ID), any());
    }

    @Test
    public void testOnNewCarPropertyEvent_pushesValueToDataSubscriber() throws Exception {
        doNothing().when(mMockCarPropertyService).registerListener(
                anyInt(), anyFloat(), mCarPropertyCallbackCaptor.capture());
        mVehiclePropertyPublisher.setBatchIntervalMillis(0L);
        mVehiclePropertyPublisher.addDataSubscriber(mMockIntDataSubscriber);

        mCarPropertyCallbackCaptor.getValue().onEvent(Collections.singletonList(PROP_INT_EVENT));
        mFakeHandlerWrapper.dispatchQueuedMessages();  // Dispatch immediately posted messages
        mFakeHandlerWrapper.dispatchQueuedMessages();  // Dispatch delay posted messages

        verify(mMockIntDataSubscriber).push(mBundleListCaptor.capture());
        assertThat(mBundleListCaptor.getValue().size()).isEqualTo(1);
        assertThat(mBundleListCaptor.getValue().get(0)
                .getInt(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_INT)).isEqualTo(1);
    }

    @Test
    public void testOnNewCarPropertyEvent_parsesValueCorrectly() throws Exception {
        doNothing().when(mMockCarPropertyService).registerListener(
                anyInt(), anyFloat(), mCarPropertyCallbackCaptor.capture());
        mVehiclePropertyPublisher.setBatchIntervalMillis(1L);
        mVehiclePropertyPublisher.addDataSubscriber(mMockStringDataSubscriber);
        mVehiclePropertyPublisher.addDataSubscriber(mMockBoolDataSubscriber);
        mVehiclePropertyPublisher.addDataSubscriber(mMockIntDataSubscriber);
        mVehiclePropertyPublisher.addDataSubscriber(mMockIntVecDataSubscriber);
        mVehiclePropertyPublisher.addDataSubscriber(mMockLongDataSubscriber);
        mVehiclePropertyPublisher.addDataSubscriber(mMockLongVecDataSubscriber);
        mVehiclePropertyPublisher.addDataSubscriber(mMockFloatDataSubscriber);
        mVehiclePropertyPublisher.addDataSubscriber(mMockFloatVecDataSubscriber);
        mVehiclePropertyPublisher.addDataSubscriber(mMockBytesDataSubscriber);
        mVehiclePropertyPublisher.addDataSubscriber(mMockMixedDataSubscriber);
        ICarPropertyEventListener eventListener = mCarPropertyCallbackCaptor.getValue();

        eventListener.onEvent(Collections.singletonList(PROP_STRING_EVENT));
        eventListener.onEvent(Collections.singletonList(PROP_BOOLEAN_EVENT));
        eventListener.onEvent(Collections.singletonList(PROP_INT_EVENT));
        eventListener.onEvent(Collections.singletonList(PROP_INT_VEC_EVENT));
        eventListener.onEvent(Collections.singletonList(PROP_LONG_EVENT));
        eventListener.onEvent(Collections.singletonList(PROP_LONG_VEC_EVENT));
        eventListener.onEvent(Collections.singletonList(PROP_FLOAT_EVENT));
        eventListener.onEvent(Collections.singletonList(PROP_FLOAT_VEC_EVENT));
        eventListener.onEvent(Collections.singletonList(PROP_BYTES_EVENT));
        eventListener.onEvent(Collections.singletonList(PROP_MIXED_EVENT));
        mFakeHandlerWrapper.dispatchQueuedMessages();  // Dispatch immediately posted messages
        mFakeHandlerWrapper.dispatchQueuedMessages();  // Dispatch delay posted messages

        verify(mMockStringDataSubscriber).push(mBundleListCaptor.capture());
        assertThat(mBundleListCaptor.getValue().get(0)
                .getString(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_STRING))
            .isEqualTo("hi");

        verify(mMockBoolDataSubscriber).push(mBundleListCaptor.capture());
        assertThat(mBundleListCaptor.getValue().get(0)
                .getBoolean(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_BOOLEAN)).isTrue();

        verify(mMockIntDataSubscriber).push(mBundleListCaptor.capture());
        assertThat(mBundleListCaptor.getValue().get(0)
                .getInt(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_INT)).isEqualTo(1);

        verify(mMockIntVecDataSubscriber).push(mBundleListCaptor.capture());
        assertThat(mBundleListCaptor.getValue().get(0)
                .getIntArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_INT_ARRAY))
            .isEqualTo(new int[] {1, 2});

        verify(mMockLongDataSubscriber).push(mBundleListCaptor.capture());
        assertThat(mBundleListCaptor.getValue().get(0)
                .getLong(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_LONG)).isEqualTo(10L);

        verify(mMockLongVecDataSubscriber).push(mBundleListCaptor.capture());
        assertThat(mBundleListCaptor.getValue().get(0)
                .getLongArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_LONG_ARRAY))
            .isEqualTo(new long[] {10L, 20L});

        verify(mMockFloatDataSubscriber).push(mBundleListCaptor.capture());
        assertThat(mBundleListCaptor.getValue().get(0)
                .getDouble(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_FLOAT)).isEqualTo(1d);

        verify(mMockFloatVecDataSubscriber).push(mBundleListCaptor.capture());
        assertThat(mBundleListCaptor.getValue().get(0)
                .getDoubleArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_FLOAT_ARRAY))
            .isEqualTo(new double[] {1d, 2d});

        verify(mMockBytesDataSubscriber).push(mBundleListCaptor.capture());
        assertThat(mBundleListCaptor.getValue().get(0)
                .getString(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_BYTE_ARRAY))
            .isEqualTo(new String(new byte[] {(byte) 1, (byte) 2}, StandardCharsets.UTF_8));

        verify(mMockMixedDataSubscriber).push(mBundleListCaptor.capture());
        assertThat(mBundleListCaptor.getValue().get(0)
                .getString(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_STRING))
            .isEqualTo("test");
        assertThat(mBundleListCaptor.getValue().get(0)
                .getBoolean(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_BOOLEAN)).isTrue();
        assertThat(mBundleListCaptor.getValue().get(0)
                .getInt(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_INT)).isEqualTo(1);
        assertThat(mBundleListCaptor.getValue().get(0)
                .getIntArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_INT_ARRAY))
            .isEqualTo(new int[] {2, 3, 4});
        assertThat(mBundleListCaptor.getValue().get(0)
                .getLong(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_LONG)).isEqualTo(2L);
        assertThat(mBundleListCaptor.getValue().get(0)
                .getLongArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_LONG_ARRAY))
            .isEqualTo(new long[] {5L, 6L});
        assertThat(mBundleListCaptor.getValue().get(0)
                .getDouble(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_FLOAT)).isEqualTo(3d);
        assertThat(mBundleListCaptor.getValue().get(0)
                .getDoubleArray(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_FLOAT_ARRAY))
            .isEqualTo(new double[] {7d, 8d});
        assertThat(mBundleListCaptor.getValue().get(0)
                .getString(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_BYTE_ARRAY))
            .isEqualTo(new String(new byte[] {(byte) 5, (byte) 6}, StandardCharsets.UTF_8));
    }

    @Test
    public void testOnNewCarPropertyEvents_batchIsPushedAfterDelay() throws Exception {
        doNothing().when(mMockCarPropertyService).registerListener(
                anyInt(), anyFloat(), mCarPropertyCallbackCaptor.capture());
        mVehiclePropertyPublisher.setBatchIntervalMillis(10L);  // Batch interval 10 milliseconds
        mVehiclePropertyPublisher.addDataSubscriber(mMockStringDataSubscriber);
        ICarPropertyEventListener eventListener = mCarPropertyCallbackCaptor.getValue();
        CarPropertyEvent propEvent1 = new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                new CarPropertyValue<>(PROP_STRING_ID, AREA_ID, STATUS, /* timestamp= */ 0L,
                        "first"));
        CarPropertyEvent propEvent2 = new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                new CarPropertyValue<>(PROP_STRING_ID, AREA_ID, STATUS, /* timestamp= */ 5L,
                        "second"));
        CarPropertyEvent propEvent3 = new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                new CarPropertyValue<>(PROP_STRING_ID, AREA_ID, STATUS, /* timestamp= */ 7L,
                        "third"));

        eventListener.onEvent(Collections.singletonList(propEvent1));
        eventListener.onEvent(Collections.singletonList(propEvent2));
        eventListener.onEvent(Collections.singletonList(propEvent3));
        mFakeHandlerWrapper.dispatchQueuedMessages();  // Dispatch immediately posted messages
        mFakeHandlerWrapper.dispatchQueuedMessages();  // Dispatch delay posted messages

        verify(mMockStringDataSubscriber).push(mBundleListCaptor.capture());
        List<PersistableBundle> bundleList = mBundleListCaptor.getValue();
        assertThat(bundleList.size()).isEqualTo(3);
        assertThat(bundleList.get(0).getString(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_STRING))
            .isEqualTo("first");
        assertThat(bundleList.get(1).getString(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_STRING))
            .isEqualTo("second");
        assertThat(bundleList.get(2).getString(Constants.VEHICLE_PROPERTY_BUNDLE_KEY_STRING))
            .isEqualTo("third");
    }
}
