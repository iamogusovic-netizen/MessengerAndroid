package com.messenger.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final String MESSENGER_URL = "http://89.125.121.18";
    private static final int REQ_MEDIA = 704;
    private Dialog galleryDialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if (Build.VERSION.SDK_INT >= 29) settings.setForceDark(WebSettings.FORCE_DARK_OFF);
        if (Build.VERSION.SDK_INT >= 33) settings.setAlgorithmicDarkeningAllowed(false);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient());
        webView.setDownloadListener((url, agent, disposition, mime) -> downloadFile(url, agent, disposition, mime));
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                requestGalleryAccess();
                return true;
            }
        });
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (state != null) webView.restoreState(state);
        else webView.loadUrl(MESSENGER_URL);
    }

    private boolean hasMediaAccess() {
        if (Build.VERSION.SDK_INT >= 34) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestGalleryAccess() {
        if (hasMediaAccess()) { showGallery(); return; }
        String[] permissions;
        if (Build.VERSION.SDK_INT >= 34) permissions = new String[] {
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        };
        else if (Build.VERSION.SDK_INT >= 33) permissions = new String[] {
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO
        };
        else permissions = new String[] { Manifest.permission.READ_EXTERNAL_STORAGE };
        requestPermissions(permissions, REQ_MEDIA);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MEDIA) {
            if (hasMediaAccess()) showGallery();
            else {
                Toast.makeText(this, "Разреши доступ к фото и видео для отображения галереи", Toast.LENGTH_LONG).show();
                finishFileChooser(null);
            }
        }
    }

    private void downloadFile(String url, String agent, String disposition, String mime) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String name = URLUtil.guessFileName(url, disposition, mime);
            request.setTitle(name);
            request.setDescription("Загрузка из Мессенджера");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            if (agent != null) request.addRequestHeader("User-Agent", agent);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "Файл сохраняется в «Загрузки»", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось скачать файл", Toast.LENGTH_LONG).show();
        }
    }

    private void showGallery() {
        if (fileCallback == null) return;
        galleryDialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(roundBg(Color.WHITE, 22));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(12), dp(12));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = new TextView(this);
        title.setText("Галерея");
        title.setTextColor(Color.rgb(25,25,30));
        title.setTextSize(19);
        title.setTypeface(null, Typeface.BOLD);
        TextView sub = new TextView(this);
        sub.setText("Фото и видео на устройстве");
        sub.setTextColor(Color.GRAY);
        sub.setTextSize(12);
        titles.addView(title); titles.addView(sub);
        TextView close = new TextView(this);
        close.setText("✕"); close.setTextSize(22); close.setTextColor(Color.DKGRAY);
        close.setGravity(Gravity.CENTER); close.setBackground(roundBg(0xFFF1F1F5, 30));
        close.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        close.setOnClickListener(v -> { galleryDialog.dismiss(); finishFileChooser(null); });
        header.addView(titles); header.addView(close); root.addView(header);
        View divider = new View(this); divider.setBackgroundColor(0xFFE8E8ED);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(5), dp(5), dp(5), dp(12));
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        ArrayList<MediaItem> items = loadMedia();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Фотографии не найдены.\nПроверь разрешение на доступ к фото и видео в настройках приложения.");
            empty.setTextColor(0xFF666666); empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(22), dp(35), dp(22), dp(35));
            grid.addView(empty);
        }
        int cols = 3;
        for (int i = 0; i < items.size(); i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + cols, items.size()); j++) {
                MediaItem item = items.get(j);
                FrameLayout cell = new FrameLayout(this);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(116), 1);
                cp.setMargins(dp(3), dp(3), dp(3), dp(3)); cell.setLayoutParams(cp);
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(roundBg(0xFFECECF1, 8));
                try {
                    if (Build.VERSION.SDK_INT >= 29) image.setImageBitmap(getContentResolver().loadThumbnail(item.uri, new android.util.Size(dp(220), dp(220)), null));
                    else image.setImageURI(item.uri);
                } catch (Exception ignored) { image.setImageURI(item.uri); }
                cell.addView(image, new FrameLayout.LayoutParams(-1, -1));
                if (item.video) {
                    TextView badge = new TextView(this);
                    badge.setText("▶ ВИДЕО"); badge.setTextSize(10); badge.setTextColor(Color.WHITE);
                    badge.setPadding(dp(5), dp(3), dp(5), dp(3)); badge.setBackground(roundBg(0xAA111111, 5));
                    FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
                    bp.setMargins(dp(5), 0, 0, dp(5)); cell.addView(badge, bp);
                }
                cell.setOnClickListener(v -> { galleryDialog.dismiss(); finishFileChooser(item.uri); });
                row.addView(cell);
            }
            while (row.getChildCount() < cols) row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(116), 1));
            grid.addView(row);
        }
        root.setPadding(0, 0, 0, dp(4));
        galleryDialog.setContentView(root);
        Window window = galleryDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.35f; window.setAttributes(attrs);
        }
        galleryDialog.setOnCancelListener(d -> finishFileChooser(null));
        galleryDialog.show();
        if (window != null) window.setLayout(-1, (int)(getResources().getDisplayMetrics().heightPixels * 0.78));
    }

    private ArrayList<MediaItem> loadMedia() {
        ArrayList<MediaItem> result = new ArrayList<>();
        String[] projection = { MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED };
        loadCollection(result, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, false);
        loadCollection(result, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, true);
        Collections.sort(result, (a,b) -> Long.compare(b.date, a.date));
        return result;
    }

    private void loadCollection(ArrayList<MediaItem> out, Uri base, String[] projection, boolean video) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(base, projection, null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC");
            if (cursor != null) while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                long date = cursor.getLong(1);
                out.add(new MediaItem(ContentUris.withAppendedId(base, id), date, video));
            }
        } catch (SecurityException e) {
            // Пользователь мог выдать только частичный доступ к медиатеке.
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void finishFileChooser(Uri uri) {
        if (fileCallback != null) {
            ValueCallback<Uri[]> callback = fileCallback;
            fileCallback = null;
            callback.onReceiveValue(uri == null ? null : new Uri[] { uri });
        }
    }
    private GradientDrawable roundBg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    private static class MediaItem {
        Uri uri; long date; boolean video;
        MediaItem(Uri uri, long date, boolean video) { this.uri = uri; this.date = date; this.video = video; }
    }
    @Override public void onBackPressed() {
        if (galleryDialog != null && galleryDialog.isShowing()) { galleryDialog.dismiss(); finishFileChooser(null); }
        else if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (webView != null) webView.saveState(state);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (fileCallback != null) finishFileChooser(null);
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
