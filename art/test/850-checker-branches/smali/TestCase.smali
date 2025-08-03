# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

.class public LTestCase;

.super Ljava/lang/Object;

## CHECK-START: int TestCase.withBranch(boolean) select_generator (before)
## CHECK: If true_count:2 false_count:1
.method public static withBranch(Z)I
  .registers 2
  const/4 v0, 0x1
  if-nez v1, :return_2
  return v0
:return_2
  const/4 v0, 0x2
  return v0
.end method
