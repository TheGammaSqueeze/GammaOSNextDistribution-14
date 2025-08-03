/*
 * Copyright (C) 2019 The Android Open Source Project
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

// This is a compilation test only, to see that types are generated as
// expected.

#include <hidl2aidl/test/BnBar.h>
#include <hidl2aidl/test/BnFoo.h>
#include <hidl2aidl/test/BpBar.h>
#include <hidl2aidl/test/BpFoo.h>
#include <hidl2aidl/test/IBar.h>
#include <hidl2aidl/test/IFoo.h>
#include <hidl2aidl/test/NameCollision.h>
#include <hidl2aidl/test/OnlyIn10.h>
#include <hidl2aidl/test/OnlyIn11.h>
#include <hidl2aidl/test/Outer.h>
#include <hidl2aidl/test/OverrideMe.h>
#include <hidl2aidl/test/Value.h>
#include <hidl2aidl/test2/BnFoo.h>
#include <hidl2aidl/test2/BpFoo.h>
#include <hidl2aidl/test2/IFoo.h>

using android::sp;
using android::String16;
using android::binder::Status;

void testIFoo(const sp<hidl2aidl::test::IFoo>& foo) {
    Status status1 = foo->someBar(String16(), String16());
    (void)status1;
    String16 f;
    Status status2 = foo->oneOutput(&f);
    (void)status2;
    hidl2aidl::test::IFoo::BigStruct big_struct;
    big_struct.type = 2;
    big_struct.value = 3;
    // Test some of the types here as well
    hidl2aidl::test::Outer outer;
    outer.inner.a = 1;
    outer.a = 2;
    hidl2aidl::test::OverrideMe override;
    override.a = String16();
    hidl2aidl::test::NameCollision collision;
    collision.a = 1;
    collision.b = String16();
    collision.c = String16();
}

void testIBar(const sp<hidl2aidl::test::IBar>& bar) {
    String16 out;
    Status status1 = bar->someBar(String16(), 3, &out);
    (void)status1;
    hidl2aidl::test::IBar::Inner inner;
    inner.a = 3;
    Status status2 = bar->extraMethod(inner);
    (void)status2;
}

static_assert(static_cast<int>(hidl2aidl::test::Value::A) == 3);
static_assert(static_cast<int>(hidl2aidl::test::Value::B) == 7);
static_assert(static_cast<int>(hidl2aidl::test::Value::C) == 8);
static_assert(static_cast<int>(hidl2aidl::test::Value::D) == 9);
static_assert(static_cast<int>(hidl2aidl::test::Value::E) == 27);
static_assert(static_cast<int>(hidl2aidl::test::Value::F) == 28);

void testIFoo2(const sp<hidl2aidl::test2::IFoo>& foo) {
    Status status = foo->someFoo(3);
    (void)status;
}
