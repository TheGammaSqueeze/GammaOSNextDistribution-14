// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package aidl

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/google/blueprint"
	"github.com/google/blueprint/proptools"

	"android/soong/aidl_library"
	"android/soong/android"
	"android/soong/cc"
	"android/soong/genrule"
	"android/soong/java"
	"android/soong/rust"
)

func TestMain(m *testing.M) {
	os.Exit(m.Run())
}

func withFiles(files map[string][]byte) android.FixturePreparer {
	return android.FixtureMergeMockFs(files)
}

func intPtr(v int) *int {
	return &v
}

func setReleaseEnv() android.FixturePreparer {
	return android.FixtureModifyProductVariables(func(variables android.FixtureProductVariables) {
		variables.Release_aidl_use_unfrozen = proptools.BoolPtr(false)
	})
}

func setTestFreezeEnv() android.FixturePreparer {
	return android.FixtureMergeEnv(map[string]string{
		"AIDL_FROZEN_REL":    "true",
		"AIDL_FROZEN_OWNERS": "aosp test",
	})
}

func setUseUnfrozenOverrideEnvTrue() android.FixturePreparer {
	return android.FixtureMergeEnv(map[string]string{
		"AIDL_USE_UNFROZEN_OVERRIDE": "true",
	})
}

func setUseUnfrozenOverrideEnvFalse() android.FixturePreparer {
	return android.FixtureMergeEnv(map[string]string{
		"AIDL_USE_UNFROZEN_OVERRIDE": "false",
	})
}

func setTransitiveFreezeEnv() android.FixturePreparer {
	return android.FixtureMergeEnv(map[string]string{
		"AIDL_TRANSITIVE_FREEZE": "true",
	})
}

func _testAidl(t *testing.T, bp string, customizers ...android.FixturePreparer) android.FixturePreparer {
	t.Helper()

	preparers := []android.FixturePreparer{}

	preparers = append(preparers,
		cc.PrepareForTestWithCcDefaultModules,
		java.PrepareForTestWithJavaDefaultModules,
		genrule.PrepareForTestWithGenRuleBuildComponents,
		android.PrepareForTestWithNamespace,
	)

	bp = bp + `
		package {
			default_visibility: ["//visibility:public"],
		}
		java_defaults {
			name: "aidl-java-module-defaults",
		}
		cc_defaults {
			name: "aidl-cpp-module-defaults",
		}
		rust_defaults {
			name: "aidl-rust-module-defaults",
		}
		cc_library {
			name: "libbinder",
			recovery_available: true,
		}
		cc_library_static {
			name: "aidl-analyzer-main",
			host_supported: true,
			vendor_available: true,
			recovery_available: true,
		}
		cc_library {
			name: "libutils",
			recovery_available: true,
		}
		cc_library {
			name: "libcutils",
			recovery_available: true,
		}
		cc_library {
			name: "libbinder_ndk",
			recovery_available: true,
			stubs: {
				versions: ["29"],
			}
		}
		ndk_library {
			name: "libbinder_ndk",
			symbol_file: "libbinder_ndk.map.txt",
			first_version: "29",
		}
		cc_library {
			name: "liblog",
			no_libcrt: true,
			nocrt: true,
			system_shared_libs: [],
		}
		rust_library {
			name: "libstd",
			crate_name: "std",
			srcs: [""],
			no_stdlibs: true,
			sysroot: true,
		}
		rust_library {
			name: "libtest",
			crate_name: "test",
			srcs: [""],
			no_stdlibs: true,
			sysroot: true,
		}
		rust_library {
			name: "libbinder_rs",
			crate_name: "binder",
			srcs: [""],
		}
		rust_proc_macro {
			name: "libasync_trait",
			crate_name: "async_trait",
			srcs: [""],
			no_stdlibs: true,
		}
	`
	preparers = append(preparers, android.FixtureWithRootAndroidBp(bp))
	preparers = append(preparers, android.FixtureAddTextFile("system/tools/aidl/build/Android.bp", `
		aidl_interfaces_metadata {
			name: "aidl_metadata_json",
			visibility: ["//system/tools/aidl:__subpackages__"],
		}
	`))

	preparers = append(preparers, android.FixtureModifyProductVariables(func(variables android.FixtureProductVariables) {
		variables.Release_aidl_use_unfrozen = proptools.BoolPtr(true)
	}))

	preparers = append(preparers, customizers...)

	preparers = append(preparers,
		rust.PrepareForTestWithRustBuildComponents,
		android.FixtureRegisterWithContext(func(ctx android.RegistrationContext) {
			ctx.RegisterModuleType("aidl_interface", AidlInterfaceFactory)
			ctx.RegisterModuleType("aidl_interface_defaults", AidlInterfaceDefaultsFactory)
			ctx.RegisterModuleType("aidl_interfaces_metadata", aidlInterfacesMetadataSingletonFactory)
			ctx.RegisterModuleType("rust_defaults", func() android.Module {
				return rust.DefaultsFactory()
			})
			ctx.RegisterModuleType("aidl_library", aidl_library.AidlLibraryFactory)

			ctx.PreArchMutators(registerPreArchMutators)
			ctx.PostDepsMutators(registerPostDepsMutators)
		}),
	)

	return android.GroupFixturePreparers(preparers...)
}

func testAidl(t *testing.T, bp string, customizers ...android.FixturePreparer) (*android.TestContext, android.Config) {
	t.Helper()
	preparer := _testAidl(t, bp, customizers...)
	result := preparer.RunTest(t)
	return result.TestContext, result.Config
}

func testAidlError(t *testing.T, pattern, bp string, customizers ...android.FixturePreparer) {
	t.Helper()
	preparer := _testAidl(t, bp, customizers...)
	preparer.
		ExtendWithErrorHandler(android.FixtureExpectsAtLeastOneErrorMatchingPattern(pattern)).
		RunTest(t)
}

// asserts that there are expected module regardless of variants
func assertModulesExists(t *testing.T, ctx *android.TestContext, names ...string) {
	t.Helper()
	missing := []string{}
	for _, name := range names {
		variants := ctx.ModuleVariantsForTests(name)
		if len(variants) == 0 {
			missing = append(missing, name)
		}
	}
	if len(missing) > 0 {
		// find all the modules that do exist
		allModuleNames := make(map[string]bool)
		ctx.VisitAllModules(func(m blueprint.Module) {
			allModuleNames[ctx.ModuleName(m)] = true
		})
		t.Errorf("expected modules(%v) not found. all modules: %v", missing, android.SortedKeys(allModuleNames))
	}
}

func assertContains(t *testing.T, actual, expected string) {
	t.Helper()
	if !strings.Contains(actual, expected) {
		t.Errorf("%q is not found in %q.", expected, actual)
	}
}

func assertListContains(t *testing.T, actual []string, expected string) {
	t.Helper()
	for _, a := range actual {
		if strings.Contains(a, expected) {
			return
		}
	}
	t.Errorf("%q is not found in %v.", expected, actual)
}

// Vintf module must have versions in release version
func TestVintfWithoutVersionInRelease(t *testing.T) {
	vintfWithoutVersionBp := `
	aidl_interface {
		name: "foo",
		stability: "vintf",
		srcs: [
			"IFoo.aidl",
		],
		owner: "test",
		backend: {
			rust: {
				enabled: true,
			},
		},
	}`
	expectedError := `module "foo_interface": versions: must be set \(need to be frozen\) because`
	testAidlError(t, expectedError, vintfWithoutVersionBp, setTestFreezeEnv())

	ctx, _ := testAidl(t, vintfWithoutVersionBp, setReleaseEnv())
	assertModulesExists(t, ctx, "foo-V1-java", "foo-V1-rust", "foo-V1-cpp", "foo-V1-ndk")
	ctx, _ = testAidl(t, vintfWithoutVersionBp)
	assertModulesExists(t, ctx, "foo-V1-java", "foo-V1-rust", "foo-V1-cpp", "foo-V1-ndk")
}

// Check if using unstable version in release cause an error.
func TestUnstableVersionUsageInRelease(t *testing.T) {
	unstableVersionUsageInJavaBp := `
	aidl_interface {
		name: "foo",
		versions: [
			"1",
		],
		srcs: [
			"IFoo.aidl",
		],
	}
	java_library {
		name: "bar",
		libs: ["foo-V2-java"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	})

	expectedError := `foo-V2-java is an unfrozen development version, and it can't be used because`
	testAidlError(t, expectedError, unstableVersionUsageInJavaBp, setTestFreezeEnv(), files)
	testAidl(t, unstableVersionUsageInJavaBp, setReleaseEnv(), files)
	testAidl(t, unstableVersionUsageInJavaBp, files)

	// A stable version can be used in release version
	stableVersionUsageInJavaBp := `
	aidl_interface {
		name: "foo",
		versions: [
			"1",
		],
		srcs: [
			"IFoo.aidl",
		],
	}
	java_library {
		name: "bar",
		libs: ["foo-V1-java"],
	}`

	testAidl(t, stableVersionUsageInJavaBp, setReleaseEnv(), files)
	testAidl(t, stableVersionUsageInJavaBp, setTestFreezeEnv(), files)
	testAidl(t, stableVersionUsageInJavaBp, files)
}

func TestUsingUnstableVersionIndirectlyInRelease(t *testing.T) {
	unstableVersionUsageInJavaBp := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		versions: ["1"],
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx-V2"],    // not OK
		versions: ["1"],
		srcs: ["IFoo.aidl"],
	}
	java_library {
		name: "bar",
		libs: ["foo-V1-java"],  // OK
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/xxx/1/foo.1.aidl": nil,
		"aidl_api/xxx/1/.hash":      nil,
	})

	expectedError := `xxx-V2-java is an unfrozen development version`
	testAidlError(t, expectedError, unstableVersionUsageInJavaBp, setTestFreezeEnv(), files)
	testAidl(t, unstableVersionUsageInJavaBp, setReleaseEnv(), files)
	testAidl(t, unstableVersionUsageInJavaBp, files)
}

func TestFrozenTrueSimple(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "foo",
		versions: ["1"],
		frozen: true,
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	})

	testAidl(t, frozenTest, files, setReleaseEnv())
	testAidl(t, frozenTest, files, setTestFreezeEnv())
	testAidl(t, frozenTest, files)
}

func TestFrozenWithNoVersions(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "foo",
		frozen: true,
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	})

	expectedError := `cannot be frozen without versions`
	testAidlError(t, expectedError, frozenTest, files, setReleaseEnv())
	testAidlError(t, expectedError, frozenTest, files, setTestFreezeEnv())
	testAidlError(t, expectedError, frozenTest, files)
}

func TestFrozenImportingFrozen(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		frozen: true,
		versions: ["1"],
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx-V1"],
		versions: ["1"],
		frozen: true,
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/xxx/1/foo.1.aidl": nil,
		"aidl_api/xxx/1/.hash":      nil,
	})

	testAidl(t, frozenTest, files, setReleaseEnv())
	testAidl(t, frozenTest, files, setTestFreezeEnv())
	testAidl(t, frozenTest, files)
}

func TestFrozenImportingVersionUnfrozen(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		frozen: false,
		versions: ["1"],
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx-V1"],
		versions: ["1"],
		frozen: true,
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/xxx/1/foo.1.aidl": nil,
		"aidl_api/xxx/1/.hash":      nil,
	})

	testAidl(t, frozenTest, files, setReleaseEnv())
	testAidl(t, frozenTest, files, setTestFreezeEnv())
	testAidl(t, frozenTest, files)
}

func TestFrozenImportingUnfrozenWithFrozen(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		frozen: false,
		versions: ["1"],
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx"],
		versions: ["1"],
		frozen: true,
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/xxx/1/foo.1.aidl": nil,
		"aidl_api/xxx/1/.hash":      nil,
	})

	expectedError := `"foo" imports "xxx" which is not frozen. Either "foo" must`
	testAidlError(t, expectedError, frozenTest, files, setReleaseEnv())
	testAidlError(t, expectedError, frozenTest, files, setTestFreezeEnv())
	testAidlError(t, expectedError, frozenTest, files)
}

func TestFrozenImportingUnfrozen(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		owner: "test",
		frozen: false,
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx"],
		versions: ["1"],
		frozen: true,
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/xxx/1/foo.1.aidl": nil,
		"aidl_api/xxx/1/.hash":      nil,
	})

	expectedError := `versions: must be set \(need to be frozen\) because`
	testAidlError(t, expectedError, frozenTest, files, setTestFreezeEnv())

	expectedError = `"foo" imports "xxx" which is not frozen. Either "foo" must`
	testAidlError(t, expectedError, frozenTest, files, setReleaseEnv())
	testAidlError(t, expectedError, frozenTest, files)
}

// This is allowed to keep legacy behavior. It could be prevented directly after API-freeze
// if all frozen interfaces are explicitly marked `frozen: true,`.
func TestFrozenImportingUnSpecified(t *testing.T) {
	frozenTrueSimple := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		versions: ["1"],
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx-V1"],
		versions: ["1"],
		frozen: true,
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/xxx/1/foo.1.aidl": nil,
		"aidl_api/xxx/1/.hash":      nil,
	})

	testAidl(t, frozenTrueSimple, files, setReleaseEnv())
	testAidl(t, frozenTrueSimple, files, setTestFreezeEnv())
	testAidl(t, frozenTrueSimple, files)
}

// Keeping legacy behavior if "frozen" is not specified
func TestImportingNewLegacy(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		versions: ["1"],
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx-V2"],
		versions_with_info: [
			{version: "1", imports: ["xxx-V1"]},
		],
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/xxx/1/foo.1.aidl": nil,
		"aidl_api/xxx/1/.hash":      nil,
	})

	testAidl(t, frozenTest, files, setReleaseEnv())
	testAidl(t, frozenTest, files, setTestFreezeEnv())
	testAidl(t, frozenTest, files)
}

// We don't have a way to know if if "xxx" has changes to it and will
// need a new version without the "frozen" attribute. So we keep the
// legacy behavior and assume "foo" is still importing the old version.
func TestFrozenImportingNewLegacy(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		versions: ["1"],
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx-V1"],
		frozen: true,
		versions_with_info: [
			{version: "1", imports: ["xxx-V1"]},
		],
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/xxx/1/foo.1.aidl": nil,
		"aidl_api/xxx/1/.hash":      nil,
	})

	testAidl(t, frozenTest, files, setReleaseEnv())
	testAidl(t, frozenTest, files, setTestFreezeEnv())
	testAidl(t, frozenTest, files)
}

func TestFrozenImportingNewImplicit(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		frozen: false,
		versions: ["1"],
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx"],
		frozen: true,
		versions_with_info: [
			{version: "1", imports: ["xxx-V1"]},
		],
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/xxx/1/foo.1.aidl": nil,
		"aidl_api/xxx/1/.hash":      nil,
	})

	expectedError := `"foo" imports "xxx" which is not frozen. Either "foo" must`
	testAidlError(t, expectedError, frozenTest, files, setReleaseEnv())
	testAidlError(t, expectedError, frozenTest, files, setTestFreezeEnv())
	testAidlError(t, expectedError, frozenTest, files)
}

func TestImportingOwned(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		owner: "unknown-owner",
		frozen: false,
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx-V1"],
		frozen: false,
		versions_with_info: [
			{version: "1", imports: []},
		],
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	})

	expectedError := "Android.bp:10:10: module \"foo_interface\": imports: \"foo\" imports \"xxx\" which is an interface owned by \"unknown-owner\". This is not allowed because the owned interface will not be frozen at the same time."
	testAidlError(t, expectedError, frozenTest, files, setReleaseEnv())
	testAidlError(t, expectedError, frozenTest, files, setTestFreezeEnv())
	testAidlError(t, expectedError, frozenTest, files)
}

func TestImportingOwnedBothOwned(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		owner: "unknown-owner",
		frozen: false,
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx-V1"],
		frozen: false,
		versions_with_info: [
			{version: "1", imports: []},
		],
		srcs: ["IFoo.aidl"],
		owner: "unknown-owner-any",
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	})

	testAidl(t, frozenTest, files, setReleaseEnv())
	testAidl(t, frozenTest, files, setTestFreezeEnv())
	testAidl(t, frozenTest, files)
}

func TestFrozenImportingNewExplicit(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		frozen: false,
		versions: ["1"],
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx-V2"],
		frozen: true,
		versions_with_info: [
			{version: "1", imports: ["xxx-V1"]},
		],
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/xxx/1/foo.1.aidl": nil,
		"aidl_api/xxx/1/.hash":      nil,
	})

	expectedError := "This interface is 'frozen: true' but the imports have changed. Set 'frozen: false' to allow changes: \\n Version current imports: map\\[xxx:2\\]\\n Version 1 imports: map\\[xxx:1\\]\\n"
	testAidlError(t, expectedError, frozenTest, files, setReleaseEnv())
	testAidlError(t, expectedError, frozenTest, files, setTestFreezeEnv())
	testAidlError(t, expectedError, frozenTest, files)
}

func TestNonFrozenImportingNewImplicit(t *testing.T) {
	frozenTest := `
	aidl_interface {
		name: "xxx",
		srcs: ["IFoo.aidl"],
		frozen: false,
		versions: ["1"],
	}
	aidl_interface {
		name: "foo",
		imports: ["xxx-V1"],
		frozen: false,
		versions_with_info: [
			{version: "1", imports: ["xxx-V1"]},
		],
		srcs: ["IFoo.aidl"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/xxx/1/foo.1.aidl": nil,
		"aidl_api/xxx/1/.hash":      nil,
	})

	testAidl(t, frozenTest, files, setReleaseEnv())
	testAidl(t, frozenTest, files, setTestFreezeEnv())
	testAidl(t, frozenTest, files)
}

// The module which has never been frozen and is not "unstable" is not allowed in release version.
func TestNonVersionedModuleUsageInRelease(t *testing.T) {
	nonVersionedModuleUsageInJavaBp := `
	aidl_interface {
		name: "foo",
		srcs: [
			"IFoo.aidl",
		],
		owner: "test",
	}

	java_library {
		name: "bar",
		libs: ["foo-V1-java"],
	}`

	expectedError := `"foo_interface": versions: must be set \(need to be frozen\) because`
	testAidlError(t, expectedError, nonVersionedModuleUsageInJavaBp, setTestFreezeEnv())
	testAidl(t, nonVersionedModuleUsageInJavaBp, setReleaseEnv())
	testAidl(t, nonVersionedModuleUsageInJavaBp)

	nonVersionedUnstableModuleUsageInJavaBp := `
	aidl_interface {
		name: "foo",
		srcs: [
			"IFoo.aidl",
		],
		unstable: true,
	}

	java_library {
		name: "bar",
		libs: ["foo-java"],
	}`

	testAidl(t, nonVersionedUnstableModuleUsageInJavaBp, setReleaseEnv())
	testAidl(t, nonVersionedUnstableModuleUsageInJavaBp, setTestFreezeEnv())
	testAidl(t, nonVersionedUnstableModuleUsageInJavaBp)
}

func TestNonVersionedModuleOwnedByTestUsageInRelease(t *testing.T) {
	nonVersionedModuleUsageInJavaBp := `
	aidl_interface {
		name: "foo",
		owner: "test",
		srcs: [
			"IFoo.aidl",
		],
	}

	java_library {
		name: "bar",
		libs: ["foo-V1-java"],
	}`

	expectedError := `"foo_interface": versions: must be set \(need to be frozen\) because`
	testAidl(t, nonVersionedModuleUsageInJavaBp, setReleaseEnv())
	testAidlError(t, expectedError, nonVersionedModuleUsageInJavaBp, setTestFreezeEnv())
	testAidl(t, nonVersionedModuleUsageInJavaBp)
}

func TestNonVersionedModuleOwnedByOtherUsageInRelease(t *testing.T) {
	nonVersionedModuleUsageInJavaBp := `
	aidl_interface {
		name: "foo",
		owner: "unknown-owner",
		srcs: [
			"IFoo.aidl",
		],
	}

	java_library {
		name: "bar",
		libs: ["foo-V1-java"],
	}`

	testAidl(t, nonVersionedModuleUsageInJavaBp, setReleaseEnv())
	testAidl(t, nonVersionedModuleUsageInJavaBp, setTestFreezeEnv())
	testAidl(t, nonVersionedModuleUsageInJavaBp)
}

func TestImportInRelease(t *testing.T) {
	importInRelease := `
	aidl_interface {
		name: "foo",
		srcs: [
			"IFoo.aidl",
		],
		imports: ["bar-V1"],
		versions: ["1"],
	}

	aidl_interface {
		name: "bar",
		srcs: [
			"IBar.aidl",
		],
		versions: ["1"],
	}
	`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/bar/1/bar.1.aidl": nil,
		"aidl_api/bar/1/.hash":      nil,
	})

	testAidl(t, importInRelease, setReleaseEnv(), files)
	testAidl(t, importInRelease, setTestFreezeEnv(), files)
	testAidl(t, importInRelease, files)
}

func TestUnstableVersionedModuleUsageInRelease(t *testing.T) {
	nonVersionedModuleUsageInJavaBp := `
	aidl_interface {
		name: "foo",
		srcs: [
			"IFoo.aidl",
		],
		versions: ["1"],
	}

	java_library {
		name: "bar",
		libs: ["foo-V2-java"],
	}`

	expectedError := `module \"bar\" variant \"android_common\": foo-V2-java is an unfrozen development version`
	testAidlError(t, expectedError, nonVersionedModuleUsageInJavaBp, setTestFreezeEnv())
	testAidl(t, nonVersionedModuleUsageInJavaBp, withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	}), setReleaseEnv())
	testAidl(t, nonVersionedModuleUsageInJavaBp, withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	}))
}

func TestUnstableVersionedModuleOwnedByTestUsageInRelease(t *testing.T) {
	nonVersionedModuleUsageInJavaBp := `
	aidl_interface {
		name: "foo",
		owner: "test",
		srcs: [
			"IFoo.aidl",
		],
		versions: ["1"],
	}

	java_library {
		name: "bar",
		libs: ["foo-V2-java"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	})

	expectedError := `Android.bp:11:2: module \"bar\" variant \"android_common\": foo-V2-java is an unfrozen development version`
	testAidl(t, nonVersionedModuleUsageInJavaBp, setReleaseEnv(), files)
	testAidlError(t, expectedError, nonVersionedModuleUsageInJavaBp, setTestFreezeEnv(), files)
	testAidl(t, nonVersionedModuleUsageInJavaBp, files)
}

func TestFrozenModuleUsageInAllEnvs(t *testing.T) {
	bp := `
	aidl_interface {
		name: "foo",
        frozen: true,
		srcs: [
			"IFoo.aidl",
		],
		versions: ["1"],
	}

	java_library {
		name: "bar",
		libs: ["foo-V2-java"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	})

	expectedError := `Android.bp:11:2: module \"bar\" variant \"android_common\": foo-V2-java is an unfrozen development version`
	testAidlError(t, expectedError, bp, setReleaseEnv(), files)
	testAidlError(t, expectedError, bp, setTestFreezeEnv(), files)
	testAidlError(t, expectedError, bp, files)
}

func TestUnstableVersionedModuleOwnedByOtherUsageInRelease(t *testing.T) {
	nonVersionedModuleUsageInJavaBp := `
	aidl_interface {
		name: "foo",
		owner: "unknown-owner",
		srcs: [
			"IFoo.aidl",
		],
		versions: ["1"],
	}

	java_library {
		name: "bar",
		libs: ["foo-V2-java"],
	}`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	})

	testAidl(t, nonVersionedModuleUsageInJavaBp, setReleaseEnv(), files)
	testAidl(t, nonVersionedModuleUsageInJavaBp, setTestFreezeEnv(), files)
	testAidl(t, nonVersionedModuleUsageInJavaBp, files)
}

func TestUnstableModules(t *testing.T) {
	testAidlError(t, `module "foo_interface": stability: must be empty when "unstable" is true`, `
		aidl_interface {
			name: "foo",
			stability: "vintf",
			unstable: true,
			srcs: [
				"IFoo.aidl",
			],
			backend: {
				rust: {
					enabled: true,
				},
			},
		}
	`)

	testAidlError(t, `module "foo_interface": versions: cannot have versions for an unstable interface`, `
		aidl_interface {
			name: "foo",
			versions: [
				"1",
			],
			unstable: true,
			srcs: [
				"IFoo.aidl",
			],
			backend: {
				rust: {
					enabled: true,
				},
			},
		}
	`)

	ctx, _ := testAidl(t, `
		aidl_interface {
			name: "foo",
			unstable: true,
			srcs: [
				"IFoo.aidl",
			],
			backend: {
				rust: {
					enabled: true,
				},
			},
		}
	`)

	assertModulesExists(t, ctx, "foo-java", "foo-rust", "foo-cpp", "foo-ndk")
}

func TestCreatesModulesWithNoVersions(t *testing.T) {
	ctx, _ := testAidl(t, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			backend: {
				rust: {
					enabled: true,
				},
			},
		}
	`)

	assertModulesExists(t, ctx, "foo-V1-java", "foo-V1-rust", "foo-V1-cpp", "foo-V1-ndk")
}

func TestCreatesModulesWithFrozenVersions(t *testing.T) {
	// Each version should be under aidl_api/<name>/<ver>
	testAidlError(t, `No sources for a previous version in aidl_api/foo/1. Was a version manually added to .bp file?`, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			versions: [
				"1",
			],
			backend: {
				rust: {
					enabled: true,
				},
			},
		}
	`)

	ctx, _ := testAidl(t, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			versions: [
				"1",
			],
			backend: {
				rust: {
					enabled: true,
				},
			},
		}
	`, withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	}))

	// For frozen version "1"
	assertModulesExists(t, ctx, "foo-V1-java", "foo-V1-rust", "foo-V1-cpp", "foo-V1-ndk")

	// For ToT (current)
	assertModulesExists(t, ctx, "foo-V2-java", "foo-V2-rust", "foo-V2-cpp", "foo-V2-ndk")
}

func TestErrorsWithUnsortedVersions(t *testing.T) {
	testAidlError(t, `versions: should be sorted`, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			versions: [
				"2",
				"1",
			],
			backend: {
				rust: {
					enabled: true,
				},
			},
		}
	`)
}

func TestErrorsWithDuplicateVersions(t *testing.T) {
	testAidlError(t, `versions: duplicate`, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			versions: [
				"1",
				"1",
			],
		}
	`)
}

func TestErrorsWithNonPositiveVersions(t *testing.T) {
	testAidlError(t, `versions: should be > 0`, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			versions: [
				"-1",
				"1",
			],
		}
	`)
}

func TestErrorsWithNonIntegerVersions(t *testing.T) {
	testAidlError(t, `versions: "first" is not an integer`, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			versions: [
				"first",
			],
		}
	`)
}

const (
	androidVariant    = "android_common"
	nativeVariant     = "android_arm_armv7-a-neon_shared"
	nativeRustVariant = "android_arm_armv7-a-neon_dylib"
)

func TestNativeOutputIsAlwaysVersioned(t *testing.T) {
	var ctx *android.TestContext
	assertOutput := func(moduleName, variant, outputFilename string) {
		t.Helper()
		producer, ok := ctx.ModuleForTests(moduleName, variant).Module().(android.OutputFileProducer)
		if !ok {
			t.Errorf("%s(%s): should be OutputFileProducer.", moduleName, variant)
		}
		paths, err := producer.OutputFiles("")
		if err != nil {
			t.Errorf("%s(%s): failed to get OutputFiles: %v", moduleName, variant, err)
		}
		if len(paths) != 1 || paths[0].Base() != outputFilename {
			t.Errorf("%s(%s): expected output %q, but got %v", moduleName, variant, outputFilename, paths)
		}
	}

	// No versions
	ctx, _ = testAidl(t, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			backend: {
				rust: {
					enabled: true,
				},
			},
		}
	`)
	// Even though there is no version, generated modules have version(V1) unless it isn't an unstable interface.
	assertOutput("foo-V1-java", androidVariant, "foo-V1-java.jar")

	assertOutput("foo-V1-cpp", nativeVariant, "foo-V1-cpp.so")
	assertOutput("foo-V1-rust", nativeRustVariant, "libfoo_V1.dylib.so")

	// With versions: "1", "2"
	ctx, _ = testAidl(t, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			versions: [
				"1", "2",
			],
			backend: {
				rust: {
					enabled: true,
				},
			},
		}
	`, withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
		"aidl_api/foo/2/foo.2.aidl": nil,
		"aidl_api/foo/2/.hash":      nil,
	}))

	// alias for the latest frozen version (=2)
	assertOutput("foo-V2-java", androidVariant, "foo-V2-java.jar")
	assertOutput("foo-V2-cpp", nativeVariant, "foo-V2-cpp.so")
	assertOutput("foo-V2-rust", nativeRustVariant, "libfoo_V2.dylib.so")

	// frozen "1"
	assertOutput("foo-V1-java", androidVariant, "foo-V1-java.jar")
	assertOutput("foo-V1-cpp", nativeVariant, "foo-V1-cpp.so")
	assertOutput("foo-V1-rust", nativeRustVariant, "libfoo_V1.dylib.so")

	// tot
	assertOutput("foo-V3-java", androidVariant, "foo-V3-java.jar")
	assertOutput("foo-V3-cpp", nativeVariant, "foo-V3-cpp.so")
	assertOutput("foo-V3-rust", nativeRustVariant, "libfoo_V3.dylib.so")

	// skip ndk since they follow the same rule with cpp
}

func TestImports(t *testing.T) {
	testAidlError(t, `Import does not exist:`, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			imports: [
				"bar",
			]
		}
	`)

	testAidlError(t, `backend.java.enabled: Java backend not enabled in the imported AIDL interface "bar"`, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			imports: [
				"bar-V1",
			]
		}
		aidl_interface {
			name: "bar",
			srcs: [
				"IBar.aidl",
			],
			backend: {
				java: {
					enabled: false,
				},
			},
		}
	`)

	testAidlError(t, `backend.cpp.enabled: C\+\+ backend not enabled in the imported AIDL interface "bar"`, `
		aidl_interface {
			name: "foo",
			srcs: [
				"IFoo.aidl",
			],
			imports: [
				"bar-V1",
			]
		}
		aidl_interface {
			name: "bar",
			srcs: [
				"IBar.aidl",
			],
			backend: {
				cpp: {
					enabled: false,
				},
			},
		}
	`)

	testAidlError(t, `imports: "foo" depends on "bar" but does not specify a version`, `
		aidl_interface {
			name: "foo",
            unstable: true,
			srcs: [
				"IFoo.aidl",
			],
			imports: [
				"bar",
			]
		}
		aidl_interface {
			name: "bar",
			srcs: [
				"IBar.aidl",
			],
		}
	`)

	ctx, _ := testAidl(t, `
		aidl_interface_defaults {
			name: "foo-defaults",
			srcs: [
				"IFoo.aidl",
			],
			backend: {
				rust: {
					enabled: true,
				},
			},
			imports: [
				"bar.1-V1",
			]
		}
		aidl_interface {
			name: "foo",
			defaults: ["foo-defaults"],
		}
		aidl_interface {
			name: "bar.1",
			srcs: [
				"IBar.aidl",
			],
			backend: {
				rust: {
					enabled: true,
				},
			},
		}
	`)

	ldRule := ctx.ModuleForTests("foo-V1-cpp", nativeVariant).Rule("ld")
	libFlags := ldRule.Args["libFlags"]
	libBar := filepath.Join("bar.1-V1-cpp", nativeVariant, "bar.1-V1-cpp.so")
	if !strings.Contains(libFlags, libBar) {
		t.Errorf("%q is not found in %q", libBar, libFlags)
	}

	rustcRule := ctx.ModuleForTests("foo-V1-rust", nativeRustVariant).Rule("rustc")
	libFlags = rustcRule.Args["libFlags"]
	libBar = filepath.Join("out", "soong", ".intermediates", "bar.1-V1-rust", nativeRustVariant, "unstripped", "libbar_1_V1.dylib.so")
	libBarFlag := "--extern bar_1=" + libBar
	if !strings.Contains(libFlags, libBarFlag) {
		t.Errorf("%q is not found in %q", libBarFlag, libFlags)
	}
}

func TestDuplicatedVersions(t *testing.T) {
	// foo depends on myiface-V2-ndk via direct dep and also on
	// myiface-V1-ndk via indirect dep. This should be prohibited.
	testAidlError(t, `depends on multiple versions of the same aidl_interface: myiface-V1-.*, myiface-V2-.*`, `
		aidl_interface {
			name: "myiface",
			srcs: ["IFoo.aidl"],
			versions: ["1", "2"],
		}

		cc_library {
			name: "foo",
			shared_libs: ["myiface-V2-ndk", "bar"],
		}

		cc_library {
			name: "bar",
			shared_libs: ["myiface-V1-ndk"],
		}

	`, withFiles(map[string][]byte{
		"aidl_api/myiface/1/myiface.1.aidl": nil,
		"aidl_api/myiface/1/.hash":          nil,
		"aidl_api/myiface/2/myiface.2.aidl": nil,
		"aidl_api/myiface/2/.hash":          nil,
	}))
	testAidlError(t, `depends on multiple versions of the same aidl_interface: myiface-V1-.*, myiface-V2-.*`, `
		aidl_interface {
			name: "myiface",
			srcs: ["IFoo.aidl"],
			versions: ["1"],
		}

		aidl_interface {
			name: "myiface2",
			srcs: ["IBar.aidl"],
			imports: ["myiface-V2"]
		}

		cc_library {
			name: "foobar",
			shared_libs: ["myiface-V1-ndk", "myiface2-V1-ndk"],
		}

	`, withFiles(map[string][]byte{
		"aidl_api/myiface/1/myiface.1.aidl": nil,
		"aidl_api/myiface/1/.hash":          nil,
	}))
	testAidlError(t, `depends on multiple versions of the same aidl_interface: myiface-V1-.*, myiface-V2-.*`, `
		aidl_interface {
			name: "myiface",
			srcs: ["IFoo.aidl"],
			versions: ["1"],
		}

		aidl_interface {
			name: "myiface2",
			srcs: ["IBar.aidl"],
			imports: ["myiface-V2"]
		}

		cc_library {
			name: "foobar",
			srcs: [":myiface-V1-ndk-source"],
			shared_libs: ["myiface2-V1-ndk"],
		}

	`, withFiles(map[string][]byte{
		"aidl_api/myiface/1/myiface.1.aidl": nil,
		"aidl_api/myiface/1/.hash":          nil,
	}))
	// Okay to reference two different
	testAidl(t, `
		aidl_interface {
			name: "myiface",
			srcs: ["IFoo.aidl"],
			versions: ["1"],
		}
		cc_library {
			name: "foobar",
			shared_libs: ["myiface-V1-cpp", "myiface-V1-ndk"],
		}
	`, withFiles(map[string][]byte{
		"aidl_api/myiface/1/myiface.1.aidl": nil,
		"aidl_api/myiface/1/.hash":          nil,
	}))
	testAidl(t, `
		aidl_interface {
			name: "myiface",
			srcs: ["IFoo.aidl"],
			versions: ["1"],
		}

		aidl_interface {
			name: "myiface2",
			srcs: ["IBar.aidl"],
			imports: ["myiface-V2"]
		}

		cc_library {
			name: "foobar",
			srcs: [":myiface-V2-ndk-source"],
			shared_libs: ["myiface2-V1-ndk"],
		}

	`, withFiles(map[string][]byte{
		"aidl_api/myiface/1/myiface.1.aidl": nil,
		"aidl_api/myiface/1/.hash":          nil,
	}))
	testAidl(t, `
		aidl_interface {
			name: "myiface",
			srcs: ["IFoo.aidl"],
			versions: ["1"],
		}

		aidl_interface {
			name: "myiface2",
			srcs: ["IBar.aidl"],
			imports: ["myiface-V2"]
		}

		cc_library {
			name: "foobar",
			shared_libs: ["myiface-V2-ndk", "myiface2-V1-ndk"],
		}

	`, withFiles(map[string][]byte{
		"aidl_api/myiface/1/myiface.1.aidl": nil,
		"aidl_api/myiface/1/.hash":          nil,
	}))
}

func TestRecoveryAvailable(t *testing.T) {
	ctx, _ := testAidl(t, `
		aidl_interface {
			name: "myiface",
			recovery_available: true,
			srcs: ["IFoo.aidl"],
		}
	`)
	ctx.ModuleForTests("myiface-V1-ndk", "android_recovery_arm64_armv8-a_shared")
	ctx.ModuleForTests("myiface-V1-cpp", "android_recovery_arm64_armv8-a_shared")
}

func TestRustDuplicateNames(t *testing.T) {
	testAidl(t, `
		aidl_interface {
			name: "myiface",
			srcs: ["dir/a/Foo.aidl", "dir/b/Foo.aidl"],
			backend: {
				rust: {
					enabled: true,
				},
			},
		}
	`)
}

func TestAidlImportFlagsForImportedModules(t *testing.T) {
	customizer := withFiles(map[string][]byte{
		"foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo-iface",
				srcs: ["a/Foo.aidl"],
				imports: ["bar-iface-V2"],
				versions: ["1"],
				headers: ["boq-iface-headers"],
			}
		`),
		"foo/a/Foo.aidl": nil,
		"foo/aidl_api/foo-iface/current/a/Foo.aidl": nil,
		"foo/aidl_api/foo-iface/1/a/Foo.aidl":       nil,
		"foo/aidl_api/foo-iface/1/.hash":            nil,

		"bar/Android.bp": []byte(`
			aidl_interface {
				name: "bar-iface",
				srcs: ["b/Bar.aidl"],
				imports: ["baz-iface-V1"],
				versions: ["1"],
			}
		`),
		"bar/b/Bar.aidl": nil,
		"bar/aidl_api/bar-iface/current/b/Bar.aidl": nil,
		"bar/aidl_api/bar-iface/1/b/Bar.aidl":       nil,
		"bar/aidl_api/bar-iface/1/.hash":            nil,

		"baz/Android.bp": []byte(`
			aidl_interface {
				name: "baz-iface",
				srcs: ["b/Baz.aidl"],
				include_dirs: ["baz-include"],
				versions: ["1"],
			}
		`),
		"baz/b/Baz.aidl": nil,
		"baz/aidl_api/baz-iface/current/b/Baz.aidl": nil,
		"baz/aidl_api/baz-iface/1/b/Baz.aidl":       nil,
		"baz/aidl_api/baz-iface/1/.hash":            nil,

		"boq/Android.bp": []byte(`
			aidl_library {
				name: "boq-iface-headers",
				srcs: ["b/Boq.aidl"],
			}
		`),
		"boq/b/Baz.aidl": nil,
	})
	ctx, _ := testAidl(t, ``, customizer)

	// checkapidump rule is to compare "compatibility" between ToT(dump) and "current"
	{
		rule := ctx.ModuleForTests("foo-iface-api", "").Output("checkapi_dump.timestamp")
		android.AssertStringEquals(t, "checkapi(dump == current) imports", "-Iboq", rule.Args["imports"])
		android.AssertStringDoesContain(t, "checkapi(dump == current) optionalFlags",
			rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/bar/bar-iface_interface/2/preprocessed.aidl")
	}

	// has_development rule runs --checkapi for equality between latest("1")
	// and ToT
	{
		rule := ctx.ModuleForTests("foo-iface-api", "").Output("has_development")
		android.AssertStringDoesContain(t, "checkapi(dump == latest(1)) should import import's preprocessed",
			rule.RuleParams.Command,
			"-pout/soong/.intermediates/bar/bar-iface_interface/2/preprocessed.aidl")
	}

	// compile (v1)
	{
		rule := ctx.ModuleForTests("foo-iface-V1-cpp-source", "").Output("a/Foo.cpp")
		android.AssertStringEquals(t, "compile(old=1) should import aidl_api/1",
			"-Iboq -Nfoo/aidl_api/foo-iface/1",
			rule.Args["imports"]+" "+rule.Args["nextImports"])
		android.AssertStringDoesContain(t, "compile(old=1) should import bar.preprocessed",
			rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/bar/bar-iface_interface/2/preprocessed.aidl")
	}
	// compile ToT(v2)
	{
		rule := ctx.ModuleForTests("foo-iface-V2-cpp-source", "").Output("a/Foo.cpp")
		android.AssertStringEquals(t, "compile(tot=2) should import base dirs of srcs", "-Iboq -Nfoo", rule.Args["imports"]+" "+rule.Args["nextImports"])
		android.AssertStringDoesContain(t, "compile(tot=2) should import bar.preprocessed",
			rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/bar/bar-iface_interface/2/preprocessed.aidl")
	}
}

func TestAidlPreprocess(t *testing.T) {
	customizer := withFiles(map[string][]byte{
		"foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo-iface",
				local_include_dir: "src",
				include_dirs: [
						"path1",
						"path2/sub",
				],
				srcs: [
						"src/foo/Foo.aidl",
				],
				imports: [
					"bar-iface",
				],
				unstable: true,
			}
			aidl_interface {
				name: "bar-iface",
				local_include_dir: "src",
				srcs: [
						"src/bar/Bar.aidl",
				],
				unstable: true,
			}
		`),
		"foo/src/foo/Foo.aidl": nil,
		"foo/src/bar/Bar.aidl": nil,
	})
	ctx, _ := testAidl(t, ``, customizer)

	rule := ctx.ModuleForTests("foo-iface_interface", "").Output("preprocessed.aidl")
	android.AssertStringDoesContain(t, "preprocessing should import srcs and include_dirs",
		rule.RuleParams.Command,
		"-Ifoo/src -Ipath1 -Ipath2/sub")
	android.AssertStringDoesContain(t, "preprocessing should import import's preprocess",
		rule.RuleParams.Command,
		"-pout/soong/.intermediates/foo/bar-iface_interface/preprocessed.aidl")
}

func TestAidlImportFlagsForUnstable(t *testing.T) {
	customizer := withFiles(map[string][]byte{
		"foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo-iface",
				local_include_dir: "src",
				include_dirs: [
						"path1",
						"path2/sub",
				],
				srcs: [
						"src/foo/Foo.aidl",
				],
				imports: [
					"bar-iface",
				],
				unstable: true,
			}
			aidl_interface {
				name: "bar-iface",
				local_include_dir: "src",
				srcs: [
						"src/bar/Bar.aidl",
				],
				unstable: true,
			}
		`),
		"foo/src/foo/Foo.aidl": nil,
		"foo/src/bar/Bar.aidl": nil,
	})
	ctx, _ := testAidl(t, ``, customizer)

	rule := ctx.ModuleForTests("foo-iface-cpp-source", "").Output("foo/Foo.cpp")
	android.AssertStringEquals(t, "compile(unstable) should import foo/base_dirs(target) and bar/base_dirs(imported)",
		"-Ipath1 -Ipath2/sub -Nfoo/src",
		rule.Args["imports"]+" "+rule.Args["nextImports"])
	android.AssertStringDoesContain(t, "compile(unstable) should import bar.preprocessed",
		rule.Args["optionalFlags"],
		"-pout/soong/.intermediates/foo/bar-iface_interface/preprocessed.aidl")
}

func TestSupportsGenruleAndFilegroup(t *testing.T) {
	customizer := withFiles(map[string][]byte{
		"foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo-iface",
				local_include_dir: "src",
				include_dirs: [
						"path1",
						"path2/sub",
				],
				srcs: [
						"src/foo/Foo.aidl",
						":filegroup1",
						":gen1",
				],
				imports: [
						"bar-iface-V1",
				],
				versions: ["1"],
			}
			filegroup {
				name: "filegroup1",
				path: "filegroup/sub",
				srcs: [
						"filegroup/sub/pkg/Bar.aidl",
				],
			}
			genrule {
				name: "gen1",
				cmd: "generate baz/Baz.aidl",
				out: [
					"baz/Baz.aidl",
				]
			}
			aidl_interface {
				name: "bar-iface",
				local_include_dir: "src",
				srcs: [
						"src/bar/Bar.aidl",
						":gen-bar",
				],
			}
			genrule {
				name: "gen-bar",
				cmd: "generate gen/GenBar.aidl",
				out: [
					"gen/GenBar.aidl",
				]
			}
		`),
		"foo/aidl_api/foo-iface/1/foo/Foo.aidl": nil,
		"foo/aidl_api/foo-iface/1/.hash":        nil,
		"foo/filegroup/sub/pkg/Bar.aidl":        nil,
		"foo/src/foo/Foo.aidl":                  nil,
	})
	ctx, _ := testAidl(t, ``, customizer)

	// aidlCompile for snapshots (v1)
	{
		rule := ctx.ModuleForTests("foo-iface-V1-cpp-source", "").Output("foo/Foo.cpp")
		android.AssertStringEquals(t, "compile(1) should import foo/aidl_api/1",
			"-Ipath1 -Ipath2/sub -Nfoo/aidl_api/foo-iface/1",
			rule.Args["imports"]+" "+rule.Args["nextImports"])
		android.AssertStringDoesContain(t, "compile(1) should import bar.preprocessed",
			rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/foo/bar-iface_interface/1/preprocessed.aidl")
	}
	// aidlCompile for ToT (v2)
	{
		rule := ctx.ModuleForTests("foo-iface-V2-cpp-source", "").Output("foo/Foo.cpp")
		android.AssertStringEquals(t, "compile(tot=2) should import foo.base_dirs",
			"-Ipath1 -Ipath2/sub -Nfoo/src -Nfoo/filegroup/sub -Nout/soong/.intermediates/foo/gen1/gen",
			rule.Args["imports"]+" "+rule.Args["nextImports"])
		android.AssertStringDoesContain(t, "compile(tot=2) should import bar.preprocessed",
			rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/foo/bar-iface_interface/1/preprocessed.aidl")
	}

	// dumpapi
	{
		rule := ctx.ModuleForTests("foo-iface-api", "").Rule("aidlDumpApiRule")
		android.AssertPathsRelativeToTopEquals(t, "dumpapi should dump srcs/filegroups/genrules", []string{
			"foo/src/foo/Foo.aidl",
			"foo/filegroup/sub/pkg/Bar.aidl",
			"out/soong/.intermediates/foo/gen1/gen/baz/Baz.aidl",
		}, rule.Inputs)

		dumpDir := "out/soong/.intermediates/foo/foo-iface-api/dump"
		android.AssertPathsRelativeToTopEquals(t, "dumpapi should dump with rel paths", []string{
			dumpDir + "/foo/Foo.aidl",
			dumpDir + "/pkg/Bar.aidl",
			dumpDir + "/baz/Baz.aidl",
			dumpDir + "/.hash",
		}, rule.Outputs.Paths())

		android.AssertStringEquals(t, "dumpapi should import base_dirs and include_dirs",
			"-Ifoo/src -Ifoo/filegroup/sub -Iout/soong/.intermediates/foo/gen1/gen -Ipath1 -Ipath2/sub",
			rule.Args["imports"])
		android.AssertStringDoesContain(t, "dumpapi should import bar.preprocessed",
			rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/foo/bar-iface_interface/1/preprocessed.aidl")
	}
}

func TestAidlFlags(t *testing.T) {
	ctx, _ := testAidl(t, `
		aidl_interface {
			name: "myiface",
			srcs: ["a/Foo.aidl", "b/Bar.aidl"],
			flags: ["-Weverything", "-Werror"],
			backend: { rust: { enabled: true }}
		}
	`)
	for module, outputs := range map[string][]string{
		"myiface-V1-cpp-source":  {"a/Foo.h", "b/Bar.h"},
		"myiface-V1-java-source": {"a/Foo.java", "b/Bar.java"},
		"myiface-V1-ndk-source":  {"aidl/a/Foo.h", "aidl/b/Bar.h"},
		"myiface-V1-rust-source": {"a/Foo.rs", "b/Bar.rs"},
	} {
		for _, output := range outputs {
			t.Run(module+"/"+output, func(t *testing.T) {
				params := ctx.ModuleForTests(module, "").Output(output)
				assertContains(t, params.Args["optionalFlags"], "-Weverything")
				assertContains(t, params.Args["optionalFlags"], "-Werror")
			})
		}
	}
}

func TestAidlModuleJavaSdkVersionDeterminesMinSdkVersion(t *testing.T) {
	ctx, _ := testAidl(t, `
		aidl_interface {
			name: "myiface",
			srcs: ["a/Foo.aidl"],
			backend: {
				java: {
					sdk_version: "28",
				},
			},
		}
	`, java.FixtureWithPrebuiltApis(map[string][]string{"28": {"foo"}}))
	params := ctx.ModuleForTests("myiface-V1-java-source", "").Output("a/Foo.java")
	assertContains(t, params.Args["optionalFlags"], "--min_sdk_version 28")
}

func TestAidlModuleNameContainsVersion(t *testing.T) {
	testAidlError(t, "aidl_interface should not have '-V<number> suffix", `
		aidl_interface {
			name: "myiface-V2",
			srcs: ["a/Foo.aidl", "b/Bar.aidl"],
		}
	`)
	// Ugly, but okay
	testAidl(t, `
		aidl_interface {
			name: "myiface-V2aa",
			srcs: ["a/Foo.aidl", "b/Bar.aidl"],
		}
	`)
}

func TestExplicitAidlModuleImport(t *testing.T) {
	for _, importVersion := range []string{"V1", "V2"} {

		ctx, _ := testAidl(t, `
			aidl_interface {
				name: "foo",
				srcs: ["Foo.aidl"],
				versions: [
					"1",
				],
				imports: ["bar-`+importVersion+`"]
			}

			aidl_interface {
				name: "bar",
				srcs: ["Bar.aidl"],
				versions: [
					"1",
				],
			}
		`, withFiles(map[string][]byte{
			"aidl_api/foo/1/Foo.aidl": nil,
			"aidl_api/foo/1/.hash":    nil,
			"aidl_api/bar/1/Bar.aidl": nil,
			"aidl_api/bar/1/.hash":    nil,
		}))
		for _, foo := range []string{"foo-V1-cpp", "foo-V2-cpp"} {
			ldRule := ctx.ModuleForTests(foo, nativeVariant).Rule("ld")
			libFlags := ldRule.Args["libFlags"]
			libBar := filepath.Join("bar-"+importVersion+"-cpp", nativeVariant, "bar-"+importVersion+"-cpp.so")
			if !strings.Contains(libFlags, libBar) {
				t.Errorf("%q is not found in %q", libBar, libFlags)
			}

		}
	}

	testAidlError(t, "module \"foo_interface\": imports: \"foo\" depends on \"bar\" version \"3\"", `
		aidl_interface {
			name: "foo",
			srcs: ["Foo.aidl"],
			versions: [
				"1",
			],
			imports: ["bar-V3"]
		}

		aidl_interface {
			name: "bar",
			srcs: ["Bar.aidl"],
			versions: [
				"1",
			],
		}
	`, withFiles(map[string][]byte{
		"aidl_api/foo/1/Foo.aidl": nil,
		"aidl_api/foo/1/.hash":    nil,
		"aidl_api/bar/1/Bar.aidl": nil,
		"aidl_api/bar/1/.hash":    nil,
	}))
}

func TestUseVersionedPreprocessedWhenImporotedWithVersions(t *testing.T) {
	ctx, _ := testAidl(t, `
		aidl_interface {
			name: "unstable-foo",
			srcs: ["foo/Foo.aidl"],
			imports: [
					"bar-V2",
					"baz-V1",
					"unstable-bar",
			],
			unstable: true,
		}
		aidl_interface {
			name: "foo",
			srcs: ["foo/Foo.aidl"],
			imports: [
					"bar-V1",
					"baz-V1",
			],
			versions: ["1"],
		}
		aidl_interface {
			name: "foo-no-versions",
			srcs: ["foo/Foo.aidl"],
			imports: [
					"bar-V2",
			],
		}
		aidl_interface {
			name: "bar",
			srcs: ["bar/Bar.aidl"],
			versions: ["1"],
		}
		aidl_interface {
			name: "unstable-bar",
			srcs: ["bar/Bar.aidl"],
			unstable: true,
		}
		aidl_interface {
			name: "baz",
			srcs: ["baz/Baz.aidl"],
			versions: ["1"],
		}
	`, withFiles(map[string][]byte{
		"foo/Foo.aidl":                nil,
		"bar/Bar.aidl":                nil,
		"baz/Baz.aidl":                nil,
		"aidl_api/foo/1/foo/Foo.aidl": nil,
		"aidl_api/foo/1/.hash":        nil,
		"aidl_api/bar/1/bar/Bar.aidl": nil,
		"aidl_api/bar/1/.hash":        nil,
		"aidl_api/baz/1/baz/Baz.aidl": nil,
		"aidl_api/baz/1/.hash":        nil,
	}))
	{
		rule := ctx.ModuleForTests("foo-V2-java-source", "").Output("foo/Foo.java")
		android.AssertStringDoesContain(t, "foo-V2(tot) imports bar-V1 for 'bar-V1'", rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/bar_interface/1/preprocessed.aidl")
		android.AssertStringDoesContain(t, "foo-V2(tot) imports baz-V1 for 'baz-V1'", rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/baz_interface/1/preprocessed.aidl")
	}
	{
		rule := ctx.ModuleForTests("foo-V1-java-source", "").Output("foo/Foo.java")
		android.AssertStringDoesContain(t, "foo-V1 imports bar-V1(latest) for 'bar'", rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/bar_interface/1/preprocessed.aidl")
		android.AssertStringDoesContain(t, "foo-V1 imports baz-V1 for 'baz-V1'", rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/baz_interface/1/preprocessed.aidl")
	}
	{
		rule := ctx.ModuleForTests("unstable-foo-java-source", "").Output("foo/Foo.java")
		android.AssertStringDoesContain(t, "unstable-foo imports bar-V2(latest) for 'bar'", rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/bar_interface/2/preprocessed.aidl")
		android.AssertStringDoesContain(t, "unstable-foo imports baz-V1 for 'baz-V1'", rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/baz_interface/1/preprocessed.aidl")
		android.AssertStringDoesContain(t, "unstable-foo imports unstable-bar(ToT) for 'unstable-bar'", rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/unstable-bar_interface/preprocessed.aidl")
	}
	{
		rule := ctx.ModuleForTests("foo-no-versions-V1-java-source", "").Output("foo/Foo.java")
		android.AssertStringDoesContain(t, "foo-no-versions-V1(latest) imports bar-V2(latest) for 'bar'", rule.Args["optionalFlags"],
			"-pout/soong/.intermediates/bar_interface/2/preprocessed.aidl")
	}
}

func FindModule(ctx *android.TestContext, name, variant, dir string) android.Module {
	var module android.Module
	ctx.VisitAllModules(func(m blueprint.Module) {
		if ctx.ModuleName(m) == name && ctx.ModuleSubDir(m) == variant && ctx.ModuleDir(m) == dir {
			module = m.(android.Module)
		}
	})
	if module == nil {
		m := ctx.ModuleForTests(name, variant).Module()
		panic(fmt.Errorf("failed to find module %q variant %q dir %q, but found one in %q",
			name, variant, dir, ctx.ModuleDir(m)))
	}
	return module
}

func TestDuplicateInterfacesWithTheSameNameInDifferentSoongNamespaces(t *testing.T) {
	ctx, _ := testAidl(t, ``, withFiles(map[string][]byte{
		"common/Android.bp": []byte(`
		  aidl_interface {
				name: "common",
				srcs: ["ICommon.aidl"],
				versions: ["1", "2"],
			}
		`),
		"common/aidl_api/common/1/ICommon.aidl": nil,
		"common/aidl_api/common/1/.hash":        nil,
		"common/aidl_api/common/2/ICommon.aidl": nil,
		"common/aidl_api/common/2/.hash":        nil,
		"vendor/a/Android.bp": []byte(`
			soong_namespace {}
		`),
		"vendor/a/foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo",
				owner: "vendor",
				srcs: ["IFoo.aidl"],
				imports: ["common-V1"],
			}
		`),
		"vendor/b/Android.bp": []byte(`
			soong_namespace {}
		`),
		"vendor/b/foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo",
				owner: "vendor",
				srcs: ["IFoo.aidl"],
				imports: ["common-V2"],
			}
		`),
	}))

	aFooV1Java := FindModule(ctx, "foo-V1-java", "android_common", "vendor/a/foo").(*java.Library)
	android.AssertStringListContains(t, "a/foo deps", aFooV1Java.CompilerDeps(), "common-V1-java")

	bFooV1Java := FindModule(ctx, "foo-V1-java", "android_common", "vendor/b/foo").(*java.Library)
	android.AssertStringListContains(t, "a/foo deps", bFooV1Java.CompilerDeps(), "common-V2-java")
}

func TestUnstableChecksForAidlInterfacesInDifferentNamespaces(t *testing.T) {
	files := withFiles(map[string][]byte{
		"vendor/a/Android.bp": []byte(`
			soong_namespace {}
		`),
		"vendor/a/foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo",
				owner: "vendor",
				srcs: ["IFoo.aidl"],
				versions: ["1", "2"],
			}
			java_library {
				name: "bar",
				libs: ["foo-V2-java"],  // OK
			}
		`),
		"vendor/a/foo/aidl_api/foo/1/IFoo.aidl": nil,
		"vendor/a/foo/aidl_api/foo/1/.hash":     nil,
		"vendor/a/foo/aidl_api/foo/2/IFoo.aidl": nil,
		"vendor/a/foo/aidl_api/foo/2/.hash":     nil,
		"vendor/b/Android.bp": []byte(`
			soong_namespace {}
		`),
		"vendor/b/foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo",
				owner: "vendor",
				srcs: ["IFoo.aidl"],
				versions: ["1"],
			}
			java_library {
				name: "bar",
				libs: ["foo-V1-java"],  // OK
			}
		`),
		"vendor/b/foo/aidl_api/foo/1/IFoo.aidl": nil,
		"vendor/b/foo/aidl_api/foo/1/.hash":     nil,
	})

	testAidl(t, ``, files, setReleaseEnv())
	testAidl(t, ``, files, setTestFreezeEnv())
	testAidl(t, ``, files)
}

func TestVersionsWithInfoAndVersions(t *testing.T) {
	conflictingFields := `
	aidl_interface {
		name: "foo",
		versions: [
			"1",
		],
		versions_with_info: [
			{
				version: "1",
			}
		],
	}
	`
	files := withFiles(map[string][]byte{
		"aidl_api/foo/1/foo.1.aidl": nil,
		"aidl_api/foo/1/.hash":      nil,
	})

	expectedError := `Use versions_with_info instead of versions.`
	testAidlError(t, expectedError, conflictingFields, files)
}

func TestVersionsWithInfo(t *testing.T) {
	ctx, _ := testAidl(t, ``, withFiles(map[string][]byte{
		"common/Android.bp": []byte(`
		  aidl_interface {
				name: "common",
				srcs: ["ICommon.aidl"],
				versions: ["1", "2"],
			}
		`),
		"common/aidl_api/common/1/ICommon.aidl": nil,
		"common/aidl_api/common/1/.hash":        nil,
		"common/aidl_api/common/2/ICommon.aidl": nil,
		"common/aidl_api/common/2/.hash":        nil,
		"foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo",
				srcs: ["IFoo.aidl"],
				imports: ["common-V3"],
				versions_with_info: [
					{version: "1", imports: ["common-V1"]},
					{version: "2", imports: ["common-V2"]},
				]
			}
		`),
		"foo/aidl_api/foo/1/IFoo.aidl": nil,
		"foo/aidl_api/foo/1/.hash":     nil,
		"foo/aidl_api/foo/2/IFoo.aidl": nil,
		"foo/aidl_api/foo/2/.hash":     nil,
	}))

	fooV1Java := FindModule(ctx, "foo-V1-java", "android_common", "foo").(*java.Library)
	android.AssertStringListContains(t, "a/foo-v1 deps", fooV1Java.CompilerDeps(), "common-V1-java")

	fooV2Java := FindModule(ctx, "foo-V2-java", "android_common", "foo").(*java.Library)
	android.AssertStringListContains(t, "a/foo-v2 deps", fooV2Java.CompilerDeps(), "common-V2-java")

	fooV3Java := FindModule(ctx, "foo-V3-java", "android_common", "foo").(*java.Library)
	android.AssertStringListContains(t, "a/foo-v3 deps", fooV3Java.CompilerDeps(), "common-V3-java")
}

func TestVersionsWithInfoImport(t *testing.T) {
	testAidlError(t, "imports in versions_with_info must specify its version", ``, withFiles(map[string][]byte{
		"common/Android.bp": []byte(`
		  aidl_interface {
				name: "common",
				srcs: ["ICommon.aidl"],
				versions: ["1", "2"],
			}
		`),
		"common/aidl_api/common/1/ICommon.aidl": nil,
		"common/aidl_api/common/1/.hash":        nil,
		"common/aidl_api/common/2/ICommon.aidl": nil,
		"common/aidl_api/common/2/.hash":        nil,
		"foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo",
				srcs: ["IFoo.aidl"],
				imports: ["common"],
				versions_with_info: [
					{version: "1", imports: ["common"]},
					{version: "2", imports: ["common-V2"]},
				]
			}
		`),
		"foo/aidl_api/foo/1/IFoo.aidl": nil,
		"foo/aidl_api/foo/1/.hash":     nil,
		"foo/aidl_api/foo/2/IFoo.aidl": nil,
		"foo/aidl_api/foo/2/.hash":     nil,
	}))
}

func TestFreezeApiDeps(t *testing.T) {
	for _, transitive := range []bool{true, false} {
		for _, testcase := range []struct {
			string
			bool
		}{{"common-V3", true}, {"common-V2", false}} {
			im := testcase.string
			customizers := []android.FixturePreparer{
				withFiles(map[string][]byte{
					"common/Android.bp": []byte(`
				  aidl_interface {
						name: "common",
						frozen: false,
						srcs: ["ICommon.aidl"],
						versions: ["1", "2"],
					}
				`),
					"common/aidl_api/common/1/ICommon.aidl": nil,
					"common/aidl_api/common/1/.hash":        nil,
					"common/aidl_api/common/2/ICommon.aidl": nil,
					"common/aidl_api/common/2/.hash":        nil,
					"foo/Android.bp": []byte(fmt.Sprintf(`
					aidl_interface {
						name: "foo",
						srcs: ["IFoo.aidl"],
						imports: ["%s"],
						frozen: false,
						versions_with_info: [
							{version: "1", imports: ["common-V1"]},
							{version: "2", imports: ["common-V2"]},
						]
					}
				`, im)),
					"foo/aidl_api/foo/1/IFoo.aidl": nil,
					"foo/aidl_api/foo/1/.hash":     nil,
					"foo/aidl_api/foo/2/IFoo.aidl": nil,
					"foo/aidl_api/foo/2/.hash":     nil,
				}),
			}
			if transitive {
				customizers = append(customizers, setTransitiveFreezeEnv())
			}

			ctx, _ := testAidl(t, ``, customizers...)
			shouldHaveDep := transitive && testcase.bool
			fooFreezeApiRule := ctx.ModuleForTests("foo-api", "").Output("update_or_freeze_api_3.timestamp")
			commonFreezeApiOutput := ctx.ModuleForTests("common-api", "").Output("update_or_freeze_api_3.timestamp").Output.String()
			testMethod := android.AssertStringListDoesNotContain
			if shouldHaveDep {
				testMethod = android.AssertStringListContains
			}
			testMethod(t, "Only if AIDL_TRANSITIVE_FREEZE is set and an aidl_interface depends on an another aidl_interface's ToT version, an imported aidl_interface should be frozen as well.",
				fooFreezeApiRule.Implicits.Strings(), commonFreezeApiOutput)
		}
	}
}

func TestAidlNoUnfrozen(t *testing.T) {
	customizer := withFiles(map[string][]byte{
		"foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo-iface",
				srcs: ["a/Foo.aidl"],
				versions: ["1", "2"],
			}
		`),
		"foo/a/Foo.aidl": nil,
		"foo/aidl_api/foo-iface/current/a/Foo.aidl": nil,
		"foo/aidl_api/foo-iface/1/a/Foo.aidl":       nil,
		"foo/aidl_api/foo-iface/1/.hash":            nil,
		"foo/aidl_api/foo-iface/2/a/Foo.aidl":       nil,
		"foo/aidl_api/foo-iface/2/.hash":            nil,
	})
	// setReleaseEnv() to set RELEASE_AIDL_USE_UNFROZEN to false
	ctx, _ := testAidl(t, ``, setReleaseEnv(), customizer)

	// compile (v1)
	{
		rule := ctx.ModuleForTests("foo-iface-V1-cpp-source", "").Output("a/Foo.cpp")
		android.AssertStringDoesNotContain(t, "Frozen versions should not have the -previous_api_dir set",
			rule.Args["optionalFlags"],
			"-previous")
	}
	// compile (v2)
	{
		rule := ctx.ModuleForTests("foo-iface-V2-cpp-source", "").Output("a/Foo.cpp")
		android.AssertStringDoesNotContain(t, "Frozen versions should not have the -previous_api_dir set",
			rule.Args["optionalFlags"],
			"-previous")
	}
	// compile ToT(v3)
	{
		rule := ctx.ModuleForTests("foo-iface-V3-cpp-source", "").Output("a/Foo.cpp")
		android.AssertStringDoesContain(t, "An unfrozen interface with previously frozen version must have --previous_api_dir when RELEASE_AIDL_USE_UNFROZEN is false (setReleaseEnv())",
			rule.Args["optionalFlags"],
			"-previous_api_dir")
		android.AssertStringDoesContain(t, "An unfrozen interface with previously frozen version must have --previous_hash when RELEASE_AIDL_USE_UNFROZEN is false (setReleaseEnv())",
			rule.Args["optionalFlags"],
			"-previous_hash")
		android.AssertStringDoesContain(t, "--previous_hash must use the last frozen version's hash file",
			rule.Args["optionalFlags"],
			"foo-iface/2/.hash")
	}
}

func TestAidlUsingUnfrozen(t *testing.T) {
	customizer := withFiles(map[string][]byte{
		"foo/Android.bp": []byte(`
			aidl_interface {
				name: "foo-iface",
				srcs: ["a/Foo.aidl"],
				versions: ["1", "2"],
			}
		`),
		"foo/a/Foo.aidl": nil,
		"foo/aidl_api/foo-iface/current/a/Foo.aidl": nil,
		"foo/aidl_api/foo-iface/1/a/Foo.aidl":       nil,
		"foo/aidl_api/foo-iface/1/.hash":            nil,
		"foo/aidl_api/foo-iface/2/a/Foo.aidl":       nil,
		"foo/aidl_api/foo-iface/2/.hash":            nil,
	})
	ctx, _ := testAidl(t, ``, customizer)

	// compile (v2)
	{
		rule := ctx.ModuleForTests("foo-iface-V2-cpp-source", "").Output("a/Foo.cpp")
		android.AssertStringDoesNotContain(t, "Frozen versions should not have the -previous_api_dir set",
			rule.Args["optionalFlags"],
			"-previous")
	}
	// compile ToT(v3)
	{
		rule := ctx.ModuleForTests("foo-iface-V3-cpp-source", "").Output("a/Foo.cpp")
		android.AssertStringDoesNotContain(t, "Unfrozen versions should not have the -previous options when RELEASE_AIDL_USE_UNFROZEN is true (default)",
			rule.Args["optionalFlags"],
			"-previous")
	}
}

func TestAidlUseUnfrozenOverrideFalse(t *testing.T) {
	customizer := withFiles(map[string][]byte{
		"foo/Android.bp": []byte(`
                        aidl_interface {
                                name: "foo-iface",
                                srcs: ["a/Foo.aidl"],
                                versions: ["1"],
                        }
                `),
		"foo/a/Foo.aidl": nil, "foo/aidl_api/foo-iface/current/a/Foo.aidl": nil,
		"foo/aidl_api/foo-iface/1/a/Foo.aidl": nil, "foo/aidl_api/foo-iface/1/.hash": nil,
	})
	ctx, _ := testAidl(t, ``, setUseUnfrozenOverrideEnvFalse(), customizer)

	rule := ctx.ModuleForTests("foo-iface-V2-cpp-source", "").Output("a/Foo.cpp")
	android.AssertStringDoesContain(t, "Unfrozen interfaces should have -previous_api_dir set when overriding the RELEASE_AIDL_USE_UNFROZEN flag",
		rule.Args["optionalFlags"],
		"-previous")
}

func TestAidlUseUnfrozenOverrideTrue(t *testing.T) {
	customizer := withFiles(map[string][]byte{
		"foo/Android.bp": []byte(`
                        aidl_interface {
                                name: "foo-iface",
                                srcs: ["a/Foo.aidl"],
                                versions: ["1"],
                        }
                `),
		"foo/a/Foo.aidl": nil, "foo/aidl_api/foo-iface/current/a/Foo.aidl": nil,
		"foo/aidl_api/foo-iface/1/a/Foo.aidl": nil, "foo/aidl_api/foo-iface/1/.hash": nil,
	})
	ctx, _ := testAidl(t, ``, setUseUnfrozenOverrideEnvTrue(), customizer)

	rule := ctx.ModuleForTests("foo-iface-V2-cpp-source", "").Output("a/Foo.cpp")
	android.AssertStringDoesNotContain(t, "Unfrozen interfaces should not have -previous_api_dir set when overriding the RELEASE_AIDL_USE_UNFROZEN flag",
		rule.Args["optionalFlags"],
		"-previous")
}
