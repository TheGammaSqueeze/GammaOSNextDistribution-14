/* Copyright (C) 2020 The Android Open Source Project
**
** This software is licensed under the terms of the GNU General Public
** License version 2, as published by the Free Software Foundation, and
** may be copied, distributed, and modified under those terms.
**
** This program is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
*/

#define LOG_TAG "GoldfishComponentStore"

#include <goldfish_codec2/store/GoldfishComponentStore.h>

#include <dlfcn.h>
#include <stdint.h>

#include <memory>
#include <mutex>

#include <C2.h>
#include <C2Config.h>
#include <cutils/properties.h>
#include <log/log.h>

namespace android {

// static
std::shared_ptr<C2ComponentStore> GoldfishComponentStore::Create() {
    ALOGI("%s()", __func__);

    static std::mutex mutex;
    static std::weak_ptr<C2ComponentStore> platformStore;

    std::lock_guard<std::mutex> lock(mutex);
    std::shared_ptr<C2ComponentStore> store = platformStore.lock();
    if (store != nullptr)
        return store;

    store = std::shared_ptr<C2ComponentStore>(new GoldfishComponentStore());
    platformStore = store;
    return store;
}

C2String GoldfishComponentStore::getName() const {
    return "android.componentStore.goldfish";
}

c2_status_t GoldfishComponentStore::ComponentModule::init(std::string libPath) {
    ALOGI("in %s", __func__);
    ALOGI("loading dll of path %s", libPath.c_str());
    mLibHandle = dlopen(libPath.c_str(), RTLD_NOW | RTLD_NODELETE);
    LOG_ALWAYS_FATAL_IF(mLibHandle == nullptr, "could not dlopen %s: %s",
                        libPath.c_str(), dlerror());

    createFactory = (C2ComponentFactory::CreateCodec2FactoryFunc)dlsym(
        mLibHandle, "CreateCodec2Factory");
    LOG_ALWAYS_FATAL_IF(createFactory == nullptr, "createFactory is null in %s",
                        libPath.c_str());

    destroyFactory = (C2ComponentFactory::DestroyCodec2FactoryFunc)dlsym(
        mLibHandle, "DestroyCodec2Factory");
    LOG_ALWAYS_FATAL_IF(destroyFactory == nullptr,
                        "destroyFactory is null in %s", libPath.c_str());

    mComponentFactory = createFactory();
    if (mComponentFactory == nullptr) {
        ALOGD("could not create factory in %s", libPath.c_str());
        mInit = C2_NO_MEMORY;
    } else {
        mInit = C2_OK;
    }

    if (mInit != C2_OK) {
        return mInit;
    }

    std::shared_ptr<C2ComponentInterface> intf;
    c2_status_t res = createInterface(0, &intf);
    if (res != C2_OK) {
        ALOGD("failed to create interface: %d", res);
        return mInit;
    }

    std::shared_ptr<C2Component::Traits> traits(new (std::nothrow)
                                                    C2Component::Traits);
    if (traits) {
        traits->name = intf->getName();

        C2ComponentKindSetting kind;
        C2ComponentDomainSetting domain;
        res = intf->query_vb({&kind, &domain}, {}, C2_MAY_BLOCK, nullptr);
        bool fixDomain = res != C2_OK;
        if (res == C2_OK) {
            traits->kind = kind.value;
            traits->domain = domain.value;
        } else {
            // TODO: remove this fall-back
            ALOGD("failed to query interface for kind and domain: %d", res);

            traits->kind = (traits->name.find("encoder") != std::string::npos)
                               ? C2Component::KIND_ENCODER
                           : (traits->name.find("decoder") != std::string::npos)
                               ? C2Component::KIND_DECODER
                               : C2Component::KIND_OTHER;
        }

        uint32_t mediaTypeIndex =
            traits->kind == C2Component::KIND_ENCODER
                ? C2PortMediaTypeSetting::output::PARAM_TYPE
                : C2PortMediaTypeSetting::input::PARAM_TYPE;
        std::vector<std::unique_ptr<C2Param>> params;
        res = intf->query_vb({}, {mediaTypeIndex}, C2_MAY_BLOCK, &params);
        if (res != C2_OK) {
            ALOGD("failed to query interface: %d", res);
            return mInit;
        }
        if (params.size() != 1u) {
            ALOGD("failed to query interface: unexpected vector size: %zu",
                  params.size());
            return mInit;
        }
        C2PortMediaTypeSetting *mediaTypeConfig =
            C2PortMediaTypeSetting::From(params[0].get());
        if (mediaTypeConfig == nullptr) {
            ALOGD("failed to query media type");
            return mInit;
        }
        traits->mediaType = std::string(
            mediaTypeConfig->m.value,
            strnlen(mediaTypeConfig->m.value, mediaTypeConfig->flexCount()));

        if (fixDomain) {
            if (strncmp(traits->mediaType.c_str(), "audio/", 6) == 0) {
                traits->domain = C2Component::DOMAIN_AUDIO;
            } else if (strncmp(traits->mediaType.c_str(), "video/", 6) == 0) {
                traits->domain = C2Component::DOMAIN_VIDEO;
            } else if (strncmp(traits->mediaType.c_str(), "image/", 6) == 0) {
                traits->domain = C2Component::DOMAIN_IMAGE;
            } else {
                traits->domain = C2Component::DOMAIN_OTHER;
            }
        }

        // TODO: get this properly from the store during emplace
        switch (traits->domain) {
        case C2Component::DOMAIN_AUDIO:
            traits->rank = 8;
            break;
        default:
            traits->rank = 512;
        }

        params.clear();
        res = intf->query_vb({}, {C2ComponentAliasesSetting::PARAM_TYPE},
                             C2_MAY_BLOCK, &params);
        if (res == C2_OK && params.size() == 1u) {
            C2ComponentAliasesSetting *aliasesSetting =
                C2ComponentAliasesSetting::From(params[0].get());
            if (aliasesSetting) {
                // Split aliases on ','
                // This looks simpler in plain C and even std::string would
                // still make a copy.
                char *aliases = ::strndup(aliasesSetting->m.value,
                                          aliasesSetting->flexCount());
                ALOGD("'%s' has aliases: '%s'", intf->getName().c_str(),
                      aliases);

                for (char *tok, *ptr, *str = aliases;
                     (tok = ::strtok_r(str, ",", &ptr)); str = nullptr) {
                    traits->aliases.push_back(tok);
                    ALOGD("adding alias: '%s'", tok);
                }
                free(aliases);
            }
        }
    }
    mTraits = traits;

    return mInit;
}

GoldfishComponentStore::ComponentModule::~ComponentModule() {
    ALOGI("in %s", __func__);
    if (destroyFactory && mComponentFactory) {
        destroyFactory(mComponentFactory);
    }
    if (mLibHandle) {
        ALOGI("unloading dll");
        dlclose(mLibHandle);
    }
}

c2_status_t GoldfishComponentStore::ComponentModule::createInterface(
    c2_node_id_t id, std::shared_ptr<C2ComponentInterface> *interface,
    std::function<void(::C2ComponentInterface *)> deleter) {
    interface->reset();
    if (mInit != C2_OK) {
        return mInit;
    }
    std::shared_ptr<ComponentModule> module = shared_from_this();
    c2_status_t res = mComponentFactory->createInterface(
        id, interface, [module, deleter](C2ComponentInterface *p) mutable {
            // capture module so that we ensure we still have it while deleting
            // interface
            deleter(p);     // delete interface first
            module.reset(); // remove module ref (not technically needed)
        });
    ALOGI("created interface");
    return res;
}

c2_status_t GoldfishComponentStore::ComponentModule::createComponent(
    c2_node_id_t id, std::shared_ptr<C2Component> *component,
    std::function<void(::C2Component *)> deleter) {
    component->reset();
    if (mInit != C2_OK) {
        return mInit;
    }
    std::shared_ptr<ComponentModule> module = shared_from_this();
    c2_status_t res = mComponentFactory->createComponent(
        id, component, [module, deleter](C2Component *p) mutable {
            // capture module so that we ensure we still have it while deleting
            // component
            deleter(p);     // delete component first
            module.reset(); // remove module ref (not technically needed)
        });
    ALOGI("created component");
    return res;
}

std::shared_ptr<const C2Component::Traits>
GoldfishComponentStore::ComponentModule::getTraits() {
    std::unique_lock<std::recursive_mutex> lock(mLock);
    return mTraits;
}

// We have a property set indicating whether to use the host side codec
// or not (ro.boot.qemu.hwcodec.<mLibNameSuffix>).
static std::string BuildHWCodecPropName(const char *libname) {
    using namespace std::literals::string_literals;
    return "ro.boot.qemu.hwcodec."s + libname;
}

static bool useAndroidGoldfishComponentInstance(const char *libname) {
    const std::string propName = BuildHWCodecPropName(libname);
    char propValue[PROP_VALUE_MAX];
    bool myret = property_get(propName.c_str(), propValue, "") > 0 &&
                 strcmp("2", propValue) == 0;
    if (myret) {
        ALOGD("%s %d found prop %s val %s", __func__, __LINE__, propName.c_str(),
              propValue);
    }
    return myret;
}

GoldfishComponentStore::GoldfishComponentStore()
    : mVisited(false), mReflector(std::make_shared<C2ReflectorHelper>()) {

    ALOGW("created goldfish store %p reflector of param %p", this,
          mReflector.get());
    auto emplace = [this](const char *libPath) {
        mComponents.emplace(libPath, libPath);
    };

    if (useAndroidGoldfishComponentInstance("vpxdec")) {
        emplace("libcodec2_goldfish_vp8dec.so");
        emplace("libcodec2_goldfish_vp9dec.so");
    }
    if (useAndroidGoldfishComponentInstance("avcdec")) {
        emplace("libcodec2_goldfish_avcdec.so");
    }
    if (useAndroidGoldfishComponentInstance("hevcdec")) {
        emplace("libcodec2_goldfish_hevcdec.so");
    }
}

c2_status_t
GoldfishComponentStore::copyBuffer(std::shared_ptr<C2GraphicBuffer> src,
                                   std::shared_ptr<C2GraphicBuffer> dst) {
    (void)src;
    (void)dst;
    return C2_OMITTED;
}

c2_status_t GoldfishComponentStore::query_sm(
    const std::vector<C2Param *> &stackParams,
    const std::vector<C2Param::Index> &heapParamIndices,
    std::vector<std::unique_ptr<C2Param>> *const heapParams) const {
    (void)heapParams;
    return stackParams.empty() && heapParamIndices.empty() ? C2_OK
                                                           : C2_BAD_INDEX;
}

c2_status_t GoldfishComponentStore::config_sm(
    const std::vector<C2Param *> &params,
    std::vector<std::unique_ptr<C2SettingResult>> *const failures) {
    (void)failures;
    return params.empty() ? C2_OK : C2_BAD_INDEX;
}

void GoldfishComponentStore::visitComponents() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mVisited) {
        return;
    }
    for (auto &pathAndLoader : mComponents) {
        const C2String &path = pathAndLoader.first;
        ComponentLoader &loader = pathAndLoader.second;
        std::shared_ptr<ComponentModule> module;
        if (loader.fetchModule(&module) == C2_OK) {
            std::shared_ptr<const C2Component::Traits> traits =
                module->getTraits();
            if (traits) {
                mComponentList.push_back(traits);
                mComponentNameToPath.emplace(traits->name, path);
                for (const C2String &alias : traits->aliases) {
                    mComponentNameToPath.emplace(alias, path);
                }
            }
        }
    }
    mVisited = true;
}

std::vector<std::shared_ptr<const C2Component::Traits>>
GoldfishComponentStore::listComponents() {
    // This method SHALL return within 500ms.
    visitComponents();
    return mComponentList;
}

c2_status_t GoldfishComponentStore::findComponent(
    C2String name, std::shared_ptr<ComponentModule> *module) {
    (*module).reset();
    visitComponents();

    auto pos = mComponentNameToPath.find(name);
    if (pos != mComponentNameToPath.end()) {
        return mComponents.at(pos->second).fetchModule(module);
    }
    return C2_NOT_FOUND;
}

c2_status_t GoldfishComponentStore::createComponent(
    C2String name, std::shared_ptr<C2Component> *const component) {
    // This method SHALL return within 100ms.
    component->reset();
    std::shared_ptr<ComponentModule> module;
    c2_status_t res = findComponent(name, &module);
    if (res == C2_OK) {
        // TODO: get a unique node ID
        res = module->createComponent(0, component);
    }
    return res;
}

c2_status_t GoldfishComponentStore::createInterface(
    C2String name, std::shared_ptr<C2ComponentInterface> *const interface) {
    // This method SHALL return within 100ms.
    interface->reset();
    std::shared_ptr<ComponentModule> module;
    c2_status_t res = findComponent(name, &module);
    if (res == C2_OK) {
        // TODO: get a unique node ID
        res = module->createInterface(0, interface);
    }
    return res;
}

c2_status_t GoldfishComponentStore::querySupportedParams_nb(
    std::vector<std::shared_ptr<C2ParamDescriptor>> *const params) const {
    (void)params;
    return C2_OK;
}

c2_status_t GoldfishComponentStore::querySupportedValues_sm(
    std::vector<C2FieldSupportedValuesQuery> &fields) const {
    return fields.empty() ? C2_OK : C2_BAD_INDEX;
}

std::shared_ptr<C2ParamReflector>
GoldfishComponentStore::getParamReflector() const {
    return mReflector;
}

} // namespace android
