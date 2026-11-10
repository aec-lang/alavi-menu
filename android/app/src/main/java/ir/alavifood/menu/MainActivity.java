package ir.alavifood.menu;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import androidx.core.content.FileProvider;

import com.getcapacitor.BridgeActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends BridgeActivity {

    /** Bridge called from JS as window.Android.saveImage(dataUrl, fileName).
     *  Decodes the PNG and writes it to Pictures/AlaviFood/ via MediaStore
     *  (Android 10+) so it appears in the Gallery, returning "ok" only
     *  after the bytes are actually on disk, or "error: ..." otherwise. */
    public class ImageSaver {
        private String cleanName(String fileName, String defExt) {
            String name = (fileName == null || fileName.isEmpty()) ? ("alavi-" + System.currentTimeMillis() + defExt) : fileName;
            name = name.replaceAll("[^A-Za-z0-9._-]", "_");
            if (!name.toLowerCase().endsWith(defExt)) name += defExt;
            return name;
        }

        private byte[] decode(String dataUrl) {
            if (dataUrl == null) return null;
            String b64 = dataUrl;
            int comma = dataUrl.indexOf(',');
            if (comma >= 0) b64 = dataUrl.substring(comma + 1);
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            return (bytes == null || bytes.length == 0) ? null : bytes;
        }

        @JavascriptInterface
        public String saveImage(String dataUrl, String fileName) {
            if (dataUrl == null || fileName == null) return "error: empty data";
            try {
                String b64 = dataUrl;
                int comma = dataUrl.indexOf(',');
                if (comma >= 0) b64 = dataUrl.substring(comma + 1);
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                if (bytes == null || bytes.length == 0) return "error: empty image";
                String name = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
                if (!name.toLowerCase().endsWith(".png")) name += ".png";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/AlaviFood");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    ContentResolver resolver = getContentResolver();
                    Uri uri = resolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) return "error: MediaStore insert failed";
                    try (OutputStream out = resolver.openOutputStream(uri)) {
                        if (out == null) return "error: cannot open output";
                        out.write(bytes);
                        out.flush();
                    }
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                    return "ok";
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES), "AlaviFood");
                    if (!dir.exists() && !dir.mkdirs()) return "error: cannot create folder";
                    File file = new File(dir, name);
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(bytes);
                        out.flush();
                    }
                    MediaScannerConnection.scanFile(MainActivity.this,
                            new String[]{file.getAbsolutePath()},
                            new String[]{"image/png"}, null);
                    return "ok";
                }
            } catch (Exception e) {
                return "error: " + e.getMessage();
            }
        }

        /** Generic file save (CSV / JSON backup) to Downloads/AlaviFood/.
         *  Returns "ok" only after the bytes are really on disk. */
        @JavascriptInterface
        public String saveFile(String dataUrl, String fileName, String mime) {
            try {
                byte[] bytes = decode(dataUrl);
                if (bytes == null) return "error: empty data";
                String type = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
                String ext = type.contains("csv") ? ".csv" : type.contains("json") ? ".json" : type.contains("png") ? ".png" : "";
                String name = cleanName(fileName, ext);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    values.put(MediaStore.Downloads.MIME_TYPE, type);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AlaviFood");
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    ContentResolver resolver = getContentResolver();
                    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) return "error: MediaStore insert failed";
                    try (OutputStream out = resolver.openOutputStream(uri)) {
                        if (out == null) return "error: cannot open output";
                        out.write(bytes);
                        out.flush();
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                    return "ok";
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AlaviFood");
                    if (!dir.exists() && !dir.mkdirs()) return "error: cannot create folder";
                    File file = new File(dir, name);
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(bytes);
                        out.flush();
                    }
                    MediaScannerConnection.scanFile(MainActivity.this, new String[]{file.getAbsolutePath()}, new String[]{type}, null);
                    return "ok";
                }
            } catch (Exception e) {
                return "error: " + e.getMessage();
            }
        }

        /** Open the phone's own share menu (ACTION_SEND chooser) with the
         *  invoice image. Returns "ok" only after the chooser was launched. */
        @JavascriptInterface
        public String shareImage(String dataUrl, String fileName, String title) {
            try {
                byte[] bytes = decode(dataUrl);
                if (bytes == null) return "error: empty data";
                String name = cleanName(fileName, ".png");
                File dir = new File(getCacheDir(), "share");
                if (!dir.exists() && !dir.mkdirs()) return "error: cannot create folder";
                final File file = new File(dir, name);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(bytes);
                    out.flush();
                }
                final String subject = (title == null || title.isEmpty()) ? "فاکتور چلوکبابی علوی" : title;
                final AtomicReference<String> result = new AtomicReference<>("error: share did not launch");
                final CountDownLatch latch = new CountDownLatch(1);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", file);
                            Intent intent = new Intent(Intent.ACTION_SEND);
                            intent.setType("image/png");
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                            intent.putExtra(Intent.EXTRA_TEXT, subject);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(intent, subject));
                            result.set("ok");
                        } catch (Exception e) {
                            result.set("error: " + e.getMessage());
                        }
                        latch.countDown();
                    }
                });
                latch.await(5, TimeUnit.SECONDS);
                return result.get();
            } catch (Exception e) {
                return "error: " + e.getMessage();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Capacitor's Bridge is ready after super.onCreate(); attach the
        // gallery-save bridge to its WebView.
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().addJavascriptInterface(new ImageSaver(), "Android");
        }
    }
}
