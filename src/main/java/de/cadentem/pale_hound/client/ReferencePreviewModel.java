package de.cadentem.pale_hound.client;

import de.cadentem.pale_hound.entities.ReferencePreviewEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class ReferencePreviewModel extends GeoModel<ReferencePreviewEntity> {
    private static ResourceLocation asset(String path) { return ResourceLocation.fromNamespaceAndPath("pale_hound", path); }
    @Override public ResourceLocation getModelResource(ReferencePreviewEntity entity) { return asset("geo/reference_preview.geo.json"); }
    @Override public ResourceLocation getTextureResource(ReferencePreviewEntity entity) { return asset("textures/entity/reference_preview.png"); }
    @Override public ResourceLocation getAnimationResource(ReferencePreviewEntity entity) { return asset("animations/reference_preview.animation.json"); }
}
