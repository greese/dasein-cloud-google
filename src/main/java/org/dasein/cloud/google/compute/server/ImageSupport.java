/**
 * Copyright (C) 2012-2014 Dell, Inc
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

package org.dasein.cloud.google.compute.server;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.ImageList;
import com.google.api.services.compute.model.Operation;
import org.apache.log4j.Logger;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.google.capabilities.GCEImageCapabilities;
import org.dasein.cloud.util.APITrace;

public class ImageSupport extends AbstractImageSupport {
	private Google provider;
	static private final Logger logger = Google.getLogger(ImageSupport.class);

    private enum ImageProject{
        DEBIAN(Platform.DEBIAN, "debian-cloud"),
        CENT_OS(Platform.CENT_OS, "centos-cloud"),
        RHEL(Platform.RHEL, "rhel-cloud"),
        SUSE(Platform.SUSE, "suse-cloud"),
        WINDOWS(Platform.WINDOWS, "windows-cloud"),
        GOOGLE(null, "google");

        private Platform platform;
        private String projectName;
        private ImageProject(Platform platform, String projectName){
            this.platform = platform;
            this.projectName = projectName;
        }

        public static String getImageProject(Platform platform){
            for(ImageProject imgProject : ImageProject.values()){
                if(platform != null && platform.equals(imgProject.platform)){
                    return imgProject.projectName;
                }
            }
            return GOOGLE.projectName;
        }
    }

	public ImageSupport(Google provider) {
        super(provider);
        this.provider = provider;
    }

	@Override
	public void addImageShare(@Nonnull String providerImageId, @Nonnull String accountNumber) throws CloudException, InternalException {
		throw new OperationNotSupportedException("No ability to share images");

	}

	@Override
	public void addPublicShare(@Nonnull String providerImageId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("No ability to make images public");

	}

	@Override
	public @Nonnull String bundleVirtualMachine(@Nonnull String virtualMachineId, @Nonnull MachineImageFormat format, @Nonnull String bucket, @Nonnull String name) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Bundling of virtual machines not supported");
	}

	@Override
	public void bundleVirtualMachineAsync(@Nonnull String virtualMachineId, @Nonnull MachineImageFormat format, @Nonnull String bucket, @Nonnull String name, AsynchronousTask<String> trackingTask) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Bundling of virtual machines not supported");
	}

    private transient volatile GCEImageCapabilities capabilities;
    @Override
    public @Nonnull GCEImageCapabilities getCapabilities(){
        if(capabilities == null){
            capabilities = new GCEImageCapabilities(provider);
        }
        return capabilities;
    }

	@Override
	public MachineImage getImage(@Nonnull String providerImageId) throws CloudException, InternalException {
        APITrace.begin(provider, "Image.getImage");

        if (providerImageId.contains("_") == false)
            throw new CloudException("Invalid image. Image does not conform to Dasein convention, " + providerImageId + " lacks a '_'" );

        try{
            ProviderContext ctx = provider.getContext();
            if( ctx == null ) {
                throw new CloudException("No context has been established for this request");
            }
            Compute gce = provider.getGoogleCompute();
            Image image;
            try{
                String[] parts = providerImageId.split("_");
                image = gce.images().get(parts[0], parts[1]).execute();
		    } catch (IOException ex) {
				logger.error("An error occurred while getting image: " + providerImageId + ": " + ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
					throw new CloudException(ex.getMessage());
			}
            return toMachineImage(image);
        }
        finally {
            APITrace.end();
        }
    }

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public @Nonnull Iterable<ResourceStatus> listImageStatus(@Nonnull ImageClass cls) throws CloudException, InternalException {
		List<ResourceStatus> status = new ArrayList<ResourceStatus>();

		Iterable<MachineImage> images = listImages(cls);
		for (MachineImage image : images) {
			MachineImageState state = image.getCurrentState();
			ResourceStatus resStatus = new ResourceStatus(image.getProviderMachineImageId(), state);
			status.add(resStatus);
		}
		return status;
	}

	@Override
	public @Nonnull Iterable<MachineImage> listImages(ImageFilterOptions options) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "Image.listImages");
        try{
            ArrayList<MachineImage> images = new ArrayList<MachineImage>();
            try{
                Compute gce = provider.getGoogleCompute();
                ImageList imgList = gce.images().list(provider.getContext().getAccountNumber()).execute();
                //TODO: Add filter options
                if(imgList.getItems() != null){
                    for(Image img : imgList.getItems()){
                        MachineImage image = toMachineImage(img);
                        if(image != null)images.add(image);
                    }
                }
		    } catch (IOException ex) {
				logger.error("An error occurred while listing images: " + ex.getMessage());
				if (ex.getClass() == GoogleJsonResponseException.class) {
					GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
					throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
				} else
					throw new CloudException(ex.getMessage());
			}
            return images;
        }
        finally {
            APITrace.end();
        }
    }

	@Override
	public @Nonnull Iterable<MachineImage> listMachineImages() throws CloudException, InternalException {
		return listImages(ImageClass.MACHINE);
	}

	@Override
	public @Nonnull Iterable<MachineImage> listMachineImagesOwnedBy(String accountId) throws CloudException, InternalException {
		return listImages(ImageClass.MACHINE, accountId);
	}

	@Override
	public @Nonnull Iterable<String> listShares(@Nonnull String providerImageId) throws CloudException, InternalException {
		return Collections.emptyList();
	}

	@Override
	public @Nonnull MachineImage registerImageBundle(@Nonnull ImageCreateOptions options) throws CloudException, InternalException {
		throw new OperationNotSupportedException ("Google does not support bundling images");
	}

	@Override
	public void remove(@Nonnull String providerImageId) throws CloudException, InternalException {
        remove(providerImageId, false);
	}

	@Override
	public void remove(@Nonnull String providerImageId, boolean checkState) throws CloudException, InternalException {
		Compute gce = provider.getGoogleCompute();
        Operation job = null;
        try{
            MachineImage image = getImage(providerImageId);
            if(image.getCurrentState().equals(MachineImageState.ACTIVE)){
                job = gce.images().delete(provider.getContext().getAccountNumber(), image.getName()).execute();

                GoogleMethod method = new GoogleMethod(provider);
                method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, "", "");
            }
	    } catch (IOException ex) {
			logger.error(ex.getMessage());
			if (ex.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException("An error occurred while deleting the image: " + ex.getMessage());
		}
	}

	@Override
	public void removeAllImageShares(@Nonnull String providerImageId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Image sharing is not supported in GCE");
	}

	@Override
	public void removeImageShare(@Nonnull String providerImageId, @Nonnull String accountNumber) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Image sharing is not supported in GCE");
	}

	@Override
	public void removePublicShare(@Nonnull String providerImageId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Image sharing is not supported in GCE");
	}

	@Override
	public @Nonnull Iterable<MachineImage> searchImages(String accountNumber, String keyword, Platform platform, Architecture architecture, ImageClass... imageClasses) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "Image.searchImages");
        try{
            ArrayList<MachineImage> results = new ArrayList<MachineImage>();
            Collection<MachineImage> images = new ArrayList<MachineImage>();
            if(accountNumber == null){
                images.addAll((Collection<MachineImage>)searchPublicImages(ImageFilterOptions.getInstance()));
            }
            images.addAll((Collection<MachineImage>)listImages(ImageFilterOptions.getInstance()));

            for( MachineImage image : images ) {
                if(image != null){
                    if( keyword != null ) {
                        if( !image.getProviderMachineImageId().contains(keyword) && !image.getName().contains(keyword) && !image.getDescription().contains(keyword) ) {
                            continue;
                        }
                    }
                    if( platform != null ) {
                        Platform p = image.getPlatform();

                        if( !platform.equals(p) ) {
                            if( platform.isWindows() ) {
                                if( !p.isWindows() ) {
                                    continue;
                                }
                            }
                            else if( platform.equals(Platform.UNIX) ){
                                if( !p.isUnix() ) {
                                    continue;
                                }
                            }
                            else {
                                continue;
                            }
                        }
                    }
                    if (architecture != null) {
                        if (architecture != image.getArchitecture()) {
                            continue;
                        }
                    }
                    results.add(image);
                }
            }

            return results;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<MachineImage> searchPublicImages(@Nonnull ImageFilterOptions options) throws InternalException, CloudException{
        APITrace.begin(getProvider(), "Image.searchPublicImages");
        try{
            ArrayList<MachineImage> images = new ArrayList<MachineImage>();
            try{
                Compute gce = provider.getGoogleCompute();
                Platform platform = options.getPlatform();
                ImageList imgList;

                if(platform != null){
                    String imageProject = ImageProject.getImageProject(platform);
                    imgList = gce.images().list(imageProject).execute();
                    if(imgList != null && imgList.getItems() != null){
                        for(Image img : imgList.getItems()){
                            MachineImage image = toMachineImage(img);
                            if(image != null)images.add(image);
                        }
                    }
                }
                else{
                    for(ImageProject imageProject : ImageProject.values()){
                        try{
                            imgList = gce.images().list(imageProject.projectName).execute();
                            if(imgList != null && imgList.getItems() != null){
                                for(Image img : imgList.getItems()){
                                    MachineImage image = toMachineImage(img);
                                    if(image != null)images.add(image);
                                }
                            }
                        }
                        catch(IOException ex){/*Don't really care, likely means the image project doesn't exist*/}
                    }
                }
    	    } catch (IOException ex) {
				logger.error("An error occurred while listing images: " + ex.getMessage());
    			if (ex.getClass() == GoogleJsonResponseException.class) {
    				GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
    				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
    			} else
    				throw new CloudException(ex.getMessage());
    		}
            return images;
        }
        finally {
            APITrace.end();
        }
    }

	@Override
	public void updateTags(@Nonnull String imageId, @Nonnull Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException ("Google image does not have meta data");
	}

	@Override
	public void updateTags(@Nonnull String[] imageIds, @Nonnull Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException ("Google image does not have meta data");
	}

	@Override
	public void removeTags(@Nonnull String imageId, @Nonnull Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException ("Google image does not have meta data");
	}

	@Override
	public void removeTags(@Nonnull String[] imageIds, @Nonnull Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException ("Google image does not have meta data");
	}

    private MachineImage toMachineImage(Image img){
        if(img.getDeprecated() != null && (img.getDeprecated().getState().equals("DELETED") || img.getDeprecated().getState().equals("DEPRECATED"))){
            return null;
        }

        String imageStatus = img.getStatus();
        MachineImageState state = null;
        if(imageStatus.equalsIgnoreCase("READY"))state = MachineImageState.ACTIVE;
        else if(imageStatus.equalsIgnoreCase("PENDING"))state = MachineImageState.PENDING;
        else return null;//TODO: This might not be appropriate - the final state is FAILED

        Architecture arch = Architecture.I64;
        Platform platform = Platform.guess(img.getName());
        String project = "";
        Pattern p = Pattern.compile("/projects/(.*?)/");
        Matcher m = p.matcher(img.getSelfLink());
        while(m.find()){
            project = m.group(1);
            break;
        }

        String owner = provider.getCloudName();
        if(project.equals(provider.getContext().getAccountNumber()))owner = provider.getContext().getAccountNumber();
        String description = null;
        if (img.getDescription() != null)
            description = img.getDescription();
        else
            description = "Created from " + img.getSourceDisk();

        MachineImage image = MachineImage.getImageInstance(owner, "", project + "_" + img.getName(), ImageClass.MACHINE, state, img.getName(), description, arch, platform, MachineImageFormat.RAW, VisibleScope.ACCOUNT_GLOBAL);
        image.setTag("contentLink", img.getSelfLink());
        image.setTag("project", project);

        return image;
    }
}
