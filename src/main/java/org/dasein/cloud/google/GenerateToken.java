package org.dasein.cloud.google;

import org.apache.commons.codec.binary.Base64;
import java.io.*; 
import java.security.*; 
import java.text.MessageFormat;  
import java.util.Enumeration;

public class GenerateToken {


	public static String getToken(String iss, String p12File) {

		String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
		String claimTemplate = "'{'\"iss\": \"{0}\", \"scope\": \"{1}\", \"aud\": \"{2}\", \"exp\": \"{3}\", \"iat\": \"{4}\"'}'";

		try {
			StringBuffer token = new StringBuffer();

			//Encode the JWT Header and add it to our string to sign
			token.append(Base64.encodeBase64URLSafeString(header.getBytes("UTF-8")));

			//Separate with a period
			token.append(".");

			//Create the JWT Claims Object
			String[] claimArray = new String[5];
			claimArray[0] = iss;
			claimArray[1] = "https://www.googleapis.com/auth/compute";
			claimArray[2] = "https://accounts.google.com/o/oauth2/token";
			claimArray[3] = Long.toString( ( System.currentTimeMillis()/1000 ) + 300);
			claimArray[4] = Long.toString( ( System.currentTimeMillis()/1000 ));
			MessageFormat claims;
			claims = new MessageFormat(claimTemplate);
			String payload = claims.format(claimArray);
//			System.out.println(claimArray[3]);
//			System.out.println(claimArray[4]);
			//Add the encoded claims object
			token.append(Base64.encodeBase64URLSafeString(payload.getBytes("UTF-8")));

			char[] password = "notasecret".toCharArray();
			FileInputStream fin = new FileInputStream(new File(p12File));
			KeyStore store = KeyStore.getInstance("PKCS12");
			try {
				store.load(fin, password);                
			} 
			finally {
				try {
					fin.close();
				} catch (IOException e) { }
			}
			String alias = "";
			// KeyStore keystore = getKeyStore(password);            
			Enumeration<String> enum1 = store.aliases(); // List the aliases
			while(enum1.hasMoreElements()) {
				String keyStoreAlias = enum1.nextElement().toString();
				if(store.isKeyEntry(keyStoreAlias)) { //Does alias refer to a private key?
					alias=keyStoreAlias;
					break;
				}   } 
			PrivateKey  privateKey = (PrivateKey) store.getKey(alias, password);


			//Sign the JWT Header + "." + JWT Claims Object
			Signature signature = Signature.getInstance("SHA256withRSA");
			signature.initSign(privateKey);
			signature.update(token.toString().getBytes("UTF-8"));
			String signedPayload = Base64.encodeBase64URLSafeString(signature.sign());

			//Separate with a period
			token.append(".");

			//Add the encoded signature
			token.append(signedPayload);

			//      System.out.println(token.toString());
			return token.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}