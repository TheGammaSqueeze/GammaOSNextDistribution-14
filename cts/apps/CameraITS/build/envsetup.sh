# Copyright 2013 The Android Open Source Project
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

# This file should be sourced from bash. Sets environment variables for
# running tests, and also checks that a number of dependences are present
# and that the unit tests for the modules passed (indicating that the setup
# is correct).

export CAMERA_ITS_TOP=$PWD
echo "CAMERA_ITS_TOP=$PWD"

[[ "${BASH_SOURCE[0]}" != "${0}" ]] || \
    { echo ">> Script must be sourced with 'source $0'" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || \
    echo ">> Require adb executable to be in path" >&2

command -v python >/dev/null 2>&1 || \
    echo ">> Require python executable to be in path" >&2

python -V 2>&1 | grep -q "Python 3.*" || \
    echo ">> Require python version 3" >&2

for M in numpy PIL matplotlib scipy.stats scipy.spatial serial
do
    python -c "import $M" >/dev/null 2>&1 || \
        echo ">> Require Python $M module" >&2
done

for N in 'PIL Image' 'matplotlib pylab'
do
    IFS=' ' read module submodule <<< "$N"
    python -c "from $module import $submodule" >/dev/null 2>&1 || \
        echo ">> Require Python $module module $submodule submodule" >&2
done

CV2_VER=$(python -c "
try:
    import cv2
    print(cv2.__version__)
except:
    print(\"N/A\")
")

echo "$CV2_VER" | grep -q -e "^3.*" -e "^4.*" || \
    echo ">> Require python opencv version greater than 3 or 4. Got $CV2_VER" >&2

COLOUR_VER=$(python -c "
try:
    import colour
    print(colour.__version__)
except:
    print(\"N/A\")
")

echo "$COLOUR_VER" | grep -q -e "^0.4.*$" || \
    echo ">> Require python colour-science version 0.4.*, Got $COLOUR_VER" >&2

export PYTHONPATH="$PWD/utils:$PYTHONPATH"
export PYTHONPATH="$PWD/tests:$PYTHONPATH"

for M in sensor_fusion_utils capture_request_utils opencv_processing_utils image_processing_utils its_session_utils image_fov_utils zoom_capture_utils imu_processing_utils
do
    python "utils/${M}_tests.py" 2>&1 | grep -q "OK" || \
        echo ">> Unit test for $M failed" >&2
done

for M in run_all_unit_tests
do
    python "tools/$M.py" 2>&1 | grep -q "OK" || \
        echo ">> Unit test for $M failed" >&2
done

echo -e "\n*****Please execute below adb command on your dut before running the tests*****\n"
echo -e "adb -s <device_id> shell am compat enable ALLOW_TEST_API_ACCESS com.android.cts.verifier\n\n"
