package cl.xgamers.board;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BoardExpansion extends PlaceholderExpansion {

    private final Board plugin;

    public BoardExpansion(Board plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "board";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AeroSama";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        String id = identifier.toLowerCase();

        if ("online".equals(id)) {
            return String.valueOf(Bukkit.getOnlinePlayers().size());
        }

        if (id.endsWith("_online") || id.endsWith("_connected")) {
            String serverId = id.replace("_online", "").replace("_connected", "");
            return String.valueOf(plugin.getServerCount(serverId));
        }

        if (id.endsWith("_max")) {
            String serverId = id.substring(0, id.length() - 4);
            return plugin.getServerRegistry().getMaxPlayers(serverId);
        }

        return null;
    }
}
