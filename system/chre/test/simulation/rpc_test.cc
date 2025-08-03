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

#include "rpc_test.h"

#include <cstdint>

#include "chre/core/event_loop.h"
#include "chre/core/event_loop_manager.h"
#include "chre/core/settings.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/time.h"
#include "chre_api/chre/event.h"
#include "chre_api/chre/re.h"

#include "gtest/gtest.h"
#include "inc/test_util.h"
#include "test_base.h"
#include "test_event.h"
#include "test_event_queue.h"
#include "test_util.h"

namespace chre {

pw::Status RpcTestService::Increment(const chre_rpc_NumberMessage &request,
                                     chre_rpc_NumberMessage &response) {
  EnvSingleton::get()->mServer.setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  response.number = request.number + 1;
  return pw::OkStatus();
}

namespace {

TEST_F(TestBase, PwRpcCanPublishServicesInNanoappStart) {
  class App : public TestNanoapp {
   public:
    bool start() override {
      struct chreNanoappRpcService servicesA[] = {
          {.id = 1, .version = 0},
          {.id = 2, .version = 0},
      };

      struct chreNanoappRpcService servicesB[] = {
          {.id = 3, .version = 0},
          {.id = 4, .version = 0},
      };

      return chrePublishRpcServices(servicesA, 2 /* numServices */) &&
             chrePublishRpcServices(servicesB, 2 /* numServices */);
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());
  Nanoapp *napp = getNanoappByAppId(appId);
  ASSERT_NE(napp, nullptr);

  EXPECT_EQ(napp->getRpcServices().size(), 4);
  EXPECT_EQ(napp->getRpcServices()[0].id, 1);
  EXPECT_EQ(napp->getRpcServices()[1].id, 2);
  EXPECT_EQ(napp->getRpcServices()[2].id, 3);
  EXPECT_EQ(napp->getRpcServices()[3].id, 4);
}

TEST_F(TestBase, PwRpcCanNotPublishDuplicateServices) {
  class App : public TestNanoapp {
    bool start() override {
      struct chreNanoappRpcService servicesA[] = {
          {.id = 1, .version = 0},
          {.id = 2, .version = 0},
      };

      bool success = chrePublishRpcServices(servicesA, 2 /* numServices */);

      EXPECT_FALSE(chrePublishRpcServices(servicesA, 2 /* numServices */));

      struct chreNanoappRpcService servicesB[] = {
          {.id = 5, .version = 0},
          {.id = 5, .version = 0},
      };

      EXPECT_FALSE(chrePublishRpcServices(servicesB, 2 /* numServices */));

      return success;
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());
  Nanoapp *napp = getNanoappByAppId(appId);
  ASSERT_NE(napp, nullptr);

  EXPECT_EQ(napp->getRpcServices().size(), 2);
  EXPECT_EQ(napp->getRpcServices()[0].id, 1);
  EXPECT_EQ(napp->getRpcServices()[1].id, 2);
}

TEST_F(TestBase, PwRpcDifferentAppCanPublishSameServices) {
  class App : public TestNanoapp {
   public:
    explicit App(uint64_t id) : TestNanoapp(TestNanoappInfo{.id = id}) {}

    bool start() override {
      struct chreNanoappRpcService services[] = {
          {.id = 1, .version = 0},
          {.id = 2, .version = 0},
      };

      return chrePublishRpcServices(services, 2 /* numServices */);
    }
  };

  uint64_t app1Id = loadNanoapp(MakeUnique<App>(0x01));
  uint64_t app2Id = loadNanoapp(MakeUnique<App>(0x02));
  Nanoapp *napp1 = getNanoappByAppId(app1Id);
  ASSERT_NE(napp1, nullptr);

  EXPECT_EQ(napp1->getRpcServices().size(), 2);
  EXPECT_EQ(napp1->getRpcServices()[0].id, 1);
  EXPECT_EQ(napp1->getRpcServices()[1].id, 2);

  Nanoapp *napp2 = getNanoappByAppId(app2Id);
  ASSERT_NE(napp2, nullptr);

  EXPECT_EQ(napp2->getRpcServices().size(), 2);
  EXPECT_EQ(napp2->getRpcServices()[0].id, 1);
  EXPECT_EQ(napp2->getRpcServices()[1].id, 2);
}

TEST_F(TestBase, PwRpcCanNotPublishServicesOutsideOfNanoappStart) {
  CREATE_CHRE_TEST_EVENT(PUBLISH_SERVICES, 0);

  class App : public TestNanoapp {
   public:
    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case PUBLISH_SERVICES: {
              struct chreNanoappRpcService services[] = {
                  {.id = 1, .version = 0},
                  {.id = 2, .version = 0},
              };

              bool success =
                  chrePublishRpcServices(services, 2 /* numServices */);
              TestEventQueueSingleton::get()->pushEvent(PUBLISH_SERVICES,
                                                        success);
              break;
            }
          }
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  bool success = true;
  sendEventToNanoapp(appId, PUBLISH_SERVICES);
  waitForEvent(PUBLISH_SERVICES, &success);
  EXPECT_FALSE(success);

  Nanoapp *napp = getNanoappByAppId(appId);
  ASSERT_NE(napp, nullptr);

  EXPECT_EQ(napp->getRpcServices().size(), 0);
}

TEST_F(TestBase, PwRpcRegisterServicesShouldGracefullyFailOnDuplicatedService) {
  class App : public TestNanoapp {
   public:
    bool start() override {
      static RpcTestService testService;

      chre::RpcServer::Service service = {.service = testService,
                                          .id = 0xca8f7150a3f05847,
                                          .version = 0x01020034};

      chre::RpcServer &server = EnvSingleton::get()->mServer;

      bool status = server.registerServices(1, &service);

      EXPECT_TRUE(status);

      EXPECT_FALSE(server.registerServices(1, &service));

      return status;
    }

    void end() override {
      EnvSingleton::get()->closeServer();
    }
  };

  EnvSingleton::init();
  uint64_t appId = loadNanoapp(MakeUnique<App>());
  unloadNanoapp(appId);
  EnvSingleton::deinit();
}

TEST_F(TestBase, PwRpcGetNanoappInfoByAppIdReturnsServices) {
  CREATE_CHRE_TEST_EVENT(QUERY_INFO, 0);

  class App : public TestNanoapp {
   public:
    bool start() override {
      struct chreNanoappRpcService services[] = {
          {.id = 1, .version = 2},
          {.id = 2, .version = 3},
      };

      return chrePublishRpcServices(services, 2 /* numServices */);
    }

    void handleEvent(uint32_t, uint16_t eventType,
                     const void *eventData) override {
      switch (eventType) {
        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case QUERY_INFO: {
              auto id = static_cast<uint64_t *>(event->data);
              bool success = chreGetNanoappInfoByAppId(*id, &mInfo);
              const struct chreNanoappInfo *pInfo = success ? &mInfo : nullptr;
              TestEventQueueSingleton::get()->pushEvent(QUERY_INFO, pInfo);
              break;
            }
          }
        }
      }
    }

   protected:
    struct chreNanoappInfo mInfo;
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  struct chreNanoappInfo *pInfo = nullptr;
  sendEventToNanoapp(appId, QUERY_INFO, appId);
  waitForEvent(QUERY_INFO, &pInfo);
  EXPECT_TRUE(pInfo != nullptr);
  EXPECT_EQ(pInfo->rpcServiceCount, 2);
  EXPECT_EQ(pInfo->rpcServices[0].id, 1);
  EXPECT_EQ(pInfo->rpcServices[0].version, 2);
  EXPECT_EQ(pInfo->rpcServices[1].id, 2);
  EXPECT_EQ(pInfo->rpcServices[1].version, 3);
  EXPECT_EQ(pInfo->reserved[0], 0);
  EXPECT_EQ(pInfo->reserved[1], 0);
  EXPECT_EQ(pInfo->reserved[2], 0);
}

TEST_F(TestBase, PwRpcClientNanoappCanRequestServerNanoapp) {
  CREATE_CHRE_TEST_EVENT(INCREMENT_REQUEST, 0);

  class ClientApp : public TestNanoapp {
   public:
    ClientApp() : TestNanoapp(TestNanoappInfo{.id = kPwRcpClientAppId}) {}

    void handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                     const void *eventData) override {
      Env *env = EnvSingleton::get();

      env->mClient.handleEvent(senderInstanceId, eventType, eventData);
      switch (eventType) {
        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case INCREMENT_REQUEST: {
              auto client =
                  env->mClient
                      .get<rpc::pw_rpc::nanopb::RpcTestService::Client>();
              if (client.has_value()) {
                chre_rpc_NumberMessage incrementRequest;
                incrementRequest.number = *static_cast<uint32_t *>(event->data);
                env->mIncrementCall = client->Increment(
                    incrementRequest, [](const chre_rpc_NumberMessage &response,
                                         pw::Status status) {
                      if (status.ok()) {
                        EnvSingleton::get()->mNumber = response.number;
                        TestEventQueueSingleton::get()->pushEvent(
                            INCREMENT_REQUEST, true);
                      } else {
                        TestEventQueueSingleton::get()->pushEvent(
                            INCREMENT_REQUEST, false);
                      }
                    });
              } else {
                TestEventQueueSingleton::get()->pushEvent(INCREMENT_REQUEST,
                                                          false);
              }
            }
          }
        }
      }
    }

    void end() {
      EnvSingleton::get()->closeClient();
    }
  };

  class ServerApp : public TestNanoapp {
   public:
    ServerApp() : TestNanoapp(TestNanoappInfo{.id = kPwRcpServerAppId}) {}

    bool start() override {
      chre::RpcServer::Service service = {
          .service = EnvSingleton::get()->mRpcTestService,
          .id = 0xca8f7150a3f05847,
          .version = 0x01020034};
      return EnvSingleton::get()->mServer.registerServices(1, &service);
    }

    void handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                     const void *eventData) override {
      EnvSingleton::get()->mServer.handleEvent(senderInstanceId, eventType,
                                               eventData);
    }

    void end() {
      EnvSingleton::get()->closeServer();
    }
  };

  EnvSingleton::init();
  uint64_t serverId = loadNanoapp(MakeUnique<ServerApp>());
  uint64_t clientId = loadNanoapp(MakeUnique<ClientApp>());
  bool status;
  constexpr uint32_t kNumber = 101;

  sendEventToNanoapp(clientId, INCREMENT_REQUEST, kNumber);
  waitForEvent(INCREMENT_REQUEST, &status);
  EXPECT_TRUE(status);
  EXPECT_EQ(EnvSingleton::get()->mNumber, kNumber + 1);
  unloadNanoapp(serverId);
  unloadNanoapp(clientId);
  EnvSingleton::deinit();
}

TEST_F(TestBase, PwRpcRpcClientHasServiceCheckForAMatchingService) {
  CREATE_CHRE_TEST_EVENT(QUERY_HAS_SERVICE, 0);

  struct ServiceInfo {
    uint64_t id;
    uint32_t version;
    uint64_t appId;
  };

  class App : public TestNanoapp {
   public:
    bool start() override {
      struct chreNanoappRpcService services[] = {{.id = 1, .version = 2}};

      return chrePublishRpcServices(services, 1 /* numServices */);
    }

    void handleEvent(uint32_t, uint16_t eventType, const void *eventData) {
      switch (eventType) {
        case CHRE_EVENT_TEST_EVENT: {
          auto event = static_cast<const TestEvent *>(eventData);
          switch (event->type) {
            case QUERY_HAS_SERVICE: {
              auto service =
                  static_cast<const struct ServiceInfo *>(event->data);
              RpcClient client{service->appId};
              bool hasService =
                  client.hasService(service->id, service->version);
              TestEventQueueSingleton::get()->pushEvent(QUERY_HAS_SERVICE,
                                                        hasService);
              break;
            }
          }
          break;
        }
      }
    }
  };

  uint64_t appId = loadNanoapp(MakeUnique<App>());

  ServiceInfo service;
  bool hasService = false;

  service = {.id = 1, .version = 2, .appId = appId};
  sendEventToNanoapp(appId, QUERY_HAS_SERVICE, service);
  waitForEvent(QUERY_HAS_SERVICE, &hasService);
  EXPECT_TRUE(hasService);
  service = {.id = 10, .version = 2, .appId = appId};
  sendEventToNanoapp(appId, QUERY_HAS_SERVICE, service);
  waitForEvent(QUERY_HAS_SERVICE, &hasService);
  EXPECT_FALSE(hasService);
}

}  // namespace

}  // namespace chre
