// Copyright 2020 Google Inc. All rights reserved.
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
	"fmt"

	"github.com/google/blueprint/proptools"
)

// This file implements Providers, modelled after Bazel
// (https://docs.bazel.build/versions/master/skylark/rules.html#providers).
// Each provider can be associated with a mutator, in which case the value for the provider for a
// module can only be set during the mutator call for the module, and the value can only be
// retrieved after the mutator call for the module. For providers not associated with a mutator, the
// value can for the provider for a module can only be set during GenerateBuildActions for the
// module, and the value can only be retrieved after GenerateBuildActions for the module.
//
// Providers are globally registered during init() and given a unique ID.  The value of a provider
// for a module is stored in an []any indexed by the ID.  If the value of a provider has
// not been set, the value in the []any will be nil.
//
// If the storage used by the provider value arrays becomes too large:
//  sizeof([]interface) * number of providers * number of modules that have a provider value set
// then the storage can be replaced with something like a bitwise trie.
//
// The purpose of providers is to provide a serializable checkpoint between modules to enable
// Blueprint to skip parts of the analysis phase when inputs haven't changed.  To that end,
// values passed to providers should be treated as immutable by callers to both the getters and
// setters.  Go doesn't provide any way to enforce immutability on arbitrary types, so it may be
// necessary for the getters and setters to make deep copies of the values, likely extending
// proptools.CloneProperties to do so.

type typedProviderKey[K any] struct {
	providerKey
}

type providerKey struct {
	id      int
	typ     string
	mutator string
}

func (p *providerKey) provider() *providerKey { return p }

type AnyProviderKey interface {
	provider() *providerKey
}
type ProviderKey[K any] struct {
	*typedProviderKey[K]
}

var _ AnyProviderKey = (*providerKey)(nil)
var _ AnyProviderKey = ProviderKey[bool]{}

var providerRegistry []*providerKey

// NewProvider returns a ProviderKey for the given type.
//
// The returned ProviderKey can be used to set a value of the ProviderKey's type for a module
// inside GenerateBuildActions for the module, and to get the value from GenerateBuildActions from
// any module later in the build graph.
func NewProvider[K any]() ProviderKey[K] {
	return NewMutatorProvider[K]("")
}

// NewMutatorProvider returns a ProviderKey for the given type.
//
// The returned ProviderKey can be used to set a value of the ProviderKey's type for a module inside
// the given mutator for the module, and to get the value from GenerateBuildActions from any
// module later in the build graph in the same mutator, or any module in a later mutator or during
// GenerateBuildActions.
func NewMutatorProvider[K any](mutator string) ProviderKey[K] {
	checkCalledFromInit()

	typ := fmt.Sprintf("%T", *new(K))

	provider := ProviderKey[K]{
		typedProviderKey: &typedProviderKey[K]{
			providerKey: providerKey{
				id:      len(providerRegistry),
				typ:     typ,
				mutator: mutator,
			},
		},
	}

	providerRegistry = append(providerRegistry, &provider.providerKey)

	return provider
}

// initProviders fills c.providerMutators with the *mutatorInfo associated with each provider ID,
// if any.
func (c *Context) initProviders() {
	c.providerMutators = make([]*mutatorInfo, len(providerRegistry))
	for _, provider := range providerRegistry {
		for _, mutator := range c.mutatorInfo {
			if mutator.name == provider.mutator {
				c.providerMutators[provider.id] = mutator
			}
		}
	}
}

// setProvider sets the value for a provider on a moduleInfo.  Verifies that it is called during the
// appropriate mutator or GenerateBuildActions pass for the provider, and that the value is of the
// appropriate type.  The value should not be modified after being passed to setProvider.
//
// Once Go has generics the value parameter can be typed:
// setProvider(type T)(m *moduleInfo, provider ProviderKey(T), value T)
func (c *Context) setProvider(m *moduleInfo, provider *providerKey, value any) {
	if provider.mutator == "" {
		if !m.startedGenerateBuildActions {
			panic(fmt.Sprintf("Can't set value of provider %s before GenerateBuildActions started",
				provider.typ))
		} else if m.finishedGenerateBuildActions {
			panic(fmt.Sprintf("Can't set value of provider %s after GenerateBuildActions finished",
				provider.typ))
		}
	} else {
		expectedMutator := c.providerMutators[provider.id]
		if expectedMutator == nil {
			panic(fmt.Sprintf("Can't set value of provider %s associated with unregistered mutator %s",
				provider.typ, provider.mutator))
		} else if c.mutatorFinishedForModule(expectedMutator, m) {
			panic(fmt.Sprintf("Can't set value of provider %s after mutator %s finished",
				provider.typ, provider.mutator))
		} else if !c.mutatorStartedForModule(expectedMutator, m) {
			panic(fmt.Sprintf("Can't set value of provider %s before mutator %s started",
				provider.typ, provider.mutator))
		}
	}

	if m.providers == nil {
		m.providers = make([]any, len(providerRegistry))
	}

	if m.providers[provider.id] != nil {
		panic(fmt.Sprintf("Value of provider %s is already set", provider.typ))
	}

	m.providers[provider.id] = value

	if c.verifyProvidersAreUnchanged {
		if m.providerInitialValueHashes == nil {
			m.providerInitialValueHashes = make([]uint64, len(providerRegistry))
		}
		hash, err := proptools.HashProvider(value)
		if err != nil {
			panic(fmt.Sprintf("Can't set value of provider %s: %s", provider.typ, err.Error()))
		}
		m.providerInitialValueHashes[provider.id] = hash
	}
}

// provider returns the value, if any, for a given provider for a module.  Verifies that it is
// called after the appropriate mutator or GenerateBuildActions pass for the provider on the module.
// If the value for the provider was not set it returns nil.  The return value should always be considered read-only.
//
// Once Go has generics the return value can be typed and the type assert by callers can be dropped:
// provider(type T)(m *moduleInfo, provider ProviderKey(T)) T
func (c *Context) provider(m *moduleInfo, provider *providerKey) (any, bool) {
	if provider.mutator == "" {
		if !m.finishedGenerateBuildActions {
			panic(fmt.Sprintf("Can't get value of provider %s before GenerateBuildActions finished",
				provider.typ))
		}
	} else {
		expectedMutator := c.providerMutators[provider.id]
		if expectedMutator != nil && !c.mutatorFinishedForModule(expectedMutator, m) {
			panic(fmt.Sprintf("Can't get value of provider %s before mutator %s finished",
				provider.typ, provider.mutator))
		}
	}

	if len(m.providers) > provider.id {
		if p := m.providers[provider.id]; p != nil {
			return p, true
		}
	}

	return nil, false
}

func (c *Context) mutatorFinishedForModule(mutator *mutatorInfo, m *moduleInfo) bool {
	if c.finishedMutators[mutator] {
		// mutator pass finished for all modules
		return true
	}

	if c.startedMutator == mutator {
		// mutator pass started, check if it is finished for this module
		return m.finishedMutator == mutator
	}

	// mutator pass hasn't started
	return false
}

func (c *Context) mutatorStartedForModule(mutator *mutatorInfo, m *moduleInfo) bool {
	if c.finishedMutators[mutator] {
		// mutator pass finished for all modules
		return true
	}

	if c.startedMutator == mutator {
		// mutator pass is currently running
		if m.startedMutator == mutator {
			// mutator has started for this module
			return true
		}
	}

	return false
}

// OtherModuleProviderContext is a helper interface that is a subset of ModuleContext, BottomUpMutatorContext, or
// TopDownMutatorContext for use in OtherModuleProvider.
type OtherModuleProviderContext interface {
	OtherModuleProvider(m Module, provider AnyProviderKey) (any, bool)
}

var _ OtherModuleProviderContext = BaseModuleContext(nil)
var _ OtherModuleProviderContext = ModuleContext(nil)
var _ OtherModuleProviderContext = BottomUpMutatorContext(nil)
var _ OtherModuleProviderContext = TopDownMutatorContext(nil)

// OtherModuleProvider reads the provider for the given module.  If the provider has been set the value is
// returned and the boolean is true.  If it has not been set the zero value of the provider's type  is returned
// and the boolean is false.  The value returned may be a deep copy of the value originally passed to SetProvider.
//
// OtherModuleProviderContext is a helper interface that accepts ModuleContext, BottomUpMutatorContext, or
// TopDownMutatorContext.
func OtherModuleProvider[K any](ctx OtherModuleProviderContext, module Module, provider ProviderKey[K]) (K, bool) {
	value, ok := ctx.OtherModuleProvider(module, provider)
	if !ok {
		var k K
		return k, false
	}
	return value.(K), ok
}

// SingletonModuleProviderContext is a helper interface that is a subset of Context and SingletonContext for use in
// SingletonModuleProvider.
type SingletonModuleProviderContext interface {
	ModuleProvider(m Module, provider AnyProviderKey) (any, bool)
}

var _ SingletonModuleProviderContext = &Context{}
var _ SingletonModuleProviderContext = SingletonContext(nil)

// SingletonModuleProvider reads the provider for the given module.  If the provider has been set the value is
// returned and the boolean is true.  If it has not been set the zero value of the provider's type  is returned
// and the boolean is false.  The value returned may be a deep copy of the value originally passed to SetProvider.
//
// SingletonModuleProviderContext is a helper interface that accepts Context or SingletonContext.
func SingletonModuleProvider[K any](ctx SingletonModuleProviderContext, module Module, provider ProviderKey[K]) (K, bool) {
	value, ok := ctx.ModuleProvider(module, provider)
	if !ok {
		var k K
		return k, false
	}
	return value.(K), ok
}

// ModuleProviderContext is a helper interface that is a subset of ModuleContext, BottomUpMutatorContext, or
// TopDownMutatorContext for use in ModuleProvider.
type ModuleProviderContext interface {
	Provider(provider AnyProviderKey) (any, bool)
}

var _ ModuleProviderContext = BaseModuleContext(nil)
var _ ModuleProviderContext = ModuleContext(nil)
var _ ModuleProviderContext = BottomUpMutatorContext(nil)
var _ ModuleProviderContext = TopDownMutatorContext(nil)

// ModuleProvider reads the provider for the current module.  If the provider has been set the value is
// returned and the boolean is true.  If it has not been set the zero value of the provider's type  is returned
// and the boolean is false.  The value returned may be a deep copy of the value originally passed to SetProvider.
//
// ModuleProviderContext is a helper interface that accepts ModuleContext, BottomUpMutatorContext, or
// TopDownMutatorContext.
func ModuleProvider[K any](ctx ModuleProviderContext, provider ProviderKey[K]) (K, bool) {
	value, ok := ctx.Provider(provider)
	if !ok {
		var k K
		return k, false
	}
	return value.(K), ok
}

// SetProviderContext is a helper interface that is a subset of ModuleContext, BottomUpMutatorContext, or
// TopDownMutatorContext for use in SetProvider.
type SetProviderContext interface {
	SetProvider(provider AnyProviderKey, value any)
}

var _ SetProviderContext = BaseModuleContext(nil)
var _ SetProviderContext = ModuleContext(nil)
var _ SetProviderContext = BottomUpMutatorContext(nil)
var _ SetProviderContext = TopDownMutatorContext(nil)

// SetProvider sets the value for a provider for the current module.  It panics if not called
// during the appropriate mutator or GenerateBuildActions pass for the provider, if the value
// is not of the appropriate type, or if the value has already been set.  The value should not
// be modified after being passed to SetProvider.
//
// SetProviderContext is a helper interface that accepts ModuleContext, BottomUpMutatorContext, or
// TopDownMutatorContext.
func SetProvider[K any](ctx SetProviderContext, provider ProviderKey[K], value K) {
	ctx.SetProvider(provider, value)
}
