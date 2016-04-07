package com.fluidnotions.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import th.co.geniustree.google.cloudprint.api.exception.CloudPrintException;
import th.co.geniustree.google.cloudprint.api.model.SubmitJob;
import th.co.geniustree.google.cloudprint.api.model.Ticket;
import th.co.geniustree.google.cloudprint.api.model.response.JobResponse;
import th.co.geniustree.google.cloudprint.api.model.response.PrinterInformationResponse;
import th.co.geniustree.google.cloudprint.api.model.response.SearchPrinterResponse;
import th.co.geniustree.google.cloudprint.api.model.response.SubmitJobResponse;

import com.fluidnotions.mock.Debug;
import com.fluidnotions.service.Download;
import com.fluidnotions.service.GCPServiceClient;
import com.google.gson.Gson;

@RestController
public class MainController {

	private static final String module = MainController.class.getName();
	private static final Log log = LogFactory.getLog(MainController.class);
	private static Gson gson = new Gson();

	@Autowired
	private Download downloadService;

	@Autowired
	private GCPServiceClient cloudPrint;

	@RequestMapping("/printers")
	public SearchPrinterResponse searchPrinters(@RequestParam("username") String username, @RequestParam("tenantKey") String tenantKey) {
		Debug.logInfo("searchPrinters called", module);
		SearchPrinterResponse response = new SearchPrinterResponse();
		try {
			response = cloudPrint.searchPrinters(username);
		} catch (CloudPrintException e) {
			e.printStackTrace();
			response.setSuccess(false);
			response.setMessage(e.getMessage());
		}
		return response;
	}

	@RequestMapping("/printer")
	public PrinterInformationResponse getPrinterInformation(@RequestParam("username") String username, @RequestParam("tenantKey") String tenantKey,
			@RequestParam("printerId") String printerId) {
		Debug.logInfo("getPrinterInformation called", module);
		PrinterInformationResponse response = new PrinterInformationResponse();
		try {
			response = cloudPrint.getPrinterInformation(printerId, username);
		} catch (CloudPrintException e) {
			e.printStackTrace();
			response.setSuccess(false);
			response.setMessage(e.getMessage());
		}

		// if (!response.isSuccess()) {
		// log.debug("response message: " + response.getMessage());
		//
		// }
		// for (Printer printer : response.getPrinters()) {
		// log.debug("printer information response: " + printer);
		// }

		return response;
	}

	@RequestMapping("/print")
	public SubmitJobResponse submitJob(@RequestParam("username") String username, @RequestParam("tenantKey") String tenantKey,
			@RequestParam("printerId") String printerId, @RequestParam("title") String title, @RequestParam("contentType") String contentType,
			@RequestParam("tempFileDirPath") String tempFileDirPath, @RequestParam("ticket") String ticketJsonString) {
		Debug.logInfo("submitJob called", module);
		String msg = "";
		Debug.logInfo("ticketJsonString: " + ticketJsonString, module);
		Ticket ticket = gson.fromJson(ticketJsonString, Ticket.class);

		// byte[] content = Base64.decodeBase64(b64);
		byte[] content = null;
		try {
			content = IOUtils.toByteArray(new FileInputStream(downloadService.getFullFilePathForTempFile(tempFileDirPath)));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			msg += e.getMessage()+";";
		} catch (IOException e) {
			e.printStackTrace();
			msg += e.getMessage()+";";
		}
		SubmitJob submitJob = new SubmitJob();
		submitJob.setContent(content);
		submitJob.setContentType(contentType);
		submitJob.setPrinterId(printerId);
		// submitJob.setTag(Arrays.asList("koalar", "hippo", "cloud"));
		submitJob.setTicket(ticket);
		submitJob.setTitle(title);
		SubmitJobResponse response = new SubmitJobResponse();
		try {
			response = cloudPrint.submitJob(submitJob, username);
		} catch (CloudPrintException e) {
			e.printStackTrace();
			msg += e.getMessage()+";";
			response.setSuccess(false);
			response.setMessage(msg);
		}
		if (response.isSuccess()) {
			try {
				Debug.logInfo("submit job id: " + response.getJob().getId(), module);
			} catch (Exception e) {
				e.printStackTrace();
				
			}
		}else{
			Debug.logInfo("submit job response: " + response.isSuccess() + "," + response.getMessage(), module);
		}

		return response;
	}

	@RequestMapping("/jobqueue")
	public JobResponse refreshJobQueue(@RequestParam("username") String username) {
		//Debug.logInfo("refreshJobQueue called", module);
		JobResponse response = new JobResponse();
		try {
			response = cloudPrint.getJobs(username);
		} catch (CloudPrintException e) {
			e.printStackTrace();
			response.setSuccess(false);
			response.setMessage(e.getMessage());
		}
		return response;
	}

	@RequestMapping("/downloadtmpdoc")
	public DownloadTempResponse downloadTempFile(@RequestParam("username") String username, @RequestParam("tenantKey") String tenantKey,
			@RequestParam("url") String url, @RequestParam(value = "title", defaultValue = "temp") String title,
			@RequestParam(value = "contentType", defaultValue = "*") String contentType) {
		Debug.logInfo("downloadTempFile called", module);
		String msg = null;
		String ts = new Long(new Date().getTime()).toString();
		Debug.logInfo("ts sub dir: " + ts, module);
		String dst = tenantKey + File.separator + username + File.separator + ts;

		try {
			downloadService.download(url, dst, title, contentType, tenantKey);
		} catch (IOException e) {
			e.printStackTrace();
			msg = e.getMessage();
		}
		DownloadTempResponse resp = new DownloadTempResponse(dst);
		
		return resp;
	}

	class DownloadTempResponse {
		String dst;

		public DownloadTempResponse(String dst) {
			super();
			this.dst = dst;
		}

		public String getDst() {
			return dst;
		}

		public void setDst(String dst) {
			this.dst = dst;
		}

	}

	@RequestMapping("/deltemp")
	public void deleteTempFile(@RequestParam("tempFileDirPath") String tempFileDirPath) {
		Debug.logInfo("removeTemp: " + tempFileDirPath, module);
		downloadService.removeTemp(tempFileDirPath);
	}

}