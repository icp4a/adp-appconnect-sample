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

import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbUserException;
import com.ibm.broker.plugin.MbXPathVariables;
import com.ibm.broker.plugin.MbElement;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The following is an example of a Java Compute node.
 */

public class Finalization_JavaCompute extends MbJavaComputeNode {

	static final private Object lock = new Object();

	public void evaluate(MbMessageAssembly inAssembly) throws MbException
	{
		String method = "Finalization_JavaCompute.evaluate: ";

		validateEnvironment();
        
		MbOutputTerminal out = getOutputTerminal("out");
		MbMessage inMessage = inAssembly.getMessage();
		MbMessage inLocalEnv = inAssembly.getLocalEnvironment();
		MbMessage outLocalEnv = new MbMessage(inLocalEnv);
		MbMessageAssembly outAssembly = null;

		Logger.info(method, "Processing message...");
		
		try {

			MbXPathVariables vars = new MbXPathVariables();

			// Create new message
			MbMessage outMessage = new MbMessage();
			outAssembly = new MbMessageAssembly(inAssembly, outMessage);

			/*
			 * Process hmac header
			 */
            String hmacValue = processHTTPInputHeaders(vars, inMessage, outLocalEnv, outMessage, outAssembly);

			 // Process headers (alternative way)
//          String hmacValue = processHeaders(inLocalEnv.getRootElement());
			
			/*
			 * Retrieve JSON Data from POST body
			 */
			JSONData jsonData = new JSONData(inMessage);
			jsonData.processBody();

			/*
			 * Verify HMAC
			 */
			boolean verified = HMACAuthentication.verifyHMACHeader(hmacValue, jsonData);
			if (!verified) {
            	String msg = "HMAC header was not confirmed";
            	Logger.error(method, msg, null);
            	setErrorOutput(vars, outLocalEnv, out, outMessage, outAssembly, 400, msg);
    			return;
			}

			/*
			 * Verify the Webhook receiverRegistrationId is handled by this client application
			 */
            if ( (jsonData.receiverRegistrationId == null) || !jsonData.receiverRegistrationId.equals(Constants.WEBHOOK_RECEIVER_REGISTRATION_ID) )
            {
            	String msg = "Receiver registration Id is not allowed.";
            	Logger.error(method, msg, null);
            	setErrorOutput(vars, outLocalEnv, out, outMessage, outAssembly, 400, msg);
    			return;
            }
            else {
                Logger.info(method, "Receiver registration Id verified");
            }


            /*
             * GraphQL example - PING
             */
        	String graphQLSchema = String.format(GraphQLCallTemplate.PING_CONTENTSERVICE_SERVER, jsonData.objectStoreId);
            JSONObject jsonGraphQLResponse = GraphQLAPIUtil.callGraphQLAPI(graphQLSchema, false);

            Logger.info(method, "PING response JSON: " + jsonGraphQLResponse.toString());

            int status = hasJSONErrors(jsonGraphQLResponse, "Error from GraphQL ping");
            if ( status != 0 )
            {
            	String msg = "Error contacting GraphQL server";
            	Logger.error(method, msg, null);
            	setErrorOutput(vars, outLocalEnv, out, outMessage, outAssembly, 400, msg);
                return;
            }
			
        	/*
        	 * GraphQL example - Retrieve the document annotation
        	 */
            graphQLSchema = String.format(GraphQLCallTemplate.GET_DOC_ANNOTATIONS, jsonData.objectStoreId, jsonData.sourceObjectId);
            JSONObject jsonGraphQLAnno = GraphQLAPIUtil.callGraphQLAPI(graphQLSchema, false);

    		Logger.info(method, "Annotations response JSON: " + jsonGraphQLAnno.toString());
    		
            status = hasJSONErrors(jsonGraphQLAnno, "Error retrieving Webhook source document annotation");
            if ( status != 0 )
            {
            	String msg = "Error retrieving annotation JSON";
            	Logger.error(method, msg, null);
            	setErrorOutput(vars, outLocalEnv, out, outMessage, outAssembly, 400, msg);
                return;
            }

            Logger.info(method, "Event source object annotation retrieved from the CPE, parsing the KVPTable");

            JSONArray jsonKVPTable = getKVPTableFromAnnotation(jsonData.sourceObjectId, jsonData.objectStoreId, jsonGraphQLAnno);
            if ( jsonKVPTable == null )
            {
            	String msg = "Failed to parse the KVPTable from the Annotation JSON";
                Logger.warn(method, "sourceObjectId: " + jsonData.sourceObjectId + "Failed to retrieve the annotation KVPTable");
            	setErrorOutput(vars, outLocalEnv, out, outMessage, outAssembly, 400, msg);
                return;
            }

            Logger.info(method, "sourceObjectId: " + jsonData.sourceObjectId + "\n    jsonKVPTable: " + jsonKVPTable.toString(2));

            // Write the KVPTable results to an output file
            synchronized (lock)
            {
                writeKVPResults(jsonKVPTable, jsonData.sourceObjectId);
            }


			/*
			 * Set return message
			 */
			vars.assign("message", "The finalization was added to some database.");
			outMessage.getRootElement().createElementAsLastChild("JSON");
			outMessage.getRootElement().evaluateXPath("?JSON/?Data/?message[set-value($message)]", vars);

		} catch (MbException e) {
			// Re-throw to allow Broker handling of MbException
			throw e;
		} catch (RuntimeException e) {
			// Re-throw to allow Broker handling of RuntimeException
			throw e;
		} catch (Exception e) {
			// Consider replacing Exception with type(s) thrown by user code
			// Example handling ensures all exceptions are re-thrown to be handled in the flow
			throw new MbUserException(this, method, "", "", e.toString(), null);
		}
		
		// The following should only be changed
		// if not propagating message to the 'out' terminal
		out.propagate(outAssembly);
		
		Logger.info(method, "Finalization: Done");
	}

	private String processHeaders(MbElement element) throws MbException
	{
		String method = "Finalization_JavaCompute.processHeaders: ";

		MbElement hmacElement = element.getFirstElementByPath("REST/Input/Parameters/hmac");
		String hmacValue = hmacElement.getValueAsString();
		Logger.debug(method, hmacElement.getName() + " = " + hmacValue);
		
//		MbElement temp1 = element.getFirstElementByPath("REST/Input/Parameters/md5");
//		Logger.debug(method, temp1.getName() + " = " + temp1.getValueAsString());
		
		return hmacValue;
	}

	private String processHTTPInputHeaders(MbXPathVariables vars, MbMessage inMessage, MbMessage outLocalEnv, 
										  MbMessage outMessage, MbMessageAssembly outAssembly) throws MbException
	{
		String method = "Finalization_JavaCompute.processHTTPInputHeaders: ";

		MbOutputTerminal out = getOutputTerminal("out");
		
		MbElement temp = inMessage.getRootElement().getFirstElementByPath("HTTPInputHeader/Hmac");
		if (temp == null || temp.getValueAsString() == null) {
        	String msg = "No 'hmac' parameter was provided in the request.";
        	Logger.error(method, msg, null);
			setErrorOutput(vars, outLocalEnv, out, outMessage, outAssembly, 400, msg);
			return null;
		}
		
		String hmac = temp.getValueAsString();
		Logger.info(method, "Hmac header = " + hmac);
		return hmac;
	}

	private int hasJSONErrors(JSONObject jsonGraphQLResponse, String errorPrefix)
    {
        int status = 0;

        if ( (jsonGraphQLResponse == null) || (jsonGraphQLResponse.length() == 0) )
        {
            // Return with error
            status = 400;
        }
        else if ( jsonGraphQLResponse.has("errors") ) 
        {
            JSONArray jsonResponseErrors = jsonGraphQLResponse.getJSONArray("errors");

            if ( jsonResponseErrors.length() > 0 )
            {
                Object graphQLErrorObj = jsonResponseErrors.get(0);
                JSONObject graphQLError = new JSONObject(graphQLErrorObj.toString());
                String errorMessage = graphQLError.getString("message");

                // Return with the error (400 by default if there is no error code in the extensions)
                status = 400;

                // Check for statusCode in extensions
                JSONObject jsonExtentions = graphQLError.getJSONObject("extensions");
                if ( jsonExtentions != null )
                {
                    status = jsonExtentions.getInt("statusCode");
                }

                // Return with the exception
                Logger.info("hasJSONErrors", errorPrefix + ", " + errorMessage);
            }
        }

        return status;
    }
	 
	/**
     * Iterate through KVPTable entries, write the document type, key classes
     *  and key class values from the table to a text file
     * 
     * @param jsonKVPTable
     *            JSONArray containing key classes and key class values
     * @param documentId
     *            Id of the source document
     *            
     * @return void
	 * @throws Exception 
     */
    private void writeKVPResults(JSONArray jsonKVPTable, String documentId) throws Exception
    {
        String method = "WebhookReceiver.writeKVPResults: ";

        FileWriter fw = null;
        BufferedWriter bw = null;

        try
        {
            fw = new FileWriter(Constants.FINALIZE_DOC_LIST, true);
            bw = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(bw);
            
            pw.write("Document:"+ documentId + "\n");

            if ( (jsonKVPTable != null) && (jsonKVPTable.length() > 0) )
            {
                // Iterate through all KVPTable elements
                for (int i = 0, size = jsonKVPTable.length(); i < size; i++)
                {
                  StringBuffer sb = new StringBuffer("  ");

                  JSONObject arrayObj = jsonKVPTable.getJSONObject(i);
                  String[] elementNames = JSONObject.getNames(arrayObj);

                  // Iterate though all KVPTable element names
                  for (String elementName : elementNames)
                  {
                      // Append the required element name and value
                      if ( elementName.equals("KeyClass") || elementName.equals("Key") || elementName.equals("Value") || elementName.equals("KeyClassConfidence") )
                      {
                    	  if (arrayObj.isNull(elementName)) {
                              sb.append(elementName + ":null  ");
                    	  } else {
                              sb.append(elementName + ":" + arrayObj.getString(elementName) + "  ");
                    	  }
                      }
                  }
                  pw.write(sb.toString() + "\n");
                }
            }
            else
            {
                pw.write("    No KVPTable elements found for document.\n");
            }
            pw.flush();
            pw.close();
        } 
        catch (Exception e)
        {
            Logger.error(method, "Failure parsing jsonKVPTable JSON: ", e);
            throw e;
        }
        finally
        {
            try
            {
                fw.close();
            }
            catch(Exception e) {/* ignore */}
            
            try
            {
                bw.close();
            }
            catch(Exception e) {/* ignore */}
        }
    }
    
	/**
     * Retrieves the KVPTable from the Annotation JSON
     * 
     * @param sourceObjectId
     *            event source object ID
     * @param objectStoreId
     *            event object store ID
     * @param jsonGraphQLAnno
     *            Annotation JSON
     *            
     * @return KVPTable as a JSONArray
	 * @throws Exception 
     */
    private JSONArray getKVPTableFromAnnotation(String sourceObjectId, String objectStoreId, JSONObject jsonGraphQLAnno) throws Exception
    {
        String method = "Finalization_JavaCompute.getKVPTableFromAnnotation: ";
        JSONArray jsonKVPTable = null;

        try
        {
            // Get annotations from document JSON
        	JSONObject jsonResponseData = jsonGraphQLAnno.getJSONObject("data");
        	JSONObject jsonDocument = jsonResponseData.getJSONObject("document");
        	JSONObject jsonAnnotations = jsonDocument.getJSONObject("annotations");
        	JSONArray jsonAnnos = jsonAnnotations.getJSONArray("annotations");

            if ( jsonAnnos.length() > 0 )
            {
            	JSONObject annot = (JSONObject)jsonAnnos.get(0);
                String annotationId = annot.getString("id");
                JSONArray jsonContentElements = annot.getJSONArray("contentElements");

                if ( jsonContentElements.length() > 0 ) 
                {
                	JSONObject element = (JSONObject)jsonContentElements.get(0);
                    Integer elemSeqNbr = element.getInt("elementSequenceNumber");

                    Logger.info(method, "Retrieving annotation  objectStoreId: " + objectStoreId + "annotationId: " + 
                    					annotationId + " elementSequenceNumber: " + elemSeqNbr.toString());

                    jsonKVPTable = HttpDownloadUtil.getAnnotationContent(objectStoreId, annotationId, elemSeqNbr);
                }
                else {
                    Logger.warn(method, "No annotation found for sourceObjectId: " + sourceObjectId + " annotation: " + annotationId);
                }
            }
            else {
                Logger.warn(method, "No annotations found for sourceObjectId: " + sourceObjectId);
            }
        }
        catch (JSONException je)
        {
            Logger.error(method, "JSON parsing exception: ", je);
            throw je;
        }
        catch (Exception e)
        {
            Logger.error(method, "Unexpected exception retrieving document annotation: ", e);
            throw e;
        }

        return jsonKVPTable;
    }

    private void setErrorOutput(MbXPathVariables vars, MbMessage outLocalEnv, MbOutputTerminal out, 
    									MbMessage outMessage, MbMessageAssembly outAssembly, int code, String errorMsg) 
    									throws MbException
    {
		vars.assign("statuscode", code);
		vars.assign("error", errorMsg);
		outLocalEnv.getRootElement().evaluateXPath("?Destination/?HTTP/?ReplyStatusCode[set-value($statuscode)]", vars);
		outMessage.getRootElement().createElementAsLastChild("JSON");
		outMessage.getRootElement().evaluateXPath("?JSON/?Data/?error[set-value($error)]", vars);
		out.propagate(outAssembly);
    }
    
    /**
     * Logs details of the source object contained in the JSON payload
     * 
     * @param sourceObjectId
     *            event source object ID
     * @param objectStoreId
     *            event object store ID
     * @param jsonPayload
     *            JSON event payload
     *            
     * @return Nothing
     * @throws Exception 
     */
    private void logSourceObject(String sourceObjectId, String objectStoreId, JSONObject jsonPayload) throws Exception
    {
        String method = "Finalization_JavaCompute.logSourceObject: ";

        String graphQLSchema = String.format(GraphQLCallTemplate.GET_DOCUMENT, objectStoreId, sourceObjectId);
        JSONObject jsonGraphQLResponse = GraphQLAPIUtil.callGraphQLAPI(graphQLSchema, false);

        int status = hasJSONErrors(jsonGraphQLResponse, method + "Error retrieving Webhook source document");

        if ( status != 0 )
            return;

        try
        {
            // Get Properties from document JSON
            JSONObject jsonResponseData = jsonGraphQLResponse.getJSONObject("data");
            JSONObject jsonDocument = jsonResponseData.getJSONObject("document");
            JSONArray jsonProperties = jsonDocument.getJSONArray("properties");

            StringBuffer sb = new StringBuffer();

            // Iterate through the properties object for the custom properties on the document
            for (Object propertyObj : jsonProperties) 
            {
            	JSONObject prop = new JSONObject(propertyObj.toString());
                String propStr = prop.getString("id");
                
                if ( prop.has("value") )
                    propStr += ": " + prop.get("value").toString();

                if ( sb.length() == 0 )
                    sb.append(propStr);
                else
                    sb.append(", " + propStr);
            }

            Logger.info(method, "Document properties= " + sb.toString());
        }
        catch (JSONException je)
        {
            Logger.error(method, "Failed to retrieve document properties: ", je);
            throw je;
        } 
   }
    
    private void validateEnvironment()
    {
        String method = "Finalization_JavaCompute.validateEnvironment: ";

        String envVar;
        envVar = System.getenv(Constants.CPE_SERVICE_USER);
        if (envVar != null) {
            Logger.debug(method, "CPE_SERVICE_USER = " + envVar);
        } else {
            Logger.debug(method, "CPE_SERVICE_USER not found");
        }
        
        if (System.getenv(Constants.UMS_SSO_URL) == null) {
        	throw new RuntimeException("Environment variable " + Constants.UMS_SSO_URL + " is not set");
        }
        if (System.getenv(Constants.UMS_CLIENT_ID) == null) {
        	throw new RuntimeException("Environment variable " + Constants.UMS_CLIENT_ID + " is not set");
        }
        if (System.getenv(Constants.UMS_CLIENT_SECRET) == null) {
        	throw new RuntimeException("Environment variable " + Constants.UMS_CLIENT_SECRET + " is not set");
        }
        if (System.getenv(Constants.CPE_SERVICE_USER) == null) {
        	throw new RuntimeException("Environment variable " + Constants.CPE_SERVICE_USER + " is not set");
        }
        if (System.getenv(Constants.CPE_SERVICE_PWD) == null) {
        	throw new RuntimeException("Environment variable " + Constants.CPE_SERVICE_PWD + " is not set");
        }
        if (System.getenv(Constants.CPE_CLIENT_USER) == null) {
        	throw new RuntimeException("Environment variable " + Constants.CPE_CLIENT_USER + " is not set");
        }
        if (System.getenv(Constants.CPE_CLIENT_PWD) == null) {
        	throw new RuntimeException("Environment variable " + Constants.CPE_CLIENT_PWD + " is not set");
        }
    }


	/**
	 * onPreSetupValidation() is called during the construction of the node
	 * to allow the node configuration to be validated.  Updating the node
	 * configuration or connecting to external resources should be avoided.
	 *
	 * @throws MbException
	 */
	@Override
	public void onPreSetupValidation() throws MbException {
	}

	/**
	 * onSetup() is called during the start of the message flow allowing
	 * configuration to be read/cached, and endpoints to be registered.
	 *
	 * Calling getPolicy() within this method to retrieve a policy links this
	 * node to the policy. If the policy is subsequently redeployed the message
	 * flow will be torn down and reinitialized to it's state prior to the policy
	 * redeploy.
	 *
	 * @throws MbException
	 */
	@Override
	public void onSetup() throws MbException {
	}

	/**
	 * onStart() is called as the message flow is started. The thread pool for
	 * the message flow is running when this method is invoked.
	 *
	 * @throws MbException
	 */
	@Override
	public void onStart() throws MbException {
	}

	/**
	 * onStop() is called as the message flow is stopped. 
	 *
	 * The onStop method is called twice as a message flow is stopped. Initially
	 * with a 'wait' value of false and subsequently with a 'wait' value of true.
	 * Blocking operations should be avoided during the initial call. All thread
	 * pools and external connections should be stopped by the completion of the
	 * second call.
	 *
	 * @throws MbException
	 */
	@Override
	public void onStop(boolean wait) throws MbException {
	}

	/**
	 * onTearDown() is called to allow any cached data to be released and any
	 * endpoints to be deregistered.
	 *
	 * @throws MbException
	 */
	@Override
	public void onTearDown() throws MbException {
	}

}
