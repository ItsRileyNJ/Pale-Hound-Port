package de.cadentem.pale_hound.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.util.Utils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class PaleHoundEyesLayer extends GeoRenderLayer<PaleHoundEntity> {
   public static ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("pale_hound", "textures/entity/pale_hound_eyes_texture" + Utils.getTextureAppend() + ".png");

   public PaleHoundEyesLayer(GeoRenderer<PaleHoundEntity> renderer) {
      super(renderer);
   }

   public void render(
      PoseStack poseStack,
      PaleHoundEntity animatable,
      BakedGeoModel bakedModel,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer buffer,
      float partialTick,
      int packedLight,
      int packedOverlay
   ) {
      packedLight = 15728880;
      RenderType eyesRenderType = RenderType.entityCutoutNoCull(TEXTURE);
      this.getRenderer()
         .reRender(
            this.getDefaultBakedModel(animatable),
            poseStack,
            bufferSource,
            animatable,
            eyesRenderType,
            bufferSource.getBuffer(eyesRenderType),
            partialTick,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            0xFFFFFFFF
         );
   }
}
