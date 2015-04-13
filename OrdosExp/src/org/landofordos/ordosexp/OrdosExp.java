package org.landofordos.ordosexp;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
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
	private boolean keepNetZero;
	private long storedExp;

	public void onDisable() {
	    this.getConfig().set("storedExp", storedExp);
	    this.saveConfig();
	    logger.info("Saved stored XP in config file.");
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
		// register events
		server.getPluginManager().registerEvents(this, this);
		loadConfig();
		if (!setupEconomy()) {
			logger.info("Vault dependency unsatisfied (Vault could not be found)!");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
	}

	private void loadConfig() {
	    this.reloadConfig();
        FileConfiguration config = this.getConfig();
        // first-run initialisation
        final boolean firstrun = config.getBoolean("firstrun");
        if (firstrun) {
            // Whatever first run initialisation is required
            config.set("firstrun", false);
            this.saveConfig();
            if (verbose) {
                logger.info("First-run initialisation complete.");
            }
        }
        // plugin vars
        // verbose logging? retrieve value from config file.
        verbose = this.getConfig().getBoolean("verboselogging");
        if (verbose) {
            logger.info("Verbose logging enabled.");
        } else {
            logger.info("Verbose logging disabled.");
        }
        // keepNetZero
        keepNetZero = config.getBoolean("keepNetZero");
        if (keepNetZero) {
            logger.info("EXP sales are restricted to net zero.");
        } else {
            logger.info("EXP sales are unrestricted.");
        }
        // be extra careful about storedExp
        /*long configStoredExp = config.getLong("storedExp", -1l);
        if (configStoredExp > 0) {
            storedExp = configStoredExp;
        }*/
        storedExp = config.getLong("storedExp", 0);
        logger.info("There is currently " + storedExp + " XP stored.");
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
    
    private boolean serverGainsXP(int expChange) throws IOException {
        // Positive number = SERVER GAINS XP
        // check for net zero failure case        
        if (keepNetZero && expChange < 1 && storedExp < 1) {
            return false;
        } else {
            storedExp += expChange;
            this.getConfig().set("storedExp", storedExp);
            this.saveConfig();
            // check successful write
            this.reloadConfig();
            if (storedExp == this.getConfig().getLong("storedExp", -1l)) {
                return true;
            } else {
                logger.log(Level.SEVERE, "Saving to configuration file failed. OrdosExp cannot function while this continues.");
                throw new IOException("Configuration file save failed.");
            }    
        }
    }

	private boolean sellXPToPlayer(Player player, int expGained, double moneyLost) {
		// this SELLS XP TO THE PLAYER
		if (econ.getBalance(player) > moneyLost) {
		    try {
                if (serverGainsXP(expGained * -1)) {
                	econ.withdrawPlayer(player, moneyLost);
                    ExperienceManager expMan = new ExperienceManager(player);
                    expMan.changeExp(expGained);
                	player.sendMessage(ChatColor.DARK_GREEN + "You purchased " + ChatColor.WHITE + expGained + " XP" + ChatColor.DARK_GREEN + " for "
                			+ ChatColor.WHITE + moneyLost + " " + econ.currencyNamePlural());
                	logger.info(player.getName() + " purchased " + expGained + " XP for " + moneyLost + " " + econ.currencyNamePlural());
                } else {
                    if (verbose) {
                        logger.info(player.getName() + " tried to purchase " + expGained + " for " + moneyLost + " "
                                + econ.currencyNamePlural() + "but there wasn't enough XP stored.");
                    }
                    player.sendMessage(ChatColor.RED + "The server does not have any XP for purchase.");			        
                }
            } catch (IOException e) {
                player.sendMessage(ChatColor.RED + "Transaction failed. Please inform an admin.");
            }
		} else {
			if (verbose) {
				logger.info(player.getName() + " tried to purchase " + expGained + " for " + moneyLost + " "
						+ econ.currencyNamePlural() + "but did not have enough credit.");
			}
			player.sendMessage(ChatColor.RED + "You don't have enough money to do that.");
		}
		return true;
	}

	private boolean buyXPFromPlayer(Player player, int expLost, double moneyGained) {
		// this BUYS XP FROM THE PLAYER
		try {
            ExperienceManager expMan = new ExperienceManager(player);
			if (expMan.getCurrentExp() >= expLost) {
                if (serverGainsXP(expLost)) {
                    expMan.changeExp(expLost * -1);
    				econ.depositPlayer(player, moneyGained);
    				player.sendMessage(ChatColor.DARK_GREEN + "You sold " + ChatColor.WHITE + expLost + " XP" + ChatColor.DARK_GREEN + " for "
    						+ ChatColor.WHITE + moneyGained + " " + econ.currencyNamePlural());
    				logger.info(player.getName() + " sold " + expLost + " XP for " + moneyGained + " " + econ.currencyNamePlural());
                } else {
                    player.sendMessage(ChatColor.RED + "Transaction failed. Please inform an admin.");
                }
			} else {
				if (verbose) {
					logger.info(player.getName() + " tried to sell " + expLost + " XP for " + moneyGained + " "
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
        // code to reload configuration
        if ((args.length == 1) && (args[0].equalsIgnoreCase("reload")) && (sender.hasPermission("ordosexp.reloadconfig"))) {
            loadConfig();
            return true;
        } else if ((args.length == 1) && (args[0].equalsIgnoreCase("stock"))) {
            if (!sender.hasPermission("ordosexp.checkstored")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to check the stored XP.");                
            } else {
                if (keepNetZero) {
                    sender.sendMessage(ChatColor.WHITE + "" + storedExp + "" + ChatColor.DARK_GREEN + " XP is held by OrdosExp.");
                } else {
                    sender.sendMessage(ChatColor.DARK_GREEN + "OrdosExp sales are not currently restricted.");
                }
                return true;
            } 
        }
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
		            if (((line1.startsWith("Buy ")) || (line1.startsWith("Sell "))) && (line1.endsWith("XP")) && (line2.startsWith("For "))) {
						int expChange;
						double moneyChange;
						// use regex to strip out all non-numeric characters from both sttrings
						try {
						    expChange = Integer.parseInt(line1.replaceAll("[^\\d.]", ""));
						    moneyChange = Double.parseDouble(line2.replaceAll("[^\\d.]", ""));
						} catch (NumberFormatException e) {
							player.sendMessage(ChatColor.RED + "This sign has not been set up correctly. Contact an admin for assistance.");
							return;
						}
						// is this sign selling XP, or buying it?
						if (line1.startsWith("Buy ")) {
		                    if (!player.hasPermission("ordosexp.buyfromserver")) {
		                        player.sendMessage(ChatColor.RED + "You do not have permission to buy XP.");                      
		                    } else {
		                        sellXPToPlayer(player, expChange, moneyChange);
		                    }
						}
						if (line1.startsWith("Sell ")) {
                            if (!player.hasPermission("ordosexp.selltoserver")) {
                                player.sendMessage(ChatColor.RED + "You do not have permission to sell XP.");                      
                            } else {
                                buyXPFromPlayer(player, expChange, moneyChange);
                            }
						}
					} else if (line1.toLowerCase().contains("xp") && line1.toLowerCase().contains("stock")) {
			            if (!player.hasPermission("ordosexp.checkstored")) {
			                player.sendMessage(ChatColor.RED + "You don't have permission to check the stored XP.");                
			            } else {
			                sign.setLine(2, "" + storedExp);
			                sign.update();
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
            String line1 = event.getLine(1);
            String line2 = event.getLine(2);
			// then check it has the correct buy and sell strings
			if (((line1.startsWith("Buy ")) || (line1.startsWith("Sell "))) && (line1.endsWith("XP")) && (line2.startsWith("For "))) {
            // check the user has appropriate permissions to place a sign like this
                if (player.hasPermission("ordosexp.place")) {
    				// finally, check the numbers themselves for errors - parse them and catch exceptions.
    				try {
    					Double.parseDouble(line1.replaceAll("[^\\d.]", ""));
    					Double.parseDouble(line2.replaceAll("[^\\d.]", ""));
    				} catch (NumberFormatException e) {
    					player.sendMessage(ChatColor.RED + "This sign has not been set up correctly.");
    					return;
    				}
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
                
			} else if (line1.toLowerCase().contains("xp") && line1.toLowerCase().contains("stock")) {
                if (player.hasPermission("ordosexp.place")) {
    			    // this is a stock check sign - add the OrdosExp tag and set the current stock level
                    event.setLine(0, ChatColor.RED + "[OrdosExp]");
    			    event.setLine(2, "" + storedExp);
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have permission to place OrdosExp signs.");
                    event.setCancelled(true);
                    return;
                }
			}
		}
		// if the user tried to fake a sign without having permission, cancel the event.
		String line0 = event.getLine(0);
		if ((line0.equalsIgnoreCase("[OrdosExp]")) && (!(player.hasPermission("ordosexp.place")))) {
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
				if (!(player.hasPermission("ordosexp.place"))) {
					player.sendMessage(ChatColor.RED + "You don't have permission to destroy OrdosExp signs.");
					event.setCancelled(true);
					return;
				}
			}
		}
	}
}
