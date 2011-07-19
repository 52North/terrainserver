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

import javax.servlet.http.HttpSession;

/**
 * Session f�r das Web Terrain Servlet. Das Session-Objekt umfasst ein HTTP-Session-Objekt und ein Objekte zur
 * Verwaltung der ben�tigten Tempor�rdateien. Das HTTP-Session-Objekt wird nur dann genutzt, wenn der Request-Parameter
 * CACHESCENE gesetzt ist.<p>
 * @author Benno Schmidt
 */
public class WTSSession
{
    private HttpSession mHttpSession = null;
    private TempFileHandler mRequTmpMngr = null;
    private TempFileHandler mSessionTmpMngr = null;

    /**
     * setzt das HTTP-Session-Obbjekt.<p>
     * @param pSession Session-Objekt oder <i>null</i>
     */
    public void setHttpSession(HttpSession pSession) {
        mHttpSession = pSession;
    }

    /**
     * liefert das HTTP-Session-Objekt.<p>
     * @return Session-Objekt oder <i>null</i>
     */
    public HttpSession getHttpSession() {
        return mHttpSession;
    }

    /**
     * setzt das Objekt zur Verwaltung der nach jeder HTTP-Anfrage zu l�schenden Tempor�rdateien.<p>
     * @param pTmpMngr <tt>TempFileHandler</tt>-Objekt oder <i>null</i>
     */
    public void setRequTempFileHandler(TempFileHandler pTmpMngr) {
        mRequTmpMngr = pTmpMngr;
    }

    /**
     * liefert das Objekt zur Verwaltung der nach jeder HTTP-Anfrage zu l�schenden Tempor�rdateien.<p>
     * @return <tt>TempFileHandler</tt>-Objekt oder <i>null</i>
     */
    public TempFileHandler getRequTempFileHandler() {
        return mRequTmpMngr;
    }

    /**
     * setzt das Objekt zur Verwaltung der nach jeder Session zu l�schenden Tempor�rdateien.<p>
     * @param pTmpMngr <tt>TempFileHandler</tt>-Objekt oder <i>null</i>
     */
    public void setSessionTempFileHandler(TempFileHandler pTmpMngr) {
        mSessionTmpMngr = pTmpMngr;
    }

    /**
     * liefert das Objekt zur Verwaltung der nach jeder Session zu l�schenden Tempor�rdateien.<p>
     * @return <tt>TempFileHandler</tt>-Objekt oder <i>null</i>
     */
    public TempFileHandler getSessionTempFileHandler() {
        return mSessionTmpMngr;
    }
}

