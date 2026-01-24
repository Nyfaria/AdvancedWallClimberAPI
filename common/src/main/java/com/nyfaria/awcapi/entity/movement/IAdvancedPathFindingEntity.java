package com.nyfaria.awcapi.entity.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.*;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Interface for entities that use advanced pathfinding capabilities like wall climbing.
 */
public interface IAdvancedPathFindingEntity {
    /**
     * The side on which the entity is currently walking
     * @return The current ground direction
     */
    default Direction getGroundSide() {
        return Direction.DOWN;
    }

    /**
     * Called when the mob tries to move along the path but is obstructed
     * @param facing The direction that is obstructed
     */
    default void onPathingObstructed(Direction facing) {
    }

    /**
     * Returns how many ticks the mob can be stuck before the path is considered to be obstructed
     * @return Maximum stuck ticks
     */
    default int getMaxStuckCheckTicks() {
        return 40;
    }

    /**
     * Returns the pathing malus for building a bridge
     * @param entity The entity
     * @param pos The position
     * @param fallPathPoint The fall path point, or null
     * @return The malus value, negative to avoid
     */
    default float getBridgePathingMalus(Mob entity, BlockPos pos, @Nullable Node fallPathPoint) {
        return -1.0f;
    }

    /**
     * Returns the pathing malus for the given {@link BlockPathTypes} and block position.
     * Nodes with negative values are avoided at all cost. Nodes with value 0.0 have the highest priority, i.e.
     * are preferred over all other nodes. Nodes with a positive value incur an additional travel cost of the same magnitude
     * and the higher their value the less they are preferred. Note that the additional travel cost increases the path's "length" (i.e. cost)
     * and thus decreases the actual maximum path length in blocks.
     * @param cache The block getter cache
     * @param entity The entity
     * @param nodeType The path type
     * @param pos The block position
     * @param direction The direction vector
     * @param sides Predicate for valid sides
     * @return The malus value
     */
    default float getPathingMalus(BlockGetter cache, Mob entity, BlockPathTypes nodeType, BlockPos pos, Vec3i direction, Predicate<Direction> sides) {
        return entity.getPathfindingMalus(nodeType);
    }

    /**
     * Called after the path finder has finished finding a path.
     * Can e.g. be used to clear caches.
     */
    default void pathFinderCleanup() {
    }
}

