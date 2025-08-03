/** ----------------------------------------------------------------------
 *
 * Copyright (C) 2016 ST Microelectronics S.A.
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
 *
 *
 ----------------------------------------------------------------------*/
#ifndef __HALCORE_PRIVATE_
#define __HALCORE_PRIVATE_

#include <pthread.h>
#include <semaphore.h>
#include <stdint.h>
#include <time.h>
#include "halcore.h"

#define MAX_NCIFRAME_PAYLOAD_SIZE 255
#define MAX_HEADER_SIZE 3

#define MAX_BUFFER_SIZE (MAX_NCIFRAME_PAYLOAD_SIZE + MAX_HEADER_SIZE)

// the three NCI bytes:
// Octet1 and Octet 2: connection-id and stuff
// Octet3: length of payload (in bytes)

/* ----------------------------------------------------------------------------------------------*/
/* ----------------------------------------------------------------------------------------------*/
/* ----------------------------------------------------------------------------------------------*/
/* ----------------------------------------------------------------------------------------------*/
/* ----------------------------------------------------------------------------------------------*/

#define HAL_QUEUE_MAX \
  8 /* max. # of messages enqueued before going into blocking mode */

/* thread messages  */
#define MSG_EXIT_REQUEST 0 /* worker thread should terminate itself */
#define MSG_TX_DATA 1      /* send a message downstream */
#define MSG_RX_DATA 2      /* a new message has arrived from lower layer */

// HAL _WRAPPER
#define MSG_TX_DATA_TIMER_START 3
#define MSG_TIMER_START 4

/* number of buffers used for incoming & outgoing data */
#define NUM_BUFFERS 10

/* constants for the return value of osWait */
#define OS_SYNC_INFINITE 0xffffffffu
#define OS_SYNC_RELEASED 0
#define OS_SYNC_TIMEOUT 1
#define OS_SYNC_FAILED 0xffffffffu

/* default timeouts */
#define HAL_SLEEP_TIMER 0
#define HAL_SLEEP_TIMER_DURATION 500 /* ordinary t1 timeout to resent data */

#define NCI_MT_SHIFT 5
#define NCI_MT_CMD 1 /* (NCI_MT_CMD << NCI_MT_SHIFT) = 0x20 */

#define NCI_MSG_CORE_SET_CONFIG 2

#define NCI_GID_CORE 0x00      /* 0000b NCI Core group */
#define NCI_GID_PROP 0x0F      /* 1111b Proprietary */

#define NCI_MSG_PROP_ANDROID 0x0C

#define NCI_ANDROID_SIGNAL_STRENGTH_NTF 0x01
#define NCI_ANDROID_FIELD_CHANGE_NTF 0x02
#define NCI_ANDROID_POLLING_FRAME_NTF 0x03

#define NCI_ANDROID_PASSIVE_OBSERVER 0x2

#define NCI_ANDROID_PASSIVE_OBSERVER_PARAM_SIZE 0x2

typedef struct tagHalBuffer {
  uint8_t data[MAX_BUFFER_SIZE];
  size_t length;
  struct tagHalBuffer* next;
} HalBuffer;

typedef struct tagThreadMessage {
  uint32_t command;    /* message type / command */
  const void* payload; /* ptr to message related data item */
  size_t length;       /* length of above payload */
  HalBuffer* buffer;   /* buffer object (optional) */
} ThreadMesssage;

typedef enum {
  EVT_RX_DATA = 0,
  EVT_TX_DATA = 1,
  // HAL WRAPPER
  EVT_TIMER = 2,
} HalEvent;

typedef struct tagTimer {
  struct timespec startTime; /* start time (CLOCK_REALTIME)       */
  uint32_t duration;         /* timer duration in milliseconds    */
  bool active;               /* true if timer is currently active */
} Timer;

typedef struct tagHalInstance {
  uint32_t flags;

  void* context;
  HAL_CALLBACK callback;

  /* current timeout values */
  uint32_t timeout;
  Timer timer;

  /* threading and runtime support */
  bool exitRequest;
  sem_t semaphore;
  pthread_t thread;
  pthread_mutex_t hMutex; /* guards the message ringbuffer */

  /* IOBuffers for read/writes */
  HalBuffer* bufferData;
  HalBuffer* freeBufferList;
  HalBuffer* pendingNciList; /* outgoing packages waiting to be processed */
  HalBuffer* nciBuffer;      /* current buffer in progress */
  sem_t bufferResourceSem;

  sem_t upstreamBlock;

  /* message ring-buffer */
  ThreadMesssage ring[HAL_QUEUE_MAX];
  int ringReadPos;
  int ringWritePos;

  /* current frame going downstream */
  uint8_t lastDsFrame[MAX_BUFFER_SIZE];
  size_t lastDsFrameSize;

  /* current frame from CLF */
  uint8_t lastUsFrame[MAX_BUFFER_SIZE];
  size_t lastUsFrameSize;

} HalInstance;

#endif
