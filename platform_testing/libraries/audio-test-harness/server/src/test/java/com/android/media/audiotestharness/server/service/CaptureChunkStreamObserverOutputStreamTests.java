/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.media.audiotestharness.server.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.media.audiotestharness.proto.AudioTestHarnessService;

import com.google.common.collect.ImmutableList;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** Tests for the {@link CaptureChunkStreamObserverOutputStream}. */
@RunWith(JUnitParamsRunner.class)
public class CaptureChunkStreamObserverOutputStreamTests {

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock CountDownLatch mCountDownLatch;

    @Test(expected = NullPointerException.class)
    public void create_throwsNullPointerException_nullStreamObserver() throws Exception {
        CaptureChunkStreamObserverOutputStream.create(/* captureChunkStreamObserver= */ null);
    }

    @Test(expected = NullPointerException.class)
    public void createInternal_throwsNullPointerException_nullStreamObserver() throws Exception {
        CaptureChunkStreamObserverOutputStream.create(
                /* captureChunkStreamObserver= */ null, new CountDownLatch(0));
    }

    @Test(expected = NullPointerException.class)
    public void createInternal_throwsNullPointerException_nullCountDownLatch() throws Exception {
        CaptureChunkStreamObserverOutputStream.create(
                createStreamObserverThatWritesTo(new ArrayList<>()), /* countDownLatch= */ null);
    }

    @Test
    @Parameters(method = "getSingleByteParams")
    public void write_writesAsExpected_singleByte(
            String testName, int byteToWrite, int expectedByte) throws Exception {
        ArrayList<byte[]> writtenBytes = new ArrayList<>();
        OutputStream stream =
                CaptureChunkStreamObserverOutputStream.create(
                        createStreamObserverThatWritesTo(writtenBytes), mCountDownLatch);

        stream.write(byteToWrite);

        byte[] expectedByteArray = new byte[1];
        expectedByteArray[0] = (byte) expectedByte;

        assertListsContainSameByteArrays(
                testName, writtenBytes, ImmutableList.of(expectedByteArray));
    }

    public Object[] getSingleByteParams() throws Exception {
        return new Object[][] {
            {"Test Case One", 125, 0x7D},
            {"Test Case Two", 254658, 0xC2},
            {"Test Case Three", -44587, 0xD5}
        };
    }

    @Test(expected = IllegalStateException.class)
    public void write_throwsIllegalStateException_singleByte_afterClose() throws Exception {
        OutputStream stream =
                CaptureChunkStreamObserverOutputStream.create(
                        createStreamObserverThatWritesTo(new ArrayList<>()), mCountDownLatch);
        stream.close();

        stream.write(/* b= */ 42);
    }

    @Test
    @Parameters(method = "getByteArrayParams")
    public void write_writesAsExpected_multipleBytes(
            String testName, List<byte[]> byteArraysToWrite) throws Exception {
        ArrayList<byte[]> writtenBytes = new ArrayList<>();
        OutputStream stream =
                CaptureChunkStreamObserverOutputStream.create(
                        createStreamObserverThatWritesTo(writtenBytes), mCountDownLatch);

        // Write all of the arrays.
        for (byte[] bytes : byteArraysToWrite) {
            stream.write(bytes);
        }

        assertListsContainSameByteArrays(testName, byteArraysToWrite, writtenBytes);
    }

    public Object[] getByteArrayParams() throws Exception {
        byte[][] testBytes = generateRandomByteArrayData(/* numArrays= */ 4);
        return new Object[][] {
            {"Test Case One", ImmutableList.of(testBytes[0])},
            {
                "Test Case Two",
                ImmutableList.of(testBytes[0], testBytes[1], testBytes[2], testBytes[3])
            },
            {"Test Case Three", ImmutableList.of(testBytes[3], testBytes[1])},
        };
    }

    @Test(expected = IllegalStateException.class)
    public void write_throwsIllegalStateException_multipleBytes_afterClose() throws Exception {
        OutputStream stream =
                CaptureChunkStreamObserverOutputStream.create(
                        createStreamObserverThatWritesTo(new ArrayList<>()), mCountDownLatch);
        stream.close();

        stream.write(new byte[4]);
    }

    @Test
    @Parameters(method = "getByteArrayParamsWithOffsetsAndLengths")
    public void write_writesAsExpected_multipleBytesOffsetAndLength(
            String testName,
            List<byte[]> byteArraysToWrite,
            List<Integer> offsets,
            List<Integer> lengths,
            List<byte[]> expectedByteArrays)
            throws Exception {
        ArrayList<byte[]> writtenBytes = new ArrayList<>();
        OutputStream stream =
                CaptureChunkStreamObserverOutputStream.create(
                        createStreamObserverThatWritesTo(writtenBytes), mCountDownLatch);

        // Write all of the arrays with proper offsets and lengths.
        for (int i = 0; i < byteArraysToWrite.size(); i++) {
            stream.write(byteArraysToWrite.get(i), offsets.get(i), lengths.get(i));
        }

        assertListsContainSameByteArrays(testName, expectedByteArrays, writtenBytes);
    }

    public Object[] getByteArrayParamsWithOffsetsAndLengths() throws Exception {
        byte[][] testBytes = generateRandomByteArrayData(/* numArrays= */ 4);
        return new Object[][] {
            {
                "Test Case One",
                /* byteArraysToWrite= */ ImmutableList.of(testBytes[0]),
                /* offsets= */ ImmutableList.of(0),
                /* lengths= */ ImmutableList.of(8),
                /* expectedByteArrays= */ ImmutableList.of(testBytes[0])
            },
            {
                "Test Case Two",
                /* byteArraysToWrite= */ ImmutableList.of(
                        testBytes[0], testBytes[1], testBytes[2], testBytes[3]),
                /* offsets= */ ImmutableList.of(0, 0, 0, 0),
                /* lengths= */ ImmutableList.of(8, 16, 24, 32),
                /* expectedByteArrays= */ ImmutableList.of(
                        testBytes[0], testBytes[1], testBytes[2], testBytes[3])
            },
            {
                "Test Case Three",
                /* byteArraysToWrite= */ ImmutableList.of(testBytes[0]),
                /* offsets= */ ImmutableList.of(2),
                /* lengths= */ ImmutableList.of(5),
                /* expectedByteArrays= */ ImmutableList.of(Arrays.copyOfRange(testBytes[0], 2, 7))
            },
            {
                "Test Case Four",
                /* byteArraysToWrite= */ ImmutableList.of(
                        testBytes[0], testBytes[1], testBytes[2], testBytes[3]),
                /* offsets= */ ImmutableList.of(2, 10, 14, 25),
                /* lengths= */ ImmutableList.of(4, 5, 9, 2),
                /* expectedByteArrays= */ ImmutableList.of(
                        Arrays.copyOfRange(testBytes[0], 2, 6),
                        Arrays.copyOfRange(testBytes[1], 10, 15),
                        Arrays.copyOfRange(testBytes[2], 14, 23),
                        Arrays.copyOfRange(testBytes[3], 25, 27))
            },
            {
                "Test Case Five",
                /* byteArraysToWrite= */ ImmutableList.of(testBytes[3], testBytes[1]),
                /* offsets= */ ImmutableList.of(17, 2),
                /* lengths= */ ImmutableList.of(15, 10),
                /* expectedByteArrays= */ ImmutableList.of(
                        Arrays.copyOfRange(testBytes[3], 17, 32),
                        Arrays.copyOfRange(testBytes[1], 2, 12))
            },
        };
    }

    @Test(expected = IllegalStateException.class)
    public void write_throwsIllegalStateException_multipleBytesOffsetAndLength_afterClose()
            throws Exception {
        OutputStream stream =
                CaptureChunkStreamObserverOutputStream.create(
                        createStreamObserverThatWritesTo(new ArrayList<>()), mCountDownLatch);
        stream.close();

        stream.write(new byte[4], 2, 1);
    }

    @Test
    public void close_countDownsCountDownLatch() throws Exception {
        OutputStream stream =
                CaptureChunkStreamObserverOutputStream.create(
                        createStreamObserverThatWritesTo(new ArrayList<>()), mCountDownLatch);

        stream.close();

        verify(mCountDownLatch).countDown();
    }

    @Test
    public void awaitClose_callsCountDownLatchAwait() throws Exception {
        CaptureChunkStreamObserverOutputStream stream =
                CaptureChunkStreamObserverOutputStream.create(
                        createStreamObserverThatWritesTo(new ArrayList<>()), mCountDownLatch);

        stream.awaitClose();

        verify(mCountDownLatch).await();
    }

    @Test
    public void awaitClose_callsCountDownLatchAwait_withParameters() throws Exception {
        CaptureChunkStreamObserverOutputStream stream =
                CaptureChunkStreamObserverOutputStream.create(
                        createStreamObserverThatWritesTo(new ArrayList<>()), mCountDownLatch);

        stream.awaitClose(/* timeout= */ 1, TimeUnit.HOURS);

        verify(mCountDownLatch).await(/* timeout= */ 1, TimeUnit.HOURS);
    }

    @Test
    public void awaitClose_returnsProperValue_withParameters() throws Exception {
        when(mCountDownLatch.await(anyLong(), any())).thenReturn(true);

        CaptureChunkStreamObserverOutputStream stream =
                CaptureChunkStreamObserverOutputStream.create(
                        createStreamObserverThatWritesTo(new ArrayList<>()), mCountDownLatch);

        assertTrue(stream.awaitClose(/* timeout= */ 1, TimeUnit.HOURS));
    }

    /**
     * Asserts that the two provided {@link List}s contain the same byte arrays in the same order.
     *
     * <p>Appends the 'message' parameter to the end of failure messages.
     */
    private void assertListsContainSameByteArrays(
            String message, List<byte[]> expected, List<byte[]> actual) {
        assertEquals(
                String.format("Failure, Written Data Size does not match Expected: %s", message),
                expected.size(),
                actual.size());
        for (int i = 0; i < expected.size(); i++) {
            byte[] expectedBytes = expected.get(i);
            byte[] actualBytes = actual.get(i);

            assertArrayEquals(
                    String.format(
                            "Failure, Payload at Index %d does not match Expected: %s", i, message),
                    expectedBytes,
                    actualBytes);
        }
    }

    /**
     * Helper method that generates an array containing numArray byte arrays with random bytes.
     *
     * <p>The generated arrays are incrementally larger in multiples of 8. So for example, asking
     * for three arrays results in an array containing 3 separate byte arrays each with lengths 8,
     * 16, and 24 bytes respectively.
     */
    private static byte[][] generateRandomByteArrayData(int numArrays) {
        Random random = ThreadLocalRandom.current();

        byte[][] result = new byte[numArrays][];

        for (int i = 0; i < numArrays; i++) {
            result[i] = new byte[8 * (i + 1)];
            random.nextBytes(result[i]);
        }

        return result;
    }

    /**
     * Creates a new {@link StreamObserver} that writes the results of the write actions to the
     * provided list for later analysis and verification.
     *
     * @param writtenBytes a List of byte[]s representing each individual call to write.
     */
    private static ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk>
            createStreamObserverThatWritesTo(final List<byte[]> writtenBytes) {
        return new ServerCallStreamObserver<AudioTestHarnessService.CaptureChunk>() {
            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setOnReadyHandler(Runnable onReadyHandler) {}

            @Override
            public void disableAutoInboundFlowControl() {}

            @Override
            public void request(int count) {}

            @Override
            public void setMessageCompression(boolean enable) {}

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void setOnCancelHandler(Runnable onCancelHandler) {}

            @Override
            public void setCompression(String compression) {}

            @Override
            public void onNext(AudioTestHarnessService.CaptureChunk value) {
                writtenBytes.add(value.getData().toByteArray());
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onCompleted() {}
        };
    }
}
