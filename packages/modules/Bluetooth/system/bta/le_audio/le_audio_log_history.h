/*
 * Copyright 2023 The Android Open Source Project
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

#include <string>

#include "types/raw_address.h"

/* Some predefined tags */
static std::string kLogConnectionTag("CONN_STATE");
static std::string kLogStateMachineTag("SM");
static std::string kLogControlPointCmd("ASCS_CP_CMD");
static std::string kLogControlPointNotif("ASCS_CP_NOTIF");
static std::string kLogAseStateNotif("ASE_NOTIF");
static std::string kLogHciEvent("HCI_EVENT");
static std::string kLogAfCallBt("AF --> ");
static std::string kLogBtCallAf("AF <-- ");

/* Operations on SM and ASEs */
static std::string kLogStateChangedOp("STATE CHANGED");
static std::string kLogTargetStateChangedOp("TARGET STATE CHANGED");
static std::string kLogAseConfigOp("CODEC_CONFIG: ");
static std::string kLogAseQoSConfigOp("QOS_CONFIG: ");
static std::string kLogAseEnableOp("ENABLE: ");
static std::string kLogAseDisableOp("DISABLE: ");
static std::string kLogAseReleaseOp("RELEASE: ");
static std::string kLogAseSuspendOp("SUSPEND: ");
static std::string kLogAseUpdateMetadataOp("METADATA_UPDATE: ");
static std::string kLogAseStartReadyOp("RCV_START_READY: ");
static std::string kLogAseStopReadyOp("RCV_STOP_READY: ");

/* Operations on CISes */
static std::string kLogCigCreateOp("CIG_CREATE:");
static std::string kLogCigRemoveOp("CIG_REMOVE:");
static std::string kLogCisCreateOp("CIS_CREATE: ");
static std::string kLogCisEstablishedOp("CIS_ESTABLISED: ");
static std::string kLogCisDisconnectOp("CIS_DISCONNECT: ");
static std::string kLogCisDisconnectedOp("CIS_DISCONNECTED: ");
static std::string kLogSetDataPathOp("SET_DATA_PATH: ");
static std::string kLogRemoveDataPathOp("REMOVE_DATA_PATH: ");
static std::string kLogDataPathCompleteOp("DATA_PATH_COMPLETE: ");

/* AF Client operations */
static std::string kLogAfResume("RESUME_REQ: ");
static std::string kLogAfSuspend("SUSPEND_REQ: ");
static std::string kLogAfMetadataUpdate("METADATA_UPDATE: ");
static std::string kLogAfResumeConfirm("RESUME_CONFIRMED: ");
static std::string kLogAfSuspendConfirm("SUSPEND_CONFIRMED: ");
static std::string kLogAfCancel("REQUEST_CANCELED: ");
static std::string kLogAfReconfigComplete("RECONFIG_COMPLETE_EVT: ");
static std::string kLogAfSuspendForReconfig("SUSPEND_FOR_RECONFIG_EVT: ");

class LeAudioLogHistory {
 public:
  virtual ~LeAudioLogHistory(void) = default;
  static LeAudioLogHistory* Get(void);
  static void Cleanup(void);
  static void DebugDump(int fd);

  virtual void AddLogHistory(const std::string& tag, int group_id,
                             const RawAddress& addr,
                             const std::string& msg) = 0;
  virtual void AddLogHistory(const std::string& tag, int group_id,
                             const RawAddress& addr, const std::string& msg,
                             const std::string& extra) = 0;
};