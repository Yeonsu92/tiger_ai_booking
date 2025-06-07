package com.tiger.withflutterweb

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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.tiger.withflutterweb.utils.dpToPx



class FlutterBridge(
    private val hostWeb: WebView,
    private val flutterWeb: WebView,
    private val activity: Activity
) {

    fun collapseFlutterWeb() {
        Log.d("customLog", "[collapseFlutterWeb] 호출됨")
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

        val js = """window.postMessage({ type: "iframe-collapsed" }, "*");""".trimIndent()
        Handler(Looper.getMainLooper()).postDelayed({
            flutterWeb.evaluateJavascript(js, null)
        }, 100)
    }

    fun sendRequestWithCoroutine(msg: JSONObject, parts: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dJson = msg.optJSONObject("data")?.toString() ?: "{}"
                val gson = Gson()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val data: Map<String, Any> = gson.fromJson(dJson, type)

                val requestBodyMap = mapOf(
                    "action" to parts[1]
                    // "data" to data ← 필요하다면 포함 가능
                )

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



    @JavascriptInterface
    fun onFlutterMessage(json: String) {
        val msg = JSONObject(json)
        val action = when {
            msg.has("action") -> msg.getString("action")
            msg.has("type") -> msg.getString("type")
            else -> {
                Log.e("customLog", "Missing 'action' and 'type' in message: $json")
                return
            }
        }
//flutter에서 수신한 메시지 처리
        activity.runOnUiThread {
            Log.d("====>", action)
            when (action) {
                // flutter -> android WebView 확장 요청 처리
                // FlutterWeb의 FAB를 클릭했을시 발동
                "expandIframe" -> {
                    // hostWeb 터치 차단 (optional)
                    hostWeb.setOnTouchListener { _, _ -> true } // true = consume touch


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
                        val js = """
    window.postMessage({ type: "iframe-expanded" }, "*");
""".trimIndent()
                        flutterWeb.evaluateJavascript(js, null)
                        // 키보드 올라올 때 WebView 높이 자동 조절
                        KeyboardUtils.setupKeyboardTranslation(activity, flutterWeb)
                    }, 200) // Flutter가 화면 확장에 대비를 할 시간을 줌



                }
                // flutter -> android WebView 축소 요청 처리
                "collapseIframe" -> {
                        collapseFlutterWeb()
                }

                //   flutter -> android WebView 리다이렉트 요청 처리
                // 타임라인 페이지에서 "일정카드 - 골프"를 터치했을때 발동
                "navigateHost" -> {
                    Log.d("customLog", "onFlutterMessage called with: $json")
                    val data = msg.optJSONObject("data")
                    val url = data?.optString("url")
                    if (!url.isNullOrEmpty()) {
                        Log.i("customLog", "Navigating to URL: $url")
                        hostWeb.loadUrl(url)
                    }
                }
                //   flutter -> android WebView 새창열기 요청 처리
                // 타임라인 페이지에서 골프 외 일정카드를 터치했을때 발동
                "openWindow" -> {
                    Log.d("customLog", "onFlutterMessage called with: $json")
                    val data = msg.optJSONObject("data")
                    val url = data?.optString("url")
                    if (!url.isNullOrEmpty()) {
                        Log.i("customLog", "Opening external browser to: $url")

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
