/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "codec_manager.h"

#include "audio_hal_client/audio_hal_client.h"
#include "broadcaster/broadcaster_types.h"
#include "device/include/controller.h"
#include "le_audio_set_configuration_provider.h"
#include "le_audio_utils.h"
#include "os/log.h"
#include "osi/include/properties.h"
#include "stack/include/hcimsgs.h"

namespace {

using bluetooth::hci::iso_manager::kIsoDataPathHci;
using bluetooth::hci::iso_manager::kIsoDataPathPlatformDefault;
using bluetooth::legacy::hci::GetInterface;
using le_audio::CodecManager;
using le_audio::types::CodecLocation;

using bluetooth::le_audio::btle_audio_codec_config_t;
using bluetooth::le_audio::btle_audio_codec_index_t;
using le_audio::AudioSetConfigurationProvider;
using le_audio::set_configurations::AudioSetConfiguration;
using le_audio::set_configurations::AudioSetConfigurations;
using le_audio::set_configurations::SetConfiguration;

typedef struct offloader_stream_maps {
  std::vector<le_audio::stream_map_info> streams_map_target;
  std::vector<le_audio::stream_map_info> streams_map_current;
  bool has_changed;
  bool is_initial;
} offloader_stream_maps_t;
}  // namespace

namespace le_audio {
template <>
offloader_stream_maps_t& types::BidirectionalPair<offloader_stream_maps_t>::get(
    uint8_t direction) {
  ASSERT_LOG(direction < types::kLeAudioDirectionBoth,
             "Unsupported complex direction. Reference to a single complex"
             " direction value is not supported.");
  return (direction == types::kLeAudioDirectionSink) ? sink : source;
}

// The mapping for sampling rate, frame duration, and the QoS config
static std::unordered_map<
    int, std::unordered_map<int, le_audio::broadcaster::BroadcastQosConfig>>
    bcast_high_reliability_qos = {
        {LeAudioCodecConfiguration::kSampleRate16000,
         {{LeAudioCodecConfiguration::kInterval7500Us,
           le_audio::broadcaster::qos_config_4_45},
          {LeAudioCodecConfiguration::kInterval10000Us,
           le_audio::broadcaster::qos_config_4_60}}},
        {LeAudioCodecConfiguration::kSampleRate24000,
         {{LeAudioCodecConfiguration::kInterval7500Us,
           le_audio::broadcaster::qos_config_4_45},
          {LeAudioCodecConfiguration::kInterval10000Us,
           le_audio::broadcaster::qos_config_4_60}}},
        {LeAudioCodecConfiguration::kSampleRate32000,
         {{LeAudioCodecConfiguration::kInterval7500Us,
           le_audio::broadcaster::qos_config_4_45},
          {LeAudioCodecConfiguration::kInterval10000Us,
           le_audio::broadcaster::qos_config_4_60}}},
        {LeAudioCodecConfiguration::kSampleRate48000,
         {{LeAudioCodecConfiguration::kInterval7500Us,
           le_audio::broadcaster::qos_config_4_50},
          {LeAudioCodecConfiguration::kInterval10000Us,
           le_audio::broadcaster::qos_config_4_65}}}};

struct codec_manager_impl {
 public:
  codec_manager_impl() {
    offload_enable_ = osi_property_get_bool(
                          "ro.bluetooth.leaudio_offload.supported", false) &&
                      !osi_property_get_bool(
                          "persist.bluetooth.leaudio_offload.disabled", true);
    if (offload_enable_ == false) {
      LOG_INFO("offload disabled");
      return;
    }

    if (!LeAudioHalVerifier::SupportsLeAudioHardwareOffload()) {
      LOG_WARN("HAL not support hardware offload");
      return;
    }

    if (!controller_get_interface()->supports_configure_data_path()) {
      LOG_WARN("Controller does not support config data path command");
      return;
    }

    LOG_INFO("LeAudioCodecManagerImpl: configure_data_path for encode");
    GetInterface().ConfigureDataPath(hci_data_direction_t::HOST_TO_CONTROLLER,
                                     kIsoDataPathPlatformDefault, {});
    GetInterface().ConfigureDataPath(hci_data_direction_t::CONTROLLER_TO_HOST,
                                     kIsoDataPathPlatformDefault, {});
    SetCodecLocation(CodecLocation::ADSP);
  }
  void start(
      const std::vector<btle_audio_codec_config_t>& offloading_preference) {
    le_audio::AudioSetConfigurationProvider::Initialize(GetCodecLocation());
    UpdateOffloadCapability(offloading_preference);
  }
  ~codec_manager_impl() {
    if (GetCodecLocation() != CodecLocation::HOST) {
      GetInterface().ConfigureDataPath(hci_data_direction_t::HOST_TO_CONTROLLER,
                                       kIsoDataPathHci, {});
      GetInterface().ConfigureDataPath(hci_data_direction_t::CONTROLLER_TO_HOST,
                                       kIsoDataPathHci, {});
    }
    le_audio::AudioSetConfigurationProvider::Cleanup();
  }
  CodecLocation GetCodecLocation(void) const { return codec_location_; }

  bool IsOffloadDualBiDirSwbSupported(void) const {
    return offload_dual_bidirection_swb_supported_;
  }

  std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
  GetLocalAudioOutputCodecCapa() {
    return codec_output_capa;
  }

  std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
  GetLocalAudioInputCodecCapa() {
    return codec_input_capa;
  }

  void UpdateActiveAudioConfig(
      const types::BidirectionalPair<stream_parameters>& stream_params,
      types::BidirectionalPair<uint16_t> delays_ms,
      std::function<void(const offload_config& config, uint8_t direction)>
          update_receiver) {
    if (GetCodecLocation() != le_audio::types::CodecLocation::ADSP) {
      return;
    }

    for (auto direction : {le_audio::types::kLeAudioDirectionSink,
                           le_audio::types::kLeAudioDirectionSource}) {
      auto& stream_map = offloader_stream_maps.get(direction);
      if (!stream_map.has_changed && !stream_map.is_initial) {
        continue;
      }
      if (stream_params.get(direction).stream_locations.empty()) {
        continue;
      }

      le_audio::offload_config unicast_cfg = {
          .stream_map = (stream_map.is_initial ||
                         LeAudioHalVerifier::SupportsStreamActiveApi())
                            ? stream_map.streams_map_target
                            : stream_map.streams_map_current,
          // TODO: set the default value 16 for now, would change it if we
          // support mode bits_per_sample
          .bits_per_sample = 16,
          .sampling_rate = stream_params.get(direction).sample_frequency_hz,
          .frame_duration = stream_params.get(direction).frame_duration_us,
          .octets_per_frame =
              stream_params.get(direction).octets_per_codec_frame,
          .blocks_per_sdu =
              stream_params.get(direction).codec_frames_blocks_per_sdu,
          .peer_delay_ms = delays_ms.get(direction),
      };
      update_receiver(unicast_cfg, direction);
      stream_map.is_initial = false;
    }
  }

  const AudioSetConfigurations* GetOffloadCodecConfig(
      types::LeAudioContextType ctx_type) {
    return context_type_offload_config_map_.count(ctx_type)
               ? &context_type_offload_config_map_[ctx_type]
               : nullptr;
  }

  void UpdateSupportedBroadcastConfig(
      const std::vector<AudioSetConfiguration>& adsp_capabilities) {
    LOG_INFO("UpdateSupportedBroadcastConfig");

    for (const auto& adsp_audio_set_conf : adsp_capabilities) {
      if (adsp_audio_set_conf.confs.size() != 1 ||
          adsp_audio_set_conf.confs[0].device_cnt != 0) {
        continue;
      }
      auto& adsp_config = adsp_audio_set_conf.confs[0];

      const types::LeAudioCoreCodecConfig core_config =
          adsp_config.codec.params.GetAsCoreCodecConfig();
      le_audio::broadcast_offload_config broadcast_config;
      broadcast_config.stream_map.resize(
          core_config.GetChannelCountPerIsoStream());
      broadcast_config.bits_per_sample =
          LeAudioCodecConfiguration::kBitsPerSample16;
      broadcast_config.sampling_rate = core_config.GetSamplingFrequencyHz();
      broadcast_config.frame_duration = core_config.GetFrameDurationUs();
      broadcast_config.octets_per_frame = *(core_config.octets_per_codec_frame);
      broadcast_config.blocks_per_sdu = 1;

      int sample_rate = broadcast_config.sampling_rate;
      int frame_duration = broadcast_config.frame_duration;

      if (bcast_high_reliability_qos.find(sample_rate) !=
              bcast_high_reliability_qos.end() &&
          bcast_high_reliability_qos[sample_rate].find(frame_duration) !=
              bcast_high_reliability_qos[sample_rate].end()) {
        auto qos = bcast_high_reliability_qos[sample_rate].at(frame_duration);
        broadcast_config.retransmission_number = qos.getRetransmissionNumber();
        broadcast_config.max_transport_latency = qos.getMaxTransportLatency();
        supported_broadcast_config.push_back(broadcast_config);
      } else {
        LOG_ERROR(
            "Cannot find the correspoding QoS config for the sampling_rate: "
            "%d, frame_duration: %d",
            sample_rate, frame_duration);
      }

      LOG_INFO("broadcast_config sampling_rate: %d",
               broadcast_config.sampling_rate);
    }
  }

  const broadcast_offload_config* GetBroadcastOffloadConfig() {
    if (supported_broadcast_config.empty()) {
      LOG_ERROR("There is no valid broadcast offload config");
      return nullptr;
    }

    LOG_INFO(
        "stream_map.size(): %zu, sampling_rate: %d, frame_duration(us): %d, "
        "octets_per_frame: %d, blocks_per_sdu %d, "
        "retransmission_number: %d, max_transport_latency: %d",
        supported_broadcast_config[0].stream_map.size(),
        supported_broadcast_config[0].sampling_rate,
        supported_broadcast_config[0].frame_duration,
        supported_broadcast_config[0].octets_per_frame,
        (int)supported_broadcast_config[0].blocks_per_sdu,
        (int)supported_broadcast_config[0].retransmission_number,
        supported_broadcast_config[0].max_transport_latency);

    return &supported_broadcast_config[0];
  }

  void UpdateBroadcastConnHandle(
      const std::vector<uint16_t>& conn_handle,
      std::function<void(const ::le_audio::broadcast_offload_config& config)>
          update_receiver) {
    auto broadcast_config = supported_broadcast_config[0];
    LOG_ASSERT(conn_handle.size() == broadcast_config.stream_map.size());

    if (broadcast_config.stream_map.size() ==
        LeAudioCodecConfiguration::kChannelNumberStereo) {
      broadcast_config.stream_map[0] = std::pair<uint16_t, uint32_t>{
          conn_handle[0], codec_spec_conf::kLeAudioLocationFrontLeft};
      broadcast_config.stream_map[1] = std::pair<uint16_t, uint32_t>{
          conn_handle[1], codec_spec_conf::kLeAudioLocationFrontRight};
    } else if (broadcast_config.stream_map.size() ==
               LeAudioCodecConfiguration::kChannelNumberMono) {
      broadcast_config.stream_map[0] = std::pair<uint16_t, uint32_t>{
          conn_handle[0], codec_spec_conf::kLeAudioLocationFrontCenter};
    }

    update_receiver(broadcast_config);
  }

  void ClearCisConfiguration(uint8_t direction) {
    if (GetCodecLocation() != le_audio::types::CodecLocation::ADSP) {
      return;
    }

    auto& stream_map = offloader_stream_maps.get(direction);
    stream_map.streams_map_target.clear();
    stream_map.streams_map_current.clear();
  }

  static uint32_t AdjustAllocationForOffloader(uint32_t allocation) {
    if ((allocation & codec_spec_conf::kLeAudioLocationAnyLeft) &&
        (allocation & codec_spec_conf::kLeAudioLocationAnyRight)) {
      return codec_spec_conf::kLeAudioLocationStereo;
    }
    if (allocation & codec_spec_conf::kLeAudioLocationAnyLeft) {
      return codec_spec_conf::kLeAudioLocationFrontLeft;
    }
    if (allocation & codec_spec_conf::kLeAudioLocationAnyRight) {
      return codec_spec_conf::kLeAudioLocationFrontRight;
    }
    return 0;
  }

  void UpdateCisConfiguration(const std::vector<struct types::cis>& cises,
                              const stream_parameters& stream_params,
                              uint8_t direction) {
    if (GetCodecLocation() != le_audio::types::CodecLocation::ADSP) {
      return;
    }

    auto available_allocations =
        AdjustAllocationForOffloader(stream_params.audio_channel_allocation);
    if (available_allocations == 0) {
      LOG_ERROR("There is no CIS connected");
      return;
    }

    auto& stream_map = offloader_stream_maps.get(direction);
    if (stream_map.streams_map_target.empty()) {
      stream_map.is_initial = true;
    } else if (stream_map.is_initial ||
               LeAudioHalVerifier::SupportsStreamActiveApi()) {
      /* As multiple CISes phone call case, the target_allocation already have
       * the previous data, but the is_initial flag not be cleared. We need to
       * clear here to avoid make duplicated target allocation stream map. */
      stream_map.streams_map_target.clear();
    }

    stream_map.streams_map_current.clear();
    stream_map.has_changed = true;
    bool all_cises_connected =
        (available_allocations == codec_spec_conf::kLeAudioLocationStereo);

    /* If all the cises are connected as stream started, reset changed_flag that
     * the bt stack wouldn't send another audio configuration for the connection
     * status. */
    if (stream_map.is_initial && all_cises_connected) {
      stream_map.has_changed = false;
    }

    const std::string tag = types::BidirectionalPair<std::string>(
                                {.sink = "Sink", .source = "Source"})
                                .get(direction);

    constexpr types::BidirectionalPair<types::CisType> cis_types = {
        .sink = types::CisType::CIS_TYPE_UNIDIRECTIONAL_SINK,
        .source = types::CisType::CIS_TYPE_UNIDIRECTIONAL_SOURCE};
    auto cis_type = cis_types.get(direction);

    for (auto const& cis_entry : cises) {
      if ((cis_entry.type == types::CisType::CIS_TYPE_BIDIRECTIONAL ||
           cis_entry.type == cis_type) &&
          cis_entry.conn_handle != 0) {
        uint32_t target_allocation = 0;
        uint32_t current_allocation = 0;
        bool is_active = false;
        for (const auto& s : stream_params.stream_locations) {
          if (s.first == cis_entry.conn_handle) {
            is_active = true;
            target_allocation = AdjustAllocationForOffloader(s.second);
            current_allocation = target_allocation;
            if (!all_cises_connected) {
              /* Tell offloader to mix on this CIS.*/
              current_allocation = codec_spec_conf::kLeAudioLocationStereo;
            }
            break;
          }
        }

        if (target_allocation == 0) {
          /* Take missing allocation for that one .*/
          target_allocation =
              codec_spec_conf::kLeAudioLocationStereo & ~available_allocations;
        }

        LOG_INFO(
            "%s: Cis handle 0x%04x, target allocation  0x%08x, current "
            "allocation 0x%08x, active: %d",
            tag.c_str(), cis_entry.conn_handle, target_allocation,
            current_allocation, is_active);

        if (stream_map.is_initial ||
            LeAudioHalVerifier::SupportsStreamActiveApi()) {
          stream_map.streams_map_target.emplace_back(stream_map_info(
              cis_entry.conn_handle, target_allocation, is_active));
        }
        stream_map.streams_map_current.emplace_back(stream_map_info(
            cis_entry.conn_handle, current_allocation, is_active));
      }
    }
  }

 private:
  void SetCodecLocation(CodecLocation location) {
    if (offload_enable_ == false) return;
    codec_location_ = location;
  }

  bool IsLc3ConfigMatched(
      const set_configurations::CodecConfigSetting& target_config,
      const set_configurations::CodecConfigSetting& adsp_config) {
    if (adsp_config.id.coding_format != types::kLeAudioCodingFormatLC3 ||
        target_config.id.coding_format != types::kLeAudioCodingFormatLC3) {
      return false;
    }

    const types::LeAudioCoreCodecConfig adsp_lc3_config =
        adsp_config.params.GetAsCoreCodecConfig();
    const types::LeAudioCoreCodecConfig target_lc3_config =
        target_config.params.GetAsCoreCodecConfig();

    if (adsp_lc3_config.sampling_frequency !=
            target_lc3_config.sampling_frequency ||
        adsp_lc3_config.frame_duration != target_lc3_config.frame_duration ||
        adsp_config.GetChannelCountPerIsoStream() !=
            target_config.GetChannelCountPerIsoStream() ||
        adsp_lc3_config.octets_per_codec_frame !=
            target_lc3_config.octets_per_codec_frame) {
      return false;
    }

    return true;
  }

  bool IsSetConfigurationMatched(const SetConfiguration& software_set_config,
                                 const SetConfiguration& adsp_set_config) {
    // Skip the check of stategry and ase_cnt due to ADSP doesn't have the info
    return (
        software_set_config.direction == adsp_set_config.direction &&
        software_set_config.device_cnt == adsp_set_config.device_cnt &&
        IsLc3ConfigMatched(software_set_config.codec, adsp_set_config.codec));
  }

  bool IsAudioSetConfigurationMatched(
      const AudioSetConfiguration* software_audio_set_conf,
      std::unordered_set<uint8_t>& offload_preference_set,
      const std::vector<AudioSetConfiguration>& adsp_capabilities) {
    if (software_audio_set_conf->confs.empty()) {
      return false;
    }

    std::unordered_map<uint8_t, const SetConfiguration&>
        software_set_conf_direction_map;

    for (auto& software_set_conf : software_audio_set_conf->confs) {
      // Checks offload preference supports the codec
      if (offload_preference_set.find(
              software_set_conf.codec.id.coding_format) ==
          offload_preference_set.end()) {
        return false;
      }
      software_set_conf_direction_map.emplace(software_set_conf.direction,
                                              software_set_conf);
    }

    // Checks any of offload config matches the input audio set config
    for (const auto& adsp_audio_set_conf : adsp_capabilities) {
      if (adsp_audio_set_conf.confs.size() !=
          software_audio_set_conf->confs.size()) {
        continue;
      }

      size_t match_cnt = 0;

      for (auto& adsp_set_conf : adsp_audio_set_conf.confs) {
        auto it = software_set_conf_direction_map.find(adsp_set_conf.direction);

        if (it == software_set_conf_direction_map.end()) {
          continue;
        }

        if (IsSetConfigurationMatched(it->second, adsp_set_conf)) {
          match_cnt++;
        }
      }

      if (match_cnt == software_set_conf_direction_map.size()) {
        return true;
      }
    }

    return false;
  }

  std::string getStrategyString(types::LeAudioConfigurationStrategy strategy) {
    switch (strategy) {
      case types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE:
        return "MONO_ONE_CIS_PER_DEVICE";
      case types::LeAudioConfigurationStrategy::STEREO_TWO_CISES_PER_DEVICE:
        return "STEREO_TWO_CISES_PER_DEVICE";
      case types::LeAudioConfigurationStrategy::STEREO_ONE_CIS_PER_DEVICE:
        return "STEREO_ONE_CIS_PER_DEVICE";
      default:
        return "RFU";
    }
  }

  uint8_t sampleFreqToBluetoothSigBitMask(int sample_freq) {
    switch (sample_freq) {
      case 8000:
        return le_audio::codec_spec_caps::kLeAudioSamplingFreq8000Hz;
      case 16000:
        return le_audio::codec_spec_caps::kLeAudioSamplingFreq16000Hz;
      case 24000:
        return le_audio::codec_spec_caps::kLeAudioSamplingFreq24000Hz;
      case 32000:
        return le_audio::codec_spec_caps::kLeAudioSamplingFreq32000Hz;
      case 44100:
        return le_audio::codec_spec_caps::kLeAudioSamplingFreq44100Hz;
      case 48000:
        return le_audio::codec_spec_caps::kLeAudioSamplingFreq48000Hz;
    }
    return le_audio::codec_spec_caps::kLeAudioSamplingFreq8000Hz;
  }

  void storeLocalCapa(
      std::vector<::le_audio::set_configurations::AudioSetConfiguration>&
          adsp_capabilities,
      const std::vector<btle_audio_codec_config_t>& offload_preference_set) {
    LOG_DEBUG(" Print adsp_capabilities:");

    for (auto adsp : adsp_capabilities) {
      LOG_DEBUG("%s, number of confs %d", adsp.name.c_str(),
                (int)(adsp.confs.size()));
      for (auto conf : adsp.confs) {
        LOG_DEBUG(
            "codecId: %d dir: %s, dev_cnt: %d ase_cnt: %d, strategy: %s, "
            "sample_freq: %d, interval %d, channel_cnt: %d",
            conf.codec.id.coding_format,
            (conf.direction == types::kLeAudioDirectionSink ? "sink"
                                                            : "source"),
            conf.device_cnt, conf.ase_cnt,
            getStrategyString(conf.strategy).c_str(),
            conf.codec.GetSamplingFrequencyHz(), conf.codec.GetDataIntervalUs(),
            conf.codec.GetChannelCountPerIsoStream());

        /* TODO: How to get bits_per_sample ? */
        btle_audio_codec_config_t capa_to_add = {
            .sample_rate = utils::translateToBtLeAudioCodecConfigSampleRate(
                conf.codec.GetSamplingFrequencyHz()),
            .bits_per_sample =
                utils::translateToBtLeAudioCodecConfigBitPerSample(16),
            .channel_count = utils::translateToBtLeAudioCodecConfigChannelCount(
                conf.codec.GetChannelCountPerIsoStream()),
            .frame_duration =
                utils::translateToBtLeAudioCodecConfigFrameDuration(
                    conf.codec.GetDataIntervalUs()),
        };

        if (conf.direction == types::kLeAudioDirectionSink) {
          LOG_DEBUG("Adding output capa %d",
                    static_cast<int>(codec_output_capa.size()));
          codec_output_capa.push_back(capa_to_add);
        } else {
          LOG_DEBUG("Adding input capa %d",
                    static_cast<int>(codec_input_capa.size()));
          codec_input_capa.push_back(capa_to_add);
        }
      }
    }

    LOG_DEBUG("Output capa: %d, Input capa: %d",
              static_cast<int>(codec_output_capa.size()),
              static_cast<int>(codec_input_capa.size()));

    LOG_DEBUG(" Print offload_preference_set: %d ",
              (int)(offload_preference_set.size()));

    int i = 0;
    for (auto set : offload_preference_set) {
      LOG_DEBUG("set %d, %s ", i++, set.ToString().c_str());
    }
  }

  void UpdateOffloadCapability(
      const std::vector<btle_audio_codec_config_t>& offloading_preference) {
    LOG(INFO) << __func__;
    std::unordered_set<uint8_t> offload_preference_set;

    if (AudioSetConfigurationProvider::Get() == nullptr) {
      LOG(ERROR) << __func__
                 << " Audio set configuration provider is not available.";
      return;
    }

    std::vector<::le_audio::set_configurations::AudioSetConfiguration>
        adsp_capabilities =
            ::bluetooth::audio::le_audio::get_offload_capabilities();

    storeLocalCapa(adsp_capabilities, offloading_preference);

    for (auto codec : offloading_preference) {
      auto it = btle_audio_codec_type_map_.find(codec.codec_type);

      if (it != btle_audio_codec_type_map_.end()) {
        offload_preference_set.insert(it->second);
      }
    }

    for (types::LeAudioContextType ctx_type :
         types::kLeAudioContextAllTypesArray) {
      // Gets the software supported context type and the corresponding config
      // priority
      const AudioSetConfigurations* software_audio_set_confs =
          AudioSetConfigurationProvider::Get()->GetConfigurations(ctx_type);

      for (const auto& software_audio_set_conf : *software_audio_set_confs) {
        if (IsAudioSetConfigurationMatched(software_audio_set_conf,
                                           offload_preference_set,
                                           adsp_capabilities)) {
          LOG(INFO) << "Offload supported conf, context type: " << (int)ctx_type
                    << ", settings -> " << software_audio_set_conf->name;
          if (AudioSetConfigurationProvider::Get()
                  ->CheckConfigurationIsDualBiDirSwb(
                      *software_audio_set_conf)) {
            offload_dual_bidirection_swb_supported_ = true;
          }
          context_type_offload_config_map_[ctx_type].push_back(
              software_audio_set_conf);
        }
      }
    }

    UpdateSupportedBroadcastConfig(adsp_capabilities);
  }

  CodecLocation codec_location_ = CodecLocation::HOST;
  bool offload_enable_ = false;
  bool offload_dual_bidirection_swb_supported_ = false;
  types::BidirectionalPair<offloader_stream_maps_t> offloader_stream_maps;
  std::vector<le_audio::broadcast_offload_config> supported_broadcast_config;
  std::unordered_map<types::LeAudioContextType, AudioSetConfigurations>
      context_type_offload_config_map_;
  std::unordered_map<btle_audio_codec_index_t, uint8_t>
      btle_audio_codec_type_map_ = {
          {::bluetooth::le_audio::LE_AUDIO_CODEC_INDEX_SOURCE_LC3,
           types::kLeAudioCodingFormatLC3}};

  std::vector<btle_audio_codec_config_t> codec_input_capa = {};
  std::vector<btle_audio_codec_config_t> codec_output_capa = {};
};  // namespace le_audio

struct CodecManager::impl {
  impl(const CodecManager& codec_manager) : codec_manager_(codec_manager) {}

  void Start(
      const std::vector<btle_audio_codec_config_t>& offloading_preference) {
    LOG_ASSERT(!codec_manager_impl_);
    codec_manager_impl_ = std::make_unique<codec_manager_impl>();
    codec_manager_impl_->start(offloading_preference);
  }

  void Stop() {
    LOG_ASSERT(codec_manager_impl_);
    codec_manager_impl_.reset();
  }

  bool IsRunning() { return codec_manager_impl_ ? true : false; }

  const CodecManager& codec_manager_;
  std::unique_ptr<codec_manager_impl> codec_manager_impl_;
};

CodecManager::CodecManager() : pimpl_(std::make_unique<impl>(*this)) {}

void CodecManager::Start(
    const std::vector<btle_audio_codec_config_t>& offloading_preference) {
  if (!pimpl_->IsRunning()) pimpl_->Start(offloading_preference);
}

void CodecManager::Stop() {
  if (pimpl_->IsRunning()) pimpl_->Stop();
}

types::CodecLocation CodecManager::GetCodecLocation(void) const {
  if (!pimpl_->IsRunning()) {
    return CodecLocation::HOST;
  }

  return pimpl_->codec_manager_impl_->GetCodecLocation();
}

bool CodecManager::IsOffloadDualBiDirSwbSupported(void) const {
  if (!pimpl_->IsRunning()) {
    return false;
  }

  return pimpl_->codec_manager_impl_->IsOffloadDualBiDirSwbSupported();
}

std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
CodecManager::GetLocalAudioOutputCodecCapa() {
  if (pimpl_->IsRunning()) {
    return pimpl_->codec_manager_impl_->GetLocalAudioOutputCodecCapa();
  }

  std::vector<bluetooth::le_audio::btle_audio_codec_config_t> empty{};
  return empty;
}

std::vector<bluetooth::le_audio::btle_audio_codec_config_t>
CodecManager::GetLocalAudioInputCodecCapa() {
  if (pimpl_->IsRunning()) {
    return pimpl_->codec_manager_impl_->GetLocalAudioOutputCodecCapa();
  }
  std::vector<bluetooth::le_audio::btle_audio_codec_config_t> empty{};
  return empty;
}

void CodecManager::UpdateActiveAudioConfig(
    const types::BidirectionalPair<stream_parameters>& stream_params,
    types::BidirectionalPair<uint16_t> delays_ms,
    std::function<void(const offload_config& config, uint8_t direction)>
        update_receiver) {
  if (pimpl_->IsRunning())
    pimpl_->codec_manager_impl_->UpdateActiveAudioConfig(
        stream_params, delays_ms, update_receiver);
}

const AudioSetConfigurations* CodecManager::GetOffloadCodecConfig(
    types::LeAudioContextType ctx_type) {
  if (pimpl_->IsRunning()) {
    return pimpl_->codec_manager_impl_->GetOffloadCodecConfig(ctx_type);
  }

  return nullptr;
}

const ::le_audio::broadcast_offload_config*
CodecManager::GetBroadcastOffloadConfig() {
  if (pimpl_->IsRunning()) {
    return pimpl_->codec_manager_impl_->GetBroadcastOffloadConfig();
  }

  return nullptr;
}

void CodecManager::UpdateBroadcastConnHandle(
    const std::vector<uint16_t>& conn_handle,
    std::function<void(const ::le_audio::broadcast_offload_config& config)>
        update_receiver) {
  if (pimpl_->IsRunning()) {
    return pimpl_->codec_manager_impl_->UpdateBroadcastConnHandle(
        conn_handle, update_receiver);
  }
}

void CodecManager::UpdateCisConfiguration(
    const std::vector<struct types::cis>& cises,
    const stream_parameters& stream_params, uint8_t direction) {
  if (pimpl_->IsRunning()) {
    return pimpl_->codec_manager_impl_->UpdateCisConfiguration(
        cises, stream_params, direction);
  }
}

void CodecManager::ClearCisConfiguration(uint8_t direction) {
  if (pimpl_->IsRunning()) {
    return pimpl_->codec_manager_impl_->ClearCisConfiguration(direction);
  }
}

}  // namespace le_audio
