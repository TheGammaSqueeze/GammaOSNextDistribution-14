/*
 * Copyright 2022 The Android Open Source Project
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

#include "gatt_api.h"

#include <base/logging.h>
#include <gtest/gtest.h>

#include "btm/btm_dev.h"
#include "btm/btm_sec_cb.h"
#include "gatt/gatt_int.h"
#include "osi/include/allocator.h"

extern tBTM_SEC_CB btm_sec_cb;

static const size_t QUEUE_SIZE_MAX = 10;

static tBTM_SEC_DEV_REC* make_bonded_ble_device(const RawAddress& bda,
                                                const RawAddress& rra) {
  tBTM_SEC_DEV_REC* dev = btm_sec_allocate_dev_rec();
  dev->sec_rec.sec_flags |= BTM_SEC_LE_LINK_KEY_KNOWN;
  dev->bd_addr = bda;
  dev->ble.pseudo_addr = rra;
  dev->sec_rec.ble_keys.key_type =
      BTM_LE_KEY_PID | BTM_LE_KEY_PENC | BTM_LE_KEY_LENC;
  return dev;
}

static tBTM_SEC_DEV_REC* make_bonded_dual_device(const RawAddress& bda,
                                                 const RawAddress& rra) {
  tBTM_SEC_DEV_REC* dev = make_bonded_ble_device(bda, rra);
  dev->sec_rec.sec_flags |= BTM_SEC_LINK_KEY_KNOWN;
  return dev;
}

extern std::optional<bool> OVERRIDE_GATT_LOAD_BONDED;

class GattApiTest : public ::testing::Test {
 protected:
  GattApiTest() = default;

  virtual ~GattApiTest() = default;

  void SetUp() override {
    btm_sec_cb.sec_dev_rec = list_new(osi_free);
    gatt_cb.srv_chg_clt_q = fixed_queue_new(QUEUE_SIZE_MAX);
    logging::SetMinLogLevel(-2);
  }

  void TearDown() override { list_free(btm_sec_cb.sec_dev_rec); }
};

static const RawAddress SAMPLE_PUBLIC_BDA = {
    {0x00, 0x00, 0x11, 0x22, 0x33, 0x44}};

static const RawAddress SAMPLE_RRA_BDA = {{0xAA, 0xAA, 0x11, 0x22, 0x33, 0x44}};

TEST_F(GattApiTest, test_gatt_load_bonded_ble_only) {
  OVERRIDE_GATT_LOAD_BONDED = std::optional{true};
  make_bonded_ble_device(SAMPLE_PUBLIC_BDA, SAMPLE_RRA_BDA);

  gatt_load_bonded();

  ASSERT_TRUE(gatt_is_bda_in_the_srv_chg_clt_list(SAMPLE_RRA_BDA));
  ASSERT_FALSE(gatt_is_bda_in_the_srv_chg_clt_list(SAMPLE_PUBLIC_BDA));
  OVERRIDE_GATT_LOAD_BONDED.reset();
}

TEST_F(GattApiTest, test_gatt_load_bonded_dual) {
  OVERRIDE_GATT_LOAD_BONDED = std::optional{true};
  make_bonded_dual_device(SAMPLE_PUBLIC_BDA, SAMPLE_RRA_BDA);

  gatt_load_bonded();

  ASSERT_TRUE(gatt_is_bda_in_the_srv_chg_clt_list(SAMPLE_RRA_BDA));
  ASSERT_TRUE(gatt_is_bda_in_the_srv_chg_clt_list(SAMPLE_PUBLIC_BDA));
  OVERRIDE_GATT_LOAD_BONDED.reset();
}
