package sscextras.drake;

import net.minecraft.block.Blocks;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

final class MountMerchantSigns {
    private static final int[] OFFSETS = {6, 10, 11};
    private final UUID[] listings = new UUID[OFFSETS.length];

    static BlockPos position(BlockPos site, int slot) { return site.add(OFFSETS[slot], 1, 2); }

    void update(ServerWorld world, BlockPos site, List<MountMerchantEntity.Listing> offers) {
        for (int i = 0; i < listings.length; i++) {
            if (listings[i] != null && find(offers, listings[i]) == null) listings[i] = null;
        }
        for (var offer : offers) {
            boolean assigned = false;
            for (var id : listings) if (offer.id().equals(id)) { assigned = true; break; }
            if (assigned) continue;
            for (int i = 0; i < listings.length; i++) if (listings[i] == null) { listings[i] = offer.id(); break; }
        }
        for (int i = 0; i < listings.length; i++) {
            var pos = position(site, i);
            if (world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSolidBlock(world, pos.down()))
                world.setBlockState(pos, Blocks.DARK_OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, 8));
            if (!(world.getBlockEntity(pos) instanceof SignBlockEntity sign)) continue;
            var offer = find(offers, listings[i]);
            var text = new SignText();
            if (offer != null) text = text.withMessage(1, offer.name()).withMessage(2, Text.translatable("screen.ssc-extras.merchant.cost", offer.price()));
            if (!Arrays.equals(sign.getFrontText().getMessages(false), text.getMessages(false))) {
                sign.setText(text, true); sign.setText(text, false); sign.markDirty();
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
        }
    }

    private static MountMerchantEntity.Listing find(List<MountMerchantEntity.Listing> offers, UUID id) {
        for (var offer : offers) if (offer.id().equals(id)) return offer;
        return null;
    }

    void write(NbtCompound tag) {
        for (int i = 0; i < listings.length; i++) if (listings[i] != null) tag.putUuid("MarketSign" + i, listings[i]);
    }

    void read(NbtCompound tag) {
        for (int i = 0; i < listings.length; i++) listings[i] = tag.containsUuid("MarketSign" + i) ? tag.getUuid("MarketSign" + i) : null;
    }
}
