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

package com.android.telephony.sats2range.write;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * An iterator that can have elements pushed back onto it. {@link #remove()} is not supported.
 *
 * @param <E> The type of the element returned by this iterator
 */
public final class PushBackIterator<E> implements Iterator<E> {

    private final ArrayList<E> mPushBackStack = new ArrayList<>();

    private final Iterator<E> mIterator;

    public PushBackIterator(Iterator<E> iterator) {
        mIterator = iterator;
    }

    @Override
    public boolean hasNext() {
        return !mPushBackStack.isEmpty() || mIterator.hasNext();
    }

    @Override
    public E next() {
        if (!mPushBackStack.isEmpty()) {
            return mPushBackStack.remove(mPushBackStack.size() - 1);
        }
        return mIterator.next();
    }

    /**
     * Pushes the element to the front of the iterator again.
     */
    public void pushBack(E element) {
        mPushBackStack.add(element);
    }
}
