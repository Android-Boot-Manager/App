package org.androidbootmanager.app.legacy;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.topjohnwu.superuser.Shell;

import org.androidbootmanager.app.BuildConfig;
import org.androidbootmanager.app.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

@SuppressLint("SdCardPath")
@SuppressWarnings({"ResultOfMethodCallIgnored", "deprecation"})
public class MainActivity extends AppCompatActivity {
    File filedir = new File("/data/data/org.androidbootmanager.app/files");
    File cfgfile = new File("/data/abm-part.cfg");
    File cndfile = new File("/data/abm-codename.cfg");
    File assetsdir = new File(filedir + "/../assets");
    String romname = "null";
    String currentDevice;
    ProgressDialog progdialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!filedir.exists()) filedir.mkdir();
        if (!assetsdir.exists()) assetsdir.mkdir();
        try {
            copyAssets();
            Shell.su("/data/data/org.androidbootmanager.app/assets/app_is_installed.sh").exec();
        } catch (RuntimeException e) {
            e.printStackTrace();
            new AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setOnCancelListener(p1 -> MainActivity.this.finish())
                    .setTitle(R.string.fatal)
                    .setMessage(R.string.cp_err_msg)
                    .show();
        }
        Window window = this.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        if (cfgfile.exists()) {
            setContentView(R.layout.main);
            configurator(null);
        } else {
            setContentView(R.layout.main_notinstall);
            if (!cndfile.exists())
                findViewById(R.id.mainnotinstallButtonUpdate).setVisibility(Button.INVISIBLE);
        }

    }

    public void configurator(View v) {
        mount(v);
        startActivity(new Intent(this, ConfiguratorActivity.class));
    }

    public void doUpdate(View v) {
        if (!new File("/data/data/org.androidbootmanager.app/files/lk2nd.img").exists())
            new AlertDialog.Builder(MainActivity.this)
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
        else {
            progdialog = new ProgressDialog(this);
            progdialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progdialog.setTitle(R.string.installing_title);
            progdialog.setMessage(getResources().getString(R.string.installing_msg));
            progdialog.setIndeterminate(true);
            progdialog.setCanceledOnTouchOutside(false);
            progdialog.setCancelable(false);
            progdialog.show();
            new Thread(() -> {
                Shell.su("cd /data/data/org.androidbootmanager.app/assets/Toolkit && /data/data/org.androidbootmanager.app/assets/Scripts/update/`cat /data/abm-codename.cfg`.sh 2>&1").exec();
                runOnUiThread(() -> {
                    progdialog.dismiss();
                    finish();
                });
            }).start();
        }
    }

    public void mount(View v) {
        Shell.su("sh /data/abm-part.cfg").exec();
    }

    public void unmount(View v) {
        Shell.su("sh /data/abm-part.2.cfg").exec();
    }

    public void doInstall(View v) {
        ArrayList<String> deviceList = new ArrayList<>();
        deviceList.add("cedric");
        deviceList.add("yggdrasil");
        currentDevice = android.os.Build.DEVICE;
        if ((!(deviceList.contains(android.os.Build.DEVICE))) && (!((SwitchMaterial) findViewById(R.id.mainnotinstallSwitch1)).isChecked())) {
            new AlertDialog.Builder(this).setCancelable(true).setTitle(R.string.wrong_device_title).setMessage(getResources().getString(R.string.wrong_device_msg, android.os.Build.DEVICE)).show();
            return;
        }
        if (!((CheckBox) findViewById(R.id.mainnotinstallCheckBox1)).isChecked()) {
            new AlertDialog.Builder(this)
                    .setCancelable(true)
                    .setTitle(R.string.allow_install_unchecked_title)
                    .setMessage(R.string.allow_install_unchecked_msg)
                    .show();
            return;
        }
        progdialog = new ProgressDialog(this);
        progdialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progdialog.setTitle(R.string.installing_title);
        progdialog.setMessage(getResources().getString(R.string.installing_msg));
        progdialog.setIndeterminate(true);
        progdialog.setCanceledOnTouchOutside(false);
        progdialog.setCancelable(false);
        final EditText input = new EditText(this);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(R.string.current_rom_name);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton(R.string.ok, (dialogif, which) -> {
            romname = input.getText().toString();
            System.out.println("installing abm for " + currentDevice);
            Shell.su("echo " + currentDevice + " >/data/abm-codename.cfg").exec();
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.select_droidboot_title)
                    .setMessage(R.string.select_droidboot_msg)
                    .setCancelable(true)
                    .setNegativeButton(R.string.cancel, (p1, p2) -> p1.dismiss())
                    .setPositiveButton(R.string.ok, (p1, p2) -> {
                        Intent intent = new Intent();
                        intent.setType("*/*");
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        startActivityForResult(intent, 5207);
                    })
                    .show();

        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        if (((SwitchMaterial) findViewById(R.id.mainnotinstallSwitch1)).isChecked()) {
            final ArrayAdapter<String> arr = new ArrayAdapter<>(this, android.R.layout.select_dialog_singlechoice);
            arr.add("cedric");
            arr.add("yggdrasil");
            new AlertDialog.Builder(this)
                    .setTitle(R.string.choose_device)
                    .setAdapter(arr, (p1, p2) -> {
                        p1.dismiss();
                        MainActivity.this.currentDevice = arr.getItem(p2);
                        builder.show();
                    })
                    .show();
        } else {
            builder.show();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == 5207) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.sure_title)
                        .setMessage(R.string.sure_msg)
                        .setCancelable(true)
                        .setNegativeButton(R.string.cancel, (p1, p2) -> p1.dismiss())
                        .setPositiveButton(R.string.ok, (p1, p2) -> {
                            progdialog.show();
                            Uri selectedUri = data.getData();
                            try {
                                InputStream initialStream;
                                if (selectedUri != null) {
                                    initialStream = getContentResolver().openInputStream(selectedUri);
                                } else {
                                    throw new IOException("null selected");
                                }
                                File targetFile = new File("/data/data/org.androidbootmanager.app/files/lk2nd.img");
                                OutputStream outStream = new FileOutputStream(targetFile);
                                assert initialStream != null;
                                copyFile(initialStream, outStream);
                                initialStream.close();
                                outStream.close();
                            } catch (IOException e) {
                                progdialog.dismiss();
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle(R.string.install_finish_title)
                                        .setMessage(getResources().getString(R.string.install_finish_msg, "ERROR_java (IOException)"))
                                        .show();
                                return;
                            }
                            new Thread(() -> {
                                copyAssets();
                                final String r = String.join("", Shell.su(assetsdir + "/app_install.sh '" + romname + "' " + currentDevice).exec().getOut());
                                runOnUiThread(() -> {
                                    progdialog.dismiss();
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle(R.string.install_finish_title)
                                            .setMessage(getResources().getString(R.string.install_finish_msg, r))
                                            .show();
                                });
                            }).start();
                        })
                        .show();
            } else if (requestCode == 5208) {
                Uri selectedUri = data.getData();
                try {
                    InputStream initialStream;
                    if (selectedUri != null) {
                        initialStream = getContentResolver().openInputStream(selectedUri);
                    } else {
                        throw new IOException("null selected");
                    }
                    File targetFile = new File("/data/data/org.androidbootmanager.app/files/lk2nd.img");
                    OutputStream outStream = new FileOutputStream(targetFile);
                    assert initialStream != null;
                    copyFile(initialStream, outStream);
                    initialStream.close();
                    outStream.close();
                    doUpdate(null);
                } catch (IOException e) {
                    progdialog.dismiss();
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.install_finish_title)
                            .setMessage(getResources().getString(R.string.install_finish_msg, "ERROR_java (IOException)"))
                            .show();
                }
            }
        } else super.onActivityResult(requestCode, resultCode, data);
    }


    private void copyAssets() {
        copyAssets("Toolkit", "Toolkit");
        copyAssets("Scripts", "Scripts");
        copyAssets("cp", "");
        Shell.sh("chmod -R +x /data/data/org.androidbootmanager.app/assets").exec();
    }

    private void copyAssets(String src, String outp) {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list(src);
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }

        assert files != null;
        for (String filename : files) {
            copyAssets(src, outp, assetManager, filename);
        }
    }

    private void copyAssets(String src, String outp, AssetManager assetManager, String filename) {
        InputStream in;
        OutputStream out;
        try {
            in = assetManager.open(src + "/" + filename);
            File outFile = new File("/data/data/org.androidbootmanager.app/assets/" + outp, filename);
            out = new FileOutputStream(outFile);
            copyFile(in, out);
            in.close();
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            new File(assetsdir + File.separator + outp + File.separator).mkdir();
            try {
                assetManager.open(src + File.separator + filename).close();
                copyAssets(src, outp, assetManager, filename);
            } catch (FileNotFoundException e2) {
                new File(assetsdir + File.separator + outp + File.separator + filename).mkdir();
                copyAssets(src + File.separator + filename, outp + File.separator + filename);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (IOException e) {
            Log.e("tag", "Failed to copy asset file: " + filename, e);
        }
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public void showLic(View v) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.readme_title)
                .setMessage(getResources().getString(R.string.readme_text, "https://github.com/Android-Boot-Manager", "https://github.com/Android-Boot-Manager/App/wiki") + "\n\n   Copyright 2020 The Android Boot Manager Project\nLicensed under the GNU General Public License Version 3 or later\n\nUnless required by applicable law or agreed to in writing, software\ndistributed under the License is distributed on an \"AS IS\" BASIS,\nWITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\nSee the License for the specific language governing permissions and\nlimitations under the License.")
                .show();
    }

    public void testRoot(View v) {
        new Thread(() -> {
            copyAssets();
            final String r = String.join("", Shell.su("/data/data/org.androidbootmanager.app/assets/hello.sh").exec().getOut());
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.test_root)
                    .setMessage(getResources().getString((r.contains("I am root, fine! :)")) ? R.string.root : R.string.no_root))
                    .show());
        }).start();
    }

    static {
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR | Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10));
    }
}
