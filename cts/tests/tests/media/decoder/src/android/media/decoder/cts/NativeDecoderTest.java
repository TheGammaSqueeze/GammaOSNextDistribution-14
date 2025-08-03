/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaTestBase;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Adler32;

@SmallTest
@RequiresDevice
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class NativeDecoderTest extends MediaTestBase {
    private static final String TAG = "NativeDecoderTest";

    private static final int RESET_MODE_NONE = 0;
    private static final int RESET_MODE_RECONFIGURE = 1;
    private static final int RESET_MODE_FLUSH = 2;
    private static final int RESET_MODE_EOS_FLUSH = 3;

    private static final String[] CSD_KEYS = new String[] { "csd-0", "csd-1" };

    private static final int CONFIG_MODE_NONE = 0;
    private static final int CONFIG_MODE_QUEUE = 1;

    static final String mInpPrefix = WorkDir.getMediaDirString();
    short[] mMasterBuffer;

    static {
        // Load jni on initialization.
        Log.i("@@@", "before loadlibrary");
        System.loadLibrary("ctsmediadecodertest_jni");
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

    // check that native extractor behavior matches java extractor

    private void compareArrays(String message, int[] a1, int[] a2) {
        if (a1 == a2) {
            return;
        }

        assertNotNull(message + ": array 1 is null", a1);
        assertNotNull(message + ": array 2 is null", a2);

        assertEquals(message + ": arraylengths differ", a1.length, a2.length);
        int length = a1.length;

        for (int i = 0; i < length; i++)
            if (a1[i] != a2[i]) {
                Log.i("@@@@", Arrays.toString(a1));
                Log.i("@@@@", Arrays.toString(a2));
                fail(message + ": at index " + i);
            }
    }

    protected static AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        Preconditions.assertTestFileExists(mInpPrefix + res);
        File inpFile = new File(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    @Test
    public void testDataSource() throws Exception {
        int testsRun = testDecoder(
                "video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_11025hz.3gp", /* wrapFd */
                true, /* useCallback */ false);
        if (testsRun == 0) {
            MediaUtils.skipTest("no decoders found");
        }
    }

    @Test
    public void testDataSourceAudioOnly() throws Exception {
        int testsRun = testDecoder(
                "loudsoftmp3.mp3",
                /* wrapFd */ true, /* useCallback */ false) +
                testDecoder(
                        "loudsoftaac.aac",
                        /* wrapFd */ false, /* useCallback */ false);
        if (testsRun == 0) {
            MediaUtils.skipTest("no decoders found");
        }
    }

    @Test
    public void testDataSourceWithCallback() throws Exception {
        int testsRun = testDecoder(
                "video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_11025hz.3gp",/* wrapFd */
                true, /* useCallback */ true);
        if (testsRun == 0) {
            MediaUtils.skipTest("no decoders found");
        }
    }

    private int testDecoder(final String res) throws Exception {
        return testDecoder(res, /* wrapFd */ false, /* useCallback */ false);
    }

    private int testDecoder(final String res, boolean wrapFd, boolean useCallback)
            throws Exception {
        Preconditions.assertTestFileExists(mInpPrefix + res);
        if (!MediaUtils.hasCodecsForResource(mInpPrefix  + res)) {
            return 0; // skip
        }

        AssetFileDescriptor fd = getAssetFileDescriptorFor(res);

        int[] jdata1 = getDecodedData(
                fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        int[] jdata2 = getDecodedData(
                fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        int[] ndata1 = getDecodedDataNative(
                fd.getParcelFileDescriptor().getFd(), fd.getStartOffset(), fd.getLength(), wrapFd,
                useCallback);
        int[] ndata2 = getDecodedDataNative(
                fd.getParcelFileDescriptor().getFd(), fd.getStartOffset(), fd.getLength(), wrapFd,
                useCallback);

        fd.close();
        compareArrays("inconsistent java decoder", jdata1, jdata2);
        compareArrays("inconsistent native decoder", ndata1, ndata2);
        compareArrays("different decoded data", jdata1, ndata1);
        return 1;
    }

    private static int[] getDecodedData(FileDescriptor fd, long offset, long size)
            throws IOException {
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(fd, offset, size);
        return getDecodedData(ex);
    }
    private static int[] getDecodedData(MediaExtractor ex) throws IOException {
        int numtracks = ex.getTrackCount();
        assertTrue("no tracks", numtracks > 0);
        ArrayList<Integer>[] trackdata = new ArrayList[numtracks];
        MediaCodec[] codec = new MediaCodec[numtracks];
        MediaFormat[] format = new MediaFormat[numtracks];
        ByteBuffer[][] inbuffers = new ByteBuffer[numtracks][];
        ByteBuffer[][] outbuffers = new ByteBuffer[numtracks][];
        for (int i = 0; i < numtracks; i++) {
            format[i] = ex.getTrackFormat(i);
            String mime = format[i].getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/") || mime.startsWith("video/")) {
                codec[i] = MediaCodec.createDecoderByType(mime);
                codec[i].configure(format[i], null, null, 0);
                codec[i].start();
                inbuffers[i] = codec[i].getInputBuffers();
                outbuffers[i] = codec[i].getOutputBuffers();
                trackdata[i] = new ArrayList<>();
            } else {
                fail("unexpected mime type: " + mime);
            }
            ex.selectTrack(i);
        }

        boolean[] sawInputEOS = new boolean[numtracks];
        boolean[] sawOutputEOS = new boolean[numtracks];
        int eosCount = 0;
        BufferInfo info = new BufferInfo();
        while(eosCount < numtracks) {
            int t = ex.getSampleTrackIndex();
            if (t >= 0) {
                assertFalse("saw input EOS twice", sawInputEOS[t]);
                int bufidx = codec[t].dequeueInputBuffer(5000);
                if (bufidx >= 0) {
                    Log.i("@@@@", "track " + t + " buffer " + bufidx);
                    ByteBuffer buf = inbuffers[t][bufidx];
                    int sampleSize = ex.readSampleData(buf, 0);
                    Log.i("@@@@", "read " + sampleSize + " @ " + ex.getSampleTime());
                    if (sampleSize < 0) {
                        sampleSize = 0;
                        sawInputEOS[t] = true;
                        Log.i("@@@@", "EOS");
                        //break;
                    }
                    long presentationTimeUs = ex.getSampleTime();

                    codec[t].queueInputBuffer(bufidx, 0, sampleSize, presentationTimeUs,
                            sawInputEOS[t] ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    ex.advance();
                }
            } else {
                Log.i("@@@@", "no more input samples");
                for (int tt = 0; tt < codec.length; tt++) {
                    if (!sawInputEOS[tt]) {
                        // we ran out of samples without ever signaling EOS to the codec,
                        // so do that now
                        int bufidx = codec[tt].dequeueInputBuffer(5000);
                        if (bufidx >= 0) {
                            codec[tt].queueInputBuffer(bufidx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS[tt] = true;
                        }
                    }
                }
            }

            // see if any of the codecs have data available
            for (int tt = 0; tt < codec.length; tt++) {
                if (!sawOutputEOS[tt]) {
                    int status = codec[tt].dequeueOutputBuffer(info, 1);
                    if (status >= 0) {
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.i("@@@@", "EOS on track " + tt);
                            sawOutputEOS[tt] = true;
                            eosCount++;
                        }
                        Log.i("@@@@", "got decoded buffer for track " + tt + ", size " + info.size);
                        if (info.size > 0) {
                            addSampleData(trackdata[tt], outbuffers[tt][status], info.size,
                                    format[tt]);
                        }
                        codec[tt].releaseOutputBuffer(status, false);
                    } else if (status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        Log.i("@@@@", "output buffers changed for track " + tt);
                        outbuffers[tt] = codec[tt].getOutputBuffers();
                    } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        format[tt] = codec[tt].getOutputFormat();
                        Log.i("@@@@", "format changed for track " + t + ": " +
                                format[tt].toString());
                    } else if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.i("@@@@", "no buffer right now for track " + tt);
                    } else {
                        Log.i("@@@@", "unexpected info code for track " + tt + ": " + status);
                    }
                } else {
                    Log.i("@@@@", "already at EOS on track " + tt);
                }
            }
        }

        int totalsize = 0;
        for (int i = 0; i < numtracks; i++) {
            totalsize += trackdata[i].size();
        }
        int[] trackbytes = new int[totalsize];
        int idx = 0;
        for (int i = 0; i < numtracks; i++) {
            ArrayList<Integer> src = trackdata[i];
            for (Integer integer : src) {
                trackbytes[idx++] = integer;
            }
        }

        for (MediaCodec mediaCodec : codec) {
            mediaCodec.release();
        }

        return trackbytes;
    }

    static void addSampleData(ArrayList<Integer> dst,
                              ByteBuffer buf, int size, MediaFormat format) throws IOException{

        Log.i("@@@", "addsample " + dst.size() + "/" + size);
        int width = format.getInteger(MediaFormat.KEY_WIDTH, size);
        int stride = format.getInteger(MediaFormat.KEY_STRIDE, width);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT, 1);
        byte[] bb = new byte[width * height];
        int offset = buf.position();
        for (int i = 0; i < height; i++) {
            buf.position(i * stride + offset);
            buf.get(bb, i * width, width);
        }
        // bb is filled with data
        long sum = adler32(bb);
        dst.add( (int) (sum & 0xffffffff));
    }

    private final static Adler32 checksummer = new Adler32();
    // simple checksum computed over every decoded buffer
    static int adler32(byte[] input) {
        checksummer.reset();
        checksummer.update(input);
        int ret = (int) checksummer.getValue();
        Log.i("@@@", "adler " + input.length + "/" + ret);
        return ret;
    }

    private static native int[] getDecodedDataNative(int fd, long offset, long size, boolean wrapFd,
                                                     boolean useCallback)
            throws IOException;
}

