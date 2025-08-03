#!/bin/bash
#
# Copyright (C) 2018 The Android Open Source Project
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
#

set -e
set -u

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)

usage() {
  echo -n "usage: $0 [-h] [-s bullseye|bullseye-cuttlefish|bullseye-rockpi|bullseye-server] "
  echo -n "[-a i386|amd64|armhf|arm64] -k /path/to/kernel "
  echo -n "-i /path/to/initramfs.gz [-d /path/to/dtb:subdir] "
  echo "[-m http://mirror/debian] [-n rootfs|disk] [-r initrd] [-e] [-g]"
  exit 1
}

mirror=http://ftp.debian.org/debian
embed_kernel_initrd_dtb=0
install_grub=0
suite=bullseye
arch=amd64

dtb_subdir=
initramfs=
kernel=
ramdisk=
disk=
dtb=

while getopts ":hs:a:m:n:r:k:O:i:d:eg" opt; do
  case "${opt}" in
    h)
      usage
      ;;
    s)
      if [[ "${OPTARG%-*}" != "bullseye" ]]; then
        echo "Invalid suite: ${OPTARG}" >&2
        usage
      fi
      suite="${OPTARG}"
      ;;
    a)
      arch="${OPTARG}"
      ;;
    m)
      mirror="${OPTARG}"
      ;;
    n)
      disk="${OPTARG}"
      ;;
    r)
      ramdisk="${OPTARG}"
      ;;
    k)
      kernel="${OPTARG}"
      ;;
    O)
      extradeb="${OPTARG}"
      ;;
    i)
      initramfs="${OPTARG}"
      ;;
    d)
      dtb="${OPTARG%:*}"
      if [ "${OPTARG#*:}" != "${dtb}" ]; then
        dtb_subdir="${OPTARG#*:}/"
      fi
      ;;
    e)
      embed_kernel_initrd_dtb=1
      ;;
    g)
      install_grub=1
      ;;
    \?)
      echo "Invalid option: ${OPTARG}" >&2
      usage
      ;;
    :)
      echo "Invalid option: ${OPTARG} requires an argument" >&2
      usage
      ;;
  esac
done

# Disable Debian's "persistent" network device renaming
cmdline="net.ifnames=0 rw 8250.nr_uarts=2 PATH=/usr/sbin:/bin:/usr/bin"
cmdline="${cmdline} embed_kernel_initrd_dtb=${embed_kernel_initrd_dtb}"
cmdline="${cmdline} install_grub=${install_grub}"

case "${arch}" in
  i386)
    cmdline="${cmdline} console=ttyS0 exitcode=/dev/ttyS1"
    machine="pc-i440fx-2.8,accel=kvm"
    qemu="qemu-system-i386"
    partguid="8303"
    cpu="max"
    ;;
  amd64)
    cmdline="${cmdline} console=ttyS0 exitcode=/dev/ttyS1"
    machine="pc-i440fx-2.8,accel=kvm"
    qemu="qemu-system-x86_64"
    partguid="8304"
    cpu="max"
    ;;
  armhf)
    cmdline="${cmdline} console=ttyAMA0 exitcode=/dev/ttyS0"
    machine="virt,gic-version=2"
    qemu="qemu-system-arm"
    partguid="8307"
    cpu="cortex-a15"
    ;;
  arm64)
    cmdline="${cmdline} console=ttyAMA0 exitcode=/dev/ttyS0"
    machine="virt,gic-version=2"
    qemu="qemu-system-aarch64"
    partguid="8305"
    cpu="cortex-a53" # "max" is too slow
    ;;
  *)
    echo "Invalid arch: ${OPTARG}" >&2
    usage
    ;;
esac

if [[ -z "${disk}" ]]; then
  if [[ "${install_grub}" = "1" ]]; then
    base_image_name=disk
  else
    base_image_name=rootfs
  fi
  disk="${base_image_name}.${arch}.${suite}.$(date +%Y%m%d)"
fi
disk=$(realpath "${disk}")

if [[ -z "${ramdisk}" ]]; then
  ramdisk="initrd.${arch}.${suite}.$(date +%Y%m%d)"
fi
ramdisk=$(realpath "${ramdisk}")

if [[ -z "${kernel}" ]]; then
  echo "$0: Path to kernel image must be specified (with '-k')"
  usage
elif [[ ! -e "${kernel}" ]]; then
  echo "$0: Kernel image not found at '${kernel}'"
  exit 2
fi

if [[ -z "${initramfs}" ]]; then
  echo "Path to initial ramdisk image must be specified (with '-i')"
  usage
elif [[ ! -e "${initramfs}" ]]; then
  echo "Initial ramdisk image not found at '${initramfs}'"
  exit 3
fi

# Sometimes it isn't obvious when the script fails
failure() {
  echo "Filesystem generation process failed." >&2
  rm -f "${disk}" "${ramdisk}"
}
trap failure ERR

# Import the package list for this release
packages=$(cpp "${SCRIPT_DIR}/rootfs/${suite}.list" | grep -v "^#" | xargs | tr -s ' ' ',')

# For the debootstrap intermediates
tmpdir=$(mktemp -d)
tmpdir_remove() {
  echo "Removing temporary files.." >&2
  sudo rm -rf "${tmpdir}"
}
trap tmpdir_remove EXIT

workdir="${tmpdir}/_"
mkdir "${workdir}"
chmod 0755 "${workdir}"
sudo chown root:root "${workdir}"

# Run the debootstrap first
cd "${workdir}"

retries=5
while ! sudo debootstrap --arch="${arch}" --variant=minbase --include="${packages}" \
        --foreign "${suite%-*}" . "${mirror}"; do
    retries=$((${retries} - 1))
    if [ ${retries} -le 0 ]; then
	failure
	exit 1
    fi
    echo "debootstrap failed - trying again - ${retries} retries left"
done

# Copy some bootstrapping scripts into the rootfs
sudo cp -a "${SCRIPT_DIR}"/rootfs/*.sh root/
sudo cp -a "${SCRIPT_DIR}"/rootfs/net_test.sh sbin/net_test.sh
sudo chown root:root sbin/net_test.sh

# Extract the ramdisk to bootstrap with to /
lz4 -lcd "${initramfs}" | sudo cpio -idum lib/modules/*

# Create /host, for the pivot_root and 9p mount use cases
sudo mkdir host

# debootstrap workaround: Run debootstrap in docker sometimes causes the
# /proc being a symlink in first stage. We need to fix the symlink to an empty
# directory.
if [ -L "${workdir}/proc" ]; then
  echo "/proc in debootstrap 1st stage is a symlink. Fixed!"
  sudo rm -f "${workdir}/proc"
  sudo mkdir "${workdir}/proc"
fi

# Leave the workdir, to build the filesystem
cd -

# For the initial ramdisk, and later for the final rootfs
mount=$(mktemp -d)
mount_remove() {
  rmdir "${mount}"
  tmpdir_remove
}
trap mount_remove EXIT

# The initial ramdisk filesystem must be <=512M, or QEMU's -initrd
# option won't touch it
initrd=$(mktemp)
initrd_remove() {
  rm -f "${initrd}"
  mount_remove
}
trap initrd_remove EXIT
truncate -s 512M "${initrd}"
/sbin/mke2fs -F -t ext4 -L ROOT "${initrd}"

# Mount the new filesystem locally
sudo mount -o loop -t ext4 "${initrd}" "${mount}"
image_unmount() {
  sudo umount "${mount}"
  initrd_remove
}
trap image_unmount EXIT

# Copy the patched debootstrap results into the new filesystem
sudo cp -a "${workdir}"/* "${mount}"
sudo rm -rf "${workdir}"

# Unmount the initial ramdisk
sudo umount "${mount}"
trap initrd_remove EXIT

if [[ "${install_grub}" = 1 ]]; then
  part_num=0
  # $1 partition size
  # $2 gpt partition type
  # $3 partition name
  # $4 bypass alignment checks (use on <1MB partitions only)
  # $5 partition attribute bit to set
  sgdisk() {
    part_num=$((part_num+1))
    [[ -n "${4:-}" ]] && prefix="-a1" || prefix=
    [[ -n "${5:-}" ]] && suffix="-A:${part_num}:set:$5" || suffix=
    /sbin/sgdisk ${prefix} \
      "-n:${part_num}:$1" "-t:${part_num}:$2" "-c:${part_num}:$3" \
      ${suffix} "${disk}" >/dev/null 2>&1
  }
  # If there's a bootloader, we need to make space for the GPT header, GPT
  # footer and EFI system partition (legacy boot is not supported)
  # Keep this simple - modern gdisk reserves 1MB for the GPT header and
  # assumes all partitions are 1MB aligned
  truncate -s "$((1 + 128 + 10 * 1024 + 1))M" "${disk}"
  /sbin/sgdisk --zap-all "${disk}" >/dev/null 2>&1
  # On RockPi devices, steal a bit of space at the start of the disk for
  # some special bootloader partitions. Some of these have to start/end
  # at specific offsets as well
  if [[ "${suite#*-}" = "rockpi" ]]; then
    # See https://opensource.rock-chips.com/wiki_Boot_option
    # Keep in sync with rootfs/*-rockpi.sh
    sgdisk "64:8127"   "8301"        "idbloader" "true"
    sgdisk "8128:+64"  "8301"        "uboot_env" "true"
    sgdisk "8M:+4M"    "8301"        "uboot"
    sgdisk "12M:+4M"   "8301"        "trust"
    sgdisk "16M:+1M"   "8301"        "misc"
    sgdisk "17M:+128M" "ef00"        "esp"       ""     "0"
    sgdisk "145M:0"    "8305"        "rootfs"    ""     "2"
    system_partition="6"
    rootfs_partition="7"
  else
    sgdisk "0:+128M"   "ef00"        "esp"       ""     "0"
    sgdisk "0:0"       "${partguid}" "rootfs"    ""     "2"
    system_partition="1"
    rootfs_partition="2"
  fi

  # Create an empty EFI system partition; it will be initialized later
  system_partition_start=$(partx -g -o START -s -n "${system_partition}" "${disk}" | xargs)
  system_partition_end=$(partx -g -o END -s -n "${system_partition}" "${disk}" | xargs)
  system_partition_num_sectors=$((${system_partition_end} - ${system_partition_start} + 1))
  system_partition_num_vfat_blocks=$((${system_partition_num_sectors} / 2))
  /sbin/mkfs.vfat -n SYSTEM -F 16 --offset=${system_partition_start} "${disk}" ${system_partition_num_vfat_blocks} >/dev/null
  # Copy the rootfs to just after the EFI system partition
  rootfs_partition_start=$(partx -g -o START -s -n "${rootfs_partition}" "${disk}" | xargs)
  rootfs_partition_end=$(partx -g -o END -s -n "${rootfs_partition}" "${disk}" | xargs)
  rootfs_partition_num_sectors=$((${rootfs_partition_end} - ${rootfs_partition_start} + 1))
  rootfs_partition_offset=$((${rootfs_partition_start} * 512))
  rootfs_partition_size=$((${rootfs_partition_num_sectors} * 512))
  dd if="${initrd}" of="${disk}" bs=512 seek="${rootfs_partition_start}" conv=fsync,notrunc 2>/dev/null
  /sbin/e2fsck -p -f "${disk}"?offset=${rootfs_partition_offset} || true
  disksize=$(stat -c %s "${disk}")
  /sbin/resize2fs "${disk}"?offset=${rootfs_partition_offset} ${rootfs_partition_num_sectors}s
  truncate -s "${disksize}" "${disk}"
  /sbin/sgdisk -e "${disk}"
  /sbin/e2fsck -p -f "${disk}"?offset=${rootfs_partition_offset} || true
  /sbin/e2fsck -fy "${disk}"?offset=${rootfs_partition_offset} || true
else
  # If there's no bootloader, the initrd is the disk image
  cp -a "${initrd}" "${disk}"
  truncate -s 10G "${disk}"
  /sbin/e2fsck -p -f "${disk}" || true
  /sbin/resize2fs "${disk}"
  system_partition=
  rootfs_partition="raw"
fi

# Create another fake block device for initrd.img writeout
raw_initrd=$(mktemp)
raw_initrd_remove() {
  rm -f "${raw_initrd}"
  initrd_remove
}
trap raw_initrd_remove EXIT
truncate -s 64M "${raw_initrd}"

# Get number of cores for qemu. Restrict the maximum value to 8.
qemucpucores=$(nproc)
if [[ ${qemucpucores} -gt 8 ]]; then
  qemucpucores=8
fi

# Complete the bootstrap process using QEMU and the specified kernel
${qemu} -machine "${machine}" -cpu "${cpu}" -m 2048 >&2 \
  -kernel "${kernel}" -initrd "${initrd}" -no-user-config -nodefaults \
  -no-reboot -display none -nographic -serial stdio -parallel none \
  -smp "${qemucpucores}",sockets="${qemucpucores}",cores=1,threads=1 \
  -object rng-random,id=objrng0,filename=/dev/urandom \
  -device virtio-rng-pci-non-transitional,rng=objrng0,id=rng0,max-bytes=1024,period=2000 \
  -drive file="${disk}",format=raw,if=none,aio=threads,id=drive-virtio-disk0 \
  -device virtio-blk-pci-non-transitional,scsi=off,drive=drive-virtio-disk0 \
  -drive file="${raw_initrd}",format=raw,if=none,aio=threads,id=drive-virtio-disk1 \
  -device virtio-blk-pci-non-transitional,scsi=off,drive=drive-virtio-disk1 \
  -chardev file,id=exitcode,path=exitcode \
  -device pci-serial,chardev=exitcode \
  -append "root=/dev/ram0 ramdisk_size=524288 init=/root/stage1.sh ${cmdline}"
[[ -s exitcode ]] && exitcode=$(cat exitcode | tr -d '\r') || exitcode=2
rm -f exitcode
if [ "${exitcode}" != "0" ]; then
  echo "Second stage debootstrap failed (err=${exitcode})"
  exit "${exitcode}"
fi

# Fix up any issues from the unclean shutdown
if [[ ${rootfs_partition} = "raw" ]]; then
    sudo e2fsck -p -f "${disk}" || true
else
    rootfs_partition_start=$(partx -g -o START -s -n "${rootfs_partition}" "${disk}" | xargs)
    rootfs_partition_end=$(partx -g -o END -s -n "${rootfs_partition}" "${disk}" | xargs)
    rootfs_partition_num_sectors=$((${rootfs_partition_end} - ${rootfs_partition_start} + 1))
    rootfs_partition_offset=$((${rootfs_partition_start} * 512))
    rootfs_partition_tempfile2=$(mktemp)
    dd if="${disk}" of="${rootfs_partition_tempfile2}" bs=512 skip=${rootfs_partition_start} count=${rootfs_partition_num_sectors}
    /sbin/e2fsck -p -f "${rootfs_partition_tempfile2}" || true
    dd if="${rootfs_partition_tempfile2}" of="${disk}" bs=512 seek=${rootfs_partition_start} count=${rootfs_partition_num_sectors} conv=fsync,notrunc
    rm -f "${rootfs_partition_tempfile2}"
    /sbin/e2fsck -fy "${disk}"?offset=${rootfs_partition_offset} || true
fi
if [[ -n "${system_partition}" ]]; then
  system_partition_start=$(partx -g -o START -s -n "${system_partition}" "${disk}" | xargs)
  system_partition_end=$(partx -g -o END -s -n "${system_partition}" "${disk}" | xargs)
  system_partition_num_sectors=$((${system_partition_end} - ${system_partition_start} + 1))
  system_partition_offset=$((${system_partition_start} * 512))
  system_partition_size=$((${system_partition_num_sectors} * 512))
  system_partition_tempfile=$(mktemp)
  dd if="${disk}" of="${system_partition_tempfile}" bs=512 skip=${system_partition_start} count=${system_partition_num_sectors}
  /sbin/fsck.vfat -a "${system_partition_tempfile}" || true
  dd if="${system_partition_tempfile}" of="${disk}" bs=512 seek=${system_partition_start} count=${system_partition_num_sectors} conv=fsync,notrunc
  rm -f "${system_partition_tempfile}"
fi

# New workdir for the initrd extraction
workdir="${tmpdir}/initrd"
mkdir "${workdir}"
chmod 0755 "${workdir}"
sudo chown root:root "${workdir}"

# Change into workdir to repack initramfs
cd "${workdir}"

# Process the initrd to remove kernel-specific metadata
kernel_version=$(basename $(lz4 -lcd "${raw_initrd}" | sudo cpio -idumv 2>&1 | grep usr/lib/modules/ - | head -n1))
lz4 -lcd "${raw_initrd}" | sudo cpio -idumv
sudo rm -rf usr/lib/modules
sudo mkdir -p usr/lib/modules

# Debian symlinks /usr/lib to /lib, but we'd prefer the other way around
# so that it more closely matches what happens in Android initramfs images.
# This enables 'cat ramdiskA.img ramdiskB.img >ramdiskC.img' to "just work".
sudo rm -f lib
sudo mv usr/lib lib
sudo ln -s /lib usr/lib

# Repack the ramdisk to the final output
find * | sudo cpio -H newc -o --quiet | lz4 -lc9 >"${ramdisk}"

# Pack another ramdisk with the combined artifacts, for boot testing
cat "${ramdisk}" "${initramfs}" >"${initrd}"

# Leave workdir to boot-test combined initrd
cd -

rootfs_partition_tempfile=$(mktemp)
# Mount the new filesystem locally
if [[ ${rootfs_partition} = "raw" ]]; then
    sudo mount -o loop -t ext4 "${disk}" "${mount}"
else
    rootfs_partition_start=$(partx -g -o START -s -n "${rootfs_partition}" "${disk}" | xargs)
    rootfs_partition_offset=$((${rootfs_partition_start} * 512))
    rootfs_partition_end=$(partx -g -o END -s -n "${rootfs_partition}" "${disk}" | xargs)
    rootfs_partition_num_sectors=$((${rootfs_partition_end} - ${rootfs_partition_start} + 1))
    dd if="${disk}" of="${rootfs_partition_tempfile}" bs=512 skip=${rootfs_partition_start} count=${rootfs_partition_num_sectors}
fi
image_unmount2() {
  sudo umount "${mount}"
  raw_initrd_remove
}
if [[ ${rootfs_partition} = "raw" ]]; then
    trap image_unmount2 EXIT
fi

# Embed the kernel and dtb images now, if requested
if [[ ${rootfs_partition} = "raw" ]]; then
    if [[ "${embed_kernel_initrd_dtb}" = "1" ]]; then
	if [ -n "${dtb}" ]; then
	    sudo mkdir -p "${mount}/boot/dtb/${dtb_subdir}"
	    sudo cp -a "${dtb}" "${mount}/boot/dtb/${dtb_subdir}"
	    sudo chown -R root:root "${mount}/boot/dtb/${dtb_subdir}"
	fi
	sudo cp -a "${kernel}" "${mount}/boot/vmlinuz-${kernel_version}"
	sudo chown root:root "${mount}/boot/vmlinuz-${kernel_version}"
    fi
    sudo cp -a "${SCRIPT_DIR}"/rootfs/cron-run-installer-script "${mount}/etc/cron.d/cron-run-installer-script"
    if [ -e "${extradeb}" ]; then
	sudo cp -a "${extradeb}" "${mount}/root/extradeb.tar.gz"
	sudo chown root:root "${mount}/root/extradeb.tar.gz"
    fi
else
    if [[ "${embed_kernel_initrd_dtb}" = "1" ]]; then
	if [ -n "${dtb}" ]; then
	    e2mkdir -G 0 -O 0 "${rootfs_partition_tempfile}":"/boot/dtb/${dtb_subdir}"
	    e2cp -G 0 -O 0 "${dtb}" "${rootfs_partition_tempfile}":"/boot/dtb/${dtb_subdir}"
	fi
	e2cp -G 0 -O 0 "${kernel}" "${rootfs_partition_tempfile}":"/boot/vmlinuz-${kernel_version}"
    fi
    e2cp -G 0 -O 0 "${SCRIPT_DIR}"/rootfs/cron-run-installer-script "${rootfs_partition_tempfile}":"/etc/cron.d/cron-run-installer-script"
    if [ -e "${extradeb}" ]; then
	e2cp -G 0 -O 0 "${extradeb}" "${rootfs_partition_tempfile}":"/root/extradeb.tar.gz"
    fi
fi

# Unmount the initial ramdisk
if [[ ${rootfs_partition} = "raw" ]]; then
    sudo umount "${mount}"
else
    rootfs_partition_start=$(partx -g -o START -s -n "${rootfs_partition}" "${disk}" | xargs)
    rootfs_partition_end=$(partx -g -o END -s -n "${rootfs_partition}" "${disk}" | xargs)
    rootfs_partition_num_sectors=$((${rootfs_partition_end} - ${rootfs_partition_start} + 1))
    dd if="${rootfs_partition_tempfile}" of="${disk}" bs=512 seek=${rootfs_partition_start} count=${rootfs_partition_num_sectors} conv=fsync,notrunc
fi
rm -f "${rootfs_partition_tempfile}"
trap raw_initrd_remove EXIT

# Boot test the new system and run stage 3
${qemu} -machine "${machine}" -cpu "${cpu}" -m 2048 >&2 \
  -kernel "${kernel}" -initrd "${initrd}" -no-user-config -nodefaults \
  -no-reboot -display none -nographic -serial stdio -parallel none \
  -smp "${qemucpucores}",sockets="${qemucpucores}",cores=1,threads=1 \
  -object rng-random,id=objrng0,filename=/dev/urandom \
  -device virtio-rng-pci-non-transitional,rng=objrng0,id=rng0,max-bytes=1024,period=2000 \
  -drive file="${disk}",format=raw,if=none,aio=threads,id=drive-virtio-disk0 \
  -device virtio-blk-pci-non-transitional,scsi=off,drive=drive-virtio-disk0 \
  -chardev file,id=exitcode,path=exitcode \
  -device pci-serial,chardev=exitcode \
  -netdev user,id=usernet0,ipv6=off \
  -device virtio-net-pci-non-transitional,netdev=usernet0,id=net0 \
  -append "root=LABEL=ROOT installer_script=/root/${suite}.sh ${cmdline}"
[[ -s exitcode ]] && exitcode=$(cat exitcode | tr -d '\r') || exitcode=2
rm -f exitcode
if [ "${exitcode}" != "0" ]; then
  echo "Root filesystem finalization failed (err=${exitcode})"
  exit "${exitcode}"
fi

# Fix up any issues from the unclean shutdown
if [[ ${rootfs_partition} = "raw" ]]; then
    sudo e2fsck -p -f "${disk}" || true
else
    rootfs_partition_start=$(partx -g -o START -s -n "${rootfs_partition}" "${disk}" | xargs)
    rootfs_partition_end=$(partx -g -o END -s -n "${rootfs_partition}" "${disk}" | xargs)
    rootfs_partition_num_sectors=$((${rootfs_partition_end} - ${rootfs_partition_start} + 1))
    rootfs_partition_offset=$((${rootfs_partition_start} * 512))
    rootfs_partition_tempfile2=$(mktemp)
    dd if="${disk}" of="${rootfs_partition_tempfile2}" bs=512 skip=${rootfs_partition_start} count=${rootfs_partition_num_sectors}
    /sbin/e2fsck -p -f "${rootfs_partition_tempfile2}" || true
    dd if="${rootfs_partition_tempfile2}" of="${disk}" bs=512 seek=${rootfs_partition_start} count=${rootfs_partition_num_sectors} conv=fsync,notrunc
    rm -f "${rootfs_partition_tempfile2}"
    /sbin/e2fsck -fy "${disk}"?offset=${rootfs_partition_offset} || true
fi
if [[ -n "${system_partition}" ]]; then
  system_partition_start=$(partx -g -o START -s -n "${system_partition}" "${disk}" | xargs)
  system_partition_end=$(partx -g -o END -s -n "${system_partition}" "${disk}" | xargs)
  system_partition_num_sectors=$((${system_partition_end} - ${system_partition_start} + 1))
  system_partition_offset=$((${system_partition_start} * 512))
  system_partition_size=$((${system_partition_num_sectors} * 512))
  system_partition_tempfile=$(mktemp)
  dd if="${disk}" of="${system_partition_tempfile}" bs=512 skip=${system_partition_start} count=${system_partition_num_sectors}
  /sbin/fsck.vfat -a "${system_partition_tempfile}" || true
  dd if="${system_partition_tempfile}" of="${disk}" bs=512 seek=${system_partition_start} count=${system_partition_num_sectors} conv=fsync,notrunc
  rm -f "${system_partition_tempfile}"
fi

# Mount the final disk image locally
if [[ ${rootfs_partition} = "raw" ]]; then
    sudo mount -o loop -t ext4 "${disk}" "${mount}"
else
    rootfs_partition_start=$(partx -g -o START -s -n "${rootfs_partition}" "${disk}" | xargs)
    rootfs_partition_offset=$((${rootfs_partition_start} * 512))
    sudo mount -o loop,offset=${rootfs_partition_offset} -t ext4 "${disk}" "${mount}"
fi
image_unmount3() {
  sudo umount "${mount}"
  raw_initrd_remove
}
trap image_unmount3 EXIT

# Fill the rest of the space with zeroes, to optimize compression
sudo dd if=/dev/zero of="${mount}/sparse" bs=1M 2>/dev/null || true
sudo rm -f "${mount}/sparse"

echo "Debian ${suite} for ${arch} filesystem generated at '${disk}'."
echo "Initial ramdisk generated at '${ramdisk}'."
