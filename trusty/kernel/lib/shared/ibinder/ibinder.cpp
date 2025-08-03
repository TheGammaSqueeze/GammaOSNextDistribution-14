/*
 * Copyright (c) 2022, Google, Inc. All rights reserved
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

#include <binder/IBinder.h>
#include <lib/shared/ibinder/ibinder.h>
#include <lib/shared/ibinder/macros.h>

using android::IBinder;

// This empty struct must be defined here and not ibinder.h since this header
// can only be included by .cpp's which ensures that this type has a size of 1
// byte.  Binder client libraries that attempt to define interfaces as empty
// structs in headers included by .c's will get a compiler error triggered by
// -Wextern-c-compat. This limitation does not apply to structs with one or more
// members.
struct ibinder {};

IBINDER_DEFINE_IFACE_CONTAINER(IBinder, ibinder);
IBINDER_DEFINE_ADD_REF_IFACE(ibinder);
IBINDER_DEFINE_RELEASE_IFACE(ibinder);
