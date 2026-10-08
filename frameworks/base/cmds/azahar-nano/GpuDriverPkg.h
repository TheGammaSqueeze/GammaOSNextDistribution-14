// GpuDriverPkg: custom Adreno (adrenotools) driver packages for azahar-nano.
//
// A driver ships as a zip (usually <name>.adpkg.zip) holding meta.json and the
// Vulkan library it names ("libraryName"). The Azahar app unpacks such a zip
// into <user dir>/gpu_drivers/<name>/ and dlopens the library from there. Two
// locations matter here:
//   - the user's data folder (/sdcard/... by default): where the zips are
//     dropped and where unpacked packages are kept so they survive a reinstall;
//   - the private root (/data/system/azahar-nano): the only place the library
//     can actually be mapped executable (the sdcard FUSE mount is noexec), so
//     the chosen package is staged there before every launch.
#pragma once

#include <string>
#include <vector>

namespace azahar_nano {
namespace gpudrv {

// "libraryName" from <dir>/meta.json, empty when missing or unreadable.
std::string libraryName(const std::string& dir);

// Unpacked packages (folder names) under <root>/gpu_drivers that carry a
// meta.json with a library that exists.
std::vector<std::string> listPackages(const std::string& root);

// Importable zips: *.zip in <root>/gpu_drivers, <root>, /sdcard and
// /sdcard/Download that contain a meta.json. Full paths, sorted.
std::vector<std::string> listZips(const std::string& root);

// Unpacks a driver zip into <root>/gpu_drivers/<name>/ (name = the zip's base
// name without .adpkg.zip / .zip). Returns the package name, empty on failure
// (the partial folder is removed). *error gets a short reason.
std::string importZip(const std::string& zipPath, const std::string& root, std::string* error);

// Copies <root>/gpu_drivers/<name>/ to <privateRoot>/gpu_drivers/<name>/ when
// the staged copy is missing or older, and returns the staged directory
// (empty when the package or its library is not there).
std::string stage(const std::string& root, const std::string& privateRoot, const std::string& name);

}  // namespace gpudrv
}  // namespace azahar_nano
