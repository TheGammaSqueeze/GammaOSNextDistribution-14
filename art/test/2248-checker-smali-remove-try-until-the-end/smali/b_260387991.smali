#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
.class public LB260387991;

.super Ljava/lang/Object;

# When eliminating the unnecessary try and its catch block we turn the
# TryBoundary instructions into Goto instructions. If one of these
# instructions is pointing to the exit block, we use its single
# predecessor instead. If this TryBoundary-turned-into-Goto instruction
# was the only one pointing to the Exit, we also have to update the dominators.

## CHECK-START: void B260387991.testInfiniteCatch() dead_code_elimination$initial (before)
## CHECK: TryBoundary
## CHECK: TryBoundary

## CHECK-START: void B260387991.testInfiniteCatch() dead_code_elimination$initial (after)
## CHECK-NOT: TryBoundary
.method public static testInfiniteCatch()V
   .registers 4
    const/4 v0, 0x2
    const/4 v1, 0x4
    :try_start
    div-int v0, v1, v0
    return-void
    :try_end
    .catchall {:try_start .. :try_end} :catch_all

    # Infinite catch block which does not lead to the exit block.
    :catch_all
    nop
    goto :catch_all
.end method
