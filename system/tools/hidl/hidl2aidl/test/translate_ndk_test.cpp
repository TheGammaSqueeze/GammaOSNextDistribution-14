/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <hidl2aidl/test/extension/translate-ndk.h>
#include <hidl2aidl/test/translate-ndk.h>

#include <gtest/gtest.h>

class Hidl2aidlTranslateTest : public ::testing::Test {};

namespace android {

TEST_F(Hidl2aidlTranslateTest, Onlyin10) {
    aidl::hidl2aidl::test::OnlyIn10 dest;
    hidl2aidl::test::V1_0::OnlyIn10 source;
    source.str = "Hello";
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.str, dest.str);
}

TEST_F(Hidl2aidlTranslateTest, OnlyIn11) {
    aidl::hidl2aidl::test::OnlyIn11 dest;
    hidl2aidl::test::V1_1::OnlyIn11 source;
    source.str = 12;
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.str, dest.str);
}

TEST_F(Hidl2aidlTranslateTest, OverrideMe) {
    aidl::hidl2aidl::test::OverrideMe dest;
    hidl2aidl::test::V1_1::OverrideMe source;
    source.a = "World";
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.a, dest.a);
}

TEST_F(Hidl2aidlTranslateTest, Outer) {
    aidl::hidl2aidl::test::Outer dest;
    hidl2aidl::test::V1_1::Outer source;
    source.a = 12;
    source.v1_0.inner.a = 16;
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.a, dest.a);
    EXPECT_EQ(source.v1_0.inner.a, dest.inner.a);
}

TEST_F(Hidl2aidlTranslateTest, OuterInner) {
    aidl::hidl2aidl::test::Outer::Inner dest;
    hidl2aidl::test::V1_0::Outer::Inner source;
    source.a = 12;
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.a, dest.a);
}

TEST_F(Hidl2aidlTranslateTest, NameCollision) {
    aidl::hidl2aidl::test::NameCollision dest;
    hidl2aidl::test::V1_2::NameCollision source;
    source.reference.reference.a = 12;
    source.reference.b = "Fancy";
    source.c = "Car";
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.reference.reference.a, dest.a);
    EXPECT_EQ(source.reference.b, dest.b);
    EXPECT_EQ(source.c, dest.c);
}

TEST_F(Hidl2aidlTranslateTest, IFooBigStruct) {
    aidl::hidl2aidl::test::IFoo::BigStruct dest;
    hidl2aidl::test::V1_1::IFoo::BigStruct source;
    source.type = 12;
    source.value = 16;
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.type, dest.type);
    EXPECT_EQ(source.value, dest.value);
}

TEST_F(Hidl2aidlTranslateTest, IBarInner) {
    aidl::hidl2aidl::test::IBar::Inner dest;
    hidl2aidl::test::V1_0::IBar::Inner source;
    source.a = 0x70000000;
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(static_cast<int32_t>(source.a), dest.a);
}

TEST_F(Hidl2aidlTranslateTest, UnsignedToSignedTooLarge) {
    aidl::hidl2aidl::test::IBar::Inner dest;
    hidl2aidl::test::V1_0::IBar::Inner source;
    // source.a is uint32_t, dest.a is int32_t
    source.a = 0xf0000000;
    ASSERT_FALSE(h2a::translate(source, &dest));
    EXPECT_NE(static_cast<int32_t>(source.a), dest.a);
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarByte) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    source.a(8);
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.a(), dest.get<aidl::hidl2aidl::test::SafeUnionBar::a>());
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarInt64) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    source.b(25000);
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(static_cast<int64_t>(source.b()), dest.get<aidl::hidl2aidl::test::SafeUnionBar::b>());
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarInnerStructBar) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    hidl2aidl::test::V1_2::SafeUnionBar::InnerStructBar inner;
    inner.x = 8;
    inner.z = 12;
    source.innerStructBar(inner);
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.innerStructBar().x,
              dest.get<aidl::hidl2aidl::test::SafeUnionBar::innerStructBar>().x);
    EXPECT_EQ(source.innerStructBar().z,
              dest.get<aidl::hidl2aidl::test::SafeUnionBar::innerStructBar>().z);
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarOnlyIn11) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    hidl2aidl::test::V1_1::OnlyIn11 onlyIn11;
    onlyIn11.str = 12;
    source.c(onlyIn11);
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.c().str, dest.get<aidl::hidl2aidl::test::SafeUnionBar::c>().str);
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarString) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    source.d("Hello world!");
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.d(), dest.get<aidl::hidl2aidl::test::SafeUnionBar::d>());
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarFloat) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    source.e(3.5f);
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.e(), dest.get<aidl::hidl2aidl::test::SafeUnionBar::e>());
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarDouble) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    source.f(3e10);
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.f(), dest.get<aidl::hidl2aidl::test::SafeUnionBar::f>());
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarBitfield) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    ::android::hardware::hidl_bitfield<::hidl2aidl::test::V1_2::FooFlag> bits(0);
    bits |= hidl2aidl::test::V1_2::FooFlag::THIRD;
    source.g(bits);
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(static_cast<aidl::hidl2aidl::test::FooFlag>(source.g()),
              dest.get<aidl::hidl2aidl::test::SafeUnionBar::g>());
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarEnum) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    source.h(hidl2aidl::test::V1_1::Value::B);
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(static_cast<aidl::hidl2aidl::test::Value>(source.h()),
              dest.get<aidl::hidl2aidl::test::SafeUnionBar::h>());
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarChar16) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    source.i(12);
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.i(), dest.get<aidl::hidl2aidl::test::SafeUnionBar::i>());
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarVec8) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    source.j({12, 6});
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.j().size(), dest.get<aidl::hidl2aidl::test::SafeUnionBar::j>().size());
    EXPECT_EQ(source.j()[0], dest.get<aidl::hidl2aidl::test::SafeUnionBar::j>()[0]);
    EXPECT_EQ(source.j()[1], dest.get<aidl::hidl2aidl::test::SafeUnionBar::j>()[1]);
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarVec64) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    source.k({hidl2aidl::test::V1_1::Value::A, hidl2aidl::test::V1_1::Value::B});
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.k().size(), dest.get<aidl::hidl2aidl::test::SafeUnionBar::k>().size());
    EXPECT_EQ(source.k()[0], static_cast<hidl2aidl::test::V1_1::Value>(
                                     dest.get<aidl::hidl2aidl::test::SafeUnionBar::k>()[0]));
    EXPECT_EQ(source.k()[1], static_cast<hidl2aidl::test::V1_1::Value>(
                                     dest.get<aidl::hidl2aidl::test::SafeUnionBar::k>()[1]));
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarArray8) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    source.l(::android::hardware::hidl_array<int8_t, 2>({12, 6}));
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.l().size(), dest.get<aidl::hidl2aidl::test::SafeUnionBar::l>().size());
    EXPECT_EQ(source.l()[0], dest.get<aidl::hidl2aidl::test::SafeUnionBar::l>()[0]);
    EXPECT_EQ(source.l()[1], dest.get<aidl::hidl2aidl::test::SafeUnionBar::l>()[1]);
}

TEST_F(Hidl2aidlTranslateTest, SafeUnionBarRepeatedFloat) {
    aidl::hidl2aidl::test::SafeUnionBar dest;
    hidl2aidl::test::V1_2::SafeUnionBar source;
    source.m(3.5f);
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.m(), dest.get<aidl::hidl2aidl::test::SafeUnionBar::m>());
}

TEST_F(Hidl2aidlTranslateTest, ArrayFoo) {
    aidl::hidl2aidl::test::ArrayFoo dest;
    hidl2aidl::test::V1_2::ArrayFoo source;
    source.a[0] = 42;
    source.a[1] = 8;
    ASSERT_EQ(sizeof(source.e), 0u);
    ASSERT_TRUE(h2a::translate(source, &dest));
    ASSERT_EQ(12u, dest.a.size());
    EXPECT_EQ(source.a[0], dest.a[0]);
    EXPECT_EQ(source.a[1], dest.a[1]);
    EXPECT_EQ(source.a[2], dest.a[2]);
    EXPECT_EQ(source.a[11], dest.a[11]);
}

TEST_F(Hidl2aidlTranslateTest, ArrayFooEmpty) {
    aidl::hidl2aidl::test::ArrayFoo dest;
    hidl2aidl::test::V1_2::ArrayFoo source;
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(12u, dest.a.size());
    EXPECT_EQ(0, dest.a[0]);
}

TEST_F(Hidl2aidlTranslateTest, ArrayFooEnum) {
    aidl::hidl2aidl::test::ArrayFoo dest;
    hidl2aidl::test::V1_2::ArrayFoo source;
    source.c[0] = hidl2aidl::test::V1_1::Value::A;
    source.c[1] = hidl2aidl::test::V1_1::Value::B;
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(static_cast<aidl::hidl2aidl::test::Value>(source.c[0]), dest.c[0]);
    EXPECT_EQ(static_cast<aidl::hidl2aidl::test::Value>(source.c[1]), dest.c[1]);
}

TEST_F(Hidl2aidlTranslateTest, ArrayFooString) {
    aidl::hidl2aidl::test::ArrayFoo dest;
    hidl2aidl::test::V1_2::ArrayFoo source;
    source.d[0] = "hello";
    source.d[1] = "world";
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.d[0], dest.d[0]);
    EXPECT_EQ(source.d[1], dest.d[1]);
}

TEST_F(Hidl2aidlTranslateTest, VectorFoo) {
    aidl::hidl2aidl::test::VectorFoo dest;
    hidl2aidl::test::V1_2::VectorFoo source;
    source.a = {42, 8};
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.a[0], dest.a[0]);
    EXPECT_EQ(source.a[1], dest.a[1]);
}

TEST_F(Hidl2aidlTranslateTest, VectorFooEmpty) {
    aidl::hidl2aidl::test::VectorFoo dest;
    hidl2aidl::test::V1_2::VectorFoo source;
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(0u, dest.a.size());
}

TEST_F(Hidl2aidlTranslateTest, VectorFooUnsigned) {
    aidl::hidl2aidl::test::VectorFoo dest;
    hidl2aidl::test::V1_2::VectorFoo source;
    // source.a is uint32_t, dest.a is int32_t
    source.b = {12, 0xf0000000};
    ASSERT_FALSE(h2a::translate(source, &dest));
}

TEST_F(Hidl2aidlTranslateTest, VectorFooEnum) {
    aidl::hidl2aidl::test::VectorFoo dest;
    hidl2aidl::test::V1_2::VectorFoo source;
    source.c = {hidl2aidl::test::V1_1::Value::A, hidl2aidl::test::V1_1::Value::B};
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(static_cast<aidl::hidl2aidl::test::Value>(source.c[0]), dest.c[0]);
    EXPECT_EQ(static_cast<aidl::hidl2aidl::test::Value>(source.c[1]), dest.c[1]);
}

TEST_F(Hidl2aidlTranslateTest, VectorFooString) {
    aidl::hidl2aidl::test::VectorFoo dest;
    hidl2aidl::test::V1_2::VectorFoo source;
    source.d = {"hello", "world"};
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.d[0], dest.d[0]);
    EXPECT_EQ(source.d[1], dest.d[1]);
}

TEST_F(Hidl2aidlTranslateTest, ExtensionArrayFoo) {
    aidl::hidl2aidl::test::extension::ArrayFoo dest;
    hidl2aidl::test::extension::V1_2::ArrayFoo source;
    source.e[0] = 12;
    source.e[1] = 2;
    ASSERT_TRUE(h2a::translate(source, &dest));
    EXPECT_EQ(source.e[0], dest.e[0]);
    EXPECT_EQ(source.e[1], dest.e[1]);
}

}  // namespace android
