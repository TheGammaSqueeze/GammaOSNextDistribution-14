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

package com.android.bluetooth.opp;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BluetoothOppHandoverReceiverTest {
    Context mContext;

    @Spy
    BluetoothMethodProxy mCallProxy = BluetoothMethodProxy.getInstance();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BluetoothMethodProxy.setInstanceForTesting(mCallProxy);
        doReturn(0).when(mCallProxy).contentResolverDelete(any(), any(Uri.class), any(), any());
        doReturn(null).when(mCallProxy).contentResolverInsert(
          any(), eq(BluetoothShare.CONTENT_URI), any());
    }

    @After
    public void tearDown() {
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void onReceive_withActionHandoverSend_startTransfer() {
        Intent intent = new Intent(Constants.ACTION_HANDOVER_SEND);
        String address = "AA:BB:CC:DD:EE:FF";
        Uri uri = Uri.parse("content:///abc/xyz.txt");
        BluetoothDevice device = (mContext.getSystemService(BluetoothManager.class))
                .getAdapter().getRemoteDevice(address);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setType("text/plain");

        BluetoothOppManager spyManager = spy(new BluetoothOppManager());
        BluetoothOppManager.setInstance(spyManager);
        new BluetoothOppHandoverReceiver().onReceive(mContext, intent);

        verify(spyManager, timeout(3_000)).startTransfer(any());

        // this will run BluetoothOppManager#startTransfer, which will then make
        // InsertShareInfoThread insert into content resolver
        verify(mCallProxy, timeout(3_000).times(1)).contentResolverInsert(any(),
                eq(BluetoothShare.CONTENT_URI), nullable(ContentValues.class));
        BluetoothOppManager.setInstance(null);
    }

    @Test
    public void onReceive_withActionHandoverSendMultiple_startTransfer() {
        Intent intent = new Intent(Constants.ACTION_HANDOVER_SEND_MULTIPLE);
        String address = "AA:BB:CC:DD:EE:FF";
        ArrayList<Uri> uris = new ArrayList<Uri>(
                List.of(Uri.parse("content:///abc/xyz.txt"),
                        Uri.parse("content:///a/b/c/d/x/y/z.txt"),
                        Uri.parse("content:///123/456.txt")));
        BluetoothDevice device = (mContext.getSystemService(BluetoothManager.class))
                .getAdapter().getRemoteDevice(address);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(Intent.EXTRA_STREAM, uris);
        intent.setType("text/plain");

        BluetoothOppManager spyManager = spy(new BluetoothOppManager());
        BluetoothOppManager.setInstance(spyManager);
        new BluetoothOppHandoverReceiver().onReceive(mContext, intent);

        verify(spyManager, timeout(3_000)).startTransfer(any());

        // this will run BluetoothOppManager#startTransfer, which will then make
        // InsertShareInfoThread insert into content resolver
        verify(mCallProxy, timeout(3_000).times(3)).contentResolverInsert(any(),
                eq(BluetoothShare.CONTENT_URI), nullable(ContentValues.class));
        BluetoothOppManager.setInstance(null);
    }

    @Test
    public void onReceive_withActionStopHandover_triggerContentResolverDelete() {
        Intent intent = new Intent(Constants.ACTION_STOP_HANDOVER);
        String address = "AA:BB:CC:DD:EE:FF";
        BluetoothDevice device = (mContext.getSystemService(BluetoothManager.class))
                .getAdapter().getRemoteDevice(address);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_ID, 0);

        new BluetoothOppHandoverReceiver().onReceive(mContext, intent);

        verify(mCallProxy).contentResolverDelete(any(), any(),
                nullable(String.class), nullable(String[].class));
    }
}