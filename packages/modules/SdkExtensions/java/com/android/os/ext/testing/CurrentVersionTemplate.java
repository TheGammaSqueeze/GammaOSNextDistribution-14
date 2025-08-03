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
package com.android.os.ext.testing;

import java.util.Set;

/**
 * This class is intended to serve as a single place to define the current SDK extension versions to
 * expect / allow in tests.
 */
public class CurrentVersion {

    /**
     * The latest train's version. Note that the value is inserted by the build pre-processing the
     * source code.
     */
    public static final int CURRENT_TRAIN_VERSION = {INSERTED_BY_BUILD};

    /** The version R shipped with (0) */
    public static final int R_BASE_VERSION = 0;

    /** The version S shipped with (1) */
    public static final int S_BASE_VERSION = 1;

    /** The version T shipped with (3) */
    public static final int T_BASE_VERSION = 3;

    /** The current platform's version */
    public static final int CURRENT_BASE_VERSION = CURRENT_TRAIN_VERSION;

    /**
     * The current SDK Extension versions to expect / allow in CTS.
     *
     * <p>Note: This construct exists because CTS is currently versioned together with the dessert
     * versions, and not with the module itself. For example, Android R shipped with extension
     * version 0, but it is allowed to preload new mainline trains with a higher extension version.
     * When a new extension version is defined, this Set must therefore be extended to include the
     * new version.
     */
    public static final Set<Integer> ALLOWED_VERSIONS_CTS =
            CURRENT_BASE_VERSION == CURRENT_TRAIN_VERSION
                    ? Set.of(CURRENT_BASE_VERSION)
                    : Set.of(CURRENT_BASE_VERSION, CURRENT_TRAIN_VERSION);
}
