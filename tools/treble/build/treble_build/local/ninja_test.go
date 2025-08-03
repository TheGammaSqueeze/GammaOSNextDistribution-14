// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package local

import (
	"bytes"
	"context"
	"reflect"
	"testing"

	"tools/treble/build/report/app"
)

type ninjaTest struct {
	command *TestCmd
	input   *TestCmd
	query   *TestCmd
	path    *TestCmd
	paths   *TestCmd
	deps    *TestCmd
	build   *TestCmd
}

func (n *ninjaTest) Command(ctx context.Context, target string) (*bytes.Buffer, error) {
	return bytes.NewBufferString(n.command.text), n.command.err
}
func (n *ninjaTest) Input(ctx context.Context, target string) (*bytes.Buffer, error) {
	return bytes.NewBufferString(n.input.text), n.input.err
}
func (n *ninjaTest) Query(ctx context.Context, target string) (*bytes.Buffer, error) {
	return bytes.NewBufferString(n.query.text), n.query.err
}
func (n *ninjaTest) Path(ctx context.Context, target string, dependency string) (*bytes.Buffer, error) {
	return bytes.NewBufferString(n.path.text), n.path.err
}
func (n *ninjaTest) Paths(ctx context.Context, target string, dependency string) (*bytes.Buffer, error) {
	return bytes.NewBufferString(n.paths.text), n.paths.err
}
func (n *ninjaTest) Deps(ctx context.Context) (*bytes.Buffer, error) {
	return bytes.NewBufferString(n.deps.text), n.deps.err
}
func (n *ninjaTest) Build(ctx context.Context, target string) (*bytes.Buffer, error) {
	return bytes.NewBufferString(n.build.text), n.build.err
}

func Test_ninja(t *testing.T) {
	type commandTest struct {
		cmd *TestCmd
		res *app.BuildCommand
	}
	type queryTest struct {
		cmd *TestCmd
		res *app.BuildQuery
	}
	type inputTest struct {
		cmd *TestCmd
		res *app.BuildInput
	}
	type pathTest struct {
		cmd *TestCmd
		res *app.BuildPath
	}
	type pathsTest struct {
		cmd *TestCmd
		res []*app.BuildPath
	}
	type depsTest struct {
		cmd *TestCmd
		res *app.BuildDeps
	}
	type buildTest struct {
		cmd *TestCmd
		res *app.BuildCmdResult
	}
	tests := []struct {
		target     string
		dependency string
		command    commandTest
		query      queryTest
		input      inputTest
		path       pathTest
		paths      pathsTest
		deps       depsTest
		build      buildTest
	}{
		{
			target:     "test",
			dependency: "dependency",
			command: commandTest{
				cmd: &TestCmd{text: "  cmd1\ncmd2\n cmd3\n", err: nil},
				res: &app.BuildCommand{Target: "test", Cmds: []string{"cmd1", "cmd2", "cmd3"}}},
			query: queryTest{
				cmd: &TestCmd{text: "input:\ninfile\noutputs:\noutfile\n", err: nil},
				res: &app.BuildQuery{Target: "test", Inputs: []string{"infile"}, Outputs: []string{"outfile"}}},
			input: inputTest{
				cmd: &TestCmd{text: "file1\nfile2\nfile3\nfile4\nfile5\n", err: nil},
				res: &app.BuildInput{Target: "test", Files: []string{"file1", "file2", "file3", "file4", "file5"}},
			},
			path: pathTest{
				cmd: &TestCmd{text: "test\nmid1\nmid2\nmid3\ndependency\n", err: nil},
				res: &app.BuildPath{Target: "test", Dependency: "dependency",
					Paths: []string{"test", "mid1", "mid2", "mid3", "dependency"}},
			},
			paths: pathsTest{
				cmd: &TestCmd{text: "test mid1 mid2 mid3 dependency\ntest mid4 dependency\n", err: nil},
				res: []*app.BuildPath{
					&app.BuildPath{Target: "test", Dependency: "dependency", Paths: []string{"test", "mid1", "mid2", "mid3", "dependency"}},
					&app.BuildPath{Target: "test", Dependency: "dependency", Paths: []string{"test", "mid4", "dependency"}},
				},
			},
			deps: depsTest{
				cmd: &TestCmd{text: "some/build/library.so: #deps1\n    dependentFile1.S\n    dependentFile2.S\nsome/build/library2.so: #deps1\n    dependentFile1.S\n    dependentFile3.S\n"},
				res: &app.BuildDeps{Targets: map[string][]string{
					"some/build/library.so":  []string{"dependentFile1.S", "dependentFile2.S"},
					"some/build/library2.so": []string{"dependentFile1.S", "dependentFile3.S"},
				},
				},
			},
			build: buildTest{
				cmd: &TestCmd{text: "", err: nil},
				res: &app.BuildCmdResult{Name: "test", Output: []string{}, Success: true}},
		},
	}
	for _, test := range tests {

		exec := &ninjaTest{
			command: test.command.cmd,
			query:   test.query.cmd,
			input:   test.input.cmd,
			path:    test.path.cmd,
			paths:   test.paths.cmd,
			deps:    test.deps.cmd,
			build:   test.build.cmd,
		}
		n := &ninjaCli{n: exec}

		if test.command.cmd != nil {
			if res, err := n.Command(nil, test.target); err != nil {
				t.Errorf("Command error %s", err)
			} else {
				if !reflect.DeepEqual(*res, *test.command.res) {
					t.Errorf("Command result %v; want %v", *res, *test.command.res)
				}
			}
		}
		if test.query.cmd != nil {
			if res, err := n.Query(nil, test.target); err != nil {
				t.Errorf("Query error %s", err)
			} else {
				if !reflect.DeepEqual(*res, *test.query.res) {
					t.Errorf("Query result %v; want %v", *res, *test.query.res)
				}
			}

		}
		if test.input.cmd != nil {
			if res, err := n.Input(nil, test.target); err != nil {
				t.Errorf("Input error %s", err)
			} else {
				if !reflect.DeepEqual(*res, *test.input.res) {
					t.Errorf("Input result %v; want %v", *res, *test.input.res)
				}
			}

		}
		if test.path.cmd != nil {
			if res, err := n.Path(nil, test.target, test.dependency); err != nil {
				t.Errorf("Path error %s", err)
			} else {
				if !reflect.DeepEqual(*res, *test.path.res) {
					t.Errorf("Path result %v; want %v", *res, *test.path.res)
				}
			}

		}
		if test.paths.cmd != nil {
			if res, err := n.Paths(nil, test.target, test.dependency); err != nil {
				t.Errorf("Paths error %s", err)
			} else {
				if !reflect.DeepEqual(res, test.paths.res) {
					t.Errorf("Paths result %v; want %v", res, test.paths.res)
				}
			}

		}
		if test.deps.cmd != nil {
			if res, err := n.Deps(nil); err != nil {
				t.Errorf("Deps error %s", err)
			} else {
				if !reflect.DeepEqual(res, test.deps.res) {
					t.Errorf("Deps result %v; want %v", res, test.deps.res)
				}
			}

		}
		if test.build.cmd != nil {
			res := n.Build(nil, test.target)
			if !reflect.DeepEqual(*res, *test.build.res) {
				t.Errorf("Build result %+v; want %+v", *res, *test.build.res)
			}

		}
	}
}
