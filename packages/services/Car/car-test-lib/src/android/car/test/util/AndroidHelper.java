/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.car.test.util;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.NonNull;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import java.util.Iterator;
import java.util.Set;

/**
 * Provides helper methods for core Android classes.
 */
public final class AndroidHelper {

    /**
     * Gets a string representing the intent, including its extras.
     */
    public static String toString(@NonNull Intent intent) {
        return intent.toString() + " Extras: " + toString(intent.getExtras());
    }

    /**
     * Gets a string representing the bundle, including its key-value pairs.
     */
    public static String toString(@NonNull Bundle bundle) {
        Set<String> keySet = bundle.keySet();
        StringBuilder string = new StringBuilder("Bundle[");
        if (keySet.isEmpty()) {
            string.append("empty");
        } else {
            string.append(keySet.size()).append(" keys: ");
            Iterator<String> iterator = keySet.iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                string.append(key).append('=').append(bundle.get(key));
                if (iterator.hasNext()) {
                    string.append(", ");
                }
            }
        }
        return string.append(']').toString();
    }

    /**
     * Asserts that the {@code intentFilter} has the {@code action}.
     */
    public static void assertFilterHasActions(IntentFilter intentFilter, String... actions) {
        for (String action : actions) {
            assertWithMessage("Filter %s has action %s", intentFilter, action)
                    .that(intentFilter.hasAction(action)).isTrue();
        }
    }

    /**
     * Asserts that the {@code intentFilter} has the {@code dataScheme}.
     */
    public static void assertFilterHasDataScheme(IntentFilter intentFilter, String dataScheme) {
        assertWithMessage("Filter %s has data scheme %s", intentFilter, dataScheme)
                .that(intentFilter.hasDataScheme(dataScheme)).isTrue();
    }

    private AndroidHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
