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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.AssetFileDescriptor;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.Preconditions;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@RunWith(Enclosed.class)
public class ExtractorUnitTest {
    private static final int MAX_SAMPLE_SIZE = 4 * 1024 * 1024;
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final String INPUT_MEDIA = "ForBiggerEscapes.mp4";
    private static final String TAG = "ExtractorUnitTest";

    private static boolean sIsAtLeastS = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S);

    @SmallTest
    public static class TestApi {
        @Rule
        public TestName testName = new TestName();

        @ApiTest(apis = "android.media.MediaExtractor#getTrackCount")
        @Test
        public void testGetTrackCountBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                assertTrue("received valid trackCount before setDataSource",
                        extractor.getTrackCount() <= 0);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getTrackCount")
        @Test
        public void testGetTrackCountAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.getTrackCount();
                fail("getTrackCount succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#selectTrack")
        @Test
        public void testSelectTrackBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.selectTrack(0);
                fail("selectTrack succeeds before setDataSource");
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#selectTrack")
        @Test
        public void testSelectTrackForInvalidIndex() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                try {
                    extractor.selectTrack(-1);
                    fail("selectTrack succeeds for track index: -1");
                } catch (Exception e) {
                    // expected
                }
                try {
                    extractor.selectTrack(extractor.getTrackCount());
                    fail("selectTrack succeeds for out of bounds track index: " +
                            extractor.getTrackCount());
                } catch (Exception e) {
                    // expected
                }
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#selectTrack")
        @Test
        public void testIdempotentSelectTrack() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                extractor.selectTrack(0);
                extractor.selectTrack(0);
            } catch (Exception e) {
                fail("multiple selection of same track has failed");
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#selectTrack")
        @Test
        public void testSelectTrackAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.selectTrack(0);
                fail("selectTrack succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#unselectTrack")
        @Test
        public void testUnselectTrackBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.unselectTrack(0);
                fail("unselectTrack succeeds before setDataSource");
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#unselectTrack")
        @Test
        public void testUnselectTrackForInvalidIndex() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                try {
                    extractor.unselectTrack(-1);
                    fail("unselectTrack succeeds for track index: -1");
                } catch (Exception e) {
                    // expected
                }
                try {
                    extractor.unselectTrack(extractor.getTrackCount());
                    fail("unselectTrack succeeds for out of bounds track index: " +
                            extractor.getTrackCount());
                } catch (Exception e) {
                    // expected
                }
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#unselectTrack")
        @Test
        public void testUnselectTrackForUnSelectedTrackIndex() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                extractor.unselectTrack(0);
            } catch (Exception e) {
                fail("un-selection of non-selected track has failed");
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#unselectTrack")
        @Test
        public void testIdempotentUnselectTrack() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                extractor.selectTrack(0);
                extractor.unselectTrack(0);
                extractor.unselectTrack(0);
            } catch (Exception e) {
                fail("multiple un-selection of selected track has failed");
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#unselectTrack")
        @Test
        public void testUnselectTrackAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.unselectTrack(0);
                fail("unselectTrack succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#seekTo")
        @Test
        public void testSeekToBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.seekTo(33000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                if (extractor.getSampleTime() != -1 || extractor.getSampleSize() != -1 ||
                        extractor.getSampleFlags() != -1) {
                    fail("seekTo() succeeds before setting data source, returns non-negative " +
                            "sampleTime / sampleSize / sampleFlags");
                }
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#seekTo")
        @Test
        public void testSeekToBeforeSelectTrack() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                extractor.seekTo(33000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                if (extractor.getSampleTime() != -1 || extractor.getSampleSize() != -1 ||
                        extractor.getSampleFlags() != -1) {
                    fail("seekTo() succeeds before selectTrack, returns non-negative " +
                            "sampleTime / sampleSize / sampleFlags");
                }
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#seekTo")
        @Test
        public void testSeekToForInvalidMode() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            extractor.selectTrack(0);
            long pts = 33000;
            try {
                extractor.seekTo(pts, (int) pts);
                fail("seekTo() succeeds for invalid mode: " + pts);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#seekTo")
        @Test
        public void testSeekToAfterRelease() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            extractor.release();
            try {
                extractor.seekTo(33000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                fail("seekTo() succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getCachedDuration")
        @Test
        @Ignore("TODO(b/148205432)")
        public void testGetCachedDurationBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                assertTrue("received valid cachedDuration before setDataSource",
                        extractor.getCachedDuration() == -1);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getCachedDuration")
        @Test
        public void testGetCachedDurationAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.getCachedDuration();
                fail("cachedDuration succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#hasCacheReachedEndOfStream")
        @Test
        @Ignore("TODO(b/148204634)")
        public void testHasCacheReachedEOSBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                assertTrue("unexpected value received from hasCacheReachedEndOfStream before" +
                        " setDataSource", !extractor.hasCacheReachedEndOfStream());
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#hasCacheReachedEndOfStream")
        @Test
        public void testHasCacheReachedEOSAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.hasCacheReachedEndOfStream();
                fail("hasCacheReachedEndOfStream succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getMetrics")
        @Test
        public void testGetMetricsBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                assertTrue("received valid metrics before setDataSource",
                        extractor.getMetrics() == null);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getMetrics")
        @Test
        public void testGetMetricsAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.getMetrics();
                fail("getMetrics() succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#release")
        @Test
        public void testIdempotentRelease() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.release();
                extractor.release();
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#advance")
        @Test
        public void testAdvanceBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                assertTrue("advance succeeds before setDataSource", !extractor.advance());
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#advance")
        @Test
        public void testAdvanceBeforeSelectTrack() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                assertTrue("advance succeeds without any active tracks", !extractor.advance());
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#advance")
        @Test
        public void testAdvanceAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.advance();
                fail("advance succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleFlags")
        @Test
        public void testGetSampleFlagsBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                assertTrue("received valid sampleFlag before setDataSource",
                        extractor.getSampleFlags() == -1);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleFlags")
        @Test
        public void testGetSampleFlagsBeforeSelectTrack() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                assertTrue("received valid sampleFlag without any active tracks",
                        extractor.getSampleFlags() == -1);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleFlags")
        @Test
        public void testGetSampleFlagsAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.getSampleFlags();
                fail("getSampleFlags succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleTime")
        @Test
        public void testGetSampleTimeBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                assertTrue("received valid sampleTime before setDataSource",
                        extractor.getSampleTime() == -1);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleTime")
        @Test
        public void testGetSampleTimeBeforeSelectTrack() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                assertTrue("received valid sampleTime without any active tracks",
                        extractor.getSampleTime() == -1);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleTime")
        @Test
        public void testGetSampleTimeAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.getSampleTime();
                fail("getSampleTime succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleSize")
        @Test
        public void testGetSampleSizeBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                assertTrue("received valid sampleSize before setDataSource",
                        extractor.getSampleSize() == -1);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleSize")
        @Test
        public void testGetSampleSizeBeforeSelectTrack() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                assertTrue("received valid sampleSize without any active tracks",
                        extractor.getSampleSize() == -1);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleSize")
        @Test
        public void testGetSampleSizeAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.getSampleSize();
                fail("getSampleSize succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleTrackIndex")
        @Test
        public void testGetSampleTrackIndexBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                assertTrue("received valid sampleTrackIndex before setDataSource",
                        extractor.getSampleTrackIndex() == -1);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleTrackIndex")
        @Test
        public void testGetSampleTrackIndexBeforeSelectTrack() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                assertTrue("received valid sampleTrackIndex without any active tracks",
                        extractor.getSampleTrackIndex() == -1);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getSampleTrackIndex")
        @Test
        public void testGetSampleTrackIndexAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.getSampleTrackIndex();
                fail("getSampleTrackIndex succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getTrackFormat")
        @Test
        public void testGetTrackFormatBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.getTrackFormat(0);
                fail("getTrackFormat succeeds before setDataSource");
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getTrackFormat")
        @Test
        public void testGetTrackFormatForInvalidIndex() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                try {
                    extractor.getTrackFormat(-1);
                    fail("getTrackFormat succeeds for track index: -1");
                } catch (Exception e) {
                    // expected
                }
                try {
                    extractor.getTrackFormat(extractor.getTrackCount());
                    fail("getTrackFormat succeeds for out of bounds track index: " +
                            extractor.getTrackCount());
                } catch (Exception e) {
                    // expected
                }
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#getTrackFormat")
        @Test
        public void testGetTrackFormatAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            extractor.release();
            try {
                extractor.getTrackFormat(0);
                fail("getTrackFormat succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#readSampleData")
        @Test
        public void testReadSampleDataBeforeSetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE);
            try {
                assertTrue("readSampleData returns val >= 0 before setDataSource",
                        extractor.readSampleData(byteBuffer, 0) < 0);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#readSampleData")
        @Test
        public void testReadSampleDataBeforeSelectTrack() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE);
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            try {
                assertTrue("readSampleData returns val >= 0 without any active tracks",
                        extractor.readSampleData(byteBuffer, 0) < 0);
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#readSampleData")
        @Test
        public void testIfInvalidOffsetIsRejectedByReadSampleData() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE);
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            extractor.setDataSource(MEDIA_DIR + INPUT_MEDIA);
            extractor.selectTrack(0);
            try {
                // readSampleData with negative offset
                try {
                    extractor.readSampleData(byteBuffer, -1);
                    fail("readSampleData succeeds with negative offset");
                } catch (Exception e) {
                    // expected
                }

                // readSampleData with byteBuffer's capacity - offset < frame size
                int sampleSize = (int) extractor.getSampleSize();
                try {
                    extractor.readSampleData(byteBuffer, MAX_SAMPLE_SIZE - sampleSize + 1);
                    fail("readSampleData succeeds when available size of byteBuffer is less than " +
                            "frame size");
                } catch (Exception e) {
                    // expected
                }
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#readSampleData")
        @Test
        public void testReadSampleDataAfterRelease() {
            MediaExtractor extractor = new MediaExtractor();
            ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_SAMPLE_SIZE);
            extractor.release();
            try {
                extractor.readSampleData(byteBuffer, 0);
                fail("readSampleData succeeds after release");
            } catch (Exception e) {
                // expected
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#setDataSource")
        @Test
        public void testIfInvalidDataSourceIsRejectedBySetDataSource() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            TestMediaDataSource dataSource =
                    TestMediaDataSource.fromString(MEDIA_DIR + INPUT_MEDIA, false, true);
            try {
                extractor.setDataSource(dataSource);
                fail("setDataSource succeeds with malformed media data source");
            } catch (Exception e) {
                // expected
            } finally {
                assertTrue(dataSource.isClosed());
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#setDataSource")
        @Test
        public void testIfNullFDIsRejectedBySetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource((FileDescriptor) null);
                fail("setDataSource succeeds with null fd");
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#setDataSource")
        @Test
        public void testIfWriteOnlyAssetFDIsRejectedBySetDataSource() throws IOException {
            File inpFile = File.createTempFile("ExtractorTestApisetDSAFD", ".in");
            MediaExtractor extractor = new MediaExtractor();
            try (ParcelFileDescriptor parcelFD = ParcelFileDescriptor
                    .open(inpFile, ParcelFileDescriptor.MODE_WRITE_ONLY);
                 AssetFileDescriptor afd = new AssetFileDescriptor(parcelFD, 0,
                         AssetFileDescriptor.UNKNOWN_LENGTH)) {
                extractor.setDataSource(afd);
                fail("setDataSource succeeds write only afd");
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
            inpFile.delete();
        }

        @ApiTest(apis = "android.media.MediaExtractor#setDataSource")
        @Test
        public void testIfWriteOnlyFDIsRejectedBySetDataSource() throws IOException {
            MediaExtractor extractor = new MediaExtractor();
            File inpFile = File.createTempFile("ExtractorTestApisetDSFD", ".in");
            try (FileOutputStream fOut = new FileOutputStream(inpFile)) {
                extractor.setDataSource(fOut.getFD());
                fail("setDataSource succeeds write only fd");
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
            inpFile.delete();
        }

        @ApiTest(apis = "android.media.MediaExtractor#setDataSource")
        @Test
        public void testIfNullMediaDataSourceIsRejectedBySetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource((MediaDataSource) null);
                fail("setDataSource succeeds with null data source");
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#setDataSource")
        @Test
        public void testIfNullFileIsRejectedBySetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource((String) null);
                fail("setDataSource succeeds with null file path");
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }

        @ApiTest(apis = "android.media.MediaExtractor#setDataSource")
        @Test
        public void testIfNullAssetFDIsRejectedBySetDataSource() {
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource((AssetFileDescriptor) null);
                fail("setDataSource succeeds with null asset fd");
            } catch (Exception e) {
                // expected
            } finally {
                extractor.release();
            }
        }
    }

    @SmallTest
    public static class TestApiNative {
        @Rule
        public TestName testName = new TestName();

        static {
            System.loadLibrary("ctsmediav2extractor_jni");
        }

        @ApiTest(apis = "AMediaExtractor_getTrackCount")
        @Test
        public void testGetTrackCountBeforeSetDataSource() {
            assertTrue(nativeTestGetTrackCountBeforeSetDataSource());
        }
        private native boolean nativeTestGetTrackCountBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_selectTrack")
        @Test
        public void testSelectTrackBeforeSetDataSource() {
            assertTrue(nativeTestSelectTrackBeforeSetDataSource());
        }
        private native boolean nativeTestSelectTrackBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_selectTrack")
        @Test
        public void testSelectTrackForInvalidIndex() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestSelectTrackForInvalidIndex(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestSelectTrackForInvalidIndex(String srcPath);

        @ApiTest(apis = "AMediaExtractor_selectTrack")
        @Test
        public void testIdempotentSelectTrack() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestIdempotentSelectTrack(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestIdempotentSelectTrack(String srcPath);

        @ApiTest(apis = "AMediaExtractor_unselectTrack")
        @Test
        public void testUnselectTrackBeforeSetDataSource() {
            assertTrue(nativeTestUnselectTrackBeforeSetDataSource());
        }
        private native boolean nativeTestUnselectTrackBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_unselectTrack")
        @Test
        public void testUnselectTrackForInvalidIndex() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestUnselectTrackForInvalidIndex(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestUnselectTrackForInvalidIndex(String srcPath);

        @ApiTest(apis = "AMediaExtractor_unselectTrack")
        @Test
        public void testUnselectTrackForUnSelectedTrackIndex() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestUnselectTrackForUnSelectedTrackIndex(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestUnselectTrackForUnSelectedTrackIndex(String srcPath);

        @ApiTest(apis = "AMediaExtractor_unselectTrack")
        @Test
        public void testIdempotentUnselectTrack() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestIdempotentUnselectTrack(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestIdempotentUnselectTrack(String srcPath);

        @ApiTest(apis = "AMediaExtractor_seekTo")
        @Test
        public void testSeekToBeforeSetDataSource() {
            assertTrue(nativeTestSeekToBeforeSetDataSource());
        }
        private native boolean nativeTestSeekToBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_seekTo")
        @Test
        public void testSeekToBeforeSelectTrack() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestSeekToBeforeSelectTrack(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestSeekToBeforeSelectTrack(String srcPath);

        @ApiTest(apis = "AMediaExtractor_getCachedDuration")
        @Test
        @Ignore("TODO(b/148205432)")
        public void testGetCachedDurationBeforeSetDataSource() {
            assertTrue(nativeTestGetCachedDurationBeforeSetDataSource());
        }
        private native boolean nativeTestGetCachedDurationBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_getFileFormat")
        @Test
        public void testIfGetFileFormatSucceedsBeforeSetDataSource() {
            assertTrue(nativeTestIfGetFileFormatSucceedsBeforeSetDataSource());
        }
        private native boolean nativeTestIfGetFileFormatSucceedsBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_advance")
        @Test
        public void testAdvanceBeforeSetDataSource() {
            assertTrue(nativeTestAdvanceBeforeSetDataSource());
        }
        private native boolean nativeTestAdvanceBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_advance")
        @Test
        public void testAdvanceBeforeSelectTrack() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestAdvanceBeforeSelectTrack(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestAdvanceBeforeSelectTrack(String srcPath);

        @ApiTest(apis = "AMediaExtractor_getSampleFlags")
        @Test
        public void testGetSampleFlagsBeforeSetDataSource() {
            assertTrue(nativeTestGetSampleFlagsBeforeSetDataSource());
        }
        private native boolean nativeTestGetSampleFlagsBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_getSampleFlags")
        @Test
        public void testGetSampleFlagsBeforeSelectTrack() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestGetSampleFlagsBeforeSelectTrack(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestGetSampleFlagsBeforeSelectTrack(String srcPath);

        @ApiTest(apis = "AMediaExtractor_getSampleTime")
        @Test
        public void testGetSampleTimeBeforeSetDataSource() {
            assertTrue(nativeTestGetSampleTimeBeforeSetDataSource());
        }
        private native boolean nativeTestGetSampleTimeBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_getSampleTime")
        @Test
        public void testGetSampleTimeBeforeSelectTrack() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestGetSampleTimeBeforeSelectTrack(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestGetSampleTimeBeforeSelectTrack(String srcPath);

        @ApiTest(apis = "AMediaExtractor_getSampleSize")
        @Test
        public void testGetSampleSizeBeforeSetDataSource() {
            assertTrue(nativeTestGetSampleSizeBeforeSetDataSource());
        }
        private native boolean nativeTestGetSampleSizeBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_getSampleSize")
        @Test
        public void testGetSampleSizeBeforeSelectTrack() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestGetSampleSizeBeforeSelectTrack(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestGetSampleSizeBeforeSelectTrack(String srcPath);

        @ApiTest(apis = "AMediaExtractor_getSampleFormat")
        @Test
        public void testIfGetSampleFormatBeforeSetDataSource() {
            assertTrue(nativeTestIfGetSampleFormatBeforeSetDataSource());
        }
        private native boolean nativeTestIfGetSampleFormatBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_getSampleFormat")
        @Test
        public void testIfGetSampleFormatBeforeSelectTrack() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestIfGetSampleFormatBeforeSelectTrack(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestIfGetSampleFormatBeforeSelectTrack(String srcPath);

        @ApiTest(apis = "AMediaExtractor_getSampleTrackIndex")
        @Test
        public void testGetSampleTrackIndexBeforeSetDataSource() {
            assertTrue(nativeTestGetSampleTrackIndexBeforeSetDataSource());
        }
        private native boolean nativeTestGetSampleTrackIndexBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_getSampleTrackIndex")
        @Test
        public void testGetSampleTrackIndexBeforeSelectTrack() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(
                    nativeTestGetSampleTrackIndexBeforeSelectTrack(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestGetSampleTrackIndexBeforeSelectTrack(String srcPath);

        @ApiTest(apis = "AMediaExtractor_getTrackFormat")
        @Test
        public void testGetTrackFormatBeforeSetDataSource() {
            assertTrue(nativeTestGetTrackFormatBeforeSetDataSource());
        }
        private native boolean nativeTestGetTrackFormatBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_getTrackFormat")
        @Test
        public void testGetTrackFormatForInvalidIndex() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestGetTrackFormatForInvalidIndex(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestGetTrackFormatForInvalidIndex(String srcPath);

        @ApiTest(apis = "AMediaExtractor_readSampleData")
        @Test
        public void testReadSampleDataBeforeSetDataSource() {
            assertTrue(nativeTestReadSampleDataBeforeSetDataSource());
        }
        private native boolean nativeTestReadSampleDataBeforeSetDataSource();

        @ApiTest(apis = "AMediaExtractor_readSampleData")
        @Test
        public void testReadSampleDataBeforeSelectTrack() {
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestReadSampleDataBeforeSelectTrack(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestReadSampleDataBeforeSelectTrack(String srcPath);

        @ApiTest(apis = "AMediaExtractor_setDataSource")
        @Test
        public void testIfNullLocationIsRejectedBySetDataSource() {
            assertTrue(nativeTestIfNullLocationIsRejectedBySetDataSource());
        }
        private native boolean nativeTestIfNullLocationIsRejectedBySetDataSource();

        @ApiTest(apis = "AMEDIAFORMAT_KEY_SAMPLE_FILE_OFFSET")
        @Test
        public void testVideoSampleFileOffsetByGetSampleFormat() {
            if (!sIsAtLeastS) {
                Log.d(TAG, "testVideoSampleFileOffsetByGetSampleFormat requires Android 12");
                return;
            }
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestVideoSampleFileOffsetByGetSampleFormat(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestVideoSampleFileOffsetByGetSampleFormat(String srcPath);

        @ApiTest(apis = "AMEDIAFORMAT_KEY_SAMPLE_FILE_OFFSET")
        @Test
        public void testAudioSampleFileOffsetByGetSampleFormat() {
            if (!sIsAtLeastS) {
                Log.d(TAG, "testAudioSampleFileOffsetByGetSampleFormat requires Android 12");
                return;
            }
            Preconditions.assertTestFileExists(MEDIA_DIR + INPUT_MEDIA);
            assertTrue(nativeTestAudioSampleFileOffsetByGetSampleFormat(MEDIA_DIR + INPUT_MEDIA));
        }
        private native boolean nativeTestAudioSampleFileOffsetByGetSampleFormat(String srcPath);
    }
}
