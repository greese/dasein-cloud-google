package org.dasein.cloud.google.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
import org.dasein.cloud.ci.HttpPort;
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
import com.google.api.services.compute.model.ForwardingRuleList;
import com.google.api.services.compute.model.HostRule;
import com.google.api.services.compute.model.HttpHealthCheck;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.PathMatcher;
import com.google.api.services.compute.model.PathRule;
import com.google.api.services.compute.model.TargetHttpProxy;
import com.google.api.services.compute.model.TargetHttpProxyList;
import com.google.api.services.compute.model.UrlMap;
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
            ForwardingRuleList result = gce.globalForwardingRules().list(ctx.getAccountNumber()).execute();
            if (null != result) {
                List<ForwardingRule> targetHttpProxies = result.getItems();
                if (null != targetHttpProxies) {
                    for (ForwardingRule targetHttpProxy: targetHttpProxies) {
                        httpLoadBalancers.add(toConvergedHttpLoadBalancer(targetHttpProxy.getName()));
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


    public ConvergedHttpLoadBalancer toConvergedHttpLoadBalancer(@Nonnull String globalForwardingRuleName) throws CloudException, InternalException {
        
        Compute gce = provider.getGoogleCompute();

        
        ConvergedHttpLoadBalancer convergedHttpLoadBalancer = ConvergedHttpLoadBalancer.getInstance();

        try {
            ForwardingRule globalForwardingRule = gce.globalForwardingRules().get(ctx.getAccountNumber(), globalForwardingRuleName.replaceAll(".*/", "")).execute();
            
            convergedHttpLoadBalancer = convergedHttpLoadBalancer.withGlobalForwardingRule(globalForwardingRule.getName(), globalForwardingRule.getDescription(), globalForwardingRule.getCreationTimestamp(), globalForwardingRule.getIPAddress(), globalForwardingRule.getIPProtocol(), globalForwardingRule.getPortRange(), globalForwardingRule.getRegion(), globalForwardingRule.getSelfLink(), globalForwardingRule.getTarget());
            TargetHttpProxy targetHttpProxy = gce.targetHttpProxies().get(ctx.getAccountNumber(), globalForwardingRule.getTarget().replaceAll(".*/", "")).execute();
            UrlMap urlMap = gce.urlMaps().get(ctx.getAccountNumber(), targetHttpProxy.getUrlMap().replaceAll(".*/", "")).execute();
            BackendService backendService = gce.backendServices().get(ctx.getAccountNumber(), urlMap.getDefaultService().replaceAll(".*/", "")).execute();
            List<String> healthChecks = backendService.getHealthChecks();
            if (null != healthChecks) {
                for (String healthChecksSelfUrl : healthChecks) {
                    HttpHealthCheck healthCheck = gce.httpHealthChecks().get(ctx.getAccountNumber(), healthChecksSelfUrl.replaceAll(".*/", "")).execute();
                }
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
        return null;
    }

    // this needs beefing up.
   /*
    public ConvergedHttpLoadBalancer toConvergedHttpLoadBalancer(TargetHttpProxy targetHttpProxy) {
 
        return ConvergedHttpLoadBalancer.getInstance(targetHttpProxy.getId(),
                targetHttpProxy.getName(),
                targetHttpProxy.getDescription(),
                targetHttpProxy.getCreationTimestamp(),
                targetHttpProxy.getUrlMap()
                ).withSelfLink(targetHttpProxy.getSelfLink());
    }
    */

    /*
     * takes either a globalForwardingRule name or url
     */
    public void removeGlobalForwardingRule(@Nonnull String globalForwardingRule) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();

        GoogleMethod method = new GoogleMethod(provider);
        try {
            Operation job = gce.globalForwardingRules().delete(ctx.getAccountNumber(), globalForwardingRule.replaceAll(".*/", "")).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred removing global forwarding rule " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing global forwarding rule " + ex.getMessage());
        }
    }

    /*
     * takes either a targetHttpProxy name or url
     */
    public void removeTargetHttpProxy(@Nonnull String targetHttpProxy) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();

        GoogleMethod method = new GoogleMethod(provider);
        try {
            Operation job = gce.targetHttpProxies().delete(ctx.getAccountNumber(), targetHttpProxy.replaceAll(".*/", "")).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred removing target http proxy " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing target http proxy " + ex.getMessage());
        }
    }

    /*
     * takes either a urlMap name or url
     */
    public void removeUrlMap(@Nonnull String urlMap) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();

        GoogleMethod method = new GoogleMethod(provider);
        try {
            Operation job = gce.urlMaps().delete(ctx.getAccountNumber(), urlMap.replaceAll(".*/", "")).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred removing url map " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing url map " + ex.getMessage());
        }
    }

    /*
     * takes either a backendService name or url
     */
    public void removeBackendService(@Nonnull String backendService) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();

        GoogleMethod method = new GoogleMethod(provider);
        try {
            Operation job = gce.backendServices().delete(ctx.getAccountNumber(), backendService.replaceAll(".*/", "")).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred removing backend service " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing backend service " + ex.getMessage());
        }
    }

    /*
     * takes either a httpHealthCheck name or url
     */
    public void removeHttpHealthCheck(@Nonnull String httpHealthCheck) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();

        GoogleMethod method = new GoogleMethod(provider);
        try {
            Operation job = gce.httpHealthChecks().delete(ctx.getAccountNumber(), httpHealthCheck.replaceAll(".*/", "")).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred removing http health check " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing http health check " + ex.getMessage());
        }
    }

    @Override
    public void removeConvergedHttpLoadBalancers(@Nonnull String urlMap) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();

        urlMap = urlMap.replaceAll(".*/", "");
        try {
            ForwardingRuleList forwardingRuleList = gce.globalForwardingRules().list(ctx.getAccountNumber()).execute();
            TargetHttpProxyList targetHttpProxyList = gce.targetHttpProxies().list(ctx.getAccountNumber()).execute();

            for (TargetHttpProxy targetProxy: targetHttpProxyList.getItems()) {
                if (targetProxy.getUrlMap().endsWith(urlMap)) {
                    for (ForwardingRule forwardingRule: forwardingRuleList.getItems()) {
                        if (forwardingRule.getTarget().endsWith(targetProxy.getName())) {
                            removeGlobalForwardingRule(forwardingRule.getName());
                        }
                    }
                    removeTargetHttpProxy(targetProxy.getName());
                }
            }

            UrlMap um = gce.urlMaps().get(ctx.getAccountNumber(), urlMap).execute(); 

            List<String> backendServices = new ArrayList<String>();
            backendServices.add(um.getDefaultService().replaceAll(".*/", ""));
            List<PathMatcher> pathMatchers = um.getPathMatchers();
            for (PathMatcher pathMatcher: pathMatchers) {
                backendServices.add(pathMatcher.getDefaultService().replaceAll(".*/", ""));
                System.out.println("inspect pathMatcher");
                if (null != pathMatcher.getPathRules()) {
                    for (PathRule pathRule: pathMatcher.getPathRules()) { 
                        backendServices.add(pathRule.getService().replaceAll(".*/", ""));
                    }
                }
            }

            removeUrlMap(um.getName());

            List<String> healthChecks = new ArrayList<String>();
            for (String backendService : new HashSet<String>(backendServices)) { // use HashSet to make it unique list
                BackendService bes = gce.backendServices().get(ctx.getAccountNumber(), backendService).execute();
                removeBackendService(backendService);

                for (String healthCheck : bes.getHealthChecks()) {
                    healthChecks.add(healthCheck);
                }
            }

            for (String healthCheck : new HashSet<String>(healthChecks)) { // use HashSet to make it unique list
                removeHttpHealthCheck(healthCheck);
            }
        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred removing convergedHttpLoadBalancer " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing Converged Http Load Balancer " + ex.getMessage());
        }
    }

    public void createBackendService(ConvergedHttpLoadbalancerOptions withConvergedHttpLoadBalancerOptions) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();
        GoogleMethod method = new GoogleMethod(provider);

        BackendService beContent = new BackendService();
        beContent.setName(withConvergedHttpLoadBalancerOptions.getBackendServiceName());
        beContent.setDescription(withConvergedHttpLoadBalancerOptions.getBackendServiceDescription());
        beContent.setPort(withConvergedHttpLoadBalancerOptions.getBackendServicePortNumber());
        beContent.setPortName(withConvergedHttpLoadBalancerOptions.getBackendServicePortName());
        List<Backend> backends = new ArrayList<Backend>();
        Backend backend = new Backend();
        backend.setGroup("https://www.googleapis.com/resourceviews/v1beta2/projects/qa-project-2/zones/europe-west1-b/resourceViews/instance-group-1");
        backends.add(backend);
        beContent.setBackends(backends);
        beContent.setHealthChecks(Arrays.asList(withConvergedHttpLoadBalancerOptions.getConvergedHttpLoadbalancerSelfUrls()));

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

        withConvergedHttpLoadBalancerOptions.setBackendServiceUrl(gce.getBaseUrl() + ctx.getAccountNumber() + "/global/backendServices/" + withConvergedHttpLoadBalancerOptions.getBackendServiceName());
    }

    public void createURLMap(ConvergedHttpLoadbalancerOptions withConvergedHttpLoadBalancerOptions) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();
        GoogleMethod method = new GoogleMethod(provider);

        try {

            PathRule pathRule = new PathRule();
            List<String> paths = new ArrayList<String>();
            //paths.add("/*");
            paths.add("/videos");
            paths.add("/videos/*");
            pathRule.setPaths(paths);
            pathRule.setService("https://www.googleapis.com/compute/v1/projects/qa-project-2/global/backendServices/roger-bes-name");
            List<PathRule> pathRules = new ArrayList<PathRule>();
            pathRules.add(pathRule);

            PathMatcher pathMatcher = new PathMatcher();
                pathMatcher.setDescription("roger");
                pathMatcher.setName("roger");
                pathMatcher.setDefaultService(withConvergedHttpLoadBalancerOptions.getBackendServiceUrl());
                pathMatcher.setPathRules(pathRules );
            List<PathMatcher> pathMatchers = new ArrayList<PathMatcher>();
            pathMatchers.add(pathMatcher);

            UrlMap urlMap = new UrlMap();
            urlMap.setName(withConvergedHttpLoadBalancerOptions.getUrlMapName());
            urlMap.setDescription(withConvergedHttpLoadBalancerOptions.getUrlMapDescription());
            urlMap.setPathMatchers(pathMatchers);
            List<HostRule> hostRules = new ArrayList<HostRule>();
            HostRule hostRule = new HostRule();
            hostRule.setPathMatcher("roger");

            List<String> hosts = new ArrayList<String>();
            hosts.add("*");
            hostRule.setHosts(hosts);
            hostRules.add(hostRule);   // not optional.
            urlMap.setHostRules(hostRules );

            urlMap.setDefaultService(withConvergedHttpLoadBalancerOptions.getBackendServiceUrl());


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

        withConvergedHttpLoadBalancerOptions.setUrlMapSelfUrl(gce.getBaseUrl() + ctx.getAccountNumber() + "/global/urlMaps/" + withConvergedHttpLoadBalancerOptions.getUrlMapName());
    }

    public void createTargetProxy(ConvergedHttpLoadbalancerOptions withConvergedHttpLoadBalancerOptions) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();
        GoogleMethod method = new GoogleMethod(provider);
        TargetHttpProxy content = new TargetHttpProxy();

        try {
            content.setName(withConvergedHttpLoadBalancerOptions.getTargetProxyName());
            content.setDescription(withConvergedHttpLoadBalancerOptions.getTargetProxyDescription());
            content.setUrlMap(withConvergedHttpLoadBalancerOptions.getUrlMapSelfUrl());
            Operation job = gce.targetHttpProxies().insert(ctx.getAccountNumber(), content ).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
        } catch (IOException ex) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred listing convergedHttpLoadBalancers " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing Converged Http Load Balancer " + ex.getMessage());
        }

        withConvergedHttpLoadBalancerOptions.setTargetProxySelfUrl(gce.getBaseUrl() + ctx.getAccountNumber() + "/global/targetHttpProxies/" + withConvergedHttpLoadBalancerOptions.getTargetProxyName());
    }

    public void createGlobalForwardingRule(ConvergedHttpLoadbalancerOptions withConvergedHttpLoadBalancerOptions) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();
        GoogleMethod method = new GoogleMethod(provider);
        ForwardingRule gfwContent = new ForwardingRule();
        try {
            gfwContent.setName(withConvergedHttpLoadBalancerOptions.getGlobalForwardingRuleName());
            gfwContent.setDescription(withConvergedHttpLoadBalancerOptions.getGlobalForwardingRuleDescription());
            if (withConvergedHttpLoadBalancerOptions.getGlobalForwardingRulePort() == HttpPort.PORT80) {
                gfwContent.setPortRange("80");
            } else if (withConvergedHttpLoadBalancerOptions.getGlobalForwardingRulePort() == HttpPort.PORT8080) {
                gfwContent.setPortRange("8080");
            } 
            if (null != withConvergedHttpLoadBalancerOptions.getGlobalForwardingRuleIpAddress()) {
                gfwContent.setIPAddress(withConvergedHttpLoadBalancerOptions.getGlobalForwardingRuleIpAddress());
            }
            gfwContent.setTarget(withConvergedHttpLoadBalancerOptions.getTargetProxySelfUrl());

            Operation job = gce.globalForwardingRules().insert(ctx.getAccountNumber(), gfwContent ).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
        } catch (IOException ex) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred creating GlobalForwardingRule: " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error occurred creating GlobalForwardingRule: " + ex.getMessage());
        }
        withConvergedHttpLoadBalancerOptions.setGlobalForwardingRuleSelfUrl(gce.getBaseUrl() + ctx.getAccountNumber() + "/global/httpHealthChecks/" + withConvergedHttpLoadBalancerOptions.getGlobalForwardingRuleName());
    }

    @Override
    public String createConvergedHttpLoadBalancer(@Nonnull ConvergedHttpLoadbalancerOptions withConvergedHttpLoadBalancerOptions) throws CloudException, InternalException {
        try {
            Iterator<ConvergedHttpLoadbalancerOptions.HttpHealthCheck> healthCheckIterator = withConvergedHttpLoadBalancerOptions.getHttpHealthChecks();
            while (healthCheckIterator.hasNext()) {
                ConvergedHttpLoadbalancerOptions.HttpHealthCheck httpHealthCheck = healthCheckIterator.next();
                createHttpHealthCheck(httpHealthCheck);
            }

            createBackendService(withConvergedHttpLoadBalancerOptions);

            createURLMap(withConvergedHttpLoadBalancerOptions);

            createTargetProxy(withConvergedHttpLoadBalancerOptions);

            createGlobalForwardingRule(withConvergedHttpLoadBalancerOptions);
 
            return withConvergedHttpLoadBalancerOptions.getGlobalForwardingRuleSelfUrl();
        } catch (Exception ex) {
            throw new CloudException("Error creating Converged Http Load Balancer " + ex.getMessage());
        }
    }

    private void createHttpHealthCheck(ConvergedHttpLoadbalancerOptions.HttpHealthCheck httpHealthCheckOprions) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();

        GoogleMethod method = new GoogleMethod(provider);
        try {
            HttpHealthCheck httpHealthCheck = new HttpHealthCheck();
            httpHealthCheck.setName(httpHealthCheckOprions.getName());
            httpHealthCheck.setDescription(httpHealthCheckOprions.getDescription());
            httpHealthCheck.setCheckIntervalSec(httpHealthCheckOprions.getCheckIntervalSeconds());
            httpHealthCheck.setHealthyThreshold(httpHealthCheckOprions.getHealthyThreshold());
            httpHealthCheck.setUnhealthyThreshold(httpHealthCheckOprions.getUnhealthyTreshold());
            httpHealthCheck.setTimeoutSec(httpHealthCheckOprions.getTimeoutSeconds());
            httpHealthCheck.setHost(httpHealthCheckOprions.getHost()); // optional i think
            httpHealthCheck.setPort(httpHealthCheckOprions.getPort());
            httpHealthCheck.setRequestPath(httpHealthCheckOprions.getRequestPath());
            Operation job = gce.httpHealthChecks().insert(ctx.getAccountNumber(), httpHealthCheck).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
        } catch ( IOException ex ) {
            if (ex.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)ex;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException("An error occurred listing convergedHttpLoadBalancers " + ex.getMessage());
        } catch (Exception ex) {
            throw new CloudException("Error removing Converged Http Load Balancer " + ex.getMessage());
        }
        httpHealthCheckOprions.setHttpHealthCheckSelfUrl(gce.getBaseUrl() + ctx.getAccountNumber() + "/global/httpHealthChecks/" + httpHealthCheckOprions.getName());
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
}
