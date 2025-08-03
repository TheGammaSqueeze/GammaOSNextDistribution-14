/*
 * Copyright 2010-2023 NXP
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

/*
 * TML Implementation.
 */

#include <phDal4Nfc_messageQueueLib.h>
#include <phNxpConfig.h>
#include <phNxpLog.h>
#include <phNxpNciHal_utils.h>
#include <phOsalNfc_Timer.h>
#include <phTmlNfc.h>
#include "NfccTransportFactory.h"

/*
 * Duration of Timer to wait after sending an Nci packet
 */
#define PHTMLNFC_MAXTIME_RETRANSMIT (200U)
#define MAX_WRITE_RETRY_COUNT 0x03
#define MAX_READ_RETRY_DELAY_IN_MILLISEC (150U)
/* Retry Count = Standby Recovery time of NFCC / Retransmission time + 1 */
static uint8_t bCurrentRetryCount = (2000 / PHTMLNFC_MAXTIME_RETRANSMIT) + 1;

/* Value to reset variables of TML  */
#define PH_TMLNFC_RESET_VALUE (0x00)

/* Indicates a Initial or offset value */
#define PH_TMLNFC_VALUE_ONE (0x01)

spTransport gpTransportObj;
extern bool_t gsIsFirstHalMinOpen;

/* Initialize Context structure pointer used to access context structure */
phTmlNfc_Context_t* gpphTmlNfc_Context = NULL;
/* Local Function prototypes */
static NFCSTATUS phTmlNfc_StartThread(void);
static void phTmlNfc_ReadDeferredCb(void* pParams);
static void phTmlNfc_WriteDeferredCb(void* pParams);
static void* phTmlNfc_TmlThread(void* pParam);
static void* phTmlNfc_TmlWriterThread(void* pParam);
static void phTmlNfc_SignalWriteComplete(void);
static int phTmlNfc_WaitReadInit(void);

/* Function definitions */

/*******************************************************************************
**
** Function         phTmlNfc_Init
**
** Description      Provides initialization of TML layer and hardware interface
**                  Configures given hardware interface and sends handle to the
**                  caller
**
** Parameters       pConfig - TML configuration details as provided by the upper
**                            layer
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - initialization successful
**                  NFCSTATUS_INVALID_PARAMETER - at least one parameter is
**                                                invalid
**                  NFCSTATUS_FAILED - initialization failed (for example,
**                                     unable to open hardware interface)
**                  NFCSTATUS_INVALID_DEVICE - device has not been opened or has
**                                             been disconnected
**
*******************************************************************************/
NFCSTATUS phTmlNfc_Init(pphTmlNfc_Config_t pConfig) {
  NFCSTATUS wInitStatus = NFCSTATUS_SUCCESS;

  /* Check if TML layer is already Initialized */
  if (NULL != gpphTmlNfc_Context) {
    /* TML initialization is already completed */
    wInitStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_ALREADY_INITIALISED);
  }
  /* Validate Input parameters */
  else if ((NULL == pConfig) ||
           (PH_TMLNFC_RESET_VALUE == pConfig->dwGetMsgThreadId)) {
    /*Parameters passed to TML init are wrong */
    wInitStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_INVALID_PARAMETER);
  } else {
    /* Allocate memory for TML context */
    gpphTmlNfc_Context =
        (phTmlNfc_Context_t*)malloc(sizeof(phTmlNfc_Context_t));

    if (NULL == gpphTmlNfc_Context) {
      wInitStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_FAILED);
    } else {
      /*Configure transport layer for communication*/
      if ((gpTransportObj == NULL) &&
          (NFCSTATUS_SUCCESS != phTmlNfc_ConfigTransport()))
        return NFCSTATUS_FAILED;

      if (gsIsFirstHalMinOpen) {
        if (!gpTransportObj->Flushdata(pConfig)) {
          NXPLOG_NCIHAL_E("Flushdata Failed");
        }
      }
      /* Initialise all the internal TML variables */
      memset(gpphTmlNfc_Context, PH_TMLNFC_RESET_VALUE,
             sizeof(phTmlNfc_Context_t));
      /* Make sure that the thread runs once it is created */
      gpphTmlNfc_Context->bThreadDone = 1;
      /* Open the device file to which data is read/written */
      wInitStatus = gpTransportObj->OpenAndConfigure(
          pConfig, &(gpphTmlNfc_Context->pDevHandle));

      if (NFCSTATUS_SUCCESS != wInitStatus) {
        wInitStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_INVALID_DEVICE);
        gpphTmlNfc_Context->pDevHandle = NULL;
      } else {
        phTmlNfc_IoCtl(phTmlNfc_e_SetNfcState);
        gpphTmlNfc_Context->tReadInfo.bEnable = 0;
        gpphTmlNfc_Context->tWriteInfo.bEnable = 0;
        gpphTmlNfc_Context->tReadInfo.bThreadBusy = false;
        gpphTmlNfc_Context->tWriteInfo.bThreadBusy = false;
        if (pConfig->fragment_len == 0x00)
          pConfig->fragment_len = PH_TMLNFC_FRGMENT_SIZE_PN557;
        gpphTmlNfc_Context->fragment_len = pConfig->fragment_len;

        if (0 != sem_init(&gpphTmlNfc_Context->rxSemaphore, 0, 0)) {
          wInitStatus = NFCSTATUS_FAILED;
        } else if (0 != phTmlNfc_WaitReadInit()) {
          wInitStatus = NFCSTATUS_FAILED;
        } else if (0 != sem_init(&gpphTmlNfc_Context->txSemaphore, 0, 0)) {
          wInitStatus = NFCSTATUS_FAILED;
        } else if (0 != sem_init(&gpphTmlNfc_Context->postMsgSemaphore, 0, 0)) {
          wInitStatus = NFCSTATUS_FAILED;
        } else {
          sem_post(&gpphTmlNfc_Context->postMsgSemaphore);
          /* Start TML thread (to handle write and read operations) */
          if (NFCSTATUS_SUCCESS != phTmlNfc_StartThread()) {
            wInitStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_FAILED);
          } else {
            /* Create Timer used for Retransmission of NCI packets */
            gpphTmlNfc_Context->dwTimerId = phOsalNfc_Timer_Create();
            if (PH_OSALNFC_TIMER_ID_INVALID != gpphTmlNfc_Context->dwTimerId) {
              /* Store the Thread Identifier to which Message is to be posted */
              gpphTmlNfc_Context->dwCallbackThreadId =
                  pConfig->dwGetMsgThreadId;
              /* Enable retransmission of Nci packet & set retry count to
               * default */
              gpphTmlNfc_Context->eConfig = phTmlNfc_e_DisableRetrans;
              /* Retry Count = Standby Recovery time of NFCC / Retransmission
               * time + 1 */
              gpphTmlNfc_Context->bRetryCount =
                  (2000 / PHTMLNFC_MAXTIME_RETRANSMIT) + 1;
              gpphTmlNfc_Context->bWriteCbInvoked = false;
            } else {
              wInitStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_FAILED);
            }
          }
        }
      }
    }
  }
  /* Clean up all the TML resources if any error */
  if (NFCSTATUS_SUCCESS != wInitStatus) {
    /* Clear all handles and memory locations initialized during init */
    phTmlNfc_Shutdown_CleanUp();
  }

  return wInitStatus;
}

/*******************************************************************************
**
** Function         phTmlNfc_ConfigTransport
**
** Description      Configure Transport channel based on transport type provided
**                  in config file
**
** Returns          NFCSTATUS_SUCCESS If transport channel is configured
**                  NFCSTATUS_FAILED If transport channel configuration failed
**
*******************************************************************************/
NFCSTATUS phTmlNfc_ConfigTransport() {
  unsigned long transportType = UNKNOWN;
  unsigned long value = 0;
  int isfound = GetNxpNumValue(NAME_NXP_TRANSPORT, &value, sizeof(value));
  if (isfound > 0) {
    transportType = value;
  }
  gpTransportObj = transportFactory.getTransport((transportIntf)transportType);
  if (gpTransportObj == nullptr) {
    NXPLOG_TML_E("No Transport channel available \n");
    return NFCSTATUS_FAILED;
  }
  return NFCSTATUS_SUCCESS;
}
/*******************************************************************************
**
** Function         phTmlNfc_ConfigNciPktReTx
**
** Description      Provides Enable/Disable Retransmission of NCI packets
**                  Needed in case of Timeout between Transmission and Reception
**                  of NCI packets. Retransmission can be enabled only if
**                  standby mode is enabled
**
** Parameters       eConfig - values from phTmlNfc_ConfigRetrans_t
**                  bRetryCount - Number of times Nci packets shall be
**                                retransmitted (default = 3)
**
** Returns          None
**
*******************************************************************************/
void phTmlNfc_ConfigNciPktReTx(phTmlNfc_ConfigRetrans_t eConfiguration,
                               uint8_t bRetryCounter) {
  /* Enable/Disable Retransmission */

  gpphTmlNfc_Context->eConfig = eConfiguration;
  if (phTmlNfc_e_EnableRetrans == eConfiguration) {
    /* Check whether Retry counter passed is valid */
    if (0 != bRetryCounter) {
      gpphTmlNfc_Context->bRetryCount = bRetryCounter;
    }
    /* Set retry counter to its default value */
    else {
      /* Retry Count = Standby Recovery time of NFCC / Retransmission time + 1
       */
      gpphTmlNfc_Context->bRetryCount =
          (2000 / PHTMLNFC_MAXTIME_RETRANSMIT) + 1;
    }
  }

  return;
}

/*******************************************************************************
**
** Function         phTmlNfc_StartThread
**
** Description      Initializes comport, reader and writer threads
**
** Parameters       None
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - threads initialized successfully
**                  NFCSTATUS_FAILED - initialization failed due to system error
**
*******************************************************************************/
static NFCSTATUS phTmlNfc_StartThread(void) {
  NFCSTATUS wStartStatus = NFCSTATUS_SUCCESS;
  void* h_threadsEvent = 0x00;
  int pthread_create_status = 0;

  /* Create Reader and Writer threads */
  pthread_create_status =
      pthread_create(&gpphTmlNfc_Context->readerThread, NULL,
                     &phTmlNfc_TmlThread, (void*)h_threadsEvent);
  if (0 != pthread_create_status) {
    wStartStatus = NFCSTATUS_FAILED;
  } else {
    /*Start Writer Thread*/
    pthread_create_status =
        pthread_create(&gpphTmlNfc_Context->writerThread, NULL,
                       &phTmlNfc_TmlWriterThread, (void*)h_threadsEvent);
    if (0 != pthread_create_status) {
      wStartStatus = NFCSTATUS_FAILED;
    }
  }

  return wStartStatus;
}

/*******************************************************************************
**
** Function         phTmlNfc_TmlThread
**
** Description      Read the data from the lower layer driver
**
** Parameters       pParam  - parameters for Writer thread function
**
** Returns          None
**
*******************************************************************************/
static void* phTmlNfc_TmlThread(void* pParam) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;
  int32_t dwNoBytesWrRd = PH_TMLNFC_RESET_VALUE;
  uint8_t temp[260];
  uint8_t readRetryDelay = 0;
  /* Transaction info buffer to be passed to Callback Thread */
  static phTmlNfc_TransactInfo_t tTransactionInfo;
  /* Structure containing Tml callback function and parameters to be invoked
     by the callback thread */
  static phLibNfc_DeferredCall_t tDeferredInfo;
  /* Initialize Message structure to post message onto Callback Thread */
  static phLibNfc_Message_t tMsg;
  UNUSED_PROP(pParam);
  NXPLOG_TML_D("PN54X - Tml Reader Thread Started................\n");

  /* Reader thread loop shall be running till shutdown is invoked */
  while (gpphTmlNfc_Context->bThreadDone) {
    /* If Tml read is requested */
    /* Set the variable to success initially */
    wStatus = NFCSTATUS_SUCCESS;
    if (-1 == sem_wait(&gpphTmlNfc_Context->rxSemaphore)) {
      NXPLOG_TML_E("sem_wait didn't return success \n");
    }

    /* If Tml read is requested */
    if (1 == gpphTmlNfc_Context->tReadInfo.bEnable) {
      NXPLOG_TML_D("PN54X - Read requested.....\n");
      /* Set the variable to success initially */
      wStatus = NFCSTATUS_SUCCESS;

      /* Variable to fetch the actual number of bytes read */
      dwNoBytesWrRd = PH_TMLNFC_RESET_VALUE;

      /* Read the data from the file onto the buffer */
      if (NULL != gpphTmlNfc_Context->pDevHandle) {
        NXPLOG_TML_D("PN54X - Invoking I2C Read.....\n");
        dwNoBytesWrRd =
            gpTransportObj->Read(gpphTmlNfc_Context->pDevHandle, temp, 260);

        if (-1 == dwNoBytesWrRd) {
          NXPLOG_TML_E("PN54X - Error in I2C Read.....\n");
          if (readRetryDelay < MAX_READ_RETRY_DELAY_IN_MILLISEC) {
            /*sleep for 30/60/90/120/150 msec between each read trial incase of
             * read error*/
            readRetryDelay += 30;
          }
          usleep(readRetryDelay * 1000);
          sem_post(&gpphTmlNfc_Context->rxSemaphore);
        } else if (dwNoBytesWrRd > 260) {
          NXPLOG_TML_E("Numer of bytes read exceeds the limit 260.....\n");
          readRetryDelay = 0;
          sem_post(&gpphTmlNfc_Context->rxSemaphore);
        } else {
          memcpy(gpphTmlNfc_Context->tReadInfo.pBuffer, temp, dwNoBytesWrRd);
          readRetryDelay = 0;

          NXPLOG_TML_D("PN54X - I2C Read successful.....\n");
          /* This has to be reset only after a successful read */
          gpphTmlNfc_Context->tReadInfo.bEnable = 0;
          if ((phTmlNfc_e_EnableRetrans == gpphTmlNfc_Context->eConfig) &&
              (0x00 != (gpphTmlNfc_Context->tReadInfo.pBuffer[0] & 0xE0))) {
            NXPLOG_TML_D("PN54X - Retransmission timer stopped.....\n");
            /* Stop Timer to prevent Retransmission */
            uint32_t timerStatus =
                phOsalNfc_Timer_Stop(gpphTmlNfc_Context->dwTimerId);
            if (NFCSTATUS_SUCCESS != timerStatus) {
              NXPLOG_TML_E("PN54X - timer stopped returned failure.....\n");
            } else {
              gpphTmlNfc_Context->bWriteCbInvoked = false;
            }
          }
          /* Update the actual number of bytes read including header */
          gpphTmlNfc_Context->tReadInfo.wLength = (uint16_t)(dwNoBytesWrRd);
          phNxpNciHal_print_packet("RECV",
                                   gpphTmlNfc_Context->tReadInfo.pBuffer,
                                   gpphTmlNfc_Context->tReadInfo.wLength);

          dwNoBytesWrRd = PH_TMLNFC_RESET_VALUE;

          /* Fill the Transaction info structure to be passed to Callback
           * Function */
          tTransactionInfo.wStatus = wStatus;
          tTransactionInfo.pBuff = gpphTmlNfc_Context->tReadInfo.pBuffer;
          /* Actual number of bytes read is filled in the structure */
          tTransactionInfo.wLength = gpphTmlNfc_Context->tReadInfo.wLength;

          /* Read operation completed successfully. Post a Message onto Callback
           * Thread*/
          /* Prepare the message to be posted on User thread */
          tDeferredInfo.pCallback = &phTmlNfc_ReadDeferredCb;
          tDeferredInfo.pParameter = &tTransactionInfo;
          tMsg.eMsgType = PH_LIBNFC_DEFERREDCALL_MSG;
          tMsg.pMsgData = &tDeferredInfo;
          tMsg.Size = sizeof(tDeferredInfo);
          NXPLOG_TML_D("PN54X - Posting read message.....\n");
          phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId, &tMsg);
        }
      } else {
        NXPLOG_TML_D("PN54X -gpphTmlNfc_Context->pDevHandle is NULL");
      }
    } else {
      NXPLOG_TML_D("PN54X - read request NOT enabled");
      usleep(10 * 1000);
    }
  } /* End of While loop */

  return NULL;
}

/*******************************************************************************
**
** Function         phTmlNfc_TmlWriterThread
**
** Description      Writes the requested data onto the lower layer driver
**
** Parameters       pParam  - context provided by upper layer
**
** Returns          None
**
*******************************************************************************/
static void* phTmlNfc_TmlWriterThread(void* pParam) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;
  int32_t dwNoBytesWrRd = PH_TMLNFC_RESET_VALUE;
  /* Transaction info buffer to be passed to Callback Thread */
  static phTmlNfc_TransactInfo_t tTransactionInfo;
  /* Structure containing Tml callback function and parameters to be invoked
     by the callback thread */
  static phLibNfc_DeferredCall_t tDeferredInfo;
  /* Initialize Message structure to post message onto Callback Thread */
  static phLibNfc_Message_t tMsg;
  /* In case of I2C Write Retry */
  static uint16_t retry_cnt;
  UNUSED_PROP(pParam);
  NXPLOG_TML_D("PN54X - Tml Writer Thread Started................\n");

  /* Writer thread loop shall be running till shutdown is invoked */
  while (gpphTmlNfc_Context->bThreadDone) {
    NXPLOG_TML_D("PN54X - Tml Writer Thread Running................\n");
    if (-1 == sem_wait(&gpphTmlNfc_Context->txSemaphore)) {
      NXPLOG_TML_E("sem_wait didn't return success \n");
    }
    /* If Tml write is requested */
    if (1 == gpphTmlNfc_Context->tWriteInfo.bEnable) {
      NXPLOG_TML_D("PN54X - Write requested.....\n");
      /* Set the variable to success initially */
      wStatus = NFCSTATUS_SUCCESS;
      if (NULL != gpphTmlNfc_Context->pDevHandle) {
      retry:
        gpphTmlNfc_Context->tWriteInfo.bEnable = 0;
        /* Variable to fetch the actual number of bytes written */
        dwNoBytesWrRd = PH_TMLNFC_RESET_VALUE;
        /* Write the data in the buffer onto the file */
        NXPLOG_TML_D("PN54X - Invoking I2C Write.....\n");
        /* TML reader writer callback synchronization mutex lock --- START */
        pthread_mutex_lock(&gpphTmlNfc_Context->wait_busy_lock);
        gpphTmlNfc_Context->gWriterCbflag = false;
        dwNoBytesWrRd =
            gpTransportObj->Write(gpphTmlNfc_Context->pDevHandle,
                                  gpphTmlNfc_Context->tWriteInfo.pBuffer,
                                  gpphTmlNfc_Context->tWriteInfo.wLength);
        /* TML reader writer callback synchronization mutex lock --- END */
        pthread_mutex_unlock(&gpphTmlNfc_Context->wait_busy_lock);

        /* Try I2C Write Five Times, if it fails : Raju */
        if (-1 == dwNoBytesWrRd) {
          if (gpTransportObj->IsFwDnldModeEnabled()) {
            if (retry_cnt++ < MAX_WRITE_RETRY_COUNT) {
              NXPLOG_TML_D("PN54X - Error in I2C Write  - Retry 0x%x",
                           retry_cnt);
              // Add a 10 ms delay to ensure NFCC is not still in stand by mode.
              usleep(10 * 1000);
              goto retry;
            }
          }
          NXPLOG_TML_D("PN54X - Error in I2C Write.....\n");
          wStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_FAILED);
        } else {
          phNxpNciHal_print_packet("SEND",
                                   gpphTmlNfc_Context->tWriteInfo.pBuffer,
                                   gpphTmlNfc_Context->tWriteInfo.wLength);
        }
        retry_cnt = 0;
        if (NFCSTATUS_SUCCESS == wStatus) {
          NXPLOG_TML_D("PN54X - I2C Write successful.....\n");
          dwNoBytesWrRd = PH_TMLNFC_VALUE_ONE;
        }
        /* Fill the Transaction info structure to be passed to Callback Function
         */
        tTransactionInfo.wStatus = wStatus;
        tTransactionInfo.pBuff = gpphTmlNfc_Context->tWriteInfo.pBuffer;
        /* Actual number of bytes written is filled in the structure */
        tTransactionInfo.wLength = (uint16_t)dwNoBytesWrRd;

        /* Prepare the message to be posted on the User thread */
        tDeferredInfo.pCallback = &phTmlNfc_WriteDeferredCb;
        tDeferredInfo.pParameter = &tTransactionInfo;
        /* Write operation completed successfully. Post a Message onto Callback
         * Thread*/
        tMsg.eMsgType = PH_LIBNFC_DEFERREDCALL_MSG;
        tMsg.pMsgData = &tDeferredInfo;
        tMsg.Size = sizeof(tDeferredInfo);

        /* Check whether Retransmission needs to be started,
         * If yes, Post message only if
         * case 1. Message is not posted &&
         * case 11. Write status is success ||
         * case 12. Last retry of write is also failure
         */
        if ((phTmlNfc_e_EnableRetrans == gpphTmlNfc_Context->eConfig) &&
            (0x00 != (gpphTmlNfc_Context->tWriteInfo.pBuffer[0] & 0xE0))) {
          if (gpphTmlNfc_Context->bWriteCbInvoked == false) {
            if ((NFCSTATUS_SUCCESS == wStatus) || (bCurrentRetryCount == 0)) {
              NXPLOG_TML_D("PN54X - Posting Write message.....\n");
              phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId,
                                    &tMsg);
              gpphTmlNfc_Context->bWriteCbInvoked = true;
            }
          }
        } else {
          NXPLOG_TML_D("PN54X - Posting Fresh Write message.....\n");
          phTmlNfc_DeferredCall(gpphTmlNfc_Context->dwCallbackThreadId, &tMsg);
          if (NFCSTATUS_SUCCESS == wStatus) {
            /*TML reader writer thread callback synchronization---START*/
            pthread_mutex_lock(&gpphTmlNfc_Context->wait_busy_lock);
            gpphTmlNfc_Context->gWriterCbflag = true;
            phTmlNfc_SignalWriteComplete();
            /*TML reader writer thread callback synchronization---END*/
            pthread_mutex_unlock(&gpphTmlNfc_Context->wait_busy_lock);
          }
        }
      } else {
        NXPLOG_TML_D("PN54X - gpphTmlNfc_Context->pDevHandle is NULL");
      }
    } else {
      NXPLOG_TML_D("PN54X - Write request NOT enabled");
      usleep(10000);
    }

  } /* End of While loop */

  return NULL;
}

/*******************************************************************************
**
** Function         phTmlNfc_CleanUp
**
** Description      Clears all handles opened during TML initialization
**
** Parameters       None
**
** Returns          None
**
*******************************************************************************/
void phTmlNfc_CleanUp(void) {
  if (NULL == gpphTmlNfc_Context) {
    return;
  }
  sem_destroy(&gpphTmlNfc_Context->rxSemaphore);
  sem_destroy(&gpphTmlNfc_Context->txSemaphore);
  sem_destroy(&gpphTmlNfc_Context->postMsgSemaphore);
  pthread_mutex_destroy(&gpphTmlNfc_Context->wait_busy_lock);
  pthread_cond_destroy(&gpphTmlNfc_Context->wait_busy_condition);
  gpTransportObj = NULL;
  /* Clear memory allocated for storing Context variables */
  free((void*)gpphTmlNfc_Context);
  /* Set the pointer to NULL to indicate De-Initialization */
  gpphTmlNfc_Context = NULL;

  return;
}

/*******************************************************************************
**
** Function         phTmlNfc_Shutdown
**
** Description      Uninitializes TML layer and hardware interface
**
** Parameters       None
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - TML configuration released successfully
**                  NFCSTATUS_INVALID_PARAMETER - at least one parameter is
**                                                invalid
**                  NFCSTATUS_FAILED - un-initialization failed (example: unable
**                                     to close interface)
**
*******************************************************************************/
NFCSTATUS phTmlNfc_Shutdown(void) {
  NFCSTATUS wShutdownStatus = NFCSTATUS_SUCCESS;

  /* Check whether TML is Initialized */
  if (NULL != gpphTmlNfc_Context) {
    /* Reset thread variable to terminate the thread */
    gpphTmlNfc_Context->bThreadDone = 0;
    usleep(1000);
    /* Clear All the resources allocated during initialization */
    sem_post(&gpphTmlNfc_Context->rxSemaphore);
    usleep(1000);
    sem_post(&gpphTmlNfc_Context->txSemaphore);
    usleep(1000);
    sem_post(&gpphTmlNfc_Context->postMsgSemaphore);
    usleep(1000);
    sem_post(&gpphTmlNfc_Context->postMsgSemaphore);
    usleep(1000);

    if (IS_CHIP_TYPE_L(sn100u)) {
      (void)gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                      MODE_POWER_OFF);
    }
    phTmlNfc_IoCtl(phTmlNfc_e_ResetNfcState);
    gpTransportObj->Close(gpphTmlNfc_Context->pDevHandle);
    gpphTmlNfc_Context->pDevHandle = NULL;
    if (0 != pthread_join(gpphTmlNfc_Context->readerThread, (void**)NULL)) {
      NXPLOG_TML_E("Fail to kill reader thread!");
    }
    if (0 != pthread_join(gpphTmlNfc_Context->writerThread, (void**)NULL)) {
      NXPLOG_TML_E("Fail to kill writer thread!");
    }
    NXPLOG_TML_D("bThreadDone == 0");

  } else {
    wShutdownStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_NOT_INITIALISED);
  }

  return wShutdownStatus;
}

/*******************************************************************************
**
** Function         phTmlNfc_Write
**
** Description      Asynchronously writes given data block to hardware
**                  interface/driver. Enables writer thread if there are no
**                  write requests pending. Returns successfully once writer
**                  thread completes write operation. Notifies upper layer using
**                  callback mechanism.
**
**                  NOTE:
**                  * it is important to post a message with id
**                    PH_TMLNFC_WRITE_MESSAGE to IntegrationThread after data
**                    has been written to PN54X
**                  * if CRC needs to be computed, then input buffer should be
**                    capable to store two more bytes apart from length of
**                    packet
**
** Parameters       pBuffer - data to be sent
**                  wLength - length of data buffer
**                  pTmlWriteComplete - pointer to the function to be invoked
**                                      upon completion
**                  pContext - context provided by upper layer
**
** Returns          NFC status:
**                  NFCSTATUS_PENDING - command is yet to be processed
**                  NFCSTATUS_INVALID_PARAMETER - at least one parameter is
**                                                invalid
**                  NFCSTATUS_BUSY - write request is already in progress
**
*******************************************************************************/
NFCSTATUS phTmlNfc_Write(uint8_t* pBuffer, uint16_t wLength,
                         pphTmlNfc_TransactCompletionCb_t pTmlWriteComplete,
                         void* pContext) {
  NFCSTATUS wWriteStatus;

  /* Check whether TML is Initialized */

  if (NULL != gpphTmlNfc_Context) {
    if ((NULL != gpphTmlNfc_Context->pDevHandle) && (NULL != pBuffer) &&
        (PH_TMLNFC_RESET_VALUE != wLength) && (NULL != pTmlWriteComplete)) {
      if (!gpphTmlNfc_Context->tWriteInfo.bThreadBusy) {
        /* Setting the flag marks beginning of a Write Operation */
        gpphTmlNfc_Context->tWriteInfo.bThreadBusy = true;
        /* Copy the buffer, length and Callback function,
           This shall be utilized while invoking the Callback function in thread
           */
        gpphTmlNfc_Context->tWriteInfo.pBuffer = pBuffer;
        gpphTmlNfc_Context->tWriteInfo.wLength = wLength;
        gpphTmlNfc_Context->tWriteInfo.pThread_Callback = pTmlWriteComplete;
        gpphTmlNfc_Context->tWriteInfo.pContext = pContext;

        wWriteStatus = NFCSTATUS_PENDING;
        // FIXME: If retry is going on. Stop the retry thread/timer
        if (phTmlNfc_e_EnableRetrans == gpphTmlNfc_Context->eConfig) {
          /* Set retry count to default value */
          // FIXME: If the timer expired there, and meanwhile we have created
          // a new request. The expired timer will think that retry is still
          // ongoing.
          bCurrentRetryCount = gpphTmlNfc_Context->bRetryCount;
          gpphTmlNfc_Context->bWriteCbInvoked = false;
        }
        /* Set event to invoke Writer Thread */
        gpphTmlNfc_Context->tWriteInfo.bEnable = 1;
        sem_post(&gpphTmlNfc_Context->txSemaphore);
      } else {
        wWriteStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_BUSY);
      }
    } else {
      wWriteStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_INVALID_PARAMETER);
    }
  } else {
    wWriteStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_NOT_INITIALISED);
  }

  return wWriteStatus;
}

/*******************************************************************************
**
** Function         phTmlNfc_Read
**
** Description      Asynchronously reads data from the driver
**                  Number of bytes to be read and buffer are passed by upper
**                  layer.
**                  Enables reader thread if there are no read requests pending
**                  Returns successfully once read operation is completed
**                  Notifies upper layer using callback mechanism
**
** Parameters       pBuffer - location to send read data to the upper layer via
**                            callback
**                  wLength - length of read data buffer passed by upper layer
**                  pTmlReadComplete - pointer to the function to be invoked
**                                     upon completion of read operation
**                  pContext - context provided by upper layer
**
** Returns          NFC status:
**                  NFCSTATUS_PENDING - command is yet to be processed
**                  NFCSTATUS_INVALID_PARAMETER - at least one parameter is
**                                                invalid
**                  NFCSTATUS_BUSY - read request is already in progress
**
*******************************************************************************/
NFCSTATUS phTmlNfc_Read(uint8_t* pBuffer, uint16_t wLength,
                        pphTmlNfc_TransactCompletionCb_t pTmlReadComplete,
                        void* pContext) {
  NFCSTATUS wReadStatus;
  int rxSemVal = 0, ret = 0;

  /* Check whether TML is Initialized */
  if (NULL != gpphTmlNfc_Context) {
    if ((gpphTmlNfc_Context->pDevHandle != NULL) && (NULL != pBuffer) &&
        (PH_TMLNFC_RESET_VALUE != wLength) && (NULL != pTmlReadComplete)) {
      if (!gpphTmlNfc_Context->tReadInfo.bThreadBusy) {
        /* Setting the flag marks beginning of a Read Operation */
        gpphTmlNfc_Context->tReadInfo.bThreadBusy = true;
        /* Copy the buffer, length and Callback function,
           This shall be utilized while invoking the Callback function in thread
           */
        gpphTmlNfc_Context->tReadInfo.pBuffer = pBuffer;
        gpphTmlNfc_Context->tReadInfo.wLength = wLength;
        gpphTmlNfc_Context->tReadInfo.pThread_Callback = pTmlReadComplete;
        gpphTmlNfc_Context->tReadInfo.pContext = pContext;
        wReadStatus = NFCSTATUS_PENDING;

        /* Set event to invoke Reader Thread */
        gpphTmlNfc_Context->tReadInfo.bEnable = 1;
        ret = sem_getvalue(&gpphTmlNfc_Context->rxSemaphore, &rxSemVal);
        /* Post rxSemaphore either if sem_getvalue() is failed or rxSemVal is 0
         */
        if (ret || !rxSemVal) {
          sem_post(&gpphTmlNfc_Context->rxSemaphore);
        } else {
          NXPLOG_TML_D(
              "%s: skip reader thread scheduling, ret=%x, rxSemaVal=%x",
              __func__, ret, rxSemVal);
        }
      } else {
        wReadStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_BUSY);
      }
    } else {
      wReadStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_INVALID_PARAMETER);
    }
  } else {
    wReadStatus = PHNFCSTVAL(CID_NFC_TML, NFCSTATUS_NOT_INITIALISED);
  }

  return wReadStatus;
}

/*******************************************************************************
**
** Function         phTmlNfc_ReadAbort
**
** Description      Aborts pending read request (if any)
**
** Parameters       None
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - ongoing read operation aborted
**                  NFCSTATUS_INVALID_PARAMETER - at least one parameter is
**                                                invalid
**                  NFCSTATUS_NOT_INITIALIZED - TML layer is not initialized
**                  NFCSTATUS_BOARD_COMMUNICATION_ERROR - unable to cancel read
**                                                        operation
**
*******************************************************************************/
NFCSTATUS phTmlNfc_ReadAbort(void) {
  NFCSTATUS wStatus = NFCSTATUS_INVALID_PARAMETER;
  gpphTmlNfc_Context->tReadInfo.bEnable = 0;

  /*Reset the flag to accept another Read Request */
  gpphTmlNfc_Context->tReadInfo.bThreadBusy = false;
  wStatus = NFCSTATUS_SUCCESS;

  return wStatus;
}

/*******************************************************************************
**
** Function         phTmlNfc_WriteAbort
**
** Description      Aborts pending write request (if any)
**
** Parameters       None
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS - ongoing write operation aborted
**                  NFCSTATUS_INVALID_PARAMETER - at least one parameter is
**                                                invalid
**                  NFCSTATUS_NOT_INITIALIZED - TML layer is not initialized
**                  NFCSTATUS_BOARD_COMMUNICATION_ERROR - unable to cancel write
**                                                        operation
**
*******************************************************************************/
NFCSTATUS phTmlNfc_WriteAbort(void) {
  NFCSTATUS wStatus = NFCSTATUS_INVALID_PARAMETER;

  gpphTmlNfc_Context->tWriteInfo.bEnable = 0;
  /* Stop if any retransmission is in progress */
  bCurrentRetryCount = 0;

  /* Reset the flag to accept another Write Request */
  gpphTmlNfc_Context->tWriteInfo.bThreadBusy = false;
  wStatus = NFCSTATUS_SUCCESS;

  return wStatus;
}

/*******************************************************************************
**
** Function         phTmlNfc_IoCtl
**
** Description      Resets device when insisted by upper layer
**                  Number of bytes to be read and buffer are passed by upper
**                  layer
**                  Enables reader thread if there are no read requests pending
**                  Returns successfully once read operation is completed
**                  Notifies upper layer using callback mechanism
**
** Parameters       eControlCode       - control code for a specific operation
**
** Returns          NFC status:
**                  NFCSTATUS_SUCCESS  - ioctl command completed successfully
**                  NFCSTATUS_FAILED   - ioctl command request failed
**
*******************************************************************************/
NFCSTATUS phTmlNfc_IoCtl(phTmlNfc_ControlCode_t eControlCode) {
  NFCSTATUS wStatus = NFCSTATUS_SUCCESS;

  if (NULL == gpphTmlNfc_Context) {
    wStatus = NFCSTATUS_FAILED;
  } else {
    uint8_t read_flag = (gpphTmlNfc_Context->tReadInfo.bEnable > 0);

    switch (eControlCode) {
      case phTmlNfc_e_PowerReset: {
        if (IS_CHIP_TYPE_GE(sn100u)) {
          /*VEN_RESET*/
          gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                    MODE_POWER_RESET);
        } else {
          gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                    MODE_POWER_ON);
          usleep(100 * 1000);
          gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                    MODE_POWER_OFF);
          usleep(100 * 1000);
          gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                    MODE_POWER_ON);
        }
        break;
      }
      case phTmlNfc_e_EnableVen: {
        if (IS_CHIP_TYPE_L(sn100u)) {
          gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                    MODE_POWER_ON);
          usleep(100 * 1000);
        }
        break;
      }
      case phTmlNfc_e_ResetDevice:

      {
        if (IS_CHIP_TYPE_L(sn100u)) {
          /*Reset PN54X*/
          gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                    MODE_POWER_ON);
          usleep(100 * 1000);
          gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                    MODE_POWER_OFF);
          usleep(100 * 1000);
          gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                    MODE_POWER_ON);
        }
        break;
      }
      case phTmlNfc_e_EnableNormalMode: {
        /*Reset PN54X*/
        gpphTmlNfc_Context->tReadInfo.bEnable = 0;
        if (nfcFL.nfccFL._NFCC_DWNLD_MODE == NFCC_DWNLD_WITH_VEN_RESET) {
          NXPLOG_TML_D(" phTmlNfc_e_EnableNormalMode complete with VEN RESET ");
          if (IS_CHIP_TYPE_L(sn100u)) {
            gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                      MODE_POWER_OFF);
            usleep(10 * 1000);
            gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                      MODE_POWER_ON);
            usleep(100 * 1000);
          } else {
            gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                      MODE_FW_GPIO_LOW);
          }
        } else if (nfcFL.nfccFL._NFCC_DWNLD_MODE == NFCC_DWNLD_WITH_NCI_CMD) {
          NXPLOG_TML_D(" phTmlNfc_e_EnableNormalMode complete with NCI CMD ");
          if (IS_CHIP_TYPE_L(sn100u)) {
            gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                      MODE_POWER_ON);
          } else {
            gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                      MODE_FW_GPIO_LOW);
          }
        }
        break;
      }
      case phTmlNfc_e_EnableDownloadMode: {
        phTmlNfc_ConfigNciPktReTx(phTmlNfc_e_DisableRetrans, 0);
        gpphTmlNfc_Context->tReadInfo.bEnable = 0;
        if (nfcFL.nfccFL._NFCC_DWNLD_MODE == NFCC_DWNLD_WITH_VEN_RESET) {
          NXPLOG_TML_D(
              " phTmlNfc_e_EnableDownloadMode complete with VEN RESET ");
          wStatus = gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                              MODE_FW_DWNLD_WITH_VEN);
        } else if (nfcFL.nfccFL._NFCC_DWNLD_MODE == NFCC_DWNLD_WITH_NCI_CMD) {
          NXPLOG_TML_D(" phTmlNfc_e_EnableDownloadMode complete with NCI CMD ");
          wStatus = gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                              MODE_FW_DWND_HIGH);
        }
        break;
      }
      case phTmlNfc_e_EnableDownloadModeWithVenRst: {
        phTmlNfc_ConfigNciPktReTx(phTmlNfc_e_DisableRetrans, 0);
        gpphTmlNfc_Context->tReadInfo.bEnable = 0;
        NXPLOG_TML_D(
            " phTmlNfc_e_EnableDownloadModewithVenRst complete with "
            "VEN RESET ");
        wStatus = gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                            MODE_FW_DWNLD_WITH_VEN);
        break;
      }
      case phTmlNfc_e_setFragmentSize: {
        if (IS_CHIP_TYPE_EQ(sn300u)) {
          if (phTmlNfc_IsFwDnldModeEnabled()) {
            gpphTmlNfc_Context->fragment_len = PH_TMLNFC_FRGMENT_SIZE_SN300;
          } else {
            gpphTmlNfc_Context->fragment_len = PH_TMLNFC_FRGMENT_SIZE_SNXXX;
          }
        } else if (IS_CHIP_TYPE_NE(pn557)) {
          gpphTmlNfc_Context->fragment_len = PH_TMLNFC_FRGMENT_SIZE_SNXXX;
        } else {
          gpphTmlNfc_Context->fragment_len = PH_TMLNFC_FRGMENT_SIZE_PN557;
        }
        NXPLOG_TML_D("Set FragmentSize: %u", gpphTmlNfc_Context->fragment_len);
        break;
      }
      case phTmlNfc_e_SetNfcState: {
        gpTransportObj->UpdateReadPending(gpphTmlNfc_Context->pDevHandle,
                                          MODE_NFC_SET_READ_PENDING);
        break;
      }
      case phTmlNfc_e_ResetNfcState: {
        gpTransportObj->UpdateReadPending(gpphTmlNfc_Context->pDevHandle,
                                          MODE_NFC_RESET_READ_PENDING);
        break;
      }
      case phTmlNfc_e_PullVenLow: {
        gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                  MODE_POWER_OFF);
        break;
      }
      case phTmlNfc_e_PullVenHigh: {
        gpTransportObj->NfccReset(gpphTmlNfc_Context->pDevHandle,
                                  MODE_POWER_ON);
        break;
      }
      default: {
        wStatus = NFCSTATUS_INVALID_PARAMETER;
        break;
      }
    }
    if (read_flag && (gpphTmlNfc_Context->tReadInfo.bEnable == 0x00)) {
      gpphTmlNfc_Context->tReadInfo.bEnable = 1;
      sem_post(&gpphTmlNfc_Context->rxSemaphore);
    }
  }

  return wStatus;
}

/*******************************************************************************
**
** Function         phTmlNfc_DeferredCall
**
** Description      Posts message on upper layer thread
**                  upon successful read or write operation
**
** Parameters       dwThreadId  - id of the thread posting message
**                  ptWorkerMsg - message to be posted
**
** Returns          None
**
*******************************************************************************/
void phTmlNfc_DeferredCall(uintptr_t dwThreadId,
                           phLibNfc_Message_t* ptWorkerMsg) {
  UNUSED_PROP(dwThreadId);
  /* Post message on the user thread to invoke the callback function */
  if (-1 == sem_wait(&gpphTmlNfc_Context->postMsgSemaphore)) {
    NXPLOG_TML_E("sem_wait didn't return success \n");
  }
  phDal4Nfc_msgsnd(gpphTmlNfc_Context->dwCallbackThreadId, ptWorkerMsg, 0);
  sem_post(&gpphTmlNfc_Context->postMsgSemaphore);
}

/*******************************************************************************
**
** Function         phTmlNfc_ReadDeferredCb
**
** Description      Read thread call back function
**
** Parameters       pParams - context provided by upper layer
**
** Returns          None
**
*******************************************************************************/
static void phTmlNfc_ReadDeferredCb(void* pParams) {
  /* Transaction info buffer to be passed to Callback Function */
  phTmlNfc_TransactInfo_t* pTransactionInfo = (phTmlNfc_TransactInfo_t*)pParams;

  /* Reset the flag to accept another Read Request */
  gpphTmlNfc_Context->tReadInfo.bThreadBusy = false;
  gpphTmlNfc_Context->tReadInfo.pThread_Callback(
      gpphTmlNfc_Context->tReadInfo.pContext, pTransactionInfo);

  return;
}

/*******************************************************************************
**
** Function         phTmlNfc_WriteDeferredCb
**
** Description      Write thread call back function
**
** Parameters       pParams - context provided by upper layer
**
** Returns          None
**
*******************************************************************************/
static void phTmlNfc_WriteDeferredCb(void* pParams) {
  /* Transaction info buffer to be passed to Callback Function */
  phTmlNfc_TransactInfo_t* pTransactionInfo = (phTmlNfc_TransactInfo_t*)pParams;

  /* Reset the flag to accept another Write Request */
  gpphTmlNfc_Context->tWriteInfo.bThreadBusy = false;
  gpphTmlNfc_Context->tWriteInfo.pThread_Callback(
      gpphTmlNfc_Context->tWriteInfo.pContext, pTransactionInfo);

  return;
}

void phTmlNfc_set_fragmentation_enabled(phTmlNfc_i2cfragmentation_t result) {
  fragmentation_enabled = result;
}

/*******************************************************************************
**
** Function         phTmlNfc_SignalWriteComplete
**
** Description      function to invoke reader thread
**
** Parameters       None
**
** Returns          None
**
*******************************************************************************/
static void phTmlNfc_SignalWriteComplete(void) {
  int ret = -1;
  if (gpphTmlNfc_Context->wait_busy_flag == true) {
    NXPLOG_TML_D("phTmlNfc_SignalWriteComplete - enter");
    gpphTmlNfc_Context->wait_busy_flag = false;

    ret = pthread_cond_signal(&gpphTmlNfc_Context->wait_busy_condition);
    if (ret) {
      NXPLOG_TML_E(" phTmlNfc_SignalWriteComplete failed, error = 0x%X", ret);
    }
    NXPLOG_TML_D("phTmlNfc_SignalWriteComplete - exit");
  }
}

/*******************************************************************************
**
** Function         phTmlNfc_WaitReadInit
**
** Description      init function for reader thread
**
** Parameters       None
**
** Returns          int
**
*******************************************************************************/
static int phTmlNfc_WaitReadInit(void) {
  int ret = -1;
  pthread_condattr_t attr;
  pthread_condattr_init(&attr);
  pthread_condattr_setclock(&attr, CLOCK_MONOTONIC);
  memset(&gpphTmlNfc_Context->wait_busy_condition, 0,
         sizeof(gpphTmlNfc_Context->wait_busy_condition));
  pthread_mutex_init(&gpphTmlNfc_Context->wait_busy_lock, NULL);
  ret = pthread_cond_init(&gpphTmlNfc_Context->wait_busy_condition, &attr);
  if (ret) {
    NXPLOG_TML_E(" phTphTmlNfc_WaitReadInit failed, error = 0x%X", ret);
  }
  return ret;
}

/*******************************************************************************
**
** Function         phTmlNfc_EnableFwDnldMode
**
** Description      wrapper function for enabling/disabling FW download mode
**
** Parameters       True/False
**
** Returns          NFCSTATUS
**
*******************************************************************************/
void phTmlNfc_EnableFwDnldMode(bool mode) {
  gpTransportObj->EnableFwDnldMode(mode);
}

/*******************************************************************************
**
** Function         phTmlNfc_IsFwDnldModeEnabled
**
** Description      wrapper function  to get the FW download flag
**
** Parameters       None
**
** Returns          True/False status of FW download flag
**
*******************************************************************************/
bool phTmlNfc_IsFwDnldModeEnabled(void) {
  return gpTransportObj->IsFwDnldModeEnabled();
}

/*******************************************************************************
**
** Function         phTmlNfc_Shutdown_CleanUp
**
** Description      wrapper function  for shutdown  and cleanup of resources
**
** Parameters       None
**
** Returns          NFCSTATUS
**
*******************************************************************************/
NFCSTATUS phTmlNfc_Shutdown_CleanUp() {
  NFCSTATUS wShutdownStatus = phTmlNfc_Shutdown();
  phTmlNfc_CleanUp();
  return wShutdownStatus;
}
