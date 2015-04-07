package org.dasein.cloud.google.network;

import java.io.IOException;
import java.util.ArrayList;
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
import com.google.api.services.compute.model.BackendService;
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

    @Override
    public String createConvergedHttpLoadBalancer(@Nonnull ConvergedHttpLoadbalancerOptions withConvergedHttpLoadBalancerOptions) throws CloudException, InternalException {
        Compute gce = provider.getGoogleCompute();
        UrlMapList result ;
        GoogleMethod method = new GoogleMethod(provider);
        try {
            TargetHttpProxy content = new TargetHttpProxy();
            
/*
                  "message" : "Invalid value for field 'resource.defaultService': ''.  Must be the URL to a Compute resource of the correct type",
                  "message" : "Invalid value for field 'resource.pathMatcher[0].defaultService': 'https://www.googleapis.com/compute/beta/projects/qa-project-2/global/backendServices/roger-bar'.  Must be the URL to a Compute resource of the correct type",
                  "message" : "Invalid value for field 'resource.pathMatcher[0].pathRule[0].backendService': 'https://www.googleapis.com/compute/beta/projects/qa-project-2/global/backendServices/roger-bar'.  Must be the URL to a Compute resource of the correct type",
                "message" : "Invalid value for field 'resource.defaultService': ''.  Must be the URL to a Compute resource of the correct type"
*/
            
            content.setName(withConvergedHttpLoadBalancerOptions.getName());
            content.setDescription(withConvergedHttpLoadBalancerOptions.getDescription());
            content.setUrlMap(withConvergedHttpLoadBalancerOptions.getUrlMap());
            
            /* creates backend service  WORKS //
            BackendService beContent = new BackendService();
            beContent.setDescription("bob");
            beContent.setName("bob");
            beContent.setPort(80);
            beContent.setPortName("bob");
            List<String> healthChecks = new ArrayList<String>();
            healthChecks.add("https://www.googleapis.com/compute/v1/projects/qa-project-2/global/httpHealthChecks/default-health-check");
            beContent.setHealthChecks(healthChecks);
            Operation foo = gce.backendServices().insert(ctx.getAccountNumber(), beContent ).execute();
            method.getOperationComplete(provider.getContext(), foo, GoogleOperationType.GLOBAL_OPERATION, null, null);
            
             what is backend Service self link? */


            String defaultBackendService = "https://www.googleapis.com/compute/beta/projects/qa-project-2/global/backendServices/bob";





            PathRule ee = new PathRule();
            List<String> paths = new ArrayList<String>();
                paths.add("/videos");
                paths.add("/videos/*");
            ee.setPaths(paths);
            ee.setService("https://www.googleapis.com/compute/beta/projects/qa-project-2/global/backendServices/dsfgdfg-backend-service");

            List<PathRule> pathRules = new ArrayList<PathRule>();
            pathRules.add(ee );

            PathMatcher e = new PathMatcher();
                e.setDescription("roger");
                e.setName("roger");
                e.setDefaultService(defaultBackendService);
                e.setPathRules(pathRules );

            List<PathMatcher> pathMatchers = new ArrayList<PathMatcher>();
            pathMatchers.add(e);
            
            UrlMap urlMap = new UrlMap();
                urlMap.setDescription("roger");
                urlMap.setName("roger");
                urlMap.setDefaultService("https://www.googleapis.com/compute/v1/projects/qa-project-2/global/backendServices/dsfgdfg-backend-service");
                //urlMap.setPathMatchers(pathMatchers );
            
            result = gce.urlMaps().list(ctx.getAccountNumber()).execute();    
            Operation job = gce.urlMaps().insert(ctx.getAccountNumber(), urlMap ).execute();
            method.getOperationComplete(provider.getContext(), job, GoogleOperationType.GLOBAL_OPERATION, null, null);
            
            
            ////UrlMapList result = gce.urlMaps().list(ctx.getAccountNumber()).execute();
            
            
            job = gce.targetHttpProxies().insert(ctx.getAccountNumber(), content ).execute();
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
        return null;
    }
}
