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

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;
import static android.media.MediaDrm.SECURITY_LEVEL_HW_SECURE_ALL;

import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaDrm;
import android.media.MediaFormat;
import android.media.UnsupportedSchemeException;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Utils;
import android.util.Log;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests the basic aspects of the media performance class.
 */
public class PerformanceClassTest {
    private static final String TAG = "PerformanceClassTest";
    public static final String[] VIDEO_CONTAINER_MEDIA_TYPES =
        {"video/mp4", "video/webm", "video/3gpp", "video/3gpp2", "video/avi", "video/x-ms-wmv",
            "video/x-ms-asf"};
    static ArrayList<String> mMimeSecureSupport = new ArrayList<>();

    @Rule
    public final TestName mTestName = new TestName();

    @Before
    public void isPerformanceClassCandidate() {
        Utils.assumeDeviceMeetsPerformanceClassPreconditions();
    }

    static {
        mMimeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        mMimeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
        mMimeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_VP9);
        mMimeSecureSupport.add(MediaFormat.MIMETYPE_VIDEO_AV1);
    }


    private boolean isHandheld() {
        // handheld nature is not exposed to package manager, for now
        // we check for touchscreen and NOT watch and NOT tv
        PackageManager pm =
            InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        return pm.hasSystemFeature(pm.FEATURE_TOUCHSCREEN)
                && !pm.hasSystemFeature(pm.FEATURE_WATCH)
                && !pm.hasSystemFeature(pm.FEATURE_TELEVISION)
                && !pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE);
    }

    @SmallTest
    @Test
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-11"})
    public void testSecureHwDecodeSupport() {
        ArrayList<String> noSecureHwDecoderForMimes = new ArrayList<>();
        for (String mime : mMimeSecureSupport) {
            boolean isSecureHwDecoderFoundForMime = false;
            boolean isHwDecoderFoundForMime = false;
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
            for (MediaCodecInfo info : codecInfos) {
                if (info.isEncoder() || !info.isHardwareAccelerated() || info.isAlias()) continue;
                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                    if (caps != null) {
                        isHwDecoderFoundForMime = true;
                        if (caps.isFeatureSupported(FEATURE_SecurePlayback))
                            isSecureHwDecoderFoundForMime = true;
                    }
                } catch (Exception ignored) {
                }
            }
            if (isHwDecoderFoundForMime && !isSecureHwDecoderFoundForMime)
                noSecureHwDecoderForMimes.add(mime);
        }

        boolean secureDecodeSupportIfHwDecoderPresent = noSecureHwDecoderForMimes.isEmpty();

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.SecureCodecRequirement r5_1__H_1_11 = pce.addR5_1__H_1_11();
        r5_1__H_1_11.setSecureReqSatisfied(secureDecodeSupportIfHwDecoderPresent);

        pce.submitAndCheck();
    }

    @SmallTest
    @Test
    @CddTest(requirements = {"2.2.7.1/5.7/H-1-2"})
    public void testMediaDrmSecurityLevelHwSecureAll() throws UnsupportedSchemeException {
        List<UUID> drmList = MediaDrm.getSupportedCryptoSchemes();
        List<UUID> supportedHwSecureAllSchemes = new ArrayList<>();

        for (UUID cryptoSchemeUUID : drmList) {
            boolean cryptoSchemeSupportedForAtleastOneMediaType = false;
            for (String mediaType : VIDEO_CONTAINER_MEDIA_TYPES) {
                cryptoSchemeSupportedForAtleastOneMediaType |= MediaDrm
                    .isCryptoSchemeSupported(cryptoSchemeUUID, mediaType,
                        SECURITY_LEVEL_HW_SECURE_ALL);
            }
            if (cryptoSchemeSupportedForAtleastOneMediaType) {
                supportedHwSecureAllSchemes.add(cryptoSchemeUUID);
            }
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.SecureCodecRequirement r5_7__H_1_2 = pce.addR5_7__H_1_2();

        r5_7__H_1_2.setNumCryptoHwSecureAllDec(supportedHwSecureAllSchemes.size());

        pce.submitAndCheck();
    }

    @SmallTest
    @Test
    public void testMediaPerformanceClassScope() throws Exception {
        // if device is not of a performance class, we are done.
        Assume.assumeTrue("not a device of a valid media performance class", Utils.isPerfClass());

        if (Utils.isPerfClass()) {
            assertTrue("performance class is only defined for Handheld devices", isHandheld());
        }
    }

    @Test
    @CddTest(requirements={
        "2.2.7.3/7.1.1.1/H-1-1",
        "2.2.7.3/7.1.1.1/H-2-1",
        "2.2.7.3/7.1.1.3/H-1-1",
        "2.2.7.3/7.1.1.3/H-2-1",})
    public void testMinimumResolutionAndDensity() {
        int density = Utils.DISPLAY_DPI;
        int longPix = Utils.DISPLAY_LONG_PIXELS;
        int shortPix = Utils.DISPLAY_SHORT_PIXELS;

        Log.i(TAG, String.format("dpi=%d size=%dx%dpix", density, longPix, shortPix));

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.ResolutionRequirement r7_1_1_1__h_1_1 = pce.addR7_1_1_1__H_1_1();
        PerformanceClassEvaluator.DensityRequirement r7_1_1_3__h_1_1 = pce.addR7_1_1_3__H_1_1();
        PerformanceClassEvaluator.ResolutionRequirement r7_1_1_1__h_2_1 = pce.addR7_1_1_1__H_2_1();
        PerformanceClassEvaluator.DensityRequirement r7_1_1_3__h_2_1 = pce.addR7_1_1_3__H_2_1();

        r7_1_1_1__h_1_1.setLongResolution(longPix);
        r7_1_1_1__h_2_1.setLongResolution(longPix);

        r7_1_1_1__h_1_1.setShortResolution(shortPix);
        r7_1_1_1__h_2_1.setShortResolution(shortPix);

        r7_1_1_3__h_1_1.setDisplayDensity(density);
        r7_1_1_3__h_2_1.setDisplayDensity(density);

        pce.submitAndCheck();
    }

    @Test
    @CddTest(requirements={
        "2.2.7.3/7.6.1/H-1-1",
        "2.2.7.3/7.6.1/H-2-1",})
    public void testMinimumMemory() {
        long totalMemoryMb = Utils.TOTAL_MEMORY_MB;

        Log.i(TAG, String.format("Total device memory = %,d MB", totalMemoryMb));

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.MemoryRequirement r7_6_1_h_1_1 = pce.addR7_6_1__H_1_1();
        PerformanceClassEvaluator.MemoryRequirement r7_6_1_h_2_1 = pce.addR7_6_1__H_2_1();

        r7_6_1_h_1_1.setPhysicalMemory(totalMemoryMb);
        r7_6_1_h_2_1.setPhysicalMemory(totalMemoryMb);

        pce.submitAndCheck();
    }
}
