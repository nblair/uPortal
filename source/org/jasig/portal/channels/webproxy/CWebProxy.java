/**
 * Copyright � 2002 The JA-SIG Collaborative.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the JA-SIG Collaborative
 *    (http://www.jasig.org/)."
 *
 * THIS SOFTWARE IS PROVIDED BY THE JA-SIG COLLABORATIVE "AS IS" AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE JA-SIG COLLABORATIVE OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.jasig.portal.channels.webproxy;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Enumeration;
import java.util.Collections;
import java.util.HashSet;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.text.ParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.tidy.Tidy;
import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;

import org.jasig.portal.ChannelCacheKey;
import org.jasig.portal.PortalException;
import org.jasig.portal.ResourceMissingException;
import org.jasig.portal.GeneralRenderingException;
import org.jasig.portal.MediaManager;
import org.jasig.portal.ChannelRuntimeProperties;
import org.jasig.portal.PortalEvent;
import org.jasig.portal.PropertiesManager;
import org.jasig.portal.ChannelRuntimeData;
import org.jasig.portal.ChannelStaticData;
import org.jasig.portal.IMultithreadedMimeResponse;
import org.jasig.portal.IMultithreadedCacheable;
import org.jasig.portal.IMultithreadedChannel;
import org.jasig.portal.utils.XSLT;
import org.jasig.portal.utils.DTDResolver;
import org.jasig.portal.utils.ResourceLoader;
import org.jasig.portal.utils.AbsoluteURLFilter;
import org.jasig.portal.utils.CookieCutter;
import org.jasig.portal.services.LogService;
import org.jasig.portal.security.IPerson;
import org.jasig.portal.security.LocalConnectionContext;

/**
 * <p>A channel which transforms and interacts with dynamic XML or HTML.
 *    See docs/website/developers/channel_docs/reference/CwebProxy.html
 *    for full documentation.
 * </p>
 *
 * <p>Static Channel Parameters:
 *    Except where indicated, static parameters can be updated by equivalent
 *    Runtime parameters.  Caching parameters can also be changed temporarily.
 *    Cache scope and mode can only be made more restrictive, not less.
 *    Cache defaults and IPerson restrictions are loaded first from properties,
 *    and overridden by static data if there.
 * </p>    
 * <ol>
 *  <li>"cw_xml" - a URI for the source XML document
 *  <li>"cw_ssl" - a URI for the corresponding .ssl (stylesheet list) file
 *  <li>"cw_xslTitle" - a title representing the stylesheet (optional)
 *                  <i>If no title parameter is specified, a default
 *                  stylesheet will be chosen according to the media</i>
 *  <li>"cw_xsl" - a URI for the stylesheet to use
 *                  <i>If <code>cw_xsl</code> is supplied, <code>cw_ssl</code>
 *                  and <code>cw_xslTitle</code> will be ignored.
 *  <li>"cw_passThrough" - indicates how RunTimeData is to be passed through.
 *                  <i>If <code>cw_passThrough</code> is supplied, and not set
 *		    to "all" or "application", additional RunTimeData
 *		    parameters not starting with "cw_" will be passed as
 *		    request parameters to the XML URI.  If
 *		    <code>cw_passThrough</code> is set to "marked", this will
 *		    happen only if there is also a RunTimeData parameter of
 *		    <code>cw_inChannelLink</code>.  "application" is intended
 *		    to keep application-specific links in the channel, while
 *		    "all" should keep all links in the channel.  This
 *		    distinction is handled entirely in the stylesheets.
 *  <li>"cw_tidy" - output from <code>xmlUri</code> will be passed though Jtidy
 *  <li>"cw_info" - a URI to be called for the <code>info</code> event.
 *  <li>"cw_help" - a URI to be called for the <code>help</code> event.
 *  <li>"cw_edit" - a URI to be called for the <code>edit</code> event.
 *  <li>"cw_cacheDefaultTimeout" - Default timeout in seconds.
 *  <li>"cw_cacheDefaultScope" - Default cache scope.  <i>May be
 *		    <code>system</code> (one copy for all users), or
 *		    <code>user</code> (one copy per user), or
 *		    <code>instance</code> (cache for this channel instance
 *		    only).</i>
 *  <li>"cw_cacheDefaultMode" - Default caching mode.
 *		    <i>May be <code>none</code> (normally don't cache),
 *		    <code>http</code> (follow http caching directives), or
 *		    <code>all</code> (cache everything).  Http is not
 *		    currently implemented.</i>
 *  <li>"cw_cacheTimeout" - override default for this request only.
 *		    <i>Primarily intended as a runtime parameter, but can
 *	            user statically to override the first instance.</i>
 *  <li>"cw_cacheScope" - override default for this request only.
 *		    <i>Primarily intended as a runtime parameter, but can
 *	            user statically to override the first instance.</i>
 *  <li>"cw_cacheMode" - override default for this request only.
 *		    <i>Primarily intended as a runtime parameter, but can
 *	            user statically to override the first instance.</i>
 *  <li>"cw_person" - IPerson attributes to pass.
 *		    <i>A comma-separated list of IPerson attributes to
 *		    pass to the back end application.  The static data
 *		    value will be passed on </i>all<i> requests not
 *		    overridden by a runtime data cw_person.</i>
 *  <li>"cw_person_allow" - Restrict IPerson attribute passing to this list.
 *		    <i>A comma-separated list of IPerson attributes that
 *		    may be passed via cw_person.  An empty or non-existent
 *		    value means use the default value from the corresponding
 *		    property.  The special value "*" means all attributes
 *		    are allowed.  The value "!*" means none are allowed.
 *		    Static data only.</i>
 *  <li>"upc_localConnContext" - LocalConnectionContext implementation class.
 *                  <i>The name of a class to use when data sent to the
 *                  backend application needs to be modified or added
 *                  to suit local needs.</i>
 * </ol>
 * <p>Runtime Channel Parameters:</p>
 *    The following parameters are runtime-only.
 * </p>
 * <ol>
 *  <li>"cw_reset" - an instruction to return to reset internal variables.
 *		   <i>The value <code>return</code> resets <code>cw_xml</code>
 *		   to its last value before changed by button events.  The
 *		   value "reset" returns all variables to the static data
 *		   values.</i>
 *  <li>"cw_download" - use download worker for this link or form 
 *                 <i>any link or form that contains this parameter will be 
 *                 handled by the download worker, if the pass-through mode 
 *                 is set to rewrite the link or form.  This allows downloads
 *                 from the proxied site to be delivered via the portal, 
 *                 primarily useful if the download requires verification 
 *                 of a session referenced by a proxied cookie</i>
 *  
 * </ol>
 * <p>This channel can be used for all XML formats with appropriate stylesheets.
 *    All static data parameters as well as additional runtime data parameters
 *    passed to this channel via HttpRequest will in turn be passed on to the
 *    XSLT stylesheet as stylesheet parameters.  They can be read in the
 *    stylesheet as follows:
 *    <code>&lt;xsl:param
 *    name="yourParamName"&gt;aDefaultValue&lt;/xsl:param&gt;</code>
 * </p>
 * @author Andrew Draskoy, andrew@mun.ca
 * @author Sarah Arnott, sarnott@mun.ca
 * @version $Revision$
 */
public class CWebProxy implements IMultithreadedChannel, IMultithreadedCacheable, IMultithreadedMimeResponse

{
  Map stateTable;
  // to prepend to the system-wide cache key
  static final String systemCacheId="org.jasig.portal.channels.webproxy.CWebProxy";

  // All state variables now stored here
  private class ChannelState
  {
    private int id;
    private IPerson iperson;
    private String person;
    private String person_allow;
    private HashSet person_allow_set;
    private String fullxmlUri;
    private String buttonxmlUri;
    private String xmlUri;
    private String passThrough;
    private String tidy;
    private String sslUri;
    private String xslTitle;
    private String xslUri;
    private String infoUri;
    private String helpUri;
    private String editUri;
    private String cacheDefaultScope;
    private String cacheScope;
    private String cacheDefaultMode;
    private String cacheMode;
    private String reqParameters;
    private long cacheDefaultTimeout;
    private long cacheTimeout;
    private ChannelRuntimeData runtimeData;
    private CookieCutter cookieCutter;
    private URLConnection connHolder;
    private LocalConnectionContext localConnContext;

    public ChannelState ()
    {
      fullxmlUri = buttonxmlUri = xmlUri = passThrough = sslUri = null;
      xslTitle = xslUri = infoUri = helpUri = editUri = tidy = null;
      id = 0;
      cacheMode = cacheScope = null;
      iperson = null;
      cacheTimeout = cacheDefaultTimeout = PropertiesManager.getPropertyAsLong("org.jasig.portal.channels.webproxy.CWebProxy.cache_default_timeout");
      cacheDefaultMode = PropertiesManager.getProperty("org.jasig.portal.channels.webproxy.CWebProxy.cache_default_mode");
      cacheDefaultScope = PropertiesManager.getProperty("org.jasig.portal.channels.webproxy.CWebProxy.cache_default_scope");
      person_allow = PropertiesManager.getProperty("org.jasig.portal.channels.webproxy.CWebProxy.person_allow");
      runtimeData = null;
      cookieCutter = new CookieCutter();
      localConnContext = null;
    }
  }

  public CWebProxy ()
  {
    stateTable = Collections.synchronizedMap(new HashMap());
  }

  /**
   * Passes ChannelStaticData to the channel.
   * This is done during channel instantiation time.
   * see org.jasig.portal.ChannelStaticData
   * @param sd channel static data
   * @see ChannelStaticData
   */
  public void setStaticData (ChannelStaticData sd, String uid)
  {
    ChannelState state = new ChannelState();

    state.id = sd.getPerson().getID();
    state.iperson = sd.getPerson();
    state.person = sd.getParameter("cw_person");
    String person_allow = sd.getParameter ("cw_person_allow");
    if ( person_allow != null && (!person_allow.trim().equals("")))
      state.person_allow = person_allow;
    // state.person_allow could have been set by a property or static data
    if ( state.person_allow != null && (!state.person_allow.trim().equals("!*")) )
    {
      state.person_allow_set = new HashSet();
      StringTokenizer st = new StringTokenizer(state.person_allow,",");
      if (st != null)
      {
        while ( st.hasMoreElements () ) {
          String pName = st.nextToken();
          if (pName!=null) {
	    pName = pName.trim();
	    if (!pName.equals(""))
	      state.person_allow_set.add(pName);
	  }
        }
      }
    }

    state.xmlUri = sd.getParameter ("cw_xml");
    state.sslUri = sd.getParameter ("cw_ssl");
    state.fullxmlUri = sd.getParameter ("cw_xml");
    state.passThrough = sd.getParameter ("cw_passThrough");
    state.tidy = sd.getParameter ("cw_tidy");
    state.infoUri = sd.getParameter ("cw_info");
    state.helpUri = sd.getParameter ("cw_help");
    state.editUri = sd.getParameter ("cw_edit");

    String cacheScope = sd.getParameter ("cw_cacheDefaultScope");
    if (cacheScope != null)
      state.cacheDefaultScope = cacheScope;
    cacheScope = sd.getParameter ("cw_cacheScope");
    if (cacheScope != null)
      state.cacheScope = cacheScope;
    String cacheMode = sd.getParameter ("cw_cacheDefaultMode");
    if (cacheMode != null)
      state.cacheDefaultMode = cacheMode;
    cacheMode = sd.getParameter ("cw_cacheMode");
    if (cacheMode != null)
      state.cacheMode = cacheMode;
    String cacheTimeout = sd.getParameter("cw_cacheDefaultTimeout");
    if (cacheTimeout != null)
      state.cacheDefaultTimeout = Long.parseLong(cacheTimeout);
    cacheTimeout = sd.getParameter("cw_cacheTimeout");
    if (cacheTimeout != null)
      state.cacheTimeout = Long.parseLong(cacheTimeout);

    String connContext = sd.getParameter ("upc_localConnContext");
    if (connContext != null)
    {
      try
      {
        state.localConnContext = (LocalConnectionContext) Class.forName(connContext).newInstance();
        state.localConnContext.init(sd);
      }
      catch (Exception e)
      {
        LogService.instance().log(LogService.ERROR, "CWebProxy: Cannot initialize LocalConnectionContext: " + e);
      }
    }

    stateTable.put(uid,state);
  }

  /**
   * Passes ChannelRuntimeData to the channel.
   * This function is called prior to the renderXML() call.
   * @param rd channel runtime data
   * @see ChannelRuntimeData
   */
  public void setRuntimeData (ChannelRuntimeData rd, String uid)
  {
     ChannelState state = (ChannelState)stateTable.get(uid);
     if (state == null)
       LogService.instance().log(LogService.ERROR,"CWebProxy:setRuntimeData() : attempting to access a non-established channel! setStaticData() hasn't been called on uid=\""+uid+"\"");
     else
     {
       state.runtimeData = rd;

       String xmlUri = state.runtimeData.getParameter("cw_xml");
       if (xmlUri != null) {
         state.xmlUri = xmlUri;
         // don't need an explicit reset if a new URI is provided.
         state.buttonxmlUri = null;
       }

       String sslUri = state.runtimeData.getParameter("cw_ssl");
       if (sslUri != null)
          state.sslUri = sslUri;

       String xslTitle = state.runtimeData.getParameter("cw_xslTitle");
       if (xslTitle != null)
          state.xslTitle = xslTitle;

       String xslUri = state.runtimeData.getParameter("cw_xsl");
       if (xslUri != null)
          state.xslUri = xslUri;

       String passThrough = state.runtimeData.getParameter("cw_passThrough");
       if (passThrough != null)
          state.passThrough = passThrough;

       String person = state.runtimeData.getParameter("cw_person");
       if (person == null)
          person = state.person;
   
       String tidy = state.runtimeData.getParameter("cw_tidy");
       if (tidy != null)
          state.tidy = tidy;

       String infoUri = state.runtimeData.getParameter("cw_info");
       if (infoUri != null)
          state.infoUri = infoUri;

       String editUri = state.runtimeData.getParameter("cw_edit");
       if (editUri != null)
          state.editUri = editUri;

       String helpUri = state.runtimeData.getParameter("cw_help");
       if (helpUri != null)
          state.helpUri = helpUri;

       // need a way to see if cacheScope, cacheMode, cacheTimeout were
       // set in static data if this is the first time.

       String cacheTimeout = state.runtimeData.getParameter("cw_cacheDefaultTimeout");
       if (cacheTimeout != null)
          state.cacheDefaultTimeout = Long.parseLong(cacheTimeout);

       cacheTimeout = state.runtimeData.getParameter("cw_cacheTimeout");
       if (cacheTimeout != null)
          state.cacheTimeout = Long.parseLong(cacheTimeout);
       else
          state.cacheTimeout = state.cacheDefaultTimeout;

       String cacheDefaultScope = state.runtimeData.getParameter("cw_cacheDefaultScope");
       if (cacheDefaultScope != null) {
          // PSEUDO see if it's a reduction fine, otherwise log error 
          state.cacheDefaultScope = cacheDefaultScope;
       }

       String cacheScope = state.runtimeData.getParameter("cw_cacheScope");
       if (cacheScope != null) {
          // PSEUDO see if it's a reduction fine, otherwise a problem
	  // for now all instance -> user
          if ( state.cacheDefaultScope.equalsIgnoreCase("system") )
             state.cacheScope = cacheScope;
	  else {
             state.cacheScope = state.cacheDefaultScope;
             LogService.instance().log(LogService.INFO,
	       "CWebProxy:setRuntimeData() : ignoring illegal scope reduction from "
	       + state.cacheDefaultScope + " to " + cacheScope);
	  }
       } else
          state.cacheScope = state.cacheDefaultScope;

       //LogService.instance().log(LogService.DEBUG, "CWebProxy setRuntimeData(): state.cacheDefaultMode was " + state.cacheDefaultMode);
       String cacheDefaultMode = state.runtimeData.getParameter("cw_cacheDefaultMode");
       //LogService.instance().log(LogService.DEBUG, "CWebProxy setRuntimeData(): cw_cacheDefaultMode is " + cacheDefaultMode);
       if (cacheDefaultMode != null) {
          // maybe don't allow if scope is system?
          state.cacheDefaultMode = cacheDefaultMode;
       }
       //LogService.instance().log(LogService.DEBUG, "CWebProxy setRuntimeData(): state.cacheDefaultMode is now " + state.cacheDefaultMode);

       //LogService.instance().log(LogService.DEBUG, "CWebProxy setRuntimeData(): state.cacheMode was " + state.cacheMode);
       String cacheMode = state.runtimeData.getParameter("cw_cacheMode");
       //LogService.instance().log(LogService.DEBUG, "CWebProxy setRuntimeData(): cw_cacheMode is " + cacheMode);
       if (cacheMode != null) {
          // maybe don't allow if scope is system?
          state.cacheMode = cacheMode;
       } else
          state.cacheMode = state.cacheDefaultMode;
       //LogService.instance().log(LogService.DEBUG, "CWebProxy setRuntimeData(): state.cacheMode is now " + state.cacheMode);

       // reset is a one-time thing.
       String reset = state.runtimeData.getParameter("cw_reset");
       if (reset != null) {
          if (reset.equalsIgnoreCase("return")) {
             state.buttonxmlUri = null;
          }
          // else if (reset.equalsIgnoreCase("reset")) {
          //  call setStaticData with our cached copy.
          // }
       }

       if ( state.buttonxmlUri != null )
           state.fullxmlUri = state.buttonxmlUri;
       else {
         //if (this.passThrough != null )
         //  LogService.instance().log(LogService.DEBUG, "CWebProxy: passThrough: "+this.passThrough);

         // Is this a case where we need to pass request parameters to the xmlURI?
         if ( state.passThrough != null &&
            !state.passThrough.equalsIgnoreCase("none") &&
              ( state.passThrough.equalsIgnoreCase("all") ||
                state.passThrough.equalsIgnoreCase("application") ||
                rd.getParameter("cw_inChannelLink") != null ) )
           {
             LogService.instance().log(LogService.DEBUG, "CWebProxy: xmlUri is " + state.xmlUri);

             StringBuffer newXML = new StringBuffer();
             String appendchar = "";

	     // here add in attributes according to cw_person
	     
	     if (person != null && state.person_allow_set != null) {
               StringTokenizer st = new StringTokenizer(person,",");
               if (st != null)
                 {
                   while (st.hasMoreElements ())
                     {
                       String pName = st.nextToken();
                       if ((pName!=null)&&(!pName.trim().equals("")))
		       {
			 if ( state.person_allow.trim().equals("*") ||
			   state.person_allow_set.contains(pName) )
			 {
                           newXML.append(appendchar);
                           appendchar = "&";
                           newXML.append(pName);
                           newXML.append("=");
                           // note, this only gets the first one if it's a
                           // java.util.Vector.  Should check
                           String pVal = (String)state.iperson.getAttribute(pName);
                           if (pVal != null)
                             newXML.append(URLEncoder.encode(pVal));
			 } else {
			   LogService.instance().log(LogService.INFO,
			     "CWebProxy: request to pass " + pName + " denied.");
			 }
                       }
                     }
                 }
	       }
	     // end new cw_person code

             // keyword and parameter processing
             // NOTE: if both exist, only keywords are appended
	     String keywords = rd.getKeywords();
	     if (keywords != null)
	     {
	       if (appendchar.equals("&"))
	         newXML.append("&keywords=" + keywords);
	       else
	         newXML.append(keywords);   
	     }
	     else
	     {
               // want all runtime parameters not specific to WebProxy
               Enumeration e=rd.getParameterNames ();
               if (e!=null)
               {
                 while (e.hasMoreElements ())
                   {
                     String pName = (String) e.nextElement ();
                     if ( !pName.startsWith("cw_") && !pName.startsWith("upc_")
                                                   && !pName.trim().equals("")) {
                       String[] value_array = rd.getParameterValues(pName);
                       if ( value_array == null || value_array.length == 0 ) {
                           // keyword-style parameter
                           newXML.append(appendchar);
                           appendchar = "&";
                           newXML.append(pName);
                        } else {
                          int i = 0;
                          while ( i < value_array.length ) {
LogService.instance().log(LogService.DEBUG, "CWebProxy: ANDREW adding runtime parameter: " + pName);
                            newXML.append(appendchar);
                            appendchar = "&";
                            newXML.append(pName);
                            newXML.append("=");
                            newXML.append(URLEncoder.encode(value_array[i++]));
                          }
                        }

                     }
                   }
               }
             }

             // to add: if not already set, make a copy of sd for
	     // the "reset" command
             state.reqParameters = newXML.toString();
             state.fullxmlUri = state.xmlUri;
             if (!state.runtimeData.getHttpRequestMethod().equals("POST")){
                if ((state.reqParameters!=null) && (!state.reqParameters.trim().equals(""))){
                  appendchar = (state.xmlUri.indexOf('?') == -1) ? "?" : "&";
                  // BUG 772 - this doesn't seem to catch all cases.
                  state.fullxmlUri = state.fullxmlUri+appendchar+state.reqParameters;
                }
                state.reqParameters = null;
             }
             LogService.instance().log(LogService.DEBUG, "CWebProxy: fullxmlUri now: " + state.fullxmlUri);
          }
       }
     }
  }

  /**
   * Process portal events.  Currently supported events are
   * EDIT_BUTTON_EVENT, HELP_BUTTON_EVENT, ABOUT_BUTTON_EVENT,
   * and SESSION_DONE.  The button events work by changing the xmlUri.
   * The new Uri's content should contain a link that will refer back
   * to the old one at the end of its task.
   * @param ev the event
   */
  public void receiveEvent (PortalEvent ev, String uid)
  {
    ChannelState state = (ChannelState)stateTable.get(uid);
    if (state == null)
       LogService.instance().log(LogService.ERROR,"CWebProxy:receiveEvent() : attempting to access a non-established channel! setStaticData() hasn't been called on uid=\""+uid+"\"");
    else {
      int evnum = ev.getEventNumber();

      switch (evnum)
      {
        case PortalEvent.EDIT_BUTTON_EVENT:
          if (state.editUri != null)
            state.buttonxmlUri = state.editUri;
          break;
        case PortalEvent.HELP_BUTTON_EVENT:
          if (state.helpUri != null)
            state.buttonxmlUri = state.helpUri;
          break;
        case PortalEvent.ABOUT_BUTTON_EVENT:
          if (state.infoUri != null)
            state.buttonxmlUri = state.infoUri;
          break;
        case PortalEvent.SESSION_DONE:
	  stateTable.remove(uid);
          break;
        // case PortalEvent.UNSUBSCRIBE: // remove db entry for channel
        default:
          break;
      }
    }
  }

  /**
   * Acquires ChannelRuntimeProperites from the channel.
   * This function may be called by the portal framework throughout the session.
   * @see ChannelRuntimeProperties
   */
  public ChannelRuntimeProperties getRuntimeProperties (String uid)
  {
    ChannelRuntimeProperties rp=new ChannelRuntimeProperties();

    // determine if such channel is registered
    if (stateTable.get(uid) == null)
    {
      rp.setWillRender(false);
      LogService.instance().log(LogService.ERROR,"CWebProxy:getRuntimeProperties() : attempting to access a non-established channel! setStaticData() hasn't been called on uid=\""+uid+"\"");
    }
    return rp;
  }

  /**
   * Ask channel to render its content.
   * @param out the SAX ContentHandler to output content to
   */
  public void renderXML (ContentHandler out, String uid) throws PortalException
  {
    ChannelState state=(ChannelState)stateTable.get(uid);
    if (state == null)
      LogService.instance().log(LogService.ERROR,"CWebProxy:renderXML() : attempting to access a non-established channel! setStaticData() hasn't been called on uid=\""+uid+"\"");
    else
      {
      String xml = null;
      Document xmlDoc = null;

      try
      {
        if (state.tidy != null && state.tidy.equals("on"))
          xml = getXmlString (state.fullxmlUri, state);
	else
	  xmlDoc = getXmlDocument (state.fullxmlUri, state);
      }
      catch (Exception e)
      {
        throw new GeneralRenderingException ("Problem occured while rendering channel.  Please restart channel.", e, false, true);
      }

      state.runtimeData.put("baseActionURL", state.runtimeData.getBaseActionURL());
      state.runtimeData.put("downloadActionURL", state.runtimeData.getBaseWorkerURL("download"));

      // Runtime data parameters are handed to the stylesheet.
      // Add any static data parameters so it gets a full set of variables.
      // Possibly this should be a copy.
      if (state.xmlUri != null)
        state.runtimeData.put("cw_xml", state.xmlUri);
      if (state.sslUri != null)
        state.runtimeData.put("cs_ssl", state.sslUri);
      if (state.xslTitle != null)
        state.runtimeData.put("cw_xslTitle", state.xslTitle);
      if (state.xslUri != null)
        state.runtimeData.put("cw_xsl", state.xslUri);
      if (state.passThrough != null)
        state.runtimeData.put("cw_passThrough", state.passThrough);
      if (state.tidy != null)
        state.runtimeData.put("cw_tidy", state.tidy);
      if (state.infoUri != null)
        state.runtimeData.put("cw_info", state.infoUri);
      if (state.helpUri != null)
        state.runtimeData.put("cw_help", state.helpUri);
      if (state.editUri != null)
        state.runtimeData.put("cw_edit", state.editUri);

      XSLT xslt = new XSLT(this);
      if (xmlDoc != null)
        xslt.setXML(xmlDoc);
      else
        xslt.setXML(xml);
      if (state.xslUri != null)
        xslt.setXSL(state.xslUri);
      else
        xslt.setXSL(state.sslUri, state.xslTitle, state.runtimeData.getBrowserInfo());

      // Determine mime type
      MediaManager mm = new MediaManager();
      String media = mm.getMedia(state.runtimeData.getBrowserInfo());
      String mimeType = mm.getReturnMimeType(media);
   
      CWebProxyURLFilter filter2 = CWebProxyURLFilter.newCWebProxyURLFilter(mimeType, state.runtimeData, out);
      AbsoluteURLFilter filter1 = AbsoluteURLFilter.newAbsoluteURLFilter(mimeType, state.xmlUri, filter2);

      xslt.setTarget(filter1);

      xslt.setStylesheetParameters(state.runtimeData);
      xslt.transform();
    }
  }

  /**
   * Get the contents of a URI as a Document object.  This is used if tidy
   * is not set or equals 'off'.
   * Also includes support for cookies.
   * @param uri the URI
   * @return the data pointed to by a URI as a Document object
   */
  private Document getXmlDocument(String uri, ChannelState state) throws Exception
  {
    URLConnection urlConnect = getConnection(uri, state);

    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    docBuilderFactory.setNamespaceAware(false);
    DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
    DTDResolver dtdResolver = new DTDResolver();
    docBuilder.setEntityResolver(dtdResolver);

    return  docBuilder.parse(urlConnect.getInputStream());
  }

  /**
   * Get the contents of a URI as a String but send it through tidy first.
   * Also includes support for cookies.
   * @param uri the URI
   * @return the data pointed to by a URI as a String
   */
  private String getXmlString (String uri, ChannelState state) throws Exception
  {
    URLConnection urlConnect = getConnection(uri, state);

    String xml;
    if ( (state.tidy != null) && (state.tidy.equalsIgnoreCase("on")) )
    {
      Tidy tidy = new Tidy ();
      tidy.setXHTML (true);
      tidy.setDocType ("omit");
      tidy.setQuiet(true);
      tidy.setShowWarnings(false);
      tidy.setNumEntities(true);
      tidy.setWord2000(true);
      if ( System.getProperty("os.name").indexOf("Windows") != -1 )
         tidy.setErrout( new PrintWriter ( new FileOutputStream (new File ("nul") ) ) );
      else
         tidy.setErrout( new PrintWriter ( new FileOutputStream (new File ("/dev/null") ) ) );
      ByteArrayOutputStream stream = new ByteArrayOutputStream (1024);

      tidy.parse (urlConnect.getInputStream(), new BufferedOutputStream (stream));
      if ( tidy.getParseErrors() > 0 )
        throw new GeneralRenderingException("Unable to convert input document to XHTML");
      xml = stream.toString();
    }
    else
    {
      String line = null;
      BufferedReader in = new BufferedReader(new InputStreamReader(urlConnect.getInputStream()));
      StringBuffer sbText = new StringBuffer (1024);

      while ((line = in.readLine()) != null)
        sbText.append (line).append ("\n");

      xml = sbText.toString ();
    }

    return xml;
  }
  
  private URLConnection getConnection(String uri, ChannelState state) throws Exception
  {
      URL url;
      if (state.localConnContext != null)
        url = ResourceLoader.getResourceAsURL(this.getClass(), state.localConnContext.getDescriptor(uri, state.runtimeData));
      else
        url = ResourceLoader.getResourceAsURL(this.getClass(), uri);

      // get info from url for cookies
      String domain = url.getHost().trim();
      String path = url.getPath();
      if ( path.indexOf("/") != -1 )
      {
        if (path.lastIndexOf("/") != 0)
          path = path.substring(0, path.lastIndexOf("/"));
      }
      String port = Integer.toString(url.getPort());

      //get connection
      URLConnection urlConnect = url.openConnection();
      String protocol = url.getProtocol();
  
      if (protocol.equals("http") || protocol.equals("https"))
      {
        if (domain != null && path != null)
        {
          //prepare the connection by setting properties and sending data
          HttpURLConnection httpUrlConnect = (HttpURLConnection) urlConnect;
          httpUrlConnect.setInstanceFollowRedirects(false);
          //send any cookie headers to proxied application
          if(state.cookieCutter.cookiesExist())
            state.cookieCutter.sendCookieHeader(httpUrlConnect, domain, path, port);
          //set connection properties if request method was post
          if (state.runtimeData.getHttpRequestMethod().equals("POST"))
          {
            if ((state.reqParameters!=null) && (!state.reqParameters.trim().equals("")))
            {
              httpUrlConnect.setRequestMethod("POST");
              httpUrlConnect.setAllowUserInteraction(false);
              httpUrlConnect.setDoOutput(true);  
            }
          }

          //send local data, if required 
          //can call getOutputStream in sendLocalData (ie. to send post params)
          //(getOutputStream can be called twice on an HttpURLConnection)
          if (state.localConnContext != null)
          {
            try
            {
              state.localConnContext.sendLocalData(httpUrlConnect, state.runtimeData);
            }
            catch (Exception e)
            {
              LogService.instance().log(LogService.ERROR, "CWebProxy: Unable to send data through " + state.runtimeData.getParameter("upc_localConnContext") + ": " + e.getMessage());
            }
          }

          //send the request parameters by post, if required
          //at this point, set or send methods cannot be called on the connection
          //object (they must be called before sendLocalData)
          if (state.runtimeData.getHttpRequestMethod().equals("POST")){
            if ((state.reqParameters!=null) && (!state.reqParameters.trim().equals(""))){
              PrintWriter post = new PrintWriter(httpUrlConnect.getOutputStream());
              post.print(state.reqParameters);
              post.flush();
              post.close();
              state.reqParameters=null;
            }
          }

          //receive cookie headers
          state.cookieCutter.storeCookieHeader(httpUrlConnect, domain, path, port);

          int status = httpUrlConnect.getResponseCode();
          String location = httpUrlConnect.getHeaderField("Location");
          switch (status)
          {
            case HttpURLConnection.HTTP_NOT_FOUND:
              throw new ResourceMissingException
                (httpUrlConnect.getURL().toExternalForm(),
                  "", "HTTP Status-Code 404: Not Found");
            case HttpURLConnection.HTTP_FORBIDDEN:
              throw new ResourceMissingException
                (httpUrlConnect.getURL().toExternalForm(),
                  "", "HTTP Status-Code 403: Forbidden");
            case HttpURLConnection.HTTP_INTERNAL_ERROR:
              throw new ResourceMissingException
                (httpUrlConnect.getURL().toExternalForm(),
                  "", "HTTP Status-Code 500: Internal Server Error");
            case HttpURLConnection.HTTP_NO_CONTENT:
              throw new ResourceMissingException
                (httpUrlConnect.getURL().toExternalForm(),
                  "", "HTTP Status-Code 204: No Content");
            /*
             * Note: these cases apply to http status codes 302 and 303
             * this will handle automatic redirection to a new GET URL
             */
            case HttpURLConnection.HTTP_MOVED_TEMP:
              httpUrlConnect.disconnect();
              httpUrlConnect = (HttpURLConnection) getConnection(location,state);
              break;
            case HttpURLConnection.HTTP_SEE_OTHER:
              httpUrlConnect.disconnect();
              httpUrlConnect = (HttpURLConnection) getConnection(location,state);
              break; 
            /*
             * Note: this cases apply to http status code 301
             * it will handle the automatic redirection of GET requests.
             * The spec calls for a POST redirect to be verified manually by the user
             * Rather than bypass this security restriction, we will throw an exception 
             */
            case HttpURLConnection.HTTP_MOVED_PERM:
              if (state.runtimeData.getHttpRequestMethod().equals("GET")){
                httpUrlConnect.disconnect();
                httpUrlConnect = (HttpURLConnection) getConnection(location,state);
              }
              else {
                throw new ResourceMissingException
                  (httpUrlConnect.getURL().toExternalForm(),
                    "", "HTTP Status-Code 301: POST Redirection currently not supported");
              }
              break;
            default:
              break;
          }
          
          return (URLConnection) httpUrlConnect;
        }
      }
      return urlConnect;
  }

  public ChannelCacheKey generateKey(String uid)
  {
    ChannelState state = (ChannelState)stateTable.get(uid);

    if (state == null)
    {
      LogService.instance().log(LogService.ERROR,"CWebProxy:generateKey() : attempting to access a non-established channel! setStaticData() hasn't been called on uid=\""+uid+"\"");
      return null;
    }

    if ( state.cacheMode.equalsIgnoreCase("none") )
      return null;
    // else if http see first if caching is on or off.  if it's on,
    // store the validity time in the state, cache it, and further
    // resolve later with isValid.
    // check cache-control, no-cache, must-revalidate, max-age,
    // Date & Expires, expiry in past
    // for 1.0 check pragma for no-cache
    // add a warning to docs about not a full http 1.1 impl.

    ChannelCacheKey k = new ChannelCacheKey();
    StringBuffer sbKey = new StringBuffer(1024);

    if ( state.cacheScope.equalsIgnoreCase("instance") ) {
      k.setKeyScope(ChannelCacheKey.INSTANCE_KEY_SCOPE);
    } else {
      k.setKeyScope(ChannelCacheKey.SYSTEM_KEY_SCOPE);
      sbKey.append(systemCacheId).append(": ");
      if ( state.cacheScope.equalsIgnoreCase("user") ) {
        sbKey.append("userId:").append(state.id).append(", ");
      }
    }
    // Later:
    // if scope==guest, do same as user, but use GUEST instead if isGuest()
    // Scope descending order: system, guest, user, instance.

    sbKey.append("sslUri:").append(state.sslUri).append(", ");

    // xslUri may either be specified as a parameter to this channel
    // or we will get it by looking in the stylesheet list file
    String xslUriForKey = state.xslUri;
    try {
      if (xslUriForKey == null) {
        String sslUri = ResourceLoader.getResourceAsURLString(this.getClass(), state.sslUri);
        xslUriForKey = XSLT.getStylesheetURI(sslUri, state.runtimeData.getBrowserInfo());
      }
    } catch (Exception e) {
      xslUriForKey = "Not attainable: " + e;
    }

    sbKey.append("xslUri:").append(xslUriForKey).append(", ");
    sbKey.append("fullxmlUri:").append(state.fullxmlUri).append(", ");
    sbKey.append("passThrough:").append(state.passThrough).append(", ");
    sbKey.append("tidy:").append(state.tidy);
    k.setKey(sbKey.toString());
    k.setKeyValidity(new Long(System.currentTimeMillis()));
    return k;
  }

  public boolean isCacheValid(Object validity,String uid)
  {
    if (!(validity instanceof Long))
      return false;

    ChannelState state = (ChannelState)stateTable.get(uid);

    if (state == null)
    {
      LogService.instance().log(LogService.ERROR,"CWebProxy:isCacheValid() : attempting to access a non-established channel! setStaticData() hasn't been called on uid=\""+uid+"\"");
      return false;
    }
    else
    return (System.currentTimeMillis() - ((Long)validity).longValue() < state.cacheTimeout*1000);
  }
  
  public String getContentType(String uid) {
    ChannelState state = (ChannelState)stateTable.get(uid);
    return state.connHolder.getContentType();
  }
  
  public InputStream getInputStream(String uid) throws IOException {
    ChannelState state = (ChannelState)stateTable.get(uid);
    InputStream rs = state.connHolder.getInputStream();
    state.connHolder = null;
    return rs;
  }
  
  public void downloadData(OutputStream out,String uid) throws IOException {
    throw(new IOException("CWebProxy: donloadData method not supported - use getInputStream only"));
  }
  
  public String getName(String uid) {
    ChannelState state = (ChannelState)stateTable.get(uid);
    return "proxyDL";
  }
  
  public Map getHeaders(String uid) {
    ChannelState state = (ChannelState)stateTable.get(uid);
    try {
      state.connHolder= getConnection(state.fullxmlUri, state);
    }
    catch (Exception e){
      LogService.instance().log(LogService.ERROR,e);
    }
    Map rhdrs = new HashMap();
    int i = 0;
    while (state.connHolder.getHeaderFieldKey(i) != null){
      rhdrs.put(state.connHolder.getHeaderFieldKey(i),state.connHolder.getHeaderField(i));
      i++;
    }
    return rhdrs;
  }
  
}

/*
 * Developer's notes for convenience.  Will be deleted later.
 * Cache control parameters:
 *  Static params
 *    cw_cacheDefaultTimeout	timeout in seconds.
 *    cw_cacheDefaultScope	"system" - one copy for all users
 *				"guest" - one copy for guest, others by user
 *				"user" - one copy per user
 *    				"instance" - cache for this instance only
 *    cw_cacheDefaultMode		"none" - normally don't cache
 *    				"init" - only cache initial view
 *    				"http" - follow http caching directives
 *				"all" - why not?  cache everything.
 *  Runtime only params:
 *    cw_cacheTimeout		override default for this request only
 *    cw_cacheScope		override default for this request only
 *    cw_cacheMode		override default for this request only
 *
 * Note: all static parameters can be replaced via equivalent runtime.
 *
 * The Scope can only be reduced, never increased.
 */
/*
 * NOTE could cw_person be multi-valued instead of comma-sep?
 *      cw_restrict should work the same way.
 * NOTE Does IPerson contain multiple instances of attributes?
 * cw_restrict - a list of runtime parameters that cannot be changed.
 *               possibly allow multi-values, with params. indicating
 *               the param can only be changed to that?
 *	       - can we encode the scope restrictions with this
 *	         as a default?
 */
