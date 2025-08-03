/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.thread;

import android.os.ParcelFileDescriptor;

import java.io.IOException;

/** Controller for the infrastructure network interface. */
public class InfraInterfaceController {
    private static final String TAG = "InfraIfController";

    static {
        System.loadLibrary("service-thread-jni");
    }

    /**
     * Creates a socket on the infrastructure network interface for sending/receiving ICMPv6
     * Neighbor Discovery messages.
     *
     * @param infraInterfaceName the infrastructure network interface name.
     * @return an ICMPv6 socket file descriptor on the Infrastructure network interface.
     * @throws IOException when fails to create the socket.
     */
    public ParcelFileDescriptor createIcmp6Socket(String infraInterfaceName) throws IOException {
        return ParcelFileDescriptor.adoptFd(nativeCreateIcmp6Socket(infraInterfaceName));
    }

    private static native int nativeCreateIcmp6Socket(String interfaceName) throws IOException;
}
