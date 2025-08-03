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

package com.android.car.hal;

import static android.hardware.automotive.vehicle.VehicleProperty.ANDROID_EPOCH_TIME;
import static android.hardware.automotive.vehicle.VehicleProperty.EXTERNAL_CAR_TIME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.time.ExternalTimeSuggestion;
import android.app.time.TimeManager;
import android.car.test.util.FakeContext;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;

import com.android.car.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.function.Supplier;

@RunWith(MockitoJUnitRunner.class)
public final class TimeHalServiceTest {

    private static final HalPropConfig ANDROID_TIME_PROP =
            VehicleHalTestingHelper.newConfig(ANDROID_EPOCH_TIME);

    private static final HalPropConfig CAR_TIME_PROP =
            VehicleHalTestingHelper.newConfig(EXTERNAL_CAR_TIME);

    private final FakeContext mFakeContext = new FakeContext();

    @Mock private Resources mMockResources;
    @Mock private TimeManager mTimeManagerService;
    @Mock private VehicleHal mVehicleHal;

    private Supplier<TimeHalService> mTimeHalServiceProvider;

    private final HalPropValueBuilder mPropValueBuilder = new HalPropValueBuilder(/*isAidl=*/true);

    @Before
    public void setUp() {
        when(mMockResources.getBoolean(R.bool.config_enableExternalCarTimeToExternalTimeSuggestion))
                .thenReturn(true);

        mFakeContext.setResources(mMockResources);
        mFakeContext.setSystemService(TimeManager.class, mTimeManagerService);

        when(mVehicleHal.getHalPropValueBuilder()).thenReturn(mPropValueBuilder);
        mTimeHalServiceProvider = () -> new TimeHalService(mFakeContext, mVehicleHal);
    }

    @Test
    public void testInitDoesNothing() {
        TimeHalService timeHalService = mTimeHalServiceProvider.get();
        timeHalService.takeProperties(Collections.emptyList());

        timeHalService.init();

        assertThat(timeHalService.isAndroidTimeSupported()).isFalse();
        assertThat(timeHalService.isExternalCarTimeSupported()).isFalse();
        mFakeContext.verifyReceiverNotRegistered();
    }

    @Test
    public void testInitRegistersBroadcastReceiver() {
        TimeHalService timeHalService = mTimeHalServiceProvider.get();
        timeHalService.takeProperties(Collections.singletonList(ANDROID_TIME_PROP));

        timeHalService.init();

        assertThat(timeHalService.isAndroidTimeSupported()).isTrue();
        mFakeContext.verifyReceiverRegistered(Intent.ACTION_TIME_CHANGED);
    }

    @Test
    public void testInitSendsAndroidTimeUpdate() {
        TimeHalService timeHalService = mTimeHalServiceProvider.get();
        timeHalService.takeProperties(Collections.singletonList(ANDROID_TIME_PROP));
        long sysTimeMillis = System.currentTimeMillis();

        timeHalService.init();

        assertThat(timeHalService.isAndroidTimeSupported()).isTrue();
        ArgumentCaptor<HalPropValue> captor = ArgumentCaptor.forClass(HalPropValue.class);
        verify(mVehicleHal).set(captor.capture());
        HalPropValue propValue = captor.getValue();
        assertThat(propValue.getPropId()).isEqualTo(ANDROID_EPOCH_TIME);
        assertThat(propValue.getAreaId()).isEqualTo(0);
        assertThat(propValue.getStatus()).isEqualTo(VehiclePropertyStatus.AVAILABLE);
        assertThat(propValue.getTimestamp()).isAtLeast(sysTimeMillis);
        assertThat(propValue.getInt64ValuesSize()).isEqualTo(1);
        assertThat(propValue.getInt64Value(0)).isAtLeast(sysTimeMillis);
    }

    @Test
    public void testInitDoesNothingWhenConfigDisabled() {
        when(mMockResources.getBoolean(R.bool.config_enableExternalCarTimeToExternalTimeSuggestion))
                .thenReturn(false);
        TimeHalService timeHalService = mTimeHalServiceProvider.get();
        timeHalService.takeProperties(Collections.singletonList(CAR_TIME_PROP));

        timeHalService.init();

        assertThat(timeHalService.isExternalCarTimeSupported()).isFalse();
        verifyZeroInteractions(mTimeManagerService);
    }

    @Test
    public void testInitSendsExternalTimeSuggestion() {
        TimeHalService timeHalService = mTimeHalServiceProvider.get();
        timeHalService.takeProperties(Collections.singletonList(CAR_TIME_PROP));
        long testTimeNanos = 123_456_789_456_123L;
        when(mVehicleHal.get(anyInt())).thenReturn(
                newExternalCarTimeValue(testTimeNanos));

        timeHalService.init();

        assertThat(timeHalService.isExternalCarTimeSupported()).isTrue();
        verify(mVehicleHal).get(EXTERNAL_CAR_TIME);
        verify(mTimeManagerService).suggestExternalTime(
                new ExternalTimeSuggestion(testTimeNanos / 1_000_000, testTimeNanos));
    }

    @Test
    public void testInitSubscribesToProperty() {
        TimeHalService timeHalService = mTimeHalServiceProvider.get();
        timeHalService.takeProperties(Collections.singletonList(CAR_TIME_PROP));
        long testTimeNanos = 123_456_789_456_123L;
        when(mVehicleHal.get(anyInt())).thenReturn(
                newExternalCarTimeValue(testTimeNanos));

        timeHalService.init();

        assertThat(timeHalService.isExternalCarTimeSupported()).isTrue();
        verify(mVehicleHal).subscribePropertySafe(timeHalService, EXTERNAL_CAR_TIME);
    }

    @Test
    public void testReleaseUnregistersBroadcastReceiver() {
        TimeHalService timeHalService = mTimeHalServiceProvider.get();
        timeHalService.takeProperties(Collections.singletonList(ANDROID_TIME_PROP));
        timeHalService.init();
        clearInvocations(mVehicleHal);

        timeHalService.release();

        mFakeContext.verifyReceiverNotRegistered();
        assertThat(timeHalService.isAndroidTimeSupported()).isFalse();
    }

    @Test
    public void testReleaseClearsExternalCarTime() {
        TimeHalService timeHalService = mTimeHalServiceProvider.get();
        timeHalService.takeProperties(Collections.singletonList(ANDROID_TIME_PROP));
        timeHalService.init();

        timeHalService.release();

        assertThat(timeHalService.isExternalCarTimeSupported()).isFalse();
    }

    @Test
    public void testSendsAndroidTimeUpdateWhenBroadcast() {
        TimeHalService timeHalService = mTimeHalServiceProvider.get();
        timeHalService.takeProperties(Collections.singletonList(ANDROID_TIME_PROP));
        timeHalService.init();
        clearInvocations(mVehicleHal);
        long sysTimeMillis = System.currentTimeMillis();

        mFakeContext.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));

        ArgumentCaptor<HalPropValue> captor = ArgumentCaptor.forClass(HalPropValue.class);
        verify(mVehicleHal).set(captor.capture());
        HalPropValue propValue = captor.getValue();
        assertThat(propValue.getPropId()).isEqualTo(ANDROID_EPOCH_TIME);
        assertThat(propValue.getAreaId()).isEqualTo(0);
        assertThat(propValue.getStatus()).isEqualTo(VehiclePropertyStatus.AVAILABLE);
        assertThat(propValue.getTimestamp()).isAtLeast(sysTimeMillis);
        assertThat(propValue.getInt64ValuesSize()).isEqualTo(1);
        assertThat(propValue.getInt64Value(0)).isAtLeast(sysTimeMillis);
    }

    @Test
    public void testOnHalEventSendsExternalTimeSuggestion() {
        TimeHalService timeHalService = mTimeHalServiceProvider.get();
        timeHalService.takeProperties(Collections.singletonList(CAR_TIME_PROP));
        when(mVehicleHal.get(anyInt())).thenReturn(newExternalCarTimeValue(0));
        timeHalService.init();
        clearInvocations(mTimeManagerService);
        long testTimeNanos = 123_456_789_456_123L;

        timeHalService.onHalEvents(
                Collections.singletonList(newExternalCarTimeValue(testTimeNanos)));

        assertThat(timeHalService.isExternalCarTimeSupported()).isTrue();
        verify(mTimeManagerService).suggestExternalTime(
                new ExternalTimeSuggestion(testTimeNanos / 1_000_000, testTimeNanos));
    }

    private HalPropValue newExternalCarTimeValue(long timeMicros) {
        return mPropValueBuilder.build(EXTERNAL_CAR_TIME, /* areaId= */ 0,
                timeMicros, VehiclePropertyStatus.AVAILABLE, timeMicros);
    }
}
