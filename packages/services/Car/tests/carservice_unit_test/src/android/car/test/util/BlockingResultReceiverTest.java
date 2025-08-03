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

package android.car.test.util;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.util.Log;

import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public final class BlockingResultReceiverTest {

    private static final String TAG = BlockingResultReceiverTest.class.getName();

    private static final long TIMEOUT_MS = 3_000L;

    @Test
    public void testSend_andAssertCalled() throws Exception {
        // Arrange
        BlockingResultReceiver receiver = new BlockingResultReceiver(TIMEOUT_MS);
        int anyResultCode = 1;
        Bundle anyBundle = new Bundle();

        BlockingQueue<Integer> resultCodeBlockingQueue = new ArrayBlockingQueue<>(1);
        BlockingQueue<Bundle> dataBlockingQueue = new ArrayBlockingQueue<>(1);

        Thread consumer = new Thread(() -> {
            try {
                resultCodeBlockingQueue.offer(receiver.getResultCode());
                dataBlockingQueue.offer(receiver.getResultData());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Failed to consume results", e);
            } catch (Exception e) {
                Log.e(TAG, "Failed to consume results", e);
            }
        }, "ResultConsumerThread");

        // Start consumer thread (blocked until resultCode and resultData are available)
        consumer.start();

        // Act (unblock consumer thread by sending resultCode and resultData)
        receiver.send(anyResultCode, anyBundle);

        // Assert
        assertThat(resultCodeBlockingQueue.poll(3, TimeUnit.SECONDS)).isEqualTo(anyResultCode);
        assertThat(dataBlockingQueue.poll(3, TimeUnit.SECONDS)).isSameInstanceAs(anyBundle);
    }
}

