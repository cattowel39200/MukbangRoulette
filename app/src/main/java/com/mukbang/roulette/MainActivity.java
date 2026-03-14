package com.mukbang.roulette;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Window;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

public class MainActivity extends Activity {

    private static final int PERM_LOCATION = 1;
    // Callback function name must be a safe JS identifier
    private static final Pattern SAFE_CB = Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*$");

    // Error codes for native bridge
    private static final int ERR_UNKNOWN = -1;
    private static final int ERR_TIMEOUT = -2;
    private static final int ERR_DNS = -3;
    private static final int ERR_SSL = -4;
    private static final int ERR_IO = -5;

    private WebView webView;
    private Handler mainHandler;
    private volatile boolean appLoaded = false;
    private volatile boolean backHandling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(Color.parseColor("#0a0a1a"));
            window.setNavigationBarColor(Color.parseColor("#0a0a1a"));
        }

        webView = new WebView(this);
        setContentView(webView);
        setupWebView();
        requestLocationPermission();
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    PERM_LOCATION
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERM_LOCATION && appLoaded) {
            webView.loadUrl("javascript:try{init()}catch(e){}");
        }
    }

    /** Validate callback function name to prevent JS injection */
    private boolean isSafeCallback(String fn) {
        return fn != null && SAFE_CB.matcher(fn).matches();
    }

    /** Safely invoke JS callback via main thread */
    private void invokeCallback(final String fn, final int code, final String b64) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    webView.loadUrl("javascript:try{" + fn + "(" + code + ",'" + b64 + "')}catch(e){}");
                }
            }
        });
    }

    /** Common HTTP fetch logic with differentiated error codes */
    private void httpFetch(final String apiUrl, final String[] headers, final int connectTimeout,
                           final int readTimeout, final boolean followRedirects, final String callbackFn) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int fCode = 0;
                String fResult = "";
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(connectTimeout);
                    conn.setReadTimeout(readTimeout);
                    conn.setInstanceFollowRedirects(followRedirects);
                    // Apply headers in pairs: [key1, value1, key2, value2, ...]
                    if (headers != null) {
                        for (int i = 0; i + 1 < headers.length; i += 2) {
                            conn.setRequestProperty(headers[i], headers[i + 1]);
                        }
                    }

                    fCode = conn.getResponseCode();
                    InputStream is = (fCode >= 200 && fCode < 300)
                            ? conn.getInputStream()
                            : conn.getErrorStream();
                    if (is == null) { fCode = ERR_IO; }
                    else {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();
                        conn.disconnect();
                        fResult = Base64.encodeToString(sb.toString().getBytes("UTF-8"), Base64.NO_WRAP);
                    }
                } catch (SocketTimeoutException e) {
                    fCode = ERR_TIMEOUT;
                } catch (UnknownHostException e) {
                    fCode = ERR_DNS;
                } catch (SSLException e) {
                    fCode = ERR_SSL;
                } catch (java.io.IOException e) {
                    fCode = ERR_IO;
                } catch (Exception e) {
                    fCode = ERR_UNKNOWN;
                }
                invokeCallback(callbackFn, fCode, fResult);
            }
        }).start();
    }

    /** Kakao API category search (shared by food/cafe) */
    private void categorySearch(String categoryCode, double lat, double lon,
                                int rad, int page, String callbackFn) {
        if (!isSafeCallback(callbackFn)) return;
        String apiUrl = "https://dapi.kakao.com/v2/local/search/category.json"
                + "?category_group_code=" + categoryCode
                + "&x=" + lon + "&y=" + lat
                + "&radius=" + rad
                + "&size=15&page=" + page
                + "&sort=accuracy";
        String[] headers = {
            "Authorization", "KakaoAK " + BuildConfig.KAKAO_API_KEY,
            "User-Agent", "MukbangRoulette/" + BuildConfig.VERSION_NAME
        };
        httpFetch(apiUrl, headers, 10000, 10000, false, callbackFn);
    }

    private void setupWebView() {
        mainHandler = new Handler(Looper.getMainLooper());

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccess(false);
        // All external resources use HTTPS; enforce strict mode
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        // Security: disable dangerous file access
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        settings.setGeolocationDatabasePath(getFilesDir().getPath());

        webView.setBackgroundColor(Color.parseColor("#0a0a1a"));
        webView.addJavascriptInterface(new JsBridge(), "Android");

        // Use WebViewAssetLoader for secure asset serving
        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("https://appassets.androidplatform.net/")) {
                    return false;
                }
                handleUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // Mark app as loaded only after page fully renders
                appLoaded = true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        // Load via asset loader URL (not file://)
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    private void handleUrl(String url) {
        if (url != null && (url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("nmap://") || url.startsWith("tel:"))) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    class JsBridge {
        @JavascriptInterface
        public void openExternal(String url) {
            handleUrl(url);
        }

        @JavascriptInterface
        public void kakaoSearch(double lat, double lon, int rad, int page, String callbackFn) {
            categorySearch("FD6", lat, lon, rad, page, callbackFn);
        }

        @JavascriptInterface
        public void cafeSearch(double lat, double lon, int rad, int page, String callbackFn) {
            categorySearch("CE7", lat, lon, rad, page, callbackFn);
        }

        @JavascriptInterface
        public void blogSearch(final String query, final String callbackFn) {
            if (query == null || !isSafeCallback(callbackFn)) return;
            try {
                String apiUrl = "https://dapi.kakao.com/v2/search/blog"
                        + "?query=" + URLEncoder.encode(query, "UTF-8")
                        + "&size=5";
                String[] headers = {
                    "Authorization", "KakaoAK " + BuildConfig.KAKAO_API_KEY,
                    "User-Agent", "MukbangRoulette/" + BuildConfig.VERSION_NAME
                };
                httpFetch(apiUrl, headers, 8000, 8000, false, callbackFn);
            } catch (Exception e) {
                invokeCallback(callbackFn, ERR_UNKNOWN, "");
            }
        }

        @JavascriptInterface
        public void fetchUrl(final String fetchUrl, final String callbackFn) {
            if (fetchUrl == null || !isSafeCallback(callbackFn)) return;
            // Restrict to known safe domains
            if (!fetchUrl.startsWith("https://place.map.kakao.com/")
                    && !fetchUrl.startsWith("https://place-api.map.kakao.com/")) return;

            String[] headers;
            if (fetchUrl.startsWith("https://place-api.map.kakao.com/")) {
                headers = new String[]{
                    "User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36",
                    "Accept", "application/json, text/plain, */*",
                    "Origin", "https://place.map.kakao.com",
                    "Referer", "https://place.map.kakao.com/",
                    "appVersion", "6.6.0",
                    "pf", "PC"
                };
            } else {
                headers = new String[]{
                    "User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36",
                    "Accept", "application/json, text/html, */*",
                    "Accept-Language", "ko-KR,ko;q=0.9",
                    "Referer", "https://place.map.kakao.com/"
                };
            }
            httpFetch(fetchUrl, headers, 8000, 8000, true, callbackFn);
        }

        @JavascriptInterface
        public boolean isOnline() {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                if (cm == null) return false;
                if (Build.VERSION.SDK_INT >= 23) {
                    android.net.Network net = cm.getActiveNetwork();
                    if (net == null) return false;
                    NetworkCapabilities cap = cm.getNetworkCapabilities(net);
                    return cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                } else {
                    NetworkInfo info = cm.getActiveNetworkInfo();
                    return info != null && info.isConnected();
                }
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void clearCache() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (webView != null) {
                        webView.clearCache(true);
                    }
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView == null || backHandling) {
            super.onBackPressed();
            return;
        }
        backHandling = true;
        webView.evaluateJavascript(
            "document.getElementById('modal').innerHTML.length > 0",
            new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    backHandling = false;
                    if ("true".equals(value)) {
                        webView.loadUrl("javascript:try{closeSettings()}catch(e){}");
                    } else if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                }
            }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
