package com.messenger.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
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
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String MESSENGER_URL = "http://89.125.121.18";
    private static final int REQ_MEDIA = 704;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private boolean waitingForGallery;
    private Dialog galleryDialog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService thumbExecutor = Executors.newFixedThreadPool(3);
    private final ArrayList<MediaItem> allMedia = new ArrayList<>();
    private final ArrayList<MediaItem> visibleMedia = new ArrayList<>();
    private final Set<Uri> selected = new HashSet<>();
    private GridView grid;
    private GalleryAdapter adapter;
    private TextView selectedLabel;
    private TextView allTab, photoTab, videoTab;
    private String currentFilter = "all";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true); s.setAllowFileAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if (Build.VERSION.SDK_INT >= 33) s.setAlgorithmicDarkeningAllowed(false);
        CookieManager cm = CookieManager.getInstance(); cm.setAcceptCookie(true); cm.setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // On Android, the paperclip opens our in-app media grid directly,
                // bypassing the website's intermediate attachment menu.
                String js = "(function(){var b=document.getElementById('attachButton');var i=document.getElementById('mediaInput');if(!b||!i||b.dataset.nativeGalleryBound==='1')return;b.dataset.nativeGalleryBound='1';b.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();i.click();},true);})();";
                view.evaluateJavascript(js, null);
            }
        });
        webView.setDownloadListener((url, ua, disposition, mime) -> downloadFile(url, ua, disposition, mime));
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                requestGalleryAccess();
                return true;
            }
        });
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.loadUrl(MESSENGER_URL);
    }

    private void requestGalleryAccess() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= 34) permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        else if (Build.VERSION.SDK_INT >= 33) permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        else permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        ArrayList<String> missing = new ArrayList<>();
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        }
        if (!missing.isEmpty()) {
            waitingForGallery = true;
            requestPermissions(missing.toArray(new String[0]), REQ_MEDIA);
        } else showGallery();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MEDIA && waitingForGallery) {
            waitingForGallery = false;
            boolean granted = hasAnyMediaPermission();
            if (granted) showGallery();
            else { Toast.makeText(this, "Разреши доступ к фото и видео", Toast.LENGTH_LONG).show(); finishFileChooser(null); }
        }
    }

    private boolean hasAnyMediaPermission() {
        if (Build.VERSION.SDK_INT >= 34) return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= 33) return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void downloadFile(String url, String userAgent, String disposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String name = URLUtil.guessFileName(url, disposition, mimeType);
            request.setTitle(name); request.setDescription("Загрузка из Мессенджера"); request.setMimeType(mimeType);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) { manager.enqueue(request); Toast.makeText(this, "Файл сохраняется в «Загрузки»", Toast.LENGTH_LONG).show(); }
        } catch (Exception e) { Toast.makeText(this, "Не удалось скачать файл", Toast.LENGTH_LONG).show(); }
    }

    private void showGallery() {
        if (fileCallback == null) return;
        selected.clear(); allMedia.clear(); visibleMedia.clear(); currentFilter = "all";
        loadMedia();
        galleryDialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackground(roundBg(Color.WHITE, 22));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(18), dp(14), dp(14), dp(12));
        LinearLayout heading = new LinearLayout(this); heading.setOrientation(LinearLayout.VERTICAL); heading.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = text("Фото и видео", 19, 0xFF17212B, true); heading.addView(title);
        TextView subtitle = text("Выберите файлы для отправки", 12, 0xFF77818C, false); heading.addView(subtitle);
        TextView close = text("✕", 20, 0xFF536273, true); close.setGravity(Gravity.CENTER); close.setBackground(roundBg(0xFFF0F3F6, 24)); close.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
        close.setOnClickListener(v -> closeGallery(false)); header.addView(heading); header.addView(close); root.addView(header);

        LinearLayout tabs = new LinearLayout(this); tabs.setPadding(dp(14), dp(2), dp(14), dp(10));
        allTab = tab("Все", true); photoTab = tab("Фото", false); videoTab = tab("Видео", false);
        allTab.setOnClickListener(v -> filter("all")); photoTab.setOnClickListener(v -> filter("photo")); videoTab.setOnClickListener(v -> filter("video"));
        tabs.addView(allTab); tabs.addView(photoTab); tabs.addView(videoTab); root.addView(tabs);
        View divider = new View(this); divider.setBackgroundColor(0xFFE9EDF1); root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        grid = new GridView(this); grid.setNumColumns(3); grid.setHorizontalSpacing(dp(3)); grid.setVerticalSpacing(dp(3)); grid.setPadding(dp(5), dp(5), dp(5), dp(8)); grid.setClipToPadding(false); grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        adapter = new GalleryAdapter(); grid.setAdapter(adapter); root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL); footer.setPadding(dp(16), dp(10), dp(16), dp(12)); footer.setBackgroundColor(Color.WHITE);
        selectedLabel = text("Выбрано: 0", 14, 0xFF66717D, false); footer.addView(selectedLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView send = text("Отправить", 14, Color.WHITE, true); send.setGravity(Gravity.CENTER); send.setPadding(dp(20), dp(11), dp(20), dp(11)); send.setBackground(roundBg(0xFF3390EC, 22));
        send.setOnClickListener(v -> closeGallery(true)); footer.addView(send); root.addView(footer);

        galleryDialog.setContentView(root); galleryDialog.setCancelable(true); galleryDialog.setOnCancelListener(d -> finishFileChooser(null));
        Window w = galleryDialog.getWindow(); if (w != null) { w.setBackgroundDrawableResource(android.R.color.transparent); w.setGravity(Gravity.BOTTOM); w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams a = w.getAttributes(); a.dimAmount = 0.32f; w.setAttributes(a); }
        galleryDialog.show(); if (w != null) w.setLayout(-1, (int)(getResources().getDisplayMetrics().heightPixels * 0.88f));
        updateSelectionLabel();
    }

    private TextView tab(String label, boolean active) {
        TextView t = text(label, 14, active ? 0xFF2488E8 : 0xFF6D7884, true);
        t.setGravity(Gravity.CENTER); t.setPadding(dp(16), dp(8), dp(16), dp(8)); t.setBackground(roundBg(active ? 0xFFE8F3FF : 0xFFF3F5F7, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.setMargins(0, 0, dp(8), 0); t.setLayoutParams(p); return t;
    }

    private void filter(String filter) {
        currentFilter = filter; visibleMedia.clear();
        for (MediaItem item : allMedia) if (filter.equals("all") || (filter.equals("video") == item.video)) visibleMedia.add(item);
        setTab(allTab, filter.equals("all")); setTab(photoTab, filter.equals("photo")); setTab(videoTab, filter.equals("video"));
        if (adapter != null) adapter.notifyDataSetChanged();
    }
    private void setTab(TextView t, boolean active) { t.setTextColor(active ? 0xFF2488E8 : 0xFF6D7884); t.setBackground(roundBg(active ? 0xFFE8F3FF : 0xFFF3F5F7, 18)); }

    private void loadMedia() {
        Uri files = MediaStore.Files.getContentUri("external");
        String[] projection = {MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.DATE_ADDED};
        String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=?";
        String[] args = {String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE), String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)};
        Cursor c = null;
        try {
            c = getContentResolver().query(files, projection, selection, args, MediaStore.MediaColumns.DATE_ADDED + " DESC");
            if (c != null) while (c.moveToNext()) {
                long id = c.getLong(0); int type = c.getInt(1); long date = c.getLong(2);
                boolean video = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                allMedia.add(new MediaItem(Uri.withAppendedPath(base, String.valueOf(id)), date, video));
            }
        } catch (SecurityException ignored) { } catch (Exception ignored) { } finally { if (c != null) c.close(); }
        Collections.sort(allMedia, (a,b) -> Long.compare(b.date, a.date));
        visibleMedia.addAll(allMedia);
    }

    private void toggle(MediaItem item) {
        if (selected.contains(item.uri)) selected.remove(item.uri); else selected.add(item.uri);
        adapter.notifyDataSetChanged(); updateSelectionLabel();
    }
    private void updateSelectionLabel() { if (selectedLabel != null) selectedLabel.setText("Выбрано: " + selected.size()); }

    private void closeGallery(boolean send) {
        ArrayList<Uri> chosen = new ArrayList<>();
        if (send) for (MediaItem item : allMedia) if (selected.contains(item.uri)) chosen.add(item.uri);
        if (galleryDialog != null && galleryDialog.isShowing()) galleryDialog.dismiss();
        galleryDialog = null;
        if (send && !chosen.isEmpty()) finishFileChooser(chosen.toArray(new Uri[0])); else finishFileChooser(null);
    }

    private void finishFileChooser(Uri... uris) {
        if (fileCallback != null) { ValueCallback<Uri[]> callback = fileCallback; fileCallback = null; callback.onReceiveValue(uris == null || uris.length == 0 ? null : uris); }
    }

    private class GalleryAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleMedia.size(); }
        @Override public Object getItem(int position) { return visibleMedia.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convert, ViewGroup parent) {
            FrameLayout cell = convert instanceof FrameLayout ? (FrameLayout) convert : new FrameLayout(MainActivity.this);
            int size = (getResources().getDisplayMetrics().widthPixels - dp(22)) / 3;
            cell.setLayoutParams(new GridView.LayoutParams(size, size));
            ImageView image;
            if (cell.getChildCount() == 0) { image = new ImageView(MainActivity.this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); cell.addView(image, new FrameLayout.LayoutParams(-1,-1)); }
            image = (ImageView) cell.getChildAt(0);
            MediaItem item = visibleMedia.get(position); image.setImageDrawable(roundBg(0xFFE9EDF1, 7)); image.setTag(item.uri);
            TextView check;
            if (cell.getChildCount() < 2) { check = text("", 12, Color.WHITE, true); check.setGravity(Gravity.CENTER); FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.TOP|Gravity.END); cp.setMargins(0, dp(6), dp(6), 0); cell.addView(check, cp); }
            check = (TextView) cell.getChildAt(1);
            boolean isSelected = selected.contains(item.uri); check.setText(isSelected ? "✓" : ""); check.setBackground(roundBg(isSelected ? 0xFF2488E8 : 0x99000000, 18));
            if (item.video) { TextView video = cell.findViewWithTag("video"); if (video == null) { video = text("▶", 13, Color.WHITE, true); video.setTag("video"); video.setGravity(Gravity.CENTER); video.setBackground(roundBg(0x99000000, 18)); FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.BOTTOM|Gravity.START); vp.setMargins(dp(6),0,0,dp(6)); cell.addView(video, vp); } } else { View old = cell.findViewWithTag("video"); if (old != null) cell.removeView(old); }
            final ImageView target = image;
            thumbExecutor.execute(() -> {
                Bitmap bitmap = null;
                try { if (Build.VERSION.SDK_INT >= 29) bitmap = getContentResolver().loadThumbnail(item.uri, new android.util.Size(dp(180), dp(180)), null); }
                catch (Exception ignored) { }
                final Bitmap result = bitmap;
                mainHandler.post(() -> { if (result != null && item.uri.equals(target.getTag())) target.setImageBitmap(result); });
            });
            cell.setOnClickListener(v -> toggle(item));
            return cell;
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(null, Typeface.BOLD); return t;
    }
    private GradientDrawable roundBg(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    @Override public void onBackPressed() { if (galleryDialog != null && galleryDialog.isShowing()) closeGallery(false); else if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { if (galleryDialog != null && galleryDialog.isShowing()) galleryDialog.dismiss(); if (fileCallback != null) finishFileChooser(null); thumbExecutor.shutdownNow(); if (webView != null) { webView.destroy(); webView = null; } super.onDestroy(); }
}
