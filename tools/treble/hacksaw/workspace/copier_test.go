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
	"path"
	"testing"
)

func TestCopy(t *testing.T) {
	// Setup codebase
	codebaseDir, err := ioutil.TempDir("", "codebase")
	if err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	project1GitDir := path.Join(codebaseDir, "project1", ".git")
	if err = os.MkdirAll(project1GitDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	emptyBytes := []byte{}
	project1File := path.Join(codebaseDir, "project1", "projectfile")
	if err = ioutil.WriteFile(project1File, emptyBytes, os.ModePerm); err != nil {
		t.Error(err)
	}
	symlink := path.Join(codebaseDir, "symlink")
	if err = os.Symlink(path.Join(codebaseDir, "project1"), symlink); err != nil {
		t.Error(err)
	}
	project2GitDir := path.Join(codebaseDir, "dirwithprojects", "project2", ".git")
	if err = os.MkdirAll(project2GitDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	dirWithoutProjects := path.Join(codebaseDir, "dirwithoutprojects")
	if err = os.Mkdir(dirWithoutProjects, os.ModePerm); err != nil {
		t.Error(err)
	}
	projectSiblingFile := path.Join(codebaseDir, "dirwithprojects", "projectsiblingfile")
	if err = ioutil.WriteFile(projectSiblingFile, emptyBytes, os.ModePerm); err != nil {
		t.Error(err)
	}
	noProjectFile1 := path.Join(dirWithoutProjects, "noprojectfile1")
	if err = ioutil.WriteFile(noProjectFile1, emptyBytes, os.ModePerm); err != nil {
		t.Error(err)
	}
	noProjectFile2 := path.Join(dirWithoutProjects, "noprojectfile2")
	if err = ioutil.WriteFile(noProjectFile2, emptyBytes, os.ModePerm); err != nil {
		t.Error(err)
	}
	topFile := path.Join(codebaseDir, "topfile")
	if err = ioutil.WriteFile(topFile, emptyBytes, os.ModePerm); err != nil {
		t.Error(err)
	}
	gitProjects := []string{
		"project1",
		"dirwithprojects/project2",
	}

	// Set up workspace
	workspaceDir, err := ioutil.TempDir("", "workspace")
	if err != nil {
		t.Error(err)
	}
	//This dir may already exist if the projects have been mounted
	wsDirWithProjects := path.Join(workspaceDir, "dirwithprojects")
	if err = os.Mkdir(wsDirWithProjects, os.ModePerm); err != nil {
		t.Error(err)
	}

	copier := NewFileCopier()
	if err = copier.Copy(codebaseDir, gitProjects, workspaceDir); err != nil {
		t.Error(err)
	}

	wsTopFile := path.Join(workspaceDir, "topfile")
	_, err = os.Stat(wsTopFile)
	if err != nil {
		t.Error(err)
	}
	wsNoProjectFile1 := path.Join(workspaceDir, "dirwithoutprojects", "noprojectfile1")
	_, err = os.Stat(wsNoProjectFile1)
	if err != nil {
		t.Error(err)
	}
	wsNoProjectFile2 := path.Join(workspaceDir, "dirwithoutprojects", "noprojectfile2")
	_, err = os.Stat(wsNoProjectFile2)
	if err != nil {
		t.Error(err)
	}
	wsProjectSiblingFile := path.Join(workspaceDir, "dirwithprojects", "projectsiblingfile")
	_, err = os.Stat(wsProjectSiblingFile)
	if err != nil {
		t.Error(err)
	}
	wsSymlink := path.Join(workspaceDir, "symlink")
	_, err = os.Stat(wsSymlink)
	if err != nil {
		t.Error(err)
	}
	//TODO: check why this is failing
	//	if linkInfo.Mode() & os.ModeSymlink != os.ModeSymlink {
	//		t.Error("Symlink not copied as symlink")
	//	}
	wsProject2Dir := path.Join(workspaceDir, "dirwithprojects", "project2")
	_, err = os.Stat(wsProject2Dir)
	if err == nil {
		t.Error("Project2 mistakenly copied")
	} else if os.IsNotExist(err) {
		//This error is expected
	} else {
		t.Error(err)
	}
	wsProject1Dir := path.Join(workspaceDir, "project1")
	_, err = os.Stat(wsProject1Dir)
	if err == nil {
		t.Error("Project1 mistakenly copied")
	} else if os.IsNotExist(err) {
		//This error is expected
	} else {
		t.Error(err)
	}
}
