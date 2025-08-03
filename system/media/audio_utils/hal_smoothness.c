/*
** Copyright 2022, The Android Open-Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define LOG_TAG "hal_smoothness"

#include <audio_utils/hal_smoothness.h>
#include <errno.h>
#include <float.h>
#include <log/log.h>
#include <math.h>
#include <stdlib.h>

typedef struct hal_smoothness_internal {
  struct hal_smoothness itfe;

  struct hal_smoothness_metrics metrics;

  // number of “total_writes” before flushing smoothness data to system (ie.
  // logcat) A flush will also reset all numeric values in the "metrics" field.
  unsigned int num_writes_to_log;

  // Client defined function to flush smoothness metrics.
  void (*client_flush_cb)(struct hal_smoothness_metrics *smoothness_metrics,
                          void *private_data);

  // Client provided pointer.
  void *private_data;
} hal_smoothness_internal;

static void reset_metrics(struct hal_smoothness_metrics *metrics) {
  metrics->underrun_count = 0;
  metrics->overrun_count = 0;
  metrics->total_writes = 0;
  metrics->total_frames_written = 0;
  metrics->total_frames_lost = 0;
  metrics->timestamp = 0;
  metrics->smoothness_value = 0.0;
}

static bool add_check_overflow(unsigned int *data, unsigned int add_amount) {
  return __builtin_add_overflow(*data, add_amount, data);
}

static int increment_underrun(struct hal_smoothness *smoothness,
                              unsigned int frames_lost) {
  if (smoothness == NULL) {
    return -EINVAL;
  }

  hal_smoothness_internal *smoothness_meta =
      (hal_smoothness_internal *)smoothness;

  if (add_check_overflow(&smoothness_meta->metrics.underrun_count, 1)) {
    return -EOVERFLOW;
  }

  if (add_check_overflow(&smoothness_meta->metrics.total_frames_lost,
                         frames_lost)) {
    return -EOVERFLOW;
  }

  return 0;
}

static int increment_overrun(struct hal_smoothness *smoothness,
                             unsigned int frames_lost) {
  if (smoothness == NULL) {
    return -EINVAL;
  }

  hal_smoothness_internal *smoothness_meta =
      (hal_smoothness_internal *)smoothness;

  if (add_check_overflow(&smoothness_meta->metrics.overrun_count, 1)) {
    return -EOVERFLOW;
  }

  if (add_check_overflow(&smoothness_meta->metrics.total_frames_lost,
                         frames_lost)) {
    return -EOVERFLOW;
  }

  return 0;
}

static double calc_smoothness_value(unsigned int total_frames_lost,
                                    unsigned int total_frames_written) {
  // If error checks are correct in this library, this error shouldn't be
  // possible.
  if (total_frames_lost == 0 && total_frames_written == 0) {
    ALOGE("total_frames_lost + total_frames_written shouldn't = 0");
    return -EINVAL;
  }

  // No bytes dropped, so audio smoothness is perfect.
  if (total_frames_lost == 0) {
    return DBL_MAX;
  }

  unsigned int total_frames = total_frames_lost;

  if (add_check_overflow(&total_frames, total_frames_written)) {
    return -EOVERFLOW;
  }

  // Division by 0 shouldn't be possible.
  double lost_frames_ratio = (double)total_frames_lost / total_frames;

  // log(0) shouldn't be possible.
  return -log(lost_frames_ratio);
}

static int flush(struct hal_smoothness *smoothness) {
  if (smoothness == NULL) {
    return -EINVAL;
  }

  hal_smoothness_internal *smoothness_meta =
      (hal_smoothness_internal *)smoothness;

  smoothness_meta->metrics.smoothness_value =
      calc_smoothness_value(smoothness_meta->metrics.total_frames_lost,
                            smoothness_meta->metrics.total_frames_written);
  smoothness_meta->client_flush_cb(&smoothness_meta->metrics,
                                   smoothness_meta->private_data);
  reset_metrics(&smoothness_meta->metrics);

  return 0;
}

static int increment_total_writes(struct hal_smoothness *smoothness,
                                  unsigned int frames_written,
                                  unsigned long timestamp) {
  if (smoothness == NULL) {
    return -EINVAL;
  }

  hal_smoothness_internal *smoothness_meta =
      (hal_smoothness_internal *)smoothness;

  if (add_check_overflow(&smoothness_meta->metrics.total_writes, 1)) {
    return -EOVERFLOW;
  }

  if (add_check_overflow(&smoothness_meta->metrics.total_frames_written,
                         frames_written)) {
    return -EOVERFLOW;
  }
  smoothness_meta->metrics.timestamp = timestamp;

  // "total_writes" count has met a value where the client's callback function
  // should be called
  if (smoothness_meta->metrics.total_writes >=
      smoothness_meta->num_writes_to_log) {
    flush(smoothness);
  }

  return 0;
}

int hal_smoothness_initialize(
    struct hal_smoothness **smoothness, unsigned int version,
    unsigned int num_writes_to_log,
    void (*client_flush_cb)(struct hal_smoothness_metrics *, void *),
    void *private_data) {
  if (num_writes_to_log == 0) {
    ALOGE("num_writes_to_logs must be > 0");

    return -EINVAL;
  }

  if (client_flush_cb == NULL) {
    ALOGE("client_flush_cb can't be NULL");

    return -EINVAL;
  }

  hal_smoothness_internal *smoothness_meta;
  smoothness_meta =
      (hal_smoothness_internal *)calloc(1, sizeof(hal_smoothness_internal));

  if (smoothness_meta == NULL) {
    int ret_err = errno;
    ALOGE("failed to calloc hal_smoothness_internal.");
    return ret_err;
  }

  smoothness_meta->itfe.version = version;
  smoothness_meta->itfe.increment_underrun = increment_underrun;
  smoothness_meta->itfe.increment_overrun = increment_overrun;
  smoothness_meta->itfe.increment_total_writes = increment_total_writes;
  smoothness_meta->itfe.flush = flush;

  smoothness_meta->num_writes_to_log = num_writes_to_log;
  smoothness_meta->client_flush_cb = client_flush_cb;
  smoothness_meta->private_data = private_data;

  *smoothness = &smoothness_meta->itfe;

  return 0;
}

void hal_smoothness_free(struct hal_smoothness **smoothness) {
  if (smoothness == NULL || *smoothness == NULL) {
    return;
  }

  free(*smoothness);
  *smoothness = NULL;
}
