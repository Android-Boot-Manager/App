package org.androidbootmanager.app.ui.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.devices.DeviceModel;
import org.androidbootmanager.app.ui.home.InstalledViewModel;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    public static boolean exit = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new ViewModelProvider(this).get(InstalledViewModel.class);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> Snackbar.make(view, "Not yet implemented", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_roms, R.id.nav_generalcfg, R.id.nav_about, R.id.nav_sd)
                .setOpenableLayout(drawer)
                .build();
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        assert navHostFragment != null;
        NavController navController = navHostFragment.getNavController();
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(exit) finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @SuppressLint("SdCardPath")
    public boolean mount(DeviceModel d) {
        if (SuFile.open("/data/abm/bootset/db").exists())
            return true;
        Shell.Result result;
        if(!(result = Shell.su("/data/data/org.androidbootmanager.app/assets/Scripts/config/mount/yggdrasil.sh").exec()).isSuccess()) {
            Log.e("ABM_MOUNT",String.join("",result.getOut()));
            Log.e("ABM_MOUNT",String.join("",result.getErr()));
            return false;
        }
        if (d.usesLegacyDir) {
            if (!(result = Shell.su("mount --bind /data/abm/bootset/lk2nd /data/abm/bootset/db").exec()).isSuccess()) {
                Log.e("ABM_MOUNT", String.join("", result.getOut()));
                Log.e("ABM_MOUNT", String.join("", result.getErr()));
                return false;
            }
        } else {
            if(!(result = Shell.su("mount --bind /data/abm/bootset/db /data/abm/bootset/lk2nd").exec()).isSuccess()) {
                Log.e("ABM_MOUNT",String.join("",result.getOut()));
                Log.e("ABM_MOUNT",String.join("",result.getErr()));
                return false;
            }
        }

         return true;
    }

    @SuppressLint("SdCardPath")
    public boolean umount(DeviceModel d) {
        Shell.Result result;

        if (d.usesLegacyDir) {
            if (!(result = Shell.su("umount /data/abm/bootset/db").exec()).isSuccess()) {
                Log.e("ABM_MOUNT", String.join("", result.getOut()));
                Log.e("ABM_MOUNT", String.join("", result.getErr()));
                return false;
            }
        } else {
            if (!(result = Shell.su("umount /data/abm/bootset/lk2nd").exec()).isSuccess()) {
                Log.e("ABM_MOUNT", String.join("", result.getOut()));
                Log.e("ABM_MOUNT", String.join("", result.getErr()));
                return false;
            }
        }
        if(!(result = Shell.su("/data/data/org.androidbootmanager.app/assets/Scripts/config/umount/yggdrasil.sh").exec()).isSuccess()) {
            Log.e("ABM_MOUNT",String.join("",result.getOut()));
            Log.e("ABM_MOUNT",String.join("",result.getErr()));
            return false;
        }

        return true;
    }
}