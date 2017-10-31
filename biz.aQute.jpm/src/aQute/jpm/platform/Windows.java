package aQute.jpm.platform;

/**
 * http://support.microsoft.com/kb/814596
 */
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

import org.boris.winrun4j.RegistryKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.osgi.Instructions;
import aQute.jpm.lib.CommandData;
import aQute.jpm.lib.JVM;
import aQute.jpm.lib.ServiceData;
import aQute.lib.getopt.Arguments;
import aQute.lib.getopt.Description;
import aQute.lib.getopt.Options;
import aQute.lib.io.IO;
import aQute.lib.strings.Strings;

/**
 * The Windows platform uses an open source library
 * <a href="http://winrun4j.sourceforge.net/">WinRun4j</a>. An executable is
 * copied to the path of the desired command. When this command is executed, it
 * looks up the same path, but then with the .exe replaced with .ini. This ini
 * file then describes what Java code to start. For JPM, we copy the base exe
 * (either console and/or 64 bit arch) and then create the ini file from the jpm
 * command data.
 * <p>
 * TODO services (fortunately, winrun4j has extensive support)
 */
public class Windows extends Platform {
	private final static Logger	logger	= LoggerFactory.getLogger(Windows.class);

	/**
	 * The default global directory.
	 */
	@Override
	public File getGlobal() {
		String sysdrive = System.getenv("SYSTEMDRIVE");
		if (sysdrive == null)
			sysdrive = "c:";

		return IO.getFile(sysdrive + "\\JPM4J");
	}

	/**
	 * The default local directory.
	 */
	@Override
	public File getLocal() {
		return IO.getFile("~/.jpm/windows");
	}

	/**
	 * The default global binary dir. Though this role is played by the
	 * c:\Windows\system directory, this is seen as a bit too ambitious. We
	 * therefore create it a subdirectory of the global directory.
	 */
	@Override
	public File getGlobalBinDir() {
		return new File(getGlobal() + "\\bin");
	}

	@Override
	public void shell(String initial) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getName() {
		return "Windows";
	}

	/**
	 * The uninstaller should be used
	 */
	@Override
	public void uninstall() throws IOException {}

	/**
	 * Create a new command. Firgure out if we need the console or the window
	 * version and the 64 or 32 bit version of the exe. Copy it, and create the
	 * ini file.
	 */
	@Override
	public String createCommand(CommandData data, Map<String,String> map, boolean force, String... extra)
			throws Exception {
		data.bin = getExecutable(data);

		File f = new File(data.bin);
		if (f.isDirectory()) {
			f = new File(data.bin, data.name);
			data.bin = f.getAbsolutePath();
		}

		if (!force && f.exists())
			return "Command already exists " + data.bin;

		process("windows/command.bat", data, data.bin, map, extra);
		return null;
	}

	@Override
	public void deleteCommand(CommandData cmd) throws Exception {
		String executable = getExecutable(cmd);
		File f = new File(executable);
		if (cmd.name.equals("jpm")) {
			logger.debug("leaving jpm behind");
			return;
		} else {
			IO.deleteWithException(f);
		}
	}

	/**
	 * Return the File to the exe file.
	 * 
	 * @param data
	 */
	protected String getExecutable(CommandData data) {
		return new File(jpm.getBinDir(), data.name + ".bat").getAbsolutePath();
	}

	/**
	 * Create a new service
	 */
	@Override
	public String createService(ServiceData data, Map<String,String> map, boolean force, String... extra)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteService(ServiceData data) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int launchService(ServiceData data) throws Exception {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void installDaemon(boolean user) throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void uninstallDaemon(boolean user) throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void chown(String user, boolean recursive, File file) throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public String user() throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String toString() {
		try {
			return "Windows";
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Provide as much detail about the jpm environment as possible.
	 * 
	 * @throws Exception
	 */

	public void report(Formatter f) throws Exception {}

	/**
	 * Add the current bindir to the environment
	 */

	@Arguments(arg = {})
	@Description("Add the bin directory for this jpm to your PATH in the user's environment variables")
	interface PathOptions extends Options {
		@Description("Remove the bindir from the user's environment variables.")
		boolean remove();

		@Description("Delete a path from the PATH environment variable")
		List<String> delete();

		@Description("Add the current binary dir to the PATH environment variable")
		boolean add();

		@Description("Add additional paths to the PATH environment variable")
		List<String> extra();
	}

	@Description("Add the bin directory for this jpm to your PATH in the user's environment variables")
	public void _path(PathOptions options) {
		RegistryKey env = RegistryKey.HKEY_CURRENT_USER.getSubKey("Environment");
		if (env == null) {
			reporter.error("Cannot find key for environment HKEY_CURRENT_USER/Environment");
			return;
		}

		String path = env.getString("Path");
		String parts[] = path == null ? new String[0] : path.split(File.pathSeparator);
		List<String> paths = new ArrayList<String>(Arrays.asList(parts));
		boolean save = false;
		if (options.extra() != null) {
			paths.addAll(options.extra());
			save = true;
		}

		for (int i = 0; i < parts.length; i++) {
			System.out.printf("%2d:%s %s %s%n", i, parts[i].toLowerCase().contains("jpm") ? "*" : " ",
					new File(parts[i]).isDirectory() ? " " : "!", parts[i]);
		}

		if (options.remove()) {
			if (!paths.remove(jpm.getBinDir().getAbsolutePath())) {
				reporter.error("Could not find %s", jpm.getBinDir());
			}
			save = true;
		}
		if (options.delete() != null) {
			Instructions instr = new Instructions(options.delete());
			paths = new ArrayList<String>(instr.select(paths, true));
		}
		if (options.add()) {
			paths.remove(jpm.getBinDir().getAbsolutePath());
			paths.add(jpm.getBinDir().getAbsolutePath());
			save = true;
		}
		if (save) {
			String p = Strings.join(File.pathSeparator, paths);
			env.setString("Path", p);
		}
	}

	@Override
	public void getVMs(Collection<JVM> vms) throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public JVM getJVM(File f) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
