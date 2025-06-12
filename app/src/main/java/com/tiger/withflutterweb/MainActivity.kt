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
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JsResult
import androidx.activity.OnBackPressedCallback
import org.json.JSONObject

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

        hostWeb = findViewById(R.id.hostWebView)
        flutterWeb = findViewById(R.id.flutterWebView)
        val flutterBridge = FlutterBridge(hostWeb, flutterWeb, this)

        // 뒤로가기 콜백 등록
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val layoutParams = flutterWeb.layoutParams as ViewGroup.LayoutParams
                val isFlutterExpanded = layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT &&
                        layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT

                when {
                    isFlutterExpanded && flutterWeb.canGoBack() -> flutterWeb.goBack()
                    hostWeb.canGoBack() -> hostWeb.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })


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
            // [navigateHost] 골프장 상세페이지로 으로 이동하는 경우 실행할 js.
            // TODO: onPageFinished로 합치기
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                val uri = Uri.parse(url ?: "")
                if (uri.host == "www.tigerbooking.golf" && (uri.path?.contains("Field") == true)) {

                    val msg = JSONObject().apply {
                        put("action", "flutter:navigate-Finished")
                    }
                    flutterBridge.sendRequestWithCoroutine(msg, listOf("flutter", "navigate-Finished"))
                    flutterBridge.collapseFlutterWeb()
                }
            }


            //hostWeb의 onPageFinshed 될 때마다 서버에 요청해서 특정 js파일을 실행시키는 로직.
            //목적 : 안드로이드 파일 수정 없이도 hostWeb의 onPageFinished에 다양한 이벤트를 등록하기 위함

            override fun onPageFinished(view: WebView?, url: String?) {
                //flutter 메시지에 shouldRunScriptOnNextFinish을 추가한 후 주석 해제
            //    if (flutterBridge.shouldRunScriptOnNextFinish) {
                    val msgToHost = JSONObject().apply {
                        put("action", "host:onPageFinished")
                        put("data", JSONObject().apply {
                            put("data", url)
                        })
                    }
                    val msgToFlutter = JSONObject().apply {
                        put("action", "flutter:onHostPageFinished")
                        put("data", JSONObject().apply {
                            put("data", url)
                        })
                    }
                    flutterBridge.sendRequestWithCoroutine(
                        msgToHost,
                        listOf("host", "onPageFinished")
                    )
                    flutterBridge.sendRequestWithCoroutine(
                        msgToFlutter,
                        listOf("flutter", "onHostPageFinished")
                    )
            //    }
            }
        }

        // flutterWeb 설정
        val webSettings: WebSettings = flutterWeb.settings
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.loadsImagesAutomatically = true
        webSettings.domStorageEnabled = true
        flutterWeb.setBackgroundColor(Color.TRANSPARENT)
        flutterWeb.addJavascriptInterface(FlutterBridge(hostWeb, flutterWeb, this), "AndroidBridge")
        flutterWeb.webViewClient = WebViewClient()
        flutterWeb.webChromeClient = WebChromeClient()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        flutterWeb.loadUrl("https://tiger.platypusoft.com/flutter")
    }
}