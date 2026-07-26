package com.monitoring.sentinel.agent.network;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Minimal in-cluster client for the Kubernetes API server - bearer token + service account
 * CA, the standard config every pod gets mounted automatically
 * (https://kubernetes.io/docs/tasks/run-application/access-api-from-pod/). No
 * kubeconfig/exec-plugin support: KubernetesAdapter is meant to run as a DaemonSet pod
 * (architecture doc, section 3.2), which always has the token/CA files this class reads.
 *
 * Built on java.net.http.HttpClient (already used by PushClient, and already enabled for the
 * native-image build - see agent/pom.xml) rather than a hand-rolled client like
 * UnixSocketHttpClient: this is regular HTTPS-over-TCP, not a unix socket, so the JDK client
 * handles it directly.
 *
 * NOT TESTED against a live cluster (none available while building this) - the auth flow and
 * endpoints match the documented Kubernetes API, but this needs real verification before
 * being trusted, same caveat as DockerAdapter.
 */
public final class K8sApiClient {

	private static final Path TOKEN_FILE = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");
	private static final Path CA_FILE = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt");

	private final HttpClient httpClient;
	private final String baseUrl;

	public K8sApiClient() throws IOException {
		this.baseUrl = "https://" + requireEnv("KUBERNETES_SERVICE_HOST") + ":" + requireEnv("KUBERNETES_SERVICE_PORT");
		try {
			this.httpClient = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(5))
					.sslContext(trustingClusterCa())
					.build();
		} catch (GeneralSecurityException e) {
			throw new IOException("Failed to build a TLS context trusting the cluster CA (" + CA_FILE + ")", e);
		}
	}

	private static String requireEnv(String name) throws IOException {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IOException(name + " is not set - not running as an in-cluster pod?");
		}
		return value;
	}

	private SSLContext trustingClusterCa() throws IOException, GeneralSecurityException {
		X509Certificate ca;
		try (var in = Files.newInputStream(CA_FILE)) {
			ca = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
		}
		KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
		trustStore.load(null, null);
		trustStore.setCertificateEntry("kube-ca", ca);

		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(trustStore);

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
		return sslContext;
	}

	/** GET a path off the API server root (e.g. "/api/v1/pods"), bearer-authenticated.
	 * Throws on any non-2xx response. */
	public String get(String path) throws IOException {
		HttpResponse<String> response = send(path);
		if (response.statusCode() / 100 != 2) {
			throw new IOException("Kubernetes API returned HTTP " + response.statusCode() + " for " + path);
		}
		return response.body();
	}

	/** Same as {@link #get}, but a 404 is treated as "not present" rather than an error -
	 * used for the metrics-server call, whose absence is an expected, commonly-hit case (see
	 * KubernetesAdapter class doc: metrics-server isn't always installed). */
	public Optional<String> getOptional(String path) throws IOException {
		HttpResponse<String> response = send(path);
		if (response.statusCode() == 404) {
			return Optional.empty();
		}
		if (response.statusCode() / 100 != 2) {
			throw new IOException("Kubernetes API returned HTTP " + response.statusCode() + " for " + path);
		}
		return Optional.of(response.body());
	}

	private HttpResponse<String> send(String path) throws IOException {
		// Re-read fresh on every call rather than caching at construction: Kubernetes
		// rotates the mounted token periodically (~hourly by default), and this client is
		// held for the lifetime of the agent process.
		String token = Files.readString(TOKEN_FILE, StandardCharsets.UTF_8).strip();
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + path))
					.timeout(Duration.ofSeconds(10))
					.header("Authorization", "Bearer " + token)
					.header("Accept", "application/json")
					.GET()
					.build();
			return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted calling the Kubernetes API", e);
		}
	}
}
