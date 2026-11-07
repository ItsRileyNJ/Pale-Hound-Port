package de.cadentem.pale_hound.entities;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pose bounds baked from the real rig, shared by server attacks and client crosshairs. */
public final class HoundCombatBounds {
    private static final Map<String, AABB[]> POSES = load();
    private HoundCombatBounds() {}
    public static final class State {
        private String pose;
        private AABB[] previous;
        private int changedAt;
    }
    private static Map<String,AABB[]> load() {
        try (var stream = HoundCombatBounds.class.getResourceAsStream("/assets/pale_hound/combat_bounds.json")) {
            if (stream == null) throw new IllegalStateException("Missing Hound combat bounds");
            var result = new HashMap<String,AABB[]>();
            for (var entry : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) {
                var array = entry.getValue().getAsJsonArray(); var boxes = new AABB[array.size()];
                for (int i=0;i<boxes.length;i++) {
                    var v=array.get(i).getAsJsonArray(); boxes[i]=new AABB(v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble(),v.get(3).getAsDouble(),v.get(4).getAsDouble(),v.get(5).getAsDouble());
                }
                result.put(entry.getKey(),boxes);
            }
            return Map.copyOf(result);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    public static void update(PaleHoundEntity hound, HoundCombatPart[] parts) {
        if (parts == null) return;
        String pose = hound.isCrawling() ? hound.getAnimationCrawlPose().name().toLowerCase(java.util.Locale.ROOT)
            : hound.isCrouching() || hound.isAggressive() ? "crouching" : "standing";
        var state=hound.getCombatBoundsState();
        if (!pose.equals(state.pose)) {
            state.previous=state.pose==null?null:POSES.get(state.pose);state.pose=pose;state.changedAt=hound.tickCount;
        }
        if (hound.tickCount-state.changedAt>=30) state.previous=null;
        var boxes=POSES.get(pose); double yaw=Math.toRadians(-hound.yBodyRot), scale=hound.getScale();
        for(int i=0;i<parts.length;i++) {
            var b=state.previous==null?boxes[i]:boxes[i].minmax(state.previous[i]); AABB bounds=null;
            for(int x=0;x<2;x++) for(int y=0;y<2;y++) for(int z=0;z<2;z++) {
                var p=new Vec3(x==0?b.minX:b.maxX,y==0?b.minY:b.maxY,z==0?b.minZ:b.maxZ).scale(scale).yRot((float)yaw).add(hound.position());
                var corner=new AABB(p,p); bounds=bounds==null?corner:bounds.minmax(corner);
            }
            var part=parts[i]; part.setPos(bounds.getCenter().x,bounds.minY,bounds.getCenter().z); part.setBoundingBox(bounds);
            part.xo=part.getX(); part.yo=part.getY(); part.zo=part.getZ();
        }
    }
}
