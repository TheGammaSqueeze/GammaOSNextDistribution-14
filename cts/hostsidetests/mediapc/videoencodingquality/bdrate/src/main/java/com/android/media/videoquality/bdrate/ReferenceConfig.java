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
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.PrimitiveIterator;

@AutoValue
public abstract class ReferenceConfig {
    public abstract String testId();

    public abstract String referenceFile();

    public abstract RateDistortionCurve referenceCurve();

    public abstract double referenceThreshold();

    public static ReferenceConfig create(
            String testId,
            String referenceFile,
            RateDistortionCurve curve,
            double referenceThreshold) {
        return new AutoValue_ReferenceConfig(testId, referenceFile, curve, referenceThreshold);
    }

    public static class Deserializer implements JsonDeserializer<ReferenceConfig> {

        @Override
        public ReferenceConfig deserialize(
                JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject referenceConfig = json.getAsJsonObject();

            String testId = referenceConfig.get("TestId").getAsString();

            // Trim the file extension off the reference filename.
            String referenceFileName = referenceConfig.get("RefFileName").getAsString();
            if (referenceFileName.endsWith(".y4m")) {
                referenceFileName = referenceFileName.substring(0, referenceFileName.length() - 4);
            }

            // These are stored as whitespace separated, so parse them,
            // split them, and convert them into a RateDistortionCurve.
            String refRates = referenceConfig.get("RefRate").getAsString();
            String refVmafs = referenceConfig.get("RefVmaf").getAsString();

            PrimitiveIterator.OfDouble rates =
                    Splitter.on(CharMatcher.whitespace())
                            .splitToStream(refRates)
                            .mapToDouble(Double::parseDouble)
                            .iterator();
            PrimitiveIterator.OfDouble vmafs =
                    Splitter.on(CharMatcher.whitespace())
                            .splitToStream(refVmafs)
                            .mapToDouble(Double::parseDouble)
                            .iterator();

            RateDistortionCurve.Builder curveBuilder = RateDistortionCurve.builder();
            while (rates.hasNext() && vmafs.hasNext()) {
                curveBuilder.addPoint(RateDistortionPoint.create(rates.next(), vmafs.next()));
            }

            // If there was a misalignment of values, throw JsonParseException
            if (rates.hasNext() || vmafs.hasNext()) {
                throw new JsonParseException(
                        "Number of bitrates did not match number of VMAF scores.");
            }
            RateDistortionCurve curve = curveBuilder.build();

            double referenceThreshold =
                    Double.parseDouble(referenceConfig.get("RefThreshold").getAsString());

            return ReferenceConfig.create(testId, referenceFileName, curve, referenceThreshold);
        }
    }
}
