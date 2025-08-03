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

#include "rpc_world_manager.h"

#include "chre/util/macros.h"
#include "chre/util/nanoapp/log.h"
#include "chre/util/time.h"

#ifndef LOG_TAG
#define LOG_TAG "[RpcWorld]"
#endif  // LOG_TAG

// [Server] Service implementations.
pw::Status RpcWorldService::Increment(const chre_rpc_NumberMessage &request,
                                      chre_rpc_NumberMessage &response) {
  RpcWorldManagerSingleton::get()->setPermissionForNextMessage(
      CHRE_MESSAGE_PERMISSION_NONE);
  response.number = request.number + 1;
  return pw::OkStatus();
}

void RpcWorldService::Timer(
    const chre_rpc_TimerRequest &request,
    RpcWorldService::ServerWriter<chre_rpc_TimerResponse> &writer) {
  RpcWorldManagerSingleton::get()->timerStart(request.num_ticks, writer);
}

void RpcWorldService::Add(
    RpcWorldService::ServerReader<chre_rpc_NumberMessage,
                                  chre_rpc_NumberMessage> &reader) {
  RpcWorldManagerSingleton::get()->addStart(reader);
}

// [Client] callbacks.
void incrementResponse(const chre_rpc_NumberMessage &response,
                       pw::Status status) {
  if (status.ok()) {
    LOGI("Increment response: %d", response.number);
  } else {
    LOGE("Increment failed with status %d", static_cast<int>(status.code()));
  }
}

void timerResponse(const chre_rpc_TimerResponse &response) {
  LOGI("Tick response: %d", response.tick_number);
}

void timerEnd(pw::Status status) {
  LOGI("Tick stream end: %d", static_cast<int>(status.code()));
}

void addResponse(const chre_rpc_NumberMessage &response, pw::Status status) {
  if (status.ok()) {
    LOGI("Add response: %d", response.number);
  } else {
    LOGE("Add failed with status %d", static_cast<int>(status.code()));
  }
}

bool RpcWorldManager::start() {
  chre::RpcServer::Service service = {.service = mRpcWorldService,
                                      .id = 0xca8f7150a3f05847,
                                      .version = 0x01020034};
  if (!mServer.registerServices(1 /*numServices*/, &service)) {
    LOGE("Error while registering the service");
  }

  auto client =
      mClient.get<chre::rpc::pw_rpc::nanopb::RpcWorldService::Client>();

  if (client.has_value()) {
    // [Client] Invoking a unary RPC.
    chre_rpc_NumberMessage incrementRequest;
    incrementRequest.number = 101;
    mIncrementCall = client->Increment(incrementRequest, incrementResponse);
    CHRE_ASSERT(mIncrementCall.active());

    // [Client] Invoking a server streaming RPC.
    chre_rpc_TimerRequest timerRequest;
    timerRequest.num_ticks = 5;
    mTimerCall = client->Timer(timerRequest, timerResponse, timerEnd);
    CHRE_ASSERT(mTimerCall.active());

    // [Client] Invoking a client streaming RPC.
    chre_rpc_NumberMessage addRequest;
    addRequest.number = 1;
    mAddCall = client->Add(addResponse);
    CHRE_ASSERT(mAddCall.active());
    mAddCall.Write(addRequest);
    mAddCall.Write(addRequest);
    mAddCall.Write(addRequest);
    mAddCall.RequestCompletion();
  } else {
    LOGE("Error while retrieving the client");
  }

  return true;
}

void RpcWorldManager::setPermissionForNextMessage(uint32_t permission) {
  mServer.setPermissionForNextMessage(permission);
}

void RpcWorldManager::handleEvent(uint32_t senderInstanceId, uint16_t eventType,
                                  const void *eventData) {
  if (!mServer.handleEvent(senderInstanceId, eventType, eventData)) {
    LOGE("[Server] An RPC error occurred");
  }

  if (!mClient.handleEvent(senderInstanceId, eventType, eventData)) {
    LOGE("[Client] An RPC error occurred");
  }

  switch (eventType) {
    case CHRE_EVENT_TIMER:
      // [Server] stream responses.
      chre_rpc_TimerResponse response;
      response.tick_number = mTimerCurrentTick;
      setPermissionForNextMessage(CHRE_MESSAGE_PERMISSION_NONE);
      mTimerWriter.Write(response);
      if (mTimerCurrentTick == mTimerTotalTicks) {
        setPermissionForNextMessage(CHRE_MESSAGE_PERMISSION_NONE);
        mTimerWriter.Finish(pw::OkStatus());
        if (chreTimerCancel(mTimerId)) {
          mTimerId = CHRE_TIMER_INVALID;
        } else {
          LOGE("Error while cancelling the timer");
        }
      }
      mTimerCurrentTick++;
  }
}

void RpcWorldManager::end() {
  mServer.close();
  mClient.close();
  if (mTimerId != CHRE_TIMER_INVALID) {
    chreTimerCancel(mTimerId);
  }
}

void RpcWorldManager::timerStart(
    uint32_t numTicks,
    RpcWorldService::ServerWriter<chre_rpc_TimerResponse> &writer) {
  mTimerCurrentTick = 1;
  mTimerTotalTicks = numTicks;
  mTimerWriter = std::move(writer);
  mTimerId = chreTimerSet(chre::kOneSecondInNanoseconds, nullptr /*cookie*/,
                          false /*oneShot*/);
}

void RpcWorldManager::addStart(
    RpcWorldService::ServerReader<chre_rpc_NumberMessage,
                                  chre_rpc_NumberMessage> &reader) {
  mSum = 0;
  reader.set_on_next([](const chre_rpc_NumberMessage &request) {
    RpcWorldManagerSingleton::get()->mSum += request.number;
  });
  reader.set_on_completion_requested([]() {
    chre_rpc_NumberMessage response;
    response.number = RpcWorldManagerSingleton::get()->mSum;
    RpcWorldManagerSingleton::get()->setPermissionForNextMessage(
        CHRE_MESSAGE_PERMISSION_NONE);
    RpcWorldManagerSingleton::get()->mAddReader.Finish(response);
  });
  mAddReader = std::move(reader);
}