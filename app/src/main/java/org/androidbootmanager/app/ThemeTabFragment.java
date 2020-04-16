package org.androidbootmanager.app;

public class ThemeTabFragment extends BaseFragment
{
	
	public ThemeTabFragment(ConfiguratorActivity x) {
		super(x);
	}
	@Override
	protected void onInit()
	{
		layout = R.layout.tab_theme;
	}
}
