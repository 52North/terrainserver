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
package org.n52.v3d.terrainserver.demservice;

import org.n52.v3d.triturus.core.T3dException;
import org.n52.v3d.triturus.gisimplm.GmEnvelope;

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
        if (pVal == null)
            throw new T3dException("Missing SRS parameter.");
        String str = pVal.toUpperCase();
        if (str.length() <= 0)
            throw new T3dException("Missing SRS parameter.");
        if (!(
            str.equals("EPSG:31466") || str.equals("EPSG:31467") || str.equals("EPSG:31468")
            || str.equals("EPSG:25832") || str.equals("EPSG:4326"))) // TODO! Dynamisch machen!! 
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
        if (pVal == null)
             throw new T3dException("Missing BBOX parameter.");
        if (pVal.areaXY() <= 0.)
            throw new T3dException("Invalid BBOX parameter: Bounding-box is 0.");
        pVal.setSRS(pSRS);
        return pVal;
    }

    /**
     * Aufbereitung/Pr�fung der FORMAT-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @param pRequest Anfragetyp f�r Profildienst ("GetGraph" oder "GetElevation")
     * @return aufbereiteter Wert
     */
    public String prepareFORMAT(String pVal, String pRequest) {
        if (pVal == null || pVal.length() <= 0)
            throw new T3dException("Please specify a proper FORMAT value...");
        String str = pVal.toLowerCase();
        if (pRequest.equalsIgnoreCase("GetGraph")) {
            if (! (str.equals("image/svg") || str.equals("image/svg+xml")))
                throw new T3dException("The specified FORMAT \"" + str + "\" is not supported by GetGraph-requests.");
        }
        if (pRequest.equalsIgnoreCase("GetElevation")) {
            if (! (str.equals("text/plain") || str.equals("text/html") || str.equals("text/xml") || str.equals("text/comma-separated-values")))
                throw new T3dException("The specified FORMAT \"" + str + "\" is not supported by GetElevation-requests.");
        }
        return str;
    }
}
