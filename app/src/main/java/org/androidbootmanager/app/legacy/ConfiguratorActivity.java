package org.androidbootmanager.app.legacy;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.topjohnwu.superuser.Shell;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.ui.activities.SplashActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ConfiguratorActivity extends FragmentActivity {

    public interface FragmentCreator {
        Fragment run();
    }

    abstract static public class BaseFragment extends Fragment {
        protected int layout = 0;
        protected ConfiguratorActivity xcontext = null;

        abstract protected void onPreInit();

        abstract protected void onInit();

        public BaseFragment() {
            super();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            onPreInit();
            return inflater.inflate(layout, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
            xcontext = (ConfiguratorActivity) getActivity();
            onInit();
            super.onViewCreated(view, savedInstanceState);
        }

        public static void registerTabs(Resources res, ConfiguratorActivity.TabAdapter adapter) {
            adapter.addFragment(RomTabFragment::new, res.getString(R.string.roms));
            adapter.addFragment(EntryTabFragment::new, res.getString(R.string.entries));
            adapter.addFragment(GeneralTabFragment::new, res.getString(R.string.general));
        }
    }


    public static class TabAdapter extends FragmentStateAdapter {
        private final List<FragmentCreator> flist = new ArrayList<>();
        private final List<String> tlist = new ArrayList<>();

        TabAdapter(FragmentActivity fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return flist.get(position).run();
        }

        public void addFragment(FragmentCreator fragment, String title) {
            flist.add(fragment);
            tlist.add(title);
        }

        @Override
        public int getItemCount() {
            return flist.size();
        }

        public String getTlist(int position) {
            return tlist.get(position);
        }
    }

    private TabAdapter adapter;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = this.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        setContentView(R.layout.legacy_cfg);
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        adapter = new TabAdapter(this);
        BaseFragment.registerTabs(getResources(), adapter);
        viewPager.setAdapter(adapter);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(adapter.getTlist(position))
        ).attach();
    }

    public void doLkUpdate(View v) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_droidboot_title)
                .setMessage(R.string.select_droidboot_msg)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, (p1, p2) -> p1.dismiss())
                .setPositiveButton(R.string.ok, (p1, p2) -> {
                    Intent intent = new Intent();
                    intent.setType("*/*");
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(intent, 5208);
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 5208) {
            if (resultCode == RESULT_OK) {
                assert data != null;
                Uri selectedUri = data.getData();
                try {
                    InputStream initialStream;
                    if (selectedUri != null) {
                        initialStream = getContentResolver().openInputStream(selectedUri);
                    } else {
                        throw new IOException("null selected");
                    }
                    @SuppressLint("SdCardPath") File targetFile = new File("/data/data/org.androidbootmanager.app/files/lk2nd.img");
                    OutputStream outStream = new FileOutputStream(targetFile);
                    assert initialStream != null;
                    SplashActivity.copyFile(initialStream, outStream);
                    initialStream.close();
                    outStream.close();
                    new Thread(() -> {
                        Shell.su("cd /data/data/org.androidbootmanager.app/assets/Toolkit && /data/data/org.androidbootmanager.app/assets/Scripts/update/`cat /data/abm-codename.legacy_cfg`.droid.sh 2>&1").exec();
                        runOnUiThread(() -> Toast.makeText(this, R.string.ok, Toast.LENGTH_LONG).show());
                    }).start();
                } catch (IOException e) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.install_finish_title)
                            .setMessage(getResources().getString(R.string.install_finish_msg, "ERROR_java (IOException)"))
                            .show();
                }
            }
        } else super.onActivityResult(requestCode, resultCode, data);
    }
}
