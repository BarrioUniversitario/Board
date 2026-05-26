package cl.xgamers.board;

import org.bukkit.Bukkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mapea placeholders del board a nombres reales en velocity.toml (como en Selector).
 */
public final class BoardServerRegistry {

    private final Board plugin;
    private final Map<String, ServerEntry> entriesById = new LinkedHashMap<>();
    private String localServerName = "";

    public BoardServerRegistry(Board plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        entriesById.clear();
        localServerName = plugin.getConfig().getString("velocity.local-server-name", "");
        if (localServerName != null) {
            localServerName = localServerName.trim();
        } else {
            localServerName = "";
        }

        List<?> serversList = plugin.getConfig().getList("servers", Collections.emptyList());
        for (Object raw : serversList) {
            if (raw instanceof Map<?, ?> map) {
                loadFromMap(map);
            }
        }
    }

    private void loadFromMap(Map<?, ?> map) {
        String id = firstNonBlank(map.get("id"), map.get("name"));
        if (id == null) {
            return;
        }
        id = id.toLowerCase(Locale.ROOT);

        List<String> velocityNames = readVelocityNames(map);
        if (velocityNames.isEmpty()) {
            velocityNames = List.of(String.valueOf(map.get("name")));
        }

        int maxPlayers = parseInt(map.get("max_players"), 0);
        boolean localFallback = !map.containsKey("local-fallback") || Boolean.parseBoolean(String.valueOf(map.get("local-fallback")));

        entriesById.put(id, new ServerEntry(id, velocityNames, maxPlayers, localFallback));
    }

    private List<String> readVelocityNames(Map<?, ?> map) {
        Object names = map.get("velocity-names");
        if (names == null) {
            names = map.get("velocity_names");
        }
        if (names instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        Object single = map.get("velocity-name");
        if (single != null) {
            return List.of(String.valueOf(single));
        }
        return Collections.emptyList();
    }

    public int getCount(String placeholderId) {
        ServerEntry entry = entriesById.get(placeholderId.toLowerCase(Locale.ROOT));
        if (entry == null) {
            return plugin.getRawServerCount(placeholderId);
        }
        return entry.resolveCount(plugin, localServerName);
    }

    public String getMaxPlayers(String placeholderId) {
        ServerEntry entry = entriesById.get(placeholderId.toLowerCase(Locale.ROOT));
        if (entry == null) {
            return "0";
        }
        return String.valueOf(entry.maxPlayers);
    }

    public Map<String, ServerEntry> getEntries() {
        return Collections.unmodifiableMap(entriesById);
    }

    private static String firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public static final class ServerEntry {
        private final String id;
        private final List<String> velocityNames;
        private final int maxPlayers;
        private final boolean localFallback;

        ServerEntry(String id, List<String> velocityNames, int maxPlayers, boolean localFallback) {
            this.id = id;
            this.velocityNames = List.copyOf(velocityNames);
            this.maxPlayers = maxPlayers;
            this.localFallback = localFallback;
        }

        int resolveCount(Board plugin, String localServerName) {
            int total = 0;
            boolean matchesLocal = false;

            for (String velocityName : velocityNames) {
                total += plugin.getRawServerCount(velocityName);
                if (!localServerName.isEmpty() && velocityName.equalsIgnoreCase(localServerName)) {
                    matchesLocal = true;
                }
            }

            if (localFallback && matchesLocal) {
                int localOnline = Bukkit.getOnlinePlayers().size();
                total = Math.max(total, localOnline);
            }

            return total;
        }

        public String getId() {
            return id;
        }

        public List<String> getVelocityNames() {
            return velocityNames;
        }
    }
}
