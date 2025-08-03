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
package com.android.adservices.cobalt;

/**
 * An exception which indicates AdServices experienced an issue while initializing Cobalt and the
 * library isn't safe to use.
 */
public final class CobaltInitializationException extends Exception {

    CobaltInitializationException() {
        super();
    }

    CobaltInitializationException(String message) {
        super(message);
    }

    CobaltInitializationException(String message, Throwable cause) {
        super(message, cause);
    }

    CobaltInitializationException(Throwable cause) {
        super(cause);
    }
}
