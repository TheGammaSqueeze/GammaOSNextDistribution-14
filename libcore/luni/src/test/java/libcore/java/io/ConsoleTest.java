/*
 * Copyright (C) 2008 The Android Open Source Project
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

package libcore.java.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.IOError;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ConsoleTest {

    private Console createConsole(InputStream inStream, OutputStream outStream) throws Exception {
        Constructor<Console> constructor =
            Console.class.getDeclaredConstructor(InputStream.class, OutputStream.class);
        constructor.setAccessible(true);
        return constructor.newInstance(inStream, outStream);
    }

    private boolean isTty() {
        // It is important for some tests to know if it is running in an environment where tty is
        // available or not. This is because some API calls end up calling the native echo()
        // function which throws an exception if tty is not available.
        // The Console.isTty() method is private, however an option to check, without reflection, if
        // tty is available is to see if console() return null or not. The function will always
        // return an instance of Console if isTty() is true, or null otherwise.
        return (Console.console() != null);
    }

    @Test
    public void testReadPassword() throws Exception {
        final byte[] bytes = "secret password\n".getBytes();
        ByteArrayInputStream inStream = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Console console = createConsole(inStream, outStream);
        try {
            String password = String.valueOf(console.readPassword());
            // Due to readPassword depending on echo, which depends on having stdin as a tty, it is
            // expected that it will throw an IOError if tty is not available
            assertTrue("readPassword succeeded unexpectedly", isTty());
            assertEquals("secret password", password);
        } catch(IOError e) {
            assertFalse("readPassword threw unexpected IOError", isTty());
            assertTrue("readPassword exception not as expected",
                    e.getMessage().contains("Inappropriate ioctl for device"));
        }
    }

    @Test
    public void testReadPasswordWithPrompt() throws Exception {
        final byte[] bytes = "secret password\n".getBytes();
        ByteArrayInputStream inStream = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Console console = createConsole(inStream, outStream);
        String username = "Alice";
        try {
            String password = String.valueOf(
                    console.readPassword("%s, please enter your password:", username));
            // Due to readPassword depending on echo, which depends on having stdin as a tty, it is
            // expected that it will throw an IOError if tty is not available
            assertTrue("readPassword succeeded unexpectedly", isTty());
            assertEquals("secret password", password);
            String prompt = new String(((ByteArrayOutputStream) outStream).toByteArray());
            assertEquals("Alice, please enter your password:", prompt);
        } catch(IOError e) {
            assertFalse("readPassword threw unexpected IOError", isTty());
            assertTrue("readPassword exception not as expected",
                    e.getMessage().contains("Inappropriate ioctl for device"));
        }
    }

    @Test
    public void testCharset() throws Exception {
        final byte[] bytes = "secret password\n".getBytes();
        ByteArrayInputStream inStream = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Console console = createConsole(inStream, outStream);
        // Android uses UTF-8 as the default charset.
        assertEquals(StandardCharsets.UTF_8, console.charset());
    }

}
