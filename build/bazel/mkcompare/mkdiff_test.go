package mkcompare

import (
	"github.com/google/go-cmp/cmp"
	"reflect"
	"testing"
)

func TestClassify(t *testing.T) {
	tests := []struct {
		name       string
		mLeft      map[string]int
		mRight     map[string]int
		wantLeft   []string
		wantCommon []string
		wantRight  []string
	}{
		{
			name:       "one",
			mLeft:      map[string]int{"a": 1, "b": 2},
			mRight:     map[string]int{"b": 3, "c": 4},
			wantLeft:   []string{"a"},
			wantCommon: []string{"b"},
			wantRight:  []string{"c"},
		},
		{
			name:       "two",
			mLeft:      map[string]int{"a": 1, "b": 2},
			mRight:     map[string]int{"a": 3},
			wantLeft:   []string{"b"},
			wantCommon: []string{"a"},
			wantRight:  nil,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			gotLeft, gotCommon, gotRight := Classify(tt.mLeft, tt.mRight, func(_ string) bool { return true })
			if !reflect.DeepEqual(gotLeft, tt.wantLeft) {
				t.Errorf("classify() gotLeft = %v, want %v", gotLeft, tt.wantLeft)
			}
			if !reflect.DeepEqual(gotCommon, tt.wantCommon) {
				t.Errorf("classify() gotCommon = %v, want %v", gotCommon, tt.wantCommon)
			}
			if !reflect.DeepEqual(gotRight, tt.wantRight) {
				t.Errorf("classify() gotRight = %v, want %v", gotRight, tt.wantRight)
			}
		})
	}
}

func Test_compareVariableValues(t *testing.T) {
	tests := []struct {
		name         string
		ref          string
		our          string
		sort         bool
		want_missing []string
		want_extra   []string
	}{
		{name: "Same", ref: "x a b", our: "a b x", sort: true},
		{name: "diff1", ref: "a b c", our: "d a", sort: true, want_missing: []string{"b", "c"}, want_extra: []string{"d"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got_missing, got_extra := compareVariableValues(tt.ref, tt.our, tt.sort)
			if diff := cmp.Diff(got_missing, tt.want_missing); diff != "" {
				t.Errorf("missing items differ: %s", diff)
			}
			if diff := cmp.Diff(got_extra, tt.want_extra); diff != "" {
				t.Errorf("extra items differ: %s", diff)
			}
		})
	}
}

func TestCompare(t *testing.T) {
	refMod1 := MkModule{Type: "foo", Location: 1, Variables: map[string]string{"var1": "a", "var2": "b"}}
	ourMod1 := MkModule{Type: "foo", Location: 3, Variables: map[string]string{"var1": "a", "var2": "c"}}
	tests := []struct {
		name      string
		refMod    *MkModule
		ourMod    *MkModule
		isGoodVar func(string) bool
		want      MkModuleDiff
	}{
		{
			name:      "Ignored vars",
			refMod:    &refMod1,
			ourMod:    &ourMod1,
			isGoodVar: func(v string) bool { return v == "var1" },
			want:      MkModuleDiff{},
		},
		{
			name:      "Different values",
			refMod:    &refMod1,
			ourMod:    &ourMod1,
			isGoodVar: func(_ string) bool { return true },
			want: MkModuleDiff{
				DiffVars: []MkVarDiff{{"var2", []string{"b"}, []string{"c"}}},
			},
		},
		{
			name:      "DifferentVars",
			refMod:    &refMod1,
			ourMod:    &MkModule{Type: "foo", Variables: map[string]string{"var2": "b", "var3": "c"}},
			isGoodVar: func(_ string) bool { return true },
			want: MkModuleDiff{
				MissingVars: []string{"var1"},
				ExtraVars:   []string{"var3"},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tt.want.Ref = tt.refMod
			tt.want.Our = tt.ourMod
			if got := Compare(tt.refMod, tt.ourMod, tt.isGoodVar); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Compare() = %v, want %v (diff =  %s)", got, tt.want, cmp.Diff(got, tt.want))
			}
		})
	}
}
