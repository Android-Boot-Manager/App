package org.androidbootmanager.app;

import android.app.Activity;
import android.os.Bundle;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class ConfiguratorActivity extends Activity
{
	File filedir = new File("/data/data/org.androidbootmanager.app/files");
	File assetsdir = new File(filedir + "/../assets");

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cfg);
	}
	
	public String doRoot(String cmd) {
		File x = new File("/data/data/org.androidbootmanager.app/files/_run.sh");
		try{if(!x.exists())x.createNewFile();}catch (IOException e){throw new RuntimeException(e);}
		x.setExecutable(true);
		try{
			PrintWriter w = new PrintWriter(filedir + "/_run.sh");
			w.write("#!/system/bin/sh\n" + cmd);
			w.flush();
			w.close();
		}catch (IOException e){throw new RuntimeException(e);}
		return doShell("su -c '/data/data/org.androidbootmanager.app/files/_run.sh'");
	}

	public String doShell(String cmd) {
		Process p;
		try
		{
			p = Runtime.getRuntime().exec(cmd);
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		try
		{
			p.waitFor();
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		}


		BufferedReader reader = 
			new BufferedReader(new InputStreamReader(p.getInputStream()));

		String line = "";
		String out = "";
		try
		{
			while ((line = reader.readLine()) != null)
			{
				out += line + "\n";
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		return out;
	}
}
