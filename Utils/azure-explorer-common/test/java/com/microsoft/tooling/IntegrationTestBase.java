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

package com.microsoft.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.powermock.core.classloader.annotations.PowerMockIgnore;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.microsoft.azure.AzureResponseBuilder;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.serializer.AzureJacksonAdapter;
import com.microsoft.azuretools.telemetry.IntegrationTestInterceptor;
import com.microsoft.rest.LogLevel;
import com.microsoft.rest.RestClient;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public abstract class IntegrationTestBase {
    private static final String GLOBAL_ENDPOINT = "https://management.azure.com";
    private static final String MOCK_HOST = "localhost";
    private static final String MOCK_PORT = String.format("3%03d", (int) (Math.random() * Math.random() * 1000));
    private static final String MOCK_URI = "http://" + MOCK_HOST + ":" + MOCK_PORT;
    private final static String RECORD_FOLDER = "records/";
    protected final static String MOCK_SUBSCRIPTION = "00000000-0000-0000-0000-000000000000";

    public static Boolean IS_MOCKED = IsMocked();

    private Map<String, String> textReplacementRules = new HashMap<String, String>();
    private String currentTestName = null;

    protected WireMock wireMock;

    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(options().port(Integer.parseInt(MOCK_PORT)));
    @Rule
    public WireMockRule instanceRule = wireMockRule;

    protected TestRecord testRecord;

    public Interceptor interceptor;

    private String azureAuthFile = "c:\\config.azureauth";

    @Rule
    public TestName name = new TestName();

    public void setUpStep() throws Exception {
        if (currentTestName == null) {
            currentTestName = name.getMethodName();
        } else {
            throw new Exception("Setting up another test in middle of a test");
        }

        addTextReplacementRule(GLOBAL_ENDPOINT, MOCK_URI + "/");

        int retries = 10;
        boolean created = false;
        while (retries > 0) {
            retries--;
            try {
                wireMock = new WireMock(MOCK_HOST, wireMockRule.port());
                wireMock.resetMappings();
                created = true;
                break;
            } catch (Exception e) {
                Thread.sleep(3000);
            }
        }

        if (!created) {
            throw new Exception("Cannot create workMock instance");
        }
        ApplicationTokenCredentials credentials = new TestCredentials();
        String defaultSubscription = "";
        if (IS_MOCKED) {
            defaultSubscription = MOCK_SUBSCRIPTION;
            File recordFile = getRecordFile();
            ObjectMapper mapper = new ObjectMapper();
            try {
                testRecord = mapper.readValue(recordFile, TestRecord.class);
            } catch (Exception e) {
                throw new Exception("Fail read test record: " + e.getMessage());
            }

        } else {
            // TODO: add non mock case

            credentials = ApplicationTokenCredentials.fromFile(new File(azureAuthFile));
            defaultSubscription = credentials.defaultSubscriptionId();

        }

        interceptor = new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                if (IS_MOCKED) {
                    return registerRecordedResponse(chain);
                }

                // TODO: non mock case
                throw new IOException("Non mocked case not supported currently");
            }
        };

        RestClient restClient = createRestClient(credentials);
        initialize(restClient, defaultSubscription, credentials.domain());
    }

    public void cleanupStep() throws Exception {
        resetTest(name.getMethodName());
    }

    protected void resetTest(String testName) throws Exception {
        if (!currentTestName.equals(testName)) {
            return;
        }

        wireMock.resetMappings();
        testRecord = null;
        currentTestName = null;
    }

    private RestClient createRestClient(ApplicationTokenCredentials credentials) throws Exception {
        RestClient restClient;

        if (IS_MOCKED) {
            credentials = new TestCredentials();
            restClient = new RestClient.Builder().withBaseUrl(MOCK_URI + "/")
                    .withSerializerAdapter(new AzureJacksonAdapter())
                    .withResponseBuilderFactory(new AzureResponseBuilder.Factory()).withCredentials(credentials)
                    .withLogLevel(LogLevel.BODY_AND_HEADERS).withNetworkInterceptor(interceptor).build();
            return restClient;
        } else {
            // TODO: non mock case
            restClient = new RestClient.Builder().withBaseUrl(GLOBAL_ENDPOINT)
                    .withSerializerAdapter(new AzureJacksonAdapter())
                    .withResponseBuilderFactory(new AzureResponseBuilder.Factory()).withCredentials(credentials)
                    .withLogLevel(LogLevel.BODY_AND_HEADERS).withInterceptor(new IntegrationTestInterceptor()).build();
            return restClient;
            // throw new Exception("Non mocked case not supported currently");
        }

    }

    private synchronized Response registerRecordedResponse(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();
        url = applyRegex(url);
        try {
            synchronized (testRecord.networkCallRecords) {
                registerStub(request, url);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return chain.proceed(chain.request());
    }

    private String removeMockHost(String url) {
        url = url.replace("http://" + MOCK_HOST + ":", "");
        url = url.substring(url.indexOf("/"));

        return url;
    }

    private void registerStub(Request request, String url) throws Exception {
        int index = 0;
        String requestMethod = request.method();
        url = removeMockHost(url);
        // TODO: map body later to get the request
        for (NetworkCallRecord record : testRecord.networkCallRecords) {
            if (requestMethod.equalsIgnoreCase(record.Method) && url.equalsIgnoreCase(removeMockHost(record.Uri))) {
                break;
            }
            index++;
        }

        if (index >= testRecord.networkCallRecords.size()) {
            System.out.println("NOT FOUND - " + requestMethod + " " + url);
            System.out.println("Remaining records " + testRecord.networkCallRecords.size());
            return;
        }

        NetworkCallRecord networkCallRecord = testRecord.networkCallRecords.remove(index);
        String recordUrl = removeMockHost(networkCallRecord.Uri);

        UrlPattern urlPattern = urlEqualTo(recordUrl);
        String method = networkCallRecord.Method;
        MappingBuilder mBuilder;
        if (method.equals("GET")) {
            mBuilder = get(urlPattern);
        } else if (method.equals("POST")) {
            mBuilder = post(urlPattern);
        } else if (method.equals("PUT")) {
            mBuilder = put(urlPattern);
        } else if (method.equals("DELETE")) {
            mBuilder = delete(urlPattern);
        } else if (method.equals("PATCH")) {
            mBuilder = patch(urlPattern);
        } else if (method.equals("HEAD")) {
            mBuilder = head(urlPattern);
        } else {
            throw new Exception("Invalid HTTP method.");
        }

        ResponseDefinitionBuilder rBuilder = aResponse()
                .withStatus(Integer.parseInt(networkCallRecord.Response.get("StatusCode")));
        for (Entry<String, String> header : networkCallRecord.Response.entrySet()) {
            if (!header.getKey().equals("StatusCode") && !header.getKey().equals("Body")
                    && !header.getKey().equals("Content-Length")) {
                String rawHeader = header.getValue();
                for (Entry<String, String> rule : textReplacementRules.entrySet()) {
                    if (rule.getValue() != null) {
                        rawHeader = rawHeader.replaceAll(rule.getKey(), rule.getValue());
                    }
                }
                rBuilder.withHeader(header.getKey(), rawHeader);
            }
        }

        String rawBody = networkCallRecord.Response.get("Body");
        if (rawBody != null) {
            for (Entry<String, String> rule : textReplacementRules.entrySet()) {
                if (rule.getValue() != null) {
                    rawBody = rawBody.replaceAll(rule.getKey(), rule.getValue());
                }
            }
            rBuilder.withBody(rawBody);
            rBuilder.withHeader("Content-Length", String.valueOf(rawBody.getBytes("UTF-8").length));
        }

        mBuilder.willReturn(rBuilder);
        wireMock.register(mBuilder);
    }

    protected void addTextReplacementRule(String regex, String replacement) {
        textReplacementRules.put(regex, replacement);
    }

    private String applyRegex(String text) {
        for (Entry<String, String> rule : textReplacementRules.entrySet()) {
            if (rule.getValue() != null) {
                text = text.replaceAll(rule.getKey(), rule.getValue());
            }
        }
        return text;
    }

    private static Boolean IsMocked() {
        // TODO: support un mocked mode

        return true;
    }

    private File getRecordFile() {
        URL folderUrl = IntegrationTestBase.class.getClassLoader().getResource(".");
        File folderFile = new File(folderUrl.getPath() + RECORD_FOLDER);
        if (!folderFile.exists())
            folderFile.mkdir();
        String filePath = folderFile.getPath() + "/" + currentTestName + ".json";
        return new File(filePath);
    }

    public static StringValuePattern equalTo(String value) {
        return new EqualToPattern(value);
    }

    public static UrlPattern urlEqualTo(String testUrl) {
        return new UrlPattern(equalTo(testUrl), false);
    }

    protected abstract void initialize(RestClient restClient, String defaultSubscription, String domain)
            throws Exception;

}
