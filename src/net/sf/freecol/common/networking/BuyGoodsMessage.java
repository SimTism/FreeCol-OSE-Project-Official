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

package net.sf.freecol.common.networking;

import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.model.ServerPlayer;

import org.w3c.dom.Element;


/**
 * The message sent when buying goods in Europe.
 */
public class BuyGoodsMessage extends DOMMessage {

    /**
     * The identifier of the carrier to load to goods onto.
     */
    private String carrierId;

    /**
     * The identifier of the type of goods to buy.
     */
    private String goodsTypeId;

    /**
     * The amount of goods to buy.
     */
    private String amountString;

    /**
     * Create a new <code>BuyGoodsMessage</code>.
     *
     * @param carrier The <code>Unit</code> to load the goods onto.
     * @param type The type of goods to buy.
     * @param amount The amount of goods to buy.
     */
    public BuyGoodsMessage(Unit carrier, GoodsType type, int amount) {
        this.carrierId = carrier.getId();
        this.goodsTypeId = type.getId();
        this.amountString = Integer.toString(amount);
    }

    /**
     * Create a new <code>BuyGoodsMessage</code> from a
     * supplied element.
     *
     * @param game The <code>Game</code> this message belongs to.
     * @param element The <code>Element</code> to use to create the message.
     */
    public BuyGoodsMessage(Game game, Element element) {
        this.carrierId = element.getAttribute("carrier");
        this.goodsTypeId = element.getAttribute("type");
        this.amountString = element.getAttribute("amount");
    }

    /**
     * Handle a "buyGoods"-message.
     *
     * @param server The <code>FreeColServer</code> handling the message.
     * @param player The <code>Player</code> the message applies to.
     * @param connection The <code>Connection</code> message was received on.
     * @return An update containing the carrier, or an error
     *     <code>Element</code> on failure.
     */
    public Element handle(FreeColServer server, Player player,
                          Connection connection) {
        ServerPlayer serverPlayer = server.getPlayer(connection);

        Unit carrier;
        try {
            carrier = player.getOurFreeColGameObject(carrierId, Unit.class);
        } catch (Exception e) {
            return DOMMessage.clientError(e.getMessage());
        }
        if (!carrier.canCarryGoods()) {
            return DOMMessage.clientError("Not a goods carrier: " + carrierId);
        } else if (!carrier.isInEurope()) {
            return DOMMessage.clientError("Not in Europe: " + carrierId);
        }

        GoodsType type = server.getSpecification().getGoodsType(goodsTypeId);
        if (type == null) {
            return DOMMessage.clientError("Not a goods type: " + goodsTypeId);
        } else if (!player.canTrade(type)) {
            return DOMMessage.clientError("Goods are boycotted: "
                + goodsTypeId);
        }

        int amount;
        try {
            amount = Integer.parseInt(amountString);
        } catch (NumberFormatException e) {
            return DOMMessage.clientError("Bad amount: " + amountString);
        }
        if (amount <= 0) {
            return DOMMessage.clientError("Amount must be positive: "
                + amountString);
        }

        // Try to buy.
        return server.getInGameController()
            .buyGoods(serverPlayer, carrier, type, amount);
    }

    /**
     * Convert this BuyGoodsMessage to XML.
     *
     * @return The XML representation of this message.
     */
    public Element toXMLElement() {
        return createMessage(getXMLElementTagName(),
            "carrier", carrierId,
            "type", goodsTypeId,
            "amount", amountString);
    }

    /**
     * The tag name of the root element representing this object.
     *
     * @return "buyGoods".
     */
    public static String getXMLElementTagName() {
        return "buyGoods";
    }
}
// TODO: this and SellGoodsMessage are almost identical, collapse into
// a single TradeGoods?
