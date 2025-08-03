/*
 * Copyright 2019 The Android Open Source Project
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

#include <aidl/android/system/suspend/ISystemSuspend.h>
#include <aidl/android/system/suspend/IWakeLock.h>
#include <android/binder_manager.h>
#include <android/system/suspend/internal/ISuspendControlServiceInternal.h>
#include <benchmark/benchmark.h>
#include <binder/IServiceManager.h>

using aidl::android::system::suspend::ISystemSuspend;
using aidl::android::system::suspend::IWakeLock;
using aidl::android::system::suspend::WakeLockType;
using android::IBinder;
using android::sp;
using android::system::suspend::internal::ISuspendControlServiceInternal;
using android::system::suspend::internal::WakeLockInfo;

static void BM_acquireWakeLock(benchmark::State& state) {
    static const std::string suspendInstance =
        std::string() + ISystemSuspend::descriptor + "/default";
    static std::shared_ptr<ISystemSuspend> suspendService = ISystemSuspend::fromBinder(
        ndk::SpAIBinder(AServiceManager_waitForService(suspendInstance.c_str())));

    while (state.KeepRunning()) {
        std::shared_ptr<IWakeLock> wl = nullptr;
        suspendService->acquireWakeLock(WakeLockType::PARTIAL, "BenchmarkWakeLock", &wl);
    }
}
BENCHMARK(BM_acquireWakeLock);

static void BM_getWakeLockStats(benchmark::State& state) {
    static sp<IBinder> controlInternal =
        android::defaultServiceManager()->getService(android::String16("suspend_control_internal"));
    static sp<ISuspendControlServiceInternal> controlServiceInternal =
        android::interface_cast<ISuspendControlServiceInternal>(controlInternal);

    while (state.KeepRunning()) {
        std::vector<WakeLockInfo> wlStats;
        controlServiceInternal->getWakeLockStats(&wlStats);
    }
}
BENCHMARK(BM_getWakeLockStats);

BENCHMARK_MAIN();
