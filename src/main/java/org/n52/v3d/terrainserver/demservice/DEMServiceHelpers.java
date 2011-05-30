package org.n52.v3d.terrainserver.demservice;

import java.util.ArrayList;

import org.n52.v3d.triturus.vgis.VgPoint;
import org.n52.v3d.triturus.vgis.VgElevationGrid;
import org.n52.v3d.triturus.vgis.VgGeomObject;
import org.n52.v3d.triturus.core.T3dException;
import org.n52.v3d.triturus.gisimplm.GmPoint;
import org.n52.v3d.triturus.gisimplm.GmEnvelope;
import org.n52.v3d.triturus.gisimplm.GmSimple2dGridGeometry;
import org.n52.v3d.triturus.gisimplm.GmSimpleElevationGrid;
import org.n52.v3d.triturus.gisimplm.FltPointSet2ElevationGrid;
import org.n52.v3d.triturus.gisimplm.IoElevationGridReader;
import org.n52.v3d.triturus.gisimplm.IoElevationGridWriter;
import org.n52.v3d.triturus.survey.TKBlattLocator;
import org.n52.v3d.triturus.survey.GaussKrugerTransformator;
import org.n52.v3d.triturus.survey.Utm32Transformator;
import org.n52.v3d.triturus.survey.TileLocator;
import org.n52.v3d.triturus.t3dutil.MpSimpleHypsometricColor;
import org.n52.v3d.triturus.t3dutil.T3dColor;
import org.n52.v3d.triturus.t3dutil.MpHypsometricColor;

/**
 * Helfermethoden für <tt>serverapp.DEMService.DEMServlet</tt> und <tt>serverapp.PovrayWTS.WebTerrainServlet</tt>.<p>
 * Die Verwendung dieser Helferklasse sollte sich auf die beiden genannten Servlets beschränken, in denen ausgehend
 * von im TK 25-Blattschnitt abgelegten Quelldateien ein Höhenmodell errechnet wird.
 * <p>
 * @author Benno Schmidt<br>
 * (c) 2003-2004, con terra GmbH & Institute for Geoinformatics<br>
 */
public class DEMServiceHelpers
{
    private boolean mLocalDebug = false; // kann für Debug-Zwecke gesetzt werden

    private double mMaxArea = 1000000000.;
    private String[] mMissingGridCells = null; // im Fehlerfall Information über nicht belegte Gitterzellen

    /**
     * Konstruktor. Als Parameter ist ein Wert für den maximalen Flächeninhalt des zu berechnenden Raumauasschnitts
     * anzugeben.<p>
     * @param pMaxArea maximale Flächengröße
     */
    public DEMServiceHelpers(double pMaxArea) {
        mMaxArea = pMaxArea;
    }

    /**
     * berechnet ein Höhenmodell aus im TK 25-Blattschnitt organisierten Quellmodellen.<p>
     * Können nicht alle Gitterpunkte belegt werden, wird eine <tt>T3dException</tt> geworfen. Falls der Debug-Modus
     * gesetzt ist( <tt>this.setLocalDebug(true)</tt>), enthält das über <tt>this.missingGridCells()</tt> abfragbare
     * String-Array enthält die visuell aufbereitete Information über die nicht belegbaren Zellen.<p>
     * @param pPnt1 erster Eckpunkt des Zielmodells
     * @param pPnt2 zweiter Eckpunkt des Zielmodells
     * @param pCellSize Gitterabstand des Zielmodells
     * @param pSearchRadius Suchradius
     * @param pTileLocator Bezeichner für verwendete Kachelung
     * @param pSrcGrdPath Verzeichnis der Quelldateien
     * @return berechnetes Höhenmodell
     * @see DEMServiceHelpers#missingGridCells
     */
    public VgElevationGrid setUpDEM(
        VgPoint pPnt1, VgPoint pPnt2, double pCellSize,
        double pSearchRadius, String pTileLocator,
        String pSrcGrdPath)
    {
        return (VgElevationGrid)
            this.setUpDEM(pPnt1, pPnt2, pCellSize, pSearchRadius, pTileLocator, false, "dummy", pSrcGrdPath, "dummy", "dummy");
    }

    /**
     * berechnet ein Höhenmodell aus im TK 25-Blattschnitt organisierten Quellmodellen. Das Ergebnismodell wird dabei
     * persistent in eine Datei geschrieben.<p>
     * Können nicht alle Gitterpunkte belegt werden, wird eine <tt>T3dException</tt> geworfen. Falls der Debug-Modus
     * gesetzt ist( <tt>this.setLocalDebug(true)</tt>), enthält das über <tt>this.missingGridCells()</tt> abfragbare
     * String-Array enthält die visuell aufbereitete Information über die nicht belegbaren Zellen.<p>
     * @param pPnt1 erster Eckpunkt des Zielmodells
     * @param pPnt2 zweiter Eckpunkt des Zielmodells
     * @param pCellSize Gitterabstand des Zielmodells
     * @param pSearchRadius Suchradius
     * @param pTileLocator Bezeichner für verwendete Kachelung
     * @param pFormat Zielformat gemäß Service-Capabilities
     * @param pSrcGrdPath Verzeichnis der Quelldateien
     * @param pDestFilePath Verzeichnis zur Ablage der Ergebnisdatei
     * @param pTmpName Zieldateiname ohne Pfad und Extension
     * @return Name der Ergebnisdatei
     * @see DEMServiceHelpers#missingGridCells
     */
    public String setUpDEM(
        VgPoint pPnt1, VgPoint pPnt2, double pCellSize,
        double pSearchRadius, String pTileLocator,
        String pFormat, String pSrcGrdPath, String pDestFilePath, String pTmpName)
    {
        return (String)
            this.setUpDEM(pPnt1, pPnt2, pCellSize, pSearchRadius, pTileLocator, true, pFormat, pSrcGrdPath, pDestFilePath, pTmpName);
    }

    private Object setUpDEM(
        VgPoint pPnt1, VgPoint pPnt2, double pCellSize,
        double pSearchRadius, String pTileLocator,
        boolean pReturnFilename,
        String pFormat, String pSrcGrdPath, String pDestFilePath, String pTmpName)
    {
        // Kachelnummern (Blattnummern) ermitteln:
        GmPoint lPnt3 = new GmPoint(pPnt1.getX(), pPnt2.getY(), 0.);
        GmPoint lPnt4 = new GmPoint(pPnt2.getX(), pPnt1.getY(), 0.);
        lPnt3.setSRS(pPnt1.getSRS());
        lPnt4.setSRS(pPnt1.getSRS());
        int[] blatt1 = this.getTileNumber(pPnt1, pTileLocator);
        int[] blatt2 = this.getTileNumber(pPnt2, pTileLocator);
        int[] blatt3 = this.getTileNumber(lPnt3, pTileLocator);
        int[] blatt4 = this.getTileNumber(lPnt4, pTileLocator);
        int bHiFrom, bHiTo, bLoFrom, bLoTo;
        bHiFrom = this.min(blatt1[0], blatt2[0], blatt3[0], blatt4[0]);
        bHiTo = this.max(blatt1[0], blatt2[0], blatt3[0], blatt4[0]);
        bLoFrom = this.min(blatt1[1], blatt2[1], blatt3[1], blatt4[1]);
        bLoTo = this.max(blatt1[1], blatt2[1], blatt3[1], blatt4[1]);
        if (mLocalDebug) {
            System.out.println("bHi: " + bHiFrom + "..." + bHiTo);
            System.out.println("bLo: " + bLoFrom + "..." + bLoTo);
        }

        // Zielgitter aufbauen:
        if (Math.abs((pPnt2.getX() - pPnt1.getX()) * (pPnt2.getY() - pPnt1.getY())) > mMaxArea) {
            throw new T3dException(
                "Destination grid size exceeds Service-internal size-limit (" + ((long) (mMaxArea/1.e6)) + ")! "
                + "Please scale-down your BBOX.", 200);
            // Sonst gäbe es auch schnell einen OutOfMemory-Fehler, falls keine weitere Maßnahmen veranlasst werden...
        }
        GmSimple2dGridGeometry lGrdGeom = this.constructDestinationGrid(pPnt1, pPnt2, pCellSize);
        if (mLocalDebug) System.out.println("Ziel-Gitter: " + lGrdGeom.toString());

        // Quellgitterpunkte einlesen:
        ArrayList lPointList = new ArrayList();
        for (int hi = bHiFrom; hi <= bHiTo; hi++) {
            for (int lo = bLoFrom; lo <= bLoTo; lo++) {
                String filename = this.constructDEMFilename(hi, lo, pSrcGrdPath, pPnt1.getSRS(), pTileLocator);
                if (mLocalDebug)
                    System.out.println("Einlesen von Gitter \"" + filename + "\"...");
                IoElevationGridReader lReader = new IoElevationGridReader("ArcIGrd");
                GmSimpleElevationGrid lGrid;
                try {
                     lGrid = lReader.readFromFile(filename);
                }
                catch (T3dException e) {
                    throw new T3dException("Missing elevation information "
                        + "(" + TKBlattLocator.blattnummer(hi, lo) + ", " + pPnt1.getSRS() + ", " + filename + ").", 201);
                }
                // Gitterwerte traversieren und an lPointList hängen:
                if (mLocalDebug)
                    System.out.println("Aufbau der Punktliste...");
                VgPoint vertex ;
                for (int ii = 0; ii < lGrid.numberOfRows(); ii++) {
                    for (int jj = 0; jj < lGrid.numberOfColumns(); jj++) {
                        vertex = ((GmSimple2dGridGeometry) lGrid.getGeometry()).getVertexCoordinate(ii, jj);
                        if (lGrid.isSet(ii, jj)) {
                            vertex.setZ(lGrid.getValue(ii, jj));
                            lPointList.add(vertex);
                        }
                    }
                }
            }
        }

        // Zielgitter mit Werten belegen:
        if (mLocalDebug) System.out.println("Starte Gridding...");
        GmSimpleElevationGrid lResGrid = this.gridding(lPointList, lGrdGeom, pSearchRadius);
        if (mLocalDebug) System.out.println("Gridding beendet... " + lGrdGeom);
        if (lResGrid == null)
            throw new T3dException("Destination grid is null.");
        if (!lResGrid.isSet()) // Anweisung redundant, da diese Exception bereits in this#gridding geworfen wird
            throw new T3dException("Did not assign values to all grid cells.");

        if (pReturnFilename) {
            // Schreiben der Ergebnisdatei:
            String ext = this.formatInfo(pFormat, "ext");
            String pOutput = pDestFilePath + "/" + pTmpName + "." + ext;
            if (mLocalDebug) System.out.println("Schreiben der Ergebnisdatei \"" + pOutput + "\"...");
            String dest = this.formatInfo(pFormat, "dest");
            IoElevationGridWriter lGridWriter = new IoElevationGridWriter(dest);
            lGridWriter.setPrecisionXY(0);
            lGridWriter.setPrecisionZ(1);

            if (dest.equalsIgnoreCase("Vrml2")) {
                MpHypsometricColor colMapper = new MpSimpleHypsometricColor();
                double elev[] = {30., 100., 300., 900.};
                T3dColor cols[] = {
                    new T3dColor(0.0f, 0.8f, 0.0f), // Grün
                    new T3dColor(1.0f, 1.0f, 0.5f), // Blassgelb
                    new T3dColor(0.78f, 0.27f, 0.0f), // Braun
                    new T3dColor(0.82f, 0.2f, 0.0f)}; // Rötlichbraun
                ((MpSimpleHypsometricColor) colMapper).setPalette(elev, cols, true);
                lGridWriter.setHypsometricColorMapper(colMapper);
            }

            lGridWriter.writeToFile(lResGrid, pOutput);
            return pOutput;
        }
        else {
            return lResGrid;
        }
    }

    private int[] getTileNumber(VgPoint pt, String pTileLocator) // Ermittlung der Blattnummer-Indizes als zweielementiges Feld
    {
        GmPoint ptLatLon = new GmPoint();
        String srs = pt.getSRS();

        short lCase = 0;
        if (pt.hasGeographicSRS()) {
            lCase = 1;
            ptLatLon.set(pt);
        }
        if (srs.equalsIgnoreCase(VgGeomObject.SRSGkk2) || srs.equalsIgnoreCase(VgGeomObject.SRSGkk3) || srs.equalsIgnoreCase(VgGeomObject.SRSGkk4)) {
            lCase = 2;
            GaussKrugerTransformator trf = new GaussKrugerTransformator();
            trf.gkk2LatLonBessel(pt, ptLatLon); // todo: Beruht Blattschnitt auf Bessel- oder WGS-84-Ellipsoid?
        }
        if (srs.equalsIgnoreCase(VgGeomObject.SRSUtmZ32N)) {
            lCase = 3;
            Utm32Transformator trf = new Utm32Transformator(); // todo: ETS89/UTM32N EPSG:32632 vs. 25832 (z. Zt. noch alte Transformation...)
            trf.utm2LatLon(pt, ptLatLon); // todo: Lat/lon Bessel statt WGS84?
        }
        if (lCase == 0)
            throw new T3dException("Spatial reference system " + pt.getSRS() + " is not supported.");

        // alt:
        // TKBlattLocator loc = new TKBlattLocator();
        // String tk25nr = loc.blattnummer("TK 25", ptLatLon);
        // neu:
        TileLocator loc = new TileLocator();
        String tileNo = loc.tileNumber(pTileLocator, ptLatLon);
        if (mLocalDebug)
            System.out.println("Kachelnummer (Blattnummer): " + tileNo /*+ ", \"" + loc.blattname(tk25nr) + "\""*/);

        int[] ret = new int[2];
        ret[0] = Integer.parseInt(tileNo.substring(0,2));
        ret[1] = Integer.parseInt(tileNo.substring(2,4));
        return ret;
    }

    private String constructDEMFilename(int i, int j, String pSrcGrdPath, String pSRS, String pTileLocatorName)
    {
        String subdir = pSRS.toLowerCase();
        subdir = subdir.replaceAll(":", "_");
        String filename = pSrcGrdPath + "/" + pTileLocatorName + "/" + subdir + "/dgm";
        if (i < 10) filename += "0";
        filename += "" + i;
        if (j < 10) filename += "0";
        filename += "" + j + ".asc";
        return filename;
    }

    private GmSimple2dGridGeometry constructDestinationGrid(VgPoint pPnt1, VgPoint pPnt2, double pCellSize)
    {
        GmEnvelope lEnv = new GmEnvelope(pPnt1, pPnt2);
        int nx = ((int) Math.floor(lEnv.getExtentX() / pCellSize)) + 1;
        int ny = ((int) Math.floor(lEnv.getExtentY() / pCellSize)) + 1;
        if (mLocalDebug)
            System.out.println("" + nx + " x " + ny + " Elemente großes Lattice wird aufgebaut...");

        return new GmSimple2dGridGeometry(
            nx, ny,
            new GmPoint(lEnv.getXMin(), lEnv.getYMin(), 0.), // untere linke Ecke
            pCellSize, pCellSize); // Gitterweiten in x- und y-Richtung
    }

    /**
     * führt das "Gridding" für die angegebene Gitter-Geometrie aus.<p>
     * Können nicht alle Gitterpunkte belegt werden, wird eine <tt>T3dException</tt> geworfen. Falls der Debug-Modus
     * gesetzt ist( <tt>this.setLocalDebug(true)</tt>), enthält das über <tt>this.missingGridCells()</tt> abfragbare
     * String-Array enthält die visuell aufbereitete Information über die nicht belegbaren Zellen.<p>
     * @param pPointList Quell-Punkte (jeweils mit x-, y- und z-Koordinate)
     * @param pGrdGeom Gitter-Geometrie (2D)
     * @param pSearchRadius Suchradius
     * @return Gitter mit belegten Höhenwerten
     * @see DEMServiceHelpers#missingGridCells
     */
    public GmSimpleElevationGrid gridding(ArrayList pPointList, GmSimple2dGridGeometry pGrdGeom, double pSearchRadius)
    {
        GmSimpleElevationGrid lResGrid = null;

        mMissingGridCells = null;

        try {
            int N = pPointList.size();
            if (mLocalDebug)
                System.out.println("Anzahl zu verarbeitender Punkte: " + N);

            if (N > 0) {
                if (mLocalDebug)
                    System.out.println("Suchradius: " + pSearchRadius);

                FltPointSet2ElevationGrid lGridder =
                    new FltPointSet2ElevationGrid(pGrdGeom, FltPointSet2ElevationGrid.cInverseDist, pSearchRadius);

                if (mLocalDebug) {
                    System.out.println("benötigter Heap-Speicher für Ziel-Gitter: " +
                        (lGridder.estimateMemoryConsumption() / 1000) + " KBytes");
                    System.out.println("Starte Gridding für " + pPointList.size() + " Eingabepunkte...");
                }

                lResGrid = lGridder.transform(pPointList);
                if (mLocalDebug)
                    System.out.println("Methode transform() lieferte " + lResGrid.toString());
                if (! lResGrid.isSet()) {
                    if (mLocalDebug) {
                        mMissingGridCells = new String[lResGrid.numberOfRows()];
                        for (int ii = 0; ii < lResGrid.numberOfRows(); ii++) {
                            for (int jj = 0; jj < lResGrid.numberOfColumns(); jj++) {
                                if (! lResGrid.isSet(ii,jj))
                                    mMissingGridCells[ii] = mMissingGridCells[ii] + "?";
                                else
                                    mMissingGridCells[ii] = mMissingGridCells[ii] + "X";
                            }
                        }
                    }
                    throw new T3dException("Did not assign values to all grid cells.", 202);
                }
            } // abfangen: n = 0 (z. B. alle Werte = NODATA -> sprechende Meldung todo
        }
        catch (T3dException e) {
            throw e;
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return lResGrid;
    }

    /**
     * liefert die Information über die nicht belegbaren Gitterzellen. Diese Information ist nur dann verfügbar, wenn
     * es in der Methode <tt>this.gridding()</tt> zu einem entsprechenden Fehler kommt. Zudem muss der Debug-Modus
     * gesetzt sein ( <tt>this.setLocalDebug(true)</tt>). Sonst wird der Wert <i>null</i> zurückgegeben.<p>
     * @return String-Array mit der visuell aufbereitete Information über die nicht belegbaren Gitterzellen
     * @see DEMServiceHelpers#gridding
     */
    public String[] missingGridCells() {
        return mMissingGridCells;
    }

    /**
     * liefert Information zu dem angegebenen Format. Über den Parameter <tt>pInfoType</tt> wird der Inhalt der
     * Abfrage gesteuert: Für "ext" wird die Datei-Extension (ohne .) geliefert, für "dest" die Servlet-interne
     * Zielformatsbezeichnung und für "mime" der MIME-Typ für die generierte Antwort.<p>
     * @param pFormat Triturus-interne Formatbezeichnung oder MIME-Typ (falls Standardformat)
     * @param pInfoType "ext", "dest", "mime"
     * @return Zeichenkette (Inhalt abhängig von abgefragtem Informationstyp)
     */
    public String formatInfo(String pFormat, String pInfoType)
    {
        if (pInfoType.equalsIgnoreCase("ext")) {
            String ext = "wrl"; // Default
            if (pFormat.equalsIgnoreCase("model/vrml")) return "wrl";
            if (pFormat.equalsIgnoreCase("text/vrml")) return "wrl";
            if (pFormat.equalsIgnoreCase("vrml")) return "wrl";
            if (pFormat.equalsIgnoreCase("vrml1")) return "wrl";
            if (pFormat.equalsIgnoreCase("vrml2")) return "wrl";
            if (pFormat.equalsIgnoreCase("AcGeo")) return "grd";
            if (pFormat.equalsIgnoreCase("AcGeoGrd")) return "grd";
            if (pFormat.equalsIgnoreCase("ArcIGrd")) return "asc";
            if (pFormat.equalsIgnoreCase("AcGeoTIN")) return "tin";
            if (pFormat.equalsIgnoreCase("model/x3d")) return "x3d";
            if (pFormat.equalsIgnoreCase("x3d")) return "x3d";
            return ext;
        }

        if (pInfoType.equalsIgnoreCase("dest")) {
            String dest = "Vrml2"; // Default
            if (pFormat.equalsIgnoreCase("model/vrml")) return "Vrml2";
            if (pFormat.equalsIgnoreCase("text/vrml")) return "Vrml2";
            if (pFormat.equalsIgnoreCase("vrml")) return "Vrml2";
            if (pFormat.equalsIgnoreCase("vrml1")) return "Vrml1";
            if (pFormat.equalsIgnoreCase("vrml2")) return "Vrml2";
            if (pFormat.equalsIgnoreCase("AcGeo")) return "AcGeo";
            if (pFormat.equalsIgnoreCase("AcGeoGrd")) return "AcGeo";
            if (pFormat.equalsIgnoreCase("ArcIGrd")) return "ArcIGrd";
            if (pFormat.equalsIgnoreCase("AcGeoTIN")) return "AcGeoTIN";
            if (pFormat.equalsIgnoreCase("model/x3d")) return "X3d";
            if (pFormat.equalsIgnoreCase("x3d")) return "X3d";
            return dest;
        }

        if (pInfoType.equalsIgnoreCase("mime")) {
            String mime = "text/plain"; // Default
            if (pFormat.equalsIgnoreCase("model/vrml")) return "model/vrml";
            if (pFormat.equalsIgnoreCase("text/vrml")) return "text/plain";
            if (pFormat.equalsIgnoreCase("vrml")) return "model/vrml";
            if (pFormat.equalsIgnoreCase("vrml1")) return "text/plain";
            if (pFormat.equalsIgnoreCase("vrml2")) return "model/vrml";
            if (pFormat.equalsIgnoreCase("AcGeo")) return "text/plain";
            if (pFormat.equalsIgnoreCase("AcGeoGrd")) return "text/plain";
            if (pFormat.equalsIgnoreCase("ArcIGrd")) return "text/plain";
            if (pFormat.equalsIgnoreCase("AcGeoTIN")) return "text/plain";
            if (pFormat.equalsIgnoreCase("model/x3d")) return "model/x3d";
            if (pFormat.equalsIgnoreCase("x3d")) return "model/x3d";
            return mime;
        }

        throw new T3dException("Something went wrong.");
    }

    private int min(int a, int b, int c, int d) {
        int res = a;
        if (b < res) res = b;
        if (c < res) res = c;
        if (d < res) res = d;
        return res;
    }

    private int max(int a, int b, int c, int d) {
        int res = a;
        if (b > res) res = b;
        if (c > res) res = c;
        if (d > res) res = d;
        return res;
    }

    /**
     * setzt des Modus für die Konsolen-Ausgabe von Kontrollausgaben.<p>
     * @param pVal <i>true</i>, falls Ausgabe erfolgen soll, sonst <i>false</i> (Voreinstellung)
     */
    public void setLocalDebug(boolean pVal) {
        mLocalDebug = pVal;
    }
}
