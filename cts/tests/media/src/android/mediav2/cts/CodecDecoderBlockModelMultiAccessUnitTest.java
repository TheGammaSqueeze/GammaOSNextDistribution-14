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

package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_MultipleFrames;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderBlockModelMultiAccessUnitTestBase;
import android.mediav2.common.cts.CodecDecoderBlockModelTestBase;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;
import com.android.media.codec.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Tests audio decoders support for feature MultipleFrames in block model mode.
 * <p>
 * MultipleFrames feature is optional and is not required to support by all components. If a
 * component supports this feature, then multiple access units are grouped together (demarcated
 * with access unit offsets and timestamps) are sent as input to the component. The components
 * processes the input sent and returns output in a large enough buffer (demarcated with access
 * unit offsets and timestamps). The number of access units that can be grouped is dependent on
 * format keys, KEY_MAX_INPUT_SIZE, KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE.
 * <p>
 * The test runs the component in MultipleFrames block model mode and normal mode and expects same
 * output for a given input.
 **/
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RequiresFlagsEnabled(Flags.FLAG_LARGE_AUDIO_FRAME)
@RunWith(Parameterized.class)
public class CodecDecoderBlockModelMultiAccessUnitTest
        extends CodecDecoderBlockModelMultiAccessUnitTestBase {
    private static final String LOG_TAG =
            CodecDecoderBlockModelMultiAccessUnitTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final int[][] OUT_SIZE_IN_MS = {
            {1000, 250},  // max out size, threshold batch out size
            {1000, 100},
            {500, 20},
            {100, 100},
            {40, 100}
    };

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_1ch_16kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_2ch_44kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_1ch_8kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_1ch_16kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_1ch_22kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_1ch_24kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_1ch_32kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_1ch_44kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_1ch_48kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_2ch_8kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_2ch_16kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_2ch_22kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_2ch_24kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_2ch_32kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_2ch_44kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bbb_2ch_48kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/highres_1ch_96kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/highres_2ch_96kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/sd_2ch_48kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bellezza_2ch_48kHz_s32le.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/bellezza_2ch_48kHz_s24le.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/highres_1ch_176kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/highres_1ch_192kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/highres_2ch_176kHz.wav"},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "audio/highres_2ch_192kHz.wav"},

                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_1ch_8kHz_lame_cbr.mp3"},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_2ch_44kHz_lame_cbr.mp3"},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_2ch_44kHz_lame_vbr.mp3"},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_1ch_16kHz_lame_vbr.mp3"},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_2ch_44kHz_lame_crc.mp3"},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_stereo_48kHz_192kbps_mp3.mp3"},

                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "bbb_1ch_16kHz_16kbps_amrwb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "bbb_1ch_16kHz_23kbps_amrwb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "audio/bbb_mono_16kHz_6.6kbps_amrwb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "audio/bbb_mono_16kHz_8.85kbps_amrwb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "audio/bbb_mono_16kHz_12.65kbps_amrwb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "audio/bbb_mono_16kHz_14.25kbps_amrwb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "audio/bbb_mono_16kHz_15.85kbps_amrwb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "audio/bbb_mono_16kHz_18.25kbps_amrwb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "audio/bbb_mono_16kHz_19.85kbps_amrwb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "audio/bbb_mono_16kHz_23.05kbps_amrwb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "audio/bbb_mono_16kHz_23.85kbps_amrwb.3gp"},

                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "bbb_1ch_8kHz_10kbps_amrnb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "bbb_1ch_8kHz_8kbps_amrnb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "audio/bbb_mono_8kHz_12.2kbps_amrnb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "audio/bbb_mono_8kHz_10.2kbps_amrnb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "audio/bbb_mono_8kHz_7.95kbps_amrnb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "audio/bbb_mono_8kHz_7.40kbps_amrnb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "audio/bbb_mono_8kHz_6.70kbps_amrnb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "audio/bbb_mono_8kHz_5.90kbps_amrnb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "audio/bbb_mono_8kHz_5.15kbps_amrnb.3gp"},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "audio/bbb_mono_8kHz_4.75kbps_amrnb.3gp"},

                {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_1ch_16kHz_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_2ch_44kHz_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_1ch_8kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_1ch_12kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_1ch_16kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_1ch_22kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_1ch_24kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_1ch_32kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_1ch_44kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_1ch_48kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_2ch_8kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_2ch_12kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_2ch_16kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_2ch_22kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_2ch_24kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_2ch_32kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_2ch_44kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/bbb_2ch_48kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/highres_1ch_96kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/highres_1ch_176kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/highres_1ch_192kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/highres_2ch_96kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/highres_2ch_176kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/highres_2ch_192kHz_lvl4_flac.mka"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/sd_2ch_48kHz_lvl4_flac.mka"},

                {MediaFormat.MIMETYPE_AUDIO_G711_ALAW, "bbb_1ch_8kHz_alaw.wav"},
                {MediaFormat.MIMETYPE_AUDIO_G711_ALAW, "bbb_2ch_8kHz_alaw.wav"},

                {MediaFormat.MIMETYPE_AUDIO_G711_MLAW, "bbb_1ch_8kHz_mulaw.wav"},
                {MediaFormat.MIMETYPE_AUDIO_G711_MLAW, "bbb_2ch_8kHz_mulaw.wav"},

                {MediaFormat.MIMETYPE_AUDIO_MSGSM, "bbb_1ch_8kHz_gsm.wav"},

                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "bbb_1ch_16kHz_vorbis.mka"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "bbb_2ch_44kHz_vorbis.mka"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_1ch_8kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_1ch_12kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_1ch_16kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_1ch_24kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_1ch_32kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_1ch_48kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_2ch_8kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_2ch_12kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_2ch_16kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_2ch_24kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_2ch_32kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/bbb_2ch_48kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/highres_1ch_96kHz_q10_vorbis.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/highres_2ch_96kHz_q10_vorbis.ogg"},

                {MediaFormat.MIMETYPE_AUDIO_OPUS, "bbb_2ch_48kHz_opus.mka"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "bbb_1ch_48kHz_opus.mka"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_1ch_8kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_1ch_12kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_1ch_16kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_1ch_24kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_1ch_32kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_1ch_48kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_2ch_8kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_2ch_12kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_2ch_16kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_2ch_24kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_2ch_32kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_2ch_48kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_5ch_8kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_5ch_12kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_5ch_16kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_5ch_24kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_5ch_32kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_5ch_48kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_6ch_8kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_6ch_12kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_6ch_16kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_6ch_24kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_6ch_32kHz_opus.ogg"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/bbb_6ch_48kHz_opus.ogg"},

                {MediaFormat.MIMETYPE_AUDIO_AAC, "bbb_1ch_16kHz_aac.mp4"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "bbb_2ch_44kHz_aac.mp4"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_8kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_12kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_16kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_22kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_24kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_32kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_44kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_48kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_8kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_12kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_16kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_22kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_24kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_32kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_44kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_48kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_8kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_12kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_16kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_22kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_24kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_32kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_44kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_48kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_8kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_12kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_16kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_22kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_24kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_32kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_44kHz_aac_lc.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_48kHz_aac_lc.m4a"},

                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_16kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_22kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_24kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_32kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_44kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_48kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_16kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_22kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_24kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_32kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_44kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_5ch_48kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_16kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_22kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_24kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_32kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_44kHz_aac_he.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_6ch_48kHz_aac_he.m4a"},

                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_16kHz_aac_hev2.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_22kHz_aac_hev2.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_24kHz_aac_hev2.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_32kHz_aac_hev2.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_44kHz_aac_hev2.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_48kHz_aac_hev2.m4a"},

                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_16kHz_aac_eld.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_22kHz_aac_eld.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_24kHz_aac_eld.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_32kHz_aac_eld.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_44kHz_aac_eld.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_48kHz_aac_eld.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_16kHz_aac_eld.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_22kHz_aac_eld.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_24kHz_aac_eld.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_32kHz_aac_eld.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_44kHz_aac_eld.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_48kHz_aac_eld.m4a"},

                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_8kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_16kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_22kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_24kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_32kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_44kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_1ch_48kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_8kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_16kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_22kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_24kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_32kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_44kHz_usac.m4a"},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "audio/bbb_2ch_48kHz_usac.m4a"},
        }));
        return prepareParamList(exhaustiveArgsList, false, true, false, true);
    }

    public CodecDecoderBlockModelMultiAccessUnitTest(String decoder, String mediaType,
            String testFile, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
    }

    @Before
    public void setUp() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        checkFormatSupport(mCodecName, mMediaType, false, formatList, null, CODEC_OPTIONAL);
    }

    /**
     * Verifies if the component under test can decode the test file correctly in multiple frame
     * block model mode. The decoding happens in asynchronous mode with eos flag signalled with
     * last compressed frame. The test verifies if the component / framework output is consistent
     * with single access unit normal mode and single access unit block model mode.
     * <p>
     * Check description of class {@link CodecDecoderBlockModelMultiAccessUnitTest}
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE",
            "android.media.MediaFormat#KEY_BUFFER_BATCH_THRESHOLD_OUTPUT_SIZE",
            "android.media.MediaCodec.Callback#onOutputBuffersAvailable",
            "android.media.MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecode() throws IOException, InterruptedException {
        assumeTrue(mCodecName + " does not support FEATURE_MultipleFrames",
                isFeatureSupported(mCodecName, mMediaType, FEATURE_MultipleFrames));

        CodecDecoderTestBase cdtb = new CodecDecoderTestBase(mCodecName, mMediaType, null,
                mAllTestParams);
        cdtb.decodeToMemory(mTestFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                Integer.MAX_VALUE);
        OutputManager ref = cdtb.getOutputManager();

        CodecDecoderBlockModelTestBase cdbmtb = new CodecDecoderBlockModelTestBase(
                mCodecName, mMediaType, null, mAllTestParams);
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());
        cdbmtb.decodeToMemory(mTestFile, mCodecName, test, 0,
                MediaExtractor.SEEK_TO_CLOSEST_SYNC, Integer.MAX_VALUE);
        if (!ref.equals(test)) {
            fail("Output in block model mode is not same as output in normal mode. \n"
                    + mTestConfig + mTestEnv + test.getErrMsg());
        }

        mSaveToMem = true;
        mOutputBuff = test;
        MediaFormat format = setUpSource(mTestFile);
        int maxSampleSize = getMaxSampleSizeForMediaType(mTestFile, mMediaType);
        mCodec = MediaCodec.createByCodecName(mCodecName);
        for (int[] outSizeInMs : OUT_SIZE_IN_MS) {
            configureKeysForLargeAudioBlockModelFrameMode(format, maxSampleSize, outSizeInMs[0],
                    outSizeInMs[1]);
            mOutputBuff.reset();
            configureCodec(format, true, true, false);
            mMaxInputLimitMs = outSizeInMs[0];
            mCodec.start();
            mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            doWork(Integer.MAX_VALUE);
            queueEOS();
            waitForAllOutputs();
            mCodec.reset();
            if (!ref.equalsByteOutput(mOutputBuff)) {
                fail("Output of decoder component when fed with multiple access units in single"
                        + " enqueue call differs from output received when each access unit is fed"
                        + " separately. \n" + mTestConfig + mTestEnv + mOutputBuff.getErrMsg());
            }
        }
        mCodec.release();
        mExtractor.release();
    }
}
