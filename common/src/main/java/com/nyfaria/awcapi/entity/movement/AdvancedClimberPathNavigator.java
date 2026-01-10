package com.nyfaria.awcapi.entity.movement;

import com.google.common.collect.ImmutableSet;
import com.nyfaria.awcapi.entity.IAdvancedClimber;
import com.nyfaria.awcapi.entity.Orientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Advanced climber path navigator that handles pathfinding on walls and ceilings.
 */
public class AdvancedClimberPathNavigator<T extends Mob & IAdvancedClimber> extends AdvancedGroundPathNavigator<T> {
    protected final IAdvancedClimber climber;

    protected Direction verticalFacing = Direction.DOWN;
    protected boolean findDirectPathPoints = false;

    public AdvancedClimberPathNavigator(T entity, Level worldIn, boolean checkObstructions, boolean canPathWalls, boolean canPathCeiling) {
        super(entity, worldIn, checkObstructions);

        this.climber = entity;

        if (this.nodeEvaluator instanceof AdvancedWalkNodeProcessor processor) {
            processor.setStartPathOnGround(false);
            processor.setCanPathWalls(canPathWalls);
            processor.setCanPathCeiling(canPathCeiling);
        }
    }

    @Override
    protected Vec3 getTempMobPos() {
        return this.mob.position().add(0, this.mob.getBbHeight() / 2.0f, 0);
    }

    @Override
    @Nullable
    public Path createPath(BlockPos pos, int checkpointRange) {
        return this.createPath(ImmutableSet.of(pos), 8, false, checkpointRange);
    }

    @Override
    @Nullable
    public Path createPath(Entity entityIn, int checkpointRange) {
        return this.createPath(ImmutableSet.of(entityIn.blockPosition()), 16, true, checkpointRange);
    }

    @Override
    public void tick() {
        ++this.tick;

        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }

        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.followThePath();
            } else if (this.path != null && !this.path.isDone()) {
                Vec3 pos = this.getTempMobPos();
                Vec3 targetPos = this.path.getNextEntityPos(this.mob);

                if (pos.y > targetPos.y && !this.mob.onGround() && Mth.floor(pos.x) == Mth.floor(targetPos.x) && Mth.floor(pos.z) == Mth.floor(targetPos.z)) {
                    this.path.advance();
                }
            }

            DebugPackets.sendPathFindingPacket(this.level, this.mob, this.path, this.maxDistanceToWaypoint);

            if (!this.isDone()) {
                Node targetPoint = this.path.getNode(this.path.getNextNodeIndex());

                Direction dir = null;

                if (targetPoint instanceof DirectionalPathPoint directionalPoint) {
                    dir = directionalPoint.getPathSide();
                }

                if (dir == null) {
                    dir = Direction.DOWN;
                }

                Vec3 targetPos = this.getExactPathingTarget(this.level, targetPoint.asBlockPos(), dir);

                MoveControl moveController = this.mob.getMoveControl();

                if (moveController instanceof ClimberMoveController && targetPoint instanceof DirectionalPathPoint directionalPoint && directionalPoint.getPathSide() != null) {
                    ((ClimberMoveController<?>) moveController).setMoveTo(targetPos.x, targetPos.y, targetPos.z, targetPoint.asBlockPos().relative(dir), directionalPoint.getPathSide(), this.speedModifier);
                } else {
                    moveController.setWantedPosition(targetPos.x, targetPos.y, targetPos.z, this.speedModifier);
                }
            }
        }
    }

    public Vec3 getExactPathingTarget(BlockGetter blockaccess, BlockPos pos, Direction dir) {
        BlockPos offsetPos = pos.relative(dir);

        VoxelShape shape = blockaccess.getBlockState(offsetPos).getCollisionShape(blockaccess, offsetPos);

        Direction.Axis axis = dir.getAxis();

        int sign = dir.getStepX() + dir.getStepY() + dir.getStepZ();
        double offset = shape.isEmpty() ? sign : (sign > 0 ? shape.min(axis) - 1 : shape.max(axis));

        double marginXZ = 1 - (this.mob.getBbWidth() % 1);
        double marginY = 1 - (this.mob.getBbHeight() % 1);

        double pathingOffsetXZ = (int) (this.mob.getBbWidth() + 1.0F) * 0.5D;
        double pathingOffsetY = (int) (this.mob.getBbHeight() + 1.0F) * 0.5D - this.mob.getBbHeight() * 0.5f;

        double x = offsetPos.getX() + pathingOffsetXZ + dir.getStepX() * marginXZ;
        double y = offsetPos.getY() + pathingOffsetY + (dir == Direction.DOWN ? -pathingOffsetY : 0.0D) + (dir == Direction.UP ? -pathingOffsetY + marginY : 0.0D);
        double z = offsetPos.getZ() + pathingOffsetXZ + dir.getStepZ() * marginXZ;

        return switch (axis) {
            case X -> new Vec3(x + offset, y, z);
            case Y -> new Vec3(x, y + offset, z);
            case Z -> new Vec3(x, y, z + offset);
        };
    }

    @Override
    protected void followThePath() {
        Vec3 pos = this.getTempMobPos();

        this.maxDistanceToWaypoint = this.mob.getBbWidth() > 0.75F ? this.mob.getBbWidth() / 2.0F : 0.75F - this.mob.getBbWidth() / 2.0F;
        float maxDistanceToWaypointY = Math.max(1, this.mob.getBbHeight() > 0.75F ? this.mob.getBbHeight() / 2.0F : 0.75F - this.mob.getBbHeight() / 2.0F);

        int sizeX = Mth.ceil(this.mob.getBbWidth());
        int sizeY = Mth.ceil(this.mob.getBbHeight());
        int sizeZ = sizeX;

        Orientation orientation = this.climber.getOrientation();
        Vec3 upVector = orientation.getGlobal(this.mob.yRot, -90);

        this.verticalFacing = Direction.getApproximateNearest((float) upVector.x, (float) upVector.y, (float) upVector.z);

        // Look up to 4 nodes ahead so it doesn't backtrack
        for (int i = 4; i >= 0; i--) {
            if (this.path.getNextNodeIndex() + i < this.path.getNodeCount()) {
                Node currentTarget = this.path.getNode(this.path.getNextNodeIndex() + i);

                double dx = Math.abs(currentTarget.x + (int) (this.mob.getBbWidth() + 1.0f) * 0.5f - this.mob.getX());
                double dy = Math.abs(currentTarget.y - this.mob.getY());
                double dz = Math.abs(currentTarget.z + (int) (this.mob.getBbWidth() + 1.0f) * 0.5f - this.mob.getZ());

                boolean isWaypointInReach = dx < this.maxDistanceToWaypoint && dy < maxDistanceToWaypointY && dz < this.maxDistanceToWaypoint;

                boolean isOnSameSideAsTarget = false;
                if (this.canFloat() && (currentTarget.type == PathType.WATER || currentTarget.type == PathType.WATER_BORDER || currentTarget.type == PathType.LAVA)) {
                    isOnSameSideAsTarget = true;
                } else if (currentTarget instanceof DirectionalPathPoint directionalPoint) {
                    Direction targetSide = directionalPoint.getPathSide();
                    isOnSameSideAsTarget = targetSide == null || this.climber.getGroundDirection().getLeft() == targetSide;
                } else {
                    isOnSameSideAsTarget = true;
                }

                if (isOnSameSideAsTarget && (isWaypointInReach || (i == 0 && this.mob.getNavigation().canCutCorner(this.path.getNextNode().type) && this.isNextTargetInLine(pos, sizeX, sizeY, sizeZ, 1 + i)))) {
                    this.path.setNextNodeIndex(this.path.getNextNodeIndex() + 1 + i);
                    break;
                }
            }
        }

        this.doStuckDetection(pos);
    }

    private boolean isNextTargetInLine(Vec3 pos, int sizeX, int sizeY, int sizeZ, int offset) {
        if (this.path.getNextNodeIndex() + offset >= this.path.getNodeCount()) {
            return false;
        } else {
            Vec3 currentTarget = Vec3.atBottomCenterOf(this.path.getNextNodePos());

            if (!pos.closerThan(currentTarget, 2.0D)) {
                return false;
            } else {
                Vec3 nextTarget = Vec3.atBottomCenterOf(this.path.getNodePos(this.path.getNextNodeIndex() + offset));
                Vec3 targetDir = nextTarget.subtract(currentTarget);
                Vec3 currentDir = pos.subtract(currentTarget);

                if (targetDir.dot(currentDir) > 0.0D) {
                    return true;
                }

                return false;
            }
        }
    }

    @Override
    protected boolean canMoveDirectly(Vec3 start, Vec3 end) {
        return switch (this.verticalFacing.getAxis()) {
            case X -> this.isDirectPathBetweenPoints(start, end, Direction.Axis.Z, Direction.Axis.X, Direction.Axis.Y, this.verticalFacing.getStepX() < 0);
            case Y -> this.isDirectPathBetweenPoints(start, end, Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z, this.verticalFacing.getStepY() < 0);
            case Z -> this.isDirectPathBetweenPoints(start, end, Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X, this.verticalFacing.getStepZ() < 0);
        };
    }

    protected boolean isDirectPathBetweenPoints(Vec3 start, Vec3 end, Direction.Axis ax, Direction.Axis ay, Direction.Axis az, boolean invertY) {
        int bx = Mth.floor(swizzle(start, ax));
        int bz = Mth.floor(swizzle(start, az));
        double dx = swizzle(end, ax) - swizzle(start, ax);
        double dz = swizzle(end, az) - swizzle(start, az);
        double dSq = dx * dx + dz * dz;

        if (dSq < 1.0E-8D) {
            return false;
        }

        return true;
    }

    protected static double swizzle(Vec3 vec, Direction.Axis axis) {
        return switch (axis) {
            case X -> vec.x;
            case Y -> vec.y;
            case Z -> vec.z;
        };
    }
}

