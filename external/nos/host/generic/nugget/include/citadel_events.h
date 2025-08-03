/*
 * Copyright (C) 2019 The Android Open Source Project
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
#ifndef __CROS_EC_INCLUDE_CITADEL_EVENTS_H
#define __CROS_EC_INCLUDE_CITADEL_EVENTS_H

#include <assert.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#ifndef __packed
#define __packed __attribute__((packed))
#endif

/*
 * When Citadel needs to tell the AP something without waiting to be asked, the
 * process is as follows:
 *
 *   1. Citadel adds an event_report to its internal queue, then asserts
 *      the CTDL_AP_IRQ signal to notify the AP.
 *
 *   2. The AP (citadeld) requests pending events from Citadel until they've
 *      all been retrieved.
 *
 *   3. Citadel deasserts CTDL_AP_IRQ.
 *
 * Because we may want to compare the history and evolution of events over a
 * long time and for multiple releases, we should only APPEND to this file
 * instead of changing things.
 */

/*
 * Event priority.  Stored events of lower priority will be evicted to store
 * higher-priority events if the queue is full.
 */
enum event_priority {
  EVENT_PRIORITY_LOW = 0,
  EVENT_PRIORITY_MEDIUM = 1,
  EVENT_PRIORITY_HIGH = 2,
};

/*
 * Event ID values live forever.
 * Add to the list, but NEVER change or delete existing entries.
 */
enum event_id {
  EVENT_NONE = 0,          // Unused ID, used as empty marker.
  EVENT_ALERT = 1,         // Globalsec alert fired.
  EVENT_REBOOTED = 2,      // Device rebooted.
  EVENT_UPGRADED = 3,      // Device has upgraded.
  EVENT_ALERT_V2 = 4,      // Globalsec Alertv2 fired
  EVENT_SEC_CH_STATE = 5,  // Update GSA-GSC secure channel state.
  EVENT_V1_NO_SUPPORT =
      6  // Report a VXX event that can't fit in struct event_report.
};

/*
 * Upgrade state definition.
 */
enum upgrade_state_def {
  UPGRADE_SUCCESS = 0,
  UPGRADE_PW_MISMATCH = 1,
  UPGRADE_EN_FW_FAIL =2,
};

/*
 * Big event header flags.
 */
enum hdr_flags {
  HDR_FLAG_EMPTY_SLOT = 0,    // Used to determine empty slot.
  HDR_FLAG_OCCUPIED_SLOT = 1  // Used to indicate an occupied slot.
};

/* Please do not change the size of this struct */
#define EVENT_REPORT_SIZE 64
struct event_report {
  uint64_t reset_count;                 /* zeroed by Citadel power cycle */
  uint64_t uptime_usecs;                /* since last Citadel reset */
  uint32_t id;
  uint32_t priority;
  union {
    /* id-specific information goes here */
    struct {
      uint32_t intr_sts[3];
    } alert;
    struct {
      uint32_t rstsrc;
      uint32_t exitpd;
      uint32_t which0;
      uint32_t which1;
    } rebooted;
    struct {
      uint32_t upgrade_state;
    } upgraded;
    struct {
      uint32_t alert_grp[4];
      uint16_t camo_breaches[2];
      uint16_t temp_min;
      uint16_t temp_max;
      uint32_t bus_err;
    } alert_v2;
    struct {
      uint32_t state;
    } sec_ch_state;

    /* uninterpreted */
    union {
      uint32_t w[10];
      uint16_t h[20];
      uint8_t b[40];
    } raw;
  } event;
} __packed;
/* Please do not change the size of this struct */
static_assert(sizeof(struct event_report) == EVENT_REPORT_SIZE,
              "Muting the Immutable");

struct big_event_report {
  struct hdr {
    /* Redundant w.r.t. to v1 event records */
    uint64_t reset_count;
    uint64_t uptime_usecs;
    uint32_t priority;

    uint8_t version;
    uint8_t flags;
    uint16_t length;
  } hdr;
  uint8_t data[384];
} __packed;

#ifdef __cplusplus
}
#endif

#endif  /* __CROS_EC_INCLUDE_CITADEL_EVENTS_H */
