/*
 *  Copyright (c) 2022 The Android Open Source Project
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
 */

#include <base/logging.h>

#include <mutex>
#include <string>
#include <string_view>

#include "audio_hal_client/audio_hal_client.h"
#include "audio_set_configurations_generated.h"
#include "audio_set_scenarios_generated.h"
#include "codec_manager.h"
#include "flatbuffers/idl.h"
#include "flatbuffers/util.h"
#include "le_audio_set_configuration_provider.h"
#include "os/log.h"
#include "osi/include/osi.h"
#include "osi/include/properties.h"

using le_audio::set_configurations::AudioSetConfiguration;
using le_audio::set_configurations::AudioSetConfigurations;
using le_audio::set_configurations::CodecConfigSetting;
using le_audio::set_configurations::LeAudioCodecIdLc3;
using le_audio::set_configurations::QosConfigSetting;
using le_audio::set_configurations::SetConfiguration;
using le_audio::types::LeAudioContextType;

namespace le_audio {
using ::le_audio::CodecManager;

#ifdef __ANDROID__
static const std::vector<
    std::pair<const char* /*schema*/, const char* /*content*/>>
    kLeAudioSetConfigs = {
        {"/apex/com.android.btservices/etc/bluetooth/le_audio/"
         "audio_set_configurations.bfbs",
         "/apex/com.android.btservices/etc/bluetooth/le_audio/"
         "audio_set_configurations.json"}};
static const std::vector<
    std::pair<const char* /*schema*/, const char* /*content*/>>
    kLeAudioSetScenarios = {{"/apex/com.android.btservices/etc/bluetooth/"
                             "le_audio/audio_set_scenarios.bfbs",
                             "/apex/com.android.btservices/etc/bluetooth/"
                             "le_audio/audio_set_scenarios.json"}};
#elif defined(TARGET_FLOSS)
static const std::vector<
    std::pair<const char* /*schema*/, const char* /*content*/>>
    kLeAudioSetConfigs = {
        {"/etc/bluetooth/le_audio/audio_set_configurations.bfbs",
         "/etc/bluetooth/le_audio/audio_set_configurations.json"}};
static const std::vector<
    std::pair<const char* /*schema*/, const char* /*content*/>>
    kLeAudioSetScenarios = {
        {"/etc/bluetooth/le_audio/audio_set_scenarios.bfbs",
         "/etc/bluetooth/le_audio/audio_set_scenarios.json"}};
#else
static const std::vector<
    std::pair<const char* /*schema*/, const char* /*content*/>>
    kLeAudioSetConfigs = {
        {"audio_set_configurations.bfbs", "audio_set_configurations.json"}};
static const std::vector<
    std::pair<const char* /*schema*/, const char* /*content*/>>
    kLeAudioSetScenarios = {
        {"audio_set_scenarios.bfbs", "audio_set_scenarios.json"}};
#endif

/** Provides a set configurations for the given context type */
struct AudioSetConfigurationProviderJson {
  static constexpr auto kDefaultScenario = "Media";

  AudioSetConfigurationProviderJson(types::CodecLocation location) {
    dual_bidirection_swb_supported_ = osi_property_get_bool(
        "bluetooth.leaudio.dual_bidirection_swb.supported", false);
    ASSERT_LOG(LoadContent(kLeAudioSetConfigs, kLeAudioSetScenarios, location),
               ": Unable to load le audio set configuration files.");
  }

  /* Use the same scenario configurations for different contexts to avoid
   * internal reconfiguration and handover that produces time gap. When using
   * the same scenario for different contexts, quality and configuration remains
   * the same while changing to same scenario based context type.
   */
  static auto ScenarioToContextTypes(const std::string& scenario) {
    static const std::multimap<std::string,
                               ::le_audio::types::LeAudioContextType>
        scenarios = {
            {"Media", types::LeAudioContextType::ALERTS},
            {"Media", types::LeAudioContextType::INSTRUCTIONAL},
            {"Media", types::LeAudioContextType::NOTIFICATIONS},
            {"Media", types::LeAudioContextType::EMERGENCYALARM},
            {"Media", types::LeAudioContextType::UNSPECIFIED},
            {"Media", types::LeAudioContextType::MEDIA},
            {"Conversational", types::LeAudioContextType::RINGTONE},
            {"Conversational", types::LeAudioContextType::CONVERSATIONAL},
            {"Live", types::LeAudioContextType::LIVE},
            {"Game", types::LeAudioContextType::GAME},
            {"VoiceAssistants", types::LeAudioContextType::VOICEASSISTANTS},
        };
    return scenarios.equal_range(scenario);
  }

  static std::string ContextTypeToScenario(
      ::le_audio::types::LeAudioContextType context_type) {
    switch (context_type) {
      case types::LeAudioContextType::ALERTS:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::INSTRUCTIONAL:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::NOTIFICATIONS:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::EMERGENCYALARM:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::UNSPECIFIED:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::SOUNDEFFECTS:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::MEDIA:
        return "Media";
      case types::LeAudioContextType::RINGTONE:
        FALLTHROUGH_INTENDED;
      case types::LeAudioContextType::CONVERSATIONAL:
        return "Conversational";
      case types::LeAudioContextType::LIVE:
        return "Live";
      case types::LeAudioContextType::GAME:
        return "Game";
      case types::LeAudioContextType::VOICEASSISTANTS:
        return "VoiceAssistants";
      default:
        return kDefaultScenario;
    }
  }

  const AudioSetConfigurations* GetConfigurationsByContextType(
      LeAudioContextType context_type) const {
    if (context_configurations_.count(context_type))
      return &context_configurations_.at(context_type);

    LOG_WARN(": No predefined scenario for the context %d was found.",
             (int)context_type);

    auto [it_begin, it_end] = ScenarioToContextTypes(kDefaultScenario);
    if (it_begin != it_end) {
      LOG_WARN(": Using '%s' scenario by default.", kDefaultScenario);
      return &context_configurations_.at(it_begin->second);
    }

    LOG_ERROR(
        ": No valid configuration for the default '%s' scenario, or no audio "
        "set configurations loaded at all.",
        kDefaultScenario);
    return nullptr;
  };

  bool IsDualBiDirSwbSupported(void) const {
    return dual_bidirection_swb_supported_;
  }

 private:
  /* Codec configurations */
  std::map<std::string, const AudioSetConfiguration> configurations_;

  /* Maps of context types to a set of configuration structs */
  std::map<::le_audio::types::LeAudioContextType, AudioSetConfigurations>
      context_configurations_;

  /* property to check if bidirectional sampling frequency >= 32k dual mic is
   * supported or not
   */
  bool dual_bidirection_swb_supported_;

  static const bluetooth::le_audio::CodecSpecificConfiguration*
  LookupCodecSpecificParam(
      const flatbuffers::Vector<
          flatbuffers::Offset<bluetooth::le_audio::CodecSpecificConfiguration>>*
          flat_codec_specific_params,
      bluetooth::le_audio::CodecSpecificLtvGenericTypes type) {
    auto it = std::find_if(
        flat_codec_specific_params->cbegin(),
        flat_codec_specific_params->cend(),
        [&type](const auto& csc) { return (csc->type() == type); });
    return (it != flat_codec_specific_params->cend()) ? *it : nullptr;
  }

  static CodecConfigSetting CodecConfigSettingFromFlat(
      const bluetooth::le_audio::CodecId* flat_codec_id,
      const flatbuffers::Vector<
          flatbuffers::Offset<bluetooth::le_audio::CodecSpecificConfiguration>>*
          flat_codec_specific_params) {
    CodecConfigSetting codec;

    /* Cache the le_audio::types::CodecId type value */
    codec.id = types::LeAudioCodecId({
        .coding_format = flat_codec_id->coding_format(),
        .vendor_company_id = flat_codec_id->vendor_company_id(),
        .vendor_codec_id = flat_codec_id->vendor_codec_id(),
    });

    /* Cache all the codec specific parameters */
    for (auto const& param : *flat_codec_specific_params) {
      auto const value = param->compound_value()->value();
      codec.params.Add(
          param->type(),
          std::vector<uint8_t>(value->data(), value->data() + value->size()));
    }

    auto param = codec.params.Find(
        bluetooth::le_audio::
            CodecSpecificLtvGenericTypes_SUPPORTED_AUDIO_CHANNEL_ALLOCATION);
    if (param) {
      auto ptr = param->data();
      uint32_t audio_channel_allocation;

      ASSERT_LOG((param->size() == sizeof(audio_channel_allocation)),
                 "invalid channel allocation value %d", (int)param->size());
      STREAM_TO_UINT32(audio_channel_allocation, ptr);
      codec.channel_count_per_iso_stream =
          std::bitset<32>(audio_channel_allocation).count();
    } else {
      // TODO: Add support for channel count in the json configurations file,
      //       keeping support for the allocations for compatibility.
    }

    return codec;
  }

  SetConfiguration SetConfigurationFromFlatSubconfig(
      const bluetooth::le_audio::AudioSetSubConfiguration* flat_subconfig,
      QosConfigSetting qos, types::CodecLocation location) {
    auto strategy_int =
        static_cast<int>(flat_subconfig->configuration_strategy());

    bool valid_strategy =
        (strategy_int >=
         (int)types::LeAudioConfigurationStrategy::MONO_ONE_CIS_PER_DEVICE) &&
        strategy_int < (int)types::LeAudioConfigurationStrategy::RFU;

    types::LeAudioConfigurationStrategy strategy =
        valid_strategy
            ? static_cast<types::LeAudioConfigurationStrategy>(strategy_int)
            : types::LeAudioConfigurationStrategy::RFU;

    auto config = SetConfiguration(
        flat_subconfig->direction(), flat_subconfig->device_cnt(),
        flat_subconfig->ase_cnt(),
        CodecConfigSettingFromFlat(flat_subconfig->codec_id(),
                                   flat_subconfig->codec_configuration()),
        qos, strategy);

    // Note that these parameters are set here since for now, we are using the
    // common configuration source for all the codec locations.
    switch (location) {
      case types::CodecLocation::ADSP:
        config.is_codec_in_controller = false;
        config.data_path_id =
            bluetooth::hci::iso_manager::kIsoDataPathPlatformDefault;
        break;
      case types::CodecLocation::HOST:
        config.is_codec_in_controller = false;
        config.data_path_id = bluetooth::hci::iso_manager::kIsoDataPathHci;
        break;
      case types::CodecLocation::CONTROLLER:
        config.is_codec_in_controller = true;
        config.data_path_id =
            bluetooth::hci::iso_manager::kIsoDataPathPlatformDefault;
        break;
    }
    return config;
  }

  static uint8_t ValidateTargetLatency(int flat_target_latency) {
    auto target_latency_int = static_cast<int>(flat_target_latency);

    bool valid_target_latency =
        (target_latency_int >= (int)types::kTargetLatencyLower &&
         target_latency_int <= (int)types::kTargetLatencyHigherReliability);

    return valid_target_latency
               ? static_cast<uint8_t>(target_latency_int)
               : types::kTargetLatencyBalancedLatencyReliability;
  }

  AudioSetConfiguration AudioSetConfigurationFromFlat(
      const bluetooth::le_audio::AudioSetConfiguration* flat_cfg,
      std::vector<const bluetooth::le_audio::CodecConfiguration*>* codec_cfgs,
      std::vector<const bluetooth::le_audio::QosConfiguration*>* qos_cfgs,
      types::CodecLocation location) {
    ASSERT_LOG(flat_cfg != nullptr, "flat_cfg cannot be null");
    std::string codec_config_key = flat_cfg->codec_config_name()->str();
    auto* qos_config_key_array = flat_cfg->qos_config_name();

    constexpr std::string_view default_qos = "QoS_Config_Balanced_Reliability";

    std::string qos_sink_key(default_qos);
    std::string qos_source_key(default_qos);

    /* We expect maximum two QoS settings. First for Sink and second for Source
     */
    if (qos_config_key_array->size() > 0) {
      qos_sink_key = qos_config_key_array->Get(0)->str();
      if (qos_config_key_array->size() > 1) {
        qos_source_key = qos_config_key_array->Get(1)->str();
      } else {
        qos_source_key = qos_sink_key;
      }
    }

    LOG_INFO("Audio set config %s: codec config %s, qos_sink %s, qos_source %s",
             flat_cfg->name()->c_str(), codec_config_key.c_str(),
             qos_sink_key.c_str(), qos_source_key.c_str());

    const bluetooth::le_audio::QosConfiguration* qos_sink_cfg = nullptr;
    for (auto i = qos_cfgs->begin(); i != qos_cfgs->end(); ++i) {
      if ((*i)->name()->str() == qos_sink_key) {
        qos_sink_cfg = *i;
        break;
      }
    }

    const bluetooth::le_audio::QosConfiguration* qos_source_cfg = nullptr;
    for (auto i = qos_cfgs->begin(); i != qos_cfgs->end(); ++i) {
      if ((*i)->name()->str() == qos_source_key) {
        qos_source_cfg = *i;
        break;
      }
    }

    QosConfigSetting qos_sink;
    if (qos_sink_cfg != nullptr) {
      qos_sink.target_latency =
          ValidateTargetLatency(qos_sink_cfg->target_latency());
      qos_sink.retransmission_number = qos_sink_cfg->retransmission_number();
      qos_sink.max_transport_latency = qos_sink_cfg->max_transport_latency();
    } else {
      LOG_ERROR("No qos config matching key %s found", qos_sink_key.c_str());
    }

    QosConfigSetting qos_source;
    if (qos_source_cfg != nullptr) {
      qos_source.target_latency =
          ValidateTargetLatency(qos_source_cfg->target_latency());
      qos_source.retransmission_number =
          qos_source_cfg->retransmission_number();
      qos_source.max_transport_latency =
          qos_source_cfg->max_transport_latency();
    } else {
      LOG_ERROR("No qos config matching key %s found", qos_source_key.c_str());
    }

    const bluetooth::le_audio::CodecConfiguration* codec_cfg = nullptr;
    for (auto i = codec_cfgs->begin(); i != codec_cfgs->end(); ++i) {
      if ((*i)->name()->str() == codec_config_key) {
        codec_cfg = *i;
        break;
      }
    }

    std::vector<SetConfiguration> subconfigs;

    bool dual_dev_one_chan_stereo_sink_swb = false;
    bool dual_dev_one_chan_stereo_source_swb = false;
    bool single_dev_one_chan_stereo_sink_swb = false;
    bool single_dev_one_chan_stereo_source_swb = false;

    if (codec_cfg != nullptr && codec_cfg->subconfigurations()) {
      /* Load subconfigurations */
      for (auto subconfig : *codec_cfg->subconfigurations()) {
        if (subconfig->direction() == le_audio::types::kLeAudioDirectionSink) {
          processSubconfig(
              *subconfig, qos_sink, dual_dev_one_chan_stereo_sink_swb,
              single_dev_one_chan_stereo_sink_swb, subconfigs, location);
        } else {
          processSubconfig(
              *subconfig, qos_source, dual_dev_one_chan_stereo_source_swb,
              single_dev_one_chan_stereo_source_swb, subconfigs, location);
        }
      }
    } else {
      if (codec_cfg == nullptr) {
        LOG_ERROR("No codec config matching key %s found",
                  codec_config_key.c_str());
      } else {
        LOG_ERROR("Configuration '%s' has no valid subconfigurations.",
                  flat_cfg->name()->c_str());
      }
    }

    if (!dual_bidirection_swb_supported_) {
      if ((dual_dev_one_chan_stereo_sink_swb &&
           dual_dev_one_chan_stereo_source_swb) ||
          (single_dev_one_chan_stereo_sink_swb &&
           single_dev_one_chan_stereo_source_swb)) {
        return AudioSetConfiguration({flat_cfg->name()->c_str(), {}});
      }
    }

    return AudioSetConfiguration({flat_cfg->name()->c_str(), subconfigs});
  }

  void processSubconfig(
      const bluetooth::le_audio::AudioSetSubConfiguration& subconfig,
      const QosConfigSetting& qos_setting, bool& dual_dev_one_chan_stereo_swb,
      bool& single_dev_one_chan_stereo_swb,
      std::vector<SetConfiguration>& subconfigs,
      types::CodecLocation location) {
    subconfigs.push_back(
        SetConfigurationFromFlatSubconfig(&subconfig, qos_setting, location));

    if (subconfigs.back().codec.GetSamplingFrequencyHz() <
        le_audio::LeAudioCodecConfiguration::kSampleRate32000) {
      return;
    }

    if (subconfigs.back().device_cnt == 2 && subconfigs.back().ase_cnt == 2) {
      dual_dev_one_chan_stereo_swb |= true;
    }
    if (subconfigs.back().device_cnt == 1 && subconfigs.back().ase_cnt == 2) {
      single_dev_one_chan_stereo_swb |= true;
    }
  }

  bool LoadConfigurationsFromFiles(const char* schema_file,
                                   const char* content_file,
                                   types::CodecLocation location) {
    flatbuffers::Parser configurations_parser_;
    std::string configurations_schema_binary_content;
    bool ok = flatbuffers::LoadFile(schema_file, true,
                                    &configurations_schema_binary_content);
    if (!ok) return ok;

    /* Load the binary schema */
    ok = configurations_parser_.Deserialize(
        (uint8_t*)configurations_schema_binary_content.c_str(),
        configurations_schema_binary_content.length());
    if (!ok) return ok;

    /* Load the content from JSON */
    std::string configurations_json_content;
    ok = flatbuffers::LoadFile(content_file, false,
                               &configurations_json_content);
    if (!ok) return ok;

    /* Parse */
    ok = configurations_parser_.Parse(configurations_json_content.c_str());
    if (!ok) return ok;

    /* Import from flatbuffers */
    auto configurations_root = bluetooth::le_audio::GetAudioSetConfigurations(
        configurations_parser_.builder_.GetBufferPointer());
    if (!configurations_root) return false;

    auto flat_qos_configs = configurations_root->qos_configurations();
    if ((flat_qos_configs == nullptr) || (flat_qos_configs->size() == 0))
      return false;

    LOG_DEBUG(": Updating %d qos config entries.", flat_qos_configs->size());
    std::vector<const bluetooth::le_audio::QosConfiguration*> qos_cfgs;
    for (auto const& flat_qos_cfg : *flat_qos_configs) {
      qos_cfgs.push_back(flat_qos_cfg);
    }

    auto flat_codec_configs = configurations_root->codec_configurations();
    if ((flat_codec_configs == nullptr) || (flat_codec_configs->size() == 0))
      return false;

    LOG_DEBUG(": Updating %d codec config entries.",
              flat_codec_configs->size());
    std::vector<const bluetooth::le_audio::CodecConfiguration*> codec_cfgs;
    for (auto const& flat_codec_cfg : *flat_codec_configs) {
      codec_cfgs.push_back(flat_codec_cfg);
    }

    auto flat_configs = configurations_root->configurations();
    if ((flat_configs == nullptr) || (flat_configs->size() == 0)) return false;

    LOG_DEBUG(": Updating %d config entries.", flat_configs->size());
    for (auto const& flat_cfg : *flat_configs) {
      auto configuration = AudioSetConfigurationFromFlat(flat_cfg, &codec_cfgs,
                                                         &qos_cfgs, location);
      if (!configuration.confs.empty()) {
        configurations_.insert({flat_cfg->name()->str(), configuration});
      }
    }

    return true;
  }

  AudioSetConfigurations AudioSetConfigurationsFromFlatScenario(
      const bluetooth::le_audio::AudioSetScenario* const flat_scenario) {
    AudioSetConfigurations items;
    if (!flat_scenario->configurations()) return items;

    for (auto config_name : *flat_scenario->configurations()) {
      if (configurations_.count(config_name->str()) == 0) continue;

      auto& cfg = configurations_.at(config_name->str());
      items.push_back(&cfg);
    }

    return items;
  }

  bool LoadScenariosFromFiles(const char* schema_file,
                              const char* content_file) {
    flatbuffers::Parser scenarios_parser_;
    std::string scenarios_schema_binary_content;
    bool ok = flatbuffers::LoadFile(schema_file, true,
                                    &scenarios_schema_binary_content);
    if (!ok) return ok;

    /* Load the binary schema */
    ok = scenarios_parser_.Deserialize(
        (uint8_t*)scenarios_schema_binary_content.c_str(),
        scenarios_schema_binary_content.length());
    if (!ok) return ok;

    /* Load the content from JSON */
    std::string scenarios_json_content;
    ok = flatbuffers::LoadFile(content_file, false, &scenarios_json_content);
    if (!ok) return ok;

    /* Parse */
    ok = scenarios_parser_.Parse(scenarios_json_content.c_str());
    if (!ok) return ok;

    /* Import from flatbuffers */
    auto scenarios_root = bluetooth::le_audio::GetAudioSetScenarios(
        scenarios_parser_.builder_.GetBufferPointer());
    if (!scenarios_root) return false;

    auto flat_scenarios = scenarios_root->scenarios();
    if ((flat_scenarios == nullptr) || (flat_scenarios->size() == 0))
      return false;

    LOG_DEBUG(": Updating %d scenarios.", flat_scenarios->size());
    for (auto const& scenario : *flat_scenarios) {
      LOG_DEBUG("Scenario %s configs:", scenario->name()->c_str());
      auto configs = AudioSetConfigurationsFromFlatScenario(scenario);
      for (auto& config : configs) {
        LOG_DEBUG("\t\t Audio set config: %s", config->name.c_str());
      }

      auto [it_begin, it_end] =
          ScenarioToContextTypes(scenario->name()->c_str());
      for (auto it = it_begin; it != it_end; ++it) {
        context_configurations_.insert_or_assign(
            it->second, AudioSetConfigurationsFromFlatScenario(scenario));
      }
    }

    return true;
  }

  bool LoadContent(
      std::vector<std::pair<const char* /*schema*/, const char* /*content*/>>
          config_files,
      std::vector<std::pair<const char* /*schema*/, const char* /*content*/>>
          scenario_files,
      types::CodecLocation location) {
    for (auto [schema, content] : config_files) {
      if (!LoadConfigurationsFromFiles(schema, content, location)) return false;
    }

    for (auto [schema, content] : scenario_files) {
      if (!LoadScenariosFromFiles(schema, content)) return false;
    }
    return true;
  }
};

struct AudioSetConfigurationProvider::impl {
  impl(const AudioSetConfigurationProvider& config_provider)
      : config_provider_(config_provider) {}

  void Initialize(types::CodecLocation location) {
    ASSERT_LOG(!config_provider_impl_, " Config provider not available.");
    config_provider_impl_ =
        std::make_unique<AudioSetConfigurationProviderJson>(location);
  }

  void Cleanup() {
    ASSERT_LOG(config_provider_impl_, " Config provider not available.");
    config_provider_impl_.reset();
  }

  bool IsRunning() { return config_provider_impl_ ? true : false; }

  void Dump(int fd) {
    std::stringstream stream;

    for (LeAudioContextType context : types::kLeAudioContextAllTypesArray) {
      auto confs = Get()->GetConfigurations(context);
      stream << "\n  === Configurations for context type: " << (int)context
             << ", num: " << (confs == nullptr ? 0 : confs->size()) << " \n";
      if (confs && confs->size() > 0) {
        for (const auto& conf : *confs) {
          stream << "  name: " << conf->name << " \n";
          for (const auto& ent : conf->confs) {
            stream << "    direction: "
                   << (ent.direction == types::kLeAudioDirectionSink
                           ? "Sink (speaker)\n"
                           : "Source (mic)\n")
                   << "     number of devices: " << +ent.device_cnt << " \n"
                   << "     number of ASEs: " << +ent.ase_cnt << " \n"
                   << "     strategy: " << (int)(ent.strategy) << " \n"
                   << "     qos->target latency: " << +ent.qos.target_latency
                   << " \n"
                   << "     qos->retransmission_number: "
                   << +ent.qos.retransmission_number << " \n"
                   << "     qos->max_transport_latency: "
                   << +ent.qos.max_transport_latency << " \n"
                   << "     channel count per ISO stream: "
                   << +ent.codec.GetChannelCountPerIsoStream() << "\n";
          }
        }
      }
    }
    dprintf(fd, "%s", stream.str().c_str());
  }

  const AudioSetConfigurationProvider& config_provider_;
  std::unique_ptr<AudioSetConfigurationProviderJson> config_provider_impl_;
};

static std::unique_ptr<AudioSetConfigurationProvider> config_provider;
std::mutex instance_mutex;

AudioSetConfigurationProvider::AudioSetConfigurationProvider()
    : pimpl_(std::make_unique<AudioSetConfigurationProvider::impl>(*this)) {}

void AudioSetConfigurationProvider::Initialize(types::CodecLocation location) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  if (!config_provider)
    config_provider = std::make_unique<AudioSetConfigurationProvider>();

  if (!config_provider->pimpl_->IsRunning())
    config_provider->pimpl_->Initialize(location);
}

void AudioSetConfigurationProvider::DebugDump(int fd) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  if (!config_provider || !config_provider->pimpl_->IsRunning()) {
    dprintf(
        fd,
        "\n AudioSetConfigurationProvider not initialized: config provider: "
        "%d, pimpl: %d \n",
        config_provider != nullptr,
        (config_provider == nullptr ? 0
                                    : config_provider->pimpl_->IsRunning()));
    return;
  }
  dprintf(fd, "\n AudioSetConfigurationProvider: \n");
  config_provider->pimpl_->Dump(fd);
}

void AudioSetConfigurationProvider::Cleanup() {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  if (!config_provider) return;
  if (config_provider->pimpl_->IsRunning()) config_provider->pimpl_->Cleanup();
  config_provider.reset();
}

AudioSetConfigurationProvider* AudioSetConfigurationProvider::Get() {
  return config_provider.get();
}

const set_configurations::AudioSetConfigurations*
AudioSetConfigurationProvider::GetConfigurations(
    ::le_audio::types::LeAudioContextType content_type) const {
  if (CodecManager::GetInstance()->GetCodecLocation() ==
      types::CodecLocation::ADSP) {
    LOG_VERBOSE("Get offload config for the context type: %d",
                (int)content_type);
    const AudioSetConfigurations* offload_confs =
        CodecManager::GetInstance()->GetOffloadCodecConfig(content_type);

    if (offload_confs != nullptr && !(*offload_confs).empty()) {
      return offload_confs;
    }

    // TODO: Need to have a mechanism to switch to software session if offload
    // doesn't support.
  }

  LOG_VERBOSE("Get software config for the context type: %d",
              (int)content_type);

  if (pimpl_->IsRunning())
    return pimpl_->config_provider_impl_->GetConfigurationsByContextType(
        content_type);

  return nullptr;
}

bool AudioSetConfigurationProvider::CheckConfigurationIsBiDirSwb(
    const set_configurations::AudioSetConfiguration& set_configuration) const {
  uint8_t dir = 0;

  for (const auto& conf : set_configuration.confs) {
    if (conf.codec.GetSamplingFrequencyHz() >=
        le_audio::LeAudioCodecConfiguration::kSampleRate32000) {
      dir |= conf.direction;
    }
  }
  return dir == le_audio::types::kLeAudioDirectionBoth;
}

bool AudioSetConfigurationProvider::CheckConfigurationIsDualBiDirSwb(
    const set_configurations::AudioSetConfiguration& set_configuration) const {
  uint8_t single_dev_dual_bidir_swb = 0;
  uint8_t dual_dev_dual_bidir_swb = 0;

  for (const auto& conf : set_configuration.confs) {
    if (conf.codec.GetSamplingFrequencyHz() <
        le_audio::LeAudioCodecConfiguration::kSampleRate32000) {
      return false;
    }
    if (conf.device_cnt == 1 && conf.ase_cnt == 2) {
      single_dev_dual_bidir_swb |= conf.direction;
    } else if (conf.device_cnt == 2 && conf.ase_cnt == 2) {
      dual_dev_dual_bidir_swb |= conf.direction;
    }
  }

  return single_dev_dual_bidir_swb == le_audio::types::kLeAudioDirectionBoth ||
         dual_dev_dual_bidir_swb == le_audio::types::kLeAudioDirectionBoth;
}

bool AudioSetConfigurationProvider::IsDualBiDirSwbSupported(void) const {
  if (pimpl_->IsRunning()) {
    return pimpl_->config_provider_impl_->IsDualBiDirSwbSupported();
  }

  return false;
}

}  // namespace le_audio
