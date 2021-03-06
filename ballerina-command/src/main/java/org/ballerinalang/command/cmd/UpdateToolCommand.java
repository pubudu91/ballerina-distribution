/*
 * Copyright (c) 2019, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ballerinalang.command.cmd;

import org.ballerinalang.command.BallerinaCliCommands;
import org.ballerinalang.command.util.ToolUtil;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * This class represents the "Update" command and it holds arguments and flags specified by the user.
 *
 * @since 0.8.0
 */
@CommandLine.Command(name = "command", description = "Update Ballerina current cli tool commands")
public class UpdateToolCommand extends Command implements BCommand {

    @CommandLine.Option(names = {"--help", "-h", "?"}, hidden = true)
    private boolean helpFlag;

    private CommandLine parentCmdParser;

    public UpdateToolCommand(PrintStream printStream) {
        super(printStream);
    }

    public void execute() {
        if (helpFlag) {
            printUsageInfo(BallerinaCliCommands.UPDATE);
            return;
        }
        updateCommands(getPrintStream());
    }

    @Override
    public String getName() {
        return BallerinaCliCommands.UPDATE;
    }

    @Override
    public void printLongDesc(StringBuilder out) {
        out.append("Updates the ballerina cli commands to latest version. \n");
    }

    @Override
    public void printUsage(StringBuilder out) {
        out.append("  ballerina command\n");
    }

    @Override
    public void setParentCmdParser(CommandLine parentCmdParser) {
        this.parentCmdParser = parentCmdParser;
    }

    private static void updateCommands(PrintStream printStream) {
        try {
            String version = ToolUtil.getCurrentToolsVersion();
            String latestVersion = ToolUtil.getLatestToolVersion();
            if (!latestVersion.equals(version)) {
                ToolUtil.downloadTool(printStream, latestVersion);
                printStream.println("Using tool version: " + latestVersion);
            } else {
                printStream.println("No update found");
            }
        } catch (IOException | KeyManagementException | NoSuchAlgorithmException e) {
            printStream.println("Cannot connect to the central server");
        }
    }
}
