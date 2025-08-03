/*
 *    Copyright (C) 2013 SAMSUNG S.LSI
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *
 */

#include <ctype.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "util.h"

/* START [H17080801] HAL config file path */
#define CFG_FILE_1 "/vendor/etc/libnfc-sec-vendor.conf"
#define CFG_FILE_2 "/etc/libnfc-sec-vendor.conf"
/* END [H17080801] HAL config file path */

#define isToken(x) (x == ':' || x == '=' || x == ' ' || x == '\t')
#define skipToken(x) \
  while (isToken(*x)) x++
#define skipSpace(x) \
  while (isspace(*x)) x++

bool willBeContinuous(char* buffer, size_t maxlen) {
  char* p;
  size_t len;
  if (!buffer) return false;

  len = strnlen(buffer, maxlen);
  if (len == maxlen && buffer[len - 2] != '\n') return true;

  p = buffer + len - 1;
  while (isspace(*p) && p > buffer) p--;
  if (*p == '\\') return true;
  return false;
}

bool find_by_name_from_current(FILE* file, const char* field) {
  char *p, buffer[256] = {
               '\0',
           };
  size_t len;
  int fp;
  bool skip = false;

  if (!file || !field) return false;

  len = strlen(field);
  while (!feof(file) && fgets(buffer, sizeof(buffer) - 1, file)) {
    if (skip) {
      skip = willBeContinuous(buffer, sizeof(buffer));
      continue;
    }
    skip = willBeContinuous(buffer, sizeof(buffer));

    p = buffer;
    skipSpace(p);
    if (*p == '#') continue;

    if (!strncmp((char const*)field, (char const*)p, len)) {
      fp = -strlen(p);
      fp += len;
      return (fseek(file, fp, SEEK_CUR) == 0) ? true : false;
    }
  }
  return false;
}

bool find_by_name(FILE* file, const char* field) {
  fseek(file, 0x00, SEEK_SET);
  return find_by_name_from_current(file, field);
}

bool __get_config_int(__attribute__((unused)) char* file_path,
                      const char* field, int* data, int option) {
  FILE* file;
  char buffer[10], *p, *endp;
  size_t len;
  long int val;

  if (!field || !data) return false;

  /* START [H17080801] HAL config file path */
  if ((file = fopen(CFG_FILE_1, "rb")) == NULL) {
    OSI_loge("Cannot open config file %s", CFG_FILE_1);
    if ((file = fopen(CFG_FILE_2, "rb")) == NULL) {
      OSI_loge("Cannot open config file %s", CFG_FILE_2);
      return 0;
    }
  }
  /* END [H17080801] HAL config file path */

  if (!find_by_name(file, field)) {
    OSI_loge("Cannot find the field name [%s]", field);
    goto fail;
  }

  if (!fgets(buffer, sizeof(buffer) - 1, file)) {
    OSI_loge("Read failed");
    goto fail;
  }

  if (willBeContinuous(buffer, sizeof(buffer)))  // not supported multi line
    goto fail;

  if ((len = strlen(buffer)) == sizeof(buffer) - 1) {
    OSI_loge("It is too long data [%s~]; max", buffer);
    goto fail;
  }

  p = buffer;
  skipToken(p);
  if (*p == '\0') {
    OSI_loge("It is empty data");
    goto fail;
  }

  if (((*p == '0') && (*(p + 1) == 'x')) || option == HAL_UTIL_GET_INT_16)
    val = strtol(p, &endp, 0x10);
  else
    val = strtol(p, &endp, 10);

  if (p == endp) {
    OSI_loge("Read failed [%s]", buffer);
    goto fail;
  }

  OSI_logd("Get config %s: %ld(0x%lx)", field, val, val);

  fclose(file);

  *data = val;
  return true;

fail:
  fclose(file);
  return false;
}

bool get_config_int(const char* field, int* data) {
  /* START [17080801] HAL config file path */
  return __get_config_int((char*)CFG_FILE_1, field, data, 0);
  /* END [17080801] HAL config file path */
}

int get_config_string(const char* field, char* strBuffer, size_t bufferSize) {
  FILE* file;
  char data[256], *buffer, *p;
  bool readmore = true;
  size_t count = 0;

  if (!field || !strBuffer || bufferSize < 1) return 0;

  /* START [H17080801] HAL config file path */
  if ((file = fopen(CFG_FILE_1, "rb")) == NULL) {
    OSI_loge("Cannot open config file %s", CFG_FILE_1);
    if ((file = fopen(CFG_FILE_2, "rb")) == NULL) {
      OSI_loge("Cannot open config file %s", CFG_FILE_2);
      return 0;
    }
  }
  /* END [H17080801] HAL config file path */

  if (!find_by_name(file, field)) {
    OSI_logd("Cannot find the field name [%s]", field);
    goto fail;
  }

  if ((buffer = (char*)malloc(bufferSize)) == NULL) {
    OSI_logd("Cannot allocate temporary buffer for [%s]", field);
    goto fail;
  }

  while (count < bufferSize - 1 && readmore) {
    if (!fgets(data, sizeof(data) - 1, file)) {
      OSI_loge("Read failed");
      goto fail_free;
    }

    readmore = willBeContinuous(data, sizeof(data));
    p = data;
    while ((p = strchr(p, '"')) != NULL)  // start string
    {
      for (p++; *p != '"'; p++)  // end string
      {
        if (*p == '\n' || *p == '\0' || *p == '\\') {
          OSI_loge("Cannot find ending point of string");
          goto fail_free;
        }
        buffer[count++] = *p;
      }
      p++;
    }
  }
  buffer[count] = '\0';

  OSI_logd("Get config %s: %s", field, buffer);
  if (count == bufferSize) {
    if (p == NULL)
      goto fail_free;
    else if (*p != '\n')
      OSI_loge("Overflower!, remained data is [%s]", p);
    else if (readmore)
      OSI_loge("Overflower!, data is remained! (multi line)");
  }

  count++;
  memcpy(strBuffer, buffer, count);
  free(buffer);

  fclose(file);
  return count;

fail_free:
  free(buffer);
fail:
  fclose(file);
  return 0;
}

int get_config_count(const char* field) {
  FILE* file;
  int count = 0;

  /* START [H17080801] HAL config file path */
  if ((file = fopen(CFG_FILE_1, "rb")) == NULL) {
    OSI_loge("Cannot open config file %s", CFG_FILE_1);
    if ((file = fopen(CFG_FILE_2, "rb")) == NULL) {
      OSI_loge("Cannot open config file %s", CFG_FILE_2);
      return 0;
    }
  }
  /* END [H17080801] HAL config file path */

  while (find_by_name_from_current(file, field)) count++;

  fclose(file);
  return count;
}

int get_hw_rev() {
  char* info_file = (char*)"/proc/cpuinfo";
  char* field = (char*)"Revision";
  int rev = -1;

  OSI_logd("%s enter;", __func__);
  __get_config_int(info_file, field, &rev, HAL_UTIL_GET_INT_16);
  OSI_logd("%s exit; rev = %d", __func__, rev);

  return rev;
}
