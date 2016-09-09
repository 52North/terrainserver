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

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.n52.v3d.terrainserver.demservice.DEMServiceHelpers;
import org.n52.v3d.triturus.core.T3dException;
import org.n52.v3d.triturus.core.T3dExceptionMessage;
import org.n52.v3d.triturus.gisimplm.GmEnvelope;
import org.n52.v3d.triturus.gisimplm.GmSimple2dGridGeometry;
import org.n52.v3d.triturus.survey.Wgs84Helper;
import org.n52.v3d.triturus.t3dutil.T3dColor;
import org.n52.v3d.triturus.t3dutil.T3dTimeList;
import org.n52.v3d.triturus.t3dutil.T3dVector;
import org.n52.v3d.triturus.t3dutil.operatingsystem.FileTools;
import org.n52.v3d.triturus.t3dutil.operatingsystem.TimeSliceAssigner;
import org.n52.v3d.triturus.vgis.VgElevationGrid;
import org.n52.v3d.triturus.vgis.VgEnvelope;
import org.n52.v3d.triturus.vgis.VgPoint;
import org.n52.v3d.triturus.vispovray.PovrayScene;
import org.n52.v3d.triturus.vscene.VsCamera;
import org.n52.v3d.triturus.vscene.VsSimpleScene;
import org.n52.v3d.triturus.vscene.VsViewpoint;
import org.n52.v3d.triturus.web.HttpRequestParams;
import org.n52.v3d.triturus.web.HttpStandardResponse;
import org.n52.v3d.triturus.web.IoHttpURLReader;
import org.n52.v3d.triturus.web.IoWMSConnector;
import org.n52.v3d.triturus.web.MimeTypeHelper;
import org.n52.v3d.triturus.web.WMSRequestConfig;

/**
 * Web Terrain Service (OGC-WTS 0.x) implementation using POV-Ray as rendering engine.<br /><br />
 * <i>German:</i> Implementierung eines Web Terrain Services (WTS) unter Nutzung von POV-Ray als Renderer.<br />
 * Beispielaufruf:
 * <tt>http://<hostname>/WebTerrainServlet?REQUEST=GetView&SRS=EPSG:31466&BBOX=2592761.3,5741340.4,2600772.4,5753813.3</tt>
 * @author Benno Schmidt
 */
public class WebTerrainServlet extends HttpServlet
{
    private Log sLogger = LogFactory.getLog(WebTerrainServlet.class);

    private static int mCounter = 0;
    private static short mRendererInstances = 0;
    private static TimeSliceAssigner mTimeSliceAssigner = null;
   
    // Einstellungen aus Deployment-Deskriptor:
    private String mCapabilitiesFile;
    private String mSourceGridPath;
    private String mShellCommand;
    private String mShellCommandParams;
    private boolean mShellCommandQuot;
    private String mWorkingDirectory;
    private String mPovrayInstallationPath;
    private String mPovrayExec;
    private boolean mPovrayWin;
    private String mDefaultDrape;
    private String mTileLocator = "TK25";
    private double mMaxArea = 1000000000.; // 1000 km^2
    private double mMinCellSize = 50.;
    private double mMinCellSizeLatLon = 4.629627e-4;
    private double mSearchRadiusMin = 49.99;
    private boolean mWebConnectProxySet;
    private String mWebConnectProxyHost;
    private int mWebConnectProxyPort;
    private String mWebConnectNonProxyHosts;
    private String mCopyrightTextContent;
    private String mCopyrightTextFont;
    private int mCopyrightTextSize;
    private T3dColor mCopyrightTextColor = new T3dColor();
    private T3dColor mPedestalColor = new T3dColor();
    private int mSessionMaxInactiveInterval = 5 * 60; // 10 min
    private short mMaxRendererInstances = 3;
    private boolean mUseTimeSlices = true;
    private int mMaxWaitTime = 5;
    private long mTimeSliceDuration = 4000;
    private long mRendererTimeout = 20000;
    private boolean mRendererImmediateTermination = false;
    private String mMonProtUser = "wtsadmin";
    private String mMonProtPasswd = "geheim";
    private boolean mLocalDebug = false;
    private String mErrMsgFile = "";

    private static final short sPNGOutput = 1;
    private static final short sJPEGOutput = 2;
    private static final short sBMPOutput = 3;

    /**
     * liest die Ablaufparameter aus dem Deployment-Deskriptor und �bertr�gt die Werte in entsprechende
     * Member-Variablen.<p>
     */
    public void fetchInitParameters()
    {
        mCapabilitiesFile = this.getInitParameter("CapabilitiesFile");
        mSourceGridPath = this.getInitParameter("SourceGridPath");
        mShellCommand = this.getInitParameter("ShellCommand");
        mShellCommand = this.getInitParameter("ShellCommand");
        mShellCommandParams = this.getInitParameter("ShellCommandParams");
        mShellCommandQuot = Boolean.valueOf(this.getInitParameter("ShellCommandQuot")).booleanValue();
        mWorkingDirectory = this.getInitParameter("WorkingDirectory");
        mPovrayInstallationPath = this.getInitParameter("PovrayInstallationPath");
        mPovrayExec = this.getInitParameter("PovrayExec");
        mPovrayWin = Boolean.valueOf(this.getInitParameter("PovrayWin")).booleanValue();
        mDefaultDrape = this.getInitParameter("DefaultDrape");
        mTileLocator = this.getInitParameter("TileLocator");
        mMaxArea = Double.parseDouble(this.getInitParameter("MaxArea"));
        mMinCellSize = Double.parseDouble(this.getInitParameter("MinCellSize"));
        mMinCellSizeLatLon = Double.parseDouble(this.getInitParameter("MinCellSizeLatLon"));
        mSearchRadiusMin = Double.parseDouble(this.getInitParameter("SearchRadiusMin"));
        mWebConnectProxySet = Boolean.valueOf(this.getInitParameter("WebConnectProxySet")).booleanValue();
        mWebConnectProxyHost = this.getInitParameter("WebConnectProxyHost");
        mWebConnectProxyPort = Integer.parseInt(this.getInitParameter("WebConnectProxyPort"));
        mWebConnectNonProxyHosts = this.getInitParameter("WebConnectNonProxyHosts");
        mCopyrightTextContent = this.getInitParameter("CopyrightTextContent");
        mCopyrightTextFont = this.getInitParameter("CopyrightTextFont");
        mCopyrightTextSize = Integer.parseInt(this.getInitParameter("CopyrightTextSize"));
        mCopyrightTextColor.setHexEncodedValue(this.getInitParameter("CopyrightTextColor"));
        mPedestalColor.setHexEncodedValue(this.getInitParameter("PedestalColor"));
        mSessionMaxInactiveInterval = Integer.parseInt(this.getInitParameter("SessionMaxInactiveInterval"));
        mMaxRendererInstances = Short.parseShort(this.getInitParameter("MaxRendererInstances"));
        mUseTimeSlices =  Boolean.valueOf(this.getInitParameter("UseTimeSlices")).booleanValue();
        mMaxWaitTime = Integer.parseInt(this.getInitParameter("MaxWaitTime"));
        mTimeSliceDuration = Long.parseLong(this.getInitParameter("TimeSliceDuration"));
        mRendererTimeout = Long.parseLong(this.getInitParameter("RendererTimeout"));
        mRendererImmediateTermination =  Boolean.valueOf(this.getInitParameter("RendererImmediateTermination")).booleanValue();
        mMonProtUser = this.getInitParameter("MonProtUser");
        mMonProtPasswd = this.getInitParameter("MonProtPasswd");
        mLocalDebug = Boolean.valueOf(this.getInitParameter("LocalDebug")).booleanValue();
        mErrMsgFile = this.getInitParameter("ErrMsgFile");
    }

/*    private String fixFilePath(String initParameter) {
        String tempString = initParameter.replace();
        return
    }*/

    /**
     * initialisiert das Servlet.<p>
     */
    public void init(ServletConfig pConf) throws ServletException
    {
        sLogger.debug("WebTerrainServlet: init() call");

        super.init(pConf);

        // Initialisierungsparameter aus "web.xml" lesen:
        this.fetchInitParameters();

        if (mUseTimeSlices)
            mTimeSliceAssigner = new TimeSliceAssigner(mMaxWaitTime, mTimeSliceDuration);

        T3dExceptionMessage.getInstance().readConfiguration(mErrMsgFile);
    }

    private HttpRequestParams fetchRequestParameters(HttpServletRequest pReq)
    {
        HttpRequestParams lReqParams = new HttpRequestParams();

        // Bekanntgabe der Anfrage-Parameter, damit diese als Defaults verf�gbar und/oder damit diese
        // getypt sind und somit automatisch geparst werden:
        lReqParams.addParameter("REQUEST", "String", "GetCapabilities");
        lReqParams.addParameter("SRS", "String", "");
        lReqParams.addParameter("BBOX", "VgEnvelope", "0.0,0.0,0.0,0.0");
        lReqParams.addParameter("POI", "VgPoint", null);
        lReqParams.addParameter("YAW", "Double", "0.0"); // Azimut in Altgrad
        lReqParams.addParameter("PITCH", "Double", "-45.0"); // Neigungswinkel in Altgrad
        lReqParams.addParameter("DISTANCE", "Double", "0.0");
        lReqParams.addParameter("AOV", "Double", "25.0");
        lReqParams.addParameter("FORMAT", "String", "image/png");
        lReqParams.addParameter("WIDTH", "Integer", "640");
        lReqParams.addParameter("HEIGHT", "Integer", "480");
        lReqParams.addParameter("EXAGGERATION", "Double", "5.0");
        lReqParams.addParameter("DRAPE", "String", "");
        lReqParams.addParameter("WMSLAYERS", "String", "");
        lReqParams.addParameter("WMSRES", "Double", "1.0");
        lReqParams.addParameter("SEARCHRADIUS", "Double", "49.99");
        lReqParams.addParameter("VISADDS", "Integer", "4");
        lReqParams.addParameter("TRANSPARENT", "Boolean", "false");
        lReqParams.addParameter("BGCOLOR", "String", "0x000000");
        lReqParams.addParameter("CACHESCENE", "Boolean", "false"); // > 0 f�r Caching!
        lReqParams.addParameter("EXCEPTIONS", "String", "application/vnd.ogc.se_xml");
        lReqParams.addParameter("QUALITY", "Integer", "75");
        lReqParams.addParameter("LIGHTINT", "Double", "1.0");
        lReqParams.addParameter("MONPROTUSER", "String", "");
        lReqParams.addParameter("MONPROTPASSWD", "String", "");

        lReqParams.fetchRequestParameters(pReq);

        // R�ckgabe der Session-spezifischen Request-Parameter:
        return lReqParams;
    }

    /**
     * bearbeitet HTTP-Post-Anfragen an das Servlet.<p>
     * @param pRequest HTTP-Anfrage-Objekt
     * @param pResponse HTTP-Antwort-Objekt
     * @throws ServletException
     * @throws IOException
     */
    public void doPost(HttpServletRequest pRequest, HttpServletResponse pResponse)
        throws ServletException, IOException
    {
        this.doGet(pRequest, pResponse);
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

        /*
Enumeration hnames = pRequest.getHeaderNames();
while(hnames.hasMoreElements()){
String header = (String) hnames.nextElement();
Enumeration headers = pRequest.getHeaders(header);
while (headers.hasMoreElements()) {
System.out.println("header: " + header + ",  value=" +  (String) headers.nextElement());
//if (header.equalsIgnoreCase("accept") && pRequest.getHeader(header).startsWith("image/"))
//    return;
}
}        */

        // Eindeutigen Tempor�rdatei-Rumpf f�r aktuelle Anfrage festlegen:
        String lTmpName = "~" + (mCounter++) + "_" + new java.util.Date().getTime();

        // Objektdefinitionen f�r Tempor�rdateien-Verwaltung:
        boolean lCacheScene = false;
        TempFileHandler lRequTmpMngr = null;
        boolean lKeepTempFilesInCaseOfError = false;
        TempFileHandler lSessionTmpMngr = null;

        try {
            // Request-Parameter holen:
            HttpRequestParams lReqParams = this.fetchRequestParameters(pRequest);
            String lSRS = (String) lReqParams.getParameterValue("SRS");
            String lRequest = (String) lReqParams.getParameterValue("REQUEST");
            GmEnvelope lBBox = (GmEnvelope) lReqParams.getParameterValue("BBOX");
            VgPoint lPoi;
            try {
                lPoi = (VgPoint) lReqParams.getParameterValue("POI");
            }
            catch (T3dException e) {
                lPoi = null;
            }
            double lYaw = ((Double) lReqParams.getParameterValue("YAW")).doubleValue();
            double lPitch = ((Double) lReqParams.getParameterValue("PITCH")).doubleValue();
            double lDistance ;
            try {
                lDistance = ((Double) lReqParams.getParameterValue("DISTANCE")).doubleValue();
            }
            catch (T3dException e) {
                lDistance = 0.;
            }
            double lAov = ((Double) lReqParams.getParameterValue("AOV")).doubleValue();
            String lFormat = (String) lReqParams.getParameterValue("FORMAT");
            int lWidth = ((Integer) lReqParams.getParameterValue("WIDTH")).intValue();
            int lHeight = ((Integer) lReqParams.getParameterValue("HEIGHT")).intValue();
            double lExaggeration = ((Double) lReqParams.getParameterValue("EXAGGERATION")).doubleValue();
            String lDrape = (String) lReqParams.getParameterValue("DRAPE");
            String lWmsLayers = (String) lReqParams.getParameterValue("WMSLAYERS");
            double lWmsRes = ((Double) lReqParams.getParameterValue("WMSRES")).doubleValue();
            double lSearchRadius = ((Double) lReqParams.getParameterValue("SEARCHRADIUS")).doubleValue();
            int lVisAdds = ((Integer) lReqParams.getParameterValue("VISADDS")).intValue();
            boolean lTransparent = ((Boolean) lReqParams.getParameterValue("TRANSPARENT")).booleanValue();
            String lBgColorHex = (String) lReqParams.getParameterValue("BGCOLOR");
            lCacheScene = ((Boolean) lReqParams.getParameterValue("CACHESCENE")).booleanValue();
            String lExceptions = (String) lReqParams.getParameterValue("EXCEPTIONS");
            int lQuality = ((Integer) lReqParams.getParameterValue("QUALITY")).intValue();
            double lLightInt = ((Double) lReqParams.getParameterValue("LIGHTINT")).doubleValue();
            String lMonProtUser = (String) lReqParams.getParameterValue("MONPROTUSER");
            String lMonProtPasswd = (String) lReqParams.getParameterValue("MONPROTPASSWD");

            if (lRequest.equalsIgnoreCase("GetCapabilities")) {
                HttpStandardResponse response = new HttpStandardResponse();
                response.sendXMLFile(mCapabilitiesFile, pResponse);
                this.logGetCapabilitiesInfo(lTmpName, pRequest);
                return;
            }

            if (lRequest.equalsIgnoreCase("GetMonProtocol")) {
                // hier evtl. besseren Zugriffsschutz realisieren...
                if (pRequest.getMethod().equalsIgnoreCase("POST")) {
                    if ((mMonProtUser != null && mMonProtPasswd != null &&
                         lMonProtUser.compareTo(mMonProtUser) == 0 && lMonProtPasswd.compareTo(mMonProtPasswd) == 0) ||
                        lMonProtUser.compareTo("wtsadmin") == 0 && lMonProtPasswd.compareTo("geheim") == 0)
                    {
                        this.monitorProtocol(pResponse);
                    }
                }
                return;
            }

            if (!lRequest.equalsIgnoreCase("GetView")) {
                lKeepTempFilesInCaseOfError = false;
                throw new T3dException("Illegal request type " + lRequest + "...");
            }

            // Request-Parameter aufbereiten und Wertebereiche pr�fen:
            ParameterPreparer pp = new ParameterPreparer();
            lSRS = pp.prepareSRS(lSRS);
            lBBox = pp.prepareBBOX(lBBox, lSRS);
            lPoi = pp.preparePOI(lPoi, lSRS);
            lYaw = pp.prepareYAW(lYaw);
            lPitch = pp.preparePITCH(lPitch);
            lDistance = pp.prepareDISTANCE(lDistance);
            lAov = pp.prepareAOV(lAov);
            lWmsLayers = pp.prepareWMSLAYERS(lWmsLayers);
            lWmsRes = pp.prepareWMSRES(lWmsRes);
            lTransparent = pp.prepareTRANSPARENT(lTransparent);
            T3dColor lBgColor = pp.prepareBGCOLOR(lBgColorHex);
            lWidth = pp.prepareWIDTH(lWidth);
            lHeight = pp.prepareHEIGHT(lHeight);
            lExceptions = pp.prepareEXCEPTIONS(lExceptions);
            lQuality = pp.prepareQUALITY(lQuality);
            lLightInt = pp.prepareLIGHTINT(lLightInt);

            sLogger.debug("WebTerrainServlet (" + lTmpName + "): Received GetView request.");

            if (lSearchRadius < mSearchRadiusMin) // todo
                lSearchRadius = mSearchRadiusMin;

            if (! lBBox.hasMetricSRS()) {
                if (lBBox.hasGeographicSRS()) {
                    // metrisch ben�tigte Parameter umrechnen
                    lExaggeration /= Wgs84Helper.degree2meter;
                    lSearchRadius /= Wgs84Helper.degree2meter;
                }
                else
                    throw new T3dException("Missing SRS support for \"" + lBBox.getSRS() + "\".");
            }

            // Tempor�rdateien-Verwaltung gew�hrleisten und ggf. Session-Objekt holen:
            WTSSession lWtsSession = new WTSSession();
            lWtsSession = this.setUpSession(lWtsSession, lCacheScene, pRequest, lBBox, lDrape, lWmsLayers);
            HttpSession lSession = lWtsSession.getHttpSession();
            lRequTmpMngr = lWtsSession.getRequTempFileHandler();
            if (lCacheScene)
                lSessionTmpMngr = lWtsSession.getSessionTempFileHandler();
            lTimeProt.setFinished("init");

            // H�henmodell berechnen (Gridding):
            lTimeProt.addTimeStamp("dem_access");
            VgElevationGrid lTerrain = this.setUpTerrain(lCacheScene, lSession, lBBox, lHeight, lWidth, lSearchRadius);
            lTimeProt.setFinished("dem_access");

            // Drape holen:
            lTimeProt.addTimeStamp("drape_access");
            String lDrapeFile = this.setUpDrape(
                lCacheScene, lWtsSession, lDrape, lWmsLayers, lBBox, lSRS, lWidth, lTerrain, lTmpName, lWmsRes);
            if (mLocalDebug) System.out.println("lDrapeFile = \"" + lDrapeFile + "\"");
            lTimeProt.setFinished("drape_access");

            // POV-Ray-Szene definieren:
            lTimeProt.addTimeStamp("scene_def");
            if (lDistance == 0. && lPoi == null)
                lDistance = this.determineDefaultDistance(lTerrain, lYaw * Math.PI/180., lExaggeration, 2. * lAov);
            VsSimpleScene lScene = this.defineScene(
                lTerrain, lDrapeFile, lVisAdds,
                lDistance, lPoi,
                lYaw * Math.PI/180., -lPitch * Math.PI/180., lExaggeration, 2. * lAov);
            lScene.setBackgroundColor(lBgColor);
            lRequTmpMngr.addTempFile(mWorkingDirectory + "/" + lTmpName + ".pov");
            lRequTmpMngr.addTempFile(mWorkingDirectory + "/" + lTmpName + ".bat");
            lTimeProt.setFinished("scene_def");

            // POV-Ray-Umgebung setzen und Szene rendern: // todo: alles noch zu POV-Ray-spezifisch -> Interface bauen/erweitern?  / j3dwts-Servlet mit gemeinsamem Kern?
            lTimeProt.addTimeStamp("rendering");
            this.configureRenderer(lScene, lWidth, lHeight, lTmpName, lLightInt);
            String lGifEncodedDEM = null;
            if (lCacheScene) // Dateinamen f�r GIF-kodiertes H�henmodell aus Session holen
                lGifEncodedDEM = (String) lSession.getAttribute("demgif_" + lSession.getId());
            boolean lInstanceOverflow = false;
            try {
                if (mUseTimeSlices) {
                    Long lTimeSlice = mTimeSliceAssigner.getAssignedSlice();
                    if (lTimeSlice != null) {
                        long lTimeSliceStart = lTimeSlice.longValue();
                        long lCurrTime = mTimeSliceAssigner.currentTime();
                        if (lTimeSliceStart > lCurrTime) {
                            long lDelay = lTimeSliceStart - lCurrTime;
                            sLogger.debug("WebTerrainServlet (" + lTmpName + "): " +
                                "Rendering process will be delayed for " + lDelay + " msecs...");
                            Thread.sleep(lDelay);
                        }
                        else {
                            // Bem.: F�r lTimeSliceStart <= lCurrTime keine Verz�gerung
                            if (mLocalDebug) System.out.println("Rendering process will not be be delayed..."); 
                        }
                    }
                    else {
                        // keine freie Zeitscheibe verf�gbar
                        lKeepTempFilesInCaseOfError = false;
                        throw new T3dException("The server is too busy at the moment. Please try again later.", 100);
                    }
                }

                if (mRendererInstances < mMaxRendererInstances) {
                    mRendererInstances++;
                    try {
                        ((PovrayScene) lScene).setLocalDebug(mLocalDebug);

                        if (lGifEncodedDEM == null) // CACHESCENE nicht gesetzt oder 1. Aufruf (GIF-Dateiname nicht in Session)
                            ((PovrayScene) lScene).render();
                        else
                            ((PovrayScene) lScene).renderCachedDEM(lGifEncodedDEM);
                    }
                    catch (Throwable e) {
                        mRendererInstances--;
                        lKeepTempFilesInCaseOfError = true;
                        throw e;
                    }
                    //System.out.println("mRendererInstances = " + mRendererInstances);
                    mRendererInstances--;
                }
                else
                    lInstanceOverflow = true;
            }
            catch (T3dException e) {
                lKeepTempFilesInCaseOfError = true;
                throw e;
            }
            catch (Exception e) {
                lKeepTempFilesInCaseOfError = true;
                throw new T3dException("For unknown reasons, the scene could not be rendered...", 400);
            }
            if (lInstanceOverflow) {
                lKeepTempFilesInCaseOfError = false;
                throw new T3dException("The server is too busy at the moment. Please try again later.", 101);
            }
            if (lCacheScene) {
                // GIF in Session legen
                lSession.setAttribute("demgif_" + lSession.getId(), ((PovrayScene) lScene).getGifEncodedDEM());
                lSessionTmpMngr.addTempFile(((PovrayScene) lScene).getGifEncodedDEM());
            } else
                lRequTmpMngr.addTempFile(((PovrayScene) lScene).getGifEncodedDEM());
            lRequTmpMngr.addTempFile(mWorkingDirectory + "/" + lTmpName + ".png");
            if (! mRendererImmediateTermination)
                lTimeProt.setFinished("rendering");

            // POV-Ray-Ergebnisbild holen:
            if (! mRendererImmediateTermination)
                lTimeProt.addTimeStamp("prepare_image");
            String resExt = MimeTypeHelper.getFileExtension(((PovrayScene) lScene).getImageFormat());
            String resFile = mWorkingDirectory + "/" + lTmpName + "." + resExt;
            sLogger.debug("WebTerrainServlet (" + lTmpName + "): Preparing image \"" + resFile + "\"...");
            File f = new File(resFile);
            ImageInputStream is = ImageIO.createImageInputStream(f);
            if (! mRendererImmediateTermination) {
                if (is == null) {
                    lKeepTempFilesInCaseOfError = true;
                    throw new T3dException("For unknown reasons, the renderer did not generate an image file.", 401);
                }
            }
            else {
                int lMaxIntervalChecks = 40;
                long lCheckInterval = mRendererTimeout / lMaxIntervalChecks;
                int ct = 0;
                while (is == null && ct < lMaxIntervalChecks) {
                    ct++;
                    try {
                        Thread.sleep(lCheckInterval);
                    }
                    catch (Exception e2) {};
                    is = ImageIO.createImageInputStream(f);
                }
                if (is == null && ct >= lMaxIntervalChecks) {
                    lKeepTempFilesInCaseOfError = true;
                    throw new T3dException("An I/O exception occured. The renderer did not generate an image file.", 402);
                }
                lTimeProt.setFinished("rendering");
                lTimeProt.addTimeStamp("prepare_image");
            }
            BufferedImage lImage = this.prepareImage(is,
                lHeight, lWidth,
                lTerrain, lPitch, lYaw, lDistance, lExaggeration,
                (lVisAdds & 4) > 0, (lVisAdds & 8) > 0);
            lTimeProt.setFinished("prepare_image");

            // Ergebnisbild als Antwort senden:
            lTimeProt.addTimeStamp("send_response");
            try {
                this.sendResponse(lFormat, pResponse, lImage, resExt, lQuality);
            }
            catch (Throwable e) {
                is.close();
                lKeepTempFilesInCaseOfError = false;
                throw e;
            }
            is.close();
            lTimeProt.setFinished("send_response");

            String lOutputFormatInfo = lFormat + " (" + lWidth + "x" + lHeight + ")";
            this.logGetViewInfo(lTmpName, lTerrain, lTimeProt, pRequest, lOutputFormatInfo);
            //this.removeTempFiles(lRequTmpMngr, lSessionTmpMngr, lCacheScene);
            sLogger.debug("WebTerrainServlet (" + lTmpName + "): Duly finished execution.");
        }
		catch (Throwable e) {
            sLogger.debug("WebTerrainServlet (" + lTmpName + "): Aborting execution. Error: " + e.getMessage());

            if (! lKeepTempFilesInCaseOfError)
                this.removeTempFiles(lRequTmpMngr, lSessionTmpMngr, lCacheScene);

            HttpStandardResponse response = new HttpStandardResponse();
            try {      
                String lExceptions = (String) this.fetchRequestParameters(pRequest).getParameterValue("EXCEPTIONS");
                if (lExceptions.equalsIgnoreCase("application/vnd.ogc.se_inimage")) {
                    int lWidth = ((Integer) this.fetchRequestParameters(pRequest).getParameterValue("WIDTH")).intValue();
                    int lHeight = ((Integer) this.fetchRequestParameters(pRequest).getParameterValue("HEIGHT")).intValue();
                    String lFormat = (String) this.fetchRequestParameters(pRequest).getParameterValue("FORMAT");
                    response.sendException(T3dExceptionMessage.getInstance().translate(e), pResponse, lFormat, lWidth, lHeight);
                }
                else
                    response.sendException(e.getMessage(), pResponse);
            }
            catch (Throwable e2) {
                try {
                    response.sendException(e.getMessage(), pResponse);
                }
                catch (Throwable e3) {
                    System.out.println("WebTerrainServlet: FATAL ERROR - " + e2.getMessage());
                }
            }
            try {
                this.logErrorInfo(lTmpName, lTimeProt, pRequest, e);
            }
            catch (Throwable e2) {
                System.out.println("WebTerrainServlet: FATAL ERROR - " + e2.getMessage());
            }
        }
    } // doGet()

    private WTSSession setUpSession(
        WTSSession pWtsSession, boolean pCacheScene, HttpServletRequest pRequest,
        VgEnvelope pBBox, String pDrape, String pWmsLayers)
    {
        HttpSession lSession;
        TempFileHandler lRequTmpMngr;
        TempFileHandler lSessionTmpMngr = null;

        if (pCacheScene)
        {
            lSession = pRequest.getSession(true);
            if (lSession == null)
                throw new T3dException("Could not get session object...", 102);

            lRequTmpMngr = new TempFileHandler();

            if (lSession.isNew()) {
                lSession.setMaxInactiveInterval(mSessionMaxInactiveInterval);
                lSessionTmpMngr = new TempFileHandler();
                lSession.setAttribute("shndlr_" + lSession.getId(), lSessionTmpMngr);
            }
            else {
                lSessionTmpMngr = (TempFileHandler) lSession.getAttribute("shndlr_" + lSession.getId());
                if (lSessionTmpMngr == null) {
                    // Session nicht neu, aber lTmpMngr nicht in Session, Fall tritt z. B. in JSP-Client auf.
                    lSessionTmpMngr = new TempFileHandler();
                    lSession.setAttribute("shndlr_" + lSession.getId(), lSessionTmpMngr);
                }
                else {
                    // Parameterwerte der letzten Anfrage holen...
                    VgEnvelope oldBBox = (VgEnvelope) lSession.getAttribute("rqBBOX_" + lSession.getId()); // BBOX
                    String oldDrape = (String) lSession.getAttribute("rqDRAPE_" + lSession.getId()); // DRAPE
                    String oldWmsLayers = (String) lSession.getAttribute("rqWMSLAYERS_" + lSession.getId()); // WMSLAYERS
                    boolean changesBBox = false, changesDrp = false;
                    // BBOX seit letzter Anfrage ge�ndert?
                    if (oldBBox != null && !oldBBox.isSpatiallyEquivalent(pBBox)) changesBBox = true;
                    // DRAPE seit letzter Anfrage ge�ndert?
                    if (oldDrape != null && oldDrape.compareTo(pDrape) != 0) changesDrp = true;
                    // WMSLAYERS seit letzter Anfrage ge�ndert?
                    if (oldWmsLayers != null && oldWmsLayers.compareTo(pWmsLayers) != 0) changesDrp = true;

                    // ... und im Falle relevanter �nderungen Cache-Inhalte leeren:
                    if (changesBBox) {
                        lSession.removeAttribute("terrain_" + lSession.getId());
                        lSessionTmpMngr.removeTempFile((String) lSession.getAttribute("demgif_" + lSession.getId()));
                        lSession.removeAttribute("demgif_" + lSession.getId());
                    }
                    if (changesDrp || changesBBox) {
                        lSessionTmpMngr.removeTempFile((String) lSession.getAttribute("drape_" + lSession.getId()));
                        lSession.removeAttribute("drape_" + lSession.getId());
                    }
                }
                lSession.setAttribute("rqBBOX_" + lSession.getId(), pBBox); // BBOX in Session legen
                lSession.setAttribute("rqDRAPE_" + lSession.getId(), pDrape); // DRAPE in Session legen
                lSession.setAttribute("rqWMSLAYERS_" + lSession.getId(), pWmsLayers); // WMSLAYERS in Session legen
            }
        }
        else {
            // F�r CACHESCENE=false ggf. Objekte aus vorherigen Aufrufen mit CACHESCENE=true aus Session entfernen:
            lSession = pRequest.getSession(false);
            if (lSession != null) {
                lSession.removeAttribute("shndlr_" + lSession.getId());
                lSession.removeAttribute("terrain_" + lSession.getId());
                lSession.removeAttribute("drape_" + lSession.getId());
                lSession.removeAttribute("demgif_" + lSession.getId());
                lSession.invalidate();
            }
            lRequTmpMngr = new TempFileHandler();
        }
        pWtsSession.setHttpSession(lSession);
        pWtsSession.setRequTempFileHandler(lRequTmpMngr);
        pWtsSession.setSessionTempFileHandler(lSessionTmpMngr);
        return pWtsSession;
    }

    private VgElevationGrid setUpTerrain(
        boolean pCacheScene, HttpSession pSession, GmEnvelope pBBox, int pHeight, int pWidth, double pSearchRadius)
    {
        VgElevationGrid lTerrain = null;
        if (pCacheScene) // H�henmodell aus Session holen
            lTerrain = (VgElevationGrid) pSession.getAttribute("terrain_" + pSession.getId());
        if (lTerrain == null) { // CACHESCENE nicht gesetzt oder kein g�ltiges Terrain in Session
            DEMServiceHelpers lHlp = new DEMServiceHelpers(mMaxArea);
            lHlp.setLocalDebug(mLocalDebug);
            double lCellSize = Math.min(pBBox.getExtentX()/pHeight, pBBox.getExtentY()/pWidth);
            if (pBBox.hasGeographicSRS())
                lCellSize = Math.max(lCellSize, mMinCellSizeLatLon);
            else
                lCellSize = Math.max(lCellSize, mMinCellSize);

            try {
                lTerrain = lHlp.setUpDEM(
                    pBBox.getLowerLeftFrontCorner(), pBBox.getUpperRightBackCorner(),
                    lCellSize, pSearchRadius, mTileLocator, mSourceGridPath);
            }
            catch (T3dException e) {
                String[] errGrd = lHlp.missingGridCells();
                if (errGrd != null) {
                    if (mLocalDebug) {
                        for (int i = 0; i < errGrd.length; i++)
                            System.out.println(errGrd[i]);
                    }
                }
                throw e;
            }

            if (pCacheScene)
                pSession.setAttribute("terrain_" + pSession.getId(), lTerrain); // Terrain in Session legen
        }
        return lTerrain;
    }

    private String setUpDrape(
        boolean pCacheScene, WTSSession pWtsSession, String pDrape, String pWmsLayers, GmEnvelope pBBox, String pSRS,
        int pWidth, VgElevationGrid pTerrain, String pTmpName, double pWmsRes)
    {
        String lDrapeFile = null;

        int lCase = 0;
        if (pDrape == null || pDrape.length() <= 0) lCase = 1; // kein Drape
        else {
            if (pDrape.toLowerCase().startsWith("http:")) lCase = 2; // URL als Drape (Standard-HTTP)
            if (pDrape.toLowerCase().startsWith("https:")) lCase = 3; // URL als Drape (HTTPS)
        }
        if (lCase == 2 && pWmsLayers != null && pWmsLayers.length() > 0) lCase = 4; // WMS als Drape

        if (pCacheScene) // Drape aus Session holen
            lDrapeFile = (String)
                pWtsSession.getHttpSession().getAttribute("drape_" + pWtsSession.getHttpSession().getId());

        if (lDrapeFile == null) { // CACHESCENE nicht gesetzt oder kein g�ltiger Drape-Dateiname in Session
            switch (lCase) {
                case 1: // kein Drape
                    lDrapeFile = mDefaultDrape; // lokale Drape-Datei
                    break;
                case 2: // HTTP-URL als Drape
                    lDrapeFile = this.getImage(pTmpName, pDrape, false);
                    if (lDrapeFile == null)
                        throw new T3dException("For unknown reasons, the requested drape URL (HTTP) did not provide an image.", 300);
                    break;
                case 3: // HTTPS-URL als Drape
                    lDrapeFile = this.getImage(pTmpName, pDrape, true);
                    if (lDrapeFile == null)
                        throw new T3dException("For unknown reasons, the requested drape URL (HTTPS) did not provide an image.", 300);
                    break;
                case 4: // WMS als Drape
                    int lHeight = pWidth * pTerrain.numberOfRows() / pTerrain.numberOfColumns();
                    lDrapeFile = this.getMapWMS(pTmpName, pDrape, pWmsLayers, pWidth, lHeight, pBBox, pSRS, pWmsRes);
                    if (lDrapeFile == null)
                        throw new T3dException("For unknown reasons, the requested WMS did not provide an image.", 301);
                    break;
                default:
                    throw new T3dException("Logical error in WebTerrainServlet#doGet.");
            }
            if (pCacheScene) // Drape-Dateiname in Session legen
                pWtsSession.getHttpSession().setAttribute("drape_" + pWtsSession.getHttpSession().getId(), lDrapeFile);
            if (lCase > 1) {
                if (pCacheScene)
                    pWtsSession.getSessionTempFileHandler().addTempFile(lDrapeFile);
                else
                    pWtsSession.getRequTempFileHandler().addTempFile(lDrapeFile);
            }
        }
        // Nachbedingung: lDrapeFile enth�lt lokalen Pfad f�r Bilddatei
        return lDrapeFile;
    }

    private double determineDefaultDistance(
        VgElevationGrid pTerrain, double pLambda, double pExaggeration, double lFovy)
    {
        // 1. VsSimpleScene instanzieren und Relief setzen
        VsSimpleScene lScene = new PovrayScene();
        // Statt PovrayScene k�nnte hier eine beliebige andere VsSimpleScene-Implementierung verwendet werden, da die
        // Szene nur zur Bestimmung einer Abstandsvorgabe dient...
        lScene.setTerrain(pTerrain);
        lScene.setDefaultExaggeration(pExaggeration);

        // 2. Vorgabe-Abstand ermitteln:
        VgEnvelope env = this.getRotatedBBoxEnvelope(lScene.getAspect(), pLambda);
        boolean orthographicView = false;
        if (Math.abs(lFovy) < 0.001) // eigentl.: falls lFovy = 0
            orthographicView = true;
        double radius;
        if (orthographicView)
            return -1.; // Dieser Fall ist in this.defineScene() gesondert handzuhaben...
        else
            radius = 0.5 * Math.max(env.getExtentX(), env.getExtentY()) / Math.tan(lFovy/2. * Math.PI/180.);
        return radius/lScene.getScale();
    }

    private VsSimpleScene defineScene(
        VgElevationGrid pTerrain, String pDrape, int pVisAdds,
        double pDistance, VgPoint pPoi,
        double pLambda, double pPhi, double pExaggeration, double lFovy)
    {
        // Szene instanzieren, Relief und Drape setzen:
        VsSimpleScene lScene = new PovrayScene(); // todo: dynamisch instanziieren; Klassenname und Renderer-spez. Properties-Dateiname aus "web.xml"
        ((PovrayScene) lScene).setRendererTimeout(mRendererTimeout);
        ((PovrayScene) lScene).setImmediateTermination(mRendererImmediateTermination);
        lScene.setTerrain(pTerrain);
        lScene.setDrape(pDrape);
        lScene.setDefaultExaggeration(pExaggeration);

        // Szene mit Kamera und Ansichtspunkt versehen:
        VsCamera lCam = new VsCamera();
        lScene.addCamera(lCam);
        VsViewpoint lViewpoint = new VsViewpoint();
        lCam.addViewpoint(lViewpoint);

        // Pr�fen, ob im Weiteren orthografische Ansicht zu generieren ist:
        boolean orthographicView = false;
        if (Math.abs(lFovy) < 0.001) // eigentl.: falls lFovy = 0
            orthographicView = true;

        // Aktuelle Ansichtspunktinformation setzen, falls kein POI in Anfrage angegeben:
        double radius, x, y, z, d_xy;
        if (pPoi == null) {
            if (!orthographicView) {
                // Bem.: Bedingung pDistance >= 0 ist sicherzustellen.
                if (pDistance > 0.)
                    radius = lScene.getScale() * pDistance;
                else { // pDistance = 0; z. B., falls im Request nicht angegeben
                    VgEnvelope env = this.getRotatedBBoxEnvelope(lScene.getAspect(), pLambda);
                    radius = 0.5 * Math.max(env.getExtentX(), env.getExtentY()) / Math.tan(lFovy/2. * Math.PI/180.);
                }
                lCam.setProjection(VsCamera.PerspectiveView);
                lCam.setFovy(lFovy);
            }
            else { // orthografische Ansicht
                radius = 10.; // beliebig, ungleich 0, >> 1 (wegen Clipping-Plane in POV-Ray)
                lCam.setProjection(VsCamera.OrthographicView);
            }
            d_xy = radius * Math.cos(pPhi);
            x = -d_xy * Math.sin(pLambda);
            y = -d_xy * Math.cos(pLambda);
            z = radius * Math.sin(pPhi); // -pi/2. <= pPhi <= pi/2.!
            //System.out.println("d_xy = " + d_xy);
            //System.out.println("x = " + x + ", y = " + y + ", z = " + z);

            double z_offset = 0.5 * (lScene.normZMax() + lScene.normZMin());
            lViewpoint.setLookFrom(lScene.denorm(new T3dVector(x, y, z/pExaggeration + z_offset)));
            lViewpoint.setLookAt(lScene.denorm(new T3dVector(0., 0., z_offset)));
            if (d_xy <= 1.e-6) // Sonderfall Draufsicht
                lViewpoint.setLookUp(new T3dVector(Math.sin(pLambda), Math.cos(pLambda), 0.));
        }

        // Ansichtspunktinformation setzen, falls POI in Anfrage angegeben:
        if (pPoi != null) {
            if (orthographicView)
                throw new T3dException("Please specify a POI, or choose an perspective view.");
            lCam.setProjection(VsCamera.PerspectiveView);
            lCam.setFovy(lFovy);

            T3dVector normPoi = lScene.norm(pPoi);

            if (Math.abs(pDistance) < 0.0001) {
                radius = -10.; // beliebig, < 0
                d_xy = radius * Math.cos(pPhi);
                x = -d_xy * Math.sin(pLambda);
                y = -d_xy * Math.cos(pLambda);
                z = radius * Math.sin(pPhi); // -pi/2. <= pPhi <= pi/2.

                lViewpoint.setLookFrom(pPoi);
                lViewpoint.setLookAt(lScene.denorm(new T3dVector(
                    normPoi.getX() + x,
                    normPoi.getY() + y,
                    normPoi.getZ() + z/pExaggeration)));
            }
            else {
                radius = lScene.getScale() * pDistance;
                d_xy = radius * Math.cos(pPhi);
                x = -d_xy * Math.sin(pLambda);
                y = -d_xy * Math.cos(pLambda);
                z = radius * Math.sin(pPhi); // -pi/2. <= pPhi <= pi/2.

                lViewpoint.setLookFrom(lScene.denorm(new T3dVector(
                    normPoi.getX() + x,
                    normPoi.getY() + y,
                    normPoi.getZ() + z/pExaggeration)));
                lViewpoint.setLookAt(pPoi);
            }
            if (d_xy <= 1.e-6) // Sonderfall Draufsicht
                lViewpoint.setLookUp(new T3dVector(Math.sin(pLambda), Math.cos(pLambda), 0.));
            //System.out.println("d_xy = " + d_xy);
            //System.out.println("x = " + x + ", y = " + y + ", z = " + z);
        }

        //System.out.println("lookFrom = " + lScene.getCurrentViewpoint().getLookFrom());
        //System.out.println("lookAt = " + lScene.getCurrentViewpoint().getLookAt());
        //System.out.println("lookUp = " + lScene.getCurrentViewpoint().getLookUp());

        // Erweiterte Darzustellungsparameter setzen:
        lScene.drawBBoxShape(false);
        if ((pVisAdds & 1) > 0) lScene.drawBBoxShape(true);
        lScene.drawTerrainPedestal(false);
        if ((pVisAdds & 2) > 0) {
            lScene.setPedestalColor(mPedestalColor);
            lScene.drawTerrainPedestal(true);
        }

        // Generierte Szene zur�ckgeben:
    	return lScene;
    }

    private VgEnvelope getRotatedBBoxEnvelope(double pAspect, double pAngle)
    {
        VgEnvelope bBox;
        if (pAspect > 1.)
            bBox = new GmEnvelope(-pAspect, pAspect, -1., 1., 0., 0.);
        else
            bBox = new GmEnvelope(-1., 1., -pAspect, pAspect, 0., 0.);
        return ((GmEnvelope) bBox).rotateXY(pAngle).envelope();
    }

    private String getMapWMS(
        String pTmpName,
        String lDrapeFile, String lWmsLayers, int pWidth, int pHeight, VgEnvelope lBBox, String lSRS,
        double pWmsRes)
    {
        // WMS-Konfiguration:
        String layers[] = lWmsLayers.split(",");
        float resFact = (float) pWmsRes;
        WMSRequestConfig lWmsRequest = new WMSRequestConfig(
            lDrapeFile, // URL-Pr�fix
            layers, // abzufragende Layer
            lBBox, // BBox
            lSRS, // SRS
            Math.round(((float) pWidth) * resFact), Math.round(((float) pHeight) * resFact), // Bildbreite und -h�he
            "image/jpeg"); // Bildformat // todo
        lWmsRequest.setStyles(true, "");

        // WMS-Zugriff:
        IoWMSConnector lConn = new IoWMSConnector(lWmsRequest);
        if (mWebConnectProxySet)
            lConn.connector().setProxy(mWebConnectProxyHost, mWebConnectProxyPort);
        sLogger.debug("WebTerrainServlet (" + pTmpName + "): WMS request \"" + lConn.getRequestConfiguration().getMapRequestURL() + "\"");
        String resFile = mWorkingDirectory + "/" + pTmpName + "-wms.jpg";
        try {
            lConn.getMap(resFile);
        }
        catch (Exception e) {
            return null; // von aufrufender Methode abzufangen
        }
        return resFile;
    }

    private String getImage(String pTmpName, String pDrapeURL, boolean pHttps) // todo: HTTPS-Zugriff wird noch nicht unterst�tzt
    {
        if (mWebConnectProxySet) {
            System.setProperty("proxySet", "true");
            //System.out.println("mWebConnectProxyHost = " + mWebConnectProxyHost);
            System.setProperty("http.proxyHost", mWebConnectProxyHost);
            //System.out.println("mWebConnectProxyPort = " + mWebConnectProxyPort);
            System.setProperty("http.proxyPort", "" + mWebConnectProxyPort);
            //System.out.println("mWebConnectNonProxyHosts = " + mWebConnectNonProxyHosts);
            System.setProperty("http.nonProxyHosts", mWebConnectNonProxyHosts);
        }
        else
            System.setProperty("proxySet", "false");

        IoHttpURLReader lConn = new IoHttpURLReader(pDrapeURL);
        String fileExtension = FileTools.getExtension(pDrapeURL);
        //String resFile = mWorkingDirectory + "/" + pTmpName + "-drp.jpg";
        String resFile = mWorkingDirectory + "/" + pTmpName + "-drp." + fileExtension;
        sLogger.debug("WebTerrainServlet (" + pTmpName + "): Image request \"" + lConn.getURL() + "\"");

        try {
            lConn.getContent(resFile);
        }
        catch (Exception e) {
            return null; // von aufrufender Methode abzufangen
        }
        return resFile;
    }

    private void configureRenderer(
        VsSimpleScene pScene, int pWidth, int pHeight, String pTmpName, double pLightIntensity)
    {
        ((PovrayScene) pScene).setShellCommand(mShellCommand);
        ((PovrayScene) pScene).setShellCommandParams(mShellCommandParams);
        ((PovrayScene) pScene).setShellCommandQuot(mShellCommandQuot);
        ((PovrayScene) pScene).setWorkingDirectory(mWorkingDirectory);
        ((PovrayScene) pScene).setPovrayInstallationPath(mPovrayInstallationPath);
        ((PovrayScene) pScene).setPovrayExecutable(mPovrayExec);
        ((PovrayScene) pScene).setPovrayWin(mPovrayWin);
        ((PovrayScene) pScene).setImageFormat("image/png");
        ((PovrayScene) pScene).setImageSize(pWidth, pHeight);
        ((PovrayScene) pScene).setTempName(pTmpName);
        ((PovrayScene) pScene).setQuality((short) 3); // erstmal nicht von au�en konfigurierbar
        ((PovrayScene) pScene).setLightIntensity(2. * pLightIntensity);
        ((PovrayScene) pScene).setDisplayVisible(false);
    }

    private BufferedImage prepareImage(ImageInputStream pImageIn,
        int pHeight, int pWidth,
        VgElevationGrid pTerrain, double pPitch, double pYaw, double pDistance, double pExaggeration,
        boolean pDrawNorthArrow, boolean pHints)
    {
        BufferedImage lImage = null;
        Iterator iter = ImageIO.getImageReaders(pImageIn); // liefert PNG-ImageReader
        ImageReader reader = (ImageReader) iter.next();
        reader.setInput(pImageIn, true); // seek forward only?

        boolean ready = false;
        int lMaxIntervalChecks = 40;
        long lCheckInterval = mRendererTimeout / lMaxIntervalChecks;
        int ct = 0;
        while ((! ready) && (ct < lMaxIntervalChecks)) {
            try {
                lImage = reader.read(0);
                ready = true;
            }
            catch (IOException e) {
                ct++;
                try {
                    Thread.sleep(lCheckInterval);
                }
                catch (Exception e2) {};
            }
        }
        if (! ready)
            throw new T3dException("An I/O exception occured. The renderer did not generate an image file.", 403);

        // Copyright-Text und Nordpfeil erg�nzen:
        this.addAnnotations(lImage, pHeight, pWidth, pPitch, pYaw, pDrawNorthArrow);
        // Ggf. weitere Information (Hints) erg�nzen:
        if (pHints)
            this.addHints(lImage, pTerrain, pDistance, pYaw, pExaggeration);

        return lImage;
    }

    private void addAnnotations(BufferedImage pImage,
        int pHeight, int pWidth, double pPitch, double pYaw, boolean pDrawNorthArrow)
    {
        if (mCopyrightTextContent.length() > 0)
        {
            Graphics2D g = pImage.createGraphics();
            g.drawImage(pImage, 0, 0, null);
            g.setColor(new java.awt.Color(
                mCopyrightTextColor.getRed(), mCopyrightTextColor.getGreen(), mCopyrightTextColor.getBlue()));

            // 1. Copyright-Vermerk
            // Etwas unsch�n: Durch JPEG-Komprimierung wird Text (insb. bei kleiner Font-Gr��e) wird unscharf...
            // TODO: Abhilfe evtl. durch Hintergrund?
            Font font = new Font(mCopyrightTextFont, Font.BOLD /* Style als int, siehe ggf. API-Dok.*/, mCopyrightTextSize);
            g.setFont(font);
            // mehrzeilige Copyright-Texte erlauben:
            StringTokenizer str = new StringTokenizer(mCopyrightTextContent, "\n");
            int spacePerRow = mCopyrightTextSize;
            int rows = str.countTokens();
            int startPos = spacePerRow * rows;
            int currRow = 0;
            while (str.hasMoreTokens()) {
                int yPos = pHeight - (startPos - (currRow * spacePerRow)) + spacePerRow / 2;
                g.drawString(str.nextToken().trim(), 5, yPos);
                currRow++;
            }

            // 2. Nordpfeil
            if (pDrawNorthArrow) {
                // Zeichenparameter:
                double radius = 35.;
                double phi = 15.;
                // Symbolkonstruktion:
                int rx = (int) radius;
                int ry = (int) Math.round(radius * Math.sin(-pPitch * Math.PI/180.));
                int mx = pWidth - rx - 5;
                int my = pHeight - ry - 5;
                int dx = (int) (radius * Math.sin(pYaw * Math.PI/180.));
                int dy = (int) (radius * Math.sin(-pPitch * Math.PI/180.) * Math.cos(pYaw * Math.PI/180.));
                int px = mx - dx, py = my - dy; // Pfeilspitze
                int qlx = mx + (int) (radius * Math.sin((pYaw + phi) * Math.PI/180.));
                int qly = my + (int) (radius * Math.sin(-pPitch * Math.PI/180.) * Math.cos((pYaw + phi)* Math.PI/180.));
                int qrx = mx + (int) (radius * Math.sin((pYaw - phi) * Math.PI/180.));
                int qry = my + (int) (radius * Math.sin(-pPitch * Math.PI/180.) * Math.cos((pYaw - phi)* Math.PI/180.));
                // Ellipse zeichnen:
                g.setStroke(new BasicStroke(2.f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g.drawOval(mx - rx, my - ry, 2 * rx, 2 * ry);
                // Striche f�r Pfeil zeichnen:

                g.setStroke(new BasicStroke(1.f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));

                boolean fillArrow = true;
                if (fillArrow)
                    g.fill(new Polygon(new int[]{px, qlx, qrx}, new int[]{py, qly, qry}, 3));
                else {
                    g.drawLine(px, py, qlx, qly);
                    g.drawLine(px, py, qrx, qry);
                    g.drawLine(qlx, qly, qrx, qry);
                }
            }

            g.dispose();
        }
    }

    private void addHints(BufferedImage pImage,
        VgElevationGrid pTerrain, double pDistance, double pYaw, double pExaggeration)
    {
        DecimalFormatSymbols dfs = new DecimalFormatSymbols();
        dfs.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("0.0", dfs);

        String deltaX = df.format(pTerrain.getGeometry().envelope().getExtentX()/1000.);
        String deltaY = df.format(pTerrain.getGeometry().envelope().getExtentY()/1000.);

        String hint =
            "DIST: " + df.format(pDistance)
            + ", EXAGG: " + pExaggeration
            + ", YAW: " + pYaw
            + ", BBOX: " + deltaX + " x " + deltaY
            + ", DZ: " + df.format(pTerrain.elevationDifference());

        Graphics2D g = pImage.createGraphics();
        g.drawImage(pImage, 0, 0, null);
        g.setColor(new java.awt.Color(
            mCopyrightTextColor.getRed(), mCopyrightTextColor.getGreen(), mCopyrightTextColor.getBlue()));
        Font font = new Font(mCopyrightTextFont, Font.BOLD /* Style als int, siehe ggf. API-Dok.*/, mCopyrightTextSize);
        g.setFont(font);
        g.drawString(hint, 5, mCopyrightTextSize + 5);
        g.dispose();
    }

    private void sendResponse(
        String lFormat, HttpServletResponse pResponse, BufferedImage lImage, String resExt, int pQuality)
    {
        try {
            OutputStream out = pResponse.getOutputStream();
            short lOutputFormat = -1;
            if (lFormat == null || lFormat.length() <= 0) // sollte vorher abgefangen werden
                throw new T3dException("No output format specified.");
            if (lFormat.equalsIgnoreCase("image/png")) lOutputFormat = sPNGOutput;
            if (lFormat.equalsIgnoreCase("image/jpeg")) lOutputFormat = sJPEGOutput;
            if (lFormat.equalsIgnoreCase("image/bmp")) lOutputFormat = sBMPOutput;
            if (lOutputFormat <= 0) // sollte vorher abgefangen werden
                throw new T3dException("Unsupported output format \"" + lFormat + "\". ");

            switch (lOutputFormat) {
                case sPNGOutput:
                    ImageIO.setUseCache(false); // wichtig!
                    pResponse.setContentType("image/png");
                    //pResponse.setHeader("Cache-Control", "no-store");
                    try {
                        ImageIO.write(lImage, resExt, out); // resExt ist informaler Formatname...
                    }
                    catch (Exception e) {
                        throw new T3dException("Did not finish PNG image send process. " + e.getMessage(), 103);
                    }
                    // out.flush(); // Flushing besser vermeiden, damit Server die L�nge der Anbtweort bestimmen kann
                    break;
                case sJPEGOutput:
                    try {
                        //ImageIO.setUseCache(false); // wichtig!
                        pResponse.setContentType("image/jpeg");
                        ImageIO.write(lImage, "jpeg", out);
//                        JPEGImageEncoder enc = JPEGCodec.createJPEGEncoder(out); // JPEG-Encoder instanziieren
//                        JPEGEncodeParam prm = enc.getDefaultJPEGEncodeParam(lImage);
//                        prm.setQuality(((float) pQuality) / 100.f, false);
//                        enc.setJPEGEncodeParam(prm);
//                        enc.encode(lImage); // Bild als JPEG encoden und an Client senden
                    } catch (Exception e) {
                        throw new T3dException("Did not finish JPEG image send process. " + e.getMessage(), 104);
                    }
                    break;
                case sBMPOutput:
                    try {
                        // Merkw�rdig, dass nachstehender Code praktisch das korrekte Resultat liefert... (todo)
                        pResponse.setContentType("image/bmp");
                        ImageIO.write(lImage, "bmp", out);
//                        JPEGImageEncoder enc = JPEGCodec.createJPEGEncoder(out); // JPEG-Encoder instanziieren
//                        JPEGEncodeParam prm = enc.getDefaultJPEGEncodeParam(lImage);
//                        prm.setQuality(1.0f, false); // Qualit�t auf 100% setzen
//                        enc.setJPEGEncodeParam(prm);
//                        enc.encode(lImage); // Bild als JPG encoden und an Client senden
//                        ImageIO.write(lImage, "jpg", out); // !
                    } catch (Exception e) {
                        throw new T3dException("Did not finish BMP image send process. " + e.getMessage(), 105);
                    }
                    break;
            }
            out.close();
        }
        catch (IOException e) {
            throw new T3dException("An I/O exception occured. The servlet could not send an image reponse.", 106);
        }
    }

    private void removeTempFiles(TempFileHandler pRequTmpMngr, TempFileHandler pSessionTmpMngr, boolean pCacheScene)
    {
        if (pRequTmpMngr != null)
            pRequTmpMngr.removeTempFiles();  // nach jeder HTTP-Anfrage zu l�schende Tempor�rdateien

        if (! pCacheScene && pSessionTmpMngr != null)
            pSessionTmpMngr.removeTempFiles(); // nach jeder Session zu l�schende Tempor�rdateien
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

    private void logGetViewInfo(
        String pTmpName,
        VgElevationGrid pTerrain, T3dTimeList pTimeProt, HttpServletRequest pRequest, String pOutputInfo)
    {
        try {
            PrintWriter lDat = new PrintWriter(new FileWriter(mWorkingDirectory + "/" + pTmpName + ".log"));

            // Berarbeitungszeit und weitere Infos protokollieren:
            lDat.println("REMOTE HOST: " + pRequest.getRemoteHost());
            lDat.println("REMOTE ADDRESS: " + pRequest.getRemoteAddr());
            //lDat.println("REMOTE USER: " + pRequest.getRemoteUser());
            lDat.println("QUERY STRING: " + pRequest.getQueryString());
            lDat.println("SESSION-ID: " + pRequest.getRequestedSessionId());
            lDat.println("BBOX: " + pTerrain.getGeometry().envelope());
            lDat.println("BBOX-SIZE: " + pTerrain.getGeometry().envelope().areaXY());
            lDat.println("CELLSIZE: " + ((GmSimple2dGridGeometry) pTerrain.getGeometry()).getDeltaX()); // �quidist.
            lDat.println("OUTPUT FORMAT: " + pOutputInfo);
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

    private void logErrorInfo(
        String pTmpName, T3dTimeList pTimeProt, HttpServletRequest pRequest, Throwable pExc)
    {
        try {
            PrintWriter lDat = new PrintWriter(new FileWriter(mWorkingDirectory + "/" + pTmpName + ".log"));

            lDat.println("REMOTE HOST: " + pRequest.getRemoteHost());
            lDat.println("REMOTE ADDRESS: " + pRequest.getRemoteAddr());
            //lDat.println("REMOTE USER: " + pRequest.getRemoteUser());
            lDat.println("QUERY STRING: " + pRequest.getQueryString());
            lDat.println("SESSION-ID: " + pRequest.getRequestedSessionId());
            lDat.println("PROCESSING_TIMES [msec]: ");
            String[] lTimeProtStr = pTimeProt.protocol();
            for (int i = 0; i < lTimeProtStr.length; i++)
                lDat.println(lTimeProtStr[i]);
            lDat.println("ERROR: " + pExc.getMessage());
            lDat.println("STACK TRACE: ");
            pExc.printStackTrace(lDat);

            lDat.close();
        }
        catch (IOException e) {
            throw new T3dException(e.getMessage());
        }
    }

    private void monitorProtocol(HttpServletResponse pResponse)
    {
        HttpStandardResponse response = new HttpStandardResponse();
        String res = "";

        File dir = new File(mWorkingDirectory);
        String[] children = dir.list();
        if (children == null) // dir ex. nicht oder ist kein Verzeichnis
            res = "Can not access monitor information...";
        else {
            int ctGif = 0, ctPov = 0, ctBat = 0, ctJpg = 0, ctPng = 0, ctLog = 0;
            for (int i = 0; i < children.length; i++) {
                String filename = children[i];
                if (filename.endsWith(".gif")) ctGif++;
                if (filename.endsWith(".jpg")) ctJpg++;
                if (filename.endsWith(".pov")) ctPov++;
                if (filename.endsWith(".bat")) ctBat++;
                if (filename.endsWith(".png")) ctPng++;
                if (filename.endsWith(".log")) ctLog++;
            }
            res = res + "Held DEM files:           " + ctGif + "\n";
            res = res + "Held Drape files:         " + ctJpg + "\n";
            res = res + "Outstanding batches:      " + ctBat + " (" + ctPov + " scene descriptions)\n";
            res = res + "Held result image files:  " + ctPng + "\n\n";
            res = res + "Accurate executions:      " + ctLog + "\n";
        }

        res = res + "\n";
        res = res + "Time-slice assigner:      ";
        if (!mUseTimeSlices)
            res = res + "not present" + "\n";
        else {
            res = res + "present" + "\n";
            res = res + "Slice interval:           " +  mTimeSliceAssigner.getSliceInterval() + " msec\n";
            res = res + "Max. wait-time:           " +  mTimeSliceAssigner.maxWaitTime() + " intervals (";
            res = res + (mTimeSliceAssigner.getSliceInterval() * mTimeSliceAssigner.maxWaitTime()) + " msec)\n";
        }

        res = res + "\n";
        res = res + "Session lifetime:         " + mSessionMaxInactiveInterval + " sec\n";

        res = res + "\n";
        res = res + "Max. renderer instances:  " + mMaxRendererInstances + "\n";

        res = res + "\n";
        res = res + "Web connection proxies:   ";
        if (!mWebConnectProxySet)
            res = res + "not set" + "\n";
        else {
            res = res + "set" + "\n";
            res = res + "Proxy host:               " +  mWebConnectProxyHost + "\n";
            res = res + "Proxy port:               " +  mWebConnectProxyPort + "\n";
            res = res + "Non-proxy hosts:          " +  mWebConnectNonProxyHosts + "\n";
        }

        response.sendMessage(res, pResponse);
    }


}
