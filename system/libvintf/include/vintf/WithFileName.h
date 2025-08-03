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

#include <string>

namespace android::vintf {

// WithFileName is a class that attaches a name to an object.
// The name is often the file name or path of the source file where the object
// is deserialized from. The name is used for informational and debugging
// purposes. It is not retained when the object is serialized.
// It is not meant to be used as an identifier of the object:
// - there may be duplicated names;
// - name may be empty.
struct WithFileName {
    WithFileName() = default;
    WithFileName(const std::string& fileName) : mFileName(fileName) {}
    const std::string& fileName() const { return mFileName; }
    void setFileName(std::string&& fileName) { mFileName = std::move(fileName); }
    void setFileName(const std::string& fileName) { mFileName = fileName; }

   private:
    std::string mFileName;
};

}  // namespace android::vintf
