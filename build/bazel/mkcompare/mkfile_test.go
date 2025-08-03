package mkcompare

import (
	"github.com/google/go-cmp/cmp"
	"strings"
	"testing"
)

func TestParseMkFile(t *testing.T) {
	tests := []struct {
		name    string
		source  string
		want    MkFile
		wantErr bool
	}{
		{
			name: "Good1",
			source: `
include $(CLEAR_VARS) # modType
LOCAL_MODULE := mymod
LOCAL_MODULE_CLASS := ETC
include $(BUILD_PREBUILT)

ignored
ignored2

include $(CLEAR_VARS)
LOCAL_MODULE := mymod2
LOCAL_MODULE_CLASS := BIN
MY_PATH := foo
include $(BUILD_PREBUILT)
`,
			want: MkFile{
				Modules: map[string]*MkModule{
					"mymod|class:ETC|target_arch:*": {
						Type:      "modType",
						Location:  2,
						Variables: map[string]string{"LOCAL_MODULE": "mymod", "LOCAL_MODULE_CLASS": "ETC"},
					},
					"mymod2|class:BIN|target_arch:*": {
						Type:      "$(BUILD_PREBUILT)",
						Location:  10,
						Variables: map[string]string{"LOCAL_MODULE": "mymod2", "LOCAL_MODULE_CLASS": "BIN", "MY_PATH": "foo"},
					},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ParseMkFile(strings.NewReader(tt.source))
			if (err != nil) != tt.wantErr {
				t.Errorf("ParseMkFile() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !cmp.Equal(got.Modules, tt.want.Modules) {
				t.Errorf("ParseMkFile() got = %v, want %v, \ndiff: %s", got.Modules, tt.want.Modules,
					cmp.Diff(got, tt.want))
			}
		})
	}
}
