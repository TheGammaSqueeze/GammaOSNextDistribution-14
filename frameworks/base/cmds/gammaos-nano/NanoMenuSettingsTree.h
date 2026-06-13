/*
 * Copyright (C) 2026 GammaOS
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

#ifndef GAMMAOS_NANO_SETTINGS_TREE_H
#define GAMMAOS_NANO_SETTINGS_TREE_H

#include <string>
#include <vector>

namespace android {

enum class SettingNodeType {
    kCategory,
    kToggle,
    kText,
    kList,
    kAction,
    kScreen,
    kInfo,
};

enum class SettingSource {
    kNone,
    kProp,
    kGlobal,
    kSecure,
    kSystem,
};

struct SettingNode {
    std::string id;
    std::string label;
    SettingNodeType type;
    SettingSource source;
    std::string key;
    std::string defaultVal;
    std::string options;
    int selfIdx;
    int parentIdx;
    int screenId;
};

struct SettingListOption {
    std::string value;
    std::string label;
};

std::vector<SettingListOption> parseListOptions(const std::string& opts);

// Direct setting read/write primitives (kProp -> property_get/set; kGlobal/
// kSecure/kSystem -> settings get/put). Shared by the legacy tree and the PS3
// settings-binding path.
std::string readSettingValue(SettingSource src, const std::string& key,
                             const std::string& def);
void writeSettingValue(SettingSource src, const std::string& key,
                       const std::string& val);

// A declarative binding from a PS3-XMB settings leaf (matched by label) to a real
// backing setting. options uses the parseListOptions "value:Label,..." format; a
// toggle is just a two-entry list. Defined in NanoMenuPS3Menu.cpp.
struct Ps3SettingBinding {
    const char* label;       // matches Ps3DataItem.name / Ps3Item.label
    SettingSource source;    // kProp | kGlobal | kSecure | kSystem
    const char* key;
    const char* def;
    const char* options;     // "value:Label,value:Label,..." (toggle = two entries)
};
const Ps3SettingBinding* ps3BindingFor(const std::string& label);

class SettingsTreeBuilder {
public:
    explicit SettingsTreeBuilder(std::vector<SettingNode>& nodes);

    void beginCategory(const char* id, const char* label);
    void endCategory();

    void toggle(const char* id, const char* label,
                SettingSource src, const char* key,
                const char* def = "false");

    void text(const char* id, const char* label,
              SettingSource src, const char* key,
              const char* def = "");

    void list(const char* id, const char* label,
              SettingSource src, const char* key,
              const char* def, const char* options);

    void action(const char* id, const char* label);

    void screen(const char* id, const char* label, int screenId);

    void info(const char* id, const char* label,
              SettingSource src, const char* key,
              const char* def = "");

private:
    std::vector<SettingNode>& mNodes;
    std::vector<int> mParentStack;
    int parent() const;
};

} // namespace android

#endif // GAMMAOS_NANO_SETTINGS_TREE_H
