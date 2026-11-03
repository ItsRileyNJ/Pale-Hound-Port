package de.cadentem.pale_hound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.util.Utils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class PaleHoundRenderer extends GeoEntityRenderer<PaleHoundEntity> {
   public PaleHoundRenderer(Context context) {
      super(context, new PaleHoundModel());
      this.shadowRadius = 0.3F;
      this.addRenderLayer(new PaleHoundEyesLayer(this));
   }

   @NotNull
   public ResourceLocation getTextureLocation(@NotNull PaleHoundEntity instance) {
      return ResourceLocation.fromNamespaceAndPath("pale_hound", "textures/entity/pale_hound_texture" + Utils.getTextureAppend() + ".png");
   }

   public void render(
      PaleHoundEntity entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight
   ) {
      poseStack.pushPose();
      if (entity.isBaby()) {
         poseStack.scale(0.1F, 0.1F, 0.1F);
      } else {
         poseStack.scale(1.3F, 1.3F, 1.3F);
      }

      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
      poseStack.popPose();
   }
}
