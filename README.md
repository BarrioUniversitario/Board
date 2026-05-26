# Board

A dynamic, animated scoreboard plugin for Paper Minecraft servers with multi-server support via Velocity proxy.

## Features

- **Animated scoreboards** — Cycle through title/header/footer frames at configurable intervals
- **Rainbow title** — HSV-based gradient animation with configurable speed, spread, saturation, and brightness
- **Multi-server player counts** — Fetches real-time online counts across Velocity servers via plugin messaging
- **PlaceholderAPI support** — Exposes `%board_<id>_online%`, `%board_<id>_connected%`, and `%board_<id>_max%` placeholders
- **Per-player toggle** — Players can show/hide their scoreboard at any time
- **Hot reload** — Reload configuration without restarting the server

## Requirements

- Paper 1.21+
- Java 21+
- PlaceholderAPI (optional)
- Velocity proxy (optional, for multi-server counts)

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/board toggle` | Show or hide your scoreboard | — |
| `/board reload` | Reload the configuration | `board.reload` |

## Placeholders

| Placeholder | Description |
|-------------|-------------|
| `%board_<id>_online%` | Online players on server `<id>` |
| `%board_<id>_connected%` | Whether server `<id>` is reachable |
| `%board_<id>_max%` | Max player slots for server `<id>` |

## Configuration

```yaml
title:
  text: "My Server"
  animation:
    enabled: true
    interval: 1        # ticks between frame changes
  rainbow:
    enabled: true
    bold: true
    speed: 14          # hue shift per frame
    spread: 30         # color gradient spread across characters

lines:
  - " &7Online: &a%board_lobby_online%"
  - " &7Max: &c%board_lobby_max%"

animations:
  enabled: true
  interval: 20         # board refresh rate in ticks

velocity:
  local-server-name: "lobby"
  sync-interval-ticks: 20

servers:
  - id: "lobby"
    velocity-names:
      - "lobby"
    max-players: 100
    local-fallback: true
```

## Building

```bash
# Linux / macOS
./mvnw package

# Windows
mvnw.cmd package
```

The compiled jar will be in `target/`.

## For developers

#### Contributing

1. Fork this repository
2. Clone your fork: `git clone https://github.com/BarrioUniversitario/Board.git`
3. Create a branch: `git switch -c feat/my-feature`
4. Make your changes and test them
5. Commit using [conventional commits](https://www.conventionalcommits.org/): `git commit -m "feat: add new animation"`
6. Push and open a pull request

We prefer conventional commit prefixes: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`.
