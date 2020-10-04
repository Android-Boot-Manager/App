package org.androidbootmanager.app;

import android.annotation.SuppressLint;

import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class ConfigFile {
    Map<String, String> data = new HashMap<>();

    String get(String name) {
        return data.get(name);
    }

    void set(String name, String value) {
        data.put(name, value);
    }

    String exportToString() {
        StringBuilder out = new StringBuilder();
        for (String key : data.keySet()) {
            out.append(key).append(" ").append(get(key)).append("\n");
        }
        return out.toString();
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

    @SuppressLint("SdCardPath")
    void exportToPrivFile(String tempfilename, String fullPath) {
        exportToFile(new File("/data/data/org.androidbootmanager.app/files/" + tempfilename));
        SuFile.open("/data/data/org.androidbootmanager.app/files/" + tempfilename).renameTo(new File(fullPath));
    }

    static ConfigFile importFromString(String s) {
        ConfigFile out = new ConfigFile();
        for (String line : s.split("\n")) {
            line = line.trim();
            out.set(line.substring(0, line.indexOf(" ")), line.substring(line.indexOf(" ")).trim());
        }
        return out;
    }

    static ConfigFile importFromFile(File f) throws ActionAbortedCleanlyError {
        StringBuilder s = new StringBuilder();
        SuFileInputStream i;
        byte[] b = new byte[1024];
        try {
            i = new SuFileInputStream(f);
        } catch (FileNotFoundException e) {
            throw new ActionAbortedCleanlyError(e);
        }
        while (true) {
            try {
                if (!(i.read(b) > 1)) break;
                s.append(Arrays.toString(b));
            } catch (IOException e) {
                throw new ActionAbortedCleanlyError(e);
            }
        }
        return importFromString(s.toString());
    }

    static ConfigFile importFromFile(String s) throws ActionAbortedCleanlyError {
        return importFromFile(new File(s));
    }
}
