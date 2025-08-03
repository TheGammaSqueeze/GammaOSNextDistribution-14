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

import android.hardware.soundtrigger.SoundTrigger.ModuleProperties;
import android.os.PersistableBundle;
import android.os.SharedMemory;
import com.android.cts.voiceinteraction.ITestVoiceInteractionServiceListener;
import com.android.cts.voiceinteraction.IProxyAlwaysOnHotwordDetector;
import com.android.cts.voiceinteraction.IProxyDetectorCallback;
import com.android.cts.voiceinteraction.IProxyKeyphraseModelManager;

/**
 * Interface for testing a voiceinteraction service implementation. Since it is not in the same
 * process, it can not be passed locally.
 */
interface ITestVoiceInteractionService {
    /**
     * Registers a listener with the test service which will receive asynchronous callbacks.
     * The service only supports one listener at a time. If this API is called when a listener is
     * already registered, the listener will be overwritten.
     */
    void registerListener(in ITestVoiceInteractionServiceListener listener);

    IProxyAlwaysOnHotwordDetector createAlwaysOnHotwordDetector(
            in String keyphrase, in String locale, IProxyDetectorCallback callback);
    IProxyAlwaysOnHotwordDetector createAlwaysOnHotwordDetectorWithTrustedService(
            in String keyphrase, in String locale, in PersistableBundle options,
            in SharedMemory sharedMemory, IProxyDetectorCallback callback);
    IProxyKeyphraseModelManager createKeyphraseModelManager();
    @nullable
    ModuleProperties getDspModuleProperties();
}