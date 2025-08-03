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

package com.android.media.audiotestharness.server.javasound;

import com.android.media.audiotestharness.proto.AudioDeviceOuterClass.AudioDevice;
import com.android.media.audiotestharness.proto.AudioFormatOuterClass.AudioFormat;
import com.android.media.audiotestharness.server.core.AudioCapturer;
import com.android.media.audiotestharness.server.core.AudioSystemService;

import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

public class JavaAudioSystemService implements AudioSystemService {

    private static final Logger LOGGER = Logger.getLogger(JavaAudioSystemService.class.getName());

    private final JavaAudioSystem mAudioSystem;

    private final JavaAudioCapturerFactory mAudioCapturerFactory;

    /**
     * Cache of {@link com.android.media.audiotestharness.proto.AudioDeviceOuterClass.AudioDevice}
     * that were provided by the {@link #getDevices()} method to the {@link Mixer.Info} they
     * correspond to.
     *
     * <p>This cache is reset upon ever call to the {@link #getDevices()} method, and is used
     * primarily to simplify the {@link #createCapturerFor(AudioDevice, AudioFormat)} method
     * significantly.
     */
    private final Map<AudioDevice, Mixer.Info> mDeviceMixerMap;

    @Inject
    public JavaAudioSystemService(
            JavaAudioSystem javaAudioSystem, JavaAudioCapturerFactory javaAudioCapturerFactory) {
        LOGGER.finest("new JavaAudioSystemService");

        mAudioSystem = javaAudioSystem;
        mAudioCapturerFactory = javaAudioCapturerFactory;

        mDeviceMixerMap = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Makes use of the Java Sound API to get currently configured devices. Devices are marked as
     * having the {@link AudioDevice.Capability#CAPTURE} capability if they have a {@link
     * javax.sound.sampled.TargetDataLine} available, meaning that audio data can be read from them.
     * Currently only sets the {@link AudioDevice}'s name to the name provided by the {@link
     * Mixer.Info} corresponding to the device.
     */
    @Override
    public ImmutableSet<AudioDevice> getDevices() {
        rebuildMixerCache();
        return ImmutableSet.copyOf(mDeviceMixerMap.keySet());
    }

    @Override
    public AudioCapturer createCapturerFor(AudioDevice device, AudioFormat audioFormat)
            throws IOException {
        LOGGER.info(
                String.format(
                        "Creating new Capturer for Device (%s) using Format (%s)",
                        device, audioFormat));

        // Build the cache so direct lookup is easy if the device is exact.
        if (mDeviceMixerMap.isEmpty()) {
            rebuildMixerCache();
        }

        Mixer.Info mixerInfo;
        mixerInfo = mDeviceMixerMap.get(device);

        // If there is no exact match for the provided device, attempt to find the nearest match.
        // By "nearest match" we mean a mixer that matches all of the fields in the provided mixer.
        // In this case, we only look for partial matches for the "name" field.
        if (mixerInfo == null) {
            mixerInfo = findClosestMatchingMixer(device, /* exactName= */ false);
        }

        if (mixerInfo == null) {
            throw new IOException("Unable to find mixer matching provided device");
        }

        Mixer mixer = mAudioSystem.getMixer(mixerInfo);

        TargetDataLine targetDataLine;
        try {
            targetDataLine =
                    (TargetDataLine)
                            mixer.getLine(
                                    new DataLine.Info(
                                            TargetDataLine.class,
                                            JavaSoundUtility.audioFormatFrom(audioFormat)));
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new IOException(
                    "Unable to build player for specified device and audio format", e);
        }

        try {
            targetDataLine.open(JavaSoundUtility.audioFormatFrom(audioFormat));
        } catch (LineUnavailableException lue) {
            throw new IOException(
                    "Failed to reserve audio system resources for specified device and audio"
                            + " format",
                    lue);
        }

        return mAudioCapturerFactory.build(device, audioFormat, targetDataLine);
    }

    /**
     * Rebuilds the {@link #mDeviceMixerMap} so that {@link AudioDevice} objects are properly mapped
     * to their corresponding {@link Mixer.Info}
     */
    private void rebuildMixerCache() {
        LOGGER.finest("Rebuildling Mixer Cache...");
        mDeviceMixerMap.clear();
        for (Mixer.Info mixerInfo : mAudioSystem.getMixerInfo()) {
            AudioDevice.Builder audioDeviceBuilder = AudioDevice.newBuilder();
            Mixer mixer = mAudioSystem.getMixer(mixerInfo);

            audioDeviceBuilder.setName(mixer.getMixerInfo().getName());

            if (mixer.getTargetLineInfo().length > 0) {
                audioDeviceBuilder.addCapabilities(AudioDevice.Capability.CAPTURE);
            }

            AudioDevice device = audioDeviceBuilder.build();

            mDeviceMixerMap.put(device, mixerInfo);
        }
        LOGGER.finest(
                String.format(
                        "Successfully Rebuilt Mixer Cache, found %d Mixers",
                        mDeviceMixerMap.size()));
    }

    /**
     * Finds the closest matching {@link Mixer.Info} corresponding to a given {@link AudioDevice} or
     * null if none can be found that matches.
     *
     * @param audioDevice the device to search for.
     * @param exactName if true, then an exact match is required for the name of the audio device,
     *     if false, then a partial match to a mixer name is sufficient.
     */
    private Mixer.Info findClosestMatchingMixer(AudioDevice audioDevice, boolean exactName) {
        LOGGER.finest(
                String.format("Searching Mixer Results for closest match to: %s", audioDevice));
        Optional<Mixer.Info> firstMatch =
                Arrays.stream(mAudioSystem.getMixerInfo())

                        // Filter by AudioDevice name either exactly or partially.
                        .filter(
                                (info) -> {
                                    if (audioDevice.getName() != null
                                            && !audioDevice.getName().isEmpty()) {
                                        if (exactName) {
                                            return info.getName().equals(audioDevice.getName());
                                        } else {
                                            return info.getName().contains(audioDevice.getName());
                                        }
                                    }

                                    return true;
                                })

                        // Filter by if the device should support capture or not.
                        .filter(
                                (info) -> {
                                    if (audioDevice
                                            .getCapabilitiesList()
                                            .contains(AudioDevice.Capability.CAPTURE)) {
                                        return mAudioSystem
                                                        .getMixer(info)
                                                        .getTargetLineInfo()
                                                        .length
                                                > 0;
                                    }

                                    return true;
                                })
                        .findFirst();

        LOGGER.finest(
                firstMatch.isPresent()
                        ? String.format("Found match: %s", firstMatch.get())
                        : "Did not find match");
        return firstMatch.orElse(null);
    }
}
