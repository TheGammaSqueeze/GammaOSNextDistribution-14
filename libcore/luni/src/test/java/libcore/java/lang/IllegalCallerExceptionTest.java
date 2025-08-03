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

package libcore.java.lang;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(JUnit4.class)
public class IllegalCallerExceptionTest {

    @Test
    public void constructor_noArg() {
        IllegalCallerException exception = new IllegalCallerException();

        assertNull(exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    public void constructor_String() {
        String message = "message";

        IllegalCallerException exception = new IllegalCallerException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    public void constructor_String_Throwable() {
        String message = "message";
        Exception cause = new Exception();

        IllegalCallerException exception = new IllegalCallerException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void constructor_Throwable() {
        Exception cause = new Exception();

        IllegalCallerException exception = new IllegalCallerException(cause);

        assertEquals(cause, exception.getCause());
    }

}
