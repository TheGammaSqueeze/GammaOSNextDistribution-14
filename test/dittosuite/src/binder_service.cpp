// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#if __ANDROID__

#include <ditto/binder_service.h>

#include <ditto/binder.h>
#include <ditto/logger.h>

#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>


using android::IPCThreadState;
using android::defaultServiceManager;

namespace dittosuite {

BinderService::BinderService(const Params& params, const std::string& name, int64_t threads)
    : Instruction(kName, params), threads_(threads), name_(name) {
  pthread_mutex_init(&s_work_lock, nullptr);
  pthread_cond_init(&s_work_cond, nullptr);
}

void BinderService::RunSingle() {
  LOGD("Joining thread pool");

  pthread_mutex_lock(&s_work_lock);
  pthread_cond_wait(&s_work_cond, &s_work_lock);
  pthread_mutex_unlock(&s_work_lock);

  LOGD("Exiting thread pool");
}

void BinderService::SetUp() {
  Instruction::SetUp();
  LOGD("Creating Binder service: " + name_);

  defaultServiceManager()->addService(String16(name_.c_str()), new DittoBinder(&s_work_cond));

  android::ProcessState::self()->setThreadPoolMaxThreadCount(threads_);
  android::ProcessState::self()->startThreadPool();

  LOGD("Demo service is now ready");
}

void BinderService::TearDown() {
  LOGD("Demo service finished");

  pthread_cond_destroy(&s_work_cond);
  pthread_mutex_destroy(&s_work_lock);

  Instruction::TearDown();
}

}  // namespace dittosuite

#endif
