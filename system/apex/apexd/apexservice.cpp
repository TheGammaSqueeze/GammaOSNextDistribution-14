/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "apexservice.h"

#include <dirent.h>
#include <stdio.h>
#include <stdlib.h>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/result.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <binder/IPCThreadState.h>
#include <binder/IResultReceiver.h>
#include <binder/IServiceManager.h>
#include <binder/LazyServiceRegistrar.h>
#include <binder/ProcessState.h>
#include <binder/Status.h>
#include <private/android_filesystem_config.h>
#include <utils/String16.h>

#include "apex_file.h"
#include "apex_file_repository.h"
#include "apexd.h"
#include "apexd_session.h"
#include "string_log.h"

#include <android/apex/BnApexService.h>

using android::base::Join;
using android::base::Result;

namespace android {
namespace apex {
namespace binder {
namespace {

using BinderStatus = ::android::binder::Status;

BinderStatus CheckCallerIsRoot(const std::string& name) {
  uid_t uid = IPCThreadState::self()->getCallingUid();
  if (uid != AID_ROOT) {
    std::string msg = "Only root is allowed to call " + name;
    return BinderStatus::fromExceptionCode(BinderStatus::EX_SECURITY,
                                           String8(msg.c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus CheckCallerSystemOrRoot(const std::string& name) {
  uid_t uid = IPCThreadState::self()->getCallingUid();
  if (uid != AID_ROOT && uid != AID_SYSTEM) {
    std::string msg = "Only root and system_server are allowed to call " + name;
    return BinderStatus::fromExceptionCode(BinderStatus::EX_SECURITY,
                                           String8(msg.c_str()));
  }
  return BinderStatus::ok();
}

class ApexService : public BnApexService {
 public:
  using BinderStatus = ::android::binder::Status;
  using SessionState = ::apex::proto::SessionState;

  ApexService(){};

  BinderStatus stagePackages(const std::vector<std::string>& paths) override;
  BinderStatus unstagePackages(const std::vector<std::string>& paths) override;
  BinderStatus submitStagedSession(const ApexSessionParams& params,
                                   ApexInfoList* apex_info_list) override;
  BinderStatus markStagedSessionReady(int session_id) override;
  BinderStatus markStagedSessionSuccessful(int session_id) override;
  BinderStatus getSessions(std::vector<ApexSessionInfo>* aidl_return) override;
  BinderStatus getStagedSessionInfo(
      int session_id, ApexSessionInfo* apex_session_info) override;
  BinderStatus getStagedApexInfos(const ApexSessionParams& params,
                                  std::vector<ApexInfo>* aidl_return) override;
  BinderStatus getActivePackages(std::vector<ApexInfo>* aidl_return) override;
  BinderStatus getActivePackage(const std::string& package_name,
                                ApexInfo* aidl_return) override;
  BinderStatus getAllPackages(std::vector<ApexInfo>* aidl_return) override;
  BinderStatus abortStagedSession(int session_id) override;
  BinderStatus revertActiveSessions() override;
  BinderStatus resumeRevertIfNeeded() override;
  BinderStatus snapshotCeData(int user_id, int rollback_id,
                              const std::string& apex_name) override;
  BinderStatus restoreCeData(int user_id, int rollback_id,
                             const std::string& apex_name) override;
  BinderStatus destroyDeSnapshots(int rollback_id) override;
  BinderStatus destroyCeSnapshots(int user_id, int rollback_id) override;
  BinderStatus destroyCeSnapshotsNotSpecified(
      int user_id, const std::vector<int>& retain_rollback_ids) override;
  BinderStatus remountPackages() override;
  BinderStatus recollectPreinstalledData(
      const std::vector<std::string>& paths) override;
  BinderStatus recollectDataApex(const std::string& path,
                                 const std::string& decompression_dir) override;
  BinderStatus markBootCompleted() override;
  BinderStatus calculateSizeForCompressedApex(
      const CompressedApexInfoList& compressed_apex_info_list,
      int64_t* required_size) override;
  BinderStatus reserveSpaceForCompressedApex(
      const CompressedApexInfoList& compressed_apex_info_list) override;
  BinderStatus installAndActivatePackage(const std::string& package_path,
                                         bool force,
                                         ApexInfo* aidl_return) override;

  status_t dump(int fd, const Vector<String16>& args) override;

  // Override onTransact so we can handle shellCommand.
  status_t onTransact(uint32_t _aidl_code, const Parcel& _aidl_data,
                      Parcel* _aidl_reply, uint32_t _aidl_flags) override;

  status_t shellCommand(int in, int out, int err, const Vector<String16>& args);
};

BinderStatus CheckDebuggable(const std::string& name) {
  if (!::android::base::GetBoolProperty("ro.debuggable", false)) {
    std::string tmp = name + " unavailable on non-debuggable builds";
    return BinderStatus::fromExceptionCode(BinderStatus::EX_SECURITY,
                                           String8(tmp.c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::stagePackages(const std::vector<std::string>& paths) {
  LOG(INFO) << "stagePackages() received by ApexService, paths "
            << android::base::Join(paths, ',');

  BinderStatus debug_check = CheckDebuggable("stagePackages");
  if (!debug_check.isOk()) {
    return debug_check;
  }
  if (auto is_root = CheckCallerIsRoot("stagePackages"); !is_root.isOk()) {
    return is_root;
  }
  Result<void> res = ::android::apex::StagePackages(paths);

  if (res.ok()) {
    return BinderStatus::ok();
  }

  LOG(ERROR) << "Failed to stage " << android::base::Join(paths, ',') << ": "
             << res.error();
  return BinderStatus::fromExceptionCode(
      BinderStatus::EX_SERVICE_SPECIFIC,
      String8(res.error().message().c_str()));
}

BinderStatus ApexService::unstagePackages(
    const std::vector<std::string>& paths) {
  LOG(INFO) << "unstagePackages() received by ApexService, paths "
            << android::base::Join(paths, ',');

  if (auto check = CheckCallerSystemOrRoot("unstagePackages"); !check.isOk()) {
    return check;
  }

  Result<void> res = ::android::apex::UnstagePackages(paths);
  if (res.ok()) {
    return BinderStatus::ok();
  }

  LOG(ERROR) << "Failed to unstage " << android::base::Join(paths, ',') << ": "
             << res.error();
  return BinderStatus::fromExceptionCode(
      BinderStatus::EX_SERVICE_SPECIFIC,
      String8(res.error().message().c_str()));
}

BinderStatus ApexService::submitStagedSession(const ApexSessionParams& params,
                                              ApexInfoList* apex_info_list) {
  LOG(INFO) << "submitStagedSession() received by ApexService, session id "
            << params.sessionId << " child sessions: ["
            << android::base::Join(params.childSessionIds, ',') << "]";

  auto check = CheckCallerSystemOrRoot("submitStagedSession");
  if (!check.isOk()) {
    return check;
  }

  Result<std::vector<ApexFile>> packages = ::android::apex::SubmitStagedSession(
      params.sessionId, params.childSessionIds, params.hasRollbackEnabled,
      params.isRollback, params.rollbackId);
  if (!packages.ok()) {
    LOG(ERROR) << "Failed to submit session id " << params.sessionId << ": "
               << packages.error();
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(packages.error().message().c_str()));
  }

  for (const auto& package : *packages) {
    ApexInfo out;
    out.moduleName = package.GetManifest().name();
    out.modulePath = package.GetPath();
    out.versionCode = package.GetManifest().version();
    apex_info_list->apexInfos.push_back(out);
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::markStagedSessionReady(int session_id) {
  LOG(INFO) << "markStagedSessionReady() received by ApexService, session id "
            << session_id;

  auto check = CheckCallerSystemOrRoot("markStagedSessionReady");
  if (!check.isOk()) {
    return check;
  }

  Result<void> success = ::android::apex::MarkStagedSessionReady(session_id);
  if (!success.ok()) {
    LOG(ERROR) << "Failed to mark session id " << session_id
               << " as ready: " << success.error();
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(success.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::markStagedSessionSuccessful(int session_id) {
  LOG(INFO)
      << "markStagedSessionSuccessful() received by ApexService, session id "
      << session_id;

  auto check = CheckCallerSystemOrRoot("markStagedSessionSuccessful");
  if (!check.isOk()) {
    return check;
  }

  Result<void> ret = ::android::apex::MarkStagedSessionSuccessful(session_id);
  if (!ret.ok()) {
    LOG(ERROR) << "Failed to mark session " << session_id
               << " as SUCCESS: " << ret.error();
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_ILLEGAL_ARGUMENT,
        String8(ret.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::markBootCompleted() {
  LOG(INFO) << "markBootCompleted() received by ApexService";

  auto check = CheckCallerSystemOrRoot("markBootCompleted");
  if (!check.isOk()) {
    return check;
  }

  ::android::apex::OnBootCompleted();
  return BinderStatus::ok();
}

BinderStatus ApexService::calculateSizeForCompressedApex(
    const CompressedApexInfoList& compressed_apex_info_list,
    int64_t* required_size) {
  std::vector<std::tuple<std::string, int64_t, int64_t>> compressed_apexes;
  compressed_apexes.reserve(compressed_apex_info_list.apexInfos.size());
  for (const auto& apex_info : compressed_apex_info_list.apexInfos) {
    compressed_apexes.emplace_back(apex_info.moduleName, apex_info.versionCode,
                                   apex_info.decompressedSize);
  }
  const auto& instance = ApexFileRepository::GetInstance();
  *required_size = ::android::apex::CalculateSizeForCompressedApex(
      compressed_apexes, instance);
  return BinderStatus::ok();
}

BinderStatus ApexService::reserveSpaceForCompressedApex(
    const CompressedApexInfoList& compressed_apex_info_list) {
  int64_t required_size;
  if (auto res = calculateSizeForCompressedApex(compressed_apex_info_list,
                                                &required_size);
      !res.isOk()) {
    return res;
  }
  if (auto res = ReserveSpaceForCompressedApex(required_size, kOtaReservedDir);
      !res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

static void ClearSessionInfo(ApexSessionInfo* session_info) {
  session_info->sessionId = -1;
  session_info->isUnknown = false;
  session_info->isVerified = false;
  session_info->isStaged = false;
  session_info->isActivated = false;
  session_info->isRevertInProgress = false;
  session_info->isActivationFailed = false;
  session_info->isSuccess = false;
  session_info->isReverted = false;
  session_info->isRevertFailed = false;
}

void ConvertToApexSessionInfo(const ApexSession& session,
                              ApexSessionInfo* session_info) {
  using SessionState = ::apex::proto::SessionState;

  ClearSessionInfo(session_info);
  session_info->sessionId = session.GetId();
  session_info->crashingNativeProcess = session.GetCrashingNativeProcess();
  session_info->errorMessage = session.GetErrorMessage();

  switch (session.GetState()) {
    case SessionState::VERIFIED:
      session_info->isVerified = true;
      break;
    case SessionState::STAGED:
      session_info->isStaged = true;
      break;
    case SessionState::ACTIVATED:
      session_info->isActivated = true;
      break;
    case SessionState::ACTIVATION_FAILED:
      session_info->isActivationFailed = true;
      break;
    case SessionState::SUCCESS:
      session_info->isSuccess = true;
      break;
    case SessionState::REVERT_IN_PROGRESS:
      session_info->isRevertInProgress = true;
      break;
    case SessionState::REVERTED:
      session_info->isReverted = true;
      break;
    case SessionState::REVERT_FAILED:
      session_info->isRevertFailed = true;
      break;
    case SessionState::UNKNOWN:
    default:
      session_info->isUnknown = true;
      break;
  }
}

static ApexInfo GetApexInfo(const ApexFile& package) {
  auto& instance = ApexFileRepository::GetInstance();
  ApexInfo out;
  out.moduleName = package.GetManifest().name();
  out.modulePath = package.GetPath();
  out.versionCode = package.GetManifest().version();
  out.versionName = package.GetManifest().versionname();
  out.isFactory = instance.IsPreInstalledApex(package);
  out.isActive = false;
  Result<std::string> preinstalled_path =
      instance.GetPreinstalledPath(package.GetManifest().name());
  if (preinstalled_path.ok()) {
    // We replace the preinstalled paths for block devices to /system/apex
    // because PackageManager will not resolve them if they aren't in one of
    // the SYSTEM_PARTITIONS defined in PackagePartitions.java.
    // b/195363518 for more context.
    const std::string block_path = "/dev/block/";
    const std::string sys_apex_path =
        std::string(kApexPackageSystemDir) + "/" +
        preinstalled_path->substr(block_path.length());
    out.preinstalledModulePath = preinstalled_path->starts_with(block_path)
                                     ? sys_apex_path
                                     : *preinstalled_path;
  }
  out.activeApexChanged = ::android::apex::IsActiveApexChanged(package);
  return out;
}

static std::string ToString(const ApexInfo& package) {
  std::string msg = StringLog()
                    << "Module: " << package.moduleName
                    << " Version: " << package.versionCode
                    << " VersionName: " << package.versionName
                    << " Path: " << package.modulePath
                    << " IsActive: " << std::boolalpha << package.isActive
                    << " IsFactory: " << std::boolalpha << package.isFactory
                    << std::endl;
  return msg;
}

BinderStatus ApexService::getSessions(
    std::vector<ApexSessionInfo>* aidl_return) {
  LOG(INFO) << "getSessions() received by ApexService";

  auto check = CheckCallerSystemOrRoot("getSessions");
  if (!check.isOk()) {
    return check;
  }

  auto sessions = ApexSession::GetSessions();
  for (const auto& session : sessions) {
    ApexSessionInfo session_info;
    ConvertToApexSessionInfo(session, &session_info);
    aidl_return->push_back(session_info);
  }

  return BinderStatus::ok();
}

BinderStatus ApexService::getStagedSessionInfo(
    int session_id, ApexSessionInfo* apex_session_info) {
  LOG(INFO) << "getStagedSessionInfo() received by ApexService, session id "
            << session_id;

  auto check = CheckCallerSystemOrRoot("getStagedSessionInfo");
  if (!check.isOk()) {
    return check;
  }

  auto session = ApexSession::GetSession(session_id);
  if (!session.ok()) {
    // Unknown session.
    ClearSessionInfo(apex_session_info);
    apex_session_info->isUnknown = true;
    return BinderStatus::ok();
  }

  ConvertToApexSessionInfo(*session, apex_session_info);

  return BinderStatus::ok();
}

BinderStatus ApexService::getStagedApexInfos(
    const ApexSessionParams& params, std::vector<ApexInfo>* aidl_return) {
  LOG(INFO) << "getStagedApexInfos() received by ApexService, session id "
            << params.sessionId << " child sessions: ["
            << android::base::Join(params.childSessionIds, ',') << "]";

  auto check = CheckCallerSystemOrRoot("getStagedApexInfos");
  if (!check.isOk()) {
    return check;
  }

  Result<std::vector<ApexFile>> files = ::android::apex::GetStagedApexFiles(
      params.sessionId, params.childSessionIds);
  if (!files.ok()) {
    LOG(ERROR) << "Failed to getStagedApexInfo session id " << params.sessionId
               << ": " << files.error();
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(files.error().message().c_str()));
  }

  // Retrieve classpath information
  auto class_path = ::android::apex::MountAndDeriveClassPath(*files);
  if (!class_path.ok()) {
    LOG(ERROR) << "Failed to getStagedApexInfo session id " << params.sessionId
               << ": " << class_path.error();
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(class_path.error().message().c_str()));
  }

  for (const auto& apex_file : *files) {
    ApexInfo apex_info = GetApexInfo(apex_file);
    auto package_name = apex_info.moduleName;
    apex_info.hasClassPathJars = class_path->HasClassPathJars(package_name);
    aidl_return->push_back(std::move(apex_info));
  }

  return BinderStatus::ok();
}

BinderStatus ApexService::getActivePackages(
    std::vector<ApexInfo>* aidl_return) {
  LOG(INFO) << "getActivePackages received by ApexService";

  auto check = CheckCallerSystemOrRoot("getActivePackages");
  if (!check.isOk()) {
    return check;
  }

  auto packages = ::android::apex::GetActivePackages();
  for (const auto& package : packages) {
    ApexInfo apex_info = GetApexInfo(package);
    apex_info.isActive = true;
    aidl_return->push_back(std::move(apex_info));
  }

  return BinderStatus::ok();
}

BinderStatus ApexService::getActivePackage(const std::string& package_name,
                                           ApexInfo* aidl_return) {
  LOG(INFO) << "getActivePackage received by ApexService package_name : "
            << package_name;

  auto check = CheckCallerSystemOrRoot("getActivePackage");
  if (!check.isOk()) {
    return check;
  }

  Result<ApexFile> apex = ::android::apex::GetActivePackage(package_name);
  if (apex.ok()) {
    *aidl_return = GetApexInfo(*apex);
    aidl_return->isActive = true;
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::getAllPackages(std::vector<ApexInfo>* aidl_return) {
  LOG(INFO) << "getAllPackages received by ApexService";

  auto check = CheckCallerSystemOrRoot("getAllPackages");
  if (!check.isOk()) {
    return check;
  }

  const auto& active = ::android::apex::GetActivePackages();
  const auto& factory = ::android::apex::GetFactoryPackages();
  for (const ApexFile& pkg : active) {
    ApexInfo apex_info = GetApexInfo(pkg);
    apex_info.isActive = true;
    aidl_return->push_back(std::move(apex_info));
  }
  for (const ApexFile& pkg : factory) {
    const auto& same_path = [&pkg](const auto& o) {
      return o.GetPath() == pkg.GetPath();
    };
    if (std::find_if(active.begin(), active.end(), same_path) == active.end()) {
      aidl_return->push_back(GetApexInfo(pkg));
    }
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::installAndActivatePackage(
    const std::string& package_path, bool force, ApexInfo* aidl_return) {
  LOG(INFO) << "installAndActivatePackage() received by ApexService, path: "
            << package_path << " force : " << force;

  auto check = CheckCallerSystemOrRoot("installAndActivatePackage");
  if (!check.isOk()) {
    return check;
  }

  if (force) {
    auto debug_check = CheckDebuggable("Forced non-staged APEX update");
    if (!debug_check.isOk()) {
      return debug_check;
    }
  }

  auto res = InstallPackage(package_path, force);
  if (!res.ok()) {
    LOG(ERROR) << "Failed to install package " << package_path << " : "
               << res.error();
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(res.error().message().c_str()));
  }
  *aidl_return = GetApexInfo(*res);
  aidl_return->isActive = true;
  return BinderStatus::ok();
}

BinderStatus ApexService::abortStagedSession(int session_id) {
  LOG(INFO) << "abortStagedSession() received by ApexService session : "
            << session_id;

  auto check = CheckCallerSystemOrRoot("abortStagedSession");
  if (!check.isOk()) {
    return check;
  }

  Result<void> res = ::android::apex::AbortStagedSession(session_id);
  if (!res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_ILLEGAL_ARGUMENT,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::revertActiveSessions() {
  LOG(INFO) << "revertActiveSessions() received by ApexService.";

  auto check = CheckCallerSystemOrRoot("revertActiveSessions");
  if (!check.isOk()) {
    return check;
  }

  Result<void> res = ::android::apex::RevertActiveSessions("", "");
  if (!res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_ILLEGAL_ARGUMENT,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::resumeRevertIfNeeded() {
  LOG(INFO) << "resumeRevertIfNeeded() received by ApexService.";

  BinderStatus debug_check = CheckDebuggable("resumeRevertIfNeeded");
  if (!debug_check.isOk()) {
    return debug_check;
  }

  auto root_check = CheckCallerIsRoot("resumeRevertIfNeeded");
  if (!root_check.isOk()) {
    return root_check;
  }

  Result<void> res = ::android::apex::ResumeRevertIfNeeded();
  if (!res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_ILLEGAL_ARGUMENT,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::snapshotCeData(int user_id, int rollback_id,
                                         const std::string& apex_name) {
  LOG(INFO) << "snapshotCeData() received by ApexService user_id : " << user_id
            << " rollback_id : " << rollback_id << " apex_name : " << apex_name;

  auto check = CheckCallerSystemOrRoot("snapshotCeData");
  if (!check.isOk()) {
    return check;
  }

  Result<void> res =
      ::android::apex::SnapshotCeData(user_id, rollback_id, apex_name);
  if (!res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::restoreCeData(int user_id, int rollback_id,
                                        const std::string& apex_name) {
  LOG(INFO) << "restoreCeData() received by ApexService user_id : " << user_id
            << " rollback_id : " << rollback_id << " apex_name : " << apex_name;

  auto check = CheckCallerSystemOrRoot("restoreCeData");
  if (!check.isOk()) {
    return check;
  }

  Result<void> res =
      ::android::apex::RestoreCeData(user_id, rollback_id, apex_name);
  if (!res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::destroyDeSnapshots(int rollback_id) {
  LOG(INFO) << "destroyDeSnapshots() received by ApexService rollback_id : "
            << rollback_id;

  auto check = CheckCallerSystemOrRoot("destroyDeSnapshots");
  if (!check.isOk()) {
    return check;
  }

  Result<void> res = ::android::apex::DestroyDeSnapshots(rollback_id);
  if (!res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::destroyCeSnapshots(int user_id, int rollback_id) {
  LOG(INFO) << "destroyCeSnapshots() received by ApexService user_id : "
            << user_id << " rollback_id : " << rollback_id;

  auto check = CheckCallerSystemOrRoot("destroyCeSnapshots");
  if (!check.isOk()) {
    return check;
  }

  Result<void> res = ::android::apex::DestroyCeSnapshots(user_id, rollback_id);
  if (!res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::destroyCeSnapshotsNotSpecified(
    int user_id, const std::vector<int>& retain_rollback_ids) {
  LOG(INFO)
      << "destroyCeSnapshotsNotSpecified() received by ApexService user_id : "
      << user_id << " retain_rollback_ids : [" << Join(retain_rollback_ids, ',')
      << "]";

  auto check = CheckCallerSystemOrRoot("destroyCeSnapshotsNotSpecified");
  if (!check.isOk()) {
    return check;
  }

  Result<void> res = ::android::apex::DestroyCeSnapshotsNotSpecified(
      user_id, retain_rollback_ids);
  if (!res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::remountPackages() {
  LOG(INFO) << "remountPackages() received by ApexService";

  if (auto debug = CheckDebuggable("remountPackages"); !debug.isOk()) {
    return debug;
  }
  if (auto root = CheckCallerIsRoot("remountPackages"); !root.isOk()) {
    return root;
  }
  if (auto res = ::android::apex::RemountPackages(); !res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::recollectPreinstalledData(
    const std::vector<std::string>& paths) {
  LOG(INFO) << "recollectPreinstalledData() received by ApexService, paths: "
            << Join(paths, ',');

  if (auto debug = CheckDebuggable("recollectPreinstalledData");
      !debug.isOk()) {
    return debug;
  }
  if (auto root = CheckCallerIsRoot("recollectPreinstalledData");
      !root.isOk()) {
    return root;
  }
  ApexFileRepository& instance = ApexFileRepository::GetInstance();
  if (auto res = instance.AddPreInstalledApex(paths); !res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

BinderStatus ApexService::recollectDataApex(
    const std::string& path, const std::string& decompression_dir) {
  LOG(INFO) << "recollectDataApex() received by ApexService, paths " << path
            << " and " << decompression_dir;

  if (auto debug = CheckDebuggable("recollectDataApex"); !debug.isOk()) {
    return debug;
  }
  if (auto root = CheckCallerIsRoot("recollectDataApex"); !root.isOk()) {
    return root;
  }
  ApexFileRepository& instance = ApexFileRepository::GetInstance();
  if (auto res = instance.AddDataApex(path); !res.ok()) {
    return BinderStatus::fromExceptionCode(
        BinderStatus::EX_SERVICE_SPECIFIC,
        String8(res.error().message().c_str()));
  }
  return BinderStatus::ok();
}

status_t ApexService::onTransact(uint32_t _aidl_code, const Parcel& _aidl_data,
                                 Parcel* _aidl_reply, uint32_t _aidl_flags) {
  switch (_aidl_code) {
    case IBinder::SHELL_COMMAND_TRANSACTION: {
      int in = _aidl_data.readFileDescriptor();
      int out = _aidl_data.readFileDescriptor();
      int err = _aidl_data.readFileDescriptor();
      int argc = _aidl_data.readInt32();
      Vector<String16> args;
      for (int i = 0; i < argc && _aidl_data.dataAvail() > 0; i++) {
        args.add(_aidl_data.readString16());
      }
      sp<IBinder> unused_callback;
      sp<IResultReceiver> result_receiver;
      status_t status;
      if ((status = _aidl_data.readNullableStrongBinder(&unused_callback)) !=
          OK)
        return status;
      if ((status = _aidl_data.readNullableStrongBinder(&result_receiver)) !=
          OK)
        return status;
      status = shellCommand(in, out, err, args);
      if (result_receiver != nullptr) {
        result_receiver->send(status);
      }
      return OK;
    }
  }
  return BnApexService::onTransact(_aidl_code, _aidl_data, _aidl_reply,
                                   _aidl_flags);
}
status_t ApexService::dump(int fd, const Vector<String16>& /*args*/) {
  std::vector<ApexInfo> list;
  BinderStatus status = getActivePackages(&list);
  dprintf(fd, "ACTIVE PACKAGES:\n");
  if (!status.isOk()) {
    std::string msg = StringLog() << "Failed to retrieve packages: "
                                  << status.toString8().c_str() << std::endl;
    dprintf(fd, "%s", msg.c_str());
    return BAD_VALUE;
  } else {
    for (const auto& item : list) {
      std::string msg = ToString(item);
      dprintf(fd, "%s", msg.c_str());
    }
  }

  dprintf(fd, "SESSIONS:\n");
  std::vector<ApexSession> sessions = ApexSession::GetSessions();

  for (const auto& session : sessions) {
    std::string child_ids_str = "";
    auto child_ids = session.GetChildSessionIds();
    if (child_ids.size() > 0) {
      child_ids_str = "Child IDs:";
      for (auto childSessionId : session.GetChildSessionIds()) {
        child_ids_str += " " + std::to_string(childSessionId);
      }
    }
    std::string revert_reason = "";
    const auto& crashing_native_process = session.GetCrashingNativeProcess();
    if (!crashing_native_process.empty()) {
      revert_reason = " Revert Reason: " + crashing_native_process;
    }
    std::string error_message_dump = "";
    const auto& error_message = session.GetErrorMessage();
    if (!error_message.empty()) {
      error_message_dump = " Error Message: " + error_message;
    }
    std::string msg =
        StringLog() << "Session ID: " << session.GetId() << child_ids_str
                    << " State: " << SessionState_State_Name(session.GetState())
                    << revert_reason << error_message_dump << std::endl;
    dprintf(fd, "%s", msg.c_str());
  }

  return OK;
}

status_t ApexService::shellCommand(int in, int out, int err,
                                   const Vector<String16>& args) {
  if (in == BAD_TYPE || out == BAD_TYPE || err == BAD_TYPE) {
    return BAD_VALUE;
  }
  auto print_help = [](int fd, const char* prefix = nullptr) {
    StringLog log;
    if (prefix != nullptr) {
      log << prefix << std::endl;
    }
    log << "ApexService:" << std::endl
        << "  help - display this help" << std::endl
        << "  stagePackages [package_path1] ([package_path2]...) - stage "
           "multiple packages from the given path"
        << std::endl
        << "  getActivePackage [package_name] - return info for active package "
           "with given name, if present"
        << std::endl
        << "  getAllPackages - return the list of all packages" << std::endl
        << "  getActivePackages - return the list of active packages"
        << std::endl
        << "  activatePackage [package_path] - activate package from the "
           "given path"
        << std::endl
        << "  deactivatePackage [package_path] - deactivate package from the "
           "given path"
        << std::endl
        << "  getStagedSessionInfo [sessionId] - displays information about a "
           "given session previously submitted"
        << std::endl
        << "  submitStagedSession [sessionId] - attempts to submit the "
           "installer session with given id"
        << std::endl
        << "  remountPackages - Force apexd to remount active packages. This "
           "call can be used to speed up development workflow of an APEX "
           "package. Example of usage:\n"
           "    1. adb shell stop\n"
           "    2. adb sync\n"
           "    3. adb shell cmd -w apexservice remountPackages\n"
           "    4. adb shell start\n"
           "\n"
           "Note: APEX package will be successfully remounted only if there "
           "are no alive processes holding a reference to it"
        << std::endl;
    dprintf(fd, "%s", log.operator std::string().c_str());
  };

  if (args.size() == 0) {
    print_help(err, "No command given");
    return BAD_VALUE;
  }

  const String16& cmd = args[0];

  if (cmd == String16("stagePackages")) {
    if (args.size() < 2) {
      print_help(err, "stagePackages requires at least one package_path");
      return BAD_VALUE;
    }
    std::vector<std::string> pkgs;
    pkgs.reserve(args.size() - 1);
    for (size_t i = 1; i != args.size(); ++i) {
      pkgs.emplace_back(String8(args[i]).c_str());
    }
    BinderStatus status = stagePackages(pkgs);
    if (status.isOk()) {
      return OK;
    }
    std::string msg = StringLog() << "Failed to stage package(s): "
                                  << status.toString8().c_str() << std::endl;
    dprintf(err, "%s", msg.c_str());
    return BAD_VALUE;
  }
  if (cmd == String16("getAllPackages")) {
    if (args.size() != 1) {
      print_help(err, "Unrecognized options");
      return BAD_VALUE;
    }
    std::vector<ApexInfo> list;
    BinderStatus status = getAllPackages(&list);
    if (status.isOk()) {
      for (const auto& item : list) {
        std::string msg = ToString(item);
        dprintf(out, "%s", msg.c_str());
      }
      return OK;
    }
    std::string msg = StringLog() << "Failed to retrieve packages: "
                                  << status.toString8().c_str() << std::endl;
    dprintf(err, "%s", msg.c_str());
    return BAD_VALUE;
  }

  if (cmd == String16("getActivePackages")) {
    if (args.size() != 1) {
      print_help(err, "Unrecognized options");
      return BAD_VALUE;
    }
    std::vector<ApexInfo> list;
    BinderStatus status = getActivePackages(&list);
    if (status.isOk()) {
      for (const auto& item : list) {
        std::string msg = ToString(item);
        dprintf(out, "%s", msg.c_str());
      }
      return OK;
    }
    std::string msg = StringLog() << "Failed to retrieve packages: "
                                  << status.toString8().c_str() << std::endl;
    dprintf(err, "%s", msg.c_str());
    return BAD_VALUE;
  }

  if (cmd == String16("getActivePackage")) {
    if (args.size() != 2) {
      print_help(err, "Unrecognized options");
      return BAD_VALUE;
    }

    ApexInfo package;
    BinderStatus status = getActivePackage(String8(args[1]).c_str(), &package);
    if (status.isOk()) {
      std::string msg = ToString(package);
      dprintf(out, "%s", msg.c_str());
      return OK;
    }

    std::string msg = StringLog()
                      << "Failed to fetch active package: "
                      << String8(args[1]).c_str()
                      << ", error: " << status.toString8().c_str() << std::endl;
    dprintf(err, "%s", msg.c_str());
    return BAD_VALUE;
  }

  if (cmd == String16("activatePackage")) {
    if (args.size() != 2) {
      print_help(err, "activatePackage requires one package_path");
      return BAD_VALUE;
    }
    std::string path = String8(args[1]).c_str();
    auto status = ::android::apex::ActivatePackage(path);
    if (status.ok()) {
      return OK;
    }
    std::string msg = StringLog() << "Failed to activate package: "
                                  << status.error().message() << std::endl;
    dprintf(err, "%s", msg.c_str());
    return BAD_VALUE;
  }

  if (cmd == String16("deactivatePackage")) {
    if (args.size() != 2) {
      print_help(err, "deactivatePackage requires one package_path");
      return BAD_VALUE;
    }
    std::string path = String8(args[1]).c_str();
    auto status = ::android::apex::DeactivatePackage(path);
    if (status.ok()) {
      return OK;
    }
    std::string msg = StringLog() << "Failed to deactivate package: "
                                  << status.error().message() << std::endl;
    dprintf(err, "%s", msg.c_str());
    return BAD_VALUE;
  }

  if (cmd == String16("getStagedSessionInfo")) {
    if (args.size() != 2) {
      print_help(err, "getStagedSessionInfo requires one session id");
      return BAD_VALUE;
    }
    int session_id = strtol(String8(args[1]).c_str(), nullptr, 10);
    if (session_id < 0) {
      std::string msg = StringLog()
                        << "Failed to parse session id. Must be an integer.";
      dprintf(err, "%s", msg.c_str());
      return BAD_VALUE;
    }

    ApexSessionInfo session_info;
    BinderStatus status = getStagedSessionInfo(session_id, &session_info);
    if (status.isOk()) {
      std::string revert_reason = "";
      std::string crashing_native_process = session_info.crashingNativeProcess;
      if (!crashing_native_process.empty()) {
        revert_reason = " revertReason: " + crashing_native_process;
      }
      std::string msg = StringLog()
                        << "session_info: "
                        << " isUnknown: " << session_info.isUnknown
                        << " isVerified: " << session_info.isVerified
                        << " isStaged: " << session_info.isStaged
                        << " isActivated: " << session_info.isActivated
                        << " isActivationFailed: "
                        << session_info.isActivationFailed << revert_reason
                        << std::endl;
      dprintf(out, "%s", msg.c_str());
      return OK;
    }
    std::string msg = StringLog() << "Failed to query session: "
                                  << status.toString8().c_str() << std::endl;
    dprintf(err, "%s", msg.c_str());
    return BAD_VALUE;
  }

  if (cmd == String16("submitStagedSession")) {
    if (args.size() != 2) {
      print_help(err, "submitStagedSession requires one session id");
      return BAD_VALUE;
    }
    int session_id = strtol(String8(args[1]).c_str(), nullptr, 10);
    if (session_id < 0) {
      std::string msg = StringLog()
                        << "Failed to parse session id. Must be an integer.";
      dprintf(err, "%s", msg.c_str());
      return BAD_VALUE;
    }

    ApexInfoList list;
    std::vector<int> empty_child_session_ids;
    ApexSessionParams params;
    params.sessionId = session_id;
    params.childSessionIds = empty_child_session_ids;
    BinderStatus status = submitStagedSession(params, &list);
    if (status.isOk()) {
        for (const auto& item : list.apexInfos) {
          std::string msg = ToString(item);
          dprintf(out, "%s", msg.c_str());
        }
      return OK;
    }
    std::string msg = StringLog() << "Failed to submit session: "
                                  << status.toString8().c_str() << std::endl;
    dprintf(err, "%s", msg.c_str());
    return BAD_VALUE;
  }

  if (cmd == String16("remountPackages")) {
    BinderStatus status = remountPackages();
    if (status.isOk()) {
      return OK;
    }
    std::string msg = StringLog() << "remountPackages failed: "
                                  << status.toString8().c_str() << std::endl;
    dprintf(err, "%s", msg.c_str());
    return BAD_VALUE;
  }

  if (cmd == String16("help")) {
    if (args.size() != 1) {
      print_help(err, "Help has no options");
      return BAD_VALUE;
    }
    print_help(out);
    return OK;
  }

  print_help(err);
  return BAD_VALUE;
}

}  // namespace

static constexpr const char* kApexServiceName = "apexservice";

using android::IPCThreadState;
using android::ProcessState;
using android::sp;
using android::binder::LazyServiceRegistrar;

void CreateAndRegisterService() {
  sp<ProcessState> ps(ProcessState::self());

  // Create binder service and register with LazyServiceRegistrar
  sp<ApexService> apex_service = sp<ApexService>::make();
  auto lazy_registrar = LazyServiceRegistrar::getInstance();
  lazy_registrar.forcePersist(true);
  lazy_registrar.registerService(apex_service, kApexServiceName);
}

void AllowServiceShutdown() {
  auto lazy_registrar = LazyServiceRegistrar::getInstance();
  lazy_registrar.forcePersist(false);
}

void StartThreadPool() {
  sp<ProcessState> ps(ProcessState::self());

  // Start threadpool, wait for IPC
  ps->startThreadPool();
}

void JoinThreadPool() {
  IPCThreadState::self()->joinThreadPool();  // should not return
}

}  // namespace binder
}  // namespace apex
}  // namespace android
