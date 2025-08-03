/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.audio;

import static android.media.AudioDeviceInfo.TYPE_BUS;
import static android.media.AudioFormat.ENCODING_DEFAULT;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED;
import static android.media.AudioFormat.ENCODING_PCM_32BIT;
import static android.media.AudioFormat.ENCODING_PCM_8BIT;
import static android.media.AudioFormat.ENCODING_PCM_FLOAT;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.car.builtin.media.AudioManagerHelper;
import android.car.builtin.media.AudioManagerHelper.AudioGainInfo;
import android.car.builtin.util.Slogf;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.audio.CarAudioDumpProto.CarAudioDeviceInfoProto;
import com.android.car.audio.hal.HalAudioDeviceInfo;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.util.List;
import java.util.Objects;

/**
 * A helper class wraps {@link AudioDeviceAttributes}, and helps manage the details of the audio
 * device: gains, format, sample rate, channel count
 *
 * Note to the reader. For whatever reason, it seems that AudioGain contains only configuration
 * information (min/max/step, etc) while the AudioGainConfig class contains the
 * actual currently active gain value(s).
 */
/* package */ final class CarAudioDeviceInfo {

    public static final int DEFAULT_SAMPLE_RATE = 48000;
    private static final int DEFAULT_NUM_CHANNELS = 1;
    private static final int UNINITIALIZED_GAIN = -1;

    /*
     * PCM 16 bit is supposed to be guaranteed for all devices
     * per {@link ENCODING_PCM_16BIT}'s documentation.
     */
    private static final int DEFAULT_ENCODING_FORMAT = ENCODING_PCM_16BIT;
    private final AudioManager mAudioManager;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private AudioDeviceAttributes mAudioDeviceAttributes;
    @GuardedBy("mLock")
    private int mDefaultGain;
    @GuardedBy("mLock")
    private int mMaxGain;
    @GuardedBy("mLock")
    private int mMinGain;
    @GuardedBy("mLock")
    private int mStepValue;
    @GuardedBy("mLock")
    private boolean mCanBeRoutedWithDynamicPolicyMixRule = true;
    @GuardedBy("mLock")
    private int mSampleRate;
    @GuardedBy("mLock")
    private int mEncodingFormat;
    @GuardedBy("mLock")
    private int mChannelCount;

    /**
     * We need to store the current gain because it is not accessible from the current
     * audio engine implementation. It would be nice if AudioPort#activeConfig() would return it,
     * but in the current implementation, that function actually works only for mixer ports.
     */
    @GuardedBy("mLock")
    private int mCurrentGain;
    @GuardedBy("mLock")
    private boolean mIsActive;

    CarAudioDeviceInfo(AudioManager audioManager, AudioDeviceAttributes audioDeviceAttributes) {
        mAudioManager = audioManager;
        mAudioDeviceAttributes = audioDeviceAttributes;
        // Device specific information will be initialized once an actual audio device info is set
        mSampleRate = DEFAULT_SAMPLE_RATE;
        mEncodingFormat = DEFAULT_ENCODING_FORMAT;
        mChannelCount = DEFAULT_NUM_CHANNELS;
        mDefaultGain = UNINITIALIZED_GAIN;
        mMaxGain = UNINITIALIZED_GAIN;
        mMinGain = UNINITIALIZED_GAIN;
        mStepValue = UNINITIALIZED_GAIN;

        mCurrentGain = UNINITIALIZED_GAIN; // Not initialized till explicitly set
    }

    boolean isActive() {
        synchronized (mLock) {
            return isActiveLocked();
        }
    }

    /**
     * Updates the volume group with new audio device information if the device info matches
     *
     * <p>Note only updates car audio devices that are dynamic (i.e. non bus devices)
     *
     * @param devices List of audio devices that will be use for update
     * @return {@code true} if the device is updated, {@code false} otherwise.
     */
    boolean audioDevicesAdded(List<AudioDeviceInfo> devices) {
        Objects.requireNonNull(devices, "Audio devices can not be null");
        // Audio device type bus do not allow for devices to be swapped at run time.
        synchronized (mLock) {
            if (getTypeLocked() == TYPE_BUS || isActiveLocked()) {
                return false;
            }

            for (int c = 0; c < devices.size(); c++) {
                if (getTypeLocked() != devices.get(c).getType()) {
                    continue;
                }
                setAudioDeviceInfoLocked(devices.get(c));
                return true;
            }
        }

        return false;
    }

    /**
     * Updates the volume group with removed device information if the device info matches
     *
     * <p>Note only updates car audio devices that are dynamic (i.e. non bus devices)
     *
     * @param devices List of audio devices that will be use for update
     * @return {@code true} if the device is removed, {@code false} otherwise.
     */
    public boolean audioDevicesRemoved(List<AudioDeviceInfo> devices) {
        Objects.requireNonNull(devices, "Audio devices can not be null");
        // Audio device type bus do not allow for devices to be swapped at run time.
        synchronized (mLock) {
            if (getTypeLocked() == TYPE_BUS) {
                return false;
            }

            for (int c = 0; c < devices.size(); c++) {
                if (getTypeLocked() != devices.get(c).getType()
                        || !getAddressLocked().equals(devices.get(c).getAddress())) {
                    continue;
                }
                setAudioDeviceInfoLocked(null);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the audio device info
     *
     * <p>Given that the audio device information may not be available at the time of construction,
     * the method must call to set the audio device info, so that the actual details of the device
     * are known.
     *
     * <p>Setting the audio device info to {@code null} means the device is not active, such is
     * the case for dynamic audio devices that should disappear on disconnection.
     *
     * @param info that will be use to obtain the device specific information
     */
    void setAudioDeviceInfo(@Nullable AudioDeviceInfo info) {
        synchronized (mLock) {
            setAudioDeviceInfoLocked(info);
        }
    }

    AudioDeviceAttributes getAudioDevice() {
        synchronized (mLock) {
            return mAudioDeviceAttributes;
        }
    }

    String getAddress() {
        synchronized (mLock) {
            return getAddressLocked();
        }
    }

    int getType() {
        synchronized (mLock) {
            return getTypeLocked();
        }
    }

    /**
     * By default, considers all AudioDevice can be used to establish dynamic policy mixing rules.
     * until validation state is performed.
     * Once called, the device is marked definitively as "connot be routed with dynamic mixes".
     */
    void resetCanBeRoutedWithDynamicPolicyMix() {
        synchronized (mLock) {
            mCanBeRoutedWithDynamicPolicyMixRule = false;
        }
    }

    boolean canBeRoutedWithDynamicPolicyMix() {
        synchronized (mLock) {
            return mCanBeRoutedWithDynamicPolicyMixRule;
        }
    }

    int getDefaultGain() {
        synchronized (mLock) {
            return mDefaultGain;
        }
    }

    int getMaxGain() {
        synchronized (mLock) {
            return mMaxGain;
        }
    }

    int getMinGain() {
        synchronized (mLock) {
            return mMinGain;
        }
    }

    int getSampleRate() {
        synchronized (mLock) {
            return mSampleRate;
        }
    }

    int getEncodingFormat() {
        synchronized (mLock) {
            return mEncodingFormat;
        }
    }

    int getChannelCount() {
        synchronized (mLock) {
            return mChannelCount;
        }
    }

    int getStepValue() {
        synchronized (mLock) {
            return mStepValue;
        }
    }


    void setCurrentGain(int gainInMillibels) {
        int gain = gainInMillibels;
        // Clamp the incoming value to our valid range.  Out of range values ARE legal input
        synchronized (mLock) {
            if (gain < mMinGain) {
                gain = mMinGain;
            } else if (gain > mMaxGain) {
                gain = mMaxGain;
            }
        }

        if (AudioManagerHelper.setAudioDeviceGain(mAudioManager,
                getAddress(), gain, true)) {
            // Since we can't query for the gain on a device port later,
            // we have to remember what we asked for
            synchronized (mLock) {
                mCurrentGain = gain;
            }
        } else {
            Slogf.e(CarLog.TAG_AUDIO, "Failed to setAudioPortGain " + gain
                    + " for output device " + getAddress());
        }
    }

    // Updates audio device info for dynamic gain stage configurations
    void updateAudioDeviceInfo(HalAudioDeviceInfo halDeviceInfo) {
        synchronized (mLock) {
            mMinGain = halDeviceInfo.getGainMinValue();
            mMaxGain = halDeviceInfo.getGainMaxValue();
            mStepValue = halDeviceInfo.getGainStepValue();
            mDefaultGain = halDeviceInfo.getGainDefaultValue();
        }
    }

    @GuardedBy("mLock")
    private int getTypeLocked() {
        return mAudioDeviceAttributes.getType();
    }

    @GuardedBy("mLock")
    private void setAudioDeviceInfoLocked(AudioDeviceInfo info) {
        if (info != null && info.getType() != getTypeLocked()) {
            return;
        }

        // BUS device type can not be unset
        if ((getTypeLocked() == TYPE_BUS)
                && (info == null || !getAddressLocked().equals(info.getAddress()))) {
            return;
        }

        resetAudioDeviceInfoToDefaultLocked();

        if (info == null) {
            return;
        }

        setAudioDeviceInfoWithNewInfoLocked(info);
    }

    @GuardedBy("mLock")
    private void setAudioDeviceInfoWithNewInfoLocked(AudioDeviceInfo info) {
        setAudioGainInfoIfNeededLocked(info);
        mIsActive = true;
    }

    @GuardedBy("mLock")
    private void setAudioGainInfoIfNeededLocked(AudioDeviceInfo info) {
        mAudioDeviceAttributes = new AudioDeviceAttributes(info);
        // Only audio device bus supports audio gain management by car audio service
        // Dynamic devices only support core audio volume management
        if (info.getType() != TYPE_BUS) {
            return;
        }
        AudioGainInfo audioGainInfo = AudioManagerHelper.getAudioGainInfo(info);
        mDefaultGain = audioGainInfo.getDefaultGain();
        mMaxGain = audioGainInfo.getMaxGain();
        mMinGain = audioGainInfo.getMinGain();
        mStepValue = audioGainInfo.getStepValue();
        mChannelCount = getMaxChannels(info);
        mSampleRate = getMaxSampleRate(info);
        mEncodingFormat = getEncodingFormat(info);
    }

    @GuardedBy("mLock")
    private void resetAudioDeviceInfoToDefaultLocked() {
        int type = mAudioDeviceAttributes.getType();
        mAudioDeviceAttributes = new AudioDeviceAttributes(AudioDeviceAttributes.ROLE_OUTPUT,
                type, /* address= */ "");
        mSampleRate = DEFAULT_SAMPLE_RATE;
        mEncodingFormat = DEFAULT_ENCODING_FORMAT;
        mChannelCount = DEFAULT_NUM_CHANNELS;
        mDefaultGain = UNINITIALIZED_GAIN;
        mMaxGain = UNINITIALIZED_GAIN;
        mMinGain = UNINITIALIZED_GAIN;
        mStepValue = UNINITIALIZED_GAIN;
        mCurrentGain = UNINITIALIZED_GAIN;
        mIsActive = false;
    }

    @GuardedBy("mLock")
    private String getAddressLocked() {
        return mAudioDeviceAttributes.getAddress();
    }

    @GuardedBy("mLock")
    private boolean isActiveLocked() {
        return mIsActive;
    }

    private static int getMaxSampleRate(AudioDeviceInfo info) {
        int[] sampleRates = info.getSampleRates();
        if (sampleRates == null || sampleRates.length == 0) {
            return DEFAULT_SAMPLE_RATE;
        }
        int sampleRate = sampleRates[0];
        for (int i = 1; i < sampleRates.length; i++) {
            if (sampleRates[i] > sampleRate) {
                sampleRate = sampleRates[i];
            }
        }
        return sampleRate;
    }

    private static int getEncodingFormat(AudioDeviceInfo info) {
        int[] formats = info.getEncodings();
        // If the formats are not specified, then arbitrary encoding are supported
        if (formats == null) {
            return DEFAULT_ENCODING_FORMAT;
        }

        for (int c = 0; c < formats.length; c++) {
            // Audio policy mix limits linear PCMs
            if (isEncodingLinearPcm(formats[c])) {
                return formats[c];
            }
        }

        return DEFAULT_ENCODING_FORMAT;
    }

    private static boolean isEncodingLinearPcm(int audioFormat) {
        switch (audioFormat) {
            case ENCODING_PCM_16BIT:
            case ENCODING_PCM_8BIT:
            case ENCODING_PCM_FLOAT:
            case ENCODING_PCM_24BIT_PACKED:
            case ENCODING_PCM_32BIT:
            case ENCODING_DEFAULT:
                return true;
            default:
                return false;
        }
    }

    /*
     * If there are no profiles in the device this will return the {@link #DEFAULT_NUM_CHANNELS}
     */
    private static int getMaxChannels(AudioDeviceInfo info) {
        int numChannels = 1;
        int[] channelMasks = info.getChannelMasks();
        if (channelMasks == null) {
            return numChannels;
        }
        for (int channelMask : channelMasks) {
            int currentNumChannels = Integer.bitCount(channelMask);
            if (currentNumChannels > numChannels) {
                numChannels = currentNumChannels;
            }
        }
        return numChannels;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public String toString() {
        int currentGain;
        synchronized (mLock) {
            currentGain = mCurrentGain;
        }
        return "address: " + getAddress()
                + " sampleRate: " + getSampleRate()
                + " encodingFormat: " + getEncodingFormat()
                + " channelCount: " + getChannelCount()
                + " currentGain: " + currentGain
                + " maxGain: " + getMaxGain()
                + " minGain: " + getMinGain();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.printf("CarAudioDeviceInfo Device(%s)\n", mAudioDeviceAttributes.getAddress());
            writer.printf("CarAudioDeviceInfo Type(%s)\n", mAudioDeviceAttributes.getType());
            writer.increaseIndent();
            writer.printf("Is active (%b)\n", mIsActive);
            writer.printf("Routing with Dynamic Mix enabled (%b)\n",
                    mCanBeRoutedWithDynamicPolicyMixRule);
            writer.printf("sample rate / encoding format / channel count: %d %d %d\n",
                    getSampleRate(), getEncodingFormat(), getChannelCount());
            writer.printf("Gain values (min / max / default/ current): %d %d %d %d\n",
                    mMinGain, mMaxGain, mDefaultGain, mCurrentGain);
            writer.decreaseIndent();
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(long fieldId, ProtoOutputStream proto) {
        long token = proto.start(fieldId);
        synchronized (mLock) {
            proto.write(CarAudioDeviceInfoProto.ADDRESS, mAudioDeviceAttributes.getAddress());
            proto.write(CarAudioDeviceInfoProto.CAN_BE_ROUTED_WITH_DYNAMIC_POLICY_MIX_RULE,
                    mCanBeRoutedWithDynamicPolicyMixRule);
            proto.write(CarAudioDeviceInfoProto.SAMPLE_RATE, getSampleRate());
            proto.write(CarAudioDeviceInfoProto.ENCODING_FORMAT, getEncodingFormat());
            proto.write(CarAudioDeviceInfoProto.CHANNEL_COUNT, getChannelCount());

            long volumeGainToken = proto.start(CarAudioDeviceInfoProto.VOLUME_GAIN);
            proto.write(CarAudioDumpProto.CarVolumeGain.MIN_GAIN_INDEX, mMinGain);
            proto.write(CarAudioDumpProto.CarVolumeGain.MAX_GAIN_INDEX, mMaxGain);
            proto.write(CarAudioDumpProto.CarVolumeGain.DEFAULT_GAIN_INDEX, mDefaultGain);
            proto.write(CarAudioDumpProto.CarVolumeGain.CURRENT_GAIN_INDEX, mCurrentGain);
            proto.write(CarAudioDeviceInfoProto.IS_ACTIVE , mIsActive);
            proto.end(volumeGainToken);
        }
        proto.end(token);
    }
}
