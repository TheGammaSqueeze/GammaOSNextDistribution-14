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

#include <nos/feature.h>

namespace nos {

bool has_feature(NuggetClientInterface& nug, enum feature_support_app_id app_id,
                 uint32_t feature) {
  uint32_t feature_id = (app_id << TA_OFFSET) | (feature & FEATURE_MASK);

  std::vector<uint8_t> req(sizeof(feature_id));
  memcpy(req.data(), &feature_id, sizeof(feature_id));

  std::vector<uint8_t> resp;
  resp.reserve(sizeof(uint8_t));

  uint32_t rv =
      nug.CallApp(APP_ID_NUGGET, NUGGET_PARAM_GET_FEATURE_SUPPORT, req, &resp);
  if (rv != APP_SUCCESS) {
    return false;
  }

  if (resp.size() < 1) {
    return false;  // I guess?
  }

  return !!resp[0];
}

}  // namespace nos
