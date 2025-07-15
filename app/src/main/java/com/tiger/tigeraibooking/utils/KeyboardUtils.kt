import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.app.Activity
import android.util.Log
import android.view.animation.DecelerateInterpolator


object KeyboardUtils {
    fun setupKeyboardTranslation(
        activity: Activity,
        targetWebView: WebView,
        minKeyboardHeightRatio: Double = 0.15,
        shouldTranslate: () -> Boolean = { true }
    ) {
        val rootView = activity.window.decorView.findViewById<View>(android.R.id.content)

        rootView.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {

            var maxVisibleHeight = 0

            override fun onGlobalLayout() {

                if (!shouldTranslate()) return
                val rect = Rect()
                rootView.getWindowVisibleDisplayFrame(rect)

                val visibleHeight = rect.height()
                if (visibleHeight > maxVisibleHeight) {
                    maxVisibleHeight = visibleHeight
                }

                val keypadHeight = maxVisibleHeight - visibleHeight
                val keyboardVisible = keypadHeight > maxVisibleHeight * minKeyboardHeightRatio

                val targetTranslationY = if (keyboardVisible) {
                    -keypadHeight.toFloat()
                } else {
                    0f
                }

//                Log.d(
//                    "KeyboardUtils",
//                    "maxVisibleHeight=$maxVisibleHeight, visibleHeight=$visibleHeight, keypadHeight=$keypadHeight"
//                )
                targetWebView.animate()
                    .translationY(targetTranslationY)
                    .setDuration(150)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        })
    }
}

