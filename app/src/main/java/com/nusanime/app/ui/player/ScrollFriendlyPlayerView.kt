package com.nusanime.app.ui.player

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.media3.ui.PlayerView

/**
 * PlayerView yang tidak "mencuri" gestur scroll vertikal dari LazyColumn:
 * drag vertikal dilempar ke parent (list bisa scroll), tap tetap diproses player.
 */
class ScrollFriendlyPlayerView(context: Context) : PlayerView(context) {
    private var downY = 0f
    private var downX = 0f
    private var dragDeclared = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y; downX = event.x; dragDeclared = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragDeclared) {
                    val dy = event.y - downY
                    val dx = event.x - downX
                    if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                        dragDeclared = true
                        (parent as? android.view.ViewGroup)?.requestDisallowInterceptTouchEvent(false)
                        return false
                    }
                }
                if (dragDeclared) return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDrag = dragDeclared
                dragDeclared = false
                if (wasDrag) return false
            }
        }
        return super.onTouchEvent(event)
    }
}
