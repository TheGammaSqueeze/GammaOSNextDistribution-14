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
package org.hyphonate.megaaudio.common;

import android.media.AudioDeviceInfo;
import android.media.AudioTrack;

/**
 * Base class for Stream Builders.
 *
 * Contains common stream attributes that will be used when building a contcrete stream object.
 */
public abstract class BuilderBase {
    @SuppressWarnings("unused")
    private static final String TAG = BuilderBase.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = false;

    // API Types - enumerated in high nibble
    public static final int TYPE_MASK = 0xF000;
    public static final int TYPE_UNDEFINED = 0xF000;
    public static final int TYPE_NONE = 0x0000;
    public static final int TYPE_JAVA = 0x1000;
    public static final int TYPE_OBOE = 0x2000;

    // API subtypes - enumerated in low nibble
    public static final int SUB_TYPE_MASK = 0x0000F;
    public static final int SUB_TYPE_OBOE_DEFAULT = 0x0000;
    public static final int SUB_TYPE_OBOE_AAUDIO = 0x0001;
    public static final int SUB_TYPE_OBOE_OPENSL_ES = 0x0002;

    /**
     * The type id of the stream to create. Composed of the above constants.
     */
    protected int mType = TYPE_UNDEFINED;

    /**
     * The number of frames per exchange of audio data to/from the AudioSink/AudioSource
     * associated with this stream.
     * Note: this is not the same as the size of record/playback buffers used by the
     * underlying Native/Java player/recorder objects. That size is determined by the
     * underlying Native/Java player/recorder objects.
     */
    protected int mNumExchangeFrames = 128;

    /**
     * The sample rate for the created stream.
     */
    protected int mSampleRate = 48000;

    /**
     * The number of channels for the created stream.
     */
    protected int mChannelCount = 2;

    // Performance Mode Constants
    public static final int PERFORMANCE_MODE_NONE = 10; // AAUDIO_PERFORMANCE_MODE_NONE
    public static final int
            PERFORMANCE_MODE_POWERSAVING = 11;  // AAUDIO_PERFORMANCE_MODE_POWER_SAVING,
    public static final int
            PERFORMANCE_MODE_LOWLATENCY = 12;   // AAUDIO_PERFORMANCE_MODE_LOW_LATENCY
    /**
     * The performance mode for the created stream. It can be any one of the constants above.
     */
    protected int mPerformanceMode = PERFORMANCE_MODE_LOWLATENCY;

    // Sharing Mode Constants
    public static final int SHARING_MODE_EXCLUSIVE = 0; // AAUDIO_SHARING_MODE_EXCLUSIVE
    public static final int SHARING_MODE_SHARED = 1;    // AAUDIO_SHARING_MODE_SHARED
    public static final int SHARING_MODE_NOTSUPPORTED = -1;    // A return value only

    /**
     * The sharing mode for the created stream. It can be any one of the constants above.
     */
    protected int mSharingMode = SHARING_MODE_EXCLUSIVE;

    /**
     * If non-null, the device to route the stream to/from upon creation.
     */
    protected AudioDeviceInfo mRouteDevice;

    /**
     * Sets the number of frames exchanged between Players/Recorder and
     * the corresponding AudioSource/AudioSink objects.
     * @param numFrames the number of frames to exchange.
     * @return this BuilderBase (for cascaded calls)
     */
    public BuilderBase setNumExchangeFrames(int numFrames) {
        mNumExchangeFrames = numFrames;
        return this;
    }

    /**
     * @return The number of frames of audio exchanged between Players/Recorder and
     * the corresponding AudioSource/AudioSink objects,
     * specified in the setNumExchangeFrames() method.
     */
    public int getNumExchangeFrames() {
        return mNumExchangeFrames;
    }

    /**
     * Specifies the sample rate for the created stream
     * @param sampleRate The sample rate for the created stream.
     * @return this BuilderBase (for cascaded calls)
     */
    public BuilderBase setSampleRate(int sampleRate) {
        mSampleRate = sampleRate;
        return this;
    }

    /**
     * @return the sample rate for the created stream, specified in the
     * setSampleRate() method.
     */
    public int getSampleRate() {
        return mSampleRate;
    }

    /**
     * Specifies a channel count for a stream
     * @param channelCount The number of channels for the created stream.
     * @return this BuilderBase (for cascaded calls)
     */
    public BuilderBase setChannelCount(int channelCount) {
        mChannelCount = channelCount;
        return this;
    }

    /**
     * @return the number of channels for the created stream, specified in the
     * setChannelCount() method.
     */
    public int getChannelCount() {
        return mChannelCount;
    }

    /**
     * Sets the sharing mode for the created stream.
     * @param mode See "Sharing Mode Constants" Above
     * @return this BuilderBase (for cascaded calls)
     */
    public BuilderBase setSharingMode(int mode) {
        mSharingMode = mode;
        return this;
    }

    /**
     * @return The sharing mode for the created stream, set in the setSharingMode() method.
     */
    public int getSharingMode() {
        return mSharingMode;
    }

    /**
     * Sets the performance mode for the created stream.
     * @param mode See "Performance Mode Constants" above
     * @return this BuilderBase (for cascaded calls)
     */
    public BuilderBase setPerformanceMode(int mode) {
        mPerformanceMode = mode;
        return this;
    }

    /**
     * @return The performance mode for the created stream, set in the setPerformanceMode() method.
     */
    public int getPerformanceMode() {
        return mPerformanceMode;
    }

    // This is needed because the performance mode constants for the Java API
    // are different than those for the AAudio/Oboe API
    // These are the JAVA AudioTrack constants
    // public static final int PERFORMANCE_MODE_NONE = 0;
    // public static final int PERFORMANCE_MODE_LOW_LATENCY = 1;
    // public static final int PERFORMANCE_MODE_POWER_SAVING = 2;
    /**
     * Maps from MegaAudio (and AAudio/Oboe) performance mode constants the equivalent
     * Java AudioTrack constants.
     * @return The Java AudioTrack constant corresponding the current performance mode
     * for the created stream.
     */
    public int getJavaPerformanceMode() {
        switch (mPerformanceMode) {
            case PERFORMANCE_MODE_NONE:
                return AudioTrack.PERFORMANCE_MODE_NONE;

            case PERFORMANCE_MODE_POWERSAVING:
                return AudioTrack.PERFORMANCE_MODE_POWER_SAVING;

            case PERFORMANCE_MODE_LOWLATENCY:
            default:
                return AudioTrack.PERFORMANCE_MODE_LOW_LATENCY;
        }
    }

    /**
     * Specifies the device to route the stream to/from upon creation. If null, the default
     * route is selected.
     * @param routeDevice The device to route to, or null for the default device.
     * @return This Builderbase (for cascaded calls)
     */
    public BuilderBase setRouteDevice(AudioDeviceInfo routeDevice) {
        mRouteDevice = routeDevice;
        return this;
    }

    /**
     * @return The AudioDevice to route the stream to/from. If null, specifies the default device.
     */
    public AudioDeviceInfo getRouteDevice() {
        return mRouteDevice;
    }

    // Indicates no specific routing (default routing).
    public static final int ROUTED_DEVICE_ID_DEFAULT = -1;

    /**
     * For convenience in converting the AudioDeviceInfo to an integer device ID.
     * @return The integer device ID of the device to route to/from. -1 if no device is specified.
     */
    public int getRouteDeviceId() {
        return mRouteDevice == null ? ROUTED_DEVICE_ID_DEFAULT : mRouteDevice.getId();
    }
}
