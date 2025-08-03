/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.a2dp;

import static android.bluetooth.BluetoothCodecConfig.SOURCE_CODEC_TYPE_INVALID;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BufferConstraints;
import android.content.AttributionSource;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AudioRoutingManager;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

public class A2dpServiceBinderTest {
    private static final AttributionSource sSource = new AttributionSource.Builder(0).build();
    private static final BluetoothAdapter sAdapter = BluetoothAdapter.getDefaultAdapter();
    private static final BluetoothDevice sDevice = TestUtils.getTestDevice(sAdapter, 0);

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock private A2dpService mA2dpService;
    @Mock private AudioRoutingManager mAudioRoutingManager;
    @Mock private PackageManager mPackageManager;

    private A2dpService.BluetoothA2dpBinder mBinder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mAudioRoutingManager).when(mA2dpService).getActiveDeviceManager();
        doReturn(mPackageManager).when(mA2dpService).getPackageManager();
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.targetSdkVersion = android.os.Build.VERSION_CODES.CUR_DEVELOPMENT;
        doReturn(appInfo).when(mPackageManager).getApplicationInfo(any(), anyInt());

        mBinder = new A2dpService.BluetoothA2dpBinder(mA2dpService);
    }

    @After
    public void cleanUp() {
        mBinder.cleanup();
    }

    @Test
    public void connect() {
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.connect(sDevice, sSource, recv);
        verify(mA2dpService).connect(sDevice);
    }

    @Test
    public void disconnect() {
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.disconnect(sDevice, sSource, recv);
        verify(mA2dpService).disconnect(sDevice);
    }

    @Test
    public void getConnectedDevices() {
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getConnectedDevices(sSource, recv);
        verify(mA2dpService).getConnectedDevices();
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        int[] states = new int[] {BluetoothProfile.STATE_CONNECTED };
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getDevicesMatchingConnectionStates(states, sSource, recv);
        verify(mA2dpService).getDevicesMatchingConnectionStates(states);
    }

    @Test
    public void getConnectionState() {
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getConnectionState(sDevice, sSource, recv);
        verify(mA2dpService).getConnectionState(sDevice);
    }

    @Test
    public void setActiveDevice() {
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_ROUTING_CENTRALIZATION);

        SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();
        mBinder.setActiveDevice(sDevice, sSource, recv);
        verify(mA2dpService).setActiveDevice(sDevice);
    }

    @Test
    public void setActiveDeviceWithAudioRouting() {
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_ROUTING_CENTRALIZATION);

        SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();
        mBinder.setActiveDevice(sDevice, sSource, recv);
        verify(mAudioRoutingManager).activateDeviceProfile(sDevice, BluetoothProfile.A2DP, recv);
    }

    @Test
    public void setActiveDevice_withNull_callsRemoveActiveDevice() {
        mSetFlagsRule.disableFlags(Flags.FLAG_AUDIO_ROUTING_CENTRALIZATION);
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.setActiveDevice(null, sSource, recv);
        verify(mA2dpService).removeActiveDevice(false);
    }

    @Test
    public void getActiveDevice() {
        final SynchronousResultReceiver<BluetoothDevice> recv = SynchronousResultReceiver.get();

        mBinder.getActiveDevice(sSource, recv);
        verify(mA2dpService).getActiveDevice();
    }

    @Test
    public void setConnectionPolicy() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.setConnectionPolicy(sDevice, connectionPolicy, sSource, recv);
        verify(mA2dpService).setConnectionPolicy(sDevice, connectionPolicy);
    }

    @Test
    public void getConnectionPolicy() {
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.getConnectionPolicy(sDevice, sSource, recv);
        verify(mA2dpService).getConnectionPolicy(sDevice);
    }

    @Test
    public void setAvrcpAbsoluteVolume() {
        int volume = 3;

        mBinder.setAvrcpAbsoluteVolume(volume, sSource);
        verify(mA2dpService).setAvrcpAbsoluteVolume(volume);
    }

    @Test
    public void isA2dpPlaying() {
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.isA2dpPlaying(sDevice, sSource, recv);
        verify(mA2dpService).isA2dpPlaying(sDevice);
    }

    @Test
    public void getCodecStatus() {
        final SynchronousResultReceiver<BluetoothCodecStatus> recv =
                SynchronousResultReceiver.get();

        mBinder.getCodecStatus(sDevice, sSource, recv);
        verify(mA2dpService).getCodecStatus(sDevice);
    }

    @Test
    public void setCodecConfigPreference() {
        BluetoothCodecConfig config = new BluetoothCodecConfig(SOURCE_CODEC_TYPE_INVALID);

        mBinder.setCodecConfigPreference(sDevice, config, sSource);
        verify(mA2dpService).setCodecConfigPreference(sDevice, config);
    }

    @Test
    public void enableOptionalCodecs() {

        mBinder.enableOptionalCodecs(sDevice, sSource);
        verify(mA2dpService).enableOptionalCodecs(sDevice);
    }

    @Test
    public void disableOptionalCodecs() {

        mBinder.disableOptionalCodecs(sDevice, sSource);
        verify(mA2dpService).disableOptionalCodecs(sDevice);
    }

    @Test
    public void isOptionalCodecsSupported() {
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.isOptionalCodecsSupported(sDevice, sSource, recv);
        verify(mA2dpService).getSupportsOptionalCodecs(sDevice);
    }

    @Test
    public void isOptionalCodecsEnabled() {
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.isOptionalCodecsEnabled(sDevice, sSource, recv);
        verify(mA2dpService).getOptionalCodecsEnabled(sDevice);
    }

    @Test
    public void setOptionalCodecsEnabled() {
        int value = BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN;

        mBinder.setOptionalCodecsEnabled(sDevice, value, sSource);
        verify(mA2dpService).setOptionalCodecsEnabled(sDevice, value);
    }

    @Test
    public void getDynamicBufferSupport() {
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.getDynamicBufferSupport(sSource, recv);
        verify(mA2dpService).getDynamicBufferSupport();
    }

    @Test
    public void getBufferConstraints() {
        final SynchronousResultReceiver<BufferConstraints> recv = SynchronousResultReceiver.get();

        mBinder.getBufferConstraints(sSource, recv);
        verify(mA2dpService).getBufferConstraints();
    }

    @Test
    public void setBufferLengthMillis() {
        int codec = 0;
        int value = BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN;
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.setBufferLengthMillis(codec, value, sSource, recv);
        verify(mA2dpService).setBufferLengthMillis(codec, value);
    }
}
