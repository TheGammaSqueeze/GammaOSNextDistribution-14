// Copyright 2022 Google LLC
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

package main

import (
	"compress/gzip"
	"crypto/md5"
	"encoding/json"
	"flag"
	"fmt"
	"html/template"
	"io"
	"os"
	"strings"
)

var (
	inputFile   string
	outputFile  = flag.String("o", "", "output file")
	listTargets = flag.Bool("list_targets", false, "list targets using each license")
)

type LicenseKind struct {
	Target     string   `json:"target"`
	Name       string   `json:"name"`
	Conditions []string `json:"conditions"`
}

type License struct {
	Rule            string        `json:"rule"`
	CopyrightNotice string        `json:"copyright_notice"`
	PackageName     string        `json:"package_name"`
	PackageUrl      string        `json:"package_url"`
	PackageVersion  string        `json:"package_version"`
	LicenseFile     string        `json:"license_text"`
	LicenseKinds    []LicenseKind `json:"license_kinds"`
	Licensees       []string      `json:"licensees"`
}

type LicenseTextHash string

// generator generates the notices for the given set of licenses read from the JSON-encoded string.
// As the contents of the license files is often the same, they are read into the map by their hash.
type generator struct {
	Licenses         []License
	LicenseTextHash  map[string]LicenseTextHash // License.rule->hash of license text contents
	LicenseTextIndex map[LicenseTextHash]string
}

func newGenerator(in string) *generator {
	g := generator{}
	decoder := json.NewDecoder(strings.NewReader(in))
	decoder.DisallowUnknownFields() //useful to detect typos, e.g. in unit tests
	err := decoder.Decode(&g.Licenses)
	maybeQuit(err)
	return &g
}

func (g *generator) buildLicenseTextIndex() {
	g.LicenseTextHash = make(map[string]LicenseTextHash, len(g.Licenses))
	g.LicenseTextIndex = make(map[LicenseTextHash]string)
	for _, l := range g.Licenses {
		if l.LicenseFile == "" {
			continue
		}
		data, err := os.ReadFile(l.LicenseFile)
		if err != nil {
			fmt.Fprintf(os.Stderr, "%s: bad license file %s: %s\n", l.Rule, l.LicenseFile, err)
			os.Exit(1)
		}
		h := LicenseTextHash(fmt.Sprintf("%x", md5.Sum(data)))
		g.LicenseTextHash[l.Rule] = h
		if _, found := g.LicenseTextIndex[h]; !found {
			g.LicenseTextIndex[h] = string(data)
		}
	}
}

func (g *generator) generate(sink io.Writer, listTargets bool) {
	const tpl = `<!DOCTYPE html>
<html>
  <head>
    <style type="text/css">
      body { padding: 2px; margin: 0; }
      .license { background-color: seashell; margin: 1em;}
      pre { padding: 1em; }</style></head>
  <body>
    The following software has been included in this product and contains the license and notice as shown below.<p>
    {{- $x := . }}
	{{- range .Licenses }}
    {{ if .PackageName  }}<strong>{{.PackageName}}</strong>{{- else }}Rule: {{.Rule}}{{ end }}
    {{- if .CopyrightNotice }}<br>Copyright Notice: {{.CopyrightNotice}}{{ end }}
    {{- $v := index $x.LicenseTextHash .Rule }}{{- if $v }}<br><a href=#{{$v}}>License</a>{{- end }}<br>
    {{- if list_targets }}
    Used by: {{- range .Licensees }} {{.}} {{- end }}<hr>
    {{- end }}
    {{- end }}
    {{ range $k, $v := .LicenseTextIndex }}<div id="{{$k}}" class="license"><pre>{{$v}}
    </pre></div> {{- end }}
  </body>
</html>
`
	funcMap := template.FuncMap{
		"list_targets": func() bool { return listTargets },
	}
	t, err := template.New("NoticesPage").Funcs(funcMap).Parse(tpl)
	maybeQuit(err)
	if g.LicenseTextHash == nil {
		g.buildLicenseTextIndex()
	}
	maybeQuit(t.Execute(sink, g))
}

func maybeQuit(err error) {
	if err == nil {
		return
	}

	fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}

func processArgs() {
	flag.Usage = func() {
		fmt.Fprintln(os.Stderr, `usage: bazelhtmlnotice -o <output> <input>`)
		flag.PrintDefaults()
		os.Exit(2)
	}
	flag.Parse()
	if len(flag.Args()) != 1 {
		flag.Usage()
	}
	inputFile = flag.Arg(0)
}

func setupWriting() (io.Writer, io.Closer, *os.File) {
	if *outputFile == "" {
		return os.Stdout, nil, nil
	}
	ofile, err := os.Create(*outputFile)
	maybeQuit(err)
	if !strings.HasSuffix(*outputFile, ".gz") {
		return ofile, nil, ofile
	}
	gz, err := gzip.NewWriterLevel(ofile, gzip.BestCompression)
	maybeQuit(err)
	return gz, gz, ofile
}

func main() {
	processArgs()
	data, err := os.ReadFile(inputFile)
	maybeQuit(err)
	sink, closer, ofile := setupWriting()
	newGenerator(string(data)).generate(sink, *listTargets)
	if closer != nil {
		maybeQuit(closer.Close())
	}
	if ofile != nil {
		maybeQuit(ofile.Close())
	}
}
