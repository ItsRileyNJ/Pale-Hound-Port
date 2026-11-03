package de.cadentem.pale_hound.client;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.util.Utils;
import net.minecraft.resources.ResourceLocation;
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
         head.setRotX(entityData.headPitch() * (float) (Math.PI / 180.0));
         head.setRotY(entityData.netHeadYaw() * (float) (Math.PI / 180.0));
      }

      super.setCustomAnimations(animatable, instanceId, animationState);
   }
}
