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
 * Control of Global attributes
 */
public class Globals {
    /**
     * Enables/Disables Oboe Workarounds.
     * @param enabled if true, turns on Oboe "Workarounds"
     */
    public static native void setOboeWorkaroundsEnabled(boolean enabled);

    /**
     * @return true if MMAP mode is supported.
     */
    public static native boolean isMMapSupported();

    /**
     * @return true if MMAP Exclusive mode is supported.
     */
    public static native boolean isMMapExclusiveSupported();

    /**
     * Enables/Disables MMAP on any stream
     * @param enabled Specifies the enable/disable state
     */
    public static native void setMMapEnabled(boolean enabled);

    /**
     * Gets the MMAP enable state
     * @return true if MMAP is enabled
     */
    public static native boolean isMMapEnabled();
}
