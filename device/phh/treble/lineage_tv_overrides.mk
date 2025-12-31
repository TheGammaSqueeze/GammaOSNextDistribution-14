#
# device/phh/treble/lineage_tv_overrides.mk
#
# This file is included by the "TV base" PHH products, but we force the end result to behave
# like a normal AOSP/Lineage device build (normal Settings + SystemUI + Trebuchet).
#

# Explicitly ensure we are not treated as ATV (overrides any upstream includes).
PRODUCT_IS_ATV := false
PRODUCT_CHARACTERISTICS := device
PRODUCT_SYSTEM_PROPERTIES += ro.product.is_atv=false

# Drop TV-specific packages pulled in by the TV base product.
# Important: some TV base products use PRODUCT_SYSTEM_EXT_PACKAGES instead of PRODUCT_PACKAGES.
# Remove from both to guarantee the package is not installed.

# TV SystemUI and TV Settings
PRODUCT_PACKAGES -= TvSystemUI
PRODUCT_SYSTEM_EXT_PACKAGES -= TvSystemUI
PRODUCT_PACKAGES -= TvSettings
PRODUCT_SYSTEM_EXT_PACKAGES -= TvSettings

# TV provisioning app that currently claims HOME: com.android.tv.provision/.DefaultActivity
PRODUCT_PACKAGES -= TvProvision
PRODUCT_SYSTEM_EXT_PACKAGES -= TvProvision

# TV launchers that may be pulled in (safe to subtract even if absent)
PRODUCT_PACKAGES -= LeanbackLauncher
PRODUCT_SYSTEM_EXT_PACKAGES -= LeanbackLauncher
PRODUCT_PACKAGES -= LauncherX
PRODUCT_SYSTEM_EXT_PACKAGES -= LauncherX
PRODUCT_PACKAGES -= TvLauncher
PRODUCT_SYSTEM_EXT_PACKAGES -= TvLauncher

# Ensure standard AOSP/Lineage UI stack.
PRODUCT_PACKAGES += SystemUI
PRODUCT_PACKAGES += Settings

# Some trees place SystemUI/Settings in system_ext; adding to system_ext list is harmless if already in base.
PRODUCT_SYSTEM_EXT_PACKAGES += SystemUI
PRODUCT_SYSTEM_EXT_PACKAGES += Settings

# Ensure Trebuchet (Launcher3) + Quickstep are present.
# If you only want one, keep TrebuchetQuickStep and remove Trebuchet.
PRODUCT_PACKAGES += TrebuchetQuickStep
PRODUCT_PACKAGES += Trebuchet

PRODUCT_SYSTEM_EXT_PACKAGES += TrebuchetQuickStep
PRODUCT_SYSTEM_EXT_PACKAGES += Trebuchet

# Kill lockscreen completely for lineage_tv_* builds.
#  - ro.lockscreen.disable.default: custom/ROM-side kill switch (used by several stacks)
#  - persist.sys.disable_lockscreen: common kill switch used by many Android forks
PRODUCT_SYSTEM_PROPERTIES += \
    ro.lockscreen.disable.default=true \
    persist.sys.disable_lockscreen=1
    
# Framework overlay that disables keyguard/lockscreen at the resource/config level.
PRODUCT_PACKAGES += NoKeyguardFrameworkOverlay
PRODUCT_SYSTEM_EXT_PACKAGES += NoKeyguardFrameworkOverlay

# IME: remove Leanback IME and restore a normal keyboard.
# gsi_tv_base.mk adds LeanbackIME (exact name) and TvSampleLeanbackLauncher.
PRODUCT_PACKAGES -= LeanbackIME
PRODUCT_SYSTEM_EXT_PACKAGES -= LeanbackIME
PRODUCT_PACKAGES -= LeanbackIme
PRODUCT_SYSTEM_EXT_PACKAGES -= LeanbackIme

PRODUCT_PACKAGES -= TvSampleLeanbackLauncher
PRODUCT_SYSTEM_EXT_PACKAGES -= TvSampleLeanbackLauncher

# Add AOSP keyboard.
PRODUCT_PACKAGES += LatinIME
PRODUCT_SYSTEM_EXT_PACKAGES += LatinIME

# Optional but recommended: include basic AOSP IME support libs if your tree expects them.
# (Safe even if they are already pulled in by other products.)
PRODUCT_PACKAGES += InputDevices