# Mod avatar generator

Renders the avatar options in `docs/avatar/` from Create's own block models and textures, on a 200 × 200 pixel grid
(stepped light, dithered soft shadows, contour lines, white sticker outline), over `background.png`.

1. Extract Create's assets next to these scripts (they are not committed):
   `unzip <gradle cache>/create-1.21.1-6.0.10-281-slim.jar "assets/create/models/*" "assets/create/textures/*" -d assets`
2. `python scenes.py all out/` writes `option_<a|b|c>.png` (2000 px) and a 4× preview for each.

Each scene is one function in `scenes.py` (`scene_a` deployer + depot, `scene_b` Create camera, `scene_c` press moment):
camera angle, model placement, the item on the depot and the effects (swooshes, sparkles, sparks) are set there.

## Hand-on-depot illustrations (`hand/`)

High-resolution illustrations of the chosen concept (the player's hand on a Create depot), rendered in Blender and
finished in Python: `docs/avatar/hand/avatar_hand_<rest|flat|place>_2000.png`.

1. Extract Create's assets as above (into `tools/avatar/assets`).
2. `bash hand/run_all.sh 2000 final` renders the three variants headless with Blender 5.2 (Eevee, cel-shaded light,
   Freestyle ink lines) — about a minute each.
3. `python hand/post.py final out` adds the background, the white sticker outline and the painted effects.

`build.py` holds the scene (camera, light, the hand built from boxes with the textures in this folder);
`post.py` holds the effects, placed relative to the depot's size on screen.
