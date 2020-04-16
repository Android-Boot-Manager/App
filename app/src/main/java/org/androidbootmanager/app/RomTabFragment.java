package org.androidbootmanager.app;

public class RomTabFragment extends BaseFragment
{
	
	public RomTabFragment(ConfiguratorActivity x) {
		super(x);
	}
	@Override
	protected void onInit()
	{
		layout = R.layout.tab_rom;
	}
}
