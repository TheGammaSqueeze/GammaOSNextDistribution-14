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

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** Utility Class containing utilities for IP ports. */
public final class PortUtility {
    private static final Logger LOGGER = Logger.getLogger(PortUtility.class.getName());

    /** The lower bound of ports to use, currently the start of the dynamic port range */
    public static final int START_PORT = 49152;

    /** The upper bound of ports to use, currently the end of the dynamic port range */
    public static final int END_PORT = 65535;

    /**
     * Maximum number of attempts that the utility should go through before failing to find an open
     * port.
     */
    public static final int MAX_ATTEMPTS = 1000;

    private PortUtility() {}

    /**
     * Searches for and returns the next open port number on the host system.
     *
     * @throws RuntimeException if unable to find an open port with {@link #MAX_ATTEMPTS} tries.
     */
    public static int nextAvailablePort() {
        int port;
        int count = 0;

        while (count < MAX_ATTEMPTS) {
            count++;
            LOGGER.finest(
                    String.format("Attempting to find open port. Number of attempts: %d", count));
            port = ThreadLocalRandom.current().nextInt(START_PORT, END_PORT);
            if (isPortFree(port)) {
                LOGGER.finest(String.format("Found open port (%d) after %d tries", port, count));
                return port;
            }
        }

        throw new RuntimeException("Unable to find an open port.");
    }

    /**
     * Helper method that identifies if a provided port is free.
     *
     * @param port the number of the port to check.
     * @return True if the port is free, false otherwise.
     */
    private static boolean isPortFree(int port) {

        // If ServerSocket throws IO Exception, port is not open.
        try {
            new ServerSocket(port).close();
            return true;
        } catch (IOException ioe) {
            return false;
        }
    }
}
