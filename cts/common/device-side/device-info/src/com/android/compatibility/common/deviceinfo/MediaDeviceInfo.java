/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.AudioCapabilities;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Range;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.DeviceInfoStore;

import java.util.Arrays;

/**
 * Media information collector.
 */
public final class MediaDeviceInfo extends DeviceInfo {

    @Override
    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        MediaCodecList allCodecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
        store.startArray("media_codec_info");
        for (MediaCodecInfo info : allCodecs.getCodecInfos()) {

            store.startGroup();
            store.addResult("name", info.getName());
            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.Q)) {
                store.addResult("canonical", info.getCanonicalName());
            }
            store.addResult("encoder", info.isEncoder());
            if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.Q)) {
                store.addResult("alias", info.isAlias());
                store.addResult("software", info.isSoftwareOnly());
                store.addResult("hardware", info.isHardwareAccelerated());
                store.addResult("vendor", info.isVendor());
            }

            store.startArray("supported_type");
            for (String type : info.getSupportedTypes()) {
                store.startGroup();
                store.addResult("type", type);
                CodecCapabilities codecCapabilities = info.getCapabilitiesForType(type);
                if (codecCapabilities.profileLevels.length > 0) {
                    store.startArray("codec_profile_level");
                    for (CodecProfileLevel profileLevel : codecCapabilities.profileLevels) {
                        store.startGroup();
                        store.addResult("level", profileLevel.level);
                        store.addResult("profile", profileLevel.profile);
                        store.endGroup();
                    }
                    store.endArray(); // codec_profile_level
                }
                if (codecCapabilities.colorFormats.length > 0) {
                    store.addArrayResult("codec_color_format", codecCapabilities.colorFormats);
                }
                store.addResult("supported_secure_playback", codecCapabilities.isFeatureSupported(
                        CodecCapabilities.FEATURE_SecurePlayback));
                store.addResult("supported_hdr_editing", codecCapabilities.isFeatureSupported(
                        CodecCapabilities.FEATURE_HdrEditing));
                VideoCapabilities videoCapabilities = codecCapabilities.getVideoCapabilities();
                if (videoCapabilities != null) {
                    store.startGroup("supported_resolutions");
                    store.addResult(
                            "supported_360p_30fps",
                            videoCapabilities.areSizeAndRateSupported(640, 360, 30));
                    store.addResult(
                            "supported_480p_30fps",
                            videoCapabilities.areSizeAndRateSupported(720, 480, 30));
                    store.addResult(
                            "supported_720p_30fps",
                            videoCapabilities.areSizeAndRateSupported(1280, 720, 30));
                    store.addResult(
                            "supported_1080p_30fps",
                            videoCapabilities.areSizeAndRateSupported(1920, 1080, 30));
                    // The QHD/WQHD 2560x1440 resolution is used to create YouTube and PlayMovies
                    // 2k content, so use that resolution to determine if a device supports 2k.
                    store.addResult(
                            "supported_2k_30fps",
                            videoCapabilities.areSizeAndRateSupported(2560, 1440, 30));
                    store.addResult(
                            "supported_4k_30fps",
                            videoCapabilities.areSizeAndRateSupported(3840, 2160, 30));
                    store.addResult(
                            "supported_8k_30fps",
                            videoCapabilities.areSizeAndRateSupported(7680, 4320, 30));
                    store.addResult(
                            "supported_360p_60fps",
                            videoCapabilities.areSizeAndRateSupported(640, 360, 60));
                    store.addResult(
                            "supported_480p_60fps",
                            videoCapabilities.areSizeAndRateSupported(720, 480, 60));
                    store.addResult(
                            "supported_720p_60fps",
                            videoCapabilities.areSizeAndRateSupported(1280, 720, 60));
                    store.addResult(
                            "supported_1080p_60fps",
                            videoCapabilities.areSizeAndRateSupported(1920, 1080, 60));
                    store.addResult(
                            "supported_2k_60fps",
                            videoCapabilities.areSizeAndRateSupported(2560, 1440, 60));
                    store.addResult(
                            "supported_4k_60fps",
                            videoCapabilities.areSizeAndRateSupported(3840, 2160, 60));
                    store.addResult(
                            "supported_8k_60fps",
                            videoCapabilities.areSizeAndRateSupported(7680, 4320, 60));
                    store.endGroup(); // supported_resolutions
                    store.addResult("width_alignment", videoCapabilities.getWidthAlignment());
                    store.addResult("height_alignment", videoCapabilities.getHeightAlignment());
                    // get min & max resolution
                    Range<Integer> widthRange = videoCapabilities.getSupportedWidths();
                    int minWidth = widthRange.getLower();
                    int minPixelCount = minWidth
                            * videoCapabilities.getSupportedHeightsFor(minWidth).getLower();
                    int maxWidth = widthRange.getUpper();
                    int maxPixelCount = maxWidth
                            * videoCapabilities.getSupportedHeightsFor(maxWidth).getUpper();
                    store.addResult("min_pixel_count", minPixelCount);
                    store.addResult("max_pixel_count", maxPixelCount);
                }
                AudioCapabilities audioCapabilities = codecCapabilities.getAudioCapabilities();
                if (audioCapabilities != null) {
                    if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.Q)) {
                        int minSampleRate = -1, maxSampleRate = -1;
                        int[] discreteSampleRates = audioCapabilities.getSupportedSampleRates();
                        if (discreteSampleRates != null) {  // codec supports only discrete sample rates
                            minSampleRate = Arrays.stream(discreteSampleRates).min().getAsInt();
                            maxSampleRate = Arrays.stream(discreteSampleRates).max().getAsInt();
                        } else {  // codec supports continuous sample rates
                            Range<Integer>[] sampleRateRanges =
                                             audioCapabilities.getSupportedSampleRateRanges();
                            minSampleRate = sampleRateRanges[0].getLower();
                            maxSampleRate = sampleRateRanges[sampleRateRanges.length - 1].getUpper();
                        }
                        store.addResult("min_sample_rate", minSampleRate);
                        store.addResult("max_sample_rate", maxSampleRate);
                    }

                    store.addResult("max_channel_count",
                                audioCapabilities.getMaxInputChannelCount());
                    if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S)) {
                        store.addResult("min_channel_count",
                                    audioCapabilities.getMinInputChannelCount());
                    }
                }
                store.endGroup();
            }
            store.endArray();
            store.endGroup();
        }

        store.endArray(); // media_codec_profile
    }
}
