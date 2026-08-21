package com.appmada.numerologiemada;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidFiles(this), "AndroidFiles");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/numerologie-mada.html?v=104");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public static final class AndroidFiles {
        private final Activity activity;
        private final Handler main = new Handler(Looper.getMainLooper());

        AndroidFiles(Activity activity) { this.activity = activity; }

        @JavascriptInterface
        public void savePdf(String base64Data, String requestedName) {
            new Thread(() -> {
                try {
                    SavedPdf saved = writePdf(base64Data, requestedName);
                    toast("PDF enregistré dans Téléchargements/Numérologie Mada/" + saved.name);
                } catch (Exception e) {
                    toast("Enregistrement PDF impossible : " + safeMessage(e));
                }
            }).start();
        }

        @JavascriptInterface
        public void sharePdf(String base64Data, String requestedName) {
            new Thread(() -> {
                try {
                    SavedPdf saved = writePdf(base64Data, requestedName);
                    main.post(() -> {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("application/pdf");
                        send.putExtra(Intent.EXTRA_STREAM, saved.uri);
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        activity.startActivity(Intent.createChooser(send, "Partager le rapport PDF"));
                    });
                } catch (Exception e) {
                    toast("Partage PDF impossible : " + safeMessage(e));
                }
            }).start();
        }

        private SavedPdf writePdf(String base64Data, String requestedName) throws Exception {
            if (Build.VERSION.SDK_INT < 29) {
                throw new IllegalStateException("Android 10 ou plus requis pour cet export direct");
            }
            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            if (bytes.length < 8 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
                throw new IllegalArgumentException("contenu PDF invalide");
            }
            String name = sanitize(requestedName);
            ContentResolver resolver = activity.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Numérologie Mada");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("stockage indisponible");
            try (OutputStream out = resolver.openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("écriture impossible");
                out.write(bytes);
                out.flush();
            } catch (Exception e) {
                resolver.delete(uri, null, null);
                throw e;
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return new SavedPdf(uri, name);
        }

        private static String sanitize(String requestedName) {
            String name = requestedName == null ? "Rapport-Numerologie.pdf" : requestedName.trim();
            name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "-");
            if (name.length() == 0) name = "Rapport-Numerologie.pdf";
            if (!name.toLowerCase().endsWith(".pdf")) name += ".pdf";
            if (name.length() > 120) name = name.substring(0, 116) + ".pdf";
            return name;
        }

        private static String safeMessage(Exception e) {
            String s = e.getMessage();
            return s == null || s.length() == 0 ? "erreur Android" : s;
        }

        private void toast(String message) {
            main.post(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
        }

        private static final class SavedPdf {
            final Uri uri;
            final String name;
            SavedPdf(Uri uri, String name) { this.uri = uri; this.name = name; }
        }
    }
}
