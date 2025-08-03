// Copyright 2017 Google Inc. All rights reserved.
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

package error_prone

import (
	"android/soong/java/config"
)

func init() {
	// These values are set into build/soong/java/config/config.go so that soong doesn't have any
	// references to external/error_prone, which may not always exist.
	config.ErrorProneClasspath = []string{
		"external/error_prone/error_prone/error_prone_core-2.23.0-with-dependencies.jar",
		"external/error_prone/error_prone/error_prone_annotations-2.23.0.jar",
		"external/error_prone/error_prone/error_prone_type_annotations-2.23.0.jar",
		"external/error_prone/checkerframework/dataflow-errorprone-3.39.0.jar",
		"external/error_prone/jFormatString/jFormatString-3.0.0.jar",
	}

	// The checks that are fatal to the build.
	config.ErrorProneChecksError = []string{
		// Errorprone default severity ERROR
		"-Xep:AndroidInjectionBeforeSuper:ERROR",
		"-Xep:ArrayFillIncompatibleType:ERROR",
		"-Xep:AsyncCallableReturnsNull:ERROR",
		"-Xep:AsyncFunctionReturnsNull:ERROR",
		"-Xep:AutoValueConstructorOrderChecker:ERROR",
		"-Xep:BundleDeserializationCast:ERROR",
		"-Xep:ChainingConstructorIgnoresParameter:ERROR",
		"-Xep:CheckReturnValue:ERROR",
		"-Xep:CollectionIncompatibleType:ERROR",
		"-Xep:ComparingThisWithNull:ERROR",
		"-Xep:ComparisonOutOfRange:ERROR",
		"-Xep:CompatibleWithAnnotationMisuse:ERROR",
		"-Xep:CompileTimeConstant:ERROR",
		"-Xep:ConstantOverflow:ERROR",
		"-Xep:DaggerProvidesNull:ERROR",
		"-Xep:DeadException:ERROR",
		"-Xep:DeadThread:ERROR",
		"-Xep:DoNotCall:ERROR",
		"-Xep:EqualsNaN:ERROR",
		"-Xep:EqualsWrongThing:ERROR",
		"-Xep:ErroneousThreadPoolConstructorChecker:ERROR",
		"-Xep:ForOverride:ERROR",
		"-Xep:FormatStringAnnotation:ERROR",
		"-Xep:FunctionalInterfaceMethodChanged:ERROR",
		"-Xep:FuturesGetCheckedIllegalExceptionType:ERROR",
		"-Xep:GuiceAssistedInjectScoping:ERROR",
		"-Xep:GuiceAssistedParameters:ERROR",
		"-Xep:GuiceInjectOnFinalField:ERROR",
		"-Xep:Immutable:ERROR",
		"-Xep:IncompatibleArgumentType:ERROR",
		"-Xep:IndexOfChar:ERROR",
		"-Xep:InexactVarargsConditional:ERROR",
		"-Xep:InfiniteRecursion:ERROR",
		"-Xep:InjectMoreThanOneScopeAnnotationOnClass:ERROR",
		"-Xep:InjectOnMemberAndConstructor:ERROR",
		"-Xep:InvalidPatternSyntax:ERROR",
		"-Xep:IsInstanceOfClass:ERROR",
		"-Xep:JUnit3TestNotRun:ERROR",
		"-Xep:JUnit4ClassAnnotationNonStatic:ERROR",
		"-Xep:JUnit4SetUpNotRun:ERROR",
		"-Xep:JUnit4TearDownNotRun:ERROR",
		"-Xep:JUnit4TestNotRun:ERROR",
		"-Xep:JUnitAssertSameCheck:ERROR",
		"-Xep:JavaxInjectOnAbstractMethod:ERROR",
		"-Xep:LiteByteStringUtf8:ERROR",
		"-Xep:LoopConditionChecker:ERROR",
		"-Xep:MissingTestCall:ERROR",
		"-Xep:MockitoUsage:ERROR",
		"-Xep:MoreThanOneInjectableConstructor:ERROR",
		"-Xep:MustBeClosedChecker:ERROR",
		"-Xep:NCopiesOfChar:ERROR",
		"-Xep:NonCanonicalStaticImport:ERROR",
		"-Xep:NonFinalCompileTimeConstant:ERROR",
		"-Xep:OptionalEquality:ERROR",
		"-Xep:OverlappingQualifierAndScopeAnnotation:ERROR",
		"-Xep:PackageInfo:ERROR",
		"-Xep:PrivateSecurityContractProtoAccess:ERROR",
		"-Xep:ProtoFieldNullComparison:ERROR",
		"-Xep:ProtoStringFieldReferenceEquality:ERROR",
		"-Xep:ProvidesMethodOutsideOfModule:ERROR",
		"-Xep:RandomCast:ERROR",
		"-Xep:RestrictedApi:ERROR",
		"-Xep:SelfAssignment:ERROR",
		"-Xep:ShouldHaveEvenArgs:ERROR",
		"-Xep:StreamToString:ERROR",
		"-Xep:SubstringOfZero:ERROR",
		"-Xep:SuppressWarningsDeprecated:ERROR",
		"-Xep:ThrowIfUncheckedKnownChecked:ERROR",
		"-Xep:ThrowNull:ERROR",
		"-Xep:TruthSelfEquals:ERROR",
		"-Xep:TypeParameterQualifier:ERROR",
		"-Xep:UnnecessaryTypeArgument:ERROR",
		"-Xep:UnusedAnonymousClass:ERROR",
		"-Xep:VarTypeName:ERROR",
		// Errorprone default severity WARNING
		// Errorprone default severity SUGGESTION
	}

	// The checks that are not fatal to the build.
	config.ErrorProneChecksWarning = []string{
		// Errorprone default severity ERROR
		"-Xep:BadAnnotationImplementation:WARN",
		"-Xep:BadShiftAmount:WARN",
		"-Xep:BanJNDI:WARN",
		"-Xep:BareDotMetacharacter:WARN",
		"-Xep:BoxedPrimitiveEquality:WARN",
		"-Xep:ComparableType:WARN",
		"-Xep:ComparisonOutOfRange:WARN",
		"-Xep:ComplexBooleanConstant:WARN",
		"-Xep:CollectionToArraySafeParameter:WARN",
		"-Xep:ConditionalExpressionNumericPromotion:WARN",
		"-Xep:DangerousLiteralNull:WARN",
		"-Xep:DoubleBraceInitialization:WARN",
		"-Xep:DurationFrom:WARN",
		"-Xep:DurationTemporalUnit:WARN",
		"-Xep:EmptyTopLevelDeclaration:WARN",
		"-Xep:EqualsHashCode:WARN",
		"-Xep:EqualsNull:WARN",
		"-Xep:EqualsReference:WARN",
		"-Xep:FormatString:WARN",
		"-Xep:FromTemporalAccessor:WARN",
		"-Xep:GetClassOnAnnotation:WARN",
		"-Xep:GetClassOnClass:WARN",
		"-Xep:GuardedBy:WARN",
		"-Xep:HashtableContains:WARN",
		"-Xep:IdentityBinaryExpression:WARN",
		"-Xep:IdentityHashMapBoxing:WARN",
		"-Xep:IgnoredPureGetter:WARN",
		"-Xep:ImmutableAnnotationChecker:WARN",
		"-Xep:ImmutableEnumChecker:WARN",
		"-Xep:InstantTemporalUnit:WARN",
		"-Xep:InvalidTimeZoneID:WARN",
		"-Xep:InvalidZoneId:WARN",
		"-Xep:IsInstanceIncompatibleType:WARN",
		"-Xep:IsLoggableTagLength:WARN",
		"-Xep:JUnitParameterMethodNotFound:WARN",
		"-Xep:LenientFormatStringValidation:WARN",
		"-Xep:LockOnBoxedPrimitive:WARN",
		"-Xep:MathRoundIntLong:WARN",
		"-Xep:MislabeledAndroidString:WARN",
		"-Xep:MisusedDayOfYear:WARN",
		"-Xep:MissingSuperCall:WARN",
		"-Xep:MisusedWeekYear:WARN",
		"-Xep:ModifyingCollectionWithItself:WARN",
		"-Xep:NoCanIgnoreReturnValueOnClasses:WARN",
		"-Xep:NonRuntimeAnnotation:WARN",
		"-Xep:NullableOnContainingClass:WARN",
		"-Xep:NullTernary:WARN",
		"-Xep:OverridesJavaxInjectableMethod:WARN",
		"-Xep:ParcelableCreator:WARN",
		"-Xep:PeriodFrom:WARN",
		"-Xep:PreconditionsInvalidPlaceholder:WARN",
		"-Xep:ProtoBuilderReturnValueIgnored:WARN",
		"-Xep:ProtocolBufferOrdinal:WARN",
		"-Xep:ProtoFieldNullComparison:WARN",
		"-Xep:RandomModInteger:WARN",
		"-Xep:RectIntersectReturnValueIgnored:WARN",
		"-Xep:ReturnValueIgnored:WARN",
		"-Xep:SelfAssignment:WARN",
		"-Xep:SelfComparison:WARN",
		"-Xep:SelfEquals:WARN",
		"-Xep:SizeGreaterThanOrEqualsZero:WARN",
		"-Xep:StringBuilderInitWithChar:WARN",
		"-Xep:TreeToString:WARN",
		"-Xep:TryFailThrowable:WARN",
		"-Xep:UnnecessaryCheckNotNull:WARN",
		"-Xep:UnusedCollectionModifiedInPlace:WARN",
		"-Xep:XorPower:WARN",
	}

	// The checks that are default-disabled
	config.ErrorProneChecksDefaultDisabled = []string{}

	config.ErrorProneChecksOff = []string{
		// We are not interested in Guava recommendations
		// for String.split.
		"-Xep:StringSplitter:OFF",
		// b/117877739.
		"-Xep:TruthAssertExpected:OFF",
		// b/170172949
		"-Xep:MissingOverride:OFF",
		"-Xep:UnnecessaryParentheses:OFF",
		// This triggers on android R classes, disable until we can ignore
		// the generated R classes
		"-Xep:MutablePublicArray:OFF",
		// These checks crash
		"-Xep:RethrowReflectiveOperationExceptionAsLinkageError:OFF",
		"-Xep:InvalidLink:OFF",
		// Commonly triggers for stubbed methods
		"-Xep:DoNotCallSuggester:OFF",
		"-Xep:MissingSummary:OFF",
		// This check increates the `platformprotos` module's build
		// time by ~15 minutes
		"-Xep:SameNameButDifferent:OFF",
		// Noisy and requires projects to add a dependency on errorprone annotations
		"-Xep:CanIgnoreReturnValueSuggester:OFF",
		// Data classes are encouraged to override toString(), but it is not a strict
		// requirement. The warning is overtriggered when source depends on the API stubs, which
		// may not include the toString() method.
		"-Xep:ObjectToString:OFF",
	}

	config.ErrorProneFlags = []string{
		"-Xdiags:verbose",
		"-XDcompilePolicy=simple",
		"-XDallowBetterNullChecks=false",
		"-XDusePolyAttribution=true",
		"-XDuseStrictMethodClashCheck=true",
		"-XDuseStructuralMostSpecificResolution=true",
		"-XDuseGraphInference=true",
		"-XDandroidCompatible=true",
		// As we emit errors as warnings,
		// increase the warning limit.
		"-Xmaxwarns 9999999",

		// Extra flags needed by ErrorProne for OpenJDK9 from
		// http://errorprone.info/docs/installation
		"-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
		"-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
		"-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
		"-J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
		"-J--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
		"-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
		"-J--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
		"-J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
		"-J--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
		"-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
	}
}
