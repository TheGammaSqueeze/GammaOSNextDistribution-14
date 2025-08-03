/******************************************************************************
 *
 *  Copyright 2020 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

#ifndef _WEAVER_UTILS_H_
#define _WEAVER_UTILS_H_

#include <log/log.h>
#include <string.h>

#define __FILENAME__                                                           \
  (strrchr(__FILE__, '/') ? strrchr(__FILE__, '/') + 1 : __FILE__)
#define TAG "Weaver-1.0"
#define UNUSED(x) (void)(x)
#define IS_NULL(value)                                                         \
  if (value == NULL)                                                           \
    ;
static bool is_weaver_debug =
    true; // configurable to enable disable debug logs.

#define LOG_D(tag, fmt, ...)                                                   \
  ALOGD_IF(is_weaver_debug, "%s::%d %s " fmt "\n", __FILENAME__, __LINE__,     \
           __FUNCTION__, ##__VA_ARGS__)
#define LOG_E(tag, fmt, ...)                                                   \
  ALOGE("%s::%d %s " fmt "\n", __FILENAME__, __LINE__, __FUNCTION__,           \
        ##__VA_ARGS__)

#define RETURN_IF_NULL(ptr, ret_value, msg)                                    \
  if (ptr == NULL) {                                                           \
    ALOGE(msg);                                                                \
    return ret_value;                                                          \
  }

#endif /* _WEAVER_UTILS_H__ */
