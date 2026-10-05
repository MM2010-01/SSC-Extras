package sscextras.drake;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class DrakeStableLayout {
    private DrakeStableLayout() { }

    // Saved bounds distinguish the original single row from the mansion's facing rows.
    public static boolean facingRows(BlockBox box) { return box.getBlockCountZ() == 27; }
    public static int stallCount(BlockBox box) { return (box.getBlockCountX() - 1) / 7 * (facingRows(box) ? 2 : 1); }
    public static int roamRange(BlockBox box) { return stallCount(box) == 6 ? 100 : 64; }
    public static int captureRange(BlockBox box) { return stallCount(box) == 6 ? 125 : 128; }

    public static int column(BlockBox box, int stall) { return facingRows(box) ? stall % 3 : stall; }
    public static int rowZ(BlockBox box, int stall, int z) {
        return !facingRows(box) ? z : stall < 3 ? 12 - z : 14 + z;
    }
    public static BlockPos position(BlockBox box, int stall, int x, int z) {
        return new BlockPos(box.getMinX() + column(box, stall) * 7 + x, box.getMinY() + 1,
                box.getMinZ() + rowZ(box, stall, z));
    }
    public static BlockPos bed(BlockBox box, int stall) { return position(box, stall, 3, 8); }
    public static BlockPos gate(BlockBox box, int stall) { return position(box, stall, 3, 3); }
    public static BlockPos tie(BlockBox box, int stall) { return position(box, stall, 3, 12); }
    public static BlockPos sign(BlockBox box, int stall) { return position(box, stall, 2, 2); }
    public static Box stall(BlockBox box, int stall) {
        var bed = bed(box, stall);
        int gate = gate(box, stall).getZ(), tie = tie(box, stall).getZ();
        return new Box(bed.getX() - 2, bed.getY(), Math.min(gate, tie) + 1,
                bed.getX() + 4, bed.getY() + 4, Math.max(gate, tie));
    }
    public static int inward(BlockBox box, BlockPos gate) {
        return facingRows(box) && gate.getZ() < box.getCenter().getZ() ? -1 : 1;
    }
    public static float outwardYaw(BlockBox box, BlockPos gate) { return inward(box, gate) > 0 ? 180 : 0; }
    public static Vec3d gatePoint(BlockBox box, BlockPos gate, double distance) {
        int direction = inward(box, gate);
        return new Vec3d(gate.getX() + 1, gate.getY(), gate.getZ() + (direction < 0 ? 1 : 0) + direction * distance);
    }
    public static boolean outsideGate(BlockBox box, BlockPos gate, Box entity, double clearance) {
        return inward(box, gate) > 0 ? entity.maxZ < gate.getZ() - clearance : entity.minZ > gate.getZ() + 1 + clearance;
    }
    public static boolean insideGate(BlockBox box, BlockPos gate, Box entity, double clearance) {
        return inward(box, gate) > 0 ? entity.minZ >= gate.getZ() + 1 + clearance : entity.maxZ <= gate.getZ() - clearance;
    }
    public static BlockPos mansionOrigin(BlockBox mansion, BlockBox entrance, BlockRotation rotation) {
        var center = entrance.getCenter();
        // Keep 24 clear blocks beyond the side wall, beside the entrance rather than across its approach.
        return switch (rotation.rotate(Direction.SOUTH)) {
            case SOUTH -> new BlockPos(center.getX() - 11, 0, mansion.getMaxZ() + 25);
            case NORTH -> new BlockPos(center.getX() - 11, 0, mansion.getMinZ() - 51);
            case EAST -> new BlockPos(mansion.getMaxX() + 25, 0, center.getZ() - 13);
            case WEST -> new BlockPos(mansion.getMinX() - 46, 0, center.getZ() - 13);
            default -> throw new IllegalArgumentException("Vertical mansion entrance");
        };
    }
}
