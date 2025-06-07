package com.tiger.withflutterweb

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.webkit.JsResult

import com.tiger.withflutterweb.FlutterBridge

class MainActivity : AppCompatActivity() {
    private lateinit var flutterWeb: WebView
    private lateinit var hostWeb: WebView

    private fun loadJSFromAssets(context: Context, fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        fun injectMessageListener(webView: WebView?) {
            val js = """
        window.addEventListener("message", function(event) {
            if (event && event.data) {
                AndroidBridge.onFlutterMessage(JSON.stringify(event.data));
            }
        });
    """
            webView?.evaluateJavascript(js, null)
        }

        hostWeb = findViewById(R.id.hostWebView)
        flutterWeb = findViewById(R.id.flutterWebView)
        val flutterBridge = FlutterBridge(hostWeb, flutterWeb, this)

        // hostWeb 설정
        hostWeb.settings.javaScriptEnabled = true
        hostWeb.settings.domStorageEnabled = true
        hostWeb.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(view?.context)
                    .setTitle("Alert from JS")
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .create()
                    .show()
                return true
            }
        }

        hostWeb.loadUrl("https://www.tigerbooking.golf")
        hostWeb.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                val uri = Uri.parse(url ?: "")
                Log.d("customLog", "onPageStarted: $url")
                // 메인페이지 접속시 실행할 js
                when {
                    uri.host == "www.tigerbooking.golf" && uri.path == "/" -> {
                        val js = loadJSFromAssets(view!!.context, "js_inject.js")
                        view.evaluateJavascript(js, null)
                    }

                }
            }
            // [navigateHost] 골프장 상세페이지로 으로 이동하는 경우 실행할 js.

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                val uri = Uri.parse(url ?: "")
                if (uri.host == "www.tigerbooking.golf" && uri.path?.contains("Field") == true) {
                    Log.d("customLog", "[onPageCommitVisible] about to collapse: $url")

                    flutterWeb.evaluateJavascript(
                        """window.postMessage({ type: "navigate-Finished" }, "*");""",
                        null
                    )
                    flutterBridge.collapseFlutterWeb()
                }
            }


        }
        Log.d("MainActivity", "✅ hostWeb.webViewClient 설정 완료")

        // flutterWeb 설정
        val webSettings: WebSettings = flutterWeb.settings
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.loadsImagesAutomatically = true
        webSettings.domStorageEnabled = true
        flutterWeb.setBackgroundColor(Color.TRANSPARENT)
        flutterWeb.addJavascriptInterface(FlutterBridge(hostWeb, flutterWeb, this), "AndroidBridge")
        flutterWeb.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectMessageListener(view)
            }
        }

        flutterWeb.webChromeClient = WebChromeClient()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
     flutterWeb.loadUrl("https://tiger.platypusoft.com/flutter")
    }
}