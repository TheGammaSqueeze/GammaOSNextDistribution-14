project(
    'drm_hwcomposer',
    ['c', 'cpp'],
    version : '2',
    license : 'APACHE-2.0',
    meson_version : '>= 0.56',
    default_options : ['buildtype=debugoptimized', 'b_ndebug=if-release', 'c_std=c11', 'cpp_std=c++17', 'cpp_rtti=false']
)

inc_include = [include_directories('.')]

src_common = files(
    'compositor/DrmKmsPlan.cpp',
    'compositor/FlatteningController.cpp',
    'backend/BackendManager.cpp',
    'backend/Backend.cpp',
    'backend/BackendClient.cpp',
    'utils/fd.cpp',
)

deps = [
    dependency('cutils'),
    dependency('drm'),
    dependency('hardware'),
    dependency('hidlbase'),
    dependency('log'),
    dependency('sync'),
    dependency('ui'),
    dependency('utils'),
]

common_cpp_flags = [
    '-DUSE_IMAPPER4_METADATA_API',
]

hwc2_cpp_flags = [
    '-DHWC2_INCLUDE_STRINGIFICATION',
    '-DHWC2_USE_CPP11',
]

subdir('drm')
subdir('bufferinfo')

drmhwc_common = static_library(
    'drm_hwcomposer_common',
    src_common,
# TODO remove hwc2 flags from common code (backends needs rework)
    cpp_args : common_cpp_flags + hwc2_cpp_flags,
    dependencies : deps,
)

subdir('hwc2_device')
