/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.os.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.Parcelable;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.util.concurrent.AbstractFuture;

import junit.framework.ComparisonFailure;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class ParcelFileDescriptorTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule.Builder()
            .setProvideMainThread(true).build();

    private static final long DURATION = 100l;

    @Test
    public void testConstructorAndOpen() throws Exception {
        ParcelFileDescriptor tempFile = makeParcelFileDescriptor();

        ParcelFileDescriptor pfd = new ParcelFileDescriptor(tempFile);
        AutoCloseInputStream in = new AutoCloseInputStream(pfd);
        try {
            // read the data that was wrote previously
            assertEquals(0, in.read());
            assertEquals(1, in.read());
            assertEquals(2, in.read());
            assertEquals(3, in.read());
        } finally {
            in.close();
        }
    }

    @Test
    public void testDetachAdopt() throws Exception {
        ParcelFileDescriptor tempFile = makeParcelFileDescriptor();

        ParcelFileDescriptor before = new ParcelFileDescriptor(tempFile);
        ParcelFileDescriptor after = ParcelFileDescriptor.adoptFd(before.detachFd());

        // Verify reading from post-adopted FD works
        try (AutoCloseInputStream in = new AutoCloseInputStream(after)) {
            assertEquals(0, in.read());
            assertEquals(1, in.read());
            assertEquals(2, in.read());
            assertEquals(3, in.read());
        }

        // Verify trying to detach a second time fails
        assertThrows(IllegalStateException.class, () -> {
            before.detachFd();
        });
    }

    @Test
    public void testReadOnly() throws Exception {
        final File file = File.createTempFile("testReadOnly", "bin");
        file.createNewFile();
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY);
             ParcelFileDescriptor.AutoCloseOutputStream out =
                     new ParcelFileDescriptor.AutoCloseOutputStream(pfd)) {
            assertThrows(IOException.class, () -> {
                out.write(42);
            });
        }
    }

    @Test
    public void testNormal() throws Exception {
        final File file = File.createTempFile("testNormal", "bin");
        doMultiWrite(file, ParcelFileDescriptor.MODE_READ_WRITE
                | ParcelFileDescriptor.MODE_CREATE);
        assertArrayEquals(new byte[]{21, 42}, Files.readAllBytes(file.toPath()));
    }

    @Test
    public void testAppend() throws Exception {
        final File file = File.createTempFile("testAppend", "bin");
        doMultiWrite(file, ParcelFileDescriptor.MODE_READ_WRITE
                | ParcelFileDescriptor.MODE_CREATE
                | ParcelFileDescriptor.MODE_APPEND);
        assertArrayEquals(new byte[]{42, 42, 21}, Files.readAllBytes(file.toPath()));
    }

    @Test
    public void testTruncate() throws Exception {
        final File file = File.createTempFile("testTruncate", "bin");
        doMultiWrite(file, ParcelFileDescriptor.MODE_READ_WRITE
                | ParcelFileDescriptor.MODE_CREATE
                | ParcelFileDescriptor.MODE_TRUNCATE);
        assertArrayEquals(new byte[]{21}, Files.readAllBytes(file.toPath()));
    }

    private void doMultiWrite(File file, int flags) throws Exception {
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, flags);
             ParcelFileDescriptor.AutoCloseOutputStream out =
                     new ParcelFileDescriptor.AutoCloseOutputStream(pfd)) {
            out.write(42);
            out.write(42);
        }
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, flags);
             ParcelFileDescriptor.AutoCloseOutputStream out =
                     new ParcelFileDescriptor.AutoCloseOutputStream(pfd)) {
            out.write(21);
        }
    }

    private static class DoneSignal extends AbstractFuture<Void> {
        public boolean set() {
            return super.set(null);
        }

        @Override
        public boolean setException(Throwable t) {
            return super.setException(t);
        }
    }

    @Test
    @AppModeFull // opening a listening socket not permitted for instant apps
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testFromSocket() throws Throwable {
        final int PORT = 12222;
        final int DATA = 1;

        final DoneSignal done = new DoneSignal();

        final Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket ss;
                    ss = new ServerSocket(PORT);
                    Socket sSocket = ss.accept();
                    OutputStream out = sSocket.getOutputStream();
                    out.write(DATA);
                    Thread.sleep(DURATION);
                    out.close();
                    done.set();
                } catch (Exception e) {
                    done.setException(e);
                }
            }
        });
        t.start();

        Thread.sleep(DURATION);
        Socket socket;
        socket = new Socket(InetAddress.getLocalHost(), PORT);
        ParcelFileDescriptor pfd = ParcelFileDescriptor.fromSocket(socket);
        AutoCloseInputStream in = new AutoCloseInputStream(pfd);
        assertEquals(DATA, in.read());
        in.close();
        socket.close();
        pfd.close();

        done.get(5, TimeUnit.SECONDS);
    }

    private static void assertFileDescriptorContent(byte[] expected, ParcelFileDescriptor fd)
        throws IOException {
        assertInputStreamContent(expected, new ParcelFileDescriptor.AutoCloseInputStream(fd));
    }

    private static void assertInputStreamContent(byte[] expected, InputStream is)
            throws IOException {
        try {
            byte[] observed = new byte[expected.length];
            int count = is.read(observed);
            assertEquals(expected.length, count);
            assertEquals(-1, is.read());
            assertArrayEquals(expected, observed);
        } finally {
            is.close();
        }
    }

    @Test
    public void testToString() throws Exception {
        ParcelFileDescriptor pfd = makeParcelFileDescriptor();
        assertNotNull(pfd.toString());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = Parcel.class)
    public void testWriteToParcel() throws Exception {
        ParcelFileDescriptor pf = makeParcelFileDescriptor();

        Parcel pl = Parcel.obtain();
        pf.writeToParcel(pl, ParcelFileDescriptor.PARCELABLE_WRITE_RETURN_VALUE);
        pl.setDataPosition(0);
        ParcelFileDescriptor pfd = ParcelFileDescriptor.CREATOR.createFromParcel(pl);
        AutoCloseInputStream in = new AutoCloseInputStream(pfd);
        try {
            // read the data that was wrote previously
            assertEquals(0, in.read());
            assertEquals(1, in.read());
            assertEquals(2, in.read());
            assertEquals(3, in.read());
        } finally {
            in.close();
        }
    }

    @Test
    public void testClose() throws Exception {
        ParcelFileDescriptor pf = makeParcelFileDescriptor();
        AutoCloseInputStream in1 = new AutoCloseInputStream(pf);
        try {
            assertEquals(0, in1.read());
        } finally {
            in1.close();
        }

        pf.close();

        AutoCloseInputStream in2 = new AutoCloseInputStream(pf);
        try {
            assertEquals(0, in2.read());
            fail("Failed to throw exception.");
        } catch (Exception e) {
            // expected
        } finally {
            in2.close();
        }
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testGetStatSize() throws Exception {
        ParcelFileDescriptor pf = makeParcelFileDescriptor();
        assertTrue(pf.getStatSize() >= 0);
    }

    @Test
    public void testGetFileDescriptor() throws Exception {
        ParcelFileDescriptor pfd = makeParcelFileDescriptor();
        assertNotNull(pfd.getFileDescriptor());

        ParcelFileDescriptor p = new ParcelFileDescriptor(pfd);
        assertSame(pfd.getFileDescriptor(), p.getFileDescriptor());
    }

    @Test
    public void testDescribeContents() throws Exception{
        ParcelFileDescriptor pfd = makeParcelFileDescriptor();
        assertTrue((Parcelable.CONTENTS_FILE_DESCRIPTOR & pfd.describeContents()) != 0);
    }

    private static void assertContains(String expected, String actual) {
        if (actual.contains(expected)) return;
        throw new ComparisonFailure("", expected, actual);
    }

    private static void write(ParcelFileDescriptor pfd, int oneByte)  throws IOException{
        new FileOutputStream(pfd.getFileDescriptor()).write(oneByte);
    }

    // This method is unlikely to be used by clients, as clients use ContentResolver,
    // which builds AutoCloseInputStream under the hood rather than FileInputStream
    // built from a raw FD.
    //
    // Using new FileInputStream(PFD.getFileDescriptor()) is discouraged, as error
    // propagation is lost, and read() will never throw IOException in such case.
    private static int read(ParcelFileDescriptor pfd) throws IOException {
        return new FileInputStream(pfd.getFileDescriptor()).read();
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testPipeNormal() throws Exception {
        final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
        final ParcelFileDescriptor red = pipe[0];
        final ParcelFileDescriptor blue = pipe[1];

        write(blue, 1);
        assertEquals(1, read(red));

        blue.close();
        assertEquals(-1, read(red));
        red.checkError();
    }

    // Reading should be done via AutoCloseInputStream if possible, rather than
    // recreating a FileInputStream from a raw FD, what's done in read(PFD).
    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testPipeError_Discouraged() throws Exception {
        final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
        final ParcelFileDescriptor red = pipe[0];
        final ParcelFileDescriptor blue = pipe[1];

        write(blue, 2);
        blue.closeWithError("OMG MUFFINS");

        // Even though closed we should still drain pipe.
        assertEquals(2, read(red));
        assertEquals(-1, read(red));
        try {
            red.checkError();
            fail("expected throw!");
        } catch (IOException e) {
            assertContains("OMG MUFFINS", e.getMessage());
        }
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testPipeError() throws Exception {
        final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
        final ParcelFileDescriptor red = pipe[0];
        final ParcelFileDescriptor blue = pipe[1];

        write(blue, 2);
        blue.closeWithError("OMG MUFFINS");

        try (AutoCloseInputStream is = new AutoCloseInputStream(red)) {
            is.read();
            is.read();  // Checks errors on EOF.
            fail("expected throw!");
        } catch (IOException e) {
            assertContains("OMG MUFFINS", e.getMessage());
        }
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = MessageQueue.class)
    public void testFileNormal() throws Exception {
        final Handler handler = new Handler(Looper.getMainLooper());
        final FutureCloseListener listener = new FutureCloseListener();
        final ParcelFileDescriptor file = ParcelFileDescriptor.open(
                File.createTempFile("pfd", "bbq"), ParcelFileDescriptor.MODE_READ_WRITE, handler,
                listener);

        write(file, 7);
        file.close();

        // make sure we were notified
        assertEquals(null, listener.get());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = MessageQueue.class)
    public void testFileError() throws Exception {
        final Handler handler = new Handler(Looper.getMainLooper());
        final FutureCloseListener listener = new FutureCloseListener();
        final ParcelFileDescriptor file = ParcelFileDescriptor.open(
                File.createTempFile("pfd", "bbq"), ParcelFileDescriptor.MODE_READ_WRITE, handler,
                listener);

        write(file, 8);
        file.closeWithError("OMG BANANAS");

        // make sure error came through
        assertContains("OMG BANANAS", listener.get().getMessage());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = MessageQueue.class)
    public void testFileDetach() throws Exception {
        final Handler handler = new Handler(Looper.getMainLooper());
        final FutureCloseListener listener = new FutureCloseListener();
        final ParcelFileDescriptor file = ParcelFileDescriptor.open(
                File.createTempFile("pfd", "bbq"), ParcelFileDescriptor.MODE_READ_WRITE, handler,
                listener);

        file.detachFd();

        // make sure detach came through
        assertContains("DETACHED", listener.get().getMessage());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = MessageQueue.class)
    public void testFileWrapped() throws Exception {
        final Handler handler1 = new Handler(Looper.getMainLooper());
        final Handler handler2 = new Handler(Looper.getMainLooper());
        final FutureCloseListener listener1 = new FutureCloseListener();
        final FutureCloseListener listener2 = new FutureCloseListener();
        final ParcelFileDescriptor file1 = ParcelFileDescriptor.open(
                File.createTempFile("pfd", "bbq"), ParcelFileDescriptor.MODE_READ_WRITE, handler1,
                listener1);
        final ParcelFileDescriptor file2 = ParcelFileDescriptor.wrap(file1, handler2, listener2);

        write(file2, 7);
        file2.close();

        // make sure we were notified
        assertEquals(null, listener2.get());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testSocketErrorAfterClose() throws Exception {
        final ParcelFileDescriptor[] pair = ParcelFileDescriptor.createReliableSocketPair();
        final ParcelFileDescriptor red = pair[0];
        final ParcelFileDescriptor blue = pair[1];

        // both sides throw their hands in the air
        blue.closeWithError("BLUE RAWR");
        red.closeWithError("RED RAWR");

        // red noticed the blue error, but after that the comm pipe was dead so
        // blue had no way of seeing the red error.
        try {
            red.checkError();
            fail("expected throw!");
        } catch (IOException e) {
            assertContains("BLUE RAWR", e.getMessage());
        }

        // expected to not throw; no error
        blue.checkError();
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testSocketMultipleCheck() throws Exception {
        final ParcelFileDescriptor[] pair = ParcelFileDescriptor.createReliableSocketPair();
        final ParcelFileDescriptor red = pair[0];
        final ParcelFileDescriptor blue = pair[1];

        // allow checking before closed; they should all pass
        blue.checkError();
        blue.checkError();
        blue.checkError();

        // and verify we actually see it
        red.closeWithError("RAWR RED");
        try {
            blue.checkError();
            fail("expected throw!");
        } catch (IOException e) {
            assertContains("RAWR RED", e.getMessage());
        }
    }

    // http://b/21578056
    @Test
    public void testFileNamesWithNonBmpChars() throws Exception {
        final File file = File.createTempFile("treble_clef_\ud834\udd1e", ".tmp");
        final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY);
        assertNotNull(pfd);
        pfd.close();
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = Os.class)
    public void testCheckFinalizerBehavior() throws Exception {
        final Runtime runtime = Runtime.getRuntime();
        ParcelFileDescriptor pfd = makeParcelFileDescriptor();
        assertTrue(checkIsValid(pfd.getFileDescriptor()));

        ParcelFileDescriptor wrappedPfd = new ParcelFileDescriptor(pfd);
        assertTrue(checkIsValid(wrappedPfd.getFileDescriptor()));

        FileDescriptor fd = pfd.getFileDescriptor();
        int rawFd = pfd.getFd();
        pfd = null;
        assertNull(pfd); // To keep tools happy - yes we are using the write to null
        runtime.gc(); runtime.runFinalization();
        assertTrue("Wrapped PFD failed to hold reference",
                checkIsValid(wrappedPfd.getFileDescriptor()));
        assertTrue("FileDescriptor failed to hold reference", checkIsValid(fd));

        wrappedPfd = null;
        assertNull(wrappedPfd); // To keep tools happy - yes we are using the write to null
        runtime.gc(); runtime.runFinalization();
        // TODO: Enable this once b/65027998 is fixed
        //assertTrue("FileDescriptor failed to hold reference", checkIsValid(fd));

        fd = null;
        assertNull(fd); // To keep tools happy - yes we are using the write to null
        runtime.gc(); runtime.runFinalization();

        try {
            ParcelFileDescriptor.fromFd(rawFd);
            fail("FD leaked");
        } catch (IOException ex) {
            // Success
        }
    }

    boolean checkIsValid(FileDescriptor fd) {
        try {
            Os.fstat(fd);
            return true;
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.EBADF) {
                return false;
            } else {
                fail(e.getMessage());
                // not reached
                return false;
            }
        }
    }

    static ParcelFileDescriptor makeParcelFileDescriptor() throws Exception {
        File file = File.createTempFile("testParcelFileDescriptor", "bin");
        try (FileOutputStream fout = new FileOutputStream(file)) {
            fout.write(new byte[] { 0x0, 0x1, 0x2, 0x3 });
        }

        ParcelFileDescriptor pf = null;
        pf = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
        return pf;
    }

    public static class FutureCloseListener extends AbstractFuture<IOException>
            implements ParcelFileDescriptor.OnCloseListener {
        @Override
        public void onClose(IOException e) {
            if (e instanceof ParcelFileDescriptor.FileDescriptorDetachedException) {
                set(new IOException("DETACHED"));
            } else {
                set(e);
            }
        }

        @Override
        public IOException get() throws InterruptedException, ExecutionException {
            try {
                return get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
