// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "host-common/opengl/EmuglBackendList.h"

#include "aemu/base/StringFormat.h"
#include "aemu/base/system/System.h"
#include "aemu/base/files/PathUtils.h"

#define DEBUG 0

#if DEBUG
#  include <stdio.h>
#  define D(...)  printf(__VA_ARGS__)
#else
#  define D(...)  ((void)0)
#endif

namespace android {
namespace opengl {

EmuglBackendList::EmuglBackendList(int programBitness,
                                   const std::vector<std::string>& names) :
        mDefaultName("auto"), mNames(names), mProgramBitness(programBitness) { }

bool EmuglBackendList::contains(const char* name) const {
    for (size_t n = 0; n < mNames.size(); ++n) {
        if (mNames[n] == name) {
            return true;
        }
    }
    return false;
}

std::string EmuglBackendList::getLibDirPath(const char* name) {
    // remove the "_indirect" suffix
    std::string suffix("_indirect");
    std::string nameNoSuffix(name);
    int nameNoSuffixLen = (int)nameNoSuffix.size() - (int)suffix.size();
    if (nameNoSuffixLen > 0 &&
        suffix == nameNoSuffix.c_str() + nameNoSuffixLen) {
        nameNoSuffix.erase(nameNoSuffixLen);
    }
    return android::base::pj({mExecDir, "lib64", std::string("gles_%s") + nameNoSuffix});
}

#ifdef _WIN32
static const char kLibSuffix[] = ".dll";
#elif defined(__APPLE__)
static const char kLibSuffix[] = ".dylib";
#else
static const char kLibSuffix[] = ".so";
#endif

bool EmuglBackendList::getBackendLibPath(const char* name,
                                         Library library,
                                         std::string* libPath) {

    const char* libraryName = NULL;
    if (library == LIBRARY_EGL) {
        libraryName = "EGL";
    } else if (library == LIBRARY_GLESv1) {
        libraryName = "GLES_CM";
    } else if (library == LIBRARY_GLESv2) {
        libraryName = "GLESv2";
    } else {
        // Should not happen.
        D("%s: Invalid library type: %d\n", __FUNCTION__, library);
        return false;
    }

    std::string path = android::base::pj({
            getLibDirPath(name), std::string("lib") + libraryName + kLibSuffix});

    *libPath = path;
    return true;
}

}  // namespace opengl
}  // namespace android
