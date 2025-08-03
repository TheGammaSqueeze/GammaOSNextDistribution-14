
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

#define TLOG_TAG "lib-stats"

#include <android/frameworks/stats/VendorAtom.h>
#include <android/frameworks/stats/VendorAtomValue.h>
#include <android/trusty/stats/tz/IStats.h>
#include <binder/RpcSession.h>
#include <binder/RpcTransportTipcTrusty.h>
#include <lib/shared/binder_discover/binder_discover.h>
#include <lib/shared/ibinder/ibinder.h>
#include <lib/shared/ibinder/macros.h>
#include <lib/stats/stats.h>
#include <lib/tipc/tipc_srv.h>
#include <stdio.h>
#include <trusty_log.h>
#include <uapi/err.h>

using android::frameworks::stats::VendorAtom;
using android::frameworks::stats::VendorAtomValue;
using android::trusty::stats::tz::IStats;

#define PARCEL_SET(value_type, value_args...)                     \
    do {                                                          \
        auto parcel = stats_vendor_atom_to_VendorAtom(self);      \
        VendorAtomValue atom_value;                               \
        atom_value.set<value_type>(value_args);                   \
        if (parcel->values.size() < atom_value_index + 1) {       \
            parcel->values.resize(atom_value_index + 1);          \
        }                                                         \
        parcel->values[atom_value_index] = std::move(atom_value); \
        return android::OK;                                       \
    } while (0)

// We do not have anything to put in the structures right now,
// but we need them to be allocatable.
struct stats_istats {};

struct stats_vendor_atom {};

IBINDER_DEFINE_IFACE(IStats, stats_istats);
IBINDER_DEFINE_PARCELABLE(VendorAtom, stats_vendor_atom);

// VendorAtom parcel setter C API

int stats_istats_get_service(const char* port,
                             size_t port_len,
                             struct stats_istats** pself) {
    assert(pself);
    android::sp<android::IBinder> binder;

    const char* binder_port = port;
    if (port_len > 0) {
        assert(port && port[port_len - 1] == '\0');
    }
    if (int rc = binder_discover_get_service(binder_port, binder);
        rc != android::OK) {
        return rc;
    }
    auto container = new (std::nothrow) stats_istats_container{
            IStats::asInterface(binder), stats_istats{}, {1}};
    if (container == nullptr) {
        return ERR_NO_MEMORY;
    }
    *pself = &container->cbinder;
    return android::OK;
}

int stats_istats_report_vendor_atom(struct stats_istats* self,
                                    struct stats_vendor_atom* vendor_atom) {
    auto iface = stats_istats_to_IStats(self);
    auto parcel = stats_vendor_atom_to_VendorAtom(vendor_atom);
    auto rc = iface->reportVendorAtom(*parcel);
    if (!rc.isOk()) {
        TLOGE("iface->reportVendorAtom failed %s\n", rc.toString8().c_str());
        return rc.exceptionCode();
    }
    return android::OK;
}

// VendorAtom parcel setter C API

int stats_vendor_atom_create_parcel(struct stats_vendor_atom** pself) {
    assert(pself);
    auto parcel = new (std::nothrow) VendorAtom();
    if (parcel == nullptr) {
        return ERR_NO_MEMORY;
    }
    const auto container = new (std::nothrow)
            stats_vendor_atom_container{parcel, stats_vendor_atom{}, {1}};
    if (container == nullptr) {
        delete parcel;
        return ERR_NO_MEMORY;
    }
    *pself = &container->cparcel;
    return android::OK;
}

int stats_vendor_atom_set_reverse_domain_name(struct stats_vendor_atom* self,
                                              const char* name,
                                              size_t name_len) {
    auto parcel = stats_vendor_atom_to_VendorAtom(self);
    parcel->reverseDomainName = android::String16(name, name_len);
    return android::OK;
}

int stats_vendor_atom_set_atom_id(struct stats_vendor_atom* self, int atom_id) {
    auto parcel = stats_vendor_atom_to_VendorAtom(self);
    parcel->atomId = atom_id;
    return android::OK;
}

int stats_vendor_atom_set_int_value_at(struct stats_vendor_atom* self,
                                       size_t atom_value_index,
                                       int32_t value) {
    PARCEL_SET(VendorAtomValue::intValue, value);
}

int stats_vendor_atom_set_long_value_at(struct stats_vendor_atom* self,
                                        size_t atom_value_index,
                                        int64_t value) {
    PARCEL_SET(VendorAtomValue::longValue, value);
}

int stats_vendor_atom_set_float_value_at(struct stats_vendor_atom* self,
                                         size_t atom_value_index,
                                         float value) {
    PARCEL_SET(VendorAtomValue::floatValue, value);
}

int stats_vendor_atom_set_string_value_at(struct stats_vendor_atom* self,
                                          size_t atom_value_index,
                                          const char* value,
                                          size_t value_len) {
    PARCEL_SET(VendorAtomValue::stringValue, value, value_len);
}
