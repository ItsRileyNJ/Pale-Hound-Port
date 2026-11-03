package de.cadentem.pale_hound.client;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.util.Utils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class PaleHoundModel extends GeoModel<PaleHoundEntity> {
   public ResourceLocation getModelResource(PaleHoundEntity ignored) {
      return ResourceLocation.fromNamespaceAndPath("pale_hound", "geo/pale_hound.geo" + Utils.getTextureAppend() + ".json");
   }

   public ResourceLocation getTextureResource(PaleHoundEntity ignored) {
      return ResourceLocation.fromNamespaceAndPath("pale_hound", "textures/entity/pale_hound_texture" + Utils.getTextureAppend() + ".png");
   }

   public ResourceLocation getAnimationResource(PaleHoundEntity ignored) {
      return ResourceLocation.fromNamespaceAndPath("pale_hound", "animations/pale_hound.animation.json");
   }

   public void setCustomAnimations(PaleHoundEntity animatable, long instanceId, AnimationState<PaleHoundEntity> animationState) {
      GeoBone head = this.getAnimationProcessor().getBone("head");
      if (head != null) {
         EntityModelData entityData = (EntityModelData)animationState.getData(DataTickets.ENTITY_MODEL_DATA);
         float radians = (float) (Math.PI / 180.0);
         if (animatable.isCrawling()) {
            // The lowered rig faces along local X: pitch around Z rather than twisting the skull around X.
            head.setRotX(0);
            head.setRotZ(head.getRotZ() - Mth.clamp(entityData.headPitch(), -20, 20) * radians);
            head.setRotY(Mth.clamp(entityData.netHeadYaw(), -35, 35) * radians);
         } else {
            head.setRotX(entityData.headPitch() * radians);
            head.setRotY(entityData.netHeadYaw() * radians);
         }
      }

      super.setCustomAnimations(animatable, instanceId, animationState);
   }
}
