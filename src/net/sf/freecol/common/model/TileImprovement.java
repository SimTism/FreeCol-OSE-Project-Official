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

package net.sf.freecol.common.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import net.sf.freecol.common.model.Map.Direction;
import net.sf.freecol.common.model.Specification;


/**
 * Represents a tile improvement, such as a river or road.
 */
public class TileImprovement extends TileItem implements Named {

    private static Logger logger = Logger.getLogger(TileImprovement.class.getName());

    /** River magnitudes */
    public static final int NO_RIVER = 0;
    public static final int SMALL_RIVER = 1;
    public static final int LARGE_RIVER = 2;
    public static final int FJORD_RIVER = 3;

    /** The type of this improvement. */
    private TileImprovementType type;

    /** Turns remaining until the improvement is complete, if any. */
    private int turnsToComplete;

    /**
     * The improvement magnitude.  Default is type.getMagnitude(), but
     * this will override.
     */
    private int magnitude;

    /** Image and overlay style information for the improvement. */
    private TileImprovementStyle style;

    /**
     * Whether this is a virtual improvement granted by some structure
     * on the tile (a Colony, for example).  Virtual improvements will
     * be removed along with the structure that granted them.
     */
    private boolean virtual;

    /** Cached bitmap of connections by direction, derived from style. */
    private long connected = 0L;


    /**
     * Creates a standard <code>TileImprovement</code>-instance.
     *
     * This constructor asserts that the game, tile and type are valid.
     * Does not set the style.
     *
     * @param game The enclosing <code>Game</code>.
     * @param tile The <code>Tile</code> on which this object sits.
     * @param type The <code>TileImprovementType</code> of this TileImprovement.
     */
    public TileImprovement(Game game, Tile tile, TileImprovementType type) {
        super(game, tile);
        if (type == null) {
            throw new IllegalArgumentException("Parameter 'type' must not be 'null'.");
        }
        this.type = type;
        if (!type.isNatural()) {
            this.turnsToComplete = tile.getType().getBasicWorkTurns()
                + type.getAddWorkTurns();
        }
        this.magnitude = type.getMagnitude();
        this.style = null;
        this.connected = 0L;
    }

    /**
     * Create an new TileImprovement from an existing one.
     *
     * @param game The enclosing <code>Game</code>.
     * @param tile The <code>Tile</code> where the improvement resides.
     * @param template The <code>TileImprovement</code> to copy.
     */
    public TileImprovement(Game game, Tile tile, TileImprovement template) {
        super(game, tile);

        this.type = template.type;
        this.turnsToComplete = template.turnsToComplete;
        this.magnitude = template.magnitude;
        this.style = template.style;
        this.virtual = template.virtual;
        this.connected = getConnectionsFromStyle();
    }

    /**
     * Create a new <code>TileImprovement</code> with the given
     * identifier.  The object should later be initialized by calling either
     * {@link #readFromXML(XMLStreamReader)} or
     * {@link #readFromXMLElement(Element)}.
     *
     * @param game The enclosing <code>Game</code>.
     * @param id The object identifier.
     */
    public TileImprovement(Game game, String id) {
        super(game, id);
    }


    /**
     * Gets the type of this tile improvement.
     *
     * @return The type of this improvement.
     */
    public TileImprovementType getType() {
        return type;
    }

    /**
     * Is this tile improvement a river?
     *
     * @return True if this is a river improvement.
     */
    public boolean isRiver() {
        return "model.improvement.river".equals(type.getId());
    }

    /**
     * Is this tile improvement a road?
     *
     * @return True if this is a road improvement.
     */
    public boolean isRoad() {
        return "model.improvement.road".equals(type.getId());
    }

    /**
     * Gets the directions that a connection can form across for this
     * this type of improvement.
     *
     * - For rivers, it is just the longSided directions.
     * - For roads, it is all directions.
     * - In other cases, no directions are relevant.
     *
     * @return An array of relevant directions, or null if none.
     */
    public Direction[] getConnectionDirections() {
        return (isRoad()) ? Direction.values()
            : (isRiver()) ? Direction.longSides
            : null;
    }

    /**
     * Gets a key for message routines.
     *
     * @return The name key.
     */
    public String getNameKey() {
        return type.getNameKey();
    }

    /**
     * How many turns remain until this improvement is complete?
     *
     * @return The current turns to completion.
     */
    public int getTurnsToComplete() {
        return turnsToComplete;
    }

    /**
     * Is this improvement complete?
     *
     * @return True if complete.
     */
    public boolean isComplete() {
        return turnsToComplete <= 0;
    }

    /**
     * Sets the turns required to complete the improvement.
     *
     * @param turns The new turns to completion.
     */
    public void setTurnsToComplete(int turns) {
        turnsToComplete = turns;
    }

    /**
     * Gets the magnitude of this improvement.
     *
     * @return The magnitude of this immprovement.
     */
    public int getMagnitude() {
        return magnitude;
    }

    /**
     * Sets the magnitude of this improvement.
     *
     * @param magnitude The new magnitude.
     */
    public void setMagnitude(int magnitude) {
        this.magnitude = magnitude;
    }

    /**
     * Gets the style of this improvement.
     *
     * @return The style
     */
    public TileImprovementStyle getStyle() {
        return style;
    }

    /**
     * Is this a virtual improvement?
     *
     * @return True if this is a virtual improvement.
     */
    public final boolean isVirtual() {
        return virtual;
    }

    /**
     * Set the virtual status of this improvement.
     * Used for the roads in a colony center tile.
     *
     * @param virtual The new virtual value.
     */
    public final void setVirtual(final boolean virtual) {
        this.virtual = virtual;
    }

    /**
     * Is this tile improvement connected to a similar improvement on
     * a neighbouring tile?
     *
     * @param direction The <code>Direction</code> to check.
     * @return True if this improvement is connected.
     */
    public boolean isConnectedTo(Direction direction) {
        return (connected & (1 << direction.ordinal())) != 0;
    }

    /**
     * Sets the connection status in a given direction.
     *
     * @param direction The <code>Direction</code> to set.
     * @param value The new status for the connection.
     */
    public void setConnected(Direction direction, boolean value) {
        boolean now = isConnectedTo(direction);
        if (now != value) {
            if (value) {
                connected |= 1 << direction.ordinal();
            } else {
                connected &= ~(1 << direction.ordinal());
            }
            style = TileImprovementStyle.getInstance(encodeConnections());
        }
    }

    /**
     * Encode a style string suitable for TileImprovementStyle.getInstance.
     *
     * @return A style string (may be null).
     */
    private String encodeConnections() {
        Direction[] dirns = getConnectionDirections();
        if (dirns == null) return null;
        String s = new String();
        for (Direction d : dirns) {
            s = s.concat((isConnectedTo(d)) ? Integer.toString(magnitude)
                : "0");
        }
        return s;
    }

    /**
     * Gets a map of connection-direction to magnitude.
     *
     * @return A map of the connections.
     */
    public Map<Direction, Integer> getConnections() {
        Direction[] dirns = getConnectionDirections();
        if (dirns == null) return Collections.emptyMap();
        Map<Direction, Integer> result
            = new EnumMap<Direction, Integer>(Direction.class);
        for (Direction d : dirns) {
            if (isConnectedTo(d)) result.put(d, magnitude);
        }
        return result;
    }

    /**
     * Gets a Modifier for the production bonus this improvement provides
     * for a given type of goods.
     *
     * @param goodsType The <code>GoodsType</code> to test.
     * @return A production <code>Modifier</code>, or null if none applicable.
     */
    public Modifier getProductionModifier(GoodsType goodsType) {
        return (isComplete()) ? type.getProductionModifier(goodsType) : null;
    }

    /**
     * Calculates the movement cost on the basis of connected tile
     * improvements.
     *
     * @param direction The <code>Direction</code> to move.
     * @param moveCost The original movement cost.
     * @return The movement cost with this improvement.
     */
    public int getMoveCost(Direction direction, int moveCost) {
        return (isComplete() && isConnectedTo(direction))
            ? type.getMoveCost(moveCost)
            : moveCost;
    }

    /**
     * What type of tile does this improvement change a given type to?
     *
     * @param tileType The original <code>TileType</code>.
     * @return The <code>TileType</code> that results from completing this
     *     improvement, or null if nothing changes.
     */
    public TileType getChange(TileType tileType) {
        return (isComplete()) ? type.getChange(tileType) : null;
    }

    /**
     * Can a unit build this improvement?
     *
     * @param unit A <code>Unit</code> to do the building.
     * @return True if the supplied unit can build this improvement.
     */
    public boolean isWorkerAllowed(Unit unit) {
        return (unit == null || isComplete()) ? false
            : type.isWorkerAllowed(unit);
    }

    /**
     * Updates the connections from the current style.
     *
     * Public for the test suite.
     *
     * @return The connections implied by the current style.
     */
    public long getConnectionsFromStyle() {
        long conn = 0L;
        if (style != null) {
            Direction[] directions = getConnectionDirections();
            if (directions != null) {
                String mask = style.getMask();
                for (int i = 0; i < directions.length; i++) {
                    if (mask.charAt(i) != '0') {
                        conn |= 1L << directions[i].ordinal();
                    }
                }
            }
        }
        return conn;
    }

    /**
     * Updates the connections from/to this river improvement on the basis
     * of the expected encoded river style.
     *
     * @param conns The encoded river connections, or null to disconnect.
     * @return The actual encoded connections found.
     */
    public String updateRiverConnections(String conns) {
        if (!isRiver()) return null;
        final Tile tile = getTile();
        int i = 0;
        String ret = "";
        for (Direction d : Direction.longSides) {
            Direction dReverse = d.getReverseDirection();
            Tile t = tile.getNeighbourOrNull(d);
            TileImprovement river = (t == null) ? null : t.getRiver();
            String c = (conns == null) ? "0" : conns.substring(i, i+1);

            if ("0".equals(c)) {
                if (river != null && river.isConnectedTo(dReverse)) {
                    river.setConnected(dReverse, false);
                }
                setConnected(d, false);
            } else {
                if (river != null) river.setConnected(dReverse, true);
                setConnected(d, true);
            }
            ret += c;
            i++;
        }
        return (style == null) ? null : style.getString();
    }

    /**
     * Updates the connections from/to this road improvement.
     *
     * @param connect If true, add connections, otherwise remove them.
     * @return A string encoding of the correct connections for this
     *     improvement.
     */
    public String updateRoadConnections(boolean connect) {
        if (!isRoad() || !isComplete()) return null;
        final Tile tile = getTile();
        String ret = "";
        for (Direction d : Direction.values()) {
            Tile t = tile.getNeighbourOrNull(d);
            TileImprovement road = (t == null) ? null : t.getRoad();
            if (road != null && road.isComplete()) {
                road.setConnected(d.getReverseDirection(), connect);
                setConnected(d, connect);
            }
        }
        return (style == null) ? null : style.getString();
    }

    /**
     * Fixes any tile improvement style discontinuities.
     *
     * We check only if this improvement is not connected to a neighbour
     * that *is* connected to this one, and connect this one.
     *
     * TODO: drop this one day when we never have style discontinuities.
     * This alas is not the case in 0.10.x.
     *
     * @param fix Fix problems if possible.
     * @return Negative if there are problems remaining, zero if
     *     problems were fixed, positive if no problems found at all.
     */
    public int checkIntegrity(boolean fix) {
        String curr = (style == null) ? null : style.getString();
        String found = (isRiver()) ? updateRiverConnections(curr)
            : (isRoad() && isComplete()) ? updateRoadConnections(true)
            : null;
        int result;
        if ((found == null && curr == null)
            || (found != null && curr != null && found.equals(curr))) {
            result = 1;
        } else if (fix) {
            this.style = TileImprovementStyle.getInstance(found);
            if ((this.style != null)
                != (isRiver() || (isRoad() && isComplete()))) {
                logger.warning("Bad style for improvement: " + this);
                result = -1;
            } else {
                logger.warning("Fixing improvement style from "
                    + curr + " to " + found + " at " + tile);
                result = 0;
            }
        } else {
            logger.warning("Broken improvement style " + curr
                + " should be " + found + " at " + tile);
            result = -1;
        }
        return result;
    }


    // Interface TileItem

    /**
     * {@inheritDoc}
     */
    public final int getZIndex() {
        return type.getZIndex();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isTileTypeAllowed(TileType tileType) {
        return type.isTileTypeAllowed(tileType);
    }

    /**
     * {@inheritDoc}
     */
    public int applyBonus(GoodsType goodsType, UnitType unitType,
                          int potential) {
        // Applies the production bonuses of this tile improvement to
        // the given base potential.  Currently, the unit type
        // argument is ignored and is only provided for the sake of
        // consistency.  The bonuses of future improvements might
        // depend on the unit type, however.

        int result = potential;
        // do not apply any bonuses if the base tile does not produce
        // any goods, and don't apply bonuses for incomplete
        // improvements (such as roads)
        if (potential > 0 && isComplete()) {
            result += type.getBonus(goodsType);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public List<Modifier> getProductionModifiers(GoodsType goodsType,
                                                 UnitType unitType) {
        List<Modifier> result = new ArrayList<Modifier>();
        Modifier modifier = getProductionModifier(goodsType);
        if (modifier != null) result.add(modifier);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isNatural() {
        return type.isNatural();
    }


    // Serialization

    private static final String MAGNITUDE_TAG = "magnitude";
    private static final String STYLE_TAG = "style";
    private static final String TILE_TAG = "tile";
    private static final String TURNS_TAG = "turns";
    private static final String TYPE_TAG = "type";
    private static final String VIRTUAL_TAG = "virtual";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void toXMLImpl(XMLStreamWriter out, Player player,
                             boolean showAll,
                             boolean toSavedGame) throws XMLStreamException {
        super.toXML(out, getXMLElementTagName(), player, showAll, toSavedGame);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(XMLStreamWriter out, Player player,
                                   boolean showAll,
                                   boolean toSavedGame) throws XMLStreamException {
        super.writeAttributes(out);

        writeAttribute(out, TILE_TAG, getTile());

        writeAttribute(out, TYPE_TAG, getType());

        writeAttribute(out, TURNS_TAG, turnsToComplete);

        writeAttribute(out, MAGNITUDE_TAG, magnitude);

        if (style != null) {
            writeAttribute(out, STYLE_TAG, style);
        }
        if (virtual) {
            writeAttribute(out, VIRTUAL_TAG, virtual);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(XMLStreamReader in) throws XMLStreamException {
        final Specification spec = getSpecification();
        final Game game = getGame();

        super.readAttributes(in);

        tile = makeFreeColGameObject(in, TILE_TAG, Tile.class);

        type = spec.getType(in, TYPE_TAG, TileImprovementType.class,
                            (TileImprovementType)null);

        turnsToComplete = getAttribute(in, TURNS_TAG, 0);

        magnitude = getAttribute(in, MAGNITUDE_TAG, 0);

        virtual = getAttribute(in, VIRTUAL_TAG, false);

        String str = getAttribute(in, STYLE_TAG, (String)null);
        Direction dirns[] = getConnectionDirections();
        if (dirns == null || str == null || "".equals(str)) {
            style = null;
        // @compat 0.10.5
        } else if (str.length() < 4) {
            String old = TileImprovementStyle.decodeOldStyle(str, dirns.length);
            style = (old == null) ? null
                : TileImprovementStyle.getInstance(old);
        // end @compat
        } else {
            style = TileImprovementStyle.getInstance(str);
            if (style == null) {
                logger.warning("At " + tile
                    + " ignored bogus TileImprovementStyle: " + str);
            }
        }
        if (style != null && style.toString().length() != dirns.length) {
            // @compat 0.10.5
            if ("0000".equals(style.getString())) {
                // Old virtual roads and fish bonuses have this style!?!
                style = null;
            } else {
            // end @compat

                throw new XMLStreamException("For " + type 
                    + ", bogus style: " + str + " -> /" + style
                    + "/ at " + tile);
            }
        }
        connected = getConnectionsFromStyle();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("[").append(getType().getId());
        if (turnsToComplete > 0) {
            sb.append(" (").append(turnsToComplete).append(" turns left)");
        }
        if (style != null) sb.append(" ").append(style.getString());
        sb.append("]");
        return sb.toString();
    }

    /**
     * Gets the tag name of the root element representing this object.
     *
     * @return "tileimprovement".
     */
    public static String getXMLElementTagName() {
        return "tileimprovement";
    }
}
