/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_ZEPHYR_STATIC_NANOAPP_INIT_H_
#define CHRE_PLATFORM_ZEPHYR_STATIC_NANOAPP_INIT_H_

#include "chre/core/nanoapp.h"
#include "chre/core/static_nanoapps.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/shared/nanoapp_support_lib_dso.h"
#include "chre/version.h"

#define CHRE_STATIC_NANOAPP_INIT(app_name, app_id, app_version, app_perms)    \
  namespace chre {                                                            \
  UniquePtr<Nanoapp> initializeStaticNanoapp##app_name() {                    \
    static struct ::chreNslNanoappInfo app_info;                              \
    UniquePtr<Nanoapp> nanoapp = MakeUnique<Nanoapp>();                       \
    app_info.magic = CHRE_NSL_NANOAPP_INFO_MAGIC;                             \
    app_info.structMinorVersion = CHRE_NSL_NANOAPP_INFO_STRUCT_MINOR_VERSION; \
    app_info.targetApiVersion = CHRE_API_VERSION;                             \
    app_info.vendor = "Zephyr";                                               \
    app_info.name = #app_name;                                                \
    app_info.isSystemNanoapp = true;                                          \
    app_info.isTcmNanoapp = false;                                            \
    app_info.appId = app_id;                                                  \
    app_info.appVersion = app_version;                                        \
    app_info.entryPoints.start = nanoappStart;                                \
    app_info.entryPoints.handleEvent = nanoappHandleEvent;                    \
    app_info.entryPoints.end = nanoappEnd;                                    \
    app_info.appVersionString = "<undefined>";                                \
    app_info.appPermissions = app_perms;                                      \
    if (nanoapp.isNull()) {                                                   \
      FATAL_ERROR("Failed to allocate nanoapp " #app_name);                   \
    } else {                                                                  \
      nanoapp->loadStatic(&app_info);                                         \
    }                                                                         \
                                                                              \
    return nanoapp;                                                           \
  }                                                                           \
  }
#endif  // CHRE_PLATFORM_ZEPHYR_STATIC_NANOAPP_INIT_H_
