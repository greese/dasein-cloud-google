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

package org.dasein.cloud.google.compute.server;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.ImageList;
import org.apache.commons.lang.StringUtils;
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.capabilities.GCEImageCapabilities;
import org.dasein.cloud.google.common.InvalidResourceIdException;
import org.dasein.cloud.google.common.NoContextException;
import org.dasein.cloud.google.util.GoogleEndpoint;
import org.dasein.cloud.google.util.GoogleExceptionUtils;
import org.dasein.cloud.google.util.GoogleLogger;
import org.dasein.cloud.google.util.model.GoogleImages;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Implements the images functionality supported in the Google Compute Engine API.
 *
 * @since 2013.01
 */
public class GoogleImageSupport extends AbstractImageSupport {

	private static final Logger logger = GoogleLogger.getLogger(GoogleImageSupport.class);

	private Google provider;

	public GoogleImageSupport(Google provider) {
		super(provider);
		this.provider = provider;
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
	public @Nullable MachineImage getImage(String providerImageId) throws CloudException, InternalException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.Images.Get getImageRequest = compute.images().get(GoogleEndpoint.IMAGE.getProjectId(providerImageId),
					GoogleEndpoint.IMAGE.getImageId(providerImageId));
			Image googleImage = getImageRequest.execute();
			if (googleImage != null) {
				return GoogleImages.toDaseinImage(googleImage, provider.getContext());
			}
		} catch (InvalidResourceIdException e) {
			// when ID is invalid return null machine image
			logger.warn("Invalid image ID [{}] was provided", providerImageId);
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		return null;
	}

	@Override
	public String getProviderTermForImage(Locale locale, ImageClass cls) {
		return "image";
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public Iterable<ResourceStatus> listImageStatus(ImageClass cls) throws CloudException, InternalException {
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
	public @Nonnull Iterable<MachineImage> listImages(@Nullable ImageFilterOptions options) throws CloudException, InternalException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		// list private account images
		if (options != null && StringUtils.isNotBlank(options.getAccountNumber())) {
			return listImagesInProject(options, options.getAccountNumber());
		}

		// list public accounts images if account ID is not provided
		List<MachineImage> daseinImages = new ArrayList<MachineImage>();
		for (String imagesProject : GoogleImages.getPublicImagesProjects()) {
			daseinImages.addAll(listImagesInProject(options, imagesProject));
		}

		return daseinImages;
	}

	public @Nonnull Collection<MachineImage> listImagesInProject(ImageFilterOptions options, String projectId) throws CloudException, InternalException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		List<MachineImage> daseinImages = new ArrayList<MachineImage>();
		try {
			Compute.Images.List listImagesRequest = compute.images().list(projectId);
			listImagesRequest.setFilter(options.getRegex());
			ImageList imageList = listImagesRequest.execute();

			if (imageList.getItems() != null) {
				for (Image googleImage : imageList.getItems()) {
					// currently return only "not deprecated" images
					if (googleImage.getDeprecated() == null) {
						MachineImage machineImage = GoogleImages.toDaseinImage(googleImage, provider.getContext());
						daseinImages.add(machineImage);
					}
				}
			}
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		// TODO: add filtering by options predicate
		return daseinImages;
	}

	@Override
	public Iterable<MachineImage> listImages(ImageClass cls, String ownedBy) throws CloudException, InternalException {
		Iterable<MachineImage> images = listImages(cls);
		List<MachineImage> ownedByImages = new ArrayList<MachineImage>();
		if (ownedBy != null) {
			for (MachineImage image : images) {
				if (image.getProviderOwnerId().equals(ownedBy)) {
					ownedByImages.add(image);
				}
			}
			return ownedByImages;
		} else {
			return images;
		}
	}

	@Override
	public MachineImage registerImageBundle(ImageCreateOptions options) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support for bundling images");
	}

	@Override
	public void remove(String providerImageId, boolean checkState) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support deprecating public images");
	}

	private @Nonnull Iterable<MachineImage> executeImageSearch(@Nullable String accountNumber, @Nullable String keyword,
															   @Nullable Platform platform, @Nullable Architecture architecture,
															   @Nonnull ImageClass cls) throws CloudException, InternalException {


		// TODO : Google Image not associated with any account info. Need to check.
		List<MachineImage> searchImages = new ArrayList<MachineImage>();
		Iterable<MachineImage> images = listMachineImages();
		for (MachineImage image : images) {
			if (accountNumber != null && !image.getProviderOwnerId().equals(accountNumber))
				continue;
			if (keyword != null)
				if (!(image.getName().contains(keyword) || image.getDescription().contains(keyword) || image.getProviderMachineImageId().contains(keyword)))
					continue;
			if (platform != null && !image.getPlatform().equals(platform))
				continue;
			if (architecture != null && !image.getArchitecture().equals(architecture))
				continue;
			if (cls != null && !image.getImageClass().equals(cls))
				continue;
			searchImages.add(image);
		}
		return searchImages;
	}

	@Override
	public @Nonnull Iterable<MachineImage> searchImages(String accountNumber,
														String keyword, Platform platform, Architecture architecture,
														ImageClass... imageClasses) throws CloudException, InternalException {
		if (imageClasses == null || imageClasses.length < 1) {
			return executeImageSearch(accountNumber, keyword, platform, architecture, ImageClass.MACHINE);
		} else if (imageClasses.length == 1) {
			return executeImageSearch(accountNumber, keyword, platform, architecture, imageClasses[0]);
		} else {
			ArrayList<MachineImage> images = new ArrayList<MachineImage>();

			for (ImageClass cls : imageClasses) {
				for (MachineImage img : executeImageSearch(accountNumber, keyword, platform, architecture, cls)) {
					images.add(img);
				}
			}
			return images;
		}
	}

	@Nonnull
	private Iterable<MachineImage> executePublicImageSearch(@Nullable String keyword, @Nullable Platform platform,
															@Nullable Architecture architecture, @Nonnull ImageClass cls) throws CloudException, InternalException {
		List<MachineImage> searchImages = new ArrayList<MachineImage>();
		Iterable<MachineImage> images = listImages(cls);
		for (MachineImage image : images) {
			if (keyword != null)
				if (!(image.getName().contains(keyword) || image.getDescription().contains(keyword) || image.getProviderMachineImageId().contains(keyword)))
					continue;
			if (platform != null && !image.getPlatform().equals(platform))
				continue;
			if (architecture != null && !image.getArchitecture().equals(architecture))
				continue;
			//				if (cls != null && !image.getImageClass().equals(cls))
			//					continue;
			searchImages.add(image);
		}
		return searchImages;
	}

	@Override
	public Iterable<MachineImage> searchPublicImages(String keyword, Platform platform, Architecture architecture,
													 ImageClass... imageClasses) throws CloudException, InternalException {
		if (imageClasses == null || imageClasses.length < 1) {
			return executePublicImageSearch(keyword, platform, architecture, ImageClass.MACHINE);
		} else if (imageClasses.length == 1) {
			return executePublicImageSearch(keyword, platform, architecture, imageClasses[0]);
		} else {
			ArrayList<MachineImage> images = new ArrayList<MachineImage>();

			for (ImageClass cls : imageClasses) {
				for (MachineImage img : executePublicImageSearch(keyword, platform, architecture, cls)) {
					images.add(img);
				}
			}
			return images;
		}
	}

	@Override
	public void updateTags(String imageId, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google image does not have meta data");
	}

	@Override
	public void updateTags(String[] imageIds, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google image does not have meta data");
	}

	@Override
	public void removeTags(String imageId, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google image does not have meta data");
	}

	@Override
	public void removeTags(String[] imageIds, Tag... tags) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google image does not have meta data");
	}

	@Override
	public Iterable<MachineImage> searchPublicImages(ImageFilterOptions arg0) throws InternalException, CloudException {
		String accountNumber = arg0.getAccountNumber();
		Architecture architecture = arg0.getArchitecture();
		Platform platform = arg0.getPlatform();
		String keyword = null;
		return searchImages(accountNumber, keyword, platform, architecture);
	}

}
