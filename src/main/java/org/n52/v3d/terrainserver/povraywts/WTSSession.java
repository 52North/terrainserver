package org.n52.v3d.terrainserver.povraywts;

import javax.servlet.http.HttpSession;

/**
 * Session für das Web Terrain Servlet. Das Session-Objekt umfasst ein HTTP-Session-Objekt und ein Objekte zur
 * Verwaltung der benötigten Temporärdateien. Das HTTP-Session-Objekt wird nur dann genutzt, wenn der Request-Parameter
 * CACHESCENE gesetzt ist.<p>
 * @author Benno Schmidt<br>
 * (c) 2004, con terra GmbH & Institute for Geoinformatics<br>
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
     * setzt das Objekt zur Verwaltung der nach jeder HTTP-Anfrage zu löschenden Temporärdateien.<p>
     * @param pTmpMngr <tt>TempFileHandler</tt>-Objekt oder <i>null</i>
     */
    public void setRequTempFileHandler(TempFileHandler pTmpMngr) {
        mRequTmpMngr = pTmpMngr;
    }

    /**
     * liefert das Objekt zur Verwaltung der nach jeder HTTP-Anfrage zu löschenden Temporärdateien.<p>
     * @return <tt>TempFileHandler</tt>-Objekt oder <i>null</i>
     */
    public TempFileHandler getRequTempFileHandler() {
        return mRequTmpMngr;
    }

    /**
     * setzt das Objekt zur Verwaltung der nach jeder Session zu löschenden Temporärdateien.<p>
     * @param pTmpMngr <tt>TempFileHandler</tt>-Objekt oder <i>null</i>
     */
    public void setSessionTempFileHandler(TempFileHandler pTmpMngr) {
        mSessionTmpMngr = pTmpMngr;
    }

    /**
     * liefert das Objekt zur Verwaltung der nach jeder Session zu löschenden Temporärdateien.<p>
     * @return <tt>TempFileHandler</tt>-Objekt oder <i>null</i>
     */
    public TempFileHandler getSessionTempFileHandler() {
        return mSessionTmpMngr;
    }
}

