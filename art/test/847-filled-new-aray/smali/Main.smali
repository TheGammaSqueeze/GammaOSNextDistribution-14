# Copyright 2023 The Android Open Source Project
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

.class LMain;
.super Ljava/lang/Object;

.method public static main([Ljava/lang/String;)V
.registers 1
    :try_start
    new-instance v0, LMissingClass;
    invoke-direct {v0}, LMissingClass;-><init>()V
    # The verifier used to fail on this instruction used to type v0 as a conflict,
    # because LSuperMissingClass was unresolved. This lead to the `move-result-object`
    # below to make the class hard fail.
    filled-new-array {v0}, [LSuperMissingClass;
    move-result-object v0
    invoke-static {v0}, LMain;->doCall([LSuperMissingClass;)V
    # Throw a NPE to signal we don't expect to enter here.
    const/4 v0, 0
    throw v0
    :try_end
    .catch Ljava/lang/NoClassDefFoundError; {:try_start .. :try_end} :catch_0
    :catch_0
    # NoClassDefFoundError expected
    return-void
.end method

.method public static doCall([LSuperMissingClass;)V
.registers 1
    return-void
.end method
