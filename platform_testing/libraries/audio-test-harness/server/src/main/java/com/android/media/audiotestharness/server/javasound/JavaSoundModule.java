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

package com.android.media.audiotestharness.server.javasound;

import com.android.media.audiotestharness.server.core.AudioSystemService;

import com.google.inject.AbstractModule;

/** {@link AbstractModule} for the Java Sound implementation of the Audio Test Harness. */
public final class JavaSoundModule extends AbstractModule {

    private JavaSoundModule() {}

    public static JavaSoundModule create() {
        return new JavaSoundModule();
    }

    @Override
    protected void configure() {
        bind(AudioSystemService.class).to(JavaAudioSystemService.class);
        bind(JavaAudioSystem.class).toInstance(JavaAudioSystem.getInstance());
        bind(JavaAudioCapturerFactory.class);
    }
}
