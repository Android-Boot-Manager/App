package org.andbootmgr.app.legacy.ui.debug;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.topjohnwu.superuser.Shell;

import org.andbootmgr.app.BuildConfig;
import org.andbootmgr.app.R;

import java.util.ArrayList;

public class DebugActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);
    }

    @SuppressWarnings("unused") //param is needed for android
    public void errorTest(View v) {
        shelldialog("echo out && echo err 1>&2");
    }

    @SuppressWarnings("unused") //param is needed for android
    public void goNew(View v) {
        getPackageManager().setComponentEnabledSetting(ComponentName.createRelative(BuildConfig.APPLICATION_ID, ".MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        getPackageManager().setComponentEnabledSetting(ComponentName.createRelative(BuildConfig.APPLICATION_ID, ".legacy.ui.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                0);
    }

    public void shelldialog(String j) {
        ArrayList<String> o = new ArrayList<>();
        ArrayList<String> e = new ArrayList<>();
        Shell.Result r = Shell.su(j).to(o, e).exec();
        new AlertDialog.Builder(this)
                .setTitle(j)
                .setMessage("OUT channel:\n" + String.join("\n", o) + "\nOUT channel using get:\n" + String.join("\n", r.getOut()) + "\nERR channel:\n" + String.join("\n", e) + "\nERR channel using get:\n" + String.join("\n", r.getErr()))
                .show();
    }
}