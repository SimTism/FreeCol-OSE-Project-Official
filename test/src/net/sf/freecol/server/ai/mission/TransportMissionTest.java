/**
 *  Copyright (C) 2002-2012  The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.server.ai.mission;

import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.CombatModel.CombatResult;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.PathNode;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.ai.AIGoods;
import net.sf.freecol.server.ai.AIMain;
import net.sf.freecol.server.ai.AIPlayer;
import net.sf.freecol.server.ai.AIUnit;
import net.sf.freecol.server.ai.Transportable;
import net.sf.freecol.server.ai.mission.TransportMission;
import net.sf.freecol.server.control.InGameController;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;
import net.sf.freecol.util.test.FreeColTestUtils;


public class TransportMissionTest extends FreeColTestCase {

    private static final GoodsType horsesType
        = spec().getGoodsType("model.goods.horses");

    private static final TileType plainsType
        = spec().getTileType("model.tile.plains");

    private static final UnitType colonistType
        = spec().getUnitType("model.unit.freeColonist");
    private static final UnitType galleonType
        = spec().getUnitType("model.unit.galleon");
    private static final UnitType privateerType
        = spec().getUnitType("model.unit.privateer");
    private static final UnitType wagonType
        = spec().getUnitType("model.unit.wagonTrain");


    @Override
    public void tearDown() throws Exception {
        ServerTestHelper.stopServerGame();
        super.tearDown();
    }


    public void testTransportMissionInvalidAfterCombatLost() {
        Map map = getCoastTestMap(plainsType);
        Game game = ServerTestHelper.startServerGame(map);
        InGameController igc = ServerTestHelper.getInGameController();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        assertNotNull(aiMain);

        ServerPlayer dutch = (ServerPlayer) game.getPlayer("model.nation.dutch");
        AIPlayer aiPlayer = aiMain.getAIPlayer(dutch);

        // Create a ship at sea carrying a colonist
        Tile tile1 = map.getTile(12, 9);

        Unit galleon = new ServerUnit(game, tile1, dutch, galleonType);
        AIUnit aiUnit = aiMain.getAIUnit(galleon);
        assertNotNull(aiUnit);
        assertTrue(galleon.hasAbility(Ability.NAVAL_UNIT));
        assertEquals("Galleon should be repaired in Europe",
            dutch.getEurope(), galleon.getRepairLocation());
        Unit colonist = new ServerUnit(game, galleon, dutch, colonistType);
        assertTrue(colonist.getLocation()==galleon);

        // Create the attacker, also at sea
        ServerPlayer french = (ServerPlayer) game.getPlayer("model.nation.french");
        Tile tile2 = map.getTile(11, 9);
        Unit privateer = new ServerUnit(game, tile2, french, privateerType);

        // assign transport mission to the ship
        aiUnit.setMission(new TransportMission(aiMain, aiUnit));

        // Simulate the combat
        igc.combat(dutch, privateer, galleon,
                   fakeAttackResult(CombatResult.WIN, privateer, galleon));

        // Verify that the outcome of the combat is a return to Europe
        // for repairs and also invalidation of the transport mission
        // as side effect.
        assertTrue(galleon.isDamaged());
        assertFalse(aiUnit.getMission().isValid());

        // This will call AIPlayer.abortInvalidMissions() and change
        // the carrier mission.
        aiPlayer.startWorking();
        assertFalse(aiUnit.getMission() instanceof TransportMission);
    }

    public void testGetNextStopAlreadyAtDestination(){
        Map map = getCoastTestMap(plainsType);
        Game game = ServerTestHelper.startServerGame(map);
        InGameController igc = ServerTestHelper.getInGameController();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        assertNotNull(aiMain);

        ServerPlayer dutch = (ServerPlayer) game.getPlayer("model.nation.dutch");

        // create a ship carrying a colonist
        Tile colonyTile = map.getTile(9, 9);
        getStandardColony(1, colonyTile.getX(), colonyTile.getY());

        Unit galleon = new ServerUnit(game, colonyTile, dutch, galleonType);
        AIUnit aiUnit = aiMain.getAIUnit(galleon);
        assertNotNull(aiUnit);

        // assign transport mission to the ship
        TransportMission mission = new TransportMission(aiMain, aiUnit);
        aiUnit.setMission(mission);
        Transportable goods = new AIGoods(aiMain,galleon, horsesType,50, colonyTile);
        mission.queueTransportable(goods, false);

        // Exercise
        Location dest = mission.getTarget();

        // Test
        assertNotNull("Unit should have a destination", dest);
        assertTrue("Unit should be already at the destination",
            Map.isSameLocation(dest, galleon.getLocation()));
    }

    public void testGetNextStopIsEurope() {
        Map map = getCoastTestMap(plainsType);
        Game game = ServerTestHelper.startServerGame(map);
        InGameController igc = ServerTestHelper.getInGameController();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        assertNotNull(aiMain);

        ServerPlayer dutch = (ServerPlayer)game.getPlayer("model.nation.dutch");
        Europe europe = dutch.getEurope();
        assertNotNull("Setup error, europe is null", europe);

        // create a ship carrying a colonist in a colony
        Tile colonyTile = map.getTile(9, 9);
        getStandardColony(1, colonyTile.getX(), colonyTile.getY());

        Unit galleon = new ServerUnit(game, colonyTile, dutch, galleonType);
        AIUnit aiUnit = aiMain.getAIUnit(galleon);
        assertNotNull(aiUnit);

        // assign transport mission to the ship
        TransportMission mission = new TransportMission(aiMain, aiUnit);
        aiUnit.setMission(mission);
        Transportable goods = new AIGoods(aiMain, galleon, horsesType, 50,
                                          europe);
        mission.queueTransportable(goods, false);
        mission.doMission();

        // Exercise
        Location dest = mission.getTarget();

        // Test
        assertNotNull("Unit should have a destination", dest);
        assertEquals("Destination should be Europe", europe, dest);
        assertFalse("Unit should have a path", 
                    galleon.getTurnsToReach(dest) == FreeColObject.INFINITY);
    }

    public void testGetNextStopIsColony() {
        Map map = getCoastTestMap(plainsType);
        Game game = ServerTestHelper.startServerGame(map);
        InGameController igc = ServerTestHelper.getInGameController();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        assertNotNull(aiMain);

        ServerPlayer dutch = (ServerPlayer)game.getPlayer("model.nation.dutch");
        Europe europe = dutch.getEurope();
        assertNotNull("Setup error, europe is null", europe);

        // create a ship carrying a colonist
        Tile colonyTile = map.getTile(9, 9);
        Tile galleonTile = map.getTile(9, 10);
        getStandardColony(1, colonyTile.getX(), colonyTile.getY());

        Unit galleon = new ServerUnit(game, galleonTile, dutch, galleonType);
        AIUnit aiUnit = aiMain.getAIUnit(galleon);
        assertNotNull(aiUnit);

        // assign transport mission to the ship
        TransportMission mission = new TransportMission(aiMain, aiUnit);
        aiUnit.setMission(mission);
        Transportable goods = new AIGoods(aiMain, galleon, horsesType, 50,
                                          colonyTile);
        mission.queueTransportable(goods, false);
        mission.doMission();

        // Exercise
        Location dest = mission.getTarget();

        // Test
        assertNotNull("Unit should have a destination", dest);
        assertEquals("Destination should be the colony",
                     colonyTile.getColony(), dest);
    }

    public void testGetDefaultDestination() {
        Map map = getCoastTestMap(plainsType);
        Game game = ServerTestHelper.startServerGame(map);
        InGameController igc = ServerTestHelper.getInGameController();
        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        assertNotNull(aiMain);

        ServerPlayer dutch = (ServerPlayer) game.getPlayer("model.nation.dutch");
        Europe europe = dutch.getEurope();
        assertNotNull("Setup error, europe is null", europe);

        // create a ship

        Tile galleonTile = map.getTile(9, 10);
        Unit galleon = new ServerUnit(game, galleonTile, dutch, galleonType);
        AIUnit aiUnit = aiMain.getAIUnit(galleon);
        assertNotNull(aiUnit);

        // assign transport mission to the ship
        TransportMission mission = new TransportMission(aiMain, aiUnit);
        aiUnit.setMission(mission);

        assertTrue("Setup error, player should not have colonies", dutch.getColonies().isEmpty());

        // Exercise
        Location dest = mission.getTarget();

        // Test
        assertNotNull("Unit should have a destination", dest);
        assertTrue("Destination should be Europe", dest instanceof Europe);

        // add colony
        Tile colonyTile = map.getTile(9, 9);
        FreeColTestUtils.ColonyBuilder builder = FreeColTestUtils.getColonyBuilder();
        builder.colonyTile(colonyTile).initialColonists(1).player(dutch).build();
        assertFalse("Player should now have a colony", dutch.getColonies().isEmpty());
        mission = new TransportMission(aiMain, aiUnit);
        aiUnit.setMission(mission);

        // Exercise
        dest = mission.getTarget();

        // Test
        assertNotNull("Unit should have a destination", dest);
        assertEquals("Destination should be the colony",
                     colonyTile.getColony(), dest);
    }

    public void testWagonTrain() {
        Map map = getTestMap();
        Game game = ServerTestHelper.startServerGame(map);
        Colony one = getStandardColony(3, 3, 3);
        Colony two = getStandardColony(3, 8, 8);
        assertEquals(one.getOwner(), two.getOwner());

        AIMain aiMain = ServerTestHelper.getServer().getAIMain();
        assertNotNull(aiMain);

        Unit wagonTrain = new ServerUnit(game, one.getTile(),
            (ServerPlayer) one.getOwner(), wagonType);
        AIUnit wagon = aiMain.getAIUnit(wagonTrain);
        assertNotNull(wagon);

        wagon.abortMission("test");
        assertNull("Transport mission should be valid",
                   TransportMission.invalidReason(wagon));
        TransportMission mission = new TransportMission(aiMain, wagon);

        Location dest = mission.getTarget();
        assertEquals("Destination should be colony one.", one, dest);

        wagonTrain.setLocation(one.getTile().getAdjacentTile(Map.Direction.NE));
        dest = mission.getTarget();
        assertEquals("Destination should still be colony one.", one, dest);

        AIGoods goods = new AIGoods(aiMain, two, horsesType, 20, one);
        mission.queueTransportable(goods, false);
        mission.doMission();

        dest = mission.getTarget();
        assertEquals("Destination should now be colony two.", two, dest);
    }
}
