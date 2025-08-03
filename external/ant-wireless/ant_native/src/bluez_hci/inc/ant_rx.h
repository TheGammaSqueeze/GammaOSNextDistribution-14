/*
 * ANT Stack
 *
 * Copyright 2009 Dynastream Innovations
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/******************************************************************************\
*
*   FILE NAME:      ant_rx.h
*
*   BRIEF:
*      This file defines the receive thread function and the ANTHCIRxParams
*      type for storing the configuration of the receive thread.
*
*
\******************************************************************************/

#ifndef __ANT_OS_H
#define __ANT_OS_H

#include <pthread.h>
#include "ant_types.h"
#include "ant_native.h"

// The configuration of a receive thread
typedef struct
{
   //The function to call back with received data
   ANTNativeANTEventCb pfRxCallback;

   //The function to call back with state changes
   ANTNativeANTStateCb pfStateCallback;

   //The thread object itself
   pthread_t thread;

} ANTHCIRxParams;

extern ANTHCIRxParams RxParams;

//The message receive thread
void* ANTHCIRxThread(void* pvHCIDevice);

#endif  /* __ANT_OS_H */

