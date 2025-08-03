/*
 * Copyright 2022 The Android Open Source Project
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

#pragma once

#include <errno.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

#define ARGP_ERR_UNKNOWN -1

#define ARGP_HELP_STD_HELP 0

#define ARGP_KEY_ARG '\0'
#define ARGP_KEY_END 256

#define OPTION_HIDDEN 1

struct argp_option {
    const char *name;
    const int key;
    const char *argname;
    int n;
    const char *docstring;
};

struct argp_state {
    int arg_num;
    void *input;
    const struct argp *argp;
};

typedef int error_t;

struct argp {
    const struct argp_option *options;
    error_t (*parser)(int key, char *arg, struct argp_state *state);
    const char *doc;
    const char *args_doc;
};

error_t argp_parse(const struct argp *argp, int argc, char **argv, int, void*, void*);
void argp_usage(struct argp_state*);
void argp_state_help(struct argp_state*, FILE *fd, int);

#ifdef __cplusplus
}
#endif
