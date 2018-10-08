package net.mmm.survival.commands;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.mmm.survival.SurvivalData;
import net.mmm.survival.mysql.AsyncMySQL;
import net.mmm.survival.player.SurvivalPlayer;
import net.mmm.survival.regions.DynmapWorldGuardPlugin;
import net.mmm.survival.regions.Regions;
import net.mmm.survival.util.CommandUtils;
import net.mmm.survival.util.Messages;
import net.mmm.survival.util.UUIDUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /zone Command
 */
public class Zone implements CommandExecutor {
  @Override
  public boolean onCommand(final CommandSender commandSender, final Command command, final String s, final String[] args) {
    if (CommandUtils.checkPlayer(commandSender) && CommandUtils.checkWorld((Player) commandSender)) {
      final SurvivalPlayer executor = SurvivalPlayer.findSurvivalPlayer((Player) commandSender);
      if (args.length == 1) {
        evaluateOneArgument(executor, args);
      } else if (args.length == 2) {
        evaluateTwoArguments(executor.getPlayer(), args);
      } else if (args.length == 3) {
        evaluateThreeArguments(executor.getPlayer(), args);
      } else {
        evaluateInvalidUsage(executor.getPlayer());
      }
    }
    return false;
  }

  private void evaluateOneArgument(final SurvivalPlayer executor, final String[] args) {
    if (args[0].equalsIgnoreCase("create")) {
      evaluateCreateZone(executor);
    } else if (args[0].equalsIgnoreCase("search")) {
      evaluateSearchZone(executor);
    } else if (args[0].equalsIgnoreCase("delete")) {
      evaluateDeleteZone(executor.getPlayer());
    } else if (args[0].equalsIgnoreCase("info")) {
      evaluateInfoZone(executor.getPlayer());
    } else {
      evaluateInvalidUsage(executor.getPlayer());
    }
  }

  private void evaluateTwoArguments(final Player executor, final String[] args) {
    if (args[0].equalsIgnoreCase("add")) {
      tryAddPlayerToZone(executor, args);
    } else if (args[0].equalsIgnoreCase("remove")) {
      evaluateRemovePlayerFromZone(executor, args);
    } else if (args[0].equalsIgnoreCase("info")) {
      evaluateInfoZone(executor, args);
    } else {
      evaluateInvalidUsage(executor);
    }
  }

  private void evaluateThreeArguments(final Player executor, final String[] args) {
    if (args[0].equalsIgnoreCase("setlength") && CommandUtils.isOperator(executor)) {
      evaluateUpdateLength(executor, args);
    } else {
      evaluateInvalidUsage(executor);
    }
  }

  private void evaluateDeleteZone(final Player executor) {
    final DynmapWorldGuardPlugin dynmapPlugin = SurvivalData.getInstance().getDynmap();
    final RegionManager regionManager = dynmapPlugin.getRegionManager();
    final String uuidString = executor.getUniqueId().toString();
    final ProtectedRegion deletedRegion = Regions.evaluateExistingRegion(regionManager, uuidString, false);
    if (checkDeleteZone(executor, deletedRegion)) {
      performDeleteZone(executor, deletedRegion);
    }
  }

  private void performDeleteZone(final Player executor, final ProtectedRegion deletedRegion) {
    final DynmapWorldGuardPlugin dynmapPlugin = SurvivalData.getInstance().getDynmap();
    final RegionManager regionManager = dynmapPlugin.getRegionManager();

    regionManager.removeRegion(deletedRegion.getId());
    executor.sendMessage(Messages.ZONE_REMOVED);
  }

  private void evaluateInfoZone(final Player executor) {
    final DynmapWorldGuardPlugin dynmapPlugin = SurvivalData.getInstance().getDynmap();
    final RegionManager regionManager = dynmapPlugin.getRegionManager();
    final ProtectedRegion selectedRegion = Regions.
        evaluateRegionOnCurrentLocation(regionManager, executor.getLocation());
    executor.sendMessage(performInfoAboutZone(selectedRegion));
  }

  private void tryAddPlayerToZone(final Player executor, final String[] args) {
    try {
      evaluateAddPlayerToZone(executor, args[1]);
    } catch (final Exception ex) {
      executor.sendMessage(Messages.ZONE_NOT_SET);
    }
  }

  private void evaluateAddPlayerToZone(final Player executor, final String arg) {
    final DynmapWorldGuardPlugin dynmapPlugin = SurvivalData.getInstance().getDynmap();
    final RegionManager regionManager = dynmapPlugin.getRegionManager();

    UUIDUtils.getUUID(arg, uuid ->
        UUIDUtils.getName(uuid, name -> {
          final ProtectedRegion existingRegion = Regions.
              evaluateExistingRegion(regionManager, uuid.toString(), false);
          performAddPlayerToZone(executor, arg, uuid, name, existingRegion);
        }));
  }

  private void performAddPlayerToZone(final Player executor, final String arg, final UUID uuid, final String name,
                                      final ProtectedRegion region) {
    if (region != null) {
      final DefaultDomain membersOfSelectedRegion = region.getMembers();
      if (!membersOfSelectedRegion.contains(uuid)) {
        final Player playerToAdd = UUIDUtils.getPlayer(arg);
        membersOfSelectedRegion.addPlayer(playerToAdd.getUniqueId());
        executor.sendMessage(Messages.PREFIX + "§7Du hast §e" + name + " §7zu deiner Zone hinzugefügt.");
      } else {
        executor.sendMessage(Messages.PREFIX + name + " §7ist bereits Mitglied deiner Zone.");
      }
    }
  }

  private void evaluateRemovePlayerFromZone(final Player executor, final String[] args) {
    try {
      performRemovePlayerFromZone(executor, args[1]);
    } catch (final Exception ex) {
      executor.sendMessage(Messages.ZONE_NOT_SET);
    }
  }

  private void performRemovePlayerFromZone(final Player executor, final String arg) {
    final DynmapWorldGuardPlugin dynmapPlugin = SurvivalData.getInstance().getDynmap();
    final RegionManager regionManager = dynmapPlugin.getRegionManager();

    UUIDUtils.getUUID(arg, uuid ->
        UUIDUtils.getName(uuid, name -> {
          final ProtectedRegion region = Regions.evaluateExistingRegion(regionManager, uuid.toString(), false);
          if (region != null) {
            final DefaultDomain regionMembers = region.getMembers();
            if (regionMembers.contains(uuid)) {
              regionMembers.removePlayer(uuid);
              executor.sendMessage(Messages.PREFIX + "§7Du hast §e" + name + " §7von deiner Zone entfernt.");
            } else {
              executor.sendMessage(Messages.PREFIX + name + " §7ist bereits Mitglied deiner Zone.");
            }
          }
        }));
  }

  private void evaluateInfoZone(final Player executor, final String[] args) {
    if (CommandUtils.isOperator(executor)) {
      try {
        determinePlayerInfo(executor, args[1]);
      } catch (final Exception ex) {
        executor.sendMessage(Messages.PLAYER_NOT_FOUND);
      }
    }
  }

  private void determinePlayerInfo(final Player executor, final String arg) {
    UUIDUtils.getUUID(arg, uuid -> {
      final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
      final Long time = offlinePlayer.getLastPlayed();
      final Long first = offlinePlayer.getFirstPlayed();
      final String lastonline = new SimpleDateFormat("dd.MM.yyyy").format(new Date(time));
      final String firstonline = new SimpleDateFormat("dd.MM.yyyy").format(new Date(first));

      sendPlayerInfo(executor, lastonline, firstonline);
    });
  }

  private void sendPlayerInfo(final Player executor, final String lastonline, final String firstonline) {
    executor.sendMessage("\n" + Messages.PREFIX + " §7Zuletzt online§8: §c" + lastonline);
    executor.sendMessage(Messages.PREFIX + " §7Zuerst online§8: §c" + firstonline + "\n");
  }

  private void evaluateUpdateLength(final Player executor, final String[] args) {
    try {
      tryUpdateLength(executor, args[1], Integer.valueOf(args[2]));
    } catch (final NumberFormatException ignored) {
      executor.sendMessage(Messages.NOT_A_NUMBER);
    }
  }

  private void tryUpdateLength(final Player executor, final String arg, final Integer max) {
    UUIDUtils.getUUID(arg, uuid -> {
      final Map<UUID, SurvivalPlayer> playerCache = SurvivalData.getInstance().getPlayers();
      final SurvivalPlayer ownerOfTargetZone = playerCache.get(uuid);

      if (checkPlayerFound(executor, ownerOfTargetZone)) {
        performUpdateLength(executor, max, uuid, ownerOfTargetZone);
      }

    });
  }

  private void performUpdateLength(final Player executor, final Integer max, final UUID uuid, final SurvivalPlayer ownerOfTargetZone) {
    ownerOfTargetZone.setMaxzone(max);
    UUIDUtils.getName(uuid, name ->
        executor.sendMessage(Messages.PREFIX + " §e" + name + " §7kann nun eine Zone mit der Länge §c" +
            max + " §7erstellen."));
  }

  private boolean checkPlayerFound(final Player executor, final SurvivalPlayer targetZoneOwner) {
    if (targetZoneOwner != null) {
      return true;
    } else {
      executor.sendMessage(Messages.PLAYER_NOT_FOUND);
    }

    return false;
  }

  private void evaluateInvalidUsage(final Player executor) {
    executor.sendMessage(executor.isOp() ? Messages.ZONE_HELP_ADMIN : Messages.ZONE_HELP);
  }

  private void evaluateCreateZone(final SurvivalPlayer creator) {
    final DynmapWorldGuardPlugin dynmapPlugin = SurvivalData.getInstance().getDynmap();
    final RegionManager regionManager = dynmapPlugin.getRegionManager();
    if (Regions.evaluateExistingRegion(regionManager, creator.getUuid().toString(), false) != null) {
      final Player creatorPlayer = creator.getPlayer();
      creatorPlayer.sendMessage(Messages.ZONE_ALREADY_EXIST);
    } else {
      allowCreateZone(creator);
    }
  }

  private void allowCreateZone(final SurvivalPlayer creator) {
    if (!creator.isZonenedit()) {
      final Player creatorPlayer = creator.getPlayer();
      creatorPlayer.sendMessage(Messages.ZONE_EXPLAINATION);
    }
    creator.setZonenedit(true);
  }

  private void evaluateSearchZone(final SurvivalPlayer finder) {
    final Player finderPlayer = finder.getPlayer();
    if (finder.isZonensearch()) {
      finderPlayer.sendMessage(Messages.ZONE_SEARCH_DISABLE);
    } else {
      finderPlayer.sendMessage(Messages.ZONE_SEARCH_ENABLE);
    }
    finder.setZonensearch(!finder.isZonensearch());
  }

  private boolean checkDeleteZone(final Player deleter, final ProtectedRegion region) {
    if (region != null) {
      return true;
    } else {
      deleter.sendMessage(Messages.ZONE_NOT_SET);
    }

    return false;
  }

  private String performInfoAboutZone(final ProtectedRegion region) {
    return region != null ? sendZoneInfo(region) : Messages.ZONE_NOT_FOUND;
  }

  private String sendZoneInfo(final ProtectedRegion region) {
    final String[] message = new String[1];
    UUIDUtils.getName(UUID.fromString(region.getId()), name ->
        message[0] = Messages.PREFIX + " §7Besitzer§8: " + name + "\n" + Messages.PREFIX +
            " §7Mitglieder§8: " + getZoneInfo(region) + "\n");

    return message[0];
  }

  private StringBuilder getZoneInfo(final ProtectedRegion region) {
    final AsyncMySQL mySQL = SurvivalData.getInstance().getAsyncMySQL();
    final DefaultDomain regionOwners = region.getOwners();
    final Set<UUID> uuidSet = regionOwners.getUniqueIds();
    final Iterator<UUID> uuidIterator = uuidSet.iterator();

    final StringBuilder member = new StringBuilder(mySQL.getName(uuidIterator.next()));
    uuidSet.forEach(uuid -> member.append(", ").append(mySQL.getName(uuid)));
    return member;
  }
}