package com.example.blankapp;

import android.app.Activity;
import android.app.DownloadManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.content.Intent;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.provider.Settings;
import android.database.Cursor;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

public class MainActivity extends Activity {
    private static final String RELEASE_API = "https://api.github.com/repos/4xl-summer/android_app001/releases/latest";
    private TextView status;
    private Button checkButton;
    private String apkUrl;
    private int latestCode;
    private long downloadId = -1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(0xFFFFFFFF);
        status = new TextView(this);
        status.setTextColor(0xFF222222);
        status.setTextSize(17);
        status.setGravity(Gravity.CENTER);
        status.setText("Blank App v" + BuildConfig.VERSION_NAME);
        root.addView(status);
        checkButton = new Button(this);
        checkButton.setText("업데이트 확인");
        root.addView(checkButton);
        checkButton.setOnClickListener(v -> checkForUpdates());
        setContentView(root);
    }

    private void show(String message) { runOnUiThread(() -> status.setText(message)); }

    private void checkForUpdates() {
        checkButton.setEnabled(false);
        show("새 버전 확인 중...");
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "BlankApp-Updater");
                if (conn.getResponseCode() != 200) throw new Exception("릴리스 조회 실패: HTTP " + conn.getResponseCode());
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] bytes = new byte[8192];
                    int count;
                    while ((count = in.read(bytes)) != -1) buffer.write(bytes, 0, count);
                }
                JSONObject release = new JSONObject(buffer.toString("UTF-8"));
                String tag = release.getString("tag_name");
                if (!tag.matches("v[0-9]+")) throw new Exception("지원하지 않는 버전 태그: " + tag);
                latestCode = Integer.parseInt(tag.substring(1));
                JSONArray assets = release.getJSONArray("assets");
                apkUrl = null;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if ("blank-app.apk".equals(asset.getString("name"))) {
                        apkUrl = asset.getString("browser_download_url");
                        break;
                    }
                }
                if (apkUrl == null) throw new Exception("릴리스에 APK가 없습니다.");
                if (latestCode <= BuildConfig.VERSION_CODE) {
                    show("이미 최신 버전입니다. (" + BuildConfig.VERSION_NAME + ")");
                } else {
                    runOnUiThread(() -> {
                        status.setText("새 버전 v1.0." + latestCode + " 발견");
                        checkButton.setText("업데이트 다운로드");
                        checkButton.setOnClickListener(v -> downloadUpdate());
                    });
                }
            } catch (Exception e) { show("업데이트 확인 실패: " + e.getMessage()); }
            finally {
                if (conn != null) conn.disconnect();
                runOnUiThread(() -> checkButton.setEnabled(true));
            }
        }).start();
    }

    private void downloadUpdate() {
        if (apkUrl == null || !apkUrl.startsWith("https://github.com/4xl-summer/android_app001/releases/download/")) {
            show("잘못된 다운로드 주소입니다.");
            return;
        }
        checkButton.setEnabled(false);
        try {
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("Blank App 업데이트");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "blank-app-update.apk");
            downloadId = manager.enqueue(request);
            show("APK 다운로드 중...");
            new Thread(() -> waitForDownload(manager, downloadId)).start();
        } catch (Exception e) {
            show("다운로드 시작 실패: " + e.getMessage());
            checkButton.setEnabled(true);
        }
    }

    private void waitForDownload(DownloadManager manager, long id) {
        for (int i = 0; i < 300; i++) {
            try {
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
                try (Cursor cursor = manager.query(query)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        if (state == DownloadManager.STATUS_SUCCESSFUL) {
                            Uri uri = manager.getUriForDownloadedFile(id);
                            runOnUiThread(() -> installUpdate(uri));
                            return;
                        }
                        if (state == DownloadManager.STATUS_FAILED) {
                            show("다운로드 실패. 다시 시도하세요.");
                            runOnUiThread(() -> checkButton.setEnabled(true));
                            return;
                        }
                    }
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                show("다운로드 확인 실패: " + e.getMessage());
                runOnUiThread(() -> checkButton.setEnabled(true));
                return;
            }
        }
        show("다운로드 대기 시간이 초과됐습니다.");
        runOnUiThread(() -> checkButton.setEnabled(true));
    }

    private void installUpdate(Uri uri) {
        if (uri == null) { show("APK 파일을 찾지 못했습니다."); return; }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            show("이 앱의 '알 수 없는 앱 설치' 권한을 허용한 뒤 다시 업데이트를 눌러주세요.");
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            checkButton.setEnabled(true);
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            show("설치 화면에서 업데이트를 승인하세요.");
        } catch (ActivityNotFoundException e) {
            show("APK 설치 앱을 찾지 못했습니다.");
        }
        checkButton.setEnabled(true);
    }
}
