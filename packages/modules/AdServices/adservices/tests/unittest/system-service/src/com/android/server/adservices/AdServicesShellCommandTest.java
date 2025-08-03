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
package com.android.server.adservices;

import static com.android.server.adservices.AdServicesShellCommand.CMD_IS_SYSTEM_SERVICE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.shell.IShellCommand;
import android.adservices.shell.IShellCommandCallback;
import android.adservices.shell.ShellCommandParam;
import android.adservices.shell.ShellCommandResult;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;
import com.android.server.adservices.AdServicesShellCommand.Injector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;

@SpyStatic(ActivityManager.class)
public final class AdServicesShellCommandTest extends AdServicesExtendedMockitoTestCase {

    private static final String HELP_ADSERVICES_SERVICE_CMDS =
            "echo <message> - prints the given message (useful to check cmd is working).";
    private static final String[] ALL_COMMANDS =
            new String[] {"help", CMD_IS_SYSTEM_SERVICE_ENABLED};
    private static final int SYSTEM_USER = UserHandle.SYSTEM.getIdentifier();

    private final StringWriter mOutStringWriter = new StringWriter();
    private final StringWriter mErrStringWriter = new StringWriter();

    private final PrintWriter mOut = new PrintWriter(mOutStringWriter);
    private final PrintWriter mErr = new PrintWriter(mErrStringWriter);

    @Mock private Flags mFlags;
    @Mock private IShellCommand mIShellCommand;

    private AdServicesShellCommand mShellCmd;

    private Injector mInjector;

    @Before
    public void setFixtures() {
        mInjector =
                new Injector() {
                    @Override
                    int getCallingUid() {
                        return Process.SHELL_UID;
                    }

                    @Override
                    IShellCommand getShellCommandService(Context context) {
                        return mIShellCommand;
                    }
                };
        mShellCmd =
                new AdServicesShellCommand(mInjector, mFlags, mMockContext) {
                    @Override
                    public PrintWriter getOutPrintWriter() {
                        return mOut;
                    }

                    @Override
                    public PrintWriter getErrPrintWriter() {
                        return mErr;
                    }
                };
        extendedMockito.mockGetCurrentUser(SYSTEM_USER);
        when(mMockContext.getUser()).thenReturn(UserHandle.SYSTEM);
    }

    @After
    public void closeStreams() {
        try {
            mOut.close();
        } finally {
            mErr.close();
        }
    }

    @Test
    public void testOnHelp() throws Exception {
        mShellCmd.onHelp();
        expectHelpOutputHasAllCommands(getOut());
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testOnCommand_wrongUid() {
        SecurityException e =
                assertThrows(
                        SecurityException.class,
                        () ->
                                new AdServicesShellCommand(
                                                new Injector() {
                                                    @Override
                                                    int getCallingUid() {
                                                        return 42;
                                                    }
                                                },
                                                mFlags,
                                                mMockContext)
                                        .onCommand("D'OH"));
        assertThat(e)
                .hasMessageThat()
                .isEqualTo(String.format(AdServicesShellCommand.WRONG_UID_TEMPLATE, 42));
    }

    @Test
    public void testExec_nullShowsHelp() throws Exception {
        mockHelpShellCommand();

        int result = runCmd((String[]) null);

        expect.withMessage("result").that(result).isEqualTo(0);
        expectHelpOutputHasAllCommands(getOut());
        expectHelpOutputHasMessages(getOut(), HELP_ADSERVICES_SERVICE_CMDS);
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_emptyShowsHelp() throws Exception {
        mockHelpShellCommand();
        int result = runCmd("");
        expect.withMessage("result").that(result).isEqualTo(0);

        expectHelpOutputHasAllCommands(getOut());
        expectHelpOutputHasMessages(getOut(), HELP_ADSERVICES_SERVICE_CMDS);
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_help() throws Exception {
        mockHelpShellCommand();
        int result = runCmd("help");
        expect.withMessage("result").that(result).isEqualTo(0);

        expectHelpOutputHasAllCommands(getOut());
        expectHelpOutputHasMessages(getOut(), HELP_ADSERVICES_SERVICE_CMDS);
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_helpShort() throws Exception {
        mockHelpShellCommand();
        int result = runCmd("-h");
        expect.withMessage("result").that(result).isEqualTo(0);

        expectHelpOutputHasAllCommands(getOut());
        expectHelpOutputHasMessages(getOut(), HELP_ADSERVICES_SERVICE_CMDS);
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_invalidCommand() throws Exception {
        String cmd = "D'OH!";
        String helpMsg = "Use -h for help.";
        ShellCommandResult responseInvalidShellCommand =
                new ShellCommandResult.Builder()
                        .setErr(String.format("Unsupported command: %s\n%s", cmd, helpMsg))
                        .setResultCode(-1)
                        .build();
        mockRunShellCommand(responseInvalidShellCommand, cmd);

        int result = runCmd(cmd);

        expect.withMessage("result").that(result).isEqualTo(-1);
        expect.withMessage("out").that(getOut()).isEmpty();
        expectHelpOutputHasMessages(getErr(), cmd, helpMsg);
    }

    @Test
    public void testExec_adServicesCommand_throwsRemoteException() throws Exception {
        String cmd = "echo";

        doThrow(new RemoteException()).when(mIShellCommand).runShellCommand(any(), any());

        int result = runCmd(cmd);

        expect.withMessage("result").that(result).isEqualTo(-1);
        expect.withMessage("out").that(getOut()).isEmpty();
        expect.withMessage("err").that(getErr()).contains("Remote exception occurred");
    }

    @Test
    public void testExec_adServicesCommand_timeoutHappens() throws Exception {
        String cmd = "xxx";

        int result = runCmd(cmd);

        expect.withMessage("result").that(result).isEqualTo(-1);
        expect.withMessage("out").that(getOut()).isEmpty();
        expect.withMessage("err").that(getErr()).contains("Timeout occurred");
    }

    @Test
    public void testExec_isSystemServiceEnabled_true() {
        mockAdServicesSystemServiceEnabled(true);
        int result = runCmd("is-system-service-enabled");
        expect.withMessage("result").that(result).isEqualTo(0);

        expect.withMessage("output of is-system-service-enabled")
                .that(getOut())
                .isEqualTo("true\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_false() {
        mockAdServicesSystemServiceEnabled(false);
        int result = runCmd("is-system-service-enabled");
        expect.withMessage("result").that(result).isEqualTo(0);

        expect.withMessage("output of is-system-service-enabled")
                .that(getOut())
                .isEqualTo("false\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_true_verbose() {
        mockAdServicesSystemServiceEnabled(true);
        int result = runCmd("is-system-service-enabled", "--verbose");
        expect.withMessage("result").that(result).isEqualTo(0);

        String out = getOut();
        expect.withMessage("output of is-system-service-enabled").that(out).isNotEqualTo("true\n");
        expect.withMessage("output of is-system-service-enabled")
                .that(out)
                .containsMatch(
                        ".*Enabled.*true.*Default.*"
                                + PhFlags.ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*DeviceConfig.*"
                                + PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_false_verbose() {
        mockAdServicesSystemServiceEnabled(false);
        int result = runCmd("is-system-service-enabled", "--verbose");
        expect.withMessage("result").that(result).isEqualTo(0);

        String out = getOut();
        expect.withMessage("output of is-system-service-enabled").that(out).isNotEqualTo("false\n");
        expect.withMessage("output of is-system-service-enabled")
                .that(out)
                .containsMatch(
                        ".*Enabled.*false.*Default.*"
                                + PhFlags.ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*DeviceConfig.*"
                                + PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_true_shortVerbose() {
        mockAdServicesSystemServiceEnabled(true);
        int result = runCmd("is-system-service-enabled", "-v");
        expect.withMessage("result").that(result).isEqualTo(0);

        String out = getOut();
        expect.withMessage("output of is-system-service-enabled").that(out).isNotEqualTo("true\n");
        expect.withMessage("output of is-system-service-enabled")
                .that(out)
                .containsMatch(
                        ".*Enabled.*true.*Default.*"
                                + PhFlags.ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*DeviceConfig.*"
                                + PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_false_shortVerbose() {
        mockAdServicesSystemServiceEnabled(false);
        int result = runCmd("is-system-service-enabled", "-v");
        expect.withMessage("result").that(result).isEqualTo(0);

        String out = getOut();
        expect.withMessage("output of is-system-service-enabled").that(out).isNotEqualTo("false\n");
        expect.withMessage("output of is-system-service-enabled")
                .that(out)
                .containsMatch(
                        ".*Enabled.*false.*Default.*"
                                + PhFlags.ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*DeviceConfig.*"
                                + PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_invalidOption() {
        int result = runCmd("is-system-service-enabled", "--D'OH!");
        expect.withMessage("result").that(result).isEqualTo(-1);

        expect.withMessage("out").that(getOut()).isEmpty();
        String err = getErr();
        expectHelpOutputHasMessages(err, CMD_IS_SYSTEM_SERVICE_ENABLED);
        expectHelpOutputHasMessages(err, "Invalid option");
        expectHelpOutputHasMessages(err, "--D'OH!");
    }

    @Test
    public void testExec_validAdServicesShellCommand_noArgs() throws Exception {
        String cmd = "CMD_XYZ";
        String out = "hello";
        ShellCommandResult response =
                new ShellCommandResult.Builder().setOut(out).setResultCode(0).build();
        mockRunShellCommand(response, cmd);

        int result = runCmd(cmd);

        expect.withMessage("result").that(result).isEqualTo(0);
        expect.withMessage("out").that(getOut()).contains(out);
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_validAdServicesShellCommand_withArgs() throws Exception {
        String cmd = "CMD_XYZ";
        String arg1 = "ARG1";
        String arg2 = "ARG2";
        String out = "hello";
        ShellCommandResult response =
                new ShellCommandResult.Builder().setOut(out).setResultCode(0).build();
        mockRunShellCommand(response, cmd, arg1, arg2);

        int result = runCmd(cmd, arg1, arg2);

        expect.withMessage("result").that(result).isEqualTo(0);
        expect.withMessage("out").that(getOut()).contains(out);
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_validAdServicesShellCommandWithTimeout() throws Exception {
        String cmd = "CMD_XYZ";
        String arg1 = "ARG1";
        String out = "hello";
        ShellCommandResult response =
                new ShellCommandResult.Builder().setOut(out).setResultCode(0).build();
        mockRunShellCommand(response, cmd, arg1);

        int result = runCmd(cmd, arg1, "--timeout", "1000");

        expect.withMessage("result").that(result).isEqualTo(0);
        expect.withMessage("out").that(getOut()).contains(out);
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_validAdServicesShellCommandWithTimeoutInAnyOrder() throws Exception {
        String cmd = "CMD_XYZ";
        String arg1 = "ARG1";
        String out = "hello";
        ShellCommandResult response =
                new ShellCommandResult.Builder().setOut(out).setResultCode(0).build();
        mockRunShellCommand(response, cmd, arg1);

        int result = runCmd(cmd, "--timeout", "1000", arg1);

        expect.withMessage("result").that(result).isEqualTo(0);
        expect.withMessage("out").that(getOut()).contains(out);
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_invalidTimeoutArgPresent() {
        // timeout value missing
        int result = runCmd("CMD_XYZ", "hello", "--timeout");
        expect.withMessage("timeout").that(result).isEqualTo(-1);
        expect.withMessage("out").that(getOut()).isEmpty();
        expect.withMessage("err").that(getErr()).contains("Argument expected after");

        // timeout value not an integer
        result = runCmd("CMD_XYZ", "hello", "--timeout", "abc");
        expect.withMessage("timeout").that(result).isEqualTo(-1);
        expect.withMessage("out").that(getOut()).isEmpty();
        expect.withMessage("err").that(getErr()).contains("Bad timeout value");
    }

    @Test
    public void testExec_validAdServicesShellCommand_secondaryUser_noArgs() throws Exception {
        when(mMockContext.getUser()).thenReturn(UserHandle.SYSTEM);
        when(mMockContext.createContextAsUser(eq(UserHandle.SYSTEM), anyInt()))
                .thenReturn(mMockContext);
        int secondaryUser = 10;
        extendedMockito.mockGetCurrentUser(secondaryUser);
        String cmd = "CMD_XYZ";
        String out = "hello";
        ShellCommandResult response =
                new ShellCommandResult.Builder().setOut(out).setResultCode(0).build();
        mockRunShellCommand(response, cmd);

        int result = runCmd(cmd);

        expect.withMessage("result").that(result).isEqualTo(0);
        expect.withMessage("out").that(getOut()).contains(out);
        expect.withMessage("err").that(getErr()).isEmpty();
        verify(mMockContext).createContextAsUser(eq(UserHandle.of(secondaryUser)), anyInt());
    }

    private void expectHelpOutputHasAllCommands(String helpOutput) {
        expectHelpOutputHasMessages(helpOutput, ALL_COMMANDS);
    }

    private void expectHelpOutputHasMessages(String helpOutput, String... messages) {
        for (String msg : messages) {
            expect.withMessage("help output").that(helpOutput).contains(msg);
        }
    }

    private int runCmd(String... args) {
        return mShellCmd.exec(
                /* target= */ null,
                FileDescriptor.in,
                FileDescriptor.out,
                FileDescriptor.err,
                args);
    }

    private String getOut() {
        mOut.flush();
        return mOutStringWriter.toString();
    }

    private String getErr() {
        mErr.flush();
        return mErrStringWriter.toString();
    }

    // TODO(b/294423183): use AdServicesFlagSetter (if / when it supports mocking unit tests)
    private void mockAdServicesSystemServiceEnabled(boolean value) {
        when(mFlags.getAdServicesSystemServiceEnabled()).thenReturn(value);
    }

    private void mockRunShellCommand(ShellCommandResult response, String... args) throws Exception {
        ShellCommandParam param = new ShellCommandParam(args);
        doAnswer(
                        invocation -> {
                            ((IShellCommandCallback) invocation.getArgument(1)).onResult(response);
                            return null;
                        })
                .when(mIShellCommand)
                .runShellCommand(eq(param), any());
    }

    private void mockHelpShellCommand() throws Exception {
        ShellCommandResult response =
                new ShellCommandResult.Builder().setOut(HELP_ADSERVICES_SERVICE_CMDS).build();
        mockRunShellCommand(response, "help");
    }
}
