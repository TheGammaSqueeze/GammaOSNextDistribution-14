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

package git

import (
	"io/ioutil"
	"os"
	"path"
	"reflect"
	"testing"
)

func TestList(t *testing.T) {
	testCodebaseDir, err := ioutil.TempDir("", "codebase")
	if err != nil {
		t.Error(err)
	}
	if err = os.Mkdir(path.Join(testCodebaseDir, ".repo"), os.ModePerm); err != nil {
		t.Error(err)
	}
	listContents := []byte(
		"path/to/project1\n" +
			"path/to/project2\n" +
			"path/to/project3")
	listFile := path.Join(testCodebaseDir, ".repo/project.list")
	if err = ioutil.WriteFile(listFile, listContents, os.ModePerm); err != nil {
		t.Error(err)
	}
	lister := NewRepoLister()
	projectList, err := lister.List(testCodebaseDir)
	if err != nil {
		t.Error(err)
	}
	expectedList := []string{
		".repo",
		"path/to/project1",
		"path/to/project2",
		"path/to/project3",
	}
	if !reflect.DeepEqual(projectList, expectedList) {
		t.Errorf("Got list %v but expected %v", projectList, expectedList)
	}
}
