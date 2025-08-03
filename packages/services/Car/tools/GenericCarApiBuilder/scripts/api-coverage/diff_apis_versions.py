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

# The following script will parse two source trees and list which APIs were added.
# Example Usage:
# python3 /ssd/master/packages/services/Car/tools/GenericCarApiBuilder/scripts/diff_apis_versions.py
# "/ssd/tm-qpr-dev" "/ssd/master"

import sys
import os
import subprocess
import re
import csv

from tempfile import NamedTemporaryFile

exempt_apis = [
    "equals",
    "hashCode",
    "finalize",
    "writeToParcel",
    "describeContents",
    "release",
    "onCarDisconnected",
    "toString",
    "dump"
]

# Strip the fully qualified names from argument names
# Ex. android.os.Parcel -> Parcel
def strip_param_prefixes(api):
    index = api.find("(")
    args = api[index+1:].split(",")
    stripped = [arg.split(".")[-1].strip() for arg in args]
    return api[:index+1] + ",".join(stripped)

# Format an API name into this format packageName.className#methodName()
def format_name(api):
    api_split = api.split()
    formatted = api_split[0] + "." + api_split[1] + "#" + "".join(api_split[3:])
    return strip_param_prefixes(formatted)

def strip_param_names(api):
    argGroup = re.search("\((.*)\)",api)
    if argGroup is None:
        return api
    arg = argGroup.group(0)
    new_arg = re.sub('[^ (]*?(?=\))|[^ ]*?(?=,)', "", arg)
    return re.sub("\((.*)\)", new_arg, api)

if (len(sys.argv) < 3):
    print("Need three arguments: <old repo location> <new repo location>")
    sys.exit(1)
oldDir = sys.argv[1]
newDir = sys.argv[2]

java_cmd= "java -jar " + newDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                  "/GenericCarApiBuilder.jar " \
                                  "--print-all-apis " \
                                  "--root-dir " + oldDir

java_cmd_2= "java -jar " + newDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                    "/GenericCarApiBuilder.jar " \
                                    "--print-all-apis " \
                                    "--root-dir " + newDir

processes = []
cmds = [java_cmd, java_cmd_2]
for cmd in cmds:
    f = NamedTemporaryFile()
    p = subprocess.Popen(cmd, shell=True, stdout=f)
    processes.append((p, f))

results = []
for p, f in processes:
    p.wait()
    f.seek(0)
    results.append(f.read().decode('utf-8').strip().split("\n"))
    f.close()

old_apis, new_apis = results[0], results[1]
old_apis = [strip_param_names(i) for i in old_apis]
new_apis = [strip_param_names(i) for i in new_apis]

new_apis = [format_name(i) for i in new_apis if i not in old_apis]

# Get the build numbers for the last 5 builds:
cmd_str = "f1-sql --csv_print_column_names=true --csv_output=true --input_file=buildnums-sql-query --output_file=\"buildnums-results.csv\" --print_queries=false;"
subprocess.run(cmd_str, shell=True)

build_nums = []
with open("buildnums-results.csv") as fp:
    next(fp)
    for row in fp:
        build_nums.append(row.strip())

for i in build_nums:
    print(i)

# Get the query results from F1
data_read = set()
for build_num in build_nums:
    f = open('api-coverage-sql-query', 'r')
    filedata = f.read()
    f.close()

    newdata = filedata.replace('build_num', build_num)

    f = open('api-coverage-sql-query', 'w')
    f.write(newdata)
    f.close()

    print(newdata)
    cmd_str = "f1-sql --csv_print_column_names=true --csv_output=true --input_file=api-coverage-sql-query --output_file=\"api-coverage-results.csv\" --print_queries=false;"
    subprocess.run(cmd_str, shell=True)

    # Writeback the old query data.
    f = open('api-coverage-sql-query', 'w')
    f.write(filedata)
    f.close()

    current_data = []
    with open("api-coverage-results.csv") as fp:
        for row in fp:
            current_data.append(strip_param_prefixes(row.replace("$", ".").replace("\"", "").strip()))

    if len(data_read) == 0:
        data_read = set(current_data)
        continue

    # Save the intersection of data over multiple runs.
    data_read = data_read.intersection(set(current_data))

print("**********THE FOLLOWING APIS HAVE BEEN ADDED FROM " + oldDir + " TO " + newDir + "************")
f = open("apis-without-coverage.txt", "w")
for api in data_read:
    if api in new_apis and not any(exempt in api for exempt in exempt_apis):
        print(api)
        f.write(api + "\n")

f.close()

# Clean up csv files
subprocess.run("rm api-coverage-results.csv", shell=True)
subprocess.run("rm buildnums-results.csv", shell=True)
