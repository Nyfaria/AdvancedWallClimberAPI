package com.nyfaria.awcapi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nyfaria.awcapi.client.IAdvancedClimberRenderState;
import com.nyfaria.awcapi.entity.IAdvancedClimber;
import com.nyfaria.awcapi.entity.Orientation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

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
     * @param buffer      The buffer source (for debug rendering, can be null)
     * @param <S>         The render state type that implements IAdvancedClimberRenderState
     * @return true if climbing transformations were reversed, false otherwise
     */
    public static <S extends IAdvancedClimberRenderState> boolean postRenderClimber(S renderState, PoseStack poseStack, MultiBufferSource buffer) {
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
        if (buffer != null && Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            renderDebugInfoFromState(renderState, poseStack, buffer, x, y, z);
        }

        poseStack.translate(-x, -y, -z);

        return true;
    }

    /**
     * Simplified version of postRenderClimber without debug rendering.
     *
     * @param renderState The render state (must implement IAdvancedClimberRenderState)
     * @param poseStack   The pose stack to restore transformations from
     * @param <S>         The render state type that implements IAdvancedClimberRenderState
     * @return true if climbing transformations were reversed, false otherwise
     */
    public static <S extends IAdvancedClimberRenderState> boolean postRenderClimber(S renderState, PoseStack poseStack) {
        return postRenderClimber(renderState, poseStack, null);
    }

    private static <S extends IAdvancedClimberRenderState> void renderDebugInfoFromState(
            S state, PoseStack poseStack, MultiBufferSource buffer, float x, float y, float z) {

        Vec3 normal = state.awca$getNormal();
        Vec3 localX = state.awca$getLocalX();
        Vec3 localY = state.awca$getLocalY();
        Vec3 localZ = state.awca$getLocalZ();

        ShapeRenderer.renderLineBox(poseStack, buffer.getBuffer(RenderType.LINES),
            new AABB(0, 0, 0, 0, 0, 0).inflate(0.2f), 1.0f, 1.0f, 1.0f, 1.0f);

        // Draw normal direction
        Matrix4f matrix4f = poseStack.last().pose();
        VertexConsumer builder = buffer.getBuffer(RenderType.LINES);

        builder.addVertex(matrix4f, 0, 0, 0).setColor(0, 1, 1, 1).setNormal(0, 0, 0);
        builder.addVertex(matrix4f, (float) normal.x * 2, (float) normal.y * 2, (float) normal.z * 2)
            .setColor(1.0f, 0.0f, 1.0f, 1.0f).setNormal(0, 0, 0);

        ShapeRenderer.renderLineBox(poseStack, buffer.getBuffer(RenderType.LINES),
            new AABB(0, 0, 0, 0, 0, 0)
                .move((float) normal.x * 2, (float) normal.y * 2, (float) normal.z * 2)
                .inflate(0.025f), 1.0f, 0.0f, 1.0f, 1.0f);

        poseStack.pushPose();
        poseStack.translate(-x, -y, -z);

        matrix4f = poseStack.last().pose();
        float halfHeight = 0.5f; // Default height since we don't have entity access

        // X axis (red)
        builder.addVertex(matrix4f, 0, halfHeight, 0).setColor(0, 1, 1, 1).setNormal(0, 0, 0);
        builder.addVertex(matrix4f, (float) localX.x, halfHeight + (float) localX.y, (float) localX.z)
            .setColor(1.0f, 0.0f, 0.0f, 1.0f).setNormal(0, 0, 0);
        ShapeRenderer.renderLineBox(poseStack, buffer.getBuffer(RenderType.LINES),
            new AABB(0, 0, 0, 0, 0, 0)
                .move((float) localX.x, halfHeight + (float) localX.y, (float) localX.z)
                .inflate(0.025f), 1.0f, 0.0f, 0.0f, 1.0f);

        // Y axis (green)
        builder.addVertex(matrix4f, 0, halfHeight, 0).setColor(0, 1, 1, 1).setNormal(0, 0, 0);
        builder.addVertex(matrix4f, (float) localY.x, halfHeight + (float) localY.y, (float) localY.z)
            .setColor(0.0f, 1.0f, 0.0f, 1.0f).setNormal(0, 0, 0);
        ShapeRenderer.renderLineBox(poseStack, buffer.getBuffer(RenderType.LINES),
            new AABB(0, 0, 0, 0, 0, 0)
                .move((float) localY.x, halfHeight + (float) localY.y, (float) localY.z)
                .inflate(0.025f), 0.0f, 1.0f, 0.0f, 1.0f);

        // Z axis (blue)
        builder.addVertex(matrix4f, 0, halfHeight, 0).setColor(0, 1, 1, 1).setNormal(0, 0, 0);
        builder.addVertex(matrix4f, (float) localZ.x, halfHeight + (float) localZ.y, (float) localZ.z)
            .setColor(0.0f, 0.0f, 1.0f, 1.0f).setNormal(0, 0, 0);
        ShapeRenderer.renderLineBox(poseStack, buffer.getBuffer(RenderType.LINES),
            new AABB(0, 0, 0, 0, 0, 0)
                .move((float) localZ.x, halfHeight + (float) localZ.y, (float) localZ.z)
                .inflate(0.025f), 0.0f, 0.0f, 1.0f, 1.0f);

        poseStack.popPose();
    }
}

