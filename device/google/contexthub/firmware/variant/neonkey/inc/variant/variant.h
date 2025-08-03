/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef _VARIANT_NEONKEY_H_
#define _VARIANT_NEONKEY_H_

#ifdef __cplusplus
extern "C" {
#endif

#define VARIANT_VER                 0x00000000

//we don't have LSE in neonkey
#define RTC_CLK                     RTC_CLK_LSI

//spi bus for comms
#define PLATFORM_HOST_INTF_SPI_BUS  0

#define SH_INT_WAKEUP               GPIO_PB(9)
#define SH_EXTI_WAKEUP_IRQ          EXTI9_5_IRQn
#define AP_INT_WAKEUP               GPIO_PB(5)
#undef AP_INT_NONWAKEUP

//enable logging through nanohub driver
#define DEBUG_LOG_EVT               0x3B474F4C

#define DEBUG_UART_UNITNO           1
#define DEBUG_UART_GPIO_TX          GPIO_PA(9)
#define DEBUG_UART_GPIO_RX          GPIO_PA(10)

#define BMP280_I2C_BUS_ID           1
#define PROX_I2C_BUS_ID             1
#define LP3943_I2C_BUS_ID           2
#define SI7034A10_I2C_BUS_ID        1

#define BMI160_I2C_BUS_ID           1
#define BMI160_INT1_PIN             GPIO_PA(0)
#define BMI160_INT1_IRQ             EXTI0_IRQn
#define BMI160_INT2_PIN             GPIO_PA(1)
#define BMI160_INT2_IRQ             EXTI1_IRQn

#define HALL_PIN                    GPIO_PB(8)
#define HALL_IRQ                    EXTI9_5_IRQn

#define PROX_INT_PIN                GPIO_PA(15)
#define PROX_IRQ                    EXTI15_10_IRQn

#define BMI160_TO_ANDROID_COORDINATE(x, y, z)   \
    do {                                        \
        float xi = x, yi = y, zi = z;           \
        x = xi; y = yi; z = zi;                 \
    } while (0)

#define BMM150_TO_ANDROID_COORDINATE(x, y, z)   \
    do {                                        \
        float xi = x, yi = y, zi = z;           \
        x = xi; y = -yi; z = -zi;               \
    } while (0)

//define tap sensor threshold
#define TAP_THRESHOLD               0x01

//define Accelerometer fast compensation config
#define ACC_FOC_CONFIG              0x3d

#ifdef __cplusplus
}
#endif

#endif
