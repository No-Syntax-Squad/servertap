package io.servertap.api.v1;

import de.tr7zw.nbtapi.NBTFile;
import io.javalin.http.*;
import io.javalin.openapi.*;
import io.servertap.Constants;
import io.servertap.api.v1.models.ItemStack;
import io.servertap.api.v1.models.Player;
import io.servertap.utils.pluginwrappers.EconomyWrapper;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.*;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static io.servertap.Constants.RANK_NOT_FOUND;

public class PlayerApi {
    private final EconomyWrapper economy;
    private final Logger log;

    public PlayerApi(Logger log, EconomyWrapper economy) {
        this.economy = economy;
        this.log = log;
    }

    @OpenApi(
            path = "/v1/players",
            summary = "Gets all currently online players",
            tags = {"Player"},
            headers = {
                    @OpenApiParam(name = "key")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = Player.class))
            }
    )
    public void playersGet(Context ctx) {
        ArrayList<Player> players = new ArrayList<>();

        Bukkit.getOnlinePlayers().forEach(player -> players.add(getPlayer(player)));

        ctx.json(players);
    }

    @OpenApi(
            path = "/v1/players/{uuid}",
            methods = {HttpMethod.GET},
            summary = "Gets a specific online player by their UUID",
            tags = {"Player"},
            headers = {
                    @OpenApiParam(name = "key")
            },
            pathParams = {
                    @OpenApiParam(name = "uuid", description = "UUID of the player")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = Player.class))
            }
    )
    public void playerGet(Context ctx) {
        String uuid = ctx.pathParam("uuid");

        if (uuid.isEmpty()) throw new BadRequestResponse(Constants.PLAYER_UUID_MISSING);

        UUID playerUUID = ValidationUtils.safeUUID(uuid);
        if (playerUUID == null) {
            throw new BadRequestResponse(Constants.INVALID_UUID);
        }

        org.bukkit.entity.Player player = Bukkit.getPlayer(playerUUID);

        if (player == null) throw new NotFoundResponse(Constants.PLAYER_NOT_FOUND);

        ctx.json(getPlayer(player));
    }

    /**
     * Internal method to convert a Bukkit player into a ServerTap player
     *
     * @param player The Bukkit player
     * @return The ServerTap player
     */
    private Player getPlayer(org.bukkit.entity.Player player) {
        Player p = new Player();
        p.setUuid(player.getUniqueId().toString());
        p.setDisplayName(player.getDisplayName());

        p.setAddress(player.getAddress().getHostName());
        p.setPort(player.getAddress().getPort());

        p.setExhaustion(player.getExhaustion());
        p.setExp(player.getExp());
        p.setExpLevel(player.getLevel());

        p.setWhitelisted(player.isWhitelisted());
        p.setBanned(player.isBanned());
        p.setOp(player.isOp());

        if (economy.isAvailable()) {
            p.setBalance(economy.getPlayerBalance(player));
        }

        p.setHunger(player.getFoodLevel());
        p.setHealth(player.getHealth());
        p.setSaturation(player.getSaturation());

        p.setDimension(player.getWorld().getEnvironment());

        Location playerLocation = player.getLocation();
        Double[] convertedLocation = new Double[3];
        convertedLocation[0] = playerLocation.getX();
        convertedLocation[1] = playerLocation.getY();
        convertedLocation[2] = playerLocation.getZ();

        p.setLocation(convertedLocation);

        p.setGamemode(player.getGameMode());

        p.setLastPlayed(player.getLastPlayed());

        return p;
    }

    @OpenApi(
            path = "/v1/players/all",
            summary = "Gets all players that have ever joined the server ",
            tags = {"Player"},
            headers = {
                    @OpenApiParam(name = "key")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = io.servertap.api.v1.models.OfflinePlayer.class))
            }
    )
    public void offlinePlayersGet(Context ctx) {

        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        ArrayList<io.servertap.api.v1.models.OfflinePlayer> players = new ArrayList<>();
        PlayerPointsAPI playerPointsAPI = PlayerPoints.getInstance().getAPI();

        OfflinePlayer[] offlinePlayers = Bukkit.getOfflinePlayers();

        for (OfflinePlayer offlinePlayer : offlinePlayers) {
            io.servertap.api.v1.models.OfflinePlayer p = new io.servertap.api.v1.models.OfflinePlayer();

            p.setDisplayName(offlinePlayer.getName());
            p.setUuid(offlinePlayer.getUniqueId().toString());
            p.setWhitelisted(offlinePlayer.isWhitelisted());
            p.setBanned(offlinePlayer.isBanned());
            p.setOp(offlinePlayer.isOp());

            if (provider != null) {
                LuckPerms luckPermsAPI = provider.getProvider();
                @NonNull CompletableFuture<User> user = luckPermsAPI.getUserManager().loadUser(offlinePlayer.getUniqueId());
                try {
                    Collection<Node> nodes = user.get().getNodes();
                    nodes.forEach(node -> {
                        if (node.getKey().equals("group.default")) p.setRank("Default");
                        if (node.getKey().equals("group.booster")) p.setRank("Booster");
                        if (node.getKey().equals("group.noble")) p.setRank("Noble");
                        if (node.getKey().equals("group.knightly")) p.setRank("Knightly");
                        if (node.getKey().equals("group.imperial")) p.setRank("Imperial");
                        if (node.getKey().equals("group.royal")) p.setRank("Royal");
                        if (node.getKey().equals("group.media")) p.setRank("Media");
                        if (node.getKey().equals("group.trainee")) p.setRank("Trainee");
                        if (node.getKey().equals("group.mod")) p.setRank("Mod");
                        if (node.getKey().equals("group.admin")) p.setRank("Admin");
                        if (node.getKey().equals("group.manager")) p.setRank("Manager");
                        if (node.getKey().equals("group.developer")) p.setRank("Developer");
                        if (node.getKey().equals("group.owner")) p.setRank("Owner");
                    });
                } catch (InterruptedException | ExecutionException e) {
                    System.err.println("[RankAPI] - " + RANK_NOT_FOUND + "/n");
                    System.err.println(e.getMessage());
                    System.err.println(Arrays.toString(e.getStackTrace()));
                }
            }

            if (playerPointsAPI != null) {
                p.setPoints(playerPointsAPI.look(offlinePlayer.getUniqueId()));
            }
            if (economy.isAvailable()) {
                p.setBalance(economy.getPlayerBalance(offlinePlayer));
            }

            p.setLastPlayed(offlinePlayer.getLastPlayed());

            players.add(p);
        }

        ctx.json(players);
    }

    @OpenApi(
            path = "/v1/players/{playerUuid}/{worldUuid}/inventory",
            methods = {HttpMethod.GET},
            summary = "Gets a specific online player's Inventory in the specified world",
            tags = {"Player"},
            headers = {
                    @OpenApiParam(name = "key")
            },
            pathParams = {
                    @OpenApiParam(name = "playerUuid", description = "UUID of the player"),
                    @OpenApiParam(name = "worldUuid", description = "UUID of the world")
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = io.servertap.api.v1.models.ItemStack.class))
            }
    )
    public void getPlayerInv(Context ctx) {
        String playerUUIDStr = ctx.pathParam("playerUuid");
        String worldUUIDStr = ctx.pathParam("worldUuid");
        if (playerUUIDStr.isEmpty() || worldUUIDStr.isEmpty()) {
            throw new BadRequestResponse(Constants.PLAYER_MISSING_PARAMS);
        }

        UUID playerUUID = ValidationUtils.safeUUID(playerUUIDStr);
        if (playerUUID == null) {
            throw new BadRequestResponse(Constants.INVALID_UUID);
        }
        UUID worldUUID = ValidationUtils.safeUUID(worldUUIDStr);
        if (worldUUID == null) {
            throw new BadRequestResponse(Constants.INVALID_UUID);
        }

        ArrayList<ItemStack> inv = new ArrayList<>();
        org.bukkit.entity.Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            player.updateInventory();
            Integer location = -1;
            for (org.bukkit.inventory.ItemStack itemStack : player.getInventory().getContents()) {
                location++;
                if (itemStack != null) {
                    ItemStack itemObj = new ItemStack();
                    // TODO: handle item namespaces other than minecraft: It's fine right now as there seem to be no forge + Paper servers
                    itemObj.setId("minecraft:" + itemStack.getType().toString().toLowerCase());
                    itemObj.setCount(itemStack.getAmount());
                    itemObj.setSlot(location);
                    inv.add(itemObj);
                }
            }
            ctx.json(inv);
        } else {
            try {
                World bukWorld = Bukkit.getWorld(worldUUID);

                if (bukWorld == null) {
                    throw new NotFoundResponse(Constants.WORLD_NOT_FOUND);
                }

                String dataPath = String.format(
                        "%s/%s/playerdata/%s.dat",
                        new File("./").getAbsolutePath(),
                        bukWorld.getName(),
                        ctx.pathParam("playerUuid")
                );
                File playerfile = new File(Paths.get(dataPath).toString());
                if (!playerfile.exists()) {
                    throw new NotFoundResponse(Constants.PLAYER_NOT_FOUND);
                }
                NBTFile playerFile = new NBTFile(playerfile);

                playerFile.getCompoundList("Inventory").forEach(item -> {
                    ItemStack itemObj = new ItemStack();
                    itemObj.setId(item.getString("id"));
                    itemObj.setCount(item.getInteger("Count"));
                    itemObj.setSlot(item.getInteger("Slot"));
                    inv.add(itemObj);
                });

                ctx.json(inv);

            } catch (HttpResponseException e) {
                // Pass any javalin exceptions up the chain
                throw e;
            } catch (Exception e) {
                log.warning(e.getMessage());
                throw new InternalServerErrorResponse(Constants.PLAYER_INV_PARSE_FAIL);
            }
        }

    }
}
