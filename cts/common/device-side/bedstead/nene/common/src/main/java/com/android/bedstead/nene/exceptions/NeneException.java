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

package com.android.bedstead.nene.exceptions;

/**
 * Top level {@link Exception} thrown by Nene APIs.
 *
 * <p>This is a {@link RuntimeException} as, because Nene APIs are only to be used in tests, it is
 * expected that exceptional behaviour should just result in a failed test.
 */
public class NeneException extends RuntimeException {
    public NeneException(Throwable cause) {
        super(cause);
    }

    public NeneException(String message) {
        super(message);
    }

    public NeneException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
