/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.mcp;

import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Looper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class McpServiceTest {
    private BluetoothAdapter mAdapter;
    private McpService mMcpService;
    private Context mTargetContext;

    @Mock
    private AdapterService mAdapterService;
    @Mock
    private MediaControlGattService mMockMcpService;
    @Mock
    private MediaControlProfile mMediaControlProfile;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        McpService.setMediaControlProfileForTesting(mMediaControlProfile);
        mMcpService = new McpService(mTargetContext);
        mMcpService.start();
        mMcpService.setAvailable(true);
    }

    @After
    public void tearDown() throws Exception {
        if (mMcpService == null) {
            return;
        }

        mMcpService.stop();
        mMcpService = McpService.getMcpService();
        Assert.assertNull(mMcpService);
        reset(mMediaControlProfile);
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void testGetService() {
        McpService mMcpServiceDuplicate = McpService.getMcpService();
        Assert.assertNotNull(mMcpServiceDuplicate);
        Assert.assertSame(mMcpServiceDuplicate, mMcpService);
    }

    @Test
    public void testAuthorization() {
        BluetoothDevice device0 = TestUtils.getTestDevice(mAdapter, 0);
        BluetoothDevice device1 = TestUtils.getTestDevice(mAdapter, 1);

        doNothing().when(mMediaControlProfile).onDeviceAuthorizationSet(any(BluetoothDevice.class));

        mMcpService.setDeviceAuthorized(device0, true);
        verify(mMediaControlProfile).onDeviceAuthorizationSet(eq(device0));
        Assert.assertEquals(
                BluetoothDevice.ACCESS_ALLOWED, mMcpService.getDeviceAuthorization(device0));

        mMcpService.setDeviceAuthorized(device1, false);
        verify(mMediaControlProfile).onDeviceAuthorizationSet(eq(device1));
        Assert.assertEquals(BluetoothDevice.ACCESS_REJECTED,
                mMcpService.getDeviceAuthorization(device1));
    }

    @Test
    public void testStopMcpService() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(mMcpService::stop);
        Assert.assertNull(McpService.getMcpService());
        Assert.assertNull(McpService.getMediaControlProfile());

        McpService.setMediaControlProfileForTesting(mMediaControlProfile);
        // Try to restart the service. Note: must be done on the main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync(mMcpService::start);
    }

    @Test
    public void testDumpDoesNotCrash() {
        mMcpService.dump(new StringBuilder());
    }
}
