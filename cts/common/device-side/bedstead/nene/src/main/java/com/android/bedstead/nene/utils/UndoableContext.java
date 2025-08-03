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

package com.android.bedstead.nene.utils;

/**
 * Generic {@link AutoCloseable} used to undo changes in device state.
 *
 * <p>Typical usage:
 * <pre>
 * try (UndoableContext v = TestApis.packages().setVerifyAdbInstalls(false)) {
 *    // The result of setVerifyAdvInstalls(false) is valid here
 * }
 * // it is not valid here
 * </pre>
 */
public class UndoableContext implements AutoCloseable {

    public static final UndoableContext EMPTY = new UndoableContext(() -> {});

    private final Runnable mUndo;

    public UndoableContext(Runnable undo) {
        mUndo = undo;
    }

    @Override
    public void close() {
        mUndo.run();
    }
}
