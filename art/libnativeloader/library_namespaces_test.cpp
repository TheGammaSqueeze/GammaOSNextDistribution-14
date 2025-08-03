/*
 * Copyright (C) 2024 The Android Open Source Project
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

#if defined(ART_TARGET_ANDROID)

#include "library_namespaces.h"

#include "android-base/result-gmock.h"
#include "gtest/gtest.h"
#include "public_libraries.h"

namespace android {
namespace nativeloader {
namespace {

using ::android::base::testing::HasError;
using ::android::base::testing::HasValue;
using ::android::base::testing::WithMessage;
using ::testing::StartsWith;

TEST(LibraryNamespacesTest, TestGetApiDomainFromPath) {
  // GetApiDomainFromPath returns API_DOMAIN_PRODUCT only if the device is
  // trebleized and has an unbundled product partition.
  ApiDomain api_domain_product = is_product_treblelized() ? API_DOMAIN_PRODUCT : API_DOMAIN_DEFAULT;

  EXPECT_EQ(GetApiDomainFromPath("/data/somewhere"), API_DOMAIN_DEFAULT);
  EXPECT_EQ(GetApiDomainFromPath("/system/somewhere"), API_DOMAIN_DEFAULT);
  EXPECT_EQ(GetApiDomainFromPath("/product/somewhere"), api_domain_product);
  EXPECT_EQ(GetApiDomainFromPath("/vendor/somewhere"), API_DOMAIN_VENDOR);
  EXPECT_EQ(GetApiDomainFromPath("/system/product/somewhere"), api_domain_product);
  EXPECT_EQ(GetApiDomainFromPath("/system/vendor/somewhere"), API_DOMAIN_VENDOR);

  EXPECT_EQ(GetApiDomainFromPath(""), API_DOMAIN_DEFAULT);
  EXPECT_EQ(GetApiDomainFromPath("/"), API_DOMAIN_DEFAULT);
  EXPECT_EQ(GetApiDomainFromPath("product/somewhere"), API_DOMAIN_DEFAULT);
  EXPECT_EQ(GetApiDomainFromPath("/product"), API_DOMAIN_DEFAULT);
  EXPECT_EQ(GetApiDomainFromPath("/product/"), api_domain_product);
  EXPECT_EQ(GetApiDomainFromPath(":/product/"), API_DOMAIN_DEFAULT);

  EXPECT_EQ(GetApiDomainFromPath("/data/somewhere:/product/somewhere"), API_DOMAIN_DEFAULT);
  EXPECT_EQ(GetApiDomainFromPath("/vendor/somewhere:/product/somewhere"), API_DOMAIN_VENDOR);
  EXPECT_EQ(GetApiDomainFromPath("/product/somewhere:/vendor/somewhere"), api_domain_product);
}

TEST(LibraryNamespacesTest, TestGetApiDomainFromPathList) {
  // GetApiDomainFromPath returns API_DOMAIN_PRODUCT only if the device is
  // trebleized and has an unbundled product partition.
  ApiDomain api_domain_product = is_product_treblelized() ? API_DOMAIN_PRODUCT : API_DOMAIN_DEFAULT;

  EXPECT_THAT(GetApiDomainFromPathList("/data/somewhere"), HasValue(API_DOMAIN_DEFAULT));
  EXPECT_THAT(GetApiDomainFromPathList("/system/somewhere"), HasValue(API_DOMAIN_DEFAULT));
  EXPECT_THAT(GetApiDomainFromPathList("/product/somewhere"), HasValue(api_domain_product));
  EXPECT_THAT(GetApiDomainFromPathList("/vendor/somewhere"), HasValue(API_DOMAIN_VENDOR));
  EXPECT_THAT(GetApiDomainFromPathList("/system/product/somewhere"), HasValue(api_domain_product));
  EXPECT_THAT(GetApiDomainFromPathList("/system/vendor/somewhere"), HasValue(API_DOMAIN_VENDOR));

  EXPECT_THAT(GetApiDomainFromPathList(""), HasValue(API_DOMAIN_DEFAULT));
  EXPECT_THAT(GetApiDomainFromPathList(":"), HasValue(API_DOMAIN_DEFAULT));
  EXPECT_THAT(GetApiDomainFromPathList(":/vendor/somewhere"), HasValue(API_DOMAIN_VENDOR));
  EXPECT_THAT(GetApiDomainFromPathList("/vendor/somewhere:"), HasValue(API_DOMAIN_VENDOR));

  EXPECT_THAT(GetApiDomainFromPathList("/data/somewhere:/product/somewhere"),
              HasValue(api_domain_product));
  if (api_domain_product == API_DOMAIN_PRODUCT) {
    EXPECT_THAT(GetApiDomainFromPathList("/vendor/somewhere:/product/somewhere"),
                HasError(WithMessage(StartsWith("Path list crosses partition boundaries"))));
    EXPECT_THAT(GetApiDomainFromPathList("/product/somewhere:/vendor/somewhere"),
                HasError(WithMessage(StartsWith("Path list crosses partition boundaries"))));
  }
}

}  // namespace
}  // namespace nativeloader
}  // namespace android

#endif  // ART_TARGET_ANDROID
