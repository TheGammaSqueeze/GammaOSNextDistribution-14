/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <string.h>
#include <string>

namespace android {
namespace base {
bool StartsWith(const std::string& s, const char* prefix) {
    return strncmp(s.c_str(), prefix, strlen(prefix)) == 0;
}
}  // namespace base
}  // namespace android
