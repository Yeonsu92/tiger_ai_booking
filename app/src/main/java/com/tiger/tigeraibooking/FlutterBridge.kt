package com.tiger.tigeraibooking

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tiger.tigeraibooking.utils.HandleFirstLaunch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.tiger.tigeraibooking.utils.dpToPx

// 엄밀히 말하면 flutter화면은 Webview 컴포넌트이지 iframe이 아니지만 이해의 편의를 위해 iframe이라고 부르고 있다.

class FlutterBridge(
    private val hostWeb: WebView,
    private val flutterWeb: WebView,
    private val activity: Activity
) {

    private var runScriptOnNextFinish = false;
    private var collapseIframeBySideEffect = false
    private var currentLang:String = "EN"

    val shouldRunScriptOnNextFinish :Boolean    get() = runScriptOnNextFinish
    val shouldCollapseIframeBySideEffect:Boolean get() =  collapseIframeBySideEffect
    val currentHostWebLang:String get()= currentLang

    private fun setShouldRunScriptOnNextFinish(value: Boolean) {
        runScriptOnNextFinish = value
    }
    private fun setShouldCollapseIframeBySideEffect(value: Boolean) {
        collapseIframeBySideEffect = value
    }

    fun readLanguageFromLocalStorage() {
        val js = """
        (function() {
            var hasSiteLang = "siteLang" in localStorage;
            var lang = localStorage.getItem('siteLang') || '';
            console.log("siteLang exists:", hasSiteLang, "| value:", lang);
            AndroidBridge.updateLang(hasSiteLang, lang); 
        })();
    """.trimIndent()

        hostWeb.evaluateJavascript(js, null)
    }
    //사용자가 hostWeb의 언어를 변경하면, flutterWeb으로 전달한다.
    @JavascriptInterface
    fun updateLang(hasSiteLang:Boolean, lang: String) {
        Log.d("====>", "is siteLang exist in localStorage: $hasSiteLang")
        Log.d("====>", "updateLang called with: $lang")
        currentLang = lang

        //flutterWeb에 hostWeb의 localStorage sitelang 설정 전달
        Handler(Looper.getMainLooper()).post {
            val safeLang = JSONObject.quote(currentLang)
            val js = """window.postMessage({ action: "sendLang", lang: $safeLang}, "*");"""

            flutterWeb.evaluateJavascript(js, null)
        }
    }


    // 요청이 서버를 거칠 필요가 있는 경우
    fun sendRequestWithCoroutine(msg: JSONObject, parts: List<String>) {
      //  Log.d("====>", "[sendRequestWithCoroutine] start")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dJson = msg.optJSONObject("data")?.toString() ?: "{}"
                val gson = Gson()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val data: Map<String, Any> = gson.fromJson(dJson, type)

                val rawData = data["data"]
                Log.d("===>", "rawData: $rawData, dataType: ${rawData?.javaClass}")

                val requestBodyMap = if (rawData != null) {
                    mapOf("action" to parts[1], "data" to rawData)
                } else {
                    Log.d("===>", "data['data'] is null")
                    mapOf("action" to parts[1])
                }

           //     Log.d("====> requestBodyMap::", gson.toJson(requestBodyMap))
                val rJson = gson.toJson(requestBodyMap)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = rJson.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("https://tigerapi.platypusoft.com/api/v001/javascript/getScript")
                    .post(requestBody)
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    Log.d("====>","success")
                    val body = response.body?.string() ?: ""

                    val apiResponse = gson.fromJson(body, ApiResponse::class.java)

                    withContext(Dispatchers.Main) {
                        Log.d("==> result:", apiResponse.result.toString())
                        Log.d("==> message:", apiResponse.resultMessage)
                        Log.d("==> javascriptCode:", apiResponse.javascriptCode)

                        if (apiResponse.result == 1) {
                            if (parts[0] == "host") {
                                hostWeb.evaluateJavascript(apiResponse.javascriptCode, null)
                            } else {
                                flutterWeb.evaluateJavascript(apiResponse.javascriptCode, null)
                            }
                        }
                    }
                } else {
                    Log.e("==> Error:", "${response.code} ${response.message}")
                }

                response.close()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("==> Exception", e.toString())
                }
            }
        }
    }

    fun collapseFlutterWeb() {
        Log.d("====>", "[collapseFlutterWeb] start")
        val params = flutterWeb.layoutParams as ConstraintLayout.LayoutParams
        params.width = dpToPx(60, activity)
        params.height = dpToPx(60, activity)
        params.startToStart = ConstraintLayout.LayoutParams.UNSET
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomMargin = dpToPx(120, activity)
        flutterWeb.layoutParams = params
        flutterWeb.requestLayout()

        hostWeb.setOnTouchListener(null)
        val js = """window.postMessage({ action: "iframeCollapsed" }, "*");""".trimIndent()
        Handler(Looper.getMainLooper()).postDelayed({
            flutterWeb.evaluateJavascript(js, null)
        }, 100)
    }



    @JavascriptInterface
    fun onFlutterMessage(json: String) {
        val msg = JSONObject(json)
        Log.d("====>", "msg:$msg")

        val action = when {
            msg.has("action") -> msg.getString("action")
            else -> {
                Log.e("====>", "Missing 'action' in message: $json")
                return
            }
        }
// onPageFinished를 실행시켜야하는지 여부
        val shouldNotify = when {
            msg.has("runScriptOnHostPageFinish") -> msg.getBoolean("runScriptOnHostPageFinish")
           else -> false
        }
        setShouldRunScriptOnNextFinish(shouldNotify)
    //hostWeb의 페이지 이동 등의 이유로, 페이지로딩이 끝난 후 iframe을 닫아야할 때
        val shouldCollapseIFrame = when {
            msg.has("collapseIframeAfterThisAction")  -> msg.getBoolean("collapseIframeAfterThisAction")
            else -> false
        }
        setShouldCollapseIframeBySideEffect(shouldCollapseIFrame)



//flutter에서 수신한 메시지 처리
        activity.runOnUiThread {
            Log.d("====>", action)
            when (action) {
                // flutterWeb이 로딩되면, hostWeb의 언어설정을 전달한다.
                "flutterIsReady" -> {


                    val uuid = HandleFirstLaunch().getOrCreateUUID(activity)
                    Log.d("flutterIsReady ===>", "앱 UUID: $uuid")
                    val safeLang = JSONObject.quote(currentLang)
                    val safeUuid = JSONObject.quote(uuid)

                    val js = "window.postMessage({ action: \"sendInitialConfig\", lang: $safeLang, uuid: $safeUuid }, \"*\");"

                    flutterWeb.evaluateJavascript(js, null)
                }
                // flutter -> android WebView 확장 요청 처리
                // FlutterWeb의 FAB를 클릭했을시 발동
                "expandIframe" -> {
                    // hostWeb 터치 차단 (optional)
                    hostWeb.setOnTouchListener { _, _ -> true } // true = consume touch

                        // flutter가 확장에 대비할 시간을 준다.
                  Handler(Looper.getMainLooper()).postDelayed({
                        val params = flutterWeb.layoutParams as ConstraintLayout.LayoutParams
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT
                        params.height = ViewGroup.LayoutParams.MATCH_PARENT
                        //정렬 변경
                        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        params.topToTop = ConstraintLayout.LayoutParams.UNSET
                        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID

                        params.bottomMargin =0;
                        flutterWeb.layoutParams = params
                        flutterWeb.requestLayout()
                        // android->flutter 확장 완료 notify
                        val js = "window.postMessage({ action: \"iframeExpanded\" }, \"*\");"

                        flutterWeb.evaluateJavascript(js, null)
                        // 키보드 올라올 때 WebView 높이 자동 조절
                        KeyboardUtils.setupKeyboardTranslation(activity, flutterWeb)
                   }, 200)


                }
                // flutter -> android WebView 축소 요청 처리
                "collapseIframe" -> {

                    collapseFlutterWeb()
                }
                "navigateHost" -> {
                    val data = msg.optJSONObject("data")
                    val url = data?.optString("url")
                    if (!url.isNullOrEmpty()) {
                        hostWeb.loadUrl(url)
                    }
                }
                "host:navigateHost" -> {}
                //   flutter -> android WebView 새창열기 요청 처리
                // 타임라인 페이지에서 골프 외 일정카드를 터치했을때 발동
                "openWindow" -> {
                    Log.d("===>", "onFlutterMessage called with: $json")
                    val data = msg.optJSONObject("data")
                    val url = data?.optString("url")
                    if (!url.isNullOrEmpty()) {
                        Log.i("===>", "Opening external browser to: $url")

                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        activity.startActivity(intent)
                    }
                }
                else -> {
                    // 해당 메시지를 서버로 보내서, 원하는 스크립트를 받아 온다.
                    // action 은 host:hello, flutter:hello 형식으로 처리되어야 한다.
                    val parts = action.split(":")

                    if (parts.size == 2) {
                        sendRequestWithCoroutine(msg, parts)
                    }
                }

            }
        }
    }
}

