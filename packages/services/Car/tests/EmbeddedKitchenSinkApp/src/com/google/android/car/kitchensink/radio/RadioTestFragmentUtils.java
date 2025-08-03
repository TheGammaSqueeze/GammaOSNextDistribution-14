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

package com.google.android.car.kitchensink.radio;

import android.hardware.radio.RadioManager;
import android.util.SparseArray;

import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;

import java.util.Iterator;
import java.util.Map;

final class RadioTestFragmentUtils {

    private RadioTestFragmentUtils() {
        throw new UnsupportedOperationException("RadioTestFragmentUtils class is noninstantiable");
    }

    static String getDabChannelName(RadioManager.ProgramInfo info,
                                    SparseArray<String> dabFrequencyToLabelMap) {
        StringBuilder channelTextBuilder = new StringBuilder();
        channelTextBuilder.append("DAB");

        int dabFrequency = ProgramSelectorExt.getFrequency(info.getSelector());
        if (dabFrequency != ProgramSelectorExt.INVALID_IDENTIFIER_VALUE) {
            channelTextBuilder.append(' ').append(dabFrequencyToLabelMap.get(dabFrequency, ""));
        }

        int dabEnsemble = ProgramSelectorExt.getDabEnsemble(info.getSelector());
        if (dabEnsemble != ProgramSelectorExt.INVALID_IDENTIFIER_VALUE) {
            channelTextBuilder.append(" Ensemble:0x").append(Integer.toHexString(dabEnsemble));
        }
        return channelTextBuilder.toString();
    }

    static SparseArray<String> getDabFrequencyToLabelMap(Map<String, Integer> dabFrequencyTable) {
        SparseArray<String> dabFrequencyToLabelMap = new SparseArray<>();
        if (dabFrequencyTable == null) {
            return dabFrequencyToLabelMap;
        }
        Iterator<Map.Entry<String, Integer>> it = dabFrequencyTable.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> dabFrequencyEntry = it.next();
            dabFrequencyToLabelMap.put(dabFrequencyEntry.getValue(), dabFrequencyEntry.getKey());
        }
        return dabFrequencyToLabelMap;
    }
}
