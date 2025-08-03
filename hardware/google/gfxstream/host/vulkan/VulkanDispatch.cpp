// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "VulkanDispatch.h"

#include "aemu/base/synchronization/Lock.h"
#include "aemu/base/files/PathUtils.h"
#include "aemu/base/SharedLibrary.h"
#include "aemu/base/system/System.h"
#include "host-common/misc.h"

using android::base::AutoLock;
using android::base::Lock;
using android::base::pj;

namespace gfxstream {
namespace vk {

static void setIcdPath(const std::string& path) {
    if (android::base::pathExists(path.c_str())) {
        // LOG(VERBOSE) << "setIcdPath: path exists: " << path;
    } else {
        // LOG(VERBOSE) << "setIcdPath: path doesn't exist: " << path;
    }
    android::base::setEnvironmentVariable("VK_ICD_FILENAMES", path);
}

static std::string icdJsonNameToProgramAndLauncherPaths(const std::string& icdFilename) {
    std::string suffix = pj({"lib64", "vulkan", icdFilename});
#if defined(_WIN32)
    const char* sep = ";";
#else
    const char* sep = ":";
#endif
    return pj({android::base::getProgramDirectory(), suffix}) + sep +
           pj({android::base::getLauncherDirectory(), suffix});
}

static const char* getTestIcdFilename() {
#if defined(__APPLE__)
    return "libvk_swiftshader.dylib";
#elif defined(__linux__) || defined(__QNX__)
    return "libvk_swiftshader.so";
#elif defined(_WIN32) || defined(_MSC_VER)
    return "vk_swiftshader.dll";
#else
#error Host operating system not supported
#endif
}

static void initIcdPaths(bool forTesting) {
    auto androidIcd = android::base::getEnvironmentVariable("ANDROID_EMU_VK_ICD");
    if (androidIcd == "") {
        // Rely on user to set VK_ICD_FILENAMES
        return;
    } else {
        if (forTesting || androidIcd == "swiftshader") {
            auto res = pj({android::base::getProgramDirectory(), "lib64", "vulkan"});
            // LOG(VERBOSE) << "In test environment or ICD set to swiftshader, using "
            // "Swiftshader ICD";
            auto libPath = pj(
                {android::base::getProgramDirectory(), "lib64", "vulkan", getTestIcdFilename()});
            ;
            if (android::base::pathExists(libPath.c_str())) {
                // LOG(VERBOSE) << "Swiftshader library exists";
            } else {
                // LOG(VERBOSE) << "Swiftshader library doesn't exist, trying launcher path";
                libPath = pj({android::base::getLauncherDirectory(), "lib64", "vulkan",
                              getTestIcdFilename()});
                ;
                if (android::base::pathExists(libPath.c_str())) {
                    // LOG(VERBOSE) << "Swiftshader library found in launcher path";
                } else {
                    // LOG(VERBOSE) << "Swiftshader library not found in program nor launcher path";
                }
            }
            setIcdPath(icdJsonNameToProgramAndLauncherPaths("vk_swiftshader_icd.json"));
            android::base::setEnvironmentVariable("ANDROID_EMU_VK_ICD", "swiftshader");
        } else {
            // LOG(VERBOSE) << "Not in test environment. ICD (blank for default): ["
            // << androidIcd << "]";
            // Mac: Use MoltenVK by default unless GPU mode is set to swiftshader,
            // and switch between that and gfx-rs libportability-icd depending on
            // the environment variable setting.
#ifdef __APPLE__
            if (androidIcd == "portability") {
                setIcdPath(icdJsonNameToProgramAndLauncherPaths("portability-macos.json"));
            } else if (androidIcd == "portability-debug") {
                setIcdPath(icdJsonNameToProgramAndLauncherPaths("portability-macos-debug.json"));
            } else {
                if (androidIcd == "swiftshader") {
                    setIcdPath(icdJsonNameToProgramAndLauncherPaths("vk_swiftshader_icd.json"));
                    android::base::setEnvironmentVariable("ANDROID_EMU_VK_ICD", "swiftshader");
                } else {
                    setIcdPath(icdJsonNameToProgramAndLauncherPaths("MoltenVK_icd.json"));
                    android::base::setEnvironmentVariable("ANDROID_EMU_VK_ICD", "moltenvk");
                }
            }
#else
            // By default, on other platforms, just use whatever the system
            // is packing.
#endif
        }
    }
}

class SharedLibraries {
   public:
    explicit SharedLibraries(size_t sizeLimit = 1) : mSizeLimit(sizeLimit) {}

    size_t size() const { return mLibs.size(); }

    bool addLibrary(const std::string& path) {
        if (size() >= mSizeLimit) {
            fprintf(stderr, "cannot add library %s: full\n", path.c_str());
            return false;
        }

        auto library = android::base::SharedLibrary::open(path.c_str());
        if (library) {
            mLibs.push_back(library);
            fprintf(stderr, "added library %s\n", path.c_str());
            return true;
        } else {
            fprintf(stderr, "cannot add library %s: failed\n", path.c_str());
            return false;
        }
    }

    bool addFirstAvailableLibrary(const std::vector<std::string>& possiblePaths) {
        for (const std::string& possiblePath : possiblePaths) {
            if (addLibrary(possiblePath)) {
                return true;
            }
        }
        return false;
    }

    ~SharedLibraries() = default;

    void* dlsym(const char* name) {
        for (const auto& lib : mLibs) {
            void* funcPtr = reinterpret_cast<void*>(lib->findSymbol(name));
            if (funcPtr) {
                return funcPtr;
            }
        }
        return nullptr;
    }

   private:
    size_t mSizeLimit;
    std::vector<android::base::SharedLibrary*> mLibs;
};

static constexpr size_t getVulkanLibraryNumLimits() {
    // macOS may have both Vulkan loader (for non MoltenVK-specific functions) and
    // MoltenVK library (only for MoltenVK-specific vk...MVK functions) loaded at
    // the same time. So there could be at most 2 libraries loaded. On other systems
    // only one Vulkan loader is allowed.
#ifdef __APPLE__
    return 2;
#else
    return 1;
#endif
}

class VulkanDispatchImpl {
   public:
    VulkanDispatchImpl() : mVulkanLibs(getVulkanLibraryNumLimits()) {}

    void initialize(bool forTesting);

    static std::vector<std::string> getPossibleLoaderPathBasenames() {
#if defined(__APPLE__)
        return std::vector<std::string>{"libvulkan.dylib"};
#elif defined(__linux__)
        // When running applications with Gfxstream as the Vulkan ICD, i.e. with
        //
        //   App -> Vulkan Loader -> Gfxstream ICD -> Vulkan Loader -> Driver ICD
        //
        // Gfxstream needs to use a different nested loader library to avoid issues with
        // conflating/deadlock with the first level loader. Detect that here and look for
        // a "libvulkan_gfxstream" which can be generated with the provided
        // scripts/build-nested-vulkan-loader.sh script.
        const std::vector<std::string> nestedVulkanLoaderVars = {
            "GFXSTREAM_VK_ADD_DRIVER_FILES",
            "GFXSTREAM_VK_ADD_LAYER_PATH",
            "GFXSTREAM_VK_DRIVER_FILES",
            "GFXSTREAM_VK_ICD_FILENAMES",
            "GFXSTREAM_VK_INSTANCE_LAYERS",
            "GFXSTREAM_VK_LAYER_PATH",
            "GFXSTREAM_VK_LAYER_PATH",
            "GFXSTREAM_VK_LOADER_DEBUG",
            "GFXSTREAM_VK_LOADER_DRIVERS_DISABLE",
            "GFXSTREAM_VK_LOADER_DRIVERS_SELECT",
            "GFXSTREAM_VK_LOADER_LAYERS_ALLOW",
            "GFXSTREAM_VK_LOADER_LAYERS_DISABLE",
            "GFXSTREAM_VK_LOADER_LAYERS_ENABLE",
        };
        bool usesNestedVulkanLoader = false;
        for (const std::string& var : nestedVulkanLoaderVars) {
            if (android::base::getEnvironmentVariable(var) != "") {
                usesNestedVulkanLoader = true;
                break;
            }
        }
        if (usesNestedVulkanLoader) {
            return std::vector<std::string>{
                "libvulkan_gfxstream.so",
                "libvulkan_gfxstream.so.1",
            };
        } else {
            return std::vector<std::string>{
                "libvulkan.so",
                "libvulkan.so.1",
            };
        }

#elif defined(_WIN32)
        return std::vector<std::string>{"vulkan-1.dll"};
#else
#error "Unhandled platform in VulkanDispatchImpl."
#endif
    }

    std::vector<std::string> getPossibleLoaderPaths() {
        const std::string explicitPath =
            android::base::getEnvironmentVariable("ANDROID_EMU_VK_LOADER_PATH");
        if (!explicitPath.empty()) {
            return {
                explicitPath,
            };
        }

        const std::vector<std::string> possibleBasenames =
            getPossibleLoaderPathBasenames();

        const std::string explicitIcd =
            android::base::getEnvironmentVariable("ANDROID_EMU_VK_ICD");

#ifdef _WIN32
        constexpr const bool isWindows = true;
#else
        constexpr const bool isWindows = false;
#endif
        if (explicitIcd.empty() || isWindows) {
            return possibleBasenames;
        }

        std::vector<std::string> possibleDirectories;

        if (mForTesting || explicitIcd == "mock") {
            possibleDirectories = {
                pj({android::base::getProgramDirectory(), "testlib64"}),
                pj({android::base::getLauncherDirectory(), "testlib64"}),
            };
        }

        possibleDirectories.push_back(
            pj({android::base::getProgramDirectory(), "lib64", "vulkan"}));
        possibleDirectories.push_back(
            pj({android::base::getLauncherDirectory(), "lib64", "vulkan"}));

        std::vector<std::string> possiblePaths;
        for (const std::string& possibleDirectory : possibleDirectories) {
            for (const std::string& possibleBasename : possibleBasenames) {
                possiblePaths.push_back(pj({possibleDirectory, possibleBasename}));
            }
        }
        return possiblePaths;
    }

#ifdef __APPLE__
    std::vector<std::string> getPossibleMoltenVkPaths() {
        const std::string explicitPath =
            android::base::getEnvironmentVariable("ANDROID_EMU_VK_LOADER_PATH");
        if (!explicitPath.empty()) {
            return {
                explicitPath,
            };
        }

        const std::string& customIcd = android::base::getEnvironmentVariable("ANDROID_EMU_VK_ICD");

        // Skip loader when using MoltenVK as this gives us access to
        // VK_MVK_moltenvk, which is required for external memory support.
        if (!mForTesting && customIcd == "moltenvk") {
            return {
                pj({android::base::getProgramDirectory(), "lib64", "vulkan", "libMoltenVK.dylib"}),
                pj({android::base::getLauncherDirectory(), "lib64", "vulkan", "libMoltenVK.dylib"}),
            };
        }

        return {};
    }
#endif

    void* dlopen() {
        if (mVulkanLibs.size() == 0) {
            mVulkanLibs.addFirstAvailableLibrary(getPossibleLoaderPaths());

#ifdef __APPLE__
            // On macOS it is possible that we are using MoltenVK as the
            // ICD. In that case we need to add MoltenVK libraries to
            // mSharedLibs to use MoltenVK-specific functions.
            mVulkanLibs.addFirstAvailableLibrary(getPossibleMoltenVkPaths());
#endif
        }
        return static_cast<void*>(&mVulkanLibs);
    }

    void* dlsym(void* lib, const char* name) {
        return (void*)((SharedLibraries*)(lib))->dlsym(name);
    }

    VulkanDispatch* dispatch() { return &mDispatch; }

   private:
    Lock mLock;
    bool mForTesting = false;
    bool mInitialized = false;
    VulkanDispatch mDispatch;
    SharedLibraries mVulkanLibs;
};

VulkanDispatchImpl* sVulkanDispatchImpl() {
    static VulkanDispatchImpl* impl = new VulkanDispatchImpl;
    return impl;
}

static void* sVulkanDispatchDlOpen() { return sVulkanDispatchImpl()->dlopen(); }

static void* sVulkanDispatchDlSym(void* lib, const char* sym) {
    return sVulkanDispatchImpl()->dlsym(lib, sym);
}

void VulkanDispatchImpl::initialize(bool forTesting) {
    AutoLock lock(mLock);

    if (mInitialized) {
        return;
    }

    mForTesting = forTesting;
    initIcdPaths(mForTesting);

    init_vulkan_dispatch_from_system_loader(sVulkanDispatchDlOpen, sVulkanDispatchDlSym,
                                            &mDispatch);

    mInitialized = true;
}

VulkanDispatch* vkDispatch(bool forTesting) {
    sVulkanDispatchImpl()->initialize(forTesting);
    return sVulkanDispatchImpl()->dispatch();
}

bool vkDispatchValid(const VulkanDispatch* vk) {
    return vk->vkEnumerateInstanceExtensionProperties != nullptr ||
           vk->vkGetInstanceProcAddr != nullptr || vk->vkGetDeviceProcAddr != nullptr;
}

}  // namespace vk
}  // namespace gfxstream
