package com.nyfaria.awcapi.entity.movement;

import com.nyfaria.awcapi.entity.IAdvancedClimber;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Look controller for wall-climbing entities.
 * Handles orientation-aware look rotation.
 */
public class ClimberLookController<T extends Mob & IAdvancedClimber> extends LookControl {
    protected final IAdvancedClimber climber;

    public ClimberLookController(T entity) {
        super(entity);
        this.climber = entity;
    }

    @Override
    protected @NotNull Optional<Float> getXRotD() {
        Vec3 dir = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getEyeY(), this.wantedZ - this.mob.getZ());
        return Optional.of(this.climber.getOrientation().getLocalRotation(dir).getRight());
    }

    @Override
    protected @NotNull Optional<Float> getYRotD() {
        Vec3 dir = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getEyeY(), this.wantedZ - this.mob.getZ());
        return Optional.of(this.climber.getOrientation().getLocalRotation(dir).getLeft());
    }
}

