package com.fluidnotions.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import th.co.geniustree.google.cloudprint.api.exception.CloudPrintException;
import th.co.geniustree.google.cloudprint.api.model.SubmitJob;
import th.co.geniustree.google.cloudprint.api.model.response.JobResponse;
import th.co.geniustree.google.cloudprint.api.model.response.PrinterInformationResponse;
import th.co.geniustree.google.cloudprint.api.model.response.SearchPrinterResponse;
import th.co.geniustree.google.cloudprint.api.model.response.SubmitJobResponse;
import th.co.geniustree.google.cloudprint.api.util.ResponseUtils;

import com.fluidnotions.controllers.MainController;
import com.fluidnotions.mock.Debug;
import com.fluidnotions.mock.MockGoogleCredentialFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.gson.Gson;

@SuppressWarnings("deprecation")
@Service
public class GCPServiceClient {

	private static final String module = GCPServiceClient.class.getName();
	private static final Log log = LogFactory.getLog(MainController.class);
	private static final String CLOUD_PRINT_URL = "https://www.google.com/cloudprint";
	private static Gson gson = new Gson();

	public SubmitJobResponse submitJob(SubmitJob submitJob, String username) throws CloudPrintException {
		ByteArrayInputStream byteArrayInputStream = null;
		InputStream inputStream = null;
		String response = "";
		try {
			byteArrayInputStream = new ByteArrayInputStream(submitJob.getContent());

			
			InputStreamBody inputStreamBody = new InputStreamBody(byteArrayInputStream, submitJob.getContentType(), submitJob.getTitle());

			MultipartEntity entity = new MultipartEntity();
			entity.addPart("content", inputStreamBody);
			entity.addPart("contentType", new StringBody(submitJob.getContentType()));
			entity.addPart("title", new StringBody(submitJob.getTitle()));
			entity.addPart("ticket", new StringBody(submitJob.getTicketJSON()));

			log.debug("TicketJSON: " + submitJob.getTicketJSON());

			if (submitJob.getTag() != null) {
				for (String tag : submitJob.getTag()) {
					entity.addPart("tag", new StringBody(tag));
				}
			}
			response = openConnection("/submit?output=json&printerid=" + submitJob.getPrinterId(), entity, username);
			Debug.logInfo("submitJob: " + response, module);
		} catch (Exception e) {
			e.printStackTrace();
			throw new CloudPrintException(e);
		} finally {
			if (byteArrayInputStream != null) {
				try {
					byteArrayInputStream.close();
				} catch (IOException ex) {
					throw new CloudPrintException(ex);
				}
			}

			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException ex) {
					throw new CloudPrintException(ex);
				}
			}
		}
		return gson.fromJson(new StringReader(response), SubmitJobResponse.class);
	}

	public PrinterInformationResponse getPrinterInformation(String printerId, String username) throws CloudPrintException {
		
		PrinterInformationResponse pir = null;
		try {
			 String response = openConnection("/printer?output=json&use_cdd=true&printerid=" + printerId, username);
			 Debug.logInfo("getPrinterInformation: " + response, module);
			 pir = gson.fromJson(new StringReader(response), PrinterInformationResponse.class);
		}  catch (Exception e) {
			e.printStackTrace();
			throw new CloudPrintException(e);
			
		}
		return pir;
	}

	public SearchPrinterResponse searchPrinters(String username) throws CloudPrintException {
		// String response = openConnection("/search?output=json&use_cdd=true");
		SearchPrinterResponse spr = null;
		try {
			String response = openConnection("/search?output=json", username);
			Debug.logInfo("searchPrinters: " + response, module);	
			if(response.contains("User credentials required")) throw new CloudPrintException("User credentials required");
			spr = gson.fromJson(new StringReader(response), SearchPrinterResponse.class);
		} catch (Exception e) {
			e.printStackTrace();
			throw new CloudPrintException(e);
			
		} 
		return spr;
	}

	public JobResponse getJobs(String username) throws CloudPrintException {
		JobResponse jr = null;
		try {
			String response = openConnection("/jobs?output=json&sortorder=CREATE_TIME_DESC&owner=" + username, username);
			jr = gson.fromJson(new StringReader(response), JobResponse.class);
		} catch (Exception e) {	
			e.printStackTrace();
			throw new CloudPrintException(e);
			
		} 
		return jr;
	}

	private String openConnection(String serviceAndParameters, String username) throws Exception {
		return openConnection(serviceAndParameters, null, username);
	}

	
	private String openConnection(String serviceAndParameters, MultipartEntity entity, String username) throws CloudPrintException {
		GoogleCredential credential = null;
		try {
			credential = MockGoogleCredentialFactory.retrieveCredential(username);
		} catch (Exception e) {
			e.printStackTrace();
			throw new CloudPrintException(e);
		}
		
		String response = "";
		HttpPost httpPost = null;
		InputStream inputStream = null;
		HttpClient httpClient = null;
		try {
			String request = CLOUD_PRINT_URL + serviceAndParameters;
			Debug.logInfo("openConnection: request: " + request, module);
			httpClient = new DefaultHttpClient();
			httpPost = new HttpPost(request);
			httpPost.setHeader("Authorization", "Bearer " + credential.getAccessToken());

			if (entity != null) {
				httpPost.setEntity(entity);
			}

			HttpResponse httpResponse = httpClient.execute(httpPost);
			inputStream = httpResponse.getEntity().getContent();
			response = ResponseUtils.streamToString(inputStream);
		} catch (Exception ex) {
			throw new CloudPrintException(ex);
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException ex) {
					throw new CloudPrintException(ex);
				}
			}

			if (httpPost != null && !httpPost.isAborted()) {
				httpPost.abort();
			}
			

		}
		return response;
	}

}
