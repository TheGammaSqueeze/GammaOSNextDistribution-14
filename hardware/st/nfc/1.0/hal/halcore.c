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
#define LOG_TAG "NfcHal"


#include <hardware/nfc.h>
#include "halcore_private.h"
#include "android_logmsg.h"
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <semaphore.h>

pthread_mutex_t debugOutputSem = PTHREAD_MUTEX_INITIALIZER;
bool halTraceMask = true;
extern int I2cWriteCmd(const uint8_t* x, size_t len);
extern void DispHal(const char* title, const void* data, size_t length);

extern uint32_t ScrProtocolTraceFlag;  // = SCR_PROTO_TRACE_ALL;

// HAL WRAPPER
static void HalStopTimer(HalInstance* inst);

typedef struct {
    struct nfc_nci_device nci_device;  // nci_device must be first struct member
    // below declarations are private variables within HAL
    nfc_stack_callback_t* p_cback;
    nfc_stack_data_callback_t* p_data_cback;
    HALHANDLE hHAL;
} st21nfc_dev_t;  // beware, is a duplication of structure in nfc_nci_st21nfc.c

/**************************************************************************************************
 *
 *                                      Private API Declaration
 *
 **************************************************************************************************/

static void* HalWorkerThread(void* arg);
static inline int sem_wait_nointr(sem_t *sem);

static void HalOnNewUpstreamFrame(HalInstance* inst, const uint8_t* data,
                                  size_t length);
static void HalTriggerNextDsPacket(HalInstance* inst);
static bool HalEnqueueThreadMessage(HalInstance* inst, ThreadMesssage* msg);
static bool HalDequeueThreadMessage(HalInstance* inst, ThreadMesssage* msg);
static HalBuffer* HalAllocBuffer(HalInstance* inst);
static HalBuffer* HalFreeBuffer(HalInstance* inst, HalBuffer* b);
static uint32_t HalSemWait(sem_t* pSemaphore, uint32_t timeout);

/**************************************************************************************************
 *
 *                                      Public API Entry-Points
 *
 **************************************************************************************************/

/**
 * Callback of HAL Core protocol layer.
 * Invoked by HAL worker thread according to if message is received from NCI
 * stack or posted by
 * I2C worker thread.
 * <p>@param context NFC callbacks for control/data
 * @param event Next HAL state machine action (send msg to I2C layer or report
 * data/control/error
 * to NFC task)
 * @param length Configure if debug and trace allowed, trace level
 */
void HalCoreCallback(void* context, uint32_t event, const void* d,
                     size_t length)
{
    const uint8_t* data = (const uint8_t*)d;
    uint8_t cmd = 'W';

    st21nfc_dev_t* dev = (st21nfc_dev_t*)context;

    switch (event) {
        case HAL_EVENT_DSWRITE:
            STLOG_HAL_V("!! got event HAL_EVENT_DSWRITE for %zu bytes\n", length);
            DispHal("TX DATA", (data), length);

            // Send write command to IO thread
            cmd = 'W';
            I2cWriteCmd(&cmd, sizeof(cmd));
            I2cWriteCmd((const uint8_t*)&length, sizeof(length));
            I2cWriteCmd(data, length);
            break;

        case HAL_EVENT_DATAIND:
            STLOG_HAL_V("!! got event HAL_EVENT_DATAIND for %zu bytes\n", length);

            if ((length >= 3) && (data[2] != (length - 3))) {
                STLOG_HAL_W("length is illogical. Header length is %d, packet length %zu\n",
                      data[2], length);
            }

            dev->p_data_cback(length, (uint8_t*)data);
            break;

        case HAL_EVENT_ERROR:
            STLOG_HAL_E("!! got event HAL_EVENT_ERROR\n");
            DispHal("Received unexpected HAL message !!!", data, length);
            break;

        case HAL_EVENT_LINKLOST:
            STLOG_HAL_E("!! got event HAL_EVENT_LINKLOST or HAL_EVENT_ERROR\n");

            dev->p_cback(HAL_NFC_ERROR_EVT, HAL_NFC_STATUS_ERR_CMD_TIMEOUT);

            // Write terminate command
            cmd = 'X';
            I2cWriteCmd(&cmd, sizeof(cmd));
            break;

        case HAL_EVENT_TIMER_TIMEOUT:
            STLOG_HAL_D("!! got event HAL_EVENT_TIMER_TIMEOUT \n");
            dev->p_cback(HAL_WRAPPER_TIMEOUT_EVT, HAL_NFC_STATUS_OK);

            //            dev->p_data_cback(0, NULL);
            break;
    }
}

/**
 * Connection to the HAL Core layer.
 * Set-up HAL context and create HAL worker thread.
 * <p>@param context NFC NCI device context, NFC callbacks for control/data, HAL
 * handle
 * @param callback HAL callback function pointer
 * @param flags Configure if debug and trace allowed, trace level
 */
HALHANDLE HalCreate(void* context, HAL_CALLBACK callback, uint32_t flags)
{
    halTraceMask = true;

    if (flags & HAL_FLAG_NO_DEBUG) {
        halTraceMask = false;
    }

    STLOG_HAL_V("HalCreate enter\n");

    HalInstance* inst = calloc(1, sizeof(HalInstance));

    if (!inst) {
        STLOG_HAL_E("!out of memory\n");
        return NULL;
    }

    // We need a semaphore to wakeup our protocol thread
    if (0 != sem_init(&inst->semaphore, 0, 0)) {
        STLOG_HAL_E("!sem_init failed\n");
        free(inst);
        return NULL;
    }

    // We need a semaphore to manage buffers
    if (0 != sem_init(&inst->bufferResourceSem, 0, NUM_BUFFERS)) {
        STLOG_HAL_E("!sem_init failed\n");
        sem_destroy(&inst->semaphore);
        free(inst);
        return NULL;
    }

    // We need a semaphore to block upstream data indications
    if (0 != sem_init(&inst->upstreamBlock, 0, 0)) {
        STLOG_HAL_E("!sem_init failed\n");
        sem_destroy(&inst->semaphore);
        sem_destroy(&inst->bufferResourceSem);
        free(inst);
        return NULL;
    }

    // Initialize remaining data-members
    inst->context = context;
    inst->callback = callback;
    inst->flags = flags;
    inst->freeBufferList = 0;
    inst->pendingNciList = 0;
    inst->nciBuffer = 0;
    inst->ringReadPos = 0;
    inst->ringWritePos = 0;
    inst->timeout = HAL_SLEEP_TIMER_DURATION;

    inst->bufferData = calloc(NUM_BUFFERS, sizeof(HalBuffer));
    if (!inst->bufferData) {
        STLOG_HAL_E("!failed to allocate memory\n");
        sem_destroy(&inst->semaphore);
        sem_destroy(&inst->bufferResourceSem);
        sem_destroy(&inst->upstreamBlock);
        free(inst);
        return NULL;
    }

    // Concatenate the buffers into a linked list for easy access
    size_t i;
    for (i = 0; i < NUM_BUFFERS; i++) {
        HalBuffer* b = &inst->bufferData[i];
        b->next = inst->freeBufferList;
        inst->freeBufferList = b;
    }

    if (0 != pthread_mutex_init(&inst->hMutex, 0))
      {
        STLOG_HAL_E("!failed to initialize Mutex \n");
        sem_destroy(&inst->semaphore);
        sem_destroy(&inst->bufferResourceSem);
        sem_destroy(&inst->upstreamBlock);
        free(inst->bufferData);
        free(inst);
        return NULL;
      }

    // Spawn the thread
    if (0 != pthread_create(&inst->thread, NULL, HalWorkerThread, inst)) {
        STLOG_HAL_E("!failed to spawn workerthread \n");
        sem_destroy(&inst->semaphore);
        sem_destroy(&inst->bufferResourceSem);
        sem_destroy(&inst->upstreamBlock);
        pthread_mutex_destroy(&inst->hMutex);
        free(inst->bufferData);
        free(inst);
        return NULL;
    }

    STLOG_HAL_V("HalCreate exit\n");
    return (HALHANDLE)inst;
}

/**
 * Disconnection of the HAL protocol layer.
 * Send message to stop the HAL worker thread and wait for it to finish. Free
 * resources.
 * @param hHAL HAL handle
 */
void HalDestroy(HALHANDLE hHAL)
{
    HalInstance* inst = (HalInstance*)hHAL;
    // Tell the thread that we want to finish
    ThreadMesssage msg;
    msg.command = MSG_EXIT_REQUEST;
    msg.payload = 0;
    msg.length = 0;

    HalEnqueueThreadMessage(inst, &msg);

    // Wait for thread to finish
    pthread_join(inst->thread, NULL);

    // Cleanup and exit
    sem_destroy(&inst->semaphore);
    sem_destroy(&inst->upstreamBlock);
    sem_destroy(&inst->bufferResourceSem);
    pthread_mutex_destroy(&inst->hMutex);

    // Free resources
    free(inst->bufferData);
    free(inst);

    STLOG_HAL_V("HalDestroy done\n");
}

/**
 * Send an NCI message downstream to HAL protocol layer (DH->NFCC transfer).
 * Block if more than NUM_BUFFERS (10) transfers are outstanding, otherwise will return immediately.
 * @param hHAL HAL handle
 * @param data Data message
 * @param size Message size
 */ bool HalSendDownstream(HALHANDLE hHAL, const uint8_t* data, size_t size)
{
    // Send an NCI frame downstream. will
    HalInstance* inst = (HalInstance*)hHAL;

    if ((size <= MAX_BUFFER_SIZE) && (size > 0)) {
        ThreadMesssage msg;
        HalBuffer* b = HalAllocBuffer(inst);

        if (!b) {
            // Should never be reachable
            return false;
        }

        memcpy(b->data, data, size);
        b->length = size;

        msg.command = MSG_TX_DATA;
        msg.payload = 0;
        msg.length = 0;
        msg.buffer = b;

        return HalEnqueueThreadMessage(inst, &msg);

    } else {
        STLOG_HAL_E("HalSendDownstream size to large %zu instead of %d\n", size,
              MAX_BUFFER_SIZE);
        return false;
    }
}

// HAL WRAPPER
/**
 * Send an NCI message downstream to HAL protocol layer (DH->NFCC transfer).
 * Block if more than NUM_BUFFERS (10) transfers are outstanding, otherwise will return immediately.
 * @param hHAL HAL handle
 * @param data Data message
 * @param size Message size
 */ bool HalSendDownstreamTimer(HALHANDLE hHAL, const uint8_t* data,
                                size_t size, uint8_t duration)
{
    // Send an NCI frame downstream. will
    HalInstance* inst = (HalInstance*)hHAL;

    if ((size <= MAX_BUFFER_SIZE) && (size > 0)) {
        ThreadMesssage msg;
        HalBuffer* b = HalAllocBuffer(inst);

        if (!b) {
            // Should never be reachable
            return false;
        }

        memcpy(b->data, data, size);
        b->length = size;

        msg.command = MSG_TX_DATA_TIMER_START;
        msg.payload = 0;
        msg.length = duration;
        msg.buffer = b;

        return HalEnqueueThreadMessage(inst, &msg);

    } else {
        STLOG_HAL_E("HalSendDownstreamTimer size to large %zu instead of %d\n", size,
              MAX_BUFFER_SIZE);
        return false;
    }
}

/**
 * Send an NCI message downstream to HAL protocol layer (DH->NFCC transfer).
 * Block if more than NUM_BUFFERS (10) transfers are outstanding, otherwise will
 * return immediately.
 * @param hHAL HAL handle
 * @param data Data message
 * @param size Message size
 */
bool HalSendDownstreamStopTimer(HALHANDLE hHAL)
{
    // Send an NCI frame downstream. will
    HalInstance* inst = (HalInstance*)hHAL;

    HalStopTimer(inst);
    return 1;


}

/**
 * Send an NCI message upstream to NFC NCI layer (NFCC->DH transfer).
 * @param hHAL HAL handle
 * @param data Data message
 * @param size Message size
 */ bool HalSendUpstream(HALHANDLE hHAL, const uint8_t* data, size_t size)
{
    HalInstance* inst = (HalInstance*)hHAL;
    if ((size <= MAX_BUFFER_SIZE) && (size > 0)) {
        ThreadMesssage msg;
        msg.command = MSG_RX_DATA;
        msg.payload = data;
        msg.length = size;

        if (HalEnqueueThreadMessage(inst, &msg)) {
            // Block until the protocol has taken a copy of the data
            sem_wait_nointr(&inst->upstreamBlock);
            return true;
        }
        return false;
    } else {
        STLOG_HAL_E("HalSendUpstream size to large %zu instead of %d\n", size,
              MAX_BUFFER_SIZE);
        return false;
    }
}

/**************************************************************************************************
 *
 *                                      Private API Definition
 *
 **************************************************************************************************/
/*
 * Get current time stamp
 */
struct timespec HalGetTimestamp(void)
{
    struct timespec tm;
    clock_gettime(CLOCK_REALTIME, &tm);
    return tm;
}

int HalTimeDiffInMs(struct timespec start, struct timespec end)
{
    struct timespec temp;
    if ((end.tv_nsec - start.tv_nsec) < 0) {
        temp.tv_sec = end.tv_sec - start.tv_sec - 1;
        temp.tv_nsec = 1000000000 + end.tv_nsec - start.tv_nsec;
    } else {
        temp.tv_sec = end.tv_sec - start.tv_sec;
        temp.tv_nsec = end.tv_nsec - start.tv_nsec;
    }

    return (temp.tv_nsec / 1000000) + (temp.tv_sec * 1000);
}


/**
 * Determine the next shortest sleep to fulfill the pending timer requirements.
 * @param inst HAL instance
 * @param now timespec structure for time definition
 */
static uint32_t HalCalcSemWaitingTime(HalInstance* inst, struct timespec* now)
{
    // Default to infinite wait time
    uint32_t result = OS_SYNC_INFINITE;

    if (inst->timer.active) {
        int delta =
            inst->timer.duration - HalTimeDiffInMs(inst->timer.startTime, *now);

        if (delta < 0) {
            // If we have a timer that has already expired, pick a zero wait time
            result = 0;

        } else if ((uint32_t)delta < result) {
            // Smaller time difference? If so take it
            result = delta;
        }
    }

    if (result != OS_SYNC_INFINITE) {
        // Add one millisecond on top of that, so the waiting semaphore will time
        // out just a moment
        // after the timer should expire
        result += 1;
    }

    return result;
}

/**************************************************************************************************
 *
 *                                     Timer Management
 *
 **************************************************************************************************/

static void HalStopTimer(HalInstance* inst)
{
    inst->timer.active = false;
    STLOG_HAL_D("HalStopTimer \n");
}

static void HalStartTimer(HalInstance* inst, uint32_t duration)
{
    STLOG_HAL_D("HalStartTimer \n");
    inst->timer.startTime = HalGetTimestamp();
    inst->timer.active = true;
    inst->timer.duration = duration;
}

/**************************************************************************************************
 *
 *                                     Thread Message Queue
 *
 **************************************************************************************************/

/**
 * Write message pointer to small ring buffer for queuing HAL messages.
 * @param inst HAL instance
 * @param msg Message to send
 * @return true if message properly copied in ring buffer
 */
static bool HalEnqueueThreadMessage(HalInstance* inst, ThreadMesssage* msg)
{
    // Put a message to the queue
    int nextWriteSlot;
    bool result = true;

    pthread_mutex_lock(&inst->hMutex);

    nextWriteSlot = inst->ringWritePos + 1;

    if (nextWriteSlot == HAL_QUEUE_MAX) {
        nextWriteSlot = 0;
    }

    // Check that we don't overflow the queue entries
    if (nextWriteSlot == inst->ringReadPos) {
        STLOG_HAL_E("HAL thread message ring: RNR (implement me!!)");
        result = false;
    }

    if (result) {
        // inst->ring[nextWriteSlot] = *msg;
        memcpy(&(inst->ring[nextWriteSlot]), msg, sizeof(ThreadMesssage));
        inst->ringWritePos = nextWriteSlot;
    }

    pthread_mutex_unlock(&inst->hMutex);

    if (result) {
        sem_post(&inst->semaphore);
    }

    return result;
}

/**
 * Remove message pointer from stored ring buffer.
 * @param inst HAL instance
 * @param msg Message received
 * @return true if there is a new message to pull, false otherwise.
 */
static bool HalDequeueThreadMessage(HalInstance* inst, ThreadMesssage* msg)
{
    int nextCmdIndex;
    bool result = true;
    // New data available
    pthread_mutex_lock(&inst->hMutex);

    // Get new timer read index
    nextCmdIndex = inst->ringReadPos + 1;

    if (nextCmdIndex == HAL_QUEUE_MAX) {
        nextCmdIndex = 0;
    }
     //check if ring buffer is empty
    if (inst->ringReadPos == inst->ringWritePos)
      {
        STLOG_HAL_E("HAL thread message ring: already read last valid data");
        result = false;
      }

    // Get new element from ringbuffer
    if (result) {
    memcpy(msg, &(inst->ring[nextCmdIndex]), sizeof(ThreadMesssage));
    inst->ringReadPos = nextCmdIndex;
    }

    pthread_mutex_unlock(&inst->hMutex);

    return result;
}

/**************************************************************************************************
 *
 *                                     Buffer/Memory Management
 *
 **************************************************************************************************/

/**
 * Allocate buffer from pre-allocated pool.
 * @param inst HAL instance
 * @return Pointer to allocated HAL buffer
 */
static HalBuffer* HalAllocBuffer(HalInstance* inst)
{
    HalBuffer* b;

    // Wait until we have a buffer resource
    sem_wait_nointr(&inst->bufferResourceSem);

    pthread_mutex_lock(&inst->hMutex);

    b = inst->freeBufferList;
    if (b) {
        inst->freeBufferList = b->next;
        b->next = 0;
    }

    pthread_mutex_unlock(&inst->hMutex);

    if (!b) {
        STLOG_HAL_E(
            "! unable to allocate buffer resource."
            "check bufferResourceSem\n");
    }

    return b;
}

/**
 * Return buffer to pool.
 * @param inst HAL instance
 * @param b Pointer of HAL buffer to free
 * @return Pointer of freed HAL buffer
 */
static HalBuffer* HalFreeBuffer(HalInstance* inst, HalBuffer* b)
{
    pthread_mutex_lock(&inst->hMutex);

    b->next = inst->freeBufferList;
    inst->freeBufferList = b;

    pthread_mutex_unlock(&inst->hMutex);

    // Unblock treads waiting for a buffer
    sem_post(&inst->bufferResourceSem);

    return b;
}

/**************************************************************************************************
 *
 *                                     State Machine
 *
 **************************************************************************************************/

/**
 * Event handler for HAL message
 * @param inst HAL instance
 * @param e HAL event
 */
static void Hal_event_handler(HalInstance* inst, HalEvent e)
{
    switch (e) {
        case EVT_RX_DATA: {
            // New data packet arrived
            const uint8_t* nciData;
            size_t nciLength;

            // Extract raw NCI data from frame
            nciData = inst->lastUsFrame;
            nciLength = inst->lastUsFrameSize;

            // Pass received raw NCI data to stack
            inst->callback(inst->context, HAL_EVENT_DATAIND, nciData, nciLength);
        }
        break;

        case EVT_TX_DATA:
            // NCI data arrived from stack
            // Send data
            inst->callback(inst->context, HAL_EVENT_DSWRITE, inst->nciBuffer->data,
                           inst->nciBuffer->length);

            // Free the buffer
            HalFreeBuffer(inst, inst->nciBuffer);
            inst->nciBuffer = 0;
            break;

        // HAL WRAPPER
        case EVT_TIMER:
            inst->callback(inst->context, HAL_EVENT_TIMER_TIMEOUT, NULL, 0);
            break;
    }
}

/**************************************************************************************************
 *
 *                                     HAL Worker Thread
 *
 **************************************************************************************************/

/**
 * HAL worker thread to serialize all actions into a single thread.
 * RX/TX/TIMER are dispatched from here.
 * @param arg HAL instance arguments
 */
static void* HalWorkerThread(void* arg)
{
    HalInstance* inst = (HalInstance*)arg;
    inst->exitRequest = false;

    STLOG_HAL_V("thread running\n");

    while (!inst->exitRequest) {
        struct timespec now = HalGetTimestamp();
        uint32_t waitResult =
            HalSemWait(&inst->semaphore, HalCalcSemWaitingTime(inst, &now));

        switch (waitResult) {
            case OS_SYNC_TIMEOUT: {
                // One or more times have expired
                STLOG_HAL_W("OS_SYNC_TIMEOUT\n");
                now = HalGetTimestamp();

                // HAL WRAPPER
                // callback to hal wrapper
                // Unblock
                sem_post(&inst->upstreamBlock);

                // Data frame
                Hal_event_handler(inst, EVT_TIMER);
            }
            break;

            case OS_SYNC_RELEASED: {
                // A message arrived
                ThreadMesssage msg;

                if (HalDequeueThreadMessage(inst, &msg)) {
                    switch (msg.command) {
                        case MSG_EXIT_REQUEST:

                            STLOG_HAL_V("received exit request from upper layer\n");
                            inst->exitRequest = true;
                            break;

                        case MSG_TX_DATA:
                            STLOG_HAL_V("received new NCI data from stack\n");

                            // Attack to end of list
                            if (!inst->pendingNciList) {
                                inst->pendingNciList = msg.buffer;
                                inst->pendingNciList->next = 0;
                            } else {
                                // Find last element of the list. b->next is zero for this
                                // element
                                HalBuffer* b;
                                for (b = inst->pendingNciList; b->next; b = b->next) {
                                };

                                // Concatenate to list
                                b->next = msg.buffer;
                                msg.buffer->next = 0;
                            }

                            // Start transmitting if we're in the correct state
                            HalTriggerNextDsPacket(inst);
                            break;

                        // HAL WRAPPER
                        case MSG_TX_DATA_TIMER_START:
                            STLOG_HAL_V("received new NCI data from stack, need timer start\n");

                            // Attack to end of list
                            if (!inst->pendingNciList) {
                                inst->pendingNciList = msg.buffer;
                                inst->pendingNciList->next = 0;
                            } else {
                                // Find last element of the list. b->next is zero for this
                                // element
                                HalBuffer* b;
                                for (b = inst->pendingNciList; b->next; b = b->next) {
                                };

                                // Concatenate to list
                                b->next = msg.buffer;
                                msg.buffer->next = 0;
                            }

                            // Start timer
                            HalStartTimer(inst, msg.length);

                            // Start transmitting if we're in the correct state
                            HalTriggerNextDsPacket(inst);
                            break;

                        case MSG_RX_DATA:
                            STLOG_HAL_D("received new data from CLF\n");
                            HalOnNewUpstreamFrame(inst, msg.payload, msg.length);
                            break;

                        default:
                            STLOG_HAL_E("!received unkown thread message?\n");
                            break;
                    }
                } else {
                    STLOG_HAL_E("!got wakeup in workerthread, but no message here? ?\n");

            }
            }
            break;

            case OS_SYNC_FAILED:

              STLOG_HAL_E(
                    "!Something went horribly wrong.. The semaphore wait function "
                    "failed\n");
                inst->exitRequest = true;
                break;
        }
    }

    STLOG_HAL_D("thread about to exit\n");
    return NULL;
}

/**************************************************************************************************
 *
 *                                     Misc. Functions
 *
 **************************************************************************************************/
/**
 *  helper to make sem_t interrupt safe
 * @param sem_t  semaphore
 * @return sem_wait return value.
 */

static inline int sem_wait_nointr(sem_t *sem) {
  while (sem_wait(sem))
    if (errno == EINTR) errno = 0;
    else return -1;
  return 0;
}

/**
 * Handle RX frames here first in HAL context.
 * @param inst HAL instance
 * @param data HAL data received from I2C worker thread
 * @param length Size of HAL data
 */
static void HalOnNewUpstreamFrame(HalInstance* inst, const uint8_t* data,
                                  size_t length)
{
    memcpy(inst->lastUsFrame, data, length);
    inst->lastUsFrameSize = length;

    // Data frame
    Hal_event_handler(inst, EVT_RX_DATA);
    // Allow the I2C thread to get the next message (if done early, it may
    // overwrite before handled)
    sem_post(&inst->upstreamBlock);
}

/**
 * Send out the next queued up buffer for TX if any.
 * @param inst HAL instance
 */
static void HalTriggerNextDsPacket(HalInstance* inst)
{
    // Check if we have something to transmit downstream
    HalBuffer* b = inst->pendingNciList;

    if (b) {
        // Get the buffer from the pending list
        inst->pendingNciList = b->next;
        inst->nciBuffer = b;

        STLOG_HAL_V("trigger transport of next NCI data downstream\n");
        // Process the new nci frame
        Hal_event_handler(inst, EVT_TX_DATA);

    } else {
        STLOG_HAL_V("no new NCI data to transmit, enter wait..\n");
    }
}

/*
 * Wait for given semaphore signaling a specific time or ever
 * param sem_t * pSemaphore
 * param uint32_t timeout
 * return uint32_t
 */
static uint32_t HalSemWait(sem_t* pSemaphore, uint32_t timeout)
{
    uint32_t result = OS_SYNC_RELEASED;
    bool gotResult = false;

    if (timeout == OS_SYNC_INFINITE) {
        while (!gotResult) {
            if (sem_wait(pSemaphore) == -1) {
                int e = errno;
                char msg[200];

                if (e == EINTR) {
                    STLOG_HAL_W(
                        "! semaphore (infin) wait interrupted by system signal. re-enter "
                        "wait");
                    continue;
                }

                strerror_r(e, msg, sizeof(msg) - 1);
                STLOG_HAL_E("! semaphore (infin) wait failed. sem=0x%p, %s", pSemaphore, msg);
                gotResult = true;
                result = OS_SYNC_FAILED;
            } else {
                gotResult = true;
            }
        };
    } else {
        struct timespec tm;
        long oneSecInNs = (int)1e9;

        clock_gettime(CLOCK_REALTIME, &tm);

        /* add timeout (can't overflow): */
        tm.tv_sec += (timeout / 1000);
        tm.tv_nsec += ((timeout % 1000) * 1000000);

        /* make sure nanoseconds are below a million */
        if (tm.tv_nsec >= oneSecInNs) {
            tm.tv_sec++;
            tm.tv_nsec -= oneSecInNs;
        }

        while (!gotResult) {
            if (sem_timedwait(pSemaphore, &tm) == -1) {
                int e = errno;

                if (e == EINTR) {
                    /* interrupted by signal? repeat sem_wait again */
                    continue;
                }

                if (e == ETIMEDOUT) {
                    result = OS_SYNC_TIMEOUT;
                    gotResult = true;
                } else {
                    result = OS_SYNC_FAILED;
                    gotResult = true;
                }
            } else {
                gotResult = true;
            }
        }
    }
    return result;
}
