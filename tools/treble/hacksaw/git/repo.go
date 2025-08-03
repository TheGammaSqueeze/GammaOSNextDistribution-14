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
	"bufio"
	"os"
	"path"
)

type repoLister struct {
}

func NewRepoLister() ProjectLister {
	var rl repoLister
	return &rl
}

func (rl repoLister) List(codebasePath string) ([]string, error) {
	projectList := []string{".repo"}
	listPath := path.Join(codebasePath, ".repo/project.list")
	listFile, err := os.Open(listPath)
	if err != nil {
		return projectList, err
	}
	defer listFile.Close()
	scanner := bufio.NewScanner(listFile)
	for scanner.Scan() {
		line := scanner.Text()
		if line != "" {
			projectList = append(projectList, line)
		}
	}
	return projectList, err
}
