/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <filesystem>

#include <android-base/file.h>
#include <apex_manifest.pb.h>
#include <gtest/gtest.h>

#include "linkerconfig/apex.h"

struct ApexTest : ::testing::Test {
  TemporaryDir tmp_dir;
  std::string root;

  void SetUp() override {
    root = tmp_dir.path;
  }

  void PrepareApex(const std::string& apex_name,
                   const std::vector<std::string>& provide_libs,
                   const std::vector<std::string>& require_libs,
                   const std::vector<std::string>& jni_libs) {
    ::apex::proto::ApexManifest manifest;
    manifest.set_name(apex_name);
    for (auto lib : provide_libs) {
      manifest.add_providenativelibs(lib);
    }
    for (auto lib : require_libs) {
      manifest.add_requirenativelibs(lib);
    }
    for (auto lib : jni_libs) {
      manifest.add_jnilibs(lib);
    }
    WriteFile("/apex/" + apex_name + "/apex_manifest.pb",
              manifest.SerializeAsString());
  }

  void Mkdir(std::string dir_path) {
    if (access(dir_path.c_str(), F_OK) == 0) return;
    Mkdir(android::base::Dirname(dir_path));
    ASSERT_NE(-1, mkdir(dir_path.c_str(), 0755) == -1)
        << "Failed to create a directory: " << dir_path;
  }

  void WriteFile(const std::string& file, const std::string& content) {
    std::string file_path = root + file;
    Mkdir(::android::base::Dirname(file_path));
    ASSERT_TRUE(::android::base::WriteStringToFile(content, file_path))
        << "Failed to write a file: " << file_path;
  }

  void CreateApexInfoList() {
    std::string content =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<apex-info-list>\n";
    for (const auto& file :
         std::filesystem::directory_iterator(root + "/apex")) {
      if (!file.is_directory()) {
        continue;
      }

      content += "<apex-info moduleName=\"";
      content += file.path().filename();
      content += "\" modulePath=\"";
      content += file.path().string();
      content +=
          "\" preinstalledModulePath=\"/test/path/1234\" isFactory=\"true\" "
          "isActive=\"true\" />\n";
    }
    content += "</apex-info-list>";

    WriteFile("/apex/apex-info-list.xml", content);
  }

  void CreatePublicLibrariesTxt() {
    std::string content = "foo.so\nbar.so\nbaz.so";
    WriteFile("/system/etc/public.libraries.txt", content);
  }
};