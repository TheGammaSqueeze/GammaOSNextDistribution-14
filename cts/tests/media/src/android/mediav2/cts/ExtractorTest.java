/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.mediav2.common.cts.CodecTestBase.hasDecoder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecTestBase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.util.Log;
import android.webkit.cts.CtsTestServer;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.Preconditions;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;

class TestMediaDataSource extends MediaDataSource {
    private static final String LOG_TAG = TestMediaDataSource.class.getSimpleName();
    private static final boolean ENABLE_LOGS = false;
    private byte[] mData;
    private boolean mFatalGetSize;
    private boolean mFatalReadAt;
    private boolean mIsClosed = false;

    static TestMediaDataSource fromString(String inpPath, boolean failSize, boolean failRead)
            throws IOException {
        try (FileInputStream fInp = new FileInputStream(inpPath)) {
            int size = (int) new File(inpPath).length();
            byte[] data = new byte[size];
            fInp.read(data, 0, size);
            return new TestMediaDataSource(data, failSize, failRead);
        }
    }

    private TestMediaDataSource(byte[] data, boolean fatalGetSize, boolean fatalReadAt) {
        mData = data;
        mFatalGetSize = fatalGetSize;
        mFatalReadAt = fatalReadAt;
    }

    @Override
    public synchronized int readAt(long srcOffset, byte[] buffer, int dstOffset, int size)
            throws IOException {
        if (mFatalReadAt) {
            throw new IOException("malformed media data source");
        }
        if (srcOffset >= mData.length) {
            return -1;
        }
        if (srcOffset + size > mData.length) {
            size = mData.length - (int) srcOffset;
        }
        System.arraycopy(mData, (int) srcOffset, buffer, dstOffset, size);
        return size;
    }

    @Override
    public synchronized long getSize() throws IOException {
        if (mFatalGetSize) {
            throw new IOException("malformed media data source");
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "getSize: " + mData.length);
        }
        return mData.length;
    }

    @Override
    public synchronized void close() {
        mIsClosed = true;
    }

    public boolean isClosed() {
        return mIsClosed;
    }
}

@RunWith(Enclosed.class)
public class ExtractorTest {
    private static final String LOG_TAG = ExtractorTest.class.getSimpleName();
    private static final boolean ENABLE_LOGS = false;
    private static final int MAX_SAMPLE_SIZE = 4 * 1024 * 1024;
    private static final String EXT_SEL_KEY = "ext-sel";
    static private final List<String> codecListforTypeMp4 =
            Arrays.asList(MediaFormat.MIMETYPE_AUDIO_MPEG, MediaFormat.MIMETYPE_AUDIO_AAC,
                    MediaFormat.MIMETYPE_AUDIO_FLAC, MediaFormat.MIMETYPE_AUDIO_VORBIS,
                    MediaFormat.MIMETYPE_AUDIO_OPUS, MediaFormat.MIMETYPE_VIDEO_MPEG2,
                    MediaFormat.MIMETYPE_VIDEO_MPEG4, MediaFormat.MIMETYPE_VIDEO_H263,
                    MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC);
    static private final List<String> codecListforTypeWebm =
            Arrays.asList(MediaFormat.MIMETYPE_AUDIO_VORBIS, MediaFormat.MIMETYPE_AUDIO_OPUS,
                    MediaFormat.MIMETYPE_VIDEO_VP8, MediaFormat.MIMETYPE_VIDEO_VP9);
    static private final List<String> codecListforType3gp =
            Arrays.asList(MediaFormat.MIMETYPE_AUDIO_AAC, MediaFormat.MIMETYPE_AUDIO_AMR_NB,
                    MediaFormat.MIMETYPE_AUDIO_AMR_WB, MediaFormat.MIMETYPE_VIDEO_MPEG4,
                    MediaFormat.MIMETYPE_VIDEO_H263, MediaFormat.MIMETYPE_VIDEO_AVC);
    static private final List<String> codecListforTypeMkv =
            Arrays.asList(MediaFormat.MIMETYPE_AUDIO_MPEG, MediaFormat.MIMETYPE_AUDIO_AAC,
                    MediaFormat.MIMETYPE_AUDIO_FLAC, MediaFormat.MIMETYPE_AUDIO_VORBIS,
                    MediaFormat.MIMETYPE_AUDIO_OPUS, MediaFormat.MIMETYPE_VIDEO_MPEG2,
                    MediaFormat.MIMETYPE_VIDEO_MPEG4, MediaFormat.MIMETYPE_VIDEO_H263,
                    MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                    MediaFormat.MIMETYPE_VIDEO_VP8, MediaFormat.MIMETYPE_VIDEO_VP9);
    static private final List<String> codecListforTypeOgg =
            Arrays.asList(MediaFormat.MIMETYPE_AUDIO_VORBIS, MediaFormat.MIMETYPE_AUDIO_OPUS);
    static private final List<String> codecListforTypeTs =
            Arrays.asList(MediaFormat.MIMETYPE_AUDIO_AAC, MediaFormat.MIMETYPE_VIDEO_MPEG2,
                    MediaFormat.MIMETYPE_VIDEO_AVC);
    static private final List<String> codecListforTypePs =
            Arrays.asList(MediaFormat.MIMETYPE_VIDEO_MPEG2);
    static private final List<String> codecListforTypeRaw =
            Arrays.asList(MediaFormat.MIMETYPE_AUDIO_AAC, MediaFormat.MIMETYPE_AUDIO_FLAC,
                    MediaFormat.MIMETYPE_AUDIO_MPEG, MediaFormat.MIMETYPE_AUDIO_AMR_NB,
                    MediaFormat.MIMETYPE_AUDIO_AMR_WB, MediaFormat.MIMETYPE_AUDIO_RAW);
    static private final List<String> codecListforTypeWav =
            Arrays.asList(MediaFormat.MIMETYPE_AUDIO_RAW,  MediaFormat.MIMETYPE_AUDIO_G711_ALAW,
                    MediaFormat.MIMETYPE_AUDIO_G711_MLAW,  MediaFormat.MIMETYPE_AUDIO_MSGSM);
    // List of codecs that are not required to be supported as per CDD but are tested
    static private final List<String> codecListSupp =
            Arrays.asList(MediaFormat.MIMETYPE_VIDEO_AV1, MediaFormat.MIMETYPE_AUDIO_AC3,
                    MediaFormat.MIMETYPE_AUDIO_AC4, MediaFormat.MIMETYPE_AUDIO_EAC3);
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static String extSel;

    static {
        android.os.Bundle args = InstrumentationRegistry.getArguments();
        final String defSel = "mp4;webm;3gp;mkv;ogg;supp;raw;ts;ps;wav";
        extSel = (null == args.getString(EXT_SEL_KEY)) ? defSel : args.getString(EXT_SEL_KEY);
    }

    private static boolean shouldRunTest(String mediaType) {
        boolean result = false;
        if ((extSel.contains("mp4") && codecListforTypeMp4.contains(mediaType))
                || (extSel.contains("webm") && codecListforTypeWebm.contains(mediaType))
                || (extSel.contains("3gp") && codecListforType3gp.contains(mediaType))
                || (extSel.contains("mkv") && codecListforTypeMkv.contains(mediaType))
                || (extSel.contains("ogg") && codecListforTypeOgg.contains(mediaType))
                || (extSel.contains("ts") && codecListforTypeTs.contains(mediaType))
                || (extSel.contains("ps") && codecListforTypePs.contains(mediaType))
                || (extSel.contains("raw") && codecListforTypeRaw.contains(mediaType))
                || (extSel.contains("wav") && codecListforTypeWav.contains(mediaType))
                || (extSel.contains("supp") && codecListSupp.contains(mediaType))) {
            result = true;
        }
        return result;
    }

    private static boolean isExtractorOKonEOS(MediaExtractor extractor) {
        return extractor.getSampleTrackIndex() < 0 && extractor.getSampleSize() < 0 &&
                extractor.getSampleFlags() < 0 && extractor.getSampleTime() < 0;
    }

    private static boolean isSampleInfoIdentical(MediaCodec.BufferInfo refSample,
            MediaCodec.BufferInfo testSample) {
        return refSample.flags == testSample.flags && refSample.size == testSample.size &&
                refSample.presentationTimeUs == testSample.presentationTimeUs;
    }

    private static boolean isSampleInfoValidAndIdentical(MediaCodec.BufferInfo refSample,
            MediaCodec.BufferInfo testSample) {
        return refSample.flags == testSample.flags && refSample.size == testSample.size &&
                Math.abs(refSample.presentationTimeUs - testSample.presentationTimeUs) <= 1 &&
                refSample.flags >= 0 && refSample.size >= 0 && refSample.presentationTimeUs >= 0;
    }

    static boolean isCSDIdentical(MediaFormat refFormat, MediaFormat testFormat) {
        String mediaType = refFormat.getString(MediaFormat.KEY_MIME);
        for (int i = 0; ; i++) {
            String csdKey = "csd-" + i;
            boolean refHasCSD = refFormat.containsKey(csdKey);
            boolean testHasCSD = testFormat.containsKey(csdKey);
            if (refHasCSD != testHasCSD) {
                if (ENABLE_LOGS) {
                    Log.w(LOG_TAG, "error, ref fmt has CSD: " + refHasCSD + " test fmt has CSD: " +
                            testHasCSD);
                }
                return false;
            }
            if (refHasCSD) {
                Log.v(LOG_TAG, mediaType + " has " + csdKey);
                ByteBuffer r = refFormat.getByteBuffer(csdKey);
                ByteBuffer t = testFormat.getByteBuffer(csdKey);
                if (!r.equals(t)) {
                    if (ENABLE_LOGS) {
                        Log.w(LOG_TAG, "ref CSD and test CSD buffers are not identical");
                    }
                    return false;
                }
            } else break;
        }
        return true;
    }

    static boolean isFormatSimilar(MediaFormat refFormat, MediaFormat testFormat) {
        String refMediaType = refFormat.getString(MediaFormat.KEY_MIME);
        String testMediaType = testFormat.getString(MediaFormat.KEY_MIME);

        if (!refMediaType.equals(testMediaType)) return false;
        if (refFormat.getLong(MediaFormat.KEY_DURATION) !=
                    testFormat.getLong(MediaFormat.KEY_DURATION)) {
            Log.w(LOG_TAG, "Duration mismatches ref / test = " +
                                   refFormat.getLong(MediaFormat.KEY_DURATION) + " / " +
                                   testFormat.getLong(MediaFormat.KEY_DURATION));
            // TODO (b/163477410)(b/163478168)
//            return false;
        }
        if (!isCSDIdentical(refFormat, testFormat)) return false;
        if (refMediaType.startsWith("audio/")) {
            if (refFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) !=
                        testFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) return false;
            if (refFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) !=
                        testFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)) return false;
        } else if (refMediaType.startsWith("video/")) {
            if (refFormat.getInteger(MediaFormat.KEY_WIDTH) !=
                        testFormat.getInteger(MediaFormat.KEY_WIDTH)) return false;
            if (refFormat.getInteger(MediaFormat.KEY_HEIGHT) !=
                        testFormat.getInteger(MediaFormat.KEY_HEIGHT)) return false;
        }
        return true;
    }

    private static boolean isMediaSimilar(MediaExtractor refExtractor, MediaExtractor testExtractor,
            String mediaType, int sampleLimit) {
        ByteBuffer refBuffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE);
        ByteBuffer testBuffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE);

        int noOfTracksMatched = 0;
        for (int refTrackID = 0; refTrackID < refExtractor.getTrackCount(); refTrackID++) {
            MediaFormat refFormat = refExtractor.getTrackFormat(refTrackID);
            String refMediaType = refFormat.getString(MediaFormat.KEY_MIME);
            if (mediaType != null && !refMediaType.equals(mediaType)) {
                continue;
            }
            for (int testTrackID = 0; testTrackID < testExtractor.getTrackCount(); testTrackID++) {
                MediaFormat testFormat = testExtractor.getTrackFormat(testTrackID);
                if (!isFormatSimilar(refFormat, testFormat)) {
                    continue;
                }
                refExtractor.selectTrack(refTrackID);
                testExtractor.selectTrack(testTrackID);

                MediaCodec.BufferInfo refSampleInfo = new MediaCodec.BufferInfo();
                MediaCodec.BufferInfo testSampleInfo = new MediaCodec.BufferInfo();
                boolean areTracksIdentical = true;
                for (int frameCount = 0; ; frameCount++) {
                    refSampleInfo.set(0, (int) refExtractor.getSampleSize(),
                            refExtractor.getSampleTime(), refExtractor.getSampleFlags());
                    testSampleInfo.set(0, (int) testExtractor.getSampleSize(),
                            testExtractor.getSampleTime(), testExtractor.getSampleFlags());
                    if (!isSampleInfoValidAndIdentical(refSampleInfo, testSampleInfo)) {
                        if (ENABLE_LOGS) {
                            Log.d(LOG_TAG,
                                    " Mediatype: " + refMediaType + " mismatch for sample: "
                                            + frameCount);
                            Log.d(LOG_TAG, " flags exp/got: " + refSampleInfo.flags + '/'
                                    + testSampleInfo.flags);
                            Log.d(LOG_TAG, " size exp/got: " + refSampleInfo.size + '/'
                                    + testSampleInfo.size);
                            Log.d(LOG_TAG, " ts exp/got: " + refSampleInfo.presentationTimeUs
                                    + '/' + testSampleInfo.presentationTimeUs);
                        }
                        areTracksIdentical = false;
                        break;
                    }
                    int refSz = refExtractor.readSampleData(refBuffer, 0);
                    if (refSz != refSampleInfo.size) {
                        if (ENABLE_LOGS) {
                            Log.d(LOG_TAG, "Mediatype: " + refMediaType + " Size exp/got: "
                                    + refSampleInfo.size + '/' + refSz);
                        }
                        areTracksIdentical = false;
                        break;
                    }
                    int testSz = testExtractor.readSampleData(testBuffer, 0);
                    if (testSz != testSampleInfo.size) {
                        if (ENABLE_LOGS) {
                            Log.d(LOG_TAG, "Mediatype: " + refMediaType + " Size exp/got: "
                                    + testSampleInfo.size + '/' + testSz);
                        }
                        areTracksIdentical = false;
                        break;
                    }
                    int trackIndex = refExtractor.getSampleTrackIndex();
                    if (trackIndex != refTrackID) {
                        if (ENABLE_LOGS) {
                            Log.d(LOG_TAG, "Mediatype: " + refMediaType + " TrackID exp/got: "
                                    + refTrackID + '/' + trackIndex);
                        }
                        areTracksIdentical = false;
                        break;
                    }
                    trackIndex = testExtractor.getSampleTrackIndex();
                    if (trackIndex != testTrackID) {
                        if (ENABLE_LOGS) {
                            Log.d(LOG_TAG, "Mediatype: " + refMediaType + " TrackID exp/got: "
                                    + testTrackID + '/' + trackIndex);
                        }
                        areTracksIdentical = false;
                        break;
                    }
                    if (!testBuffer.equals(refBuffer)) {
                        if (ENABLE_LOGS) {
                            Log.d(LOG_TAG,
                                    "Mediatype: " + refMediaType + " sample data is not identical");
                        }
                        areTracksIdentical = false;
                        break;
                    }
                    boolean haveRefSamples = refExtractor.advance();
                    boolean haveTestSamples = testExtractor.advance();
                    if (haveRefSamples != haveTestSamples) {
                        if (ENABLE_LOGS) {
                            Log.d(LOG_TAG, "Mediatype: " + refMediaType + " Mismatch "
                                    + "in sampleCount");
                        }
                        areTracksIdentical = false;
                        break;
                    }

                    if (!haveRefSamples && !isExtractorOKonEOS(refExtractor)) {
                        if (ENABLE_LOGS) {
                            Log.d(LOG_TAG,
                                    "Mediatype: " + refMediaType
                                            + " calls post advance() are not OK");
                        }
                        areTracksIdentical = false;
                        break;
                    }
                    if (!haveTestSamples && !isExtractorOKonEOS(testExtractor)) {
                        if (ENABLE_LOGS) {
                            Log.d(LOG_TAG,
                                    "Mediatype: " + refMediaType
                                            + " calls post advance() are not OK");
                        }
                        areTracksIdentical = false;
                        break;
                    }
                    if (ENABLE_LOGS) {
                        Log.v(LOG_TAG, "Mediatype: " + refMediaType + " Sample: " + frameCount
                                + " flags: " + refSampleInfo.flags + " size: " + refSampleInfo.size
                                + " ts: " + refSampleInfo.presentationTimeUs);
                    }
                    if (!haveRefSamples || frameCount >= sampleLimit) {
                        break;
                    }
                }
                testExtractor.unselectTrack(testTrackID);
                refExtractor.unselectTrack(refTrackID);
                if (areTracksIdentical) {
                    noOfTracksMatched++;
                    break;
                }
            }
            if (mediaType != null && noOfTracksMatched > 0) break;
        }
        if (mediaType == null) {
            return noOfTracksMatched == refExtractor.getTrackCount();
        } else {
            return noOfTracksMatched > 0;
        }
    }

    private static long readAllData(MediaExtractor extractor, String mediaType, int sampleLimit) {
        CRC32 checksum = new CRC32();
        ByteBuffer buffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE);
        int tracksSelected = 0;
        for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
            MediaFormat format = extractor.getTrackFormat(trackID);
            String srcMediaType = format.getString(MediaFormat.KEY_MIME);
            if (mediaType != null && !srcMediaType.equals(mediaType)) {
                continue;
            }
            extractor.selectTrack(trackID);
            tracksSelected++;
            if (srcMediaType.startsWith("audio/")) {
                buffer.putInt(0);
                buffer.putInt(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                buffer.putInt(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            } else if (srcMediaType.startsWith("video/")) {
                buffer.putInt(1);
                buffer.putInt(format.getInteger(MediaFormat.KEY_WIDTH));
                buffer.putInt(format.getInteger(MediaFormat.KEY_HEIGHT));
            } else {
                buffer.putInt(2);
            }
            buffer.putLong(format.getLong(MediaFormat.KEY_DURATION));
            for (int i = 0; ; i++) {
                String csdKey = "csd-" + i;
                if (format.containsKey(csdKey)) {
                    checksum.update(format.getByteBuffer(csdKey));
                } else break;
            }
        }
        assertTrue(tracksSelected > 0);
        buffer.flip();
        checksum.update(buffer);

        MediaCodec.BufferInfo sampleInfo = new MediaCodec.BufferInfo();
        for (int sampleCount = 0; sampleCount < sampleLimit; sampleCount++) {
            sampleInfo.set(0, (int) extractor.getSampleSize(), extractor.getSampleTime(),
                    extractor.getSampleFlags());
            extractor.readSampleData(buffer, 0);
            checksum.update(buffer);
            assertEquals(sampleInfo.size, buffer.limit());
            assertTrue(sampleInfo.flags != -1);
            assertTrue(sampleInfo.presentationTimeUs != -1);
            buffer.position(0);
            buffer.putInt(sampleInfo.size)
                    .putInt(sampleInfo.flags)
                    .putLong(sampleInfo.presentationTimeUs);
            buffer.flip();
            checksum.update(buffer);
            sampleCount++;
            if (!extractor.advance()) {
                assertTrue(isExtractorOKonEOS(extractor));
                break;
            }
        }
        for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
            extractor.unselectTrack(trackID);
        }
        return checksum.getValue();
    }

    private static native long nativeReadAllData(String srcPath, String mediaType, int sampleLimit,
            String[] keys, String[] values, boolean isSrcUrl);

    /**
     * Tests setDataSource(...) Api by observing the extractor behavior after its successful
     * instantiation using a media stream.
     */
    @ApiTest(apis = {"android.media.MediaExtractor#setDataSource", "AMediaExtractor_setDataSource"})
    @SmallTest
    public static class SetDataSourceTest {
        @Rule
        public TestName testName = new TestName();

        private static final String INPUT_MEDIA = "ForBiggerEscapes.mp4";
        private static final String RES_STRING = "raw/forbiggerescapes";
        private CtsTestServer mWebServer;
        private String mInpMediaUrl;
        private MediaExtractor mRefExtractor;

        static {
            System.loadLibrary("ctsmediav2extractor_jni");
        }

        @Before
        public void setUp() throws IOException {
            mRefExtractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            mRefExtractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                mWebServer = new CtsTestServer(context);
                mInpMediaUrl = mWebServer.getAssetUrl(RES_STRING);
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }

        @After
        public void tearDown() {
            mRefExtractor.release();
            mRefExtractor = null;
            mWebServer.shutdown();
        }

        private static boolean areMetricsIdentical(MediaExtractor refExtractor,
                MediaExtractor testExtractor) {
            PersistableBundle bundle = refExtractor.getMetrics();
            int refNumTracks = bundle.getInt(MediaExtractor.MetricsConstants.TRACKS);
            String refFormat = bundle.getString(MediaExtractor.MetricsConstants.FORMAT);
            String refMediaType = bundle.getString(MediaExtractor.MetricsConstants.MIME_TYPE);
            bundle = testExtractor.getMetrics();
            int testNumTracks = bundle.getInt(MediaExtractor.MetricsConstants.TRACKS);
            String testFormat = bundle.getString(MediaExtractor.MetricsConstants.FORMAT);
            String testMediaType = bundle.getString(MediaExtractor.MetricsConstants.MIME_TYPE);
            boolean result = testNumTracks == refNumTracks && testFormat.equals(refFormat) &&
                    testMediaType.equals(refMediaType);
            if (ENABLE_LOGS) {
                Log.d(LOG_TAG, " NumTracks exp/got: " + refNumTracks + '/' + testNumTracks);
                Log.d(LOG_TAG, " Format exp/got: " + refFormat + '/' + testFormat);
                Log.d(LOG_TAG, " Mediatype exp/got: " + refMediaType + '/' + testMediaType);
            }
            return result;
        }

        private static boolean isSeekOk(MediaExtractor refExtractor, MediaExtractor testExtractor) {
            final long maxEstDuration = 14000000;
            final int MAX_SEEK_POINTS = 7;
            final long mSeed = 0x12b9b0a1;
            final Random randNum = new Random(mSeed);
            MediaCodec.BufferInfo refSampleInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo testSampleInfo = new MediaCodec.BufferInfo();
            boolean result = true;
            for (int trackID = 0; trackID < refExtractor.getTrackCount() && result; trackID++) {
                refExtractor.selectTrack(trackID);
                testExtractor.selectTrack(trackID);
                for (int i = 0; i < MAX_SEEK_POINTS && result; i++) {
                    long pts = (long) (randNum.nextDouble() * maxEstDuration);
                    for (int mode = MediaExtractor.SEEK_TO_PREVIOUS_SYNC;
                         mode <= MediaExtractor.SEEK_TO_CLOSEST_SYNC; mode++) {
                        refExtractor.seekTo(pts, mode);
                        testExtractor.seekTo(pts, mode);
                        refSampleInfo.set(0, (int) refExtractor.getSampleSize(),
                                refExtractor.getSampleTime(), refExtractor.getSampleFlags());
                        testSampleInfo.set(0, (int) testExtractor.getSampleSize(),
                                testExtractor.getSampleTime(), testExtractor.getSampleFlags());
                        result = isSampleInfoIdentical(refSampleInfo, testSampleInfo);
                        int refTrackIdx = refExtractor.getSampleTrackIndex();
                        int testTrackIdx = testExtractor.getSampleTrackIndex();
                        result &= (refTrackIdx == testTrackIdx);
                        if (ENABLE_LOGS) {
                            Log.d(LOG_TAG, " mode/pts/trackId:" + mode + "/" + pts + "/" + trackID);
                            Log.d(LOG_TAG, " trackId exp/got: " + refTrackIdx + '/' + testTrackIdx);
                            Log.d(LOG_TAG, " flags exp/got: " +
                                    refSampleInfo.flags + '/' + testSampleInfo.flags);
                            Log.d(LOG_TAG, " size exp/got: " +
                                    refSampleInfo.size + '/' + testSampleInfo.size);
                            Log.d(LOG_TAG, " ts exp/got: " + refSampleInfo.presentationTimeUs +
                                    '/' + testSampleInfo.presentationTimeUs);
                        }
                    }
                }
                refExtractor.unselectTrack(trackID);
                testExtractor.unselectTrack(trackID);
            }
            return result;
        }

        @Test
        public void testAssetFD() throws IOException {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            File inpFile = new File(MEDIA_DIR + INPUT_MEDIA);
            MediaExtractor testExtractor = new MediaExtractor();
            try (ParcelFileDescriptor parcelFD = ParcelFileDescriptor
                    .open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
                 AssetFileDescriptor afd = new AssetFileDescriptor(parcelFD, 0,
                         AssetFileDescriptor.UNKNOWN_LENGTH)) {
                testExtractor.setDataSource(afd);
            }
            assertTrue(testExtractor.getCachedDuration() < 0);
            if (!isMediaSimilar(mRefExtractor, testExtractor, null, Integer.MAX_VALUE) ||
                    !areMetricsIdentical(mRefExtractor, testExtractor) ||
                    !isSeekOk(mRefExtractor, testExtractor)) {
                fail("setDataSource failed: " + testName.getMethodName());
            }
            testExtractor.release();
        }

        @Test
        public void testFileDescriptor() throws IOException {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            File inpFile = new File(MEDIA_DIR + INPUT_MEDIA);
            MediaExtractor testExtractor = new MediaExtractor();
            try (FileInputStream fInp = new FileInputStream(inpFile)) {
                testExtractor.setDataSource(fInp.getFD());
            }
            assertTrue(testExtractor.getCachedDuration() < 0);
            if (!isMediaSimilar(mRefExtractor, testExtractor, null, Integer.MAX_VALUE) ||
                    !areMetricsIdentical(mRefExtractor, testExtractor) ||
                    !isSeekOk(mRefExtractor, testExtractor)) {
                fail("setDataSource failed: " + testName.getMethodName());
            }
            long sdkChecksum = readAllData(testExtractor, null, Integer.MAX_VALUE);
            long ndkChecksum = nativeReadAllData(MEDIA_DIR + INPUT_MEDIA, "",
                    Integer.MAX_VALUE, null, null, false);
            testExtractor.release();
            assertEquals("SDK and NDK checksums mismatch", sdkChecksum, ndkChecksum);
        }

        @Test
        public void testFileDescriptorLenOffset() throws IOException {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            File inpFile = new File(MEDIA_DIR + INPUT_MEDIA);
            File outFile = File.createTempFile("temp", ".out");
            byte[] garbageAppend = "PrefixGarbage".getBytes();
            try (FileInputStream fInp = new FileInputStream(inpFile);
                 FileOutputStream fOut = new FileOutputStream(outFile)) {
                fOut.write(garbageAppend);
                byte[] data = new byte[(int) new File(inpFile.toString()).length()];
                if (fInp.read(data) == -1) {
                    fail("Failed to read input file");
                }
                fOut.write(data);
                fOut.write(garbageAppend);
            }
            MediaExtractor testExtractor = new MediaExtractor();
            try (FileInputStream fInp = new FileInputStream(outFile)) {
                testExtractor.setDataSource(fInp.getFD(), garbageAppend.length,
                        inpFile.length());
            }
            assertTrue(testExtractor.getCachedDuration() < 0);
            if (!isMediaSimilar(mRefExtractor, testExtractor, null, Integer.MAX_VALUE) ||
                    !areMetricsIdentical(mRefExtractor, testExtractor) ||
                    !isSeekOk(mRefExtractor, testExtractor)) {
                fail("setDataSource failed: " + testName.getMethodName());
            }
            testExtractor.release();
            outFile.delete();
        }

        @Test
        public void testMediaDataSource() throws Exception {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            TestMediaDataSource dataSource =
                    TestMediaDataSource.fromString(MEDIA_DIR + INPUT_MEDIA, false, false);
            MediaExtractor testExtractor = new MediaExtractor();
            testExtractor.setDataSource(dataSource);
            assertTrue(testExtractor.getCachedDuration() < 0);
            if (!isMediaSimilar(mRefExtractor, testExtractor, null, Integer.MAX_VALUE) ||
                    !areMetricsIdentical(mRefExtractor, testExtractor) ||
                    !isSeekOk(mRefExtractor, testExtractor)) {
                fail("setDataSource failed: " + testName.getMethodName());
            }
            testExtractor.release();
            assertTrue(dataSource.isClosed());
        }

        @Test
        public void testContextUri() throws IOException {
            Context context = InstrumentationRegistry.getInstrumentation().getContext();
            String path = "android.resource://android.mediav2.cts/" + RES_STRING;
            MediaExtractor testExtractor = new MediaExtractor();
            testExtractor.setDataSource(context, Uri.parse(path), null);
            assertTrue(testExtractor.getCachedDuration() < 0);
            if (!isMediaSimilar(mRefExtractor, testExtractor, null, Integer.MAX_VALUE) ||
                    !areMetricsIdentical(mRefExtractor, testExtractor) ||
                    !isSeekOk(mRefExtractor, testExtractor)) {
                fail("setDataSource failed: " + testName.getMethodName());
            }
            testExtractor.release();
        }

        private void checkExtractorOkForUrlDS(Map<String, String> headers) throws Exception {
            MediaExtractor testExtractor = new MediaExtractor();
            testExtractor.setDataSource(mInpMediaUrl, headers);
            HttpRequest req = mWebServer.getLastAssetRequest(RES_STRING);
            if (headers != null) {
                for (String key : headers.keySet()) {
                    String value = headers.get(key);
                    Header[] header = req.getHeaders(key);
                    assertTrue(
                            "expecting " + key + ":" + value + ", saw " + Arrays.toString(header),
                            header.length == 1 && header[0].getValue().equals(value));
                }
            }
            if (!isMediaSimilar(mRefExtractor, testExtractor, null, Integer.MAX_VALUE) ||
                    !areMetricsIdentical(mRefExtractor, testExtractor) ||
                    !isSeekOk(mRefExtractor, testExtractor)) {
                fail("setDataSource failed: " + testName.getMethodName());
            }
            testExtractor.selectTrack(0);
            for (int idx = 0; ; idx++) {
                if ((idx & (idx - 1)) == 0) {
                    long cachedDuration = testExtractor.getCachedDuration();
                    if (ENABLE_LOGS) {
                        Log.v(LOG_TAG, "cachedDuration at frame: " + idx + " is:" + cachedDuration);
                    }
                    assertTrue("cached duration should be non-negative", cachedDuration >= 0);
                }
                if (!testExtractor.advance()) break;
            }
            assertTrue(testExtractor.hasCacheReachedEndOfStream());
            testExtractor.unselectTrack(0);
            testExtractor.release();
        }

        @Test
        public void testUrlDataSource() throws Exception {
            checkExtractorOkForUrlDS(null);

            Map<String, String> headers = new HashMap<>();
            checkExtractorOkForUrlDS(headers);

            String[] keys = new String[]{"From", "Client", "Location"};
            String[] values = new String[]{"alcor@bigdipper.asm", "CtsTestServer", "UrsaMajor"};
            for (int i = 0; i < keys.length; i++) {
                headers.put(keys[i], values[i]);
            }
            checkExtractorOkForUrlDS(headers);

            MediaExtractor testExtractor = new MediaExtractor();
            testExtractor.setDataSource(mInpMediaUrl, headers);
            long sdkChecksum = readAllData(testExtractor, null, Integer.MAX_VALUE);
            testExtractor.release();
            long ndkChecksum = nativeReadAllData(mInpMediaUrl, "", Integer.MAX_VALUE, keys,
                    values, true);
            assertEquals("SDK and NDK checksums mismatch", sdkChecksum, ndkChecksum);
            ndkChecksum = nativeReadAllData(mInpMediaUrl, "", Integer.MAX_VALUE, new String[0],
                    new String[0], true);
            assertEquals("SDK and NDK checksums mismatch", sdkChecksum, ndkChecksum);
        }

        private native boolean nativeTestDataSource(String srcPath, String srcUrl);

        @Test
        public void testDataSourceNative() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(testName.getMethodName() + " failed ",
                    nativeTestDataSource(MEDIA_DIR + INPUT_MEDIA, mInpMediaUrl));
        }
    }

    /**
     * Encloses extractor functionality tests
     */
    @RunWith(Parameterized.class)
    public static class FunctionalityTest {
        private static final int MAX_SEEK_POINTS = 7;
        private static final long SEED = 0x12b9b0a1;
        private final Random mRandNum = new Random(SEED);
        private String[] mSrcFiles;
        private String mMediaType;

        static {
            System.loadLibrary("ctsmediav2extractor_jni");
        }

        @Rule
        public TestName testName = new TestName();

        @Parameterized.Parameters(name = "{index}_{0}")
        public static Collection<Object[]> input() {
            return Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_MPEG2, new String[]{
                            "bbb_cif_768kbps_30fps_mpeg2_stereo_48kHz_192kbps_mp3.mp4",
                            "bbb_cif_768kbps_30fps_mpeg2.mkv",
                            /* TODO(b/162919907)
                            "bbb_cif_768kbps_30fps_mpeg2.vob",*/
                            /* TODO(b/162715861)
                            "bbb_cif_768kbps_30fps_mpeg2.ts" */}},
                    {MediaFormat.MIMETYPE_VIDEO_H263, new String[]{
                            "bbb_cif_768kbps_30fps_h263.mp4",
                            "bbb_cif_768kbps_30fps_h263_stereo_48kHz_192kbps_flac.mkv",
                            "bbb_cif_768kbps_30fps_h263_mono_8kHz_12kbps_amrnb.3gp",}},
                    {MediaFormat.MIMETYPE_VIDEO_MPEG4, new String[]{
                            "bbb_cif_768kbps_30fps_mpeg4.mkv",
                            "bbb_cif_768kbps_30fps_mpeg4_stereo_48kHz_192kbps_flac.mp4",
                            "bbb_cif_768kbps_30fps_mpeg4_mono_16kHz_20kbps_amrwb.3gp",}},
                    {MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                            "bbb_cif_768kbps_30fps_avc_stereo_48kHz_192kbps_vorbis.mp4",
                            "bbb_cif_768kbps_30fps_avc_stereo_48kHz_192kbps_aac.mkv",
                            "bbb_cif_768kbps_30fps_avc_stereo_48kHz_192kbps_aac.3gp",
                            /* TODO(b/162715861)
                            "bbb_cif_768kbps_30fps_avc.ts",*/}},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                            "bbb_cif_768kbps_30fps_hevc_stereo_48kHz_192kbps_opus.mp4",
                            "bbb_cif_768kbps_30fps_hevc_stereo_48kHz_192kbps_mp3.mkv",}},
                    {MediaFormat.MIMETYPE_VIDEO_VP8, new String[]{
                            "bbb_cif_768kbps_30fps_vp8_stereo_48kHz_192kbps_vorbis.webm",
                            "bbb_cif_768kbps_30fps_vp8_stereo_48kHz_192kbps_vorbis.mkv"}},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{
                            "bbb_cif_768kbps_30fps_vp9_stereo_48kHz_192kbps_opus.webm",
                            "bbb_cif_768kbps_30fps_vp9_stereo_48kHz_192kbps_opus.mkv",}},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, new String[]{
                            "bbb_cif_768kbps_30fps_av1.mp4",
                            "bbb_cif_768kbps_30fps_av1.webm",
                            "bbb_cif_768kbps_30fps_av1.mkv",}},
                    {MediaFormat.MIMETYPE_AUDIO_VORBIS, new String[]{
                            "bbb_cif_768kbps_30fps_avc_stereo_48kHz_192kbps_vorbis.mp4",
                            "bbb_cif_768kbps_30fps_vp8_stereo_48kHz_192kbps_vorbis.mkv",
                            "bbb_cif_768kbps_30fps_vp8_stereo_48kHz_192kbps_vorbis.webm",
                            "bbb_stereo_48kHz_192kbps_vorbis.ogg",}},
                    {MediaFormat.MIMETYPE_AUDIO_OPUS, new String[]{
                            "bbb_cif_768kbps_30fps_vp9_stereo_48kHz_192kbps_opus.webm",
                            "bbb_cif_768kbps_30fps_vp9_stereo_48kHz_192kbps_opus.mkv",
                            "bbb_cif_768kbps_30fps_hevc_stereo_48kHz_192kbps_opus.mp4",
                            "bbb_stereo_48kHz_192kbps_opus.ogg",}},
                    {MediaFormat.MIMETYPE_AUDIO_MPEG, new String[]{
                            "bbb_stereo_48kHz_192kbps_mp3.mp3",
                            "bbb_cif_768kbps_30fps_mpeg2_stereo_48kHz_192kbps_mp3.mp4",
                            "bbb_cif_768kbps_30fps_hevc_stereo_48kHz_192kbps_mp3.mkv",}},
                    {MediaFormat.MIMETYPE_AUDIO_AAC, new String[]{
                            "bbb_stereo_48kHz_192kbps_aac.mp4",
                            "bbb_cif_768kbps_30fps_avc_stereo_48kHz_192kbps_aac.3gp",
                            "bbb_cif_768kbps_30fps_avc_stereo_48kHz_192kbps_aac.mkv",
                            "bbb_stereo_48kHz_128kbps_aac.ts",}},
                    {MediaFormat.MIMETYPE_AUDIO_AMR_NB, new String[]{
                            "bbb_cif_768kbps_30fps_h263_mono_8kHz_12kbps_amrnb.3gp",
                            "bbb_mono_8kHz_12kbps_amrnb.amr",}},
                    {MediaFormat.MIMETYPE_AUDIO_AMR_WB, new String[]{
                            "bbb_cif_768kbps_30fps_mpeg4_mono_16kHz_20kbps_amrwb.3gp",
                            "bbb_mono_16kHz_20kbps_amrwb.amr"}},
                    {MediaFormat.MIMETYPE_AUDIO_FLAC, new String[]{
                            "bbb_cif_768kbps_30fps_mpeg4_stereo_48kHz_192kbps_flac.mp4",
                            "bbb_cif_768kbps_30fps_h263_stereo_48kHz_192kbps_flac.mkv",}},
                    {MediaFormat.MIMETYPE_AUDIO_RAW, new String[]{"canon.mid",}},
                    {MediaFormat.MIMETYPE_AUDIO_AC3, new String[]{
                            "testac3mp4.mp4", "testac3ts.ts",}},
                    {MediaFormat.MIMETYPE_AUDIO_AC4, new String[]{"multi0.mp4",}},
                    {MediaFormat.MIMETYPE_AUDIO_EAC3, new String[]{
                            "testeac3mp4.mp4", "testeac3ts.ts",}},
                    {MediaFormat.MIMETYPE_AUDIO_RAW, new String[]{"bbb_1ch_16kHz.wav",}},
                    {MediaFormat.MIMETYPE_AUDIO_G711_ALAW, new String[]{"bbb_2ch_8kHz_alaw.wav",}},
                    {MediaFormat.MIMETYPE_AUDIO_G711_MLAW, new String[]{"bbb_2ch_8kHz_mulaw.wav",}},
                    {MediaFormat.MIMETYPE_AUDIO_MSGSM, new String[]{"bbb_1ch_8kHz_gsm.wav",}},
            });
        }

        private native boolean nativeTestExtract(String srcPath, String refPath, String mediaType);

        private native boolean nativeTestSeek(String srcPath, String mediaType);

        private native boolean nativeTestSeekFlakiness(String srcPath, String mediaType);

        private native boolean nativeTestSeekToZero(String srcPath, String mediaType);

        private native boolean nativeTestFileFormat(String srcPath);

        public FunctionalityTest(String mediaType, String[] srcFiles) {
            mMediaType = mediaType;
            mSrcFiles = srcFiles;
        }

        // content necessary for testing seek are grouped in this class
        private class SeekTestParams {
            MediaCodec.BufferInfo mExpected;
            long mTimeStamp;
            int mMode;

            SeekTestParams(MediaCodec.BufferInfo expected, long timeStamp, int mode) {
                mExpected = expected;
                mTimeStamp = timeStamp;
                mMode = mode;
            }
        }

        private ArrayList<MediaCodec.BufferInfo> getSeekablePoints(String srcFile, String mediaType)
                throws IOException {
            ArrayList<MediaCodec.BufferInfo> bookmarks = null;
            if (mediaType == null) return null;
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + srcFile);
            extractor.setDataSource(MEDIA_DIR + srcFile);
            for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
                MediaFormat format = extractor.getTrackFormat(trackID);
                if (!mediaType.equals(format.getString(MediaFormat.KEY_MIME))) continue;
                extractor.selectTrack(trackID);
                bookmarks = new ArrayList<>();
                do {
                    int sampleFlags = extractor.getSampleFlags();
                    if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BufferInfo sampleInfo = new MediaCodec.BufferInfo();
                        sampleInfo.set(0, (int) extractor.getSampleSize(),
                                extractor.getSampleTime(), extractor.getSampleFlags());
                        bookmarks.add(sampleInfo);
                    }
                } while (extractor.advance());
                extractor.unselectTrack(trackID);
                break;
            }
            extractor.release();
            return bookmarks;
        }

        private ArrayList<SeekTestParams> generateSeekTestArgs(String srcFile, String mediaType,
                boolean isRandom) throws IOException {
            ArrayList<SeekTestParams> testArgs = new ArrayList<>();
            if (mediaType == null) return null;
            Preconditions.assertTestFileExists(MEDIA_DIR + srcFile);
            if (isRandom) {
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(MEDIA_DIR + srcFile);
                final long maxEstDuration = 4000000;
                for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
                    MediaFormat format = extractor.getTrackFormat(trackID);
                    if (!mediaType.equals(format.getString(MediaFormat.KEY_MIME))) continue;
                    extractor.selectTrack(trackID);
                    for (int i = 0; i < MAX_SEEK_POINTS; i++) {
                        long pts = (long) (mRandNum.nextDouble() * maxEstDuration);
                        for (int mode = MediaExtractor.SEEK_TO_PREVIOUS_SYNC;
                             mode <= MediaExtractor.SEEK_TO_CLOSEST_SYNC; mode++) {
                            MediaCodec.BufferInfo currInfo = new MediaCodec.BufferInfo();
                            extractor.seekTo(pts, mode);
                            currInfo.set(0, (int) extractor.getSampleSize(),
                                    extractor.getSampleTime(), extractor.getSampleFlags());
                            testArgs.add(new SeekTestParams(currInfo, pts, mode));
                        }
                    }
                    extractor.unselectTrack(trackID);
                    break;
                }
                extractor.release();
            } else {
                ArrayList<MediaCodec.BufferInfo> bookmarks = getSeekablePoints(srcFile, mediaType);
                if (bookmarks == null) return null;
                int size = bookmarks.size();
                int[] indices;
                if (size > MAX_SEEK_POINTS) {
                    indices = new int[MAX_SEEK_POINTS];
                    indices[0] = 0;
                    indices[MAX_SEEK_POINTS - 1] = size - 1;
                    for (int i = 1; i < MAX_SEEK_POINTS - 1; i++) {
                        indices[i] = (int) (mRandNum.nextDouble() * (MAX_SEEK_POINTS - 1) + 1);
                    }
                } else {
                    indices = new int[size];
                    for (int i = 0; i < size; i++) indices[i] = i;
                }
                // closest sync : Seek to the sync sample CLOSEST to the specified time
                // previous sync : Seek to a sync sample AT or AFTER the specified time
                // next sync : Seek to a sync sample AT or BEFORE the specified time
                for (int i : indices) {
                    MediaCodec.BufferInfo currInfo = bookmarks.get(i);
                    long pts = currInfo.presentationTimeUs;
                    testArgs.add(
                            new SeekTestParams(currInfo, pts, MediaExtractor.SEEK_TO_CLOSEST_SYNC));
                    testArgs.add(
                            new SeekTestParams(currInfo, pts, MediaExtractor.SEEK_TO_NEXT_SYNC));
                    testArgs.add(
                            new SeekTestParams(currInfo, pts,
                                    MediaExtractor.SEEK_TO_PREVIOUS_SYNC));
                    if (i > 0) {
                        MediaCodec.BufferInfo prevInfo = bookmarks.get(i - 1);
                        long ptsMinus = prevInfo.presentationTimeUs;
                        ptsMinus = pts - ((pts - ptsMinus) >> 3);
                        testArgs.add(new SeekTestParams(currInfo, ptsMinus,
                                MediaExtractor.SEEK_TO_CLOSEST_SYNC));
                        testArgs.add(new SeekTestParams(currInfo, ptsMinus,
                                MediaExtractor.SEEK_TO_NEXT_SYNC));
                        testArgs.add(new SeekTestParams(prevInfo, ptsMinus,
                                MediaExtractor.SEEK_TO_PREVIOUS_SYNC));
                    }
                    if (i < size - 1) {
                        MediaCodec.BufferInfo nextInfo = bookmarks.get(i + 1);
                        long ptsPlus = nextInfo.presentationTimeUs;
                        ptsPlus = pts + ((ptsPlus - pts) >> 3);
                        testArgs.add(new SeekTestParams(currInfo, ptsPlus,
                                MediaExtractor.SEEK_TO_CLOSEST_SYNC));
                        testArgs.add(new SeekTestParams(nextInfo, ptsPlus,
                                MediaExtractor.SEEK_TO_NEXT_SYNC));
                        testArgs.add(new SeekTestParams(currInfo, ptsPlus,
                                MediaExtractor.SEEK_TO_PREVIOUS_SYNC));
                    }
                }
            }
            return testArgs;
        }

        int checkSeekPoints(String srcFile, String mediaType,
                ArrayList<SeekTestParams> seekTestArgs) throws IOException {
            int errCnt = 0;
            Preconditions.assertTestFileExists(MEDIA_DIR + srcFile);
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(MEDIA_DIR + srcFile);
            for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
                MediaFormat format = extractor.getTrackFormat(trackID);
                if (!format.getString(MediaFormat.KEY_MIME).equals(mediaType)) continue;
                extractor.selectTrack(trackID);
                MediaCodec.BufferInfo received = new MediaCodec.BufferInfo();
                for (SeekTestParams arg : seekTestArgs) {
                    extractor.seekTo(arg.mTimeStamp, arg.mMode);
                    received.set(0, (int) extractor.getSampleSize(), extractor.getSampleTime(),
                            extractor.getSampleFlags());
                    if (!isSampleInfoIdentical(arg.mExpected, received)) {
                        errCnt++;
                        if (ENABLE_LOGS) {
                            Log.d(LOG_TAG, " flags exp/got: " + arg.mExpected.flags + '/' +
                                    received.flags);
                            Log.d(LOG_TAG,
                                    " size exp/got: " + arg.mExpected.size + '/' + received.size);
                            Log.d(LOG_TAG,
                                    " ts exp/got: " + arg.mExpected.presentationTimeUs + '/' +
                                            received.presentationTimeUs);
                        }
                    }
                }
                extractor.unselectTrack(trackID);
                break;
            }
            extractor.release();
            return errCnt;
        }

        private boolean isFileSeekable(String srcFile) throws IOException {
            MediaExtractor ext = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + srcFile);
            ext.setDataSource(MEDIA_DIR + srcFile);
            String format = ext.getMetrics().getString(MediaExtractor.MetricsConstants.FORMAT);
            ext.release();
            // MPEG2TS and MPEG2PS files are non-seekable
            return !(format.equalsIgnoreCase("MPEG2TSExtractor") ||
                    format.equalsIgnoreCase("MPEG2PSExtractor"));
        }

        /**
         * Audio, Video codecs support a variety of file-types/container formats. For example,
         * Vorbis supports OGG, MP4, WEBM and MKV. H.263 supports 3GPP, WEBM and MKV. For every
         * mediaType, a list of test vectors are provided one for each container) but underlying
         * elementary stream is the same for all. The streams of a mediaType are extracted and
         * compared with each other for similarity.
         */
        @CddTest(requirements = {"5.1.3", "5.1.8"})
        @LargeTest
        @Test
        public void testExtract() throws IOException {
            assumeTrue(shouldRunTest(mMediaType));
            Preconditions.assertTestFileExists(MEDIA_DIR + mSrcFiles[0]);
            MediaExtractor refExtractor = new MediaExtractor();
            refExtractor.setDataSource(MEDIA_DIR + mSrcFiles[0]);
            long sdkChecksum = readAllData(refExtractor, mMediaType, Integer.MAX_VALUE);
            long ndkChecksum = nativeReadAllData(MEDIA_DIR + mSrcFiles[0], mMediaType,
                    Integer.MAX_VALUE, null, null, false);
            assertEquals("SDK and NDK checksums mismatch", sdkChecksum, ndkChecksum);
            if (mSrcFiles.length == 1) {
                refExtractor.release();
                return;
            }
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_VORBIS));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_OPUS));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_MPEG));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AAC));
            boolean isOk = true;
            for (int i = 1; i < mSrcFiles.length && isOk; i++) {
                MediaExtractor testExtractor = new MediaExtractor();
                Preconditions.assertTestFileExists(MEDIA_DIR + mSrcFiles[i]);
                testExtractor.setDataSource(MEDIA_DIR + mSrcFiles[i]);
                if (!isMediaSimilar(refExtractor, testExtractor, mMediaType, Integer.MAX_VALUE)) {
                    if (ENABLE_LOGS) {
                        Log.d(LOG_TAG, "Files: " + mSrcFiles[0] + ", " + mSrcFiles[i] +
                                " are different from extractor perspective");
                    }
                    if (!codecListSupp.contains(mMediaType)) {
                        isOk = false;
                    }
                }
                testExtractor.release();
            }
            refExtractor.release();
            assertTrue(testName.getMethodName() + " failed for Mediatype: " + mMediaType, isOk);
        }

        /**
         * Tests seek functionality, verifies if we seek to most accurate point for a given
         * choice of timestamp and mode.
         */
        @ApiTest(apis = {"android.media.MediaExtractor#seekTo",
                "android.media.MediaExtractor#SEEK_TO_CLOSEST_SYNC",
                "android.media.MediaExtractor#SEEK_TO_NEXT_SYNC",
                "android.media.MediaExtractor#SEEK_TO_PREVIOUS_SYNC"})
        @LargeTest
        @Test
        @Ignore("TODO(b/146420831)")
        public void testSeek() throws IOException {
            assumeTrue(shouldRunTest(mMediaType)
                    && !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_RAW));
            boolean isOk = true;
            for (String srcFile : mSrcFiles) {
                if (!isFileSeekable(srcFile)) continue;
                ArrayList<SeekTestParams> seekTestArgs =
                        generateSeekTestArgs(srcFile, mMediaType, false);
                assertTrue("mediaType is null.", seekTestArgs != null);
                assertTrue("No sync samples found.", !seekTestArgs.isEmpty());
                Collections.shuffle(seekTestArgs, mRandNum);
                int seekAccErrCnt = checkSeekPoints(srcFile, mMediaType, seekTestArgs);
                if (seekAccErrCnt != 0) {
                    if (ENABLE_LOGS) {
                        Log.d(LOG_TAG, "For " + srcFile + " seek chose inaccurate Sync point in: " +
                                seekAccErrCnt + "/" + seekTestArgs.size());
                    }
                    if (!codecListSupp.contains(mMediaType)) {
                        isOk = false;
                        break;
                    }
                }
            }
            assertTrue(testName.getMethodName() + " failed for mediaType: " + mMediaType, isOk);
        }

        /**
         * Tests if we get the same content each time after a call to seekto;
         */
        @ApiTest(apis = {"android.media.MediaExtractor#seekTo",
                "android.media.MediaExtractor#SEEK_TO_CLOSEST_SYNC",
                "android.media.MediaExtractor#SEEK_TO_NEXT_SYNC",
                "android.media.MediaExtractor#SEEK_TO_PREVIOUS_SYNC"})
        @LargeTest
        @Test
        public void testSeekFlakiness() throws IOException {
            assumeTrue(shouldRunTest(mMediaType));
            boolean isOk = true;
            for (String srcFile : mSrcFiles) {
                if (!isFileSeekable(srcFile)) continue;
                ArrayList<SeekTestParams> seekTestArgs =
                        generateSeekTestArgs(srcFile, mMediaType, true);
                assertTrue("mediaType is null.", seekTestArgs != null);
                assertTrue("No samples found.", !seekTestArgs.isEmpty());
                Collections.shuffle(seekTestArgs, mRandNum);
                int flakyErrCnt = checkSeekPoints(srcFile, mMediaType, seekTestArgs);
                if (flakyErrCnt != 0) {
                    if (ENABLE_LOGS) {
                        Log.d(LOG_TAG,
                                "No. of Samples where seek showed flakiness is: " + flakyErrCnt);
                    }
                    if (!codecListSupp.contains(mMediaType)) {
                        isOk = false;
                        break;
                    }
                }
            }
            assertTrue(testName.getMethodName() + " failed for Mediatype: " + mMediaType, isOk);
        }

        /**
         * Test if seekTo(0) yields the same content as if we had just opened the file and started
         * reading.
         */
        @ApiTest(apis = {"android.media.MediaExtractor#seekTo",
                "android.media.MediaExtractor#SEEK_TO_CLOSEST_SYNC",
                "android.media.MediaExtractor#SEEK_TO_NEXT_SYNC",
                "android.media.MediaExtractor#SEEK_TO_PREVIOUS_SYNC"})
        @SmallTest
        @Test
        public void testSeekToZero() throws IOException {
            assumeTrue(shouldRunTest(mMediaType));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_VORBIS));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_MPEG));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AAC));
            boolean isOk = true;
            for (String srcFile : mSrcFiles) {
                if (!isFileSeekable(srcFile)) continue;
                MediaExtractor extractor = new MediaExtractor();
                Preconditions.assertTestFileExists(MEDIA_DIR + srcFile);
                extractor.setDataSource(MEDIA_DIR + srcFile);
                MediaCodec.BufferInfo sampleInfoAtZero = new MediaCodec.BufferInfo();
                MediaCodec.BufferInfo currInfo = new MediaCodec.BufferInfo();
                final long randomSeekPts = 1 << 20;
                for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
                    MediaFormat format = extractor.getTrackFormat(trackID);
                    if (!mMediaType.equals(format.getString(MediaFormat.KEY_MIME))) continue;
                    extractor.selectTrack(trackID);
                    sampleInfoAtZero.set(0, (int) extractor.getSampleSize(),
                            extractor.getSampleTime(), extractor.getSampleFlags());
                    extractor.seekTo(randomSeekPts, MediaExtractor.SEEK_TO_NEXT_SYNC);
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    currInfo.set(0, (int) extractor.getSampleSize(),
                            extractor.getSampleTime(), extractor.getSampleFlags());
                    if (!isSampleInfoIdentical(sampleInfoAtZero, currInfo)) {
                        if (!codecListSupp.contains(mMediaType)) {
                            if (ENABLE_LOGS) {
                                Log.d(LOG_TAG, "seen mismatch seekTo(0, SEEK_TO_CLOSEST_SYNC)");
                                Log.d(LOG_TAG, " flags exp/got: " + sampleInfoAtZero.flags + '/' +
                                        currInfo.flags);
                                Log.d(LOG_TAG, " size exp/got: " + sampleInfoAtZero.size + '/' +
                                        currInfo.size);
                                Log.d(LOG_TAG,
                                        " ts exp/got: " + sampleInfoAtZero.presentationTimeUs +
                                                '/' + currInfo.presentationTimeUs);
                            }
                            isOk = false;
                            break;
                        }
                    }
                    extractor.seekTo(-1L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    currInfo.set(0, (int) extractor.getSampleSize(),
                            extractor.getSampleTime(), extractor.getSampleFlags());
                    if (!isSampleInfoIdentical(sampleInfoAtZero, currInfo)) {
                        if (!codecListSupp.contains(mMediaType)) {
                            if (ENABLE_LOGS) {
                                Log.d(LOG_TAG, "seen mismatch seekTo(-1, SEEK_TO_CLOSEST_SYNC)");
                                Log.d(LOG_TAG, " flags exp/got: " + sampleInfoAtZero.flags + '/' +
                                        currInfo.flags);
                                Log.d(LOG_TAG, " size exp/got: " + sampleInfoAtZero.size + '/' +
                                        currInfo.size);
                                Log.d(LOG_TAG,
                                        " ts exp/got: " + sampleInfoAtZero.presentationTimeUs +
                                                '/' + currInfo.presentationTimeUs);
                            }
                            isOk = false;
                            break;
                        }
                    }
                    extractor.unselectTrack(trackID);
                }
                extractor.release();
            }
            assertTrue(testName.getMethodName() + " failed for Mediatype: " + mMediaType, isOk);
        }

        @ApiTest(apis = {"android.media.MediaExtractor#getMetrics"})
        @SmallTest
        @Test
        public void testMetrics() throws IOException {
            assumeTrue(shouldRunTest(mMediaType));
            for (String srcFile : mSrcFiles) {
                MediaExtractor extractor = new MediaExtractor();
                Preconditions.assertTestFileExists(MEDIA_DIR + srcFile);
                extractor.setDataSource(MEDIA_DIR + srcFile);
                PersistableBundle bundle = extractor.getMetrics();
                int numTracks = bundle.getInt(MediaExtractor.MetricsConstants.TRACKS);
                String format = bundle.getString(MediaExtractor.MetricsConstants.FORMAT);
                String mediaType = bundle.getString(MediaExtractor.MetricsConstants.MIME_TYPE);
                assertTrue(numTracks == extractor.getTrackCount() && format != null &&
                        mediaType != null);
                extractor.release();
            }
        }

        @CddTest(requirements = {"5.1.3", "5.1.8"})
        @LargeTest
        @Test
        public void testExtractNative() {
            assumeTrue(shouldRunTest(mMediaType));
            if (mSrcFiles.length == 1) return;
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_VORBIS));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_OPUS));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_MPEG));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AAC));
            boolean isOk = true;
            Preconditions.assertTestFileExists(MEDIA_DIR + mSrcFiles[0]);
            for (int i = 1; i < mSrcFiles.length; i++) {
                Preconditions.assertTestFileExists(MEDIA_DIR + mSrcFiles[i]);
                if (!nativeTestExtract(MEDIA_DIR + mSrcFiles[0], MEDIA_DIR + mSrcFiles[i],
                        mMediaType)) {
                    Log.d(LOG_TAG, "Files: " + mSrcFiles[0] + ", " + mSrcFiles[i] +
                            " are different from extractor perpsective");
                    if (!codecListSupp.contains(mMediaType)) {
                        isOk = false;
                        break;
                    }
                }
            }
            assertTrue(testName.getMethodName() + " failed for Mediatype: " + mMediaType, isOk);
        }

        @ApiTest(apis = {"AMediaExtractor_seekTo", "AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC",
                "AMEDIAEXTRACTOR_SEEK_NEXT_SYNC", "AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC"})
        @LargeTest
        @Test
        @Ignore("TODO(b/146420831)")
        public void testSeekNative() throws IOException {
            assumeTrue(shouldRunTest(mMediaType)
                    && !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_RAW));
            boolean isOk = true;
            for (String srcFile : mSrcFiles) {
                Preconditions.assertTestFileExists(MEDIA_DIR + srcFile);
                if (!isFileSeekable(srcFile)) continue;
                if (!nativeTestSeek(MEDIA_DIR + srcFile, mMediaType)) {
                    if (!codecListSupp.contains(mMediaType)) {
                        isOk = false;
                        break;
                    }
                }
            }
            assertTrue(testName.getMethodName() + " failed for Mediatype: " + mMediaType, isOk);
        }

        @ApiTest(apis = {"AMediaExtractor_seekTo", "AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC",
                "AMEDIAEXTRACTOR_SEEK_NEXT_SYNC", "AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC"})
        @LargeTest
        @Test
        public void testSeekFlakinessNative() throws IOException {
            assumeTrue(shouldRunTest(mMediaType));
            boolean isOk = true;
            for (String srcFile : mSrcFiles) {
                Preconditions.assertTestFileExists(MEDIA_DIR + srcFile);
                if (!isFileSeekable(srcFile)) continue;
                if (!nativeTestSeekFlakiness(MEDIA_DIR + srcFile, mMediaType)) {
                    if (!codecListSupp.contains(mMediaType)) {
                        isOk = false;
                        break;
                    }
                }
            }
            assertTrue(testName.getMethodName() + " failed for Mediatype: " + mMediaType, isOk);
        }

        @ApiTest(apis = {"AMediaExtractor_seekTo", "AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC",
                "AMEDIAEXTRACTOR_SEEK_NEXT_SYNC", "AMEDIAEXTRACTOR_SEEK_PREVIOUS_SYNC"})
        @SmallTest
        @Test
        public void testSeekToZeroNative() throws IOException {
            assumeTrue(shouldRunTest(mMediaType));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_VORBIS));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_MPEG));
            assumeTrue("TODO(b/146925481)", !mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AAC));
            boolean isOk = true;
            for (String srcFile : mSrcFiles) {
                Preconditions.assertTestFileExists(MEDIA_DIR + srcFile);
                if (!isFileSeekable(srcFile)) continue;
                if (!nativeTestSeekToZero(MEDIA_DIR + srcFile, mMediaType)) {
                    if (!codecListSupp.contains(mMediaType)) {
                        isOk = false;
                        break;
                    }
                }
            }
            assertTrue(testName.getMethodName() + " failed for Mediatype: " + mMediaType, isOk);
        }

        @ApiTest(apis = "AMediaExtractor_getFileFormat")
        @SmallTest
        @Test
        public void testFileFormatNative() {
            assumeTrue(shouldRunTest(mMediaType));
            boolean isOk = true;
            for (String srcFile : mSrcFiles) {
                Preconditions.assertTestFileExists(MEDIA_DIR + srcFile);
                if (!nativeTestFileFormat(MEDIA_DIR + srcFile)) {
                    isOk = false;
                    break;
                }
            }
            assertTrue(testName.getMethodName() + " failed for Mediatype: " + mMediaType, isOk);
        }
    }

    /**
     * Encloses extractor test for validating extractor output for extractors which directly
     * decode instead of extracting.
     */
    @RunWith(Parameterized.class)
    public static class FusedExtractorDecoderTest {
        private final String mMediaType;
        private final String mRefFile;
        private final String mTestFile;

        public FusedExtractorDecoderTest(String mediaType, String refFile, String testFile) {
            mMediaType = mediaType;
            mRefFile = refFile;
            mTestFile = testFile;
        }

        @Parameterized.Parameters(name = "{index}_{0}")
        public static Collection<Object[]> input() {
            return Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_AUDIO_FLAC,
                            "bbb_cif_768kbps_30fps_mpeg4_stereo_48kHz_192kbps_flac.mp4",
                            "bbb_stereo_48kHz_192kbps_flac.flac"},
                    /* TODO(b/163566531)
                    {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_1ch_16kHz.mkv", "bbb_1ch_16kHz.wav"},*/
            });
        }

        @CddTest(requirements = {"5.1.3"})
        @LargeTest
        @Test
        public void testExtractDecodeAndValidate() throws IOException, InterruptedException {
            MediaExtractor testExtractor = new MediaExtractor();
            testExtractor.setDataSource(MEDIA_DIR + mTestFile);
            MediaFormat format = testExtractor.getTrackFormat(0);
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            if (mediaType.equals(MediaFormat.MIMETYPE_AUDIO_RAW)) {
                ArrayList<String> listOfDecoders =
                        CodecTestBase.selectCodecs(mMediaType, null, null, false);
                assertTrue("no suitable codecs found for Mediatype: " + mMediaType,
                        !listOfDecoders.isEmpty());
                CodecDecoderTestBase cdtb =
                        new CodecDecoderTestBase(listOfDecoders.get(0), mMediaType, mRefFile,
                                "invalid");
                cdtb.decodeToMemory(MEDIA_DIR + mRefFile, listOfDecoders.get(0), 0,
                        MediaExtractor.SEEK_TO_CLOSEST_SYNC, Integer.MAX_VALUE);
                String log = String.format("test file: %s, ref file: %s:: ", mTestFile, mRefFile);
                final ByteBuffer refBuffer = cdtb.getOutputManager().getBuffer();

                testExtractor.selectTrack(0);
                ByteBuffer testBuffer = ByteBuffer.allocate(refBuffer.limit());
                int bufOffset = 0;
                while (true) {
                    long bytesRead = testExtractor.readSampleData(testBuffer, bufOffset);
                    if (bytesRead == -1) break;
                    bufOffset += bytesRead;
                    testExtractor.advance();
                }
                testBuffer.rewind();
                assertEquals(log + "Output mismatch", 0, refBuffer.compareTo(testBuffer));
                assertTrue(log + "Output formats mismatch",
                        cdtb.isFormatSimilar(cdtb.getOutputFormat(), format));
            } else if (mediaType.equals(mMediaType)) {
                MediaExtractor refExtractor = new MediaExtractor();
                refExtractor.setDataSource(MEDIA_DIR + mRefFile);
                if (!isMediaSimilar(refExtractor, testExtractor, mMediaType, Integer.MAX_VALUE)) {
                    fail("Files: " + mRefFile + ", " + mTestFile +
                            " are different from extractor perspective");
                }
                refExtractor.release();
            } else {
                fail("unexpected Mediatype: " + mediaType);
            }
            testExtractor.release();
        }
    }

    /**
     * Test if extractor populates key-value pairs correctly
     */
    @RunWith(Parameterized.class)
    public static class ValidateKeyValuePairs {
        private static final String MEDIA_DIR = WorkDir.getMediaDirString();
        private final String mMediaType;
        private final String[] mInpFiles;
        private final int mProfile;
        private final int mLevel;
        private final int mWR;
        private final int mHCh;

        public ValidateKeyValuePairs(String mediaType, String[] inpFiles, int profile, int level,
                int wr, int hCh) {
            mMediaType = mediaType;
            mInpFiles = inpFiles;
            mProfile = profile;
            mLevel = level;
            mWR = wr;
            mHCh = hCh;
        }

        @Parameterized.Parameters(name = "{index}_{0}")
        public static Collection<Object[]> input() {
            // mediaType, clips, profile, level, width/sample rate, height/channel count
            List<Object[]> exhaustiveArgsList = new ArrayList<>();

            if (hasDecoder(MediaFormat.MIMETYPE_VIDEO_MPEG2)) {
                // profile and level constraints as per sec 2.3.2 of cdd
                /* TODO(b/159582475)
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_MPEG2, new String[]{
                        "bbb_1920x1080_30fps_mpeg2_main_high.mp4",
                        "bbb_1920x1080_30fps_mpeg2_main_high.mkv"},
                        MediaCodecInfo.CodecProfileLevel.MPEG2ProfileMain,
                        MediaCodecInfo.CodecProfileLevel.MPEG2LevelHL, 1920, 1080});*/
            }

            if (hasDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                // profile and level constraints as per sec 2.3.2 of cdd
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                        "bbb_1920x1080_avc_baseline_l42.mp4",
                        "bbb_1920x1080_avc_baseline_l42.mkv",
                        "bbb_1920x1080_avc_baseline_l42.3gp"},
                        MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline,
                        MediaCodecInfo.CodecProfileLevel.AVCLevel42, 1920, 1080});
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                        "bbb_1920x1080_avc_main_l42.mp4",
                        "bbb_1920x1080_avc_main_l42.mkv",
                        "bbb_1920x1080_avc_main_l42.3gp"},
                        MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
                        MediaCodecInfo.CodecProfileLevel.AVCLevel42, 1920, 1080});
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                        "bbb_1920x1080_avc_high_l42.mp4",
                        "bbb_1920x1080_avc_high_l42.mkv",
                        "bbb_1920x1080_avc_high_l42.3gp"},
                        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                        MediaCodecInfo.CodecProfileLevel.AVCLevel42, 1920, 1080});
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                        "video_dovi_1920x1080_60fps_dvav_09.mp4"},
                        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                        MediaCodecInfo.CodecProfileLevel.AVCLevel42, 1920, 1080});
                // profile/level constraints for avc as per sec 5.3.4 of cdd
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                        "bbb_1920x1080_avc_baseline_l40.mp4",
                        "bbb_1920x1080_avc_baseline_l40.mkv",
                        "bbb_1920x1080_avc_baseline_l40.3gp"},
                        MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline,
                        MediaCodecInfo.CodecProfileLevel.AVCLevel4, 1920, 1080});
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                        "bbb_1920x1080_avc_main_l40.mp4",
                        "bbb_1920x1080_avc_main_l40.mkv",
                        "bbb_1920x1080_avc_main_l40.3gp"},
                        MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
                        MediaCodecInfo.CodecProfileLevel.AVCLevel4, 1920, 1080});
            }

            if (hasDecoder(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                // profile and level constraints as per sec 2.3.2 of cdd
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                        "bbb_1920x1080_hevc_main_l41.mp4",
                        "bbb_1920x1080_hevc_main_l41.mkv"},
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain,
                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41, 1920, 1080});
                // profile/level constraints for hevc as per sec 5.3.5 of cdd
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                        "bbb_1920x1080_hevc_main_l40.mp4",
                        "bbb_1920x1080_hevc_main_l40.mkv"},
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain,
                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4, 1920, 1080});
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                        "video_dovi_1920x1080_30fps_dvhe_04.mp4"},
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4, 1920, 1080});
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                        "video_dovi_1920x1080_60fps_dvhe_08.mp4"},
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41, 1920, 1080});
            }

            if (hasDecoder(MediaFormat.MIMETYPE_VIDEO_VP9)) {
                // profile and level constraints as per sec 2.3.2 of cdd
                /* TODO(b/159582475)
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{
                        "bbb_1920x1080_vp9_main_l41.webm",
                        "bbb_1920x1080_vp9_main_l41.mkv"},
                        MediaCodecInfo.CodecProfileLevel.VP9Profile0,
                        MediaCodecInfo.CodecProfileLevel.VP9Level41, 1920, 1080});*/
                // profile/level constraints for vp9 as per sec 5.3.6 of cdd
                /* TODO(b/159582475)
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{
                        "bbb_1920x1080_vp9_main_l40.webm",
                        "bbb_1920x1080_vp9_main_l40.mkv"},
                        MediaCodecInfo.CodecProfileLevel.VP9Profile0,
                        MediaCodecInfo.CodecProfileLevel.VP9Level4, 1920, 1080});*/
            }

            if (hasDecoder(MediaFormat.MIMETYPE_VIDEO_H263)) {
                // profile/level constraints for h263 as per sec 5.3.2 of cdd
                /* TODO(b/159582475)
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_H263, new String[]{
                        "bbb_352x288_384kbps_30fps_h263_baseline_l3.3gp",
                        "bbb_352x288_384kbps_30fps_h263_baseline_l3.mp4",
                        "bbb_352x288_384kbps_30fps_h263_baseline_l3.mkv"},
                        MediaCodecInfo.CodecProfileLevel.H263ProfileBaseline,
                        MediaCodecInfo.CodecProfileLevel.H263Level30, 352, 288});*/
            }

            if (hasDecoder(MediaFormat.MIMETYPE_VIDEO_MPEG4)) {
                // profile/level constraints for mpeg4 as per sec 5.3.3 of cdd
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_MPEG4, new String[]{
                        "bbb_352x288_384kbps_30fps_mpeg4_simple_l3.mp4",
                        "bbb_352x288_384kbps_30fps_mpeg4_simple_l3.3gp",
                        "bbb_352x288_384kbps_30fps_mpeg4_simple_l3.mkv"},
                        MediaCodecInfo.CodecProfileLevel.MPEG4ProfileSimple,
                        MediaCodecInfo.CodecProfileLevel.MPEG4Level3, 352, 288});
            }

            if (hasDecoder(MediaFormat.MIMETYPE_AUDIO_AAC)) {
                // profile and level constraints for devices that have audio output as per sec 2.2.2,
                // sec 2.3.2, sec 2.5.2, sec 5.1.2 of cdd
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_AUDIO_AAC, new String[]{
                        "bbb_stereo_44kHz_192kbps_aac_lc.mp4",
                        "bbb_stereo_44kHz_192kbps_aac_lc.3gp",
                        "bbb_stereo_44kHz_192kbps_aac_lc.mkv"},
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC, 0, 44100, 2});
                /* TODO(b/159582475)
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_AUDIO_AAC, new String[]{
                        "bbb_stereo_44kHz_192kbps_aac_he.mp4",
                        "bbb_stereo_44kHz_192kbps_aac_he.3gp",
                        "bbb_stereo_44kHz_192kbps_aac_he.mkv"},
                        MediaCodecInfo.CodecProfileLevel.AACObjectHE, 0, 44100, 2});*/
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_AUDIO_AAC, new String[] {
                        "bbb_stereo_44kHz_192kbps_aac_eld.mp4",
                        "bbb_stereo_44kHz_192kbps_aac_eld.3gp",
                        "bbb_stereo_44kHz_192kbps_aac_eld.mkv"},
                        MediaCodecInfo.CodecProfileLevel.AACObjectELD, 0, 44100, 2});
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_AUDIO_AAC, new String[]{
                        "bbb_stereo_44kHz_192kbps_aac_ld.mp4",
                        "bbb_stereo_44kHz_192kbps_aac_ld.3gp",
                        "bbb_stereo_44kHz_192kbps_aac_ld.mkv"},
                        MediaCodecInfo.CodecProfileLevel.AACObjectLD, 0, 44100, 2});
                /*TODO(b/159582475)
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_AUDIO_AAC, new String[]{
                        "bbb_stereo_44kHz_192kbps_aac_hev2.mp4",
                        "bbb_stereo_44kHz_192kbps_aac_hev2.3gp",
                        "bbb_stereo_44kHz_192kbps_aac_hev2.mkv"},
                        MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS, 0, 44100, 2});*/
            }

            // Miscellaneous
            if (hasDecoder(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION)) {
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
                        new String[]{"video_dovi_1920x1080_30fps_dvhe_04.mp4"},
                        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtr,
                        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd30, 1920, 1080});
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
                        new String[]{"video_dovi_1920x1080_60fps_dvhe_05.mp4"},
                        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn,
                        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd60, 1920, 1080});
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
                        new String[]{"video_dovi_1920x1080_60fps_dvhe_08.mp4"},
                        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt,
                        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd60, 1920, 1080});
                exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION,
                        new String[]{"video_dovi_1920x1080_60fps_dvav_09.mp4"},
                        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavSe,
                        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd60, 1920, 1080});
            }

            return exhaustiveArgsList;
        }

        @ApiTest(apis = {"android.media.MediaExtractor#getTrackFormat"})
        @Test
        public void validateKeyValuePairs() throws IOException {
            for (String file : mInpFiles) {
                MediaFormat format = null;
                MediaExtractor extractor = new MediaExtractor();
                Preconditions.assertTestFileExists(MEDIA_DIR + file);
                extractor.setDataSource(MEDIA_DIR + file);
                for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
                    MediaFormat fmt = extractor.getTrackFormat(trackID);
                    if (mMediaType.equalsIgnoreCase(fmt.getString(MediaFormat.KEY_MIME))) {
                        format = fmt;
                        break;
                    }
                }
                extractor.release();
                assertTrue("missing track format from file " +  file, format != null);
                if (mMediaType.equals(MediaFormat.MIMETYPE_AUDIO_AAC)) {
                    assertTrue("neither KEY_AAC_PROFILE nor KEY_PROFILE found in file " + file,
                            format.containsKey(MediaFormat.KEY_AAC_PROFILE) ||
                            format.containsKey(MediaFormat.KEY_PROFILE));
                    if (format.containsKey(MediaFormat.KEY_AAC_PROFILE)) {
                        int profile = format.getInteger(MediaFormat.KEY_AAC_PROFILE, -1);
                        assertEquals("mismatched KEY_AAC_PROFILE in file " + file,
                                     mProfile, profile);
                    }
                    if (format.containsKey(MediaFormat.KEY_PROFILE)) {
                        int profile = format.getInteger(MediaFormat.KEY_PROFILE, -1);
                        assertEquals("mismatched KEY_PROFILE in file " + file, mProfile, profile);
                    }
                } else {
                    int profile = format.getInteger(MediaFormat.KEY_PROFILE, -1);
                    assertEquals("mismatched KEY_PROFILE in file " + file, mProfile, profile);
                    int level = format.getInteger(MediaFormat.KEY_LEVEL, -1);
                    assertEquals("mismatched KEY_LEVEL in file " + file, mLevel, level);
                }
                if (mMediaType.startsWith("audio/")) {
                    int sample_rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, -1);
                    assertEquals("mismatched KEY_SAMPLE_RATE in file " + file,
                                 mWR, sample_rate);
                    int channel_count = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, -1);
                    assertEquals("mismatched KEY_CHANNEL_COUNT in file " + file,
                                 mHCh, channel_count);
                } else if (mMediaType.startsWith("video/")) {
                    int width = format.getInteger(MediaFormat.KEY_WIDTH, -1);
                    assertEquals("mismatched KEY_WIDTH in file " + file, mWR, width);
                    int height = format.getInteger(MediaFormat.KEY_HEIGHT, -1);
                    assertEquals("mismatched KEY_HEIGHT in file " + file, mHCh, height);
                }
            }
        }
    }

    /**
     * Makes sure if PTS(order) of a file matches the expected values in the corresponding text
     * file with just PTS values.
     */
    @RunWith(Parameterized.class)
    public static class ExtractorTimeStampTest {
        private final String mRefFile;
        private final String mPTSListFile;
        private int mTrackIndex;
        // Allowing tolerance of +1/-1 for rounding error.
        private static final int PTS_TOLERANCE = 1;

        public ExtractorTimeStampTest(String refFile, String textFile, int trackIndex) {
            mRefFile = refFile;
            mPTSListFile = textFile;
            mTrackIndex = trackIndex;
        }

        @Parameterized.Parameters
        public static Collection<Object[]> input() {
            final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                    {"bbb_384x216_768kbps_30fps_avc_2b.mp4",
                            "pts_bbb_384x216_768kbps_30fps_avc_2b.txt", 0},
                    {"bbb_384x216_768kbps_25fps_avc_7b.mp4",
                            "pts_bbb_384x216_768kbps_25fps_avc_7b.txt", 0},
                    {"bbb_384x216_768kbps_24fps_avc_5b.mkv",
                            "pts_bbb_384x216_768kbps_24fps_avc_5b.txt", 0},
                    {"bbb_384x216_768kbps_30fps_avc_badapt.mkv",
                            "pts_bbb_384x216_768kbps_30fps_avc_badapt.txt", 0},
                    {"bbb_384x216_768kbps_30fps_avc_2b.3gp",
                            "pts_bbb_384x216_768kbps_30fps_avc_2b.txt", 0},
                    {"bbb_384x216_768kbps_25fps_avc_7b.3gp",
                            "pts_bbb_384x216_768kbps_25fps_avc_7b.txt", 0},
                    {"bbb_384x216_768kbps_30fps_avc_badapt_bbb_480x360_768kbps_24fps_avc_5b.mkv",
                            "pts_bbb_384x216_768kbps_30fps_avc_badapt.txt", 0},
                    {"bbb_384x216_768kbps_30fps_avc_badapt_bbb_480x360_768kbps_24fps_avc_5b.mkv",
                            "pts_bbb_480x360_768kbps_24fps_avc_5b.txt", 1},
                    {"bbb_384x216_768kbps_30fps_avc_2b_bbb_cif_768kbps_25fps_avc_7b.mp4",
                            "pts_bbb_384x216_768kbps_30fps_avc_2b.txt", 0},
                    {"bbb_384x216_768kbps_30fps_avc_2b_bbb_cif_768kbps_25fps_avc_7b.mp4",
                            "pts_bbb_cif_768kbps_25fps_avc_7b.txt", 1},
                    {"bbb_384x216_768kbps_30fps_hevc_2b.mp4",
                            "pts_bbb_384x216_768kbps_30fps_hevc_2b.txt", 0},
                    {"bbb_384x216_768kbps_25fps_hevc_7b.mp4",
                            "pts_bbb_384x216_768kbps_25fps_hevc_7b.txt", 0},
                    {"bbb_384x216_768kbps_24fps_hevc_5b.mkv",
                            "pts_bbb_384x216_768kbps_24fps_hevc_5b.txt", 0},
                    {"bbb_384x216_768kbps_30fps_hevc_badapt.mkv",
                            "pts_bbb_384x216_768kbps_30fps_hevc_badapt.txt", 0},
                    {"bbb_384x216_768kbps_30fps_hevc_badapt_bbb_480x360_768kbps_24fps_hevc_5b.mkv",
                            "pts_bbb_384x216_768kbps_30fps_hevc_badapt.txt", 0},
                    {"bbb_384x216_768kbps_30fps_hevc_badapt_bbb_480x360_768kbps_24fps_hevc_5b.mkv",
                            "pts_bbb_480x360_768kbps_24fps_hevc_5b.txt", 1},
                    {"bbb_384x216_768kbps_30fps_hevc_2b_bbb_cif_768kbps_25fps_hevc_7b.mp4",
                            "pts_bbb_384x216_768kbps_30fps_hevc_2b.txt", 0},
                    {"bbb_384x216_768kbps_30fps_hevc_2b_bbb_cif_768kbps_25fps_hevc_7b.mp4",
                            "pts_bbb_cif_768kbps_25fps_hevc_7b.txt", 1},
                    {"bbb_384x216_768kbps_30fps_mpeg2_2b.mp4",
                            "pts_bbb_384x216_768kbps_30fps_mpeg2_2b.txt", 0},
                    {"bbb_384x216_768kbps_25fps_mpeg2_5b.mp4",
                            "pts_bbb_384x216_768kbps_25fps_mpeg2_5b.txt", 0},
                    {"bbb_384x216_768kbps_24fps_mpeg2_5b.mkv",
                            "pts_bbb_384x216_768kbps_24fps_mpeg2_5b.txt", 0},
                    {"bbb_384x216_768kbps_30fps_mpeg2_2b.ts",
                            "pts_bbb_384x216_768kbps_30fps_mpeg2_2b.txt", 0},
                    {"bbb_384x216_768kbps_25fps_mpeg2_7b.ts",
                            "pts_bbb_384x216_768kbps_25fps_mpeg2_7b.txt", 0},
                    {"bbb_cif_768kbps_30fps_vp8_stereo_48kHz_192kbps_vorbis.webm",
                            "pts_bbb_cif_768kbps_30fps_vp8.txt", 0},
                    {"bbb_cif_768kbps_30fps_vp8_stereo_48kHz_192kbps_vorbis.mkv",
                            "pts_bbb_cif_768kbps_30fps_vp8.txt", 0},
                    {"bbb_cif_768kbps_30fps_vp8_stereo_48kHz_192kbps_vorbis.webm",
                            "pts_stereo_48kHz_192kbps_vorbis.txt", 1},
                    {"bbb_cif_768kbps_30fps_vp8_stereo_48kHz_192kbps_vorbis.mkv",
                            "pts_stereo_48kHz_192kbps_vorbis.txt", 1},
                    {"bbb_340x280_768kbps_30fps_split_non_display_frame_vp9.webm",
                            "pts_bbb_340x280_768kbps_30fps_split_non_display_frame_vp9.txt", 0},
                    {"bbb_cif_768kbps_30fps_vp9_stereo_48kHz_192kbps_opus.webm",
                            "pts_bbb_cif_768kbps_30fps_vp9.txt", 0},
                    {"bbb_cif_768kbps_30fps_vp9_stereo_48kHz_192kbps_opus.mkv",
                            "pts_bbb_cif_768kbps_30fps_vp9.txt", 0},
                    {"bbb_cif_768kbps_30fps_av1.mp4",
                            "pts_bbb_cif_768kbps_30fps_av1.txt", 0},
                    {"bbb_cif_768kbps_30fps_av1.mkv",
                            "pts_bbb_cif_768kbps_30fps_av1.txt", 0},
                    {"bbb_cif_768kbps_30fps_av1.webm",
                            "pts_bbb_cif_768kbps_30fps_av1.txt", 0},
                    {"binary_counter_320x240_30fps_600frames.mp4",
                            "pts_binary_counter_320x240_30fps_600frames.txt", 0},
            });
            return exhaustiveArgsList;
        }

        @ApiTest(apis = {"android.media.MediaExtractor#getSampleTime"})
        @LargeTest
        @Test
        public void testPresentationTimeStampsMatch() throws IOException {
            try (FileInputStream file = new FileInputStream(MEDIA_DIR + mPTSListFile);
                 InputStreamReader input = new InputStreamReader(file);
                 Reader txtRdr = new BufferedReader(input)) {
                StreamTokenizer strTok = new StreamTokenizer(txtRdr);
                strTok.parseNumbers();

                MediaExtractor extractor = new MediaExtractor();
                Preconditions.assertTestFileExists(MEDIA_DIR + mRefFile);
                extractor.setDataSource(MEDIA_DIR + mRefFile);
                assertTrue(mTrackIndex < extractor.getTrackCount());
                extractor.selectTrack(mTrackIndex);
                while (true) {
                    if (strTok.nextToken() == StreamTokenizer.TT_EOF) break;
                    assertTrue("PTS mismatch exp/got: " + (long) strTok.nval + "/" +
                                    extractor.getSampleTime(),
                            Math.abs(extractor.getSampleTime() - (long) strTok.nval) <=
                                    PTS_TOLERANCE);
                    if (!extractor.advance()) break;
                }
                assertEquals(StreamTokenizer.TT_EOF, strTok.nextToken());
                assertTrue(!extractor.advance());
                extractor.release();
            }
        }
    }
}
