/*
 * Copyright (C) 2021 The Android Open Source Project
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

package libcore.javax.xml.xpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;

import javax.xml.xpath.XPathException;

@RunWith(JUnit4.class)
public class XPathExceptionTest {
    @Test
    public void constructorWithString() {
        XPathException e = new XPathException("message");
        assertEquals("message", e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    public void constructorWithStringNull() {
        try {
            XPathException unused = new XPathException((String) null);
            fail("Expected NullPointerException with null String");
        } catch (NullPointerException ex) {
            // Expected
        }
    }

    @Test
    public void constructorWithThrowable() {
        Throwable t = new Throwable();
        XPathException e = new XPathException(t);
        assertEquals("java.lang.Throwable", e.getMessage());
        assertEquals(t, e.getCause());
    }

    @Test
    public void constructorWithThrowableNull() {
        try {
            XPathException unused = new XPathException((Throwable) null);
            fail("Expected NullPointerException with null Throwable");
        } catch (NullPointerException ex) {
            // Expected
        }
    }

    @Test
    public void printStackTrace_noArgs() {
        XPathException e = new XPathException("message");
        e.printStackTrace();
    }

    @Test
    public void printStackTraceWithPrintStream_nullCause() {
        XPathException e = new XPathException("message");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true);
        e.printStackTrace(ps);

        assertNotEquals(-1, baos.toString().indexOf("javax.xml.xpath.XPathException"));
    }

    @Test
    public void printStackTraceWithPrintStream_nonNullCause() {
        XPathException e = new XPathException(new TestCauseException());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true);
        e.printStackTrace(ps);

        assertNotEquals(-1, baos.toString().indexOf("TestCauseException"));
        assertNotEquals(-1, baos.toString().indexOf("javax.xml.xpath.XPathException"));
    }

    @Test
    public void printStackTraceWithPrintWriter_nullCause() {
        XPathException e = new XPathException("message");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos, true);
        e.printStackTrace(pw);

        assertNotEquals(-1, baos.toString().indexOf("javax.xml.xpath.XPathException"));
    }

    @Test
    public void printStackTraceWithPrintWriter_nonNullCause() {
        XPathException e = new XPathException(new TestCauseException());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos, true);
        e.printStackTrace(pw);

        assertNotEquals(-1, baos.toString().indexOf("TestCauseException"));
        assertNotEquals(-1, baos.toString().indexOf("javax.xml.xpath.XPathException"));
    }

    // Defining a new exception to be used as cause in printStackTrace tests
    private static class TestCauseException extends Throwable {}
}
