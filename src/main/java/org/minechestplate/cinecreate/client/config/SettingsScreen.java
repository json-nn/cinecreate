package org.minechestplate.cinecreate.client.config;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.lwjgl.glfw.GLFW;
import org.minechestplate.cinecreate.client.ClientConfig;
import org.minechestplate.cinecreate.client.ClientMode;
import org.minechestplate.cinecreate.station.SceneKind;
import org.minechestplate.cinecreate.station.SceneKinds;
import org.minechestplate.cinecreate.station.Trigger;

/**
 * The add-on's settings (the mod list's Config button): pages in a rail, a search across all of them, and a plate that
 * explains whatever the cursor is on. Edits are a draft until Done; Cancel and Esc drop them. Every part fits its text
 * at any GUI scale: the plate is as tall as the longest description needs, long names end in "…".
 */
public final class SettingsScreen extends Screen {
    private static final int MARGIN = 10, HEADER = 32, FOOTER = 26, GAP = 6, ROW = 22, CARD = 24, RESET = 14, LINE = 10;

    private final Screen parent;
    private final List<Draft<?>> drafts = new ArrayList<>();
    private final List<Page> pages = new ArrayList<>();
    private final Map<Draft<?>, double[]> knobs = new IdentityHashMap<>();
    private final List<Placed> placed = new ArrayList<>();
    private int page;
    private String query = "";
    private EditBox search;
    private double scroll, shownScroll, frameTime;
    private long lastFrame = System.nanoTime();
    private boolean barHeld;
    @Nullable
    private Slider held;
    @Nullable
    private Hover hover;
    @Nullable
    private Hover pinned;
    private int plateFor = -1, plateHeight;

    private record Page(String key, ItemStack icon, List<Row> rows) {
        Component name() {
            return Component.translatable(key);
        }
    }

    private record Placed(Row row, int x, int y, int w, int h) {}

    /** What the plate explains: a title, its text, and the setting's default (null: none). */
    private record Hover(Component title, Component text, @Nullable Component standard) {}

    public SettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("cinecreate.settings.title"));
        this.parent = parent;
        build();
    }

    // ---- the pages ------------------------------------------------------------------------------------

    private void build() {
        List<SceneKind> depots = List.of(SceneKinds.DEPOT, SceneKinds.PRESSING, SceneKinds.SPOUTING, SceneKinds.DEPLOYING, SceneKinds.FAN);
        List<SceneKind> processing = List.of(SceneKinds.BASIN, SceneKinds.BURNER, SceneKinds.MILLSTONE, SceneKinds.SAW, SceneKinds.DRAIN);
        List<SceneKind> byHand = List.of(SceneKinds.VALVE, SceneKinds.CRANK, SceneKinds.CRAFTER, SceneKinds.DEPLOYER, SceneKinds.EJECTOR);
        List<List<SceneKind>> sections = List.of(depots, processing, byHand);
        List<String> sectionKeys = List.of("depots", "processing", "by_hand");

        List<Card> triggerCards = new ArrayList<>();
        List<Row> scenes = new ArrayList<>();
        scenes.add(new Note(() -> Component.translatable("cinecreate.settings.scenes.intro")));
        scenes.add(new Actions(List.of(
                new Action("cinecreate.settings.all_click", () -> triggerCards.forEach(c -> c.set(Trigger.USE))),
                new Action("cinecreate.settings.all_key", () -> triggerCards.forEach(c -> c.set(Trigger.KEY))),
                new Action("cinecreate.settings.all_off", () -> triggerCards.forEach(c -> c.set(Trigger.OFF))))));
        for (int s = 0; s < sections.size(); s++) {
            scenes.add(new Heading("cinecreate.settings.section." + sectionKeys.get(s)));
            List<Card> cards = new ArrayList<>();
            for (SceneKind kind : sections.get(s)) {
                Draft<Trigger> draft = draft("cinecreate.configuration." + kind.name(), ClientConfig.triggerValue(kind), SettingsScreen::trigger);
                cards.add(new Card(icon(kind), draft, List.of(Trigger.USE, Trigger.KEY, Trigger.OFF), () -> false,
                        value -> "cinecreate.settings.trigger." + ((Trigger) value).name().toLowerCase(Locale.ROOT) + ".means"));
            }
            triggerCards.addAll(cards);
            scenes.add(new Cards(cards));
        }
        scenes.add(new Heading("cinecreate.settings.section.key"));
        scenes.add(new Toggle(draft("cinecreate.configuration.invitation", ClientConfig.INVITATION, SettingsScreen::onOff), () -> false));
        pages.add(new Page("cinecreate.settings.page.scenes", AllItems.BRASS_HAND.asStack(), scenes));

        Draft<Boolean> all = draft("cinecreate.configuration.particles.enabled", ClientConfig.PARTICLES, SettingsScreen::onOff);
        List<Card> particleCards = new ArrayList<>();
        List<Row> effects = new ArrayList<>();
        effects.add(new Heading("cinecreate.settings.section.particles"));
        effects.add(new Toggle(all, () -> false));
        effects.add(new Slider(draft("cinecreate.configuration.particles.amount", ClientConfig.AMOUNT, SettingsScreen::percent), 0, 100, 5,
                () -> !all.now));
        effects.add(new Toggle(draft("cinecreate.configuration.particles.followGame", ClientConfig.FOLLOW_GAME, SettingsScreen::onOff),
                () -> !all.now));
        effects.add(new Heading("cinecreate.settings.section.blocks"));
        effects.add(new Actions(List.of(
                new Action("cinecreate.settings.all_on", () -> particleCards.forEach(c -> c.set(true))),
                new Action("cinecreate.settings.all_off_particles", () -> particleCards.forEach(c -> c.set(false))))));
        for (SceneKind kind : SceneKinds.all()) {
            Draft<Boolean> draft = draft("cinecreate.configuration.particles." + kind.name(), ClientConfig.particlesValue(kind), SettingsScreen::onOff);
            particleCards.add(new Card(icon(kind), draft, List.of(true, false), () -> !all.now, null));
        }
        effects.add(new Cards(particleCards));
        effects.add(new Heading("cinecreate.settings.section.camera"));
        effects.add(new Toggle(draft("cinecreate.configuration.jolts", ClientConfig.JOLTS, SettingsScreen::onOff), () -> false));
        effects.add(new Toggle(draft("cinecreate.configuration.flashes", ClientConfig.FLASHES, SettingsScreen::onOff), () -> false));
        pages.add(new Page("cinecreate.settings.page.effects", new ItemStack(Items.BLAZE_POWDER), effects));

        Draft<Boolean> follow = draft("cinecreate.configuration.followLaunches", ClientConfig.FOLLOW_LAUNCHES, SettingsScreen::onOff);
        List<Row> view = new ArrayList<>();
        view.add(new Heading("cinecreate.settings.section.ejector"));
        view.add(new Toggle(follow, () -> false));
        view.add(new Slider(draft("cinecreate.configuration.watchReach", ClientConfig.WATCH_REACH, SettingsScreen::blocks), 8, 64, 4,
                () -> !follow.now));
        view.add(new Heading("cinecreate.settings.section.crafters"));
        view.add(new Toggle(draft("cinecreate.configuration.wallMap", ClientConfig.WALL_MAP, SettingsScreen::onOff), () -> false));
        view.add(new Toggle(draft("cinecreate.configuration.edgeTurn", ClientConfig.EDGE_TURN, SettingsScreen::onOff), () -> false));
        pages.add(new Page("cinecreate.settings.page.view", AllItems.GOGGLES.asStack(), view));

        List<Row> client = new ArrayList<>();
        client.add(new Heading("cinecreate.settings.section.client"));
        client.add(new Toggle(draft("cinecreate.configuration.clientMode", ClientConfig.CLIENT_MODE, SettingsScreen::onOff), () -> false));
        client.add(new Note(SettingsScreen::serverState));
        client.add(new Heading("cinecreate.settings.section.hands"));
        client.add(new Note(() -> Component.translatable("cinecreate.settings.hands")));
        pages.add(new Page("cinecreate.settings.page.client", AllItems.WRENCH.asStack(), client));
    }

    private <T> Draft<T> draft(String key, ModConfigSpec.ConfigValue<T> value, Function<T, Component> shown) {
        Draft<T> draft = new Draft<>(key, value, shown);
        drafts.add(draft);
        return draft;
    }

    private static ItemStack icon(SceneKind kind) {
        if (kind == SceneKinds.PRESSING) {
            return AllBlocks.MECHANICAL_PRESS.asStack();
        } else if (kind == SceneKinds.SPOUTING) {
            return AllBlocks.SPOUT.asStack();
        } else if (kind == SceneKinds.DEPLOYING || kind == SceneKinds.DEPLOYER) {
            return AllBlocks.DEPLOYER.asStack();
        } else if (kind == SceneKinds.FAN) {
            return AllBlocks.ENCASED_FAN.asStack();
        } else if (kind == SceneKinds.VALVE) {
            return AllBlocks.COPPER_VALVE_HANDLE.asStack();
        } else if (kind == SceneKinds.CRAFTER) {
            return AllBlocks.MECHANICAL_CRAFTER.asStack();
        } else if (kind == SceneKinds.CRANK) {
            return AllBlocks.HAND_CRANK.asStack();
        } else if (kind == SceneKinds.BASIN) {
            return AllBlocks.BASIN.asStack();
        } else if (kind == SceneKinds.BURNER) {
            return AllBlocks.BLAZE_BURNER.asStack();
        } else if (kind == SceneKinds.DRAIN) {
            return AllBlocks.ITEM_DRAIN.asStack();
        } else if (kind == SceneKinds.MILLSTONE) {
            return AllBlocks.MILLSTONE.asStack();
        } else if (kind == SceneKinds.SAW) {
            return AllBlocks.MECHANICAL_SAW.asStack();
        } else if (kind == SceneKinds.EJECTOR) {
            return AllBlocks.WEIGHTED_EJECTOR.asStack();
        }
        return AllBlocks.DEPOT.asStack();
    }

    private static Component onOff(Boolean on) {
        return Component.translatable(on ? "cinecreate.settings.on" : "cinecreate.settings.off");
    }

    private static Component percent(Integer value) {
        return Component.translatable("cinecreate.settings.percent", value);
    }

    private static Component blocks(Integer value) {
        return Component.translatable("cinecreate.settings.blocks", value);
    }

    private static Component trigger(Trigger trigger) {
        return Component.translatable("cinecreate.settings.trigger." + trigger.name().toLowerCase(Locale.ROOT));
    }

    private static int valueColor(Object value) {
        if (value instanceof Trigger trigger) {
            return trigger == Trigger.USE ? Look.BRASS : trigger == Trigger.KEY ? Look.TEAL : Look.SLATE;
        }
        return Boolean.TRUE.equals(value) ? Look.BRASS : Look.SLATE;
    }

    private static Component serverState() {
        Minecraft mc = Minecraft.getInstance();
        String state = mc.getConnection() == null ? "none" : ClientMode.serverHasAddon() ? "addon" : ClientMode.active() ? "active" : "off";
        return Component.translatable("cinecreate.settings.client.state." + state);
    }

    private static <T> Hover describe(Draft<T> draft, @Nullable Component more) {
        Component text = Component.translatable(draft.key + ".tooltip");
        if (more != null) {
            text = Component.empty().append(text).append("\n").append(more);
        }
        return new Hover(Component.translatable(draft.key), text, Component.translatable("cinecreate.settings.default", draft.show(draft.standard())));
    }

    private static Hover describe(String key) {
        return new Hover(Component.translatable(key), Component.translatable(key + ".tooltip"), null);
    }

    // ---- the frame ------------------------------------------------------------------------------------

    /** Narrow screens (a big GUI scale) keep the rail to its icons; the names come up on the plate. */
    private boolean compact() {
        return width < 520;
    }

    private int railWidth() {
        return compact() ? 28 : 104;
    }

    private int bodyLeft() {
        return MARGIN + railWidth() + GAP;
    }

    private int bodyWidth() {
        return width - MARGIN - bodyLeft();
    }

    private int top() {
        return HEADER + 6;
    }

    private int footerTop() {
        return height - FOOTER;
    }

    /** As tall as the longest description needs at this width, but never more than half of the body. */
    private int plateHeight() {
        int textWidth = bodyWidth() - 20;
        if (plateFor != textWidth) {
            plateFor = textWidth;
            int most = font.split(Component.translatable("cinecreate.settings.hint"), textWidth).size() * LINE;
            for (Hover h : everyHover()) {
                most = Math.max(most, lines(h, textWidth) * LINE + 2);
            }
            plateHeight = most + 14;
        }
        return Math.max(30, Math.min(plateHeight, (footerTop() - top()) / 2));
    }

    private int lines(Hover h, int textWidth) {
        return font.split(h.title(), textWidth).size() + font.split(h.text(), textWidth).size() + (h.standard() == null ? 0 : 1);
    }

    private List<Hover> everyHover() {
        List<Hover> all = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            all.add(describe(pages.get(i).key()));
            for (Row row : pages.get(i).rows()) {
                row.hovers(all);
            }
        }
        for (String key : List.of("cinecreate.settings.reset_all", "cinecreate.settings.cancel", "cinecreate.settings.done")) {
            all.add(describe(key));
        }
        return all;
    }

    private int plateTop() {
        return footerTop() - 2 - plateHeight();
    }

    private int listBottom() {
        return plateTop() - GAP;
    }

    private int searchWidth() {
        return Math.max(90, Math.min(176, width / 3));
    }

    private int buttonWidth() {
        return Math.max(52, Math.min(84, (width - 2 * MARGIN - 2 * GAP) / 4));
    }

    /** The text cut to this width, ending in "…" when it had to be cut. */
    private String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        int room = Math.max(0, width - font.width("…"));
        return font.plainSubstrByWidth(text, room) + "…";
    }

    @Override
    protected void init() {
        int w = searchWidth();
        search = new EditBox(font, width - MARGIN - w + 18, 14, w - 24, 10, Component.translatable("cinecreate.settings.search"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(Look.TEXT);
        search.setValue(query);
        search.setResponder(text -> {
            query = text.trim().toLowerCase(Locale.ROOT);
            scroll = 0;
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        plateFor = -1;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, HEADER, Look.BAND);
        g.fill(0, footerTop(), width, height, Look.BAND);
        g.fill(0, footerTop(), width, footerTop() + 1, Look.LINE);
        g.fill(0, HEADER - 1, width, HEADER, Look.LINE);
        g.fill(MARGIN, HEADER - 1, MARGIN + Math.max(40, railWidth()), HEADER, Look.BRASS_DIM);
        int searchLeft = width - MARGIN - searchWidth();
        g.renderItem(AllBlocks.COGWHEEL.asStack(), MARGIN, 8);
        int room = searchLeft - 10 - (MARGIN + 22);
        if (room > 30) {
            g.drawString(font, fit(title.getString(), room), MARGIN + 22, 8, Look.TEXT, true);
            g.drawString(font, fit(Component.translatable("cinecreate.settings.subtitle").getString(), room), MARGIN + 22, 19, Look.DIM, false);
        }
        Look.plate(g, searchLeft, 8, width - MARGIN, 26, Look.FIELD, search != null && search.isFocused() ? Look.BRASS : Look.LINE);
        // лупа
        int lx = searchLeft + 6, ly = 12;
        g.fill(lx + 1, ly, lx + 5, ly + 1, Look.DIM);
        g.fill(lx + 1, ly + 5, lx + 5, ly + 6, Look.DIM);
        g.fill(lx, ly + 1, lx + 1, ly + 5, Look.DIM);
        g.fill(lx + 5, ly + 1, lx + 6, ly + 5, Look.DIM);
        g.fill(lx + 5, ly + 6, lx + 7, ly + 8, Look.DIM);
        g.fill(lx + 7, ly + 8, lx + 9, ly + 10, Look.DIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        frameTime = Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        super.render(g, mouseX, mouseY, partialTick);
        if (search.getValue().isEmpty()) {
            int room = searchWidth() - 26;
            g.drawString(font, fit(Component.translatable("cinecreate.settings.search").getString(), room), search.getX(), 14, Look.MUTED, false);
        }
        hover = null;
        renderRail(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY);
        renderFooter(g, mouseX, mouseY);
        renderPlate(g);
    }

    private boolean searching() {
        return !query.isEmpty();
    }

    private void renderRail(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = MARGIN, x1 = MARGIN + railWidth();
        Look.casing(g, x0, top(), x1, footerTop() - 2, Look.RAIL);
        for (int i = 0; i < pages.size(); i++) {
            Page p = pages.get(i);
            int y = tabTop(i);
            boolean on = !searching() && i == page, over = in(mouseX, mouseY, x0 + 3, y, x1 - 3, y + 20);
            int found = searching() ? count(matches(p)) : -1;
            if (on) {
                g.fill(x0 + 3, y, x1 - 3, y + 20, Look.BRASS_WASH);
                g.fill(x0 + 3, y, x0 + 5, y + 20, Look.BRASS);
            } else if (over) {
                g.fill(x0 + 3, y, x1 - 3, y + 20, Look.HOVER);
            }
            g.renderItem(p.icon(), x0 + 6, y + 2);
            if (!compact()) {
                int color = on || over ? Look.TEXT : found == 0 ? Look.MUTED : Look.DIM;
                int room = x1 - x0 - 30 - (found > 0 ? font.width(Integer.toString(found)) + 10 : 0);
                g.drawString(font, fit(p.name().getString(), room), x0 + 26, y + 6, color, false);
            }
            if (found > 0) {
                String text = Integer.toString(found);
                int cw = font.width(text) + 6, cx = compact() ? x0 + 14 : x1 - 6 - cw, cy = compact() ? y + 11 : y + 4;
                Look.chip(g, cx, cy, cw, Look.BRASS);
                g.drawString(font, text, cx + 3, cy + 2, Look.BRASS, false);
            }
            if (over) {
                hover = describe(p.key());
            }
        }
    }

    private int tabTop(int i) {
        return top() + 6 + i * 24;
    }

    /** The rows shown: the page's, or every page's matches under its name while searching. */
    private List<Row> shown() {
        if (!searching()) {
            return pages.get(page).rows();
        }
        List<Row> rows = new ArrayList<>();
        int found = 0;
        for (Page p : pages) {
            List<Row> hits = matches(p);
            if (!hits.isEmpty()) {
                rows.add(new Heading(p.key()));
                rows.addAll(hits);
                found += count(hits);
            }
        }
        Component line = found == 0 ? Component.translatable("cinecreate.settings.none", query) : Component.translatable("cinecreate.settings.found", found);
        rows.add(0, new Note(() -> line));
        return rows;
    }

    private static int count(List<Row> hits) {
        int found = 0;
        for (Row hit : hits) {
            found += hit instanceof Cards cards ? cards.cards.size() : 1;
        }
        return found;
    }

    private List<Row> matches(Page p) {
        List<Row> hits = new ArrayList<>();
        for (Row row : p.rows()) {
            Row hit = row.matching();
            if (hit != null) {
                hits.add(hit);
            }
        }
        return hits;
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = bodyLeft(), x1 = width - MARGIN, y0 = top(), y1 = listBottom();
        Look.casing(g, x0, y0, x1, y1, Look.PANEL);
        List<Row> rows = shown();
        int w = x1 - x0 - 24;
        int total = 10;
        for (Row row : rows) {
            total += row.height(w);
        }
        double most = Math.max(0, total - (y1 - y0));
        scroll = Math.max(0, Math.min(most, scroll));
        shownScroll += (scroll - shownScroll) * (1 - Math.exp(-18 * frameTime));
        placed.clear();
        g.enableScissor(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
        int y = y0 + 6 - (int) Math.round(shownScroll);
        boolean inside = in(mouseX, mouseY, x0, y0, x1, y1);
        for (Row row : rows) {
            int h = row.height(w);
            if (y + h > y0 && y < y1) {
                row.render(g, x0 + 10, y, w, inside ? mouseX : -1, inside ? mouseY : -1);
                placed.add(new Placed(row, x0 + 10, y, w, h));
            }
            y += h;
        }
        g.disableScissor();
        if (most > 0) {
            int track = y1 - y0 - 12, thumb = Math.max(16, (int) (track * (y1 - y0) / (double) total));
            int ty = y0 + 6 + (int) Math.round((track - thumb) * shownScroll / most);
            g.fill(x1 - 7, y0 + 6, x1 - 5, y1 - 6, Look.LINE_SOFT);
            boolean over = barHeld || in(mouseX, mouseY, x1 - 10, y0, x1, y1);
            g.fill(x1 - 7, ty, x1 - 5, ty + thumb, over ? Look.BRASS : Look.BRASS_DIM);
        }
    }

    private void renderPlate(GuiGraphics g) {
        int x0 = bodyLeft(), x1 = width - MARGIN, y0 = plateTop(), y1 = footerTop() - 2, textWidth = x1 - x0 - 20;
        Look.casing(g, x0, y0, x1, y1, Look.PANEL);
        Hover shown = hover != null ? hover : pinned;
        g.enableScissor(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
        int y = y0 + 7;
        if (shown == null) {
            for (FormattedCharSequence line : font.split(Component.translatable("cinecreate.settings.hint"), textWidth)) {
                g.drawString(font, line, x0 + 10, y, Look.DIM, false);
                y += LINE;
            }
        } else {
            for (FormattedCharSequence line : font.split(shown.title(), textWidth)) {
                g.drawString(font, line, x0 + 10, y, Look.BRASS, false);
                y += LINE;
            }
            y += 2;
            for (FormattedCharSequence line : font.split(shown.text(), textWidth)) {
                g.drawString(font, line, x0 + 10, y, Look.TEXT, false);
                y += LINE;
            }
            if (shown.standard() != null) {
                g.drawString(font, fit(shown.standard().getString(), textWidth), x0 + 10, y, Look.MUTED, false);
            }
        }
        g.disableScissor();
    }

    private void renderFooter(GuiGraphics g, int mouseX, int mouseY) {
        int y = footerTop() + 4, bw = buttonWidth();
        button(g, mouseX, mouseY, MARGIN, y, bw, "cinecreate.settings.reset_all", false);
        int cancel = width - MARGIN - 2 * bw - GAP;
        button(g, mouseX, mouseY, cancel, y, bw, "cinecreate.settings.cancel", false);
        button(g, mouseX, mouseY, width - MARGIN - bw, y, bw, "cinecreate.settings.done", true);
        if (drafts.stream().anyMatch(Draft::edited)) {
            String note = Component.translatable("cinecreate.settings.unsaved").getString();
            int room = cancel - GAP - (MARGIN + bw + GAP) - 8;
            int x = cancel - GAP - Math.min(room, font.width(note));
            g.fill(x - 7, y + 7, x - 3, y + 11, Look.BRASS);
            if (room > 20) {
                g.drawString(font, fit(note, room), x, y + 5, Look.BRASS_DIM, false);
            }
        }
    }

    private void button(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String key, boolean main) {
        boolean over = in(mouseX, mouseY, x, y, x + w, y + 18);
        int fill = main ? (over ? 0xFFE6B356 : Look.BRASS) : over ? Look.CARD_HOVER : Look.CARD;
        Look.plate(g, x, y, x + w, y + 18, fill, main ? Look.BRASS_DIM : over ? Look.BRASS : Look.LINE);
        String text = fit(Component.translatable(key).getString(), w - 8);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + 5, main ? Look.INK : Look.TEXT, false);
        if (over) {
            hover = describe(key);
        }
    }

    // ---- input ----------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        setFocused(null);
        int y = footerTop() + 4, bw = buttonWidth();
        if (button == 0 && in(mouseX, mouseY, MARGIN, y, MARGIN + bw, y + 18)) {
            drafts.forEach(Draft::reset);
            clickSound();
            return true;
        }
        int cancel = width - MARGIN - 2 * bw - GAP;
        if (button == 0 && in(mouseX, mouseY, cancel, y, cancel + bw, y + 18)) {
            clickSound();
            onClose();
            return true;
        }
        if (button == 0 && in(mouseX, mouseY, width - MARGIN - bw, y, width - MARGIN, y + 18)) {
            clickSound();
            done();
            return true;
        }
        for (int i = 0; i < pages.size(); i++) {
            int ty = tabTop(i);
            if (in(mouseX, mouseY, MARGIN + 3, ty, MARGIN + railWidth() - 3, ty + 20)) {
                page = i;
                search.setValue("");
                scroll = shownScroll = 0;
                clickSound();
                return true;
            }
        }
        int x1 = width - MARGIN;
        if (in(mouseX, mouseY, x1 - 10, top(), x1, listBottom())) {
            barHeld = true;
            barTo(mouseY);
            return true;
        }
        if (!in(mouseX, mouseY, bodyLeft(), top(), x1, listBottom())) {
            return false;
        }
        for (Placed p : placed) {
            if (mouseY >= p.y() && mouseY < p.y() + p.h() && p.row().click(mouseX, mouseY, p.x(), p.y(), p.w(), button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (held != null) {
            for (Placed p : placed) {
                if (p.row() == held) {
                    held.set(mouseX, p.x(), p.w());
                }
            }
            return true;
        }
        if (barHeld) {
            barTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        held = null;
        barHeld = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (in(mouseX, mouseY, bodyLeft(), top(), width - MARGIN, listBottom())) {
            scroll -= scrollY * ROW * 1.5;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void barTo(double mouseY) {
        int w = bodyWidth() - 24, total = 10;
        for (Row row : shown()) {
            total += row.height(w);
        }
        double most = Math.max(0, total - (listBottom() - top()));
        double t = (mouseY - top() - 6) / Math.max(1, listBottom() - top() - 12);
        scroll = Math.max(0, Math.min(1, t)) * most;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            done();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void done() {
        drafts.forEach(Draft::store);
        ClientConfig.SPEC.save();
        ClientConfig.sendToServer();
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void clickSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private static boolean in(double x, double y, double x0, double y0, double x1, double y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }

    /** Eases a switch's knob toward where its setting is. */
    private double knob(Draft<?> draft, boolean on) {
        double[] k = knobs.computeIfAbsent(draft, d -> new double[] {on ? 1 : 0});
        k[0] += ((on ? 1 : 0) - k[0]) * (1 - Math.exp(-22 * frameTime));
        return k[0];
    }

    private boolean matches(String key) {
        return Component.translatable(key).getString().toLowerCase(Locale.ROOT).contains(query)
                || Component.translatable(key + ".tooltip").getString().toLowerCase(Locale.ROOT).contains(query);
    }

    /** The reset arrow at a row's end, while the setting is off its default; true when the cursor is on it. */
    private boolean resetArrow(GuiGraphics g, Draft<?> draft, int x, int y, int mouseX, int mouseY) {
        if (draft.standing()) {
            return false;
        }
        boolean over = in(mouseX, mouseY, x, y, x + RESET, y + RESET);
        Look.plate(g, x, y, x + RESET, y + RESET, over ? Look.CARD_HOVER : Look.CARD, over ? Look.BRASS : Look.LINE);
        g.drawString(font, "↺", x + 4, y + 3, over ? Look.BRASS : Look.DIM, false);
        if (over) {
            hover = new Hover(Component.translatable("cinecreate.settings.reset"), Component.translatable("cinecreate.settings.reset.tooltip"),
                    Component.translatable("cinecreate.settings.default", standardOf(draft)));
        }
        return over;
    }

    private static <T> Component standardOf(Draft<T> draft) {
        return draft.show(draft.standard());
    }

    // ---- rows -----------------------------------------------------------------------------------------

    private abstract class Row {
        abstract int height(int width);

        abstract void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY);

        boolean click(double mouseX, double mouseY, int x, int y, int w, int button) {
            return false;
        }

        /** The row as a search shows it, or null when it does not match (headings and notes never do). */
        @Nullable
        Row matching() {
            return null;
        }

        /** Every description this row can put on the plate, for the plate's height. */
        void hovers(List<Hover> into) {}
    }

    private final class Heading extends Row {
        private final String key;

        Heading(String key) {
            this.key = key;
        }

        @Override
        int height(int width) {
            return 22;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            String text = fit(Component.translatable(key).getString(), w - 24);
            int tw = font.width(text);
            g.drawString(font, text, x, y + 9, Look.BRASS, false);
            g.fill(x + tw + 6, y + 13, x + w, y + 14, Look.LINE);
            g.fill(x + tw + 6, y + 13, x + Math.min(w, tw + 18), y + 14, Look.BRASS_DIM);
        }
    }

    private final class Note extends Row {
        private final Supplier<Component> text;

        Note(Supplier<Component> text) {
            this.text = text;
        }

        @Override
        int height(int width) {
            return font.split(text.get(), width).size() * LINE + 6;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            int ly = y + 3;
            for (FormattedCharSequence line : font.split(text.get(), w)) {
                g.drawString(font, line, x, ly, Look.DIM, false);
                ly += LINE;
            }
        }
    }

    private final class Toggle extends Row {
        private final Draft<Boolean> draft;
        private final Supplier<Boolean> dim;

        Toggle(Draft<Boolean> draft, Supplier<Boolean> dim) {
            this.draft = draft;
            this.dim = dim;
        }

        @Override
        int height(int width) {
            return ROW;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            boolean over = in(mouseX, mouseY, x - 4, y, x + w, y + ROW);
            if (over) {
                g.fill(x - 4, y + 1, x + w, y + ROW - 1, Look.HOVER);
            }
            int sx = x + w - RESET - 6 - 26;
            String label = Component.translatable(draft.key).getString();
            String state = draft.show(draft.now).getString();
            int room = sx - 8 - x;
            boolean withState = font.width(label) + 8 + font.width(state) <= room;
            g.drawString(font, fit(label, withState ? room - font.width(state) - 8 : room), x, y + 7, dim.get() ? Look.MUTED : Look.TEXT, false);
            if (withState) {
                g.drawString(font, state, sx - 6 - font.width(state), y + 7, draft.now && !dim.get() ? Look.BRASS : Look.MUTED, false);
            }
            Look.toggle(g, sx, y + 5, knob(draft, draft.now), over, dim.get());
            boolean onArrow = resetArrow(g, draft, x + w - RESET, y + 4, mouseX, mouseY);
            if (over && !onArrow) {
                hover = describe(draft, null);
            }
        }

        @Override
        boolean click(double mouseX, double mouseY, int x, int y, int w, int button) {
            if (!draft.standing() && in(mouseX, mouseY, x + w - RESET, y + 4, x + w, y + 4 + RESET)) {
                draft.reset();
            } else {
                draft.now = !draft.now;
            }
            clickSound();
            return true;
        }

        @Override
        Row matching() {
            return matches(draft.key) ? this : null;
        }

        @Override
        void hovers(List<Hover> into) {
            into.add(describe(draft, null));
        }
    }

    private final class Slider extends Row {
        private final Draft<Integer> draft;
        private final int min, max, step;
        private final Supplier<Boolean> dim;

        Slider(Draft<Integer> draft, int min, int max, int step, Supplier<Boolean> dim) {
            this.draft = draft;
            this.min = min;
            this.max = max;
            this.step = step;
            this.dim = dim;
        }

        @Override
        int height(int width) {
            return ROW;
        }

        private int trackWidth(int w) {
            return Math.max(44, Math.min(88, w / 4));
        }

        private int valueWidth() {
            return Math.max(font.width(draft.show(max).getString()), font.width(draft.show(min).getString()));
        }

        private int trackLeft(int x, int w) {
            return x + w - RESET - 6 - valueWidth() - 6 - trackWidth(w);
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            boolean over = in(mouseX, mouseY, x - 4, y, x + w, y + ROW);
            if (over || held == this) {
                g.fill(x - 4, y + 1, x + w, y + ROW - 1, Look.HOVER);
            }
            int tx = trackLeft(x, w), tw = trackWidth(w);
            g.drawString(font, fit(Component.translatable(draft.key).getString(), tx - 10 - x), x, y + 7, dim.get() ? Look.MUTED : Look.TEXT, false);
            double t = (draft.now - min) / (double) (max - min);
            g.fill(tx, y + 10, tx + tw, y + 12, Look.LINE);
            g.fill(tx, y + 10, tx + (int) Math.round(tw * t), y + 12, dim.get() ? Look.BRASS_DIM : Look.BRASS);
            int kx = tx + (int) Math.round(tw * t) - 2;
            Look.plate(g, kx, y + 5, kx + 5, y + 17, held == this ? 0xFFFFF4DC : 0xFFE9DFC8, Look.BRASS_DIM);
            g.drawString(font, draft.show(draft.now), tx + tw + 6, y + 7, dim.get() ? Look.MUTED : Look.BRASS, false);
            boolean onArrow = resetArrow(g, draft, x + w - RESET, y + 4, mouseX, mouseY);
            if ((over || held == this) && !onArrow) {
                hover = describe(draft, null);
            }
        }

        @Override
        boolean click(double mouseX, double mouseY, int x, int y, int w, int button) {
            if (!draft.standing() && in(mouseX, mouseY, x + w - RESET, y + 4, x + w, y + 4 + RESET)) {
                draft.reset();
                clickSound();
                return true;
            }
            int tx = trackLeft(x, w);
            if (in(mouseX, mouseY, tx - 4, y, tx + trackWidth(w) + 4, y + ROW)) {
                held = this;
                set(mouseX, x, w);
                return true;
            }
            return false;
        }

        void set(double mouseX, int x, int w) {
            int tx = trackLeft(x, w);
            double t = Math.max(0, Math.min(1, (mouseX - tx) / (double) trackWidth(w)));
            draft.now = Math.max(min, Math.min(max, min + (int) Math.round(t * (max - min) / step) * step));
        }

        @Override
        Row matching() {
            return matches(draft.key) ? this : null;
        }

        @Override
        void hovers(List<Hover> into) {
            into.add(describe(draft, null));
        }
    }

    /** A block's setting as a card: its item, its name, and a chip with the value; a click steps it on, a right-click back. */
    private final class Card {
        private final ItemStack icon;
        private final Draft<Object> draft;
        private final List<?> values;
        private final Supplier<Boolean> dim;
        /** The lang key that says what a value means, or null. */
        @Nullable
        private final Function<Object, String> means;

        @SuppressWarnings("unchecked")
        <T> Card(ItemStack icon, Draft<T> draft, List<T> values, Supplier<Boolean> dim, @Nullable Function<Object, String> means) {
            this.icon = icon;
            this.draft = (Draft<Object>) draft;
            this.values = values;
            this.dim = dim;
            this.means = means;
        }

        void set(Object value) {
            draft.now = value;
        }

        void step(int by) {
            int i = values.indexOf(draft.now);
            draft.now = values.get(Math.floorMod(i + by, values.size()));
        }

        Hover hover(Object value) {
            return describe(draft, means == null ? null : Component.translatable(means.apply(value)).withStyle(ChatFormatting.GRAY));
        }

        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            boolean over = in(mouseX, mouseY, x, y, x + w, y + CARD);
            boolean faded = dim.get();
            Look.plate(g, x, y, x + w, y + CARD, over ? Look.CARD_HOVER : Look.CARD, over ? Look.BRASS : Look.LINE);
            if (!draft.standing()) {
                g.fill(x + 2, y + 2, x + 5, y + 3, Look.BRASS);
                g.fill(x + 2, y + 3, x + 3, y + 5, Look.BRASS);
            }
            g.renderItem(icon, x + 5, y + 4);
            String chip = draft.show(draft.now).getString();
            int cw = font.width(chip) + 8, color = faded ? Look.SLATE : valueColor(draft.now);
            Look.chip(g, x + w - cw - 5, y + 6, cw, color);
            g.drawString(font, chip, x + w - cw - 1, y + 8, color, false);
            g.drawString(font, fit(Component.translatable(draft.key).getString(), w - cw - 34), x + 25, y + 8, faded ? Look.MUTED : Look.TEXT, false);
            if (over) {
                hover = hover(draft.now);
            }
        }
    }

    private final class Cards extends Row {
        private final List<Card> cards;

        Cards(List<Card> cards) {
            this.cards = cards;
        }

        private int columns(int width) {
            return width >= 300 ? 2 : 1;
        }

        private int cardWidth(int width) {
            int columns = columns(width);
            return (width - 6 * (columns - 1)) / columns;
        }

        @Override
        int height(int width) {
            int columns = columns(width);
            return (cards.size() + columns - 1) / columns * (CARD + 4) + 2;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            int columns = columns(w), cw = cardWidth(w);
            for (int i = 0; i < cards.size(); i++) {
                cards.get(i).render(g, x + i % columns * (cw + 6), y + i / columns * (CARD + 4), cw, mouseX, mouseY);
            }
        }

        @Override
        boolean click(double mouseX, double mouseY, int x, int y, int w, int button) {
            int columns = columns(w), cw = cardWidth(w);
            for (int i = 0; i < cards.size(); i++) {
                int cx = x + i % columns * (cw + 6), cy = y + i / columns * (CARD + 4);
                if (in(mouseX, mouseY, cx, cy, cx + cw, cy + CARD)) {
                    cards.get(i).step(button == 1 ? -1 : 1);
                    clickSound();
                    return true;
                }
            }
            return false;
        }

        @Override
        Row matching() {
            List<Card> hits = cards.stream().filter(card -> matches(card.draft.key)).toList();
            return hits.isEmpty() ? null : new Cards(hits);
        }

        @Override
        void hovers(List<Hover> into) {
            for (Card card : cards) {
                for (Object value : card.values) {
                    into.add(card.hover(value));
                }
            }
        }
    }

    private record Action(String key, Runnable run) {}

    /** A row of buttons that set many at once; they wrap to a second line where the row is narrow. */
    private final class Actions extends Row {
        private final List<Action> actions;

        Actions(List<Action> actions) {
            this.actions = actions;
        }

        private int buttonWidth(Action action, int w) {
            return Math.min(w, font.width(Component.translatable(action.key())) + 14);
        }

        /** Each button's place: x offset and line. */
        private int[][] places(int w) {
            int[][] at = new int[actions.size()][2];
            int bx = 0, line = 0;
            for (int i = 0; i < actions.size(); i++) {
                int bw = buttonWidth(actions.get(i), w);
                if (bx > 0 && bx + bw > w) {
                    bx = 0;
                    line++;
                }
                at[i][0] = bx;
                at[i][1] = line;
                bx += bw + 6;
            }
            return at;
        }

        @Override
        int height(int width) {
            int[][] at = places(width);
            return (at[at.length - 1][1] + 1) * 20 + 2;
        }

        @Override
        void render(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
            int[][] at = places(w);
            for (int i = 0; i < actions.size(); i++) {
                Action action = actions.get(i);
                int bw = buttonWidth(action, w), bx = x + at[i][0], by = y + 3 + at[i][1] * 20;
                boolean over = in(mouseX, mouseY, bx, by, bx + bw, by + 16);
                Look.plate(g, bx, by, bx + bw, by + 16, over ? Look.CARD_HOVER : Look.CARD, over ? Look.BRASS : Look.LINE);
                g.drawString(font, fit(Component.translatable(action.key()).getString(), bw - 10), bx + 7, by + 4, over ? Look.BRASS : Look.TEXT, false);
                if (over) {
                    hover = describe(action.key());
                }
            }
        }

        @Override
        boolean click(double mouseX, double mouseY, int x, int y, int w, int button) {
            int[][] at = places(w);
            for (int i = 0; i < actions.size(); i++) {
                int bw = buttonWidth(actions.get(i), w), bx = x + at[i][0], by = y + 3 + at[i][1] * 20;
                if (in(mouseX, mouseY, bx, by, bx + bw, by + 16)) {
                    actions.get(i).run().run();
                    clickSound();
                    return true;
                }
            }
            return false;
        }

        @Override
        void hovers(List<Hover> into) {
            for (Action action : actions) {
                into.add(describe(action.key()));
            }
        }
    }

    // ---- development ----------------------------------------------------------------------------------

    /** Development (SmokeTest): shows a page, the search cleared. */
    public void smokePage(int index) {
        page = index;
        search.setValue("");
        scroll = shownScroll = 0;
    }

    /** Development (SmokeTest): types into the search. */
    public void smokeSearch(String text) {
        search.setValue(text);
    }

    /** Development (SmokeTest): the plate explains this setting while the cursor is on nothing else. */
    public void smokeHover(String key) {
        pinned = null;
        for (Draft<?> draft : drafts) {
            if (draft.key.equals(key)) {
                pinned = describe(draft, key.startsWith("cinecreate.configuration.") && draft.now instanceof Trigger t
                        ? Component.translatable("cinecreate.settings.trigger." + t.name().toLowerCase(Locale.ROOT) + ".means").withStyle(ChatFormatting.GRAY)
                        : null);
            }
        }
    }

    /** Development (SmokeTest): whether the plate has room for every line of the longest description. */
    public boolean smokePlateFits() {
        int textWidth = bodyWidth() - 20, most = 0;
        for (Hover h : everyHover()) {
            most = Math.max(most, lines(h, textWidth) * LINE + 2 + 14);
        }
        return plateHeight() >= most;
    }

    /**
     * Development (SmokeTest): where a click works a setting on the screen as last drawn (its switch, its card), or one of
     * the footer's buttons ("done", "cancel", "reset_all"); null if not shown.
     */
    @Nullable
    public double[] smokeWhere(String key) {
        int y = footerTop() + 13, bw = buttonWidth();
        switch (key) {
            case "reset_all" -> {
                return new double[] {MARGIN + bw / 2.0, y};
            }
            case "cancel" -> {
                return new double[] {width - MARGIN - 2 * bw - GAP + bw / 2.0, y};
            }
            case "done" -> {
                return new double[] {width - MARGIN - bw / 2.0, y};
            }
            default -> {
            }
        }
        for (Placed p : placed) {
            if (p.row() instanceof Toggle toggle && toggle.draft.key.equals(key)) {
                return new double[] {p.x() + p.w() - RESET - 6 - 13, p.y() + 11};
            }
            if (p.row() instanceof Cards cards) {
                int columns = cards.columns(p.w()), cw = cards.cardWidth(p.w());
                for (int i = 0; i < cards.cards.size(); i++) {
                    if (cards.cards.get(i).draft.key.equals(key)) {
                        return new double[] {p.x() + i % columns * (cw + 6) + cw / 2.0, p.y() + i / columns * (CARD + 4) + CARD / 2.0};
                    }
                }
            }
        }
        return null;
    }
}
