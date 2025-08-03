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

// Test cases for local GIT.
type TestCmd struct {
	err  error
	text string
}
type gitTestCli struct {
	revParse   *TestCmd
	remoteUrl  *TestCmd
	tree       *TestCmd
	commit     *TestCmd
	diffBranch *TestCmd
}

func (g *gitTestCli) ProjectInfo(ctx context.Context, gitDir, workDir string) (*bytes.Buffer, error) {
	return bytes.NewBufferString(g.revParse.text), g.revParse.err
}
func (g *gitTestCli) RemoteUrl(ctx context.Context, gitDir, workDir, remote string) (*bytes.Buffer, error) {
	return bytes.NewBufferString(g.remoteUrl.text), g.remoteUrl.err
}
func (g *gitTestCli) Tree(ctx context.Context, gitDir, workDir, revision string) (*bytes.Buffer, error) {
	return bytes.NewBufferString(g.tree.text), g.tree.err
}
func (g *gitTestCli) CommitInfo(ctx context.Context, gitDir, workDir, sha string) (*bytes.Buffer, error) {
	return bytes.NewBufferString(g.commit.text), g.tree.err
}
func (g *gitTestCli) DiffBranches(ctx context.Context, gitDir, workDir, upstream, sha string) (*bytes.Buffer, error) {
	return bytes.NewBufferString(g.diffBranch.text), g.tree.err
}

func Test_git(t *testing.T) {

	type projectTest struct {
		revCmd    *TestCmd
		remoteCmd *TestCmd
		treeCmd   *TestCmd
		res       *app.GitProject
	}

	type commitTest struct {
		sha string
		cmd *TestCmd
		res *app.GitCommit
	}

	tests := []struct {
		path     string
		gitDir   string
		remote   string
		revision string
		getFiles bool
		project  projectTest
		commit   commitTest
	}{
		{
			path:     "work/dir",
			gitDir:   "",
			remote:   "origin",
			revision: "sha_revision",
			getFiles: true,
			project: projectTest{
				revCmd:    &TestCmd{text: "/abs/path/to/work/dir\nsha_revision\n", err: nil},
				remoteCmd: &TestCmd{text: "http://url/workdir", err: nil},
				treeCmd:   &TestCmd{text: "", err: nil},
				res: &app.GitProject{
					RepoDir:   "work/dir",
					WorkDir:   "/abs/path/to/work/dir",
					GitDir:    ".git",
					Remote:    "origin",
					RemoteUrl: "http://url/workdir",
					Revision:  "sha_revision",
					Files:     make(map[string]*app.GitTreeObj)},
			},
			// Test empty commit
			commit: commitTest{
				sha: "commit_sha",
				cmd: &TestCmd{text: "commit_sha", err: nil},
				res: &app.GitCommit{Sha: "commit_sha", Files: []app.GitCommitFile{}},
			},
		},
		{
			path:     "work/dir",
			gitDir:   "",
			remote:   "origin",
			revision: "sha_revision",
			getFiles: true,
			project: projectTest{
				revCmd:    &TestCmd{text: "/abs/path/to/work/dir\nsha_revision\n", err: nil},
				remoteCmd: &TestCmd{text: "http://url/workdir", err: nil},
				treeCmd:   &TestCmd{text: "100644 blob 0000000000000000000000000000000000000001 file.1\n", err: nil},
				res: &app.GitProject{
					RepoDir:   "work/dir",
					WorkDir:   "/abs/path/to/work/dir",
					GitDir:    ".git",
					Remote:    "origin",
					RemoteUrl: "http://url/workdir",
					Revision:  "sha_revision",
					Files: map[string]*app.GitTreeObj{"file.1": &app.GitTreeObj{Permissions: "100644", Type: "blob",
						Sha: "0000000000000000000000000000000000000001", Filename: "file.1"}}},
			},
			commit: commitTest{
				sha: "HEAD",
				cmd: &TestCmd{text: "sha_for_head\nR removed.1\nA added.1\nM modified.1\n", err: nil},
				res: &app.GitCommit{
					Sha: "sha_for_head",
					Files: []app.GitCommitFile{
						{Filename: "removed.1", Type: app.GitFileRemoved},
						{Filename: "added.1", Type: app.GitFileAdded},
						{Filename: "modified.1", Type: app.GitFileModified},
					},
				},
			},
		},
	}
	for _, test := range tests {
		git := &gitCli{git: &gitTestCli{
			revParse:  test.project.revCmd,
			remoteUrl: test.project.remoteCmd,
			tree:      test.project.treeCmd,
			commit:    test.commit.cmd,
		}}

		proj, err := git.Project(nil, test.path, test.gitDir, test.remote, test.revision)
		if err != nil {
			t.Fatal("Failed to parse project")
		}
		if test.getFiles {
			_ = git.PopulateFiles(nil, proj, "")
		}
		if !reflect.DeepEqual(*proj, *test.project.res) {
			t.Errorf("Project = %+v; want %+v", *proj, *test.project.res)
		}
		if test.commit.cmd != nil {
			c, err := git.CommitInfo(nil, proj, test.commit.sha)
			if err != nil {
				t.Errorf("Failed to get; %v", test)
			} else {
				if !reflect.DeepEqual(*c, *test.commit.res) {
					t.Errorf("Commit = %v; want %v", c, *test.commit.res)
				}
			}
		}
	}

}
