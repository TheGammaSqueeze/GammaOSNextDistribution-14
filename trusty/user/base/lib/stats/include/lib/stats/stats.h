
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
#pragma once

#include <lib/shared/ibinder/ibinder.h>
#include <lk/compiler.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
#include <android/frameworks/stats/VendorAtom.h>
#include <android/trusty/stats/tz/IStats.h>
#endif

__BEGIN_CDECLS

struct stats_istats;
struct stats_vendor_atom;

void stats_istats_add_ref(struct stats_istats* self);

void stats_istats_release(struct stats_istats** pself);

__WARN_UNUSED_RESULT int stats_istats_get_service(const char* port,
                                                  size_t port_len,
                                                  struct stats_istats** pself);

__WARN_UNUSED_RESULT int stats_istats_report_vendor_atom(
        struct stats_istats* self,
        struct stats_vendor_atom* vendor_atom);

void stats_vendor_atom_add_ref(struct stats_vendor_atom* self);

void stats_vendor_atom_release(struct stats_vendor_atom** pself);

__WARN_UNUSED_RESULT int stats_vendor_atom_create_parcel(
        struct stats_vendor_atom** pself);

__WARN_UNUSED_RESULT int stats_vendor_atom_set_reverse_domain_name(
        struct stats_vendor_atom* self,
        const char* name,
        size_t name_len);

__WARN_UNUSED_RESULT int stats_vendor_atom_set_atom_id(
        struct stats_vendor_atom* self,
        int atom_id);

__WARN_UNUSED_RESULT int stats_vendor_atom_set_int_value_at(
        struct stats_vendor_atom* self,
        size_t atom_value_index,
        int32_t value);

__WARN_UNUSED_RESULT int stats_vendor_atom_set_long_value_at(
        struct stats_vendor_atom* self,
        size_t atom_value_index,
        int64_t value);

__WARN_UNUSED_RESULT int stats_vendor_atom_set_float_value_at(
        struct stats_vendor_atom* self,
        size_t atom_value_index,
        float value);

__WARN_UNUSED_RESULT int stats_vendor_atom_set_string_value_at(
        struct stats_vendor_atom* self,
        size_t atom_value_index,
        const char* value,
        size_t value_len);

__END_CDECLS

#ifdef __cplusplus
android::frameworks::stats::VendorAtom* stats_vendor_atom_to_VendorAtom(
        struct stats_vendor_atom* self);
android::sp<android::trusty::stats::tz::IStats>& stats_istats_to_IStats(
        struct stats_istats* self);
#endif
