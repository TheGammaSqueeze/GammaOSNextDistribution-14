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

#define LOG_TAG "apexd"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <selinux/android.h>
#include <strings.h>
#include <sys/stat.h>

#include <memory>

#include "apexd.h"
#include "apexd_checkpoint_vold.h"
#include "apexd_lifecycle.h"
#include "apexservice.h"

namespace {

using android::base::SetDefaultTag;

int HandleSubcommand(int argc, char** argv) {
  if (strcmp("--bootstrap", argv[1]) == 0) {
    SetDefaultTag("apexd-bootstrap");
    return android::apex::OnBootstrap();
  }

  if (strcmp("--unmount-all", argv[1]) == 0) {
    SetDefaultTag("apexd-unmount-all");
    bool also_include_staged_apexes =
        argc >= 3 && strcmp("--also-include-staged-apexes", argv[2]) == 0;
    std::unique_ptr<android::apex::ApexSessionManager> session_manager;
    if (also_include_staged_apexes) {
      session_manager = android::apex::ApexSessionManager::Create(
          android::apex::GetSessionsDir());
      android::apex::InitializeSessionManager(session_manager.get());
    }
    return android::apex::UnmountAll(also_include_staged_apexes);
  }

  if (strcmp("--otachroot-bootstrap", argv[1]) == 0) {
    SetDefaultTag("apexd-otachroot");
    bool also_include_staged_apexes =
        argc >= 3 && strcmp("--also-include-staged-apexes", argv[2]) == 0;
    std::unique_ptr<android::apex::ApexSessionManager> session_manager;
    if (also_include_staged_apexes) {
      session_manager = android::apex::ApexSessionManager::Create(
          android::apex::GetSessionsDir());
      android::apex::InitializeSessionManager(session_manager.get());
    }
    return android::apex::OnOtaChrootBootstrap(also_include_staged_apexes);
  }

  if (strcmp("--snapshotde", argv[1]) == 0) {
    SetDefaultTag("apexd-snapshotde");
    // Need to know if checkpointing is enabled so that a prerestore snapshot
    // can be taken if it's not.
    android::base::Result<android::apex::VoldCheckpointInterface>
        vold_service_st = android::apex::VoldCheckpointInterface::Create();
    if (!vold_service_st.ok()) {
      LOG(ERROR) << "Could not retrieve vold service: "
                 << vold_service_st.error();
    } else {
      android::apex::InitializeVold(&*vold_service_st);
    }

    // We are running regular apexd, which starts after /metadata/apex/sessions
    // and /data/apex/sessions have been created by init. It is safe to create
    // ApexSessionManager.
    auto session_manager = android::apex::ApexSessionManager::Create(
        android::apex::GetSessionsDir());
    android::apex::InitializeSessionManager(session_manager.get());

    int result = android::apex::SnapshotOrRestoreDeUserData();

    if (result == 0) {
      // Notify other components (e.g. init) that all APEXs are ready to be used
      // Note that it's important that the binder service is registered at this
      // point, since other system services might depend on it.
      android::apex::OnAllPackagesReady();
    }
    return result;
  }

  if (strcmp("--vm", argv[1]) == 0) {
    SetDefaultTag("apexd-vm");
    return android::apex::OnStartInVmMode();
  }

  LOG(ERROR) << "Unknown subcommand: " << argv[1];
  return 1;
}

void InstallSigtermSignalHandler() {
  struct sigaction action = {};
  action.sa_handler = [](int /*signal*/) {
    // Handle SIGTERM gracefully.
    // By default, when SIGTERM is received a process will exit with non-zero
    // exit code, which will trigger reboot_on_failure handler if one is
    // defined. This doesn't play well with userspace reboot which might
    // terminate apexd with SIGTERM if apexd was running at the moment of
    // userspace reboot, hence this custom handler to exit gracefully.
    _exit(0);
  };
  sigaction(SIGTERM, &action, nullptr);
}

void InstallSelinuxLogging() {
  union selinux_callback cb;
  cb.func_log = selinux_log_callback;
  selinux_set_callback(SELINUX_CB_LOG, cb);
}

}  // namespace

int main(int argc, char** argv) {
  android::base::InitLogging(argv, &android::base::KernelLogger);
  // TODO(b/158468454): add a -v flag or an external setting to change severity.
  android::base::SetMinimumLogSeverity(android::base::INFO);

  const bool has_subcommand = argv[1] != nullptr;
  LOG(INFO) << "Started. subcommand = "
            << (has_subcommand ? argv[1] : "(null)");

  // set umask to 022 so that files/dirs created are accessible to other
  // processes e.g.) /apex/apex-info-list.xml is supposed to be read by other
  // processes
  umask(022);

  // In some scenarios apexd needs to adjust the selinux label of the files.
  // Install the selinux logging callback so that we can catch potential errors.
  InstallSelinuxLogging();

  InstallSigtermSignalHandler();

  android::apex::SetConfig(android::apex::kDefaultConfig);

  android::apex::ApexdLifecycle& lifecycle =
      android::apex::ApexdLifecycle::GetInstance();
  bool booting = lifecycle.IsBooting();

  if (has_subcommand) {
    return HandleSubcommand(argc, argv);
  }

  // We are running regular apexd, which starts after /metadata/apex/sessions
  // and /data/apex/sessions have been created by init. It is safe to create
  // ApexSessionManager.
  auto session_manager = android::apex::ApexSessionManager::Create(
      android::apex::GetSessionsDir());
  android::apex::InitializeSessionManager(session_manager.get());

  android::base::Result<android::apex::VoldCheckpointInterface>
      vold_service_st = android::apex::VoldCheckpointInterface::Create();
  android::apex::VoldCheckpointInterface* vold_service = nullptr;
  if (!vold_service_st.ok()) {
    LOG(ERROR) << "Could not retrieve vold service: "
               << vold_service_st.error();
  } else {
    vold_service = &*vold_service_st;
  }
  android::apex::Initialize(vold_service);

  if (booting) {
    auto res = session_manager->MigrateFromOldSessionsDir(
        android::apex::kOldApexSessionsDir);
    if (!res.ok()) {
      LOG(ERROR) << "Failed to migrate sessions to /metadata partition : "
                 << res.error();
    }
    android::apex::OnStart();
  } else {
    // TODO(b/172911822): Trying to use data apex related ApexFileRepository
    //  apis without initializing it should throw error. Also, unit tests should
    //  not pass without initialization.
    // TODO(b/172911822): Consolidate this with Initialize() when
    //  ApexFileRepository can act as cache and re-scanning is not expensive
    android::apex::InitializeDataApex();
  }
  // start apexservice before ApexdLifecycle::WaitForBootStatus which waits for
  // IApexService::markBootComplete().
  android::apex::binder::CreateAndRegisterService();
  android::apex::binder::StartThreadPool();

  if (booting) {
    // Notify other components (e.g. init) that all APEXs are correctly mounted
    // and activated (but are not yet ready to be used). Configuration based on
    // activated APEXs may be performed at this point, but use of APEXs
    // themselves should wait for the ready status instead, which is set when
    // the "--snapshotde" subcommand is received and snapshot/restore is
    // complete.
    android::apex::OnAllPackagesActivated(/*is_bootstrap=*/false);
    lifecycle.WaitForBootStatus(android::apex::RevertActiveSessionsAndReboot);
    // Run cleanup routine on boot complete.
    // This should run before AllowServiceShutdown() to prevent
    // service_manager killing apexd in the middle of the cleanup.
    android::apex::BootCompletedCleanup();
  }

  android::apex::binder::AllowServiceShutdown();

  android::apex::binder::JoinThreadPool();
  return 1;
}
