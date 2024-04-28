package net.kdt.pojavlaunch.customcontrols;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.config.LwjglGlfwKeycode;

@Keep
public class CustomControls {
    public static final int CURRENT_VERSION = 6;
    public int version = -1;
    public float scaledAt;
    @NonNull
    public final List<ControlData> mControlDataList;
    @NonNull
    public final List<ControlDrawerData> mDrawerDataList;
    @NonNull
    public final List<ControlJoystickData> mJoystickDataList;

    public CustomControls() {
        this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public CustomControls(@NonNull List<ControlData> mControlDataList,
                          @NonNull List<ControlDrawerData> mDrawerDataList,
                          @NonNull List<ControlJoystickData> mJoystickDataList) {
        this.mControlDataList = mControlDataList;
        this.mDrawerDataList = mDrawerDataList;
        this.mJoystickDataList = mJoystickDataList;
        this.scaledAt = 100f;
    }

    // Generate default control
    // Here for historical reasons
    // Just admire it idk
    @SuppressWarnings("unused")
    public CustomControls(@NonNull Context ctx) {
        this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        addDefaultControls(ctx);
        this.version = CURRENT_VERSION;
    }

    private void addDefaultControls(@NonNull Context ctx) {
        mControlDataList.add(new ControlData(ControlData.getSpecialButtons()[0])); // Keyboard
        mControlDataList.add(new ControlData(ControlData.getSpecialButtons()[1])); // GUI
        mControlDataList.add(new ControlData(ControlData.getSpecialButtons()[2])); // Primary Mouse mControlDataList
        mControlDataList.add(new ControlData(ControlData.getSpecialButtons()[3])); // Secondary Mouse mControlDataList
        mControlDataList.add(new ControlData(ControlData.getSpecialButtons()[4])); // Virtual mouse toggle

        mControlDataList.add(new ControlData(ctx, R.string.control_debug, new int[]{LwjglGlfwKeycode.GLFW_KEY_F3}, "${margin}", "${margin}", false));
        mControlDataList.add(new ControlData(ctx, R.string.control_chat, new int[]{LwjglGlfwKeycode.GLFW_KEY_T}, "${margin} * 2 + ${width}", "${margin}", false));
        mControlDataList.add(new ControlData(ctx, R.string.control_listplayers, new int[]{LwjglGlfwKeycode.GLFW_KEY_TAB}, "${margin} * 4 + ${width} * 3", "${margin}", false));
        mControlDataList.add(new ControlData(ctx, R.string.control_thirdperson, new int[]{LwjglGlfwKeycode.GLFW_KEY_F5}, "${margin}", "${height} + ${margin}", false));

        mControlDataList.add(new ControlData(ctx, R.string.control_up, new int[]{LwjglGlfwKeycode.GLFW_KEY_W}, "${margin} * 2 + ${width}", "${bottom} - ${margin} * 3 - ${height} * 2", true));
        mControlDataList.add(new ControlData(ctx, R.string.control_left, new int[]{LwjglGlfwKeycode.GLFW_KEY_A}, "${margin}", "${bottom} - ${margin} * 2 - ${height}", true));
        mControlDataList.add(new ControlData(ctx, R.string.control_down, new int[]{LwjglGlfwKeycode.GLFW_KEY_S}, "${margin} * 2 + ${width}", "${bottom} - ${margin}", true));
        mControlDataList.add(new ControlData(ctx, R.string.control_right, new int[]{LwjglGlfwKeycode.GLFW_KEY_D}, "${margin} * 3 + ${width} * 2", "${bottom} - ${margin} * 2 - ${height}", true));

        mControlDataList.add(new ControlData(ctx, R.string.control_inventory, new int[]{LwjglGlfwKeycode.GLFW_KEY_E}, "${margin} * 3 + ${width} * 2", "${bottom} - ${margin}", true));

        ControlData shiftData = new ControlData(ctx, R.string.control_shift, new int[]{LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT}, "${margin} * 2 + ${width}", "${screen_height} - ${margin} * 2 - ${height} * 2", true);
        shiftData.isToggle = true;
        mControlDataList.add(shiftData);
        mControlDataList.add(new ControlData(ctx, R.string.control_jump, new int[]{LwjglGlfwKeycode.GLFW_KEY_SPACE}, "${right} - ${margin} * 2 - ${width}", "${bottom} - ${margin} * 2 - ${height}", true));
    }

    public void save(@NonNull String path) throws IOException {
        if (version != CURRENT_VERSION) {
            version = CURRENT_VERSION;
        }
        Tools.write(path, Tools.GLOBAL_GSON.toJson(this));
    }
}
