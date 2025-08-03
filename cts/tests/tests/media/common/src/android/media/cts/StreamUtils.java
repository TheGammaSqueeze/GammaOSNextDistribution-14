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

package android.media.cts;

import static org.junit.Assert.fail;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class StreamUtils {
    private static final String TAG = "CtsMediaStreamUtils";

    public static abstract class ByteBufferStream {
        public abstract ByteBuffer read() throws IOException;
    }

    public static class MediaCodecStream extends ByteBufferStream implements Closeable {
        private ByteBufferStream mBufferInputStream;
        private InputStream mInputStream;
        private MediaCodec mCodec;
        public boolean mIsFloat;
        BufferInfo mInfo = new BufferInfo();
        boolean mSawOutputEOS;
        boolean mSawInputEOS;
        boolean mEncode;
        boolean mSentConfig;

        // result stream
        private byte[] mBuf = null;
        private byte[] mCache = null;
        private int mBufIn = 0;
        private int mBufOut = 0;
        private int mBufCounter = 0;

        private MediaExtractor mExtractor; // Read from Extractor instead of InputStream
        // helper for bytewise read()
        private byte[] mOneByte = new byte[1];

        public MediaCodecStream(
                MediaFormat format,
                boolean encode) throws Exception {
            String mime = format.getString(MediaFormat.KEY_MIME);
            mEncode = encode;
            if (mEncode) {
                mCodec = MediaCodec.createEncoderByType(mime);
            } else {
                mCodec = MediaCodec.createDecoderByType(mime);
            }
            mCodec.configure(format,null, null, encode ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0);

            // check if float
            final MediaFormat actualFormat =
                    encode ? mCodec.getInputFormat() : mCodec.getOutputFormat();

            mIsFloat = actualFormat.getInteger(
                    MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                            == AudioFormat.ENCODING_PCM_FLOAT;

            mCodec.start();
        }

        public MediaCodecStream(
                InputStream input,
                MediaFormat format,
                boolean encode) throws Exception {
            this(format, encode);
            mInputStream = input;
        }

        public MediaCodecStream(
                ByteBufferStream input,
                MediaFormat format,
                boolean encode) throws Exception {
            this(format, encode);
            mBufferInputStream = input;
        }

        public MediaCodecStream(MediaExtractor mediaExtractor,
                MediaFormat format) throws Exception {
            this(format, false /* encode */);
            mExtractor = mediaExtractor;
        }

        @Override
        public ByteBuffer read() throws IOException {

            if (mSawOutputEOS) {
                return null;
            }

            // first push as much data into the codec as possible
            while (!mSawInputEOS) {
                Log.i(TAG, "sending data to " + mCodec.getName());
                int index = mCodec.dequeueInputBuffer(5000);
                if (index < 0) {
                    // no input buffer currently available
                    break;
                } else {
                    ByteBuffer buf = mCodec.getInputBuffer(index);
                    buf.clear();
                    int inBufLen = buf.limit();
                    int numRead = 0;
                    long timestampUs = 0; // non-zero for MediaExtractor mode
                    if (mExtractor != null) {
                        numRead = mExtractor.readSampleData(buf, 0 /* offset */);
                        timestampUs = mExtractor.getSampleTime();
                        Log.v(TAG, "MediaCodecStream.read using Extractor, numRead "
                                + numRead +" timestamp " + timestampUs);
                        mExtractor.advance();
                        if(numRead < 0) {
                           mSawInputEOS = true;
                           timestampUs = 0;
                           numRead =0;
                        }
                    } else if (mBufferInputStream != null) {
                        ByteBuffer in = null;
                        do {
                            in = mBufferInputStream.read();
                        } while (in != null && in.limit() - in.position() == 0);
                        if (in == null) {
                            mSawInputEOS = true;
                        } else {
                            final int n = in.limit() - in.position();
                            numRead += n;
                            buf.put(in);
                        }
                    } else if (mInputStream != null) {
                        if (mBuf == null) {
                            mBuf = new byte[inBufLen];
                        }
                        for (numRead = 0; numRead < inBufLen; ) {
                            int n = mInputStream.read(mBuf, numRead, inBufLen - numRead);
                            if (n == -1) {
                                mSawInputEOS = true;
                                break;
                            }
                            numRead += n;
                        }
                        buf.put(mBuf, 0, numRead);
                    } else {
                        fail("no input");
                    }

                    int flags = mSawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
                    if (!mEncode && !mSentConfig && mExtractor == null) {
                        flags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
                        mSentConfig = true;
                    }
                    Log.i(TAG, "queuing input buffer " + index +
                            ", size " + numRead + ", flags: " + flags +
                            " on " + mCodec.getName());
                    mCodec.queueInputBuffer(index,
                            0 /* offset */,
                            numRead,
                            timestampUs /* presentationTimeUs */,
                            flags);
                    Log.i(TAG, "queued input buffer " + index + ", size " + numRead);
                }
            }

            // now read data from the codec
            Log.i(TAG, "reading from " + mCodec.getName());
            int index = mCodec.dequeueOutputBuffer(mInfo, 5000);
            if (index >= 0) {
                Log.i(TAG, "got " + mInfo.size + " bytes from " + mCodec.getName());
                ByteBuffer out = mCodec.getOutputBuffer(index);
                ByteBuffer ret = ByteBuffer.allocate(mInfo.size);
                ret.put(out);
                mCodec.releaseOutputBuffer(index,  false /* render */);
                if ((mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.i(TAG, "saw output EOS on " + mCodec.getName());
                    mSawOutputEOS = true;
                }
                ret.flip(); // prepare buffer for reading from it
                // XXX chck that first encoded buffer has CSD flags set
                if (mEncode && mBufCounter++ == 0 && (mInfo.flags &
                    MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    fail("first encoded buffer missing CSD flag");
                }
                return ret;
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.i(TAG, mCodec.getName() + " new format: " + mCodec.getOutputFormat());
            }
            return ByteBuffer.allocate(0);
        }

        @Override
        public void close() throws IOException {
            try {
                if (mInputStream != null) {
                    mInputStream.close();
                }
            } finally {
                mInputStream = null;
                try {
                    if (mCodec != null) {
                        mCodec.release();
                    }
                } finally {
                    mCodec = null;
                }
            }
        }

        @Override
        protected void finalize() throws Throwable {
            if (mCodec != null) {
                Log.w(TAG, "MediaCodecInputStream wasn't closed");
                mCodec.release();
            }
        }
    };

    public static class ByteBufferInputStream extends InputStream {
        ByteBufferStream mInput;
        ByteBuffer mBuffer;

        public ByteBufferInputStream(ByteBufferStream in) {
            mInput = in;
            mBuffer = ByteBuffer.allocate(0);
        }

        @Override
        public int read() throws IOException {
            while (mBuffer != null && !mBuffer.hasRemaining()) {
                Log.i(TAG, "reading buffer");
                mBuffer = mInput.read();
            }

            if (mBuffer == null) {
                return -1;
            }

            return (0xff & mBuffer.get());
        }
    };

    public static class PcmAudioBufferStream extends ByteBufferStream {

        public int mCount;         // either 0 or 1 if the buffer has been delivered
        public ByteBuffer mBuffer; // the audio buffer (furnished duplicated, read only).

        public PcmAudioBufferStream(
            int samples, int sampleRate, double frequency, double sweep, boolean useFloat) {
            final int sampleSize = useFloat ? 4 : 2;
            final int sizeInBytes = samples * sampleSize;
            mBuffer = ByteBuffer.allocate(sizeInBytes);
            mBuffer.order(java.nio.ByteOrder.nativeOrder());
            if (useFloat) {
                FloatBuffer fb = mBuffer.asFloatBuffer();
                float[] fa = AudioHelper.createSoundDataInFloatArray(
                    samples, sampleRate, frequency, sweep);
                for (int i = 0; i < fa.length; ++i) {
                    // quantize to a Q.23 integer so that identity is preserved
                    fa[i] = (float)((int)(fa[i] * ((1 << 23) - 1))) / (1 << 23);
                }
                fb.put(fa);
            } else {
                ShortBuffer sb = mBuffer.asShortBuffer();
                sb.put(AudioHelper.createSoundDataInShortArray(
                    samples, sampleRate, frequency, sweep));
            }
            mBuffer.limit(sizeInBytes);
        }

        // duplicating constructor
        public PcmAudioBufferStream(PcmAudioBufferStream other) {
            mCount = 0;
            mBuffer = other.mBuffer; // ok to copy, furnished read-only
        }

        public int sizeInBytes() {
            return mBuffer.capacity();
        }

        @Override
        public ByteBuffer read() throws IOException {
            if (mCount < 1 /* only one buffer */) {
                ++mCount;
                return mBuffer.asReadOnlyBuffer();
            }
            return null;
        }
    }

    public static int compareStreams(InputStream test, InputStream reference) {
        Log.i(TAG, "compareStreams");
        BufferedInputStream buffered_test = new BufferedInputStream(test);
        BufferedInputStream buffered_reference = new BufferedInputStream(reference);
        int numread = 0;
        try {
            while (true) {
                int b1 = buffered_test.read();
                int b2 = buffered_reference.read();
                if (b1 != b2) {
                    Log.e(TAG, "streams differ at " + numread + ": " + b1 + "/" + b2);
                    return -1;
                }
                if (b1 == -1) {
                    Log.e(TAG, "streams ended at " + numread);
                    break;
                }
                numread++;
            }
        } catch (Exception e) {
            Log.e(TAG, "read error", e);
            return -1;
        }
        Log.i(TAG, "compareStreams read " + numread);
        return numread;
    }
}
