package cam72cam.mod.block.tile;

import cam72cam.mod.math.Vec3i;
import cam72cam.mod.net.Packet;

public class BlockEntityUpdatePacket extends Packet {
    public BlockEntityUpdatePacket() {
    }

    public BlockEntityUpdatePacket(TileEntity entity) {
        data.setVec3i("pos", new Vec3i(entity.getPos()));
        data.set("data", entity.getUpdateTag());
    }

    @Override
    protected void handle() {
        net.minecraft.block.entity.BlockEntity te = getWorld().internal.getBlockEntity(data.getVec3i("pos").internal);
        if (te instanceof TileEntity) {
            ((TileEntity)te).handleUpdateTag(data.get("data"));
        }
    }
}
