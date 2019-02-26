/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liferay.blade.cli.command;

import com.liferay.blade.cli.BladeCLI;
import com.liferay.blade.cli.BladeTest;
import com.liferay.blade.cli.BladeTest.BladeTestBuilder;
import com.liferay.blade.cli.StringPrintStream;
import com.liferay.blade.cli.gradle.GradleExec;
import com.liferay.blade.cli.gradle.ProcessResult;

import java.io.File;

import java.util.HashMap;
import java.util.Map;

import org.easymock.EasyMock;
import org.easymock.IExpectationSetters;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;

/**
 * @author Christopher Bryan Boyd
 */
@PowerMockIgnore(
	{
		"java.util.ServiceLoader", "com.liferay.blade.cli.WorkspaceProvider",
		"com.liferay.blade.cli.gradle.GradleWorkspaceProvider"
	}
)
@PrepareForTest({InstallExtensionCommand.class, BladeCLI.class, BladeTest.class})
public class GradlePrintErrorTest {

	@Before
	public void setUp() throws Exception {
		_rootDir = temporaryFolder.getRoot();
		_extensionsDir = temporaryFolder.newFolder(".blade", "extensions");
	}

	@Test
	public void testGradleError() throws Exception {
		String[] args = {"extension", "install", "https://github.com/gamerson/blade-sample-command"};

		BladeTestBuilder bladeTestBuilder = BladeTest.builder();

		bladeTestBuilder.setExtensionsDir(_extensionsDir.toPath());
		bladeTestBuilder.setSettingsDir(_rootDir.toPath());
		bladeTestBuilder.setAssertErrors(false);

		BladeTest bladeTest = bladeTestBuilder.build();

		PowerMock.mockStaticPartialNice(BladeCLI.class, "getCommandMapByClassLoader");

		InstallExtensionCommand command = new InstallExtensionCommand();

		command.setArgs(new InstallExtensionArgs());

		Map<String, BaseCommand<? extends BaseArgs>> commandMap = new HashMap<>();

		commandMap.put("extension install", command);

		IExpectationSetters<Map<String, BaseCommand<? extends BaseArgs>>> expect = EasyMock.expect(
			BladeCLI.getCommandMapByClassLoader(EasyMock.anyString(), EasyMock.isA(ClassLoader.class)));

		expect.andReturn(commandMap).atLeastOnce();

		PowerMock.expectNew(
			GradleExec.class, EasyMock.isA(BladeTest.class)).andReturn(new GradleExecSpecial(bladeTest));

		PowerMock.replay(BladeCLI.class, GradleExec.class);

		bladeTest.run(args);

		StringPrintStream errPrintStream = (StringPrintStream)bladeTest.error();

		String error = errPrintStream.toString();

		error = error.toLowerCase();

		Assert.assertTrue(error, error.contains("build failed"));

		Assert.assertTrue(error, error.contains("foobar"));

		PowerMock.verifyAll();
	}

	@Rule
	public final PowerMockRule rule = new PowerMockRule();

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	private File _extensionsDir = null;
	private File _rootDir = null;

	private static class GradleExecSpecial extends GradleExec {

		public GradleExecSpecial(BladeCLI blade) {
			super(blade);
		}

		@Override
		public ProcessResult executeTask(String cmd, File dir) throws Exception {
			cmd = cmd.replace("assemble", "foobar");

			return super.executeTask(cmd, dir);
		}

	}

}