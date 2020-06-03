/*
 * Copyright (c) 2018, SomeoneWithAnInternetConnection
 * Copyright (c) 2018, oplosthee <https://github.com/oplosthee>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.varrockrooftopagility;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import static net.runelite.api.ObjectID.ROUGH_WALL_14412;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.plugins.botutils.BotUtils;
import org.pf4j.Extension;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import static net.runelite.client.plugins.varrockrooftopagility.VarrockAgilityState.*;


@Extension
@PluginDependency(BotUtils.class)
@PluginDescriptor(
	name = "Varrock Agility",
	enabledByDefault = false,
	description = "Illumine Varrock rooftop agility plugin",
	tags = {"agility"},
	type = PluginType.SKILLING
)
@Slf4j
public class VarrockAgilityPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private VarrockAgilityConfiguration config;

	@Inject
	private BotUtils utils;

	@Inject
	private ConfigManager configManager;

	private BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1);
	private ThreadPoolExecutor executorService = new ThreadPoolExecutor(1, 1, 25, TimeUnit.SECONDS, queue,
		new ThreadPoolExecutor.DiscardPolicy());

	VarrockAgilityState state;

	TileItem markOfGrace;
	Tile markOfGraceTile;
	MenuEntry targetMenu;
	LocalPoint beforeLoc = new LocalPoint(0,0); //initiate to mitigate npe, this sucks
	int timeout = 0;

	private final Set<Integer> itemIds = new HashSet<>();
	private final Set<Integer> gameObjIds = new HashSet<>();
	private final List<Integer> VARROCK_REGION_IDS = List.of(12853, 12597); //12853, 12597


	@Provides
	VarrockAgilityConfiguration provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VarrockAgilityConfiguration.class);
	}

	@Override
	protected void startUp()
	{

	}

	@Override
	protected void shutDown()
	{
		configManager.setConfiguration("VarrockAgility", "startBot", false);
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("VarrockAgility"))
		{
			return;
		}
		getConfigValues();
		if (event.getKey().equals("startBot"))
		{
			if (client != null && client.getLocalPlayer() != null && client.getGameState().equals(GameState.LOGGED_IN))
			{
				if (config.startBot())
				{
					//skillLocation = client.getLocalPlayer().getWorldLocation();
					getConfigValues();
					//log.info("Starting power-skiller at location: " + skillLocation);
				}
			}
			else
			{
				if (config.startBot())
				{
					log.info("Stopping bot");
					configManager.setConfiguration("VarrockAgility", "startBot", false);
				}
			}
		}
	}

	private void getConfigValues()
	{
		gameObjIds.clear();

		for (int i : utils.stringToIntArray(config.gameObjects()))
		{
			gameObjIds.add(i);
		}

		itemIds.clear();

		for (int i : utils.stringToIntArray(config.items()))
		{
			itemIds.add(i);
		}
	}

	//enables run if below given minimum energy with random positive variation
	private void handleRun(int minEnergy, int randMax)
	{
		if (utils.isRunEnabled())
		{
			return;
		}
		else if (client.getEnergy() > (minEnergy + utils.getRandomIntBetweenRange(0, randMax)))
		{
			log.info("enabling run");
			targetMenu = new MenuEntry("Toggle Run", "", 1, 57, -1, 10485782, false);
			utils.sleep(60, 350);
			utils.clickRandomPoint(0, 200);
		}
	}

	private void findObstacle()
	{
		VarrockAgilityObstacles varObstacle = VarrockAgilityObstacles.getObstacle(client.getLocalPlayer().getWorldLocation());
		if (varObstacle != null)
		{
			log.info(String.valueOf(varObstacle.getObstacleId()));
			if (varObstacle.getObstacleId() == ROUGH_WALL_14412)
			{
				DecorativeObject decObstacle = utils.findNearestDecorObject(varObstacle.getObstacleId());
				if (decObstacle != null)
				{
					targetMenu = new MenuEntry("", "", decObstacle.getId(), 3, decObstacle.getLocalLocation().getSceneX(), decObstacle.getLocalLocation().getSceneY(), false);
					utils.sleep(60, 350);
					utils.clickRandomPoint(client.getCenterX() + utils.getRandomIntBetweenRange(0, 300), client.getCenterY() + utils.getRandomIntBetweenRange(0, 300));
					return;
				}
			}
			GameObject objObstacle = utils.findNearestGameObject(varObstacle.getObstacleId()); //this probably doesn't work for climbing rough wall?
			if (objObstacle != null)
			{
				targetMenu = new MenuEntry("", "", objObstacle.getId(), 3, objObstacle.getSceneMinLocation().getX(), objObstacle.getSceneMinLocation().getY(), false);
				utils.sleep(60, 350);
				utils.clickRandomPoint(client.getCenterX() + utils.getRandomIntBetweenRange(0, 300), client.getCenterY() + utils.getRandomIntBetweenRange(0, 300));
				return;
			}
		}
		else
		{
			log.info("enum is null, not in obstacle area");
		}
	}


	public VarrockAgilityState getState()
	{
		if (timeout > 0)
		{
			return TIMEOUT;
		}
		if (utils.isMoving(beforeLoc)) //could also test with just isMoving
		{
			timeout = 2;
			return MOVING;
		}
		if (markOfGrace != null && markOfGraceTile != null)
		{
			VarrockAgilityObstacles currentObstacle = VarrockAgilityObstacles.getObstacle(client.getLocalPlayer().getWorldLocation());
			if (currentObstacle == null)
			{
				timeout = 1;
				return MOVING;
			}
			if (currentObstacle.getLocation().distanceTo(markOfGraceTile.getWorldLocation()) == 0)
			{
				return MARK_OF_GRACE;
			}
		}
		if (!utils.isMoving(beforeLoc))
		{
			return FIND_OBSTACLE;
		}
		return ANIMATING; //need to determine an appropriate default
	}

	@Subscribe
	private void onGameTick(GameTick tick)
	{
		//if (client != null && client.getLocalPlayer() != null && config.startBot())
		if (client != null && client.getLocalPlayer() != null && config.startBot())
		{
			if (!VARROCK_REGION_IDS.contains(client.getLocalPlayer().getWorldLocation().getRegionID()))
			{
				log.info("not in Varrock course region");
				return;
			}
			handleRun(40, 20);
			state = getState();
			//this seems shit
			beforeLoc = client.getLocalPlayer().getLocalLocation();
			switch (state)
			{
				case TIMEOUT:
					timeout--;
					return;
				case MARK_OF_GRACE:
					utils.sendGameMessage("Picking up mark of grace");
					targetMenu = new MenuEntry("", "", ItemID.MARK_OF_GRACE, 20, markOfGraceTile.getSceneLocation().getX(), markOfGraceTile.getSceneLocation().getY(), false);
					utils.sleep(60, 350);
					utils.clickRandomPoint(client.getCenterX() + utils.getRandomIntBetweenRange(0, 300), client.getCenterY() + utils.getRandomIntBetweenRange(0, 300));
					return;
				case FIND_OBSTACLE:
					findObstacle();
					return;
				case MOVING:
					break;
				default:
					return;
			}
		}
		else
		{
			//log.info("client/ player is null or bot isn't started");
			return;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (config.startBot())
		{
			if (targetMenu == null)
			{
				log.info("Modified MenuEntry is null");
				return;
			}
			else
			{
				//log.info("MenuEntry string event: " + targetMenu.toString());
				event.setMenuEntry(targetMenu);
				timeout = 2;
				targetMenu = null; //this allow the player to interact with the client without their clicks being overridden
			}
		}
		else
		{
			//TODO: capture object clicks
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		if (!VARROCK_REGION_IDS.contains(client.getLocalPlayer().getWorldLocation().getRegionID()) )
		{
			return;
		}

		TileItem item = event.getItem();
		Tile tile = event.getTile();

		if (item.getId() == ItemID.MARK_OF_GRACE)
		{
			utils.sendGameMessage("Mark of grace spawned");
			markOfGrace = item;
			markOfGraceTile = tile;
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		if (!VARROCK_REGION_IDS.contains(client.getLocalPlayer().getWorldLocation().getRegionID()))
		{
			return;
		}

		TileItem item = event.getItem();

		if (item.getId() == ItemID.MARK_OF_GRACE)
		{
			utils.sendGameMessage("Mark of grace despawned");
			markOfGrace = null;
		}
	}
}