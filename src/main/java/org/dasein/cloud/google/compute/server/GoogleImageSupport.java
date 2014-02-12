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
import org.dasein.cloud.*;
import org.dasein.cloud.compute.*;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.common.NoContextException;
import org.dasein.cloud.google.util.GoogleExceptionUtils;
import org.dasein.cloud.google.util.model.GoogleImages;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.util.Cache;
import org.dasein.cloud.util.CacheLevel;
import org.dasein.util.uom.time.Hour;
import org.dasein.util.uom.time.TimePeriod;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * Implements the images functionality supported in the Google Compute Engine API.
 *
 * @since 2013.01
 */
public class GoogleImageSupport implements MachineImageSupport {

	private static final Logger logger = Google.getLogger(GoogleImageSupport.class);

	private Google provider;

	public GoogleImageSupport(Google provider) {
		this.provider = provider;
	}

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	@Override
	public void addImageShare(String providerImageId, String accountNumber) throws CloudException, InternalException {
		throw new OperationNotSupportedException("No ability to share images");
	}

	@Override
	public void addPublicShare(String providerImageId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("No ability to make images public");
	}

	@Nonnull
	@Override
	public Iterable<VmState> getCaptureImageStates(@Nullable MachineImage img) {
		logger.warn("Operation not supported yet");
		return Collections.emptyList();
	}

	@Nonnull
	@Override
	public Iterable<VmState> getBundleVirtualMachineStates(@Nullable MachineImage img) {
		logger.warn("Operation not supported yet");
		return Collections.emptyList();
	}

	@Override
	public String bundleVirtualMachine(String virtualMachineId, MachineImageFormat format, String bucket, String name)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Bundling of virtual machines not supported");
	}

	@Override
	public void bundleVirtualMachineAsync(String virtualMachineId, MachineImageFormat format, String bucket, String name,
										  AsynchronousTask<String> trackingTask) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Bundling of virtual machines not supported");
	}

	@Override
	public MachineImage captureImage(ImageCreateOptions options) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Capturing image of virtual machines not supported");
	}

	@Override
	public void captureImageAsync(ImageCreateOptions options, AsynchronousTask<MachineImage> taskTracker)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Capturing image of virtual machines not supported");
	}

	@Override
	public MachineImage getImage(String providerImageId) throws CloudException, InternalException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		Compute compute = provider.getGoogleCompute();

		try {
			Compute.Images.Get getImageRequest = compute.images().get(GoogleImages.GOOGLE_IMAGES_PROJECT, providerImageId);
			Image googleImage = getImageRequest.execute();
			if (googleImage != null) {
				return GoogleImages.toDaseinImage(googleImage, provider.getContext());
			}
		} catch (IOException e) {
			GoogleExceptionUtils.handleGoogleResponseError(e);
		}

		return null;
	}

	@Override
	public MachineImage getMachineImage(String providerImageId)
			throws CloudException, InternalException {
		return getImage(providerImageId);
	}

	@Override
	public String getProviderTermForImage(Locale locale) {
		return "image";
	}

	@Override
	public String getProviderTermForImage(Locale locale, ImageClass cls) {
		return "image";
	}

	@Override
	public String getProviderTermForCustomImage(Locale locale, ImageClass cls) {
		return "image";
	}

	@Override
	public boolean hasPublicLibrary() {
		return true;
	}

	@Override
	public Requirement identifyLocalBundlingRequirement() throws CloudException, InternalException {
		return Requirement.NONE;
	}

	@Override
	public AsynchronousTask<String> imageVirtualMachine(String vmId, String name, String description)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support capturing images");
	}

	@Override
	public boolean isImageSharedWithPublic(String providerImageId) throws CloudException, InternalException {
		return false;
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
	public Iterable<MachineImage> listImages(ImageClass cls) throws CloudException, InternalException {
		// TODO: modify filter to match 'cls' image class if needed - for now {@link ImageClass#MACHINE} is the only one supported by Google
		return listImages(ImageFilterOptions.getInstance());
	}

	@Override
	public Iterable<MachineImage> listImages(ImageFilterOptions options) throws CloudException, InternalException {
		if (!provider.isInitialized()) {
			throw new NoContextException();
		}

		ProviderContext context = provider.getContext();
		List<MachineImage> daseinImages = new ArrayList<MachineImage>();

		// load public images from cache if possible
		Cache<MachineImage> cache = Cache.getInstance(provider, context.getAccountNumber() + "-public-images",
				MachineImage.class, CacheLevel.CLOUD_ACCOUNT, new TimePeriod<Hour>(1, TimePeriod.HOUR));
		Collection<MachineImage> cachedPublicImages = (Collection<MachineImage>) cache.get(context);

		if (cachedPublicImages == null) {
			cachedPublicImages = new ArrayList<MachineImage>();
			for (String imagesProject : GoogleImages.getPublicImagesProjects()) {
				cachedPublicImages.addAll(listImagesInProject(options, imagesProject));
			}
			cache.put(context, cachedPublicImages);
		}

		// add globally public images
		daseinImages.addAll(cachedPublicImages);
		// add imaged owned by this project
		daseinImages.addAll(listImagesInProject(options, context.getAccountNumber()));

		return daseinImages;
	}

	@Nonnull
	public Collection<MachineImage> listImagesInProject(ImageFilterOptions options, String projectId) throws CloudException, InternalException {
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
	public Iterable<MachineImageFormat> listSupportedFormats()
			throws CloudException, InternalException {
		return Collections.singletonList(MachineImageFormat.RAW);
	}

	@Override
	public Iterable<MachineImageFormat> listSupportedFormatsForBundling()
			throws CloudException, InternalException {
		return Collections.emptyList();
	}

	@Override
	public Iterable<MachineImage> listMachineImages() throws CloudException,
			InternalException {
		return listImages(ImageClass.MACHINE);
	}

	@Override
	public Iterable<MachineImage> listMachineImagesOwnedBy(String accountId)
			throws CloudException, InternalException {
		return listImages(ImageClass.MACHINE, accountId);
	}

	@Override
	public Iterable<String> listShares(String providerImageId)
			throws CloudException, InternalException {
		return Collections.emptyList();
	}

	@Override
	public Iterable<ImageClass> listSupportedImageClasses() throws CloudException, InternalException {
		ArrayList<ImageClass> tmp = new ArrayList<ImageClass>();
		tmp.add(ImageClass.MACHINE);
		return tmp;
	}

	@Override
	public Iterable<MachineImageType> listSupportedImageTypes() throws CloudException, InternalException {
		ArrayList<MachineImageType> imageType = new ArrayList<MachineImageType>();
		imageType.add(MachineImageType.STORAGE);
		return imageType;
	}

	@Override
	public MachineImage registerImageBundle(ImageCreateOptions options)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support for bundling images");
	}

	@Override
	public void remove(String providerImageId) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support deprecating public images");
	}

	@Override
	public void remove(String providerImageId, boolean checkState) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support deprecating public images");
	}

	@Override
	public void removeAllImageShares(String providerImageId) throws CloudException, InternalException {
		// NO-OP
	}

	@Override
	public void removeImageShare(String providerImageId, String accountNumber)
			throws CloudException, InternalException {
//		throw new OperationNotSupportedException ("Google does not support sharing images");

	}

	@Override
	public void removePublicShare(String providerImageId)
			throws CloudException, InternalException {
//		throw new OperationNotSupportedException ("Google does not support sharing images");

	}

	@Nonnull
	private Iterable<MachineImage> executeImageSearch(@Nullable String accountNumber, @Nullable String keyword, @Nullable Platform platform, @Nullable Architecture architecture, @Nonnull ImageClass cls) throws CloudException, InternalException {
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
	public Iterable<MachineImage> searchImages(String accountNumber,
											   String keyword, Platform platform, Architecture architecture,
											   ImageClass... imageClasses) throws CloudException,
			InternalException {
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

	@Override
	public Iterable<MachineImage> searchMachineImages(String keyword,
													  Platform platform, Architecture architecture)
			throws CloudException, InternalException {
		return searchPublicImages(keyword, platform, architecture, ImageClass.MACHINE);
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
	public Iterable<MachineImage> searchPublicImages(String keyword,
													 Platform platform, Architecture architecture,
													 ImageClass... imageClasses) throws CloudException,
			InternalException {
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
	public void shareMachineImage(String providerImageId, String withAccountId, boolean allow) throws CloudException, InternalException {
		throw new OperationNotSupportedException("Google does not support sharing images");

	}

	@Override
	public boolean supportsCustomImages() throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean supportsDirectImageUpload() throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean supportsImageCapture(MachineImageType type) throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean supportsImageSharing() throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean supportsImageSharingWithPublic() throws CloudException,
			InternalException {
		return false;
	}

	@Override
	public boolean supportsPublicLibrary(ImageClass cls) throws CloudException, InternalException {
		return true;
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
