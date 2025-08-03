#!/usr/bin/env python3
#  Copyright (C) 2022 The Android Open Source Project
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
    argGroup = re.search("\((.*)\)",api)
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

options = ["--print-hidden-apis"]

java_cmd = javaHomeDir + "/bin/java -jar " + rootDir + \
           "/packages/services/Car/tools/GenericCarApiBuilder" \
           "/GenericCarApiBuilder.jar --root-dir " + rootDir + " " + " ".join(options)

new_hidden_apis = subprocess.check_output(java_cmd, shell=True).decode('utf-8').strip().split("\n")

# read existing hidden APIs
existing_hidden_apis_path = rootDir + "/packages/services/Car/tests/carservice_unit_test/res/raw" \
                                      "/car_hidden_apis.txt"

existing_hidden_apis = []
with open(existing_hidden_apis_path) as f:
    existing_hidden_apis = f.read().splitlines()

modified_hidden_api = set(new_hidden_apis)^set(existing_hidden_apis)

if len(modified_hidden_api) > 0:
    print("\nFollowing Hidden APIs are modified in this CL:")
    print("\n".join(modified_hidden_api))
    print("\n")
    print("\nPlease run following command to update hidden API list:")
    print("\ncd $ANDROID_BUILD_TOP && m -j GenericCarApiBuilder && GenericCarApiBuilder "
          "--update-hidden-apis")
    print("\n")
    sys.exit(1)