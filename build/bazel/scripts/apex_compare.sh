#! /bin/bash -eu

# Compares two APEX files.
# This script is aimed at regression testing. It allows to compare an
# APEX target built by Bazel to the same target built by Soong.
# The first of its arguments is the reference APEX (the one built by
# Soong), the second is "our" APEX (built by Bazel).
#
# An APEX is a ZIP archive, so we treat each APEX as a file system and
# compare these two file systems. The script displays:
#  - missing files (those in the reference APEX missing from our APEX)
#  - extra files (those only in our APEX)
#  - for each file present in both, their difference.
# The main part of an APEX is an image file (payload.img), which is an
# image of a filesystem in EXT2 format. The script "mounts" such image
# and then compares them side by side.
#
# This script relies on the presence of an executable (binary/script)
# to "mount" a file of certain formats as file systems. It runs this
# executable as follows:
# * mount ZIPFILE at DIR:
#    view_file_as_fs zip ZIPFILE DIR
# * unmount ZIPFILE at DIR:
#    view_file_as_fs -u zip DIR
# * mount EXT2 image IMGFILE at DIR:
#    view_file_as_fs ext2 IMGFILE DIR
# * unmount EXT2 image IMGFILE at DIR:
#    view_file_as_fs -u ext2 DIR
#

function die() { format=$1; shift; printf "$format\n" $@; exit 1; }

# Delouse
(($# == 2)) || die "usage: ${0##*/} REF_APEX OUR_APEX"
declare -r ref_apex=$1 our_apex=$2
for f in $ref_apex $our_apex; do
    [[ -f $f ]] || die "$f does not exist"
done

# Maybe we are lucky.
cmp -s $ref_apex $our_apex && exit

declare -r file_as_fs_viewer=$(which view_file_as_fs)
if [[ -z "${file_as_fs_viewer}" ]]; then
    cat <<"EOF"
You need to have file-as-filesystem viewer application `view_file_as_fs`
on the PATH. If you have FUSE's fuse-ext2 and fuse-zip installed, you
can the following script below view_file_as_fs:

#!/bin/bash -eu
#
# Mounts a file as a read-only filesystem or unmounts such previously
# mounted file system.
# This script can mount a zip file or an file containing an ext2 image
# as a file system. It requires the presence of fuse-zip and fuse-ext2
# FUSE packages.
function die() { format=$1; shift; printf "$format\n" $@; exit 1; }
function usage() {
    die "Usage:\n ${0##*/} {ext2|zip} FILE MOUNT-POINT\nor\n ${0##*/} -u {ext2|zip} MOUNT-POINT"
}

declare umount=
while getopts "u" opt; do
    case $opt in
      u) umount=t ;;
      ?) usage
    esac
done

shift $(($OPTIND-1))
if [[ -n "$umount" ]]; then
    (($#==2)) || usage
    mount | grep -q "on $2 " && umount "$2"
else
    (($#==3)) || usage
    declare -r file="$2" mt="$3"
    [[ -d "$mt" && -z "$(ls -1A $mt)" ]] || die "$mt should be an empty directory"
    case "$1" in
        ext2) fuse-ext2 "$file" "$mt" ;;
        zip)
            [[ -f $file ]] || die "$file is not a file"  # Because fuse-zip silently mounts it as empty
            fuse-zip "$file" "$mt" ;;
        *) usage ;;
    esac
fi
EOF
    exit 1
fi

# "Mounts" file as filesystem and prints the sorted list of files in it.
function mount_and_list() {
    $file_as_fs_viewer $1 $2 $3 2>/dev/null
    find $3 -type f -printf "%P\n"
}

function cleanup() {
    for d in $fuse_dir/*.img; do
        $file_as_fs_viewer -u ext2 $d || /bin/true
    done
    for d in $fuse_dir/*.apex; do
        $file_as_fs_viewer -u zip $d || /bin/true
    done
    rm -rf $fuse_dir
}

function dump_proto() {
    protoc --decode $1 $2
}

function dump_buildinfo() {
    dump_proto apex.proto.ApexBuildInfo system/apex/proto/apex_build_info.proto
}

function dump_apex_manifest() {
    dump_proto apex.proto.ApexManifest system/apex/proto/apex_manifest.proto
}

function compare_images() {
    local -r ref_img=$1 our_img=$2

    # Mount each APEX and save its sorted contents. Classify the contents
    mount_and_list ext2 $ref_img $fuse_dir/ref.img >$fuse_dir/ref.img.list
    mount_and_list ext2 $our_img $fuse_dir/our.img >$fuse_dir/our.img.list
    . <(classify $fuse_dir/ref.img.list $fuse_dir/our.img.list; /bin/true)

    # Now we have missing/extra/common holding respective file lists. Compare
    ((${#missing[@]}==0)) || \
      { printf "Missing image files:"; printf " %s" ${missing[@]}; printf "\n"; }
    ((${#extra[@]}==0)) || \
      { printf "Extra image files:"; printf " %s" ${extra[@]}; printf "\n"; }
    for f in "${common[@]}"; do
        cmp -s $fuse_dir/{ref,our}.img/$f && continue
        echo "    $f" in image differs:
        case $f in
            etc/init.rc)
                diff $fuse_dir/{ref,our}.img/$f || /bin/true
                ;;
            apex_manifest.pb)
                diff <(dump_apex_manifest <$fuse_dir/ref.img/$f) <(dump_apex_manifest <$fuse_dir/our.img/$f) || bin/true
                ;;
            *)
                # TODO: should do more than just size comparison.
                sizes=($(stat --format "%s" $fuse_dir/{ref,our}.img/$f))
                delta=$((${sizes[1]}-${sizes[0]}))
                (($delta==0)) || printf "      size differs: %d (%d)\n" ${sizes[1]} $delta
                ;;
        esac
    done
}

# Prints the script that sets `missing`/`extra`/`common` shell
# variable to an array containing corresponding files, i.e. its
# output is
#   declare declare -a missing=() extra=() common=()
#   missing+=(missing_file)
#   extra+=(extra_file)
#   common+=(common_file)
#   .....
function classify() {
    comm $1 $2 | sed -nr \
      -e '1ideclare -a missing=() extra=() common=()' \
      -e '/^\t\t/{s/\t\t(.*)/common+=(\1)/p;d}' \
      -e '/^\t/{s/^\t(.*)/extra+=(\1)/p;d}' \
      -e 's/(.*)/missing+=(\1)/p'; /bin/true
}

fuse_dir=$(mktemp -d --tmpdir apexfuse.XXXXX)
mkdir -p $fuse_dir/{our,ref}.{apex,img}
trap cleanup EXIT

# Mount each APEX and save its sorted contents. Classify the contents
mount_and_list zip $ref_apex $fuse_dir/ref.apex >$fuse_dir/ref.apex.list
mount_and_list zip $our_apex $fuse_dir/our.apex >$fuse_dir/our.apex.list
. <(classify $fuse_dir/ref.apex.list $fuse_dir/our.apex.list; /bin/true)

# Now we have missing/extra/common holding respective file lists. Compare
((${#missing[@]}==0)) || { printf "Missing files:"; printf " %s" ${missing[@]}; printf "\n"; }
((${#extra[@]}==0)) || { printf "Extra files:"; printf " %s" ${extra[@]}; printf "\n"; }

for f in "${common[@]}"; do
    cmp -s $fuse_dir/{ref,our}.apex/$f && continue
    # File differs, compare known file types intelligently
    case $f in
        AndroidManifest.xml)
            echo $f differs:
            diff \
              <(aapt dump xmltree $fuse_dir/ref.apex AndroidManifest.xml) \
              <(aapt dump xmltree $fuse_dir/our.apex AndroidManifest.xml) || /bin/true
            ;;
        apex_build_info.pb)
            echo $f differs:
            diff <(dump_buildinfo <$fuse_dir/ref.apex/$f) <(dump_buildinfo <$fuse_dir/our.apex/$f) || /bin/true
            ;;
        manifest.pb)
            echo $f differs:
            diff <(dump_apex_manifest <$fuse_dir/ref.apex/$f) <(dump_apex_manifest <$fuse_dir/our.apex/$f) || bin/true
            ;;
        apex_payload.img)
            echo image $f differs, mounting it:
            compare_images $fuse_dir/{ref,our}.apex/$f
            ;;
        META-INF/*)
            # Ignore these. They are derived from the rest
            # showing their difference does not help.
            ;;
        *) echo $f; diff $fuse_dir/{ref,our}.apex/$f || /bin/true
    esac
done
