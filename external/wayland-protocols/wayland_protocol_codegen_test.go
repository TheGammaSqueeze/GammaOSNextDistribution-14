// Copyright 2023 The Android Open Source Project
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

package soong_wayland_protocol_codegen

import (
	"os"
	"regexp"
	"testing"

	"android/soong/android"
)

func TestMain(m *testing.M) {
	os.Exit(m.Run())
}

var prepareForCodeGenTest = android.GroupFixturePreparers(
	android.PrepareForTestWithArchMutator,
	android.PrepareForTestWithDefaults,
	android.PrepareForTestWithFilegroup,
	android.GroupFixturePreparers(
		android.FixtureRegisterWithContext(registerCodeGenBuildComponents),
	),
	android.FixtureRegisterWithContext(func(ctx android.RegistrationContext) {
		android.RegisterPrebuiltMutators(ctx)
		ctx.RegisterModuleType("fake_android_host_tool", fakeAndroidHostToolFactory)
	}),
	android.FixtureMergeMockFs(android.MockFS{
		"android_host_tool": nil,
		"tool_src_file":     nil,
		"tool_src_file_1":   nil,
		"tool_src_file_2":   nil,
		"src_file":          nil,
		"src_file_1":        nil,
		"src_file_2":        nil,
	}),
)

func testCodeGenBp() string {
	return `
		fake_android_host_tool {
			name: "host_tool",
		}

		filegroup {
			name: "tool_single_source_file_filegroup",
			srcs: [
				"tool_src_file",
			],
		}

		filegroup {
			name: "tool_multi_source_files_filegroup",
			srcs: [
				"tool_src_file_1",
				"tool_src_file_2",
			],
		}

		filegroup {
			name: "single_source_filegroup",
			srcs: [
				"src_file",
			],
		}

		filegroup {
			name: "multi_source_filegroup",
			srcs: [
				"src_file_1",
				"src_file_2",
			],
		}

		filegroup {
			name: "empty_filegroup",
		}
	`
}

func TestWaylandCodeGen(t *testing.T) {
	testcases := []struct {
		name string
		prop string

		err   string
		cmds  []string
		files []string
	}{
		{
			name: "single_source_with_host_tool",
			prop: `
				tools: ["host_tool"],
				srcs: ["src_file"],
				output: "prefix_$(in)_suffix",
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < src_file > __SBOX_SANDBOX_DIR__/out/prefix_src_file_suffix'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_suffix",
			},
		},
		{
			name: "multi_source_with_host_tool",
			prop: `
				tools: ["host_tool"],
				srcs: ["src_file_1", "src_file_2"],
				output: "prefix_$(in)_suffix",
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < src_file_1 > __SBOX_SANDBOX_DIR__/out/prefix_src_file_1_suffix' && bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < src_file_2 > __SBOX_SANDBOX_DIR__/out/prefix_src_file_2_suffix'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_1_suffix",
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_2_suffix",
			},
		},
		{
			name: "single_source_filegroup_with_host_tool",
			prop: `
				tools: ["host_tool"],
				srcs: [":single_source_filegroup"],
				output: "prefix_$(in)_suffix",
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < src_file > __SBOX_SANDBOX_DIR__/out/prefix_src_file_suffix'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_suffix",
			},
		},
		{
			name: "multi_source_filegroup_with_host_tool",
			prop: `
				tools: ["host_tool"],
				srcs: [":multi_source_filegroup"],
				output: "prefix_$(in)_suffix",
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < src_file_1 > __SBOX_SANDBOX_DIR__/out/prefix_src_file_1_suffix' && bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < src_file_2 > __SBOX_SANDBOX_DIR__/out/prefix_src_file_2_suffix'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_1_suffix",
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_2_suffix",
			},
		},
		{
			name: "single_source_with_single_tool_file",
			prop: `
				tool_files: ["tool_src_file"],
				srcs: ["src_file"],
				output: "prefix_$(in)_suffix",
				cmd: "$(location tool_src_file) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/tool_src_file gen < src_file > __SBOX_SANDBOX_DIR__/out/prefix_src_file_suffix'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_suffix",
			},
		},
		{
			name: "multi_source_with_single_tool_file",
			prop: `
				tool_files: ["tool_src_file"],
				srcs: ["src_file_1", "src_file_2"],
				output: "prefix_$(in)_suffix",
				cmd: "$(location tool_src_file) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/tool_src_file gen < src_file_1 > __SBOX_SANDBOX_DIR__/out/prefix_src_file_1_suffix' && bash -c '__SBOX_SANDBOX_DIR__/tools/src/tool_src_file gen < src_file_2 > __SBOX_SANDBOX_DIR__/out/prefix_src_file_2_suffix'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_1_suffix",
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_2_suffix",
			},
		},
		{
			name: "single_source_filegroup_with_single_tool_file",
			prop: `
				tool_files: ["tool_src_file"],
				srcs: [":single_source_filegroup"],
				output: "prefix_$(in)_suffix",
				cmd: "$(location tool_src_file) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/tool_src_file gen < src_file > __SBOX_SANDBOX_DIR__/out/prefix_src_file_suffix'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_suffix",
			},
		},
		{
			name: "multi_source_filegroup_with_single_tool_file",
			prop: `
				tool_files: ["tool_src_file"],
				srcs: [":multi_source_filegroup"],
				output: "prefix_$(in)_suffix",
				cmd: "$(location tool_src_file) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/tool_src_file gen < src_file_1 > __SBOX_SANDBOX_DIR__/out/prefix_src_file_1_suffix' && bash -c '__SBOX_SANDBOX_DIR__/tools/src/tool_src_file gen < src_file_2 > __SBOX_SANDBOX_DIR__/out/prefix_src_file_2_suffix'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_1_suffix",
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_2_suffix",
			},
		},
		{
			name: "multiple_tool_files",
			prop: `
				tool_files: ["tool_src_file_1", "tool_src_file_2"],
				srcs: ["src_file"],
				output: "prefix_$(in)_suffix",
				cmd: "$(location tool_src_file_1) $(location tool_src_file_2) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/tool_src_file_1 __SBOX_SANDBOX_DIR__/tools/src/tool_src_file_2 gen < src_file > __SBOX_SANDBOX_DIR__/out/prefix_src_file_suffix'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/prefix_src_file_suffix",
			},
		},
		{
			name: "output_template_explicit_base_only",
			prop: `
				tools: ["host_tool"],
				srcs: ["txt/a/file.txt"],
				output: "$(in:base)",
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < txt/a/file.txt > __SBOX_SANDBOX_DIR__/out/file'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/file",
			},
		},
		{
			name: "output_template_explicit_base_and_ext",
			prop: `
				tools: ["host_tool"],
				srcs: ["txt/a/file.txt"],
				output: "$(in:base.ext)",
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < txt/a/file.txt > __SBOX_SANDBOX_DIR__/out/file.txt'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/file.txt",
			},
		},
		{
			name: "output_template_explicit_path_and_base",
			prop: `
				tools: ["host_tool"],
				srcs: ["txt/a/file.txt"],
				output: "$(in:path/base)",
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < txt/a/file.txt > __SBOX_SANDBOX_DIR__/out/txt/a/file'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/txt/a/file",
			},
		},
		{
			name: "output_template_explicit_path_and_base_and_ext",
			prop: `
				tools: ["host_tool"],
				srcs: ["txt/a/file.txt"],
				output: "$(in:path/base.ext)",
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < txt/a/file.txt > __SBOX_SANDBOX_DIR__/out/txt/a/file.txt'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/txt/a/file.txt",
			},
		},
		{
			name: "single_source_file_does_not_need_distinct_outputs",
			prop: `
				tools: ["host_tool"],
				srcs: ["src_file"],
				output: "output",
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < src_file > __SBOX_SANDBOX_DIR__/out/output'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/output",
			},
		},
		{
			name: "legacy_prefix_suffix",
			prop: `
				tools: ["host_tool"],
				srcs: ["src_file"],
				prefix: "legacy_prefix_",
				suffix: "_legacy_suffix",
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			cmds: []string{
				"bash -c '__SBOX_SANDBOX_DIR__/tools/src/out/host_tool gen < src_file > __SBOX_SANDBOX_DIR__/out/legacy_prefix_src_file_legacy_suffix'",
			},
			files: []string{
				"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/legacy_prefix_src_file_legacy_suffix",
			},
		},
		{
			name: "error_if_no_sources",
			prop: `
				tools: ["host_tool"],
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			err: "must have at least one source file",
		},
		{
			name: "error_if_no_filegroup_sources",
			prop: `
				tools: ["host_tool"],
				srcs: [":empty_filegroup"],
				cmd: "$(location host_tool) gen < $(in) > $(out)",
			`,
			err: "must have at least one source file",
		},
		{
			name: "error_if_in_outputs_are_not_distinct",
			prop: `
				tools: ["host_tool"],
				tool_files: ["tool_src_file"],
				srcs: ["src_file_1", "src_file_2"],
				output: "not_unique",
				cmd: "$(location)"
			`,
			err: "Android.bp:39:2: module \"codegen\": generation conflict: both 'src_file_1' and 'src_file_2' generate 'not_unique'",
		},
		{
			name: "error_if_output_expansion_fails",
			prop: `
				tools: ["host_tool"],
				tool_files: ["tool_src_file"],
				srcs: ["src_file"],
				output: "prefix_$(bad)_suffix",
				cmd: "$(location)"
			`,
			err: "Android.bp:45:11: module \"codegen\": output: unknown variable '$(bad)'",
		},
		{
			name: "error_if_cmd_expansion_fails",
			prop: `
				tools: ["host_tool"],
				tool_files: ["tool_src_file"],
				srcs: ["src_file"],
				output: "prefix_$(in)_suffix",
				cmd: "$(location bad_name)"
			`,
			err: "Android.bp:46:8: module \"codegen\": cmd: unknown location label \"bad_name\"",
		},
	}

	for _, test := range testcases {
		t.Run(test.name, func(t *testing.T) {
			bp := "wayland_protocol_codegen {\n"
			bp += `name: "codegen",` + "\n"
			bp += test.prop
			bp += "}\n"

			var expectedErrors []string
			if test.err != "" {
				expectedErrors = append(expectedErrors, regexp.QuoteMeta(test.err))
			}

			result := prepareForCodeGenTest.
				ExtendWithErrorHandler(android.FixtureExpectsAllErrorsToMatchAPattern(expectedErrors)).
				RunTestWithBp(t, testCodeGenBp()+bp)

			if expectedErrors != nil {
				return
			}

			gen := result.Module("codegen", "").(*Module)
			android.AssertDeepEquals(t, "cmd", test.cmds, gen.rawCommands)
			android.AssertPathsRelativeToTopEquals(t, "files", test.files, gen.outputFiles)
		})
	}
}

func TestDefaults(t *testing.T) {
	bp := `
		wayland_protocol_codegen_defaults {
			name: "gen_defaults1",
			cmd: "cp $(in) $(out)",
			output: "$(in).h",
		}

		wayland_protocol_codegen_defaults {
			name: "gen_defaults2",
			srcs: ["in1"],
		}

		wayland_protocol_codegen {
			name: "codegen",
			defaults: ["gen_defaults1", "gen_defaults2"],
		}
		`

	result := prepareForCodeGenTest.RunTestWithBp(t, testCodeGenBp()+bp)

	gen := result.Module("codegen", "").(*Module)

	expectedCmd := "bash -c cp in1 __SBOX_SANDBOX_DIR__/out/in1.h"
	android.AssertStringEquals(t, "cmd", expectedCmd, gen.rawCommands[0])

	expectedSrcs := []string{"in1"}
	android.AssertDeepEquals(t, "srcs", expectedSrcs, gen.properties.Srcs)

	expectedFiles := []string{"out/soong/.intermediates/codegen/gen/wayland_protocol_codegen/in1.h"}
	android.AssertPathsRelativeToTopEquals(t, "files", expectedFiles, gen.outputFiles)
}

type fakeAndroidHostTool struct {
	android.ModuleBase
	outputFile android.Path
}

func fakeAndroidHostToolFactory() android.Module {
	module := &fakeAndroidHostTool{}
	android.InitAndroidArchModule(module, android.HostSupported, android.MultilibFirst)
	return module
}

func (t *fakeAndroidHostTool) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	t.outputFile = android.PathForTesting("out", ctx.ModuleName())
}

func (t *fakeAndroidHostTool) HostToolPath() android.OptionalPath {
	return android.OptionalPathForPath(t.outputFile)
}

var _ android.HostToolProvider = (*fakeAndroidHostTool)(nil)
