package com.LastEcho.lastecho_npc;

import com.periut.retroapi.entity.spawn.RetroMobSpawnData;
import com.periut.retroapi.particle.RetroParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HumanEntity extends AnimalEntity implements RetroMobSpawnData {

    public static final NamespacedIdentifier ID = LastEchoNPCMod.id("human");

    private static final float[] SIZE_SCALES = {0.75F, 0.85F, 0.95F};

    private static final int SKIN_SLOT = 16;
    private static final int STATIONARY_SLOT = 17;
    private static final int SIZE_SLOT = 18;
    private static final int PERSONALITY_SLOT = 19;
    private static final int FOLLOWING_SLOT = 20;
    private static final int SEX_SLOT = 21;

    // ---- Food items that exist in Beta 1.7.3 (using numeric IDs) ----
    private static final Set<Integer> FOOD_IDS = new HashSet<>();

    static {
        // Verified food IDs from Beta 1.7.3
        FOOD_IDS.add(260); // Apple
        FOOD_IDS.add(297); // Bread
        FOOD_IDS.add(320); // Cooked Porkchop
        FOOD_IDS.add(322); // Golden Apple
        FOOD_IDS.add(282); // Mushroom Soup
        FOOD_IDS.add(357); // Cookie
        FOOD_IDS.add(335); // Milk (u drink it)
        // Note: Raw porkchop is ID 319, could add it but maybe not
    }

    private int ambientTimer = 0;
    private int nextAmbientInterval = 200;

    private int messageCooldown = 0;
    private int lastMessageIndex = -1;

    public HumanEntity(World world) {
        super(world);
        this.setBoundingBoxSpacing(0.6F, 1.8F);
        this.health = 20;
        this.movementSpeed = 0.3F;

        List<String> skins = LastEchoNPCMod.SKIN_PATHS;
        int skinIndex = 0;
        if (!skins.isEmpty()) {
            skinIndex = this.random.nextInt(skins.size());
        }
        this.setSkinIndex(skinIndex);
        updateTextureFromSkin();

        setStationary(false);
        setSizeIndex(1);

        int count = LastEchoNPCMod.getPersonalityCount();
        int personality = count == 0 ? 0 : this.random.nextInt(count);
        setPersonalityIndex(personality);

        setSex(this.random.nextInt(2));
        setFollowing(false);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(SKIN_SLOT, (byte)0);
        this.dataTracker.startTracking(STATIONARY_SLOT, (byte)0);
        this.dataTracker.startTracking(SIZE_SLOT, (byte)1);
        this.dataTracker.startTracking(PERSONALITY_SLOT, (byte)0);
        this.dataTracker.startTracking(FOLLOWING_SLOT, (byte)0);
        this.dataTracker.startTracking(SEX_SLOT, (byte)0);
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putByte("skinIndex", (byte) this.getSkinIndex());
        nbt.putBoolean("stationary", this.isStationary());
        nbt.putByte("sizeIndex", (byte) this.getSizeIndex());
        nbt.putByte("personalityIndex", (byte) this.getPersonalityIndex());
        nbt.putByte("following", (byte) (isFollowing() ? 1 : 0));
        nbt.putByte("sex", (byte) getSex());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.setSkinIndex(nbt.getByte("skinIndex"));
        this.setStationary(nbt.getBoolean("stationary"));
        this.setSizeIndex(nbt.getByte("sizeIndex"));
        this.setPersonalityIndex(nbt.getByte("personalityIndex"));
        this.setFollowing(nbt.getByte("following") == 1);
        this.setSex(nbt.getByte("sex"));
        updateTextureFromSkin();
    }

    // --- Skin ---
    public int getSkinIndex() {
        return this.dataTracker.getByte(SKIN_SLOT) & 0xFF;
    }

    public void setSkinIndex(int index) {
        this.dataTracker.set(SKIN_SLOT, (byte) index);
    }

    private void updateTextureFromSkin() {
        List<String> skins = LastEchoNPCMod.SKIN_PATHS;
        if (skins.isEmpty()) {
            this.texture = "/assets/lastecho_npc/textures/entity/human0.png";
            return;
        }
        int idx = getSkinIndex() % skins.size();
        this.texture = skins.get(idx);
    }

    public void cycleSkin() {
        List<String> skins = LastEchoNPCMod.SKIN_PATHS;
        if (skins.isEmpty()) return;
        int newIndex = (getSkinIndex() + 1) % skins.size();
        setSkinIndex(newIndex);
        updateTextureFromSkin();
    }

    // --- Stationary ---
    public boolean isStationary() {
        return this.dataTracker.getByte(STATIONARY_SLOT) == 1;
    }

    public void setStationary(boolean stationary) {
        this.dataTracker.set(STATIONARY_SLOT, (byte)(stationary ? 1 : 0));
    }

    // --- Size ---
    public int getSizeIndex() {
        return this.dataTracker.getByte(SIZE_SLOT) & 0xFF;
    }

    public void setSizeIndex(int index) {
        if (index < 0) index = 0;
        if (index >= SIZE_SCALES.length) index = SIZE_SCALES.length - 1;
        this.dataTracker.set(SIZE_SLOT, (byte) index);
        float scale = SIZE_SCALES[index];
        this.setBoundingBoxSpacing(0.6F * scale, 1.8F * scale);
    }

    public float getSizeScale() {
        int idx = getSizeIndex();
        return (idx >= 0 && idx < SIZE_SCALES.length) ? SIZE_SCALES[idx] : 1.0F;
    }

    public void cycleSize() {
        int newIndex = (getSizeIndex() + 1) % SIZE_SCALES.length;
        setSizeIndex(newIndex);
    }

    // --- Personality ---
    public int getPersonalityIndex() {
        return this.dataTracker.getByte(PERSONALITY_SLOT) & 0xFF;
    }

    public void setPersonalityIndex(int index) {
        int count = LastEchoNPCMod.getPersonalityCount();
        if (count == 0) index = 0;
        else index = Math.max(0, Math.min(index, count - 1));
        this.dataTracker.set(PERSONALITY_SLOT, (byte) index);
    }

    public void cyclePersonality() {
        int count = LastEchoNPCMod.getPersonalityCount();
        if (count == 0) return;
        int newIdx = (getPersonalityIndex() + 1) % count;
        setPersonalityIndex(newIdx);
    }

    // --- Following ---
    public boolean isFollowing() {
        return this.dataTracker.getByte(FOLLOWING_SLOT) == 1;
    }

    public void setFollowing(boolean following) {
        this.dataTracker.set(FOLLOWING_SLOT, (byte)(following ? 1 : 0));
    }

    // --- Sex ---
    public int getSex() {
        return this.dataTracker.getByte(SEX_SLOT) & 0xFF;
    }

    public void setSex(int sex) {
        if (sex < 0) sex = 0;
        if (sex > 1) sex = 1;
        this.dataTracker.set(SEX_SLOT, (byte) sex);
    }

    public void cycleSex() {
        setSex(getSex() == 0 ? 1 : 0);
    }

    // --- Helper to check if an item is food ---
    private boolean isFoodItem(int itemId) {
        return FOOD_IDS.contains(itemId);
    }

    // --- Sounds ---
    private String getSoundPrefix() {
        return getSex() == 0 ? "npc.male" : "npc.female";
    }

    public void playAmbientSound() {
        if (world.isRemote) return;
        String soundId = "lastecho_npc:" + getSoundPrefix() + ".idle";
        world.playSound(x, y, z, soundId, 0.3F, 1.0F + (random.nextFloat() - 0.5F) * 0.2F);
    }

    @Override
    public boolean damage(Entity source, int amount) {
        if (super.damage(source, amount)) {
            // Hurt sound is handled by getHurtSound()
            return true;
        }
        return false;
    }

    @Override
    protected String getHurtSound() {
        return "lastecho_npc:" + getSoundPrefix() + ".hurt";
    }

    @Override
    protected String getDeathSound() {
        return "lastecho_npc:" + getSoundPrefix() + ".hurt";
    }

    // --- Movement ---
    @Override
    protected void tickLiving() {
        if (messageCooldown > 0) {
            messageCooldown--;
        }

        if (isStationary()) {
            this.movementSpeed = 0.0F;
            this.velocityX = 0;
            this.velocityY = 0;
            this.velocityZ = 0;
            this.forwardSpeed = 0.0F;
            this.sidewaysSpeed = 0.0F;
            super.tickLiving();
            return;
        }

        // ---- Simplified follow: move toward the nearest player ----
        if (isFollowing()) {
            PlayerEntity target = world.getClosestPlayer(this, 16.0);
            if (target != null) {
                double dx = target.x - this.x;
                double dz = target.z - this.z;
                double distSq = dx*dx + dz*dz;
                if (distSq > 2.0) {
                    double dist = Math.sqrt(distSq);
                    float speed = 0.5F;
                    this.velocityX = (dx / dist) * speed;
                    this.velocityZ = (dz / dist) * speed;
                    this.yaw = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
                    this.movementSpeed = speed;
                } else {
                    this.movementSpeed = 0.0F;
                    this.velocityX *= 0.8;
                    this.velocityZ *= 0.8;
                }
            } else {
                this.movementSpeed = 0.3F;
            }
        } else {
            this.movementSpeed = 0.3F;
        }

        super.tickLiving();

        // ---- Ambient sounds ----
        if (!world.isRemote && !isStationary()) {
            ambientTimer++;
            if (ambientTimer >= nextAmbientInterval) {
                playAmbientSound();
                ambientTimer = 0;
                nextAmbientInterval = 200 + random.nextInt(300);
            }
        }
    }

    // --- Interaction ---
    @Override
    public boolean interact(PlayerEntity player) {
        ItemStack stack = player.inventory.getSelectedItem();
        if (stack != null) {
            // ---- Identity Tome ----
            if (stack.itemId == LastEchoNPCMod.IDENTITY_TOME.id) {
                if (!world.isRemote) {
                    cyclePersonality();
                    int newIdx = getPersonalityIndex();
                    String name = LastEchoNPCMod.getPersonalityName(newIdx);
                    player.sendMessage("Personality changed to: " + name);
                }
                return true;
            }

            // ---- Sugar (toggle follow) ----
            if (stack.itemId == Item.SUGAR.id) {
                if (!world.isRemote) {
                    boolean newFollow = !isFollowing();
                    setFollowing(newFollow);
                    player.sendMessage(newFollow ? "Human will follow you." : "Human stopped following.");
                }
                return true;
            }

            // ---- Crystal Rose (sex toggle) ----
            if (stack.itemId == LastEchoNPCMod.CRYSTAL_ROSE.id) {
                if (!world.isRemote) {
                    cycleSex();
                    String sexName = getSex() == 0 ? "Male" : "Female";
                    player.sendMessage("Sex changed to: " + sexName);
                }
                return true;
            }

            // ---- Food healing ----
            if (isFoodItem(stack.itemId)) {
                if (!world.isRemote) {
                    if (this.health < 20) {
                        int healAmount = 4; // 2 hearts
                        this.health = Math.min(20, this.health + healAmount);

                        // Consume one item
                        stack.count--;
                        if (stack.count <= 0) {
                            player.inventory.main[player.inventory.selectedSlot] = null;
                        }

                        // Spawn heart particles
                        for (int i = 0; i < 6; i++) {
                            double dx = (random.nextDouble() - 0.5) * 0.5;
                            double dy = random.nextDouble() * 0.5 + 0.5;
                            double dz = (random.nextDouble() - 0.5) * 0.5;
                            RetroParticles.spawnVanilla(world, "heart",
                                this.x + dx, this.y + 1.5 + dy, this.z + dz, 0, 0, 0);
                        }
                        player.sendMessage("NPC healed!");
                    } else {
                        player.sendMessage("This NPC is already at full health.");
                    }
                }
                return true;
            }

            // ---- Existing interactions ----
            if (stack.itemId == Item.STICK.id) {
                cycleSkin();
                return true;
            }
            if (stack.itemId == Item.FEATHER.id) {
                setStationary(!isStationary());
                if (!world.isRemote) {
                    player.sendMessage(isStationary() ? "Human now stays still." : "Human now moves freely.");
                }
                return true;
            }
            if (stack.itemId == Item.SLIMEBALL.id) {
                if (!world.isRemote) {
                    cycleSize();
                    float scale = getSizeScale();
                    player.sendMessage("Size: " + (int)(scale * 100) + "%");
                }
                return true;
            }
        }

        // ---- Fallback: say a random message ----
        if (!world.isRemote) {
            List<String> messages = LastEchoNPCMod.getMessagesForPersonality(getPersonalityIndex());
            if (!messages.isEmpty()) {
                if (messageCooldown == 0) {
                    int idx;
                    if (messages.size() == 1) {
                        idx = 0;
                    } else {
                        int attempts = 0;
                        do {
                            idx = random.nextInt(messages.size());
                            attempts++;
                        } while (idx == lastMessageIndex && attempts < 10);
                    }
                    lastMessageIndex = idx;
                    String msg = messages.get(idx);
                    player.sendMessage(msg);
                    messageCooldown = 40;
                }
            } else {
                player.sendMessage("...");
            }
        }
        return true;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean canSpawn() {
        return false;
    }

    @Override
    public NamespacedIdentifier getHandlerId() {
        return ID;
    }
}
