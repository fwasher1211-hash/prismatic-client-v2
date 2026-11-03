package com.prismatic.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Locale;

/** /prismatic wp | team | item | quick */
public final class Commands {
	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("prismatic")
				// waypoints
				.then(ClientCommandManager.literal("wp")
					.then(ClientCommandManager.literal("add")
						.then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
							MinecraftClient mc = MinecraftClient.getInstance();
							if (mc.player == null || mc.world == null) return 0;
							String name = StringArgumentType.getString(c, "name");
							Config.waypoints().removeIf(w -> w.name.equalsIgnoreCase(name));
							Config.Waypoint w = new Config.Waypoint();
							w.name = name;
							w.dim = mc.world.getRegistryKey().getValue().toString();
							w.x = mc.player.getX();
							w.y = mc.player.getY();
							w.z = mc.player.getZ();
							Config.waypoints().add(w);
							Config.save();
							c.getSource().sendFeedback(Text.literal("Waypoint '" + name + "' saved. Turn on the Waypoints module to see it."));
							return 1;
						})))
					.then(ClientCommandManager.literal("remove")
						.then(ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
							String name = StringArgumentType.getString(c, "name");
							boolean gone = Config.waypoints().removeIf(w -> w.name.equalsIgnoreCase(name));
							Config.save();
							c.getSource().sendFeedback(Text.literal(gone ? "Removed '" + name + "'." : "No waypoint called '" + name + "'."));
							return 1;
						})))
					.then(ClientCommandManager.literal("list").executes(c -> {
						if (Config.waypoints().isEmpty()) c.getSource().sendFeedback(Text.literal("No waypoints saved."));
						for (Config.Waypoint w : Config.waypoints()) {
							c.getSource().sendFeedback(Text.literal(String.format(Locale.ROOT, "%s: %.0f %.0f %.0f (%s)", w.name, w.x, w.y, w.z, w.dim)));
						}
						return 1;
					}))
					.then(ClientCommandManager.literal("clear").executes(c -> {
						Config.waypoints().clear();
						Config.save();
						c.getSource().sendFeedback(Text.literal("Waypoints cleared."));
						return 1;
					})))
				// team
				.then(ClientCommandManager.literal("team")
					.then(ClientCommandManager.literal("add")
						.then(ClientCommandManager.argument("player", StringArgumentType.word()).executes(c -> {
							String n = StringArgumentType.getString(c, "player");
							Config.team().removeIf(s -> s.equalsIgnoreCase(n));
							Config.team().add(n);
							Config.save();
							c.getSource().sendFeedback(Text.literal("Added " + n + " to your team."));
							return 1;
						})))
					.then(ClientCommandManager.literal("remove")
						.then(ClientCommandManager.argument("player", StringArgumentType.word()).executes(c -> {
							String n = StringArgumentType.getString(c, "player");
							Config.team().removeIf(s -> s.equalsIgnoreCase(n));
							Config.save();
							c.getSource().sendFeedback(Text.literal("Removed " + n + "."));
							return 1;
						})))
					.then(ClientCommandManager.literal("clear").executes(c -> {
						Config.team().clear();
						Config.save();
						c.getSource().sendFeedback(Text.literal("Team cleared."));
						return 1;
					})))
				// item counter
				.then(ClientCommandManager.literal("item")
					.then(ClientCommandManager.literal("add")
						.then(ClientCommandManager.argument("item", StringArgumentType.word()).executes(c -> {
							String n = StringArgumentType.getString(c, "item").toLowerCase(Locale.ROOT).replace("minecraft:", "");
							if (Config.items().size() >= 10) {
								c.getSource().sendFeedback(Text.literal("You can track up to 10 items."));
								return 0;
							}
							if (!Config.items().contains(n)) Config.items().add(n);
							Config.save();
							c.getSource().sendFeedback(Text.literal("Tracking " + n + "."));
							return 1;
						})))
					.then(ClientCommandManager.literal("remove")
						.then(ClientCommandManager.argument("item", StringArgumentType.word()).executes(c -> {
							String n = StringArgumentType.getString(c, "item").toLowerCase(Locale.ROOT).replace("minecraft:", "");
							Config.items().remove(n);
							Config.save();
							c.getSource().sendFeedback(Text.literal("Stopped tracking " + n + "."));
							return 1;
						})))
					.then(ClientCommandManager.literal("clear").executes(c -> {
						Config.items().clear();
						Config.save();
						c.getSource().sendFeedback(Text.literal("Item counter cleared."));
						return 1;
					})))
				// quick action message
				.then(ClientCommandManager.literal("quick")
					.then(ClientCommandManager.argument("message", StringArgumentType.greedyString()).executes(c -> {
						String m = StringArgumentType.getString(c, "message");
						Config.setText("quick", m);
						c.getSource().sendFeedback(Text.literal("Quick action message set to: " + m));
						return 1;
					}))));
		});
	}

	private Commands() {
	}
}
