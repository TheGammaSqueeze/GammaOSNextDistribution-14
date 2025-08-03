package display

import (
	"android/soong/android"
	"android/soong/cc"

	"github.com/google/blueprint/proptools"
)

func init() {
	android.RegisterModuleType("display_go_defaults_sm7250", display_DefaultsFactory)
}

func display_DefaultsFactory() android.Module {
	module := cc.DefaultsFactory()
	android.AddLoadHook(module, display_Defaults)
	return module
}

func display_Defaults(ctx android.LoadHookContext) {
	type props struct {
		Enabled *bool
	}
	p := &props{}
	p.Enabled = display_globalDefaults(ctx)
	ctx.AppendProperties(p)
}

func display_globalDefaults(ctx android.LoadHookContext) *bool {
	var module_enabled *bool

	if android.ExistentPathForSource(ctx, "vendor/qcom/opensource/commonsys-intf/display/Android.bp").Valid() == false {
		module_enabled = proptools.BoolPtr(false)
	}

	return module_enabled
}
