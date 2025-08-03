/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#pragma once

#include <stddef.h>
#include <virtio-console.h>

#define MIN(x, y) (((x) < (y)) ? (x) : (y))

/**
 * init_log() - Enable the default logger.
 * @console - virtio serial console device.
 *
 * Returns 0 if succeed to initialize logger, returns negative value
 * otherwise.
 */
int init_log(struct virtio_console* console);

/**
 * abort_msg() - Terminates abnormally, printing the message provided.
 * @msg - Null-terminated string to output before dying.
 *
 * This function is used to indicate framework failure as opposed to test
 * failure. For now, that is signaled by exiting with status code 2 via
 * testrunner0 communication channel.
 */
void abort_msg(const char* msg);

/**
 * log_msg() - Logs a null terminated string.
 * @msg - Null terminated string to log.
 */
void log_msg(const char* msg);

/**
 * log_buf - Logs a sized buffer.
 * @buf - Pointer to the start of the buffer to log.
 * @len - Size of the buffer to log.
 *
 * This is a more raw form of log_msg to use when, for example, relaying a
 * message from Trusty directly into the log.
 *
 * Data in the buffer should be printable, this function is not intended for
 * dumping raw buffers.
 */
void log_buf(const char* buf, size_t len);
