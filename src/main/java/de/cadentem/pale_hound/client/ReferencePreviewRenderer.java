package de.cadentem.pale_hound.client;

import de.cadentem.pale_hound.entities.ReferencePreviewEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public final class ReferencePreviewRenderer extends GeoEntityRenderer<ReferencePreviewEntity> {
    public ReferencePreviewRenderer(EntityRendererProvider.Context context) {
        super(context, new ReferencePreviewModel()); shadowRadius = 1.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
