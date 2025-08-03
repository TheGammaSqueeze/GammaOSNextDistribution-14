/******************************************************************************
 *
 *  Copyright 2016 Google, Inc.
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
 ******************************************************************************/

#define LOG_TAG "bt_osi_rand"

#include "osi/include/osi.h"

#include <base/logging.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include "check.h"
#include "os/log.h"

#define RANDOM_PATH "/dev/urandom"

int osi_rand(void) {
  int rand;
  int rand_fd = open(RANDOM_PATH, O_RDONLY);

  if (rand_fd == INVALID_FD) {
    LOG_ERROR("%s can't open rand fd %s: %s ", __func__, RANDOM_PATH,
              strerror(errno));
    CHECK(rand_fd != INVALID_FD);
  }

  ssize_t read_bytes = read(rand_fd, &rand, sizeof(rand));
  close(rand_fd);

  CHECK(read_bytes == sizeof(rand));

  if (rand < 0) rand = -rand;

  return rand;
}
