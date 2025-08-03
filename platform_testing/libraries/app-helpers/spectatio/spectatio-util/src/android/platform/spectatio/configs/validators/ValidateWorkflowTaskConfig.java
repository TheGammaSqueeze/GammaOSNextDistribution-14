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
import android.platform.spectatio.configs.WorkflowTaskConfig;
import android.platform.spectatio.constants.JsonConfigConstants;

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
 * {@link ValidateWorkflowTaskConfig} is a deserializer that validates Task Config in Workflow for
 * Spectatio JSON Config while deserializing it to a Java Object.
 *
 * <p>It checks for valid configuration ( TEXT or UI_ELEMENT ) is provided for the Workflow Task
 */
public class ValidateWorkflowTaskConfig implements JsonDeserializer<WorkflowTaskConfig> {
    private Set<String> mSupportedProperties =
            Set.of(JsonConfigConstants.CONFIG_TEXT, JsonConfigConstants.CONFIG_UI_ELEMENT);

    @Override
    public WorkflowTaskConfig deserialize(
            JsonElement jsonElement, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        validateProperties(jsonObject);

        String textValue = validateAndGetTextValue(JsonConfigConstants.CONFIG_TEXT, jsonObject);

        UiElement uiElementValue =
                validateAndGetUiElementValue(
                        JsonConfigConstants.CONFIG_UI_ELEMENT, jsonObject, context);

        // One of the configurations ( TEXT or UI_ELEMENT ) must be present and must be valid
        if (textValue == null && uiElementValue == null) {
            throw new RuntimeException(
                    String.format("Workflow Task Config %s is invalid.", jsonObject));
        }

        return new WorkflowTaskConfig(textValue, uiElementValue);
    }

    private String validateAndGetTextValue(String key, JsonObject jsonObject) {
        JsonElement value = jsonObject.get(key);
        if (value != null
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString()
                && !value.getAsString().trim().isEmpty()) {
            return value.getAsString().trim();
        }
        return null;
    }

    private UiElement validateAndGetUiElementValue(
            String key, JsonObject jsonObject, JsonDeserializationContext context) {
        JsonElement value = jsonObject.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return (UiElement) context.deserialize(value, UiElement.class);
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
                            unknownProperties.stream().collect(Collectors.joining(", ")),
                            jsonObject));
        }
    }
}
