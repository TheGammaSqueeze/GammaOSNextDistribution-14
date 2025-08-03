#!/bin/bash
#
# Run tests in this directory.
#

if [ -z "$ANDROID_BUILD_TOP" ]; then
    echo "Android build environment not set"
        exit -1
fi

# ensure we have mm
. $ANDROID_BUILD_TOP/build/envsetup.sh

mmma system/media -j

echo "waiting for device"

adb root && adb wait-for-device remount

# location of test files
testdir="/data/local/tmp/biquadTest"

echo "========================================"
echo "testing biquad filter"
adb shell mkdir -p $testdir
adb push $ANDROID_BUILD_TOP/cts/tests/tests/media/audio/res/raw/sinesweepraw.raw $testdir

adb push $OUT/system/bin/biquad_filter $testdir
for ch in {1..8}
    do
    adb shell $testdir/biquad_filter -ch:$ch $testdir/sinesweepraw.raw \
        $testdir/sinesweep_$((ch)).raw

    # two channel files should be identical to higher channel
    # computation (first 2 channels).
    if [[ "$ch" -gt 2 ]]
    then
        adb shell cmp $testdir/sinesweep_2.raw \
                $testdir/sinesweep_$((ch)).raw
    fi
done

adb shell rm -r $testdir
