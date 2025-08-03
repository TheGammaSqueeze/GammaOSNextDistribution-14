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
package com.android.server.uwb.secure.omapi;

import androidx.annotation.WorkerThread;

import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.ResponseApdu;

import java.io.IOException;

/** Interface for using OMAPI to communicate with a secure element applet with APDUs */
@WorkerThread
public interface OmapiConnection {
    /** Callback for listening to initialization completion event. */
    @WorkerThread
    public interface InitCompletionCallback {
        /** Called when initializtion is completed. */
        void onInitCompletion();
    }

    /** Initialize the connection. */
    void init(InitCompletionCallback callback);

    /** Transmits the given CommandApdu to the secure element */
    ResponseApdu transmit(CommandApdu command) throws IOException;

    /** Opens a logical channel to the FiRa applet. */
    ResponseApdu openChannel() throws IOException;

    /** Closes all channels to the SE. */
    void closeChannel() throws IOException;
}
