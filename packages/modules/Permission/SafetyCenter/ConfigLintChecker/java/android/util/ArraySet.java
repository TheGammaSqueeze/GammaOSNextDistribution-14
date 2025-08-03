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

package android.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * A simple ArraySet implementation for the lint checker.
 *
 * <p>It's not array based, but for this simple purpose that doesn't matter.
 *
 * @param <E> the type of elements maintained by this set
 */
public final class ArraySet<E> extends HashSet<E> {
    public ArraySet() {
        super();
    }

    public ArraySet(Collection<? extends E> c) {
        super(c);
    }

    public ArraySet(E[] array) {
        super(Arrays.stream(array).toList());
    }
}
