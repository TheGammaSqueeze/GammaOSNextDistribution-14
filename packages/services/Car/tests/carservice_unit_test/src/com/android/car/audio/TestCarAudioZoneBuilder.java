/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.audio;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DEBUGGING_CODE;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import java.util.ArrayList;
import java.util.List;

public final class TestCarAudioZoneBuilder {

    private final int mAudioZoneId;
    private final List<CarAudioZoneConfig> mCarAudioZoneConfigs = new ArrayList<>();
    private final String mAudioZoneName;
    private CarAudioContext mCarAudioContext =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
    public TestCarAudioZoneBuilder(String audioZoneName, int audioZoneId) {
        mAudioZoneId = audioZoneId;
        mAudioZoneName = audioZoneName;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
    TestCarAudioZoneBuilder setCarAudioContexts(CarAudioContext carAudioContext) {
        mCarAudioContext = carAudioContext;
        return this;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
    TestCarAudioZoneBuilder addCarAudioZoneConfig(CarAudioZoneConfig carAudioZoneConfig) {
        mCarAudioZoneConfigs.add(carAudioZoneConfig);
        return this;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
    CarAudioZone build() {
        CarAudioZone carAudioZone = new CarAudioZone(mCarAudioContext, mAudioZoneName,
                mAudioZoneId);
        for (int i = 0; i < mCarAudioZoneConfigs.size(); i++) {
            carAudioZone.addZoneConfig(mCarAudioZoneConfigs.get(i));
        }
        return carAudioZone;
    }
}
