package net.kdt.pojavlaunch.customcontrols.gamepad;

import net.kdt.pojavlaunch.utils.LwjglGlfwKeycode;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class GamepadBindingsTest {
    @Test
    public void newInstallPreservesBothOriginalProfiles() {
        GamepadMap game = GamepadMap.getDefaultGameMap();
        GamepadMap menu = GamepadMap.getDefaultMenuMap();
        GamepadBindings.apply(Collections.emptyMap(), game);
        GamepadBindings.apply(Collections.emptyMap(), menu);
        assertProfilesEqual(GamepadMap.getDefaultGameMap(), game);
        assertProfilesEqual(GamepadMap.getDefaultMenuMap(), menu);
        assertTrue(game.THUMBSTICK_RIGHT.isToggleable);
        assertArrayEquals(new int[]{LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT}, menu.BUTTON_A.keycodes);
        assertEquals(5, menu.DIRECTION_FORWARD.length);
    }

    @Test
    public void everyInputCanBeReboundInBothProfilesWithoutChangingOtherInputs() {
        for (GamepadBindings.Input selected : GamepadBindings.Input.values()) {
            Map<String, Object> saved = new HashMap<>();
            saved.put(selected.preferenceKey, Integer.toString(LwjglGlfwKeycode.GLFW_KEY_F6));
            for (boolean captured : new boolean[]{false, true}) {
                GamepadMap original = captured ? GamepadMap.getDefaultGameMap() : GamepadMap.getDefaultMenuMap();
                GamepadMap rebound = captured ? GamepadMap.getDefaultGameMap() : GamepadMap.getDefaultMenuMap();
                GamepadBindings.apply(saved, rebound);
                for (GamepadBindings.Input input : GamepadBindings.Input.values()) {
                    assertArrayEquals(input.name(), selected == input
                            ? new int[]{LwjglGlfwKeycode.GLFW_KEY_F6} : original.getBinding(input),
                            rebound.getBinding(input));
                }
            }
        }
    }

    @Test
    public void buttonIdentitiesMatchTheRuntimeDispatcher() {
        GamepadMap map = GamepadMap.getDefaultMenuMap();
        GamepadButton[] expected = {map.BUTTON_A, map.BUTTON_B, map.BUTTON_X, map.BUTTON_Y,
                map.BUTTON_SELECT, map.BUTTON_START, map.TRIGGER_LEFT, map.TRIGGER_RIGHT,
                map.SHOULDER_LEFT, map.SHOULDER_RIGHT, map.THUMBSTICK_LEFT, map.THUMBSTICK_RIGHT,
                map.DPAD_UP, map.DPAD_RIGHT, map.DPAD_DOWN, map.DPAD_LEFT};
        for (int i = 0; i < expected.length; i++) {
            int[] key = new int[]{LwjglGlfwKeycode.GLFW_KEY_F1 + i};
            map.setBinding(GamepadBindings.Input.values()[i], key);
            assertArrayEquals(key, expected[i].keycodes);
        }
        map.setBinding(GamepadBindings.Input.STICK_UP, new int[]{11});
        map.setBinding(GamepadBindings.Input.STICK_RIGHT, new int[]{12});
        map.setBinding(GamepadBindings.Input.STICK_DOWN, new int[]{13});
        map.setBinding(GamepadBindings.Input.STICK_LEFT, new int[]{14});
        assertArrayEquals(new int[]{11}, map.DIRECTION_FORWARD);
        assertArrayEquals(new int[]{12}, map.DIRECTION_RIGHT);
        assertArrayEquals(new int[]{13}, map.DIRECTION_BACKWARD);
        assertArrayEquals(new int[]{14}, map.DIRECTION_LEFT);
    }

    @Test
    public void unbindingAndResettingRestoreMouseActionsAndToggleDefaults() {
        Map<String, Object> saved = new HashMap<>();
        for (GamepadBindings.Input input : GamepadBindings.Input.values()) {
            saved.put(input.preferenceKey, GamepadBindings.UNBOUND);
        }
        GamepadMap unbound = GamepadMap.getDefaultGameMap();
        GamepadBindings.apply(saved, unbound);
        for (GamepadBindings.Input input : GamepadBindings.Input.values()) {
            assertEquals(0, unbound.getBinding(input).length);
            saved.remove(input.preferenceKey);
        }
        assertFalse(unbound.THUMBSTICK_RIGHT.isToggleable);
        GamepadMap restored = GamepadMap.getDefaultMenuMap();
        GamepadBindings.apply(saved, restored);
        assertProfilesEqual(GamepadMap.getDefaultMenuMap(), restored);
    }

    @Test
    public void reboundStickClickIsMomentaryInsteadOfLeavingTheKeyToggled() {
        Map<String, Object> saved = new HashMap<>();
        saved.put(GamepadBindings.Input.R3.preferenceKey, Integer.toString(LwjglGlfwKeycode.GLFW_KEY_ENTER));
        GamepadMap map = GamepadMap.getDefaultGameMap();
        GamepadBindings.apply(saved, map);
        assertFalse(map.THUMBSTICK_RIGHT.isToggleable);
    }

    @Test
    public void invalidPreferencesCannotBecomeMouseCodesOrCrashStartup() {
        for (Object invalid : new Object[]{null, 290, true, "garbage", "999999999999", "-1", "-2", "0", "1", "2", "999", "default"}) {
            Map<String, Object> saved = new HashMap<>();
            saved.put(GamepadBindings.Input.A.preferenceKey, invalid);
            GamepadMap map = GamepadMap.getDefaultMenuMap();
            GamepadBindings.apply(saved, map);
            assertProfilesEqual(GamepadMap.getDefaultMenuMap(), map);
        }
    }

    @Test
    public void savedKeysSurviveFreshMapsAndUseIndependentStablePreferenceNames() {
        Set<String> preferenceNames = new HashSet<>();
        Map<String, Object> saved = new HashMap<>();
        for (GamepadBindings.Input input : GamepadBindings.Input.values()) {
            assertTrue(preferenceNames.add(input.preferenceKey));
            saved.put(input.preferenceKey, Integer.toString(LwjglGlfwKeycode.GLFW_KEY_A + input.ordinal()));
        }
        GamepadMap firstLaunch = GamepadMap.getDefaultMenuMap();
        GamepadMap nextLaunch = GamepadMap.getDefaultMenuMap();
        GamepadBindings.apply(saved, firstLaunch);
        GamepadBindings.apply(new HashMap<>(saved), nextLaunch);
        assertProfilesEqual(firstLaunch, nextLaunch);
        assertEquals("gamepad_binding_a", GamepadBindings.Input.A.preferenceKey);
        assertEquals("gamepad_binding_stick_left", GamepadBindings.Input.STICK_LEFT.preferenceKey);
    }

    private static void assertProfilesEqual(GamepadMap expected, GamepadMap actual) {
        for (GamepadBindings.Input input : GamepadBindings.Input.values()) {
            assertArrayEquals(input.name(), expected.getBinding(input), actual.getBinding(input));
        }
        GamepadButton[] expectedButtons = expected.getButtons();
        GamepadButton[] actualButtons = actual.getButtons();
        for (int i = 0; i < expectedButtons.length; i++) {
            assertEquals(expectedButtons[i].isToggleable, actualButtons[i].isToggleable);
        }
    }
}
