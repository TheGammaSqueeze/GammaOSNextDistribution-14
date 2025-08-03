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

#pragma once

#include <string>
#include <vector>

namespace android {
namespace opengl {

class EmuglBackendList {
public:
    // Parse the content of |execDir|/<lib>/ for Emugl backends.
    // |programBitness| can be 0 (autodetect), 32 or 64, and determines
    // the value of <lib> which will be 'lib' for 32-bit systems,
    // and 'lib64' for 64-bit ones.
    EmuglBackendList(const char* execDir, int programBitness);

    // Create a backend list with a fixed list of names
    EmuglBackendList(int programBitness, const std::vector<std::string>& names);

    // Return the name of the default Emugl backend.
    const std::string& defaultName() const { return mDefaultName; }

    // Return the list of installed Emugl backends.
    const std::vector<std::string>& names() const { return mNames; }

    // Returns true if |name| is part of names().
    bool contains(const char* name) const;

    // Convert the name of an Emugl backend into the path of the
    // corresponding sub-directory, if it exits, or NULL otherwise.
    std::string getLibDirPath(const char* name);

    // List of supported Emugl shared libraries.
    enum Library {
        LIBRARY_NONE,
        LIBRARY_EGL,
        LIBRARY_GLESv1,
        LIBRARY_GLESv2,
        LIBRARY_GLES12TR
    };

    // Probe the library directory for Emugl backend |name| and return
    // the path of one of the EmuGL shared libraries for it. The result
    // will be empty if there is no such library.
    // |library| is a library type. On success, return true and sets
    // |*libPath| to the library's path value. On failure, return false.
    bool getBackendLibPath(const char* name, Library library,
                           std::string* libPath);

private:
    std::string mDefaultName;
    std::vector<std::string> mNames;
    int mProgramBitness;
    std::string mExecDir;
};

}  // namespace opengl
}  // namespace android
