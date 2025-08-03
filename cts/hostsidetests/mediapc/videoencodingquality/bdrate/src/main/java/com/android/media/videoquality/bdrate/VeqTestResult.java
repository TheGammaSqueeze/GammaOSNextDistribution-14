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

package com.android.media.videoquality.bdrate;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.List;

@AutoValue
public abstract class VeqTestResult {

    private static final Splitter LINE_SPLITTER = Splitter.on(System.lineSeparator());

    private static final Splitter KEY_VALUE_SPLITTER = Splitter.on("=").trimResults();

    public abstract String referenceFile();

    public abstract RateDistortionCurve curve();

    public static VeqTestResult parseFromTestResult(String result) {
        String referenceFile = null;
        ArrayList<Double> bitrates = new ArrayList<>();
        ArrayList<Double> vmafs = new ArrayList<>();

        for (String line : LINE_SPLITTER.split(result)) {
            List<String> keyValue = KEY_VALUE_SPLITTER.splitToList(line);
            if (keyValue.size() != 2) {
                continue;
            }

            String key = keyValue.get(0);
            String value = keyValue.get(1);

            switch (key) {
                case "Y4M file":
                    if (referenceFile == null) {
                        referenceFile = value;
                    } else if (!referenceFile.equals(value)) {
                        throw new IllegalArgumentException(
                                "Test result data contained multiple reference files and cannot "
                                        + "be parsed.");
                    }
                    break;
                case "Bitrate kbps":
                    bitrates.add(Double.parseDouble(value));
                    break;
                case "VMAF score":
                    vmafs.add(Double.parseDouble(value));
                    break;
                default:
                    // Skip any unknown key/value pairs.
            }
        }

        if (bitrates.size() != vmafs.size()) {
            throw new IllegalArgumentException(
                    "Test result data did not have a matching number of bitrate and vmaf "
                            + "values.");
        }

        RateDistortionCurve.Builder curveBuilder = RateDistortionCurve.builder();
        for (int i = 0; i < bitrates.size(); i++) {
            curveBuilder.addPoint(RateDistortionPoint.create(bitrates.get(i), vmafs.get(i)));
        }

        if (referenceFile.endsWith(".y4m")) {
            referenceFile = referenceFile.substring(0, referenceFile.length() - 4);
        }

        return new AutoValue_VeqTestResult(referenceFile, curveBuilder.build());
    }
}
