package com.fluidnotions.mock;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

//import org.ofbiz.base.location.FlexibleLocation;
//import org.ofbiz.base.util.Debug;
//import org.ofbiz.base.util.UtilProperties;
import com.google.api.client.auth.oauth2.DataStoreCredentialRefreshListener;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreUtils;
import com.google.api.client.util.store.FileDataStoreFactory;

public class MockGoogleCredentialFactory {

	private static final Log log = LogFactory.getLog(MockGoogleCredentialFactory.class);
	private static final String module = MockGoogleCredentialFactory.class.getName();
	public static final String DEFAULT_DATA_STORE_ID = "GOOGLE_IDENTITY_CREDENTIALS";
	private static DataStore<StoredCredential> dataStore;
	
	//mock properties
	static String dataStoreDirectory = MockProperties.dataStoreDirectory;
	static String clientSecretFilePath = MockProperties.clientSecretFilePath;
	static String commaSepScopes = MockProperties.commaSepScopes;

	static {
		try {
			FileDataStoreFactory fileDataStoreFactory = new FileDataStoreFactory(new File(dataStoreDirectory));
			dataStore = fileDataStoreFactory.getDataStore(DEFAULT_DATA_STORE_ID);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static GoogleCredential retrieveCredential(String username) throws Exception {
		if (dataStore.containsKey(username)) {
			
			NetHttpTransport transport = new NetHttpTransport();
			JacksonFactory jsonFactory = new JacksonFactory();


			GoogleCredential credential = new GoogleCredential.Builder().setTransport(transport).setJsonFactory(jsonFactory).setClientSecrets(GoogleClientSecrets.load(jsonFactory, new FileReader(clientSecretFilePath)))
					.addRefreshListener(new DataStoreCredentialRefreshListener(username, dataStore)).build();

			StoredCredential storedCredential = dataStore.get(username);
			credential.setAccessToken(storedCredential.getAccessToken());
			credential.setRefreshToken(storedCredential.getRefreshToken());
			credential.setExpirationTimeMilliseconds(storedCredential.getExpirationTimeMilliseconds());
			
			//Debug.logInfo("accessToken: "+credential.getAccessToken()+", refreshToken: "+credential.getRefreshToken(), module);
			Long expireIn = credential.getExpiresInSeconds();
			//Debug.logInfo("crdential accessToken due to expire in "+expireIn+" secs", module);
			if(expireIn == null || expireIn<0){
				boolean successfullyRefreshed = false;
				try {
					 successfullyRefreshed = credential.refreshToken();
				} catch (IOException e) {
					e.printStackTrace();
				}
				Debug.logInfo("expired credential "+(successfullyRefreshed?"was successfully refreshed!":"could not be refreshed!!!!"), module);
			}

			return credential;

		} else {
			log.debug(
					"username: " + username + "not found in datastore named: " + DEFAULT_DATA_STORE_ID + ", with current state: "
							+ DataStoreUtils.toString(dataStore));
			throw new Exception("username: " + username + "not found in datastore named: " + DEFAULT_DATA_STORE_ID);
		}
	}

	public static GoogleCredential storeCredential(String authorizationCode, String username) throws Exception {
		GoogleCredential credential = null;

		if (authorizationCode != null) {

			NetHttpTransport transport = new NetHttpTransport();
			JacksonFactory jsonFactory = new JacksonFactory();

			log.debug("dataStore contains username: " + username + "? :" + dataStore.containsKey(username));
			log.debug("authorizationCode: " + authorizationCode);
			log.debug("clientSecretFilePath: " + clientSecretFilePath);
			// Exchange auth code for access token
			GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, new FileReader(clientSecretFilePath));
			String clientId = clientSecrets.getDetails().getClientId();
			String secret = clientSecrets.getDetails().getClientSecret();
			String firstRedirectUri = clientSecrets.getDetails().getRedirectUris().get(0);
			log.debug("clientId: " + clientId + ", secret: " + secret +", firstRedirectUri: "+firstRedirectUri);

			GoogleAuthorizationCodeTokenRequest tokenRequest = new GoogleAuthorizationCodeTokenRequest(transport, JacksonFactory.getDefaultInstance(),
					"https://accounts.google.com/o/oauth2/token", clientId, secret, authorizationCode, firstRedirectUri);

			tokenRequest.setGrantType("authorization_code");
			
			String[] scopes = {commaSepScopes};
			if(commaSepScopes.indexOf(",")>-1){
				scopes = commaSepScopes.split(",");
			}
			tokenRequest.setScopes(Arrays.asList(scopes));
			log.debug("tokenRequest: " + tokenRequest.toString());
			GoogleTokenResponse tokenResponse = tokenRequest.execute();

			log.debug("tokenResponse: " + tokenResponse.toString());

			if (tokenResponse != null) {
				//String accessToken = tokenResponse.getAccessToken();
				String refreshToken = tokenResponse.getRefreshToken();
				if (refreshToken == null) {
					log.debug("cannot store credential when there is no refresh token, check client config settings access_type is set to offline");
					throw new Exception("cannot store credential when there is no refresh token, check client config settings access_type is set to offline");
				} 
				log.debug("refreshToken: " + refreshToken);
				
//				credential = new GoogleCredential();
//				credential.setAccessToken(accessToken);
//				credential.setRefreshToken(refreshToken);
				
				// Create the OAuth2 credential.
				credential = new GoogleCredential.Builder()
				      .setTransport(new NetHttpTransport())
				      .setJsonFactory(new JacksonFactory())
				      .setClientSecrets(clientSecrets)
				      .build();

				  // Set authorized credentials.
				  credential.setFromTokenResponse(tokenResponse);
				
				dataStore.set(username, new StoredCredential(credential));

				StoredCredential storedCredential = dataStore.get(username);
				log.debug("StoredCredential added to datastore for: " + storedCredential.toString());
			}

		}
		return credential;
	}

}

