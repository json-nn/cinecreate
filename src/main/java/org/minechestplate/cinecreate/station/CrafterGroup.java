package org.minechestplate.cinecreate.station;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Mechanical Crafters worked as one scene: the wall the clicked one stands in (crafters next to each other in the plane
 * of their fronts, all facing the same way), up to {@link #MAX}×{@link #MAX}. A bigger wall is cut down to the crafters
 * Create's recipe chain links to the clicked one; when even that is bigger, there is no scene (DESIGN §10).
 *
 * <p>The cells are in reading order as seen from the front (rows from the top, each from the left); the bridge menu has
 * one slot per cell in that order. {@code left} is the leftmost column counted along {@link #across()}, {@code top} the
 * top row's height.
 */
public record CrafterGroup(Direction facing, List<BlockPos> cells, int left, int top, int width, int height) {
    /** The largest group the camera frames: Create's crafters build up to 10×10 (DESIGN §10). */
    public static final int MAX = 10;
    /** How many crafters of a wall are looked at, at most: a wall this big is too big anyway. */
    private static final int SEARCH = 2 * MAX * MAX;
    /** How far out from a crafter's centre Create draws its window's item. */
    public static final double WINDOW_OUT = 0.58;

    /** The group the crafter at {@code pos} is worked in, or null (not a crafter, or too big for a scene). */
    @Nullable
    public static CrafterGroup of(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!AllBlocks.MECHANICAL_CRAFTER.has(state)) {
            return null;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        List<BlockPos> wall = wall(level, pos, facing);
        CrafterGroup group = wall == null ? null : of(facing, wall);
        if (group != null && group.fits()) {
            return group;
        }
        // стена больше 10×10: тогда только цепочка рецепта, в которой стоит этот крафтер
        if (level.getBlockEntity(pos) instanceof MechanicalCrafterBlockEntity crafter) {
            List<MechanicalCrafterBlockEntity> chain = RecipeGridHandler.getAllCraftersOfChain(crafter);
            if (chain != null) {
                List<BlockPos> cells = new ArrayList<>();
                for (MechanicalCrafterBlockEntity linked : chain) {
                    cells.add(linked.getBlockPos().immutable());
                }
                group = of(facing, cells);
                if (group.fits()) {
                    return group;
                }
            }
        }
        return null;
    }

    /** The crafters touching this one in the plane of its front, and those touching them, and so on (null: too many). */
    @Nullable
    private static List<BlockPos> wall(BlockGetter level, BlockPos start, Direction facing) {
        Direction across = across(facing);
        List<Direction> steps = List.of(Direction.UP, Direction.DOWN, across, across.getOpposite());
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(start.immutable());
        seen.add(start.immutable());
        while (!open.isEmpty()) {
            BlockPos at = open.poll();
            found.add(at);
            if (found.size() > SEARCH) {
                return null;
            }
            for (Direction step : steps) {
                BlockPos next = at.relative(step);
                if (seen.add(next) && isCrafter(level, next, facing)) {
                    open.add(next);
                }
            }
        }
        return found;
    }

    private static boolean isCrafter(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return AllBlocks.MECHANICAL_CRAFTER.has(state) && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    /** The group of these cells (any order), measured and put in reading order. */
    public static CrafterGroup of(Direction facing, List<BlockPos> cells) {
        Direction across = across(facing);
        int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE, top = Integer.MIN_VALUE, bottom = Integer.MAX_VALUE;
        for (BlockPos cell : cells) {
            int u = u(cell, across);
            left = Math.min(left, u);
            right = Math.max(right, u);
            top = Math.max(top, cell.getY());
            bottom = Math.min(bottom, cell.getY());
        }
        int l = left, t = top;
        List<BlockPos> ordered = new ArrayList<>(cells);
        ordered.sort(Comparator.<BlockPos>comparingInt(cell -> t - cell.getY()).thenComparingInt(cell -> u(cell, across) - l));
        return new CrafterGroup(facing, List.copyOf(ordered), left, top, right - left + 1, top - bottom + 1);
    }

    /** The player's right, looking at the fronts. */
    public static Direction across(Direction facing) {
        return facing.getOpposite().getClockWise();
    }

    public Direction across() {
        return across(facing);
    }

    private static int u(BlockPos pos, Direction across) {
        return pos.getX() * across.getStepX() + pos.getZ() * across.getStepZ();
    }

    public boolean fits() {
        return width <= MAX && height <= MAX;
    }

    public int column(BlockPos cell) {
        return u(cell, across()) - left;
    }

    public int row(BlockPos cell) {
        return top - cell.getY();
    }

    /** The cell at this column and row, or -1 when no crafter of the group stands there. */
    public int index(int column, int row) {
        for (int i = 0; i < cells.size(); i++) {
            BlockPos cell = cells.get(i);
            if (column(cell) == column && row(cell) == row) {
                return i;
            }
        }
        return -1;
    }

    public Vec3 face(BlockPos cell) {
        return Vec3.atCenterOf(cell).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5));
    }

    /** Where Create draws a window's item, and so where the scene puts one: just out from the crafter's front. */
    public Vec3 window(BlockPos cell) {
        return Vec3.atCenterOf(cell).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(WINDOW_OUT));
    }

    /** A point on the plane of the fronts, by column and row (fractions allowed: -0.5 is the left edge). */
    public Vec3 point(double column, double row) {
        BlockPos first = cells.get(0);
        Vec3 across = Vec3.atLowerCornerOf(across().getNormal());
        return face(first).add(across.scale(column - column(first))).add(0, row(first) - row, 0);
    }

    public Vec3 centre() {
        return point((width - 1) / 2.0, (height - 1) / 2.0);
    }

    /** One crafter by itself (the scene's fallback when its group cannot be found on the client). */
    public static CrafterGroup single(BlockPos pos, Direction facing) {
        return of(facing, List.of(pos.immutable()));
    }

    /** For the bridge menu's client copy: the server's group, so both build the same slots. */
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(facing);
        buf.writeVarInt(cells.size());
        for (BlockPos cell : cells) {
            buf.writeBlockPos(cell);
        }
    }

    public static CrafterGroup read(RegistryFriendlyByteBuf buf) {
        Direction facing = buf.readEnum(Direction.class);
        int size = Math.min(buf.readVarInt(), MAX * MAX);
        List<BlockPos> cells = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            cells.add(buf.readBlockPos());
        }
        return of(facing, cells);
    }
}
