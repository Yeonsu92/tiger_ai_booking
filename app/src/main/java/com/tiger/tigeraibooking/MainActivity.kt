package com.tiger.tigeraibooking

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var flutterWeb: WebView
    private lateinit var hostWeb: WebView
    private val siteLang:String = "EN" // 기본값
    private var isBackHandlerEnabled = true // 무한 루프 방지용 플래그
    private val handler = Handler(Looper.getMainLooper())

    private fun clearWebViewCache(context: Context) {
        try {
            val appCacheDir = File(context.cacheDir.absolutePath)
            if (appCacheDir.exists()) {
                appCacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e("WebViewCache", "Failed to clear cache: ${e.message}")
        }
    }

    private val useTestServer = false;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        hostWeb = findViewById(R.id.hostWebView)
        flutterWeb = findViewById(R.id.flutterWebView)

        val flutterBridge = FlutterBridge(hostWeb, flutterWeb, this)

        clearWebViewCache(this)
        flutterWeb.clearCache(true)



// 뒤로가기 콜백 등록
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {

            override fun handleOnBackPressed() {
                Log.d("===> ", "handleOnBackPressed :start =======")

                val layoutParams = flutterWeb.layoutParams as ViewGroup.LayoutParams
                val isFlutterExpanded = layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT &&
                        layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT

                if (isFlutterExpanded) {
                    Log.d("===> ", "handleOnBackPressed : flutter에서 뒤로가기 처리")
                    // 1. Flutter에 직접 메시지 → handleBack 요청
                    val js = """window.postMessage({ action: "handleBack" }, "*");""".trimIndent()
                    flutterWeb.evaluateJavascript(js, null)

                } else if (hostWeb.canGoBack()) {
                    Log.d("===> ", "handleOnBackPressed : hostWeb뒤로 갈 수 있음")
                    hostWeb.goBack()
                } else {
                    Log.d("===> ", "handleOnBackPressed : hostWeb뒤로갈 수 없음, 앱종료")
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()

                    // 0.5초 후 다시 콜백 활성화
                    Handler(Looper.getMainLooper()).postDelayed({
                        isEnabled = true
                    }, 500)
                }
            }
        })




        // hostWeb 설정
        hostWeb.settings.javaScriptEnabled = true
        hostWeb.settings.domStorageEnabled = true
        hostWeb.addJavascriptInterface(flutterBridge, "AndroidBridge")
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
            if (useTestServer) {
                hostWeb.loadUrl("https://dev-renew.tigerbooking.golf/")
            } else {
                hostWeb.loadUrl("https://www.tigerbooking.golf")
            }

        hostWeb.webViewClient = object : WebViewClient() {
            // [navigateHost] 네이게이션이 끝나면 flutter에게 알려, 플러터 웹뷰를 닫는다.
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                if (flutterBridge.shouldCollapseIframeBySideEffect) {
                    val msg = JSONObject().apply {
                        put("action", "flutter:navigateFinished")
                    }
                    flutterBridge.sendRequestWithCoroutine(msg, listOf("flutter", "navigateFinished"))
                }
            }



            //hostWeb의 onPageFinshed 될 때마다 서버에 요청해서 특정 js파일을 실행시키는 로직.
            //목적 : 안드로이드 파일 수정 없이도 hostWeb의 onPageFinished에 다양한 이벤트를 등록하기 위함

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url);
                flutterBridge.readLanguageFromLocalStorage();

                // flutterWeb이 보이는건 ostWeb이 로드된 이후로만
                if (flutterWeb.visibility != View.VISIBLE) {
                    flutterWeb.visibility = View.VISIBLE
                }

                //flutter 메시지에 runScriptOnHostPageFinish이 추가된 버전이 배포되면 해제해도됨
               if (flutterBridge.shouldRunScriptOnNextFinish) {
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
              }
            }
        }


        // flutterWeb 설정
        val webSettings: WebSettings = flutterWeb.settings
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.loadsImagesAutomatically = true
        webSettings.domStorageEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_NO_CACHE

        flutterWeb.setBackgroundColor(Color.TRANSPARENT)
        flutterWeb.addJavascriptInterface(flutterBridge, "AndroidBridge")
        flutterWeb.webViewClient = WebViewClient()
        flutterWeb.webChromeClient = WebChromeClient()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (useTestServer) {

           flutterWeb.loadUrl("https://tigertest.platypusoft.com/flutter?_ts=${System.currentTimeMillis()}")
        }
        else{ flutterWeb.loadUrl("https://tiger.platypusoft.com/flutter?_ts=${System.currentTimeMillis()}") }
    }
    // dispatchKeyEvent 수정 - 무한 루프 방지
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d("===>", "dispatchKeyEvent: BACK key detected")

            // 뒤로가기 처리가 이미 진행 중이면 중복 처리 방지
            if (isBackHandlerEnabled) {
                isBackHandlerEnabled = false
                onBackPressedDispatcher.onBackPressed()
                // 짧은 딜레이 후 다시 활성화
                handler.postDelayed({ isBackHandlerEnabled = true }, 100)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onDestroy() {

        super.onDestroy()
        flutterWeb.loadUrl("about:blank")
        flutterWeb.clearHistory()
        flutterWeb.removeAllViews()
        flutterWeb.destroy()

        hostWeb.loadUrl("about:blank")
        hostWeb.clearHistory()
        hostWeb.removeAllViews()
        hostWeb.destroy()
    }

}

