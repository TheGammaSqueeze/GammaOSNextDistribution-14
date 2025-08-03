/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.graphics.ImageFormat;
import android.media.AudioFormat;

/**
 * Class to hold raw resource attributes.
 */
public class RawResource {
    public final String mFileName;
    public final boolean mIsAudio;
    public final int mWidth;
    public final int mHeight;
    public final int mColorFormat;
    public final int mSampleRate;
    public final int mChannelCount;
    public final int mBytesPerSample;
    public final int mAudioEncoding;

    private RawResource(Builder builder) {
        if (builder.mFileName == null || builder.mFileName.isEmpty()) {
            throw new IllegalArgumentException("Invalid raw resource file name");
        }
        if (builder.mIsAudio && (builder.mSampleRate <= 0 || builder.mChannelCount <= 0
                || builder.mBytesPerSample <= 0)) {
            throw new IllegalArgumentException("Invalid arguments for raw audio resource");
        }
        if (!builder.mIsAudio && (builder.mWidth <= 0 || builder.mHeight <= 0
                || builder.mBytesPerSample <= 0)) {
            throw new IllegalArgumentException("Invalid arguments for raw video resource");
        }
        mFileName = builder.mFileName;
        mIsAudio = builder.mIsAudio;
        mWidth = builder.mWidth;
        mHeight = builder.mHeight;
        mColorFormat = builder.mColorFormat;
        mSampleRate = builder.mSampleRate;
        mChannelCount = builder.mChannelCount;
        mBytesPerSample = builder.mBytesPerSample;
        mAudioEncoding = builder.mAudioEncoding;
    }

    public static class Builder {
        private String mFileName;
        private boolean mIsAudio;
        private int mWidth;
        private int mHeight;
        private int mColorFormat = ImageFormat.UNKNOWN;
        private int mSampleRate;
        private int mChannelCount;
        private int mBytesPerSample;
        private int mAudioEncoding = AudioFormat.ENCODING_INVALID;

        public Builder setFileName(String fileName, boolean isAudio) {
            this.mFileName = fileName;
            this.mIsAudio = isAudio;
            return this;
        }

        public Builder setDimension(int width, int height) {
            this.mWidth = width;
            this.mHeight = height;
            return this;
        }

        public Builder setColorFormat(int colorFormat) {
            this.mColorFormat = colorFormat;
            return this;
        }

        public Builder setSampleRate(int sampleRate) {
            this.mSampleRate = sampleRate;
            return this;
        }

        public Builder setChannelCount(int channelCount) {
            this.mChannelCount = channelCount;
            return this;
        }

        public Builder setBytesPerSample(int bytesPerSample) {
            this.mBytesPerSample = bytesPerSample;
            return this;
        }

        public Builder setAudioEncoding(int audioEncoding) {
            this.mAudioEncoding = audioEncoding;
            return this;
        }

        public RawResource build() {
            return new RawResource(this);
        }
    }
}
