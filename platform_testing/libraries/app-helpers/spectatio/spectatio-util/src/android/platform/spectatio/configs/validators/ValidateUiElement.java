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

import android.platform.spectatio.configs.UiElement;
import android.platform.spectatio.constants.JsonConfigConstants;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link ValidateUiElement} is a deserializer that validates Ui Elements in Spectatio JSON Config
 * while deserializing it to a Java Object.
 *
 * <p>It checks for - 1. If TYPE is valid ( TEXT, TEXT_CONTAINS, DESCRIPTION, RESOURCE_ID, CLASS )
 * 2. If values are correctly provided ( PACKAGE is required if TYPE is RESOURCE_ID )
 */
public class ValidateUiElement implements JsonDeserializer<UiElement> {
    private Set<String> mSupportedTypes =
            Set.of(
                    JsonConfigConstants.CLICKABLE,
                    JsonConfigConstants.SCROLLABLE,
                    JsonConfigConstants.TEXT,
                    JsonConfigConstants.TEXT_CONTAINS,
                    JsonConfigConstants.DESCRIPTION,
                    JsonConfigConstants.CLASS,
                    JsonConfigConstants.HAS_ANCESTOR,
                    JsonConfigConstants.HAS_DESCENDANT,
                    JsonConfigConstants.MULTIPLE,
                    JsonConfigConstants.RESOURCE_ID);

    private Set<String> mSupportedProperties =
            Set.of(
                    JsonConfigConstants.TYPE,
                    JsonConfigConstants.VALUE,
                    JsonConfigConstants.PACKAGE,
                    JsonConfigConstants.FLAG,
                    JsonConfigConstants.MAX_DEPTH,
                    JsonConfigConstants.ANCESTOR,
                    JsonConfigConstants.DESCENDANT,
                    JsonConfigConstants.SPECIFIERS);

    @Override
    public UiElement deserialize(
            JsonElement jsonElement, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        validateProperties(jsonObject);

        String type =
                validateAndGetValue(JsonConfigConstants.TYPE, jsonObject, /*isOptional*/ false);

        validateType(type);

        if (JsonConfigConstants.CLICKABLE.equals(type)
                || JsonConfigConstants.SCROLLABLE.equals(type)) {
            boolean flag = validateAndGetBoolean(JsonConfigConstants.FLAG, jsonObject, true);
            return new UiElement(type, flag);
        }

        if (JsonConfigConstants.MULTIPLE.equals(type)) {
            JsonArray specifiersJson =
                    validateAndGetArray(JsonConfigConstants.SPECIFIERS, jsonObject);
            List<UiElement> specifiers =
                    specifiersJson.asList().stream()
                            .map(element -> context.<UiElement>deserialize(element, typeOfT))
                            .collect(Collectors.toList());

            int ancestorSpecifiers = 0;
            for (UiElement specifier : specifiers) {
                if (JsonConfigConstants.MULTIPLE.equals(specifier.getType())) {
                    throw new RuntimeException(
                            "Multiple-specifier can't contain a multiple-specifier.");
                }
                if (JsonConfigConstants.HAS_ANCESTOR.equals(specifier.getType())) {
                    ancestorSpecifiers++;
                    if (ancestorSpecifiers > 1) {
                        throw new RuntimeException(
                                "Multiple-specifier can't contain more than one ancestor "
                                        + "specifier.");
                    }
                }
            }
            return new UiElement(specifiers);
        }

        if (JsonConfigConstants.HAS_ANCESTOR.equals(type)) {
            JsonObject parent = validateAndGetObject(JsonConfigConstants.ANCESTOR, jsonObject);
            int maxDepth = validateAndGetInteger(JsonConfigConstants.MAX_DEPTH, jsonObject, 1);
            return new UiElement(
                    JsonConfigConstants.HAS_ANCESTOR,
                    context.deserialize(parent, typeOfT),
                    maxDepth);
        }

        if (JsonConfigConstants.HAS_DESCENDANT.equals(type)) {
            JsonObject childJson = validateAndGetObject(JsonConfigConstants.DESCENDANT, jsonObject);
            int maxDepth = validateAndGetInteger(JsonConfigConstants.MAX_DEPTH, jsonObject, 1);
            return new UiElement(
                    JsonConfigConstants.HAS_DESCENDANT,
                    context.deserialize(childJson, typeOfT),
                    maxDepth);
        }

        String value =
                validateAndGetValue(JsonConfigConstants.VALUE, jsonObject, /*isOptional*/ false);

        // Package is not required for SCROLLABLE, CLICKABLE, TEXT, TEXT_CONTAINS and DESCRIPTION

        // Package is optional for CLASS and RESOURCE_ID
        String pkg =
                validateAndGetValue(JsonConfigConstants.PACKAGE, jsonObject, /*isOptional*/ true);

        return new UiElement(type, value, pkg);
    }

    /**
     * Validate and return the value of given property from Json Object
     *
     * @param key : Property name to get the value from Json Object
     * @param jsonObject : Json Object to read the property
     * @param isOptional : If the property is optional, it will be true else false.
     *     <p>If isOptional is false, then throw an exception if the property is missing If
     *     isOptional is true, then ignore if the property is missing
     */
    private String validateAndGetValue(String key, JsonObject jsonObject, boolean isOptional) {
        JsonElement value = jsonObject.get(key);
        if (value != null
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString()
                && !value.getAsString().trim().isEmpty()) {
            return value.getAsString().trim();
        }
        if (!isOptional) {
            throw new RuntimeException(
                    String.format(
                            "Non-optional string property %s for %s in Spectatio JSON Config "
                                    + "is either missing or not a valid string.",
                            key, jsonObject));
        }
        return null;
    }

    private boolean validateAndGetBoolean(String key, JsonObject jsonObject, Boolean defaultValue) {
        JsonElement value = jsonObject.get(key);
        if (value == null) {
            if (defaultValue == null) {
                throw new RuntimeException(
                        String.format(
                                "Non-optional boolean property %s for %s in Spectatio JSON Config "
                                        + "is missing.",
                                key, jsonObject));
            }
            return defaultValue;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new RuntimeException(
                    String.format(
                            "Non-optional boolean property %s for %s in Spectatio JSON Config "
                                    + "is not a valid boolean.",
                            key, jsonObject));
        }
        return value.getAsBoolean();
    }

    private int validateAndGetInteger(String key, JsonObject jsonObject, Integer defaultValue) {
        JsonElement value = jsonObject.get(key);
        if (value == null) {
            if (defaultValue == null) {
                throw new RuntimeException(
                        String.format(
                                "Non-optional integer property %s for %s in Spectatio JSON Config "
                                        + "is missing.",
                                key, jsonObject));
            }
            return defaultValue;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new RuntimeException(
                    String.format(
                            "Non-optional integer property %s for %s in Spectatio JSON Config "
                                    + "is not a valid integer.",
                            key, jsonObject));
        }
        try {
            return value.getAsInt();
        } catch (NumberFormatException e) {
            throw new RuntimeException(
                    String.format(
                            "Non-optional integer property %s for %s in Spectatio JSON Config "
                                    + "is not a valid integer.",
                            key, jsonObject));
        }
    }

    private JsonObject validateAndGetObject(String key, JsonObject jsonObject) {
        JsonElement value = jsonObject.get(key);
        if (value != null && value.isJsonObject()) {
            return value.getAsJsonObject();
        }
        throw new RuntimeException(
                String.format(
                        "Non-optional object property %s for %s in Spectatio JSON Config "
                                + "is either missing or not a valid JSON object.",
                        key, jsonObject));
    }

    private JsonArray validateAndGetArray(String key, JsonObject jsonObject) {
        JsonElement value = jsonObject.get(key);
        if (value != null && value.isJsonArray()) {
            return value.getAsJsonArray();
        }
        throw new RuntimeException(
                String.format(
                        "Non-optional array property %s for %s in Spectatio JSON Config "
                                + "is either missing or not a valid JSON array.",
                        key, jsonObject));
    }

    private void validateType(String type) {
        if (!mSupportedTypes.contains(type)) {
            throw new RuntimeException(
                    String.format(
                            "UI Element TYPE %s in Spectatio JSON Config is invalid. Supported"
                                + " Types: [ RESOURCE_ID, TEXT, TEXT_CONTAINS, DESCRIPTION, CLASS,"
                                + " MULTIPLE, HAS_ANCESTOR, HAS_DESCENDANT ]",
                            type));
        }
    }

    private void validateProperties(JsonObject jsonObject) {
        List<String> unknownProperties =
                jsonObject.entrySet().stream()
                        .map(Entry::getKey)
                        .map(String::trim)
                        .filter(key -> !mSupportedProperties.contains(key))
                        .collect(Collectors.toList());
        if (!unknownProperties.isEmpty()) {
            throw new RuntimeException(
                    String.format(
                            "Unknown properties: [ %s ] for %s in Spectatio JSON Config",
                            String.join(", ", unknownProperties), jsonObject));
        }
    }
}
