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

#pragma once
#include "NfcJniUtil.h"
#include "nfa_wlc_api.h"

/*****************************************************************************
**
**  Name:           NativeWlcManager
**
**  Description:    Manage Wlc activities at stack level.
**
*****************************************************************************/
class NativeWlcManager {
 public:
  /*******************************************************************************
  **
  ** Function:        getInstance
  **
  ** Description:     Get the singleton of this object.
  **
  ** Returns:         Reference to this object.
  **
  *******************************************************************************/
  static NativeWlcManager& getInstance();

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
  void initialize(nfc_jni_native_data* native);

  /*******************************************************************************
  **
  ** Function:        registerJniFunctions
  **
  ** Description:     Register WLC JNI functions.
  **
  ** Returns:         None
  **
  *******************************************************************************/
  int registerJniFunctions(JNIEnv* e);

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
  void notifyWlcCompletion(uint8_t wpt_end_condition);

 private:
  // Fields below are final after initialize()
  nfc_jni_native_data* mNativeData;

  bool mIsWlcEnabled = false;

  /*******************************************************************************
  **
  ** Function:        NativeWlcManager
  **
  ** Description:     Initialize member variables.
  **
  ** Returns:         None
  **
  *******************************************************************************/
  NativeWlcManager();

  /*******************************************************************************
  **
  ** Function:        ~NativeWlcManager
  **
  ** Description:     Release all resources.
  **
  ** Returns:         None
  **
  *******************************************************************************/
  ~NativeWlcManager();

  /*******************************************************************************
  **
  ** Function:        wlcManagementCallback
  **
  ** Description:     Callback function for the stack.
  **                  event: event ID.
  **                  eventData: event's data.
  **
  ** Returns:         None
  **
  *******************************************************************************/
  static void wlcManagementCallback(tNFA_WLC_EVT wlcEvent,
                                    tNFA_WLC_EVT_DATA* eventData);

  static const JNINativeMethod sMethods[];

  static jboolean com_android_nfc_wlc_startWlcP(JNIEnv* e, jobject, jint mode);
  static jboolean com_android_nfc_wlc_chargeWlcListener(JNIEnv* e, jobject,
                                                        jint power_adj_req,
                                                        jint wpt_time_int);
};
