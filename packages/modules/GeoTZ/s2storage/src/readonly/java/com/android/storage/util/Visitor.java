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

package com.android.storage.util;

/** A base class for visitor classes to extend. */
public interface Visitor {

    /** Called before any other visitor method. */
    default void begin() throws VisitException {
    }

    /** Called after all other visitor methods. Will be called even in the event of an exception */
    default void end() throws VisitException {
    }

    /** An exception that a visitor can throw to terminate early. */
    final class VisitException extends Exception {

        public VisitException(String s) {
            super(s);
        }

        public VisitException(String s, Throwable throwable) {
            super(s, throwable);
        }

        public VisitException(Throwable throwable) {
            super(throwable);
        }
    }
}
