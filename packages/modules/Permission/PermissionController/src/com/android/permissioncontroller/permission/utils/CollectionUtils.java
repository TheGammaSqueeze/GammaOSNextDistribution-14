/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.permissioncontroller.permission.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for dealing with {@link java.util.Collection}s.
 */
public final class CollectionUtils {

    private CollectionUtils() {}

    /**
     * Check whether a collection is {@code null} or empty.
     *
     * @param collection the collection to check
     *
     * @return whether the collection is {@code null} or empty
     */
    public static boolean isEmpty(@Nullable Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Return the first element of a list, or {@code null} if the list is {@code null} or empty.
     *
     * @param <T> the class of the elements of the list
     * @param list the list to get the first element
     *
     * @return the first element of the list, or {@code null} if the list is {@code null} or empty
     */
    @Nullable
    public static <T> T firstOrNull(@Nullable List<T> list) {
        return !isEmpty(list) ? list.get(0) : null;
    }

    /**
     * Return a singleton list containing the element, or an empty list if the element is
     * {@code null}.
     *
     * @param <T> the class of the element
     * @param element the element to be put into the list
     *
     * @return a singleton list containing the element, or an empty list if the element is
     *         {@code null}.
     */
    @NonNull
    public static <T> List<T> singletonOrEmpty(@Nullable T element) {
        return element != null ? Collections.singletonList(element) : Collections.emptyList();
    }

    /**
     * Returns whether a byte array is contained within a {@link Set} of byte arrays. Equality is
     * not compared by reference, but by comparing the elements contained in the arrays.
     *
     * @param byteArrays a {@link Set} of byte arrays that will be searched
     * @param otherByteArray byte array to be searched
     * @return {@code true} if {@code byteArrays} contains a byte array with identical elements as
     *         {@code otherByteArray}.
     */
    public static boolean contains(@NonNull Set<byte[]> byteArrays,
            @NonNull byte[] otherByteArray) {
        for (byte[] byteArray : byteArrays) {
            if (Arrays.equals(byteArray, otherByteArray)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a {@link Set} of byte arrays is contained within a {@link Set} of byte arrays
     * as a subset. Equality for arrays is not compared by reference, but by comparing the elements
     * contained in the arrays.
     *
     * @param byteArrays a {@link Set} of byte arrays which will be checked as a superset
     * @param otherByteArrays a {@link Set} of byte arrays which be checked as a subset
     * @return {@code true} if {@code byteArrays} contains all the arrays in {@code
     *     otherByteArrays}.
     */
    public static boolean containsSubset(
            @NonNull Set<byte[]> byteArrays, @NonNull Set<byte[]> otherByteArrays) {
        for (byte[] byteArray : otherByteArrays) {
            if (!contains(byteArrays, byteArray)) {
                return false;
            }
        }
        return true;
    }
}
