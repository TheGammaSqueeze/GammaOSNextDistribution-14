/*
 * Copyright 2021 The Android Open Source Project
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <string.h>

#include <cstdint>
#include <map>
#include <memory>
#include <string>

#include "bta/pan/bta_pan_int.h"
#include "test/common/main_handler.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_stack_pan_api.h"

using namespace testing;

namespace {

struct UuidPiece {
  uint16_t uuid16;
  bool adding;
};

struct EventPiece {
  tBTA_PAN_EVT event;
  tBTA_PAN data;
};

std::function<void(tBTA_PAN_EVT, tBTA_PAN*)> bta_pan_event_closure;
void BTA_PAN_CBACK(tBTA_PAN_EVT event, tBTA_PAN* p_data) {
  bta_pan_event_closure(event, p_data);
}

std::function<void(uint16_t, bool)> bta_sys_eir_closure;
void BTA_SYS_EIR_CBACK(uint16_t uuid16, bool adding) {
  bta_sys_eir_closure(uuid16, adding);
}

constexpr tBTA_PAN kNoData = {};

}  // namespace

class BtaPanTest : public ::testing::Test {
 protected:
  void SetUp() override {
    uuids.clear();
    main_thread_start_up();
    bta_pan_event_closure = [this](tBTA_PAN_EVT event, tBTA_PAN* data) {
      events.push_back({
          .event = event,
          .data = (data == nullptr) ? kNoData : *data,
      });
    };
    tBTA_PAN_DATA data = {
        .api_enable =
            {
                .p_cback = BTA_PAN_CBACK,
            },
    };
    bta_sys_eir_closure = [this](uint16_t uuid16, bool adding) {
      uuids.push_back({
          .uuid16 = uuid16,
          .adding = adding,
      });
    };
    bta_pan_enable(&data);
    sync_main_handler();
    auto e = events.front();
    events.pop_front();
    ASSERT_EQ(BTA_PAN_ENABLE_EVT, e.event);
  }

  void TearDown() override {
    bta_pan_disable();
    sync_main_handler();
    main_thread_shut_down();
  }

  std::deque<struct EventPiece> events;
  std::deque<struct UuidPiece> uuids;
};

class BtaPanRegisteredTest : public BtaPanTest {
 protected:
  void SetUp() {
    BtaPanTest::SetUp();
    bta_sys_eir_register(BTA_SYS_EIR_CBACK);
  }

  void TearDown() {
    bta_sys_eir_unregister();
    BtaPanTest::TearDown();
  }
};

TEST_F(BtaPanTest, nop) {}

TEST_F(BtaPanRegisteredTest, BTA_PanSetRole_Null) {
  tBTA_PAN_ROLE role = BTA_PAN_ROLE_PANU | BTA_PAN_ROLE_NAP;
  tBTA_PAN_ROLE_INFO user_info = {
      .p_srv_name = std::string(),
      .app_id = 12,
  };

  tBTA_PAN_ROLE_INFO nap_info = {
      .p_srv_name = std::string(),
      .app_id = 34,
  };

  BTA_PanSetRole(role, user_info, nap_info);

  // Wait for main thread to complete
  sync_main_handler();

  ASSERT_EQ(12, bta_pan_cb.app_id[0]);
  ASSERT_EQ(0, bta_pan_cb.app_id[1]);
  ASSERT_EQ(34, bta_pan_cb.app_id[2]);

  ASSERT_EQ(2U, uuids.size());
  ASSERT_EQ(0x1116, uuids[0].uuid16);
  ASSERT_EQ(true, uuids[0].adding);
  ASSERT_EQ(0x1115, uuids[1].uuid16);
  ASSERT_EQ(true, uuids[1].adding);
}

TEST_F(BtaPanRegisteredTest, BTA_PanSetRole_WithNames) {
  tBTA_PAN_ROLE role = BTA_PAN_ROLE_PANU | BTA_PAN_ROLE_NAP;
  tBTA_PAN_ROLE_INFO user_info = {
      .p_srv_name = "TestPanUser",
      .app_id = 12,
  };
  tBTA_PAN_ROLE_INFO nap_info = {
      .p_srv_name = "TestPanNap",
      .app_id = 34,
  };

  uint8_t stack_pan_role;
  std::string stack_pan_user_name;
  std::string stack_pan_nap_name;

  test::mock::stack_pan_api::PAN_SetRole.body =
      [&stack_pan_role, &stack_pan_user_name, &stack_pan_nap_name](
          uint8_t role, std::string user_name,
          std::string nap_name) -> tPAN_RESULT {
    stack_pan_role = role;
    stack_pan_user_name = user_name;
    stack_pan_nap_name = nap_name;
    return PAN_SUCCESS;
  };

  bta_sys_eir_register(BTA_SYS_EIR_CBACK);
  BTA_PanSetRole(role, user_info, nap_info);

  // Wait for main thread to complete
  sync_main_handler();

  ASSERT_EQ(1UL, events.size());
  auto e = events.front();
  events.pop_front();
  ASSERT_EQ(BTA_PAN_SET_ROLE_EVT, e.event);
  ASSERT_EQ(BTA_PAN_ROLE_PANU | BTA_PAN_ROLE_NAP, e.data.set_role.role);
  ASSERT_EQ(BTA_PAN_SUCCESS, e.data.set_role.status);

  ASSERT_EQ(BTA_PAN_ROLE_PANU | BTA_PAN_ROLE_NAP, stack_pan_role);
  ASSERT_THAT("TestPanUser", StrEq(stack_pan_user_name));
  ASSERT_THAT("TestPanNap", StrEq(stack_pan_nap_name));

  test::mock::stack_pan_api::PAN_SetRole = {};
}

constexpr size_t kBtaServiceNameLen = static_cast<size_t>(BTA_SERVICE_NAME_LEN);

TEST_F(BtaPanRegisteredTest, BTA_PanSetRole_WithLongNames) {
  tBTA_PAN_ROLE role = BTA_PAN_ROLE_PANU | BTA_PAN_ROLE_NAP;
  tBTA_PAN_ROLE_INFO user_info = {
      .p_srv_name = std::string(200, 'A'),
      .app_id = 12,
  };
  ASSERT_EQ(200UL, user_info.p_srv_name.size());

  tBTA_PAN_ROLE_INFO nap_info = {
      .p_srv_name = std::string(201, 'A'),
      .app_id = 34,
  };
  ASSERT_EQ(201UL, nap_info.p_srv_name.size());

  uint8_t stack_pan_role;
  std::string stack_pan_user_name;
  std::string stack_pan_nap_name;

  test::mock::stack_pan_api::PAN_SetRole.body =
      [&stack_pan_role, &stack_pan_user_name, &stack_pan_nap_name](
          uint8_t role, std::string user_name,
          std::string nap_name) -> tPAN_RESULT {
    stack_pan_role = role;
    stack_pan_user_name = user_name;
    stack_pan_nap_name = nap_name;
    return PAN_SUCCESS;
  };

  bta_sys_eir_register(BTA_SYS_EIR_CBACK);
  BTA_PanSetRole(role, user_info, nap_info);

  // Wait for main thread to complete
  sync_main_handler();

  ASSERT_EQ(1UL, events.size());
  auto e = events.front();
  events.pop_front();
  ASSERT_EQ(BTA_PAN_SET_ROLE_EVT, e.event);
  ASSERT_EQ(BTA_PAN_ROLE_PANU | BTA_PAN_ROLE_NAP, e.data.set_role.role);
  ASSERT_EQ(BTA_PAN_SUCCESS, e.data.set_role.status);

  ASSERT_EQ(BTA_PAN_ROLE_PANU | BTA_PAN_ROLE_NAP, stack_pan_role);

  ASSERT_EQ(kBtaServiceNameLen, stack_pan_user_name.size());
  ASSERT_EQ(kBtaServiceNameLen, stack_pan_nap_name.size());

  ASSERT_THAT(stack_pan_user_name, StrEq(std::string(kBtaServiceNameLen, 'A')));
  ASSERT_THAT(stack_pan_user_name,
              StrEq("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
  ASSERT_THAT(stack_pan_nap_name, StrEq(std::string(kBtaServiceNameLen, 'A')));
  ASSERT_THAT(stack_pan_nap_name, StrEq("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));

  test::mock::stack_pan_api::PAN_SetRole = {};
}
