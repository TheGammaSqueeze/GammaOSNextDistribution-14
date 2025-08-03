#!/bin/bash -e

# List of files required in output. Every other file generated will be skipped.
OUTFILES=(
  com.android.apex.test.bar_stripped.v1.libvX.apex
  com.android.apex.test.bar_stripped.v2.libvY.apex
  com.android.apex.test.bar.v1.libvX.apex
  com.android.apex.test.bar.v2.libvY.apex
  com.android.apex.test.baz_stripped.v1.libvX.apex
  com.android.apex.test.foo_stripped.v1.libvX.apex
  com.android.apex.test.foo_stripped.v2.libvY.apex
  com.android.apex.test.foo.v1.libvX.apex
  com.android.apex.test.foo.v2.libvY.apex
  com.android.apex.test.pony_stripped.v1.libvZ.apex
  com.android.apex.test.pony.v1.libvZ.apex
  com.android.apex.test.sharedlibs_generated.v1.libvX.apex
  com.android.apex.test.sharedlibs_generated.v2.libvY.apex
  com.android.apex.test.sharedlibs_secondary_generated.v1.libvZ.apex
)

# "apex" type build targets to build.
APEX_TARGETS=(
  system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.bar:com.android.apex.test.bar
  system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.foo:com.android.apex.test.foo
  system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.pony:com.android.apex.test.pony
)

# "genrule" type build targets to build, and directory they are built from.
GENRULE_TARGETS=(
  system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.bar:com.android.apex.test.bar_stripped
  system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.baz:com.android.apex.test.baz_stripped
  system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.foo:com.android.apex.test.foo_stripped
  system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.pony:com.android.apex.test.pony_stripped
  system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.sharedlibs:com.android.apex.test.sharedlibs_generated
  system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.sharedlibs_secondary:com.android.apex.test.sharedlibs_secondary_generated
)

if [ ! -e "build/make/core/Makefile" ]; then
    echo "$0 must be run from the top of the tree"
    exit 1
fi

OUT_DIR=$(source build/envsetup.sh > /dev/null; TARGET_PRODUCT= get_build_var OUT_DIR)
DIST_DIR=$(source build/envsetup.sh > /dev/null; TARGET_PRODUCT= get_build_var DIST_DIR)
TMPDIR=$(source build/envsetup.sh > /dev/null; TARGET_PRODUCT= get_build_var TMPDIR)

manifestdirs=()

for t in "${APEX_TARGETS[@]}" "${GENRULE_TARGETS[@]}"; do
    IFS=: read -a ar <<< "${t}"
    manifestdirs+=( ${ar[0]})
done

manifestdirs=($(printf "%s\n" "${manifestdirs[@]}" | sort -u))

generated_artifacts=()

archs=(
  arm
  arm64
  x86
  x86_64
)

apexversions=(
  1
  2
)

libversions=(
  X
  Y
  Z
)

for arch in "${archs[@]}"; do
    for apexversion in "${apexversions[@]}"; do
        apexfingerprint="VERSION_${apexversion}"
        sed -i "s/#define FINGERPRINT .*/#define FINGERPRINT \"${apexfingerprint}\"/g" \
        system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.bar/bar_test.cc \
        system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.baz/baz_test.cc \
        system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.foo/foo_test.cc \
        system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.pony/pony_test.cc

        for d in "${manifestdirs[@]}"; do
            sed -i "s/  \"version\": .*/  \"version\": ${apexversion}/g" \
            ${d}/manifest.json
        done
        for libversion in "${libversions[@]}"; do
            # Check if we need to build this combination of versions.
            found=n
            for t in "${APEX_TARGETS[@]}" "${GENRULE_TARGETS[@]}"; do
                IFS=: read -a ar <<< "${t}"
                outfile=${ar[1]}.v${apexversion}.libv${libversion}.apex
                if printf '%s\n' "${OUTFILES[@]}" | grep -q -F "${outfile}"; then
                    found=y
                    break
                fi
            done
            if [ "${found}" != "y" ]; then
                # Skipping this combination.
                continue
            fi

            echo "Building combination arch: ${arch}, apexversion: ${apexversion}, libversion: ${libversion}"
            libfingerprint="VERSION_${libversion}"
            sed -i "s/#define FINGERPRINT .*/#define FINGERPRINT \"${libfingerprint}\"/g" \
            system/apex/tests/testdata/sharedlibs/build/sharedlibstest.cpp

            build/soong/soong_ui.bash \
                --make-mode \
                TARGET_PRODUCT=aosp_${arch} \
                dist sharedlibs_test

            for t in "${APEX_TARGETS[@]}" "${GENRULE_TARGETS[@]}"; do
                IFS=: read -a ar <<< "${t}"
                outfile=${ar[1]}.v${apexversion}.libv${libversion}.apex
                if printf '%s\n' "${OUTFILES[@]}" | grep -q -P "^${outfile}\$"; then
                    cp -v \
                    "${DIST_DIR}"/"${ar[1]}".apex \
                    system/apex/tests/testdata/sharedlibs/prebuilts/${arch}/${outfile}
                    generated_artifacts+=(system/apex/tests/testdata/sharedlibs/prebuilts/${arch}/${outfile})
                fi
            done
        done
    done
done

# Generate the Android.bp file for the prebuilts.
tmpfile=$(mktemp)

cat > "${tmpfile}" << EOF
// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This file is auto-generated by
// ./system/apex/tests/testdata/sharedlibs/build/build_artifacts.sh
// Do NOT edit manually.

package {
    default_applicable_licenses: ["Android-Apache-2.0"],
}
EOF

artifacts_filenames=()
for artifact in "${generated_artifacts[@]}"; do
    artifacts_filenames+=($(basename ${artifact}))
done

artifacts_filenames=($(printf '%s\n' "${artifacts_filenames[@]}" | sort -u))

for artifact in "${artifacts_filenames[@]}"; do
    outfile=$(basename "${artifact}")
    # remove .apex suffix
    rulename=${outfile%.apex}

    cat >> "${tmpfile}" << EOF

prebuilt_apex {
  name: "${rulename}_prebuilt",
  arch: {
EOF

    for arch in "${archs[@]}"; do
        cat >> "${tmpfile}" << EOF
    ${arch}: {
      src: "${arch}/${outfile}",
    },
EOF
    done

    cat >> "${tmpfile}" << EOF
  },
  filename: "${outfile}",
  installable: false,
}
EOF
done

mv "${tmpfile}" system/apex/tests/testdata/sharedlibs/prebuilts/Android.bp

# Restore the default version string to avoid bogus diffs.
sed -i "s/#define FINGERPRINT .*/#define FINGERPRINT \"VERSION_XXX\"/g" \
system/apex/tests/testdata/sharedlibs/build/sharedlibstest.cpp \
system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.bar/bar_test.cc \
system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.baz/baz_test.cc \
system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.foo/foo_test.cc \
system/apex/tests/testdata/sharedlibs/build/com.android.apex.test.pony/pony_test.cc

for d in "${manifestdirs[@]}"; do
    sed -i "s/  \"version\": .*/  \"version\": 1/g" \
    ${d}/manifest.json
done

ls -l "${generated_artifacts[@]}"
