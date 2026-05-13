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
                    nontv_model_suffix="Full"
                fi
                if [ "$apps" == "gapps-go" ]; then
                    apps_suffix="o"
                    apps_script='$(call inherit-product, device/phh/treble/gapps-go.mk)'
                    apps_name="Go"
                    nontv_model_suffix="Go"
                fi
                if [ "$apps" == "foss" ]; then
                    apps_suffix="f"
                    apps_script='$(call inherit-product, vendor/foss/foss.mk)'
                    apps_name="with FOSS apps"
                    nontv_model_suffix="with FOSS apps"
                fi
                if [ "$apps" == "vanilla" ]; then
                    apps_suffix="v"
                    apps_script=''
                    apps_name="vanilla"
                    nontv_model_suffix="Lite"
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
PRODUCT_MODEL := GammaOS Next ${nontv_model_suffix}

# Overwrite the inherited "emulator" characteristics
PRODUCT_CHARACTERISTICS := device

PRODUCT_PACKAGES += ${extra_packages}

# Force CC with read barriers (concurrent-copying=true). Our GSI does not bundle
# a kernel, so without this the AOSP build probe falls back to "<unknown-kernel>"
# -> uffd CMC, which makes the host dex2oat emit boot.art with
# concurrent-copying=false. On kernels without UFFD SIGBUS the runtime falls
# back to read-barrier CC (gUseReadBarrier=true), the OatHeader mismatch trips
# zygote-side regeneration via dex2oat on every cold boot (16 to 20 seconds
# wasted). Setting this here on the top-level product makefile ensures the
# value wins the inherit-product resolution chain.
PRODUCT_ENABLE_UFFD_GC := false
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
PRODUCT_MODEL := GammaOS Core

PRODUCT_CHARACTERISTICS := tv

PRODUCT_PACKAGES += ${extra_packages}

# GammaOS Nano: ATV builds (used by handheld emulator devices like TrimUI
# Brick) do not need the full Android apex set. Drop apex modules that
# are never exercised on a retro gaming handheld so apexd activation is
# faster at boot. com.android.runtime / art / i18n / tzdata / conscrypt /
# resolv / media / media.swcodec / adbd / permission / wifi / statsd /
# tethering / configinfrastructure / mediaprovider / scheduling /
# sdkext / extservices stay - they back framework functionality that
# RetroArch + the Nano menu actually use. The list below is the set
# that:
#   - has no hardware behind it (uwb, virt)
#   - is for use cases the device does not target (adservices,
#     appsearch, healthfitness, ondevicepersonalization, devicelock,
#     ipsec, rkpd, neuralnetworks)
#   - is a CTS test shim only (apex.cts.shim)
# btservices stays because we DO ship Bluetooth on these devices.
PRODUCT_PACKAGES_REMOVE += \\
    com.android.adservices \\
    com.android.apex.cts.shim \\
    com.android.appsearch \\
    com.android.devicelock \\
    com.android.healthfitness \\
    com.android.ipsec \\
    com.android.neuralnetworks \\
    com.android.ondevicepersonalization \\
    com.android.rkpd \\
    com.android.uwb \\
    com.android.virt

# Force CC with read barriers (concurrent-copying=true). Our GSI does not
# bundle a kernel, so without this the AOSP build probe falls back to
# "<unknown-kernel>" -> uffd CMC, which makes the host dex2oat emit boot.art
# with concurrent-copying=false. On kernels without UFFD SIGBUS the runtime
# falls back to read-barrier CC (gUseReadBarrier=true), the OatHeader
# mismatch trips zygote-side regeneration via dex2oat on every cold boot
# (16 to 20 seconds wasted). On the TV chain gsi_release.mk forces this to
# true via inherit-product so we MUST reassert it here at the top level for
# the value to win.
PRODUCT_ENABLE_UFFD_GC := false
EOF

                echo -e '\t$(LOCAL_DIR)/'"${tv_target}.mk"' \' >> AndroidProducts.mk
            done
        done
    done
done

echo >> AndroidProducts.mk
