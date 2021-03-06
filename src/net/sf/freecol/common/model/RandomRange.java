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
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import net.sf.freecol.common.util.Utils;


public class RandomRange {

    private static Logger logger = Logger.getLogger(RandomRange.class.getName());

    /** Percentage probability that the result is not zero. */
    private int probability = 0;

    /** The inclusive lower bound of the range. */
    private int minimum = 0;

    /** The inclusive upper bound of the range. */
    private int maximum = 0;

    /** Factor to multiply the final value with. */
    private int factor = 1;

    /** A list of Scopes limiting the applicability of this Feature. */
    private List<Scope> scopes = null;


    /**
     * Creates a new <code>RandomRange</code> instance.
     *
     * @param probability The probability of this result.
     * @param minimum The range inclusive minimum.
     * @param maximum The range inclusive maximum.
     * @param factor The result multiplier.
     */
    public RandomRange(int probability, int minimum, int maximum, int factor) {
        if (probability < 0) {
            throw new IllegalArgumentException("Negative probability "
                + probability);
        }
        if (minimum > maximum) {
            throw new IllegalArgumentException("Min " + minimum
                + " > Max " + maximum);
        }
        this.probability = probability;
        this.minimum = minimum;
        this.maximum = maximum;
        this.factor = factor;
    }

    /**
     * Read a new <code>RandomRange</code> instance from a stream.
     *
     * @param in The <code>XMLStreamReader</code> to read from.
     * @exception XMLStreamException if there is a problem reading the
     *     stream.
     */
    public RandomRange(XMLStreamReader in) throws XMLStreamException {
        readFromXML(in);
    }


    /**
     * Get the result probability.
     *
     * @return The probability.
     */
    public final int getProbability() {
        return probability;
    }

    /**
     * Get the range lower bound.
     *
     * @return The lower bound.
     */
    public final int getMinimum() {
        return minimum;
    }

    /**
     * Get the range upper bound.
     *
     * @return The upper bound.
     */
    public final int getMaximum() {
        return maximum;
    }

    /**
     * Get the multiplication factor.
     *
     * @return The factor.
     */
    public final int getFactor() {
        return factor;
    }

    /**
     * Get the scopes of this random range.
     *
     * @return The scopes of this <code>RandomRange</code>.
     */
    public List<Scope> getScopes() {
        if (scopes == null) return Collections.emptyList();
        return scopes;
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
     * Gets a random value from this range.
     *
     * @param prefix A logger prefix.
     * @param random A pseudo-random number source.
     * @param continuous Choose a continuous or discrete result.
     * @return A random amount of plunder as defined by this
     *     <code>RandomRange</code>.
     */
    public int getAmount(String prefix, Random random, boolean continuous) {
        if (probability >= 100
            || (probability > 0
                && Utils.randomInt(logger, prefix + " check-probability",
                                   random, 100) < probability)) {
            int range = maximum - minimum + 1;
            if (continuous) {
                int r = Utils.randomInt(logger, prefix + " random-range",
                                        random, range * factor);
                return r + minimum * factor;
            } else {
                int r = Utils.randomInt(logger, prefix + " random-range",
                                        random, range);
                return (r + minimum) * factor;
            }
        }
        return 0;
    }


    // Interface Object

    /**
     * {@inheritDoc}
     */
    public RandomRange clone() {
        return new RandomRange(probability, maximum, minimum, factor);
    }


    // Serialization
    // Note, this is not a FreeColObject, so the usual convenience functions
    // are not available.

    private static final String FACTOR_TAG = "factor";
    private static final String MAXIMUM_TAG = "maximum";
    private static final String MINIMUM_TAG = "minimum";
    private static final String PROBABILITY_TAG = "probability";
    private static final String SCOPE_TAG = "scope";


    /**
     * This method writes an XML-representation of this object to
     * the given stream.
     *
     * @param out The target stream.
     * @exception XMLStreamException if there are any problems writing
     *     to the stream.
     */
    public void toXML(XMLStreamWriter out, String tag) throws XMLStreamException {
        out.writeStartElement(tag);

        out.writeAttribute(PROBABILITY_TAG, Integer.toString(probability));

        out.writeAttribute(MINIMUM_TAG, Integer.toString(minimum));

        out.writeAttribute(MAXIMUM_TAG, Integer.toString(maximum));

        out.writeAttribute(FACTOR_TAG, Integer.toString(factor));

        for (Scope scope : getScopes()) scope.toXML(out);

        out.writeEndElement();
    }

    /**
     * Initializes this object from an XML-representation of this object.
     *
     * @param in The input stream with the XML.
     * @exception XMLStreamException if there are any problems reading
     *     from the stream.
     */
    public void readFromXML(XMLStreamReader in) throws XMLStreamException {
        probability = FreeColObject.getAttribute(in, PROBABILITY_TAG, 0);

        minimum = FreeColObject.getAttribute(in, MINIMUM_TAG, 0);

        maximum = FreeColObject.getAttribute(in, MAXIMUM_TAG, 0);

        factor = FreeColObject.getAttribute(in, FACTOR_TAG, 0);

        // Clear containers
        if (scopes != null) scopes.clear();

        while (in.nextTag() != XMLStreamConstants.END_ELEMENT) {
            final String tag = in.getLocalName();

            if (SCOPE_TAG.equals(tag)) {
                addScope(new Scope(in));

            } else {
                logger.warning("Bad RandomRange tag: " + tag);
            }
        }
    }
}
