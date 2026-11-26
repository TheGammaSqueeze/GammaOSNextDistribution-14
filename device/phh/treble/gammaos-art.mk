# GammaOS ART runtime settings shared by every product (base.mk for the lite image, and
# lineage_tv_arm64_bvN.mk, which has its own base and does not inherit base.mk).

# GammaOS: do not have ART madvise(MADV_WILLNEED) an app's whole odex and vdex at process
# start (the platform default reads up to 100 MB of each synchronously from flash before
# the app runs). On a 1 GB handheld that is the black screen on every launch of a large
# app: Mupen64Plus AE's 76 MB odex plus 27 MB vdex were read twice (main and emulation
# process) while the kernel was already thrashing. Pages fault in on demand instead.
PRODUCT_PRODUCT_PROPERTIES += \
    dalvik.vm.madvise.vdexfile.size=0 \
    dalvik.vm.madvise.odexfile.size=0
