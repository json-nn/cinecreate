# cinecreate — status after M7, and what comes next

Written 2026-10-21 after milestone M1, updated after M2 and M3 the same day, after M4 and M5 on 2026-10-22, and after M6 and M7 on 2026-10-23. The research, block catalogue and animation specs are in [DESIGN.md](DESIGN.md); this file tracks what is built and plans the rest.

**Contents**

1. [What is built](#1-what-is-built) — [M1: the depot scene](#11-m1--the-depot-scene), [M2: trigger policy](#12-m2--trigger-policy), [M3: pressing](#13-m3--pressing), [M4: the Valve Handle](#14-m4--the-valve-handle), [M5: Mechanical Crafters](#15-m5--mechanical-crafters), [M6: the Hand Crank, the Basin and the Blaze Burner](#16-m6--the-hand-crank-the-basin-and-the-blaze-burner), [M7: Wave 2](#17-m7--wave-2-saw-millstone-deployer-ejector-fan-drain-spout), [after M7: value boxes](#18-after-m7-creates-value-boxes-stay-creates), [after M7: the second round](#19-after-m7-the-ejector-watched-no-view-through-blocks-notices-1010-crafters-the-extendo-grip), [after M7: big crafters with the grip](#110-after-m7-big-crafter-walls-with-the-grip-a-section-at-a-time-and-creates-reach)
2. [Known limitations](#2-known-limitations)
3. [Next milestones](#3-next-milestones)
4. [Later: ideas worth doing](#4-later-ideas-worth-doing)
5. [How to work on a milestone](#5-how-to-work-on-a-milestone)

---

## 1. What is built

### 1.1 M1 — the depot scene

Right-clicking the **top** of a Create depot now plays a Cinematic Interactions scene:

1. The player walks up and the camera leans over the plate (CI's bars, breathing camera and skinned arms).
2. The stack in hand is carried over the plate and set down. It lands where Create draws a depot item, with Create's slide sound.
3. Touching the stack lifts it back into the hand; Create's leftover outputs go to the inventory.
4. RMB sets down a single item, Shift sends the stack to the inventory, and a shift-click in CI's inventory drawer moves a stack onto the depot.
5. Esc steps back; whatever is on the depot stays there.

Under the hood, CI's add-on API is extended with **bridge menus** and **three fail-soft hooks**, as planned in DESIGN §5.3.

| Part | File | What it does |
|---|---|---|
| Bridge base | `bridge/BridgeMenu` | Windowless menu: the block's slots, then the player's 36 slots. Valid within 6 blocks while the block is unchanged; quick-move goes into inputs only |
| Depot bridge | `bridge/DepotMenu` | Slot 0 = the stack on the depot, through Create's `DepotBehaviour` (slide-in, sounds, sync); slots 1–8 = Create's output buffer (take only); plain synced copy on the client |
| Server logic | `bridge/Bridge` | Opens the bridge with the position as extra data; Create's own use as the fallback; the use rules |
| Registry | `station/SceneBlock`, `SceneBlocks` | Which Create blocks get scenes (sides, working faces, menu, fallback hit); registers them with `AddonStations` |
| Hook check | `station/CiHooks`, `CiHookMarker` | Before registering anything, checks that all three hooked methods exist and our mixins were applied; otherwise scenes are off and Create behaves normally |
| CI hooks | `mixin/MenuActionsMixin`, `SessionsMixin`, `StationEventsMixin` | 1: open our menu · 2: fallback = Create's use · 3: leave wrench/goggles/filters and non-working faces to Create (`require = 0`, so a changed CI never crashes the game) |
| Bypass items | `data/cinecreate/tags/item/scene_bypass.json` | Wrench, goggles, mechanical arm, belt connector, super glue, clipboard, filters, schedule, linked controller, `#c:tools/wrench` |
| Scene | `client/scene/DepotScene` | Staging per DESIGN §7.1; set-down (0.55 s, beat 0.38) and take (0.45 s, beat 0.20) timelines; a "ghost" stack until Create's renderer has it; glow under the cursor, red when refused |
| Fallback window | `client/BridgeScreen` | Plain screen. Minecraft needs one registered or the menu never reaches the player; it only shows if a scene dies before the menu arrives |
| Dev check | `client/dev/SmokeTest`, `smoke` run | Drives the scene in a test world, checks the server state after every step, saves screenshots, writes `run-smoke/smoke-results.txt` and quits |
| Text | `assets/cinecreate/lang/en_us.json`, `ru_ru.json` | Hint line and menu title |

#### Acceptance (DESIGN §9, M1)

| Criterion | Result | How it was checked |
|---|---|---|
| Right-click a depot → scene | ✅ | Smoke test plus screenshots 01–02 |
| Put down and take work (server state correct) | ✅ | Smoke: the server's depot holds 16 after setting down and 0 after taking; the hand holds the reverse |
| RMB = one item; drawer shift-click | ✅ | Smoke |
| A failed reach check gives Create's use | ✅ | Smoke sends CI's own fallback request: the stack lands on the depot via Create's code |
| Wrench and other held-item bypasses | ✅ | Smoke: a wrench right-click starts no scene |
| Clicks on non-working faces stay Create's | ✅ | Smoke: a block placed against the depot's side is placed, no scene |
| Leaving loses nothing and closes the session's menu | ✅ | Smoke |
| Sneak gives Create's use | ✅ by CI's design (`isSecondaryUseActive`) | Not in the smoke test; check by hand |
| A second player sees the item move | ⏳ not yet checked | Needs two clients on the dev server (Create renders the depot for everyone, so this is expected to just work) |

**Smoke test: 13/13 checks pass** (see §5 for the run log).

### 1.2 M2 — trigger policy

Each scene now has a **trigger** that every player picks for themselves: **USE** (a right-click starts the scene), **KEY** (a right-click is Create's own and a key starts the scene) or **OFF**. The bare depot defaults to KEY. This answers the "fun for 20 minutes, then repetitive" review (DESIGN §3): putting a stack on a depot is one click again, and the scene is there when the player asks for it.

1. **KEY:** a right-click on the depot is pure Create (put down / take at once), on the client and on the server.
2. Looking at the depot, **H** ("Work at the machine", in its own *Cinematic Interactions: Create* controls category) starts the scene. H is also CI's campfire key, on purpose: NeoForge fires every mapping bound to a key, and each one acts only on its own blocks. In USE mode the key works too.
3. A small **"[H] Work at the depot"** plate under the crosshair invites to the scene. It shows only for KEY blocks, only where the scene can actually happen from where the player stands, and never during a scene. It can be turned off.
4. **OFF:** never a scene. Right-clicks are Create's, the key does nothing, and CI does not offer the depot for moving over from another scene.
5. A change applies at once, from the mod list's config screen or by editing `config/cinecreate-client.toml`. There is no relog.

| Part | File | What it does |
|---|---|---|
| Trigger | `station/Trigger` | `USE` / `KEY` / `OFF`, with translated names for the config screen |
| Defaults | `station/SceneBlock.trigger`, `SceneBlocks` | Depot: KEY. When a scene is OFF, its station has no working sides on the client, so CI neither starts it nor offers it as a neighbour; the server still sees all sides |
| Client config | `client/ClientConfig` | `[scenes] depot = "KEY"` and `invitation = true` in `cinecreate-client.toml`; NeoForge's `ConfigurationScreen` behind the mod list's Config button. Every reload (screen or file) sends the prefs |
| Prefs payload | `bridge/ScenePrefsPayload`, `bridge/ScenePrefs` | `cinecreate:scene_prefs`, client to server, an **optional** channel. Sent at login and on every config reload. The server keeps one map per player and drops it at logout; a player who sent nothing gets USE (the M1 behaviour) |
| Use rule | `bridge/Bridge.leaveToCreate` (hook 3) | Trigger ≠ USE → the click is left to Create. The client reads its own config; the server reads that player's map |
| Key and invitation | `client/WorkKey` | The key mapping; `Scene.begin(pos, ADDON)` after a reach check that mirrors CI's private `Scene.choose`. If no scene is possible there, it shows CI's reach notice rather than doing Create's use (the right-click is for that). The invitation is a GUI layer above the crosshair, drawn like CI's campfire invitation |
| Text | `assets/cinecreate/lang/en_us.json`, `ru_ru.json` | Key and category, the invitation, config screen labels and tooltips, trigger names |
| Dev check | `client/dev/SmokeTest` | Seven more checks: the M1 steps run in USE, then KEY and OFF are switched in the config the way the screen does it |

#### Acceptance (ROADMAP M2 row, DESIGN §9)

| Criterion | Result | How it was checked |
|---|---|---|
| Switching the policy applies without relogging | ✅ | Smoke switches USE → KEY → OFF in one session; the next right-click follows each switch, on the server too |
| KEY: a right-click is pure Create | ✅ | Smoke: no scene, and the server's depot holds the stack. Had the server not heard KEY, its CI would have cancelled the click and the depot would be empty |
| H starts the scene | ✅ | Smoke presses the key through `KeyMapping.click`, the same path as a real key press (CI's campfire mapping on H gets the press too); the depot scene opens with its bridge menu (screenshot 10) |
| OFF never starts one | ✅ | Smoke: right-click → Create's use; the key → nothing; no invitation; CI's `usable()` is false on the client and still true on the server |
| The invitation renders | ✅ | Smoke: shown when looking at a KEY depot, absent in OFF (screenshot 09) |
| Server and client agree; the dedicated server works | ✅ partly | Integrated server: covered by the KEY and OFF smoke checks. Dedicated server (`Server` + `ClientJoin`): the hooks apply, the client joins without errors, and the server logs the client's prefs at login (`Dev starts scenes: {cinecreate:depot=KEY}`). A KEY right-click on the dedicated server was not exercised: nothing scripts the joining client (limitation 6), so check it by hand |

**Smoke test: 19/19 checks pass** (12 from M1 and 7 for M2; the M1 notes above count 13, but the M1 part of the smoke test has 12 checks; see §5 for the run log).

### 1.3 M3 — pressing

A depot with a Mechanical Press two blocks above it now plays the **pressing scene** (DESIGN §7.2). In it the depot is the anvil, and Create drives everything; the scene only listens.

1. The player lays the stack on the plate (the depot scene's own moves) and keeps both hands on the rim. The camera stands further back and lower than at the bare depot, so the press head is in the frame too.
2. While the head comes down, the hands **flinch back** (0.12 over 0.22 s) and return after the impact.
3. On the frame the head reaches the plate comes the **impact**: a camera kick, a warm flash (CI's hot-metal colour), a ring of hot brass sparks from under the head and a quiet metallic ring. Create's own press sound comes from the server. If no rendered frame shows the head on the plate (a hitch, or a fast press at a low frame rate), the impact lands on the first frame after.
4. When the stack on the depot turns into the result, warm motes rise off it with a chime.
5. **Speed limits:** at most one kick and flash per 0.35 s. A cycle shorter than a second weakens every effect in proportion (at 256 RPM a cycle is about 0.4 s, so effects run at about 40 %).
6. **No rotation** (below 1 RPM, or overstressed): the hint line says the press has no rotation, and the fingers drum on the rim.
7. **The press itself:** a right-click on the press starts the same scene at the depot below. A block in hand is still placed against the press (a shaft, say), and the wrench and other bypass items keep their Create use.
8. **Its own trigger:** pressing is a separate entry in the client config (`[scenes] pressing`, default **USE**). The bare depot stays on KEY, so a right-click on a bare depot is Create's, while one on a depot under a press starts the scene.

| Part | File | What it does |
|---|---|---|
| Scene kinds | `station/SceneKind`, `SceneKinds` | The thing a player sets a trigger for: `depot` (KEY) and `pressing` (USE). One block can play several kinds, so config, prefs payload and the use rule now key on the kind; `SceneBlock.kindAt` picks it per position |
| Composites | `station/Composites` | `pressOver(depot)` (a press two blocks up) and `workedBy(press)` (the depot it works) |
| Press click | `bridge/Bridge.onMachineUse` | A right-click on a press over one of our depots, decided the same way on both sides like CI's own stations: cancelled on both, and the client begins the depot's scene (`ClientBridge.useStation`) |
| Scene | `client/scene/PressScene` (extends `DepotScene`) | Camera `eye = W + S·1.05 + Y·0.55`, look at `W + Y·0.45`, roll −1.5°, FOV 66, body pitch 30; rim rests ±0.34. Each frame (`beforeFrame`, with that frame's partial tick) it reads Create's own `PressingBehaviour` interpolation: flinch while the head comes down, impact once the cycle reaches the plate (`CYCLE/2`), result when the depot's contents change after the impact |
| Scene choice | `CineCreateClient` | The depot's scene factory returns `PressScene` when a press stands over the depot as the scene starts |
| Frame numbers | `client/ClientEvents` | `FrameCounter` runs first in `RenderFrameEvent.Pre`, so per-frame scene work sees the current frame's number |
| Text | lang EN/RU | Pressing hints (normal and stopped), "[H] Work at the press", the `pressing` config entry |
| Dev check | `client/dev/SmokeTest` | Nine more checks: a press and a creative motor at 256 RPM over the depot, 4 ingots pressed, impacts timed against an independent per-frame sampler of Create's head, a 10 s watch, the stopped hint, and a click on the press itself. `setUp` now revokes all advancements, so each run meets Create's first-time advancements the way a new player does |

#### Acceptance (ROADMAP M3 row, DESIGN §9)

| Criterion | Result | How it was checked |
|---|---|---|
| The depot under a press (and the press itself) gives the press scene | ✅ | Smoke: with the bare depot on KEY, a right-click on the depot under the press starts `PressScene`; so does a right-click on the press itself, at the depot below |
| Impact lands within one frame of Create's head | ✅ | Smoke: an independent sampler records, at render time, the first frame of each cycle on which Create draws the head at (or past) the plate. Every impact was on that same frame (e.g. frames 1263 / 1279 / 1308 / 1341, one of them a cycle whose head no frame showed at the plate) |
| ≤ 1 kick per 0.35 s at 256 RPM | ✅ | Smoke at 256 RPM (Create's top speed): 4 impacts about 0.55 s apart, every gap ≥ 0.35 s; effects at about 40 % strength |
| Smoke builds press + motor and screenshots the impact | ✅ | Screenshots 11 (impact), 12 (pressed: sheets on the plate), 13 (stopped: hint line) |
| The "no power" hint shows | ✅ | Smoke stops the motor: the hint reads "The press has no rotation…" |
| Pressing really happens on the server | ✅ | Smoke: 4 iron sheets on the depot, no iron left; one impact and one result per ingot |
| Dedicated server | ✅ partly | `Server` + `ClientJoin`: joins without errors; the server receives `{cinecreate:depot=KEY, cinecreate:pressing=USE}`. Pressing on the dedicated server was not exercised (limitation 6) |

**Smoke test: 28/28 checks pass** (12 from M1, 7 for M2, 9 for M3; see §5 for the run log).

### 1.4 M4 — the Valve Handle

All 17 Valve Handles (copper and the 16 dyed ones) now have a scene (DESIGN §7.4). The player takes the wheel with both hands at ten and two.

1. **LMB turns** the wheel by its set angle and **RMB turns it back**, through Create's own `ValveHandleBlockEntity.activate`. The network behind it turns by exactly the set angle, and the wheel itself snaps a quarter turn (half a turn past 135°), as Create does it.
2. The hands **ride the rim** with Create's drawn wheel (`getIndependentAngle(pt)`, the same rotation Create applies). After a quarter turn of travel a hand lets go, lifts off and takes hold again back at its place. The trailing hand goes first, one hand at a time, and both return to ten and two when the wheel stops.
3. A short brace before the turn; when the wheel stops, a **clunk** runs through the camera (a small kick and a quiet iron-trapdoor sound). The camera rolls slightly into the turn.
4. The **scroll wheel sets the angle**: 5° a notch (Shift 1°, Ctrl 15°), −180…180, never 0. The value floats over the hub with Create's ⟳ / ⟲ direction symbols and is sent 0.25 s after the last notch. It is Create's own setting on the block (its value box).
5. **Refusals:** a click while the wheel turns, while something else drives the network, or with the network overstressed gives the deny shake. The hint line says why ("something else turns this network", "the network is overstressed"). A refusal the server gives (cooldown) is caught too: if the wheel does not move within 0.6 s, the hands shake.
6. **The hub is Create's value box.** ~~A hub click started the scene.~~ Since the fix after M7 (§1.8), a hub click is Create's own angle setting, as on every block's value boxes; a click anywhere else on the wheel starts the scene.
7. **Dyes stay Create's:** a dye in hand recolours the valve, with no scene.
8. Its own trigger: `[scenes] valve_handle`, default **USE**. On a wall the scene is worked from the front only; a valve on a floor or ceiling can be worked from any side, looking down or up at it.

| Part | File | What it does |
|---|---|---|
| Stations | `station/SceneBlocks.VALVES` | 17 `SceneBlock`s in a loop (CI takes one block per definition), kind `valve_handle` |
| Block rules | `station/SceneBlock` | Two new parts: `use`, Create's own right-click for the fallback (a valve turns in `ValveHandleBlock.clicked`, not in `useItemOn`), and `bypass`, held items that stay Create's on that block (dyes on a valve). The M1 `hit` factory became the depot's `use` |
| Bridge | `bridge/ValveMenu` | No item slots; buttons `TURN`, `TURN_BACK` and `ANGLE + 180 + angle`, run on the server through `activate()` and the block's own `angleInput` |
| Use rules | `bridge/Bridge.onUse` (was `onMachineUse`) | HIGH priority: clicks on a machine working one of our blocks (the press). Until §1.8 it also started the scene for clicks on our blocks' value boxes |
| Scene | `client/scene/ValveScene` | Camera 1.2 out from the wheel's centre and 0.25 up (FOV 62, body pitch 15; looking down or up for floor and ceiling valves); grips, regrips, brace, clunk, roll, readout, refusals; no inventory drawer |
| Overlays | `client/ClientEvents` | While one of our scenes plays, the tick's crosshair target is cleared after vanilla picks it, so Create draws no value box or other crosshair overlay over the work (it showed the old angle next to ours) |
| Text | lang EN/RU | Valve hints (normal, driven, overstressed), "[H] Turn the valve", the `valve_handle` config entry, menu title |
| Dev check | `client/dev/SmokeTest` | Nine more checks; the window now opens maximized (full screen, with borders) and logs its state |

#### Acceptance (ROADMAP M4 row, DESIGN §9)

| Criterion | Result | How it was checked |
|---|---|---|
| Smoke turns a valve on a shaft and checks the angle Create stored | ✅ | Smoke: a copper valve on a shaft; after a turn the server's valve stands at −90°, after a turn back at +90°. During the turn the shaft behind it runs at −32 RPM |
| Angle scroll is saved by Create | ✅ | Smoke scrolls 45° → 90°; the server's `angleInput` reads 90 |
| Grips follow the real wheel; hand over hand past 90° | ✅ by construction | The grips use Create's own `getIndependentAngle(pt)` and rotation axis; screenshots 14–15. Not measured by the smoke |
| Deny while turning (or overstressed) | ✅ turning | Smoke: a second click mid-turn is refused (deny). The overstressed and "driven" cases are not set up in the smoke |
| A hub click starts the scene whatever the mod order | ✅, then changed (§1.8) | Smoke right-clicked the hub (the value box) and got the valve scene. Since §1.8 the hub is Create's, and the smoke checks that instead |
| Dyes stay Create's; all 17 colours | ✅ | Smoke: a red dye recolours the valve with no scene; the red valve then starts its own scene. The dedicated server registers all 17 stations |
| Dedicated server | ✅ partly | `Server` + `ClientJoin`: joins without errors; the server receives `{…, cinecreate:valve_handle=USE}`. Turning a valve on the dedicated server was not exercised (limitation 6) |

**Smoke test: 37/37 checks pass** (12 from M1, 7 for M2, 9 for M3, 9 for M4; see §5 for the run log).

### 1.5 M5 — Mechanical Crafters

A right-click on the front of any Mechanical Crafter now works its whole group in a scene (DESIGN §7.3).

1. **The group** is the wall the clicked crafter stands in: crafters touching in the plane of their fronts, all facing the same way, up to **5×5**. A bigger wall shrinks to the clicked crafter's recipe chain (the crafters that pass items to it, Create's `RecipeGridHandler`). When even that is bigger than 5×5 there is no scene: the click stays Create's, on both sides (DESIGN §10). One bridge slot per crafter, in reading order as seen from the front.
2. **Framing.** The camera stands straight in front of the group, far enough back for all of it: the field of view is 62° for one crafter and 4° more per extra row or column (78° for 5×5), and the distance is worked out from the group's size and the game window's shape. Every window stays clear of CI's inventory drawer along the top and its hint line along the bottom, so the camera stands a little above the group's middle. A 3×3 is seen from 2.7 blocks back, a 5×5 from 4.0. A wall behind the player brings the camera in front of it, with a wider view (up to 90°). The camera is free of CI's tether (`freeCamera()`); the player stands 1.0–1.8 blocks out.
3. **Putting in:** one item per window, through that crafter's own inventory, so Create decides what fits, plays its own insert sound and starts the recipe once every window is filled. A window within arm's reach gets the item carried in and pushed into the slot. A farther window gets a **toss**: a wind-up, the item tumbles along an arc into the window with a soft whoosh, and settles in as Create draws it. From where a 2×2 or bigger group is all in frame, that is every window (limitation 25); a crafter by itself is filled by hand.
4. **Taking out:** an empty hand takes the item into the hand (Shift: into the inventory); from afar it flies out to the hand.
5. **Slot covers:** a Crafter Slot Cover in hand covers an empty window, and an empty hand takes the cover off into the inventory, both through Create's own click on that crafter's front (bridge buttons `COVER` and `UNCOVER`).
6. **Refusals:** a full or covered window, or an item Create will not take, gives the deny shake. While the crafters work, every click is refused (Create's own click would throw the whole grid out).
7. **Lights:** faint lights run across each front the way its items will travel.
8. **The watch:** while any crafter of the group is at work (Create's phase is not idle), the camera draws back 0.4 and opens 4° wider over 0.8 s, and the hands sink. Create draws the items hopping from crafter to crafter. When the result forms (Create's countdown passes its middle, when the server plays `crafter_craft`), the output window flashes warm with a puff of motes. The camera comes back once all are idle.
9. Its own trigger: `[scenes] mechanical_crafter`, default **USE**. Worked from the front only.

| Part | File | What it does |
|---|---|---|
| Group | `station/CrafterGroup` | The wall (or chain) the clicked crafter is worked in, measured and in reading order; written into the menu's extra data |
| Crafter state | `station/Crafters` | Create's phase and countdown, read by reflection, fail-soft (limitation 26); covered, takes, coverable |
| Station | `station/SceneBlocks.CRAFTER` | Front only; `kindAt` gives no kind (null) where there is no scene, a group too big. `SceneBlock.kindAt` may now return null, and `Bridge` and `WorkKey` leave such clicks to Create |
| Bridge | `bridge/CrafterMenu` | One slot per crafter over its own inventory; `COVER` / `UNCOVER` buttons; opened with the group as extra data |
| Extra data | `bridge/BridgeMenu.ExtraData`, `Bridge.open` | A menu provider can now write more than the block's position for its client copy |
| Scene | `client/scene/CrafterScene` | Framing, hands, toss, take, covers, lights, the watch and the flash |
| Text | lang EN/RU | Hints (normal, no rotation, at work), the covered window's tooltip, "[H] Work at the crafters", the `mechanical_crafter` config entry, menu title |
| Dev check | `client/dev/SmokeTest` | 17 more checks: a 3×3 for a chest, a lone crafter, a 5×5, a chain six wide |

#### Acceptance (ROADMAP M5 row, DESIGN §9)

| Criterion | Result | How it was checked |
|---|---|---|
| 3×3 and 5×5 groups framed fully | ✅ | Smoke projects every window and the group's corners with the frame just drawn: all windows between the drawer and the hint line, the corners on screen. Screenshots 17 and 23 |
| Smoke fills a 3×3 recipe and sees the result | ✅ | A chest: eight oak planks round a Slot Cover. The crafters set to work, the scene watches, catches the moment the result forms (once), and the chest comes out (the player picks it up); the crafters are idle and empty after |
| Toss beyond reach | ✅ | All nine puts on the 3×3 are tosses (screenshot 18); the lone crafter is filled by hand (screenshot 22) |
| Slot cover | ✅ | Covered with the cover in hand (used up), uncovered with an empty hand (back in the inventory), covered again to finish the chest |
| The watch pull-back on crafting | ✅ | The scene watches while the crafters work (screenshot 20); the pull-back itself is seen on the screenshots, not measured |
| Groups too big fall back to Create | ✅ | A chain six crafters wide: no scene, and Create's own click puts the plank in |
| Take out, refusals | ✅ | A plank taken out of its window into the hand and put back; a full window refuses another plank |
| Dedicated server | ✅ partly | `Server` + `ClientJoin`: the server registers the crafter station, the client joins without errors, and the server receives `{…, cinecreate:mechanical_crafter=USE}`. Filling crafters on the dedicated server was not exercised (limitation 6) |

**Smoke test: 54/54 checks pass** (12 from M1, 7 for M2, 9 for M3, 9 for M4, 17 for M5; see §5 for the run log).

### 1.6 M6 — the Hand Crank, the Basin and the Blaze Burner

Three scenes (DESIGN §7.5–7.7). The Hand Crank was added to this milestone on 2026-10-22, at the user's request.

**Hand Crank** (`[scenes] hand_crank`, default USE):

1. **Turned by hand.** Holding the left button cranks, holding the right one cranks the other way. While the button is held, the scene sends a turn every 4 ticks, as holding Create's own use repeats itself. The server does each one as Create's click does: `HandCrankBlockEntity.turn(back)` (which keeps the crank generating for half a second) and Create's hunger cost (`32 × crankHungerMultiplier` exhaustion, nothing with an Extendo Grip; Create's advancement at food 0).
2. **Both hands on the grip** (the user's call: a left hand braced on the block looked like it floated). The right fist holds the grip's outer end, the left one its inner end, by the arm. Both ride Create's drawn handle exactly: the grip of Create's handle model, turned the way Create's Flywheel visual turns it (the model turned to face the crank's way, then about the axis's positive direction by `getIndependentAngle(pt)`), computed with the same JOML calls.
3. **Animated by the crank itself:** let go and the handle runs down and coasts to a stop on Create's own chase, the hands still on it. The camera bobs and rolls a little with each turn, as strongly as the handle turns; a hungry player (food under 6) bobs more and the hint says "You're getting tired". The client turns its own crank too, so Create's cranking sound plays as with its own click.
4. Camera 1.0 in front and 0.3 above the hub, FOV 70 (DESIGN's 0.75 made the block-long handle fill the frame; from farther back CI's arms, hung from the camera, reach out like poles). A crank on a floor or a ceiling is worked from any side, looking down or up at it, as the valve.

**Basin** (`[scenes] basin`, default USE; a click on the mixer or the press above it works it too):

1. **Dropping in:** the carried stack (RMB: one item) is carried over the opening and let go; it falls and settles, splashing on liquid or with Create's depot plop on the dry floor. It goes through the basin's own input inventory, which takes a new item only into its first free slot, as an item thrown in gets in.
2. **Pouring and filling:** a bucket or a bottle tips over the rim, and a stream in the fluid's colour runs into the basin; an empty one dips in and fills up. Both are Create's own click with the carried item (`Bridge.useWithCarried`: the carried stack is in the main hand for the call), so Create's rules apply, and the filled bucket goes into the inventory as Create's click puts it.
3. **Taking out:** an empty hand reaches in and takes everything into the inventory (Create's click).
4. **Heat:** a Blaze Burner below glows on the basin's floor, orange when kindled (dimmer when fading), blue when seething.
5. **The watch:** while a mixer above works, the camera rises and backs off a little, steam and bubbles come up in the liquid's colour, and every output that appears flashes in with a puff of motes. Under a press, each stroke (timed as the press scene times its impact) puffs dust, or splashes the liquid, and jolts the camera.
6. Camera steep over the rim, about 70° down (0.55 out and 1.25 up from the middle of the inside): from DESIGN's 50° the near wall hides the floor and what lies on it. The camera is free of CI's tether (a basin on a burner is higher than the player's head).

**Blaze Burner** (`[scenes] blaze_burner`, default USE; its top stays Create's, so a basin can still be placed on it):

1. **Feeding:** fuel the blaze will eat (Create's own test, tried first without doing it) is held out, drawn back and tossed through the cage into the blaze; Create munches it. A Blaze Cake makes it seethe: a blue flash, a jolt and a shimmer of heat in the view. Lava's bucket comes back to the hand, as Create's click leaves it.
2. **Lighting:** flint and steel is struck twice at an empty burner (no blaze yet: it has no block entity, so the menu checks the block); the second strike lights it, and the scene ends as the lit burner (another block) takes its place.
3. **Refusals:** anything else is refused, and the hint says "The blaze won't eat that". The hint shows the heat otherwise.
4. Crouched; camera 1.05 out and 0.6 up (from DESIGN's 0.85 the held-out hand sat on the camera).

| Part | File | What it does |
|---|---|---|
| Stations | `station/SceneBlocks.CRANK`, `.BASIN`, `.BURNER` | Crank: front only on a wall, any side on a floor or ceiling; basin: the opening only (its sides take funnels and the like); burner: any side but the top |
| Composites | `station/Composites` | `mixerOver`; `workedBy` now gives the basin under a press or a mixer too |
| Bridges | `bridge/CrankMenu`, `BasinMenu`, `BurnerMenu` | Crank: buttons TURN / TURN_BACK; basin: 9 input slots over Create's input inventory, 9 take-only outputs, button USE; burner: button FEED + side |
| Use with what is carried | `bridge/Bridge.useWithCarried` | Create's own click done with the scene's carried stack: pour, fill, take all, feed, light |
| Scenes | `client/scene/CrankScene`, `BasinScene`, `BurnerScene` | As above |
| Text | lang EN/RU | Hints, heat names, invitations, config entries, menu titles |
| Dev check | `client/dev/SmokeTest` | 21 more checks; `-PsmokeFrom="<step>"` starts a run at a step after the set-up |

#### Acceptance (ROADMAP M6 row, DESIGN §9)

| Criterion | Result | How it was checked |
|---|---|---|
| Crank: held, it turns the shaft behind both ways and stops when let go; the hands on Create's drawn knob | ✅ | Smoke holds each button 30 ticks: the encased shaft behind runs at −32 / +32 RPM, and stands still after; the handle turns on a little after letting go. The hands follow by construction (Create's own transform); screenshots 24–25 at two angles show both fists on the grip |
| Crank: Create's hunger cost; tired when hungry | ✅ | Exhaustion is exactly 0.32 per turn; at food 2 the hint says the player is tired |
| Basin: drops, pours, takes | ✅ | A water bucket pours 1000 mB in and comes back empty; the empty bucket fills up again (into the inventory); andesite and an iron nugget dropped in; an empty hand takes everything out |
| Basin: watching the mix | ✅ | The mixer comes down, the scene watches, and the andesite alloy appears and flashes in (screenshot 27) |
| Basin: heat glow by level | ✅ | After the burner seethes, the basin's floor glows blue (screenshot 30) |
| Compacting variant | ✅ by construction | Strokes timed as the press scene's impact (checked in M3); not set up in the smoke (limitation 32) |
| Burner: feeding, the blaze-cake flare, lighting, refusals | ✅ | Dirt refused with the hint; coal kindles it (KINDLED), a Blaze Cake makes it seethe (SEETHING); flint and steel lights an empty burner (screenshots 28–29) |
| Dedicated server | ✅ partly | `Server` + `ClientJoin`: the server registers the crank, basin and burner stations; the client joins without errors, and the server receives all seven triggers. Working these blocks on the dedicated server was not exercised (limitation 6) |

**Smoke test: 75/75 checks pass** (12 from M1, 7 for M2, 9 for M3, 9 for M4, 17 for M5, 21 for M6; see §5 for the run log).

### 1.7 M7 — Wave 2: saw, millstone, deployer, ejector, fan, drain, spout

Five new scenes and three depot composites (DESIGN §7.8–7.13), each with its trigger in the config (all default USE).

**Depot composites.** A depot plays the scene of the machine working it, decided when the scene starts: a press (M3), then a Spout two blocks up, then a Deployer pointing down two blocks up, then an Encased Fan's stream that does something at the depot; otherwise the plain depot. A click on the spout or the downward deployer works the depot under it, as the press does.

1. **Spouting** (`spouting`): the bottle is set on the plate as on any depot; while the spout pours (Create draws the nozzle), droplets bead on it in the liquid's colour, and the tick Create fills it, it flares in that colour (CI's brewing flare) with a faint flash.
2. **Deploying** (`deploying`): the deployer's punch (the frame Create's hand turns back from past half its reach) jolts the camera (kick 0.25) and throws warm sparks; when the work changes under it, it glows briefly.
3. **Bulk processing** (`fan_processing`): a steady wind tremble (kick 0.08) and FOV −2 while the stream blows; particles in the stream's kind (washing motes, smoke, blasting sparks, soul motes) while Create can process what lies there, and a flash in its colour when the result appears; for blasting and haunting the left hand shields the eyes. Which depots a stream works is Create's own rule, followed exactly (`Composites.fanStream`): the current's length rounded up (a depot stops the air at its face and is still worked), and the block under a sideways current too; pushing or pulling.

**Item Drain** (`item_drain`): a filled container is laid on the far side of the grate (through Create's own side input, as a belt would put it in), so Create rolls it to the middle, drains it while the liquid spirals down in its colour, and rolls it on toward the player and off the near edge, where the right hand reaches out to catch it. One filled container at a time; an empty hand takes back what lies there.

**Deployer hand-off** (`deployer`, a deployer facing sideways): the hand holds the item out before the claw, reaches it at the beat (0.30 s) and draws back with whatever the deployer held: Create's own click on the deployer's hand, so the swap is Create's.

**Millstone** (`millstone`): what is carried is tipped over the mouth and four pieces trickle in (the mouth is Create's own input, which takes only what the stones can mill: anything else is refused). While the stones grind, flour dust puffs from the gap between them, more the faster they turn, with a faint constant tremble. An empty hand reaches to the side and gathers what is ground (Create's own empty-handed click). "The millstone isn't turning" when it stands still.

**Mechanical Saw facing up** (`mechanical_saw`, the stonecutter of Create): the shapes it can cut hang in CI's stonecutter arc behind the blade, for what lies on the table or, with the table empty, for what the hand carries. Touching a shape sets the saw's filter to it (Create's own `setFilter`), touching it again clears it, and the wheel steps through them; the chosen one is ringed and the others dim. Laid on the table, the work is pushed along by one hand just behind where Create draws it, from the edge it comes in at to the middle as it is cut; the other hand waits at the far edge for the pieces. Dust sprays in the stone's map colour, as fast as the blade turns.

**Weighted Ejector** (`weighted_ejector`): the depot scene on the ejector's plate. When the plate snaps (Create's state leaving CHARGED), the camera kicks (0.40). ~~It then let go of the player's head and glided after the item.~~ Since §1.9 the player stays and only turns their head after the throw. Only with "Watch launches" on (`followLaunches`, default on) and a target within 32 blocks.

| Part | File | What it does |
|---|---|---|
| Kinds | `station/SceneKinds` | spouting, deploying, fan_processing, item_drain, deployer, millstone, mechanical_saw, weighted_ejector |
| Stations | `station/SceneBlocks.DRAIN`, `.DEPLOYER`, `.MILLSTONE`, `.SAW`, `.EJECTOR`; the depot's kinds | Drain: the grate, from any side; deployer: its front, only when it faces sideways; millstone: any side; saw: any side, only facing up; ejector: the plate (it shares the depot's bridge) |
| Composites | `station/Composites` | `spoutOver`, `deployerOver`, `fanStream`; `workedBy` gives the depot under a spout or a downward deployer |
| Bridges | `bridge/DrainMenu`, `DeployerMenu`, `MillstoneMenu`, `SawMenu` | Drain: the grate slot through Create's side input; deployer: button SWAP; millstone: the mouth over Create's input, 9 take-only outputs, button TAKE_ALL; saw: the table slot, buttons TAKE_ALL / ANY_SHAPE / SHAPE + i |
| Scenes | `client/scene/SpoutScene`, `DeployingScene`, `FanScene` (on `DepotScene`), `DrainScene`, `DeployerScene`, `MillstoneScene`, `SawScene`, `EjectorScene` | As above |
| Config | `client/ClientConfig` | `followLaunches` |
| Text | lang EN/RU | Hints, invitations, config entries, menu titles |
| Dev check | `client/dev/SmokeTest` | 23 more checks, one block per machine |

#### Acceptance (ROADMAP M7 row, DESIGN §9)

| Criterion | Result | How it was checked |
|---|---|---|
| Drain | ✅ | A water bucket laid on the grate rolls in and is drained (server: 1000 mB), and the empty bucket rolls back out to the player and into the inventory (screenshot 31) |
| Spout over a depot | ✅ | A glass bottle on the plate: the spout pours and the bottle flares as it fills (server: a water bottle; screenshots 32–33) |
| Deployer over a depot ("Application") | ✅ | Andesite alloy in the deployer, a stripped log on the plate: one punch, and the log becomes andesite casing (server; screenshot 34) |
| Deployer hand-off | ✅ | Andesite handed over, the flint it held taken back (server: the deployer holds the andesite; screenshot 35) |
| Fan composite | ✅ | A depot in a fan's stream through a lit campfire has the smoking scene; raw beef on the plate is smoked (server: cooked beef; screenshot 36). The fan is checked to blow its air out |
| Millstone | ✅ | Wheat poured into the mouth is ground with flour dust from the gap; an empty hand gathers the wheat flour (screenshot 37) |
| Saw | ✅ | Stone in hand: 7 shapes on the dial, stone bricks among them; touching them sets the saw's filter; laid down, the stone is pushed and cut, dust spraying, into stone bricks (screenshots 38–39) |
| Ejector | ✅ | Iron on the plate: the plate snaps, the camera follows the throw to where it lands (server: the iron lies by the target) and comes home (screenshots 40–41) |
| Hands driven by machine progress | ✅ | The saw's pushing hand follows Create's drawn work; the deployer's claw and the spout's droplets and flare follow Create's state |
| Dedicated server | ✅ partly | See §5. Working these blocks on the dedicated server was not exercised (limitation 6) |

**Smoke test: 98/98 checks pass** (12 from M1, 7 for M2, 9 for M3, 9 for M4, 17 for M5, 21 for M6, 23 for M7; see §5 for the run log).

### 1.8 After M7: Create's value boxes stay Create's

Reported by the user on 2026-10-23: some blocks have zones that open Create's own setting when clicked (the Weighted Ejector's stack size, a filter slot such as the saw's or a deployer's, the valve's angle), and a click there started the scene instead, so the block could not be configured without switching its scene to the key first. Two paths did it: M4's rule that a value-box click on one of our blocks starts the scene (meant for the valve's hub, whose scene offers the same angle), and the click on a machine working a depot (a deployer pointing down), which started the depot's scene wherever it landed on the machine.

The fix is one rule for every block, in `Bridge.onValueBox`: a click that Create's own input would take, on a value box of any kind (a value setting, a filter, a sided filter: Create's `ValueSettingsInputHandler`) or a redstone-link frequency slot (`LinkHandler`), is Create's. It is decided as Create decides it (the held item, sneaking, the wrench-only boxes, the side the box is on), the same way on both sides, so the box gets the click whatever order the handlers run in. CI's click handler leaves such clicks alone (`leaveToCreate`, hook 3), and so does our own HIGH-priority rule for machines working our blocks (`onUse`); value boxes no longer start a scene. This is what DESIGN §6 had specified for hook 3; M4's hub exception is gone: the valve's hub now opens Create's angle setting, and the rest of the wheel starts the scene.

| Criterion | Result | How it was checked |
|---|---|---|
| The valve's hub is Create's; the wheel starts the scene | ✅ | Smoke right-clicks the hub: Create's value input takes it, and no scene starts; then a click on the wheel starts the valve scene |
| The ejector's stack size is Create's | ✅ | Smoke finds the box where Create places it (its front face), right-clicks it: Create's value input takes it, no scene |
| A saw's filter is Create's | ✅ | Stone clicked onto the saw's filter box sets its filter (server), no scene |
| A deployer working a depot keeps its filter | ✅ | A stripped log clicked onto the deployer's filter box sets the filter (server), and the depot's scene does not start |

Smoke after the fix: 104/104 (M4 has two checks more, M7 four more).

### 1.9 After M7: the ejector watched, no view through blocks, notices, 10×10 crafters, the Extendo Grip

The user's second round of notes, on 2026-10-23. The Extendo Grip design was settled with the user first: work from where you stand, hang from a high basin, re-plan when the grip is put away, and the grip's hand at every machine.

1. **Ejector: the head turns, the camera stays.** The flying camera is gone. When the plate snaps, the player stays leaning over it, and only the look turns after the item: it follows Create's path, rests where the item lands, and comes back (DESIGN §7.12). A neck turns only so far, at most 110° to either side, and a far throw is watched a little narrower.
2. **Blocks where the player would stand.** Blocks set one space in front of the crafters, where the player stands for the scene, let the scene play with the player behind them and the view on the other side: the crafters' free camera never checked that the player's eyes could get to it. Now a scene whose camera stands away from the head (the crafters' framing, the basin's look over its rim) is refused when a block is between the player's eyes and that camera (`Views.reachable`). In a one-block gap the scene still plays, with the view in the gap.
3. **Notices** (DESIGN §8.4). Cards that slide in at the right edge say why a scene did not start or why it ended: CI's refusal reasons, made specific where our scenes know better (a block where you'd stand, a block between you and the view, the crafters' height rule, "an Extendo Grip would reach it"). CI is silent when its height logic falls back to Create's click; a fourth, optional, client hook (`SceneRefuseMixin` on `Scene.refuse`) brings the card.
4. **Crafters up to 10×10**, as Create builds them (5×5 before). The framing's field of view stops at 84°, and the camera stands farther back instead: 7.5 for a 10×10. A group is worked when its bottom row is within the player's height (the windows above are tossed to). The "too wide for a scene" smoke step is gone: the user asked not to build larger than 10×10.
5. **The Extendo Grip** (DESIGN §8.5). With one in either hand, every scene gets Create's own extra reach (+3, +5 with two, capped by the server's 6-block limit). Within that reach, a scene is worked from where the player stands. The hand holding the grip stays by the body, and the grip opens out to the work, a scissor lattice in Create's own texture with the player's hand at its end. That hand sets down and takes, turns valves and cranks, pushes stone into the saw, feeds the blaze through the cage, puts items straight into far crafter windows, and hands over claw to claw with a deployer.
   - **The high basin:** a basin too high to lean over is hung from, the grip hooked on its rim, the view over the rim swaying a little.
   - **The grip put away mid-scene:** if the scene needed it, the scene starts again without it (the player walks up, and a card says so). Hanging from the basin, the player drops: the view only, no fall damage.
   - All scenes now share a base class, `CineStation`, which adds the grip to each scene's own hands, rests, approach and drawing.

| Part | File | What it does |
|---|---|---|
| Ejector | `client/scene/EjectorScene` | Head-turn watch instead of the camera flight |
| Views | `client/scene/Views` | `reachable` (the view from the eyes), `noteStand` (a block where the player would stand, for the card) |
| Notices | `client/Notices`, `mixin/SceneRefuseMixin` | The cards; CI's refusals turned into cards for our blocks |
| Crafters | `station/CrafterGroup.MAX` = 10, `client/scene/CrafterScene` | Framing up to 10×10; its own refusal reasons; with the grip, windows within reach filled by its hand |
| Grip | `client/scene/Grip`, `GripArm`, `CineStation` | Reach, drawing, and the hands for every scene |
| Burner | `client/scene/BurnerScene` | With the grip, fuel carried in through the cage instead of tossed |
| Basin | `client/scene/BasinScene` | With the grip, the hang (hook, sway, drop) and work from afar (camera at the eyes) |
| Text | lang EN/RU | Card reasons; the grip's cards (Create's Russian name, «Длиннохват»); "Watch launches" |
| Dev check | `client/dev/SmokeTest` | Ejector checks rewritten; the blocked-stand, 10×10 and grip blocks (12 more checks than §1.8) |

| Criterion (the user's notes) | Result | How it was checked |
|---|---|---|
| Ejector: head turns, no flying | ✅ | The look turned 81° after the iron while the eyes moved 0.01 blocks; no free camera; the look comes back after the landing |
| Blocks where the player stands: no scene through them | ✅ | Stone one space in front of the crafters, player behind it: refused, the card names the stone; from on top of the stone, "too low"; from the gap, the scene plays with its view in the gap |
| Hints on screen, animated, out of the way | ✅ | Cards at the right edge (screenshot 42), checked in the blocked-stand and grip steps |
| Crafters to 10×10 | ✅ | A 10×10 wall is one group, framed whole clear of the drawer and hint line (screenshot 23) |
| Extendo Grip: longer reach for every scene | ✅ | A depot 4.5 blocks off worked without walking; the grip's hand sets an iron down (screenshot 43); a valve turned from 3 blocks (screenshot 45) |
| Grip put away: reset, or a fall | ✅ | The depot scene starts again and the player walks up (card); hanging from a basin 2.1 up, letting go drops the player (card) |
| Basin: hang from it | ✅ | Bare-handed, refused with "an Extendo Grip would reach it"; with the grip, hanging, the bare hand drops andesite in (screenshot 44) |

Smoke: 116/116 in a maximized window (5½ minutes).

### 1.10 After M7: big crafter walls with the grip, a section at a time, and Create's reach

The user's third round, on 2026-10-23.

1. **No flying at big crafters.** The crafters' camera used to stand wherever the whole group fit, which for a 10×10 meant floating 7.5 blocks in front of the wall and halfway up it. Now the view is always the player's own eyes. By hand, the player steps back until the group fits, and the view tilts to it instead of rising (crouched for a low group).
2. **Big walls are the grip's.** By hand only up to 3×3, from its bottom row up to 1.5 above the eyes. A bigger or higher group is refused without an Extendo Grip, and the card says to hold one.
   - **Standing.** With the grip, the player stands on the ground where they reach the clicked crafter, and sees the wall a section at a time: up to 3×3 windows, overlapping evenly.
   - **Turning the head** between sections takes 0.35 s, with W/A/S/D or the arrows, the wheel (Shift: sideways), or the cursor resting at an edge (a faint arrow fills there). A small map at the right edge shows the wall and the section looked at.
   - **Reach.** The grip's hand puts items into every window within reach. Windows out of reach are shaded red and refused with a card ("stand closer or higher"): from the ground with one grip, the top rows of a 10×10 are out of reach, as in Create.
3. **The grip's reach is Create's.** A scene is worked from where the player stands only when their block interaction range reaches the block, measured as the game measures a click from the eyes to the block, Create's grip modifier included. It must also be within the 6 blocks the scene server allows. Before this, the scenes used their own rule (1.9 + 3 from the eyes to the work's middle). It never reached beyond Create's, but it wasn't Create's rule.

| Criterion (the user's notes) | Result | How it was checked |
|---|---|---|
| No flying or hovering at big crafters | ✅ | With the grip at a 10×10, the camera is 0.00 from the player's eyes, with no free camera; by hand, the 3×3, the lone crafter and the blocked-stand checks pass with the view at the eyes |
| Big crafters only with the grip | ✅ | The 10×10 by hand: refused, the card asks for an Extendo Grip; with the grip: the scene (screenshot 23) |
| Turning the head between sections to reach the slots | ✅ | W twice turns the head two sections up the wall (pitch −1° to −62°), the eyes where they were (screenshot 23b); the grip fills a window low down and one up there |
| The grip's range as in Create | ✅ | Every reach check is the player's block interaction range; the top window of the 10×10 is beyond it from the ground and is refused with its card |

Smoke: see the run log.

### 1.11 After M7: the grip drawn as Create's, no second arm

The user's report, on 2026-10-23: approaching a basin with an Extendo Grip, the left arm looked doubled, as if a second arm grew from it.

The cause was the grip's tip. It was drawn as the player's own skinned arm, while Create draws a deployer hand there. Close to the work (a basin leaned over, its rim right under the shoulder), the grip was only a few tenths of a block long. Its skinned forearm then lay along the real arm. CI also draws a scene's world only while the scene is under way, but the arms in every phase. So the grip popped in at full length, its arm already on the rim, while the real hand was still coming up.

1. **Create's own grip.** Create's handle model is in the fist, its cog turning. The lattice opens out from it, its links spreading wider as it folds. At the end is the claw: Create's deployer hand, pointing when it reaches, holding when it carries or holds on, a fist at rest. What the working hand carries is in the claw; a dropped item now falls from the claw, not from the fist.
2. **Folded, never shorter.** From the fist to the claw's palm the grip is at least 0.45. Close to the work, the fist rests where the bare hand would, and the folded grip points ahead.
3. **With the hand.** The grip is drawn with the arms (CI's body hook, every phase). It comes up with the hand and goes down with it. The claw keeps its place from the fist, and the lattice eases out and in as the work moves.
4. **The hook.** Hanging from a basin, the left fist is below the rim outside, and the claw's palm is on the wall with its fingers over into the basin.

| Criterion (the user's report) | Result | How it was checked |
|---|---|---|
| No doubled arm at the basin with the grip | ✅ | Leaning over a basin with the grip in hand, the grip is never drawn shorter than folded, fist to claw (0.45). The claw is Create's deployer hand, not an arm (screenshots 46–48). Hanging, the fist is 0.36 below the claw on the rim (screenshot 44) |
| The grip comes up with the hand | ✅ | The grip is drawn while the hands rise, both leaning over the basin and hooking on (screenshots 44a, 46) |
| Visuals updated | ✅ | Create's handle, cog and claw at the depot and the valve from afar (screenshots 43, 45); the claw drops the andesite into the basin |

---

## 2. Known limitations

| # | Limitation | Plan |
|---|---|---|
| 1 | **Size jump at set-down.** CI draws the carried item small; Create draws a depot stack about twice as big. The hook CI uses to scale a carried item (`SpatialTarget.layoutHeldScale`) is package-private | Ask for a `heldItemScale()` hook in the upstream PR (§4 A). Meanwhile, possibly a short scale tween on the ghost |
| 2 | **Brief double image when taking.** For about 1–2 ticks, until Create's sync arrives, the stack shows both in the hand and on the plate | Hide Create's depot render for that block while a take is pending (a small client mixin on `DepotRenderer`), or accept it |
| 3 | CI calls `renderScene` **twice per frame** for add-on stations (`MenuRenderer` and `SceneRenderer`) | Guarded with `FrameCounter`; report upstream |
| 4 | ~~**Every** top-face right-click starts the scene; there is no KEY policy yet~~ | ✅ Done in M2: per-scene trigger, the bare depot defaults to KEY |
| 5 | ~~A depot under a press (✅ M3), deployer, spout or fan gets the plain depot scene; a basin under a press has no scene~~ | ✅ M6 (basin), M7 (deployer, spout, fan) |
| 6 | The smoke test runs only in singleplayer (integrated server). For M2 this means the KEY right-click is checked against the integrated server's copy of the prefs, not a dedicated server's; for M3, pressing is not exercised on a dedicated server | §4 C: a dedicated-server variant |
| 7 | The hooks target CI internals; CI ships several builds a day. The dependency is pinned to `[0.18.12, 0.19)` | Run `runSmoke` on every CI update; push the upstream API PR so the hooks can go |
| 8 | One NeoForge early-window crash at launch (`FileSystemNotFoundException` in `fml_earlydisplay`); not reproduced since | If it recurs: set `earlyWindowControl = false` in `run*/config/fml.toml` |
| 9 | The fallback window is a plain panel | Fine unless players actually see it; restyle in M9 |
| 10 | The release jar includes `client/dev/SmokeTest`. It does nothing without `-Dcinecreate.smoke=true` | Exclude `**/client/dev/**` from the `jar` task in M9 |
| 11 | The controls screen shows **H in red**: our key and CI's "Sit by the fire" share it on purpose (both are in-game keys). Both still work | Accept. If players find it confusing, use a key conflict context that only conflicts with itself (this would also hide real clashes), or propose one shared "cinematic action" key upstream |
| 12 | The key and the invitation check reach with a copy of CI's private `Scene.choose` (sides by nearness, then beside the work). The result is cached per block and the block the player stands on, so it can be stale if blocks change while the player stands still (CI's campfire invitation has the same limit) | Ask for a public `Scene.canBegin(pos, kind)` in the upstream PR (§4 A) |
| 13 | OFF stops "moving over" by giving the station **no working sides on the client thread**. This relies on CI asking `Definition.sides` through `StationKind.usable`. If anything in CI ever called `Scene.begin` for an OFF block, its `freeSide` would throw on the empty list (nothing does in 0.18.12). Since M3 one block can play several kinds (the depot five since M7: bare, pressing, spouting, deploying, fan), and CI passes only the block state, so the sides are taken away only when **every** kind of that block is OFF. With the bare depot OFF but another of its kinds on, another scene can still offer to move over to that depot | `startsOnUse(player, state, hit)` in the upstream PR replaces it (or a client mixin on `Scene.findNeighbor`, if it matters before then); `runSmoke` checks the all-OFF case on every CI update |
| 14 | The server assumes USE until the client's prefs arrive (sent at login). A right-click in that first moment could be decided differently by the two sides, losing at most that one click | Accept |
| 15 | The policy is a client preference only: a server cannot force scenes off | §4 F (server config) |
| 16 | **Pressing camera.** The DESIGN §7.2 pose must fit the plate and the press body into a 66° view, so the plate sits at the bottom edge with the hands and the middle of the frame is the head's travel | Tune by eye in play (eye height, distance, look point); with CI Developer Mode for add-ons (§4 H), players could tune it too |
| 17 | The server runs a few ticks ahead of the client. When its impact packet arrives first, Create snaps the head onto the plate, and the impact lands on that frame. The result can show on the impact frame itself | This is how Create draws it; nothing to do |
| 18 | "Below 1 RPM: deny + hint" (DESIGN §7.2) is done as hint + drumming only. Laying a stack on the depot of a stopped press is still a valid depot action, so it is not refused | Revisit if players expect a refusal |
| 19 | The result effect fires on any change of the depot's contents after an impact. A player taking the stack in the same moment would also get the motes and chime | Rare; accept |
| 20 | In the first M3 smoke run the pressing scene ended by itself while the harness waited 20 s; nothing in the log explained it. Seven later runs did not reproduce it, including runs with all advancements revoked and a 10 s watch check | The watch check stays in the smoke; look out for it in play (Watch scenes in M5/M6 stay much longer) |
| 21 | DESIGN §7.4 put the wheel's centre at `pos + 0.5 + FACING·5/16` and the camera 0.8 out. Create's model has the rim 5–8 px out from the face the valve is fixed to (centre `pos + 0.5 − FACING·1.5/16`), and at 0.8 the wheel filled the frame. The scene uses the model's numbers and 1.2 | DESIGN §7.4 corrected |
| 22 | The valve's turn length on the client is an estimate of Create's (`ceil(angle / 9.6°) + 2` ticks), used for the camera roll only. The hands and the clunk follow Create's synced state | Accept |
| 23 | The overstressed and "driven by something else" refusals rely on the client's copy of Create's kinetic state, and the smoke does not set them up | Check by hand: a valve on a network with a motor; a valve on an overloaded network |
| 24 | Clearing the crosshair target during our scenes also hides any other mod's tick-time crosshair overlays while the scene plays (the scene hides the crosshair anyway) | Accept; narrow it to Create's value boxes if a mod needs the target during scenes |
| 25 | From where a 2×2 or bigger crafter group is all in frame, no hand reaches a window: CI's arms hang from the camera, not from the player's body, so every put there is a toss. Only a crafter by itself is filled by hand. DESIGN §7.3 expected tosses for the far windows only | Accept for now. If players miss the hand: lean the camera in toward the window for each put (costs camera motion per item) |
| 26 | The crafter's phase and countdown are protected fields (the phase of a package-private type), read by reflection. If Create renames them, the scene logs it once and stops watching; filling, taking and covers still work | Check on each Create update (as DESIGN §10 says for 6.0.11); ask Create for a public getter |
| 27 | Our put goes into the clicked window's own crafter, also when crafters are wrench-connected; Create's own click fills a connected group in its order instead | Accept: in the scene the player picks each window |
| 28 | The framing assumes CI's "scene zoom" at 100%. Below that, CI blends the scene's field of view toward the player's own, and a narrow one could cut the group's edges | Accept; read the setting if players report it |
| 29 | The drawer's and the hint line's heights (46 and 24 GUI pixels) were measured on screenshots, not read from CI | Ask CI to expose them in the upstream PR (§4 A) |
| 30 | A wall holding several separate recipe chains is framed as one group, and the watch starts when any of them works | Accept |
| 31 | Shift-click in the drawer fills the empty windows in reading order, one item each (the vanilla quick move), without an animation per window | Accept, it is a handy quick fill; animate later if wanted |
| 32 | The compacting variant (a press over a basin) is timed like the press scene's impact, but the smoke does not set it up | Check by hand: a press over a basin with a compacting recipe |
| 33 | A dropped item settles in the middle of the basin; Create then draws it in its own ring, so it may hop a little when Create's copy arrives | Accept |
| 34 | Taking out of a basin, and filling a bucket from it, put things into the inventory, not into the hand: that is Create's own click | Accept: the drawer shows them |
| 35 | The basin's heat glows on its floor: the burner's own grate is out of sight under it | Accept |
| 36 | The crank's server takes every turn it is sent: a time limit dropped turns when a slow server handled several packets in one tick. A modified client could only crank no faster and spend its own hunger | Accept |
| 37 | Lighting an empty burner turns it into another block (Create's lit burner), so the scene ends there, with no look at the new flame | Accept; a short outro on the lit burner later, if wanted |
| 38 | IntelliJ was open on the project during M6 and M7 (it once reverted files in M3). Every edited file was checked before each build; nothing was lost | Keep IntelliJ closed while sessions edit `src/` |
| 39 | Create 6.0.10's Item Drain never fills a container from its tank on a click (it only empties into it; its liquid goes to pipes). DESIGN's "fill from the drain" was dropped: an empty bucket laid on the grate just rolls through | Accept: that is Create's drain |
| 40 | The deployer's punch sparks are warm, not tinted with the applied item: the deployer's held item is not readable on the client | Accept, or read it from Create's synced data if it matters |
| 41 | The ejector follow plays Create's planned path to the target; an item Create stops early (it hits something on the way) is followed to the planned end anyway | Accept; rare in a built line |
| 42 | Create moves things across a saw along its own axis and blade direction, not the player's. When they come in on the far side, the pushing hand reaches over the blade | Accept: which way the saw cuts is Create's |
| 43 | A depot upwind of a fan's catalyst (a pulling fan, or the catalyst past the depot) is in the stream but not processed, and keeps the plain depot scene, as Create leaves it unprocessed | By design |
| 44 | A fan blowing up into a campfire is stopped by the campfire's full bottom face (Create's air rule), so it reaches no depot above; the smoke test's rig blows sideways | Create's rule; nothing to do |
| 45 | Only a deployer facing sideways has the hand-off; one facing up has no scene, one facing down works the depot under it | Accept |
| 46 | ~~The hanging basin's hook is short, so the grip mostly hides behind the left arm~~ | ✅ §1.11: the fist hangs below the rim and Create's claw holds the rim |
| 47 | ~~With a grip, the crafters' framing camera still stands where the group fits~~ | ✅ §1.10: the view is always the player's eyes; big walls are seen a section at a time |
| 50 | Sections are seen from where the player stands: at a steep look up (the top of a 10×10 from close by), the windows are foreshortened | Accept: it is what the player would see; stepping back (out of reach) is the trade-off |
| 51 | Turning the head by the cursor at an edge also happens while the player only means to reach for a window near the edge (after 0.35 s) | Accept; the keys and the wheel turn without it |
| 48 | The grip's hand positions come from CI's internals by reflection (`Hands.position`); if CI renames it, the grip is drawn from the hands' targets (it lags a little behind the arm while it moves) | Check on CI updates; ask for a public getter in the upstream PR (§4 A) |
| 49 | A card for a scene CI refuses relies on the optional `Scene.refuse` hook; without it (CI changed), refusals are silent again, as CI's height logic makes them | `runSmoke` checks the cards on every CI update |

---

## 3. Next milestones

Sizes: S ≈ one session, M ≈ two or three, L ≈ a week of sessions. Every milestone ends with `runSmoke` extended for it, plus a dedicated-server check with `Server` + `ClientJoin`.

| # | Milestone | Size | Depends on | What it adds | Done when |
|---|---|---|---|---|---|
| **M2** ✅ | Trigger policy + config | M | M1 | Client config (NeoForge `ModConfigSpec`): **USE / KEY / OFF** per scene. A `cinecreate:scene_prefs` C2S payload so the server's hook 3 decides the same way. Key "Work at the machine" (default **H**, CI's campfire key) calls `Scene.begin(pos, ADDON)`. "[H] Work at the depot" invitation under the crosshair. Defaults: bare depot **KEY**, everything else USE | Switching policy applies without relogging; in KEY mode a right-click is pure Create on a dedicated server; H starts the scene; OFF never starts one |
| **M3** ✅ | Pressing composite | M | M1, M2 | Depot (and the press itself) under a Mechanical Press → the press scene (DESIGN §7.2). Beats read from `PressingBehaviour.runningTicks`; flinch, kick, sparks, flash; "needs power" hint | Impact lands within one frame of Create's head; ≤ 1 kick per 0.35 s at 256 RPM; smoke builds press + motor and screenshots the impact |
| **M4** ✅ | Valve Handle (17 colours) | M | M1 | `ValveMenu` with button actions (turn ±, set angle); grips follow `getIndependentAngle`; hand over hand past 90°; angle scroll and readout; deny while turning or overstressed | Smoke turns a valve on a shaft and checks the angle Create stored |
| **M5** ✅ | Mechanical Crafter | L | M1 | Group bridge (one slot per crafter in the connected group); framing camera; toss beyond reach; slot covers; watch pull-back during crafting | 3×3 and 5×5 groups framed fully; smoke fills a 3×3 recipe and sees the result |
| **M6** ✅ | Basin + Mixer + Burner, Blaze Burner, **Hand Crank** | L | M3, M4 | Drop / pour / take, heat glow, mixing watch, compacting variant; burner feeding and the blaze-cake flare. **Hand Crank** (added 2026-10-22, DESIGN §7.5): held LMB / RMB turns it by hand through Create's own `turn(back)` (and its hunger cost), the hand rides the knob at Create's rendered angle, the crank coasts when let go, the camera bobs with each turn, fatigue when hungry | Per DESIGN §7.6–7.7; crank: smoke holds a turn and sees the shaft behind run both ways and stop when let go, with the hand on Create's drawn knob |
| **M7** ✅ | Saw, Millstone, Deployer, Ejector, Fan composites, Drain / Spout | L | M3 | Per DESIGN §7.8–7.13; hands driven by machine progress | Per spec; one smoke block each |
| **M8** | Wave 3 (native Create menus and short scenes) | L | M2 | Toolbox, Schematic Table, Schematicannon, Lectern Controller, Clipboard, Table Cloth, Packager / Postbox, Desk Bell, Analog Lever, Seat, Item Hatch, Backtank | Native menus open with Create's extra data; no client crash on fallback |
| **M9** | Polish & release | M | all | EN/RU pass, Iris check, two-player dedicated-server test, mod page that says both sides are needed, offer the upstream PR | Release build on CurseForge / Modrinth |

**Suggested order:** M8 next (M2–M7 are done). M2 came first because the "repetitive after 20 minutes" feedback (DESIGN §3) applies to depots more than to anything else. M3 is the showcase moment, and M4 proves the action-button bridge that M5–M7 reuse.

---

## 4. Later: ideas worth doing

### A. Upstream (Cinematic Interactions)

The CI author asked for add-on ideas and said they don't know what to do with Create. One PR to `dcvke/CinematicInteractions` could retire all three of our mixins. It would add to `AddonStations.Definition`:

- `opener(ServerPlayer, BlockPos)` — open the menu, including extra data
- `vanillaUse(ServerPlayer, BlockPos)` — what a "window" means for menu-less blocks
- `startsOnUse(player, state, hit)` — a held-item / face / policy predicate on both sides
- `heldItemScale()` — limitation 1
- `Scene.canBegin(pos, kind)` — whether a scene can start here, for our key and invitation (limitation 12)
- the inventory drawer's and the hint line's heights, so a scene can frame its work clear of them (limitation 29)
- per-add-on toggles in CI's settings screen

The same PR should fix the double `renderScene` call and make `Scene.sound`, `MenuStation.frame` and `Hands.tap` protected or public. Offer it after M2 or M3, when it can point at a working add-on.

### B. Multiplayer

- **Onlooker FX:** press impacts and valve strain shown to nearby players, through our own S2C payload keyed to Create's block entity state (CI's API has no observer FX yet).
- **Scene-specific poses for onlookers:** gripping the valve, leaning over the basin. This needs CI's pose API.

### C. Testing

- **Dedicated-server smoke:** the same steps against `run-server`. Setup through commands (Dev as op on the dev server); checks read the client's synced state.
- **GameTests for the bridge menus** (server-only, no rendering): insert, take, quick-move, stack limits, outputs never accept anything. Then `runGameTestServer` becomes a CI job (GitHub Actions already runs `build`).
- **Screenshot diffs:** keep reference frames per scene and flag big pixel changes after a CI or Create update.

### D. Content and compatibility

- **Depot-like blocks from other Create add-ons,** via a block tag `cinecreate:depot_like`.
- **Create: The Factory Must Grow** — MCP: Utils already depends on it: a survey of its interactive machines.
- **Data-driven scene tuning** (camera offsets, timings) in JSON, so modpacks and resource packs can adjust without code.

### E. Look and feel

- A sound pass (quiet local cues, DESIGN §8).
- The held-item scale fix (limitation 1) and the take double image (limitation 2).
- An Iris / Oculus shader check.
- A "reduced motion" preset (shake, flash and bars off; fast lean).
- Vivecraft: TikTok viewers asked for VR. Investigate whether CI's camera override coexists with VR at all.

### F. Server and admin

- A **server config** that turns scenes off for everyone, per block (event servers, minigames).
- Optionally a permission node, if the Minechestplate server wants to gate scenes.
- Performance budgets per scene (sparks per second, kicks), already set in DESIGN §8; verify them with many players at one machine.

### G. Release

- A mod page built from smoke-test screenshots (the smoke run can also record frame sequences for GIFs).
- A changelog, and a version scheme tied to the CI range it supports.

### H. CI Developer Mode

- Let players move our scenes' item spots and camera with CI's in-world layout editors. This needs CI to expose layout profiles to add-ons (its guide lists this as future API work).

---

## 5. How to work on a milestone

1. Implement against the DESIGN.md spec for that block.
2. Extend `client/dev/SmokeTest` with the new steps: set up the blocks on the integrated server, drive the scene, check the server state, take screenshots.
3. Run `gradlew runSmoke` (about 5 minutes since M7; `gradlew runSmoke -PsmokeFrom="<step name>"` does the set-up and then starts at that step, for working on one block). Read `run-smoke/smoke-results.txt` and look at `run-smoke/screenshots/`. In the harness, wait on state, never on a fixed number of ticks: scene moves run on CI's frame clock (slow frames stretch them, and CI drops clicks while one plays), and the server runs a few ticks ahead of the client. The log traces every scene start and end (`scene X -> Y`) with the step and the player's state.
4. Check by hand in singleplayer (`Client`), then on the dev server (`Server` + `ClientJoin`, two clients for anything onlookers should see).
5. Tick the milestone in DESIGN.md §9 and update this file's limitations list.

**M1 smoke runs** (2026-10-21):

| Run | Result | Note |
|---|---|---|
| 1 | Crashed while launching | NeoForge early-window `FileSystemNotFoundException`, before any mod code ran (limitation 8) |
| 2 | 8/13 | Harness bugs: the fallback check ran with the stone still selected on the server, and later counts were off (see run 4) |
| 3 | 13/13 | After fixing the hotbar sync and hiding the tutorial toast |
| 4 | 7/13 | Harness bug: the reused test world's old depot dropped its stack when the area was cleared, and the player picked it up (32 instead of 16). Debug logs showed the scene itself did everything right |
| 5, 6 | 13/13, 13/13 | After emptying the old depot and clearing stray item entities in setup |

Dedicated dev server: the hooks apply and the depot is registered on the server; `ClientJoin` joins without errors.

**M2 smoke runs** (2026-10-21). Two leftover Minecraft instances from the M1 dedicated-server check were still running during runs 1 and 2, so frames were slow:

| Run | Result | Note |
|---|---|---|
| 1 | 2 failed | All 7 M2 checks passed. Harness bug: the drawer shift-click was sent while the one-item move was still playing, and CI drops quick moves while a station is busy. The "after leaving" check then failed as a consequence. Moves run on CI's frame clock, which falls behind the ticks when frames are slow |
| 2 | 1 failed | Same cause one step earlier: the RMB click came while the take move was still playing |
| 3 | 19/19 | Every step that clicks the scene now waits until the previous move has ended (`SmokeTest.moving()`) instead of relying on a fixed tick delay |

Dedicated dev server after M2: the server registers the depot; `ClientJoin` joins without errors, and the server logs the prefs it received (`[cinecreate] Dev starts scenes: {cinecreate:depot=KEY}`, a DEBUG line from `ScenePrefs`).

**M3 smoke runs** (2026-10-21):

| Run | Result | Note |
|---|---|---|
| 1 | 2 failed | Harness bug: Create presses the last ingot in place, so its sheet stays on the plate, not in the outputs, and the "pressed" wait never ended. The scene also ended by itself during that 20 s wait (limitation 20); the "stopped" step then found no scene |
| 2, 3 | 1 failed each | Harness bug: the server finished the 4th ingot while the client's head was still coming down; the check ran before the client's 4th impact. Fixed by also waiting for the client's press to stand still |
| 4, 5 | all passed | Run 5 with every advancement revoked first, to replay a first-time player; the scene stayed |
| 6 | 1 failed | **Real bug:** in one cycle no rendered frame showed the head on the plate (the impact screenshot stalled rendering), so that impact was lost. The scene now strikes once the cycle has reached the plate, on the first frame after if none showed it there |
| 7, 8 | all passed | Run 8, with the result check added (one result per ingot), is the final 28/28 |

Dedicated dev server after M3: the server registers the depot; `ClientJoin` joins without errors, and the server receives `{cinecreate:depot=KEY, cinecreate:pressing=USE}`.

**M4 smoke runs** (2026-10-21/22; from M4 on the window opens maximized, with borders, and the log confirms it: `window: maximized true, decorated true, fullscreen false`):

| Run | Result | Note |
|---|---|---|
| 1 | 10 failed | **Real bug:** CI refused every valve scene ("the work is out of reach: 1.50 over the feet"). It treats a station's work as a surface (at most 1.42 above the feet) unless `workOnTop()` says otherwise; a wall valve is a face (1.85). Fixed in `ValveScene` |
| 2 | 1 failed | Harness bug: the dye step teleported the player and clicked in the same tick, and the server ignores clicks until the client confirms a teleport. Also fixed: our value-box test counted the valve's top face as the hub (Create's valve box skips the side check, Create's input handler does not) |
| 3 | all passed | The screenshots showed the camera too close (the wheel filled the frame) and Create's own angle box over the hub next to ours, with a different value |
| 4, 5 | all passed | Camera moved back to 1.2; the crosshair target cleared after the tick's pick (clearing it in `ClientTickEvent.Pre` came too early: vanilla picks again mid-tick). Only our readout shows |
| 6 | all passed (37/37) | Re-run for watching, with the window-state log line: `maximized true, decorated true, fullscreen false` |

Dedicated dev server after M4: the server registers the depot and all 17 valve stations; `ClientJoin` joins without errors, and the server receives `{cinecreate:depot=KEY, cinecreate:valve_handle=USE, cinecreate:pressing=USE}`.

**M5 smoke runs** (2026-10-22, maximized window):

| Run | Result | Note |
|---|---|---|
| 1 | 1 failed | Harness bug: the toss count left out the Slot Cover, which was tossed too (9, not 8). Every scene check passed. The screenshots showed CI's drawer over the 5×5's top row and the hands over its bottom row, a tooltip left up during the watch, and the direction lights hidden behind Create's belt models |
| 2 | all passed | The framing now keeps every window between the drawer and the hint line (the camera stands a little above the group's middle and looks straight on); the smoke checks that band. Lights moved out in front of the belts; no hover or tooltip while the crafters work |
| 3 | all passed (54/54) | Final run: the lights a little brighter, and the test world's animals cleared from the crafters' corner (a cow walked into a screenshot). Window confirmed `maximized true, decorated true, fullscreen false` |

Dedicated dev server after M5: the server registers the crafter station next to the depot and the 17 valves; `ClientJoin` joins without errors, and the server receives `{cinecreate:depot=KEY, cinecreate:valve_handle=USE, cinecreate:pressing=USE, cinecreate:mechanical_crafter=USE}`.

**M6 smoke runs** (2026-10-22/23; most runs started at the block being worked on, with `-PsmokeFrom`):

| Run | Result | Note |
|---|---|---|
| Crank 1 | 4 failed | **Real bug:** every turn was refused: the server's rate limit started from `Long.MIN_VALUE`, and the difference overflowed |
| Crank 2–4 | all passed, then 1 failed | The hand looked off the knob from 1.05; at 1.5 the arms stretched like poles. A red mark at the scene's grip (development only) showed the hand on Create's grip at every angle; camera set to 1.0 and FOV 70. The hunger check failed: on peaceful, saturation refills by itself, so the test now reads the exhaustion before it reaches 4 |
| Crank 5–8 | 1–2 failed each | Both hands put on the grip (the user's request). **Real bug:** the server's time limit refused turns that a slow server handled several to a tick (after a screenshot); removed. Harness: the "still turns" check read the angle the scene stores each frame, and no frame was drawn after a screenshot (now read from the crank) |
| Basin 1 | 9 failed | Harness bug: CI moves the cursor's target only when the mouse moves, so the scene never pointed at the basin; the steps now point first, as the M1 and M5 steps do. **Real bug:** an empty burner has no block entity, so its menu would not open |
| Basin 2–4 | 4, 1, 3 failed | Filling puts the bucket in the inventory (Create's click), the check expected the hand; the test mixer had no rotation (the mixer is a cog, with no shaft: it now turns from a cogwheel beside it) |
| Basin 5, 6 | all passed | The screenshots showed the basin's near wall hiding its floor; the camera now looks down at about 70°, and the burner's held-out hand moved away from the camera |
| Full | all passed (75/75); the window confirmed `maximized true, decorated true, fullscreen false` | Every step, M1 to M6 |

Dedicated dev server after M6: the server registers the hand crank, basin and blaze burner stations next to the earlier ones; `ClientJoin` joins without errors, and the server receives `{cinecreate:hand_crank=USE, cinecreate:basin=USE, cinecreate:blaze_burner=USE, cinecreate:depot=KEY, cinecreate:valve_handle=USE, cinecreate:pressing=USE, cinecreate:mechanical_crafter=USE}`.

**M7 smoke runs** (2026-10-23; the M7 block started with `-PsmokeFrom="build the drain"`):

| Run | Result | Note |
|---|---|---|
| M7 1 | 10 failed | **Real:** Create 6.0.10's drain never fills a bucket from its tank on a click, so the scene's fill move could not work; it was dropped (limitation 39). Harness: the spout check read the server a tick before Create filled; the fan rig blew up into a campfire, whose full bottom face stops the air (Create's rule), so the depot above was never in the stream, and the millstone steps failed after it (the fan scene was never left). The screenshots showed the hand-off camera too close and the saw's waiting hand reaching across the blade |
| M7 2 | 7 failed | Drain and spout pass. **Real bug:** the depot never found the (now sideways) stream: Create's current stops its length at a depot's face and still works it, because its handler search rounds the length up (and covers the block under a sideways current). `Composites.fanStream` now follows that rule exactly. The millstone failures were again a consequence |
| M7 3 | all passed (22/22) | |
| Fan | — | The user saw the test fan pulling its air in instead of blowing it out. The step that turned it (+64 RPM) flipped it silently to −64 when it found no stream, which it always did in run 2 (the bug above); −64 pulls. The flip is gone: the set-up turns the fan at +64 from the moment it is built, and a check fails if it pulls |
| Full 1 | 5 failed | The new check caught −64 pulling (briefly set so on a misreading; back to +64). **Harness:** the M2 "OFF" check switched off only the bare depot and pressing, and since M7 the depot block has five kinds, so CI still saw working sides (limitation 13, as designed). The step now switches off every kind of the depot, and the M7 block switches its kinds back on |
| Full 2 | all passed (98/98); the window confirmed `maximized true, decorated true, fullscreen false` | Every step, M1 to M7, in about 4½ minutes; the fan check confirmed it blows out at +64 RPM |

Dedicated dev server after M7: the server registers the item drain, deployer, millstone, mechanical saw and weighted ejector stations next to the earlier ones; `ClientJoin` joins without errors, and the server receives all fifteen triggers, the eight new ones among them (`cinecreate:spouting`, `deploying`, `fan_processing`, `item_drain`, `deployer`, `millstone`, `mechanical_saw`, `weighted_ejector`, all USE).

**Value boxes (§1.8) run** (2026-10-23, the full smoke, recorded for the user's video):

| Run | Result | Note |
|---|---|---|
| 1 (recorded) | 4 failed | Harness: the valve's "turn back" clicked while the first turn's move still played (the recording slowed the frames, and CI drops clicks during a move); the rest of the valve block followed. The step now waits for `!moving()` |
| 2 (recorded) | 1 failed | Harness: the red-valve step still clicked the hub, which is Create's now; it clicks the wheel |
| 3 (recorded) | all passed (104/104); the window confirmed `maximized true, decorated true, fullscreen false` | The video of every scene (`run-smoke/cinecreate-animations.mp4`, 3 min, 22 labelled clips) was cut from this run by the smoke log's scene timestamps |

**Second round after M7** (2026-10-23, the user's notes):

| Run | Result | Note |
|---|---|---|
| Repro | — | Stone at the crafters' stand spot: from behind it the scene played with the camera 0.1 from the crafters, through the stone (the crafters' camera was placed by room in front of the wall only) |
| From the crafters | all passed (69/69) | Ejector watch, 10×10, the blocked-stand checks. The card for standing on the stone said CI's "too far"; the crafters now give their own reasons ("too low") |
| Grip block | all passed (12/12) | First run of the depot from afar, the re-plan, the basin hang and drop, and the valve with the grip |
| Full | all passed (116/116); the window confirmed `maximized true, decorated true, fullscreen false` | Every step |

**Third round after M7** (2026-10-23, the user's notes):

| Run | Result | Note |
|---|---|---|
| From the crafters | 1 failed (82/83) | Every big-crafter check passed. Behind the stone the scene was refused as it should be, but the card said "too far": the stone also kept the stand spot close, so the player behind it was too far by distance. The crafters now name the block between the player and the wall |
| Full 1 | 7 failed | The machine was overloaded: the server fell up to 67 s behind ("Can't keep up") and frames came one every 2 s. The press checks (impacts counted per drawn frame) and the valve's angle (sent 0.25 s after the last notch) failed with it; nothing in their code changed this round |
| Full 2 | 1 failed (120/121) | Still some lag. The millstone's gather was checked on the server during a 2-second lag spike; that step now waits for the server's answer instead (the M2 lesson: wait on state, never on ticks) |
| From the millstone | all passed (29/29) | No lag |
| Full 3 | all passed (121/121); the window confirmed `maximized true, decorated true, fullscreen false` | No lag; 4¾ minutes. The wall's map moved below CI's field-guide button at the right edge, which it had covered |

**The grip drawn as Create's** (2026-10-23, the user's report of a doubled arm at the basin):

| Run | Result | Note |
|---|---|---|
| From the grip | all passed (16/16) | The first try put the claw where it belongs, but the shots taken while the hands came up showed no grip: CI calls a station's world drawing only while the scene is under way. The grip moved to CI's body hook, drawn with the arms in every phase. The dropped item also fell from the fist, not the claw, which predates this; the claw now carries it |
| From the grip, again | all passed (17/17) | The grip seen coming up with the hand (screenshots 44a, 46). The shortest grip drawn was 0.44 against 0.45 folded: the lattice cut the corner while easing between directions. It is now held to the folded length after the easing too |
| Full | all passed (127/127); `maximized true, decorated true, fullscreen false` | No lag; 4 minutes. `gradlew build` passes |
