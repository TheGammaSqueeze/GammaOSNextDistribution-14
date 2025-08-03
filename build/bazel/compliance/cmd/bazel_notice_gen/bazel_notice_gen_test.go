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
	"bytes"
	"testing"
)

func Test_doit(t *testing.T) {
	input := `
[
  {
    "rule": "@//build/soong/licenses:Android-Apache-2.0",
    "license_kinds": [
      {
        "target": "@//build/soong/licenses:SPDX-license-identifier-Apache-2.0",
        "name": "SPDX-license-identifier-Apache-2.0",
        "conditions": ["notice"]
      }
    ],
    "copyright_notice": "Copyright (C) The Android Open Source Project",
    "package_name": "Discombobulator",
    "package_url": null,
    "package_version": null,
    "license_text": "../../testdata/NOTICE_LICENSE",
	"licensees": [
        "@//bionic/libc:libc_bionic_ndk",
		"@//system/logging/liblog:liblog"
    ]
 },
 {
    "rule": "@//external/scudo:external_scudo_license",
    "license_kinds": [
      {
        "target": "@//build/soong/licenses:SPDX-license-identifier-Apache-2.0",
        "name": "SPDX-license-identifier-Apache-2.0",
        "conditions": ["notice"]
      }
    ],
    "copyright_notice": "",
    "package_name": "Scudo Standalone",
    "package_url": null,
    "package_version": null,
	"licensees": [
        "@//external/scudo:foo"
    ]
  }
]
`
	tests := []struct {
		name        string
		in          string
		listTargets bool
		want        string
	}{
		{
			name:        "ListTargets",
			in:          input,
			listTargets: true,
			want: `<!DOCTYPE html>
<html>
  <head>
    <style type="text/css">
      body { padding: 2px; margin: 0; }
      .license { background-color: seashell; margin: 1em;}
      pre { padding: 1em; }</style></head>
  <body>
    The following software has been included in this product and contains the license and notice as shown below.<p>
    <strong>Discombobulator</strong><br>Copyright Notice: Copyright (C) The Android Open Source Project<br><a href=#b9835e4a000fb18a4c8970690daa3b95>License</a><br>
    Used by: @//bionic/libc:libc_bionic_ndk @//system/logging/liblog:liblog<hr>
    <strong>Scudo Standalone</strong><br>
    Used by: @//external/scudo:foo<hr>
    <div id="b9835e4a000fb18a4c8970690daa3b95" class="license"><pre>neque porro quisquam est qui do-
lorem ipsum

    </pre></div>
  </body>
</html>
`,
		},
		{
			name:        "NoTargets",
			in:          input,
			listTargets: false,
			want: `<!DOCTYPE html>
<html>
  <head>
    <style type="text/css">
      body { padding: 2px; margin: 0; }
      .license { background-color: seashell; margin: 1em;}
      pre { padding: 1em; }</style></head>
  <body>
    The following software has been included in this product and contains the license and notice as shown below.<p>
    <strong>Discombobulator</strong><br>Copyright Notice: Copyright (C) The Android Open Source Project<br><a href=#b9835e4a000fb18a4c8970690daa3b95>License</a><br>
    <strong>Scudo Standalone</strong><br>
    <div id="b9835e4a000fb18a4c8970690daa3b95" class="license"><pre>neque porro quisquam est qui do-
lorem ipsum

    </pre></div>
  </body>
</html>
`,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			buf := bytes.Buffer{}
			newGenerator(tt.in).generate(&buf, tt.listTargets)
			got := buf.String()
			if got != tt.want {
				t.Errorf("doit() = %v, want %v", got, tt.want)
			}
		})
	}
}
