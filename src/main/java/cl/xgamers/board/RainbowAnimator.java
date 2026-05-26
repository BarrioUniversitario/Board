package cl.xgamers.board;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.regex.Pattern;

/**
 * Genera títulos con gradiente arcoíris animado (&#RRGGBB por carácter).
 */
public final class RainbowAnimator {

    private static final Pattern STRIP_COLOR = Pattern.compile("(?i)[&§][0-9a-fk-orx]|&#[0-9a-fA-F]{6}|#[0-9a-fA-F]{6}");

    private RainbowAnimator() {
    }

    public static String format(FileConfiguration config, int frame) {
        String text = config.getString("title.text", null);
        if (text == null || text.isBlank()) {
            text = config.getString("title", "Board");
        }
        if (text == null || text.isBlank()) {
            var lines = config.getStringList("title.lines");
            if (!lines.isEmpty()) {
                text = lines.getFirst();
            } else {
                text = "Board";
            }
        }

        boolean bold = config.getBoolean("title.rainbow.bold", true);
        double speed = config.getDouble("title.rainbow.speed", 12.0);
        double spread = config.getDouble("title.rainbow.spread", 28.0);
        float saturation = (float) clamp(config.getDouble("title.rainbow.saturation", 0.85), 0, 1);
        float brightness = (float) clamp(config.getDouble("title.rainbow.brightness", 1.0), 0.2, 1);

        return Hex.colorize(buildGradient(stripColors(text), frame, speed, spread, saturation, brightness, bold));
    }

    private static String buildGradient(String plain, int frame, double speed, double spread,
                                        float saturation, float brightness, boolean bold) {
        if (plain.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder(plain.length() * 14);
        if (bold) {
            out.append("&l");
        }

        char[] chars = plain.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == ' ') {
                out.append(' ');
                continue;
            }
            float hue = (float) ((frame * speed + i * spread) % 360.0);
            out.append("&#").append(hsvToHex(hue, saturation, brightness)).append(c);
        }
        return out.toString();
    }

    private static String stripColors(String input) {
        String stripped = STRIP_COLOR.matcher(input).replaceAll("");
        return ChatColor.stripColor(stripped != null ? stripped : input).trim();
    }

    private static String hsvToHex(float hue, float saturation, float brightness) {
        float c = brightness * saturation;
        float x = c * (1 - Math.abs((hue / 60f) % 2 - 1));
        float m = brightness - c;

        float r;
        float g;
        float b;

        if (hue < 60) {
            r = c;
            g = x;
            b = 0;
        } else if (hue < 120) {
            r = x;
            g = c;
            b = 0;
        } else if (hue < 180) {
            r = 0;
            g = c;
            b = x;
        } else if (hue < 240) {
            r = 0;
            g = x;
            b = c;
        } else if (hue < 300) {
            r = x;
            g = 0;
            b = c;
        } else {
            r = c;
            g = 0;
            b = x;
        }

        int ri = Math.round((r + m) * 255);
        int gi = Math.round((g + m) * 255);
        int bi = Math.round((b + m) * 255);

        return String.format("%02x%02x%02x", ri, gi, bi);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
