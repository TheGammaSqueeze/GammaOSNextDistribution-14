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

package android.car.cts.property;

import android.util.ArrayMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A parser for CarSvcProps.json which is the config file for car property service.
 */
public final class CarSvcPropsParser {
    private static final String CONFIG_RESOURCE_NAME = "CarSvcProps.json";
    private static final String JSON_FIELD_NAME_PROPERTIES = "properties";

    private final List<Integer> mAllSystemPropertyIds = new ArrayList<>();
    private final Map<String, List<Integer>> mSystemPropertyIdsByFlag = new ArrayMap<>();

    public CarSvcPropsParser() {
        String configString;
        try (InputStream configFile = this.getClass().getClassLoader()
                .getResourceAsStream(CONFIG_RESOURCE_NAME)) {
            try {
                configString = new String(configFile.readAllBytes());
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Cannot read from config file: " + CONFIG_RESOURCE_NAME, e);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close config resource stream", e);
        }

        JSONObject configJsonObject;
        try {
            configJsonObject = new JSONObject(configString);
        } catch (JSONException e) {
            throw new IllegalStateException("Config file: " + CONFIG_RESOURCE_NAME
                    + " does not contain a valid JSONObject.", e);
        }
        try {
            JSONObject properties = configJsonObject.getJSONObject(JSON_FIELD_NAME_PROPERTIES);
            Iterator<String> keysIt = properties.keys();
            while (keysIt.hasNext()) {
                String propertyName = keysIt.next();
                JSONObject propertyObj = properties.getJSONObject(propertyName);
                if (propertyObj.optBoolean("deprecated")) {
                    continue;
                }
                int propertyId = propertyObj.getInt("propertyId");
                mAllSystemPropertyIds.add(propertyId);
                String featureFlag = propertyObj.optString("featureFlag");
                if (!featureFlag.isEmpty()) {
                    if (mSystemPropertyIdsByFlag.get(featureFlag) == null) {
                        mSystemPropertyIdsByFlag.put(featureFlag, new ArrayList<>());
                    }
                    mSystemPropertyIdsByFlag.get(featureFlag).add(propertyId);
                }
            }
        } catch (JSONException e) {
            throw new IllegalStateException("Config file: " + CONFIG_RESOURCE_NAME
                    + " has invalid JSON format.", e);
        }
    }

    /**
     * Gets all the defined system property IDs.
     */
    public List<Integer> getAllSystemPropertyIds() {
        return new ArrayList<>(mAllSystemPropertyIds);
    }

    /**
     * Gets the defined system property IDs under the given flag.
     */
    public List<Integer> getSystemPropertyIdsForFlag(String flag) {
        List<Integer> ids = mSystemPropertyIdsByFlag.get(flag);
        if (ids == null) {
            return new ArrayList<Integer>();
        }
        return new ArrayList<Integer>(ids);
    }
}
