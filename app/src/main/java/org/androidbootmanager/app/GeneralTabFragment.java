package org.androidbootmanager.app;
import java.io.File;

public class GeneralTabFragment extends BaseFragment
{
	ConfigFile generalConfig;
	String fileName;
	
	@Override
	protected void onInit()
	{
		layout = R.layout.tab_general;
		fileName = Shell.doRoot("ls /data/bootset/lk2nd/lk2nd.conf >/dev/null 2>/dev/null && echo /data/bootset/lk2nd/lk2nd.conf || echo /data/bootset/lk2nd/db.conf");
		generalConfig = ConfigFile.importFromString(Shell.doRoot("cat " + fileName));
		generalConfig.exportToPrivFile("lk2nd.conf", fileName);
	}
}
