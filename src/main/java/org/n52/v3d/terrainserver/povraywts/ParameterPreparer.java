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

import org.n52.v3d.triturus.gisimplm.GmEnvelope;
import org.n52.v3d.triturus.vgis.VgPoint;
import org.n52.v3d.triturus.vgis.VgGeomObject;
import org.n52.v3d.triturus.t3dutil.T3dColor;
import org.n52.v3d.triturus.core.T3dException;

/**
 * Helper class to process request parameters and to check the request values for correctness.<br /><br />
 * <i>German:</i> Hilfsklasse zur Aufbereitung der Anfrage-Parameter und zur &Uuml;berpf&uuml;fung der Wertebereiche.
 * <br />
 * Falls ein angegebener Wert au&szlig;erhalb des zul&auml;ssigen Wertebereichs liegt, wirft die zugeh&ouml;rige Methode
 * eine <tt>T3dException</tt>.
 * todo engl. JavaDoc in nachstehenden Methoden
 * @author Benno Schmidt
 */
public class ParameterPreparer
{
    /**
     * Aufbereitung/Pr�fung der SRS-Angabe.<p>
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
     * Aufbereitung/Pr�fung der BBOX-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @param pSRS f�r Geometrie zu setzendes SRS
     * @return aufbereiteter Wert
     */
    public GmEnvelope prepareBBOX(GmEnvelope pVal, String pSRS) {
        pVal.setSRS(pSRS);
        return pVal;
    }

    /**
     * Aufbereitung/Pr�fung der POI-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @param pSRS f�r Geometrie zu setzendes SRS
     * * @return aufbereiteter Wert
     */
    public VgPoint preparePOI(VgPoint pVal, String pSRS) {
        if (pVal == null)
            return null;
        pVal.setSRS(pSRS);
        return pVal;
    }

    /**
     * Aufbereitung/Pr�fung der WMSLAYERS-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public String prepareWMSLAYERS(String pVal) {
        pVal = pVal.replaceAll(" ", "%20");
        return pVal;
    }

    /**
     * Aufbereitung/Pr�fung der WMSRES-Angabe.<p>
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
     * Aufbereitung/Pr�fung der TRANSPARENT-Angabe.<p>
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
     * Aufbereitung/Pr�fung der BGCOLOR-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public T3dColor prepareBGCOLOR(String pVal) {
        T3dColor res = new T3dColor();
        res.setHexEncodedValue(pVal);
        return res;
    }

    /**
     * Aufbereitung/Pr�fung der WIDTH-Angabe.<p>
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
     * Aufbereitung/Pr�fung der HEIGHT-Angabe.<p>
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
     * Aufbereitung/Pr�fung der YAW-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public double prepareYAW(double pVal) {
        if (pVal < 0. || pVal > 360.)
            throw new T3dException("Invalid YAW value. The YAW value must lie within the range 0...360!");
        return pVal;
    }

    /**
     * Aufbereitung/Pr�fung der PITCH-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public double preparePITCH(double pVal) {
        if (pVal < -90. || pVal > 90.)
            throw new T3dException("Invalid PITCH value. PITCH value must lie within the range -90...+90!");
        return pVal;
    }

    /**
     * Aufbereitung/Pr�fung der DISTANCE-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public double prepareDISTANCE(double pVal) {
        if (pVal < 0.)
            throw new T3dException("Invalid DISTANCE value. DISTANCE must be > 0!");
        return pVal;
    }

    /**
     * Aufbereitung/Pr�fung der AOV-Angabe.<p>
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
     * Aufbereitung/Pr�fung der EXCEPTIONS-Angabe.<p>
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
     * Aufbereitung/Pr�fung der QUALITY-Angabe.<p>
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
     * Aufbereitung/Pr�fung der LIGHTINT-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @return aufbereiteter Wert
     */
    public double prepareLIGHTINT(double pVal) {
        if (pVal <= 0.)
            throw new T3dException("Invalid LIGHTINT value. The LIGHTINT value must be > 0.");
        return pVal;
    }
}
