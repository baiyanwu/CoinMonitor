package com.tradingview.lightweightcharts.api.series.models

/**
 * The TouchMouseEventData interface represents events that occur due to the user interacting with a
 * pointing device (such as a mouse).
 */
data class TouchMouseEventData(
    /**
     * The X coordinate of the mouse pointer in local (DOM content) coordinates.
     */
    var clientX: Float?,
    /**
     * The Y coordinate of the mouse pointer in local (DOM content) coordinates.
     */
    var clientY: Float?,
    /**
     * The X coordinate of the mouse pointer relative to the whole document.
     */
    var pageX: Float?,
    /**
     * The Y coordinate of the mouse pointer relative to the whole document.
     */
    var pageY: Float?,
    /**
     * The X coordinate of the mouse pointer in global (screen) coordinates.
     * The X coordinate of the mouse pointer in global (screen) coordinates.
     */
    var screenX: Float?,
    /**
     * The Y coordinate of the mouse pointer in global (screen) coordinates.
     */
    var screenY: Float?,
    /**
     * The X coordinate of the mouse pointer relative to the chart / price axis / time axis canvas element.
     */
    var localX: Float?,
    /**
     * The Y coordinate of the mouse pointer relative to the chart / price axis / time axis canvas element.
     */
    var localY: Float?,

    /**
     * Returns a boolean value that is true if the Ctrl key was active when the key event was generated.
     */
    var ctrlKey: Boolean?,
    /**
     * Returns a boolean value that is true if the Alt (Option or ⌥ on macOS) key was active when the
     * key event was generated.
     */
    var altKey: Boolean?,
    /**
     * Returns a boolean value that is true if the Shift key was active when the key event was generated.
     */
    var shiftKey: Boolean?,
    /**
     * Returns a boolean value that is true if the Meta key (on Mac keyboards, the ⌘ Command key; on
     * Windows keyboards, the Windows key (⊞)) was active when the key event was generated.
     */
    var metaKey: Boolean?,
)
