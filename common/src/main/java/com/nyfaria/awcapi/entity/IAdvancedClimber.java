package com.nyfaria.awcapi.entity;

import com.nyfaria.awcapi.entity.movement.IAdvancedPathFindingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Main interface for entities that want wall climbing capabilities.
 * Implement this interface on your entity class to enable advanced wall climbing.
 *
 * <p>Usage:
 * <pre>
 * public class MyClimberEntity extends PathfinderMob implements IAdvancedClimber {
 *     private final ClimberComponent climberComponent = new ClimberComponent(this);
 *
 *     {@literal @}Override
 *     public ClimberComponent getClimberComponent() {
 *         return climberComponent;
 *     }
 *
 *     {@literal @}Override
 *     public void tick() {
 *         super.tick();
 *         ClimberHelper.tickClimber(this);
 *     }
 * }
 * </pre>
 */
public interface IAdvancedClimber extends IAdvancedPathFindingEntity {

    /**
     * Gets the climber component that manages the climbing state and logic.
     * @return The ClimberComponent for this entity
     */
    ClimberComponent getClimberComponent();

    /**
     * Gets the offset for rendering based on the attachment position.
     * @param axis The axis to get the offset for
     * @param partialTicks Partial tick time for interpolation
     * @return The attachment offset
     */
    default float getAttachmentOffset(Direction.Axis axis, float partialTicks) {
        return getClimberComponent().getAttachmentOffset(axis, partialTicks);
    }

    /**
     * Gets the vertical offset for positioning.
     * @param partialTicks Partial tick time for interpolation
     * @return The vertical offset (default 0.075f)
     */
    default float getVerticalOffset(float partialTicks) {
        return 0.075f;
    }

    /**
     * Gets the current orientation of the climber.
     * @return The orientation
     */
    default Orientation getOrientation() {
        return getClimberComponent().getOrientation();
    }

    /**
     * Calculates the orientation at the given partial tick.
     * @param partialTicks Partial tick time for interpolation
     * @return The calculated orientation
     */
    default Orientation calculateOrientation(float partialTicks) {
        return getClimberComponent().calculateOrientation(partialTicks);
    }

    /**
     * Sets the render orientation for use during rendering.
     * @param orientation The render orientation
     */
    default void setRenderOrientation(Orientation orientation) {
        getClimberComponent().setRenderOrientation(orientation);
    }

    /**
     * Gets the render orientation.
     * @return The render orientation, or null if not set
     */
    @Nullable
    default Orientation getRenderOrientation() {
        return getClimberComponent().getRenderOrientation();
    }

    /**
     * Gets the movement speed of the entity.
     * @return The movement speed
     */
    float getMovementSpeed();

    /**
     * Gets the current ground direction (the surface the entity is attached to).
     * @return A pair of the direction and the normalized vector
     */
    default Pair<Direction, Vec3> getGroundDirection() {
        return getClimberComponent().getGroundDirection();
    }

    /**
     * Whether to track pathing targets for debug rendering.
     * @return true to track pathing targets
     */
    default boolean shouldTrackPathingTargets() {
        return false;
    }

    /**
     * Gets the tracked movement target position.
     * @return The movement target, or null if not tracking
     */
    @Nullable
    default Vec3 getTrackedMovementTarget() {
        return getClimberComponent().getTrackedMovementTarget();
    }

    /**
     * Gets the tracked pathing targets.
     * @return List of pathing targets, or null if not tracking
     */
    @Nullable
    default List<PathingTarget> getTrackedPathingTargets() {
        return getClimberComponent().getTrackedPathingTargets();
    }

    /**
     * Checks if the entity can climb on the given block.
     * Override to customize which blocks can be climbed.
     * @param state The block state
     * @param pos The block position
     * @return true if the block can be climbed
     */
    default boolean canClimbOnBlock(BlockState state, BlockPos pos) {
        return true;
    }

    /**
     * Gets the slipperiness of a block at the given position.
     * @param pos The block position
     * @return The slipperiness factor
     */
    float getBlockSlipperiness(BlockPos pos);

    /**
     * Whether the climber can trigger walking animations/sounds.
     * @return true if walking can be triggered
     */
    default boolean canClimberTriggerWalking() {
        return true;
    }

    /**
     * Whether the climber can climb while in water.
     * @return true if can climb in water
     */
    default boolean canClimbInWater() {
        return getClimberComponent().canClimbInWater();
    }

    /**
     * Sets whether the climber can climb in water.
     * @param value true to allow climbing in water
     */
    default void setCanClimbInWater(boolean value) {
        getClimberComponent().setCanClimbInWater(value);
    }

    /**
     * Whether the climber can climb while in lava.
     * @return true if can climb in lava
     */
    default boolean canClimbInLava() {
        return getClimberComponent().canClimbInLava();
    }

    /**
     * Sets whether the climber can climb in lava.
     * @param value true to allow climbing in lava
     */
    default void setCanClimbInLava(boolean value) {
        getClimberComponent().setCanClimbInLava(value);
    }

    /**
     * Gets the range for collision inclusion calculations.
     * @return The collision inclusion range
     */
    default float getCollisionsInclusionRange() {
        return getClimberComponent().getCollisionsInclusionRange();
    }

    /**
     * Sets the collision inclusion range.
     * @param range The new range
     */
    default void setCollisionsInclusionRange(float range) {
        getClimberComponent().setCollisionsInclusionRange(range);
    }

    /**
     * Gets the range for collision smoothing.
     * @return The collision smoothing range
     */
    default float getCollisionsSmoothingRange() {
        return getClimberComponent().getCollisionsSmoothingRange();
    }

    /**
     * Sets the collision smoothing range.
     * @param range The new range
     */
    default void setCollisionsSmoothingRange(float range) {
        getClimberComponent().setCollisionsSmoothingRange(range);
    }

    /**
     * Sets the jump direction for wall-aware jumping.
     * @param dir The jump direction, or null for default
     */
    default void setJumpDirection(@Nullable Vec3 dir) {
        getClimberComponent().setJumpDirection(dir);
    }

    /**
     * Gets the entity as a Mob.
     * @return The mob instance
     */
    Mob asMob();

    // Lerp rotation setters for network sync
    void setLerpYRot(Float yRot);
    void setLerpXRot(Float xRot);
    void setLerpYHeadRot(Float yHeadRot);
    void setLerpHeadSteps(int steps);
}

