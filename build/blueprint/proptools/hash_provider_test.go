package proptools

import (
	"strings"
	"testing"
)

func mustHash(t *testing.T, provider interface{}) uint64 {
	t.Helper()
	result, err := HashProvider(provider)
	if err != nil {
		t.Fatal(err)
	}
	return result
}

func TestHashingMapGetsSameResults(t *testing.T) {
	provider := map[string]string{"foo": "bar", "baz": "qux"}
	first := mustHash(t, provider)
	second := mustHash(t, provider)
	third := mustHash(t, provider)
	fourth := mustHash(t, provider)
	if first != second || second != third || third != fourth {
		t.Fatal("Did not get the same result every time for a map")
	}
}

func TestHashingNonSerializableTypesFails(t *testing.T) {
	testCases := []struct {
		name     string
		provider interface{}
	}{
		{
			name:     "function pointer",
			provider: []func(){nil},
		},
		{
			name:     "channel",
			provider: []chan int{make(chan int)},
		},
		{
			name:     "list with non-serializable type",
			provider: []interface{}{"foo", make(chan int)},
		},
	}
	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			_, err := HashProvider(testCase)
			if err == nil {
				t.Fatal("Expected hashing error but didn't get one")
			}
			expected := "providers may only contain primitives, strings, arrays, slices, structs, maps, and pointers"
			if !strings.Contains(err.Error(), expected) {
				t.Fatalf("Expected %q, got %q", expected, err.Error())
			}
		})
	}
}

func TestHashSuccessful(t *testing.T) {
	testCases := []struct {
		name     string
		provider interface{}
	}{
		{
			name:     "int",
			provider: 5,
		},
		{
			name:     "string",
			provider: "foo",
		},
		{
			name:     "*string",
			provider: StringPtr("foo"),
		},
		{
			name:     "array",
			provider: [3]string{"foo", "bar", "baz"},
		},
		{
			name:     "slice",
			provider: []string{"foo", "bar", "baz"},
		},
		{
			name: "struct",
			provider: struct {
				foo string
				bar int
			}{
				foo: "foo",
				bar: 3,
			},
		},
		{
			name: "map",
			provider: map[string]int{
				"foo": 3,
				"bar": 4,
			},
		},
		{
			name:     "list of interfaces with different types",
			provider: []interface{}{"foo", 3, []string{"bar", "baz"}},
		},
	}
	for _, testCase := range testCases {
		t.Run(testCase.name, func(t *testing.T) {
			mustHash(t, testCase.provider)
		})
	}
}
