/******************************************************************************
 *
 *  Copyright (C) 1999-2012 Broadcom Corporation
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
#include "NfcAdaptation.h"

#include <aidl/android/hardware/nfc/BnNfc.h>
#include <aidl/android/hardware/nfc/BnNfcClientCallback.h>
#include <aidl/android/hardware/nfc/INfc.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/hardware/nfc/1.1/INfc.h>
#include <android/hardware/nfc/1.2/INfc.h>
#include <cutils/properties.h>
#include <hwbinder/ProcessState.h>

#include "debug_nfcsnoop.h"
#include "nfa_api.h"
#include "nfa_rw_api.h"
#include "nfc_config.h"
#include "nfc_int.h"

using ::android::wp;
using ::android::hardware::hidl_death_recipient;
using ::android::hidl::base::V1_0::IBase;

using android::OK;
using android::sp;
using android::status_t;

using android::base::StringPrintf;
using android::hardware::ProcessState;
using android::hardware::Return;
using android::hardware::Void;
using android::hardware::nfc::V1_0::INfc;
using android::hardware::nfc::V1_1::PresenceCheckAlgorithm;
using INfcV1_1 = android::hardware::nfc::V1_1::INfc;
using INfcV1_2 = android::hardware::nfc::V1_2::INfc;
using NfcVendorConfigV1_1 = android::hardware::nfc::V1_1::NfcConfig;
using NfcVendorConfigV1_2 = android::hardware::nfc::V1_2::NfcConfig;
using android::hardware::nfc::V1_1::INfcClientCallback;
using android::hardware::hidl_vec;
using INfcAidl = ::aidl::android::hardware::nfc::INfc;
using NfcAidlConfig = ::aidl::android::hardware::nfc::NfcConfig;
using AidlPresenceCheckAlgorithm =
    ::aidl::android::hardware::nfc::PresenceCheckAlgorithm;
using INfcAidlClientCallback =
    ::aidl::android::hardware::nfc::INfcClientCallback;
using NfcAidlEvent = ::aidl::android::hardware::nfc::NfcEvent;
using NfcAidlStatus = ::aidl::android::hardware::nfc::NfcStatus;
using ::aidl::android::hardware::nfc::NfcCloseType;
using Status = ::ndk::ScopedAStatus;

#define VERBOSE_VENDOR_LOG_PROPERTY "persist.nfc.vendor_debug_enabled"

std::string NFC_AIDL_HAL_SERVICE_NAME = "android.hardware.nfc.INfc/default";

extern void GKI_shutdown();
extern void verify_stack_non_volatile_store();
extern void delete_stack_non_volatile_store(bool forceDelete);

NfcAdaptation* NfcAdaptation::mpInstance = nullptr;
ThreadMutex NfcAdaptation::sLock;
ThreadCondVar NfcAdaptation::mHalOpenCompletedEvent;
ThreadCondVar NfcAdaptation::mHalCloseCompletedEvent;
sp<INfc> NfcAdaptation::mHal;
sp<INfcV1_1> NfcAdaptation::mHal_1_1;
sp<INfcV1_2> NfcAdaptation::mHal_1_2;
INfcClientCallback* NfcAdaptation::mCallback;
std::shared_ptr<INfcAidlClientCallback> mAidlCallback;
::ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;
std::shared_ptr<INfcAidl> mAidlHal;

bool nfc_nci_reset_keep_cfg_enabled = false;
uint8_t nfc_nci_reset_type = 0x00;
std::string nfc_storage_path;
uint8_t appl_dta_mode_flag = 0x00;
bool isDownloadFirmwareCompleted = false;
bool use_aidl = false;

extern tNFA_DM_CFG nfa_dm_cfg;
extern tNFA_PROPRIETARY_CFG nfa_proprietary_cfg;
extern tNFA_HCI_CFG nfa_hci_cfg;
extern uint8_t nfa_ee_max_ee_cfg;
extern bool nfa_poll_bail_out_mode;

// Whitelist for hosts allowed to create a pipe
// See ADM_CREATE_PIPE command in the ETSI test specification
// ETSI TS 102 622, section 6.1.3.1
static std::vector<uint8_t> host_allowlist;

namespace {
void initializeGlobalDebugEnabledFlag() {
  bool nfc_debug_enabled =
      (NfcConfig::getUnsigned(NAME_NFC_DEBUG_ENABLED, 0) != 0) ||
      property_get_bool("persist.nfc.debug_enabled", false);

  android::base::SetMinimumLogSeverity(nfc_debug_enabled ? android::base::DEBUG
                                                         : android::base::INFO);

  LOG(DEBUG) << StringPrintf("%s: level=%u", __func__, nfc_debug_enabled);
}

// initialize NciResetType Flag
// NCI_RESET_TYPE
// 0x00 default, reset configurations every time.
// 0x01, reset configurations only once every boot.
// 0x02, keep configurations.
void initializeNciResetTypeFlag() {
  nfc_nci_reset_type = NfcConfig::getUnsigned(NAME_NCI_RESET_TYPE, 0);
  LOG(DEBUG) << StringPrintf("%s: nfc_nci_reset_type=%u", __func__,
                             nfc_nci_reset_type);
}

// Abort nfc service when AIDL process died.
void HalAidlBinderDied(void* /* cookie */) {
  LOG(ERROR) << __func__ << "INfc aidl hal died, exiting procces to restart";
  exit(0);
}

}  // namespace

class NfcClientCallback : public INfcClientCallback {
 public:
  NfcClientCallback(tHAL_NFC_CBACK* eventCallback,
                    tHAL_NFC_DATA_CBACK dataCallback) {
    mEventCallback = eventCallback;
    mDataCallback = dataCallback;
  };
  virtual ~NfcClientCallback() = default;
  Return<void> sendEvent_1_1(
      ::android::hardware::nfc::V1_1::NfcEvent event,
      ::android::hardware::nfc::V1_0::NfcStatus event_status) override {
    mEventCallback((uint8_t)event, (tHAL_NFC_STATUS)event_status);
    return Void();
  };
  Return<void> sendEvent(
      ::android::hardware::nfc::V1_0::NfcEvent event,
      ::android::hardware::nfc::V1_0::NfcStatus event_status) override {
    mEventCallback((uint8_t)event, (tHAL_NFC_STATUS)event_status);
    return Void();
  };
  Return<void> sendData(
      const ::android::hardware::nfc::V1_0::NfcData& data) override {
    ::android::hardware::nfc::V1_0::NfcData copy = data;
    mDataCallback(copy.size(), &copy[0]);
    return Void();
  };

 private:
  tHAL_NFC_CBACK* mEventCallback;
  tHAL_NFC_DATA_CBACK* mDataCallback;
};

class NfcHalDeathRecipient : public hidl_death_recipient {
 public:
  android::sp<android::hardware::nfc::V1_0::INfc> mNfcDeathHal;
  NfcHalDeathRecipient(android::sp<android::hardware::nfc::V1_0::INfc>& mHal) {
    mNfcDeathHal = mHal;
  }

  virtual void serviceDied(
      uint64_t /* cookie */,
      const wp<::android::hidl::base::V1_0::IBase>& /* who */) {
    ALOGE(
        "NfcHalDeathRecipient::serviceDied - Nfc-Hal service died. Killing "
        "NfcService");
    if (mNfcDeathHal) {
      mNfcDeathHal->unlinkToDeath(this);
    }
    mNfcDeathHal = NULL;
    exit(0);
  }
  void finalize() {
    if (mNfcDeathHal) {
      mNfcDeathHal->unlinkToDeath(this);
    } else {
      LOG(DEBUG) << StringPrintf("%s: mNfcDeathHal is not set", __func__);
    }

    ALOGI("NfcHalDeathRecipient::destructor - NfcService");
    mNfcDeathHal = NULL;
  }
};

class NfcAidlClientCallback
    : public ::aidl::android::hardware::nfc::BnNfcClientCallback {
 public:
  NfcAidlClientCallback(tHAL_NFC_CBACK* eventCallback,
                        tHAL_NFC_DATA_CBACK dataCallback) {
    mEventCallback = eventCallback;
    mDataCallback = dataCallback;
  };
  virtual ~NfcAidlClientCallback() = default;

  ::ndk::ScopedAStatus sendEvent(NfcAidlEvent event,
                                 NfcAidlStatus event_status) override {
    uint8_t e_num;
    uint8_t s_num;
    switch (event) {
      case NfcAidlEvent::OPEN_CPLT:
        e_num = HAL_NFC_OPEN_CPLT_EVT;
        break;
      case NfcAidlEvent::CLOSE_CPLT:
        e_num = HAL_NFC_CLOSE_CPLT_EVT;
        break;
      case NfcAidlEvent::POST_INIT_CPLT:
        e_num = HAL_NFC_POST_INIT_CPLT_EVT;
        break;
      case NfcAidlEvent::PRE_DISCOVER_CPLT:
        e_num = HAL_NFC_PRE_DISCOVER_CPLT_EVT;
        break;
      case NfcAidlEvent::HCI_NETWORK_RESET:
        e_num = HAL_HCI_NETWORK_RESET;
        break;
      case NfcAidlEvent::ERROR:
      default:
        e_num = HAL_NFC_ERROR_EVT;
    }
    switch (event_status) {
      case NfcAidlStatus::OK:
        s_num = HAL_NFC_STATUS_OK;
        break;
      case NfcAidlStatus::FAILED:
        s_num = HAL_NFC_STATUS_FAILED;
        break;
      case NfcAidlStatus::ERR_TRANSPORT:
        s_num = HAL_NFC_STATUS_ERR_TRANSPORT;
        break;
      case NfcAidlStatus::ERR_CMD_TIMEOUT:
        s_num = HAL_NFC_STATUS_ERR_CMD_TIMEOUT;
        break;
      case NfcAidlStatus::REFUSED:
        s_num = HAL_NFC_STATUS_REFUSED;
        break;
      default:
        s_num = HAL_NFC_STATUS_FAILED;
    }
    mEventCallback(e_num, (tHAL_NFC_STATUS)s_num);
    return ::ndk::ScopedAStatus::ok();
  };
  ::ndk::ScopedAStatus sendData(const std::vector<uint8_t>& data) override {
    std::vector<uint8_t> copy = data;
    mDataCallback(copy.size(), &copy[0]);
    return ::ndk::ScopedAStatus::ok();
  };

 private:
  tHAL_NFC_CBACK* mEventCallback;
  tHAL_NFC_DATA_CBACK* mDataCallback;
};

/*******************************************************************************
**
** Function:    NfcAdaptation::NfcAdaptation()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
NfcAdaptation::NfcAdaptation() {
  memset(&mHalEntryFuncs, 0, sizeof(mHalEntryFuncs));
  mDeathRecipient = ::ndk::ScopedAIBinder_DeathRecipient(
      AIBinder_DeathRecipient_new(HalAidlBinderDied));
}

/*******************************************************************************
**
** Function:    NfcAdaptation::~NfcAdaptation()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
NfcAdaptation::~NfcAdaptation() { mpInstance = nullptr; }

/*******************************************************************************
**
** Function:    NfcAdaptation::GetInstance()
**
** Description: access class singleton
**
** Returns:     pointer to the singleton object
**
*******************************************************************************/
NfcAdaptation& NfcAdaptation::GetInstance() {
  AutoThreadMutex a(sLock);

  if (!mpInstance) {
    mpInstance = new NfcAdaptation;
    mpInstance->InitializeHalDeviceContext();
  }
  return *mpInstance;
}

void NfcAdaptation::GetVendorConfigs(
    std::map<std::string, ConfigValue>& configMap) {
  NfcVendorConfigV1_2 configValue;
  NfcAidlConfig aidlConfigValue;
  if (mAidlHal) {
    mAidlHal->getConfig(&aidlConfigValue);
  } else if (mHal_1_2) {
    mHal_1_2->getConfig_1_2(
        [&configValue](NfcVendorConfigV1_2 config) { configValue = config; });
  } else if (mHal_1_1) {
    mHal_1_1->getConfig([&configValue](NfcVendorConfigV1_1 config) {
      configValue.v1_1 = config;
      configValue.defaultIsoDepRoute = 0x00;
    });
  }

  if (mAidlHal) {
    std::vector<int8_t> nfaPropCfg = {
        aidlConfigValue.nfaProprietaryCfg.protocol18092Active,
        aidlConfigValue.nfaProprietaryCfg.protocolBPrime,
        aidlConfigValue.nfaProprietaryCfg.protocolDual,
        aidlConfigValue.nfaProprietaryCfg.protocol15693,
        aidlConfigValue.nfaProprietaryCfg.protocolKovio,
        aidlConfigValue.nfaProprietaryCfg.protocolMifare,
        aidlConfigValue.nfaProprietaryCfg.discoveryPollKovio,
        aidlConfigValue.nfaProprietaryCfg.discoveryPollBPrime,
        aidlConfigValue.nfaProprietaryCfg.discoveryListenBPrime};
    configMap.emplace(NAME_NFA_PROPRIETARY_CFG, ConfigValue(nfaPropCfg));
    configMap.emplace(NAME_NFA_POLL_BAIL_OUT_MODE,
                      ConfigValue(aidlConfigValue.nfaPollBailOutMode ? 1 : 0));
    if (aidlConfigValue.offHostRouteUicc.size() != 0) {
      configMap.emplace(NAME_OFFHOST_ROUTE_UICC,
                        ConfigValue(aidlConfigValue.offHostRouteUicc));
    }
    if (aidlConfigValue.offHostRouteEse.size() != 0) {
      configMap.emplace(NAME_OFFHOST_ROUTE_ESE,
                        ConfigValue(aidlConfigValue.offHostRouteEse));
    }
    // AIDL byte would be int8_t in C++.
    // Here we force cast int8_t to uint8_t for ConfigValue
    configMap.emplace(
        NAME_DEFAULT_OFFHOST_ROUTE,
        ConfigValue((uint8_t)aidlConfigValue.defaultOffHostRoute));
    configMap.emplace(NAME_DEFAULT_ROUTE,
                      ConfigValue((uint8_t)aidlConfigValue.defaultRoute));
    configMap.emplace(
        NAME_DEFAULT_NFCF_ROUTE,
        ConfigValue((uint8_t)aidlConfigValue.defaultOffHostRouteFelica));
    configMap.emplace(NAME_DEFAULT_ISODEP_ROUTE,
                      ConfigValue((uint8_t)aidlConfigValue.defaultIsoDepRoute));
    configMap.emplace(
        NAME_DEFAULT_SYS_CODE_ROUTE,
        ConfigValue((uint8_t)aidlConfigValue.defaultSystemCodeRoute));
    configMap.emplace(
        NAME_DEFAULT_SYS_CODE_PWR_STATE,
        ConfigValue((uint8_t)aidlConfigValue.defaultSystemCodePowerState));
    configMap.emplace(NAME_OFF_HOST_SIM_PIPE_ID,
                      ConfigValue((uint8_t)aidlConfigValue.offHostSIMPipeId));
    configMap.emplace(NAME_OFF_HOST_ESE_PIPE_ID,
                      ConfigValue((uint8_t)aidlConfigValue.offHostESEPipeId));

    configMap.emplace(NAME_ISO_DEP_MAX_TRANSCEIVE,
                      ConfigValue(aidlConfigValue.maxIsoDepTransceiveLength));
    if (aidlConfigValue.hostAllowlist.size() != 0) {
      configMap.emplace(NAME_DEVICE_HOST_ALLOW_LIST,
                        ConfigValue(aidlConfigValue.hostAllowlist));
    }
    /* For Backwards compatibility */
    if (aidlConfigValue.presenceCheckAlgorithm ==
        AidlPresenceCheckAlgorithm::ISO_DEP_NAK) {
      configMap.emplace(NAME_PRESENCE_CHECK_ALGORITHM,
                        ConfigValue((uint32_t)NFA_RW_PRES_CHK_ISO_DEP_NAK));
    } else {
      configMap.emplace(
          NAME_PRESENCE_CHECK_ALGORITHM,
          ConfigValue((uint32_t)aidlConfigValue.presenceCheckAlgorithm));
    }
  } else if (mHal_1_1 || mHal_1_2) {
    std::vector<uint8_t> nfaPropCfg = {
        configValue.v1_1.nfaProprietaryCfg.protocol18092Active,
        configValue.v1_1.nfaProprietaryCfg.protocolBPrime,
        configValue.v1_1.nfaProprietaryCfg.protocolDual,
        configValue.v1_1.nfaProprietaryCfg.protocol15693,
        configValue.v1_1.nfaProprietaryCfg.protocolKovio,
        configValue.v1_1.nfaProprietaryCfg.protocolMifare,
        configValue.v1_1.nfaProprietaryCfg.discoveryPollKovio,
        configValue.v1_1.nfaProprietaryCfg.discoveryPollBPrime,
        configValue.v1_1.nfaProprietaryCfg.discoveryListenBPrime};
    configMap.emplace(NAME_NFA_PROPRIETARY_CFG, ConfigValue(nfaPropCfg));
    configMap.emplace(NAME_NFA_POLL_BAIL_OUT_MODE,
                      ConfigValue(configValue.v1_1.nfaPollBailOutMode ? 1 : 0));
    configMap.emplace(NAME_DEFAULT_OFFHOST_ROUTE,
                      ConfigValue(configValue.v1_1.defaultOffHostRoute));
    if (configValue.offHostRouteUicc.size() != 0) {
      configMap.emplace(NAME_OFFHOST_ROUTE_UICC,
                        ConfigValue(configValue.offHostRouteUicc));
    }
    if (configValue.offHostRouteEse.size() != 0) {
      configMap.emplace(NAME_OFFHOST_ROUTE_ESE,
                        ConfigValue(configValue.offHostRouteEse));
    }
    configMap.emplace(NAME_DEFAULT_ROUTE,
                      ConfigValue(configValue.v1_1.defaultRoute));
    configMap.emplace(NAME_DEFAULT_NFCF_ROUTE,
                      ConfigValue(configValue.v1_1.defaultOffHostRouteFelica));
    configMap.emplace(NAME_DEFAULT_ISODEP_ROUTE,
                      ConfigValue(configValue.defaultIsoDepRoute));
    configMap.emplace(NAME_DEFAULT_SYS_CODE_ROUTE,
                      ConfigValue(configValue.v1_1.defaultSystemCodeRoute));
    configMap.emplace(
        NAME_DEFAULT_SYS_CODE_PWR_STATE,
        ConfigValue(configValue.v1_1.defaultSystemCodePowerState));
    configMap.emplace(NAME_OFF_HOST_SIM_PIPE_ID,
                      ConfigValue(configValue.v1_1.offHostSIMPipeId));
    configMap.emplace(NAME_OFF_HOST_ESE_PIPE_ID,
                      ConfigValue(configValue.v1_1.offHostESEPipeId));
    configMap.emplace(NAME_ISO_DEP_MAX_TRANSCEIVE,
                      ConfigValue(configValue.v1_1.maxIsoDepTransceiveLength));
    if (configValue.v1_1.hostWhitelist.size() != 0) {
      configMap.emplace(NAME_DEVICE_HOST_ALLOW_LIST,
                        ConfigValue(configValue.v1_1.hostWhitelist));
    }
    /* For Backwards compatibility */
    if (configValue.v1_1.presenceCheckAlgorithm ==
        PresenceCheckAlgorithm::ISO_DEP_NAK) {
      configMap.emplace(NAME_PRESENCE_CHECK_ALGORITHM,
                        ConfigValue((uint32_t)NFA_RW_PRES_CHK_ISO_DEP_NAK));
    } else {
      configMap.emplace(
          NAME_PRESENCE_CHECK_ALGORITHM,
          ConfigValue((uint32_t)configValue.v1_1.presenceCheckAlgorithm));
    }
  }
}
/*******************************************************************************
**
** Function:    NfcAdaptation::Initialize()
**
** Description: class initializer
**
** Returns:     none
**
*******************************************************************************/
void NfcAdaptation::Initialize() {
  const char* func = "NfcAdaptation::Initialize";
  // Init log tag
  android::base::InitLogging(nullptr);
  android::base::SetDefaultTag("libnfc_nci");

  initializeGlobalDebugEnabledFlag();
  initializeNciResetTypeFlag();

  LOG(DEBUG) << StringPrintf("%s: enter", func);

  nfc_storage_path = NfcConfig::getString(NAME_NFA_STORAGE, "/data/nfc");

  if (NfcConfig::hasKey(NAME_NFA_DM_CFG)) {
    std::vector<uint8_t> dm_config = NfcConfig::getBytes(NAME_NFA_DM_CFG);
    if (dm_config.size() > 0) nfa_dm_cfg.auto_detect_ndef = dm_config[0];
    if (dm_config.size() > 1) nfa_dm_cfg.auto_read_ndef = dm_config[1];
    if (dm_config.size() > 2) nfa_dm_cfg.auto_presence_check = dm_config[2];
    if (dm_config.size() > 3) nfa_dm_cfg.presence_check_option = dm_config[3];
    // NOTE: The timeout value is not configurable here because the endianness
    // of a byte array is ambiguous and needlessly difficult to configure.
    // If this value needs to be configurable, a numeric config option should
    // be used.
  }

  if (NfcConfig::hasKey(NAME_NFA_MAX_EE_SUPPORTED)) {
    nfa_ee_max_ee_cfg = NfcConfig::getUnsigned(NAME_NFA_MAX_EE_SUPPORTED);
    LOG(DEBUG) << StringPrintf(
        "%s: Overriding NFA_EE_MAX_EE_SUPPORTED to use %d", func,
        nfa_ee_max_ee_cfg);
  }

  if (NfcConfig::hasKey(NAME_NFA_POLL_BAIL_OUT_MODE)) {
    nfa_poll_bail_out_mode =
        NfcConfig::getUnsigned(NAME_NFA_POLL_BAIL_OUT_MODE);
    LOG(DEBUG) << StringPrintf(
        "%s: Overriding NFA_POLL_BAIL_OUT_MODE to use %d", func,
        nfa_poll_bail_out_mode);
  }

  if (NfcConfig::hasKey(NAME_NFA_PROPRIETARY_CFG)) {
    std::vector<uint8_t> p_config =
        NfcConfig::getBytes(NAME_NFA_PROPRIETARY_CFG);
    if (p_config.size() > 0)
      nfa_proprietary_cfg.pro_protocol_18092_active = p_config[0];
    if (p_config.size() > 1)
      nfa_proprietary_cfg.pro_protocol_b_prime = p_config[1];
    if (p_config.size() > 2)
      nfa_proprietary_cfg.pro_protocol_dual = p_config[2];
    if (p_config.size() > 3)
      nfa_proprietary_cfg.pro_protocol_15693 = p_config[3];
    if (p_config.size() > 4)
      nfa_proprietary_cfg.pro_protocol_kovio = p_config[4];
    if (p_config.size() > 5) nfa_proprietary_cfg.pro_protocol_mfc = p_config[5];
    if (p_config.size() > 6)
      nfa_proprietary_cfg.pro_discovery_kovio_poll = p_config[6];
    if (p_config.size() > 7)
      nfa_proprietary_cfg.pro_discovery_b_prime_poll = p_config[7];
    if (p_config.size() > 8)
      nfa_proprietary_cfg.pro_discovery_b_prime_listen = p_config[8];
  }

  // Configure allowlist of HCI host ID's
  // See specification: ETSI TS 102 622, section 6.1.3.1
  if (NfcConfig::hasKey(NAME_DEVICE_HOST_ALLOW_LIST)) {
    host_allowlist = NfcConfig::getBytes(NAME_DEVICE_HOST_ALLOW_LIST);
    nfa_hci_cfg.num_allowlist_host = host_allowlist.size();
    nfa_hci_cfg.p_allowlist = &host_allowlist[0];
  }

  verify_stack_non_volatile_store();
  if (NfcConfig::hasKey(NAME_PRESERVE_STORAGE) &&
      NfcConfig::getUnsigned(NAME_PRESERVE_STORAGE) == 1) {
    LOG(DEBUG) << StringPrintf("%s: preserve stack NV store", __func__);
  } else {
    delete_stack_non_volatile_store(FALSE);
  }

  GKI_init();
  GKI_enable();
  GKI_create_task((TASKPTR)NFCA_TASK, BTU_TASK, (int8_t*)"NFCA_TASK", nullptr, 0,
                  (pthread_cond_t*)nullptr, nullptr);
  {
    AutoThreadMutex guard(mCondVar);
    GKI_create_task((TASKPTR)Thread, MMI_TASK, (int8_t*)"NFCA_THREAD", nullptr, 0,
                    (pthread_cond_t*)nullptr, nullptr);
    mCondVar.wait();
  }

  debug_nfcsnoop_init();
  LOG(DEBUG) << StringPrintf("%s: exit", func);
}

/*******************************************************************************
**
** Function:    NfcAdaptation::Finalize()
**
** Description: class finalizer
**
** Returns:     none
**
*******************************************************************************/
void NfcAdaptation::Finalize() {
  const char* func = "NfcAdaptation::Finalize";
  AutoThreadMutex a(sLock);

  LOG(DEBUG) << StringPrintf("%s: enter", func);
  GKI_shutdown();

  NfcConfig::clear();

  if (mAidlHal != nullptr) {
    AIBinder_unlinkToDeath(mAidlHal->asBinder().get(), mDeathRecipient.get(),
                           nullptr);
  } else if (mHal != nullptr) {
    mNfcHalDeathRecipient->finalize();
  }
  LOG(DEBUG) << StringPrintf("%s: exit", func);
  delete this;
}

void NfcAdaptation::FactoryReset() {
  if (mAidlHal != nullptr) {
    mAidlHal->factoryReset();
  } else if (mHal_1_2 != nullptr) {
    mHal_1_2->factoryReset();
  } else if (mHal_1_1 != nullptr) {
    mHal_1_1->factoryReset();
  }
}

void NfcAdaptation::DeviceShutdown() {
  if (mAidlHal != nullptr) {
    mAidlHal->close(NfcCloseType::HOST_SWITCHED_OFF);
    AIBinder_unlinkToDeath(mAidlHal->asBinder().get(), mDeathRecipient.get(),
                           nullptr);
    mAidlHal = nullptr;
  } else {
    if (mHal_1_2 != nullptr) {
      mHal_1_2->closeForPowerOffCase();
    } else if (mHal_1_1 != nullptr) {
      mHal_1_1->closeForPowerOffCase();
    }
    if (mHal != nullptr) {
      mHal->unlinkToDeath(mNfcHalDeathRecipient);
    }
  }
}

/*******************************************************************************
**
** Function:    NfcAdaptation::Dump
**
** Description: Native support for dumpsys function.
**
** Returns:     None.
**
*******************************************************************************/
void NfcAdaptation::Dump(int fd) { debug_nfcsnoop_dump(fd); }

/*******************************************************************************
**
** Function:    NfcAdaptation::signal()
**
** Description: signal the CondVar to release the thread that is waiting
**
** Returns:     none
**
*******************************************************************************/
void NfcAdaptation::signal() { mCondVar.signal(); }

/*******************************************************************************
**
** Function:    NfcAdaptation::NFCA_TASK()
**
** Description: NFCA_TASK runs the GKI main task
**
** Returns:     none
**
*******************************************************************************/
uint32_t NfcAdaptation::NFCA_TASK(__attribute__((unused)) uint32_t arg) {
  const char* func = "NfcAdaptation::NFCA_TASK";
  LOG(DEBUG) << StringPrintf("%s: enter", func);
  GKI_run(nullptr);
  LOG(DEBUG) << StringPrintf("%s: exit", func);
  return 0;
}

/*******************************************************************************
**
** Function:    NfcAdaptation::Thread()
**
** Description: Creates work threads
**
** Returns:     none
**
*******************************************************************************/
uint32_t NfcAdaptation::Thread(__attribute__((unused)) uint32_t arg) {
  const char* func = "NfcAdaptation::Thread";
  LOG(DEBUG) << StringPrintf("%s: enter", func);

  {
    ThreadCondVar CondVar;
    AutoThreadMutex guard(CondVar);
    GKI_create_task((TASKPTR)nfc_task, NFC_TASK, (int8_t*)"NFC_TASK", nullptr, 0,
                    (pthread_cond_t*)CondVar, (pthread_mutex_t*)CondVar);
    CondVar.wait();
  }

  NfcAdaptation::GetInstance().signal();

  GKI_exit_task(GKI_get_taskid());
  LOG(DEBUG) << StringPrintf("%s: exit", func);
  return 0;
}

/*******************************************************************************
**
** Function:    NfcAdaptation::GetHalEntryFuncs()
**
** Description: Get the set of HAL entry points.
**
** Returns:     Functions pointers for HAL entry points.
**
*******************************************************************************/
tHAL_NFC_ENTRY* NfcAdaptation::GetHalEntryFuncs() { return &mHalEntryFuncs; }

/*******************************************************************************
**
** Function:    NfcAdaptation::InitializeHalDeviceContext
**
** Description: Check validity of current handle to the nfc HAL service
**
** Returns:     None.
**
*******************************************************************************/
void NfcAdaptation::InitializeHalDeviceContext() {
  const char* func = "NfcAdaptation::InitializeHalDeviceContext";

  mHalEntryFuncs.initialize = HalInitialize;
  mHalEntryFuncs.terminate = HalTerminate;
  mHalEntryFuncs.open = HalOpen;
  mHalEntryFuncs.close = HalClose;
  mHalEntryFuncs.core_initialized = HalCoreInitialized;
  mHalEntryFuncs.write = HalWrite;
  mHalEntryFuncs.prediscover = HalPrediscover;
  mHalEntryFuncs.control_granted = HalControlGranted;
  mHalEntryFuncs.power_cycle = HalPowerCycle;
  mHalEntryFuncs.get_max_ee = HalGetMaxNfcee;
  LOG(INFO) << StringPrintf("%s: INfc::getService()", func);
  mAidlHal = nullptr;
  mHal = mHal_1_1 = mHal_1_2 = nullptr;
  if (!use_aidl) {
    mHal = mHal_1_1 = mHal_1_2 = INfcV1_2::getService();
  }
  if (!use_aidl && mHal_1_2 == nullptr) {
    mHal = mHal_1_1 = INfcV1_1::getService();
    if (mHal_1_1 == nullptr) {
      mHal = INfc::getService();
    }
  }
  if (mHal == nullptr) {
    // Try get AIDL
    ::ndk::SpAIBinder binder(
        AServiceManager_waitForService(NFC_AIDL_HAL_SERVICE_NAME.c_str()));
    mAidlHal = INfcAidl::fromBinder(binder);
    if (mAidlHal != nullptr) {
      use_aidl = true;
      AIBinder_linkToDeath(mAidlHal->asBinder().get(), mDeathRecipient.get(),
                           nullptr /* cookie */);
      mHal = mHal_1_1 = mHal_1_2 = nullptr;
      LOG(INFO) << StringPrintf("%s: INfcAidl::fromBinder returned", func);
    }
    LOG_ALWAYS_FATAL_IF(mAidlHal == nullptr,
                        "Failed to retrieve the NFC AIDL!");
  } else {
    LOG(INFO) << StringPrintf("%s: INfc::getService() returned %p (%s)", func,
                              mHal.get(),
                              (mHal->isRemote() ? "remote" : "local"));
    mNfcHalDeathRecipient = new NfcHalDeathRecipient(mHal);
    mHal->linkToDeath(mNfcHalDeathRecipient, 0);
  }
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalInitialize
**
** Description: Not implemented because this function is only needed
**              within the HAL.
**
** Returns:     None.
**
*******************************************************************************/
void NfcAdaptation::HalInitialize() {
  const char* func = "NfcAdaptation::HalInitialize";
  LOG(DEBUG) << StringPrintf("%s", func);
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalTerminate
**
** Description: Not implemented because this function is only needed
**              within the HAL.
**
** Returns:     None.
**
*******************************************************************************/
void NfcAdaptation::HalTerminate() {
  const char* func = "NfcAdaptation::HalTerminate";
  LOG(DEBUG) << StringPrintf("%s", func);
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalOpen
**
** Description: Turn on controller, download firmware.
**
** Returns:     None.
**
*******************************************************************************/
void NfcAdaptation::HalOpen(tHAL_NFC_CBACK* p_hal_cback,
                            tHAL_NFC_DATA_CBACK* p_data_cback) {
  const char* func = "NfcAdaptation::HalOpen";
  LOG(DEBUG) << StringPrintf("%s", func);

  if (mAidlHal != nullptr) {
    mAidlCallback = ::ndk::SharedRefBase::make<NfcAidlClientCallback>(
        p_hal_cback, p_data_cback);
    Status status = mAidlHal->open(mAidlCallback);
    if (!status.isOk()) {
      LOG(ERROR) << "Open Error: "
                 << ::aidl::android::hardware::nfc::toString(
                        static_cast<NfcAidlStatus>(
                            status.getServiceSpecificError()));
    } else {
      bool verbose_vendor_log =
          android::base::GetBoolProperty(VERBOSE_VENDOR_LOG_PROPERTY, false);
      mAidlHal->setEnableVerboseLogging(verbose_vendor_log);
      LOG(DEBUG) << StringPrintf("%s: verbose_vendor_log=%u", __func__,
                                 verbose_vendor_log);
    }
  } else if (mHal_1_1 != nullptr) {
    mCallback = new NfcClientCallback(p_hal_cback, p_data_cback);
    mHal_1_1->open_1_1(mCallback);
  } else if (mHal != nullptr) {
    mCallback = new NfcClientCallback(p_hal_cback, p_data_cback);
    mHal->open(mCallback);
  }
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalClose
**
** Description: Turn off controller.
**
** Returns:     None.
**
*******************************************************************************/
void NfcAdaptation::HalClose() {
  const char* func = "NfcAdaptation::HalClose";
  LOG(DEBUG) << StringPrintf("%s", func);
  if (mAidlHal != nullptr) {
    mAidlHal->close(NfcCloseType::DISABLE);
  } else if (mHal != nullptr) {
    mHal->close();
  }
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalWrite
**
** Description: Write NCI message to the controller.
**
** Returns:     None.
**
*******************************************************************************/
void NfcAdaptation::HalWrite(uint16_t data_len, uint8_t* p_data) {
  const char* func = "NfcAdaptation::HalWrite";
  LOG(DEBUG) << StringPrintf("%s", func);

  if (mAidlHal != nullptr) {
    int ret;
    std::vector<uint8_t> aidl_data(p_data, p_data + data_len);
    mAidlHal->write(aidl_data, &ret);
  } else if (mHal != nullptr) {
    ::android::hardware::nfc::V1_0::NfcData data;
    data.setToExternal(p_data, data_len);
    mHal->write(data);
  }
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalCoreInitialized
**
** Description: Adjust the configurable parameters in the controller.
**
** Returns:     None.
**
*******************************************************************************/
void NfcAdaptation::HalCoreInitialized(uint16_t data_len,
                                       uint8_t* p_core_init_rsp_params) {
  const char* func = "NfcAdaptation::HalCoreInitialized";
  LOG(DEBUG) << StringPrintf("%s", func);
  if (mAidlHal != nullptr) {
    // AIDL coreInitialized doesn't send data to HAL.
    mAidlHal->coreInitialized();
  } else if (mHal != nullptr) {
    hidl_vec<uint8_t> data;
    data.setToExternal(p_core_init_rsp_params, data_len);
    mHal->coreInitialized(data);
  }
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalPrediscover
**
** Description:     Perform any vendor-specific pre-discovery actions (if
**                  needed) If any actions were performed TRUE will be returned,
**                  and HAL_PRE_DISCOVER_CPLT_EVT will notify when actions are
**                  completed.
**
** Returns:         TRUE if vendor-specific pre-discovery actions initialized
**                  FALSE if no vendor-specific pre-discovery actions are
**                  needed.
**
*******************************************************************************/
bool NfcAdaptation::HalPrediscover() {
  const char* func = "NfcAdaptation::HalPrediscover";
  LOG(DEBUG) << StringPrintf("%s", func);
  if (mAidlHal != nullptr) {
    Status status = mAidlHal->preDiscover();
    if (status.isOk()) {
      LOG(DEBUG) << StringPrintf("%s wait for NFC_PRE_DISCOVER_CPLT_EVT", func);
      return true;
    }
  } else if (mHal != nullptr) {
    mHal->prediscover();
  }

  return false;
}

/*******************************************************************************
**
** Function:        HAL_NfcControlGranted
**
** Description:     Grant control to HAL control for sending NCI commands.
**                  Call in response to HAL_REQUEST_CONTROL_EVT.
**                  Must only be called when there are no NCI commands pending.
**                  HAL_RELEASE_CONTROL_EVT will notify when HAL no longer
**                  needs control of NCI.
**
** Returns:         void
**
*******************************************************************************/
void NfcAdaptation::HalControlGranted() {
  const char* func = "NfcAdaptation::HalControlGranted";
  LOG(DEBUG) << StringPrintf("%s", func);
  if (mAidlHal != nullptr) {
    LOG(ERROR) << StringPrintf("Unsupported function %s", func);
  } else if (mHal != nullptr) {
    mHal->controlGranted();
  }
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalPowerCycle
**
** Description: Turn off and turn on the controller.
**
** Returns:     None.
**
*******************************************************************************/
void NfcAdaptation::HalPowerCycle() {
  const char* func = "NfcAdaptation::HalPowerCycle";
  LOG(DEBUG) << StringPrintf("%s", func);
  if (mAidlHal != nullptr) {
    mAidlHal->powerCycle();
  } else if (mHal != nullptr) {
    mHal->powerCycle();
  }
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalGetMaxNfcee
**
** Description: Turn off and turn on the controller.
**
** Returns:     None.
**
*******************************************************************************/
uint8_t NfcAdaptation::HalGetMaxNfcee() {
  const char* func = "NfcAdaptation::HalGetMaxNfcee";
  LOG(DEBUG) << StringPrintf("%s", func);

  return nfa_ee_max_ee_cfg;
}

/*******************************************************************************
**
** Function:    NfcAdaptation::DownloadFirmware
**
** Description: Download firmware patch files.
**
** Returns:     None.
**
*******************************************************************************/
bool NfcAdaptation::DownloadFirmware() {
  const char* func = "NfcAdaptation::DownloadFirmware";
  isDownloadFirmwareCompleted = false;
  LOG(DEBUG) << StringPrintf("%s: enter", func);
  HalInitialize();

  mHalOpenCompletedEvent.lock();
  LOG(DEBUG) << StringPrintf("%s: try open HAL", func);
  HalOpen(HalDownloadFirmwareCallback, HalDownloadFirmwareDataCallback);
  mHalOpenCompletedEvent.wait();

  LOG(DEBUG) << StringPrintf("%s: try close HAL", func);
  HalClose();

  HalTerminate();
  LOG(DEBUG) << StringPrintf("%s: exit", func);

  return isDownloadFirmwareCompleted;
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalDownloadFirmwareCallback
**
** Description: Receive events from the HAL.
**
** Returns:     None.
**
*******************************************************************************/
void NfcAdaptation::HalDownloadFirmwareCallback(nfc_event_t event,
                                                __attribute__((unused))
                                                nfc_status_t event_status) {
  const char* func = "NfcAdaptation::HalDownloadFirmwareCallback";
  LOG(DEBUG) << StringPrintf("%s: event=0x%X", func, event);
  switch (event) {
    case HAL_NFC_OPEN_CPLT_EVT: {
      LOG(DEBUG) << StringPrintf("%s: HAL_NFC_OPEN_CPLT_EVT", func);
      if (event_status == HAL_NFC_STATUS_OK) isDownloadFirmwareCompleted = true;
      mHalOpenCompletedEvent.signal();
      break;
    }
    case HAL_NFC_CLOSE_CPLT_EVT: {
      LOG(DEBUG) << StringPrintf("%s: HAL_NFC_CLOSE_CPLT_EVT", func);
      break;
    }
  }
}

/*******************************************************************************
**
** Function:    NfcAdaptation::HalDownloadFirmwareDataCallback
**
** Description: Receive data events from the HAL.
**
** Returns:     None.
**
*******************************************************************************/
void NfcAdaptation::HalDownloadFirmwareDataCallback(__attribute__((unused))
                                                    uint16_t data_len,
                                                    __attribute__((unused))
                                                    uint8_t* p_data) {}

/*******************************************************************************
**
** Function:    ThreadMutex::ThreadMutex()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
ThreadMutex::ThreadMutex() {
  pthread_mutexattr_t mutexAttr;

  pthread_mutexattr_init(&mutexAttr);
  pthread_mutex_init(&mMutex, &mutexAttr);
  pthread_mutexattr_destroy(&mutexAttr);
}

/*******************************************************************************
**
** Function:    ThreadMutex::~ThreadMutex()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
ThreadMutex::~ThreadMutex() { pthread_mutex_destroy(&mMutex); }

/*******************************************************************************
**
** Function:    ThreadMutex::lock()
**
** Description: lock kthe mutex
**
** Returns:     none
**
*******************************************************************************/
void ThreadMutex::lock() { pthread_mutex_lock(&mMutex); }

/*******************************************************************************
**
** Function:    ThreadMutex::unblock()
**
** Description: unlock the mutex
**
** Returns:     none
**
*******************************************************************************/
void ThreadMutex::unlock() { pthread_mutex_unlock(&mMutex); }

/*******************************************************************************
**
** Function:    ThreadCondVar::ThreadCondVar()
**
** Description: class constructor
**
** Returns:     none
**
*******************************************************************************/
ThreadCondVar::ThreadCondVar() {
  pthread_condattr_t CondAttr;

  pthread_condattr_init(&CondAttr);
  pthread_cond_init(&mCondVar, &CondAttr);

  pthread_condattr_destroy(&CondAttr);
}

/*******************************************************************************
**
** Function:    ThreadCondVar::~ThreadCondVar()
**
** Description: class destructor
**
** Returns:     none
**
*******************************************************************************/
ThreadCondVar::~ThreadCondVar() { pthread_cond_destroy(&mCondVar); }

/*******************************************************************************
**
** Function:    ThreadCondVar::wait()
**
** Description: wait on the mCondVar
**
** Returns:     none
**
*******************************************************************************/
void ThreadCondVar::wait() {
  pthread_cond_wait(&mCondVar, *this);
  pthread_mutex_unlock(*this);
}

/*******************************************************************************
**
** Function:    ThreadCondVar::signal()
**
** Description: signal the mCondVar
**
** Returns:     none
**
*******************************************************************************/
void ThreadCondVar::signal() {
  AutoThreadMutex a(*this);
  pthread_cond_signal(&mCondVar);
}

/*******************************************************************************
**
** Function:    AutoThreadMutex::AutoThreadMutex()
**
** Description: class constructor, automatically lock the mutex
**
** Returns:     none
**
*******************************************************************************/
AutoThreadMutex::AutoThreadMutex(ThreadMutex& m) : mm(m) { mm.lock(); }

/*******************************************************************************
**
** Function:    AutoThreadMutex::~AutoThreadMutex()
**
** Description: class destructor, automatically unlock the mutex
**
** Returns:     none
**
*******************************************************************************/
AutoThreadMutex::~AutoThreadMutex() { mm.unlock(); }
