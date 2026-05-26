package cl.xgamers.board;

import fr.mrmicky.fastboard.FastBoard;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoardManager {

    private final Board plugin;
    private final Map<UUID, FastBoard> boards = new HashMap<>();
    private final Map<UUID, Boolean> toggledOff = new HashMap<>();
    private int titleAnimationIndex = 0;
    private int titleRainbowTicks = 0;
    private int headerAnimationIndex = 0;
    private int footerAnimationIndex = 0;
    private long elapsedTicks = 0;

    public BoardManager(Board plugin) {
        this.plugin = plugin;
    }

    public void createBoard(Player player) {
        FastBoard board = new FastBoard(player);
        boards.put(player.getUniqueId(), board);
        updateBoard(player);
    }

    public void removeBoard(Player player) {
        FastBoard board = boards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    public void updateBoard(Player player) {
        FastBoard board = boards.get(player.getUniqueId());
        if (board == null || isToggledOff(player)) return;

        board.updateTitle(getTitle());

        List<String> headers = plugin.getConfig().getStringList("header.lines");
        List<String> footers = plugin.getConfig().getStringList("footer.lines");
        List<String> lines = plugin.getConfig().getStringList("lines");

        String header = getAnimatedLine("header", headerAnimationIndex, "");
        String footer = getAnimatedLine("footer", footerAnimationIndex, "");

        // FastBoard: index 0 = bottom, last index = top (below title)
        int extraLines = (footers.isEmpty() ? 0 : 1) + (headers.isEmpty() ? 0 : 1);
        String[] boardLines = new String[lines.size() + extraLines];
        int index = 0;

        if (!footers.isEmpty()) {
            boardLines[index++] = footer.isEmpty() ? "" : footer;
        }
        for (String line : lines) {
            boardLines[index++] = Hex.colorize(replacePlaceholders(line, player));
        }
        if (!headers.isEmpty()) {
            boardLines[index++] = header.isEmpty() ? "" : header;
        }

        board.updateLines(boardLines);
    }

    private String getTitle() {
        if (isRainbowTitle()) {
            return RainbowAnimator.format(plugin.getConfig(), titleAnimationIndex);
        }
        return Hex.colorize(getAnimatedLine("title", titleAnimationIndex, "Board"));
    }

    private boolean isRainbowTitle() {
        if (!plugin.getConfig().getBoolean("animations.enabled", true)) {
            return false;
        }
        return plugin.getConfig().getBoolean("title.rainbow.enabled", false);
    }

    private boolean shouldAnimateTitle() {
        if (!plugin.getConfig().getBoolean("animations.enabled", true)) {
            return false;
        }
        if (isRainbowTitle()) {
            return true;
        }
        return shouldAnimate("title");
    }

    private String getAnimatedLine(String section, int animationIndex, String fallback) {
        List<String> sectionLines = getSectionLines(section);
        if (sectionLines.isEmpty()) {
            return fallback;
        }
        if (!shouldAnimate(section)) {
            return Hex.colorize(sectionLines.getFirst());
        }
        return Hex.colorize(sectionLines.get(animationIndex % sectionLines.size()));
    }

    private List<String> getSectionLines(String section) {
        if ("title".equals(section)) {
            if (plugin.getConfig().isString("title")) {
                String singleTitle = plugin.getConfig().getString("title");
                return singleTitle != null && !singleTitle.isBlank()
                        ? List.of(singleTitle)
                        : Collections.emptyList();
            }
            List<String> titleLines = plugin.getConfig().getStringList("title.lines");
            if (!titleLines.isEmpty()) {
                return titleLines;
            }
            String legacyTitle = plugin.getConfig().getString("title", "Board");
            return legacyTitle != null && !legacyTitle.isBlank()
                    ? List.of(legacyTitle)
                    : Collections.emptyList();
        }
        return plugin.getConfig().getStringList(section + ".lines");
    }

    private boolean shouldAnimate(String section) {
        if (!plugin.getConfig().getBoolean("animations.enabled", true)) {
            return false;
        }
        List<String> sectionLines = getSectionLines(section);
        if (sectionLines.size() < 2) {
            return false;
        }
        return plugin.getConfig().getBoolean(section + ".animation.enabled", false);
    }

    private String replacePlaceholders(String line, Player player) {
        line = line.replace("%board_online%", String.valueOf(Bukkit.getOnlinePlayers().size()));

        for (BoardServerRegistry.ServerEntry entry : plugin.getServerRegistry().getEntries().values()) {
            String id = entry.getId();
            String count = String.valueOf(plugin.getServerCount(id));
            String max = plugin.getServerRegistry().getMaxPlayers(id);
            line = line.replace("%board_" + id + "_online%", count);
            line = line.replace("%board_" + id + "_connected%", count);
            line = line.replace("%board_" + id + "_max%", max);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            line = PlaceholderAPI.setPlaceholders(player, line);
        }

        return line;
    }

    public void updateAllBoards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateBoard(player);
        }

        int updateInterval = plugin.getConfig().getInt("animations.interval", 20);
        elapsedTicks += updateInterval;
        advanceAnimations();
    }

    private void advanceAnimations() {
        if (!plugin.getConfig().getBoolean("animations.enabled", true)) {
            return;
        }

        if (shouldAnimateTitle()) {
            if (isRainbowTitle()) {
                titleRainbowTicks++;
                if (titleRainbowTicks >= getAnimationInterval("title")) {
                    titleAnimationIndex++;
                    titleRainbowTicks = 0;
                }
            } else if (elapsedTicks % getAnimationInterval("title") == 0) {
                titleAnimationIndex++;
            }
        }
        if (shouldAnimate("header") && elapsedTicks % getAnimationInterval("header") == 0) {
            headerAnimationIndex++;
        }
        if (shouldAnimate("footer") && elapsedTicks % getAnimationInterval("footer") == 0) {
            footerAnimationIndex++;
        }
    }

    private int getAnimationInterval(String section) {
        int interval = plugin.getConfig().getInt(section + ".animation.interval", 20);
        return Math.max(1, interval);
    }

    public void toggleBoard(Player player) {
        UUID uuid = player.getUniqueId();
        boolean currentlyOff = toggledOff.getOrDefault(uuid, false);
        toggledOff.put(uuid, !currentlyOff);
        if (!currentlyOff) {
            FastBoard board = boards.get(uuid);
            if (board != null) {
                board.updateLines();
            }
        } else {
            updateBoard(player);
        }
    }

    public boolean isToggledOff(Player player) {
        return toggledOff.getOrDefault(player.getUniqueId(), false);
    }

    public void reload() {
        plugin.reloadConfig();
        plugin.reloadServerRegistry();
        titleAnimationIndex = 0;
        titleRainbowTicks = 0;
        headerAnimationIndex = 0;
        footerAnimationIndex = 0;
        elapsedTicks = 0;
        plugin.rescheduleBoardUpdates();
        plugin.rescheduleVelocitySync();
        plugin.requestServerCounts();
        updateAllBoards();
    }
}
