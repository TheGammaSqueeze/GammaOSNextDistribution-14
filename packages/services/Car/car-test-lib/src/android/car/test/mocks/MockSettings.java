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
package android.car.test.mocks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.car.test.mocks.AbstractExtendedMockitoTestCase.CustomMockitoSessionBuilder;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.util.Preconditions;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import android.util.ArrayMap;

// TODO (b/156033195): Clean settings API. For example, don't mock xyzForUser() methods (as
// they should not be used due to mainline) and explicitly use a MockSettings per user or
// something like that (to make sure the code being test is passing the writer userId to
// Context.createContextAsUser())
public final class MockSettings {

    private static final String TAG = MockSettings.class.getSimpleName();
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static final int INVALID_DEFAULT_INDEX = -1;

    private final ArrayMap<String, Object> mSettingsMapping = new ArrayMap<>();

    public MockSettings(CustomMockitoSessionBuilder sessionBuilder) {
        // TODO (b/156033195): change from mock to spy - or don't use mock at all
        sessionBuilder
                .mockStatic(Settings.Global.class)
                .mockStatic(Settings.System.class)
                .mockStatic(Settings.Secure.class);
        sessionBuilder.setSessionCallback(() -> setExpectations());
    }

    private void setExpectations() {
        Answer<Object> insertObjectAnswer =
                invocation -> insertObjectFromInvocation(invocation, 1, 2);
        Answer<Integer> getIntAnswer = invocation ->
                getAnswer(invocation, Integer.class, 1, 2);
        Answer<String> getStringAnswer = invocation ->
                getAnswer(invocation, String.class, 1, INVALID_DEFAULT_INDEX);

        when(Settings.Global.putInt(any(), any(), anyInt())).thenAnswer(insertObjectAnswer);

        when(Settings.Global.getInt(any(), any(), anyInt())).thenAnswer(getIntAnswer);

        when(Settings.System.putInt(any(), any(), anyInt())).thenAnswer(insertObjectAnswer);

        when(Settings.System.getInt(any(), any(), anyInt())).thenAnswer(getIntAnswer);

        when(Settings.Secure.putIntForUser(any(), any(), anyInt(), anyInt()))
                .thenAnswer(insertObjectAnswer);

        when(Settings.Secure.getIntForUser(any(), any(), anyInt(), anyInt()))
                .thenAnswer(getIntAnswer);

        when(Settings.Secure.getInt(any(), any(), anyInt())).thenAnswer(getIntAnswer);

        when(Settings.Secure.putStringForUser(any(), anyString(), anyString(), anyInt()))
                .thenAnswer(insertObjectAnswer);

        when(Settings.Global.putString(any(), any(), any()))
                .thenAnswer(insertObjectAnswer);

        when(Settings.Global.getString(any(), any())).thenAnswer(getStringAnswer);

        when(Settings.System.putIntForUser(any(), any(), anyInt(), anyInt()))
                .thenAnswer(insertObjectAnswer);

        when(Settings.System.getIntForUser(any(), any(), anyInt(), anyInt()))
                .thenAnswer(getIntAnswer);

        when(Settings.System.putStringForUser(any(), any(), anyString(), anyInt()))
                .thenAnswer(insertObjectAnswer);

        when(Settings.System.putString(any(), any(), any()))
                .thenAnswer(insertObjectAnswer);
    }

    private Object insertObjectFromInvocation(InvocationOnMock invocation, int keyIndex,
            int valueIndex) {
        String key = (String) invocation.getArguments()[keyIndex];
        Object value = invocation.getArguments()[valueIndex];
        insertObject(key, value);
        // NOTE:  the return value is not really used but it's needed so it can be used as a lambda
        // for Answer<REAL_TYPE>. In fact, returning `value` would cause tests to fail due to
        // invalid casts at runtime.
        return null;
    }

    private void insertObject(String key, Object value) {
        if (VERBOSE) {
            Log.v(TAG, "Inserting Setting " + key + ": " + value);
        }
        mSettingsMapping.put(key, value);
    }

    private <T> T getAnswer(InvocationOnMock invocation, Class<T> clazz, int keyIndex,
            int defaultValueIndex) {
        String key = (String) invocation.getArguments()[keyIndex];
        T defaultValue = null;
        if (defaultValueIndex > INVALID_DEFAULT_INDEX) {
            defaultValue = safeCast(invocation.getArguments()[defaultValueIndex], clazz);
        }
        return get(key, defaultValue, clazz);
    }

    @Nullable
    private <T> T get(String key, T defaultValue, Class<T> clazz) {
        if (VERBOSE) {
            Log.v(TAG, "get(): key=" + key + ", default=" + defaultValue + ", class=" + clazz);
        }
        Object value = mSettingsMapping.get(key);
        if (value == null) {
            if (VERBOSE) {
                Log.v(TAG, "not found");
            }
            return defaultValue;
        }

        if (VERBOSE) {
            Log.v(TAG, "returning " + value);
        }
        return safeCast(value, clazz);
    }

    private static <T> T safeCast(Object value, Class<T> clazz) {
        if (value == null) {
            return null;
        }
        Preconditions.checkArgument(value.getClass() == clazz,
                "Setting value has class %s but requires class %s",
                value.getClass(), clazz);
        return clazz.cast(value);
    }

    /**
     * Adds key-value(int) pair in mocked Settings.Global and Settings.Secure
     */
    public void putInt(String key, int value) {
        insertObject(key, value);
    }

    /**
     * Adds key-value(String) pair in mocked Settings.Global and Settings.Secure
     */
    public void putString(String key, String value) {
        insertObject(key, value);
    }

    public String getString(String key) {
        return get(key, null, String.class);
    }

    public int getInt(String key) {
        return get(key, null, Integer.class);
    }

    public void assertDoesNotContainsKey(String key) {
        if (mSettingsMapping.containsKey(key)) {
            throw new AssertionError("Should not have key " + key + ", but has: "
                    + mSettingsMapping.get(key));
        }
    }
}
