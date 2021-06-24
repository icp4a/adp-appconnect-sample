/*
 * Licensed Materials - Property of IBM (c) Copyright IBM Corp. 2021 All Rights Reserved.
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
import java.net.URI;

// Basic authentication
//import java.util.Base64;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.json.JSONObject;

/**
 * Utility class for handling calls to the Content Services GraphQL API.
 * 
 * This class will use the TLS 1.2 protocol for calls to the GraphQL API. If a
 * different protocol is required, the protocol can be changed in the code for
 * this class.
 */
public class GraphQLAPIUtil 
{
    /**
     * Handles calls to the Content Services GraphQL API.
     * 
     * @param graphQLCommand
     *            GraphQL command, either a query or mutation, to pass to the
     *            Content Services GraphQL API
     * @return Response for the call to the Content Services GraphQL API
     * @throws Exception 
     */
    public static JSONObject callGraphQLAPI(String graphQLCommand, boolean bAdminUser) throws Exception
    {
        String method = "GraphQLAPIUtil.callGraphQLAPI: ";
        Logger.debug(method, "graphQLCommand: " + graphQLCommand);

        JSONObject jsonGraphQLResponse = null;
        HttpResponse response = null;
        CloseableHttpClient httpClient = null;
        BufferedReader breader = null;

        try
        {
            String csServerURL = CSServerInfo.CS_SERVER_GRAPHQL_URL;

            // Basic authentication
            //String csServerUsername = CSServerInfo.CS_SERVER_USERNAME;
            //String csServerPassword = CSServerInfo.CS_SERVER_PASSWORD;

             /*
             * Create HTTPClient and have it use TLSv1.2. If you do not wish to
             * force TLSv1.2 to be used, you can change the protocol or completely 
             * remove the setSSLSocketFactory() call from the HttpClientBuilder call.
             */
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
                if ( bAdminUser )
                {
                    // Use a CPEServiceAccount to get the bearer token
                    bearerToken = CPEServiceAccount.getInstance().getBearerToken();
                }
                else
                {
                    // Use a CPEClientAccount to get the bearer token
                    bearerToken = CPEClientAccount.getInstance().getBearerToken();
                }

                if ( (bearerToken == null) || (bearerToken.length() == 0) )
                {
                	String msg = "Failed to retrieve valid bearerToken";
                    Logger.error(method, msg, null);
                    throw new RuntimeException(msg);
//                    return null;
                }

                Logger.debug(method, "bearerToken: " + bearerToken); 
            }
            catch(Exception e)
            {
                Logger.error(method, "Exception getting UMS bearerToken", e);
                throw e;
//                return null;
            }

            // Build the URL to the GraphQL API using the base URL from CSServerInfo.properties
            URI uri = new URIBuilder(csServerURL).build();

            // Set headers for GraphQL API call
            HttpPost httpPost = new HttpPost(uri);

            // Basic authentication
            //httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);

            // UMS authentication
            httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);

            httpPost.addHeader("content-type", "application/json");
            httpPost.addHeader("Accept", "application/json");

            // Pass GraphQL API call via the value for a query parameter using JSON.
            JSONObject jsonGraphQLCommand = new JSONObject();

            jsonGraphQLCommand.put("query", graphQLCommand);

            StringEntity jsonQueryEntity = new StringEntity(jsonGraphQLCommand.toString());

            httpPost.setEntity(jsonQueryEntity);

            // Trace statement logs GraphQL API call arguments
            Logger.debug(method, "csServerURL: " + csServerURL + " httpPost: " + httpPost.toString());

            // Handle the response
            response = httpClient.execute(httpPost);

            StatusLine statusLine = response.getStatusLine();

            if ( statusLine.getStatusCode() == 200 )
            {
                // Get response string
                breader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

                StringBuilder responseString = new StringBuilder();

                String line = "";

                while ((line = breader.readLine()) != null)
                {
                    responseString.append(line);
                }

                String responseGraphQL = responseString.toString();
                jsonGraphQLResponse = new JSONObject(responseGraphQL);

                Logger.debug(method, "jsonGraphQLResponse: " + jsonGraphQLResponse.toString(2) + "\n\n");
            }
            else
            {
                throw new RuntimeException("GraphQL response code: " + statusLine.toString());
            }
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
                Logger.error(method, "Failure closing BufferedReader: ", e);
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
                Logger.error(method, "Failure closing CloseableHttpClient: ", ioe);
            }
        }

        return jsonGraphQLResponse;
    }
}
