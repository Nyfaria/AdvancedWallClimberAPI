package com.nyfaria.awcapi;

import com.nyfaria.awcapi.entity.*;
import com.nyfaria.awcapi.entity.movement.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.phys.*;

/**
 * Main helper class for the Advanced Wall Climber API.
 * Use these static methods to easily integrate wall climbing into your entities.
 *
 * <p>Example usage in your entity:
 * <pre>
 * public class MyClimberEntity extends PathfinderMob implements IAdvancedClimber {
 *     private final ClimberComponent climberComponent = new ClimberComponent(this);
 *
 *     public MyClimberEntity(EntityType&lt;?&gt; type, Level level) {
 *         super(type, level);
 *         ClimberHelper.initClimber(this);
 *     }
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
 *
 *     {@literal @}Override
 *     public void aiStep() {
 *         super.aiStep();
 *         ClimberHelper.livingTickClimber(this);
 *     }
 *
 *     // ... implement other IAdvancedClimber methods
 * }
 * </pre>
 */
public class ClimberHelper {

    /**
     * Initializes the climber entity with the appropriate controllers.
     * Call this in your entity's constructor after calling super().
     *
     * <p>Note: Since controllers are protected, you should call this from within
     * your entity class or override the controllers in your entity's constructor:
     * <pre>
     * public MyClimberEntity(EntityType&lt;?&gt; type, Level level) {
     *     super(type, level);
     *     this.initClimber(this);
     * }
     * </pre>
     *
     * @param climber The climber entity to initialize
     * @param <T>     The type of mob that implements IAdvancedClimber
     */
    public static <T extends Mob & IAdvancedClimber> void initClimber(T climber) {
        climber.moveControl = new ClimberMoveController<>(climber);
        climber.lookControl = new ClimberLookController<>(climber);
        climber.jumpControl = new ClimberJumpController<>(climber);
        climber.setMaxUpStep(0.1f); // Prevent default step-up behavior
        // Note: Move, look, and jump controllers must be set from within the entity class
        // as they are protected fields
    }

    /**
     * Ticks the climber logic. Call this in your entity's tick() method.
     *
     * @param climber The climber entity
     */
    public static void tickClimber(IAdvancedClimber climber) {
        climber.getClimberComponent().tick();
    }

    /**
     * Ticks the living entity climbing logic. Call this in your entity's aiStep() method.
     *
     * @param climber The climber entity
     */
    public static void livingTickClimber(IAdvancedClimber climber) {
        climber.getClimberComponent().livingTick();
    }

    /**
     * Handles travel for the climber. Call this at the start of your entity's travel() method.
     * If this returns true, you should return early from travel().
     *
     * @param climber  The climber entity
     * @param relative The relative movement vector
     * @return True if travel was handled and default behavior should be skipped
     */
    public static boolean handleTravel(IAdvancedClimber climber, Vec3 relative) {
        return climber.getClimberComponent().onTravel(relative, true);
    }

    /**
     * Called after travel for post-processing. Call this at the end of your entity's travel() method.
     *
     * @param climber  The climber entity
     * @param relative The relative movement vector
     */
    public static void postTravel(IAdvancedClimber climber, Vec3 relative) {
        climber.getClimberComponent().onTravel(relative, false);
    }

    /**
     * Handles the jump for wall-climbing awareness.
     *
     * @param climber The climber entity
     * @return True if the jump was handled by the climber component
     */
    public static boolean handleJump(IAdvancedClimber climber) {
        return climber.getClimberComponent().onJump();
    }

    /**
     * Handles movement for wall-climbing awareness.
     *
     * @param climber  The climber entity
     * @param type     The mover type
     * @param movement The movement vector
     * @param pre      True if called before movement, false if after
     * @return True if the move should be handled differently
     */
    public static boolean handleMove(IAdvancedClimber climber, MoverType type, Vec3 movement, boolean pre) {
        return climber.getClimberComponent().onMove(type, movement, pre);
    }

    /**
     * Gets the adjusted on-position for a climber. Use this when overriding getOnPos().
     *
     * @param climber    The climber entity
     * @param defaultPos The default on-position
     * @return The adjusted position
     */
    public static BlockPos getAdjustedOnPosition(IAdvancedClimber climber, BlockPos defaultPos) {
        BlockPos adjusted = climber.getClimberComponent().getAdjustedOnPosition(defaultPos);
        return adjusted != null ? adjusted : defaultPos;
    }
}

