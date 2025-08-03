/*
 * Copyright 2021 NXP
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

#if (NXP_NFC_RECOVERY == TRUE)

#ifndef __PHNXPNCIHAL_RECOVERY_H_
#define __PHNXPNCIHAL_RECOVERY_H_

#define NCI_MSG_RSP 0x40
#define NCI_MSG_NTF 0x60
#define NCI_RSP_IDX (0)
#define DL_RSP_IDX (0)
#define DL_RSP_STAT_IDX (2)
#define DL_RSP_SESS_IDX (3)
#define DL_MSG_RSP 0x00
#define DL_MSG_STAT_RSP 0x00
#define DL_SESSION_CLOSE_TAG 0x00

// timeout for tml read response
#define RESPONSE_READ_TIMEOUT_NS (200 * 1000 * 1000)
// timeout for tml write cmd
#define WRITE_TIMEOUT_NS (200 * 1000 * 1000)
// Time to wait for first NCI rest response
#define NCI_RESET_RESP_READ_DELAY_US (10000)

void phNxpNciHal_RecoverFWTearDown();
#endif
#endif
