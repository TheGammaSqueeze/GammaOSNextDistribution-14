/*
 * Copyright 2021, The Android Open Source Project
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

/*
 * This is a reimplementation of obstack_printf for use by libcpu, which
 * uses it to print an error message.
 */

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <obstack.h>

int __attribute__((__format__(printf, 2, 3)))  obstack_printf(struct obstack *obs, const char *format, ...) {

  va_list ap;
  va_start(ap, format);

  char* str = NULL;
  int len = vasprintf(&str, format, ap);
  if (len < 0) {
    return len;
  }

  obstack_grow(obs, str, len);
  free(str);

  return len;
}
