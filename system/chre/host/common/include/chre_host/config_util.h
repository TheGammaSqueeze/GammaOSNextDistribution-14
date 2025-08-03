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

#ifndef CHRE_HOST_CONFIG_UTIL_H_
#define CHRE_HOST_CONFIG_UTIL_H_

#include <functional>
#include <vector>

namespace android {
namespace chre {

/**
 * Gets the preloaded nanoapps from the config file at path: configFilePath.
 *
 * @param configFilePath        the file path of the config file on the device
 * @param outDirectory          (out) the directory that contains the nanoapps
 *                              on the device
 * @param outNanoapps           (out) the list of nanoapps in the directory
 * @return bool                 success
 */
bool getPreloadedNanoappsFromConfigFile(const std::string &configFilePath,
                                        std::string &outDirectory,
                                        std::vector<std::string> &outNanoapps);

}  // namespace chre
}  // namespace android

#endif  // CHRE_HOST_CONFIG_UTIL_H_
