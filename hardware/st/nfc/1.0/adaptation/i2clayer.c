/** ----------------------------------------------------------------------
 *
 * Copyright (C) 2013 ST Microelectronics S.A.
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
#define _GNU_SOURCE
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <linux/input.h> /* not required for all builds */
#include <poll.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <pthread.h>

#include "halcore.h"
#include "halcore_private.h"
#include "android_logmsg.h"

#define ST21NFC_MAGIC 0xEA

#define ST21NFC_GET_WAKEUP _IOR(ST21NFC_MAGIC, 0x01, unsigned int)
#define ST21NFC_PULSE_RESET _IOR(ST21NFC_MAGIC, 0x02, unsigned int)
#define ST21NFC_SET_POLARITY_RISING _IOR(ST21NFC_MAGIC, 0x03, unsigned int)
#define ST21NFC_SET_POLARITY_FALLING _IOR(ST21NFC_MAGIC, 0x04, unsigned int)
#define ST21NFC_SET_POLARITY_HIGH _IOR(ST21NFC_MAGIC, 0x05, unsigned int)
#define ST21NFC_SET_POLARITY_LOW _IOR(ST21NFC_MAGIC, 0x06, unsigned int)

#define LINUX_DBGBUFFER_SIZE 300

static int fidI2c = 0;
static int cmdPipe[2] = {0, 0};

static struct pollfd event_table[2];
static pthread_t threadHandle = (pthread_t)NULL;
pthread_mutex_t i2ctransport_mtx = PTHREAD_MUTEX_INITIALIZER;

/**************************************************************************************************
 *
 *                                      Private API Declaration
 *
 **************************************************************************************************/

static int i2cSetPolarity(int fid, bool low, bool edge);
static int i2cResetPulse(int fid);
static int i2cRead(int fid, uint8_t* pvBuffer, int length);
static int i2cGetGPIOState(int fid);
static int i2cWrite(int fd, const uint8_t* pvBuffer, int length);

/**************************************************************************************************
 *
 *                                      Public API Entry-Points
 *
 **************************************************************************************************/

/**
 * Worker thread for I2C data processing.
 * On exit of this thread, destroy the HAL thread instance.
 * @param arg  Handle of the HAL layer
 */
static void* I2cWorkerThread(void* arg)
{
    bool closeThread = false;
    HALHANDLE hHAL = (HALHANDLE)arg;
    STLOG_HAL_V("echo thread started...\n");
    bool readOk= false;

    do {
        event_table[0].fd = fidI2c;
        event_table[0].events = POLLIN;
        event_table[0].revents = 0;

        event_table[1].fd = cmdPipe[0];
        event_table[1].events = POLLIN;
        event_table[1].revents = 0;

        STLOG_HAL_D("echo thread go to sleep...\n");

        int poll_status = poll(event_table, 2, -1);

        if (-1 == poll_status) {
            STLOG_HAL_E("error in poll call\n");
            return false;
        }

        if (event_table[0].revents & POLLIN) {
            STLOG_HAL_D("echo thread wakeup from chip...\n");

            uint8_t buffer[300];

            do {
                // load first four bytes:
                int bytesRead = i2cRead(fidI2c, buffer, 3);

                if (bytesRead == 3) {
                    if ((buffer[0] != 0x7E) && (buffer[1] != 0x7E)) {
                        readOk = true;
                    } else {
                        if (buffer[1] != 0x7E) {
                            STLOG_HAL_W("Idle data: 2nd byte is 0x%02x\n, reading next 2 bytes",
                                  buffer[1]);
                            buffer[0] = buffer[1];
                            buffer[1] = buffer[2];
                            bytesRead = i2cRead(fidI2c, buffer + 2, 1);
                            if (bytesRead == 1) {
                                readOk = true;
                            }
                        } else if (buffer[2] != 0x7E) {
                            STLOG_HAL_W("Idle data: 3rd byte is 0x%02x\n, reading next  byte",
                                  buffer[2]);
                            buffer[0] = buffer[2];
                            bytesRead = i2cRead(fidI2c, buffer + 1, 2);
                            if (bytesRead == 2) {
                                readOk = true;
                            }
                        } else {
                            STLOG_HAL_W("received idle data\n");
                        }
                    }

                    if (readOk == true) {
                        int remaining = buffer[2];

                        // read and pass to HALCore
                        bytesRead = i2cRead(fidI2c, buffer + 3, remaining);
                        if (bytesRead == remaining) {
                            DispHal("RX DATA", buffer, 3 + bytesRead);
                            HalSendUpstream(hHAL, buffer, 3 + bytesRead);
                        } else {
                            readOk = false;
                            STLOG_HAL_E("! didn't read expected bytes from i2c\n");
                        }
                    }

                } else {
                    STLOG_HAL_E("! didn't read 3 requested bytes from i2c\n");
                }

                readOk = false;
                memset(buffer, 0xca, sizeof(buffer));

                /* read while we have data available */
            } while (i2cGetGPIOState(fidI2c) == 1);
        }

        if (event_table[1].revents & POLLIN) {
            STLOG_HAL_V("thread received command.. \n");

            char cmd = 0;
            read(cmdPipe[0], &cmd, 1);

            switch (cmd) {
                case 'X':
                    STLOG_HAL_D("received close command\n");
                    closeThread = true;
                    break;

                case 'W': {
                    size_t length;
                    uint8_t buffer[MAX_BUFFER_SIZE];
                    STLOG_HAL_V("received write command\n");
                    read(cmdPipe[0], &length, sizeof(length));
                    if (length <= MAX_BUFFER_SIZE)
                      {
                        read(cmdPipe[0], buffer, length);
                        i2cWrite(fidI2c, buffer, length);
                      }
                    else {
                        STLOG_HAL_E("! received bigger data than expected!! Data not transmitted to NFCC \n");
                        size_t bytes_read = 1;
                        // Read all the data to empty but do not use it as not expected
                        while((bytes_read > 0) && (length > 0))
                          {
                            bytes_read = read(cmdPipe[0],buffer,MAX_BUFFER_SIZE);
                            length = length - bytes_read;
                          }
                    }
                }
                break;
            }
        }

    } while (!closeThread);

    close(fidI2c);
    close(cmdPipe[0]);
    close(cmdPipe[1]);

    HalDestroy(hHAL);
    STLOG_HAL_D("thread exit\n");
    return 0;
}

/**
 * Put command into queue for worker thread to process it.
 * @param x Command 'X' to close I2C layer or 'W' to write data down to I2C
 * layer followed by data frame
 * @param len Size of command or data
 * @return
 */
int I2cWriteCmd(const uint8_t* x, size_t len)
{
    return write(cmdPipe[1], x, len);
}

/**
 * Initialize the I2C layer.
 * @param dev NFC NCI device context, NFC callbacks for control/data, HAL handle
 * @param callb HAL Core callback upon reception on I2C
 * @param pHandle HAL context handle
 */
bool I2cOpenLayer(void* dev, HAL_CALLBACK callb, HALHANDLE* pHandle)
{
    uint32_t NoDbgFlag = HAL_FLAG_DEBUG;

      (void) pthread_mutex_lock(&i2ctransport_mtx);
    fidI2c = open("/dev/st21nfc", O_RDWR);
    if (fidI2c < 0) {
        STLOG_HAL_W("unable to open /dev/st21nfc\n");
        return false;
    }

    i2cSetPolarity(fidI2c, false, true);
    i2cResetPulse(fidI2c);

    if ((pipe(cmdPipe) == -1)) {
        STLOG_HAL_W("unable to open cmdpipe\n");
        return false;
    }

    *pHandle = HalCreate(dev, callb, NoDbgFlag);

    if (!*pHandle) {
        STLOG_HAL_E("failed to create NFC HAL Core \n");
        return false;
    }

      (void) pthread_mutex_unlock(&i2ctransport_mtx);

    return (pthread_create(&threadHandle, NULL, I2cWorkerThread, *pHandle) == 0);
}

/**
 * Terminates the I2C layer.
 */
void I2cCloseLayer()
{
    uint8_t cmd = 'X';
    int ret;
    ALOGD("%s: enter\n", __func__);

    (void)pthread_mutex_lock(&i2ctransport_mtx);

    if (threadHandle == (pthread_t)NULL)
        return;

    I2cWriteCmd(&cmd, sizeof(cmd));
    /* wait for terminate */
    ret = pthread_join(threadHandle,(void**)NULL);
    if (ret != 0) {
        ALOGE("%s: failed to wait for thread (%d)", __func__, ret);
    }
    threadHandle = (pthread_t)NULL;
    (void)pthread_mutex_unlock(&i2ctransport_mtx);
}
/**************************************************************************************************
 *
 *                                      Private API Definition
 *
 **************************************************************************************************/
/**
 * Call the st21nfc driver to adjust wake-up polarity.
 * @param fid File descriptor for NFC device
 * @param low Polarity (HIGH or LOW)
 * @param edge Polarity (RISING or FALLING)
 * @return Result of IOCTL system call (0 if ok)
 */
static int i2cSetPolarity(int fid, bool low, bool edge)
{
    int result;
    unsigned int io_code;

    if (low) {
        if (edge) {
            io_code = ST21NFC_SET_POLARITY_FALLING;
        } else {
            io_code = ST21NFC_SET_POLARITY_LOW;
        }

    } else {
        if (edge) {
            io_code = ST21NFC_SET_POLARITY_RISING;
        } else {
            io_code = ST21NFC_SET_POLARITY_HIGH;
        }
    }

    if (-1 == (result = ioctl(fid, io_code, NULL))) {
        result = -1;
    }

    return result;
} /* i2cSetPolarity*/

/**
 * Call the st21nfc driver to generate a 30ms pulse on RESET line.
 * @param fid File descriptor for NFC device
 * @return Result of IOCTL system call (0 if ok)
 */
static int i2cResetPulse(int fid)
{
    int result;

    if (-1 == (result = ioctl(fid, ST21NFC_PULSE_RESET, NULL))) {
        result = -1;
    }
    STLOG_HAL_D("! i2cResetPulse!!, result = %d", result);
    return result;
} /* i2cResetPulse*/

/**
 * Write data to st21nfc, on failure do max 3 retries.
 * @param fid File descriptor for NFC device
 * @param pvBuffer Data to write
 * @param length Data size
 * @return 0 if bytes written, -1 if error
 */
static int i2cWrite(int fid, const uint8_t* pvBuffer, int length)
{
    int retries = 0;
    int result = 0;

    while (retries < 3) {
        result = write(fid, pvBuffer, length);

        if (result < 0) {
            char msg[LINUX_DBGBUFFER_SIZE];

            strerror_r(errno, msg, LINUX_DBGBUFFER_SIZE);
            STLOG_HAL_W("! i2cWrite!!, errno is '%s'", msg);
            usleep(4000);
            retries++;
        } else if (result > 0) {
            result = 0;
            return result;
        } else {
            STLOG_HAL_W("write on i2c failed, retrying\n");
            usleep(4000);
            retries++;
        }
    }

    return -1;
} /* i2cWrite */

/**
 * Read data from st21nfc, on failure do max 3 retries.
 *
 * @param fid File descriptor for NFC device
 * @param pvBuffer Buffer where to copy read data
 * @param length Data size to read
 * @return Length of read data, -1 if error
 */
static int i2cRead(int fid, uint8_t* pvBuffer, int length)
{
    int retries = 0;
    int result = -1;

    while ((retries < 3) && (result < 0)) {
        result = read(fid, pvBuffer, length);

        if (result == -1) {
            int e = errno;
            if (e == EAGAIN) {
                /* File is nonblocking, and no data is available.
                 * This is not an error condition!
                 */
                result = 0;
                STLOG_HAL_D("## i2cRead - got EAGAIN. No data available. return 0 bytes");
            } else {
                /* unexpected result */
                char msg[LINUX_DBGBUFFER_SIZE];
                strerror_r(e, msg, LINUX_DBGBUFFER_SIZE);
                STLOG_HAL_W("## i2cRead returns %d errno %d (%s)", result, e, msg);
            }
        }

        if (result < 0) {
            if (retries < 3) {
                /* delays are different and increasing for the three retries. */
                static const uint8_t delayTab[] = {2, 3, 5};
                int delay = delayTab[retries];

                retries++;
                STLOG_HAL_W("## i2cRead retry %d/3 in %d milliseconds.", retries, delay);
                usleep(delay * 1000);
                continue;
            }
        }
    }
    return result;
} /* i2cRead */

/**
 * Get the activation status of wake-up pin from st21nfc.
 *  The decision 'active' depends on selected polarity.
 *  The decision is handled inside the driver(st21nfc).
 * @param fid File descriptor for NFC device
 * @return
 *  Result < 0:     Error condition
 *  Result > 0:     Pin active
 *  Result = 0:     Pin not active
 */
static int i2cGetGPIOState(int fid)
{
    int result;

    if (-1 == (result = ioctl(fid, ST21NFC_GET_WAKEUP, NULL))) {
        result = -1;
    }

    return result;
} /* i2cGetGPIOState */
