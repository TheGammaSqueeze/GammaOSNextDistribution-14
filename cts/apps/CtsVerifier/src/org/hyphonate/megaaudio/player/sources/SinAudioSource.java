/*
 * Copyright 2020 The Android Open Source Project
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

public class SinAudioSource extends WaveTableSource {

    /**
     * The number of SAMPLES in the Sin Wave table.
     * This is plenty of samples for a clear sine wave.
     * the + 1 is to avoid special handling of the interpolation on the last sample.
     */
    static final int WAVETABLE_LENGTH = 2049;

    public SinAudioSource() {
        super();
        float[] waveTbl = new float[WAVETABLE_LENGTH];
        WaveTableSource.genSinWave(waveTbl);

        super.setWaveTable(waveTbl);
    }
}
