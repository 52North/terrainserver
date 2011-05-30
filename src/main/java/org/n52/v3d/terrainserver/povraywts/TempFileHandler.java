package org.n52.v3d.terrainserver.povraywts;

import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionEvent;
import java.util.ArrayList;
import java.io.File;

/**
 * Hilfsklasse zur Bereinigung der Temporärdateien auf dem Server.<p>
 * @author Benno Schmidt<br>
 * (c) 2004, con terra GmbH & Institute for Geoinformatics<br>
 */
public class TempFileHandler implements javax.servlet.http.HttpSessionBindingListener
{
    private ArrayList mFiles = new ArrayList();
    private boolean mLocalDebug = false;

    /**
     * fügt der Liste der zu löschenden Temporärdateien einen Eintrag hinzu. Diese Temporärdatei wird erst durch Aufruf
     * der Methode <tt>this.removeTempFiles</tt> wieder gelöscht.<p>
     * @param pFilename Dateiname (inkl. vollständiger Pfadangabe)
     */
    public void addTempFile(String pFilename)
    {
        if (!mFiles.contains(pFilename))
            mFiles.add(pFilename);
    }

    /**
     * löscht die Datei mit dem angegebenen Namen vom Server und entfernt den zugehörigen Eintrag aus der Liste der
     * Temporärdateien.<p>
     * @param pFilename Dateiname (inkl. vollständiger Pfadangabe)
     */
    public void removeTempFile(String pFilename)
    {
        if (mFiles.contains(pFilename)) {
            boolean stat1 = mFiles.remove(pFilename);
            boolean stat2 = false;
            if (pFilename != null && pFilename.length() >= 20) {
                 File f = new File(pFilename);
                 stat2 = f.delete();
            }
            if (mLocalDebug)
                System.out.println("delete \"" + pFilename + "\" -> " + stat1 + ", " + stat2);
        }
    }

    /**
     * löscht die Temporärdateien vom Server.<p>
     * Diese Methode wird nur für die HTTP-Anfrage-spezifischen Temporärdateien aufzurufen. Für den Fall der
     * Session-Unterstützung (CACHESCENE=true) wird für die Session-spezifischen Temporärdateien die Methode
     * <tt>SessionHandler#valueUnbound</tt> automatisch aufgerufen.<p>
     */
    public void removeTempFiles()
    {
        for (int i = 0; i < mFiles.size(); i++) {
            String lFilename = ((String) mFiles.get(i));
            if (lFilename != null && lFilename.length() >= 20) { // todo vielleicht noch sicherer machen
                File f = new File(lFilename);
                boolean stat = f.delete();
                if (mLocalDebug)
                    System.out.println("delete \"" + lFilename + "\" -> " + stat);
            }
        }
    }

    /**
     * wird aufgerufen, wenn das Objekt in die Session gelegt wird.<p>
     * Bem.: Die Methode ist für die Implementierung der Schnittstelle <tt>HttpSessionBindingListener</tt> erforderlich.
     * In diesem Fall ist nichts weiter zu veranlassen.<p>
     * @param e auslösendes Ereignis
     */
    public void valueBound(HttpSessionBindingEvent e) {
        ;
    }

    /**
     * wird aufgerufen, wenn das Objekt aus der Session entfernt wird oder die Session ungültig wird.<p>
     * Bem.: Die Methode ist für die Implementierung der Schnittstelle <tt>HttpSessionBindingListener</tt> erforderlich.
     * In diesem Fall ist nichts weiter zu veranlassen.<p>
     * @param e auslösendes Ereignis
     */
    public void valueUnbound(HttpSessionBindingEvent e)
    {
        //if (mLocalDebug)
            System.out.println("WebTerrainServlet: TempFileHandler#valueUnbound(HttpSessionBindingEvent) has been called...");

        this.removeTempFiles();
    }
}
