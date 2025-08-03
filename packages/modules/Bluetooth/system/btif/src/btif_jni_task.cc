/*
 * Copyright 2023 The Android Open Source Project
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

#include "btif/include/btif_jni_task.h"

#include <base/functional/bind.h>
#include <base/location.h>
#include <base/logging.h>
#include <base/threading/platform_thread.h>
#include <bluetooth/log.h>

#include <cstdint>
#include <utility>

#include "common/message_loop_thread.h"
#include "include/hardware/bluetooth.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_types.h"

using base::PlatformThread;
using namespace bluetooth;

static bluetooth::common::MessageLoopThread jni_thread("bt_jni_thread");

void jni_thread_startup() { jni_thread.StartUp(); }

void jni_thread_shutdown() { jni_thread.ShutDown(); }

/*******************************************************************************
 *
 * Function         btif_task
 *
 * Description      BTIF task handler managing all messages being passed
 *                  Bluetooth HAL and BTA.
 *
 * Returns          void
 *
 ******************************************************************************/
static void bt_jni_msg_ready(void* context) {
  tBTIF_CONTEXT_SWITCH_CBACK* p = (tBTIF_CONTEXT_SWITCH_CBACK*)context;
  if (p->p_cb) p->p_cb(p->event, p->p_param);
  osi_free(p);
}

/*******************************************************************************
 *
 * Function         btif_transfer_context
 *
 * Description      This function switches context to btif task
 *
 *                  p_cback   : callback used to process message in btif context
 *                  event     : event id of message
 *                  p_params  : parameter area passed to callback (copied)
 *                  param_len : length of parameter area
 *                  p_copy_cback : If set this function will be invoked for deep
 *                                 copy
 *
 * Returns          void
 *
 ******************************************************************************/

bt_status_t btif_transfer_context(tBTIF_CBACK* p_cback, uint16_t event,
                                  char* p_params, int param_len,
                                  tBTIF_COPY_CBACK* p_copy_cback) {
  tBTIF_CONTEXT_SWITCH_CBACK* p_msg = (tBTIF_CONTEXT_SWITCH_CBACK*)osi_malloc(
      sizeof(tBTIF_CONTEXT_SWITCH_CBACK) + param_len);

  log::verbose("btif_transfer_context event {}, len {}", event, param_len);

  /* allocate and send message that will be executed in btif context */
  p_msg->hdr.event = BT_EVT_CONTEXT_SWITCH_EVT; /* internal event */
  p_msg->p_cb = p_cback;

  p_msg->event = event; /* callback event */

  /* check if caller has provided a copy callback to do the deep copy */
  if (p_copy_cback) {
    p_copy_cback(event, p_msg->p_param, p_params);
  } else if (p_params) {
    memcpy(p_msg->p_param, p_params, param_len); /* callback parameter data */
  }

  return do_in_jni_thread(base::BindOnce(&bt_jni_msg_ready, p_msg));
}

/**
 * This function posts a task into the btif message loop, that executes it in
 * the JNI message loop.
 **/
bt_status_t do_in_jni_thread(const base::Location& from_here,
                             base::OnceClosure task) {
  if (!jni_thread.DoInThread(from_here, std::move(task))) {
    log::error("Post task to task runner failed!");
    return BT_STATUS_FAIL;
  }
  return BT_STATUS_SUCCESS;
}

bt_status_t do_in_jni_thread(base::OnceClosure task) {
  return do_in_jni_thread(FROM_HERE, std::move(task));
}

bool is_on_jni_thread() {
  return jni_thread.GetThreadId() == PlatformThread::CurrentId();
}

static void do_post_on_bt_jni(BtJniClosure closure) { closure(); }

void post_on_bt_jni(BtJniClosure closure) {
  ASSERT(do_in_jni_thread(FROM_HERE, base::BindOnce(do_post_on_bt_jni,
                                                    std::move(closure))) ==
         BT_STATUS_SUCCESS);
}
