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

package com.liferay.blade.extensions.maven.profile;

import aQute.lib.io.IO;

import com.liferay.blade.cli.TestUtil;
import com.liferay.blade.extensions.maven.profile.internal.MavenUtil;

import java.io.File;
import java.io.FileInputStream;

import java.nio.file.Files;

import java.util.Properties;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author Gregory Amerson
 */
public class InitCommandMavenTest {

	@Before
	public void setUp() throws Exception {
		_workspaceDir = temporaryFolder.newFolder("build", "test", "workspace");

		_extensionsDir = temporaryFolder.newFolder(".blade", "extensions");
	}

	@Test
	public void testMavenInitProjectCorrectLocation() throws Exception {
		String[] args = {"--base", _workspaceDir.getPath(), "init", "-P", "maven", "mavenworkspace"};

		File mavenworkspace = new File(_workspaceDir, "mavenworkspace");

		TestUtil.runBlade(mavenworkspace, _extensionsDir, args);

		Assert.assertTrue(mavenworkspace.exists());

		args = new String[] {"--base", mavenworkspace.getPath(), "create", "-t", "portlet", "project1"};

		TestUtil.runBlade(mavenworkspace, _extensionsDir, args);

		File projectDirectory = new File(mavenworkspace, "modules/project1");

		Assert.assertTrue(projectDirectory.exists());

		File projectPomFile = new File(projectDirectory, "pom.xml");

		Assert.assertTrue(projectPomFile.exists());
	}

	@Test
	public void testMavenInitWithNameWorkspaceDirectoryEmpty() throws Exception {
		String[] args = {"--base", _workspaceDir.getPath(), "init", "-P", "maven", "newproject"};

		File newproject = new File(_workspaceDir, "newproject");

		Assert.assertTrue(newproject.mkdirs());

		TestUtil.runBlade(newproject, _extensionsDir, args);

		Assert.assertTrue(new File(newproject, "pom.xml").exists());

		Assert.assertTrue(new File(newproject, "modules").exists());

		String contents = new String(Files.readAllBytes(new File(newproject, "pom.xml").toPath()));

		Assert.assertTrue(contents, contents.contains("com.liferay.portal.tools.bundle.support"));

		File metadataFile = new File(_workspaceDir, "newproject/.blade/settings.properties");

		Assert.assertTrue(metadataFile.exists());

		Properties settingsProperties = new Properties();

		FileInputStream settingsInputStream = new FileInputStream(metadataFile);

		settingsProperties.load(settingsInputStream);

		String profile = settingsProperties.getProperty("profile.name");

		Assert.assertEquals("maven", profile);
	}

	@Test
	public void testMavenInitWithNameWorkspaceDirectoryHasFiles() throws Exception {
		String[] args = {"--base", _workspaceDir.getPath(), "init", "-P", "maven", "newproject"};

		File projectDir = new File(_workspaceDir, "newproject");

		Assert.assertTrue(projectDir.mkdirs());

		Assert.assertTrue(new File(_workspaceDir, "newproject/foo").createNewFile());

		TestUtil.runBlade(_workspaceDir, _extensionsDir, false, args);

		Assert.assertFalse(new File(_workspaceDir, "newproject/pom.xml").exists());
	}

	@Test
	public void testMavenInitWithNameWorkspaceNotExists() throws Exception {
		String[] args = {"--base", _workspaceDir.getPath(), "init", "-P", "maven", "newproject"};

		File projectDir = new File(_workspaceDir, "newproject");

		TestUtil.runBlade(projectDir, _extensionsDir, args);

		Assert.assertTrue(new File(_workspaceDir, "newproject/pom.xml").exists());

		Assert.assertTrue(new File(_workspaceDir, "newproject/modules").exists());
	}

	@Test
	public void testMavenInitWorkspaceDirectoryEmpty() throws Exception {
		String[] args = {"--base", _workspaceDir.getPath(), "init", "-P", "maven"};

		TestUtil.runBlade(_workspaceDir, _extensionsDir, args);

		Assert.assertTrue(new File(_workspaceDir, "pom.xml").exists());

		Assert.assertTrue(new File(_workspaceDir, "modules").exists());

		Assert.assertFalse(new File(_workspaceDir, "build.gradle").exists());

		Assert.assertFalse(new File(_workspaceDir, "gradle.properties").exists());

		Assert.assertFalse(new File(_workspaceDir, "gradle-local.properties").exists());

		Assert.assertFalse(new File(_workspaceDir, "settings.gradle").exists());

		_verifyMavenBuild();
	}

	@Test
	public void testMavenInitWorkspaceDirectoryHasFiles() throws Exception {
		String[] args = {"--base", _workspaceDir.getPath(), "init", "-P", "maven"};

		Assert.assertTrue(new File(_workspaceDir, "foo").createNewFile());

		TestUtil.runBlade(_workspaceDir, _extensionsDir, false, args);

		Assert.assertFalse(new File(_workspaceDir, "pom.xml").exists());
	}

	@Test
	public void testMavenInitWorkspaceDirectoryHasFilesForce() throws Exception {
		String[] args = {"--base", _workspaceDir.getPath(), "init", "-f", "-P", "maven"};

		TestUtil.runBlade(_workspaceDir, _extensionsDir, args);

		Assert.assertTrue(_workspaceDir.exists());

		Assert.assertTrue(new File(_workspaceDir, "pom.xml").exists());

		Assert.assertTrue(new File(_workspaceDir, "modules").exists());

		Assert.assertFalse(new File(_workspaceDir, "build.gradle").exists());

		Assert.assertFalse(new File(_workspaceDir, "gradle.properties").exists());

		Assert.assertFalse(new File(_workspaceDir, "gradle-local.properties").exists());

		Assert.assertFalse(new File(_workspaceDir, "settings.gradle").exists());

		_verifyMavenBuild();
	}

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	private void _createMavenBundle() throws Exception {
		String projectPath = new File(_workspaceDir, "modules").getAbsolutePath();

		String[] args = {"create", "-t", "mvc-portlet", "-d", projectPath, "-P", "maven", "foo"};

		TestUtil.runBlade(_workspaceDir, _extensionsDir, args);

		File file = IO.getFile(projectPath + "/foo");
		File bndFile = IO.getFile(projectPath + "/foo/bnd.bnd");

		Assert.assertTrue(file.exists());

		Assert.assertTrue(bndFile.exists());
	}

	private void _verifyMavenBuild() throws Exception {
		_createMavenBundle();

		String projectPath = _workspaceDir.getPath() + "/modules/foo";

		TestUtil.updateMavenRepositories(projectPath);

		MavenUtil.executeGoals(projectPath, new String[] {"clean", "package"});

		MavenTestUtil.verifyBuildOutput(projectPath, "foo-1.0.0.jar");
	}

	private File _extensionsDir = null;
	private File _workspaceDir = null;

}