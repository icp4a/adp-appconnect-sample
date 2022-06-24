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

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

public class HMACAuthentication
{
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    /**
     * Verifies the External Event action call has a valid HMAC by comparing it
     * to an independently and dynamically generated HMAC. As long as the
     * payload has not been tampered with or altered, the secret key is the same
     * on both the External Event action and the Receiver application, and the
     * same algorithm is used, the HMAC keys should be the same.
     * 
     * The sample application uses the constant {@code HMAC_CREDENTIALS_HEADER}
     * for the HMAC validation and also sets the same secret on the External
     * Event Action that is created/configured on startup of the application.
	 * 
     * Verifies that the HMAC credential passed by the External Event call
     * matches an HMAC credential generated from the Secret Credential and the
     * External Event action call payload. As long as the secret and algorithm
     * on the External Event action and the application match, the HMACs should
     * match.
     * 
     * @param hmacHeaderValue
     *            HMAC from External Event action call to validate
     * @param requestPayloadBytes
     *            A byte array representation of the JSON payload from the
     *            External Event action call. The HMAC is dynamically generated
     *            from this byte array and the Secret Credential
     * @return true if the External Event action call HMAC matches the
     *         dynamically generated HMAC, or false if they do not match.
     */
    static public boolean verifyHMACHeader(String hmacHeaderValue, byte[] requestPayloadBytes)
    {
        String method = "HMACAuthentication.verifyHMACHeader: ";
        boolean verified = false;
        String hmacComputed = null;
        
        String requestPayload = new String(requestPayloadBytes, StandardCharsets.UTF_8);
        
        try
        {
            if (requestPayload != null)
            {
                hmacComputed = calculateHMAC(requestPayload, Constants.HMAC_CREDENTIAL_SECRET);
            }

            Logger.debug(method, "hmacHeaderValue = " + hmacHeaderValue);
            Logger.debug(method, "hmacComputed =    " + hmacComputed);
            
            // Verify HMAC header value and computed HMAC are equal and not null
            verified = ((hmacComputed != null) && (hmacHeaderValue != null) && hmacComputed.equals(hmacHeaderValue));

        } catch (Exception e) {
            Logger.error(method, "Exception thrown when attempting to validate HMAC", e);
        }

        Logger.info(method, "HMAC Verified = " + verified);
        
        return verified;
    }

    /**
     * Calculates an HMAC for a given string and key.
     * 
     * @param data
     *            String to calculate an HMAC for
     * @param key
     *            A secret cryptographic key to use to calculate HMAC
     * @return HMAC for the given string and key
     * @throws SignatureException
     *             if the HMAC is unable to be generated
     * @throws NoSuchAlgorithmException
     *             if no Provider supports the {@code HmacSHA1} algorithm
     * @throws InvalidKeyException
     *             if the given key is inappropriate for initializing the HMAC.
     */
    static String calculateHMAC(String data, String key)
    									throws SignatureException, NoSuchAlgorithmException, InvalidKeyException
    {
        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(),HMAC_SHA1_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        mac.init(signingKey);
        return toBase64(mac.doFinal(data.getBytes()));
    }

    /**
     * Converts an array of bytes into a Base 64 encoded string.
     * 
     * @param bytes
     *            Array of bytes to convert
     * @return A base 64 encoded string representation of the provided byte
     *         array
     */
    private static String toBase64(byte[] bytes) {
        return DatatypeConverter.printBase64Binary(bytes);
    }
}
