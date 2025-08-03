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

package android.videoencoding.app;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.mediav2.common.cts.CodecTestBase.CONTEXT;
import static android.mediav2.common.cts.CodecTestBase.MEDIA_CODEC_LIST_REGULAR;
import static android.mediav2.common.cts.CodecTestBase.selectCodecs;
import static android.mediav2.common.cts.DecodeStreamToYuv.getFormatInStream;
import static android.os.Environment.buildPath;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderSurfaceTestBase;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;
import android.os.Environment;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.Preconditions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * Test transcoding using media codec api.
 * <p>
 * The test decodes an input clip to surface. This decoded output is fed as input to encoder.
 * Assuming no frame drops, the test expects,
 * <ul>
 *     <li>The number of encoded frames to be identical to number of frames present in input clip
 *     .</li>
 *     <li>The encoder output timestamps list should be identical to decoder input timestamp list
 *     .</li>
 * </ul>
 * <p>
 * The test has provision to validate the encoded output by computing PSNR against input. This is
 * however disabled as VMAF is chosen for analysis. This analysis is done on host side.
 */
@RunWith(AndroidJUnit4.class)
public class VideoTranscoderTest {
    private static final String MEDIA_DIR = "/sdcard/veq/input/";
    public static final String ENC_CONFIG_JSON = "conf-json";
    private static final String ENC_CONFIG_FILE;
    private static String PATH_PREFIX;
    public static final int DEFAULT_TEST_TIMEOUT_MS = 360000;

    private String mEncoderName;
    private String mEncMediaType;
    private String mDecoderName;
    private String mTestFileMediaType;
    private String mTestFile;
    private EncoderConfigParams[] mEncCfgParams;
    private String[] mOutputFileNames;
    private int mDecColorFormat;

    static {
        android.os.Bundle args = InstrumentationRegistry.getArguments();
        ENC_CONFIG_FILE = args.getString(ENC_CONFIG_JSON);
    }

    static class TestTranscode extends CodecEncoderSurfaceTestBase {
        private final String mOutputFileName;

        TestTranscode(String encoder, String mediaType, String decoder, String testFileMediaType,
                String testFile, EncoderConfigParams encCfgParams, String outputFileName,
                int decColorFormat, boolean isOutputToneMapped, boolean usePersistentSurface,
                String allTestParams) {
            super(encoder, mediaType, decoder, testFileMediaType, testFile, encCfgParams,
                    decColorFormat, isOutputToneMapped, usePersistentSurface, allTestParams);
            mOutputFileName = outputFileName;
        }

        @Override
        public void setUpCodecEncoderSurfaceTestBase()
                throws IOException, CloneNotSupportedException {
            super.setUpCodecEncoderSurfaceTestBase();
            mEncoderFormat = mEncCfgParams.getFormat();
        }

        private String getTempFilePath(String infix) throws IOException {
            String totalPath = PATH_PREFIX + infix + ".mp4";
            new FileOutputStream(totalPath).close();
            return totalPath;
        }

        public void doTranscode()
                throws IOException, InterruptedException, CloneNotSupportedException {
            try {
                setUpCodecEncoderSurfaceTestBase();
                encodeToMemory(false, false, false, new OutputManager(), true,
                        getTempFilePath(mOutputFileName));
            } finally {
                tearDownCodecEncoderSurfaceTestBase();
            }
        }
    }

    private void parseEncoderConfigurationFile(String jsonPath) throws JSONException, IOException {
        Preconditions.assertTestFileExists(jsonPath);
        String jsonString =
                new String(Files.readAllBytes(Paths.get(jsonPath)), StandardCharsets.UTF_8);
        JSONArray jsonArray = new JSONArray(jsonString);
        JSONObject obj = jsonArray.getJSONObject(0);
        mTestFile = MEDIA_DIR + "samples/" + obj.getString("RefFileName");
        mTestFileMediaType = obj.getString("RefMediaType");
        mEncMediaType = obj.getString("TestMediaType");
        int width = obj.getInt("Width");
        int height = obj.getInt("Height");
        String componentType = obj.getString("EncoderType");
        CodecTestBase.ComponentClass cType = CodecTestBase.ComponentClass.ALL;
        if (componentType.equals("hw")) {
            cType = CodecTestBase.ComponentClass.HARDWARE;
        } else if (componentType.equals("sw")) {
            cType = CodecTestBase.ComponentClass.SOFTWARE;
        }
        mDecColorFormat = COLOR_FormatSurface;
        JSONArray codecConfigs = obj.getJSONArray("CodecConfigs");
        mEncCfgParams = new EncoderConfigParams[codecConfigs.length()];
        mOutputFileNames = new String[codecConfigs.length()];
        for (int i = 0; i < codecConfigs.length(); i++) {
            JSONObject codecConfig = codecConfigs.getJSONObject(i);
            mEncCfgParams[i] = new EncoderConfigParams.Builder(mEncMediaType)
                    .setWidth(width)
                    .setHeight(height)
                    .setKeyFrameInterval(codecConfig.getInt("KeyFrameInterval"))
                    .setMaxBFrames(codecConfig.getInt("MaxBFrames"))
                    .setBitRate(codecConfig.getInt("BitRate"))
                    .setProfile(codecConfig.getInt("Profile"))
                    .setLevel(codecConfig.getInt("Level"))
                    .setColorFormat(COLOR_FormatSurface)
                    .build();
            String outFileName = codecConfig.getString("EncodedFileName");
            mOutputFileNames[i] = outFileName.substring(0, outFileName.lastIndexOf('.'));
        }
        MediaFormat format = getFormatInStream(mTestFileMediaType, mTestFile);
        mDecoderName = MEDIA_CODEC_LIST_REGULAR.findDecoderForFormat(format);
        ArrayList<MediaFormat> formats = new ArrayList<>();
        for (EncoderConfigParams param : mEncCfgParams) {
            formats.add(param.getFormat());
        }
        ArrayList<String> codecs = selectCodecs(mEncMediaType, formats, null, true, cType);
        if (!codecs.isEmpty()) mEncoderName = codecs.get(0);
    }

    @LargeTest
    @Test(timeout = DEFAULT_TEST_TIMEOUT_MS)
    public void testTranscode() throws IOException, InterruptedException,
            JSONException, CloneNotSupportedException {
        Assume.assumeTrue("Test did not receive config file for encoding", ENC_CONFIG_FILE != null);
        parseEncoderConfigurationFile(MEDIA_DIR + "json/" + ENC_CONFIG_FILE);
        Assume.assumeTrue("Found no encoder supporting the config file", mEncoderName != null);
        Assume.assumeTrue("Found no decoder supporting the config file", mDecoderName != null);
        Assert.assertEquals("Apk does not have permissions to write to external storage",
                PackageManager.PERMISSION_GRANTED,
                CONTEXT.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        File pub = new File("/sdcard/veq/output/");
        File dir = buildPath(pub,
                "output_" + ENC_CONFIG_FILE.substring(0, ENC_CONFIG_FILE.lastIndexOf('.')));
        if (!dir.exists()) {
            Assert.assertTrue("Unable to create dir " + dir.getAbsolutePath(), dir.mkdirs());
        }
        PATH_PREFIX = dir.getAbsolutePath() + File.separator;
        for (int i = 0; i < mEncCfgParams.length; i++) {
            TestTranscode ep = new TestTranscode(mEncoderName, mEncMediaType,
                    mDecoderName, mTestFileMediaType, mTestFile, mEncCfgParams[i],
                    mOutputFileNames[i], mDecColorFormat, false, false, "");
            ep.doTranscode();
        }
    }
}
