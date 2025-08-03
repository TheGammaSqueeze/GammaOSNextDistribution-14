/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <trusty/sysdeps.h>
#include <trusty/trusty_ipc.h>

__BEGIN_CDECLS
/*
 * Initialize binder library. Returns one of trusty_err.
 *
 * @dev: trusty_ipc_dev
 */
int tidl_init(struct trusty_ipc_dev* dev);

/*
 * Shutdown binder clients
 *
 */
void tidl_shutdown(void);

/*
 * close binder channel
 *
 * @fd: binder channel handle
 */
int tidl_chan_close(handle_t fd);

__END_CDECLS
