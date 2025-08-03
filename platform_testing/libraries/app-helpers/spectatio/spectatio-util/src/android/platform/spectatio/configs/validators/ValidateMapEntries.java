/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.platform.spectatio.configs.validators;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * {@link ValidateMapEntries} is a deserializer that validates Commands, Actions and Packages in the
 * Spectatio JSON Config while deserializing it to a Java Map.
 *
 * <p>It ensures that the keys and values in the {@link Map} are Non-Null and Non-Empty Strings
 */
public class ValidateMapEntries implements JsonDeserializer<Map<String, String>> {
    @Override
    public Map<String, String> deserialize(
            JsonElement jsonElement, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        Map<String, String> deserializedMap = new HashMap<String, String>();

        for (Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String key = entry.getKey().trim();

            // GSON converts null key to "null" String
            if (key == null || key.trim().isEmpty() || key.trim().equalsIgnoreCase("null")) {
                throw new RuntimeException(
                        "Property names in Spectatio Json Config should be "
                                + "Non-Null & Non-Empty String.");
            }

            if (entry.getValue().isJsonNull()
                    || (entry.getValue().isJsonPrimitive()
                            && (!entry.getValue().getAsJsonPrimitive().isString()
                                    || entry.getValue().getAsString().trim().isEmpty()))) {
                throw new RuntimeException(
                        String.format(
                                "Value for key %s in Spectatio Json Config should be Non-Null &"
                                        + " Non-Empty String.",
                                key));
            }

            String value = entry.getValue().getAsString().trim();
            deserializedMap.put(key, value);
        }
        return deserializedMap;
    }
}
