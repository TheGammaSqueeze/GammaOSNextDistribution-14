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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;

import android.media.AudioFormat;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

/**
 * Class to hold encoder configuration settings.
 */
public class EncoderConfigParams {
    public static final String TOKEN_SEPARATOR = "<>";

    public final boolean mIsAudio;
    public final String mMediaType;

    // video params
    public final int mWidth;
    public final int mHeight;
    public final int mFrameRate;
    public final float mKeyFrameInterval;
    public final int mMaxBFrames;
    public final int mBitRateMode;
    public final int mLevel;
    public final int mColorFormat;
    public final int mInputBitDepth;
    public final int mRange;
    public final int mStandard;
    public final int mTransfer;

    // audio params
    public final int mSampleRate;
    public final int mChannelCount;
    public final int mCompressionLevel;
    public final int mPcmEncoding;

    // common params
    public final int mProfile;
    public final int mBitRate;

    Builder mBuilder;
    MediaFormat mFormat;
    StringBuilder mMsg;

    private EncoderConfigParams(Builder cfg) {
        if (cfg.mMediaType == null) {
            throw new IllegalArgumentException("null media type");
        }
        mIsAudio = cfg.mMediaType.startsWith("audio/");
        boolean mIsVideo = cfg.mMediaType.startsWith("video/");
        if (mIsAudio == mIsVideo) {
            throw new IllegalArgumentException("invalid media type, it is neither audio nor video");
        }
        mMediaType = cfg.mMediaType;
        if (mIsAudio) {
            if (cfg.mSampleRate <= 0 || cfg.mChannelCount <= 0) {
                throw new IllegalArgumentException("bad config params for audio component");
            }
            mSampleRate = cfg.mSampleRate;
            mChannelCount = cfg.mChannelCount;
            if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                if (cfg.mCompressionLevel < 0 || cfg.mCompressionLevel > 8) {
                    throw new IllegalArgumentException("bad compression level for flac component");
                }
                mCompressionLevel = cfg.mCompressionLevel;
                mBitRate = -1;
            } else {
                if (cfg.mBitRate <= 0) {
                    throw new IllegalArgumentException("bad bitrate value for audio component");
                }
                mBitRate = cfg.mBitRate;
                mCompressionLevel = -1;
            }
            if (cfg.mPcmEncoding != AudioFormat.ENCODING_PCM_FLOAT
                    && cfg.mPcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                throw new IllegalArgumentException("bad input pcm encoding for audio component");
            }
            if (cfg.mInputBitDepth != -1) {
                throw new IllegalArgumentException(
                        "use pcm encoding to signal input attributes, don't use bitdepth");
            }
            mPcmEncoding = cfg.mPcmEncoding;
            mProfile = cfg.mProfile;

            // satisfy Variable '*' might not have been initialized, unused by this media type
            mWidth = 352;
            mHeight = 288;
            mFrameRate = -1;
            mBitRateMode = -1;
            mKeyFrameInterval = 1.0f;
            mMaxBFrames = 0;
            mLevel = -1;
            mColorFormat = COLOR_FormatYUV420Flexible;
            mInputBitDepth = -1;
            mRange = -1;
            mStandard = -1;
            mTransfer = -1;
        } else {
            if (cfg.mWidth <= 0 || cfg.mHeight <= 0) {
                throw new IllegalArgumentException("bad config params for video component");
            }
            mWidth = cfg.mWidth;
            mHeight = cfg.mHeight;
            if (cfg.mFrameRate <= 0) {
                if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4)) {
                    mFrameRate = 12;
                } else if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_H263)) {
                    mFrameRate = 12;
                } else {
                    mFrameRate = 30;
                }
            } else {
                mFrameRate = cfg.mFrameRate;
            }
            if (cfg.mBitRate <= 0) {
                throw new IllegalArgumentException("bad bitrate value for video component");
            }
            mBitRate = cfg.mBitRate;
            mKeyFrameInterval = cfg.mKeyFrameInterval;
            mMaxBFrames = cfg.mMaxBFrames;
            mBitRateMode = cfg.mBitRateMode;
            mProfile = cfg.mProfile;
            mLevel = cfg.mLevel;
            if (cfg.mColorFormat != COLOR_FormatYUV420Flexible
                    && cfg.mColorFormat != COLOR_FormatYUVP010
                    && cfg.mColorFormat != COLOR_FormatSurface
                    && cfg.mColorFormat != COLOR_FormatYUV420SemiPlanar
                    && cfg.mColorFormat != COLOR_FormatYUV420Planar) {
                throw new IllegalArgumentException("bad color format config for video component");
            }
            mColorFormat = cfg.mColorFormat;
            if (cfg.mInputBitDepth != -1) {
                if (cfg.mColorFormat == COLOR_FormatYUV420Flexible && cfg.mInputBitDepth != 8) {
                    throw new IllegalArgumentException(
                            "bad bit depth configuration for COLOR_FormatYUV420Flexible");
                } else if (cfg.mColorFormat == COLOR_FormatYUV420SemiPlanar
                        && cfg.mInputBitDepth != 8) {
                    throw new IllegalArgumentException(
                            "bad bit depth configuration for COLOR_FormatYUV420SemiPlanar");
                } else if (cfg.mColorFormat == COLOR_FormatYUV420Planar
                        && cfg.mInputBitDepth != 8) {
                    throw new IllegalArgumentException(
                            "bad bit depth configuration for COLOR_FormatYUV420Planar");
                } else if (cfg.mColorFormat == COLOR_FormatYUVP010 && cfg.mInputBitDepth != 10) {
                    throw new IllegalArgumentException(
                            "bad bit depth configuration for COLOR_FormatYUVP010");
                } else if (cfg.mColorFormat == COLOR_FormatSurface && cfg.mInputBitDepth != 8
                        && cfg.mInputBitDepth != 10) {
                    throw new IllegalArgumentException(
                            "bad bit depth configuration for COLOR_FormatSurface");
                }
                mInputBitDepth = cfg.mInputBitDepth;
            } else if (cfg.mColorFormat == COLOR_FormatYUVP010) {
                mInputBitDepth = 10;
            } else {
                mInputBitDepth = 8;
            }
            if (mProfile == -1) {
                if ((mColorFormat == COLOR_FormatSurface && mInputBitDepth == 10) || (mColorFormat
                        == COLOR_FormatYUVP010)) {
                    throw new IllegalArgumentException("If color format is configured to "
                            + "COLOR_FormatSurface and bitdepth is set to 10 or color format is "
                            + "configured to COLOR_FormatYUVP010 then profile needs to be"
                            + " configured");
                }
            }
            mRange = cfg.mRange;
            mStandard = cfg.mStandard;
            mTransfer = cfg.mTransfer;

            // satisfy Variable '*' might not have been initialized, unused by this media type
            mSampleRate = 8000;
            mChannelCount = 1;
            mCompressionLevel = 5;
            mPcmEncoding = AudioFormat.ENCODING_INVALID;
        }
        mBuilder = cfg;
    }

    public Builder getBuilder() throws CloneNotSupportedException {
        return mBuilder.clone();
    }

    public MediaFormat getFormat() {
        if (mFormat != null) return new MediaFormat(mFormat);

        mFormat = new MediaFormat();
        mFormat.setString(MediaFormat.KEY_MIME, mMediaType);
        if (mIsAudio) {
            mFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, mSampleRate);
            mFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, mChannelCount);
            if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                mFormat.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, mCompressionLevel);
            } else {
                mFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            }
            if (mProfile >= 0 && mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AAC)) {
                mFormat.setInteger(MediaFormat.KEY_PROFILE, mProfile);
                mFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, mProfile);
            }
            mFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, mPcmEncoding);
        } else {
            mFormat.setInteger(MediaFormat.KEY_WIDTH, mWidth);
            mFormat.setInteger(MediaFormat.KEY_HEIGHT, mHeight);
            mFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
            mFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            mFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, mKeyFrameInterval);
            mFormat.setInteger(MediaFormat.KEY_MAX_B_FRAMES, mMaxBFrames);
            if (mBitRateMode >= 0) mFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, mBitRateMode);
            if (mProfile >= 0) mFormat.setInteger(MediaFormat.KEY_PROFILE, mProfile);
            if (mLevel >= 0) mFormat.setInteger(MediaFormat.KEY_LEVEL, mLevel);
            mFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
            if (mRange >= 0) mFormat.setInteger(MediaFormat.KEY_COLOR_RANGE, mRange);
            if (mStandard >= 0) mFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, mStandard);
            if (mTransfer >= 0) mFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER, mTransfer);
        }
        return new MediaFormat(mFormat);
    }

    /**
     * Converts MediaFormat object to a string. All Keys, ValueTypes, Values are concatenated with
     * a separator and sent for further usage.
     */
    public static String serializeMediaFormat(MediaFormat format) {
        StringBuilder msg = new StringBuilder();
        java.util.Set<String> keys = format.getKeys();
        for (String key : keys) {
            int valueTypeForKey = format.getValueTypeForKey(key);
            msg.append(key).append(TOKEN_SEPARATOR);
            msg.append(valueTypeForKey).append(TOKEN_SEPARATOR);
            if (valueTypeForKey == MediaFormat.TYPE_INTEGER) {
                msg.append(format.getInteger(key)).append(TOKEN_SEPARATOR);
            } else if (valueTypeForKey == MediaFormat.TYPE_FLOAT) {
                msg.append(format.getFloat(key)).append(TOKEN_SEPARATOR);
            } else if (valueTypeForKey == MediaFormat.TYPE_STRING) {
                msg.append(format.getString(key)).append(TOKEN_SEPARATOR);
            } else {
                throw new RuntimeException("unrecognized Type for Key: " + key);
            }
        }
        return msg.toString();
    }

    @NonNull
    @Override
    public String toString() {
        if (mMsg != null) return mMsg.toString();

        mMsg = new StringBuilder();
        mMsg.append(String.format("media type : %s, ", mMediaType));
        if (mIsAudio) {
            mMsg.append(String.format("Sample rate : %d, ", mSampleRate));
            mMsg.append(String.format("Channel count : %d, ", mChannelCount));
            if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                mMsg.append(String.format("Compression level : %d, ", mCompressionLevel));
            } else {
                mMsg.append(String.format("Bitrate : %d, ", mBitRate));
            }
            if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AAC) && mProfile != -1) {
                mMsg.append(String.format("Profile : %d, ", mProfile));
            }
            mMsg.append(String.format("encoding : %d, ", mPcmEncoding));
        } else {
            mMsg.append(String.format("Width : %d, ", mWidth));
            mMsg.append(String.format("Height : %d, ", mHeight));
            mMsg.append(String.format("Frame rate : %d, ", mFrameRate));
            mMsg.append(String.format("Bit rate : %d, ", mBitRate));
            mMsg.append(String.format("key frame interval : %f, ", mKeyFrameInterval));
            mMsg.append(String.format("max b frames : %d, ", mMaxBFrames));
            if (mBitRateMode >= 0) mMsg.append(String.format("bitrate mode : %d, ", mBitRateMode));
            if (mProfile >= 0) mMsg.append(String.format("profile : %x, ", mProfile));
            if (mLevel >= 0) mMsg.append(String.format("level : %x, ", mLevel));
            mMsg.append(String.format("color format : %x, ", mColorFormat));
            if (mColorFormat == COLOR_FormatSurface) {
                mMsg.append(String.format("bit depth : %d, ", mInputBitDepth));
            }
            if (mRange >= 0) mMsg.append(String.format("color range : %d, ", mRange));
            if (mStandard >= 0) mMsg.append(String.format("color standard : %d, ", mStandard));
            if (mTransfer >= 0) mMsg.append(String.format("color transfer : %d, ", mTransfer));
        }
        mMsg.append("\n");
        return mMsg.toString();
    }

    public static class Builder implements Cloneable {
        public String mMediaType;

        // video params
        public int mWidth = 352;
        public int mHeight = 288;
        public int mFrameRate = -1;
        public int mBitRateMode = -1;
        public float mKeyFrameInterval = 1.0f;
        public int mMaxBFrames = 0;
        public int mLevel = -1;
        public int mColorFormat = COLOR_FormatYUV420Flexible;
        public int mInputBitDepth = -1;
        public int mRange = -1;
        public int mStandard = -1;
        public int mTransfer = -1;

        // audio params
        public int mSampleRate = 8000;
        public int mChannelCount = 1;
        public int mCompressionLevel = 5;
        public int mPcmEncoding = AudioFormat.ENCODING_PCM_16BIT;

        // common params
        public int mProfile = -1;
        public int mBitRate = 256000;

        public Builder(String mediaType) {
            mMediaType = mediaType;
        }

        public Builder setWidth(int width) {
            this.mWidth = width;
            return this;
        }

        public Builder setHeight(int height) {
            this.mHeight = height;
            return this;
        }

        public Builder setFrameRate(int frameRate) {
            this.mFrameRate = frameRate;
            return this;
        }

        public Builder setBitRateMode(int bitRateMode) {
            this.mBitRateMode = bitRateMode;
            return this;
        }

        public Builder setKeyFrameInterval(float keyFrameInterval) {
            this.mKeyFrameInterval = keyFrameInterval;
            return this;
        }

        public Builder setMaxBFrames(int maxBFrames) {
            this.mMaxBFrames = maxBFrames;
            return this;
        }

        public Builder setLevel(int level) {
            this.mLevel = level;
            return this;
        }

        public Builder setColorFormat(int colorFormat) {
            this.mColorFormat = colorFormat;
            return this;
        }

        public Builder setInputBitDepth(int inputBitDepth) {
            this.mInputBitDepth = inputBitDepth;
            return this;
        }

        public Builder setRange(int range) {
            this.mRange = range;
            return this;
        }

        public Builder setStandard(int standard) {
            this.mStandard = standard;
            return this;
        }

        public Builder setTransfer(int transfer) {
            this.mTransfer = transfer;
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

        public Builder setCompressionLevel(int compressionLevel) {
            this.mCompressionLevel = compressionLevel;
            return this;
        }

        public Builder setPcmEncoding(int pcmEncoding) {
            this.mPcmEncoding = pcmEncoding;
            return this;
        }

        public Builder setProfile(int profile) {
            this.mProfile = profile;
            // encoder profile requires also level to be set prior to Android U,
            // but this can be a default/unknown value. Setting this to 1 as all
            // codecs use a value of 1 for lowest level.
            if (mLevel < 0) {
                mLevel = 1;
            }
            return this;
        }

        public Builder setBitRate(int bitRate) {
            this.mBitRate = bitRate;
            return this;
        }

        public EncoderConfigParams build() {
            return new EncoderConfigParams(this);
        }

        @NonNull
        public Builder clone() throws CloneNotSupportedException {
            return (Builder) super.clone();
        }
    }
}
