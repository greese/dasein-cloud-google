package org.dasein.cloud.google.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Tag;
import org.dasein.cloud.ci.AbstractConvergedHttpLoadBalancer;
import org.dasein.cloud.ci.ConvergedHttpLoadBalancer;
import org.dasein.cloud.ci.ConvergedHttpLoadBalancerFilterOptions;
import org.dasein.cloud.ci.ConvergedHttpLoadbalancerOptions;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.GoogleMethod;
import org.dasein.cloud.google.GoogleOperationType;
import org.dasein.cloud.identity.ServiceAction;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Backend;
import com.google.api.services.compute.model.BackendService;
import com.google.api.services.compute.model.ForwardingRule;
import com.google.api.services.compute.model.HostRule;
import com.google.api.services.compute.model.HttpHealthCheck;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.PathMatcher;
import com.google.api.services.compute.model.PathRule;
import com.google.api.services.compute.model.TargetHttpProxy;
import com.google.api.services.compute.model.TargetHttpProxyList;
import com.google.api.services.compute.model.UrlMap;
import com.google.api.services.compute.model.UrlMapList;

public class HttpLoadBalancer extends AbstractConvergedHttpLoadBalancer<Google> {

    private Google provider;
    private ProviderContext ctx;

    public HttpLoadBalancer(Google provider) {
        super(provider);
        this.provider = provider;

        ctx = provider.getContext(); 
    }

    @Override
    public String getProviderTermForConvergedHttpLoadBalancer(Locale locale) {
        return "HTTP load balancer";
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        return true; // TODO punt!
    }

    @Override
    public Iterable<ConvergedHttpLoadBalancer> listConvergedHttpLoadBalancers(ConvergedHttpLoadBalancerFilterOptions options) throws CloudException, InternalException {
        List<ConvergedHttpLoadBalancer> httpLoadBalancers = new ArrayList<ConvergedHttpLoadBalancer>();

        Compute gce = provider.getGoogleCompute();

        try {
            TargetHttpProxyList result = gce.targetHttpProxies().list(ctx.getAccountNumber()).execute();
            if (null != result) {
                List<TargetHttpProxy> targetHttpProxies = result.getItems();
                if (null != targetHttpProxies) {
                    for (TargetHttpProxy targetHttpProxy: targetHttpProxies) {
                        httpLoadBalancers.add(toConvergedHttpLoadBalancer(targetHttpProxy));
                    }
                }
            }
        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred listing convergedHttpLoadBalancers " + ex.getMessage());
        }

        return httpLoadBalancers;
    }

    public ConvergedHttpLoadBalancer toConvergedHttpLoadBalancer(TargetHttpProxy targetHttpProxy) {
        return ConvergedHttpLoadBalancer.getInstance(targetHttpProxy.getId(),
                targetHttpProxy.getName(),
                targetHttpProxy.getDescription(),
                targetHttpProxy.getCreationTimestamp(),
                targetHttpProxy.getUrlMap()
                ).withSelfLink(targetHttpProxy.getSelfLink());
    }

    @Override
    public void updateTags(String convergedHttpLoadbalancerId, Tag... tags) throws CloudException, InternalException {
        // TODO Auto-generated method stub
    }

    @Override
    public void updateTags(String[] convergedHttpLoadbalancerIds, Tag... tags) throws CloudException, InternalException {
        // TODO Auto-generated method stub
    }

    @Override
    public void removeTags(String convergedHttpLoadbalancerId, Tag... tags) throws CloudException, InternalException {
        // TODO Auto-generated method stub
    }

    @Override
    public void removeTags(String[] convergedHttpLoadbalancerIds, Tag... tags) throws CloudException, InternalException {
        // TODO Auto-generated method stub
    }

    @Override
    public String[] mapServiceAction(ServiceAction action) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void removeConvergedHttpLoadBalancers(String[] convergedHttpLoadBalancerIds) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();

        GoogleMethod method = new GoogleMethod(provider);
        try {
            Operation job;
            for (String targetHttpProxy : convergedHttpLoadBalancerIds) {
                job = gce.targetHttpProxies().delete(ctx.getAccountNumber(), targetHttpProxy).execute();

                method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
            }

        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred listing convergedHttpLoadBalancers " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing Converged Http Load Balancer " + ex.getMessage());
        }
    }

    public String createBackendService(@Nonnull String name, String description, int portNumber, String portName, String [] healthCheckLink) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();
        GoogleMethod method = new GoogleMethod(provider);
        // creates backend service - WORKS //
        BackendService beContent = new BackendService();
        beContent.setName(name);
        beContent.setDescription(description);
        beContent.setPort(portNumber);
        beContent.setPortName(portName);
        List<Backend> backends = new ArrayList<Backend>();
        Backend backend = new Backend();
        backend.setGroup("https://www.googleapis.com/resourceviews/v1beta2/projects/qa-project-2/zones/europe-west1-b/resourceViews/instance-group-1");
        backends.add(backend);
        beContent.setBackends(backends);
        List<String> healthChecks = new ArrayList<String>();
        healthChecks.add("https://www.googleapis.com/compute/v1/projects/qa-project-2/global/httpHealthChecks/default-health-check");
        beContent.setHealthChecks(Arrays.asList(healthCheckLink));

        try {
            Operation foo = gce.backendServices().insert(ctx.getAccountNumber(), beContent ).execute();
            method.getOperationComplete(provider.getContext(), foo, GoogleOperationType.GLOBAL_OPERATION, null, null);
        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred listing convergedHttpLoadBalancers " + ex.getMessage());
        } catch ( Exception ex ) {
            throw new CloudException("Error removing Converged Http Load Balancer " + ex.getMessage());
        }

        return gce.getBaseUrl() + ctx.getAccountNumber() + "/global/backendServices/" + name;
    }

    public String createURLMap(@Nonnull String name, String description, String defaultBackendServiceUrl) throws CloudException, InternalException  {
        Compute gce = provider.getGoogleCompute();
        GoogleMethod method = new GoogleMethod(provider);

        try {

            PathRule pathRule = new PathRule();
            List<String> paths = new ArrayList<String>();
            paths.add("/*");
            //paths.add("/videos");
            //paths.add("/videos/*");
            pathRule.setPaths(paths);
            pathRule.setService("https://www.googleapis.com/compute/v1/projects/qa-project-2/global/backendServices/bob");
            List<PathRule> pathRules = new ArrayList<PathRule>();
            pathRules.add(pathRule);

            PathMatcher pathMatcher = new PathMatcher();
                pathMatcher.setDescription("roger");
                pathMatcher.setName("roger");
                pathMatcher.setDefaultService(defaultBackendServiceUrl);
                pathMatcher.setPathRules(pathRules );
            List<PathMatcher> pathMatchers = new ArrayList<PathMatcher>();
            pathMatchers.add(pathMatcher);

            UrlMap urlMap = new UrlMap();
            urlMap.setName(name);
            urlMap.setDescription(description);
            urlMap.setPathMatchers(pathMatchers);
            List<HostRule> hostRules = new ArrayList<HostRule>();
            HostRule hostRule = new HostRule();
            hostRule.setPathMatcher("roger");

            List<String> hosts = new ArrayList<String>();
            hosts.add("*");
            hostRule.setHosts(hosts);
            hostRules.add(hostRule);
            urlMap.setHostRules(hostRules );

            urlMap.setDefaultService(defaultBackendServiceUrl);


            Operation job = gce.urlMaps().insert(ctx.getAccountNumber(), urlMap ).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("XX An error occurred listing convergedHttpLoadBalancers " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing Converged Http Load Balancer " + ex.getMessage());
        }
        return gce.getBaseUrl() + ctx.getAccountNumber() + "/global/urlMaps/" + name;
    }

    public String createTargetProxy(@Nonnull String name, String description, String urlMapSelfUrl) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();

        GoogleMethod method = new GoogleMethod(provider);
        try {
            TargetHttpProxy content = new TargetHttpProxy();
            content.setName(name); //withConvergedHttpLoadBalancerOptions.getName());
            content.setDescription(description); //withConvergedHttpLoadBalancerOptions.getDescription());
            content.setUrlMap(urlMapSelfUrl); // "https://www.googleapis.com/compute/v1/projects/qa-project-2/global/urlMaps/wizzard-lb"); //withConvergedHttpLoadBalancerOptions.getUrlMap());
            Operation job = gce.targetHttpProxies().insert(ctx.getAccountNumber(), content ).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("XX An error occurred listing convergedHttpLoadBalancers " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing Converged Http Load Balancer " + ex.getMessage());
        }

        return gce.getBaseUrl() + ctx.getAccountNumber() + "/global/targetHttpProxies/" + name;
    }

    @Override
    public String createConvergedHttpLoadBalancer(@Nonnull ConvergedHttpLoadbalancerOptions withConvergedHttpLoadBalancerOptions) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();
        GoogleMethod method = new GoogleMethod(provider);

        try {
            HttpHealthCheck httpHealthCheck = new HttpHealthCheck();
            httpHealthCheck.setName("default-health-check");
            httpHealthCheck.setDescription("default-health-check");
            httpHealthCheck.setCheckIntervalSec(5);
            httpHealthCheck.setHealthyThreshold(2);
            httpHealthCheck.setUnhealthyThreshold(2);
            httpHealthCheck.setTimeoutSec(5);
            //httpHealthCheck.setHost(""); // optional i think
            httpHealthCheck.setPort(80);
            httpHealthCheck.setRequestPath("/");
            Operation job = gce.httpHealthChecks().insert(ctx.getAccountNumber(), httpHealthCheck).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);

            String backendServiceUrl = createBackendService("bob", "bob", 80, "bob", new String[] {"https://www.googleapis.com/compute/v1/projects/qa-project-2/global/httpHealthChecks/default-health-check"});

            String urlMapSelfUrl = createURLMap("roger-url-map", "roger-url-map", backendServiceUrl);

            String targetProxy = createTargetProxy("bob", "bob", urlMapSelfUrl);

            ForwardingRule gfwContent = new ForwardingRule();
            gfwContent.setName("bobfr");
            gfwContent.setDescription("bobfr");
            gfwContent.setPortRange("80");  // 80 or 8080
            //gfwContent.setIPAddress(iPAddress);
            gfwContent.setTarget(targetProxy);
            //Operation 
            job = gce.globalForwardingRules().insert(ctx.getAccountNumber(), gfwContent ).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);

        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("XX An error occurred listing convergedHttpLoadBalancers " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing Converged Http Load Balancer " + ex.getMessage());
        }
        return null;
    }
}
