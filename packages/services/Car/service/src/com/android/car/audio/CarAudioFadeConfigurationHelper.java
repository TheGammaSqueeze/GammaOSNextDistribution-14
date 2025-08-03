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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.oem.CarAudioFadeConfiguration;
import android.media.AudioAttributes;
import android.media.FadeManagerConfiguration;
import android.util.ArrayMap;
import android.util.Xml;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.IntArray;
import com.android.internal.util.Preconditions;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Set;

/**
 * A class to load {@link android.media.FadeManagerConfiguration} from the configuration XML file.
 */
final class CarAudioFadeConfigurationHelper {
    private static final String TAG = CarLog.tagFor(CarAudioFadeConfigurationHelper.class);

    private static final String NAMESPACE = null;
    private static final String TAG_ROOT = "carAudioFadeConfiguration";
    private static final String TAG_CONFIGS = "configs";
    private static final String TAG_CONFIG = "config";
    private static final String CONFIG_NAME = "name";
    private static final String CONFIG_DEFAULT_FADE_OUT_DURATION = "defaultFadeOutDurationInMillis";
    private static final String CONFIG_DEFAULT_FADE_IN_DURATION = "defaultFadeInDurationInMillis";
    private static final String CONFIG_FADE_STATE = "fadeState";
    private static final String CONFIG_FADEABLE_USAGES = "fadeableUsages";
    private static final String CONFIG_UNFADEABLE_CONTENT_TYPES = "unfadeableContentTypes";
    private static final String CONFIG_UNFADEABLE_AUDIO_ATTRIBUTES = "unfadeableAudioAttributes";
    private static final String CONFIG_FADE_OUT_CONFIGURATIONS = "fadeOutConfigurations";
    private static final String CONFIG_FADE_IN_CONFIGURATIONS = "fadeInConfigurations";
    private static final String CONFIG_FADE_CONFIGURATION = "fadeConfiguration";
    private static final String FADE_DURATION = "fadeDurationMillis";
    private static final String FADE_STATE_VALUE = "value";
    private static final String VERSION = "version";
    private static final int INVALID = -1;
    private static final int SUPPORTED_VERSION_1 = 1;

    private static final IntArray SUPPORTED_VERSIONS = IntArray.wrap(new int[] {
            SUPPORTED_VERSION_1,
    });

    private final ArrayMap<String, CarAudioFadeConfiguration> mNameToCarAudioFadeConfigurationMap =
            new ArrayMap<>();

    CarAudioFadeConfigurationHelper(@NonNull InputStream stream)
            throws XmlPullParserException, IOException {
        Objects.requireNonNull(stream, "Car audio fade config input stream can not be null");
        parseFadeManagerConfigFile(stream);
    }

    @Nullable
    public CarAudioFadeConfiguration getCarAudioFadeConfiguration(String configName) {
        if (!isConfigAvailable(configName)) {
            return null;
        }

        return mNameToCarAudioFadeConfigurationMap.get(configName);
    }

    public boolean isConfigAvailable(String configName) {
        return mNameToCarAudioFadeConfigurationMap.containsKey(configName);
    }

    @NonNull
    public Set<String> getAllConfigNames() {
        return mNameToCarAudioFadeConfigurationMap.keySet();
    }

    private void parseFadeManagerConfigFile(InputStream stream)
            throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, NAMESPACE != null);
        parser.setInput(stream, null);

        // Ensure <carAudioFadeConfiguration> is the root
        parser.nextTag();
        parser.require(XmlPullParser.START_TAG, NAMESPACE, TAG_ROOT);

        // Version check
        int versionNumber = Integer.parseInt(parser.getAttributeValue(NAMESPACE, VERSION));
        if (SUPPORTED_VERSIONS.indexOf(versionNumber) == INVALID) {
            throw new IllegalArgumentException("Latest Supported version:"
                    + SUPPORTED_VERSION_1 + " , got version:" + versionNumber);
        }

        // Get all fade configs configured under <configs> tag
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), TAG_CONFIGS)) {
                parseFadeConfigs(parser);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }

        if (mNameToCarAudioFadeConfigurationMap.isEmpty()) {
            throw new MissingResourceException(TAG_CONFIGS + " is missing from configuration ",
                    TAG_ROOT, TAG_CONFIGS);
        }
    }

    private void parseFadeConfigs(XmlPullParser parser) throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), TAG_CONFIG)) {
                CarAudioFadeConfiguration afc = parseFadeConfig(parser);
                mNameToCarAudioFadeConfigurationMap.put(afc.getName(), afc);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
    }

    private CarAudioFadeConfiguration parseFadeConfig(XmlPullParser parser)
            throws XmlPullParserException, IOException {

        String configName = parser.getAttributeValue(NAMESPACE, CONFIG_NAME);
        Objects.requireNonNull(configName, "Config name can not be null");
        Preconditions.checkArgument(!configName.isEmpty(), "Config name can not be empty");

        String defaultFadeOutDurationLiteral = parser.getAttributeValue(NAMESPACE,
                CONFIG_DEFAULT_FADE_OUT_DURATION);
        String defaultFadeInDurationLiteral = parser.getAttributeValue(NAMESPACE,
                CONFIG_DEFAULT_FADE_IN_DURATION);

        FadeManagerConfiguration.Builder builder;
        if (defaultFadeOutDurationLiteral != null && defaultFadeInDurationLiteral != null) {
            builder = new FadeManagerConfiguration.Builder(
                    CarAudioParserUtils.parsePositiveLongAttribute(CONFIG_DEFAULT_FADE_OUT_DURATION,
                            defaultFadeOutDurationLiteral),
                    CarAudioParserUtils.parsePositiveLongAttribute(CONFIG_DEFAULT_FADE_IN_DURATION,
                            defaultFadeInDurationLiteral));
        } else {
            builder = new FadeManagerConfiguration.Builder();
        }

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), CONFIG_FADE_STATE)) {
                parseFadeState(parser, builder);
            } else if (Objects.equals(parser.getName(), CONFIG_FADEABLE_USAGES)) {
                parseFadeableUsages(parser, builder);
            } else if (Objects.equals(parser.getName(), CONFIG_UNFADEABLE_CONTENT_TYPES)) {
                parseUnfadeableContentTypes(parser, builder);
            } else if (Objects.equals(parser.getName(), CONFIG_UNFADEABLE_AUDIO_ATTRIBUTES)) {
                parseUnfadeableAudioAttributes(parser, builder, configName);
            } else if (Objects.equals(parser.getName(), CONFIG_FADE_OUT_CONFIGURATIONS)) {
                parseFadeOutConfigurations(parser, builder);
            } else if (Objects.equals(parser.getName(), CONFIG_FADE_IN_CONFIGURATIONS)) {
                parseFadeInConfigurations(parser, builder);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
        return new CarAudioFadeConfiguration.Builder(builder.build()).setName(configName).build();
    }

    private void parseFadeState(XmlPullParser parser, FadeManagerConfiguration.Builder builder)
            throws XmlPullParserException, IOException {
        String fadeStateLiteral = parser.getAttributeValue(NAMESPACE, FADE_STATE_VALUE);
        int fadeState = CarAudioParserUtils.parsePositiveIntAttribute(FADE_STATE_VALUE,
                fadeStateLiteral);
        builder.setFadeState(fadeState);
        CarAudioParserUtils.skip(parser);
    }

    private void parseFadeableUsages(XmlPullParser parser, FadeManagerConfiguration.Builder builder)
            throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), CarAudioParserUtils.TAG_USAGE)) {
                int usage = CarAudioParserUtils.parseUsageValue(parser,
                        CarAudioParserUtils.ATTR_USAGE_VALUE);
                builder.addFadeableUsage(usage);
            }
            CarAudioParserUtils.skip(parser);
        }
    }

    private void parseUnfadeableContentTypes(XmlPullParser parser,
            FadeManagerConfiguration.Builder builder) throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), CONFIG_UNFADEABLE_CONTENT_TYPES)) {
                builder.addUnfadeableContentType(CarAudioParserUtils.parseContentTypeValue(parser));
            }
            CarAudioParserUtils.skip(parser);
        }
    }

    private void parseUnfadeableAudioAttributes(XmlPullParser parser,
            FadeManagerConfiguration.Builder builder, String configName)
            throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), CarAudioParserUtils.TAG_AUDIO_ATTRIBUTES)) {
                builder.setUnfadeableAudioAttributes(
                        CarAudioParserUtils.parseAudioAttributes(parser, configName));
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
    }

    private void parseFadeOutConfigurations(XmlPullParser parser,
            FadeManagerConfiguration.Builder builder) throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), CONFIG_FADE_CONFIGURATION)) {
                parseFadeConfiguration(parser, builder, /* isFadeIn= */ false);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
    }

    private void parseFadeInConfigurations(XmlPullParser parser,
            FadeManagerConfiguration.Builder builder) throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), CONFIG_FADE_CONFIGURATION)) {
                parseFadeConfiguration(parser, builder, /* isFadeIn= */ true);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
    }

    private void parseFadeConfiguration(XmlPullParser parser,
            FadeManagerConfiguration.Builder builder, boolean isFadeIn)
            throws XmlPullParserException, IOException {
        long fadeDuration = CarAudioParserUtils.parsePositiveLongAttribute(
                FADE_DURATION,
                parser.getAttributeValue(NAMESPACE, FADE_DURATION));
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), CarAudioParserUtils.TAG_AUDIO_ATTRIBUTES)) {
                parseAudioAttributesForFade(parser, builder, fadeDuration, isFadeIn);
            } else {
                CarAudioParserUtils.skip(parser);
            }
        }
    }

    private void parseAudioAttributesForFade(XmlPullParser parser,
            FadeManagerConfiguration.Builder builder, long fadeDuration, boolean isFadeIn)
            throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if (Objects.equals(parser.getName(), CarAudioParserUtils.TAG_USAGE)) {
                parseFadeDurationForUsage(parser, builder, fadeDuration, isFadeIn);
            } else if (Objects.equals(parser.getName(), CarAudioParserUtils.TAG_AUDIO_ATTRIBUTE)) {
                parseFadeDurationForAudioAttributes(parser, builder, fadeDuration, isFadeIn);
            }
            CarAudioParserUtils.skip(parser);
        }
    }

    private void parseFadeDurationForUsage(XmlPullParser parser,
            FadeManagerConfiguration.Builder builder, long fadeDuration, boolean isFadeIn)
            throws XmlPullParserException, IOException {
        int usage = CarAudioParserUtils.parseUsageValue(parser,
                CarAudioParserUtils.ATTR_USAGE_VALUE);
        if (isFadeIn) {
            builder.setFadeInDurationForUsage(usage, fadeDuration);
        } else {
            builder.setFadeOutDurationForUsage(usage, fadeDuration);
        }
    }

    private void parseFadeDurationForAudioAttributes(XmlPullParser parser,
            FadeManagerConfiguration.Builder builder, long fadeDuration, boolean isFadeIn)
            throws XmlPullParserException, IOException {
        AudioAttributes attr = CarAudioParserUtils.parseAudioAttribute(parser,
                CONFIG_FADE_CONFIGURATION);
        if (isFadeIn) {
            builder.setFadeInDurationForAudioAttributes(attr, fadeDuration);
        } else {
            builder.setFadeOutDurationForAudioAttributes(attr, fadeDuration);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.increaseIndent();
        writer.printf("Available fade manager configurations: %d\n",
                mNameToCarAudioFadeConfigurationMap.size());
        for (int index = 0; index < mNameToCarAudioFadeConfigurationMap.size(); index++) {
            writer.printf((index + 1) + ". " + mNameToCarAudioFadeConfigurationMap.valueAt(index)
                    + "\n");
        }
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        if (mNameToCarAudioFadeConfigurationMap == null) {
            return;
        }

        for (int index = 0; index < mNameToCarAudioFadeConfigurationMap.size(); index++) {
            CarAudioProtoUtils.dumpCarAudioFadeConfigurationProto(
                    mNameToCarAudioFadeConfigurationMap.valueAt(index),
                    CarAudioDumpProto.AVAILABLE_CAR_FADE_CONFIGURATIONS, proto);
        }
    }
}
