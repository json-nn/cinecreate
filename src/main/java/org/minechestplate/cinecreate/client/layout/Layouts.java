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

/** Our scenes' layout profiles (three per scene, as in CI), kept in {@code config/cinecreate-layouts.json}. */
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
    }

    private record Saved(Profile[] profiles, int selected) {}

    private static final Map<String, Saved> SAVED = new HashMap<>();
    private static final Map<String, Profile> PREVIEW = new HashMap<>();
    private static boolean loaded;

    public static Profile current(String id) {
        Profile shown = PREVIEW.get(id);
        if (shown != null) {
            return shown;
        }
        Saved saved = saved(id);
        return saved.profiles()[saved.selected()];
    }

    public static Vec3 point(LayoutScene scene, int spot) {
        Vec3 base = scene.spotBase(spot);
        Profile profile = current(scene.layoutId());
        if (profile.untouched()) {
            return base;
        }
        Region whole = profile.whole(), own = profile.region(spot);
        Vec3 across = scene.layoutAcross(), towards = scene.layoutTowards();
        Vec3 centre = centre(scene);
        Vec3 rel = base.subtract(centre);
        double a = rel.dot(across) * whole.width() / 100, t = rel.dot(towards) * whole.depth() / 100;
        double turn = Math.toRadians(whole.rotation()), cos = Math.cos(turn), sin = Math.sin(turn);
        Vec3 out = centre.add(across.scale(a * cos - t * sin)).add(towards.scale(a * sin + t * cos)).add(0, rel.y, 0);
        return out.add(across.scale((whole.x() + own.x()) / 16)).add(towards.scale((whole.towards() + own.towards()) / 16))
                .add(0, (whole.height() + own.height()) / 16, 0);
    }

    /** The drawn item's scale. */
    public static float size(LayoutScene scene, int spot) {
        Profile profile = current(scene.layoutId());
        return (float) (profile.whole().size() / 100 * profile.region(spot).size() / 100);
    }

    /** The spot's click area, as a share of the scene's own. */
    public static double area(LayoutScene scene, int spot) {
        Profile profile = current(scene.layoutId());
        Region whole = profile.whole(), own = profile.region(spot);
        return Math.sqrt(whole.width() * whole.depth() * own.width() * own.depth()) / 10000;
    }

    /** Degrees the drawn item turns about the vertical. */
    public static double rotation(LayoutScene scene, int spot) {
        Profile profile = current(scene.layoutId());
        return profile.whole().rotation() + profile.region(spot).rotation();
    }

    static Vec3 centre(LayoutScene scene) {
        int n = scene.spotNames().size();
        Vec3 sum = Vec3.ZERO;
        for (int i = 0; i < n; i++) {
            sum = sum.add(scene.spotBase(i));
        }
        return n == 0 ? sum : sum.scale(1.0 / n);
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
