package com.messenger.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.app.DownloadManager;
import android.content.Context;
import android.os.Environment;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
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
import android.content.res.ColorStateList;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final String MESSENGER_URL = "http://89.125.121.18";
    private static final int REQ_MEDIA = 704;
    private boolean waitingForGallery = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        webView = new WebView(this); setContentView(webView);
        WebSettings s = webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true); s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true); s.setAllowFileAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        // Не позволяем системной тёмной теме Android перекрашивать сайт.
        if (Build.VERSION.SDK_INT >= 33) s.setAlgorithmicDarkeningAllowed(false);
        if (Build.VERSION.SDK_INT >= 29) s.setForceDark(WebSettings.FORCE_DARK_OFF);
        CookieManager cm = CookieManager.getInstance(); cm.setAcceptCookie(true); cm.setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> downloadFile(url, userAgent, contentDisposition, mimeType));
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback; requestGalleryAccess(); return true;
            }
        });
        // Всегда загружаем сайт с сервера при запуске приложения,
        // чтобы получать последние версии интерфейса без пересборки APK.
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.loadUrl(MESSENGER_URL);
    }

    private void requestGalleryAccess() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= 34) perms = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        else if (Build.VERSION.SDK_INT >= 33) perms = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        else perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        ArrayList<String> missing = new ArrayList<>();
        for (String p : perms) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty()) { waitingForGallery = true; requestPermissions(missing.toArray(new String[0]), REQ_MEDIA); }
        else showGallery();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MEDIA && waitingForGallery) {
            waitingForGallery = false;
            boolean granted = false;
            for (String permission : permissions) {
                if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) { granted = true; break; }
            }
            if (granted) showGallery();
            else { Toast.makeText(this, "Разреши доступ к фото и видео, чтобы открыть галерею", Toast.LENGTH_LONG).show(); finishFileChooser(null); }
        }
    }

    // Скачивание файлов из WebView в системную папку «Загрузки».
    // Android сам показывает уведомление о ходе загрузки; доступ ко всему хранилищу не нужен.
    private void downloadFile(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(fileName);
            request.setDescription("Загрузка из Мессенджера");
            request.setMimeType(mimeType);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "Файл скачивается в папку «Загрузки»", Toast.LENGTH_LONG).show();
            } else Toast.makeText(this, "Не удалось запустить загрузку", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка загрузки файла", Toast.LENGTH_LONG).show();
        }
    }

    private void showGallery() {
        if (fileCallback == null) return;
        final Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackground(roundBg(Color.WHITE, 24));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(18), dp(12), dp(12), dp(12));
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = new TextView(this); title.setText("Галерея"); title.setTextColor(Color.rgb(25,25,30)); title.setTextSize(19); title.setTypeface(null, Typeface.BOLD);
        TextView sub = new TextView(this); sub.setText("Выбери фото или видео"); sub.setTextColor(Color.GRAY); sub.setTextSize(12); titles.addView(title); titles.addView(sub);
        TextView close = new TextView(this); close.setText("✕"); close.setTextSize(22); close.setTextColor(Color.DKGRAY); close.setGravity(Gravity.CENTER); close.setBackground(roundBg(0xFFF1F1F5, 30)); close.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        close.setOnClickListener(v -> { dialog.dismiss(); finishFileChooser(null); }); header.addView(titles); header.addView(close); root.addView(header);
        View line = new View(this); line.setBackgroundColor(0xFFE8E8ED); root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL); grid.setPadding(dp(5), dp(5), dp(5), dp(12)); scroll.addView(grid); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        ArrayList<MediaItem> items = loadMedia();
        if (items.isEmpty()) { TextView empty = new TextView(this); empty.setText("Медиа не найдено или доступ не разрешён."); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20), dp(30), dp(20), dp(30)); grid.addView(empty); }
        int cols = 3;
        for (int i=0; i<items.size(); i+=cols) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j=i; j<Math.min(i+cols, items.size()); j++) {
                MediaItem item = items.get(j); FrameLayout cell = new FrameLayout(this); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(116), 1); cp.setMargins(dp(3), dp(3), dp(3), dp(3)); cell.setLayoutParams(cp);
                ImageView img = new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); img.setImageURI(item.uri); img.setBackground(roundBg(0xFFECECF1, 8)); cell.addView(img, new FrameLayout.LayoutParams(-1,-1));
                if (item.video) { TextView badge = new TextView(this); badge.setText("▶ ВИДЕО"); badge.setTextSize(10); badge.setTextColor(Color.WHITE); badge.setPadding(dp(5),dp(3),dp(5),dp(3)); badge.setBackground(roundBg(0xAA111111, 5)); FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START); bp.setMargins(dp(5),0,0,dp(5)); cell.addView(badge,bp); }
                cell.setOnClickListener(v -> { dialog.dismiss(); finishFileChooser(item.uri); }); row.addView(cell);
            }
            while (row.getChildCount()<cols) { View spacer = new View(this); row.addView(spacer,new LinearLayout.LayoutParams(0,dp(116),1)); }
            grid.addView(row);
        }
        root.setPadding(0,0,0,dp(4)); dialog.setContentView(root); Window w=dialog.getWindow(); if(w!=null){ w.setBackgroundDrawableResource(android.R.color.transparent); w.setLayout(-1, (int)(getResources().getDisplayMetrics().heightPixels*0.62)); w.setGravity(Gravity.BOTTOM); w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams a=w.getAttributes(); a.dimAmount=0.35f; w.setAttributes(a); }
        dialog.setOnCancelListener(d -> finishFileChooser(null)); dialog.show(); if(w!=null) w.setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*0.62));
    }

    private ArrayList<MediaItem> loadMedia() {
        ArrayList<MediaItem> out = new ArrayList<>();
        Uri images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI, videos = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED};
        loadCollection(out, images, projection, false); loadCollection(out, videos, projection, true);
        java.util.Collections.sort(out, (a,b) -> Long.compare(b.date, a.date)); return out;
    }
    private void loadCollection(ArrayList<MediaItem> out, Uri base, String[] projection, boolean video) {
        Cursor c=null; try { c=getContentResolver().query(base,projection,null,null,MediaStore.MediaColumns.DATE_ADDED+" DESC"); if(c!=null) while(c.moveToNext()) { long id=c.getLong(0), date=c.getLong(1); out.add(new MediaItem(ContentUris.withAppendedId(base,id),date,video)); } } catch(Exception ignored) {} finally { if(c!=null)c.close(); }
    }
    private void finishFileChooser(Uri uri) { if(fileCallback!=null){ ValueCallback<Uri[]> cb=fileCallback; fileCallback=null; cb.onReceiveValue(uri==null?null:new Uri[]{uri}); } }
    private GradientDrawable roundBg(int color, int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private static class MediaItem { Uri uri; long date; boolean video; MediaItem(Uri u,long d,boolean v){uri=u;date=d;video=v;} }
    @Override public void onBackPressed(){ if(webView!=null&&webView.canGoBack())webView.goBack(); else super.onBackPressed(); }
    @Override protected void onSaveInstanceState(Bundle b){ if(webView!=null)webView.saveState(b); super.onSaveInstanceState(b); }
    @Override protected void onDestroy(){ if(fileCallback!=null)finishFileChooser(null); if(webView!=null){webView.destroy();webView=null;} super.onDestroy(); }
}
