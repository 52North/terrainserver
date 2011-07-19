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
package org.n52.v3d.terrainserver.profileservice;

import java.io.*;
import java.lang.String;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import javax.servlet.http.*;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

import org.n52.v3d.triturus.core.T3dException;
import org.n52.v3d.triturus.vgis.*;
import org.n52.v3d.triturus.web.*;
import org.n52.v3d.triturus.gisimplm.*;
import org.n52.v3d.triturus.t3dutil.T3dTimeList;
import org.n52.v3d.terrainserver.demservice.DEMServiceHelpers;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;

/**
 * Implementation of a cross-estion generation service.<br /><br />
 * <i>German:</i> Implementierung eines Profildienstes Der Dienst l&auml;sst sich auch dazu nutzen, punktuell einen
 * H&ouml;henwert abzufragen.<br />
 * Beispielaufrufe:
 * <tt>http://<hostname>/WebTerrainServlet?REQUEST=GetGraph&SRS=EPSG:31468&DEFLINE=4440675,5271075,0,4449475,5275000,0</tt>
 * <tt>http://<hostname>/WebTerrainServlet?REQUEST=GetElevation&SRS=EPSG:31466&POINT=2592761.3,5741340.4,0.0</tt>
 * @author Benno Schmidt
 */
public class ProfileServlet extends HttpServlet
{
    private Log sLogger = LogFactory.getLog(ProfileServlet.class);

    private static int mCounter = 0;

    // Einstellungen aus Deployment-Deskriptor:
    private String mCapabilitiesFile;
    private String mSourceGridPath;
    private String mWorkingDirectory;
    private String mTileLocator = "TK25";
    private double mMaxArea = 1000000000.; // 1000 km^2
    private double mMinCellSize = 50.;
    private double mMinCellSizeLatLon = 4.629627e-4;

    /**
     * liest die Ablaufparameter aus dem Deployment-Deskriptor und �bertr�gt die Werte in entsprechende
     * Member-Variablen.<p>
     */
    public void fetchInitParameters()
    {
        mCapabilitiesFile = this.getInitParameter("CapabilitiesFile");
        mSourceGridPath = this.getInitParameter("SourceGridPath");
        mWorkingDirectory = this.getInitParameter("WorkingDirectory");
        mTileLocator = this.getInitParameter("TileLocator");
        mMaxArea = Double.parseDouble(this.getInitParameter("MaxArea"));
        mMinCellSize = Double.parseDouble(this.getInitParameter("MinCellSize"));
        mMinCellSizeLatLon = Double.parseDouble(this.getInitParameter("MinCellSizeLatLon"));
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

        // Bekanntgabe der Anfrage-Parameter, damit diese als Defaults verf�gbar und/oder damit diese
        // getypt sind und somit automatisch geparst werden:
        lReqParams.addParameter("REQUEST", "String", "GetCapabilities");
        lReqParams.addParameter("DEFLINE", "VgLineString", "0.0, 0.0, 0.0, 0.0, 0.0, 0.0");
        lReqParams.addParameter("SRS", "String", "");
        lReqParams.addParameter("FORMAT", "String", "");
        lReqParams.addParameter("WIDTH", "Integer", "640");
        lReqParams.addParameter("HEIGHT", "Integer", "480");
        lReqParams.addParameter("EXAGGERATION", "Double", "5.0");
        lReqParams.addParameter("VISADDS", "Integer", "4");
        lReqParams.addParameter("POINT", "VgPoint", "0.0, 0.0, 0.0"); // nur f�r GetElevation-Anfrage

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

        // Eindeutigen Tempor�rdatei-Rumpf f�r aktuelle Anfrage festlegen:
        String lTmpName = "~" + (mCounter++) + "_" + new java.util.Date().getTime();

        try {
            // Request-Parameter holen:
            HttpRequestParams lReqParams = this.fetchRequestParameters(pRequest);
            String lRequest = (String) lReqParams.getParameterValue("REQUEST");
            VgLineString lDefLine = (VgLineString) lReqParams.getParameterValue("DEFLINE");
            String lSRS = (String) lReqParams.getParameterValue("SRS");
            String lFormat = (String) lReqParams.getParameterValue("FORMAT");
            int lWidth = ((Integer) lReqParams.getParameterValue("WIDTH")).intValue();
            int lHeight = ((Integer) lReqParams.getParameterValue("HEIGHT")).intValue();
            double lExaggeration = ((Double) lReqParams.getParameterValue("EXAGGERATION")).doubleValue(); // todo sinnvoll?
            int lVisAdds = ((Integer) lReqParams.getParameterValue("VISADDS")).intValue();
            VgPoint lPoint = (VgPoint) lReqParams.getParameterValue("POINT");

            if (lRequest.equalsIgnoreCase("GetCapabilities")) {
                HttpStandardResponse response = new HttpStandardResponse();
                response.sendXMLFile(mCapabilitiesFile, pResponse);           // todo: serverrun/Capabilities.xml einrichten und web.xml-Additions
                this.logGetCapabilitiesInfo(lTmpName, pRequest);
                return;
            }

            if (! (lRequest.equalsIgnoreCase("GetGraph") || lRequest.equalsIgnoreCase("GetElevation")))
                throw new T3dException("Illegal request type " + lRequest + "...");

            // Request-Parameter aufbereiten und Wertebereiche pr�fen:
            ParameterPreparer pp = new ParameterPreparer();
            lSRS = pp.prepareSRS(lSRS);
            lFormat = pp.prepareFORMAT(lFormat, lRequest);
            if (lRequest.equalsIgnoreCase("GetGraph")) {
                lWidth = pp.prepareWIDTH(lWidth);
                lHeight = pp.prepareHEIGHT(lHeight);
            }
            if (lRequest.equalsIgnoreCase("GetElevation")) {
                String str = ""
                    + lPoint.getX() + "," + lPoint.getY() + ",0.0," /* erster Eckpunkt = Interpolationspunkt */
                    + lPoint.getX() + "," + lPoint.getY() + ",0.0"; /* zweiter Eckpunkt als Hilfspunkt */
                lDefLine = new GmLineString(str);
                if(lPoint instanceof VgPoint)
                {
                    lPoint.setSRS(lSRS);
                }
            }

            sLogger.debug("ProfileServlet (" + lTmpName + "): Received " + lRequest + " request.");
            lTimeProt.setFinished("init");

            // H�henmodell berechnen (Gridding):
            lTimeProt.addTimeStamp("dem_access");
            final boolean lDebug = false; // todo: auf 'false' setzen
            if (lDebug)
                System.out.println("lDefLine = " + lDefLine);
            VgEnvelope lBBox = lDefLine.envelope();
            lBBox.setSRS(lSRS);
            lBBox = this.assureBBoxExtent(lBBox);
            if (lDebug)
                System.out.println("lBBox = " + lBBox);
            GmSimpleElevationGrid lTerrain = this.setUpTerrain(lBBox);
            if (lDebug) {
                System.out.println("lTerrain = " + lTerrain);
                System.out.println("lTerrain.envelope = " + lTerrain.getGeometry().envelope());
            }
            lTimeProt.setFinished("dem_access");

            // Profil generieren:
            lTimeProt.addTimeStamp("profile_generation");
            FltElevationGrid2Profile lProc = new FltElevationGrid2Profile();
            VgProfile lProfile = lProc.transform(lTerrain, lDefLine);
            lTimeProt.setFinished("profile_generation");

            if (lRequest.equalsIgnoreCase("GetGraph"))
            {
                // Ergebnisbild generieren:
                lTimeProt.addTimeStamp("rendering");
                IoProfileWriter lWriter = new IoProfileWriter("SVG"); // stets SVG generieren
                String lResFile = mWorkingDirectory + "/" + lTmpName + ".svg";
                lWriter.writeToFile(lProfile, lResFile);
                boolean lSendAsPngImage = false;
                String lResFilePng = "";
                if (lFormat.equalsIgnoreCase("image/png"))
                    lSendAsPngImage = true;
                if (lSendAsPngImage) {
                    PNGTranscoder lTranscoder = new PNGTranscoder();
                    String lSvgURI = new File(lResFile).toURL().toString();
                    TranscoderInput lInput = new TranscoderInput(lSvgURI);
                    lResFilePng = mWorkingDirectory + "/" + lTmpName + ".png"; 
                    OutputStream lOStream = new FileOutputStream(lResFilePng);
                    TranscoderOutput lOutput = new TranscoderOutput(lOStream);
                    lTranscoder.transcode(lInput, lOutput);
                    lOStream.flush();
                    lOStream.close();
                }
                lTimeProt.setFinished("rendering");

                // Ergebnisbild als Antwort senden:
                lTimeProt.addTimeStamp("send_response");
                if (! lSendAsPngImage) {
                    pResponse.setContentType("image/svg+xml"); // MIME-Typ f�r Antwort setzen
                    BufferedReader lDatRead;
                    try {
                        lDatRead = new BufferedReader(new FileReader(lResFile));
                    }
                    catch (FileNotFoundException e) {
                        throw new T3dException("Internal error while reading \"" + lResFile + "\".");
                    }
                    PrintWriter out = pResponse.getWriter(); // PrintWriter auf die Antwort aufsetzen
                    String line = lDatRead.readLine();
                    while (line != null) { // generierte Tempor�rdatei zeilenweise senden
                        out.println(line);
                        line = lDatRead.readLine();
                    }
                    lDatRead.close();
                    out.flush();
                    out.close();
                }
                else {
                    // Bild senden (vgl. Code aus WebTerrainServlet:
                    try {
                        File f = new File(lResFilePng);
                        ImageInputStream is = ImageIO.createImageInputStream(f);
                        Iterator iter = ImageIO.getImageReaders(is); // liefert PNG-ImageReader
                        ImageReader reader = (ImageReader) iter.next();
                        reader.setInput(is, true); // seek forward only?
                        BufferedImage lImage = reader.read(0);

                        OutputStream out = pResponse.getOutputStream();
                        ImageIO.setUseCache(false); // wichtig!
                        pResponse.setContentType(lFormat); // MIME-Typ f�r Antwort setzen
                        String resExt = MimeTypeHelper.getFileExtension(lFormat);
                        try {
                            ImageIO.write(lImage, resExt, out); // resExt ist informaler Formatname...
                        }
                        catch (Exception e) {
                            throw new T3dException("Did not finish PNG image send process. " + e.getMessage(), 103); // todo fehler-nr. pr�fen und in doku
                        }
                        is.close();
                        out.close();
                    }
                    catch (IOException e) {
                        throw new T3dException("An I/O exception occured. The servlet could not send an image reponse.", 106); // todo fehler-nr. pr�fen und in doku
                    }
                }
                File fSvg = new File(lResFile);
                fSvg.delete();
                if (lSendAsPngImage) {
                    File fPng = new File(lResFilePng);
                    fPng.delete();
                }
                lTimeProt.setFinished("send_response");
            }
            else {
                if (lRequest.equalsIgnoreCase("GetElevation"))
                {
                    lTimeProt.addTimeStamp("send_response");
                    double x, y, z;
                    try {
                        x = ((VgLineString)(lProfile.getGeometry())).getVertex(0).getX(); // = lPoint.getX()
                        y = ((VgLineString)(lProfile.getGeometry())).getVertex(0).getY(); // = lPoint.getY()
                        z = (lProfile.getTZPair(0))[1];
                    }
                    catch (Throwable e) {
                        throw new T3dException("No elevation information available.");
                    }

                    // Antwort senden:
                    short lCase = 0;
                    if (lFormat.equalsIgnoreCase("text/plain")) lCase = 1;
                    if (lFormat.equalsIgnoreCase("text/xml")) lCase = 2;
                    if (lFormat.equalsIgnoreCase("text/html")) lCase = 3;
                    if (lFormat.equalsIgnoreCase("text/comma-separated-values")) lCase=4;
                    if (lCase <= 0 )
                        throw new T3dException("Internal servlet error."); // Kann nicht auftreten
                    pResponse.setContentType(lFormat); // MIME-Typ f�r Antwort setzen
                    PrintWriter out = pResponse.getWriter(); // PrintWriter auf die Antwort aufsetzen
                    switch (lCase) {
                        case 1:
                            out.println("Position:");
                            out.println("SRS: " + lPoint.getSRS()); // = lSRS
                            out.println("X = " + x);
                            out.println("Y = " + y);
                            out.println("");
                            out.println("Elevation = " + z);
                            break;
                        case 2:
                            out.println("<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\" ?>");
                            out.println("<ServiceResponse>");
                            out.println("  <Position>");
                            out.println("    <SRS>" + lPoint.getSRS() + "</SRS>"); // = lSRS
                            out.println("    <X>" + x + "</X>");
                            out.println("    <Y>" + y + "</Y>");
                            out.println("  </Position>");
                            out.println("  <Elevation>" + z + "</Elevation>");
                            out.println("</ServiceResponse>");
                            break;
                        case 3:
                            out.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\">");
                            out.println("<html>");
                            out.println("<head>");
                            out.println("<title>sdi.suite terrainServer elevation information</title>");
                            out.println("<font face=\"Verdana, Arial, Helvetica\" size=1>");
                            out.println("<meta http-equiv=Content-Type content=\"text/html; charset=iso-8859-1\">");
                            out.println("<body text=#000000 bgColor=#ffffff leftMargin=0 topMargin=0>");
                            out.println("<table border=\"1\">");
                            out.println("<tr><td><b>Position:</b></td><td><br></td></tr>");
                            out.println("<tr><td>SRS:</td><td>" + lPoint.getSRS() + "</td></tr>"); // lPoint.getSRS() = lSRS
                            out.println("<tr><td>X:</td><td>" + x + "<td></tr>");
                            out.println("<tr><td>Y:</td><td>" + y + "<td></tr>");
                            out.println("<tr><td><b>Elevation:</b></td><td>" + z + "<td></tr>");
                            out.println("</table>");
                            out.println("</html>");
                            break;
                        case 4:
                            out.println(z);
                            break;
                    }
                    out.flush();
                    out.close();
                    lTimeProt.setFinished("send_response");
                }
            }

            //String lOutputFormatInfo = lFormat + " (" + lWidth + "x" + lHeight + ")";
            if (lRequest.equalsIgnoreCase("GetGraph"))
                this.logGetGraphInfo(lTmpName, lTerrain, lDefLine, lTimeProt, pRequest, lFormat /*lOutputFormatInfo*/);
            else {
                if (lRequest.equalsIgnoreCase("GetElevation"))
                    this.logGetElevationInfo(lTmpName, lTerrain, lPoint, lTimeProt, pRequest, lFormat);
            }

            sLogger.debug("ProfileServlet (" + lTmpName + "): Duly finished execution.");
        }
		catch (Throwable e) {
            sLogger.debug("ProfileServlet (" + lTmpName + "): Aborting execution. Error: " + e.getMessage());

            HttpStandardResponse response = new HttpStandardResponse();
            try {
                response.sendException(e.getMessage(), pResponse);
            }
            catch (Throwable e2) {
                try {
                    response.sendException(e.getMessage(), pResponse);
                }
                catch (Throwable e3) {
                    System.out.println("ProfileServlet: FATAL ERROR - " + e3.getMessage());
                }
            }
            try {
                this.logErrorInfo(lTmpName, lTimeProt, pRequest, e);
            }
            catch (Throwable e2) {
                System.out.println("ProfileServlet: FATAL ERROR - " + e2.getMessage());
            }
        }
    } // doGet()

    private VgEnvelope assureBBoxExtent(VgEnvelope pBBox)
    {
        double lEps = mMinCellSize;
        if (pBBox.hasGeographicSRS())
            lEps = mMinCellSizeLatLon;

        if (pBBox.getExtentX() <= lEps) {
            pBBox.setXMin(pBBox.getXMin() - lEps);
            pBBox.setXMax(pBBox.getXMax() + lEps);
        }
        if (pBBox.getExtentY() <= lEps) {
            pBBox.setYMin(pBBox.getYMin() - lEps);
            pBBox.setYMax(pBBox.getYMax() + lEps);
        }

        return pBBox;
    }

    private GmSimpleElevationGrid setUpTerrain(VgEnvelope pBBox)
    {
        GmSimpleElevationGrid lTerrain;
        double lCellSize = mMinCellSize;
        double lSearchRadius = mMinCellSize;
        if (pBBox.hasGeographicSRS()) {
            lCellSize = mMinCellSizeLatLon;
            lSearchRadius = mMinCellSizeLatLon;
        }

        double xMin = pBBox.getXMin();
        double yMin = pBBox.getYMin();
        double xMax = pBBox.getXMax();
        double yMax = pBBox.getYMax();

        VgPoint pt1 = new GmPoint(xMin, yMin, 0.);
        pt1.setSRS(pBBox.getSRS());
        VgPoint pt2 = new GmPoint(xMax, yMax, 0.);
        pt2.setSRS(pBBox.getSRS());

        DEMServiceHelpers lHlp = new DEMServiceHelpers(mMaxArea);
        try {
            lTerrain = (GmSimpleElevationGrid) lHlp.setUpDEM(pt1, pt2, lCellSize, lSearchRadius, mTileLocator, mSourceGridPath);
        }
        catch (T3dException e) {
            String[] errGrd = lHlp.missingGridCells();
            if (errGrd != null) {
                final boolean lDebug = false;
                if (lDebug) {
                    for (int i = 0; i < errGrd.length; i++)
                        System.out.println(errGrd[i]);
                }
            }
            throw e;
        }

        return lTerrain;
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

    private void logGetGraphInfo(
        String pTmpName,
        VgElevationGrid pTerrain, VgLineString pDefLine, T3dTimeList pTimeProt, HttpServletRequest pRequest, String pOutputInfo)
    {
        try {
            PrintWriter lDat = new PrintWriter(new FileWriter(mWorkingDirectory + "/" + pTmpName + ".log"));

            // Berarbeitungszeit und weitere Infos protokollieren:
            lDat.println("REMOTE HOST: " + pRequest.getRemoteHost());
            lDat.println("REMOTE ADDRESS: " + pRequest.getRemoteAddr());
            //lDat.println("REMOTE USER: " + pRequest.getRemoteUser());
            lDat.println("QUERY STRING: " + pRequest.getQueryString());
            lDat.println("SESSION-ID: " + pRequest.getRequestedSessionId());
            lDat.println("DEFLINE: " + pDefLine);
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

    private void logGetElevationInfo(
        String pTmpName,
        VgElevationGrid pTerrain, VgPoint pPoint, T3dTimeList pTimeProt, HttpServletRequest pRequest, String pOutputInfo)
    {
        try {
            PrintWriter lDat = new PrintWriter(new FileWriter(mWorkingDirectory + "/" + pTmpName + ".log"));

            // Berarbeitungszeit und weitere Infos protokollieren:
            lDat.println("REMOTE HOST: " + pRequest.getRemoteHost());
            lDat.println("REMOTE ADDRESS: " + pRequest.getRemoteAddr());
            //lDat.println("REMOTE USER: " + pRequest.getRemoteUser());
            lDat.println("QUERY STRING: " + pRequest.getQueryString());
            lDat.println("SESSION-ID: " + pRequest.getRequestedSessionId());
            lDat.println("POINT: " + pPoint);
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
}
