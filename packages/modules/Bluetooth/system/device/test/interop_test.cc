/******************************************************************************
 *
 *  Copyright 2015 Google, Inc.
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

#include "device/include/interop.h"

#include <gtest/gtest.h>

#include "btcore/include/module.h"
#include "device/include/interop_config.h"
#include "types/raw_address.h"

#ifndef __ANDROID__
#include <base/files/file_util.h>
#include <unistd.h>

#include <filesystem>
#include <system_error>

static const std::filesystem::path kStaticConfigFileConfigFile =
    std::filesystem::temp_directory_path() / "interop_database.conf";

static const char* INTEROP_STATIC_FILE_PATH =
    kStaticConfigFileConfigFile.c_str();
static const char INTEROP_STATIC_FILE_CONTENT[] =
    "                                                                                        \n\
#Disable secure connections                                                                  \n\
#This is for pre BT 4.1/2 devices that do not handle secure mode very well.                  \n\
[INTEROP_DISABLE_LE_SECURE_CONNECTIONS]                                                      \n\
08:62:66 = Address_Based                                                                     \n\
38:2C:4A:E6 = Address_Based                                                                  \n\
                                                                                             \n\
# Disable automatic pairing with headsets/car-kits                                           \n\
# Some car kits do not react kindly to a failed pairing attempt and                          \n\
# do not allow immediate re-pairing. Denylist these so that the initial                      \n\
# pairing attempt makes it to the user instead                                               \n\
[INTEROP_DISABLE_AUTO_PAIRING]                                                               \n\
34:C7:31 = Address_Based                                                                     \n\
Audi = Name_Based                                                                            \n\
BMW = Name_Based                                                                             \n\
                                                                                             \n\
# Devices requiring this workaround do not handle Bluetooth Absolute Volume                  \n\
# control correctly, leading to undesirable (potentially harmful) volume levels              \n\
# or general lack of controlability.                                                         \n\
[INTEROP_DISABLE_ABSOLUTE_VOLUME]                                                            \n\
A0:E9:DB = Address_Based                                                                     \n\
00:0f:59:50:00:00-00:0f:59:6f:ff:ff = Address_Range_Based                                    \n\
                                                                                             \n\
# HID Keyboards that claim support for multitouch functionality have issue with              \n\
# normal functioning of keyboard because of issues in USB HID kernel driver.                 \n\
# To avoid degrading the user experience with those devices, digitizer record                \n\
# is removed from the report descriptor.                                                     \n\
[INTEROP_REMOVE_HID_DIG_DESCRIPTOR]                                                          \n\
Motorola Keyboard KZ500 = Name_Based                                                         \n\
0x22b8-0x093D = Vndr_Prdt_Based                                                              \n\
                                                                                             \n\
# some remote hid devices cannot work properly as they laod special hid usb driver in kernel,\n\
# so modify their vid/pid so that generic hid driver are loaded.                             \n\
[INTEROP_CHANGE_HID_VID_PID]                                                                 \n\
CK87BT = Name_Based                                                                          \n\
0x05ac-0x0255 = Vndr_Prdt_Based                                                              \n\
                                                                                             \n\
# Some HID devices have problematic behaviour where when hid link is in Sniff                \n\
# and DUT is in Peripheral role for SCO link ( not eSCO) any solution cannot maintain        \n\
# the link as  SCO scheduling over a short period will overlap with Sniff link due to        \n\
# peripheral drift.                                                                          \n\
# To avoid degrading the user experience with those devices, sniff is disabled from          \n\
# link policy when sco is active, and enabled when sco is disabled.                          \n\
[INTEROP_DISABLE_SNIFF_DURING_SCO]                                                           \n\
20:4C:10 = Address_Based                                                                     \n\
0x004C = Manufacturer_Based                                                                  \n\
                                                                                             \n\
# Devices requiring this workaround do not handle SSR max latency values as mentioned,       \n\
# in their SDP HID Record properly and lead to connection timeout or lags. To prevent        \n\
# such scenarios, device requiring this workaorund need to use specific ssr max latency      \n\
# values.                                                                                    \n\
[INTEROP_UPDATE_HID_SSR_MAX_LAT]                                                             \n\
00:1B:DC-0x0012 = SSR_Max_Lat_Based                                                          \n\
DC:2C:26-0x0000 = SSR_Max_Lat_Based                                                          \n\
";
#endif

extern const module_t interop_module;

class InteropTest : public ::testing::Test {
 protected:
  virtual void SetUp() override {
#ifndef __ANDROID__
    FILE* fp = fopen(INTEROP_STATIC_FILE_PATH, "wte");
    ASSERT_NE(fp, nullptr);
    ASSERT_EQ(fwrite(INTEROP_STATIC_FILE_CONTENT, 1,
                     sizeof(INTEROP_STATIC_FILE_CONTENT), fp),
              sizeof(INTEROP_STATIC_FILE_CONTENT));
    // Force data to be flushed on disk and not only in user-space
    ASSERT_EQ(fsync(fileno(fp)), 0)
        << "Associated ERRNO error is: " << strerror(errno);
    ASSERT_EQ(fclose(fp), 0);
#endif
  }
  virtual void TearDown() override {
#ifndef __ANDROID__
    std::error_code ec;
    EXPECT_TRUE(std::filesystem::remove(kStaticConfigFileConfigFile, ec))
        << "Associated error is: " << ec;
#endif
  }
};

TEST_F(InteropTest, test_lookup_hit) {
  module_init(&interop_module);

  RawAddress test_address;

  RawAddress::FromString("38:2c:4a:e6:67:89", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, &test_address));

  RawAddress::FromString("34:c7:31:12:34:56", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DISABLE_AUTO_PAIRING, &test_address));

#ifdef __ANDROID__
  RawAddress::FromString("9c:df:03:12:34:56", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_AUTO_RETRY_PAIRING, &test_address));

  RawAddress::FromString("a0:e9:db:e6:67:89", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_ABSOLUTE_VOLUME, &test_address));

  RawAddress::FromString("00:0f:f6:e6:67:89", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_KEYBOARD_REQUIRES_FIXED_PIN, &test_address));

  RawAddress::FromString("00:18:91:12:34:56", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_2MBPS_LINK_ONLY, &test_address));

  RawAddress::FromString("00:12:a1:e6:67:89", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_AUTH_FOR_HID_POINTING, &test_address));

  RawAddress::FromString("20:4c:10:12:34:56", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_SNIFF_DURING_SCO, &test_address));

  RawAddress::FromString("00:14:09:e6:67:89", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_INCREASE_AG_CONN_TIMEOUT, &test_address));

  RawAddress::FromString("fc:c2:de:12:34:56", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DISABLE_ROLE_SWITCH, &test_address));

  RawAddress::FromString("28:a1:83:9c:20:a8", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DISABLE_AAC_CODEC, &test_address));

  RawAddress::FromString("28:83:35:7a:5f:23", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DISABLE_AAC_VBR_CODEC, &test_address));

  RawAddress::FromString("b8:ad:3e:12:34:56", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_ENABLE_AAC_CODEC, &test_address));

  RawAddress::FromString("ac:fd:ce:e6:67:89", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_PCE_SDP_AFTER_PAIRING, &test_address));

  RawAddress::FromString("98:7b:f3:12:34:56", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DISABLE_HF_INDICATOR, &test_address));

  RawAddress::FromString("04:52:c7:e6:67:89", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DELAY_SCO_FOR_MT_CALL, &test_address));

  RawAddress::FromString("04:52:c7:12:34:56", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DELAY_SCO_FOR_MT_CALL, &test_address));

  RawAddress::FromString("00:08:8a:f0:1d:8a", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_CODEC_NEGOTIATION, &test_address));

  RawAddress::FromString("a0:56:b2:4f:86:a8", test_address);
  EXPECT_TRUE(interop_match_addr(
      INTEROP_DISABLE_PLAYER_APPLICATION_SETTING_CMDS, &test_address));

  RawAddress::FromString("a0:14:3d:e6:67:89", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DISABLE_CONNECTION_AFTER_COLLISION,
                                 &test_address));

  RawAddress::FromString("38:2c:4a:c9:34:56", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_HID_PREF_CONN_SUP_TIMEOUT_3S, &test_address));

  RawAddress::FromString("00:1d:86:e6:67:89", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_AVDTP_RECONFIGURE, &test_address));

  RawAddress::FromString("2c:dc:ad:08:91:89", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_AVRCP_BROWSE_OPEN_CHANNEL_COLLISION,
                                 &test_address));

  RawAddress::FromString("10:b7:f6:03:38:b0", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_SNIFF_DURING_CALL, &test_address));

  RawAddress::FromString("00:0e:9f:12:34:56", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_SKIP_INCOMING_STATE, &test_address));

  RawAddress::FromString("98:b6:e9:e6:67:89", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_HID_HOST_LIMIT_SNIFF_INTERVAL, &test_address));

  RawAddress::FromString("04:4e:af:a8:a0:01", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DISABLE_REFRESH_ACCEPT_SIG_TIMER,
                                 &test_address));

  RawAddress::FromString("bc:30:7e:5e:f6:27", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_NOT_UPDATE_AVRCP_PAUSED_TO_REMOTE,
                                 &test_address));

  RawAddress::FromString("10:4f:a8:08:91:89", test_address);
  EXPECT_TRUE(interop_match_addr(
      INTEROP_PHONE_POLICY_REDUCED_DELAY_CONNECT_OTHER_PROFILES,
      &test_address));

  RawAddress::FromString("00:15:83:03:38:b0", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_HFP_FAKE_INCOMING_CALL_INDICATOR,
                                 &test_address));

  RawAddress::FromString("00:09:93:a6:c5:4d", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DELAY_SCO_FOR_MO_CALL, &test_address));

  RawAddress::FromString("48:eb:62:e6:67:89", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DISABLE_ROLE_SWITCH_DURING_CONNECTION,
                                 &test_address));

  RawAddress::FromString("9c:df:03:a8:a0:01", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_AUTO_RETRY_PAIRING, &test_address));

  RawAddress::FromString("d4:7a:e2:5e:f6:27", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DISABLE_NAME_REQUEST, &test_address));

  RawAddress::FromString("48:f0:7b:08:91:89", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_AVRCP_1_4_ONLY, &test_address));

  RawAddress::FromString("00:0a:08:03:38:b0", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_AVRCP_1_3_ONLY, &test_address));

  RawAddress::FromString("44:ea:d8:a6:c5:4d", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_DISABLE_SNIFF, &test_address));

  RawAddress::FromString("94:b2:cc:30:c5:4d", test_address);
  EXPECT_TRUE(interop_match_addr(INTEROP_SLC_SKIP_BIND_COMMAND, &test_address));
#endif

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_lookup_miss) {
  module_init(&interop_module);

  RawAddress test_address;

  RawAddress::FromString("00:00:00:00:00:00", test_address);
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, &test_address));

  RawAddress::FromString("ff:ff:ff:ff:ff:ff", test_address);
  EXPECT_FALSE(interop_match_addr(INTEROP_AUTO_RETRY_PAIRING, &test_address));

  RawAddress::FromString("42:08:15:ae:ae:ae", test_address);
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, &test_address));

  RawAddress::FromString("38:2c:4a:59:67:89", test_address);
  EXPECT_FALSE(interop_match_addr(INTEROP_AUTO_RETRY_PAIRING, &test_address));

  RawAddress::FromString("ff:ff:ff:ff:ff:ff", test_address);
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_AUTO_RETRY_PAIRING, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_ABSOLUTE_VOLUME, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_DISABLE_AUTO_PAIRING, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_KEYBOARD_REQUIRES_FIXED_PIN, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_2MBPS_LINK_ONLY, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_HID_PREF_CONN_SUP_TIMEOUT_3S, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_GATTC_NO_SERVICE_CHANGED_IND, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_SDP_AFTER_PAIRING, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_AUTH_FOR_HID_POINTING, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_REMOVE_HID_DIG_DESCRIPTOR, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_SNIFF_DURING_SCO, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_INCREASE_AG_CONN_TIMEOUT, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_DISABLE_LE_CONN_PREFERRED_PARAMS,
                                  &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_DISABLE_AAC_CODEC, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_AAC_VBR_CODEC, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_DYNAMIC_ROLE_SWITCH, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_DISABLE_ROLE_SWITCH, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_ROLE_SWITCH_POLICY, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_HFP_1_7_DENYLIST, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_ADV_PBAP_VER_1_1, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_UPDATE_HID_SSR_MAX_LAT, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_AVDTP_RECONFIGURE, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_DISABLE_HF_INDICATOR, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_LE_CONN_UPDATES, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DELAY_SCO_FOR_MT_CALL, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_CODEC_NEGOTIATION, &test_address));
  EXPECT_FALSE(interop_match_addr(
      INTEROP_DISABLE_PLAYER_APPLICATION_SETTING_CMDS, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_ENABLE_AAC_CODEC, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_DISABLE_CONNECTION_AFTER_COLLISION,
                                  &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_AVRCP_BROWSE_OPEN_CHANNEL_COLLISION,
                                  &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_ADV_PBAP_VER_1_2, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_PCE_SDP_AFTER_PAIRING, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_SNIFF_LINK_DURING_SCO, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_SNIFF_DURING_CALL, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_HID_HOST_LIMIT_SNIFF_INTERVAL, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_DISABLE_REFRESH_ACCEPT_SIG_TIMER,
                                  &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_BROWSE_PLAYER_ALLOW_LIST, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_SKIP_INCOMING_STATE, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_NOT_UPDATE_AVRCP_PAUSED_TO_REMOTE,
                                  &test_address));
  EXPECT_FALSE(interop_match_addr(
      INTEROP_PHONE_POLICY_INCREASED_DELAY_CONNECT_OTHER_PROFILES,
      &test_address));
  EXPECT_FALSE(interop_match_addr(
      INTEROP_PHONE_POLICY_REDUCED_DELAY_CONNECT_OTHER_PROFILES,
      &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_HFP_FAKE_INCOMING_CALL_INDICATOR,
                                  &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_HFP_SEND_CALL_INDICATORS_BACK_TO_BACK,
                                  &test_address));
  EXPECT_FALSE(interop_match_addr(
      INTEROP_SETUP_SCO_WITH_NO_DELAY_AFTER_SLC_DURING_CALL, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_ENABLE_PREFERRED_CONN_PARAMETER,
                                  &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_RETRY_SCO_AFTER_REMOTE_REJECT_SCO,
                                  &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DELAY_SCO_FOR_MO_CALL, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_CHANGE_HID_VID_PID, &test_address));
  EXPECT_FALSE(interop_match_addr(END_OF_INTEROP_LIST, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_HFP_1_8_DENYLIST, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_DISABLE_ROLE_SWITCH_DURING_CONNECTION,
                                  &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_DISABLE_NAME_REQUEST, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_AVRCP_1_4_ONLY, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_DISABLE_SNIFF, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_AVDTP_SUSPEND, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_SLC_SKIP_BIND_COMMAND, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_AVRCP_1_3_ONLY, &test_address));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_dynamic_db_clear) {
  module_init(&interop_module);

  RawAddress test_address;

  RawAddress::FromString("11:22:33:44:55:66", test_address);
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, &test_address));

  interop_database_add(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, &test_address, 3);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, &test_address));
  EXPECT_FALSE(interop_match_addr(INTEROP_AUTO_RETRY_PAIRING, &test_address));

  RawAddress::FromString("66:55:44:33:22:11", test_address);
  EXPECT_FALSE(interop_match_addr(INTEROP_AUTO_RETRY_PAIRING, &test_address));

  interop_database_add(INTEROP_AUTO_RETRY_PAIRING, &test_address, 3);
  EXPECT_TRUE(interop_match_addr(INTEROP_AUTO_RETRY_PAIRING, &test_address));
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, &test_address));

  interop_database_clear();

  EXPECT_FALSE(interop_match_addr(INTEROP_AUTO_RETRY_PAIRING, &test_address));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_name_hit) {
  module_init(&interop_module);

  EXPECT_TRUE(interop_match_name(INTEROP_DISABLE_AUTO_PAIRING, "BMW M3"));
  EXPECT_TRUE(interop_match_name(INTEROP_DISABLE_AUTO_PAIRING, "Audi"));

#ifdef __ANDROID__
  EXPECT_TRUE(interop_match_name(INTEROP_DISABLE_AUTO_PAIRING,
                                 "Caramel"));  // Starts with "Car" ;)

  EXPECT_TRUE(
      interop_match_name(INTEROP_GATTC_NO_SERVICE_CHANGED_IND, "MiMouse"));
  EXPECT_TRUE(interop_match_name(INTEROP_DISABLE_AUTH_FOR_HID_POINTING,
                                 "Targus BT Laser Notebook Mouse"));
  EXPECT_TRUE(interop_match_name(INTEROP_REMOVE_HID_DIG_DESCRIPTOR,
                                 "Motorola Keyboard KZ500"));
  EXPECT_TRUE(interop_match_name(INTEROP_DISABLE_LE_CONN_PREFERRED_PARAMS,
                                 "BSMBB09DS"));
  EXPECT_TRUE(interop_match_name(INTEROP_DISABLE_AAC_CODEC, "abramtek M1"));
  EXPECT_TRUE(
      interop_match_name(INTEROP_DISABLE_AAC_VBR_CODEC, "Audi_MMI_2781"));
  EXPECT_TRUE(
      interop_match_name(INTEROP_DISABLE_AVDTP_RECONFIGURE, "KMM-BT51*HD"));
  EXPECT_TRUE(interop_match_name(INTEROP_DISABLE_LE_CONN_UPDATES, "ITAG"));
  EXPECT_TRUE(interop_match_name(INTEROP_DELAY_SCO_FOR_MT_CALL, "AirPods Pro"));
  EXPECT_TRUE(
      interop_match_name(INTEROP_DISABLE_CODEC_NEGOTIATION, "JABRA EASYGO"));
  EXPECT_TRUE(interop_match_name(INTEROP_ENABLE_AAC_CODEC, "MDR-1RBT"));
  EXPECT_TRUE(
      interop_match_name(INTEROP_DISABLE_SNIFF_LINK_DURING_SCO, "AirPods"));
  EXPECT_TRUE(interop_match_name(INTEROP_DISABLE_SNIFF_DURING_CALL, "AirPods"));
  EXPECT_TRUE(
      interop_match_name(INTEROP_HID_HOST_LIMIT_SNIFF_INTERVAL, "Joy-Con"));
  EXPECT_TRUE(
      interop_match_name(INTEROP_DISABLE_REFRESH_ACCEPT_SIG_TIMER, "HB20"));
  EXPECT_TRUE(
      interop_match_name(INTEROP_NOT_UPDATE_AVRCP_PAUSED_TO_REMOTE, "Audi"));
  EXPECT_TRUE(interop_match_name(
      INTEROP_SETUP_SCO_WITH_NO_DELAY_AFTER_SLC_DURING_CALL, "Geely_BT"));
  EXPECT_TRUE(interop_match_name(INTEROP_ENABLE_PREFERRED_CONN_PARAMETER,
                                 "Microsoft Bluetooth Mouse"));
  EXPECT_TRUE(interop_match_name(INTEROP_RETRY_SCO_AFTER_REMOTE_REJECT_SCO,
                                 "HAVAL M6"));
  EXPECT_TRUE(interop_match_name(INTEROP_CHANGE_HID_VID_PID, "CK87BT"));
#endif

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_name_miss) {
  module_init(&interop_module);

  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_AUTO_PAIRING, "__GOOGLE__"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_AUTO_PAIRING, "BM"));
  EXPECT_FALSE(interop_match_name(INTEROP_AUTO_RETRY_PAIRING, "BMW M3"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_AUTO_RETRY_PAIRING, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_ABSOLUTE_VOLUME, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_AUTO_PAIRING, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_KEYBOARD_REQUIRES_FIXED_PIN, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_2MBPS_LINK_ONLY, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_HID_PREF_CONN_SUP_TIMEOUT_3S, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_GATTC_NO_SERVICE_CHANGED_IND, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_SDP_AFTER_PAIRING, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_DISABLE_AUTH_FOR_HID_POINTING, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_REMOVE_HID_DIG_DESCRIPTOR, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_SNIFF_DURING_SCO, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_INCREASE_AG_CONN_TIMEOUT, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_DISABLE_LE_CONN_PREFERRED_PARAMS, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_AAC_CODEC, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_AAC_VBR_CODEC, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DYNAMIC_ROLE_SWITCH, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_ROLE_SWITCH, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_ROLE_SWITCH_POLICY, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_HFP_1_7_DENYLIST, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_ADV_PBAP_VER_1_1, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_UPDATE_HID_SSR_MAX_LAT, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_AVDTP_RECONFIGURE, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_HF_INDICATOR, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_LE_CONN_UPDATES, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DELAY_SCO_FOR_MT_CALL, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_CODEC_NEGOTIATION, "TEST"));
  EXPECT_FALSE(interop_match_name(
      INTEROP_DISABLE_PLAYER_APPLICATION_SETTING_CMDS, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_ENABLE_AAC_CODEC, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_DISABLE_CONNECTION_AFTER_COLLISION, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_AVRCP_BROWSE_OPEN_CHANNEL_COLLISION, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_ADV_PBAP_VER_1_2, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_DISABLE_PCE_SDP_AFTER_PAIRING, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_DISABLE_SNIFF_LINK_DURING_SCO, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_SNIFF_DURING_CALL, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_HID_HOST_LIMIT_SNIFF_INTERVAL, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_DISABLE_REFRESH_ACCEPT_SIG_TIMER, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_BROWSE_PLAYER_ALLOW_LIST, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_SKIP_INCOMING_STATE, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_NOT_UPDATE_AVRCP_PAUSED_TO_REMOTE, "TEST"));
  EXPECT_FALSE(interop_match_name(
      INTEROP_PHONE_POLICY_INCREASED_DELAY_CONNECT_OTHER_PROFILES, "TEST"));
  EXPECT_FALSE(interop_match_name(
      INTEROP_PHONE_POLICY_REDUCED_DELAY_CONNECT_OTHER_PROFILES, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_HFP_FAKE_INCOMING_CALL_INDICATOR, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_HFP_SEND_CALL_INDICATORS_BACK_TO_BACK,
                                  "TEST"));
  EXPECT_FALSE(interop_match_name(
      INTEROP_SETUP_SCO_WITH_NO_DELAY_AFTER_SLC_DURING_CALL, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_ENABLE_PREFERRED_CONN_PARAMETER, "TEST"));
  EXPECT_FALSE(
      interop_match_name(INTEROP_RETRY_SCO_AFTER_REMOTE_REJECT_SCO, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DELAY_SCO_FOR_MO_CALL, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_CHANGE_HID_VID_PID, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_HFP_1_8_DENYLIST, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_ROLE_SWITCH_DURING_CONNECTION,
                                  "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_NAME_REQUEST, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_AVRCP_1_4_ONLY, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_SNIFF, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_DISABLE_AVDTP_SUSPEND, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_SLC_SKIP_BIND_COMMAND, "TEST"));
  EXPECT_FALSE(interop_match_name(INTEROP_AVRCP_1_3_ONLY, "TEST"));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_range_hit) {
  module_init(&interop_module);

  RawAddress test_address;
  RawAddress::FromString("00:0f:59:50:00:00", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_ABSOLUTE_VOLUME, &test_address));
  RawAddress::FromString("00:0f:59:59:12:34", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_ABSOLUTE_VOLUME, &test_address));
  RawAddress::FromString("00:0f:59:6f:ff:ff", test_address);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_ABSOLUTE_VOLUME, &test_address));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_range_miss) {
  module_init(&interop_module);

  RawAddress test_address;
  RawAddress::FromString("00:0f:59:49:12:34", test_address);
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_ABSOLUTE_VOLUME, &test_address));
  RawAddress::FromString("00:0f:59:70:12:34", test_address);
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_ABSOLUTE_VOLUME, &test_address));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_vndr_prdt_hit) {
  module_init(&interop_module);

  uint16_t vendor_id = 0x22b8;
  uint16_t product_id = 0x093D;

  EXPECT_TRUE(interop_database_match_vndr_prdt(
      INTEROP_REMOVE_HID_DIG_DESCRIPTOR, vendor_id, product_id));

  vendor_id = 0x05ac;
  product_id = 0x0255;

  EXPECT_TRUE(interop_database_match_vndr_prdt(INTEROP_CHANGE_HID_VID_PID,
                                               vendor_id, product_id));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_vndr_prdt_miss) {
  module_init(&interop_module);

  uint16_t vendor_id = 0x22b9;
  uint16_t product_id = 0x093D;

  EXPECT_FALSE(interop_database_match_vndr_prdt(
      INTEROP_REMOVE_HID_DIG_DESCRIPTOR, vendor_id, product_id));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_manufacturer_hit) {
  module_init(&interop_module);

  uint16_t manufacturer = 0x004C;

  EXPECT_TRUE(interop_database_match_manufacturer(
      INTEROP_DISABLE_SNIFF_DURING_SCO, manufacturer));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_manufacturer_miss) {
  module_init(&interop_module);

  uint16_t manufacturer = 0x004D;

  EXPECT_FALSE(interop_database_match_manufacturer(
      INTEROP_DISABLE_SNIFF_DURING_SCO, manufacturer));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_ssr_max_latency_hit) {
  module_init(&interop_module);

  RawAddress test_address;
  RawAddress::FromString("00:1b:dc:70:12:34", test_address);
  uint16_t max_lat = 0;

  EXPECT_TRUE(interop_database_match_addr_get_max_lat(
      INTEROP_UPDATE_HID_SSR_MAX_LAT, &test_address, &max_lat));
  EXPECT_TRUE(max_lat == 0x0012);

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_ssr_max_latency_miss) {
  module_init(&interop_module);

  RawAddress test_address;
  RawAddress::FromString("00:1b:db:70:12:34", test_address);
  uint16_t max_lat = 0;

  EXPECT_FALSE(interop_database_match_addr_get_max_lat(
      INTEROP_UPDATE_HID_SSR_MAX_LAT, &test_address, &max_lat));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_dynamic_addr) {
  module_init(&interop_module);

  RawAddress test_address;

  RawAddress::FromString("11:22:33:44:55:66", test_address);
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, &test_address));

  interop_database_add_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS,
                            &test_address, 3);
  EXPECT_TRUE(
      interop_match_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, &test_address));

  interop_database_remove_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS,
                               &test_address);
  EXPECT_FALSE(
      interop_match_addr(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, &test_address));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_dynamic_name) {
  module_init(&interop_module);

  EXPECT_FALSE(
      interop_match_name(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, "TEST"));

  interop_database_add_name(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, "TEST");
  EXPECT_TRUE(
      interop_match_name(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, "TEST"));

  interop_database_remove_name(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, "TEST");
  EXPECT_FALSE(
      interop_match_name(INTEROP_DISABLE_LE_SECURE_CONNECTIONS, "TEST"));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_dynamic_vndr_prdt) {
  module_init(&interop_module);

  uint16_t vendor_id = 0x11b8;
  uint16_t product_id = 0x193D;

  EXPECT_FALSE(interop_database_match_vndr_prdt(
      INTEROP_REMOVE_HID_DIG_DESCRIPTOR, vendor_id, product_id));

  interop_database_add_vndr_prdt(INTEROP_REMOVE_HID_DIG_DESCRIPTOR, vendor_id,
                                 product_id);
  EXPECT_TRUE(interop_database_match_vndr_prdt(
      INTEROP_REMOVE_HID_DIG_DESCRIPTOR, vendor_id, product_id));

  interop_database_remove_vndr_prdt(INTEROP_REMOVE_HID_DIG_DESCRIPTOR,
                                    vendor_id, product_id);

  EXPECT_FALSE(interop_database_match_vndr_prdt(INTEROP_CHANGE_HID_VID_PID,
                                                vendor_id, product_id));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_dynamic_addr_get_ssr_max_lat) {
  module_init(&interop_module);

  RawAddress test_address;
  RawAddress::FromString("11:22:33:44:55:66", test_address);
  uint16_t max_lat = 0;

  EXPECT_FALSE(interop_database_match_addr_get_max_lat(
      INTEROP_UPDATE_HID_SSR_MAX_LAT, &test_address, &max_lat));
  interop_database_add_addr_max_lat(INTEROP_UPDATE_HID_SSR_MAX_LAT,
                                    &test_address, 0x0012);

  interop_database_match_addr_get_max_lat(INTEROP_UPDATE_HID_SSR_MAX_LAT,
                                          &test_address, &max_lat);
  EXPECT_TRUE(max_lat == 0x0012);

  interop_database_remove_addr_max_lat(INTEROP_UPDATE_HID_SSR_MAX_LAT,
                                       &test_address, 0x0012);

  max_lat = 0;
  EXPECT_FALSE(interop_database_match_addr_get_max_lat(
      INTEROP_UPDATE_HID_SSR_MAX_LAT, &test_address, &max_lat));

  EXPECT_FALSE(max_lat == 0x0012);

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_dynamic_manufacturer) {
  module_init(&interop_module);

  uint16_t manufacturer = 0xFFFF;

  EXPECT_FALSE(interop_database_match_manufacturer(
      INTEROP_DISABLE_SNIFF_DURING_SCO, manufacturer));

  interop_database_add_manufacturer(INTEROP_DISABLE_SNIFF_DURING_SCO,
                                    manufacturer);

  EXPECT_TRUE(interop_database_match_manufacturer(
      INTEROP_DISABLE_SNIFF_DURING_SCO, manufacturer));

  interop_database_remove_manufacturer(INTEROP_DISABLE_SNIFF_DURING_SCO,
                                       manufacturer);

  EXPECT_FALSE(interop_database_match_manufacturer(
      INTEROP_DISABLE_SNIFF_DURING_SCO, manufacturer));

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_dynamic_addr_get_lmp_version) {
  module_init(&interop_module);

  RawAddress test_address;
  RawAddress::FromString("11:22:33:44:55:66", test_address);
  uint8_t lmp_version = 0;
  uint16_t lmp_sub_version = 0;

  EXPECT_FALSE(interop_database_match_addr_get_lmp_ver(
      INTEROP_DISABLE_SNIFF_DURING_SCO, &test_address, &lmp_version,
      &lmp_sub_version));
  interop_database_add_addr_lmp_version(INTEROP_DISABLE_SNIFF_DURING_SCO,
                                        &test_address, 0xFF, 0xFFFF);

  EXPECT_TRUE(interop_database_match_addr_get_lmp_ver(
      INTEROP_DISABLE_SNIFF_DURING_SCO, &test_address, &lmp_version,
      &lmp_sub_version));

  EXPECT_TRUE(lmp_version == 0xFF && lmp_sub_version == 0xFFFF);

  interop_database_remove_addr_lmp_version(INTEROP_DISABLE_SNIFF_DURING_SCO,
                                           &test_address, 0xFF, 0xFFFF);

  lmp_version = 0;
  lmp_sub_version = 0;

  EXPECT_FALSE(interop_database_match_addr_get_lmp_ver(
      INTEROP_DISABLE_SNIFF_DURING_SCO, &test_address, &lmp_version,
      &lmp_sub_version));

  EXPECT_FALSE(lmp_version == 0xFF && lmp_sub_version == 0xFFFF);

  module_clean_up(&interop_module);
}

TEST_F(InteropTest, test_dynamic_did_version) {
  module_init(&interop_module);

  RawAddress test_address;
  RawAddress::FromString("11:22:33:44:55:66", test_address);
  uint16_t did_version = 0xABCD;

  EXPECT_FALSE(interop_database_match_version(INTEROP_DISABLE_SNIFF_DURING_SCO,
                                              did_version));
  interop_database_add_version(INTEROP_DISABLE_SNIFF_DURING_SCO, did_version);

  EXPECT_TRUE(interop_database_match_version(INTEROP_DISABLE_SNIFF_DURING_SCO,
                                             did_version));

  interop_database_remove_version(INTEROP_DISABLE_SNIFF_DURING_SCO,
                                  did_version);

  EXPECT_FALSE(interop_database_match_version(INTEROP_DISABLE_SNIFF_DURING_SCO,
                                              did_version));

  module_clean_up(&interop_module);
}
