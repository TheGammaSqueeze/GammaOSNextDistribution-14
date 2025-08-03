/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include <com_android_bluetooth_flags.h>
#include <flag_macros.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <chrono>

#include "bta/csis/csis_types.h"
#include "bta_gatt_api_mock.h"
#include "bta_gatt_queue_mock.h"
#include "bta_groups.h"
#include "bta_le_audio_api.h"
#include "btif_storage_mock.h"
#include "btm_api_mock.h"
#include "btm_iso_api.h"
#include "common/message_loop_thread.h"
#include "device/include/controller.h"
#include "fake_osi.h"
#include "gatt/database_builder.h"
#include "hardware/bt_gatt_types.h"
#include "internal_include/stack_config.h"
#include "le_audio_health_status.h"
#include "le_audio_set_configuration_provider.h"
#include "le_audio_types.h"
#include "mock_codec_manager.h"
#include "mock_controller.h"
#include "mock_csis_client.h"
#include "mock_device_groups.h"
#include "mock_iso_manager.h"
#include "mock_state_machine.h"
#include "os/log.h"
#include "test/common/mock_functions.h"

#define TEST_BT com::android::bluetooth::flags

using testing::_;
using testing::AnyNumber;
using testing::AtLeast;
using testing::AtMost;
using testing::DoAll;
using testing::Expectation;
using testing::InSequence;
using testing::Invoke;
using testing::Matcher;
using testing::Mock;
using testing::MockFunction;
using testing::NiceMock;
using testing::NotNull;
using testing::Return;
using testing::SaveArg;
using testing::SetArgPointee;
using testing::Test;
using testing::WithArg;

using bluetooth::Uuid;

using namespace bluetooth::le_audio;

using le_audio::LeAudioCodecConfiguration;
using le_audio::LeAudioDeviceGroup;
using le_audio::LeAudioHealthStatus;
using le_audio::LeAudioSinkAudioHalClient;
using le_audio::LeAudioSourceAudioHalClient;

using le_audio::DsaMode;
using le_audio::DsaModes;
using le_audio::types::AudioContexts;
using le_audio::types::BidirectionalPair;
using le_audio::types::LeAudioContextType;

extern struct fake_osi_alarm_set_on_mloop fake_osi_alarm_set_on_mloop_;

constexpr int max_num_of_ases = 5;
constexpr le_audio::types::LeAudioContextType
    kLeAudioDefaultConfigurationContext =
        le_audio::types::LeAudioContextType::UNSPECIFIED;

static constexpr char kNotifyUpperLayerAboutGroupBeingInIdleDuringCall[] =
    "persist.bluetooth.leaudio.notify.idle.during.call";
const char* test_flags[] = {
    "INIT_default_log_level_str=LOG_VERBOSE",
    "INIT_leaudio_targeted_announcement_reconnection_mode=true",
    "INIT_leaudio_enable_health_based_actions=false",
    "INIT_leaudio_broadcast_audio_handover_policies=false",
    nullptr,
};

const char* test_flags_with_health_status[] = {
    "INIT_default_log_level_str=LOG_VERBOSE",
    "INIT_leaudio_targeted_announcement_reconnection_mode=true",
    "INIT_leaudio_enable_health_based_actions=true",
    "INIT_leaudio_broadcast_audio_handover_policies=false",
    nullptr,
};

const char* test_flags_with_handover_mode[] = {
    "INIT_default_log_level_str=LOG_VERBOSE",
    "INIT_leaudio_targeted_announcement_reconnection_mode=true",
    "INIT_leaudio_enable_health_based_actions=false",
    "INIT_leaudio_broadcast_audio_handover_policies=true",
    nullptr,
};

void osi_property_set_bool(const char* key, bool value);

// Disables most likely false-positives from base::SplitString()
extern "C" const char* __asan_default_options() {
  return "detect_container_overflow=0";
}

std::atomic<int> num_async_tasks;
static base::MessageLoop* message_loop_;
bluetooth::common::MessageLoopThread message_loop_thread("test message loop");
bluetooth::common::MessageLoopThread* get_main_thread() {
  return &message_loop_thread;
}

bt_status_t do_in_main_thread(const base::Location& from_here,
                              base::OnceClosure task) {
  if (!message_loop_) return BT_STATUS_FAIL;

  // Wrap the task with task counter so we could later know if there are
  // any callbacks scheduled and we should wait before performing some actions
  if (!message_loop_thread.DoInThread(
          from_here,
          base::BindOnce(
              [](base::OnceClosure task, std::atomic<int>& num_async_tasks) {
                std::move(task).Run();
                num_async_tasks--;
              },
              std::move(task), std::ref(num_async_tasks)))) {
    LOG(ERROR) << __func__ << ": failed from " << from_here.ToString();
    return BT_STATUS_FAIL;
  }
  num_async_tasks++;
  return BT_STATUS_SUCCESS;
}

bt_status_t do_in_main_thread_delayed(const base::Location& from_here,
                                      base::OnceClosure task,
                                      std::chrono::microseconds delay) {
  /* For testing purpose it is ok to just skip delay */
  return do_in_main_thread(from_here, std::move(task));
}

base::MessageLoop* get_main_message_loop() { return message_loop_; }

static void init_message_loop_thread() {
  num_async_tasks = 0;
  message_loop_thread.StartUp();
  if (!message_loop_thread.IsRunning()) {
    FAIL() << "unable to create message loop thread.";
  }

  if (!message_loop_thread.EnableRealTimeScheduling())
    LOG(ERROR) << "Unable to set real time scheduling";

  message_loop_ = message_loop_thread.message_loop();
  if (message_loop_ == nullptr) FAIL() << "unable to get message loop.";
}

static void cleanup_message_loop_thread() {
  message_loop_ = nullptr;
  message_loop_thread.ShutDown();
}

void invoke_switch_codec_cb(bool is_low_latency_buffer_size) {}
void invoke_switch_buffer_size_cb(bool is_low_latency_buffer_size) {}

const std::string kSmpOptions("mock smp options");
bool get_pts_avrcp_test(void) { return false; }
bool get_pts_secure_only_mode(void) { return false; }
bool get_pts_conn_updates_disabled(void) { return false; }
bool get_pts_crosskey_sdp_disable(void) { return false; }
const std::string* get_pts_smp_options(void) { return &kSmpOptions; }
int get_pts_smp_failure_case(void) { return 123; }
bool get_pts_force_eatt_for_notifications(void) { return false; }
bool get_pts_connect_eatt_unconditionally(void) { return false; }
bool get_pts_connect_eatt_before_encryption(void) { return false; }
bool get_pts_unencrypt_broadcast(void) { return false; }
bool get_pts_eatt_peripheral_collision_support(void) { return false; }
bool get_pts_force_le_audio_multiple_contexts_metadata(void) { return false; }
bool get_pts_le_audio_disable_ases_before_stopping(void) { return false; }
config_t* get_all(void) { return nullptr; }

stack_config_t mock_stack_config{
    .get_pts_avrcp_test = get_pts_avrcp_test,
    .get_pts_secure_only_mode = get_pts_secure_only_mode,
    .get_pts_conn_updates_disabled = get_pts_conn_updates_disabled,
    .get_pts_crosskey_sdp_disable = get_pts_crosskey_sdp_disable,
    .get_pts_smp_options = get_pts_smp_options,
    .get_pts_smp_failure_case = get_pts_smp_failure_case,
    .get_pts_force_eatt_for_notifications =
        get_pts_force_eatt_for_notifications,
    .get_pts_connect_eatt_unconditionally =
        get_pts_connect_eatt_unconditionally,
    .get_pts_connect_eatt_before_encryption =
        get_pts_connect_eatt_before_encryption,
    .get_pts_unencrypt_broadcast = get_pts_unencrypt_broadcast,
    .get_pts_eatt_peripheral_collision_support =
        get_pts_eatt_peripheral_collision_support,
    .get_pts_force_le_audio_multiple_contexts_metadata =
        get_pts_force_le_audio_multiple_contexts_metadata,
    .get_pts_le_audio_disable_ases_before_stopping =
        get_pts_le_audio_disable_ases_before_stopping,
    .get_all = get_all,
};
const stack_config_t* stack_config_get_interface(void) {
  return &mock_stack_config;
}

namespace le_audio {
class MockLeAudioSourceHalClient;
MockLeAudioSourceHalClient* mock_le_audio_source_hal_client_;
std::unique_ptr<LeAudioSourceAudioHalClient>
    owned_mock_le_audio_source_hal_client_;
bool is_audio_unicast_source_acquired;

std::unique_ptr<LeAudioSourceAudioHalClient>
LeAudioSourceAudioHalClient::AcquireUnicast() {
  if (is_audio_unicast_source_acquired) return nullptr;
  is_audio_unicast_source_acquired = true;
  return std::move(owned_mock_le_audio_source_hal_client_);
}

void LeAudioSourceAudioHalClient::DebugDump(int fd) {}

class MockLeAudioSinkHalClient;
MockLeAudioSinkHalClient* mock_le_audio_sink_hal_client_;
std::unique_ptr<LeAudioSinkAudioHalClient> owned_mock_le_audio_sink_hal_client_;
bool is_audio_unicast_sink_acquired;

std::unique_ptr<LeAudioSinkAudioHalClient>
LeAudioSinkAudioHalClient::AcquireUnicast() {
  if (is_audio_unicast_sink_acquired) return nullptr;
  is_audio_unicast_sink_acquired = true;
  return std::move(owned_mock_le_audio_sink_hal_client_);
}

void LeAudioSinkAudioHalClient::DebugDump(int fd) {}

class MockAudioHalClientCallbacks
    : public bluetooth::le_audio::LeAudioClientCallbacks {
 public:
  MOCK_METHOD((void), OnInitialized, (), (override));
  MOCK_METHOD((void), OnConnectionState,
              (ConnectionState state, const RawAddress& address), (override));
  MOCK_METHOD((void), OnGroupStatus, (int group_id, GroupStatus group_status),
              (override));
  MOCK_METHOD((void), OnGroupStreamStatus,
              (int group_id, GroupStreamStatus group_stream_status),
              (override));
  MOCK_METHOD((void), OnGroupNodeStatus,
              (const RawAddress& bd_addr, int group_id,
               GroupNodeStatus node_status),
              (override));
  MOCK_METHOD((void), OnAudioConf,
              (uint8_t direction, int group_id, uint32_t snk_audio_location,
               uint32_t src_audio_location, uint16_t avail_cont),
              (override));
  MOCK_METHOD((void), OnSinkAudioLocationAvailable,
              (const RawAddress& bd_addr, uint32_t snk_audio_location),
              (override));
  MOCK_METHOD(
      (void), OnAudioLocalCodecCapabilities,
      (std::vector<btle_audio_codec_config_t> local_input_capa_codec_conf,
       std::vector<btle_audio_codec_config_t> local_output_capa_codec_conf),
      (override));
  MOCK_METHOD((void), OnAudioGroupCurrentCodecConf,
              (int group_id, btle_audio_codec_config_t input_codec_conf,
               btle_audio_codec_config_t output_codec_conf),
              (override));
  MOCK_METHOD(
      (void), OnAudioGroupSelectableCodecConf,
      (int group_id,
       std::vector<btle_audio_codec_config_t> input_selectable_codec_conf,
       std::vector<btle_audio_codec_config_t> output_selectable_codec_conf),
      (override));
  MOCK_METHOD((void), OnHealthBasedRecommendationAction,
              (const RawAddress& address, LeAudioHealthBasedAction action),
              (override));
  MOCK_METHOD((void), OnHealthBasedGroupRecommendationAction,
              (int group_id, LeAudioHealthBasedAction action), (override));
  MOCK_METHOD((void), OnUnicastMonitorModeStatus,
              (uint8_t direction, UnicastMonitorModeStatus status));
};

class MockLeAudioSinkHalClient : public LeAudioSinkAudioHalClient {
 public:
  MockLeAudioSinkHalClient() = default;
  MOCK_METHOD((bool), Start,
              (const LeAudioCodecConfiguration& codecConfiguration,
               LeAudioSinkAudioHalClient::Callbacks* audioReceiver,
               DsaModes dsa_modes),
              (override));
  MOCK_METHOD((void), Stop, (), (override));
  MOCK_METHOD((size_t), SendData, (uint8_t * data, uint16_t size), (override));
  MOCK_METHOD((void), ConfirmStreamingRequest, (), (override));
  MOCK_METHOD((void), CancelStreamingRequest, (), (override));
  MOCK_METHOD((void), UpdateRemoteDelay, (uint16_t delay), (override));
  MOCK_METHOD((void), UpdateAudioConfigToHal,
              (const ::le_audio::offload_config&), (override));
  MOCK_METHOD((void), SuspendedForReconfiguration, (), (override));
  MOCK_METHOD((void), ReconfigurationComplete, (), (override));

  MOCK_METHOD((void), OnDestroyed, ());
  virtual ~MockLeAudioSinkHalClient() override { OnDestroyed(); }
};

class MockLeAudioSourceHalClient : public LeAudioSourceAudioHalClient {
 public:
  MockLeAudioSourceHalClient() = default;
  MOCK_METHOD((bool), Start,
              (const LeAudioCodecConfiguration& codecConfiguration,
               LeAudioSourceAudioHalClient::Callbacks* audioReceiver,
               DsaModes dsa_modes),
              (override));
  MOCK_METHOD((void), Stop, (), (override));
  MOCK_METHOD((void), ConfirmStreamingRequest, (), (override));
  MOCK_METHOD((void), CancelStreamingRequest, (), (override));
  MOCK_METHOD((void), UpdateRemoteDelay, (uint16_t delay), (override));
  MOCK_METHOD((void), UpdateAudioConfigToHal,
              (const ::le_audio::offload_config&), (override));
  MOCK_METHOD((void), UpdateBroadcastAudioConfigToHal,
              (const ::le_audio::broadcast_offload_config&), (override));
  MOCK_METHOD((void), SuspendedForReconfiguration, (), (override));
  MOCK_METHOD((void), ReconfigurationComplete, (), (override));

  MOCK_METHOD((void), OnDestroyed, ());
  virtual ~MockLeAudioSourceHalClient() override { OnDestroyed(); }
};

class UnicastTestNoInit : public Test {
 public:
  bool use_health_status = false;
  bool use_handover_mode = false;

 protected:
  void RegisterSourceHalClientMock() {
    owned_mock_le_audio_source_hal_client_.reset(
        new NiceMock<MockLeAudioSourceHalClient>());
    mock_le_audio_source_hal_client_ =
        (MockLeAudioSourceHalClient*)
            owned_mock_le_audio_source_hal_client_.get();

    is_audio_unicast_source_acquired = false;
    ON_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _))
        .WillByDefault(
            [this](const LeAudioCodecConfiguration& codec_configuration,
                   LeAudioSourceAudioHalClient::Callbacks* audioReceiver,
                   DsaModes dsa_modes) {
              unicast_source_hal_cb_ = audioReceiver;
              return true;
            });
    ON_CALL(*mock_le_audio_source_hal_client_, OnDestroyed).WillByDefault([]() {
      mock_le_audio_source_hal_client_ = nullptr;
      is_audio_unicast_source_acquired = false;
    });
  }

  void RegisterSinkHalClientMock() {
    owned_mock_le_audio_sink_hal_client_.reset(
        new NiceMock<MockLeAudioSinkHalClient>());
    mock_le_audio_sink_hal_client_ =
        (MockLeAudioSinkHalClient*)owned_mock_le_audio_sink_hal_client_.get();

    is_audio_unicast_sink_acquired = false;
    ON_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _))
        .WillByDefault(
            [this](const LeAudioCodecConfiguration& codec_configuration,
                   LeAudioSinkAudioHalClient::Callbacks* audioReceiver,
                   DsaModes dsa_modes) {
              unicast_sink_hal_cb_ = audioReceiver;
              return true;
            });
    ON_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed).WillByDefault([]() {
      mock_le_audio_sink_hal_client_ = nullptr;
      is_audio_unicast_sink_acquired = false;
    });
  }

  void SetUpMockAudioHal() {
    if (use_health_status) {
      bluetooth::common::InitFlags::Load(test_flags_with_health_status);
    } else if (use_handover_mode) {
      bluetooth::common::InitFlags::Load(test_flags_with_handover_mode);
    } else {
      bluetooth::common::InitFlags::Load(test_flags);
    }

    /* Since these are returned by the Acquire() methods as unique_ptrs, we
     * will not free them manually.
     */
    RegisterSourceHalClientMock();

    owned_mock_le_audio_sink_hal_client_.reset(
        new NiceMock<MockLeAudioSinkHalClient>());
    mock_le_audio_sink_hal_client_ =
        (MockLeAudioSinkHalClient*)owned_mock_le_audio_sink_hal_client_.get();

    owned_mock_le_audio_source_hal_client_.reset(
        new NiceMock<MockLeAudioSourceHalClient>());
    mock_le_audio_source_hal_client_ =
        (MockLeAudioSourceHalClient*)
            owned_mock_le_audio_source_hal_client_.get();

    is_audio_unicast_source_acquired = false;
    ON_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _))
        .WillByDefault(
            [this](const LeAudioCodecConfiguration& codec_configuration,
                   LeAudioSourceAudioHalClient::Callbacks* audioReceiver,
                   DsaModes dsa_modes) {
              unicast_source_hal_cb_ = audioReceiver;
              return true;
            });
    ON_CALL(*mock_le_audio_source_hal_client_, OnDestroyed).WillByDefault([]() {
      mock_le_audio_source_hal_client_ = nullptr;
      is_audio_unicast_source_acquired = false;
    });

    is_audio_unicast_sink_acquired = false;
    ON_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _))
        .WillByDefault(
            [this](const LeAudioCodecConfiguration& codec_configuration,
                   LeAudioSinkAudioHalClient::Callbacks* audioReceiver,
                   DsaModes dsa_modes) {
              unicast_sink_hal_cb_ = audioReceiver;
              return true;
            });
    ON_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed).WillByDefault([]() {
      mock_le_audio_sink_hal_client_ = nullptr;
      is_audio_unicast_sink_acquired = false;
    });

    ON_CALL(*mock_le_audio_sink_hal_client_, SendData)
        .WillByDefault([](uint8_t* data, uint16_t size) { return size; });

    // HAL
    ON_CALL(mock_hal_2_1_verifier, Call()).WillByDefault([]() -> bool {
      return true;
    });
  }

  void InjectGroupDeviceRemoved(const RawAddress& address, int group_id) {
    group_callbacks_->OnGroupMemberRemoved(address, group_id);
  }

  void InjectGroupDeviceAdded(const RawAddress& address, int group_id) {
    bluetooth::Uuid uuid = le_audio::uuid::kCapServiceUuid;

    int group_members_num = 0;
    for (const auto& [addr, id] : groups) {
      if (id == group_id) group_members_num++;
    }

    bool first_device = (group_members_num == 1);
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(
            [](const RawAddress& addr, int group_id, bluetooth::Uuid uuid,
               bluetooth::groups::DeviceGroupsCallbacks* group_callbacks,
               bool first_device) {
              if (first_device) {
                group_callbacks->OnGroupAdded(addr, uuid, group_id);
              } else {
                group_callbacks->OnGroupMemberAdded(addr, group_id);
              }
            },
            address, group_id, uuid, base::Unretained(this->group_callbacks_),
            first_device));
  }

  void InjectServiceChangedEvent(const RawAddress& address, uint16_t conn_id) {
    tBTA_GATTC_SERVICE_CHANGED event_data = {.remote_bda = address,
                                             .conn_id = conn_id};

    do_in_main_thread(FROM_HERE,
                      base::BindOnce(
                          [](tBTA_GATTC_CBACK* gatt_callback,
                             tBTA_GATTC_SERVICE_CHANGED event_data) {
                            gatt_callback(BTA_GATTC_SRVC_CHG_EVT,
                                          (tBTA_GATTC*)&event_data);
                          },
                          base::Unretained(this->gatt_callback), event_data));
  }

  void InjectConnectedEvent(const RawAddress& address, uint16_t conn_id,
                            tGATT_STATUS status = GATT_SUCCESS) {
    ASSERT_NE(conn_id, GATT_INVALID_CONN_ID);
    tBTA_GATTC_OPEN event_data = {
        .status = status,
        .conn_id = conn_id,
        .client_if = gatt_if,
        .remote_bda = address,
        .transport = GATT_TRANSPORT_LE,
        .mtu = 240,
    };

    if (status == GATT_SUCCESS) {
      ASSERT_NE(peer_devices.count(conn_id), 0u);
      peer_devices.at(conn_id)->connected = true;
    }

    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(
            [](tBTA_GATTC_CBACK* gatt_callback, tBTA_GATTC_OPEN event_data) {
              gatt_callback(BTA_GATTC_OPEN_EVT, (tBTA_GATTC*)&event_data);
            },
            base::Unretained(this->gatt_callback), event_data));
  }

  void InjectEncryptionChangedEvent(const RawAddress& address) {
    tBTA_GATTC_ENC_CMPL_CB event_data = {
        .client_if = gatt_if,
        .remote_bda = address,
    };

    do_in_main_thread(FROM_HERE,
                      base::BindOnce(
                          [](tBTA_GATTC_CBACK* gatt_callback,
                             tBTA_GATTC_ENC_CMPL_CB event_data) {
                            gatt_callback(BTA_GATTC_ENC_CMPL_CB_EVT,
                                          (tBTA_GATTC*)&event_data);
                          },
                          base::Unretained(this->gatt_callback), event_data));
  }

  void InjectDisconnectedEvent(
      uint16_t conn_id,
      tGATT_DISCONN_REASON reason = GATT_CONN_TERMINATE_LOCAL_HOST) {
    ASSERT_NE(conn_id, GATT_INVALID_CONN_ID);
    ASSERT_NE(peer_devices.count(conn_id), 0u);

    tBTA_GATTC_CLOSE event_data = {
        .conn_id = conn_id,
        .status = GATT_SUCCESS,
        .client_if = gatt_if,
        .remote_bda = peer_devices.at(conn_id)->addr,
        .reason = reason,
    };

    peer_devices.at(conn_id)->connected = false;
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(
            [](tBTA_GATTC_CBACK* gatt_callback, tBTA_GATTC_CLOSE event_data) {
              gatt_callback(BTA_GATTC_CLOSE_EVT, (tBTA_GATTC*)&event_data);
            },
            base::Unretained(this->gatt_callback), event_data));
  }

  void InjectSearchCompleteEvent(uint16_t conn_id) {
    ASSERT_NE(conn_id, GATT_INVALID_CONN_ID);
    tBTA_GATTC_SEARCH_CMPL event_data = {
        .conn_id = conn_id,
        .status = GATT_SUCCESS,
    };

    do_in_main_thread(FROM_HERE,
                      base::BindOnce(
                          [](tBTA_GATTC_CBACK* gatt_callback,
                             tBTA_GATTC_SEARCH_CMPL event_data) {
                            gatt_callback(BTA_GATTC_SEARCH_CMPL_EVT,
                                          (tBTA_GATTC*)&event_data);
                          },
                          base::Unretained(this->gatt_callback), event_data));
  }

  void InjectNotificationEvent(const RawAddress& test_address, uint16_t conn_id,
                               uint16_t handle, std::vector<uint8_t> value) {
    ASSERT_NE(conn_id, GATT_INVALID_CONN_ID);
    tBTA_GATTC_NOTIFY event_data = {
        .conn_id = conn_id,
        .bda = test_address,
        .handle = handle,
        .len = (uint8_t)value.size(),
        .is_notify = true,
    };

    std::copy(value.begin(), value.end(), event_data.value);
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(
            [](tBTA_GATTC_CBACK* gatt_callback, tBTA_GATTC_NOTIFY event_data) {
              gatt_callback(BTA_GATTC_NOTIF_EVT, (tBTA_GATTC*)&event_data);
            },
            base::Unretained(this->gatt_callback), event_data));
  }

  void InjectContextTypes(const RawAddress& test_address, uint16_t conn_id,
                          uint16_t handle, AudioContexts sink_ctxs,
                          AudioContexts source_ctxs) {
    std::vector<uint8_t> contexts = {
        (uint8_t)(sink_ctxs.value()), (uint8_t)(sink_ctxs.value() >> 8),
        (uint8_t)(source_ctxs.value()), (uint8_t)(source_ctxs.value() >> 8)};

    InjectNotificationEvent(test_address, conn_id, handle, contexts);
  }

  void InjectSupportedContextTypes(const RawAddress& test_address,
                                   uint16_t conn_id, AudioContexts sink_ctxs,
                                   AudioContexts source_ctxs) {
    /* 0x0077 pacs->supp_contexts_char + 1 */
    InjectContextTypes(test_address, conn_id, 0x0077, sink_ctxs, source_ctxs);
    SyncOnMainLoop();
  }

  void InjectAvailableContextTypes(const RawAddress& test_address,
                                   uint16_t conn_id, AudioContexts sink_ctxs,
                                   AudioContexts source_ctxs) {
    /* 0x0074 is pacs->avail_contexts_char + 1 */
    InjectContextTypes(test_address, conn_id, 0x0074, sink_ctxs, source_ctxs);
    SyncOnMainLoop();
  }

  void SetUpMockGatt() {
    // default action for GetCharacteristic function call
    ON_CALL(mock_gatt_interface_, GetCharacteristic(_, _))
        .WillByDefault(
            Invoke([&](uint16_t conn_id,
                       uint16_t handle) -> const gatt::Characteristic* {
              std::list<gatt::Service>& services =
                  peer_devices.at(conn_id)->services;
              for (auto const& service : services) {
                for (auto const& characteristic : service.characteristics) {
                  if (characteristic.value_handle == handle) {
                    return &characteristic;
                  }
                }
              }

              return nullptr;
            }));

    // default action for GetOwningService function call
    ON_CALL(mock_gatt_interface_, GetOwningService(_, _))
        .WillByDefault(Invoke(
            [&](uint16_t conn_id, uint16_t handle) -> const gatt::Service* {
              std::list<gatt::Service>& services =
                  peer_devices.at(conn_id)->services;
              for (auto const& service : services) {
                if (service.handle <= handle && service.end_handle >= handle) {
                  return &service;
                }
              }

              return nullptr;
            }));

    // default action for ServiceSearchRequest function call
    ON_CALL(mock_gatt_interface_, ServiceSearchRequest(_, _))
        .WillByDefault(WithArg<0>(Invoke(
            [&](uint16_t conn_id) { InjectSearchCompleteEvent(conn_id); })));

    // default action for GetServices function call
    ON_CALL(mock_gatt_interface_, GetServices(_))
        .WillByDefault(WithArg<0>(
            Invoke([&](uint16_t conn_id) -> std::list<gatt::Service>* {
              return &peer_devices.at(conn_id)->services;
            })));

    // default action for RegisterForNotifications function call
    ON_CALL(mock_gatt_interface_, RegisterForNotifications(gatt_if, _, _))
        .WillByDefault(Return(GATT_SUCCESS));

    // default action for DeregisterForNotifications function call
    ON_CALL(mock_gatt_interface_, DeregisterForNotifications(gatt_if, _, _))
        .WillByDefault(Return(GATT_SUCCESS));

    // default action for WriteDescriptor function call
    ON_CALL(mock_gatt_queue_, WriteDescriptor(_, _, _, _, _, _))
        .WillByDefault(Invoke([this](uint16_t conn_id, uint16_t handle,
                                     std::vector<uint8_t> value,
                                     tGATT_WRITE_TYPE write_type,
                                     GATT_WRITE_OP_CB cb,
                                     void* cb_data) -> void {
          auto& ascs = peer_devices.at(conn_id)->ascs;
          uint8_t idx;

          if (handle == ascs->ctp_ccc) {
            value = UINT16_TO_VEC_UINT8(ascs->ctp_ccc_val);
          } else {
            for (idx = 0; idx < max_num_of_ases; idx++) {
              if (handle == ascs->sink_ase_ccc[idx] + 1) {
                value = UINT16_TO_VEC_UINT8(ascs->sink_ase_ccc_val[idx]);
                break;
              }
              if (handle == ascs->source_ase_char[idx] + 1) {
                value = UINT16_TO_VEC_UINT8(ascs->source_ase_ccc_val[idx]);
                break;
              }
            }
          }

          if (cb)
            do_in_main_thread(
                FROM_HERE,
                base::BindOnce(
                    [](GATT_WRITE_OP_CB cb, uint16_t conn_id, uint16_t handle,
                       uint16_t len, uint8_t* value, void* cb_data) {
                      cb(conn_id, GATT_SUCCESS, handle, len, value, cb_data);
                    },
                    cb, conn_id, handle, value.size(), value.data(), cb_data));
        }));

    global_conn_id = 1;
    ON_CALL(mock_gatt_interface_, Open(_, _, BTM_BLE_DIRECT_CONNECTION, _))
        .WillByDefault(
            Invoke([&](tGATT_IF client_if, const RawAddress& remote_bda,
                       bool is_direct, bool opportunistic) {
              InjectConnectedEvent(remote_bda, global_conn_id++);
            }));

    ON_CALL(mock_gatt_interface_, Close(_))
        .WillByDefault(Invoke([&](uint16_t conn_id) {
          ASSERT_NE(conn_id, GATT_INVALID_CONN_ID);
          InjectDisconnectedEvent(conn_id);
        }));

    // default Characteristic read handler dispatches requests to service mocks
    ON_CALL(mock_gatt_queue_, ReadCharacteristic(_, _, _, _))
        .WillByDefault(Invoke([&](uint16_t conn_id, uint16_t handle,
                                  GATT_READ_OP_CB cb, void* cb_data) {
          do_in_main_thread(
              FROM_HERE,
              base::BindOnce(
                  [](std::map<uint16_t,
                              std::unique_ptr<NiceMock<MockDeviceWrapper>>>*
                         peer_devices,
                     uint16_t conn_id, uint16_t handle, GATT_READ_OP_CB cb,
                     void* cb_data) -> void {
                    if (peer_devices->count(conn_id)) {
                      auto& device = peer_devices->at(conn_id);
                      auto svc = std::find_if(
                          device->services.begin(), device->services.end(),
                          [handle](const gatt::Service& svc) {
                            return (handle >= svc.handle) &&
                                   (handle <= svc.end_handle);
                          });
                      if (svc == device->services.end()) return;

                      // Dispatch to mockable handler functions
                      if (svc->handle == device->csis->start) {
                        device->csis->OnReadCharacteristic(handle, cb, cb_data);
                      } else if (svc->handle == device->cas->start) {
                        device->cas->OnReadCharacteristic(handle, cb, cb_data);
                      } else if (svc->handle == device->ascs->start) {
                        device->ascs->OnReadCharacteristic(handle, cb, cb_data);
                      } else if (svc->handle == device->pacs->start) {
                        device->pacs->OnReadCharacteristic(handle, cb, cb_data);
                      }
                    }
                  },
                  &peer_devices, conn_id, handle, cb, cb_data));
        }));
  }

  void SetUpMockGroups() {
    MockCsisClient::SetMockInstanceForTesting(&mock_csis_client_module_);
    MockDeviceGroups::SetMockInstanceForTesting(&mock_groups_module_);
    MockLeAudioGroupStateMachine::SetMockInstanceForTesting(
        &mock_state_machine_);

    ON_CALL(mock_csis_client_module_, Get())
        .WillByDefault(Return(&mock_csis_client_module_));

    // Store group callbacks so that we could inject grouping events
    group_callbacks_ = nullptr;
    ON_CALL(mock_groups_module_, Initialize(_))
        .WillByDefault(SaveArg<0>(&group_callbacks_));

    ON_CALL(mock_groups_module_, GetGroupId(_, _))
        .WillByDefault([this](const RawAddress& addr, bluetooth::Uuid uuid) {
          if (groups.find(addr) != groups.end()) return groups.at(addr);
          return bluetooth::groups::kGroupUnknown;
        });

    ON_CALL(mock_groups_module_, RemoveDevice(_, _))
        .WillByDefault([this](const RawAddress& addr, int group_id_) {
          int group_id = -1;
          if (groups.find(addr) != groups.end()) {
            group_id = groups[addr];
            groups.erase(addr);
          }
          if (group_id < 0) return;

          do_in_main_thread(
              FROM_HERE,
              base::BindOnce(
                  [](const RawAddress& address, int group_id,
                     bluetooth::groups::DeviceGroupsCallbacks*
                         group_callbacks) {
                    group_callbacks->OnGroupMemberRemoved(address, group_id);
                  },
                  addr, group_id, base::Unretained(group_callbacks_)));
        });

    // Our test devices have unique LSB - use it for unique grouping when
    // devices added with a non-CIS context and no grouping info
    ON_CALL(mock_groups_module_,
            AddDevice(_, le_audio::uuid::kCapServiceUuid, _))
        .WillByDefault(
            [this](const RawAddress& addr,
                   bluetooth::Uuid uuid = le_audio::uuid::kCapServiceUuid,
                   int group_id = bluetooth::groups::kGroupUnknown) -> int {
              if (group_id == bluetooth::groups::kGroupUnknown) {
                /* Generate group id from address */
                groups[addr] = addr.address[RawAddress::kLength - 1];
                group_id = groups[addr];
              } else {
                groups[addr] = group_id;
              }

              InjectGroupDeviceAdded(addr, groups[addr]);
              return addr.address[RawAddress::kLength - 1];
            });

    ON_CALL(mock_state_machine_, Initialize(_))
        .WillByDefault(SaveArg<0>(&state_machine_callbacks_));

    ON_CALL(mock_state_machine_, ConfigureStream(_, _, _, _))
        .WillByDefault(
            [this](LeAudioDeviceGroup* group,
                   types::LeAudioContextType context_type,
                   types::BidirectionalPair<types::AudioContexts>
                       metadata_context_types,
                   types::BidirectionalPair<std::vector<uint8_t>> ccid_lists) {
              bool isReconfiguration = group->IsPendingConfiguration();

              /* This shall be called only for user reconfiguration */
              if (!isReconfiguration) return false;

              /* Do what ReleaseCisIds(group) does: start */
              LeAudioDevice* leAudioDevice = group->GetFirstDevice();
              while (leAudioDevice != nullptr) {
                for (auto& ase : leAudioDevice->ases_) {
                  ase.cis_id = le_audio::kInvalidCisId;
                }
                leAudioDevice = group->GetNextDevice(leAudioDevice);
              }
              group->ClearAllCises();
              /* end */

              if (!group->Configure(context_type, metadata_context_types,
                                    ccid_lists)) {
                LOG_ERROR(
                    "Could not configure ASEs for group %d content type %d",
                    group->group_id_, int(context_type));

                return false;
              }

              group->cig.GenerateCisIds(context_type);

              for (LeAudioDevice* device = group->GetFirstDevice();
                   device != nullptr; device = group->GetNextDevice(device)) {
                for (auto& ase : device->ases_) {
                  ase.cis_state = types::CisState::IDLE;
                  ase.data_path_state = types::DataPathState::IDLE;
                  ase.active = false;
                  ase.state =
                      types::AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED;
                }
              }

              // Inject the state
              group->SetTargetState(
                  types::AseState::BTA_LE_AUDIO_ASE_STATE_CODEC_CONFIGURED);
              group->SetState(group->GetTargetState());
              group->ClearPendingConfiguration();
              do_in_main_thread(
                  FROM_HERE,
                  base::BindOnce(
                      [](int group_id,
                         le_audio::LeAudioGroupStateMachine::Callbacks*
                             state_machine_callbacks) {
                        state_machine_callbacks->StatusReportCb(
                            group_id, GroupStreamStatus::CONFIGURED_BY_USER);
                      },
                      group->group_id_,
                      base::Unretained(this->state_machine_callbacks_)));
              return true;
            });

    ON_CALL(mock_state_machine_, AttachToStream(_, _, _))
        .WillByDefault([](LeAudioDeviceGroup* group,
                          LeAudioDevice* leAudioDevice,
                          types::BidirectionalPair<std::vector<uint8_t>>
                              ccids) {
          if (group->GetState() !=
              types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
            return false;
          }

          group->Configure(group->GetConfigurationContextType(),
                           group->GetMetadataContexts(), ccids);
          if (!group->cig.AssignCisIds(leAudioDevice)) return false;
          group->AssignCisConnHandlesToAses(leAudioDevice);

          auto* stream_conf = &group->stream_conf;

          for (auto& ase : leAudioDevice->ases_) {
            if (!ase.active) continue;

            // And also skip the ase establishment procedure which should
            // be tested as part of the state machine unit tests
            ase.cis_state = types::CisState::CONNECTED;
            ase.data_path_state = types::DataPathState::CONFIGURED;
            ase.state = types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING;

            uint16_t cis_conn_hdl = ase.cis_conn_hdl;
            auto core_config = ase.codec_config.GetAsCoreCodecConfig();

            /* Copied from state_machine.cc ProcessHciNotifSetupIsoDataPath */
            if (ase.direction == le_audio::types::kLeAudioDirectionSource) {
              auto iter = std::find_if(
                  stream_conf->stream_params.source.stream_locations.begin(),
                  stream_conf->stream_params.source.stream_locations.end(),
                  [cis_conn_hdl](auto& pair) {
                    return cis_conn_hdl == pair.first;
                  });

              if (iter ==
                  stream_conf->stream_params.source.stream_locations.end()) {
                stream_conf->stream_params.source.stream_locations.emplace_back(
                    std::make_pair(ase.cis_conn_hdl,
                                   *core_config.audio_channel_allocation));

                stream_conf->stream_params.source.num_of_devices++;
                stream_conf->stream_params.source.num_of_channels +=
                    core_config.GetChannelCountPerIsoStream();

                LOG_INFO(
                    " Added Source Stream Configuration. CIS Connection "
                    "Handle: %d"
                    ", Audio Channel Allocation: %d"
                    ", Source Number Of Devices: %d"
                    ", Source Number Of Channels: %d",
                    +ase.cis_conn_hdl, +(*core_config.audio_channel_allocation),
                    +stream_conf->stream_params.source.num_of_devices,
                    +stream_conf->stream_params.source.num_of_channels);
              }
            } else {
              auto iter = std::find_if(
                  stream_conf->stream_params.sink.stream_locations.begin(),
                  stream_conf->stream_params.sink.stream_locations.end(),
                  [cis_conn_hdl](auto& pair) {
                    return cis_conn_hdl == pair.first;
                  });

              if (iter ==
                  stream_conf->stream_params.sink.stream_locations.end()) {
                stream_conf->stream_params.sink.stream_locations.emplace_back(
                    std::make_pair(ase.cis_conn_hdl,
                                   *core_config.audio_channel_allocation));

                stream_conf->stream_params.sink.num_of_devices++;
                stream_conf->stream_params.sink.num_of_channels +=
                    core_config.GetChannelCountPerIsoStream();

                LOG_INFO(
                    " Added Sink Stream Configuration. CIS Connection Handle: "
                    "%d"
                    ", Audio Channel Allocation: %d"
                    ", Sink Number Of Devices: %d"
                    ", Sink Number Of Channels: %d",
                    +ase.cis_conn_hdl, +(*core_config.audio_channel_allocation),
                    +stream_conf->stream_params.sink.num_of_devices,
                    +stream_conf->stream_params.sink.num_of_channels);
              }
            }
          }

          return true;
        });

    ON_CALL(mock_state_machine_, StartStream(_, _, _, _))
        .WillByDefault([this](LeAudioDeviceGroup* group,
                              types::LeAudioContextType context_type,
                              types::BidirectionalPair<types::AudioContexts>
                                  metadata_context_types,
                              types::BidirectionalPair<std::vector<uint8_t>>
                                  ccid_lists) {
          /* Do nothing if already streaming - the implementation would
           * probably update the metadata.
           */
          if (group->GetState() ==
              types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING) {
            return true;
          }

          /* Do what ReleaseCisIds(group) does: start */
          LeAudioDevice* leAudioDevice = group->GetFirstDevice();
          while (leAudioDevice != nullptr) {
            for (auto& ase : leAudioDevice->ases_) {
              ase.cis_id = le_audio::kInvalidCisId;
            }
            leAudioDevice = group->GetNextDevice(leAudioDevice);
          }
          group->ClearAllCises();
          /* end */

          if (!group->Configure(context_type, metadata_context_types,
                                ccid_lists)) {
            LOG(ERROR) << __func__ << ", failed to set ASE configuration";
            return false;
          }

          if (group->GetState() ==
              types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE) {
            group->cig.GenerateCisIds(context_type);

            std::vector<uint16_t> conn_handles;
            for (uint8_t i = 0; i < (uint8_t)(group->cig.cises.size()); i++) {
              conn_handles.push_back(iso_con_counter_++);
            }
            group->cig.AssignCisConnHandles(conn_handles);
            for (LeAudioDevice* device = group->GetFirstActiveDevice();
                 device != nullptr;
                 device = group->GetNextActiveDevice(device)) {
              if (!group->cig.AssignCisIds(device)) return false;
              group->AssignCisConnHandlesToAses(device);
            }
          }

          auto* stream_conf = &group->stream_conf;

          // Fake ASE configuration
          for (LeAudioDevice* device = group->GetFirstActiveDevice();
               device != nullptr; device = group->GetNextActiveDevice(device)) {
            for (auto& ase : device->ases_) {
              if (!ase.active) continue;

              // And also skip the ase establishment procedure which should
              // be tested as part of the state machine unit tests
              ase.cis_state = types::CisState::CONNECTED;
              ase.data_path_state = types::DataPathState::CONFIGURED;
              ase.state = types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING;
              ase.pres_delay_min = 2500;
              ase.pres_delay_max = 2500;
              ase.preferred_pres_delay_min = 2500;
              ase.preferred_pres_delay_max = 2500;
              auto core_config = ase.codec_config.GetAsCoreCodecConfig();

              uint16_t cis_conn_hdl = ase.cis_conn_hdl;

              /* Copied from state_machine.cc ProcessHciNotifSetupIsoDataPath */
              if (ase.direction == le_audio::types::kLeAudioDirectionSource) {
                auto iter = std::find_if(
                    stream_conf->stream_params.source.stream_locations.begin(),
                    stream_conf->stream_params.source.stream_locations.end(),
                    [cis_conn_hdl](auto& pair) {
                      return cis_conn_hdl == pair.first;
                    });

                if (iter ==
                    stream_conf->stream_params.source.stream_locations.end()) {
                  stream_conf->stream_params.source.stream_locations
                      .emplace_back(std::make_pair(
                          ase.cis_conn_hdl,
                          *core_config.audio_channel_allocation));

                  stream_conf->stream_params.source.num_of_devices++;
                  stream_conf->stream_params.source.num_of_channels +=
                      core_config.GetChannelCountPerIsoStream();
                  stream_conf->stream_params.source.audio_channel_allocation |=
                      *core_config.audio_channel_allocation;

                  if (stream_conf->stream_params.source.sample_frequency_hz ==
                      0) {
                    stream_conf->stream_params.source.sample_frequency_hz =
                        core_config.GetSamplingFrequencyHz();
                  } else {
                    ASSERT_LOG(
                        stream_conf->stream_params.source.sample_frequency_hz ==
                            core_config.GetSamplingFrequencyHz(),
                        "sample freq mismatch: %d!=%d",
                        stream_conf->stream_params.source.sample_frequency_hz,
                        core_config.GetSamplingFrequencyHz());
                  }

                  if (stream_conf->stream_params.source
                          .octets_per_codec_frame == 0) {
                    stream_conf->stream_params.source.octets_per_codec_frame =
                        *core_config.octets_per_codec_frame;
                  } else {
                    ASSERT_LOG(stream_conf->stream_params.source
                                       .octets_per_codec_frame ==
                                   *core_config.octets_per_codec_frame,
                               "octets per frame mismatch: %d!=%d",
                               stream_conf->stream_params.source
                                   .octets_per_codec_frame,
                               *core_config.octets_per_codec_frame);
                  }

                  if (stream_conf->stream_params.source
                          .codec_frames_blocks_per_sdu == 0) {
                    stream_conf->stream_params.source
                        .codec_frames_blocks_per_sdu =
                        *core_config.codec_frames_blocks_per_sdu;
                  } else {
                    ASSERT_LOG(stream_conf->stream_params.source
                                       .codec_frames_blocks_per_sdu ==
                                   *core_config.codec_frames_blocks_per_sdu,
                               "codec_frames_blocks_per_sdu: %d!=%d",
                               stream_conf->stream_params.source
                                   .codec_frames_blocks_per_sdu,
                               *core_config.codec_frames_blocks_per_sdu);
                  }

                  LOG_INFO(
                      " Added Source Stream Configuration. CIS Connection "
                      "Handle: %d"
                      ", Audio Channel Allocation: %d"
                      ", Source Number Of Devices: %d"
                      ", Source Number Of Channels: %d",
                      +ase.cis_conn_hdl,
                      +(*core_config.audio_channel_allocation),
                      +stream_conf->stream_params.source.num_of_devices,
                      +stream_conf->stream_params.source.num_of_channels);
                }
              } else {
                auto iter = std::find_if(
                    stream_conf->stream_params.sink.stream_locations.begin(),
                    stream_conf->stream_params.sink.stream_locations.end(),
                    [cis_conn_hdl](auto& pair) {
                      return cis_conn_hdl == pair.first;
                    });

                if (iter ==
                    stream_conf->stream_params.sink.stream_locations.end()) {
                  stream_conf->stream_params.sink.stream_locations.emplace_back(
                      std::make_pair(ase.cis_conn_hdl,
                                     *core_config.audio_channel_allocation));

                  stream_conf->stream_params.sink.num_of_devices++;
                  stream_conf->stream_params.sink.num_of_channels +=
                      core_config.GetChannelCountPerIsoStream();

                  stream_conf->stream_params.sink.audio_channel_allocation |=
                      *core_config.audio_channel_allocation;

                  if (stream_conf->stream_params.sink.sample_frequency_hz ==
                      0) {
                    stream_conf->stream_params.sink.sample_frequency_hz =
                        core_config.GetSamplingFrequencyHz();
                  } else {
                    ASSERT_LOG(
                        stream_conf->stream_params.sink.sample_frequency_hz ==
                            core_config.GetSamplingFrequencyHz(),
                        "sample freq mismatch: %d!=%d",
                        stream_conf->stream_params.sink.sample_frequency_hz,
                        core_config.GetSamplingFrequencyHz());
                  }

                  if (stream_conf->stream_params.sink.octets_per_codec_frame ==
                      0) {
                    stream_conf->stream_params.sink.octets_per_codec_frame =
                        *core_config.octets_per_codec_frame;
                  } else {
                    ASSERT_LOG(
                        stream_conf->stream_params.sink
                                .octets_per_codec_frame ==
                            *core_config.octets_per_codec_frame,
                        "octets per frame mismatch: %d!=%d",
                        stream_conf->stream_params.sink.octets_per_codec_frame,
                        *core_config.octets_per_codec_frame);
                  }

                  if (stream_conf->stream_params.sink
                          .codec_frames_blocks_per_sdu == 0) {
                    stream_conf->stream_params.sink
                        .codec_frames_blocks_per_sdu =
                        *core_config.codec_frames_blocks_per_sdu;
                  } else {
                    ASSERT_LOG(stream_conf->stream_params.sink
                                       .codec_frames_blocks_per_sdu ==
                                   *core_config.codec_frames_blocks_per_sdu,
                               "codec_frames_blocks_per_sdu: %d!=%d",
                               stream_conf->stream_params.sink
                                   .codec_frames_blocks_per_sdu,
                               *core_config.codec_frames_blocks_per_sdu);
                  }

                  LOG_INFO(
                      " Added Sink Stream Configuration. CIS Connection "
                      "Handle: %d"
                      ", Audio Channel Allocation: %d"
                      ", Sink Number Of Devices: %d"
                      ", Sink Number Of Channels: %d",
                      +ase.cis_conn_hdl,
                      +(*core_config.audio_channel_allocation),
                      +stream_conf->stream_params.sink.num_of_devices,
                      +stream_conf->stream_params.sink.num_of_channels);
                }
              }
            }
          }

          // Inject the state
          group->SetTargetState(
              types::AseState::BTA_LE_AUDIO_ASE_STATE_STREAMING);
          group->SetState(group->GetTargetState());
          streaming_groups[group->group_id_] = group;

          /* Assume CIG is created */
          group->cig.SetState(le_audio::types::CigState::CREATED);

          if (block_streaming_state_callback) return true;

          do_in_main_thread(
              FROM_HERE, base::BindOnce(
                             [](int group_id,
                                le_audio::LeAudioGroupStateMachine::Callbacks*
                                    state_machine_callbacks) {
                               state_machine_callbacks->StatusReportCb(
                                   group_id, GroupStreamStatus::STREAMING);
                             },
                             group->group_id_,
                             base::Unretained(this->state_machine_callbacks_)));
          return true;
        });

    ON_CALL(mock_state_machine_, SuspendStream(_))
        .WillByDefault([this](LeAudioDeviceGroup* group) {
          // Fake ASE state
          for (LeAudioDevice* device = group->GetFirstDevice();
               device != nullptr; device = group->GetNextDevice(device)) {
            for (auto& ase : device->ases_) {
              ase.cis_state = types::CisState::CONNECTED;
              ase.active = false;
              ase.state =
                  types::AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED;
            }
          }

          // Inject the state
          group->SetTargetState(
              types::AseState::BTA_LE_AUDIO_ASE_STATE_QOS_CONFIGURED);
          group->SetState(group->GetTargetState());
          state_machine_callbacks_->StatusReportCb(
              group->group_id_, GroupStreamStatus::SUSPENDED);
        });

    ON_CALL(mock_state_machine_, ProcessHciNotifAclDisconnected(_, _))
        .WillByDefault([this](LeAudioDeviceGroup* group,
                              LeAudioDevice* leAudioDevice) {
          if (!group) return;
          auto* stream_conf = &group->stream_conf;
          if (!stream_conf->stream_params.sink.stream_locations.empty() ||
              !stream_conf->stream_params.source.stream_locations.empty()) {
            stream_conf->stream_params.sink.stream_locations.erase(
                std::remove_if(
                    stream_conf->stream_params.sink.stream_locations.begin(),
                    stream_conf->stream_params.sink.stream_locations.end(),
                    [leAudioDevice, &stream_conf](auto& pair) {
                      auto ases =
                          leAudioDevice->GetAsesByCisConnHdl(pair.first);
                      if (ases.sink) {
                        stream_conf->stream_params.sink.num_of_devices--;
                        stream_conf->stream_params.sink.num_of_channels -=
                            ases.sink->codec_config.GetAsCoreCodecConfig()
                                .GetChannelCountPerIsoStream();

                        LOG_INFO(
                            ", Source Number Of Devices: %d"
                            ", Source Number Of Channels: %d",
                            +stream_conf->stream_params.source.num_of_devices,
                            +stream_conf->stream_params.source.num_of_channels);
                      }
                      return ases.sink;
                    }),
                stream_conf->stream_params.sink.stream_locations.end());

            stream_conf->stream_params.source.stream_locations.erase(
                std::remove_if(
                    stream_conf->stream_params.source.stream_locations.begin(),
                    stream_conf->stream_params.source.stream_locations.end(),
                    [leAudioDevice, &stream_conf](auto& pair) {
                      auto ases =
                          leAudioDevice->GetAsesByCisConnHdl(pair.first);
                      if (ases.source) {
                        stream_conf->stream_params.source.num_of_devices--;
                        stream_conf->stream_params.source.num_of_channels -=
                            ases.source->codec_config.GetAsCoreCodecConfig()
                                .GetChannelCountPerIsoStream();

                        LOG_INFO(
                            ", Source Number Of Devices: %d"
                            ", Source Number Of Channels: %d",
                            +stream_conf->stream_params.source.num_of_devices,
                            +stream_conf->stream_params.source.num_of_channels);
                      }
                      return ases.source;
                    }),
                stream_conf->stream_params.source.stream_locations.end());
          }

          group->cig.UnassignCis(leAudioDevice);

          if (group->IsEmpty()) {
            group->cig.SetState(le_audio::types::CigState::NONE);
            InjectCigRemoved(group->group_id_);
          }
        });

    ON_CALL(mock_state_machine_, ProcessHciNotifCisDisconnected(_, _, _))
        .WillByDefault([](LeAudioDeviceGroup* group,
                          LeAudioDevice* leAudioDevice,
                          const bluetooth::hci::iso_manager::
                              cis_disconnected_evt* event) {
          if (!group) return;
          auto ases_pair =
              leAudioDevice->GetAsesByCisConnHdl(event->cis_conn_hdl);
          if (ases_pair.sink) {
            ases_pair.sink->cis_state = types::CisState::ASSIGNED;
            ases_pair.sink->active = false;
          }
          if (ases_pair.source) {
            ases_pair.source->active = false;
            ases_pair.source->cis_state = types::CisState::ASSIGNED;
          }
          /* Invalidate stream configuration if needed */
          auto* stream_conf = &group->stream_conf;
          if (!stream_conf->stream_params.sink.stream_locations.empty() ||
              !stream_conf->stream_params.source.stream_locations.empty()) {
            stream_conf->stream_params.sink.stream_locations.erase(
                std::remove_if(
                    stream_conf->stream_params.sink.stream_locations.begin(),
                    stream_conf->stream_params.sink.stream_locations.end(),
                    [leAudioDevice, &stream_conf](auto& pair) {
                      auto ases =
                          leAudioDevice->GetAsesByCisConnHdl(pair.first);

                      LOG_INFO(
                          ", sink ase to delete. Cis handle: %d"
                          ", ase pointer: %p",
                          +(int)(pair.first), +ases.sink);
                      if (ases.sink) {
                        stream_conf->stream_params.sink.num_of_devices--;
                        stream_conf->stream_params.sink.num_of_channels -=
                            ases.sink->codec_config.GetAsCoreCodecConfig()
                                .GetChannelCountPerIsoStream();

                        LOG_INFO(
                            " Sink Number Of Devices: %d"
                            ", Sink Number Of Channels: %d",
                            +stream_conf->stream_params.sink.num_of_devices,
                            +stream_conf->stream_params.sink.num_of_channels);
                      }
                      return ases.sink;
                    }),
                stream_conf->stream_params.sink.stream_locations.end());

            stream_conf->stream_params.source.stream_locations.erase(
                std::remove_if(
                    stream_conf->stream_params.source.stream_locations.begin(),
                    stream_conf->stream_params.source.stream_locations.end(),
                    [leAudioDevice, &stream_conf](auto& pair) {
                      auto ases =
                          leAudioDevice->GetAsesByCisConnHdl(pair.first);

                      LOG_INFO(
                          ", source to delete. Cis handle: %d"
                          ", ase pointer: %p",
                          +(int)(pair.first), ases.source);
                      if (ases.source) {
                        stream_conf->stream_params.source.num_of_devices--;
                        stream_conf->stream_params.source.num_of_channels -=
                            ases.source->codec_config.GetAsCoreCodecConfig()
                                .GetChannelCountPerIsoStream();

                        LOG_INFO(
                            ", Source Number Of Devices: %d"
                            ", Source Number Of Channels: %d",
                            +stream_conf->stream_params.source.num_of_devices,
                            +stream_conf->stream_params.source.num_of_channels);
                      }
                      return ases.source;
                    }),
                stream_conf->stream_params.source.stream_locations.end());
          }

          group->cig.UnassignCis(leAudioDevice);
        });

    ON_CALL(mock_state_machine_, StopStream(_))
        .WillByDefault([this](LeAudioDeviceGroup* group) {
          for (LeAudioDevice* device = group->GetFirstDevice();
               device != nullptr; device = group->GetNextDevice(device)) {
            /* Invalidate stream configuration if needed */
            auto* stream_conf = &group->stream_conf;
            if (!stream_conf->stream_params.sink.stream_locations.empty() ||
                !stream_conf->stream_params.source.stream_locations.empty()) {
              stream_conf->stream_params.sink.stream_locations.erase(
                  std::remove_if(
                      stream_conf->stream_params.sink.stream_locations.begin(),
                      stream_conf->stream_params.sink.stream_locations.end(),
                      [device, &stream_conf](auto& pair) {
                        auto ases = device->GetAsesByCisConnHdl(pair.first);

                        LOG_INFO(
                            ", sink ase to delete. Cis handle: %d"
                            ", ase pointer: %p",
                            +(int)(pair.first), +ases.sink);
                        if (ases.sink) {
                          stream_conf->stream_params.sink.num_of_devices--;
                          stream_conf->stream_params.sink.num_of_channels -=
                              ases.sink->codec_config.GetAsCoreCodecConfig()
                                  .GetChannelCountPerIsoStream();

                          LOG_INFO(
                              " Sink Number Of Devices: %d"
                              ", Sink Number Of Channels: %d",
                              +stream_conf->stream_params.sink.num_of_devices,
                              +stream_conf->stream_params.sink.num_of_channels);
                        }
                        return ases.sink;
                      }),
                  stream_conf->stream_params.sink.stream_locations.end());

              stream_conf->stream_params.source.stream_locations.erase(
                  std::remove_if(
                      stream_conf->stream_params.source.stream_locations
                          .begin(),
                      stream_conf->stream_params.source.stream_locations.end(),
                      [device, &stream_conf](auto& pair) {
                        auto ases = device->GetAsesByCisConnHdl(pair.first);

                        LOG_INFO(
                            ", source to delete. Cis handle: %d"
                            ", ase pointer: %p",
                            +(int)(pair.first), +ases.source);
                        if (ases.source) {
                          stream_conf->stream_params.source.num_of_devices--;
                          stream_conf->stream_params.source.num_of_channels -=
                              ases.source->codec_config.GetAsCoreCodecConfig()
                                  .GetChannelCountPerIsoStream();

                          LOG_INFO(
                              ", Source Number Of Devices: %d"
                              ", Source Number Of Channels: %d",
                              +stream_conf->stream_params.source.num_of_devices,
                              +stream_conf->stream_params.source
                                   .num_of_channels);
                        }
                        return ases.source;
                      }),
                  stream_conf->stream_params.source.stream_locations.end());
            }

            group->cig.UnassignCis(device);

            for (auto& ase : device->ases_) {
              ase.cis_state = types::CisState::IDLE;
              ase.data_path_state = types::DataPathState::IDLE;
              ase.active = false;
              ase.state = types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE;
              ase.cis_id = 0;
              ase.cis_conn_hdl = 0;
            }
          }

          // Inject the state
          group->SetTargetState(types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
          group->SetState(group->GetTargetState());
          state_machine_callbacks_->StatusReportCb(
              group->group_id_, GroupStreamStatus::RELEASING);

          do_in_main_thread(
              FROM_HERE,
              base::BindOnce(
                  [](le_audio::LeAudioGroupStateMachine::Callbacks* cb,
                     int group_id) {
                    cb->StatusReportCb(group_id, GroupStreamStatus::IDLE);
                  },
                  state_machine_callbacks_, group->group_id_));
        });
  }

  void SetUp() override {
    init_message_loop_thread();
    ON_CALL(controller_interface_, SupportsBleConnectedIsochronousStreamCentral)
        .WillByDefault(Return(true));
    ON_CALL(controller_interface_,
            SupportsBleConnectedIsochronousStreamPeripheral)
        .WillByDefault(Return(true));

    controller::SetMockControllerInterface(&controller_interface_);
    bluetooth::manager::SetMockBtmInterface(&mock_btm_interface_);
    gatt::SetMockBtaGattInterface(&mock_gatt_interface_);
    gatt::SetMockBtaGattQueue(&mock_gatt_queue_);
    bluetooth::storage::SetMockBtifStorageInterface(&mock_btif_storage_);

    iso_manager_ = bluetooth::hci::IsoManager::GetInstance();
    ASSERT_NE(iso_manager_, nullptr);
    iso_manager_->Start();

    mock_iso_manager_ = MockIsoManager::GetInstance();
    ON_CALL(*mock_iso_manager_, RegisterCigCallbacks(_))
        .WillByDefault(SaveArg<0>(&cig_callbacks_));

    // Required since we call OnAudioDataReady()
    const auto codec_location = ::le_audio::types::CodecLocation::HOST;

    SetUpMockAudioHal();
    SetUpMockGroups();
    SetUpMockGatt();
    SetUpMockCodecManager(codec_location);

    block_streaming_state_callback = false;

    available_snk_context_types_ = 0xffff;
    available_src_context_types_ = 0xffff;
    supported_snk_context_types_ = 0xffff;
    supported_src_context_types_ = 0xffff;

    le_audio::AudioSetConfigurationProvider::Initialize(codec_location);
    ASSERT_FALSE(LeAudioClient::IsLeAudioClientRunning());
  }

  void SetUpMockCodecManager(types::CodecLocation location) {
    codec_manager_ = le_audio::CodecManager::GetInstance();
    ASSERT_NE(codec_manager_, nullptr);
    std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
        mock_offloading_preference(0);
    codec_manager_->Start(mock_offloading_preference);
    mock_codec_manager_ = MockCodecManager::GetInstance();
    ASSERT_NE((void*)mock_codec_manager_, (void*)codec_manager_);
    ASSERT_NE(mock_codec_manager_, nullptr);
    ON_CALL(*mock_codec_manager_, GetCodecLocation())
        .WillByDefault(Return(location));
  }

  void TearDown() override {
    if (is_audio_unicast_source_acquired) {
      if (unicast_source_hal_cb_ != nullptr) {
        EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop).Times(1);
      }
      EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
    }

    if (is_audio_unicast_sink_acquired) {
      if (unicast_sink_hal_cb_ != nullptr) {
        EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop).Times(1);
      }
      EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
    }

    // Message loop cleanup should wait for all the 'till now' scheduled calls
    // so it should be called right at the very begginning of teardown.
    cleanup_message_loop_thread();

    // This is required since Stop() and Cleanup() may trigger some callbacks or
    // drop unique pointers to mocks we have raw pointer for and we want to
    // verify them all.
    Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

    if (LeAudioClient::IsLeAudioClientRunning()) {
      EXPECT_CALL(mock_gatt_interface_, AppDeregister(gatt_if)).Times(1);
      LeAudioClient::Cleanup();
      ASSERT_FALSE(LeAudioClient::IsLeAudioClientRunning());
    }

    owned_mock_le_audio_sink_hal_client_.reset();
    owned_mock_le_audio_source_hal_client_.reset();

    if (le_audio::AudioSetConfigurationProvider::Get())
      le_audio::AudioSetConfigurationProvider::Cleanup();

    iso_manager_->Stop();
  }

 protected:
  class MockDeviceWrapper {
    class IGattHandlers {
     public:
      // IGattHandlers() = default;
      virtual ~IGattHandlers() = default;
      virtual void OnReadCharacteristic(uint16_t handle, GATT_READ_OP_CB cb,
                                        void* cb_data) = 0;
      virtual void OnWriteCharacteristic(uint16_t handle,
                                         std::vector<uint8_t> value,
                                         tGATT_WRITE_TYPE write_type,
                                         GATT_WRITE_OP_CB cb,
                                         void* cb_data) = 0;
    };

   public:
    struct csis_mock : public IGattHandlers {
      uint16_t start = 0;
      uint16_t end = 0;
      uint16_t sirk_char = 0;
      uint16_t sirk_ccc = 0;
      uint16_t size_char = 0;
      uint16_t size_ccc = 0;
      uint16_t lock_char = 0;
      uint16_t lock_ccc = 0;
      uint16_t rank_char = 0;

      int rank = 0;
      int size = 0;

      MOCK_METHOD((void), OnReadCharacteristic,
                  (uint16_t handle, GATT_READ_OP_CB cb, void* cb_data),
                  (override));
      MOCK_METHOD((void), OnWriteCharacteristic,
                  (uint16_t handle, std::vector<uint8_t> value,
                   tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
                   void* cb_data),
                  (override));
    };

    struct cas_mock : public IGattHandlers {
      uint16_t start = 0;
      uint16_t end = 0;
      uint16_t csis_include = 0;

      MOCK_METHOD((void), OnReadCharacteristic,
                  (uint16_t handle, GATT_READ_OP_CB cb, void* cb_data),
                  (override));
      MOCK_METHOD((void), OnWriteCharacteristic,
                  (uint16_t handle, std::vector<uint8_t> value,
                   tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
                   void* cb_data),
                  (override));
    };

    struct pacs_mock : public IGattHandlers {
      uint16_t start = 0;
      uint16_t sink_pac_char = 0;
      uint16_t sink_pac_ccc = 0;
      uint16_t sink_audio_loc_char = 0;
      uint16_t sink_audio_loc_ccc = 0;
      uint16_t source_pac_char = 0;
      uint16_t source_pac_ccc = 0;
      uint16_t source_audio_loc_char = 0;
      uint16_t source_audio_loc_ccc = 0;
      uint16_t avail_contexts_char = 0;
      uint16_t avail_contexts_ccc = 0;
      uint16_t supp_contexts_char = 0;
      uint16_t supp_contexts_ccc = 0;
      uint16_t end = 0;

      MOCK_METHOD((void), OnReadCharacteristic,
                  (uint16_t handle, GATT_READ_OP_CB cb, void* cb_data),
                  (override));
      MOCK_METHOD((void), OnWriteCharacteristic,
                  (uint16_t handle, std::vector<uint8_t> value,
                   tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
                   void* cb_data),
                  (override));
    };

    struct ascs_mock : public IGattHandlers {
      uint16_t start = 0;
      uint16_t sink_ase_char[max_num_of_ases] = {0};
      uint16_t sink_ase_ccc[max_num_of_ases] = {0};
      uint16_t sink_ase_ccc_val[max_num_of_ases] = {0};
      uint16_t source_ase_char[max_num_of_ases] = {0};
      uint16_t source_ase_ccc[max_num_of_ases] = {0};
      uint16_t source_ase_ccc_val[max_num_of_ases] = {0};
      uint16_t ctp_char = 0;
      uint16_t ctp_ccc = 0;
      uint16_t ctp_ccc_val = 0;
      uint16_t end = 0;

      MOCK_METHOD((void), OnReadCharacteristic,
                  (uint16_t handle, GATT_READ_OP_CB cb, void* cb_data),
                  (override));
      MOCK_METHOD((void), OnWriteCharacteristic,
                  (uint16_t handle, std::vector<uint8_t> value,
                   tGATT_WRITE_TYPE write_type, GATT_WRITE_OP_CB cb,
                   void* cb_data),
                  (override));
    };

    MockDeviceWrapper(
        RawAddress addr, const std::list<gatt::Service>& services,
        std::unique_ptr<NiceMock<MockDeviceWrapper::csis_mock>> csis,
        std::unique_ptr<NiceMock<MockDeviceWrapper::cas_mock>> cas,
        std::unique_ptr<NiceMock<MockDeviceWrapper::ascs_mock>> ascs,
        std::unique_ptr<NiceMock<MockDeviceWrapper::pacs_mock>> pacs)
        : addr(addr) {
      this->services = services;
      this->csis = std::move(csis);
      this->cas = std::move(cas);
      this->ascs = std::move(ascs);
      this->pacs = std::move(pacs);
    }

    ~MockDeviceWrapper() {
      Mock::VerifyAndClearExpectations(csis.get());
      Mock::VerifyAndClearExpectations(cas.get());
      Mock::VerifyAndClearExpectations(ascs.get());
      Mock::VerifyAndClearExpectations(pacs.get());
    }

    RawAddress addr;
    bool connected = false;

    // A list of services and their useful params
    std::list<gatt::Service> services;
    std::unique_ptr<csis_mock> csis;
    std::unique_ptr<cas_mock> cas;
    std::unique_ptr<ascs_mock> ascs;
    std::unique_ptr<pacs_mock> pacs;
  };

  void SyncOnMainLoop() {
    // Wait for the main loop to flush
    // WARNING: Not tested with Timers pushing periodic tasks to the main loop
    while (num_async_tasks > 0)
      ;
  }

  void ConnectLeAudio(const RawAddress& address, bool isEncrypted = true,
                      bool expect_connected_event = true) {
    // by default indicate link as encrypted
    ON_CALL(mock_btm_interface_, BTM_IsEncrypted(address, _))
        .WillByDefault(DoAll(Return(isEncrypted)));

    ON_CALL(mock_btm_interface_, IsLinkKeyKnown(address, _))
        .WillByDefault(DoAll(Return(true)));

    EXPECT_CALL(mock_gatt_interface_,
                Open(gatt_if, address, BTM_BLE_DIRECT_CONNECTION, _))
        .Times(1);

    /* If connected event is not expected to arrive, don't test those two below
     */
    if (expect_connected_event) {
      EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, address, false));
      EXPECT_CALL(
          mock_gatt_interface_,
          Open(gatt_if, address, BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
          .Times(1);
    }

    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&LeAudioClient::Connect,
                       base::Unretained(LeAudioClient::Get()), address));

    SyncOnMainLoop();
    Mock::VerifyAndClearExpectations(&mock_btm_interface_);
    Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
    Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  }

  void DisconnectLeAudioWithGattClose(
      const RawAddress& address, uint16_t conn_id,
      tGATT_DISCONN_REASON reason = GATT_CONN_TERMINATE_LOCAL_HOST) {
    EXPECT_CALL(mock_audio_hal_client_callbacks_,
                OnConnectionState(ConnectionState::DISCONNECTED, address))
        .Times(1);

    // For test purpose use the acl handle same as conn_id
    ON_CALL(mock_btm_interface_, GetHCIConnHandle(address, _))
        .WillByDefault([conn_id](RawAddress const& bd_addr,
                                 tBT_TRANSPORT transport) { return conn_id; });
    EXPECT_CALL(mock_btm_interface_, AclDisconnectFromHandle(conn_id, _))
        .Times(0);
    EXPECT_CALL(mock_gatt_interface_, Close(conn_id)).Times(1);

    do_in_main_thread(
        FROM_HERE, base::Bind(&LeAudioClient::Disconnect,
                              base::Unretained(LeAudioClient::Get()), address));
    SyncOnMainLoop();
    Mock::VerifyAndClearExpectations(&mock_btm_interface_);
    Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
    Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  }

  void DisconnectLeAudioWithAclClose(
      const RawAddress& address, uint16_t conn_id,
      tGATT_DISCONN_REASON reason = GATT_CONN_TERMINATE_LOCAL_HOST) {
    EXPECT_CALL(mock_audio_hal_client_callbacks_,
                OnConnectionState(ConnectionState::DISCONNECTED, address))
        .Times(1);

    // For test purpose use the acl handle same as conn_id
    ON_CALL(mock_btm_interface_, GetHCIConnHandle(address, _))
        .WillByDefault([conn_id](RawAddress const& bd_addr,
                                 tBT_TRANSPORT transport) { return conn_id; });
    EXPECT_CALL(mock_btm_interface_, AclDisconnectFromHandle(conn_id, _))
        .WillOnce([this, &reason](uint16_t handle, tHCI_STATUS rs) {
          InjectDisconnectedEvent(handle, reason);
        });
    EXPECT_CALL(mock_gatt_interface_, Close(conn_id)).Times(0);

    do_in_main_thread(
        FROM_HERE, base::Bind(&LeAudioClient::Disconnect,
                              base::Unretained(LeAudioClient::Get()), address));
    SyncOnMainLoop();
    Mock::VerifyAndClearExpectations(&mock_btm_interface_);
    Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
    Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  }

  void DisconnectLeAudioNoDisconnectedEvtExpected(const RawAddress& address,
                                                  uint16_t conn_id) {
    EXPECT_CALL(mock_gatt_interface_, Close(conn_id)).Times(0);
    EXPECT_CALL(mock_btm_interface_, AclDisconnectFromHandle(conn_id, _))
        .Times(1);
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&LeAudioClient::Disconnect,
                       base::Unretained(LeAudioClient::Get()), address));
    SyncOnMainLoop();
    Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
    Mock::VerifyAndClearExpectations(&mock_btm_interface_);
  }

  void ConnectCsisDevice(const RawAddress& addr, uint16_t conn_id,
                         uint32_t sink_audio_allocation,
                         uint32_t source_audio_allocation, uint8_t group_size,
                         int group_id, uint8_t rank,
                         bool connect_through_csis = false,
                         bool new_device = true) {
    SetSampleDatabaseEarbudsValid(conn_id, addr, sink_audio_allocation,
                                  source_audio_allocation, default_channel_cnt,
                                  default_channel_cnt,
                                  0x0004, /* source sample freq 16khz */
                                  true,   /*add_csis*/
                                  true,   /*add_cas*/
                                  true,   /*add_pacs*/
                                  true,   /*add_ascs*/
                                  group_size, rank);
    EXPECT_CALL(mock_audio_hal_client_callbacks_,
                OnConnectionState(ConnectionState::CONNECTED, addr))
        .Times(1);

    if (new_device) {
      EXPECT_CALL(mock_audio_hal_client_callbacks_,
                  OnGroupNodeStatus(addr, group_id, GroupNodeStatus::ADDED))
          .Times(1);
    }

    if (connect_through_csis) {
      // Add it the way CSIS would do: add to group and then connect
      do_in_main_thread(FROM_HERE,
                        base::BindOnce(&LeAudioClient::GroupAddNode,
                                       base::Unretained(LeAudioClient::Get()),
                                       group_id, addr));
      ConnectLeAudio(addr);
    } else {
      // The usual connect
      // Since device has CSIS, lets add it here to groups already now
      groups[addr] = group_id;
      ConnectLeAudio(addr);
      InjectGroupDeviceAdded(addr, group_id);
    }
  }

  void ConnectNonCsisDevice(const RawAddress& addr, uint16_t conn_id,
                            uint32_t sink_audio_allocation,
                            uint32_t source_audio_allocation) {
    SetSampleDatabaseEarbudsValid(
        conn_id, addr, sink_audio_allocation, source_audio_allocation,
        default_channel_cnt, default_channel_cnt, 0x0004,
        /* source sample freq 16khz */ false, /*add_csis*/
        true,                                 /*add_cas*/
        true,                                 /*add_pacs*/
        true,                                 /*add_ascs*/
        0, 0);
    EXPECT_CALL(mock_audio_hal_client_callbacks_,
                OnConnectionState(ConnectionState::CONNECTED, addr))
        .Times(1);

    ConnectLeAudio(addr);
  }

  void UpdateLocalSourceMetadata(
      std::vector<struct playback_track_metadata> tracks,
      bool reconfigure_existing_stream = false) {
    std::vector<playback_track_metadata_v7> tracks_vec;
    tracks_vec.reserve(tracks.size());
    for (const auto& track : tracks) {
      playback_track_metadata_v7 desc_track = {
          .base =
              {
                  .usage = static_cast<audio_usage_t>(track.usage),
                  .content_type =
                      static_cast<audio_content_type_t>(track.content_type),
                  .gain = track.gain,
              },
      };
      if (test_tags_ptr_) {
        memcpy(desc_track.tags, test_tags_ptr_, strlen(test_tags_ptr_));
      }

      tracks_vec.push_back(desc_track);
    }

    ASSERT_NE(nullptr, mock_le_audio_source_hal_client_);
    /* Local Source may reconfigure once the metadata is updated */
    if (reconfigure_existing_stream) {
      Expectation reconfigure = EXPECT_CALL(*mock_le_audio_source_hal_client_,
                                            SuspendedForReconfiguration())
                                    .Times(1);
      EXPECT_CALL(*mock_le_audio_source_hal_client_, CancelStreamingRequest())
          .Times(1);
      EXPECT_CALL(*mock_le_audio_source_hal_client_, ReconfigurationComplete())
          .Times(1)
          .After(reconfigure);
    } else {
      EXPECT_CALL(*mock_le_audio_source_hal_client_,
                  SuspendedForReconfiguration())
          .Times(0);
      EXPECT_CALL(*mock_le_audio_source_hal_client_, ReconfigurationComplete())
          .Times(0);
    }

    ASSERT_NE(unicast_source_hal_cb_, nullptr);
    unicast_source_hal_cb_->OnAudioMetadataUpdate(std::move(tracks_vec),
                                                  DsaMode::DISABLED);
  }

  void UpdateLocalSourceMetadata(audio_usage_t usage,
                                 audio_content_type_t content_type,
                                 bool reconfigure_existing_stream = false) {
    std::vector<struct playback_track_metadata> tracks = {
        {{AUDIO_USAGE_UNKNOWN, AUDIO_CONTENT_TYPE_UNKNOWN, 0},
         {AUDIO_USAGE_UNKNOWN, AUDIO_CONTENT_TYPE_UNKNOWN, 0}}};

    tracks[0].usage = usage;
    tracks[0].content_type = content_type;
    UpdateLocalSourceMetadata(tracks, reconfigure_existing_stream);
  }

  void UpdateLocalSinkMetadata(audio_source_t audio_source) {
    std::vector<struct record_track_metadata> tracks = {
        {{AUDIO_SOURCE_INVALID, 0.5, AUDIO_DEVICE_NONE, "00:11:22:33:44:55"},
         {AUDIO_SOURCE_MIC, 0.7, AUDIO_DEVICE_OUT_BLE_HEADSET,
          "AA:BB:CC:DD:EE:FF"}}};

    tracks[1].source = audio_source;

    std::vector<record_track_metadata_v7> tracks_vec;
    tracks_vec.reserve(tracks.size());
    for (const auto& track : tracks) {
      record_track_metadata_v7 desc_track = {
          .base =
              {
                  .source = static_cast<audio_source_t>(track.source),
                  .gain = track.gain,
                  .dest_device =
                      static_cast<audio_devices_t>(track.dest_device),
              },
      };

      strcpy(desc_track.base.dest_device_address, track.dest_device_address);
      tracks_vec.push_back(desc_track);
    }

    ASSERT_NE(nullptr, unicast_sink_hal_cb_);
    unicast_sink_hal_cb_->OnAudioMetadataUpdate(std::move(tracks_vec));
  }

  void LocalAudioSourceSuspend(void) {
    ASSERT_NE(unicast_source_hal_cb_, nullptr);
    unicast_source_hal_cb_->OnAudioSuspend();
    SyncOnMainLoop();
  }

  void LocalAudioSourceResume(bool expected_confirmation = true,
                              bool expected_cancel = false) {
    ASSERT_NE(nullptr, mock_le_audio_source_hal_client_);
    if (expected_confirmation) {
      EXPECT_CALL(*mock_le_audio_source_hal_client_, ConfirmStreamingRequest())
          .Times(1);
    }

    if (expected_cancel) {
      EXPECT_CALL(*mock_le_audio_source_hal_client_, CancelStreamingRequest())
          .Times(1);
    }

    do_in_main_thread(FROM_HERE,
                      base::BindOnce(
                          [](LeAudioSourceAudioHalClient::Callbacks* cb) {
                            cb->OnAudioResume();
                          },
                          unicast_source_hal_cb_));

    SyncOnMainLoop();
    Mock::VerifyAndClearExpectations(&*mock_le_audio_source_hal_client_);
  }

  void LocalAudioSinkSuspend(void) {
    ASSERT_NE(unicast_sink_hal_cb_, nullptr);
    unicast_sink_hal_cb_->OnAudioSuspend();
    SyncOnMainLoop();
  }

  void LocalAudioSinkResume(void) {
    ASSERT_NE(unicast_sink_hal_cb_, nullptr);
    do_in_main_thread(FROM_HERE,
                      base::BindOnce(
                          [](LeAudioSinkAudioHalClient::Callbacks* cb) {
                            cb->OnAudioResume();
                          },
                          unicast_sink_hal_cb_));

    SyncOnMainLoop();
    Mock::VerifyAndClearExpectations(&*mock_le_audio_sink_hal_client_);
  }

  void StartStreaming(audio_usage_t usage, audio_content_type_t content_type,
                      int group_id,
                      audio_source_t audio_source = AUDIO_SOURCE_INVALID,
                      bool reconfigure_existing_stream = false,
                      bool expected_resume_confirmation = true) {
    ASSERT_NE(unicast_source_hal_cb_, nullptr);

    UpdateLocalSourceMetadata(usage, content_type, reconfigure_existing_stream);
    if (audio_source != AUDIO_SOURCE_INVALID) {
      UpdateLocalSinkMetadata(audio_source);
    }

    /* Stream has been automatically restarted on UpdateLocalSourceMetadata */
    if (reconfigure_existing_stream) return;

    LocalAudioSourceResume(expected_resume_confirmation);
    SyncOnMainLoop();
    Mock::VerifyAndClearExpectations(&mock_state_machine_);

    if (usage == AUDIO_USAGE_VOICE_COMMUNICATION ||
        audio_source != AUDIO_SOURCE_INVALID) {
      ASSERT_NE(unicast_sink_hal_cb_, nullptr);
      do_in_main_thread(FROM_HERE,
                        base::BindOnce(
                            [](LeAudioSinkAudioHalClient::Callbacks* cb) {
                              cb->OnAudioResume();
                            },
                            unicast_sink_hal_cb_));
    }
    SyncOnMainLoop();
  }

  void StopStreaming(int group_id, bool suspend_source = false) {
    ASSERT_NE(unicast_source_hal_cb_, nullptr);

    /* TODO We should have a way to confirm Stop() otherwise, audio framework
     * might have different state that it is in the le_audio code - as tearing
     * down CISes might take some time
     */
    /* It's enough to call only one resume even if it'll be bi-directional
     * streaming. First suspend will trigger GroupStop.
     *
     * There is no - 'only source receiver' scenario (e.g. single microphone).
     * If there will be such test oriented scenario, such resume choose logic
     * should be applied.
     */
    unicast_source_hal_cb_->OnAudioSuspend();

    if (suspend_source) {
      ASSERT_NE(unicast_sink_hal_cb_, nullptr);
      unicast_sink_hal_cb_->OnAudioSuspend();
    }
    SyncOnMainLoop();
  }

  void set_sample_database(
      uint16_t conn_id, RawAddress addr,
      std::unique_ptr<NiceMock<MockDeviceWrapper::csis_mock>> csis,
      std::unique_ptr<NiceMock<MockDeviceWrapper::cas_mock>> cas,
      std::unique_ptr<NiceMock<MockDeviceWrapper::ascs_mock>> ascs,
      std::unique_ptr<NiceMock<MockDeviceWrapper::pacs_mock>> pacs) {
    gatt::DatabaseBuilder bob;

    /* Generic Access Service */
    bob.AddService(0x0001, 0x0003, Uuid::From16Bit(0x1800), true);
    /* Device Name Char. */
    bob.AddCharacteristic(0x0002, 0x0003, Uuid::From16Bit(0x2a00),
                          GATT_CHAR_PROP_BIT_READ);

    if (csis->start) {
      bool is_primary = true;
      bob.AddService(csis->start, csis->end, bluetooth::csis::kCsisServiceUuid,
                     is_primary);
      if (csis->sirk_char) {
        bob.AddCharacteristic(
            csis->sirk_char, csis->sirk_char + 1,
            bluetooth::csis::kCsisSirkUuid,
            GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
        if (csis->sirk_ccc)
          bob.AddDescriptor(csis->sirk_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (csis->size_char) {
        bob.AddCharacteristic(
            csis->size_char, csis->size_char + 1,
            bluetooth::csis::kCsisSizeUuid,
            GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_NOTIFY);
        if (csis->size_ccc)
          bob.AddDescriptor(csis->size_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (csis->lock_char) {
        bob.AddCharacteristic(csis->lock_char, csis->lock_char + 1,
                              bluetooth::csis::kCsisLockUuid,
                              GATT_CHAR_PROP_BIT_READ |
                                  GATT_CHAR_PROP_BIT_NOTIFY |
                                  GATT_CHAR_PROP_BIT_WRITE);
        if (csis->lock_ccc)
          bob.AddDescriptor(csis->lock_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (csis->rank_char)
        bob.AddCharacteristic(csis->rank_char, csis->rank_char + 1,
                              bluetooth::csis::kCsisRankUuid,
                              GATT_CHAR_PROP_BIT_READ);
    }

    if (cas->start) {
      bool is_primary = true;
      bob.AddService(cas->start, cas->end, le_audio::uuid::kCapServiceUuid,
                     is_primary);
      // Include CSIS service inside
      if (cas->csis_include)
        bob.AddIncludedService(cas->csis_include,
                               bluetooth::csis::kCsisServiceUuid, csis->start,
                               csis->end);
    }

    if (pacs->start) {
      bool is_primary = true;
      bob.AddService(pacs->start, pacs->end,
                     le_audio::uuid::kPublishedAudioCapabilityServiceUuid,
                     is_primary);

      if (pacs->sink_pac_char) {
        bob.AddCharacteristic(
            pacs->sink_pac_char, pacs->sink_pac_char + 1,
            le_audio::uuid::kSinkPublishedAudioCapabilityCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->sink_pac_ccc)
          bob.AddDescriptor(pacs->sink_pac_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (pacs->sink_audio_loc_char) {
        bob.AddCharacteristic(
            pacs->sink_audio_loc_char, pacs->sink_audio_loc_char + 1,
            le_audio::uuid::kSinkAudioLocationCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->sink_audio_loc_ccc)
          bob.AddDescriptor(pacs->sink_audio_loc_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (pacs->source_pac_char) {
        bob.AddCharacteristic(
            pacs->source_pac_char, pacs->source_pac_char + 1,
            le_audio::uuid::kSourcePublishedAudioCapabilityCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->source_pac_ccc)
          bob.AddDescriptor(pacs->source_pac_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (pacs->source_audio_loc_char) {
        bob.AddCharacteristic(
            pacs->source_audio_loc_char, pacs->source_audio_loc_char + 1,
            le_audio::uuid::kSourceAudioLocationCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->source_audio_loc_ccc)
          bob.AddDescriptor(pacs->source_audio_loc_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (pacs->avail_contexts_char) {
        bob.AddCharacteristic(
            pacs->avail_contexts_char, pacs->avail_contexts_char + 1,
            le_audio::uuid::kAudioContextAvailabilityCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->avail_contexts_ccc)
          bob.AddDescriptor(pacs->avail_contexts_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }

      if (pacs->supp_contexts_char) {
        bob.AddCharacteristic(
            pacs->supp_contexts_char, pacs->supp_contexts_char + 1,
            le_audio::uuid::kAudioSupportedContextCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (pacs->supp_contexts_ccc)
          bob.AddDescriptor(pacs->supp_contexts_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }
    }

    if (ascs->start) {
      bool is_primary = true;
      bob.AddService(ascs->start, ascs->end,
                     le_audio::uuid::kAudioStreamControlServiceUuid,
                     is_primary);
      for (int i = 0; i < max_num_of_ases; i++) {
        if (ascs->sink_ase_char[i]) {
          bob.AddCharacteristic(ascs->sink_ase_char[i],
                                ascs->sink_ase_char[i] + 1,
                                le_audio::uuid::kSinkAudioStreamEndpointUuid,
                                GATT_CHAR_PROP_BIT_READ);
          if (ascs->sink_ase_ccc[i])
            bob.AddDescriptor(ascs->sink_ase_ccc[i],
                              Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
        }
        if (ascs->source_ase_char[i]) {
          bob.AddCharacteristic(ascs->source_ase_char[i],
                                ascs->source_ase_char[i] + 1,
                                le_audio::uuid::kSourceAudioStreamEndpointUuid,
                                GATT_CHAR_PROP_BIT_READ);
          if (ascs->source_ase_ccc[i])
            bob.AddDescriptor(ascs->source_ase_ccc[i],
                              Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
        }
      }
      if (ascs->ctp_char) {
        bob.AddCharacteristic(
            ascs->ctp_char, ascs->ctp_char + 1,
            le_audio::uuid::kAudioStreamEndpointControlPointCharacteristicUuid,
            GATT_CHAR_PROP_BIT_READ);
        if (ascs->ctp_ccc)
          bob.AddDescriptor(ascs->ctp_ccc,
                            Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG));
      }
    }

    // Assign conn_id to a certain device - this does not mean it is connected
    auto dev_wrapper = std::make_unique<NiceMock<MockDeviceWrapper>>(
        addr, bob.Build().Services(), std::move(csis), std::move(cas),
        std::move(ascs), std::move(pacs));
    peer_devices.emplace(conn_id, std::move(dev_wrapper));
  }

  void SetSampleDatabaseEmpty(uint16_t conn_id, RawAddress addr) {
    auto csis = std::make_unique<NiceMock<MockDeviceWrapper::csis_mock>>();
    auto cas = std::make_unique<NiceMock<MockDeviceWrapper::cas_mock>>();
    auto pacs = std::make_unique<NiceMock<MockDeviceWrapper::pacs_mock>>();
    auto ascs = std::make_unique<NiceMock<MockDeviceWrapper::ascs_mock>>();
    set_sample_database(conn_id, addr, std::move(csis), std::move(cas),
                        std::move(ascs), std::move(pacs));
  }

  void SetSampleDatabaseEarbudsValid(
      uint16_t conn_id, RawAddress addr, uint32_t sink_audio_allocation,
      uint32_t source_audio_allocation, uint8_t sink_channel_cnt = 0x03,
      uint8_t source_channel_cnt = 0x03, uint16_t sample_freq_mask = 0x0004,
      bool add_csis = true, bool add_cas = true, bool add_pacs = true,
      int add_ascs_cnt = 1, uint8_t set_size = 2, uint8_t rank = 1,
      GattStatus gatt_status = GATT_SUCCESS) {
    auto csis = std::make_unique<NiceMock<MockDeviceWrapper::csis_mock>>();
    if (add_csis) {
      // attribute handles
      csis->start = 0x0010;
      csis->sirk_char = 0x0020;
      csis->sirk_ccc = 0x0022;
      csis->size_char = 0x0023;
      csis->size_ccc = 0x0025;
      csis->lock_char = 0x0026;
      csis->lock_ccc = 0x0028;
      csis->rank_char = 0x0029;
      csis->end = 0x0030;
      // other params
      csis->size = set_size;
      csis->rank = rank;
    }

    auto cas = std::make_unique<NiceMock<MockDeviceWrapper::cas_mock>>();
    if (add_cas) {
      // attribute handles
      cas->start = 0x0040;
      if (add_csis) cas->csis_include = 0x0041;
      cas->end = 0x0050;
      // other params
    }

    auto pacs = std::make_unique<NiceMock<MockDeviceWrapper::pacs_mock>>();
    if (add_pacs) {
      // attribute handles
      pacs->start = 0x0060;
      pacs->sink_pac_char = 0x0061;
      pacs->sink_pac_ccc = 0x0063;
      pacs->sink_audio_loc_char = 0x0064;
      pacs->sink_audio_loc_ccc = 0x0066;
      pacs->source_pac_char = 0x0067;
      pacs->source_pac_ccc = 0x0069;
      pacs->source_audio_loc_char = 0x0070;
      pacs->source_audio_loc_ccc = 0x0072;
      pacs->avail_contexts_char = 0x0073;
      pacs->avail_contexts_ccc = 0x0075;
      pacs->supp_contexts_char = 0x0076;
      pacs->supp_contexts_ccc = 0x0078;
      pacs->end = 0x0080;
      // other params
    }

    auto ascs = std::make_unique<NiceMock<MockDeviceWrapper::ascs_mock>>();
    if (add_ascs_cnt > 0) {
      // attribute handles
      ascs->start = 0x0090;
      uint16_t handle = 0x0091;
      for (int i = 0; i < add_ascs_cnt; i++) {
        if (sink_audio_allocation != 0) {
          ascs->sink_ase_char[i] = handle;
          handle += 2;
          ascs->sink_ase_ccc[i] = handle;
          handle++;
        }

        if (source_audio_allocation != 0) {
          ascs->source_ase_char[i] = handle;
          handle += 2;
          ascs->source_ase_ccc[i] = handle;
          handle++;
        }
      }
      ascs->ctp_char = handle;
      handle += 2;
      ascs->ctp_ccc = handle;
      handle++;
      ascs->end = handle;
      // other params
    }

    set_sample_database(conn_id, addr, std::move(csis), std::move(cas),
                        std::move(ascs), std::move(pacs));

    if (add_pacs) {
      uint8_t snk_allocation[4];
      uint8_t src_allocation[4];

      snk_allocation[0] = (uint8_t)(sink_audio_allocation);
      snk_allocation[1] = (uint8_t)(sink_audio_allocation >> 8);
      snk_allocation[2] = (uint8_t)(sink_audio_allocation >> 16);
      snk_allocation[3] = (uint8_t)(sink_audio_allocation >> 24);

      src_allocation[0] = (uint8_t)(source_audio_allocation);
      src_allocation[1] = (uint8_t)(source_audio_allocation >> 8);
      src_allocation[2] = (uint8_t)(source_audio_allocation >> 16);
      src_allocation[3] = (uint8_t)(source_audio_allocation >> 24);

      uint8_t sample_freq[2];
      sample_freq[0] = (uint8_t)(sample_freq_mask);
      sample_freq[1] = (uint8_t)(sample_freq_mask >> 8);

      // Set pacs default read values
      ON_CALL(*peer_devices.at(conn_id)->pacs, OnReadCharacteristic(_, _, _))
          .WillByDefault([this, conn_id, snk_allocation, src_allocation,
                          sample_freq, sink_channel_cnt, source_channel_cnt,
                          gatt_status](uint16_t handle, GATT_READ_OP_CB cb,
                                       void* cb_data) {
            auto& pacs = peer_devices.at(conn_id)->pacs;
            std::vector<uint8_t> value;
            if (gatt_status == GATT_SUCCESS) {
              if (handle == pacs->sink_pac_char + 1) {
                value = {
                    // Num records
                    0x02,
                    // Codec_ID
                    0x06,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    // Codec Spec. Caps. Len
                    0x10,
                    0x03, /* sample freq */
                    0x01,
                    sample_freq[0],
                    sample_freq[1],
                    0x02,
                    0x02, /* frame duration */
                    0x03,
                    0x02, /* channel count */
                    0x03,
                    sink_channel_cnt,
                    0x05,
                    0x04,
                    0x1E,
                    0x00,
                    0x78,
                    0x00,
                    // Metadata Length
                    0x00,
                    // Codec_ID
                    0x06,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    // Codec Spec. Caps. Len
                    0x10,
                    0x03, /* sample freq */
                    0x01,
                    0x80, /* 48kHz */
                    0x00,
                    0x02, /* frame duration */
                    0x02,
                    0x03,
                    0x02, /* channel count */
                    0x03,
                    sink_channel_cnt,
                    0x05, /* octects per frame */
                    0x04,
                    0x78,
                    0x00,
                    0x78,
                    0x00,
                    // Metadata Length
                    0x00,
                };
              } else if (handle == pacs->sink_audio_loc_char + 1) {
                value = {
                    // Audio Locations
                    snk_allocation[0],
                    snk_allocation[1],
                    snk_allocation[2],
                    snk_allocation[3],
                };
              } else if (handle == pacs->source_pac_char + 1) {
                value = {
                    // Num records
                    0x02,
                    // Codec_ID
                    0x06,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    // Codec Spec. Caps. Len
                    0x10,
                    0x03,
                    0x01,
                    sample_freq[0],
                    sample_freq[1],
                    0x02,
                    0x02,
                    0x03,
                    0x02,
                    0x03,
                    source_channel_cnt,
                    0x05,
                    0x04,
                    0x1E,
                    0x00,
                    0x78,
                    0x00,
                    // Metadata Length
                    0x00,
                    // Codec_ID
                    0x06,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    // Codec Spec. Caps. Len
                    0x10,
                    0x03,
                    0x01,
                    0x24,
                    0x00,
                    0x02,
                    0x02,
                    0x03,
                    0x02,
                    0x03,
                    source_channel_cnt,
                    0x05,
                    0x04,
                    0x1E,
                    0x00,
                    0x50,
                    0x00,
                    // Metadata Length
                    0x00,
                };
              } else if (handle == pacs->source_audio_loc_char + 1) {
                value = {
                    // Audio Locations
                    src_allocation[0],
                    src_allocation[1],
                    src_allocation[2],
                    src_allocation[3],
                };
              } else if (handle == pacs->avail_contexts_char + 1) {
                value = {
                    // Sink Avail Contexts
                    (uint8_t)(available_snk_context_types_),
                    (uint8_t)(available_snk_context_types_ >> 8),
                    // Source Avail Contexts
                    (uint8_t)(available_src_context_types_),
                    (uint8_t)(available_src_context_types_ >> 8),
                };
              } else if (handle == pacs->supp_contexts_char + 1) {
                value = {
                    // Sink Supp Contexts
                    (uint8_t)(supported_snk_context_types_),
                    (uint8_t)(supported_snk_context_types_ >> 8),
                    // Source Supp Contexts
                    (uint8_t)(supported_src_context_types_),
                    (uint8_t)(supported_src_context_types_ >> 8),
                };
              }
            }
            cb(conn_id, gatt_status, handle, value.size(), value.data(),
               cb_data);
          });
    }

    if (add_ascs_cnt > 0) {
      // Set ascs default read values
      ON_CALL(*peer_devices.at(conn_id)->ascs, OnReadCharacteristic(_, _, _))
          .WillByDefault([this, conn_id, gatt_status](uint16_t handle,
                                                      GATT_READ_OP_CB cb,
                                                      void* cb_data) {
            auto& ascs = peer_devices.at(conn_id)->ascs;
            std::vector<uint8_t> value;
            bool is_ase_sink_request = false;
            bool is_ase_src_request = false;
            uint8_t idx;

            if (handle == ascs->ctp_ccc && ccc_stored_byte_val_.has_value()) {
              value = {*ccc_stored_byte_val_, 00};
              cb(conn_id, gatt_read_ctp_ccc_status_, handle, value.size(),
                 value.data(), cb_data);
              return;
            }

            if (gatt_status == GATT_SUCCESS) {
              if (handle == ascs->ctp_ccc) {
                value = UINT16_TO_VEC_UINT8(ascs->ctp_ccc_val);
              } else {
                for (idx = 0; idx < max_num_of_ases; idx++) {
                  if (handle == ascs->sink_ase_ccc[idx] + 1) {
                    value = UINT16_TO_VEC_UINT8(ascs->sink_ase_ccc_val[idx]);
                    break;
                  }
                  if (handle == ascs->source_ase_char[idx] + 1) {
                    value = UINT16_TO_VEC_UINT8(ascs->source_ase_ccc_val[idx]);
                    break;
                  }
                }
              }

              for (idx = 0; idx < max_num_of_ases; idx++) {
                if (handle == ascs->sink_ase_char[idx] + 1) {
                  is_ase_sink_request = true;
                  break;
                }
                if (handle == ascs->source_ase_char[idx] + 1) {
                  is_ase_src_request = true;
                  break;
                }
              }

              if (is_ase_sink_request) {
                value = {
                    // ASE ID
                    static_cast<uint8_t>(idx + 1),
                    // State
                    static_cast<uint8_t>(
                        le_audio::types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE),
                    // No Additional ASE params for IDLE state
                };
              } else if (is_ase_src_request) {
                value = {
                    // ASE ID
                    static_cast<uint8_t>(idx + 6),
                    // State
                    static_cast<uint8_t>(
                        le_audio::types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE),
                    // No Additional ASE params for IDLE state
                };
              }
            }
            cb(conn_id, gatt_status, handle, value.size(), value.data(),
               cb_data);
          });
    }
  }

  void TestAudioDataTransfer(int group_id, uint8_t cis_count_out,
                             uint8_t cis_count_in, int data_len,
                             int in_data_len = 40,
                             uint16_t decoded_in_data_len = 0) {
    ASSERT_NE(unicast_source_hal_cb_, nullptr);
    ASSERT_NE(mock_le_audio_sink_hal_client_, nullptr);

    // Expect two channels ISO Data to be sent
    std::vector<uint16_t> handles;
    EXPECT_CALL(*mock_iso_manager_, SendIsoData(_, _, _))
        .Times(cis_count_out)
        .WillRepeatedly(
            [&handles](uint16_t iso_handle, const uint8_t* data,
                       uint16_t data_len) { handles.push_back(iso_handle); });
    std::vector<uint8_t> data(data_len);
    unicast_source_hal_cb_->OnAudioDataReady(data);

    // Inject microphone data from group
    if (decoded_in_data_len) {
      EXPECT_CALL(*mock_le_audio_sink_hal_client_,
                  SendData(_, decoded_in_data_len))
          .Times(cis_count_in > 0 ? 1 : 0);
    } else {
      EXPECT_CALL(*mock_le_audio_sink_hal_client_, SendData(_, _))
          .Times(cis_count_in > 0 ? 1 : 0);
    }
    ASSERT_EQ(streaming_groups.count(group_id), 1u);

    if (cis_count_in) {
      ASSERT_NE(unicast_sink_hal_cb_, nullptr);

      auto group = streaming_groups.at(group_id);
      for (LeAudioDevice* device = group->GetFirstDevice(); device != nullptr;
           device = group->GetNextDevice(device)) {
        for (auto& ase : device->ases_) {
          if (ase.direction == le_audio::types::kLeAudioDirectionSource) {
            InjectIncomingIsoData(group_id, ase.cis_conn_hdl, in_data_len);
            --cis_count_in;
            if (!cis_count_in) break;
          }
        }
        if (!cis_count_in) break;
      }
    }

    SyncOnMainLoop();
    std::sort(handles.begin(), handles.end());
    ASSERT_EQ(cis_count_in, 0);
    handles.clear();

    Mock::VerifyAndClearExpectations(mock_iso_manager_);
  }

  void InjectIncomingIsoData(uint16_t cig_id, uint16_t cis_con_hdl,
                             size_t payload_size) {
    BT_HDR* bt_hdr = (BT_HDR*)malloc(sizeof(BT_HDR) + payload_size);

    bt_hdr->offset = 0;
    bt_hdr->len = payload_size;

    bluetooth::hci::iso_manager::cis_data_evt cis_evt;
    cis_evt.cig_id = cig_id;
    cis_evt.cis_conn_hdl = cis_con_hdl;
    cis_evt.ts = 0;
    cis_evt.evt_lost = 0;
    cis_evt.p_msg = bt_hdr;

    ASSERT_NE(cig_callbacks_, nullptr);
    cig_callbacks_->OnCisEvent(
        bluetooth::hci::iso_manager::kIsoEventCisDataAvailable, &cis_evt);
    free(bt_hdr);
  }

  void InjectCisDisconnected(uint16_t cig_id, uint16_t cis_con_hdl,
                             uint8_t reason = 0) {
    bluetooth::hci::iso_manager::cis_disconnected_evt cis_evt;
    cis_evt.cig_id = cig_id;
    cis_evt.cis_conn_hdl = cis_con_hdl;
    cis_evt.reason = reason;

    ASSERT_NE(cig_callbacks_, nullptr);
    cig_callbacks_->OnCisEvent(
        bluetooth::hci::iso_manager::kIsoEventCisDisconnected, &cis_evt);
  }

  void InjectCigRemoved(uint8_t cig_id) {
    bluetooth::hci::iso_manager::cig_remove_cmpl_evt evt;
    evt.status = 0;
    evt.cig_id = cig_id;

    ASSERT_NE(cig_callbacks_, nullptr);
    cig_callbacks_->OnCisEvent(
        bluetooth::hci::iso_manager::kIsoEventCigOnRemoveCmpl, &evt);
  }

  NiceMock<MockAudioHalClientCallbacks> mock_audio_hal_client_callbacks_;
  LeAudioSourceAudioHalClient::Callbacks* unicast_source_hal_cb_ = nullptr;
  LeAudioSinkAudioHalClient::Callbacks* unicast_sink_hal_cb_ = nullptr;

  uint8_t default_channel_cnt = 0x03;
  uint8_t default_ase_cnt = 1;

  NiceMock<MockCsisClient> mock_csis_client_module_;
  NiceMock<MockDeviceGroups> mock_groups_module_;
  bluetooth::groups::DeviceGroupsCallbacks* group_callbacks_;
  NiceMock<MockLeAudioGroupStateMachine> mock_state_machine_;

  NiceMock<MockFunction<void()>> mock_storage_load;
  NiceMock<MockFunction<bool()>> mock_hal_2_1_verifier;

  NiceMock<controller::MockControllerInterface> controller_interface_;
  NiceMock<bluetooth::manager::MockBtmInterface> mock_btm_interface_;
  NiceMock<gatt::MockBtaGattInterface> mock_gatt_interface_;
  NiceMock<gatt::MockBtaGattQueue> mock_gatt_queue_;
  tBTA_GATTC_CBACK* gatt_callback;
  const uint8_t gatt_if = 0xfe;
  uint16_t global_conn_id = 1;
  le_audio::LeAudioGroupStateMachine::Callbacks* state_machine_callbacks_;
  std::map<int, LeAudioDeviceGroup*> streaming_groups;
  bool block_streaming_state_callback = false;

  bluetooth::hci::IsoManager* iso_manager_;
  MockIsoManager* mock_iso_manager_;
  bluetooth::hci::iso_manager::CigCallbacks* cig_callbacks_ = nullptr;
  uint16_t iso_con_counter_ = 1;

  le_audio::CodecManager* codec_manager_;
  MockCodecManager* mock_codec_manager_;

  uint16_t available_snk_context_types_ = 0xffff;
  uint16_t available_src_context_types_ = 0xffff;
  uint16_t supported_snk_context_types_ = 0xffff;
  uint16_t supported_src_context_types_ = 0xffff;

  NiceMock<bluetooth::storage::MockBtifStorageInterface> mock_btif_storage_;

  std::map<uint16_t, std::unique_ptr<NiceMock<MockDeviceWrapper>>> peer_devices;
  std::list<int> group_locks;
  std::map<RawAddress, int> groups;

  /* CCC descriptor data */
  tGATT_STATUS gatt_read_ctp_ccc_status_ = GATT_SUCCESS;
  std::optional<uint8_t> ccc_stored_byte_val_ = std::nullopt;

  /* Audio track metadata */
  char* test_tags_ptr_ = nullptr;
};

class UnicastTest : public UnicastTestNoInit {
 protected:
  void SetUp() override {
    UnicastTestNoInit::SetUp();

    EXPECT_CALL(mock_hal_2_1_verifier, Call()).Times(1);
    EXPECT_CALL(mock_storage_load, Call()).Times(1);

    ON_CALL(mock_btm_interface_, GetHCIConnHandle(_, _))
        .WillByDefault([this](RawAddress const& bd_addr,
                              tBT_TRANSPORT transport) -> uint16_t {
          for (auto const& [conn_id, dev_wrapper] : peer_devices) {
            if (dev_wrapper->addr == bd_addr) {
              return conn_id;
            }
          }
          LOG_ERROR("GetHCIConnHandle Mock: not a valid test device!");
          return 0x00FE;
        });
    ON_CALL(mock_btm_interface_, AclDisconnectFromHandle(_, _))
        .WillByDefault([this](uint16_t handle, tHCI_STATUS rs) {
          ASSERT_NE(handle, GATT_INVALID_CONN_ID);
          InjectDisconnectedEvent(handle, GATT_CONN_TERMINATE_LOCAL_HOST);
        });

    std::vector<::bluetooth::le_audio::btle_audio_codec_config_t>
        framework_encode_preference;
    BtaAppRegisterCallback app_register_callback;
    EXPECT_CALL(mock_gatt_interface_, AppRegister(_, _, _))
        .WillOnce(DoAll(SaveArg<0>(&gatt_callback),
                        SaveArg<1>(&app_register_callback)));
    LeAudioClient::Initialize(
        &mock_audio_hal_client_callbacks_,
        base::Bind([](MockFunction<void()>* foo) { foo->Call(); },
                   &mock_storage_load),
        base::Bind([](MockFunction<bool()>* foo) { return foo->Call(); },
                   &mock_hal_2_1_verifier),
        framework_encode_preference);

    SyncOnMainLoop();
    ASSERT_TRUE(gatt_callback);
    ASSERT_TRUE(group_callbacks_);
    ASSERT_TRUE(app_register_callback);
    app_register_callback.Run(gatt_if, GATT_SUCCESS);
    Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
  }

  void TearDown() override {
    // Clear the default actions before the parent class teardown is called
    Mock::VerifyAndClear(&mock_btm_interface_);
    Mock::VerifyAndClear(&mock_gatt_interface_);
    Mock::VerifyAndClear(&mock_audio_hal_client_callbacks_);
    groups.clear();
    UnicastTestNoInit::TearDown();
  }
};

class UnicastTestHealthStatus : public UnicastTest {
 protected:
  void SetUp() override {
    use_health_status = true;
    UnicastTest::SetUp();
    group_ = new LeAudioDeviceGroup(group_id_);
  }

  void TearDown() override {
    delete group_;
    UnicastTest::TearDown();
  }

  const int group_id_ = 0;
  LeAudioDeviceGroup* group_ = nullptr;
};

class UnicastTestHandoverMode : public UnicastTest {
 protected:
  void SetUp() override {
    use_handover_mode = true;
    UnicastTest::SetUp();
    group_ = new LeAudioDeviceGroup(group_id_);
  }

  void TearDown() override {
    delete group_;
    UnicastTest::TearDown();
  }

  const int group_id_ = 0;
  LeAudioDeviceGroup* group_ = nullptr;
};

RawAddress GetTestAddress(uint8_t index) {
  CHECK_LT(index, UINT8_MAX);
  RawAddress result = {{0xC0, 0xDE, 0xC0, 0xDE, 0x00, index}};
  return result;
}

TEST_F(UnicastTest, Initialize) {
  ASSERT_NE(LeAudioClient::Get(), nullptr);
  ASSERT_TRUE(LeAudioClient::IsLeAudioClientRunning());
}

TEST_F(UnicastTestNoInit, InitializeNoHal_2_1) {
  ASSERT_FALSE(LeAudioClient::IsLeAudioClientRunning());

  // Report False when asked for Audio HAL 2.1 support
  ON_CALL(mock_hal_2_1_verifier, Call()).WillByDefault([]() -> bool {
    return false;
  });

  BtaAppRegisterCallback app_register_callback;
  ON_CALL(mock_gatt_interface_, AppRegister(_, _, _))
      .WillByDefault(DoAll(SaveArg<0>(&gatt_callback),
                           SaveArg<1>(&app_register_callback)));
  std::vector<::bluetooth::le_audio::btle_audio_codec_config_t>
      framework_encode_preference;

  EXPECT_DEATH(
      LeAudioClient::Initialize(
          &mock_audio_hal_client_callbacks_,
          base::Bind([](MockFunction<void()>* foo) { foo->Call(); },
                     &mock_storage_load),
          base::Bind([](MockFunction<bool()>* foo) { return foo->Call(); },
                     &mock_hal_2_1_verifier),
          framework_encode_preference),
      ", LE Audio Client requires Bluetooth Audio HAL V2.1 at least. Either "
      "disable LE Audio Profile, or update your HAL");
}

TEST_F(UnicastTest, ConnectOneEarbudEmpty) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEmpty(1, test_address0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(1);
  ConnectLeAudio(test_address0);
}

TEST_F(UnicastTest, ConnectOneEarbudNoPacs) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      false,                               /*add_pacs*/
      default_ase_cnt /*add_ascs*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(1);
  ConnectLeAudio(test_address0);
}

TEST_F(UnicastTest, ConnectOneEarbudNoAscs) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      0 /*add_ascs*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(1);
  ConnectLeAudio(test_address0);
}

TEST_F(UnicastTest, ConnectOneEarbudNoCas) {
  const RawAddress test_address0 = GetTestAddress(0);
  uint16_t conn_id = 1;
  SetSampleDatabaseEarbudsValid(
      conn_id, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      false,                               /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt /*add_ascs*/);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);
}

TEST_F(UnicastTest, ConnectOneEarbudNoCsis) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false, /*add_csis*/
      true,                                 /*add_cas*/
      true,                                 /*add_pacs*/
      default_ase_cnt /*add_ascs*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);
}

TEST_F(UnicastTest, ConnectOneEarbudWithInvalidCsis) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt /*add_ascs*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(1);

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  /* Make sure Group has not knowledge about the device */
  ON_CALL(mock_groups_module_, GetGroupId(_, _))
      .WillByDefault([](const RawAddress& addr, bluetooth::Uuid uuid) {
        return bluetooth::groups::kGroupUnknown;
      });

  ConnectLeAudio(test_address0);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F_WITH_FLAGS(UnicastTestHealthStatus,
                  ConnectOneEarbudEmpty_withHealthStatus,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_enable_health_based_actions))) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEmpty(1, test_address0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnHealthBasedRecommendationAction(
                  test_address0, LeAudioHealthBasedAction::DISABLE))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(1);
  ConnectLeAudio(test_address0);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  LeAudioHealthStatus::Get()->RemoveStatistics(
      test_address0, bluetooth::groups::kGroupUnknown);
}

TEST_F_WITH_FLAGS(UnicastTestHealthStatus,
                  ConnectOneEarbudNoPacs_withHealthStatus,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_enable_health_based_actions))) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      false,                               /*add_pacs*/
      default_ase_cnt /*add_ascs*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnHealthBasedRecommendationAction(
                  test_address0, LeAudioHealthBasedAction::DISABLE))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(1);
  ConnectLeAudio(test_address0);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  LeAudioHealthStatus::Get()->RemoveStatistics(
      test_address0, bluetooth::groups::kGroupUnknown);
}

TEST_F_WITH_FLAGS(UnicastTestHealthStatus,
                  ConnectOneEarbudNoAscs_withHealthStatus,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_enable_health_based_actions))) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      0 /*add_ascs*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnHealthBasedRecommendationAction(
                  test_address0, LeAudioHealthBasedAction::DISABLE))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(1);
  ConnectLeAudio(test_address0);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  LeAudioHealthStatus::Get()->RemoveStatistics(
      test_address0, bluetooth::groups::kGroupUnknown);
}

TEST_F(UnicastTestHealthStatus, ConnectOneEarbudNoCas_withHealthStatus) {
  const RawAddress test_address0 = GetTestAddress(0);
  uint16_t conn_id = 1;
  SetSampleDatabaseEarbudsValid(
      conn_id, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      false,                               /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt /*add_ascs*/);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnHealthBasedRecommendationAction(
                  test_address0, LeAudioHealthBasedAction::DISABLE))
      .Times(0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  LeAudioHealthStatus::Get()->RemoveStatistics(
      test_address0, bluetooth::groups::kGroupUnknown);
}

TEST_F(UnicastTestHealthStatus, ConnectOneEarbudNoCsis_withHealthStatus) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false, /*add_csis*/
      true,                                 /*add_cas*/
      true,                                 /*add_pacs*/
      default_ase_cnt /*add_ascs*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnHealthBasedRecommendationAction(
                  test_address0, LeAudioHealthBasedAction::DISABLE))
      .Times(0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  LeAudioHealthStatus::Get()->RemoveStatistics(
      test_address0, bluetooth::groups::kGroupUnknown);
}

TEST_F_WITH_FLAGS(UnicastTestHealthStatus,
                  ConnectOneEarbudWithInvalidCsis_withHealthStatus,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_enable_health_based_actions))) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt /*add_ascs*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnHealthBasedRecommendationAction(
                  test_address0, LeAudioHealthBasedAction::DISABLE))
      .Times(1);

  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(1);

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  /* Make sure Group has not knowledge about the device */
  ON_CALL(mock_groups_module_, GetGroupId(_, _))
      .WillByDefault([](const RawAddress& addr, bluetooth::Uuid uuid) {
        return bluetooth::groups::kGroupUnknown;
      });

  ConnectLeAudio(test_address0);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  LeAudioHealthStatus::Get()->RemoveStatistics(
      test_address0, bluetooth::groups::kGroupUnknown);
}

TEST_F_WITH_FLAGS(UnicastTestHealthStatus,
                  ConnectOneEarbudDisable_withHealthStatus,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_enable_health_based_actions))) {
  const RawAddress test_address0 = GetTestAddress(0);
  int conn_id = 1;

  SetSampleDatabaseEarbudsValid(
      conn_id, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);

  ConnectLeAudio(test_address0);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  LeAudioClient::Get()->GroupSetActive(group_id_);
  auto device = std::make_shared<LeAudioDevice>(
      test_address0, DeviceConnectState::DISCONNECTED);
  group_->AddNode(device);
  SyncOnMainLoop();

  auto health_status = LeAudioHealthStatus::Get();

  /* Inject stream error */
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnHealthBasedGroupRecommendationAction(
                  group_id_, LeAudioHealthBasedAction::DISABLE))
      .Times(1);
  health_status->AddStatisticForGroup(
      group_, LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED);
  health_status->AddStatisticForGroup(
      group_, LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED);

  /* Do not act on disconnect */
  ON_CALL(mock_gatt_interface_, Close(_)).WillByDefault(DoAll(Return()));
  ON_CALL(mock_btm_interface_, AclDisconnectFromHandle(_, _))
      .WillByDefault(DoAll(Return()));

  state_machine_callbacks_->OnStateTransitionTimeout(group_id_);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnHealthBasedGroupRecommendationAction(
                  group_id_, LeAudioHealthBasedAction::DISABLE))
      .Times(0);
  health_status->AddStatisticForGroup(
      group_, LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED);
  health_status->AddStatisticForGroup(
      group_, LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F_WITH_FLAGS(UnicastTestHealthStatus,
                  ConnectOneEarbudConsiderDisabling_withHealthStatus,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_enable_health_based_actions))) {
  const RawAddress test_address0 = GetTestAddress(0);
  int conn_id = 1;

  SetSampleDatabaseEarbudsValid(
      conn_id, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);

  ConnectLeAudio(test_address0);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  LeAudioClient::Get()->GroupSetActive(group_id_);
  auto device = std::make_shared<LeAudioDevice>(
      test_address0, DeviceConnectState::DISCONNECTED);
  group_->AddNode(device);
  SyncOnMainLoop();

  auto health_status = LeAudioHealthStatus::Get();

  /* Inject stream success and error */
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnHealthBasedGroupRecommendationAction(
                  group_id_, LeAudioHealthBasedAction::CONSIDER_DISABLING))
      .Times(1);
  health_status->AddStatisticForGroup(
      group_, LeAudioHealthGroupStatType::STREAM_CREATE_SUCCESS);
  health_status->AddStatisticForGroup(
      group_, LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED);
  health_status->AddStatisticForGroup(
      group_, LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED);

  /* Do not act on disconnect */
  ON_CALL(mock_gatt_interface_, Close(_)).WillByDefault(DoAll(Return()));
  ON_CALL(mock_btm_interface_, AclDisconnectFromHandle(_, _))
      .WillByDefault(DoAll(Return()));

  state_machine_callbacks_->OnStateTransitionTimeout(group_id_);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnHealthBasedGroupRecommendationAction(
                  1, LeAudioHealthBasedAction::CONSIDER_DISABLING))
      .Times(0);
  health_status->AddStatisticForGroup(
      group_, LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED);
  health_status->AddStatisticForGroup(
      group_, LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F(UnicastTest, ConnectDisconnectOneEarbud) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(1, test_address0,
                                codec_spec_conf::kLeAudioLocationStereo,
                                codec_spec_conf::kLeAudioLocationStereo);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);
  DisconnectLeAudioWithAclClose(test_address0, 1);
}

TEST_F(UnicastTest, ConnectRemoteServiceDiscoveryCompleteBeforeEncryption) {
  const RawAddress test_address0 = GetTestAddress(0);
  uint16_t conn_id = 1;
  SetSampleDatabaseEarbudsValid(conn_id, test_address0,
                                codec_spec_conf::kLeAudioLocationStereo,
                                codec_spec_conf::kLeAudioLocationStereo);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(0);
  ConnectLeAudio(test_address0, false);
  InjectSearchCompleteEvent(conn_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address0, _))
      .WillByDefault(DoAll(Return(true)));
  InjectEncryptionChangedEvent(test_address0);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F(UnicastTest, DisconnectWhenLinkKeyIsGone) {
  const RawAddress test_address0 = GetTestAddress(0);
  uint16_t conn_id = 1;
  SetSampleDatabaseEarbudsValid(conn_id, test_address0,
                                codec_spec_conf::kLeAudioLocationStereo,
                                codec_spec_conf::kLeAudioLocationStereo);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);

  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address0, _))
      .WillByDefault(DoAll(Return(false)));

  ON_CALL(mock_btm_interface_, SetEncryption(test_address0, _, _, _, _))
      .WillByDefault(Return(BTM_ERR_KEY_MISSING));

  EXPECT_CALL(mock_gatt_interface_, Close(conn_id)).Times(1);
  do_in_main_thread(
      FROM_HERE,
      base::BindOnce(&LeAudioClient::Connect,
                     base::Unretained(LeAudioClient::Get()), test_address0));

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_btm_interface_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

/* same as above case except the disconnect is initiated by remote */
TEST_F(UnicastTest, ConnectRemoteDisconnectOneEarbud) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(1, test_address0,
                                codec_spec_conf::kLeAudioLocationStereo,
                                codec_spec_conf::kLeAudioLocationStereo);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  /* Make sure when remote device disconnects us, TA is used */
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, _))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  InjectDisconnectedEvent(1, GATT_CONN_TERMINATE_PEER_USER);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);

  /* When reconnected, we always remove background connect, as we do not track
   * which type (allow list or TA) was used and then make sure the TA is used.
   */
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, _))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  /* For background connect, test needs to Inject Connected Event */
  InjectConnectedEvent(test_address0, 1);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

/* same as above case except the disconnect is initiated by remote */
TEST_F(UnicastTest, ConnectRemoteDisconnectOnTimeoutOneEarbud) {
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(1, test_address0,
                                codec_spec_conf::kLeAudioLocationStereo,
                                codec_spec_conf::kLeAudioLocationStereo);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);

  /* Remove default action on the direct connect */
  ON_CALL(mock_gatt_interface_, Open(_, _, BTM_BLE_DIRECT_CONNECTION, _))
      .WillByDefault(Return());

  /* For remote disconnection, expect stack to try background re-connect */
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);

  InjectDisconnectedEvent(1, GATT_CONN_TIMEOUT);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);

  /* For background connect, test needs to Inject Connected Event */
  InjectConnectedEvent(test_address0, 1);
  SyncOnMainLoop();
}

TEST_F(UnicastTest, ConnectTwoEarbudsCsisGrouped) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  /* for Target announcements AutoConnect is always there, until
   * device is removed
   */
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, false))
      .Times(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, false))
      .Times(0);

  // Verify grouping information
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  DisconnectLeAudioWithAclClose(test_address0, 1);
  DisconnectLeAudioWithAclClose(test_address1, 2);
}

TEST_F(UnicastTest, ConnectTwoEarbudsCsisGroupUnknownAtConnect) {
  uint8_t group_size = 2;
  uint8_t group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud connects without known grouping
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  // Verify grouping information
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  /* for Target announcements AutoConnect is always there, until
   *  device is removed
   */
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, false))
      .Times(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, false))
      .Times(0);
  DisconnectLeAudioWithAclClose(test_address0, 1);
  DisconnectLeAudioWithAclClose(test_address1, 2);
}

TEST_F(UnicastTestNoInit, ConnectFailedDueToInvalidParameters) {
  // Prepare two devices
  uint8_t group_size = 2;
  uint8_t group_id = 2;

  /* Prepare  mock to not inject connect event so the device can stay in
   * CONNECTING state*/
  ON_CALL(mock_gatt_interface_, Open(_, _, BTM_BLE_DIRECT_CONNECTION, false))
      .WillByDefault(DoAll(Return()));

  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationFrontLeft,
      codec_spec_conf::kLeAudioLocationFrontLeft, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 1);

  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseEarbudsValid(
      2, test_address1, codec_spec_conf::kLeAudioLocationFrontRight,
      codec_spec_conf::kLeAudioLocationFrontRight, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 2);

  // Load devices from the storage when storage API is called
  bool autoconnect = true;

  /* Common storage values */
  std::vector<uint8_t> handles;
  LeAudioClient::GetHandlesForStorage(test_address0, handles);

  std::vector<uint8_t> ases;
  LeAudioClient::GetAsesForStorage(test_address0, ases);

  std::vector<uint8_t> src_pacs;
  LeAudioClient::GetSourcePacsForStorage(test_address0, src_pacs);

  std::vector<uint8_t> snk_pacs;
  LeAudioClient::GetSinkPacsForStorage(test_address0, snk_pacs);

  EXPECT_CALL(mock_storage_load, Call()).WillOnce([&]() {
    do_in_main_thread(
        FROM_HERE,
        base::Bind(&LeAudioClient::AddFromStorage, test_address0, autoconnect,
                   codec_spec_conf::kLeAudioLocationFrontLeft,
                   codec_spec_conf::kLeAudioLocationFrontLeft, 0xff, 0xff,
                   std::move(handles), std::move(snk_pacs), std::move(src_pacs),
                   std::move(ases)));
    do_in_main_thread(
        FROM_HERE,
        base::Bind(&LeAudioClient::AddFromStorage, test_address1, autoconnect,
                   codec_spec_conf::kLeAudioLocationFrontRight,
                   codec_spec_conf::kLeAudioLocationFrontRight, 0xff, 0xff,
                   std::move(handles), std::move(snk_pacs), std::move(src_pacs),
                   std::move(ases)));
  });

  // Expect stored device0 to connect automatically (first directed connection )
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);

  // Expect stored device1 to connect automatically (first direct connection)
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address1, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);

  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address1, _))
      .WillByDefault(DoAll(Return(true)));
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address0, _))
      .WillByDefault(DoAll(Return(true)));

  ON_CALL(mock_groups_module_, GetGroupId(_, _))
      .WillByDefault(DoAll(Return(group_id)));

  ON_CALL(mock_btm_interface_,
          GetSecurityFlagsByTransport(test_address0, NotNull(), _))
      .WillByDefault(
          DoAll(SetArgPointee<1>(BTM_SEC_FLAG_ENCRYPTED), Return(true)));

  std::vector<::bluetooth::le_audio::btle_audio_codec_config_t>
      framework_encode_preference;

  // Initialize
  BtaAppRegisterCallback app_register_callback;
  ON_CALL(mock_gatt_interface_, AppRegister(_, _, _))
      .WillByDefault(DoAll(SaveArg<0>(&gatt_callback),
                           SaveArg<1>(&app_register_callback)));
  LeAudioClient::Initialize(
      &mock_audio_hal_client_callbacks_,
      base::Bind([](MockFunction<void()>* foo) { foo->Call(); },
                 &mock_storage_load),
      base::Bind([](MockFunction<bool()>* foo) { return foo->Call(); },
                 &mock_hal_2_1_verifier),
      framework_encode_preference);
  if (app_register_callback) app_register_callback.Run(gatt_if, GATT_SUCCESS);

  // We need to wait for the storage callback before verifying stuff
  SyncOnMainLoop();
  ASSERT_TRUE(LeAudioClient::IsLeAudioClientRunning());
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  // Simulate connect parameters are invalid and phone does not fallback
  // to background connect.
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(0);

  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address1,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(0);

  // Devices not found
  InjectConnectedEvent(test_address0, 0, GATT_ILLEGAL_PARAMETER);
  InjectConnectedEvent(test_address1, 0, GATT_ILLEGAL_PARAMETER);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

TEST_F(UnicastTestNoInit, LoadStoredEarbudsBroakenStorage) {
  // Prepare two devices
  uint8_t group_size = 2;
  uint8_t group_id = 2;
  /* If the storage has been broken, make sure device will be rediscovered after
   * reconnection
   */

  /* Prepare  mock to not inject connect event so the device can stay in
   * CONNECTING state*/
  ON_CALL(mock_gatt_interface_, Open(_, _, BTM_BLE_DIRECT_CONNECTION, false))
      .WillByDefault(DoAll(Return()));

  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationFrontLeft,
      codec_spec_conf::kLeAudioLocationFrontLeft, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 1);

  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseEarbudsValid(
      2, test_address1, codec_spec_conf::kLeAudioLocationFrontRight,
      codec_spec_conf::kLeAudioLocationFrontRight, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 2);

  // Load devices from the storage when storage API is called
  bool autoconnect = true;
  std::vector<uint8_t> empty_buf;

  EXPECT_CALL(mock_storage_load, Call()).WillOnce([&]() {
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&LeAudioClient::AddFromStorage, test_address0,
                       autoconnect, codec_spec_conf::kLeAudioLocationFrontLeft,
                       codec_spec_conf::kLeAudioLocationFrontLeft, 0xff, 0xff,
                       std::move(empty_buf), std::move(empty_buf),
                       std::move(empty_buf), std::move(empty_buf)));
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&LeAudioClient::AddFromStorage, test_address1,
                       autoconnect, codec_spec_conf::kLeAudioLocationFrontRight,
                       codec_spec_conf::kLeAudioLocationFrontRight, 0xff, 0xff,
                       std::move(empty_buf), std::move(empty_buf),
                       std::move(empty_buf), std::move(empty_buf)));
    SyncOnMainLoop();
  });

  // Expect stored device0 to connect automatically (first directed connection )
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);

  // Expect stored device1 to connect automatically (first direct connection)
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address1, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);

  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address1, _))
      .WillByDefault(DoAll(Return(true)));
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address0, _))
      .WillByDefault(DoAll(Return(true)));

  ON_CALL(mock_groups_module_, GetGroupId(_, _))
      .WillByDefault(DoAll(Return(group_id)));

  ON_CALL(mock_btm_interface_,
          GetSecurityFlagsByTransport(test_address0, NotNull(), _))
      .WillByDefault(
          DoAll(SetArgPointee<1>(BTM_SEC_FLAG_ENCRYPTED), Return(true)));

  std::vector<::bluetooth::le_audio::btle_audio_codec_config_t>
      framework_encode_preference;

  // Initialize
  BtaAppRegisterCallback app_register_callback;
  ON_CALL(mock_gatt_interface_, AppRegister(_, _, _))
      .WillByDefault(DoAll(SaveArg<0>(&gatt_callback),
                           SaveArg<1>(&app_register_callback)));
  LeAudioClient::Initialize(
      &mock_audio_hal_client_callbacks_,
      base::Bind([](MockFunction<void()>* foo) { foo->Call(); },
                 &mock_storage_load),
      base::Bind([](MockFunction<bool()>* foo) { return foo->Call(); },
                 &mock_hal_2_1_verifier),
      framework_encode_preference);
  if (app_register_callback) app_register_callback.Run(gatt_if, GATT_SUCCESS);

  // We need to wait for the storage callback before verifying stuff
  SyncOnMainLoop();
  ASSERT_TRUE(LeAudioClient::IsLeAudioClientRunning());
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  // Simulate devices are not there and phone fallbacks to targeted
  // announcements
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address1,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  // Devices not found
  InjectConnectedEvent(test_address0, 0, GATT_ERROR);
  InjectConnectedEvent(test_address1, 0, GATT_ERROR);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  /* Stack should rediscover services as storage is broken */
  EXPECT_CALL(mock_gatt_interface_, ServiceSearchRequest(2, _)).Times(1);
  EXPECT_CALL(mock_gatt_interface_, ServiceSearchRequest(1, _)).Times(1);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address1))
      .Times(1);

  /* For background connect, test needs to Inject Connected Event */
  InjectConnectedEvent(test_address0, 1);
  InjectConnectedEvent(test_address1, 2);
  SyncOnMainLoop();

  // Verify if all went well and we got the proper group
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  DisconnectLeAudioWithAclClose(test_address0, 1);
  DisconnectLeAudioWithAclClose(test_address1, 2);
}

TEST_F(UnicastTestNoInit, LoadStoredEarbudsCsisGrouped) {
  // Prepare two devices
  uint8_t group_size = 2;
  uint8_t group_id = 2;

  /* Prepare  mock to not inject connect event so the device can stay in
   * CONNECTING state*/
  ON_CALL(mock_gatt_interface_, Open(_, _, BTM_BLE_DIRECT_CONNECTION, false))
      .WillByDefault(DoAll(Return()));

  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationFrontLeft,
      codec_spec_conf::kLeAudioLocationFrontLeft, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 1);

  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseEarbudsValid(
      2, test_address1, codec_spec_conf::kLeAudioLocationFrontRight,
      codec_spec_conf::kLeAudioLocationFrontRight, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 2);

  // Load devices from the storage when storage API is called
  bool autoconnect = true;

  /* Common storage values */
  std::vector<uint8_t> handles;
  LeAudioClient::GetHandlesForStorage(test_address0, handles);

  std::vector<uint8_t> ases;
  LeAudioClient::GetAsesForStorage(test_address0, ases);

  std::vector<uint8_t> src_pacs;
  LeAudioClient::GetSourcePacsForStorage(test_address0, src_pacs);

  std::vector<uint8_t> snk_pacs;
  LeAudioClient::GetSinkPacsForStorage(test_address0, snk_pacs);

  EXPECT_CALL(mock_storage_load, Call()).WillOnce([&]() {
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&LeAudioClient::AddFromStorage, test_address0,
                       autoconnect, codec_spec_conf::kLeAudioLocationFrontLeft,
                       codec_spec_conf::kLeAudioLocationFrontLeft, 0xff, 0xff,
                       std::move(handles), std::move(snk_pacs),
                       std::move(src_pacs), std::move(ases)));
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&LeAudioClient::AddFromStorage, test_address1,
                       autoconnect, codec_spec_conf::kLeAudioLocationFrontRight,
                       codec_spec_conf::kLeAudioLocationFrontRight, 0xff, 0xff,
                       std::move(handles), std::move(snk_pacs),
                       std::move(src_pacs), std::move(ases)));
    SyncOnMainLoop();
  });

  // Expect stored device0 to connect automatically (first directed connection )
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);

  // Expect stored device1 to connect automatically (first direct connection)
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address1, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);

  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address1, _))
      .WillByDefault(DoAll(Return(true)));
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address0, _))
      .WillByDefault(DoAll(Return(true)));

  ON_CALL(mock_groups_module_, GetGroupId(_, _))
      .WillByDefault(DoAll(Return(group_id)));

  ON_CALL(mock_btm_interface_,
          GetSecurityFlagsByTransport(test_address0, NotNull(), _))
      .WillByDefault(
          DoAll(SetArgPointee<1>(BTM_SEC_FLAG_ENCRYPTED), Return(true)));

  std::vector<::bluetooth::le_audio::btle_audio_codec_config_t>
      framework_encode_preference;

  // Initialize
  BtaAppRegisterCallback app_register_callback;
  ON_CALL(mock_gatt_interface_, AppRegister(_, _, _))
      .WillByDefault(DoAll(SaveArg<0>(&gatt_callback),
                           SaveArg<1>(&app_register_callback)));
  LeAudioClient::Initialize(
      &mock_audio_hal_client_callbacks_,
      base::Bind([](MockFunction<void()>* foo) { foo->Call(); },
                 &mock_storage_load),
      base::Bind([](MockFunction<bool()>* foo) { return foo->Call(); },
                 &mock_hal_2_1_verifier),
      framework_encode_preference);
  if (app_register_callback) app_register_callback.Run(gatt_if, GATT_SUCCESS);

  // We need to wait for the storage callback before verifying stuff
  SyncOnMainLoop();
  ASSERT_TRUE(LeAudioClient::IsLeAudioClientRunning());
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  // Simulate devices are not there and phone fallbacks to targeted
  // announcements
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address1,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  // Devices not found
  InjectConnectedEvent(test_address0, 0, GATT_ERROR);
  InjectConnectedEvent(test_address1, 0, GATT_ERROR);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address1))
      .Times(1);

  /* For background connect, test needs to Inject Connected Event */
  InjectConnectedEvent(test_address0, 1);
  InjectConnectedEvent(test_address1, 2);
  SyncOnMainLoop();

  // Verify if all went well and we got the proper group
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  DisconnectLeAudioWithAclClose(test_address0, 1);
  DisconnectLeAudioWithAclClose(test_address1, 2);
}

TEST_F(UnicastTestNoInit, ServiceChangedBeforeServiceIsConnected) {
  // Prepare two devices
  uint8_t group_size = 2;
  uint8_t group_id = 2;

  /* Prepare  mock to not inject connect event so the device can stay in
   * CONNECTING state*/
  ON_CALL(mock_gatt_interface_, Open(_, _, BTM_BLE_DIRECT_CONNECTION, false))
      .WillByDefault(DoAll(Return()));

  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationFrontLeft,
      codec_spec_conf::kLeAudioLocationFrontLeft, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 1);

  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseEarbudsValid(
      2, test_address1, codec_spec_conf::kLeAudioLocationFrontRight,
      codec_spec_conf::kLeAudioLocationFrontRight, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 2);

  // Load devices from the storage when storage API is called
  bool autoconnect = true;

  /* Common storage values */
  std::vector<uint8_t> handles;
  LeAudioClient::GetHandlesForStorage(test_address0, handles);

  std::vector<uint8_t> ases;
  LeAudioClient::GetAsesForStorage(test_address0, ases);

  std::vector<uint8_t> src_pacs;
  LeAudioClient::GetSourcePacsForStorage(test_address0, src_pacs);

  std::vector<uint8_t> snk_pacs;
  LeAudioClient::GetSinkPacsForStorage(test_address0, snk_pacs);

  EXPECT_CALL(mock_storage_load, Call()).WillOnce([&]() {
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&LeAudioClient::AddFromStorage, test_address0,
                       autoconnect, codec_spec_conf::kLeAudioLocationFrontLeft,
                       codec_spec_conf::kLeAudioLocationFrontLeft, 0xff, 0xff,
                       std::move(handles), std::move(snk_pacs),
                       std::move(src_pacs), std::move(ases)));
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&LeAudioClient::AddFromStorage, test_address1,
                       autoconnect, codec_spec_conf::kLeAudioLocationFrontRight,
                       codec_spec_conf::kLeAudioLocationFrontRight, 0xff, 0xff,
                       std::move(handles), std::move(snk_pacs),
                       std::move(src_pacs), std::move(ases)));
    SyncOnMainLoop();
  });

  // Expect stored device0 to connect automatically (first directed connection )
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);

  // Expect stored device1 to connect automatically (first direct connection)
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address1, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);

  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address1, _))
      .WillByDefault(DoAll(Return(true)));
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address0, _))
      .WillByDefault(DoAll(Return(true)));

  ON_CALL(mock_groups_module_, GetGroupId(_, _))
      .WillByDefault(DoAll(Return(group_id)));

  ON_CALL(mock_btm_interface_,
          GetSecurityFlagsByTransport(test_address0, NotNull(), _))
      .WillByDefault(
          DoAll(SetArgPointee<1>(BTM_SEC_FLAG_ENCRYPTED), Return(true)));

  std::vector<::bluetooth::le_audio::btle_audio_codec_config_t>
      framework_encode_preference;

  // Initialize
  BtaAppRegisterCallback app_register_callback;
  ON_CALL(mock_gatt_interface_, AppRegister(_, _, _))
      .WillByDefault(DoAll(SaveArg<0>(&gatt_callback),
                           SaveArg<1>(&app_register_callback)));
  LeAudioClient::Initialize(
      &mock_audio_hal_client_callbacks_,
      base::Bind([](MockFunction<void()>* foo) { foo->Call(); },
                 &mock_storage_load),
      base::Bind([](MockFunction<bool()>* foo) { return foo->Call(); },
                 &mock_hal_2_1_verifier),
      framework_encode_preference);
  if (app_register_callback) app_register_callback.Run(gatt_if, GATT_SUCCESS);

  // We need to wait for the storage callback before verifying stuff
  SyncOnMainLoop();
  ASSERT_TRUE(LeAudioClient::IsLeAudioClientRunning());
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  /* Inject Service Changed */
  InjectServiceChangedEvent(test_address1, 0xffff);
  SyncOnMainLoop();
  InjectServiceChangedEvent(test_address0, 0xffff);
  SyncOnMainLoop();
  /* Stack should rediscover services as storage is broken */
  EXPECT_CALL(mock_gatt_interface_, ServiceSearchRequest(2, _)).Times(1);
  EXPECT_CALL(mock_gatt_interface_, ServiceSearchRequest(1, _)).Times(1);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address1))
      .Times(1);

  /* For background connect, test needs to Inject Connected Event */
  InjectConnectedEvent(test_address0, 1);
  InjectConnectedEvent(test_address1, 2);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F(UnicastTestNoInit, LoadStoredEarbudsCsisGroupedDifferently) {
  // Prepare two devices
  uint8_t group_size = 1;

  // Device 0
  uint8_t group_id0 = 2;
  bool autoconnect0 = true;
  const RawAddress test_address0 = GetTestAddress(0);
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationFrontLeft,
      codec_spec_conf::kLeAudioLocationFrontLeft, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      true,                                /*add_ascs*/
      group_size, 1);

  ON_CALL(mock_groups_module_, GetGroupId(test_address0, _))
      .WillByDefault(DoAll(Return(group_id0)));

  // Device 1
  uint8_t group_id1 = 3;
  bool autoconnect1 = false;
  const RawAddress test_address1 = GetTestAddress(1);
  SetSampleDatabaseEarbudsValid(
      2, test_address1, codec_spec_conf::kLeAudioLocationFrontRight,
      codec_spec_conf::kLeAudioLocationFrontRight, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ true, /*add_csis*/
      true,                                /*add_cas*/
      true,                                /*add_pacs*/
      default_ase_cnt,                     /*add_ascs_cnt*/
      group_size, 2);

  ON_CALL(mock_groups_module_, GetGroupId(test_address1, _))
      .WillByDefault(DoAll(Return(group_id1)));

  /* Commont storage values */
  std::vector<uint8_t> handles;
  LeAudioClient::GetHandlesForStorage(test_address0, handles);

  std::vector<uint8_t> ases;
  LeAudioClient::GetAsesForStorage(test_address0, ases);

  std::vector<uint8_t> src_pacs;
  LeAudioClient::GetSourcePacsForStorage(test_address0, src_pacs);

  std::vector<uint8_t> snk_pacs;
  LeAudioClient::GetSinkPacsForStorage(test_address0, snk_pacs);

  // Load devices from the storage when storage API is called
  EXPECT_CALL(mock_storage_load, Call()).WillOnce([&]() {
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&LeAudioClient::AddFromStorage, test_address0,
                       autoconnect0, codec_spec_conf::kLeAudioLocationFrontLeft,
                       codec_spec_conf::kLeAudioLocationFrontLeft, 0xff, 0xff,
                       std::move(handles), std::move(snk_pacs),
                       std::move(src_pacs), std::move(ases)));
    do_in_main_thread(
        FROM_HERE,
        base::BindOnce(&LeAudioClient::AddFromStorage, test_address1,
                       autoconnect1,
                       codec_spec_conf::kLeAudioLocationFrontRight,
                       codec_spec_conf::kLeAudioLocationFrontRight, 0xff, 0xff,
                       std::move(handles), std::move(snk_pacs),
                       std::move(src_pacs), std::move(ases)));
  });

  // Expect stored device0 to connect automatically
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address0, _))
      .WillByDefault(DoAll(Return(true)));

  // First device will got connected
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, _))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  // Expect stored device1 to NOT connect automatically
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address1))
      .Times(0);
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address1, _))
      .WillByDefault(DoAll(Return(true)));

  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address1, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(0);

  // Initialize
  BtaAppRegisterCallback app_register_callback;
  ON_CALL(mock_gatt_interface_, AppRegister(_, _, _))
      .WillByDefault(DoAll(SaveArg<0>(&gatt_callback),
                           SaveArg<1>(&app_register_callback)));
  std::vector<::bluetooth::le_audio::btle_audio_codec_config_t>
      framework_encode_preference;
  LeAudioClient::Initialize(
      &mock_audio_hal_client_callbacks_,
      base::Bind([](MockFunction<void()>* foo) { foo->Call(); },
                 &mock_storage_load),
      base::Bind([](MockFunction<bool()>* foo) { return foo->Call(); },
                 &mock_hal_2_1_verifier),
      framework_encode_preference);
  if (app_register_callback) app_register_callback.Run(gatt_if, GATT_SUCCESS);

  // We need to wait for the storage callback before verifying stuff
  SyncOnMainLoop();
  ASSERT_TRUE(LeAudioClient::IsLeAudioClientRunning());
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Simulate device is not there and phone fallbacks to targeted announcements
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  // Devices 0 is connected. Disconnect it
  InjectDisconnectedEvent(1, GATT_CONN_TERMINATE_PEER_USER);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, _))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  /* Keep device in Getting Ready state */
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address0, _))
      .WillByDefault(DoAll(Return(false)));
  ON_CALL(mock_btm_interface_, SetEncryption(test_address0, _, _, _, _))
      .WillByDefault(Return(BTM_SUCCESS));

  /* For background connect, test needs to Inject Connected Event */
  InjectConnectedEvent(test_address0, 1);

  // We need to wait for the storage callback before verifying stuff
  SyncOnMainLoop();
  ASSERT_TRUE(LeAudioClient::IsLeAudioClientRunning());
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id0);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_EQ(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  devs = LeAudioClient::Get()->GetGroupDevices(group_id1);
  ASSERT_EQ(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  /* Disconnects while being in getting ready state */
  DisconnectLeAudioWithGattClose(test_address0, 1);
}

TEST_F(UnicastTest, GroupingAddRemove) {
  // Earbud connects without known grouping
  uint8_t group_id0 = bluetooth::groups::kGroupUnknown;
  const RawAddress test_address0 = GetTestAddress(0);

  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectNonCsisDevice(test_address0, 1 /*conn_id*/,
                       codec_spec_conf::kLeAudioLocationFrontLeft,
                       codec_spec_conf::kLeAudioLocationFrontLeft);

  group_id0 = MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address0);

  // Earbud connects without known grouping
  uint8_t group_id1 = bluetooth::groups::kGroupUnknown;
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectNonCsisDevice(test_address1, 2 /*conn_id*/,
                       codec_spec_conf::kLeAudioLocationFrontRight,
                       codec_spec_conf::kLeAudioLocationFrontRight);

  group_id1 = MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address1);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  // Verify individual groups
  ASSERT_NE(group_id0, bluetooth::groups::kGroupUnknown);
  ASSERT_NE(group_id1, bluetooth::groups::kGroupUnknown);
  ASSERT_NE(group_id0, group_id1);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id0).size(), 1u);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id1).size(), 1u);

  // Expectations on reassigning second earbud to the first group
  int dev1_storage_group = bluetooth::groups::kGroupUnknown;
  int dev1_new_group = bluetooth::groups::kGroupUnknown;

  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address1, group_id1, GroupNodeStatus::REMOVED))
      .Times(AtLeast(1));
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address1, _, GroupNodeStatus::ADDED))
      .WillRepeatedly(SaveArg<1>(&dev1_new_group));
  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address1, group_id1))
      .Times(AtLeast(1));
  EXPECT_CALL(mock_groups_module_, AddDevice(test_address1, _, _))
      .Times(AnyNumber());

  LeAudioClient::Get()->GroupRemoveNode(group_id1, test_address1);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_groups_module_);
  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  EXPECT_CALL(mock_groups_module_, AddDevice(test_address1, _, group_id0))
      .Times(1);

  LeAudioClient::Get()->GroupAddNode(group_id0, test_address1);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_groups_module_);

  dev1_storage_group =
      MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address1);

  // Verify regrouping results
  EXPECT_EQ(dev1_new_group, group_id0);
  EXPECT_EQ(dev1_new_group, dev1_storage_group);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id1).size(), 0u);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id0).size(), 2u);
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id0);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());
}

TEST_F(UnicastTest, DoubleResumeFromAF) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  constexpr int gmcs_ccid = 1;
  constexpr int gtbs_ccid = 2;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->SetCcidInformation(gmcs_ccid, 4 /* Media */);
  LeAudioClient::Get()->SetCcidInformation(gtbs_ccid, 2 /* Phone */);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  types::BidirectionalPair<std::vector<uint8_t>> ccids = {.sink = {gmcs_ccid},
                                                          .source = {}};
  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, ccids)).Times(1);

  block_streaming_state_callback = true;

  UpdateLocalSourceMetadata(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC);
  LocalAudioSourceResume(false);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Additional resume shall be ignored.
  LocalAudioSourceResume(false, false);

  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(0);

  do_in_main_thread(
      FROM_HERE,
      base::BindOnce(
          [](int group_id, le_audio::LeAudioGroupStateMachine::Callbacks*
                               state_machine_callbacks) {
            state_machine_callbacks->StatusReportCb(
                group_id, GroupStreamStatus::STREAMING);
          },
          group_id, base::Unretained(state_machine_callbacks_)));
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  // Verify Data transfer on one audio source cis
  constexpr uint8_t cis_count_out = 1;
  constexpr uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);
}

TEST_F(UnicastTest, DoubleResumeFromAFOnLocalSink) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  default_channel_cnt = 1;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, _)).Times(1);

  block_streaming_state_callback = true;

  UpdateLocalSinkMetadata(AUDIO_SOURCE_MIC);
  LocalAudioSinkResume();

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  EXPECT_CALL(*mock_le_audio_sink_hal_client_, CancelStreamingRequest())
      .Times(0);

  // Actuall test here: send additional resume which shall be ignored.
  LocalAudioSinkResume();

  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(0);

  do_in_main_thread(
      FROM_HERE,
      base::BindOnce(
          [](int group_id, le_audio::LeAudioGroupStateMachine::Callbacks*
                               state_machine_callbacks) {
            state_machine_callbacks->StatusReportCb(
                group_id, GroupStreamStatus::STREAMING);
          },
          group_id, base::Unretained(state_machine_callbacks_)));
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  // Verify Data transfer on local audio sink which is started
  constexpr uint8_t cis_count_out = 0;
  constexpr uint8_t cis_count_in = 1;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 0, 40);
}

TEST_F(UnicastTest, HandleResumeWithoutMetadataUpdateOnLocalSink) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  /**
   * In this test we want to make sure that if MetadataUpdate is
   * not called before Resume, but the context type is supported,
   * stream should be created
   */

  default_channel_cnt = 1;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, _)).Times(1);

  UpdateLocalSinkMetadata(AUDIO_SOURCE_MIC);
  LocalAudioSinkResume();

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  // Verify Data transfer on local audio sink which is started
  constexpr uint8_t cis_count_out = 0;
  constexpr uint8_t cis_count_in = 1;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 0, 40);

  SyncOnMainLoop();
  /* Clear cache by changing context types, this is required for the test
   * as setting active device actually generate cache
   */
  auto sink_available_context = types::kLeAudioContextAllRemoteSinkOnly;
  auto source_available_context = types::kLeAudioContextAllRemoteSource;
  InjectAvailableContextTypes(test_address0, 1, sink_available_context,
                              source_available_context);

  StopStreaming(group_id, true);
  SyncOnMainLoop();

  // simulate suspend timeout passed, alarm executing
  fake_osi_alarm_set_on_mloop_.cb(fake_osi_alarm_set_on_mloop_.data);
  SyncOnMainLoop();

  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, _)).Times(1);

  // Resume without metadata update while cached configuration is cleared
  LocalAudioSinkResume();
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
}

TEST_F(UnicastTest, RemoveNodeWhileStreaming) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  constexpr uint8_t cis_count_out = 1;
  constexpr uint8_t cis_count_in = 0;

  constexpr int gmcs_ccid = 1;
  constexpr int gtbs_ccid = 2;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->SetCcidInformation(gmcs_ccid, 4 /* Media */);
  LeAudioClient::Get()->SetCcidInformation(gtbs_ccid, 2 /* Phone */);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  types::BidirectionalPair<std::vector<uint8_t>> ccids = {.sink = {gmcs_ccid},
                                                          .source = {}};
  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, ccids)).Times(1);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address0, group_id))
      .Times(1);
  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(1);
  EXPECT_CALL(mock_state_machine_, ProcessHciNotifAclDisconnected(_, _))
      .Times(1);
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address0, group_id, GroupNodeStatus::REMOVED));
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);

  LeAudioClient::Get()->GroupRemoveNode(group_id, test_address0);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_groups_module_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F(UnicastTest, InactiveDeviceOnInternalStateMachineError) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  constexpr uint8_t cis_count_out = 1;
  constexpr uint8_t cis_count_in = 0;

  constexpr int gmcs_ccid = 1;
  constexpr int gtbs_ccid = 2;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->SetCcidInformation(gmcs_ccid, 4 /* Media */);
  LeAudioClient::Get()->SetCcidInformation(gtbs_ccid, 2 /* Phone */);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  types::BidirectionalPair<std::vector<uint8_t>> ccids = {.sink = {gmcs_ccid},
                                                          .source = {}};
  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, ccids)).Times(1);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStatus(group_id, GroupStatus::INACTIVE))
      .Times(1);

  /* This is internal error of the state machine */
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::RELEASING);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F(UnicastTest, GroupingAddTwiceNoRemove) {
  // Earbud connects without known grouping
  uint8_t group_id0 = bluetooth::groups::kGroupUnknown;
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .WillOnce(Return())
      .RetiresOnSaturation();
  ConnectNonCsisDevice(test_address0, 1 /*conn_id*/,
                       codec_spec_conf::kLeAudioLocationFrontLeft,
                       codec_spec_conf::kLeAudioLocationFrontLeft);

  group_id0 = MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address0);

  // Earbud connects without known grouping
  uint8_t group_id1 = bluetooth::groups::kGroupUnknown;
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .WillOnce(Return())
      .RetiresOnSaturation();
  ConnectNonCsisDevice(test_address1, 2 /*conn_id*/,
                       codec_spec_conf::kLeAudioLocationFrontRight,
                       codec_spec_conf::kLeAudioLocationFrontRight);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  group_id1 = MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address1);
  // Verify individual groups
  ASSERT_NE(group_id0, bluetooth::groups::kGroupUnknown);
  ASSERT_NE(group_id1, bluetooth::groups::kGroupUnknown);
  ASSERT_NE(group_id0, group_id1);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id0).size(), 1u);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id1).size(), 1u);

  // Expectations on reassigning second earbud to the first group
  int dev1_storage_group = bluetooth::groups::kGroupUnknown;
  int dev1_new_group = bluetooth::groups::kGroupUnknown;

  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address1, group_id1, GroupNodeStatus::REMOVED))
      .Times(AtLeast(1));
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address1, _, GroupNodeStatus::ADDED))
      .WillRepeatedly(SaveArg<1>(&dev1_new_group));

  // FIXME: We should expect removal with group_id context. No such API exists.
  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address1, group_id1))
      .Times(AtLeast(1));
  EXPECT_CALL(mock_groups_module_, AddDevice(test_address1, _, _))
      .Times(AnyNumber());
  EXPECT_CALL(mock_groups_module_, AddDevice(test_address1, _, group_id0))
      .Times(1);

  // Regroup device: assign new group without removing it from the first one
  LeAudioClient::Get()->GroupAddNode(group_id0, test_address1);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_groups_module_);

  dev1_storage_group =
      MockDeviceGroups::DeviceGroups::Get()->GetGroupId(test_address1);

  // Verify regrouping results
  EXPECT_EQ(dev1_new_group, group_id0);
  EXPECT_EQ(dev1_new_group, dev1_storage_group);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id1).size(), 0u);
  ASSERT_EQ(LeAudioClient::Get()->GetGroupDevices(group_id0).size(), 2u);
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id0);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());
}

TEST_F(UnicastTest, RemoveTwoEarbudsCsisGrouped) {
  uint8_t group_size = 2;
  int group_id0 = 2;
  int group_id1 = 3;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First group - First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id0, 1 /* rank*/);

  // First group - Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id0, 2 /* rank*/, true /*connect_through_csis*/);

  // Second group - First earbud
  const RawAddress test_address2 = GetTestAddress(2);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address2, true))
      .Times(1);
  ConnectCsisDevice(test_address2, 3 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id1, 1 /* rank*/);

  // Second group - Second earbud
  const RawAddress test_address3 = GetTestAddress(3);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address3, true))
      .Times(1);
  ConnectCsisDevice(test_address3, 4 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id1, 2 /* rank*/, true /*connect_through_csis*/);

  // First group - verify grouping information
  std::vector<RawAddress> group0_devs =
      LeAudioClient::Get()->GetGroupDevices(group_id0);
  ASSERT_NE(std::find(group0_devs.begin(), group0_devs.end(), test_address0),
            group0_devs.end());
  ASSERT_NE(std::find(group0_devs.begin(), group0_devs.end(), test_address1),
            group0_devs.end());

  // Second group - verify grouping information
  std::vector<RawAddress> group1_devs =
      LeAudioClient::Get()->GetGroupDevices(group_id1);
  ASSERT_NE(std::find(group1_devs.begin(), group1_devs.end(), test_address2),
            group1_devs.end());
  ASSERT_NE(std::find(group1_devs.begin(), group1_devs.end(), test_address3),
            group1_devs.end());
  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  // Expect one of the groups to be dropped and devices to be disconnected
  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address0, group_id0))
      .Times(1);
  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address1, group_id0))
      .Times(1);
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address0, group_id0, GroupNodeStatus::REMOVED));
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address1, group_id0, GroupNodeStatus::REMOVED));
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address1))
      .Times(1);

  EXPECT_CALL(mock_btm_interface_, AclDisconnectFromHandle(1, _)).Times(1);
  EXPECT_CALL(mock_btm_interface_, AclDisconnectFromHandle(2, _)).Times(1);

  // Expect the other groups to be left as is
  EXPECT_CALL(mock_audio_hal_client_callbacks_, OnGroupStatus(group_id1, _))
      .Times(0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address2))
      .Times(0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address3))
      .Times(0);

  EXPECT_CALL(mock_btm_interface_, AclDisconnectFromHandle(3, _)).Times(0);
  EXPECT_CALL(mock_btm_interface_, AclDisconnectFromHandle(4, _)).Times(0);

  do_in_main_thread(
      FROM_HERE,
      base::BindOnce(&LeAudioClient::GroupDestroy,
                     base::Unretained(LeAudioClient::Get()), group_id0));

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_btif_storage_);
  Mock::VerifyAndClearExpectations(&mock_btm_interface_);
}

TEST_F(UnicastTest, RemoveDeviceWhenConnected) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;
  uint16_t conn_id = 1;

  SetSampleDatabaseEarbudsValid(
      conn_id, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, false))
      .Times(1);
  EXPECT_CALL(mock_gatt_queue_, Clean(conn_id)).Times(AtLeast(1));
  EXPECT_CALL(mock_btm_interface_, AclDisconnectFromHandle(1, _)).Times(1);

  /*
   * StopStream will put calls on main_loop so to keep the correct order
   * of operations and to avoid races we put the test command on main_loop as
   * well.
   */
  do_in_main_thread(FROM_HERE, base::BindOnce(
                                   [](LeAudioClient* client,
                                      const RawAddress& test_address0) {
                                     client->RemoveDevice(test_address0);
                                   },
                                   LeAudioClient::Get(), test_address0));
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);
  Mock::VerifyAndClearExpectations(&mock_gatt_queue_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

TEST_F(UnicastTest, RemoveDeviceWhenConnecting) {
  const RawAddress test_address0 = GetTestAddress(0);
  uint16_t conn_id = 1;

  /* Prepare  mock to not inject connect event so the device can stay in
   * CONNECTING state*/
  ON_CALL(mock_gatt_interface_, Open(_, _, BTM_BLE_DIRECT_CONNECTION, _))
      .WillByDefault(DoAll(Return()));

  SetSampleDatabaseEarbudsValid(
      conn_id, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(0);
  ConnectLeAudio(test_address0, true, false);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, true))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, false))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Open(gatt_if, test_address0, _, _))
      .Times(0);

  /*
   * StopStream will put calls on main_loop so to keep the correct order
   * of operations and to avoid races we put the test command on main_loop as
   * well.
   */
  do_in_main_thread(FROM_HERE, base::BindOnce(
                                   [](LeAudioClient* client,
                                      const RawAddress& test_address0) {
                                     client->RemoveDevice(test_address0);
                                   },
                                   LeAudioClient::Get(), test_address0));

  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

TEST_F(UnicastTest, RemoveDeviceWhenGettingConnectionReady) {
  const RawAddress test_address0 = GetTestAddress(0);
  uint16_t conn_id = 1;

  /* Prepare  mock to not inject Service Search Complete*/
  ON_CALL(mock_gatt_interface_, ServiceSearchRequest(_, _))
      .WillByDefault(DoAll(Return()));

  SetSampleDatabaseEarbudsValid(
      conn_id, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(0);
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, false))
      .Times(0);
  ConnectLeAudio(test_address0);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  EXPECT_CALL(mock_gatt_queue_, Clean(conn_id)).Times(AtLeast(1));
  EXPECT_CALL(mock_gatt_interface_, Close(conn_id)).Times(1);

  /* Cancel should be called in RemoveDevice */
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, false))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Open(gatt_if, test_address0, _, _))
      .Times(0);

  /*
   * StopStream will put calls on main_loop so to keep the correct order
   * of operations and to avoid races we put the test command on main_loop as
   * well.
   */
  do_in_main_thread(FROM_HERE, base::BindOnce(
                                   [](LeAudioClient* client,
                                      const RawAddress& test_address0) {
                                     client->RemoveDevice(test_address0);
                                   },
                                   LeAudioClient::Get(), test_address0));

  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

TEST_F(UnicastTest, DisconnectDeviceWhenConnected) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;
  uint16_t conn_id = 1;

  SetSampleDatabaseEarbudsValid(
      conn_id, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  /* for Target announcements AutoConnect is always there, until
   * device is removed
   */
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, false))
      .Times(0);
  EXPECT_CALL(mock_gatt_queue_, Clean(conn_id)).Times(AtLeast(1));
  EXPECT_CALL(mock_btm_interface_, AclDisconnectFromHandle(1, _)).Times(1);

  LeAudioClient::Get()->Disconnect(test_address0);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);
  Mock::VerifyAndClearExpectations(&mock_gatt_queue_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

TEST_F(UnicastTest, DisconnectDeviceWhenConnecting) {
  const RawAddress test_address0 = GetTestAddress(0);
  uint16_t conn_id = 1;

  /* Prepare  mock to not inject connect event so the device can stay in
   * CONNECTING state*/
  ON_CALL(mock_gatt_interface_, Open(_, _, BTM_BLE_DIRECT_CONNECTION, _))
      .WillByDefault(DoAll(Return()));

  SetSampleDatabaseEarbudsValid(
      conn_id, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(0);
  ConnectLeAudio(test_address0, true, false);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  /* Prepare on call mock on Close - to not trigger Inject Disconnection, as it
   * is done in default mock.
   */
  ON_CALL(mock_gatt_interface_, Close(_)).WillByDefault(DoAll(Return()));
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, true))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, Open(gatt_if, test_address0, _, _))
      .Times(0);

  LeAudioClient::Get()->Disconnect(test_address0);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

TEST_F(UnicastTest, DisconnectDeviceWhenGettingConnectionReady) {
  const RawAddress test_address0 = GetTestAddress(0);
  uint16_t conn_id = global_conn_id;

  /* Prepare  mock to not inject Service Search Complete*/
  ON_CALL(mock_gatt_interface_, ServiceSearchRequest(_, _))
      .WillByDefault(DoAll(Return()));

  SetSampleDatabaseEarbudsValid(
      conn_id, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(0);
  ConnectLeAudio(test_address0);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  /* TA reconnect is enabled in ConnectLeAudio. Make sure this is not removed */
  EXPECT_CALL(mock_gatt_queue_, Clean(conn_id)).Times(AtLeast(1));
  EXPECT_CALL(mock_gatt_interface_, Close(conn_id)).Times(1);
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, _))
      .Times(0);
  EXPECT_CALL(mock_gatt_interface_, Open(gatt_if, test_address0, _, _))
      .Times(0);

  LeAudioClient::Get()->Disconnect(test_address0);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_gatt_queue_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

TEST_F(UnicastTest, RemoveWhileStreaming) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  constexpr uint8_t cis_count_out = 1;
  constexpr uint8_t cis_count_in = 0;

  constexpr int gmcs_ccid = 1;
  constexpr int gtbs_ccid = 2;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->SetCcidInformation(gmcs_ccid, 4 /* Media */);
  LeAudioClient::Get()->SetCcidInformation(gtbs_ccid, 2 /* Phone */);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  types::BidirectionalPair<std::vector<uint8_t>> ccids = {.sink = {gmcs_ccid},
                                                          .source = {}};
  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, ccids)).Times(1);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  EXPECT_CALL(mock_groups_module_, RemoveDevice(test_address0, group_id))
      .Times(1);

  LeAudioDeviceGroup* group = nullptr;
  EXPECT_CALL(mock_state_machine_, ProcessHciNotifAclDisconnected(_, _))
      .WillOnce(DoAll(SaveArg<0>(&group)));
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnGroupNodeStatus(test_address0, group_id, GroupNodeStatus::REMOVED));

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);

  /*
   * StopStream will put calls on main_loop so to keep the correct order
   * of operations and to avoid races we put the test command on main_loop as
   * well.
   */
  do_in_main_thread(FROM_HERE, base::BindOnce(
                                   [](LeAudioClient* client,
                                      const RawAddress& test_address0) {
                                     client->RemoveDevice(test_address0);
                                   },
                                   LeAudioClient::Get(), test_address0));

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_groups_module_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  ASSERT_EQ(group, nullptr);
}

TEST_F(UnicastTest, DisconnecteWhileAlmostStreaming) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  constexpr int gmcs_ccid = 1;
  constexpr int gtbs_ccid = 2;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->SetCcidInformation(gmcs_ccid, 4 /* Media */);
  LeAudioClient::Get()->SetCcidInformation(gtbs_ccid, 2 /* Phone */);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  types::BidirectionalPair<std::vector<uint8_t>> ccids = {.sink = {gmcs_ccid},
                                                          .source = {}};
  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, ccids)).Times(1);

  /* We want here to CIS be established but device not being yet in streaming
   * state
   */
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  SyncOnMainLoop();

  /* This is test code, which will change the group state to the one which
   * is required by test
   */
  auto group_inject = streaming_groups.at(group_id);
  group_inject->SetState(types::AseState::BTA_LE_AUDIO_ASE_STATE_ENABLING);

  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(1);

  LeAudioDeviceGroup* group = nullptr;
  EXPECT_CALL(mock_state_machine_, ProcessHciNotifAclDisconnected(_, _))
      .WillOnce(DoAll(SaveArg<0>(&group)));

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);

  /*
   * StopStream will put calls on main_loop so to keep the correct order
   * of operations and to avoid races we put the test command on main_loop as
   * well.
   */
  do_in_main_thread(FROM_HERE, base::BindOnce(
                                   [](LeAudioClient* client,
                                      const RawAddress& test_address0) {
                                     client->Disconnect(test_address0);
                                   },
                                   LeAudioClient::Get(), test_address0));

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_groups_module_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  ASSERT_EQ(group->GetState(), types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
}

TEST_F(UnicastTest, EarbudsTwsStyleStreaming) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, 0x01, 0x01, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, 2 /*add_ascs_cnt*/, 1 /*set_size*/, 0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Suspend
  /*TODO Need a way to verify STOP */
  LeAudioClient::Get()->GroupSuspend(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Resume
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Stop
  StopStreaming(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Release
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

TEST_F(UnicastTest, SpeakerFailedConversationalStreaming) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  available_src_context_types_ = 0;
  supported_src_context_types_ =
      available_src_context_types_ |
      types::AudioContexts(types::LeAudioContextType::UNSPECIFIED).value();
  available_snk_context_types_ = 0x0004;
  supported_snk_context_types_ =
      available_snk_context_types_ |
      types::AudioContexts(types::LeAudioContextType::UNSPECIFIED).value();

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo, 0,
      default_channel_cnt, default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Audio sessions are started only when device gets active
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  /* Nothing to do - expect no crash */
}

TEST_F(UnicastTest, SpeakerStreaming) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Suspend
  /*TODO Need a way to verify STOP */
  LeAudioClient::Get()->GroupSuspend(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Resume
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Stop
  StopStreaming(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Release
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

TEST_F(UnicastTest, SpeakerStreamingNonDefault) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  /**
   * Scenario test steps
   * 1. Set group active and stream VOICEASSISTANT
   * 2. Suspend group and resume with VOICEASSISTANT
   * 3. Stop Stream and make group inactive
   * 4. Start stream without setting metadata.
   * 5. Verify that UNSPECIFIED context type is used.
   */

  available_snk_context_types_ = (types::LeAudioContextType::VOICEASSISTANTS |
                                  types::LeAudioContextType::MEDIA |
                                  types::LeAudioContextType::UNSPECIFIED)
                                     .value();
  supported_snk_context_types_ = available_snk_context_types_;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_ASSISTANT, AUDIO_CONTENT_TYPE_UNKNOWN, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Suspend
  /*TODO Need a way to verify STOP */
  LeAudioClient::Get()->GroupSuspend(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Resume
  StartStreaming(AUDIO_USAGE_ASSISTANT, AUDIO_CONTENT_TYPE_UNKNOWN, group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Stop
  StopStreaming(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Release
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  /* When session is closed, the hal client mocks are freed - get new ones */
  SetUpMockAudioHal();
  /* Expect the previous release to clear the old audio session metadata */
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();
  EXPECT_CALL(mock_state_machine_,
              StartStream(_, types::LeAudioContextType::VOICEASSISTANTS, _, _))
      .Times(0);
  EXPECT_CALL(mock_state_machine_,
              StartStream(_, kLeAudioDefaultConfigurationContext, _, _))
      .Times(1);
  LocalAudioSourceResume();
}

TEST_F(UnicastTest, SpeakerStreamingAutonomousRelease) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, 1 /* cis_count_out */, 0 /* cis_count_in */,
                        1920);

  // Inject the IDLE state as if an autonomous release happened
  auto group = streaming_groups.at(group_id);
  ASSERT_NE(group, nullptr);
  for (LeAudioDevice* device = group->GetFirstDevice(); device != nullptr;
       device = group->GetNextDevice(device)) {
    for (auto& ase : device->ases_) {
      ase.cis_state = types::CisState::IDLE;
      ase.data_path_state = types::DataPathState::IDLE;
      ase.state = types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE;
      InjectCisDisconnected(group_id, ase.cis_conn_hdl);
    }
  }

  // Verify no Data transfer after the autonomous release
  TestAudioDataTransfer(group_id, 0 /* cis_count_out */, 0 /* cis_count_in */,
                        1920);
}

TEST_F(UnicastTest, TwoEarbudsStreaming) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on two peer sinks and one source
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 2;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  // Suspend
  LeAudioClient::Get()->GroupSuspend(group_id);
  SyncOnMainLoop();

  // Resume
  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Verify Data transfer still works
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  auto group = streaming_groups.at(group_id);

  // Stop
  StopStreaming(group_id, true);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Check if cache configuration is still present
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSource)
                  .has_value());

  // Release
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Setting group inactive, shall not change cached configuration
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSource)
                  .has_value());
}

TEST_F(UnicastTest, StreamingVxAospSampleSound) {
  uint8_t group_size = 2;
  int group_id = 2;

  /* Test to verify that tag VX_AOSP_SAMPLESOUND is always mapped to
   * LeAudioContextType::SOUNDEFFECTS
   */

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Set a test TAG
  char test_tag[] = "TEST_TAG2;VX_AOSP_SAMPLESOUND;TEST_TAG1";

  test_tags_ptr_ = test_tag;

  auto initial_context = types::LeAudioContextType::SOUNDEFFECTS;
  types::BidirectionalPair<std::vector<uint8_t>> ccids = {.sink = {},
                                                          .source = {}};
  EXPECT_CALL(mock_state_machine_, StartStream(_, initial_context, _, ccids))
      .Times(1);
  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on two peer sinks and one source
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 0);
}

TEST_F(UnicastTest, UpdateActiveAudioConfigForLocalSinkSource) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Set group as active
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Start streaming
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, UpdateAudioConfigToHal(_))
      .Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, UpdateAudioConfigToHal(_))
      .Times(1);
  EXPECT_CALL(*mock_codec_manager_, UpdateActiveAudioConfig(_, _, _))
      .Times(1)
      .WillOnce(
          [](const types::BidirectionalPair<stream_parameters>& stream_params,
             types::BidirectionalPair<uint16_t> delays_ms,
             std::function<void(const offload_config& config,
                                uint8_t direction)>
                 update_receiver) {
            le_audio::offload_config unicast_cfg;
            if (delays_ms.sink != 0) {
              update_receiver(unicast_cfg,
                              le_audio::types::kLeAudioDirectionSink);
            }
            if (delays_ms.source != 0) {
              update_receiver(unicast_cfg,
                              le_audio::types::kLeAudioDirectionSource);
            }
          });
  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_codec_manager_);

  // Verify Data transfer on two peer sinks and two sources
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 2;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  // Suspend
  LeAudioClient::Get()->GroupSuspend(group_id);
  SyncOnMainLoop();
}

TEST_F(UnicastTest, UpdateActiveAudioConfigForLocalSource) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Set group as active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, UpdateAudioConfigToHal(_))
      .Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, UpdateAudioConfigToHal(_))
      .Times(0);
  EXPECT_CALL(*mock_codec_manager_, UpdateActiveAudioConfig(_, _, _))
      .Times(1)
      .WillOnce(
          [](const types::BidirectionalPair<stream_parameters>& stream_params,
             types::BidirectionalPair<uint16_t> delays_ms,
             std::function<void(const offload_config& config,
                                uint8_t direction)>
                 update_receiver) {
            le_audio::offload_config unicast_cfg;
            if (delays_ms.sink != 0) {
              update_receiver(unicast_cfg,
                              le_audio::types::kLeAudioDirectionSink);
            }
            if (delays_ms.source != 0) {
              update_receiver(unicast_cfg,
                              le_audio::types::kLeAudioDirectionSource);
            }
          });
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_codec_manager_);

  // Verify Data transfer on two peer sinks and no source
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  // Suspend
  LeAudioClient::Get()->GroupSuspend(group_id);
  SyncOnMainLoop();
}

TEST_F(UnicastTest, TwoEarbudsStreamingContextSwitchNoReconfigure) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Start streaming with new metadata, there was no previous stream so start
  // with this new configuration
  auto initial_context = types::LeAudioContextType::NOTIFICATIONS;
  types::BidirectionalPair<types::AudioContexts> contexts = {
      .sink = types::AudioContexts(initial_context),
      .source = types::AudioContexts()};
  EXPECT_CALL(mock_state_machine_, StartStream(_, initial_context, contexts, _))
      .Times(1);

  StartStreaming(AUDIO_USAGE_NOTIFICATION, AUDIO_CONTENT_TYPE_UNKNOWN,
                 group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Do a metadata content switch to ALERTS but stay on previous configuration
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start).Times(0);
  contexts = {.sink = types::AudioContexts(types::LeAudioContextType::ALERTS),
              .source = types::AudioContexts()};
  EXPECT_CALL(mock_state_machine_, StartStream(_, initial_context, contexts, _))
      .Times(1);
  UpdateLocalSourceMetadata(AUDIO_USAGE_ALARM, AUDIO_CONTENT_TYPE_UNKNOWN);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Do a metadata content switch to EMERGENCY but stay on previous
  // configuration
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start).Times(0);

  contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::EMERGENCYALARM),
      .source = types::AudioContexts()};
  EXPECT_CALL(mock_state_machine_, StartStream(_, initial_context, contexts, _))
      .Times(1);
  UpdateLocalSourceMetadata(AUDIO_USAGE_EMERGENCY, AUDIO_CONTENT_TYPE_UNKNOWN);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Do a metadata content switch to INSTRUCTIONAL but stay on previous
  // configuration
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start).Times(0);
  contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::INSTRUCTIONAL),
      .source = types::AudioContexts()};
  EXPECT_CALL(mock_state_machine_, StartStream(_, initial_context, contexts, _))
      .Times(1);
  UpdateLocalSourceMetadata(AUDIO_USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                            AUDIO_CONTENT_TYPE_UNKNOWN);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

TEST_F(UnicastTest, TwoEarbudsStreamingContextSwitchReconfigure) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  constexpr int gmcs_ccid = 1;
  constexpr int gtbs_ccid = 2;

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming MEDIA
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->SetCcidInformation(gmcs_ccid, 4 /* Media */);
  LeAudioClient::Get()->SetCcidInformation(gtbs_ccid, 2 /* Phone */);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  types::BidirectionalPair<std::vector<uint8_t>> ccids = {.sink = {gmcs_ccid},
                                                          .source = {}};
  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, ccids)).Times(1);
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Verify Data transfer on two peer sinks
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Stop
  StopStreaming(group_id);
  // simulate suspend timeout passed, alarm executing
  fake_osi_alarm_set_on_mloop_.cb(fake_osi_alarm_set_on_mloop_.data);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // SetInCall is used by GTBS - and only then we can expect CCID to be set.
  LeAudioClient::Get()->SetInCall(true);

  // Conversational is a bidirectional scenario so expect GTBS CCID
  // in the metadata for both directions. Can be called twice when one
  // direction resume after the other and metadata is updated.
  ccids = {.sink = {gtbs_ccid}, .source = {gtbs_ccid}};
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, types::LeAudioContextType::CONVERSATIONAL, _, ccids))
      .Times(AtLeast(1));
  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Verify Data transfer on two peer sinks and one source
  cis_count_out = 2;
  cis_count_in = 2;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  LeAudioClient::Get()->SetInCall(false);
  // Stop
  StopStreaming(group_id, true);

  // Switch back to MEDIA
  ccids = {.sink = {gmcs_ccid}, .source = {}};
  types::BidirectionalPair<types::AudioContexts> contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::MEDIA),
      .source = types::AudioContexts()};
  EXPECT_CALL(mock_state_machine_,
              ConfigureStream(_, le_audio::types::LeAudioContextType::MEDIA,
                              contexts, ccids))
      .Times(1);
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id,
                 AUDIO_SOURCE_INVALID, true);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

TEST_F(UnicastTest, TwoEarbudsVoipStreamingVerifyMetadataUpdate) {
  uint8_t group_size = 2;
  int group_id = 2;

  /*
   * Scenario
   * 1. Configure stream for the VOIP
   * 2. Verify CONVERSATIONAL metadata and context is used.
   * 3. Resume LocalSink
   * 4. Make sure there is no change of the metadata and context
   */

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  constexpr int gtbs_ccid = 2;

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));
  LeAudioClient::Get()->SetCcidInformation(gtbs_ccid, 2 /* Phone */);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  // VOIP not using Telecom API has no ccids.
  types::BidirectionalPair<std::vector<uint8_t>> ccids = {.sink = {},
                                                          .source = {}};
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, types::LeAudioContextType::CONVERSATIONAL, _, ccids))
      .Times(AtLeast(1));

  UpdateLocalSourceMetadata(AUDIO_USAGE_VOICE_COMMUNICATION,
                            AUDIO_CONTENT_TYPE_SPEECH);
  UpdateLocalSinkMetadata(AUDIO_SOURCE_MIC);

  LocalAudioSourceResume();
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Verify Data transfer are sending. The LocalSink is not yet resumed.
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 0);

  types::BidirectionalPair<types::AudioContexts> contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::CONVERSATIONAL),
      .source =
          types::AudioContexts(types::LeAudioContextType::CONVERSATIONAL)};
  EXPECT_CALL(mock_state_machine_,
              StartStream(_, types::LeAudioContextType::CONVERSATIONAL,
                          contexts, ccids))
      .Times(AtLeast(1));

  LocalAudioSinkResume();
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
}

TEST_F(UnicastTest, TwoReconfigureAndVerifyEnableContextType) {
  uint8_t group_size = 2;
  int group_id = 2;

  /* Scenario
   * 1. Earbuds streaming MEDIA
   * 2. Reconfigure to VOIP
   * 3. Check if Metadata in Enable command are set to CONVERSATIONAL
   */

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  constexpr int gmcs_ccid = 1;
  constexpr int gtbs_ccid = 2;

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming MEDIA
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->SetCcidInformation(gmcs_ccid, 4 /* Media */);
  LeAudioClient::Get()->SetCcidInformation(gtbs_ccid, 2 /* Phone */);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  // Update metadata on local audio sink
  UpdateLocalSinkMetadata(AUDIO_SOURCE_MIC);

  types::BidirectionalPair<std::vector<uint8_t>> ccids = {.sink = {gmcs_ccid},
                                                          .source = {}};
  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, ccids)).Times(1);
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Verify Data transfer on two peer sinks
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Conversational is a bidirectional scenario so expect GTBS CCID
  // in the metadata for both directions. Can be called twice when one
  // direction resume after the other and metadata is updated.
  ccids = {.sink = {gtbs_ccid}, .source = {gtbs_ccid}};
  types::BidirectionalPair<types::AudioContexts> conversiational_contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::CONVERSATIONAL),
      .source =
          types::AudioContexts(types::LeAudioContextType::CONVERSATIONAL)};

  EXPECT_CALL(
      mock_state_machine_,
      ConfigureStream(_, types::LeAudioContextType::CONVERSATIONAL, _, _))
      .Times(AtLeast(1));

  // Update metadata and resume
  UpdateLocalSourceMetadata(AUDIO_USAGE_VOICE_COMMUNICATION,
                            AUDIO_CONTENT_TYPE_SPEECH, true);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  EXPECT_CALL(mock_state_machine_,
              StartStream(_, types::LeAudioContextType::CONVERSATIONAL,
                          conversiational_contexts, ccids))
      .Times(AtLeast(1));

  LeAudioClient::Get()->SetInCall(true);

  LocalAudioSourceResume(true);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_state_machine_);
}

TEST_F(UnicastTest, TwoEarbuds2ndLateConnect) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  const RawAddress test_address0 = GetTestAddress(0);
  const RawAddress test_address1 = GetTestAddress(1);

  // First earbud
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Expect one iso channel to be fed with data
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Second earbud connects during stream
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  cis_count_out = 2;
  cis_count_in = 0;

  /* The above will trigger reconfiguration. After that Audio Hal action
   * is needed to restart the stream */
  LocalAudioSourceResume();

  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);
}

TEST_F(UnicastTest,
       ReconnectedDeviceNotAttachedToStreamBecauseOfNotAvailableContext) {
  uint8_t group_size = 2;
  int group_id = 2;

  /* Scenario
   * 1. Two devices A and B are streaming
   * 2. Device A Release ASE and removes all available context types
   * 3. Device B keeps streaming
   * 4. Device A disconnectes
   * 5. Device A reconnect and should not be attached to the stream
   */

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  const RawAddress test_address0 = GetTestAddress(0);
  const RawAddress test_address1 = GetTestAddress(1);

  // First earbud connects
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud connects
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Expect two iso channel to be fed with data
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  /* Get group and Device A */
  auto group = streaming_groups.at(group_id);
  ASSERT_NE(group, nullptr);
  auto device = group->GetFirstDevice();

  /* Simulate available context type being cleared */
  InjectAvailableContextTypes(device->address_, device->conn_id_,
                              types::AudioContexts(0), types::AudioContexts(0));

  /* Simulate ASE releasing and CIS Disconnection */
  for (auto& ase : device->ases_) {
    /* Releasing state */
    if (!ase.active) {
      continue;
    }

    std::vector<uint8_t> releasing_state = {
        ase.id, static_cast<uint8_t>(
                    types::AseState::BTA_LE_AUDIO_ASE_STATE_RELEASING)};
    InjectNotificationEvent(device->address_, device->conn_id_,
                            ase.hdls.val_hdl, releasing_state);
    SyncOnMainLoop();
    InjectCisDisconnected(group_id, ase.cis_conn_hdl);
    SyncOnMainLoop();
  }

  cis_count_out = 1;
  cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  /* Device A will disconnect, and do not reconnect automatically */
  ON_CALL(mock_gatt_interface_,
          Open(_, device->address_, BTM_BLE_DIRECT_CONNECTION, _))
      .WillByDefault(Return());

  /* Disconnect first device */
  auto conn_id = device->conn_id_;
  InjectDisconnectedEvent(conn_id, GATT_CONN_TERMINATE_PEER_USER);
  SyncOnMainLoop();

  /* For background connect, test needs to Inject Connected Event */
  InjectConnectedEvent(device->address_, conn_id);
  SyncOnMainLoop();

  /* Check single device is streaming */
  cis_count_out = 1;
  cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);
}

TEST_F(UnicastTest, TwoEarbuds2ndReleaseAseRemoveAvailableContextAndBack) {
  uint8_t group_size = 2;
  int group_id = 2;

  /* Scenario
   * 1. Two devices A and B are streaming
   * 2. Device A Release ASE and removes all available context types
   * 3. Device B keeps streaming
   * 4. Device A sets available context types
   * 5. Device A should be attached to the stream
   */

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  const RawAddress test_address0 = GetTestAddress(0);
  const RawAddress test_address1 = GetTestAddress(1);

  // First earbud connects
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud connects
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Expect two iso channel to be fed with data
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  /* Get group and Device A */
  auto group = streaming_groups.at(group_id);
  ASSERT_NE(group, nullptr);
  auto device = group->GetFirstDevice();

  /* Simulate available context type being cleared */
  InjectAvailableContextTypes(device->address_, device->conn_id_,
                              types::AudioContexts(0), types::AudioContexts(0));

  /* Simulate ASE releasing and CIS Disconnection */
  for (auto& ase : device->ases_) {
    /* Releasing state */
    if (!ase.active) {
      continue;
    }

    std::vector<uint8_t> releasing_state = {
        ase.id, static_cast<uint8_t>(
                    types::AseState::BTA_LE_AUDIO_ASE_STATE_RELEASING)};
    InjectNotificationEvent(device->address_, device->conn_id_,
                            ase.hdls.val_hdl, releasing_state);
    SyncOnMainLoop();
    InjectCisDisconnected(group_id, ase.cis_conn_hdl);
    SyncOnMainLoop();
  }

  cis_count_out = 1;
  cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  /* Bring back available context types */
  InjectAvailableContextTypes(device->address_, device->conn_id_,
                              types::kLeAudioContextAllTypes,
                              types::kLeAudioContextAllTypes);

  /* Check both devices are streaming */
  cis_count_out = 2;
  cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);
}

TEST_F(UnicastTest, StartStream_AvailableContextTypeNotifiedLater) {
  uint8_t group_size = 2;
  int group_id = 2;

  available_snk_context_types_ = 0;

  /* Scenario (Devices A and B called "Remote")
   * 1. Remote  does supports all the context types, but has NO available
   * contexts at the beginning
   * 2. After connection Remote add Available context types
   * 3. Android start stream with MEDIA
   * 4. Make sure stream will be started
   */

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  const RawAddress test_address0 = GetTestAddress(0);
  const RawAddress test_address1 = GetTestAddress(1);

  // First earbud connects
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud connects
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  // Inject Supported and available context types
  auto sink_available_contexts = types::kLeAudioContextAllRemoteSinkOnly;
  auto source_available_contexts = types::kLeAudioContextAllRemoteSource;

  InjectAvailableContextTypes(test_address0, 1, sink_available_contexts,
                              source_available_contexts);
  InjectAvailableContextTypes(test_address1, 2, sink_available_contexts,
                              source_available_contexts);
  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  BidirectionalPair<AudioContexts> contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::MEDIA),
      .source = types::AudioContexts()};

  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::MEDIA, contexts, _))
      .Times(1);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Expect two iso channel to be fed with data
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);
}

TEST_F(UnicastTest, ModifyContextTypeOnDeviceA_WhileDeviceB_IsDisconnected) {
  uint8_t group_size = 2;
  int group_id = 2;

  /* Scenario (Device A and B called Remote)
   * 1. Remote set does supports all the context types and make them available
   * 2. Android start stream with MEDIA, verify it works.
   * 3. Android stops the stream
   * 4. Device B disconnects
   * 5. Device A removes Media from Available Contexts
   * 6. Android start stream with MEDIA, verify it will not be started
   */

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  const RawAddress test_address0 = GetTestAddress(0);
  const RawAddress test_address1 = GetTestAddress(1);

  // First earbud connects
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud connects
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  BidirectionalPair<AudioContexts> contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::MEDIA),
      .source = types::AudioContexts()};

  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::MEDIA, contexts, _))
      .Times(1);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Expect two iso channel to be fed with data
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Stop
  StopStreaming(group_id);
  // simulate suspend timeout passed, alarm executing
  fake_osi_alarm_set_on_mloop_.cb(fake_osi_alarm_set_on_mloop_.data);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Device B got disconnected and will not reconnect.
  ON_CALL(mock_gatt_interface_,
          Open(_, test_address1, BTM_BLE_DIRECT_CONNECTION, _))
      .WillByDefault(Return());
  InjectDisconnectedEvent(2, GATT_CONN_TERMINATE_PEER_USER);
  SyncOnMainLoop();

  // Device A changes available context type
  // Inject Supported and available context types
  auto sink_supported_context = types::kLeAudioContextAllRemoteSinkOnly;
  sink_supported_context.unset(LeAudioContextType::MEDIA);
  sink_supported_context.set(LeAudioContextType::UNSPECIFIED);

  auto source_supported_context = types::kLeAudioContextAllRemoteSource;
  source_supported_context.set(LeAudioContextType::UNSPECIFIED);

  InjectSupportedContextTypes(test_address0, 1, sink_supported_context,
                              source_supported_context);
  InjectAvailableContextTypes(test_address0, 1, sink_supported_context,
                              source_supported_context);

  /* Android starts stream. */
  EXPECT_CALL(mock_state_machine_, StartStream(_, _, _, _)).Times(0);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id,
                 AUDIO_SOURCE_INVALID, false, false);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_state_machine_);
}

TEST_F(UnicastTest, StartStreamToUnsupportedContextTypeUsingUnspecified) {
  uint8_t group_size = 2;
  int group_id = 2;

  /* Scenario (Devices A and B called "Remote")
   * 1. Remote  does supports all the context types and make them available
   * 2. Remote removes SoundEffect from the supported and available context
   * types
   * 3. Android start stream with SoundEffects
   * 4. Make sure stream will be started with Unspecified context type
   */

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  const RawAddress test_address0 = GetTestAddress(0);
  const RawAddress test_address1 = GetTestAddress(1);

  // First earbud connects
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud connects
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  // Inject Supported and available context types
  auto sink_supported_context = types::kLeAudioContextAllRemoteSinkOnly;
  sink_supported_context.unset(LeAudioContextType::SOUNDEFFECTS);
  sink_supported_context.set(LeAudioContextType::UNSPECIFIED);

  auto source_supported_context = types::kLeAudioContextAllRemoteSource;
  source_supported_context.set(LeAudioContextType::UNSPECIFIED);

  InjectSupportedContextTypes(test_address0, 1, sink_supported_context,
                              source_supported_context);
  InjectAvailableContextTypes(test_address0, 1, sink_supported_context,
                              source_supported_context);
  InjectSupportedContextTypes(test_address1, 2, sink_supported_context,
                              source_supported_context);
  InjectAvailableContextTypes(test_address1, 2, sink_supported_context,
                              source_supported_context);
  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  BidirectionalPair<AudioContexts> contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::UNSPECIFIED),
      .source = types::AudioContexts(0)};

  EXPECT_CALL(mock_state_machine_,
              StartStream(_, le_audio::types::LeAudioContextType::SOUNDEFFECTS,
                          contexts, _))
      .Times(1);

  StartStreaming(AUDIO_USAGE_ASSISTANCE_SONIFICATION,
                 AUDIO_CONTENT_TYPE_SONIFICATION, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Expect two iso channel to be fed with data
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);
}

TEST_F(UnicastTest,
       StartStreamToUnsupportedContextTypeUnspecifiedNotSupported) {
  uint8_t group_size = 2;
  int group_id = 2;

  /* Scenario (Device A and B called Remote)
   * 1. Remote does supports all the context types and make them available
   * 2. Remote removes SoundEffect from the Available Context Types
   * 3. Remote also removes UNSPECIFIED from the Available Context Types.
   * 4. Android start stream with SoundEffects
   * 5. Make sure stream will be NOT be started
   */

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  const RawAddress test_address0 = GetTestAddress(0);
  const RawAddress test_address1 = GetTestAddress(1);

  // First earbud connects
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud connects
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  // Inject Supported and available context types
  auto sink_supported_context = types::kLeAudioContextAllRemoteSinkOnly;
  sink_supported_context.unset(LeAudioContextType::SOUNDEFFECTS);
  sink_supported_context.set(LeAudioContextType::UNSPECIFIED);

  auto source_supported_context = types::kLeAudioContextAllRemoteSource;
  source_supported_context.set(LeAudioContextType::UNSPECIFIED);

  InjectSupportedContextTypes(test_address0, 1, sink_supported_context,
                              source_supported_context);
  InjectSupportedContextTypes(test_address1, 2, sink_supported_context,
                              source_supported_context);

  auto sink_available_context = sink_supported_context;
  sink_available_context.unset(LeAudioContextType::UNSPECIFIED);

  auto source_available_context = source_supported_context;
  source_available_context.unset(LeAudioContextType::UNSPECIFIED);

  InjectAvailableContextTypes(test_address0, 1, sink_available_context,
                              source_available_context);
  InjectAvailableContextTypes(test_address1, 2, sink_available_context,
                              source_available_context);
  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  BidirectionalPair<AudioContexts> contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::UNSPECIFIED),
      .source = types::AudioContexts()};

  EXPECT_CALL(mock_state_machine_,
              StartStream(_, le_audio::types::LeAudioContextType::SOUNDEFFECTS,
                          contexts, _))
      .Times(0);

  StartStreaming(AUDIO_USAGE_ASSISTANCE_SONIFICATION,
                 AUDIO_CONTENT_TYPE_SONIFICATION, group_id,
                 AUDIO_SOURCE_INVALID, false, false);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();
}

TEST_F(UnicastTest, StartStreamToSupportedContextTypeThenMixUnavailable) {
  uint8_t group_size = 2;
  int group_id = 2;

  /* Scenario (Device A and B called Remote)
   * 1. Remote set does supports all the context types and make them available
   * 2. Abdriud start stream with MEDIA, verify it works.
   * 3. Stream becomes to be mixed with Soundeffect and Media - verify metadata
   *    update
   * 4. Android Stop stream.
   * 5. Remote removes SoundEffect from the supported and available context
   * types
   * 6. Android start stream with MEDIA, verify it works.
   * 7. Stream becomes to be mixed with Soundeffect and Media
   * 8. Make sure metadata updated does not contain unavailable context
   *    note: eventually, Audio framework should not give us unwanted context
   * types
   */

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  const RawAddress test_address0 = GetTestAddress(0);
  const RawAddress test_address1 = GetTestAddress(1);

  // First earbud connects
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud connects
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  BidirectionalPair<AudioContexts> contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::MEDIA),
      .source = types::AudioContexts()};

  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::MEDIA, contexts, _))
      .Times(1);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  auto group = streaming_groups.at(group_id);

  // Expect two iso channel to be fed with data
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  contexts.sink = types::AudioContexts(types::LeAudioContextType::MEDIA |
                                       types::LeAudioContextType::SOUNDEFFECTS);
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::MEDIA, contexts, _))
      .Times(1);

  /* Simulate metadata update, expect upadate , metadata */
  std::vector<struct playback_track_metadata> tracks = {
      {{AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, 0},
       {AUDIO_USAGE_ASSISTANCE_SONIFICATION, AUDIO_CONTENT_TYPE_SONIFICATION,
        0}}};
  UpdateLocalSourceMetadata(tracks);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  /* Stop stream */
  StopStreaming(group_id);
  // simulate suspend timeout passed, alarm executing
  fake_osi_alarm_set_on_mloop_.cb(fake_osi_alarm_set_on_mloop_.data);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Inject Supported and available context types
  auto sink_supported_context = types::kLeAudioContextAllRemoteSinkOnly;
  sink_supported_context.unset(LeAudioContextType::SOUNDEFFECTS);
  sink_supported_context.set(LeAudioContextType::UNSPECIFIED);

  auto source_supported_context = types::kLeAudioContextAllRemoteSource;
  source_supported_context.set(LeAudioContextType::UNSPECIFIED);

  InjectSupportedContextTypes(test_address0, 1, sink_supported_context,
                              source_supported_context);
  InjectAvailableContextTypes(test_address0, 1, sink_supported_context,
                              source_supported_context);
  InjectSupportedContextTypes(test_address1, 2, sink_supported_context,
                              source_supported_context);
  InjectAvailableContextTypes(test_address1, 2, sink_supported_context,
                              source_supported_context);

  // Verify cache has been removed due to available context change
  ASSERT_FALSE(group
                   ->GetCachedCodecConfigurationByDirection(
                       types::LeAudioContextType::MEDIA,
                       le_audio::types::kLeAudioDirectionSink)
                   .has_value());
  /* Start Media again */
  contexts.sink = types::AudioContexts(types::LeAudioContextType::MEDIA);
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::MEDIA, contexts, _))
      .Times(1);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  SyncOnMainLoop();

  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::MEDIA,
                      le_audio::types::kLeAudioDirectionSink)
                  .has_value());

  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Expect two iso channel to be fed with data
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  /* Update metadata, and do not expect new context type*/
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::MEDIA, contexts, _))
      .Times(1);

  /* Simulate metadata update */
  UpdateLocalSourceMetadata(tracks);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
}

TEST_F(UnicastTest, TwoEarbuds2ndDisconnected) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);
  auto group = streaming_groups.at(group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();
  ASSERT_EQ(2, group->NumOfConnected());

  // Expect two iso channels to be fed with data
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Disconnect one device and expect the group to keep on streaming
  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(0);
  auto device = group->GetFirstDevice();
  for (auto& ase : device->ases_) {
    InjectCisDisconnected(group_id, ase.cis_conn_hdl);
  }

  /* Disconnect ACL and do not reconnect. */
  ON_CALL(mock_gatt_interface_,
          Open(_, device->address_, BTM_BLE_DIRECT_CONNECTION, _))
      .WillByDefault(Return());
  EXPECT_CALL(mock_gatt_interface_,
              Open(_, device->address_, BTM_BLE_DIRECT_CONNECTION, false))
      .Times(1);

  // Record NumOfConnected when groupStateMachine_ gets notified about the
  // disconnection
  int num_of_connected = 0;
  ON_CALL(mock_state_machine_, ProcessHciNotifAclDisconnected(_, _))
      .WillByDefault([&num_of_connected](LeAudioDeviceGroup* group,
                                         LeAudioDevice* leAudioDevice) {
        num_of_connected = group->NumOfConnected();
      });

  auto conn_id = device->conn_id_;
  InjectDisconnectedEvent(device->conn_id_, GATT_CONN_TERMINATE_PEER_USER);
  SyncOnMainLoop();

  // Make sure the state machine knows about the disconnected device
  ASSERT_EQ(1, num_of_connected);

  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Expect one channel ISO Data to be sent
  cis_count_out = 1;
  cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  InjectConnectedEvent(device->address_, conn_id);
  SyncOnMainLoop();

  // Expect two iso channels to be fed with data
  cis_count_out = 2;
  cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);
}

TEST_F(UnicastTest, TwoEarbudsStreamingProfileDisconnect) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Expect two iso channels to be fed with data
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(1);

  /* Do not inject OPEN_EVENT by default */
  ON_CALL(mock_gatt_interface_, Open(_, _, _, _))
      .WillByDefault(DoAll(Return()));
  ON_CALL(mock_gatt_interface_, Close(_)).WillByDefault(DoAll(Return()));
  ON_CALL(mock_btm_interface_, AclDisconnectFromHandle(_, _))
      .WillByDefault(DoAll(Return()));

  DisconnectLeAudioNoDisconnectedEvtExpected(test_address0, 1);
  DisconnectLeAudioNoDisconnectedEvtExpected(test_address1, 2);

  EXPECT_CALL(mock_gatt_interface_,
              Open(_, _, BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(2);

  InjectDisconnectedEvent(1);
  InjectDisconnectedEvent(2);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

TEST_F(UnicastTest, TwoEarbudsStreamingProfileDisconnectStreamStopTimeout) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Expect two iso channels to be fed with data
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Expect StopStream to be called before Close or ACL Disconnect is called.
  ON_CALL(mock_state_machine_, StopStream(_))
      .WillByDefault([](LeAudioDeviceGroup* group) {
        /* Stub the process of stopping stream, just set the target state.
         * this simulates issue with stopping the stream
         */
        group->SetTargetState(types::AseState::BTA_LE_AUDIO_ASE_STATE_IDLE);
      });

  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(2);
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(0);
  EXPECT_CALL(mock_btm_interface_, AclDisconnectFromHandle(_, _)).Times(0);

  do_in_main_thread(
      FROM_HERE,
      base::Bind(&LeAudioClient::Disconnect,
                 base::Unretained(LeAudioClient::Get()), test_address0));
  do_in_main_thread(
      FROM_HERE,
      base::Bind(&LeAudioClient::Disconnect,
                 base::Unretained(LeAudioClient::Get()), test_address1));

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
  Mock::VerifyAndClearExpectations(&mock_btm_interface_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  /* Now stream is trying to be stopped and devices are about to be
   * disconnected. Simulate stop stream failure and timeout fired. Make sure
   * code will not try to do recovery connect
   */
  ON_CALL(mock_btm_interface_, AclDisconnectFromHandle(_, _))
      .WillByDefault(DoAll(Return()));
  EXPECT_CALL(mock_gatt_interface_, Close(_)).Times(0);
  EXPECT_CALL(mock_btm_interface_, AclDisconnectFromHandle(_, _)).Times(2);

  auto group = streaming_groups.at(group_id);
  ASSERT_TRUE(group != nullptr);
  ASSERT_TRUE(group->NumOfConnected() > 0);

  state_machine_callbacks_->OnStateTransitionTimeout(group_id);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_btm_interface_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  auto device = group->GetFirstDevice();
  ASSERT_TRUE(device != nullptr);
  ASSERT_NE(device->GetConnectionState(),
            DeviceConnectState::DISCONNECTING_AND_RECOVER);
  device = group->GetNextDevice(device);
  ASSERT_TRUE(device != nullptr);
  ASSERT_NE(device->GetConnectionState(),
            DeviceConnectState::DISCONNECTING_AND_RECOVER);
}

TEST_F(UnicastTest, EarbudsWithStereoSinkMonoSourceSupporting32kHz) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = 0;
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationFrontLeft, default_channel_cnt,
      default_channel_cnt, 0x0024,
      /* source sample freq 32/16khz */ true, /*add_csis*/
      true,                                   /*add_cas*/
      true,                                   /*add_pacs*/
      default_ase_cnt /*add_ascs_cnt*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);

  // LeAudioCodecConfiguration received_af_sink_config;
  const LeAudioCodecConfiguration expected_af_sink_config = {
      .num_channels = 2,
      .sample_rate = bluetooth::audio::le_audio::kSampleRate32000,
      .bits_per_sample = bluetooth::audio::le_audio::kBitsPerSample16,
      .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us,
  };

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_,
              Start(expected_af_sink_config, _, _))
      .Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();
}

TEST_F(UnicastTest, TwoEarbudsWithSourceSupporting32kHz) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = 0;
  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0024,
      /* source sample freq 32/16khz */ true, /*add_csis*/
      true,                                   /*add_cas*/
      true,                                   /*add_pacs*/
      default_ase_cnt /*add_ascs_cnt*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  ConnectLeAudio(test_address0);

  // LeAudioCodecConfiguration received_af_sink_config;
  const LeAudioCodecConfiguration expected_af_sink_config = {
      .num_channels = 2,
      .sample_rate = bluetooth::audio::le_audio::kSampleRate32000,
      .bits_per_sample = bluetooth::audio::le_audio::kBitsPerSample16,
      .data_interval_us = LeAudioCodecConfiguration::kInterval10000Us,
  };

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_,
              Start(expected_af_sink_config, _, _))
      .Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();
}

TEST_F(UnicastTest, MicrophoneAttachToCurrentMediaScenario) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0024, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  // When the local audio source resumes we have no knowledge of recording
  EXPECT_CALL(mock_state_machine_,
              StartStream(_, le_audio::types::LeAudioContextType::MEDIA, _, _))
      .Times(1);

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id,
                 AUDIO_SOURCE_INVALID);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Verify Data transfer on one audio source cis
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // When the local audio sink resumes we should reconfigure
  EXPECT_CALL(
      mock_state_machine_,
      ConfigureStream(_, le_audio::types::LeAudioContextType::LIVE, _, _))
      .Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, ReconfigurationComplete())
      .Times(1);

  // Update metadata on local audio sink
  UpdateLocalSinkMetadata(AUDIO_SOURCE_MIC);

  // Resume on local audio sink
  ASSERT_NE(unicast_sink_hal_cb_, nullptr);
  do_in_main_thread(
      FROM_HERE,
      base::BindOnce(
          [](LeAudioSinkAudioHalClient::Callbacks* cb) { cb->OnAudioResume(); },
          unicast_sink_hal_cb_));

  /* The above will trigger reconfiguration. After that Audio Hal action
   * is needed to restart the stream */
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  LocalAudioSourceResume();
  do_in_main_thread(
      FROM_HERE,
      base::BindOnce(
          [](LeAudioSinkAudioHalClient::Callbacks* cb) { cb->OnAudioResume(); },
          unicast_sink_hal_cb_));
  SyncOnMainLoop();

  // Verify Data transfer on one audio source and sink cis
  cis_count_out = 1;
  cis_count_in = 1;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 60);

  // Stop
  StopStreaming(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Release
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  do_in_main_thread(
      FROM_HERE, base::BindOnce(
                     [](LeAudioClient* client) {
                       client->GroupSetActive(bluetooth::groups::kGroupUnknown);
                     },
                     LeAudioClient::Get()));
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

/* When a certain context is unavailable and not supported we should stream
 * as UNSPECIFIED for the backwards compatibility.
 * Since UNSPECIFIED is available, put the UNSPECIFIED into the metadata instead
 * What we can do now is to keep streaming (and reconfigure if needed for the
 * use case).
 */
TEST_F(UnicastTest, UpdateNotSupportedContextTypeUnspecifiedAvailable) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  available_snk_context_types_ = (types::LeAudioContextType::RINGTONE |
                                  types::LeAudioContextType::CONVERSATIONAL |
                                  types::LeAudioContextType::UNSPECIFIED |
                                  types::LeAudioContextType::MEDIA)
                                     .value();
  supported_snk_context_types_ = available_snk_context_types_;
  available_src_context_types_ = available_snk_context_types_;
  supported_src_context_types_ = available_src_context_types_;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;

  LeAudioClient::Get()->SetInCall(true);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE,
                 AUDIO_CONTENT_TYPE_UNKNOWN, group_id);
  LocalAudioSourceResume();
  LocalAudioSinkResume();

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  LeAudioClient::Get()->SetInCall(false);
  LocalAudioSinkSuspend();

  /* We should use GAME configuration, but do not send the GAME context type, as
   * it is not available on the remote device.
   */
  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(0);
  types::BidirectionalPair<types::AudioContexts> contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::UNSPECIFIED),
      .source = types::AudioContexts(types::LeAudioContextType::UNSPECIFIED)};
  EXPECT_CALL(mock_state_machine_,
              StartStream(_, types::LeAudioContextType::GAME, contexts, _))
      .Times(1);
  UpdateLocalSourceMetadata(AUDIO_USAGE_GAME, AUDIO_CONTENT_TYPE_UNKNOWN,
                            false);
  SyncOnMainLoop();
}

/* Some bidirectional scenarios are triggered by the local sink, local source
 * metadata or the In Call preference callback call. Since each call invalidates
 * previous context source, make sure that getting all of these in a sequence,
 * always results with one bidirectional context, so that the remote device
 * is not confused about our intentions.
 */
TEST_F(UnicastTest, UpdateMultipleBidirContextTypes) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  available_snk_context_types_ =
      (types::LeAudioContextType::CONVERSATIONAL |
       types::LeAudioContextType::GAME | types::LeAudioContextType::LIVE)
          .value();
  supported_snk_context_types_ =
      available_snk_context_types_ |
      types::AudioContexts(types::LeAudioContextType::UNSPECIFIED).value();
  available_src_context_types_ = available_snk_context_types_;
  supported_src_context_types_ =
      available_src_context_types_ |
      types::AudioContexts(types::LeAudioContextType::UNSPECIFIED).value();

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationAnyLeft,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0024, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  // When the local audio sink resumes expect only LIVE context
  types::BidirectionalPair<types::AudioContexts> contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::LIVE),
      .source = types::AudioContexts(types::LeAudioContextType::LIVE)};
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::LIVE, contexts, _))
      .Times(1);

  // 1) Start the recording. Sink resume will trigger the reconfiguration
  // ---------------------------------------------------------------------
  ASSERT_NE(nullptr, unicast_sink_hal_cb_);
  UpdateLocalSinkMetadata(AUDIO_SOURCE_MIC);
  LocalAudioSinkResume();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  /* After the reconfiguration the local Audio Sink HAL has to resume again */
  LocalAudioSourceResume();
  LocalAudioSinkResume();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  // Verify Data transfer on one audio source and sink cis
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 1;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  // Stop
  StopStreaming(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // 2) Now set in call preference to get CONVERSATIONAL into the mix
  // -----------------------------------------------------------------
  LeAudioClient::Get()->SetInCall(true);

  // Verify that we only got CONVERSATIONAL context and no LIVE
  contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::CONVERSATIONAL),
      .source =
          types::AudioContexts(types::LeAudioContextType::CONVERSATIONAL)};
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::CONVERSATIONAL,
                  contexts, _))
      .Times(1);

  // Start with ringtone on local source
  ASSERT_NE(nullptr, unicast_sink_hal_cb_);
  StartStreaming(AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE,
                 AUDIO_CONTENT_TYPE_UNKNOWN, group_id);

  // Resume both directions
  LocalAudioSourceResume();
  LocalAudioSinkResume();

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  // Verify Data transfer on one audio source cis
  cis_count_out = 1;
  cis_count_in = 1;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  // 3) Disable call so we could go to GAME
  // ---------------------------------------
  LeAudioClient::Get()->SetInCall(false);

  /* Start the game on local source - expect no previous sink (LIVE) metadata */
  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(0);
  contexts = {.sink = types::AudioContexts(types::LeAudioContextType::GAME),
              .source = types::AudioContexts(types::LeAudioContextType::GAME)};
  EXPECT_CALL(mock_state_machine_,
              StartStream(_, types::LeAudioContextType::GAME, contexts, _))
      .Times(1);
  UpdateLocalSourceMetadata(AUDIO_USAGE_GAME, AUDIO_CONTENT_TYPE_UNKNOWN,
                            false);

  /* If the above triggers reconfiguration, Audio Hal action is needed to
   * restart the stream.
   */
  LocalAudioSourceResume();
  LocalAudioSinkResume();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  // 4) Stop streaming
  // ------------------
  StopStreaming(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Release
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  do_in_main_thread(
      FROM_HERE, base::BindOnce(
                     [](LeAudioClient* client) {
                       client->GroupSetActive(bluetooth::groups::kGroupUnknown);
                     },
                     LeAudioClient::Get()));
  SyncOnMainLoop();
}

TEST_F(UnicastTest, UpdateDisableLocalAudioSinkOnGame) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  available_snk_context_types_ =
      (types::LeAudioContextType::CONVERSATIONAL |
       types::LeAudioContextType::GAME | types::LeAudioContextType::LIVE)
          .value();
  supported_snk_context_types_ =
      available_snk_context_types_ |
      types::AudioContexts(types::LeAudioContextType::UNSPECIFIED).value();
  available_src_context_types_ = available_snk_context_types_;
  supported_src_context_types_ =
      available_src_context_types_ |
      types::AudioContexts(types::LeAudioContextType::UNSPECIFIED).value();

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationAnyLeft,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0024, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  // Start GAME stream
  types::BidirectionalPair<types::AudioContexts> contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::GAME),
      .source = types::AudioContexts(types::LeAudioContextType::GAME)};
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::GAME, contexts, _))
      .Times(1);

  // 1) Start the recording. Sink resume will trigger the reconfiguration
  // ---------------------------------------------------------------------
  StartStreaming(AUDIO_USAGE_GAME, AUDIO_CONTENT_TYPE_MUSIC, group_id,
                 AUDIO_SOURCE_MIC);

  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  // Verify Data transfer on one audio source and sink cis
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 1;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  SyncOnMainLoop();

  // 2) Now Lets suspend MIC and do not expect reconfiguration
  // -----------------------------------------------------------------

  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(0);
  LocalAudioSinkSuspend();
  // simulate suspend timeout passed, alarm executing
  fake_osi_alarm_set_on_mloop_.cb(fake_osi_alarm_set_on_mloop_.data);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
}

/* Start music when in a call, end the call, continue with music only */
TEST_F(UnicastTest, MusicDuringCallContextTypes) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  available_snk_context_types_ =
      (types::LeAudioContextType::CONVERSATIONAL |
       types::LeAudioContextType::RINGTONE | types::LeAudioContextType::GAME |
       types::LeAudioContextType::MEDIA | types::LeAudioContextType::LIVE)
          .value();
  supported_snk_context_types_ =
      available_snk_context_types_ |
      types::AudioContexts(types::LeAudioContextType::UNSPECIFIED).value();
  available_src_context_types_ = available_snk_context_types_;
  supported_src_context_types_ =
      available_src_context_types_ |
      types::AudioContexts(types::LeAudioContextType::UNSPECIFIED).value();

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationAnyLeft,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0024, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  // 1) Start with the call first
  // -----------------------------
  // CONVERSATIONAL is from In Call preference, and RINGTONE is from metadata
  LeAudioClient::Get()->SetInCall(true);
  types::BidirectionalPair<types::AudioContexts> contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::RINGTONE |
                                   types::LeAudioContextType::CONVERSATIONAL),
      .source =
          types::AudioContexts(types::LeAudioContextType::CONVERSATIONAL)};
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::CONVERSATIONAL,
                  contexts, _))
      .Times(1);
  StartStreaming(AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE,
                 AUDIO_CONTENT_TYPE_UNKNOWN, group_id);
  LocalAudioSourceResume();
  LocalAudioSinkResume();

  // Verify
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  // Verify Data transfer
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 1;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  // 2) Start MEDIA during the call, expect MEDIA only on the remote sink
  contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::CONVERSATIONAL |
                                   types::LeAudioContextType::MEDIA),
      .source =
          types::AudioContexts(types::LeAudioContextType::CONVERSATIONAL)};
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, le_audio::types::LeAudioContextType::CONVERSATIONAL,
                  contexts, _))
      .Times(1);
  UpdateLocalSourceMetadata(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, false);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  // 2) Disable In Call preference but do not suspend the local sink
  // We should stay in CONVERSATIONAL until the local sink suspends
  // ---------------------------------------
  LeAudioClient::Get()->SetInCall(false);

  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(0);
  contexts = {
      .sink = types::AudioContexts(types::LeAudioContextType::MEDIA |
                                   types::LeAudioContextType::CONVERSATIONAL),
      .source =
          types::AudioContexts(types::LeAudioContextType::CONVERSATIONAL)};
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, types::LeAudioContextType::CONVERSATIONAL, contexts, _))
      .Times(1);
  UpdateLocalSourceMetadata(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC,
                            /*reconfigure=*/false);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  // 3) Disable call so we could go back to MEDIA
  // ---------------------------------------
  // Suspend should stop the stream
  EXPECT_CALL(mock_state_machine_, StopStream(_)).Times(1);
  LocalAudioSourceSuspend();
  LocalAudioSinkSuspend();
  // simulate suspend timeout passed, alarm executing
  fake_osi_alarm_set_on_mloop_.cb(fake_osi_alarm_set_on_mloop_.data);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);

  // Restart the stream with MEDIA
  contexts = {.sink = types::AudioContexts(types::LeAudioContextType::MEDIA),
              .source = types::AudioContexts()};
  EXPECT_CALL(mock_state_machine_,
              StartStream(_, types::LeAudioContextType::MEDIA, contexts, _))
      .Times(1);
  UpdateLocalSourceMetadata(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC,
                            /*reconfigure=*/false);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  /* The source needs to resume to reconfigure to MEDIA */
  LocalAudioSourceResume(/*expect_confirm=*/false);
  LocalAudioSourceResume(/*expect_confirm=*/true);
  Mock::VerifyAndClearExpectations(&mock_state_machine_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  // 4) Stop streaming
  // ------------------
  StopStreaming(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Release
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  do_in_main_thread(
      FROM_HERE, base::BindOnce(
                     [](LeAudioClient* client) {
                       client->GroupSetActive(bluetooth::groups::kGroupUnknown);
                     },
                     LeAudioClient::Get()));
  SyncOnMainLoop();
}

/* When a certain context is unavailable but supported we should not stream that
 * context - either stop the stream or eliminate this strim from the mix
 * This could be na IOP issue so continue streaming (and reconfigure if needed
 * for that use case).
 * Since the unavailable context is supported, do not put this context into
 * the metadata, and do not replace it with UNSPECIFIED.
 */
TEST_F(UnicastTest, StartNotAvailableSupportedContextType) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  // EMERGENCYALARM is not available, but supported
  available_snk_context_types_ = (types::LeAudioContextType::RINGTONE |
                                  types::LeAudioContextType::CONVERSATIONAL |
                                  types::LeAudioContextType::UNSPECIFIED |
                                  types::LeAudioContextType::MEDIA)
                                     .value();
  available_src_context_types_ = available_snk_context_types_;
  supported_snk_context_types_ = types::kLeAudioContextAllTypes.value();
  supported_src_context_types_ = (types::kLeAudioContextAllRemoteSource |
                                  types::LeAudioContextType::UNSPECIFIED)
                                     .value();

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Expect configuring to (or staying with) the right configuration but the
  // metadata should not get the EMERGENCYALARM context, nor the UNSPECIFIED
  // Since the initial config is UNSPECIFIED, then even for sonification events
  // we should reconfigure to less generic EMERGENCYALARM scenario
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  types::BidirectionalPair<types::AudioContexts> metadata = {
      .sink = types::AudioContexts(), .source = types::AudioContexts()};
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, types::LeAudioContextType::EMERGENCYALARM, metadata, _))
      .Times(0);

  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_EMERGENCY, AUDIO_CONTENT_TYPE_UNKNOWN, group_id,
                 AUDIO_SOURCE_INVALID, false, false);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

/* When a certain context is unavailable and not supported and the UNSPECIFIED
 * is not available we should stop the stream.
 * For now, stream will not be started in such a case.
 * In future we should be able to eliminate this context from the track mix.
 */
TEST_F(UnicastTest, StartNotAvailableUnsupportedContextTypeUnspecifiedUnavail) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  // EMERGENCYALARM is not available, nor supported
  available_snk_context_types_ = (types::LeAudioContextType::RINGTONE |
                                  types::LeAudioContextType::CONVERSATIONAL |
                                  types::LeAudioContextType::MEDIA)
                                     .value();
  available_src_context_types_ = available_snk_context_types_;
  supported_snk_context_types_ =
      (available_snk_context_types_ | types::LeAudioContextType::UNSPECIFIED)
          .value();
  supported_src_context_types_ =
      (available_src_context_types_ | types::LeAudioContextType::UNSPECIFIED)
          .value();

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Expect configuring to the default config since the EMERGENCYALARM is
  // not on the list of supported contexts and UNSPECIFIED should not be
  // in the metadata as it is unavailable.
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  types::BidirectionalPair<types::AudioContexts> metadata = {
      .sink = types::AudioContexts(), .source = types::AudioContexts()};
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, types::LeAudioContextType::EMERGENCYALARM, metadata, _))
      .Times(0);

  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_EMERGENCY, AUDIO_CONTENT_TYPE_UNKNOWN, group_id,
                 AUDIO_SOURCE_INVALID, false, false);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
}

/* This test verifies if we use UNSPCIFIED context when another context is
 * unavailable and not supported but UNSPCIFIED is in available audio contexts.
 */
TEST_F(UnicastTest, StartNotAvailableUnsupportedContextTypeUnspecifiedAvail) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  // EMERGENCYALARM is not available, nor supported
  available_snk_context_types_ = (types::LeAudioContextType::RINGTONE |
                                  types::LeAudioContextType::CONVERSATIONAL |
                                  types::LeAudioContextType::UNSPECIFIED |
                                  types::LeAudioContextType::MEDIA)
                                     .value();
  available_src_context_types_ = available_snk_context_types_;
  supported_snk_context_types_ = available_snk_context_types_;
  supported_src_context_types_ = available_src_context_types_;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Expect configuring to the default config since the EMERGENCYALARM is
  // not on the list of supported contexts and UNSPECIFIED will be used in
  // the metadata.
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  types::BidirectionalPair<types::AudioContexts> metadata = {
      .sink = types::AudioContexts(types::LeAudioContextType::UNSPECIFIED),
      .source = types::AudioContexts()};
  EXPECT_CALL(
      mock_state_machine_,
      StartStream(_, types::LeAudioContextType::EMERGENCYALARM, metadata, _))
      .Times(1);

  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_EMERGENCY, AUDIO_CONTENT_TYPE_UNKNOWN, group_id);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Verify Data transfer on one audio source cis
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);
}

TEST_F(UnicastTest, NotifyAboutGroupTunrnedIdleEnabled) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  osi_property_set_bool(kNotifyUpperLayerAboutGroupBeingInIdleDuringCall, true);

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;

  LeAudioClient::Get()->SetInCall(true);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE,
                 AUDIO_CONTENT_TYPE_UNKNOWN, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Release

  /* To be called twice
   * 1. GroupStatus::INACTIVE
   * 2. GroupStatus::TURNED_IDLE_DURING_CALL
   */
  EXPECT_CALL(mock_audio_hal_client_callbacks_, OnGroupStatus(group_id, _))
      .Times(2);

  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);

  do_in_main_thread(
      FROM_HERE, base::BindOnce(
                     [](LeAudioClient* client) {
                       client->GroupSetActive(bluetooth::groups::kGroupUnknown);
                     },
                     LeAudioClient::Get()));

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  LeAudioClient::Get()->SetInCall(false);
  osi_property_set_bool(kNotifyUpperLayerAboutGroupBeingInIdleDuringCall,
                        false);
}

TEST_F(UnicastTest, NotifyAboutGroupTunrnedIdleDisabled) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;

  LeAudioClient::Get()->SetInCall(true);

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_NOTIFICATION_TELEPHONY_RINGTONE,
                 AUDIO_CONTENT_TYPE_UNKNOWN, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Release

  /* To be called once only
   * 1. GroupStatus::INACTIVE
   */
  EXPECT_CALL(mock_audio_hal_client_callbacks_, OnGroupStatus(group_id, _))
      .Times(1);

  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);

  do_in_main_thread(
      FROM_HERE, base::BindOnce(
                     [](LeAudioClient* client) {
                       client->GroupSetActive(bluetooth::groups::kGroupUnknown);
                     },
                     LeAudioClient::Get()));

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  LeAudioClient::Get()->SetInCall(false);
}

TEST_F(UnicastTest, HandleDatabaseOutOfSync) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  InjectDisconnectedEvent(1, GATT_CONN_TERMINATE_PEER_USER);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  /* Simulate DATABASE OUT OF SYNC */
  ccc_stored_byte_val_ = 0x01;
  gatt_read_ctp_ccc_status_ = GATT_DATABASE_OUT_OF_SYNC;

  EXPECT_CALL(mock_gatt_queue_, WriteDescriptor(_, _, _, _, _, _)).Times(0);
  ON_CALL(mock_gatt_interface_, ServiceSearchRequest(_, _))
      .WillByDefault(Return());
  EXPECT_CALL(mock_gatt_interface_, ServiceSearchRequest(_, _));

  InjectConnectedEvent(test_address0, 1);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
  Mock::VerifyAndClearExpectations(&mock_gatt_queue_);
}

TEST_F(UnicastTest, TestRemoteDeviceKeepCccValues) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  InjectDisconnectedEvent(1, GATT_CONN_TERMINATE_PEER_USER);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_gatt_queue_);

  /* Simulate remote cache is good */
  ccc_stored_byte_val_ = 0x01;

  EXPECT_CALL(mock_gatt_queue_, WriteDescriptor(_, _, _, _, _, _)).Times(0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);

  InjectConnectedEvent(test_address0, 1);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_queue_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F(UnicastTest, TestRemoteDeviceForgetsCccValues) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004, false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::DISCONNECTED, test_address0))
      .Times(1);
  InjectDisconnectedEvent(1, GATT_CONN_TERMINATE_PEER_USER);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_gatt_queue_);

  /* Simulate remote cache is broken */
  ccc_stored_byte_val_ = 0;
  EXPECT_CALL(mock_gatt_queue_, WriteDescriptor(_, _, _, _, _, _))
      .Times(AtLeast(1));
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);

  InjectConnectedEvent(test_address0, 1);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_queue_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F(UnicastTest, SpeakerStreamingTimeout) {
  const RawAddress test_address0 = GetTestAddress(0);
  int group_id = bluetooth::groups::kGroupUnknown;

  SetSampleDatabaseEarbudsValid(
      1, test_address0, codec_spec_conf::kLeAudioLocationStereo,
      codec_spec_conf::kLeAudioLocationStereo, default_channel_cnt,
      default_channel_cnt, 0x0004,
      /* source sample freq 16khz */ false /*add_csis*/, true /*add_cas*/,
      true /*add_pacs*/, default_ase_cnt /*add_ascs_cnt*/, 1 /*set_size*/,
      0 /*rank*/);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupNodeStatus(test_address0, _, GroupNodeStatus::ADDED))
      .WillOnce(DoAll(SaveArg<1>(&group_id)));

  ConnectLeAudio(test_address0);
  ASSERT_NE(group_id, bluetooth::groups::kGroupUnknown);

  // Start streaming
  uint8_t cis_count_out = 1;
  uint8_t cis_count_in = 0;

  // Audio sessions are started only when device gets active
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on one audio source cis
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920);

  // Do not accept direct connect, but expect it to arrive.
  ON_CALL(mock_gatt_interface_, Open(_, _, BTM_BLE_DIRECT_CONNECTION, _))
      .WillByDefault(Return());

  state_machine_callbacks_->OnStateTransitionTimeout(group_id);
  SyncOnMainLoop();

  /* No assigned cises should remain when transition remains in IDLE state */
  auto group = streaming_groups.at(group_id);
  ASSERT_EQ(0, static_cast<int>(group->cig.cises.size()));
}

TEST_F(UnicastTest, AddMemberToAllowListWhenOneDeviceConnected) {
  uint8_t group_size = 2;
  int group_id = 2;
  int conn_id_dev_0 = 1;
  int conn_id_dev_1 = 2;

  /*Scenario to test
   * 1. Connect Device A and disconnect
   * 2. Connect Device B
   * 3. verify Device B is in the allow list with direct connect.
   */
  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);

  ConnectCsisDevice(test_address0, conn_id_dev_0,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  SyncOnMainLoop();

  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, _))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  InjectDisconnectedEvent(conn_id_dev_0);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);

  /* Do not connect first  device but expect Open will arrive.*/
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, false))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);
  ON_CALL(mock_gatt_interface_,
          Open(_, test_address0, BTM_BLE_DIRECT_CONNECTION, _))
      .WillByDefault(Return());

  ConnectCsisDevice(test_address1, conn_id_dev_1,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

TEST_F(UnicastTest, ResetToDefaultReconnectionMode) {
  uint8_t group_size = 2;
  int group_id = 2;
  int conn_id_dev_0 = 1;
  int conn_id_dev_1 = 2;

  /*Scenario to test
   * 1. Connect Device A and disconnect
   * 2. Connect Device B
   * 3. verify Device B is in the allow list.
   * 4. Disconnect B device
   * 5, Verify A and B device are back in targeted announcement reconnection
   * mode
   */
  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);

  ConnectCsisDevice(test_address0, conn_id_dev_0,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  SyncOnMainLoop();

  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, _))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  InjectDisconnectedEvent(conn_id_dev_0);

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);

  /* Verify first earbud will start doing direct connect first */
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, false))
      .Times(1);
  ON_CALL(mock_gatt_interface_,
          Open(_, test_address0, BTM_BLE_DIRECT_CONNECTION, _))
      .WillByDefault(Return());
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);

  ConnectCsisDevice(test_address1, conn_id_dev_1,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);

  // Disconnect Device B, expect default reconnection mode for Device A.
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, false))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address1,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address1, false))
      .Times(1);

  InjectDisconnectedEvent(conn_id_dev_1, GATT_CONN_TERMINATE_PEER_USER);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
}

TEST_F(UnicastTest, DisconnectAclBeforeGettingReadResponses) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  const RawAddress test_address0 = GetTestAddress(0);
  const RawAddress test_address1 = GetTestAddress(1);

  /* Due to imitated problems with GATT read operations (status != GATT_SUCCESS)
   * a CONNECTED state should not be propagated together with audio location
   */
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(0);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnSinkAudioLocationAvailable(
                  test_address0, codec_spec_conf::kLeAudioLocationFrontLeft))
      .Times(0);

  // First earbud initial connection
  SetSampleDatabaseEarbudsValid(1 /* conn_id */, test_address0,
                                codec_spec_conf::kLeAudioLocationFrontLeft,
                                codec_spec_conf::kLeAudioLocationFrontLeft,
                                default_channel_cnt, default_channel_cnt,
                                0x0004, /* source sample freq 16khz */
                                true,   /*add_csis*/
                                true,   /*add_cas*/
                                true,   /*add_pacs*/
                                true,   /*add_ascs*/
                                group_size, 1 /* rank */, GATT_INTERNAL_ERROR);
  groups[test_address0] = group_id;
  // by default indicate link as encrypted
  ON_CALL(mock_btm_interface_, BTM_IsEncrypted(test_address0, _))
      .WillByDefault(DoAll(Return(true)));

  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0, BTM_BLE_DIRECT_CONNECTION, _))
      .Times(1);
  /* When connected it will got to TA */
  EXPECT_CALL(mock_gatt_interface_, CancelOpen(gatt_if, test_address0, _))
      .Times(1);
  EXPECT_CALL(mock_gatt_interface_,
              Open(gatt_if, test_address0,
                   BTM_BLE_BKG_CONNECT_TARGETED_ANNOUNCEMENTS, _))
      .Times(1);

  do_in_main_thread(
      FROM_HERE,
      base::BindOnce(&LeAudioClient::Connect,
                     base::Unretained(LeAudioClient::Get()), test_address0));

  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_btm_interface_);
  Mock::VerifyAndClearExpectations(&mock_gatt_interface_);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  InjectGroupDeviceAdded(test_address0, group_id);

  // Second earbud initial connection
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnSinkAudioLocationAvailable(
                  test_address1, codec_spec_conf::kLeAudioLocationFrontRight))
      .Times(1);

  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  /* for Target announcements AutoConnect is always there, until
   * device is removed
   */
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, false))
      .Times(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, false))
      .Times(0);

  // Verify grouping information
  std::vector<RawAddress> devs =
      LeAudioClient::Get()->GetGroupDevices(group_id);
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address0), devs.end());
  ASSERT_NE(std::find(devs.begin(), devs.end(), test_address1), devs.end());

  /* Remove default action on the direct connect */
  ON_CALL(mock_gatt_interface_, Open(_, _, BTM_BLE_DIRECT_CONNECTION, _))
      .WillByDefault(Return());

  /* Initiate disconnection with timeout reason, the possible reason why GATT
   * read attribute operation may be not handled
   */
  InjectDisconnectedEvent(1, GATT_CONN_TIMEOUT);
  SyncOnMainLoop();

  /* After reconnection a sink audio location callback with connection state
   * should be propagated.
   */
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnConnectionState(ConnectionState::CONNECTED, test_address0))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnSinkAudioLocationAvailable(
                  test_address0, codec_spec_conf::kLeAudioLocationFrontLeft))
      .Times(1);

  /* Prepare valid GATT status responsing attributes */
  SetSampleDatabaseEarbudsValid(1 /* conn_id */, test_address0,
                                codec_spec_conf::kLeAudioLocationFrontLeft,
                                codec_spec_conf::kLeAudioLocationFrontLeft,
                                default_channel_cnt, default_channel_cnt,
                                0x0004, /* source sample freq 16khz */
                                true,   /*add_csis*/
                                true,   /*add_cas*/
                                true,   /*add_pacs*/
                                true,   /*add_ascs*/
                                group_size, 1 /* rank */);

  /* For background connect, test needs to Inject Connected Event */
  InjectConnectedEvent(test_address0, 1);
  SyncOnMainLoop();
}

TEST_F_WITH_FLAGS(UnicastTest, GroupStreamStatus,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_callback_on_group_stream_status))) {
  int group_id = bluetooth::groups::kGroupUnknown;

  InSequence s;

  /* Check if all states are properly notified */
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::IDLE))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id, GroupStreamStatus::IDLE);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::STREAMING))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::STREAMING);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::IDLE))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::RELEASING);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::STREAMING))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::STREAMING);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::IDLE))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::SUSPENDING);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::STREAMING))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::STREAMING);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::IDLE))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::SUSPENDED);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::STREAMING))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::STREAMING);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::IDLE))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(
      group_id, GroupStreamStatus::CONFIGURED_AUTONOMOUS);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::STREAMING))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::STREAMING);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::IDLE))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(
      group_id, GroupStreamStatus::CONFIGURED_BY_USER);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::STREAMING))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::STREAMING);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::IDLE))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::DESTROYED);

  /* Check if there are no resending of the same state */
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::RELEASING);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::SUSPENDING);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::SUSPENDED);
  state_machine_callbacks_->StatusReportCb(
      group_id, GroupStreamStatus::CONFIGURED_AUTONOMOUS);
  state_machine_callbacks_->StatusReportCb(
      group_id, GroupStreamStatus::CONFIGURED_BY_USER);
  state_machine_callbacks_->StatusReportCb(group_id, GroupStreamStatus::IDLE);

  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::STREAMING))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::STREAMING);
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::STREAMING);
}

TEST_F_WITH_FLAGS(UnicastTest, GroupStreamStatusManyGroups,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_callback_on_group_stream_status))) {
  uint8_t group_size = 2;
  int group_id_1 = 1;
  int group_id_2 = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  ON_CALL(mock_csis_client_module_, GetDesiredSize(_))
      .WillByDefault(Return(group_size));

  // First group - First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id_1, 1 /* rank*/);

  // First group - Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id_1, 2 /* rank*/, true /*connect_through_csis*/);

  // Second group - First earbud
  const RawAddress test_address2 = GetTestAddress(2);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address2, true))
      .Times(1);
  ConnectCsisDevice(test_address2, 3 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id_2, 1 /* rank*/);

  // Second group - Second earbud
  const RawAddress test_address3 = GetTestAddress(3);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address3, true))
      .Times(1);
  ConnectCsisDevice(test_address3, 4 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id_2, 2 /* rank*/, true /*connect_through_csis*/);

  InSequence s;

  // Group 1 IDLE
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id_1, GroupStreamStatus::IDLE))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id_1, GroupStreamStatus::IDLE);

  // Group 2 IDLE
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id_2, GroupStreamStatus::IDLE))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id_2, GroupStreamStatus::IDLE);

  // Group 1 active and start streaming
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id_1, GroupStreamStatus::STREAMING))
      .Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id_1);
  SyncOnMainLoop();
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id_1);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Group 2 active
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id_1, GroupStreamStatus::IDLE))
      .Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id_2);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Group 2 start streaming
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id_2, GroupStreamStatus::STREAMING))
      .Times(1);
  StartStreaming(AUDIO_USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC, group_id_2);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F_WITH_FLAGS(UnicastTest, GroupStreamStatusResendAfterRemove,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_callback_on_group_stream_status))) {
  uint8_t group_size = 2;
  int group_id = 1;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  ON_CALL(mock_csis_client_module_, GetDesiredSize(_))
      .WillByDefault(Return(group_size));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  Mock::VerifyAndClearExpectations(&mock_btif_storage_);

  InSequence s;

  // Activate group, start streaming and immediately stop
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::STREAMING))
      .Times(1);
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::IDLE))
      .Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();
  state_machine_callbacks_->StatusReportCb(group_id,
                                           GroupStreamStatus::STREAMING);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // No resend
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::IDLE))
      .Times(0);
  state_machine_callbacks_->StatusReportCb(group_id, GroupStreamStatus::IDLE);

  // No resend after removing only one device
  /*
   * StopStream will put calls on main_loop so to keep the correct order
   * of operations and to avoid races we put the test command on main_loop as
   * well.
   */
  do_in_main_thread(FROM_HERE, base::BindOnce(
                                   [](LeAudioClient* client,
                                      const RawAddress& test_address0) {
                                     client->RemoveDevice(test_address0);
                                   },
                                   LeAudioClient::Get(), test_address0));
  SyncOnMainLoop();
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::IDLE))
      .Times(0);
  state_machine_callbacks_->StatusReportCb(group_id, GroupStreamStatus::IDLE);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Resend after removing last device
  /*
   * StopStream will put calls on main_loop so to keep the correct order
   * of operations and to avoid races we put the test command on main_loop as
   * well.
   */
  do_in_main_thread(FROM_HERE, base::BindOnce(
                                   [](LeAudioClient* client,
                                      const RawAddress& test_address1) {
                                     client->RemoveDevice(test_address1);
                                   },
                                   LeAudioClient::Get(), test_address1));
  SyncOnMainLoop();
  EXPECT_CALL(mock_audio_hal_client_callbacks_,
              OnGroupStreamStatus(group_id, GroupStreamStatus::IDLE))
      .Times(1);
  state_machine_callbacks_->StatusReportCb(group_id, GroupStreamStatus::IDLE);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F_WITH_FLAGS(UnicastTestHandoverMode,
                  SetSinkMonitorModeWhileUnicastIsActive,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_broadcast_audio_handover_policies))) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on two peer sinks and one source
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 2;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  // Imitate activation of monitor mode
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&LeAudioClient::SetUnicastMonitorMode,
                                   base::Unretained(LeAudioClient::Get()),
                                   le_audio::types::kLeAudioDirectionSink,
                                   true /* enable */));

  auto group = streaming_groups.at(group_id);

  // Stop streaming and expect Service to be informed about straming suspension
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSink,
                                 UnicastMonitorModeStatus::STREAMING_SUSPENDED))
      .Times(1);

  // Stop
  StopStreaming(group_id, true);

  // Check if cache configuration is still present
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSource)
                  .has_value());

  // Release, Sink HAL client should remain in monitor mode
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop()).Times(0);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(0);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  // Re-initialize mock for destroyed hal client
  RegisterSourceHalClientMock();

  // Setting group inactive, shall not change cached configuration
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSource)
                  .has_value());

  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSink,
                                 UnicastMonitorModeStatus::STREAMING_REQUESTED))
      .Times(1);

  // Start streaming to trigger next group going to IDLE state
  LocalAudioSinkResume();

  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Stop streaming and expect Service to be informed about straming suspension
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSink,
                                 UnicastMonitorModeStatus::STREAMING_SUSPENDED))
      .Times(1);

  // Stop
  StopStreaming(group_id, true);

  // Release, Sink HAL client should remain in monitor mode
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop()).Times(0);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(0);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  // De-activate monitoring mode
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&LeAudioClient::SetUnicastMonitorMode,
                                   base::Unretained(LeAudioClient::Get()),
                                   le_audio::types::kLeAudioDirectionSink,
                                   false /* enable */));
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);
}

TEST_F_WITH_FLAGS(UnicastTestHandoverMode,
                  SetSinkMonitorModeWhileUnicastIsInactive,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_broadcast_audio_handover_policies))) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Imitate activation of monitor mode
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&LeAudioClient::SetUnicastMonitorMode,
                                   base::Unretained(LeAudioClient::Get()),
                                   le_audio::types::kLeAudioDirectionSink,
                                   true /* enable */));

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Expect no streaming request on stream resume when group is already active
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSink,
                                 UnicastMonitorModeStatus::STREAMING_REQUESTED))
      .Times(0);

  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on two peer sinks and one source
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 2;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  auto group = streaming_groups.at(group_id);

  // Stop streaming and expect Service to be informed about straming suspension
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSink,
                                 UnicastMonitorModeStatus::STREAMING_SUSPENDED))
      .Times(1);

  // Stop
  StopStreaming(group_id, true);

  // Check if cache configuration is still present
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSource)
                  .has_value());

  // Release, Sink HAL client should remain in monitor mode
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop()).Times(0);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(0);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  // Setting group inactive, shall not change cached configuration
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSource)
                  .has_value());
}

TEST_F_WITH_FLAGS(UnicastTestHandoverMode,
                  ClearSinkMonitorModeWhileUnicastIsActive,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_broadcast_audio_handover_policies))) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Imitate activation of monitor mode
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&LeAudioClient::SetUnicastMonitorMode,
                                   base::Unretained(LeAudioClient::Get()),
                                   le_audio::types::kLeAudioDirectionSink,
                                   true /* enable */));

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);

  // Expect no streaming request on stream resume when group is already active
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSink,
                                 UnicastMonitorModeStatus::STREAMING_REQUESTED))
      .Times(0);

  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on two peer sinks and one source
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 2;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  auto group = streaming_groups.at(group_id);

  // De-activate monitoring mode
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&LeAudioClient::SetUnicastMonitorMode,
                                   base::Unretained(LeAudioClient::Get()),
                                   le_audio::types::kLeAudioDirectionSink,
                                   false /* enable */));

  // Stop
  StopStreaming(group_id, true);
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  // Check if cache configuration is still present
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSource)
                  .has_value());

  // Release of sink and source hals due to de-activating monitor mode
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  // Setting group inactive, shall not change cached configuration
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSource)
                  .has_value());
}

TEST_F_WITH_FLAGS(UnicastTestHandoverMode,
                  SetAndClearSinkMonitorModeWhileUnicastIsInactive,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_broadcast_audio_handover_policies))) {
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(0);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(0);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop()).Times(0);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);

  // Imitate activation of monitor mode
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&LeAudioClient::SetUnicastMonitorMode,
                                   base::Unretained(LeAudioClient::Get()),
                                   le_audio::types::kLeAudioDirectionSink,
                                   true /* enable */));
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&LeAudioClient::SetUnicastMonitorMode,
                                   base::Unretained(LeAudioClient::Get()),
                                   le_audio::types::kLeAudioDirectionSink,
                                   true /* enable */));

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  SyncOnMainLoop();
}

TEST_F_WITH_FLAGS(UnicastTestHandoverMode,
                  SetSourceMonitorModeWhileUnicastIsInactive,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_broadcast_audio_handover_policies))) {
  /* Enabling monitor mode for source while group is not active should result in
   * sending STREAMING_SUSPENDED notification.
   */
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSource,
                                 UnicastMonitorModeStatus::STREAMING_SUSPENDED))
      .Times(1);

  // Imitate activation of monitor mode
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&LeAudioClient::SetUnicastMonitorMode,
                                   base::Unretained(LeAudioClient::Get()),
                                   le_audio::types::kLeAudioDirectionSource,
                                   true /* enable */));
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F_WITH_FLAGS(UnicastTestHandoverMode,
                  SetSourceMonitorModeWhileUnicastIsNotStreaming,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_broadcast_audio_handover_policies))) {
  int group_id = 2;

  LeAudioClient::Get()->GroupSetActive(group_id);

  /* Enabling monitor mode for source while group is not active should result in
   * sending STREAMING_SUSPENDED notification.
   */
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSource,
                                 UnicastMonitorModeStatus::STREAMING_SUSPENDED))
      .Times(1);

  // Imitate activation of monitor mode
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&LeAudioClient::SetUnicastMonitorMode,
                                   base::Unretained(LeAudioClient::Get()),
                                   le_audio::types::kLeAudioDirectionSource,
                                   true /* enable */));
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
}

TEST_F_WITH_FLAGS(UnicastTestHandoverMode,
                  SetSourceMonitorModeWhileUnicastIsActive,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(
                      TEST_BT, leaudio_broadcast_audio_handover_policies))) {
  uint8_t group_size = 2;
  int group_id = 2;

  // Report working CSIS
  ON_CALL(mock_csis_client_module_, IsCsisClientRunning())
      .WillByDefault(Return(true));

  // First earbud
  const RawAddress test_address0 = GetTestAddress(0);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address0, true))
      .Times(1);
  ConnectCsisDevice(test_address0, 1 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontLeft,
                    codec_spec_conf::kLeAudioLocationFrontLeft, group_size,
                    group_id, 1 /* rank*/);

  // Second earbud
  const RawAddress test_address1 = GetTestAddress(1);
  EXPECT_CALL(mock_btif_storage_, AddLeaudioAutoconnect(test_address1, true))
      .Times(1);
  ConnectCsisDevice(test_address1, 2 /*conn_id*/,
                    codec_spec_conf::kLeAudioLocationFrontRight,
                    codec_spec_conf::kLeAudioLocationFrontRight, group_size,
                    group_id, 2 /* rank*/, true /*connect_through_csis*/);

  ON_CALL(mock_csis_client_module_, GetDesiredSize(group_id))
      .WillByDefault(Invoke([&](int group_id) { return 2; }));

  // Start streaming
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);
  SyncOnMainLoop();

  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);

  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);
  SyncOnMainLoop();

  // Verify Data transfer on two peer sinks and one source
  uint8_t cis_count_out = 2;
  uint8_t cis_count_in = 2;
  TestAudioDataTransfer(group_id, cis_count_out, cis_count_in, 1920, 40);

  /* Enabling monitor mode for source while stream is active should result in
   * sending STREAMING notification.
   */
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSource,
                                 UnicastMonitorModeStatus::STREAMING))
      .Times(1);

  // Imitate activation of monitor mode
  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&LeAudioClient::SetUnicastMonitorMode,
                                   base::Unretained(LeAudioClient::Get()),
                                   le_audio::types::kLeAudioDirectionSource,
                                   true /* enable */));
  SyncOnMainLoop();
  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);

  auto group = streaming_groups.at(group_id);

  // Stop streaming and expect Service to be informed about straming suspension
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSource,
                                 UnicastMonitorModeStatus::STREAMING_SUSPENDED))
      .Times(1);

  // Stop
  StopStreaming(group_id, true);

  // Check if cache configuration is still present
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSource)
                  .has_value());

  // Both Sink and Source HAL clients should be stopped
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  // Re-initialize mock for destroyed hal client
  RegisterSourceHalClientMock();
  RegisterSinkHalClientMock();

  // Setting group inactive, shall not change cached configuration
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSink)
                  .has_value());
  ASSERT_TRUE(group
                  ->GetCachedCodecConfigurationByDirection(
                      types::LeAudioContextType::CONVERSATIONAL,
                      le_audio::types::kLeAudioDirectionSource)
                  .has_value());

  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSource,
                                 UnicastMonitorModeStatus::STREAMING_REQUESTED))
      .Times(1);

  EXPECT_CALL(*mock_le_audio_source_hal_client_, Start(_, _, _)).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Start(_, _, _)).Times(1);
  LeAudioClient::Get()->GroupSetActive(group_id);

  // Start streaming to trigger next group going to IDLE state
  StartStreaming(AUDIO_USAGE_VOICE_COMMUNICATION, AUDIO_CONTENT_TYPE_SPEECH,
                 group_id);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  // Stop streaming and expect Service to be informed about straming suspension
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSource,
                                 UnicastMonitorModeStatus::STREAMING_SUSPENDED))
      .Times(1);

  // Stop
  StopStreaming(group_id, true);

  // Both Sink and Source HAL clients should be stopped
  EXPECT_CALL(*mock_le_audio_source_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_source_hal_client_, OnDestroyed()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, Stop()).Times(1);
  EXPECT_CALL(*mock_le_audio_sink_hal_client_, OnDestroyed()).Times(1);
  LeAudioClient::Get()->GroupSetActive(bluetooth::groups::kGroupUnknown);
  SyncOnMainLoop();

  Mock::VerifyAndClearExpectations(&mock_audio_hal_client_callbacks_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_source_hal_client_);
  Mock::VerifyAndClearExpectations(&mock_le_audio_sink_hal_client_);

  // De-activate monitoring mode
  EXPECT_CALL(
      mock_audio_hal_client_callbacks_,
      OnUnicastMonitorModeStatus(le_audio::types::kLeAudioDirectionSource,
                                 UnicastMonitorModeStatus::STREAMING_SUSPENDED))
      .Times(0);

  do_in_main_thread(FROM_HERE,
                    base::BindOnce(&LeAudioClient::SetUnicastMonitorMode,
                                   base::Unretained(LeAudioClient::Get()),
                                   le_audio::types::kLeAudioDirectionSink,
                                   false /* enable */));
}
}  // namespace le_audio
