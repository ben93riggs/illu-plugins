package net.runelite.client.plugins.iutils.game;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.iutils.scene.Locatable;
import net.runelite.client.plugins.iutils.scene.ObjectCategory;
import net.runelite.client.plugins.iutils.scene.Position;

import java.util.Arrays;
import java.util.Objects;

import static net.runelite.api.Constants.CHUNK_SIZE;

@Slf4j
public class iTile implements Locatable {
    final Game game;
    final Tile tile;
    //    List<GroundItem> items = new ArrayList<>();
    iObject regularObject;
    iObject wall;
    iObject wallDecoration;
    iObject floorDecoration;

    iTile(Game game, Tile tile) {
        this.game = game;
        this.tile = tile;
        //tile.getGameObjects()).filter(Objects::nonNull).findFirst().orElse(null)
    }

    //    @Override
    public Game game() {
        return game;
    }

    @Override
    public Client client() {
        return game.client;
    }

    @Override
    public Position position() {
        return new Position(tile.getSceneLocation().getX(), tile.getSceneLocation().getY(), tile.getPlane());
    }

    public Position templatePosition() {
        if (client().isInInstancedRegion()) {
            LocalPoint localPoint = client().getLocalPlayer().getLocalLocation();
            int[][][] instanceTemplateChunks = client().getInstanceTemplateChunks();
            int z = client().getPlane();
            int chunkData = instanceTemplateChunks[z][localPoint.getSceneX() / CHUNK_SIZE][localPoint.getSceneY() / CHUNK_SIZE];

            int rotation = chunkData >> 1 & 0x3;
            int chunkY = (chunkData >> 3 & 0x7FF) * CHUNK_SIZE;
            int chunkX = (chunkData >> 14 & 0x3FF) * CHUNK_SIZE;

            return new Position(chunkX, chunkY, rotation);
        }
        return game.localPlayer().position();
    }

    public void walkTo() {
        game.clientThread.invoke(() -> game.walkUtils.sceneWalk(position(), 0, 0));
    }

//    public List<GroundItem> items() {
//        return items;
//    }

//    public List<iObject> objects() {
//        ArrayList<iObject> objects = new ArrayList<>(4);
//        if (regularObject != null) objects.add(regularObject);
//        if (wall != null) objects.add(wall);
//        if (wallDecoration != null) objects.add(wallDecoration);
//        if (floorDecoration != null) objects.add(floorDecoration);
//        return objects;
//    }

    public iObject object(ObjectCategory category) {
        switch (category) {
            case REGULAR:
                GameObject go = Arrays.stream(tile.getGameObjects()).filter(Objects::nonNull).findFirst().orElse(null);
                return (go == null) ? null : new iObject(game, go,
                        game.getFromClientThread(() -> client().getObjectDefinition(go.getId()))
                );
            case WALL:
                WallObject wo = tile.getWallObject();
                return (wo == null) ? null :  new iObject(game, wo, game.getFromClientThread(() -> client().getObjectDefinition(wo.getId())));
            case WALL_DECORATION:
                DecorativeObject dec = tile.getDecorativeObject();
                return (dec == null) ? null :  new iObject(game, dec, game.getFromClientThread(() -> client().getObjectDefinition(dec.getId())));
            case FLOOR_DECORATION:
                GroundObject ground = tile.getGroundObject();
                return (ground == null) ? null :  new iObject(game, ground, game.getFromClientThread(() -> client().getObjectDefinition(ground.getId())));
            default:
                return null;
        }
    }

    /*public iObject object(ObjectCategory category) {
    	iObject result;
        return switch (category) {
            case REGULAR -> regularObject;
            case WALL -> wall;
            case WALL_DECORATION -> wallDecoration;
            case FLOOR_DECORATION -> floorDecoration;
        };
    }*/
}