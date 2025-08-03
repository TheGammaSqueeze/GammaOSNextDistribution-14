/*
 * Copyright 2018 Google Inc. All Rights Reserved.
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

#define LOG_TAG "oslo_sound_model"

#include <ctype.h>
#include <cutils/properties.h>
#include <hardware/sound_trigger.h>
#include <inttypes.h>
#include <log/log.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <android/hardware/soundtrigger/2.0/ISoundTriggerHw.h>

#include "oslo_sound_model_control.h"
#include "sound_trigger_hw_iaxxx.h"

using android::sp;
using android::hardware::Return;
using android::hardware::soundtrigger::V2_0::ISoundTriggerHw;
using android::hardware::soundtrigger::V2_0::SoundModelHandle;
using android::hardware::soundtrigger::V2_0::SoundModelType;

#define OSLO_SOUND_MODEL_HANDLE_PROP "vendor.oslo.sm.hndl"

static SoundModelHandle osloSoundModelHandle = 0;

static bool strToUuid(const char* uuid_str, sound_trigger_uuid_t* uuid) {
  if (uuid_str == NULL) {
    ALOGI("Invalid str_to_uuid input.");
    return false;
  }

  int tmp[10];
  if (sscanf(uuid_str, "%08x-%04x-%04x-%04x-%02x%02x%02x%02x%02x%02x",
             tmp, tmp+1, tmp+2, tmp+3, tmp+4, tmp+5,
             tmp+6, tmp+7, tmp+8, tmp+9) < 10) {
    ALOGI("Invalid UUID, got: %s", uuid_str);
    return false;
  }
  uuid->timeLow = (unsigned int)tmp[0];
  uuid->timeMid = (unsigned short)tmp[1];
  uuid->timeHiAndVersion = (unsigned short)tmp[2];
  uuid->clockSeq = (unsigned short)tmp[3];
  uuid->node[0] = (unsigned char)tmp[4];
  uuid->node[1] = (unsigned char)tmp[5];
  uuid->node[2] = (unsigned char)tmp[6];
  uuid->node[3] = (unsigned char)tmp[7];
  uuid->node[4] = (unsigned char)tmp[8];
  uuid->node[5] = (unsigned char)tmp[9];

  return true;
}

/**
 * Loads oslo sound model via the SoundTrigger HAL HIDL service.
 *
 * @return true if oslo was enabled successfully, false otherwise.
 */
static bool osloLoadSoundModel(SoundModelHandle *hndl) {
  ALOGD("Loading oslo sound model");

  sound_trigger_uuid_t uuid;
  strToUuid(SENSOR_MANAGER_MODEL, &uuid);
  ISoundTriggerHw::SoundModel soundModel;
  soundModel.type = SoundModelType::GENERIC;
  soundModel.vendorUuid.timeLow = uuid.timeLow;
  soundModel.vendorUuid.timeMid = uuid.timeMid;
  soundModel.vendorUuid.versionAndTimeHigh = uuid.timeHiAndVersion;
  soundModel.vendorUuid.variantAndClockSeqHigh = uuid.clockSeq;

  memcpy(&soundModel.vendorUuid.node[0], &uuid.node[0], sizeof(uuid.node));
  soundModel.data.resize(1);  // Insert an unused byte to bypass HAL NULL checks.

  bool loaded = false;
  sp<ISoundTriggerHw> stHal = ISoundTriggerHw::getService();
  if (stHal == nullptr) {
    ALOGE("Failed to get ST HAL service for oslo load");
  } else {
    int32_t loadResult;
    Return<void> hidlResult = stHal->loadSoundModel(soundModel, NULL, 0,
        [&](int32_t retval, SoundModelHandle handle) {
            loadResult = retval;
            *hndl = handle;
        });

    if (hidlResult.isOk()) {
      if (loadResult == 0) {
        ALOGI("Loaded oslo %d", *hndl);
        loaded = true;
      } else {
        ALOGE("Failed to load oslo with %" PRId32, loadResult);
      }
    } else {
      ALOGE("Failed to load oslo due to hidl error %s",
            hidlResult.description().c_str());
    }
  }

  return loaded;
}

/**
 * Unloads oslo sound model via the SoundTrigger HAL HIDL service.
 */
static void osloUnloadSoundModel(SoundModelHandle hndl) {
  ALOGD("Unloading oslo sound model %d", hndl);

  sp<ISoundTriggerHw> stHal = ISoundTriggerHw::getService();
  if (stHal == nullptr) {
    ALOGE("Failed to get ST HAL service for oslo unload");
  } else {
    Return<int32_t> hidlResult = stHal->unloadSoundModel(hndl);

    if (hidlResult.isOk()) {
      if (hidlResult == 0) {
        ALOGI("Unloaded oslo");
      } else {
        ALOGE("Failed to unload oslo with %" PRId32, int32_t(hidlResult));
      }
    } else {
      ALOGE("Failed to unload oslo due to hidl error %s",
            hidlResult.description().c_str());
    }
  }
}

void osloSoundModelEnable(bool enable) {
  if (enable) {
    if (!osloLoadSoundModel(&osloSoundModelHandle)) {
      ALOGE("%s: Failed to load oslo sound model", __func__);
    }
  }
  else {
    if (osloSoundModelHandle == 0) {
      char prop[PROPERTY_VALUE_MAX];
      property_get(OSLO_SOUND_MODEL_HANDLE_PROP, prop, "0");
      osloSoundModelHandle = atoi(prop);
    }

    if (osloSoundModelHandle != 0) {
      osloUnloadSoundModel(osloSoundModelHandle);
      osloSoundModelHandle = 0;
    }
  }

  property_set(OSLO_SOUND_MODEL_HANDLE_PROP,
               std::to_string(osloSoundModelHandle).c_str());
}
