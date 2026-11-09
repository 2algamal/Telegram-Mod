package org.telegram.messenger;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashLogger {
    private static final String TAG = "TGVirtualCrash";
    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static Context appContext;
    private static final Object LOCK = new Object();
    private static Uri downloadUri = null;
    private static boolean downloadsReset = false;

    public static void init(Context context) {
        if (appContext != null) return;
        appContext = context.getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            log("FATAL", "Uncaught in " + thread.getName(), e);
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
        log("INFO", "=== CrashLogger init, pkg=" + appContext.getPackageName() + " ===", null);
    }

    public static void log(String level, String message, Throwable t) {
        Log.e(TAG, "[" + level + "] " + message, t);
        if (appContext == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(SDF.format(new Date())).append(" [").append(level).append("] ").append(message).append("\n");
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString()).append("\n");
        }
        String line = sb.toString();
        synchronized (LOCK) {
            try {
                File f = new File(appContext.getExternalFilesDir(null), "telegram-virtual-crash.log");
                try (FileOutputStream fos = new FileOutputStream(f, true)) {
                    fos.write(line.getBytes());
                }
            } catch (Throwable ignored) {}
            try {
                writeToDownloads(line);
            } catch (Throwable ignored) {}
        }
    }

    private static void writeToDownloads(String line) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            if (!downloadsReset) {
                try {
                    appContext.getContentResolver().delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        MediaStore.Downloads.DISPLAY_NAME + "=?",
                        new String[]{"telegram-virtual-crash.log"});
                } catch (Throwable ignored) {}
                downloadsReset = true;
            }
            if (downloadUri == null) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, "telegram-virtual-crash.log");
                cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                downloadUri = appContext.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            }
            if (downloadUri != null) {
                try (OutputStream os = appContext.getContentResolver().openOutputStream(downloadUri, "wa")) {
                    if (os != null) os.write(line.getBytes());
                }
            }
        } else {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloads.exists()) downloads.mkdirs();
            File f = new File(downloads, "telegram-virtual-crash.log");
            try (FileOutputStream fos = new FileOutputStream(f, true)) {
                fos.write(line.getBytes());
            }
        }
    }
}
