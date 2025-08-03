// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package client

import (
	"io/ioutil"
	"os"
	"os/exec"
	"path"
	"testing"

	"android.googlesource.com/platform/tools/treble.git/hacksaw/bind"
	"android.googlesource.com/platform/tools/treble.git/hacksaw/codebase"
	"android.googlesource.com/platform/tools/treble.git/hacksaw/config"
)

func TestHelpCommand(t *testing.T) {
	cmd := NewCommand(bind.NewFakePathBinder(), "")
	args := []string{"hacksaw", "help"}
	err := cmd.Handle(args)
	if err != nil {
		t.Error(err)
	}
}

func TestNoCommand(t *testing.T) {
	cmd := NewCommand(bind.NewFakePathBinder(), "")
	args := []string{"hacksaw"}
	err := cmd.Handle(args)
	if err != nil {
		t.Error(err)
	}
}

func TestBadCommand(t *testing.T) {
	cmd := NewCommand(bind.NewFakePathBinder(), "")
	args := []string{"hacksaw", "doesnotexist"}
	if err := cmd.Handle(args); err == nil {
		t.Errorf("Failed to handle bad command")
	}
}

func TestEmptyCodebaseCommand(t *testing.T) {
	cmd := NewCommand(bind.NewFakePathBinder(), "")
	args := []string{"hacksaw", "codebase"}
	if err := cmd.Handle(args); err == nil {
		t.Errorf("Allowed an empty codebase command")
	}
}

func TestAddCodebase(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "test")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	cmd := NewCommand(bind.NewFakePathBinder(), "")
	args := []string{"hacksaw", "codebase", "add", "test-codebase", codebaseDir}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
}

func TestDefaultCodebase(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "test")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	gitDir := path.Join(codebaseDir, "project", ".git")
	if err = os.MkdirAll(gitDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	repoDir := path.Join(codebaseDir, ".repo")
	if err = os.Mkdir(repoDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	listContents := []byte("project")
	listPath := path.Join(repoDir, "project.list")
	if err = ioutil.WriteFile(listPath, listContents, os.ModePerm); err != nil {
		t.Error(err)
	}
	cmd := NewCommand(bind.NewFakePathBinder(), "")
	args := []string{"hacksaw", "codebase", "add", "test-codebase", codebaseDir}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
	args = []string{"hacksaw", "codebase", "default", "test-codebase"}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}

	wsTempDir, err := ioutil.TempDir("", "workspace")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(wsTempDir)
	wsTopDir := path.Join(wsTempDir, "hacksaw")
	if err = os.Mkdir(wsTopDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	cmd = NewCommand(bind.NewFakePathBinder(), wsTopDir)
	args = []string{"hacksaw", "workspace", "new", "test-workspace"}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
}

func TestListCodebases(t *testing.T) {
	cmd := NewCommand(bind.NewFakePathBinder(), "")
	args := []string{"hacksaw", "codebase", "list"}
	if err := cmd.Handle(args); err != nil {
		t.Error(err)
	}
}

func TestRemoveCodebase(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "test")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	cmd := NewCommand(bind.NewFakePathBinder(), "")
	args := []string{"hacksaw", "codebase", "add", "test-codebase", codebaseDir}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
	args = []string{"hacksaw", "codebase", "remove", "test-codebase"}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
}

func TestEmptyWorkspaceCommand(t *testing.T) {
	cmd := NewCommand(bind.NewFakePathBinder(), "")
	args := []string{"hacksaw", "workspace"}
	if err := cmd.Handle(args); err == nil {
		t.Errorf("Allowed an empty workspace command")
	}
}

func TestCreateWorkspace(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "test")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	gitDir := path.Join(codebaseDir, "project", ".git")
	if err = os.MkdirAll(gitDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	repoDir := path.Join(codebaseDir, ".repo")
	if err = os.Mkdir(repoDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	listContents := []byte("project")
	listPath := path.Join(repoDir, "project.list")
	if err = ioutil.WriteFile(listPath, listContents, os.ModePerm); err != nil {
		t.Error(err)
	}
	_, err = codebase.Add("test-codebase", codebaseDir)
	if err != nil {
		t.Error(err)
	}

	wsTempDir, err := ioutil.TempDir("", "workspace")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(wsTempDir)
	wsTopDir := path.Join(wsTempDir, "hacksaw")
	if err = os.Mkdir(wsTopDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	cmd := NewCommand(bind.NewFakePathBinder(), wsTopDir)
	args := []string{"hacksaw", "workspace", "new", "test-workspace", "test-codebase"}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
}

func TestRecreateWorkspace(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "test")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	gitDir := path.Join(codebaseDir, "project", ".git")
	if err = os.MkdirAll(gitDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	repoDir := path.Join(codebaseDir, ".repo")
	if err = os.Mkdir(repoDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	listContents := []byte("project")
	listPath := path.Join(repoDir, "project.list")
	if err = ioutil.WriteFile(listPath, listContents, os.ModePerm); err != nil {
		t.Error(err)
	}
	_, err = codebase.Add("test-codebase", codebaseDir)
	if err != nil {
		t.Error(err)
	}

	wsTempDir, err := ioutil.TempDir("", "workspace")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(wsTempDir)
	wsTopDir := path.Join(wsTempDir, "hacksaw")
	if err = os.Mkdir(wsTopDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	cmd := NewCommand(bind.NewFakePathBinder(), wsTopDir)
	args := []string{"hacksaw", "workspace", "new", "test-workspace", "test-codebase"}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
	args = []string{"hacksaw", "workspace", "recreate", "test-workspace"}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
	args = []string{"hacksaw", "workspace", "recreate", "does-not-exist"}
	if err = cmd.Handle(args); err == nil {
		t.Error("Allowed to recreate an unexistant workspace")
	}
}

func TestListWorkspace(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "test")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	gitDir := path.Join(codebaseDir, "project", ".git")
	if err = os.MkdirAll(gitDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	repoDir := path.Join(codebaseDir, ".repo")
	if err = os.Mkdir(repoDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	listContents := []byte("project")
	listPath := path.Join(repoDir, "project.list")
	if err = ioutil.WriteFile(listPath, listContents, os.ModePerm); err != nil {
		t.Error(err)
	}
	_, err = codebase.Add("test-codebase", codebaseDir)
	if err != nil {
		t.Error(err)
	}
	wsTempDir, err := ioutil.TempDir("", "workspace")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(wsTempDir)
	wsTopDir := path.Join(wsTempDir, "hacksaw")
	if err = os.Mkdir(wsTopDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	cmd := NewCommand(bind.NewFakePathBinder(), wsTopDir)

	args := []string{"hacksaw", "workspace", "new", "test-workspace", "test-codebase"}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}

	args = []string{"hacksaw", "workspace", "list"}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
}

func TestRemoveWorkspace(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "test")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	gitDir := path.Join(codebaseDir, "project", ".git")
	if err = os.MkdirAll(gitDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	repoDir := path.Join(codebaseDir, ".repo")
	if err = os.Mkdir(repoDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	listContents := []byte("project")
	listPath := path.Join(repoDir, "project.list")
	if err = ioutil.WriteFile(listPath, listContents, os.ModePerm); err != nil {
		t.Error(err)
	}
	_, err = codebase.Add("test-codebase", codebaseDir)
	if err != nil {
		t.Error(err)
	}
	wsTempDir, err := ioutil.TempDir("", "workspace")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(wsTempDir)
	wsTopDir := path.Join(wsTempDir, "hacksaw")
	if err = os.Mkdir(wsTopDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	cmd := NewCommand(bind.NewFakePathBinder(), wsTopDir)

	args := []string{"hacksaw", "workspace", "new", "test-workspace", "test-codebase"}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}

	args = []string{"hacksaw", "workspace", "remove", "test-workspace"}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
}

func TestEditWorkspace(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "codebase")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	projectDir := path.Join(codebaseDir, "project")
	if err = os.MkdirAll(projectDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	gitCmd := exec.Command("git", "-C", projectDir, "init")
	output, err := gitCmd.CombinedOutput()
	if err != nil {
		t.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
			gitCmd.String(), err.Error(), output)
	}
	gitCmd = exec.Command("git", "-C", projectDir, "commit", `--message="Initial commit"`, "--allow-empty")
	output, err = gitCmd.CombinedOutput()
	if err != nil {
		t.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
			gitCmd.String(), err.Error(), output)
	}
	repoDir := path.Join(codebaseDir, ".repo")
	if err = os.Mkdir(repoDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	listContents := []byte("project")
	listPath := path.Join(repoDir, "project.list")
	if err = ioutil.WriteFile(listPath, listContents, os.ModePerm); err != nil {
		t.Error(err)
	}
	_, err = codebase.Add("test-codebase", codebaseDir)
	if err != nil {
		t.Error(err)
	}
	wsTempDir, err := ioutil.TempDir("", "workspace")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(wsTempDir)
	wsTopDir := path.Join(wsTempDir, "hacksaw")
	if err = os.Mkdir(wsTopDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	cmd := NewCommand(bind.NewFakePathBinder(), wsTopDir)

	args := []string{"hacksaw", "workspace", "new", "test-workspace", "test-codebase"}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
	wsProjectDir := path.Join(wsTopDir, "test-workspace", "project")
	args = []string{"hacksaw", "edit", wsProjectDir}
	if err = cmd.Handle(args); err != nil {
		t.Error(err)
	}
}
