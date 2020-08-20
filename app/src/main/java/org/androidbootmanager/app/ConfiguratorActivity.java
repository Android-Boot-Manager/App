package org.androidbootmanager.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.obsez.android.lib.filechooser.ChooserDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    public VM vm;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = this.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        setContentView(R.layout.cfg);
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
                    MainActivity.copyFile(initialStream, outStream);
                    initialStream.close();
                    outStream.close();
                    new Thread(() -> {
                        Log.i("abm",Shell.doRoot("cd /data/data/org.androidbootmanager.app/assets/Toolkit && /data/data/org.androidbootmanager.app/assets/Scripts/update/`cat /data/abm-codename.cfg`.droid.sh 2>&1"));
                        runOnUiThread(() -> Toast.makeText(this,R.string.ok,Toast.LENGTH_LONG).show());
                    }).start();
                } catch (IOException e) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.install_finish_title)
                            .setMessage(getResources().getString(R.string.install_finish_msg, "ERROR_java (IOException)"))
                            .show();
                }
            }
        }
        else super.onActivityResult(requestCode, resultCode, data);
    }

    public void runVM(String commands) {
        ArrayList<ArrayList<String>> commandsparsed = new ArrayList<>();
        for (String command : commands.split("\n")) {
            ArrayList<String> commandParsed = new ArrayList<>();
            StringBuilder tmp = new StringBuilder();
            boolean clipped = false;
            for (char c : (command + " ").toCharArray()) {
                switch (c) {
                    case '\'':
                        clipped = !clipped;
                        break;
                    case ' ':
                        if (clipped) {
                            tmp.append(c);
                            break;
                        }
                        commandParsed.add(tmp.toString());
                        tmp = new StringBuilder();
                        break;
                    default:
                        tmp.append(c);
                        break;
                }
            }
            if (clipped) Log.e("vmparse","malformed command"); else commandsparsed.add(commandParsed);
        }
        vm = new VM(commandsparsed);
        Resources res = this.getResources();
        String p = ConfiguratorActivity.this.getPackageName();
        vm.natives.put("getString",(next, params, var) ->  {
            var.put(params.get(2),res.getString(res.getIdentifier(params.get(3), "string", p)));
            next.run();
        });
        vm.command();
    }
    public interface Native {
        void call(Runnable next, final ArrayList<String> params, final ArrayMap<String, String> var);
    }
    public class VM {
        ArrayList<ArrayList<String>> commands;
        ArrayMap<String, String> var = new ArrayMap<>();
        ArrayMap<String, Native> natives = new ArrayMap<>();
        ProgressDialog progdialog;
        int command = 0;
        public VM(ArrayList<ArrayList<String>> cmdparsed) {
            commands = cmdparsed;
        }
        @SuppressLint("SdCardPath")
        public void command() {
            if (command == commands.size()) {
                Log.i("vm","done");
                return;
            }
            ArrayList<String> cmd = new ArrayList<>();
            for(String param : commands.get(command)) {
                class Dummy {
                    String s;
                }
                Dummy d = new Dummy();
                d.s = param;
                var.forEach((key, val) -> d.s = d.s.replace("%" + key + "%", val));
                cmd.add(d.s);
            }
            switch (cmd.get(0)) {
                case "log":
                    Log.i("vm","log: " + cmd.get(1));
                    command++; command();
                    break;
                case "dialoginfo":
                    new AlertDialog.Builder(ConfiguratorActivity.this)
                            .setTitle(cmd.get(1))
                            .setMessage(cmd.get(2))
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok,(p1,p2) -> {
                                command++; command();
                            })
                            .show();
                    break;
                case "dialogchoice":
                    new AlertDialog.Builder(ConfiguratorActivity.this)
                            .setTitle(cmd.get(1))
                            .setMessage(cmd.get(2))
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok,(p1,p2) -> {
                                command++; command();
                            })
                            .setNegativeButton(R.string.cancel,(p1,p2) -> p1.dismiss())
                            .show();
                    break;
                case "dialogtext":
                    final EditText e = new EditText(ConfiguratorActivity.this);
                    new AlertDialog.Builder(ConfiguratorActivity.this)
                            .setTitle(cmd.get(1))
                            .setView(e)
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok,(p1,p2) -> {
                                var.put(cmd.get(2),e.getText().toString());
                                command++; command();
                            })
                            .show();
                    break;
                case "dialogfile":
                    new ChooserDialog(ConfiguratorActivity.this)
                            .withStartFile("/sdcard/")
                            .withChosenListener((path, pathFile) -> {
                                var.put(cmd.get(1), path);
                                command++; command();
                            })
                            .withOnCancelListener(DialogInterface::dismiss)
                            .build()
                            .show();
                break;
                case "exec":
                    String x = cmd.get(1);
                    new Thread(() -> {
                        final String o = Shell.doShell(x);
                        runOnUiThread(() -> {
                            var.put(cmd.get(2),o);
                            Log.i("abm","o:"+o);
                            command++; command();
                        });
                    }).start();
                    break;
                case "su":
                    String x1 = cmd.get(1);
                    new Thread(() -> {
                        final String o = Shell.doRoot(x1);
                        runOnUiThread(() -> {
                            var.put(cmd.get(2),o);
                            command++; command();
                        });
                    }).start();
                    break;
                case "global":
                    String x2 = cmd.get(1);
                    new Thread(() -> {
                        final String o = Shell.doRootGlobal(x2);
                        runOnUiThread(() -> {
                            var.put(cmd.get(2),o);
                            command++; command();
                        });
                    }).start();
                    break;
                case "dialogloading":
                    progdialog = new ProgressDialog(ConfiguratorActivity.this);
                    progdialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    progdialog.setTitle(cmd.get(1));
                    progdialog.setMessage(cmd.get(2));
                    progdialog.setIndeterminate(true);
                    progdialog.setCanceledOnTouchOutside(false);
                    progdialog.setCancelable(false);
                    progdialog.show();
                    command++; command();
                    break;
                case "dialogloadingquit":
                    progdialog.dismiss();
                    command++; command();
                    break;
                case "set":
                    var.put(cmd.get(1), cmd.get(2));
                    command++; command();
                    break;
                case "native":
                    Objects.requireNonNull(natives.get(cmd.get(1))).call(() -> {
                            command++; command();
                        }
                    ,cmd,var);
                    break;
                default:
                    Log.e("vm","execution aborted: illegal instruction");
                    break;
            }
        }
    }
}
