package cl.xgamers.board;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Integración con Velocity/Core vía canal {@value #CHANNEL}.
 * Mismo protocolo que Selector ({@code PlayerCountAll} / {@code PlayerCount}).
 */
public final class Board extends JavaPlugin implements Listener, PluginMessageListener {

    public static final String CHANNEL = "serverconnector:main";

    private BoardManager boardManager;
    private BoardServerRegistry serverRegistry;
    private BukkitTask updateTask;
    private BukkitTask syncTask;
    private final Map<String, Integer> serverCounts = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        serverRegistry = new BoardServerRegistry(this);
        boardManager = new BoardManager(this);

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("board").setExecutor(new BoardCommand(this));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new BoardExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registrado correctamente.");
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);

        for (Player player : getServer().getOnlinePlayers()) {
            boardManager.createBoard(player);
        }

        scheduleBoardUpdates();
        scheduleVelocitySync();

        if (!getServer().getOnlinePlayers().isEmpty()) {
            getServer().getScheduler().runTaskLater(this, (Runnable) this::requestServerCounts, 20L);
        }
    }

    @Override
    public void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        if (syncTask != null) {
            syncTask.cancel();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, this);
        for (Player player : getServer().getOnlinePlayers()) {
            boardManager.removeBoard(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        boardManager.createBoard(event.getPlayer());
        getServer().getScheduler().runTaskLater(this, () -> requestServerCounts(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        boardManager.removeBoard(event.getPlayer());
    }

    public BoardManager getBoardManager() {
        return boardManager;
    }

    public void scheduleBoardUpdates() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        int interval = Math.max(1, getConfig().getInt("animations.interval", 20));
        updateTask = getServer().getScheduler().runTaskTimer(this, boardManager::updateAllBoards, 0L, interval);
    }

    public void rescheduleBoardUpdates() {
        scheduleBoardUpdates();
    }

    private void scheduleVelocitySync() {
        if (syncTask != null) {
            syncTask.cancel();
        }
        int interval = Math.max(20, getConfig().getInt("velocity.sync-interval-ticks", 20));
        syncTask = getServer().getScheduler().runTaskTimer(this, (Runnable) this::requestServerCounts, interval, interval);
    }

    public void rescheduleVelocitySync() {
        scheduleVelocitySync();
    }

    public void requestServerCounts() {
        if (getServer().getOnlinePlayers().isEmpty()) {
            return;
        }
        requestServerCounts(getServer().getOnlinePlayers().iterator().next());
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%board_(\\w+?)_(?:online|connected|max)%");

    public void requestServerCounts(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Set<String> requested = new HashSet<>();

        for (BoardServerRegistry.ServerEntry entry : serverRegistry.getEntries().values()) {
            for (String velocityName : entry.getVelocityNames()) {
                if (requested.add(velocityName)) {
                    sendPlayerCountRequest(player, velocityName);
                }
            }
        }

        Set<String> configuredIds = serverRegistry.getEntries().keySet();
        List<String> allLines = new ArrayList<>();
        allLines.addAll(getConfig().getStringList("lines"));
        allLines.addAll(getConfig().getStringList("header.lines"));
        allLines.addAll(getConfig().getStringList("footer.lines"));
        for (String line : allLines) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(line);
            while (matcher.find()) {
                String id = matcher.group(1).toLowerCase(Locale.ROOT);
                if (!configuredIds.contains(id) && requested.add(id)) {
                    sendPlayerCountRequest(player, id);
                }
            }
        }

        sendPlayerCountAllRequest(player);
    }

    private void sendPlayerCountRequest(Player player, String serverName) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeUTF("PlayerCount");
            out.writeUTF(serverName);
            player.sendPluginMessage(this, CHANNEL, buffer.toByteArray());
        } catch (IOException e) {
            getLogger().warning("Error enviando PlayerCount a Velocity: " + e.getMessage());
        }
    }

    private void sendPlayerCountAllRequest(Player player) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeUTF("PlayerCountAll");
            player.sendPluginMessage(this, CHANNEL, buffer.toByteArray());
        } catch (IOException e) {
            getLogger().warning("Error enviando PlayerCountAll a Velocity: " + e.getMessage());
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String subChannel = in.readUTF();

            boolean updated = false;
            if ("PlayerCount".equals(subChannel)) {
                serverCounts.put(in.readUTF(), in.readInt());
                updated = true;
            } else if ("PlayerCountAll".equals(subChannel)) {
                if (in.available() >= 4) {
                    in.mark(0);
                    int possibleCount = in.readInt();
                    if (possibleCount > 0 && possibleCount < 1024) {
                        for (int i = 0; i < possibleCount && in.available() > 0; i++) {
                            serverCounts.put(in.readUTF(), in.readInt());
                        }
                    } else {
                        in.reset();
                        while (in.available() > 0) {
                            serverCounts.put(in.readUTF(), in.readInt());
                        }
                    }
                }
                updated = true;
            } else {
                getLogger().fine("Subcanal no manejado: " + subChannel);
            }
            if (updated) {
                if (getConfig().getBoolean("velocity.debug", false)) {
                    getLogger().info("Conteos Velocity: " + serverCounts);
                }
                boardManager.updateAllBoards();
            }
        } catch (IOException e) {
            getLogger().warning("Error leyendo mensaje de Velocity: " + e.getMessage());
        }
    }

    public BoardServerRegistry getServerRegistry() {
        return serverRegistry;
    }

    public void reloadServerRegistry() {
        serverRegistry.reload();
    }

    /** Conteo crudo recibido de Velocity (nombre exacto del proxy). */
    public int getRawServerCount(String velocityName) {
        if (velocityName == null) {
            return 0;
        }
        Integer count = serverCounts.get(velocityName);
        if (count != null) {
            return count;
        }
        for (Map.Entry<String, Integer> entry : serverCounts.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(velocityName)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    /** Conteo para placeholders (%board_&lt;id&gt;_online%). */
    public int getServerCount(String placeholderId) {
        return serverRegistry.getCount(placeholderId);
    }
}
