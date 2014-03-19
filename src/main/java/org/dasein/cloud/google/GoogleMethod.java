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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Operation;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.*;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Properties;

/**
 * Represents the interaction point between Dasein Cloud and the underlying REST API.
 * @author Drew Lyall
 * @version 2014.03 initial version
 * @since 2014.03
 */
public class GoogleMethod {

	static private final Logger logger = Google.getLogger(GoogleMethod.class);
	static private final Logger wire = Google.getWireLogger(GoogleMethod.class);

	private Google provider;

	public GoogleMethod(@Nonnull Google provider) {
        this.provider = provider;
    }

    public @Nonnull String getOperationTarget(@Nonnull ProviderContext ctx, @Nonnull Operation job, @Nonnull GoogleOperationType operationType, String regionId, String dataCenterId, boolean getLink)throws CloudException, InternalException{
        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 20L);
        while(timeout > System.currentTimeMillis()) {
            if(job.getError() != null){
                System.out.println("1");
                for(Operation.Error.Errors error : job.getError().getErrors()){
                    System.out.println("2");
                    throw new CloudException("An error occurred: " + error.getMessage());
                }
            }
            else if(job.getStatus().equals("DONE")){
                if(getLink) return job.getTargetLink();
                else return job.getTargetLink().substring(job.getTargetLink().lastIndexOf("/") + 1);
            }

            try{
                Thread.sleep(150L);
            }
            catch(InterruptedException ignore){}

            try{
                Compute gce = provider.getGoogleCompute();
                switch(operationType){
                    case GLOBAL_OPERATION:{
                        job = gce.globalOperations().get(ctx.getAccountNumber(), job.getName()).execute();
                        break;
                    }
                    case REGION_OPERATION:{
                        job = gce.regionOperations().get(ctx.getAccountNumber(), regionId, job.getName()).execute();
                        break;
                    }
                    case ZONE_OPERATION:{
                        job = gce.zoneOperations().get(ctx.getAccountNumber(), dataCenterId, job.getName()).execute();
                        break;
                    }
                }
            }
            catch(IOException ex){
                logger.error(ex.getMessage());
            }
        }
        throw new CloudException(CloudErrorType.COMMUNICATION, 408, "", "System timed out waiting for Operation to complete");
    }

    public @Nonnull boolean getOperationComplete(ProviderContext ctx, Operation job, GoogleOperationType operationType, String regionId, String dataCenterId)throws CloudException, InternalException{
        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 20L);
        while(timeout > System.currentTimeMillis()) {
            if(job.getError() != null){
                for(Operation.Error.Errors error : job.getError().getErrors()){
                    throw new CloudException("An error occurred: " + error.getMessage());
                }
            }
            else if(job.getStatus().equals("DONE")){
                return true;
            }

            try{
                Thread.sleep(150L);
            }
            catch(InterruptedException ignore){}

            try{
                Compute gce = provider.getGoogleCompute();
                switch(operationType){
                    case GLOBAL_OPERATION:{
                        job = gce.globalOperations().get(ctx.getAccountNumber(), job.getName()).execute();
                        break;
                    }
                    case REGION_OPERATION:{
                        job = gce.regionOperations().get(ctx.getAccountNumber(), regionId, job.getName()).execute();
                        break;
                    }
                    case ZONE_OPERATION:{
                        job = gce.zoneOperations().get(ctx.getAccountNumber(), dataCenterId, job.getName()).execute();
                        break;
                    }
                }
            }
            catch(IOException ex){

            }
        }
        throw new CloudException(CloudErrorType.COMMUNICATION, 408, "", "System timed out waiting for Operation to complete");
    }
}
