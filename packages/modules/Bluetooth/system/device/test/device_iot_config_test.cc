/******************************************************************************
 *
 *  Copyright (C) 2022 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include "device/include/device_iot_config.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <sys/mman.h>

#include "btcore/include/module.h"
#include "btif/include/btif_common.h"
#include "common/init_flags.h"
#include "device/src/device_iot_config_int.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_osi_alarm.h"
#include "test/mock/mock_osi_allocator.h"
#include "test/mock/mock_osi_config.h"
#include "test/mock/mock_osi_future.h"
#include "test/mock/mock_osi_properties.h"

using namespace testing;

const char* test_flags_feature_enabled[] = {
    "INIT_logging_debug_enabled_for_all=true",
    "INIT_device_iot_config_logging=true",
    nullptr,
};

const char* test_flags_feature_disabled[] = {
    "INIT_logging_debug_enabled_for_all=true",
    "INIT_device_iot_config_logging=false",
    nullptr,
};

extern module_t device_iot_config_module;

bt_status_t btif_transfer_context(tBTIF_CBACK* p_cback, uint16_t event,
                                  char* p_params, int param_len,
                                  tBTIF_COPY_CBACK* p_copy_cback) {
  inc_func_call_count(__func__);
  return BT_STATUS_SUCCESS;
}

struct alarm_t {
  alarm_t(const char* name){};
  int any_value;
};

struct future_t {
  future_t(void* value){};
  void* value;
};

struct alarm_t placeholder_alarm("");
struct future_t placeholder_future(NULL);
std::string true_val = "true";

class DeviceIotConfigModuleTest : public testing::Test {
 protected:
  void SetUp() override {
    bluetooth::common::InitFlags::Load(test_flags_feature_enabled);

    test::mock::osi_alarm::alarm_new.body = [&](const char* name) -> alarm_t* {
      return &placeholder_alarm;
    };

    test::mock::osi_properties::osi_property_get_bool.body =
        [&](const char* key, bool default_value) -> int { return false; };

    test::mock::osi_alarm::alarm_set.body =
        [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
            void* data) { return; };

    test::mock::osi_alarm::alarm_free.body = [](alarm_t* alarm) {};

    test::mock::osi_alarm::alarm_is_scheduled.body =
        [&](const alarm_t* alarm) -> bool { return false; };

    test::mock::osi_future::future_new_immediate.body =
        [&](void* value) -> future_t* { return &placeholder_future; };

    test::mock::osi_config::config_new_empty.body =
        [&]() -> std::unique_ptr<config_t> {
      return std::make_unique<config_t>();
    };

    test::mock::osi_config::config_new.body =
        [&](const char* filename) -> std::unique_ptr<config_t> {
      return std::make_unique<config_t>();
    };

    test::mock::osi_config::config_get_int.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key, int def_value) { return def_value; };

    test::mock::osi_config::config_set_int.body =
        [&](config_t* config, const std::string& section,
            const std::string& key, int value) { return; };

    test::mock::osi_config::config_get_string.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key,
            const std::string* def_value) { return def_value; };

    test::mock::osi_config::config_set_string.body =
        [&](config_t* config, const std::string& section,
            const std::string& key, const std::string& value) { return; };

    test::mock::osi_allocator::osi_free.body = [&](void* ptr) {};

    reset_mock_function_count_map();
  }

  void TearDown() override {
    test::mock::osi_alarm::alarm_new = {};
    test::mock::osi_alarm::alarm_set = {};
    test::mock::osi_alarm::alarm_free = {};
    test::mock::osi_alarm::alarm_is_scheduled = {};
    test::mock::osi_future::future_new_immediate = {};
    test::mock::osi_properties::osi_property_get = {};
    test::mock::osi_config::config_new_empty = {};
    test::mock::osi_config::config_new = {};
    test::mock::osi_config::config_get_int = {};
    test::mock::osi_config::config_set_int = {};
    test::mock::osi_config::config_get_string = {};
    test::mock::osi_config::config_set_string = {};
    test::mock::osi_allocator::osi_free = {};
  }
};

TEST_F(DeviceIotConfigModuleTest,
       test_device_iot_config_module_init_is_factory_reset) {
  bool is_factory_reset = false;
  config_t* config_new_return_value = NULL;
  config_t* config_new_empty_return_value = NULL;

  test::mock::osi_properties::osi_property_get_bool.body =
      [&](const char* key, bool default_value) -> int {
    return is_factory_reset;
  };

  test::mock::osi_config::config_new.body = [&](const char* filename) {
    return std::unique_ptr<config_t>(config_new_return_value);
  };

  test::mock::osi_config::config_new_empty.body = [&](void) {
    return std::unique_ptr<config_t>(config_new_empty_return_value);
  };

  {
    reset_mock_function_count_map();

    is_factory_reset = true;
    config_new_return_value = NULL;
    config_new_empty_return_value = NULL;

    errno = 0;
    int file_fd = -1;
    int backup_fd = -1;

    file_fd = open(IOT_CONFIG_FILE_PATH, O_CREAT | O_RDWR | O_TRUNC | O_CLOEXEC,
                   S_IRUSR | S_IWUSR);
    EXPECT_TRUE(file_fd > 0);
    EXPECT_EQ(errno, 0);

    backup_fd = open(IOT_CONFIG_BACKUP_PATH,
                     O_CREAT | O_RDWR | O_TRUNC | O_CLOEXEC, S_IRUSR | S_IWUSR);
    EXPECT_TRUE(backup_fd > 0);
    EXPECT_EQ(errno, 0);

    EXPECT_EQ(access(IOT_CONFIG_FILE_PATH, F_OK), 0);
    EXPECT_EQ(access(IOT_CONFIG_BACKUP_PATH, F_OK), 0);

    device_iot_config_module_init();

    errno = 0;
    EXPECT_EQ(access(IOT_CONFIG_FILE_PATH, F_OK), -1);
    EXPECT_EQ(errno, ENOENT);

    errno = 0;
    EXPECT_EQ(access(IOT_CONFIG_BACKUP_PATH, F_OK), -1);
    EXPECT_EQ(errno, ENOENT);

    EXPECT_EQ(get_func_call_count("config_new"), 2);
    EXPECT_EQ(get_func_call_count("config_new_empty"), 1);
    EXPECT_EQ(get_func_call_count("alarm_free"), 1);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
}

TEST_F(DeviceIotConfigModuleTest,
       test_device_iot_config_module_init_no_config) {
  test::mock::osi_config::config_new.body = [&](const char* filename) {
    return std::unique_ptr<config_t>(nullptr);
  };

  test::mock::osi_config::config_new_empty.body = [&](void) {
    return std::unique_ptr<config_t>(nullptr);
  };

  {
    reset_mock_function_count_map();

    device_iot_config_module_init();

    EXPECT_EQ(get_func_call_count("config_new"), 2);
    EXPECT_EQ(get_func_call_count("config_new_empty"), 1);
    EXPECT_EQ(get_func_call_count("alarm_free"), 1);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
}

TEST_F(DeviceIotConfigModuleTest, test_device_iot_config_module_init_original) {
  std::string enable_logging_property_get_value;
  std::string factory_reset_property_get_value;
  config_t* config_new_return_value = NULL;
  config_t* config_new_empty_return_value = NULL;

  test::mock::osi_config::config_new.body = [&](const char* filename) {
    return std::unique_ptr<config_t>(config_new_return_value);
  };

  test::mock::osi_config::config_new_empty.body = [&](void) {
    return std::unique_ptr<config_t>(config_new_empty_return_value);
  };

  {
    reset_mock_function_count_map();

    enable_logging_property_get_value = "true";
    factory_reset_property_get_value = "false";
    config_new_return_value = new config_t();
    config_new_empty_return_value = NULL;
    int config_get_int_return_value = DEVICE_IOT_INFO_CURRENT_VERSION;
    std::string config_get_string_return_value(TIME_STRING_FORMAT);

    test::mock::osi_config::config_get_int.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key,
            int def_value) { return config_get_int_return_value; };

    test::mock::osi_config::config_get_string.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key, const std::string* def_value) {
          return &config_get_string_return_value;
        };

    device_iot_config_module_init();

    EXPECT_EQ(get_func_call_count("config_new"), 1);
    EXPECT_EQ(get_func_call_count("config_new_empty"), 0);
    EXPECT_EQ(get_func_call_count("config_set_int"), 0);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_new"), 1);
    EXPECT_EQ(get_func_call_count("alarm_free"), 0);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
}

TEST_F(DeviceIotConfigModuleTest, test_device_iot_config_module_init_backup) {
  std::string enable_logging_property_get_value;
  std::string factory_reset_property_get_value;
  config_t* config_new_return_value = NULL;
  config_t* config_new_empty_return_value = NULL;

  test::mock::osi_config::config_new.body = [&](const char* filename) {
    if (strcmp(filename, IOT_CONFIG_BACKUP_PATH) == 0) {
      return std::unique_ptr<config_t>(config_new_return_value);
    }
    return std::unique_ptr<config_t>(nullptr);
  };

  test::mock::osi_config::config_new_empty.body = [&](void) {
    return std::unique_ptr<config_t>(config_new_empty_return_value);
  };

  {
    reset_mock_function_count_map();

    enable_logging_property_get_value = "true";
    factory_reset_property_get_value = "false";
    config_new_return_value = new config_t();
    config_new_empty_return_value = NULL;
    int config_get_int_return_value = DEVICE_IOT_INFO_CURRENT_VERSION;
    std::string config_get_string_return_value(TIME_STRING_FORMAT);

    test::mock::osi_config::config_get_int.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key,
            int def_value) { return config_get_int_return_value; };

    test::mock::osi_config::config_get_string.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key, const std::string* def_value) {
          return &config_get_string_return_value;
        };

    device_iot_config_module_init();

    EXPECT_EQ(get_func_call_count("config_new"), 2);
    EXPECT_EQ(get_func_call_count("config_new_empty"), 0);
    EXPECT_EQ(get_func_call_count("config_set_int"), 0);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_new"), 1);
    EXPECT_EQ(get_func_call_count("alarm_free"), 0);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
}

TEST_F(DeviceIotConfigModuleTest, test_device_iot_config_module_init_new_file) {
  std::string enable_logging_property_get_value;
  std::string factory_reset_property_get_value;
  config_t* config_new_return_value = NULL;
  config_t* config_new_empty_return_value = NULL;

  test::mock::osi_config::config_new.body = [&](const char* filename) {
    return std::unique_ptr<config_t>(config_new_return_value);
  };

  test::mock::osi_config::config_new_empty.body = [&](void) {
    return std::unique_ptr<config_t>(config_new_empty_return_value);
  };

  {
    reset_mock_function_count_map();

    enable_logging_property_get_value = "true";
    factory_reset_property_get_value = "false";
    config_new_return_value = NULL;
    config_new_empty_return_value = new config_t();
    std::string config_get_string_return_value(TIME_STRING_FORMAT);

    test::mock::osi_config::config_get_string.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key, const std::string* def_value) {
          return &config_get_string_return_value;
        };

    device_iot_config_module_init();

    EXPECT_EQ(get_func_call_count("config_new"), 2);
    EXPECT_EQ(get_func_call_count("config_new_empty"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_new"), 1);
    EXPECT_EQ(get_func_call_count("alarm_free"), 0);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
}

TEST_F(DeviceIotConfigModuleTest,
       test_device_iot_config_module_init_version_invalid) {
  std::string enable_logging_property_get_value;
  std::string factory_reset_property_get_value;
  config_t* config_new_return_value = NULL;
  config_t* config_new_empty_return_value = NULL;

  test::mock::osi_config::config_new.body = [&](const char* filename) {
    return std::unique_ptr<config_t>(config_new_return_value);
  };

  test::mock::osi_config::config_new_empty.body = [&](void) {
    return std::unique_ptr<config_t>(config_new_empty_return_value);
  };

  {
    reset_mock_function_count_map();

    enable_logging_property_get_value = "true";
    factory_reset_property_get_value = "false";
    config_new_return_value = new config_t();
    config_new_empty_return_value = NULL;
    int config_get_int_return_value = -1;
    std::string config_get_string_return_value(TIME_STRING_FORMAT);

    test::mock::osi_config::config_get_int.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key,
            int def_value) { return config_get_int_return_value; };

    test::mock::osi_config::config_get_string.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key, const std::string* def_value) {
          return &config_get_string_return_value;
        };

    device_iot_config_module_init();

    EXPECT_EQ(get_func_call_count("config_new"), 1);
    EXPECT_EQ(get_func_call_count("config_new_empty"), 0);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_new"), 1);
    EXPECT_EQ(get_func_call_count("alarm_free"), 0);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
}

TEST_F(
    DeviceIotConfigModuleTest,
    test_device_iot_config_module_init_version_new_config_new_empty_success) {
  std::string enable_logging_property_get_value;
  std::string factory_reset_property_get_value;
  config_t* config_new_return_value = NULL;
  config_t* config_new_empty_return_value = NULL;

  test::mock::osi_config::config_new.body = [&](const char* filename) {
    return std::unique_ptr<config_t>(config_new_return_value);
  };

  test::mock::osi_config::config_new_empty.body = [&](void) {
    return std::unique_ptr<config_t>(config_new_empty_return_value);
  };

  {
    reset_mock_function_count_map();

    enable_logging_property_get_value = "true";
    factory_reset_property_get_value = "true";
    config_new_return_value = new config_t();
    config_new_empty_return_value = new config_t();
    int config_get_int_return_value = 2;
    std::string config_get_string_return_value(TIME_STRING_FORMAT);

    test::mock::osi_config::config_get_int.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key,
            int def_value) { return config_get_int_return_value; };

    test::mock::osi_config::config_get_string.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key, const std::string* def_value) {
          return &config_get_string_return_value;
        };

    int file_fd = -1;
    int backup_fd = -1;

    errno = 0;
    file_fd = open(IOT_CONFIG_FILE_PATH, O_CREAT | O_RDWR | O_TRUNC | O_CLOEXEC,
                   S_IRUSR | S_IWUSR);
    EXPECT_TRUE(file_fd > 0);
    EXPECT_EQ(errno, 0);

    errno = 0;
    backup_fd = open(IOT_CONFIG_BACKUP_PATH,
                     O_CREAT | O_RDWR | O_TRUNC | O_CLOEXEC, S_IRUSR | S_IWUSR);
    EXPECT_TRUE(backup_fd > 0);
    EXPECT_EQ(errno, 0);

    EXPECT_EQ(access(IOT_CONFIG_FILE_PATH, F_OK), 0);
    EXPECT_EQ(access(IOT_CONFIG_BACKUP_PATH, F_OK), 0);

    device_iot_config_module_init();

    errno = 0;
    EXPECT_EQ(access(IOT_CONFIG_FILE_PATH, F_OK), -1);
    EXPECT_EQ(errno, ENOENT);

    errno = 0;
    EXPECT_EQ(access(IOT_CONFIG_BACKUP_PATH, F_OK), -1);
    EXPECT_EQ(errno, ENOENT);

    EXPECT_EQ(get_func_call_count("config_new"), 1);
    EXPECT_EQ(get_func_call_count("config_new_empty"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_new"), 1);
    EXPECT_EQ(get_func_call_count("alarm_free"), 0);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
}

TEST_F(DeviceIotConfigModuleTest,
       test_device_iot_config_module_init_version_new_config_new_empty_fail) {
  std::string enable_logging_property_get_value;
  std::string factory_reset_property_get_value;
  config_t* config_new_return_value = NULL;
  config_t* config_new_empty_return_value = NULL;

  test::mock::osi_config::config_new.body = [&](const char* filename) {
    return std::unique_ptr<config_t>(config_new_return_value);
  };

  test::mock::osi_config::config_new_empty.body = [&](void) {
    return std::unique_ptr<config_t>(config_new_empty_return_value);
  };

  {
    reset_mock_function_count_map();

    enable_logging_property_get_value = "true";
    factory_reset_property_get_value = "false";
    config_new_return_value = new config_t();
    config_new_empty_return_value = NULL;
    int config_get_int_return_value = 2;
    std::string config_get_string_return_value(TIME_STRING_FORMAT);

    test::mock::osi_config::config_get_int.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key,
            int def_value) { return config_get_int_return_value; };

    test::mock::osi_config::config_get_string.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key, const std::string* def_value) {
          return &config_get_string_return_value;
        };

    int file_fd = -1;
    int backup_fd = -1;

    errno = 0;
    file_fd = open(IOT_CONFIG_FILE_PATH, O_CREAT | O_RDWR | O_TRUNC | O_CLOEXEC,
                   S_IRUSR | S_IWUSR);
    EXPECT_TRUE(file_fd > 0);
    EXPECT_EQ(errno, 0);

    errno = 0;
    backup_fd = open(IOT_CONFIG_BACKUP_PATH,
                     O_CREAT | O_RDWR | O_TRUNC | O_CLOEXEC, S_IRUSR | S_IWUSR);
    EXPECT_TRUE(backup_fd > 0);
    EXPECT_EQ(errno, 0);

    EXPECT_EQ(access(IOT_CONFIG_FILE_PATH, F_OK), 0);
    EXPECT_EQ(access(IOT_CONFIG_BACKUP_PATH, F_OK), 0);

    device_iot_config_module_init();

    errno = 0;
    EXPECT_EQ(access(IOT_CONFIG_FILE_PATH, F_OK), -1);
    EXPECT_EQ(errno, ENOENT);

    errno = 0;
    EXPECT_EQ(access(IOT_CONFIG_BACKUP_PATH, F_OK), -1);
    EXPECT_EQ(errno, ENOENT);

    EXPECT_EQ(get_func_call_count("config_new"), 1);
    EXPECT_EQ(get_func_call_count("config_new_empty"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 0);
    EXPECT_EQ(get_func_call_count("config_get_string"), 0);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_new"), 0);
    EXPECT_EQ(get_func_call_count("alarm_free"), 1);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
}

TEST_F(DeviceIotConfigModuleTest,
       test_device_iot_config_module_init_original_timestamp_null) {
  std::string enable_logging_property_get_value;
  std::string factory_reset_property_get_value;
  config_t* config_new_return_value = NULL;
  config_t* config_new_empty_return_value = NULL;

  test::mock::osi_config::config_new.body = [&](const char* filename) {
    return std::unique_ptr<config_t>(config_new_return_value);
  };

  test::mock::osi_config::config_new_empty.body = [&](void) {
    return std::unique_ptr<config_t>(config_new_empty_return_value);
  };

  {
    reset_mock_function_count_map();

    enable_logging_property_get_value = "true";
    factory_reset_property_get_value = "false";
    config_new_return_value = new config_t();
    config_new_empty_return_value = NULL;
    int config_get_int_return_value = DEVICE_IOT_INFO_CURRENT_VERSION;

    test::mock::osi_config::config_get_int.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key,
            int def_value) { return config_get_int_return_value; };

    test::mock::osi_config::config_get_string.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key,
            const std::string* def_value) { return nullptr; };

    device_iot_config_module_init();

    EXPECT_EQ(get_func_call_count("config_new"), 1);
    EXPECT_EQ(get_func_call_count("config_new_empty"), 0);
    EXPECT_EQ(get_func_call_count("config_set_int"), 0);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("alarm_new"), 1);
    EXPECT_EQ(get_func_call_count("alarm_free"), 0);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
}

TEST_F(DeviceIotConfigModuleTest,
       test_device_iot_config_module_init_alarm_new_fail) {
  std::string enable_logging_property_get_value;
  std::string factory_reset_property_get_value;
  config_t* config_new_return_value = NULL;
  config_t* config_new_empty_return_value = NULL;

  test::mock::osi_config::config_new.body = [&](const char* filename) {
    return std::unique_ptr<config_t>(config_new_return_value);
  };

  test::mock::osi_config::config_new_empty.body = [&](void) {
    return std::unique_ptr<config_t>(config_new_empty_return_value);
  };

  {
    reset_mock_function_count_map();

    enable_logging_property_get_value = "true";
    factory_reset_property_get_value = "false";
    config_new_return_value = new config_t();
    config_new_empty_return_value = NULL;
    int config_get_int_return_value = DEVICE_IOT_INFO_CURRENT_VERSION;
    std::string config_get_string_return_value(TIME_STRING_FORMAT);

    test::mock::osi_config::config_get_int.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key,
            int def_value) { return config_get_int_return_value; };

    test::mock::osi_config::config_get_string.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key, const std::string* def_value) {
          return &config_get_string_return_value;
        };

    test::mock::osi_alarm::alarm_new.body = [&](const char* name) {
      return nullptr;
    };

    device_iot_config_module_init();

    EXPECT_EQ(get_func_call_count("config_new"), 1);
    EXPECT_EQ(get_func_call_count("config_new_empty"), 0);
    EXPECT_EQ(get_func_call_count("config_set_int"), 0);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_new"), 1);
    EXPECT_EQ(get_func_call_count("alarm_free"), 1);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
}

TEST_F(DeviceIotConfigModuleTest, test_device_iot_config_module_start_up) {
  std::string enable_logging_property_get_value;
  std::string factory_reset_property_get_value;

  enable_logging_property_get_value = "true";

  device_iot_config_module_init();

  {
    reset_mock_function_count_map();

    device_iot_config_module_start_up();

    EXPECT_EQ(get_func_call_count("config_new"), 0);
    EXPECT_EQ(get_func_call_count("config_new_empty"), 0);
    EXPECT_EQ(get_func_call_count("alarm_free"), 0);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
}

TEST_F(DeviceIotConfigModuleTest, test_device_iot_config_module_shutdown) {
  bool return_value;
  std::string enable_logging_property_get_value;
  std::string factory_reset_property_get_value;

  test::mock::osi_alarm::alarm_is_scheduled.body =
      [&](const alarm_t* alarm) -> bool { return return_value; };

  enable_logging_property_get_value = "true";
  device_iot_config_module_init();

  {
    reset_mock_function_count_map();

    return_value = false;

    device_iot_config_module_shut_down();

    EXPECT_EQ(get_func_call_count("alarm_is_scheduled"), 1);
    EXPECT_EQ(get_func_call_count("alarm_cancel"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("config_save"), 1);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  {
    reset_mock_function_count_map();

    return_value = true;

    device_iot_config_module_shut_down();

    EXPECT_EQ(get_func_call_count("alarm_is_scheduled"), 1);
    EXPECT_EQ(get_func_call_count("alarm_cancel"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("config_save"), 1);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
  test::mock::osi_alarm::alarm_is_scheduled.body = {};
}

TEST_F(DeviceIotConfigModuleTest, test_device_iot_config_module_clean_up) {
  bool return_value;
  std::string enable_logging_property_get_value;
  std::string factory_reset_property_get_value;

  test::mock::osi_alarm::alarm_is_scheduled.body =
      [&](const alarm_t* alarm) -> bool { return return_value; };

  enable_logging_property_get_value = "true";
  device_iot_config_module_init();

  {
    reset_mock_function_count_map();

    return_value = false;
    device_iot_config_module_clean_up();

    EXPECT_EQ(get_func_call_count("alarm_is_scheduled"), 1);
    EXPECT_EQ(get_func_call_count("alarm_free"), 1);
    EXPECT_EQ(get_func_call_count("alarm_cancel"), 0);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("config_save"), 0);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  device_iot_config_module_init();

  {
    reset_mock_function_count_map();

    return_value = true;
    device_iot_config_module_clean_up();

    EXPECT_EQ(get_func_call_count("alarm_is_scheduled"), 2);
    EXPECT_EQ(get_func_call_count("alarm_free"), 1);
    EXPECT_EQ(get_func_call_count("alarm_cancel"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("config_save"), 1);
    EXPECT_EQ(get_func_call_count("future_new_immediate"), 1);
  }

  test::mock::osi_config::config_new.body = {};
  test::mock::osi_config::config_new_empty.body = {};
  test::mock::osi_alarm::alarm_is_scheduled.body = {};
}

class DeviceIotConfigTest : public testing::Test {
 protected:
  void SetUp() override {
    bluetooth::common::InitFlags::Load(test_flags_feature_enabled);

    test::mock::osi_alarm::alarm_new.body = [&](const char* name) -> alarm_t* {
      return &placeholder_alarm;
    };

    test::mock::osi_properties::osi_property_get_bool.body =
        [&](const char* key, bool default_value) -> int { return false; };

    test::mock::osi_alarm::alarm_set.body =
        [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
            void* data) { return; };

    test::mock::osi_alarm::alarm_free.body = [](alarm_t* alarm) {};

    test::mock::osi_alarm::alarm_is_scheduled.body =
        [&](const alarm_t* alarm) -> bool { return false; };

    test::mock::osi_future::future_new_immediate.body =
        [&](void* value) -> future_t* { return &placeholder_future; };

    test::mock::osi_config::config_new_empty.body =
        [&]() -> std::unique_ptr<config_t> {
      return std::make_unique<config_t>();
    };

    test::mock::osi_config::config_new.body =
        [&](const char* filename) -> std::unique_ptr<config_t> {
      return std::make_unique<config_t>();
    };

    test::mock::osi_config::config_get_int.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key, int def_value) { return def_value; };

    test::mock::osi_config::config_set_int.body =
        [&](config_t* config, const std::string& section,
            const std::string& key, int value) { return; };

    test::mock::osi_config::config_get_string.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key,
            const std::string* def_value) { return def_value; };

    test::mock::osi_config::config_set_string.body =
        [&](config_t* config, const std::string& section,
            const std::string& key, const std::string& value) { return; };

    test::mock::osi_allocator::osi_free.body = [&](void* ptr) {};

    device_iot_config_module_init();
    device_iot_config_module_start_up();

    reset_mock_function_count_map();
  }

  void TearDown() override {
    test::mock::osi_alarm::alarm_new = {};
    test::mock::osi_alarm::alarm_set = {};
    test::mock::osi_alarm::alarm_free = {};
    test::mock::osi_alarm::alarm_is_scheduled = {};
    test::mock::osi_future::future_new_immediate = {};
    test::mock::osi_properties::osi_property_get = {};
    test::mock::osi_config::config_new_empty = {};
    test::mock::osi_config::config_new = {};
    test::mock::osi_config::config_get_int = {};
    test::mock::osi_config::config_set_int = {};
    test::mock::osi_config::config_get_string = {};
    test::mock::osi_config::config_set_string = {};
    test::mock::osi_allocator::osi_free = {};
  }
};

TEST_F(DeviceIotConfigTest, test_device_iot_config_sections_sort_by_entry_key) {
  {
    config_t conf;
    device_iot_config_sections_sort_by_entry_key(conf, NULL);
  }

  {
    config_t conf;
    conf.sections = {
        section_t{.entries =
                      {
                          entry_t{
                              .key = "a",
                          },
                          entry_t{
                              .key = "b",
                          },
                          entry_t{
                              .key = "c",
                          },
                          entry_t{
                              .key = "d",
                          },
                      }},

        section_t{.entries =
                      {
                          entry_t{
                              .key = "d",
                          },
                          entry_t{
                              .key = "c",
                          },
                          entry_t{
                              .key = "b",
                          },
                          entry_t{
                              .key = "a",
                          },
                      }},

    };
    device_iot_config_sections_sort_by_entry_key(
        conf, [](const entry_t& first, const entry_t& second) {
          return first.key.compare(second.key) >= 0;
        });

    auto& sec1 = conf.sections.front();
    auto& sec2 = conf.sections.back();

    for (auto i = 0; i < 4; ++i) {
      EXPECT_EQ(sec1.entries.front().key, sec2.entries.front().key);
      sec1.entries.pop_front();
      sec2.entries.pop_front();
    }
  }
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_has_section) {
  std::string actual_section, expected_section = "abc";
  bool return_value = false;

  test::mock::osi_config::config_has_section.body =
      [&](const config_t& config, const std::string& section) {
        actual_section = section;
        return return_value;
      };

  {
    reset_mock_function_count_map();

    EXPECT_EQ(device_iot_config_has_section(expected_section), return_value);
    EXPECT_EQ(actual_section, expected_section);

    EXPECT_EQ(get_func_call_count("config_has_section"), 1);
  }

  {
    reset_mock_function_count_map();

    return_value = true;

    EXPECT_EQ(device_iot_config_has_section(expected_section), return_value);

    EXPECT_EQ(get_func_call_count("config_has_section"), 1);
  }

  test::mock::osi_config::config_has_section.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_exist) {
  std::string actual_section, actual_key, expected_section = "abc",
                                          expected_key = "def";
  bool return_value = false;

  test::mock::osi_config::config_has_key.body = [&](const config_t& config,
                                                    const std::string& section,
                                                    const std::string& key) {
    actual_section = section;
    actual_key = key;
    return return_value;
  };

  {
    reset_mock_function_count_map();

    EXPECT_EQ(device_iot_config_exist(expected_section, expected_key),
              return_value);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_has_key"), 1);
  }

  {
    reset_mock_function_count_map();

    return_value = true;

    EXPECT_EQ(device_iot_config_exist(expected_section, expected_key),
              return_value);

    EXPECT_EQ(get_func_call_count("config_has_key"), 1);
  }

  test::mock::osi_config::config_has_key.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_has_key_value) {
  std::string actual_section, actual_key, expected_section = "abc",
                                          expected_key = "def";
  std::string expected_value_str = "xyz", actual_value_str;
  const std::string* actual_def_value = NULL;
  const std::string* return_value = NULL;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        actual_def_value = def_value;
        return return_value;
      };

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_has_key_value(expected_section, expected_key,
                                                 expected_value_str));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    actual_value_str = "xyy";
    return_value = &actual_value_str;
    EXPECT_FALSE(device_iot_config_has_key_value(expected_section, expected_key,
                                                 expected_value_str));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    actual_value_str = "xy";
    return_value = &actual_value_str;
    EXPECT_FALSE(device_iot_config_has_key_value(expected_section, expected_key,
                                                 expected_value_str));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    actual_value_str = "xyyy";
    return_value = &actual_value_str;
    EXPECT_FALSE(device_iot_config_has_key_value(expected_section, expected_key,
                                                 expected_value_str));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    actual_value_str = "xyz";
    return_value = &actual_value_str;
    EXPECT_TRUE(device_iot_config_has_key_value(expected_section, expected_key,
                                                expected_value_str));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_get_int) {
  std::string actual_section, actual_key, expected_section = "abc",
                                          expected_key = "def";
  bool return_value = false;
  int int_value = 0, new_value = 0xff;

  test::mock::osi_config::config_has_key.body = [&](const config_t& config,
                                                    const std::string& section,
                                                    const std::string& key) {
    actual_section = section;
    actual_key = key;
    return return_value;
  };

  test::mock::osi_config::config_get_int.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, int def_value) { return new_value; };

  {
    reset_mock_function_count_map();

    EXPECT_EQ(
        device_iot_config_get_int(expected_section, expected_key, int_value),
        return_value);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_has_key"), 1);
    EXPECT_EQ(get_func_call_count("config_get_int"), 0);
  }

  {
    reset_mock_function_count_map();

    return_value = true;

    EXPECT_EQ(
        device_iot_config_get_int(expected_section, expected_key, int_value),
        return_value);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, new_value);

    EXPECT_EQ(get_func_call_count("config_has_key"), 1);
    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
  }

  test::mock::osi_config::config_has_key.body = {};
  test::mock::osi_config::config_get_int.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_addr_get_int) {
  const RawAddress peer_addr{};
  std::string actual_section, actual_key,
      expected_section = "00:00:00:00:00:00", expected_key = "def";
  bool return_value = false;
  int int_value = 0, new_value = 0xff;

  test::mock::osi_config::config_has_key.body = [&](const config_t& config,
                                                    const std::string& section,
                                                    const std::string& key) {
    actual_section = section;
    actual_key = key;
    return return_value;
  };

  test::mock::osi_config::config_get_int.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, int def_value) { return new_value; };

  {
    reset_mock_function_count_map();

    EXPECT_EQ(
        DEVICE_IOT_CONFIG_ADDR_GET_INT(peer_addr, expected_key, int_value),
        return_value);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_has_key"), 1);
    EXPECT_EQ(get_func_call_count("config_get_int"), 0);
  }

  {
    reset_mock_function_count_map();

    return_value = true;

    EXPECT_EQ(
        DEVICE_IOT_CONFIG_ADDR_GET_INT(peer_addr, expected_key, int_value),
        return_value);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, new_value);

    EXPECT_EQ(get_func_call_count("config_has_key"), 1);
    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
  }

  test::mock::osi_config::config_has_key.body = {};
  test::mock::osi_config::config_get_int.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_set_int) {
  std::string actual_section, actual_key, expected_section = "abc",
                                          expected_key = "def";
  std::string string_return_value = "123456789";
  std::string old_string_value = string_return_value;
  std::string new_string_value;
  int int_value = 123456789;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        return &string_return_value;
      };

  test::mock::osi_config::config_set_string.body =
      [&](config_t* config, const std::string& section, const std::string& key,
          const std::string& value) { new_string_value = value; };

  test::mock::osi_alarm::alarm_set.body =
      [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
          void* data) {};

  {
    reset_mock_function_count_map();

    EXPECT_TRUE(
        device_iot_config_set_int(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "123";

    EXPECT_TRUE(
        device_iot_config_set_int(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(new_string_value, old_string_value);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
  test::mock::osi_config::config_set_string.body = {};
  test::mock::osi_alarm::alarm_set.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_addr_set_int) {
  const RawAddress peer_addr{};
  std::string actual_key, expected_key = "def";
  std::string actual_section, expected_section = "00:00:00:00:00:00";
  std::string string_return_value = "123456789";
  std::string old_string_value = string_return_value;
  std::string new_string_value;
  int int_value = 123456789;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        return &string_return_value;
      };

  test::mock::osi_config::config_set_string.body =
      [&](config_t* config, const std::string& section, const std::string& key,
          const std::string& value) { new_string_value = value; };

  test::mock::osi_alarm::alarm_set.body =
      [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
          void* data) {};

  {
    reset_mock_function_count_map();

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_SET_INT(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "123";

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_SET_INT(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(new_string_value, old_string_value);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
  test::mock::osi_config::config_set_string.body = {};
  test::mock::osi_alarm::alarm_set.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_int_add_one) {
  std::string actual_section, actual_key, expected_section = "abc",
                                          expected_key = "def";
  int int_value = 0, get_default_value, set_value;

  test::mock::osi_config::config_get_int.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, int def_value) {
        actual_section = section;
        actual_key = key;
        get_default_value = def_value;
        return int_value;
      };

  test::mock::osi_config::config_set_int.body =
      [&](config_t* config, const std::string& section, const std::string& key,
          int val) { set_value = val; };

  test::mock::osi_alarm::alarm_set.body =
      [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
          void* data) {};

  {
    reset_mock_function_count_map();

    int_value = -1;

    EXPECT_TRUE(device_iot_config_int_add_one(expected_section, expected_key));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_default_value, 0);
    EXPECT_EQ(set_value, 0);

    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  {
    reset_mock_function_count_map();

    int_value = 0;

    EXPECT_TRUE(device_iot_config_int_add_one(expected_section, expected_key));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_default_value, 0);
    EXPECT_EQ(set_value, int_value + 1);

    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  {
    reset_mock_function_count_map();

    int_value = 1;

    EXPECT_TRUE(device_iot_config_int_add_one(expected_section, expected_key));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_default_value, 0);
    EXPECT_EQ(set_value, int_value + 1);

    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  {
    reset_mock_function_count_map();

    int_value = INT_MAX;

    EXPECT_TRUE(device_iot_config_int_add_one(expected_section, expected_key));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_default_value, 0);
    EXPECT_EQ(set_value, int_value + 1);
    EXPECT_EQ(set_value, INT_MIN);

    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  {
    reset_mock_function_count_map();

    int_value = INT_MIN;

    EXPECT_TRUE(device_iot_config_int_add_one(expected_section, expected_key));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_default_value, 0);
    EXPECT_EQ(set_value, 0);

    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  test::mock::osi_config::config_get_int.body = {};
  test::mock::osi_config::config_set_int.body = {};
  test::mock::osi_alarm::alarm_set.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_addr_int_add_one) {
  const RawAddress peer_addr{};
  std::string actual_section, actual_key,
      expected_section = "00:00:00:00:00:00", expected_key = "def";
  int int_value = 0, get_default_value, set_value;

  test::mock::osi_config::config_get_int.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, int def_value) {
        actual_section = section;
        actual_key = key;
        get_default_value = def_value;
        return int_value;
      };

  test::mock::osi_config::config_set_int.body =
      [&](config_t* config, const std::string& section, const std::string& key,
          int val) { set_value = val; };

  test::mock::osi_alarm::alarm_set.body =
      [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
          void* data) {};

  {
    reset_mock_function_count_map();

    int_value = -1;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(peer_addr, expected_key));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_default_value, 0);
    EXPECT_EQ(set_value, 0);

    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  {
    reset_mock_function_count_map();

    int_value = 0;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(peer_addr, expected_key));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_default_value, 0);
    EXPECT_EQ(set_value, int_value + 1);

    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  {
    reset_mock_function_count_map();

    int_value = 1;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(peer_addr, expected_key));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_default_value, 0);
    EXPECT_EQ(set_value, int_value + 1);

    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  {
    reset_mock_function_count_map();

    int_value = INT_MAX;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(peer_addr, expected_key));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_default_value, 0);
    EXPECT_EQ(set_value, int_value + 1);
    EXPECT_EQ(set_value, INT_MIN);

    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  {
    reset_mock_function_count_map();

    int_value = INT_MIN;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(peer_addr, expected_key));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_default_value, 0);
    EXPECT_EQ(set_value, 0);

    EXPECT_EQ(get_func_call_count("config_get_int"), 1);
    EXPECT_EQ(get_func_call_count("config_set_int"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }
  test::mock::osi_config::config_get_int.body = {};
  test::mock::osi_config::config_set_int.body = {};
  test::mock::osi_alarm::alarm_set.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_get_hex) {
  std::string actual_section, actual_key,
      expected_section = "00:00:00:00:00:00", expected_key = "def";
  int int_value = 0;
  std::string string_value;
  std::string* get_string_return_value = NULL;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        return get_string_return_value;
      };

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(
        device_iot_config_get_hex(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, 0);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "g";
    get_string_return_value = &string_value;
    EXPECT_FALSE(
        device_iot_config_get_hex(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, 0);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "abcg";
    get_string_return_value = &string_value;
    EXPECT_FALSE(
        device_iot_config_get_hex(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, 0);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "f";
    get_string_return_value = &string_value;
    EXPECT_TRUE(
        device_iot_config_get_hex(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, 15);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "0";
    get_string_return_value = &string_value;
    EXPECT_TRUE(
        device_iot_config_get_hex(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, 0);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "1";
    get_string_return_value = &string_value;
    EXPECT_TRUE(
        device_iot_config_get_hex(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, 1);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "-e";
    get_string_return_value = &string_value;
    EXPECT_TRUE(
        device_iot_config_get_hex(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, -14);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "-f";
    get_string_return_value = &string_value;
    EXPECT_TRUE(
        device_iot_config_get_hex(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, -15);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "0x7fffffff";
    get_string_return_value = &string_value;
    EXPECT_TRUE(
        device_iot_config_get_hex(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, INT_MAX);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "-0x80000000";
    get_string_return_value = &string_value;
    EXPECT_TRUE(
        device_iot_config_get_hex(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, INT_MIN);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "0xffffffff";
    get_string_return_value = &string_value;
    EXPECT_TRUE(
        device_iot_config_get_hex(expected_section, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, -1);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_addr_get_hex) {
  const RawAddress peer_addr{};
  std::string actual_section, actual_key,
      expected_section = "00:00:00:00:00:00", expected_key = "def";
  int int_value = 0;
  std::string string_value;
  std::string* get_string_return_value = NULL;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        return get_string_return_value;
      };

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(
        DEVICE_IOT_CONFIG_ADDR_GET_HEX(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, 0);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "g";
    get_string_return_value = &string_value;

    EXPECT_FALSE(
        DEVICE_IOT_CONFIG_ADDR_GET_HEX(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, 0);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "f";
    get_string_return_value = &string_value;

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_GET_HEX(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, 15);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "0";
    get_string_return_value = &string_value;

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_GET_HEX(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, 0);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "1";
    get_string_return_value = &string_value;

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_GET_HEX(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, 1);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "-e";
    get_string_return_value = &string_value;

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_GET_HEX(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, -14);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "-f";
    get_string_return_value = &string_value;

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_GET_HEX(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, -15);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "0x7fffffff";
    get_string_return_value = &string_value;

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_GET_HEX(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, INT_MAX);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "-0x80000000";
    get_string_return_value = &string_value;

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_GET_HEX(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, INT_MIN);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    string_value = "0xffffffff";
    get_string_return_value = &string_value;

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_GET_HEX(peer_addr, expected_key, int_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(int_value, -1);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_set_hex) {
  std::string actual_key, expected_key = "def";
  std::string actual_section, expected_section = "00:00:00:00:00:00";
  std::string string_return_value;
  std::string new_string_value;
  std::string* get_string_return_value = NULL;
  int int_value, byte_num;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        return get_string_return_value;
      };

  test::mock::osi_config::config_set_string.body =
      [&](config_t* config, const std::string& section, const std::string& key,
          const std::string& value) { new_string_value = value; };

  test::mock::osi_alarm::alarm_set.body =
      [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
          void* data) {};

  {
    reset_mock_function_count_map();

    string_return_value = "01";
    int_value = 1;
    byte_num = 1;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(device_iot_config_set_hex(expected_section, expected_key,
                                          int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "0001";
    int_value = 1;
    byte_num = 2;
    get_string_return_value = &string_return_value;
    EXPECT_TRUE(device_iot_config_set_hex(expected_section, expected_key,
                                          int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "000001";
    int_value = 1;
    byte_num = 3;
    get_string_return_value = &string_return_value;
    EXPECT_TRUE(device_iot_config_set_hex(expected_section, expected_key,
                                          int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "00000001";
    int_value = 1;
    byte_num = 4;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(device_iot_config_set_hex(expected_section, expected_key,
                                          int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "";
    int_value = 1;
    byte_num = 0;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(device_iot_config_set_hex(expected_section, expected_key,
                                          int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "";
    int_value = 1;
    byte_num = 5;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(device_iot_config_set_hex(expected_section, expected_key,
                                          int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "ff";
    int_value = 1;
    byte_num = 1;
    get_string_return_value = &string_return_value;
    std::string expected_string_value = "01";

    EXPECT_TRUE(device_iot_config_set_hex(expected_section, expected_key,
                                          int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(new_string_value, expected_string_value);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
  test::mock::osi_config::config_set_string.body = {};
  test::mock::osi_alarm::alarm_set.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_addr_set_hex) {
  const RawAddress peer_addr{};
  std::string actual_key, expected_key = "def";
  std::string actual_section, expected_section = "00:00:00:00:00:00";
  std::string string_return_value;
  std::string old_string_value = string_return_value;
  std::string new_string_value;
  std::string* get_string_return_value = NULL;
  int int_value = 123456789;
  int byte_num = 1;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        return get_string_return_value;
      };

  test::mock::osi_config::config_set_string.body =
      [&](config_t* config, const std::string& section, const std::string& key,
          const std::string& value) { new_string_value = value; };

  test::mock::osi_alarm::alarm_set.body =
      [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
          void* data) {};

  {
    reset_mock_function_count_map();

    string_return_value = "01";
    int_value = 1;
    byte_num = 1;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_HEX(peer_addr, expected_key,
                                               int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "0001";
    int_value = 1;
    byte_num = 2;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_HEX(peer_addr, expected_key,
                                               int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "000001";
    int_value = 1;
    byte_num = 3;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_HEX(peer_addr, expected_key,
                                               int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "00000001";
    int_value = 1;
    byte_num = 4;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_HEX(peer_addr, expected_key,
                                               int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "";
    int_value = 1;
    byte_num = 0;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_HEX(peer_addr, expected_key,
                                               int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "";
    int_value = 1;
    byte_num = 5;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_HEX(peer_addr, expected_key,
                                               int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "ff";
    int_value = 1;
    byte_num = 1;
    get_string_return_value = &string_return_value;
    std::string expected_string_value = "01";

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_HEX(peer_addr, expected_key,
                                               int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(new_string_value, expected_string_value);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
  test::mock::osi_config::config_set_string.body = {};
  test::mock::osi_alarm::alarm_set.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_addr_set_hex_if_greater) {
  const RawAddress peer_addr{};
  std::string actual_key, expected_key = "def";
  std::string actual_section, expected_section = "00:00:00:00:00:00";
  std::string string_return_value;
  std::string old_string_value = string_return_value;
  std::string new_string_value;
  std::string* get_string_return_value = NULL;
  int int_value = 123456789;
  int byte_num = 1;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        return get_string_return_value;
      };

  test::mock::osi_config::config_set_string.body =
      [&](config_t* config, const std::string& section, const std::string& key,
          const std::string& value) { new_string_value = value; };

  test::mock::osi_alarm::alarm_set.body =
      [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
          void* data) {};

  {
    reset_mock_function_count_map();

    string_return_value = "00";
    int_value = 1;
    byte_num = 1;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_HEX_IF_GREATER(
        peer_addr, expected_key, int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 2);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "01";
    int_value = 1;
    byte_num = 1;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_HEX_IF_GREATER(
        peer_addr, expected_key, int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "02";
    int_value = 1;
    byte_num = 1;
    get_string_return_value = &string_return_value;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_HEX_IF_GREATER(
        peer_addr, expected_key, int_value, byte_num));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  test::mock::osi_config::config_get_string.body = {};
  test::mock::osi_config::config_set_string.body = {};
  test::mock::osi_alarm::alarm_set.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_get_str) {
  std::string actual_section, actual_key, expected_section = "abc",
                                          expected_key = "def";
  std::string actual_value_str;
  const std::string* actual_def_value = NULL;
  const std::string* return_value = NULL;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        actual_def_value = def_value;
        return return_value;
      };

  {
    reset_mock_function_count_map();

    int initial_size_bytes = 30;
    int size_bytes = initial_size_bytes;
    char get_value_str[size_bytes];
    EXPECT_FALSE(device_iot_config_get_str(expected_section, expected_key,
                                           get_value_str, &size_bytes));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(size_bytes, initial_size_bytes);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    int initial_size_bytes = 30;
    int size_bytes = initial_size_bytes;
    char get_value_str[size_bytes];

    actual_value_str = "abc";
    return_value = &actual_value_str;
    EXPECT_TRUE(device_iot_config_get_str(expected_section, expected_key,
                                          get_value_str, &size_bytes));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(size_bytes, (int)actual_value_str.length() + 1);
    EXPECT_TRUE(strncmp(get_value_str, actual_value_str.c_str(), size_bytes) ==
                0);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_set_str) {
  std::string actual_key, expected_key = "def";
  std::string actual_section, expected_section = "00:00:00:00:00:00";
  std::string input_value;
  std::string string_return_value;
  std::string old_string_value = string_return_value;
  std::string new_string_value;
  std::string* get_string_return_value = NULL;
  std::string str_value;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        return get_string_return_value;
      };

  test::mock::osi_config::config_set_string.body =
      [&](config_t* config, const std::string& section, const std::string& key,
          const std::string& value) { new_string_value = value; };

  test::mock::osi_alarm::alarm_set.body =
      [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
          void* data) {};

  {
    reset_mock_function_count_map();

    string_return_value = "01";
    get_string_return_value = &string_return_value;

    input_value = "01";
    EXPECT_TRUE(
        device_iot_config_set_str(expected_section, expected_key, input_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "02";
    get_string_return_value = &string_return_value;

    input_value = "01";
    EXPECT_TRUE(
        device_iot_config_set_str(expected_section, expected_key, input_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(new_string_value, input_value);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
  test::mock::osi_config::config_set_string.body = {};
  test::mock::osi_alarm::alarm_set.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_addr_set_str) {
  const RawAddress peer_addr{};
  std::string actual_key, expected_key = "def";
  std::string actual_section, expected_section = "00:00:00:00:00:00";
  std::string input_value;
  std::string string_return_value;
  std::string old_string_value = string_return_value;
  std::string new_string_value;
  std::string* get_string_return_value = NULL;
  std::string str_value;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        return get_string_return_value;
      };

  test::mock::osi_config::config_set_string.body =
      [&](config_t* config, const std::string& section, const std::string& key,
          const std::string& value) { new_string_value = value; };

  test::mock::osi_alarm::alarm_set.body =
      [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
          void* data) {};

  {
    reset_mock_function_count_map();

    string_return_value = "01";
    get_string_return_value = &string_return_value;
    input_value = "01";

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_SET_STR(peer_addr, expected_key, input_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
  }

  {
    reset_mock_function_count_map();

    string_return_value = "02";
    get_string_return_value = &string_return_value;
    input_value = "01";

    EXPECT_TRUE(
        DEVICE_IOT_CONFIG_ADDR_SET_STR(peer_addr, expected_key, input_value));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(new_string_value, input_value);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
  test::mock::osi_config::config_set_string.body = {};
  test::mock::osi_alarm::alarm_set.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_get_bin) {
  std::string actual_section, actual_key, expected_section = "abc",
                                          expected_key = "def";
  std::string actual_value_str;
  const std::string* actual_def_value = NULL;
  const std::string* return_value = NULL;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        actual_def_value = def_value;
        return return_value;
      };

  {
    reset_mock_function_count_map();

    size_t initial_size_bytes = 3;
    size_t size_bytes = initial_size_bytes;
    uint8_t value[size_bytes];

    EXPECT_FALSE(device_iot_config_get_bin(expected_section, expected_key,
                                           value, &size_bytes));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(size_bytes, initial_size_bytes);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    size_t initial_size_bytes = 3;
    size_t size_bytes = initial_size_bytes;
    uint8_t value[size_bytes];
    actual_value_str = "abc";
    return_value = &actual_value_str;

    EXPECT_FALSE(device_iot_config_get_bin(expected_section, expected_key,
                                           value, &size_bytes));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(size_bytes, initial_size_bytes);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    size_t initial_size_bytes = 3;
    size_t size_bytes = initial_size_bytes;
    uint8_t value[size_bytes];
    actual_value_str = "aabbccdd";
    return_value = &actual_value_str;

    EXPECT_FALSE(device_iot_config_get_bin(expected_section, expected_key,
                                           value, &size_bytes));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(size_bytes, initial_size_bytes);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    size_t initial_size_bytes = 3;
    size_t size_bytes = initial_size_bytes;
    uint8_t value[size_bytes];
    actual_value_str = "abcdefgh";
    return_value = &actual_value_str;

    EXPECT_FALSE(device_iot_config_get_bin(expected_section, expected_key,
                                           value, &size_bytes));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(size_bytes, initial_size_bytes);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();

    size_t initial_size_bytes = 3;
    size_t size_bytes = initial_size_bytes;
    uint8_t value[size_bytes];
    actual_value_str = "abcdef";
    return_value = &actual_value_str;

    EXPECT_TRUE(device_iot_config_get_bin(expected_section, expected_key, value,
                                          &size_bytes));
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(size_bytes, actual_value_str.length() / 2);

    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_get_bin_length) {
  std::string actual_section, actual_key, expected_section = "abc",
                                          expected_key = "def";
  std::string actual_value_str;
  const std::string* actual_def_value = NULL;
  const std::string* return_value = NULL;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        actual_def_value = def_value;
        return return_value;
      };

  {
    reset_mock_function_count_map();
    EXPECT_EQ(device_iot_config_get_bin_length(expected_section, expected_key),
              0u);
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();
    actual_value_str = "abc";
    return_value = &actual_value_str;

    EXPECT_EQ(device_iot_config_get_bin_length(expected_section, expected_key),
              0u);
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();
    actual_value_str = "aabbccdd";
    return_value = &actual_value_str;

    EXPECT_EQ(device_iot_config_get_bin_length(expected_section, expected_key),
              4u);
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();
    /* does not check if characters are correct*/
    actual_value_str = "abcdefgh";
    return_value = &actual_value_str;

    EXPECT_EQ(device_iot_config_get_bin_length(expected_section, expected_key),
              4u);
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  {
    reset_mock_function_count_map();
    actual_value_str = "abcdef";
    return_value = &actual_value_str;

    EXPECT_EQ(device_iot_config_get_bin_length(expected_section, expected_key),
              3u);
    EXPECT_TRUE(actual_def_value == NULL);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
  }

  test::mock::osi_config::config_get_string.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_set_bin) {
  std::string actual_key, expected_key = "def";
  std::string actual_section, expected_section = "00:00:00:00:00:00";
  std::string string_return_value;
  std::string old_string_value = string_return_value;
  std::string new_string_value;
  std::string* get_string_return_value = NULL;
  std::string str_value;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        return get_string_return_value;
      };

  test::mock::osi_config::config_set_string.body =
      [&](config_t* config, const std::string& section, const std::string& key,
          const std::string& value) { new_string_value = value; };

  test::mock::osi_alarm::alarm_set.body =
      [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
          void* data) {};

  test::mock::osi_allocator::osi_calloc.body = [&](size_t size) {
    return new char[size];
  };

  {
    reset_mock_function_count_map();
    string_return_value = "010203";
    get_string_return_value = &string_return_value;

    uint8_t input_value[] = {0x01, 0x02, 0x03};
    size_t length = sizeof(input_value);

    EXPECT_TRUE(device_iot_config_set_bin(expected_section, expected_key,
                                          input_value, length));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("osi_calloc"), 1);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
    EXPECT_EQ(get_func_call_count("osi_free"), 1);
  }

  {
    reset_mock_function_count_map();
    string_return_value = "\0";
    get_string_return_value = &string_return_value;

    uint8_t input_value[] = {0x01, 0x02, 0x03};
    size_t length = 0;

    EXPECT_TRUE(device_iot_config_set_bin(expected_section, expected_key,
                                          input_value, length));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("osi_calloc"), 1);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
    EXPECT_EQ(get_func_call_count("osi_free"), 1);
  }

  {
    reset_mock_function_count_map();
    string_return_value = "010101";
    get_string_return_value = &string_return_value;

    uint8_t input_value[] = {0x01, 0x02, 0x03};
    size_t length = sizeof(input_value);

    EXPECT_TRUE(device_iot_config_set_bin(expected_section, expected_key,
                                          input_value, length));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("osi_calloc"), 1);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
    EXPECT_EQ(get_func_call_count("osi_free"), 1);
  }

  {
    reset_mock_function_count_map();
    test::mock::osi_allocator::osi_calloc.body = [&](size_t size) {
      return nullptr;
    };

    uint8_t input_value[] = {0x01, 0x02, 0x03};
    size_t length = sizeof(input_value);

    EXPECT_FALSE(device_iot_config_set_bin(expected_section, expected_key,
                                           input_value, length));

    EXPECT_EQ(get_func_call_count("osi_calloc"), 1);
    EXPECT_EQ(get_func_call_count("config_get_string"), 0);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
    EXPECT_EQ(get_func_call_count("osi_free"), 0);
  }

  test::mock::osi_allocator::osi_calloc.body = {};
  test::mock::osi_allocator::osi_free.body = {};
  test::mock::osi_config::config_get_string.body = {};
  test::mock::osi_config::config_set_string.body = {};
  test::mock::osi_alarm::alarm_set.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_addr_set_bin) {
  const RawAddress peer_addr{};
  std::string actual_key, expected_key = "def";
  std::string actual_section, expected_section = "00:00:00:00:00:00";
  std::string string_return_value;
  std::string old_string_value = string_return_value;
  std::string new_string_value;
  std::string* get_string_return_value = NULL;
  std::string str_value;

  test::mock::osi_config::config_get_string.body =
      [&](const config_t& config, const std::string& section,
          const std::string& key, const std::string* def_value) {
        actual_section = section;
        actual_key = key;
        return get_string_return_value;
      };

  test::mock::osi_config::config_set_string.body =
      [&](config_t* config, const std::string& section, const std::string& key,
          const std::string& value) { new_string_value = value; };

  test::mock::osi_alarm::alarm_set.body =
      [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
          void* data) {};

  test::mock::osi_allocator::osi_calloc.body = [&](size_t size) {
    return new char[size];
  };

  {
    reset_mock_function_count_map();
    string_return_value = "010203";
    get_string_return_value = &string_return_value;

    uint8_t input_value[] = {0x01, 0x02, 0x03};
    size_t length = sizeof(input_value);

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_BIN(peer_addr, expected_key,
                                               input_value, length));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("osi_calloc"), 1);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
    EXPECT_EQ(get_func_call_count("osi_free"), 1);
  }

  {
    reset_mock_function_count_map();
    string_return_value = "\0";
    get_string_return_value = &string_return_value;

    uint8_t input_value[] = {0x01, 0x02, 0x03};
    size_t length = 0;

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_BIN(peer_addr, expected_key,
                                               input_value, length));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("osi_calloc"), 1);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
    EXPECT_EQ(get_func_call_count("osi_free"), 1);
  }

  {
    reset_mock_function_count_map();
    string_return_value = "010101";
    get_string_return_value = &string_return_value;

    uint8_t input_value[] = {0x01, 0x02, 0x03};
    size_t length = sizeof(input_value);

    EXPECT_TRUE(DEVICE_IOT_CONFIG_ADDR_SET_BIN(peer_addr, expected_key,
                                               input_value, length));
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("osi_calloc"), 1);
    EXPECT_EQ(get_func_call_count("config_get_string"), 1);
    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
    EXPECT_EQ(get_func_call_count("osi_free"), 1);
  }

  {
    reset_mock_function_count_map();
    test::mock::osi_allocator::osi_calloc.body = [&](size_t size) {
      return nullptr;
    };

    uint8_t input_value[] = {0x01, 0x02, 0x03};
    size_t length = sizeof(input_value);

    EXPECT_FALSE(DEVICE_IOT_CONFIG_ADDR_SET_BIN(peer_addr, expected_key,
                                                input_value, length));

    EXPECT_EQ(get_func_call_count("osi_calloc"), 1);
    EXPECT_EQ(get_func_call_count("config_get_string"), 0);
    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_set"), 0);
    EXPECT_EQ(get_func_call_count("osi_free"), 0);
  }

  test::mock::osi_allocator::osi_calloc.body = {};
  test::mock::osi_allocator::osi_free.body = {};
  test::mock::osi_config::config_get_string.body = {};
  test::mock::osi_config::config_set_string.body = {};
  test::mock::osi_alarm::alarm_set.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_remove) {
  std::string actual_key, expected_key = "def";
  std::string actual_section, expected_section = "00:00:00:00:00:00";
  bool return_value;

  test::mock::osi_config::config_remove_key.body =
      [&](config_t* config, const std::string& section,
          const std::string& key) {
        actual_section = section;
        actual_key = key;
        return return_value;
      };

  {
    reset_mock_function_count_map();

    return_value = false;

    EXPECT_EQ(device_iot_config_remove(expected_section, expected_key),
              return_value);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_remove_key"), 1);
  }

  {
    reset_mock_function_count_map();

    return_value = true;

    EXPECT_EQ(device_iot_config_remove(expected_section, expected_key),
              return_value);
    EXPECT_EQ(actual_section, expected_section);
    EXPECT_EQ(actual_key, expected_key);

    EXPECT_EQ(get_func_call_count("config_remove_key"), 1);
  }

  test::mock::osi_config::config_remove_key.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_save_async) {
  {
    reset_mock_function_count_map();

    device_iot_config_save_async();

    EXPECT_EQ(get_func_call_count("alarm_set"), 1);
  }
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_flush) {
  bool return_value;

  test::mock::osi_alarm::alarm_is_scheduled.body =
      [&](const alarm_t* alarm) -> bool { return return_value; };

  {
    reset_mock_function_count_map();

    return_value = false;

    device_iot_config_flush();

    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("alarm_is_scheduled"), 1);
    EXPECT_EQ(get_func_call_count("alarm_cancel"), 1);
    EXPECT_EQ(get_func_call_count("config_save"), 1);
  }

  {
    reset_mock_function_count_map();

    return_value = true;

    device_iot_config_flush();

    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("alarm_is_scheduled"), 1);
    EXPECT_EQ(get_func_call_count("alarm_cancel"), 1);
    EXPECT_EQ(get_func_call_count("config_save"), 1);
  }

  test::mock::osi_alarm::alarm_is_scheduled.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_clear) {
  config_t* config_new_empty_return_value;
  bool config_save_return_value;

  test::mock::osi_alarm::alarm_cancel.body = [&](alarm_t* alarm) {};

  test::mock::osi_config::config_new_empty.body = [&]() {
    return std::unique_ptr<config_t>(config_new_empty_return_value);
  };

  test::mock::osi_config::config_save.body =
      [&](const config_t& config, const std::string& filename) -> bool {
    return config_save_return_value;
  };

  {
    reset_mock_function_count_map();

    config_new_empty_return_value = new config_t();
    config_save_return_value = false;

    EXPECT_FALSE(device_iot_config_clear());

    EXPECT_EQ(get_func_call_count("config_new_empty"), 1);
    EXPECT_EQ(get_func_call_count("alarm_cancel"), 1);
    EXPECT_EQ(get_func_call_count("config_save"), 1);
  }

  {
    reset_mock_function_count_map();

    config_new_empty_return_value = new config_t();
    config_save_return_value = true;

    EXPECT_TRUE(device_iot_config_clear());

    EXPECT_EQ(get_func_call_count("config_new_empty"), 1);
    EXPECT_EQ(get_func_call_count("alarm_cancel"), 1);
    EXPECT_EQ(get_func_call_count("config_save"), 1);
  }

  {
    reset_mock_function_count_map();

    config_new_empty_return_value = NULL;

    EXPECT_FALSE(device_iot_config_clear());

    EXPECT_EQ(get_func_call_count("config_new_empty"), 1);
    EXPECT_EQ(get_func_call_count("alarm_cancel"), 1);
    EXPECT_EQ(get_func_call_count("config_save"), 0);
  }

  test::mock::osi_alarm::alarm_cancel.body = {};
  test::mock::osi_config::config_new_empty.body = {};
  test::mock::osi_config::config_save.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_timer_save_cb) {
  {
    reset_mock_function_count_map();

    device_iot_config_timer_save_cb(NULL);

    EXPECT_EQ(get_func_call_count("btif_transfer_context"), 1);
  }
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_set_modified_time) {
  {
    reset_mock_function_count_map();

    device_iot_config_set_modified_time();

    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
  }
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_get_device_num) {
  {
    config_t config;
    auto num = device_iot_config_get_device_num(config);
    EXPECT_EQ(num, 0);
  }

  {
    section_t section1 = {.name = "00:01:02:03:04:05"};
    section_t section2 = {.name = "01:01:01:01:01:01"};
    section_t section3 = {.name = "00:00:00:00:00:00"};
    section_t section4 = {.name = ""};
    config_t config;
    config.sections.push_back(section1);
    config.sections.push_back(section2);
    config.sections.push_back(section3);
    config.sections.push_back(section4);
    auto num = device_iot_config_get_device_num(config);
    EXPECT_EQ(num, 3);
  }
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_restrict_device_num) {
  section_t section = {.name = "00:01:02:03:04:05"};

  {
    config_t config;

    EXPECT_EQ(device_iot_config_get_device_num(config), 0);
    device_iot_config_restrict_device_num(config);
    EXPECT_EQ(device_iot_config_get_device_num(config), 0);
  }

  {
    int section_count = DEVICES_MAX_NUM_IN_IOT_INFO_FILE;
    int expected_count = section_count;
    config_t config;
    for (int i = 0; i < section_count; ++i) {
      config.sections.push_back(section);
    }

    EXPECT_EQ(device_iot_config_get_device_num(config), section_count);
    device_iot_config_restrict_device_num(config);
    EXPECT_EQ(device_iot_config_get_device_num(config), expected_count);
  }

  {
    int section_count = DEVICES_MAX_NUM_IN_IOT_INFO_FILE + 1;
    int expected_count = DEVICES_MAX_NUM_IN_IOT_INFO_FILE - DEVICES_NUM_MARGIN;
    config_t config;
    for (int i = 0; i < section_count; ++i) {
      config.sections.push_back(section);
    }

    EXPECT_EQ(device_iot_config_get_device_num(config), section_count);
    device_iot_config_restrict_device_num(config);
    EXPECT_EQ(device_iot_config_get_device_num(config), expected_count);
  }

  {
    int section_count = 2 * DEVICES_MAX_NUM_IN_IOT_INFO_FILE;
    int expected_count = DEVICES_MAX_NUM_IN_IOT_INFO_FILE - DEVICES_NUM_MARGIN;
    config_t config;
    for (int i = 0; i < section_count; ++i) {
      config.sections.push_back(section);
    }

    EXPECT_EQ(device_iot_config_get_device_num(config), section_count);
    device_iot_config_restrict_device_num(config);
    EXPECT_EQ(device_iot_config_get_device_num(config), expected_count);
  }
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_compare_key) {
  {
    entry_t first =
                {
                    .key = "NotProfile/a",
                },
            second = {
                .key = "NotProfile/b",
            };

    EXPECT_TRUE(device_iot_config_compare_key(first, second));
  }

  {
    entry_t first =
                {
                    .key = "Profile/a",
                },
            second = {
                .key = "Profile/b",
            };

    EXPECT_TRUE(device_iot_config_compare_key(first, second));
  }

  {
    entry_t first =
                {
                    .key = "Profile/b",
                },
            second = {
                .key = "Profile/a",
            };

    EXPECT_FALSE(device_iot_config_compare_key(first, second));
  }

  {
    entry_t first =
                {
                    .key = "Profile/b",
                },
            second = {
                .key = "NotProfile/a",
            };

    EXPECT_FALSE(device_iot_config_compare_key(first, second));
  }

  {
    entry_t first =
                {
                    .key = "NotProfile/b",
                },
            second = {
                .key = "Profile/a",
            };

    EXPECT_TRUE(device_iot_config_compare_key(first, second));
  }
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_write) {
  test::mock::osi_config::config_save.body =
      [&](const config_t& config, const std::string& filename) -> bool {
    return true;
  };

  {
    reset_mock_function_count_map();

    int event = IOT_CONFIG_FLUSH_EVT;
    device_iot_config_write(event, NULL);

    EXPECT_EQ(get_func_call_count("config_set_string"), 0);
    EXPECT_EQ(get_func_call_count("config_save"), 1);
  }

  {
    reset_mock_function_count_map();

    int event = IOT_CONFIG_SAVE_TIMER_FIRED_EVT;
    device_iot_config_write(event, NULL);

    EXPECT_EQ(get_func_call_count("config_set_string"), 1);
    EXPECT_EQ(get_func_call_count("config_save"), 1);
  }
  test::mock::osi_config::config_save.body = {};
}

TEST_F(DeviceIotConfigTest, test_device_debug_iot_config_dump) {
  {
    errno = 0;
    int fd = -1;
    const int BUF_SIZE = 100;
    char buf[BUF_SIZE] = {0};

    fd = open(IOT_CONFIG_FILE_PATH, O_CREAT | O_RDWR | O_TRUNC | O_CLOEXEC,
              S_IRUSR | S_IWUSR);
    EXPECT_TRUE(fd > 0);
    EXPECT_EQ(errno, 0);

    lseek(fd, 0, SEEK_SET);
    auto bytes_read = read(fd, buf, BUF_SIZE);
    EXPECT_EQ(bytes_read, 0);
    EXPECT_EQ(errno, 0);
    lseek(fd, 0, SEEK_SET);

    device_debug_iot_config_dump(fd);

    lseek(fd, 0, SEEK_SET);
    bytes_read = read(fd, buf, BUF_SIZE);
    EXPECT_TRUE(bytes_read > 0);
    EXPECT_EQ(errno, 0);
    lseek(fd, 0, SEEK_SET);

    close(fd);
  }
}

TEST_F(DeviceIotConfigTest, test_device_iot_config_is_factory_reset) {
  bool return_value;
  test::mock::osi_properties::osi_property_get_bool.body =
      [&](const char* key, bool default_value) -> bool { return return_value; };

  {
    return_value = false;
    EXPECT_FALSE(device_iot_config_is_factory_reset());
  }

  {
    return_value = true;
    EXPECT_TRUE(device_iot_config_is_factory_reset());
  }
}

TEST_F(DeviceIotConfigTest, test_device_debug_iot_config_delete_files) {
  {
    errno = 0;
    int file_fd = -1;
    int backup_fd = -1;

    file_fd = open(IOT_CONFIG_FILE_PATH, O_CREAT | O_RDWR | O_TRUNC | O_CLOEXEC,
                   S_IRUSR | S_IWUSR);
    EXPECT_TRUE(file_fd > 0);
    EXPECT_EQ(errno, 0);

    backup_fd = open(IOT_CONFIG_BACKUP_PATH,
                     O_CREAT | O_RDWR | O_TRUNC | O_CLOEXEC, S_IRUSR | S_IWUSR);
    EXPECT_TRUE(backup_fd > 0);
    EXPECT_EQ(errno, 0);

    EXPECT_EQ(access(IOT_CONFIG_FILE_PATH, F_OK), 0);
    EXPECT_EQ(access(IOT_CONFIG_BACKUP_PATH, F_OK), 0);

    device_iot_config_delete_files();

    errno = 0;
    EXPECT_EQ(access(IOT_CONFIG_FILE_PATH, F_OK), -1);
    EXPECT_EQ(errno, ENOENT);

    errno = 0;
    EXPECT_EQ(access(IOT_CONFIG_BACKUP_PATH, F_OK), -1);
    EXPECT_EQ(errno, ENOENT);
  }
}
class DeviceIotConfigDisabledTest : public testing::Test {
 protected:
  void SetUp() override {
    bluetooth::common::InitFlags::Load(test_flags_feature_disabled);

    test::mock::osi_alarm::alarm_new.body = [&](const char* name) -> alarm_t* {
      return &placeholder_alarm;
    };

    test::mock::osi_alarm::alarm_set.body =
        [&](alarm_t* alarm, uint64_t interval_ms, alarm_callback_t cb,
            void* data) { return; };

    test::mock::osi_alarm::alarm_free.body = [](alarm_t* alarm) {};

    test::mock::osi_alarm::alarm_is_scheduled.body =
        [&](const alarm_t* alarm) -> bool { return false; };

    test::mock::osi_future::future_new_immediate.body =
        [&](void* value) -> future_t* { return &placeholder_future; };

    test::mock::osi_config::config_new_empty.body =
        [&]() -> std::unique_ptr<config_t> {
      return std::make_unique<config_t>();
    };

    test::mock::osi_config::config_new.body =
        [&](const char* filename) -> std::unique_ptr<config_t> {
      return std::make_unique<config_t>();
    };

    test::mock::osi_config::config_get_int.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key, int def_value) { return def_value; };

    test::mock::osi_config::config_set_int.body =
        [&](config_t* config, const std::string& section,
            const std::string& key, int value) { return; };

    test::mock::osi_config::config_get_string.body =
        [&](const config_t& config, const std::string& section,
            const std::string& key,
            const std::string* def_value) { return def_value; };

    test::mock::osi_config::config_set_string.body =
        [&](config_t* config, const std::string& section,
            const std::string& key, const std::string& value) { return; };

    test::mock::osi_allocator::osi_free.body = [&](void* ptr) {};

    device_iot_config_module_init();
    device_iot_config_module_start_up();

    reset_mock_function_count_map();
  }

  void TearDown() override {
    test::mock::osi_alarm::alarm_new = {};
    test::mock::osi_alarm::alarm_set = {};
    test::mock::osi_alarm::alarm_free = {};
    test::mock::osi_alarm::alarm_is_scheduled = {};
    test::mock::osi_future::future_new_immediate = {};
    test::mock::osi_properties::osi_property_get = {};
    test::mock::osi_config::config_new_empty = {};
    test::mock::osi_config::config_new = {};
    test::mock::osi_config::config_get_int = {};
    test::mock::osi_config::config_set_int = {};
    test::mock::osi_config::config_get_string = {};
    test::mock::osi_config::config_set_string = {};
    test::mock::osi_allocator::osi_free = {};
  }
};

TEST_F(DeviceIotConfigDisabledTest, test_device_iot_config_disabled) {
  const RawAddress peer_addr{};
  std::string section, key, value_str;
  int value_int{};

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_has_section(section));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_exist(section, key));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_get_int(section, key, value_int));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(DEVICE_IOT_CONFIG_ADDR_GET_INT(peer_addr, key, value_int));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_set_int(section, key, 0));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(DEVICE_IOT_CONFIG_ADDR_SET_INT(peer_addr, key, 0));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_int_add_one(section, key));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(peer_addr, key));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_get_hex(section, key, value_int));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(DEVICE_IOT_CONFIG_ADDR_GET_HEX(peer_addr, key, value_int));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_set_hex(section, key, 0, 0));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(DEVICE_IOT_CONFIG_ADDR_SET_HEX(peer_addr, key, 0, 0));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(
        DEVICE_IOT_CONFIG_ADDR_SET_HEX_IF_GREATER(peer_addr, key, 0, 0));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_get_str(section, key, NULL, NULL));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_set_str(section, key, value_str));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(DEVICE_IOT_CONFIG_ADDR_SET_STR(peer_addr, key, value_str));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_get_bin(section, key, NULL, NULL));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_set_bin(section, key, NULL, 0));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(DEVICE_IOT_CONFIG_ADDR_SET_BIN(peer_addr, key, NULL, 0));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_FALSE(device_iot_config_remove(section, key));
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_EQ(device_iot_config_get_bin_length(section, key), 0u);
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    device_iot_config_flush();
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    EXPECT_TRUE(device_iot_config_clear());
    EXPECT_EQ(get_func_call_size(), 0);
  }

  {
    reset_mock_function_count_map();

    device_debug_iot_config_dump(0);
    EXPECT_EQ(get_func_call_size(), 0);
  }
}
