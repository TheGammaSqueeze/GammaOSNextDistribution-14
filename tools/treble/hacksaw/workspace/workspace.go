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

// Package workspace let's you manage workspaces
package workspace

import (
	"fmt"
	"io/ioutil"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"android.googlesource.com/platform/tools/treble.git/hacksaw/bind"
	"android.googlesource.com/platform/tools/treble.git/hacksaw/codebase"
	"android.googlesource.com/platform/tools/treble.git/hacksaw/config"
	"android.googlesource.com/platform/tools/treble.git/hacksaw/git"
)

type Workspace struct {
	composer Composer
	topDir   string
}

func New(bm bind.PathBinder, topDir string) Workspace {
	return Workspace{NewComposer(bm), topDir}
}

// Create workspace
func (w Workspace) Create(workspaceName string, codebaseName string) (string, error) {
	cfg := config.GetConfig()
	_, ok := cfg.Codebases[codebaseName]
	if !ok {
		return "", fmt.Errorf("Codebase %s does not exist", codebaseName)
	}
	if _, ok := cfg.Workspaces[workspaceName]; ok {
		return "", fmt.Errorf("Workspace %s already exists", workspaceName)
	}
	cfg.Workspaces[workspaceName] = codebaseName
	workspaceDir, err := w.GetDir(workspaceName)
	if err != nil {
		return "", err
	}
	if err = os.MkdirAll(workspaceDir, os.ModePerm); err != nil {
		return "", err
	}
	codebaseDir, err := codebase.GetDir(codebaseName)
	if err != nil {
		return "", err
	}
	//TODO: match the order of parameters with Create
	if _, err = w.composer.Compose(codebaseDir, workspaceDir); err != nil {
		return "", err
	}
	return workspaceDir, nil
}

// Recreate workspace
func (w Workspace) Recreate(workspaceName string) (string, error) {
	cfg := config.GetConfig()
	codebaseName, ok := cfg.Workspaces[workspaceName]
	if !ok {
		return "", fmt.Errorf("Workspace %s does not exist", workspaceName)
	}
	workspaceDir, err := w.GetDir(workspaceName)
	if err != nil {
		return "", err
	}
	codebaseDir, err := codebase.GetDir(codebaseName)
	if err != nil {
		return "", err
	}
	if _, err = w.composer.Compose(codebaseDir, workspaceDir); err != nil {
		return "", err
	}
	return workspaceDir, nil
}

// GetDir retrieves the directory of a specific workspace
func (w Workspace) GetDir(workspaceName string) (string, error) {
	cfg := config.GetConfig()
	_, ok := cfg.Workspaces[workspaceName]
	if !ok {
		return "", fmt.Errorf("Workspace %s not found", workspaceName)
	}
	dir := filepath.Join(w.topDir, workspaceName)
	return dir, nil
}

// GetCodebase retrieves the codebase that a workspace belongs to
func (w Workspace) GetCodebase(workspaceName string) (string, error) {
	cfg := config.GetConfig()
	codebase, ok := cfg.Workspaces[workspaceName]
	if !ok {
		return "", fmt.Errorf("Workspace %s not found", workspaceName)
	}
	return codebase, nil
}

//SetTopDir sets the directory that contains all workspaces
func (w *Workspace) SetTopDir(dir string) {
	w.topDir = dir
}

func (w Workspace) List() map[string]string {
	cfg := config.GetConfig()
	list := make(map[string]string)
	for name, codebaseName := range cfg.Workspaces {
		list[name] = codebaseName
	}
	return list
}

func (w Workspace) DetachGitWorktrees(workspaceName string, unbindList []string) error {
	workspaceDir, err := w.GetDir(workspaceName)
	if err != nil {
		return err
	}
	workspaceDir, err = filepath.Abs(workspaceDir)
	if err != nil {
		return err
	}
	//resolve all symlinks so it can be
	//matched to mount paths
	workspaceDir, err = filepath.EvalSymlinks(workspaceDir)
	if err != nil {
		return err
	}
	codebaseName, err := w.GetCodebase(workspaceName)
	if err != nil {
		return err
	}
	codebaseDir, err := codebase.GetDir(codebaseName)
	if err != nil {
		return err
	}
	lister := git.NewRepoLister()
	gitProjects, err := lister.List(codebaseDir)
	if err != nil {
		return err
	}
	gitWorktrees := make(map[string]bool)
	for _, project := range gitProjects {
		gitWorktrees[project] = true
	}
	//projects that were unbound were definitely
	//never git worktrees
	for _, unbindPath := range unbindList {
		project, err := filepath.Rel(workspaceDir, unbindPath)
		if err != nil {
			return err
		}
		if _, ok := gitWorktrees[project]; ok {
			gitWorktrees[project] = false
		}
	}
	for project, isWorktree := range gitWorktrees {
		if !isWorktree {
			continue
		}
		codebaseProject := filepath.Join(codebaseDir, project)
		workspaceProject := filepath.Join(workspaceDir, project)
		_, err = os.Stat(workspaceProject)
		if err == nil {
			//proceed to detach
		} else if os.IsNotExist(err) {
			//just skip if it doesn't exist
			continue
		} else {
			return err
		}
		contents, err := ioutil.ReadDir(workspaceProject)
		if err != nil {
			return err
		}
		if len(contents) == 0 {
			//empty directory, not even a .git
			//not a wortree
			continue
		}
		fmt.Print(".")
		cmd := exec.Command("git",
			"-C", codebaseProject,
			"worktree", "remove", "--force", workspaceProject)
		output, err := cmd.CombinedOutput()
		if err != nil {
			return fmt.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
				cmd.String(), err.Error(), output)
		}
		cmd = exec.Command("git",
			"-C", codebaseProject,
			"branch", "--delete", "--force", workspaceName)
		output, err = cmd.CombinedOutput()
		if err != nil {
			return fmt.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
				cmd.String(), err.Error(), output)
		}
	}
	return nil
}

func (w Workspace) Remove(remove string) (*config.Config, error) {
	cfg := config.GetConfig()
	_, ok := cfg.Workspaces[remove]
	if !ok {
		return cfg, fmt.Errorf("Workspace %s not found", remove)
	}
	workspaceDir, err := w.GetDir(remove)
	if err != nil {
		return cfg, err
	}
	unbindList, err := w.composer.Dismantle(workspaceDir)
	if err != nil {
		return cfg, err
	}
	fmt.Print("Detaching worktrees")
	if err = w.DetachGitWorktrees(remove, unbindList); err != nil {
		return cfg, err
	}
	fmt.Print("\n")
	fmt.Println("Removing files")
	if err = os.RemoveAll(workspaceDir); err != nil {
		return cfg, err
	}
	delete(cfg.Workspaces, remove)
	return cfg, err
}

func (w Workspace) Edit(editPath string) (string, string, error) {
	editPath, err := filepath.Abs(editPath)
	if err != nil {
		return "", "", err
	}
	editPath, err = filepath.EvalSymlinks(editPath)
	if err != nil {
		return "", "", err
	}
	relProjectPath, err := w.getReadOnlyProjectFromPath(editPath)
	if err != nil {
		return "", "", err
	}
	workspaceName, err := w.getWorkspaceFromPath(editPath)
	if err != nil {
		return "", "", err
	}
	workspaceDir, err := w.GetDir(workspaceName)
	if err != nil {
		return "", "", err
	}
	codebaseName, err := w.GetCodebase(workspaceName)
	if err != nil {
		return "", "", err
	}
	codebaseDir, err := codebase.GetDir(codebaseName)
	if err != nil {
		return "", "", err
	}
	wsProjectPath := filepath.Join(workspaceDir, relProjectPath)
	if err = w.composer.Unbind(wsProjectPath); err != nil {
		return "", "", err
	}
	//TODO: support editing nested projects
	//the command above unbinds nested child projects but
	//we don't rebind them after checking out an editable project branch
	cbProjectPath := filepath.Join(codebaseDir, relProjectPath)
	branchName := workspaceName
	cmd := exec.Command("git",
		"-C", cbProjectPath,
		"worktree", "add",
		"-b", branchName,
		wsProjectPath)
	output, err := cmd.CombinedOutput()
	if err != nil {
		return "", "", fmt.Errorf("Command\n%s\nfailed with the following:\n%s\n%s",
			cmd.String(), err.Error(), output)
	}
	return branchName, wsProjectPath, err
}

func (w Workspace) getReadOnlyProjectFromPath(inPath string) (string, error) {
	worspaceName, err := w.getWorkspaceFromPath(inPath)
	if err != nil {
		return "", err
	}
	workspacePath, err := w.GetDir(worspaceName)
	if err != nil {
		return "", err
	}
	bindList, err := w.composer.List(workspacePath)
	if err != nil {
		return "", err
	}
	for _, bindPath := range bindList {
		if !strings.HasPrefix(inPath+"/", bindPath+"/") {
			continue
		}
		relProjectPath, err := filepath.Rel(workspacePath, bindPath)
		if err != nil {
			return "", err
		}
		return relProjectPath, nil
	}
	return "", fmt.Errorf("Path %s is already editable", inPath)
}

func (w Workspace) getWorkspaceFromPath(inPath string) (string, error) {
	for workspaceName, _ := range w.List() {
		dir, err := w.GetDir(workspaceName)
		if err != nil {
			return "", err
		}
		if strings.HasPrefix(inPath+"/", dir+"/") {
			return workspaceName, nil
		}
	}
	return "", fmt.Errorf("Path %s is not contained in a workspace", inPath)
}
