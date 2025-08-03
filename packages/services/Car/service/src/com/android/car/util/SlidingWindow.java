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
package com.android.car.util;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * This class keeps track of a limited fixed number of sample data points, correctly removing
 * older samples as new ones are added, and it allows inspecting the samples, as well as
 * easily answering N out of M questions.
 *
 * @param <T> data to iterate
 */
public class SlidingWindow<T> implements Iterable<T> {
    private final ArrayDeque<T> mElements;
    private final int mMaxSize;

    /** TODO: add javadoc */
    public SlidingWindow(int size) {
        mMaxSize = size;
        mElements = new ArrayDeque<>(mMaxSize);
    }

    /** TODO: add javadoc */
    public void add(T sample) {
        if (mElements.size() == mMaxSize) {
            mElements.removeFirst();
        }
        mElements.addLast(sample);
    }

    /** TODO: add javadoc */
    public void addAll(Iterable<T> elements) {
        elements.forEach(this::add);
    }

    @Override
    public Iterator<T> iterator() {
        return mElements.iterator();
    }

    /** TODO: add javadoc */
    public Stream<T> stream() {
        return mElements.stream();
    }

    /** TODO: add javadoc */
    public int size() {
        return mElements.size();
    }

    /** TODO: add javadoc */
    public int count(Predicate<T> predicate) {
        return (int) stream().filter(predicate).count();
    }
}
