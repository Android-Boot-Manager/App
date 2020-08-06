package org.androidbootmanager.app;
import android.annotation.SuppressLint;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

@SuppressLint("SdCardPath")
public class Shell {
	public static String doRootGlobal(String cmd) {
		File x = new File("/data/data/org.androidbootmanager.app/files/_run.sh");
		try {if (!x.exists())x.createNewFile();} catch (IOException e) {throw new RuntimeException(e);}
		x.setExecutable(true);
		try {
			PrintWriter w = new PrintWriter(x);
			w.write("#!/system/bin/sh\n" + cmd);
			w.flush();
			w.close();
		} catch (IOException e) {throw new RuntimeException(e);}
		return doShell("su -M -c '/data/data/org.androidbootmanager.app/files/_run.sh'");
	}

	public static String doRoot(String cmd) {
		File x = new File("/data/data/org.androidbootmanager.app/files/_run.sh");
		try {if (!x.exists())x.createNewFile();} catch (IOException e) {throw new RuntimeException(e);}
		x.setExecutable(true);
		try {
			PrintWriter w = new PrintWriter(x);
			w.write("#!/system/bin/sh\n" + cmd);
			w.flush();
			w.close();
		} catch (IOException e) {throw new RuntimeException(e);}
		return doShell("su -c '/data/data/org.androidbootmanager.app/files/_run.sh'");
	}

	public static String doShell(String cmd) {
		Process p;
		try {
			p = Runtime.getRuntime().exec(cmd);
		} catch (IOException e) {
			e.printStackTrace();
			return "exec failed: io exception";
		}
		try {
			p.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return "exec failed: waitFor was interrupted";
		}


		BufferedReader reader = 
			new BufferedReader(new InputStreamReader(p.getInputStream()));

		String line;
		StringBuilder out = new StringBuilder();
		try {
			while ((line = reader.readLine()) != null) {
				out.append(line).append("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
			return "exec failed: io exception (write)";
		}
		return out.toString();
	}
}
