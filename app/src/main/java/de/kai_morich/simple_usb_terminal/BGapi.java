package de.kai_morich.simple_usb_terminal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper class for the constants we use to communicate with the Gecko.
 *
 * While these technically should be findable in Silicon Lab's documentation, it's a lot easier
 *  to open up the NCP Commander in Simplicity Studio, manually send the commands there, and turn
 *  on the option to display the bytes that were sent
 * */
public class BGapi {

    private static final HashMap<String, byte[]> knownResponses = new HashMap<>();
    private static final HashMap<String, String> commands = new HashMap<>();

    static {
        knownResponses.put("scanner_set_mode_rsp", new byte[]{0x20, 0x02, 0x05, 0x02, 0x00, 0x00});
        knownResponses.put("scanner_set_timing_rsp", new byte[]{0x20, 0x02, 0x05, 0x01, 0x00, 0x00});
        knownResponses.put("connection_set_default_parameters_rsp", new byte[]{0x20, 0x02, 0x06, 0x00, 0x00, 0x00});
        knownResponses.put("scanner_start_rsp", new byte[]{0x20, 0x02, 0x05, 0x03, 0x00, 0x00});
        knownResponses.put("scanner_start_failed_rsp", new byte[]{0x20, 0x02, 0x05, 0x03, 0x02, 0x00});
        knownResponses.put("scanner_stop_rsp", new byte[]{0x20, 0x02, 0x05, 0x05, 0x00, 0x00});
        knownResponses.put("message_rotate_slow_rsp", new byte[]{0x20, 0x04, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01, 0x01});
        knownResponses.put("message_rotate_fast_rsp", new byte[]{0x20, 0x04, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01, 0x02});
        knownResponses.put("message_rotate_cw_rsp", new byte[]{0x20, 0x04, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01, 0x03});
        knownResponses.put("message_rotate_ccw_rsp", new byte[]{0x20, 0x04, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01, 0x04});
        knownResponses.put("message_rotate_stop_rsp", new byte[]{0x20, 0x04, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01, 0x05});
        knownResponses.put("message_read_angle_rsp", new byte[]{(byte) 0xA0, 0x04, (byte) 0xFF, 0x00, 0x03});
        knownResponses.put("message_user_to_target_rsp", new byte[]{0x20, 0x03, (byte) 0xFF, 0x00, 0x01, 0x00, 0x00});
        //knownResponses.put("message_related_rsp???", new byte[]{0x20, 0x03, (byte) 0xFF, 0x00, 0x01, 0x00, 0x00});
        knownResponses.put("message_system_boot", new byte[]{(byte) 0xA0, 0x12, 0x01, 0x00, 0x03, 0x00, 0x03, 0x00, 0x02, 0x00,
                (byte)0x96, 0x01, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, (byte)0xE0, 0x5C, 0x35, 0x51});

        commands.put("scanner_set_mode", "200205020401");
        commands.put("scanner_set_timing", "200505010410001000");
        commands.put("connection_set_parameters", "200c060050005000000064000000ffff");
        commands.put("scanner_start", "200205030402");
        commands.put("scanner_stop", "20000505");
        commands.put("message_rotate_slow", "2002FF000101");//this is toggle PWM_LOW
        commands.put("message_rotate_fast", "2002FF000102");//this is toggle PWM_HIGH
        commands.put("message_rotate_cw", "2002FF000103");//this is move MTR_RIGHT
        commands.put("message_rotate_ccw", "2002FF000104");//this is move MTR_LEFT
        commands.put("message_rotate_stop", "2002FF000105");//this is PWM_OFF
        commands.put("message_read_pot_angle", "2002FF000106");
        commands.put("message_get_temp", "2002FF000104");
    }

    public static final String SCANNER_SET_MODE = commands.get("scanner_set_mode");
    public static final String SCANNER_SET_TIMING = commands.get("scanner_set_timing");
    public static final String CONNECTION_SET_PARAMETERS = commands.get("connection_set_parameters");
    public static final String SCANNER_START = commands.get("scanner_start");
    public static final String SCANNER_STOP = commands.get("scanner_stop");
    public static final String ROTATE_CW = commands.get("message_rotate_cw");
    public static final String ROTATE_CCW = commands.get("message_rotate_ccw");
    public static final String ROTATE_STOP = commands.get("message_rotate_stop");
    public static final String ROTATE_SLOW = commands.get("message_rotate_slow");
    public static final String ROTATE_FAST = commands.get("message_rotate_fast");
    public static final String GET_ANGLE = commands.get("message_read_pot_angle");
    public static final String GET_TEMP = commands.get("message_get_temp");

    public static boolean isScanReportEvent(byte[] bytes) {
        // Note: the casting of 0xA1 is necessary because Java thinks that signed hex should exist?
        //      and as a result does funny things. This only applies to A0 because the other values
        //      do not change if treated as signed
        //return bytes.length > 3 && bytes[0] == (byte) 0xA0 && bytes[1] == 0x00
        //        && bytes[2] == 0x05 && bytes[3] == 0x02;

        //updated to reflect new extended advertisement report logic. Not totally sure why byte-by-
        //byte comparison is done, just following existing style for now.
        //
        return bytes.length > 3 && bytes[0] == (byte) 0xA0 && bytes[1] == (byte) 0x04
                && bytes[2] == (byte)0xFF && bytes[3] == (byte)0x00;
    }

    public static boolean isAngleOrBattResponse(byte[] bytes) {

        return bytes.length > 4 && bytes[0] == (byte) 0xA0 //stupid, but I know it'll work based on the above
                && bytes[1] == 0x04 //todo: why do these change?
                && bytes[2] == (byte) 0xFF
                && bytes[3] == 0x00
                && bytes[4] == 0x03;
    }

    public static boolean isTemperatureResponse(byte[] bytes) {
        return bytes.length == 9 && bytes[bytes.length-1] == 0x69;
    }

    public static boolean isKnownResponse(byte[] bytes) {
        for (byte[] response : knownResponses.values()) {
            if (findPatternInBytes(bytes, response) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find a pattern within a byte array
     * @param buffer The buffer to search in
     * @param pattern The pattern to search for
     * @return The starting position of the pattern, or -1 if not found
     */
    private static int findPatternInBytes(byte[] buffer, byte[] pattern) {
        for (int i = 0; i <= buffer.length - pattern.length; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                if (buffer[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

    public static String getResponseName(byte[] bytes) {
        for (Map.Entry<String, byte[]> entry : knownResponses.entrySet()) {
            if (findPatternInBytes(bytes, entry.getValue()) >= 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get the response name and position where it was found
     * @param bytes The byte array to search in
     * @return An array containing [responseName, position] or null if not found
     */
    public static Object[] getResponseNameAndPosition(byte[] bytes) {
        for (Map.Entry<String, byte[]> entry : knownResponses.entrySet()) {
            int position = findPatternInBytes(bytes, entry.getValue());
            if (position >= 0) {
                return new Object[]{entry.getKey(), position};
            }
        }
        return null;
    }

    public static String getCommandValue(String msg) {
        for (Map.Entry<String, String> entry : commands.entrySet()) {
            if (entry.getKey().equals(msg)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static String getCommandName(String msg) {
        for (Map.Entry<String, String> entry : commands.entrySet()) {
            if (entry.getValue().equals(msg)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean isCommand(String msg) {
        for (Map.Entry<String, String> entry : commands.entrySet()) {
            if (entry.getValue().equals(msg)) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, byte[]> getKnownResponses() {
        return new HashMap<>(knownResponses);
    }
}
