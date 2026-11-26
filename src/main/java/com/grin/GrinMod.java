package com.grin;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

public class GrinMod implements ModInitializer {
    public static final String MOD_ID = "grinmod";

    public static final EntityType<GrinEntity> GRIN = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "grin"),
            EntityType.Builder.create(GrinEntity::new, SpawnGroup.MONSTER)
                    .dimensions(1.0f, 2.4f)
                    .maxTrackingRange(10)
                    .build("grin"));

    public static final Item GRIN_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "grin_spawn_egg"),
            new SpawnEggItem(GRIN, 0x000000, 0xFFFFFF, new Item.Settings()));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(GRIN, GrinEntity.createAttributes());

        SpawnRestriction.register(GRIN, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, GrinEntity::canSpawnInDark);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(),
                SpawnGroup.MONSTER, GRIN, 1, 1, 1);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS)
                .register(entries -> entries.add(GRIN_SPAWN_EGG));

        // Occasionally something shows up behind a player at night.
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getRegistryKey() != World.OVERWORLD) return;
            if (world.getTime() % 600 != 0 || !world.isNight()) return;

            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.isSpectator()) continue;
                if (world.random.nextInt(12) != 0) continue;
                if (!world.getEntitiesByType(GRIN, p.getBoundingBox().expand(128), e -> true).isEmpty()) continue;

                Vec3d back = p.getRotationVector().multiply(-1, 0, -1).normalize().multiply(22);
                int x = MathHelper.floor(p.getX() + back.x);
                int z = MathHelper.floor(p.getZ() + back.z);
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(y - p.getBlockY()) > 6) continue;

                GrinEntity g = GRIN.create(world);
                if (g == null) continue;
                g.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 0, 0);
                world.spawnEntity(g);
            }
        });
    }
}
