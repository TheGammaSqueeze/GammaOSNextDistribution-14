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

package com.google.android.tv.btservices.remote;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import com.google.android.tv.btservices.remote.RemoteProxy.DfuResult;
import com.google.android.tv.btservices.remote.Transport.Result;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class DfuManager implements TransportManager.GattStateListener {

    private static final String TAG = "Atv.DfuManager";

    private static final long FINAL_WAIT_MS = 10000;

    private static final String LOCK_ID = "dfu_lock";

    public interface Listener {

        void onDfuProgress(BluetoothDevice device, RemoteProxy.DfuResult result);
    }

    private TransportManager mTransportManager;
    private Listener mListener;
    private DfuBinary mDfu;
    private Handler mHandler = new Handler();
    private DfuResult mCurrentProgress = null;
    private BluetoothDevice mDevice;
    private AtomicBoolean mHasGattBeenDisconnected = new AtomicBoolean(false);

    public DfuManager(TransportManager transportManager) {
        mTransportManager = transportManager;
    }

    public CompletableFuture<RemoteProxy.DfuResult> requestDfu(
            BluetoothDevice device, DfuBinary dfu, Listener listener) {
        CompletableFuture<RemoteProxy.DfuResult> ret = new CompletableFuture<>();
        mDevice = device;
        mListener = listener;
        mDfu = dfu;
        mCurrentProgress = new DfuResult(0);
        mHasGattBeenDisconnected.set(false);
        mTransportManager.addGattStateListener(this);

        sendPackets(mDfu.getPackets(), 0, ret);
        return ret;
    }

    private void finish(Result res, CompletableFuture<RemoteProxy.DfuResult> ret) {
        mTransportManager.removeGattStateListener(this);
        mTransportManager.unlock(LOCK_ID);
        mListener = null;
        mCurrentProgress = null;
        mDevice = null;
        switch (res.code()) {
            case Result.SUCCESS:
                ret.complete(DfuResult.RESULT_SUCCESS);
                break;
            case Result.FAILURE_GATT_DISCONNECTED:
                ret.complete(DfuResult.RESULT_GATT_DISCONNECTED);
                break;
            case Result.FAILURE:
            default:
                ret.complete(DfuResult.RESULT_FAILURE);
                break;
        }
    }

    // TransportManager.GattStateListener implementation
    @Override
    public void onGattDisconnected() {
        mHasGattBeenDisconnected.set(true);
    }

    // TransportManager.GattStateListener implementation
    @Override
    public void onGattConnected() {
    }

    private void confirmDfu(CompletableFuture<RemoteProxy.DfuResult> ret) {
        Log.i(TAG, "confirmDfu: start final confirmation");
        // The completion of the DFU will trigger a disconnection of the Gatt. We wait for that and
        // then send a request to get the version. If the new version matches that of the DFU
        // version, then we return success. Otherwise, we've failed.
        final long startTime = SystemClock.elapsedRealtime();
        CompletableFuture.runAsync(() -> {
            Log.i(TAG, "confirmDfu: wait for disconnect");
            while (true) {
                long currentTime = SystemClock.elapsedRealtime();
                if (mHasGattBeenDisconnected.get()) {
                    break;
                }
                if ((currentTime - startTime) > FINAL_WAIT_MS) {
                    Log.w(TAG, "confirmDfu: timed out in final wait");
                    break;
                }
                try {
                    Thread.sleep(500);
                } catch (Exception e){
                    Log.e(TAG, "confirmDfu: failed to sleep in loop;");
                }
            }
            try {
                // Wait for two more seconds.
                Thread.sleep(2000);
            } catch (Exception e){
                Log.e(TAG, "confirmDfu: failed to sleep out of loop;");
            }
        }).thenRun(() -> {
            Log.i(TAG, "confirmDfu: dfu completed");
            mCurrentProgress = new DfuResult(1);
            mHandler.post(() -> {
                mListener.onDfuProgress(mDevice, mCurrentProgress);
                finish(TransportManager.RESULT_SUCCESS, ret);
            });
        });
    }

    private void sendPackets(Packet[] packets, int ind,
            CompletableFuture<RemoteProxy.DfuResult> ret) {

        int len = packets.length;
        if (ind == len) {
            confirmDfu(ret);
            return;
        }
        mTransportManager.handlePacket(packets[ind], LOCK_ID)
                .thenAccept(result -> {
                    if (result.code() != Result.SUCCESS) {
                        finish(result, ret);
                        return;
                    }
                    double previousPercent = (ind - 1) / ((double)len);
                    double percent = ind / ((double)len);
                    mCurrentProgress = new DfuResult(percent);

                    if (Math.floor(previousPercent * 100) != Math.floor(percent * 100)) {
                        mHandler.post(() -> mListener.onDfuProgress(mDevice, mCurrentProgress));
                    }
                    mHandler.post(() -> sendPackets(packets, ind + 1, ret));
                });
    }

    public DfuResult getProgress() {
        return mCurrentProgress;
    }
}
