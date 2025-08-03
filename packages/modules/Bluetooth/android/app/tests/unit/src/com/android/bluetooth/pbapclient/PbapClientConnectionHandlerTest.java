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

package com.android.bluetooth.pbapclient;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.SdpPseRecord;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PbapClientConnectionHandlerTest {

    private static final String TAG = "ConnHandlerTest";
    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";

    private HandlerThread mThread;
    private Looper mLooper;
    private Context mTargetContext;
    private BluetoothDevice mRemoteDevice;

    @Mock
    private AdapterService mAdapterService;

    @Mock
    private DatabaseManager mDatabaseManager;

    private BluetoothAdapter mAdapter;

    private PbapClientService mService;

    private PbapClientStateMachine mStateMachine;

    private PbapClientConnectionHandler mHandler;

    @Before
    public void setUp() throws Exception {
        mTargetContext = spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        mService = new PbapClientService(mTargetContext);
        mService.start();

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        mThread = new HandlerThread("test_handler_thread");
        mThread.start();
        mLooper = mThread.getLooper();
        mRemoteDevice = mAdapter.getRemoteDevice(REMOTE_DEVICE_ADDRESS);

        mStateMachine = new PbapClientStateMachine(mService, mRemoteDevice);
        mHandler = new PbapClientConnectionHandler.Builder()
                .setLooper(mLooper)
                .setClientSM(mStateMachine)
                .setContext(mTargetContext)
                .setRemoteDevice(mRemoteDevice)
                .build();
    }

    @After
    public void tearDown() throws Exception {
        mService.stop();
        mService = PbapClientService.getPbapClientService();
        assertThat(mService).isNull();
        TestUtils.clearAdapterService(mAdapterService);
        mLooper.quit();
    }

    @Test
    public void connectSocket_whenBluetoothIsNotEnabled_returnsFalse() {
        assertThat(mHandler.connectSocket()).isFalse();
    }

    @Test
    public void connectSocket_whenBluetoothIsNotEnabled_returnsFalse_withInvalidL2capPsm() {
        SdpPseRecord record = mock(SdpPseRecord.class);
        mHandler.setPseRecord(record);

        when(record.getL2capPsm()).thenReturn(PbapClientConnectionHandler.L2CAP_INVALID_PSM);
        assertThat(mHandler.connectSocket()).isFalse();
    }

    @Test
    public void connectSocket_whenBluetoothIsNotEnabled_returnsFalse_withValidL2capPsm() {
        SdpPseRecord record = mock(SdpPseRecord.class);
        mHandler.setPseRecord(record);

        when(record.getL2capPsm()).thenReturn(1); // Valid PSM ranges 1 to 30;
        assertThat(mHandler.connectSocket()).isFalse();
    }

    // TODO: Add connectObexSession_returnsTrue

    @Test
    public void connectObexSession_returnsFalse_withoutConnectingSocket() {
        assertThat(mHandler.connectObexSession()).isFalse();
    }

    @Test
    public void abort() {
        SdpPseRecord record = mock(SdpPseRecord.class);
        when(record.getL2capPsm()).thenReturn(1); // Valid PSM ranges 1 to 30;
        mHandler.setPseRecord(record);
        mHandler.connectSocket(); // Workaround for setting mSocket as non-null value
        assertThat(mHandler.getSocket()).isNotNull();

        mHandler.abort();

        assertThat(mThread.isInterrupted()).isTrue();
        assertThat(mHandler.getSocket()).isNull();
    }

    @Test
    public void removeCallLog_doesNotCrash() {
        ContentResolver res = mock(ContentResolver.class);
        when(mTargetContext.getContentResolver()).thenReturn(res);
        mHandler.removeCallLog();

        // Also test when content resolver is null.
        when(mTargetContext.getContentResolver()).thenReturn(null);
        mHandler.removeCallLog();
    }

    @Test
    public void isRepositorySupported_withoutSettingPseRecord_returnsFalse() {
        mHandler.setPseRecord(null);
        final int mask = 0x11;

        assertThat(mHandler.isRepositorySupported(mask)).isFalse();
    }

    @Test
    public void isRepositorySupported_withSettingPseRecord() {
        SdpPseRecord record = mock(SdpPseRecord.class);
        when(record.getSupportedRepositories()).thenReturn(1);
        mHandler.setPseRecord(record);
        final int mask = 0x11;

        assertThat(mHandler.isRepositorySupported(mask)).isTrue();
    }
}
