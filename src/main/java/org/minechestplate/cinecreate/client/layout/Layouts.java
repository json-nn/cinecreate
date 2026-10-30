package org.minechestplate.cinecreate.client.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import org.minechestplate.cinecreate.CineCreate;

/** Where our scenes' hands work, as moved in the editor: three profiles per scene (as in CI), in {@code config/cinecreate-layouts.json}. */
public final class Layouts {
    private Layouts() {}

    public static final int PROFILES = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Profile(Region whole, List<Region> spots) {
        public static final Profile NONE = new Profile(Region.NONE, List.of());

        public Region region(int spot) {
            return spot < 0 ? whole : spot < spots.size() ? spots.get(spot) : Region.NONE;
        }

        public Profile with(int spot, Region region) {
            if (spot < 0) {
                return new Profile(region, spots);
            }
            List<Region> all = new ArrayList<>(spots);
            while (all.size() <= spot) {
                all.add(Region.NONE);
            }
            all.set(spot, region);
            return new Profile(whole, List.copyOf(all));
        }

        boolean untouched() {
            return whole.equals(Region.NONE) && spots.stream().allMatch(Region.NONE::equals);
        }

        Profile blend(Profile to, double t) {
            List<Region> all = new ArrayList<>();
            for (int i = 0; i < Math.max(spots.size(), to.spots().size()); i++) {
                all.add(region(i).blend(to.region(i), t));
            }
            return new Profile(whole.blend(to.whole(), t), List.copyOf(all));
        }

        boolean near(Profile to) {
            for (int i = -1; i < Math.max(spots.size(), to.spots().size()); i++) {
                double[] a = region(i).values(), b = to.region(i).values();
                for (int f = 0; f < Region.FIELDS; f++) {
                    if (Math.abs(a[f] - b[f]) > 1e-5) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private record Shown(Profile profile, long at) {}

    /** What each scene shows now: edits ease in at CI's rate (1 - e^(-18 dt)) instead of jumping. */
    private static final Map<String, Shown> SHOWN = new HashMap<>();

    private record Saved(Profile[] profiles, int selected) {}

    private static final Map<String, Saved> SAVED = new HashMap<>();
    private static final Map<String, Profile> PREVIEW = new HashMap<>();
    private static boolean loaded;

    public static Profile current(String id) {
        Profile target = PREVIEW.get(id);
        if (target == null) {
            Saved saved = saved(id);
            target = saved.profiles()[saved.selected()];
        }
        long now = System.nanoTime();
        Shown shown = SHOWN.get(id);
        if (shown == null) {
            SHOWN.put(id, new Shown(target, now));
            return target;
        }
        if (shown.profile() == target || now == shown.at()) {
            return shown.profile();
        }
        double dt = Math.min(0.1, (now - shown.at()) / 1e9);
        Profile next = shown.profile().blend(target, 1 - Math.exp(-18 * dt));
        next = next.near(target) ? target : next;
        SHOWN.put(id, new Shown(next, now));
        return next;
    }

    /** Development (SmokeTest): edits show at once instead of easing in. */
    public static void smokeSettle() {
        SHOWN.clear();
    }

    /** The spot's surface axes: across, along (towards the player on a top, up on a face) and out of it. */
    public static Vec3[] axes(LayoutScene scene, int spot) {
        Vec3 across = scene.layoutAcross(), towards = scene.layoutTowards(), up = new Vec3(0, 1, 0);
        return scene.spotOnFace(spot) ? new Vec3[] {across, up, towards} : new Vec3[] {across, towards, up};
    }

    /** The hand's spot: the scene's own point moved by the whole layout's and the spot's offsets. */
    public static Vec3 point(LayoutScene scene, int spot) {
        Vec3 base = scene.spotBase(spot);
        Profile profile = current(scene.layoutId());
        if (profile.untouched()) {
            return base;
        }
        Region whole = profile.whole(), own = profile.region(spot);
        Vec3[] axes = axes(scene, spot);
        return base.add(axes[0].scale((whole.x() + own.x()) / 16)).add(axes[1].scale((whole.along() + own.along()) / 16))
                .add(axes[2].scale((whole.out() + own.out()) / 16));
    }

    static Profile[] profiles(String id) {
        return saved(id).profiles().clone();
    }

    static int selected(String id) {
        return saved(id).selected();
    }

    static void preview(String id, Profile profile) {
        PREVIEW.put(id, profile);
    }

    static void endPreview(String id) {
        PREVIEW.remove(id);
    }

    static boolean save(String id, Profile[] profiles, int selected) {
        load();
        Saved before = SAVED.get(id);
        SAVED.put(id, new Saved(profiles.clone(), selected));
        try {
            write();
            return true;
        } catch (IOException | RuntimeException e) {
            CineCreate.LOGGER.warn("[cinecreate] could not save the scene layouts: {}", e.toString());
            if (before == null) {
                SAVED.remove(id);
            } else {
                SAVED.put(id, before);
            }
            return false;
        }
    }

    private static Saved saved(String id) {
        load();
        return SAVED.computeIfAbsent(id, k -> {
            Profile[] none = new Profile[PROFILES];
            java.util.Arrays.fill(none, Profile.NONE);
            return new Saved(none, 0);
        });
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("cinecreate-layouts.json");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> scene : root.entrySet()) {
                JsonObject saved = scene.getValue().getAsJsonObject();
                Profile[] profiles = new Profile[PROFILES];
                JsonArray list = saved.getAsJsonArray("profiles");
                for (int i = 0; i < PROFILES; i++) {
                    profiles[i] = i < list.size() ? profile(list.get(i).getAsJsonObject()) : Profile.NONE;
                }
                int selected = Math.floorMod(saved.get("selected").getAsInt(), PROFILES);
                SAVED.put(scene.getKey(), new Saved(profiles, selected));
            }
        } catch (IOException | RuntimeException e) {
            CineCreate.LOGGER.warn("[cinecreate] could not read {}: {}", file, e.toString());
        }
    }

    private static Profile profile(JsonObject json) {
        List<Region> spots = new ArrayList<>();
        for (JsonElement spot : json.getAsJsonArray("spots")) {
            spots.add(region(spot.getAsJsonArray()));
        }
        return new Profile(region(json.getAsJsonArray("whole")), List.copyOf(spots));
    }

    private static Region region(JsonArray values) {
        double[] v = new double[values.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = values.get(i).getAsDouble();
        }
        return Region.of(v);
    }

    private static JsonArray json(Region region) {
        JsonArray values = new JsonArray();
        for (double v : region.values()) {
            values.add(v);
        }
        return values;
    }

    private static void write() throws IOException {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Saved> entry : SAVED.entrySet()) {
            JsonArray profiles = new JsonArray();
            for (Profile profile : entry.getValue().profiles()) {
                JsonObject json = new JsonObject();
                json.add("whole", json(profile.whole()));
                JsonArray spots = new JsonArray();
                profile.spots().forEach(spot -> spots.add(json(spot)));
                json.add("spots", spots);
                profiles.add(json);
            }
            JsonObject saved = new JsonObject();
            saved.addProperty("selected", entry.getValue().selected());
            saved.add("profiles", profiles);
            root.add(entry.getKey(), saved);
        }
        Path file = file(), temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
