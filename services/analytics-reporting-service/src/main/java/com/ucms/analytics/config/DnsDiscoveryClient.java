package com.ucms.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every running replica of a service this one calls, found through DNS: Docker's embedded
 * DNS answers a service name with one address per replica, so no registry is needed.
 * Locations come from ucms.discovery.services (the *_SERVICE_URI settings); a literal
 * address or "localhost" (local development) is a single instance. The image sets
 * sun.net.inetaddr.ttl=5, so replicas that come and go are noticed within seconds.
 */
@Component
@EnableConfigurationProperties(DnsDiscoveryClient.Services.class)
public class DnsDiscoveryClient implements DiscoveryClient {

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
    public List<ServiceInstance> getInstances(String serviceId) {
        URI location = config.getServices().get(serviceId);
        if (location == null) return List.of();
        String host = location.getHost();
        boolean secure = "https".equals(location.getScheme());
        int port = location.getPort() != -1 ? location.getPort() : (secure ? 443 : 80);
        if ("localhost".equalsIgnoreCase(host) || host.matches("[0-9.]+") || host.contains(":")) {
            return List.of(new DefaultServiceInstance(serviceId + "@" + host, serviceId, host, port, secure));
        }
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            List<InetAddress> v4 = Arrays.stream(all).filter(a -> a instanceof Inet4Address).toList();
            List<ServiceInstance> instances = new ArrayList<>();
            for (InetAddress address : v4.isEmpty() ? Arrays.asList(all) : v4) {
                instances.add(new DefaultServiceInstance(serviceId + "@" + address.getHostAddress(), serviceId,
                        address.getHostAddress(), port, secure));
            }
            return instances;
        } catch (UnknownHostException e) {
            return List.of();
        }
    }

    @Override
    public List<String> getServices() {
        return List.copyOf(config.getServices().keySet());
    }
}
