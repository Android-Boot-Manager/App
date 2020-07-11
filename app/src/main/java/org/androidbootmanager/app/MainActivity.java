package org.androidbootmanager.app;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import static org.androidbootmanager.app.Shell.doShell;
import static org.androidbootmanager.app.Shell.doRoot;
import static org.androidbootmanager.app.Shell.doRootGlobal;

public class MainActivity extends AppCompatActivity {
	File filedir = new File("/data/data/org.androidbootmanager.app/files");
	File cfgfile = new File("/data/abm-part.cfg");
	File assetsdir = new File(filedir + "/../assets");
	String romname = "null";
	String currentDevice;
	ProgressDialog progdialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		if (!filedir.exists()) filedir.mkdir();
		if (!assetsdir.exists()) assetsdir.mkdir();
		try { copyAssets(); doRoot("/data/data/org.androidbootmanager.app/assets/app_is_installed.sh");} catch (RuntimeException e) {e.printStackTrace(); new AlertDialog.Builder(this).setCancelable(true).setOnCancelListener(new DialogInterface.OnCancelListener(){
					@Override
					public void onCancel(DialogInterface p1) {
						MainActivity.this.finish();
					}
				}).setTitle(R.string.fatal).setMessage(R.string.cp_err_msg).show();}
		Window window = this.getWindow();
		window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
		window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
		if (cfgfile.exists()) {
      		setContentView(R.layout.main);
			configurator(null);
		} else {
			setContentView(R.layout.main_notinstall);
		}

    }

	public void configurator(View v) {
		mount(v);
		startActivity(new Intent(this, ConfiguratorActivity.class));
	}

	public void mount(View v) {
		doRootGlobal("mount -t ext4 /dev/block/bootdevice/by-name/oem /data/bootset");
	}

	public void unmount(View v) {
		doRootGlobal("umount /data/bootset");
	}

	public void doInstall(View v) {
		ArrayList<String> deviceList = new ArrayList<String>();
		deviceList.add("cedric");
		deviceList.add("yggdrasil");
		currentDevice = android.os.Build.DEVICE;
		if ((!(deviceList.contains(android.os.Build.DEVICE))) && (!((Switch) findViewById(R.id.mainnotinstallSwitch1)).isChecked())) {new AlertDialog.Builder(this).setCancelable(true).setTitle(R.string.wrong_device_title).setMessage(getResources().getString(R.string.wrong_device_msg, android.os.Build.DEVICE)).show(); return;}
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
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() { 
				@Override
				public void onClick(DialogInterface dialogif, int which) {
					romname = input.getText().toString();
					new AlertDialog.Builder(MainActivity.this)
						.setTitle(R.string.select_droidboot_title)
						.setMessage(R.string.select_droidboot_msg)
						.setCancelable(true)
						.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener(){

							@Override
							public void onClick(DialogInterface p1, int p2) {
								p1.dismiss();
							}
						})
						.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener(){

							@Override
							public void onClick(DialogInterface p1, int p2) {
								Intent intent = new Intent();
								intent.setType("*/*");
								intent.setAction(Intent.ACTION_GET_CONTENT);
								startActivityForResult(intent, 5207);
							}
						})
						.show();

				}
			});
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});
		if (((Switch)findViewById(R.id.mainnotinstallSwitch1)).isChecked()) {
			final ArrayAdapter<String> arr = new ArrayAdapter<String>(this, android.R.layout.select_dialog_singlechoice);
			arr.add("cedric");
			arr.add("yggdrasil");
			new AlertDialog.Builder(this)
				.setTitle(R.string.choose_device)
				.setAdapter(arr, new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface p1, int p2) {
						p1.dismiss();
						String device = arr.getItem(p2);
						currentDevice = device;
						builder.show();
					}
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
					.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener(){

						@Override
						public void onClick(DialogInterface p1, int p2) {
							p1.dismiss();
						}
					})
					.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener(){

						@Override
						public void onClick(DialogInterface p1, int p2) {
							progdialog.show();
							Uri selectedUri = data.getData();
							try {
								InputStream initialStream = getContentResolver().openInputStream(selectedUri);
								File targetFile = new File("/data/data/org.androidbootmanager.app/files/lk2nd.img");
								OutputStream outStream = new FileOutputStream(targetFile);
								copyFile(initialStream, outStream);
								initialStream.close();
								outStream.close();
							} catch (IOException e) {
								progdialog.dismiss();
								new AlertDialog.Builder(MainActivity.this)
									.setTitle(R.string.install_finish_title)
									.setMessage(getResources().getString(R.string.install_finish_msg, "error_cpfail"))
									.show();
								return;
							}
							new AsyncTask(){
								@Override
								protected Object doInBackground(Object[] p1) {
									copyAssets();
									return doRoot(assetsdir + "/app_install.sh '" + "/data/data/org.androidbootmanager.app/files/lk2nd.img" + "' '" + romname + "'");
								}
								@Override
								protected void onPostExecute(Object r) {
									progdialog.dismiss();
									new AlertDialog.Builder(MainActivity.this)
										.setTitle(R.string.install_finish_title)
										.setMessage(getResources().getString(R.string.install_finish_msg, (String)r))
										.show();
								}
							}.execute();
						}
					})
					.show();
			}
		}
	}


	private void copyAssets() {
		File x = new File(assetsdir, "Toolkit");
		File y = new File(assetsdir, "Scripts");
		if (!x.exists())x.mkdir();
		if (!y.exists())y.mkdir();
		copyAssets("Toolkit", "Toolkit");
		copyAssets("Scripts", "Scripts");
		copyAssets("cp", "");
	}

	private void copyAssets(String src, String outp) {
		AssetManager assetManager = getAssets();
		int apk_ver = 0;
		int fs_ver = 0;
		try {
			InputStream in = assetManager.open("cp/asset_ver");
			byte[] buffer = new byte[1024];
			String x = "";
			int read;
			while ((read = in.read(buffer)) != -1) {
				x = x + new String(buffer, 0, read);
			}
			apk_ver = Integer.valueOf(x.charAt(1));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		try {
			String out = "";
			FileReader r = new FileReader(assetsdir + "/asset_ver");
			int read;
			char[] x = new char[1024];
			while ((read = r.read(x)) != -1) {
				out += new String(x, 0, read);
			}
			fs_ver = Integer.valueOf(out);
		} catch (IOException e) {} catch (NumberFormatException e) {}
		if (fs_ver == apk_ver) return;
		String[] files = null;
		try {
			files = assetManager.list(src); 
		} catch (IOException e) {
			Log.e("tag", "Failed to get asset file list.", e);
		}

		for (String filename : files) {
			InputStream in = null;
			OutputStream out = null;
			try {
				in = assetManager.open(src + "/" + filename);
				File outFile = new File("/data/data/org.androidbootmanager.app/assets/" + outp, filename);
				out = new FileOutputStream(outFile);
				copyFile(in, out);
				in.close();
				in = null;
				out.flush();
				out.close();
				out = null;
			} catch (IOException e) {
				Log.e("tag", "Failed to copy asset file: " + filename, e);
			}
		}
		doShell("chmod -R +x /data/data/org.androidbootmanager.app/assets");
	}

	private void copyFile(InputStream in, OutputStream out) throws IOException {
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
		new AsyncTask(){
			@Override
			protected Object doInBackground(Object[] p1) {
				copyAssets();
				return doRoot("/data/data/org.androidbootmanager.app/assets/hello.sh");
			}
			@Override
			protected void onPostExecute(Object r) {
				new AlertDialog.Builder(MainActivity.this)
					.setTitle(R.string.test_root)
					.setMessage(getResources().getString((((String)r).contains("I am root, fine! :)")) ?R.string.root: R.string.no_root))
					.show();
			}
		}.execute();
	}
}
