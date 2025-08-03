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

import android.platform.spectatio.configs.ScrollConfig;
import android.platform.spectatio.configs.SwipeConfig;
import android.platform.spectatio.configs.WorkflowTask;
import android.platform.spectatio.configs.WorkflowTaskConfig;
import android.platform.spectatio.constants.JsonConfigConstants;
import android.platform.spectatio.constants.JsonConfigConstants.SupportedWorkFlowTasks;

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
 * {@link ValidateWorkflowTask} is a deserializer that validates the given Workflow Task for
 * Spectatio JSON Config while deserializing it to a Java Object.
 *
 * <p>It checks for 1. Workflow Task has valid properties. 2. Workflow Task Name is provided. 3.
 * Workflow Task Type is provided and is valid. 4. Workflow Task Config is provided and is valid. 5.
 * For the type that needs scrolling, a valid Scroll Config is provided.
 */
public class ValidateWorkflowTask implements JsonDeserializer<WorkflowTask> {
    private final Set<String> mSupportedProperties =
            Set.of(
                    JsonConfigConstants.NAME,
                    JsonConfigConstants.WORKFLOW_TYPE,
                    JsonConfigConstants.CONFIG,
                    JsonConfigConstants.REPEAT_COUNT,
                    JsonConfigConstants.SCROLL_CONFIG,
                    JsonConfigConstants.SWIPE_CONFIG);

    @Override
    public WorkflowTask deserialize(
            JsonElement jsonElement, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        validateProperties(jsonObject);

        String name =
                validateAndGetStringValue(
                        JsonConfigConstants.NAME, jsonObject, /*isOptional= */ false);

        String type =
                validateAndGetStringValue(
                        JsonConfigConstants.WORKFLOW_TYPE, jsonObject, /*isOptional= */ false);
        SupportedWorkFlowTasks workflowTaskType = null;
        try {
            workflowTaskType = SupportedWorkFlowTasks.valueOf(type);
        } catch (IllegalArgumentException ex) {
            throwRuntimeException("Workflow Task Type", type, jsonObject, "Not Supported");
        }

        if (workflowTaskType != SupportedWorkFlowTasks.SWIPE) {
            validateNotNull(JsonConfigConstants.CONFIG, jsonObject);
        }

        WorkflowTaskConfig config =
                context.deserialize(
                        jsonObject.get(JsonConfigConstants.CONFIG), WorkflowTaskConfig.class);

        // Check valid config is provided based on the type of workflow
        switch (workflowTaskType) {
            case COMMAND:
            case PRESS:
            case LONG_PRESS:
            case HAS_PACKAGE_IN_FOREGROUND:
            case WAIT_MS:
                if (config.getText() == null) {
                    throwRuntimeException(
                            "Config TEXT for Workflow Task Type",
                            type,
                            jsonObject,
                            "Missing or Invalid");
                }
                break;
            case CLICK:
            case LONG_CLICK:
            case CLICK_IF_EXIST:
            case HAS_UI_ELEMENT_IN_FOREGROUND:
            case SCROLL_TO_FIND_AND_CLICK:
            case SCROLL_TO_FIND_AND_CLICK_IF_EXIST:
            case SWIPE_TO_FIND_AND_CLICK:
            case SWIPE_TO_FIND_AND_CLICK_IF_EXIST:
                if (config.getUiElement() == null) {
                    throwRuntimeException(
                            "Config UI_ELEMENT for Workflow Task Type",
                            type,
                            jsonObject,
                            "Missing or Invalid");
                }
                break;
            case SWIPE:
                // the SWIPE task type only has type-specific config, handled below
                break;
            default:
                throwRuntimeException("Workflow Task Type", type, jsonObject, "Not Supported");
        }

        // Check if a valid type-specific config is provided when e.g. scrolling/swiping is required
        ScrollConfig scrollConfig = null;
        SwipeConfig swipeConfig = null;
        switch (workflowTaskType) {
            case SCROLL_TO_FIND_AND_CLICK:
            case SCROLL_TO_FIND_AND_CLICK_IF_EXIST:
                validateNotNull(JsonConfigConstants.SCROLL_CONFIG, jsonObject);
                scrollConfig =
                        context.deserialize(
                                jsonObject.get(JsonConfigConstants.SCROLL_CONFIG),
                                ScrollConfig.class);
                break;
            case SWIPE:
            case SWIPE_TO_FIND_AND_CLICK:
            case SWIPE_TO_FIND_AND_CLICK_IF_EXIST:
                validateNotNull(JsonConfigConstants.SWIPE_CONFIG, jsonObject);
                swipeConfig =
                        context.deserialize(
                                jsonObject.get(JsonConfigConstants.SWIPE_CONFIG),
                                SwipeConfig.class);
                break;
            default:
                // Nothing To Do
                break;
        }

        int repeatCount = validateAndGetIntValue(JsonConfigConstants.REPEAT_COUNT, jsonObject);

        return new WorkflowTask(name, type, config, repeatCount, scrollConfig, swipeConfig);
    }

    private int validateAndGetIntValue(String key, JsonObject jsonObject) {
        JsonElement value = jsonObject.get(key);
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsInt();
        }
        return 0;
    }

    private String validateAndGetStringValue(
            String key, JsonObject jsonObject, boolean isOptional) {
        JsonElement value = jsonObject.get(key);
        if (value != null
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString()
                && !value.getAsString().trim().isEmpty()) {
            return value.getAsString().trim();
        }
        if (!isOptional) {
            throwRuntimeException("Non-optional property", key, jsonObject, "Missing or Invalid");
        }
        return null;
    }

    private void validateNotNull(String key, JsonObject jsonObject) {
        JsonElement value = jsonObject.get(key);
        if (value == null || value.isJsonNull()) {
            throwRuntimeException("Non-optional property", key, jsonObject, "Missing or Invalid");
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

    private void throwRuntimeException(
            String property, String value, JsonObject jsonObject, String reason) {
        throw new RuntimeException(
                String.format(
                        "%s %s for %s in Spectatio Config is %s.",
                        property, value, jsonObject, reason));
    }
}
