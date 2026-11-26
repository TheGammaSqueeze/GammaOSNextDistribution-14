# GammaOS Nano, shared by every GammaOS product (base.mk for the lite image, and
# lineage_tv_arm64_bvN.mk, which has its own base and does not inherit base.mk).
#
# The nano home and its emulators live in their own repositories, linked as git submodules:
#   frameworks/base/cmds/gammaos-nano     the home
#   gammaos/emulators/drastic-nano        DS (DraStic)
#   gammaos/emulators/melonds-nano        DS (melonDS)
#   gammaos/emulators/azahar-nano         3DS (Azahar)
#   gammaos/emulators/retroarch-nano      libretro cores (RetroArch)
# A tree without them still builds: whatever is not checked out is left out of the image.
# Without the home there is no nano mode at all, and the image boots as plain Android
# (ro.gammaos.nano.available stays unset: init, SurfaceFlinger and the power menu then
# never choose nano, see system/core/rootdir/init.rc).
#
# None of them is compiled by Soong: each repository builds its programs with the NDK
# (gammaos/build-nano-cores.sh runs their build scripts before the image build) and its
# Android.bp installs what its prebuilt/ folder holds.
#
# An emulator needs the home; none of them needs another emulator.

GAMMAOS_NANO_HOME := $(wildcard frameworks/base/cmds/gammaos-nano/Android.bp)
GAMMAOS_NANO_DRASTIC := $(if $(GAMMAOS_NANO_HOME),$(wildcard gammaos/emulators/drastic-nano/Android.bp))
GAMMAOS_NANO_MELONDS := $(if $(GAMMAOS_NANO_HOME),$(wildcard gammaos/emulators/melonds-nano/Android.bp))
GAMMAOS_NANO_AZAHAR := $(if $(GAMMAOS_NANO_HOME),$(wildcard gammaos/emulators/azahar-nano/Android.bp))
GAMMAOS_NANO_RETROARCH := $(if $(GAMMAOS_NANO_HOME),$(wildcard gammaos/emulators/retroarch-nano/Android.bp))

# Modules: each of these programs can be updated on a console without a system update, see
# gammaos/modules. A repository's module.prop says which version the image ships, and a
# console only uses an installed module that is newer. ro.gammaos.modules.platform is what
# a module must have been built for; it is the number in nano-common's
# platform/MODULE_PLATFORM and rises when programs built against an older platform export
# would no longer run on the image.
PRODUCT_PACKAGES += gammaos-modules
PRODUCT_SYSTEM_PROPERTIES += ro.gammaos.modules.platform=1
PRODUCT_COPY_FILES += $(foreach m, \
    $(if $(GAMMAOS_NANO_HOME),frameworks/base/cmds/gammaos-nano/module.prop:gammaos-nano) \
    $(if $(GAMMAOS_NANO_DRASTIC),gammaos/emulators/drastic-nano/module.prop:drastic-nano) \
    $(if $(GAMMAOS_NANO_MELONDS),gammaos/emulators/melonds-nano/module.prop:melonds-nano) \
    $(if $(GAMMAOS_NANO_AZAHAR),gammaos/emulators/azahar-nano/module.prop:azahar-nano) \
    $(if $(GAMMAOS_NANO_RETROARCH),gammaos/emulators/retroarch-nano/module.prop:retroarch-nano), \
    $(if $(wildcard $(word 1,$(subst :, ,$(m)))), \
        $(word 1,$(subst :, ,$(m))):system/etc/gammaos/modules/$(word 2,$(subst :, ,$(m))).prop))

# The signed record of what each of them was built as (module-pack.py record writes it into
# prebuilt/): by it the home tells a program GammaOS built from one put in its place, and a
# console that is not registered only starts the former. The property says the image has
# them; it is set only when every program that is shipped has one, so a build made from
# sources without records does not lock its own emulators out.
GAMMAOS_NANO_RECORDS := \
    $(if $(GAMMAOS_NANO_HOME),frameworks/base/cmds/gammaos-nano/prebuilt/module.record:gammaos-nano) \
    $(if $(GAMMAOS_NANO_DRASTIC),gammaos/emulators/drastic-nano/prebuilt/module.record:drastic-nano) \
    $(if $(GAMMAOS_NANO_MELONDS),gammaos/emulators/melonds-nano/prebuilt/module.record:melonds-nano) \
    $(if $(GAMMAOS_NANO_AZAHAR),gammaos/emulators/azahar-nano/prebuilt/module.record:azahar-nano) \
    $(if $(GAMMAOS_NANO_RETROARCH),gammaos/emulators/retroarch-nano/prebuilt/module.record:retroarch-nano)
# (module-pack.py check: the record is there and lists the built files exactly as they are. A
# program built again without a new record would be refused on unregistered consoles.)
GAMMAOS_NANO_RECORD_CHECK := frameworks/base/cmds/gammaos-nano/common/tools/module-pack.py
GAMMAOS_NANO_RECORDS_MISSING := $(strip $(foreach m,$(GAMMAOS_NANO_RECORDS), \
    $(if $(filter ok,$(shell python3 $(GAMMAOS_NANO_RECORD_CHECK) check \
        --repo $(patsubst %/prebuilt/module.record,%,$(word 1,$(subst :, ,$(m)))) >/dev/null 2>&1 && echo ok)),,$(m))))
ifeq ($(GAMMAOS_NANO_RECORDS_MISSING),)
PRODUCT_COPY_FILES += $(foreach m,$(GAMMAOS_NANO_RECORDS), \
    $(word 1,$(subst :, ,$(m))):system/etc/gammaos/modules/$(word 2,$(subst :, ,$(m))).record \
    $(word 1,$(subst :, ,$(m))).sig:system/etc/gammaos/modules/$(word 2,$(subst :, ,$(m))).record.sig)
PRODUCT_SYSTEM_PROPERTIES += ro.gammaos.modules.records=1
else
$(warning GammaOS Nano: no signed record that matches the built files for $(GAMMAOS_NANO_RECORDS_MISSING): an unregistered console will not check its emulators (run common/tools/module-pack.py record in each))
endif

ifneq ($(GAMMAOS_NANO_HOME),)
PRODUCT_PACKAGES += gammaos-nano
PRODUCT_SYSTEM_PROPERTIES += ro.gammaos.nano.available=true
else
$(warning GammaOS Nano: frameworks/base/cmds/gammaos-nano is not checked out, building without nano mode)
endif

# The in-game menu the emulators draw (melonds-nano, azahar-nano, retroarch-nano) uses one set
# of art, kept in nano-common, which each of them links as its submodule common/. It is
# installed once, from whichever of them is checked out, where they look for it: the packs for
# panels 480 and 768 lines high with the shared words in /system/etc/melonds-nano/menu, the
# packs for 960 and 1080 lines with the 3DS words in /system/etc/azahar-nano/menu.
GAMMAOS_NANO_MENU_ART := $(firstword $(wildcard \
    gammaos/emulators/melonds-nano/common/menu/packs \
    gammaos/emulators/azahar-nano/common/menu/packs \
    gammaos/emulators/retroarch-nano/common/menu/packs))
GAMMAOS_NANO_MENU_ART_LARGE := $(firstword $(wildcard \
    gammaos/emulators/azahar-nano/common/menu/packs-large \
    gammaos/emulators/retroarch-nano/common/menu/packs-large \
    gammaos/emulators/melonds-nano/common/menu/packs-large))
ifneq ($(GAMMAOS_NANO_HOME),)
ifneq ($(GAMMAOS_NANO_MENU_ART),)
PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*,$(GAMMAOS_NANO_MENU_ART),system/etc/melonds-nano/menu)
endif
ifneq ($(GAMMAOS_NANO_MENU_ART_LARGE),)
PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*,$(GAMMAOS_NANO_MENU_ART_LARGE),system/etc/azahar-nano/menu)
endif
endif

# drastic-nano runs libdrastic and its data from /system so the DS emulator keeps
# working even when the DraStic APK is uninstalled: libdrastic_arm64.so + libdrastic_cpu.so
# (in /system/lib64, the linker namespace of a system binary cannot dlopen from /system/etc),
# the BIOS/firmware, the game database, the cheat database, the default touch layout and the
# default shaders. drastic-nano seeds its private data root from here at launch; saves,
# save states and user shaders live on /sdcard/drastic-nano.
ifneq ($(GAMMAOS_NANO_DRASTIC),)
PRODUCT_PACKAGES += drastic-nano
PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*,gammaos/emulators/drastic-nano/data,system/etc/drastic-nano) \
    gammaos/emulators/drastic-nano/prebuilt/libdrastic_arm64.so:system/lib64/libdrastic_arm64.so \
    gammaos/emulators/drastic-nano/prebuilt/libdrastic_cpu.so:system/lib64/libdrastic_cpu.so
endif

# melonds-nano: melonDS with the GammaOS Vulkan renderer and the DRM/KMS frontend, built with
# the NDK by its build-core.sh into its prebuilt/ and installed as
# /system/bin/melonds-nano. The service (melonds-nano.rc) is started through
# sys.gammaos.melonds_nano.start=1.
ifneq ($(GAMMAOS_NANO_MELONDS),)
PRODUCT_PACKAGES += melonds-nano
endif

# azahar-nano: the native 3DS core (Azahar 2126.1.2 built with the NDK behind a C ABI)
# and the adrenotools hook libraries for custom GPU drivers live directly in /system/lib64
# (the system binary's linker namespace does not permit subdirectories). The frontend is
# the azahar-nano program; its user data lives in /data/system/azahar-nano (private
# root: config, nand, system files, shader cache) and /sdcard/azahar-nano (saves, states).
# The GammaOS Turnip Vulkan driver (github.com/TheGammaSqueeze/gammaos-turnip, adreno-610)
# ships as a bundled adrenotools package, the default on the Adreno 610 (GpuDriverPkg.h), and
# malisx2 (github.com/bmdhacks/malisx2) the same way, the default on the Mali-G52.
ifneq ($(GAMMAOS_NANO_AZAHAR),)
PRODUCT_PACKAGES += azahar-nano azahar-nano-cheats
PRODUCT_COPY_FILES += \
    gammaos/emulators/azahar-nano/prebuilt/libazahar-nano-core.so:system/lib64/libazahar-nano-core.so \
    gammaos/emulators/azahar-nano/prebuilt/libc++_shared.so:system/lib64/libc++_shared.so \
    gammaos/emulators/azahar-nano/prebuilt/libmain_hook.so:system/lib64/libmain_hook.so \
    gammaos/emulators/azahar-nano/prebuilt/libfile_redirect_hook.so:system/lib64/libfile_redirect_hook.so \
    gammaos/emulators/azahar-nano/prebuilt/libgsl_alloc_hook.so:system/lib64/libgsl_alloc_hook.so \
    gammaos/emulators/azahar-nano/prebuilt/libhook_impl.so:system/lib64/libhook_impl.so \
    gammaos/emulators/azahar-nano/prebuilt/gpu_drivers/gammaos-turnip-adreno-610/meta.json:system/etc/azahar-nano/gpu_drivers/gammaos-turnip-adreno-610/meta.json \
    gammaos/emulators/azahar-nano/prebuilt/gpu_drivers/gammaos-turnip-adreno-610/vulkan.gammaos.so:system/etc/azahar-nano/gpu_drivers/gammaos-turnip-adreno-610/vulkan.gammaos.so \
    gammaos/emulators/azahar-nano/prebuilt/gpu_drivers/malisx2-g52/meta.json:system/etc/azahar-nano/gpu_drivers/malisx2-g52/meta.json \
    gammaos/emulators/azahar-nano/prebuilt/gpu_drivers/malisx2-g52/libvulkan_malisx2.so:system/etc/azahar-nano/gpu_drivers/malisx2-g52/libvulkan_malisx2.so
PRODUCT_PRODUCT_PROPERTIES += \
    persist.gammaos.nano.azahar_nano=1
endif

# RetroArch Nano: native RetroArch on the DRM panels for the nano home's libretro launches,
# unless persist.gammaos.nano.retroarch_nano=0 (Game Settings > RetroArch). It runs the
# RetroArch APK's own cores and configuration; the stock udev joypad profiles ship here for it.
ifneq ($(GAMMAOS_NANO_RETROARCH),)
PRODUCT_PACKAGES += retroarch-nano
PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*.cfg,gammaos/emulators/retroarch-nano/autoconfig/udev,system/etc/retroarch-nano/autoconfig/udev)
endif
