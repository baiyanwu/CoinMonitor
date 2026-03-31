package com.tradingview.lightweightcharts.view.gesture

import android.view.MotionEvent
import android.view.ViewGroup

interface TouchDelegate {
    /**
     * Implement this method to run custom logic before the onTouchEvent method hits
     */
    fun beforeTouchEvent(view: ViewGroup)

    /**
     * Implement this method to handle touch screen motion events
     *
     * @param event The motion event.
     * @return True if the event was handled, false otherwise.
     */
    fun onTouchEvent(view: ViewGroup, event: MotionEvent): Boolean
}
