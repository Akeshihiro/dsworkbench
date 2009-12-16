/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.tor.tribes.util.stat;

import de.tor.tribes.io.DataHolder;
import de.tor.tribes.types.Ally;
import de.tor.tribes.types.NoAlly;
import de.tor.tribes.types.Tribe;
import de.tor.tribes.types.TribeStatsElement;
import de.tor.tribes.ui.DSWorkbenchStatsFrame;
import de.tor.tribes.util.GlobalOptions;
import java.io.File;
import java.io.FileFilter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import org.apache.log4j.Logger;

/**
 *
 * @author Jejkal
 */
public class StatManager {

    private static Logger logger = Logger.getLogger("StatManager");
    private static StatManager SINGLETON = null;
    private boolean INITIALIZED = false;
    private Hashtable<Integer, Hashtable<Integer, TribeStatsElement>> data = null;

    public static synchronized StatManager getSingleton() {
        if (SINGLETON == null) {
            SINGLETON = new StatManager();
        }
        return SINGLETON;
    }

    public void setup() {
        if (INITIALIZED) {
            storeStats();
        }
        INITIALIZED = false;
        logger.debug("Start loading stats");
        String dataPath = "./servers/" + GlobalOptions.getSelectedServer() + "/stats/";
        if (!new File(dataPath).exists()) {
            if (!new File(dataPath).mkdir()) {
                logger.error(" * Failed to create stats directory");
            } else {
                logger.debug(" * Created stats directory");
            }
        }
        data = new Hashtable<Integer, Hashtable<Integer, TribeStatsElement>>();

        File[] allyDirs = new File(dataPath).listFiles(new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });

        logger.debug(" * Loading stats from '" + dataPath + "'");
        for (File allyDir : allyDirs) {
            try {
                Integer allyId = Integer.parseInt(allyDir.getName());
                Hashtable<Integer, TribeStatsElement> tribeStats = null;
                if (allyId == -1 || DataHolder.getSingleton().getAllies().get(allyId) != null) {
                    //directory for non ally tribes or valid ally dir found
                    logger.debug("  - Loading ally data from '" + allyDir.getPath() + "'");
                    tribeStats = readTribeStats(allyDir);
                    if (tribeStats != null) {
                        data.put(allyId, tribeStats);
                    }
                } else {
                    logger.info(" * No ally with ID '" + allyId + "' was found. Removing stat dir.");
                    deleteDirectory(allyDir);
                }
            } catch (Exception e) {
                //ally id invalid!? ignore!
            }
        }

        updateAllyChanges();

        logger.debug("Finished loading stats");
        INITIALIZED = true;
    }

    private Hashtable<Integer, TribeStatsElement> readTribeStats(File pStatDir) {
        Hashtable<Integer, TribeStatsElement> tribeStats = new Hashtable<Integer, TribeStatsElement>();
        File[] tribeStatDirs = pStatDir.listFiles(new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                return pathname.isFile();
            }
        });

        for (File tribeStatFile : tribeStatDirs) {
            try {
                Integer tribeId = Integer.parseInt(tribeStatFile.getName().substring(0, tribeStatFile.getName().lastIndexOf(".")));
                if (DataHolder.getSingleton().getTribes().get(tribeId) == null) {
                    logger.info("No tribe with ID '" + tribeId + "' was found. Removing stat file.");
                    tribeStatFile.delete();
                } else {
                    //read tribe stats
                    logger.debug(" - Loading tribe data from '" + tribeStatFile.getPath() + "'");
                    TribeStatsElement elem = TribeStatsElement.loadFromFile(tribeStatFile);
                    if (elem != null) {
                        tribeStats.put(tribeId, elem);
                    }
                }
            } catch (Exception e) {
                //invalid tribe stat file
            }
        }

        return tribeStats;
    }

    private void updateAllyChanges() {
        logger.debug(" * Updating ally changes");
        Enumeration<Integer> allyKeys = data.keys();
        List<TribeStatsElement> outdatedElements = new LinkedList<TribeStatsElement>();
        logger.debug("  - Checking " + data.size() + " allies");
        while (allyKeys.hasMoreElements()) {
            //check all allies
            Integer allyKey = allyKeys.nextElement();
            Hashtable<Integer, TribeStatsElement> tribesData = data.get(allyKey);
            if (tribesData == null) {
                //avoid NPE
                tribesData = new Hashtable<Integer, TribeStatsElement>();
            }
            Enumeration<Integer> tribeKeys = tribesData.keys();
            //get tribes that have changed the ally
            List<Tribe> outdatedTribes = new LinkedList<Tribe>();
            String allyPath = "./servers/" + GlobalOptions.getSelectedServer() + "/stats/" + allyKey;
            logger.debug("  - Checking " + tribesData.size() + " tribes");
            while (tribeKeys.hasMoreElements()) {
                Integer tribeKey = tribeKeys.nextElement();
                Tribe tribe = DataHolder.getSingleton().getTribes().get(tribeKey);
                if (tribe.getAllyID() != allyKey) {
                    logger.debug("  - Tribe '" + tribe + "' is outdated");
                    //tribe has changed ally
                    outdatedTribes.add(tribe);
                    //remove old tribe stats file from ally dir
                    String tribePath = allyPath + "/" + tribeKey + ".stat";
                    logger.debug("  - Removing stats file from '" + tribePath + "'");
                    new File(tribePath).delete();
                }
            }
            //go through all tribes that are not longer in the current ally
            logger.debug("  - removing outdated tribes");
            for (Tribe t : outdatedTribes) {
                //add TribeStatElement to outdated list
                outdatedElements.add(tribesData.get(t.getId()));
                //remove tribe id from current allies table
                tribesData.remove(t.getId());
            }
        }

        logger.debug("  - assigning outdated tribes to new allies");
        //re-assign outdated elems
        for (TribeStatsElement outdatedElem : outdatedElements) {
            Ally a = outdatedElem.getTribe().getAlly();
            if (a == null) {
                a = NoAlly.getSingleton();
            }
            Hashtable<Integer, TribeStatsElement> tribeData = data.get(a.getId());
            if (tribeData == null) {
                logger.debug("  - Creating new ally stats for ally '" + a + "'");
                //add new tribe and ally data elements
                tribeData = new Hashtable<Integer, TribeStatsElement>();
                tribeData.put(outdatedElem.getTribe().getId(), outdatedElem);
                data.put(a.getId(), tribeData);
            } else {
                logger.debug("  - Assigning tribe to existing ally stats for ally '" + a + "'");
                //add only tribe to existing tribe data table
                tribeData.put(outdatedElem.getTribe().getId(), outdatedElem);
            }
        }

        logger.debug("  - Removing empty ally stats");
        allyKeys = data.keys();
        while (allyKeys.hasMoreElements()) {
            Integer allyKey = allyKeys.nextElement();
            Hashtable<Integer, TribeStatsElement> tribesData = data.get(allyKey);
            Ally a = NoAlly.getSingleton();
            if (allyKey != -1) {
                a = DataHolder.getSingleton().getAllies().get(allyKey);
            }

            String allyPath = "./servers/" + GlobalOptions.getSelectedServer() + "/stats/" + allyKey;
            if (tribesData == null || tribesData.isEmpty()) {
                logger.debug("  - Removing ally stats for ally '" + a + "'");
                //remove empty ally data table
                data.remove(allyKey);
                //remove existing ally dir
                deleteDirectory(new File(allyPath));
            }
        }
        logger.debug(" * Finished updating ally changes");
    }

    public void storeStats() {
        Enumeration<Integer> allyKeys = data.keys();
        String dataPath = "./servers/" + GlobalOptions.getSelectedServer() + "/stats";
        while (allyKeys.hasMoreElements()) {
            Integer allyKey = allyKeys.nextElement();
            Hashtable<Integer, TribeStatsElement> tribes = data.get(allyKey);
            Enumeration<Integer> tribeKeys = tribes.keys();
            while (tribeKeys.hasMoreElements()) {
                Integer tribeKey = tribeKeys.nextElement();
                TribeStatsElement elem = tribes.get(tribeKey);
                new File(dataPath + "/" + allyKey + "/").mkdirs();
                File f = new File(dataPath + "/" + allyKey + "/" + tribeKey + ".stat");
                try {
                    elem.store(f);
                } catch (Exception e) {
                    logger.error("Failed to store stats for tribe '" + elem.getTribe() + "'");
                }
            }
        }
    }

    public void monitorTribe(Tribe pTribe) {
        if (pTribe == null) {
            return;
        }
        logger.debug("Adding stat monitor for tribe '" + pTribe.getName() + "'");
        Ally a = pTribe.getAlly();
        Hashtable<Integer, TribeStatsElement> allyData = null;
        int allyId = -1;
        if (a == null) {
            allyData = data.get(allyId);
        } else {
            allyId = a.getId();
            allyData = data.get(allyId);
        }

        if (allyData == null) {
            allyData = new Hashtable<Integer, TribeStatsElement>();
            data.put(allyId, allyData);
        }

        TribeStatsElement elem = allyData.get(pTribe.getId());
        if (elem == null) {
            //tribe not yet monitores
            elem = new TribeStatsElement(pTribe);
            allyData.put(pTribe.getId(), elem);
        } else {
            //tribe already exists
        }
        long now = System.currentTimeMillis();
        logger.debug("Taking initial stat snapshot from '" + now + "'");
        elem.takeSnapshot(now);
        logger.debug("Stat monitor successfully added");
    }

    public void monitorAlly(Ally pAlly) {
        logger.debug("Adding stat monitor for all '" + pAlly.getName() + "'");
        for (Tribe t : pAlly.getTribes()) {
            monitorTribe(t);
        }
    }

    public void takeSnapshot() {
        long now = System.currentTimeMillis();
        logger.debug("Taking stat snapshot from '" + now + "'");
        Enumeration<Integer> allyKeys = data.keys();
        while (allyKeys.hasMoreElements()) {
            Integer allyKey = allyKeys.nextElement();
            Hashtable<Integer, TribeStatsElement> tribes = data.get(allyKey);
            Enumeration<Integer> tribeKeys = tribes.keys();
            while (tribeKeys.hasMoreElements()) {
                Integer tribeKey = tribeKeys.nextElement();
                TribeStatsElement elem = tribes.get(tribeKey);
                elem.takeSnapshot(now);
            }
        }
    }

    public Ally[] getMonitoredAllies() {
        List<Ally> allies = new LinkedList<Ally>();
        Enumeration<Integer> allyKeys = data.keys();
        while (allyKeys.hasMoreElements()) {
            Integer allyKey = allyKeys.nextElement();
            if (allyKey == -1) {
                allies.add(NoAlly.getSingleton());
            } else {
                Ally ally = DataHolder.getSingleton().getAllies().get(allyKey);
                if (ally != null) {
                    allies.add(ally);
                }
            }
        }
        return allies.toArray(new Ally[]{});
    }

    public Tribe[] getMonitoredTribes(Ally pAlly) {
        Hashtable<Integer, TribeStatsElement> tribeData = data.get(pAlly.getId());
        List<Tribe> tribes = new LinkedList<Tribe>();
        if (tribeData == null) {
            return new Tribe[]{};
        }
        Enumeration<Integer> tribeKeys = tribeData.keys();
        while (tribeKeys.hasMoreElements()) {
            Integer tribeKey = tribeKeys.nextElement();
            Tribe tribe = DataHolder.getSingleton().getTribes().get(tribeKey);
            if (tribe != null) {
                tribes.add(tribe);
            }
        }
        return tribes.toArray(new Tribe[]{});
    }

    public boolean deleteDirectory(File directory) {
        File fileToDelete = directory;
        boolean result = false;
        if (fileToDelete.exists()) {
            File directoryFiles[] = fileToDelete.listFiles();
            for (int i = 0; i < directoryFiles.length; i++) {
                if (directoryFiles[i].isFile()) {
                    directoryFiles[i].delete();
                } else {
                    deleteDirectory(directoryFiles[i]);
                }
            }
            fileToDelete.delete();
            result = true;
        }

        return result;
    }

    public void removeAllyData(Ally a) {
        if (a == null) {
            return;
        }
        logger.debug("Removing stats for ally " + a);
        logger.debug(" - removing data from memory");
        data.remove(a.getId());
        String dataPath = "./servers/" + GlobalOptions.getSelectedServer() + "/stats/" + a.getId();
        logger.debug(" - deleting data directory '" + dataPath + "'");
        if (deleteDirectory(new File(dataPath))) {
            logger.debug("Stats successfully removed");
        }
    }

    public void removeTribeData(Tribe t) {
        if (t == null) {
            return;
        }
        logger.debug("Removing stats for tribe " + t);
        Ally a = t.getAlly();
        if (a == null) {
            a = NoAlly.getSingleton();
        }
        Hashtable<Integer, TribeStatsElement> allyData = data.get(a.getId());
        if (allyData == null) {
            logger.warn("No stats available!?");
            return;
        }
        logger.debug(" - removing data from memory");
        allyData.remove(t.getId());
        if (allyData.size() == 0) {
            removeAllyData(a);
        } else {
            String dataPath = "./servers/" + GlobalOptions.getSelectedServer() + "/stats/" + a.getId() + "/" + t.getId() + ".stat";
            logger.debug(" - deleting data file '" + dataPath + "'");
            if (new File(dataPath).delete()) {
                logger.debug("Stats successfully removed");
            }
        }
    }

    public TribeStatsElement getStatsForTribe(Tribe pTribe) {
        if (pTribe == null) {
            return null;
        }
        Ally a = pTribe.getAlly();
        if (a == null) {
            a = NoAlly.getSingleton();
        }

        Hashtable<Integer, TribeStatsElement> tribeData = data.get(a.getId());
        return tribeData.get(pTribe.getId());
    }

    public void removeDataBefore(Tribe pTribe, long pTimestamp) {
        if (pTribe == null) {
            return;
        }
        Ally a = pTribe.getAlly();
        if (a == null) {
            a = NoAlly.getSingleton();
        }

        Hashtable<Integer, TribeStatsElement> tribeData = data.get(a.getId());
        TribeStatsElement elem = tribeData.get(pTribe.getId());
        elem.removeDataBefore(pTimestamp);
    }

    public void removeDataAfter(Tribe pTribe, long pTimestamp) {
        if (pTribe == null) {
            return;
        }
        Ally a = pTribe.getAlly();
        if (a == null) {
            a = NoAlly.getSingleton();
        }

        Hashtable<Integer, TribeStatsElement> tribeData = data.get(a.getId());
        TribeStatsElement elem = tribeData.get(pTribe.getId());
        elem.removeDataAfter(pTimestamp);
    }

    public void removeDataBetween(Tribe pTribe, long pStartTimestamp, long pEndTimestamp) {
        if (pTribe == null) {
            return;
        }
        Ally a = pTribe.getAlly();
        if (a == null) {
            a = NoAlly.getSingleton();
        }

        Hashtable<Integer, TribeStatsElement> tribeData = data.get(a.getId());
        TribeStatsElement elem = tribeData.get(pTribe.getId());
        elem.removeDataBetween(pStartTimestamp, pEndTimestamp);
    }
}

