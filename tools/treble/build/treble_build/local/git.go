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

package local

//
// Command line implementation of Git interface
//

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"

	"tools/treble/build/report/app"
)

// Separate out the executable to allow tests to override the results
type gitExec interface {
	ProjectInfo(ctx context.Context, gitDir, workDir string) (out *bytes.Buffer, err error)
	RemoteUrl(ctx context.Context, gitDir, workDir, remote string) (*bytes.Buffer, error)
	Tree(ctx context.Context, gitDir, workDir, revision string) (*bytes.Buffer, error)
	CommitInfo(ctx context.Context, gitDir, workDir, revision string) (*bytes.Buffer, error)
	DiffBranches(ctx context.Context, gitDir, workDir, upstream, sha string) (*bytes.Buffer, error)
}

type gitCli struct {
	git gitExec // Git executable
}

// Create GIT project based on input parameters
func (cli gitCli) Project(ctx context.Context, path, gitDir, remote, revision string) (*app.GitProject, error) {
	workDir := path
	// Set defaults
	if remote == "" {
		remote = "origin"
	}
	if gitDir == "" {
		gitDir = ".git"
	}

	if raw, err := cli.git.ProjectInfo(ctx, gitDir, workDir); err == nil {
		topLevel, projRevision, err := parseProjectInfo(raw)
		if err == nil {
			// Update work dir to use absolute path
			workDir = topLevel
			if revision == "" {
				revision = projRevision
			}
		}
	}
	// Create project to use to run commands
	out := &app.GitProject{
		RepoDir:  path,
		WorkDir:  workDir,
		GitDir:   gitDir,
		Remote:   remote,
		Revision: revision,
		Files:    make(map[string]*app.GitTreeObj)}

	// Remote URL
	if raw, err := cli.git.RemoteUrl(ctx, gitDir, workDir, remote); err == nil {
		url, err := parseRemoteUrl(raw)
		if err == nil {
			out.RemoteUrl = url
		}
	}

	return out, nil
}

// Get all files in the repository if, upstream branch is provided mark which files differ from upstream
func (cli gitCli) PopulateFiles(ctx context.Context, proj *app.GitProject, upstream string) error {
	if raw, err := cli.git.Tree(ctx, proj.GitDir, proj.WorkDir, proj.Revision); err == nil {
		lsFiles, err := parseLsTree(raw)
		if err == nil {
			for _, file := range lsFiles {
				proj.Files[file.Filename] = file
			}
		}
		if upstream != "" {

			if diff, err := cli.git.DiffBranches(ctx, proj.GitDir, proj.WorkDir, upstream, proj.Revision); err == nil {
				if diffFiles, err := parseBranchDiff(diff); err == nil {
					for f, d := range diffFiles {
						if file, exists := proj.Files[f]; exists {
							file.BranchDiff = d
						}
					}
				}
			}

		}
	}
	return nil
}

// Get the commit information associated with the input sha
func (cli gitCli) CommitInfo(ctx context.Context, proj *app.GitProject, sha string) (*app.GitCommit, error) {
	if sha == "" {
		sha = "HEAD"
	}
	raw, err := cli.git.CommitInfo(ctx, proj.GitDir, proj.WorkDir, sha)

	if err != nil {
		return nil, err
	}
	return parseCommitInfo(raw)
}

// parse rev-parse
func parseProjectInfo(data *bytes.Buffer) (topLevel string, revision string, err error) {
	s := bufio.NewScanner(data)
	scanner := newLineScanner(2)
	if err = scanner.Parse(s); err != nil {
		return "", "", err
	}
	return scanner.Lines[0], scanner.Lines[1], nil

}

// parse remote get-url
func parseRemoteUrl(data *bytes.Buffer) (url string, err error) {
	s := bufio.NewScanner(data)
	scanner := newLineScanner(1)
	if err = scanner.Parse(s); err != nil {
		return "", err
	}
	return scanner.Lines[0], nil

}

// parse ls-tree
func parseLsTree(data *bytes.Buffer) ([]*app.GitTreeObj, error) {
	out := []*app.GitTreeObj{}
	s := bufio.NewScanner(data)
	for s.Scan() {
		obj := &app.GitTreeObj{}
		// TODO
		// Filename could contain a <space> as quotepath is turned off, truncating the name here
		fmt.Sscanf(s.Text(), "%s %s %s %s", &obj.Permissions, &obj.Type, &obj.Sha, &obj.Filename)
		out = append(out, obj)
	}
	return out, nil
}

// parse branch diff (diff --num-stat)
func parseBranchDiff(data *bytes.Buffer) (map[string]*app.GitDiff, error) {
	out := make(map[string]*app.GitDiff)
	s := bufio.NewScanner(data)
	for s.Scan() {
		d := &app.GitDiff{}
		var fname, added, deleted string
		_, err := fmt.Sscanf(s.Text(), "%s %s %s", &added, &deleted, &fname)
		if err == nil {
			if added == "-" || deleted == "-" {
				d.BinaryDiff = true
			} else {
				d.AddedLines, _ = strconv.Atoi(added)
				d.DeletedLines, _ = strconv.Atoi(deleted)
			}
		}
		out[fname] = d
	}
	return out, nil
}

// parse commit diff-tree
func parseCommitInfo(data *bytes.Buffer) (*app.GitCommit, error) {
	out := &app.GitCommit{Files: []app.GitCommitFile{}}
	s := bufio.NewScanner(data)
	first := true
	for s.Scan() {
		if first {
			out.Sha = s.Text()
		} else {
			file := app.GitCommitFile{}
			t := ""
			fmt.Sscanf(s.Text(), "%s %s", &t, &file.Filename)
			switch t {
			case "M":
				file.Type = app.GitFileModified
			case "A":
				file.Type = app.GitFileAdded
			case "R":
				file.Type = app.GitFileRemoved
			}
			out.Files = append(out.Files, file)
		}
		first = false
	}
	return out, nil
}

// Command line git
type gitCmd struct {
	cmd     string        // GIT executable
	timeout time.Duration // Timeout for commands
}

// Run git command in working directory
func (git *gitCmd) runDirCmd(ctx context.Context, gitDir string, workDir string, args []string) (*bytes.Buffer, error) {
	gitArgs := append([]string{"--git-dir", gitDir, "-C", workDir}, args...)
	out, err, _ := run(ctx, git.timeout, git.cmd, gitArgs)
	if err != nil {
		return nil, errors.New(fmt.Sprintf("Failed to run %s %s [error %s]", git.cmd, strings.Join(gitArgs, " ")))
	}
	return out, nil
}

func (git *gitCmd) ProjectInfo(ctx context.Context, gitDir, workDir string) (*bytes.Buffer, error) {
	return git.runDirCmd(ctx, gitDir, workDir, []string{"rev-parse", "--show-toplevel", "HEAD"})
}
func (git *gitCmd) RemoteUrl(ctx context.Context, gitDir, workDir, remote string) (*bytes.Buffer, error) {
	return git.runDirCmd(ctx, gitDir, workDir, []string{"remote", "get-url", remote})
}
func (git *gitCmd) Tree(ctx context.Context, gitDir, workDir, revision string) (*bytes.Buffer, error) {
	cmdArgs := []string{"-c", "core.quotepath=off", "ls-tree", "--full-name", revision, "-r", "-t"}
	return git.runDirCmd(ctx, gitDir, workDir, cmdArgs)
}
func (git *gitCmd) CommitInfo(ctx context.Context, gitDir, workDir, sha string) (*bytes.Buffer, error) {
	cmdArgs := []string{"diff-tree", "-r", "-m", "--name-status", "--root", sha}
	return git.runDirCmd(ctx, gitDir, workDir, cmdArgs)
}
func (git *gitCmd) DiffBranches(ctx context.Context, gitDir, workDir, upstream, sha string) (*bytes.Buffer, error) {
	cmdArgs := []string{"diff", "--numstat", fmt.Sprintf("%s...%s", upstream, sha)}
	return git.runDirCmd(ctx, gitDir, workDir, cmdArgs)
}
func NewGitCli() *gitCli {
	cli := &gitCli{git: &gitCmd{cmd: "git", timeout: 100000 * time.Millisecond}}
	return cli
}
