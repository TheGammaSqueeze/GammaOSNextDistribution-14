// value-parameterized test. Support INSTANTIATE_TEST_CASE_P even it had been stopped
// supported and replaced by INSTANTIATE_TEST_SUITE_P.
TEST_P( ValueParamClass1, VPMethod1) {
  Run("List<{}>", kListSupportExpectations);
}
INSTANTIATE_TEST_SUITE_P( Instantiation1, /* Instance Name */
                          ValueParamClass1, // Test Case Name
    testing::Combine(testing::Values(Options::Language::CPP, Options::Language::JAVA,
                                     Options::Language::NDK, Options::Language::RUST),
                     testing::ValuesIn(kTypeParams)),
    [](const testing::TestParamInfo<std::tuple<Options::Language, TypeParam>>& info) {
      return Options::LanguageToString(std::get<0>(info.param)) + "_" +
             std::get<1>(info.param).kind;
    });

TEST_P( ValueParamClass2, VPMethod2) {
  Run("List<{}>", kListSupportExpectations);
}
INSTANTIATE_TEST_CASE_P(Instantiation2,
    ValueParamClass2,
    testing::Combine(testing::Values(Options::Language::CPP, Options::Language::JAVA,
                                     Options::Language::NDK, Options::Language::RUST),
                     testing::ValuesIn(kTypeParams)),
    [](const testing::TestParamInfo<std::tuple<Options::Language, TypeParam>>& info) {
      return Options::LanguageToString(std::get<0>(info.param)) + "_" +
             std::get<1>(info.param).kind;
    });


// Regular gtest with fixtures
TEST(Class1, Method1) {
  Run("List<{}>", kListSupportExpectations);
}
TEST(Class1, Method2) {
  Run("List<{}>", kListSupportExpectations);
}
TEST_F(
FClass,
FMethod1) {
  Run("List<{}>", kListSupportExpectations);
}

TEST_F(FClass, FMethod2) {
  Run("List<{}>", kListSupportExpectations);
}

// GTest with flags
TEST_WITH_FLAGS(
  FlagClass1,
  Method1,
  REQUIRES_FLAGS_DISABLED(ACONFIG_FLAG(ns::test, flag))
) {
  Run("List<{}>", kListSupportExpectations);
}
TEST_WITH_FLAGS(FlagClass1, Method2,
  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(ns::test, flag)),
  REQUIRES_FLAGS_DISABLED(LEGACY_FLAG(ns.test, ns::test, flag))
) {
  Run("List<{}>", kListSupportExpectations);
}
TEST_F_WITH_FLAGS(
  FFlagClass,
  FMethod1,
  REQUIRES_FLAGS_DISABLED(ACONFIG_FLAG(ns::test, flag))
) {
  Run("List<{}>", kListSupportExpectations);
}

TEST_F_WITH_FLAGS(FFlagClass, FMethod2,
  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(ns::test, flag)),
  REQUIRES_FLAGS_DISABLED(LEGACY_FLAG(ns.test, ns::test, flag))
) {
  Run("List<{}>", kListSupportExpectations);
}

// Typed test.
TYPED_TEST_SUITE(TypedTestClass, Implementations);
TYPED_TEST(TypedTestClass, TypedTestName) {
  EXPECT_FALSE(this->table_->IsPrime(-5));
  EXPECT_FALSE(this->table_->IsPrime(100));
}


// Typed-parameterized tests. Support INSTANTIATE_TYPED_TEST_CASE_P even it had been
// stopped supported and replaced by INSTANTIATE_TYPED_TEST_SUITE_P.
TYPED_TEST_SUITE_P(TypedParamTestClass)
TYPED_TEST_P(TypedParamTestClass, TypedParamTestName) {
  EXPECT_EQ(2, this->table_->GetNextPrime(0));
  EXPECT_EQ(131, this->table_->GetNextPrime(128));
}
INSTANTIATE_TYPED_TEST_CASE_P(/* Prefix */ Instantiation3,
                              TypedParamTestClass, // SuiteName
                              IntTypes);
INSTANTIATE_TYPED_TEST_SUITE_P(Instantiation4, TypedParamTestClass, IntTypes);
