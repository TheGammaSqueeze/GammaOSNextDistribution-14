/* Copyright (C) 2008 The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
** http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#pragma once

#include "host-common/hw-config.h"
#include "host-common/hw-lcd.h"
#include "aemu/base/c_header.h"

typedef struct CIniFile CIniFile;

ANDROID_BEGIN_HEADER

/* Set all default values, based on the target API level */
void androidHwConfig_init( AndroidHwConfig*  hwConfig,
                           int               apiLevel );

/* reads a hardware configuration file from disk.
 * returns -1 if the file could not be read, or 0 in case of success.
 *
 * note that default values are written to hwConfig if the configuration
 * file doesn't have the corresponding hardware properties.
 */
int androidHwConfig_read(AndroidHwConfig* hwConfig, CIniFile* configFile);

/* Write a hardware configuration to a config file object.
 * Returns 0 in case of success. Note that any value that is set to the
 * default will not bet written.
 */
int androidHwConfig_write(AndroidHwConfig* hwConfig, CIniFile* configFile);

/* Finalize a given hardware configuration */
void androidHwConfig_done( AndroidHwConfig* config );

/* Checks if screen doesn't support touch, or multi-touch */
int  androidHwConfig_isScreenNoTouch( AndroidHwConfig* config );
/* Checks if screen supports touch (but not multi-touch). */
int  androidHwConfig_isScreenTouch( AndroidHwConfig* config );
/* Checks if screen supports multi-touch. */
int  androidHwConfig_isScreenMultiTouch( AndroidHwConfig* config );

/* Returns the Screen Size */
hwLcd_screenSize_t androidHwConfig_getScreenSize(AndroidHwConfig* config);

/* Returns CDD defined minimum heap size in MB */
int androidHwConfig_getMinVmHeapSize(AndroidHwConfig* config, int apiLevel);

// Return an integer indicating if the kernel requires a new device
// naming scheme. More specifically:
//  -1 -> don't know, caller will need to auto-detect.
//   0 -> legacy device naming
//   1 -> new device naming.
//
// The new device naming was allegedly introduced in Linux 3.10 and
// replaces /dev/ttyS<num with /dev/ttyGF<num>. Also see related
// declarations in android/kernel/kernel_utils.h
int androidHwConfig_getKernelDeviceNaming( AndroidHwConfig* config );

// Return an integer indicating is the kernel supports YAFFS2 partition
// images. More specifically:
//  -1 -> don't know, caller will need to auto-detect.
//   0 -> does not support YAFFS2 partitions.
//   1 -> does support YAFFS2 partitions.
int androidHwConfig_getKernelYaffs2Support( AndroidHwConfig* config );

// Return the kernel device prefix for serial ports, depending on
// kernel.newDeviceNaming.
const char* androidHwConfig_getKernelSerialPrefix( AndroidHwConfig* config );

// Remove all default values from the |source| ini file and write only
// non-defaulted settings into |target|
void androidHwConfig_stripDefaults(CIniFile* source, CIniFile* target);

// Checks if the hw config has the virtual scene camera enabled.
int androidHwConfig_hasVirtualSceneCamera(AndroidHwConfig* config);

// Checks if the hw config has the video playback camera enabled.
int androidHwConfig_hasVideoPlaybackCamera(AndroidHwConfig* config);


int androidHwConfig_hasVideoPlaybackFrontCamera(AndroidHwConfig* config);

int androidHwConfig_hasVideoPlaybackBackCamera(AndroidHwConfig* config);

ANDROID_END_HEADER
