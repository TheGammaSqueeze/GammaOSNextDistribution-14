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

/** [ChronicleAnalyticsClient] represents the chronicle clients for logging purposes */
enum class ChronicleAnalyticsClient {
  /* [NOOP] is used by clients which do not have a real implementation but needed as a NoOpModule */
  NOOP,
  ECHO, // copybara:strip(Remove things specific to PCC)
  PECAN, // copybara:strip(Remove things specific to PCC)
  AUTOFILL, // copybara:strip(Remove things specific to PCC)
  CONTENTCAPTURE,
  OVERVIEW, // copybara:strip(Remove things specific to PCC)
  SAFECOMMS, // copybara:strip(Remove things specific to PCC)
  SIMPLESTORAGE, // copybara:strip(Remove things specific to PCC)
  TEXTCLASSIFIER, // copybara:strip(Remove things specific to PCC)
  ARCS, // copybara:strip(Remove things specific to PCC)
  TEST,
  INTERESTSMODEL, // copybara:strip(Remove things specific to PCC)
  CONTENTSUGGESTIONS, // copybara:strip(Remove things specific to PCC)
  LIVETRANSLATE, // copybara:strip(Remove things specific to PCC)
  NOWPLAYING, // copybara:strip(Remove things specific to PCC)
  NEXTCONVERSATION, // copybara:strip(Remove things specific to PCC)
  SMARTSELECT, // copybara:strip(Remove things specific to PCC)
  SEARCH, // copybara:strip(Remove things specific to PCC)
  PEOPLE_SERVICE_PLATFORM, // copybara:strip(Remove things specific to PCC)
  BLOBSTORE,
}
