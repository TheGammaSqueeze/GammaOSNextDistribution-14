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

package com.android.media.audiotestharness.server.utility;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.ServerSocket;

@RunWith(JUnit4.class)
public class PortUtilityTests {

    @Test
    public void nextAvailablePort_returnsAvailablePortWithinRange_fuzz() throws Exception {
        // Fuzz test the utility ten times.
        for (int i = 0; i < 10; i++) {
            int port = PortUtility.nextAvailablePort();

            assertTrue(port >= PortUtility.START_PORT);
            assertTrue(port < PortUtility.END_PORT);

            // If no exception is thrown, the port was indeed open and can be used.
            new ServerSocket(port).close();
        }
    }
}
