#!/bin/bash

# Resolve ANDROID build top even when envsetup was not sourced.
# This script lives at: $TOP/device/phh/treble/generate.sh
TOP="$(cd "$(dirname "$0")/../../.." && pwd)"
if [ ! -d "$TOP/build" ]; then
    echo "Could not resolve build top from $TOP"
    exit 1
fi

rom_script=''
if [ -n "$1" ]; then
    if echo "$1" | grep -qF /; then
        rom_script=''
        for i in "$@"; do
            rom_script="${rom_script}"$'\n''$(call inherit-product, '"$i"')'
        done
    else
        rom_script='$(call inherit-product, device/phh/treble/'$1'.mk)'
    fi
fi

echo 'PRODUCT_MAKEFILES := \' > AndroidProducts.mk

for part in a ab; do
    for apps in vanilla gapps foss gapps-go; do
        for arch in arm64 arm a64; do
            for su in yes no; do
                apps_suffix=""
                apps_script=""
                apps_name=""
                extra_packages=""
                vndk="vndk.mk"
                optional_base=""

                baseArch="$arch"
                if [ "$arch" = "a64" ]; then
                    baseArch="arm"
                fi

                if [ "$apps" == "gapps" ]; then
                    apps_suffix="g"
                    apps_script='$(call inherit-product, vendor/gapps/'$baseArch'/'$baseArch'-vendor.mk)'
                    apps_name="with GApps"
                fi
                if [ "$apps" == "gapps-go" ]; then
                    apps_suffix="o"
                    apps_script='$(call inherit-product, device/phh/treble/gapps-go.mk)'
                    apps_name="Go"
                fi
                if [ "$apps" == "foss" ]; then
                    apps_suffix="f"
                    apps_script='$(call inherit-product, vendor/foss/foss.mk)'
                    apps_name="with FOSS apps"
                fi
                if [ "$apps" == "vanilla" ]; then
                    apps_suffix="v"
                    apps_script=''
                    apps_name="vanilla"
                fi
                if [ "$arch" == "arm" ]; then
                    vndk="vndk-binder32.mk"
                fi
                if [ "$arch" == "a64" ]; then
                    vndk="vndk32.mk"
                fi

                su_suffix='N'
                if [ "$su" == "yes" ]; then
                    su_suffix='S'
                    extra_packages+=' phh-su me.phh.superuser su'
                fi

                part_suffix='a'
                if [ "$part" == 'ab' ]; then
                    part_suffix='b'
                else
                    optional_base='$(call inherit-product, device/phh/treble/base-sas.mk)'
                fi

                target="lineage_${arch}_${part_suffix}${apps_suffix}${su_suffix}"

                zygote=32
                if [ "$arch" = "arm64" ]; then
                    zygote=64_32
                fi

                #
                # Standard (non-TV) target
                #
                cat > "${target}.mk" <<EOF
TARGET_GAPPS_ARCH := ${baseArch}
include build/make/target/product/aosp_${baseArch}.mk
\$(call inherit-product, device/phh/treble/base.mk)
${optional_base}
${apps_script}
${rom_script}

PRODUCT_NAME := ${target}
PRODUCT_DEVICE := tdgsi_${arch}_${part}
PRODUCT_BRAND := google
PRODUCT_SYSTEM_BRAND := google
PRODUCT_MODEL := TrebleDroid ${apps_name}

# Overwrite the inherited "emulator" characteristics
PRODUCT_CHARACTERISTICS := device

PRODUCT_PACKAGES += ${extra_packages}
EOF

                echo -e '\t$(LOCAL_DIR)/'"${target}.mk"' \' >> AndroidProducts.mk

                #
                # TV target
                #
                tv_target="lineage_tv_${arch}_${part_suffix}${apps_suffix}${su_suffix}"

                # Pick the correct TV base product file for this tree.
                # Your tree provides TV GSI base products under device/google/atv/products/.
                tv_base=""
                for cand in \
                    "device/google/atv/products/gsi_tv_${baseArch}.mk" \
                    "device/google/atv/products/gsi_tv_base.mk" \
                    "build/make/target/product/gsi/gsi_tv_${baseArch}.mk" \
                    "build/make/target/product/gsi_tv_${baseArch}.mk" \
                    "build/make/target/product/gsi/gsi_tv_${baseArch}_system.mk"
                do
                    if [ -f "$TOP/$cand" ]; then
                        tv_base="$cand"
                        break
                    fi
                done

                if [ -z "$tv_base" ]; then
                    echo "ERROR: Could not find a TV GSI base product makefile for arch=${baseArch}"
                    echo "Searched:"
                    echo "  device/google/atv/products/gsi_tv_${baseArch}.mk"
                    echo "  device/google/atv/products/gsi_tv_base.mk"
                    echo "  build/make/target/product/gsi/gsi_tv_${baseArch}.mk"
                    echo "  build/make/target/product/gsi_tv_${baseArch}.mk"
                    echo "  build/make/target/product/gsi/gsi_tv_${baseArch}_system.mk"
                    exit 1
                fi

                # If we ended up on gsi_tv_base.mk (shared), we still want the arch specific file if it exists.
                # This keeps behavior stable across different trees.
                tv_arch_specific="device/google/atv/products/gsi_tv_${baseArch}.mk"
                tv_arch_include=""
                if [ "$tv_base" == "device/google/atv/products/gsi_tv_base.mk" ] && [ -f "$TOP/$tv_arch_specific" ]; then
                    tv_arch_include="include ${tv_arch_specific}"
                fi

                cat > "${tv_target}.mk" <<EOF
TARGET_GAPPS_ARCH := ${baseArch}
include ${tv_base}
${tv_arch_include}

# gsi_tv_* products enable Artifact Path Requirements enforcement for compliance GSIs.
# This build intentionally installs additional PHH/Gamma packages into system/, so disable APR enforcement.
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := false

# Suppress the compliance debug policy warning for non-compliance, custom GSIs.
PRODUCT_INSTALL_DEBUG_POLICY_TO_SYSTEM_EXT := false

\$(call inherit-product, device/phh/treble/base.mk)
${optional_base}
${apps_script}

${rom_script}

PRODUCT_NAME := ${tv_target}
PRODUCT_DEVICE := tdgsi_${arch}_${part}
PRODUCT_BRAND := google
PRODUCT_SYSTEM_BRAND := google
PRODUCT_MODEL := TrebleDroid TV ${apps_name}

PRODUCT_CHARACTERISTICS := tv

PRODUCT_PACKAGES += ${extra_packages}
EOF

                echo -e '\t$(LOCAL_DIR)/'"${tv_target}.mk"' \' >> AndroidProducts.mk
            done
        done
    done
done

echo >> AndroidProducts.mk
