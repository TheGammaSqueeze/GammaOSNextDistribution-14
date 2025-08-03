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
import android.platform.spectatio.configs.UiElement;
import android.platform.spectatio.constants.JsonConfigConstants;
import android.platform.spectatio.constants.JsonConfigConstants.ScrollActions;
import android.platform.spectatio.constants.JsonConfigConstants.ScrollDirection;

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
 * {@link ValidateScrollConfig} is a deserializer that validates Scroll Config in Spectatio JSON
 * Config while deserializing it to a Java Object.
 *
 * <p>It checks for - 1. If SCROLL_ACTION is valid ( USE_BUTTON or USE_GESTURE ) 2. If
 * SCROLL_DIRECTION is valid ( VERTICAL or HORIZONTAL, required only for USE_GESTURE ) 3. If values
 * are correctly provided ( for USE_BUTTON, both SCROLL_FORWARD and SCROLL_BACKWARD button is
 * needed. for USE_GESTURE, SCROLL_ELEMENT is needed. )
 */
public class ValidateScrollConfig implements JsonDeserializer<ScrollConfig> {
    private Set<String> mSupportedProperties =
            Set.of(
                    JsonConfigConstants.SCROLL_ACTION,
                    JsonConfigConstants.SCROLL_DIRECTION,
                    JsonConfigConstants.SCROLL_FORWARD,
                    JsonConfigConstants.SCROLL_BACKWARD,
                    JsonConfigConstants.SCROLL_ELEMENT,
                    JsonConfigConstants.SCROLL_MARGIN,
                    JsonConfigConstants.SCROLL_WAIT_TIME);

    @Override
    public ScrollConfig deserialize(
            JsonElement jsonElement, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        validateProperties(jsonObject);

        String scrollAction =
                validateAndGetValue(
                        JsonConfigConstants.SCROLL_ACTION, jsonObject, /*isOptional= */ false);

        ScrollActions scrollActionValue = null;
        try {
            scrollActionValue = ScrollActions.valueOf(scrollAction);
        } catch (IllegalArgumentException ex) {
            throwRuntimeException("Scroll Action", scrollAction, jsonObject, "Not Supported");
        }

        ScrollConfig scrollConfig = null;
        switch (scrollActionValue) {
            case USE_BUTTON:
                validateNotNull(JsonConfigConstants.SCROLL_FORWARD, jsonObject);
                validateNotNull(JsonConfigConstants.SCROLL_BACKWARD, jsonObject);
                scrollConfig =
                        new ScrollConfig(
                                scrollAction,
                                (UiElement)
                                        context.deserialize(
                                                jsonObject.get(JsonConfigConstants.SCROLL_FORWARD),
                                                UiElement.class),
                                (UiElement)
                                        context.deserialize(
                                                jsonObject.get(JsonConfigConstants.SCROLL_BACKWARD),
                                                UiElement.class));
                break;
            case USE_GESTURE:
                String scrollDirection =
                        validateAndGetValue(
                                JsonConfigConstants.SCROLL_DIRECTION,
                                jsonObject, /*isOptional*/
                                false);
                String scrollMargin =
                        validateAndGetValue(
                                JsonConfigConstants.SCROLL_MARGIN, jsonObject, /*isOptional*/ true);
                if (scrollMargin != null) {
                    validateIsNumeric(scrollMargin, jsonObject);
                }

                String scrollWaitTime =
                        validateAndGetValue(
                                JsonConfigConstants.SCROLL_WAIT_TIME,
                                jsonObject, /*isOptional*/
                                true);
                if (scrollWaitTime != null) {
                    validateIsNumeric(scrollWaitTime, jsonObject);
                }

                try {
                    // Check if given scroll direction is valid
                    ScrollDirection.valueOf(scrollDirection);
                } catch (IllegalArgumentException ex) {
                    throwRuntimeException(
                            "Scroll Direction", scrollDirection, jsonObject, "Not Supported");
                }
                validateNotNull(JsonConfigConstants.SCROLL_ELEMENT, jsonObject);
                scrollConfig =
                        new ScrollConfig(
                                scrollAction,
                                scrollDirection,
                                (UiElement)
                                        context.deserialize(
                                                jsonObject.get(JsonConfigConstants.SCROLL_ELEMENT),
                                                UiElement.class),
                                scrollMargin,
                                scrollWaitTime);
                break;
        }
        return scrollConfig;
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
            throwRuntimeException("Non-optional Property", key, jsonObject, "Missing or Invalid");
        }
        return null;
    }

    private void validateNotNull(String key, JsonObject jsonObject) {
        JsonElement value = jsonObject.get(key);
        if (value == null || value.isJsonNull()) {
            throwRuntimeException("Non-optional Property", key, jsonObject, "Missing or Invalid");
        }
    }

    private void validateIsNumeric(String str, JsonObject jsonObject) {
        try {
            Integer.parseInt(str);
        } catch (NumberFormatException e) {
            throwRuntimeException("Numeric Property", str, jsonObject, "Invalid");
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
                            unknownProperties.stream().collect(Collectors.joining(", ")),
                            jsonObject));
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
