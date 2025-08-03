/*******************************************************************************
 *
 *  Copyright 2019 NXP
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

#ifndef ESE_UPDATE_3_H_
#define ESE_UPDATE_3_H_
#include <unistd.h>

extern bool nfc_debug_enabled;

typedef void (*fpCheckEseClientUpdate_t)();
typedef void (*fpPerformEseClientUpdate_t)();
typedef void (*fpEseClientUpdate_Nfc_Thread_t)();
typedef void (*fpSeteSEClientState_t)(uint8_t state);

void initializeEseClient();
void checkEseClientUpdate();
void perform_eSEClientUpdate();
void seteSEClientState(uint8_t state);
void eSEClientUpdate_NFC_Thread();
void deinitializeEseClient();

#endif
