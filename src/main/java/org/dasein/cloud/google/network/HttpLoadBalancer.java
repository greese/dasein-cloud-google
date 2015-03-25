package org.dasein.cloud.google.network;

import java.util.Locale;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.ci.AbstractConvergedHttpLoadBalancer; 
import org.dasein.cloud.ci.ConvergedHttpLoadBalancer;
import org.dasein.cloud.ci.ConvergedHttpLoadBalancerFilterOptions;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.identity.ServiceAction;

public class HttpLoadBalancer extends AbstractConvergedHttpLoadBalancer<Google> {

    private Google provider;

    public HttpLoadBalancer(Google provider) {
        super(provider);
        this.provider = provider;
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
        // TODO Auto-generated method stub
        return null;
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
    public String getConvergedHttpLoadBalancerId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResourceStatus getCurrentState() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String[] mapServiceAction(ServiceAction action) {
        // TODO Auto-generated method stub
        return null;
    }


}
