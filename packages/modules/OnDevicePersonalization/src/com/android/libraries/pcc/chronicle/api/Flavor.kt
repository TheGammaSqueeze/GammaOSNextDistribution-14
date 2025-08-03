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

package com.android.libraries.pcc.chronicle.api

import com.android.libraries.pcc.chronicle.api.Flavor.ASI_DEBUG
import com.android.libraries.pcc.chronicle.api.Flavor.ASI_DOG_FOOD
import com.android.libraries.pcc.chronicle.api.Flavor.ASI_DROID_FOOD
import com.android.libraries.pcc.chronicle.api.Flavor.ASI_FISH_FOOD
import com.android.libraries.pcc.chronicle.api.Flavor.ASI_PROD

/** copybara:intracomment_strip_begin
 * AiAi external name is Android System Intelligence (ASI)
 * Astrea external name is Private Compute Services (PCS)
 * ODAD external name is Google Play Protect Services (GPPS)
 * copybara:intracomment_strip_end
 */

/** Used in policies that should target all available ASI flavors. */
val ASI_ALL_FLAVORS =
  arrayOf(
    ASI_DEBUG,
    ASI_FISH_FOOD,
    ASI_DOG_FOOD,
    ASI_DROID_FOOD,
    ASI_PROD, // includes SYSIMG and PLAYSTORE
  )

/** Build flavor where this policy variant will be shipped */
// We don't reuse BuildGuard flavor because this set should include all PCC flavors and should be
// independent of ASI deps as much as possible.
enum class Flavor {
  /** [ASI_DEBUG] includes TESTONLY */
  ASI_DEBUG,
  ASI_FISH_FOOD,
  ASI_DOG_FOOD,
  ASI_DROID_FOOD,
  /** [ASI_PROD] includes SYSIMG and PLAYSTORE */
  ASI_PROD,
  /** For local development on real devices. GPPS adjusts policy parameters during dev, so it
   * is useful for them to have a distinct policy from integration testing. */
  GPPS_DEBUG,
  /** For usage in integration tests, not used on real devices. */
  GPPS_INTEGRATION_TEST_ONLY,
  /** Fish food also known as Team food for GPPS */
  GPPS_FISH_FOOD,
  GPPS_PROD,
  PCS_INTERNAL,
}
