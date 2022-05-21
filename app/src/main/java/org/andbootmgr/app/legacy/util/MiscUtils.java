package org.andbootmgr.app.legacy.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.topjohnwu.superuser.Shell;

import org.andbootmgr.app.R;

public class MiscUtils {
    public static AlertDialog prog;
    public static void sure(Context c, DialogInterface d, String msg, DialogInterface.OnClickListener v) {
        d.dismiss();
        new AlertDialog.Builder(c)
                .setPositiveButton(R.string.ok, v)
                .setNegativeButton(R.string.cancel, (id, p) -> id.dismiss())
                .setCancelable(true)
                .setTitle(R.string.sure_title)
                .setMessage(msg)
                .show();
    }

    public static void sure(Context c, DialogInterface d, int msg, DialogInterface.OnClickListener v) {
        sure(c,d, c.getString(msg), v);
    }

    public static void w(Context c, String msg, Runnable r) {
        View v = LayoutInflater.from(c).inflate(R.layout.progressdialog, null);
        ((TextView) v.findViewById(R.id.prog_message)).setText(msg);
        prog = new AlertDialog.Builder(c)
        .setCancelable(false)
        .setTitle(R.string.wait)
        .setView(v)
        .show();
        r.run();
    }

    public static void w(Context c, int msg, Runnable r) {
        w(c, c.getString(msg), r);
    }

    public static Shell.ResultCallback w2(Shell.ResultCallback r) {
        return (a) -> {
            r.onResult(a);
            prog.dismiss();
        };
    }

    public static Runnable w2(Runnable r) {
        return () -> {
            r.run();
            prog.dismiss();
        };
    }

    public static Runnable w2t(Activity a, Runnable r) {
        return () -> {
            r.run();
            a.runOnUiThread(() -> prog.dismiss());
        };
    }
}
