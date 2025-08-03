/******************************************************************************
 *
 *  Copyright 2014 Google, Inc.
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

#pragma once

///// LEGACY DEFINITIONS /////

/* Message event mask across Host/Controller lib and stack */
#define MSG_EVT_MASK 0xFF00     /* eq. BT_EVT_MASK */
#define MSG_SUB_EVT_MASK 0x00FF /* eq. BT_SUB_EVT_MASK */

/* Message event ID passed from Host/Controller lib to stack */
#define MSG_HC_TO_STACK_HCI_ERR 0x1300      /* eq. BT_EVT_TO_BTU_HCIT_ERR */
#define MSG_HC_TO_STACK_HCI_ISO 0x1700      /* eq. BT_EVT_TO_BTU_HCI_ISO */
#define MSG_HC_TO_STACK_HCI_EVT 0x1000      /* eq. BT_EVT_TO_BTU_HCI_EVT */

/* Message event ID passed from stack to vendor lib */
#define MSG_STACK_TO_HC_HCI_ISO 0x2d00 /* eq. BT_EVT_TO_LM_HCI_ISO */
#define MSG_STACK_TO_HC_HCI_CMD 0x2000 /* eq. BT_EVT_TO_LM_HCI_CMD */

/* Local Bluetooth Controller ID for BR/EDR */
#define LOCAL_BR_EDR_CONTROLLER_ID 0

///// END LEGACY DEFINITIONS /////
