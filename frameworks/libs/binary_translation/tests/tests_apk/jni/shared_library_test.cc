/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "gtest/gtest.h"

#include <android/api-level.h>
#include <dlfcn.h>
#include <link.h>

extern "C" {
int SharedFunction();
}

namespace {

void TestDlopenAndDlsym(const char* library_name, const char* symbol_name) {
  void* handle = dlopen(library_name, RTLD_NOW);
  ASSERT_TRUE(handle != nullptr) << dlerror();
  ASSERT_TRUE(dlsym(handle, symbol_name) != nullptr) << dlerror();
  ASSERT_EQ(dlclose(handle), 0);
}

void TestDlopenAndDlsymUnstable(const char* library_name,
                                std::initializer_list<const char*> symbol_names) {
  void* handle = dlopen(library_name, RTLD_NOW);
  ASSERT_TRUE(handle != nullptr) << dlerror();
  bool symbol_found = false;
  for (const auto& symbol_name : symbol_names) {
    if (dlsym(handle, symbol_name) != nullptr) {
      symbol_found = true;
      break;
    }
  }
  ASSERT_TRUE(symbol_found);
  ASSERT_EQ(dlclose(handle), 0);
}

struct DlIteratePhdrData {
  int n;
};

int DlIteratePhdrCallback(struct dl_phdr_info* /* info */, size_t /* size */, void* data) {
  DlIteratePhdrData* phdr_data = static_cast<DlIteratePhdrData*>(data);
  ++phdr_data->n;
  return 0;
}

}  // namespace

TEST(Shared, CallFunction) {
  EXPECT_TRUE(SharedFunction());
}

TEST(Shared, DlOpen) {
  TestDlopenAndDlsym("libberberis_ndk_tests_shared_lib.so", "SharedFunction");
}

TEST(Shared, DlOpenGreylistedLibrariesAndroidM) {
  TestDlopenAndDlsym(
      "libandroid_runtime.so",
      "_ZN7android14AndroidRuntime21registerNativeMethodsEP7_JNIEnvPKcPK15JNINativeMethodi");
  TestDlopenAndDlsym("libstagefright.so", "_ZN7android25MEDIA_MIMETYPE_AUDIO_MPEGE");
}

TEST(Shared, DlOpenSystemLibraries) {
  TestDlopenAndDlsym("libEGL.so", "eglGetError");
  TestDlopenAndDlsym("libGLESv1_CM.so", "glScalef");
  TestDlopenAndDlsym("libGLESv2.so", "glClear");
  TestDlopenAndDlsym("libOpenSLES.so", "SL_IID_OBJECT");
  TestDlopenAndDlsym("libandroid.so", "AConfiguration_new");
  TestDlopenAndDlsymUnstable("libicuuc.so",
                             {"ucnv_convert",
                              "ucnv_convert_3_2",
                              "ucnv_convert_3_8",
                              "ucnv_convert_4_2",
                              "ucnv_convert_44",
                              "ucnv_convert_46",
                              "ucnv_convert_48",
                              "ucnv_convert_50",
                              "ucnv_convert_51",
                              "ucnv_convert_52",
                              "ucnv_convert_53",
                              "ucnv_convert_54",
                              "ucnv_convert_55",
                              "ucnv_convert_56",
                              "ucnv_convert_57",
                              "ucnv_convert_58",
                              "ucnv_convert_59",
                              "ucnv_convert_60"});
  TestDlopenAndDlsym("libdl.so", "dlopen");
  TestDlopenAndDlsym("libjnigraphics.so", "AndroidBitmap_getInfo");
  TestDlopenAndDlsym("liblog.so", "__android_log_print");
  TestDlopenAndDlsym("libm.so", "sinh");
  TestDlopenAndDlsym("libnativehelper.so", "jniRegisterNativeMethods");
  TestDlopenAndDlsym("libz.so", "gzopen");
}

TEST(Shared, DlSym) {
  void* handle = dlopen("libberberis_ndk_tests_shared_lib.so", RTLD_NOW);
  void* func = reinterpret_cast<void*>(SharedFunction);
  EXPECT_EQ(func, dlsym(handle, "SharedFunction"));
  EXPECT_EQ(func, dlsym(RTLD_DEFAULT, "SharedFunction"));
}

TEST(Shared, DlIteratePhdr) {
  DlIteratePhdrData data{};
  dl_iterate_phdr(DlIteratePhdrCallback, &data);
  EXPECT_LT(0, data.n);
}
