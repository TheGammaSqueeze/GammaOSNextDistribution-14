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
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"path/filepath"
	"strings"
)

type FileCopier struct {
}

func NewFileCopier() *FileCopier {
	var f FileCopier
	return &f
}

func (f FileCopier) GetIsGitProjectFunc(codebaseDir string, gitProjects []string) func(string) (bool, error) {
	//Convert the git project list to a set to speed up lookups
	gitProjectSet := make(map[string]struct{})
	var exists = struct{}{}
	for _, project := range gitProjects {
		gitProjectSet[project] = exists
	}

	return func(pathToCheck string) (bool, error) {
		var err error
		if pathToCheck, err = filepath.Rel(codebaseDir, pathToCheck); err != nil {
			return false, err
		}
		if _, ok := gitProjectSet[pathToCheck]; ok {
			return true, err
		}
		return false, err
	}
}

func (f FileCopier) GetContainsGitProjectFunc(codebaseDir string, gitProjects []string) func(string) (bool, error) {
	//Extract the set of dirs that contain git projects
	containsGitSet := make(map[string]struct{})
	var exists = struct{}{}
	for _, project := range gitProjects {
		for dir := project; dir != "." && dir != "/"; dir = filepath.Dir(dir) {
			containsGitSet[dir] = exists
		}
	}

	return func(pathToCheck string) (bool, error) {
		var err error
		if pathToCheck, err = filepath.Rel(codebaseDir, pathToCheck); err != nil {
			return false, err
		}
		if _, ok := containsGitSet[pathToCheck]; ok {
			return true, err
		}
		return false, err
	}
}

//gitProjects is relative to codebaseDir
func (f FileCopier) Copy(codebaseDir string, gitProjects []string, workspaceDir string) error {
	isGitProject := f.GetIsGitProjectFunc(codebaseDir, gitProjects)
	containsGitProject := f.GetContainsGitProjectFunc(codebaseDir, gitProjects)

	return filepath.Walk(codebaseDir,
		func(path string, info os.FileInfo, err error) error {
			if err != nil {
				return err
			}

			// Copy files
			if !info.IsDir() {
				return f.CopyNode(info, codebaseDir, path, workspaceDir)
			}

			if path == filepath.Clean(codebaseDir) {
				return nil
			}

			// Always skip traversal of root repo directories
			if path == filepath.Join(codebaseDir, ".repo") {
				return filepath.SkipDir
			}

			// Skip all git projects
			var isGitProj bool
			if isGitProj, err = isGitProject(path); err != nil {
				return err
			}
			if isGitProj {
				return filepath.SkipDir
			}

			// Copy over files
			var containsGitProj bool
			if containsGitProj, err = containsGitProject(path); err != nil {
				return err
			}
			if !containsGitProj {
				destPath, err := f.GetDestPath(codebaseDir, path, workspaceDir)
				if err != nil {
					return err
				}
				if err = f.CopyDirRecursive(info, path, destPath); err != nil {
					return err
				}
				return filepath.SkipDir
			}
			return f.CopyNode(info, codebaseDir, path, workspaceDir)
		})
}

func (f FileCopier) GetDestPath(codebaseDir, sourcePath, workspaceDir string) (string, error) {
	if !strings.HasPrefix(sourcePath+"/", codebaseDir+"/") {
		return "", fmt.Errorf("%s is not contained in %s", sourcePath, codebaseDir)
	}
	relPath, err := filepath.Rel(codebaseDir, sourcePath)
	if err != nil {
		return "", err
	}
	destPath := filepath.Join(workspaceDir, relPath)
	return destPath, err
}

// Copy any single file, symlink or dir non-recursively
// sourcePath must be contained in codebaseDir
func (f FileCopier) CopyNode(sourceInfo os.FileInfo, codebaseDir, sourcePath, workspaceDir string) error {
	destPath, err := f.GetDestPath(codebaseDir, sourcePath, workspaceDir)
	if err != nil {
		return err
	}
	switch {
	case sourceInfo.Mode()&os.ModeSymlink == os.ModeSymlink:
		return f.CopySymlink(sourcePath, destPath)
	case sourceInfo.Mode().IsDir():
		return f.CopyDirOnly(sourceInfo, destPath)
	default:
		return f.CopyFile(sourceInfo, sourcePath, destPath)
	}
}

func (f FileCopier) CopySymlink(sourcePath string, destPath string) error {
	// Skip symlink if it already exists at the destination
	_, err := os.Lstat(destPath)
	if err == nil {
		return nil
	}

	target, err := os.Readlink(sourcePath)
	if err != nil {
		return err
	}

	return os.Symlink(target, destPath)
}

// CopyDirOnly copies a directory non-recursively
// sourcePath must be contained in codebaseDir
func (f FileCopier) CopyDirOnly(sourceInfo os.FileInfo, destPath string) error {
	_, err := os.Stat(destPath)
	if err == nil {
		// Dir already exists, nothing to do
		return err
	} else if os.IsNotExist(err) {
		return os.Mkdir(destPath, sourceInfo.Mode())
	}
	return err
}

// CopyFile copies a single file
// sourcePath must be contained in codebaseDir
func (f FileCopier) CopyFile(sourceInfo os.FileInfo, sourcePath, destPath string) error {
	//Skip file if it already exists at the destination
	_, err := os.Lstat(destPath)
	if err == nil {
		return nil
	}

	sourceFile, err := os.Open(sourcePath)
	if err != nil {
		return err
	}
	defer sourceFile.Close()

	destFile, err := os.Create(destPath)
	if err != nil {
		return err
	}
	defer destFile.Close()

	_, err = io.Copy(destFile, sourceFile)
	if err != nil {
		return err
	}
	return os.Chmod(destPath, sourceInfo.Mode())
}

func (f FileCopier) CopyDirRecursive(sourceInfo os.FileInfo, sourcePath, destPath string) error {
	if err := f.CopyDirOnly(sourceInfo, destPath); err != nil {
		return err
	}
	childNodes, err := ioutil.ReadDir(sourcePath)
	if err != nil {
		return err
	}
	for _, childInfo := range childNodes {
		childSourcePath := filepath.Join(sourcePath, childInfo.Name())
		childDestPath := filepath.Join(destPath, childInfo.Name())
		switch {
		case childInfo.Mode()&os.ModeSymlink == os.ModeSymlink:
			if err = f.CopySymlink(childSourcePath, childDestPath); err != nil {
				return err
			}
		case childInfo.Mode().IsDir():
			if err = f.CopyDirRecursive(childInfo, childSourcePath, childDestPath); err != nil {
				return err
			}
		default:
			if err = f.CopyFile(childInfo, childSourcePath, childDestPath); err != nil {
				return err
			}
		}
	}
	return err
}
