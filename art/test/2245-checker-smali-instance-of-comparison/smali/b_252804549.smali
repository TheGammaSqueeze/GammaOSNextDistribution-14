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
.class public LB252804549;

.super Ljava/lang/Object;

## CHECK-START: int B252804549.compareInstanceOfWithTwo(java.lang.Object) builder (after)
## CHECK-DAG: <<Const2:i\d+>>     IntConstant 2
## CHECK-DAG: <<InstanceOf:z\d+>> InstanceOf
## CHECK-DAG: <<Eq:z\d+>>         Equal [<<InstanceOf>>,<<Const2>>]
## CHECK-DAG:                     If [<<Eq>>]
.method public static compareInstanceOfWithTwo(Ljava/lang/Object;)I
   .registers 2
   instance-of v0, p0, Ljava/lang/String;
   const/4 v1, 0x2
   # Compare instance-of with 2 (i.e. neither 0 nor 1)
   if-eq v0, v1, :Lequal
   const/4 v1, 0x3
   return v1
:Lequal
   return v1
.end method
