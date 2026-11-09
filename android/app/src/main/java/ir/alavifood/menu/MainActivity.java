package ir.alavifood.menu;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import com.getcapacitor.BridgeActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends BridgeActivity {

    /** Bridge called from JS as window.Android.saveImage(dataUrl, fileName).
     *  Decodes the PNG and writes it to Pictures/AlaviFood/ via MediaStore
     *  (Android 10+) so it appears in the Gallery, returning "ok" only
     *  after the bytes are actually on disk, or "error: ..." otherwise. */
    public class ImageSaver {
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
