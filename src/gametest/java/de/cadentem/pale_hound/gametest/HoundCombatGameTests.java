package de.cadentem.pale_hound.gametest;
import de.cadentem.pale_hound.entities.*;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("pale_hound") @PrefixGameTestTemplate(false)
public final class HoundCombatGameTests {
    @GameTest(template="stalking_test",skyAccess=true,setupTicks=30)
    public static void topAndSideHitsReachTheHoundWithoutExpandingCaveCollision(GameTestHelper helper) {
        var hound=ModEntityTypes.PALE_HOUND.get().create(helper.getLevel());
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64,2,64))));
        hound.setNoAi(true);hound.setCrawling(true);hound.yBodyRot=0;
        helper.getLevel().addFreshEntity(hound);HoundCombatBounds.update(hound,hound.getParts());
        try {
            helper.assertTrue(hound.getBoundingBox().getYsize() <= .51,"Combat targeting enlarged the physical cave collision box");
            var head=hound.getParts()[1];var b=head.getBoundingBox();
            var top=new Vec3(b.getCenter().x,b.maxY+.5,b.getCenter().z);
            var bottom=top.add(0,-1.5,0);
            var queried=helper.getLevel().getEntities(hound,new AABB(top,bottom).inflate(.01));
            helper.assertTrue(queried.contains(head),"World targeting query could not find the head from above");
            helper.assertTrue(b.clip(top,bottom).isPresent(),"Top-down attack ray missed visible upper anatomy");
            var arm=hound.getParts()[2].getBoundingBox();
            helper.assertTrue(arm.clip(arm.getCenter().add(2,0,0),arm.getCenter().add(-2,0,0)).isPresent(),"Side attack ray missed arm anatomy");
            float health=hound.getHealth();
            helper.assertTrue(head.hurt(hound.damageSources().generic(),4) && hound.getHealth()<health,"Anatomical hit did not damage the parent Hound");
            for(int i=0;i<hound.getParts().length;i++) helper.assertTrue(helper.getLevel().getEntityOrPart(hound.getId()+i+1)==hound.getParts()[i],"Part IDs were not registered for server attack packets");
        } finally {hound.discard();}
        helper.succeed();
    }
}
