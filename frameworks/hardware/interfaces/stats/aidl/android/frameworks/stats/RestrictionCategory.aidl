//
// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package android.frameworks.stats;

/*
 * Mimics packages/modules/StatsD/lib/libstatssocket/include/stats_annotations.h
 * These ids must stay consistent with those in stats_annotations.h
 */
@VintfStability
@Backing(type="int")
enum RestrictionCategory {
    UNKNOWN = 0,

    /**
     * Restriction category for atoms about diagnostics.
     */
    DIAGNOSTIC = 1,

    /**
     * Restriction category for atoms about system intelligence.
     */
    SYSTEM_INTELLIGENCE = 2,

    /**
     * Restriction category for atoms about authentication.
     */
    AUTHENTICATION = 3,

    /**
     * Restriction category for atoms about fraud and abuse.
     */
    FRAUD_AND_ABUSE = 4,
}
