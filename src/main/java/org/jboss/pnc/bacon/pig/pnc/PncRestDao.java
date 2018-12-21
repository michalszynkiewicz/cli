/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.bacon.pig.pnc;

import lombok.experimental.Delegate;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.message.BasicNameValuePair;
import org.jboss.pnc.dto.BuildConfiguration;
import org.jboss.pnc.dto.SCMRepository;
import org.jboss.pnc.rest.api.endpoints.BuildConfigurationEndpoint;
import org.jboss.pnc.rest.api.endpoints.GroupConfigurationEndpoint;
import org.jboss.prod.generator.pnc.PncBuildConfig;
import org.jboss.prod.generator.pnc.model.PncMilestone;
import org.jboss.prod.generator.pnc.model.PncProduct;
import org.jboss.prod.generator.pnc.model.PncProductVersion;
import org.jboss.prod.generator.pnc.model.PncProject;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * for playing with the api to discover the options use: http://orch.cloud.pnc.engineering.redhat.com/pnc-web/apidocs/
 *
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 12/5/18
 */
public class PncRestDao {
    // TODO replace endpoint usage with Matej's client usage
    private BuildConfigurationEndpoint configEndpoint;
    private GroupConfigurationEndpoint configGroupEndpoint;


    public Optional<BuildConfiguration> getBuildConfig(Integer id) { // 120
        BuildConfiguration content = configEndpoint.getSpecific(id).getContent();
        return Optional.ofNullable(content);
    }

    public Collection<BuildConfiguration> listBuildConfigsInGroup(Integer groupId) {
        return configGroupEndpoint.getConfigurations(groupId, null).getContent();
    }

    @SuppressWarnings("rawtypes")
    public void markMilestoneCurrent(Integer versionId, Integer milestoneId) {
        String versionUrl = productVersion(versionId);
        Map versionAsMap = getProductVersionAsMap(versionId);
        //noinspection unchecked
        versionAsMap.put("currentProductMilestoneId", milestoneId);

        HttpPut put = new HttpPut(versionUrl);
        HttpEntity entity = EntityBuilder.create()
                .setText(client.toJson(versionAsMap))
                .build();
        put.setEntity(entity);
        client.executeAuthenticatedRequest(put);
    }

    protected Map<?, ?> getProductVersionAsMap(Integer versionId) {
        String versionUrl = productVersion(versionId);
        HttpGet get = new HttpGet(versionUrl);
        HttpEntity responseEntity = client.executeRequest(get).getEntity();

        return client.unwrap(responseEntity, Map.class);
    }

    public Optional<Integer> getMilestoneIdForVersionAndName(Integer versionId, String milestoneName) {
        List<Map> result = client.getFromAllPages(
                milestonesForVersion(versionId),
                Map.class,
                pair("q", "version==" + milestoneName));

        return result.size() > 0
                ? Optional.of((Integer) result.iterator().next().get("id"))
                : Optional.empty();
    }

    public PncMilestone createMilestone(PncMilestone milestone) {
        String json = client.toJson(milestone);
        HttpPost post = new HttpPost(urls.milestones());
        HttpEntity entity = EntityBuilder.create().setText(json).build();
        post.setEntity(entity);
        HttpResponse response = client.executeAuthenticatedRequest(post);
        return client.unwrap(response.getEntity(), PncMilestone.class);
    }

    public Optional<PncProduct> getProductByName(String name) {
        return getOptionalMatch(urls.products(), "name==" + name, PncProduct.class);
    }

    public Optional<PncProductVersion> getProductVersion(int productId, String majorMinor) {
        return getOptionalMatch(urls.versionsForProduct(productId), "version==" + majorMinor, PncProductVersion.class);
    }

    public Optional<PncProject> getProjectByName(String name) {
        return getOptionalMatch(urls.projects(), "name==" + name, PncProject.class);
    }

    private <T> Optional<T> getOptionalMatch(String url, String query, Class<T> resultClass) {
        List<T> products = client.getFromAllPages(url, resultClass, pair("q", query));
        switch (products.size()) {
            case 0:
                return Optional.empty();
            case 1:
                return Optional.of(products.iterator().next());
            default:
                throw new RuntimeException("Expected at most one match for url: " + url + " and query: " + query + ", got " + products.size());
        }
    }

    public Optional<PncRepositoryConfiguration> getRepositoryConfigurationByScmUri(String shortScmURIPath) {
        return null;  // TODO: Customise this generated block
    }

    private NameValuePair pair(String key, String value) {
        return new BasicNameValuePair(key, value);
    }

    /**
     * @deprecated for test usage only
     */
    @Deprecated
    PncRestClient getClient() {
        return client;
    }

    public Optional<SCMRepository> getRepositoryConfigurationForBuildConfigByScmUri(String shortScmURIPath) {
        return null;  // TODO: Customise this generated block
    }
}
