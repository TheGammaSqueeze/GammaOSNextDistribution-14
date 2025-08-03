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

package android.content.res.cts;

import static android.system.OsConstants.S_ISFIFO;

import android.content.res.AssetFileDescriptor;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.system.Os;
import android.system.StructStat;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class AssetFileDescriptor_AutoCloseInputStreamTest extends AndroidTestCase {
    private static final int FILE_END = -1;
    private static final String FILE_NAME = "testAssertFileDescriptorAutoCloseInputStream";
    private static final byte[] FILE_DATA = new byte[]{
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08
    };
    private static final int FILE_LENGTH = FILE_DATA.length;
    private static final String METHOD_NOT_SUPPORTED_MESSAGE =
            "This Method is not supported in AutoCloseInputStream FileChannel.";
    private File mFile;
    private AssetFileDescriptor mFd;
    private AssetFileDescriptor.AutoCloseInputStream mInput;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mFile = new File(getContext().getFilesDir(), FILE_NAME);
        FileOutputStream outputStream = new FileOutputStream(mFile);
        outputStream.write(FILE_DATA);
        outputStream.close();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mFd != null) {
            mFd.close();
            mFd = null;
        }
        mFile.delete();
    }

    public void testSkip() throws IOException {
        openInput(0, FILE_LENGTH);
        assertEquals(FILE_DATA[0], mInput.read());
        assertEquals(0, mInput.skip(0));
        assertEquals(FILE_DATA[1], mInput.read());
        assertEquals(3, mInput.skip(3));
        assertEquals(FILE_DATA[5], mInput.read());
        assertEquals(3, mInput.skip(10));
        assertEquals(FILE_END, mInput.read());
    }

    public void testRead() throws IOException {
        openInput(0, FILE_LENGTH);
        for (int i = 0; i < FILE_LENGTH; i++) {
            assertEquals(FILE_DATA[i], mInput.read());
        }
        assertEquals(FILE_END, mInput.read());
    }

    public void testReadPartial() throws IOException {
        long len = 6;
        openInput(0, len);
        for (int i = 0; i < len; i++) {
            assertEquals(FILE_DATA[i], mInput.read());
        }
        assertEquals(FILE_END, mInput.read());
    }

    public void testReadBufferLen() throws IOException {
        openInput(0, FILE_LENGTH);
        byte[] buf = new byte[FILE_LENGTH];
        assertEquals(3, mInput.read(buf, 0, 3));
        assertEquals(3, mInput.read(buf, 3, 3));
        assertEquals(3, mInput.read(buf, 6, 4));
        MoreAsserts.assertEquals(FILE_DATA, buf);
        assertEquals(FILE_END, mInput.read(buf, 0, 4));
    }

    public void testReadBuffer() throws IOException {
        openInput(0, FILE_LENGTH);
        byte[] buf = new byte[6];
        assertEquals(6, mInput.read(buf));
        assertEquals(FILE_DATA[0], buf[0]);
        assertEquals(3, mInput.read(buf));
        assertEquals(FILE_DATA[6], buf[0]);
        assertEquals(FILE_END, mInput.read(buf));
    }

    public void testReadBufferPartial() throws IOException {
        long len = 8;
        openInput(0, len);
        byte[] buf = new byte[6];
        assertEquals(6, mInput.read(buf));
        assertEquals(FILE_DATA[0], buf[0]);
        assertEquals(2, mInput.read(buf));
        assertEquals(FILE_DATA[6], buf[0]);
        assertEquals(FILE_END, mInput.read(buf));
    }

    public void testAvailableRead() throws IOException {
        openInput(0, FILE_LENGTH);
        assertEquals(FILE_LENGTH, mInput.available());
        assertEquals(FILE_DATA[0], mInput.read());
        assertEquals(FILE_LENGTH - 1, mInput.available());
    }

    public void testAvailableReadBuffer() throws IOException {
        openInput(0, FILE_LENGTH);
        byte[] buf = new byte[3];
        assertEquals(FILE_LENGTH, mInput.available());
        assertEquals(buf.length, mInput.read(buf));
        assertEquals(FILE_LENGTH - buf.length, mInput.available());
    }

    public void testAvailableReadBufferLen() throws IOException {
        openInput(0, FILE_LENGTH);
        byte[] buf = new byte[3];
        assertEquals(FILE_LENGTH, mInput.available());
        assertEquals(2, mInput.read(buf, 0, 2));
        assertEquals(FILE_LENGTH - 2, mInput.available());
    }

    /*
     * Tests that AutoInputStream doesn't support mark().
     */
    public void testMark() throws IOException {
        openInput(0, FILE_LENGTH);
        assertFalse(mInput.markSupported());
        assertEquals(FILE_DATA[0], mInput.read());
        mInput.mark(FILE_LENGTH);  // should do nothing
        assertEquals(FILE_DATA[1], mInput.read());
        mInput.reset();  // should do nothing
        assertEquals(FILE_DATA[2], mInput.read());
    }

    public void testTwoFileDescriptorsWorkIndependently() throws IOException {
        openInput(0, FILE_LENGTH);

        AssetFileDescriptor fd2 = new AssetFileDescriptor(mFd.getParcelFileDescriptor(),
                0,
                FILE_LENGTH);
        AssetFileDescriptor.AutoCloseInputStream input2 =
                new AssetFileDescriptor.AutoCloseInputStream(fd2);

        input2.skip(2);
        input2.read();

        for (int i = 0; i < FILE_LENGTH; i++) {
            assertEquals(FILE_DATA[i], mInput.read());
        }
        assertEquals(FILE_END, mInput.read());
    }

    private void openInput(long startOffset, long length)
            throws IOException {
        if (mFd != null) {
            mFd.close();
            mFd = null;
        }
        ParcelFileDescriptor fd =
                ParcelFileDescriptor.open(mFile, ParcelFileDescriptor.MODE_READ_WRITE);
        mFd = new AssetFileDescriptor(fd, startOffset, length);
        mInput = new AssetFileDescriptor.AutoCloseInputStream(mFd);
    }

    /*
     * Tests that AutoInputStream is returning customized File Channel of getChannel(),
     * which could help update the channel position.
     */
    public void testGetChannel() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        input.skip(2);
        FileChannel fc = input.getChannel();
        input.read();
        assertEquals(3, fc.position());
    }

    public void testOffsetCorrectFileChannelSize() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();
        assertEquals(fc.size(), FILE_LENGTH);
    }

    public void testOffsetCorrectFileChannelReadBuffer() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        int startPosition = 0;
        int bufferSize = 4;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        int bytesRead = fc.read(buffer);
        assertEquals(bufferSize, bytesRead);

        buffer.flip();
        for (int i = startPosition; i < startPosition + bufferSize; i++) {
            assertEquals(FILE_DATA[i], buffer.get());
        }
        assertFalse(buffer.hasRemaining());
        assertEquals(startPosition + bufferSize, fc.position());
    }

    public void testOffsetCorrectFileChannelReadBuffers() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        int startPosition = 0;
        int bufferSize = 4;
        ByteBuffer[] buffers = new ByteBuffer[1];
        buffers[0] = ByteBuffer.allocate(bufferSize);
        fc.read(buffers, 0, buffers.length);
        buffers[0].flip();
        for (int i = startPosition; i < startPosition + bufferSize; i++) {
            assertEquals(FILE_DATA[i], buffers[0].get());
        }
        assertFalse(buffers[0].hasRemaining());
        assertEquals(startPosition + bufferSize, fc.position());
    }

    public void testOffsetCorrectFileChannelReadBufferFromPosition() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        int startPosition = 0;
        int bufferSize = 4;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        int readPosition = 1;
        fc.read(buffer, readPosition);
        buffer.flip();
        for (int i = readPosition; i < readPosition + bufferSize; i++) {
            assertEquals(FILE_DATA[i], buffer.get());
        }
        assertEquals(startPosition, fc.position());
    }

    public void testOffsetCorrectFileChannelTransferTo() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        String outputFileName = "outputFile.txt";
        File outputFile = new File(getContext().getFilesDir(), outputFileName);
        FileOutputStream output = new FileOutputStream(outputFile);
        WritableByteChannel targetChannel = output.getChannel();
        int startPosition = 1;
        int transferSize = 3;
        long bytesTransferred = fc.transferTo(startPosition, transferSize, targetChannel);
        assertEquals(transferSize, bytesTransferred);
        assertEquals(0, fc.position());

        ParcelFileDescriptor fd =
                ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_WRITE);
        AssetFileDescriptor afd = new AssetFileDescriptor(fd, 0, transferSize);
        AssetFileDescriptor.AutoCloseInputStream input2 =
                new AssetFileDescriptor.AutoCloseInputStream(afd);

        for (int i = startPosition; i < startPosition + transferSize; i++) {
            assertEquals(FILE_DATA[i], input2.read());
        }
        assertEquals(-1, input2.read());

        targetChannel.close();
        output.close();
    }

    public void testOffsetCorrectFileChannelMap() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        int startPosition = 0;
        int mapPosition = 1;
        int mapSize = 4;
        MappedByteBuffer mbb = fc.map(FileChannel.MapMode.READ_ONLY, mapPosition, mapSize);
        for (int i = mapPosition; i < mapPosition + mapSize; i++) {
            assertEquals(FILE_DATA[i], mbb.get());
        }
        assertFalse(mbb.hasRemaining());
        assertEquals(startPosition, fc.position());
    }

    public void testOffsetCorrectFileChannelWriteBuffer() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        ByteBuffer buffer = ByteBuffer.allocate(1);
        try {
            fc.write(buffer);
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals(METHOD_NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
    }

    public void testOffsetCorrectFileChannelWriteBuffers() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        int bufferSize = 4;
        ByteBuffer[] buffers = new ByteBuffer[1];
        buffers[0] = ByteBuffer.allocate(bufferSize);
        try {
            fc.write(buffers, 0, 1);
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals(METHOD_NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
    }

    public void testOffsetCorrectFileChannelWriteBufferFromPosition() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        int bufferSize = 4;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        try {
            fc.write(buffer, 0);
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals(METHOD_NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
    }

    public void testOffsetCorrectFileChannelTransferFrom() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        FileInputStream input2 = new FileInputStream(mFile);
        FileChannel fc2 = input2.getChannel();
        try {
            fc.transferFrom(fc2, 0, fc2.size());
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals(METHOD_NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
        fc2.close();
        input2.close();
    }

    public void testOffsetCorrectFileChannelTruncate() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        try {
            fc.truncate(FILE_LENGTH + 1);
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals(METHOD_NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
    }

    public void testOffsetCorrectFileChannelForce() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        try {
            fc.force(true);
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals(METHOD_NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
    }

    public void testOffsetCorrectFileChannelLock() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        try {
            fc.lock(0, 4, true);
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals(METHOD_NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
    }

    public void testOffsetCorrectFileChannelTryLock() throws IOException {
        AssetFileDescriptor.AutoCloseInputStream input = getInputStream();
        FileChannel fc = input.getChannel();

        try {
            fc.tryLock(0, 4, true);
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals(METHOD_NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
    }

    private AssetFileDescriptor.AutoCloseInputStream getInputStream() throws IOException {
        openInput(0, FILE_LENGTH);
        AssetFileDescriptor fd = new AssetFileDescriptor(mFd.getParcelFileDescriptor(),
                0,
                FILE_LENGTH);
        AssetFileDescriptor.AutoCloseInputStream input =
                new AssetFileDescriptor.AutoCloseInputStream(fd);
        return input;
    }

    public void testNonSeekableInputStream() throws Exception {
        final FileDescriptor[] fds = Os.pipe();
        AssetFileDescriptor readAFd = new AssetFileDescriptor(
                new ParcelFileDescriptor(fds[0]), 0, FILE_LENGTH);
        FileDescriptor writeFd = fds[1];
        FileOutputStream out = new FileOutputStream(writeFd);
        out.write(FILE_DATA);
        out.close();

        StructStat ss = Os.fstat(readAFd.getParcelFileDescriptor().getFileDescriptor());
        assertTrue(S_ISFIFO(ss.st_mode));

        AssetFileDescriptor.AutoCloseInputStream in =
                new AssetFileDescriptor.AutoCloseInputStream(readAFd);
        assertEquals(FILE_LENGTH, in.available());
        assertEquals(FILE_DATA[0], in.read());
        assertEquals(FILE_LENGTH - 1, in.available());

        byte[]buffer = new byte[2];
        assertEquals(buffer.length, in.read(buffer));
        assertEquals(FILE_DATA[3], in.read());
        assertEquals(FILE_LENGTH - 4, in.available());

        assertEquals(1, in.skip(1));
        assertEquals(FILE_DATA[5], in.read());
        in.close();
    }
}
