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

package android.hdmicec.cts.error;

/** Enum contains the list of possible causes for an {@link CecClientWrapperException}. */
public enum ErrorCodes {
    CecMessageNotFound("Could not find CEC message "),
    CecMessageFound("Found the CEC message "),
    CecClientStart("Could not start the cec-client process "),
    CecClientStop("Could not stop the cec-client process "),
    CecClientNotRunning("Cec-client not running"),
    CecPortBusy("Cec port busy "),
    DeviceNotAvailable("Device not found "),
    ReadConsole("Exception while reading from the console"),
    WriteConsole("Exception while writing into the console");

    private final String message;

    public String getExceptionMessage() {
        return this.message;
    }

    private ErrorCodes(String message) {
        this.message = message;
    }
}
