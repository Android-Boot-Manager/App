package org.androidbootmanager.app;

import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import java.util.ArrayList;
import java.util.List;

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
