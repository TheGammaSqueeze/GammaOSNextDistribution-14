/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.mediav2.common.cts;

import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.media.MediaCodec;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Pair;

import androidx.test.filters.SdkSuppress;

import com.android.media.codec.flags.Flags;

import java.util.ArrayDeque;
import java.util.LinkedList;

/**
 * Helper class for running mediacodec in asynchronous mode in large buffer mode. All mediacodec
 * callback events are registered in this object so that the client can take appropriate action
 * in time.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@RequiresFlagsEnabled(Flags.FLAG_LARGE_AUDIO_FRAME)
public class CodecAsyncHandlerMultiAccessUnits extends CodecAsyncHandler {
    private final LinkedList<Pair<Integer, ArrayDeque<MediaCodec.BufferInfo>>> mCbOutputQueue;

    public CodecAsyncHandlerMultiAccessUnits() {
        super();
        mCbOutputQueue = new LinkedList<>();
    }

    @Override
    public void clearQueues() {
        super.clearQueues();
        mLock.lock();
        try {
            mCbOutputQueue.clear();
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void resetContext() {
        super.resetContext();
    }

    @Override
    public void onOutputBuffersAvailable(@NonNull MediaCodec codec, int bufferIndex,
            @NonNull ArrayDeque<MediaCodec.BufferInfo> infos) {
        assertTrue(bufferIndex >= 0);
        mLock.lock();
        try {
            mCbOutputQueue.add(new Pair<>(bufferIndex, infos));
            mCondition.signalAll();
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void onOutputBufferAvailable(@NonNull MediaCodec codec, int bufferIndex,
            @NonNull MediaCodec.BufferInfo info) {
        assertTrue(bufferIndex >= 0);
        mLock.lock();
        try {
            ArrayDeque<MediaCodec.BufferInfo> infos = new ArrayDeque<>();
            infos.add(info);
            mCbOutputQueue.add(new Pair<>(bufferIndex, infos));
            mCondition.signalAll();
        } finally {
            mLock.unlock();
        }
    }

    public Pair<Integer, ArrayDeque<MediaCodec.BufferInfo>> getOutputs()
            throws InterruptedException {
        Pair<Integer, ArrayDeque<MediaCodec.BufferInfo>> element = null;
        mLock.lock();
        try {
            while (!mSignalledError) {
                if (mCbOutputQueue.isEmpty()) {
                    mCondition.await();
                } else {
                    element = mCbOutputQueue.remove(0);
                    break;
                }
            }
        } finally {
            mLock.unlock();
        }
        return element;
    }

    public Pair<Integer, ArrayDeque<MediaCodec.BufferInfo>> getWorkList()
            throws InterruptedException {
        Pair<Integer, ArrayDeque<MediaCodec.BufferInfo>> element = null;
        mLock.lock();
        try {
            while (!mSignalledError) {
                if (mCbInputQueue.isEmpty() && mCbOutputQueue.isEmpty()) {
                    mCondition.await();
                } else {
                    if (!mCbOutputQueue.isEmpty()) {
                        element = mCbOutputQueue.remove(0);
                    } else {
                        Pair<Integer, MediaCodec.BufferInfo> item = mCbInputQueue.remove(0);
                        element = new Pair<>(item.first, null);
                    }
                    break;
                }
            }
        } finally {
            mLock.unlock();
        }
        return element;
    }
}
