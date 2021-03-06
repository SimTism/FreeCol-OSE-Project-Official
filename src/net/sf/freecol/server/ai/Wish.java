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


package net.sf.freecol.server.ai;

import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Location;

import org.w3c.dom.Element;


/**
 * Represents a need for something at a given <code>Location</code>.
 */
public abstract class Wish extends ValuedAIObject {

    private static final Logger logger = Logger.getLogger(Wish.class.getName());

    /** The requesting location of this wish. */
    protected Location destination;

    /**
     * The <code>Transportable</code> which will realize the wish,
     * or <code>null</code> if no <code>Transportable</code> has
     * been chosen.
     */
    protected Transportable transportable;


    /**
     * Creates a new uninitialized <code>Wish</code>.
     *
     * @param aiMain The main AI-object.
     * @param id The object identifier.
     */
    public Wish(AIMain aiMain, String id) {
        super(aiMain, id);

        this.destination = null;
        this.transportable = null;
    }

    /**
     * Creates a new <code>Wish</code> from the given
     * XML-representation.
     *
     * @param aiMain The main AI-object.
     * @param element The root element for the XML-representation 
     *       of a <code>Wish</code>.
     */
    public Wish(AIMain aiMain, Element element) {
        super(aiMain, element);
    }
    
    /**
     * Creates a new <code>Wish</code> from the given
     * XML-representation.
     *
     * @param aiMain The main AI-object.
     * @param in The input stream containing the XML.
     * @throws XMLStreamException if a problem was encountered
     *      during parsing.
     */
    public Wish(AIMain aiMain, XMLStreamReader in) throws XMLStreamException {
        super(aiMain, in);
    }


    /**
     * Checks if this <code>Wish</code> needs to be stored in a savegame.
     *
     * @return True if it has been allocated a transportable.
     */
    public boolean shouldBeStored() {
        return transportable != null;
    }

    /**
     * Gets the <code>Transportable</code> assigned to this <code>Wish</code>.
     *
     * @return The <code>Transportable</code> which will realize this wish,
     *         or <code>null</code> if none has been assigned.
     * @see #setTransportable
     * @see net.sf.freecol.server.ai.mission.WishRealizationMission
     */
    public Transportable getTransportable() {
        return transportable;
    }

    /**
     * Assigns a <code>Transportable</code> to this <code>Wish</code>.
     *
     * @param transportable The <code>Transportable</code> which should
     *        realize this wish.
     * @see #getTransportable
     * @see net.sf.freecol.server.ai.mission.WishRealizationMission
     */
    public void setTransportable(Transportable transportable) {
        this.transportable = transportable;
    }

    /**
     * Gets the destination of this <code>Wish</code>.
     *
     * @return The <code>Location</code> in which the
     *       {@link #getTransportable transportable} assigned to
     *       this <code>Wish</code> will have to reach.
     */
    public Location getDestination() {
        return destination;
    }

    /**
     * Gets the destination AI colony, if any.
     *
     * @return The destination <code>AIColony</code>.
     */
    public AIColony getDestinationAIColony() {
        return (destination instanceof Colony)
            ? getAIMain().getAIColony((Colony)destination)
            : null;
    }


    // Override AIObject

    /**
     * Disposes of this <code>AIObject</code> by removing any references
     * to this object.
     */
    @Override
    public void dispose() {
        destination = null;
        transportable = null;
        super.dispose();
    }

    /**
     * Checks the integrity of a <code>Wish</code>.
     * The destination must be neither null nor disposed, the transportable
     * may be null but must otherwise be intact.
     *
     * @param fix Fix problems if possible.
     * @return Negative if there are problems remaining, zero if
     *     problems were fixed, positive if no problems found at all.
     */
    @Override
    public int checkIntegrity(boolean fix) {
        int result = super.checkIntegrity(fix);
        if (transportable != null) {
            result = Math.min(result, 
                              ((AIObject)transportable).checkIntegrity(fix));
        }
        if (destination == null
            || ((FreeColGameObject)destination).isDisposed()) {
            result = -1;
        }
        return result;
    }


    // Serialization

    private static final String DESTINATION_TAG = "destination";
    private static final String TRANSPORTABLE_TAG = "transportable";


    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(XMLStreamWriter out) throws XMLStreamException {
        super.writeAttributes(out);

        // Write identifier, Location will match Object
        if (destination != null) {
            writeAttribute(out, DESTINATION_TAG, destination.getId());

            if (transportable != null) {
                writeAttribute(out, TRANSPORTABLE_TAG, transportable.getId());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(XMLStreamReader in) throws XMLStreamException {
        super.readAttributes(in);

        final AIMain aiMain = getAIMain();

        destination = findLocationAttribute(in, DESTINATION_TAG,
                                            aiMain.getGame());

        if (hasAttribute(in, TRANSPORTABLE_TAG)) {
            transportable = getAttribute(in, TRANSPORTABLE_TAG,
                                         AIUnit.class, (AIUnit)null);
            if (transportable == null) {
                transportable = new AIUnit(getAIMain(),
                    getAttribute(in, TRANSPORTABLE_TAG, (String)null));
            }
        } else {            
            transportable = null;
        }
    }
}
