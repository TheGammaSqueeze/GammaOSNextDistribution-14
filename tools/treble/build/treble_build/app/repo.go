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
package app

import (
	"encoding/xml"
	"io/ioutil"
)

type RepoRemote struct {
	Name     string `xml:"name,attr"`
	Revision string `xml:"fetch,attr"`
}
type RepoDefault struct {
	Remote   string `xml:"remote,attr"`
	Revision string `xml:"revision,attr"`
}
type RepoProject struct {
	Groups   string  `xml:"groups,attr"`
	Name     string  `xml:"name,attr"`
	Revision string  `xml:"revision,attr"`
	Path     string  `xml:"path,attr"`
	Remote   *string `xml:"remote,attr"`
}
type RepoManifest struct {
	XMLName  xml.Name      `xml:"manifest"`
	Remotes  []RepoRemote  `xml:"remote"`
	Default  RepoDefault   `xml:"default"`
	Projects []RepoProject `xml:"project"`
}

// Parse a repo manifest file
func ParseXml(filename string) (*RepoManifest, error) {
	data, err := ioutil.ReadFile(filename)
	if err != nil {
		return nil, err
	}
	v := &RepoManifest{}
	err = xml.Unmarshal(data, &v)
	if err != nil {
		return nil, err
	}
	return v, nil
}
