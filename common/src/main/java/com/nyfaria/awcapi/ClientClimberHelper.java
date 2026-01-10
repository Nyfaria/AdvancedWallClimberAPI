package com.nyfaria.awcapi;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.*;
import com.nyfaria.awcapi.entity.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import org.joml.*;

import java.lang.Math;
import java.util.*;

/**
 * Client helper class for the Advanced Wall Climber API.
 * Use these static methods to easily integrate wall climbing into your entities renderer.
 *
 * <p>Example usage in your renderer:
 * <pre>
 * public class MyClimberRenderer extends MobRenderer&lt;MyClimberEntity, MyClimberModel&gt; {
 *     {@literal @}Override
 *     public void render(MyClimberEntity entity, float entityYaw, float partialTicks,
 *                        PoseStack matrixStack, MultiBufferSource buffer, int packedLight) {
 *         ClientClimberHelper.preRenderClimber(entity, partialTicks, matrixStack);
 *         super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
 *         ClientClimberHelper.postRenderClimber(entity, partialTicks, matrixStack, buffer);
 *     }
 * }
 * </pre>
 */
public class ClientClimberHelper {

    // ==================== RENDERING ====================

    /**
     * Call this before rendering the climber to set up the pose stack correctly.
     *
     * @param climber The climber entity
     * @param partialTicks Partial tick time
     * @param matrixStack The pose stack
     */
    public static void preRenderClimber(IAdvancedClimber climber, float partialTicks, PoseStack matrixStack) {
        ClimberComponent component = climber.getClimberComponent();
        Orientation orientation = climber.getOrientation();
        Orientation renderOrientation = climber.calculateOrientation(partialTicks);
        climber.setRenderOrientation(renderOrientation);

        float verticalOffset = climber.getVerticalOffset(partialTicks);

        float x = climber.getAttachmentOffset(Direction.Axis.X, partialTicks) - (float) renderOrientation.normal.x * verticalOffset;
        float y = climber.getAttachmentOffset(Direction.Axis.Y, partialTicks) - (float) renderOrientation.normal.y * verticalOffset;
        float z = climber.getAttachmentOffset(Direction.Axis.Z, partialTicks) - (float) renderOrientation.normal.z * verticalOffset;

        matrixStack.translate(x, y, z);

        matrixStack.mulPose(Axis.YP.rotationDegrees(renderOrientation.yaw));
        matrixStack.mulPose(Axis.XP.rotationDegrees(renderOrientation.pitch));
        matrixStack.mulPose(Axis.YP.rotationDegrees((float) Math.signum(0.5f - orientation.componentY - orientation.componentZ - orientation.componentX) * renderOrientation.yaw));
    }

    /**
     * Call this after rendering the climber to restore the pose stack.
     *
     * @param climber The climber entity
     * @param partialTicks Partial tick time
     * @param matrixStack The pose stack
     * @param buffer The buffer source (for debug rendering, can be null)
     */
    public static void postRenderClimber(IAdvancedClimber climber, float partialTicks, PoseStack matrixStack, MultiBufferSource buffer) {
        Orientation orientation = climber.getOrientation();
        Orientation renderOrientation = climber.getRenderOrientation();

        if (renderOrientation != null) {
            float verticalOffset = climber.getVerticalOffset(partialTicks);

            float x = climber.getAttachmentOffset(Direction.Axis.X, partialTicks) - (float) renderOrientation.normal.x * verticalOffset;
            float y = climber.getAttachmentOffset(Direction.Axis.Y, partialTicks) - (float) renderOrientation.normal.y * verticalOffset;
            float z = climber.getAttachmentOffset(Direction.Axis.Z, partialTicks) - (float) renderOrientation.normal.z * verticalOffset;

            matrixStack.mulPose(Axis.YP.rotationDegrees(-(float) Math.signum(0.5f - orientation.componentY - orientation.componentZ - orientation.componentX) * renderOrientation.yaw));
            matrixStack.mulPose(Axis.XP.rotationDegrees(-renderOrientation.pitch));
            matrixStack.mulPose(Axis.YP.rotationDegrees(-renderOrientation.yaw));

            // Debug rendering
            if (buffer != null && Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes()) {
                renderDebugInfo(climber, partialTicks, matrixStack, buffer, x, y, z, orientation);
            }

            matrixStack.translate(-x, -y, -z);
        }
    }

    private static void renderDebugInfo(IAdvancedClimber climber, float partialTicks, PoseStack matrixStack,
                                         MultiBufferSource buffer, float x, float y, float z, Orientation orientation) {
        Mob mob = climber.asMob();

        LevelRenderer.renderLineBox(matrixStack, buffer.getBuffer(RenderType.LINES),
            new AABB(0, 0, 0, 0, 0, 0).inflate(0.2f), 1.0f, 1.0f, 1.0f, 1.0f);

        double rx = mob.xo + (mob.getX() - mob.xo) * partialTicks;
        double ry = mob.yo + (mob.getY() - mob.yo) * partialTicks;
        double rz = mob.zo + (mob.getZ() - mob.zo) * partialTicks;

        Vec3 movementTarget = climber.getTrackedMovementTarget();
        if (movementTarget != null) {
            LevelRenderer.renderLineBox(matrixStack, buffer.getBuffer(RenderType.LINES),
                new AABB(movementTarget.x() - 0.25f, movementTarget.y() - 0.25f, movementTarget.z() - 0.25f,
                        movementTarget.x() + 0.25f, movementTarget.y() + 0.25f, movementTarget.z() + 0.25f)
                    .move(-rx - x, -ry - y, -rz - z), 0.0f, 1.0f, 1.0f, 1.0f);
        }

        List<PathingTarget> pathingTargets = climber.getTrackedPathingTargets();
        if (pathingTargets != null) {
            int i = 0;
            for (PathingTarget pathingTarget : pathingTargets) {
                BlockPos pos = pathingTarget.pos;

                LevelRenderer.renderLineBox(matrixStack, buffer.getBuffer(RenderType.LINES),
                    new AABB(pos).move(-rx - x, -ry - y, -rz - z),
                    1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 0.15f);

                matrixStack.pushPose();
                matrixStack.translate(pos.getX() + 0.5D - rx - x, pos.getY() + 0.5D - ry - y, pos.getZ() + 0.5D - rz - z);
                matrixStack.mulPose(pathingTarget.side.getOpposite().getRotation());

                LevelRenderer.renderLineBox(matrixStack, buffer.getBuffer(RenderType.LINES),
                    new AABB(-0.501D, -0.501D, -0.501D, 0.501D, -0.45D, 0.501D),
                    1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f);

                Matrix4f matrix4f = matrixStack.last().pose();
                VertexConsumer builder = buffer.getBuffer(RenderType.LINES);

                builder.addVertex(matrix4f, -0.501f, -0.45f, -0.501f).setColor(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f);
                builder.addVertex(matrix4f, 0.501f, -0.45f, 0.501f).setColor(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f);
                builder.addVertex(matrix4f, -0.501f, -0.45f, 0.501f).setColor(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f);
                builder.addVertex(matrix4f, 0.501f, -0.45f, -0.501f).setColor(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f);

                matrixStack.popPose();
                i++;
            }
        }

        // Draw orientation axes
        Matrix4f matrix4f = matrixStack.last().pose();
        VertexConsumer builder = buffer.getBuffer(RenderType.LINES);

        builder.addVertex(matrix4f, 0, 0, 0).setColor(0, 1, 1, 1).setNormal(0, 0, 0);
        builder.addVertex(matrix4f, (float) orientation.normal.x * 2, (float) orientation.normal.y * 2, (float) orientation.normal.z * 2)
            .setColor(1.0f, 0.0f, 1.0f, 1.0f).setNormal(0, 0, 0);

        LevelRenderer.renderLineBox(matrixStack, buffer.getBuffer(RenderType.LINES),
            new AABB(0, 0, 0, 0, 0, 0)
                .move((float) orientation.normal.x * 2, (float) orientation.normal.y * 2, (float) orientation.normal.z * 2)
                .inflate(0.025f), 1.0f, 0.0f, 1.0f, 1.0f);

        matrixStack.pushPose();
        matrixStack.translate(-x, -y, -z);

        matrix4f = matrixStack.last().pose();

        float halfHeight = mob.getBbHeight() * 0.5f;

        // X axis (red)
        builder.addVertex(matrix4f, 0, halfHeight, 0).setColor(0, 1, 1, 1).setNormal(0, 0, 0);
        builder.addVertex(matrix4f, (float) orientation.localX.x, halfHeight + (float) orientation.localX.y, (float) orientation.localX.z)
            .setColor(1.0f, 0.0f, 0.0f, 1.0f).setNormal(0, 0, 0);
        LevelRenderer.renderLineBox(matrixStack, buffer.getBuffer(RenderType.LINES),
            new AABB(0, 0, 0, 0, 0, 0)
                .move((float) orientation.localX.x, halfHeight + (float) orientation.localX.y, (float) orientation.localX.z)
                .inflate(0.025f), 1.0f, 0.0f, 0.0f, 1.0f);

        // Y axis (green)
        builder.addVertex(matrix4f, 0, halfHeight, 0).setColor(0, 1, 1, 1).setNormal(0, 0, 0);
        builder.addVertex(matrix4f, (float) orientation.localY.x, halfHeight + (float) orientation.localY.y, (float) orientation.localY.z)
            .setColor(0.0f, 1.0f, 0.0f, 1.0f).setNormal(0, 0, 0);
        LevelRenderer.renderLineBox(matrixStack, buffer.getBuffer(RenderType.LINES),
            new AABB(0, 0, 0, 0, 0, 0)
                .move((float) orientation.localY.x, halfHeight + (float) orientation.localY.y, (float) orientation.localY.z)
                .inflate(0.025f), 0.0f, 1.0f, 0.0f, 1.0f);

        // Z axis (blue)
        builder.addVertex(matrix4f, 0, halfHeight, 0).setColor(0, 1, 1, 1).setNormal(0, 0, 0);
        builder.addVertex(matrix4f, (float) orientation.localZ.x, halfHeight + (float) orientation.localZ.y, (float) orientation.localZ.z)
            .setColor(0.0f, 0.0f, 1.0f, 1.0f).setNormal(0, 0, 0);
        LevelRenderer.renderLineBox(matrixStack, buffer.getBuffer(RenderType.LINES),
            new AABB(0, 0, 0, 0, 0, 0)
                .move((float) orientation.localZ.x, halfHeight + (float) orientation.localZ.y, (float) orientation.localZ.z)
                .inflate(0.025f), 0.0f, 0.0f, 1.0f, 1.0f);

        matrixStack.popPose();
    }
}

