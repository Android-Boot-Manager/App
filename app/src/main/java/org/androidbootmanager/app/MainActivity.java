package org.androidbootmanager.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.widget.CheckBox;
import android.widget.ToggleButton;
import android.widget.Switch;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainActivity extends Activity 
{
	File filedir = new File("/data/data/org.androidbootmanager.app/files");
	File cfgfile = new File(filedir + "/cfg");
	File assetsdir = new File(filedir + "/../assets");
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		if (!filedir.exists()) filedir.mkdir();
		if (!assetsdir.exists()) assetsdir.mkdir();
		if (cfgfile.exists()) {
      		setContentView(R.layout.main);
		} else {
			setContentView(R.layout.main_notinstall);
		}
    }
	
	public void doInstall(View v){
		if((!android.os.Build.DEVICE.equals("cedric"))&&(!((Switch) findViewById(R.id.mainnotinstallSwitch1)).isChecked())){new AlertDialog.Builder(this).setCancelable(true).setTitle("Wrong device").setMessage("Android Boot Manager is not available for this device (" + android.os.Build.DEVICE + ").").show(); return;}
		if (!((CheckBox) findViewById(R.id.mainnotinstallCheckBox1)).isChecked()) {
			new AlertDialog.Builder(this)
			.setCancelable(true)
			.setTitle("Allow install not checked")
			.setMessage("Seems like you didn't look at this page. That's not good.")
			.show();
			return;
		}
		ProgressDialog dialog = new ProgressDialog(this);
		dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dialog.setTitle("Installing...");
		dialog.setMessage("Installing. Please wait...");
		dialog.setIndeterminate(true);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setCancelable(false);
		dialog.show();
		copyAssets();
	}
	
	private void copyAssets() {
		AssetManager assetManager = getAssets();
		String[] files = null;
		try {
			files = assetManager.list("cp"); 
		} catch (IOException e) {
			Log.e("tag", "Failed to get asset file list.", e);
		}

		for(String filename : files) {
			InputStream in = null;
			OutputStream out = null;
			try {
				in = assetManager.open("cp/" + filename);
				File outFile = new File("/data/data/org.androidbootmanager.app/assets", filename);
				out = new FileOutputStream(outFile);
				copyFile(in, out);
				in.close();
				in = null;
				out.flush();
				out.close();
				out = null;
			} catch(IOException e) {
				Log.e("tag", "Failed to copy asset file: " + filename, e);
			}
		}
		doShell("chmod -R +x /data/data/org.androidbootmanager.app/assets");
	}

	private void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while((read = in.read(buffer)) != -1){
			out.write(buffer, 0, read);
		}
	}
	
	public void showLic(View v) {
		new AlertDialog.Builder(this)
		.setTitle("README, License and Source")
		.setMessage("The source is available here: https://github.com/Android-Boot-Manager\nThe README is available here: https://github.com/Android-Boot-Manager/App/wiki\n\n   Copyright 2020 The Android Boot Manager Project\nLicensed under the Apache License, Version 2.0 (the \"License\");\nyou may not use this file except in compliance with the License.\nYou may obtain a copy of the License at\n\nhttp://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable law or agreed to in writing, software\ndistributed under the License is distributed on an \"AS IS\" BASIS,\nWITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\nSee the License for the specific language governing permissions and\nlimitations under the License.")
		.show();
	}
	public void testRoot(View v) {
		copyAssets();
		new AlertDialog.Builder(this)
		.setTitle("Test Root")
		.setMessage(doRoot("/data/data/org.androidbootmanager.app/assets/hello.sh"))
		.show();
	}
	
	public String doRoot(String cmd) {
		File x = new File("/data/data/org.androidbootmanager.app/files/_run.sh");
		try{x.createNewFile();}catch (IOException e){}
		x.setExecutable(true);
		try{Files.write(Paths.get("/data/data/org.androidbootmanager.app/files/_run.sh"), ("#!/system/bin/sh\n" + cmd).getBytes());}catch (IOException e){throw new RuntimeException(e);}
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
