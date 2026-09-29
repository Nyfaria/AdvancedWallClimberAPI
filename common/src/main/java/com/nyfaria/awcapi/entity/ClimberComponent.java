package com.nyfaria.awcapi.entity;

import com.nyfaria.awcapi.util.CollisionSmoothingUtil;
import com.nyfaria.awcapi.util.Matrix4f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Component that handles all the climbing logic for an entity.
 * Attach this to your entity and delegate climbing-related methods to it.
 */
public class ClimberComponent {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int MAX_ATTACHED_TICKS = 5;
    private static final int SOLVE_ITERATIONS = 3;
    private static final double SOLVE_CONVERGENCE_DOT = 0.9999D;
    private static final int SOLVE_REFRESH_TICKS = 10;
    private static final double SOLVE_REUSE_DISTANCE_SQ = 1.0E-8D;
    private static final double CONTACT_OVERLAP_MARGIN = 0.2D;
    private static final double CONTACT_DISTANCE = 0.05D;
    private static final double MIN_ATTACH_DIRECTION = 0.25D;
    private static final double BLOCKED_MOVEMENT_RATIO = 0.35D;
    private static final double CLIPPED_MOVEMENT_RATIO = 0.7D;
    private static final double MIN_CLIMB_DIRECTION = 0.1D;
    private static final int DETACH_TICKS = 8;

    private final Mob mob;
    private final IAdvancedClimber climber;

    private double prevAttachmentOffsetX, prevAttachmentOffsetY, prevAttachmentOffsetZ;
    private double attachmentOffsetX, attachmentOffsetY, attachmentOffsetZ;

    private Vec3 attachmentNormal = UP;
    private Vec3 prevAttachmentNormal = UP;

    private double lastAttachmentOffsetX, lastAttachmentOffsetY, lastAttachmentOffsetZ;
    private Vec3 lastAttachmentOrientationNormal = UP;

    private int attachedTicks = MAX_ATTACHED_TICKS;

    private Vec3 attachedSides = new Vec3(0, 0, 0);
    private Vec3 prevAttachedSides = new Vec3(0, 0, 0);

    private boolean canClimbInWater = false;
    private boolean canClimbInLava = false;

    private boolean isTravelingInFluid = false;

    private float collisionsInclusionRange = 2.0f;
    private float collisionsSmoothingRange = 1.25f;

    private Orientation orientation;
    private Pair<Direction, Vec3> groundDirection = Pair.of(Direction.DOWN, new Vec3(0, -1, 0));
    private final double[] facingDistances = new double[Direction.values().length];

    private Orientation renderOrientation;

    private Vec3 preWalkingPosition;

    private double preMoveY;

    private Vec3 jumpDir;

    private int detachTicks;
    private boolean detachOnJump;

    private boolean hasSolve;
    private boolean solvedAttached;
    private boolean solveConverged;
    private double solvedX, solvedY, solvedZ;
    private int ticksSinceSolve;

    // Data for syncing
    private float movementTargetX, movementTargetY, movementTargetZ;
    private final List<Direction> pathingSides = new ArrayList<>(List.of(
            Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.DOWN,
            Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.DOWN
    ));
    private final List<Optional<BlockPos>> pathingTargets = new ArrayList<>(List.of(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
    ));

    public ClimberComponent(Mob mob) {
        this.mob = mob;
        if (!(mob instanceof IAdvancedClimber)) {
            throw new IllegalArgumentException("Mob must implement IAdvancedClimber");
        }
        this.climber = (IAdvancedClimber) mob;
        this.orientation = calculateOrientation(1);
        this.groundDirection = getGroundDirection();
        Arrays.fill(this.facingDistances, Double.MAX_VALUE);
    }

    public void writeToNbt(ValueOutput nbt) {
        nbt.putDouble("awcapi.AttachmentNormalX", this.attachmentNormal.x);
        nbt.putDouble("awcapi.AttachmentNormalY", this.attachmentNormal.y);
        nbt.putDouble("awcapi.AttachmentNormalZ", this.attachmentNormal.z);
        nbt.putDouble("awcapi.LastAttachmentNormalX", this.lastAttachmentOrientationNormal.x);
        nbt.putDouble("awcapi.LastAttachmentNormalY", this.lastAttachmentOrientationNormal.y);
        nbt.putDouble("awcapi.LastAttachmentNormalZ", this.lastAttachmentOrientationNormal.z);
        nbt.putDouble("awcapi.LastAttachmentOffsetX", this.lastAttachmentOffsetX);
        nbt.putDouble("awcapi.LastAttachmentOffsetY", this.lastAttachmentOffsetY);
        nbt.putDouble("awcapi.LastAttachmentOffsetZ", this.lastAttachmentOffsetZ);
        nbt.putInt("awcapi.AttachedTicks", this.attachedTicks);
    }

    public void readFromNbt(ValueInput nbt) {
        this.attachmentNormal = safeNormalize(new Vec3(
                nbt.getDoubleOr("awcapi.AttachmentNormalX", 0),
                nbt.getDoubleOr("awcapi.AttachmentNormalY", 1),
                nbt.getDoubleOr("awcapi.AttachmentNormalZ", 0)), UP);
        this.prevAttachmentNormal = this.attachmentNormal;
        this.lastAttachmentOrientationNormal = safeNormalize(new Vec3(
                nbt.getDoubleOr("awcapi.LastAttachmentNormalX", this.attachmentNormal.x),
                nbt.getDoubleOr("awcapi.LastAttachmentNormalY", this.attachmentNormal.y),
                nbt.getDoubleOr("awcapi.LastAttachmentNormalZ", this.attachmentNormal.z)), this.attachmentNormal);
        this.lastAttachmentOffsetX = nbt.getDoubleOr("awcapi.LastAttachmentOffsetX", 0);
        this.lastAttachmentOffsetY = nbt.getDoubleOr("awcapi.LastAttachmentOffsetY", 0);
        this.lastAttachmentOffsetZ = nbt.getDoubleOr("awcapi.LastAttachmentOffsetZ", 0);
        this.attachedTicks = Mth.clamp(nbt.getIntOr("awcapi.AttachedTicks", MAX_ATTACHED_TICKS), 0, MAX_ATTACHED_TICKS);
        this.hasSolve = false;
        this.orientation = calculateOrientation(1);
    }

    public boolean canClimbInWater() {
        return canClimbInWater;
    }

    public void setCanClimbInWater(boolean value) {
        this.canClimbInWater = value;
    }

    public boolean canClimbInLava() {
        return canClimbInLava;
    }

    public void setCanClimbInLava(boolean value) {
        this.canClimbInLava = value;
    }

    public float getCollisionsInclusionRange() {
        return collisionsInclusionRange;
    }

    public void setCollisionsInclusionRange(float range) {
        this.collisionsInclusionRange = range;
        this.hasSolve = false;
    }

    public float getCollisionsSmoothingRange() {
        return collisionsSmoothingRange;
    }

    public void setCollisionsSmoothingRange(float range) {
        this.collisionsSmoothingRange = range;
        this.hasSolve = false;
    }

    public float getMovementSpeed() {
        AttributeInstance attribute = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        return attribute != null ? (float) attribute.getValue() : 1.0f;
    }

    public Pair<Direction, Vec3> getGroundDirection() {
        return groundDirection;
    }

    public Direction getGroundSide() {
        return groundDirection.getKey();
    }

    public boolean isTouchingSide(Direction side) {
        return groundDirection.getKey() == side || facingDistances[side.ordinal()] < CONTACT_DISTANCE;
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public void setRenderOrientation(Orientation orientation) {
        this.renderOrientation = orientation;
    }

    public Orientation getRenderOrientation() {
        return renderOrientation;
    }

    public float getAttachmentOffset(Direction.Axis axis, float partialTicks) {
        return switch (axis) {
            case X -> (float) (prevAttachmentOffsetX + (attachmentOffsetX - prevAttachmentOffsetX) * partialTicks);
            case Y -> (float) (prevAttachmentOffsetY + (attachmentOffsetY - prevAttachmentOffsetY) * partialTicks);
            case Z -> (float) (prevAttachmentOffsetZ + (attachmentOffsetZ - prevAttachmentOffsetZ) * partialTicks);
        };
    }

    public void setJumpDirection(@Nullable Vec3 dir) {
        this.jumpDir = dir != null ? dir.normalize() : null;
    }

    public Vec3 getJumpDirection() {
        return jumpDir;
    }

    public void setDetachOnJump(boolean detach) {
        this.detachOnJump = detach;
    }

    public Vec3 getAttachmentNormal() {
        return attachmentNormal;
    }

    @Nullable
    public Vec3 getTrackedMovementTarget() {
        if (climber.shouldTrackPathingTargets()) {
            return new Vec3(movementTargetX, movementTargetY, movementTargetZ);
        }
        return null;
    }

    @Nullable
    public List<PathingTarget> getTrackedPathingTargets() {
        if (climber.shouldTrackPathingTargets()) {
            List<PathingTarget> targets = new ArrayList<>(pathingTargets.size());
            int i = 0;
            for (Optional<BlockPos> key : pathingTargets) {
                BlockPos pos = key.orElse(null);
                if (pos != null) {
                    targets.add(new PathingTarget(pos, pathingSides.get(i)));
                }
                i++;
            }
            return targets;
        }
        return null;
    }

    // ==================== CORE TICK LOGIC ====================

    /**
     * Call this in the entity's tick method.
     */
    public void tick() {
        if (!mob.level().isClientSide()) {
            Orientation orientation = getOrientation();

            if (climber.shouldTrackPathingTargets()) {
                if (mob.xxa != 0) {
                    Vec3 forwardVector = orientation.getGlobal(mob.getYRot(), 0);
                    Vec3 strafeVector = orientation.getGlobal(mob.getYRot() + 90.0f, 0);
                    Vec3 offset = forwardVector.scale(mob.zza).add(strafeVector.scale(mob.xxa)).normalize();

                    movementTargetX = (float) (mob.getX() + offset.x);
                    movementTargetY = (float) (mob.getY() + mob.getBbHeight() * 0.5f + offset.y);
                    movementTargetZ = (float) (mob.getZ() + offset.z);
                } else {
                    movementTargetX = (float) mob.getMoveControl().getWantedX();
                    movementTargetY = (float) mob.getMoveControl().getWantedY();
                    movementTargetZ = (float) mob.getMoveControl().getWantedZ();
                }

                Path path = mob.getNavigation().getPath();
                if (path != null) {
                    for (int i = 0; i < pathingTargets.size(); i++) {
                        if (path.getNextNodeIndex() + i < path.getNodeCount()) {
                            Node point = path.getNode(path.getNextNodeIndex() + i);
                            pathingTargets.set(i, Optional.of(new BlockPos(point.x, point.y, point.z)));
                        } else {
                            pathingTargets.set(i, Optional.empty());
                            pathingSides.set(i, Direction.DOWN);
                        }
                    }
                } else {
                    for (int i = 0; i < pathingTargets.size(); i++) {
                        pathingTargets.set(i, Optional.empty());
                    }
                    for (int i = 0; i < pathingSides.size(); i++) {
                        pathingSides.set(i, Direction.DOWN);
                    }
                }
            }
        }
    }

    /**
     * Call this in the entity's aiStep/livingTick method.
     */
    public void livingTick() {
        if (detachTicks > 0) {
            detachTicks--;
        }

        prevAttachmentOffsetX = attachmentOffsetX;
        prevAttachmentOffsetY = attachmentOffsetY;
        prevAttachmentOffsetZ = attachmentOffsetZ;
        prevAttachmentNormal = attachmentNormal;

        updateWalkingSide();
    }

    // ==================== WALKING SIDE LOGIC ====================

    private void updateWalkingSide() {
        AABB entityBox = mob.getBoundingBox();
        double closestFacingDst = Double.MAX_VALUE;
        Direction closestFacing = null;
        Vec3 weighting = new Vec3(0, 0, 0);

        float stickingDistance = mob.zza != 0 ? 1.5f : 0.1f;

        AABB grownBox = entityBox.inflate(0.2f);
        List<AABB> collisionBoxes = getCollisionBoxes(grownBox.inflate(stickingDistance));

        if (!computeFacingDistances(collisionBoxes, entityBox, grownBox, stickingDistance, 0.0D)) {
            computeFacingDistances(collisionBoxes, entityBox, grownBox, stickingDistance, CONTACT_OVERLAP_MARGIN);
        }

        for (Direction facing : Direction.values()) {
            double closestDst = facingDistances[facing.ordinal()];

            if (closestDst < closestFacingDst) {
                closestFacingDst = closestDst;
                closestFacing = facing;
            }

            if (closestDst < Double.MAX_VALUE) {
                weighting = weighting.add(new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()).scale(1 - Math.min(closestDst, stickingDistance) / stickingDistance));
            }
        }

        if (closestFacing == null) {
            groundDirection = Pair.of(Direction.DOWN, new Vec3(0, -1, 0));
        } else {
            groundDirection = Pair.of(closestFacing, safeNormalize(safeNormalize(weighting, new Vec3(0, -1, 0)).add(0, -0.001f, 0), new Vec3(0, -1, 0)));
        }
    }

    private boolean computeFacingDistances(List<AABB> collisionBoxes, AABB entityBox, AABB grownBox, float stickingDistance, double margin) {
        boolean contact = false;

        for (Direction facing : Direction.values()) {
            AABB facingRegion = grownBox.expandTowards(
                    facing.getStepX() * stickingDistance,
                    facing.getStepY() * stickingDistance,
                    facing.getStepZ() * stickingDistance);

            double closestDst = Double.MAX_VALUE;

            for (AABB collisionBox : collisionBoxes) {
                if (!collisionBox.intersects(facingRegion)) {
                    continue;
                }

                switch (facing) {
                    case EAST, WEST ->
                            closestDst = Math.min(closestDst, Math.abs(calculateXOffset(entityBox, collisionBox, -facing.getStepX() * stickingDistance, margin)));
                    case UP, DOWN ->
                            closestDst = Math.min(closestDst, Math.abs(calculateYOffset(entityBox, collisionBox, -facing.getStepY() * stickingDistance, margin)));
                    case NORTH, SOUTH ->
                            closestDst = Math.min(closestDst, Math.abs(calculateZOffset(entityBox, collisionBox, -facing.getStepZ() * stickingDistance, margin)));
                }
            }

            facingDistances[facing.ordinal()] = closestDst;

            if (closestDst < stickingDistance - 1.0E-6D) {
                contact = true;
            }
        }

        return contact;
    }

    // ==================== TRAVEL LOGIC ====================

    /**
     * Called during entity travel. Returns true if travel was handled.
     * @param relative The relative movement vector
     * @param pre True if called before default travel, false if after
     * @return True if the default travel should be cancelled
     */
    public boolean onTravel(Vec3 relative, boolean pre) {
        if (pre) {
            boolean canTravel = mob.isEffectiveAi() || mob.isLocalInstanceAuthoritative();
            isTravelingInFluid = false;

            FluidState fluidState = mob.level().getFluidState(mob.blockPosition());

            if (!canClimbInWater && mob.isInWater() && !mob.canStandOnFluid(fluidState)) {
                isTravelingInFluid = true;
                if (canTravel) {
                    return false;
                }
            } else if (!canClimbInLava && mob.isInLava() && !mob.canStandOnFluid(fluidState)) {
                isTravelingInFluid = true;
                if (canTravel) {
                    return false;
                }
            } else if (canTravel) {
                travelOnGround(relative);
            }

            if (!canTravel) {
                mob.calculateEntityAnimation(true);
            }

            return true;
        } else {
            updateOffsetsAndOrientation();
            return false;
        }
    }

    private void travelOnGround(Vec3 relative) {
        Orientation orientation = getOrientation();

        Vec3 forwardVector = orientation.getGlobal(mob.getYRot(), 0);
        Vec3 strafeVector = orientation.getGlobal(mob.getYRot() + 90.0f, 0);
        Vec3 upVector = orientation.getGlobal(mob.getYRot(), -90.0f);

        Pair<Direction, Vec3> groundDirection = getGroundDirection();
        Vec3 stickingForce = detachTicks > 0 ? new Vec3(0, -mob.getGravity(), 0) : getStickingForce(groundDirection);

        boolean isFalling = mob.getDeltaMovement().y <= 0.0D;
        if (isFalling && mob.hasEffect(MobEffects.SLOW_FALLING)) {
            mob.fallDistance = 0;
        }

        float forward = (float) relative.z;
        float strafe = (float) relative.x;

        if (forward != 0 || strafe != 0) {
            float slipperiness = 0.91f;

            if (mob.onGround()) {
                BlockPos offsetPos = new BlockPos(mob.blockPosition()).relative(groundDirection.getLeft());
                slipperiness = climber.getBlockSlipperiness(offsetPos);
            }

            float f = forward * forward + strafe * strafe;
            if (f >= 1.0E-4F) {
                f = Math.max(Mth.sqrt(f), 1.0f);
                f = getRelevantMoveFactor(slipperiness) / f;
                forward *= f;
                strafe *= f;

                Vec3 movementOffset = new Vec3(
                        forwardVector.x * forward + strafeVector.x * strafe,
                        forwardVector.y * forward + strafeVector.y * strafe,
                        forwardVector.z * forward + strafeVector.z * strafe);

                AABB aabb = mob.getBoundingBox();

                Vec3 allowedMovement = collide(aabb, movementOffset);
                double requestedLength = movementOffset.length();
                double blockedLength = movementOffset.subtract(allowedMovement).length();

                if (requestedLength > 1.0E-6D && (allowedMovement.length() < BLOCKED_MOVEMENT_RATIO * requestedLength || blockedLength > CLIPPED_MOVEMENT_RATIO * requestedLength)) {
                    Vec3 climbDir = getObstacleClimbDirection(movementOffset, allowedMovement, upVector);
                    if (climbDir != null) {
                        Vec3 climbMovement = collide(aabb, climbDir.scale(requestedLength));
                        if (climbMovement.length() > 0.5D * requestedLength) {
                            allowedMovement = climbMovement;
                        }
                    }
                }

                Vec3 movementDir = allowedMovement.normalize();

                Vec3 probeVector = new Vec3(
                        Math.abs(movementDir.x) < 0.001D ? -Math.signum(upVector.x) : 0,
                        Math.abs(movementDir.y) < 0.001D ? -Math.signum(upVector.y) : 0,
                        Math.abs(movementDir.z) < 0.001D ? -Math.signum(upVector.z) : 0).normalize().scale(0.0001D);
                Vec3 probed = collide(aabb, probeVector);

                Vec3 collisionNormal = new Vec3(
                        Math.abs(probed.x - probeVector.x) > 0.000001D ? Math.signum(-probeVector.x) : 0,
                        Math.abs(probed.y - probeVector.y) > 0.000001D ? Math.signum(-probeVector.y) : 0,
                        Math.abs(probed.z - probeVector.z) > 0.000001D ? Math.signum(-probeVector.z) : 0).normalize();

                Vec3 surfaceMovementDir = movementDir.subtract(collisionNormal.scale(collisionNormal.dot(movementDir))).normalize();
                boolean isInnerCorner = Math.abs(collisionNormal.x) + Math.abs(collisionNormal.y) + Math.abs(collisionNormal.z) > 1.0001f;

                if (!isInnerCorner) {
                    movementDir = surfaceMovementDir;
                }

                stickingForce = stickingForce.subtract(surfaceMovementDir.scale(surfaceMovementDir.normalize().dot(stickingForce)));

                float moveSpeed = Mth.sqrt(forward * forward + strafe * strafe);
                mob.setDeltaMovement(mob.getDeltaMovement().add(movementDir.scale(moveSpeed)));
            }
        }

        mob.setDeltaMovement(mob.getDeltaMovement().add(stickingForce));

        double px = mob.getX();
        double py = mob.getY();
        double pz = mob.getZ();
        Vec3 motion = mob.getDeltaMovement();

        mob.move(MoverType.SELF, motion);

        prevAttachedSides = attachedSides;
        attachedSides = new Vec3(
                Math.abs(mob.getX() - px - motion.x) > 0.001D ? -Math.signum(motion.x) : 0,
                Math.abs(mob.getY() - py - motion.y) > 0.001D ? -Math.signum(motion.y) : 0,
                Math.abs(mob.getZ() - pz - motion.z) > 0.001D ? -Math.signum(motion.z) : 0);

        float slipperiness = 0.91f;

        if (mob.onGround()) {
            mob.fallDistance = 0;
            BlockPos offsetPos = new BlockPos(mob.blockPosition()).relative(groundDirection.getLeft());
            slipperiness = climber.getBlockSlipperiness(offsetPos);
        }

        motion = mob.getDeltaMovement();
        Vec3 orthogonalMotion = upVector.scale(upVector.dot(motion));
        Vec3 tangentialMotion = motion.subtract(orthogonalMotion);

        mob.setDeltaMovement(
                tangentialMotion.x * slipperiness + orthogonalMotion.x * 0.98f,
                tangentialMotion.y * slipperiness + orthogonalMotion.y * 0.98f,
                tangentialMotion.z * slipperiness + orthogonalMotion.z * 0.98f);

        boolean detachedX = attachedSides.x != prevAttachedSides.x && Math.abs(attachedSides.x) < 0.001D;
        boolean detachedY = attachedSides.y != prevAttachedSides.y && Math.abs(attachedSides.y) < 0.001D;
        boolean detachedZ = attachedSides.z != prevAttachedSides.z && Math.abs(attachedSides.z) < 0.001D;
        boolean stillAttached = Math.abs(attachedSides.x) > 0.001D || Math.abs(attachedSides.y) > 0.001D || Math.abs(attachedSides.z) > 0.001D;

        if (detachTicks > 0 && detachTicks < DETACH_TICKS && mob.onGround()) {
            detachTicks = 0;
        }

        if ((detachedX || detachedY || detachedZ) && !stillAttached && detachTicks == 0) {
            reattach(upVector, detachedX, detachedY, detachedZ);
        }

        mob.calculateEntityAnimation(true);
    }

    private void reattach(Vec3 upVector, boolean detachedX, boolean detachedY, boolean detachedZ) {
        Vec3 travel = mob.getDeltaMovement();
        AttributeInstance stepAttr = mob.getAttribute(Attributes.STEP_HEIGHT);
        double stepHeight = stepAttr != null ? stepAttr.getBaseValue() : 0;
        boolean prevOnGround = mob.onGround();
        boolean prevCollidedHorizontally = mob.horizontalCollision;
        boolean prevCollidedVertically = mob.verticalCollision;

        if (stepAttr != null) stepAttr.setBaseValue(0);
        mob.move(MoverType.SELF, new Vec3(
                detachedX ? -prevAttachedSides.x * 0.25f : 0,
                detachedY ? -prevAttachedSides.y * 0.25f : 0,
                detachedZ ? -prevAttachedSides.z * 0.25f : 0));
        if (stepAttr != null) stepAttr.setBaseValue(stepHeight);

        if (mob.onGround()) {
            mob.setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 attachVector = getReattachDirection(prevAttachedSides, upVector, travel);

        if (attachVector == null) {
            mob.setOnGround(prevOnGround);
            mob.horizontalCollision = prevCollidedHorizontally;
            mob.verticalCollision = prevCollidedVertically;
            return;
        }

        Vec3 attachMovement = attachVector.scale(mob.getDeltaMovement().length() + 0.1f);

        AABB box = mob.getBoundingBox();
        boolean attached = false;

        for (int i = 0; i < 2 && !attached; i++) {
            Vec3 moved = collide(box, attachMovement);
            box = box.move(moved);
            attached = moved.distanceToSqr(attachMovement) > 1.0E-14D;
        }

        if (attached) {
            mob.setBoundingBox(box);
            setLocationFromBoundingbox();
            mob.setOnGround(true);
            mob.setDeltaMovement(Vec3.ZERO);
        } else {
            mob.setOnGround(prevOnGround);
            mob.horizontalCollision = prevCollidedHorizontally;
            mob.verticalCollision = prevCollidedVertically;
        }
    }

    @Nullable
    static Vec3 getReattachDirection(Vec3 detachedSides, Vec3 upVector, Vec3 travel) {
        Vec3 axis = detachedSides.normalize();

        Vec3 attachVector = removeComponent(upVector.scale(-1), axis);
        if (attachVector.length() < MIN_ATTACH_DIRECTION) {
            attachVector = removeComponent(travel.scale(-1), axis);
            if (attachVector.lengthSqr() < 1.0E-8D) {
                return null;
            }
        }

        if (Math.abs(attachVector.x) >= Math.abs(attachVector.y) && Math.abs(attachVector.x) >= Math.abs(attachVector.z)) {
            return new Vec3(Math.signum(attachVector.x), 0, 0);
        } else if (Math.abs(attachVector.y) >= Math.abs(attachVector.z)) {
            return new Vec3(0, Math.signum(attachVector.y), 0);
        } else {
            return new Vec3(0, 0, Math.signum(attachVector.z));
        }
    }

    @Nullable
    static Vec3 getObstacleClimbDirection(Vec3 requested, Vec3 allowed, Vec3 upVector) {
        double nx = Math.abs(allowed.x - requested.x) > 1.0E-7D ? -Math.signum(requested.x) : 0;
        double ny = Math.abs(allowed.y - requested.y) > 1.0E-7D ? -Math.signum(requested.y) : 0;
        double nz = Math.abs(allowed.z - requested.z) > 1.0E-7D ? -Math.signum(requested.z) : 0;

        Vec3 faceNormal = new Vec3(nx, ny, nz);
        if (faceNormal.lengthSqr() < 1.0E-8D) {
            return null;
        }
        faceNormal = faceNormal.normalize();

        Vec3 climbDir = removeComponent(upVector, faceNormal);
        if (climbDir.length() < MIN_CLIMB_DIRECTION) {
            return null;
        }
        return climbDir.normalize();
    }

    private static Vec3 removeComponent(Vec3 vec, Vec3 axis) {
        return vec.subtract(axis.scale(axis.dot(vec)));
    }

    private static Vec3 safeNormalize(Vec3 vec, Vec3 fallback) {
        return vec.lengthSqr() < 1.0E-8D ? fallback : vec.normalize();
    }

    private Vec3 collide(AABB box, Vec3 movement) {
        return Entity.collideBoundingBox(mob, movement, box, mob.level(), mob.level().getEntityCollisions(mob, box.expandTowards(movement)));
    }

    private float getRelevantMoveFactor(float slipperiness) {
        return mob.onGround() ? mob.getSpeed() * (0.16277136F / (slipperiness * slipperiness * slipperiness)) : 0.02f;
    }

    private Vec3 getStickingForce(Pair<Direction, Vec3> walkingSide) {
        double uprightness = Math.max(attachmentNormal.y, 0);
        double gravity = mob.getGravity();
        double stickingForce = gravity * uprightness + 0.08D * (1 - uprightness);
        return walkingSide.getRight().scale(stickingForce);
    }

    // ==================== ORIENTATION LOGIC ====================

    public void updateOffsetsAndOrientation() {
        Vec3 direction = getOrientation().getGlobal(mob.getYRot(), mob.getXRot());

        boolean isAttached = false;

        double baseStickingOffsetX = 0.0f;
        double baseStickingOffsetY = climber.getVerticalOffset(1);
        double baseStickingOffsetZ = 0.0f;
        Vec3 baseOrientationNormal = UP;

        if (!isTravelingInFluid && mob.onGround() && mob.getVehicle() == null) {
            Vec3 p = mob.position();

            if (canReuseSolve(p)) {
                isAttached = solvedAttached;
                ticksSinceSolve++;
            } else {
                isAttached = solveAttachment(p);
                hasSolve = true;
                solvedAttached = isAttached;
                solvedX = p.x;
                solvedY = p.y;
                solvedZ = p.z;
                ticksSinceSolve = 0;
            }
        } else {
            hasSolve = false;
        }

        float attachmentBlend = attachedTicks * 0.2f;

        attachmentOffsetX = baseStickingOffsetX + (lastAttachmentOffsetX - baseStickingOffsetX) * attachmentBlend;
        attachmentOffsetY = baseStickingOffsetY + (lastAttachmentOffsetY - baseStickingOffsetY) * attachmentBlend;
        attachmentOffsetZ = baseStickingOffsetZ + (lastAttachmentOffsetZ - baseStickingOffsetZ) * attachmentBlend;
        attachmentNormal = safeNormalize(baseOrientationNormal.add(lastAttachmentOrientationNormal.subtract(baseOrientationNormal).scale(attachmentBlend)), lastAttachmentOrientationNormal);

        if (!isAttached) {
            attachedTicks = Math.max(0, attachedTicks - 1);
        } else {
            attachedTicks = Math.min(MAX_ATTACHED_TICKS, attachedTicks + 1);
        }

        orientation = calculateOrientation(1);

        Pair<Float, Float> newRotations = getOrientation().getLocalRotation(direction);

        float yawDelta = newRotations.getLeft() - mob.getYRot();
        float pitchDelta = newRotations.getRight() - mob.getXRot();

        mob.setYRot(Mth.wrapDegrees(mob.getYRot() + yawDelta));
        mob.yRotO = wrapAngleInRange(mob.yRotO, mob.getYRot());

        mob.yBodyRot = Mth.wrapDegrees(mob.yBodyRot + yawDelta);
        mob.yBodyRotO = wrapAngleInRange(mob.yBodyRotO, mob.yBodyRot);

        mob.yHeadRot = Mth.wrapDegrees(mob.yHeadRot + yawDelta);
        mob.yHeadRotO = wrapAngleInRange(mob.yHeadRotO, mob.yHeadRot);

        mob.setXRot(Mth.wrapDegrees(mob.getXRot() + pitchDelta));
        mob.xRotO = wrapAngleInRange(mob.xRotO, mob.getXRot());
    }

    private boolean canReuseSolve(Vec3 p) {
        if (!hasSolve || !solvedAttached || !solveConverged || attachedTicks < MAX_ATTACHED_TICKS || ticksSinceSolve >= SOLVE_REFRESH_TICKS) {
            return false;
        }
        double dx = p.x - solvedX;
        double dy = p.y - solvedY;
        double dz = p.z - solvedZ;
        return dx * dx + dy * dy + dz * dz < SOLVE_REUSE_DISTANCE_SQ;
    }

    private boolean solveAttachment(Vec3 p) {
        solveConverged = false;

        Vec3 s = p.add(0, mob.getBbHeight() * 0.5f, 0);
        AABB inclusionBox = new AABB(s.x, s.y, s.z, s.x, s.y, s.z).inflate(collisionsInclusionRange);
        List<AABB> boxes = getCollisionBoxes(inclusionBox);

        if (boxes.isEmpty()) {
            return false;
        }

        AABB entityBox = mob.getBoundingBox();
        Vec3 searchNormal = attachmentNormal;
        boolean attached = false;

        for (int i = 0; i < SOLVE_ITERATIONS; i++) {
            Pair<Vec3, Vec3> attachmentPoint = CollisionSmoothingUtil.findClosestPoint(
                    consumer -> boxes.forEach(box -> consumer.consume(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)),
                    s, searchNormal.scale(-1), collisionsSmoothingRange, 1.0f, 0.001f, 20, 0.05f, s);

            if (attachmentPoint == null) {
                break;
            }

            Vec3 attachmentPos = attachmentPoint.getLeft();

            double dx = Math.max(entityBox.minX - attachmentPos.x, attachmentPos.x - entityBox.maxX);
            double dy = Math.max(entityBox.minY - attachmentPos.y, attachmentPos.y - entityBox.maxY);
            double dz = Math.max(entityBox.minZ - attachmentPos.z, attachmentPos.z - entityBox.maxZ);

            if (Math.max(dx, Math.max(dy, dz)) >= 0.5f) {
                break;
            }

            Vec3 normal = attachmentPoint.getRight();

            lastAttachmentOffsetX = Mth.clamp(attachmentPos.x - p.x, -mob.getBbWidth() / 2, mob.getBbWidth() / 2);
            lastAttachmentOffsetY = Mth.clamp(attachmentPos.y - p.y, 0, mob.getBbHeight());
            lastAttachmentOffsetZ = Mth.clamp(attachmentPos.z - p.z, -mob.getBbWidth() / 2, mob.getBbWidth() / 2);
            lastAttachmentOrientationNormal = normal;
            attached = true;

            if (normal.dot(searchNormal) > SOLVE_CONVERGENCE_DOT) {
                solveConverged = true;
                break;
            }

            searchNormal = normal;
        }

        return attached;
    }

    private float wrapAngleInRange(float angle, float target) {
        while (target - angle < -180.0F) {
            angle -= 360.0F;
        }
        while (target - angle >= 180.0F) {
            angle += 360.0F;
        }
        return angle;
    }

    public Orientation calculateOrientation(float partialTicks) {
        Vec3 attachmentNormal = prevAttachmentNormal.add(this.attachmentNormal.subtract(prevAttachmentNormal).scale(partialTicks));

        Vec3 localZ = new Vec3(0, 0, 1);
        Vec3 localY = new Vec3(0, 1, 0);
        Vec3 localX = new Vec3(1, 0, 0);

        float componentZ = (float) localZ.dot(attachmentNormal);
        float componentY;
        float componentX = (float) localX.dot(attachmentNormal);

        float yaw = (float) Math.toDegrees(Mth.atan2(componentX, componentZ));

        localZ = new Vec3(Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        localY = new Vec3(0, 1, 0);
        localX = new Vec3(Math.sin(Math.toRadians(yaw - 90)), 0, Math.cos(Math.toRadians(yaw - 90)));

        componentZ = (float) localZ.dot(attachmentNormal);
        componentY = (float) localY.dot(attachmentNormal);
        componentX = (float) localX.dot(attachmentNormal);

        float pitch = (float) Math.toDegrees(Mth.atan2(Mth.sqrt(componentX * componentX + componentZ * componentZ), componentY));

        Matrix4f m = new Matrix4f();

        m.multiply(new Matrix4f((float) Math.toRadians(yaw), 0, 1, 0));
        m.multiply(new Matrix4f((float) Math.toRadians(pitch), 1, 0, 0));
        m.multiply(new Matrix4f((float) Math.toRadians(Math.signum(0.5f - componentY - componentZ - componentX) * yaw), 0, 1, 0));

        localZ = m.multiply(new Vec3(0, 0, -1));
        localY = m.multiply(new Vec3(0, 1, 0));
        localX = m.multiply(new Vec3(1, 0, 0));

        return new Orientation(attachmentNormal, localZ, localY, localX, componentZ, componentY, componentX, yaw, pitch);
    }

    // ==================== JUMP LOGIC ====================

    /**
     * Called during entity jump. Returns true if jump was handled.
     */
    public boolean onJump() {
        if (jumpDir != null) {
            float jumpStrength = 0.42f; // Default jump power
            if (mob.hasEffect(MobEffects.JUMP_BOOST)) {
                var effect = mob.getEffect(MobEffects.JUMP_BOOST);
                if (effect != null) {
                    jumpStrength += 0.1F * (effect.getAmplifier() + 1);
                }
            }

            Vec3 motion = mob.getDeltaMovement();
            Vec3 orthogonalMotion = jumpDir.scale(jumpDir.dot(motion));
            Vec3 tangentialMotion = motion.subtract(orthogonalMotion);

            mob.setDeltaMovement(
                    tangentialMotion.x + jumpDir.x * jumpStrength,
                    tangentialMotion.y + jumpDir.y * jumpStrength,
                    tangentialMotion.z + jumpDir.z * jumpStrength);

            if (mob.isSprinting()) {
                Vec3 boost = getOrientation().getGlobal(mob.getYRot(), 0).scale(0.2f);
                mob.setDeltaMovement(mob.getDeltaMovement().add(boost));
            }

            mob.needsSync = true;
            jumpDir = null;
            if (detachOnJump) {
                detachTicks = DETACH_TICKS;
                detachOnJump = false;
            }
            return true;
        }
        return false;
    }

    // ==================== MOVEMENT HOOKS ====================

    /**
     * Called during entity move.
     */
    public boolean onMove(MoverType type, Vec3 pos, boolean pre) {
        if (pre) {
            preWalkingPosition = mob.position();
            preMoveY = mob.getY();
        } else {
            if (Math.abs(mob.getY() - preMoveY - pos.y) > 0.000001D) {
                mob.setDeltaMovement(mob.getDeltaMovement().multiply(1, 0, 1));
            }
            mob.setOnGround(mob.horizontalCollision || mob.verticalCollision);
        }
        return false;
    }

    @Nullable
    public BlockPos getAdjustedOnPosition(BlockPos onPosition) {
        float verticalOffset = climber.getVerticalOffset(1);

        int x = Mth.floor(mob.getX() + attachmentOffsetX - (float) attachmentNormal.x * (verticalOffset + 0.2f));
        int y = Mth.floor(mob.getY() + attachmentOffsetY - (float) attachmentNormal.y * (verticalOffset + 0.2f));
        int z = Mth.floor(mob.getZ() + attachmentOffsetZ - (float) attachmentNormal.z * (verticalOffset + 0.2f));
        BlockPos pos = new BlockPos(x, y, z);

        if (mob.level().isEmptyBlock(pos) && attachmentNormal.y < 0.0f) {
            BlockPos posDown = pos.below();
            BlockState stateDown = mob.level().getBlockState(posDown);

            if (stateDown.is(BlockTags.FENCES) || stateDown.is(BlockTags.WALLS) || stateDown.getBlock() instanceof FenceGateBlock) {
                return posDown;
            }
        }

        return pos;
    }

    public boolean getAdjustedCanTriggerWalking(boolean canTriggerWalking) {
        if (preWalkingPosition != null && climber.canClimberTriggerWalking() && !mob.isPassenger()) {
            preWalkingPosition = null;
            // Note: walkDist and moveDist updates should be done in the entity
        }
        return false;
    }

    // ==================== COLLISION HELPERS ====================

    private BlockGetter climbableView(BlockGetter getter) {
        return new BlockGetter() {
            @Override
            public int getHeight() {
                return getter.getHeight();
            }

            @Override
            public int getMinY() {
                return getter.getMinY();
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return getter.getBlockEntity(pos);
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                BlockState state = getter.getBlockState(pos);
                return state.isAir() || climber.canClimbOnBlock(state, pos) ? state : Blocks.AIR.defaultBlockState();
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return getter.getFluidState(pos);
            }
        };
    }

    private void forEachCollisonBox(AABB aabb, Shapes.DoubleLineConsumer action) {
        int minChunkX = ((Mth.floor(aabb.minX - 1.0E-7D) - 1) >> 4);
        int maxChunkX = ((Mth.floor(aabb.maxX + 1.0E-7D) + 1) >> 4);
        int minChunkZ = ((Mth.floor(aabb.minZ - 1.0E-7D) - 1) >> 4);
        int maxChunkZ = ((Mth.floor(aabb.maxZ + 1.0E-7D) + 1) >> 4);

        int width = maxChunkX - minChunkX + 1;
        int depth = maxChunkZ - minChunkZ + 1;

        BlockGetter[] blockReaderCache = new BlockGetter[width * depth];

        CollisionGetter collisionReader = mob.level();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                BlockGetter chunk = collisionReader.getChunkForCollisions(cx, cz);
                blockReaderCache[(cx - minChunkX) + (cz - minChunkZ) * width] = chunk != null ? climbableView(chunk) : null;
            }
        }

        final int fMinChunkX = minChunkX;
        final int fMinChunkZ = minChunkZ;

        CollisionGetter cachedCollisionReader = new CollisionGetter() {
            @Override
            public int getHeight() {
                return mob.level().getHeight();
            }

            @Override
            public int getMinY() {
                return mob.level().getMinY();
            }

            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return collisionReader.getBlockEntity(pos);
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                BlockState state = collisionReader.getBlockState(pos);
                return state.isAir() || climber.canClimbOnBlock(state, pos) ? state : Blocks.AIR.defaultBlockState();
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return collisionReader.getFluidState(pos);
            }

            @Override
            public WorldBorder getWorldBorder() {
                return collisionReader.getWorldBorder();
            }

            @Override
            public List<VoxelShape> getEntityCollisions(Entity entity, AABB aabb) {
                return collisionReader.getEntityCollisions(entity, aabb);
            }

            @Override
            public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
                return blockReaderCache[(chunkX - fMinChunkX) + (chunkZ - fMinChunkZ) * width];
            }
        };

        Iterable<VoxelShape> shapes = cachedCollisionReader.getBlockCollisions(mob, aabb);
        shapes.forEach(shape -> shape.forAllBoxes(action));
    }

    private List<AABB> getCollisionBoxes(AABB aabb) {
        List<AABB> boxes = new ArrayList<>();
        forEachCollisonBox(aabb, (minX, minY, minZ, maxX, maxY, maxZ) -> boxes.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ)));
        return boxes;
    }

    private static double calculateXOffset(AABB aabb, AABB other, double offsetX, double margin) {
        if (other.maxY > aabb.minY - margin && other.minY < aabb.maxY + margin && other.maxZ > aabb.minZ - margin && other.minZ < aabb.maxZ + margin) {
            if (offsetX > 0.0D && other.maxX <= aabb.minX) {
                double dx = aabb.minX - other.maxX;
                if (dx < offsetX) {
                    offsetX = dx;
                }
            } else if (offsetX < 0.0D && other.minX >= aabb.maxX) {
                double dx = aabb.maxX - other.minX;
                if (dx > offsetX) {
                    offsetX = dx;
                }
            }
            return offsetX;
        } else {
            return offsetX;
        }
    }

    private static double calculateYOffset(AABB aabb, AABB other, double offsetY, double margin) {
        if (other.maxX > aabb.minX - margin && other.minX < aabb.maxX + margin && other.maxZ > aabb.minZ - margin && other.minZ < aabb.maxZ + margin) {
            if (offsetY > 0.0D && other.maxY <= aabb.minY) {
                double dy = aabb.minY - other.maxY;
                if (dy < offsetY) {
                    offsetY = dy;
                }
            } else if (offsetY < 0.0D && other.minY >= aabb.maxY) {
                double dy = aabb.maxY - other.minY;
                if (dy > offsetY) {
                    offsetY = dy;
                }
            }
            return offsetY;
        } else {
            return offsetY;
        }
    }

    private static double calculateZOffset(AABB aabb, AABB other, double offsetZ, double margin) {
        if (other.maxX > aabb.minX - margin && other.minX < aabb.maxX + margin && other.maxY > aabb.minY - margin && other.minY < aabb.maxY + margin) {
            if (offsetZ > 0.0D && other.maxZ <= aabb.minZ) {
                double dz = aabb.minZ - other.maxZ;
                if (dz < offsetZ) {
                    offsetZ = dz;
                }
            } else if (offsetZ < 0.0D && other.minZ >= aabb.maxZ) {
                double dz = aabb.maxZ - other.minZ;
                if (dz > offsetZ) {
                    offsetZ = dz;
                }
            }
            return offsetZ;
        } else {
            return offsetZ;
        }
    }

    private void setLocationFromBoundingbox() {
        AABB axisalignedbb = mob.getBoundingBox();
        mob.setPosRaw((axisalignedbb.minX + axisalignedbb.maxX) / 2.0D, axisalignedbb.minY, (axisalignedbb.minZ + axisalignedbb.maxZ) / 2.0D);
    }
}
