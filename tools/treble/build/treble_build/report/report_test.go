// Copyright 2022 The Android Open Source Project
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

package report

import (
	"context"
	"errors"
	"fmt"
	"reflect"
	"strconv"
	"testing"

	"tools/treble/build/report/app"
)

type reportTest struct {
	manifest   *app.RepoManifest
	commands   map[string]*app.BuildCommand
	inputs     map[string]*app.BuildInput
	queries    map[string]*app.BuildQuery
	paths      map[string]map[string]*app.BuildPath
	multipaths map[string]map[string][]*app.BuildPath
	projects   map[string]*app.GitProject
	commits    map[*app.GitProject]map[string]*app.GitCommit

	deps           *app.BuildDeps
	projectCommits map[string]int
}

func (r *reportTest) Manifest(filename string) (*app.RepoManifest, error) {
	var err error
	out := r.manifest
	if out == nil {
		err = errors.New(fmt.Sprintf("No manifest named %s", filename))
	}
	return r.manifest, err
}
func (r *reportTest) Command(ctx context.Context, target string) (*app.BuildCommand, error) {
	var err error
	out := r.commands[target]
	if out == nil {
		err = errors.New(fmt.Sprintf("No command for target %s", target))
	}
	return out, err
}

func (r *reportTest) Input(ctx context.Context, target string) (*app.BuildInput, error) {
	var err error
	out := r.inputs[target]
	if out == nil {
		err = errors.New(fmt.Sprintf("No inputs for target %s", target))
	}
	return out, err
}

func (r *reportTest) Query(ctx context.Context, target string) (*app.BuildQuery, error) {
	var err error
	out := r.queries[target]
	if out == nil {
		err = errors.New(fmt.Sprintf("No queries for target %s", target))
	}
	return out, err
}

func (r *reportTest) Path(ctx context.Context, target string, dependency string) (*app.BuildPath, error) {
	return r.paths[target][dependency], nil
}

func (r *reportTest) Paths(ctx context.Context, target string, dependency string) ([]*app.BuildPath, error) {
	return r.multipaths[target][dependency], nil
}

func (r *reportTest) Deps(ctx context.Context) (*app.BuildDeps, error) {
	return r.deps, nil
}
func (r *reportTest) Project(ctx context.Context, path string, gitDir string, remote string, revision string) (*app.GitProject, error) {
	var err error
	out := r.projects[path]
	if out == nil {
		err = errors.New(fmt.Sprintf("No projects for target %s", path))
	}
	return out, err
}
func (r *reportTest) PopulateFiles(ctx context.Context, proj *app.GitProject, upstream string) error {
	return nil
}
func (r *reportTest) CommitInfo(ctx context.Context, proj *app.GitProject, sha string) (*app.GitCommit, error) {
	var err error
	out := r.commits[proj][sha]
	if out == nil {
		err = errors.New(fmt.Sprintf("No commit for sha %s", sha))
	}
	return out, err
}

// Helper routine used in test function to create array of unique names
func createStrings(name string, count int) []string {
	var out []string
	for i := 0; i < count; i++ {
		out = append(out, name+strconv.Itoa(i))
	}
	return out
}

// Project names used in tests
func projName(i int) string {
	return "proj." + strconv.Itoa(i)
}

func fileName(i int) (filename string, sha string) {
	iString := strconv.Itoa(i)
	return "source." + iString, "sha." + iString
}
func createFile(i int) *app.GitTreeObj {
	fname, sha := fileName(i)
	return &app.GitTreeObj{Permissions: "100644", Type: "blob", Filename: fname, Sha: sha}
}
func createProject(name string) *app.GitProject {
	return &app.GitProject{
		RepoDir: name, WorkDir: name, GitDir: ".git", Remote: "origin",
		RemoteUrl: "origin_url", Revision: name + "_sha",
		Files: make(map[string]*app.GitTreeObj)}

}

// Create basic test data for given inputs
func createTest(projCount int, fileCount int) *reportTest {
	test := &reportTest{
		manifest: &app.RepoManifest{
			Remotes:  []app.RepoRemote{{Name: "remote1", Revision: "revision_1"}},
			Default:  app.RepoDefault{Remote: "remote1", Revision: "revision_2"},
			Projects: []app.RepoProject{},
		},
		commands: map[string]*app.BuildCommand{},
		inputs:   map[string]*app.BuildInput{},
		queries:  map[string]*app.BuildQuery{},
		projects: map[string]*app.GitProject{},
		commits:  map[*app.GitProject]map[string]*app.GitCommit{},
	}

	// Create projects with files
	for i := 0; i <= projCount; i++ {
		name := projName(i)

		proj := createProject(name)

		for i := 0; i <= fileCount; i++ {
			treeObj := createFile(i)
			proj.Files[treeObj.Filename] = treeObj

		}
		test.projects[name] = proj
		test.manifest.Projects = append(test.manifest.Projects,
			app.RepoProject{Groups: "group", Name: name, Revision: "sha", Path: name})

	}
	return test
}

func Test_report(t *testing.T) {

	test := createTest(10, 20)

	// Test cases will specify input file by project and file index
	type inputFile struct {
		proj int
		file int
	}

	targetDefs := []struct {
		name          string      // Target name
		cmds          int         // Number of build steps
		inputTargets  int         // Number of input targets
		outputTargets int         // Number of output targets
		inputFiles    []inputFile // Input files for target
	}{
		{
			name:          "target",
			cmds:          7,
			inputTargets:  4,
			outputTargets: 7,
			inputFiles:    []inputFile{{proj: 0, file: 1}, {proj: 1, file: 0}},
		},
		{
			name:          "target2",
			cmds:          0,
			inputTargets:  0,
			outputTargets: 0,
			inputFiles:    []inputFile{{proj: 0, file: 1}, {proj: 0, file: 2}, {proj: 1, file: 0}},
		},
		{
			name:          "null_target",
			cmds:          0,
			inputTargets:  0,
			outputTargets: 0,
			inputFiles:    []inputFile{},
		},
	}

	// Create target data based on definitions
	var targets []string

	// Build expected output while creating the targets
	resTargets := make(map[string]*app.BuildTarget)

	for _, target := range targetDefs {

		res := &app.BuildTarget{Name: target.name,
			Steps:     target.cmds,
			FileCount: len(target.inputFiles),
			Projects:  make(map[string]*app.GitProject),
		}

		// Add files to the build target
		var inputFiles []string
		for _, in := range target.inputFiles {
			// Get project by name
			pName := projName(in.proj)
			bf := createFile(in.file)
			p := test.projects[pName]

			inputFiles = append(inputFiles,
				fmt.Sprintf("%s/%s", p.WorkDir, bf.Filename))

			if _, exists := res.Projects[pName]; !exists {
				res.Projects[pName] = createProject(pName)
			}
			res.Projects[pName].Files[bf.Filename] = bf
		}

		// Create test data
		test.commands[target.name] = &app.BuildCommand{Target: target.name, Cmds: createStrings("cmd.", target.cmds)}
		test.inputs[target.name] = &app.BuildInput{Target: target.name, Files: inputFiles}
		test.queries[target.name] = &app.BuildQuery{
			Target:  target.name,
			Inputs:  createStrings("target.in.", target.inputTargets),
			Outputs: createStrings("target.out.", target.outputTargets)}

		targets = append(targets, target.name)
		resTargets[res.Name] = res
	}

	rtx := &Context{RepoBase: "/src", Repo: test, Build: test, Project: test, WorkerCount: 1, BuildWorkerCount: 1}
	rtx.ResolveProjectMap(nil, "test_file", "")
	req := &app.ReportRequest{Targets: targets}
	rsp, err := RunReport(nil, rtx, req)
	if err != nil {
		t.Errorf("Failed to run report for request %+v", req)
	} else {
		if !reflect.DeepEqual(rsp.Targets, resTargets) {
			t.Errorf("Got targets %+v, expected %+v", rsp.Targets, resTargets)
		}
	}
}
