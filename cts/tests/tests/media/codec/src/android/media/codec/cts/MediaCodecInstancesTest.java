/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.media.codec.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Vector;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MediaCodecInstancesTest {
    private static final String TAG = MediaCodecInstancesTest.class.getSimpleName();

    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private CodecDynamicTestActivity mDynamicActivity;

    @Rule
    public ActivityScenarioRule<CodecDynamicTestActivity> mActivityRule =
            new ActivityScenarioRule<>(CodecDynamicTestActivity.class);

    private static MediaFormat createMinFormat(String mime, MediaCodecInfo.CodecCapabilities caps) {
        MediaFormat format;
        if (caps.getVideoCapabilities() != null) {
            MediaCodecInfo.VideoCapabilities vcaps = caps.getVideoCapabilities();
            int minWidth = vcaps.getSupportedWidths().getLower();
            int minHeight = vcaps.getSupportedHeightsFor(minWidth).getLower();
            int minBitrate = vcaps.getBitrateRange().getLower();
            int minFrameRate = Math.max(vcaps.getSupportedFrameRatesFor(minWidth, minHeight)
                    .getLower().intValue(), 1);
            format = MediaFormat.createVideoFormat(mime, minWidth, minHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, caps.colorFormats[0]);
            format.setInteger(MediaFormat.KEY_BIT_RATE, minBitrate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, minFrameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        } else {
            MediaCodecInfo.AudioCapabilities acaps = caps.getAudioCapabilities();
            int minSampleRate = acaps.getSupportedSampleRateRanges()[0].getLower();
            int minChannelCount = 1;
            int minBitrate = acaps.getBitrateRange().getLower();
            format = MediaFormat.createAudioFormat(mime, minSampleRate, minChannelCount);
            format.setInteger(MediaFormat.KEY_BIT_RATE, minBitrate);
        }

        return format;
    }

    private int getActualMax(boolean isEncoder, String name, String mime,
            MediaCodecInfo.CodecCapabilities caps, int max) {
        boolean isCompSecureVidDec =
                !isEncoder && !mime.startsWith("audio/") && caps.isFeatureSupported(
                        FEATURE_SecurePlayback);
        int flag = isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0;
        boolean memory_limited = false;
        MediaFormat format = createMinFormat(mime, caps);
        Log.d(TAG, "Test format " + format);
        Vector<MediaCodec> codecs = new Vector<>();
        Vector<Pair<Integer, Surface>> surfaces = new Vector<>();
        MediaCodec codec = null;
        ActivityManager am = CONTEXT.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo outInfo = new ActivityManager.MemoryInfo();
        for (int i = 0; i < max; ++i) {
            Pair<Integer, Surface> obj = null;
            try {
                Log.d(TAG, "Create codec " + name + " #" + i);
                if (isCompSecureVidDec) {
                    obj = mDynamicActivity.getSurface();
                    if (obj == null) {
                        int index = mDynamicActivity.addSurfaceView();
                        mDynamicActivity.waitTillSurfaceIsCreated(index);
                        obj = mDynamicActivity.getSurface();
                    }
                }
                codec = MediaCodec.createByCodecName(name);
                codec.configure(format, isCompSecureVidDec ? obj.second : null, null, flag);
                codec.start();
                codecs.add(codec);
                codec = null;
                if (isCompSecureVidDec) {
                    surfaces.add(obj);
                    obj = null;
                }

                am.getMemoryInfo(outInfo);
                if (outInfo.lowMemory) {
                    Log.d(TAG, "System is in low memory condition, stopping. max: " + i);
                    memory_limited = true;
                    break;
                }
            } catch (InterruptedException e) {
                fail("Got unexpected InterruptedException " + e.getMessage());
            } catch (IllegalArgumentException e) {
                fail("Got unexpected IllegalArgumentException " + e.getMessage());
            } catch (IOException e) {
                fail("Got unexpected IOException " + e.getMessage());
            } catch (MediaCodec.CodecException e) {
                // ERROR_INSUFFICIENT_RESOURCE is expected as the test keep creating codecs.
                // But other exception should be treated as failure.
                if (e.getErrorCode() == MediaCodec.CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                    Log.d(TAG, "Got CodecException with ERROR_INSUFFICIENT_RESOURCE.");
                    break;
                } else {
                    fail("Unexpected CodecException " + e.getDiagnosticInfo());
                }
            } finally {
                if (codec != null) {
                    Log.d(TAG, "release codec");
                    codec.release();
                    codec = null;
                }
                if (obj != null) {
                    mDynamicActivity.markSurface(obj.first, true);
                }
            }
        }
        int actualMax = codecs.size();
        for (int i = 0; i < codecs.size(); ++i) {
            Log.d(TAG, "release codec #" + i);
            codecs.get(i).release();
        }
        if (isCompSecureVidDec) {
            for (int i = 0; i < surfaces.size(); ++i) {
                Log.d(TAG, "mark surface usable #" + i);
                mDynamicActivity.markSurface(surfaces.get(i).first, true);
            }
            surfaces.clear();
        }
        codecs.clear();
        // encode both actual max and whether we ran out of memory
        if (memory_limited) {
            actualMax = -actualMax;
        }
        return actualMax;
    }

    private boolean knownTypes(String type) {
        return (type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_AAC)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_AC3)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_AMR_NB)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_AMR_WB)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_EAC3)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_FLAC)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_G711_ALAW)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_G711_MLAW)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_MPEG)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_MSGSM)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_OPUS)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_RAW)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_VORBIS)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AV1)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_H263)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_HEVC)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_MPEG2)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_MPEG4)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_VP8)
                || type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_VP9));
    }

    @ApiTest(apis = "MediaCodecInfo.CodecCapabilities#getMaxSupportedInstances")
    @Test
    public void testGetMaxSupportedInstances() {
        StringBuilder xmlOverrides = new StringBuilder();
        MediaCodecList allCodecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
        final boolean isLowRam = ActivityManager.isLowRamDeviceStatic();
        mActivityRule.getScenario().onActivity(activity -> mDynamicActivity = activity);
        for (MediaCodecInfo info : allCodecs.getCodecInfos()) {
            Log.d(TAG, "codec: " + info.getName());
            Log.d(TAG, "  isEncoder = " + info.isEncoder());

            // don't bother testing aliases
            if (info.isAlias()) {
                Log.d(TAG, "skipping: " + info.getName() + " is an alias for "
                        + info.getCanonicalName());
                continue;
            }

            String[] types = info.getSupportedTypes();
            for (String type : types) {
                if (!knownTypes(type)) {
                    Log.d(TAG, "skipping unknown type " + type);
                    continue;
                }
                Log.d(TAG, "calling getCapabilitiesForType " + type);
                MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
                int advertised = caps.getMaxSupportedInstances();
                Log.d(TAG, "getMaxSupportedInstances returns " + advertised);
                assertTrue(advertised > 0);

                // see how well the declared max matches against reality

                int tryMax = isLowRam ? 16 : 32;
                int tryMin = isLowRam ? 4 : 16;

                int trials = Math.min(advertised + 2, tryMax);
                int actualMax = getActualMax(
                        info.isEncoder(), info.getName(), type, caps, trials);
                Log.d(TAG, "actualMax " + actualMax + " vs advertised " + advertised
                        + " tryMin " + tryMin + " tryMax " + tryMax);

                boolean memory_limited = false;
                if (actualMax < 0) {
                    memory_limited = true;
                    actualMax = -actualMax;
                }

                boolean compliant = true;
                if (info.isHardwareAccelerated()) {
                    // very specific bounds for HW codecs
                    // so the adv+2 above is to see if the HW codec lets us go beyond adv
                    // (it should not)
                    if (actualMax != Math.min(advertised, tryMax)) {
                        Log.d(TAG, "NO: hwcodec " + actualMax + " != min(" + advertised
                                + "," + tryMax + ")");
                        compliant = false;
                    }
                } else {
                    // sw codecs get a little more relaxation due to memory pressure
                    if (actualMax >= Math.min(advertised, tryMax)) {
                        // no memory issues, and we allocated them all
                        Log.d(TAG, "OK: swcodec " + actualMax + " >= min(" + advertised
                                + "," + tryMax + ")");
                    } else if (actualMax >= Math.min(advertised, tryMin) && memory_limited) {
                        // memory issues, but we hit our floors
                        Log.d(TAG, "OK: swcodec " + actualMax + " >= min(" + advertised
                                + "," + tryMin + ") + memory limited");
                    } else {
                        Log.d(TAG, "NO: swcodec didn't meet criteria");
                        compliant = false;
                    }
                }

                if (!compliant) {
                    String codec = "<MediaCodec name=\"" + info.getName() + "\" type=\"" + type
                            + "\" >";
                    String limit = "    <Limit name=\"concurrent-instances\" max=\"" + actualMax
                            + "\" />";
                    xmlOverrides.append(codec);
                    xmlOverrides.append("\n");
                    xmlOverrides.append(limit);
                    xmlOverrides.append("\n");
                    xmlOverrides.append("</MediaCodec>\n");
                }
            }
        }

        if (xmlOverrides.length() > 0) {
            String failMessage = "In order to pass the test, please publish following "
                    + "codecs' concurrent instances limit in /etc/media_codecs.xml: \n";
            fail(failMessage + xmlOverrides);
        }
    }
}
