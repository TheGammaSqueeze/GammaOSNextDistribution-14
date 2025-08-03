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
package org.hyphonate.megaaudio.player.sources;

import org.hyphonate.megaaudio.player.AudioSource;
import org.hyphonate.megaaudio.player.AudioSourceProvider;

/**
 * An AudioSourceProvider for SparseChannelAudioSources
 */
public class SparseChannelAudioSourceProvider extends AudioSourceProvider {
    public static final int CHANNELMASK_LEFT = 0x01;
    public static final int CHANNELMASK_RIGHT = 0x02;

    private int mChannelsMask;
    public SparseChannelAudioSourceProvider(int mask) {
        super();

        mChannelsMask = mask;
    }

    @Override
    public AudioSource getJavaSource() {
        return mActiveSource = mJavaSource != null
                ? mJavaSource
                : (mJavaSource = new SparseChannelAudioSource(mChannelsMask));
    }

    @Override
    public AudioSource getNativeSource() {
        // NOTE: until a native version of the SparseChannelAudioSource is implemented, returning
        // null from here will default back to the Java SparseChannelAudioSource.
        // return new NativeAudioSource(allocNativeSource());
        return mActiveSource = null;
    }

    // private native long allocNativeSource();
}
