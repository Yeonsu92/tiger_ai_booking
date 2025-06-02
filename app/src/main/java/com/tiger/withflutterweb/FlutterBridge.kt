package com.tiger.withflutterweb

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import androidx.constraintlayout.widget.ConstraintLayout

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
            when (action) {
                // flutter -> android WebView 확장 요청 처리
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
                "navigateHost" -> {
                    Log.d("customLog", "onFlutterMessage called with: $json")
                    val data = msg.optJSONObject("data")
                    val url = data?.optString("url")
                    if (!url.isNullOrEmpty()) {
                        Log.i("customLog", "Navigating to URL: $url")
                        hostWeb.loadUrl(url)
                    }
                }


                // 기타 이벤트 처리
                // ex. flutter의 요청으로 hostWeb을 스크롤하는 예시
                "scrollToBottom" -> {
                    val js = "window.scrollTo(0, document.body.scrollHeight);"
                    hostWeb.evaluateJavascript(js, null)
                }
            }
        }
    }
}
