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

package com.android.car.bluetooth;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.car.builtin.bluetooth.BluetoothHeadsetClientHelper;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.content.res.Resources;
import android.os.ParcelUuid;
import android.os.UserManager;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.RequiresDevice;

import com.android.car.CarPerUserServiceImpl;
import com.android.car.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link CarBluetoothUserService}
 *
 * Run:
 * atest CarBluetoothUserServiceTest
 */
@RequiresDevice
public class CarBluetoothUserServiceTest extends AbstractExtendedMockitoBluetoothTestCase {
    private static final String TAG = CarBluetoothUserServiceTest.class.getSimpleName();

    static final String DEVICE_NAME = "name";
    static final String DEVICE_ADDRESS_STRING = "11:22:33:44:55:66";

    private static final String DEFAULT_DEVICE = "00:11:22:33:44:55";
    private static final List<String> DEVICE_LIST_WITHOUT_DEFAULT = Arrays.asList(
            "DE:AD:BE:EF:00:00",
            "DE:AD:BE:EF:00:01",
            "DE:AD:BE:EF:00:02",
            "DE:AD:BE:EF:00:03");
    private static final List<String> DEVICE_LIST_WITH_DEFAULT = Arrays.asList(
            "DE:AD:BE:EF:00:00",
            "DE:AD:BE:EF:00:01",
            "DE:AD:BE:EF:00:02",
            DEFAULT_DEVICE,
            "DE:AD:BE:EF:00:03");

    private CarBluetoothUserService mCarBluetoothUserService;

    private MockContext mMockContext;

    @Mock private CarPerUserServiceImpl mMockCarPerUserServiceImpl;
    @Mock private BluetoothManager mMockBluetoothManager;
    @Mock private BluetoothAdapter mMockBluetoothAdapter;
    @Captor private ArgumentCaptor<BluetoothProfile.ServiceListener> mProfileServiceListenerCaptor;
    @Mock private BluetoothHeadsetClient mMockBluetoothHeadsetClient;
    @Mock private TelecomManager mMockTelecomManager;
    @Mock private PhoneAccountHandle mMockPhoneAccountHandle;
    @Captor private ArgumentCaptor<BluetoothDevice> mBvraDeviceCaptor;
    @Mock private Resources mMockResources;
    @Mock private UserManager mMockUserManager;

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(BluetoothHeadsetClientHelper.class);
    }

    //-------------------------------------------------------------------------------------------//
    // Setup/TearDown                                                                            //
    //-------------------------------------------------------------------------------------------//

    @Before
    public void setUp() {
        mMockContext = new MockContext(InstrumentationRegistry.getTargetContext());
        when(mMockCarPerUserServiceImpl.getApplicationContext()).thenReturn(mMockContext);
        mMockContext.addMockedSystemService(BluetoothManager.class, mMockBluetoothManager);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getName()).thenReturn(DEVICE_NAME);
        when(mMockBluetoothAdapter.getAddress()).thenReturn(DEVICE_ADDRESS_STRING);

        // for testing BVRA
        mMockContext.addMockedSystemService(TelecomManager.class, mMockTelecomManager);
        when(mMockTelecomManager.getUserSelectedOutgoingPhoneAccount())
                .thenReturn(mMockPhoneAccountHandle);
        when(mMockPhoneAccountHandle.getId()).thenReturn(DEFAULT_DEVICE);

        // for FastPairProvider
        when(mMockResources.getInteger(R.integer.fastPairModelId)).thenReturn(123);
        when(mMockResources.getString(R.string.fastPairAntiSpoofKey)).thenReturn("HelloWorld");
        when(mMockResources.getBoolean(R.bool.fastPairAutomaticAcceptance)).thenReturn(false);
        when(mMockCarPerUserServiceImpl.getResources()).thenReturn(mMockResources);
        when(mMockCarPerUserServiceImpl.getSystemService(eq(BluetoothManager.class)))
                .thenReturn(mMockBluetoothManager);
        when(mMockCarPerUserServiceImpl.getSystemService(eq(UserManager.class)))
                .thenReturn(mMockUserManager);
        when(mMockUserManager.isUserUnlocked()).thenReturn(false);

        mCarBluetoothUserService = new CarBluetoothUserService(mMockCarPerUserServiceImpl);

        // Grab the {@link BluetoothProfile.ServiceListener} to inject mock profile proxies
        doReturn(true).when(mMockBluetoothAdapter).getProfileProxy(
                any(Context.class), mProfileServiceListenerCaptor.capture(), anyInt());
        mCarBluetoothUserService.setupBluetoothConnectionProxies();
    }

    //-------------------------------------------------------------------------------------------//
    // Voice recognition (HFP Client BVRA) tests                                                 //
    //-------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - HeadsetClient proxy is {@code null}.
     * - There is at least one device that supports BVRA.
     *
     * Actions:
     * - Invoke {@link #startBluetoothVoiceRecognition}
     *
     * Outcome:
     * - No attempt to start voice recognition.
     * - Return {@code false}.
     */
    @Test
    public void testBvra_noProxy_doNothing() {
        setBluetoothProfileProxy(BluetoothProfile.HEADSET_CLIENT, /* proxy= */ null);

        List<BluetoothDevice> devicesToReturn = DEVICE_LIST_WITHOUT_DEFAULT.stream()
                .map(CarBluetoothUserServiceTest::createMockDevice).collect(Collectors.toList());
        mockHeadsetClientGetConnectedBvraDevices(devicesToReturn);

        assertThat(mCarBluetoothUserService.startBluetoothVoiceRecognition()).isFalse();

        verify(() -> BluetoothHeadsetClientHelper.startVoiceRecognition(
                any(BluetoothHeadsetClient.class), any(BluetoothDevice.class)), never());
    }

    /**
     * Preconditions:
     * - No connected devices support BVRA.
     * - HeadsetClient proxy is not {@code null}.
     *
     * Actions:
     * - Invoke {@link #startBluetoothVoiceRecognition}
     *
     * Outcome:
     * - No attempt to start voice recognition.
     * - Return {@code false}.
     */
    @Test
    public void testBvra_noDevices_doNothing() {
        setBluetoothProfileProxy(BluetoothProfile.HEADSET_CLIENT, mMockBluetoothHeadsetClient);

        List<BluetoothDevice> devicesToReturn = Collections.emptyList();
        mockHeadsetClientGetConnectedBvraDevices(devicesToReturn);

        assertThat(mCarBluetoothUserService.startBluetoothVoiceRecognition()).isFalse();

        verify(() -> BluetoothHeadsetClientHelper.startVoiceRecognition(
                any(BluetoothHeadsetClient.class), any(BluetoothDevice.class)), never());
    }

    /**
     * Preconditions:
     * - The default phone device supports BVRA.
     * - The default device is not the first (or only) device in the list.
     * - HeadsetClient proxy is not {@code null}.
     *
     * Actions:
     * - Invoke {@link #startBluetoothVoiceRecognition}
     *
     * Outcome:
     * - Voice recognition is invoked.
     * - The default phone device is used.
     */
    @Test
    public void testBvra_defaultDeviceSupports_bvraOnDefaultDevice() {
        setBluetoothProfileProxy(BluetoothProfile.HEADSET_CLIENT, mMockBluetoothHeadsetClient);

        assertThat(DEFAULT_DEVICE).isNotEqualTo(DEVICE_LIST_WITH_DEFAULT.get(0));
        List<BluetoothDevice> devicesToReturn = DEVICE_LIST_WITH_DEFAULT.stream()
                .map(CarBluetoothUserServiceTest::createMockDevice).collect(Collectors.toList());
        mockHeadsetClientGetConnectedBvraDevices(devicesToReturn);

        mCarBluetoothUserService.startBluetoothVoiceRecognition();

        verify(() -> BluetoothHeadsetClientHelper.startVoiceRecognition(
                any(BluetoothHeadsetClient.class), mBvraDeviceCaptor.capture()));
        assertThat(mBvraDeviceCaptor.getValue().getAddress()).isEqualTo(DEFAULT_DEVICE);
    }

    /**
     * Preconditions:
     * - The default phone device does not support BVRA.
     * - There is at least one other device that supports BVRA.
     * - HeadsetClient proxy is not {@code null}.
     *
     * Actions:
     * - Invoke {@link #startBluetoothVoiceRecognition}
     *
     * Outcome:
     * - Voice recognition is invoked.
     * - The first phone device is used.
     */
    @Test
    public void testBvra_defaultDeviceNoSupport_bvraOnFirstDevice() {
        setBluetoothProfileProxy(BluetoothProfile.HEADSET_CLIENT, mMockBluetoothHeadsetClient);

        assertThat(DEFAULT_DEVICE).isNotIn(DEVICE_LIST_WITHOUT_DEFAULT);
        List<BluetoothDevice> devicesToReturn = DEVICE_LIST_WITHOUT_DEFAULT.stream()
                .map(CarBluetoothUserServiceTest::createMockDevice).collect(Collectors.toList());
        mockHeadsetClientGetConnectedBvraDevices(devicesToReturn);

        mCarBluetoothUserService.startBluetoothVoiceRecognition();

        verify(() -> BluetoothHeadsetClientHelper.startVoiceRecognition(
                any(BluetoothHeadsetClient.class), mBvraDeviceCaptor.capture()));
        assertThat(mBvraDeviceCaptor.getValue().getAddress())
                .isEqualTo(DEVICE_LIST_WITHOUT_DEFAULT.get(0));
    }

    /**
     * Preconditions:
     * - There is at least one device that supports BVRA.
     * - HeadsetClient proxy is not {@code null}.
     *
     * Actions:
     * - Invoke {@link #startBluetoothVoiceRecognition} twice, once where
     *   {@link BluetoothHeadsetClientHelper#startVoiceRecognition} returns {@code true}, and
     *   once where it returns {@code false}.
     *
     * Outcome:
     * - {@link BluetoothHeadsetClientHelper#startVoiceRecognition} is called twice.
     * - Correctly passes up the results of calling
     *   {@link BluetoothHeadsetClientHelper#startVoiceRecognition}.
     */
    @Test
    public void testBvra_passUpInvocationResult() {
        setBluetoothProfileProxy(BluetoothProfile.HEADSET_CLIENT, mMockBluetoothHeadsetClient);

        List<BluetoothDevice> devicesToReturn = DEVICE_LIST_WITH_DEFAULT.stream()
                .map(CarBluetoothUserServiceTest::createMockDevice).collect(Collectors.toList());
        mockHeadsetClientGetConnectedBvraDevices(devicesToReturn);

        mockHeadsetClientStartVoiceRecognition(true);
        assertThat(mCarBluetoothUserService.startBluetoothVoiceRecognition()).isTrue();

        mockHeadsetClientStartVoiceRecognition(false);
        assertThat(mCarBluetoothUserService.startBluetoothVoiceRecognition()).isFalse();
    }

    //-------------------------------------------------------------------------------------------//
    // Utilities                                                                                 //
    //-------------------------------------------------------------------------------------------//

    /**
     * Mocks a call to {@link BluetoothHeadsetClientHelper#getConnectedBvraDevices}.
     *
     * @throws IllegalStateException if class didn't override {@link #newSessionBuilder()} and
     * called {@code spyStatic(BluetoothHeadsetClientHelper.class)} on the session passed to it.
     *
     * (Similar to {@link AbstractExtendedMockitoCarServiceTestCase#mockGetCarLocalService}).
     */
    protected final <T> void mockHeadsetClientGetConnectedBvraDevices(
            @NonNull List<BluetoothDevice> devices) {
        Slogf.v(TAG, "mockHeadsetClientGetConnectedBvraDevices");
        assertSpied(BluetoothHeadsetClientHelper.class);

        doReturn(devices).when(() -> BluetoothHeadsetClientHelper.getConnectedBvraDevices(
                any(BluetoothHeadsetClient.class)));
    }

    /**
     * Mocks a call to {@link BluetoothHeadsetClientHelper#startVoiceRecognition}.
     *
     * @throws IllegalStateException if class didn't override {@link #newSessionBuilder()} and
     * called {@code spyStatic(BluetoothHeadsetClientHelper.class)} on the session passed to it.
     *
     * (Similar to {@link AbstractExtendedMockitoCarServiceTestCase#mockGetCarLocalService}).
     */
    protected final <T> void mockHeadsetClientStartVoiceRecognition(boolean successOrNot) {
        Slogf.v(TAG, "mockHeadsetClientStartVoiceRecognition");
        assertSpied(BluetoothHeadsetClientHelper.class);

        doReturn(successOrNot).when(() -> BluetoothHeadsetClientHelper.startVoiceRecognition(
                any(BluetoothHeadsetClient.class), any(BluetoothDevice.class)));
    }

    private void setBluetoothProfileProxy(int profile, BluetoothProfile proxy) {
        mProfileServiceListenerCaptor.getValue().onServiceConnected(profile, proxy);
    }

    private static BluetoothDevice createMockDevice(String bdAddr) {
        // Tests assume HFP is the only supported profile
        return createMockDevice(bdAddr,
                new ParcelUuid[]{BluetoothUuid.HFP_AG, BluetoothUuid.HSP_AG});
    }

    private static BluetoothDevice createMockDevice(String bdAddr, ParcelUuid[] uuids) {
        BluetoothDevice device = mock(BluetoothDevice.class);
        when(device.getAddress()).thenReturn(bdAddr);
        when(device.getName()).thenReturn(bdAddr);
        when(device.getUuids()).thenReturn(uuids);
        when(device.connect()).thenReturn(BluetoothStatusCodes.SUCCESS);
        when(device.disconnect()).thenReturn(BluetoothStatusCodes.SUCCESS);
        return device;
    }
}
