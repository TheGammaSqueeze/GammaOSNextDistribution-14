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

// Package client implements the hacksaw cli client
package client

import (
	"io/ioutil"
	"os"
	"path"
	"reflect"

	"android.googlesource.com/platform/tools/treble.git/hacksaw/bind"
	"android.googlesource.com/platform/tools/treble.git/hacksaw/config"
)

func ensureConfigFileExists(cfgPath string) error {
	_, err := os.Stat(cfgPath)
	if os.IsNotExist(err) {
		//continue to init config file
	} else {
		return err
	}

	//init config file
	if err = os.MkdirAll(path.Dir(cfgPath), os.ModePerm); err != nil {
		return err
	}
	return ioutil.WriteFile(cfgPath, []byte("{}"), os.ModePerm)
}

func HandleCommand(workspaceTopDir string, pathBinder bind.PathBinder, args []string) error {
	cfgPath := path.Join(workspaceTopDir, "config.json")
	if err := ensureConfigFileExists(cfgPath); err != nil {
		return err
	}
	cfg := config.GetConfig()
	if err := cfg.ReadConfigFromFile(cfgPath); err != nil {
		return err
	}
	//Save a copy of the config to detect changes
	savedCfg := cfg.Copy()
	cmd := NewCommand(pathBinder, workspaceTopDir)
	if err := cmd.Handle(args); err != nil {
		return err
	}
	if reflect.DeepEqual(savedCfg, cfg) {
		return nil
	}
	return cfg.WriteConfigToFile(cfgPath)
}
