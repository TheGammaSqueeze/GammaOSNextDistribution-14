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

#include "jdwpargs.h"

#include <algorithm>
#include <sstream>

#include "base/logging.h"  // For VLOG.

namespace adbconnection {

JdwpArgs::JdwpArgs(const std::string& opts) {
  std::stringstream ss(opts);

  // Split on ',' character
  while (!ss.eof()) {
    std::string w;
    getline(ss, w, ',');

    // Trim spaces
    w.erase(std::remove_if(w.begin(), w.end(), ::isspace), w.end());

    // Extract key=value
    auto pos = w.find('=');

    // Check for bad format such as no '=' or '=' at either extremity
    if (pos == std::string::npos || w.back() == '=' || w.front() == '=') {
      VLOG(jdwp) << "Skipping jdwp parameters '" << opts << "', token='" << w << "'";
      continue;
    }

    // Set
    std::string key = w.substr(0, pos);
    std::string value = w.substr(pos + 1);
    put(key, value);
    VLOG(jdwp) << "Found jdwp parameters '" << key << "'='" << value << "'";
  }
}

void JdwpArgs::put(const std::string& key, const std::string& value) {
  if (store.find(key) == store.end()) {
    keys.emplace_back(key);
  }

  store[key] = value;
}

std::string JdwpArgs::join() {
  std::string opts;
  for (const auto& key : keys) {
    opts += key + "=" + store[key] + ",";
  }

  // Remove the trailing comma if there is one
  if (opts.length() >= 2) {
    opts = opts.substr(0, opts.length() - 1);
  }

  return opts;
}
}  // namespace adbconnection
