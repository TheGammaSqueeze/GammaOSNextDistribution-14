package mkcompare

import (
	"fmt"
	"io"
	"regexp"
	"sort"
	"strings"
)

// Classify takes two maps with string keys and return the lists of left-only, common, and right-only keys
func Classify[V interface{}](mLeft map[string]V, mRight map[string]V, varFilter func(_ string) bool) (left []string, common []string, right []string) {
	for k := range mLeft {
		if !varFilter(k) {
			break
		}
		if _, ok := mRight[k]; ok {
			common = append(common, k)
		} else {
			left = append(left, k)
		}
	}
	for k := range mRight {
		if !varFilter(k) {
			break
		}
		if _, ok := mLeft[k]; !ok {
			right = append(right, k)
		}
	}

	return left, common, right
}

var normalizer = map[string]func(ref, our string) (string, string){
	"LOCAL_SOONG_INSTALL_PAIRS":         normalizeInstallPairs,
	"LOCAL_COMPATIBILITY_SUPPORT_FILES": normalizeInstallPairs,
	"LOCAL_PREBUILT_MODULE_FILE":        normalizePrebuiltModuleFile,
	"LOCAL_SOONG_CLASSES_JAR":           normalizePrebuiltModuleFile,
	"LOCAL_SOONG_HEADER_JAR":            normalizePrebuiltModuleFile,
}

func normalizePrebuiltModuleFile(ref string, our string) (string, string) {
	return strings.ReplaceAll(ref, "/bazelCombined/", "/combined/"), strings.ReplaceAll(our, "/bazelCombined/", "/combined/")
}

var rexRemoveInstallSource = regexp.MustCompile("([^ ]+:)")

func normalizeInstallPairs(ref string, our string) (string, string) {
	return rexRemoveInstallSource.ReplaceAllString(ref, ""), rexRemoveInstallSource.ReplaceAllString(our, "")
}

type MkVarDiff struct {
	Name         string
	MissingItems []string `json:",omitempty"`
	ExtraItems   []string `json:",omitempty"`
}

// MkModuleDiff holds module difference between reference and our mkfile.
type MkModuleDiff struct {
	Ref          *MkModule   `json:"-"`
	Our          *MkModule   `json:"-"`
	MissingVars  []string    `json:",omitempty"`
	ExtraVars    []string    `json:",omitempty"`
	DiffVars     []MkVarDiff `json:",omitempty"`
	TypeDiffers  bool        `json:",omitempty"`
	ExtrasDiffer bool        `json:",omitempty"`
}

// Empty returns true if there is no difference
func (d *MkModuleDiff) Empty() bool {
	return !d.TypeDiffers && !d.ExtrasDiffer && len(d.MissingVars) == 0 && len(d.ExtraVars) == 0 && len(d.DiffVars) == 0
}

// Print prints the difference
func (d *MkModuleDiff) Print(sink io.Writer, name string) {
	if d.Empty() {
		return
	}
	fmt.Fprintf(sink, "%s (ref line %d, our line %d):\n", name, d.Ref.Location, d.Our.Location)
	if d.TypeDiffers {
		fmt.Fprintf(sink, "  type %s <-> %s\n", d.Ref.Type, d.Our.Type)
	}

	if !d.ExtrasDiffer {
		fmt.Fprintf(sink, "  extras %d <-> %d\n", d.Ref.Extras, d.Our.Extras)
	}

	if len(d.MissingVars)+len(d.DiffVars) > 0 {
		fmt.Fprintf(sink, "  variables:\n")
		if len(d.MissingVars) > 0 {
			fmt.Fprintf(sink, "    -%v\n", d.MissingVars)
		}
		if len(d.ExtraVars) > 0 {
			fmt.Fprintf(sink, "    +%v\n", d.ExtraVars)
		}
	}
	for _, vdiff := range d.DiffVars {
		fmt.Printf("   %s value:\n", vdiff.Name)
		if len(vdiff.MissingItems) > 0 {
			fmt.Printf("    -%v\n", vdiff.MissingItems)
		}
		if len(vdiff.ExtraItems) > 0 {
			fmt.Printf("    +%v\n", vdiff.ExtraItems)
		}
	}
}

// Compare returns the difference for a module. Only the variables filtered by the given
// function are considered.
func Compare(refMod *MkModule, ourMod *MkModule, varFilter func(string) bool) MkModuleDiff {
	d := MkModuleDiff{
		Ref:          refMod,
		Our:          ourMod,
		TypeDiffers:  refMod.Type != ourMod.Type,
		ExtrasDiffer: refMod.Extras != ourMod.Extras,
	}
	var common []string
	d.MissingVars, common, d.ExtraVars = Classify(d.Ref.Variables, d.Our.Variables, varFilter)

	if len(common) > 0 {
		for _, v := range common {
			doSort := true // TODO(asmundak): find if for some variables the value should not be sorted
			refValue := d.Ref.Variables[v]
			ourValue := d.Our.Variables[v]
			if f, ok := normalizer[v]; ok {
				refValue, ourValue = f(refValue, ourValue)
			}
			missingItems, extraItems := compareVariableValues(refValue, ourValue, doSort)
			if len(missingItems)+len(extraItems) > 0 {
				d.DiffVars = append(d.DiffVars, MkVarDiff{
					Name:         v,
					MissingItems: missingItems,
					ExtraItems:   extraItems,
				})
			}
		}
	}
	return d
}

func compareVariableValues(ref string, our string, sortItems bool) ([]string, []string) {
	refTokens := strings.Split(ref, " ")
	ourTokens := strings.Split(our, " ")
	if sortItems {
		sort.Strings(refTokens)
		sort.Strings(ourTokens)
	}
	var missing []string
	var extra []string
	refStream := &tokenStream{refTokens, 0}
	ourStream := &tokenStream{ourTokens, 0}
	refToken := refStream.next()
	ourToken := ourStream.next()
	compare := 0
	for refToken != tsEOF || ourToken != tsEOF {
		if refToken == tsEOF {
			compare = 1
		} else if ourToken == tsEOF {
			compare = -1
		} else {
			compare = 0
			if refToken <= ourToken {
				compare = -1
			}
			if refToken >= ourToken {
				compare = compare + 1
			}
		}
		switch compare {
		case -1:
			missing = append(missing, refToken)
			refToken = refStream.next()
		case 0:
			refToken = refStream.next()
			ourToken = ourStream.next()
		case 1:
			extra = append(extra, ourToken)
			ourToken = ourStream.next()
		}
	}
	return missing, extra
}

// Auxiliary stuff used to find the difference
const tsEOF = " "

type tokenStream struct {
	tokens  []string
	current int
}

func (ts *tokenStream) next() string {
	if ts.current >= len(ts.tokens) {
		return tsEOF
	}
	ret := ts.tokens[ts.current]
	ts.current = ts.current + 1
	return ret
}
