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

package com.android.server.sdksandbox.verifier;

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * StringTrie is a string equality version of {@link com.android.tradefed.util.RegexTrie} It
 * supports wildcard matching, null being the wildcard key. Wildcard can be in leaf nodes and inner
 * nodes in the trie. This variant favors exact matching over wildcard matching. Matches by wildcard
 * will be added to the captures. This structure is not thread safe.
 *
 * @param <V> type of the values contained in the trie
 */
public class StringTrie<V> {
    private V mValue = null;
    private Map<String, StringTrie<V>> mChildren = new LinkedHashMap<String, StringTrie<V>>();

    /** Clears the trie */
    public void clear() {
        mValue = null;
        for (StringTrie<V> child : mChildren.values()) {
            child.clear();
        }
        mChildren.clear();
    }

    boolean containsKey(String... strings) {
        return retrieve(strings) != null;
    }

    @Nullable
    V recursivePut(V value, List<String> keys) {
        // Cases:
        // 1) keys is empty -- set our value
        // 2) keys is non-empty -- recurse downward, creating a child if necessary
        if (keys.isEmpty()) {
            V oldValue = mValue;
            mValue = value;
            return oldValue;
        } else {
            String curKey = keys.get(0);
            List<String> nextKeys = keys.subList(1, keys.size());

            // Create a new child to handle
            StringTrie<V> nextChild = mChildren.get(curKey);
            if (nextChild == null) {
                nextChild = new StringTrie<V>();
                mChildren.put(curKey, nextChild);
            }
            return nextChild.recursivePut(value, nextKeys);
        }
    }

    /**
     * Add an entry to the trie.
     *
     * @param value The value to set
     * @param keys The sequence of {@link Strings}s that must be sequentially matched to retrieve
     *     the associated {@code value}
     * @return previous value stored for the given key sequence or null if no value was stored
     */
    public @Nullable V put(V value, String... keys) {
        if (keys.length == 0) {
            throw new IllegalArgumentException("string list must be non-empty.");
        }
        List<String> kList = Arrays.asList(keys);
        return recursivePut(value, kList);
    }

    @Nullable
    V recursiveRetrieve(List<String> captures, List<String> strings) {
        // Cases:
        // 1) strings is empty -- return the value of this node
        // 2) strings is non-empty -- find the first child that matches, recurse downward
        if (strings.isEmpty()) {
            return mValue;

        } else {
            String curKey = strings.get(0);
            List<String> nextKeys = strings.subList(1, strings.size());

            // a more specific match takes precedence over a wildcard match
            if (mChildren.containsKey(curKey)) {
                V exactMatch = mChildren.get(curKey).recursiveRetrieve(captures, nextKeys);
                if (exactMatch != null) {
                    return exactMatch;
                }
            }

            V wildcardMatch = null;

            if (mChildren.containsKey(null)) {
                if (captures != null) {
                    captures.add(curKey);
                }
                if (!nextKeys.isEmpty()) {
                    // if there is a match after the wildcard continue down the tree
                    if (mChildren.get(null).mChildren.containsKey(nextKeys.get(0))) {
                        wildcardMatch = mChildren.get(null).recursiveRetrieve(captures, nextKeys);
                    } else {
                        // otherwise, keep matching wildcard
                        wildcardMatch = recursiveRetrieve(captures, nextKeys);
                    }
                } else {
                    // last key to be matched with wildcard
                    wildcardMatch = mChildren.get(null).recursiveRetrieve(captures, nextKeys);
                }
            }

            return wildcardMatch;
        }
    }

    /**
     * Fetch a value from the trie, by matching the provided sequence of {@link String}s to a
     * sequence stored in the trie.
     *
     * @param strings A sequence of {@link String}s to match
     * @return The associated value, or {@code null} if no value was found
     */
    public @Nullable V retrieve(@NonNull String... strings) {
        return retrieve(null, strings);
    }

    /**
     * Fetch a value from the trie, by matching the provided sequence of {@link String}s to a
     * sequence of {@link Strings}s stored in the trie. This version of the method also returns a
     * {@link List} of matched captures.
     *
     * <p>For each level, the matched strings will be stored. Note that {@code captures} will be
     * {@link List#clear()}ed before the retrieval begins. Also, if the retrieval fails after a
     * partial sequence of matches, {@code captures} will still reflect the capture groups from the
     * partial match.
     *
     * @param captures A {@code List<String>} through which wildcard matched keys will be returned.
     * @param strings A sequence of {@link String}s to match
     * @return The associated value, or {@code null} if no value was found
     */
    public @Nullable V retrieve(@Nullable List<String> captures, @NonNull String... strings) {
        if (strings == null || strings.length == 0) {
            throw new IllegalArgumentException("string list must be non-empty");
        }
        List<String> sList = Arrays.asList(strings);
        if (captures != null) {
            captures.clear();
        }
        return recursiveRetrieve(captures, sList);
    }

    @Override
    public String toString() {
        return String.format("{V: %s, C: %s}", mValue, mChildren);
    }
}
