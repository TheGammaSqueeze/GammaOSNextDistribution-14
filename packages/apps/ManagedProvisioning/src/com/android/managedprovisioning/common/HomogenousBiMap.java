/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.managedprovisioning.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A bi-directional map where the key and value are of the same type.
 *
 * @param <T> the type of the elements in this map
 */
public final class HomogenousBiMap<T> {
    private final Map<T, T> mForwardMap = new HashMap<>();
    private final Map<T, T> mReverseMap = new HashMap<>();

    /**
     * Puts an element to the map.
     *
     * @see #getForwardMap()
     * @see #getReverseMap()
     */
    public void put(T key, T value) {
        mForwardMap.put(key, value);
        mReverseMap.put(value, key);
    }

    /**
     * Returns a copy of the forward map. After {@link #put(Object, Object)} is called, the {@code
     * key} is in the key set of this map, and the {@code value} is the value list of this map.
     */
    public Map<T, T> getForwardMap() {
        return new HashMap<>(mForwardMap);
    }

    /**
     * Returns a copy of the reverse map. After {@link #put(Object, Object)} is called, the {@code
     * value} is the key set of this map, and the {@code key} is the value list of this map.
     */
    public Map<T, T> getReverseMap() {
        return new HashMap<>(mReverseMap);
    }

    /**
     * Clears the map.
     */
    public void clear() {
        mForwardMap.clear();
        mReverseMap.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HomogenousBiMap)) return false;
        HomogenousBiMap<?> that = (HomogenousBiMap<?>) o;
        return Objects.equals(mForwardMap, that.mForwardMap) &&
                Objects.equals(mReverseMap, that.mReverseMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mForwardMap, mReverseMap);
    }
}
