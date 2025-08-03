/*
 * ANT Stack
 *
 * Copyright 2011 Dynastream Innovations
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
*   FILE NAME:      ant_rx_chardev.c
*
*   BRIEF:
*      This file implements the receive thread function which will loop reading
*      ANT messages until told to exit.
*
*
\******************************************************************************/

#include <errno.h>
#include <string.h>
#include <poll.h>
#include <pthread.h>
#include <stdint.h> /* for uint64_t */
#include <string.h>

#include "ant_types.h"
#include "antradio_power.h"
#include "ant_rx_chardev.h"
#include "ant_hci_defines.h"
#include "ant_log.h"
#include "ant_native.h"  // ANT_HCI_MAX_MSG_SIZE, ANT_MSG_ID_OFFSET, ANT_MSG_DATA_OFFSET,
                         // ant_radio_enabled_status()

extern ANTStatus ant_tx_message_flowcontrol_none(ant_channel_type eTxPath, ANT_U8 ucMessageLength, ANT_U8 *pucTxMessage);

#undef LOG_TAG
#define LOG_TAG "antradio_rx"

#define ANT_POLL_TIMEOUT         ((int)30000)
#define KEEPALIVE_TIMEOUT        ((int)5000)

static ANT_U8 aucRxBuffer[NUM_ANT_CHANNELS][ANT_HCI_MAX_MSG_SIZE];

#ifdef ANT_DEVICE_NAME // Single transport path
	static int iRxBufferLength[NUM_ANT_CHANNELS] = {0};
#else
	static int iRxBufferLength[NUM_ANT_CHANNELS] = {0, 0};
#endif // 

// Defines for use with the poll() call
#define EVENT_DATA_AVAILABLE (POLLIN|POLLRDNORM)
#define EVENT_CHIP_SHUTDOWN (POLLHUP)
#define EVENT_HARD_RESET (POLLERR|POLLPRI|POLLRDHUP)

#define EVENTS_TO_LISTEN_FOR (EVENT_DATA_AVAILABLE|EVENT_CHIP_SHUTDOWN|EVENT_HARD_RESET)

// Plus one is for the eventfd shutdown signal.
#define NUM_POLL_FDS (NUM_ANT_CHANNELS + 1)
#define EVENTFD_IDX NUM_ANT_CHANNELS

static ANT_U8 KEEPALIVE_MESG[] = {0x01, 0x00, 0x00};
static ANT_U8 KEEPALIVE_RESP[] = {0x03, 0x40, 0x00, 0x00, 0x28};

void doReset(ant_rx_thread_info_t *stRxThreadInfo);
int readChannelMsg(ant_channel_type eChannel, ant_channel_info_t *pstChnlInfo);

/*
 * Function to check that all given flags are set in a particular value.
 * Designed for use with the revents field of pollfds filled out by poll().
 *
 * Parameters:
 *    - value: The value that will be checked to contain all flags.
 *    - flags: Bitwise-or of the flags that value should be checked for.
 *
 * Returns:
 *    - true IFF all the bits that are set in 'flags' are also set in 'value'
 */
ANT_BOOL areAllFlagsSet(short value, short flags)
{
   value &= flags;
   return (value == flags);
}

/*
 * This thread is run occasionally as a detached thread in order to send a keepalive message to the
 * chip.
 */
void *fnKeepAliveThread(void *unused)
{
   ANT_DEBUG_V("Sending dummy keepalive message.");
   ant_tx_message(sizeof(KEEPALIVE_MESG)/sizeof(ANT_U8), KEEPALIVE_MESG);
   return NULL;
}

/*
 * This thread waits for ANT messages from a VFS file.
 */
void *fnRxThread(void *ant_rx_thread_info)
{
   int iMutexLockResult;
   int iPollRet;
   ant_rx_thread_info_t *stRxThreadInfo;
   struct pollfd astPollFd[NUM_POLL_FDS];
   ant_channel_type eChannel;
   ANT_FUNC_START();

   stRxThreadInfo = (ant_rx_thread_info_t *)ant_rx_thread_info;
   for (eChannel = 0; eChannel < NUM_ANT_CHANNELS; eChannel++) {
      astPollFd[eChannel].fd = stRxThreadInfo->astChannels[eChannel].iFd;
      astPollFd[eChannel].events = EVENTS_TO_LISTEN_FOR;
   }
   // Fill out poll request for the shutdown signaller.
   astPollFd[EVENTFD_IDX].fd = stRxThreadInfo->iRxShutdownEventFd;
   astPollFd[EVENTFD_IDX].events = POLL_IN;

   // Reset the waiting for response, since we don't want a stale value if we were reset.
   stRxThreadInfo->bWaitingForKeepaliveResponse = ANT_FALSE;

   /* continue running as long as not terminated */
   while (stRxThreadInfo->ucRunThread) {
      /* Wait for data available on any file (transport path), shorter wait if we just timed out. */
      int timeout = stRxThreadInfo->bWaitingForKeepaliveResponse ? KEEPALIVE_TIMEOUT : ANT_POLL_TIMEOUT;
      iPollRet = poll(astPollFd, NUM_POLL_FDS, timeout);
      if (!iPollRet) {
         if(!stRxThreadInfo->bWaitingForKeepaliveResponse)
         {
            stRxThreadInfo->bWaitingForKeepaliveResponse = ANT_TRUE;
            // Keep alive is done on a separate thread so that rxThread can handle flow control during
            // the message.
            pthread_t thread;
            // Don't care if it failed as the consequence is just a missed keep-alive.
            pthread_create(&thread, NULL, fnKeepAliveThread, NULL);
            // Detach the thread so that we don't need to join it later.
            pthread_detach(thread);
            ANT_DEBUG_V("poll timed out, checking exit cond");
         } else
         {
            ANT_DEBUG_E("No response to keepalive, attempting recovery.");
            doReset(stRxThreadInfo);
            goto out;
         }
      } else if (iPollRet < 0) {
         ANT_ERROR("unhandled error: %s, attempting recovery.", strerror(errno));
         doReset(stRxThreadInfo);
         goto out;
      } else {
         for (eChannel = 0; eChannel < NUM_ANT_CHANNELS; eChannel++) {
            if (areAllFlagsSet(astPollFd[eChannel].revents, EVENT_HARD_RESET)) {
               ANT_ERROR("Hard reset indicated by %s. Attempting recovery.",
                            stRxThreadInfo->astChannels[eChannel].pcDevicePath);
               doReset(stRxThreadInfo);
               goto out;
            } else if (areAllFlagsSet(astPollFd[eChannel].revents, EVENT_CHIP_SHUTDOWN)) {
               /* chip reported it was unexpectedly disabled */
               ANT_DEBUG_D(
                     "poll hang-up from %s. Attempting recovery.",
                     stRxThreadInfo->astChannels[eChannel].pcDevicePath);

               doReset(stRxThreadInfo);
               goto out;
            } else if (areAllFlagsSet(astPollFd[eChannel].revents, EVENT_DATA_AVAILABLE)) {
               ANT_DEBUG_D("data on %s. reading it",
                            stRxThreadInfo->astChannels[eChannel].pcDevicePath);

               // Doesn't matter what data we received, we know the chip is alive.
               stRxThreadInfo->bWaitingForKeepaliveResponse = ANT_FALSE;

               if (readChannelMsg(eChannel, &stRxThreadInfo->astChannels[eChannel]) < 0) {
                  ANT_ERROR("Read of data failed. Attempting recovery.");
                  doReset(stRxThreadInfo);
                  goto out;
               }
            } else if (areAllFlagsSet(astPollFd[eChannel].revents, POLLNVAL)) {
               ANT_ERROR("poll was called on invalid file descriptor %s. Attempting recovery.",
                     stRxThreadInfo->astChannels[eChannel].pcDevicePath);
               doReset(stRxThreadInfo);
               goto out;
            } else if (areAllFlagsSet(astPollFd[eChannel].revents, POLLERR)) {
               ANT_ERROR("Unknown error from %s. Attempting recovery.",
                     stRxThreadInfo->astChannels[eChannel].pcDevicePath);
               doReset(stRxThreadInfo);
               goto out;
            } else if (astPollFd[eChannel].revents) {
               ANT_DEBUG_W("unhandled poll result %#x from %s",
                            astPollFd[eChannel].revents,
                            stRxThreadInfo->astChannels[eChannel].pcDevicePath);
            }
         }
         // Now check for shutdown signal
         if(areAllFlagsSet(astPollFd[EVENTFD_IDX].revents, POLLIN))
         {
            ANT_DEBUG_I("rx thread caught shutdown signal.");
            // reset the counter by reading.
            uint64_t counter;
            read(stRxThreadInfo->iRxShutdownEventFd, &counter, sizeof(counter));
            // don't care if read error, going to close the thread anyways.
            stRxThreadInfo->ucRunThread = 0;
         } else if (astPollFd[EVENTFD_IDX].revents != 0) {
            ANT_ERROR("Shutdown event descriptor had unexpected event: %#x. exiting rx thread.",
                  astPollFd[EVENTFD_IDX].revents);
            stRxThreadInfo->ucRunThread = 0;
         }
      }
   }

   /* disable ANT radio if not already disabling */
   // Try to get stEnabledStatusLock.
   // if you get it then no one is enabling or disabling
   // if you can't get it assume something made you exit
   ANT_DEBUG_V("try getting stEnabledStatusLock in %s", __FUNCTION__);
   iMutexLockResult = pthread_mutex_trylock(stRxThreadInfo->pstEnabledStatusLock);
   if (!iMutexLockResult) {
      ANT_DEBUG_V("got stEnabledStatusLock in %s", __FUNCTION__);
      ANT_WARN("rx thread has unexpectedly crashed, cleaning up");

      // spoof our handle as closed so we don't try to join ourselves in disable
      stRxThreadInfo->stRxThread = 0;

      if (g_fnStateCallback) {
         g_fnStateCallback(RADIO_STATUS_DISABLING);
      }

      ant_disable();

      if (g_fnStateCallback) {
         g_fnStateCallback(ant_radio_enabled_status());
      }

      ANT_DEBUG_V("releasing stEnabledStatusLock in %s", __FUNCTION__);
      pthread_mutex_unlock(stRxThreadInfo->pstEnabledStatusLock);
      ANT_DEBUG_V("released stEnabledStatusLock in %s", __FUNCTION__);
   } else if (iMutexLockResult != EBUSY) {
      ANT_ERROR("rx thread closing code, trylock on state lock failed: %s",
            strerror(iMutexLockResult));
   } else {
      ANT_DEBUG_V("stEnabledStatusLock busy");
   }

   out:
   ANT_FUNC_END();
#ifdef ANDROID
   return NULL;
#endif
}

void doReset(ant_rx_thread_info_t *stRxThreadInfo)
{
   int iMutexLockResult;

   ANT_FUNC_START();
   /* Chip was reset or other error, only way to recover is to
    * close and open ANT chardev */
   stRxThreadInfo->ucChipResetting = 1;

   if (g_fnStateCallback) {
      g_fnStateCallback(RADIO_STATUS_RESETTING);
   }

   stRxThreadInfo->ucRunThread = 0;

   ANT_DEBUG_V("getting stEnabledStatusLock in %s", __FUNCTION__);
   iMutexLockResult = pthread_mutex_lock(stRxThreadInfo->pstEnabledStatusLock);
   if (iMutexLockResult < 0) {
      ANT_ERROR("chip was reset, getting state mutex failed: %s",
            strerror(iMutexLockResult));
      stRxThreadInfo->stRxThread = 0;
      stRxThreadInfo->ucChipResetting = 0;
   } else {
      ANT_DEBUG_V("got stEnabledStatusLock in %s", __FUNCTION__);

      stRxThreadInfo->stRxThread = 0; /* spoof our handle as closed so we don't
                                       * try to join ourselves in disable */

      ant_disable();

      int enableResult = ant_enable();

      stRxThreadInfo->ucChipResetting = 0;
      if (enableResult) { /* failed */
         if (g_fnStateCallback) {
            g_fnStateCallback(RADIO_STATUS_DISABLED);
         }
      } else { /* success */
         if (g_fnStateCallback) {
            g_fnStateCallback(RADIO_STATUS_RESET);
         }
      }

      ANT_DEBUG_V("releasing stEnabledStatusLock in %s", __FUNCTION__);
      pthread_mutex_unlock(stRxThreadInfo->pstEnabledStatusLock);
      ANT_DEBUG_V("released stEnabledStatusLock in %s", __FUNCTION__);
   }

   ANT_FUNC_END();
}

////////////////////////////////////////////////////////////////////
//  setFlowControl
//
//  Sets the flow control "flag" to the value provided and signals the transmit
//  thread to check the value.
//
//  Parameters:
//      pstChnlInfo   the details of the channel being updated
//      ucFlowSetting the value to use
//
//  Returns:
//      Success:
//          0
//      Failure:
//          -1
////////////////////////////////////////////////////////////////////
int setFlowControl(ant_channel_info_t *pstChnlInfo, ANT_U8 ucFlowSetting)
{
   int iRet = -1;
   int iMutexResult;
   ANT_FUNC_START();

   ANT_DEBUG_V("getting stFlowControlLock in %s", __FUNCTION__);
   iMutexResult = pthread_mutex_lock(pstChnlInfo->pstFlowControlLock);
   if (iMutexResult) {
      ANT_ERROR("failed to lock flow control mutex during response: %s", strerror(iMutexResult));
   } else {
      ANT_DEBUG_V("got stFlowControlLock in %s", __FUNCTION__);

      pstChnlInfo->ucFlowControlResp = ucFlowSetting;

      ANT_DEBUG_V("releasing stFlowControlLock in %s", __FUNCTION__);
      pthread_mutex_unlock(pstChnlInfo->pstFlowControlLock);
      ANT_DEBUG_V("released stFlowControlLock in %s", __FUNCTION__);

      pthread_cond_signal(pstChnlInfo->pstFlowControlCond);

      iRet = 0;
   }

   ANT_FUNC_END();
   return iRet;
}

int readChannelMsg(ant_channel_type eChannel, ant_channel_info_t *pstChnlInfo)
{
   int iRet = -1;
   int iRxLenRead;
   int iCurrentHciPacketOffset;
   int iHciDataSize;
   ANT_FUNC_START();

   // Keep trying to read while there is an error, and that error is EAGAIN
   while (((iRxLenRead = read(pstChnlInfo->iFd, &aucRxBuffer[eChannel][iRxBufferLength[eChannel]], (sizeof(aucRxBuffer[eChannel]) - iRxBufferLength[eChannel]))) < 0)
                   && errno == EAGAIN)
      ;

   if (iRxLenRead < 0) {
      if (errno == ENODEV) {
         ANT_ERROR("%s not enabled",
               pstChnlInfo->pcDevicePath);

         goto out;
      } else if (errno == ENXIO) {
         ANT_ERROR("%s there is no physical ANT device connected",
               pstChnlInfo->pcDevicePath);

         goto out;
      } else {
         ANT_ERROR("%s: unhandled error: %s",
               pstChnlInfo->pcDevicePath, strerror(errno));

         goto out;
      }
   } else {
      ANT_SERIAL(aucRxBuffer[eChannel], iRxLenRead, 'R');

      iRxLenRead += iRxBufferLength[eChannel];   // add existing data on
      
      // if we didn't get a full packet, then just exit
      if (iRxLenRead < (aucRxBuffer[eChannel][ANT_HCI_SIZE_OFFSET] + ANT_HCI_HEADER_SIZE + ANT_HCI_FOOTER_SIZE)) {
         iRxBufferLength[eChannel] = iRxLenRead;
         iRet = 0;
         goto out;
      }

      iRxBufferLength[eChannel] = 0;    // reset buffer length here since we should have a full packet
      
#if ANT_HCI_OPCODE_SIZE == 1  // Check the different message types by opcode
      ANT_U8 opcode = aucRxBuffer[eChannel][ANT_HCI_OPCODE_OFFSET];

      if(ANT_HCI_OPCODE_COMMAND_COMPLETE == opcode) {
         // Command Complete, so signal a FLOW_GO
         if(setFlowControl(pstChnlInfo, ANT_FLOW_GO)) {
            goto out;
         }
      } else if(ANT_HCI_OPCODE_FLOW_ON == opcode) {
         // FLow On, so resend the last Tx
#ifdef ANT_FLOW_RESEND
         // Check if there is a message to resend
         if(pstChnlInfo->ucResendMessageLength > 0) {
            ant_tx_message_flowcontrol_none(eChannel, pstChnlInfo->ucResendMessageLength, pstChnlInfo->pucResendMessage);
         } else {
            ANT_DEBUG_D("Resend requested by chip, but tx request cancelled");
         }
#endif // ANT_FLOW_RESEND
      } else if(ANT_HCI_OPCODE_ANT_EVENT == opcode)
         // ANT Event, send ANT packet to Rx Callback
#endif // ANT_HCI_OPCODE_SIZE == 1
      {
      // Received an ANT packet
         iCurrentHciPacketOffset = 0;

         while(iCurrentHciPacketOffset < iRxLenRead) {

            // TODO Allow HCI Packet Size value to be larger than 1 byte
            // This currently works as no size value is greater than 255, and little endian
            iHciDataSize = aucRxBuffer[eChannel][iCurrentHciPacketOffset + ANT_HCI_SIZE_OFFSET];

            if ((iHciDataSize + ANT_HCI_HEADER_SIZE + ANT_HCI_FOOTER_SIZE + iCurrentHciPacketOffset) > 
                  iRxLenRead) {
               // we don't have a whole packet
               iRxBufferLength[eChannel] = iRxLenRead - iCurrentHciPacketOffset;
               memcpy(aucRxBuffer[eChannel], &aucRxBuffer[eChannel][iCurrentHciPacketOffset], iRxBufferLength[eChannel]);
               // the increment at the end should push us out of the while loop
            } else
#ifdef ANT_MESG_FLOW_CONTROL
            if (aucRxBuffer[eChannel][iCurrentHciPacketOffset + ANT_HCI_DATA_OFFSET + ANT_MSG_ID_OFFSET] == 
                  ANT_MESG_FLOW_CONTROL) {
               // This is a flow control packet, not a standard ANT message
               if(setFlowControl(pstChnlInfo, \
                     aucRxBuffer[eChannel][iCurrentHciPacketOffset + ANT_HCI_DATA_OFFSET + ANT_MSG_DATA_OFFSET])) {
                  goto out;
               }
            } else
#endif // ANT_MESG_FLOW_CONTROL
            {
               ANT_U8 *msg = aucRxBuffer[eChannel] + iCurrentHciPacketOffset + ANT_HCI_DATA_OFFSET;
               ANT_BOOL bIsKeepAliveResponse = memcmp(msg, KEEPALIVE_RESP, sizeof(KEEPALIVE_RESP)/sizeof(ANT_U8)) == 0;
               if (bIsKeepAliveResponse) {
                  ANT_DEBUG_V("Filtered out keepalive response.");
               } else if (pstChnlInfo->fnRxCallback != NULL) {

                  // Loop through read data until all HCI packets are written to callback
                     pstChnlInfo->fnRxCallback(iHciDataSize, \
                           msg);
               } else {
                  ANT_WARN("%s rx callback is null", pstChnlInfo->pcDevicePath);
               }
            }
            
            iCurrentHciPacketOffset = iCurrentHciPacketOffset + ANT_HCI_HEADER_SIZE + ANT_HCI_FOOTER_SIZE + iHciDataSize;               
         }         
      }

      iRet = 0;
   }

out:
   ANT_FUNC_END();
   return iRet;
}
