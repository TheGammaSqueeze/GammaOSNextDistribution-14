/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "Regex.h"

#include <log/log.h>

namespace android {
namespace os {
namespace statsd {

using std::string;
using std::unique_ptr;

Regex::Regex(regex_t impl) : mImpl(std::move(impl)) {
}

Regex::~Regex() {
    regfree(&mImpl);
}

unique_ptr<Regex> Regex::create(const string& pattern) {
    regex_t impl;
    int status = regcomp(&impl, pattern.c_str(), REG_EXTENDED);

    if (status != 0) {  // Invalid regex pattern.
        // Calculate size of string needed to store error description.
        size_t errBufSize = regerror(status, &impl, nullptr, 0);

        // Get the error description.
        char errBuf[errBufSize];
        regerror(status, &impl, errBuf, errBufSize);

        ALOGE("regex_error: %s, pattern: %s", errBuf, pattern.c_str());
        regfree(&impl);
        return nullptr;
    } else if (impl.re_nsub > 0) {
        ALOGE("regex_error: subexpressions are not allowed, pattern: %s", pattern.c_str());
        regfree(&impl);
        return nullptr;
    } else {
        return std::make_unique<Regex>(impl);
    }
}

bool Regex::replace(string& str, const string& replacement) {
    regmatch_t match;
    int status = regexec(&mImpl, str.c_str(), 1 /* nmatch */, &match /* pmatch */, 0 /* flags */);

    if (status != 0 || match.rm_so == -1) {  // No match.
        return false;
    }
    str.replace(match.rm_so, match.rm_eo - match.rm_so, replacement);
    return true;
}

}  // namespace statsd
}  // namespace os
}  // namespace android
