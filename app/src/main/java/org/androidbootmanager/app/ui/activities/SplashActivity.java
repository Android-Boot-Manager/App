package org.androidbootmanager.app.ui.activities;

import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.topjohnwu.superuser.Shell;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.util.Constants;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SplashActivity extends AppCompatActivity {

    boolean fail = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Thread(() -> {
            copyAssets();
            runOnUiThread(() -> {
                if (!fail) {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    finish();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.fatal)
                            .setMessage(R.string.cp_err_msg)
                            .setNegativeButton(R.string.cancel, (p1, p2) -> this.finish())
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok, (p1, p2) -> {
                                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                                finish();
                            })
                            .show();
                }
            });
        }).start();
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
            Log.e("ABM_AssetCopy", "Failed to get asset file list.", e);
            fail = true;
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
            File outFile = new File(Constants.assetDir + outp, filename);
            out = new FileOutputStream(outFile);
            copyFile(in, out);
            in.close();
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            // result ignored on purpose as it often fails, but it does it's job if needed
            new File(Constants.assetDir + outp + File.separator).mkdir();
            try {
                assetManager.open(src + File.separator + filename).close();
                copyAssets(src, outp, assetManager, filename);
            } catch (FileNotFoundException e2) {
                // result ignored on purpose as it often fails, but it does it's job if needed
                new File(Constants.assetDir + outp + File.separator + filename).mkdir();
                copyAssets(src + File.separator + filename, outp + File.separator + filename);
            } catch (IOException ex) {
                Log.e("ABM_AssetCopy", "Failed to copy asset file: " + filename, ex);
                fail = true;
            }
        } catch (IOException e) {
            Log.e("ABM_AssetCopy", "Failed to copy asset file: " + filename, e);
            fail = true;
        }
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}