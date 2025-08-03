/*
 * Copyright (C) 2014 The Android Open Source Project
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

#pragma once

#include "Network.h"
#include "Permission.h"

namespace android::net {

class PhysicalNetwork : public Network {
  public:
    class Delegate {
      public:
        virtual ~Delegate();

        [[nodiscard]] virtual int addFallthrough(const std::string& physicalInterface,
                                                 Permission permission) = 0;
        [[nodiscard]] virtual int removeFallthrough(const std::string& physicalInterface,
                                                    Permission permission) = 0;
    };

    PhysicalNetwork(unsigned netId, Delegate* delegate, bool local);
    virtual ~PhysicalNetwork();

    // These refer to permissions that apps must have in order to use this network.
    Permission getPermission() const;
    [[nodiscard]] int setPermission(Permission permission);

    [[nodiscard]] int addAsDefault();
    [[nodiscard]] int removeAsDefault();
    [[nodiscard]] int addUsers(const UidRanges& uidRanges, int32_t subPriority) override;
    [[nodiscard]] int removeUsers(const UidRanges& uidRanges, int32_t subPriority) override;
    bool isPhysical() override { return true; }
    bool canAddUsers() override { return true; }

  private:
    std::string getTypeString() const override { return "PHYSICAL"; };
    [[nodiscard]] int addInterface(const std::string& interface) override;
    [[nodiscard]] int removeInterface(const std::string& interface) override;
    int destroySocketsLackingPermission(Permission permission);
    void invalidateRouteCache(const std::string& interface);
    bool isValidSubPriority(int32_t priority) override;

    Delegate* const mDelegate;
    Permission mPermission;
    bool mIsDefault;
    const bool mIsLocalNetwork;
};

}  // namespace android::net
