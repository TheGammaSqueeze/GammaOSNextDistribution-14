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

package android.media.decoder.cts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.TestArgs;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conformance test for decoders on the device.
 *
 * This test will decode test vectors and calculate every decoded frame's md5
 * checksum to see if it matches with the correct md5 value read from a
 * reference file associated with the test vector. Test vector md5 sums are
 * based on the YUV 420 plannar format.
 */
@AppModeFull(reason = "There should be no instant apps specific behavior related to conformance")
@RunWith(Parameterized.class)
public class DecoderConformanceTest {
    private enum Status {
        FAIL,
        PASS,
        SKIP;
    }

    private static final String REPORT_LOG_NAME = "CtsMediaDecoderTestCases";
    private static final String TAG = "DecoderConformanceTest";
    private static final String CONFORMANCE_SUBDIR = "conformance_vectors/";
    private static final String mInpPrefix = WorkDir.getMediaDirString() + CONFORMANCE_SUBDIR;
    private static final Map<String, String> MIMETYPE_TO_TAG = Map.of(
            MediaFormat.MIMETYPE_VIDEO_VP9, "vp9");

    private final String mDecoderName;
    private final String mMediaType;
    private final String mTestVector;

    private MediaCodec mDecoder;
    private MediaExtractor mExtractor;

    private DeviceReportLog mReportLog;

    @Parameterized.Parameters(name = "{index}_{0}")
    public static Collection<Object[]> input() throws Exception {
        final String[] mediaTypeList = new String[] {MediaFormat.MIMETYPE_VIDEO_VP9};
        final List<Object[]> argsList = new ArrayList<>();
        for (String mediaType : mediaTypeList) {
            if (TestArgs.shouldSkipMediaType(mediaType)) {
                continue;
            }
            String[] componentNames = MediaUtils.getDecoderNamesForMime(mediaType);
            List<String> testVectors = readCodecTestVectors(mediaType);
            for (String testVector : testVectors) {
                for (String name : componentNames) {
                    if (TestArgs.shouldSkipCodec(name)) {
                        continue;
                    }
                    argsList.add(new Object[] {name, mediaType, testVector});
                }
            }
        }
        return argsList;
    }

    public DecoderConformanceTest(String decodername, String mediaType, String testvector) {
        mDecoderName = decodername;
        mMediaType = mediaType;
        mTestVector = testvector;
    }

    private static List<String> readResourceLines(String fileName) throws Exception {
        InputStream is = new FileInputStream(mInpPrefix + fileName);
        BufferedReader in = new BufferedReader(new InputStreamReader(is, "UTF-8"));

        // Read the file line by line.
        List<String> lines = new ArrayList<String>();
        String str;
        while ((str = in.readLine()) != null) {
            int k = str.indexOf(' ');
            String line = k >= 0 ? str.substring(0, k) : str;
            lines.add(line);
        }

        is.close();
        return lines;
    }

    private static List<String> readCodecTestVectors(String mime) throws Exception {
        String tag = MIMETYPE_TO_TAG.get(mime);
        String testVectorFileName = tag + "_test_vectors";
        return readResourceLines(testVectorFileName);
    }

    private List<String> readVectorMD5Sums(String mime, String vectorName) throws Exception {
        String tag = MIMETYPE_TO_TAG.get(mime);
        String md5FileName = vectorName + "_" + tag + "_md5";
        return readResourceLines(md5FileName);
    }

    private void release() {
        try {
            mDecoder.stop();
        } catch (Exception e) {
            Log.e(TAG, "Mediacodec stop exception");
        }

        try {
            mDecoder.release();
            mExtractor.release();
        } catch (Exception e) {
            Log.e(TAG, "Mediacodec release exception");
        }

        mDecoder = null;
        mExtractor = null;
    }

    protected static AssetFileDescriptor getAssetFileDescriptorFor(final String res, String mime)
            throws FileNotFoundException {
        String tag = MIMETYPE_TO_TAG.get(mime);
        Preconditions.assertTestFileExists(mInpPrefix + res + "." + tag);
        File inpFile = new File(mInpPrefix + res + "." + tag);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    private Status decodeTestVector(String mime, String decoderName, String vectorName)
            throws Exception {
        AssetFileDescriptor testFd = getAssetFileDescriptorFor(vectorName, mime);
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                testFd.getLength());
        mExtractor.selectTrack(0);

        mDecoder = MediaCodec.createByCodecName(decoderName);
        MediaCodecInfo codecInfo = mDecoder.getCodecInfo();
        MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(mime);
        if (!caps.isFormatSupported(mExtractor.getTrackFormat(0))) {
            return Status.SKIP;
        }

        List<String> frameMD5Sums;
        try {
            frameMD5Sums = readVectorMD5Sums(mime, vectorName);
        } catch(Exception e) {
            Log.e(TAG, "Fail to read " + vectorName + "md5sum file");
            return Status.FAIL;
        }

        try {
            if (MediaUtils.verifyDecoder(mDecoder, mExtractor, frameMD5Sums)) {
                return Status.PASS;
            }
            Log.d(TAG, vectorName + " decoded frames do not match");
            return Status.FAIL;
        } finally {
            release();
        }
    }

    @Test
    public void testDecoderConformance() {
        Log.d(TAG, "Decode vector " + mTestVector + " with " + mDecoderName);
        Status stat = Status.PASS;
        try {
            stat = decodeTestVector(mMediaType, mDecoderName, mTestVector);
        } catch (Exception e) {
            Log.e(TAG, "Decode " + mTestVector + " fail");
            fail("Received exception " + e);
        } finally {
            release();
        }
        String streamName = "decoder_conformance_test";
        mReportLog = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        mReportLog.addValue("mime", mMediaType, ResultType.NEUTRAL, ResultUnit.NONE);
        mReportLog.addValue("pass", stat != Status.FAIL, ResultType.NEUTRAL, ResultUnit.NONE);
        mReportLog.addValue("vector_name", mTestVector, ResultType.NEUTRAL, ResultUnit.NONE);
        mReportLog.addValue("decode_name", mDecoderName, ResultType.NEUTRAL, ResultUnit.NONE);
        mReportLog.submit(InstrumentationRegistry.getInstrumentation());
        assumeTrue(mDecoderName + " failed for " + mTestVector, stat != Status.FAIL);
        assumeTrue(mDecoderName + " skipped for " + mTestVector, stat != Status.SKIP);
    }
}
