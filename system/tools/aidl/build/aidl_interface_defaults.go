package aidl

import (
	"android/soong/android"
)

func init() {
	android.RegisterModuleType("aidl_interface_defaults", AidlInterfaceDefaultsFactory)
}

type Defaults struct {
	android.ModuleBase
	android.DefaultsModuleBase
}

func (d *Defaults) GenerateAndroidBuildActions(ctx android.ModuleContext) {
}

func (d *Defaults) DepsMutator(ctx android.BottomUpMutatorContext) {
}

func AidlInterfaceDefaultsFactory() android.Module {
	module := &Defaults{}

	module.AddProperties(
		&aidlInterfaceProperties{},
	)

	android.InitDefaultsModule(module)

	return module
}
