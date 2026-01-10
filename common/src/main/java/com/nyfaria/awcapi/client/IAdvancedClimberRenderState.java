package com.nyfaria.awcapi.client;

import net.minecraft.world.phys.Vec3;

/**
 * Interface for render states that support wall climbing entities.
 * Implement this on your entity's render state class to enable proper climbing orientation rendering.
 *
 * <p>Example implementation:
 * <pre>
 * public class MyClimberRenderState extends LivingEntityRenderState implements IAdvancedClimberRenderState {
 *     // Required fields - you must create these
 *     private boolean awca_climbing;
 *     private Vec3 awca_normal = new Vec3(0, 1, 0);
 *     private Vec3 awca_localX = new Vec3(1, 0, 0);
 *     private Vec3 awca_localY = new Vec3(0, 1, 0);
 *     private Vec3 awca_localZ = new Vec3(0, 0, 1);
 *     private float awca_componentX, awca_componentY, awca_componentZ;
 *     private float awca_yaw, awca_pitch;
 *     private float awca_attachmentOffsetX, awca_attachmentOffsetY, awca_attachmentOffsetZ;
 *     private float awca_verticalOffset;
 *
 *     // Implement all interface methods using these fields...
 * }
 * </pre>
 */
public interface IAdvancedClimberRenderState {

    // ==================== CLIMBING STATE ====================

    /**
     * Whether the entity is currently in climbing mode.
     */
    boolean awca$isClimbing();
    void awca$setClimbing(boolean climbing);

    // ==================== ORIENTATION NORMAL ====================

    /**
     * The surface normal vector the entity is attached to.
     */
    Vec3 awca$getNormal();
    void awca$setNormal(Vec3 normal);

    // ==================== LOCAL COORDINATE SYSTEM ====================

    /**
     * The local X axis in global coordinates.
     */
    Vec3 awca$getLocalX();
    void awca$setLocalX(Vec3 localX);

    /**
     * The local Y axis in global coordinates.
     */
    Vec3 awca$getLocalY();
    void awca$setLocalY(Vec3 localY);

    /**
     * The local Z axis in global coordinates.
     */
    Vec3 awca$getLocalZ();
    void awca$setLocalZ(Vec3 localZ);

    // ==================== ORIENTATION COMPONENTS ====================

    /**
     * The X component of the orientation.
     */
    float awca$getComponentX();
    void awca$setComponentX(float componentX);

    /**
     * The Y component of the orientation.
     */
    float awca$getComponentY();
    void awca$setComponentY(float componentY);

    /**
     * The Z component of the orientation.
     */
    float awca$getComponentZ();
    void awca$setComponentZ(float componentZ);

    // ==================== ROTATION ANGLES ====================

    /**
     * The yaw rotation angle for rendering.
     */
    float awca$getYaw();
    void awca$setYaw(float yaw);

    /**
     * The pitch rotation angle for rendering.
     */
    float awca$getPitch();
    void awca$setPitch(float pitch);

    // ==================== ATTACHMENT OFFSETS ====================

    /**
     * The X attachment offset for positioning.
     */
    float awca$getAttachmentOffsetX();
    void awca$setAttachmentOffsetX(float offset);

    /**
     * The Y attachment offset for positioning.
     */
    float awca$getAttachmentOffsetY();
    void awca$setAttachmentOffsetY(float offset);

    /**
     * The Z attachment offset for positioning.
     */
    float awca$getAttachmentOffsetZ();
    void awca$setAttachmentOffsetZ(float offset);

    // ==================== VERTICAL OFFSET ====================

    /**
     * The vertical offset for positioning relative to the surface.
     */
    float awca$getVerticalOffset();
    void awca$setVerticalOffset(float offset);

}

