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

package android.car.builtin.media;

import static android.media.AudioAttributes.USAGE_VIRTUAL_SOURCE;
import static android.media.AudioManager.EXTRA_VOLUME_STREAM_TYPE;
import static android.media.AudioManager.GET_DEVICES_INPUTS;
import static android.media.AudioManager.GET_DEVICES_OUTPUTS;
import static android.media.AudioManager.MASTER_MUTE_CHANGED_ACTION;
import static android.media.AudioManager.VOLUME_CHANGED_ACTION;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.builtin.util.Slogf;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioAttributes.AttributeUsage;
import android.media.AudioDeviceInfo;
import android.media.AudioDevicePort;
import android.media.AudioFormat;
import android.media.AudioGain;
import android.media.AudioGainConfig;
import android.media.AudioManager;
import android.media.AudioPatch;
import android.media.AudioPortConfig;
import android.media.AudioSystem;
import android.media.audiopolicy.AudioProductStrategy;
import android.text.TextUtils;

import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Helper for Audio related operations.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class AudioManagerHelper {
    private static final String TAG = AudioManagerHelper.class.getSimpleName();

    public static final int UNDEFINED_STREAM_TYPE = -1;

    public static final String AUDIO_ATTRIBUTE_TAG_SEPARATOR = ";";

    private AudioManagerHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * Set the audio device gain for device with {@code address}
     * @param audioManager audio manager
     * @param address Address for device to set gain
     * @param gainInMillibels gain in millibels to set
     * @param isOutput is the device an output device
     * @return true if the gain was successfully set
     */
    public static boolean setAudioDeviceGain(@NonNull AudioManager audioManager,
            @NonNull String address, int gainInMillibels, boolean isOutput) {
        Preconditions.checkNotNull(audioManager,
                "Audio Manager can not be null in set device gain, device address %s", address);
        AudioDeviceInfo deviceInfo = getAudioDeviceInfo(audioManager, address, isOutput);

        AudioGain audioGain = getAudioGain(deviceInfo.getPort());

        // size of gain values is 1 in MODE_JOINT
        AudioGainConfig audioGainConfig = audioGain.buildConfig(
                AudioGain.MODE_JOINT,
                audioGain.channelMask(),
                new int[] { gainInMillibels },
                0);
        if (audioGainConfig == null) {
            throw new IllegalStateException("Failed to construct AudioGainConfig for device "
                    + address);
        }

        return AudioManager.setAudioPortGain(deviceInfo.getPort(), audioGainConfig)
                == AudioManager.SUCCESS;
    }

    private static AudioDeviceInfo getAudioDeviceInfo(@NonNull AudioManager audioManager,
            @NonNull String address, boolean isOutput) {
        Objects.requireNonNull(address, "Device address can not be null");
        Preconditions.checkStringNotEmpty(address, "Device Address can not be empty");

        AudioDeviceInfo[] devices =
                audioManager.getDevices(isOutput ? GET_DEVICES_OUTPUTS : GET_DEVICES_INPUTS);

        for (int index = 0; index < devices.length; index++) {
            AudioDeviceInfo device = devices[index];
            if (address.equals(device.getAddress())) {
                return device;
            }
        }

        throw new IllegalStateException((isOutput ? "Output" : "Input")
                + " Audio device info not found for device address " + address);
    }

    private static AudioGain getAudioGain(@NonNull AudioDevicePort deviceport) {
        Objects.requireNonNull(deviceport, "Audio device port can not be null");
        Preconditions.checkArgument(deviceport.gains().length > 0,
                "Audio device must have gains defined");
        for (int index = 0; index < deviceport.gains().length; index++) {
            AudioGain gain = deviceport.gains()[index];
            if ((gain.mode() & AudioGain.MODE_JOINT) != 0) {
                return checkAudioGainConfiguration(gain);
            }
        }
        throw new IllegalStateException("Audio device does not have a valid audio gain");
    }

    private static AudioGain checkAudioGainConfiguration(@NonNull AudioGain audioGain) {
        Preconditions.checkArgument(audioGain.maxValue() >= audioGain.minValue(),
                "Max gain %d is lower than min gain %d",
                audioGain.maxValue(), audioGain.minValue());
        Preconditions.checkArgument((audioGain.defaultValue() >= audioGain.minValue())
                && (audioGain.defaultValue() <= audioGain.maxValue()),
                "Default gain %d not in range (%d,%d)", audioGain.defaultValue(),
                audioGain.minValue(), audioGain.maxValue());
        Preconditions.checkArgument(
                ((audioGain.maxValue() - audioGain.minValue()) % audioGain.stepValue()) == 0,
                "Gain step value %d greater than min gain to max gain range %d",
                audioGain.stepValue(), audioGain.maxValue() - audioGain.minValue());
        Preconditions.checkArgument(
                ((audioGain.defaultValue() - audioGain.minValue()) % audioGain.stepValue()) == 0,
                "Gain step value %d greater than min gain to default gain range %d",
                audioGain.stepValue(), audioGain.defaultValue() - audioGain.minValue());
        return audioGain;
    }

    /**
     * Returns the audio gain information for the specified device.
     * @param deviceInfo
     * @return
     */
    public static AudioGainInfo getAudioGainInfo(@NonNull AudioDeviceInfo deviceInfo) {
        Objects.requireNonNull(deviceInfo, "Audio device gain info can not be null");
        return new AudioGainInfo(getAudioGain(deviceInfo.getPort()));
    }

    /**
     * Creates an audio patch from source and sink source
     * @param sourceDevice Source device for the patch
     * @param sinkDevice Sink device of the patch
     * @param gainInMillibels gain to apply to the source device
     * @return The audio patch information that was created
     */
    public static AudioPatchInfo createAudioPatch(@NonNull AudioDeviceInfo sourceDevice,
            @NonNull AudioDeviceInfo sinkDevice, int gainInMillibels) {
        Preconditions.checkNotNull(sourceDevice,
                "Source device can not be null, sink info %s", sinkDevice);
        Preconditions.checkNotNull(sinkDevice,
                "Sink device can not be null, source info %s", sourceDevice);

        AudioDevicePort sinkPort = Preconditions.checkNotNull(sinkDevice.getPort(),
                "Sink device [%s] does not contain an audio port", sinkDevice);

        // {@link android.media.AudioPort#activeConfig()} is valid for mixer port only,
        // since audio framework has no clue what's active on the device ports.
        // Therefore we construct an empty / default configuration here, which the audio HAL
        // implementation should ignore.
        AudioPortConfig sinkConfig = sinkPort.buildConfig(0,
                AudioFormat.CHANNEL_OUT_DEFAULT, AudioFormat.ENCODING_DEFAULT, null);
        Slogf.d(TAG, "createAudioPatch sinkConfig: " + sinkConfig);

        // Configure the source port to match the output port except for a gain adjustment
        AudioGain audioGain = Objects.requireNonNull(getAudioGain(sourceDevice.getPort()),
                "Gain controller not available for source port");

        // size of gain values is 1 in MODE_JOINT
        AudioGainConfig audioGainConfig = audioGain.buildConfig(AudioGain.MODE_JOINT,
                audioGain.channelMask(), new int[] { gainInMillibels }, 0);
        // Construct an empty / default configuration excepts gain config here and it's up to the
        // audio HAL how to interpret this configuration, which the audio HAL
        // implementation should ignore.
        AudioPortConfig sourceConfig = sourceDevice.getPort().buildConfig(0,
                AudioFormat.CHANNEL_IN_DEFAULT, AudioFormat.ENCODING_DEFAULT, audioGainConfig);

        // Create an audioPatch to connect the two ports
        AudioPatch[] patch = new AudioPatch[] { null };
        int result = AudioManager.createAudioPatch(patch,
                new AudioPortConfig[] { sourceConfig },
                new AudioPortConfig[] { sinkConfig });
        if (result != AudioManager.SUCCESS) {
            throw new RuntimeException("createAudioPatch failed with code " + result);
        }

        Preconditions.checkNotNull(patch[0],
                "createAudioPatch didn't provide expected single handle [source: %s,sink: %s]",
                sinkDevice, sourceDevice);
        Slogf.d(TAG, "Audio patch created: " + patch[0]);

        return createAudioPatchInfo(patch[0]);
    }

    private static AudioPatchInfo createAudioPatchInfo(AudioPatch patch) {
        Preconditions.checkArgument(patch.sources().length == 1
                        && patch.sources()[0].port() instanceof AudioDevicePort,
                "Accepts exactly one device port as source");
        Preconditions.checkArgument(patch.sinks().length == 1
                        && patch.sinks()[0].port() instanceof AudioDevicePort,
                "Accepts exactly one device port as sink");

        return new AudioPatchInfo(((AudioDevicePort) patch.sources()[0].port()).address(),
                ((AudioDevicePort) patch.sinks()[0].port()).address(),
                patch.id());
    }

    /**
     * Releases audio patch handle
     * @param audioManager manager to call for releasing of handle
     * @param info patch information to release
     * @return returns true if the patch was successfully removed
     */
    public static boolean releaseAudioPatch(@NonNull AudioManager audioManager,
            @NonNull AudioPatchInfo info) {
        Preconditions.checkNotNull(audioManager,
                "Audio Manager can not be null in release audio patch for %s", info);
        Preconditions.checkNotNull(info,
                "Audio Patch Info can not be null in release audio patch for %s", info);
        // NOTE:  AudioPolicyService::removeNotificationClient will take care of this automatically
        //        if the client that created a patch quits.
        ArrayList<AudioPatch> patches = new ArrayList<>();
        int result = audioManager.listAudioPatches(patches);
        if (result != AudioManager.SUCCESS) {
            throw new RuntimeException("listAudioPatches failed with code " + result);
        }

        // Look for a patch that matches the provided user side handle
        for (AudioPatch patch : patches) {
            if (info.represents(patch)) {
                // Found it!
                result = AudioManager.releaseAudioPatch(patch);
                if (result != AudioManager.SUCCESS) {
                    throw new RuntimeException("releaseAudioPatch failed with code " + result);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the string representation of {@link android.media.AudioAttributes.AttributeUsage}.
     *
     * <p>See {@link android.media.AudioAttributes.usageToString}.
     */
    public static String usageToString(@AttributeUsage int usage) {
        return AudioAttributes.usageToString(usage);
    }

    /**
     * Returns the xsd string representation of
     * {@link android.media.AudioAttributes.AttributeUsage}.
     *
     * <p>See {@link android.media.AudioAttributes.usageToXsdString}.
     */
    public static String usageToXsdString(@AttributeUsage int usage) {
        return AudioAttributes.usageToXsdString(usage);
    }

    /**
     * Returns {@link android.media.AudioAttributes.AttributeUsage} representation of
     * xsd usage string.
     *
     * <p>See {@link android.media.AudioAttributes.xsdStringToUsage}.
     */
    public static int xsdStringToUsage(String usage) {
        return AudioAttributes.xsdStringToUsage(usage);
    }

    /**
     * Returns {@link android.media.AudioAttributes.AttributeUsage} for
     * {@link android.media.AudioAttributes.AttributeUsage.USAGE_VIRTUAL_SOURCE}.
     */
    public static int getUsageVirtualSource() {
        return USAGE_VIRTUAL_SOURCE;
    }

    /**
     * Returns the string representation of volume adjustment.
     *
     * <p>See {@link android.media.AudioManager#adjustToString(int)}
     */
    public static String adjustToString(int adjustment) {
        return AudioManager.adjustToString(adjustment);
    }

    /**
     * Sets the system master mute state.
     *
     * <p>See {@link android.media.AudioManager#setMasterMute(boolean, int)}.
     */
    public static void setMasterMute(@NonNull AudioManager audioManager, boolean mute, int flags) {
        Objects.requireNonNull(audioManager, "AudioManager must not be null.");
        audioManager.setMasterMute(mute, flags);
    }

    /**
     * Gets system master mute state.
     *
     * <p>See {@link android.media.AudioManager#isMasterMute()}.
     */
    public static boolean isMasterMute(@NonNull AudioManager audioManager) {
        Objects.requireNonNull(audioManager, "AudioManager must not be null.");
        return audioManager.isMasterMute();
    }

    /**
     * Registers volume and mute receiver
     */
    public static void registerVolumeAndMuteReceiver(Context context,
            VolumeAndMuteReceiver audioAndMuteHelper) {
        Objects.requireNonNull(context, "Context can not be null.");
        Objects.requireNonNull(audioAndMuteHelper, "Audio and Mute helper can not be null.");

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(VOLUME_CHANGED_ACTION);
        intentFilter.addAction(MASTER_MUTE_CHANGED_ACTION);
        context.registerReceiver(audioAndMuteHelper.getReceiver(), intentFilter,
                Context.RECEIVER_NOT_EXPORTED);
    }

    /**
     * Unregisters volume and mute receiver
     */
    public static void unregisterVolumeAndMuteReceiver(Context context,
            VolumeAndMuteReceiver audioAndMuteHelper) {
        Objects.requireNonNull(context, "Context can not be null.");
        Objects.requireNonNull(audioAndMuteHelper, "Audio and Mute helper can not be null.");

        context.unregisterReceiver(audioAndMuteHelper.getReceiver());
    }

    /**
     * Checks if the client id is equal to the telephony's focus client id.
     */
    public static boolean isCallFocusRequestClientId(String clientId) {
        return AudioSystem.IN_VOICE_COMM_FOCUS_ID.equals(clientId);
    }


    /**
     * Audio gain information for a particular device:
     * Contains Max, Min, Default gain and the step value between gain changes
     */
    public static class AudioGainInfo {

        private final int mMinGain;
        private final int mMaxGain;
        private final int mDefaultGain;
        private final int mStepValue;

        private AudioGainInfo(AudioGain gain) {
            mMinGain = gain.minValue();
            mMaxGain = gain.maxValue();
            mDefaultGain = gain.defaultValue();
            mStepValue = gain.stepValue();
        }

        public int getMinGain() {
            return mMinGain;
        }

        public int getMaxGain() {
            return mMaxGain;
        }

        public int getDefaultGain() {
            return mDefaultGain;
        }

        public int getStepValue() {
            return mStepValue;
        }
    }

    /**
     * Contains the audio patch information for the created audio patch:
     * Patch handle id, source device address, sink device address
     */
    public static class AudioPatchInfo {
        private final int mHandleId;

        private final String mSourceAddress;
        private final String mSinkAddress;


        public AudioPatchInfo(@NonNull String sourceAddress, @NonNull String sinkAddress,
                int handleId) {
            mSourceAddress = Preconditions.checkNotNull(sourceAddress,
                    "Source Address can not be null for patch id %d", handleId);
            mSinkAddress = Preconditions.checkNotNull(sinkAddress,
                    "Sink Address can not be null for patch id %d", handleId);
            mHandleId = handleId;
        }

        public int getHandleId() {
            return mHandleId;
        }

        public String getSourceAddress() {
            return mSourceAddress;
        }

        public String getSinkAddress() {
            return mSinkAddress;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Source{ ");
            builder.append(mSourceAddress);
            builder.append("} Sink{ ");
            builder.append(mSinkAddress);
            builder.append("} Handle{ ");
            builder.append(mHandleId);
            builder.append("}");
            return builder.toString();
        }

        private boolean represents(AudioPatch patch) {
            return patch.id() == mHandleId;
        }
    }

    /**
     * Class to manage volume and mute changes from audio manager
     */
    public abstract static class VolumeAndMuteReceiver {

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case VOLUME_CHANGED_ACTION:
                        int streamType =
                                intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, UNDEFINED_STREAM_TYPE);
                        onVolumeChanged(streamType);
                        break;
                    case MASTER_MUTE_CHANGED_ACTION:
                        onMuteChanged();
                        break;
                    default:
                        break;
                }
            }
        };

        private BroadcastReceiver getReceiver() {
            return mReceiver;
        }

        /**
         * Called on volume changes
         * @param streamType type of stream for the volume change
         */
        public abstract void onVolumeChanged(int streamType);

        /**
         * Called on mute changes
         */
        public abstract void onMuteChanged();
    }

    /**
     * Adds a tags to the {@link AudioAttributes}.
     *
     * <p>{@link AudioProductStrategy} may use additional information to override the current
     * stream limitation used for routing.
     *
     * <p>As Bundler are not propagated to native layer, tags were used to be dispatched to the
     * AudioPolicyManager.
     *
     * @param builder {@link AudioAttributes.Builder} helper to build {@link AudioAttributes}
     * @param tag to be added to the {@link AudioAttributes} once built.
     */
    public static void addTagToAudioAttributes(@NonNull AudioAttributes.Builder builder,
            @NonNull String tag) {
        builder.addTag(tag);
    }

    /**
     * Gets a separated string of tags associated to given {@link AudioAttributes}
     *
     * @param attributes {@link AudioAttributes} to be considered
     * @return the tags of the given {@link AudioAttributes} as a
     * {@link #AUDIO_ATTRIBUTE_TAG_SEPARATOR} separated string.
     */
    public static String getFormattedTags(@NonNull AudioAttributes attributes) {
        Preconditions.checkNotNull(attributes, "Audio Attributes must not be null");
        return TextUtils.join(AUDIO_ATTRIBUTE_TAG_SEPARATOR, attributes.getTags());
    }

    /**
     * Gets a set of string of tags associated to given {@link AudioAttributes}
     *
     * @param attributes {@link AudioAttributes} to be considered
     * @return the tags of the given {@link AudioAttributes} as a Set of Strings.
     */
    public static Set<String> getTags(@NonNull AudioAttributes attributes) {
        Preconditions.checkNotNull(attributes, "Audio Attributes must not be null");
        return attributes.getTags();
    }

    private static final Map<String, Integer> XSD_STRING_TO_CONTENT_TYPE = Map.of(
            "AUDIO_CONTENT_TYPE_UNKNOWN", AudioAttributes.CONTENT_TYPE_UNKNOWN,
            "AUDIO_CONTENT_TYPE_SPEECH", AudioAttributes.CONTENT_TYPE_SPEECH,
            "AUDIO_CONTENT_TYPE_MUSIC", AudioAttributes.CONTENT_TYPE_MUSIC,
            "AUDIO_CONTENT_TYPE_MOVIE", AudioAttributes.CONTENT_TYPE_MOVIE,
            "AUDIO_CONTENT_TYPE_SONIFICATION", AudioAttributes.CONTENT_TYPE_SONIFICATION,
            "AUDIO_CONTENT_TYPE_ULTRASOUND", AudioAttributes.CONTENT_TYPE_ULTRASOUND
    );

    /**
     * Converts a literal representation of tags into {@link AudioAttributes.ContentType} value.
     *
     * @param xsdString string to be converted into {@link AudioAttributes.ContentType}
     * @return {@link AudioAttributes.ContentType} representation of xsd content type string if
     * found, {@code AudioAttributes.CONTENT_TYPE_UNKNOWN} otherwise.
     */
    public static int xsdStringToContentType(String xsdString) {
        if (XSD_STRING_TO_CONTENT_TYPE.containsKey(xsdString)) {
            return XSD_STRING_TO_CONTENT_TYPE.get(xsdString);
        }
        return AudioAttributes.CONTENT_TYPE_UNKNOWN;
    }

    /**
     * Gets the {@link android.media.AudioVolumeGroup} id associated with given
     * {@link AudioProductStrategy} and {@link AudioAttributes}
     *
     * @param strategy {@link AudioProductStrategy} to be considered
     * @param attributes {@link AudioAttributes} to be considered
     * @return the id of the {@link android.media.AudioVolumeGroup} supporting the given
     * {@link AudioAttributes} and {@link AudioProductStrategy} if found,
     * {@link android.media.AudioVolumeGroup.DEFAULT_VOLUME_GROUP} otherwise.
     */
    public static int getVolumeGroupIdForAudioAttributes(
            @NonNull AudioProductStrategy strategy, @NonNull AudioAttributes attributes) {
        Preconditions.checkNotNull(attributes, "Audio Attributes must not be null");
        Preconditions.checkNotNull(strategy, "Audio Product Strategy must not be null");
        return strategy.getVolumeGroupIdForAudioAttributes(attributes);
    }

    /**
     * Gets the last audible volume for a given {@link android.media.AudioVolumeGroup} id.
     * <p>The last audible index is the current index if not muted, or index applied before mute if
     * muted. If muted by volume 0, the last audible index is 0. See
     * {@link AudioManager#getLastAudibleVolumeForVolumeGroup} for details.
     *
     * @param audioManager {@link AudioManager} instance to be used for the request
     * @param amGroupId id of the {@link android.media.AudioVolumeGroup} to consider
     * @return the last audible volume of the {@link android.media.AudioVolumeGroup}
     * referred by its id if found, {@code 0} otherwise.
     */
    public static int getLastAudibleVolumeGroupVolume(@NonNull AudioManager audioManager,
                                                     int amGroupId) {
        Objects.requireNonNull(audioManager, "Audio manager can not be null");
        return audioManager.getLastAudibleVolumeForVolumeGroup(amGroupId);
    }

    /**
     * Checks if the given {@link android.media.AudioVolumeGroup} is muted or not.
     * <p>See {@link AudioManager#isVolumeGroupMuted} for details
     *
     * @param audioManager {@link AudioManager} instance to be used for the request
     * @param amGroupId id of the {@link android.media.AudioVolumeGroup} to consider
     * @return true if the {@link android.media.AudioVolumeGroup} referred by its id is found and
     * muted, false otherwise.
     */
    public static boolean isVolumeGroupMuted(@NonNull AudioManager audioManager, int amGroupId) {
        Objects.requireNonNull(audioManager, "Audio manager can not be null");
        return audioManager.isVolumeGroupMuted(amGroupId);
    }

    /**
     * Adjusts the volume for the {@link android.media.AudioVolumeGroup} id if found. No-operation
     * otherwise.
     * <p>See {@link AudioManager#adjustVolumeGroupVolume} for details
     *
     * @param audioManager audio manager to use for managing the volume group
     * @param amGroupId id of the {@link android.media.AudioVolumeGroup} to consider
     * @param direction direction to adjust the volume, one of {@link AudioManager#VolumeAdjustment}
     * @param flags one ore more flags of {@link AudioManager#Flags}
     */
    public static void adjustVolumeGroupVolume(@NonNull AudioManager audioManager,
            int amGroupId, int direction, @AudioManager.Flags int flags) {
        Objects.requireNonNull(audioManager, "Audio manager can not be null");
        audioManager.adjustVolumeGroupVolume(amGroupId, direction, flags);
    }
}
