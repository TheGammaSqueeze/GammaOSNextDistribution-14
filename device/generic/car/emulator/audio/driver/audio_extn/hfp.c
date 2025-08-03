/*
 * Copyright (C) 2022 The Android Open Source Project
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
#ifdef HFP_ENABLED

#define LOG_TAG "audio_hw_hfp"
#define LOG_NDDEBUG 0

#include "audio_extn.h"
#include <cutils/log.h>
#include <cutils/str_parms.h>
#include <errno.h>
#include <stdlib.h>

#define AUDIO_PARAMETER_HFP_ENABLE "hfp_enable"
#define AUDIO_PARAMETER_HFP_SET_SAMPLING_RATE "hfp_set_sampling_rate"
#define AUDIO_PARAMETER_HFP_VOLUME "hfp_volume"
#define AUDIO_PARAMETER_HFP_VALUE_MAX 128

static int hfp_set_enable(struct generic_audio_device *adev, bool enable) {
  struct mixer_ctl *ctl;
  ALOGD("%s: enter enable : %d", __func__, enable);

  ctl = mixer_get_ctl_by_name(adev->mixer, AUDIO_PARAMETER_HFP_ENABLE);
  if (!ctl) {
    ALOGE("%s: Could not get mixer ctl for - %s", __func__,
          AUDIO_PARAMETER_HFP_ENABLE);
    return -EINVAL;
  }

  if (mixer_ctl_set_value(ctl, 0, enable) < 0) {
    ALOGE("%s: Couldn't set mixer ctrl for %s", __func__,
          AUDIO_PARAMETER_HFP_ENABLE);
    return -EINVAL;
  }

  adev->hfp_running = enable;
  ALOGD("%s: exit: status success", __func__);
  return 0;
}

static int hfp_set_sampling_rate(struct generic_audio_device *adev, int rate) {
  struct mixer_ctl *ctl;
  ALOGD("%s: enter rate = %d", __func__, rate);

  ctl =
      mixer_get_ctl_by_name(adev->mixer, AUDIO_PARAMETER_HFP_SET_SAMPLING_RATE);
  if (!ctl) {
    ALOGE("%s: Could not get mixer ctl for - %s", __func__,
          AUDIO_PARAMETER_HFP_SET_SAMPLING_RATE);
    return -EINVAL;
  }

  if (mixer_ctl_set_value(ctl, 0, rate) < 0) {
    ALOGE("%s: Couldn't set mixer ctrl for %s", __func__,
          AUDIO_PARAMETER_HFP_SET_SAMPLING_RATE);
    return -EINVAL;
  }

  ALOGD("%s: exit: status success", __func__);
  return 0;
}

static int hfp_set_volume(struct generic_audio_device *adev, int vol) {
  struct mixer_ctl *ctl;
  ALOGD("%s: enter vol = %d", __func__, vol);

  ctl = mixer_get_ctl_by_name(adev->mixer, AUDIO_PARAMETER_HFP_VOLUME);
  if (!ctl) {
    ALOGE("%s: Could not get mixer ctl for - %s", __func__,
          AUDIO_PARAMETER_HFP_VOLUME);
    return -EINVAL;
  }

  if (mixer_ctl_set_value(ctl, 0, vol) < 0) {
    ALOGE("%s: Couldn't set mixer ctrl for %s", __func__,
          AUDIO_PARAMETER_HFP_VOLUME);
    return -EINVAL;
  }

  ALOGD("%s: exit: status success", __func__);
  return 0;
}

int audio_extn_hfp_set_parameters(struct generic_audio_device *adev,
                                  struct str_parms *parms) {
  int ret = 0, rate, vol;
  char value[AUDIO_PARAMETER_HFP_VALUE_MAX] = {0};

  ret = str_parms_get_str(parms, AUDIO_PARAMETER_HFP_ENABLE, value,
                          sizeof(value));
  if (ret >= 0) {
    if (!strncmp(value, "true", sizeof(value))) {
      if (!adev->hfp_running)
        ret = hfp_set_enable(adev, true);
      else
        ALOGW("%s: HFP is already active.", __func__);
    } else {
      if (adev->hfp_running)
        ret = hfp_set_enable(adev, false);
      else
        ALOGW("%s: ignore STOP, HFP not active", __func__);
    }

    if (ret < 0)
      goto exit;
  }

  memset(value, 0, sizeof(value));
  ret = str_parms_get_str(parms, AUDIO_PARAMETER_HFP_SET_SAMPLING_RATE, value,
                          sizeof(value));
  if (ret >= 0) {
    rate = strtol(value, NULL, 10);
    ret = hfp_set_sampling_rate(adev, rate);
    if (ret < 0)
      goto exit;
  }

  memset(value, 0, sizeof(value));
  ret = str_parms_get_str(parms, AUDIO_PARAMETER_HFP_VOLUME, value,
                          sizeof(value));
  if (ret >= 0) {
    vol = strtol(value, NULL, 10);
    ret = hfp_set_volume(adev, vol);
  }

exit:
  ALOGD("%s exit: status", __func__);
  return ret;
}
#endif /*HFP_ENABLED*/
