package org.n52.v3d.terrainserver.povraywts;

import org.n52.v3d.triturus.gisimplm.GmEnvelope;
import org.n52.v3d.triturus.vgis.VgPoint;
import org.n52.v3d.triturus.vgis.VgGeomObject;
import org.n52.v3d.triturus.t3dutil.T3dColor;
import org.n52.v3d.triturus.core.T3dException;

/**
 * Hilfsklasse zur Aufbereitung der Anfrage-Parameter und zur Überprüfung der Wertebereiche.<p>
 * Falls ein angegebener Wert außerhalb des zulässigen Wertebereichs liegt, wirft die zugehörige Methode eine
 * <tt>T3dException</tt>.<p>
 * @author Benno Schmidt<br>
 * (c) 2004-2005, con terra GmbH & Institute for Geoinformatics<br>
 */
public class ParameterPreparer
{
    /**
     * Aufbereitung/Prüfung der SRS-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public String prepareSRS(String pVal) {
        String str = pVal.toUpperCase();
        str = VgGeomObject.mapDeprecatedSRSCodes(str);
        if (!(
            str.equals("EPSG:4326")
            || str.equals("EPSG:31466") || str.equals("EPSG:31467") || str.equals("EPSG:31468")
            || str.equals("EPSG:25832")))
            // TODO! Dynamisch machen!!
        {
            throw new T3dException("The specified SRS " + str + " is not supported by this service.");
        }
        return str;
    }

    /**
     * Aufbereitung/Prüfung der BBOX-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @param pSRS für Geometrie zu setzendes SRS
     * @return aufbereiteter Wert
     */
    public GmEnvelope prepareBBOX(GmEnvelope pVal, String pSRS) {
        pVal.setSRS(pSRS);
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der POI-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @param pSRS für Geometrie zu setzendes SRS
     * * @return aufbereiteter Wert
     */
    public VgPoint preparePOI(VgPoint pVal, String pSRS) {
        if (pVal == null)
            return null;
        pVal.setSRS(pSRS);
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der WMSLAYERS-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public String prepareWMSLAYERS(String pVal) {
        pVal = pVal.replaceAll(" ", "%20");
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der WMSRES-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public double prepareWMSRES(double pVal) {
        if (pVal <= 0.)
            throw new T3dException("Invalid WMSRES value. WMSRES must be > 0.0!");
        if (pVal >= 10.)
            throw new T3dException("Invalid WMSRES value. WMSRES must be < 10.0!");
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der TRANSPARENT-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public boolean prepareTRANSPARENT(boolean  pVal) {
        if (pVal)
            throw new T3dException("This WTS can not generate transparent image backgrounds."
                + " Please set TRANSPARENT=false in your request...");
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der BGCOLOR-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public T3dColor prepareBGCOLOR(String pVal) {
        T3dColor res = new T3dColor();
        res.setHexEncodedValue(pVal);
        return res;
    }

    /**
     * Aufbereitung/Prüfung der WIDTH-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public int prepareWIDTH(int pVal) {
        final int limit = 1280;
        if (pVal <= 0)
            throw new T3dException("Invalid WIDTH value. Please specify a proper WIDTH value...");
        if (pVal > limit)
            throw new T3dException("Invalid WIDTH value."
                + " Please decrease the image size (WIDTH may not exceed " + limit + " pixels).");
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der HEIGHT-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public int prepareHEIGHT(int pVal) {
        final int limit = 1024;
        if (pVal <= 0)
            throw new T3dException("Invalid HEIGHT value. Please specify a proper HEIGHT value...");
        if (pVal > limit)
            throw new T3dException("Invalid HEIGHT value."
                + " Please decrease the image size (HEIGHT may not exceed " + limit + " pixels).");
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der YAW-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public double prepareYAW(double pVal) {
        if (pVal < 0. || pVal > 360.)
            throw new T3dException("Invalid YAW value. The YAW value must lie within the range 0...360!");
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der PITCH-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public double preparePITCH(double pVal) {
        if (pVal < -90. || pVal > 90.)
            throw new T3dException("Invalid PITCH value. PITCH value must lie within the range -90...+90!");
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der DISTANCE-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public double prepareDISTANCE(double pVal) {
        if (pVal < 0.)
            throw new T3dException("Invalid DISTANCE value. DISTANCE must be > 0!");
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der AOV-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public double prepareAOV(double pVal) {
        if (pVal < 0.)
            throw new T3dException("Invalid AOV value. AOV value must lie within the range 0...90!");
        if (pVal >= 90.)
            throw new T3dException("Invalid AOV value. AOV must < 90!");
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der EXCEPTIONS-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public String prepareEXCEPTIONS(String pVal)
    {
        if (pVal.equalsIgnoreCase("application/vnd.ogc.se_xml"))
            return "application/vnd.ogc.se_xml";
        if (pVal.equalsIgnoreCase("application/vnd.ogc.se_inimage"))
            return "application/vnd.ogc.se_inimage";
    
        throw new T3dException("Invalid EXCEPTIONS value. \"" + pVal + "\" is not supported.") ;
    }

    /**
     * Aufbereitung/Prüfung der QUALITY-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public int prepareQUALITY(int pVal) {
        if (pVal <= 0)
            throw new T3dException("Invalid QUALITY value. The QUALITY value should be > 0.");
        if (pVal > 100)
            throw new T3dException("Invalid QUALITY value. The QUALITY may not exceed 100 percent.");
        return pVal;
    }

    /**
     * Aufbereitung/Prüfung der LIGHTINT-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public double prepareLIGHTINT(double pVal) {
        if (pVal <= 0.)
            throw new T3dException("Invalid LIGHTINT value. The LIGHTINT value must be > 0.");
        return pVal;
    }
}
