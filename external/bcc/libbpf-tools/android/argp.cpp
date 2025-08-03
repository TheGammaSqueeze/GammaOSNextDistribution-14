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

#include "argp.h"

#include <getopt.h>
#include <stdio.h>
#include <sysexits.h>

#include <algorithm>
#include <sstream>
#include <string>
#include <vector>

extern "C" error_t argp_parse(const struct argp *argp, int argc, char **argv, int /*unused*/,
                              void * /* unused */, void *input) {
    int longindex;
    std::string optstring;
    std::vector<struct option> optvec;

    // process argp_option array for use with getopt_long
    for (const struct argp_option *opt = argp->options; opt->name || opt->docstring; ++opt) {
        if (opt->key && isprint(opt->key)) {
            optstring += opt->key;
            if (opt->argname) optstring += ':';
        }

        if (opt->name) {
            optvec.push_back({ .name = opt->name, .has_arg = opt->argname ? 1 : 0,
                    .flag = opt->key ? nullptr : &longindex, .val = opt->key });
        }
    }
    int longhelp = 0;
    optvec.push_back({ .name = "help", .has_arg = 0, .flag = &longhelp, .val = 1 });
    optvec.push_back({});

    int opt;
    while ((opt = getopt_long(argc, argv, optstring.c_str(), optvec.data(), &longindex)) != -1) {
        struct argp_state state = { .input = input, .argp = argp };
        if (!opt) {
            if (longhelp) argp_state_help(&state, stdout, ARGP_HELP_STD_HELP);
            return EINVAL;
        }
        error_t ret = argp->parser(opt, optarg, &state);
        if (ret) return ret;
    }

    // Handle positional arguments
    if (optind < argc) {
        for (int idx = optind; idx < argc; idx++) {
            struct argp_state state = { .input = input, .argp = argp, .arg_num = idx - optind };
            const error_t ret = argp->parser(ARGP_KEY_ARG, argv[idx], &state);
            if (ret) return ret;
        }
    }
    struct argp_state state = {.input = input, .argp = argp};
    const error_t ret = argp->parser(ARGP_KEY_END, 0, &state);
    // Not all tools expect ARGP_KEY_END, so ARGP_ERR_UNKNOWN here is benign
    if (ret && ret != ARGP_ERR_UNKNOWN) return ret;
    return 0;
}


extern "C" void argp_usage(struct argp_state* state) {
    fprintf(stderr, "%s", state->argp->doc);
    exit(EX_USAGE);
}

extern "C" void argp_state_help(struct argp_state* state, FILE *fd, int /* unused */) {
    constexpr size_t kFlagOffset = 2, kNameOffset = 6, kDocstringOffset = 29;

    fprintf(fd, "%s\n", state->argp->doc);
    for (const struct argp_option *opt = state->argp->options; opt->name || opt->docstring; ++opt) {
        // Skip hidden arguments and empty entries in the argp_option array
        if (opt->n == OPTION_HIDDEN || (opt->docstring && opt->docstring[0] == '\0')) continue;

        std::string s(kFlagOffset, ' ');

        // Append short argument form (e.g. "-p,") if applicable, then whitespace
        if (opt->key && isprint(opt->key)) {
            s.append("-");
            s.append(1, (char)opt->key);
            s.append(",");
        }
        s.append(kNameOffset - s.length(), ' ');

        // Append long argument form (e.g. "--pid=PID") or whitespace
        if (opt->name) {
            s.append("--");
            s.append(opt->name);
            if (opt->argname) {
                s.append("=");
                s.append(opt->argname);
            }
        }
        if (s.length() < kDocstringOffset) {
            s.append(kDocstringOffset - s.length(), ' ');
        } else {
            s.append(" ");
        }

        // Append docstring
        s.append(opt->docstring);
        s.append("\n");
        fprintf(fd, "%s", s.c_str());
    }
    exit(EX_OK);
}
