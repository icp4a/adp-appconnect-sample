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

/**
 * General constants for the Automated Document Processing Webhook Receiver application.
 */
public class Constants
{
    // Webhook Event Action properties
    public static final String WEBHOOK_RECEIVER_REGISTRATION_ID = "FinalizeWebhook";
    public static final String HMAC_CREDENTIAL_SECRET = "eb497ac4891d6009d8ef601bfdf6c3f5";

    // Environment variable names containing Aria service account connection parameters to UMS identity provider
    public static final String ZEN_FRONT_DOOR_URL = "ZEN_FRONT_DOOR_URL";
    public static final String ZEN_ENABLED = "ZEN_ENABLED";
    public static final String IDP_SSO_URL = "IDP_SSO_URL";
    public static final String CPE_SERVICE_USER = "CPE_SERVICE_USER";
    public static final String CPE_SERVICE_PWD = "CPE_SERVICE_PWD";
    public static final String CPE_CLIENT_USER = "CPE_CLIENT_USER";
    public static final String CPE_CLIENT_PWD = "CPE_CLIENT_PWD";
    
    // Finalize document output file
    public static final String FINALIZE_DOC_LIST = "c:/myaceworkdir/ADP101/logs/KVPClassValues.txt";
}
