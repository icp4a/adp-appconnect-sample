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

import java.io.IOException;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;

import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbElement;

public class JSONData {

	public MbMessage inMessage;
	public String hmacJsonStr = "";

	public String receiverRegistrationId;
	public String eventDateTime;
	public String objectStoreId;
	public String sourceObjectId;
	public String eventType;
	public String subscriptionId;
	public String initiatingUser;
	
    public JSONData(MbMessage inMessage)
    {
    	this.inMessage = inMessage;
    }

	public void processBody() throws MbException, IOException
	{
		String method = "JSONData.processBody: ";
		
		MbElement element = inMessage.getRootElement().getFirstElementByPath("JSON/Data");
		
		/*
		 * Iterate through the Data element tree
		 */
		JSONObject jsonObject = new JSONObject();
		JSONObject jsonChild = new JSONObject();
		jsonObject.put(element.getName(), jsonChild);

		processChildren(element, jsonChild, 0);

		/*
		 * Print resulting JSON - These can be large
		 */
		Logger.debug(method, "Body Json = " + jsonObject.serialize(true).toString().substring(0, 500));

//		Logger.debug(method, "hmacJsonStr = " + hmacJsonStr);
		
		
		// Retrieve JSON payload variables
		JSONObject jsonData = (JSONObject) jsonObject.get("Data");

		receiverRegistrationId = (String) jsonData.get("receiverRegistrationId");
		eventDateTime = (String) jsonData.get("eventDateTime");
		objectStoreId = (String) jsonData.get("objectStoreId");
		sourceObjectId = (String) jsonData.get("sourceObjectId");
		eventType = (String) jsonData.get("eventType");
		subscriptionId = (String) jsonData.get("subscriptionId");
		initiatingUser = (String) jsonData.get("initiatingUser");

		Logger.info(method, "receiverRegistrationId = " + receiverRegistrationId);
		Logger.info(method, "eventDateTime = " + eventDateTime);
		Logger.info(method, "objectStoreId = " + objectStoreId);
		Logger.info(method, "sourceObjectId = " + sourceObjectId);
		Logger.info(method, "eventType = " + eventType);
		Logger.info(method, "subscriptionId = " + subscriptionId);
		Logger.info(method, "initiatingUser = " + initiatingUser);
	}
	
	private void processChildren(MbElement element, JSONObject jsonChild, int depth) throws MbException
	{
		if (element == null) {
			return;
		}

		int newDepth = depth + 1;

		try
		{
			MbElement child = element.getFirstChild();
			if (child == null) {
				return;
			}

			hmacJsonStr += "{";

			while(child != null)
			{
				MbElement newChild = child.getFirstChild();
				
				buildJsonForHmac(child, newDepth, newChild, false);
				
				if (newChild != null)
				{
					if (newChild.getName().equals("Item"))
					{
						JSONArray jsonArray = new JSONArray();
						jsonChild.put(child.getName(), jsonArray);
						processItemArray(child, jsonArray, newDepth);
					}
					else
					{
						JSONObject jsonNewChild = new JSONObject();
						jsonChild.put(child.getName(), jsonNewChild);
						processChildren(child, jsonNewChild, newDepth);
					}
				}
				else
				{
					jsonChild.put(child.getName(), child.getValueAsString());
				}
				
				child = child.getNextSibling();
				
				if (child != null) {
					hmacJsonStr += ",";
				}
			}
			
			hmacJsonStr += "}";

		} catch (MbException e) {
			Logger.error("JSONData.processChildren: ", "Parsing failure", e);
			throw e;
		}
	}

	private void processItemArray(MbElement element, JSONArray jsonArrayChild, int depth) throws MbException
	{
		try
		{
			MbElement child = element.getFirstChild();
			if (child == null) {
				return;
			}
			
			hmacJsonStr += "[";

			while(child != null)
			{
				MbElement newChild = child.getFirstChild();

				buildJsonForHmac(child, depth, newChild, true);

				if (newChild != null)
				{
					JSONObject jsonNewChild = new JSONObject();
					jsonArrayChild.add(jsonNewChild);
					processChildren(child, jsonNewChild, depth);
				}
				else
				{
					JSONObject jsonObj = new JSONObject();
					jsonObj.put(child.getName(), child.getValueAsString());
					jsonArrayChild.add(jsonObj);
				}
				
				child = child.getNextSibling();
				
				if (child != null) {
					hmacJsonStr += ",";
				}
			}
			
			hmacJsonStr += "]";
			
		} catch (MbException e) {
			Logger.error("JSONData.processItemArray: ", "Parsing failure", e);
			throw e;
		}
	}

	/*
	 * Build Json for HMAC confirmation
	 */
	private void buildJsonForHmac(MbElement child, int depth, MbElement newChild, boolean isArray) throws MbException
	{
		if (!isArray) {
			hmacJsonStr += "\"" + child.getName() + "\":";
		}
		
		if (newChild == null) {
			String value = child.getValueAsString();
	        value = value.replace("/", "\\/");
			hmacJsonStr += "\"" + value  + "\"";
		}
		
		// Optional printout
/*		String pad = "   ";
		String spaces = "";
		
		for (int i = 0; i < depth; i++) {
			spaces+= pad;
		}
		
		Logger.debug("", spaces + child.getName() + ":  " + child.getValueAsString());*/
	}
}
