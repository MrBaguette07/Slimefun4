package io.github.bakedlibs.dough.skins;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.apache.commons.lang.Validate;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import com.mojang.authlib.GameProfile;

import io.github.bakedlibs.dough.skins.nms.PlayerHeadAdapter;
import io.github.bakedlibs.dough.versions.UnknownServerVersionException;

/**
 * Override local qui coopère avec le {@link CustomGameProfile} mis à jour.
 */
public final class PlayerHead {
    private static final PlayerHeadAdapter adapter = resolveAdapter();

    private PlayerHead() {}

    /**
     * Cette méthode retourne simplement la tête du joueur spécifié
     *
     * @param player
     *            Le propriétaire de votre tête
     *
     * @return Un nouvel item de tête pour le joueur spécifié
     */
    public static @Nonnull ItemStack getItemStack(@Nonnull OfflinePlayer player) {
        Validate.notNull(player, "The player can not be null!");
        return getItemStack(meta -> meta.setOwningPlayer(player));
    }

    /**
     * Cette méthode retourne simplement la tête du joueur spécifié
     *
     * @param skin
     *            Le skin de la tête que vous souhaitez.
     *
     * @return Un nouvel item de tête pour le joueur spécifié
     */
    public static @Nonnull ItemStack getItemStack(@Nonnull PlayerSkin skin) {
        Validate.notNull(skin, "The skin can not be null!");
        return getItemStack(meta -> {
            try {
                skin.getProfile().apply(meta);
            } catch (NoSuchFieldException | IllegalAccessException | UnknownServerVersionException e) {
                e.printStackTrace();
            }
        });
    }

    private static @Nonnull ItemStack getItemStack(@Nonnull Consumer<SkullMeta> consumer) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        consumer.accept(meta);
        item.setItemMeta(meta);
        return item;
    }

    @ParametersAreNonnullByDefault
    public static void setSkin(Block block, PlayerSkin skin, boolean sendBlockUpdate) {
        if (adapter == null) {
            throw new UnsupportedOperationException("Cannot update skin texture, no adapter found");
        }

        Material material = block.getType();
        if (material != Material.PLAYER_HEAD && material != Material.PLAYER_WALL_HEAD) {
            throw new IllegalArgumentException("Cannot update a head texture. Expected a Player Head, received: " + material);
        }

        try {
            GameProfile profile = skin.getProfile().asGameProfile();
            adapter.setGameProfile(block, profile, sendBlockUpdate);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private static PlayerHeadAdapter resolveAdapter() {
        return null;
    }
}
