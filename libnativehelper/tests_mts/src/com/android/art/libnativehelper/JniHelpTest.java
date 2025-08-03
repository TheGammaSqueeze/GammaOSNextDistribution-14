/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.art.libnativehelper;

import android.test.AndroidTestCase;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;

import android.system.ErrnoException;

import org.junit.Assert;

public class JniHelpTest extends AndroidTestCase {
    private static native void throwException(String className, String message);
    private static native void throwExceptionWithIntFormat(String className,
                                                           String format,
                                                           int value);
    private static native void throwNullPointerException(String message);
    private static native void throwRuntimeException(String message);
    private static native void throwIOException(int cause) throws IOException;
    private static native void throwErrnoException(String fileName, int cause) throws ErrnoException;
    private static native void logException(Throwable throwable);

    private static native FileDescriptor fileDescriptorCreate(int unixFd);
    private static native int fileDescriptorGetFD(FileDescriptor jiofd);
    private static native void fileDescriptorSetFD(FileDescriptor jiofd, int unixFd);

    private static native String createString(String input);

    public void testThrowException() {
        final String message = "Because test";
        try {
            throwException("java/lang/RuntimeException", message);
            fail("Unreachable");
        } catch (RuntimeException e) {
            assertEquals(message, e.getMessage());
        }
    }

    public void testThrowExceptionWithIntFormat() {
        final String format = "Because test %d";
        try {
            throwExceptionWithIntFormat("java/lang/RuntimeException", format, 101);
            fail("Unreachable");
        } catch (RuntimeException e) {
            assertEquals("Because test 101", e.getMessage());
        }
    }

    public void testThrowNullPointerException() {
        final String message = "Because another test";
        try {
            throwNullPointerException(message);
            fail("Unreachable");
        } catch (NullPointerException e) {
            assertEquals(message, e.getMessage());
        }
    }

    public void testThrowRuntimeException() {
        final String message = "Because test again";
        try {
            throwRuntimeException(message);
            fail("Unreachable");
        } catch (RuntimeException e) {
            assertEquals(message, e.getMessage());
        }
    }

    public void testIOException() {
        String s1 = null;
        try {
            throwIOException(70);
            fail("Unreachable");
        } catch (IOException e) {
            s1 = e.getMessage();
        }
        assertNotNull(s1);

        String s2 = null;
        try {
            throwIOException(71);
            fail("Unreachable");
        } catch (IOException e) {
            s2 = e.getMessage();
        }
        assertNotNull(s2);

        assertFalse(s1.equals(s2));
    }

    public void testErrnoException() {
        final String functionName = "execve";
        final int err = 42;
        try {
            throwErrnoException(functionName, err);
            fail("Unreachable");
        } catch (ErrnoException e) {
            // The message contains the function name as well as the string for the errno, just only
            // check the first part of the message
            assertTrue("Function name", e.getMessage().startsWith(functionName));
            assertEquals(err, e.errno);
        }
    }

    public void testLogException() {
        try {
            throw new RuntimeException("Exception for logging test");
        } catch (RuntimeException e) {
            // TODO: Mock/redirect logcat to test output is logged appropriately.
            // Or add extend JNIHelp API to write to a buffer or file.
            logException(e);
        }
    }

    public void testFileDescriptorCreate() {
        int [] unix_fds = { -999, -1, 0, 1, 1000 };
        for (int unix_fd : unix_fds) {
            FileDescriptor f0 = fileDescriptorCreate(unix_fd);
            assertNotNull(f0);
            assertSame(f0.getClass(), FileDescriptor.class);
        }
    }

    public void testFileDescriptorGetNull() {
        assertEquals(-1, fileDescriptorGetFD(null));
    }

    public void testFileDescriptorGetNonNull() {
        final int UNIX_FD = 77;
        FileDescriptor jiofd = fileDescriptorCreate(UNIX_FD);
        assertEquals(UNIX_FD, fileDescriptorGetFD(jiofd));
    }

    public void testFileDescriptorSetNull() {
        try {
            fileDescriptorSetFD(null, 1);
            fail("Expected NullPointerException to be thrown.");
        } catch (NullPointerException e) {}
    }

    public void testFileDescriptorSetNonNull() {
        final int UNIX_FD = 127;
        FileDescriptor jiofd = fileDescriptorCreate(0);
        fileDescriptorSetFD(jiofd, UNIX_FD);
        assertEquals(UNIX_FD, fileDescriptorGetFD(jiofd));
    }

    public void testCreateString() {
        String input = "The treacherous mountain path lay ahead.";
        String output = createString(input);
        assertEquals(input, output);
        assertNotSame(input, output);
    }
}
