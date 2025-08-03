/*
 * Copyright (C) 2020 The Android Open Source Project
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

#pragma once

#include <map>
#include <string>

namespace android::vintf::details {

using Dirmap = std::map<std::string, std::string>;

// Assuming each arg is in the format <key><split><value>, turn into a map
// of key-value pairs. E.g. if split is '=', then
// {"foo=bar", "bar=baz"} -> map{foo: bar, bar: baz}
template <typename T>
std::map<std::string, std::string> splitArgs(const T& args, char split) {
    std::map<std::string, std::string> ret;
    for (const auto& arg : args) {
        auto pos = arg.find(split);
        auto key = arg.substr(0, pos);
        auto value = pos == std::string::npos ? std::string{} : arg.substr(pos + 1);
        ret[key] = value;
    }
    return ret;
}

// {"foo:bar", "bar:baz"} -> map{foo: bar, bar: baz}
template <typename T>
Dirmap getDirmap(const T& args) {
    return splitArgs(args, ':');
}

}  // namespace android::vintf::details
