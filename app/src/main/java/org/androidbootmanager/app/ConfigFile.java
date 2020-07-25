package org.androidbootmanager.app;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class ConfigFile
{
	Map<String,String> data = new HashMap<>();
	String get(String name) {
		return data.get(name);
	}
	void set(String name, String value) {
		data.put(name, value);
	}
	String exportToString() {
		String out = "";
		for (String key : data.keySet()) {
			out += key + " " + get(key) + "\n";
		}
		return out;
	}
	void exportToFile(File file) {
		try {
			if (!file.exists()) file.createNewFile();
			PrintWriter out = new PrintWriter(file);
			out.write(exportToString());
			out.flush();
			out.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	void exportToPrivFile(String tempfilename, String fullPath) {
		exportToFile(new File("/data/data/org.androidbootmanager.app/files/" + tempfilename));
		Shell.doRoot("cp /data/data/org.androidbootmanager.app/files/" + tempfilename + " " + fullPath);
	}
	static ConfigFile importFromString(String s) {
		ConfigFile out = new ConfigFile();
		for (String line : s.split("\n")) {
			line = line.trim();
			out.set(line.substring(0,line.indexOf(" ")),line.substring(line.indexOf(" ")).trim());
		}
		return out;
	}
}
