package com.nhanh.phim;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

public class MainActivity extends Activity {
  private WebView web;
  private String insetJs = "";

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    installCrashLog();
    web = new WebView(this);
    web.setBackgroundColor(0xFF1E2A55);
    web.setOverScrollMode(View.OVER_SCROLL_NEVER);
    setContentView(web);
    try { edgeToEdge(); } catch (Throwable t) { logError(t); }

    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(true);
    s.setTextZoom(100);

    final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
        .build();
    web.setWebViewClient(new WebViewClientCompat() {
      @Override
      public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return loader.shouldInterceptRequest(request.getUrl());
      }
      @Override
      public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
        logError(new RuntimeException("WebView renderer gone, crashed=" + detail.didCrash()));
        recreate();
        return true;
      }
      @Override
      public void onPageFinished(WebView view, String url) {
        if (!insetJs.isEmpty()) view.evaluateJavascript(insetJs, null);
      }
    });
    web.setOnApplyWindowInsetsListener((v, ins) -> {
      try {
      int t, b, l, r;
      if (Build.VERSION.SDK_INT >= 30) {
        Insets bars = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Insets ime = ins.getInsets(WindowInsets.Type.ime());
        t = bars.top; l = bars.left; r = bars.right; b = Math.max(bars.bottom, ime.bottom);
      } else {
        t = ins.getSystemWindowInsetTop(); b = ins.getSystemWindowInsetBottom();
        l = ins.getSystemWindowInsetLeft(); r = ins.getSystemWindowInsetRight();
      }
      float d = getResources().getDisplayMetrics().density;
      insetJs = "(function(s){s.setProperty('--sat','" + Math.round(t / d) + "px');s.setProperty('--sab','" + Math.round(b / d)
          + "px');s.setProperty('--sal','" + Math.round(l / d) + "px');s.setProperty('--sar','" + Math.round(r / d) + "px');})(document.documentElement.style)";
      web.evaluateJavascript(insetJs, null);
      } catch (Throwable e) { logError(e); }
      return ins;
    });
    web.addJavascriptInterface(new Bridge(this), "Native");
    web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
  }

  /** Ghi lại lỗi vào file để kiểm tra qua USB: Android/data/com.nhanh.phim/files/loi.txt */
  private void installCrashLog() {
    final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((th, ex) -> { logError(ex); if (prev != null) prev.uncaughtException(th, ex); });
  }

  void logError(Throwable t) {
    try {
      java.io.File f = new java.io.File(getExternalFilesDir(null), "loi.txt");
      java.io.StringWriter sw = new java.io.StringWriter();
      t.printStackTrace(new java.io.PrintWriter(sw));
      java.io.FileWriter w = new java.io.FileWriter(f, true);
      w.write(new java.util.Date() + "\n" + sw + "\n");
      w.close();
    } catch (Throwable ignored) {}
  }

  /** Tràn viền: nền app nằm dưới thanh trạng thái và thanh điều hướng, biểu tượng hệ thống màu trắng. */
  private void edgeToEdge() {
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    getWindow().setNavigationBarColor(Color.TRANSPARENT);
    if (Build.VERSION.SDK_INT >= 28) {
      WindowManager.LayoutParams lp = getWindow().getAttributes();
      lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      getWindow().setAttributes(lp);
    }
    if (Build.VERSION.SDK_INT >= 29) {
      getWindow().setNavigationBarContrastEnforced(false);
      getWindow().setStatusBarContrastEnforced(false);
    }
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      WindowInsetsController c = getWindow().getInsetsController();
      if (c != null) c.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    } else {
      getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
  }

  @Override
  public void onBackPressed() {
    web.evaluateJavascript("(window.onNativeBack && window.onNativeBack()) ? '1' : '0'", v -> {
      if (!"\"1\"".equals(v)) moveTaskToBack(true);
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (web != null) web.evaluateJavascript("window.onNativeResume && window.onNativeResume()", null);
  }
}
