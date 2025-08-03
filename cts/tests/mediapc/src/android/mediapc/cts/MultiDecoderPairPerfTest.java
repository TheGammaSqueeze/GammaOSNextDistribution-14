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

package android.mediapc.cts;

import static android.mediapc.cts.CodecTestBase.codecFilter;
import static android.mediapc.cts.CodecTestBase.codecPrefix;
import static android.mediapc.cts.CodecTestBase.mediaTypePrefix;

import android.media.MediaFormat;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Utils;
import android.util.Pair;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The following test class calculates the maximum number of concurrent decode sessions that it can
 * support by the two hardware (mime - decoder) pair calculated via the
 * CodecCapabilities.getMaxSupportedInstances() and
 * VideoCapabilities.getSupportedPerformancePoints() methods. Splits the maximum supported instances
 * between the two pairs and ensures that all the supported sessions succeed in decoding
 * with meeting the expected frame rate.
 */
@RunWith(Parameterized.class)
public class MultiDecoderPairPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiDecoderPairPerfTest.class.getSimpleName();
    private static final int REQUIRED_CONCURRENT_NON_SECURE_INSTANCES_WITH_SECURE = 3;

    private final Pair<String, String> mFirstPair;
    private final Pair<String, String> mSecondPair;

    public MultiDecoderPairPerfTest(Pair<String, String> firstPair, Pair<String, String> secondPair,
            boolean isAsync) {
        super(null, null, isAsync);
        mFirstPair = firstPair;
        mSecondPair = secondPair;
    }

    @Rule
    public final TestName mTestName = new TestName();

    // Returns the list of params with two hardware (mime - decoder) pairs in both
    // sync and async modes.
    // Parameters {0}_{1}_{2} -- Pair(Mime DecoderName)_Pair(Mime DecoderName)_isAsync
    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}")
    public static Collection<Object[]> inputParams() {
        final List<Object[]> argsList = new ArrayList<>();
        ArrayList<Pair<String, String>> mimeTypeDecoderPairs = new ArrayList<>();
        for (String mime : mMimeList) {
            if (mediaTypePrefix != null && !mime.startsWith(mediaTypePrefix)) {
                continue;
            }
            ArrayList<String> listOfDecoders = getHardwareCodecsForMime(mime, false, true);
            for (String decoder : listOfDecoders) {
                if ((codecPrefix != null && !decoder.startsWith(codecPrefix))
                        || (codecFilter != null && !codecFilter.matcher(decoder).matches())) {
                    continue;
                }
                mimeTypeDecoderPairs.add(Pair.create(mime, decoder));
            }
        }
        for (int i = 0; i < mimeTypeDecoderPairs.size(); i++) {
            for (int j = i + 1; j < mimeTypeDecoderPairs.size(); j++) {
                Pair<String, String> pair1 = mimeTypeDecoderPairs.get(i);
                Pair<String, String> pair2 = mimeTypeDecoderPairs.get(j);
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{pair1, pair2, isAsync});
                }
            }
        }
        return argsList;
    }

    /**
     * This test calculates the number of 720p 30 fps decoder instances that the given two
     * (mime - decoder) pairs can support. Assigns the same number of instances to the two pairs
     * (if max instances are even), or one more to one pair (if odd) and ensures that all the
     * concurrent sessions succeed in decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-1", "2.2.7.1/5.1/H-1-2"})
    public void test720p() throws Exception {
        Assume.assumeTrue(Utils.isSPerfClass() || Utils.isRPerfClass() || !Utils.isPerfClass());
        Assume.assumeFalse("Skipping regular performance tests for secure codecs",
                isSecureSupportedCodec(mFirstPair.second, mFirstPair.first) ||
                        isSecureSupportedCodec(mSecondPair.second, mSecondPair.first));

        boolean hasVP9 = mFirstPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9) ||
                mSecondPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
        int requiredMinInstances = getRequiredMinConcurrentInstances720p(hasVP9);
        testCodec(m720pTestFiles, null, 720, 1280, requiredMinInstances);
    }

    /**
     * This test calculates the number of 1080p 30 fps decoder instances that the given two
     * (mime - decoder) pairs can support. Assigns the same number of instances to the two pairs
     * (if max instances are even), or one more to one pair (if odd) and ensures that all the
     * concurrent sessions succeed in decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {
            "2.2.7.1/5.1/H-1-1",
            "2.2.7.1/5.1/H-1-2",
            "2.2.7.1/5.1/H-1-9",
            "2.2.7.1/5.1/H-1-10",})
    public void test1080p() throws Exception {
        Assume.assumeTrue(Utils.isTPerfClass() || !Utils.isPerfClass());
        boolean isFirstSecure = isSecureSupportedCodec(mFirstPair.second, mFirstPair.first);
        boolean isSecondSecure = isSecureSupportedCodec(mSecondPair.second, mSecondPair.first);
        boolean onlyOneSecure = isFirstSecure ^ isSecondSecure;
        boolean bothSecure = isFirstSecure & isSecondSecure;

        if (bothSecure) {
            testCodec(null, m1080pWidevineTestFiles, 1080, 1920,
                    REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES);
        } else if (onlyOneSecure) {
            testCodec(m1080pTestFiles, m1080pWidevineTestFiles, 1080, 1920,
                    REQUIRED_CONCURRENT_NON_SECURE_INSTANCES_WITH_SECURE + 1);
        } else {
            testCodec(m1080pTestFiles, null, 1080, 1920, REQUIRED_MIN_CONCURRENT_INSTANCES);
        }
    }

    /**
     * This test calculates the number of 4k 30 fps SDR decoder instances that the given two
     * (mime - decoder) pairs can support. Assigns the same number of instances to the two pairs
     * (if max instances are even), or one more to one pair (if odd) and ensures that all the
     * concurrent sessions succeed in decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {
            "2.2.7.1/5.1/H-1-1",
            "2.2.7.1/5.1/H-1-2",
            "2.2.7.1/5.1/H-1-9",
            "2.2.7.1/5.1/H-1-10"})
    public void test4k() throws Exception {
        Assume.assumeTrue(Utils.isUPerfClass() || !Utils.isPerfClass());
        boolean isFirstSecure = isSecureSupportedCodec(mFirstPair.second, mFirstPair.first);
        boolean isSecondSecure = isSecureSupportedCodec(mSecondPair.second, mSecondPair.first);
        boolean onlyOneSecure = isFirstSecure ^ isSecondSecure;
        boolean bothSecure = isFirstSecure & isSecondSecure;

        if (bothSecure) {
            testCodec(null, m2160pPc14WidevineTestFiles, 2160, 3840,
                    REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES);
        } else if (onlyOneSecure) {
            testCodec(m2160pPc14TestFiles, m2160pPc14WidevineTestFiles, 2160, 3840,
                    REQUIRED_CONCURRENT_NON_SECURE_INSTANCES_WITH_SECURE + 1);
        } else {
            testCodec(m2160pPc14TestFiles, null, 2160, 3840, REQUIRED_MIN_CONCURRENT_INSTANCES);
        }
    }

    /**
     * This test calculates the number of 4k 30 fps HDR decoder instances that the given two
     * (mime - decoder) pairs can support. Assigns the same number of instances to the two pairs
     * (if max instances are even), or one more to one pair (if odd) and ensures that all the
     * concurrent sessions succeed in decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {
            "2.2.7.1/5.1/H-1-9",
            "2.2.7.1/5.1/H-1-10"})
    public void test4kHbd() throws Exception {
        Assume.assumeTrue(Utils.isUPerfClass() || !Utils.isPerfClass());
        boolean isFirstSecure = isSecureSupportedCodec(mFirstPair.second, mFirstPair.first);
        boolean isSecondSecure = isSecureSupportedCodec(mSecondPair.second, mSecondPair.first);
        boolean onlyOneSecure = isFirstSecure ^ isSecondSecure;
        boolean bothSecure = isFirstSecure & isSecondSecure;

        if (bothSecure) {
            testCodec(null, m2160pPc1410bitWidevineTestFiles, 2160, 3840,
                    REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES);
        } else if (onlyOneSecure) {
            // 2 non-secure 4k HDR, 1 secure 4k SDR , 1 non-secure 1080p SDR
            testCodec(m2160pPc1410bitTestFiles, m2160pPc14WidevineTestFiles, 2160, 3840,
                    REQUIRED_CONCURRENT_NON_SECURE_INSTANCES_WITH_SECURE + 1);
        } else {
            Assume.assumeFalse("Skipping regular performance tests for pair of non-secure decoders",
                true);
        }
    }

    private void testCodec(Map<String, String> testFiles, Map<String, String> widevineTestFiles,
            int height, int width, int requiredMinInstances) throws Exception {
        mTestFiles = testFiles;
        ArrayList<Pair<String, String>> mimeDecoderPairs = new ArrayList<>();
        mimeDecoderPairs.add(mFirstPair);
        mimeDecoderPairs.add(mSecondPair);
        boolean isFirstSecure = isSecureSupportedCodec(mFirstPair.second, mFirstPair.first);
        boolean isSecondSecure = isSecureSupportedCodec(mSecondPair.second, mSecondPair.first);
        boolean secureWithUnsecure = isFirstSecure ^ isSecondSecure;
        boolean bothSecure = isFirstSecure & isSecondSecure;
        boolean noneSecure = !(isFirstSecure || isSecondSecure);
        int maxInstances = checkAndGetMaxSupportedInstancesForCodecCombinations(height, width,
                mimeDecoderPairs, false, requiredMinInstances);
        double achievedFrameRate = 0.0;
        boolean meetsPreconditions = (isFirstSecure || isSecondSecure) ?
                meetsSecureDecodePreconditions() : true;
        // secure test should not reach this point if secure codec doesn't support PP
        if (meetsPreconditions && (maxInstances >= requiredMinInstances || secureWithUnsecure)) {
            int secondPairInstances = maxInstances / 2;
            int firstPairInstances = maxInstances - secondPairInstances;
            if (secureWithUnsecure) {
                firstPairInstances =
                        isSecureSupportedCodec(mFirstPair.second, mFirstPair.first) ? 1 : 3;
                secondPairInstances = requiredMinInstances - firstPairInstances;
                maxInstances = requiredMinInstances;
            }
            List<Decode> testList = new ArrayList<>();

            if (height > 1080 && secureWithUnsecure) {
                // Add 1 1080p instance for H-1-9
                if (isFirstSecure) {
                    String testFile = m1080pTestFiles.get(mSecondPair.first);
                    testList.add(new Decode(mSecondPair.first, testFile, mSecondPair.second,
                            mIsAsync, false));
                    secondPairInstances--;
                } else {
                    String testFile = m1080pTestFiles.get(mFirstPair.first);
                    testList.add(new Decode(mFirstPair.first, testFile, mFirstPair.second,
                            mIsAsync, false));
                    firstPairInstances--;
                }
            }

            for (int i = 0; i < firstPairInstances; i++) {
                boolean isSecure = isFirstSecure;
                String testFile = isSecure ? widevineTestFiles.get(mFirstPair.first) :
                        mTestFiles.get(mFirstPair.first);
                Assume.assumeTrue("Add " + (isSecure ? "secure" : "") + " test vector for mime: " +
                        mFirstPair.first, testFile != null);
                testList.add(new Decode(mFirstPair.first, testFile, mFirstPair.second, mIsAsync,
                        isSecure));
            }
            for (int i = 0; i < secondPairInstances; i++) {
                boolean isSecure = isSecondSecure;
                String testFile = isSecure ? widevineTestFiles.get(mSecondPair.first) :
                        mTestFiles.get(mSecondPair.first);
                if (height > 1080 && noneSecure) testFile = m1080pTestFiles.get(mSecondPair.first);
                Assume.assumeTrue("Add " + (isSecure ? "secure" : "") + " test vector for mime: " +
                        mSecondPair.first, testFile != null);
                testList.add(new Decode(mSecondPair.first, testFile, mSecondPair.second,
                        mIsAsync, isSecure));
            }
            ExecutorService pool = Executors.newFixedThreadPool(maxInstances);
            List<Future<Double>> resultList = pool.invokeAll(testList);
            for (Future<Double> result : resultList) {
                achievedFrameRate += result.get();
            }
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        if (secureWithUnsecure) {
            PerformanceClassEvaluator.ConcurrentCodecRequirement r5_1__H_1_10;
            if (height > 1080) {
                r5_1__H_1_10 = pce.addR5_1__H_1_10_4k();
            } else {
                r5_1__H_1_10 = pce.addR5_1__H_1_10_1080p();
            }
            r5_1__H_1_10.setConcurrentFps(achievedFrameRate);
        } else if (bothSecure) {
            PerformanceClassEvaluator.ConcurrentCodecRequirement r5_1__H_1_9;
            if (height > 1080) {
                r5_1__H_1_9 = pce.addR5_1__H_1_9_4k();
            } else {
                r5_1__H_1_9 = pce.addR5_1__H_1_9_1080p();
            }
            r5_1__H_1_9.setConcurrentFps(achievedFrameRate);
        } else {
            PerformanceClassEvaluator.ConcurrentCodecRequirement r5_1__H_1_1;
            PerformanceClassEvaluator.ConcurrentCodecRequirement r5_1__H_1_2;
            if (height > 1080) {
                r5_1__H_1_1 = pce.addR5_1__H_1_1_4k();
                r5_1__H_1_2 = pce.addR5_1__H_1_2_4k();
                r5_1__H_1_1.setConcurrentInstances(maxInstances);
                r5_1__H_1_2.setConcurrentFps(achievedFrameRate);
            } else if (height == 1080) {
                r5_1__H_1_1 = pce.addR5_1__H_1_1_1080p();
                r5_1__H_1_2 = pce.addR5_1__H_1_2_1080p();
                r5_1__H_1_1.setConcurrentInstances(maxInstances);
                r5_1__H_1_2.setConcurrentFps(achievedFrameRate);
            } else {
                r5_1__H_1_1 = pce.addR5_1__H_1_1_720p(mFirstPair.first, mSecondPair.first, height);
                r5_1__H_1_2 = pce.addR5_1__H_1_2_720p(mFirstPair.first, mSecondPair.first, height);
                r5_1__H_1_1.setConcurrentInstances(maxInstances);
                r5_1__H_1_2.setConcurrentFps(achievedFrameRate);
            }
        }
        pce.submitAndCheck();
    }
}
