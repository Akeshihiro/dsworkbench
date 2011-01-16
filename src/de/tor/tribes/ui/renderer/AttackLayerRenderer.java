/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.tor.tribes.ui.renderer;

import de.tor.tribes.io.DataHolder;
import de.tor.tribes.io.UnitHolder;
import de.tor.tribes.types.Attack;
import de.tor.tribes.types.Village;
import de.tor.tribes.ui.ImageManager;
import de.tor.tribes.ui.TwoD.ShapeStroke;
import de.tor.tribes.util.DSCalculator;
import de.tor.tribes.util.GlobalOptions;
import de.tor.tribes.util.attack.AttackManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import javax.swing.ImageIcon;

/**
 *
 * @author Torridity
 */
public class AttackLayerRenderer extends AbstractDirectLayerRenderer {

    @Override
    public void performRendering(RenderSettings pSettings, Graphics2D pG2d) {
        if (!pSettings.isLayerVisible()) {
            return;
        }
        Point2D.Double mapPos = new Point2D.Double(pSettings.getMapBounds().getX(), pSettings.getMapBounds().getY());
        Stroke s = pG2d.getStroke();
        pG2d.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        renderAttacks(mapPos, pSettings, pG2d);
        pG2d.setStroke(s);
    }

    private void renderAttacks(Point2D.Double viewStartPoint, RenderSettings pSettings, Graphics2D pG2D) {
        HashMap<String, Color> attackColors = new HashMap<String, Color>();
        for (UnitHolder unit : DataHolder.getSingleton().getUnits()) {
            Color unitColor = Color.RED;
            try {
                unitColor = Color.decode(GlobalOptions.getProperty(unit.getName() + ".color"));
            } catch (Exception e) {
                unitColor = Color.RED;
            }
            attackColors.put(unit.getName(), unitColor);
        }

        GeneralPath p = new GeneralPath();
        p.moveTo(0, 0);
        p.lineTo(10, 5);
        p.lineTo(0, 10);
        p.lineTo(0, 0);
        ShapeStroke stroke_attack = new ShapeStroke(
                new Shape[]{
                    p,
                    new Rectangle2D.Float(0, 0, 10, 2)
                },
                10.0f);

        p = new GeneralPath();
        p.moveTo(0, 0);
        p.lineTo(5, 3);
        p.lineTo(0, 6);
        p.lineTo(0, 0);
        ShapeStroke stroke_fake = new ShapeStroke(
                new Shape[]{
                    p,
                    new Rectangle2D.Float(0, 0, 10, 2)
                },
                20.0f);
        Enumeration<String> keys = AttackManager.getSingleton().getPlans();
        boolean showBarbarian = true;
        try {
            showBarbarian = Boolean.parseBoolean(GlobalOptions.getProperty("show.barbarian"));
        } catch (Exception e) {
            showBarbarian = true;
        }

        boolean markedOnly = false;
        try {
            markedOnly = Boolean.parseBoolean(GlobalOptions.getProperty("draw.marked.only"));
        } catch (Exception e) {
            markedOnly = false;
        }
        while (keys.hasMoreElements()) {
            String plan = keys.nextElement();
            Attack[] attacks = AttackManager.getSingleton().getAttackPlan(plan).toArray(new Attack[]{});
            for (Attack attack : attacks) {
                //go through all attacks
                //render if shown on map or if either source or target are visible
                if (attack.isShowOnMap() && (attack.getSource().isVisibleOnMap() || attack.getTarget().isVisibleOnMap())) {
                    //only enter if attack should be visible
                    //get line for this attack
                    Line2D.Double attackLine = new Line2D.Double(attack.getSource().getX(), attack.getSource().getY(), attack.getTarget().getX(), attack.getTarget().getY());
                    String value = GlobalOptions.getProperty("attack.movement");
                    boolean showAttackMovement = (value == null) ? false : Boolean.parseBoolean(value);
                    double xStart = (attackLine.getX1() - viewStartPoint.x) * pSettings.getFieldWidth() + pSettings.getFieldWidth() / 2;
                    double yStart = (attackLine.getY1() - viewStartPoint.y) * pSettings.getFieldHeight() + pSettings.getFieldHeight() / 2;
                    double xEnd = (attackLine.getX2() - viewStartPoint.x) * pSettings.getFieldWidth() + pSettings.getFieldWidth() / 2;
                    double yEnd = (attackLine.getY2() - viewStartPoint.y) * pSettings.getFieldHeight() + pSettings.getFieldHeight() / 2;
                    ImageIcon unitIcon = null;
                    int unitXPos = 0;
                    int unitYPos = 0;
                    if (showAttackMovement) {
                        unitIcon = ImageManager.getUnitIcon(attack.getUnit());
                        if (unitIcon != null) {
                            long dur = (long) (DSCalculator.calculateMoveTimeInSeconds(attack.getSource(), attack.getTarget(), attack.getUnit().getSpeed()) * 1000);
                            long arrive = attack.getArriveTime().getTime();
                            long start = arrive - dur;
                            long current = System.currentTimeMillis();

                            if ((start < current) && (arrive > current)) {
                                //attack running
                                long runTime = System.currentTimeMillis() - start;
                                double perc = 100 * runTime / dur;
                                perc /= 100;
                                double xTar = xStart + (xEnd - xStart) * perc;
                                double yTar = yStart + (yEnd - yStart) * perc;
                                unitXPos = (int) xTar - unitIcon.getIconWidth() / 2;
                                unitYPos = (int) yTar - unitIcon.getIconHeight() / 2;
                            } else if ((start > System.currentTimeMillis()) && (arrive > current)) {
                                //attack not running, draw unit between source and target
                                double perc = .5;
                                double xTar = xStart + (xEnd - xStart) * perc;
                                double yTar = yStart + (yEnd - yStart) * perc;
                                unitXPos = (int) xTar - unitIcon.getIconWidth() / 2;
                                unitYPos = (int) yTar - unitIcon.getIconHeight() / 2;
                            } else {
                                //attack arrived
                                unitXPos = (int) xEnd - unitIcon.getIconWidth() / 2;
                                unitYPos = (int) yEnd - unitIcon.getIconHeight() / 2;
                            }

                        }
                    }

                    pG2D.setColor(attackColors.get(attack.getUnit().getName()));
                    if (attack.getType() == Attack.FAKE_TYPE || attack.getType() == Attack.FAKE_DEFF_TYPE) {
                        /* g2d.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));*/
                        pG2D.setStroke(stroke_fake);
                    } else {
                        pG2D.setStroke(stroke_attack);
                    }


                    pG2D.drawLine((int) Math.floor(xStart), (int) Math.floor(yStart), (int) Math.floor(xEnd), (int) Math.floor(yEnd));

                    if (unitIcon != null) {
                        pG2D.drawImage(unitIcon.getImage(), unitXPos, unitYPos, null);
                    }

                }
            }
            attacks = null;
        }
    }
}
