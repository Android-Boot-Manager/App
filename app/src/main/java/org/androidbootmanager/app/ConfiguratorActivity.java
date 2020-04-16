package org.androidbootmanager.app;

import android.app.Activity;
import android.os.Bundle;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;
import android.view.WindowManager;
import android.support.v4.content.ContextCompat;

public class ConfiguratorActivity extends AppCompatActivity
{
	public File filedir = new File("/data/data/org.androidbootmanager.app/files");
	public File assetsdir = new File(filedir + "/../assets");
	private TabLayout tabLayout;
	private ViewPager viewPager;
	private TabAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Window window = this.getWindow();
		window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
		window.setStatusBarColor(ContextCompat.getColor(this,R.color.colorPrimary));
		setContentView(R.layout.cfg);
		viewPager = (ViewPager) findViewById(R.id.viewPager);
		tabLayout = (TabLayout) findViewById(R.id.tabLayout);
		adapter = new TabAdapter(getSupportFragmentManager());
		adapter.addFragment(new RomTabFragment(this), "ROMs");
		adapter.addFragment(new ThemeTabFragment(this), "Themes");
		viewPager.setAdapter(adapter);
		tabLayout.setupWithViewPager(viewPager);
	}
	
	public String doRootGlobal(String cmd) {
		File x = new File("/data/data/org.androidbootmanager.app/files/_runw.sh");
		try{if(!x.exists())x.createNewFile();}catch (IOException e){throw new RuntimeException(e);}
		x.setExecutable(true);
		try{
			PrintWriter w = new PrintWriter(x);
			w.write("#!/system/bin/sh\n" + cmd);
			w.flush();
			w.close();
		}catch (IOException e){throw new RuntimeException(e);}
		return doRoot("su -M -c '/data/data/org.androidbootmanager.app/files/_runw.sh'");
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
