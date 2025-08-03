/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.media.FadeManagerConfiguration.FADE_STATE_DISABLED;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.car.oem.CarAudioFadeConfiguration;
import android.media.AudioAttributes;
import android.media.FadeManagerConfiguration;
import android.media.VolumeShaper;
import android.util.proto.ProtoOutputStream;

import com.android.car.audio.CarAudioDumpProto.CarAudioAttributesProto;
import com.android.car.audio.CarAudioDumpProto.CarAudioFadeConfigurationProto;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import java.util.List;

/**
 * Utils for common proto dump functionalities
 */
@ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
/* package */ final class CarAudioProtoUtils {

    private CarAudioProtoUtils() {
    }

    static void dumpCarAudioAttributesListProto(@Nullable List<AudioAttributes> attributesList,
            long fieldId, ProtoOutputStream proto) {
        if (attributesList == null) {
            return;
        }

        for (int index = 0; index < attributesList.size(); index++) {
            dumpCarAudioAttributesProto(attributesList.get(index), fieldId, proto);
        }
    }

    static void dumpCarAudioAttributesProto(@Nullable AudioAttributes attributes, long fieldId,
            ProtoOutputStream proto) {
        if (attributes == null) {
            return;
        }

        long token = proto.start(fieldId);
        proto.write(CarAudioAttributesProto.USAGE, attributes.getUsage());
        proto.write(CarAudioAttributesProto.CONTENT_TYPE, attributes.getContentType());
        proto.end(token);
    }

    static void dumpCarAudioFadeConfigurationProto(
            @Nullable CarAudioFadeConfiguration carAudioFadeConfiguration,
            long fieldId, ProtoOutputStream proto) {
        if (carAudioFadeConfiguration == null) {
            return;
        }

        long token = proto.start(fieldId);
        proto.write(CarAudioFadeConfigurationProto.NAME, carAudioFadeConfiguration.getName());
        dumpFadeManagerConfigurationProto(carAudioFadeConfiguration.getFadeManagerConfiguration(),
                CarAudioFadeConfigurationProto.FADE_MANAGER_CONFIGURATION, proto);
        proto.end(token);
    }

    private static void dumpFadeManagerConfigurationProto(
            @Nullable FadeManagerConfiguration fadeManagerConfig, long fieldId,
            ProtoOutputStream proto) {
        if (fadeManagerConfig == null) {
            return;
        }

        long token = proto.start(fieldId);
        int fadeState = fadeManagerConfig.getFadeState();
        proto.write(CarAudioFadeConfigurationProto.FadeManagerConfigurationProto.STATE,
                fadeManagerConfig.getFadeState());
        if (fadeState == FADE_STATE_DISABLED) {
            proto.end(token);
            return;
        }

        List<Integer> fadeableUsages = fadeManagerConfig.getFadeableUsages();

        dumpIntegerListForRepeatedProto(fadeableUsages,
                CarAudioFadeConfigurationProto.FadeManagerConfigurationProto.FADEABLE_USAGES,
                proto);
        dumpIntegerListForRepeatedProto(fadeManagerConfig.getUnfadeableContentTypes(),
                CarAudioFadeConfigurationProto.FadeManagerConfigurationProto
                        .UNFADEABLE_CONTENT_TYPES, proto);
        dumpIntegerListForRepeatedProto(fadeManagerConfig.getUnfadeableUids(),
                CarAudioFadeConfigurationProto.FadeManagerConfigurationProto.UNFADEABLE_UIDS,
                proto);
        dumpCarAudioAttributesListProto(fadeManagerConfig.getUnfadeableAudioAttributes(),
                CarAudioFadeConfigurationProto.FadeManagerConfigurationProto.UNFADEABLE_ATTRIBUTES,
                proto);

        for (int index = 0; index < fadeableUsages.size(); index++) {
            int usage = fadeableUsages.get(index);
            dumpUsageToVolumeShaperConfigProto(usage,
                    fadeManagerConfig.getFadeOutVolumeShaperConfigForUsage(usage),
                    CarAudioFadeConfigurationProto.FadeManagerConfigurationProto
                            .USAGES_TO_VOLUME_SHAPER_CONFIG,
                    CarAudioFadeConfigurationProto.FadeManagerConfigurationProto
                            .UsagesToVolumeShaperConfiguration.FADE_OUT_VOL_SHAPER_CONFIG, proto);
            dumpUsageToVolumeShaperConfigProto(usage,
                    fadeManagerConfig.getFadeInVolumeShaperConfigForUsage(usage),
                    CarAudioFadeConfigurationProto.FadeManagerConfigurationProto
                            .USAGES_TO_VOLUME_SHAPER_CONFIG,
                    CarAudioFadeConfigurationProto.FadeManagerConfigurationProto
                            .UsagesToVolumeShaperConfiguration.FADE_IN_VOL_SHAPER_CONFIG, proto);
        }

        List<AudioAttributes> attrsWithVolumeShapers = fadeManagerConfig
                .getAudioAttributesWithVolumeShaperConfigs();
        for (int index = 0; index < attrsWithVolumeShapers.size(); index++) {
            AudioAttributes attr = attrsWithVolumeShapers.get(index);
            dumpAttributeToVolumeShaperConfigProto(attr,
                    fadeManagerConfig.getFadeOutVolumeShaperConfigForAudioAttributes(attr),
                    CarAudioFadeConfigurationProto.FadeManagerConfigurationProto
                            .ATTR_TO_VOLUME_SHAPER_CONFIG,
                    CarAudioFadeConfigurationProto.FadeManagerConfigurationProto
                            .AttributeToVolumeShaperConfiguration.FADE_OUT_VOL_SHAPER_CONFIG,
                    proto);
            dumpAttributeToVolumeShaperConfigProto(attr,
                    fadeManagerConfig.getFadeInVolumeShaperConfigForAudioAttributes(attr),
                    CarAudioFadeConfigurationProto.FadeManagerConfigurationProto
                            .ATTR_TO_VOLUME_SHAPER_CONFIG,
                    CarAudioFadeConfigurationProto.FadeManagerConfigurationProto
                            .AttributeToVolumeShaperConfiguration.FADE_IN_VOL_SHAPER_CONFIG, proto);
        }
        proto.end(token);
    }

    private static void dumpIntegerListForRepeatedProto(List<Integer> integerList,
            long repeatedFieldId, ProtoOutputStream proto) {
        for (int index = 0; index < integerList.size(); index++) {
            proto.write(repeatedFieldId, integerList.get(index));
        }
    }

    private static void dumpUsageToVolumeShaperConfigProto(int usage,
            @Nullable VolumeShaper.Configuration volShaperConfig, long fieldId,
            long fadeTypeFieldId, ProtoOutputStream proto) {
        if (volShaperConfig == null) {
            return;
        }

        long token = proto.start(fieldId);
        proto.write(CarAudioFadeConfigurationProto.FadeManagerConfigurationProto
                .UsagesToVolumeShaperConfiguration.USAGE, usage);
        dumpVolumeShaperConfiguration(volShaperConfig, fadeTypeFieldId, proto);
        proto.end(token);
    }

    private static void dumpAttributeToVolumeShaperConfigProto(@Nullable AudioAttributes attr,
            @Nullable VolumeShaper.Configuration volShaperConfig, long fieldId,
            long fadeTypeFieldId, ProtoOutputStream proto) {
        if (volShaperConfig == null || attr == null) {
            return;
        }

        long token = proto.start(fieldId);
        dumpCarAudioAttributesProto(attr, CarAudioFadeConfigurationProto
                .FadeManagerConfigurationProto.AttributeToVolumeShaperConfiguration.ATTRIBUTES,
                proto);
        dumpVolumeShaperConfiguration(volShaperConfig, fadeTypeFieldId, proto);
        proto.end(token);
    }

    private static void dumpVolumeShaperConfiguration(VolumeShaper.Configuration volShaperConfig,
            long fieldId, ProtoOutputStream proto) {
        long token = proto.start(fieldId);
        proto.write(CarAudioFadeConfigurationProto.FadeManagerConfigurationProto
                .VolumeShaperConfiguration.DURATION, volShaperConfig.getDuration());
        proto.end(token);
    }
}
