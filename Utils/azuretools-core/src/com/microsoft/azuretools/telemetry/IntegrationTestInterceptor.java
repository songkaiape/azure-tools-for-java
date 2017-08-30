package com.microsoft.azuretools.telemetry;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.Util;
import okio.Buffer;
import okio.BufferedSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

public class IntegrationTestInterceptor implements Interceptor {

	private static final String Record_Folder = "records/";
	static SimpleDateFormat dateformat=new SimpleDateFormat("yyyy-MM-dd-HH-mm");
	
	private static final String Record_File = dateformat.format(new Date());
	private static final String Record_Path = "c:/";
	private static final String Record_FilePath = Record_Path + Record_Folder + Record_File + ".json";
	static final String StatusCode = "StatusCode";
	static final String Body = "Body";

	private static final String GLOBAL_ENDPOINT = "https://management.azure.com";
	protected final static String MOCK_SUBSCRIPTION = "00000000-0000-0000-0000-000000000000";
	private static final String MOCK_HOST = "localhost";
	private static final String MOCK_PORT = String.format("3%03d", (int) (Math.random() * Math.random() * 1000));
	private static final String MOCK_URI = "http://" + MOCK_HOST + ":" + MOCK_PORT;
	private TestRecord testrecord = new TestRecord();

	@Override
	public Response intercept(Chain chain) throws IOException {
		final Request request = chain.request();

		File recordFile = new File(Record_FilePath);
		if (!recordFile.exists()) {
			File folderFile = new File(Record_Path + Record_Folder);
			if (!folderFile.exists())
				folderFile.mkdir();
			recordFile.createNewFile();

		}

		NetworkCallRecord record = new NetworkCallRecord();
		synchronized (this) {
			if (!request.headers().get("x-ms-logging-context").contains("poll")) {
				record.Method = request.method();
				record.Uri = replaceUrl(request.url().toString());
				record.Headers = new HashMap<String, String>();
				getHeader(request.headers(), record.Headers);

				final Response response = chain.proceed(request);
				ResponseBody responseBody = response.body();
				BufferedSource responseBuffer = responseBody.source();
				responseBuffer.request(9223372036854775807L);
				Buffer buffer = responseBuffer.buffer().clone();
				String responseBodyString = null;

				if (response.header("Content-Encoding") == null) {
					responseBodyString = new String(buffer.readString(Util.UTF_8));
				} else if (response.header("Content-Encoding").equalsIgnoreCase("gzip")) {
					GZIPInputStream gis = new GZIPInputStream(buffer.inputStream());
					responseBodyString = IOUtils.toString(gis);
				}

				record.Response = new HashMap<String, String>();
				record.Response.put(Body, replaceUrl(responseBodyString));
				record.Response.put(StatusCode, Integer.toString(response.code()));
				getHeader(response.headers(), record.Response);
				testrecord.networkCallRecords.add(record);
				ObjectMapper mapper = new ObjectMapper();

				try {

					FileOutputStream out = new FileOutputStream(Record_FilePath);
					mapper.writeValue(out, testrecord);

				} catch (IOException e) {
					e.printStackTrace();
				}

				Response newResponse = response.newBuilder()
						.body(ResponseBody.create(responseBody.contentType(), responseBodyString.getBytes())).build();
				return newResponse;
			} else {
				return chain.proceed(request);
			}
		}
	}

	public void getHeader(Headers h, Map<String, String> m) {
		Set<String> headerKeys = h.names();
		for (String s : headerKeys) {
			m.put(s, h.get(s));
		}

	}

	public String replaceUrl(String url) {
		String regex = "(?<=subscriptions\\/).+?(?=\\/)";
		url = url.replaceAll(regex, MOCK_SUBSCRIPTION);
		url = url.replaceAll(GLOBAL_ENDPOINT, MOCK_URI);
		return url;

	}

}