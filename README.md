# Cinematic Interactions: Create (`cinecreate`)

Add-on for [Cinematic Interactions](https://github.com/dcvke/CinematicInteractions) that gives Create machines first-person scenes
(depot, press, mechanical crafter, valve handle, hand crank, basin, saw, millstone, deployer, ejector, …). NeoForge 1.21.1 · Create 6.0.10 · Cinematic Interactions 0.18.12.
Needs to be installed on **both** the client and the server, next to Create and Cinematic Interactions.

## Dev setup

- JDK 21, IntelliJ IDEA: open this folder; it imports as a Gradle project and ModDevGradle adds the run configs on sync.
- `libs/` holds the Cinematic Interactions jar (its add-on API lives inside the mod jar).

| Run | What it does | Game folder |
|---|---|---|
| `Client` / `runClient` | Dev client | `run/` |
| `Server` / `runServer` | Dedicated dev server (offline mode, creative, flat world) | `run-server/` |
| `ClientJoin` / `runClientJoin` | Dev client that connects to the dev server on start; start `Server` first | `run/` |
| `GameTestServer` / `runGameTestServer` | Runs the mod's game tests and exits (fails while the mod has none) | `run-gametest/` |
| `Smoke` / `runSmoke` | Dev check: opens a test world, works the depot scene, the USE / KEY / OFF trigger policy, the press scene, a Valve Handle, and Mechanical Crafters (a chest crafted in a 3×3, a 10×10 framed), a Hand Crank, a Basin under a mixer and a Blaze Burner by itself, then Wave 2 (an Item Drain, depots under a Spout and a Deployer and in a fan's smoking stream, a Deployer's hand-off, a Millstone, a Mechanical Saw, a Weighted Ejector's throw watched), Create's value boxes left to Create, blocks where the player would stand, and the Extendo Grip (worked from afar, a basin hung from, a valve turned), in a maximized window, saves screenshots, writes `smoke-results.txt` and quits | `run-smoke/` |

Research, block list, animation specs: [docs/DESIGN.md](docs/DESIGN.md). What is built and what comes next: [docs/ROADMAP.md](docs/ROADMAP.md).

```
gradlew build            # jar in build/libs
gradlew runServer        # then, in a second terminal:
gradlew runClientJoin
```
