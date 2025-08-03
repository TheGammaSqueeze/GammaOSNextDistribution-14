#!/bin/bash -eux

source "$(dirname $0)/target_lists.sh"
cd "$(dirname $0)/../../.."
OUT_DIR=$(realpath ${OUT_DIR:-out})
DIST_DIR=$(realpath ${DIST_DIR:-out/dist})


read -ra PRODUCTS <<<"$(build/soong/soong_ui.bash --dumpvar-mode all_named_products)"

FAILED_PRODUCTS=()
PRODUCTS_WITH_BP2BUILD_DIFFS=()

function report {
  # Turn off -x so that we can see the printfs more clearly
  set +x
  # check if FAILED_PRODUCTS is not empty
  if (( ${#FAILED_PRODUCTS[@]} )); then
    printf "Failed products:\n" >&2
    printf '%s\n' "${FAILED_PRODUCTS[@]}" >&2
  fi
  if (( ${#PRODUCTS_WITH_BP2BUILD_DIFFS[@]} )); then
    printf "\n\nProducts that produced different bp2build files from aosp_arm64:\n" >&2
    printf '%s\n' "${PRODUCTS_WITH_BP2BUILD_DIFFS[@]}" >&2

    # TODO(b/261023967): Don't fail the build until every product is OK and we want to prevent backsliding.
    # exit 1
  fi

  # TODO(b/262192655): Support riscv64 products in Bazel.
  for product in "${FAILED_PRODUCTS[@]}"; do
    if [[ "$product" != *"riscv64"* ]]; then
      exit 1
    fi
  done
}

trap report EXIT

rm -rf "${DIST_DIR}/multiproduct_analysis"
mkdir -p "${DIST_DIR}/multiproduct_analysis"

# Create zip of the bp2build files for aosp_arm64. We'll check that all other products produce
# identical bp2build files.
# We have to run tar and gzip as separate commands because tar with -z doesn't provide an option
# to not include a timestamp in the gzip header. (--mtime is only for the tar parts, not gzip)
export TARGET_PRODUCT="aosp_arm64"
build/soong/soong_ui.bash --make-mode --skip-soong-tests bp2build
tar c --mtime='1970-01-01' -C out/soong/bp2build . | gzip -n > "${DIST_DIR}/multiproduct_analysis/reference_bp2build_files_aosp_arm64.tar.gz"

total=${#PRODUCTS[@]}
count=1

for product in "${PRODUCTS[@]}"; do
  echo "Product ${count}/${total}: ${product}"

  # Ensure that all processes later use the same TARGET_PRODUCT.
  export TARGET_PRODUCT="${product}"

  # Re-run product config and bp2build for every TARGET_PRODUCT.
  build/soong/soong_ui.bash --make-mode --skip-soong-tests bp2build
  # Remove the ninja_build output marker file to communicate to buildbot that this is not a regular Ninja build, and its
  # output should not be parsed as such.
  rm -f out/ninja_build

  rm -f out/multiproduct_analysis_current_bp2build_files.tar.gz
  tar c --mtime='1970-01-01' -C out/soong/bp2build . | gzip -n > "${DIST_DIR}/multiproduct_analysis/bp2build_files_${product}.tar.gz"
  if diff -q "${DIST_DIR}/multiproduct_analysis/bp2build_files_${product}.tar.gz" "${DIST_DIR}/multiproduct_analysis/reference_bp2build_files_aosp_arm64.tar.gz"; then
    rm -f "${DIST_DIR}/multiproduct_analysis/bp2build_files_${product}.tar.gz"
  else
    PRODUCTS_WITH_BP2BUILD_DIFFS+=("${product}")
  fi

  STARTUP_FLAGS=(
    # Keep the Bazel server alive, package cache hot and reduce excessive I/O
    # and wall time by ensuring that max_idle_secs is longer than bp2build which
    # runs in every loop. bp2build takes ~20 seconds to run, so set this to a
    # minute to account for resource contention, but still ensure that the bazel
    # server doesn't stick around after.
    --max_idle_secs=60
  )

  FLAGS=(
    --config=bp2build
    --config=ci
    --nobuild
    --keep_going
  )

  build/bazel/bin/bazel ${STARTUP_FLAGS[@]} build ${FLAGS[@]} --config=linux_x86_64 -- ${BUILD_TARGETS} || \
    FAILED_PRODUCTS+=("${product} --config=linux_x86_64")

  build/bazel/bin/bazel ${STARTUP_FLAGS[@]} build ${FLAGS[@]} --config=android -- ${BUILD_TARGETS} || \
    FAILED_PRODUCTS+=("${product} --config=android")

  count=$((count+1))
done

