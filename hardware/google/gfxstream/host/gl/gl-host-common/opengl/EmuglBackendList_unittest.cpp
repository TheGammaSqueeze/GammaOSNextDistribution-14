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

#include "android/opengl/EmuglBackendList.h"

#include "android/base/files/PathUtils.h"
#include "android/base/testing/TestSystem.h"
#include "android/base/testing/TestTempDir.h"

#include <gtest/gtest.h>

namespace android {
namespace opengl {

#define ARRAYLEN(x)  (sizeof(x)/sizeof(x[0]))

using android::base::pj;
using android::base::System;
using android::base::TestTempDir;
using android::base::TestSystem;

static std::string makeLibSubPath(const char* name) {
    return pj("foo", System::kLibSubDir, name);
}

static void makeLibSubDir(TestTempDir* dir, const char* name) {
    dir->makeSubDir(makeLibSubPath(name).c_str());
}

static void makeLibSubFile(TestTempDir* dir, const char* name) {
    dir->makeSubFile(makeLibSubPath(name).c_str());
}

TEST(EmuglBackendList, init) {
    TestSystem testSys("foo", System::kProgramBitness, "/");
    TestTempDir* myDir = testSys.getTempRoot();

    myDir->makeSubDir("foo");

    makeLibSubDir(myDir, "");
    makeLibSubDir(myDir, "gles_first");
    makeLibSubFile(myDir, "gles_second");  // should be ignored (file).
    makeLibSubDir(myDir, "gles_");  // should be ignored (no name).
    makeLibSubDir(myDir, "gles_fourth");
    makeLibSubDir(myDir, "gles_fifth");

    EmuglBackendList list("foo", System::kProgramBitness);

    // NOTE: Must appear in alphabetical order
    const char* const kExpected[] = {
        "fifth", "first", "fourth",
    };
    const size_t kExpectedLen = ARRAYLEN(kExpected);

    const std::vector<std::string>& names = list.names();
    EXPECT_EQ(kExpectedLen, names.size());
    for (size_t n = 0; n < kExpectedLen; ++n) {
        EXPECT_STREQ(kExpected[n], names[n].c_str()) << "#" << n;
        EXPECT_TRUE(list.contains(kExpected[n]));
    }
}

TEST(EmuglBackendList, getBackendLibPath) {
    TestSystem testSys("foo", System::kProgramBitness, "/");
    TestTempDir* myDir = testSys.getTempRoot();

    myDir->makeSubDir("foo");

    makeLibSubDir(myDir, "");
    makeLibSubDir(myDir, "gles_bar");

    static const struct {
        EmuglBackendList::Library library;
        const char* libName;
    } kData[] = {
#ifdef _WIN32
        { EmuglBackendList::LIBRARY_EGL, "libEGL.dll" },
        { EmuglBackendList::LIBRARY_GLESv1, "libGLES_CM.dll" },
        { EmuglBackendList::LIBRARY_GLESv2, "libGLESv2.dll" },
#elif defined(__APPLE__)
        { EmuglBackendList::LIBRARY_EGL, "libEGL.dylib" },
        { EmuglBackendList::LIBRARY_GLESv1, "libGLES_CM.dylib" },
        { EmuglBackendList::LIBRARY_GLESv2, "libGLESv2.dylib" },
#else
        { EmuglBackendList::LIBRARY_EGL, "libEGL.so" },
        { EmuglBackendList::LIBRARY_GLESv1, "libGLES_CM.so" },
        { EmuglBackendList::LIBRARY_GLESv2, "libGLESv2.so" },
#endif
    };
    const size_t kDataLen = ARRAYLEN(kData);

    for (size_t n = 0; n < kDataLen; ++n) {
        std::string file =
            pj("gles_bar", kData[n].libName);
        makeLibSubFile(myDir, file.c_str());
    }

    auto sysdir = pj("/", "foo");
    EmuglBackendList list(sysdir.c_str(), System::kProgramBitness);
    const std::vector<std::string>& names = list.names();

    EXPECT_EQ(1U, names.size());
    EXPECT_STREQ("bar", names[0].c_str());

    for (size_t n = 0; n < kDataLen; ++n) {
        std::string expected =
            pj("/", "foo", System::kLibSubDir, "gles_bar", kData[n].libName);
        std::string libdir;
        EXPECT_TRUE(list.getBackendLibPath("bar", kData[n].library, &libdir));
        EXPECT_TRUE(list.contains("bar"));
        EXPECT_FALSE(list.contains("foo"));
        EXPECT_STREQ(expected.c_str(), libdir.c_str());
    }
}

TEST(EmuglBackend, defaultName) {
    EmuglBackendList list("foo", 0);
    EXPECT_STREQ("auto", list.defaultName().c_str());
}

}  // namespace opengl
}  // namespace android


