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

package libcore.java.lang.invoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.invoke.LambdaConversionException;

@RunWith(JUnit4.class)
public class LambdaConversionExceptionTest {
    private static final String MESSAGE = "message";

    @Test
    public void constructor() {
        try {
            throw new LambdaConversionException();
        } catch (LambdaConversionException e) {
            assertNull(e.getMessage());
            assertNull(e.getCause());
        }
    }

    @Test
    public void constructorLString() {
        try {
            throw new LambdaConversionException(MESSAGE);
        } catch (LambdaConversionException e) {
            assertEquals(MESSAGE, e.getMessage());
            assertNull(e.getCause());
        }
    }

    @Test
    public void constructorLStringLThrowable() {
        try {
            try {
                throw new IllegalArgumentException();
            } catch (Throwable t) {
                throw new LambdaConversionException(MESSAGE, t);
            }
        } catch (LambdaConversionException e) {
            assertEquals(MESSAGE, e.getMessage());
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void constructorLThrowable() {
        try {
            try {
                throw new IllegalArgumentException();
            } catch (Throwable t) {
                throw new LambdaConversionException(t);
            }
        } catch (LambdaConversionException e) {
            assertEquals("java.lang.IllegalArgumentException", e.getMessage());
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }

        try {
            throw new LambdaConversionException((Throwable) null);
        } catch (LambdaConversionException e) {
            assertNull(e.getMessage());
            assertNull(e.getCause());
        }
    }

    @Test
    public void constructorWithSuppressionsAndWritableStackTraces() {
        boolean trueAndFalse[] = { true, false };

        for (boolean enableSuppression : trueAndFalse) {
            for (boolean writableStackTrace : trueAndFalse) {
                try {
                    throw new LambdaConversionException(MESSAGE, null, enableSuppression,
                                                        writableStackTrace);
                } catch (LambdaConversionException e) {
                    assertEquals(MESSAGE, e.getMessage());

                    // Check if exceptions can be suppressed.
                    e.addSuppressed(new LambdaConversionException());
                    boolean haveSuppressed = (e.getSuppressed().length == 1);
                    assertEquals(enableSuppression, haveSuppressed);

                    // Check if stack trace is writable.
                    boolean stackTraceHasElements = (0 != e.getStackTrace().length);
                    assertEquals(writableStackTrace, stackTraceHasElements);
                    if (stackTraceHasElements) {
                        // Only a writable stack trace has elements present. Now re-write it.
                        e.setStackTrace(new StackTraceElement[0]);
                        boolean wroteStackTrace = (e.getStackTrace().length == 0);
                        assertEquals(writableStackTrace, wroteStackTrace);
                    }
                }
            }
        }
    }
}
