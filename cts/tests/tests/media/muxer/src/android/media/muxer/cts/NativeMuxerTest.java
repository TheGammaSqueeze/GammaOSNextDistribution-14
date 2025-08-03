/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.media.muxer.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.res.AssetFileDescriptor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.cts.MediaTestBase;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.Set;

@SmallTest
@RequiresDevice
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class NativeMuxerTest extends MediaTestBase {
    private static final String TAG = "NativeMuxerTest";

    private static final boolean sIsAtLeastS = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S);

    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    static {
        // Load jni on initialization.
        Log.i("@@@", "before loadlibrary");
        System.loadLibrary("ctsmediamuxertest_jni");
        Log.i("@@@", "after loadlibrary");
    }

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    private static AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        Preconditions.assertTestFileExists(MEDIA_DIR + res);
        File inpFile = new File(MEDIA_DIR + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    // check that native extractor behavior matches java extractor
    @Presubmit
    @NonMainlineTest
    @Test
    public void testMuxerAvc() throws Exception {
        // IMPORTANT: this file must not have B-frames
        testMuxer("video_1280x720_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    @NonMainlineTest
    @Test
    public void testMuxerH263() throws Exception {
        // IMPORTANT: this file must not have B-frames
        testMuxer("video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_11025hz.3gp");
    }

    @NonMainlineTest
    @Test
    public void testMuxerHevc() throws Exception {
        // IMPORTANT: this file must not have B-frames
        testMuxer("video_640x360_mp4_hevc_450kbps_no_b.mp4");
    }

    @NonMainlineTest
    @Test
    public void testMuxerVp8() throws Exception {
        testMuxer("bbb_s1_640x360_webm_vp8_2mbps_30fps_vorbis_5ch_320kbps_48000hz.webm");
    }

    @NonMainlineTest
    @Test
    public void testMuxerVp9() throws Exception {
        testMuxer("video_1280x720_webm_vp9_csd_309kbps_25fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    @NonMainlineTest
    @Test
    public void testMuxerVp9NoCsd() throws Exception {
        testMuxer("bbb_s1_640x360_webm_vp9_0p21_1600kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    @NonMainlineTest
    @Test
    public void testMuxerVp9Hdr() throws Exception {
        testMuxer("video_256x144_webm_vp9_hdr_83kbps_24fps.webm");
    }

    // We do not support MPEG-2 muxing as of yet
    @Ignore
    @Test
    public void SKIP_testMuxerMpeg2() throws Exception {
        // IMPORTANT: this file must not have B-frames
        testMuxer("video_176x144_mp4_mpeg2_105kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    @NonMainlineTest
    @Test
    public void testMuxerMpeg4() throws Exception {
        // IMPORTANT: this file must not have B-frames
        testMuxer("video_176x144_mp4_mpeg4_300kbps_25fps_aac_stereo_128kbps_44100hz.mp4");
    }

    @NonMainlineTest
    @Test
    public void testMuxerAv1() throws Exception {
        testMuxer("video_1280x720_mp4_av1_2000kbps_30fps_aac_stereo_128kbps_44100hz.mp4", true);
    }

    private void testMuxer(final String res) throws Exception {
        testMuxer(res, false);
    }

    private void testMuxer(final String res, boolean signalEos) throws Exception {
        boolean webm = res.endsWith("webm");
        Preconditions.assertTestFileExists(MEDIA_DIR + res);
        if (!MediaUtils.checkCodecsForResource(MEDIA_DIR + res)) {
            return; // skip
        }

        AssetFileDescriptor infd = getAssetFileDescriptorFor(res);

        File base = mContext.getExternalFilesDir(null);
        String tmpFile = base.getPath() + "/tmp.dat";
        Log.i("@@@", "using tmp file " + tmpFile);
        new File(tmpFile).delete();
        ParcelFileDescriptor out = ParcelFileDescriptor.open(new File(tmpFile),
                ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE);

        assertTrue(
                "muxer failed",
                testMuxerNative(
                        infd.getParcelFileDescriptor().getFd(),
                        infd.getStartOffset(),
                        infd.getLength(),
                        out.getFd(),
                        webm,
                        signalEos));

        // compare the original with the remuxed
        MediaExtractor org = new MediaExtractor();
        org.setDataSource(infd.getFileDescriptor(),
                infd.getStartOffset(), infd.getLength());

        MediaExtractor remux = new MediaExtractor();
        remux.setDataSource(out.getFileDescriptor());

        assertEquals("mismatched numer of tracks", org.getTrackCount(), remux.getTrackCount());
        // allow duration mismatch for webm files as ffmpeg does not consider the duration of the
        // last frame while libwebm (and our framework) does.
        final long maxDurationDiffUs = webm ? 50000 : 0; // 50ms for webm
        for (int i = 0; i < org.getTrackCount(); i++) {
            MediaFormat format1 = org.getTrackFormat(i);
            MediaFormat format2 = remux.getTrackFormat(i);
            Log.i("@@@", "org: " + format1);
            Log.i("@@@", "remux: " + format2);
            assertTrue("different formats", compareFormats(format1, format2, maxDurationDiffUs));
        }

        org.release();
        remux.release();

        Preconditions.assertTestFileExists(MEDIA_DIR + res);
        MediaPlayer player1 =
                MediaPlayer.create(mContext, Uri.fromFile(new File(MEDIA_DIR + res)));
        MediaPlayer player2 = MediaPlayer.create(mContext, Uri.parse("file://" + tmpFile));
        assertEquals("duration is different",
                player1.getDuration(), player2.getDuration(), maxDurationDiffUs * 0.001);
        player1.release();
        player2.release();
        new File(tmpFile).delete();
    }

    private String hexString(ByteBuffer buf) {
        if (buf == null) {
            return "(null)";
        }
        final char[] digits =
                {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

        StringBuilder hex = new StringBuilder();
        for (int i = buf.position(); i < buf.limit(); ++i) {
            byte c = buf.get(i);
            hex.append(digits[(c >> 4) & 0xf]);
            hex.append(digits[c & 0xf]);
        }
        return hex.toString();
    }

    /**
     * returns: null if key is in neither formats, true if they match and false otherwise
     */
    private Boolean compareByteBufferInFormats(MediaFormat f1, MediaFormat f2, String key) {
        ByteBuffer bufF1 = f1.containsKey(key) ? f1.getByteBuffer(key) : null;
        ByteBuffer bufF2 = f2.containsKey(key) ? f2.getByteBuffer(key) : null;
        if (bufF1 == null && bufF2 == null) {
            return null;
        }
        if (bufF1 == null || !bufF1.equals(bufF2)) {
            Log.i("@@@", "org " + key + ": " + hexString(bufF1));
            Log.i("@@@", "rmx " + key + ": " + hexString(bufF2));
            return false;
        }
        return true;
    }

    private boolean compareFormats(MediaFormat f1, MediaFormat f2, long maxDurationDiffUs) {
        final String KEY_DURATION = MediaFormat.KEY_DURATION;

        // allow some difference in durations
        if (maxDurationDiffUs > 0
                && f1.containsKey(KEY_DURATION) && f2.containsKey(KEY_DURATION)
                && Math.abs(f1.getLong(KEY_DURATION)
                - f2.getLong(KEY_DURATION)) <= maxDurationDiffUs) {
            f2.setLong(KEY_DURATION, f1.getLong(KEY_DURATION));
        }

        // verify hdr-static-info
        if (Boolean.FALSE.equals(compareByteBufferInFormats(f1, f2, "hdr-static-info"))) {
            return false;
        }

        // verify CSDs
        for (int i = 0; ; ++i) {
            String key = "csd-" + i;
            Boolean match = compareByteBufferInFormats(f1, f2, key);
            if (match == null) {
                break;
            } else if (!match) {
                return false;
            }
        }

        // before S, mpeg4 writers jammed a fixed SAR value into the output;
        // this was fixed in S
        if (!sIsAtLeastS) {
            if (f1.containsKey(MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT)
                    && f2.containsKey(MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT)) {
                f2.setInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT,
                        f1.getInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_HEIGHT));
            }
            if (f1.containsKey(MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH)
                    && f2.containsKey(MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH)) {
                f2.setInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH,
                        f1.getInteger(MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH));
            }
        }

        // look for f2 (the new) being a superset (>=) of f1 (the original)
        // ensure that all of our fields in f1 appear in f2 with the same
        // value. We allow f2 to contain extra fields.
        Set<String> keys = f1.getKeys();
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            if (!f2.containsKey(key)) {
                return false;
            }
            int f1Type = f1.getValueTypeForKey(key);
            if (f1Type != f2.getValueTypeForKey(key)) {
                return false;
            }
            switch (f1Type) {
                case MediaFormat.TYPE_INTEGER:
                    int f1Int = f1.getInteger(key);
                    int f2Int = f2.getInteger(key);
                    if (f1Int != f2Int) {
                        return false;
                    }
                    break;
                case MediaFormat.TYPE_LONG:
                    long f1Long = f1.getLong(key);
                    long f2Long = f2.getLong(key);
                    if (f1Long != f2Long) {
                        return false;
                    }
                    break;
                case MediaFormat.TYPE_FLOAT:
                    float f1Float = f1.getFloat(key);
                    float f2Float = f2.getFloat(key);
                    if (f1Float != f2Float) {
                        return false;
                    }
                    break;
                case MediaFormat.TYPE_STRING:
                    String f1String = f1.getString(key);
                    String f2String = f2.getString(key);
                    if (!f1String.equals(f2String)) {
                        return false;
                    }
                    break;
                case MediaFormat.TYPE_BYTE_BUFFER:
                    ByteBuffer f1ByteBuffer = f1.getByteBuffer(key);
                    ByteBuffer f2ByteBuffer = f2.getByteBuffer(key);
                    if (!f1ByteBuffer.equals(f2ByteBuffer)) {
                        return false;
                    }
                    break;
                default:
                    return false;
            }
        }

        // repeat for getFeatures
        // (which we don't use in this test, but include for completeness)
        Set<String> features = f1.getFeatures();
        for (String key : features) {
            if (key == null) {
                continue;
            }
            if (!f2.containsKey(key)) {
                return false;
            }
            int f1Type = f1.getValueTypeForKey(key);
            if (f1Type != f2.getValueTypeForKey(key)) {
                return false;
            }
            switch (f1Type) {
                case MediaFormat.TYPE_INTEGER:
                    int f1Int = f1.getInteger(key);
                    int f2Int = f2.getInteger(key);
                    if (f1Int != f2Int) {
                        return false;
                    }
                    break;
                case MediaFormat.TYPE_LONG:
                    long f1Long = f1.getLong(key);
                    long f2Long = f2.getLong(key);
                    if (f1Long != f2Long) {
                        return false;
                    }
                    break;
                case MediaFormat.TYPE_FLOAT:
                    float f1Float = f1.getFloat(key);
                    float f2Float = f2.getFloat(key);
                    if (f1Float != f2Float) {
                        return false;
                    }
                    break;
                case MediaFormat.TYPE_STRING:
                    String f1String = f1.getString(key);
                    String f2String = f2.getString(key);
                    if (!f1String.equals(f2String)) {
                        return false;
                    }
                    break;
                case MediaFormat.TYPE_BYTE_BUFFER:
                    ByteBuffer f1ByteBuffer = f1.getByteBuffer(key);
                    ByteBuffer f2ByteBuffer = f2.getByteBuffer(key);
                    if (!f1ByteBuffer.equals(f2ByteBuffer)) {
                        return false;
                    }
                    break;
                default:
                    return false;
            }
        }

        // not otherwise disqualified
        return true;
    }

    private static native boolean testMuxerNative(
            int in, long inoffset, long insize, int out, boolean webm, boolean signaleos);
}
