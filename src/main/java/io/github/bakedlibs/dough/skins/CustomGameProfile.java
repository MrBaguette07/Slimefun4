package io.github.bakedlibs.dough.skins;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import io.github.bakedlibs.dough.reflection.ReflectionUtils;
import io.github.bakedlibs.dough.versions.MinecraftVersion;
import io.github.bakedlibs.dough.versions.UnknownServerVersionException;

/**
 * Override local qui reflète la classe en amont mais évite d'étendre {@link GameProfile},
 * qui est final sur 1.21.10. Nous encapsulons un vrai {@link GameProfile} en interne.
 */
public final class CustomGameProfile {

    /**
     * Le nom du joueur pour ce profil.
     * "CS-CoreLib" pour des raisons historiques et de rétrocompatibilité.
     */
    private static final String PLAYER_NAME = "CS-CoreLib";

    /**
     * La clé de propriété du skin.
     */
    private static final String PROPERTY_KEY = "textures";

    private final URL skinUrl;
    private final String texture;
    private final GameProfile handle;

    private static final Method PROPERTY_ACCESSOR = resolvePropertyAccessor();
    private static final Method ID_ACCESSOR = resolveIdAccessor();
    private static final Constructor<GameProfile> MODERN_CONSTRUCTOR = resolveModernConstructor();
    private static final Constructor<PropertyMap> PROPERTY_MAP_CONSTRUCTOR = resolvePropertyMapConstructor();
    private static final boolean PROPERTY_MAP_REQUIRES_MULTIMAP = PROPERTY_MAP_CONSTRUCTOR != null && PROPERTY_MAP_CONSTRUCTOR.getParameterCount() == 1;

    CustomGameProfile(@Nonnull UUID uuid, @Nullable String texture, @Nonnull URL url) {
        this.handle = createProfile(uuid, texture);
        this.skinUrl = url;
        this.texture = texture;
    }

    void apply(@Nonnull SkullMeta meta) throws NoSuchFieldException, IllegalAccessException, UnknownServerVersionException {
        if (MinecraftVersion.get().isAtLeast(MinecraftVersion.parse("1.20"))) {
            PlayerProfile playerProfile = Bukkit.createPlayerProfile(resolveId(this.handle), PLAYER_NAME);
            PlayerTextures playerTextures = playerProfile.getTextures();
            playerTextures.setSkin(this.skinUrl);
            playerProfile.setTextures(playerTextures);
            meta.setOwnerProfile(playerProfile);
        } else {
            ReflectionUtils.setFieldValue(meta, "profile", this.handle);
            meta.setOwningPlayer(meta.getOwningPlayer());
            ReflectionUtils.setFieldValue(meta, "profile", this.handle);
        }
    }

    /**
     * Récupère la texture encodée en base64 depuis le GameProfile.
     *
     * @return la texture encodée en base64.
     */
    @Nullable
    public String getBase64Texture() {
        return this.texture;
    }

    @Nonnull
    GameProfile asGameProfile() {
        return this.handle;
    }

    private static PropertyMap propertyMap(@Nonnull GameProfile profile) {
        try {
            return (PropertyMap) PROPERTY_ACCESSOR.invoke(profile);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to access GameProfile properties", e);
        }
    }

    private static Method resolvePropertyAccessor() {
        for (String accessor : new String[] { "getProperties", "properties" }) {
            try {
                Method method = GameProfile.class.getMethod(accessor);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new IllegalStateException("Could not find GameProfile#getProperties() or GameProfile#properties()");
    }

    private static Method resolveIdAccessor() {
        for (String accessor : new String[] { "getId", "id" }) {
            try {
                Method method = GameProfile.class.getMethod(accessor);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new IllegalStateException("Could not find GameProfile#getId() or GameProfile#id()");
    }

    private static UUID resolveId(GameProfile profile) {
        try {
            return (UUID) ID_ACCESSOR.invoke(profile);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to access GameProfile id", e);
        }
    }

    private static GameProfile createProfile(@Nonnull UUID uuid, @Nullable String texture) {
        if (MODERN_CONSTRUCTOR != null) {
            PropertyMap properties = newPropertyMap(texture);
            try {
                return MODERN_CONSTRUCTOR.newInstance(uuid, PLAYER_NAME, properties);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Failed to instantiate GameProfile with custom PropertyMap", e);
            }
        }

        GameProfile profile = new GameProfile(uuid, PLAYER_NAME);
        if (texture != null) {
            try {
                propertyMap(profile).put(PROPERTY_KEY, new Property(PROPERTY_KEY, texture));
            } catch (UnsupportedOperationException ignored) {}
        }
        return profile;
    }

    private static PropertyMap newPropertyMap(@Nullable String texture) {
        if (PROPERTY_MAP_CONSTRUCTOR == null) {
            throw new IllegalStateException("Could not resolve PropertyMap constructor");
        }

        try {
            if (PROPERTY_MAP_REQUIRES_MULTIMAP) {
                var multimap = ArrayListMultimap.<String, Property>create();
                if (texture != null) {
                    multimap.put(PROPERTY_KEY, new Property(PROPERTY_KEY, texture));
                }
                return PROPERTY_MAP_CONSTRUCTOR.newInstance(multimap);
            }
            return PROPERTY_MAP_CONSTRUCTOR.newInstance();
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to instantiate PropertyMap", e);
        }
    }

    private static Constructor<GameProfile> resolveModernConstructor() {
        try {
            Constructor<GameProfile> constructor = GameProfile.class.getConstructor(UUID.class, String.class, PropertyMap.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Constructor<PropertyMap> resolvePropertyMapConstructor() {
        try {
            Constructor<PropertyMap> constructor = PropertyMap.class.getConstructor(com.google.common.collect.Multimap.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException ignored) {
            try {
                Constructor<PropertyMap> legacyConstructor = PropertyMap.class.getConstructor();
                legacyConstructor.setAccessible(true);
                return legacyConstructor;
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    }
}
