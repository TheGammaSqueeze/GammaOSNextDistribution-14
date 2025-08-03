/*
 *  Copyright (C) 2023 The Android Open Source Project.
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

#include "NativeWlcManager.h"

#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <cutils/properties.h>
#include <errno.h>
#include <nativehelper/JNIPlatformHelp.h>
#include <nativehelper/ScopedLocalRef.h>
#include <nativehelper/ScopedPrimitiveArray.h>
#include <nativehelper/ScopedUtfChars.h>
#include <semaphore.h>

#include "JavaClassConstants.h"
#include "NfcJniUtil.h"
#include "SyncEvent.h"
#include "nfa_api.h"

using android::base::StringPrintf;

/*****************************************************************************
**
** private variables and functions
**
*****************************************************************************/

static void nfaWlcManagementCallback(tNFA_WLC_EVT wlcEvent,
                                     tNFA_WLC_EVT_DATA* eventData);

static SyncEvent sNfaWlcEnableEvent;  // event for NFA_WlcStart()
static SyncEvent sNfaWlcEvent;        // event for NFA_Wlc...()

static bool sIsWlcpStarted = false;

Mutex gMutexWlc;

const JNINativeMethod NativeWlcManager::sMethods[] = {

    {"startWlcPowerTransfer", "(II)Z",
     (void*)NativeWlcManager::com_android_nfc_wlc_chargeWlcListener},
    {"enableWlc", "(I)Z",
     (void*)NativeWlcManager::com_android_nfc_wlc_startWlcP},

};

/*******************************************************************************
**
** Function:        NativeWlcManager
**
** Description:     Initialize member variables.
**
** Returns:         None
**
*******************************************************************************/
NativeWlcManager::NativeWlcManager()
    : mNativeData(NULL), mIsWlcEnabled(false) {}

/*******************************************************************************
**
** Function:        ~NativeWlcManager
**
** Description:     Release all resources.
**
** Returns:         None
**
*******************************************************************************/
NativeWlcManager::~NativeWlcManager() {}

/*******************************************************************************
**
** Function:        getInstance
**
** Description:     Get a reference to the singleton NativeWlcManager object.
**
** Returns:         Reference to NativeWlcManager object.
**
*******************************************************************************/
NativeWlcManager& NativeWlcManager::getInstance() {
  static NativeWlcManager manager;
  return manager;
}

/*******************************************************************************
**
** Function:        initialize
**
** Description:     Reset member variables.
**                  native: Native data.
**
** Returns:         None
**
*******************************************************************************/
void NativeWlcManager::initialize(nfc_jni_native_data* native) {
  tNFA_STATUS stat = NFA_STATUS_FAILED;

  LOG(DEBUG) << StringPrintf("%s: enter", __func__);

  mNativeData = native;
  mIsWlcEnabled = false;

  SyncEventGuard g(sNfaWlcEnableEvent);
  // TODO: only do it once at NfcManager init if WLC allowed
  stat = NFA_WlcEnable(nfaWlcManagementCallback);

  if (stat == NFA_STATUS_OK) {
    // TODO: get enable result to stop directly if failed
    sNfaWlcEnableEvent.wait();
    LOG(DEBUG) << StringPrintf("%s: enable Wlc module success", __func__);
  } else {
    LOG(ERROR) << StringPrintf("%s: fail enable Wlc module; error=0x%X",
                               __func__, stat);
  }
}

/*******************************************************************************
**
** Function:        notifyWlcCompletion
**
** Description:     Notify end of WLC procedure.
**                  wpt_end_condition: End condition from NFCC.
**
** Returns:         None
**
*******************************************************************************/
void NativeWlcManager::notifyWlcCompletion(uint8_t wpt_end_condition) {
  JNIEnv* e = NULL;
  ScopedAttach attach(mNativeData->vm, &e);
  if (e == NULL) {
    LOG(ERROR) << "jni env is null";
    return;
  }

  LOG(DEBUG) << StringPrintf("%s: ", __func__);

  e->CallVoidMethod(mNativeData->manager,
                    android::gCachedNfcManagerNotifyWlcStopped,
                    (int)wpt_end_condition);
  if (e->ExceptionCheck()) {
    e->ExceptionClear();
    LOG(ERROR) << StringPrintf("fail notify");
  }
}

/*******************************************************************************
**
** Function:        nfaWlcManagementCallback
**
** Description:     Receive Wlc management events from stack.
**                  wlcEvent: Wlc-management event ID.
**                  eventData: Data associated with event ID.
**
** Returns:         None
**
*******************************************************************************/
void nfaWlcManagementCallback(tNFA_WLC_EVT wlcEvent,
                              tNFA_WLC_EVT_DATA* eventData) {
  LOG(DEBUG) << StringPrintf("%s: enter; event=0x%X", __func__, wlcEvent);

  switch (wlcEvent) {
    case NFA_WLC_ENABLE_RESULT_EVT:  // whether WLC module enabled
    {
      LOG(DEBUG) << StringPrintf("%s: NFA_WLC_ENABLE_RESULT_EVT: status = %u",
                                 __func__, eventData->status);

      SyncEventGuard guard(sNfaWlcEnableEvent);
      sNfaWlcEnableEvent.notifyOne();
    } break;

    case NFA_WLC_START_RESULT_EVT:  // whether WLCP successfully started
    {
      LOG(DEBUG) << StringPrintf("%s: NFA_WLC_START_RESULT_EVT: status = %u",
                                 __func__, eventData->status);

      sIsWlcpStarted = eventData->status == NFA_STATUS_OK;
      SyncEventGuard guard(sNfaWlcEvent);
      sNfaWlcEvent.notifyOne();
    } break;

    case NFA_WLC_START_WPT_RESULT_EVT:  // whether WLC Power Transfer
                                        // successfully started
    {
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_WLC_START_WPT_RESULT_EVT: status = %u", __func__,
          eventData->status);

      SyncEventGuard guard(sNfaWlcEvent);
      sNfaWlcEvent.notifyOne();
    } break;

    case NFA_WLC_CHARGING_RESULT_EVT:  // notify completion of power transfer
                                       // phase
    {
      LOG(DEBUG) << StringPrintf(
          "%s: NFA_WLC_CHARGING_RESULT_EVT: End Condition = 0x%x", __func__,
          eventData->wpt_end_cdt);

      /* Return WPT end condition to service */
      NativeWlcManager::getInstance().notifyWlcCompletion(
          eventData->wpt_end_cdt);
    } break;

    default:
      LOG(DEBUG) << StringPrintf("%s: unhandled event", __func__);
      break;
  }
}

/*******************************************************************************
**
** Function:        com_android_nfc_wlc_startWlcP
**
** Description:     Start WLC Poller
**                  e: JVM environment.
**                  mode: WLC mode
**
** Returns:         True if WLCP started done
**
*******************************************************************************/
jboolean NativeWlcManager::com_android_nfc_wlc_startWlcP(JNIEnv* e, jobject,
                                                         jint mode) {
  tNFA_STATUS stat = NFA_STATUS_FAILED;

  LOG(DEBUG) << StringPrintf("%s: enter", __func__);

  gMutexWlc.lock();
  SyncEventGuard g(sNfaWlcEvent);
  stat = NFA_WlcStart(mode);

  if (stat == NFA_STATUS_OK) {
    LOG(DEBUG) << StringPrintf(
        "%s: start Wlc Poller, wait for success confirmation", __func__);
    sNfaWlcEvent.wait();
  } else {
    LOG(ERROR) << StringPrintf("%s: fail start WlcPoller; error=0x%X", __func__,
                               stat);
  }
  gMutexWlc.unlock();
  return sIsWlcpStarted ? JNI_TRUE : JNI_FALSE;
}

/*******************************************************************************
**
** Function:        com_android_nfc_wlc_chargeWlcListener
**
** Description:     Start charging WLC Listener
**                  e: JVM environment.
**                  power_adj_req:
**                  wpt_time_int:
**
** Returns:         True if WLCL charging started properly
**
*******************************************************************************/
jboolean NativeWlcManager::com_android_nfc_wlc_chargeWlcListener(
    JNIEnv* e, jobject, jint power_adj_req, jint wpt_time_int) {
  tNFA_STATUS stat = NFA_STATUS_FAILED;

  LOG(DEBUG) << StringPrintf("%s: wpt_time_int = %d", __func__, wpt_time_int);

  gMutexWlc.lock();
  SyncEventGuard g(sNfaWlcEvent);
  // TODO: condition call to sIsWlcpStarted
  // TODO: limit the min of wpt_time_int
  stat = NFA_WlcStartWPT((uint16_t)(power_adj_req & 0xFFFF), wpt_time_int);
  if (stat == NFA_STATUS_OK) {
    LOG(DEBUG) << StringPrintf(
        "%s: charge Wlc Listener, wait for success confirmation", __func__);
    sNfaWlcEvent.wait();
  } else {
    LOG(ERROR) << StringPrintf("%s: fail charge Wlc Listener; error=0x%X",
                               __func__, stat);
    gMutexWlc.unlock();
    return false;
  }
  gMutexWlc.unlock();
  return true;
}

/*******************************************************************************
**
** Function:        registerJniFunctions
**
** Description:     Register WLC feature JNI functions
**                  e: JVM environment.
**
** Returns:         -1 if JNI register error
**
*******************************************************************************/
int NativeWlcManager::registerJniFunctions(JNIEnv* e) {
  static const char fn[] = "NativeWlcManager::registerJniFunctions";
  LOG(DEBUG) << StringPrintf("%s", fn);
  return jniRegisterNativeMethods(e, "com/android/nfc/wlc/NfcCharging",
                                  sMethods, NELEM(sMethods));
}
