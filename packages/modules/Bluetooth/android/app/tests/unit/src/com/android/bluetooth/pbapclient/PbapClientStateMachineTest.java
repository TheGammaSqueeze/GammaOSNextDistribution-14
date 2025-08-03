/*
 * Copyright 2023 The Android Open Source Project
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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.BroadcastOptions;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.util.Log;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PbapClientStateMachineTest{
    private static final String TAG = "PbapClientStateMachineTest";

    private PbapClientStateMachine mPbapClientStateMachine = null;

    @Mock
    private PbapClientService mMockPbapClientService;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private PbapClientConnectionHandler mMockHandler;

    private BluetoothDevice mTestDevice;
    private BluetoothAdapter mAdapter;

    private ArgumentCaptor<Intent> mIntentArgument = ArgumentCaptor.forClass(Intent.class);


    static final int DISCONNECT_TIMEOUT = 5000;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // This line must be called to make sure relevant objects are initialized properly
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        // Get a device for testing
        mTestDevice = mAdapter.getRemoteDevice("00:01:02:03:04:05");
        when(mMockPbapClientService.getSystemServiceName(UserManager.class))
                .thenReturn(Context.USER_SERVICE);
        when(mMockPbapClientService.getSystemService(UserManager.class))
                .thenReturn(mMockUserManager);
        mPbapClientStateMachine = new PbapClientStateMachine(mMockPbapClientService, mTestDevice,
                mMockHandler);
        mPbapClientStateMachine.start();
    }

    @After
    public void tearDown() throws Exception {
        if (mPbapClientStateMachine != null) {
            mPbapClientStateMachine.doQuit();
        }
    }

    /**
     * Test that default state is STATE_CONNECTING
     */
    @Test
    public void testDefaultConnectingState() {
        Log.i(TAG, "in testDefaultConnectingState");
        // it appears that enter and exit can overlap sometimes when calling doQuit()
        // currently solved by waiting for looper to finish task
        TestUtils.waitForLooperToFinishScheduledTask(mPbapClientStateMachine.getHandler()
                .getLooper());
        assertThat(mPbapClientStateMachine.getConnectionState())
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    /**
     * Test transition from STATE_CONNECTING to STATE_DISCONNECTING
     * and then to STATE_DISCONNECTED after timeout.
     */
    @Test
    public void testStateTransitionFromConnectingToDisconnected() {
        assertThat(mPbapClientStateMachine.getConnectionState())
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);

        mPbapClientStateMachine.disconnect(mTestDevice);

        TestUtils.waitForLooperToFinishScheduledTask(mPbapClientStateMachine.getHandler()
                .getLooper());
        assertThat(mPbapClientStateMachine.getConnectionState())
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTING);

        //wait until timeout occurs
        Mockito.clearInvocations(mMockPbapClientService);
        verify(mMockPbapClientService, timeout(DISCONNECT_TIMEOUT))
                .sendBroadcastMultiplePermissions(mIntentArgument.capture(), any(String[].class),
                        any(BroadcastOptions.class));
        assertThat(mPbapClientStateMachine.getConnectionState())
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }
}
