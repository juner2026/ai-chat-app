package com.juner2026.aichat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * AI Chat 壳：把 assets/index.html 当作 https://appassets.androidplatform.net/ 下的网页跑。
 * 用 https 域名而不是 file://，是为了让 IndexedDB / fetch / CORS / 剪贴板 都跟正常网页一样。
 */
public class MainActivity extends Activity {

    private static final String TAG = "AIChat";
    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";
    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_FILE = 1001;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private long lastBack = 0L;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 19) WebView.setWebContentsDebuggingEnabled(true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage + IndexedDB 的命根子
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);   // 语音条自动播
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            s.setSafeBrowsingEnabled(false);
        }

        web.addJavascriptInterface(new Saver(), "AndroidSaver");

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                String sch = u.getScheme() == null ? "" : u.getScheme();
                if (HOST.equals(u.getHost())) return false;   // 内部页面
                if (sch.equals("http") || sch.equals("https") || sch.equals("mailto")
                        || sch.equals("tel") || sch.equals("weixin") || sch.equals("alipays")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (Exception e) {
                        Log.w(TAG, "open external fail", e);
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                try {
                    String js = readAsset("inject.js");
                    if (js != null && js.length() > 0) v.evaluateJavascript(js, null);
                } catch (Exception e) { }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // 麦克风 / 相机：直接放行
                runOnUiThread(new Runnable() {
                    @Override public void run() { request.grant(request.getResources()); }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) { fileCb.onReceiveValue(null); }
                fileCb = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                    return true;
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
            }

            @Override
            public boolean onJsAlert(WebView v, String url, String msg, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(msg)
                        .setPositiveButton("好", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) { r.confirm(); }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override public void onCancel(DialogInterface d) { r.cancel(); }
                        })
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, final JsResult r) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(msg)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) { r.confirm(); }
                        })
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) { r.cancel(); }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override public void onCancel(DialogInterface d) { r.cancel(); }
                        })
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView v, String url, String msg, String def, final JsPromptResult r) {
                final EditText et = new EditText(MainActivity.this);
                et.setText(def == null ? "" : def);
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(msg)
                        .setView(et)
                        .setPositiveButton("确��", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) { r.confirm(et.getText().toString()); }
                        })
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) { r.cancel(); }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override public void onCancel(DialogInterface d) { r.cancel(); }
                        })
                        .show();
                return true;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.i(TAG, cm.message() + " @" + cm.sourceId() + ":" + cm.lineNumber());
                return true;
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                if (url != null && url.startsWith("http")) {
                    try {
                        android.app.DownloadManager.Request rq =
                                new android.app.DownloadManager.Request(Uri.parse(url));
                        rq.addRequestHeader("User-Agent", ua);
                        rq.addRequestHeader("Cookie", android.webkit.CookieManager.getInstance().getCookie(url));
                        rq.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        rq.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, guessName(cd, url));
                        android.app.DownloadManager dm = (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        dm.enqueue(rq);
                        toast("开始下载…");
                    } catch (Exception e) {
                        Log.w(TAG, "download fail", e);
                    }
                }
            }
        });

        if (b != null) {
            web.restoreState(b);
        } else {
            web.loadUrl(START_URL);
        }
    }

    private String guessName(String cd, String url) {
        try {
            if (cd != null && cd.contains("filename=")) {
                String n = cd.substring(cd.indexOf("filename=") + 9).replace("\"", "").trim();
                if (n.length() > 0) return n;
            }
            int i = url.lastIndexOf('/');
            if (i >= 0 && i < url.length() - 1) return url.substring(i + 1).split("\\?")[0];
        } catch (Exception e) { }
        return "download_" + System.currentTimeMillis();
    }

    /** 供页面调用的桥：把 blob / data: 内容落盘 */
    public class Saver {
        @JavascriptInterface
        public void saveBase64(String name, String b64) {
            try {
                String n = (name == null || name.length() == 0)
                        ? ("download_" + System.currentTimeMillis()) : name;
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                final String path = saveToDownloads(n, data);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        toast(path.length() == 0 ? "保存失败" : ("已保存到 " + path));
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "saveBase64 fail", e);
            }
        }

        @JavascriptInterface
        public void toastMsg(String m) {
            toast(m);
        }
    }

    private String saveToDownloads(String name, byte[] data) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return "";
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(data);
                os.flush();
                os.close();
                return "Download/" + name;
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(data);
                fo.flush();
                fo.close();
                return f.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.w(TAG, "saveToDownloads fail", e);
            return "";
        }
    }

    private String readAsset(String name) {
        try {
            InputStream is = getAssets().open(name);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private void toast(String m) {
        try { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); } catch (Exception e) { }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            if (fileCb == null) { super.onActivityResult(req, res, data); return; }
            Uri[] result = null;
            try {
                if (res == RESULT_OK && data != null) {
                    if (data.getClipData() != null) {
                        int c = data.getClipData().getItemCount();
                        result = new Uri[c];
                        for (int i = 0; i < c; i++) result[i] = data.getClipData().getItemAt(i).getUri();
                    } else if (data.getData() != null) {
                        result = new Uri[] { data.getData() };
                    }
                }
            } catch (Exception e) { }
            fileCb.onReceiveValue(result);
            fileCb = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        final String js = "(function(){try{"
                + "var d=document.getElementById('chatDrawer');if(d&&d.classList.contains('active')){if(window.closeChatDrawer){closeChatDrawer();}else{d.classList.remove('active');}return 1;}"
                + "var ov=document.getElementById('chatDrawerOverlay');if(ov&&ov.classList.contains('active')){ov.classList.remove('active');}"
                + "var s=document.getElementById('sidebar');if(s&&s.classList.contains('active')){if(window.closeSidebar){closeSidebar();}else{s.classList.remove('active');}return 1;}"
                + "var p=document.querySelector('.sp-panel.active');if(p){if(window.closeChatCfg){closeChatCfg();}else{p.classList.remove('active');}return 1;}"
                + "var a=document.querySelector('.app-page.active');if(a){a.classList.remove('active');document.body.classList.remove('app-open');return 1;}"
                + "return 0;}catch(e){return 0;}})()";
        web.evaluateJavascript(js, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                boolean handled = value != null && value.contains("1");
                if (handled) return;
                long now = System.currentTimeMillis();
                if (now - lastBack < 1600) {
                    finish();
                } else {
                    lastBack = now;
                    toast("再按一���返回键退出");
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        try { web.saveState(out); } catch (Exception e) { }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { web.onPause(); } catch (Exception e) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { web.onResume(); } catch (Exception e) { }
    }

    @Override
    protected void onDestroy() {
        try {
            if (web != null) {
                web.loadUrl("about:blank");
                web.destroy();
            }
        } catch (Exception e) { }
        super.onDestroy();
    }
}
