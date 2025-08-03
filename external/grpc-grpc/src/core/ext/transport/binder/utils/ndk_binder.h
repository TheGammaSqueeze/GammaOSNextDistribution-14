// Copyright 2021 gRPC authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef GRPC_SRC_CORE_EXT_TRANSPORT_BINDER_UTILS_NDK_BINDER_H
#define GRPC_SRC_CORE_EXT_TRANSPORT_BINDER_UTILS_NDK_BINDER_H

#include <grpc/support/port_platform.h>

#ifdef GPR_SUPPORT_BINDER_TRANSPORT

#include <android/binder_ibinder.h>
#include <android/binder_ibinder_jni.h>
#include <android/binder_parcel_utils.h>

namespace grpc_binder {
namespace ndk_util {

using ::AIBinder;
using ::AParcel;
using ::AIBinder_Class;

// Only enum values used by the project is defined here
enum {
  FLAG_ONEWAY = 0x01,
};
enum {
  STATUS_OK = 0,
  STATUS_UNKNOWN_ERROR = (-2147483647 - 1),
};

using ::binder_status_t;
using ::binder_flags_t;
using ::transaction_code_t;

using ::AParcel_byteArrayAllocator;
using ::AParcel_stringAllocator;
using ::AIBinder_Class_onCreate;
using ::AIBinder_Class_onDestroy;
using ::AIBinder_Class_onTransact;

using ::AIBinder_Class_disableInterfaceTokenHeader;
using ::AIBinder_getUserData;
using ::AIBinder_getCallingUid;
using ::AIBinder_fromJavaBinder;
using ::AIBinder_Class_define;
using ::AIBinder_new;
using ::AIBinder_associateClass;
using ::AIBinder_incStrong;
using ::AIBinder_decStrong;
using ::AIBinder_transact;
using ::AParcel_readByteArray;
using ::AParcel_delete;
using ::AParcel_getDataSize;
using ::AParcel_writeInt32;
using ::AParcel_writeInt64;
using ::AParcel_writeStrongBinder;
using ::AParcel_writeString;
using ::AParcel_readInt32;
using ::AParcel_readInt64;
using ::AParcel_readString;
using ::AParcel_readStrongBinder;
using ::AParcel_writeByteArray;
using ::AIBinder_prepareTransaction;
using ::AIBinder_toJavaBinder;

}  // namespace ndk_util

}  // namespace grpc_binder

#endif  // GPR_SUPPORT_BINDER_TRANSPORT

#endif  // GRPC_SRC_CORE_EXT_TRANSPORT_BINDER_UTILS_NDK_BINDER_H
