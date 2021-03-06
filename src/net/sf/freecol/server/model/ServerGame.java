/**
 *  Copyright (C) 2002-2012   The FreeCol Team
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

package net.sf.freecol.server.model;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Event;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.FreeColGameObjectListener;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GameOptions;
import net.sf.freecol.common.model.HighSeas;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Limit;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.SimpleCombatModel;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.util.Utils;
import net.sf.freecol.server.control.ChangeSet;
import net.sf.freecol.server.control.ChangeSet.ChangePriority;
import net.sf.freecol.server.control.ChangeSet.See;


/**
 * The server representation of the game.
 */
public class ServerGame extends Game implements ServerModelObject {

    private static final Logger logger = Logger.getLogger(ServerGame.class.getName());

    /** How many cities of Cibola? */
    private static final int CIBOLA_COUNT = 7;

    // Timestamp of last move, if any.
    // Do not serialize.
    private long lastTime = -1L;


    /**
     * Creates a new game model.
     *
     * @param specification The <code>Specification</code> to use in this game.
     * @see net.sf.freecol.server.FreeColServer
     */
    public ServerGame(Specification specification) {
        super(specification);

        this.combatModel = new SimpleCombatModel();
        currentPlayer = null;
    }

    /**
     * Initiate a new <code>ServerGame</code> with information from a
     * saved game.
     *
     * @param freeColGameObjectListener A listener that should be monitoring
     *     this <code>Game</code>.
     * @param in The input stream containing the XML.
     * @param serverStrings A list of server object type,identifier
     *     pairs to create.  in this <code>Game</code>.
     * @param specification The <code>Specification</code> to use in this game.
     * @exception XMLStreamException if an error occurred during parsing.
     * @see net.sf.freecol.server.FreeColServer#loadGame
     */
    public ServerGame(FreeColGameObjectListener freeColGameObjectListener,
                      XMLStreamReader in, List<String> serverStrings,
                      Specification specification)
        throws XMLStreamException {
        this(specification);

        this.freeColGameObjectListener = freeColGameObjectListener;
        this.viewOwner = null;
        this.setGame(this);

        // Need a container to hold a reference to all the server
        // objects until the rest of the game is read.  Without this,
        // because the server objects are just placeholders with no
        // real references to them, the WeakReferences in the Game are
        // insufficient to preserve them across garbage collections.
        List<Object> serverObjects = new ArrayList<Object>();

        // Create trivial instantiations of all the server objects.
        while (!serverStrings.isEmpty()) {
            String type = serverStrings.remove(0);
            String id = serverStrings.remove(0);
            try {
                Object o = makeServerObject(type, id);
                serverObjects.add(o);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Build " + type + " failed", e);
            }
        }

        readFromXML(in);
    }

    /**
     * Makes a trivial server object in this game given a server object tag
     * and an identifier.
     *
     * @param type The server object tag.
     * @param id The object identifier.
     * @return A trivial server object.
     */
    private Object makeServerObject(String type, String id)
        throws ClassNotFoundException, IllegalAccessException,
               InstantiationException, InvocationTargetException,
               NoSuchMethodException {
        type = "net.sf.freecol.server.model."
            + type.substring(0,1).toUpperCase() + type.substring(1);
        Class<?> c = Class.forName(type);
        return c.getConstructor(Game.class, String.class)
            .newInstance(this, id);
    }


    /**
     * Get a unique identifier to identify a <code>FreeColGameObject</code>.
     * 
     * @return A unique identifier.
     */
    public String getNextId() {
        String id = Integer.toString(nextId);
        nextId++;
        return id;
    }


    /**
     * Checks if anybody has won this game.
     *
     * @return The <code>Player</code> who has won the game or null if none.
     */
    public Player checkForWinner() {
        Specification spec = getSpecification();
        if (spec.getBoolean(GameOptions.VICTORY_DEFEAT_REF)) {
            for (Player player : getLiveEuropeanPlayers()) {
                if (player.getPlayerType() == Player.PlayerType.INDEPENDENT) {
                    return player;
                }
            }
        }
        if (spec.getBoolean(GameOptions.VICTORY_DEFEAT_EUROPEANS)) {
            Player winner = null;
            for (Player player : getLiveEuropeanPlayers()) {
                if (!player.isREF()) {
                    if (winner != null) { // A live European player
                        winner = null;
                        break;
                    }
                    winner = player;
                }
            }
            if (winner != null) return winner;
        }
        if (spec.getBoolean(GameOptions.VICTORY_DEFEAT_HUMANS)) {
            Player winner = null;
            for (Player player : getLiveEuropeanPlayers()) {
                if (!player.isAI()) {
                    if (winner != null) { // A live human player
                        winner = null;
                        break;
                    }
                    winner = player;
                }
            }
            if (winner != null) return winner;
        }
        return null;
    }


    /**
     * Is the next player in a new turn?
     */
    public boolean isNextPlayerInNewTurn() {
        Player nextPlayer = getNextPlayer();
        return players.indexOf(currentPlayer) > players.indexOf(nextPlayer)
            || currentPlayer == nextPlayer;
    }


    /**
     * New turn for this game.
     *
     * @param random A <code>Random</code> number source.
     * @param cs A <code>ChangeSet</code> to update.
     */
    public void csNewTurn(Random random, ChangeSet cs) {
        String duration = null;
        long now = new Date().getTime();
        if (lastTime >= 0) {
            duration = ", previous turn duration = " + (now - lastTime) + "ms";
        }
        lastTime = now;

        TransactionSession.completeAll(cs);
        setTurn(getTurn().next());
        logger.finest("ServerGame.csNewTurn, turn is "
            + getTurn().toString() + duration);
        cs.addTrivial(See.all(), "newTurn", ChangePriority.CHANGE_NORMAL,
                      "turn", Integer.toString(getTurn().getNumber()));

        for (Player player : getPlayers()) {
            if (!player.isUnknownEnemy() && !player.isDead()) {
                ((ServerPlayer) player).csNewTurn(random, cs);
            }
        }

        Event spanishSuccession = getSpecification().getEvent("model.event.spanishSuccession");
        if (spanishSuccession != null && !getSpanishSuccession()) {
            Limit yearLimit = spanishSuccession.getLimit("model.limit.spanishSuccession.year");
            if (yearLimit.evaluate(this)) {
                csSpanishSuccession(cs, spanishSuccession);
            }
        }
    }

    /**
     * Checks for and if necessary performs the War of Spanish
     * Succession changes.
     *
     * @param cs A <code>ChangeSet</code> to update.
     * @param spanishSuccession A Spanish Succession <code>Event</code>.
     */
    private void csSpanishSuccession(ChangeSet cs, Event spanishSuccession) {
        Limit weakLimit = spanishSuccession.getLimit("model.limit.spanishSuccession.weakestPlayer");
        Limit strongLimit = spanishSuccession.getLimit("model.limit.spanishSuccession.strongestPlayer");
        Map<Player, Integer> scores = new HashMap<Player, Integer>();
        boolean ready = false;
        for (Player player : getLiveEuropeanPlayers()) {
            if (player.isREF()) continue;
            ready |= strongLimit.evaluate(player);
            // Human players can trigger the event, but only transfer assets
            // between AI players.
            if (player.isAI()) { 
                scores.put(player,
                    new Integer(player.getSpanishSuccessionScore()));
            }
        }
        if (!ready) return; // No player meets the support limit.

        Player weakestAIPlayer = null;
        Player strongestAIPlayer = null;
        for (Player player : scores.keySet()) {
            if ((weakestAIPlayer == null
                    || scores.get(weakestAIPlayer) > scores.get(player))
                && weakLimit.evaluate(player)) {
                weakestAIPlayer = player;
            }
            if (strongestAIPlayer == null
                || scores.get(strongestAIPlayer) < scores.get(player)) {
                strongestAIPlayer = player;
            }
        }

        if (weakestAIPlayer != null
            && strongestAIPlayer != null
            && weakestAIPlayer != strongestAIPlayer) {
            String logMe = "Spanish succession"
                + " in " + getTurn()
                + " scores[";
            for (Player player : scores.keySet()) {
                logMe += " " + player.getName() + "=" + scores.get(player);
            }
            logMe += " ]\n=> " + weakestAIPlayer.getName()
                + " cedes to " + strongestAIPlayer.getName()
                + ":";
            for (Player player : getPlayers()) {
                if (!player.isIndian()) continue;
                for (IndianSettlement settlement
                         : player.getIndianSettlementsWithMission(weakestAIPlayer)) {
                    logMe += " " + settlement.getName() + "(mission)";
                    settlement.setContacted(strongestAIPlayer);
                    Unit missionary = settlement.getMissionary();
                    missionary.setOwner(strongestAIPlayer);
                    Tile t = settlement.getTile();
                    t.updatePlayerExploredTile(strongestAIPlayer, true);
                    t.updatePlayerExploredTiles();
                    cs.add(See.perhaps().always((ServerPlayer)strongestAIPlayer),
                           settlement);
                }
            }
            for (Colony colony : weakestAIPlayer.getColonies()) {
                colony.changeOwner(strongestAIPlayer);
                logMe += " " + colony.getName();
                for (Tile tile : colony.getOwnedTiles()) {
                    cs.add(See.perhaps(), tile);
                }
            }
            for (Unit unit : weakestAIPlayer.getUnits()) {
                unit.setOwner(strongestAIPlayer);
                logMe += " " + unit.getId();
                if (unit.getLocation() instanceof Europe) {
                    unit.setLocation(strongestAIPlayer.getEurope());
                } else if (unit.getLocation() instanceof HighSeas) {
                    unit.setLocation(strongestAIPlayer.getHighSeas());
                }
                cs.add(See.perhaps(), unit);
            }

            StringTemplate loser = weakestAIPlayer.getNationName();
            StringTemplate winner = strongestAIPlayer.getNationName();
            cs.addMessage(See.all(),
                new ModelMessage(ModelMessage.MessageType.FOREIGN_DIPLOMACY,
                                 "model.diplomacy.spanishSuccession",
                                 strongestAIPlayer)
                          .addStringTemplate("%loserNation%", loser)
                          .addStringTemplate("%nation%", winner));
            cs.addGlobalHistory(this,
                new HistoryEvent(getTurn(),
                                 HistoryEvent.EventType.SPANISH_SUCCESSION)
                                .addStringTemplate("%loserNation%", loser)
                                .addStringTemplate("%nation%", winner));
            setSpanishSuccession(true);
            cs.addPartial(See.all(), this, "spanishSuccession");

            ((ServerPlayer) weakestAIPlayer).csKill(cs);
            logger.info(logMe);
        }
    }

    /**
     * Collects a list of all the ServerModelObjects in this game.
     *
     * @return A list of all the ServerModelObjects in this game.
     */
    public List<ServerModelObject> getServerModelObjects() {
        List<ServerModelObject> objs = new ArrayList<ServerModelObject>();
        for (WeakReference<FreeColGameObject> wr :freeColGameObjects.values()) {
            if (wr.get() instanceof ServerModelObject) {
                objs.add((ServerModelObject) wr.get());
            }
        }
        return objs;
    }

    /**
     * Initialize the list of cities of Cibola.
     *
     * @param random A pseudo-random number source.
     */
    public void initializeCitiesOfCibola(Random random) {
        citiesOfCibola.clear();
        for (int index = 0; index < CIBOLA_COUNT; index++) {
            citiesOfCibola.add("lostCityRumour.cityName." + index);
        }
        Utils.randomShuffle(logger, "Cibola", citiesOfCibola, random);
    }

    /**
     * Gets the tag name of the root element representing this object.
     *
     * @return "serverGame".
     */
    public String getServerXMLElementTagName() {
        return "serverGame";
    }
}
