#! /bin/bash -eu

# Compare object files the linker used to build given binary for
# two different configurations.
# As an example, suppose we comnt to compare `adbd` binary that is
# included in `com.android.adbd` APEX. We first build this APEX
# with Soong and rename the build tree to `out.ref`:
#   $ m com.android.adbd && mv out out.ref
# Then we build it again with mixed build and rename the build tree
# to `out.mix`
#   $ m --bazel-mode-staging com.android.adbd && mv out out.mix
# Now we can run this script to compare `adbd` binaries between
# two builds as follows:
#   $ compare_elf.sh adbd out.ref out.mix
# Note that we refer to the first of the two build directories as
# 'reference' and to the second one as 'our'.
#
# There are two ways to specify the binaries to compare:
# * compare_elf.sh REFDIR REFELF OURDIR OURELF
#     Compare REFDIR/**/REFELF (i.e., the file in REFDIR whose path ends
#     with REFELF in REFDIR) to OURDIR/**/OUROELF
# * compare_elf.sh ELF REFDIR OURDIR
#     This is a shortcut:
#     if ELF ends with .so, the same as
#          compare_elf.sh REFDIR ELF OURDIR ELF
#     otherwise the same as
#          compare_elf.sh REFDIR ELF OURDIR ELF.unstripped
#
# Overall, the process is as follows:
#  * For each build, extract the list of the input objects and
#    map each such object's unique configuration-independent key
#  * Compare the maps. For each common key, use `elfdiff` to compare
#    the ELF files
function die() { format=$1; shift; printf "$format\n" $@; exit 1; }

case $# in
  3) declare -r refelf=$1 refdir=$2 ourdir=$3
    [[ ${ourelf:=$refelf} =~ .so$ ]] || ourelf=$ourelf.unstripped ;;
  4) declare -r refdir=$1 refelf=$2 ourdir=$3 ourelf=$4 ;;
  *) die "usage:\n ${0##*/} ELF REFDIR OURDIR\nor\n ${0##*/} REFDIR REFELF OURDIR OURELF" ;;
esac
[[ -d $refdir ]] || die "$refdir is not a build directory"
[[ -d $ourdir ]] || die "$outdir is not a build directory"

declare -r elf_input_files="${0%/*}"/elf_input_files.sh

# Outputs the script that initialize an associative array with
# given name that maps object keys to their paths inside the tree.
# Ignore prebuilts and .so files.
# Normalize library names as in Bazel they sometimes start with
# `liblib` instead of `lib` and may end with `_bp2build_library_static`
# It's a rather ugly sed script.
# Anyways, the output script looks like this:
#  declare -A <name>=(
#     ["libfoo.a(abc.o)"]="<path>/libfoo(abc.o)"
#     ....
#  )
function objects_map() {
    local -r name=$1 out_dir=$2 prefix="${3:-}"
    grep -v -e '^prebuilts/' -e '\.so$' | sed -nr \
     -e "1ideclare -A $name=(" \
     -e "s|^|$prefix|" \
     -e "s|^out/|$out_dir/|" \
     -e '/_bp2build_cc_library_static\.a/s|(.*)/(lib)?(lib[^/]*)(_bp2build_cc_library_static\.a)\((.+)\)$|["\3.a(\5)"]="\1/\2\3\4(\5)"|p' \
     -e '/_bp2build_cc_library_static\.a/!s|(.*)/(lib)?(lib[^/]*)\((.+)\)$|["\3(\4)"]="\1/\2\3(\4)"|p' \
     -e 's|(.*)/([^/]*\.s?o)$|["\2"]="\1/\2"|p' \
     -e '$i)'
}

declare -r reffiles=$(mktemp --suffix=.ref) ourfiles=$(mktemp --suffix=.our)
declare -r comparator=$(mktemp /tmp/elfdiff.XXXXXX)
trap 'rm -f $ourfiles $reffiles $comparator' EXIT

# Initialize `ref_objects` to be objects map for ref build
"$elf_input_files" $refelf $refdir >$reffiles || exit 1
. <(objects_map ref_objects $refdir <$reffiles )

# Initialize `our_objects` to be objects map for our build
"$elf_input_files" $ourelf $ourdir >$ourfiles || exit 1
declare -r bazel_prefix=out/bazel/output/execroot/__main__/
. <(objects_map our_objects $ourdir $bazel_prefix <$ourfiles )

# Minor re-keying fo `our_objects` (e.g., Soong's `main.o` is
# Bazel's libadbd__internal_root.lo(main.o)
declare -Ar equivalences=(
    ["libadbd__internal_root.lo(main.o)"]="main.o"
    ["libadbd__internal_root.lo(libbuildversion.o)"]="libbuildversion.a(libbuildversion.o)"
    ["crtend.o"]="crtend_android.o")
for k in "${!equivalences[@]}"; do
    if [[ -v "our_objects[$k]" ]]; then
        our_objects["${equivalences[$k]}"]="${our_objects[$k]}"
        unset "our_objects[$k]"
    fi
done

declare -a missing extra common
# Compare the keys from `ref_objects` and `our_objects` and output the script
# to initialize `missing`, `extra` and `common` arrays to resp. only in
# `ref_objects`, only in `sour_objects`, and common
function classify() {
  comm <(printf "%s\n" "${!ref_objects[@]}" | sort) <(printf "%s\n" "${!our_objects[@]}" | sort) \
    | sed -nr '/^\t\t/{s|^\t\t(.*)|common+=("\1")|p;d};/^\t/{s|^\t(.*)|extra+=("\1")|p;d};s|(.*)|missing+=("\1")|p'
}

. <(classify)
if [[ -v missing ]]; then
    printf "The following input object files are missing:\n"
    for o in "${missing[@]}"; do
        printf "  %s\n" "${ref_objects[$o]}"
    done
fi

if [[ -v extra ]]; then
    printf "The following input object files are extra:\n"
    for o in "${extra[@]}"; do
        printf "  %s\n" "${our_objects[$o]}"
    done
fi

# Build the ELF files comparator, it is Go binary.
declare -r elfdiff=android/bazel/mkcompare/elfdiff/...
GOWORK=$PWD/build/bazel/mkcompare/go.work go build -o $comparator $elfdiff || exit 1

# Output ELF file pairs to compare and feed them the parallel executor.
for o in "${common[@]}"; do echo "${ref_objects[$o]} ${our_objects[$o]}"; done |\
  parallel --colsep ' ' $comparator {1} {2}
