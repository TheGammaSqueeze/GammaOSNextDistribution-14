/*
 * Copyright 2015 The Android Open Source Project
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

package android.keystore.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.security.keystore.BackendBusyException;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests basic functionality of the BackendBusyException.
 */
@RunWith(AndroidJUnit4.class)
public class BackendBusyExceptionTest {
    /**
     * Tests if a BackendBusyException constructed with a given backoff hint returns
     * that value through getBackOffHintMillis().
     * Also the constructor must throw IllegalArgumentException if the backoff hint is negative.
     */
    @Test
    public void testBackOffHint () {
        BackendBusyException backendBusyException = new BackendBusyException(1);
        assertEquals(backendBusyException.getBackOffHintMillis(), 1);
        backendBusyException = new BackendBusyException(10);
        assertEquals(backendBusyException.getBackOffHintMillis(), 10);
        backendBusyException = new BackendBusyException(1, "Message");
        assertEquals(backendBusyException.getBackOffHintMillis(), 1);
        backendBusyException = new BackendBusyException(10, "Message");
        assertEquals(backendBusyException.getBackOffHintMillis(), 10);
        backendBusyException = new BackendBusyException(1, "Message", new Exception());
        assertEquals(backendBusyException.getBackOffHintMillis(), 1);
        backendBusyException = new BackendBusyException(10, "Message", new Exception());
        assertEquals(backendBusyException.getBackOffHintMillis(), 10);

        try {
            new BackendBusyException(-1);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}

        try {
            new BackendBusyException(-1, "Message");
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}

        try {
            new BackendBusyException(-1, "Message", new Exception());
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}
    }

    /**
     * Test that getMessage returns the message passed to the constructor.
     */
    @Test
    public void testMessage() {
        BackendBusyException backendBusyException = new BackendBusyException(1, "My test Message.");
        assertTrue(backendBusyException.getMessage().equals("My test Message."));
        backendBusyException = new BackendBusyException(1, "My test Message 2.", new Exception());
        assertTrue(backendBusyException.getMessage().equals("My test Message 2."));
    }

    /**
     * Test that getCause returns the cause passed to the constructor.
     */
    @Test
    public void testCause() {
        Exception cause = new Exception("My Cause");

        BackendBusyException backendBusyException =
                new BackendBusyException(1, "My test Message.", cause);
        assertTrue(backendBusyException.getCause() == cause);
    }

}
