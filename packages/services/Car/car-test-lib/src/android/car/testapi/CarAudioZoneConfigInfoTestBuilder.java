/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.testapi;

import android.car.media.CarAudioZoneConfigInfo;

/**
 * Factory class used to create an instance of {@link android.car.media.CarAudioZoneConfigInfo}
 * for testing purposes since the constructor is hidden.
 */
public final class CarAudioZoneConfigInfoTestBuilder {

    private String mName = "name";
    private int mZoneId;
    private int mConfigId;

    /**
     * Builder used to creates an {@link android.car.media.CarAudioZoneConfigInfo}
     * object of the desired state, setting name, {@code zoneId}, and {@code configId}.
     */
    public CarAudioZoneConfigInfoTestBuilder() {
    }

    /** Sets the name of the {@link android.car.media.CarAudioZoneConfigInfo} object */
    public CarAudioZoneConfigInfoTestBuilder setName(String name) {
        mName = name;
        return this;
    }
    /**
     * Sets the audio {@code zoneId} of the
     * {@link android.car.media.CarAudioZoneConfigInfo} object
     */
    public CarAudioZoneConfigInfoTestBuilder setZoneId(int zoneId) {
        mZoneId = zoneId;
        return this;
    }
    /** Sets the {@code configId} of the {@link android.car.media.CarAudioZoneConfigInfo} object */
    public CarAudioZoneConfigInfoTestBuilder setConfigId(int configId) {
        mConfigId = configId;
        return this;
    }

    /**
     * Builds an {@link android.car.media.CarAudioZoneConfigInfo}
     * object of the desired state, setting name, {@code zoneId}, and {@code configId}.
     */
    public CarAudioZoneConfigInfo build() {
        return new CarAudioZoneConfigInfo(mName, mZoneId, mConfigId);
    }
}
