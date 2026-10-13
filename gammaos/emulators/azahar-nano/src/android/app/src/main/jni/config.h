// Copyright 2014-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <map>
#include <memory>
#include <string>
#include "common/settings.h"

class INIReader;

/**
 * GammaOS: config.ini with the per-game overrides the frontend hands over
 * (azn_set_config_overrides, "Section/key" -> value) laid on top: a key the overrides hold is
 * answered from them, every other key from config.ini. config.ini itself is never changed by
 * an override, so a crash can never leave the global file holding one game's values.
 */
class ConfigReader {
public:
    ConfigReader(const char* buffer, std::size_t size);
    ~ConfigReader();

    int ParseError() const;
    std::string Get(const std::string& section, const std::string& name,
                    const std::string& default_value) const;
    std::string GetString(const std::string& section, const std::string& name,
                          const std::string& default_value) const;
    long GetInteger(const std::string& section, const std::string& name, long default_value) const;
    double GetReal(const std::string& section, const std::string& name,
                   double default_value) const;
    bool GetBoolean(const std::string& section, const std::string& name,
                    bool default_value) const;

    /// Replaces the overrides; an empty value is the same as no override for that key.
    static void SetOverrides(std::map<std::string, std::string> values);

private:
    const std::string* Override(const std::string& section, const std::string& name) const;
    std::unique_ptr<INIReader> ini;
};

class Config {
private:
    std::unique_ptr<ConfigReader> android_config;
    std::string android_config_loc;

    bool LoadINI(const std::string& default_contents = "", bool retry = true);
    void ReadValues();

public:
    Config();
    ~Config();

    void Reload();

private:
    /**
     * Applies a value read from the android_config to a Setting.
     *
     * @param group The name of the INI group
     * @param setting The yuzu setting to modify
     */
    template <typename Type, bool ranged>
    void ReadSetting(const std::string& group, Settings::Setting<Type, ranged>& setting);
};
