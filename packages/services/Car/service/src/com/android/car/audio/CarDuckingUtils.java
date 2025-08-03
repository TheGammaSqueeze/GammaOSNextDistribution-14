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

import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class CarDuckingUtils {

    private CarDuckingUtils() {
    }

    static CarDuckingInfo generateDuckingInfo(CarDuckingInfo oldDuckingInfo,
            List<AudioAttributes> attributesToDuck, List<AudioAttributes> attributesHoldingFocus,
            CarAudioZone zone) {

        List<String> addressesToDuck =
                getAddressesToDuck(attributesToDuck, attributesHoldingFocus, zone);
        List<String> addressesToUnduck =
                getAddressesToUnduck(addressesToDuck, oldDuckingInfo.mAddressesToDuck);

        return new CarDuckingInfo(
                zone.getId(),
                addressesToDuck,
                addressesToUnduck,
                CarHalAudioUtils.audioAttributesToMetadatas(attributesHoldingFocus, zone));
    }

    static List<AudioAttributes> getAudioAttributesHoldingFocus(List<AudioFocusInfo> focusHolders) {
        List<AudioAttributes> audioAttributes = new ArrayList<>(focusHolders.size());
        for (int index = 0; index < focusHolders.size(); index++) {
            audioAttributes.add(focusHolders.get(index).getAttributes());
        }
        return CarAudioContext.getUniqueAttributesHoldingFocus(audioAttributes);
    }

    private static List<String> getAddressesToDuck(List<AudioAttributes> audioAttributesToDuck,
            List<AudioAttributes> activeAudioAttributes, CarAudioZone zone) {
        Set<Integer> uniqueContexts = zone.getCarAudioContext()
                .getUniqueContextsForAudioAttributes(activeAudioAttributes);
        Set<Integer> contextsToDuck = zone.getCarAudioContext()
                .getUniqueContextsForAudioAttributes(audioAttributesToDuck);
        Set<String> addressesToDuck = getAddressesForContexts(contextsToDuck, zone);

        Set<Integer> unduckedContexts = getUnduckedContexts(uniqueContexts, contextsToDuck);
        Set<String> unduckedAddresses = getAddressesForContexts(unduckedContexts, zone);

        // We should not duck any device that's associated with an unducked context holding focus.
        addressesToDuck.removeAll(unduckedAddresses);
        return new ArrayList<>(addressesToDuck);
    }

    private static List<String> getAddressesToUnduck(List<String> addressesToDuck,
            List<String> oldAddressesToDuck) {
        List<String> addressesToUnduck = new ArrayList<>(oldAddressesToDuck);
        addressesToUnduck.removeAll(addressesToDuck);
        return addressesToUnduck;
    }

    private static Set<Integer> getUnduckedContexts(Set<Integer> contexts,
            Set<Integer> duckedContexts) {
        Set<Integer> unduckedContexts = new ArraySet<>(contexts);
        unduckedContexts.removeAll(duckedContexts);
        return unduckedContexts;
    }

    private static Set<String> getAddressesForContexts(Set<Integer> contexts, CarAudioZone zone) {
        Set<String> addresses = new ArraySet<>();
        for (Integer context : contexts) {
            addresses.add(zone.getAddressForContext(context));
        }
        return addresses;
    }
}
