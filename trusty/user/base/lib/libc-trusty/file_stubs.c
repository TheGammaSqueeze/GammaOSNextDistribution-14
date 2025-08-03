/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

/* These stubs exist mostly to support gtest. */

FILE* fopen(const char* restrict filename, const char* restrict mode) {
    errno = ENOENT;
    return NULL;
}

/*
 * gtest expects getcwd to return a non-empty string, otherwise it will error
 * internally.
 */
char* getcwd(char* buf, size_t size) {
    strncpy(buf, "/", size);
    return buf;
}

int isatty(int fd) {
    return 0;
}

int remove(const char* pathname) {
    errno = EACCES;
    return -1;
}

int mkdir(const char* pathname, mode_t mode) {
    errno = EACCES;
    return -1;
}

int stat(const char* path, struct stat* buf) {
    errno = EACCES;
    return -1;
}
