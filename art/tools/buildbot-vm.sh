#! /bin/bash
#
# Copyright (C) 2023 The Android Open Source Project
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

set -e

ART_TEST_ON_VM=true . "$(dirname $0)/buildbot-utils.sh"
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

known_actions="create|boot|geniso|install-keys|setup-ssh|connect|quit"

if [[ -z $ANDROID_BUILD_TOP ]]; then
    msgfatal "ANDROID_BUILD_TOP is not set"
elif [[ ( $# -ne 1 ) || ! ( "$1" =~ ^($known_actions)$ ) ]]; then
    msgfatal "usage: $0 <$known_actions>"
fi

action="$1"

get_stable_binary() {
    mkdir tmp && cd tmp
    wget "http://security.ubuntu.com/ubuntu/pool/main/$1"
    7z x "$(basename $1)" && zstd -d data.tar.zst && tar -xf data.tar
    mv "$2" ..
    cd .. && rm -rf tmp
}

if [[ $action = create ]]; then
(
    rm -rf "$ART_TEST_VM_DIR"
    mkdir -p "$ART_TEST_VM_DIR"
    cd "$ART_TEST_VM_DIR"

    # sudo apt install qemu-system-<arch> qemu-efi cloud-image-utils

    # Get the cloud image for Ubunty 23.10 (Mantic Minotaur)
    wget "http://cloud-images.ubuntu.com/releases/23.10/release/$ART_TEST_VM_IMG"

    if [[ "$TARGET_ARCH" = "riscv64" ]]; then
        # Get U-Boot for Ubuntu 22.04 (Jammy)
        get_stable_binary \
            u/u-boot/u-boot-qemu_2023.07+dfsg-1ubuntu2_all.deb \
            usr/lib/u-boot/qemu-riscv64_smode/uboot.elf

        # Get OpenSBI for Ubuntu 22.04 (Jammy)
        get_stable_binary \
            o/opensbi/opensbi_1.3-1ubuntu0.23.04.2_all.deb \
            usr/lib/riscv64-linux-gnu/opensbi/generic/fw_jump.elf

    elif [[ "$TARGET_ARCH" = "arm64" ]]; then
        # Get EFI (ARM64)
        get_stable_binary \
            e/edk2/qemu-efi-aarch64_2023.05-2ubuntu0.1_all.deb \
            usr/share/qemu-efi-aarch64/QEMU_EFI.fd

        dd if=/dev/zero of=flash0.img bs=1M count=64
        dd if=QEMU_EFI.fd of=flash0.img conv=notrunc
        dd if=/dev/zero of=flash1.img bs=1M count=64
    fi

    qemu-img resize "$ART_TEST_VM_IMG" +128G
)
elif [[ $action = geniso ]]; then
(
    #https://help.ubuntu.com/community/CloudInit
    cat >user-data <<EOF
#cloud-config
ssh_pwauth: true
chpasswd:
  expire: false
  users:
    - name: $ART_TEST_SSH_USER
      password: ubuntu
      type: text
users:
  - default
  - name: $ART_TEST_SSH_USER
    ssh-authorized-keys:
      - ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQCOYmwd9qoYd7rfYI6Q8zzqoZ3BtLC/SQo0WCvBFoJT6JzwU8F7nkN57KBQPLtvX2OBeDnFbtEY8uLtuNEp1Z19VcDbRd3LhyAMYFz6Ox/vWtPfl0hv0kUMQMAne1Bg0tawlNxawP2HXrLOh/FaXdSBSRUHNqMTQEnkIYw4faArDS/zKjVDs0/+e9mhtjL0akLcK04crlk2KD8Q2csya5givdAD7fVNOx7DtckRR47FLM1bERe0t0FlUESx/x7oLjNEmNUrPXV6GSkCoskmKSZC1vwgAf0VrxFADv1EywQXmlNaa4+rzqS4jMYuwi5QCtQXFFZl5qQ1Sh1rnliTRJvJzjXCeq3QPsPzUJInfVGzrPClfHG7whlJE/Uwv8UOF7WHzUt5OBOsW6nZrplldvfYif/qz6dR+RX2G0zi8tC/2Mzahr6toAqtsqbdp3coYvpi/OjHIV3RhyJxG1FtyGYQRnmGPs8R9ic3pupjLFWM9qIilUCjFrUoiw7QAgfUrUc= ubuntu_user@example.com
    sudo: ALL=(ALL) NOPASSWD:ALL
    groups: users, admin
EOF
    # meta-data is necessary, even if empty.
    cat >meta-data <<EOF
EOF
    genisoimage -output user-data.img -volid cidata -joliet -rock user-data meta-data
    mv user-data.img "$(dirname $0)/user-data.img"
    rm user-data meta-data
)
elif [[ $action = boot ]]; then
(
    cp "$(dirname $0)/user-data.img" "$ART_TEST_VM_DIR/user-data.img"
    cd "$ART_TEST_VM_DIR"
    if [[ "$TARGET_ARCH" = "riscv64" ]]; then
        (qemu-system-riscv64 \
            -m 16G \
            -smp 8 \
            -M virt \
            -nographic \
            -bios fw_jump.elf \
            -kernel uboot.elf \
            -cpu rv64,v=true,vlen=256,vext_spec=v1.0 \
            -drive file="$ART_TEST_VM_IMG",if=virtio \
            -drive file=user-data.img,format=raw,if=virtio \
            -device virtio-net-device,netdev=usernet \
            -netdev user,id=usernet,hostfwd=tcp::$ART_TEST_SSH_PORT-:22 > $SCRIPT_DIR/boot.out &)
        echo "Now listening for successful boot"
        finish_str='.*finished at.*'
        while IFS= read -d $'\0' -n 1 a ; do
            line+="${a}"
            if [[ "$line" =~ $finish_str ]] ; then
                echo $line
                echo "VM Successfully booted!"
                exit 0
            elif [[ $a = $'\n' ]]
            then
                echo $line
                unset line
            fi
        done < <(tail -f $SCRIPT_DIR/boot.out)

    elif [[ "$TARGET_ARCH" = "arm64" ]]; then
        (qemu-system-aarch64 \
            -m 16G \
            -smp 8 \
            -cpu cortex-a710,sve=on \
            -M virt \
            -nographic \
            -drive if=none,file="$ART_TEST_VM_IMG",id=hd0 \
            -pflash flash0.img \
            -pflash flash1.img \
            -drive file=user-data.img,format=raw,id=cloud \
            -device virtio-blk-device,drive=hd0 \
            -device virtio-net-device,netdev=usernet \
            -netdev user,id=usernet,hostfwd=tcp::$ART_TEST_SSH_PORT-:22 > $SCRIPT_DIR/boot.out &)
        echo "Now listening for successful boot"
        finish_str='.*finished at.*'
        while IFS= read -d $'\0' -n 1 a ; do
            line+="${a}"
            if [[ "$line" =~ $finish_str ]] ; then
                echo $line
                echo "VM Successfully booted!"
                exit 0
            elif [[ $a = $'\n' ]]
            then
                echo $line
                unset line
            fi
        done < <(tail -f $SCRIPT_DIR/boot.out)
    fi

)
elif [[ $action = setup-ssh ]]; then
    # Clean up mentions of this VM from known_hosts
    sed -i -E "/\[$ART_TEST_SSH_HOST.*\]:$ART_TEST_SSH_PORT .*/d" $HOME/.ssh/known_hosts
    ssh-copy-id -p "$ART_TEST_SSH_PORT" -o IdentityAgent=none -o StrictHostKeyChecking=no "$ART_TEST_SSH_USER@$ART_TEST_SSH_HOST"

elif [[ $action = install-keys ]]; then
    if [ -f "$HOME/.ssh/known_hosts" ]; then
        sed -i -E "/\[$ART_TEST_SSH_HOST.*\]:$ART_TEST_SSH_PORT .*/d" $HOME/.ssh/known_hosts
    fi
    # This key is only used to authorize access to a local test VM and does
    # not pose any security risk.
    echo "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQCOYmwd9qoYd7rfYI6Q8zzqoZ3BtLC/SQo0WCvBFoJT6JzwU8F7nkN57KBQPLtvX2OBeDnFbtEY8uLtuNEp1Z19VcDbRd3LhyAMYFz6Ox/vWtPfl0hv0kUMQMAne1Bg0tawlNxawP2HXrLOh/FaXdSBSRUHNqMTQEnkIYw4faArDS/zKjVDs0/+e9mhtjL0akLcK04crlk2KD8Q2csya5givdAD7fVNOx7DtckRR47FLM1bERe0t0FlUESx/x7oLjNEmNUrPXV6GSkCoskmKSZC1vwgAf0VrxFADv1EywQXmlNaa4+rzqS4jMYuwi5QCtQXFFZl5qQ1Sh1rnliTRJvJzjXCeq3QPsPzUJInfVGzrPClfHG7whlJE/Uwv8UOF7WHzUt5OBOsW6nZrplldvfYif/qz6dR+RX2G0zi8tC/2Mzahr6toAqtsqbdp3coYvpi/OjHIV3RhyJxG1FtyGYQRnmGPs8R9ic3pupjLFWM9qIilUCjFrUoiw7QAgfUrUc= ubuntu_user@example.com" > ~/.ssh/ubuntu.pub
    echo "-----BEGIN OPENSSH PRIVATE KEY-----
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
-----END OPENSSH PRIVATE KEY-----" > ~/.ssh/ubuntu
    chmod 600 ~/.ssh/ubuntu

elif [[ $action = connect ]]; then
    $ART_SSH_CMD

elif [[ $action = quit ]]; then
    $ART_SSH_CMD "sudo poweroff"

fi
