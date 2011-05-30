package org.n52.v3d.terrainserver.demservice;

import java.io.*;

import org.n52.v3d.triturus.core.T3dException;
import org.n52.v3d.triturus.vgis.VgEnvelope;
import org.n52.v3d.triturus.gisimplm.GmEnvelope;
import org.n52.v3d.triturus.web.HttpRequestParams;
import org.n52.v3d.triturus.web.HttpStandardResponse;
import org.n52.v3d.triturus.survey.Wgs84Helper;
import org.n52.v3d.triturus.t3dutil.T3dTimeList;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Implementierung eines Web-Dienstes für den Zugriff auf Höhenmodelle.<p>
 * Beispielaufruf:
 * <tt>http://<hostname>/DEMServlet?REQUEST=GetDEM&SRS=EPSG:31466&BBOX=2590000,5740000,2600000,5750000&CELLSIZE=100&FORMAT=ArcIGrd</tt><p>
 * Bem.: Der Dienst ist konform zur W3DS-Spezifikation des "3D-Piloten" der GDI-NRW.<p>
 * Voraussetzung für die Lauffähigkeit des Servlets ist eine entsprechende Organisation der Höhenmodelldaten. Die
 * Modelle müssen TK 25-Blattschnitt-weise als ArcInfo-ASCII-Grids unter <tt>\dgm&lt;MM&gt;&lt;NN&gt;.asc</tt> abgelegt
 * sein, wobei &lt;MM&gt;, &lt;NN&gt; die TK-Blattnummer bezeichnen. Das Quellverzeichnis ist über den Parameter
 * "SourceGridPath" im Deployment-Deskriptor einstellbar.
 * <p>
 * @author Benno Schmidt<br>
 * (c) 2003-2006, con terra GmbH & Institute for Geoinformatics<br>
 */
public class DEMServlet extends HttpServlet
{
    private Log sLogger = LogFactory.getLog(DEMServlet.class);

    private boolean mLocalDebug = false;

    private static int mCounter = 0;

    // Einstellungen aus Deployment-Deskriptor:
    private String mDestFilePath;
    private String mSourceGridPath;
    private String mTileLocator = "TK25";
    private double mMaxArea = 1000000000.; // 1000 km^2
    private double mMinCellSize = 50.; // todo: doku inst-hbu / Inst-HBU: "hängt von Quell-Grids ab!"
    private double mMinCellSizeLatLon = 4.629627e-4; // todo: doku inst-hbu / Inst-HBU: "hängt von Quell-Grids ab!"
    private double mSearchRadiusMin = 49.99; // todo: doku inst-hbu
    private String mCapabilitiesFile;
    private String mWorkingDirectory;

    /**
     * liest die Ablaufparameter aus dem Deployment-Deskriptor und überträgt die Werte in entsprechende
     * Member-Variablen.<p>
     */
    public void fetchInitParameters()
    {
        mCapabilitiesFile = this.getInitParameter("CapabilitiesFile");
        mSourceGridPath = this.getInitParameter("SourceGridPath");
        mTileLocator = this.getInitParameter("TileLocator");
        mMaxArea = Double.parseDouble(this.getInitParameter("MaxArea"));
        mMinCellSize = Double.parseDouble(this.getInitParameter("MinCellSize"));
        mMinCellSizeLatLon = Double.parseDouble(this.getInitParameter("MinCellSizeLatLon"));
        mSearchRadiusMin = Double.parseDouble(this.getInitParameter("SearchRadiusMin"));
        mDestFilePath = this.getInitParameter("DestFilePath");
        mWorkingDirectory = this.getInitParameter("WorkingDirectory");

        if (mLocalDebug) {
            System.out.println("SourceGridPath = " + mSourceGridPath);
            System.out.println("MinCellSize = " + mMinCellSize);
        }
    }

    /**
     * initialisiert das Servlet.<p>
     */
    public void init(ServletConfig pConf) throws ServletException {
        super.init(pConf);

        // Initialisierungsparameter aus "web.xml" lesen:
        this.fetchInitParameters();
    }

    private HttpRequestParams fetchRequestParameters(HttpServletRequest pReq)
    {
        HttpRequestParams lReqParams = new HttpRequestParams();

        // Bekanntgabe der Anfrage-Parameter, damit diese als Defaults verfügbar und/oder damit diese
        // getypt sind und somit automatisch geparst werden:
        lReqParams.addParameter("REQUEST", "String", "GetCapabilities");
        lReqParams.addParameter("SERVICE", "String", "DEM");
        lReqParams.addParameter("SRS", "String", "");
        lReqParams.addParameter("BBOX", "VgEnvelope", "0,0,0,0");
        lReqParams.addParameter("CELLSIZE", "Double", "250");
        lReqParams.addParameter("FORMAT", "String", "model/vrml");
        lReqParams.addParameter("SEARCHRADIUS", "Double", "49.99"); // vgl. Initialisierungsparam. SearchRadiusDefault!
        lReqParams.addParameter("DRAPE", "String", "");

        lReqParams.fetchRequestParameters(pReq);

        // Rückgabe der Session-spezifischen Request-Parameter:
        return lReqParams;
    }

    /**
     * bearbeitet HTTP-Get-Anfragen an das Servlet.<p>
     * @param pRequest HTTP-Anfrage-Objekt
     * @param pResponse HTTP-Antwort-Objekt
     * @throws ServletException
     * @throws IOException
     */
    public void doGet(HttpServletRequest pRequest, HttpServletResponse pResponse)
        throws ServletException, IOException
    {
        T3dTimeList lTimeProt = new T3dTimeList(); // zur Protokollierung der Rechenzeiten
        lTimeProt.addTimeStamp("init");

        String lTmpName = "~" + (mCounter++) + "_" + new java.util.Date().getTime();
        
    	try {
            // Request-Parameter ermitteln:
    		HttpRequestParams lReqParams = this.fetchRequestParameters(pRequest);
            String lService = (String) lReqParams.getParameterValue("SERVICE"); // wird noch ignoriert...
            String lRequest = (String) lReqParams.getParameterValue("REQUEST");
            String lSRS = (String) lReqParams.getParameterValue("SRS");
            GmEnvelope lBBox = (GmEnvelope) lReqParams.getParameterValue("BBOX");
            double lCellSize = ((Double) lReqParams.getParameterValue("CELLSIZE")).doubleValue();
            String lFormat = (String) lReqParams.getParameterValue("FORMAT");
            double lSearchRadius = ((Double) lReqParams.getParameterValue("SEARCHRADIUS")).doubleValue();
            String lDrape = (String) lReqParams.getParameterValue("DRAPE");

            if (mLocalDebug) {
                System.out.println("CELLSIZE = " + lCellSize);
                System.out.println("SEARCHRADIUS = " + lSearchRadius);
                System.out.println("FORMAT = " + lFormat);
                System.out.println("DRAPE = " + lDrape);
            }

            // Bearbeitung GetCapabilities-Anfrage:
            if (lRequest.equalsIgnoreCase("GetCapabilities")) {
                HttpStandardResponse response = new HttpStandardResponse();
                response.sendXMLFile(mCapabilitiesFile, pResponse);
                logGetCapabilitiesInfo(lTmpName, pRequest);
                return;
            }

            // Bearbeitung GetDEM- und GetScene-Anfrage:
            if (lRequest.equalsIgnoreCase("GetDEM") || lRequest.equalsIgnoreCase("GetScene"))
            {
                // Request-Parameter aufbereiten und Wertebereiche prüfen:
                ParameterPreparer pp = new ParameterPreparer();
                lSRS = pp.prepareSRS(lSRS);
                lBBox = pp.prepareBBOX(lBBox, lSRS);
                lFormat = pp.prepareFORMAT(lFormat, lRequest);
                if (mLocalDebug)
                    System.out.println("env = " + lBBox);

                if (! lBBox.hasMetricSRS()) {
                    if (lBBox.hasGeographicSRS()) {
                        // metrisch benötigte Parameter umrechnen  // todo inst-hbu
                        lSearchRadius /= Wgs84Helper.degree2meter;
                    }
                    else {
                        throw new T3dException("Missing SRS support for \"" + lBBox.getSRS() + "\".");
                    }
                }
                if (lSearchRadius < mSearchRadiusMin)
                    lSearchRadius = mSearchRadiusMin;

                DEMServiceHelpers lHlp = new DEMServiceHelpers(mMaxArea);
                //lHlp.setLocalDebug(mLocalDebug);

                if (! lBBox.hasGeographicSRS()) {
                    if (lCellSize < mMinCellSize)
                        throw new T3dException("Cell-size may not be less than " + mMinCellSize + " meters.");
                }
                else {
                    if (lCellSize < mMinCellSizeLatLon)
                        throw new T3dException("Cell-size may not be less than " + mMinCellSizeLatLon + " degrees.");
                }

                sLogger.debug("DEMServlet (" + lTmpName + "): Received " + lRequest + " request.");
                lTimeProt.setFinished("init");

                // Höhenmodell berechnen (Gridding):
                lTimeProt.addTimeStamp("dem_access");
                String lResFile;
                try {
                    lResFile = lHlp.setUpDEM(     // TODO arbeitet für lat/lon noch nicht sauber! -> QS!
                        lBBox.getLowerLeftFrontCorner(), lBBox.getUpperRightBackCorner(), lCellSize,
                        lSearchRadius, mTileLocator,
                        lFormat, mSourceGridPath, mDestFilePath, lTmpName);
                }
                catch (T3dException e) {
                    final boolean lDebug = false;
                    String[] errGrd = lHlp.missingGridCells();
                    if (errGrd != null) {
                        if (lDebug) {
                            for (int i = 0; i < errGrd.length; i++)
                                System.out.println(errGrd[i]);
                        }
                    }
                    throw e;
                }
                lTimeProt.setFinished("dem_access");

                // Antwort generieren:
                lTimeProt.addTimeStamp("generate_response");
                String mime = lHlp.formatInfo(lFormat, "mime");
                pResponse.setContentType(mime); // MIME-Typ für Antwort setzen
                BufferedReader lDatRead;
                try {
                    lDatRead = new BufferedReader(new FileReader(lResFile));
                }
                catch (FileNotFoundException e) {
                    throw new T3dException("Internal error while reading \"" + lResFile + "\".");
                }
                PrintWriter out = pResponse.getWriter(); // PrintWriter auf die Antwort aufsetzen
           	    String line = lDatRead.readLine();
           	    while (line != null) { // generierte Temporärdatei zeilenweise senden
               	    out.println(line);
               	    line = lDatRead.readLine();
           	    }
           	    lDatRead.close();
       		    out.flush();
       		    out.close();
                File f = new File(lResFile);
                f.delete();
                lTimeProt.setFinished("generate_response");
                return;
            }

            // Bearbeitung sonstiger Anfragen:
            if (!(lRequest.equalsIgnoreCase("GetDEM") || lRequest.equalsIgnoreCase("GetScene"))) {
                HttpStandardResponse response = new HttpStandardResponse();
                response.sendException("Illegal REQUEST parameter value.", pResponse);
                return;
            }

            this.logGetDEMInfo(lTmpName, lBBox, lCellSize, lTimeProt, pRequest, lFormat);
            if (mLocalDebug)
                System.out.println("logged info");

            sLogger.debug("DEMServlet (" + lTmpName + "): Duly finished execution.");

		}
        catch (Throwable e) {
            sLogger.debug("DEMServlet (" + lTmpName + "): Aborting execution. Error: " + e.getMessage());

            HttpStandardResponse response = new HttpStandardResponse();
            try {
                response.sendException(e.getMessage(), pResponse);
            }
            catch (Throwable e2) {
                System.out.println("DEMServlet: FATAL ERROR - " + e2.getMessage());
            }
            try {
                this.logErrorInfo(lTmpName, pRequest, e);
            }
            catch (Throwable e2) {
                System.out.println("DEMServlet: FATAL ERROR - " + e2.getMessage());
            }
        }
    }

    private void logGetCapabilitiesInfo(String pTmpName, HttpServletRequest pRequest)
    {
        try {
            PrintWriter lDat = new PrintWriter(new FileWriter(mWorkingDirectory + "/" + pTmpName + ".log"));

            lDat.println("REMOTE HOST: " + pRequest.getRemoteHost());
            lDat.println("REMOTE ADDRESS: " + pRequest.getRemoteAddr());
            //lDat.println("REMOTE USER: " + pRequest.getRemoteUser());
            lDat.println("QUERY STRING: " + pRequest.getQueryString());
            lDat.println("SESSION-ID: " + pRequest.getRequestedSessionId());

            lDat.close();
        }
        catch (IOException e) {
            throw new T3dException(e.getMessage());
        }
    }

    private void logGetDEMInfo(
        String pTmpName, VgEnvelope pBBox, double pCellSize, T3dTimeList pTimeProt, HttpServletRequest pRequest, String pFormat)
    {
        try {
            PrintWriter lDat = new PrintWriter(new FileWriter(mWorkingDirectory + "/" + pTmpName + ".log"));
    
            lDat.println("REMOTE HOST: " + pRequest.getRemoteHost());
            lDat.println("REMOTE ADDRESS: " + pRequest.getRemoteAddr());
            //lDat.println("REMOTE USER: " + pRequest.getRemoteUser());
            lDat.println("QUERY STRING: " + pRequest.getQueryString());
            lDat.println("SESSION-ID: " + pRequest.getRequestedSessionId());
            lDat.println("BBOX: " + pBBox);
            lDat.println("CELLSIZE: " + pCellSize);
            lDat.println("OUTPUT FORMAT: " + pFormat);
            lDat.println("PROCESSING_TIMES [msec]: ");
            String[] lTimeProtStr = pTimeProt.protocol();
            for (int i = 0; i < lTimeProtStr.length; i++)
                lDat.println(lTimeProtStr[i]);

            lDat.close();
        }
        catch (IOException e) {
            throw new T3dException(e.getMessage());
        }
    }

    private void logErrorInfo(String pTmpName, HttpServletRequest pRequest, Throwable pExc)
    {
        try {
            PrintWriter lDat = new PrintWriter(new FileWriter(mWorkingDirectory + "/" + pTmpName + ".log"));

            lDat.println("REMOTE HOST: " + pRequest.getRemoteHost());
            lDat.println("REMOTE ADDRESS: " + pRequest.getRemoteAddr());
            //lDat.println("REMOTE USER: " + pRequest.getRemoteUser());
            lDat.println("QUERY STRING: " + pRequest.getQueryString());
            lDat.println("SESSION-ID: " + pRequest.getRequestedSessionId());
            lDat.println("ERROR: " + pExc.getMessage());
            lDat.println("STACK TRACE: ");
            pExc.printStackTrace(lDat);

            lDat.close();
        }
        catch (IOException e) {
            throw new T3dException(e.getMessage());
        }
    }
}
