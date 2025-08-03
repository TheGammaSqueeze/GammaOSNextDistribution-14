#
# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#variant makefile for neonkey


ifneq ($(PLATFORM),stm32)
        $(error "NEONKEY variant cannot be build on a platform that is not stm32")
endif

FLAGS += -DPLATFORM_HW_TYPE=0x4E65   #'Ne' ->neonkey
FLAGS += -DDEBUG_SWD

FLAGS += -I$(VARIANT_PATH)/inc

#board configuration shims
SRCS_os += $(VARIANT_PATH)/src/os/i2c.c
SRCS_os += $(VARIANT_PATH)/src/os/spi.c
SRCS_os += $(VARIANT_PATH)/src/os/led.c
#keys

SRCS_os += \
    os/algos/calibration/magnetometer/mag_cal/mag_cal.c \
    os/algos/calibration/diversity_checker/diversity_checker.c \
    os/algos/common/math/kasa.c \
    os/algos/common/math/mat.c \
    os/algos/common/math/quat.c \
    os/algos/common/math/vec.c \
    os/algos/fusion.c \
    os/algos/time_sync.c

#drivers
# Bosch BMI160 accel and gyro, BMM150 mag drivers
FLAGS += -DUSE_BMM150 -DMAG_SLAVE_PRESENT
SRCS_os += \
    os/drivers/bosch_bmi160/bosch_bmi160.c \
    os/drivers/bosch_bmi160/bosch_bmm150_slave.c

# ROHM ALS/prox
SRCS_os += os/drivers/rohm_rpr0521/rohm_rpr0521.c

# Leds-Gpio
SRCS_os += os/drivers/leds/leds_gpio.c os/drivers/leds/leds_lp3943.c

# Humidity/Temp
SRCS_os += os/drivers/si_si7034/si7034a10.c

# Orientation sensor driver
SRCS_os += os/drivers/orientation/orientation.c

# Window orientation sensor driver
SRCS_os += os/drivers/window_orientation/window_orientation.c

# Bosch BMP280 Barometer/Temperature
SRCS_os += os/drivers/bosch_bmp280/bosch_bmp280.c

# Hall effect sensor driver
SRCS_os += os/drivers/hall/hall.c

# Tilt detection
SRCS_os += os/drivers/tilt_detection/tilt_detection.c

$(info included NEONKEY variant)
