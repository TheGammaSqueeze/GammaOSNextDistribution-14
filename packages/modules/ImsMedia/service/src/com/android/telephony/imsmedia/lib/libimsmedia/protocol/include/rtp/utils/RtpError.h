/**
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

/** \addtogroup  RTP_Stack
 *  @{
 */

#ifndef __RTP_ERROR_H__
#define __RTP_ERROR_H__

#define RTP_ERRORCODES_START     100
#define RTP_ERRORCODES_GAP       100

#define RTP_ERRORCODES_UTILS     (RTP_ERRORCODES_START + (0 * RTP_ERRORCODES_GAP))
#define RTP_ERRORCODES_TXNTRANSP (RTP_ERRORCODES_START + (1 * RTP_ERRORCODES_GAP))
#define RTP_ERRORCODES_MSG       (RTP_ERRORCODES_START + (2 * RTP_ERRORCODES_GAP))

typedef enum _RtpEn_ErrorTypes
{
    RTP_UTIL_ERR_START = RTP_ERRORCODES_UTILS,
    ERR_LIST_INV_INPUT,
    ERR_MALLOC_FAILED,
    ERR_LIST_NO_COMPARE_FXN,
    ERR_LIST_ELEMENT_NOT_EXIST,
    ERR_INVALID_PARAM,
    ERR_INVALID_INPUT,
    ERR_TIMER_NOT_CREATED,
    ERR_TIMER_RESET_SUCCESS,
    ERR_TIMER_RESET_FAIL,
    ERR_HASH_ELEMENT_EXCEEDED,
    ERR_HASH_KEY_ALREADY_EXISTS,
    ERR_HASH_ELEMENT_NOT_FOUND,
    ERR_MUTEX_INIT_FAILED,
    TXN_INVALID = RTP_INVALID
} RtpEn_ErrorTypes;

#endif /* __RTP_ERROR_H__*/

/** @}*/
