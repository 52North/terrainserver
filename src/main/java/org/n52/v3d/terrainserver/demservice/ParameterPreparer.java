package org.n52.v3d.terrainserver.demservice;

import org.n52.v3d.triturus.core.T3dException;
import org.n52.v3d.triturus.gisimplm.GmEnvelope;

/**
 * Hilfsklasse zur Aufbereitung der Anfrage-Parameter und zur Überpfüfung der Wertebereiche.<p>
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
     * Aufbereitung/Prüfung der BBOX-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @param pSRS für Geometrie zu setzendes SRS
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
     * Aufbereitung/Prüfung der FORMAT-Angabe.<p>
     * @param pVal getypter Request-Parameter aus <tt>HttpRequestParams</tt>
     * @param pRequest Anfragetyp für Profildienst ("GetGraph" oder "GetElevation")
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
