/*
 * Copyright (c) 2019, Google Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

/*
 * Interface for SMC access policy. Implementation of the policy must be
 * defined per platform.
 */

#include <interface/smc/smc.h>
#include <stdint.h>
#include <uapi/trusty_uuid.h>

struct smc_access_policy {
    /* Check whether a given SMC is allowed */
    int (*check_access)(uint32_t smc_nr);
    /* Check whether a given SMC request is valid */
    int (*check_request)(uint32_t smc_nr,
                         const struct uuid* uuid,
                         const struct smc_msg* request);
};

/*
 * smc_load_access_policy() - load client's permissions to issue SMCs
 * @uuid: uuid of the client whose permissions are being loaded
 * @policy: smc_access_policy to be filled out
 */
void smc_load_access_policy(const struct uuid* uuid,
                            struct smc_access_policy* policy);
