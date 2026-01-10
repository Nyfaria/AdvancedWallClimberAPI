package com.nyfaria.awcapi.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Represents a target position for pathfinding with an associated side.
 */
public class PathingTarget {
    public final BlockPos pos;
    public final Direction side;

    public PathingTarget(BlockPos pos, Direction side) {
        this.pos = pos;
        this.side = side;
    }
}

