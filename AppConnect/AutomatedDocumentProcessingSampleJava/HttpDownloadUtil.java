/*
 * Licensed Materials - Property of IBM (c) Copyright IBM Corp. 2021-2022 All Rights Reserved.
 * 
 * US Government Users Restricted Rights - Use, duplication or disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * 
 * DISCLAIMER OF WARRANTIES :
 * 
 * Permission is granted to copy and modify this Sample code, and to distribute modified versions provided that both the
 * copyright notice, and this permission notice and warranty disclaimer appear in all copies and modified versions.
 * 
 * THIS SAMPLE CODE IS LICENSED TO YOU AS-IS. IBM AND ITS SUPPLIERS AND LICENSORS DISCLAIM ALL WARRANTIES, EITHER
 * EXPRESS OR IMPLIED, IN SUCH SAMPLE CODE, INCLUDING THE WARRANTY OF NON-INFRINGEMENT AND THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT WILL IBM OR ITS LICENSORS OR SUPPLIERS BE LIABLE FOR
 * ANY DAMAGES ARISING OUT OF THE USE OF OR INABILITY TO USE THE SAMPLE CODE, DISTRIBUTION OF THE SAMPLE CODE, OR
 * COMBINATION OF THE SAMPLE CODE WITH ANY OTHER CODE. IN NO EVENT SHALL IBM OR ITS LICENSORS AND SUPPLIERS BE LIABLE
 * FOR ANY LOST REVENUE, LOST PROFITS OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, EVEN IF IBM OR ITS LICENSORS OR SUPPLIERS HAVE
 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;

// Basic authentication
//import java.util.Base64;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;

import javax.net.ssl.SSLContext;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Utility class to handle HttpGet of annotation content from the CPE
 */
public class HttpDownloadUtil 
{
    /**
     * Use Retrieve the Annotation content
     *
     * @param repositoryId
     *            repository Id from where to retrieve the Annotation
     * @param annotationId
     *            annotation Id of the Annotation to retrieve
     * @param elemSeqNbr
     *            element sequence number of the Annotation
     *            
     * @return The Annotation KVPTable as a JSONArray
     * @throws Exception 
     */
    public static JSONArray getAnnotationContent(String repositoryId, String annotationId, Integer elemSeqNbr) throws Exception
    {
        String method = "HttpDownloadUtil.getAnnotationContent: ";
        Logger.debug(method, "repositoryId: " + repositoryId + ", annotationId: " + annotationId + ", elemSeqNbr: " + elemSeqNbr.toString());

        HttpResponse httpResponse = null;
        CloseableHttpClient httpClient = null;
        BufferedReader breader = null;
        JSONArray jsonKVPTable = null;

        try
        {
            String csServerURL = CSServerInfo.CS_SERVER_CONTENT_DOWNLOAD_URL;
            
            // Basic authentication
            //String csServerUsername = CSServerInfo.CS_SERVER_USERNAME;
            //String csServerPassword = CSServerInfo.CS_SERVER_PASSWORD;

            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(new TrustSelfSignedStrategy()).build();

            SSLConnectionSocketFactory sslConnectionSocketFactory =
                    new SSLConnectionSocketFactory(
                    sslContext,
                    new String[] {"TLSv1.2"},
                    null,
                    SSLConnectionSocketFactory.getDefaultHostnameVerifier());

            httpClient = HttpClientBuilder.create().setSSLSocketFactory(sslConnectionSocketFactory).build();

            // Basic authentication
            //String credentials = csServerUsername + ":" + csServerPassword;
            //String encoding = Base64.getEncoder().encodeToString(credentials.getBytes("UTF-8"));

            String bearerToken = null;

            // UMS authentication
            try
            {
                // Use a CPEClientAccount to get the bearer token
                bearerToken = CPEClientAccount.getInstance().getBearerToken();

                if ( (bearerToken == null) || (bearerToken.length() == 0) )
                {
                    Logger.error(method, "Failed to retrieve valid bearerToken", null);
                    return null;
                }

                Logger.debug(method, "bearerToken: " + bearerToken); 
            }
            catch(Exception e)
            {
                Logger.error(method, "Exception getting UMS bearerToken from CAServiceAccount: ", e);
                throw e;
//                return null;
            }

            // Build the download URL with urlencoded ids
            StringBuilder url = new StringBuilder(csServerURL);
            url.append("/content");
            url.append("?repositoryIdentifier="+URLEncoder.encode(repositoryId, "utf-8"));
            url.append("&annotationId="+URLEncoder.encode(annotationId, "utf-8"));
            url.append("&elementSequenceNumber="+elemSeqNbr.toString());

            HttpGet httpGet = new HttpGet(url.toString());

            // Basic authentication
            //httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);

            // Set UMS authentication bearerToken
            httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);

            // Pull down the annotation JSON content
            httpResponse = httpClient.execute(httpGet);

            // Get the request status
            StatusLine statusLine = httpResponse.getStatusLine();
            
            Logger.info(method, "statusLine = " + statusLine.toString());

            // Status ok
            if ( statusLine.getStatusCode() == 200 )
            {
                String jsonTgt = null;

                // Retrieve the response and navigate to the KVPTable JSONObject
                try
                {
                    // Get response string
                    breader = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));

                    StringBuilder responseString = new StringBuilder();
                    String line = "";

                    while ((line = breader.readLine()) != null)
                    {
                        responseString.append(line);
                    }

//                    Logger.info(method, "responseString = " + responseString.toString());

                    JSONObject jsonResponse = new JSONObject(responseString.toString());

                    jsonTgt = "result";
                    JSONArray jsonResults = jsonResponse.getJSONArray(jsonTgt);

                    JSONObject jsonData = null;

                    for(Object item: jsonResults) 
                    {
                        jsonTgt = "data";
                        jsonData = ((JSONObject)item).getJSONObject(jsonTgt);

                        if ( jsonData != null )
                            break;
                    }

                    if ( jsonData == null ) {
                        return jsonKVPTable;
                    }

                    jsonTgt = "pageList";
                    JSONArray jsonPageList = jsonData.getJSONArray(jsonTgt);

                    for(Object item: jsonPageList) 
                    {
                        jsonData = (JSONObject)item;

                        jsonTgt = "KVPTable";
                        jsonKVPTable = (JSONArray)jsonData.get(jsonTgt);

                        if ( jsonKVPTable != null )
                            break;
                    }
                }
                catch(Exception e)
                {
                    Logger.error(method, "failed to retreive JSON: " + jsonTgt, e);
                    throw e;
                }
            }
        } 
        catch (Exception ex)
        {
            Logger.error(method, "httpGet failed: ", ex);
            throw ex;
        }
        finally
        {
            // Close the Buffered Reader
            try
            {
                if (breader != null)
                {
                    breader.close();
                }
            }
            catch (IOException e)
            {
                Logger.error(method, "IOException", e);
            }

            // Close the HTTP connection
            try
            {
                if (httpClient != null)
                {
                    httpClient.close();
                }
            }
            catch (IOException ioe)
            {
                Logger.error(method, "IOException", ioe);
            }
        }

        return jsonKVPTable;
    }
}
