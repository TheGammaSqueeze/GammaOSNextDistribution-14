/*
 * Copyright 2021 HIMSA II K/S - www.himsa.dk.
 * Represented by EHIMA - www.ehima.com
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

#include "mock_csis_client.h"

#include <base/logging.h>

static MockCsisClient* mock_csis_client;

void MockCsisClient::SetMockInstanceForTesting(MockCsisClient* mock) {
  mock_csis_client = mock;
}

void bluetooth::csis::CsisClient::Initialize(
    bluetooth::csis::CsisClientCallbacks* callbacks, base::Closure initCb) {
  LOG_ASSERT(mock_csis_client) << "Mock CsisClient interface not set!";
  mock_csis_client->Initialize(callbacks, initCb);
}

void bluetooth::csis::CsisClient::CleanUp() {
  LOG_ASSERT(mock_csis_client) << "Mock CsisClient interface not set!";
  mock_csis_client->CleanUp();
}

bluetooth::csis::CsisClient* bluetooth::csis::CsisClient::Get() {
  LOG_ASSERT(mock_csis_client) << "Mock CsisClient interface not set!";
  return mock_csis_client->Get();
}

void bluetooth::csis::CsisClient::DebugDump(int fd) {
  LOG_ASSERT(mock_csis_client) << "Mock CsisClient interface not set!";
  mock_csis_client->DebugDump(fd);
}

bool bluetooth::csis::CsisClient::IsCsisClientRunning() {
  LOG_ASSERT(mock_csis_client) << "Mock CsisClient interface not set!";
  return mock_csis_client->IsCsisClientRunning();
}
