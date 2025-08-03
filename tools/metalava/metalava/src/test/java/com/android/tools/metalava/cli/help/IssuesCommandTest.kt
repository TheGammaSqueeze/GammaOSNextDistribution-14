/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.metalava.cli.help

import com.android.tools.metalava.cli.common.BaseCommandTest
import org.junit.Test

class IssuesCommandTest : BaseCommandTest<HelpCommand>({ HelpCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("help", "issues")

            expectedStdout =
                """
Usage: metalava help issues <issue>?

  Provides help related to issues and issue reporting

Available Issues                             |  Category                |   Default Severity
---------------------------------------------+--------------------------+--------------------
  AbstractInner                              |  api_lint                |   warning
  AcronymName                                |  api_lint                |   warning
  ActionValue                                |  api_lint                |   error
  AddSealed                                  |  compatibility           |   error
  AddedAbstractMethod                        |  compatibility           |   error
  AddedClass                                 |  compatibility           |   hidden
  AddedField                                 |  compatibility           |   hidden
  AddedFinal                                 |  compatibility           |   error
  AddedFinalUninstantiable                   |  compatibility           |   hidden
  AddedInterface                             |  compatibility           |   hidden
  AddedMethod                                |  compatibility           |   hidden
  AddedPackage                               |  compatibility           |   hidden
  AddedReified                               |  compatibility           |   error
  AllUpper                                   |  api_lint                |   error
  AndroidUri                                 |  api_lint                |   error
  AnnotationExtraction                       |  unknown                 |   error
  ArrayReturn                                |  api_lint                |   warning
  AsyncSuffixFuture                          |  api_lint                |   error
  AutoBoxing                                 |  api_lint                |   error
  BadFuture                                  |  api_lint                |   error
  BannedThrow                                |  api_lint                |   error
  BecameUnchecked                            |  compatibility           |   error
  BothPackageInfoAndHtml                     |  documentation           |   warning
  BroadcastBehavior                          |  documentation           |   lint
  BrokenArtifactFile                         |  documentation           |   error
  BuilderSetStyle                            |  api_lint                |   warning
  CallbackInterface                          |  api_lint                |   hidden
  CallbackMethodName                         |  api_lint                |   error
  CallbackName                               |  api_lint                |   warning
  ChangedAbstract                            |  compatibility           |   error
  ChangedClass                               |  compatibility           |   error
  ChangedDefault                             |  compatibility           |   error
  ChangedDeprecated                          |  compatibility           |   hidden
  ChangedNative                              |  compatibility           |   hidden
  ChangedScope                               |  compatibility           |   error
  ChangedStatic                              |  compatibility           |   error
  ChangedSuperclass                          |  compatibility           |   error
  ChangedSynchronized                        |  compatibility           |   hidden
  ChangedThrows                              |  compatibility           |   error
  ChangedTransient                           |  compatibility           |   error
  ChangedType                                |  compatibility           |   error
  ChangedValue                               |  compatibility           |   error
  ChangedVolatile                            |  compatibility           |   error
  CompileTimeConstant                        |  api_lint                |   error
  ConcreteCollection                         |  api_lint                |   error
  ConfigFieldName                            |  api_lint                |   error
  ConflictingShowAnnotations                 |  unknown                 |   error
  ContextFirst                               |  api_lint                |   error
  ContextNameSuffix                          |  api_lint                |   error
  DefaultValueChange                         |  api_lint                |   error
  Deprecated                                 |  documentation           |   hidden
  DeprecatedOption                           |  unknown                 |   warning
  DeprecationMismatch                        |  documentation           |   error
  DocumentExceptions                         |  api_lint                |   error
  DuplicateSourceClass                       |  unknown                 |   warning
  EndsWithImpl                               |  api_lint                |   error
  Enum                                       |  api_lint                |   error
  EqualsAndHashCode                          |  api_lint                |   error
  ExceptionName                              |  api_lint                |   error
  ExecutorRegistration                       |  api_lint                |   warning
  ExpectedPlatformType                       |  unknown                 |   hidden
  ExtendsDeprecated                          |  unknown                 |   hidden
  ExtendsError                               |  api_lint                |   error
  FlaggedApiLiteral                          |  api_lint                |   hidden
  ForbiddenSuperClass                        |  api_lint                |   error
  ForbiddenTag                               |  unknown                 |   error
  FractionFloat                              |  api_lint                |   error
  FunRemoval                                 |  compatibility           |   error
  GenericCallbacks                           |  api_lint                |   error
  GenericException                           |  api_lint                |   error
  GetterOnBuilder                            |  api_lint                |   warning
  GetterSetterNames                          |  api_lint                |   error
  HeavyBitSet                                |  api_lint                |   error
  HiddenAbstractMethod                       |  unknown                 |   error
  HiddenSuperclass                           |  documentation           |   warning
  HiddenTypeParameter                        |  documentation           |   warning
  HiddenTypedefConstant                      |  unknown                 |   error
  IgnoringSymlink                            |  unknown                 |   info
  IllegalStateException                      |  api_lint                |   warning
  InfixRemoval                               |  compatibility           |   error
  IntDef                                     |  documentation           |   hidden
  IntentBuilderName                          |  api_lint                |   warning
  IntentName                                 |  api_lint                |   error
  InterfaceConstant                          |  api_lint                |   error
  InternalClasses                            |  api_lint                |   error
  InternalError                              |  unknown                 |   error
  InternalField                              |  api_lint                |   error
  InvalidFeatureEnforcement                  |  documentation           |   lint
  InvalidNullConversion                      |  compatibility           |   error
  InvalidNullabilityAnnotation               |  unknown                 |   error
  InvalidNullabilityAnnotationWarning        |  unknown                 |   warning
  InvalidNullabilityOverride                 |  api_lint                |   error
  InvalidSyntax                              |  unknown                 |   error
  IoError                                    |  unknown                 |   error
  KotlinDefaultParameterOrder                |  api_lint_androidx_misc  |   error
  KotlinKeyword                              |  api_lint                |   error
  KotlinOperator                             |  api_lint                |   info
  ListenerInterface                          |  api_lint                |   error
  ListenerLast                               |  api_lint                |   warning
  ManagerConstructor                         |  api_lint                |   error
  ManagerLookup                              |  api_lint                |   error
  MentionsGoogle                             |  api_lint                |   error
  MethodNameTense                            |  api_lint                |   warning
  MethodNameUnits                            |  api_lint                |   error
  MinMaxConstant                             |  api_lint                |   warning
  MissingBuildMethod                         |  api_lint                |   warning
  MissingColumn                              |  documentation           |   warning
  MissingGetterMatchingBuilder               |  api_lint                |   warning
  MissingJvmstatic                           |  api_lint                |   warning
  MissingNullability                         |  api_lint                |   error
  MissingPermission                          |  documentation           |   lint
  MultipleThreadAnnotations                  |  documentation           |   lint
  MutableBareField                           |  api_lint                |   error
  NoArtifactData                             |  documentation           |   hidden
  NoByteOrShort                              |  api_lint                |   warning
  NoClone                                    |  api_lint                |   error
  NoSettingsProvider                         |  api_lint                |   hidden
  NotCloseable                               |  api_lint                |   warning
  Nullable                                   |  documentation           |   hidden
  NullableCollection                         |  api_lint                |   warning
  OnNameExpected                             |  api_lint                |   warning
  OperatorRemoval                            |  compatibility           |   error
  OptionalBuilderConstructorArgument         |  api_lint                |   warning
  OverlappingConstants                       |  api_lint                |   warning
  PackageLayering                            |  api_lint                |   warning
  PairedRegistration                         |  api_lint                |   error
  ParameterNameChange                        |  compatibility           |   error
  ParcelConstructor                          |  api_lint                |   error
  ParcelCreator                              |  api_lint                |   error
  ParcelNotFinal                             |  api_lint                |   error
  ParcelableList                             |  api_lint                |   warning
  ParseError                                 |  unknown                 |   error
  PercentageInt                              |  api_lint                |   error
  PrivateSuperclass                          |  documentation           |   warning
  ProtectedMember                            |  api_lint                |   error
  PublicTypedef                              |  api_lint                |   error
  RawAidl                                    |  api_lint                |   error
  ReferencesDeprecated                       |  unknown                 |   hidden
  ReferencesHidden                           |  unknown                 |   error
  RegistrationName                           |  api_lint                |   error
  RemovedClass                               |  compatibility           |   error
  RemovedDeprecatedClass                     |  compatibility           |   inherit
  RemovedDeprecatedField                     |  compatibility           |   inherit
  RemovedDeprecatedMethod                    |  compatibility           |   inherit
  RemovedField                               |  compatibility           |   error
  RemovedFinal                               |  compatibility           |   error
  RemovedFinalStrict                         |  compatibility           |   error
  RemovedInterface                           |  compatibility           |   error
  RemovedJvmDefaultWithCompatibility         |  compatibility           |   error
  RemovedMethod                              |  compatibility           |   error
  RemovedPackage                             |  compatibility           |   error
  RequiresPermission                         |  documentation           |   lint
  ResourceFieldName                          |  api_lint                |   error
  ResourceStyleFieldName                     |  api_lint                |   error
  ResourceValueFieldName                     |  api_lint                |   error
  RethrowRemoteException                     |  api_lint                |   error
  ReturningUnexpectedConstant                |  unknown                 |   warning
  SamShouldBeLast                            |  api_lint                |   warning
  SdkConstant                                |  documentation           |   lint
  ServiceName                                |  api_lint                |   error
  SetterReturnsThis                          |  api_lint                |   warning
  ShowingMemberInHiddenClass                 |  unknown                 |   error
  SingleMethodInterface                      |  api_lint                |   error
  SingletonConstructor                       |  api_lint                |   error
  SingularCallback                           |  api_lint                |   error
  StartWithLower                             |  api_lint                |   error
  StartWithUpper                             |  api_lint                |   error
  StaticFinalBuilder                         |  api_lint                |   warning
  StaticUtils                                |  api_lint                |   error
  StreamFiles                                |  api_lint                |   warning
  SuperfluousPrefix                          |  unknown                 |   warning
  Todo                                       |  documentation           |   lint
  TopLevelBuilder                            |  api_lint                |   warning
  UnavailableSymbol                          |  documentation           |   warning
  UnflaggedApi                               |  api_lint                |   hidden
  UnhiddenSystemApi                          |  unknown                 |   error
  UniqueKotlinOperator                       |  api_lint                |   error
  UnmatchedMergeAnnotation                   |  unknown                 |   error
  UnresolvedClass                            |  documentation           |   lint
  UnresolvedImport                           |  unknown                 |   info
  UnresolvedLink                             |  documentation           |   lint
  UseIcu                                     |  api_lint                |   warning
  UseParcelFileDescriptor                    |  api_lint                |   error
  UserHandle                                 |  api_lint                |   warning
  UserHandleName                             |  api_lint                |   warning
  VarargRemoval                              |  compatibility           |   error
  VisiblySynchronized                        |  api_lint                |   error
"""
                    .trimIndent()
        }
    }

    @Test
    fun `Test issue help`() {
        commandTest {
            args += arrayOf("help", "issues", "AddedFinal")

            expectedStdout = "Under construction. No additional help available at the moment."
        }
    }

    @Test
    fun `Test unknown issue`() {
        commandTest {
            args += arrayOf("help", "issues", "AdddFinal")

            expectedStderr =
                """
                Aborting: Usage: metalava help issues <issue>?

                Error: no such issue: "AdddFinal". (Possible issues: AddedFinal, AddedField, AddedFinalUninstantiable)
            """
                    .trimIndent()
        }
    }
}
