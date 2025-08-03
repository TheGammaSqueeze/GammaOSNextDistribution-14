
#!/usr/bash
# Copyright (C) 2023 The Android Open Source Project
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

set -e
readonly script="$(dirname $0)/*.py"
readonly white='\033[1;37m'
readonly nocolor='\033[0m' # No Color

[[ $(type -P "pyformat") ]] || (echo "Run: pip install pyformat"; exit 1)
echo -e "${white}[ Run pyformat ]${nocolor}"
pyformat --in_place ${script}

[[ $(type -P "pytype") ]] || (echo "Run: pip install pytype"; exit 1)
echo -e "${white}[ Run pytype ]${nocolor}"
pytype ${script}
