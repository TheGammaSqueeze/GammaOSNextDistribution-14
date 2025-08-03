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
 * This class contains constants that are used both by Cobalt classes and PhFlags (which in turn is
 * used by tests), it cannot contain any external dependency (otherwise it would break the test
 * project - we want to keep it simple).
 */
public final class CobaltConstants {

    /** The default API key. */
    public static final String DEFAULT_API_KEY = "cobalt-default-api-key";

    /**
     * The default release stage is GA to ensure the low privacy reports aren't collected if a
     * release stage isn't set.
     */
    public static final String DEFAULT_RELEASE_STAGE = "GA";

    private CobaltConstants() {
        throw new UnsupportedOperationException("Contains only constants");
    }
}
