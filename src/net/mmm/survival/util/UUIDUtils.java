package net.mmm.survival.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import net.mmm.survival.SurvivalData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Verbesserter UUIDFetcher - angepasst auf MineMagicMania-Speicher der jemals
 * verbundenen Spieler. Wenn sich ein Spieler auf dem Server einloggt, wird die
 * UUID und der Name des Spielers gespeichert. Auf diesen Speicher wird hierbei
 * zugegriffen.
 *
 * @author Abgie on 27.09.2018 17:55
 * project SurvivalProjekt
 * @version 1.0
 * @since JDK 8
 */
public final class UUIDUtils {
  private static final ExecutorService pool = Executors.newCachedThreadPool();
  private static final Map<UUID, String> cache = SurvivalData.getInstance().getPlayerCache();

  /**
   * Bestimme die UUID, mit des Namens, mit dem sich der Spieler der gesuchten
   * UUID zuletzt verbunden hat.
   *
   * @param playerName letzer bekannter Name des Spielers
   * @return Universally Unique Identifier des Spielers
   */
  public static UUID getUUID(final String playerName) {
    final Map<UUID, String> playerCache = SurvivalData.getInstance().getPlayerCache();
    for (final UUID id : playerCache.keySet()) {
      final String nameFromUUID = playerCache.get(id);
      if (nameFromUUID.equalsIgnoreCase(playerName)) {
        return id;
      }
    }
    return null;
  }

  /**
   * Fetches the uuid asynchronously and passes it to the consumer
   *
   * @param name The name
   * @param action Do what you want to do with the uuid her
   */
  public static void getUUID(final String name, final Consumer<UUID> action) {
    pool.execute(() -> {
      final UUID uuid = getUUID(name);
      action.accept(uuid);
    });
  }

  /**
   * Bestimme den Namen, mithilfe der UUID, mit dem sich der Spieler des
   * gesuchten Namen zuletzt verbunden hat.
   *
   * @param uuid Universally Unique Identifier des Spielers
   * @return letzter bekannter Name des Spielers
   */
  private static String getName(final UUID uuid) {
    return cache.get(uuid);
  }

  /**
   * Fetches the name asynchronously and passes it to the consumer
   *
   * @param uuid The uuid
   * @param action Do what you want to do with the name her
   */
  public static void getName(final UUID uuid, final Consumer<String> action) {
    pool.execute(() -> action.accept(getName(uuid)));
  }

  /**
   * Bestimme den Spieler, mithilfe der UUID, mit dem sich der gesuchte Spieler
   * zuletzt verbunden hat.
   *
   * @param uuid Universally Unique Identifier des Spielers
   * @return org.bukkit.entity.Player des Spielers
   */
  public static Player getPlayer(final UUID uuid) {
    Player player = Bukkit.getPlayer(uuid);
    if (player == null) {
      final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
      player = offlinePlayer.getPlayer();
    }
    return player;
  }

  /**
   * Bestimme den Spieler, mithilfe des Namens, mit dem sich der gesuchte Spieler
   * zuletzt verbunden hat.
   *
   * @param playerName letzer bekannter Name des Spielers
   * @return org.bukkit.entity.Player des Spielers
   */
  public static Player getPlayer(final String playerName) {
    final UUID uuid = getUUID(playerName);
    Player player = Bukkit.getPlayer(uuid);
    if (player == null) {
      final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
      player = offlinePlayer.getPlayer();
    }
    return player;
  }

}
