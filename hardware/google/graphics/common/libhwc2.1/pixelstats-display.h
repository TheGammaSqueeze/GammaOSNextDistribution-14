/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <aidl/android/frameworks/stats/IStats.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include "pixelatoms_defs.h"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <sys/types.h>
#include <utils/Errors.h>

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;
using android::hardware::google::pixel::PixelAtoms::DisplayPortUsage;

std::shared_ptr<IStats> getStatsService();
void reportDisplayPortUsage(uint32_t width, uint32_t height, float refreshRate, uint32_t vendorId,
                            uint32_t productId, bool enabled);
