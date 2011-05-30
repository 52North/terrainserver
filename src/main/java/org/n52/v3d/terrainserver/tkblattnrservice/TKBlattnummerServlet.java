package org.n52.v3d.terrainserver.tkblattnrservice;

import java.io.*;
import java.lang.String;
import javax.servlet.http.*;
import javax.servlet.*;

import org.n52.v3d.triturus.core.T3dException;
import org.n52.v3d.triturus.vgis.VgPoint;
import org.n52.v3d.triturus.gisimplm.GmPoint;
import org.n52.v3d.triturus.survey.TKBlattLocator;
import org.n52.v3d.triturus.survey.GaussKrugerTransformator;
import org.n52.v3d.triturus.web.HttpRequestParams;
import org.n52.v3d.triturus.web.HttpStandardResponse;

/** 
 * Implementierung eines Web-Dienstes zur Ermittlung des TK-Kartenblatts zu einer Koordinate.<p>
 * Beispielaufruf:
 * <tt>http://<hostname>/TKBlattnummerServlet?REQUEST=GetBlattnummer&LOCATION=3398000,5726000&SRS=EPSG:31493</tt>
 * <p>
 * @author Benno Schmidt<br>
 * (c) 2004, con terra GmbH & Institute for Geoinformatics<br>
 */
public class TKBlattnummerServlet extends HttpServlet
{
    private String mCapabilitiesFile; // Einstellung aus Deployment-Deskriptor

    /**
     * liest die Ablaufparameter aus dem Deployment-Deskriptor und überträgt die Werte in entsprechende
     * Member-Variablen.<p>
     */
    public void fetchInitParameters()
    {
        mCapabilitiesFile = this.getInitParameter("CapabilitiesFile");
    }

    private HttpRequestParams fetchRequestParameters(HttpServletRequest pReq)
    {
        HttpRequestParams lReqParams = new HttpRequestParams();

        // Bekanntgabe der Anfrage-Parameter, damit diese als Defaults verfügbar und/oder damit diese
        // getypt sind und somit automatisch geparst werden:
        lReqParams.addParameter("REQUEST", "String", "GetCapabilities"); // somit als Default verfügbar
        lReqParams.addParameter("SRS", "String", "EPSG:31493"); // somit als Default verfügbar
        lReqParams.addParameter("LOCATION", "VgPoint", "0,0"); // somit getypt und automatisch geparst

        lReqParams.fetchRequestParameters(pReq);

        // Rückgabe der Session-spezifischen Request-Parameter:
        return lReqParams;
    }

    /**
     * initialisiert das Servlet.<p>
     */
    public void init(ServletConfig pConf) throws ServletException {
        super.init(pConf);

        // Initialisierungsparameter aus "web.xml" lesen:
        this.fetchInitParameters();
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
    	try {
            // Request-Parameter holen:
    		HttpRequestParams lReqParams = this.fetchRequestParameters(pRequest);
            String lRequest = (String) lReqParams.getParameterValue("REQUEST");
            String lSRS = (String) lReqParams.getParameterValue("SRS");
            VgPoint lLocation = (VgPoint) lReqParams.getParameterValue("LOCATION");

            lLocation.setZ(0.);
            lLocation.setSRS(lSRS); // Wert darf auch im 2er- oder 4er-Streifen liegen

            if (lRequest.equalsIgnoreCase("GetCapabilities")) {
                HttpStandardResponse response = new HttpStandardResponse();
                response.sendXMLFile(mCapabilitiesFile, pResponse);
                return;
            }
            else {
		    	GaussKrugerTransformator trf = new GaussKrugerTransformator();
			    GmPoint pt = new GmPoint();
			    trf.gkk2LatLon(lLocation, pt);
			
			    TKBlattLocator loc = new TKBlattLocator();
			    String tknr;

        	    pResponse.setContentType("text/xml"); // MIME-Typ für Antwort setzen
        	    PrintWriter out = pResponse.getWriter(); // PrintWriter auf die Antwort aufsetzen

	            out.println("<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\" ?>");
			    out.println("<ServiceResponse>");
	    
			    tknr = loc.blattnummer("TK 25", pt);
			    out.println("  <TK25>");
			    out.println("    <Description>Nummer und Name des TK 25-Blatts</Description>");
			    out.println("    <Number>" + tknr + "</Number>");
			    out.println("    <Name>" + loc.blattname(tknr) + "</Name>");
			    out.println("  </TK25>");
		
			    tknr = loc.blattnummer("TK 50", pt);
			    out.println("  <TK50>");
			    out.println("    <Description>Nummer und Name des TK 50-Blatts</Description>");
			    out.println("    <Number>" + tknr + "</Number>");
			    out.println("    <Name>" + loc.blattname(tknr) + "</Name>");
			    out.println("  </TK50>");
		
			    tknr = loc.blattnummer("TK 100", pt);
			    out.println("  <TK100>");
			    out.println("    <Description>Nummer und Name des TK 100-Blatts</Description>");
			    out.println("    <Number>" + tknr + "</Number>");
			    out.println("    <Name>" + loc.blattname(tknr) + "</Name>");
			    out.println("  </TK100>");
			    out.println("</ServiceResponse>");

        	    out.flush();
        	    out.close();
		    }
        }
		catch (T3dException e) {
            HttpStandardResponse response = new HttpStandardResponse();
            response.sendException(e.getMessage(), pResponse);
            return;
        }
    }
}
