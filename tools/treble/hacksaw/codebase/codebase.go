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

// Package codebase let's you manage codebases
package codebase

import (
	"fmt"
	"os"
	"path/filepath"

	"android.googlesource.com/platform/tools/treble.git/hacksaw/config"
)

// Add a codebase to the list of supported codebases
func Add(name string, path string) (*config.Config, error) {
	absPath, err := filepath.Abs(path)
	if err != nil {
		return nil, err
	}
	//check that the codebase path is valid before adding
	_, err = os.Stat(absPath)
	if err != nil {
		return nil, err
	}
	cfg := config.GetConfig()
	if _, ok := cfg.Codebases[name]; ok {
		return cfg, fmt.Errorf("Codebase %s already exists", name)
	}
	cfg.Codebases[name] = absPath
	return cfg, err
}

// Remove an existing codebase
func Remove(remove string) (*config.Config, error) {
	cfg := config.GetConfig()
	_, ok := cfg.Codebases[remove]
	if !ok {
		return nil, fmt.Errorf("Codebase %s not found", remove)
	}
	delete(cfg.Codebases, remove)
	if cfg.DefaultCodebase == remove {
		cfg.DefaultCodebase = ""
	}
	return cfg, nil
}

// Default gets the default codebase
func Default() string {
	cfg := config.GetConfig()
	def := cfg.DefaultCodebase
	return def
}

// SetDefault sets the default codebase
func SetDefault(def string) error {
	cfg := config.GetConfig()
	_, ok := cfg.Codebases[def]
	if !ok {
		return fmt.Errorf("Codebase %s not found", def)
	}
	cfg.DefaultCodebase = def
	return nil
}

// List all supported codebases
func List() map[string]string {
	cfg := config.GetConfig()
	return cfg.Codebases
}

// GetDir retrieves the directory of a specific workspace
func GetDir(codebase string) (string, error) {
	cfg := config.GetConfig()
	dir, ok := cfg.Codebases[codebase]
	if !ok {
		return dir, fmt.Errorf("Codebase %s not found",
			codebase)
	}
	return dir, nil
}
