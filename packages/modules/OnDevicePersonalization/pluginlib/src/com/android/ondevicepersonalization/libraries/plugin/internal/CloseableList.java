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

package com.android.ondevicepersonalization.libraries.plugin.internal;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * List of closeable elements, that can be opened in try-with-resources.
 *
 * @param <E> A type that implements {@link Closeable}
 */
public class CloseableList<E extends Closeable> implements Closeable {
    private final List<E> mCloseables;

    public CloseableList(List<E> fileList) {
        this.mCloseables = fileList;
    }

    /** Returns the closeable elements. */
    public List<E> closeables() {
        return mCloseables;
    }

    @Override
    public void close() throws IOException {
        for (E closeable : mCloseables) {
            closeable.close();
        }
    }
}
