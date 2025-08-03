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

import androidx.annotation.NonNull;

import com.google.cobalt.ReleaseStage;

import java.util.Objects;

/** Static data and functions related to Cobalt release stages. */
public final class CobaltReleaseStages {

    /** Parses a release stage string into a {@link ReleaseStage}. */
    static ReleaseStage getReleaseStage(@NonNull String releaseStage)
            throws CobaltInitializationException {
        Objects.requireNonNull(releaseStage);
        if (releaseStage.equals("DEBUG")) {
            return ReleaseStage.DEBUG;
        } else if (releaseStage.equals("FISHFOOD")) {
            return ReleaseStage.FISHFOOD;
        } else if (releaseStage.equals("DOGFOOD")) {
            return ReleaseStage.DOGFOOD;
        } else if (releaseStage.equals("OPEN_BETA")) {
            return ReleaseStage.OPEN_BETA;
        } else if (releaseStage.equals("GA")) {
            return ReleaseStage.GA;
        }

        throw new CobaltInitializationException("Unknown release stage: " + releaseStage);
    }

    private CobaltReleaseStages() {
        throw new UnsupportedOperationException("Contains only static members");
    }
}
