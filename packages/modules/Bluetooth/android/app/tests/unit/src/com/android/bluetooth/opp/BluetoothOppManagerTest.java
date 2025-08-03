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

import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.Intents.intending;
import static androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent;

import static com.android.bluetooth.opp.BluetoothOppManager.ALLOWED_INSERT_SHARE_THREAD_NUMBER;
import static com.android.bluetooth.opp.BluetoothOppManager.OPP_PREFERENCE_FILE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;

import androidx.test.espresso.intent.Intents;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class BluetoothOppManagerTest {
    Context mContext;

    BluetoothMethodProxy mCallProxy;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));

        mCallProxy = spy(BluetoothMethodProxy.getInstance());
        BluetoothMethodProxy.setInstanceForTesting(mCallProxy);

        doReturn(null).when(mCallProxy).contentResolverInsert(
                any(), eq(BluetoothShare.CONTENT_URI), any());

        Intents.init();
    }

    @After
    public void tearDown() {
        BluetoothMethodProxy.setInstanceForTesting(null);
        BluetoothOppUtility.sSendFileMap.clear();
        mContext.getSharedPreferences(OPP_PREFERENCE_FILE, 0).edit().clear().apply();
        BluetoothOppManager.sInstance = null;

        Intents.release();
    }

    @Test
    public void
    restoreApplicationData_afterSavingSingleSendingFileInfo_containsSendingFileInfoSaved() {
        BluetoothOppManager bluetoothOppManager = BluetoothOppManager.getInstance(mContext);
        bluetoothOppManager.mSendingFlag = true;
        bluetoothOppManager.saveSendingFileInfo("text/plain", "content:///abc/xyz.txt", false,
                true);

        BluetoothOppManager.sInstance = null;
        BluetoothOppManager restartedBluetoothOppManager = BluetoothOppManager.getInstance(
                mContext);
        assertThat(bluetoothOppManager.mSendingFlag).isEqualTo(
                restartedBluetoothOppManager.mSendingFlag);
        assertThat(bluetoothOppManager.mMultipleFlag).isEqualTo(
                restartedBluetoothOppManager.mMultipleFlag);
        assertThat(bluetoothOppManager.mUriOfSendingFile).isEqualTo(
                restartedBluetoothOppManager.mUriOfSendingFile);
        assertThat(bluetoothOppManager.mUrisOfSendingFiles).isEqualTo(
                restartedBluetoothOppManager.mUrisOfSendingFiles);
        assertThat(bluetoothOppManager.mMimeTypeOfSendingFile).isEqualTo(
                restartedBluetoothOppManager.mMimeTypeOfSendingFile);
        assertThat(bluetoothOppManager.mMimeTypeOfSendingFiles).isEqualTo(
                restartedBluetoothOppManager.mMimeTypeOfSendingFiles);
    }

    @Test
    public void
    restoreApplicationData_afterSavingMultipleSendingFileInfo_containsSendingFileInfoSaved() {
        BluetoothOppManager bluetoothOppManager = BluetoothOppManager.getInstance(mContext);
        bluetoothOppManager.mSendingFlag = true;
        bluetoothOppManager.saveSendingFileInfo("text/plain", new ArrayList<Uri>(
                        List.of(Uri.parse("content:///abc/xyz.txt"), Uri.parse("content:///123"
                                + "/456.txt"))),
                false, true);

        BluetoothOppManager.sInstance = null;
        BluetoothOppManager restartedBluetoothOppManager = BluetoothOppManager.getInstance(
                mContext);
        assertThat(bluetoothOppManager.mSendingFlag).isEqualTo(
                restartedBluetoothOppManager.mSendingFlag);
        assertThat(bluetoothOppManager.mMultipleFlag).isEqualTo(
                restartedBluetoothOppManager.mMultipleFlag);
        assertThat(bluetoothOppManager.mUriOfSendingFile).isEqualTo(
                restartedBluetoothOppManager.mUriOfSendingFile);
        assertThat(bluetoothOppManager.mUrisOfSendingFiles).isEqualTo(
                restartedBluetoothOppManager.mUrisOfSendingFiles);
        assertThat(bluetoothOppManager.mMimeTypeOfSendingFile).isEqualTo(
                restartedBluetoothOppManager.mMimeTypeOfSendingFile);
        assertThat(bluetoothOppManager.mMimeTypeOfSendingFiles).isEqualTo(
                restartedBluetoothOppManager.mMimeTypeOfSendingFiles);
    }

    @Test
    public void isAcceptedList_inAcceptList_returnsTrue() {
        BluetoothOppManager bluetoothOppManager = BluetoothOppManager.getInstance(mContext);
        String address1 = "AA:BB:CC:DD:EE:FF";
        String address2 = "00:11:22:33:44:55";

        bluetoothOppManager.addToAcceptlist(address1);
        bluetoothOppManager.addToAcceptlist(address2);
        assertThat(bluetoothOppManager.isAcceptlisted(address1)).isTrue();
        assertThat(bluetoothOppManager.isAcceptlisted(address2)).isTrue();
    }

    @Test
    public void isAcceptedList_notInAcceptList_returnsFalse() {
        BluetoothOppManager bluetoothOppManager = BluetoothOppManager.getInstance(mContext);
        String address = "01:23:45:67:89:AB";

        assertThat(bluetoothOppManager.isAcceptlisted(address)).isFalse();

        bluetoothOppManager.addToAcceptlist(address);
        assertThat(bluetoothOppManager.isAcceptlisted(address)).isTrue();
    }

    @Test
    public void startTransfer_withMultipleUris_contentResolverInsertMultipleTimes() {
        BluetoothOppManager bluetoothOppManager = BluetoothOppManager.getInstance(mContext);
        String address = "AA:BB:CC:DD:EE:FF";
        bluetoothOppManager.saveSendingFileInfo("text/plain", new ArrayList<Uri>(
                List.of(Uri.parse("content:///abc/xyz.txt"),
                        Uri.parse("content:///a/b/c/d/x/y/z.docs"),
                        Uri.parse("content:///123/456.txt"))), false, true);
        BluetoothDevice device = (mContext.getSystemService(BluetoothManager.class))
                .getAdapter().getRemoteDevice(address);
        bluetoothOppManager.startTransfer(device);
        // add 2 files
        verify(mCallProxy, timeout(5_000)
                .times(3)).contentResolverInsert(any(), nullable(Uri.class),
                nullable(ContentValues.class));
    }

    @Test
    public void startTransfer_withOneUri_contentResolverInsertOnce() {
        BluetoothOppManager bluetoothOppManager = BluetoothOppManager.getInstance(mContext);
        String address = "AA:BB:CC:DD:EE:FF";
        bluetoothOppManager.saveSendingFileInfo("text/plain", "content:///abc/xyz.txt",
                false, true);
        BluetoothDevice device = (mContext.getSystemService(BluetoothManager.class))
                .getAdapter().getRemoteDevice(address);
        bluetoothOppManager.startTransfer(device);
        verify(mCallProxy, timeout(5_000).times(1)).contentResolverInsert(any(),
                nullable(Uri.class), nullable(ContentValues.class));
    }

    @Ignore("b/267270055")
    @Test
    public void startTransferMoreThanAllowedInsertShareThreadNumberTimes_blockExceedingTransfer()
            throws InterruptedException {
        BluetoothOppManager bluetoothOppManager = BluetoothOppManager.getInstance(mContext);
        String address = "AA:BB:CC:DD:EE:FF";
        bluetoothOppManager.saveSendingFileInfo("text/plain", "content:///abc/xyz.txt",
                false, true);
        BluetoothDevice device = (mContext.getSystemService(BluetoothManager.class))
                .getAdapter().getRemoteDevice(address);

        AtomicBoolean intended = new AtomicBoolean(false);
        intending(anyIntent()).respondWithFunction(
                intent -> {
                    // verify that at least one exceeding thread is blocked
                    intended.set(true);
                    return null;
                });

        // try flushing the transferring queue,
        for (int i = 0; i < ALLOWED_INSERT_SHARE_THREAD_NUMBER + 15; i++) {
            bluetoothOppManager.startTransfer(device);
        }

        // success at least ALLOWED_INSERT_SHARE_THREAD_NUMBER times
        verify(mCallProxy,
                timeout(5_000).atLeast(ALLOWED_INSERT_SHARE_THREAD_NUMBER)).contentResolverInsert(
                any(), nullable(Uri.class), nullable(ContentValues.class));

        // there is at least a failed attempt
        assertThat(intended.get()).isTrue();
    }

    @Test
    public void isEnabled() {
        BluetoothOppManager bluetoothOppManager = BluetoothOppManager.getInstance(mContext);
        doReturn(true).when(mCallProxy).bluetoothAdapterIsEnabled(any());
        assertThat(bluetoothOppManager.isEnabled()).isTrue();
        doReturn(false).when(mCallProxy).bluetoothAdapterIsEnabled(any());
        assertThat(bluetoothOppManager.isEnabled()).isFalse();
    }

    @Test
    public void cleanUpSendingFileInfo_fileInfoCleaned() {
        BluetoothOppUtility.sSendFileMap.clear();
        Uri uri = Uri.parse("content:///a/new/folder/abc/xyz.txt");
        assertThat(BluetoothOppUtility.sSendFileMap.size()).isEqualTo(0);
        BluetoothOppManager.getInstance(mContext).saveSendingFileInfo("text/plain",
                uri.toString(), false, true);
        assertThat(BluetoothOppUtility.sSendFileMap.size()).isEqualTo(1);

        BluetoothOppManager.getInstance(mContext).cleanUpSendingFileInfo();
        assertThat(BluetoothOppUtility.sSendFileMap.size()).isEqualTo(0);
    }
}
