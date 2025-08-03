/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.voiceinteraction;

/**
 * Interface for testing a voiceinteraction service implementation. Used along with
 * {@link ITestVoiceInteractionService#registerListener} to receive asyncrounous callbacks.
 */
interface ITestVoiceInteractionServiceListener {
    /**
     * Callback to notify the listener is registered with the server.
     * This will be the first callback called on this interface, and no further callbacks will be
     * made until onReady is called.
     */
    void onReady();
    /**
     * Callback to notify listener that a shutdown has occurred within the system service.
     * The listener will from then on be invalid, and the client must re-register a new listener.
     */
    void onShutdown();
}