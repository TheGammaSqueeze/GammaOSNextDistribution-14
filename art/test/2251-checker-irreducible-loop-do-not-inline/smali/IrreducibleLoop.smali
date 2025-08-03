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

.class public LIrreducibleLoop;

.super Ljava/lang/Object;

# Back-edges in the ascii-art graphs are represented with dash '-'.
#
# Check that testDoNotInlineInner has a simple irreducible loop
#
#        entry
#       /    \
#      /      \
# loop_entry   \
#    /    \-    \
# try_start\-    \
#           other_loop_entry
#
# Consistency check: we didn't optimize away the irreducible loop
## CHECK-START: java.lang.Object IrreducibleLoop.testDoNotInlineInner(java.lang.Object) register (after)
## CHECK: irreducible:true
#
# We shouldn't inline `inner`.
## CHECK-START: java.lang.Object IrreducibleLoop.testDoNotInlineInner(java.lang.Object) inliner (before)
## CHECK: InvokeStaticOrDirect method_name:Main.inner
#
## CHECK-START: java.lang.Object IrreducibleLoop.testDoNotInlineInner(java.lang.Object) inliner (after)
## CHECK: InvokeStaticOrDirect method_name:Main.inner
.method public static testDoNotInlineInner(Ljava/lang/Object;)Ljava/lang/Object;
  .registers 3
  const/16 v0, 42
  const/16 v1, 21
  # Irreducible loop
  if-eq v1, v0, :other_loop_entry
  :loop_entry
  if-ne v1, v0, :continue
  add-int v0, v0, v0
  :other_loop_entry
  add-int v0, v0, v0
  goto :loop_entry

  :continue
  invoke-static {p0}, LMain;->inner(Ljava/lang/Object;)Ljava/lang/Object;
  move-result-object v0
  return-object v0
.end method
