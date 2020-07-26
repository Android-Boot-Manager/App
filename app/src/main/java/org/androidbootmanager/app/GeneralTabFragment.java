package org.androidbootmanager.app;
import java.io.File;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.View;
import android.widget.Button;
import android.view.View.OnClickListener;

public class GeneralTabFragment extends BaseFragment
{

	ConfigFile generalConfig;
	String fileName;
	
	@Override
	protected void onPreInit()
	{
		layout = R.layout.tab_general;
	}
	
	@Override
	protected void onInit() {
		fileName = Shell.doRoot("ls /data/bootset/lk2nd/lk2nd.conf >/dev/null 2>/dev/null && echo /data/bootset/lk2nd/lk2nd.conf || echo /data/bootset/lk2nd/db.conf");
		generalConfig = ConfigFile.importFromString(Shell.doRoot("cat " + fileName));
		generalConfig.exportToPrivFile("lk2nd.conf", fileName);
		((EditText) getView().findViewById(R.id.tabgeneralSettingTimeout)).setText(generalConfig.get("timeout"));
		((EditText) getView().findViewById(R.id.tabgeneralSettingDefaultEntry)).setText(generalConfig.get("default"));
		((EditText) getView().findViewById(R.id.tabgeneralSettingTimeout)).addTextChangedListener(new ConfigTextWatcher(generalConfig, "timeout"));
		((EditText) getView().findViewById(R.id.tabgeneralSettingDefaultEntry)).addTextChangedListener(new ConfigTextWatcher(generalConfig, "default"));
			
		((Button) getView().findViewById(R.id.tabgeneralSave)).setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View p1) {
					generalConfig.exportToPrivFile("lk2nd.conf", fileName);
				}
			});
	}
}
