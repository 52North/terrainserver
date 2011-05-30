package org.n52.v3d.terrainserver.profileservice;

import org.n52.v3d.triturus.core.T3dException;

/**
 * Hilfsklasse zur Aufbereitung der Anfrage-Parameter und zur Überpfüfung der Wertebereiche.<p>
 * Falls ein angegebener Wert außerhalb des zulässigen Wertebereichs liegt, wirft die zugehörige Methode eine
 * <tt>T3dException</tt>.<p>
 * @author Benno Schmidt<br>
 * (c) 2004, con terra GmbH & Institute for Geoinformatics<br>
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
        if (!(
            str.equals("EPSG:31466") || str.equals("EPSG:31467") || str.equals("EPSG:31468")
            || str.equals("EPSG:25832") || str.equals("EPSG:4326"))) // TODO! Dynamisch machen!! 
        {
            throw new T3dException("The specified SRS " + str + " is not supported by this service.");
        }
        return str;
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
            if (! (str.equals("image/svg") || str.equals("image/svg+xml") || str.equals("image/png")))
                throw new T3dException("The specified FORMAT \"" + str + "\" is not supported by GetGraph-requests.");
        }
        if (pRequest.equalsIgnoreCase("GetElevation")) {
            if (! (str.equals("text/plain") || str.equals("text/html") || str.equals("text/xml") || str.equals("text/comma-separated-values")))
                throw new T3dException("The specified FORMAT \"" + str + "\" is not supported by GetElevation-requests.");
        }
        return str;
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
}
