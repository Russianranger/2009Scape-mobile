package net.kdt.pojavlaunch.customcontrols.gamepad;

import net.kdt.pojavlaunch.utils.LwjglGlfwKeycode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keyboard overrides shared by the settings page and the HD controller handler. */
public final class GamepadBindings {
    public static final String PREFIX = "gamepad_binding_";
    public static final String DEFAULT = "default";
    public static final String UNBOUND = "none";

    // The first sixteen entries follow GamepadMap.getButtons(). Never use ordinals as saved keys.
    public enum Input {
        A("a"), B("b"), X("x"), Y("y"), SELECT("select"), START("start"),
        L2("l2"), R2("r2"), L1("l1"), R1("r1"), L3("l3"), R3("r3"),
        DPAD_UP("dpad_up"), DPAD_RIGHT("dpad_right"),
        DPAD_DOWN("dpad_down"), DPAD_LEFT("dpad_left"),
        STICK_UP("stick_up"), STICK_RIGHT("stick_right"),
        STICK_DOWN("stick_down"), STICK_LEFT("stick_left");

        public final String preferenceKey;

        Input(String id) {
            preferenceKey = PREFIX + id;
        }
    }

    /** Stable GLFW values, not Android key codes or positions in a picker. */
    public static final Map<Integer, String> KEY_NAMES;
    static {
        Map<Integer, String> keys = new LinkedHashMap<>();
        for (int key = LwjglGlfwKeycode.GLFW_KEY_A; key <= LwjglGlfwKeycode.GLFW_KEY_Z; key++) {
            keys.put(key, Character.toString((char) key));
        }
        for (int key = LwjglGlfwKeycode.GLFW_KEY_0; key <= LwjglGlfwKeycode.GLFW_KEY_9; key++) {
            keys.put(key, Character.toString((char) key));
        }
        for (int i = 0; i < 25; i++) keys.put(LwjglGlfwKeycode.GLFW_KEY_F1 + i, "F" + (i + 1));
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_SPACE, "Space");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_ENTER, "Enter");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_ESCAPE, "Escape");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_TAB, "Tab");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_BACKSPACE, "Backspace");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_UP, "Up arrow");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_DOWN, "Down arrow");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_LEFT, "Left arrow");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_RIGHT, "Right arrow");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_INSERT, "Insert");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_DELETE, "Delete");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_HOME, "Home");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_END, "End");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_PAGE_UP, "Page Up");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_PAGE_DOWN, "Page Down");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, "Left Shift");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT, "Right Shift");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, "Left Ctrl");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL, "Right Ctrl");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT, "Left Alt");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT, "Right Alt");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_LEFT_SUPER, "Left Super");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_RIGHT_SUPER, "Right Super");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_MENU, "Menu");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK, "Caps Lock");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK, "Num Lock");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_SCROLL_LOCK, "Scroll Lock");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_PRINT_SCREEN, "Print Screen");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_PAUSE, "Pause");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE, "Apostrophe (')");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_COMMA, "Comma (,)");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_MINUS, "Minus (-)");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_PERIOD, "Period (.)");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_SLASH, "Slash (/)");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_SEMICOLON, "Semicolon (;)");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_EQUAL, "Equals (=)");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET, "Left bracket ([)");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET, "Right bracket (])");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_BACKSLASH, "Backslash (\\)");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT, "Grave accent (`)");
        for (int i = 0; i < 10; i++) keys.put(LwjglGlfwKeycode.GLFW_KEY_KP_0 + i, "Numpad " + i);
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_KP_DECIMAL, "Numpad decimal");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_KP_DIVIDE, "Numpad /");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_KP_MULTIPLY, "Numpad *");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_KP_SUBTRACT, "Numpad -");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_KP_ADD, "Numpad +");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_KP_ENTER, "Numpad Enter");
        keys.put((int) LwjglGlfwKeycode.GLFW_KEY_KP_EQUAL, "Numpad =");
        KEY_NAMES = Collections.unmodifiableMap(keys);
    }

    private GamepadBindings() {}

    /** Treat corrupt, obsolete, or wrong-type preferences as the original mapping. */
    public static String readValue(Map<String, ?> preferences, Input input) {
        Object value = preferences.get(input.preferenceKey);
        if (UNBOUND.equals(value)) return UNBOUND;
        if (value instanceof String) {
            try {
                int key = Integer.parseInt((String) value);
                if (KEY_NAMES.containsKey(key)) return Integer.toString(key);
            } catch (NumberFormatException ignored) {
                // Includes the explicit "default" choice.
            }
        }
        return DEFAULT;
    }

    /** Apply to a fresh default map so untouched mouse actions and toggle behavior survive. */
    public static void apply(Map<String, ?> preferences, GamepadMap map) {
        for (Input input : Input.values()) {
            String value = readValue(preferences, input);
            if (DEFAULT.equals(value)) continue;
            map.setBinding(input, UNBOUND.equals(value) ? new int[0]
                    : new int[]{Integer.parseInt(value)});
        }
    }
}
