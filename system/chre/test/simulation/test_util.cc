/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "test_util.h"

#include <gtest/gtest.h>
#include <unordered_map>

#include "chre/core/event_loop_manager.h"
#include "chre/core/nanoapp.h"
#include "chre/util/dynamic_vector.h"
#include "chre/util/macros.h"
#include "chre/util/memory.h"
#include "chre_api/chre/version.h"
#include "nanoapp/include/chre_nsl_internal/platform/shared/nanoapp_support_lib_dso.h"

namespace chre {

namespace {
/**
 * List of chreNslNanoappInfo.
 *
 * Keep the chreNslNanoappInfo instances alive for the lifetime of the test
 * nanoapps.
 */
DynamicVector<UniquePtr<chreNslNanoappInfo>> gNanoappInfos;

/** Registry of nanoapp by ID. */
std::unordered_map<uint64_t, UniquePtr<TestNanoapp>> nanoapps;

/**
 * @return a pointer to a registered nanoapp or nullptr if the appId is not
 *         registered.
 */
TestNanoapp *queryNanoapp(uint64_t appId) {
  return nanoapps.count(appId) == 0 ? nullptr : nanoapps[appId].get();
}

/**
 * Nanoapp start.
 *
 * Invokes the start method of a registered TestNanoapp.
 */
bool start() {
  uint64_t id = chreGetAppId();
  TestNanoapp *app = queryNanoapp(id);
  if (app == nullptr) {
    LOGE("[start] unregistered nanoapp 0x%016" PRIx64, id);
    return false;
  }
  return app->start();
}

/**
 * Nanoapp handleEvent.
 *
 * Invokes the handleMethod method of a registered TestNanoapp.
 */
void handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                 const void *eventData) {
  uint64_t id = chreGetAppId();
  TestNanoapp *app = queryNanoapp(id);
  if (app == nullptr) {
    LOGE("[handleEvent] unregistered nanoapp 0x%016" PRIx64, id);
  } else {
    app->handleEvent(senderInstanceId, eventType, eventData);
  }
}

/**
 * Nanoapp end.
 *
 * Invokes the end method of a registered TestNanoapp.
 */
void end() {
  uint64_t id = chreGetAppId();
  TestNanoapp *app = queryNanoapp(id);
  if (app == nullptr) {
    LOGE("[end] unregistered nanoapp 0x%016" PRIx64, id);
  } else {
    app->end();
  }
}

/**
 * Registers a test nanoapp.
 *
 * TestNanoapps are registered when they are loaded so that their entry point
 * methods can be called.
 */
void registerNanoapp(UniquePtr<TestNanoapp> app) {
  if (nanoapps.count(app->id()) != 0) {
    LOGE("A nanoapp with the same id is already registered");
  } else {
    nanoapps[app->id()] = std::move(app);
  }
}

/**
 * Unregisters a nanoapp.
 *
 * Calls the TestNanoapp destructor.
 */
void unregisterNanoapp(uint64_t appId) {
  if (nanoapps.erase(appId) == 0) {
    LOGE("The nanoapp is not registered");
  }
}

}  // namespace

void unregisterAllTestNanoapps() {
  nanoapps.clear();
}

UniquePtr<Nanoapp> createStaticNanoapp(
    const char *name, uint64_t appId, uint32_t appVersion, uint32_t appPerms,
    decltype(nanoappStart) *startFunc,
    decltype(nanoappHandleEvent) *handleEventFunc,
    decltype(nanoappEnd) *endFunc) {
  return createStaticNanoapp(CHRE_NSL_NANOAPP_INFO_STRUCT_MINOR_VERSION, name,
                             appId, appVersion, appPerms, startFunc,
                             handleEventFunc, endFunc);
}

UniquePtr<Nanoapp> createStaticNanoapp(
    uint8_t infoStructVersion, const char *name, uint64_t appId,
    uint32_t appVersion, uint32_t appPerms, decltype(nanoappStart) *startFunc,
    decltype(nanoappHandleEvent) *handleEventFunc,
    decltype(nanoappEnd) *endFunc) {
  auto nanoapp = MakeUnique<Nanoapp>();
  auto nanoappInfo = MakeUnique<chreNslNanoappInfo>();
  chreNslNanoappInfo *appInfo = nanoappInfo.get();
  gNanoappInfos.push_back(std::move(nanoappInfo));
  appInfo->magic = CHRE_NSL_NANOAPP_INFO_MAGIC;
  appInfo->structMinorVersion = infoStructVersion;
  appInfo->targetApiVersion = CHRE_API_VERSION;
  appInfo->vendor = "Google";
  appInfo->name = name;
  appInfo->isSystemNanoapp = true;
  appInfo->isTcmNanoapp = true;
  appInfo->appId = appId;
  appInfo->appVersion = appVersion;
  appInfo->entryPoints.start = startFunc;
  appInfo->entryPoints.handleEvent = handleEventFunc;
  appInfo->entryPoints.end = endFunc;
  appInfo->appVersionString = "<undefined>";
  appInfo->appPermissions = appPerms;
  EXPECT_FALSE(nanoapp.isNull());
  nanoapp->loadStatic(appInfo);

  return nanoapp;
}

void deleteNanoappInfos() {
  gNanoappInfos.clear();
}

bool defaultNanoappStart() {
  return true;
}

void defaultNanoappHandleEvent(uint32_t /*senderInstanceId*/,
                               uint16_t /*eventType*/,
                               const void * /*eventData*/) {}

void defaultNanoappEnd() {}

void loadNanoapp(const char *name, uint64_t appId, uint32_t appVersion,
                 uint32_t appPerms, decltype(nanoappStart) *startFunc,
                 decltype(nanoappHandleEvent) *handleEventFunc,
                 decltype(nanoappEnd) *endFunc) {
  UniquePtr<Nanoapp> nanoapp = createStaticNanoapp(
      name, appId, appVersion, appPerms, startFunc, handleEventFunc, endFunc);

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::FinishLoadingNanoapp, std::move(nanoapp),
      testFinishLoadingNanoappCallback);

  TestEventQueueSingleton::get()->waitForEvent(
      CHRE_EVENT_SIMULATION_TEST_NANOAPP_LOADED);
}

uint64_t loadNanoapp(UniquePtr<TestNanoapp> app) {
  TestNanoapp *pApp = app.get();
  registerNanoapp(std::move(app));
  loadNanoapp(pApp->name(), pApp->id(), pApp->version(), pApp->perms(), &start,
              &handleEvent, &end);

  return pApp->id();
}

void sendEventToNanoapp(uint64_t appId, uint16_t eventType) {
  uint16_t instanceId;
  if (EventLoopManagerSingleton::get()
          ->getEventLoop()
          .findNanoappInstanceIdByAppId(appId, &instanceId)) {
    auto event = memoryAlloc<TestEvent>();
    ASSERT_NE(event, nullptr);
    event->type = eventType;
    EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
        CHRE_EVENT_TEST_EVENT, static_cast<void *>(event),
        freeTestEventDataCallback, instanceId);

  } else {
    LOGE("No instance found for nanoapp id = 0x%016" PRIx64, appId);
  }
}

void unloadNanoapp(uint64_t appId) {
  uint64_t *ptr = memoryAlloc<uint64_t>();
  ASSERT_NE(ptr, nullptr);
  *ptr = appId;
  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::HandleUnloadNanoapp, ptr,
      testFinishUnloadingNanoappCallback);

  TestEventQueueSingleton::get()->waitForEvent(
      CHRE_EVENT_SIMULATION_TEST_NANOAPP_UNLOADED);

  unregisterNanoapp(appId);
}

void testFinishLoadingNanoappCallback(SystemCallbackType /* type */,
                                      UniquePtr<Nanoapp> &&nanoapp) {
  EventLoopManagerSingleton::get()->getEventLoop().startNanoapp(nanoapp);
  TestEventQueueSingleton::get()->pushEvent(
      CHRE_EVENT_SIMULATION_TEST_NANOAPP_LOADED);
}

void testFinishUnloadingNanoappCallback(uint16_t /* type */, void *data,
                                        void * /* extraData */) {
  EventLoop &eventLoop = EventLoopManagerSingleton::get()->getEventLoop();
  uint16_t instanceId = 0;
  uint64_t *appId = static_cast<uint64_t *>(data);
  eventLoop.findNanoappInstanceIdByAppId(*appId, &instanceId);
  eventLoop.unloadNanoapp(instanceId, true);
  memoryFree(data);
  TestEventQueueSingleton::get()->pushEvent(
      CHRE_EVENT_SIMULATION_TEST_NANOAPP_UNLOADED);
}

void freeTestEventDataCallback(uint16_t /*eventType*/, void *eventData) {
  auto testEvent = static_cast<TestEvent *>(eventData);
  memoryFree(testEvent->data);
  memoryFree(testEvent);
}

}  // namespace chre
