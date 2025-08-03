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
	"os"
	"path/filepath"
	"sort"
	"strings"

	"android.googlesource.com/platform/tools/treble.git/hacksaw/bind"
	"android.googlesource.com/platform/tools/treble.git/hacksaw/git"
)

type Composer struct {
	pathBinder bind.PathBinder
}

func NewComposer(bm bind.PathBinder) Composer {
	return Composer{bm}
}

func isDirEmpty(name string) (bool, error) {
	dir, err := os.Open(name)
	if err != nil {
		return false, err
	}
	defer dir.Close()
	_, err = dir.Readdirnames(1)
	if err == io.EOF {
		return true, nil
	}
	return false, err
}

//Compose a workspace from a codebase
//Returns a list of path binds in the order they
//were bound
func (m Composer) Compose(codebasePath string, workspacePath string) ([]string, error) {
	lister := git.NewRepoLister()
	gitProjects, err := lister.List(codebasePath)
	if err != nil {
		return nil, err
	}
	fmt.Print("Composing")
	var bindList []string
	//Sorting the list of projects in alphabetical
	//order ensures that parent projects are bound
	//before their nested child projects, which is important
	//to avoid bind conflicts
	sort.Strings(gitProjects)
	for _, project := range gitProjects {
		fmt.Print(".") //Display some progress
		//skip empty project names
		if project == "" {
			continue
		}
		source := filepath.Join(codebasePath, project)
		destination := filepath.Join(workspacePath, project)
		if err = os.MkdirAll(destination, os.ModePerm); err != nil {
			fmt.Print("\n")
			return bindList, err
		}
		isEmpty, err := isDirEmpty(destination)
		if err != nil {
			return bindList, err
		}
		if !isEmpty {
			// If the destination dir already existed and
			// was not empty then assume we are recreating
			// a workspace and the current path already
			// existed in the workspace
			continue
		}
		if err = m.pathBinder.BindReadOnly(source, destination); err != nil {
			fmt.Print("\n")
			return bindList, err
		}
		bindList = append(bindList, destination)
	}
	fmt.Print("\n")
	fmt.Println("Workspace composed")
	copier := NewFileCopier()
	return bindList, copier.Copy(codebasePath, gitProjects, workspacePath)
}

//Dismantle a workspace
//Returns a list of path unbinds in the order they
//were unbound
func (m Composer) Dismantle(dismantlePath string) ([]string, error) {
	bindList, err := m.List(dismantlePath)
	if err != nil {
		return nil, err
	}
	//Sorting the list of binds in reverse alphabetical
	//order ensures that nested child projects are unbound
	//before their parent projects, which is important
	//to avoid unbind conflicts
	sort.Sort(sort.Reverse(sort.StringSlice(bindList)))
	fmt.Print("Dismantling")
	var unbindList []string
	for _, bindPath := range bindList {
		fmt.Print(".") //Display some progress
		if err = m.pathBinder.Unbind(bindPath); err != nil {
			fmt.Print("\n")
			return unbindList, err
		}
		unbindList = append(unbindList, bindPath)
	}
	fmt.Print("\n")
	fmt.Println("Workspace dismantled")
	return unbindList, err
}

//Unbind a project
func (m Composer) Unbind(unbindPath string) error {
	return m.pathBinder.Unbind(unbindPath)
}

//List all binds attached under a directory
func (m Composer) List(listPath string) ([]string, error) {
	listPath, err := filepath.EvalSymlinks(listPath)
	if err != nil {
		return nil, err
	}
	fullBindList, err := m.pathBinder.List()
	if err != nil {
		return nil, err
	}
	var matchBindList []string
	for _, bindPath := range fullBindList {
		if strings.HasPrefix(bindPath+"/", listPath+"/") {
			matchBindList = append(matchBindList, bindPath)
		}
	}
	return matchBindList, err
}
