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
 * @deprecated
 * Service implementation to wquery German TK25-Blatt numbers.<br /><br />
 * <i>German:</i> Implementierung eines Web-Dienstes zur Ermittlung des TK-Kartenblatts zu einer Koordinate.<br />
 * Beispielaufruf:
 * <tt>http://<hostname>/TKBlattnummerServlet?REQUEST=GetBlattnummer&LOCATION=3398000,5726000&SRS=EPSG:31493</tt>
 * @author Benno Schmidt
 */
public class TKBlattnummerServlet extends HttpServlet
{
    private String mCapabilitiesFile; // Einstellung aus Deployment-Deskriptor

    /**
     * liest die Ablaufparameter aus dem Deployment-Deskriptor und �bertr�gt die Werte in entsprechende
     * Member-Variablen.<p>
     */
    public void fetchInitParameters()
    {
        mCapabilitiesFile = this.getInitParameter("CapabilitiesFile");
    }

    private HttpRequestParams fetchRequestParameters(HttpServletRequest pReq)
    {
        HttpRequestParams lReqParams = new HttpRequestParams();

        // Bekanntgabe der Anfrage-Parameter, damit diese als Defaults verf�gbar und/oder damit diese
        // getypt sind und somit automatisch geparst werden:
        lReqParams.addParameter("REQUEST", "String", "GetCapabilities"); // somit als Default verf�gbar
        lReqParams.addParameter("SRS", "String", "EPSG:31493"); // somit als Default verf�gbar
        lReqParams.addParameter("LOCATION", "VgPoint", "0,0"); // somit getypt und automatisch geparst

        lReqParams.fetchRequestParameters(pReq);

        // R�ckgabe der Session-spezifischen Request-Parameter:
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

        	    pResponse.setContentType("text/xml"); // MIME-Typ f�r Antwort setzen
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
