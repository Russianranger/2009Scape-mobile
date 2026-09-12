package net.kdt.pojavlaunch.customcontrols.gamepad;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GamepadJoystickTest {
    @Test
    public void allCardinalAndDiagonalDirectionsHaveValidSectors() {
        GamepadJoystick stick = new GamepadJoystick(0, 1, null) {
            @Override public float getDeadzone() { return 0.2f; }
        };
        int[][] samples = {
                {1, 0, GamepadJoystick.DIRECTION_EAST},
                {1, -1, GamepadJoystick.DIRECTION_NORTH_EAST},
                {0, -1, GamepadJoystick.DIRECTION_NORTH},
                {-1, -1, GamepadJoystick.DIRECTION_NORTH_WEST},
                {-1, 0, GamepadJoystick.DIRECTION_WEST},
                {-1, 1, GamepadJoystick.DIRECTION_SOUTH_WEST},
                {0, 1, GamepadJoystick.DIRECTION_SOUTH},
                {1, 1, GamepadJoystick.DIRECTION_SOUTH_EAST},
                {0, 0, GamepadJoystick.DIRECTION_NONE}
        };
        for (int[] sample : samples) {
            stick.setXAxisValue(sample[0]);
            stick.setYAxisValue(sample[1]);
            assertEquals(sample[0] + "," + sample[1], sample[2], stick.getHeightDirection());
        }
        stick.setXAxisValue(0.1f);
        stick.setYAxisValue(0.1f);
        assertEquals(GamepadJoystick.DIRECTION_NONE, stick.getHeightDirection());
    }
}
