/**
 * Copyright (C) 2012-2013 Dell, Inc
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.google;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @deprecated replaced with {@link org.dasein.cloud.google.util.GoogleAuthUtils}
 */
public class HttpsConnection {

	/** E-mail address of the service account. */

//	public static InputStream getResponseBody(HttpMethod conn) throws Exception {
//		if (conn == null) throw new Exception("Not connected !");
//		return conn.getResponseBodyAsStream();
//	}

	public static File writeToFile(InputStream in, String name) throws Exception 
	{

		BufferedReader is 	= new BufferedReader(new InputStreamReader(in));
		String line 		= "";
		File f 				= new File(name);
		OutputStream out 	= new FileOutputStream(f);
		while ((line = is.readLine()) != null)
		{
			out.write(line.getBytes());
		}
		out.close();
		in.close();
		return f;
	}
	public static String printFile(String file) throws Exception 
	{
		BufferedReader inputFile = new BufferedReader(new FileReader(file));
		String line =  "";
		String buff = "";
		// Read first line from file
		while ((line = inputFile.readLine()) != null) buff += line + "\n";
		return buff;
	}

	public static void main(String[] args) throws Exception {
		JSONArray a = new JSONArray(Arrays.asList(getJSON("676516473943-jt3udkrj247hvsuh1nrfivr5cfn84uio@developer.gserviceaccount.com", "D:\\GCE-privatekey.p12")));
		System.out.println("Array : " + a.toString());
		
	}
	public static String getJSON(String iss, String p12File) throws Exception  {
		System.out.println("ISS : " + iss);
		System.out.println("P12File : " + p12File);
		
		HttpClient client = new DefaultHttpClient();
		List formparams = new ArrayList();
		formparams.add(new BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer"));
		formparams.add(new BasicNameValuePair("assertion", GenerateToken.getToken(iss, p12File)));
		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");
		
		
//		HttpClient client1 = new HttpClient();
		String url 	  =  "https://accounts.google.com/o/oauth2/token";
//		System.out.println(url);
//		PostMethod pm = new PostMethod(url); 
//		pm.addRequestHeader("Content-Type", "application/x-www-form-urlencoded");
//		pm.addRequestHeader("Host", "accounts.google.com");
////		pm.addRequestHeader("Host", "accounts.google.com");
//		pm.addParameter("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
//		pm.addParameter("assertion", GenerateToken.getData());
////		System.out.println(C.getData());
//
//		int statusCode = client1.executeMethod(pm);

		HttpPost httppost = new HttpPost(url);
		httppost.addHeader("Content-Type", "application/x-www-form-urlencoded");
			httppost.setEntity(entity);
			HttpResponse httpResponse1 = client.execute(httppost);
			int s1 = httpResponse1.getStatusLine().getStatusCode();
			if (s1 == HttpStatus.SC_OK) {
				try {
					//	InputStream in = getResponseBody(pm);
						 InputStream in = httpResponse1.getEntity().getContent();
						writeToFile(in, "D:\\google_out.txt");
						System.out.println(printFile("D:\\google_out.txt"));
					} catch (Exception e) {
						System.out.println("No response body !");
					}
			}
		

		JSONObject obj1 = new JSONObject(printFile("D:\\google_out.txt"));
		String access_token = obj1.getString("access_token");
		String token_type = obj1.getString("token_type");
		String expires_in = obj1.getString("expires_in");
String resource = "instances";
		url = "https://www.googleapis.com/compute/v1beta14/projects/enstratus.com:enstratus-dev/zones/us-east1-a/"+ resource+ "?access_token=" + access_token + "&token_type=Bearer&expires_in=3600" ;
		String str = "{"+ 
 "\"image\": \"https://www.googleapis.com/compute/v1beta14/projects/google/global/images/gcel-10-04-v20130104\","+ 
 "\"machineType\": \"https://www.googleapis.com/compute/v1beta14/projects/enstratus.com:enstratus-dev/global/machineTypes/n1-standard-1\","+ 
 "\"name\": \"trial\","+ 
 "\"zone\": \"https://www.googleapis.com/compute/v1beta14/projects/enstratus.com:enstratus-dev/zones/us-east1-a/\"," +
 "\"networkInterfaces\": [ {  \"network\": \"https://www.googleapis.com/compute/v1beta14/projects/enstratus.com:enstratus-dev/networks/default\" } ],"+
 "\"disks\": [ { \"type\": \"EPHEMERAL\",  \"deleteOnTerminate\": true  } ], \"metadata\": { \"items\": [ ],  \"kind\": \"compute#metadata\" }}";
		System.out.println(str);
		JSONObject json = new JSONObject(str);
		System.out.println("POST Methods : " + url);
		StringEntity se = new StringEntity(str);
		 JSONObject json1 = new JSONObject();
		 json1.put("image", "https://www.googleapis.com/compute/v1beta14/projects/google/global/images/gcel-10-04-v20130104");
		 json1.put("machineType", "https://www.googleapis.com/compute/v1beta14/projects/enstratus.com:enstratus-dev/global/machineTypes/n1-standard-1");
		 json1.put("name", "trial");
		 json1.put("zone", "https://www.googleapis.com/compute/v1beta14/projects/enstratus.com:enstratus-dev/zones/us-east1-a/");
		 //json1.put("image", "https://www.googleapis.com/compute/v1beta13/projects/google/images/ubuntu-10-04-v20120621");
		 System.out.println(" JSON 1 : " + json.toString() + " \n JSON 2 : " + json1.toString());
		List<NameValuePair> nameValuePairs = new
		         ArrayList<NameValuePair>(1);
		         nameValuePairs.add(new BasicNameValuePair("json", str));
		         
		         
		         
		         

//		        
//		         JSONObject jsonPayload = null;
//		         JSONObject obj = new JSONObject();
//		         
//		         try {
//		         obj.put("name", "vinotrial");
//		         obj.put("IPv4Range", "192.0.0.0/16");
//		         obj.put("description", "wrwer");
//
//		         } catch (Exception e) {
//		         	
//		         }
		         
		         
		         
		         
			        /*
		         JSONObject jsonPayload = null;
		         JSONObject obj = new JSONObject();
		         
		         try {
		         obj.put("name", "testCreateStandardFirewall1734".toLowerCase());
		         obj.put("description", "SSH allowed from anywhere");
		         obj.put("network", "https://www.googleapis.com/compute/v1beta13/projects/enstratus.com:enstratus-dev/networks/default");
		         JSONArray sranges = new JSONArray();
		         JSONArray allowed = new JSONArray();
		         JSONObject allowedtemp = new JSONObject();
		         JSONArray ports = new JSONArray();
		         allowedtemp.put("IPProtocol", "tcp");
		         ports.put("22");
		         allowedtemp.put("ports", ports);
		         allowed.put(allowedtemp);
//		         
//		         JSONObject allowedtemp1 = new JSONObject();
//		         JSONArray ports1 = new JSONArray();
//		         allowedtemp1.put("IPProtocol", "udp");
//		         ports1.put("1-65535");
//		         allowedtemp1.put("ports", ports1);
//		         allowed.put(allowedtemp1);
//		         
//		         
//		         
//		         JSONObject allowedtemp2 = new JSONObject();
//		         
//		         allowedtemp2.put("IPProtocol", "icmp");
//		         
//		         allowed.put(allowedtemp2);
		         
		         
		         sranges.put("0.0.0.0/0");
		         obj.put("sourceRanges", sranges);
		         obj.put("allowed", allowed);
		         } catch (Exception e) {
		         	
		         }
		         
		         
		      */   
		
		//UrlEncodedFormEntity entity1 = new UrlEncodedFormEntity(formparams1, "UTF-8");
		System.out.println("Creating an instance");
		HttpPost httppost1 = new HttpPost(url);
		httppost1.setHeader("Content-type", "application/json");
		//httppost1.addHeader("X-JavaScript-User-Agent", "trov");
			//httppost1.setEntity(se);
	//	httppost1.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		
		
		System.out.println("payload:" + json1.toString());
		System.out.println("url:" + url);
		StringEntity se1 = new StringEntity(json1.toString());
		se1.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
		httppost1.setEntity(se1);
		//
		
		
		
		HttpClient base = new DefaultHttpClient();
		SSLContext ctx = SSLContext.getInstance("TLS");
        X509TrustManager tm = new X509TrustManager() {

        	public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
            }

            public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };
        ctx.init(null, new TrustManager[]{tm}, null);
        SSLSocketFactory ssf = new SSLSocketFactory(ctx);
        ssf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        ClientConnectionManager ccm = base.getConnectionManager();
        SchemeRegistry sr = ccm.getSchemeRegistry();
        sr.register(new Scheme("https", ssf, 443));
        HttpClient client1 = new DefaultHttpClient(ccm);
		
		
		
		
		
		
		
		
		//HttpClient client1 = new DefaultHttpClient();
			HttpResponse httpResponse2 = client1.execute(httppost1);
			int s2 = httpResponse2.getStatusLine().getStatusCode();
			if (s2 == HttpStatus.SC_OK) {
				try {
					//	InputStream in = getResponseBody(pm);
						 InputStream in = httpResponse2.getEntity().getContent();
						writeToFile(in, "D:\\google_out.txt");
						System.out.println(printFile("D:\\google_out.txt"));
					} catch (Exception e) {
						System.out.println("No response body !");
					}
			} else {
				System.out.println("Instance creation failed with error status " + s2);
				 InputStream in = httpResponse2.getEntity().getContent();
					writeToFile(in, "D:\\google_out.txt");
					System.out.println(printFile("D:\\google_out.txt"));
			}

		       String[]  Zone = {"europe-west1-a",
		    		   "europe-west1-b",
		    		   "us-central1-a",
		    		   "us-central1-b",
		    		   "us-central2-a", "us-east1-a"};
		       for (String zone : Zone) {
//		       {
		    	   HttpClient client3 = new DefaultHttpClient();
		resource = "instances";
			System.out.println("listing the instances !");
//		url= "https://www.googleapis.com/compute/v1beta13/projects/google/kernels?access_token=" + access_token + "&token_type=Bearer&expires_in=3600" ;
		url = "https://www.googleapis.com/compute/v1beta14/projects/enstratus.com:enstratus-dev/zones/us-central1-a/" +  resource +"?access_token=" + access_token + "&token_type=Bearer&expires_in=3600" ;
		//	url = "https://www.googleapis.com/compute/v1beta13/projects/enstratus.com:enstratus-dev?access_token=" + access_token + "&token_type=Bearer&expires_in=3600" ;
//			url = "https://www.googleapis.com/compute/v1beta14/projects/enstratus.com:enstratus-dev/zones/us-east1-a/instances?access_token=" + access_token + "&token_type=Bearer&expires_in=3600" ;
			System.out.println("url : -----------" + url );
		
		JSONArray items = new JSONArray();
		
		HttpGet method = new HttpGet(url);

		HttpResponse httpResponse = client3.execute(method);
		int s = httpResponse.getStatusLine().getStatusCode();
	
		if (s == HttpStatus.SC_OK) {
			
			try {
				System.out.println("\nResponse from Server : ");
				//InputStream in = getResponseBody(gm);
				InputStream in = httpResponse.getEntity().getContent();
				writeToFile(in, "D:\\calendar_out.txt");
				String str1 = printFile("D:\\calendar_out.txt");
				System.out.println(str1);
				JSONObject jsonO = new JSONObject(str1);
				items = (JSONArray) jsonO.get("items");
				
			//	return printFile("D:\\calendar_out.txt");
			} catch (Exception e) {
				System.out.println("No response body !" + e.getLocalizedMessage());
			}
			
		} else System.out.println(httpResponse);
		       
		
		
		for (int i = 0; i < items.length(); i++ ) {
		
			JSONObject item  = (JSONObject) items.get(i);
			String name = null;
			if (item.has("name")) name = (String) item.get("name");
			//System.out.println("instance : " + name);
			
		if (!name.contains("default")) {
		System.out.println("Deleting the instance " + name);
		url = "https://www.googleapis.com/compute/v1beta14/projects/enstratus.com:enstratus-dev/zones/us-central1-a/" + resource + "/"+ name + "?access_token=" + access_token + "&token_type=Bearer&expires_in=3600" ;
		System.out.println("url : " + url );
		
		
		HttpDelete delMethod = new HttpDelete(url);
		HttpResponse httpResponse3 = client.execute(delMethod);
		int s3 = httpResponse3.getStatusLine().getStatusCode();
		if (s3 == HttpStatus.SC_OK) {
			
			try {
				System.out.println("\nResponse from Server : ");
				//InputStream in = getResponseBody(gm);
				InputStream in = httpResponse3.getEntity().getContent();
				writeToFile(in, "D:\\calendar_out.txt");
				System.out.println(printFile("D:\\calendar_out.txt"));
			//	return printFile("D:\\calendar_out.txt");
			} catch (Exception e) {
				System.out.println("No response body !");
				
			}
			
		} else {
			System.out.println("Deleting failed with status : " + s3);
			try {
			InputStream in = httpResponse3.getEntity().getContent();
			writeToFile(in, "D:\\calendar_out.txt");
			System.out.println(printFile("D:\\calendar_out.txt"));
			} catch (Exception e) {
				
			}
		}
		}
		}
		
		       
//		https://www.googleapis.com/compute/v1beta13/projects/enstratus.com%3Aenstratus-dev/instances/trial
//		GetMethod gm      = new GetMethod(url); 
//		HttpMethodParams params = new HttpMethodParams();
////		params.setParameter("calendarId", "vidhyanallasamy%40gmail.com");
//		gm.setParams(params);
//
//		statusCode = client1.executeMethod(gm);
//		System.out.println("\nStatus Code : " + statusCode);
//
//		try {
//			System.out.println("\nResponse from Server : ");
//			InputStream in = getResponseBody(gm);
//			writeToFile(in, "D:\\calendar_out.txt");
//			System.out.println(printFile("D:\\calendar_out.txt"));
//		} catch (Exception e) {
//			System.out.println("No response body !");
//		}\
		       }
		return null;
	}
	
}
