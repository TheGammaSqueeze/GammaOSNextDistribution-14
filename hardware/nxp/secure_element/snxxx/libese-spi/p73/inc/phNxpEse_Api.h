/******************************************************************************
 *
 *  Copyright 2018-2023 NXP
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

/**
 * \addtogroup spi_libese
 * \brief ESE Lib layer interface to application
 * @{ */

#ifndef _PHNXPSPILIB_API_H_
#define _PHNXPSPILIB_API_H_

#include <phEseStatus.h>
#include <phNxpEsePal.h>

/**
 * \ingroup spi_libese
 * \brief Ese data buffer
 *
 */
typedef struct phNxpEse_data {
  uint32_t len;    /*!< length of the buffer */
  uint8_t* p_data; /*!< pointer to a buffer */
} phNxpEse_data;

/**
 * \ingroup spi_libese
 * \brief Ese Channel mode
 *
 */
typedef enum {
  ESE_MODE_NORMAL = 0, /*!< All wired transaction other OSU */
  ESE_MODE_OSU         /*!< Jcop Os update mode */
} phNxpEse_initMode;

/**
 * \ingroup spi_libese
 * \brief Ese logical interface  i.e. MediaType
 *
 */
typedef enum {
  ESE_PROTOCOL_MEDIA_SPI = 0x08,          /*!< Media Type - SPI legacy  */
  ESE_PROTOCOL_MEDIA_SPI_APDU_GATE = 0xD0 /*!Media Type - APDU Gate */
} phNxpEse_mediaType;

typedef enum {
  WTX_ONGOING = 1,
  WTX_END = 2,
} phNxpEse_wtxState;

typedef enum phNxpEseProto7816_OsType {
  UNKNOWN_MODE = 0,
  JCOP_MODE = 0x1,
  OSU_MODE = 0x2,
} phNxpEseProto7816_OsType_t;

#define MODE_JCOP 0x01
#define MODE_OSU 0x02
#define RESET_APP_WTX_COUNT 0

typedef void(NotifyWtxReq)(phNxpEse_wtxState);
/**
 * \ingroup spi_libese
 * \brief Ese library init parameters to be set while calling phNxpEse_init
 *
 */
typedef struct phNxpEse_initParams {
  phNxpEse_initMode initMode;   /*!< Ese communication mode */
  phNxpEse_mediaType mediaType; /*!< Logical channel for Ese communication */
  NotifyWtxReq* fPtr_WtxNtf;    /*!< Wait extension callback notification*/
} phNxpEse_initParams;

/*!
 * \brief SEAccess kit MW Android version
 */
#define NXP_ANDROID_VER (9U)

/*!
 * \brief SEAccess kit MW Major version
 */
#define ESELIB_MW_VERSION_MAJ (0x0U)

/*!
 * \brief SEAccess kit MW Minor version
 */
#define ESELIB_MW_VERSION_MIN (0x04)

/**
 * \ingroup spi_libese
 *
 * \brief  This function is called by Jni/phNxpEse_open during the
 *         initialization of the ESE. It initializes protocol stack instance
 * variables.
 *
 * \param[in]    initParams - init parameters to be set while calling
 * phNxpEse_init
 *
 * \retval This function return ESESTATUS_SUCCESS (0) in case of success
 *         In case of failure returns other failure value.
 *
 */
ESESTATUS phNxpEse_init(phNxpEse_initParams initParams);

#ifdef NXP_BOOTTIME_UPDATE
/**
 * \ingroup spi_libese
 *
 * \brief  This function is used to communicate from nfc-hal to ese-hal
 *
 * \param[in]     ioctlType - ioctl cmd
 *\param[out]    p_data - value read out
 *
 * \retval This function return ESESTATUS_SUCCESS (0) in case of success
 *         In case of failure returns other failure value.
 *
 */
ESESTATUS phNxpEse_spiIoctl(uint64_t ioctlType, void* p_data);
#endif

/**
 * \ingroup spi_libese
 *
 * \brief  This function is called by hal interface api before any
 *         communication. It sets the end point variables
 *
 *  \param[in]     uEndPoint - select the end point type (  END_POINT_ESE = 0,
 * END_POINT_eUICC =1 ).
 *
 * \retval This function return ESESTATUS_SUCCESS (0) in case of success
 *         In case of failure returns other failure value.
 *
 */
ESESTATUS phNxpEse_SetEndPoint_Cntxt(uint8_t uEndPoint);

/**
 * \ingroup spi_libese
 *
 * \brief  This function is called by hal interface api before any
 *         communication. It resets the end point variables
 *
 * \param[in]     uEndPoint - select the end point type (  END_POINT_ESE = 0,
 * END_POINT_eUICC =1 ).
 *
 * \retval This function return ESESTATUS_SUCCESS (0) in case of success
 *         In case of failure returns other failure value.
 *
 */
ESESTATUS phNxpEse_ResetEndPoint_Cntxt(uint8_t uEndPoint);

/**
 * \ingroup spi_libese
 * \brief This function is called by Jni during the
 *        initialization of the ESE. It opens the physical connection
 *        with ESE () and initializes the protocol stack
 *
 * \param[in]     initParams - Initialize with init mode ( normal/osu) and media
 * type(SPI- legacy/ APDU type).
 *
 * \retval ESESTATUS_SUCCESS On Success ESESTATUS_SUCCESS else proper error code
 *
 */
ESESTATUS phNxpEse_open(phNxpEse_initParams initParams);

/**
 * \ingroup spi_libese
 * \brief This function prepares the C-APDU, send to ESE and then receives the
 *response from ESE,
 *         decode it and returns data.
 *
 * \param[in]       pCmd: Command to ESE
 * \param[out]     pRsp: Response from ESE (Returned data to be freed
 *after copying)
 *
 * \retval ESESTATUS_SUCCESS On Success ESESTATUS_SUCCESS else proper error code
 *
 */

ESESTATUS phNxpEse_Transceive(phNxpEse_data* pCmd, phNxpEse_data* pRsp);

/**
 * \ingroup spi_libese
 *
 * \brief  This function is called by Jni/phNxpEse_close during the
 *         de-initialization of the ESE. It de-initializes protocol stack
 *instance variables
 *
 * \retval This function return ESESTATUS_SUCCESS (0) in case of success
 *         In case of failure returns other failure value.
 *
 */
ESESTATUS phNxpEse_deInit(void);

/**
 * \ingroup spi_libese
 * \brief This function close the ESE interface and free all resources.
 *
 *
 * \retval ESESTATUS_SUCCESS Always return ESESTATUS_SUCCESS (0).
 *
 */

ESESTATUS phNxpEse_close(ESESTATUS deInitStatus = ESESTATUS_SUCCESS);

/**
 * \ingroup spi_libese
 * \brief This function reset the ESE interface and free all
 *
 *
 * \retval ESESTATUS_SUCCESS Always return ESESTATUS_SUCCESS (0).
 *
 */
ESESTATUS phNxpEse_reset(void);

/**
 * \ingroup spi_libese
 * \brief This function reset the ESE
 *
 *
 * \retval ESESTATUS_SUCCESS Always return ESESTATUS_SUCCESS (0).
 *
 */
ESESTATUS phNxpEse_resetJcopUpdate(void);

/**
 * \ingroup spi_libese
 * \brief This function reset the P73 through ISO RST pin
 *
 *
 * \retval ESESTATUS_SUCCESS Always return ESESTATUS_SUCCESS (0).
 *
 */
ESESTATUS phNxpEse_chipReset(void);

/**
 * \ingroup spi_libese
 * \brief This function is used to set IFSC size
 *
 * \param[in]       IFS_Size
 *
 * \retval ESESTATUS_SUCCESS Always return ESESTATUS_SUCCESS (0).
 *
 */
ESESTATUS phNxpEse_setIfs(uint16_t IFS_Size);

/**
 * \ingroup spi_libese
 * \brief This function is used to get the ATR data from ESE
 *
 * \param[out]      pATR
 *
 * \retval ESESTATUS_SUCCESS Always return ESESTATUS_SUCCESS (0).
 *
 */
ESESTATUS phNxpEse_getAtr(phNxpEse_data* pATR);

/**
 * \ingroup spi_libese
 * \brief This function  suspends execution of the calling thread for
 *           (at least) usec microseconds
 *
 * \param[in]       usec
 *
 * \retval ESESTATUS_SUCCESS Always return ESESTATUS_SUCCESS (0).
 *
 */
ESESTATUS phNxpEse_Sleep(uint32_t usec);

/**
 * \ingroup spi_libese
 * \brief This function updates destination buffer with val
 *                 data in len size
 *
 * \param[in]    buff                - Array to be updated
 * \param[in]    val                 - value to be updated
 * \param[in]    len                 - length of array to be updated
 *
 * \retval   void
 *
 */
void* phNxpEse_memset(void* buff, int val, size_t len);

/**
 * \ingroup spi_libese
 * \brief This function copies source buffer to  destination buffer
 *                 data in len size
 *
 * \param[in]    dest                - Destination array to be updated
 * \param[in]    src                 - Source array to be updated
 * \param[in]    len                 - length of array to be updated
 *
 * \retval   void
 *
 */
void* phNxpEse_memcpy(void* dest, const void* src, size_t len);

/**
 * \ingroup spi_libese
 * \brief This function  suspends allocate memory
 *
 * \param[in]       size
 *
 * \retval allocated memory.
 *
 */
void* phNxpEse_memalloc(uint32_t size);

/**
 * \ingroup spi_libese
 * \brief This is utility function for runtime heap memory allocation
 *
 *\param[in]     dataType          - data type
 * \param[in]    size                 - number of bytes to be allocated
 *
 * \retval   void
 *
 */
void* phNxpEse_calloc(size_t dataType, size_t size);

/**
 * \ingroup spi_libese
 * \brief This is utility function for freeeing heap memory allocated
 *
 * \param[in]    ptr                 - Address pointer to previous allocation
 *
 * \retval   void
 *
 */
void phNxpEse_free(void* ptr);

/**
 * \ingroup spi_libese
 * \brief This function power recycles the ESE
 *        (using prop. FW command) by talking to NFC HAL
 *
 *        Note:
 *        After cold reset, phNxpEse_init need to be called to
 *        reset the host AP T=1 stack parameters
 *
 *
 * \retval ESESTATUS_SUCCESS Always return ESESTATUS_SUCCESS (0).
 *
 */
ESESTATUS phNxpEse_coldReset(void);

/**
 * \ingroup spi_libese
 * \brief  This function notifies SE hal service if it registers
 *
 * \param[out]       state - WTX_ONGOIGN/WTX_END
 *
 * \retval void.
 *
 */
void phNxpEse_NotifySEWtxRequest(phNxpEse_wtxState state);

/**
 * \ingroup ISO7816-3_protocol_lib
 * \brief This function is used to get OS mode(JCOP/OSU)
 *
 * \retval OS mode(JCOP/OSU).
 *
 */
phNxpEseProto7816_OsType_t phNxpEse_GetOsMode(void);

/**
 * \ingroup spi_libese
 * \brief This function enable/disable resetprotection
 *
 * \param[in]    flag   - indicated enable or disable resetprotection.
 *
 * \retval ESESTATUS_SUCCESS Always return ESESTATUS_SUCCESS (0).
 *
 */
ESESTATUS phNxpEse_doResetProtection(bool flag);

/**
 * \ingroup spi_libese
 * \brief This function is used to set the wtx count limit
 *
 * \param[in]    wtxCount     - value to set for wtx count limit
 *
 * \retval void.
 *
 */
void phNxpEse_setWtxCountLimit(unsigned long int wtxCount);

/**
 * \ingroup spi_libese
 * \brief This function returns whether priority channel enabled or
 *  not
 *
 * \param[in] void
 *
 * \retval uint8_t - Priority Access enabled(1)/disabled(0).
 *
 */
bool phNxpEse_isPriorityAccessEnabled(void);

/** @} */
#endif /* _PHNXPSPILIB_API_H_ */
