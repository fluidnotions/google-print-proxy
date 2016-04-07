package com.fluidnotions.controllers;


public class Models {

	public static class ResponseWrapper {

		public final static String SUCCESS = "SUCCESS";
		public final static String ERRROR = "ERRROR";
		private String status;
		private String errorMessage;
		private Object content;

		public ResponseWrapper(Object content) {
			this(ResponseWrapper.SUCCESS, null, content);
		}

		public ResponseWrapper(String errorMessage) {
			this(ResponseWrapper.ERRROR, null, errorMessage);
		}

		public ResponseWrapper(String status, String errorMessage, Object content) {
			super();
			this.status = status;
			this.errorMessage = errorMessage;
			this.content = content;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getErrorMessage() {
			return errorMessage;
		}

		public void setErrorMessage(String errorMessage) {
			this.errorMessage = errorMessage;
		}

		public Object getContent() {
			return content;
		}

		public void setContent(Object content) {
			this.content = content;
		}

	}

}
