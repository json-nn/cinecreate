# Cinematic Interactions: Create — research & design

`cinecreate` · NeoForge 1.21.1 · Create 6.0.10 · Cinematic Interactions 0.18.12 (add-on API preview)
Written 2026-10-20. Research sources are listed at the end.

**Contents**

1. [Summary](#1-summary)
2. [What Cinematic Interactions is](#2-what-cinematic-interactions-is)
3. [How players received it](#3-how-players-received-it)
4. [How it works: the principles behind the visuals](#4-how-it-works-the-principles-behind-the-visuals)
5. [The add-on API, and what it means for Create](#5-the-add-on-api-and-what-it-means-for-create)
6. [Create blocks with Cinematic-style interactions](#6-create-blocks-with-cinematic-style-interactions)
7. [Animation specs](#7-animation-specs)
8. [Shared look: FX, sound, feedback](#8-shared-look-fx-sound-feedback)
9. [Milestones](#9-milestones)
10. [Risks and open questions](#10-risks-and-open-questions)
11. [Sources](#11-sources)

---

## 1. Summary

- **Cinematic Interactions** (CI, mod id `cinematic`, by Southware, MIT) turns 19 vanilla workstations into first-person scenes: the player walks up, the camera leans in, the player's own skinned arms do the work in the world, and the real vanilla menu does the logic underneath. It is a week old and spreading fast: 30.5k CurseForge downloads, and a single TikTok about it has 155k likes.
- Someone asked for Create support on CI's roadmap issue. The author answered that they have plans for Iron's Spells, *"but idk what i can do with create"*. Nobody has claimed this niche yet.
- CI ships a **NeoForge 1.21.1 add-on API** (`AddonStations`, `AddonScenes`, `AddonMenuStation`). It only covers **menu-backed** blocks, and it opens menus via `getMenuProvider()` with no extra data. No Create block passes both checks: most Create blocks have no menu, and the ones that do need extra open data. The add-on therefore uses **bridge menus** (a windowless server menu over each Create block's real inventory and actions), plus **three small mixins into CI** as a stopgap. We should also offer CI an upstream PR that makes those hooks official.
- **Design rule for Create, taken from the reviews:** *machines keep time; the scene listens.* The player's own gestures (put in, take out, turn) land on the beat, as in CI. Machine work (pressing, mixing, crafting) stays Create's; the scene reads the block entity's client-synced state and decorates it. It never simulates the process.
- The most-liked comment says the scenes get repetitive after 20 minutes. So high-frequency logistics blocks start on a key (like CI's campfire **H**), and workstation-like blocks start on use. Players can change this per block.
- **Wave 1** (proves the architecture): Depot, Depot + Press (pressing), Valve Handle, Mechanical Crafter. Waves 2–3 add 20+ more blocks (see §6).

---

## 2. What Cinematic Interactions is

| | |
|---|---|
| Author / license | Southware (GitHub `dcvke`), solo project. Code MIT; the imported "Just a Hammer" model is CC BY 4.0 (RDiamonds) |
| Age / traction | Released 2026-10-12. CurseForge: 30.5k downloads, 272 followers, 146 comments (2026-10-20) |
| Loaders | NeoForge 1.21.1 (reference implementation, root project); Fabric 1.21–1.21.11 and 26.1.1–26.3; Forge 1.20.1 beta |
| Install | Both client and server need it. Since 2026-10-19 there is also an optional **client-only preview** for selected stations on servers without the mod |
| Release pace | Several builds a day (0.18.1 → 0.18.12 in four days) |

**Stations (19 block variants):** crafting table, furnace, smoker, blast furnace, crafter · anvil, grindstone, smithing table, stonecutter · enchanting table, brewing stand · loom, cartography table · lectern, chiseled bookshelf, jukebox · campfire, soul campfire, beacon. Chests, barrels and shulker boxes keep their normal screens.

**Controls:** right-click a station to start; left/right-click to work; Esc, E, any movement key or jump to leave; sneak + right-click gives the vanilla behaviour; **H** sits you down at a campfire; hold **Left Alt** to orbit the camera around your character; right-click a highlighted neighbouring station to move over to it without leaving.

**Comfort settings:** a toggle per station, entrance/exit speed 50–200 %, lean and zoom 0–100 %, quick return (5–30 s window, default 12 s), and separate toggles for cinematic bars, idle camera motion, shake and flashes. Developer Mode adds in-world layout editors with three local profiles per station.

---

## 3. How players received it

Two TikTok reviews, the CurseForge page and the GitHub tracker, read 2026-10-20:

| Source | Reach | What people said |
|---|---|---|
| StudiousGoose, "The Best Cinematic Minecraft Mod" (1:13) | 155.6k likes · 70.4k saves · 19.8k shares · 1.2k comments | Top comment (5.1k likes): *"It's fun for about 20 minutes and then it gets repetitive."* Then: VR (1.3k likes), "Minecraft Story Mode" (many), story/adventure maps (1k), horror modpacks (182), "creeper jackpot", "holy inefficiency", "stuck in the furnace". The mod author replied in the thread |
| Masi Main, "Minecraft Cinematic Interactions Mod" (0:59) | 71.3k views · 5.4k likes · 2.2k saves | "Bedrock?" (most liked), "is it client-side?", "does it work on servers?", "1.20.1 / Forge please", "cool yes, efficient no", "a creeper blew me up first, especially in a cave", a chest glitch report |
| GitHub issues | 10 issues | Roadmap (23 comments), compat requests (Punchy, Inventory Particles/Interactions, Penchant Reforged crash), "make an addon possibility", **"Create & Iron's Spells support?" → author: plans for Iron's Spells, unsure what to do with Create** |

**What this means for the add-on:**

| Feedback | Design rule |
|---|---|
| Repetitive after a while | Default **KEY** start for blocks used dozens of times an hour (bare depot, funnel, chute, hatch). Short scenes for one-shot actions. Keep CI's quick return. Never make a scene slower than the vanilla click it replaces |
| Inefficient | A scene must never be *needed*: sneak, the per-block toggle, or a failed reach check all fall back to Create's normal use, instantly |
| Story mode / VR / horror | The audience wants immersion and spectacle. Spend the effort on machine moments (press impact, mixer swirl, valve strain), not on menus |
| "Is it client-side?" | We can't be client-only: the API and the bridge menus need the server. Say so in the first line of the mod page |
| Creepers mid-scene | CI already steps away when the player is hurt or starts falling. Long watch scenes (mixing, crafting) must show a "stop watching" hint |
| Shader / compat bugs | Draw with CI's own helpers (`Fx`, `ItemPlacer`, `Hands`) so its shader-depth and inventory-effects fixes carry over |

---

## 4. How it works: the principles behind the visuals

Read from the NeoForge source (`src/main/java/dev/dyslexic/cinematic`, commit `c926f72`, 2026-10-20).

### 4.1 Scene lifecycle (`client/Scene`)

```
use block ─► Reach.plan ─┬─ scene possible ─► INTRO: walk ─► lean (1.2 s) ─► ACTIVE (SceneScreen, drawer, hands)
                         │                                                        │ Esc / E / move / jump / hurt / fall
                         └─ not possible ─► block's own window (or a notice)       ▼
                                                                   OUTRO (1.05 s): glide back to the eyes + step back
```

- **The walk is real.** CI injects movement input so the player physically walks to the stand point (≈ 0.86 blocks out from the block centre). It never teleports. If the walk stalls for 0.7 s, the scene falls back to the window.
- The **lean** starts only once the player has arrived. It takes 1.2 s (`inOutCubic`), with a small vertical hump (+0.045) and a little roll proportional to the turn. A **quick return** within 12 s of leaving uses a 0.4 s lean and no bars.
- Leaving takes **1.05 s**: the camera glides back to wherever the eyes are by then, and the body takes a short step back (impulse −0.45 for 0.2–0.6 s) if there is ground behind.
- The body keeps facing the station (`Scene.steer`), so other players see a believable working pose.
- **Moving over:** while working, right-clicking another station in reach (4.5 blocks) moves to it without the full outro and intro. The hands lift and settle; the camera turns.

### 4.2 Reach and fallback (`client/Reach`)

Each station has *working sides*, a *work point*, and sample points that must be in view (`workSamples`). CI tries the nearest side, then other sides, then standing beside the work (if `worksAtAnAngle`). If none works, the block's own window opens; with "height logic" off, a notice says why. **Every scene has a vanilla escape hatch.**

### 4.3 Camera (`client/CameraRig`)

| Phase | Behaviour |
|---|---|
| Lean pose | `eye = work + side·d + up·h` and `look = work + small offset`. Typical values: d 0.66–1.2, h 0.2–1.0, roll −2…+1.5°, FOV 64–68 (anvil 66, grindstone/jukebox 64, campfire 68, beacon 60→68 while pulling back) |
| Breathing | Position bobs 3.5 mm at 1.3 rad/s; yaw ±0.16°, pitch ±0.13° on slow sines |
| Parallax | Once working, the camera drifts toward the cursor: up to 1.1° yaw and 0.8° pitch, with exponential approach rate 4 |
| Kick | `cameraKick()` 0..1 shakes yaw/pitch/roll (±0.18°·k) and punches FOV by −7°·k. The anvil uses `0.5·e^(−13·t)` after each blow |
| Flash | `flash()` 0..1 full-screen tint in `flashColor()`. The anvil uses `0.11·e^(−16·t)` in 0xFFE6B8 |
| Bars | Letterbox at 8.5 % of screen height, only while the camera travels (not during quick returns or moving over) |
| Tether | The camera never leaves what the head could reach from where the player really stands (`Reach.cameraPose`) |

The HUD is hidden during a scene, except chat, subtitles, the debug overlay and the tab list.

### 4.4 Hands (`client/Hands`, `ArmMesh`)

- The **player's own arms, with their skin** (sleeves too), drawn in the world (scale 0.55). They rise from below the frame at the start and sink out of it at the end.
- Each hand is a **critically damped spring** (`Anim.Spring3`, stiffness ≈ 20). The hand nearer the cursor does the work while the other rests. Stations can override both hands for a special move (`handsOverride`), and `maxReach` stops a hand short instead of stretching the arm.
- A held item hangs off the fist (`heldItemPoint`, tilt 18°). Props (the hammer) come from a custom model.

### 4.5 Items (`client/ItemPlacer`)

Items lie flat on surfaces (flat items 0.205, blocks 0.15). When the player steps away, items left on a menu that returns them fly back in an arc and shrink into the player (`drawLeaving`, staggered 0.07 s apart). An item in hand is the menu's carried stack.

### 4.6 One big move, with the server action on the beat (`client/MenuStation`)

Each station has **one signature move** played on a timeline: `startMove(length, beat, action)`. The real server action (a slot click) is sent **on the beat**, the frame the hammer lands or the blade bites, so the server's sound, the XP and the picture coincide.

- Anvil: 2.05 s move, blows at 0.70 / 1.02 / 1.30 s, click on the first blow.
- Grindstone: 2.4 s move, blade contact at 0.6 s.
- Stonecutter: 1.05 s cut (1.5 s for a whole stack), blade bite at 0.26 s.
- Jukebox: insert 1.3 s, eject 1.15 s.

Refusals get `deny()`: a 0.35 s sideways shake plus `CHAIN_HIT`.

### 4.7 Authority

The scene sends the same slot clicks and button clicks a container screen would (`slotClick`, `clickMenuButton`), so recipes, costs, rewards and other mods' hooks stay vanilla's. *"Do not duplicate recipes or invent client-side results"* (API guide). Stations without menus (table, furnace, jukebox, bookshelf, campfire) have their own server actions (`StationActionPayload`).

### 4.8 Multiplayer

- `AtStationPayload` tells nearby players who is at which station, so they see the pose.
- `MenuDisplayPayload` shows what lies on menu stations.
- `MenuFxPayload` replays the big moment for onlookers.

Full observer animation exists for the anvil, grindstone, stonecutter and bookshelf only. The add-on API does **not** expose observer FX yet.

### 4.9 Feedback language

- A soft glow disc under the cursor (warm when the item fits, dim red when it doesn't), and face patches on vertical work faces.
- A hint line of controls at the bottom of the screen; tooltips for what's under the cursor.
- An inventory drawer of glass at the top of the frame: the hotbar always, the rest when the cursor goes up there.
- The item's name floats over it (anvil); choices hang in arcs around the work (stonecutter dial, enchanting runes, beacon crown).

### 4.10 The CI style, distilled into rules for our scenes

1. **Physical, never teleported.** Walk up, lean in, step back.
2. **The world is the UI.** Items lie on the block where they really are, and choices hang around the work.
3. **Your hands, your skin.** Two arms, springs, the nearer hand works, and props only for signature moves.
4. **One signature move per station**, timed so the real action lands on its beat.
5. **Small camera, big moments.** It breathes all the time and kicks and flashes only at impacts. Every effect has an off switch.
6. **Vanilla stays the authority.** Real menus and real clicks; the scene never invents a result.
7. **Always an exit.** Sneak, a toggle or a failed reach check gives the normal behaviour, at once.
8. **Readable at a glance.** Warm glow means yes, a head-shake means no, and one hint line.
9. **Describe the scene as a storyboard.** Every CI station class opens with a short prose storyboard. We do the same (§7).
10. **Numbers in seconds, eased.** `inOutCubic` for travel, `outBack` for settling, `e^(−k·t)` for kicks and flashes, `hump` for arcs.

---

## 5. The add-on API, and what it means for Create

### 5.1 The API (0.18.12 preview)

```java
AddonStations.register(new AddonStations.Definition(sceneId, blockId, MenuClass.class, state -> List<Direction> sides));  // both sides
AddonScenes.register(sceneId, (pos, state, player, side) -> new MyScene(pos, side));                                   // client
class MyScene extends AddonMenuStation<MenuClass> { ... }   // = MenuStation with kind() == ADDON
```

Once a block is registered, CI takes care of the use interception, Reach, the walk/lean/outro, the drawer, quick-move, the session lifetime, the window fallback, moving over, and the observer pose. The add-on supplies the scene (camera, rests, cursor, clicks, drawing). Only non-`minecraft` blocks may register, one definition per block. The API ships inside the CI jar (`libs/` in this project); declare `cinematic` as required on both sides.

### 5.2 Why Create blocks don't fit as-is

| Constraint in CI 0.18.12 | Effect on Create |
|---|---|
| Server opens the scene menu via `state.getMenuProvider(level, pos)` (`MenuActions.provider`) | Create blocks don't override `getMenuProvider`, so it returns `null` and the session closes. Most Create blocks (depot, press, crafter, valve, basin, burner…) have **no menu at all** |
| `player.openMenu(provider)` without an extra-data writer | Create's own menus (`MenuBase`: toolbox, schematic table, schematicannon, stock ticker…) read the block position from extra data and would crash on the client with a null buffer |
| Window fallback (`Sessions.openWindow`) opens the provider's menu | For menu-less blocks the fallback should be *Create's normal right-click*, not a window |
| `startsOnUse()` is always true for `ADDON` and CI cancels every non-sneak right-click | That would swallow Create's own item interactions on these blocks: the **wrench**, goggles on a burner, dyes on valves/toolboxes/seats, crafter slot covers, the mechanical arm, value-box/filter-slot clicks. There is also no way to choose KEY vs USE per block |
| Per-station toggles skip `ADDON` | The add-on needs its own toggles |
| No menu-less stations, no observer FX, no layout-editor profiles for add-ons (stated in the guide) | Known limits; we work around the first and accept the other two |
| Package-private helpers (`Scene.sound`, `MenuStation.frame`, many `Fx`/station statics) | We keep tiny local equivalents |

A client-side detail matters too. A menu needs a **registered screen**, or `MenuScreens` never assigns it to `player.containerMenu`, and CI's "keep the menu, cancel the window" path never fires. So every bridge menu type gets a simple fallback screen.

### 5.3 Integration architecture

```
                 ┌──────────────── server ─────────────────┐            ┌────────── client ──────────┐
right-click ──►  CI StationEvents ──(mixin 3: use rules)──► CI Sessions.begin ──(mixin 1: opener)──► BridgeMenu ◄─sync─► BridgeMenu
                    │ bypass: wrench, dye, goggles,              │                                  ▲ slots = Create IItemHandler     │
                    │ value-box hits, policy≠USE                 │ fallback (mixin 2):              │ buttons = Create actions        ▼
                    ▼                                            ▼ Create's normal use              │                      ScenesX extends
               Create's normal use                       (synthetic hit per block)          clickMenuButton(id) ◄──── AddonMenuStation<BridgeMenu>
```

**Bridge menus** (`bridge/`, server and common):

- One `MenuType` per block family: `depot`, `crafter`, `valve`, `crank`, `basin`, `burner`, `saw`, `millstone`, `deployer`, `ejector`, `drain`. Each is an `AbstractContainerMenu` whose machine slots **delegate to the block entity's own `IItemHandler`** (the same capability Create's funnels and arms use). That way Create's acceptance rules, stack limits and recipes apply untouched.
- The **player's 36 inventory slots** are included, so CI's quick-move (`menu.clicked(…, QUICK_MOVE)`) works.
- **Actions** that aren't slots are `clickMenuButton(id)` (the vanilla button packet, as the stonecutter and loom use): turn the valve forward/back or set its angle, crank, set the saw's filter, toggle a crafter slot cover, pour the carried fluid container, ignite the burner.
- They are opened **with extra data** (`pos` + layout size) by our opener, so client and server agree on the slot count (a crafter group can be any size).
- `stillValid`: the block is unchanged and within 6 blocks, matching CI's reach.
- Create's **native menus** (toolbox, schematic table, schematicannon, stock ticker, redstone requester, package ports) are opened as Create opens them (`openMenu(be, be::sendToMenu)`), and their `Definition` uses Create's menu class.

**Mixins into CI.** Each is `@Pseudo`, version-gated to `[0.18.12, 0.19)`, and fails soft: if one doesn't apply, our blocks keep Create's behaviour and we log one warning.

| # | Target | Purpose |
|---|---|---|
| 1 | `station.MenuActions#open(ServerPlayer, Session)` | For our blocks, open the bridge or native menu with extra data |
| 2 | `station.Sessions#openWindow(ServerPlayer, BlockPos, StationKind, int)` | For our blocks, the "window" is Create's normal use (`useItemOn`, then `useWithoutItem`, with a synthetic hit on the block's working face) |
| 3 | `station.StationEvents#onRightClickBlock` | **Use rules**: leave the click to Create when the held item has its own Create interaction (wrench, goggles, dye, slot cover, arm, belt connector, super glue, clipboard, filters), when the hit lands on a Create value box or filter slot, or when the player's trigger policy for this block isn't USE |

**Upstream proposal** (PR to `dcvke/CinematicInteractions`, which welcomes contributions): extend `AddonStations.Definition` with optional `opener`, `vanillaUse`, `startsOnUse(player, state, hit)` and per-add-on toggles in its settings screen. That retires all three mixins and helps every future add-on. Offer it once Wave 1 works.

**Trigger policy:** USE, KEY or OFF per block, with defaults in §6. It is a client preference, sent to the server in a tiny `cinecreate:scene_prefs` payload at login and whenever it changes, so both sides make the same decision in mixin 3. In KEY mode a right-click is pure Create, and the scene starts on our key. The key defaults to **H**, deliberately the same as CI's campfire key ("H = do this cinematically"); it only fires on our blocks. Like CI's campfire, a small "[H] Work at the depot" invitation shows under the crosshair (it can be turned off).

**Machines keep time.** Create machine work is never driven by the scene. Scenes read client-synced block-entity state for their beats:

| Machine | Client field used as the beat |
|---|---|
| Press | `PressingBehaviour.runningTicks` crossing `CYCLE/2` (= 120) is the impact; `getRenderedHeadOffset(pt)` drives the hands' flinch |
| Mixer | `running`, `runningTicks`, `getRenderedHeadOffset` |
| Crafter | `phase` (IDLE → ACCEPTING → ASSEMBLING → CRAFTING → EXPORTING) |
| Valve / crank | `getIndependentAngle(pt)`, `inUse`, `totalUseTicks` |
| Saw / millstone | processing inventory `remainingTime` against the recipe duration |
| Ejector | launcher state and the target trajectory |
| Burner | `HEAT_LEVEL` and the remaining fuel time |

Player gestures (put in, take out, turn, toss) use CI's beat pattern: the bridge click is sent on the gesture's contact frame.

### 5.4 Package layout (for the implementation milestones)

```
org.minechestplate.cinecreate
├─ CineCreate / CineCreateClient     entry points (already in the scaffold)
├─ registry/   MenuTypes, Stations (AddonStations + AddonScenes registration, dyed variants looped)
├─ bridge/     BridgeMenu + per-family menus, BridgeOpener, VanillaUse, UseRules, ScenePrefs(+payload)
├─ mixin/      CI hooks 1–3
├─ client/     scenes/ (one ClientStation per scene), fx/ (CreateFx: brass sparks, steam, wind), keys, config
└─ compat/     detection of composites (press/mixer/deployer/spout/fan over or around a block)
```

---

## 6. Create blocks with Cinematic-style interactions

The blocks are grouped by **CI's six activity categories**, matched by interaction type, plus two categories CI doesn't have and Create needs: *kinetic controls* and *logistics*.

- **Scene types:** **Station** (stay and work), **Short** (one action, then step away by yourself, like CI's jukebox), **Composite** (the scene of a base block changes with the machine above or around it), **Watch** (load, then watch the machine work).
- **Bridge:** **I** = item bridge (slots over the block's `IItemHandler`), **A** = action bridge (buttons), **N** = Create's native menu.
- **Trigger** is the default policy.
- **Wave** is the priority: 1 now, 2 next, 3 later. ✗ means not worth a scene.

### 6.1 Crafting & smelting  *(CI: crafting table, furnaces, crafter)*

| Block (id) | Create interaction today | CI analog | Scene | Bridge | Trigger | Wave |
|---|---|---|---|---|---|---|
| Mechanical Crafter `mechanical_crafter` | Insert 1 item per crafter face; empty hand takes it; slot cover; wrench to connect | Crafter (9 windows on a face) | Station + Watch | I + A (cover) | USE | **1** |
| Blaze Burner `blaze_burner` | Feed fuel (kindled / seething with blaze cake); flint & steel to light an empty burner; goggles | Furnace fuel opening | Short | I + A (ignite) | USE | 2 |
| Millstone `millstone` | Items go in at the top; empty hand takes outputs (or inputs if no output) | Furnace (in, cook, collect) | Station + Watch | I | USE | 2 |
| Depot under an **Encased Fan** stream (`encased_fan`) — bulk washing / smoking / blasting / haunting | Items in the air current get processed | Smoker / blast furnace | Composite of Depot | I | USE | 2 |
| Crushing Wheels `crushing_wheel` | Throw items in between | Furnace | — | — | — | ✗ (no block to work at; items are thrown) |

### 6.2 Tools & equipment  *(CI: anvil, grindstone, smithing table, stonecutter)*

| Block (id) | Create interaction today | CI analog | Scene | Bridge | Trigger | Wave |
|---|---|---|---|---|---|---|
| **Mechanical Press** `mechanical_press` over a Depot / Basin | Presses what's below (plates; compacting in a basin) | **Anvil** (hammer blows) | Composite of Depot (and Basin); using the press itself starts the same scene | I (the block below) | USE | **1** |
| Mechanical Saw `mechanical_saw`, facing up | Items on it are cut (cutting includes stonecutting recipes); a filter slot picks the output; empty hand takes outputs | **Stonecutter** (dial of shapes) | Station + Watch | I + A (set filter) | USE | 2 |
| Deployer `deployer` | Swap the held item with the deployer's hand. Over a depot it applies items (item application, sequenced assembly) | **Smithing table** (apply one thing onto another) | Short (hand-off) + Composite of Depot ("Application") | I | USE | 2 |
| Toolbox `*_toolbox` (16 colours) | Native menu: 8 compartments; dye recolours | Chiseled bookshelf (draw out by the slot) | Station | N | USE | 3 |
| Backtank `copper_backtank`, `netherite_backtank` | Use with an empty chest slot to put it on | — | Short (shoulder it) | A | KEY | 3 |

### 6.3 Enchanting & brewing  *(CI: enchanting table, brewing stand)*

| Block (id) | Create interaction today | CI analog | Scene | Bridge | Trigger | Wave |
|---|---|---|---|---|---|---|
| **Basin** `basin` (+ Mixer `mechanical_mixer` or Press above, Blaze Burner below) | Insert items; fluid containers exchange fluid; empty hand takes everything; heat from the burner | **Brewing stand** (vessel, ingredients, heat, watch it brew; Create mixes potions here) | Station + Watch + Composite | I + A (pour) | USE | 2 |
| Item Drain `item_drain` | Lay a filled container; it drains; empty hand takes it | Brewing (bottles) | Short + Watch | I | USE | 2 |
| Depot under a **Spout** `spout` | The spout fills containers on the depot | Brewing stand bottles | Composite of Depot ("Filling") | I | USE | 2 |
| Blaze Burner + Blaze Cake (superheat) | — | Enchanting "ritual" flare | covered by the Blaze Burner scene | | | 2 |

### 6.4 Banners & maps  *(CI: loom, cartography table)* — drafting and printing

| Block (id) | Create interaction today | CI analog | Scene | Bridge | Trigger | Wave |
|---|---|---|---|---|---|---|
| Schematic Table `schematic_table` | Native menu: write a schematic file onto an empty schematic | Cartography table (copy a map) | Station | N | USE | 3 |
| Schematicannon `schematicannon` | Native menu: schematic, checklist book, gunpowder; start/stop printing | Beacon payment + burst | Station + Watch | N | USE | 3 |
| Table Cloth `*_table_cloth` | Lay items for display; with a stock ticker it becomes a shop counter | Crafting-table top (items lie on it) | Station | I (cloth BE) | USE | 3 |
| Placard `placard` | Put an item in the frame; matching items light it | Item frame | — | — | — | ✗ (vanilla click is already ideal) |

### 6.5 Books & music  *(CI: lectern, chiseled bookshelf, jukebox)*

| Block (id) | Create interaction today | CI analog | Scene | Bridge | Trigger | Wave |
|---|---|---|---|---|---|---|
| Lectern Controller `lectern_controller` | Stand at it and use the linked controller (movement keys send redstone links) | **Lectern** | Station (hands on the console) | A | USE | 3 |
| Clipboard (placed) `clipboard` | Opens Create's client screen (lists, checkboxes) | Lectern / open book | Short: pick it up, lean over, then hand off to Create's screen | — (client screen) | USE | 3 |
| Desk Bell `desk_bell` | Ding (redstone pulse) | Jukebox (short music scene) | Short | A | KEY | 3 |
| Steam Whistle `steam_whistle` | Plays when powered; adding whistles extends its pitch range | Jukebox | — | — | — | ✗ (the interaction is redstone, not hands) |
| Peculiar / Haunted Bell | Ring | — | — | — | — | ✗ |

### 6.6 Rest & effects  *(CI: campfire, beacon)*

| Block (id) | Create interaction today | CI analog | Scene | Bridge | Trigger | Wave |
|---|---|---|---|---|---|---|
| Seat `*_seat` (16 colours) | Sit (rides a seat entity); dye recolours | **Campfire sitting** (H) | Short: sit-down camera, legs in frame, then plain riding | — | KEY | 3 |

### 6.7 Kinetic controls  *(Create only: turning things by hand)*

| Block (id) | Create interaction today | CI analog | Scene | Bridge | Trigger | Wave |
|---|---|---|---|---|---|---|
| **Valve Handle** `copper_valve_handle` + 16 dyed `*_valve_handle` | Each use rotates by a set angle (−180…180°, default 45°; set on its value box); sneak reverses; dye recolours | Grindstone wheel / loom shuttle (a turning gesture) | Station | A (turn ±, set angle) | USE | **1** |
| Hand Crank `hand_crank` | Hold use to turn it (32 RPM); sneak reverses; costs hunger | Grindstone | Station | A (crank ±) | USE | 2 (M6) |
| Analog Lever `analog_lever` | Each use +1 signal (sneak −1), 0–15 | — | Short | A | KEY | 3 |
| Contraption Controls `contraption_controls` | Toggle a contraption's actors | — | — | — | — | ✗ |

### 6.8 Logistics  *(Create only: handing items to machines)*

| Block (id) | Create interaction today | CI analog | Scene | Bridge | Trigger | Wave |
|---|---|---|---|---|---|---|
| **Depot** `depot` | Use on the top face: put the held stack down; take what's there and its outputs | Jukebox top / table top | Short (bare); **Station** when a press / deployer / spout / fan works on it | I | **KEY bare / USE with a machine** | **1** |
| Weighted Ejector `weighted_ejector` | Same as the depot; launches items at a wrench-selected target | Beacon (camera pulls back for the big moment) | Short + Watch (camera follows the arc) | I | USE | 2 |
| Packager `packager`, Postbox `*_postbox`, Frogport `package_frogport` | Unwrap a package by hand; package-port menus | Jukebox (insert / eject) | Short | I / N | KEY | 3 |
| Stock Ticker `stock_ticker`, Redstone Requester `redstone_requester` | Native request menus | — | — | N | — | 3 (only if the shop counter needs it) |
| Item Hatch `item_hatch` | Dump the hotbar (or the rest of the inventory, sneaking) into the vault behind it | Furnace opening | Short | A | KEY | 3 |
| Funnels, Chutes, Belts | Insert / take | — | — | — | — | ✗ (pure throughput: a scene only slows play) |

---

## 7. Animation specs

**Conventions**

- `W` is the work point.
- `S` is the unit vector from the block toward the player (`side()`), `R` is the player's right, and `Y` is up.
- Times are seconds at 100 % speed.
- Easing names are CI's `Anim` functions.
- *Kick* and *flash* follow CI's `e^(−k·t)` form.
- "Beat" is the frame the bridge click or button is sent.
- Every scene also gets CI's free features: the drawer, quick-move, the hint line, deny, leaving, moving over, orbit view, and the comfort toggles.
- Hover feedback is CI's glow disc on surfaces and face patch on faces (warm = accepted, dim red = refused).

### 7.1 Depot — the base scene  *(Wave 1)*

> *The player steps up and leans over the depot. The stack in hand is carried over the plate and set down flat; it slides the last pixel with the depot's own slide sound. Touching what lies there lifts it by its edge back into the hand, together with any outputs. When a machine works on this depot, the scene becomes that machine's (7.2, 7.10, 7.11, 7.13).*

| Staging | |
|---|---|
| Sides | Any horizontal side (nearest free) |
| Stand | 0.86 from the centre (CI's anvil value) |
| Work point | Depot top: `pos + (0.5, 13/16, 0.5)` |
| Camera | `eye = W + S·0.72 + Y·0.62`; look at `W + S·(−0.04) + Y·0.06`; roll 0; FOV 64; body pitch 45 |
| Hand rests | `W + S·0.34 ± R·0.30 + Y·0.06` (hands on the near rim) |
| Controls | LMB on the plate: put down the carried stack, or pick up · Shift+LMB on the item: straight into the inventory · drawer shift-click: quick-move onto the depot |

| Set down (0.55 s) | | |
|---|---|---|
| 0.00–0.20 | carried stack rises 0.06 over the plate, tilt 18° → 0° | `outCubic` |
| 0.20–0.38 | lowered to the plate | `inOutCubic` |
| **0.38** | **beat**: slot click (server places it and plays `depot_slide`) · 6 grey dust motes | |
| 0.38–0.55 | hand back to rest | spring |

| Take (0.45 s) | | |
|---|---|---|
| 0.00–0.18 | hand to the item's edge | spring |
| **0.20** | **beat**: pickup click · item rises 0.10 with the hand (Shift: flies to the drawer and shrinks, 0.35 s, `inOutCubic` + `hump` 0.14) | |

- **Bare-depot mode:** short scene. The player steps away by themselves 0.55 s after the beat, like CI's jukebox.
- **Observers:** Create renders the depot item for everyone; CI shows the pose.

### 7.2 Pressing — Depot / Basin under a Mechanical Press  *(Wave 1, the anvil of Create)*

> *Under the press, the depot is the anvil. The player lays the ingot on the plate and keeps the hands on the rim. The press head comes down by itself — Create drives it — and each time it lands the scene answers: a jolt through the camera, a ring of hot brass sparks from under the head, a short warm flash, and the ingot is a sheet. The hand slides the sheet off. Without rotation the head never moves: the hint says the press needs power, and the fingers drum on the casing.*

| Staging | |
|---|---|
| Sides | The depot's sides; using the press itself targets the block below |
| Camera | Pulled back to frame both the plate and the head: `eye = W + S·1.05 + Y·0.55`; look at `W + Y·0.45`; roll −1.5°; FOV 66; body pitch 30 |
| Hand rests | Rim, `±R·0.34`. When the head starts down (`headOffset > 0.05`) both hands **flinch back** along `+S` by 0.12 in 0.22 s (`inOutCubic`) and return after impact |

**Impact** (each time `runningTicks` crosses 120):

- `cameraKick = 0.45·e^(−13·t)`; `flash = 0.10·e^(−16·t)`, colour 0xFFE6B8.
- `Fx.blowSparks(plate, 28, 2.2)` + `Fx.hotSparks(plate, −S + Y·0.7, 0.5, 10, 2.4)`.
- Sound: Create's `mechanical_press_activation` (server), plus a local `ANVIL_PLACE` at volume 0.2, pitch 1.7.

**Result** (the depot stack changes): 10 warm motes (1.0, 0.75, 0.4) rise 0.2 off the new item, and a chime (`AMETHYST_BLOCK_CHIME` 0.4 / 1.3).

**Speed limits:** at most one kick per 0.35 s. FX strength scales with `min(1, cycleSeconds / 1.0)`, so 256 RPM stays readable. Below 1 RPM: deny + hint.

**Basin variant (compacting):** same impact, but sparks become a puff of compressed dust (or a splash with fluid in the basin) and there is no flash.

### 7.3 Mechanical Crafter  *(Wave 1)*

> *The crafters stand as a wall of windows. The camera steps back until the whole connected group is in frame. Each window takes one item: the hand carries it in and pushes it into the slot until it clicks. Windows out of arm's reach are filled with a short toss. A covered window shows its slate; a slot cover in hand covers an empty one. Arrows on each face glow faintly in the direction items will travel. When every window is full and rotation arrives, the camera draws back a little more and watches: items hop crafter to crafter, the last one flashes, and the result pops out.*

| Staging | |
|---|---|
| Sides | The crafters' front (`HORIZONTAL_FACING`) only |
| Work point | Centre of the group: the wall the clicked crafter stands in (same facing, touching in the plane of the fronts), up to 10×10 (5×5 in M5; the user's note after M7: Create builds crafters up to 10×10); a bigger wall shrinks to the clicked crafter's recipe chain (`RecipeGridHandler`), and bigger still means no scene. **By hand only up to 3×3, from its bottom row up to 1.5 above the eyes; anything bigger or higher needs an Extendo Grip** (its card says so) |
| Camera | Always the player's own eyes (after M7, the user's third round: big walls framed from a camera floating in front of them looked like flying). **By hand**: the player stands back until the whole group fits (FOV `62 + 4·(max(w,h) − 1)`, at most 84°; every window clear of CI's drawer and hint line; a wall behind brings them closer and the view wider, ≤ 90°), and the view tilts to the group instead of rising to it; a lone crafter is stood close to (1.0), so the hand reaches it. **With the grip**: a section at a time (below) |
| Crouch | By hand, when the group's top is more than 0.5 below the standing eyes: the eyes are then at crouch height (CI crouches the body) |
| Hand rests | Low in the frame, off the camera (CI's arms hang from it): `eye + F·0.55 − Y·0.42 ± R·0.24` (M5) |
| Slots | One bridge slot per crafter in the group, in reading order from the front. Each insert goes through that crafter's own inventory (1 item; M5: not its capability, which for wrench-connected crafters is the whole connected input, filled in Create's order) |

| Insert (0.45 s) | | |
|---|---|---|
| 0.00–0.25 | carry to the window | spring |
| 0.25–0.37 | push 0.10 along `−S` into the face | `inOutCubic` |
| **0.33** | **beat**: insert (Create's own insertion plays its quiet item-frame click; `crafter_click` belongs to items moving between crafters) | |
| 0.37–0.45 | hand withdraws | |

**Toss** (by hand only: window farther than 1.15 from the shoulder, CI's reach; M5: from where a 2×2 or bigger group is in frame, that is every window): 0.10 s wind-up, 0.30 s arc rising `0.12 + 0.05·distance`, the item tumbling; beat on arrival.

**With the grip: the wall a section at a time** (the user's third round after M7):
- **Standing and seeing.** The player stands on the ground where the grip reaches the clicked crafter: where they are, or walked to 2.6 before it. The view is their eyes; it never leaves the head.
- **Sections.** The wall is seen a section at a time: up to 3×3 windows, spread evenly with some overlap (a 10-wide side has 4 sections, starting at windows 0, 2, 5 and 7). The section looked at fills the band between the drawer and the hint line; it starts with the one holding the clicked crafter.
- **Turning the head.** The head turns from section to section in 0.35 s (`inOutCubic`, the eyes where they are, a soft rustle):
  - W/A/S/D or the arrow keys;
  - the wheel (Shift: along the wall);
  - the cursor resting 0.35 s at an edge of the view: the sides, just under the drawer, just over the hint line. A faint arrow there fills while it rests, and the head goes on turning while the cursor stays.
- **The map.** A small map at the right edge shows the wall: each window a dot (filled brass, covered grey, empty dark, out of reach red), the section looked at framed.
- **Reach is Create's.** A window is worked when its crafter is within the player's block interaction range from their eyes, as Create's grip lets a click reach. The grip's hand puts the item in, with no toss. Windows out of reach are shaded red; a click there is refused, and its card says to stand closer or higher. From the ground with one grip, the top rows of a 10×10 are out of reach, as they would be in Create.

**Watch** (`phase ≠ IDLE`):

- The camera pulls back 0.4 and FOV opens +4 over 0.8 s (`inOutCubic`); the hands sink.
- On the CRAFTING phase: `flash 0.06` in 0xFFF2C8, 16 motes at the output crafter, and `crafter_craft` (server).
- The camera returns over 0.8 s.

**Deny:** clicking a full window that holds a different item shakes and plays `CHAIN_HIT`.

### 7.4 Valve Handle  *(Wave 1)*

> *The player takes the wheel with both hands at ten and two. A click turns it by its set angle: the shoulders lean into the turn, the hands travel round with the spokes, and at the end the wheel stops with a soft clunk that runs through the camera. Long turns go hand over hand. The scroll wheel sets the angle; the number floats over the hub like the anvil's name. The right button turns it back.*

| Staging | |
|---|---|
| Sides | `FACING` horizontal: from that side only. Facing up or down: any side (looking down at 60°, or up at −35°) |
| Work point | Wheel centre `H = pos + 0.5 − FACING·(1.5/16)` (Create's model: the rim is 5–8 px out from the face the valve is fixed to; corrected in M4 from `+FACING·5/16`) |
| Camera | `eye = H + S·1.2 + Y·0.25` (M4: at 0.8 the wheel filled the frame); a valve on a floor or ceiling is seen from 1.2 at 60° down or 35° up; look at `H`; FOV 62; body pitch 15; roll `2.5°·hump(turnProgress)·dir` |
| Grips | Points on the rim, radius 7/16, at 60° (right) and 120° (left) from the wheel's "up". They **rotate with the block entity's rendered angle** (`getIndependentAngle(pt)`), so the hands follow the real wheel |
| Controls | LMB turn by the angle · RMB turn back · scroll changes the angle by ±5° (Shift ±1°, Ctrl ±15°; −180…180, 0 is skipped) |

| Turn (length = Create's `totalUseTicks` / 20) | | |
|---|---|---|
| 0.00–0.08 | brace: grips tighten 0.01 inward, camera dips 4 mm | `outCubic` |
| **0.08** | **beat**: button "turn" (server calls `activate(back)`) · local `cranking` sound 0.25 | |
| turn | hands ride the grips; at every 90° of travel the trailing hand lets go, lifts 0.03 and re-grips 90° back (0.15 s, `outBack 1.4`) | |
| end | `kick = 0.25·e^(−14·t)`, local `IRON_TRAPDOOR_CLOSE` 0.3 / 1.4 (the clunk) | |

**Angle readout:** the text floats at `H + S·0.06 + Y·0.18`, scale 0.006 (CI anvil text). It pops `outBack` on change; each step plays Create's `scroll_value` locally; the value is sent on release (debounced 0.25 s).

**Deny:** a click while the wheel is still turning or cooling down, or when the network doesn't move (overstressed): shake + hint "The valve won't budge — the network is overstressed".

**Observers:** the wheel turns for everyone (Create); the arms show CI's generic pose.

### 7.5 Hand Crank  *(Wave 2, M6)*

> *Both hands on the grip, one behind the other. Holding the left button cranks: the arms circle with the handle at its real speed, the camera bobs gently with each revolution, and the player tires (hunger, as Create charges it). Letting go lets the crank coast. The right button cranks the other way.*

| Staging | |
|---|---|
| Camera | `eye = K + S·1.0 + Y·0.30` (`K` = crank hub); FOV 70 (M6: the handle is a block long, and CI's arms, hung from the camera, stretch like poles from farther back); a crank on a floor or ceiling is seen as the valve is; while turning, bob `Y·0.006·sin(angle)` and roll `1°·sin(angle)`, both by the handle's speed |
| Hands | Both hands on the grip (the user's call in M6: the left hand braced on the block looked like it floated): Create's handle model has the grip 7/16 out from the axis, from 1 px behind the hub to 5 px in front; the right fist holds its outer end (3.5 px in front), the left the inner end (0.5 px). Both follow the rendered angle, `getIndependentAngle(pt)`, turned as Create's visual turns the handle |
| Bridge | Button "crank ±" re-sent every 4 ticks while held, as held use repeats; the server takes each as Create's click: `turn(back)` plus its hunger (M6: a time-based rate limit dropped turns when a slow server handled several packets in one tick; extra turns gain nothing anyway) |
| Fatigue | Food < 6: bob ×1.6, faster breathing, hint "You're getting tired". Create's advancement at food 0 stays Create's |

### 7.6 Basin (+ Mixer / Press above, Blaze Burner below)  *(Wave 2, the brewing stand of Create)*

> *Leaning over the rim, the player drops ingredients in one by one; they splash and settle (or bob on the liquid). A bucket tipped over the rim makes the liquid rise. If a mixer hangs above, its whisk comes down into the basin and spins: the liquid swirls, bubbles and steam rise, and heat from the burner glows orange — or blue when superheated — through the grate. The result floats up; an empty hand scoops everything out.*

| Staging | |
|---|---|
| Work point | Basin interior `pos + (0.5, 0.5, 0.5)` |
| Camera | `eye = W + S·0.55 + Y·1.25` (looking down ≈ 70°; M6: from `S·0.75 + Y·0.85`, ≈ 50°, the near wall hides the floor and what lies on it); free of CI's tether (a basin on a burner is above the player's head); roll −1°; FOV 64; body pitch 50 |
| Controls | LMB with an item: drop it in (RMB: one) · LMB with a fluid container: pour or fill · LMB with an empty hand: take all (Create's click puts it all in the inventory, so Shift changes nothing) |

| Drop (0.5 s) | | |
|---|---|---|
| 0.00–0.20 | carry over the centre | spring |
| **0.22** | **beat**: insert. The item falls 0.15 s under gravity | |
| landing | with fluid: splash motes tinted with the fluid + `GENERIC_SPLASH` 0.2 / 1.6; dry: `depot_plop` | |

| Pour (0.9 s) | | |
|---|---|---|
| 0.00–0.35 | the container tilts 0 → 100° | `inOutCubic` |
| **0.35** | **beat**: button "pour" (server runs Create's fluid exchange with the carried item) | |
| 0.30–0.75 | stream of tinted streak sparks | |
| 0.75–0.90 | the empty container returns | |

**Watch (mixer running):**

- The camera rises 0.3 and backs off 0.1 over 0.6 s to show the whisk (M6: farther back, the near wall hides the floor again).
- `Fx.steam` 6/s and bubble motes on the surface.
- Heat glow on the basin's floor (M6: the burner's grate under it is out of sight): kindled 0xFF8A2A, seething 0x3AA0FF.
- When outputs appear: they rise with motes, plus `flash 0.05` in the fluid's colour.

**Compacting:** see 7.2.

### 7.7 Blaze Burner  *(Wave 2)*

> *Crouched at the burner, the player holds out the fuel; the blaze inside turns to it, and the item is tossed through the grate and eaten. Coal and logs kindle it; a blaze cake makes it seethe — a blue flash and a gust of heat that shimmers the view. An empty burner is lit with flint and steel struck at the grate.*

| Staging | |
|---|---|
| Crouch | Yes |
| Camera | `eye = B + S·1.05 + Y·0.6` (M6: from `S·0.85` the held-out hand sat on the camera); look at `B + Y·0.45`; FOV 66; body pitch 30 |
| Hands | The cage sides, `±R·0.36` |

| Toss (0.6 s) | | |
|---|---|---|
| 0.00–0.15 | wind-up: the hand draws back 0.08 | |
| 0.15–0.40 | arc into the grate, `hump` 0.12 | |
| **0.40** | **beat**: insert (Create plays `blaze_munch`) · 12 orange motes · `flash 0.04` in 0xFF9A3A | |

- **Blaze cake:** `flash 0.12` in 0x9BD8FF, `kick 0.30`, then heat shimmer (roll wobble 0.5° at 6 Hz, fading over 0.8 s).
- **Ignite:** two strikes 0.25 s apart, sparks, beat on the second.
- **Deny:** not a fuel → shake + "The blaze won't eat that".

### 7.8 Mechanical Saw, facing up  *(Wave 2, the stonecutter of Create)*

> *The stone is laid on the saw table, and the shapes it can be cut into hang in an arc around the blade. Choosing one sets the saw's filter: that shape glows, the others dim. While the blade spins, the hands push the stone into it in step with the saw's real progress; stone dust sprays, and the pieces come out on the far side, where the other hand gathers them.*

| Staging | |
|---|---|
| Camera | As CI's stonecutter: `eye = top + S·1.0 + Y·0.95`; look at `top − S·0.05 + Y·0.25`; roll 1.5°; FOV 64; body pitch 50 |
| Dial | Up to 9 shapes on an arc 0.72 wide and 0.5 high, CI's stonecutter layout (M7: the shapes of what lies on the table, or, with the table empty, of what the hand carries, so a shape can be chosen before laying it down). Touching a shape is button "filter = i"; touching the chosen one again clears the filter; scroll steps the filter through the shapes and "none". Each is Create's own `FilteringBehaviour.setFilter`, so the result is Create's (its round-robin when no filter is set) |
| Push | M7: the pushing hand rides just behind the work where Create draws it: from the edge things come in at to the middle while it is cut (`progress = 1 − remainingTime/duration`), then on to the far edge as the pieces leave. Create moves things along its own axis and blade direction, not the player's, so the hand on that side pushes and the other waits at the far edge, on its own side (reaching straight across, its arm crossed the view). The scene never moves the stone faster than the saw does |
| FX | Dust sparks tinted with the item's map colour (sawdust tan for items), rate ∝ RPM (cap 110/s); a faint kick of 0.04 while cutting; Create plays `saw_activate_stone`/`_wood` |
| No rotation | Blade still → hint "The saw isn't turning" |

### 7.9 Millstone  *(Wave 2)*

> *Standing over the millstone, the player pours the input into the mouth on top; it trickles down between the stones. The upper stone turns with the shaft, and flour dust puffs from the gap in time with it. When the output is ready, the hand reaches to the side and gathers it.*

| Staging | |
|---|---|
| Camera | `eye = top + S·0.80 + Y·0.70`; look at `top − Y·0.20`; FOV 64; body pitch 45 |
| Pour (0.7 s) | Tilt over the mouth, 3–5 sprites sink 0.2–0.55 s, **beat 0.25** (M7: 4 pieces; the mouth is Create's own input, which takes only what the stones can mill: anything else is refused) |
| Watch | Motes in 0xEEE6D2 from the gap ring at a rate ∝ RPM. With shake enabled, a faint constant kick of 0.05 |
| Gather (0.5 s, beat 0.25) | M7: an empty hand reaches to the side by the gap and gathers: Create's own empty-handed click (the ground output into the inventory, or, with none, what is still in the mouth). Blade still → hint "The millstone isn't turning" |

### 7.10 Deployer — hand-off, and "Application" on a depot  *(Wave 2, the smithing table of Create)*

> *Facing the deployer's hand, the player holds out the item; the claw closes on it just as the player's fingers let go — a handshake between person and machine. Reaching for what the deployer holds swaps it back. With a depot under the deployer, the work piece goes on the depot first; when the deployer punches down, the scene answers like the press, smaller.*

| Staging | |
|---|---|
| Hand-off (0.7 s) | Reach the claw 0–0.30 s, **beat 0.30** (swap), retract 0.30–0.70 s with the deployer's former item. M7: the swap is Create's own click on the deployer's hand, with the carried item; only a deployer facing sideways has the hand-off (one pointing down works the depot under it). Camera 1.05 in front of the claw and 0.2 up (from 0.85 the deployer filled the frame), FOV 64; the claw is read from `getHandOffset(pt)` |
| Application | M7: the camera stands as for the press, pulled back and a little lower, so the deployer's head is in frame. Impact on the deployer's punch (the frame Create's hand turns back from past half its reach): `kick 0.25`, 12 warm sparks (M7: not tinted: the deployer's held item is not readable on the client). When the work on the depot changes, it glows briefly (motes) |

### 7.11 Bulk processing — Depot in an Encased Fan stream  *(Wave 2, the smoker / blast furnace of Create)*

> *Setting the item on a depot in a fan's stream, the player feels the wind: sleeves and camera tremble, and the item is washed, smoked, blasted or haunted right in front of them. For blasting and haunting, the free hand comes up to shield the eyes.*

| Type | Particles | Colour | Result flash |
|---|---|---|---|
| Washing | rising motes | 0x6FB7FF | 0.04 |
| Smoking | `Fx.steam` (grey) | — | 0.03 |
| Blasting | orange sparks | 0xFF7A2A | 0.06 |
| Haunting | soul motes | 0x6FE3E8 | 0.05 |

- **Wind:** a noise kick of 0.06–0.10 while in the stream (M7: 0.08); FOV −2.
- M7: the stream is found by looking from the depot up to 24 blocks along each direction for an Encased Fan whose air reaches it; its type at the depot is Create's (`AirCurrent.getTypeAt`), named as Create names it (splashing, smoking, blasting, haunting). A depot in a stream with no type (plain air) keeps the depot scene.
- **Shield:** the left hand rises to the brow, `eye − S·0.18 + Y·0.05`.

### 7.12 Weighted Ejector  *(Wave 2)*

> *The item is set on the plate like a depot. When the plate snaps, the player stays where they lean over it and turns their head after the throw: the eyes follow the item along its arc, rest on it where it lands, and come back to the ejector.* (After M7, at the user's request: the camera no longer flies after the item.)

| Staging | |
|---|---|
| Base | As the depot (7.1) |
| Launch | `kick 0.40`; the launch is seen when Create's state leaves CHARGED |
| Watch | The eyes stay where they are (no free camera); only the look turns: it catches up with the item over 0.25 s, follows Create's own path (`getLaunchedItemLocation`; the flight time is the first moment that path covers the distance to the target), rests 0.4 s where it lands, and comes back over 0.6 s (`inOutCubic`). A neck turns only so far: at most 110° to either side, 70° up, 60° down; a throw that goes behind the player is followed to the end of the turn. A far landing is watched a little narrower (up to 12° less at 16 blocks), as if squinting after it |
| Limit | Only when the "Watch launches" setting is on (default on; `followLaunches` in the client config) and the target is ≤ 32 blocks away |

### 7.13 Item Drain and Spout filling  *(Wave 2)*

> *Drain: the filled container is laid on the grate; it tips by itself as Create drains it, the liquid spirals into the grate, and the empty container is taken back. Spout over a depot: the nozzle lowers and pours, droplets bead on the bottle, and at the end it flares in its new colour like the brewing stand's potions.*

The drain uses the depot staging with the work point at the grate. The spout flare reuses CI's brewing flare: a billboard glow at 0.09 → 0.14 in the potion's colour.

M7: the container is laid on the far side of the grate (through Create's own input from that side), so it rolls to the middle, is drained, and rolls on toward the player and off the near edge, where the right hand reaches out to catch it. The liquid spirals down in its colour while it drains. One filled container at a time, as the drain takes them. DESIGN's "empty container taken back" is the hand catching it; an empty bucket does not fill from the drain: Create 6.0.10's drain only empties into its tank on a click, and gives its liquid to pipes. The spout camera stands as for the deployer above a depot; the droplets bead while the spout pours (its countdown above 5), and the flare comes the tick Create fills (the countdown reaching 5).

### 7.14 Wave 3 (short specs)

| Block | Storyboard (one line) | Key numbers |
|---|---|---|
| Toolbox | The lid swings open (0.35 s, `outBack 1.2`); 8 compartments are visible; tools are drawn out by the handle and slid back in, as CI's bookshelf does with books | Crouch; native menu slots 0–7 |
| Schematic Table | The empty schematic lies on the slope; touching the file list writes it — a quill scratches and lines fill in from top to bottom over the upload progress | Native menu; progress read from the menu |
| Schematicannon | Load schematic, book and gunpowder into the breech; on Start, the camera draws back like the beacon and watches the first few blocks fly | `kick 0.3` per shot, capped at 2/s |
| Lectern Controller | Hands on the controller over the lectern; keys glow as Create's linked controller sends them; leaving gives the controller back | Uses Create's own "start using" |
| Clipboard | Lift the board off the wall to eye level (0.45 s), then hand over to Create's screen; on close, put it back | Short; no bridge |
| Table Cloth | Items laid on the cloth like CI's table top; with a stock ticker, the price tag hangs over each item | Bridge over the cloth BE |
| Packager / Postbox / Frogport | A package in hand is set into the hatch and unwrapped / posted (the frog's tongue catches it) | Short |
| Desk Bell | The palm slaps the bell; `kick 0.15`, Create's `desk_bell` | Short, KEY |
| Analog Lever | The hand ratchets the lever one notch per click; the 0–15 dial floats beside it | Short, KEY |
| Seat | Sit-down camera with the legs in frame (CI campfire style, 0.9 s), then normal riding | KEY |
| Item Hatch | The hotbar empties into the hatch in a rapid fan of items (0.06 s per stack) | Short, KEY |
| Backtank | Lift it by the straps and swing it onto the back (0.8 s), then the chest slot is filled | Short, KEY |

---

## 8. Shared look: FX, sound, feedback

- **Palette** (each tied to a Create material):
  - Brass sparks 0xFFC060
  - Andesite dust 0xB5B5A8
  - Copper glints 0xE77C56
  - Hot metal 0xFFE6B8 (CI's anvil flash)
  - Superheat 0x9BD8FF
  - Haunting 0x6FE3E8
  - Steam: `Fx.steam`
- **Sounds:** Create's sounds play from the server when Create's real action runs (`depot_slide`, `depot_plop`, `mechanical_press_activation`, `mixing`, `crafter_click`, `crafter_craft`, `blaze_munch`, `saw_activate_*`, `desk_bell`). The scene adds only local, quiet cues for moments Create has no sound for (the valve clunk, hand-offs, tosses), always at ≤ 0.35 volume.
- **Feedback:** CI's glow disc and face patch for hover; deny = shake + `CHAIN_HIT` (or Create's `deny` for logistics blocks); one hint line; floating text in CI's anvil text scale.
- **Budgets:** ≤ 120 sparks/s per scene; one kick per 0.35 s; flash ≤ 0.12; and the off-switches for shake, flash, bars and living camera are always respected (they are CI's own settings).
- **Our config** (client): trigger policy per block (USE / KEY / OFF), the "[H]" invitation, watch launches, and the watch-scene camera pull-back.

### 8.4 Notices, and never a view through blocks  *(after M7, the user's report)*

**Notices.** When a scene does not start, or ends because of something the player did, a small card says why. It slides in from the right edge a little below the status effects (0.35 s, `outBack`), stays 3.4 s and slides out (0.3 s). There are at most three at a time; the same card again only comes back to the front. Each card shows the block's icon, its name and the reason. The reasons come from Cinematic Interactions' own refusal (too high, too low, too far, no way there, wrong side, something in the way, the server said no), made specific where our scene knows better:
- a block where the player would stand;
- a block between the player and the scene's view;
- the crafters' own height rule;
- "an Extendo Grip would reach it", when one would.

CI's own refusal is silent when its height logic falls back to Create's click; a client hook on `Scene.refuse` (optional, `require = 0`) brings the card.

**Views.** A scene whose camera stands away from the player's head (the crafters' framing, the basin's look over its rim) is refused when that camera cannot be reached from the player's own eyes without passing through a block (`Views.reachable`). Otherwise, with blocks set where the player would stand, the scene played with the player behind them and the view on the other side. In a narrow gap the scene still plays, with its view in the gap.

### 8.5 Long reach: Create's Extendo Grip  *(after M7, the user's request)*

Holding an Extendo Grip in either hand lets the scenes reach exactly as far as Create's grip lets a click reach. That is the player's block interaction range, Create's modifier included: 4.5 + 3, or + 5 with one in each hand. It is measured as the game does, from the eyes to the nearest point of the block. A scene is started only within that (the user's third round: no more than the item reaches in Create), and within the 6 blocks the scene server allows from the block clicked.

- **Worked from where the player stands.** When the work is within the grip's reach, the scene does not walk the player up. The view stays at the eyes (CI's tether), except for the hanging basin, where the view rises over the rim. The hand holding the grip stays by the body, and the grip opens out to the work.
- **Drawn as Create draws it.** Create's own handle model is in the fist, with its cog turning. A scissor lattice with Create's link texture opens out from it; the shorter it is, the wider its links spread, as scissors do. At the end is the grip's claw, one of Create's deployer hands. It points when it reaches empty, holds when it carries or holds on (a crank, a valve's wheel, a basin's rim), and is a fist at rest. Until the user's report after round three, the claw was the player's own skinned arm. Close to the work, or while the hands came up, it lay on the real arm and looked like a second arm growing from it.
- **Folded, never shorter.** From the fist to the claw's palm the grip is at least 0.45 (its handle, the folded lattice and the claw). When a hand's resting place is within a bare arm's reach (a basin leaned over, a depot up close), the fist rests where the bare hand would, and the folded grip points ahead of it.
- **Moving with the hand.** The claw is drawn from the fist where CI draws that hand. It comes up with the hand as a scene begins and goes down with it as it ends, and the lattice eases out and in (14 per second) as the work moves. What the working hand carries is drawn in the claw.
- **The grip's hand does the work at every machine.** It sets down and takes at depots, drains and saws, turns a valve's wheel and a crank's handle (both hands on the grip's handle), and pushes stone into the saw. It feeds the blaze through the cage instead of a toss. It puts items straight into crafter windows within its reach (a big crafter wall is the grip's alone, §7.3), and hands over claw to claw with a deployer.
- **The basin, hung from.** A basin whose inside is more than 1.9 above the feet (too high to lean over) is worked hanging from its rim. The grip is hooked on the near rim from the left hand: the fist below the rim outside, the claw's palm on the wall and its fingers over into the basin. The view rises over the rim, and the right hand works bare. Within 1.5 of the basin and up to the grip's reach above.
- **The grip put away mid-scene** (moved in the drawer, another hotbar slot, worn out). If the scene needed it (begun out of a bare arm's reach), it starts again without it: the player walks up if there is a way, and a card says so; otherwise CI refuses and the card says why. Hanging from a basin, the player drops: the view falls back to the eyes, a thud, a card.

Possible problems, and how they work out for the player:
- The grip does not wear in scenes: Create only charges it for placing and breaking blocks.
- The drop from a basin is the view only (the player never left the ground): no fall damage.
- Losing the grip never loses the scene where a path exists: it walks the player up.
- Crafter windows within the grip's reach are filled by hand: no toss arcs to wait through.
- The refusal card suggests a grip when one would reach.
- CI's line-of-sight check still applies at a distance: a railing between the player and the work refuses the scene with "… is in the way".
- The 6-block server limit caps two grips' +5.
- The hands' drawn positions are read from CI by reflection. If CI changes, the grip is drawn from the hands' targets instead (logged once).

---

## 9. Milestones

Each milestone ends with a test in singleplayer **and** on the dev server with the dev client (`run-server/` + `run/`).

| # | Milestone | Acceptance criteria |
|---|---|---|
| **M0** ✅ | Scaffold | `gradlew build` passes; **Client** and **Server** run configs start with Create, Ponder, Flywheel and CI loaded; the client joins the dev server |
| **M1** ✅ | Bridge core + bare Depot (status and the plan after it: [ROADMAP.md](ROADMAP.md)) | Right-click a depot → scene; put down and take work; sneak gives Create's use; a failed reach check gives Create's use instantly; the wrench and other held-item bypasses work; a second player sees the item move |
| **M2** ✅ | Trigger policy + config (what was built: [ROADMAP.md](ROADMAP.md) §1.2) | USE / KEY / OFF per block; H starts KEY scenes; the prefs payload keeps server and client in agreement; the invitation renders |
| **M3** ✅ | Pressing composite (what was built: [ROADMAP.md](ROADMAP.md) §1.3; the basin variant comes with M6) | Press impacts land within one frame of Create's head reaching the plate; no FX spam at 256 RPM; the "no power" hint shows |
| **M4** ✅ | Valve Handle (17 variants; what was built: [ROADMAP.md](ROADMAP.md) §1.4) | Grips follow the real wheel; hand-over-hand above 90°; angle scroll is saved by Create; deny while turning or overstressed |
| **M5** ✅ | Mechanical Crafter (what was built: [ROADMAP.md](ROADMAP.md) §1.5) | Groups up to 5×5 framed fully; toss beyond reach; slot cover; the watch pull-back on crafting |
| **M6** ✅ | Basin + Mixer + Burner, Blaze Burner, Hand Crank (added 2026-10-22; what was built: [ROADMAP.md](ROADMAP.md) §1.6) | Drops, pours, heat glow by level; watching the mix; compacting variant; the crank turned by hand, the hand on Create's drawn knob |
| **M7** ✅ | Saw, Millstone, Deployer, Ejector, Fan composites, Drain / Spout (what was built: [ROADMAP.md](ROADMAP.md) §1.7) | Per the §7 specs; machine-progress-driven hands |
| M8 | Wave 3 | Native-menu scenes open with correct extra data; no client crash on fallback |
| M9 | Polish & release | EN/RU lang; Iris check; dedicated-server test with two real players; mod page states clearly that both sides need it; upstream API PR offered to CI |

---

## 10. Risks and open questions

- **CI's API is a preview, and CI ships several builds a day.** Pin the dependency to `[0.18.12, 0.19)`, keep the mixins fail-soft, and push the upstream PR early so the hooks become official.
- **Event order between CI and Create.** Create's value-settings click handler and CI's use interception both run at NORMAL priority, so mod load order decides which goes first. Mixin 3's value-box test must make the outcome independent of order. Test with the depot's and the saw's filter slots. (Done after M7: `Bridge.onValueBox` mirrors Create's value-settings and link handlers for every block, ours and the machines working them; the smoke checks the valve's hub, the ejector's stack size, and the saw's and a deployer's filters.)
- **Large crafter groups** (Create allows any size). Done in M5: capped at 5×5 (a bigger wall shrinks to its recipe chain, else Create's use), and the camera stops in front of a wall behind it. After M7: up to 10×10, as Create builds them. The group is worked from in front when its bottom row is within the player's height; the windows above are tossed to (or reached with an Extendo Grip, §8.5).
- **Create 6.0.11** (in development on Create's maven): its block-entity field names may change. Check them on each Create update.
- **Shaders:** CI fixed effect depth for shaders on Fabric 26.x. Verify our FX on NeoForge with Iris (if Iris/Oculus is used on the server's modpack).
- **Licensing:** CI and Create are both MIT. If we ever reuse CI's hammer model, credit RDiamonds (CC BY 4.0).
- **Open questions for you:** should the mod use the server's `mcp` naming instead of `cinecreate`? Should it be public on CurseForge/Modrinth, or Minechestplate-only? Should the upstream PR to CI be offered before or after Wave 1?

---

## 11. Sources

- CurseForge — Cinematic Interactions: https://www.curseforge.com/minecraft/mc-mods/cinematic-interactions
- GitHub — dcvke/CinematicInteractions (README, `docs/addon-api.md`, `docs/wiki/*`, `examples/addon-template`, NeoForge source at `c926f72`): https://github.com/dcvke/CinematicInteractions
- Roadmap issue #1 (Create question and author's reply): https://github.com/dcvke/CinematicInteractions/issues/1
- Release `v0.18.12-neoforge-1.21.1` (API jar in `libs/`): https://github.com/dcvke/CinematicInteractions/releases/tag/v0.18.12-neoforge-1.21.1
- TikTok — StudiousGoose: https://www.tiktok.com/@thestudiousgoose/video/7698081576302677261
- TikTok — Masi Main: https://www.tiktok.com/@masimain_/video/7698275569384901908
- Create 6.0.10 sources (`create-1.21.1-6.0.10-281-sources.jar`), maven.createmod.net
