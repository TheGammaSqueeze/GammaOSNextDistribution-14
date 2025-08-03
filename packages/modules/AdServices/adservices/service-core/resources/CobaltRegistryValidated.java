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
 * A class whose presence indicates the Cobalt registry was validated at build time.
 *
 * <p>This source file isn't included in `service-core` unless the build rule that validates the
 * Cobalt registry completes successfully.
 */
final class CobaltRegistryValidated {
    /**
     * Value indicating the Cobalt binary proto registry was confirmed the same as the text proto
     * registry at build-time.
     *
     * <p>This value will be missing if the registry wasn't validated because the build will fail.
     */
    public static final boolean IS_REGISTRY_VALIDATED = true;

    private CobaltRegistryValidated() {}
}
