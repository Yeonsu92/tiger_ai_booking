import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.*

object KeyboardUtils {

    fun setupImeRemainder(
        activity: Activity,
        container: WebView,
        expanded: Boolean,
        settleDurationMs: Long = 100L
    ) {
        // flutterWeb이 축소되어있을때는 비활성화
        if (!expanded) {
            detach(container)
            return
        }

        val root = activity.findViewById<View>(android.R.id.content)

        var baselineRootHeight = 0
        var animRunning = false
        var lastAppliedBottom = -1

        fun stableNavBottom(): Int =
            ViewCompat.getRootWindowInsets(root)
                ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
                ?.bottom ?: 0

        fun imeBottom(insets: WindowInsetsCompat) =
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

        fun remainderSpace(insets: WindowInsetsCompat): Int {
            val ime = imeBottom(insets)
            if (ime == 0) return 0
            val currentRootHeight = root.height
            if (baselineRootHeight < currentRootHeight) {
                baselineRootHeight = currentRootHeight
            }
            val resizedBySystem = (baselineRootHeight - currentRootHeight).coerceAtLeast(0)
            val nav = stableNavBottom()
            return (ime - resizedBySystem - nav).coerceAtLeast(0)
        }

        fun setBottom(px: Int) {
            if (px == lastAppliedBottom) return
            val lp = container.layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                if (lp.bottomMargin != px) {
                    lp.bottomMargin = px
                    container.layoutParams = lp
                }
            } else {
                if (container.paddingBottom != px) {
                    container.setPadding(
                        container.paddingLeft, container.paddingTop, container.paddingRight, px
                    )
                }
            }
            lastAppliedBottom = px
            Log.d("===>", "bottom set to $lastAppliedBottom")
        }

        // 높이조절
        ViewCompat.setWindowInsetsAnimationCallback(
            container,
            object : WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
            ) {
                var frame = 0
                var t0 = 0L

                override fun onStart(
                    animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.BoundsCompat
                ): WindowInsetsAnimationCompat.BoundsCompat {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        animRunning = true
                        frame = 0
                        t0 = android.os.SystemClock.uptimeMillis()
                        baselineRootHeight = maxOf(baselineRootHeight, root.height)
                        return bounds
                    }
                    return bounds
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    running: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
//                    if (animRunning) {
//                        Log.d("===>", "onProgress")
//                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        animRunning = false
                        val i = ViewCompat.getRootWindowInsets(root)
                        val rem = i?.let { remainderSpace(it) } ?: 0
                        Log.d("===>", "onEnd")
                        setBottom(rem) // 최종 정착
                    }
                }
            }
        )

        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            if (!animRunning) {
                Log.d("===>", "onEnd")
                setBottom(remainderSpace(insets)) // 최종값만 반영
                container.translationY = 0f
            }
            insets // consume 하지 않음
        }
    }


    private fun detach(container: View) {
            ViewCompat.setWindowInsetsAnimationCallback(container, null)
            ViewCompat.setOnApplyWindowInsetsListener(container, null)

    }
}

