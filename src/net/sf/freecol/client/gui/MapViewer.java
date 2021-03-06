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

package net.sf.freecol.client.gui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JToolTip;
import javax.swing.UIManager;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GameOptions;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.LostCityRumour;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Map.Direction;
import net.sf.freecol.common.model.Map.Position;
import net.sf.freecol.common.model.PathNode;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Resource;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.TileItem;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.resources.ImageResource;
import net.sf.freecol.common.resources.ResourceManager;
import net.sf.freecol.common.util.Utils;


/**
 * This class is responsible for drawing the map/background on the
 * <code>Canvas</code>.
 *
 * In addition, the graphical state of the map (focus, active unit..)
 * is also a responsibility of this class.
 */
public final class MapViewer {

    public static enum BorderType { COUNTRY, REGION }

 
    class TextSpecification {
        public String text;
        public Font font;
        public TextSpecification(String newText, Font newFont) {
            text = newText;
            font = newFont;
        }
    }
    private static final Logger logger = Logger.getLogger(MapViewer.class.getName());

    private final FreeColClient freeColClient;
    private Dimension size;


    /**
     * Scaled ImageLibrary only used for map painting.
     */
    private ImageLibrary lib;

    private TerrainCursor cursor;
    private final Vector<GUIMessage> messages;

    private Tile selectedTile;
    private Tile focus = null;
    private Unit activeUnit;

    private Unit savedActiveUnit;

    private int currentMode;

    /** A path to be displayed on the map. */
    private PathNode currentPath;

    private PathNode gotoPath = null;
    private boolean gotoStarted = false;
    // Helper variables for displaying the map.
    private int tileHeight, tileWidth, halfHeight, halfWidth,
    topSpace,
    topRows,
    //bottomSpace,
    bottomRows,
    leftSpace,
    rightSpace;
    // The y-coordinate of the Tiles that will be drawn at the bottom
    private int bottomRow = -1;

    // The y-coordinate of the Tiles that will be drawn at the top
    private int topRow;
    // The y-coordinate on the screen (in pixels) of the images of the
    // Tiles that will be drawn at the bottom
    private int bottomRowY;
    // The y-coordinate on the screen (in pixels) of the images of the
    // Tiles that will be drawn at the top
    private int topRowY;

    // The x-coordinate of the Tiles that will be drawn at the left side
    private int leftColumn;

    // The x-coordinate of the Tiles that will be drawn at the right side
    private int rightColumn;
    // The x-coordinate on the screen (in pixels) of the images of the
    // Tiles that will be drawn at the left (can be less than 0)
    private int leftColumnX;

    // The height offset to paint a Unit at (in pixels).
    private static final int UNIT_OFFSET = 20,
        STATE_OFFSET_X = 25,
        STATE_OFFSET_Y = 10,
        OTHER_UNITS_OFFSET_X = -5, // Relative to the state indicator.
        OTHER_UNITS_OFFSET_Y = 1,
        OTHER_UNITS_WIDTH = 3,
        MAX_OTHER_UNITS = 10,
        MESSAGE_COUNT = 3,
        MESSAGE_AGE = 30000; // The amount of time before a message gets deleted (in milliseconds).;

    public static final int OVERLAY_INDEX = 100;
    public static final int FOREST_INDEX = 200;

    private GeneralPath gridPath = null;
    private GeneralPath fog = new GeneralPath();
    // Debug variables:
    public boolean displayCoordinates = false;

    public boolean displayColonyValue = false;
    public Player displayColonyValuePlayer = null;

    public boolean debugShowMission = false;

    public boolean debugShowMissionInfo = false;

    private volatile boolean blinkingMarqueeEnabled;

    private Image cursorImage;
    private GrayLayer greyLayer;

    private java.util.Map<Unit, Integer> unitsOutForAnimation;
    private java.util.Map<Unit, JLabel> unitsOutForAnimationLabels;

    // roads
    private EnumMap<Direction, Point2D.Float> corners =
        new EnumMap<Direction, Point2D.Float>(Direction.class);
    private EnumMap<Direction, List<Direction>> prohibitedRoads =
        new EnumMap<Direction, List<Direction>>(Direction.class);
    private Stroke roadStroke = new BasicStroke(2);
    // borders
    private EnumMap<Direction, Point2D.Float> borderPoints =
        new EnumMap<Direction, Point2D.Float>(Direction.class);

    private EnumMap<Direction, Point2D.Float> controlPoints =
        new EnumMap<Direction, Point2D.Float>(Direction.class);

    private Stroke borderStroke = new BasicStroke(4);

    private Stroke gridStroke = new BasicStroke(1);
    private GUI gui;

    /**
    * The constructor to use.
    *
    * @param freeColClient The main control class.
    * @param size The size of the GUI (= the entire screen if the app is displayed in full-screen).
    * @param lib The library of images needed to display certain things visually.
    */
    public MapViewer(FreeColClient freeColClient, GUI gui, Dimension size, ImageLibrary lib) {
        this.freeColClient = freeColClient;
        this.gui = gui;
        this.size = size;

        setImageLibrary(lib);

        unitsOutForAnimation = new HashMap<Unit, Integer>();
        unitsOutForAnimationLabels = new HashMap<Unit, JLabel>();

        logger.info("GUI created.");
        messages = new Vector<GUIMessage>(MESSAGE_COUNT);
        logger.info("Starting in Move Units View Mode");
        blinkingMarqueeEnabled = true;

        cursor = new net.sf.freecol.client.gui.TerrainCursor();

    }

    /**
    * Adds a message to the list of messages that need to be displayed on the GUI.
    * @param message The message to add.
    */
    public synchronized void addMessage(GUIMessage message) {
        if (getMessageCount() == MESSAGE_COUNT) {
            messages.remove(0);
        }
        messages.add(message);

    }

    /**
     * Centers the map on the selected unit.
     */
    public void centerActiveUnit() {
        if (activeUnit != null && activeUnit.getTile() != null) {
            setFocus(activeUnit.getTile());
        }
    }

    /**
     * Converts the given screen coordinates to Map coordinates.
     * It checks to see to which Tile the given pixel 'belongs'.
     *
     * @param x The x-coordinate in pixels.
     * @param y The y-coordinate in pixels.
     * @return The Tile that is located at
     * the given position on the screen.
     */
    public Tile convertToMapTile(int x, int y) {
        Game gameData = freeColClient.getGame();
        if ((gameData == null) || (gameData.getMap() == null)) {
            return null;
        }

        int leftOffset;
        if (focus.getX() < getLeftColumns()) {
            // we are at the left side of the map
            if ((focus.getY() % 2) == 0) {
                leftOffset = tileWidth * focus.getX() + halfWidth;
            } else {
                leftOffset = tileWidth * (focus.getX() + 1);
            }
        } else if (focus.getX() >= (gameData.getMap().getWidth() - getRightColumns())) {
            // we are at the right side of the map
            if ((focus.getY() % 2) == 0) {
                leftOffset = size.width - (gameData.getMap().getWidth() - focus.getX()) * tileWidth;
            } else {
                leftOffset = size.width - (gameData.getMap().getWidth() - focus.getX() - 1) * tileWidth - halfWidth;
            }
        } else {
            if ((focus.getY() % 2) == 0) {
                leftOffset = (size.width / 2);
            } else {
                leftOffset = (size.width / 2) + halfWidth;
            }
        }

        int topOffset;
        if (focus.getY() < topRows) {
            // we are at the top of the map
            topOffset = (focus.getY() + 1) * (halfHeight);
        } else if (focus.getY() >= (gameData.getMap().getHeight() - bottomRows)) {
            // we are at the bottom of the map
            topOffset = size.height - (gameData.getMap().getHeight() - focus.getY()) * (halfHeight);
        } else {
            topOffset = (size.height / 2);
        }

        // At this point (leftOffset, topOffset) is the center pixel of the Tile
        // that was on focus (= the Tile that should have been drawn at the center
        // of the screen if possible).

        // The difference in rows/columns between the selected
        // tile (x, y) and the current center Tile.
        // These values are positive if (x, y) is located NW
        // of the current center Tile.
        int diffUp = (topOffset - y) / (tileHeight / 4),
            diffLeft = (leftOffset - x) / (tileWidth / 4);

        // The following values are used when the user clicked somewhere
        // near the crosspoint of 4 Tiles.
        int orDiffUp = diffUp,
            orDiffLeft = diffLeft,
            remainderUp = (topOffset - y) % (tileHeight / 4),
            remainderLeft = (leftOffset - x) % (tileWidth / 4);

        if ((diffUp % 2) == 0) {
            diffUp = diffUp / 2;
        } else {
            if (diffUp < 0) {
                diffUp = (diffUp / 2) - 1;
            } else {
                diffUp = (diffUp / 2) + 1;
            }
        }

        if ((diffLeft % 2) == 0) {
            diffLeft = diffLeft / 2;
        } else {
            if (diffLeft < 0) {
                diffLeft = (diffLeft / 2) - 1;
            } else {
                diffLeft = (diffLeft / 2) + 1;
            }
        }

        boolean done = false;
        while (!done) {
            if ((diffUp % 2) == 0) {
                if ((diffLeft % 2) == 0) {
                    diffLeft = diffLeft / 2;
                    done = true;
                } else {
                    // Crosspoint
                    if (((orDiffLeft % 2) == 0) && ((orDiffUp % 2) == 0)) {
                        if ((orDiffLeft > 0) && (orDiffUp > 0)) {
                            // Upper-Left
                            if ((remainderUp * 2) > remainderLeft) {
                                diffUp++;
                            } else {
                                diffLeft++;
                            }
                        } else if (orDiffUp > 0) {
                            // Upper-Right
                            if ((remainderUp * 2) > -remainderLeft) {
                                diffUp++;
                            } else {
                                diffLeft--;
                            }
                        } else if ((orDiffLeft > 0) && (orDiffUp == 0)) {
                            if (remainderUp > 0) {
                                // Upper-Left
                                if ((remainderUp * 2) > remainderLeft) {
                                    diffUp++;
                                } else {
                                    diffLeft++;
                                }
                            } else {
                                // Lower-Left
                                if ((-remainderUp * 2) > remainderLeft) {
                                    diffUp--;
                                } else {
                                    diffLeft++;
                                }
                            }
                        } else if (orDiffUp == 0) {
                            if (remainderUp > 0) {
                                // Upper-Right
                                if ((remainderUp * 2) > -remainderLeft) {
                                    diffUp++;
                                } else {
                                    diffLeft--;
                                }
                            } else {
                                // Lower-Right
                                if ((-remainderUp * 2) > -remainderLeft) {
                                    diffUp--;
                                } else {
                                    diffLeft--;
                                }
                            }
                        } else if (orDiffLeft > 0) {
                            // Lower-Left
                            if ((-remainderUp * 2) > remainderLeft) {
                                diffUp--;
                            } else {
                                diffLeft++;
                            }
                        } else {
                            // Lower-Right
                            if ((-remainderUp * 2) > -remainderLeft) {
                                diffUp--;
                            } else {
                                diffLeft--;
                            }
                        }
                    } else if ((orDiffLeft % 2) == 0) {
                        if ((orDiffLeft > 0) && (orDiffUp > 0)) {
                            // Lower-Left
                            if ((remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                diffLeft++;
                            } else {
                                diffUp--;
                            }
                        } else if (orDiffUp > 0) {
                            // Lower-Right
                            if ((remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                diffLeft--;
                            } else {
                                diffUp--;
                            }
                        } else if (orDiffLeft > 0) {
                            // Upper-Left
                            if ((-remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                diffLeft++;
                            } else {
                                diffUp++;
                            }
                        } else {
                            // Upper-Right
                            if ((-remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                diffLeft--;
                            } else {
                                diffUp++;
                            }
                        }
                    } else if ((orDiffUp % 2) == 0) {
                        if ((orDiffLeft > 0) && (orDiffUp > 0)) {
                            // Upper-Right
                            if ((remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                diffUp++;
                            } else {
                                diffLeft--;
                            }
                        } else if (orDiffUp > 0) {
                            // Upper-Left
                            if ((remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                diffUp++;
                            } else {
                                diffLeft++;
                            }
                        } else if ((orDiffLeft > 0) && (orDiffUp == 0)) {
                            if (remainderUp > 0) {
                                // Upper-Right
                                if ((remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                    diffUp++;
                                } else {
                                    diffLeft--;
                                }
                            } else {
                                // Lower-Right
                                if ((-remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                    diffUp--;
                                } else {
                                    diffLeft--;
                                }
                            }
                        } else if (orDiffUp == 0) {
                            if (remainderUp > 0) {
                                // Upper-Left
                                if ((remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                    diffUp++;
                                } else {
                                    diffLeft++;
                                }
                            } else {
                                // Lower-Left
                                if ((-remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                    diffUp--;
                                } else {
                                    diffLeft++;
                                }
                            }
                        } else if (orDiffLeft > 0) {
                            // Lower-Right
                            if ((-remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                diffUp--;
                            } else {
                                diffLeft--;
                            }
                        } else {
                            // Lower-Left
                            if ((-remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                diffUp--;
                            } else {
                                diffLeft++;
                            }
                        }
                    } else {
                        if ((orDiffLeft > 0) && (orDiffUp > 0)) {
                            // Lower-Right
                            if ((remainderUp * 2) > remainderLeft) {
                                diffLeft--;
                            } else {
                                diffUp--;
                            }
                        } else if (orDiffUp > 0) {
                            // Lower-Left
                            if ((remainderUp * 2) > -remainderLeft) {
                                diffLeft++;
                            } else {
                                diffUp--;
                            }
                        } else if (orDiffLeft > 0) {
                            // Upper-Right
                            if ((-remainderUp * 2) > remainderLeft) {
                                diffLeft--;
                            } else {
                                diffUp++;
                            }
                        } else {
                            // Upper-Left
                            if ((-remainderUp * 2) > -remainderLeft) {
                                diffLeft++;
                            } else {
                                diffUp++;
                            }
                        }
                    }
                }
            } else {
                if ((diffLeft % 2) == 0) {
                    // Crosspoint
                    if (((orDiffLeft % 2) == 0) && ((orDiffUp % 2) == 0)) {
                        if ((orDiffLeft > 0) && (orDiffUp > 0)) {
                            // Upper-Left
                            if ((remainderUp * 2) > remainderLeft) {
                                diffUp++;
                            } else {
                                diffLeft++;
                            }
                        } else if (orDiffLeft > 0) {
                            // Lower-Left
                            if ((-remainderUp * 2) > remainderLeft) {
                                diffUp--;
                            } else {
                                diffLeft++;
                            }
                        } else if ((orDiffUp > 0) && (orDiffLeft == 0)) {
                            if (remainderLeft > 0) {
                                // Upper-Left
                                if ((remainderUp * 2) > remainderLeft) {
                                    diffUp++;
                                } else {
                                    diffLeft++;
                                }
                            } else {
                                // Upper-Right
                                if ((remainderUp * 2) > -remainderLeft) {
                                    diffUp++;
                                } else {
                                    diffLeft--;
                                }
                            }
                        } else if (orDiffLeft == 0) {
                            if (remainderLeft > 0) {
                                // Lower-Left
                                if ((-remainderUp * 2) > remainderLeft) {
                                    diffUp--;
                                } else {
                                    diffLeft++;
                                }
                            } else {
                                // Lower-Right
                                if ((-remainderUp * 2) > -remainderLeft) {
                                    diffUp--;
                                } else {
                                    diffLeft--;
                                }
                            }
                        } else if (orDiffUp > 0) {
                            // Upper-Right
                            if ((remainderUp * 2) > -remainderLeft) {
                                diffUp++;
                            } else {
                                diffLeft--;
                            }
                        } else {
                            // Lower-Right
                            if ((-remainderUp * 2) > -remainderLeft) {
                                diffUp--;
                            } else {
                                diffLeft--;
                            }
                        }
                    } else if ((orDiffLeft % 2) == 0) {
                        if ((orDiffLeft > 0) && (orDiffUp > 0)) {
                            // Lower-Left
                            if ((remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                diffLeft++;
                            } else {
                                diffUp--;
                            }
                        } else if (orDiffLeft > 0) {
                            // Upper-Left
                            if ((-remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                diffLeft++;
                            } else {
                                diffUp++;
                            }
                        } else if ((orDiffUp > 0) && (orDiffLeft == 0)) {
                            if (remainderLeft > 0) {
                                // Lower-Left
                                if ((remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                    diffLeft++;
                                } else {
                                    diffUp--;
                                }
                            } else {
                                // Lower-Right
                                if ((remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                    diffLeft--;
                                } else {
                                    diffUp--;
                                }
                            }
                        } else if (orDiffLeft == 0) {
                            if (remainderLeft > 0) {
                                // Upper-Left
                                if ((-remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                    diffLeft++;
                                } else {
                                    diffUp++;
                                }
                            } else {
                                // Upper-Right
                                if ((-remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                    diffLeft--;
                                } else {
                                    diffUp++;
                                }
                            }
                        } else if (orDiffUp > 0) {
                            // Lower-Right
                            if ((remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                diffLeft--;
                            } else {
                                diffUp--;
                            }
                        } else {
                            // Upper-Right
                            if ((-remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                diffLeft--;
                            } else {
                                diffUp++;
                            }
                        }
                    } else if ((orDiffUp % 2) == 0) {
                        if ((orDiffLeft > 0) && (orDiffUp > 0)) {
                            // Upper-Right
                            if ((remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                diffUp++;
                            } else {
                                diffLeft--;
                            }
                        } else if (orDiffUp > 0) {
                            // Upper-Left
                            if ((remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                diffUp++;
                            } else {
                                diffLeft++;
                            }
                        } else if (orDiffLeft > 0) {
                            // Lower-Right
                            if ((-remainderUp * 2 + remainderLeft) > (tileWidth / 4)) {
                                diffUp--;
                            } else {
                                diffLeft--;
                            }
                        } else {
                            // Lower-Left
                            if ((-remainderUp * 2 - remainderLeft) > (tileWidth / 4)) {
                                diffUp--;
                            } else {
                                diffLeft++;
                            }
                        }
                    } else {
                        if ((orDiffLeft > 0) && (orDiffUp > 0)) {
                            // Lower-Right
                            if ((remainderUp * 2) > remainderLeft) {
                                diffLeft--;
                            } else {
                                diffUp--;
                            }
                        } else if (orDiffUp > 0) {
                            // Lower-Left
                            if ((remainderUp * 2) > -remainderLeft) {
                                diffLeft++;
                            } else {
                                diffUp--;
                            }
                        } else if (orDiffLeft > 0) {
                            // Upper-Right
                            if ((-remainderUp * 2) > remainderLeft) {
                                diffLeft--;
                            } else {
                                diffUp++;
                            }
                        } else {
                            // Upper-Left
                            if ((-remainderUp * 2) > -remainderLeft) {
                                diffLeft++;
                            } else {
                                diffUp++;
                            }
                        }
                    }
                } else {
                    if ((focus.getY() % 2) == 0) {
                        if (diffLeft < 0) {
                            diffLeft = diffLeft / 2;
                        } else {
                            diffLeft = (diffLeft / 2) + 1;
                        }
                    } else {
                        if (diffLeft < 0) {
                            diffLeft = (diffLeft / 2) - 1;
                        } else {
                            diffLeft = diffLeft / 2;
                        }
                    }
                    done = true;
                }
            }
        }
        Position position = new Map.Position(focus.getX() - diffLeft, focus.getY() - diffUp);

        if (!freeColClient.getGame().getMap().isValid(position))
            return null;

        return freeColClient.getGame().getMap().getTile(position);

    }

    /**
     * Displays this GUI onto the given Graphics2D.
     * @param g The Graphics2D on which to display this GUI.
     */
    public void display(Graphics2D g) {
        if ((freeColClient.getGame() != null)
                && (freeColClient.getGame().getMap() != null)
                && (focus != null)
                && freeColClient.isInGame()) {
            removeOldMessages();
            displayMap(g);
        } else {
            if (freeColClient.isMapEditor()) {
                g.setColor(Color.black);
                g.fillRect(0, 0, size.width, size.height);
            } else {
                Image bgImage = ResourceManager.getImage("CanvasBackgroundImage", size);
                if (bgImage != null) {
                    g.drawImage(bgImage, 0, 0, gui.getCanvas());

                    // Show version on initial screen
                    String versionStr = "v. " + FreeCol.getVersion();
                    Font oldFont = g.getFont();
                    Color oldColor = g.getColor();
                    Font newFont = oldFont.deriveFont(Font.BOLD);
                    TextLayout layout = new TextLayout(versionStr, newFont, g.getFontRenderContext());

                    Rectangle2D bounds = layout.getBounds();
                    float x = getWidth() - (float) bounds.getWidth() - 5;
                    float y = getHeight() - (float) bounds.getHeight();
                    g.setColor(Color.white);
                    layout.draw(g, x, y);

                    // restore old values
                    g.setFont(oldFont);
                    g.setColor(oldColor);

                } else {
                    g.setColor(Color.black);
                    g.fillRect(0, 0, size.width, size.height);
                }
            }
        }
    }

    /**
     * Displays the given <code>Tile</code> onto the given
     * <code>Graphics2D</code> object at the location specified
     * by the coordinates. The visualization of the <code>Tile</code>
     * also includes information from the corresponding
     * <code>ColonyTile</code> from the given <code>Colony</code>.
     *
     * @param g The <code>Graphics2D</code> object on which to draw
     *      the <code>Tile</code>.
     * @param tile The <code>Tile</code> to draw.
     * @param colony The <code>Colony</code> to create the visualization
     *      of the <code>Tile</code> for. This object is also used to
     *      get the <code>ColonyTile</code> for the given <code>Tile</code>.
     */
    public void displayColonyTile(Graphics2D g, Tile tile, Colony colony) {
        boolean tileCannotBeWorked = false;
        Unit unit = null;
        int price = 0;
        if (colony != null) {
            ColonyTile colonyTile = colony.getColonyTile(tile);
            unit = colonyTile.getOccupyingUnit();
            price = colony.getOwner().getLandPrice(tile);
            switch (colonyTile.getNoWorkReason()) {
            case NONE: case COLONY_CENTER: case CLAIM_REQUIRED:
                break;
            default:
                tileCannotBeWorked = true;
            }
        }

        displayBaseTile(g, lib, tile, false);
        displayTileOverlays(g, tile, false, false);

        if (tileCannotBeWorked) {
            g.drawImage(lib.getMiscImage(ImageLibrary.TILE_TAKEN),
                        0, 0, null);
        }

        if (price > 0 && tile.getSettlement() == null) {
            Image image = lib.getMiscImage(ImageLibrary.TILE_OWNED_BY_INDIANS);
            centerImage(g, image);
        }

        if (unit != null) {
            ImageIcon image = lib.getUnitImageIcon(unit, 0.5);
            g.drawImage(image.getImage(),
                        tileWidth/4 - image.getIconWidth() / 2,
                        halfHeight - image.getIconHeight() / 2, null);
            // Draw an occupation and nation indicator.
            boolean owner = freeColClient.getMyPlayer().owns(unit);
            String text = Messages.message(unit.getOccupationKey(owner));
            g.drawImage(lib.getOccupationIndicatorChip(unit, text),
                        (int)(STATE_OFFSET_X * lib.getScalingFactor()),
                        0, null);
        }
    }

    /**
     * Displays the given Tile onto the given Graphics2D object at the
     * location specified by the coordinates. Draws the terrain and
     * improvements. Doesn't draw settlements, lost city rumours, fog
     * of war, optional values neither units.
     *
     * <br><br>The same as calling <code>displayTile(g, map, tile, x, y, true);</code>.
     * @param g The Graphics2D object on which to draw the Tile.
     * @param tile The Tile to draw.
     */
    public void displayTerrain(Graphics2D g, Tile tile) {
        displayBaseTile(g, lib, tile, true);
        displayTileItems(g, tile);
    }

    /**
     * Run some code with the given unit made invisible.
     * You can nest several of these method calls in order
     * to hide multiple units. There are no problems
     * related to nested calls with the same unit.
     *
     * @param unit The unit to be hidden.
     * @param sourceTile a <code>Tile</code> value
     * @param r The code to be executed.
     */
    public void executeWithUnitOutForAnimation(final Unit unit,
                                               final Tile sourceTile,
                                               final OutForAnimationCallback r) {
        final JLabel unitLabel = enterUnitOutForAnimation(unit, sourceTile);
        try {
            r.executeWithUnitOutForAnimation(unitLabel);
        } finally {
            releaseUnitOutForAnimation(unit);
        }
    }

    /**
     * Force the next screen repaint to reposition the tiles on the window.
     */
    public void forceReposition() {
        bottomRow = -1;
    }


    /**
    * Gets the active unit.
    *
    * @return The <code>Unit</code>.
    * @see #setActiveUnit
    */
    public Unit getActiveUnit() {
        return activeUnit;
    }


    /**
     * Describe <code>getCursor</code> method here.
     *
     * @return a <code>TerrainCursor</code> value
     */
    public TerrainCursor getCursor() {
        return cursor;
    }

    /**
    * Gets the focus of the map. That is the center tile of the displayed
    * map.
    *
    * @return The center tile of the
    *         displayed map
    * @see #setFocus(Tile)
    */
    public Tile getFocus() {
        return focus;
    }

    /**
    * Gets the path to be drawn on the map.
    * @return The path that should be drawn on the map
    *        or <code>null</code> if no path should be drawn.
    */
    public PathNode getGotoPath() {
        return gotoPath;
    }

    /**
     * Returns the height of this GUI.
     * @return The height of this GUI.
     */
    public int getHeight() {
        return size.height;
    }

    /**
     * Get the current scale of the map.
     *
     * @return a <code>float</code> value
     */
    public float getMapScale() {
        return lib.getScalingFactor();
    }

    /**
     * Gets the selected tile.
     *
     * @return The <code>Tile</code> selected.
     * @see #setSelectedTile(Tile, boolean)
     */
    public Tile getSelectedTile() {
        return selectedTile;
    }

    /**
     * Calculate the bounds of the rectangle containing a Tile on the screen,
     * and return it. If the Tile is not on-screen a maximal rectangle is returned.
     * The bounds includes a one-tile padding area above the Tile, to include the space
     * needed by any units in the Tile.
     * @param tile The tile on the screen.
     * @return The bounds rectangle
     */
    public Rectangle getTileBounds(Tile tile) {
        Rectangle result = new Rectangle(0, 0, size.width, size.height);
        if (isTileVisible(tile)) {
            result.x = ((tile.getX() - leftColumn) * tileWidth) + leftColumnX;
            result.y = ((tile.getY() - topRow) * halfHeight) + topRowY - tileHeight;
            if ((tile.getY() % 2) != 0) {
                result.x += halfWidth;
            }
            result.width = tileWidth;
            result.height = tileHeight * 2;
        }
        return result;
    }


    /**
     * Describe <code>getTileHeight</code> method here.
     *
     * @return an <code>int</code> value
     */
    public int getTileHeight() {
        return tileHeight;
    }


    /**
     * Gets the position of the given <code>Tile</code>
     * on the drawn map.
     *
     * @param t The <code>Tile</code>.
     * @return The position of the given <code>Tile</code>,
     *      or <code>null</code> if the <code>Tile</code> is
     *      not drawn on the mapboard.
     */
    public Point getTilePosition(Tile t) {
        repositionMapIfNeeded();
        if (!isTileVisible(t)) return null;

        int x = ((t.getX() - leftColumn) * tileWidth) + leftColumnX;
        int y = ((t.getY() - topRow) * halfHeight) + topRowY;
        if ((t.getY() % 2) != 0) x += halfWidth;
        return new Point(x, y);
    }


    /**
     * Describe <code>getTileWidth</code> method here.
     *
     * @return an <code>int</code> value
     */
    public int getTileWidth() {
        return tileWidth;
    }

    /**
     * Gets the position where a unitLabel located at tile should be drawn.
     * @param unitLabel The unit label with the unit's image and occupation indicator drawn.
     * @param tileP The position of the Tile on the screen.
     * @return The position where to put the label, null if tileP is null.
     */
    public Point getUnitLabelPositionInTile(JLabel unitLabel, Point tileP) {
        if (tileP != null) {
            int labelX = tileP.x + getTileWidth() / 2 - unitLabel.getWidth() / 2;
            int labelY = tileP.y + getTileHeight() / 2 - unitLabel.getHeight() / 2 -
                        (int) (UNIT_OFFSET * lib.getScalingFactor());

            return new Point(labelX, labelY);
        } else {
            return null;
        }
    }


    /**
     * Returns the width of this GUI.
     * @return The width of this GUI.
     */
    public int getWidth() {
        return size.width;
    }

    /**
     * Checks if there is currently a goto operation on the mapboard.
     * @return <code>true</code> if a goto operation is in progress.
     */
    public boolean isGotoStarted() {
        return gotoStarted;
    }

    /**
     * Checks if the Tile/Units at the given coordinates are displayed
     * on the screen (or, if the map is already displayed and the focus
     * has been changed, whether they will be displayed on the screen
     * the next time it'll be redrawn).
     *
     * @param tileToCheck The position of the Tile in question.
     * @return <i>true</i> if the Tile will be drawn on the screen, <i>false</i>
     * otherwise.
     */
    public boolean onScreen(Tile tileToCheck) {
        if (tileToCheck == null)
            return false;
        repositionMapIfNeeded();
        return tileToCheck.getY() - 2 > topRow
            && tileToCheck.getY() + 4 < bottomRow
            && tileToCheck.getX() - 1 > leftColumn
            && tileToCheck.getX() + 2 < rightColumn;
    }


    /**
     * Describe <code>restartBlinking</code> method here.
     *
     */
    public void restartBlinking() {
        blinkingMarqueeEnabled = true;
    }

    /**
     * Scroll the map in the given direction.
     *
     * @param direction The <code>Direction</code> to scroll in.
     * @return True if scrolling occurred.
     */
    public boolean scrollMap(Direction direction) {
        Tile t = getFocus();
        if (t == null) return false;
        int fx = t.getX(), fy = t.getY();
        if ((t = t.getNeighbourOrNull(direction)) == null) return false;
        int tx = t.getX(), ty = t.getY();
        int x, y;

        // When already close to an edge, resist moving the focus closer,
        // but if moving away immediately jump out of the `nearTo' area.
        if (isMapNearTop(ty) && isMapNearTop(fy)) {
            y = (ty <= fy) ? fy : topRows;
        } else if (isMapNearBottom(ty) && isMapNearBottom(fy)) {
            y = (ty >= fy) ? fy : freeColClient.getGame().getMap().getWidth()
                - bottomRows;
        } else {
            y = ty;
        }
        if (isMapNearLeft(tx, ty) && isMapNearLeft(fx, fy)) {
            x = (tx <= fx) ? fx : getLeftColumns(ty);
        } else if (isMapNearRight(tx, ty) && isMapNearRight(fx, fy)) {
            x = (tx >= fx) ? fx : freeColClient.getGame().getMap().getWidth()
                - getRightColumns(ty);
        } else {
            x = tx;
        }

        if (x == fx && y == fy) return false;
        setFocus(freeColClient.getGame().getMap().getTile(x,y));
        return true;
    }

    /**
     * Sets the active unit.  
     * Invokes {@link #setSelectedTile(Tile, boolean)} if the selected
     * tile is another tile than where the <code>activeUnit</code> is located.
     *
     * @param activeUnit The new active <code>Unit</code>.
     * @return True if the focus was set.
     * @see #setSelectedTile(Tile, boolean)
     */
    public boolean setActiveUnit(Unit activeUnit) {
        // Don't select a unit with zero moves left. -sjm
        // The user might what to check the status of a unit - SG
        Tile tile = (activeUnit == null) ? null : activeUnit.getTile();
        this.activeUnit = activeUnit;

        // The user activated a unit
        if (getView() == GUI.VIEW_TERRAIN_MODE && activeUnit != null) {
            changeViewMode(GUI.MOVE_UNITS_MODE);
        }

        if (activeUnit == null || tile == null) {
            freeColClient.updateActions();
            gui.updateMenuBar();
            gui.updateMapControls();
        } else {
            updateGotoPathForActiveUnit();
            if (!setSelectedTile(tile, false)
                || freeColClient.getClientOptions()
                .getBoolean(ClientOptions.JUMP_TO_ACTIVE_UNIT)) {
                setFocus(tile);
                return true;
            }
        }
        return false;
    }

    /**
    * Sets the focus of the map.
    *
    * @param focus The <code>Position</code> of the center tile of the
    *             displayed map.
    * @see #getFocus
    */
    public void setFocus(Tile focus) {
        this.focus = focus;

        gui.refresh();
    }

    /**
    * Sets the focus of the map and repaints the screen immediately.
    *
    * @param focus The <code>Position</code> of the center tile of the
    *             displayed map.
    * @see #getFocus
    */
    public void setFocusImmediately(Tile focus) {
        this.focus = focus;

        forceReposition();
        gui.getCanvas().paintImmediately(0, 0, getWidth(), getHeight());
    }


    /**
    * Sets the path to be drawn on the map.
    * @param gotoPath The path that should be drawn on the map
    *        or <code>null</code> if no path should be drawn.
    */
    public void setGotoPath(PathNode gotoPath) {
        this.gotoPath = gotoPath;

        gui.refresh();
    }


    /**
     * Sets the focus of the map but offset to the left or right so that
     * the focus position can still be visible when a popup is raised.
     * If successful, the supplied position will either be at the center of
     * the left or right half of the map.
     *
     * @param tile <code>Tile</code> of the displayed map
     * @return Positive if the focus is on the right hand side, negative
     *         if on the left, zero on failure.
     * @see #getFocus
     */
    public int setOffsetFocus(Tile tile) {
        int where = 0;
        if (tile != null) {
            positionMap(tile);
            Map map = freeColClient.getGame().getMap();
            if (leftColumn == 0) {
                where = -1; // At left edge already
            } else if (rightColumn == map.getWidth() - 1) {
                where = 1; // At right edge already
            } else { // Move focus left 1/4 screen
                int x = tile.getX() - (tile.getX() - leftColumn) / 2;
                tile = map.getTile(x, tile.getY());
                where = 1;
            }
            setFocus(tile);
        }
        return where;
    }


    /**
    * Selects the tile at the specified position. There are three
    * possible cases:
    *
    * <ol>
    *   <li>If there is a {@link Colony} on the {@link Tile} the
    *       {@link Canvas#showColonyPanel} will be invoked.
    *   <li>If the tile contains a unit that can become active, then
    *       that unit will be set as the active unit, and clear their
    *       goto orders if clearGoToOrders is <code>true</code>
    *   <li>If the two conditions above do not match, then the
    *       <code>selectedTile</code> will become the map focus.
    * </ol>
    *
    * If a unit is active and is located on the selected tile,
    * then nothing (except perhaps a map reposition) will happen.
    *
    * @param newTile The <code>Tile</code>, the tile to be selected
    * @param clearGoToOrders Use <code>true</code> to clear goto orders
    *                        of the unit which is activated
    * @return True if the focus was set.
    * @see #getSelectedTile
    * @see #setActiveUnit
    * @see #setFocus(Tile)
    */
    public boolean setSelectedTile(Tile newTile, boolean clearGoToOrders) {
        Tile oldTile = this.selectedTile;
        boolean ret = false;
        selectedTile = newTile;

        if (getView() == GUI.MOVE_UNITS_MODE) {
            if (noActiveUnitIsAt(newTile)) {
                if (newTile != null && newTile.getSettlement() != null) {
                    gui.getCanvas().showSettlement(newTile.getSettlement());
                    return false;
                }

                // else, just select a unit on the selected tile
                Unit unitInFront = getUnitInFront(newTile);
                if (unitInFront != null) {
                    ret = setActiveUnit(unitInFront);
                    updateGotoPathForActiveUnit();
                } else {
                    setFocus(newTile);
                    ret = true;
                }
            } else {
                // Clear goto order when unit is already active
                if (clearGoToOrders) {
                    freeColClient.getInGameController().clearGotoOrders(activeUnit);
                    updateGotoPathForActiveUnit();
                }
            }
        }

        freeColClient.updateActions();
        gui.updateMenuBar();

        gui.updateMapControls();

        // Check for refocus
        if (!onScreen(newTile)
            || freeColClient.getClientOptions().getBoolean(ClientOptions.ALWAYS_CENTER)) {
            setFocus(newTile);
            ret = true;
        } else {
            if (oldTile != null) {
                gui.refreshTile(oldTile);
            }

            if (newTile != null) {
                gui.refreshTile(newTile);
            }
        }
        return ret;
    }


    /**
     * Describe <code>setSize</code> method here.
     *
     * @param size a <code>Dimension</code> value
     */
    public void setSize(Dimension size) {
        this.size = size;
        updateMapDisplayVariables();
    }


    /**
     * Starts the unit-selection-cursor blinking animation.
     */
    public void startCursorBlinking() {

        ActionListener taskPerformer = new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if (!blinkingMarqueeEnabled)
                    return;
                if (getActiveUnit() != null && getActiveUnit().getTile() != null) {
                    Tile tile = getActiveUnit().getTile();
                    if (isTileVisible(tile)) {
                        gui.refreshTile(tile);
                    }
                }
            }
        };

        cursor.addActionListener(taskPerformer);

        cursor.startBlinking();
    }


    /**
    * Starts a goto operation on the mapboard.
    */
    public void startGoto() {
        gotoStarted = true;
        gui.getCanvas().setCursor((java.awt.Cursor) UIManager.get("cursor.go"));
        setGotoPath(null);
    }

    /**
     * Describe <code>stopBlinking</code> method here.
     *
     */
    public void stopBlinking() {
        blinkingMarqueeEnabled = false;
    }


    /**
     * Stops any ongoing goto operation on the mapboard.
     */
    public void stopGoto() {
        gui.getCanvas().setCursor(null);
        setGotoPath(null);
        updateGotoPathForActiveUnit();
        gotoStarted = false;
    }

    /**
     * Sets the path of the active unit to display it.
     */
    public void updateGotoPathForActiveUnit() {
        currentPath = (activeUnit == null
                       || activeUnit.getDestination() == null
                       || Map.isSameLocation(activeUnit.getLocation(),
                                             activeUnit.getDestination()))
            ? null
            : activeUnit.findPath(activeUnit.getDestination());
    }

    /**
     * Change the scale of the map by delta.
     *
     * @param delta a <code>float</code> value
     */
    void scaleMap(float delta) {
        float newScale = lib.getScalingFactor() + delta;
        try {
            if (newScale >= 1f) {
                setImageLibrary(gui.getImageLibrary());
            } else {
                setImageLibrary(new ImageLibrary(newScale));
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to retrieve scaled image library.", ex);
        }
    }


    /**
     * Centers the given Image on the tile.
     *
     * @param g a <code>Graphics2D</code> value
     * @param image an <code>Image</code> value
     */
    private void centerImage(Graphics2D g, Image image) {
        g.drawImage(image,
                    (tileWidth - image.getWidth(null))/2,
                    (tileHeight - image.getHeight(null))/2,
                    null);
    }


    /**
     * Center the given String on the current Tile.
     *
     * @param g a <code>Graphics2D</code> value
     * @param text a <code>String</code> value
     */
    private void centerString(Graphics2D g, String text) {
        g.setColor(Color.BLACK);
        g.setFont(ResourceManager.getFont("NormalFont", 12f));
        g.drawString(text,
                     (tileWidth - g.getFontMetrics().stringWidth(text))/2,
                     (tileHeight - g.getFontMetrics().getAscent())/2);
    }


    /**
     * Draws the pentagram indicating a native capital.
     *
     */
    private Image createCapitalLabel(int extent, int padding, Color backgroundColor) {
        String key = "dynamic.label.nativeCapital"
            + "." + Integer.toHexString(backgroundColor.getRGB());
        Image image = (Image) ResourceManager.getImage(key, lib.getScalingFactor());
        if (image != null) {
            return image;
        }

        // create path
        double deg2rad = Math.PI/180.0;
        double angle = -90.0 * deg2rad;
        double offset = extent * 0.5;
        double size = (extent - padding - padding) * 0.5;

        GeneralPath path = new GeneralPath();
        path.moveTo(Math.cos(angle) * size + offset, Math.sin(angle) * size + offset);
        for (int i = 0; i < 4; i++) {
            angle += 144 * deg2rad;
            path.lineTo(Math.cos(angle) * size + offset, Math.sin(angle) * size + offset);
        }
        path.closePath();

        // draw everything
        BufferedImage bi = new BufferedImage(extent, extent, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(backgroundColor);
        g.fill(new RoundRectangle2D.Float(0, 0, extent, extent, padding, padding));
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
        g.setColor(Color.WHITE);
        g.fill(path);
        ResourceManager.addGameMapping(key, new ImageResource(bi));
        return (Image) ResourceManager.getImage(key, lib.getScalingFactor());
    }


    /**
     * Creates an Image that shows the given text centred on a
     * translucent rounded rectangle with the given color.
     *
     * @param g a <code>Graphics2D</code> value
     * @param text a <code>String</code> value
     * @param font a <code>Font</code> value
     * @param backgroundColor a <code>Color</code> value
     * @return an <code>Image</code> value
     */
    private Image createLabel(Graphics2D g, String text, Font font, Color backgroundColor) {
        TextSpecification[] specs = new TextSpecification[1];
        specs[0] = new TextSpecification(text, font);
        return createLabel(g, specs, backgroundColor);
    }


    /**
     * Creates an Image that shows the given text centred on a
     * translucent rounded rectangle with the given color.
     *
     * @param g a <code>Graphics2D</code> value
     * @param textSpecs a <code>TextSpecification</code> array
     * @param backgroundColor a <code>Color</code> value
     * @return an <code>Image</code> value
     */
    private Image createLabel(Graphics2D g, TextSpecification[] textSpecs, Color backgroundColor) {
        int hPadding = 15;
        int vPadding = 10;
        int linePadding = 2;
        int width = 0;
        int height = vPadding;
        int i;

        TextSpecification spec;
        TextLayout[] labels = new TextLayout[textSpecs.length];
        TextLayout label;

        for (i = 0; i < textSpecs.length; i++) {
            spec = textSpecs[i];
            label = new TextLayout(spec.text, spec.font, g.getFontRenderContext());
            labels[i] = label;
            width = Math.max(width, (int) label.getBounds().getWidth() + hPadding);
            height += (int) (label.getAscent() + label.getDescent());
            if (i > 0) height += linePadding;
        }

        int radius = Math.min(hPadding, vPadding);

        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        g2.scale(lib.getScalingFactor(), lib.getScalingFactor());
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        g2.setColor(backgroundColor);
        g2.fill(new RoundRectangle2D.Float(0, 0, width, height, radius, radius));
        g2.setColor(lib.getForegroundColor(backgroundColor));
        int offset = 0;
        for (i = 0; i < labels.length; i++) {
            if (i > 0) offset += labels[i - 1].getAscent() + linePadding + vPadding/2;
            labels[i].draw(g2, (float) (width - labels[i].getBounds().getWidth())/2,
                           offset + labels[i].getAscent() + vPadding/2);
        }
        return bi;
    }


    /**
     * Draws a cross indicating a religious mission is present in the
     * native village.
     */
    private Image createReligiousMissionLabel(int extent, int padding, Color backgroundColor, boolean expertMissionary) {
        String key = "dynamic.label.religiousMission"
            + (expertMissionary ? ".expert" : "")
            + "." + Integer.toHexString(backgroundColor.getRGB());
        Image image = (Image) ResourceManager.getImage(key, lib.getScalingFactor());
        if (image != null) {
            return image;
        }

        // create path
        double offset = extent * 0.5;
        double size = extent - padding - padding;
        double bar = size / 3.0;
        double inset = 0.0;
        double kludge = 0.0;

        GeneralPath circle = new GeneralPath();
        GeneralPath cross = new GeneralPath();
        if (expertMissionary) {
            // this is meant to represent the eucharist (the -1, +1 thing is a nasty kludge)
            circle.append(new Ellipse2D.Double(padding-1, padding-1, size+1, size+1), false);
            inset = 4.0;
            bar = (size - inset - inset) / 3.0;
            // more nasty -1, +1 kludges
            kludge = 1.0;
        }
        offset -= 1.0;
        cross.moveTo(offset, padding + inset - kludge);
        cross.lineTo(offset, extent - padding - inset);
        cross.moveTo(offset - bar, padding + bar + inset);
        cross.lineTo(offset + bar + 1, padding + bar + inset);

        // draw everything
        BufferedImage bi = new BufferedImage(extent, extent, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(backgroundColor);
        g.fill(new RoundRectangle2D.Float(0, 0, extent, extent, padding, padding));
        g.setColor(lib.getForegroundColor(backgroundColor));
        if (expertMissionary) {
            g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(circle);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        } else {
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }
        g.draw(cross);
        ResourceManager.addGameMapping(key, new ImageResource(bi));
        return (Image) ResourceManager.getImage(key, lib.getScalingFactor());
    }

    /**
     * Displays the given Tile onto the given Graphics2D object at the
     * location specified by the coordinates. Only base terrain will be drawn.
     *
     * @param g The Graphics2D object on which to draw the Tile.
     * @param library The <code>ImageLibrary</code> to use. 
     * @param tile The Tile to draw.
     * @param drawUnexploredBorders If true; draws border between explored and
     *        unexplored terrain.
     */
    private void displayBaseTile(Graphics2D g, ImageLibrary library, Tile tile,
                                 boolean drawUnexploredBorders) {
        if (tile != null) {
            int x = tile.getX();
            int y = tile.getY();
            // ATTENTION: we assume that all base tiles have the same size
            g.drawImage(library.getTerrainImage(tile.getType(), x, y),
                        0, 0, null);
            if (tile.isExplored()) {
                if (!tile.isLand() && tile.getStyle() > 0) {
                    int edgeStyle = tile.getStyle() >> 4;
                    if (edgeStyle > 0) {
                        g.drawImage(library.getBeachEdgeImage(edgeStyle, x, y),
                                    0, 0, null);
                    }
                    int cornerStyle = tile.getStyle() & 15;
                    if (cornerStyle > 0) {
                        g.drawImage(library.getBeachCornerImage(cornerStyle, x, y),
                                    0, 0, null);
                    }
                }

                for (Direction direction : Direction.values()) {
                    Tile borderingTile = tile.getAdjacentTile(direction);
                    if (borderingTile != null) {

                        if (!drawUnexploredBorders && !borderingTile.isExplored() &&
                            (direction == Direction.SE || direction == Direction.S ||
                             direction == Direction.SW)) {
                            continue;
                        }

                        if (tile.getType() == borderingTile.getType()) {
                            // Equal tiles, no need to draw border
                            continue;
                        } else if (tile.isLand() && !borderingTile.isLand()) {
                            // The beach borders are drawn on the side of water tiles only
                            continue;
                        } else if (!tile.isLand() && borderingTile.isLand() && borderingTile.isExplored()) {
                            // If there is a Coast image (eg. beach) defined, use it, otherwise skip
                            // Draw the grass from the neighboring tile, spilling over on the side of this tile
                            g.drawImage(library.getBorderImage(borderingTile.getType(), direction, x, y),
                                        0, 0, null);
                            TileImprovement river = borderingTile.getRiver();
                            if (river != null && river.isConnectedTo(direction.getReverseDirection())) {
                                g.drawImage(library.getRiverMouthImage(direction, borderingTile.getRiver().getMagnitude(), x, y),
                                            0, 0, null);
                            }
                        } else if (borderingTile.isExplored()) {
                            if (library.getTerrainImage(tile.getType(), 0, 0)
                                .equals(library.getTerrainImage(borderingTile.getType(), 0, 0))) {
                                // Do not draw limit between tile that share same graphics (ocean & great river)
                                continue;
                            } else if (borderingTile.getType().getIndex() < tile.getType().getIndex()) {
                                // Draw land terrain with bordering land type, or ocean/high seas limit
                                g.drawImage(library.getBorderImage(borderingTile.getType(), direction,
                                                               x, y), 0, 0, null);
                            }
                        }
                    }
                }
            }
        }
    }



    /**
     * Displays the given Tile onto the given Graphics2D object at the
     * location specified by the coordinates.  Fog of war will be drawn.
     *
     * @param g The <code>Graphics2D</code> object on which to draw
     *     the <code>Tile</code>.
     * @param tile The <code>Tile</code> to draw.
     */
    private void displayFogOfWar(Graphics2D g, Tile tile) {
        if (freeColClient.getGame() != null
            && freeColClient.getGame().getSpecification()
                .getBoolean(GameOptions.FOG_OF_WAR)
            && freeColClient.getMyPlayer() != null
            && !freeColClient.getMyPlayer().canSee(tile)) {
            g.setColor(Color.BLACK);
            Composite oldComposite = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                                      0.2f));
            g.fill(fog);
            g.setComposite(oldComposite);
        }
    }

    /**
     * Describe <code>displayGotoPath</code> method here.
     *
     * @param g a <code>Graphics2D</code> value
     * @param gotoPath a <code>PathNode</code> value
     */
    private void displayGotoPath(Graphics2D g, PathNode gotoPath) {
        final Font font = ResourceManager.getFont("NormalFont", 12f);

        for (PathNode p = gotoPath; p != null; p = p.next) {
            Tile tile = p.getTile();
            if (tile == null) continue;

            Unit show = (activeUnit != null && activeUnit.isNaval()
                && tile.isExplored() && tile.isLand()
                && (tile.getColony() == null
                    || !tile.getColony().getOwner().owns(activeUnit)))
                ? activeUnit.getFirstUnit()
                : activeUnit;
            Image image = null, turns = null;
            if (p.getTurns() == 0) {
                g.setColor(Color.GREEN);
                if (show != null) image = lib.getPathImage(show);
            } else {
                g.setColor(Color.RED);
                image = lib.getPathNextTurnImage(show);
                turns = lib.getStringImage(g, Integer.toString(p.getTurns()), 
                                           Color.WHITE, font);
            }
            Point point = getTilePosition(tile);
            if (point != null) {
                g.translate(point.x, point.y);
                if (image == null) {
                    g.fillOval(halfWidth, halfHeight, 10, 10);
                    g.setColor(Color.BLACK);
                    g.drawOval(halfWidth, halfHeight, 10, 10);
                } else {
                    centerImage(g, image);
                    if (turns != null) centerImage(g, turns);
                }
                g.translate(-point.x, -point.y);
            }
        }
    }


    /**
     * Displays the Map onto the given Graphics2D object.  The Tile at
     * location (x, y) is displayed in the center.
     *
     * @param g The Graphics2D object on which to draw the Map.
     */
    private void displayMap(Graphics2D g) {
        final ClientOptions options = freeColClient.getClientOptions();
        final Player player = freeColClient.getMyPlayer();
        AffineTransform originTransform = g.getTransform();
        Rectangle clipBounds = g.getClipBounds();
        Map map = freeColClient.getGame().getMap();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        /*
        PART 1
        ======
        Position the map if it is not positioned yet.
        */

        repositionMapIfNeeded();

        /*
        PART 1a
        =======
        Determine which tiles need to be redrawn.
        */
        int firstRow = (clipBounds.y - topRowY) / (halfHeight) - 1;
        int clipTopY = topRowY + firstRow * (halfHeight);
        firstRow = topRow + firstRow;

        int firstColumn = (clipBounds.x - leftColumnX) / tileWidth - 1;
        int clipLeftX = leftColumnX + firstColumn * tileWidth;
        firstColumn = leftColumn + firstColumn;

        int lastRow = (clipBounds.y + clipBounds.height - topRowY)
            / (halfHeight);
        lastRow = topRow + lastRow;

        int lastColumn = (clipBounds.x + clipBounds.width - leftColumnX)
            / tileWidth;
        lastColumn = leftColumn + lastColumn;

        /*
        PART 1b
        =======
        Create a GeneralPath to draw the grid with, if needed.
        */
        if (options.getBoolean(ClientOptions.DISPLAY_GRID)) {
            gridPath = new GeneralPath();
            gridPath.moveTo(0, 0);
            int nextX = halfWidth;
            int nextY = -halfHeight;

            for (int i = 0; i <= ((lastColumn - firstColumn) * 2 + 1); i++) {
                gridPath.lineTo(nextX, nextY);
                nextX += halfWidth;
                nextY = (nextY == 0 ? -halfHeight : 0);
            }
        }

        /*
        PART 2
        ======
        Display the Tiles and the Units.
        */

        g.setColor(Color.black);
        g.fillRect(clipBounds.x, clipBounds.y,
                   clipBounds.width, clipBounds.height);

        /*
        PART 2a
        =======
        Display the base Tiles
        */
        g.translate(clipLeftX, clipTopY);
        AffineTransform baseTransform = g.getTransform();
        AffineTransform rowTransform = null;

        // Row per row; start with the top modified row
        for (int row = firstRow; row <= lastRow; row++) {
            rowTransform = g.getTransform();
            if (row % 2 == 1) {
                g.translate(halfWidth, 0);
            }

            // Column per column; start at the left side to display the tiles.
            for (int column = firstColumn; column <= lastColumn; column++) {
                Tile tile = map.getTile(column, row);
                displayBaseTile(g, lib, tile, true);
                g.translate(tileWidth, 0);
            }
            g.setTransform(rowTransform);
            g.translate(0, halfHeight);
        }
        g.setTransform(baseTransform);

        /*
        PART 2b
        =======
        Display the Tile overlays and Units
        */

        List<Unit> units = new ArrayList<Unit>();
        List<AffineTransform> unitTransforms = new ArrayList<AffineTransform>();
        List<Settlement> settlements = new ArrayList<Settlement>();
        List<AffineTransform> settlementTransforms
            = new ArrayList<AffineTransform>();

        int colonyLabels = options.getInteger(ClientOptions.COLONY_LABELS);
        boolean withNumbers = colonyLabels == ClientOptions.COLONY_LABELS_CLASSIC;
        // Row per row; start with the top modified row
        for (int row = firstRow; row <= lastRow; row++) {
            rowTransform = g.getTransform();
            if (row % 2 == 1) {
                g.translate(halfWidth, 0);
            }

            if (options.getBoolean(ClientOptions.DISPLAY_GRID)) {
                // Display the grid.
                g.translate(0, halfHeight);
                g.setStroke(gridStroke);
                g.setColor(Color.BLACK);
                g.draw(gridPath);
                g.translate(0, -halfHeight);
            }

            // Column per column; start at the left side to display the tiles.
            for (int column = firstColumn; column <= lastColumn; column++) {
                Tile tile = map.getTile(column, row);

                // paint full borders
                paintBorders(g, tile, BorderType.COUNTRY, true);
                // Display the Tile overlays:
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                   RenderingHints.VALUE_ANTIALIAS_OFF);
                displayTileOverlays(g, tile, true, withNumbers);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                   RenderingHints.VALUE_ANTIALIAS_ON);
                // paint transparent borders
                paintBorders(g, tile, BorderType.COUNTRY, false);

                if (displayTileCursor(tile)) {
                    drawCursor(g);
                }
                // check for units
                if (tile != null) {
                    Unit unitInFront = getUnitInFront(tile);
                    if (unitInFront != null && !isOutForAnimation(unitInFront)) {
                        units.add(unitInFront);
                        unitTransforms.add(g.getTransform());
                    }
                    // check for settlements
                    Settlement settlement = tile.getSettlement();
                    if (settlement != null) {
                        settlements.add(settlement);
                        settlementTransforms.add(g.getTransform());
                    }
                }
                g.translate(tileWidth, 0);
            }

            g.setTransform(rowTransform);
            g.translate(0, halfHeight);
        }
        g.setTransform(baseTransform);

        /*
        PART 2c
        =======
        Display units
        */
        if (units.size() > 0) {
            g.setColor(Color.BLACK);
            final Image im = lib.getMiscImage(ImageLibrary.DARKNESS);
            for (int index = 0; index < units.size(); index++) {
                final Unit unit = units.get(index);
                g.setTransform(unitTransforms.get(index));
                if (unit.isUndead()) {
                    // display darkness
                    centerImage(g, im);
                }
                displayUnit(g, unit);
            }
            g.setTransform(baseTransform);
        }

        /*
        PART 3
        ======
        Display the colony names.
        */
        if (settlements.size() > 0
            && colonyLabels != ClientOptions.COLONY_LABELS_NONE) {
            for (int index = 0; index < settlements.size(); index++) {
                final Settlement settlement = settlements.get(index);
                if (settlement.isDisposed()) {
                    logger.warning("Settlement display race detected: "
                                   + settlement.getName());
                    continue;
                }
                String name = Messages.message(settlement.getLocationNameFor(player));
                if (name == null) continue;
                Color backgroundColor = lib.getColor(settlement.getOwner());
                Font font = ResourceManager.getFont("NormalFont", 18f);
                Font italicFont = ResourceManager.getFont("NormalFont", Font.ITALIC, 18f);
                Font productionFont = ResourceManager.getFont("NormalFont", 12f);
                // int yOffset = lib.getSettlementImage(settlement).getHeight(null) + 1;
                int yOffset = tileHeight;
                g.setTransform(settlementTransforms.get(index));
                switch (colonyLabels) {
                case ClientOptions.COLONY_LABELS_CLASSIC:
                    Image img = lib.getStringImage(g, name, backgroundColor, font);
                    g.drawImage(img, (tileWidth - img.getWidth(null))/2 + 1,
                                yOffset, null);
                    break;
                case ClientOptions.COLONY_LABELS_MODERN:
                default:
                    backgroundColor = new Color(backgroundColor.getRed(),
                                                backgroundColor.getGreen(),
                                                backgroundColor.getBlue(), 128);
                    TextSpecification[] specs = new TextSpecification[1];
                    if (settlement instanceof Colony
                        && settlement.getOwner() == player) {
                        Colony colony = (Colony) settlement;
                        BuildableType buildable = colony.getCurrentlyBuilding();
                        if (buildable != null) {
                            specs = new TextSpecification[2];
                            String t = Messages.message(buildable.getNameKey())
                                + " " + Messages.getTurnsText(colony.getTurnsToComplete(buildable));
                            specs[1] = new TextSpecification(t, productionFont);
                        }
                    }
                    specs[0] = new TextSpecification(name, font);
                    
                    Image nameImage = createLabel(g, specs, backgroundColor);
                    if (nameImage != null) {
                        int spacing = 3;
                        Image leftImage = null;
                        Image rightImage = null;
                        if (settlement instanceof Colony) {
                            Colony colony = (Colony)settlement;
                            String size = Integer.toString(colony.getDisplayUnitCount());
                            
                            leftImage = createLabel(g, size,
                                ((colony.getPreferredSizeChange() > 0) ? italicFont : font),
                                backgroundColor);
                            if (player.owns(settlement)) {
                                int bonusProduction = colony.getProductionBonus();
                                if (bonusProduction != 0) {
                                    String bonus = (bonusProduction > 0)
                                        ? "+" + bonusProduction
                                        : Integer.toString(bonusProduction);
                                    rightImage = createLabel(g, bonus, font,
                                                             backgroundColor);
                                }
                            }
                        } else if (settlement instanceof IndianSettlement) {
                            IndianSettlement is = (IndianSettlement) settlement;
                            if (is.getType().isCapital()) {
                                leftImage = createCapitalLabel(nameImage.getHeight(null), 5, backgroundColor);
                            }
                            
                            Unit missionary = is.getMissionary();
                            if (missionary != null) {
                                boolean expert = missionary.hasAbility(Ability.EXPERT_MISSIONARY);
                                backgroundColor = lib.getColor(missionary.getOwner());
                                backgroundColor = new Color(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(), 128);
                                rightImage = createReligiousMissionLabel(nameImage.getHeight(null), 5, backgroundColor, expert);
                            }
                        }
                        
                        int width = (int)((nameImage.getWidth(null)
                                * lib.getScalingFactor())
                            + ((leftImage != null)
                                ? (leftImage.getWidth(null)
                                    * lib.getScalingFactor()) + spacing
                                : 0)
                            + ((rightImage != null)
                                ? (rightImage.getWidth(null)
                                    * lib.getScalingFactor()) + spacing
                                : 0));
                        int labelOffset = (tileWidth - width)/2;
                        yOffset -= (nameImage.getHeight(null)
                            * lib.getScalingFactor())/2;
                        if (leftImage != null) {
                            g.drawImage(leftImage, labelOffset, yOffset, null);
                            labelOffset += (leftImage.getWidth(null)
                                * lib.getScalingFactor()) + spacing;
                        }
                        g.drawImage(nameImage, labelOffset, yOffset, null);
                        if (rightImage != null) {
                            labelOffset += (nameImage.getWidth(null)
                                * lib.getScalingFactor()) + spacing;
                            g.drawImage(rightImage, labelOffset, yOffset, null);
                        }
                        break;
                    }
                }
            }
        }
        g.setTransform(originTransform);

        /*
        PART 4
        ======
        Display goto path
        */
        if (currentPath != null)
            displayGotoPath(g, currentPath);
        if (gotoPath != null)
            displayGotoPath(g, gotoPath);

        /*
        PART 5
        ======
        Grey out the map if it is not my turn (and a multiplayer game).
        */
        Canvas canvas = gui.getCanvas();

        if (!freeColClient.isMapEditor()
            && freeColClient.getGame() != null
            && !freeColClient.currentPlayerIsMyPlayer()) {

            if (greyLayer == null) greyLayer = new GrayLayer(lib, freeColClient);
            if (greyLayer.getParent() == null) { // Not added to the canvas yet.
                canvas.addToCanvas(greyLayer, JLayeredPane.DEFAULT_LAYER);
                canvas.moveToFront(greyLayer);
            }

            greyLayer.setBounds(0, 0, canvas.getSize().width, 
                                canvas.getSize().height);
            greyLayer.setPlayer(freeColClient.getGame().getCurrentPlayer());
        } else {
            if (greyLayer != null && greyLayer.getParent() != null) {
                canvas.removeFromCanvas(greyLayer);
            }
        }
        
        /*
        PART 6
        ======
        Display the messages, if there are any.
        */

        if (getMessageCount() > 0) {
            // Don't edit the list of messages while I'm drawing them.
            synchronized (this) {
                Font font = ResourceManager.getFont("NormalFont", 12f);
                GUIMessage message = getMessage(0);
                Image si = lib.getStringImage(g, message.getMessage(),
                                              message.getColor(), font);
                int yy = size.height - 300 - getMessageCount()
                    * si.getHeight(null);
                int xx = 40;

                for (int i = 0; i < getMessageCount(); i++) {
                    message = getMessage(i);
                    g.drawImage(lib.getStringImage(g, message.getMessage(),
                                                   message.getColor(), font),
                                xx, yy, null);
                    yy += si.getHeight(null);
                }
            }
        }

        Image decoration = ResourceManager.getImage("menuborder.shadow.s.image");
        int width = decoration.getWidth(null);
        for (int index = 0; index < size.width; index += width) {
            g.drawImage(decoration, index, 0, null);
        }
        decoration = ResourceManager.getImage("menuborder.shadow.sw.image");
        g.drawImage(decoration, 0, 0, null);
        decoration = ResourceManager.getImage("menuborder.shadow.se.image");
        g.drawImage(decoration, size.width - decoration.getWidth(null), 0, null);

    }

    /**
     * Displays the given Tile onto the given Graphics2D object at the
     * location specified by the coordinates. Show tile names, coordinates
     * and colony values.
     * @param g The Graphics2D object on which to draw the Tile.
     * @param tile The Tile to draw.
     */
    private void displayOptionalValues(Graphics2D g, Tile tile) {
        String text = null;
        switch (freeColClient.getClientOptions().getInteger(ClientOptions.DISPLAY_TILE_TEXT)) {
        case ClientOptions.DISPLAY_TILE_TEXT_NAMES:
            if (tile.getNameKey() != null) {
                text = Messages.message(tile.getNameKey());
            }
            break;
        case ClientOptions.DISPLAY_TILE_TEXT_OWNERS:
            if (tile.getOwner() != null) {
                text = Messages.message(tile.getOwner().getNationName());
            }
            break;
        case ClientOptions.DISPLAY_TILE_TEXT_REGIONS:
            if (tile.getRegion() != null) {
                if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)
                    && tile.getRegion().getName() == null) {
                    text = Utils.lastPart(tile.getRegion().getNameKey(), ".");
                } else {
                    text = Messages.message(tile.getRegion().getLabel());
                }
            }
            paintBorders(g, tile, BorderType.REGION, true);
            break;
        case ClientOptions.DISPLAY_TILE_TEXT_EMPTY:
            break;
        default:
            logger.warning("displayTileText out of range");
            break;
        }

        if (text != null) {
            int b = Messages.getBreakingPoint(text);
            if (b == -1) {
                centerString(g, text);
            } else {
                g.setColor(Color.BLACK);
                g.setFont(ResourceManager.getFont("NormalFont", 12f));
                g.drawString(text.substring(0, b),
                             (tileWidth -
                              g.getFontMetrics().stringWidth(text.substring(0, b)))/2,
                             halfHeight - (g.getFontMetrics().getAscent()*2)/3);
                g.drawString(text.substring(b+1),
                             (tileWidth -
                              g.getFontMetrics().stringWidth(text.substring(b+1)))/2,
                             halfHeight + (g.getFontMetrics().getAscent()*2)/3);
            }
        }

        if (displayCoordinates) {
            String posString = tile.getX() + ", " + tile.getY();
            if (tile.getHighSeasCount() >= 0) {
                posString += "/" + Integer.toString(tile.getHighSeasCount());
            }
            centerString(g, posString);
        }
        if (displayColonyValue && tile.isExplored() && tile.isLand()) {
            String valueString;
            if (displayColonyValuePlayer == null) {
                valueString = Integer.toString(freeColClient.getGame().getCurrentPlayer().getOutpostValue(tile));
            } else {
                valueString = Integer.toString(displayColonyValuePlayer.getColonyValue(tile));
            }
            centerString(g, valueString);
        }
    }

    /**
     * Displays the given Tile onto the given Graphics2D object at the
     * location specified by the coordinates. Settlements and Lost City
     * Rumours will be shown.
     *
     * @param g The Graphics2D object on which to draw the Tile.
     * @param tile The Tile to draw.
     * @param withNumber Whether to display the number of units present.
     */
    private void displaySettlement(Graphics2D g, Tile tile, boolean withNumber) {
        final Settlement settlement = tile.getSettlement();

        if (settlement != null) {
            if (settlement instanceof Colony) {
                Colony colony = (Colony)settlement;

                // Draw image of colony in center of the tile.
                Image colonyImage = lib.getSettlementImage(settlement);
                centerImage(g, colonyImage);

                if (withNumber) {
                    String populationString = Integer.toString(colony.getDisplayUnitCount());
                    int bonus = colony.getProductionBonus();
                    Color theColor = ResourceManager.getProductionColor(bonus);
                    // if government admits even more units, use
                    // italic and bigger number icon
                    Font font = (colony.getPreferredSizeChange() > 0)
                        ? ResourceManager.getFont("SimpleFont", Font.BOLD | Font.ITALIC, 18f)
                        : ResourceManager.getFont("SimpleFont", Font.BOLD, 12f);
                    Image stringImage = lib.getStringImage(g, populationString,
                                                           theColor, font);
                    centerImage(g, stringImage);
                }

            } else if (settlement instanceof IndianSettlement) {
                IndianSettlement is = (IndianSettlement)settlement;
                Image settlementImage = lib.getSettlementImage(settlement);

                // Draw image of indian settlement in center of the tile.
                centerImage(g, settlementImage);

                String text = null;
                Image chip = null;
                Color background = lib.getColor(is.getOwner());
                Color foreground = lib.getForegroundColor(background);
                float xOffset = STATE_OFFSET_X * lib.getScalingFactor();
                float yOffset = STATE_OFFSET_Y * lib.getScalingFactor();
                int colonyLabels = freeColClient.getClientOptions()
                    .getInteger(ClientOptions.COLONY_LABELS);
                if (colonyLabels != ClientOptions.COLONY_LABELS_MODERN) {
                    // Draw the settlement chip
                    chip = lib.getIndianSettlementChip(is,
                        Messages.message("indianSettlement."
                            + ((is.getType().isCapital()) ? "capital"
                                : "normal")));
                    g.drawImage(chip, (int)xOffset, (int)yOffset, null);
                    xOffset += chip.getWidth(null) + 2;

                    // Draw the mission chip if needed.
                    if ((chip = lib.getMissionChip(is)) != null) {
                        g.drawImage(chip, (int)xOffset, (int)yOffset, null);
                        xOffset += chip.getWidth(null) + 2;
                    }
                }

                // Draw the alarm chip if needed.
                Player player = freeColClient.getMyPlayer();
                if (player != null && is.hasContacted(player)) {
                    chip = lib.getAlarmChip(is, player,
                        Messages.message((is.hasScouted(player))
                            ? "indianSettlement.scouted"
                            : "indianSettlement.contacted"));
                    g.drawImage(chip, (int)xOffset, (int)yOffset, null);
                }
            } else {
                logger.warning("Bogus settlement: " + settlement);
            }
        }
    }

    /**
     * Displays the given Tile onto the given Graphics2D object at the
     * location specified by the coordinates.  Addtions and
     * improvements to Tile will be drawn.
     *
     * @param g The Graphics2D object on which to draw the Tile.
     * @param tile The Tile to draw.
     */
    private void displayTileItems(Graphics2D g, Tile tile) {
        // ATTENTION: we assume that only overlays and forests
        // might be taller than a tile.
        if (!tile.isExplored()) {
            g.drawImage(lib.getTerrainImage(null, tile.getX(), tile.getY()), 0, 0, null);
        } else {
            // layer additions and improvements according to zIndex
            List<TileItem> tileItems = new ArrayList<TileItem>();
            if (tile.getTileItemContainer() != null) {
                tileItems = tile.getTileItemContainer().getTileItems();
            }
            int startIndex = 0;
            for (int index = startIndex; index < tileItems.size(); index++) {
                if (tileItems.get(index).getZIndex() < OVERLAY_INDEX) {
                    drawItem(g, tile, tileItems.get(index));
                    startIndex = index + 1;
                } else {
                    startIndex = index;
                    break;
                }
            }
            // Tile Overlays (eg. hills and mountains)
            Image overlayImage = lib.getOverlayImage(tile.getType(), tile.getX(), tile.getY());
            if (overlayImage != null) {
                g.drawImage(overlayImage, 0, (tileHeight - overlayImage.getHeight(null)), null);
            }
            for (int index = startIndex; index < tileItems.size(); index++) {
                if (tileItems.get(index).getZIndex() < FOREST_INDEX) {
                    drawItem(g, tile, tileItems.get(index));
                    startIndex = index + 1;
                } else {
                    startIndex = index;
                    break;
                }
            }
            // Forest
            if (tile.isForested()) {
                Image forestImage = lib.getForestImage(tile.getType(), tile.getRiverStyle());
                // @compat 0.10.6
                // Workaround for BR#3599586.  America_large used to contain
                // tiles with an isolated river (old river style="0"!).
                // There will never be an image for these, so just drop the
                // river style.  The map is now fixed, this is just for the
                // the saved games.
                if (forestImage == null) forestImage = lib.getForestImage(tile.getType(), null);
                // @end compatibility code
                g.drawImage(forestImage, 0, (tileHeight - forestImage.getHeight(null)), null);
            }

            // draw all remaining items
            for (int index = startIndex; index < tileItems.size(); index++) {
                drawItem(g, tile, tileItems.get(index));
            }
        }
    }

    /**
     * Displays the given Tile onto the given Graphics2D object at the
     * location specified by the coordinates.  Everything located on
     * the Tile will also be drawn except for units because their
     * image can be larger than a Tile.
     *
     * @param g The Graphics2D object on which to draw the Tile.
     * @param tile The Tile to draw.
     * @param drawUnexploredBorders If true; draws border between explored and
     *     unexplored terrain.
     * @param withNumber indicates if the number of inhabitants should
     *     be drawn too.
     */
    private void displayTileOverlays(Graphics2D g, Tile tile,
                                     boolean drawUnexploredBorders,
                                     boolean withNumber) {
        if (tile != null && tile.isExplored()) {
            if (drawUnexploredBorders) {
                for (Direction direction : Direction.values()) {
                    Tile borderingTile = tile.getAdjacentTile(direction);
                    if (borderingTile != null && !borderingTile.isExplored()) {
                        g.drawImage(lib.getBorderImage(null, direction,
                                tile.getX(), tile.getY()), 0, 0, null);
                    }
                }
            }
            displayTileItems(g, tile);
            displaySettlement(g, tile, withNumber);
            displayFogOfWar(g, tile);
            displayOptionalValues(g, tile);
        }
    }

    /**
     * Displays the given Unit onto the given Graphics2D object at the
     * location specified by the coordinates.
     * @param g The Graphics2D object on which to draw the Unit.
     * @param unit The Unit to draw.
     */
    private void displayUnit(Graphics2D g, Unit unit) {
        final Player player = freeColClient.getMyPlayer();
 
        try {
            // Draw the 'selected unit' image if needed.
            //if ((unit == getActiveUnit()) && cursor) {
            if (displayUnitCursor(unit)) {
                drawCursor(g);
            }

            // Draw the unit.
            // If unit is sentry, draw in grayscale
            boolean fade = (unit.getState() == Unit.UnitState.SENTRY)
                || (unit.getTile() != null
                    && player != null
                    && !player.canSee(unit.getTile()));
            Image image = lib.getUnitImageIcon(unit, fade).getImage();
            Point p = getUnitImagePositionInTile(image);
            g.drawImage(image, p.x, p.y, null);

            // Draw an occupation and nation indicator.
            boolean owned = player != null && player.owns(unit);
            String text = Messages.message(unit.getOccupationKey(owned));
            g.drawImage(lib.getOccupationIndicatorChip(unit, text),
                        (int)(STATE_OFFSET_X * lib.getScalingFactor()), 0,
                        null);

            // Draw one small line for each additional unit (like in civ3).
            int unitsOnTile = 0;
            if (unit.getTile() != null) {
                // When a unit is moving from tile to tile, it is
                // removed from the source tile.  So the unit stack
                // indicator cannot be drawn during the movement see
                // UnitMoveAnimation.animate() for details
                unitsOnTile = unit.getTile().getTotalUnitCount();
            }
            if (unitsOnTile > 1) {
                g.setColor(Color.WHITE);
                int unitLinesY = OTHER_UNITS_OFFSET_Y;
                int x1 = (int)((STATE_OFFSET_X + OTHER_UNITS_OFFSET_X)
                    * lib.getScalingFactor());
                int x2 = (int)((STATE_OFFSET_X + OTHER_UNITS_OFFSET_X
                        + OTHER_UNITS_WIDTH) * lib.getScalingFactor());
                for (int i = 0; i < unitsOnTile && i < MAX_OTHER_UNITS; i++) {
                    g.drawLine(x1, unitLinesY, x2, unitLinesY);
                    unitLinesY += 2;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "displayUnit " + unit.toString(), e);
        }

        // FOR DEBUGGING
        net.sf.freecol.server.ai.AIUnit au;
        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)
            && player != null
            && !player.owns(unit)
            && unit.getOwner().isAI()
            && freeColClient.getFreeColServer() != null
            && (au = freeColClient.getFreeColServer().getAIMain()
                .getAIUnit(unit)) != null) {
            if (debugShowMission) {
                g.setColor(Color.WHITE);
                g.drawString((!au.hasMission()) ? "No mission"
                    : Utils.lastPart(au.getMission().getClass().toString(), "."),
                    0, 0);
            }
            if (debugShowMissionInfo && au.hasMission()) {
                g.setColor(Color.WHITE);
                g.drawString(au.getMission().toString(), 0, 25);
            }
        }
    }

    /**
     * Describe <code>drawCursor</code> method here.
     *
     * @param g a <code>Graphics2D</code> value
     */
    private void drawCursor(Graphics2D g) {
        g.drawImage(cursorImage, 0, 0, null);
    }


    /**
     * Draws the given TileItem on the given Tile.
     *
     * @param g a <code>Graphics2D</code> value
     * @param tile a <code>Tile</code> value
     * @param item a <code>TileItem</code> value
     */
    private void drawItem(Graphics2D g, Tile tile, TileItem item) {

        if (item instanceof Resource) {
            Image bonusImage = lib.getBonusImage(((Resource) item).getType());
            if (bonusImage != null) {
                centerImage(g, bonusImage);
            }
        } else if (item instanceof LostCityRumour) {
            centerImage(g, lib.getMiscImage(ImageLibrary.LOST_CITY_RUMOUR));
        } else {
            TileImprovement ti = (TileImprovement)item;
            if (ti.isComplete()) {
                String key = ti.getType().getId() + ".image";
                if (ResourceManager.hasResource(key)) {
                    // Has its own Overlay Image in Misc, use it
                    Image overlay = ResourceManager.getImage(key,
                        lib.getScalingFactor());
                    g.drawImage(overlay, 0, 0, null);
                } else if (ti.isRiver()
                    && ti.getMagnitude() < TileImprovement.FJORD_RIVER) {
                    g.drawImage(lib.getRiverImage(ti.getStyle()), 0, 0, null);
                } else if (ti.isRoad()) {
                    drawRoad(g, tile);
                }
            }
        }
    }


    /**
     * Draws all roads on the given Tile.
     *
     * @param g The <code>Graphics</code> to draw the road upon.
     * @param tile a <code>Tile</code> value
     */
    private void drawRoad(Graphics2D g, Tile tile) {

        Color oldColor = g.getColor();
        g.setColor(ResourceManager.getColor("road.color"));
        g.setStroke(roadStroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GeneralPath path = new GeneralPath();
        List<Point2D.Float> points = new ArrayList<Point2D.Float>(8);
        List<Direction> directions = new ArrayList<Direction>(8);
        for (Direction direction : Direction.values()) {
            Tile borderingTile = tile.getAdjacentTile(direction);
            TileImprovement r;
            if (borderingTile != null
                && (r = borderingTile.getRoad()) != null
                && r.isComplete()) {
                points.add(corners.get(direction));
                directions.add(direction);
            }
        }

        switch(points.size()) {
        case 0:
            path.moveTo(0.35f * tileWidth, 0.35f * tileHeight);
            path.lineTo(0.65f * tileWidth, 0.65f * tileHeight);
            path.moveTo(0.35f * tileWidth, 0.65f * tileHeight);
            path.lineTo(0.65f * tileWidth, 0.35f * tileHeight);
            break;
        case 1:
            path.moveTo(halfWidth, halfHeight);
            path.lineTo(points.get(0).getX(), points.get(0).getY());
            break;
        case 2:
            path.moveTo(points.get(0).getX(), points.get(0).getY());
            path.quadTo(halfWidth, halfHeight, points.get(1).getX(), points.get(1).getY());
            break;
        case 3:
        case 4: {
            Direction pen = directions.get(directions.size() - 1);
            Point2D p = corners.get(pen);
            path.moveTo(p.getX(), p.getY());
            for (Direction d : directions) {
                p = corners.get(d);
                if(prohibitedRoads.get(pen).contains(d)) {
                    path.moveTo(p.getX(), p.getY());
                } else {
                    path.quadTo(halfWidth, halfHeight, p.getX(), p.getY());
                }
                pen = d;
            }
            break;
        }
        default:
            for (Point2D p : points) {
                path.moveTo(halfWidth, halfHeight);
                path.lineTo(p.getX(), p.getY());
            }
        }
        g.draw(path);
        g.setColor(oldColor);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }


    /**
     * Describe <code>enterUnitOutForAnimation</code> method here.
     *
     * @param unit an <code>Unit</code> value
     * @param sourceTile a <code>Tile</code> value
     * @return a <code>JLabel</code> value
     */
    private JLabel enterUnitOutForAnimation(final Unit unit, final Tile sourceTile) {
        Integer i = unitsOutForAnimation.get(unit);
        if (i == null) {
            final JLabel unitLabel = getUnitLabel(unit);
            final Integer UNIT_LABEL_LAYER = JLayeredPane.DEFAULT_LAYER;

            i = 1;
            unitLabel.setLocation(getUnitLabelPositionInTile(unitLabel,
                    getTilePosition(sourceTile)));
            unitsOutForAnimationLabels.put(unit, unitLabel);
            gui.getCanvas().addToCanvas(unitLabel, UNIT_LABEL_LAYER);
        } else {
            i++;
        }
        unitsOutForAnimation.put(unit, i);
        return unitsOutForAnimationLabels.get(unit);
    }

    /**
     * Returns the amount of columns that are to the left of the Tile
     * that is displayed in the center of the Map.
     * @return The amount of columns that are to the left of the Tile
     * that is displayed in the center of the Map.
     */
    private int getLeftColumns() {
        return getLeftColumns(focus.getY());
    }

    /**
     * Returns the amount of columns that are to the left of the Tile
     * with the given y-coordinate.
     * @param y The y-coordinate of the Tile in question.
     * @return The amount of columns that are to the left of the Tile
     * with the given y-coordinate.
     */
    private int getLeftColumns(int y) {
        int leftColumns = leftSpace / tileWidth + 1;

        if ((y % 2) == 0) {
            if ((leftSpace % tileWidth) > 32) {
                leftColumns++;
            }
        } else {
            if ((leftSpace % tileWidth) == 0) {
                leftColumns--;
            }
        }

        return leftColumns;
    }

    /**
     * Gets the message at position 'index'. The message at position 0 is the oldest
     * message and is most likely to be removed during the next call of removeOldMessages().
     * The higher the index of a message, the more recently it was added.
     *
     * @param index The index of the message to return.
     * @return The message at position 'index'.
     */
    private GUIMessage getMessage(int index) {
        return messages.get(index);
    }

    /**
     * Gets the amount of message that are currently being displayed on this GUI.
     * @return The amount of message that are currently being displayed on this GUI.
     */
    private int getMessageCount() {
        return messages.size();
    }



    /**
     * Returns the amount of columns that are to the right of the Tile
     * that is displayed in the center of the Map.
     * @return The amount of columns that are to the right of the Tile
     * that is displayed in the center of the Map.
     */
    private int getRightColumns() {
        return getRightColumns(focus.getY());
    }

    /**
     * Returns the amount of columns that are to the right of the Tile
     * with the given y-coordinate.
     * @param y The y-coordinate of the Tile in question.
     * @return The amount of columns that are to the right of the Tile
     * with the given y-coordinate.
     */
    private int getRightColumns(int y) {
        int rightColumns = rightSpace / tileWidth + 1;

        if ((y % 2) == 0) {
            if ((rightSpace % tileWidth) == 0) {
                rightColumns--;
            }
        } else {
            if ((rightSpace % tileWidth) > 32) {
                rightColumns++;
            }
        }

        return rightColumns;
    }

    /**
     * Gets the coordinates to draw a unit in a given tile.
     * @param unitImage The unit's image
     * @return The coordinates where the unit should be drawn onscreen
     */
    private Point getUnitImagePositionInTile(Image unitImage) {
        int unitX = (tileWidth - unitImage.getWidth(null)) / 2;
        int unitY = (tileHeight - unitImage.getHeight(null)) / 2 -
                    (int) (UNIT_OFFSET * lib.getScalingFactor());

        return new Point(unitX, unitY);
    }

    /**
    * Gets the unit that should be displayed on the given tile.
    *
    * @param unitTile The <code>Tile</code>.
    * @return The <code>Unit</code> or <i>null</i> if no unit applies.
    */
    private Unit getUnitInFront(Tile unitTile) {
        if (unitTile == null || unitTile.getUnitCount() <= 0) {
            return null;
        }

        if (activeUnit != null && activeUnit.getTile() == unitTile) {
            return activeUnit;
        } else {
            if (unitTile.getSettlement() == null) {
                Unit bestDefendingUnit = null;
                if (activeUnit != null) {
                    bestDefendingUnit = unitTile.getDefendingUnit(activeUnit);
                    if (bestDefendingUnit != null) {
                        return bestDefendingUnit;
                    }
                }

                Unit movableUnit = unitTile.getMovableUnit();
                if (movableUnit != null && movableUnit.getLocation() == movableUnit.getTile()) {
                    return movableUnit;
                } else {
                    Unit bestPick = null;
                    Iterator<Unit> unitIterator = unitTile.getUnitIterator();
                    while (unitIterator.hasNext()) {
                        Unit u = unitIterator.next();
                        if (bestPick == null || bestPick.getMovesLeft() < u.getMovesLeft()) {
                            bestPick = u;
                        }
                    }

                    return bestPick;
                }
            } else {
                return null;
            }
        }
    }

    /**
     * Draw the unit's image and occupation indicator in one JLabel object.
     * @param unit The unit to be drawn
     * @return A JLabel object with the unit's image.
     */
    private JLabel getUnitLabel(Unit unit) {
        final Image unitImg = lib.getUnitImageIcon(unit).getImage();

        final int width = halfWidth + unitImg.getWidth(null)/2;
        final int height = unitImg.getHeight(null);

        BufferedImage img = new BufferedImage(width, height,
                                              BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.getGraphics();

        final int unitX = (width - unitImg.getWidth(null)) / 2;
        g.drawImage(unitImg, unitX, 0, null);

        String text = Messages.message(unit.getOccupationKey(true));
        g.drawImage(lib.getOccupationIndicatorChip(unit, text), 0, 0, null);

        final JLabel label = new JLabel(new ImageIcon(img));
        label.setSize(width, height);
        return label;
    }

    /**
     * Is a y-coordinate near the bottom?
     *
     * @param y The y-coordinate.
     * @return True if near the bottom.
     */
    private boolean isMapNearBottom(int y) {
        return y >= freeColClient.getGame().getMap().getHeight() - bottomRows;
    }

    /**
     * Is an x,y coordinate near the left?
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @return True if near the left.
     */
    private boolean isMapNearLeft(int x, int y) {
        return x < getLeftColumns(y);
    }

    /**
     * Is an x,y coordinate near the right?
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @return True if near the right.
     */
    private boolean isMapNearRight(int x, int y) {
        return x >= freeColClient.getGame().getMap().getWidth()
            - getRightColumns(y);
    }

    /**
     * Is a y-coordinate near the top?
     *
     * @param y The y-coordinate.
     * @return True if near the top.
     */
    private boolean isMapNearTop(int y) {
        return y < topRows;
    }


    /**
     * Returns true if the given Unit is being animated.
     *
     * @param unit an <code>Unit</code> value
     * @return a <code>boolean</code> value
     */
    private boolean isOutForAnimation(final Unit unit) {
        return unitsOutForAnimation.containsKey(unit);
    }


    private boolean isTileVisible(Tile tile) {
        return tile.getY() >= topRow && tile.getY() <= bottomRow
            && tile.getX() >= leftColumn && tile.getX() <= rightColumn;
    }


    private boolean noActiveUnitIsAt(Tile tile) {
        return activeUnit == null || activeUnit.getTile() != tile;
    }


    /**
     * Draws the borders of a territory on the given Tile. The
     * territory is either a country or a region.
     *
     * @param g a <code>Graphics2D</code> value
     * @param tile a <code>Tile</code> value
     * @param type a <code>BorderType</code> value
     * @param opaque a <code>boolean</code> value
     */
    private void paintBorders(Graphics2D g, Tile tile, BorderType type, boolean opaque) {
        if (tile == null ||
            (type == BorderType.COUNTRY
             && !freeColClient.getClientOptions().getBoolean(ClientOptions.DISPLAY_BORDERS))) {
            return;
        }
        Player owner = tile.getOwner();
        Region region = tile.getRegion();
        if ((type == BorderType.COUNTRY && owner != null)
            || (type == BorderType.REGION && region != null)) {
            Stroke oldStroke = g.getStroke();
            g.setStroke(borderStroke);
            Color oldColor = g.getColor();
            Color newColor = Color.WHITE;
            if (type == BorderType.COUNTRY) {
                newColor = new Color(lib.getColor(owner).getRed(),
                                     lib.getColor(owner).getGreen(),
                                     lib.getColor(owner).getBlue(),
                                     opaque ? 255 : 100);
            }
            g.setColor(newColor);
            GeneralPath path = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
            path.moveTo(borderPoints.get(Direction.longSides[0]).x,
                        borderPoints.get(Direction.longSides[0]).y);
            for (Direction d : Direction.longSides) {
                Tile otherTile = tile.getNeighbourOrNull(d);
                Direction next = d.getNextDirection();
                Direction next2 = next.getNextDirection();
                if (otherTile == null
                    || (type == BorderType.COUNTRY && !owner.owns(otherTile))
                    || (type == BorderType.REGION && otherTile.getRegion() != region)) {
                    Tile tile1 = tile.getNeighbourOrNull(next);
                    Tile tile2 = tile.getNeighbourOrNull(next2);
                    if (tile2 == null
                        || (type == BorderType.COUNTRY && !owner.owns(tile2))
                        || (type == BorderType.REGION && tile2.getRegion() != region)) {
                        // small corner
                        path.lineTo(borderPoints.get(next).x,
                                    borderPoints.get(next).y);
                        path.quadTo(controlPoints.get(next).x,
                                    controlPoints.get(next).y,
                                    borderPoints.get(next2).x,
                                    borderPoints.get(next2).y);
                    } else {
                        int dx = 0, dy = 0;
                        switch(d) {
                        case NW: dx = halfWidth; dy = -halfHeight; break;
                        case NE: dx = halfWidth; dy = halfHeight; break;
                        case SE: dx = -halfWidth; dy = halfHeight; break;
                        case SW: dx = -halfWidth; dy = -halfHeight; break;
                        }
                        if (tile1 != null
                            && ((type == BorderType.COUNTRY && owner.owns(tile1))
                                || (type == BorderType.REGION && tile1.getRegion() == region))) {
                            // short straight line
                            path.lineTo(borderPoints.get(next).x,
                                        borderPoints.get(next).y);
                            // big corner
                            Direction previous = d.getPreviousDirection();
                            Direction previous2 = previous.getPreviousDirection();
                            int ddx = 0, ddy = 0;
                            switch(d) {
                            case NW: ddy = -tileHeight; break;
                            case NE: ddx = tileWidth; break;
                            case SE: ddy = tileHeight; break;
                            case SW: ddx = -tileWidth; break;
                            }
                            path.quadTo(controlPoints.get(previous).x + dx,
                                        controlPoints.get(previous).y + dy,
                                        borderPoints.get(previous2).x + ddx,
                                        borderPoints.get(previous2).y + ddy);
                        } else {
                            // straight line
                            path.lineTo(borderPoints.get(d).x + dx,
                                        borderPoints.get(d).y + dy);
                        }
                    }
                } else {
                    path.moveTo(borderPoints.get(next2).x,
                                borderPoints.get(next2).y);
                }
            }
            g.draw(path);
            g.setColor(oldColor);
            g.setStroke(oldStroke);
        }
    }

    /**
     * Position the map so that the supplied location is
     * displayed at the center.
     *
     * @param pos The position to center at.
     */
    private void positionMap(Tile pos) {
        Game gameData = freeColClient.getGame();

        int x = pos.getX(),
            y = pos.getY();
        int leftColumns = getLeftColumns(),
            rightColumns = getRightColumns();

        /*
        PART 1
        ======
        Calculate: bottomRow, topRow, bottomRowY, topRowY
        This will tell us which rows need to be drawn on the screen (from
        bottomRow until and including topRow).
        bottomRowY will tell us at which height the bottom row needs to be
        drawn.
        */

        if (y < topRows) {
            // We are at the top of the map
            bottomRow = (size.height / (halfHeight)) - 1;
            if ((size.height % (halfHeight)) != 0) {
                bottomRow++;
            }
            topRow = 0;
            bottomRowY = bottomRow * (halfHeight);
            topRowY = 0;
        } else if (y >= (gameData.getMap().getHeight() - bottomRows)) {
            // We are at the bottom of the map
            bottomRow = gameData.getMap().getHeight() - 1;

            topRow = size.height / (halfHeight);
            if ((size.height % (halfHeight)) > 0) {
                topRow++;
            }
            topRow = gameData.getMap().getHeight() - topRow;

            bottomRowY = size.height - tileHeight;
            topRowY = bottomRowY - (bottomRow - topRow) * (halfHeight);
        } else {
            // We are not at the top of the map and not at the bottom
            bottomRow = y + bottomRows;
            topRow = y - topRows;
            bottomRowY = topSpace + (halfHeight) * bottomRows;
            topRowY = topSpace - topRows * (halfHeight);
        }

        /*
        PART 2
        ======
        Calculate: leftColumn, rightColumn, leftColumnX
        This will tell us which columns need to be drawn on the screen (from
        leftColumn until and including rightColumn).
        leftColumnX will tell us at which x-coordinate the left column needs
        to be drawn (this is for the Tiles where y%2==0; the others should be
        halfWidth more to the right).
        */

        if (x < leftColumns) {
            // We are at the left side of the map
            leftColumn = 0;

            rightColumn = size.width / tileWidth - 1;
            if ((size.width % tileWidth) > 0) {
                rightColumn++;
            }

            leftColumnX = 0;
        } else if (x >= (gameData.getMap().getWidth() - rightColumns)) {
            // We are at the right side of the map
            rightColumn = gameData.getMap().getWidth() - 1;

            leftColumn = size.width / tileWidth;
            if ((size.width % tileWidth) > 0) {
                leftColumn++;
            }

            leftColumnX = size.width - tileWidth - halfWidth -
                leftColumn * tileWidth;
            leftColumn = rightColumn - leftColumn;
        } else {
            // We are not at the left side of the map and not at the right side
            leftColumn = x - leftColumns;
            rightColumn = x + rightColumns;
            leftColumnX = (size.width - tileWidth) / 2 - leftColumns * tileWidth;
        }
    }


    /**
     * Describe <code>releaseUnitOutForAnimation</code> method here.
     *
     * @param unit an <code>Unit</code> value
     */
    private void releaseUnitOutForAnimation(final Unit unit) {
        Integer i = unitsOutForAnimation.get(unit);
        if (i == null) {
            throw new IllegalStateException("Tried to release unit that was not out for animation");
        }
        if (i == 1) {
            unitsOutForAnimation.remove(unit);
            gui.getCanvas().removeFromCanvas(unitsOutForAnimationLabels.remove(unit));
        } else {
            i--;
            unitsOutForAnimation.put(unit, i);
        }
    }

    /**
     * Removes all the message that are older than MESSAGE_AGE.
     * @return 'true' if at least one message has been removed, 'false' otherwise.
     * This can be useful to see if it is necessary to refresh the screen.
     */
    private synchronized boolean removeOldMessages() {
        long currentTime = new Date().getTime();
        boolean result = false;

        int i = 0;
        while (i < getMessageCount()) {
            long messageCreationTime = getMessage(i).getCreationTime().getTime();
            if ((currentTime - messageCreationTime) >= MESSAGE_AGE) {
                result = true;
                messages.remove(i);
            } else {
                i++;
            }
        }

        return result;
    }


    private void repositionMapIfNeeded() {
        if (bottomRow < 0 && focus != null)
            positionMap(focus);
    }


    /**
     * Sets the ImageLibrary and calculates various items that depend
     * on tile size.
     *
     * @param lib an <code>ImageLibrary</code> value
     */
    private void setImageLibrary(ImageLibrary lib) {
        this.lib = lib;
        cursorImage = lib.getMiscImage(ImageLibrary.UNIT_SELECT);
        // ATTENTION: we assume that all base tiles have the same size
        Image unexplored = lib.getTerrainImage(null, 0, 0);
        tileHeight = unexplored.getHeight(null);
        halfHeight = tileHeight/2;
        tileWidth = unexplored.getWidth(null);
        halfWidth = tileWidth/2;

        int dx = tileWidth/16;
        int dy = tileHeight/16;
        int ddx = dx + dx/2;
        int ddy = dy + dy/2;

        // corners
        corners.put(Direction.N,  new Point2D.Float(halfWidth, 0));
        corners.put(Direction.NE, new Point2D.Float(0.75f * tileWidth, 0.25f * tileHeight));
        corners.put(Direction.E,  new Point2D.Float(tileWidth, halfHeight));
        corners.put(Direction.SE, new Point2D.Float(0.75f * tileWidth, 0.75f * tileHeight));
        corners.put(Direction.S,  new Point2D.Float(halfWidth, tileHeight));
        corners.put(Direction.SW, new Point2D.Float(0.25f * tileWidth, 0.75f * tileHeight));
        corners.put(Direction.W,  new Point2D.Float(0, halfHeight));
        corners.put(Direction.NW, new Point2D.Float(0.25f * tileWidth, 0.25f * tileHeight));

        // small corners
        controlPoints.put(Direction.N, new Point2D.Float(halfWidth, dy));
        controlPoints.put(Direction.E, new Point2D.Float(tileWidth - dx, halfHeight));
        controlPoints.put(Direction.S, new Point2D.Float(halfWidth, tileHeight - dy));
        controlPoints.put(Direction.W, new Point2D.Float(dx, halfHeight));
        // big corners
        controlPoints.put(Direction.SE, new Point2D.Float(halfWidth, tileHeight));
        controlPoints.put(Direction.NE, new Point2D.Float(tileWidth, halfHeight));
        controlPoints.put(Direction.SW, new Point2D.Float(0, halfHeight));
        controlPoints.put(Direction.NW, new Point2D.Float(halfWidth, 0));
        // small corners
        borderPoints.put(Direction.NW, new Point2D.Float(dx + ddx, halfHeight - ddy));
        borderPoints.put(Direction.N,  new Point2D.Float(halfWidth - ddx, dy + ddy));
        borderPoints.put(Direction.NE, new Point2D.Float(halfWidth + ddx, dy + ddy));
        borderPoints.put(Direction.E,  new Point2D.Float(tileWidth - dx - ddx, halfHeight - ddy));
        borderPoints.put(Direction.SE, new Point2D.Float(tileWidth - dx - ddx, halfHeight + ddy));
        borderPoints.put(Direction.S,  new Point2D.Float(halfWidth + ddx, tileHeight - dy - ddy));
        borderPoints.put(Direction.SW, new Point2D.Float(halfWidth - ddx, tileHeight - dy - ddy));
        borderPoints.put(Direction.W,  new Point2D.Float(dx + ddx, halfHeight + ddy));

        // road pairs to skip drawing when doing 3 or 4 exit point tiles
        //  don't put more than two directions in each list,
        //  otherwise a 3-point tile may not draw any roads at all!
        prohibitedRoads.put(Direction.N,  Arrays.asList(Direction.NW, Direction.NE));
        prohibitedRoads.put(Direction.NE, Arrays.asList(Direction.N, Direction.E));
        prohibitedRoads.put(Direction.E,  Arrays.asList(Direction.NE, Direction.SE));
        prohibitedRoads.put(Direction.SE, Arrays.asList(Direction.E, Direction.S));
        prohibitedRoads.put(Direction.S,  Arrays.asList(Direction.SE, Direction.SW));
        prohibitedRoads.put(Direction.SW, Arrays.asList(Direction.S, Direction.W));
        prohibitedRoads.put(Direction.W,  Arrays.asList(Direction.SW, Direction.NW));
        prohibitedRoads.put(Direction.NW, Arrays.asList(Direction.W, Direction.N));


        borderStroke = new BasicStroke(dy);
        roadStroke = new BasicStroke(dy/2);
        gridStroke = new BasicStroke(lib.getScalingFactor());

        fog.reset();
        fog.moveTo(halfWidth, 0);
        fog.lineTo(tileWidth, halfHeight);
        fog.lineTo(halfWidth, tileHeight);
        fog.lineTo(0, halfHeight);
        fog.closePath();

        updateMapDisplayVariables();
    }

    /**
     * Describe <code>updateMapDisplayVariables</code> method here.
     *
     */
    private void updateMapDisplayVariables() {
        // Calculate the amount of rows that will be drawn above the central Tile
        topSpace = (size.height - tileHeight) / 2;
        if ((topSpace % (halfHeight)) != 0) {
            topRows = topSpace / (halfHeight) + 2;
        } else {
            topRows = topSpace / (halfHeight) + 1;
        }
        bottomRows = topRows;
        leftSpace = (size.width - tileWidth) / 2;
        rightSpace = leftSpace;
    }


    public void toggleViewMode() {
        logger.warning("Changing view");
        changeViewMode(1 - currentMode);
    }

    public void changeViewMode(int newViewMode) {

        if (newViewMode == currentMode) {
            logger.warning("Trying to change to the same view mode");
            return;
        }

        currentMode = newViewMode;

        switch (currentMode) {
        case GUI.MOVE_UNITS_MODE:
            if (getActiveUnit() == null) {
                setActiveUnit(savedActiveUnit);
            }
            savedActiveUnit = null;
            logger.warning("Change view to Move Units Mode");
            break;
        case GUI.VIEW_TERRAIN_MODE:
            savedActiveUnit = activeUnit;
            setActiveUnit(null);
            logger.warning("Change view to View Terrain Mode");
            break;
        }
    }

    public int getView() {
        return currentMode;
    }

    public boolean displayTileCursor(Tile tile) {
        return (currentMode == GUI.VIEW_TERRAIN_MODE) && 
            tile != null &&
            tile.equals(selectedTile);
    }

    public boolean displayUnitCursor(Unit unit) {
        return (currentMode == GUI.MOVE_UNITS_MODE) &&
            (unit == activeUnit) && 
            (cursor.isActive() || (unit.getMovesLeft() == 0)) ;
    }
    

}
