package dev.flakyhunter.notification;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import dev.flakyhunter.common.ApiException;

/**
 * Webhook targets must be public HTTPS URLs. Internal addresses are refused so a webhook can't be used to make
 * the server call into private networks (SSRF). Checked when saving and again before every delivery
 * (DNS can change in between).
 */
final class WebhookUrls {

	private WebhookUrls() {
	}

	static URI requirePublicHttps(String url) {
		URI uri;
		try {
			uri = URI.create(url.strip());
		}
		catch (IllegalArgumentException e) {
			throw ApiException.badRequest("That is not a valid URL");
		}
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
			throw ApiException.badRequest("Webhook URLs must start with https://");
		}
		if (uri.getUserInfo() != null) {
			throw ApiException.badRequest("Webhook URLs may not contain a user name or password");
		}
		try {
			for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
				if (isInternal(address)) {
					throw ApiException.badRequest("Webhook URLs must point to a public address");
				}
			}
		}
		catch (UnknownHostException e) {
			throw ApiException.badRequest("Unknown host: " + uri.getHost());
		}
		return uri;
	}

	static boolean isInternal(InetAddress a) {
		if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
				|| a.isMulticastAddress()) {
			return true;
		}
		byte[] b = a.getAddress();
		if (a instanceof Inet6Address) {
			return (b[0] & 0xfe) == 0xfc; // fc00::/7 unique local
		}
		int first = b[0] & 0xff;
		int second = b[1] & 0xff;
		return first == 0 || (first == 100 && second >= 64 && second <= 127) // carrier-grade NAT
				|| (first == 169 && second == 254); // cloud metadata services
	}

}
