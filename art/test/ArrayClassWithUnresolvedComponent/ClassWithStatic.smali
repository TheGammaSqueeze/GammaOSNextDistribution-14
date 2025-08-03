# Copyright (C) 2022 The Android Open Source Project
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

.class public LClassWithStatic;

.super Ljava/lang/Object;

.method static constructor <clinit>()V
    .registers 1
    const-string v0, "[LClassWithMissingInterface;"
    invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;
    move-result-object v0
    sput-object v0, LClassWithStatic;->field:Ljava/lang/Class;
    return-void
.end method

.field public static field:Ljava/lang/Class;
