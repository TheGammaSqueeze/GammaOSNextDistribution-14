#
# Copyright (C) 2017 STMicroelectronics
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

#variant makefile for argonkey

ifneq ($(PLATFORM),stm32)
        $(error "ARGONKEY variant cannot be build on a platform that is not stm32")
endif

FLAGS += -DPLATFORM_HW_TYPE=0x4172   #'Ar' -> argonkey
FLAGS += -DDEBUG_SWD

FLAGS += -I$(VARIANT_PATH)/inc

#board configuration shims
SRCS_os += $(VARIANT_PATH)/src/os/i2c.c
SRCS_os += $(VARIANT_PATH)/src/os/led.c
SRCS_os += $(VARIANT_PATH)/src/os/spi.c
#keys

SRCS_os += \
    os/algos/calibration/accelerometer/accel_cal.c \
    os/algos/calibration/gyroscope/gyro_cal.c \
    os/algos/calibration/gyroscope/gyro_stillness_detect.c \
    os/algos/calibration/magnetometer/mag_cal/mag_cal.c \
    os/algos/calibration/diversity_checker/diversity_checker.c \
    os/algos/calibration/over_temp/over_temp_cal.c \
    os/algos/common/math/kasa.c \
    os/algos/common/math/mat.c \
    os/algos/common/math/quat.c \
    os/algos/common/math/vec.c \
    os/algos/fusion.c \
    os/algos/time_sync.c

#drivers
# LSM6DSL accel+gyro with mag slave
SRCS_os += os/drivers/st_lsm6dsm/st_lsm6dsm.c

# Orientation sensor driver
SRCS_os += os/drivers/orientation/orientation.c

# Window orientation sensor driver
SRCS_os += os/drivers/window_orientation/window_orientation.c

# LPS22HB baro+temp
SRCS_os += os/drivers/st_lps22hb/lps22hb.c

# Hall effect sensor driver
SRCS_os += os/drivers/hall/hall.c

# HTS221 humidity
SRCS_os += os/drivers/st_hts221/hts221.c

# ISL29034 ALS
SRCS_os += os/drivers/intersil_isl29034/isl29034.c

# LED on GPIO
SRCS_os += os/drivers/leds/leds_gpio.c

# LP3943 LED controller
SRCS_os += os/drivers/leds/leds_lp3943.c

# Tilt detection
SRCS_os += os/drivers/tilt_detection/tilt_detection.c

# Digital microphone
# FIXME

# VL53L0X Proximity
# FIXME

# Button
# FIXME

$(info included ARGONKEY variant)
