/******************************************************************************
 *
 *  Copyright 2001-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#define LOG_TAG "bt_bte"

#include <cstdarg>
#include <cstdint>

#include "internal_include/bt_trace.h"
#include "internal_include/stack_config.h"
#include "main/main_int.h"

static future_t* init(void) {
  const stack_config_t* stack_config = stack_config_get_interface();
  init_cpp_logging(stack_config->get_all());
  return NULL;
}

EXPORT_SYMBOL extern const module_t bte_logmsg_module = {
    .name = BTE_LOGMSG_MODULE,
    .init = init,
    .start_up = NULL,
    .shut_down = NULL,
    .clean_up = NULL,
    .dependencies = {STACK_CONFIG_MODULE, NULL}};
