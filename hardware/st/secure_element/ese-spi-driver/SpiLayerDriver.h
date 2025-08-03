/******************************************************************************
 *
 *  Copyright (C) 2018 ST Microelectronics S.A.
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
 *
 ******************************************************************************/
#ifndef SPILAYERDRIVER_H_
#define SPILAYERDRIVER_H_

#include <fcntl.h>
#include <linux/spi/spidev.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include "SpiLayerInterface.h"

#define ATP_FILE_PATH "/data/vendor/ese/atp.bin"

#define MODE_TX 0
#define MODE_RX 1
#define MIN_TIME_BETWEEN_MODE_SWITCH 1
#define ST54J_SE_MAGIC 0xE5
#define ST54J_SE_PULSE_RESET _IOR(ST54J_SE_MAGIC, 0x01, unsigned int)

/**
 * Initialize internal variables.
 *
 * @return  null
 */

void SpiLayerDriver_init(SpiDriver_config_t* tSpiDriver);

/**
 * Open the spi device driver.
 *
 * @return  -1 if an error occurred, file descriptor if success.
 */
int SpiLayerDriver_open(char *spiDevPath);

/**
 * Close the spi device driver.
 *
 */
void SpiLayerDriver_close();

/**
 * Reads bytesToRead bytes from the SPI interface.
 *
 * @param rxBuffer The buffer where the received bytes are stored.
 * @param bytesToRead The expected number of bytes to be read.
 *
 * @return The amount of bytes read from the slave, -1 if something failed.
 */
int SpiLayerDriver_read(uint8_t *rxBuffer, unsigned int bytesToRead);

/**
 * Write txBufferLength bytes to the SPI interface.
 *
 * @param txBuffer The buffer where the bytes to write are placed.
 * @param txBufferLength The number of bytes to be written.
 *
 * @return The amount of bytes written to the slave, -1 if something failed.
 */
int SpiLayerDriver_write(uint8_t *writeBuffer, unsigned int bytesToWrite);

/**
 * Send a Reset pulse to the eSE.
 *
 * @return 0 if success, -1 if something failed.
 */
int SpiLayerDriver_reset();

#endif /* SPILAYERDRIVER_H_ */
