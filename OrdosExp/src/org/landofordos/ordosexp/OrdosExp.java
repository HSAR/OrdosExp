package org.landofordos.ordosexp;

import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.economy.Economy;

public class OrdosExp extends JavaPlugin implements Listener {

	// Important plugin objects
	private static Server server;
	private static Logger logger;
	public static Economy econ = null;
	//
	private boolean verbose;

	public void onDisable() {
		logger.info("Disabled.");
	}

	public void onEnable() {
		// static reference to this plugin and the server
		// plugin = this;
		server = getServer();
		// start the logger
		logger = getLogger();
		// save config to default location if not already there
		this.saveDefaultConfig();
		// verbose logging? retrieve value from config file.
		verbose = this.getConfig().getBoolean("verboselogging");
		if (verbose) {
			logger.info("Verbose logging enabled.");
		} else {
			logger.info("Verbose logging disabled.");
		}
		// register events
		server.getPluginManager().registerEvents(this, this);
		// first-run initialisation
		final boolean firstrun = this.getConfig().getBoolean("firstrun");
		if (firstrun) {
			// Whatever first run initialisation is required
			this.getConfig().set("firstrun", false);
			this.saveConfig();
			if (verbose) {
				logger.info("First-run initialisation complete.");
			}
		}
		if (!setupEconomy()) {
			logger.info("Vault dependency unsatisfied (Vault could not be found)!");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
	}

	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		econ = rsp.getProvider();
		return econ != null;
	}

	private boolean sellXP(Player player, int expGained, double moneyLost) {
		// this SELLS XP TO THE PLAYER
		try {
			if (econ.getBalance(player.getName()) > moneyLost) {
				econ.withdrawPlayer(player.getName(), moneyLost);
				player.giveExp(expGained);
				player.sendMessage(ChatColor.DARK_GREEN + "You purchased " + ChatColor.WHITE + expGained + " XP" + ChatColor.DARK_GREEN + " for "
						+ ChatColor.WHITE + moneyLost + " " + econ.currencyNamePlural());
				logger.info("Player " + player.getName() + " purchased " + expGained + " XP for " + moneyLost + " " + econ.currencyNamePlural());
			} else {
				if (verbose) {
					logger.info("Player " + player.getName() + " tried to purchase " + expGained + " for " + moneyLost + " "
							+ econ.currencyNamePlural() + "but did not have enough credit.");
				}
				player.sendMessage(ChatColor.RED + "You don't have enough money to do that.");
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private boolean buyXP(Player player, double moneyGained, int expLost) {
		// this BUYS XP FROM THE PLAYER
		try {
			if (player.getTotalExperience() >= expLost) {
				player.giveExp(expLost * -1);
				econ.depositPlayer(player.getName(), moneyGained);
				player.sendMessage(ChatColor.DARK_GREEN + "You sold " + ChatColor.WHITE + expLost + " XP" + ChatColor.DARK_GREEN + " for "
						+ ChatColor.WHITE + moneyGained + " " + econ.currencyNamePlural());
				logger.info("Player " + player.getName() + " sold " + expLost + " XP for " + moneyGained + " " + econ.currencyNamePlural());
			} else {
				if (verbose) {
					logger.info("Player " + player.getName() + " tried to sell " + expLost + " XP for " + moneyGained + " "
							+ econ.currencyNamePlural() + "but did not have enough experience points to sell.");
				}
				player.sendMessage(ChatColor.RED + "You don't have enough experience points to do that.");
			}
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		return false;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	// EventPriority.NORMAL by default
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			Player player = event.getPlayer();
			Block block = event.getClickedBlock();
			if ((block.getType() == Material.WALL_SIGN) || (block.getType() == Material.SIGN_POST)) {
				Sign sign = (Sign) block.getState();
				// now that we have the sign, pick the first line and check if it's an OrdosExp sign.
				String line0 = sign.getLine(0);
				if (line0.equals(ChatColor.RED + "[OrdosExp]")) {
					// if it is, check the second and third lines for appropriate data required
					String line1 = sign.getLine(1);
					String line2 = sign.getLine(2);
					if ((line1.startsWith("Buy ")) && (line2.startsWith("For "))) {
						double buyamount;
						double sellamount;
						// use regex to strip out all non-numeric characters from both sttrings
						try {
							buyamount = Double.parseDouble(line1.replaceAll("[^\\d.]", ""));
							sellamount = Double.parseDouble(line2.replaceAll("[^\\d.]", ""));
						} catch (NumberFormatException e) {
							player.sendMessage(ChatColor.RED + "This sign has not been set up correctly. Contact an admin for assistance.");
							return;
						}
						// is this sign selling XP, or buying it?
						if (line1.endsWith("XP")) {
							sellXP(player, (int) buyamount, sellamount);
						}
						if (line2.endsWith("XP")) {
							buyXP(player, buyamount, (int) sellamount);
						}
					}
				}
			} else {
				// ignore event
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	// EventPriority.NORMAL by default
	public void onSignChange(SignChangeEvent event) {
		Player player = event.getPlayer();
		// first of all, check the bottom line for five dashes that signify clearly it's an OrdosExp sign.
		if (event.getLine(3).equalsIgnoreCase("-----")) {
			// then check it has the correct buy and sell strings
			if ((event.getLine(1).startsWith("Buy ")) && (event.getLine(2).startsWith("For "))) {
				// and check that ONLY ONE of those lines has "XP" on it, using XOR
				if ((event.getLine(1).endsWith("XP")) ^ (event.getLine(2).endsWith("XP"))) {
					// finally, check the numbers themselves for errors - parse them and catch exceptions.
					try {
						Double.parseDouble(event.getLine(1).replaceAll("[^\\d.]", ""));
						Double.parseDouble(event.getLine(2).replaceAll("[^\\d.]", ""));
					} catch (NumberFormatException e) {
						player.sendMessage(ChatColor.RED + "This sign has not been set up correctly.");
						return;
					}
					// check the user has appropriate permissions to place a sign like this
					if (player.hasPermission("ordosexp.place")) {
						// if all is well, place [OrdosExp] at the top of the sign as a confirmation.
						if (verbose) {
							Block block = event.getBlock();
							logger.info("Player " + player.getName() + " created a new OrdosExp sign at " + block.getLocation().toString());
						}
						event.setLine(0, ChatColor.RED + "[OrdosExp]");
						return;
					} else {
						player.sendMessage(ChatColor.RED + "You don't have permission to place OrdosExp signs.");
						event.setCancelled(true);
						return;
					}
				}
			}
		}
		// if the user tried to fake a sign without having permission, cancel the event.
		String line0 = event.getLine(0);
		if ((line0.equalsIgnoreCase("[OrdosExp]")) && (!(player.hasPermission("ordosname.place")))) {
			player.sendMessage(ChatColor.RED + "You don't have permission to place OrdosExp signs.");
			event.setCancelled(true);
			return;
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	// EventPriority.NORMAL by default
	public void onBlockBreak(BlockBreakEvent event) {
		Player player = event.getPlayer();
		Block block = event.getBlock();
		if ((block.getType() == Material.WALL_SIGN) || (block.getType() == Material.SIGN_POST)) {
			Sign sign = (Sign) block.getState();
			if (sign.getLine(0).equals(ChatColor.RED + "[OrdosExp]")) {
				// if the sign is an OrdosExp sign, check if users have permission - if not, cancel the event.
				if (!(player.hasPermission("ordosname.place"))) {
					player.sendMessage(ChatColor.RED + "You don't have permission to destroy OrdosExp signs.");
					event.setCancelled(true);
					return;
				}
			}
		}
	}
}