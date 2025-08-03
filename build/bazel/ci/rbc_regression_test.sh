#!/bin/bash -u
# Regression test for the product and/or board configuration converter.
#
# Builds 'nothing' for a given product-variant twice: with product/board
# config makefiles converted to Starlark, and without such conversion.
# The generated Ninja files should be the same.
set -u

function die() {
    echo $@ >&2
    exit 1
}

function usage() {
    cat <<EOF >&2
Usage: $myname [-q] [-r] <product-variant> [product-variant ...]
  -q: Quiet. Suppress all output other than a failure message
  -r: Retain Ninja files
EOF
    exit 1
}

function build() {
    local -r flavor="$1"
    local -r product="$2"
    local -r variant="$3"
    shift 3
    command="build/soong/soong_ui.bash --make-mode TARGET_PRODUCT=$product TARGET_BUILD_VARIANT=$variant $@ nothing"
    if ! ANDROID_QUIET_BUILD=$quiet $command; then
        printf "%s-%s: %s build failed, actual command:\n  %s\n" $product $variant $flavor "$command" >&2
        exit 1
    fi
}

mypath=$(realpath "$0")
declare -r mydir=${mypath%/*/*/*/*}
declare -r myname=${mypath#${mydir}/}

flags_rbc=(RBC_PRODUCT_CONFIG=true)
quiet=
while getopts "qr" o; do
    case "${o}" in
        q) quiet=true ;;
        r) retain_files=t ;;
        *) usage ;;
    esac
done
shift $((OPTIND-1))
[[ $# -gt 0 ]] || usage

cd $mydir
rc=0
for arg in $@; do
    [[ "$arg" =~ ^([a-zA-Z0-9_]+)-([a-zA-Z0-9_]+)$ ]] || \
        die "Invalid product name: $arg. Example: aosp_arm64-userdebug"
    product="${BASH_REMATCH[1]}"
    variant="${BASH_REMATCH[2]}"
    ninja_files=(soong/build.${product}.ninja build-${product}.ninja build-${product}-package.ninja)

    # Build with converter, save Ninja files, build without it.
    saved_ninja_dir=out/ninja_rbc/${product}-${variant}
    build RBC $product $variant ${flags_rbc[@]} && \
      rm -rf $saved_ninja_dir && mkdir -p $saved_ninja_dir/soong && \
      (for f in ${ninja_files[@]}; do mv -f out/$f $saved_ninja_dir/$f || exit 1; done) && \
      build baseline $product $variant
    rc=$?

    # Compare Ninja files
    if ((rc==0)); then
        for f in "${ninja_files[@]}"; do
            diff_file=$(mktemp)
            diff out/$f $saved_ninja_dir/$f | head >& $diff_file
            if [[ -s $diff_file ]]; then
                echo ${product}-${variant}: "$f" is different '< make, > RBC):' >&2
                cat $diff_file >&2
                echo ...
                rc=1
            fi
            rm $diff_file
        done
    fi
    [[ -n "${retain_files:-}" ]] || rm -rf $saved_ninja_dir
done

((rc==0)) || printf "In order to reproduce the failures above, run\n  %s <product>-<variant>\n" $myname >&2
exit $rc
