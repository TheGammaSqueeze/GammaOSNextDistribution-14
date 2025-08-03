/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.fixture;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import android.os.SystemProperties;
import android.util.Log;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.testing.StaticMockFixture;

import org.mockito.invocation.InvocationOnMock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fixture class mocks {@link SystemProperties} to return the set value or its default value. */
public final class TestableSystemProperties implements StaticMockFixture {

    private static final String TAG = TestableSystemProperties.class.getSimpleName();
    private static Map<String, String> sKeyValueMap = new ConcurrentHashMap<>();

    @Override
    public StaticMockitoSessionBuilder setUpMockedClasses(
            StaticMockitoSessionBuilder sessionBuilder) {
        sessionBuilder.spyStatic(SystemProperties.class);
        return sessionBuilder;
    }

    @Override
    public void setUpMockBehaviors() {
        ExtendedMockito.doAnswer(invocation -> getIntValue(invocation))
                .when(() -> SystemProperties.getInt(anyString(), anyInt()));
        ExtendedMockito.doAnswer(invocation -> getLongValue(invocation))
                .when(() -> SystemProperties.getLong(anyString(), anyLong()));
        ExtendedMockito.doAnswer(invocation -> getBooleanValue(invocation))
                .when(() -> SystemProperties.getBoolean(anyString(), anyBoolean()));
        ExtendedMockito.doAnswer(invocation -> getStringValue(invocation))
                .when(() -> SystemProperties.get(anyString(), anyString()));
        ExtendedMockito.doAnswer(invocation -> getStringValueWithoutDefault(invocation))
                .when(() -> SystemProperties.get(anyString()));
    }

    @Override
    public void tearDown() {
        reset();
    }

    /** Sets the system property for the given {@code key} */
    public static void set(String key, String value) {
        Log.v(TAG, String.format("Set(key = %s, value = %s", key, value));
        sKeyValueMap.put(key, value);
    }

    /** Resets/Clears all the key,value pairs. */
    public static void reset() {
        sKeyValueMap.clear();
    }

    private static String getStringValueWithoutDefault(InvocationOnMock invocation) {
        String key = invocation.getArgument(0);
        String valueToReturn = sKeyValueMap.getOrDefault(key, "");
        logGetterCall(invocation, valueToReturn);
        return valueToReturn;
    }

    private static String getStringValue(InvocationOnMock invocation) {
        String key = invocation.getArgument(0);
        String defaultValue = invocation.getArgument(1);
        String valueToReturn = sKeyValueMap.getOrDefault(key, defaultValue);
        logGetterCall(invocation, valueToReturn);
        return valueToReturn;
    }

    private static Integer getIntValue(InvocationOnMock invocation) {
        String key = invocation.getArgument(0);
        Integer defaultValue = invocation.getArgument(1);
        Integer valueToReturn =
                sKeyValueMap.containsKey(key)
                        ? Integer.parseInt(sKeyValueMap.get(key))
                        : defaultValue;
        logGetterCall(invocation, valueToReturn);
        return valueToReturn;
    }

    private static Long getLongValue(InvocationOnMock invocation) {
        String key = invocation.getArgument(0);
        Long defaultValue = invocation.getArgument(1);
        Long valueToReturn =
                sKeyValueMap.containsKey(key)
                        ? Long.parseLong(sKeyValueMap.get(key))
                        : defaultValue;
        logGetterCall(invocation, valueToReturn);
        return valueToReturn;
    }

    private static Boolean getBooleanValue(InvocationOnMock invocation) {
        String key = invocation.getArgument(0);
        Boolean defaultValue = invocation.getArgument(1);
        Boolean valueToReturn =
                sKeyValueMap.containsKey(key)
                        ? Boolean.parseBoolean(sKeyValueMap.get(key))
                        : defaultValue;
        logGetterCall(invocation, valueToReturn);
        return valueToReturn;
    }

    private static <T> void logGetterCall(InvocationOnMock invocation, T valueToReturn) {
        Log.v(
                TAG,
                invocation.getMethod().getName()
                        + "(\""
                        + invocation.getArgument(0)
                        + "\") will return "
                        + valueToReturn);
    }
}
