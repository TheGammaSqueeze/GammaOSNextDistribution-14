// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Ve
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
	"reflect"
	"testing"

	"android.googlesource.com/platform/tools/treble.git/hacksaw/bind"
)

func TestBasicCompose(t *testing.T) {
	var err error
	var codebaseDir string
	if codebaseDir, err = ioutil.TempDir("", "codebase"); err != nil {
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
	var wsTempDir string
	if wsTempDir, err = ioutil.TempDir("", "workspace"); err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(wsTempDir)
	wsTopDir := path.Join(wsTempDir, "hacksaw")
	if err = os.Mkdir(wsTopDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	fakePathBinder := bind.NewFakePathBinder()
	wsComposer := NewComposer(fakePathBinder)
	list, err := wsComposer.Compose(codebaseDir, wsTopDir)
	if err != nil {
		t.Error(err)
	}
	expectedList := []string{
		path.Join(wsTopDir, ".repo"),
		path.Join(wsTopDir, "project"),
	}
	if !reflect.DeepEqual(expectedList, list) {
		t.Errorf("Expected a list of binds %v and got %v", expectedList, list)
	}
	list, err = wsComposer.Dismantle(wsTopDir)
	if err != nil {
		t.Error(err)
	}
	expectedList = []string{
		path.Join(wsTopDir, "project"),
		path.Join(wsTopDir, ".repo"),
	}
	if !reflect.DeepEqual(expectedList, list) {
		t.Errorf("Expected a list of unbinds %v and got %v", expectedList, list)
	}
}

const projectListContents = `project2/subprojectb
project1
project2
project1/subprojecta`

func TestBindOrder(t *testing.T) {
	var err error
	var codebaseDir string
	if codebaseDir, err = ioutil.TempDir("", "codebase"); err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(codebaseDir)
	project2Git := path.Join(codebaseDir, "project2", ".git")
	if err = os.MkdirAll(project2Git, os.ModePerm); err != nil {
		t.Error(err)
	}
	project1Git := path.Join(codebaseDir, "project1", ".git")
	if err = os.MkdirAll(project1Git, os.ModePerm); err != nil {
		t.Error(err)
	}
	subProjectAGit := path.Join(codebaseDir, "project1", "subprojecta", ".git")
	if err = os.MkdirAll(subProjectAGit, os.ModePerm); err != nil {
		t.Error(err)
	}
	subProjectBGit := path.Join(codebaseDir, "project2", "subprojectb", ".git")
	if err = os.MkdirAll(subProjectBGit, os.ModePerm); err != nil {
		t.Error(err)
	}
	repoDir := path.Join(codebaseDir, ".repo")
	if err = os.Mkdir(repoDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	listContents := []byte(projectListContents)
	listPath := path.Join(repoDir, "project.list")
	if err = ioutil.WriteFile(listPath, listContents, os.ModePerm); err != nil {
		t.Error(err)
	}
	var wsTempDir string
	if wsTempDir, err = ioutil.TempDir("", "workspace"); err != nil {
		t.Error(err)
	}
	defer os.RemoveAll(wsTempDir)
	wsTopDir := path.Join(wsTempDir, "hacksaw")
	if err = os.Mkdir(wsTopDir, os.ModePerm); err != nil {
		t.Error(err)
	}
	fakePathBinder := bind.NewFakePathBinder()
	wsComposer := NewComposer(fakePathBinder)
	bindList, err := wsComposer.Compose(codebaseDir, wsTopDir)
	if err != nil {
		t.Error(err)
	}
	expectedList := []string{
		path.Join(wsTopDir, ".repo"),
		path.Join(wsTopDir, "project1"),
		path.Join(wsTopDir, "project1", "subprojecta"),
		path.Join(wsTopDir, "project2"),
		path.Join(wsTopDir, "project2", "subprojectb"),
	}
	if !reflect.DeepEqual(expectedList, bindList) {
		t.Errorf("Expected a list of binds %v and got %v", expectedList, bindList)
	}
	unbindList, err := wsComposer.Dismantle(wsTopDir)
	if err != nil {
		t.Error(err)
	}
	expectedList = []string{
		path.Join(wsTopDir, "project2", "subprojectb"),
		path.Join(wsTopDir, "project2"),
		path.Join(wsTopDir, "project1", "subprojecta"),
		path.Join(wsTopDir, "project1"),
		path.Join(wsTopDir, ".repo"),
	}
	if !reflect.DeepEqual(expectedList, unbindList) {
		t.Errorf("Expected a list of unbinds %v and got %v", expectedList, unbindList)
	}
}
