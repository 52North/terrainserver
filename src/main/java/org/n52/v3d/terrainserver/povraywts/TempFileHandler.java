/***************************************************************************************
 * Copyright (C) 2011 by 52 North Initiative for Geospatial Open Source Software GmbH  *
 *                                                                                     *
 * Contact: Benno Schmidt & Martin May, 52 North Initiative for Geospatial Open Source *
 * Software GmbH, Martin-Luther-King-Weg 24, 48155 Muenster, Germany, info@52north.org *
 *                                                                                     *
 * This program is free software; you can redistribute and/or modify it under the      *
 * terms of the GNU General Public License version 2 as published by the Free Software *
 * Foundation.                                                                         *
 *                                                                                     *
 * This program is distributed WITHOUT ANY WARRANTY; even without the implied WARRANTY *
 * OF MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public  *
 * License for more details.                                                           *
 *                                                                                     *
 * You should have received a copy of the GNU General Public License along with this   *
 * program (see gnu-gpl v2.txt). If not, write to the Free Software Foundation, Inc.,  *
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA, or visit the Free Software *
 * Foundation web page, http://www.fsf.org.                                            *
 **************************************************************************************/
package org.n52.v3d.terrainserver.povraywts;

import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionEvent;
import java.util.ArrayList;
import java.io.File;

/**
 * Hilfsklasse zur Bereinigung der Tempor�rdateien auf dem Server.<p>
 * @author Benno Schmidt
 */
public class TempFileHandler implements javax.servlet.http.HttpSessionBindingListener
{
    private ArrayList mFiles = new ArrayList();
    private boolean mLocalDebug = false;

    /**
     * f�gt der Liste der zu l�schenden Tempor�rdateien einen Eintrag hinzu. Diese Tempor�rdatei wird erst durch Aufruf
     * der Methode <tt>this.removeTempFiles</tt> wieder gel�scht.<p>
     * @param pFilename Dateiname (inkl. vollst�ndiger Pfadangabe)
     */
    public void addTempFile(String pFilename)
    {
        if (!mFiles.contains(pFilename))
            mFiles.add(pFilename);
    }

    /**
     * l�scht die Datei mit dem angegebenen Namen vom Server und entfernt den zugeh�rigen Eintrag aus der Liste der
     * Tempor�rdateien.<p>
     * @param pFilename Dateiname (inkl. vollst�ndiger Pfadangabe)
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
     * l�scht die Tempor�rdateien vom Server.<p>
     * Diese Methode wird nur f�r die HTTP-Anfrage-spezifischen Tempor�rdateien aufzurufen. F�r den Fall der
     * Session-Unterst�tzung (CACHESCENE=true) wird f�r die Session-spezifischen Tempor�rdateien die Methode
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
     * Bem.: Die Methode ist f�r die Implementierung der Schnittstelle <tt>HttpSessionBindingListener</tt> erforderlich.
     * In diesem Fall ist nichts weiter zu veranlassen.<p>
     * @param e ausl�sendes Ereignis
     */
    public void valueBound(HttpSessionBindingEvent e) {
        ;
    }

    /**
     * wird aufgerufen, wenn das Objekt aus der Session entfernt wird oder die Session ung�ltig wird.<p>
     * Bem.: Die Methode ist f�r die Implementierung der Schnittstelle <tt>HttpSessionBindingListener</tt> erforderlich.
     * In diesem Fall ist nichts weiter zu veranlassen.<p>
     * @param e ausl�sendes Ereignis
     */
    public void valueUnbound(HttpSessionBindingEvent e)
    {
        //if (mLocalDebug)
            System.out.println("WebTerrainServlet: TempFileHandler#valueUnbound(HttpSessionBindingEvent) has been called...");

        this.removeTempFiles();
    }
}
