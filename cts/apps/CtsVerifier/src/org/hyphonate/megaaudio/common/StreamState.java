/*
 * Copyright 2022 The Android Open Source Project
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
package org.hyphonate.megaaudio.common;

/**
 * Constants for Stream State
 */
public class StreamState {
    public static final int UNINITIALIZED = 0;  // AAUDIO_STREAM_STATE_UNINITIALIZED,
    public static final int UNKNOWN = 1;        // AAUDIO_STREAM_STATE_UNKNOWN,
    public static final int OPEN = 2;           // AAUDIO_STREAM_STATE_OPEN,
    public static final int STARTING = 3;       // AAUDIO_STREAM_STATE_STARTING,
    public static final int STARTED = 4;        // AAUDIO_STREAM_STATE_STARTED,
    public static final int PAUSING = 5;        // AAUDIO_STREAM_STATE_PAUSING,
    public static final int PAUSED = 6;         // AAUDIO_STREAM_STATE_PAUSED,
    public static final int FLUSHING = 7;       // AAUDIO_STREAM_STATE_FLUSHING,
    public static final int FLUSHED = 8;        // AAUDIO_STREAM_STATE_FLUSHED,
    public static final int STOPPING = 9;       // AAUDIO_STREAM_STATE_STOPPING,
    public static final int STOPPED = 10;       // AAUDIO_STREAM_STATE_STOPPED,
    public static final int CLOSING = 11;       // AAUDIO_STREAM_STATE_CLOSING,
    public static final int CLOSED = 12;        // AAUDIO_STREAM_STATE_CLOSED,
    public static final int DISCONNECTED = 13;  // AAUDIO_STREAM_STATE_DISCONNECTED,
}
