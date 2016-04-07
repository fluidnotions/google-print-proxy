package com.fluidnotions.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.client.methods.ZeroCopyConsumer;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.springframework.stereotype.Service;

import com.fluidnotions.mock.Debug;
import com.fluidnotions.mock.MockProperties;

@Service
public class Download {

	// private static final Log log = LogFactory.getLog(Download.class);
	private static final String module = Download.class.getName();
	static String mockDownloadTempDir = MockProperties.mockDownloadTempDir;

//	private String getBaseDownloadTempDir() {
//		try {
//			return FlexibleLocation.resolveLocation("component://googlecloudprint/docdownloads/").getFile();
//		} catch (MalformedURLException e) {
//			e.printStackTrace();
//		}
//		return null;
//	}

//	private String getOTUsernameAndPasswordRequestString() {
//		String otusername = UtilProperties.getPropertyValue("googlecloudprint.properties", "dynamicDocReqUsername");
//		String otpassword = UtilProperties.getPropertyValue("googlecloudprint.properties", "dynamicDocReqPassword");
//		return "&USERNAME=" + otusername + "&PASSWORD=" + otpassword;
//	}

	public boolean download(String downloadSrcUrl, String downloadSubFolder, String title, String contentType, String tenantKey) throws IOException {

		CloseableHttpAsyncClient httpclient = HttpAsyncClients.createDefault();
		try {
			httpclient.start();

			//File downloadDir = new File(getBaseDownloadTempDir() + File.separator + downloadSubFolder + File.separator);
			File downloadDir = new File(mockDownloadTempDir + File.separator + downloadSubFolder + File.separator);

			if (!downloadDir.exists()) {
				downloadDir.mkdirs();
			}

			//File download = new File(getBaseDownloadTempDir() + File.separator + downloadSubFolder + File.separator + title);
			File download = new File(mockDownloadTempDir + File.separator + downloadSubFolder + File.separator + title);

			// need to append admin level username and password to the request
			// in order to access the pdf report
			//String url = downloadSrcUrl + getOTUsernameAndPasswordRequestString();
			String url = downloadSrcUrl;
			Debug.logInfo("url: " + url, module);
			final HttpGet httpVerb = new HttpGet(url);
			httpVerb.setHeader("Accept-Encoding", contentType);

			BasicAsyncRequestProducer producer = new BasicAsyncRequestProducer(new HttpHost(httpVerb.getURI().getHost(), httpVerb.getURI().getPort(), httpVerb
					.getURI().getScheme()), httpVerb);

			ZeroCopyConsumer<File> consumer = new ZeroCopyConsumer<File>(download) {

				@Override
				protected File process(final HttpResponse response, final File file, final ContentType contentType) throws Exception {
					Debug.logInfo("dynamic pdf: file process: contentType: " + contentType + ", path: " + file.getPath(), module);
					return file;
				}

			};

			Future<File> future = httpclient.execute(producer, consumer, null);
			future.get();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		} catch (ExecutionException e) {
			e.printStackTrace();
			return false;
		} finally {
			httpclient.close();
		}
		Debug.logInfo("download: Done", module);
		return true;
	}

	public boolean removeTemp(String dir) {
		//File tempDir = new File(getBaseDownloadTempDir() + File.separator + dir);
		File tempDir = new File(mockDownloadTempDir + File.separator + dir);
		try {
			FileUtils.deleteDirectory(tempDir);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public String getFullFilePathForTempFile(String tempFileDirPath) {
		//File tempDir = new File(getBaseDownloadTempDir() + File.separator + tempFileDirPath);
		File tempDir = new File(mockDownloadTempDir + File.separator + tempFileDirPath);
		// there should only ever be 1 in each temp dir
		String fullFilePath = tempDir.listFiles()[0].getAbsolutePath();
		// log.debug("getFullFilePathForTempFile: tempFileDirPath: "+tempFileDirPath+", fullFilePath: "+fullFilePath);
		return fullFilePath;
	}

	/**
	 * Gets (and creates if necessary) a key to be used for an external login
	 * parameter
	 */
//	private static String getExternalLoginKey(GenericValue userLogin) {
//
//		String externalKey = "EL000000000GCP";
//		if (!LoginWorker.externalLoginKeys.containsKey(externalKey)) {
//			LoginWorker.externalLoginKeys.put(externalKey, userLogin);
//		}
//
//		return externalKey;
//
//	}

}
