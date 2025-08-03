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

package workspace

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

func TestBasicCreate(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "codebase")
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
	// The top dir must be named "hacksaw"
	// otherwise the mounters will reject any
	// mount requests
	wsTempDir, err := ioutil.TempDir("", "workspace")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(wsTempDir)
	wsTopDir := path.Join(wsTempDir, "hacksaw")
	if err = os.Mkdir(wsTopDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	ws := New(bind.NewFakePathBinder(), wsTopDir)
	if _, err = ws.Create("test-workspace", "test-codebase"); err != nil {
		t.Error(err)
	}
	workspaceDir, err := ws.GetDir("test-workspace")
	if err != nil {
		t.Error(err)
	}
	_, err = os.Stat(workspaceDir)
	if err != nil {
		t.Error(err)
	}
}

func TestWorkspaceDuplicate(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "codebase")
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
	// The top dir must be named "hacksaw"
	// otherwise the mounters will reject any
	// mount requests
	wsTempDir, err := ioutil.TempDir("", "workspace")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(wsTempDir)
	wsTopDir := path.Join(wsTempDir, "hacksaw")
	if err = os.Mkdir(wsTopDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	ws := New(bind.NewFakePathBinder(), wsTopDir)
	_, err = ws.Create("test-workspace", "test-codebase")
	if err != nil {
		t.Error(err)
	}
	_, err = ws.Create("test-workspace", "test-codebase")
	if err == nil {
		t.Error("Allowed workspace duplicate")
	}
}

func TestCreateWorkspaceFromBadCodebase(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "test")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
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
	ws := New(bind.NewFakePathBinder(), wsTopDir)
	if _, err = ws.Create("test-workspace", "does-not-exist"); err == nil {
		t.Error("Allowed bad codebase")
	}
}

func TestList(t *testing.T) {
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
	ws := New(bind.NewFakePathBinder(), wsTopDir)
	if _, err = ws.Create("test-workspace", "test-codebase"); err != nil {
		t.Error(err)
	}
	list := ws.List()
	cb, ok := list["test-workspace"]
	if !ok || cb != "test-codebase" {
		t.Error("Added workspace not listed")
	}
}

func TestRemove(t *testing.T) {
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
	ws := New(bind.NewFakePathBinder(), wsTopDir)
	if _, err = ws.Create("test-workspace", "test-codebase"); err != nil {
		t.Error(err)
	}
	workspaceDir, err := ws.GetDir("test-workspace")
	if err != nil {
		t.Error(err)
	}
	_, err = os.Stat(workspaceDir)
	if err != nil {
		t.Error(err)
	}
	cfg, err := ws.Remove("test-workspace")
	if err != nil {
		t.Error(err)
	}
	_, ok := cfg.Workspaces["test-codebase"]
	if ok {
		t.Error("Removed workspace test-codebase is still in the configuration")
	}
	_, err = os.Stat(workspaceDir)
	if err == nil {
		t.Error("Workspace test-workspace was removed but its directory remains")
	} else if os.IsNotExist(err) {
		// This is the expected error
	} else {
		t.Error(err)
	}
}

func TestEdit(t *testing.T) {
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
	cmd := exec.Command("git", "-C", projectDir, "init")
	output, err := cmd.CombinedOutput()
	if err != nil {
		t.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
			cmd.String(), err.Error(), output)
	}
	cmd = exec.Command("git", "-C", projectDir, "commit", `--message="Initial commit"`, "--allow-empty")
	output, err = cmd.CombinedOutput()
	if err != nil {
		t.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
			cmd.String(), err.Error(), output)
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
	ws := New(bind.NewFakePathBinder(), wsTopDir)
	if _, err = ws.Create("test-workspace", "test-codebase"); err != nil {
		t.Error(err)
	}
	workspaceDir, err := ws.GetDir("test-workspace")
	if err != nil {
		t.Error(err)
	}
	_, err = os.Stat(workspaceDir)
	if err != nil {
		t.Error(err)
	}
	editPath := path.Join(workspaceDir, "project")
	branchName, wsProjectDir, err := ws.Edit(editPath)
	if err != nil {
		t.Error(err)
	}
	if branchName == "" {
		t.Error("Editing returned an empty branch")
	}
	if wsProjectDir == "" {
		t.Error("Editing returned an empty project path")
	}
	cmd = exec.Command("git", "-C", wsProjectDir, "show", branchName)
	output, err = cmd.CombinedOutput()
	if err != nil {
		t.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
			cmd.String(), err.Error(), output)
	}
	//Recreate workspace and try editing again
	_, err = ws.Remove("test-workspace")
	if err != nil {
		t.Error(err)
	}
	_, err = ws.Create("test-workspace", "test-codebase")
	if err != nil {
		t.Error(err)
	}
	_, _, err = ws.Edit(editPath)
	if err != nil {
		t.Error(err)
	}
}

const projectList = `read-only-project
editable-project`

func TestRecreate(t *testing.T) {
	defer config.GetConfig().Reset()
	codebaseDir, err := ioutil.TempDir("", "codebase")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	roProjectDir := path.Join(codebaseDir, "read-only-project")
	if err = os.MkdirAll(roProjectDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	cmd := exec.Command("git", "-C", roProjectDir, "init")
	output, err := cmd.CombinedOutput()
	if err != nil {
		t.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
			cmd.String(), err.Error(), output)
	}
	cmd = exec.Command("git", "-C", roProjectDir, "commit", `--message="Initial commit"`, "--allow-empty")
	output, err = cmd.CombinedOutput()
	if err != nil {
		t.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
			cmd.String(), err.Error(), output)
	}
	linkPath := path.Join(codebaseDir, "symlink")
	if err = os.Symlink(roProjectDir, linkPath); err != nil {
		t.Error(err)
	}
	rwProjectDir := path.Join(codebaseDir, "editable-project")
	if err = os.MkdirAll(rwProjectDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	cmd = exec.Command("git", "-C", rwProjectDir, "init")
	output, err = cmd.CombinedOutput()
	if err != nil {
		t.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
			cmd.String(), err.Error(), output)
	}
	cmd = exec.Command("git", "-C", rwProjectDir, "commit", `--message="Initial commit"`, "--allow-empty")
	output, err = cmd.CombinedOutput()
	if err != nil {
		t.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
			cmd.String(), err.Error(), output)
	}
	repoDir := path.Join(codebaseDir, ".repo")
	if err = os.Mkdir(repoDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	listContents := []byte(projectList)
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
	pathBinder := bind.NewFakePathBinder()
	ws := New(pathBinder, wsTopDir)
	if _, err = ws.Create("test-workspace", "test-codebase"); err != nil {
		t.Error(err)
	}
	workspaceDir, err := ws.GetDir("test-workspace")
	if err != nil {
		t.Error(err)
	}
	editPath := path.Join(workspaceDir, "editable-project")
	_, _, err = ws.Edit(editPath)
	if err != nil {
		t.Error(err)
	}
	emptyFilePath := path.Join(editPath, "empty-edit")
	emptyFile, err := os.Create(emptyFilePath)
	if err != nil {
		t.Error(err)
	}
	emptyFile.Close()
	if _, err = ws.Recreate("test-workspace"); err != nil {
		t.Error(err)
	}
	_, err = os.Stat(emptyFilePath)
	if err != nil {
		t.Error(err)
	}
	wsRoProjectDir := path.Join(workspaceDir, "read-only-project")
	isRoPathBound := false
	pathList, err := pathBinder.List()
	if err != nil {
		t.Error(err)
	}
	for _, path := range pathList {
		if path == wsRoProjectDir {
			isRoPathBound = true
		}
	}
	if !isRoPathBound {
		t.Error("Read only project was not mounted to the workspace")
	}
}
