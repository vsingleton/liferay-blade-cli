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

package com.liferay.blade.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.JCommander.Builder;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import com.liferay.blade.cli.command.BaseArgs;
import com.liferay.blade.cli.command.BaseCommand;
import com.liferay.blade.cli.command.BladeProfile;
import com.liferay.blade.cli.command.UpdateCommand;
import com.liferay.blade.cli.command.VersionCommand;
import com.liferay.blade.cli.util.CombinedClassLoader;
import com.liferay.blade.cli.util.WorkspaceUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.fusesource.jansi.AnsiConsole;

/**
 * @author Gregory Amerson
 * @author David Truong
 */
public class BladeCLI {

	public static Map<String, BaseCommand<? extends BaseArgs>> getCommandMapByClassLoader(
			String profileName, ClassLoader classLoader)
		throws IllegalAccessException, InstantiationException {

		Collection<BaseCommand<?>> allCommands = _getCommandsByClassLoader(classLoader);

		Map<String, BaseCommand<?>> commandMap = new HashMap<>();

		Collection<BaseCommand<?>> commandsToRemove = new ArrayList<>();

		boolean profileNameIsPresent = false;

		if ((profileName != null) && (profileName.length() > 0)) {
			profileNameIsPresent = true;
		}

		for (BaseCommand<?> baseCommand : allCommands) {
			Collection<String> profileNames = _getBladeProfiles(baseCommand.getClass());

			Class<? extends BaseArgs> argsClass = baseCommand.getArgsClass();

			if (profileNameIsPresent && profileNames.contains(profileName)) {
				_addCommand(commandMap, baseCommand, argsClass);

				commandsToRemove.add(baseCommand);
			}
			else if ((profileNames != null) && !profileNames.isEmpty()) {
				commandsToRemove.add(baseCommand);
			}
		}

		allCommands.removeAll(commandsToRemove);

		for (BaseCommand<?> baseCommand : allCommands) {
			Class<? extends BaseArgs> argsClass = baseCommand.getArgsClass();

			_addCommand(commandMap, baseCommand, argsClass);
		}

		return commandMap;
	}

	public static void main(String[] args) {
		BladeCLI bladeCLI = new BladeCLI();

		try {
			bladeCLI.run(args);
		}
		catch (Exception e) {
			bladeCLI.error("Unexpected error occured.");

			e.printStackTrace(bladeCLI._error);
		}
	}

	public BladeCLI() {
		this(System.out, System.err, System.in);
	}

	public BladeCLI(PrintStream out, PrintStream err, InputStream in) {
		AnsiConsole.systemInstall();

		_out = out;
		_error = err;
		_in = in;
	}

	public void addErrors(String prefix, Collection<String> data) {
		error().println("Error: " + prefix);

		data.forEach(error()::println);
	}

	public PrintStream error() {
		return _error;
	}

	public void error(String msg) {
		_error.println(msg);
	}

	public void error(String string, String name, String message) {
		error(string + " [" + name + "]");
		error(message);
	}

	public void error(Throwable error) {
		error(error.getMessage());
		error.printStackTrace(error());
	}

	public BaseArgs getArgs() {
		return _args;
	}

	public BladeSettings getBladeSettings() throws IOException {
		final File settingsFile;

		if (WorkspaceUtil.isWorkspace(this)) {
			File workspaceDir = WorkspaceUtil.getWorkspaceDir(this);

			settingsFile = new File(workspaceDir, ".blade/settings.properties");
		}
		else {
			settingsFile = new File(_USER_HOME_DIR, ".blade/settings.properties");
		}

		return new BladeSettings(settingsFile);
	}

	public BaseCommand<?> getCommand() {
		return _baseCommand;
	}

	public Path getExtensionsPath() throws IOException {
		Path userBladePath = _getUserBladePath();

		Path extensions = userBladePath.resolve("extensions");

		if (Files.notExists(extensions)) {
			Files.createDirectories(extensions);
		}
		else if (!Files.isDirectory(extensions)) {
			throw new IOException(".blade/extensions is not a directory!");
		}

		return extensions;
	}

	public InputStream in() {
		return _in;
	}

	public PrintStream out() {
		return _out;
	}

	public void out(String msg) {
		out().println(msg);
	}

	public void postRunCommand() {
		if (_shouldCheckForUpdates()) {
			try {
				_writeLastUpdateCheck();

				printUpdateIfAvailable();
			}
			catch (IOException ioe) {
				error(ioe);
			}
		}
	}

	public boolean printUpdateIfAvailable() throws IOException {
		boolean available;

		String bladeCLIVersion = VersionCommand.getBladeCLIVersion();

		boolean fromSnapshots = false;

		if (bladeCLIVersion == null) {
			throw new IOException("Could not determine blade version");
		}

		fromSnapshots = bladeCLIVersion.contains("SNAPSHOT");

		String updateVersion = "";

		try {
			updateVersion = UpdateCommand.getUpdateVersion(fromSnapshots);

			available = UpdateCommand.shouldUpdate(bladeCLIVersion, updateVersion);

			if (available) {
				out(System.lineSeparator() + "blade version " + bladeCLIVersion + System.lineSeparator());
				out(
					"Run \'blade update" + (fromSnapshots ? " --snapshots" : "") + "\' to update to " +
						(fromSnapshots ? "the latest snapshot " : " ") + "version " + updateVersion +
							System.lineSeparator());
			}
			else {
				if (fromSnapshots) {
					if (!UpdateCommand.equal(bladeCLIVersion, updateVersion)) {
						out(
							String.format(
								"blade version %s is newer than latest snapshot %s; skipping update.\n",
								bladeCLIVersion, updateVersion));
					}
				}
			}
		}
		catch (IOException ioe) {
			available = false;
		}

		return available;
	}

	public void printUsage() {
		StringBuilder usageString = new StringBuilder();

		_jCommander.usage(usageString);

		try (Scanner scanner = new Scanner(usageString.toString())) {
			StringBuilder simplifiedUsageString = new StringBuilder();

			while (scanner.hasNextLine()) {
				String oneLine = scanner.nextLine();

				if (!oneLine.startsWith("          ") && !oneLine.contains("Options:")) {
					simplifiedUsageString.append(oneLine + System.lineSeparator());
				}
			}

			String output = simplifiedUsageString.toString();

			out(output);
		}
	}

	public void printUsage(String command) {
		_jCommander.usage(command);
	}

	public void printUsage(String command, String message) {
		out(message);
		_jCommander.usage(command);
	}

	public void run(String[] args) throws Exception {
		String basePath = _extractBasePath(args);

		String profileName = _extractProfileName(args);

		File baseDir = new File(basePath).getAbsoluteFile();

		_args.setBase(baseDir);

		System.setOut(out());

		System.setErr(error());

		BladeSettings bladeSettings = getBladeSettings();

		if (profileName != null) {
			bladeSettings.setProfileName(profileName);
		}

		bladeSettings.migrateWorkspaceIfNecessary();

		Extensions extensions = new Extensions(bladeSettings, getExtensionsPath());

		_commands = extensions.getCommands();

		args = Extensions.sortArgs(_commands, args);

		_jCommander = _buildJCommanderWithCommandMap(_commands);

		if ((args.length == 1) && args[0].equals("--help")) {
			printUsage();
		}
		else {
			try {
				_jCommander.parse(args);

				String command = _jCommander.getParsedCommand();

				Map<String, JCommander> jCommands = _jCommander.getCommands();

				JCommander jCommander = jCommands.get(command);

				if (jCommander == null) {
					printUsage();

					extensions.close();

					return;
				}

				List<Object> objects = jCommander.getObjects();

				Object commandArgs = objects.get(0);

				_command = command;

				_args = (BaseArgs)commandArgs;

				_args.setProfileName(profileName);

				_args.setBase(baseDir);

				runCommand();

				postRunCommand();
			}
			catch (MissingCommandException mce) {
				error("Error");

				StringBuilder stringBuilder = new StringBuilder("0. No such command");

				for (String arg : args) {
					stringBuilder.append(" " + arg);
				}

				error(stringBuilder.toString());

				printUsage();
			}
			catch (ParameterException pe) {
				error(_jCommander.getParsedCommand() + ": " + pe.getMessage());
			}
		}

		extensions.close();
	}

	public void runCommand() {
		try {
			if (_args.isHelp()) {
				if (Objects.isNull(_command) || (_command.length() == 0)) {
					printUsage();
				}
				else {
					printUsage(_command);
				}
			}
			else {
				if (_args != null) {
					_runCommand();
				}
				else {
					_jCommander.usage();
				}
			}
		}
		catch (ParameterException pe) {
			throw pe;
		}
		catch (Exception e) {
			Class<?> exceptionClass = e.getClass();

			String exceptionClassName = exceptionClass.getName();

			error("error: " + exceptionClassName + " :: " + e.getMessage() + System.lineSeparator());

			if (_args.isTrace()) {
				e.printStackTrace(error());
			}
			else {
				error("\tat " + e.getStackTrace()[0] + System.lineSeparator());
				error("For more information run `blade " + _command + " --trace");
			}
		}
	}

	public void trace(String s, Object... args) {
		if (_args.isTrace() && (_tracer != null)) {
			_tracer.format("# " + s + "%n", args);
			_tracer.flush();
		}
	}

	private static void _addCommand(
			Map<String, BaseCommand<?>> map, BaseCommand<?> baseCommand, Class<? extends BaseArgs> argsClass)
		throws IllegalAccessException, InstantiationException {

		BaseArgs baseArgs = argsClass.newInstance();

		baseCommand.setArgs(baseArgs);

		Parameters parameters = argsClass.getAnnotation(Parameters.class);

		if (parameters == null) {
			throw new IllegalArgumentException(
				"Loaded base command class that does not have a Parameters annotation " + argsClass.getName());
		}

		String[] commandNames = parameters.commandNames();

		map.putIfAbsent(commandNames[0], baseCommand);
	}

	private static JCommander _buildJCommander(String profileName) throws Exception {
		Thread currentThread = Thread.currentThread();

		ClassLoader classLoader = currentThread.getContextClassLoader();

		Map<String, BaseCommand<? extends BaseArgs>> commandMap = getCommandMapByClassLoader(profileName, classLoader);

		JCommander jCommander = _buildJCommanderWithCommandMap(commandMap);

		return jCommander;
	}

	private static JCommander _buildJCommanderWithCommandMap(Map<String, BaseCommand<? extends BaseArgs>> commandMap) {
		Builder builder = JCommander.newBuilder();

		for (Entry<String, BaseCommand<? extends BaseArgs>> entry : commandMap.entrySet()) {
			BaseCommand<? extends BaseArgs> value = entry.getValue();

			try {
				builder.addCommand(entry.getKey(), value.getArgs());
			}
			catch (ParameterException pe) {
				System.err.println(pe.getMessage());
			}
		}

		return builder.build();
	}

	private static String _extractBasePath(String[] args) {
		String defaultBasePath = ".";

		if (args.length > 2) {
			return IntStream.range(
				0, args.length - 1
			).filter(
				i -> args[i].equals("--base") && (args.length > (i + 1))
			).mapToObj(
				i -> args[i + 1]
			).findFirst(
			).orElse(
				defaultBasePath
			);
		}

		return defaultBasePath;
	}

	private static Collection<String> _getBladeProfiles(Class<?> commandClass) {
		return Stream.of(
			commandClass.getAnnotationsByType(BladeProfile.class)
		).filter(
			Objects::nonNull
		).map(
			BladeProfile::value
		).collect(
			Collectors.toList()
		);
	}

	private static String _getCommandProfile(String[] args) throws MissingCommandException {
		String profile = null;

		try {
			JCommander jCommander = _buildJCommander("gradle");

			jCommander.parse(args);

			String command = jCommander.getParsedCommand();

			Map<String, JCommander> jCommands = jCommander.getCommands();

			jCommander = jCommands.get(command);

			if (jCommander != null) {
				List<Object> objects = jCommander.getObjects();

				Object commandArgs = objects.get(0);

				BaseArgs baseArgs = BaseArgs.class.cast(commandArgs);

				profile = baseArgs.getProfileName();
			}
		}
		catch (MissingCommandException mce) {
			mce.printStackTrace();

			throw mce;
		}
		catch (Throwable throwable) {
			throw new MissingCommandException(throwable.getMessage());
		}

		return profile;
	}

	@SuppressWarnings("rawtypes")
	private static Collection<BaseCommand<?>> _getCommandsByClassLoader(ClassLoader classLoader) {
		Collection<BaseCommand<?>> allCommands = new ArrayList<>();
		ServiceLoader<BaseCommand> serviceLoader = ServiceLoader.load(BaseCommand.class, classLoader);

		for (BaseCommand<?> baseCommand : serviceLoader) {
			baseCommand.setClassLoader(classLoader);

			allCommands.add(baseCommand);
		}

		return allCommands;
	}

	private String _extractProfileName(String[] args) {
		List<String> argsList = new ArrayList<>();
		List<String> originalArgsList = Arrays.asList(args);

		argsList.addAll(originalArgsList);

		for (int x = 0; x < argsList.size(); x++) {
			String arg = argsList.get(x);

			if (Objects.equals(arg, "--base")) {
				argsList.remove(x);
				argsList.remove(x);

				break;
			}
		}

		try {
			return _getCommandProfile(argsList.toArray(new String[0]));
		}
		catch (MissingCommandException mce) {
			System.out.println(mce);
			mce.printStackTrace();
		}

		return null;
	}

	private Path _getUpdateCheckPath() throws IOException {
		Path userBladePath = _getUserBladePath();

		return userBladePath.resolve("updateCheck.properties");
	}

	private Path _getUserBladePath() throws IOException {
		Path userHomePath = _USER_HOME_DIR.toPath();

		Path userBladePath = userHomePath.resolve(".blade");

		if (Files.notExists(userBladePath)) {
			Files.createDirectories(userBladePath);
		}
		else if (!Files.isDirectory(userBladePath)) {
			throw new IOException(userBladePath + " is not a directory.");
		}

		return userBladePath;
	}

	private void _runCommand() throws Exception {
		BaseCommand<?> command = null;

		if (_commands.containsKey(_command)) {
			command = _commands.get(_command);
		}

		if (command != null) {
			_baseCommand = command;
			command.setArgs(_args);
			command.setBlade(this);

			Thread thread = Thread.currentThread();

			ClassLoader currentClassLoader = thread.getContextClassLoader();

			ClassLoader combinedClassLoader = new CombinedClassLoader(currentClassLoader, command.getClassLoader());

			try {
				thread.setContextClassLoader(combinedClassLoader);

				command.execute();
			}
			catch (Throwable th) {
				throw th;
			}
			finally {
				if (command instanceof AutoCloseable) {
					((AutoCloseable)command).close();
				}

				thread.setContextClassLoader(currentClassLoader);
			}
		}
		else {
			printUsage();
		}
	}

	private boolean _shouldCheckForUpdates() {
		try {
			if (_command.contains("update")) {
				return false;
			}

			Path updateCheckPath = _getUpdateCheckPath();

			if (!Files.exists(updateCheckPath)) {
				return true;
			}

			Properties properties = new Properties();

			InputStream inputStream = Files.newInputStream(updateCheckPath);

			properties.load(inputStream);

			inputStream.close();

			Instant lastUpdateCheck = Instant.ofEpochMilli(
				Long.parseLong(properties.getProperty(_LAST_UPDATE_CHECK_KEY)));

			Instant now = Instant.now();

			Instant yesterday = now.minus(1, ChronoUnit.DAYS);

			if (yesterday.isAfter(lastUpdateCheck)) {
				return true;
			}
		}
		catch (Exception ioe) {
		}

		return false;
	}

	private void _writeLastUpdateCheck() throws IOException {
		Path updateCheckPath = _getUpdateCheckPath();

		Properties properties = new Properties();

		Instant now = Instant.now();

		properties.put(_LAST_UPDATE_CHECK_KEY, String.valueOf(now.toEpochMilli()));

		try (OutputStream outputStream = Files.newOutputStream(updateCheckPath)) {
			properties.store(outputStream, null);
		}
	}

	private static final String _LAST_UPDATE_CHECK_KEY = "lastUpdateCheck";

	private static final File _USER_HOME_DIR = new File(System.getProperty("user.home"));

	private static final Formatter _tracer = new Formatter(System.out);

	private BaseArgs _args = new BaseArgs();
	private BaseCommand<?> _baseCommand;
	private String _command;
	private Map<String, BaseCommand<? extends BaseArgs>> _commands;
	private final PrintStream _error;
	private final InputStream _in;
	private JCommander _jCommander;
	private final PrintStream _out;

}