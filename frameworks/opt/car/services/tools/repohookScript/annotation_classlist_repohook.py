#!/usr/bin/env python3

#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import sys
import os
import subprocess
import re

from tempfile import NamedTemporaryFile
from pathlib import Path

# Helper method that strips out the parameter names of methods. This will allow users to change
# parameter names for hidden apis without mistaking them as having been removed.
# [^ ]* --> Negation set on SPACE character. This wll match everything until a SPACE.
# *?(?=\)) --> This means the character ')' will not be included in the match.
# [^ (]*?(?=\)) --> This will handle the last parameter at the end of a method signature.
# It excludes matching any '(' characters when there are no parameters, i.e. method().
# [^ ]*?(?=,) --> This will handle multiple parameters delimited by commas.
def strip_param_names(api):
    # get the arguments first
    argGroup = re.search("\((.*)\)", api)
    if argGroup is None:
        return api
    arg = argGroup.group(0)
    new_arg = re.sub('[^ (]*?(?=\))|[^ ]*?(?=,)', "", arg)
    return re.sub("\((.*)\)", new_arg, api)

rootDir = os.getenv("ANDROID_BUILD_TOP")
if rootDir is None or rootDir == "":
    # env variable is not set. Then use the arg passed as Git root
    rootDir = sys.argv[1]

javaHomeDir = os.getenv("JAVA_HOME")
if javaHomeDir is None or javaHomeDir == "":
    if Path(rootDir + '/prebuilts/jdk/jdk17/linux-x86').is_dir():
        javaHomeDir = rootDir + "/prebuilts/jdk/jdk17/linux-x86"
    else:
        print("$JAVA_HOME is not set. Please use source build/envsetup.sh` in $ANDROID_BUILD_TOP")
        sys.exit(1)

# Marker is set in GenerateApi.java class and should not be changed.
marker = "Start-"
options = ["--print-non-hidden-classes-CSHS",
           "--print-addedin-without-requires-api-in-CSHS"]

java_cmd = javaHomeDir + "/bin/java -jar " + rootDir + \
           "/packages/services/Car/tools/GenericCarApiBuilder" \
           "/GenericCarApiBuilder.jar --root-dir " + rootDir + " " + " ".join(options)

all_data = subprocess.check_output(java_cmd, shell=True).decode('utf-8').strip().split("\n")
all_results = []
marker_index = []
for i in range(len(all_data)):
    if all_data[i].replace(marker, "") in options:
        marker_index.append(i)

previous_mark = 0
for mark in marker_index:
    if mark > previous_mark:
        all_results.append(all_data[previous_mark+1:mark])
        previous_mark = mark
all_results.append(all_data[previous_mark+1:])

# Update this line when adding more options
new_class_list = all_results[0]
incorrect_addedin_api_usage_in_CSHS_errors = all_results[1]

existing_CSHS_classes_path = rootDir + "/frameworks/opt/car/services/builtInServices/tests/" \
                                          "res/raw/CSHS_classes.txt"
existing_class_list = []
with open(existing_CSHS_classes_path) as f:
    existing_class_list.extend(f.read().splitlines())

# Find the diff in both class list
extra_new_classes = [i for i in new_class_list if i not in existing_class_list]
extra_deleted_classes = [i for i in existing_class_list if i not in new_class_list]

# Print error is there is any class added or removed without changing test
error = ""
if len(extra_deleted_classes) > 0:
    error = error + "Following Classes are deleted \n" + "\n".join(extra_deleted_classes)
if len(extra_new_classes) > 0:
    error = error + "\n\nFollowing new classes are added \n" + "\n".join(extra_new_classes)

if error != "":
    print(error)
    print("\nRun following command to generate classlist for annotation test")
    print("cd $ANDROID_BUILD_TOP && m -j GenericCarApiBuilder && GenericCarApiBuilder "
          "--update-non-hidden-classes-CSHS")
    print("\nThen run following test to make sure classes are properly annotated")
    print("atest com.android.server.wm.AnnotationTest")
    sys.exit(1)

if len(incorrect_addedin_api_usage_in_CSHS_errors) > 0:
    print("\nFollowing APIs are missing RequiresAPI annotations. See "
          "go/car-api-version-annotation#using-requiresapi-for-version-check")
    print("\n".join(incorrect_addedin_api_usage_in_CSHS_errors))
    sys.exit(1)
