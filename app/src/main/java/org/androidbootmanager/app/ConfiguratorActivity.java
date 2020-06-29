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
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.Fragment;
import java.util.List;
import android.support.v4.app.FragmentManager;
import java.util.ArrayList;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

public class ConfiguratorActivity extends AppCompatActivity
{
	
	public class TabAdapter extends FragmentStatePagerAdapter {
		private final List<Fragment> flist = new ArrayList<>();
		private final List<String> tlist = new ArrayList<>();

		TabAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			return flist.get(position);
		}

		public void addFragment(Fragment fragment, String title) {
			flist.add(fragment);
			tlist.add(title);
		}

		@Nullable
		@Override
		public CharSequence getPageTitle(int position) {
			return tlist.get(position);
		}

		@Override
		public int getCount() {
			return flist.size();
		}
	} 
	
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
		BaseFragment.registerTabs(adapter);
		viewPager.setAdapter(adapter);
		tabLayout.setupWithViewPager(viewPager);
	}

}
