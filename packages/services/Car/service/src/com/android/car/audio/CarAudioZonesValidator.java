/*
 * Copyright (C) 2019 The Android Open Source Project
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


import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC;

import android.media.AudioDeviceAttributes;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.internal.util.Preconditions;

import java.util.List;
import java.util.Set;

/*
 * Class to help validate audio zones are constructed correctly.
 */
final class CarAudioZonesValidator {

    private CarAudioZonesValidator() {
        throw new UnsupportedOperationException(
                "CarAudioZonesValidator class is non-instantiable, contains static members only");
    }

    /**
     * Returns {@code true} if validation succeeds, throws an a run time exception otherwise.
     *
     * <p>The current rules that apply are:
     * <ul>
     * <li>There must be a zone defined
     * <li>Has valid zone configuration, see
     *  {@link CarAudioZoneConfig#validateVolumeGroups(CarAudioContext, boolean)}) for further
     *  information.
     *  <li>Configurations can be routed by dynamic audio policy if core routing is not used, see
     *  {@link CarAudioZoneConfig#validateCanUseDynamicMixRouting(boolean)} for further information.
     *  <li>Device addresses are not shared across zones
     *  <li>Device addresses are not shared across volume groups in same config
     *  <li>Device addresses can be shared across configs in the same zone
     * </ul>
     *
     * @param carAudioZones Audio zones to validate
     * @param useCoreAudioRouting If the service is using core audio routing
     * @throws RuntimeException when ever there is a failure when validating the audio zones
     */
    static void validate(SparseArray<CarAudioZone> carAudioZones, boolean useCoreAudioRouting)
            throws RuntimeException {
        validateAtLeastOneZoneDefined(carAudioZones);
        validateZoneConfigsForEachZone(carAudioZones, useCoreAudioRouting);
        if (!useCoreAudioRouting) {
            validateEachAddressAppearsAtMostOnceInOneConfig(carAudioZones);
        }
        validatePrimaryZoneHasInputDevice(carAudioZones);
    }

    private static void validatePrimaryZoneHasInputDevice(SparseArray<CarAudioZone> carAudioZones) {
        CarAudioZone primaryZone = carAudioZones.get(PRIMARY_AUDIO_ZONE);
        List<AudioDeviceAttributes> devices = primaryZone.getInputAudioDevices();
        Preconditions.checkCollectionNotEmpty(devices, "Primary Zone Input Devices");
        for (int index = 0; index < devices.size(); index++) {
            AudioDeviceAttributes device = devices.get(index);
            if (device.getType() == TYPE_BUILTIN_MIC) {
                return;
            }
        }
        throw new RuntimeException("Primary Zone must have at least one microphone input device");
    }

    private static void validateAtLeastOneZoneDefined(SparseArray<CarAudioZone> carAudioZones) {
        if (carAudioZones.size() == 0) {
            throw new RuntimeException("At least one zone should be defined");
        }
    }

    private static void validateZoneConfigsForEachZone(SparseArray<CarAudioZone> carAudioZones,
            boolean useCoreAudioRouting) {
        for (int i = 0; i < carAudioZones.size(); i++) {
            CarAudioZone zone = carAudioZones.valueAt(i);
            if (!zone.validateZoneConfigs(useCoreAudioRouting)) {
                throw new RuntimeException(
                        "Invalid zone configurations for zone " + zone.getId());
            }
            // TODO(b/301391301) use fore routing for all zones.
            // Currently force "useCoreAudioRouting" to be false for non primary zones as only
            // primary zone supports core routing
            if (!zone.validateCanUseDynamicMixRouting(
                    zone.isPrimaryZone() && useCoreAudioRouting)) {
                throw new RuntimeException(
                        "Invalid Configuration to use Dynamic Mix for zone " + zone.getId());
            }
        }
    }

    private static void validateEachAddressAppearsAtMostOnceInOneConfig(
            SparseArray<CarAudioZone> carAudioZones) {
        Set<String> addresses = new ArraySet<>();
        for (int i = 0; i < carAudioZones.size(); i++) {
            List<CarAudioZoneConfig> zoneConfigs =
                    carAudioZones.valueAt(i).getAllCarAudioZoneConfigs();
            ArraySet<String> addressesPerZone = new ArraySet<>();
            for (int configIndex = 0; configIndex < zoneConfigs.size(); configIndex++) {
                Set<String> addressesPerConfig = new ArraySet<>();
                CarAudioZoneConfig config = zoneConfigs.get(configIndex);
                CarVolumeGroup[] groups = config.getVolumeGroups();
                for (CarVolumeGroup carVolumeGroup : groups) {
                    validateVolumeGroupAddresses(addressesPerConfig, carVolumeGroup.getAddresses());
                }
                // No need to check for addresses shared among configurations
                // as that is allowed
                addressesPerZone.addAll(addressesPerConfig);
            }

            for (int c = 0; c < addressesPerZone.size(); c++) {
                String address = addressesPerZone.valueAt(c);
                if (addresses.add(address)) {
                    continue;
                }
                throw  new IllegalStateException("Address " + address + " repeats among multiple"
                        + " zones in car_audio_configuration.xml");
            }
        }
    }

    private static void validateVolumeGroupAddresses(Set<String> addressesPerConfig,
                                                     List<String> groupAddresses) {
        for (int c = 0; c < groupAddresses.size(); c++) {
            String address = groupAddresses.get(c);
            // Ignore dynamic devices as they may not have addresses until the device is connected
            if (address == null || address.isEmpty()) {
                continue;
            }
            if (!addressesPerConfig.add(address)) {
                throw new RuntimeException("Device with address " + address
                        + " appears in multiple volume groups in the same configuration");
            }
        }
    }
}
