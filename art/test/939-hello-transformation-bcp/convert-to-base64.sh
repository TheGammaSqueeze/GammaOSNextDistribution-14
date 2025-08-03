#
# Copyright (C) 2021 The Android Open Source Project
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

#!/usr/bin/bash

# This script prints two base64 encoded strings to be pasted in Main.java.

# The first string is base64 encoded src-optional/java/util/OptionalLong.java.

# The second is base64 encoded dex, compiled from
# src-optional/java/util/OptionalLong.class, which in its turn compiled from
# src-optional/java/util/OptionalLong.java.

set -e

if [ -t 1 ]; then
  # Color sequences if terminal is a tty.
  red='\033[0;31m'
  green='\033[0;32m'
  yellow='\033[0;33m'
  magenta='\033[0;35m'
  nc='\033[0m'
fi

function help {
  cat << EOF
Usage: $0 -d8 $ANDROID_HOME/build-tools/*/d8 \\
          -android-jar $ANDROID_HOME/platforms/android-*/android.jar

This script automates regeneration of CLASS_BYTES and DEX_BYTES variables in Main.java
EOF

  exit 0
}

while [ $# -gt 0 ]; do
  key="$1"

  case $key in
    -d8)
      d8="$2"
      shift
      shift
      ;;
    -android-jar)
      android_jar="$2"
      shift
      shift
      ;;
    -h|--help)
      help
      ;;
  esac
done

if [ -z $d8 ]; then
  printf "${red}No path to d8 executable is specified${nc}\n"
  help
fi

if [ -z $android_jar ]; then
  printf "${red}No path to android.jar specified${nc}\n"
  help
fi

if [ ! -f "src-optional/java/util/OptionalLong.java" ]; then
  printf "${red}src-optional/OptionalLong.java does not exist${nc}\n"
  exit 1
fi

printf "${green}Compiling OptionalLong.java... ${nc}"
javac  -source 8 -target 8 src-optional/java/util/OptionalLong.java 1>/dev/null 2>/dev/null
$d8 --lib $android_jar --release --output . src-optional/java/util/*.class
printf "${green}Done\n${nc}\n"


printf "CLASS_BYTES to be pasted in src/Main.java are below:\n"
printf "${yellow}8<------------------------------------------------------------------------------${nc}\n"
cat src-optional/java/util/OptionalLong.java | base64 | sed "s/\(.*\)/\"\1\" \+/g"
printf "${yellow}8<------------------------------------------------------------------------------${nc}\n\n\n"

printf "DEX_BYTES to be pasted in src/Main.java are below:\n"
printf "${yellow}8<------------------------------------------------------------------------------${nc}\n"
cat classes.dex | base64 | sed "s/\(.*\)/\"\1\" \+/g"
printf "${yellow}8<------------------------------------------------------------------------------${nc}\n\n\n"

printf "${green}Cleaning up...${nc} "
rm -f src-optional/java/util/OptionalLong.class
rm -f classes.dex
printf "${green}Done${nc}\n"
