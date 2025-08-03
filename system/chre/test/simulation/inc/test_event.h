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

#ifndef CHRE_SIMULATION_TEST_EVENT_H_
#define CHRE_SIMULATION_TEST_EVENT_H_

#include "chre/event.h"

/**
 * First possible value for CHRE_EVENT_SIMULATION_TEST events. These events are
 * reserved for utility events that can be used by any simulation test.
 */
#define CHRE_EVENT_SIMULATION_TEST_FIRST_EVENT CHRE_EVENT_FIRST_USER_VALUE

/**
 * Produce an event ID in the block of IDs reserved for CHRE simulation test
 * events.
 *
 * @param offset Index into simulation test event ID block; valid range is [0,
 * 0xFFF].
 *
 * @defgroup CHRE_SIMULATION_TEST_EVENT_ID
 * @{
 */
#define CHRE_SIMULATION_TEST_EVENT_ID(offset) \
  (CHRE_EVENT_SIMULATION_TEST_FIRST_EVENT + (offset))

/**
 * First possible value for CHRE_EVENT_SPECIFIC_SIMULATION_TEST events. Each
 * simulation test can define specific events for its use case.
 */
#define CHRE_EVENT_SPECIFIC_SIMULATION_TEST_FIRST_EVENT \
  CHRE_EVENT_FIRST_USER_VALUE + 0x1000

/**
 * Produce an event ID in the block of IDs reserved for events belonging to a
 * specific CHRE simulation test.
 *
 * @param offset Index into the event ID block of a specific simulation test;
 * valid range is [0, 0xFFF].
 *
 * @defgroup CHRE_SIMULATION_TEST_EVENT_ID
 * @{
 */
#define CHRE_SPECIFIC_SIMULATION_TEST_EVENT_ID(offset) \
  (CHRE_EVENT_SPECIFIC_SIMULATION_TEST_FIRST_EVENT + (offset))

/**
 * Produce an event ID in the block of IDs reserved for events belonging to a
 * specific CHRE simulation test.
 *
 * @param offset Index into the event ID block of a specific simulation test;
 * valid range is [0, 0xFFF].
 *
 * @defgroup CHRE_SIMULATION_TEST_EVENT_ID
 * @{
 */
#define CREATE_CHRE_TEST_EVENT(name, offset) \
  constexpr uint16_t name = CHRE_SPECIFIC_SIMULATION_TEST_EVENT_ID(offset)

#define CHRE_EVENT_TEST_EVENT CHRE_EVENT_FIRST_USER_VALUE + 0x2000

/**
 * Events used to communicate to and from the test nanoapps.
 */
struct TestEvent {
  uint16_t type;
  void *data = nullptr;
};

#endif  // CHRE_SIMULATION_TEST_EVENT_H_