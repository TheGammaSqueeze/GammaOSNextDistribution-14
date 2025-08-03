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
package com.android.car.property;

import static android.car.hardware.property.CarPropertyEvent.PROPERTY_EVENT_ERROR;
import static android.car.hardware.property.CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE;
import static android.car.hardware.property.CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArraySet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class CarPropertyServiceClientUnitTest {
    private static final int FIRST_PROPERTY_ID = 1234;
    private static final int SECOND_PROPERTY_ID = 1235;
    private static final int AREA_ID_1 = 908;
    private static final int AREA_ID_2 = 304;
    private static final int[] REGISTERED_AREA_IDS = new int[] {AREA_ID_1, AREA_ID_2};
    private static final float FIRST_UPDATE_RATE_HZ = 1F;
    private static final float SECOND_BIGGER_UPDATE_RATE_HZ = 2F;
    private static final float SECOND_SMALLER_UPDATE_RATE_HZ = 0.5F;
    private static final float RESOLUTION = 10.0F;
    private static final long TIMESTAMP_NANOS = Duration.ofSeconds(1).toNanos();
    private static final long FRESH_TIMESTAMP_NANOS = Duration.ofSeconds(2).toNanos();
    private static final long ALMOST_FRESH_TIMESTAMP_NANOS = Duration.ofMillis(1899).toNanos();
    private static final long STALE_TIMESTAMP_NANOS = Duration.ofMillis(500).toNanos();
    private static final Integer INTEGER_VALUE_1 = 8438;
    private static final Integer INTEGER_VALUE_2 = 4834;
    private static final Integer SANITIZED_INTEGER_VALUE_1 = 8440;
    private static final Integer SIMILAR_TO_INTEGER_VALUE_1 = 8442;
    private static final CarPropertyValue<Integer> GOOD_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS, INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> SANITIZED_GOOD_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    SANITIZED_INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> SIMILAR_GOOD_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS,
                    SIMILAR_TO_INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> SECOND_GOOD_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(SECOND_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS, INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> FRESH_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, FRESH_TIMESTAMP_NANOS,
                    INTEGER_VALUE_2);
    private static final CarPropertyValue<Integer> FRESH_CAR_PROPERTY_SAME_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, FRESH_TIMESTAMP_NANOS,
                    INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> ALMOST_FRESH_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, ALMOST_FRESH_TIMESTAMP_NANOS,
                    INTEGER_VALUE_2);
    private static final CarPropertyValue<Integer> STALE_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, STALE_TIMESTAMP_NANOS,
                    INTEGER_VALUE_2);
    private static final CarPropertyValue<Integer>
            STALE_CAR_PROPERTY_VALUE_WITH_DIFFERENT_PROPERTY_ID = new CarPropertyValue<>(
                    SECOND_PROPERTY_ID, AREA_ID_1, STALE_TIMESTAMP_NANOS, INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> STALE_CAR_PROPERTY_VALUE_WITH_DIFFERENT_AREA_ID =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_2, STALE_TIMESTAMP_NANOS,
                    INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> ERROR_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS, null);

    private CarPropertyServiceClient mCarPropertyServiceClient;

    @Mock
    private ICarPropertyEventListener mICarPropertyEventListener;
    @Mock
    private IBinder mListenerBinder;
    @Mock
    private CarPropertyServiceClient.UnregisterCallback mUnregisterCallback;
    @Captor
    private ArgumentCaptor<List<CarPropertyEvent>> mCarPropertyEventListCaptor;

    @Before
    public void setup() {
        when(mICarPropertyEventListener.asBinder()).thenReturn(mListenerBinder);
        mCarPropertyServiceClient = new CarPropertyServiceClient(mICarPropertyEventListener,
                mUnregisterCallback);
    }

    // Most of the logic in CarPropertyServiceClient is already tested in
    // CarPropertyEventCallbackControllerUnitTest since they all use CarPropertyEventController.
    @Test
    public void testAddContinuousProperty_getUpdateRateHzOnePropertyAdded() {
        mCarPropertyServiceClient.addContinuousProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS,
                FIRST_UPDATE_RATE_HZ, /* enableVur= */ false, /* resolution */ 0.0f);

        assertThat(mCarPropertyServiceClient.getUpdateRateHz(FIRST_PROPERTY_ID, AREA_ID_1))
                .isEqualTo(FIRST_UPDATE_RATE_HZ);
        assertThat(mCarPropertyServiceClient.getUpdateRateHz(FIRST_PROPERTY_ID, AREA_ID_2))
                .isEqualTo(FIRST_UPDATE_RATE_HZ);
    }

    @Test
    public void testAddContinuousProperty_getLatestRateIfSamePropertyAddedTwice() {
        mCarPropertyServiceClient.addContinuousProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS,
                FIRST_UPDATE_RATE_HZ, /* enableVur= */ false, /* resolution */ 0.0f);
        mCarPropertyServiceClient.addContinuousProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS,
                SECOND_BIGGER_UPDATE_RATE_HZ, /* enableVur= */ false, /* resolution */ 0.0f);

        assertThat(mCarPropertyServiceClient.getUpdateRateHz(FIRST_PROPERTY_ID, AREA_ID_1))
                .isEqualTo(SECOND_BIGGER_UPDATE_RATE_HZ);
        assertThat(mCarPropertyServiceClient.getUpdateRateHz(FIRST_PROPERTY_ID, AREA_ID_2))
                .isEqualTo(SECOND_BIGGER_UPDATE_RATE_HZ);

        mCarPropertyServiceClient.addContinuousProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS,
                SECOND_SMALLER_UPDATE_RATE_HZ, /* enableVur= */ false, /* resolution */ 0.0f);

        assertThat(mCarPropertyServiceClient.getUpdateRateHz(FIRST_PROPERTY_ID, AREA_ID_1))
                .isEqualTo(SECOND_SMALLER_UPDATE_RATE_HZ);
        assertThat(mCarPropertyServiceClient.getUpdateRateHz(FIRST_PROPERTY_ID, AREA_ID_2))
                .isEqualTo(SECOND_SMALLER_UPDATE_RATE_HZ);
    }

    @Test
    public void testAddContinuousProperty_rateNotFoundIfBinderDead() {
        mCarPropertyServiceClient.binderDied();
        mCarPropertyServiceClient.addContinuousProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS,
                FIRST_UPDATE_RATE_HZ, /* enableVur= */ false, /* resolution */ 0.0f);

        assertThat(mCarPropertyServiceClient.getUpdateRateHz(FIRST_PROPERTY_ID, AREA_ID_1))
                .isEqualTo(0f);
        assertThat(mCarPropertyServiceClient.getUpdateRateHz(FIRST_PROPERTY_ID, AREA_ID_2))
                .isEqualTo(0f);
    }

    @Test
    public void testGetUpdateRateHz_rateNotFoundNoPropertyAdded() {
        assertThat(mCarPropertyServiceClient.getUpdateRateHz(FIRST_PROPERTY_ID, AREA_ID_1))
                .isEqualTo(0f);
        assertThat(mCarPropertyServiceClient.getUpdateRateHz(FIRST_PROPERTY_ID, AREA_ID_2))
                .isEqualTo(0f);
    }

    @Test
    public void testRemove_multipleProperties_doesNothingIfNoPropertyAdded() {
        assertThat(mCarPropertyServiceClient.remove(
                new ArraySet<>(Set.of(FIRST_PROPERTY_ID)))).isTrue();
        verify(mListenerBinder).unlinkToDeath(any(), anyInt());
    }

    @Test
    public void testRemove_multipleProperties_onePropertyRemoved() {
        mCarPropertyServiceClient.addContinuousProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS,
                FIRST_UPDATE_RATE_HZ, /* enableVur= */ false, /* resolution */ 0.0f);
        mCarPropertyServiceClient.addContinuousProperty(SECOND_PROPERTY_ID, REGISTERED_AREA_IDS,
                FIRST_UPDATE_RATE_HZ, /* enableVur= */ false, /* resolution */ 0.0f);

        assertThat(mCarPropertyServiceClient.remove(
                new ArraySet<>(Set.of(FIRST_PROPERTY_ID)))).isFalse();
    }

    @Test
    public void testOnEvent_listenerNotCalledIfNoPropertyAdded() throws RemoteException {
        List<CarPropertyEvent> events = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE));

        mCarPropertyServiceClient.onEvent(events);

        verify(mICarPropertyEventListener, never()).onEvent(any());
    }

    @Test
    public void testOnEvent_listenerNotCalledIfBinderDead() throws RemoteException {
        List<CarPropertyEvent> events = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_ERROR, ERROR_CAR_PROPERTY_VALUE));

        mCarPropertyServiceClient.binderDied();
        mCarPropertyServiceClient.onEvent(events);

        verify(mICarPropertyEventListener, never()).onEvent(any());
    }

    @Test
    public void testOnEvent_listenerCalledForErrorEvents() throws RemoteException {
        mCarPropertyServiceClient.addOnChangeProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS);
        List<CarPropertyEvent> events = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_ERROR, ERROR_CAR_PROPERTY_VALUE));

        mCarPropertyServiceClient.onEvent(events);

        verify(mICarPropertyEventListener).onEvent(mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(events);
    }

    @Test
    public void testOnEvent_listenerCalledForChangeEvents() throws RemoteException {
        mCarPropertyServiceClient.addOnChangeProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS);
        List<CarPropertyEvent> events = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE));

        mCarPropertyServiceClient.onEvent(events);

        verify(mICarPropertyEventListener).onEvent(mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(events);
    }

    @Test
    public void testOnEvent_listenerCalledForMultipleProperties() throws RemoteException {
        mCarPropertyServiceClient.addOnChangeProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS);
        mCarPropertyServiceClient.addOnChangeProperty(SECOND_PROPERTY_ID, REGISTERED_AREA_IDS);
        List<CarPropertyEvent> events = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE),
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                        SECOND_GOOD_CAR_PROPERTY_VALUE));

        mCarPropertyServiceClient.onEvent(events);

        verify(mICarPropertyEventListener).onEvent(mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(events);
    }

    @Test
    public void testOnEvent_listenerNotCalledForStaleCarPropertyValues() throws RemoteException {
        mCarPropertyServiceClient.addOnChangeProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS);
        List<CarPropertyEvent> goodEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE));
        List<CarPropertyEvent> staleEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, STALE_CAR_PROPERTY_VALUE));

        mCarPropertyServiceClient.onEvent(goodEvents);
        mCarPropertyServiceClient.onEvent(staleEvents);

        verify(mICarPropertyEventListener).onEvent(mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(goodEvents);
    }

    @Test
    public void testOnEvent_listenerNotCalledForCarPropertyValuesBeforeNextUpdateTime()
            throws RemoteException {
        mCarPropertyServiceClient.addContinuousProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS,
                FIRST_UPDATE_RATE_HZ, /* enableVur= */ false, /* resolution */ 0.0f);
        List<CarPropertyEvent> goodEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE));
        List<CarPropertyEvent> tooEarlyEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                        ALMOST_FRESH_CAR_PROPERTY_VALUE));

        mCarPropertyServiceClient.onEvent(goodEvents);
        mCarPropertyServiceClient.onEvent(tooEarlyEvents);

        verify(mICarPropertyEventListener).onEvent(mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(goodEvents);
    }

    @Test
    public void testOnEvent_listenerCalledForFreshCarPropertyValues() throws RemoteException {
        mCarPropertyServiceClient.addOnChangeProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS);
        List<CarPropertyEvent> goodEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE));
        List<CarPropertyEvent> freshEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, FRESH_CAR_PROPERTY_VALUE));

        mCarPropertyServiceClient.onEvent(goodEvents);
        mCarPropertyServiceClient.onEvent(freshEvents);

        verify(mICarPropertyEventListener, times(2)).onEvent(
                mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(goodEvents,
                freshEvents);
    }

    @Test
    public void testOnEvent_listenerCalledForFreshCarPropertyValuesNonZeroUpdateRate()
            throws RemoteException {
        mCarPropertyServiceClient.addContinuousProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS,
                FIRST_UPDATE_RATE_HZ, /* enableVur= */ false, /* resolution */ 0.0f);
        List<CarPropertyEvent> goodEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE));
        List<CarPropertyEvent> freshEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, FRESH_CAR_PROPERTY_VALUE));

        mCarPropertyServiceClient.onEvent(goodEvents);
        mCarPropertyServiceClient.onEvent(freshEvents);

        verify(mICarPropertyEventListener, times(2)).onEvent(
                mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(goodEvents,
                freshEvents);
    }

    @Test
    public void testOnEvent_listenerCalledForCarPropertyValuesWithDifferentPropertyId()
            throws RemoteException {
        mCarPropertyServiceClient.addOnChangeProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS);
        mCarPropertyServiceClient.addOnChangeProperty(SECOND_PROPERTY_ID, REGISTERED_AREA_IDS);
        List<CarPropertyEvent> goodEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE));
        List<CarPropertyEvent> staleEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                        STALE_CAR_PROPERTY_VALUE_WITH_DIFFERENT_PROPERTY_ID));

        mCarPropertyServiceClient.onEvent(goodEvents);
        mCarPropertyServiceClient.onEvent(staleEvents);

        verify(mICarPropertyEventListener, times(2)).onEvent(
                mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(goodEvents,
                staleEvents);
    }

    @Test
    public void testOnEvent_listenerCalledForCarPropertyValuesWithDifferentAreaId()
            throws RemoteException {
        mCarPropertyServiceClient.addOnChangeProperty(FIRST_PROPERTY_ID, REGISTERED_AREA_IDS);
        List<CarPropertyEvent> goodEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE));
        List<CarPropertyEvent> staleEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                        STALE_CAR_PROPERTY_VALUE_WITH_DIFFERENT_AREA_ID));

        mCarPropertyServiceClient.onEvent(goodEvents);
        mCarPropertyServiceClient.onEvent(staleEvents);

        verify(mICarPropertyEventListener, times(2)).onEvent(
                mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(goodEvents,
                staleEvents);
    }

    @Test
    public void testOnEvent_VurEnabled_forwardsEventIfValueIsDifferent() throws Exception {
        mCarPropertyServiceClient.addContinuousProperty(
                FIRST_PROPERTY_ID, REGISTERED_AREA_IDS, FIRST_UPDATE_RATE_HZ,
                /* enableVur= */ true, /* resolution */ 0.0f);
        List<CarPropertyEvent> goodEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE));
        List<CarPropertyEvent> freshEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, FRESH_CAR_PROPERTY_VALUE));

        mCarPropertyServiceClient.onEvent(goodEvents);
        mCarPropertyServiceClient.onEvent(freshEvents);

        verify(mICarPropertyEventListener, times(2)).onEvent(
                mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(goodEvents,
                freshEvents);
    }

    @Test
    public void testOnEvent_VurEnabled_ignoreEventIfValueIsSame() throws Exception {
        mCarPropertyServiceClient.addContinuousProperty(
                FIRST_PROPERTY_ID, REGISTERED_AREA_IDS, FIRST_UPDATE_RATE_HZ,
                /* enableVur= */ true, /* resolution */ 0.0f);
        List<CarPropertyEvent> goodEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE));
        List<CarPropertyEvent> sameValueEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                        FRESH_CAR_PROPERTY_SAME_VALUE));

        mCarPropertyServiceClient.onEvent(goodEvents);
        mCarPropertyServiceClient.onEvent(sameValueEvents);

        verify(mICarPropertyEventListener, times(1)).onEvent(
                mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(goodEvents);
    }

    @Test
    public void testOnEvent_withResolution_sanitizesEvent() throws Exception {
        mCarPropertyServiceClient.addContinuousProperty(
                FIRST_PROPERTY_ID, REGISTERED_AREA_IDS, FIRST_UPDATE_RATE_HZ,
                /* enableVur= */ true, RESOLUTION);
        // using placeholder CPE error code to ensure that all fields in the CPE object are copied
        // correctly.
        List<CarPropertyEvent> goodEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE,
                        CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN));
        List<CarPropertyEvent> sanitizedEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                        SANITIZED_GOOD_CAR_PROPERTY_VALUE, CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN));

        mCarPropertyServiceClient.onEvent(goodEvents);

        verify(mICarPropertyEventListener, times(1)).onEvent(
                mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(sanitizedEvents);
    }

    @Test
    public void testOnEvent_withResolution_ignoreEventIfValueIsUnderResolution() throws Exception {
        mCarPropertyServiceClient.addContinuousProperty(
                FIRST_PROPERTY_ID, REGISTERED_AREA_IDS, FIRST_UPDATE_RATE_HZ,
                /* enableVur= */ true, RESOLUTION);
        // using placeholder CPE error code to ensure that all fields in the CPE object are copied
        // correctly.
        List<CarPropertyEvent> goodEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE, GOOD_CAR_PROPERTY_VALUE,
                        CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN));
        List<CarPropertyEvent> sanitizedEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                        SANITIZED_GOOD_CAR_PROPERTY_VALUE, CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN));
        List<CarPropertyEvent> sameValueEvents = List.of(
                new CarPropertyEvent(PROPERTY_EVENT_PROPERTY_CHANGE,
                        SIMILAR_GOOD_CAR_PROPERTY_VALUE, CAR_SET_PROPERTY_ERROR_CODE_TRY_AGAIN));

        mCarPropertyServiceClient.onEvent(goodEvents);
        mCarPropertyServiceClient.onEvent(sameValueEvents);

        verify(mICarPropertyEventListener, times(1)).onEvent(
                mCarPropertyEventListCaptor.capture());
        assertThat(mCarPropertyEventListCaptor.getAllValues()).containsExactly(sanitizedEvents);
    }
}
