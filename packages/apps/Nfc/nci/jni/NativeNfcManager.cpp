/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <cutils/properties.h>
#include <errno.h>
#include <nativehelper/JNIPlatformHelp.h>
#include <nativehelper/ScopedLocalRef.h>
#include <nativehelper/ScopedPrimitiveArray.h>
#include <nativehelper/ScopedUtfChars.h>
#include <semaphore.h>

#include "HciEventManager.h"
#include "JavaClassConstants.h"
#include "NativeWlcManager.h"
#include "NfcAdaptation.h"
#ifdef DTA_ENABLED
#include "NfcDta.h"
#endif /* DTA_ENABLED */
#include "NfcJniUtil.h"
#include "NfcTag.h"
#include "PowerSwitch.h"
#include "RoutingManager.h"
#include "SyncEvent.h"
#include "android_nfc.h"
#include "ce_api.h"
#include "debug_lmrt.h"
#include "nfa_api.h"
#include "nfa_ee_api.h"
#include "nfc_brcm_defs.h"
#include "nfc_config.h"
#include "rw_api.h"

using android::base::StringPrintf;

extern tNFA_DM_DISC_FREQ_CFG* p_nfa_dm_rf_disc_freq_cfg;  // defined in stack
namespace android {
extern bool gIsTagDeactivating;
extern bool gIsSelectingRfInterface;
extern void nativeNfcTag_doTransceiveStatus(tNFA_STATUS status, uint8_t* buf,
                                            uint32_t buflen);
extern void nativeNfcTag_notifyRfTimeout();
extern void nativeNfcTag_doConnectStatus(jboolean is_connect_ok);
extern void nativeNfcTag_doDeactivateStatus(int status);
extern void nativeNfcTag_doWriteStatus(jboolean is_write_ok);
extern jboolean nativeNfcTag_doDisconnect(JNIEnv*, jobject);
extern void nativeNfcTag_doCheckNdefResult(tNFA_STATUS status,
                                           uint32_t max_size,
                                           uint32_t current_size,
                                           uint8_t flags);
extern void nativeNfcTag_doMakeReadonlyResult(tNFA_STATUS status);
extern void nativeNfcTag_doPresenceCheckResult(tNFA_STATUS status);
extern void nativeNfcTag_formatStatus(bool is_ok);
extern void nativeNfcTag_resetPresenceCheck();
extern void nativeNfcTag_doReadCompleted(tNFA_STATUS status);
extern void nativeNfcTag_setRfInterface(tNFA_INTF_TYPE rfInterface);
extern void nativeNfcTag_setActivatedRfProtocol(tNFA_INTF_TYPE rfProtocol);
extern void nativeNfcTag_abortWaits();
extern void nativeNfcTag_registerNdefTypeHandler();
extern void nativeNfcTag_acquireRfInterfaceMutexLock();
extern void nativeNfcTag_releaseRfInterfaceMutexLock();
}  // namespace android

/*****************************************************************************
**
** public variables and functions
**
*****************************************************************************/
bool gActivated = false;
SyncEvent gDeactivatedEvent;
SyncEvent sNfaSetPowerSubState;
int recovery_option = 0;
int nfcee_power_and_link_conf = 0;

namespace android {
jmethodID gCachedNfcManagerNotifyNdefMessageListeners;
jmethodID gCachedNfcManagerNotifyTransactionListeners;
jmethodID gCachedNfcManagerNotifyHostEmuActivated;
jmethodID gCachedNfcManagerNotifyHostEmuData;
jmethodID gCachedNfcManagerNotifyHostEmuDeactivated;
jmethodID gCachedNfcManagerNotifyRfFieldActivated;
jmethodID gCachedNfcManagerNotifyRfFieldDeactivated;
jmethodID gCachedNfcManagerNotifyEeUpdated;
jmethodID gCachedNfcManagerNotifyHwErrorReported;
jmethodID gCachedNfcManagerNotifyPollingLoopFrame;
jmethodID gCachedNfcManagerNotifyWlcStopped;
jmethodID gCachedNfcManagerNotifyVendorSpecificEvent;
const char* gNativeP2pDeviceClassName =
    "com/android/nfc/dhimpl/NativeP2pDevice";
const char* gNativeNfcTagClassName = "com/android/nfc/dhimpl/NativeNfcTag";
const char* gNativeNfcManagerClassName =
    "com/android/nfc/dhimpl/NativeNfcManager";
const char* gNfcVendorNciResponseClassName =
    "com/android/nfc/NfcVendorNciResponse";
void doStartupConfig();
void startStopPolling(bool isStartPolling);
void startRfDiscovery(bool isStart);
bool isDiscoveryStarted();
}  // namespace android

/*****************************************************************************
**
** private variables and functions
**
*****************************************************************************/
namespace android {
static jint sLastError = ERROR_BUFFER_TOO_SMALL;
static SyncEvent sNfaEnableEvent;                // event for NFA_Enable()
static SyncEvent sNfaDisableEvent;               // event for NFA_Disable()
static SyncEvent sNfaEnableDisablePollingEvent;  // event for
                                                 // NFA_EnablePolling(),
                                                 // NFA_DisablePolling()
SyncEvent gNfaSetConfigEvent;                    // event for Set_Config....
SyncEvent gNfaGetConfigEvent;                    // event for Get_Config....
SyncEvent gNfaVsCommand;                         // event for VS commands
SyncEvent gSendRawVsCmdEvent;  // event for NFA_SendRawVsCommand()
static bool sIsNfaEnabled = false;
static bool sDiscoveryEnabled = false;  // is polling or listening
static bool sPollingEnabled = false;    // is polling for tag?
static bool sIsDisabling = false;
static bool sRfEnabled = false;   // whether RF discovery is enabled
static bool sSeRfActive = false;  // whether RF with SE is likely active
static bool sReaderModeEnabled =
    false;  // whether we're only reading tags, not allowing P2p/card emu
static bool sP2pEnabled = false;
static bool sP2pActive = false;  // whether p2p was last active
static bool sAbortConnlessWait = false;
static jint sLfT3tMax = 0;
static bool sRoutingInitialized = false;
static bool sIsRecovering = false;
static bool sIsAlwaysPolling = false;
static std::vector<uint8_t> sRawVendorCmdResponse;

#define CONFIG_UPDATE_TECH_MASK (1 << 1)
#define DEFAULT_TECH_MASK                                                  \
  (NFA_TECHNOLOGY_MASK_A | NFA_TECHNOLOGY_MASK_B | NFA_TECHNOLOGY_MASK_F | \
   NFA_TECHNOLOGY_MASK_V | NFA_TECHNOLOGY_MASK_B_PRIME |                   \
   NFA_TECHNOLOGY_MASK_A_ACTIVE | NFA_TECHNOLOGY_MASK_F_ACTIVE |           \
   NFA_TECHNOLOGY_MASK_KOVIO)
#define DEFAULT_DISCOVERY_DURATION 500
#define READER_MODE_DISCOVERY_DURATION 200

static void nfaConnectionCallback(uint8_t event, tNFA_CONN_EVT_DATA* eventData);
static void nfaDeviceManagementCallback(uint8_t event,
                                        tNFA_DM_CBACK_DATA* eventData);
static bool isPeerToPeer(tNFA_ACTIVATED& activated);
static bool isListenMode(tNFA_ACTIVATED& activated);
static tNFA_STATUS stopPolling_rfDiscoveryDisabled();
static tNFA_STATUS startPolling_rfDiscoveryDisabled(
    tNFA_TECHNOLOGY_MASK tech_mask);
static void nfcManager_doSetScreenState(JNIEnv* e, jobject o,
                                        jint screen_state_mask,
                                        jboolean alwaysPoll);
static jboolean nfcManager_doSetPowerSavingMode(JNIEnv* e, jobject o,
                                                bool flag);
static void sendRawVsCmdCallback(uint8_t event, uint16_t param_len,
                                 uint8_t* p_param);
static jbyteArray nfcManager_getProprietaryCaps(JNIEnv* e, jobject o);
tNFA_STATUS gVSCmdStatus = NFA_STATUS_OK;
uint16_t gCurrentConfigLen;
uint8_t gConfig[256];
std::vector<uint8_t> gCaps(0);
static int prevScreenState = NFA_SCREEN_STATE_OFF_LOCKED;
static int NFA_SCREEN_POLLING_TAG_MASK = 0x10;
static bool gIsDtaEnabled = false;
static bool gObserveModeEnabled = false;
/////////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////

namespace {
void initializeGlobalDebugEnabledFlag() {
  bool nfc_debug_enabled =
      (NfcConfig::getUnsigned(NAME_NFC_DEBUG_ENABLED, 1) != 0) ||
      property_get_bool("persist.nfc.debug_enabled", true);

  android::base::SetMinimumLogSeverity(nfc_debug_enabled ? android::base::DEBUG
                                                         : android::base::INFO);
}

void initializeRecoveryOption() {
  recovery_option = NfcConfig::getUnsigned(NAME_RECOVERY_OPTION, 0);

  LOG(DEBUG) << __func__ << ": recovery option=" << recovery_option;
}

void initializeNfceePowerAndLinkConf() {
  nfcee_power_and_link_conf =
      NfcConfig::getUnsigned(NAME_ALWAYS_ON_SET_EE_POWER_AND_LINK_CONF, 0);

  LOG(DEBUG) << __func__ << ": Always on set NFCEE_POWER_AND_LINK_CONF="
             << nfcee_power_and_link_conf;
}

}  // namespace

/*******************************************************************************
**
** Function:        getNative
**
** Description:     Get native data
**
** Returns:         Native data structure.
**
*******************************************************************************/
nfc_jni_native_data* getNative(JNIEnv* e, jobject o) {
  static struct nfc_jni_native_data* sCachedNat = NULL;
  if (e) {
    sCachedNat = nfc_jni_get_nat(e, o);
  }
  return sCachedNat;
}

/*******************************************************************************
**
** Function:        handleRfDiscoveryEvent
**
** Description:     Handle RF-discovery events from the stack.
**                  discoveredDevice: Discovered device.
**
** Returns:         None
**
*******************************************************************************/
static void handleRfDiscoveryEvent(tNFC_RESULT_DEVT* discoveredDevice) {
  NfcTag& natTag = NfcTag::getInstance();
  natTag.setNumDiscNtf(natTag.getNumDiscNtf() + 1);
  if (discoveredDevice->more == NCI_DISCOVER_NTF_MORE) {
    // there is more discovery notification coming
    return;
  }

  bool isP2p = natTag.isP2pDiscovered();

  if (natTag.getNumDiscNtf() > 1) {
    natTag.setMultiProtocolTagSupport(true);
    if (isP2p) {
      // Remove NFC_DEP NTF count
      // Skip NFC_DEP protocol in MultiProtocolTag select.
      natTag.setNumDiscNtf(natTag.getNumDiscNtf() - 1);
    }
  }

  if (sP2pEnabled && !sReaderModeEnabled && isP2p) {
    // select the peer that supports P2P
    natTag.selectP2p();
  } else {
    natTag.setNumDiscNtf(natTag.getNumDiscNtf() - 1);
    // select the first of multiple tags that is discovered
    natTag.selectFirstTag();
  }
}

/*******************************************************************************
**
** Function:        nfaConnectionCallback
**
** Description:     Receive connection-related events from stack.
**                  connEvent: Event code.
**                  eventData: Event data.
**
** Returns:         None
**
*******************************************************************************/
static void nfaConnectionCallback(uint8_t connEvent,
                                  tNFA_CONN_EVT_DATA* eventData) {
  tNFA_STATUS status = NFA_STATUS_FAILED;
  LOG(DEBUG) << StringPrintf("%s: event= %u", __func__, connEvent);

  switch (connEvent) {
    case NFA_LISTEN_ENABLED_EVT:  // whether listening successfully started
    {
      LOG(DEBUG) << StringPrintf("%s: NFA_LISTEN_ENABLED_EVT:status= %u",
                                 __func__, eventData->status);

      SyncEventGuard guard(sNfaEnableDisablePollingEvent);
      sNfaEnableDisablePollingEvent.notifyOne();
    } break;

    case NFA_POLL_ENABLED_EVT:  // whether polling successfully started
    {
      LOG(DEBUG) << StringPrintf("%s: NFA_POLL_ENABLED_EVT: status = %u",
                                 __func__, eventData->status);

      SyncEventGuard guard(sNfaEnableDisablePollingEvent);
      sNfaEnableDisablePollingEvent.notifyOne();
    } break;

    case NFA_POLL_DISABLED_EVT:  // Listening/Polling stopped
    {
      LOG(DEBUG) << StringPrintf("%s: NFA_POLL_DISABLED_EVT: status = %u",
                                 __func__, eventData->status);

      SyncEventGuard guard(sNfaEnableDisablePollingEvent);
      sNfaEnableDisablePollingEvent.notifyOne();
    } break;

    case NFA_RF_DISCOVERY_STARTED_EVT:  // RF Discovery started
    {
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_RF_DISCOVERY_STARTED_EVT: status = %u", __func__,
          eventData->status);

      SyncEventGuard guard(sNfaEnableDisablePollingEvent);
      sNfaEnableDisablePollingEvent.notifyOne();
    } break;

    case NFA_RF_DISCOVERY_STOPPED_EVT:  // RF Discovery stopped event
    {
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_RF_DISCOVERY_STOPPED_EVT: status = %u", __func__,
          eventData->status);

      gActivated = false;

      SyncEventGuard guard(sNfaEnableDisablePollingEvent);
      sNfaEnableDisablePollingEvent.notifyOne();
    } break;

    case NFA_DISC_RESULT_EVT:  // NFC link/protocol discovery notificaiton
      status = eventData->disc_result.status;
      LOG(DEBUG) << StringPrintf("%s: NFA_DISC_RESULT_EVT: status = %d",
                                 __func__, status);
      if (status != NFA_STATUS_OK) {
        NfcTag::getInstance().setNumDiscNtf(0);
        LOG(ERROR) << StringPrintf("%s: NFA_DISC_RESULT_EVT error: status = %d",
                                   __func__, status);
      } else {
        NfcTag::getInstance().connectionEventHandler(connEvent, eventData);
        handleRfDiscoveryEvent(&eventData->disc_result.discovery_ntf);
      }
      break;

    case NFA_SELECT_RESULT_EVT:  // NFC link/protocol discovery select response
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_SELECT_RESULT_EVT: status = %d, gIsSelectingRfInterface = "
          "%d, "
          "sIsDisabling=%d",
          __func__, eventData->status, gIsSelectingRfInterface, sIsDisabling);

      if (sIsDisabling) break;

      if (eventData->status != NFA_STATUS_OK) {
        if (gIsSelectingRfInterface) {
          nativeNfcTag_doConnectStatus(false);
        }

        LOG(ERROR) << StringPrintf(
            "%s: NFA_SELECT_RESULT_EVT error: status = %d", __func__,
            eventData->status);
        NFA_Deactivate(FALSE);
      }
      break;

    case NFA_DEACTIVATE_FAIL_EVT:
      LOG(DEBUG) << StringPrintf("%s: NFA_DEACTIVATE_FAIL_EVT: status = %d",
                                 __func__, eventData->status);
      break;

    case NFA_ACTIVATED_EVT:  // NFC link/protocol activated
    {
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_ACTIVATED_EVT: gIsSelectingRfInterface=%d, sIsDisabling=%d",
          __func__, gIsSelectingRfInterface, sIsDisabling);
      uint8_t activatedProtocol =
          (tNFA_INTF_TYPE)eventData->activated.activate_ntf.protocol;
      if (NFC_PROTOCOL_T5T == activatedProtocol &&
          NfcTag::getInstance().getNumDiscNtf()) {
        /* T5T doesn't support multiproto detection logic */
        NfcTag::getInstance().setNumDiscNtf(0);
      }
      if ((eventData->activated.activate_ntf.protocol !=
           NFA_PROTOCOL_NFC_DEP) &&
          (!isListenMode(eventData->activated))) {
        nativeNfcTag_setRfInterface(
            (tNFA_INTF_TYPE)eventData->activated.activate_ntf.intf_param.type);
        nativeNfcTag_setActivatedRfProtocol(activatedProtocol);
      }
      NfcTag::getInstance().setActive(true);
      if (sIsDisabling || !sIsNfaEnabled) break;
      gActivated = true;

      NfcTag::getInstance().setActivationState();
      if (gIsSelectingRfInterface) {
        nativeNfcTag_doConnectStatus(true);
        break;
      }

      nativeNfcTag_resetPresenceCheck();
      if (!isListenMode(eventData->activated) &&
          (prevScreenState == NFA_SCREEN_STATE_OFF_LOCKED ||
           prevScreenState == NFA_SCREEN_STATE_OFF_UNLOCKED)) {
        if (!sIsAlwaysPolling) {
          NFA_Deactivate(FALSE);
        }
      }
      if (isPeerToPeer(eventData->activated)) {
        if (sReaderModeEnabled) {
          LOG(DEBUG) << StringPrintf("%s: ignoring peer target in reader mode.",
                                     __func__);
          NFA_Deactivate(FALSE);
          break;
        }
        sP2pActive = true;
        LOG(DEBUG) << StringPrintf("%s: NFA_ACTIVATED_EVT; is p2p", __func__);
        if (NFC_GetNCIVersion() == NCI_VERSION_1_0) {
          // Disable RF field events in case of p2p
          uint8_t nfa_disable_rf_events[] = {0x00};
          LOG(DEBUG) << StringPrintf("%s: Disabling RF field events", __func__);
          status = NFA_SetConfig(NCI_PARAM_ID_RF_FIELD_INFO,
                                 sizeof(nfa_disable_rf_events),
                                 &nfa_disable_rf_events[0]);
          if (status == NFA_STATUS_OK) {
            LOG(DEBUG) << StringPrintf("%s: Disabled RF field events",
                                       __func__);
          } else {
            LOG(ERROR) << StringPrintf("%s: Failed to disable RF field events",
                                       __func__);
          }
        }
      } else {
        NfcTag::getInstance().connectionEventHandler(connEvent, eventData);
        if (NfcTag::getInstance().getNumDiscNtf()) {
          /*If its multiprotocol tag, deactivate tag with current selected
          protocol to sleep . Select tag with next supported protocol after
          deactivation event is received*/
          NFA_Deactivate(true);
        }

        // We know it is not activating for P2P.  If it activated in
        // listen mode then it is likely for an SE transaction.
        // Send the RF Event.
        if (isListenMode(eventData->activated)) {
          sSeRfActive = true;
        }
      }
    } break;
    case NFA_DEACTIVATED_EVT:  // NFC link/protocol deactivated
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_DEACTIVATED_EVT   Type: %u, gIsTagDeactivating: %d",
          __func__, eventData->deactivated.type, gIsTagDeactivating);
      NfcTag::getInstance().setDeactivationState(eventData->deactivated);
      NfcTag::getInstance().selectNextTagIfExists();
      if (eventData->deactivated.type != NFA_DEACTIVATE_TYPE_SLEEP) {
        {
          SyncEventGuard g(gDeactivatedEvent);
          gActivated = false;  // guard this variable from multi-threaded access
          gDeactivatedEvent.notifyOne();
        }
        nativeNfcTag_resetPresenceCheck();
        NfcTag::getInstance().connectionEventHandler(connEvent, eventData);
        nativeNfcTag_abortWaits();
        NfcTag::getInstance().abort();
      } else if (gIsTagDeactivating) {
        NfcTag::getInstance().setActive(false);
        nativeNfcTag_doDeactivateStatus(0);
      }

      // If RF is activated for what we think is a Secure Element transaction
      // and it is deactivated to either IDLE or DISCOVERY mode, notify w/event.
      if ((eventData->deactivated.type == NFA_DEACTIVATE_TYPE_IDLE) ||
          (eventData->deactivated.type == NFA_DEACTIVATE_TYPE_DISCOVERY)) {
        if (sSeRfActive) {
          sSeRfActive = false;
        } else if (sP2pActive) {
          sP2pActive = false;
          // Make sure RF field events are re-enabled
          LOG(DEBUG) << StringPrintf("%s: NFA_DEACTIVATED_EVT; is p2p",
                                     __func__);
          if (NFC_GetNCIVersion() == NCI_VERSION_1_0) {
            // Disable RF field events in case of p2p
            uint8_t nfa_enable_rf_events[] = {0x01};

            if (!sIsDisabling && sIsNfaEnabled) {
              LOG(DEBUG) << StringPrintf("%s: Enabling RF field events",
                                         __func__);
              status = NFA_SetConfig(NCI_PARAM_ID_RF_FIELD_INFO,
                                     sizeof(nfa_enable_rf_events),
                                     &nfa_enable_rf_events[0]);
              if (status == NFA_STATUS_OK) {
                LOG(DEBUG) << StringPrintf("%s: Enabled RF field events",
                                           __func__);
              } else {
                LOG(ERROR) << StringPrintf(
                    "%s: Failed to enable RF field events", __func__);
              }
            }
          }
        }
      }

      break;

    case NFA_TLV_DETECT_EVT:  // TLV Detection complete
      status = eventData->tlv_detect.status;
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_TLV_DETECT_EVT: status = %d, protocol = %d, num_tlvs = %d, "
          "num_bytes = %d",
          __func__, status, eventData->tlv_detect.protocol,
          eventData->tlv_detect.num_tlvs, eventData->tlv_detect.num_bytes);
      if (status != NFA_STATUS_OK) {
        LOG(ERROR) << StringPrintf("%s: NFA_TLV_DETECT_EVT error: status = %d",
                                   __func__, status);
      }
      break;

    case NFA_NDEF_DETECT_EVT:  // NDEF Detection complete;
      // if status is failure, it means the tag does not contain any or valid
      // NDEF data;  pass the failure status to the NFC Service;
      status = eventData->ndef_detect.status;
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_NDEF_DETECT_EVT: status = 0x%X, protocol = %u, "
          "max_size = %u, cur_size = %u, flags = 0x%X",
          __func__, status, eventData->ndef_detect.protocol,
          eventData->ndef_detect.max_size, eventData->ndef_detect.cur_size,
          eventData->ndef_detect.flags);
      NfcTag::getInstance().connectionEventHandler(connEvent, eventData);
      nativeNfcTag_doCheckNdefResult(status, eventData->ndef_detect.max_size,
                                     eventData->ndef_detect.cur_size,
                                     eventData->ndef_detect.flags);
      break;

    case NFA_DATA_EVT:  // Data message received (for non-NDEF reads)
      LOG(DEBUG) << StringPrintf("%s: NFA_DATA_EVT: status = 0x%X, len = %d",
                                 __func__, eventData->status,
                                 eventData->data.len);
      nativeNfcTag_doTransceiveStatus(eventData->status, eventData->data.p_data,
                                      eventData->data.len);
      break;
    case NFA_RW_INTF_ERROR_EVT:
      LOG(DEBUG) << StringPrintf("%s: NFC_RW_INTF_ERROR_EVT", __func__);
      nativeNfcTag_notifyRfTimeout();
      nativeNfcTag_doReadCompleted(NFA_STATUS_TIMEOUT);
      break;
    case NFA_SELECT_CPLT_EVT:  // Select completed
      status = eventData->status;
      LOG(DEBUG) << StringPrintf("%s: NFA_SELECT_CPLT_EVT: status = %d",
                                 __func__, status);
      if (status != NFA_STATUS_OK) {
        LOG(ERROR) << StringPrintf("%s: NFA_SELECT_CPLT_EVT error: status = %d",
                                   __func__, status);
      }
      break;

    case NFA_READ_CPLT_EVT:  // NDEF-read or tag-specific-read completed
      LOG(DEBUG) << StringPrintf("%s: NFA_READ_CPLT_EVT: status = 0x%X",
                                 __func__, eventData->status);
      nativeNfcTag_doReadCompleted(eventData->status);
      NfcTag::getInstance().connectionEventHandler(connEvent, eventData);
      break;

    case NFA_WRITE_CPLT_EVT:  // Write completed
      LOG(DEBUG) << StringPrintf("%s: NFA_WRITE_CPLT_EVT: status = %d",
                                 __func__, eventData->status);
      nativeNfcTag_doWriteStatus(eventData->status == NFA_STATUS_OK);
      break;

    case NFA_SET_TAG_RO_EVT:  // Tag set as Read only
      LOG(DEBUG) << StringPrintf("%s: NFA_SET_TAG_RO_EVT: status = %d",
                                 __func__, eventData->status);
      nativeNfcTag_doMakeReadonlyResult(eventData->status);
      break;

    case NFA_CE_NDEF_WRITE_START_EVT:  // NDEF write started
      LOG(DEBUG) << StringPrintf("%s: NFA_CE_NDEF_WRITE_START_EVT: status: %d",
                                 __func__, eventData->status);

      if (eventData->status != NFA_STATUS_OK)
        LOG(ERROR) << StringPrintf(
            "%s: NFA_CE_NDEF_WRITE_START_EVT error: status = %d", __func__,
            eventData->status);
      break;

    case NFA_CE_NDEF_WRITE_CPLT_EVT:  // NDEF write completed
      LOG(DEBUG) << StringPrintf("%s: FA_CE_NDEF_WRITE_CPLT_EVT: len = %u",
                                 __func__, eventData->ndef_write_cplt.len);
      break;

    case NFA_PRESENCE_CHECK_EVT:
      LOG(DEBUG) << StringPrintf("%s: NFA_PRESENCE_CHECK_EVT", __func__);
      nativeNfcTag_doPresenceCheckResult(eventData->status);
      break;
    case NFA_FORMAT_CPLT_EVT:
      LOG(DEBUG) << StringPrintf("%s: NFA_FORMAT_CPLT_EVT: status=0x%X",
                                 __func__, eventData->status);
      nativeNfcTag_formatStatus(eventData->status == NFA_STATUS_OK);
      break;

    case NFA_I93_CMD_CPLT_EVT:
      LOG(DEBUG) << StringPrintf("%s: NFA_I93_CMD_CPLT_EVT: status=0x%X",
                                 __func__, eventData->status);
      break;

    case NFA_CE_UICC_LISTEN_CONFIGURED_EVT:
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_CE_UICC_LISTEN_CONFIGURED_EVT : status=0x%X", __func__,
          eventData->status);
      break;

    default:
      LOG(DEBUG) << StringPrintf("%s: unknown event (%d) ????", __func__,
                                 connEvent);
      break;
  }
}

/*******************************************************************************
**
** Function:        nfcManager_initNativeStruc
**
** Description:     Initialize variables.
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         True if ok.
**
*******************************************************************************/
static jboolean nfcManager_initNativeStruc(JNIEnv* e, jobject o) {
  initializeGlobalDebugEnabledFlag();
  initializeRecoveryOption();
  initializeNfceePowerAndLinkConf();
  LOG(DEBUG) << StringPrintf("%s: enter", __func__);

  nfc_jni_native_data* nat =
      (nfc_jni_native_data*)malloc(sizeof(struct nfc_jni_native_data));
  if (nat == NULL) {
    LOG(ERROR) << StringPrintf("%s: fail allocate native data", __func__);
    return JNI_FALSE;
  }

  memset(nat, 0, sizeof(*nat));
  e->GetJavaVM(&(nat->vm));
  nat->env_version = e->GetVersion();
  nat->manager = e->NewGlobalRef(o);

  ScopedLocalRef<jclass> cls(e, e->GetObjectClass(o));
  jfieldID f = e->GetFieldID(cls.get(), "mNative", "J");
  e->SetLongField(o, f, (jlong)nat);

  /* Initialize native cached references */
  gCachedNfcManagerNotifyNdefMessageListeners =
      e->GetMethodID(cls.get(), "notifyNdefMessageListeners",
                     "(Lcom/android/nfc/dhimpl/NativeNfcTag;)V");

  gCachedNfcManagerNotifyHostEmuActivated =
      e->GetMethodID(cls.get(), "notifyHostEmuActivated", "(I)V");

  gCachedNfcManagerNotifyHostEmuData =
      e->GetMethodID(cls.get(), "notifyHostEmuData", "(I[B)V");

  gCachedNfcManagerNotifyHostEmuDeactivated =
      e->GetMethodID(cls.get(), "notifyHostEmuDeactivated", "(I)V");

  gCachedNfcManagerNotifyRfFieldActivated =
      e->GetMethodID(cls.get(), "notifyRfFieldActivated", "()V");
  gCachedNfcManagerNotifyRfFieldDeactivated =
      e->GetMethodID(cls.get(), "notifyRfFieldDeactivated", "()V");

  gCachedNfcManagerNotifyTransactionListeners = e->GetMethodID(
      cls.get(), "notifyTransactionListeners", "([B[BLjava/lang/String;)V");

  gCachedNfcManagerNotifyEeUpdated =
      e->GetMethodID(cls.get(), "notifyEeUpdated", "()V");

  gCachedNfcManagerNotifyHwErrorReported =
      e->GetMethodID(cls.get(), "notifyHwErrorReported", "()V");

  gCachedNfcManagerNotifyPollingLoopFrame =
      e->GetMethodID(cls.get(), "notifyPollingLoopFrame", "(I[B)V");

  gCachedNfcManagerNotifyVendorSpecificEvent =
      e->GetMethodID(cls.get(), "notifyVendorSpecificEvent", "(II[B)V");

  gCachedNfcManagerNotifyWlcStopped =
      e->GetMethodID(cls.get(), "notifyWlcStopped", "(I)V");

  if (nfc_jni_cache_object(e, gNativeNfcTagClassName, &(nat->cached_NfcTag)) ==
      -1) {
    LOG(ERROR) << StringPrintf("%s: fail cache NativeNfcTag", __func__);
    return JNI_FALSE;
  }

  if (nfc_jni_cache_object(e, gNativeP2pDeviceClassName,
                           &(nat->cached_P2pDevice)) == -1) {
    LOG(ERROR) << StringPrintf("%s: fail cache NativeP2pDevice", __func__);
    return JNI_FALSE;
  }

  LOG(DEBUG) << StringPrintf("%s: exit", __func__);
  return JNI_TRUE;
}

/*******************************************************************************
**
** Function:        nfaDeviceManagementCallback
**
** Description:     Receive device management events from stack.
**                  dmEvent: Device-management event ID.
**                  eventData: Data associated with event ID.
**
** Returns:         None
**
*******************************************************************************/
void nfaDeviceManagementCallback(uint8_t dmEvent,
                                 tNFA_DM_CBACK_DATA* eventData) {
  LOG(DEBUG) << StringPrintf("%s: enter; event=0x%X", __func__, dmEvent);

  switch (dmEvent) {
    case NFA_DM_ENABLE_EVT: /* Result of NFA_Enable */
    {
      SyncEventGuard guard(sNfaEnableEvent);
      LOG(DEBUG) << StringPrintf("%s: NFA_DM_ENABLE_EVT; status=0x%X", __func__,
                                 eventData->status);
      sIsNfaEnabled = eventData->status == NFA_STATUS_OK;
      sIsDisabling = false;
      sNfaEnableEvent.notifyOne();
    } break;

    case NFA_DM_DISABLE_EVT: /* Result of NFA_Disable */
    {
      SyncEventGuard guard(sNfaDisableEvent);
      LOG(DEBUG) << StringPrintf("%s: NFA_DM_DISABLE_EVT", __func__);
      sIsNfaEnabled = false;
      sIsDisabling = false;
      sNfaDisableEvent.notifyOne();
    } break;

    case NFA_DM_SET_CONFIG_EVT:  // result of NFA_SetConfig
      LOG(DEBUG) << StringPrintf("%s: NFA_DM_SET_CONFIG_EVT", __func__);
      {
        SyncEventGuard guard(gNfaSetConfigEvent);
        gNfaSetConfigEvent.notifyOne();
      }
      break;

    case NFA_DM_GET_CONFIG_EVT: /* Result of NFA_GetConfig */
      LOG(DEBUG) << StringPrintf("%s: NFA_DM_GET_CONFIG_EVT", __func__);
      {
        SyncEventGuard guard(gNfaGetConfigEvent);
        if (eventData->status == NFA_STATUS_OK &&
            eventData->get_config.tlv_size <= sizeof(gConfig)) {
          gCurrentConfigLen = eventData->get_config.tlv_size;
          memcpy(gConfig, eventData->get_config.param_tlvs,
                 eventData->get_config.tlv_size);
        } else {
          LOG(ERROR) << StringPrintf("%s: NFA_DM_GET_CONFIG failed", __func__);
          gCurrentConfigLen = 0;
        }
        gNfaGetConfigEvent.notifyOne();
      }
      break;

    case NFA_DM_RF_FIELD_EVT:
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_DM_RF_FIELD_EVT; status=0x%X; field status=%u", __func__,
          eventData->rf_field.status, eventData->rf_field.rf_field_status);
      if (!sP2pActive && eventData->rf_field.status == NFA_STATUS_OK) {
        struct nfc_jni_native_data* nat = getNative(NULL, NULL);
        if (!nat) {
          LOG(ERROR) << StringPrintf("cached nat is null");
          return;
        }
        JNIEnv* e = NULL;
        ScopedAttach attach(nat->vm, &e);
        if (e == NULL) {
          LOG(ERROR) << StringPrintf("jni env is null");
          return;
        }
        if (eventData->rf_field.rf_field_status == NFA_DM_RF_FIELD_ON)
          e->CallVoidMethod(nat->manager,
                            android::gCachedNfcManagerNotifyRfFieldActivated);
        else
          e->CallVoidMethod(nat->manager,
                            android::gCachedNfcManagerNotifyRfFieldDeactivated);
      }
      break;

    case NFA_DM_NFCC_TRANSPORT_ERR_EVT:
    case NFA_DM_NFCC_TIMEOUT_EVT: {
      if (dmEvent == NFA_DM_NFCC_TIMEOUT_EVT)
        LOG(ERROR) << StringPrintf("%s: NFA_DM_NFCC_TIMEOUT_EVT; abort",
                                   __func__);
      else if (dmEvent == NFA_DM_NFCC_TRANSPORT_ERR_EVT)
        LOG(ERROR) << StringPrintf("%s: NFA_DM_NFCC_TRANSPORT_ERR_EVT; abort",
                                   __func__);

      struct nfc_jni_native_data* nat = getNative(NULL, NULL);
      if (recovery_option && nat != NULL) {
        JNIEnv* e = NULL;
        ScopedAttach attach(nat->vm, &e);
        if (e == NULL) {
          LOG(ERROR) << StringPrintf("jni env is null");
          return;
        }
        LOG(ERROR) << StringPrintf("%s: toggle NFC state to recovery nfc",
                                   __func__);
        sIsRecovering = true;
        e->CallVoidMethod(nat->manager,
                          android::gCachedNfcManagerNotifyHwErrorReported);
        {
          LOG(DEBUG) << StringPrintf(
              "%s: aborting  sNfaEnableDisablePollingEvent", __func__);
          SyncEventGuard guard(sNfaEnableDisablePollingEvent);
          sNfaEnableDisablePollingEvent.notifyOne();
        }
        {
          LOG(DEBUG) << StringPrintf("%s: aborting  sNfaEnableEvent", __func__);
          SyncEventGuard guard(sNfaEnableEvent);
          sNfaEnableEvent.notifyOne();
        }
        {
          LOG(DEBUG) << StringPrintf("%s: aborting  sNfaDisableEvent",
                                     __func__);
          SyncEventGuard guard(sNfaDisableEvent);
          sNfaDisableEvent.notifyOne();
        }
        {
          LOG(DEBUG) << StringPrintf("%s: aborting  sNfaSetPowerSubState",
                                     __func__);
          SyncEventGuard guard(sNfaSetPowerSubState);
          sNfaSetPowerSubState.notifyOne();
        }
        {
          LOG(DEBUG) << StringPrintf("%s: aborting gNfaSetConfigEvent",
                                     __func__);
          SyncEventGuard guard(gNfaSetConfigEvent);
          gNfaSetConfigEvent.notifyOne();
        }
        {
          LOG(DEBUG) << StringPrintf("%s: aborting gNfaGetConfigEvent",
                                     __func__);
          SyncEventGuard guard(gNfaGetConfigEvent);
          gNfaGetConfigEvent.notifyOne();
        }
      } else {
        nativeNfcTag_abortWaits();
        NfcTag::getInstance().abort();
        sAbortConnlessWait = true;
        {
          LOG(DEBUG) << StringPrintf(
              "%s: aborting  sNfaEnableDisablePollingEvent", __func__);
          SyncEventGuard guard(sNfaEnableDisablePollingEvent);
          sNfaEnableDisablePollingEvent.notifyOne();
        }
        {
          LOG(DEBUG) << StringPrintf("%s: aborting  sNfaEnableEvent", __func__);
          SyncEventGuard guard(sNfaEnableEvent);
          sNfaEnableEvent.notifyOne();
        }
        {
          LOG(DEBUG) << StringPrintf("%s: aborting  sNfaDisableEvent",
                                     __func__);
          SyncEventGuard guard(sNfaDisableEvent);
          sNfaDisableEvent.notifyOne();
        }
        sDiscoveryEnabled = false;
        sPollingEnabled = false;
        PowerSwitch::getInstance().abort();

        if (!sIsDisabling && sIsNfaEnabled) {
          NFA_Disable(FALSE);
          sIsDisabling = true;
        } else {
          sIsNfaEnabled = false;
          sIsDisabling = false;
        }
        PowerSwitch::getInstance().initialize(PowerSwitch::UNKNOWN_LEVEL);
        LOG(ERROR) << StringPrintf("%s: crash NFC service", __func__);
        //////////////////////////////////////////////
        // crash the NFC service process so it can restart automatically
        abort();
        //////////////////////////////////////////////
      }
    } break;

    case NFA_DM_PWR_MODE_CHANGE_EVT:
      PowerSwitch::getInstance().deviceManagementCallback(dmEvent, eventData);
      break;

    case NFA_DM_SET_POWER_SUB_STATE_EVT: {
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_DM_SET_POWER_SUB_STATE_EVT; status=0x%X", __FUNCTION__,
          eventData->power_sub_state.status);
      SyncEventGuard guard(sNfaSetPowerSubState);
      sNfaSetPowerSubState.notifyOne();
    } break;
    default:
      LOG(DEBUG) << StringPrintf("%s: unhandled event", __func__);
      break;
  }
}

/*******************************************************************************
**
** Function:        nfcManager_sendRawFrame
**
** Description:     Send a raw frame.
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         True if ok.
**
*******************************************************************************/
static jboolean nfcManager_sendRawFrame(JNIEnv* e, jobject, jbyteArray data) {
  ScopedByteArrayRO bytes(e, data);
  uint8_t* buf =
      const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(&bytes[0]));
  size_t bufLen = bytes.size();
  tNFA_STATUS status = NFA_SendRawFrame(buf, bufLen, 0);

  return (status == NFA_STATUS_OK);
}

/*******************************************************************************
**
** Function:        nfcManager_routeAid
**
** Description:     Route an AID to an EE
**                  e: JVM environment.
**                  aid: aid to be added to routing table.
**                  route: aid route location. i.e. DH/eSE/UICC
**                  aidInfo: prefix or suffix aid.
**
** Returns:         True if aid is accpted by NFA Layer.
**
*******************************************************************************/
static jboolean nfcManager_routeAid(JNIEnv* e, jobject, jbyteArray aid,
                                    jint route, jint aidInfo, jint power) {
  uint8_t* buf;
  size_t bufLen;
  if (sIsDisabling || !sIsNfaEnabled) {
    return false;
  }

  if (aid == NULL) {
    buf = NULL;
    bufLen = 0;
    return RoutingManager::getInstance().addAidRouting(buf, bufLen, route,
                                                       aidInfo, power);
  }
  ScopedByteArrayRO bytes(e, aid);
  buf = const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(&bytes[0]));
  bufLen = bytes.size();
  return RoutingManager::getInstance().addAidRouting(buf, bufLen, route,
                                                     aidInfo, power);
}

/*******************************************************************************
**
** Function:        nfcManager_unrouteAid
**
** Description:     Remove a AID routing
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         True if ok.
**
*******************************************************************************/
static jboolean nfcManager_unrouteAid(JNIEnv* e, jobject, jbyteArray aid) {
  uint8_t* buf;
  size_t bufLen;
  if (sIsDisabling || !sIsNfaEnabled) {
    return false;
  }

  if (aid == NULL) {
    buf = NULL;
    bufLen = 0;
    return RoutingManager::getInstance().removeAidRouting(buf, bufLen);
  }
  ScopedByteArrayRO bytes(e, aid);
  buf = const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(&bytes[0]));
  bufLen = bytes.size();
  return RoutingManager::getInstance().removeAidRouting(buf, bufLen);
}

/*******************************************************************************
**
** Function:        nfcManager_commitRouting
**
** Description:     Sends the AID routing table to the controller
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         True if ok.
**
*******************************************************************************/
static jboolean nfcManager_commitRouting(JNIEnv* e, jobject) {
  if (sRfEnabled) {
    /*Update routing table only in Idle state.*/
    startRfDiscovery(false);
  }
  jboolean commitStatus = RoutingManager::getInstance().commitRouting();
  startRfDiscovery(true);
  return commitStatus;
}

void static nfaVSCallback(uint8_t event, uint16_t param_len, uint8_t* p_param) {
  switch (event & NCI_OID_MASK) {
    case NCI_MSG_PROP_ANDROID: {
      uint8_t android_sub_opcode = p_param[3];
      switch (android_sub_opcode) {
        case NCI_ANDROID_PASSIVE_OBSERVE: {
          gVSCmdStatus = p_param[4];
          LOG(INFO) << StringPrintf("Observe mode RSP: status: %x",
                                    gVSCmdStatus);
          SyncEventGuard guard(gNfaVsCommand);
          gNfaVsCommand.notifyOne();
        } break;
        case NCI_ANDROID_GET_CAPS: {
          gVSCmdStatus = p_param[4];
          u_int16_t android_version = *(u_int16_t*)&p_param[5];
          u_int8_t len = p_param[7];
          gCaps.assign(p_param + 8, p_param + 8 + len);
        } break;
        case NCI_ANDROID_POLLING_FRAME_NTF: {
          struct nfc_jni_native_data* nat = getNative(NULL, NULL);
          if (!nat) {
            LOG(ERROR) << StringPrintf("cached nat is null");
            return;
          }
          JNIEnv* e = NULL;
          ScopedAttach attach(nat->vm, &e);
          if (e == NULL) {
            LOG(ERROR) << StringPrintf("jni env is null");
            return;
          }
          ScopedLocalRef<jobject> dataJavaArray(e, e->NewByteArray(param_len));
          if (dataJavaArray.get() == NULL) {
            LOG(ERROR) << "fail allocate array";
            return;
          }
          e->SetByteArrayRegion((jbyteArray)dataJavaArray.get(), 0, param_len,
                                (jbyte*)(p_param));
          if (e->ExceptionCheck()) {
            e->ExceptionClear();
            LOG(ERROR) << "failed to fill array";
            return;
          }
          e->CallVoidMethod(nat->manager,
                            android::gCachedNfcManagerNotifyPollingLoopFrame,
                            (jint)param_len, dataJavaArray.get());

        } break;
        default:
          LOG(DEBUG) << StringPrintf("Unknown Android sub opcode %x",
                                     android_sub_opcode);
      }
    } break;
    default: {
      struct nfc_jni_native_data* nat = getNative(NULL, NULL);
      if (!nat) {
        LOG(ERROR) << StringPrintf("%s: cached nat is null", __FUNCTION__);
        return;
      }
      JNIEnv* e = NULL;
      ScopedAttach attach(nat->vm, &e);
      if (e == NULL) {
        LOG(ERROR) << StringPrintf("%s: jni env is null", __FUNCTION__);
      return;
      }
      ScopedLocalRef<jobject> dataJavaArray(e, e->NewByteArray(param_len));
      if (dataJavaArray.get() == NULL) {
        LOG(ERROR) << StringPrintf("%s: fail allocate array", __FUNCTION__);
        return;
      }
      e->SetByteArrayRegion((jbyteArray)dataJavaArray.get(), 0, param_len, (jbyte*)(p_param));
      if (e->ExceptionCheck()) {
        e->ExceptionClear();
        LOG(ERROR) << StringPrintf("%s failed to fill array", __FUNCTION__);
        return;
      }
      e->CallVoidMethod(nat->manager, android::gCachedNfcManagerNotifyVendorSpecificEvent,
                        (jint)event, (jint)param_len, dataJavaArray.get());
    } break;
  }
}

static jboolean isObserveModeSupported(JNIEnv* e, jobject o) {
  ScopedLocalRef<jclass> cls(e, e->GetObjectClass(o));
  jmethodID isSupported =
      e->GetMethodID(cls.get(), "isObserveModeSupported", "()Z");
  return e->CallBooleanMethod(o, isSupported);
}

static jboolean nfcManager_isObserveModeEnabled(JNIEnv* e, jobject o) {
  if (isObserveModeSupported(e, o) == JNI_FALSE) {
    return false;
  }
  LOG(DEBUG) << StringPrintf(
      "%s: returning %s", __FUNCTION__,
      (gObserveModeEnabled != JNI_FALSE ? "TRUE" : "FALSE"));
  return gObserveModeEnabled;
}

static void nfaSendRawVsCmdCallback(uint8_t event, uint16_t param_len,
                                    uint8_t* p_param) {
  if (param_len == 5) {
    gVSCmdStatus = p_param[4];
  } else {
    gVSCmdStatus = NFA_STATUS_FAILED;
  }
  SyncEventGuard guard(gNfaVsCommand);
  gNfaVsCommand.notifyOne();
}

static jboolean nfcManager_setObserveMode(JNIEnv* e, jobject o,
                                          jboolean enable) {
  if (isObserveModeSupported(e, o) == JNI_FALSE) {
    return false;
  }

  if ((enable != JNI_FALSE) ==
      (nfcManager_isObserveModeEnabled(e, o) != JNI_FALSE)) {
    LOG(DEBUG) << StringPrintf(
        "%s: called with %s but it is already %s, returning early",
        __FUNCTION__, (enable != JNI_FALSE ? "TRUE" : "FALSE"),
        (gObserveModeEnabled != JNI_FALSE ? "TRUE" : "FALSE"));
    return true;
  }
  bool reenbleDiscovery = false;
  if (sRfEnabled) {
    startRfDiscovery(false);
    reenbleDiscovery = true;
  }
  uint8_t cmd[] = {
      (NCI_MT_CMD << NCI_MT_SHIFT) | NCI_GID_PROP, NCI_MSG_PROP_ANDROID,
      NCI_ANDROID_PASSIVE_OBSERVE_PARAM_SIZE, NCI_ANDROID_PASSIVE_OBSERVE,
      static_cast<uint8_t>(enable != JNI_FALSE
                               ? NCI_ANDROID_PASSIVE_OBSERVE_PARAM_ENABLE
                               : NCI_ANDROID_PASSIVE_OBSERVE_PARAM_DISABLE)};

  tNFA_STATUS status = NFA_SendRawVsCommand(sizeof(cmd), cmd, nfaVSCallback);

  if (status == NFA_STATUS_OK) {
    if (!gNfaVsCommand.wait(1000)) {
      LOG(ERROR) << StringPrintf(
          "%s: Timed out waiting for a response to set observe mode ",
          __FUNCTION__);
      gVSCmdStatus = NFA_STATUS_FAILED;
    }
  } else {
    LOG(DEBUG) << StringPrintf("%s: Failed to set observe mode ", __FUNCTION__);
    gVSCmdStatus = NFA_STATUS_FAILED;
  }

  if (reenbleDiscovery) {
    startRfDiscovery(true);
  }

  if (gVSCmdStatus == NFA_STATUS_OK) {
    gObserveModeEnabled = enable;
  } else {
    gObserveModeEnabled = nfcManager_isObserveModeEnabled(e, o);
  }

  LOG(DEBUG) << StringPrintf(
      "%s: Set observe mode to %s with result %x, observe mode is now %s.",
      __FUNCTION__, (enable != JNI_FALSE ? "TRUE" : "FALSE"), gVSCmdStatus,
      (gObserveModeEnabled ? "enabled" : "disabled"));
  return gObserveModeEnabled == enable;
}

/*******************************************************************************
**
** Function:        nfcManager_doRegisterT3tIdentifier
**
** Description:     Registers LF_T3T_IDENTIFIER for NFC-F.
**                  e: JVM environment.
**                  o: Java object.
**                  t3tIdentifier: LF_T3T_IDENTIFIER value (10 or 18 bytes)
**
** Returns:         Handle retrieve from RoutingManager.
**
*******************************************************************************/
static jint nfcManager_doRegisterT3tIdentifier(JNIEnv* e, jobject,
                                               jbyteArray t3tIdentifier) {
  LOG(DEBUG) << StringPrintf("%s: enter", __func__);

  ScopedByteArrayRO bytes(e, t3tIdentifier);
  uint8_t* buf =
      const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(&bytes[0]));
  size_t bufLen = bytes.size();
  int handle = RoutingManager::getInstance().registerT3tIdentifier(buf, bufLen);

  LOG(DEBUG) << StringPrintf("%s: handle=%d", __func__, handle);
  if (handle != NFA_HANDLE_INVALID)
    RoutingManager::getInstance().commitRouting();
  LOG(DEBUG) << StringPrintf("%s: exit", __func__);

  return handle;
}

/*******************************************************************************
**
** Function:        nfcManager_doDeregisterT3tIdentifier
**
** Description:     Deregisters LF_T3T_IDENTIFIER for NFC-F.
**                  e: JVM environment.
**                  o: Java object.
**                  handle: Handle retrieve from libnfc-nci.
**
** Returns:         None
**
*******************************************************************************/
static void nfcManager_doDeregisterT3tIdentifier(JNIEnv*, jobject,
                                                 jint handle) {
  LOG(DEBUG) << StringPrintf("%s: enter; handle=%d", __func__, handle);

  RoutingManager::getInstance().deregisterT3tIdentifier(handle);
  RoutingManager::getInstance().commitRouting();

  LOG(DEBUG) << StringPrintf("%s: exit", __func__);
}

/*******************************************************************************
**
** Function:        nfcManager_getLfT3tMax
**
** Description:     Returns LF_T3T_MAX value.
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         LF_T3T_MAX value.
**
*******************************************************************************/
static jint nfcManager_getLfT3tMax(JNIEnv*, jobject) {
  LOG(DEBUG) << StringPrintf("%s: enter", __func__);
  LOG(DEBUG) << StringPrintf("LF_T3T_MAX=%d", sLfT3tMax);
  LOG(DEBUG) << StringPrintf("%s: exit", __func__);

  return sLfT3tMax;
}

/*******************************************************************************
**
** Function:        nfcManager_doInitialize
**
** Description:     Turn on NFC.
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         True if ok.
**
*******************************************************************************/
static jboolean nfcManager_doInitialize(JNIEnv* e, jobject o) {
  initializeGlobalDebugEnabledFlag();
  tNFA_STATUS stat = NFA_STATUS_OK;
  sIsRecovering = false;

  PowerSwitch& powerSwitch = PowerSwitch::getInstance();

  if (sIsNfaEnabled) {
    LOG(DEBUG) << StringPrintf("%s: already enabled", __func__);
    goto TheEnd;
  }

  powerSwitch.initialize(PowerSwitch::FULL_POWER);

  {

    NfcAdaptation& theInstance = NfcAdaptation::GetInstance();
    theInstance.Initialize();  // start GKI, NCI task, NFC task

    {
      SyncEventGuard guard(sNfaEnableEvent);
      tHAL_NFC_ENTRY* halFuncEntries = theInstance.GetHalEntryFuncs();

      NFA_Init(halFuncEntries);

      stat = NFA_Enable(nfaDeviceManagementCallback, nfaConnectionCallback);
      if (stat == NFA_STATUS_OK) {
        sNfaEnableEvent.wait();  // wait for NFA command to finish
      }
    }

    if (stat == NFA_STATUS_OK) {
      // sIsNfaEnabled indicates whether stack started successfully
      if (sIsNfaEnabled) {
        sRoutingInitialized =
            RoutingManager::getInstance().initialize(getNative(e, o));
        nativeNfcTag_registerNdefTypeHandler();
        NfcTag::getInstance().initialize(getNative(e, o));
        HciEventManager::getInstance().initialize(getNative(e, o));
        NativeWlcManager::getInstance().initialize(getNative(e, o));

        /////////////////////////////////////////////////////////////////////////////////
        // Add extra configuration here (work-arounds, etc.)

        if (gIsDtaEnabled == true) {
          uint8_t configData = 0;
          configData = 0x01; /* Poll NFC-DEP : Highest Available Bit Rates */
          NFA_SetConfig(NCI_PARAM_ID_BITR_NFC_DEP, sizeof(uint8_t),
                        &configData);
          configData = 0x0B; /* Listen NFC-DEP : Waiting Time */
          NFA_SetConfig(NFC_PMID_WT, sizeof(uint8_t), &configData);
          configData = 0x0F; /* Specific Parameters for NFC-DEP RF Interface */
          NFA_SetConfig(NCI_PARAM_ID_NFC_DEP_OP, sizeof(uint8_t), &configData);
        }

        struct nfc_jni_native_data* nat = getNative(e, o);
        if (nat) {
          nat->tech_mask =
              NfcConfig::getUnsigned(NAME_POLLING_TECH_MASK, DEFAULT_TECH_MASK);
          LOG(DEBUG) << StringPrintf("%s: tag polling tech mask=0x%X", __func__,
                                     nat->tech_mask);

          // if this value exists, set polling interval.
          nat->discovery_duration = NfcConfig::getUnsigned(
              NAME_NFA_DM_DISC_DURATION_POLL, DEFAULT_DISCOVERY_DURATION);
          NFA_SetRfDiscoveryDuration(nat->discovery_duration);
        } else {
          LOG(ERROR) << StringPrintf("nat is null");
        }

        // get LF_T3T_MAX
        {
          SyncEventGuard guard(gNfaGetConfigEvent);
          tNFA_PMID configParam[1] = {NCI_PARAM_ID_LF_T3T_MAX};
          stat = NFA_GetConfig(1, configParam);
          if (stat == NFA_STATUS_OK) {
            gNfaGetConfigEvent.wait();
            if (gCurrentConfigLen >= 4 ||
                gConfig[1] == NCI_PARAM_ID_LF_T3T_MAX) {
              LOG(DEBUG) << StringPrintf("%s: lfT3tMax=%d", __func__,
                                         gConfig[3]);
              sLfT3tMax = gConfig[3];
            }
          }
        }

        prevScreenState = NFA_SCREEN_STATE_OFF_LOCKED;

        // Do custom NFCA startup configuration.
        doStartupConfig();
#ifdef DTA_ENABLED
        NfcDta::getInstance().setNfccConfigParams();
#endif /* DTA_ENABLED */
        goto TheEnd;
      }
    }

    LOG(ERROR) << StringPrintf("%s: fail nfa enable; error=0x%X", __func__,
                               stat);

    if (sIsNfaEnabled) {
      stat = NFA_Disable(FALSE /* ungraceful */);
    }

    theInstance.Finalize();
  }

TheEnd:
  if (sIsNfaEnabled) {
    PowerSwitch::getInstance().setLevel(PowerSwitch::LOW_POWER);
    if (android_nfc_nfc_read_polling_loop() || android_nfc_nfc_vendor_cmd()) {
      NFA_RegVSCback(true, &nfaVSCallback);
    }
  }
  LOG(DEBUG) << StringPrintf("%s: exit", __func__);
  return sIsNfaEnabled ? JNI_TRUE : JNI_FALSE;
}

static void nfcManager_doEnableDtaMode(JNIEnv*, jobject) {
  gIsDtaEnabled = true;
}

static void nfcManager_doDisableDtaMode(JNIEnv*, jobject) {
  gIsDtaEnabled = false;
}

static void nfcManager_doFactoryReset(JNIEnv*, jobject) {
  NfcAdaptation& theInstance = NfcAdaptation::GetInstance();
  theInstance.FactoryReset();
}

static void nfcManager_doShutdown(JNIEnv*, jobject) {
  NfcAdaptation& theInstance = NfcAdaptation::GetInstance();
  theInstance.DeviceShutdown();
}

static void nfcManager_configNfccConfigControl(bool flag) {
    // configure NFCC_CONFIG_CONTROL- NFCC allowed to manage RF configuration.
    if (NFC_GetNCIVersion() != NCI_VERSION_1_0) {
        uint8_t nfa_set_config[] = { 0x00 };

        nfa_set_config[0] = (flag == true ? 1 : 0);

        tNFA_STATUS status = NFA_SetConfig(NCI_PARAM_ID_NFCC_CONFIG_CONTROL,
                                           sizeof(nfa_set_config),
                                           &nfa_set_config[0]);
        if (status != NFA_STATUS_OK) {
            LOG(ERROR) << __func__
            << ": Failed to configure NFCC_CONFIG_CONTROL";
        }
    }
}

/*******************************************************************************
**
** Function:        nfcManager_enableDiscovery
**
** Description:     Start polling and listening for devices.
**                  e: JVM environment.
**                  o: Java object.
**                  technologies_mask: the bitmask of technologies for which to
*enable discovery
**                  enable_lptd: whether to enable low power polling (default:
*false)
**
** Returns:         None
**
*******************************************************************************/
static void nfcManager_enableDiscovery(JNIEnv* e, jobject o,
                                       jint technologies_mask,
                                       jboolean enable_lptd,
                                       jboolean reader_mode,
                                       jboolean enable_host_routing,
                                       jboolean enable_p2p, jboolean restart) {
  tNFA_TECHNOLOGY_MASK tech_mask = DEFAULT_TECH_MASK;
  struct nfc_jni_native_data* nat = getNative(e, o);

  if (technologies_mask == -1 && nat)
    tech_mask = (tNFA_TECHNOLOGY_MASK)nat->tech_mask;
  else if (technologies_mask != -1)
    tech_mask = (tNFA_TECHNOLOGY_MASK)technologies_mask;
  LOG(DEBUG) << StringPrintf("%s: enter; tech_mask = %02x", __func__,
                             tech_mask);

  if (sDiscoveryEnabled && !restart) {
    LOG(ERROR) << StringPrintf("%s: already discovering", __func__);
    return;
  }

  PowerSwitch::getInstance().setLevel(PowerSwitch::FULL_POWER);

  if (sRfEnabled) {
    // Stop RF discovery to reconfigure
    startRfDiscovery(false);
  }

  // Check polling configuration
  if (tech_mask != 0) {
    stopPolling_rfDiscoveryDisabled();
    startPolling_rfDiscoveryDisabled(tech_mask);

    // Start P2P listening if tag polling was enabled
    if (sPollingEnabled) {
            LOG(DEBUG) << StringPrintf("%s: Enable p2pListening", __func__);

            if (reader_mode && !sReaderModeEnabled) {
        sReaderModeEnabled = true;
        NFA_DisableListening();

        // configure NFCC_CONFIG_CONTROL- NFCC not allowed to manage RF configuration.
        nfcManager_configNfccConfigControl(false);

        NFA_SetRfDiscoveryDuration(READER_MODE_DISCOVERY_DURATION);
      } else if (!reader_mode && sReaderModeEnabled) {
        struct nfc_jni_native_data* nat = getNative(e, o);
        sReaderModeEnabled = false;
        NFA_EnableListening();

        // configure NFCC_CONFIG_CONTROL- NFCC allowed to manage RF configuration.
        nfcManager_configNfccConfigControl(true);

        if (nat) {
          NFA_SetRfDiscoveryDuration(nat->discovery_duration);
        } else {
          LOG(ERROR) << StringPrintf("nat is null");
        }
      }
    }
  } else {
    if (!reader_mode && sReaderModeEnabled) {
      LOG(DEBUG) << StringPrintf(
          "%s: if reader mode disable, enable listen again", __func__);
      struct nfc_jni_native_data* nat = getNative(e, o);
      sReaderModeEnabled = false;
      NFA_EnableListening();

      // configure NFCC_CONFIG_CONTROL- NFCC allowed to manage RF configuration.
      nfcManager_configNfccConfigControl(true);

      if (nat) {
        NFA_SetRfDiscoveryDuration(nat->discovery_duration);
      } else {
        LOG(ERROR) << StringPrintf("nat is null");
      }
    }
    // No technologies configured, stop polling
    stopPolling_rfDiscoveryDisabled();
  }

  // Check listen configuration
  if (enable_host_routing) {
    RoutingManager::getInstance().enableRoutingToHost();
    RoutingManager::getInstance().commitRouting();
  } else {
    RoutingManager::getInstance().disableRoutingToHost();
    RoutingManager::getInstance().commitRouting();
  }
  // Actually start discovery.
  startRfDiscovery(true);
  sDiscoveryEnabled = true;

  PowerSwitch::getInstance().setModeOn(PowerSwitch::DISCOVERY);

  LOG(DEBUG) << StringPrintf("%s: exit", __func__);
}

/*******************************************************************************
**
** Function:        nfcManager_disableDiscovery
**
** Description:     Stop polling and listening for devices.
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         None
**
*******************************************************************************/
void nfcManager_disableDiscovery(JNIEnv* e, jobject o) {
  tNFA_STATUS status = NFA_STATUS_OK;
  LOG(DEBUG) << StringPrintf("%s: enter;", __func__);

  if (sDiscoveryEnabled == false) {
    LOG(DEBUG) << StringPrintf("%s: already disabled", __func__);
    goto TheEnd;
  }

  // Stop RF Discovery.
  startRfDiscovery(false);
  sDiscoveryEnabled = false;
  if (sPollingEnabled) status = stopPolling_rfDiscoveryDisabled();

  // if nothing is active after this, then tell the controller to power down
  if (!PowerSwitch::getInstance().setModeOff(PowerSwitch::DISCOVERY))
    PowerSwitch::getInstance().setLevel(PowerSwitch::LOW_POWER);
TheEnd:
  LOG(DEBUG) << StringPrintf("%s: exit: Status = 0x%X", __func__, status);
}

/*******************************************************************************
**
** Function:        nfcManager_doGetLastError
**
** Description:     Get the last error code.
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         Last error code.
**
*******************************************************************************/
static jint nfcManager_doGetLastError(JNIEnv*, jobject) {
  LOG(DEBUG) << StringPrintf("%s: last error=%i", __func__, sLastError);
  return sLastError;
}

/*******************************************************************************
**
** Function:        nfcManager_doDeinitialize
**
** Description:     Turn off NFC.
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         True if ok.
**
*******************************************************************************/
static jboolean nfcManager_doDeinitialize(JNIEnv*, jobject) {
  LOG(DEBUG) << StringPrintf("%s: enter", __func__);

  sIsDisabling = true;

  if (!recovery_option || !sIsRecovering) {
    RoutingManager::getInstance().onNfccShutdown();
  }
  PowerSwitch::getInstance().initialize(PowerSwitch::UNKNOWN_LEVEL);
  HciEventManager::getInstance().finalize();

  if (sIsNfaEnabled) {
    SyncEventGuard guard(sNfaDisableEvent);
    tNFA_STATUS stat = NFA_Disable(TRUE /* graceful */);
    if (stat == NFA_STATUS_OK) {
      LOG(DEBUG) << StringPrintf("%s: wait for completion", __func__);
      sNfaDisableEvent.wait();  // wait for NFA command to finish
    } else {
      LOG(ERROR) << StringPrintf("%s: fail disable; error=0x%X", __func__,
                                 stat);
    }
  }
  nativeNfcTag_abortWaits();
  NfcTag::getInstance().abort();
  sAbortConnlessWait = true;
  sIsNfaEnabled = false;
  sRoutingInitialized = false;
  sDiscoveryEnabled = false;
  sPollingEnabled = false;
  sIsDisabling = false;
  sP2pEnabled = false;
  sReaderModeEnabled = false;
  gActivated = false;
  sLfT3tMax = 0;

  {
    // unblock NFA_EnablePolling() and NFA_DisablePolling()
    SyncEventGuard guard(sNfaEnableDisablePollingEvent);
    sNfaEnableDisablePollingEvent.notifyOne();
  }

  NfcAdaptation& theInstance = NfcAdaptation::GetInstance();
  theInstance.Finalize();

  LOG(DEBUG) << StringPrintf("%s: exit", __func__);
  return JNI_TRUE;
}

/*******************************************************************************
**
** Function:        isPeerToPeer
**
** Description:     Whether the activation data indicates the peer supports
*NFC-DEP.
**                  activated: Activation data.
**
** Returns:         True if the peer supports NFC-DEP.
**
*******************************************************************************/
static bool isPeerToPeer(tNFA_ACTIVATED& activated) { return false; }

/*******************************************************************************
**
** Function:        isListenMode
**
** Description:     Indicates whether the activation data indicates it is
**                  listen mode.
**
** Returns:         True if this listen mode.
**
*******************************************************************************/
static bool isListenMode(tNFA_ACTIVATED& activated) {
  return (
      (NFC_DISCOVERY_TYPE_LISTEN_A ==
       activated.activate_ntf.rf_tech_param.mode) ||
      (NFC_DISCOVERY_TYPE_LISTEN_B ==
       activated.activate_ntf.rf_tech_param.mode) ||
      (NFC_DISCOVERY_TYPE_LISTEN_F ==
       activated.activate_ntf.rf_tech_param.mode) ||
      (NFC_DISCOVERY_TYPE_LISTEN_A_ACTIVE ==
       activated.activate_ntf.rf_tech_param.mode) ||
      (NFC_DISCOVERY_TYPE_LISTEN_F_ACTIVE ==
       activated.activate_ntf.rf_tech_param.mode) ||
      (NFC_DISCOVERY_TYPE_LISTEN_ISO15693 ==
       activated.activate_ntf.rf_tech_param.mode) ||
      (NFC_DISCOVERY_TYPE_LISTEN_B_PRIME ==
       activated.activate_ntf.rf_tech_param.mode) ||
      (NFC_INTERFACE_EE_DIRECT_RF == activated.activate_ntf.intf_param.type));
}

/*******************************************************************************
**
** Function:        nfcManager_doAbort
**
** Description:     Not used.
**
** Returns:         None
**
*******************************************************************************/
static void nfcManager_doAbort(JNIEnv* e, jobject, jstring msg) {
  ScopedUtfChars message = {e, msg};
  e->FatalError(message.c_str());
  abort();  // <-- Unreachable
}

/*******************************************************************************
**
** Function:        nfcManager_doDownload
**
** Description:     Download firmware patch files.  Do not turn on NFC.
**
** Returns:         True if ok.
**
*******************************************************************************/
static jboolean nfcManager_doDownload(JNIEnv*, jobject) {
  LOG(DEBUG) << StringPrintf("%s: enter", __func__);
  NfcAdaptation& theInstance = NfcAdaptation::GetInstance();
  bool result = JNI_FALSE;
  theInstance.Initialize();  // start GKI, NCI task, NFC task
  result = theInstance.DownloadFirmware();
  theInstance.Finalize();
  LOG(DEBUG) << StringPrintf("%s: exit", __func__);
  return result;
}

/*******************************************************************************
**
** Function:        nfcManager_doResetTimeouts
**
** Description:     Not used.
**
** Returns:         None
**
*******************************************************************************/
static void nfcManager_doResetTimeouts(JNIEnv*, jobject) {
  LOG(DEBUG) << StringPrintf("%s", __func__);
  NfcTag::getInstance().resetAllTransceiveTimeouts();
}

/*******************************************************************************
**
** Function:        nfcManager_doSetTimeout
**
** Description:     Set timeout value.
**                  e: JVM environment.
**                  o: Java object.
**                  tech: technology ID.
**                  timeout: Timeout value.
**
** Returns:         True if ok.
**
*******************************************************************************/
static bool nfcManager_doSetTimeout(JNIEnv*, jobject, jint tech, jint timeout) {
  if (timeout <= 0) {
    LOG(ERROR) << StringPrintf("%s: Timeout must be positive.", __func__);
    return false;
  }
  LOG(DEBUG) << StringPrintf("%s: tech=%d, timeout=%d", __func__, tech,
                             timeout);
  NfcTag::getInstance().setTransceiveTimeout(tech, timeout);
  return true;
}

/*******************************************************************************
**
** Function:        nfcManager_doGetTimeout
**
** Description:     Get timeout value.
**                  e: JVM environment.
**                  o: Java object.
**                  tech: technology ID.
**
** Returns:         Timeout value.
**
*******************************************************************************/
static jint nfcManager_doGetTimeout(JNIEnv*, jobject, jint tech) {
  int timeout = NfcTag::getInstance().getTransceiveTimeout(tech);
  LOG(DEBUG) << StringPrintf("%s: tech=%d, timeout=%d", __func__, tech,
                             timeout);
  return timeout;
}

/*******************************************************************************
**
** Function:        nfcManager_doDump
**
** Description:     Get libnfc-nci dump
**                  e: JVM environment.
**                  obj: Java object.
**                  fdobj: File descriptor to be used
**
** Returns:         Void
**
*******************************************************************************/
static void nfcManager_doDump(JNIEnv* e, jobject obj, jobject fdobj) {
  int fd = jniGetFDFromFileDescriptor(e, fdobj);
  if (fd < 0) return;

  NfcAdaptation& theInstance = NfcAdaptation::GetInstance();
  theInstance.Dump(fd);
}

static jint nfcManager_doGetNciVersion(JNIEnv*, jobject) {
  return NFC_GetNCIVersion();
}

static void nfcManager_doSetScreenState(JNIEnv* e, jobject o,
                                        jint screen_state_mask,
                                        jboolean alwaysPoll) {
  tNFA_STATUS status = NFA_STATUS_OK;
  uint8_t state = (screen_state_mask & NFA_SCREEN_STATE_MASK);
  uint8_t discovry_param =
      NCI_LISTEN_DH_NFCEE_ENABLE_MASK | NCI_POLLING_DH_ENABLE_MASK;
  sIsAlwaysPolling = alwaysPoll;

  LOG(DEBUG) << StringPrintf(
      "%s: state = %d prevScreenState= %d, discovry_param = %d", __FUNCTION__,
      state, prevScreenState, discovry_param);

  if (prevScreenState == state) {
    LOG(DEBUG) << StringPrintf(
        "New screen state is same as previous state. No action taken");
    return;
  }

  if (sIsDisabling || !sIsNfaEnabled ||
      (NFC_GetNCIVersion() != NCI_VERSION_2_0)) {
    prevScreenState = state;
    return;
  }

  // skip remaining SetScreenState tasks when trying to silent recover NFCC
  if (recovery_option && sIsRecovering) {
    prevScreenState = state;
    return;
  }

  if (prevScreenState == NFA_SCREEN_STATE_OFF_LOCKED ||
      prevScreenState == NFA_SCREEN_STATE_OFF_UNLOCKED ||
      prevScreenState == NFA_SCREEN_STATE_ON_LOCKED) {
    SyncEventGuard guard(sNfaSetPowerSubState);
    status = NFA_SetPowerSubStateForScreenState(state);
    if (status != NFA_STATUS_OK) {
      LOG(ERROR) << StringPrintf("%s: fail enable SetScreenState; error=0x%X",
                                 __FUNCTION__, status);
      return;
    } else {
      sNfaSetPowerSubState.wait();
    }
  }

  // skip remaining SetScreenState tasks when trying to silent recover NFCC
  if (recovery_option && sIsRecovering) {
    prevScreenState = state;
    return;
  }

  if (state == NFA_SCREEN_STATE_OFF_LOCKED ||
      state == NFA_SCREEN_STATE_OFF_UNLOCKED) {
    // disable poll and enable listen on DH 0x00
    discovry_param =
        NCI_POLLING_DH_DISABLE_MASK | NCI_LISTEN_DH_NFCEE_ENABLE_MASK;
  }

  if (state == NFA_SCREEN_STATE_ON_LOCKED) {
    // disable poll and enable listen on DH 0x00
    discovry_param =
        (screen_state_mask & NFA_SCREEN_POLLING_TAG_MASK)
            ? (NCI_LISTEN_DH_NFCEE_ENABLE_MASK | NCI_POLLING_DH_ENABLE_MASK)
            : (NCI_POLLING_DH_DISABLE_MASK | NCI_LISTEN_DH_NFCEE_ENABLE_MASK);
  }

  if (state == NFA_SCREEN_STATE_ON_UNLOCKED) {
    // enable both poll and listen on DH 0x01
    discovry_param =
        NCI_LISTEN_DH_NFCEE_ENABLE_MASK | NCI_POLLING_DH_ENABLE_MASK;
  }

  if (!sIsAlwaysPolling) {
    SyncEventGuard guard(gNfaSetConfigEvent);
    status = NFA_SetConfig(NCI_PARAM_ID_CON_DISCOVERY_PARAM,
                           NCI_PARAM_LEN_CON_DISCOVERY_PARAM, &discovry_param);
    if (status == NFA_STATUS_OK) {
      gNfaSetConfigEvent.wait();
    } else {
      LOG(ERROR) << StringPrintf("%s: Failed to update CON_DISCOVER_PARAM",
                                 __FUNCTION__);
      return;
    }
  }
  // skip remaining SetScreenState tasks when trying to silent recover NFCC
  if (recovery_option && sIsRecovering) {
    prevScreenState = state;
    return;
  }

  if (prevScreenState == NFA_SCREEN_STATE_ON_UNLOCKED) {
    SyncEventGuard guard(sNfaSetPowerSubState);
    status = NFA_SetPowerSubStateForScreenState(state);
    if (status != NFA_STATUS_OK) {
      LOG(ERROR) << StringPrintf("%s: fail enable SetScreenState; error=0x%X",
                                 __FUNCTION__, status);
    } else {
      sNfaSetPowerSubState.wait();
    }
  }

  // skip remaining SetScreenState tasks when trying to silent recover NFCC
  if (recovery_option && sIsRecovering) {
    prevScreenState = state;
    return;
  }

  if ((state == NFA_SCREEN_STATE_OFF_LOCKED ||
       state == NFA_SCREEN_STATE_OFF_UNLOCKED) &&
      (prevScreenState == NFA_SCREEN_STATE_ON_UNLOCKED ||
       prevScreenState == NFA_SCREEN_STATE_ON_LOCKED) &&
      (!sP2pActive) && (!sSeRfActive)) {
    // screen turns off, disconnect tag if connected
    nativeNfcTag_doDisconnect(NULL, NULL);
  }

  prevScreenState = state;
}
/*******************************************************************************
**
** Function:        nfcManager_doSetP2pInitiatorModes
**
** Description:     Set P2P initiator's activation modes.
**                  e: JVM environment.
**                  o: Java object.
**                  modes: Active and/or passive modes.  The values are
*specified
**                          in external/libnfc-nxp/inc/phNfcTypes.h.  See
**                          enum phNfc_eP2PMode_t.
**
** Returns:         None.
**
*******************************************************************************/
static void nfcManager_doSetP2pInitiatorModes(JNIEnv* e, jobject o,
                                              jint modes) {
  LOG(DEBUG) << StringPrintf("%s: modes=0x%X", __func__, modes);
  struct nfc_jni_native_data* nat = getNative(e, o);

  if (nat == NULL) {
    LOG(ERROR) << StringPrintf("nat is null");
    return;
  }
  tNFA_TECHNOLOGY_MASK mask = 0;
  if (modes & 0x01) mask |= NFA_TECHNOLOGY_MASK_A;
  if (modes & 0x02) mask |= NFA_TECHNOLOGY_MASK_F;
  if (modes & 0x04) mask |= NFA_TECHNOLOGY_MASK_F;
  if (modes & 0x08) mask |= NFA_TECHNOLOGY_MASK_A_ACTIVE;
  if (modes & 0x10) mask |= NFA_TECHNOLOGY_MASK_F_ACTIVE;
  if (modes & 0x20) mask |= NFA_TECHNOLOGY_MASK_F_ACTIVE;
  nat->tech_mask = mask;
}

/*******************************************************************************
**
** Function:        nfcManager_doSetP2pTargetModes
**
** Description:     Set P2P target's activation modes.
**                  e: JVM environment.
**                  o: Java object.
**                  modes: Active and/or passive modes.
**
** Returns:         None.
**
*******************************************************************************/
static void nfcManager_doSetP2pTargetModes(JNIEnv*, jobject, jint modes) {
  LOG(DEBUG) << StringPrintf("%s: modes=0x%X", __func__, modes);
}

static void nfcManager_doEnableScreenOffSuspend(JNIEnv* e, jobject o) {
  PowerSwitch::getInstance().setScreenOffPowerState(
      PowerSwitch::POWER_STATE_FULL);
}

static void nfcManager_doDisableScreenOffSuspend(JNIEnv* e, jobject o) {
  PowerSwitch::getInstance().setScreenOffPowerState(
      PowerSwitch::POWER_STATE_OFF);
}

/*******************************************************************************
**
** Function:        nfcManager_getIsoDepMaxTransceiveLength
**
** Description:     Get maximum ISO DEP Transceive Length supported by the NFC
**                  chip. Returns default 261 bytes if the property is not set.
**
** Returns:         max value.
**
*******************************************************************************/
static jint nfcManager_getIsoDepMaxTransceiveLength(JNIEnv*, jobject) {
  /* Check if extended APDU is supported by the chip.
   * If not, default value is returned.
   * The maximum length of a default IsoDep frame consists of:
   * CLA, INS, P1, P2, LC, LE + 255 payload bytes = 261 bytes
   */
  return NfcConfig::getUnsigned(NAME_ISO_DEP_MAX_TRANSCEIVE, 261);
}

/*******************************************************************************
 **
 ** Function:        nfcManager_getAidTableSize
 ** Description:     Get the maximum supported size for AID routing table.
 **
 **                  e: JVM environment.
 **                  o: Java object.
 **
 *******************************************************************************/
static jint nfcManager_getAidTableSize(JNIEnv*, jobject) {
  return NFA_GetAidTableSize();
}

/*******************************************************************************
**
** Function:        nfcManager_IsMultiTag
**
** Description:     Check if it a multi tag case.
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         None.
**
*******************************************************************************/
static bool nfcManager_isMultiTag() {
  LOG(DEBUG) << StringPrintf("%s: enter mNumRfDiscId = %d", __func__,
                             NfcTag::getInstance().mNumRfDiscId);
  bool status = false;
  if (NfcTag::getInstance().mNumRfDiscId > 1) status = true;
  LOG(DEBUG) << StringPrintf("isMultiTag = %d", status);
  return status;
}

/*******************************************************************************
**
** Function:        nfcManager_doStartStopPolling
**
** Description:     Start or stop NFC RF polling
**                  e: JVM environment.
**                  o: Java object.
**                  start: start or stop RF polling
**
** Returns:         None
**
*******************************************************************************/
static void nfcManager_doStartStopPolling(JNIEnv* e, jobject o,
                                          jboolean start) {
  startStopPolling(start);
}

/*******************************************************************************
**
** Function:        nfcManager_doSetNfcSecure
**
** Description:     Set NfcSecure enable/disable.
**                  e: JVM environment.
**                  o: Java object.
**                  enable: Sets true/false to enable/disable NfcSecure
**                  It only updates the routing table cache without commit to
**                  NFCC.
**
** Returns:         True always
**
*******************************************************************************/
static jboolean nfcManager_doSetNfcSecure(JNIEnv* e, jobject o,
                                          jboolean enable) {
  RoutingManager& routingManager = RoutingManager::getInstance();
  routingManager.setNfcSecure(enable);
  if (sRoutingInitialized) {
    routingManager.disableRoutingToHost();
    routingManager.updateRoutingTable();
    routingManager.enableRoutingToHost();
  }
  return true;
}

static jstring nfcManager_doGetNfaStorageDir(JNIEnv* e, jobject o) {
  string nfaStorageDir = NfcConfig::getString(NAME_NFA_STORAGE, "/data/nfc");
  return e->NewStringUTF(nfaStorageDir.c_str());
}

static void nfcManager_doSetNfceePowerAndLinkCtrl(JNIEnv* e, jobject o,
                                                  jboolean enable) {
  RoutingManager& routingManager = RoutingManager::getInstance();
  if (enable) {
    routingManager.eeSetPwrAndLinkCtrl((uint8_t)nfcee_power_and_link_conf);
  } else {
    routingManager.eeSetPwrAndLinkCtrl(0);
  }
}

/*******************************************************************************
**
** Function:        nfcManager_doGetMaxRoutingTableSize
**
** Description:     Retrieve the max routing table size from cache
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         Max Routing Table size
**
*******************************************************************************/
static jint nfcManager_doGetMaxRoutingTableSize(JNIEnv* e, jobject o) {
  return lmrt_get_max_size();
}

/*******************************************************************************
**
** Function:        nfcManager_doGetRoutingTable
**
** Description:     Retrieve the committed listen mode routing configuration
**                  e: JVM environment.
**                  o: Java object.
**
** Returns:         Committed listen mode routing configuration
**
*******************************************************************************/
static jbyteArray nfcManager_doGetRoutingTable(JNIEnv* e, jobject o) {
  std::vector<uint8_t>* routingTable = lmrt_get_tlvs();

  CHECK(e);
  jbyteArray rtJavaArray = e->NewByteArray((*routingTable).size());
  CHECK(rtJavaArray);
  e->SetByteArrayRegion(rtJavaArray, 0, (*routingTable).size(),
                        (jbyte*)&(*routingTable)[0]);

  return rtJavaArray;
}

static void nfcManager_clearRoutingEntry(JNIEnv* e, jobject o,
                                         jint clearFlags) {
  LOG(DEBUG) << StringPrintf("%s: clearFlags=0x%X", __func__, clearFlags);
  RoutingManager::getInstance().disableRoutingToHost();
  RoutingManager::getInstance().clearRoutingEntry(clearFlags);
}

static void nfcManager_updateIsoDepProtocolRoute(JNIEnv* e, jobject o,
                                                 jint route) {
  LOG(DEBUG) << StringPrintf("%s: clearFlags=0x%X", __func__, route);
  RoutingManager::getInstance().updateIsoDepProtocolRoute(route);
}

static void nfcManager_updateTechnologyABRoute(JNIEnv* e, jobject o,
                                               jint route) {
  LOG(DEBUG) << StringPrintf("%s: clearFlags=0x%X", __func__, route);
  RoutingManager::getInstance().updateTechnologyABRoute(route);
}

/*******************************************************************************
**
** Function:        nfcManager_setDiscoveryTech
**
** Description:     Temporarily changes the RF parameter
**                  pollTech: RF tech parameters for poll mode
**                  listenTech: RF tech parameters for listen mode
**
** Returns:         None.
**
*******************************************************************************/
static void nfcManager_setDiscoveryTech(JNIEnv* e, jobject o, jint pollTech,
                                        jint listenTech) {
  tNFA_STATUS nfaStat;
  bool isRevertPoll = false;
  bool isRevertListen = false;
  LOG(DEBUG) << StringPrintf("%s  pollTech = 0x%x, listenTech = 0x%x", __func__,
                             pollTech, listenTech);

  if (pollTech < 0) isRevertPoll = true;
  if (listenTech < 0) isRevertListen = true;

  nativeNfcTag_acquireRfInterfaceMutexLock();
  SyncEventGuard guard(sNfaEnableDisablePollingEvent);

  nfaStat = NFA_ChangeDiscoveryTech(pollTech, listenTech, isRevertPoll,
                                    isRevertListen);

  if (nfaStat == NFA_STATUS_OK) {
    // wait for NFA_LISTEN_DISABLED_EVT
    sNfaEnableDisablePollingEvent.wait();
  } else {
    LOG(ERROR) << StringPrintf("%s: fail disable polling; error=0x%X", __func__,
                               nfaStat);
  }
  nativeNfcTag_releaseRfInterfaceMutexLock();
}

/*******************************************************************************
**
** Function:        nfcManager_resetDiscoveryTech
**
** Description:     Restores the RF tech to the state before
**                  nfcManager_setDiscoveryTech was called
**
** Returns:         None.
**
*******************************************************************************/
static void nfcManager_resetDiscoveryTech(JNIEnv* e, jobject o) {
  tNFA_STATUS nfaStat;
  LOG(DEBUG) << StringPrintf("%s : enter", __func__);

  nativeNfcTag_acquireRfInterfaceMutexLock();
  SyncEventGuard guard(sNfaEnableDisablePollingEvent);

  nfaStat = NFA_ChangeDiscoveryTech(0xFF, 0xFF, true, true);

  if (nfaStat == NFA_STATUS_OK) {
    // wait for NFA_LISTEN_DISABLED_EVT
    sNfaEnableDisablePollingEvent.wait();
  } else {
    LOG(ERROR) << StringPrintf("%s: fail disable polling; error=0x%X", __func__,
                               nfaStat);
  }
  nativeNfcTag_releaseRfInterfaceMutexLock();
}
static jobject nfcManager_nativeSendRawVendorCmd(JNIEnv* env, jobject o,
                                                 jint mt, jint gid, jint oid,
                                                 jbyteArray payload) {
  LOG(DEBUG) << StringPrintf("%s : enter", __func__);
  ScopedByteArrayRO payloaBytes(env, payload);
  ScopedLocalRef<jclass> cls(env,
                             env->FindClass(gNfcVendorNciResponseClassName));
  jmethodID responseConstructor =
      env->GetMethodID(cls.get(), "<init>", "(BII[B)V");

  jbyte mStatus = NFA_STATUS_FAILED;
  jint resGid = 0;
  jint resOid = 0;
  jbyteArray resPayload = nullptr;

  sRawVendorCmdResponse.clear();

  std::vector<uint8_t> command;
  command.push_back((uint8_t)((mt << NCI_MT_SHIFT) | gid));
  command.push_back((uint8_t)oid);
  if (payloaBytes.size() > 0) {
    command.push_back((uint8_t)payloaBytes.size());
    command.insert(command.end(), &payloaBytes[0],
                   &payloaBytes[payloaBytes.size()]);
  } else {
    return env->NewObject(cls.get(), responseConstructor, mStatus, resGid,
                          resOid, resPayload);
  }

  SyncEventGuard guard(gSendRawVsCmdEvent);
  mStatus = NFA_SendRawVsCommand(command.size(), command.data(),
                                 sendRawVsCmdCallback);
  if (mStatus == NFA_STATUS_OK) {
    if (gSendRawVsCmdEvent.wait(2000) == false) {
      mStatus = NFA_STATUS_FAILED;
      LOG(ERROR) << StringPrintf("%s: timeout ", __func__);
    }

    if (mStatus == NFA_STATUS_OK && sRawVendorCmdResponse.size() > 2) {
      resGid = sRawVendorCmdResponse[0] & NCI_GID_MASK;
      resOid = sRawVendorCmdResponse[1];
      const jsize len = static_cast<jsize>(sRawVendorCmdResponse[2]);
      if (sRawVendorCmdResponse.size() >= (sRawVendorCmdResponse[2] + 3)) {
        resPayload = env->NewByteArray(len);
        std::vector<uint8_t> payloadVec(sRawVendorCmdResponse.begin() + 3,
                                        sRawVendorCmdResponse.end());
        env->SetByteArrayRegion(
            resPayload, 0, len,
            reinterpret_cast<const jbyte*>(payloadVec.data()));
      } else {
        mStatus = NFA_STATUS_FAILED;
        LOG(ERROR) << StringPrintf("%s: invalid payload data", __func__);
      }
    } else {
      mStatus = NFA_STATUS_FAILED;
    }
  }

  LOG(DEBUG) << StringPrintf("%s : exit", __func__);
  return env->NewObject(cls.get(), responseConstructor, mStatus, resGid, resOid,
                        resPayload);
}

static void sendRawVsCmdCallback(uint8_t event, uint16_t param_len,
                                 uint8_t* p_param) {
  sRawVendorCmdResponse = std::vector<uint8_t>(p_param, p_param + param_len);

  SyncEventGuard guard(gSendRawVsCmdEvent);
  gSendRawVsCmdEvent.notifyOne();
} /* namespace android */

/*****************************************************************************
**
** JNI functions for android-4.0.1_r1
**
*****************************************************************************/
static JNINativeMethod gMethods[] = {
    {"doDownload", "()Z", (void*)nfcManager_doDownload},

    {"initializeNativeStructure", "()Z", (void*)nfcManager_initNativeStruc},

    {"doInitialize", "()Z", (void*)nfcManager_doInitialize},

    {"doDeinitialize", "()Z", (void*)nfcManager_doDeinitialize},

    {"sendRawFrame", "([B)Z", (void*)nfcManager_sendRawFrame},

    {"routeAid", "([BIII)Z", (void*)nfcManager_routeAid},

    {"unrouteAid", "([B)Z", (void*)nfcManager_unrouteAid},

    {"commitRouting", "()Z", (void*)nfcManager_commitRouting},

    {"doRegisterT3tIdentifier", "([B)I",
     (void*)nfcManager_doRegisterT3tIdentifier},

    {"doDeregisterT3tIdentifier", "(I)V",
     (void*)nfcManager_doDeregisterT3tIdentifier},

    {"getLfT3tMax", "()I", (void*)nfcManager_getLfT3tMax},

    {"doEnableDiscovery", "(IZZZZZ)V", (void*)nfcManager_enableDiscovery},

    {"doStartStopPolling", "(Z)V", (void*)nfcManager_doStartStopPolling},

    {"doGetLastError", "()I", (void*)nfcManager_doGetLastError},

    {"disableDiscovery", "()V", (void*)nfcManager_disableDiscovery},

    {"doSetTimeout", "(II)Z", (void*)nfcManager_doSetTimeout},

    {"doGetTimeout", "(I)I", (void*)nfcManager_doGetTimeout},

    {"doResetTimeouts", "()V", (void*)nfcManager_doResetTimeouts},

    {"doAbort", "(Ljava/lang/String;)V", (void*)nfcManager_doAbort},

    {"doSetP2pInitiatorModes", "(I)V",
     (void*)nfcManager_doSetP2pInitiatorModes},

    {"doSetP2pTargetModes", "(I)V", (void*)nfcManager_doSetP2pTargetModes},

    {"doEnableScreenOffSuspend", "()V",
     (void*)nfcManager_doEnableScreenOffSuspend},

    {"doSetScreenState", "(IZ)V", (void*)nfcManager_doSetScreenState},

    {"doDisableScreenOffSuspend", "()V",
     (void*)nfcManager_doDisableScreenOffSuspend},

    {"doDump", "(Ljava/io/FileDescriptor;)V", (void*)nfcManager_doDump},

    {"getNciVersion", "()I", (void*)nfcManager_doGetNciVersion},
    {"doEnableDtaMode", "()V", (void*)nfcManager_doEnableDtaMode},
    {"doDisableDtaMode", "()V", (void*)nfcManager_doDisableDtaMode},
    {"doFactoryReset", "()V", (void*)nfcManager_doFactoryReset},
    {"doShutdown", "()V", (void*)nfcManager_doShutdown},

    {"getIsoDepMaxTransceiveLength", "()I",
     (void*)nfcManager_getIsoDepMaxTransceiveLength},

    {"getAidTableSize", "()I", (void*)nfcManager_getAidTableSize},

    {"doSetNfcSecure", "(Z)Z", (void*)nfcManager_doSetNfcSecure},

    {"getNfaStorageDir", "()Ljava/lang/String;",
     (void*)nfcManager_doGetNfaStorageDir},

    {"doSetNfceePowerAndLinkCtrl", "(Z)V",
     (void*)nfcManager_doSetNfceePowerAndLinkCtrl},

    {"doSetPowerSavingMode", "(Z)Z", (void*)nfcManager_doSetPowerSavingMode},

    {"getRoutingTable", "()[B", (void*)nfcManager_doGetRoutingTable},

    {"getMaxRoutingTableSize", "()I",
     (void*)nfcManager_doGetMaxRoutingTableSize},

    {"setObserveMode", "(Z)Z", (void*)nfcManager_setObserveMode},

    {"isObserveModeEnabled", "()Z", (void*)nfcManager_isObserveModeEnabled},

    {"isMultiTag", "()Z", (void*)nfcManager_isMultiTag},

    {"clearRoutingEntry", "(I)V", (void*)nfcManager_clearRoutingEntry},

    {"setIsoDepProtocolRoute", "(I)V",
     (void*)nfcManager_updateIsoDepProtocolRoute},

    {"setTechnologyABRoute", "(I)V", (void*)nfcManager_updateTechnologyABRoute},

    {"setDiscoveryTech", "(II)V", (void*)nfcManager_setDiscoveryTech},

    {"resetDiscoveryTech", "()V", (void*)nfcManager_resetDiscoveryTech},
    {"nativeSendRawVendorCmd", "(III[B)Lcom/android/nfc/NfcVendorNciResponse;",
     (void*)nfcManager_nativeSendRawVendorCmd},

    {"getProprietaryCaps", "()[B", (void*)nfcManager_getProprietaryCaps},
};

/*******************************************************************************
**
** Function:        register_com_android_nfc_NativeNfcManager
**
** Description:     Regisgter JNI functions with Java Virtual Machine.
**                  e: Environment of JVM.
**
** Returns:         Status of registration.
**
*******************************************************************************/
int register_com_android_nfc_NativeNfcManager(JNIEnv* e) {
  LOG(DEBUG) << StringPrintf("%s: enter", __func__);
  PowerSwitch::getInstance().initialize(PowerSwitch::UNKNOWN_LEVEL);
  LOG(DEBUG) << StringPrintf("%s: exit", __func__);
  return jniRegisterNativeMethods(e, gNativeNfcManagerClassName, gMethods,
                                  NELEM(gMethods));
}

/*******************************************************************************
**
** Function:        startRfDiscovery
**
** Description:     Ask stack to start polling and listening for devices.
**                  isStart: Whether to start.
**
** Returns:         None
**
*******************************************************************************/
void startRfDiscovery(bool isStart) {
  tNFA_STATUS status = NFA_STATUS_FAILED;

  LOG(DEBUG) << StringPrintf("%s: is start=%d", __func__, isStart);
  nativeNfcTag_acquireRfInterfaceMutexLock();
  SyncEventGuard guard(sNfaEnableDisablePollingEvent);
  status = isStart ? NFA_StartRfDiscovery() : NFA_StopRfDiscovery();
  if (status == NFA_STATUS_OK) {
    sNfaEnableDisablePollingEvent.wait();  // wait for NFA_RF_DISCOVERY_xxxx_EVT
    sRfEnabled = isStart;
  } else {
    LOG(ERROR) << StringPrintf(
        "%s: Failed to start/stop RF discovery; error=0x%X", __func__, status);
  }
  nativeNfcTag_releaseRfInterfaceMutexLock();
}

/*******************************************************************************
**
** Function:        isDiscoveryStarted
**
** Description:     Indicates whether the discovery is started.
**
** Returns:         True if discovery is started
**
*******************************************************************************/
bool isDiscoveryStarted() { return sRfEnabled; }

/*******************************************************************************
**
** Function:        doStartupConfig
**
** Description:     Configure the NFC controller.
**
** Returns:         None
**
*******************************************************************************/
void doStartupConfig() {
  // configure RF polling frequency for each technology
  static tNFA_DM_DISC_FREQ_CFG nfa_dm_disc_freq_cfg;
  // values in the polling_frequency[] map to members of nfa_dm_disc_freq_cfg
  std::vector<uint8_t> polling_frequency;
  if (NfcConfig::hasKey(NAME_POLL_FREQUENCY))
    polling_frequency = NfcConfig::getBytes(NAME_POLL_FREQUENCY);
  if (polling_frequency.size() == 8) {
    LOG(DEBUG) << StringPrintf("%s: polling frequency", __func__);
    memset(&nfa_dm_disc_freq_cfg, 0, sizeof(nfa_dm_disc_freq_cfg));
    nfa_dm_disc_freq_cfg.pa = polling_frequency[0];
    nfa_dm_disc_freq_cfg.pb = polling_frequency[1];
    nfa_dm_disc_freq_cfg.pf = polling_frequency[2];
    nfa_dm_disc_freq_cfg.pi93 = polling_frequency[3];
    nfa_dm_disc_freq_cfg.pbp = polling_frequency[4];
    nfa_dm_disc_freq_cfg.pk = polling_frequency[5];
    nfa_dm_disc_freq_cfg.paa = polling_frequency[6];
    nfa_dm_disc_freq_cfg.pfa = polling_frequency[7];
    p_nfa_dm_rf_disc_freq_cfg = &nfa_dm_disc_freq_cfg;
  }

  // configure NFCC_CONFIG_CONTROL- NFCC allowed to manage RF configuration.
  nfcManager_configNfccConfigControl(true);

}

/*******************************************************************************
**
** Function:        nfcManager_isNfcActive
**
** Description:     Used externaly to determine if NFC is active or not.
**
** Returns:         'true' if the NFC stack is running, else 'false'.
**
*******************************************************************************/
bool nfcManager_isNfcActive() { return sIsNfaEnabled; }

/*******************************************************************************
**
** Function:        startStopPolling
**
** Description:     Start or stop polling.
**                  isStartPolling: true to start polling; false to stop
*polling.
**
** Returns:         None.
**
*******************************************************************************/
void startStopPolling(bool isStartPolling) {
  tNFA_STATUS status = NFA_STATUS_FAILED;
  uint8_t discovry_param = 0;
  LOG(DEBUG) << StringPrintf("%s: enter; isStart=%u", __func__, isStartPolling);

  if (NFC_GetNCIVersion() >= NCI_VERSION_2_0) {
    SyncEventGuard guard(gNfaSetConfigEvent);
    if (isStartPolling) {
      discovry_param =
          NCI_LISTEN_DH_NFCEE_ENABLE_MASK | NCI_POLLING_DH_ENABLE_MASK;
    } else {
      discovry_param =
          NCI_LISTEN_DH_NFCEE_ENABLE_MASK | NCI_POLLING_DH_DISABLE_MASK;
    }
    status = NFA_SetConfig(NCI_PARAM_ID_CON_DISCOVERY_PARAM,
                           NCI_PARAM_LEN_CON_DISCOVERY_PARAM, &discovry_param);
    if (status == NFA_STATUS_OK) {
      gNfaSetConfigEvent.wait();
    } else {
      LOG(ERROR) << StringPrintf("%s: Failed to update CON_DISCOVER_PARAM",
                                 __FUNCTION__);
    }
  } else {
    startRfDiscovery(false);
    if (isStartPolling)
      startPolling_rfDiscoveryDisabled(0);
    else
      stopPolling_rfDiscoveryDisabled();
    startRfDiscovery(true);
  }
  LOG(DEBUG) << StringPrintf("%s: exit", __func__);
}

static tNFA_STATUS startPolling_rfDiscoveryDisabled(
    tNFA_TECHNOLOGY_MASK tech_mask) {
  tNFA_STATUS stat = NFA_STATUS_FAILED;

  if (tech_mask == 0)
    tech_mask =
        NfcConfig::getUnsigned(NAME_POLLING_TECH_MASK, DEFAULT_TECH_MASK);

  nativeNfcTag_acquireRfInterfaceMutexLock();
  SyncEventGuard guard(sNfaEnableDisablePollingEvent);
  LOG(DEBUG) << StringPrintf("%s: enable polling", __func__);
  stat = NFA_EnablePolling(tech_mask);
  if (stat == NFA_STATUS_OK) {
    LOG(DEBUG) << StringPrintf("%s: wait for enable event", __func__);
    sPollingEnabled = true;
    sNfaEnableDisablePollingEvent.wait();  // wait for NFA_POLL_ENABLED_EVT
  } else {
    LOG(ERROR) << StringPrintf("%s: fail enable polling; error=0x%X", __func__,
                               stat);
  }
  nativeNfcTag_releaseRfInterfaceMutexLock();

  return stat;
}

static tNFA_STATUS stopPolling_rfDiscoveryDisabled() {
  tNFA_STATUS stat = NFA_STATUS_FAILED;

  nativeNfcTag_acquireRfInterfaceMutexLock();
  SyncEventGuard guard(sNfaEnableDisablePollingEvent);
  LOG(DEBUG) << StringPrintf("%s: disable polling", __func__);
  stat = NFA_DisablePolling();
  if (stat == NFA_STATUS_OK) {
    sPollingEnabled = false;
    sNfaEnableDisablePollingEvent.wait();  // wait for NFA_POLL_DISABLED_EVT
  } else {
    LOG(ERROR) << StringPrintf("%s: fail disable polling; error=0x%X", __func__,
                               stat);
  }
  nativeNfcTag_releaseRfInterfaceMutexLock();

  return stat;
}

static jboolean nfcManager_doSetPowerSavingMode(JNIEnv* e, jobject o,
                                                bool flag) {
  LOG(DEBUG) << StringPrintf("%s: enter; ", __func__);
  uint8_t cmd[] = {(NCI_MT_CMD << NCI_MT_SHIFT) | NCI_GID_PROP,
                   NCI_MSG_PROP_ANDROID, NCI_ANDROID_POWER_SAVING_PARAM_SIZE,
                   NCI_ANDROID_POWER_SAVING,
                   NCI_ANDROID_POWER_SAVING_PARAM_DISABLE};
  cmd[4] = flag ? NCI_ANDROID_POWER_SAVING_PARAM_ENABLE
                : NCI_ANDROID_POWER_SAVING_PARAM_DISABLE;

  SyncEventGuard guard(gNfaVsCommand);
  tNFA_STATUS status =
      NFA_SendRawVsCommand(sizeof(cmd), cmd, nfaSendRawVsCmdCallback);
  if (status == NFA_STATUS_OK) {
    gNfaVsCommand.wait();
  } else {
    LOG(ERROR) << StringPrintf("%s: Failed to set power-saving mode", __func__);
    gVSCmdStatus = NFA_STATUS_FAILED;
  }
  return gVSCmdStatus == NFA_STATUS_OK;
}

static jbyteArray nfcManager_getProprietaryCaps(JNIEnv* e, jobject o) {
  LOG(DEBUG) << StringPrintf("%s: enter; ", __func__);
  uint8_t cmd[] = {(NCI_MT_CMD << NCI_MT_SHIFT) | NCI_GID_PROP,
                   NCI_MSG_PROP_ANDROID, 0, NCI_ANDROID_GET_CAPS};
  SyncEventGuard guard(gNfaVsCommand);
  tNFA_STATUS status =
      NFA_SendRawVsCommand(sizeof(cmd), cmd, nfaSendRawVsCmdCallback);
  if (status == NFA_STATUS_OK) {
    gNfaVsCommand.wait();
  } else {
    LOG(ERROR) << StringPrintf("%s: Failed to get caps", __func__);
    gVSCmdStatus = NFA_STATUS_FAILED;
  }
  CHECK(e);
  jbyteArray rtJavaArray = e->NewByteArray(gCaps.size());
  CHECK(rtJavaArray);
  e->SetByteArrayRegion(rtJavaArray, 0, gCaps.size(), (jbyte*)gCaps.data());
  return rtJavaArray;
}

} /* namespace android */
