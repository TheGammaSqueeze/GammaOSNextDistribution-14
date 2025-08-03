/*
 * Copyright 2021 The Android Open Source Project
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
package org.hyphonate.megaaudio.player;

/**
 * Implements facility for wrapping a JavaAudioSource in a NativeAudioSource.
 */
public class JavaSourceProxy {
    /**
     * Initializes the JNI required to use this class.
     */
    public static native void initN();

    /**
     * Creates and initializes a native audio source (NativeAudioSource) object
     * and associates it with the specified Java AudioSource.
     * @param javaSource the Java AudioSource to associate.
     * @return A pointer to the native audio source object (cast as a long).
     */
    public static native long allocNativeSource(AudioSource javaSource);
}
