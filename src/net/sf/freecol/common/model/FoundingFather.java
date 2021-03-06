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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import net.sf.freecol.common.model.Turn;


/**
 * Represents one FoundingFather to be contained in a Player object.
 * The FoundingFather is able to grant new abilities or bonuses to the
 * player, or to cause certain events.
 */
public class FoundingFather extends FreeColGameObjectType {

    public static enum FoundingFatherType {
        TRADE,
        EXPLORATION,
        MILITARY,
        POLITICAL,
        RELIGIOUS
    }

    /** The type of this FoundingFather. */
    private FoundingFatherType type;

    /**
     * The probability of this FoundingFather being offered for selection,
     * across the game ages.
     */
    private int[] weight = new int[Turn.NUMBER_OF_AGES];

    /**
     * Players that want to elect this founding father must match one
     * of these scopes.
     */
    private List<Scope> scopes = null;

    /** The events triggered by this founding father. */
    private List<Event> events = null;

    /** Holds the upgrades of Units caused by this FoundingFather. */
    private Map<UnitType, UnitType> upgrades = null;

    /** A list of AbstractUnits generated by this FoundingFather. */
    private List<AbstractUnit> units = null;


    /**
     * Create a new founding father.
     *
     * @param id The object identifier.
     * @param specification The <code>Specification</code> to refer to.
     */
    public FoundingFather(String id, Specification specification) {
        super(id, specification);

        setModifierIndex(Modifier.FATHER_PRODUCTION_INDEX);
    }


    /**
     * Gets the type of this FoundingFather.
     *
     * @return The type of this FoundingFather.
     */
    public FoundingFatherType getType() {
        return type;
    }

    /**
     * Set the type of this FoundingFather.
     *
     * Public for the test suite.
     *
     * @param type A new <code>FoundingFatherType</code>.
     */
    public void setType(FoundingFatherType type) {
        this.type = type;
    }

    /**
     * Get a key for the type of this FoundingFather.
     *
     * @return A type key.
     */
    public String getTypeKey() {
        return getTypeKey(type);
    }

    /**
     * Get a key for the type of a FoundingFather.
     *
     * @param type The <code>FoundingFatherType</code> to make a key for.
     * @return The type key.
     */
    public static String getTypeKey(FoundingFatherType type) {
        return "model.foundingFather." + type.toString().toLowerCase(Locale.US);
    }

    /**
     * Get the weight of this FoundingFather.
     * This is used to select a random FoundingFather.
     *
     * @param age The age (currently 1 -- 3).
     * @return The weight of this father in the given age.
     */
    public int getWeight(int age) {
        return (age >= 1 && age <= weight.length) ? weight[age-1] : 0;
    }

    /**
     * Get the events this father triggers.
     *
     * @return A list of <code>Event</code>s.
     */
    public final List<Event> getEvents() {
        if (events == null) return Collections.emptyList();
        return events;
    }

    /**
     * Set the events this founding father triggers.
     *
     * Public for the test suite.
     *
     * @param newEvents The new events.
     */
    public final void setEvents(final List<Event> newEvents) {
        this.events = newEvents;
    }

    /**
     * Add an event.
     *
     * @param event The <code>Event</code> to add.
     */
    private void addEvent(Event event) {
        if (events == null) events = new ArrayList<Event>();
        events.add(event);
    }

    /**
     * Get any scopes on the election of this father.
     *
     * @return A list of <code>Scope</code>s.
     */
    public final List<Scope> getScopes() {
        if (scopes == null) return Collections.emptyList();
        return scopes;
    }

    /**
     * Set the scopes on this founding father.
     *
     * Public for the test suite.
     *
     * @param newScopes The new scopes.
     */
    public final void setScopes(final List<Scope> newScopes) {
        this.scopes = newScopes;
    }

    /**
     * Add a scope.
     *
     * @param scope The <code>Scope</code> to add.
     */
    private void addScope(Scope scope) {
        if (scopes == null) scopes = new ArrayList<Scope>();
        scopes.add(scope);
    }

    /**
     * Get the upgrades triggered by this founding father.
     *
     * @return A map of old to new <code>UnitType</code>s.
     */
    public final Map<UnitType, UnitType> getUpgrades() {
        if (upgrades == null) return Collections.emptyMap();
        return upgrades;
    }

    /**
     * Set the upgrades triggered by this founding father.
     *
     * Public for the test suite.
     *
     * @param newUpgrades The new upgrades map.
     */
    public final void setUpgrades(final Map<UnitType, UnitType> newUpgrades) {
        this.upgrades = newUpgrades;
    }

    /**
     * Add an upgrade.
     *
     * @param fromType The initial <code>UnitType</code>.
     * @param toType The upgraded <code>UnitType</code>.
     */
    private void addUpgrade(UnitType fromType, UnitType toType) {
        if (upgrades == null) {
            upgrades = new HashMap<UnitType, UnitType>();
        }
        upgrades.put(fromType, toType);
    }

    /**
     * Get the units this father supplies.
     *
     * @return A list of <code>AbstractUnit</code>s.
     */
    public final List<AbstractUnit> getUnits() {
        if (units == null) return Collections.emptyList();
        return units;
    }

    /**
     * Set the units supplied by this founding father.
     *
     * Public for the test suite.
     *
     * @param newUnits The new units.
     */
    public final void setUnits(final List<AbstractUnit> newUnits) {
        this.units = newUnits;
    }

    /**
     * Add a unit.
     *
     * @param unit The <code>AbstractUnit</code> to add.
     */
    private void addUnit(AbstractUnit unit) {
        if (units == null) units = new ArrayList<AbstractUnit>();
        units.add(unit);
    }

    /**
     * Is this founding father available to the given player?
     *
     * Note that this does not cover restrictions due to the Age.
     *
     * @param player The <code>Player</code> to test.
     * @return True if the father is available.
     */
    public boolean isAvailableTo(Player player) {
        if (!player.isEuropean()) return false;
        if (scopes == null) return true;
        for (Scope scope : scopes) {
            if (scope.appliesTo(player)) return true;
        }
        return false;
    }


    // Serialization

    private static final String FROM_ID_TAG = "from-id";
    private static final String TO_ID_TAG = "to-id";
    private static final String TYPE_TAG = "type";
    private static final String UNIT_TAG = "unit";
    private static final String UPGRADE_TAG = "upgrade";
    private static final String WEIGHT_TAG = "weight";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void toXMLImpl(XMLStreamWriter out) throws XMLStreamException {
        super.toXML(out, getXMLElementTagName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(XMLStreamWriter out) throws XMLStreamException {
        super.writeAttributes(out);

        writeAttribute(out, TYPE_TAG, type);

        for (int i = 0; i < weight.length; i++) {
            writeAttribute(out, WEIGHT_TAG + (i + 1), weight[i]);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeChildren(XMLStreamWriter out) throws XMLStreamException {
        super.writeChildren(out);

        for (Event event : getEvents()) event.toXML(out);

        for (Scope scope : getScopes()) scope.toXML(out);

        for (AbstractUnit unit : getUnits()) {
            out.writeStartElement(UNIT_TAG);

            writeAttribute(out, ID_ATTRIBUTE_TAG, unit);

            out.writeEndElement();
        }

        if (upgrades != null) {
            for (Map.Entry<UnitType, UnitType> entry : upgrades.entrySet()) {
                out.writeStartElement(UPGRADE_TAG);

                writeAttribute(out, FROM_ID_TAG, entry.getKey().getId());

                writeAttribute(out, TO_ID_TAG, entry.getValue().getId());

                out.writeEndElement();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(XMLStreamReader in) throws XMLStreamException {
        super.readAttributes(in);

        type = getAttribute(in, TYPE_TAG, FoundingFatherType.class,
                            (FoundingFatherType)null);

        for (int i = 0; i < weight.length; i++) {
            weight[i] = getAttribute(in, WEIGHT_TAG + (i + 1), 0);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readChildren(XMLStreamReader in) throws XMLStreamException {
        // Clear containers.
        if (readShouldClearContainers(in)) {
            events = null;
            scopes = null;
            units = null;
            upgrades = null;
        }
        
        super.readChildren(in);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readChild(XMLStreamReader in) throws XMLStreamException {
        final Specification spec = getSpecification();
        final String tag = in.getLocalName();

        if (UPGRADE_TAG.equals(tag)) {
            UnitType fromType = spec.getType(in, FROM_ID_TAG, UnitType.class,
                                             (UnitType)null);
            UnitType toType = spec.getType(in, TO_ID_TAG, UnitType.class,
                                           (UnitType)null);
            if (fromType != null && toType != null) {
                addUpgrade(fromType, toType);
            }
            closeTag(in, UPGRADE_TAG);

        } else if (UNIT_TAG.equals(tag)) {
            addUnit(new AbstractUnit(in));

        } else if (Event.getXMLElementTagName().equals(tag)) {
            addEvent(new Event(in, spec));

        } else if (Scope.getXMLElementTagName().equals(tag)) {
            addScope(new Scope(in));

        } else {
            super.readChild(in);
        }
    }

    /**
     * Compatibility hack, called from the specification when it is
     * finishing up.
     */
    public void fixup09x() {
        // @compat 0.9.x
        try { // Cortes has changed
            if (!getModifierSet("model.modifier.nativeTreasureModifier")
                .isEmpty()) {
                addAbility(new Ability("model.ability.plunderNatives"));
            }
        } catch (Exception e) {} // we don't care
        // end @compat
    }

    /**
     * Gets the tag name of the root element representing this object.
     *
     * @return "founding-father".
     */
    public static String getXMLElementTagName() {
        return "founding-father";
    }
}
