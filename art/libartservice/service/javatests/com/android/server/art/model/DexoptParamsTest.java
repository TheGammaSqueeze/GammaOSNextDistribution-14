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
 * limitations under the License
 */

package com.android.server.art.model;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DexoptParamsTest {
    @Test
    public void testBuild() {
        new DexoptParams.Builder("install").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildEmptyReason() {
        new DexoptParams.Builder("").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildReasonVdex() {
        new DexoptParams.Builder("vdex").setCompilerFilter("speed").setPriorityClass(90).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildInvalidCompilerFilter() {
        new DexoptParams.Builder("install").setCompilerFilter("invalid").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildInvalidPriorityClass() {
        new DexoptParams.Builder("install").setPriorityClass(101).build();
    }

    @Test
    public void testBuildCustomReason() {
        new DexoptParams.Builder("custom").setCompilerFilter("speed").setPriorityClass(90).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildCustomReasonEmptyCompilerFilter() {
        new DexoptParams.Builder("custom").setPriorityClass(ArtFlags.PRIORITY_INTERACTIVE).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildCustomReasonEmptyPriorityClass() {
        new DexoptParams.Builder("custom").setCompilerFilter("speed").build();
    }

    @Test
    public void testSingleSplit() {
        new DexoptParams.Builder("install")
                .setFlags(ArtFlags.FLAG_FOR_PRIMARY_DEX | ArtFlags.FLAG_FOR_SINGLE_SPLIT)
                .setSplitName("split_0")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSingleSplitNoPrimaryFlag() {
        new DexoptParams.Builder("install")
                .setFlags(ArtFlags.FLAG_FOR_SINGLE_SPLIT)
                .setSplitName("split_0")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSingleSplitSecondaryFlag() {
        new DexoptParams.Builder("install")
                .setFlags(ArtFlags.FLAG_FOR_PRIMARY_DEX | ArtFlags.FLAG_FOR_SECONDARY_DEX
                        | ArtFlags.FLAG_FOR_SINGLE_SPLIT)
                .setSplitName("split_0")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSingleSplitDependenciesFlag() {
        new DexoptParams.Builder("install")
                .setFlags(ArtFlags.FLAG_FOR_PRIMARY_DEX | ArtFlags.FLAG_SHOULD_INCLUDE_DEPENDENCIES
                        | ArtFlags.FLAG_FOR_SINGLE_SPLIT)
                .setSplitName("split_0")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSplitNameNoSingleSplitFlag() {
        new DexoptParams.Builder("install")
                .setFlags(ArtFlags.FLAG_FOR_PRIMARY_DEX)
                .setSplitName("split_0")
                .build();
    }

    @Test
    public void testToBuilder() {
        // Update this test with new fields if this assertion fails.
        assertThat(Arrays.stream(DexoptParams.class.getDeclaredFields())
                           .filter(field -> !Modifier.isStatic(field.getModifiers()))
                           .map(Field::getName)
                           .collect(Collectors.toList()))
                .containsExactly(
                        "mFlags", "mCompilerFilter", "mPriorityClass", "mReason", "mSplitName");

        DexoptParams params1 =
                new DexoptParams.Builder("install")
                        .setFlags(ArtFlags.FLAG_FOR_PRIMARY_DEX | ArtFlags.FLAG_FOR_SINGLE_SPLIT)
                        .setCompilerFilter("speed")
                        .setPriorityClass(90)
                        .setSplitName("split_0")
                        .build();

        DexoptParams params2 = params1.toBuilder().build();

        assertThat(params1.getFlags()).isEqualTo(params2.getFlags());
        assertThat(params1.getCompilerFilter()).isEqualTo(params2.getCompilerFilter());
        assertThat(params1.getPriorityClass()).isEqualTo(params2.getPriorityClass());
        assertThat(params1.getReason()).isEqualTo(params2.getReason());
        assertThat(params1.getSplitName()).isEqualTo(params2.getSplitName());
    }
}
