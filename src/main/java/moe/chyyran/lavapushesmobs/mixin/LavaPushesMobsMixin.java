package moe.chyyran.lavapushesmobs.mixin;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static moe.chyyran.lavapushesmobs.LavaPushesMobsMod.LOGGER;

@Mixin(Entity.class)
public class LavaPushesMobsMixin {
    @Inject(method = "handleWaterMovement", at = @At("TAIL"))
    private void handleLavaMovement(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity)(Object)this;

        if (!entity.isImmuneToFire()) {
            return;
        }

        // Get entity's bounding box
        AxisAlignedBB entityBB = entity.getEntityBoundingBox();

        // Check all blocks that intersect with the entity's bounding box
        int minX = MathHelper.floor(entityBB.minX);
        int maxX = MathHelper.ceil(entityBB.maxX);
        int minY = MathHelper.floor(entityBB.minY);
        int maxY = MathHelper.ceil(entityBB.maxY);
        int minZ = MathHelper.floor(entityBB.minZ);
        int maxZ = MathHelper.ceil(entityBB.maxZ);

        boolean inLava = false;
        Vec3d totalFlow = Vec3d.ZERO;
        int flowSamples = 0;

        // Check each block position
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    IBlockState state = entity.world.getBlockState(pos);

                    if (state.getMaterial() == Material.LAVA) {
                        double lavaHeight = getLavaHeight(entity.world, pos, state);
                        double blockBottom = pos.getY();
                        double blockTop = blockBottom + lavaHeight;

                        // Check if entity's bounding box intersects with the lava
                        if (blockTop > entityBB.minY && blockBottom < entityBB.maxY) {
                            inLava = true;

                            // Sample flow at this position
                            Vec3d flow = getLavaFlow(entity.world, pos);
                            totalFlow = totalFlow.add(flow);
                            flowSamples++;
                        }
                    }
                }
            }
        }

        if (inLava && flowSamples > 0) {
            // Average the flow vectors and apply movement
            Vec3d averageFlow = totalFlow.scale(1.0D / flowSamples);

            double pushStrength = 0.014D;
            entity.motionX += averageFlow.x * pushStrength;
            entity.motionY += averageFlow.y * pushStrength;
            entity.motionZ += averageFlow.z * pushStrength;
        }
    }

    private double getLavaHeight(World world, BlockPos pos, IBlockState state) {
        if (state.getMaterial() != Material.LAVA) {
            return 0.0D;
        }

        // For BlockLiquid (vanilla lava)
        if (state.getBlock() instanceof BlockLiquid) {
            int level = state.getValue(BlockLiquid.LEVEL);

            if (level >= 8) {
                // Source block - full height
                return 1.0D;
            } else {
                // Flowing lava - calculate height based on level
                // Level 0 = highest (7/8 height), Level 7 = lowest (1/8 height)
                return (8.0D - level) / 8.0D;
            }
        }

        // For modded lava blocks, assume full height
        return 1.0D;
    }

    private Vec3d getLavaFlow(World world, BlockPos pos) {
        double flowX = 0.0D;
        double flowZ = 0.0D;

        IBlockState centerState = world.getBlockState(pos);
        if (centerState.getMaterial() != Material.LAVA) {
            return Vec3d.ZERO;
        }

        int centerLevel = getLavaLevel(centerState);

        // Check all horizontal directions for flow
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos adjacentPos = pos.offset(facing);
            IBlockState adjacentState = world.getBlockState(adjacentPos);

            int flowPressure = getFlowPressure(world, adjacentPos, adjacentState, centerLevel);

            if (flowPressure > 0) {
                double flowStrength = (double)flowPressure;
                flowX += facing.getXOffset() * flowStrength;
                flowZ += facing.getZOffset() * flowStrength;
            }
        }

        // Normalize the flow vector
        Vec3d flow = new Vec3d(flowX, 0.0D, flowZ);
        double length = flow.length();
        if (length > 0.0D) {
            flow = flow.scale(1.0D / length);
        }

        return flow;
    }

    private int getLavaLevel(IBlockState state) {
        if (state.getMaterial() != Material.LAVA) {
            return -1;
        }

        if (state.getBlock() instanceof BlockLiquid) {
            int level = state.getValue(BlockLiquid.LEVEL);
            // Convert to flow pressure (higher level = higher pressure)
            return level >= 8 ? 8 : (8 - level);
        }

        return 8; // Assume source block
    }

    private int getFlowPressure(World world, BlockPos pos, IBlockState state, int centerLevel) {
        int adjacentLevel = getLavaLevel(state);

        if (adjacentLevel < 0) {
            // Not lava - check if it can be flowed into
            if (canLavaFlowInto(world, pos, state)) {
                return centerLevel; // Full pressure toward flowable space
            }
            return 0; // Can't flow here
        }

        // Both are lava - flow from higher pressure to lower
        return Math.max(0, centerLevel - adjacentLevel);
    }

    private boolean canLavaFlowInto(World world, BlockPos pos, IBlockState state) {
        return state.getBlock().isReplaceable(world, pos) ||
                state.getMaterial() == Material.AIR ||
                state.getMaterial() == Material.FIRE;
    }
}
