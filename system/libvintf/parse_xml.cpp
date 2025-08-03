/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Convert objects from and to xml.

#define LOG_TAG "libvintf"
#include <android-base/logging.h>

#include "parse_xml.h"

#include <type_traits>

#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <tinyxml2.h>

#include "Regex.h"
#include "constants-private.h"
#include "constants.h"
#include "parse_string.h"
#include "parse_xml_for_test.h"
#include "utils.h"

using namespace std::string_literals;

#ifdef LIBVINTF_TARGET
static constexpr bool kDevice = true;
#else
static constexpr bool kDevice = false;
#endif

namespace android {
namespace vintf {

// --------------- tinyxml2 details

using NodeType = tinyxml2::XMLElement;
using DocType = tinyxml2::XMLDocument;

// caller is responsible for deleteDocument() call
inline DocType *createDocument() {
    return new tinyxml2::XMLDocument();
}

// caller is responsible for deleteDocument() call
inline DocType *createDocument(const std::string &xml) {
    DocType *doc = new tinyxml2::XMLDocument();
    if (doc->Parse(xml.c_str()) == tinyxml2::XML_SUCCESS) {
        return doc;
    }
    delete doc;
    return nullptr;
}

inline void deleteDocument(DocType *d) {
    delete d;
}

inline std::string printDocument(DocType *d) {
    tinyxml2::XMLPrinter p;
    d->Print(&p);
    return std::string{p.CStr()};
}

inline NodeType *createNode(const std::string &name, DocType *d) {
    return d->NewElement(name.c_str());
}

inline void appendChild(NodeType *parent, NodeType *child) {
    parent->InsertEndChild(child);
}

inline void appendChild(DocType *parent, NodeType *child) {
    parent->InsertEndChild(child);
}

inline void appendStrAttr(NodeType *e, const std::string &attrName, const std::string &attr) {
    e->SetAttribute(attrName.c_str(), attr.c_str());
}

// text -> text
inline void appendText(NodeType *parent, const std::string &text, DocType *d) {
    parent->InsertEndChild(d->NewText(text.c_str()));
}

inline std::string nameOf(NodeType *root) {
    return root->Name() == NULL ? "" : root->Name();
}

inline std::string getText(NodeType *root) {
    return root->GetText() == NULL ? "" : root->GetText();
}

inline NodeType *getChild(NodeType *parent, const std::string &name) {
    return parent->FirstChildElement(name.c_str());
}

inline NodeType *getRootChild(DocType *parent) {
    return parent->FirstChildElement();
}

inline std::vector<NodeType *> getChildren(NodeType *parent, const std::string &name) {
    std::vector<NodeType *> v;
    for (NodeType *child = parent->FirstChildElement(name.c_str());
         child != nullptr;
         child = child->NextSiblingElement(name.c_str())) {
        v.push_back(child);
    }
    return v;
}

inline bool getAttr(NodeType *root, const std::string &attrName, std::string *s) {
    const char *c = root->Attribute(attrName.c_str());
    if (c == NULL)
        return false;
    *s = c;
    return true;
}

// --------------- tinyxml2 details end.

// Helper functions for XmlConverter
static bool parse(const std::string &attrText, bool *attr) {
    if (attrText == "true" || attrText == "1") {
        *attr = true;
        return true;
    }
    if (attrText == "false" || attrText == "0") {
        *attr = false;
        return true;
    }
    return false;
}

static bool parse(const std::string& attrText, std::optional<std::string>* attr) {
    *attr = attrText;
    return true;
}

static bool parse(const std::string& s, std::optional<uint64_t>* out) {
    uint64_t val;
    if (base::ParseUint(s, &val)) {
        *out = val;
        return true;
    }
    return false;
}

// ---------------------- XmlNodeConverter definitions

// When serializing an object to an XML document, these parameters don't change until
// the object is fully serialized.
// These parameters are also passed to converters of child nodes so they see the same
// serialization parameters.
struct MutateNodeParam {
    DocType* d;
    SerializeFlags::Type flags = SerializeFlags::EVERYTHING;
};

// When deserializing an XML document to an object, these parameters don't change until
// the XML document is fully deserialized.
// * Except metaVersion, which is immediately modified when parsing top-level <manifest>
//   or <compatibility-matrix>, and unchanged thereafter;
//   see HalManifestConverter::BuildObject and CompatibilityMatrixConverter::BuildObject)
// These parameters are also passed to converters of child nodes so they see the same
// deserialization parameters.
struct BuildObjectParam {
    std::string* error;
    Version metaVersion;
    std::string fileName;
};

template <typename Object>
struct XmlNodeConverter {
    XmlNodeConverter() {}
    virtual ~XmlNodeConverter() {}

   protected:
    virtual void mutateNode(const Object& object, NodeType* root, const MutateNodeParam&) const = 0;
    virtual bool buildObject(Object* object, NodeType* root, const BuildObjectParam&) const = 0;

   public:
    // Methods for other (usually parent) converters
    // Name of the XML element.
    virtual std::string elementName() const = 0;
    // Serialize |o| into an XML element.
    inline NodeType* operator()(const Object& o, const MutateNodeParam& param) const {
        NodeType* root = createNode(this->elementName(), param.d);
        this->mutateNode(o, root, param);
        return root;
    }
    // Deserialize XML element |root| into |object|.
    inline bool operator()(Object* object, NodeType* root, const BuildObjectParam& param) const {
        if (nameOf(root) != this->elementName()) {
            return false;
        }
        return this->buildObject(object, root, param);
    }

    // Public methods for android::vintf::fromXml / android::vintf::toXml.
    // Serialize |o| into an XML string.
    inline std::string toXml(const Object& o, SerializeFlags::Type flags) const {
        DocType* doc = createDocument();
        appendChild(doc, (*this)(o, MutateNodeParam{doc, flags}));
        std::string s = printDocument(doc);
        deleteDocument(doc);
        return s;
    }
    // Deserialize XML string |xml| into |o|.
    inline bool fromXml(Object* o, const std::string& xml, std::string* error) const {
        std::string errorBuffer;
        if (error == nullptr) error = &errorBuffer;

        auto doc = createDocument(xml);
        if (doc == nullptr) {
            *error = "Not a valid XML";
            return false;
        }
        // For top-level <manifest> and <compatibility-matrix>, HalManifestConverter and
        // CompatibilityMatrixConverter fills in metaversion and pass down to children.
        // For other nodes, we don't know metaversion of the original XML, so just leave empty
        // for maximum backwards compatibility.
        BuildObjectParam buildObjectParam{error, {}, {}};
        // Pass down filename for the current XML document.
        if constexpr (std::is_base_of_v<WithFileName, Object>) {
            // Get the last filename in case `o` keeps the list of filenames
            std::string_view fileName{o->fileName()};
            if (auto pos = fileName.rfind(':'); pos != fileName.npos) {
                fileName.remove_prefix(pos + 1);
            }
            buildObjectParam.fileName = std::string(fileName);
        }
        bool ret = (*this)(o, getRootChild(doc), buildObjectParam);
        deleteDocument(doc);
        return ret;
    }

    // convenience methods for subclasses to implement virtual functions.

    // All append* functions helps mutateNode() to serialize the object into XML.
    template <typename T>
    inline void appendAttr(NodeType *e, const std::string &attrName, const T &attr) const {
        return appendStrAttr(e, attrName, ::android::vintf::to_string(attr));
    }

    inline void appendAttr(NodeType *e, const std::string &attrName, bool attr) const {
        return appendStrAttr(e, attrName, attr ? "true" : "false");
    }

    // text -> <name>text</name>
    inline void appendTextElement(NodeType *parent, const std::string &name,
                const std::string &text, DocType *d) const {
        NodeType *c = createNode(name, d);
        appendText(c, text, d);
        appendChild(parent, c);
    }

    // text -> <name>text</name>
    template<typename Array>
    inline void appendTextElements(NodeType *parent, const std::string &name,
                const Array &array, DocType *d) const {
        for (const std::string &text : array) {
            NodeType *c = createNode(name, d);
            appendText(c, text, d);
            appendChild(parent, c);
        }
    }

    template <typename T, typename Array>
    inline void appendChildren(NodeType* parent, const XmlNodeConverter<T>& conv,
                               const Array& array, const MutateNodeParam& param) const {
        for (const T &t : array) {
            appendChild(parent, conv(t, param));
        }
    }

    // All parse* functions helps buildObject() to deserialize XML to the object. Returns
    // true if deserialization is successful, false if any error, and "error" will be
    // set to error message.
    template <typename T>
    inline bool parseOptionalAttr(NodeType* root, const std::string& attrName, T&& defaultValue,
                                  T* attr, std::string* /* error */) const {
        std::string attrText;
        bool success = getAttr(root, attrName, &attrText) &&
                       ::android::vintf::parse(attrText, attr);
        if (!success) {
            *attr = std::move(defaultValue);
        }
        return true;
    }

    template <typename T>
    inline bool parseAttr(NodeType* root, const std::string& attrName, T* attr,
                          std::string* error) const {
        std::string attrText;
        bool ret = getAttr(root, attrName, &attrText) && ::android::vintf::parse(attrText, attr);
        if (!ret) {
            *error = "Could not find/parse attr with name \"" + attrName + "\" and value \"" +
                     attrText + "\" for element <" + elementName() + ">";
        }
        return ret;
    }

    inline bool parseAttr(NodeType* root, const std::string& attrName, std::string* attr,
                          std::string* error) const {
        bool ret = getAttr(root, attrName, attr);
        if (!ret) {
            *error = "Could not find attr with name \"" + attrName + "\" for element <" +
                     elementName() + ">";
        }
        return ret;
    }

    inline bool parseTextElement(NodeType* root, const std::string& elementName, std::string* s,
                                 std::string* error) const {
        NodeType *child = getChild(root, elementName);
        if (child == nullptr) {
            *error = "Could not find element with name <" + elementName + "> in element <" +
                     this->elementName() + ">";
            return false;
        }
        *s = getText(child);
        return true;
    }

    inline bool parseOptionalTextElement(NodeType* root, const std::string& elementName,
                                         std::string&& defaultValue, std::string* s,
                                         std::string* /* error */) const {
        NodeType* child = getChild(root, elementName);
        *s = child == nullptr ? std::move(defaultValue) : getText(child);
        return true;
    }

    inline bool parseTextElements(NodeType* root, const std::string& elementName,
                                  std::vector<std::string>* v, std::string* /* error */) const {
        auto nodes = getChildren(root, elementName);
        v->resize(nodes.size());
        for (size_t i = 0; i < nodes.size(); ++i) {
            v->at(i) = getText(nodes[i]);
        }
        return true;
    }

    template <typename T>
    inline bool parseChild(NodeType* root, const XmlNodeConverter<T>& conv, T* t,
                           const BuildObjectParam& param) const {
        NodeType *child = getChild(root, conv.elementName());
        if (child == nullptr) {
            *param.error = "Could not find element with name <" + conv.elementName() +
                           "> in element <" + this->elementName() + ">";
            return false;
        }
        return conv(t, child, param);
    }

    template <typename T>
    inline bool parseOptionalChild(NodeType* root, const XmlNodeConverter<T>& conv,
                                   T&& defaultValue, T* t, const BuildObjectParam& param) const {
        NodeType *child = getChild(root, conv.elementName());
        if (child == nullptr) {
            *t = std::move(defaultValue);
            return true;
        }
        return conv(t, child, param);
    }

    template <typename T>
    inline bool parseOptionalChild(NodeType* root, const XmlNodeConverter<T>& conv,
                                   std::optional<T>* t, const BuildObjectParam& param) const {
        NodeType* child = getChild(root, conv.elementName());
        if (child == nullptr) {
            *t = std::nullopt;
            return true;
        }
        *t = std::make_optional<T>();
        return conv(&**t, child, param);
    }

    template <typename T>
    inline bool parseChildren(NodeType* root, const XmlNodeConverter<T>& conv, std::vector<T>* v,
                              const BuildObjectParam& param) const {
        auto nodes = getChildren(root, conv.elementName());
        v->resize(nodes.size());
        for (size_t i = 0; i < nodes.size(); ++i) {
            if (!conv(&v->at(i), nodes[i], param)) {
                *param.error = "Could not parse element with name <" + conv.elementName() +
                               "> in element <" + this->elementName() + ">: " + *param.error;
                return false;
            }
        }
        return true;
    }

    template <typename Container, typename T = typename Container::value_type,
              typename = typename Container::key_compare>
    inline bool parseChildren(NodeType* root, const XmlNodeConverter<T>& conv, Container* s,
                              const BuildObjectParam& param) const {
        std::vector<T> vec;
        if (!parseChildren(root, conv, &vec, param)) {
            return false;
        }
        s->clear();
        s->insert(vec.begin(), vec.end());
        if (s->size() != vec.size()) {
            *param.error = "Duplicated elements <" + conv.elementName() + "> in element <" +
                           this->elementName() + ">";
            s->clear();
            return false;
        }
        return true;
    }

    template <typename K, typename V>
    inline bool parseChildren(NodeType* root, const XmlNodeConverter<std::pair<K, V>>& conv,
                              std::map<K, V>* s, const BuildObjectParam& param) const {
        return parseChildren<std::map<K, V>, std::pair<K, V>>(root, conv, s, param);
    }

    inline bool parseText(NodeType* node, std::string* s, std::string* /* error */) const {
        *s = getText(node);
        return true;
    }

    template <typename T>
    inline bool parseText(NodeType* node, T* s, std::string* error) const {
        bool (*parser)(const std::string&, T*) = ::android::vintf::parse;
        return parseText(node, s, {parser}, error);
    }

    template <typename T>
    inline bool parseText(NodeType* node, T* s,
                          const std::function<bool(const std::string&, T*)>& parse,
                          std::string* error) const {
        std::string text = getText(node);
        bool ret = parse(text, s);
        if (!ret) {
            *error = "Could not parse text \"" + text + "\" in element <" + elementName() + ">";
        }
        return ret;
    }
};

template<typename Object>
struct XmlTextConverter : public XmlNodeConverter<Object> {
    void mutateNode(const Object& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendText(root, ::android::vintf::to_string(object), param.d);
    }
    bool buildObject(Object* object, NodeType* root, const BuildObjectParam& param) const override {
        return this->parseText(root, object, param.error);
    }
};

template <typename Pair, typename FirstConverter, typename SecondConverter>
struct XmlPairConverter : public XmlNodeConverter<Pair> {
    void mutateNode(const Pair& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendChild(root, FirstConverter{}(object.first, param));
        appendChild(root, SecondConverter{}(object.second, param));
    }
    bool buildObject(Pair* object, NodeType* root, const BuildObjectParam& param) const override {
        return this->parseChild(root, FirstConverter{}, &object->first, param) &&
               this->parseChild(root, SecondConverter{}, &object->second, param);
    }
};

// ---------------------- XmlNodeConverter definitions end

struct VersionConverter : public XmlTextConverter<Version> {
    std::string elementName() const override { return "version"; }
};

struct SepolicyVersionConverter : public XmlTextConverter<SepolicyVersion> {
    std::string elementName() const override { return "version"; }
};

struct VersionRangeConverter : public XmlTextConverter<VersionRange> {
    std::string elementName() const override { return "version"; }
};

struct SepolicyVersionRangeConverter : public XmlTextConverter<SepolicyVersionRange> {
    std::string elementName() const override { return "sepolicy-version"; }
};

// <version>100</version> <=> Version{kFakeAidlMajorVersion, 100}
struct AidlVersionConverter : public XmlNodeConverter<Version> {
    std::string elementName() const override { return "version"; }
    void mutateNode(const Version& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendText(root, aidlVersionToString(object), param.d);
    }
    bool buildObject(Version* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        return parseText(root, object, {parseAidlVersion}, param.error);
    }
};

// <version>100</version> <=> VersionRange{kFakeAidlMajorVersion, 100, 100}
// <version>100-105</version> <=> VersionRange{kFakeAidlMajorVersion, 100, 105}
struct AidlVersionRangeConverter : public XmlNodeConverter<VersionRange> {
    std::string elementName() const override { return "version"; }
    void mutateNode(const VersionRange& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendText(root, aidlVersionRangeToString(object), param.d);
    }
    bool buildObject(VersionRange* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        return parseText(root, object, {parseAidlVersionRange}, param.error);
    }
};

struct TransportArchConverter : public XmlNodeConverter<TransportArch> {
    std::string elementName() const override { return "transport"; }
    void mutateNode(const TransportArch& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        if (object.arch != Arch::ARCH_EMPTY) {
            appendAttr(root, "arch", object.arch);
        }
        if (object.ip.has_value()) {
            appendAttr(root, "ip", *object.ip);
        }
        if (object.port.has_value()) {
            appendAttr(root, "port", *object.port);
        }
        appendText(root, ::android::vintf::to_string(object.transport), param.d);
    }
    bool buildObject(TransportArch* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        if (!parseOptionalAttr(root, "arch", Arch::ARCH_EMPTY, &object->arch, param.error) ||
            !parseOptionalAttr(root, "ip", {}, &object->ip, param.error) ||
            !parseOptionalAttr(root, "port", {}, &object->port, param.error) ||
            !parseText(root, &object->transport, param.error)) {
            return false;
        }
        if (!object->isValid(param.error)) {
            return false;
        }
        return true;
    }
};

struct KernelConfigTypedValueConverter : public XmlNodeConverter<KernelConfigTypedValue> {
    std::string elementName() const override { return "value"; }
    void mutateNode(const KernelConfigTypedValue& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendAttr(root, "type", object.mType);
        appendText(root, ::android::vintf::to_string(object), param.d);
    }
    bool buildObject(KernelConfigTypedValue* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        std::string stringValue;
        if (!parseAttr(root, "type", &object->mType, param.error) ||
            !parseText(root, &stringValue, param.error)) {
            return false;
        }
        if (!::android::vintf::parseKernelConfigValue(stringValue, object)) {
            *param.error = "Could not parse kernel config value \"" + stringValue + "\"";
            return false;
        }
        return true;
    }
};

struct KernelConfigKeyConverter : public XmlTextConverter<KernelConfigKey> {
    std::string elementName() const override { return "key"; }
};

struct MatrixKernelConfigConverter : public XmlPairConverter<KernelConfig, KernelConfigKeyConverter,
                                                             KernelConfigTypedValueConverter> {
    std::string elementName() const override { return "config"; }
};

struct HalInterfaceConverter : public XmlNodeConverter<HalInterface> {
    std::string elementName() const override { return "interface"; }
    void mutateNode(const HalInterface& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        if (!object.name().empty()) {
            appendTextElement(root, "name", object.name(), param.d);
        }
        appendTextElements(root, "instance", object.mInstances, param.d);
        appendTextElements(root, "regex-instance", object.mRegexes, param.d);
    }
    bool buildObject(HalInterface* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        std::vector<std::string> instances;
        std::vector<std::string> regexes;
        if (!parseOptionalTextElement(root, "name", {}, &object->mName, param.error) ||
            !parseTextElements(root, "instance", &instances, param.error) ||
            !parseTextElements(root, "regex-instance", &regexes, param.error)) {
            return false;
        }
        bool success = true;
        for (const auto& e : instances) {
            if (!object->insertInstance(e, false /* isRegex */)) {
                if (!param.error->empty()) *param.error += "\n";
                *param.error += "Duplicated instance '" + e + "' in " + object->name();
                success = false;
            }
        }
        for (const auto& e : regexes) {
            details::Regex regex;
            if (!regex.compile(e)) {
                if (!param.error->empty()) *param.error += "\n";
                *param.error += "Invalid regular expression '" + e + "' in " + object->name();
                success = false;
            }
            if (!object->insertInstance(e, true /* isRegex */)) {
                if (!param.error->empty()) *param.error += "\n";
                *param.error += "Duplicated regex-instance '" + e + "' in " + object->name();
                success = false;
            }
        }
        return success;
    }
};

struct MatrixHalConverter : public XmlNodeConverter<MatrixHal> {
    std::string elementName() const override { return "hal"; }
    void mutateNode(const MatrixHal& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendAttr(root, "format", object.format);
        appendAttr(root, "optional", object.optional);
        // Only include update-via-apex if enabled
        if (object.updatableViaApex) {
            appendAttr(root, "updatable-via-apex", object.updatableViaApex);
        }
        appendTextElement(root, "name", object.name, param.d);
        if (object.format == HalFormat::AIDL) {
            // By default, buildObject() assumes a <version>0</version> tag if no <version> tag
            // is specified. Don't output any <version> tag if there's only one <version>0</version>
            // tag.
            if (object.versionRanges.size() != 1 ||
                object.versionRanges[0] != details::kDefaultAidlVersionRange) {
                appendChildren(root, AidlVersionRangeConverter{}, object.versionRanges, param);
            }
        } else {
            appendChildren(root, VersionRangeConverter{}, object.versionRanges, param);
        }
        appendChildren(root, HalInterfaceConverter{}, iterateValues(object.interfaces), param);
    }
    bool buildObject(MatrixHal* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        std::vector<HalInterface> interfaces;
        if (!parseOptionalAttr(root, "format", HalFormat::HIDL, &object->format, param.error) ||
            !parseOptionalAttr(root, "optional", true /* defaultValue */, &object->optional,
                               param.error) ||
            !parseOptionalAttr(root, "updatable-via-apex", false /* defaultValue */,
                               &object->updatableViaApex, param.error) ||
            !parseTextElement(root, "name", &object->name, param.error) ||
            !parseChildren(root, HalInterfaceConverter{}, &interfaces, param)) {
            return false;
        }
        if (object->format == HalFormat::AIDL) {
            if (!parseChildren(root, AidlVersionRangeConverter{}, &object->versionRanges, param)) {
                return false;
            }
            // Insert fake version for AIDL HALs so that compatibility check for AIDL and other
            // HAL formats can be unified.
            if (object->versionRanges.empty()) {
                object->versionRanges.push_back(details::kDefaultAidlVersionRange);
            }
        } else {
            if (!parseChildren(root, VersionRangeConverter{}, &object->versionRanges, param)) {
                return false;
            }
        }
        for (auto&& interface : interfaces) {
            std::string name{interface.name()};
            auto res = object->interfaces.emplace(std::move(name), std::move(interface));
            if (!res.second) {
                *param.error = "Duplicated interface entry \"" + res.first->first +
                               "\"; if additional instances are needed, add them to the "
                               "existing <interface> node.";
                return false;
            }
        }
        if (!checkAdditionalRestrictionsOnHal(*object, param.error)) {
            return false;
        }

        if (!object->isValid(param.error)) {
            param.error->insert(0, "'" + object->name + "' is not a valid Matrix HAL: ");
            return false;
        }
        return true;
    }

   private:
    bool checkAdditionalRestrictionsOnHal(const MatrixHal& hal, std::string* error) const {
        // Do not check for target-side libvintf to avoid restricting ability for upgrade
        // accidentally.
        if constexpr (kDevice) {
            return true;
        }
        if (hal.getName() == "netutils-wrapper") {
            if (hal.versionRanges.size() != 1) {
                *error =
                    "netutils-wrapper HAL must specify exactly one version x.0, "
                    "but multiple <version> element is specified.";
                return false;
            }
            const VersionRange& v = hal.versionRanges.at(0);
            if (!v.isSingleVersion()) {
                *error =
                    "netutils-wrapper HAL must specify exactly one version x.0, "
                    "but a range is provided. Perhaps you mean '" +
                    to_string(Version{v.majorVer, 0}) + "'?";
                return false;
            }
            if (v.minMinor != 0) {
                *error =
                    "netutils-wrapper HAL must specify exactly one version x.0, "
                    "but minor version is not 0. Perhaps you mean '" +
                    to_string(Version{v.majorVer, 0}) + "'?";
                return false;
            }
        }
        return true;
    }
};

struct MatrixKernelConditionsConverter : public XmlNodeConverter<std::vector<KernelConfig>> {
    std::string elementName() const override { return "conditions"; }
    void mutateNode(const std::vector<KernelConfig>& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendChildren(root, MatrixKernelConfigConverter{}, object, param);
    }
    bool buildObject(std::vector<KernelConfig>* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        return parseChildren(root, MatrixKernelConfigConverter{}, object, param);
    }
};

struct MatrixKernelConverter : public XmlNodeConverter<MatrixKernel> {
    std::string elementName() const override { return "kernel"; }
    void mutateNode(const MatrixKernel& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        KernelVersion kv = object.mMinLts;
        if (!param.flags.isKernelMinorRevisionEnabled()) {
            kv.minorRev = 0u;
        }
        appendAttr(root, "version", kv);

        if (object.getSourceMatrixLevel() != Level::UNSPECIFIED) {
            appendAttr(root, "level", object.getSourceMatrixLevel());
        }

        if (!object.mConditions.empty()) {
            appendChild(root, MatrixKernelConditionsConverter{}(object.mConditions, param));
        }
        if (param.flags.isKernelConfigsEnabled()) {
            appendChildren(root, MatrixKernelConfigConverter{}, object.mConfigs, param);
        }
    }
    bool buildObject(MatrixKernel* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        Level sourceMatrixLevel = Level::UNSPECIFIED;
        if (!parseAttr(root, "version", &object->mMinLts, param.error) ||
            !parseOptionalAttr(root, "level", Level::UNSPECIFIED, &sourceMatrixLevel,
                               param.error) ||
            !parseOptionalChild(root, MatrixKernelConditionsConverter{}, {}, &object->mConditions,
                                param) ||
            !parseChildren(root, MatrixKernelConfigConverter{}, &object->mConfigs, param)) {
            return false;
        }
        object->setSourceMatrixLevel(sourceMatrixLevel);
        return true;
    }
};

struct FqInstanceConverter : public XmlTextConverter<FqInstance> {
    std::string elementName() const override { return "fqname"; }
};

// Convert ManifestHal from and to XML. Returned object is guaranteed to have
// .isValid() == true.
struct ManifestHalConverter : public XmlNodeConverter<ManifestHal> {
    std::string elementName() const override { return "hal"; }
    void mutateNode(const ManifestHal& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendAttr(root, "format", object.format);
        appendTextElement(root, "name", object.name, param.d);
        if (!object.transportArch.empty()) {
            appendChild(root, TransportArchConverter{}(object.transportArch, param));
        }
        if (object.format == HalFormat::AIDL) {
            // By default, buildObject() assumes a <version>0</version> tag if no <version> tag
            // is specified. Don't output any <version> tag if there's only one <version>0</version>
            // tag.
            if (object.versions.size() != 1 || object.versions[0] != details::kDefaultAidlVersion) {
                appendChildren(root, AidlVersionConverter{}, object.versions, param);
            }
        } else {
            appendChildren(root, VersionConverter{}, object.versions, param);
        }
        if (object.isOverride()) {
            appendAttr(root, "override", object.isOverride());
        }
        if (const auto& apex = object.updatableViaApex(); apex.has_value()) {
            appendAttr(root, "updatable-via-apex", apex.value());
        }
        if (param.flags.isFqnameEnabled()) {
            std::set<std::string> simpleFqInstances;
            object.forEachInstance([&simpleFqInstances](const auto& manifestInstance) {
                simpleFqInstances.emplace(manifestInstance.getSimpleFqInstance());
                return true;
            });
            appendTextElements(root, FqInstanceConverter{}.elementName(), simpleFqInstances,
                               param.d);
        }
        if (object.getMaxLevel() != Level::UNSPECIFIED) {
            appendAttr(root, "max-level", object.getMaxLevel());
        }
        if (object.getMinLevel() != Level::UNSPECIFIED) {
            appendAttr(root, "min-level", object.getMinLevel());
        }
    }
    bool buildObject(ManifestHal* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        std::vector<HalInterface> interfaces;
        if (!parseOptionalAttr(root, "format", HalFormat::HIDL, &object->format, param.error) ||
            !parseOptionalAttr(root, "override", false, &object->mIsOverride, param.error) ||
            !parseOptionalAttr(root, "updatable-via-apex", {}, &object->mUpdatableViaApex,
                               param.error) ||
            !parseTextElement(root, "name", &object->name, param.error) ||
            !parseOptionalChild(root, TransportArchConverter{}, {}, &object->transportArch,
                                param) ||
            !parseOptionalAttr(root, "max-level", Level::UNSPECIFIED, &object->mMaxLevel,
                               param.error) ||
            !parseOptionalAttr(root, "min-level", Level::UNSPECIFIED, &object->mMinLevel,
                               param.error)) {
            return false;
        }

        std::string_view apexName = parseApexName(param.fileName);
        if (!apexName.empty()) {
            if (object->mUpdatableViaApex.has_value()) {
                // When defined in APEX, updatable-via-apex can be either
                // - ""(empty)  : the HAL isn't updatable even if it's in APEX
                // - {apex name}: the HAL is updtable via the current APEX
                const std::string& updatableViaApex = object->mUpdatableViaApex.value();
                if (!updatableViaApex.empty() && apexName.compare(updatableViaApex) != 0) {
                    *param.error = "Invalid APEX HAL " + object->name + ": updatable-via-apex " +
                                   updatableViaApex + " doesn't match with the defining APEX " +
                                   std::string(apexName) + "\n";
                    return false;
                }
            } else {
                // Set updatable-via-apex to the defining APEX when it's not set explicitly.
                // This should be set before calling insertInstances() which copies the current
                // value to ManifestInstance.
                object->mUpdatableViaApex = apexName;
            }
        }

        switch (object->format) {
            case HalFormat::HIDL: {
                if (!parseChildren(root, VersionConverter{}, &object->versions, param))
                    return false;
                if (object->transportArch.empty()) {
                    *param.error =
                        "HIDL HAL '" + object->name + "' should have <transport> defined.";
                    return false;
                }
                if (object->transportArch.transport == Transport::INET ||
                    object->transportArch.ip.has_value() ||
                    object->transportArch.port.has_value()) {
                    *param.error = "HIDL HAL '" + object->name +
                                   "' should not have <transport> \"inet\" " +
                                   "or ip or port attributes defined.";
                    return false;
                }
            } break;
            case HalFormat::NATIVE: {
                if (!parseChildren(root, VersionConverter{}, &object->versions, param))
                    return false;
                if (!object->transportArch.empty()) {
                    *param.error =
                        "Native HAL '" + object->name + "' should not have <transport> defined.";
                    return false;
                }
            } break;
            case HalFormat::AIDL: {
                if (!object->transportArch.empty() &&
                    object->transportArch.transport != Transport::INET) {
                    if (param.metaVersion >= kMetaVersionAidlInet) {
                        *param.error = "AIDL HAL '" + object->name +
                                       R"(' only supports "inet" or empty <transport>, found ")" +
                                       to_string(object->transportArch) + "\"";
                        return false;
                    }
                    LOG(WARNING) << "Ignoring <transport> on manifest <hal format=\"aidl\"> "
                                 << object->name << ". Only \"inet\" supported.";
                    object->transportArch = {};
                }
                if (!parseChildren(root, AidlVersionConverter{}, &object->versions, param)) {
                    return false;
                }
                // Insert fake version for AIDL HALs so that forEachInstance works.
                if (object->versions.empty()) {
                    object->versions.push_back(details::kDefaultAidlVersion);
                }
            } break;
            default: {
                LOG(FATAL) << "Unhandled HalFormat "
                           << static_cast<typename std::underlying_type<HalFormat>::type>(
                                  object->format);
            } break;
        }
        if (!object->transportArch.isValid(param.error)) return false;

        // Parse <fqname> into fqInstances list
        std::set<FqInstance> fqInstances;
        if (!parseChildren(root, FqInstanceConverter{}, &fqInstances, param)) {
            return false;
        }

        // Handle deprecated <interface> x <instance>
        if (!parseChildren(root, HalInterfaceConverter{}, &interfaces, param)) {
            return false;
        }
        // Check duplicated <interface><name>
        std::set<std::string> interface_names;
        for (auto &&interface : interfaces) {
            auto res = interface_names.emplace(interface.name());
            if (!res.second) {
                *param.error = "Duplicated interface entry \"" + *res.first +
                               "\"; if additional instances are needed, add them to the "
                               "existing <interface> node.";
                return false;
            }
        }
        // Turn <version> x <interface> x <instance> into <fqname>s; insert into
        // fqInstances list.
        bool convertedInstancesIntoFqnames = false;
        for (const auto& v : object->versions) {
            for (const auto& intf : interfaces) {
                if (param.metaVersion >= kMetaVersionNoHalInterfaceInstance &&
                    (object->format == HalFormat::HIDL || object->format == HalFormat::AIDL) &&
                    !intf.hasAnyInstance()) {
                    *param.error +=
                        "<hal> " + object->name + " <interface> " + intf.name() +
                        " has no <instance>. Either specify <instance> or, "
                        "preferably, specify <fqname> and delete <version> and <interface>.";
                    return false;
                }
                bool cont = intf.forEachInstance(
                    [&v, &fqInstances, &convertedInstancesIntoFqnames, &object, &param](
                        const auto& interface, const auto& instance, bool /* isRegex */) {
                        auto fqInstance = details::convertLegacyInstanceIntoFqInstance(
                            object->name, v, interface, instance, object->format, param.error);

                        if (!fqInstance.has_value()) {
                            return false;
                        }

                        // Check for duplication in fqInstances.
                        // Before kMetaVersionNoHalInterfaceInstance: It is okay to have duplication
                        // between <interface> and <fqname>.
                        // After kMetaVersionNoHalInterfaceInstance: Duplication between
                        // <interface> and <fqname> is not allowed.
                        auto&& [it, inserted] = fqInstances.emplace(std::move(fqInstance.value()));
                        if (param.metaVersion >= kMetaVersionNoHalInterfaceInstance && !inserted) {
                            std::string debugString =
                                object->format == HalFormat::AIDL
                                    ? toAidlFqnameString(object->name, interface, instance)
                                    : toFQNameString(object->name, v, interface, instance);
                            *param.error = "Duplicated " + debugString +
                                           " in <interface><instance> and <fqname>. ";
                            if constexpr (kDevice) {
                                *param.error +=
                                    "(Did you copy source manifests to the device directly "
                                    "without going through assemble_vintf, e.g. not using "
                                    "DEVICE_MANIFEST_FILE or ODM_MANIFEST_FILES?)";
                            } else {
                                *param.error += "Remove deprecated <interface>.";
                            }
                            return false;
                        }

                        convertedInstancesIntoFqnames = true;
                        return true;  // continue
                    });
                if (!cont) {
                    return false;
                }
            }
        }

        if (!checkAdditionalRestrictionsOnHal(*object, param.error)) {
            return false;
        }

        // For HIDL, if any <version> x <interface> x <instance> tuple, all <version>
        // tags can be cleared. <version> information is already in <fqname>'s.
        // For AIDL, <version> information is not in <fqname>, so don't clear them.
        // For HALs with only <version> but no <interface>
        // (e.g. native HALs like netutils-wrapper), <version> is kept.
        if (convertedInstancesIntoFqnames && object->format != HalFormat::AIDL) {
            object->versions.clear();
        }

        std::set<FqInstance> fqInstancesToInsert;
        for (auto& e : fqInstances) {
            if (e.hasPackage()) {
                *param.error = "Should not specify package: \"" + e.string() + "\"";
                return false;
            }
            if (object->format == HalFormat::AIDL) {
                // <fqname> in AIDL HALs should not contain version.
                if (e.hasVersion()) {
                    *param.error = "Should not specify version in <fqname> for AIDL HAL: \"" +
                                   e.string() + "\"";
                    return false;
                }
                // Put in the fake kDefaultAidlVersion so that HalManifest can
                // store it in an FqInstance object with a non-empty package.
                FqInstance withFakeVersion;
                if (!withFakeVersion.setTo(details::kDefaultAidlVersion.majorVer,
                                           details::kDefaultAidlVersion.minorVer, e.getInterface(),
                                           e.getInstance())) {
                    return false;
                }
                fqInstancesToInsert.emplace(std::move(withFakeVersion));
            } else {
                fqInstancesToInsert.emplace(std::move(e));
            }
        }

        if (param.metaVersion >= kMetaVersionNoHalInterfaceInstance &&
            (object->format == HalFormat::HIDL || object->format == HalFormat::AIDL) &&
            fqInstancesToInsert.empty() && !object->isOverride()) {
            *param.error = "<hal> " + object->name + " has no instance. Fix by adding <fqname>.";
            return false;
        }

        bool allowMajorVersionDup = param.metaVersion < kMetaVersionNoHalInterfaceInstance;
        if (!object->insertInstances(fqInstancesToInsert, allowMajorVersionDup, param.error)) {
            return false;
        }

        if (!object->isValid(param.error)) {
            param.error->insert(0, "'" + object->name + "' is not a valid Manifest HAL: ");
            return false;
        }

        return true;
    }

   private:
    bool checkAdditionalRestrictionsOnHal(const ManifestHal& hal, std::string* error) const {
        // Do not check for target-side libvintf to avoid restricting upgrade accidentally.
        if constexpr (kDevice) {
            return true;
        }
        if (hal.getName() == "netutils-wrapper") {
            for (const Version& v : hal.versions) {
                if (v.minorVer != 0) {
                    *error =
                        "netutils-wrapper HAL must specify exactly one version x.0, "
                        "but minor version is not 0. Perhaps you mean '" +
                        to_string(Version{v.majorVer, 0}) + "'?";
                    return false;
                }
            }
        }
        return true;
    }
};

struct KernelSepolicyVersionConverter : public XmlTextConverter<KernelSepolicyVersion> {
    std::string elementName() const override { return "kernel-sepolicy-version"; }
};

struct SepolicyConverter : public XmlNodeConverter<Sepolicy> {
    std::string elementName() const override { return "sepolicy"; }
    void mutateNode(const Sepolicy& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendChild(root, KernelSepolicyVersionConverter{}(object.kernelSepolicyVersion(), param));
        appendChildren(root, SepolicyVersionRangeConverter{}, object.sepolicyVersions(), param);
    }
    bool buildObject(Sepolicy* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        if (!parseChild(root, KernelSepolicyVersionConverter{}, &object->mKernelSepolicyVersion,
                        param) ||
            !parseChildren(root, SepolicyVersionRangeConverter{}, &object->mSepolicyVersionRanges,
                           param)) {
            return false;
        }
        return true;
    }
};

struct [[deprecated]] VndkVersionRangeConverter : public XmlTextConverter<VndkVersionRange> {
    std::string elementName() const override { return "version"; }
};

struct VndkVersionConverter : public XmlTextConverter<std::string> {
    std::string elementName() const override { return "version"; }
};

struct VndkLibraryConverter : public XmlTextConverter<std::string> {
    std::string elementName() const override { return "library"; }
};

struct [[deprecated]] VndkConverter : public XmlNodeConverter<Vndk> {
    std::string elementName() const override { return "vndk"; }
    void mutateNode(const Vndk& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendChild(root, VndkVersionRangeConverter{}(object.mVersionRange, param));
        appendChildren(root, VndkLibraryConverter{}, object.mLibraries, param);
    }
    bool buildObject(Vndk* object, NodeType* root, const BuildObjectParam& param) const override {
        if (!parseChild(root, VndkVersionRangeConverter{}, &object->mVersionRange, param) ||
            !parseChildren(root, VndkLibraryConverter{}, &object->mLibraries, param)) {
            return false;
        }
        return true;
    }
};

struct VendorNdkConverter : public XmlNodeConverter<VendorNdk> {
    std::string elementName() const override { return "vendor-ndk"; }
    void mutateNode(const VendorNdk& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendChild(root, VndkVersionConverter{}(object.mVersion, param));
        appendChildren(root, VndkLibraryConverter{}, object.mLibraries, param);
    }
    bool buildObject(VendorNdk* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        if (!parseChild(root, VndkVersionConverter{}, &object->mVersion, param) ||
            !parseChildren(root, VndkLibraryConverter{}, &object->mLibraries, param)) {
            return false;
        }
        return true;
    }
};

struct SystemSdkVersionConverter : public XmlTextConverter<std::string> {
    std::string elementName() const override { return "version"; }
};

struct SystemSdkConverter : public XmlNodeConverter<SystemSdk> {
    std::string elementName() const override { return "system-sdk"; }
    void mutateNode(const SystemSdk& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendChildren(root, SystemSdkVersionConverter{}, object.versions(), param);
    }
    bool buildObject(SystemSdk* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        return parseChildren(root, SystemSdkVersionConverter{}, &object->mVersions, param);
    }
};

struct HalManifestSepolicyConverter : public XmlNodeConverter<SepolicyVersion> {
    std::string elementName() const override { return "sepolicy"; }
    void mutateNode(const SepolicyVersion& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendChild(root, SepolicyVersionConverter{}(object, param));
    }
    bool buildObject(SepolicyVersion* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        return parseChild(root, SepolicyVersionConverter{}, object, param);
    }
};

struct ManifestXmlFileConverter : public XmlNodeConverter<ManifestXmlFile> {
    std::string elementName() const override { return "xmlfile"; }
    void mutateNode(const ManifestXmlFile& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendTextElement(root, "name", object.name(), param.d);
        appendChild(root, VersionConverter{}(object.version(), param));
        if (!object.overriddenPath().empty()) {
            appendTextElement(root, "path", object.overriddenPath(), param.d);
        }
    }
    bool buildObject(ManifestXmlFile* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        if (!parseTextElement(root, "name", &object->mName, param.error) ||
            !parseChild(root, VersionConverter{}, &object->mVersion, param) ||
            !parseOptionalTextElement(root, "path", {}, &object->mOverriddenPath, param.error)) {
            return false;
        }
        return true;
    }
};

struct StringKernelConfigKeyConverter : public XmlTextConverter<std::string> {
    std::string elementName() const override { return "key"; }
};

struct KernelConfigValueConverter : public XmlTextConverter<std::string> {
    std::string elementName() const override { return "value"; }
};

struct StringKernelConfigConverter
    : public XmlPairConverter<std::pair<std::string, std::string>, StringKernelConfigKeyConverter,
                              KernelConfigValueConverter> {
    std::string elementName() const override { return "config"; }
};

struct KernelInfoConverter : public XmlNodeConverter<KernelInfo> {
    std::string elementName() const override { return "kernel"; }
    void mutateNode(const KernelInfo& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        if (object.version() != KernelVersion{}) {
            appendAttr(root, "version", object.version());
        }
        if (object.level() != Level::UNSPECIFIED) {
            appendAttr(root, "target-level", object.level());
        }
        if (param.flags.isKernelConfigsEnabled()) {
            appendChildren(root, StringKernelConfigConverter{}, object.configs(), param);
        }
    }
    bool buildObject(KernelInfo* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        return parseOptionalAttr(root, "version", {}, &object->mVersion, param.error) &&
               parseOptionalAttr(root, "target-level", Level::UNSPECIFIED, &object->mLevel,
                                 param.error) &&
               parseChildren(root, StringKernelConfigConverter{}, &object->mConfigs, param);
    }
};

struct HalManifestConverter : public XmlNodeConverter<HalManifest> {
    std::string elementName() const override { return "manifest"; }
    void mutateNode(const HalManifest& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        if (param.flags.isMetaVersionEnabled()) {
            // Append the current metaversion of libvintf because the XML file
            // is generated with libvintf @ current meta version.
            appendAttr(root, "version", kMetaVersion);
        }
        if (param.flags.isSchemaTypeEnabled()) {
            appendAttr(root, "type", object.mType);
        }

        if (param.flags.isHalsEnabled()) {
            appendChildren(root, ManifestHalConverter{}, object.getHals(), param);
        }
        if (object.mType == SchemaType::DEVICE) {
            if (param.flags.isSepolicyEnabled()) {
                if (object.device.mSepolicyVersion != SepolicyVersion{}) {
                    appendChild(root, HalManifestSepolicyConverter{}(object.device.mSepolicyVersion,
                                                                     param));
                }
            }
            if (object.mLevel != Level::UNSPECIFIED) {
                this->appendAttr(root, "target-level", object.mLevel);
            }

            if (param.flags.isKernelEnabled()) {
                if (!!object.kernel()) {
                    appendChild(root, KernelInfoConverter{}(*object.kernel(), param));
                }
            }
        } else if (object.mType == SchemaType::FRAMEWORK) {
            if (param.flags.isVndkEnabled()) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
                appendChildren(root, VndkConverter{}, object.framework.mVndks, param);
#pragma clang diagnostic pop

                appendChildren(root, VendorNdkConverter{}, object.framework.mVendorNdks, param);
            }
            if (param.flags.isSsdkEnabled()) {
                if (!object.framework.mSystemSdk.empty()) {
                    appendChild(root, SystemSdkConverter{}(object.framework.mSystemSdk, param));
                }
            }
        }

        if (param.flags.isXmlFilesEnabled()) {
            appendChildren(root, ManifestXmlFileConverter{}, object.getXmlFiles(), param);
        }
    }
    bool buildObject(HalManifest* object, NodeType* root,
                     const BuildObjectParam& constParam) const override {
        BuildObjectParam param = constParam;
        if (!parseAttr(root, "version", &param.metaVersion, param.error)) return false;
        if (param.metaVersion > kMetaVersion) {
            *param.error = "Unrecognized manifest.version " + to_string(param.metaVersion) +
                           " (libvintf@" + to_string(kMetaVersion) + ")";
            return false;
        }
        object->mSourceMetaVersion = param.metaVersion;

        if (!parseAttr(root, "type", &object->mType, param.error)) {
            return false;
        }

        std::vector<ManifestHal> hals;
        if (!parseChildren(root, ManifestHalConverter{}, &hals, param)) {
            return false;
        }
        for (auto&& hal : hals) {
            hal.setFileName(object->fileName());
        }

        if (object->mType == SchemaType::DEVICE) {
            // tags for device hal manifest only.
            // <sepolicy> can be missing because it can be determined at build time, not hard-coded
            // in the XML file.
            if (!parseOptionalChild(root, HalManifestSepolicyConverter{}, {},
                                    &object->device.mSepolicyVersion, param)) {
                return false;
            }

            if (!parseOptionalAttr(root, "target-level", Level::UNSPECIFIED, &object->mLevel,
                                   param.error)) {
                return false;
            }

            if (!parseOptionalChild(root, KernelInfoConverter{}, &object->device.mKernel, param)) {
                return false;
            }
        } else if (object->mType == SchemaType::FRAMEWORK) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
            if (!parseChildren(root, VndkConverter{}, &object->framework.mVndks, param)) {
                return false;
            }
            for (const auto& vndk : object->framework.mVndks) {
                if (!vndk.mVersionRange.isSingleVersion()) {
                    *param.error = "vndk.version " + to_string(vndk.mVersionRange) +
                                   " cannot be a range for manifests";
                    return false;
                }
            }
#pragma clang diagnostic pop

            if (!parseChildren(root, VendorNdkConverter{}, &object->framework.mVendorNdks, param)) {
                return false;
            }

            std::set<std::string> vendorNdkVersions;
            for (const auto& vendorNdk : object->framework.mVendorNdks) {
                if (vendorNdkVersions.find(vendorNdk.version()) != vendorNdkVersions.end()) {
                    *param.error = "Duplicated manifest.vendor-ndk.version " + vendorNdk.version();
                    return false;
                }
                vendorNdkVersions.insert(vendorNdk.version());
            }

            if (!parseOptionalChild(root, SystemSdkConverter{}, {}, &object->framework.mSystemSdk,
                                    param)) {
                return false;
            }
        }
        for (auto &&hal : hals) {
            std::string description{hal.name};
            if (!object->add(std::move(hal), param.error)) {
                param.error->insert(0, "Duplicated manifest.hal entry " + description + ": ");
                return false;
            }
        }

        std::vector<ManifestXmlFile> xmlFiles;
        if (!parseChildren(root, ManifestXmlFileConverter{}, &xmlFiles, param)) {
            return false;
        }
        for (auto&& xmlFile : xmlFiles) {
            std::string description{xmlFile.name()};
            if (!object->addXmlFile(std::move(xmlFile))) {
                *param.error = "Duplicated manifest.xmlfile entry " + description +
                               "; entries cannot have duplicated name and version";
                return false;
            }
        }

        return true;
    }
};

struct AvbVersionConverter : public XmlTextConverter<Version> {
    std::string elementName() const override { return "vbmeta-version"; }
};

struct AvbConverter : public XmlNodeConverter<Version> {
    std::string elementName() const override { return "avb"; }
    void mutateNode(const Version& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendChild(root, AvbVersionConverter{}(object, param));
    }
    bool buildObject(Version* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        return parseChild(root, AvbVersionConverter{}, object, param);
    }
};

struct MatrixXmlFileConverter : public XmlNodeConverter<MatrixXmlFile> {
    std::string elementName() const override { return "xmlfile"; }
    void mutateNode(const MatrixXmlFile& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        appendTextElement(root, "name", object.name(), param.d);
        appendAttr(root, "format", object.format());
        appendAttr(root, "optional", object.optional());
        appendChild(root, VersionRangeConverter{}(object.versionRange(), param));
        if (!object.overriddenPath().empty()) {
            appendTextElement(root, "path", object.overriddenPath(), param.d);
        }
    }
    bool buildObject(MatrixXmlFile* object, NodeType* root,
                     const BuildObjectParam& param) const override {
        if (!parseTextElement(root, "name", &object->mName, param.error) ||
            !parseAttr(root, "format", &object->mFormat, param.error) ||
            !parseOptionalAttr(root, "optional", false, &object->mOptional, param.error) ||
            !parseChild(root, VersionRangeConverter{}, &object->mVersionRange, param) ||
            !parseOptionalTextElement(root, "path", {}, &object->mOverriddenPath, param.error)) {
            return false;
        }
        return true;
    }
};

struct CompatibilityMatrixConverter : public XmlNodeConverter<CompatibilityMatrix> {
    std::string elementName() const override { return "compatibility-matrix"; }
    void mutateNode(const CompatibilityMatrix& object, NodeType* root,
                    const MutateNodeParam& param) const override {
        if (param.flags.isMetaVersionEnabled()) {
            appendAttr(root, "version", kMetaVersion);
        }
        if (param.flags.isSchemaTypeEnabled()) {
            appendAttr(root, "type", object.mType);
        }

        if (param.flags.isHalsEnabled()) {
            appendChildren(root, MatrixHalConverter{}, iterateValues(object.mHals), param);
        }
        if (object.mType == SchemaType::FRAMEWORK) {
            if (param.flags.isKernelEnabled()) {
                appendChildren(root, MatrixKernelConverter{}, object.framework.mKernels, param);
            }
            if (param.flags.isSepolicyEnabled()) {
                if (!(object.framework.mSepolicy == Sepolicy{})) {
                    appendChild(root, SepolicyConverter{}(object.framework.mSepolicy, param));
                }
            }
            if (param.flags.isAvbEnabled()) {
                if (!(object.framework.mAvbMetaVersion == Version{})) {
                    appendChild(root, AvbConverter{}(object.framework.mAvbMetaVersion, param));
                }
            }
            if (object.mLevel != Level::UNSPECIFIED) {
                this->appendAttr(root, "level", object.mLevel);
            }
        } else if (object.mType == SchemaType::DEVICE) {
            if (param.flags.isVndkEnabled()) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
                if (!(object.device.mVndk == Vndk{})) {
                    appendChild(root, VndkConverter{}(object.device.mVndk, param));
                }
#pragma clang diagnostic pop

                if (!(object.device.mVendorNdk == VendorNdk{})) {
                    appendChild(root, VendorNdkConverter{}(object.device.mVendorNdk, param));
                }
            }

            if (param.flags.isSsdkEnabled()) {
                if (!object.device.mSystemSdk.empty()) {
                    appendChild(root, SystemSdkConverter{}(object.device.mSystemSdk, param));
                }
            }
        }

        if (param.flags.isXmlFilesEnabled()) {
            appendChildren(root, MatrixXmlFileConverter{}, object.getXmlFiles(), param);
        }
    }
    bool buildObject(CompatibilityMatrix* object, NodeType* root,
                     const BuildObjectParam& constParam) const override {
        BuildObjectParam param = constParam;
        if (!parseAttr(root, "version", &param.metaVersion, param.error)) return false;
        if (param.metaVersion > kMetaVersion) {
            *param.error = "Unrecognized compatibility-matrix.version " +
                           to_string(param.metaVersion) + " (libvintf@" + to_string(kMetaVersion) +
                           ")";
            return false;
        }

        std::vector<MatrixHal> hals;
        if (!parseAttr(root, "type", &object->mType, param.error) ||
            !parseChildren(root, MatrixHalConverter{}, &hals, param)) {
            return false;
        }

        if (object->mType == SchemaType::FRAMEWORK) {
            // <avb> and <sepolicy> can be missing because it can be determined at build time, not
            // hard-coded in the XML file.
            if (!parseChildren(root, MatrixKernelConverter{}, &object->framework.mKernels, param) ||
                !parseOptionalChild(root, SepolicyConverter{}, {}, &object->framework.mSepolicy,
                                    param) ||
                !parseOptionalChild(root, AvbConverter{}, {}, &object->framework.mAvbMetaVersion,
                                    param)) {
                return false;
            }

            std::set<Version> seenKernelVersions;
            for (const auto& kernel : object->framework.mKernels) {
                Version minLts(kernel.minLts().version, kernel.minLts().majorRev);
                if (seenKernelVersions.find(minLts) != seenKernelVersions.end()) {
                    continue;
                }
                if (!kernel.conditions().empty()) {
                    *param.error = "First <kernel> for version " + to_string(minLts) +
                                   " must have empty <conditions> for backwards compatibility.";
                    return false;
                }
                seenKernelVersions.insert(minLts);
            }

            if (!parseOptionalAttr(root, "level", Level::UNSPECIFIED, &object->mLevel,
                                   param.error)) {
                return false;
            }

        } else if (object->mType == SchemaType::DEVICE) {
            // <vndk> can be missing because it can be determined at build time, not hard-coded
            // in the XML file.
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
            if (!parseOptionalChild(root, VndkConverter{}, {}, &object->device.mVndk, param)) {
                return false;
            }
#pragma clang diagnostic pop

            if (!parseOptionalChild(root, VendorNdkConverter{}, {}, &object->device.mVendorNdk,
                                    param)) {
                return false;
            }

            if (!parseOptionalChild(root, SystemSdkConverter{}, {}, &object->device.mSystemSdk,
                                    param)) {
                return false;
            }
        }

        for (auto &&hal : hals) {
            if (!object->add(std::move(hal))) {
                *param.error = "Duplicated compatibility-matrix.hal entry";
                return false;
            }
        }

        std::vector<MatrixXmlFile> xmlFiles;
        if (!parseChildren(root, MatrixXmlFileConverter{}, &xmlFiles, param)) {
            return false;
        }
        for (auto&& xmlFile : xmlFiles) {
            if (!xmlFile.optional()) {
                *param.error = "compatibility-matrix.xmlfile entry " + xmlFile.name() +
                               " has to be optional for compatibility matrix version 1.0";
                return false;
            }
            std::string description{xmlFile.name()};
            if (!object->addXmlFile(std::move(xmlFile))) {
                *param.error = "Duplicated compatibility-matrix.xmlfile entry " + description;
                return false;
            }
        }

        return true;
    }
};

#define CREATE_CONVERT_FN(type)                                         \
    std::string toXml(const type& o, SerializeFlags::Type flags) {      \
        return type##Converter{}.toXml(o, flags);                       \
    }                                                                   \
    bool fromXml(type* o, const std::string& xml, std::string* error) { \
        return type##Converter{}.fromXml(o, xml, error);                \
    }

// Create convert functions for public usage.
CREATE_CONVERT_FN(HalManifest)
CREATE_CONVERT_FN(CompatibilityMatrix)

// Create convert functions for internal usage.
CREATE_CONVERT_FN(KernelInfo)

// Create convert functions for testing.
CREATE_CONVERT_FN(Version)
CREATE_CONVERT_FN(SepolicyVersion)
CREATE_CONVERT_FN(KernelConfigTypedValue)
CREATE_CONVERT_FN(MatrixHal)
CREATE_CONVERT_FN(ManifestHal)

#undef CREATE_CONVERT_FN

} // namespace vintf
} // namespace android
