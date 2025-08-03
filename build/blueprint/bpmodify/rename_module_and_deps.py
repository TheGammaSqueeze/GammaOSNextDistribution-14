#!/usr/bin/env python3
#
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
"""A tool to run bpmodify for a given module in order to rename it and all references to it"""
import os
import subprocess
import sys


def main():
  if len(sys.argv) < 2:
    print("Usage: rename_module_and_deps <pathToModule1,pathToModule2,...>")
    return

  modulePaths = sys.argv[1].split(",")
  replacementsList = []
  colonReplacementsList = []

  for modulePath in modulePaths:
    moduleName = modulePath.split("/")[-1]
    replacementsList.append(moduleName + "=" + moduleName + "_lib")
    # add in the colon replacement
    colonReplaceString = ":" + moduleName + "=" + ":" + moduleName + "_lib"
    replacementsList.append(colonReplaceString)
    colonReplacementsList.append(colonReplaceString)

  replacementsString = ",".join(replacementsList)
  colonReplacementsString = ",".join(colonReplacementsList)
  buildTop = os.getenv("ANDROID_BUILD_TOP")

  if not buildTop:
    raise Exception(
        "$ANDROID_BUILD_TOP not found in environment. Have you run lunch?")

  rename_deps_cmd = f"{buildTop}/prebuilts/go/linux-x86/bin/go run bpmodify.go -w -m=* -property=static_libs,deps,required,test_suites,name,host,libs,data_bins,data_native_bins,tools,shared_libs,file_contexts,target.not_windows.required,target.android.required,target.platform.required -replace-property={replacementsString} {buildTop}"
  print(rename_deps_cmd)
  subprocess.check_output(rename_deps_cmd, shell=True)

  # Some properties (for example,  data ), refer to files. Such properties may also refer to a filegroup module by prefixing it with a colon. Replacing these module references must thus be done separately.
  colon_rename_deps_cmd = f"{buildTop}/prebuilts/go/linux-x86/bin/go run bpmodify.go -w -m=* -property=data -replace-property={colonReplacementsString} {buildTop}"
  print(colon_rename_deps_cmd)
  subprocess.check_output(colon_rename_deps_cmd, shell=True)


if __name__ == "__main__":
  main()
