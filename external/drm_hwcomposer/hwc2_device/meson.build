src_hwc2_device = files(
    'hwc2_device.cpp',
    'DrmHwcTwo.cpp',
    'HwcDisplayConfigs.cpp',
    'HwcDisplay.cpp',
    'HwcLayer.cpp',
)

shared_library(
    'hwcomposer.drm',
    src_hwc2_device,
    name_prefix : '',
    cpp_args : common_cpp_flags + hwc2_cpp_flags,
    dependencies : deps,
    install : true,
    link_whole: drmhwc_common,
    install_dir : get_option('libdir') / 'hw',
    include_directories: inc_include,
)
