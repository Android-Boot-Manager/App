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
	public BaseFragment() {
		super();
	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		xcontext = (ConfiguratorActivity) getActivity();
		onInit();
		return inflater.inflate(layout, container, false);
	}

	public static void registerTabs(ConfiguratorActivity.TabAdapter adapter) {
		adapter.addFragment(new RomTabFragment(), "ROMs");
		adapter.addFragment(new ThemeTabFragment(), "Themes");
	}
}
