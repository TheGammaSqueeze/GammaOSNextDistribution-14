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

package android.mediav2.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecTestBase;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

@RunWith(Enclosed.class)
public class CodecUnitTest {
    private static final String TAG = "CodecUnitTest";
    static final int PER_TEST_TIMEOUT_MS = 10000;
    static final long STALL_TIME_MS = 1000;

    @SmallTest
    // Following tests were added in Android R and are not limited to c2.android.* codecs.
    // Hence limit the tests to Android R and above and also annotate as NonMainlineTest
    @SdkSuppress(minSdkVersion = 30)
    @NonMainlineTest
    public static class TestApi extends CodecTestBase {
        @Rule
        public Timeout timeout = new Timeout(PER_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        @After
        public void hasSeenError() {
            assertFalse(mAsyncHandle.hasSeenError());
        }

        public TestApi() {
            super("", "", "");
        }

        protected void enqueueInput(int bufferIndex) {
            fail("something went wrong, shouldn't have reached here");
        }

        protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                mSawOutputEOS = true;
            }
            mCodec.releaseOutputBuffer(bufferIndex, false);
        }

        private MediaFormat getSampleAudioFormat() {
            MediaFormat format = new MediaFormat();
            String mediaType = MediaFormat.MIMETYPE_AUDIO_AAC;
            format.setString(MediaFormat.KEY_MIME, mediaType);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 16000);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            return format;
        }

        private MediaFormat getSampleVideoFormat() {
            MediaFormat format = new MediaFormat();
            String mediaType = MediaFormat.MIMETYPE_VIDEO_AVC;
            format.setString(MediaFormat.KEY_MIME, mediaType);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 256000);
            format.setInteger(MediaFormat.KEY_WIDTH, 352);
            format.setInteger(MediaFormat.KEY_HEIGHT, 288);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1.0f);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            return format;
        }

        private Bundle updateBitrate(int bitrate) {
            final Bundle bitrateUpdate = new Bundle();
            bitrateUpdate.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate);
            return bitrateUpdate;
        }

        void testConfigureCodecForIncompleteFormat(MediaFormat format, String[] keys,
                boolean isEncoder) throws IOException {
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            if (isEncoder) {
                mCodec = MediaCodec.createEncoderByType(mediaType);
            } else {
                mCodec = MediaCodec.createDecoderByType(mediaType);
            }
            for (String key : keys) {
                MediaFormat formatClone = new MediaFormat(format);
                formatClone.removeKey(key);
                try {
                    mCodec.configure(formatClone, null, null,
                            isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0);
                    fail("codec configure succeeds with missing mandatory keys :: " + key);
                } catch (Exception e) {
                    if (!(e instanceof IllegalArgumentException)) {
                        fail("codec configure rec/exp :: " + e + " / IllegalArgumentException");
                    }
                    Log.v(TAG, "expected exception thrown", e);
                }
            }
            try {
                mCodec.configure(format, null, null,
                        isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0);
            } catch (Exception e) {
                fail("configure failed unexpectedly");
            } finally {
                mCodec.release();
            }
        }

        void testConfigureCodecForBadFlags(boolean isEncoder) throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            if (isEncoder) {
                mCodec = MediaCodec.createEncoderByType(mediaType);
            } else {
                mCodec = MediaCodec.createDecoderByType(mediaType);
            }
            try {
                mCodec.configure(format, null, null,
                        isEncoder ? 0 : MediaCodec.CONFIGURE_FLAG_ENCODE);
                fail("codec configure succeeds with bad configure flag");
            } catch (Exception e) {
                if (!(e instanceof IllegalArgumentException)) {
                    fail("codec configure rec/exp :: " + e + " / IllegalArgumentException");
                }
                Log.v(TAG, "expected exception thrown", e);
            } finally {
                mCodec.release();
            }
        }

        void tryConfigureCodecInInvalidState(MediaFormat format, boolean isAsync, String msg) {
            try {
                configureCodec(format, isAsync, false, true);
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryDequeueInputBufferInInvalidState(String msg) {
            try {
                mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryDequeueOutputBufferInInvalidState(String msg) {
            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                mCodec.dequeueOutputBuffer(info, Q_DEQ_TIMEOUT_US);
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryFlushInInvalidState(String msg) {
            try {
                flushCodec();
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryGetMetaData(String msg) {
            try {
                mCodec.getName();
            } catch (IllegalStateException e) {
                fail(msg + ", get name resulted in" + e.getMessage());
            }

            try {
                mCodec.getCanonicalName();
            } catch (IllegalStateException e) {
                fail(msg + ", get canonical name resulted in" + e.getMessage());
            }

            try {
                mCodec.getCodecInfo();
            } catch (IllegalStateException e) {
                fail(msg + ", get codec info resulted in" + e.getMessage());
            }

            try {
                mCodec.getMetrics();
            } catch (IllegalStateException e) {
                fail(msg + ", get metrics resulted in" + e.getMessage());
            }
        }

        void tryGetInputBufferInInvalidState(String msg) {
            try {
                mCodec.getInputBuffer(0);
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryGetInputFormatInInvalidState(String msg) {
            try {
                mCodec.getInputFormat();
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryGetOutputBufferInInvalidState(String msg) {
            try {
                mCodec.getOutputBuffer(0);
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryGetOutputFormatInInvalidState(String msg) {
            try {
                mCodec.getOutputFormat();
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }

            try {
                mCodec.getOutputFormat(0);
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryStartInInvalidState(String msg) {
            try {
                mCodec.start();
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryGetInputImageInInvalidState(String msg) {
            try {
                mCodec.getInputImage(0);
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryGetOutputImageInInvalidState(String msg) {
            try {
                mCodec.getOutputImage(0);
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryQueueInputBufferInInvalidState(String msg) {
            try {
                mCodec.queueInputBuffer(0, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        void tryReleaseOutputBufferInInvalidState(String msg) {
            try {
                mCodec.releaseOutputBuffer(0, false);
                fail(msg);
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#createByCodecName")
        @Test
        public void testCreateByCodecNameForNull() throws IOException {
            try {
                mCodec = MediaCodec.createByCodecName(null);
                fail("createByCodecName succeeds with null argument");
            } catch (NullPointerException e) {
                Log.v(TAG, "expected exception thrown", e);
            } finally {
                if (mCodec != null) mCodec.release();
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#createByCodecName")
        @Test
        public void testCreateByCodecNameForInvalidName() throws IOException {
            try {
                mCodec = MediaCodec.createByCodecName("invalid name");
                fail("createByCodecName succeeds with invalid name");
            } catch (IllegalArgumentException e) {
                Log.v(TAG, "expected exception thrown", e);
            } finally {
                if (mCodec != null) mCodec.release();
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#createDecoderByType")
        @Test
        public void testCreateDecoderByTypeForNull() throws IOException {
            try {
                mCodec = MediaCodec.createDecoderByType(null);
                fail("createDecoderByType succeeds with null argument");
            } catch (NullPointerException e) {
                Log.v(TAG, "expected exception thrown", e);
            } finally {
                if (mCodec != null) mCodec.release();
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#createDecoderByType")
        @Test
        public void testCreateDecoderByTypeForInvalidMediaType() throws IOException {
            try {
                mCodec = MediaCodec.createDecoderByType("invalid mediaType");
                fail("createDecoderByType succeeds with invalid mediaType");
            } catch (IllegalArgumentException e) {
                Log.v(TAG, "expected exception thrown", e);
            } finally {
                if (mCodec != null) mCodec.release();
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#createEncoderByType")
        @Test
        public void testCreateEncoderByTypeForNull() throws IOException {
            try {
                mCodec = MediaCodec.createEncoderByType(null);
                fail("createEncoderByType succeeds with null argument");
            } catch (NullPointerException e) {
                Log.v(TAG, "expected exception thrown", e);
            } finally {
                if (mCodec != null) mCodec.release();
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#createEncoderByType")
        @Test
        public void testCreateEncoderByTypeForInvalidMediaType() throws IOException {
            try {
                mCodec = MediaCodec.createEncoderByType("invalid mediaType");
                fail("createEncoderByType succeeds with invalid mediaType");
            } catch (IllegalArgumentException e) {
                Log.v(TAG, "expected exception thrown", e);
            } finally {
                if (mCodec != null) mCodec.release();
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        @Ignore("TODO(b/151302868)")
        public void testConfigureForNullFormat() throws IOException {
            mCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mCodec.configure(null, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        @Ignore("TODO(b/151302868)")
        public void testConfigureForEmptyFormat() throws IOException {
            mCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mCodec.configure(new MediaFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        @Ignore("TODO(b/151302868)")
        public void testConfigureAudioDecodeForIncompleteFormat() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String[] mandatoryKeys =
                    new String[]{MediaFormat.KEY_MIME, MediaFormat.KEY_CHANNEL_COUNT,
                            MediaFormat.KEY_SAMPLE_RATE};
            testConfigureCodecForIncompleteFormat(format, mandatoryKeys, false);
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        @Ignore("TODO(b/151302868)")
        public void testConfigureAudioEncodeForIncompleteFormat() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String[] mandatoryKeys =
                    new String[]{MediaFormat.KEY_MIME, MediaFormat.KEY_CHANNEL_COUNT,
                            MediaFormat.KEY_SAMPLE_RATE, MediaFormat.KEY_BIT_RATE};
            testConfigureCodecForIncompleteFormat(format, mandatoryKeys, true);
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        @Ignore("TODO(b/151302868)")
        public void testConfigureVideoDecodeForIncompleteFormat() throws IOException {
            MediaFormat format = getSampleVideoFormat();
            String[] mandatoryKeys =
                    new String[]{MediaFormat.KEY_MIME, MediaFormat.KEY_WIDTH,
                            MediaFormat.KEY_HEIGHT};
            testConfigureCodecForIncompleteFormat(format, mandatoryKeys, false);
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        @Ignore("TODO(b/151302868, b/151303041)")
        public void testConfigureVideoEncodeForIncompleteFormat() throws IOException {
            MediaFormat format = getSampleVideoFormat();
            String[] mandatoryKeys =
                    new String[]{MediaFormat.KEY_MIME, MediaFormat.KEY_WIDTH,
                            MediaFormat.KEY_HEIGHT, MediaFormat.KEY_I_FRAME_INTERVAL,
                            MediaFormat.KEY_FRAME_RATE, MediaFormat.KEY_BIT_RATE,
                            MediaFormat.KEY_COLOR_FORMAT};
            testConfigureCodecForIncompleteFormat(format, mandatoryKeys, true);
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        @Ignore("TODO(b/151304147)")
        public void testConfigureEncoderForBadFlags() throws IOException {
            testConfigureCodecForBadFlags(true);
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        @Ignore("TODO(b/151304147)")
        public void testConfigureDecoderForBadFlags() throws IOException {
            testConfigureCodecForBadFlags(false);
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        public void testConfigureInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                // configure in initialized state
                tryConfigureCodecInInvalidState(format, isAsync,
                        "codec configure succeeds in initialized state");
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        @Ignore("TODO(b/151894670)")
        public void testConfigureAfterStart() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                // configure in running state
                tryConfigureCodecInInvalidState(format, isAsync,
                        "codec configure succeeds after Start()");
                queueEOS();
                waitForAllOutputs();
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        @Ignore("TODO(b/151894670)")
        public void testConfigureAfterQueueInputBuffer() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                queueEOS();
                // configure in running state
                tryConfigureCodecInInvalidState(format, isAsync,
                        "codec configure succeeds after QueueInputBuffer()");
                waitForAllOutputs();
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        public void testConfigureInEOSState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                queueEOS();
                waitForAllOutputs();
                // configure in eos state
                tryConfigureCodecInInvalidState(format, isAsync,
                        "codec configure succeeds in eos state");
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        @Ignore("TODO(b/147576107)")
        public void testConfigureInFlushState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                flushCodec();
                // configure in flush state
                tryConfigureCodecInInvalidState(format, isAsync,
                        "codec configure succeeds in flush state");
                if (mIsCodecInAsyncMode) mCodec.start();
                queueEOS();
                waitForAllOutputs();
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        public void testConfigureInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.stop();
                // configure in uninitialized state
                try {
                    configureCodec(format, isAsync, false, true);
                } catch (Exception e) {
                    fail("codec configure fails in uninitialized state");
                }
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#configure")
        @Test
        public void testConfigureInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            tryConfigureCodecInInvalidState(format, false,
                    "codec configure succeeds in release state");
        }

        @ApiTest(apis = "android.media.MediaCodec#dequeueInputBuffer")
        @Test
        public void testDequeueInputBufferInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                // dequeue buffer in uninitialized state
                tryDequeueInputBufferInInvalidState(
                        "dequeue input buffer succeeds in uninitialized state");
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                // dequeue buffer in stopped state
                tryDequeueInputBufferInInvalidState(
                        "dequeue input buffer succeeds in stopped state");
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#dequeueInputBuffer")
        @Test
        public void testDequeueInputBufferInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                // dequeue buffer in initialized state
                tryDequeueInputBufferInInvalidState(
                        "dequeue input buffer succeeds in initialized state");
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#dequeueInputBuffer")
        @Test
        public void testDequeueInputBufferInRunningState()
                throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                if (mIsCodecInAsyncMode) {
                    // dequeue buffer in running state
                    tryDequeueInputBufferInInvalidState(
                            "dequeue input buffer succeeds in running state, async mode");
                }
                queueEOS();
                waitForAllOutputs();
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#dequeueInputBuffer")
        @Test
        public void testDequeueInputBufferInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            // dequeue buffer in released state
            tryDequeueInputBufferInInvalidState(
                    "dequeue input buffer succeeds in release state");
        }

        @ApiTest(apis = "android.media.MediaCodec#dequeueOutputBuffer")
        @Test
        public void testDequeueOutputBufferInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                // dequeue buffer in uninitialized state
                tryDequeueOutputBufferInInvalidState(
                        "dequeue output buffer succeeds in uninitialized state");
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                // dequeue buffer in stopped state
                tryDequeueOutputBufferInInvalidState(
                        "dequeue output buffer succeeds in stopped state");
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#dequeueOutputBuffer")
        @Test
        public void testDequeueOutputBufferInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                // dequeue buffer in initialized state
                tryDequeueOutputBufferInInvalidState(
                        "dequeue output buffer succeeds in initialized state");
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#dequeueOutputBuffer")
        @Test
        public void testDequeueOutputBufferInRunningState()
                throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                if (mIsCodecInAsyncMode) {
                    // dequeue buffer in running state
                    tryDequeueOutputBufferInInvalidState(
                            "dequeue output buffer succeeds in running state, async mode");
                }
                queueEOS();
                waitForAllOutputs();
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#dequeueOutputBuffer")
        @Test
        public void testDequeueOutputBufferInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            // dequeue buffer in released state
            tryDequeueOutputBufferInInvalidState(
                    "dequeue output buffer succeeds in release state");
        }

        @ApiTest(apis = "android.media.MediaCodec#flush")
        @Test
        public void testFlushInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                // flush uninitialized state
                tryFlushInInvalidState("codec flush succeeds in uninitialized state");
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                // flush in stopped state
                tryFlushInInvalidState("codec flush succeeds in stopped state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#flush")
        @Test
        public void testFlushInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                // flush in initialized state
                tryFlushInInvalidState("codec flush succeeds in initialized state");
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#flush")
        @Test
        @Ignore("TODO(b/147576107)")
        public void testFlushInRunningState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            configureCodec(format, true, false, true);
            mCodec.start();
            flushCodec();
            Thread.sleep(STALL_TIME_MS);
            assertTrue("received input buffer callback before start",
                    mAsyncHandle.isInputQueueEmpty());
            mCodec.start();
            Thread.sleep(STALL_TIME_MS);
            assertFalse("did not receive input buffer callback after start",
                    mAsyncHandle.isInputQueueEmpty());
            mCodec.stop();
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#flush")
        @Test
        public void testFlushInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            tryFlushInInvalidState("codec flush succeeds in release state");
        }

        @ApiTest(apis = {"android.media.MediaCodec#getName",
                         "android.media.MediaCodec#getCanonicalName",
                         "android.media.MediaCodec#getCodecInfo",
                         "android.media.MediaCodec#getMetrics"})
        @Test
        public void testGetMetaDataInUnInitState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                tryGetMetaData("codec get metadata call fails in uninitialized state");
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                queueEOS();
                waitForAllOutputs();
                mCodec.stop();
                tryGetMetaData("codec get metadata call fails in stopped state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = {"android.media.MediaCodec#getName",
                         "android.media.MediaCodec#getCanonicalName",
                         "android.media.MediaCodec#getCodecInfo",
                         "android.media.MediaCodec#getMetrics"})
        @Test
        public void testGetMetaDataInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                tryGetMetaData("codec get metadata call fails in initialized state");
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = {"android.media.MediaCodec#getName",
                         "android.media.MediaCodec#getCanonicalName",
                         "android.media.MediaCodec#getCodecInfo",
                         "android.media.MediaCodec#getMetrics"})
        @Test
        public void testGetMetaDataInRunningState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                tryGetMetaData("codec get metadata call fails in running state");
                queueEOS();
                waitForAllOutputs();
                tryGetMetaData("codec get metadata call fails in eos state");
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = {"android.media.MediaCodec#getName",
                         "android.media.MediaCodec#getCanonicalName",
                         "android.media.MediaCodec#getCodecInfo",
                         "android.media.MediaCodec#getMetrics"})
        @Test
        public void testGetMetaDataInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            try {
                mCodec.getCanonicalName();
                fail("get canonical name succeeds after codec release");
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }

            try {
                mCodec.getCodecInfo();
                fail("get codec info succeeds after codec release");
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }

            try {
                mCodec.getName();
                fail("get name succeeds after codec release");
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }

            try {
                mCodec.getMetrics();
                fail("get metrics succeeds after codec release");
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#setCallback")
        @Test
        public void testSetCallBackInUnInitState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);

            boolean isAsync = true;
            // set component in async mode
            mAsyncHandle.setCallBack(mCodec, isAsync);
            mIsCodecInAsyncMode = isAsync;
            // configure component to sync mode
            configureCodec(format, !isAsync, false, true);
            mCodec.start();
            queueEOS();
            waitForAllOutputs();
            mCodec.stop();

            // set component in sync mode
            mAsyncHandle.setCallBack(mCodec, !isAsync);
            mIsCodecInAsyncMode = !isAsync;
            // configure component in async mode
            configureCodec(format, isAsync, false, true);
            mCodec.start();
            queueEOS();
            waitForAllOutputs();
            mCodec.stop();
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#setCallback")
        @Test
        public void testSetCallBackInInitState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);

            // configure component in async mode
            boolean isAsync = true;
            configureCodec(format, isAsync, false, true);
            // change component to sync mode
            mAsyncHandle.setCallBack(mCodec, !isAsync);
            mIsCodecInAsyncMode = !isAsync;
            mCodec.start();
            queueEOS();
            waitForAllOutputs();
            mCodec.stop();

            // configure component in sync mode
            configureCodec(format, !isAsync, false, true);
            // change the component to operate in async mode
            mAsyncHandle.setCallBack(mCodec, isAsync);
            mIsCodecInAsyncMode = isAsync;
            mCodec.start();
            queueEOS();
            waitForAllOutputs();
            mCodec.stop();
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#setCallback")
        @Test
        @Ignore("TODO(b/151305056)")
        public void testSetCallBackInRunningState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean isAsync = false;
            // configure codec in sync mode
            configureCodec(format, isAsync, false, true);
            mCodec.start();
            // set call back should fail once the component is sailed to running state
            try {
                mAsyncHandle.setCallBack(mCodec, !isAsync);
                mIsCodecInAsyncMode = !isAsync;
                fail("set call back succeeds in running state");
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
            queueEOS();
            waitForAllOutputs();
            mCodec.stop();

            // configure codec in async mode
            configureCodec(format, !isAsync, false, true);
            mCodec.start();
            // set call back should fail once the component is sailed to running state
            try {
                mAsyncHandle.setCallBack(mCodec, isAsync);
                mIsCodecInAsyncMode = isAsync;
                fail("set call back succeeds in running state");
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
            queueEOS();
            waitForAllOutputs();
            mCodec.stop();
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#setCallback")
        @Test
        public void testSetCallBackInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            // set callbacks in release state
            try {
                mAsyncHandle.setCallBack(mCodec, false);
                fail("set call back succeeds in released state");
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputBuffer")
        @Test
        public void testGetInputBufferInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                tryGetInputBufferInInvalidState("getInputBuffer succeeds in uninitialized state");
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                tryGetInputBufferInInvalidState("getInputBuffer succeeds in stopped state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputBuffer")
        @Test
        public void testGetInputBufferInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                tryGetInputBufferInInvalidState("getInputBuffer succeeds in initialized state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputBuffer")
        @Test
        @Ignore("TODO(b/151304147)")
        public void testGetInputBufferInRunningState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                try {
                    ByteBuffer buffer = mCodec.getInputBuffer(-1);
                    assertNull("getInputBuffer succeeds for bad buffer index " + -1, buffer);
                } catch (Exception e) {
                    fail("getInputBuffer rec/exp :: " + e + " / null");
                }
                int bufferIndex = mIsCodecInAsyncMode ? mAsyncHandle.getInput().first :
                        mCodec.dequeueInputBuffer(-1);
                ByteBuffer buffer = mCodec.getInputBuffer(bufferIndex);
                assertNotNull(buffer);
                ByteBuffer bufferDup = mCodec.getInputBuffer(bufferIndex);
                assertNotNull(bufferDup);
                enqueueEOS(bufferIndex);
                waitForAllOutputs();
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputBuffer")
        @Test
        public void testGetInputBufferInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            tryGetInputBufferInInvalidState("getInputBuffer succeeds in release state");
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputFormat")
        @Test
        public void testGetInputFormatInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                tryGetInputFormatInInvalidState("getInputFormat succeeds in uninitialized state");
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                tryGetInputFormatInInvalidState("getInputFormat succeeds in stopped state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputFormat")
        @Test
        public void testGetInputFormatInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                try {
                    mCodec.getInputFormat();
                } catch (Exception e) {
                    fail("getInputFormat fails in initialized state");
                }
                mCodec.start();
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputFormat")
        @Test
        public void testGetInputFormatInRunningState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                try {
                    mCodec.getInputFormat();
                } catch (Exception e) {
                    fail("getInputFormat fails in running state");
                }
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputFormat")
        @Test
        public void testGetInputFormatInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            tryGetInputFormatInInvalidState("getInputFormat succeeds in release state");
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputBuffer")
        @Test
        public void testGetOutputBufferInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                tryGetOutputBufferInInvalidState("getOutputBuffer succeeds in uninitialized state");
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                tryGetOutputBufferInInvalidState("getOutputBuffer succeeds in stopped state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputBuffer")
        @Test
        public void testGetOutputBufferInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                tryGetOutputBufferInInvalidState("getOutputBuffer succeeds in initialized state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputBuffer")
        @Test
        @Ignore("TODO(b/151304147)")
        public void testGetOutputBufferInRunningState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                try {
                    ByteBuffer buffer = mCodec.getOutputBuffer(-1);
                    assertNull("getOutputBuffer succeeds for bad buffer index " + -1, buffer);
                } catch (Exception e) {
                    fail("getOutputBuffer rec/exp :: " + e + " / null");
                }
                queueEOS();
                int bufferIndex = 0;
                while (!mSawOutputEOS) {
                    if (mIsCodecInAsyncMode) {
                        Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
                        bufferIndex = element.first;
                        ByteBuffer buffer = mCodec.getOutputBuffer(bufferIndex);
                        assertNotNull(buffer);
                        dequeueOutput(element.first, element.second);
                    } else {
                        bufferIndex = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                        if (bufferIndex >= 0) {
                            ByteBuffer buffer = mCodec.getOutputBuffer(bufferIndex);
                            assertNotNull(buffer);
                            dequeueOutput(bufferIndex, outInfo);
                        }
                    }
                }
                try {
                    ByteBuffer buffer = mCodec.getOutputBuffer(bufferIndex);
                    assertNull("getOutputBuffer succeeds for buffer index not owned by client",
                            buffer);
                } catch (Exception e) {
                    fail("getOutputBuffer rec/exp :: " + e + " / null");
                }
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputBuffer")
        @Test
        public void testGetOutputBufferInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            tryGetOutputBufferInInvalidState("getOutputBuffer succeeds in release state");
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputFormat")
        @Test
        public void testGetOutputFormatInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                tryGetOutputFormatInInvalidState("getOutputFormat succeeds in uninitialized state");
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                tryGetOutputFormatInInvalidState("getOutputFormat succeeds in stopped state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputFormat")
        @Test
        public void testGetOutputFormatInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                try {
                    mCodec.getOutputFormat();
                } catch (Exception e) {
                    fail("getOutputFormat fails in initialized state");
                }
                try {
                    mCodec.getOutputFormat(0);
                    fail("getOutputFormat succeeds in released state");
                } catch (IllegalStateException e) {
                    Log.v(TAG, "expected exception thrown", e);
                }
                mCodec.start();
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputFormat")
        @Test
        @Ignore("TODO(b/151304147)")
        public void testGetOutputFormatInRunningState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                queueEOS();
                try {
                    mCodec.getOutputFormat();
                } catch (Exception e) {
                    fail("getOutputFormat fails in running state");
                }
                try {
                    MediaFormat outputFormat = mCodec.getOutputFormat(-1);
                    assertNull("getOutputFormat succeeds for bad buffer index " + -1, outputFormat);
                } catch (Exception e) {
                    fail("getOutputFormat rec/exp :: " + e + " / null");
                }
                int bufferIndex = 0;
                while (!mSawOutputEOS) {
                    if (mIsCodecInAsyncMode) {
                        Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
                        bufferIndex = element.first;
                        MediaFormat outputFormat = mCodec.getOutputFormat(bufferIndex);
                        assertNotNull(outputFormat);
                        dequeueOutput(element.first, element.second);
                    } else {
                        bufferIndex = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                        if (bufferIndex >= 0) {
                            MediaFormat outputFormat = mCodec.getOutputFormat(bufferIndex);
                            assertNotNull(outputFormat);
                            dequeueOutput(bufferIndex, outInfo);
                        }
                    }
                }
                try {
                    MediaFormat outputFormat = mCodec.getOutputFormat(bufferIndex);
                    assertNull("getOutputFormat succeeds for index not owned by client",
                            outputFormat);
                } catch (Exception e) {
                    fail("getOutputFormat rec/exp :: " + e + " / null");
                }
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputFormat")
        @Test
        public void testGetOutputFormatInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            tryGetOutputFormatInInvalidState("getOutputFormat succeeds in release state");
        }

        @ApiTest(apis = "android.media.MediaCodec#setParameters")
        @Test
        public void testSetParametersInUnInitState() throws IOException {
            MediaFormat format = getSampleVideoFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            int bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            // call set param in uninitialized state
            mCodec.setParameters(null);
            mCodec.setParameters(updateBitrate(bitrate >> 1));
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                mCodec.setParameters(null);
                mCodec.setParameters(updateBitrate(bitrate >> 1));
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#setParameters")
        @Test
        public void testSetParametersInInitState() throws IOException {
            MediaFormat format = getSampleVideoFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            int bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.setParameters(null);
                mCodec.setParameters(updateBitrate(bitrate >> 1));
                mCodec.start();
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#setParameters")
        @Test
        public void testSetParametersInRunningState() throws IOException, InterruptedException {
            MediaFormat format = getSampleVideoFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            int bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.setParameters(null);
                mCodec.setParameters(updateBitrate(bitrate >> 1));
                queueEOS();
                mCodec.setParameters(null);
                mCodec.setParameters(updateBitrate(bitrate << 1));
                waitForAllOutputs();
                mCodec.setParameters(null);
                mCodec.setParameters(updateBitrate(bitrate));
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#setParameters")
        @Test
        public void testSetParametersInReleaseState() throws IOException {
            MediaFormat format = getSampleVideoFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            int bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            try {
                mCodec.setParameters(updateBitrate(bitrate >> 1));
                fail("Codec set parameter succeeds in release mode");
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#start")
        @Test
        public void testStartInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            // call start in uninitialized state
            tryStartInInvalidState("codec start succeeds before initialization");
            configureCodec(format, false, false, true);
            mCodec.start();
            mCodec.stop();
            // call start in stopped state
            tryStartInInvalidState("codec start succeeds in stopped state");
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#start")
        @Test
        public void testStartInRunningState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            configureCodec(format, false, false, true);
            mCodec.start();
            // call start in running state
            tryStartInInvalidState("codec start succeeds in running state");
            mCodec.stop();
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#start")
        @Test
        public void testStartInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            // call start in release state
            tryStartInInvalidState("codec start succeeds in release state");
        }

        @ApiTest(apis = "android.media.MediaCodec#stop")
        @Test
        public void testStopInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.stop();
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#stop")
        @Test
        public void testStopInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#stop")
        @Test
        public void testStopInRunningState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                queueEOS();
                mCodec.stop();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#stop")
        @Test
        public void testStopInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            try {
                mCodec.stop();
                fail("Codec stop succeeds in release mode");
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#reset")
        @Test
        public void testResetInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.reset();
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#reset")
        @Test
        public void testResetInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#reset")
        @Test
        public void testResetInRunningState() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                queueEOS();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#reset")
        @Test
        public void testResetInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            try {
                mCodec.reset();
                fail("Codec reset succeeds in release mode");
            } catch (IllegalStateException e) {
                Log.v(TAG, "expected exception thrown", e);
            }
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputImage")
        @Test
        public void testGetInputImageInUnInitState() throws IOException {
            MediaFormat format = getSampleVideoFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                tryGetInputImageInInvalidState("getInputImage succeeds in uninitialized state");
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                tryGetInputImageInInvalidState("getInputImage succeeds in stopped state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputImage")
        @Test
        public void testGetInputImageInInitState() throws IOException {
            MediaFormat format = getSampleVideoFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                tryGetInputImageInInvalidState("getInputImage succeeds in initialized state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputImage")
        @Test
        @Ignore("TODO(b/151304147)")
        public void testGetInputImageInRunningStateVideo()
                throws IOException, InterruptedException {
            MediaFormat format = getSampleVideoFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                try {
                    Image img = mCodec.getInputImage(-1);
                    assertNull("getInputImage succeeds for bad buffer index " + -1, img);
                } catch (Exception e) {
                    fail("getInputImage rec/exp :: " + e + " / null");
                }
                int bufferIndex = mIsCodecInAsyncMode ? mAsyncHandle.getInput().first :
                        mCodec.dequeueInputBuffer(-1);
                Image img = mCodec.getInputImage(bufferIndex);
                assertNotNull(img);
                Image imgDup = mCodec.getInputImage(bufferIndex);
                assertNotNull(imgDup);
                enqueueEOS(bufferIndex);
                waitForAllOutputs();
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputImage")
        @Test
        @Ignore("TODO(b/151304147)")
        public void testGetInputImageInRunningStateAudio()
                throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                try {
                    Image img = mCodec.getInputImage(-1);
                    assertNull("getInputImage succeeds for bad buffer index " + -1, img);
                } catch (Exception e) {
                    fail("getInputImage rec/exp :: " + e + " / null");
                }
                int bufferIndex = mIsCodecInAsyncMode ? mAsyncHandle.getInput().first :
                        mCodec.dequeueInputBuffer(-1);
                Image img = mCodec.getInputImage(bufferIndex);
                assertNull("getInputImage returns non null for buffers that do not hold raw img",
                        img);
                enqueueEOS(bufferIndex);
                waitForAllOutputs();
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getInputImage")
        @Test
        public void testGetInputImageInReleaseState() throws IOException {
            MediaFormat format = getSampleVideoFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            tryGetInputImageInInvalidState("getInputImage succeeds in release state");
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputImage")
        @Test
        public void testGetOutputImageInUnInitState() throws IOException {
            MediaFormat format = getSampleVideoFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createDecoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                tryGetOutputImageInInvalidState("getOutputImage succeeds in uninitialized state");
                configureCodec(format, isAsync, false, false);
                mCodec.start();
                mCodec.stop();
                tryGetOutputImageInInvalidState("getOutputImage succeeds in stopped state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputImage")
        @Test
        public void testGetOutputImageInInitState() throws IOException {
            MediaFormat format = getSampleVideoFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createDecoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, false);
                tryGetOutputImageInInvalidState("getOutputImage succeeds in initialized state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputImage")
        @Test
        @Ignore("TODO(b/151304147)")
        public void testGetOutputImageInRunningState() throws IOException, InterruptedException {
            MediaFormat format = getSampleVideoFormat();
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createDecoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, false);
                mCodec.start();
                try {
                    Image img = mCodec.getOutputImage(-1);
                    assertNull("getOutputImage succeeds for bad buffer index " + -1, img);
                } catch (Exception e) {
                    fail("getOutputImage rec/exp :: " + e + " / null");
                }
                queueEOS();
                int bufferIndex = 0;
                while (!mSawOutputEOS) {
                    if (mIsCodecInAsyncMode) {
                        Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
                        bufferIndex = element.first;
                        dequeueOutput(element.first, element.second);
                    } else {
                        bufferIndex = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                        if (bufferIndex >= 0) {
                            dequeueOutput(bufferIndex, outInfo);
                        }
                    }
                }
                try {
                    Image img = mCodec.getOutputImage(bufferIndex);
                    assertNull("getOutputImage succeeds for buffer index not owned by client", img);
                } catch (Exception e) {
                    fail("getOutputBuffer rec/exp :: " + e + " / null");
                }
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#getOutputImage")
        @Test
        public void testGetOutputImageInReleaseState() throws IOException {
            MediaFormat format = getSampleVideoFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createDecoderByType(mediaType);
            mCodec.release();
            tryGetOutputImageInInvalidState("getOutputImage succeeds in release state");
        }

        @ApiTest(apis = "android.media.MediaCodec#queueInputBuffer")
        @Test
        public void testQueueInputBufferInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                tryQueueInputBufferInInvalidState(
                        "queueInputBuffer succeeds in uninitialized state");
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                tryQueueInputBufferInInvalidState("queueInputBuffer succeeds in stopped state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#queueInputBuffer")
        @Test
        public void testQueueInputBufferInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                tryQueueInputBufferInInvalidState("queueInputBuffer succeeds in initialized state");
                mCodec.start();
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#queueInputBuffer")
        @Test
        public void testQueueInputBufferWithBadIndex() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                try {
                    mCodec.queueInputBuffer(-1, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    fail("queueInputBuffer succeeds with bad buffer index :: " + -1);
                } catch (Exception e) {
                    Log.v(TAG, "expected exception thrown", e);
                }
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#queueInputBuffer")
        @Test
        public void testQueueInputBufferWithBadSize() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                int bufferIndex = mIsCodecInAsyncMode ? mAsyncHandle.getInput().first :
                        mCodec.dequeueInputBuffer(-1);
                ByteBuffer buffer = mCodec.getInputBuffer(bufferIndex);
                assertNotNull(buffer);
                try {
                    mCodec.queueInputBuffer(bufferIndex, 0, buffer.capacity() + 100, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    fail("queueInputBuffer succeeds with bad size param :: " + buffer.capacity() +
                            100);
                } catch (Exception e) {
                    Log.v(TAG, "expected exception thrown", e);
                }
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#queueInputBuffer")
        @Test
        public void testQueueInputBufferWithBadBuffInfo() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                int bufferIndex = mIsCodecInAsyncMode ? mAsyncHandle.getInput().first :
                        mCodec.dequeueInputBuffer(-1);
                ByteBuffer buffer = mCodec.getInputBuffer(bufferIndex);
                assertNotNull(buffer);
                try {
                    mCodec.queueInputBuffer(bufferIndex, 16, buffer.capacity(), 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    fail("queueInputBuffer succeeds with bad offset and size param");
                } catch (Exception e) {
                    Log.v(TAG, "expected exception thrown", e);
                }
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#queueInputBuffer")
        @Test
        @Ignore("TODO(b/151305059)")
        public void testQueueInputBufferWithBadOffset() throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                int bufferIndex = mIsCodecInAsyncMode ? mAsyncHandle.getInput().first :
                        mCodec.dequeueInputBuffer(-1);
                ByteBuffer buffer = mCodec.getInputBuffer(bufferIndex);
                assertNotNull(buffer);
                try {
                    mCodec.queueInputBuffer(bufferIndex, -1, buffer.capacity(), 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    fail("queueInputBuffer succeeds with bad offset param :: " + -1);
                } catch (Exception e) {
                    Log.v(TAG, "expected exception thrown", e);
                }
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#queueInputBuffer")
        @Test
        public void testQueueInputBufferInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            tryQueueInputBufferInInvalidState("queueInputBuffer succeeds in release state");
        }

        @ApiTest(apis = {"android.media.MediaCodec#queueInputBuffer",
                "android.media.MediaCodec#queueSecureInputBuffer"})
        @Test
        public void testExceptionThrownWhenBufferIsEOSAndDecodeOnly() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                final int flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        | MediaCodec.BUFFER_FLAG_DECODE_ONLY;

                Assert.assertThrows(MediaCodec.InvalidBufferFlagsException.class,
                        () -> mCodec.queueInputBuffer(0, 0, 0, 0, flags));

                Assert.assertThrows(MediaCodec.InvalidBufferFlagsException.class,
                        () -> mCodec.queueSecureInputBuffer(0, 0, null, 0, flags));
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#releaseOutputBuffer")
        @Test
        public void testReleaseOutputBufferInUnInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                tryReleaseOutputBufferInInvalidState(
                        "releaseOutputBuffer succeeds in uninitialized state");
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                mCodec.stop();
                tryReleaseOutputBufferInInvalidState(
                        "releaseOutputBuffer succeeds in stopped state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#releaseOutputBuffer")
        @Test
        public void testReleaseOutputBufferInInitState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                tryReleaseOutputBufferInInvalidState(
                        "releaseOutputBuffer succeeds in initialized state");
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#releaseOutputBuffer")
        @Test
        public void testReleaseOutputBufferInRunningState()
                throws IOException, InterruptedException {
            MediaFormat format = getSampleAudioFormat();
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                try {
                    mCodec.releaseOutputBuffer(-1, false);
                    fail("releaseOutputBuffer succeeds for bad buffer index " + -1);
                } catch (MediaCodec.CodecException e) {
                    Log.v(TAG, "expected exception thrown", e);
                }
                queueEOS();
                int bufferIndex = 0;
                while (!mSawOutputEOS) {
                    if (mIsCodecInAsyncMode) {
                        Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
                        bufferIndex = element.first;
                        ByteBuffer buffer = mCodec.getOutputBuffer(bufferIndex);
                        assertNotNull(buffer);
                        dequeueOutput(element.first, element.second);
                    } else {
                        bufferIndex = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                        if (bufferIndex >= 0) {
                            ByteBuffer buffer = mCodec.getOutputBuffer(bufferIndex);
                            assertNotNull(buffer);
                            dequeueOutput(bufferIndex, outInfo);
                        }
                    }
                }
                try {
                    mCodec.releaseOutputBuffer(bufferIndex, false);
                    fail("releaseOutputBuffer succeeds for buffer index not owned by client");
                } catch (MediaCodec.CodecException e) {
                    Log.v(TAG, "expected exception thrown", e);
                }
                mCodec.stop();
                mCodec.reset();
            }
            mCodec.release();
        }

        @ApiTest(apis = "android.media.MediaCodec#releaseOutputBuffer")
        @Test
        public void testReleaseOutputBufferInReleaseState() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            tryReleaseOutputBufferInInvalidState(
                    "releaseOutputBuffer succeeds in release state");
        }

        @ApiTest(apis = "android.media.MediaCodec#releaseOutputBuffer")
        @Test
        public void testReleaseIdempotent() throws IOException {
            MediaFormat format = getSampleAudioFormat();
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mediaType);
            mCodec.release();
            mCodec.release();
        }
    }

    @SmallTest
    // Following tests were added in Android R and are not limited to c2.android.* codecs.
    // Hence limit the tests to Android R and above and also annotate as NonMainlineTest
    @SdkSuppress(minSdkVersion = 30)
    @NonMainlineTest
    public static class TestApiNative {
        @Rule
        public Timeout timeout = new Timeout(PER_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        static {
            System.loadLibrary("ctsmediav2codecapiunit_jni");
        }

        @ApiTest(apis = "AMediaCodec_createCodecByName")
        @Test
        public void testCreateByCodecNameForNull() {
            assertTrue(nativeTestCreateByCodecNameForNull());
        }

        private native boolean nativeTestCreateByCodecNameForNull();

        @ApiTest(apis = "AMediaCodec_createCodecByName")
        @Test
        public void testCreateByCodecNameForInvalidName() {
            assertTrue(nativeTestCreateByCodecNameForInvalidName());
        }

        private native boolean nativeTestCreateByCodecNameForInvalidName();

        @ApiTest(apis = "AMediaCodec_createDecoderByType")
        @Test
        public void testCreateDecoderByTypeForNull() {
            assertTrue(nativeTestCreateDecoderByTypeForNull());
        }

        private native boolean nativeTestCreateDecoderByTypeForNull();

        @ApiTest(apis = "AMediaCodec_createDecoderByType")
        @Test
        public void testCreateDecoderByTypeForInvalidMediaType() {
            assertTrue(nativeTestCreateDecoderByTypeForInvalidMediaType());
        }

        private native boolean nativeTestCreateDecoderByTypeForInvalidMediaType();

        @ApiTest(apis = "AMediaCodec_createEncoderByType")
        @Test
        public void testCreateEncoderByTypeForNull() {
            assertTrue(nativeTestCreateEncoderByTypeForNull());
        }

        private native boolean nativeTestCreateEncoderByTypeForNull();

        @ApiTest(apis = "AMediaCodec_createEncoderByType")
        @Test
        public void testCreateEncoderByTypeForInvalidMediaType() {
            assertTrue(nativeTestCreateEncoderByTypeForInvalidMediaType());
        }

        private native boolean nativeTestCreateEncoderByTypeForInvalidMediaType();

        @ApiTest(apis = "AMediaCodec_configure")
        @Test
        @Ignore("TODO(b/151302868)")
        public void testConfigureForNullFormat() {
            assertTrue(nativeTestConfigureForNullFormat());
        }

        private native boolean nativeTestConfigureForNullFormat();

        @ApiTest(apis = "AMediaCodec_configure")
        @Test
        public void testConfigureForEmptyFormat() {
            assertTrue(nativeTestConfigureForEmptyFormat());
        }

        private native boolean nativeTestConfigureForEmptyFormat();

        @ApiTest(apis = "AMediaCodec_configure")
        @Test
        @Ignore("TODO(b/151303041)")
        public void testConfigureCodecForIncompleteFormat() {
            boolean[] boolStates = {false, true};
            for (boolean isEncoder : boolStates) {
                for (boolean isAudio : boolStates) {
                    assertTrue(
                            "testConfigureCodecForIncompleteFormat failed for isAudio " + isAudio +
                                    ", isEncoder " + isEncoder,
                            nativeTestConfigureCodecForIncompleteFormat(isAudio, isEncoder));
                }
            }
        }

        private native boolean nativeTestConfigureCodecForIncompleteFormat(boolean isAudio,
                boolean isEncoder);

        @ApiTest(apis = "AMediaCodec_configure")
        @Test
        public void testConfigureEncoderForBadFlags() {
            assertTrue(nativeTestConfigureEncoderForBadFlags());
        }

        private native boolean nativeTestConfigureEncoderForBadFlags();

        @ApiTest(apis = "AMediaCodec_configure")
        @Test
        public void testConfigureDecoderForBadFlags() {
            assertTrue(nativeTestConfigureDecoderForBadFlags());
        }

        private native boolean nativeTestConfigureDecoderForBadFlags();

        @ApiTest(apis = "AMediaCodec_configure")
        @Test
        public void testConfigureInInitState() {
            assertTrue(nativeTestConfigureInInitState());
        }

        private native boolean nativeTestConfigureInInitState();

        @ApiTest(apis = "AMediaCodec_configure")
        @Test
        public void testConfigureInRunningState() {
            assertTrue(nativeTestConfigureInRunningState());
        }

        private native boolean nativeTestConfigureInRunningState();

        @ApiTest(apis = "AMediaCodec_configure")
        @Test
        public void testConfigureInUnInitState() {
            assertTrue(nativeTestConfigureInUnInitState());
        }

        private native boolean nativeTestConfigureInUnInitState();

        @ApiTest(apis = "AMediaCodec_dequeueInputBuffer")
        @Test
        public void testDequeueInputBufferInInitState() {
            assertTrue(nativeTestDequeueInputBufferInInitState());
        }

        private native boolean nativeTestDequeueInputBufferInInitState();

        @ApiTest(apis = "AMediaCodec_dequeueInputBuffer")
        @Test
        public void testDequeueInputBufferInRunningState() {
            assertTrue(nativeTestDequeueInputBufferInRunningState());
        }

        private native boolean nativeTestDequeueInputBufferInRunningState();

        @ApiTest(apis = "AMediaCodec_dequeueInputBuffer")
        @Test
        public void testDequeueInputBufferInUnInitState() {
            assertTrue(nativeTestDequeueInputBufferInUnInitState());
        }

        private native boolean nativeTestDequeueInputBufferInUnInitState();

        @ApiTest(apis = "AMediaCodec_dequeueOutputBuffer")
        @Test
        public void testDequeueOutputBufferInInitState() {
            assertTrue(nativeTestDequeueOutputBufferInInitState());
        }

        private native boolean nativeTestDequeueOutputBufferInInitState();

        @ApiTest(apis = "AMediaCodec_dequeueOutputBuffer")
        @Test
        public void testDequeueOutputBufferInRunningState() {
            assertTrue(nativeTestDequeueOutputBufferInRunningState());
        }

        private native boolean nativeTestDequeueOutputBufferInRunningState();

        @ApiTest(apis = "AMediaCodec_dequeueOutputBuffer")
        @Test
        public void testDequeueOutputBufferInUnInitState() {
            assertTrue(nativeTestDequeueOutputBufferInUnInitState());
        }

        private native boolean nativeTestDequeueOutputBufferInUnInitState();

        @ApiTest(apis = "AMediaCodec_flush")
        @Test
        public void testFlushInInitState() {
            assertTrue(nativeTestFlushInInitState());
        }

        private native boolean nativeTestFlushInInitState();

        @ApiTest(apis = "AMediaCodec_flush")
        @Test
        public void testFlushInRunningState() {
            assertTrue(nativeTestFlushInRunningState());
        }

        private native boolean nativeTestFlushInRunningState();

        @ApiTest(apis = "AMediaCodec_flush")
        @Test
        public void testFlushInUnInitState() {
            assertTrue(nativeTestFlushInUnInitState());
        }

        private native boolean nativeTestFlushInUnInitState();

        @ApiTest(apis = "AMediaCodec_getName")
        @Test
        public void testGetNameInInitState() {
            assertTrue(nativeTestGetNameInInitState());
        }

        private native boolean nativeTestGetNameInInitState();

        @ApiTest(apis = "AMediaCodec_getName")
        @Test
        public void testGetNameInRunningState() {
            assertTrue(nativeTestGetNameInRunningState());
        }

        private native boolean nativeTestGetNameInRunningState();

        @ApiTest(apis = "AMediaCodec_getName")
        @Test
        public void testGetNameInUnInitState() {
            assertTrue(nativeTestGetNameInUnInitState());
        }

        private native boolean nativeTestGetNameInUnInitState();

        @ApiTest(apis = "AMediaCodec_setAsyncNotifyCallback")
        @Test
        @Ignore("TODO(b/148523403)")
        public void testSetAsyncNotifyCallbackInInitState() {
            assertTrue(nativeTestSetAsyncNotifyCallbackInInitState());
        }

        private native boolean nativeTestSetAsyncNotifyCallbackInInitState();

        @ApiTest(apis = "AMediaCodec_setAsyncNotifyCallback")
        @Test
        @Ignore("TODO(b/152553625)")
        public void testSetAsyncNotifyCallbackInRunningState() {
            assertTrue(nativeTestSetAsyncNotifyCallbackInRunningState());
        }

        private native boolean nativeTestSetAsyncNotifyCallbackInRunningState();

        @ApiTest(apis = "AMediaCodec_setAsyncNotifyCallback")
        @Test
        public void testSetAsyncNotifyCallbackInUnInitState() {
            assertTrue(nativeTestSetAsyncNotifyCallbackInUnInitState());
        }

        private native boolean nativeTestSetAsyncNotifyCallbackInUnInitState();

        @ApiTest(apis = "AMediaCodec_getInputBuffer")
        @Test
        public void tesGetInputBufferInInitState() {
            assertTrue(nativeTestGetInputBufferInInitState());
        }

        private native boolean nativeTestGetInputBufferInInitState();

        @ApiTest(apis = "AMediaCodec_getInputBuffer")
        @Test
        public void testGetInputBufferInRunningState() {
            assertTrue(nativeTestGetInputBufferInRunningState());
        }

        private native boolean nativeTestGetInputBufferInRunningState();

        @ApiTest(apis = "AMediaCodec_getInputBuffer")
        @Test
        public void testGetInputBufferInUnInitState() {
            assertTrue(nativeTestGetInputBufferInUnInitState());
        }

        private native boolean nativeTestGetInputBufferInUnInitState();

        @ApiTest(apis = "AMediaCodec_getInputFormat")
        @Test
        public void testGetInputFormatInInitState() {
            assertTrue(nativeTestGetInputFormatInInitState());
        }

        private native boolean nativeTestGetInputFormatInInitState();

        @ApiTest(apis = "AMediaCodec_getInputFormat")
        @Test
        public void testGetInputFormatInRunningState() {
            assertTrue(nativeTestGetInputFormatInRunningState());
        }

        private native boolean nativeTestGetInputFormatInRunningState();

        @ApiTest(apis = "AMediaCodec_getInputFormat")
        @Test
        public void testGetInputFormatInUnInitState() {
            assertTrue(nativeTestGetInputFormatInUnInitState());
        }

        private native boolean nativeTestGetInputFormatInUnInitState();

        @ApiTest(apis = "AMediaCodec_getOutputBuffer")
        @Test
        public void testGetOutputBufferInInitState() {
            assertTrue(nativeTestGetOutputBufferInInitState());
        }

        private native boolean nativeTestGetOutputBufferInInitState();

        @ApiTest(apis = "AMediaCodec_getOutputBuffer")
        @Test
        public void testGetOutputBufferInRunningState() {
            assertTrue(nativeTestGetOutputBufferInRunningState());
        }

        private native boolean nativeTestGetOutputBufferInRunningState();

        @ApiTest(apis = "AMediaCodec_getOutputBuffer")
        @Test
        public void testGetOutputBufferInUnInitState() {
            assertTrue(nativeTestGetOutputBufferInUnInitState());
        }

        private native boolean nativeTestGetOutputBufferInUnInitState();

        @ApiTest(apis = "AMediaCodec_getOutputFormat")
        @Test
        public void testGetOutputFormatInInitState() {
            assertTrue(nativeTestGetOutputFormatInInitState());
        }

        private native boolean nativeTestGetOutputFormatInInitState();

        @ApiTest(apis = "AMediaCodec_getOutputFormat")
        @Test
        public void testGetOutputFormatInRunningState() {
            assertTrue(nativeTestGetOutputFormatInRunningState());
        }

        private native boolean nativeTestGetOutputFormatInRunningState();

        @ApiTest(apis = "AMediaCodec_getOutputFormat")
        @Test
        public void testGetOutputFormatInUnInitState() {
            assertTrue(nativeTestGetOutputFormatInUnInitState());
        }

        private native boolean nativeTestGetOutputFormatInUnInitState();

        @ApiTest(apis = "AMediaCodec_setParameters")
        @Test
        @Ignore("TODO(b/)")
        public void testSetParametersInInitState() {
            assertTrue(nativeTestSetParametersInInitState());
        }

        private native boolean nativeTestSetParametersInInitState();

        @ApiTest(apis = "AMediaCodec_setParameters")
        @Test
        public void testSetParametersInRunningState() {
            assertTrue(nativeTestSetParametersInRunningState());
        }

        private native boolean nativeTestSetParametersInRunningState();

        @ApiTest(apis = "AMediaCodec_setParameters")
        @Test
        @Ignore("TODO(b/)")
        public void testSetParametersInUnInitState() {
            assertTrue(nativeTestSetParametersInUnInitState());
        }

        private native boolean nativeTestSetParametersInUnInitState();

        @ApiTest(apis = "AMediaCodec_start")
        @Test
        public void testStartInRunningState() {
            assertTrue(nativeTestStartInRunningState());
        }

        private native boolean nativeTestStartInRunningState();

        @ApiTest(apis = "AMediaCodec_start")
        @Test
        public void testStartInUnInitState() {
            assertTrue(nativeTestStartInUnInitState());
        }

        private native boolean nativeTestStartInUnInitState();

        @ApiTest(apis = "AMediaCodec_stop")
        @Test
        public void testStopInInitState() {
            assertTrue(nativeTestStopInInitState());
        }

        private native boolean nativeTestStopInInitState();

        @ApiTest(apis = "AMediaCodec_stop")
        @Test
        public void testStopInRunningState() {
            assertTrue(nativeTestStopInRunningState());
        }

        private native boolean nativeTestStopInRunningState();

        @ApiTest(apis = "AMediaCodec_stop")
        @Test
        public void testStopInUnInitState() {
            assertTrue(nativeTestStopInUnInitState());
        }

        private native boolean nativeTestStopInUnInitState();

        @ApiTest(apis = "AMediaCodec_queueInputBuffer")
        @Test
        public void testQueueInputBufferInInitState() {
            assertTrue(nativeTestQueueInputBufferInInitState());
        }

        private native boolean nativeTestQueueInputBufferInInitState();

        @ApiTest(apis = "AMediaCodec_queueInputBuffer")
        @Test
        public void testQueueInputBufferWithBadIndex() {
            assertTrue(nativeTestQueueInputBufferWithBadIndex());
        }

        private native boolean nativeTestQueueInputBufferWithBadIndex();

        @ApiTest(apis = "AMediaCodec_queueInputBuffer")
        @Test
        public void testQueueInputBufferWithBadSize() {
            assertTrue(nativeTestQueueInputBufferWithBadSize());
        }

        private native boolean nativeTestQueueInputBufferWithBadSize();

        @ApiTest(apis = "AMediaCodec_queueInputBuffer")
        @Test
        public void testQueueInputBufferWithBadBuffInfo() {
            assertTrue(nativeTestQueueInputBufferWithBadBuffInfo());
        }

        private native boolean nativeTestQueueInputBufferWithBadBuffInfo();

        @ApiTest(apis = "AMediaCodec_queueInputBuffer")
        @Test
        public void testQueueInputBufferWithBadOffset() {
            assertTrue(nativeTestQueueInputBufferWithBadOffset());
        }

        private native boolean nativeTestQueueInputBufferWithBadOffset();

        @ApiTest(apis = "AMediaCodec_queueInputBuffer")
        @Test
        public void testQueueInputBufferInUnInitState() {
            assertTrue(nativeTestQueueInputBufferInUnInitState());
        }

        private native boolean nativeTestQueueInputBufferInUnInitState();

        @ApiTest(apis = "AMediaCodec_releaseOutputBuffer")
        @Test
        public void testReleaseOutputBufferInInitState() {
            assertTrue(nativeTestReleaseOutputBufferInInitState());
        }

        private native boolean nativeTestReleaseOutputBufferInInitState();

        @ApiTest(apis = "AMediaCodec_releaseOutputBuffer")
        @Test
        public void testReleaseOutputBufferInRunningState() {
            assertTrue(nativeTestReleaseOutputBufferInRunningState());
        }

        private native boolean nativeTestReleaseOutputBufferInRunningState();

        @ApiTest(apis = "AMediaCodec_releaseOutputBuffer")
        @Test
        public void testReleaseOutputBufferInUnInitState() {
            assertTrue(nativeTestReleaseOutputBufferInUnInitState());
        }

        private native boolean nativeTestReleaseOutputBufferInUnInitState();

        @ApiTest(apis = "AMediaCodec_getBufferFormat")
        @Test
        public void testGetBufferFormatInInitState() {
            assertTrue(nativeTestGetBufferFormatInInitState());
        }

        private native boolean nativeTestGetBufferFormatInInitState();

        @ApiTest(apis = "AMediaCodec_getBufferFormat")
        @Test
        public void testGetBufferFormatInRunningState() {
            assertTrue(nativeTestGetBufferFormatInRunningState());
        }

        private native boolean nativeTestGetBufferFormatInRunningState();

        @ApiTest(apis = "AMediaCodec_getBufferFormat")
        @Test
        public void testGetBufferFormatInUnInitState() {
            assertTrue(nativeTestGetBufferFormatInUnInitState());
        }

        private native boolean nativeTestGetBufferFormatInUnInitState();
    }
}
