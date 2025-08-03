/*****************************************************************************
 * Copyright ©2017-2019 Gemalto – a Thales Company. All rights Reserved.
 *
 * This copy is licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *     http://www.apache.org/licenses/LICENSE-2.0 or https://www.apache.org/licenses/LICENSE-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.

 ****************************************************************************/

/**
 * @file
 * $Author$
 * $Revision$
 * $Date$
 *
 * Internal of libspiplus.
 *
 */

#ifndef LIBSE_GTO_PRIVATE_H
#define LIBSE_GTO_PRIVATE_H

#include <se-gto/libse-gto.h>
#include "iso7816_t1.h"

#define SE_GTO_EXPORT __attribute__((visibility("default")))

/**
 * SECTION:libspiplus
 * @short_description: libspiplus context
 *
 * The context contains the default values for the library user,
 * and is passed to all library operations.
 */
struct se_gto_ctx {
    void *userdata;

    int            log_level;
    se_gto_log_fn *log_fn;
    char          *log_buf;

    const char *gtodev;

    void *spi_buffer;
    int   spi_nbuffer;

    struct t1_state t1;

    uint8_t check_alive;
};

#include "log.h"

#endif /* ifndef LIBSE_GTO_PRIVATE_H */
