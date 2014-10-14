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

import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.Compute;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.util.CalendarWrapper;

import javax.annotation.Nonnull;

import java.io.*;

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
                for(Operation.Error.Errors error : job.getError().getErrors()){
                    throw new CloudException("An error occurred: " + error.getMessage());
                }
            }
            else if(job.getStatus().equals("DONE")){
                if(getLink) return job.getTargetLink();
                else return job.getTargetLink().substring(job.getTargetLink().lastIndexOf("/") + 1);
            }

            try{
                Thread.sleep(1000L);
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
                Thread.sleep(1000L);
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
