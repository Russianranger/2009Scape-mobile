# Controller key bindings

Open **Settings → Control customization → Controller settings → Controller key bindings**.
Tap a controller input and choose a keyboard key. No physical keyboard is required.
The selection saves automatically. Restart the HD game to apply changes, including resets.

All 16 controller buttons (including triggers, shoulders, stick clicks, Start/Select and
the D-pad) and the four directional stick inputs can be rebound. The list includes
letters, digits, function keys, arrows, modifiers, navigation, punctuation and numpad keys.
**Unbound** disables that input. **Default** restores its original action, including mouse
clicks and scrolling. **Reset key bindings** restores every input without changing
controller calibration, deadzones, touch controls or other settings.

The defaults shown are for pointer mode, which is the usual 2009Scape mode. In that mode
the left stick moves the pointer and the right stick supplies directional inputs.
When the game captures the cursor, those roles swap. A custom keyboard binding applies
in both modes, while Default preserves each mode's original mapping. A rebound button
is held only while pressed, including R3, which originally toggles Shift in captured mode.

This setting uses the existing **HD** controller support. The SD launcher has a separate
AWT input path and does not use these mappings. Game or plugin support still determines
what a selected keyboard key does.

## Where the functionality lives

Paths below are relative to `app_pojavlauncher/src/main/`.

| Component | Location and responsibility |
| --- | --- |
| Android controller events | `java/net/kdt/pojavlaunch/GLFWGLSurface.java`: `processKeyEvent` and `dispatchGenericMotionEvent` pass controller events to the existing hardware remapper. |
| Controller dispatch | `java/net/kdt/pojavlaunch/customcontrols/gamepad/Gamepad.java`: `handleGamepadInput`, `sendDirectionalKeycode` and `sendInput` translate logical inputs to keyboard/mouse events. |
| Original mappings | `java/net/kdt/pojavlaunch/customcontrols/gamepad/GamepadMap.java`: `getDefaultGameMap` and `getDefaultMenuMap` define the two original cursor modes. |
| Saved keyboard overrides | `java/net/kdt/pojavlaunch/customcontrols/gamepad/GamepadBindings.java`: stable input identifiers, keyboard choices, validation and application to both default maps. |
| Settings | `res/xml/pref_control.xml` links to `java/net/kdt/pojavlaunch/prefs/screens/LauncherPreferenceGamepadFragment.java`, with layout and text in `res/xml/pref_gamepad_bindings.xml` and `res/values/gamepad_bindings.xml`. |
| Native keyboard bridge | `java/org/lwjgl/glfw/CallbackBridge.java`: sends GLFW keyboard events to the client. |

The existing `GamepadRemapPreference` clears the external hardware remapper's calibration;
it is separate from keyboard bindings.

Overrides use the normal launcher preferences, under `gamepad_binding_<input>`, with
string values containing stable GLFW key codes. `default` preserves the original mapping;
`none` disables it. Missing or invalid values fall back to the defaults. The HD game runs
in a separate Android process and reads a snapshot when the gamepad is created. No
cross-process preference listener or live-reload guarantee is assumed.

`Gamepad.releaseAllInputs()` releases the current buttons and directional keys before a
cursor-mode change and when `MainActivity` pauses, so a held rebound key does not stay
pressed after leaving the game.

## Validation

`GamepadBindingsTest` covers preservation of both default profiles, all 20 logical inputs,
runtime field identities, invalid preference values, unbinding/reset, fresh-map reload
and the momentary behavior of rebound R3. `GamepadJoystickTest` checks all eight stick
sectors and the deadzone; angle normalization fixes the negative sector numbers previously
produced for downward stick movement. Run with a configured Android toolchain:

```sh
./gradlew :app_pojavlauncher:testDebugUnitTest :app_pojavlauncher:assembleDebug
```

Device checks for an AYN Thor or other Android controller:

1. In HD mode, confirm the original pointer, mouse-button and scroll behavior before
   changing bindings.
2. Bind A to F1, a shoulder to F2, a trigger to F3, D-pad up to Up arrow, and a directional
   stick input to Right arrow. Restart HD and verify each input; test triggers and the
   D-pad because controllers can report them as either axes or button events.
3. Bind R3 to a letter or modifier. Press/release it, background the app while holding it,
   and return. Confirm the key releases and the pointer stops moving while paused.
4. Reopen settings and verify saved selections. Set one input to Unbound, then Default,
   and test after each restart. Reset all bindings and verify the original mouse actions
   and other settings.

These device checks require a real controller and are separate from the automated tests.
