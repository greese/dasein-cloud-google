package org.dasein.cloud.google.util.model;

import com.google.api.services.compute.model.MachineType;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.util.uom.storage.Storage;

/**
 * @author igoonich
 * @since 15.12.2013
 */
public final class GoogleMachineTypes {

	public static VirtualMachineProduct toDaseinVmProduct(MachineType googleMachineType) {
		VirtualMachineProduct product = new VirtualMachineProduct();

		product.setName(googleMachineType.getName());
		product.setProviderProductId(googleMachineType.getName());
		product.setCpuCount(googleMachineType.getGuestCpus());
		product.setRamSize(new Storage(googleMachineType.getMemoryMb(), Storage.MEGABYTE));
		product.setDescription(googleMachineType.getDescription() + " [" + googleMachineType.getZone() + "]");


		// TODO: Check if the json output from the server has ephemeralDisks & the diskGb is 10
//		product.setRootVolumeSize(new Storage<Gigabyte>(10, Storage.GIGABYTE));

		return product;
	}

}
