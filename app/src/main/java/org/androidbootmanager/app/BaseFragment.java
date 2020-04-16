package org.androidbootmanager.app;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

abstract public class BaseFragment extends Fragment {
	protected int layout = 0;
	protected ConfiguratorActivity xcontext = null;
	abstract protected void onInit();
	public BaseFragment(ConfiguratorActivity x) {
		xcontext = x;
	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		onInit();
		return inflater.inflate(layout, container, false);
	}
}
