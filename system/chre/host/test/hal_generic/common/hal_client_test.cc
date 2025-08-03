/*
 * Copyright (C) 2023 The Android Open Source Project
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
#include "chre_host/hal_client.h"
#include "chre_host/hal_error.h"

#include <unordered_set>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

#include <aidl/android/hardware/contexthub/IContextHub.h>

namespace android::chre {

namespace {
using ::aidl::android::hardware::contexthub::ContextHubMessage;
using ::aidl::android::hardware::contexthub::HostEndpointInfo;
using ::aidl::android::hardware::contexthub::IContextHub;
using ::aidl::android::hardware::contexthub::IContextHubCallbackDefault;
using ::aidl::android::hardware::contexthub::IContextHubDefault;

using ::ndk::ScopedAStatus;

using ::testing::_;
using ::testing::ByMove;
using ::testing::Field;
using ::testing::IsEmpty;
using ::testing::Return;
using ::testing::UnorderedElementsAre;

using HostEndpointId = char16_t;
constexpr HostEndpointId kEndpointId = 0x10;

class HalClientForTest : public HalClient {
 public:
  HalClientForTest(const std::shared_ptr<IContextHub> &contextHub,
                   const std::vector<HostEndpointId> &connectedEndpoints,
                   const std::shared_ptr<IContextHubCallback> &callback =
                       ndk::SharedRefBase::make<IContextHubCallbackDefault>())
      : HalClient(callback) {
    mContextHub = contextHub;
    for (const HostEndpointId &endpointId : connectedEndpoints) {
      mConnectedEndpoints[endpointId] = {.hostEndpointId = endpointId};
    }
  }

  std::unordered_set<HostEndpointId> getConnectedEndpointIds() {
    std::unordered_set<HostEndpointId> result{};
    for (const auto &[endpointId, unusedEndpointInfo] : mConnectedEndpoints) {
      result.insert(endpointId);
    }
    return result;
  }

  HalClientCallback *getClientCallback() {
    return mCallback.get();
  }
};

class MockContextHub : public IContextHubDefault {
 public:
  MOCK_METHOD(ScopedAStatus, onHostEndpointConnected,
              (const HostEndpointInfo &info), (override));
  MOCK_METHOD(ScopedAStatus, onHostEndpointDisconnected,
              (HostEndpointId endpointId), (override));
  MOCK_METHOD(ScopedAStatus, queryNanoapps, (int32_t icontextHubId),
              (override));
  MOCK_METHOD(ScopedAStatus, sendMessageToHub,
              (int32_t contextHubId, const ContextHubMessage &message),
              (override));
};

}  // namespace

TEST(HalClientTest, EndpointConnectionBasic) {
  auto mockContextHub = ndk::SharedRefBase::make<MockContextHub>();
  const HostEndpointInfo kInfo = {
      .hostEndpointId = kEndpointId,
      .type = HostEndpointInfo::Type::NATIVE,
      .packageName = "HalClientTest",
      .attributionTag{},
  };

  auto halClient = std::make_unique<HalClientForTest>(
      mockContextHub, std::vector<HostEndpointId>{});
  EXPECT_THAT(halClient->getConnectedEndpointIds(), IsEmpty());

  EXPECT_CALL(*mockContextHub,
              onHostEndpointConnected(
                  Field(&HostEndpointInfo::hostEndpointId, kEndpointId)))
      .WillOnce(Return(ScopedAStatus::ok()));

  halClient->connectEndpoint(kInfo);
  EXPECT_THAT(halClient->getConnectedEndpointIds(),
              UnorderedElementsAre(kEndpointId));
}

TEST(HalClientTest, EndpointConnectionMultipleRequests) {
  auto mockContextHub = ndk::SharedRefBase::make<MockContextHub>();
  const HostEndpointInfo kInfo = {
      .hostEndpointId = kEndpointId,
      .type = HostEndpointInfo::Type::NATIVE,
      .packageName = "HalClientTest",
      .attributionTag{},
  };

  auto halClient = std::make_unique<HalClientForTest>(
      mockContextHub, std::vector<HostEndpointId>{});
  EXPECT_THAT(halClient->getConnectedEndpointIds(), IsEmpty());

  // multiple requests are tolerated
  EXPECT_CALL(*mockContextHub,
              onHostEndpointConnected(
                  Field(&HostEndpointInfo::hostEndpointId, kEndpointId)))
      .WillOnce(Return(ScopedAStatus::ok()))
      .WillOnce(Return(ScopedAStatus::ok()));

  halClient->connectEndpoint(kInfo);
  halClient->connectEndpoint(kInfo);

  EXPECT_THAT(halClient->getConnectedEndpointIds(),
              UnorderedElementsAre(kEndpointId));
}

TEST(HalClientTest, EndpointDisconnectionBasic) {
  auto mockContextHub = ndk::SharedRefBase::make<MockContextHub>();
  auto halClient = std::make_unique<HalClientForTest>(
      mockContextHub, std::vector<HostEndpointId>{kEndpointId});

  EXPECT_THAT(halClient->getConnectedEndpointIds(),
              UnorderedElementsAre(kEndpointId));

  EXPECT_CALL(*mockContextHub, onHostEndpointDisconnected(kEndpointId))
      .WillOnce(Return(ScopedAStatus::ok()));
  halClient->disconnectEndpoint(kEndpointId);

  EXPECT_THAT(halClient->getConnectedEndpointIds(), IsEmpty());
}

TEST(HalClientTest, EndpointDisconnectionMultipleRequest) {
  auto mockContextHub = ndk::SharedRefBase::make<MockContextHub>();
  auto halClient = std::make_unique<HalClientForTest>(
      mockContextHub, std::vector<HostEndpointId>{kEndpointId});
  EXPECT_THAT(halClient->getConnectedEndpointIds(),
              UnorderedElementsAre(kEndpointId));

  EXPECT_CALL(*mockContextHub, onHostEndpointDisconnected(kEndpointId))
      .WillOnce(Return(ScopedAStatus::ok()))
      .WillOnce(Return(ScopedAStatus::ok()));

  halClient->disconnectEndpoint(kEndpointId);
  halClient->disconnectEndpoint(kEndpointId);

  EXPECT_THAT(halClient->getConnectedEndpointIds(), IsEmpty());
}

TEST(HalClientTest, SendMessageBasic) {
  auto mockContextHub = ndk::SharedRefBase::make<MockContextHub>();
  const ContextHubMessage contextHubMessage = {
      .nanoappId = 0xbeef,
      .hostEndPoint = kEndpointId,
      .messageBody = {},
      .permissions = {},
  };
  auto halClient = std::make_unique<HalClientForTest>(
      mockContextHub, std::vector<HostEndpointId>{kEndpointId});

  EXPECT_CALL(*mockContextHub, sendMessageToHub(_, _))
      .WillOnce(Return(ScopedAStatus::ok()));
  halClient->sendMessage(contextHubMessage);
}

TEST(HalClientTest, QueryNanoapp) {
  auto mockContextHub = ndk::SharedRefBase::make<MockContextHub>();
  auto halClient = std::make_unique<HalClientForTest>(
      mockContextHub, std::vector<HostEndpointId>{});

  EXPECT_CALL(*mockContextHub, queryNanoapps(HalClient::kDefaultContextHubId));
  halClient->queryNanoapps();
}

TEST(HalClientTest, HandleChreRestart) {
  auto mockContextHub = ndk::SharedRefBase::make<MockContextHub>();

  auto halClient = std::make_unique<HalClientForTest>(
      mockContextHub,
      std::vector<HostEndpointId>{kEndpointId, kEndpointId + 1});

  EXPECT_CALL(*mockContextHub, onHostEndpointConnected(
                                   Field(&HostEndpointInfo::hostEndpointId, _)))
      .WillOnce(Return(ScopedAStatus::ok()))
      .WillOnce(Return(ScopedAStatus::ok()));

  halClient->getClientCallback()->handleContextHubAsyncEvent(
      AsyncEventType::RESTARTED);
  EXPECT_THAT(halClient->getConnectedEndpointIds(),
              UnorderedElementsAre(kEndpointId, kEndpointId + 1));
}

TEST(HalClientTest, IsConnected) {
  auto mockContextHub = ndk::SharedRefBase::make<MockContextHub>();

  auto halClient = std::make_unique<HalClientForTest>(
      mockContextHub,
      std::vector<HostEndpointId>{kEndpointId, kEndpointId + 1});

  EXPECT_THAT(halClient->isConnected(), true);
}
}  // namespace android::chre
