/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.media.decoder.cts;

import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

class VideoDecoderCallback extends MediaCodec.Callback {
    public interface OnInputBufferAvailable {
        void onInputBufferAvailable(int index, int sampleSize, long presentationTime,
                                    int flags);
    }

    public interface OnOutputBufferAvailable {
        void onOutputBufferAvailable(int index, MediaCodec.BufferInfo info);
    }

    VideoDecoderCallback(MediaExtractor videoExtractor) {
        mVideoExtractor = videoExtractor;
        mOnInputBufferAvailable = null;
        mOnOutputBufferAvailable = null;
    }

    public void setOnInputBufferAvailable(OnInputBufferAvailable onInputBufferAvailable) {
        mOnInputBufferAvailable = onInputBufferAvailable;
    }

    public void setOnOutputBufferAvailable(OnOutputBufferAvailable onOutputBufferAvailable) {
        mOnOutputBufferAvailable = onOutputBufferAvailable;
    }

    @Override
    public void onInputBufferAvailable(MediaCodec codec, int index) {
        ByteBuffer inputBuffer = codec.getInputBuffer(index);
        int sampleSize = mVideoExtractor.readSampleData(inputBuffer, 0);
        long presentationTime = mVideoExtractor.getSampleTime();
        int flags = mVideoExtractor.getSampleFlags();
        if (mOnInputBufferAvailable == null) {
            codec.queueInputBuffer(index, 0, sampleSize, presentationTime, flags);
        } else {
            mOnInputBufferAvailable.onInputBufferAvailable(index, sampleSize, presentationTime,
                                                           flags);
        }
        mVideoExtractor.advance();
    }

    @Override
    public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
        if (mOnOutputBufferAvailable == null) {
            codec.releaseOutputBuffer(index, false);
        } else {
            mOnOutputBufferAvailable.onOutputBufferAvailable(index, info);
        }
    }

    @Override
    public void onError(MediaCodec codec, MediaCodec.CodecException e) {
        fail("Encountered unexpected error while decoding video: " + e.getMessage());
    }

    @Override
    public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
        // do nothing
    }

    private final MediaExtractor mVideoExtractor;
    private OnInputBufferAvailable mOnInputBufferAvailable;
    private OnOutputBufferAvailable mOnOutputBufferAvailable;
}
