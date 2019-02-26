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

import com.liferay.blade.cli.TestUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.InetAddress;
import java.net.Socket;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.zeroturnaround.process.PidProcess;
import org.zeroturnaround.process.Processes;

/**
 * @author Christopher Bryan Boyd
 * @author Gregory Amerson
 */
public class ServerStartCommandTest {

	@Before
	public void setUp() throws Exception {
		File testWorkspaceFile = temporaryFolder.newFolder("testWorkspaceDir");

		_testWorkspacePath = testWorkspaceFile.toPath();

		File extensionsFile = temporaryFolder.newFolder(".blade", "extensions");

		_extensionsPath = extensionsFile.toPath();
	}

	@Test
	public void testServerInitCustomEnvironment() throws Exception {
		_initBladeWorkspace();

		_customizeProdProperties();

		_initServerBundle("--environment", "prod");

		Path bundleConfigPath = _getBundleConfigPath();

		_validateBundleConfigFile(bundleConfigPath);
	}

	@Test
	public void testServerStartCommandExists() throws Exception {
		Assert.assertTrue(_commandExists("server", "start"));
		Assert.assertTrue(_commandExists("server start"));
		Assert.assertFalse(_commandExists("server", "startx"));
		Assert.assertFalse(_commandExists("server startx"));
		Assert.assertFalse(_commandExists("serverx", "start"));
		Assert.assertFalse(_commandExists("serverx start"));
	}

	@Test
	public void testServerStartCommandTomcat() throws Exception {
		boolean useDebugging = false;

		_initBladeWorkspace();

		_addTomcatBundleToGradle();

		_initServerBundle();

		_verifyTomcatBundlePath();

		_startServer(useDebugging);

		_findAndTerminateTomcat(useDebugging);
	}

	@Test
	public void testServerStartCommandTomcatDebug() throws Exception {
		boolean useDebugging = true;

		_initBladeWorkspace();

		_addTomcatBundleToGradle();

		_initServerBundle();

		_verifyTomcatBundlePath();

		_startServer(useDebugging);

		_findAndTerminateTomcat(useDebugging);
	}

	@Test
	public void testServerStartCommandWildfly() throws Exception {
		boolean useDebugging = false;

		_initBladeWorkspace();

		_addWildflyBundleToGradle();

		_initServerBundle();

		_verifyWildflyBundlePath();

		_startServer(useDebugging);

		_findAndTerminateWildfly(useDebugging);
	}

	@Test
	public void testServerStartCommandWildflyDebug() throws Exception {
		boolean useDebugging = true;

		_initBladeWorkspace();

		_addWildflyBundleToGradle();

		_initServerBundle();

		_verifyWildflyBundlePath();

		_startServer(useDebugging);

		_findAndTerminateWildfly(useDebugging);
	}

	@Test
	public void testServerStopCommandExists() throws Exception {
		Assert.assertTrue(_commandExists("server", "stop"));
		Assert.assertTrue(_commandExists("server stop"));
		Assert.assertFalse(_commandExists("server", "stopx"));
		Assert.assertFalse(_commandExists("server stopx"));
		Assert.assertFalse(_commandExists("serverx", "stopx"));
		Assert.assertFalse(_commandExists("serverx stop"));
	}

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	private static boolean _isDebugPortListening(int debugPort) {
		InetAddress loopbackAddress = InetAddress.getLoopbackAddress();

		try (Socket socket = new Socket(loopbackAddress, debugPort)) {
			return true;
		}
		catch (IOException ioe) {
			return false;
		}
	}

	private static void _terminateProcess(PidProcess tomcatPidProcess) throws InterruptedException, IOException {
		tomcatPidProcess.destroyForcefully();

		tomcatPidProcess.waitFor(1, TimeUnit.SECONDS);

		String processName = tomcatPidProcess.getDescription();

		Assert.assertFalse("Expected " + processName + " process to be destroyed.", tomcatPidProcess.isAlive());
	}

	private void _addBundleToGradle(String bundleFileName) throws Exception {
		Path gradlePropertiesPath = _testWorkspacePath.resolve("gradle.properties");

		String contents = new String(Files.readAllBytes(gradlePropertiesPath));

		StringBuilder sb = new StringBuilder();

		sb.append(_LIFERAY_WORKSPACE_BUNDLE_KEY);
		sb.append("=");
		sb.append(_LIFERAY_WORKSPACE_BUNDLE_URL);
		sb.append(bundleFileName);
		sb.append(System.lineSeparator());

		String bundleUrl = sb.toString();

		contents = bundleUrl + contents;

		Files.write(gradlePropertiesPath, bundleUrl.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
	}

	private void _addTomcatBundleToGradle() throws Exception {
		_addBundleToGradle(_LIFERAY_WORKSPACE_BUNDLE_TOMCAT);
	}

	private void _addWildflyBundleToGradle() throws Exception {
		_addBundleToGradle(_LIFERAY_WORKSPACE_BUNDLE_WILDFLY);
	}

	private boolean _commandExists(String... args) {
		try {
			TestUtil.runBlade(_testWorkspacePath, _extensionsPath, args);
		}
		catch (Throwable throwable) {
			String message = throwable.getMessage();

			if (Objects.nonNull(message) && !message.contains("No such command")) {
				return true;
			}

			return false;
		}

		return false;
	}

	private void _customizeProdProperties() throws FileNotFoundException, IOException {
		Path prodConfigPath = _testWorkspacePath.resolve(Paths.get("configs", "prod", "portal-ext.properties"));

		Properties portalExtProperties = new Properties();

		try (InputStream inputStream = Files.newInputStream(prodConfigPath)) {
			portalExtProperties.load(inputStream);
		}

		portalExtProperties.put("foo.bar", "foobar");

		try (OutputStream outputStream = Files.newOutputStream(prodConfigPath)) {
			portalExtProperties.store(outputStream, "");
		}
	}

	private void _findAndTerminateServer(Predicate<JavaProcess> processFilter, boolean debugFlag, int debugPort)
		throws Exception {

		PidProcess serverProcess = _findServerProcess(processFilter);

		boolean debugPortListening = _isDebugPortListening(debugPort);

		Assert.assertEquals("Debug port not in a correct state", debugFlag, debugPortListening);

		_terminateProcess(serverProcess);

		if (debugFlag) {
			debugPortListening = _isDebugPortListening(debugPort);

			Assert.assertFalse("Debug port should no longer be listening", debugPortListening);
		}
	}

	private void _findAndTerminateTomcat(boolean debugFlag) throws Exception {
		_findAndTerminateServer(_FILTER_TOMCAT, debugFlag, _DEBUG_PORT_TOMCAT);
	}

	private void _findAndTerminateWildfly(boolean debugFlag) throws Exception {
		_findAndTerminateServer(_FILTER_WILDFLY, debugFlag, _DEBUG_PORT_WILDFLY);
	}

	private Optional<JavaProcess> _findProcess(
		Collection<JavaProcess> javaProcesses, Predicate<JavaProcess> processFilter) {

		Stream<JavaProcess> stream = javaProcesses.stream();

		return stream.filter(
			processFilter
		).findFirst();
	}

	private PidProcess _findServerProcess(Predicate<JavaProcess> processFilter)
		throws InterruptedException, IOException {

		Collection<JavaProcess> javaProcesses = JavaProcesses.list();

		Optional<JavaProcess> optionalProcess = _findProcess(javaProcesses, processFilter);

		Assert.assertTrue(
			"Expected to find server process:\n" + _printDisplayNames(javaProcesses), optionalProcess.isPresent());

		JavaProcess javaProcess = optionalProcess.get();

		String processName = javaProcess.getDisplayName();

		PidProcess pidProcess = Processes.newPidProcess(javaProcess.getId());

		Assert.assertTrue("Expected " + processName + " process to be alive", pidProcess.isAlive());

		return pidProcess;
	}

	private Path _getBundleConfigPath() {
		Path bundlesFolderPath = _testWorkspacePath.resolve("bundles");

		boolean bundlesFolderExists = Files.exists(bundlesFolderPath);

		Assert.assertTrue(bundlesFolderExists);

		Path bundleConfigPath = bundlesFolderPath.resolve("portal-ext.properties");

		boolean bundleConfigFileExists = Files.exists(bundleConfigPath);

		Assert.assertTrue(bundleConfigFileExists);

		return bundleConfigPath;
	}

	private void _initBladeWorkspace() throws Exception {
		String[] initArgs = {"--base", _testWorkspacePath.toString(), "init", "-v", "7.1"};

		TestUtil.runBlade(_testWorkspacePath, _extensionsPath, initArgs);
	}

	private void _initServerBundle(String... additionalArgs) throws Exception {
		String[] serverInitArgs = {"--base", _testWorkspacePath.toString(), "server", "init"};

		if ((additionalArgs != null) && (additionalArgs.length > 0)) {
			Collection<String> serverInitArgsCollection = Arrays.asList(serverInitArgs);

			Collection<String> additionalArgsCollection = Arrays.asList(additionalArgs);

			serverInitArgsCollection = new ArrayList<>(serverInitArgsCollection);

			serverInitArgsCollection.addAll(additionalArgsCollection);

			serverInitArgs = serverInitArgsCollection.toArray(new String[0]);
		}

		TestUtil.runBlade(_testWorkspacePath, _extensionsPath, serverInitArgs);
	}

	private String _printDisplayNames(Collection<JavaProcess> javaProcesses) {
		StringBuilder sb = new StringBuilder();

		for (JavaProcess javaProcess : javaProcesses) {
			sb.append(javaProcess.getDisplayName() + System.lineSeparator());
		}

		return sb.toString();
	}

	private void _startServer(boolean debugFlag) throws Exception, InterruptedException {
		String[] serverStartArgs = {"--base", _testWorkspacePath.toString(), "server", "start"};

		Collection<String> serverStartArgsCollection = Arrays.asList(serverStartArgs);

		serverStartArgsCollection = new ArrayList<>(serverStartArgsCollection);

		if (debugFlag) {
			serverStartArgsCollection.add("--debug");
		}

		serverStartArgs = serverStartArgsCollection.toArray(new String[0]);

		TestUtil.runBlade(_testWorkspacePath, _extensionsPath, serverStartArgs);

		Thread.sleep(1000);
	}

	private void _validateBundleConfigFile(Path bundleConfigPath) throws FileNotFoundException, IOException {
		Properties runtimePortalExtProperties = new Properties();

		try (InputStream inputStream = Files.newInputStream(bundleConfigPath)) {
			runtimePortalExtProperties.load(inputStream);
		}

		String fooBarProperty = runtimePortalExtProperties.getProperty("foo.bar");

		Assert.assertEquals("foobar", fooBarProperty);
	}

	private void _verifyBundlePath(String folderName) {
		Path bundlesPath = _testWorkspacePath.resolve(Paths.get("bundles", folderName));

		boolean bundlesPathExists = Files.exists(bundlesPath);

		Assert.assertTrue("Bundles folder " + bundlesPath + " must exist", bundlesPathExists);
	}

	private void _verifyTomcatBundlePath() {
		_verifyBundlePath(_BUNDLE_FOLDER_NAME_TOMCAT);
	}

	private void _verifyWildflyBundlePath() {
		_verifyBundlePath(_BUNDLE_FOLDER_NAME_WILDFLY);
	}

	private static final String _BUNDLE_FOLDER_NAME_TOMCAT = "tomcat-9.0.10";

	private static final String _BUNDLE_FOLDER_NAME_WILDFLY = "wildfly-11.0.0";

	private static final int _DEBUG_PORT_TOMCAT = 8000;

	private static final int _DEBUG_PORT_WILDFLY = 8787;

	private static final Predicate<JavaProcess> _FILTER_TOMCAT = process -> {
		String displayName = process.getDisplayName();

		return displayName.contains("org.apache.catalina.startup.Bootstrap");
	};

	private static final Predicate<JavaProcess> _FILTER_WILDFLY = process -> {
		String displayName = process.getDisplayName();

		return displayName.contains("jboss-modules");
	};

	private static final String _LIFERAY_WORKSPACE_BUNDLE_KEY = "liferay.workspace.bundle.url";

	private static final String _LIFERAY_WORKSPACE_BUNDLE_TOMCAT =
		"liferay-ce-portal-tomcat-7.1.1-ga2-20181112144637000.tar.gz";

	private static final String _LIFERAY_WORKSPACE_BUNDLE_URL = "https://releases-cdn.liferay.com/portal/7.1.1-ga2/";

	private static final String _LIFERAY_WORKSPACE_BUNDLE_WILDFLY =
		"liferay-ce-portal-wildfly-7.1.1-ga2-20181112144637000.tar.gz";

	private Path _extensionsPath = null;
	private Path _testWorkspacePath = null;

}