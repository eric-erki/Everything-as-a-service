package org.platformlayer.ops.networks;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsTarget;
import org.platformlayer.ops.packages.AsBlock;

import com.google.common.base.Objects;

public class NetworkPoint {
	final String privateNetworkId;
	final InetAddress address;

	// For now, we assume there's one private network
	public static final String PRIVATE_NETWORK_ID = "private";

	public NetworkPoint(String privateNetworkId, InetAddress address) {
		this.privateNetworkId = privateNetworkId;
		this.address = address;
	}

	public static String getMyNetworkKey() {
		// We assume we're on the private network
		return PRIVATE_NETWORK_ID;
	}

	public static NetworkPoint forSameNetwork(InetAddress address) {
		return new NetworkPoint(getMyNetworkKey(), address);
	}

	public static NetworkPoint forMe() {
		// TODO: This is probably not the address we really want...
		InetAddress myAddress;
		try {
			myAddress = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			throw new IllegalStateException("Cannot get my IP", e);
		}
		return forSameNetwork(myAddress);
	}

	public static NetworkPoint forTarget(OpsTarget target) {
		return target.getNetworkPoint();
	}

	public static NetworkPoint forPublicInternet() {
		return new NetworkPoint(null, null);
	}

	public static NetworkPoint forPublicHostname(String hostname) throws OpsException {
		InetAddress address;
		try {
			address = InetAddress.getByName(hostname);
		} catch (UnknownHostException e) {
			throw new OpsException("Error resolving hostname", e);
		}
		return new NetworkPoint(null, address);
	}

	public static NetworkPoint forTargetInContext() {
		OpsTarget target = OpsContext.get().getInstance(OpsTarget.class);

		return forTarget(target);
	}

	public String getPrivateNetworkId() {
		return privateNetworkId;
	}

	public InetAddress getAddress() {
		return address;
	}

	public boolean isPublicInternet() {
		return privateNetworkId == null;
	}

	public boolean isPrivateNetwork() {
		return this.privateNetworkId != null;
	}

	public static NetworkPoint forNetwork(String network) {
		return new NetworkPoint(network, null);
	}

	@Override
	public String toString() {
		return "NetworkPoint [privateNetworkId=" + privateNetworkId + ", address=" + address.getHostAddress() + "]";
	}

	public static int estimateDistance(NetworkPoint a, NetworkPoint b) {
		if (a.equals(b)) {
			return 0;
		}

		if (!Objects.equal(a.getPrivateNetworkId(), b.getPrivateNetworkId())) {
			// We need to download from A and then upload to B, so d(A, Me) + d(Me, B)
			// TODO: This is a poor metric. Our metric isn't really rich enough here
			return 8;
		}

		AsBlock asA = AsBlock.find(a.getAddress());
		AsBlock asB = AsBlock.find(b.getAddress());

		if (asA.equals(asB)) {
			return 1;
		}

		if (Objects.equal(asA.getCountry(), asB.getCountry())) {
			return 2;
		}

		return 4;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((address == null) ? 0 : address.hashCode());
		result = prime * result + ((privateNetworkId == null) ? 0 : privateNetworkId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		NetworkPoint other = (NetworkPoint) obj;
		if (address == null) {
			if (other.address != null) {
				return false;
			}
		} else if (!address.equals(other.address)) {
			return false;
		}
		if (privateNetworkId == null) {
			if (other.privateNetworkId != null) {
				return false;
			}
		} else if (!privateNetworkId.equals(other.privateNetworkId)) {
			return false;
		}
		return true;
	}

}
