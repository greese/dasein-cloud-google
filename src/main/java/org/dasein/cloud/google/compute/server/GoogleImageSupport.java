package org.dasein.cloud.google.compute.server;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.dasein.cloud.AsynchronousTask;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.ImageCreateOptions;
import org.dasein.cloud.compute.ImageFilterOptions;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageFormat;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.MachineImageSupport;
import org.dasein.cloud.compute.MachineImageType;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleMethod.Param;
import org.dasein.cloud.identity.ServiceAction;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GoogleImageSupport  implements MachineImageSupport {
	private Google provider;
	static private final Logger logger = Google.getLogger(GoogleImageSupport.class);

	public GoogleImageSupport(Google provider) { this.provider = provider; }

	@Override
	public String[] mapServiceAction(ServiceAction action) {
		return new String[0];
	}

	@Override
	public void addImageShare(String providerImageId, String accountNumber)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("No ability to share images");

	}

	@Override
	public void addPublicShare(String providerImageId) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException("No ability to make images public");

	}

	@Override
	public String bundleVirtualMachine(String virtualMachineId,
			MachineImageFormat format, String bucket, String name)
					throws CloudException, InternalException {
		throw new OperationNotSupportedException("Bundling of virtual machines not supported");
	}

	@Override
	public void bundleVirtualMachineAsync(String virtualMachineId,
			MachineImageFormat format, String bucket, String name,
			AsynchronousTask<String> trackingTask) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Bundling of virtual machines not supported");

	}

	@Override
	public MachineImage captureImage(ImageCreateOptions options)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException("Capturing image of virtual machines not supported");
	}

	@Override
	public void captureImageAsync(ImageCreateOptions options,
			AsynchronousTask<MachineImage> taskTracker) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException("Capturing image of virtual machines not supported");

	}

	@Override
	public MachineImage getImage(String providerImageId) throws CloudException,
	InternalException {

		GoogleMethod method = new GoogleMethod(provider);
		JSONArray list = method.get(GoogleMethod.IMAGE  + "/" + providerImageId);
		if( list == null ) {
			return null;
		}

		for( int i=0; i<list.length(); i++ ) {
			try {
				MachineImage image = toImage(list.getJSONObject(i));

				if( image != null && image.getProviderMachineImageId().equals(providerImageId) ) {
					return image; 
				}
			}
			catch( JSONException e ) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
				throw new CloudException(e);
			}
		}
		return null;
	}

	private @Nullable MachineImage toImage(JSONObject json) throws CloudException, InternalException {

		if( json == null ) {
			return null;
		}
		MachineImage image = new MachineImage();	

		image.setProviderOwnerId(provider.getContext().getAccountNumber());
		image.setSoftware("");
		image.setProviderRegionId(provider.getContext().getRegionId());
		image.setArchitecture(Architecture.I32);

		image.setType(MachineImageType.STORAGE);
		image.setStorageFormat(MachineImageFormat.RAW);
		image.setCurrentState(MachineImageState.ACTIVE);
		image.setImageClass(ImageClass.MACHINE);
		try {
			if( json.has("name") ) {
				image.setName(json.getString("name"));
				image.setProviderMachineImageId(json.getString("name"));
				image.setPlatform(Platform.guess(json.getString("name")));
			}
			if( json.has("description") ) {
				image.setDescription(json.getString("description"));
			}
			if( json.has("preferredKernel") ) {
				image.setKernelImageId(json.getString("preferredKernel"));
			}

			if( json.has("deprecated") ) {
				JSONObject deprecated = json.getJSONObject("deprecated");
				if (json.has("state")){
					String state = deprecated.getString("state");
					if(state.equals("DEPRECATED") && state.equals("OBSOLETE") && state.equals("DELETED"))
						image.setCurrentState(MachineImageState.DELETED);
				}			
			}	

			if(json.has("creationTimestamp") ) {
				SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
				String value = json.getString("creationTimestamp");
				try {
					image.setCreationTimestamp(fmt.parse(value).getTime());
				} 
				catch( ParseException e ) {
					logger.error(e);
					e.printStackTrace();
					throw new CloudException(e);
				}				
			}

			if( image.getDescription() == null || image.getDescription().equals("") ) {
				image.setDescription(image.getName() +  " (" + image.getArchitecture().toString() + " " + 
						image.getPlatform().toString() + ")");
			}
		}
		catch( JSONException e ) {
			logger.error("Failed to parse JSON from the cloud: " + e.getMessage());
			e.printStackTrace();
			throw new CloudException(e);
		}
		return image;
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
	public Requirement identifyLocalBundlingRequirement()
			throws CloudException, InternalException {
		return Requirement.NONE;
	}

	@Override
	public AsynchronousTask<String> imageVirtualMachine(String vmId,
			String name, String description) throws CloudException,
			InternalException {
		throw new OperationNotSupportedException ("Google does not support capturing images");
	}

	@Override
	public boolean isImageSharedWithPublic(String providerImageId)
			throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		return true;
	}

	@Override
	public Iterable<ResourceStatus> listImageStatus(ImageClass cls)
			throws CloudException, InternalException {
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
	public Iterable<MachineImage> listImages(ImageFilterOptions options)
			throws CloudException, InternalException {
		GoogleMethod method = new GoogleMethod(provider);

		Param param = new Param("filter", options.getRegex());
		JSONArray list = method.get(GoogleMethod.IMAGE, param); 

		ArrayList<MachineImage> images = new ArrayList<MachineImage>();
		for( int i=0; i<list.length(); i++ ) {
			try {
				MachineImage image = toImage(list.getJSONObject(i));

				if( image != null) {
					images.add(image);
				}
			}
			catch( JSONException e ) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
			}
		}
		return images;	
	}

	@Override
	public Iterable<MachineImage> listImages(ImageClass cls)
			throws CloudException, InternalException {
		GoogleMethod method = new GoogleMethod(provider);

		JSONArray list = method.get(GoogleMethod.IMAGE); 

		ArrayList<MachineImage> images = new ArrayList<MachineImage>();
		for( int i=0; i<list.length(); i++ ) {
			try {
				MachineImage image = toImage(list.getJSONObject(i));

				if( image != null  && image.getImageClass().equals(cls)) {
					images.add(image);
				}
			}
			catch( JSONException e ) {
				logger.error("Failed to parse JSON: " + e.getMessage());
				e.printStackTrace();
			}
		}
		return images;	
	}

	@Override
	public Iterable<MachineImage> listImages(ImageClass cls, String ownedBy)
			throws CloudException, InternalException {
		Iterable<MachineImage> images = listImages(cls);
		ArrayList<MachineImage> listImage = new ArrayList<MachineImage>();
		if (ownedBy != null){
			for (MachineImage image : images) 		
				if (image.getProviderOwnerId().equals(ownedBy))
					listImage.add(image);
			return listImage;
		} else return images;
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
		return (Iterable<MachineImage>)listImages(ImageClass.MACHINE);
	}

	@Override
	public Iterable<MachineImage> listMachineImagesOwnedBy(String accountId)
			throws CloudException, InternalException {
		return (Iterable<MachineImage>)listImages(ImageClass.MACHINE, accountId);
	}

	@Override
	public Iterable<String> listShares(String providerImageId)
			throws CloudException, InternalException {
		return Collections.emptyList();
	}

	@Override
	public Iterable<ImageClass> listSupportedImageClasses()
			throws CloudException, InternalException {
		ArrayList<ImageClass> tmp = new ArrayList<ImageClass>();
		tmp.add(ImageClass.MACHINE);
		return tmp;
	}

	@Override
	public Iterable<MachineImageType> listSupportedImageTypes()
			throws CloudException, InternalException {
		ArrayList<MachineImageType> imageType = new ArrayList<MachineImageType>();
		imageType.add(MachineImageType.STORAGE);
		return imageType;
	}

	@Override
	public MachineImage registerImageBundle(ImageCreateOptions options)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException ("Google does not support for bundling images");
	}

	@Override
	public void remove(String providerImageId) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException ("Google does not support deprecating public images");
	}

	@Override
	public void remove(String providerImageId, boolean checkState)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException ("Google does not support deprecating public images");

	}

	@Override
	public void removeAllImageShares(String providerImageId)
			throws CloudException, InternalException {
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

	private @Nonnull Iterable<MachineImage> executeImageSearch(@Nullable String accountNumber, @Nullable String keyword, @Nullable Platform platform, @Nullable Architecture architecture, @Nonnull ImageClass cls) throws CloudException, InternalException {
		// TODO : Google Image not associated with any account info. Need to check.
		List<MachineImage> searchImages  = new ArrayList<MachineImage>();
		Iterable<MachineImage> images = listMachineImages(); 
		for (MachineImage image : images){
			if(accountNumber != null && !image.getProviderOwnerId().equals(accountNumber))
				continue;
			if (keyword != null)
				if (!(image.getName().contains(keyword) || image.getDescription().contains(keyword) || image.getProviderMachineImageId().contains(keyword)))
					continue;
			if (platform != null  && !image.getPlatform().equals(platform))
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
		if( imageClasses == null || imageClasses.length < 1 ) {
			return executeImageSearch(accountNumber, keyword, platform, architecture, ImageClass.MACHINE);
		}
		else if( imageClasses.length == 1 ) {
			return executeImageSearch(accountNumber, keyword, platform, architecture, imageClasses[0]);
		}
		else {
			ArrayList<MachineImage> images = new ArrayList<MachineImage>();

			for( ImageClass cls : imageClasses ) {
				for( MachineImage img : executeImageSearch(accountNumber, keyword, platform, architecture, cls) ) {
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


	private @Nonnull Iterable<MachineImage> executePublicImageSearch(@Nullable String keyword, @Nullable Platform platform, 
			@Nullable Architecture architecture, @Nonnull ImageClass cls) throws CloudException, InternalException {
		List<MachineImage> searchImages  = new ArrayList<MachineImage>();
		Iterable<MachineImage> images = listImages(cls); 
		for (MachineImage image : images){
			if (keyword != null)
				if (!(image.getName().contains(keyword) || image.getDescription().contains(keyword) || image.getProviderMachineImageId().contains(keyword)))
					continue;
			if (platform != null  && !image.getPlatform().equals(platform))
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
		if( imageClasses == null || imageClasses.length < 1 ) {
			return executePublicImageSearch(keyword, platform, architecture, ImageClass.MACHINE);
		}
		else if( imageClasses.length == 1 ) {
			return executePublicImageSearch(keyword, platform, architecture, imageClasses[0]);
		}
		else {
			ArrayList<MachineImage> images = new ArrayList<MachineImage>();

			for( ImageClass cls : imageClasses ) {
				for( MachineImage img : executePublicImageSearch(keyword, platform, architecture, cls) ) {
					images.add(img);
				}
			}
			return images;
		}
	}

	@Override
	public void shareMachineImage(String providerImageId, String withAccountId,
			boolean allow) throws CloudException, InternalException {
		throw new OperationNotSupportedException ("Google does not support sharing images");

	}

	@Override
	public boolean supportsCustomImages() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public boolean supportsDirectImageUpload() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public boolean supportsImageCapture(MachineImageType type)
			throws CloudException, InternalException {
		return false;
	}

	@Override
	public boolean supportsImageSharing() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public boolean supportsImageSharingWithPublic() throws CloudException,
	InternalException {
		return false;
	}

	@Override
	public boolean supportsPublicLibrary(ImageClass cls) throws CloudException,
	InternalException {
		return true;
	}

	@Override
	public void updateTags(String imageId, Tag... tags) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException ("Google image does not have meta data");

	}

	@Override
	public void updateTags(String[] imageIds, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException ("Google image does not have meta data");

	}

	@Override
	public void removeTags(String imageId, Tag... tags) throws CloudException,
	InternalException {
		throw new OperationNotSupportedException ("Google image does not have meta data");

	}

	@Override
	public void removeTags(String[] imageIds, Tag... tags)
			throws CloudException, InternalException {
		throw new OperationNotSupportedException ("Google image does not have meta data");

	}

	@Override
	public Iterable<MachineImage> searchPublicImages(ImageFilterOptions arg0)
			throws InternalException, CloudException {
		String	accountNumber = arg0.getAccountNumber();
		Architecture architecture = arg0.getArchitecture();
		Platform platform = arg0.getPlatform();
		String keyword = null;
		return searchImages(accountNumber, keyword,  platform, architecture) ;
	}

}
