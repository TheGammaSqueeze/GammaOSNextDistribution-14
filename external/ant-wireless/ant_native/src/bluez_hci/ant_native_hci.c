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
*   FILE NAME:      ant_native_hci.c
*
*   BRIEF:
*      This file provides the BlueZ HCI implementation of ant_native.h
*
*
\******************************************************************************/

#include <pthread.h>
#include <string.h>
#include <unistd.h>

#include "ant_types.h"
#include "ant_native.h"
#include "ant_utils.h"
#include "ant_version.h"

#if USE_EXTERNAL_POWER_LIBRARY
#include "antradio_power.h"
#endif

#include "ant_rx.h"
#include "ant_tx.h"
#include "ant_hciutils.h"
#include "ant_log.h"

static pthread_mutex_t         txLock;
pthread_mutex_t                enableLock;

ANTRadioEnabledStatus radio_status = RADIO_STATUS_DISABLED;
ANTRadioEnabledStatus get_and_set_radio_status(void);

////////////////////////////////////////////////////////////////////
//  ant_init
//
//  Initialises the native environment.
//
//  Parameters:
//      -
//
//  Returns:
//      Success:
//          ANT_STATUS_SUCCESS
//      Failures:
//          ANT_STATUS_FAILED if could not initialize mutex
//
//  Psuedocode:
/*
LOCK antdevice_LOCK
    CREATE mutex for locking Tx attempt
    IF could not create mutex
        RESULT = ANT_STATUS_FAILED
    ELSE
        CREATE mutex for locking enable/disable
        IF could not create mutex
            RESULT = ANT_STATUS_FAILED
        ELSE
            RESULT = ANT_STATUS_SUCCESS
        ENDIF
    ENDIF
UNLOCK
*/
////////////////////////////////////////////////////////////////////
ANTStatus ant_init(void)
{
   int mutexResult;
   ANTStatus status = ANT_STATUS_FAILED;
   ANT_FUNC_START();

   mutexResult = pthread_mutex_init(&txLock, NULL); //use default attr
   if (mutexResult)
   {
      ANT_ERROR("Tx Lock mutex initialization failed: %s", strerror(mutexResult));
   }
   else
   {
      mutexResult = pthread_mutex_init(&enableLock, NULL);
      if (mutexResult)
      {
         ANT_ERROR("Enable Lock mutex init failed %s", strerror(mutexResult));
      }
      else
      {
         status = ANT_STATUS_SUCCESS;
      }
   }

   ANT_FUNC_END();
   return status;
}


////////////////////////////////////////////////////////////////////
//  ant_deinit
//
//  De-initialises the native environment.
//
//  Parameters:
//      -
//
//  Returns:
//      Success:
//          ANT_STATUS_SUCCESS
//      Failures:
//          ANT_STATUS_FAILED if could not de-initialize mutex
//
//  Psuedocode:
/*
LOCK antdevice_LOCK (also Init and Tx)
    DESTROY mutex for locking Tx attempt
    IF could not destroy mutex
        RESULT = ANT_STATUS_FAILED
    ELSE
        DESTROY mutex for locking enable/disable
        IF coult not destroy mutex
            RESULT = ANT_STATUS_FAILED
        ELSE
            RESULT = ANT_STATUS_SUCCESS
        ENDIF
    ENDIF
UNLOCK
*/
////////////////////////////////////////////////////////////////////
ANTStatus ant_deinit(void)
{
   int mutexResult;
   ANTStatus result_status = ANT_STATUS_FAILED;
   ANT_FUNC_START();

   mutexResult = pthread_mutex_destroy(&txLock);
   if (mutexResult)
   {
      ANT_ERROR("Tx Lock mutex destroy failed: %s", strerror(mutexResult));
   }
   else
   {
      mutexResult = pthread_mutex_destroy(&enableLock);
      if (mutexResult)
      {
         ANT_ERROR("Enable Lock mutex destroy failed: %s", strerror(mutexResult));
      }
      else
      {
         result_status = ANT_STATUS_SUCCESS;
      }
   }

   ANT_FUNC_END();
   return result_status;
}


////////////////////////////////////////////////////////////////////
//  ant_enable_radio
//
//  Powers on the ANT part and initialises the transport to the chip.
//  Changes occur in part implementing ant_enable() call
//      On Android this is in the Bluedroid module.
//      On Linux this is device specific
//
//  Parameters:
//      -
//
//  Returns:
//      Success:
//          ANT_STATUS_SUCCESS
//      Failures:
//          ANT_STATUS_TRANSPORT_INIT_ERR if could not enable
//          ANT_STATUS_FAILED if failed to get mutex or init rx thread
//
//  Psuedocode:
/*
LOCK enable_LOCK
    LOCK tx_LOCK
        IF state is not enabled
            STATE = ENABLING
        ENDIF
        ant enable
        IF ant_enable success
            IF rx thread is running
                STATE = ENABLED
                RESULT = SUCCESS
            ELSE
                start rx thread
                IF starting rx thread fails
                    ant disable
                    get state
                    IF state is enabled
                        log a serious error
                    ENDIF
                    RESULT = FAILED
                ELSE
                    STATE = ENABLED
                    RESULT = SUCCESS
                ENDIF
            ENDIF
        ELSE
            get state
            IF state is enabled
                log a serious error
            ENDIF
            RESULT = FAILURE
        ENDIF
    UNLOCK
UNLOCK
*/
////////////////////////////////////////////////////////////////////
ANTStatus ant_enable_radio(void)
{
   int result;
   int lockResult;
   ANTStatus result_status = ANT_STATUS_FAILED;
   ANT_FUNC_START();

   ANT_DEBUG_V("getting enableLock in %s", __FUNCTION__);
   lockResult = pthread_mutex_lock(&enableLock);
   if(lockResult)
   {
      ANT_ERROR("Enable failed to get enableLock mutex: %s", strerror(lockResult));
      return ANT_STATUS_FAILED;
   }
   ANT_DEBUG_V("got enableLock in %s", __FUNCTION__);

   if(RADIO_STATUS_DISABLED == radio_status)
   {
      radio_status = RADIO_STATUS_ENABLING;
   }

   ANT_DEBUG_V("getting txLock in %s", __FUNCTION__);
   lockResult = pthread_mutex_lock(&txLock);
   if (lockResult)
   {
      ANT_ERROR("Enable txLock failed: %s", strerror(lockResult));
      pthread_mutex_unlock(&enableLock);
      return ANT_STATUS_FAILED;
   }
   ANT_DEBUG_V("got txLock in %s", __FUNCTION__);

   if (get_and_set_radio_status() != RADIO_STATUS_ENABLED)
   {
      if (RxParams.thread)
      {
         ANT_WARN("in enable call: rx thread still exists but radio crashed. trying to recover");
         ANT_DEBUG_V("radio crashed, letting rx thread join");
         pthread_join(RxParams.thread, NULL);
         RxParams.thread = 0;
         ANT_DEBUG_V("recovered. joined by rx thread");
      }

      ANT_DEBUG_I("radio_status (%d -> %d)", radio_status, RADIO_STATUS_ENABLING);
      radio_status = RADIO_STATUS_ENABLING;

#if USE_EXTERNAL_POWER_LIBRARY
      if (RxParams.pfStateCallback)
         RxParams.pfStateCallback(radio_status);
#endif
   }

#if USE_EXTERNAL_POWER_LIBRARY
   result = ant_enable();

   ANT_DEBUG_D("ant_enable() result is %d", result);
#else
   result = 0;
#endif
   if (result == 0)
   {
      if (RxParams.thread)
      {
         result_status = ANT_STATUS_SUCCESS;
         radio_status = RADIO_STATUS_ENABLED; // sanity assign, cant be enabling
         ANT_DEBUG_D("ANT radio re-enabled");
      }
      else
      {
         result = pthread_create(&RxParams.thread, NULL, ANTHCIRxThread, NULL);
         if (result)
         {
            ANT_ERROR("Thread initialization failed: %s", strerror(result));
            result_status = ANT_STATUS_FAILED;
         }
         else
         {
            result_status = ANT_STATUS_SUCCESS;
#if USE_EXTERNAL_POWER_LIBRARY
            if (radio_status == RADIO_STATUS_ENABLING)
            {
               ANT_DEBUG_I("radio_status (%d -> %d)", radio_status, RADIO_STATUS_ENABLED);
               radio_status = RADIO_STATUS_ENABLED;

               if (RxParams.pfStateCallback)
                  RxParams.pfStateCallback(radio_status);
            }
            else
            {
               ANT_WARN("radio was already enabled but rx thread was not running");
            }
#else
            radio_status = RADIO_STATUS_ENABLED;
#endif
         }
      }
   }
   else
   {
      result_status = ANT_STATUS_TRANSPORT_INIT_ERR;
   }

   if (result_status != ANT_STATUS_SUCCESS) // ant_enable() or rx thread creating failed
   {
#if USE_EXTERNAL_POWER_LIBRARY
      ant_disable();
#endif

      switch (get_and_set_radio_status())
      {
         case RADIO_STATUS_ENABLED:
            ANT_ERROR("SERIOUS: enable failed, but ANT is enabled without a rx thread");
            break;
         default:
            ANT_DEBUG_D("Enable failed, after cleanup chip is not enabled");
            break;
      }
   }

   ANT_DEBUG_V("releasing txLock in %s", __FUNCTION__);
   pthread_mutex_unlock(&txLock);
   ANT_DEBUG_V("released txLock in %s", __FUNCTION__);

   ANT_DEBUG_V("releasing enableLock in %s", __FUNCTION__);
   pthread_mutex_unlock(&enableLock);
   ANT_DEBUG_V("released enableLock in %s", __FUNCTION__);

   ANT_FUNC_END();
   return result_status;
}

////////////////////////////////////////////////////////////////////
//  ant_radio_hard_reset
//
//  Does nothing as Hard Reset is not supported.
//
//  Parameters:
//      -
//
//  Returns:
//      ANT_NOT_SUPPORTED
//
//  Psuedocode:
/*
RESULT = NOT SUPPORTED
*/
////////////////////////////////////////////////////////////////////
ANTStatus ant_radio_hard_reset(void)
{
   ANTStatus result_status = ANT_STATUS_NOT_SUPPORTED;
   ANT_FUNC_START();
   ANT_FUNC_END();
   return result_status;
}

////////////////////////////////////////////////////////////////////
//  ant_disable_radio
//
//  Disables the HCI transport and powers off the ANT part.
//
//  Parameters:
//      -
//
//  Returns:
//      Success:
//          ANT_STATUS_SUCCESS
//      Failures:
//          ANT_STATUS_FAILED if could not get mutex
//          ANT_STATUS_NOT_DE_INITIALIZED if ant_disable failed
//
//  Psuedocode:
/*
LOCK enable_LOCK
    LOCK tx_LOCK
        IF not disabled
            STATE = DISABLING
        ENDIF
        ant disable
        IF rx thread is running
            wait for rx thread to terminate
        ENDIF
        get radio status
        IF radio is disabled
            RESULT = SUCCESS
        ELSE IF radio is enabled
            log a serious error
            RESULT = FAILURE
        ELSE
            RESULT = FAILURE
        ENDIF
    UNLOCK
UNLOCK
*/
////////////////////////////////////////////////////////////////////
ANTStatus ant_disable_radio(void)
{
   int result;
   int lockResult;
   ANTStatus ret = ANT_STATUS_FAILED;
   ANT_FUNC_START();

   ANT_DEBUG_V("getting enableLock in %s", __FUNCTION__);
   lockResult = pthread_mutex_lock(&enableLock);
   if(lockResult)
   {
      ANT_ERROR("Disable failed to get enableLock mutex: %s", strerror(lockResult));
      return ANT_STATUS_FAILED;
   }
   ANT_DEBUG_V("got enableLock in %s", __FUNCTION__);

   ANT_DEBUG_V("getting txLock in %s", __FUNCTION__);
   lockResult = pthread_mutex_lock(&txLock);
   if (lockResult)
   {
      ANT_ERROR("Disable txLock failed: %s", strerror(lockResult));
      pthread_mutex_unlock(&enableLock);
      return ANT_STATUS_FAILED;
   }
   ANT_DEBUG_V("got txLock in %s", __FUNCTION__);

#if USE_EXTERNAL_POWER_LIBRARY
   if (get_and_set_radio_status() != RADIO_STATUS_DISABLED)
   {
      ANT_DEBUG_I("radio_status (%d -> %d)", radio_status, RADIO_STATUS_DISABLING);
      radio_status = RADIO_STATUS_DISABLING;

      if (RxParams.pfStateCallback)
         RxParams.pfStateCallback(radio_status);
   }

   result = ant_disable();

   ANT_DEBUG_D("ant_disable() result is %d", result);
#else
   radio_status = RADIO_STATUS_DISABLED;
#endif

   // If rx thread exists ( != 0)
   if (RxParams.thread)
   {
      ANT_DEBUG_V("quit rx thread, joining");
      pthread_join(RxParams.thread, NULL);
      RxParams.thread = 0;
      ANT_DEBUG_V("joined by rx thread");
   }
   else
   {
      ANT_DEBUG_W("rx thread is 0 (not created?)");
   }

   switch (get_and_set_radio_status())
   {
      case RADIO_STATUS_DISABLED:
         ret = ANT_STATUS_SUCCESS;
         break;
      case RADIO_STATUS_ENABLED:
         ANT_ERROR("SERIOUS: ANT was disabled, rx thread quit, but ANT is enabled");
         ret = ANT_STATUS_FAILED;
         break;
      default:
         ret = ANT_STATUS_NOT_DE_INITIALIZED;
         break;
   }

   ANT_DEBUG_V("releasing txLock in %s", __FUNCTION__);
   pthread_mutex_unlock(&txLock);
   ANT_DEBUG_V("released txLock in %s", __FUNCTION__);

   ANT_DEBUG_V("releasing enableLock in %s", __FUNCTION__);
   pthread_mutex_unlock(&enableLock);
   ANT_DEBUG_V("released enableLock in %s", __FUNCTION__);

   ANT_FUNC_END();
   return ret;
}


////////////////////////////////////////////////////////////////////
//  ant_radio_enabled_status
//
//  Returns if the chip/transport is disabled/disabling/enabling/enabled. This
//  function will NOT overwrite internal state variable enabling or disabling.
//  It will call get_and_set_radio_status() if not enabling/disabling which can
//  change internal state on errors.
//
//  Parameters:
//      -
//
//  Returns:
//      The current radio status (ANTRadioEnabledStatus)
//
//  Psuedocode:
/*
IF Enabling
    RESULT = Enabling
ELSE IF Disabling
    RESULT = Disabling
ELSE
    IF ant_is_enabled
        Enabled
        RESULT = Enabled
    ELSE
        Disabled
        RESULT = Disabled
    ENDIF
ENDIF
*/
////////////////////////////////////////////////////////////////////
ANTRadioEnabledStatus ant_radio_enabled_status(void)
{
   ANT_FUNC_START();

   if ((RADIO_STATUS_ENABLING != radio_status) &&
         (RADIO_STATUS_DISABLING != radio_status))
   {
      get_and_set_radio_status();
   }

   ANT_FUNC_END();
   return radio_status;
}

////////////////////////////////////////////////////////////////////
//  get_and_set_radio_status
//
//  Returns if the chip/transport is disabled/enabled/unknown This function WILL
//  overwrite what it thinks the state is with what the BlueZ core (kernel)
//  thinks it is. It will overwrite enabling or disabling states, and might
//  change internal state from enabled, disabled, or unknown to any of these
//  three on errors.
//
//  Parameters:
//      -
//
//  Returns:
//      The current radio status (ANTRadioEnabledStatus)
//
////////////////////////////////////////////////////////////////////
ANTRadioEnabledStatus get_and_set_radio_status(void)
{
   ANT_FUNC_START();
#if USE_EXTERNAL_POWER_LIBRARY
   ANTRadioEnabledStatus orig_status = radio_status;
   switch (ant_is_enabled())
   {
      case 0:
         radio_status = RADIO_STATUS_DISABLED;
         break;
      case 1:
         radio_status = RADIO_STATUS_ENABLED;
         break;
      default:
         ANT_ERROR("getting chip state returned an error");
         radio_status = RADIO_STATUS_UNKNOWN;
         break;
   }
   if (orig_status != radio_status)
   {
      ANT_DEBUG_I("radio_status (%d -> %d)", orig_status, radio_status);

      if (RxParams.pfStateCallback)
         RxParams.pfStateCallback(radio_status);
   }
#endif
   ANT_FUNC_END();
   return radio_status;
}

////////////////////////////////////////////////////////////////////
//  set_ant_rx_callback
//
//  Sets which function to call when an ANT message is received.
//
//  Parameters:
//      rx_callback_func   the ANTNativeANTEventCb function to be used
//                         for received messages.
//
//  Returns:
//          ANT_STATUS_SUCCESS
//
//  Psuedocode:
/*
    Rx Callback = rx_callback_func
*/
////////////////////////////////////////////////////////////////////
ANTStatus set_ant_rx_callback(ANTNativeANTEventCb rx_callback_func)
{
   ANTStatus status = ANT_STATUS_SUCCESS;
   ANT_FUNC_START();

   RxParams.pfRxCallback = rx_callback_func;

   ANT_FUNC_END();
   return status;
}

////////////////////////////////////////////////////////////////////
//  set_ant_state_callback
//
//  Sets which function to call when an ANT state change occurs.
//
//  Parameters:
//      state_callback_func   the ANTNativeANTStateCb function to be used
//                            for received state changes.
//
//  Returns:
//          ANT_STATUS_SUCCESS
//
//  Psuedocode:
/*
    State Callback = state_callback_func
*/
////////////////////////////////////////////////////////////////////
ANTStatus set_ant_state_callback(ANTNativeANTStateCb state_callback_func)
{
   ANTStatus status = ANT_STATUS_SUCCESS;
   ANT_FUNC_START();

   RxParams.pfStateCallback = state_callback_func;

   ANT_FUNC_END();

   return status;
}

////////////////////////////////////////////////////////////////////
//  ant_tx_message
//
//  Sends an ANT message to the chip
//
//  Parameters:
//      ucLen   the length of the message
//      pucMesg pointer to the message data
//
//  Returns:
//      Success:
//          ANT_STATUS_SUCCESS
//      Failure:
//          ANT_STATUS_NOT_ENABLED
//
//  Psuedocode:
/*
IF not enabled
    RESULT = NOT ENABLED
ELSE
    Lock
    IF Lock failed
        RESULT = FAILED
    ELSE
        STORE length (little endian) in send buffer
        STORE data in send buffer
    ENDIF
ENDIF
*/
////////////////////////////////////////////////////////////////////
ANTStatus ant_tx_message(ANT_U8 ucLen, ANT_U8 *pucMesg)
{
   ANTStatus   status;

   /* Socket for sending HCI commands */
   int tx_socket = -1;

   int lockResult;

   ANT_FUNC_START();

   ANT_DEBUG_V("getting txLock in %s", __FUNCTION__);
   lockResult = pthread_mutex_lock(&txLock);
   if (lockResult)
   {
      ANT_ERROR("ant_tx_message, could not get txLock: %s", strerror(lockResult));
      return ANT_STATUS_FAILED;
   }

   ANT_DEBUG_V("got txLock in %s", __FUNCTION__);

   if(RADIO_STATUS_ENABLED != get_and_set_radio_status())
   {
      ANT_DEBUG_E("ant_tx_message, ANT not enabled - ABORTING. Radio status = %d",
                                                                  radio_status);
      ANT_DEBUG_V("releasing txLock in %s", __FUNCTION__);
      pthread_mutex_unlock(&txLock);
      ANT_DEBUG_V("released txLock in %s", __FUNCTION__);
      return ANT_STATUS_FAILED_BT_NOT_INITIALIZED;
   }

   // Open socket
   tx_socket = ant_open_tx_transport();

   if(tx_socket < 0)
   {
      ANT_ERROR("Could not open Tx socket");
      ANT_DEBUG_V("releasing txLock in %s", __FUNCTION__);
      pthread_mutex_unlock(&txLock);
      ANT_DEBUG_V("released txLock in %s", __FUNCTION__);
      return ANT_STATUS_FAILED;
   }

   // Send HCI packet
   ANT_BOOL retryRx;
   ANT_BOOL retryTx;
   status = ANT_STATUS_FAILED;

   int MAX_RETRIES_WRITE_FAIL = 10;
   int MAX_RETRY_TIME_SECS = 10;
   int commandWriteFailRetries = 0;

   // Start New timed retry code:
   time_t startTime = time(NULL);
   time_t endTime = 0;
   if((time_t)-1 != startTime)
   {
      endTime = startTime + MAX_RETRY_TIME_SECS;
   }
   else
   {
      ANT_ERROR("failed to get current time");
   }

   do // while (retryTx)
   {
      retryTx = ANT_FALSE;

      if(ANT_STATUS_SUCCESS == write_data(pucMesg, ucLen))
      {
         do // while (retryRx)
         {
            retryRx = ANT_FALSE;
         
            if(ANT_TRUE == wait_for_message(tx_socket))
            {
               ANT_DEBUG_D("New HCI data available, reading...");
            
               status = get_command_complete_result(tx_socket);
               switch (status)
               {
                  case ANT_STATUS_NO_VALUE_AVAILABLE:
                  {
                     ANT_WARN("Did not get an expected response for write, trying again");
                     retryRx = ANT_TRUE;
                     break;
                  }
                  case ANT_STATUS_FAILED:
                  {
                     ANT_ERROR("Command Complete: ANT_STATUS_FAILED");
                     break;
                  }
                  case ANT_STATUS_COMMAND_WRITE_FAILED:
                  {
                     ANT_ERROR("Command Complete: ANT_STATUS_WRITE_FAILED");
                   
                     if(++commandWriteFailRetries < MAX_RETRIES_WRITE_FAIL)
                     {
                        ANT_DEBUG_D("Retrying.  Retry count = %d", 
                                                      commandWriteFailRetries);
                     
                        retryTx = ANT_TRUE;
                     }
                     else
                     {
                        ANT_ERROR("Aborting.  Retry count = %d.  Max retries = %d",
                              commandWriteFailRetries, MAX_RETRIES_WRITE_FAIL);
                     }
                     break;
                  }
                  case ANT_STATUS_TRANSPORT_UNSPECIFIED_ERROR:
                  {
                     ANT_DEBUG_D("Command Complete: ANT_STATUS_UNSPECIFIED_ERROR");

                     // Give the chip a break before we try to resend data.
                     nanosleep((struct timespec[]){{0, 50000000}}, NULL);
                   
                     time_t currentTime = time(NULL);
                      
                     if((time_t)-1 != currentTime)
                     {
                        if(currentTime < endTime)
                        {
                           ANT_DEBUG_V("Retrying. Current time = %d. "
                              "End time = %d", (int)currentTime, (int)endTime);
                        
                           retryTx = ANT_TRUE;
                        }
                        else
                        {
                           ANT_ERROR("Command Complete: ANT_STATUS_UNSPECIFIED_ERROR");
                           ANT_ERROR("Aborting. Current time = %d. End Time = %d",
                                                (int)currentTime, (int)endTime);
                        }
                     }
                     else
                     {
                        ANT_ERROR("Command Complete: failed to get current time");
                     }
                  
                     break;
                  }
                  case ANT_STATUS_SUCCESS:
                  {
                     break;
                  }
                  default:
                  {
                     ANT_ERROR("Unhandled command complete status");
                     break;
                  }
               }
            }
            else
            {
               ANT_WARN("Command Complete: wait for message failed");
            }
         } while (retryRx);
      }
      else
      {
         ANT_ERROR("write failed");
         retryRx = ANT_FALSE;
      
         status = ANT_STATUS_FAILED;
      }
   } while(retryTx);
   
   ant_close_tx_transport(tx_socket);

   ANT_DEBUG_V("releasing txLock in %s", __FUNCTION__);
   pthread_mutex_unlock(&txLock);
   ANT_DEBUG_V("released txLock in %s", __FUNCTION__);

   ANT_FUNC_END();

   return status;
}

const char *ant_get_lib_version()
{
   return "libantradio.so Bluez HCI Transport Version " 
          LIBANT_STACK_MAJOR "." LIBANT_STACK_MINOR "." LIBANT_STACK_INCRE;
}

