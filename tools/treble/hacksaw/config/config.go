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

// Package config reads and writes hacksaw configuration data to io
package config

import (
	"encoding/json"
	"io"
	"io/ioutil"
	"os"
	"sync"
)

type Config struct {
	DefaultCodebase string
	Codebases       map[string]string //key: name, value: path
	Workspaces      map[string]string //key: name, value: codebase
}

//Read the configuration from an io.Reader
func (c *Config) Read(input io.Reader) error {
	cfgBytes, err := ioutil.ReadAll(input)
	if err != nil {
		return err
	}
	return json.Unmarshal(cfgBytes, &c)
}

//Write the configuration to an io.Writer
func (c Config) Write(output io.Writer) error {
	cfgBytes, err := json.MarshalIndent(c, "", " ")
	if err != nil {
		return err
	}
	_, err = output.Write(cfgBytes)
	return err
}

func (c *Config) ReadConfigFromFile(filePath string) error {
	_, err := os.Stat(filePath)
	if err != nil {
		return err
	}
	cfgFile, err := os.Open(filePath)
	if err != nil {
		return err
	}
	defer cfgFile.Close()
	err = c.Read(cfgFile)
	return err
}

func (c Config) WriteConfigToFile(filePath string) error {
	cfgFile, err := os.Create(filePath)
	if err != nil {
		return err
	}
	defer cfgFile.Close()
	return c.Write(cfgFile)
}

//Config gets a copy of the config
func (c Config) Copy() Config {
	cfgCopy := Config{
		DefaultCodebase: c.DefaultCodebase,
		Codebases:       map[string]string{},
		Workspaces:      map[string]string{}}
	for name, path := range c.Codebases {
		cfgCopy.Codebases[name] = path
	}
	for name, codebase := range c.Workspaces {
		cfgCopy.Workspaces[name] = codebase
	}
	return cfgCopy
}

//Reset sets the config to zero values
func (c *Config) Reset() {
	*c = Config{
		DefaultCodebase: "",
		Codebases:       map[string]string{},
		Workspaces:      map[string]string{}}
}

var singleton *Config
var once sync.Once

//Config gets the singleton config instance
func GetConfig() *Config {
	once.Do(func() {
		singleton = &Config{
			DefaultCodebase: "",
			Codebases:       map[string]string{},
			Workspaces:      map[string]string{}}
	})
	return singleton
}
