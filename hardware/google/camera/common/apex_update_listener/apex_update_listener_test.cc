#include "apex_update_listener.h"

#include <android-base/file.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <log/log.h>
#include <fstream>
#include <iostream>
#include <set>
#include <thread>

#undef LOG_TAG
#define LOG_TAG "apex_update_listener_test"

namespace {

using InfoSet = std::set<ApexUpdateListener::Info>;
using testing::MockFunction;
using testing::StrictMock;

const std::string kApexInfoStateJustPreinstalled = R"xml(
<?xml version="1.0" encoding="utf-8"?>
<apex-info-list>
    <apex-info moduleName="com.test.apex" modulePath="/vendor/apex/com.test.apex" preinstalledModulePath="/vendor/apex/com.test.apex" versionCode="100" versionName="" isFactory="true" isActive="true"></apex-info>
</apex-info-list>
)xml";

const std::string kApexInfoStateOneOnData = R"xml(
<?xml version="1.0" encoding="utf-8"?>
<apex-info-list>
    <apex-info moduleName="com.test.apex" modulePath="/data/apex/com.test.apex@200" preinstalledModulePath="/vendor/apex/com.test.apex" versionCode="200" versionName="" isFactory="false" isActive="true"></apex-info>
    <apex-info moduleName="com.test.apex" modulePath="/vendor/apex/com.test.apex" preinstalledModulePath="/vendor/apex/com.test.apex" versionCode="100" versionName="" isFactory="true" isActive="false"></apex-info>
</apex-info-list>
)xml";

void WriteStringToFile(const char* filename, const std::string& data) {
  std::ofstream xml_stream(filename);
  xml_stream.write(data.data(), data.size());
}

}  // namespace

TEST(ApexUpdateListener, InstallUpdate) {
  TemporaryFile tmp_xml_file;

  StrictMock<MockFunction<ApexUpdateListener::CallbackFunction>> callback;
  std::mutex mutex;
  std::condition_variable wait_cv;
  EXPECT_CALL(
      callback,
      Call(InfoSet{{.module_name = "com.test.apex",
                    .module_path = "/vendor/apex/com.test.apex",
                    .preinstalled_module_path = "/vendor/apex/com.test.apex",
                    .version_code = 100,
                    .version_name = "",
                    .is_factory = true,
                    .is_active = true}},
           InfoSet{{.module_name = "com.test.apex",
                    .module_path = "/vendor/apex/com.test.apex",
                    .preinstalled_module_path = "/vendor/apex/com.test.apex",
                    .version_code = 100,
                    .version_name = "",
                    .is_factory = true,
                    .is_active = false},
                   {.module_name = "com.test.apex",
                    .module_path = "/data/apex/com.test.apex@200",
                    .preinstalled_module_path = "/vendor/apex/com.test.apex",
                    .version_code = 200,
                    .version_name = "",
                    .is_factory = false,
                    .is_active = true}}))
      .WillOnce([&mutex, &wait_cv]() {
        std::lock_guard<std::mutex> lock(mutex);
        wait_cv.notify_one();
      });

  WriteStringToFile(tmp_xml_file.path, kApexInfoStateJustPreinstalled);
  std::unique_ptr<ApexUpdateListener> ptr = ApexUpdateListener::Make(
      "com.test.apex", callback.AsStdFunction(), false, tmp_xml_file.path);
  EXPECT_NE(ptr, nullptr);
  WriteStringToFile(tmp_xml_file.path, kApexInfoStateOneOnData);
  std::unique_lock<std::mutex> lock(mutex);
  wait_cv.wait_for(lock, std::chrono::milliseconds(30000));
}

TEST(ApexUpdateListener, InvokeWithInitialVersion) {
  TemporaryFile tmp_xml_file;

  StrictMock<MockFunction<ApexUpdateListener::CallbackFunction>> callback;
  std::mutex mutex;
  std::condition_variable wait_cv;

  EXPECT_CALL(
      callback,
      Call(InfoSet{},
           InfoSet{{.module_name = "com.test.apex",
                    .module_path = "/vendor/apex/com.test.apex",
                    .preinstalled_module_path = "/vendor/apex/com.test.apex",
                    .version_code = 100,
                    .version_name = "",
                    .is_factory = true,
                    .is_active = true}}))
      .WillOnce([&mutex, &wait_cv]() {
        std::lock_guard<std::mutex> lock(mutex);
        wait_cv.notify_one();
      });

  WriteStringToFile(tmp_xml_file.path, kApexInfoStateJustPreinstalled);
  std::unique_ptr<ApexUpdateListener> ptr = ApexUpdateListener::Make(
      "com.test.apex", callback.AsStdFunction(), true, tmp_xml_file.path);
  EXPECT_NE(ptr, nullptr);
  std::unique_lock<std::mutex> lock(mutex);
  wait_cv.wait_for(lock, std::chrono::milliseconds(30000));
}
