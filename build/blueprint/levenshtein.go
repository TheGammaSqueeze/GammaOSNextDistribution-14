// Copyright 2021 Google Inc. All rights reserved.
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

package blueprint

import (
	"sort"
)

func abs(a int) int {
	if a < 0 {
		return -a
	}
	return a
}

// This implementation is written to be recursive, because
// we know Soong names are short, so we shouldn't hit the stack
// depth. Also, the buffer is indexed this way so that new
// allocations aren't needed.
func levenshtein(a, b string, ai, bi, max int, buf [][]int) int {
	if max == 0 {
		return 0
	}
	if ai >= len(a) {
		return len(b) - bi
	}
	if bi >= len(b) {
		return len(a) - ai
	}
	if buf[bi][ai] != 0 {
		return buf[bi][ai]
	}
	if abs(len(a)-len(b)) >= max {
		return max
	}
	var res = max
	if a[ai] == b[bi] {
		res = levenshtein(a, b, ai+1, bi+1, max, buf)
	} else {
		if c := levenshtein(a, b, ai+1, bi+1, max-1, buf); c < res {
			res = c // replace
		}
		if c := levenshtein(a, b, ai+1, bi, max-1, buf); c < res {
			res = c // delete from a
		}
		if c := levenshtein(a, b, ai, bi+1, max-1, buf); c < res {
			res = c // delete from b
		}
		res += 1
	}
	buf[bi][ai] = res
	return res
}

func stringIn(arr []string, str string) bool {
	for _, a := range arr {
		if a == str {
			return true
		}
	}
	return false
}

func namesLike(name string, unlike string, moduleGroups []*moduleGroup) []string {
	const kAllowedDifferences = 10
	buf := make([][]int, len(name)+kAllowedDifferences)
	for i := range buf {
		buf[i] = make([]int, len(name))
	}

	var best []string
	bestVal := kAllowedDifferences + 1

	for _, group := range moduleGroups {
		other := group.name

		if other == unlike {
			continue
		}

		l := levenshtein(name, other, 0, 0, kAllowedDifferences, buf)
		// fmt.Printf("levenshtein %q %q %d\n", name, other, l)

		// slightly better to use a min-heap
		if l == 0 {
			// these are the same, so it must be in a different namespace
			// ignore...
		} else if l < bestVal {
			bestVal = l
			best = []string{other}
		} else if l == bestVal && !stringIn(best, other) {
			best = append(best, other)
		}

		// zero buffer once used
		for _, v := range buf {
			for j := range v {
				v[j] = 0
			}
		}
	}

	sort.Strings(best)
	return best
}
