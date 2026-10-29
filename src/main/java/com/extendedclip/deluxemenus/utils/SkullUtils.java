package com.extendedclip.deluxemenus.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.extendedclip.deluxemenus.DeluxeMenus;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SkullUtils {

    private static final String TEXTURES_PROPERTY = "textures";
    private static final String TEXTURE_URL_PREFIX = "https://textures.minecraft.net/texture/";

    private static final Gson GSON = new Gson();

    /**
     * Upper bound for {@link #TEXTURE_CACHE}. Each entry is a couple of short strings, so this stays small.
     */
    private static final int TEXTURE_CACHE_SIZE = 2048;

    /**
     * Known head textures keyed by {@code uuid:lowercase-name}. Only the textures property is kept, never a
     * {@link Player} or {@link PlayerProfile}, so nothing here keeps a player object alive. Access-ordered,
     * so the least recently used entry is dropped once the cache is full.
     */
    private static final Map<String, ProfileProperty> TEXTURE_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, ProfileProperty> eldest) {
                    return size() > TEXTURE_CACHE_SIZE;
                }
            }
    );

    /**
     * Helper method to get the encoded bytes for a full MC Texture
     *
     * @param url the url of the texture
     * @return fully encoded texture url
     */
    @NotNull
    public static String getEncoded(@NotNull final String url) {
        final byte[] encodedData = Base64.getEncoder().encode(String
                .format("{%s:{SKIN:{url:\"%s\"}}}", TEXTURES_PROPERTY, TEXTURE_URL_PREFIX + url)
                .getBytes());
        return new String(encodedData);
    }

    /**
     * Get the skull from a base64 encoded texture url
     *
     * @param base64Url base64 encoded url to use
     * @return skull
     */
    @NotNull
    public static ItemStack getSkullByBase64EncodedTextureUrl(@NotNull final DeluxeMenus plugin, @NotNull final String base64Url) {
        final ItemStack head = plugin.getHead().clone();
        if (base64Url.isEmpty()) {
            return head;
        }

        final SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta == null) {
            return head;
        }

        final PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty(TEXTURES_PROPERTY, base64Url));
        headMeta.setPlayerProfile(profile);

        head.setItemMeta(headMeta);
        return head;
    }

    /**
     * Get the texture id of a skull, i.e. the trailing path segment of its skin url.
     *
     * @return the texture id, or {@code null} if the item is not a skull or carries no texture
     */
    public static @Nullable String getTextureFromSkull(@NotNull final ItemStack item) {
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return null;

        final PlayerProfile profile = meta.getPlayerProfile();
        if (profile == null) return null;

        for (final ProfileProperty property : profile.getProperties()) {
            if (TEXTURES_PROPERTY.equals(property.getName())) {
                return getTextureIdFromBase64(property.getValue());
            }
        }

        return null;
    }

    /**
     * Get the skull from a player name
     * <p>
     * The profile is taken from the online player when possible (it already carries the textures property),
     * otherwise from the texture cache. A profile without textures is only handed to Paper when Mojang can
     * actually resolve it (a version 4 UUID). For anything else, such as offline-mode (version 3) UUIDs, a
     * plain head is returned, because Paper would otherwise query Mojang on every {@code setItemMeta} call.
     *
     * @param playerName the player name to use
     * @return skull
     */
    @NotNull
    public static ItemStack getSkullByName(@NotNull final DeluxeMenus plugin, @NotNull final String playerName) {
        final ItemStack head = plugin.getHead().clone();
        if (playerName.isEmpty()) {
            return head;
        }

        final SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta == null) {
            return head;
        }

        PlayerProfile profile = resolveProfile(null, playerName);
        if (profile == null) {
            profile = lookupResolvableProfile(playerName);
        }

        if (profile == null) {
            return head;
        }

        headMeta.setPlayerProfile(profile);
        head.setItemMeta(headMeta);
        return head;
    }

    /**
     * Make sure a skull meta does not carry a profile that would make Paper go to Mojang for nothing. A profile
     * without textures gets the textures of the online player or from the cache. If none are known and Mojang
     * cannot resolve the profile either, the profile is removed so the item becomes a plain head.
     *
     * @param meta the skull meta to check
     * @return {@code true} if the meta was changed and has to be applied to the item again
     */
    public static boolean sanitizeProfile(@NotNull final SkullMeta meta) {
        final PlayerProfile profile = meta.getPlayerProfile();
        if (profile == null || profile.hasTextures()) {
            return false;
        }

        final PlayerProfile resolved = resolveProfile(profile.getId(), profile.getName());
        if (resolved != null) {
            meta.setPlayerProfile(resolved);
            return true;
        }

        if (isResolvableByMojang(profile)) {
            return false;
        }

        meta.setPlayerProfile(null);
        return true;
    }

    /**
     * Remember the textures of a profile so heads of this player keep their skin while the player is offline.
     *
     * @param profile the profile to take the textures from
     */
    public static void cacheTextures(@Nullable final PlayerProfile profile) {
        if (profile == null || profile.getId() == null || profile.getName() == null) {
            return;
        }

        final ProfileProperty textures = getTexturesProperty(profile);
        if (textures != null) {
            TEXTURE_CACHE.put(textureCacheKey(profile.getId(), profile.getName()), textures);
        }
    }

    public static void clearTextureCache() {
        TEXTURE_CACHE.clear();
    }

    /**
     * Find a profile with textures without any network request: the online player's own profile first, then the
     * texture cache.
     *
     * @return a profile carrying textures, or {@code null} if no textures are known locally
     */
    private static @Nullable PlayerProfile resolveProfile(@Nullable UUID id, @Nullable String name) {
        final Player player = id != null ? Bukkit.getPlayer(id) : (name != null ? Bukkit.getPlayerExact(name) : null);
        if (player != null) {
            final PlayerProfile playerProfile = player.getPlayerProfile();
            if (playerProfile.hasTextures()) {
                cacheTextures(playerProfile);
                return playerProfile;
            }

            id = player.getUniqueId();
            name = player.getName();
        }

        if (id == null && name != null) {
            final OfflinePlayer cachedPlayer = Bukkit.getOfflinePlayerIfCached(name);
            if (cachedPlayer != null) {
                id = cachedPlayer.getUniqueId();
                if (cachedPlayer.getName() != null) {
                    name = cachedPlayer.getName();
                }
            }
        }

        if (id == null || name == null) {
            return null;
        }

        final ProfileProperty textures = TEXTURE_CACHE.get(textureCacheKey(id, name));
        if (textures == null) {
            return null;
        }

        final PlayerProfile profile = Bukkit.createProfile(id, name);
        profile.setProperty(textures);
        return profile;
    }

    /**
     * The old lookup path, limited to profiles Mojang can resolve (version 4 UUIDs). On an offline-mode server
     * this only runs for players the server already knows under an online-mode UUID, so unknown names and
     * offline-mode UUIDs never cause a request.
     */
    private static @Nullable PlayerProfile lookupResolvableProfile(@NotNull final String playerName) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(playerName);
        if (offlinePlayer == null) {
            if (!Bukkit.getOnlineMode()) {
                return null;
            }
            offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        }

        PlayerProfile profile = offlinePlayer.getPlayerProfile();
        if (!profile.hasTextures()) {
            if (!isResolvableByMojang(profile)) {
                return null;
            }

            final PlayerProfile cached = resolveProfile(profile.getId(), profile.getName());
            if (cached != null) {
                return cached;
            }

            // updates the Player Profile and populates textures for offline players - for some reason this doesn't populate when getting the Profile first time
            profile = profile.update().join();
        }

        if (!profile.hasTextures()) {
            return null;
        }

        cacheTextures(profile);
        return profile;
    }

    /**
     * Mojang only knows version 4 (online-mode) UUIDs. A name-only profile is only worth a lookup on an
     * online-mode server.
     */
    private static boolean isResolvableByMojang(@NotNull final PlayerProfile profile) {
        final UUID id = profile.getId();
        if (id != null) {
            return id.version() == 4;
        }

        return profile.getName() != null && Bukkit.getOnlineMode();
    }

    private static @Nullable ProfileProperty getTexturesProperty(@NotNull final PlayerProfile profile) {
        for (final ProfileProperty property : profile.getProperties()) {
            if (TEXTURES_PROPERTY.equals(property.getName())) {
                return property;
            }
        }

        return null;
    }

    private static @NotNull String textureCacheKey(@NotNull final UUID id, @NotNull final String name) {
        return id + ":" + name.toLowerCase(Locale.ROOT);
    }

    public static String getSkullOwner(ItemStack skull) {
        if (skull == null || !(skull.getItemMeta() instanceof SkullMeta)) return null;
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta.getOwningPlayer() == null) return null;
        return meta.getOwningPlayer().getName();
    }

    /**
     * Extract the texture id from a base64 encoded texture blob.
     *
     * @return the texture id, or {@code null} if the blob carries no skin url
     */
    public static @Nullable String getTextureIdFromBase64(@NotNull final String base64Texture) {
        final String url = decodeSkinUrl(base64Texture);
        if (url == null) {
            return null;
        }

        return url.substring(url.lastIndexOf('/') + 1);
    }

    /**
     * Decode a base64 string and extract the url of the skin. Example:
     * <br>
     * - Base64: {@code eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGNlYjE3MDhkNTQwNGVmMzI2MTAzZTdiNjA1NTljOTE3OGYzZGNlNzI5MDA3YWM5YTBiNDk4YmRlYmU0NjEwNyJ9fX0=}
     * <br>
     * - JSON: {@code {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/dceb1708d5404ef326103e7b60559c9178f3dce729007ac9a0b498bdebe46107"}}}}
     * <br>
     * - Result: {@code http://textures.minecraft.net/texture/dceb1708d5404ef326103e7b60559c9178f3dce729007ac9a0b498bdebe46107}
     * <br>
     * Credit: <a href="https://github.com/TriumphTeam/triumph-gui/pull/104/files#diff-ef6f3ffdac8e5f722e2e9121be8003b26d087c2d7871ca43d31b65c7565b0c1fR92">iGabyTM</a>
     *
     * @param base64Texture the texture
     * @return the url of the texture if found, otherwise {@code null}
     */
    @Nullable
    public static String decodeSkinUrl(@NotNull final String base64Texture) {
        final String decoded = new String(Base64.getDecoder().decode(base64Texture));
        final JsonObject object = GSON.fromJson(decoded, JsonObject.class);

        final JsonElement textures = object.get("textures");

        if (textures == null) {
            return null;
        }

        final JsonElement skin = textures.getAsJsonObject().get("SKIN");

        if (skin == null) {
            return null;
        }

        final JsonElement url = skin.getAsJsonObject().get("url");
        return url == null ? null : url.getAsString();
    }
}
