# LastEchoNPC

A lightweight NPC mod for **Minecraft Beta 1.7.3**, built with [RetroAPI](https://github.com/periut/retroapi) and Fabric.

LastEchoNPC adds customizable human NPCs that can wander, follow players, talk, react to items, and be personalized with skins, personalities, and voices.

[![LastEcho's NPCs Showcase](https://img.youtube.com/vi/WogYXGxWTJE/maxresdefault.jpg)](https://www.youtube.com/watch?v=WogYXGxWTJE)

---

## Features

* **Human NPCs** — Fully customizable biped entities that spawn and persist in your world.
* **15+ skins** — Cycle through skins live, or add your own via a simple text file.
* **Personalities** — Multiple dialogue sets loaded from JSON; NPCs say a random line from their current personality.
* **Follow mode** — NPCs follow the nearest player when toggled.
* **Custom male & female voices** — Separate idle and hurt sounds for each sex.
* **Size scaling** — Small, medium, or large.
* **Stationary mode** — Freeze NPCs in place for shops, guards, statues, and similar builds.
* **Food healing** — Feed NPCs to restore health, with heart particles.
* **Anti-repeat dialogue** — Cooldown and no-repeat logic prevents NPCs from repeatedly saying the same line.

---

## Items & Recipes

### Human Spawner

Spawns a new NPC where you're standing.

**Recipe:**

```text
R R R
R A R
R R R
```

* `R` = Redstone Dust
* `A` = Apple

**Result:** 1 Human Spawner

### Identity Tome

Cycles the NPC's personality to the next one in the list.

**Shapeless recipe:**

* 1 Book
* 1 Feather
* 1 Lapis Lazuli

**Result:** 1 Identity Tome

### Crystal Rose

Toggles the NPC's sex, which also changes its voice.

**Shapeless recipe:**

* 1 Rose
* 1 Glass Block
* 1 Glowstone Dust

**Result:** 1 Crystal Rose

---

## Controls & Interactions

Right-click an NPC while holding one of the following items:

| Item                   | Effect                                                 |
| ---------------------- | ------------------------------------------------------ |
| **Identity Tome**      | Cycle to the next personality                          |
| **Sugar**              | Toggle follow mode; the NPC follows the nearest player |
| **Crystal Rose**       | Toggle sex; changes between male and female voices     |
| **Stick**              | Cycle skin                                             |
| **Feather**            | Toggle stationary mode; freeze or unfreeze the NPC     |
| **Slimeball**          | Cycle size: small, medium, or large                    |
| **Food**               | Heal 2 hearts and spawn heart particles                |
| **Empty hand / other** | NPC says a random line from its current personality    |

### Foods That Heal

The following foods heal NPCs in Beta 1.7.3:

| Food            | Item ID |
| --------------- | ------: |
| Apple           |   `260` |
| Bread           |   `297` |
| Cooked Porkchop |   `320` |
| Golden Apple    |   `322` |
| Mushroom Soup   |   `282` |
| Cookie          |   `357` |
| Milk            |   `335` |

---

## Customisation

### Skins

Edit:

```text
src/main/resources/data/lastecho_npc/skins.txt
```

Example:

```text
# One path per line, relative to the assets folder
assets/lastecho_npc/textures/entity/human0.png
assets/lastecho_npc/textures/entity/human1.png
assets/lastecho_npc/textures/entity/human2.png

# Lines starting with # are ignored
```

Each path should point to a valid **64×32 player-format skin**.

### Personalities

Edit:

```text
src/main/resources/data/lastecho_npc/personalities.json
```

Example:

```json
{
  "personalities": [
    {
      "name": "Friendly",
      "messages": [
        "Hello!",
        "Nice to meet you.",
        "How are you today?"
      ]
    },
    {
      "name": "Grumpy",
      "messages": [
        "Go away.",
        "I don't like you.",
        "Leave me alone."
      ]
    }
  ]
}
```

You can add up to **256 personalities** because the personality index is stored as a byte.

Each NPC selects a personality at random when it spawns and remembers that personality across saves.

### Sounds

Drop `.ogg` files into the following folders to override the default sounds:

```text
assets/lastecho_npc/sounds/sound/npc/male/idle1.ogg
assets/lastecho_npc/sounds/sound/npc/male/idle2.ogg
assets/lastecho_npc/sounds/sound/npc/male/idle3.ogg
assets/lastecho_npc/sounds/sound/npc/male/hurt1.ogg
assets/lastecho_npc/sounds/sound/npc/male/hurt2.ogg

assets/lastecho_npc/sounds/sound/npc/female/idle1.ogg
assets/lastecho_npc/sounds/sound/npc/female/idle2.ogg
assets/lastecho_npc/sounds/sound/npc/female/idle3.ogg
assets/lastecho_npc/sounds/sound/npc/female/hurt1.ogg
assets/lastecho_npc/sounds/sound/npc/female/hurt2.ogg
```

Numbered variants are grouped into a single event ID:

```text
lastecho_npc:npc.male.idle
```

The engine selects one variant at random. No additional registration is required.

---

## Installation

1. Install **Fabric Loader** for Minecraft Beta 1.7.3 through [Babric](https://babric.github.io/) or **Ornithe**.
2. Download and place [RetroAPI](https://github.com/periut/retroapi) in your `mods/` folder.
3. Place `lastecho_npc-<version>.jar` in your `mods/` folder.
4. Launch the game.

---

## Building from Source

### Requirements

* **Java 21**

### Build

Run:

```bash
./gradlew clean build
```

Build artifacts will be generated in:

```text
build/libs/
```

The directory contains:

* `lastecho_npc-<version>.jar` — Installable mod.
* `lastecho_npc-<version>-sources.jar` — Sources for IDE use.

### Run a Development Client

```bash
./gradlew runClient
```

---

## Project Structure

```text
src/main/java/com/LastEcho/lastecho_npc/
├── LastEchoNPCMod.java       # Common entrypoint, registration, recipes
├── LastEchoNPCModClient.java # Client entrypoint, entity renderer
├── HumanEntity.java          # NPC entity: AI, interactions, and data
├── HumanRenderer.java        # Applies size scaling
└── HumanSpawnItem.java       # NPC spawner item

src/main/resources/
├── assets/lastecho_npc/
│   ├── lang/en_US.lang
│   ├── textures/entity/       # Skin PNGs
│   └── textures/item/         # Item icons
└── data/lastecho_npc/
    ├── skins.txt              # Skin list
    └── personalities.json     # Personality definitions
```

---

## Requirements

* **Minecraft Beta 1.7.3**
* **Fabric Loader** for Beta 1.7.3 (Babric or Ornithe)
* [**RetroAPI**](https://github.com/periut/retroapi) **0.3.7 or newer**

---

## License

This mod is released under the **MIT License**. See [`LICENSE`](LICENSE) for details.

---

## Credits

* Built with [RetroAPI](https://github.com/periut/retroapi) by periut.
* Skin textures sourced from community skin packs.
* Thanks to the Beta 1.7.3 modding community, including **Babric**, **Ornithe**, and **StationAPI**.
