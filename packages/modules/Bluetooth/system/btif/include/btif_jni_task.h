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

#pragma once

#include "btif/include/btif_common.h"
#include "include/hardware/bluetooth.h"

void jni_thread_startup();
void jni_thread_shutdown();

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
                                  tBTIF_COPY_CBACK* p_copy_cback);

/**
 * This function posts a task into the btif message loop, that executes it in
 * the JNI message loop.
 **/
bt_status_t do_in_jni_thread(const base::Location& from_here,
                             base::OnceClosure task);

bt_status_t do_in_jni_thread(base::OnceClosure task);

bool is_on_jni_thread();

void post_on_bt_jni(BtJniClosure closure);
