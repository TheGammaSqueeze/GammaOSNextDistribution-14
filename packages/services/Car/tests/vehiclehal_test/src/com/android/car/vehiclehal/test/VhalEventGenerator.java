/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.vehiclehal.test;

interface VhalEventGenerator {

    // Command bits sent via GENERATE_FAKE_DATA_CONTROLLING_PROPERTY to control fake data generation
    int CMD_START_LINEAR = 0; // Start linear fake data generation
    int CMD_STOP_LINEAR = 1; // Stop linear fake data generation
    int CMD_START_JSON = 2; // Start JSON-based fake data generation
    int CMD_STOP_JSON = 3; // Stop JSON-based fake data generation

    /**
     * Asynchronous call to tell VHAL to start fake event generation. VHAL will start generating
     * data after this call
     *
     * @throws Exception
     */
    void start() throws Exception;

    /**
     * Synchronous call to tell VHAL to stop fake event generation. VHAL should always stopped
     * generating data after this call.
     *
     * @throws Exception
     */
    void stop() throws Exception;
}
