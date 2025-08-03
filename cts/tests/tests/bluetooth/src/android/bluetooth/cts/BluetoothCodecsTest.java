/*
 * Copyright 2022 The Android Open Source Project
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

package android.bluetooth.cts;

import android.app.UiAutomation;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.test.AndroidTestCase;

import androidx.test.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BluetoothCodecsTest extends AndroidTestCase {
    private static final String TAG = BluetoothCodecsTest.class.getSimpleName();

    // Codec configs: A and B are same; C is different
    private static final BluetoothCodecConfig config_A =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig config_B =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig config_C =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                                 1000, 2000, 3000, 4000);

    // Local capabilities: A and B are same; C is different
    private static final BluetoothCodecConfig local_capability1_A =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100 |
                                 BluetoothCodecConfig.SAMPLE_RATE_48000,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO |
                                 BluetoothCodecConfig.CHANNEL_MODE_MONO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig local_capability1_B =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100 |
                                 BluetoothCodecConfig.SAMPLE_RATE_48000,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO |
                                 BluetoothCodecConfig.CHANNEL_MODE_MONO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig local_capability1_C =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100 |
                                 BluetoothCodecConfig.SAMPLE_RATE_48000,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig local_capability2_A =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100 |
                                 BluetoothCodecConfig.SAMPLE_RATE_48000,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO |
                                 BluetoothCodecConfig.CHANNEL_MODE_MONO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig local_capability2_B =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100 |
                                 BluetoothCodecConfig.SAMPLE_RATE_48000,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO |
                                 BluetoothCodecConfig.CHANNEL_MODE_MONO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig local_capability2_C =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100 |
                                 BluetoothCodecConfig.SAMPLE_RATE_48000,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                                 1000, 2000, 3000, 4000);

    // Selectable capabilities: A and B are same; C is different
    private static final BluetoothCodecConfig selectable_capability1_A =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO |
                                 BluetoothCodecConfig.CHANNEL_MODE_MONO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig selectable_capability1_B =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO |
                                 BluetoothCodecConfig.CHANNEL_MODE_MONO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig selectable_capability1_C =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig selectable_capability2_A =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO |
                                 BluetoothCodecConfig.CHANNEL_MODE_MONO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig selectable_capability2_B =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO |
                                 BluetoothCodecConfig.CHANNEL_MODE_MONO,
                                 1000, 2000, 3000, 4000);

    private static final BluetoothCodecConfig selectable_capability2_C =
            buildBluetoothCodecConfig(BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                                 BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                                 BluetoothCodecConfig.SAMPLE_RATE_44100,
                                 BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                                 BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                                 1000, 2000, 3000, 4000);

    private static final List<BluetoothCodecConfig> LOCAL_CAPABILITY_A = List.of(
            local_capability1_A,
            local_capability2_A);

    private static final List<BluetoothCodecConfig> LOCAL_CAPABILITY_B = List.of(
            local_capability1_B,
            local_capability2_B);

    private static final List<BluetoothCodecConfig> LOCAL_CAPABILITY_C = List.of(
            local_capability1_C,
            local_capability2_C);

    private static final List<BluetoothCodecConfig> SELECTABLE_CAPABILITY_A = List.of(
            selectable_capability1_A,
            selectable_capability2_A);

    private static final List<BluetoothCodecConfig> SELECTABLE_CAPABILITY_B = List.of(
            selectable_capability1_B,
            selectable_capability2_B);

    private static final List<BluetoothCodecConfig> SELECTABLE_CAPABILITY_C = List.of(
            selectable_capability1_C,
            selectable_capability2_C);

    private static final BluetoothCodecStatus bcs_A =
            new BluetoothCodecStatus.Builder()
                    .setCodecConfig(config_A)
                    .setCodecsLocalCapabilities(LOCAL_CAPABILITY_A)
                    .setCodecsSelectableCapabilities(SELECTABLE_CAPABILITY_A)
                    .build();
    private static final BluetoothCodecStatus bcs_B =
            new BluetoothCodecStatus.Builder()
                    .setCodecConfig(config_B)
                    .setCodecsLocalCapabilities(LOCAL_CAPABILITY_B)
                    .setCodecsSelectableCapabilities(SELECTABLE_CAPABILITY_B)
                    .build();
    private static final BluetoothCodecStatus bcs_C =
            new BluetoothCodecStatus.Builder()
                    .setCodecConfig(config_C)
                    .setCodecsLocalCapabilities(LOCAL_CAPABILITY_C)
                    .setCodecsSelectableCapabilities(SELECTABLE_CAPABILITY_C)
                    .build();

    public void test_BluetoothCodecStatusBuilder() {
        BluetoothCodecStatus builderConfig = new BluetoothCodecStatus.Builder()
                .setCodecConfig(config_A)
                .setCodecsLocalCapabilities(LOCAL_CAPABILITY_B)
                .setCodecsSelectableCapabilities(SELECTABLE_CAPABILITY_C)
                .build();

        assertTrue(Objects.equals(builderConfig.getCodecConfig(), config_A));
        assertTrue(Objects.equals(builderConfig.getCodecsLocalCapabilities(),
                LOCAL_CAPABILITY_B));
        assertTrue(Objects.equals(builderConfig.getCodecsSelectableCapabilities(),
                SELECTABLE_CAPABILITY_C));
    }

    public void test_BluetoothCodecConfigBuilder() {
        BluetoothCodecConfig builderConfig = new BluetoothCodecConfig.Builder()
                .setCodecType(config_A.getCodecType())
                .setCodecPriority(config_A.getCodecPriority())
                .setSampleRate(config_A.getSampleRate())
                .setBitsPerSample(config_A.getBitsPerSample())
                .setChannelMode(config_A.getChannelMode())
                .setCodecSpecific1(config_A.getCodecSpecific1())
                .setCodecSpecific2(config_A.getCodecSpecific2())
                .setCodecSpecific3(config_A.getCodecSpecific3())
                .setCodecSpecific4(config_A.getCodecSpecific4())
                .build();

        assertTrue(Objects.equals(builderConfig, config_A));
        assertTrue(builderConfig.isMandatoryCodec());
    }

    public void test_GetCodecConfig() {
        assertTrue(Objects.equals(bcs_A.getCodecConfig(), config_A));
        assertTrue(Objects.equals(bcs_A.getCodecConfig(), config_B));
        assertFalse(Objects.equals(bcs_A.getCodecConfig(), config_C));
    }

    public void test_CodecsCapabilities() {
        assertTrue(bcs_A.getCodecsLocalCapabilities().equals(LOCAL_CAPABILITY_A));
        assertTrue(bcs_A.getCodecsLocalCapabilities().equals(LOCAL_CAPABILITY_B));
        assertFalse(bcs_A.getCodecsLocalCapabilities().equals(LOCAL_CAPABILITY_C));

        assertTrue(bcs_A.getCodecsSelectableCapabilities()
                                 .equals(SELECTABLE_CAPABILITY_A));
        assertTrue(bcs_A.getCodecsSelectableCapabilities()
                                  .equals(SELECTABLE_CAPABILITY_B));
        assertFalse(bcs_A.getCodecsSelectableCapabilities()
                                  .equals(SELECTABLE_CAPABILITY_C));
    }

    public void test_IsCodecConfigSelectable() {
        assertFalse(bcs_A.isCodecConfigSelectable(null));
        assertTrue(bcs_A.isCodecConfigSelectable(selectable_capability1_C));
        assertTrue(bcs_A.isCodecConfigSelectable(selectable_capability2_C));

        // Not selectable due to multiple channel modes
        assertFalse(bcs_A.isCodecConfigSelectable(selectable_capability1_A));
        assertFalse(bcs_A.isCodecConfigSelectable(selectable_capability1_B));
        assertFalse(bcs_A.isCodecConfigSelectable(selectable_capability2_A));
        assertFalse(bcs_A.isCodecConfigSelectable(selectable_capability2_B));
    }

    private static BluetoothCodecConfig buildBluetoothCodecConfig(int sourceCodecType,
            int codecPriority, int sampleRate, int bitsPerSample, int channelMode,
            long codecSpecific1, long codecSpecific2, long codecSpecific3, long codecSpecific4) {
        return new BluetoothCodecConfig.Builder()
                    .setCodecType(sourceCodecType)
                    .setCodecPriority(codecPriority)
                    .setSampleRate(sampleRate)
                    .setBitsPerSample(bitsPerSample)
                    .setChannelMode(channelMode)
                    .setCodecSpecific1(codecSpecific1)
                    .setCodecSpecific2(codecSpecific2)
                    .setCodecSpecific3(codecSpecific3)
                    .setCodecSpecific4(codecSpecific4)
                    .build();
    }
}
