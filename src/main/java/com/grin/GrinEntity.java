package com.grin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class GrinEntity extends HostileEntity {
    private boolean frozen = false;
    private int watchedTicks = 0;

    public GrinEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.34)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 32.0f));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    private boolean isWatched() {
        for (PlayerEntity p : getWorld().getPlayers()) {
            if (p.isSpectator() || distanceTo(p) > 64) continue;
            Vec3d look = p.getRotationVec(1.0f).normalize();
            Vec3d to = getEyePos().subtract(p.getEyePos()).normalize();
            if (look.dotProduct(to) > 0.6 && p.canSee(this)) return true;
        }
        return false;
    }

    @Override
    protected void mobTick() {
        super.mobTick();

        frozen = isWatched();
        if (frozen) watchedTicks++; else watchedTicks = Math.max(0, watchedTicks - 2);

        // Held eye contact for too long.
        if (watchedTicks > 200) {
            for (PlayerEntity p : getWorld().getPlayers()) {
                if (distanceTo(p) < 24) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0));
                    getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                            SoundCategory.HOSTILE, 0.6f, 0.5f);
                }
            }
            discard();
            return;
        }

        if (age % 40 == 0) {
            PlayerEntity near = getWorld().getClosestPlayer(this, 16);
            if (near != null) {
                getWorld().playSound(null, near.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                        SoundCategory.HOSTILE, 1.0f, 1.0f);
            }
            PlayerEntity close = getWorld().getClosestPlayer(this, 10);
            if (close != null) {
                for (BlockPos pos : BlockPos.iterate(getBlockPos().add(-6, -3, -6), getBlockPos().add(6, 3, 6))) {
                    BlockState s = getWorld().getBlockState(pos);
                    if (s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH)) {
                        getWorld().breakBlock(pos, true);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void travel(Vec3d movementInput) {
        if (frozen) {
            super.travel(Vec3d.ZERO);
            return;
        }
        super.travel(movementInput);
    }

    @Override
    public boolean tryAttack(Entity target) {
        if (frozen) return false;
        boolean hit = super.tryAttack(target);
        if (hit && target instanceof PlayerEntity p) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0));
            getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GHAST_SCREAM,
                    SoundCategory.HOSTILE, 1.0f, 0.4f);
        }
        return hit;
    }
}
