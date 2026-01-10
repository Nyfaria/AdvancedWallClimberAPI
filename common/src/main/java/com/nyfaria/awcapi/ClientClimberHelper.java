package com.nyfaria.awcapi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nyfaria.awcapi.client.IAdvancedClimberRenderState;
import com.nyfaria.awcapi.entity.IAdvancedClimber;
import com.nyfaria.awcapi.entity.Orientation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Client helper class for the Advanced Wall Climber API.
 * Use these static methods to easily integrate wall climbing into your entity's renderer.
 *
 * <p><b>Usage in renderer (1.21+):</b>
 * <pre>
 * // In extractRenderState:
 * {@literal @}Override
 * public void extractRenderState(MyClimberEntity entity, MyRenderState state, float partialTick) {
 *     super.extractRenderState(entity, state, partialTick);
 *     ClientClimberHelper.extractClimbingRenderState(entity, state, partialTick);
 * }
 *
 * // In render:
 * {@literal @}Override
 * public void render(MyRenderState state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
 *     ClientClimberHelper.preRenderClimber(state, poseStack);
 *     super.render(state, poseStack, buffer, packedLight);
 *     ClientClimberHelper.postRenderClimber(state, poseStack, buffer);
 * }
 * </pre>
 */
public class ClientClimberHelper {

    // ==================== RENDER STATE EXTRACTION ====================

    /**
     * Extracts climbing render state from an entity implementing IAdvancedClimber
     * and populates the render state. Call this in your renderer's extractRenderState method.
     *
     * <p>Example usage:
     * <pre>
     * {@literal @}Override
     * public void extractRenderState(MyClimberEntity entity, MyRenderState state, float partialTick) {
     *     super.extractRenderState(entity, state, partialTick);
     *     ClientClimberHelper.extractClimbingRenderState(entity, state, partialTick);
     * }
     * </pre>
     *
     * @param entity      The entity to extract climbing data from (must implement IAdvancedClimber)
     * @param renderState The render state to populate (must implement IAdvancedClimberRenderState)
     * @param partialTick The partial tick for interpolation
     * @param <S>         The render state type that implements IAdvancedClimberRenderState
     */
    public static <S extends IAdvancedClimberRenderState> void extractClimbingRenderState(LivingEntity entity, S renderState, float partialTick) {
        if (!(entity instanceof IAdvancedClimber climber)) {
            renderState.awca$setClimbing(false);
            return;
        }

        renderState.awca$setClimbing(true);

        // Calculate the orientation at the current partial tick
        Orientation orientation = climber.calculateOrientation(partialTick);

        // Store normal vector
        renderState.awca$setNormal(orientation.normal);

        // Store local coordinate system
        renderState.awca$setLocalX(orientation.localX);
        renderState.awca$setLocalY(orientation.localY);
        renderState.awca$setLocalZ(orientation.localZ);

        // Store orientation components
        renderState.awca$setComponentX(orientation.componentX);
        renderState.awca$setComponentY(orientation.componentY);
        renderState.awca$setComponentZ(orientation.componentZ);

        // Store rotation angles
        renderState.awca$setYaw(orientation.yaw);
        renderState.awca$setPitch(orientation.pitch);

        // Store attachment offsets
        renderState.awca$setAttachmentOffsetX(climber.getAttachmentOffset(Direction.Axis.X, partialTick));
        renderState.awca$setAttachmentOffsetY(climber.getAttachmentOffset(Direction.Axis.Y, partialTick));
        renderState.awca$setAttachmentOffsetZ(climber.getAttachmentOffset(Direction.Axis.Z, partialTick));

        // Store vertical offset
        renderState.awca$setVerticalOffset(climber.getVerticalOffset(partialTick));
    }

    // ==================== RENDER STATE BASED RENDERING ====================

    /**
     * Call this before rendering the climber to apply climbing transformations.
     * Use this in renderers that use the render state system (1.21+).
     *
     * <p>Example usage:
     * <pre>
     * {@literal @}Override
     * public void render(MyRenderState state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
     *     ClientClimberHelper.preRenderClimber(state, poseStack);
     *     super.render(state, poseStack, buffer, packedLight);
     *     ClientClimberHelper.postRenderClimber(state, poseStack, buffer);
     * }
     * </pre>
     *
     * @param renderState The render state (must implement IAdvancedClimberRenderState)
     * @param poseStack   The pose stack to apply transformations to
     * @param <S>         The render state type that implements IAdvancedClimberRenderState
     * @return true if climbing transformations were applied, false otherwise
     */
    public static <S extends IAdvancedClimberRenderState> boolean preRenderClimber(S renderState, PoseStack poseStack) {
        if (!renderState.awca$isClimbing()) {
            return false;
        }

        Vec3 normal = renderState.awca$getNormal();
        float yaw = renderState.awca$getYaw();
        float pitch = renderState.awca$getPitch();
        float componentX = renderState.awca$getComponentX();
        float componentY = renderState.awca$getComponentY();
        float componentZ = renderState.awca$getComponentZ();
        float verticalOffset = renderState.awca$getVerticalOffset();

        float attachmentOffsetX = renderState.awca$getAttachmentOffsetX();
        float attachmentOffsetY = renderState.awca$getAttachmentOffsetY();
        float attachmentOffsetZ = renderState.awca$getAttachmentOffsetZ();

        float x = attachmentOffsetX - (float) normal.x * verticalOffset;
        float y = attachmentOffsetY - (float) normal.y * verticalOffset;
        float z = attachmentOffsetZ - (float) normal.z * verticalOffset;

        poseStack.translate(x, y, z);

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(Math.signum(0.5f - componentY - componentZ - componentX) * yaw));

        return true;
    }

    /**
     * Call this after rendering the climber to restore the pose stack.
     * Use this in renderers that use the render state system (1.21+).
     *
     * <p>Example usage:
     * <pre>
     * {@literal @}Override
     * public void render(MyRenderState state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
     *     ClientClimberHelper.preRenderClimber(state, poseStack);
     *     super.render(state, poseStack, buffer, packedLight);
     *     ClientClimberHelper.postRenderClimber(state, poseStack, buffer);
     * }
     * </pre>
     *
     * @param renderState The render state (must implement IAdvancedClimberRenderState)
     * @param poseStack   The pose stack to restore transformations from
     * @param <S>         The render state type that implements IAdvancedClimberRenderState
     * @return true if climbing transformations were reversed, false otherwise
     */
    public static <S extends IAdvancedClimberRenderState> boolean postRenderClimber(S renderState, PoseStack poseStack) {
        if (!renderState.awca$isClimbing()) {
            return false;
        }

        Vec3 normal = renderState.awca$getNormal();
        float yaw = renderState.awca$getYaw();
        float pitch = renderState.awca$getPitch();
        float componentX = renderState.awca$getComponentX();
        float componentY = renderState.awca$getComponentY();
        float componentZ = renderState.awca$getComponentZ();
        float verticalOffset = renderState.awca$getVerticalOffset();

        float attachmentOffsetX = renderState.awca$getAttachmentOffsetX();
        float attachmentOffsetY = renderState.awca$getAttachmentOffsetY();
        float attachmentOffsetZ = renderState.awca$getAttachmentOffsetZ();

        float x = attachmentOffsetX - (float) normal.x * verticalOffset;
        float y = attachmentOffsetY - (float) normal.y * verticalOffset;
        float z = attachmentOffsetZ - (float) normal.z * verticalOffset;

        // Reverse the transformations in opposite order
        poseStack.mulPose(Axis.YP.rotationDegrees(-Math.signum(0.5f - componentY - componentZ - componentX) * yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));

        // Debug rendering
        if (Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
            renderDebugInfoFromState(renderState, poseStack, x, y, z);
        }

        poseStack.translate(-x, -y, -z);

        return true;
    }



    private static <S extends IAdvancedClimberRenderState> void renderDebugInfoFromState(
            S state, PoseStack poseStack, float x, float y, float z) {

        Vec3 normal = state.awca$getNormal();
        Vec3 localX = state.awca$getLocalX();
        Vec3 localY = state.awca$getLocalY();
        Vec3 localZ = state.awca$getLocalZ();

        // Origin marker
        Gizmos.cuboid(new AABB(0, 0, 0, 0, 0, 0).inflate(0.2f),
            GizmoStyle.stroke(0xFFFFFFFF));

        // Draw normal direction (magenta)
        Vec3 origin = Vec3.ZERO;
        Vec3 normalEnd = new Vec3(normal.x * 2, normal.y * 2, normal.z * 2);
        Gizmos.arrow(origin, normalEnd, 0xFFFF00FF); // Magenta
        Gizmos.cuboid(
            new AABB(0, 0, 0, 0, 0, 0)
                .move(normalEnd.x, normalEnd.y, normalEnd.z)
                .inflate(0.025f), GizmoStyle.stroke(0xFFFF00FF));

        float halfHeight = 0.5f; // Default height since we don't have entity access
        Vec3 axisOrigin = new Vec3(-x, halfHeight - y, -z);

        // X axis (red)
        Vec3 localXEnd = new Vec3(-x + localX.x, halfHeight - y + localX.y, -z + localX.z);
        Gizmos.arrow(axisOrigin, localXEnd, 0xFFFF0000); // Red
        Gizmos.cuboid(
            new AABB(0, 0, 0, 0, 0, 0)
                .move(localXEnd.x, localXEnd.y, localXEnd.z)
                .inflate(0.025f), GizmoStyle.stroke(0xFFFF0000));

        // Y axis (green)
        Vec3 localYEnd = new Vec3(-x + localY.x, halfHeight - y + localY.y, -z + localY.z);
        Gizmos.arrow(axisOrigin, localYEnd, 0xFF00FF00); // Green
        Gizmos.cuboid(
            new AABB(0, 0, 0, 0, 0, 0)
                .move(localYEnd.x, localYEnd.y, localYEnd.z)
                .inflate(0.025f), GizmoStyle.stroke(0xFF00FF00));

        // Z axis (blue)
        Vec3 localZEnd = new Vec3(-x + localZ.x, halfHeight - y + localZ.y, -z + localZ.z);
        Gizmos.arrow(axisOrigin, localZEnd, 0xFF0000FF); // Blue
        Gizmos.cuboid(new AABB(0, 0, 0, 0, 0, 0)
                .move(localZEnd.x, localZEnd.y, localZEnd.z)
                .inflate(0.025f), GizmoStyle.stroke(0xFF0000FF));
    }
}

