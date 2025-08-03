// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package aidl

import (
	"android/soong/android"

	"fmt"
	"strings"

	"github.com/google/blueprint"
	"github.com/google/blueprint/proptools"
)

var (
	aidlMetadataRule = pctx.StaticRule("aidlMetadataRule", blueprint.RuleParams{
		Command: `rm -f ${out} && { ` +
			`echo '{' && ` +
			`echo "\"name\": \"${name}\"," && ` +
			`echo "\"stability\": \"${stability}\"," && ` +
			`echo "\"types\": [${types}]," && ` +
			`echo "\"hashes\": [${hashes}]," && ` +
			`echo "\"has_development\": ${has_development}," && ` +
			`echo "\"use_unfrozen\": ${use_unfrozen}," && ` +
			`echo "\"versions\": [${versions}]" && ` +
			`echo '}' ` +
			`;} >> ${out}`,
		Description: "AIDL metadata: ${out}",
	}, "name", "stability", "types", "hashes", "has_development", "use_unfrozen", "versions")

	joinJsonObjectsToArrayRule = pctx.StaticRule("joinJsonObjectsToArrayRule", blueprint.RuleParams{
		Rspfile:        "$out.rsp",
		RspfileContent: "$files",
		Command: "rm -rf ${out} && " +
			// Start the output array with an opening bracket.
			"echo '[' >> ${out} && " +
			// Append each input file and a comma to the output.
			"for file in $$(cat ${out}.rsp); do " +
			"cat $$file >> ${out}; echo ',' >> ${out}; " +
			"done && " +
			// Remove the last comma, replacing it with the closing bracket.
			"sed -i '$$d' ${out} && echo ']' >> ${out}",
		Description: "Joining JSON objects into array ${out}",
	}, "files")
)

func init() {
	android.RegisterModuleType("aidl_interfaces_metadata", aidlInterfacesMetadataSingletonFactory)
}

func aidlInterfacesMetadataSingletonFactory() android.Module {
	i := &aidlInterfacesMetadataSingleton{}
	android.InitAndroidModule(i)
	return i
}

type aidlInterfacesMetadataSingleton struct {
	android.ModuleBase

	metadataPath android.WritablePath
}

var _ android.OutputFileProducer = (*aidlInterfacesMetadataSingleton)(nil)

func (m *aidlInterfacesMetadataSingleton) GenerateAndroidBuildActions(ctx android.ModuleContext) {
	if m.Name() != aidlMetadataSingletonName {
		ctx.PropertyErrorf("name", "must be %s", aidlMetadataSingletonName)
		return
	}

	type ModuleInfo struct {
		Stability      string
		ComputedTypes  []string
		HashFiles      []string
		HasDevelopment android.WritablePath
		UseUnfrozen    bool
		Versions       []string
	}

	// name -> ModuleInfo
	moduleInfos := map[string]ModuleInfo{}
	ctx.VisitDirectDeps(func(m android.Module) {
		if !m.ExportedToMake() {
			return
		}

		switch t := m.(type) {
		case *aidlInterface:
			info := moduleInfos[t.ModuleBase.Name()]
			info.Stability = proptools.StringDefault(t.properties.Stability, "")
			info.ComputedTypes = t.computedTypes
			info.Versions = t.getVersions()
			info.UseUnfrozen = t.useUnfrozen(ctx)
			moduleInfos[t.ModuleBase.Name()] = info
		case *aidlGenRule:
			info := moduleInfos[t.properties.BaseName]
			if t.hashFile != nil {
				info.HashFiles = append(info.HashFiles, t.hashFile.String())
			}
			moduleInfos[t.properties.BaseName] = info
		case *aidlApi:
			info := moduleInfos[t.properties.BaseName]
			info.HasDevelopment = t.hasDevelopment
			moduleInfos[t.properties.BaseName] = info
		}

	})

	var metadataOutputs android.Paths
	for _, name := range android.SortedKeys(moduleInfos) {
		info := moduleInfos[name]
		metadataPath := android.PathForModuleOut(ctx, "metadata_"+name)
		metadataOutputs = append(metadataOutputs, metadataPath)

		// There is one aidlGenRule per-version per-backend. If we had
		// objects per version and sub-objects per backend, we could
		// avoid needing to filter out duplicates.
		info.HashFiles = android.FirstUniqueStrings(info.HashFiles)
		readHashes := ""
		if len(info.HashFiles) > 0 {
			readHashes = "$$(sed 's/.*/\"&\",/' " + strings.Join(info.HashFiles, " ") +
				"| tr '\\n' ' ' | sed 's/, $$//')"
		}

		implicits := android.PathsForSource(ctx, info.HashFiles)
		hasDevelopmentValue := "true"
		if info.HasDevelopment != nil {
			hasDevelopmentValue = "$$(if [ \"$$(cat " + info.HasDevelopment.String() +
				")\" = \"1\" ]; then echo true; else echo false; fi)"
		}

		useUnfrozenValue := "false"
		if info.UseUnfrozen {
			useUnfrozenValue = "true"
		}

		ctx.Build(pctx, android.BuildParams{
			Rule:      aidlMetadataRule,
			Implicits: implicits,
			Input:     info.HasDevelopment,
			Output:    metadataPath,
			Args: map[string]string{
				"name":            name,
				"stability":       info.Stability,
				"types":           strings.Join(wrap(`\"`, info.ComputedTypes, `\"`), ", "),
				"hashes":          readHashes,
				"has_development": hasDevelopmentValue,
				"use_unfrozen":    useUnfrozenValue,
				"versions":        strings.Join(info.Versions, ", "),
			},
		})
	}

	m.metadataPath = android.PathForModuleOut(ctx, "aidl_metadata.json")

	ctx.Build(pctx, android.BuildParams{
		Rule:   joinJsonObjectsToArrayRule,
		Inputs: metadataOutputs,
		Output: m.metadataPath,
		Args: map[string]string{
			"files": strings.Join(metadataOutputs.Strings(), " "),
		},
	})
}

func (m *aidlInterfacesMetadataSingleton) OutputFiles(tag string) (android.Paths, error) {
	if tag != "" {
		return nil, fmt.Errorf("unsupported tag %q", tag)
	}

	return android.Paths{m.metadataPath}, nil
}
