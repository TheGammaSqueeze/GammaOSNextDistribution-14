/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "apexd_session.h"

#include <android-base/errors.h>
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <dirent.h>
#include <sys/stat.h>

#include <filesystem>
#include <fstream>
#include <optional>
#include <utility>

#include "apexd_utils.h"
#include "session_state.pb.h"
#include "string_log.h"

using android::base::Error;
using android::base::Result;
using android::base::StringPrintf;
using apex::proto::SessionState;

namespace android {
namespace apex {

namespace {

static constexpr const char* kStateFileName = "state";

static Result<SessionState> ParseSessionState(const std::string& session_dir) {
  auto path = StringPrintf("%s/%s", session_dir.c_str(), kStateFileName);
  SessionState state;
  std::fstream state_file(path, std::ios::in | std::ios::binary);
  if (!state_file) {
    return Error() << "Failed to open " << path;
  }

  if (!state.ParseFromIstream(&state_file)) {
    return Error() << "Failed to parse " << path;
  }

  return std::move(state);
}

}  // namespace

std::string GetSessionsDir() {
  static std::string result;
  static std::once_flag once_flag;
  std::call_once(once_flag, [&]() {
    auto status =
        FindFirstExistingDirectory(kNewApexSessionsDir, kOldApexSessionsDir);
    if (!status.ok()) {
      LOG(FATAL) << status.error();
    }
    result = std::move(*status);
  });
  return result;
}

ApexSession::ApexSession(SessionState state, std::string session_dir)
    : state_(std::move(state)), session_dir_(std::move(session_dir)) {}

Result<void> ApexSession::MigrateToMetadataSessionsDir() {
  return MoveDir(kOldApexSessionsDir, kNewApexSessionsDir);
}

Result<ApexSession> ApexSession::CreateSession(int session_id) {
  SessionState state;
  // Create session directory
  std::string session_dir = GetSessionsDir() + "/" + std::to_string(session_id);
  if (auto status = CreateDirIfNeeded(session_dir, 0700); !status.ok()) {
    return status.error();
  }
  state.set_id(session_id);

  return ApexSession(state, std::move(session_dir));
}

Result<ApexSession> ApexSession::GetSessionFromDir(
    const std::string& session_dir) {
  auto state = ParseSessionState(session_dir);
  if (!state.ok()) {
    return state.error();
  }
  return ApexSession(*state, session_dir);
}

Result<ApexSession> ApexSession::GetSession(int session_id) {
  auto session_dir =
      StringPrintf("%s/%d", GetSessionsDir().c_str(), session_id);

  return GetSessionFromDir(session_dir);
}

std::vector<ApexSession> ApexSession::GetSessions() {
  std::vector<ApexSession> sessions;

  Result<std::vector<std::string>> session_paths = ReadDir(
      GetSessionsDir(), [](const std::filesystem::directory_entry& entry) {
        std::error_code ec;
        return entry.is_directory(ec);
      });

  if (!session_paths.ok()) {
    return sessions;
  }

  for (const std::string& session_dir_path : *session_paths) {
    // Try to read session state
    auto session = GetSessionFromDir(session_dir_path);
    if (!session.ok()) {
      LOG(WARNING) << session.error();
      continue;
    }
    sessions.push_back(std::move(*session));
  }

  return sessions;
}

std::vector<ApexSession> ApexSession::GetSessionsInState(
    SessionState::State state) {
  auto sessions = GetSessions();
  sessions.erase(
      std::remove_if(sessions.begin(), sessions.end(),
                     [&](const ApexSession &s) { return s.GetState() != state; }),
      sessions.end());

  return sessions;
}

SessionState::State ApexSession::GetState() const { return state_.state(); }

int ApexSession::GetId() const { return state_.id(); }

const std::string& ApexSession::GetBuildFingerprint() const {
  return state_.expected_build_fingerprint();
}

bool ApexSession::IsFinalized() const {
  switch (GetState()) {
    case SessionState::SUCCESS:
    case SessionState::ACTIVATION_FAILED:
    case SessionState::REVERTED:
    case SessionState::REVERT_FAILED:
      return true;
    default:
      return false;
  }
}

bool ApexSession::HasRollbackEnabled() const {
  return state_.rollback_enabled();
}

bool ApexSession::IsRollback() const { return state_.is_rollback(); }

int ApexSession::GetRollbackId() const { return state_.rollback_id(); }

const std::string& ApexSession::GetCrashingNativeProcess() const {
  return state_.crashing_native_process();
}

const std::string& ApexSession::GetErrorMessage() const {
  return state_.error_message();
}

const google::protobuf::RepeatedField<int> ApexSession::GetChildSessionIds()
    const {
  return state_.child_session_ids();
}

void ApexSession::SetChildSessionIds(
    const std::vector<int>& child_session_ids) {
  *(state_.mutable_child_session_ids()) = {child_session_ids.begin(),
                                           child_session_ids.end()};
}

const google::protobuf::RepeatedPtrField<std::string>
ApexSession::GetApexNames() const {
  return state_.apex_names();
}

const std::string& ApexSession::GetSessionDir() const { return session_dir_; }

void ApexSession::SetBuildFingerprint(const std::string& fingerprint) {
  *(state_.mutable_expected_build_fingerprint()) = fingerprint;
}

void ApexSession::SetHasRollbackEnabled(const bool enabled) {
  state_.set_rollback_enabled(enabled);
}

void ApexSession::SetIsRollback(const bool is_rollback) {
  state_.set_is_rollback(is_rollback);
}

void ApexSession::SetRollbackId(const int rollback_id) {
  state_.set_rollback_id(rollback_id);
}

void ApexSession::SetCrashingNativeProcess(
    const std::string& crashing_process) {
  state_.set_crashing_native_process(crashing_process);
}

void ApexSession::SetErrorMessage(const std::string& error_message) {
  state_.set_error_message(error_message);
}

void ApexSession::AddApexName(const std::string& apex_name) {
  state_.add_apex_names(apex_name);
}

Result<void> ApexSession::UpdateStateAndCommit(
    const SessionState::State& session_state) {
  state_.set_state(session_state);

  auto state_file_path =
      StringPrintf("%s/%s", session_dir_.c_str(), kStateFileName);

  std::fstream state_file(state_file_path,
                          std::ios::out | std::ios::trunc | std::ios::binary);
  if (!state_.SerializeToOstream(&state_file)) {
    return Error() << "Failed to write state file " << state_file_path;
  }

  return {};
}

Result<void> ApexSession::DeleteSession() const {
  LOG(INFO) << "Deleting " << session_dir_;
  auto path = std::filesystem::path(session_dir_);
  std::error_code error_code;
  std::filesystem::remove_all(path, error_code);
  if (error_code) {
    return Error() << "Failed to delete " << session_dir_ << " : "
                   << error_code.message();
  }
  return {};
}

std::ostream& operator<<(std::ostream& out, const ApexSession& session) {
  return out << "[id = " << session.GetId()
             << "; state = " << SessionState::State_Name(session.GetState())
             << "; session_dir = " << session.GetSessionDir() << "]";
}

void ApexSession::DeleteFinalizedSessions() {
  auto sessions = GetSessions();
  for (const ApexSession& session : sessions) {
    if (!session.IsFinalized()) {
      continue;
    }
    auto result = session.DeleteSession();
    if (!result.ok()) {
      LOG(WARNING) << "Failed to delete finalized session: " << session.GetId();
    }
  }
}

std::vector<std::string> ApexSession::GetStagedApexDirs(
    const std::string& staged_session_dir) const {
  const google::protobuf::RepeatedField<int>& child_session_ids =
      state_.child_session_ids();
  std::vector<std::string> dirs;
  if (child_session_ids.empty()) {
    dirs.push_back(staged_session_dir + "/session_" + std::to_string(GetId()));
  } else {
    for (auto child_session_id : child_session_ids) {
      dirs.push_back(staged_session_dir + "/session_" +
                     std::to_string(child_session_id));
    }
  }
  return dirs;
}

ApexSessionManager::ApexSessionManager(std::string sessions_base_dir)
    : sessions_base_dir_(std::move(sessions_base_dir)) {}

ApexSessionManager::ApexSessionManager(ApexSessionManager&& other) noexcept
    : sessions_base_dir_(std::move(other.sessions_base_dir_)) {}

ApexSessionManager& ApexSessionManager::operator=(
    ApexSessionManager&& other) noexcept {
  sessions_base_dir_ = std::move(other.sessions_base_dir_);
  return *this;
}

std::unique_ptr<ApexSessionManager> ApexSessionManager::Create(
    std::string sessions_base_dir) {
  return std::unique_ptr<ApexSessionManager>(
      new ApexSessionManager(std::move(sessions_base_dir)));
}

Result<ApexSession> ApexSessionManager::CreateSession(int session_id) {
  SessionState state;
  // Create session directory
  std::string session_dir =
      sessions_base_dir_ + "/" + std::to_string(session_id);
  OR_RETURN(CreateDirIfNeeded(session_dir, 0700));
  state.set_id(session_id);

  return ApexSession(std::move(state), std::move(session_dir));
}

Result<ApexSession> ApexSessionManager::GetSession(int session_id) const {
  auto session_dir =
      StringPrintf("%s/%d", sessions_base_dir_.c_str(), session_id);

  auto state = OR_RETURN(ParseSessionState(session_dir));
  return ApexSession(std::move(state), std::move(session_dir));
}

std::vector<ApexSession> ApexSessionManager::GetSessions() const {
  std::vector<ApexSession> sessions;

  auto walk_status = WalkDir(sessions_base_dir_, [&](const auto& entry) {
    if (!entry.is_directory()) {
      return;
    }

    std::string session_dir = entry.path();
    auto state = ParseSessionState(session_dir);
    if (!state.ok()) {
      LOG(WARNING) << state.error();
      return;
    }

    ApexSession session(std::move(*state), std::move(session_dir));
    sessions.push_back(std::move(session));
  });

  if (!walk_status.ok()) {
    LOG(WARNING) << walk_status.error();
    return std::move(sessions);
  }

  return std::move(sessions);
}

std::vector<ApexSession> ApexSessionManager::GetSessionsInState(
    const SessionState::State& state) const {
  std::vector<ApexSession> sessions = GetSessions();
  sessions.erase(std::remove_if(sessions.begin(), sessions.end(),
                                [&](const ApexSession& s) {
                                  return s.GetState() != state;
                                }),
                 sessions.end());

  return sessions;
}

Result<void> ApexSessionManager::MigrateFromOldSessionsDir(
    const std::string& old_sessions_base_dir) {
  if (old_sessions_base_dir == sessions_base_dir_) {
    LOG(INFO)
        << old_sessions_base_dir
        << " is the same as the current session directory. Nothing to migrate";
    return {};
  }

  return MoveDir(old_sessions_base_dir, sessions_base_dir_);
}

}  // namespace apex
}  // namespace android
