/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef LIBCONNECTIVITY_CONNECTIVITY_NATIVE_H_
#define LIBCONNECTIVITY_CONNECTIVITY_NATIVE_H_

#include <sys/cdefs.h>
#include <netinet/in.h>

// For branches that do not yet have __ANDROID_API_U__ defined, like module
// release branches.
#ifndef __ANDROID_API_U__
#define __ANDROID_API_U__ 34
#endif

__BEGIN_DECLS

/**
 * Blocks a port from being assigned during bind(). The caller is responsible for updating
 * /proc/sys/net/ipv4/ip_local_port_range with the port being blocked so that calls to connect()
 * will not automatically assign one of the blocked ports.
 * Will return success even if port was already blocked.
 *
 * Returns 0 on success, or a POSIX error code (see errno.h) on failure:
 *  - EINVAL for invalid port number
 *  - EPERM if the UID of the client doesn't have network stack permission
 *  - Other errors as per https://man7.org/linux/man-pages/man2/bpf.2.html
 *
 * @param port Int corresponding to port number.
 */
int AConnectivityNative_blockPortForBind(in_port_t port) __INTRODUCED_IN(__ANDROID_API_U__);

/**
 * Unblocks a port that has previously been blocked.
 * Will return success even if port was already unblocked.
 *
 * Returns 0 on success, or a POSIX error code (see errno.h) on failure:
 *  - EINVAL for invalid port number
 *  - EPERM if the UID of the client doesn't have network stack permission
 *  - Other errors as per https://man7.org/linux/man-pages/man2/bpf.2.html
 *
 * @param port Int corresponding to port number.
 */
int AConnectivityNative_unblockPortForBind(in_port_t port) __INTRODUCED_IN(__ANDROID_API_U__);

/**
 * Unblocks all ports that have previously been blocked.
 *
 * Returns 0 on success, or a POSIX error code (see errno.h) on failure:
 *  - EINVAL for invalid port number
 *  - EPERM if the UID of the client doesn't have network stack permission
 *  - Other errors as per https://man7.org/linux/man-pages/man2/bpf.2.html
 */
int AConnectivityNative_unblockAllPortsForBind() __INTRODUCED_IN(__ANDROID_API_U__);

/**
 * Gets the list of ports that have been blocked.
 *
 * Returns 0 on success, or a POSIX error code (see errno.h) on failure:
 *  - EINVAL for invalid port number
 *  - EPERM if the UID of the client doesn't have network stack permission
 *  - Other errors as per https://man7.org/linux/man-pages/man2/bpf.2.html
 *
 * @param ports Array of ports that will be filled with the port numbers.
 * @param count Pointer to the size of the ports array; the value will be set to the total number of
 *              blocked ports, which may be larger than the ports array that was filled.
 */
int AConnectivityNative_getPortsBlockedForBind(in_port_t* _Nonnull ports, size_t* _Nonnull count)
    __INTRODUCED_IN(__ANDROID_API_U__);

__END_DECLS


#endif
