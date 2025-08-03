package bootstrap

import (
	"fmt"
	"path/filepath"
	"reflect"

	"github.com/google/blueprint"
	"github.com/google/blueprint/bootstrap/bpdoc"
	"github.com/google/blueprint/pathtools"
)

// ModuleTypeDocs returns a list of bpdoc.ModuleType objects that contain information relevant
// to generating documentation for module types supported by the primary builder.
func ModuleTypeDocs(ctx *blueprint.Context, factories map[string]reflect.Value) ([]*bpdoc.Package, error) {
	// Find the module that's marked as the "primary builder", which means it's
	// creating the binary that we'll use to generate the non-bootstrap
	// build.ninja file.
	var primaryBuilders []*GoBinary
	ctx.VisitAllModulesIf(isBootstrapBinaryModule,
		func(module blueprint.Module) {
			binaryModule := module.(*GoBinary)
			if binaryModule.properties.PrimaryBuilder {
				primaryBuilders = append(primaryBuilders, binaryModule)
			}
		})

	var primaryBuilder *GoBinary
	switch len(primaryBuilders) {
	case 0:
		return nil, fmt.Errorf("no primary builder module present")

	case 1:
		primaryBuilder = primaryBuilders[0]

	default:
		return nil, fmt.Errorf("multiple primary builder modules present")
	}

	pkgFiles := make(map[string][]string)
	ctx.VisitDepsDepthFirst(primaryBuilder, func(module blueprint.Module) {
		switch m := module.(type) {
		case (*GoPackage):
			pkgFiles[m.properties.PkgPath] = pathtools.PrefixPaths(m.properties.Srcs,
				filepath.Join(ctx.SrcDir(), ctx.ModuleDir(m)))
		default:
			panic(fmt.Errorf("unknown dependency type %T", module))
		}
	})

	mergedFactories := make(map[string]reflect.Value)
	for moduleType, factory := range factories {
		mergedFactories[moduleType] = factory
	}

	for moduleType, factory := range ctx.ModuleTypeFactories() {
		if _, exists := mergedFactories[moduleType]; !exists {
			mergedFactories[moduleType] = reflect.ValueOf(factory)
		}
	}

	return bpdoc.AllPackages(pkgFiles, mergedFactories, ctx.ModuleTypePropertyStructs())
}
