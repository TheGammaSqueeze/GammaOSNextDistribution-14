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

package android.media.misc.cts;

import static org.junit.Assert.assertTrue;

import android.content.res.AssetFileDescriptor;
import android.media.MediaExtractor;
import android.media.cts.MediaTestBase;
import android.media.cts.TestUtils.Monitor;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;
import android.webkit.cts.CtsTestServer;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.Preconditions;

import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.impl.io.SocketOutputBuffer;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.util.CharArrayBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
@RequiresDevice
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class NativeDecoderTest extends MediaTestBase {
    private static final String TAG = "DecoderTest";

    private static final boolean sIsAtLeastS = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S);

    static final String mInpPrefix = WorkDir.getMediaDirString();
    short[] mMasterBuffer;

    static {
        // Load jni on initialization.
        Log.i("@@@", "before loadlibrary");
        System.loadLibrary("ctsmediamisc_jni");
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

    protected static AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        Preconditions.assertTestFileExists(mInpPrefix + res);
        File inpFile = new File(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    @Presubmit
    @Test
    public void testFormat() throws Exception {
        assertTrue("media format fail, see log for details", testFormatNative());
    }

    private static native boolean testFormatNative();

    @Presubmit
    @Test
    public void testPssh() throws Exception {
        testPssh("psshtest.mp4");
    }

    private void testPssh(final String res) throws Exception {
        AssetFileDescriptor fd = getAssetFileDescriptorFor(res);

        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(fd.getParcelFileDescriptor().getFileDescriptor(),
                fd.getStartOffset(), fd.getLength());
        testPssh(ex);
        ex.release();

        boolean ret = testPsshNative(
                fd.getParcelFileDescriptor().getFd(), fd.getStartOffset(), fd.getLength());
        assertTrue("native pssh error", ret);
    }

    private static void testPssh(MediaExtractor ex) {
        Map<UUID, byte[]> map = ex.getPsshInfo();
        Set<UUID> keys = map.keySet();
        for (UUID uuid: keys) {
            Log.i("@@@", "uuid: " + uuid + ", data size " +
                    map.get(uuid).length);
        }
    }

    private static native boolean testPsshNative(int fd, long offset, long size);

    @Test
    public void testCryptoInfo() throws Exception {
        assertTrue("native cryptoinfo failed, see log for details", testCryptoInfoNative());
    }

    private static native boolean testCryptoInfoNative();

    @Presubmit
    @Test
    public void testMediaFormat() throws Exception {
        assertTrue("native mediaformat failed, see log for details", testMediaFormatNative());
    }

    private static native boolean testMediaFormatNative();

    @Presubmit
    @Test
    public void testAMediaDataSourceClose() throws Throwable {

        final CtsTestServer slowServer = new SlowCtsTestServer();
        final String url = slowServer.getAssetUrl("noiseandchirps.ogg");
        final long ds = createAMediaDataSource(url);
        final long ex = createAMediaExtractor();

        try {
            setAMediaExtractorDataSourceAndFailIfAnr(ex, ds);
        } finally {
            slowServer.shutdown();
            deleteAMediaExtractor(ex);
            deleteAMediaDataSource(ds);
        }

    }

    private void setAMediaExtractorDataSourceAndFailIfAnr(final long ex, final long ds)
            throws Throwable {
        final Monitor setAMediaExtractorDataSourceDone = new Monitor();
        final int HEAD_START_MILLIS = 1000;
        final int ANR_TIMEOUT_MILLIS = 2500;
        final int JOIN_TIMEOUT_MILLIS = 1500;

        Thread setAMediaExtractorDataSourceThread = new Thread() {
            public void run() {
                setAMediaExtractorDataSource(ex, ds);
                setAMediaExtractorDataSourceDone.signal();
            }
        };

        try {
            setAMediaExtractorDataSourceThread.start();
            Thread.sleep(HEAD_START_MILLIS);
            closeAMediaDataSource(ds);
            boolean closed = setAMediaExtractorDataSourceDone.waitForSignal(ANR_TIMEOUT_MILLIS);
            assertTrue("close took longer than " + ANR_TIMEOUT_MILLIS, closed);
        } finally {
            setAMediaExtractorDataSourceThread.join(JOIN_TIMEOUT_MILLIS);
        }

    }

    private class SlowCtsTestServer extends CtsTestServer {

        private static final int SERVER_DELAY_MILLIS = 5000;
        private final CountDownLatch mDisconnected = new CountDownLatch(1);

        SlowCtsTestServer() throws Exception {
            super(mContext);
        }

        @Override
        protected DefaultHttpServerConnection createHttpServerConnection() {
            return new SlowHttpServerConnection(mDisconnected, SERVER_DELAY_MILLIS);
        }

        @Override
        public void shutdown() {
            mDisconnected.countDown();
            super.shutdown();
        }
    }

    private static class SlowHttpServerConnection extends DefaultHttpServerConnection {

        private final CountDownLatch mDisconnected;
        private final int mDelayMillis;

        public SlowHttpServerConnection(CountDownLatch disconnected, int delayMillis) {
            mDisconnected = disconnected;
            mDelayMillis = delayMillis;
        }

        @Override
        protected SessionOutputBuffer createHttpDataTransmitter(
                Socket socket, int buffersize, HttpParams params) throws IOException {
            return createSessionOutputBuffer(socket, buffersize, params);
        }

        SessionOutputBuffer createSessionOutputBuffer(
                Socket socket, int buffersize, HttpParams params) throws IOException {
            return new SocketOutputBuffer(socket, buffersize, params) {
                @Override
                public void write(byte[] b) throws IOException {
                    write(b, 0, b.length);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    while (len-- > 0) {
                        write(b[off++]);
                    }
                }

                @Override
                public void writeLine(String s) throws IOException {
                    delay();
                    super.writeLine(s);
                }

                @Override
                public void writeLine(CharArrayBuffer buffer) throws IOException {
                    delay();
                    super.writeLine(buffer);
                }

                @Override
                public void write(int b) throws IOException {
                    delay();
                    super.write(b);
                }

                private void delay() throws IOException {
                    try {
                        mDisconnected.await(mDelayMillis, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        // Ignored
                    }
                }

            };
        }
    }

    private static native long createAMediaExtractor();
    private static native long createAMediaDataSource(String url);
    private static native int  setAMediaExtractorDataSource(long ex, long ds);
    private static native void closeAMediaDataSource(long ds);
    private static native void deleteAMediaExtractor(long ex);
    private static native void deleteAMediaDataSource(long ds);

}

