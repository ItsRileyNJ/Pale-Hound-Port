package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.entities.*;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid="pale_hound",value=Dist.CLIENT)
public final class ClientHoundCombatSmoke {
    private static boolean started,attacked;
    private static volatile int id=-1;
    private static int ticks;
    private static volatile boolean damageConfirmed;
    @SubscribeEvent public static void draw(RenderGuiEvent.Post event) {
        if (!Boolean.getBoolean("pale_hound.combatSmoke")) return;
        var client=Minecraft.getInstance();
        if (client.level==null || !(client.level.getEntity(id) instanceof PaleHoundEntity hound)) return;
        var g=event.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight();
        g.fill(0,0,w,h,0xFF18191A);
        // A real renderer view from above, so shoulder gaps cannot hide behind the chest.
        var pose=new Quaternionf().rotateZ((float)Math.PI).rotateX((float)Math.toRadians(-65));
        InventoryScreen.renderEntityInInventory(g,w/2F,h*.78F,Math.min(w,h)/5.2F,new Vector3f(),pose,null,hound);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("pale_hound.combatSmoke")) return;
        var c=Minecraft.getInstance();if(c.player==null||c.getSingleplayerServer()==null)return;
        if(!started){
            started=true;c.options.hideGui=false;c.options.pauseOnLostFocus=false;
            var uuid=c.player.getUUID();var server=c.getSingleplayerServer();
            server.execute(()->{
                var player=server.getPlayerList().getPlayer(uuid);var level=player.serverLevel();
                player.setGameMode(GameType.CREATIVE);player.teleportTo(0.5,105,0.5);
                level.getEntitiesOfClass(PaleHoundEntity.class,new AABB(-4,98,-4,5,108,5), e->e.isNoAi()).forEach(net.minecraft.world.entity.Entity::discard);
                var hound=new PaleHoundEntity(ModEntityTypes.PALE_HOUND.get(),level) {
                    @Override public void tick() { super.tick(); setCrawling(true); HoundCombatBounds.update(this,getParts()); }
                };hound.setNoAi(true);hound.setNoGravity(true);
                hound.setPos(.5,102,.5);hound.setCrawling(true);level.addFreshEntity(hound);id=hound.getId();
                var head=hound.getParts()[1].getBoundingBox();player.teleportTo(head.getCenter().x,head.maxY+.3,head.getCenter().z);
            });
        }
        ticks++;
        if(ticks>=60&&!attacked&&c.level.getEntity(id) instanceof PaleHoundEntity hound){
            var b=hound.getParts()[1].getBoundingBox();var start=new Vec3(b.getCenter().x,b.maxY+.3,b.getCenter().z);var end=start.add(0,-1.5,0);
            var hit=ProjectileUtil.getEntityHitResult(c.player,start,end,new AABB(start,end).inflate(.01),e->e.isPickable() && (e==hound || e instanceof HoundCombatPart part && part.getParent()==hound),4);
            if(hit==null||!(hit.getEntity() instanceof HoundCombatPart part)||part.getParent()!=hound)throw new AssertionError("Client top-down pick missed Hound anatomy");
            for(int i=0;i<hound.getParts().length;i++)if(hound.getParts()[i].getId()!=hound.getId()+i+1)throw new AssertionError("Client part IDs differ");
            c.gameMode.attack(c.player,hit.getEntity());attacked=true;
        }
        if(ticks==100){
            var server=c.getSingleplayerServer();server.execute(()->{
                var hound=(PaleHoundEntity)server.overworld().getEntity(id);
                if(hound==null||hound.getHealth()>=hound.getMaxHealth())throw new AssertionError("Real multipart attack packet did not damage server Hound");
                damageConfirmed=true;
            });
        }
        if(ticks==130)Screenshot.grab(c.gameDirectory,"hound-shoulders-top.png",c.getMainRenderTarget(),m->{});
        if(ticks==160){
            if(!damageConfirmed)throw new AssertionError("Server damage was not confirmed");
            de.cadentem.pale_hound.PaleHound.LOG.info("COMBAT CLIENT SMOKE PASSED: top pick, synchronized part IDs and real server damage verified");c.stop();
        }
    }
}
