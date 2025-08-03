/*
 * Copyright 2020 The Android Open Source Project
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
package org.hyphonate.megaaudio.player;

import android.media.AudioTimestamp;
import android.util.Log;

public class OboePlayer extends Player {
    @SuppressWarnings("unused")
    private static final String TAG = OboePlayer.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = true;

    private int mPlayerSubtype;

    private long mNativePlayer;

    public OboePlayer(PlayerBuilder builder, AudioSourceProvider sourceProvider,
                      int playerSubtype) {
        super(sourceProvider);

        mPlayerSubtype = playerSubtype;
        mAudioSource = mSourceProvider.getNativeSource();
        if (mAudioSource != null) {
            mNativePlayer = allocNativePlayer(
                    ((NativeAudioSource) mAudioSource).getNativeObject(), mPlayerSubtype);
        } else {
            // No native source provided, so wrap a Java source in a native provider wrapper
            mAudioSource = mSourceProvider.getJavaSource();
            mNativePlayer = allocNativePlayer(
                    JavaSourceProxy.allocNativeSource(mAudioSource), mPlayerSubtype);
        }

        setupStream(builder);
    }

    public int getNumBufferFrames() {
        return getBufferFrameCountN(mNativePlayer);
    }

    @Override
    public int getRoutedDeviceId() {
        return getRoutedDeviceIdN(mNativePlayer);
    }

    @Override
    public int getSharingMode() {
        return getSharingModeN(mNativePlayer);
    }

    @Override
    public int getChannelCount() {
        return getChannelCountN(mNativePlayer);
    }

    private int setupStream(PlayerBuilder builder) {
        mChannelCount = builder.getChannelCount();
        mSampleRate = builder.getSampleRate();
        mNumExchangeFrames = builder.getNumExchangeFrames();
        mPerformanceMode = builder.getPerformanceMode();
        mSharingMode = builder.getSharingMode();
        int routeDeviceId = builder.getRouteDeviceId();
        if (LOG) {
            Log.i(TAG, "setupStream()");
            Log.i(TAG, "  chans:" + mChannelCount);
            Log.i(TAG, "  rate: " + mSampleRate);
            Log.i(TAG, "  frames: " + mNumExchangeFrames);
            Log.i(TAG, "  perf mode: " + mPerformanceMode);
            Log.i(TAG, "  route device: " + routeDeviceId);
            Log.i(TAG, "  sharing mode: " + mSharingMode);
        }
        return setupStreamN(
                mNativePlayer, mChannelCount, mSampleRate, mPerformanceMode, mSharingMode,
                routeDeviceId);
    }

    @Override
    public int teardownStream() {
        int errCode = teardownStreamN(mNativePlayer);

        mChannelCount = 0;
        mSampleRate = 0;

        return errCode;
    }

    @Override
    public int startStream() {
        int retVal = startStreamN(mNativePlayer, mPlayerSubtype);
        // TODO - Need Java constants defined for the C++ StreamBase.Result enum
        mPlaying = retVal == 0;
        return retVal;
    }

    @Override
    public int stopStream() {
        mPlaying = false;

        return stopN(mNativePlayer);
    }

    /**
     * Gets a timestamp from the audio stream
     *
     * @param timestamp
     * @return
     */
    public boolean getTimestamp(AudioTimestamp timestamp) {
        return getTimestampN(mNativePlayer, timestamp);
    }

    public int getStreamState() {
        return getStreamStateN(mNativePlayer);
    }

    public int getLastErrorCallbackResult() {
        return getLastErrorCallbackResultN(mNativePlayer);
    }

    private native long allocNativePlayer(long nativeSource, int playerSubtype);

    private native int setupStreamN(long nativePlayer, int channelCount, int sampleRate,
                                    int performanceMode, int sharingMode, int routeDeviceId);

    private native int teardownStreamN(long nativePlayer);

    private native int startStreamN(long nativePlayer, int playerSubtype);

    private native int stopN(long nativePlayer);

    private native int getBufferFrameCountN(long mNativePlayer);

    private native int getRoutedDeviceIdN(long nativePlayer);

    private native int getSharingModeN(long nativePlayer);

    private native int getChannelCountN(long nativePlayer);

    private native boolean getTimestampN(long nativePlayer, AudioTimestamp timestamp);

    private native int getStreamStateN(long nativePlayer);

    private native int getLastErrorCallbackResultN(long nativePlayer);
}
