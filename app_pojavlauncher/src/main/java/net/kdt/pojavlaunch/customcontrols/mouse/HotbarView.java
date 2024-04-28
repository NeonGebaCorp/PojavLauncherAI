package net.kdt.pojavlaunch.customcontrols.mouse;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.TapDetector;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.utils.MathUtils;
import org.lwjgl.glfw.CallbackBridge;

/**
 * A custom view for handling hotbar interactions.
 */
public class HotbarView extends View implements MCOptionUtils.MCOptionListener, View.OnLayoutChangeListener, Runnable {
    private final TapDetector mDoubleTapDetector = new TapDetector(2, TapDetector.DETECTION_METHOD_DOWN);
    private View mParentView;
    private static final int[] HOTBAR_KEYS = {
            LwjglGlfwKeycode.GLFW_KEY_1, LwjglGlfwKeycode.GLFW_KEY_2,   LwjglGlfwKeycode.GLFW_KEY_3,
            LwjglGlfwKeycode.GLFW_KEY_4, LwjglGlfwKeycode.GLFW_KEY_5,   LwjglGlfwKeycode.GLFW_KEY_6,
            LwjglGlfwKeycode.GLFW_KEY_7, LwjglGlfwKeycode.GLFW_KEY_8, LwjglGlfwKeycode.GLFW_KEY_9};
    private final DropGesture mDropGesture = new DropGesture(new Handler(Looper.getMainLooper()));
    private final float mScaleFactor = LauncherPreferences.PREF_SCALE_FACTOR/100f;
    private int mWidth;
    private int mLastIndex;
    private int mGuiScale;

    public HotbarView(Context context) {
        super(context);
        initialize();
    }

    public HotbarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public HotbarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    @SuppressWarnings("unused") // You suggested me this constructor, Android
    public HotbarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }

    /**
     * Initialize the HotbarView.
     */
    private void initialize() {
        MCOptionUtils.addMCOptionListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewParent parent = getParent();
        if (parent == null || !(parent instanceof View)) return;
        mParentView = (View) parent;
        mParentView.addOnLayoutChangeListener(this);
        mGuiScale = MCOptionUtils.getMcScale();
        repositionView();
    }

    /**
     * Reposition the HotbarView based on the current scale.
     */
    private void repositionView() {
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams))
            throw new RuntimeException("Incorrect LayoutParams type, expected ViewGroup.MarginLayoutParams");
        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
        int height;
        marginLayoutParams.width = mWidth = mcScale(180);
        marginLayoutParams.height = height = mcScale(20);
        marginLayoutParams.leftMargin = (CallbackBridge.physicalWidth / 2) - (mWidth / 2);
        marginLayoutParams.topMargin = CallbackBridge.physicalHeight - height;
        setLayoutParams(marginLayoutParams);
    }

    /**
     * Handle touch events for the HotbarView.
     *
     * @param event The MotionEvent to handle.
     * @return True if the event was handled, false otherwise.
     */
    @SuppressWarnings("ClickableViewAccessibility") // performClick does not report coordinates.
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!CallbackBridge.isGrabbing()) return false;
        boolean hasDoubleTapped = mDoubleTapDetector.onTouchEvent(event);

        // Check if we need to cancel the drop event
        int actionMasked = event.getActionMasked();
        if (isLastEventInGesture(actionMasked)) mDropGesture.cancel();
        else mDropGesture.submit();

        // Determine the hotbar slot
        float x = event.getX();
        if (x < 0 || x > mWidth) {
            // If out of bounds, cancel the hotbar gesture to avoid dropping items on last hotbar slots
            mDropGesture.cancel();
            return true;
        }
        int hotbarIndex = (int) MathUtils.map(x, 0, mWidth, 0, HOTBAR_KEYS.length);

        // Check if the slot changed and we need to make a key press
        if (hotbarIndex == mLastIndex) {
            // Only check for doubletapping if the slot has not changed
            if (hasDoubleTapped && !LauncherPreferences.PREF_DISABLE_SWAP_HAND) CallbackBridge.sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_F);
            return true;
        }
        mLastIndex = hotbarIndex;
        int hotbarKey = HOTBAR_KEYS[hotbarIndex];
        CallbackBridge.sendKeyPress(hotbarKey);

        // Cancel the event since we changed hotbar slots.
        mDropGesture.cancel();

        // Only resubmit the gesture only if it isn't the last event we will receive.
        if (!isLastEventInGesture(actionMasked)) mDropGesture.submit();
        return true;
    }

    /**
     * Check if the current event is the last event in the gesture.
     *
     * @param actionMasked The action mask of the MotionEvent.
     * @return True if the event is the last event in the gesture, false otherwise.
     */
    private boolean isLastEventInGesture(int actionMasked) {
        return actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL;
    }

    /**
     * Scale the input value based on the current scale factor.
     *
     * @param input The input value to scale.
     * @return The scaled value.
     */
    private int mcScale(int input) {
        return (int) ((mGuiScale * input) / mScaleFactor);
    }

    /**
     * Handle option changes for the HotbarView.
     */
    @Override
    public void onOptionChanged() {
        post(this);
    }

    /**
     * Reposition the HotbarView when the scale changes.
     */
    @Override
    public void run() {
        if (getParent() == null) return;
        int scale = MCOptionUtils.getMcScale();
        if (scale == mGuiScale) return;
        mGuiScale = scale;
        repositionView();
    }

    /**
     * Handle layout changes for the HotbarView.
     *
     * @param v The view that changed layout.
     * @param left The new left position of the view.
     * @param top The new top position of the view.
     * @param right The new right position of the view.
     * @param bottom The new bottom position of the view.
     * @param oldLeft The old left position of the view.
     * @param oldTop The old top position of the view.
     * @param oldRight The old right position of the view.
     * @param oldBottom The old bottom position of the view.
     */
    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        // We need to check whether dimensions match or not because here we are looking specifically for changes of dimensions
        // and Android keeps calling this without dimensions actually changing for some reason.
        if (v.equals(mParentView) && (left != oldLeft || right != oldRight || top != oldTop || bottom != oldBottom)) {
            // Need to post this, because it is not correct to resize the view
            // during a layout pass.
            post(this::repositionView);
        }
    }
}
