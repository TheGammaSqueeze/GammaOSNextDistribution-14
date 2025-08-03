/*
 * Copyright (C) 2017 STMicroelectronics
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

#ifndef _VARIANT_ARGONKEY_H_
#define _VARIANT_ARGONKEY_H_

#ifdef __cplusplus
extern "C" {
#endif

#define PLATFORM_HW_TYPE            0x4172   // 'Ar' -> argonkey

#define VARIANT_VER                 0x00000000

//we have LSE in argonkey
#define RTC_CLK                     RTC_CLK_LSE
#define RTC_PREDIV_A                0UL
#define RTC_PREDIV_S                32767UL

//spi bus for comms
#define PLATFORM_HOST_INTF_SPI_BUS  0

#define SH_INT_WAKEUP               GPIO_PA(0)
#define SH_EXTI_WAKEUP_IRQ          EXTI0_IRQn
#define AP_INT_WAKEUP               GPIO_PA(1)
/*
 * Allocating a GPIO for non-wakeup host interrupt is optional in CHRE.
 * ArgonKey implements this feature and enables it here. If you want to use
 * the GPIO_PA(3) for other purposes, you should comment the line below and
 * the corresponding line in the host's devicetree.
 */
#define AP_INT_NONWAKEUP            GPIO_PA(3)

#define DEBUG_UART_UNITNO           1
#define DEBUG_UART_GPIO_TX          GPIO_PA(9)
#define DEBUG_UART_GPIO_RX          GPIO_PA(10)

#define DEBUG_LOG_EVT               0x3B474F4C

#define BL_FLASH_TABLE                                                  \
{                                                                       \
    { (uint8_t *)(&BL),                      0x04000, BL_FLASH_BL     },\
    { (uint8_t *)(__eedata_start),           0x04000, BL_FLASH_EEDATA },\
    { (uint8_t *)(__eedata_start + 0x04000), 0x04000, BL_FLASH_EEDATA },\
    { (uint8_t *)(__code_start),             0x04000, BL_FLASH_KERNEL },\
    { (uint8_t *)(__code_start + 0x04000),   0x10000, BL_FLASH_KERNEL },\
    { (uint8_t *)(__code_start + 0x14000),   0x20000, BL_FLASH_KERNEL },\
    { (uint8_t *)(__shared_start),           0x20000, BL_FLASH_SHARED },\
    { (uint8_t *)(__shared_start + 0x20000), 0x20000, BL_FLASH_SHARED },\
    { (uint8_t *)(__shared_start + 0x40000), 0x20000, BL_FLASH_SHARED },\
    { (uint8_t *)(__shared_start + 0x60000), 0x20000, BL_FLASH_SHARED },\
    { (uint8_t *)(__shared_start + 0x80000), 0x20000, BL_FLASH_SHARED },\
    { (uint8_t *)(__shared_start + 0xA0000), 0x20000, BL_FLASH_SHARED },\
}

/* ------------------------------------------------------------------------ */
/* sensor accel+gyro lsm6dsl (compatible lsm6dsm) on spi */

// #define LSM6DSM_DBG_ENABLED                 1

#define LSM6DSM_SPI_SLAVE_BUS_ID               1
#define LSM6DSM_SPI_SLAVE_FREQUENCY_HZ         8000000
#define LSM6DSM_SPI_SLAVE_CS_GPIO              GPIO_PB(12)

#define LSM6DSM_INT_IRQ                        EXTI1_IRQn
#define LSM6DSM_INT1_GPIO                      GPIO_PB(1)

/* i2c slave sensor mag lis2mdl (lsm303agr compatible) */
#define LSM6DSM_I2C_MASTER_LSM303AGR           1

#define LSM6DSM_ACCEL_GYRO_ROT_MATRIX          -1, 0, 0, 0, -1, 0, 0, 0, 1
#define LSM6DSM_MAGN_ROT_MATRIX                -1, 0, 0, 0, 1, 0, 0, 0, 1

#define LSM6DSM_ACCEL_CALIB_ENABLED            1
#define LSM6DSM_GYRO_CALIB_ENABLED             1
#define LSM6DSM_MAGN_CALIB_ENABLED             1
#define LSM6DSM_OVERTEMP_CALIB_ENABLED         1

/* ------------------------------------------------------------------------ */
/* sensor baro+temp lps22hb on i2c */
/* unused PIN GPIO_PA(15), INT EXTI15_10_IRQn */

// #define LPS22HB_DBG_ENABLED                 1

#define LPS22HB_I2C_BUS_ID                     1
#define LPS22HB_I2C_SPEED                      400000
#define LPS22HB_I2C_ADDR                       0x5D

/* ------------------------------------------------------------------------ */
/* sensor humidity hts221 on i2c */
/* unused PIN GPIO_PA(2), INT EXTI2_IRQn is used as button */

// #define HTS221_DBG_ENABLED                  1

#define HTS221_I2C_BUS_ID                      1
#define HTS221_I2C_SPEED                       400000
#define HTS221_I2C_ADDR                        0x5F

/* ------------------------------------------------------------------------ */
/* sensor als isl29034 on i2c */

#define ISL29034_I2C_BUS_ID                    1
#define ISL29034_I2C_SPEED                     400000
#define ISL29034_I2C_ADDR                      0x44

/* ------------------------------------------------------------------------ */
/* sensor proximity vl53l0x on i2c */

// FIXME
// I2C_BUS_ID 1
// I2C_SPEED  400000
// I2C_ADDR   0x29
// PIN        GPIO_PB(3)
// IRQ        EXTI3_IRQn

/* ------------------------------------------------------------------------ */
/* sensor hall mrms501a on gpio */

#define HALL_PIN                               GPIO_PB(5)
#define HALL_IRQ                               EXTI9_5_IRQn

/* ------------------------------------------------------------------------ */
/* microphone on i2s */

// FIXME
// I2S_BUS_ID 4

/* ------------------------------------------------------------------------ */
/* led controller lp3943 on i2c */

#define LP3943_I2C_BUS_ID                      2
#define LP3943_I2C_SPEED                       200000
#define LP3943_I2C_ADDR                        0x60

/* ------------------------------------------------------------------------ */
/* button on gpio */

// FIXME
// PIN GPIO_PA(2)
// INT EXTI2_IRQn

#ifdef __cplusplus
}
#endif

#endif
