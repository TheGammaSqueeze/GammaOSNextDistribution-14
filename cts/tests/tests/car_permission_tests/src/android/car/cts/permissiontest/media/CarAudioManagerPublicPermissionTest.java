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

package android.car.cts.permissiontest.media;

import static android.car.Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.media.CarAudioManager;
import android.car.media.CarAudioManager.CarVolumeCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class contains security permission tests for the {@link CarAudioManager}'s public APIs.
 */
@RunWith(AndroidJUnit4.class)
public final class CarAudioManagerPublicPermissionTest extends AbstractCarManagerPermissionTest {
    private CarAudioManager mCarAudioManager;

    @Before
    public void setUp() throws Exception {
        super.connectCar();
        mCarAudioManager = (CarAudioManager) mCar.getCarManager(Car.AUDIO_SERVICE);
    }

    @Test
    public void registerCarVolumeCallbackPermission() {
        CarVolumeCallback callback = new CarVolumeCallback() {};
        Exception e = assertThrows(SecurityException.class,
                () -> mCarAudioManager.registerCarVolumeCallback(callback));
        assertThat(e.getMessage()).contains(PERMISSION_CAR_CONTROL_AUDIO_VOLUME);
    }

    @Test
    public void isAudioFeatureEnabled_withVolumeGroupMuteFeature_succeeds() {
        boolean volumeGroupMutingEnabled =
                mCarAudioManager.isAudioFeatureEnabled(
                        CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING);

        assertThat(volumeGroupMutingEnabled).isAnyOf(true, false);
    }

    @Test
    public void isAudioFeatureEnabled_withDynamicRoutingFeature_succeeds() {
        boolean dynamicRoutingEnabled =
                mCarAudioManager.isAudioFeatureEnabled(
                        CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING);

        assertThat(dynamicRoutingEnabled).isAnyOf(true, false);
    }

    @Test
    public void isAudioFeatureEnabled_withNonAudioFeature_fails() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.isAudioFeatureEnabled(0));

        assertThat(exception).hasMessageThat().contains("Unknown Audio Feature");
    }
}
