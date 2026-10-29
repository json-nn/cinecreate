package org.minechestplate.cinecreate.client;

import java.lang.reflect.Member;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.minechestplate.cinecreate.CineCreate;

/** Whether the installed Cinematic Interactions still has every client member the scenes call; if not, every scene is off. */
public final class CiApi {
    private CiApi() {}

    private static final Map<String, List<String>> USED = Map.ofEntries(
            Map.entry("client.Scene", List.of("phase", "station", "frameDt", "time", "clock", "leave", "begin", "beginClientOnly", "abort",
                    "active", "introProgress")),
            Map.entry("client.ClientStation", List.of("pos", "aim", "center", "lean", "hint", "renderWorld", "renderBody", "handTarget")),
            Map.entry("client.Picking", List.of("cursorOnPlane", "mouseGui", "toGui")),
            Map.entry("client.Hands", List.of("shoulder", "itemInHand")),
            Map.entry("client.CameraRig", List.of("last")),
            Map.entry("client.ItemPlacer", List.of("renderOne", "drawOriented", "render", "isBlock")),
            Map.entry("client.Fx", List.of("motes", "facePatch", "hotSparks", "spark", "steam", "blowSparks")),
            Map.entry("client.EnchantFx", List.of("disc", "billboard", "GLOW", "CIRCLE")),
            Map.entry("client.Anim", List.of("window", "inOutCubic", "outCubic", "hump", "lerp", "outBack", "smoother", "approach")),
            Map.entry("client.Settings", List.of("developerMode")),
            Map.entry("client.Reach", List.of("plan", "planBeside")),
            Map.entry("client.ReachNotice", List.of("show", "text")),
            Map.entry("api.client.AddonScenes", List.of("register", "available", "create")),
            Map.entry("client.MenuStation", List.of("menu", "slotClick", "startMove", "deny")));

    private static Boolean ok;

    public static boolean ok() {
        if (ok == null) {
            ok = check();
        }
        return ok;
    }

    private static boolean check() {
        for (Map.Entry<String, List<String>> entry : USED.entrySet()) {
            String name = "dev.dyslexic.cinematic." + entry.getKey();
            try {
                Class<?> type = Class.forName(name);
                List<String> members = Stream.concat(all(type, true), all(type, false)).toList();
                for (String member : entry.getValue()) {
                    if (!members.contains(member)) {
                        return off(name + "." + member);
                    }
                }
            } catch (ClassNotFoundException | LinkageError e) {
                return off(name);
            }
        }
        return true;
    }

    private static Stream<String> all(Class<?> type, boolean methods) {
        Stream<String> own = Stream.empty();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            Member[] members = methods ? c.getDeclaredMethods() : c.getDeclaredFields();
            own = Stream.concat(own, Arrays.stream(members).map(Member::getName));
        }
        return own;
    }

    private static boolean off(String missing) {
        CineCreate.LOGGER.error("[cinecreate] this Cinematic Interactions version has no {}: Create scenes are off, Create's blocks keep their own use",
                missing);
        return false;
    }
}
