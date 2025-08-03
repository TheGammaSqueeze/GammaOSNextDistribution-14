/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "dex/proto_reference.h"
#include <vector>

#include "dex/dex_file_types.h"
#include "dex/test_dex_file_builder.h"
#include "gtest/gtest.h"

namespace art {

TEST(ProtoReference, WithinOneDexFile) {
    TestDexFileBuilder builder;
    builder.AddMethod("LClass", "()I", "sideEffect2");
    builder.AddMethod("LClass", "(I)Ljava/lang/String;", "toString");
    builder.AddMethod("LClass", "(II)Ljava/lang/String;", "toString");
    builder.AddMethod("LClass", "(IJ)Ljava/lang/String;", "toString");
    builder.AddMethod("LClass", "(JJ)Ljava/lang/String;", "toString");
    builder.AddMethod("LClass", "()V", "sideEffect1");

    std::unique_ptr<const DexFile> dex_file = builder.Build("fake location");
    const size_t num_protos = 6u;
    EXPECT_EQ(num_protos, dex_file->NumProtoIds());

    std::vector<ProtoReference> protos;

    for (size_t i = 0; i < num_protos; ++i) {
        protos.emplace_back(ProtoReference(dex_file.get(), dex::ProtoIndex(i)));
    }

    ProtoReferenceValueComparator cmp;
    for (size_t i = 0; i < num_protos; ++i) {
        for (size_t j = 0; j < num_protos; ++j) {
            EXPECT_EQ(cmp(protos[i], protos[j]), i < j)
                << "Inconsistent at i=" << i << " and j=" << j;
        }
    }
}

TEST(ProtoReference, AcrossDifferentDexFiles) {
    TestDexFileBuilder builder1;
    builder1.AddMethod("LClass", "()I", "sideEffect2");
    builder1.AddMethod("LClass", "(I)Ljava/lang/String;", "toString");
    builder1.AddMethod("LClass", "(II)Ljava/lang/String;", "toString");
    builder1.AddMethod("LClass", "(IJ)Ljava/lang/String;", "toString");
    builder1.AddMethod("LClass", "(IJZ)Ljava/lang/String;", "toString");
    builder1.AddMethod("LClass", "()V", "sideEffect1");

    std::unique_ptr<const DexFile> dex_file1 = builder1.Build("fake location");
    EXPECT_EQ(6u, dex_file1->NumProtoIds());

    TestDexFileBuilder builder2;
    builder2.AddMethod("LClass2", "(IJ)Ljava/lang/String;", "toString");
    builder2.AddMethod("LClass2", "()V", "sideEffect1");
    builder2.AddMethod("LClass2", "(I)V", "sideEffect2");

    std::unique_ptr<const DexFile> dex_file2 = builder2.Build("fake location 2");
    EXPECT_EQ(3u, dex_file2->NumProtoIds());

    ProtoReference V_dex1 = ProtoReference(dex_file1.get(), dex::ProtoIndex(5));
    ProtoReference V_dex2 = ProtoReference(dex_file2.get(), dex::ProtoIndex(1));

    ProtoReferenceValueComparator cmp;

    EXPECT_FALSE(cmp(V_dex1, V_dex2));
    EXPECT_FALSE(cmp(V_dex2, V_dex1));

    ProtoReference IString_dex1 = ProtoReference(dex_file1.get(), dex::ProtoIndex(1));
    ProtoReference IIString_dex1 = ProtoReference(dex_file1.get(), dex::ProtoIndex(2));
    ProtoReference IJString_dex1 = ProtoReference(dex_file1.get(), dex::ProtoIndex(3));
    ProtoReference IJZString_dex1 = ProtoReference(dex_file1.get(), dex::ProtoIndex(4));

    ProtoReference IJString_dex2 = ProtoReference(dex_file2.get(), dex::ProtoIndex(0));

    EXPECT_TRUE(cmp(IString_dex1, V_dex2));

    EXPECT_TRUE(cmp(IString_dex1, IJString_dex2));
    EXPECT_TRUE(cmp(IIString_dex1, IJString_dex2));
    EXPECT_FALSE(cmp(IJString_dex1, IJString_dex2));
    EXPECT_FALSE(cmp(IJString_dex2, IJString_dex1));
    EXPECT_FALSE(cmp(IJZString_dex1, IJString_dex2));

    EXPECT_TRUE(cmp(IJString_dex2, IJZString_dex1));
}

}  // namespace art
