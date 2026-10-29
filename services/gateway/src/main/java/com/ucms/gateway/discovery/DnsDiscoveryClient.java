package com.ucms.gateway.discovery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every running replica of a service, found through DNS: Docker's embedded DNS answers a
 * service name with one address per replica, so no registry (Eureka/Consul) is needed.
 * Each service's location comes from the same *_SERVICE_URI settings as before; a literal
 * address or "localhost" (local development) is a single instance.
 *
 * The JVM caches lookups (the image sets sun.net.inetaddr.ttl=5), and the load balancer
 * health-checks what this returns, so a replica that dies is skipped within seconds.
 */
@Component
@EnableConfigurationProperties(DnsDiscoveryClient.Services.class)
public class DnsDiscoveryClient implements ReactiveDiscoveryClient {

    @ConfigurationProperties("ucms.discovery")
    public static class Services {
        private Map<String, URI> services = new LinkedHashMap<>();
        public Map<String, URI> getServices() { return services; }
        public void setServices(Map<String, URI> services) { this.services = services; }
    }

    private final Services config;

    public DnsDiscoveryClient(Services config) {
        this.config = config;
    }

    @Override
    public String description() {
        return "UCMS DNS discovery";
    }

    @Override
    public Flux<ServiceInstance> getInstances(String serviceId) {
        URI location = config.getServices().get(serviceId);
        if (location == null) return Flux.empty();
        return Flux.defer(() -> Flux.fromIterable(resolve(serviceId, location)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<String> getServices() {
        return Flux.fromIterable(config.getServices().keySet());
    }

    static List<ServiceInstance> resolve(String serviceId, URI location) {
        String host = location.getHost();
        boolean secure = "https".equals(location.getScheme());
        int port = location.getPort() != -1 ? location.getPort() : (secure ? 443 : 80);
        if (isLiteralOrLocal(host)) {
            return List.of(new DefaultServiceInstance(serviceId + "@" + host, serviceId, host, port, secure));
        }
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            List<InetAddress> v4 = Arrays.stream(all).filter(a -> a instanceof Inet4Address).toList();
            return (v4.isEmpty() ? Arrays.asList(all) : v4).stream()
                    .map(a -> (ServiceInstance) new DefaultServiceInstance(
                            serviceId + "@" + a.getHostAddress(), serviceId, a.getHostAddress(), port, secure))
                    .toList();
        } catch (UnknownHostException e) {
            return List.of();
        }
    }

    private static boolean isLiteralOrLocal(String host) {
        return "localhost".equalsIgnoreCase(host) || host.matches("[0-9.]+") || host.contains(":");
    }
}
