/*
 * Copyright (c) Microsoft Corporation
 * <p/>
 * All rights reserved.
 * <p/>
 * MIT License
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.microsoft.tooling.msservices.serviceexplorer.azure.rediscache;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.mockito.Mock;

import com.google.common.collect.ImmutableList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.redis.RedisAccessKeys;
import com.microsoft.azure.management.redis.RedisCache;
import com.microsoft.azure.management.redis.Sku;
import com.microsoft.azuretools.authmanage.AuthMethodManager;
import com.microsoft.azuretools.authmanage.SubscriptionManager;
import com.microsoft.azuretools.authmanage.models.SubscriptionDetail;
import com.microsoft.azuretools.azurecommons.helpers.RedisCacheUtil;
import com.microsoft.azuretools.azurecommons.rediscacheprocessors.ProcessingStrategy;
import com.microsoft.azuretools.core.mvp.model.AzureMvpModelHelper;
import com.microsoft.azuretools.sdkmanage.AzureManager;
import com.microsoft.rest.RestClient;

import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.microsoft.tooling.IntegrationTestBase;
import com.microsoft.tooling.msservices.components.DefaultLoader;
import com.microsoft.tooling.msservices.helpers.UIHelper;
import com.microsoft.tooling.msservices.helpers.collections.ObservableList;
import com.microsoft.tooling.msservices.serviceexplorer.Node;
import com.microsoft.tooling.msservices.serviceexplorer.NodeActionListener;
import junit.framework.Assert;

@PowerMockIgnore("javax.net.ssl.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest({ AuthMethodManager.class, AzureManager.class, SubscriptionManager.class, DefaultLoader.class })

public class RedisCacheNonMock extends IntegrationTestBase {

    @Mock
    private AuthMethodManager authMethodManagerMock;

    @Mock
    private AzureManager azureManagerMock;

    @Mock
    private SubscriptionManager subscriptionManagerMock;

    @Mock
    private UIHelper uiHelper;

    private Azure azure;

    private RedisCacheModule redisModule;

    private String defaultSubscription;

    private SubscriptionDetail currentsub;

    private final LinkedHashMap<String, String> skus = RedisCacheUtil.initSkus();
    private final LinkedHashMap<String, String> priceTier = initPriceTier();
    private String redisCacheQueryString;
    
    RedisCreateConfig BasicNewResGrpConfig = new RedisCreateConfig("MonaC1BasicNew", "East US", "MonaC1BasicNewRg",
            priceTier.get("BASIC1"), false, true);
    RedisCreateConfig BasicNewResGrpConfigNonSsl = new RedisCreateConfig("MonaC2BasicNewNonSsl", "East US",
            "MonaC2BasicNewNonSslRg", priceTier.get("BASIC2"), true, true);
    RedisCreateConfig BasicExistedResGrpConfig = new RedisCreateConfig("MonaC3BasicExist", "East US", "MonaExist",
            priceTier.get("BASIC3"), false, false);
    RedisCreateConfig BasicExistedResGrpConfigNonSsl = new RedisCreateConfig("MonaC4BasicExistNonSsl", "East US",
            "MonaExist", priceTier.get("BASIC4"), true, false);

    RedisCreateConfig StdNewResGrpConfig = new RedisCreateConfig("MonaC3StdNew", "East US", "MonaC3StdNewRg",
            priceTier.get("STD3"), false, true);
    RedisCreateConfig StdNewResGrpConfigNonSsl = new RedisCreateConfig("MonaC4StdNewNonSsl", "East US",
            "MonaC4StdNewNonSslRg", priceTier.get("STD4"), true, true);
    RedisCreateConfig StdExistedResGrpConfig = new RedisCreateConfig("MonaC5StdExist", "East US", "MonaExist",
            priceTier.get("STD5"), false, false);
    RedisCreateConfig StdExistedResGrpConfigNonSsl = new RedisCreateConfig("MonaC6StdExistNonSsl", "East US",
            "MonaExist", priceTier.get("STD6"), true, false);

    RedisCreateConfig PremNewResGrpConfig = new RedisCreateConfig("MonaP1New", "East US", "MonaP1NewRg",
            priceTier.get("PREMIUM1"), false, true);
    RedisCreateConfig PremNewResGrpConfigNonSsl = new RedisCreateConfig("MonaP2NewNonSsl", "East US",
            "MonaP2NewNonSslRg", priceTier.get("PREMIUM2"), true, true);
    RedisCreateConfig PremExistedResGrpConfig = new RedisCreateConfig("MonaP3Exist", "East US", "MonaExist",
            priceTier.get("PREMIUM3"), false, false);
    RedisCreateConfig PremExistedResGrpConfigNonSsl = new RedisCreateConfig("MonaP4ExistNonSsl", "East US", "MonaExist",
            priceTier.get("PREMIUM4"), true, false);

    @Before
    public void setUp() throws Exception {
        IntegrationTestBase.IS_MOCKED=false;
        setUpStep();
    }

    // basic redisCache test case
    @Test
    public void testRedisCacheCreateBasicWithNewResGrp() throws Exception {
        createRedisTest(redisModule, BasicNewResGrpConfig);
    }

    @Test
    public void testRedisCacheCreateBasicWithNewResGrpNonSsl() throws Exception {
        createRedisTest(redisModule, BasicNewResGrpConfigNonSsl);
    }

    @Test
    public void testRedisCacheCreateBasicWithExistedResGrp() throws Exception {
        createRedisTest(redisModule, BasicExistedResGrpConfig);
    }

    @Test
    public void testRedisCacheCreateBasicWithExistedResGrpNonSsl() throws Exception {
        createRedisTest(redisModule, BasicExistedResGrpConfigNonSsl);
    }

    // Standard redisCache test case
    @Test
    public void testRedisCacheCreateStdWithNewResGrp() throws Exception {
        createRedisTest(redisModule, StdNewResGrpConfig);
    }

    @Test
    public void testRedisCacheCreateStdWithNewResGrpNonSsl() throws Exception {
        createRedisTest(redisModule, StdNewResGrpConfigNonSsl);
    }

    @Test
    public void testRedisCacheCreateStdWithExistedResGrp() throws Exception {
        createRedisTest(redisModule, StdExistedResGrpConfig);
    }

    @Test
    public void testRedisCacheCreateStdWithExistedResGrpNonSsl() throws Exception {
        createRedisTest(redisModule, StdExistedResGrpConfigNonSsl);
    }

    // Premium redisCache test case
    @Test
    public void testRedisCacheCreatePremWithNewResGrp() throws Exception {
        createRedisTest(redisModule, PremNewResGrpConfig);
    }

    @Test
    public void testRedisCacheCreatePremWithNewResGrpNonSsl() throws Exception {
        createRedisTest(redisModule, PremNewResGrpConfigNonSsl);
    }

    @Test
    public void testRedisCacheCreatePremWithExistedResGrp() throws Exception {
        createRedisTest(redisModule, PremExistedResGrpConfig);
    }

    @Test
    public void testRedisCacheCreatePremWithExistedResGrpNonSsl() throws Exception {
        createRedisTest(redisModule, PremExistedResGrpConfigNonSsl);
    }

    @After
    public void tearDown() throws Exception {
        resetTest(name.getMethodName());
        setUp();
    }

    @Override
    protected void initialize(RestClient restClient, String defaultSubscription, String domain) throws Exception {
        Azure.Authenticated azureAuthed = Azure.authenticate(restClient, defaultSubscription, domain);
        azure = azureAuthed.withSubscription(defaultSubscription);
        this.defaultSubscription = defaultSubscription;
        Set<String> sidList = Stream.of(defaultSubscription).collect(Collectors.toSet());

        PowerMockito.mockStatic(AuthMethodManager.class);
        when(AuthMethodManager.getInstance()).thenReturn(authMethodManagerMock);
        when(authMethodManagerMock.getAzureManager()).thenReturn(azureManagerMock);
        when(azureManagerMock.getAzure(anyString())).thenReturn(azure);
        when(azureManagerMock.getSubscriptionManager()).thenReturn(subscriptionManagerMock);
        when(subscriptionManagerMock.getAccountSidList()).thenReturn(sidList);

        PowerMockito.mockStatic(DefaultLoader.class);
        when(DefaultLoader.getUIHelper()).thenReturn(uiHelper);
        when(uiHelper.isDarkTheme()).thenReturn(false);
        //currentsub=subscriptionManagerMock.getSubscriptionDetails().get(0);
        currentsub = new SubscriptionDetail(defaultSubscription, defaultSubscription, defaultSubscription, true);
        redisCacheQueryString = "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Cache/Redis/%s";
        redisModule = new RedisCacheModule(null) {
            protected void loadActions() {
            }
        };

        Node.setNode2Actions(new HashMap<Class<? extends Node>, ImmutableList<Class<? extends NodeActionListener>>>());
    }

    public LinkedHashMap<String, String> initPriceTier() {
        final LinkedHashMap<String, String> Tier = new LinkedHashMap<String, String>();
        for (String key : skus.keySet()) {
            Tier.put(skus.get(key), key);
        }
        return Tier;
    }

    public void createRedisTest(RedisCacheModule redisModule, RedisCreateConfig config) throws Exception {
        redisModule.refreshItems();
        //assertEquals(redisModule.getChildNodes().size(), 0);
        CreateRedisCache(azureManagerMock, currentsub, config);
        redisModule.removeAllChildNodes();
        redisModule.refreshItems();
       // assertEquals(redisModule.getChildNodes().size(), 1);
       //assertEquals(redisModule.getChildNodes().get(0).getName(), config.dnsNameValue);
        
        //verify redisCache properties
        //String id="/subscriptions/ffa52f27-be12-4cad-b1ea-c2c241b6cceb/resourceGroups/MonaExist/providers/Microsoft.Cache/Redis/MonaC4BasicExistNonSsl";
        //RedisCache redisCacheInstance2 = AzureMvpModelHelper.getInstance().getRedisCache(defaultSubscription,
        //        id);
        String redisCacheId = String.format(redisCacheQueryString, defaultSubscription,config.selectedResGrpValue,config.dnsNameValue);
        RedisCache redisCacheInstance = AzureMvpModelHelper.getInstance().getRedisCache(defaultSubscription,
                redisCacheId);
        
        assertEquals(redisCacheInstance.name(), config.dnsNameValue);
        assertEquals(redisCacheInstance.resourceGroupName(), config.selectedResGrpValue);
        assertEquals(redisCacheInstance.regionName(), config.selectedLocationValue);
        assertEquals(redisCacheInstance.nonSslPort(), config.noSSLPort);

        Sku skuVal = redisCacheInstance.sku();
        String tier = skus.get(config.selectedPriceTierValue).replace("STD", "STANDARD");
        assertEquals(skuVal.name().toString().toUpperCase() + Integer.toString(skuVal.capacity()), tier);
        
        RedisAccessKeys accessKeys=redisCacheInstance.getKeys();
        assertNotNull(accessKeys.primaryKey());
        assertNotNull(accessKeys.secondaryKey());
        
        redisModule.refreshItems();
        ObservableList<Node> nodes = redisModule.getChildNodes();
        RedisCacheNode redisCacheNode=null;
        for(Node node:nodes) {
            if (node.getName().equals(config.dnsNameValue)) {
                redisCacheNode = (RedisCacheNode) node;
            }
        }
        if(redisCacheNode==null) {
            throw new Exception("can't find Node"+config.dnsNameValue);
        }
        
        redisModule.removeNode(this.defaultSubscription, redisCacheNode.getResourceId(), redisCacheNode);
        //azure.storageAccounts().deleteByResourceGroup(config.selectedResGrpValue,config.dnsNameValue);
        Thread.sleep(500);
    }

    public void CreateRedisCache(AzureManager azureManager, SubscriptionDetail currentSub, RedisCreateConfig config)
            throws Exception {
        Azure azure = azureManager.getAzure(currentSub.getSubscriptionId());
        ProcessingStrategy processor = RedisCacheUtil.doGetProcessor(azure, skus, config.dnsNameValue,
                config.selectedLocationValue, config.selectedResGrpValue, config.selectedPriceTierValue,
                config.noSSLPort, config.newResGrp);
        try {
            processor.process();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class RedisCreateConfig {
        public RedisCreateConfig(String dnsNameValue, String selectedLocationValue, String selectedResGrpValue,
                String selectedPriceTierValue, boolean noSSLPort, boolean newResGrp) {
            this.dnsNameValue = dnsNameValue;
            this.selectedLocationValue = selectedLocationValue;
            this.selectedResGrpValue = selectedResGrpValue;
            this.selectedPriceTierValue = selectedPriceTierValue;
            this.noSSLPort = noSSLPort;
            this.newResGrp = newResGrp;
        }

        public String dnsNameValue;
        public String selectedResGrpValue;
        public String selectedLocationValue;
        public String selectedPriceTierValue;
        public boolean noSSLPort;
        public boolean newResGrp;
    }

}
